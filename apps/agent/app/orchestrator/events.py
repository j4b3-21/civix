"""Execution event emission with a durable outbox. Events never carry secrets, document text or field values."""

from __future__ import annotations

import asyncio
import datetime as dt
import re
import uuid
from typing import Any

import structlog

from app.clients.api_client import ApiCallError, ApiClient
from app.contracts.common import AgentName, ApplicationStatus, EventType, TaskStatus
from app.persistence.db import AgentDatabase

log = structlog.get_logger("civix.events")
_NS = uuid.UUID("8f0c7a52-3c1e-4c39-9e0f-6b0e4a3f2c11")
_UNSET: Any = object()
_SECRETISH = re.compile(r"(otp|password|token|cookie|secret)", re.I)


def event_id(task_id: str, counter: int) -> str:
    """Deterministic: a node re-executed after a crash re-emits the same ids, which Spring de-duplicates."""
    return str(uuid.uuid5(_NS, f"{task_id}:{counter}"))


def _safe_metadata(md: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for k, v in md.items():
        if _SECRETISH.search(k) and k not in ("approvalId",):
            continue
        out[k] = v
    return out


def build_event(
    *,
    task_id: str,
    counter: int,
    type: EventType,
    agent: AgentName,
    message: str,
    step: str | None = None,
    metadata: dict[str, Any] | None = None,
    task_status: TaskStatus | None = None,
    application_status: ApplicationStatus | None = None,
    current_step: str | None = None,
    current_agent: AgentName | None = None,
    pending_citizen_input: Any = _UNSET,
    observation: dict[str, Any] | None = None,
    error: dict[str, Any] | None = None,
) -> dict[str, Any]:
    ev: dict[str, Any] = {
        "eventId": event_id(task_id, counter),
        "taskId": task_id,
        "type": type.value,
        "timestamp": dt.datetime.now(dt.UTC).isoformat().replace("+00:00", "Z"),
        "agent": agent.value,
        "step": step,
        "message": message[:1000],
        "metadata": _safe_metadata(metadata or {}),
        "currentAgent": (current_agent or agent).value,
    }
    if task_status is not None:
        ev["taskStatus"] = task_status.value
    if application_status is not None:
        ev["applicationStatus"] = application_status.value
    if current_step is not None:
        ev["currentStep"] = current_step
    if pending_citizen_input is not _UNSET:
        ev["pendingCitizenInput"] = pending_citizen_input
    if observation is not None:
        ev["observation"] = observation
    if error is not None:
        ev["error"] = error
    return ev


class EventSink:
    """Delivers events to Spring in order. With a database, events are written to the outbox first (durable)."""

    def __init__(self, api: ApiClient, db: AgentDatabase | None = None) -> None:
        self._api = api
        self._db = db
        self._lock = asyncio.Lock()
        self._flusher: asyncio.Task[None] | None = None

    async def publish(self, events: list[dict[str, Any]]) -> None:
        if not events:
            return
        for e in events:
            log.info("event.emitted", task_id=e["taskId"], event_type=e["type"], step=e.get("step"))
        if self._db is None:
            await self._api.post_events(events)
            return
        await self._db.enqueue_events(events)
        await self.flush()

    async def flush(self) -> bool:
        if self._db is None:
            return True
        async with self._lock:
            while True:
                rows = await self._db.pending_events()
                if not rows:
                    return True
                ids = [r["id"] for r in rows]
                try:
                    await self._api.post_events([r["payload"] for r in rows])
                except ApiCallError as exc:
                    await self._db.mark_attempt(ids)
                    if exc.status == 409:
                        # Spring rejected a transition; mark delivered so the outbox is not blocked, and log loudly.
                        log.error("event.rejected_by_api", code=str(exc.status))
                        await self._db.mark_delivered(ids)
                        continue
                    log.warning("event.delivery_deferred", code=str(exc.status))
                    return False
                await self._db.mark_delivered(ids)

    def start_background_flush(self, interval: float = 5.0) -> None:
        async def loop() -> None:
            while True:
                await asyncio.sleep(interval)
                try:
                    await self.flush()
                except Exception:  # noqa: BLE001 - keep retrying
                    log.exception("event.flush_failed")

        if self._db is not None and self._flusher is None:
            self._flusher = asyncio.create_task(loop())

    async def stop(self) -> None:
        if self._flusher:
            self._flusher.cancel()
            self._flusher = None
