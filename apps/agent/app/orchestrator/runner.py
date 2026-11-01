"""TaskRunner: starts, resumes, cancels and restart-recovers LangGraph runs. One run per task at a time."""

from __future__ import annotations

import asyncio
from typing import Any

import structlog
from langgraph.types import Command

from app.contracts.common import TaskStatus
from app.orchestrator.events import build_event
from app.orchestrator.graph import GraphDeps
from app.contracts.common import AgentName, ApplicationStatus, EventType

log = structlog.get_logger("civix.runner")


class TaskRunner:
    def __init__(self, graph: Any, deps: GraphDeps, max_concurrent: int = 4) -> None:
        self.graph = graph
        self.deps = deps
        self._sem = asyncio.Semaphore(max_concurrent)
        self._locks: dict[str, asyncio.Lock] = {}
        self._tasks: dict[str, asyncio.Task[None]] = {}
        self._cancelled: set[str] = set()
        self._memory_status: dict[str, str] = {}
        deps.is_cancelled = lambda tid: tid in self._cancelled

    def _config(self, task_id: str) -> dict[str, Any]:
        return {"configurable": {"thread_id": task_id}, "recursion_limit": 400}

    async def _set_status(self, task_id: str, status: str) -> None:
        self._memory_status[task_id] = status
        if self.deps.db:
            await self.deps.db.set_run_status(task_id, status)

    # ------------------------------------------------------------------ commands
    async def start(self, *, task_id: str, application_id: str, user_id: str, service_code: str, intent: dict[str, Any],
                    citizen_context: dict[str, Any], documents: list[dict[str, Any]], data_version: int) -> None:
        if self.deps.db:
            await self.deps.db.upsert_run(task_id, application_id, user_id, service_code, TaskStatus.QUEUED.value)
        self._memory_status[task_id] = TaskStatus.QUEUED.value
        initial = {
            "task_id": task_id, "application_id": application_id, "user_id": user_id, "service_code": service_code,
            "intent": intent, "citizen_context": citizen_context, "documents": documents, "data_version": data_version,
            "current_step": 0, "event_counter": 0, "errors": [],
        }
        self._spawn(task_id, initial)

    async def resume_approval(self, task_id: str, *, approval_id: str, decision: str, token: str | None) -> None:
        if token and decision == "APPROVE":
            self.deps.vault.put(approval_id, token)
        self._spawn(task_id, Command(resume={"kind": "APPROVAL", "approvalId": approval_id, "decision": decision}))

    async def resume_citizen_input(self, task_id: str, *, request_id: str, value: str) -> None:
        self.deps.inbox.put(request_id, value)
        self._spawn(task_id, Command(resume={"kind": "CITIZEN_INPUT", "requestId": request_id}))

    async def retry(self, task_id: str) -> None:
        self._cancelled.discard(task_id)
        self._spawn(task_id, None)

    async def cancel(self, task_id: str) -> None:
        self._cancelled.add(task_id)
        state = await self.graph.aget_state(self._config(task_id))
        if state is not None and state.next and state.interrupts:
            self._spawn(task_id, Command(resume={"kind": "CANCEL"}))
        elif task_id not in self._tasks:
            # not running and not waiting (e.g. never started or already finished): report directly
            if not state or not state.values or state.values.get("execution_status") in (None, "RUNNING", "QUEUED"):
                await self.deps.sink.publish([build_event(
                    task_id=task_id, counter=10_000 + len(self._cancelled), type=EventType.TASK_CANCELLED, agent=AgentName.ORCHESTRATOR,
                    message="Cancelled at your request.", task_status=TaskStatus.CANCELLED, application_status=ApplicationStatus.CANCELLED)])
                await self._set_status(task_id, TaskStatus.CANCELLED.value)

    async def recover_on_startup(self) -> int:
        """Re-drive runs that were RUNNING/QUEUED when the process stopped. Waiting runs stay paused at their interrupt."""
        if not self.deps.db:
            return 0
        runs = await self.deps.db.runs_with_status(TaskStatus.RUNNING.value, TaskStatus.QUEUED.value)
        for r in runs:
            log.info("runner.resume_after_restart", task_id=str(r["task_id"]))
            self._spawn(str(r["task_id"]), None)
        return len(runs)

    # ------------------------------------------------------------------ execution
    def _spawn(self, task_id: str, payload: Any) -> None:
        t = asyncio.create_task(self._drive(task_id, payload))
        self._tasks[task_id] = t
        t.add_done_callback(lambda _t: self._tasks.pop(task_id, None) if self._tasks.get(task_id) is _t else None)

    async def _drive(self, task_id: str, payload: Any) -> None:
        lock = self._locks.setdefault(task_id, asyncio.Lock())
        async with lock, self._sem:
            await self._set_status(task_id, TaskStatus.RUNNING.value)
            try:
                await self.graph.ainvoke(payload, config=self._config(task_id))
            except Exception:  # noqa: BLE001 - the run stays resumable from its last checkpoint
                log.exception("runner.run_crashed", task_id=task_id)
                await self.deps.sink.publish([build_event(
                    task_id=task_id, counter=20_000 + int(asyncio.get_running_loop().time()) % 10_000, type=EventType.TASK_FAILED,
                    agent=AgentName.ORCHESTRATOR, message="CIVIX hit an internal error. The task can be retried from where it stopped.",
                    metadata={"errorCode": "INTERNAL_ERROR", "retryable": True}, task_status=TaskStatus.FAILED,
                    error={"code": "INTERNAL_ERROR", "message": "internal error"})])
                await self._set_status(task_id, TaskStatus.FAILED.value)
                return
            snap = await self.graph.aget_state(self._config(task_id))
            if snap.next:
                kind = (snap.interrupts[0].value or {}).get("type") if snap.interrupts else None
                await self._set_status(task_id, TaskStatus.WAITING_CITIZEN.value if kind == "CITIZEN_INPUT" else TaskStatus.WAITING_APPROVAL.value)
            else:
                await self._set_status(task_id, (snap.values or {}).get("execution_status", TaskStatus.COMPLETED.value))
                self._cancelled.discard(task_id)

    async def wait_idle(self, task_id: str, timeout: float = 120) -> None:
        t = self._tasks.get(task_id)
        if t:
            await asyncio.wait_for(asyncio.shield(t), timeout)

    async def status(self, task_id: str) -> dict[str, Any] | None:
        snap = await self.graph.aget_state(self._config(task_id))
        if snap is None or not snap.values:
            return None
        v = snap.values
        pending = None
        if snap.interrupts:
            pending = snap.interrupts[0].value
        status = self._memory_status.get(task_id)
        if status is None and self.deps.db:
            run = await self.deps.db.get_run(task_id)
            status = run["status"] if run else None
        plan = v.get("execution_plan")
        steps = plan["steps"] if plan else []
        idx = v.get("current_step", 0)
        return {
            "taskId": task_id, "status": status, "currentNode": snap.next[0] if snap.next else None,
            "currentStep": steps[idx]["id"] if steps and idx < len(steps) else None,
            "plan": {"steps": [{"id": st["id"], "title": st["title"], "action": st["action"], "approval": st["approval"]} for st in steps]} if plan else None,
            "pendingInterrupt": pending, "recoveryAttempts": v.get("recovery_attempts", 0), "errors": v.get("errors", []),
            "final": v.get("final"),
        }
