"""CIVIX LangGraph orchestration (docs/WORKFLOWS.md §1).

START → intent_analysis → profile_retrieval → service_discovery → eligibility_evaluation → document_validation
      → execution_planning → human_approval ⇄ human_approval_wait → browser_execution ⇄ (citizen_input_wait | recovery)
      → final_submission_approval ⇄ final_submission_wait → submission → verification → application_result → END

The orchestrator owns the workflow. Agents return typed results; only nodes change state; only Spring changes
business records (via callbacks that it validates).
"""

from __future__ import annotations

import asyncio
import base64
import re
import time
import uuid
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

import structlog
from langgraph.errors import GraphBubbleUp
from langgraph.graph import END, START, StateGraph
from langgraph.types import interrupt
from prometheus_client import Counter, Histogram

from app.agents import document_agent, profile_agent, recovery_agent
from app.agents.planning_agent import PlanningError, build_plan
from app.browser.client import BrowserClient, BrowserUnavailable
from app.clients.api_client import ApiCallError, ApiClient
from app.config.settings import Settings
from app.contracts.browser import ActionRequest, ActionResult, Observation
from app.contracts.common import (
    ActionClass,
    AgentName,
    ApplicationStatus,
    ApprovalKind,
    CitizenInputKind,
    ConsentPurpose,
    EligibilityOutcome,
    EventType,
    TaskStatus,
)
from app.contracts.context import CitizenContext, TaskDocument
from app.contracts.plan import ExecutionPlan, PlannedStep
from app.contracts.registry import Target
from app.orchestrator.events import EventSink, build_event
from app.orchestrator.secrets import SecretInbox, TokenVault
from app.orchestrator.state import CivixState
from app.persistence.db import AgentDatabase
from app.policies import approval_tokens
from app.policies.eligibility import evaluate_service
from app.retrieval.registry import Registry
from app.services.llm.base import LLMProvider, NullProvider, llm_context
from app.services.storage.s3 import ObjectStorage

log = structlog.get_logger("civix.orchestrator")

NODE_SECONDS = Histogram("civix_agent_node_duration_seconds", "Graph node duration", ["node"])
BROWSER_ACTIONS = Counter("civix_browser_actions_total", "Browser actions", ["action", "outcome"])
RECOVERIES = Counter("civix_recovery_total", "Recovery decisions", ["reason", "action"])
TASK_OUTCOMES = Counter("civix_task_outcomes_total", "Task outcomes", ["task_status", "application_status"])
NODE_ERRORS = Counter("civix_agent_node_errors_total", "Node errors", ["node", "error"])


@dataclass
class GraphDeps:
    settings: Settings
    registry: Callable[[], Registry]
    api: ApiClient
    sink: EventSink
    browser: BrowserClient
    storage: ObjectStorage
    inbox: SecretInbox
    vault: TokenVault
    db: AgentDatabase | None = None
    llm: LLMProvider = NullProvider()
    is_cancelled: Callable[[str], bool] = lambda _tid: False
    submissions_seen: set[str] | None = None  # used only when db is None (tests)


# ------------------------------------------------------------------------------------------------ helpers


class Emitter:
    def __init__(self, deps: GraphDeps, state: CivixState) -> None:
        self.deps = deps
        self.task_id = state["task_id"]
        self.counter = state.get("event_counter", 0)

    async def emit(self, type: EventType, agent: AgentName, message: str, **kw: Any) -> None:
        self.counter += 1
        ev = build_event(task_id=self.task_id, counter=self.counter, type=type, agent=agent, message=message, **kw)
        await self.deps.sink.publish([ev])

    def update(self, **values: Any) -> dict[str, Any]:
        return {"event_counter": self.counter, **values}


def _final(task_status: TaskStatus, application_status: ApplicationStatus, *, code: str | None = None, message: str,
           retryable: bool = False, required_action: dict[str, Any] | None = None) -> dict[str, Any]:
    return {"taskStatus": task_status.value, "applicationStatus": application_status.value, "errorCode": code,
            "message": message, "retryable": retryable, "requiredAction": required_action}


def _plan(state: CivixState) -> ExecutionPlan:
    return ExecutionPlan.model_validate(state["execution_plan"])


def _ctx(state: CivixState) -> CitizenContext:
    return CitizenContext.model_validate(state["citizen_context"])


async def _save_screenshot(deps: GraphDeps, task_id: str, png_b64: str | None) -> str | None:
    if not png_b64:
        return None
    sid = str(uuid.uuid4())
    try:
        await deps.storage.put(f"tasks/{task_id}/screenshots/{sid}.png", base64.b64decode(png_b64), "image/png")
    except Exception:  # noqa: BLE001 - screenshots are best-effort evidence
        log.warning("screenshot.store_failed", task_id=task_id)
        return None
    return sid


async def _obs_summary(deps: GraphDeps, task_id: str, obs: Observation | None) -> dict[str, Any] | None:
    if obs is None:
        return None
    shot = await _save_screenshot(deps, task_id, obs.screenshot_png)
    return {"url": obs.url, "title": obs.title, "screenshotId": shot, "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}


def _target(base: Target | None, override_label: str | None) -> Target:
    t = (base or Target()).model_copy(deep=True)
    if override_label:
        t.alternate_labels = [x for x in [t.label, *t.alternate_labels] if x]
        t.label = override_label
    return t


def _step_needs_token(deps: GraphDeps, state: CivixState, step: PlannedStep) -> bool:
    if step.approval not in (ApprovalKind.PREPARATION, ApprovalKind.DOCUMENT_UPLOAD):
        return False
    aid = (state.get("granted_approvals") or {}).get(step.approval.value)
    return aid is None or deps.vault.get(aid) is None


# ------------------------------------------------------------------------------------------------ nodes


def build_graph(deps: GraphDeps, checkpointer: Any) -> Any:
    s = deps.settings

    async def intent_analysis(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        intent = state.get("intent") or {"type": "APPLY"}
        summary = f"Apply for {state['service_code']}" if intent.get("type") == "APPLY" else f"Discover services: {intent.get('text', '')[:120]}"
        await em.emit(EventType.TASK_STARTED, AgentName.ORCHESTRATOR, "CIVIX started working on your application.",
                      task_status=TaskStatus.RUNNING, application_status=ApplicationStatus.IN_PROGRESS, current_step="intent_analysis")
        return em.update(intent={**intent, "summary": summary}, errors=state.get("errors", []), granted_approvals=state.get("granted_approvals", {}),
                         approval_status=state.get("approval_status", {}), step_attempts={}, selector_overrides={}, recovery_attempts=0,
                         reauth_count=0, citizen_input_attempts={}, execution_status=TaskStatus.RUNNING.value)

    async def profile_retrieval(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        ctx = _ctx(state)
        if not ctx.has_consent(ConsentPurpose.AGENT_EXECUTION):
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="CONSENT_REQUIRED",
                                          message="Allow CIVIX to act on your behalf (Trust Center → Agent execution) and try again.",
                                          required_action={"type": "PROFILE", "message": "Grant the 'Agent execution' permission."}))
        report = profile_agent.analyze(ctx, deps.registry().get(state["service_code"]))
        total = len(report.available) + len(report.missing)
        await em.emit(EventType.PROFILE_VALIDATED, AgentName.PROFILE_AGENT,
                      f"{len(report.available)} of {total} needed profile fields available; {len(report.unverified)} not yet confirmed by you.",
                      metadata={"available": len(report.available), "missing": report.missing, "unverified": report.unverified},
                      current_step="profile_retrieval")
        return em.update(profile_report=report.wire(), missing_information=report.missing)

    async def service_discovery(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        svc = deps.registry().get(state["service_code"])
        if svc is None:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="SERVICE_NOT_FOUND",
                                          message="This service is no longer in the CIVIX registry."))
        if svc.is_test_service and not s.civix_enable_test_services:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="SERVICE_UNAVAILABLE",
                                          message="Test services are disabled in this environment."))
        if not svc.is_executable:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="AUTOMATION_UNAVAILABLE",
                                          message=svc.automation_note or "CIVIX cannot apply to this service automatically. Apply on the official portal.",
                                          required_action={"type": "PROFILE", "message": "Apply on the official portal using the prepared checklist."}))
        candidate = {"serviceCode": svc.code, "name": svc.name, "department": svc.department, "jurisdiction": svc.jurisdiction.wire(),
                     "sources": [{"url": x.url, "title": x.title, "retrievedAt": x.retrieved_at.isoformat()} for x in svc.sources],
                     "isTestService": svc.is_test_service}
        await em.emit(EventType.SERVICE_DISCOVERED, AgentName.DISCOVERY_AGENT, f"Selected service: {svc.name}",
                      metadata={"serviceCode": svc.code, "version": svc.version, "verificationStatus": svc.verification_status.value,
                                "isTestService": svc.is_test_service, "sources": [x.url for x in svc.sources]},
                      current_step="service_discovery")
        return em.update(selected_service={"code": svc.code, "version": svc.version, "name": svc.name}, discovered_services=[candidate])

    async def eligibility_evaluation(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        reg = deps.registry()
        svc = reg.get(state["service_code"])
        assert svc is not None
        result = evaluate_service(svc, _ctx(state), reg.profile_fields)
        await em.emit(EventType.ELIGIBILITY_EVALUATED, AgentName.ELIGIBILITY_AGENT, result.explanation,
                      metadata={"outcome": result.outcome.value, "failed": result.failed, "unknown": result.unknown},
                      current_step="eligibility_evaluation")
        upd = em.update(eligibility=result.wire(), missing_information=result.missing_fields)
        if result.outcome is EligibilityOutcome.INELIGIBLE:
            upd["final"] = _final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="INELIGIBLE",
                                  message="Based on your profile you do not meet this service's eligibility rules. If your profile is wrong, correct it and try again.")
        elif result.outcome is EligibilityOutcome.NEEDS_MORE_INFORMATION:
            upd["final"] = _final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="NEEDS_PROFILE_INFORMATION",
                                  message=f"Add these to your profile first: {', '.join(result.missing_fields)}.",
                                  required_action={"type": "PROFILE", "message": "Complete missing profile fields", "fields": result.missing_fields})
        return upd

    async def document_validation(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        svc = deps.registry().get(state["service_code"])
        assert svc is not None
        docs = [TaskDocument.model_validate(d) for d in state.get("documents", [])]
        check = document_agent.check_documents(svc, docs)
        if check.missing_mandatory:
            cats = [m["category"] for m in check.missing_mandatory]
            await em.emit(EventType.DOCUMENT_REQUIRED, AgentName.DOCUMENT_AGENT, f"Upload the required document(s): {', '.join(cats)}.",
                          metadata={"missing": cats}, current_step="document_validation")
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="DOCUMENT_REQUIRED",
                                          message=f"Upload: {', '.join(cats)}.",
                                          required_action={"type": "DOCUMENT", "message": "Upload required documents", "categories": cats}))
        needs_upload = bool(svc.workflow and any(st.documents for st in svc.workflow.steps))
        if needs_upload and not _ctx(state).has_consent(ConsentPurpose.PORTAL_DOCUMENT_UPLOAD):
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="CONSENT_REQUIRED",
                                          message="This portal needs your documents. Allow 'Portal document upload' in the Trust Center and try again.",
                                          required_action={"type": "DOCUMENT", "message": "Grant the 'Portal document upload' permission."}))
        note = " Some documents have not been reviewed by you yet." if check.review_required else ""
        await em.emit(EventType.DOCUMENT_EXTRACTED, AgentName.DOCUMENT_AGENT,
                      f"{len(check.satisfied)} required document(s) available.{note}",
                      metadata={"satisfied": [x["category"] for x in check.satisfied], "unreviewed": len(check.unreviewed)},
                      current_step="document_validation")
        return em.update()

    async def execution_planning(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        svc = deps.registry().get(state["service_code"])
        assert svc is not None
        docs = [TaskDocument.model_validate(d) for d in state.get("documents", [])]
        try:
            res = build_plan(svc, _ctx(state), docs, state.get("data_version", 1))
        except PlanningError as exc:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="PLANNING_FAILED", message=str(exc)))
        if res.plan is None:
            missing = res.missing_fields + res.missing_documents
            kind = "DOCUMENT" if res.missing_documents and not res.missing_fields else "PROFILE"
            return em.update(missing_information=missing, final=_final(
                TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="NEEDS_PROFILE_INFORMATION",
                message=f"The portal form needs: {', '.join(missing)}.", required_action={"type": kind, "message": "Complete missing information", "fields": missing}))
        plan = res.plan
        await em.emit(EventType.PLAN_CREATED, AgentName.PLANNING_AGENT, f"Prepared a {len(plan.steps)}-step plan for {svc.name}.",
                      metadata={"steps": len(plan.steps), "stepIds": [x.id for x in plan.steps],
                                "approvalsRequired": [k.value for k in ApprovalKind if plan.requires(k)]},
                      current_step="execution_planning")
        return em.update(execution_plan=plan.wire(), current_step=0)

    # --------------------------------------------------------------------- approvals
    async def _request_approval(state: CivixState, kind: ApprovalKind, em: Emitter) -> dict[str, Any]:
        plan = _plan(state)
        portal = plan.allowed_domains[0]
        if kind is ApprovalKind.PREPARATION:
            fields = [{"label": f.label, "value": f.value} for st in plan.steps for f in st.fields]
            details = {"fields": fields, "documents": [], "portal": portal}
            summary, action_class, digest = f"Fill {len(fields)} field(s) on {portal} from your profile", ActionClass.REVERSIBLE, plan.preparation_digest
            event_type = EventType.APPROVAL_REQUIRED
        elif kind is ApprovalKind.DOCUMENT_UPLOAD:
            docs = [{"documentId": d.document_id, "category": d.category.value, "fileName": d.file_name} for st in plan.steps for d in st.documents]
            details = {"fields": [], "documents": docs, "portal": portal}
            summary, action_class, digest = f"Upload {len(docs)} document(s) to {portal}", ActionClass.SENSITIVE, plan.upload_digest
            event_type = EventType.APPROVAL_REQUIRED
        else:
            fields = [{"label": f.label, "value": f.value} for st in plan.steps for f in st.fields]
            docs = [{"documentId": d.document_id, "category": d.category.value, "fileName": d.file_name} for st in plan.steps for d in st.documents]
            details = {"fields": fields, "documents": docs, "portal": portal,
                       "note": "Submitting is final. CIVIX will click the portal's submit button once and then verify the acknowledgement."}
            summary, action_class, digest = f"Submit the application on {portal}", ActionClass.CONSEQUENTIAL, plan.submission_digest
            event_type = EventType.SUBMISSION_APPROVAL_REQUIRED
        created = await deps.api.create_approval({
            "taskId": state["task_id"], "applicationId": state["application_id"], "kind": kind.value, "actionClass": action_class.value,
            "summary": summary, "details": details, "payloadDigest": digest, "dataVersion": plan.data_version,
            "expiresInSeconds": s.approval_ttl_seconds,
        })
        await em.emit(event_type, AgentName.ORCHESTRATOR, f"Your approval is needed: {summary}.",
                      metadata={"approvalId": created.approval_id, "kind": kind.value, "expiresAt": created.expires_at},
                      task_status=TaskStatus.WAITING_APPROVAL, application_status=ApplicationStatus.ACTION_REQUIRED,
                      current_step=f"approval:{kind.value.lower()}")
        approval_status = dict(state.get("approval_status") or {})
        approval_status[kind.value] = "PENDING"
        return em.update(pending_approval={"approvalId": created.approval_id, "kind": kind.value, "digest": digest, "expiresAt": created.expires_at},
                         approval_status=approval_status)

    async def human_approval(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        plan = _plan(state)
        granted = state.get("granted_approvals") or {}
        for kind in (ApprovalKind.PREPARATION, ApprovalKind.DOCUMENT_UPLOAD):
            if plan.requires(kind) and (kind.value not in granted or deps.vault.get(granted[kind.value]) is None):
                return await _request_approval(state, kind, em)
        return em.update(pending_approval=None)

    async def _handle_approval_resume(state: CivixState, resume: Any) -> dict[str, Any]:
        em = Emitter(deps, state)
        pa = state["pending_approval"] or {}
        kind = ApprovalKind(pa["kind"])
        if not isinstance(resume, dict) or resume.get("kind") == "CANCEL":
            return em.update(cancel_requested=True)
        if resume.get("approvalId") != pa["approvalId"]:
            log.warning("approval.stale_resume", task_id=state["task_id"])
            return em.update()  # still pending; wait again
        approval_status = dict(state.get("approval_status") or {})
        if resume.get("decision") != "APPROVE":
            approval_status[kind.value] = "REJECTED"
            await em.emit(EventType.APPROVAL_REJECTED, AgentName.ORCHESTRATOR, "You rejected the request. CIVIX stopped and did not act.",
                          metadata={"approvalId": pa["approvalId"], "kind": kind.value})
            return em.update(pending_approval=None, approval_status=approval_status,
                             final=_final(TaskStatus.CANCELLED, ApplicationStatus.CANCELLED, code="APPROVAL_REJECTED",
                                          message="Stopped because you rejected the approval request."))
        token = deps.vault.get(pa["approvalId"])
        try:
            if token is None:
                raise approval_tokens.ApprovalTokenError("APPROVAL_TOKEN_MISSING", "approval token not available")
            approval_tokens.verify(s.approval_signing_secret.get_secret_value(), token, expected_task_id=state["task_id"],
                                   expected_kind=kind, expected_digest=pa["digest"], expected_approval_id=pa["approvalId"])
        except approval_tokens.ApprovalTokenError as exc:
            approval_status[kind.value] = "INVALID"
            return em.update(pending_approval=None, approval_status=approval_status, final=_final(
                TaskStatus.FAILED, ApplicationStatus.FAILED, code="APPROVAL_INVALID", retryable=True,
                message=f"The approval could not be verified ({exc.code}); nothing was done."))
        approval_status[kind.value] = "APPROVED"
        granted = dict(state.get("granted_approvals") or {})
        granted[kind.value] = pa["approvalId"]
        await em.emit(EventType.APPROVAL_GRANTED, AgentName.ORCHESTRATOR, "Approval received and verified.",
                      metadata={"approvalId": pa["approvalId"], "kind": kind.value},
                      task_status=TaskStatus.RUNNING, application_status=ApplicationStatus.IN_PROGRESS)
        return em.update(pending_approval=None, approval_status=approval_status, granted_approvals=granted)

    async def human_approval_wait(state: CivixState) -> dict[str, Any]:
        pa = state["pending_approval"] or {}
        resume = interrupt({"type": "APPROVAL", "approvalId": pa.get("approvalId"), "kind": pa.get("kind")})
        return await _handle_approval_resume(state, resume)

    async def final_submission_approval(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        plan = _plan(state)
        try:
            ctx = await deps.api.get_task_context(state["task_id"])
        except ApiCallError as exc:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="PRECHECK_FAILED", retryable=True,
                                          message=f"Could not confirm your data is unchanged before submission ({exc.status})."))
        if int(ctx.get("dataVersion", plan.data_version)) != plan.data_version:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="DATA_CHANGED",
                                          message="Your profile or documents changed after you approved. Run the application again so you can review the new data.",
                                          required_action={"type": "PROFILE", "message": "Data changed; review and run again."}))
        return await _request_approval(state, ApprovalKind.FINAL_SUBMISSION, em)

    async def final_submission_wait(state: CivixState) -> dict[str, Any]:
        pa = state["pending_approval"] or {}
        resume = interrupt({"type": "APPROVAL", "approvalId": pa.get("approvalId"), "kind": pa.get("kind")})
        return await _handle_approval_resume(state, resume)

    # --------------------------------------------------------------------- browser
    async def _act(sid: str, req: ActionRequest) -> ActionResult:
        req.timeout_ms = min(req.timeout_ms, int(s.browser_step_timeout_seconds * 1000))
        res = await deps.browser.act(sid, req)
        BROWSER_ACTIONS.labels(req.action, "ok" if res.ok and res.verified else (res.error.code if res.error else "unverified")).inc()
        return res

    async def _fail_step(state: CivixState, em: Emitter, idx: int, sid: str | None, res: ActionResult | None, code: str, message: str) -> dict[str, Any]:
        plan = _plan(state)
        expired = False
        if sid and plan.session_expired_markers and code not in ("BROWSER_CRASHED", "SESSION_NOT_FOUND"):
            try:
                expired = await deps.browser.matches(sid, plan.session_expired_markers)
            except BrowserUnavailable:
                expired = False
        obs = await _obs_summary(deps, state["task_id"], res.observation if res else None)
        errors = [*state.get("errors", []), {"step": plan.steps[idx].id, "code": code, "message": message[:300]}][-20:]
        return em.update(last_error={"code": code, "message": message[:300], "stepIndex": idx, "sessionExpired": expired},
                         browser_session_id=sid, browser_observation=obs or state.get("browser_observation"), errors=errors)

    async def browser_execution(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        plan = _plan(state)
        idx = state.get("current_step", 0)
        step = plan.steps[idx]
        if step.action in ("SUBMIT", "VERIFY_RECEIPT"):
            return em.update()
        if _step_needs_token(deps, state, step):
            granted = dict(state.get("granted_approvals") or {})
            granted.pop(step.approval.value, None)  # type: ignore[union-attr]
            return em.update(granted_approvals=granted)
        sid = state.get("browser_session_id")
        alive = bool(sid) and await deps.browser.has_session(sid)  # type: ignore[arg-type]
        if not alive:
            if sid or idx > 0:
                await em.emit(EventType.RECOVERY_STARTED, AgentName.RECOVERY_AGENT,
                              "The browser session was interrupted. Opening a new isolated session and replaying reversible steps.",
                              metadata={"reason": "INTERRUPTED", "attempt": state.get("recovery_attempts", 0) + 1,
                                        "maxAttempts": s.max_recovery_attempts_per_task})
                idx, step = 0, plan.steps[0]
                state = {**state, "recovery_attempts": state.get("recovery_attempts", 0) + 1}  # type: ignore[assignment]
            sid = await deps.browser.create_session(state["task_id"], plan.allowed_domains)
            await em.emit(EventType.BROWSER_STARTED, AgentName.BROWSER_AGENT, "Opened an isolated browser session.",
                          metadata={"allowedDomains": plan.allowed_domains}, current_step=step.id)
        assert sid is not None
        overrides = (state.get("selector_overrides") or {}).get(step.id, {})
        base_update = {"current_step": idx, "browser_session_id": sid, "recovery_attempts": state.get("recovery_attempts", 0)}

        if step.precondition and step.action != "NAVIGATE":
            if not await deps.browser.matches(sid, step.precondition):
                obs = await deps.browser.observe(sid)
                return {**await _fail_step(state, em, idx, sid, ActionResult(ok=False, verified=False, observation=obs), "EXPECTATION_FAILED",
                                           f"'{step.title}': the portal is not showing the expected page"), "current_step": idx,
                        "recovery_attempts": base_update["recovery_attempts"]}

        approval_id = None
        if step.approval in (ApprovalKind.PREPARATION, ApprovalKind.DOCUMENT_UPLOAD):
            approval_id = (state.get("granted_approvals") or {}).get(step.approval.value)

        last: ActionResult | None = None
        if step.action == "NAVIGATE":
            last = await _act(sid, ActionRequest(action="NAVIGATE", url=step.url, expected=step.expected))
        elif step.action == "FILL_FORM":
            for f in step.fields:
                tgt = Target(label=overrides.get(f.key, f.label), alternate_labels=[x for x in [f.label, *f.alternate_labels] if x != overrides.get(f.key, f.label)], name=f.name)
                if f.input_type in ("select",):
                    req = ActionRequest(action="SELECT", target=tgt, value=f.value, capture_screenshot=False)
                elif f.input_type in ("checkbox", "radio"):
                    if f.value != "true":
                        continue
                    req = ActionRequest(action="CHECK", target=tgt, capture_screenshot=False)
                else:
                    req = ActionRequest(action="FILL", target=tgt, value=f.value, capture_screenshot=False)
                last = await _act(sid, req)
                if not last.ok:
                    return {**await _fail_step(state, em, idx, sid, last, last.error.code if last.error else "UNKNOWN",
                                               f"'{f.label}': {last.error.message if last.error else 'failed'}"), **{k: v for k, v in base_update.items() if k != 'browser_session_id'}}
            last = await _act(sid, ActionRequest(action="CLICK", target=_target(step.continue_button, overrides.get("__continue__")), expected=step.expected))
        elif step.action == "UPLOAD_DOCUMENTS":
            token = deps.vault.get(approval_id or "")
            assert token is not None
            for d in step.documents:
                try:
                    content = await deps.storage.get(d.storage_key)
                    await deps.browser.stage_file(sid, d.document_id, d.file_name, content, token, plan.upload_digest)
                except BrowserUnavailable as exc:
                    return {**await _fail_step(state, em, idx, sid, None, "APPROVAL_INVALID" if "APPROVAL" in str(exc) else "BROWSER_CRASHED", str(exc)), **base_update}
                except KeyError:
                    return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="DOCUMENT_UNAVAILABLE",
                                                  message=f"The {d.category.value} file is no longer available. Upload it again.",
                                                  required_action={"type": "DOCUMENT", "message": "Re-upload document", "categories": [d.category.value]}))
                tgt = _target(Target(label=d.label, alternate_labels=d.alternate_labels), overrides.get(f"doc:{d.document_id}"))
                last = await _act(sid, ActionRequest(action="UPLOAD", target=tgt, document_id=d.document_id, capture_screenshot=False))
                if not last.ok:
                    return {**await _fail_step(state, em, idx, sid, last, last.error.code if last.error else "UNKNOWN", f"upload {d.label}"), **base_update}
            last = await _act(sid, ActionRequest(action="CLICK", target=_target(step.continue_button, overrides.get("__continue__")), expected=step.expected))
        elif step.action == "CITIZEN_INPUT":
            return await _citizen_input_step(state, em, plan, idx, step, sid, overrides, base_update)
        elif step.action == "REVIEW":
            text = await deps.browser.read_text(sid, Target(css="body")) or ""
            norm = re.sub(r"\s+", " ", text)
            values = {f.key: (f.label, f.value) for st in plan.steps for f in st.fields}
            mismatched = [values[k][0] for k in step.review_fields if k in values and values[k][1] not in norm]
            obs = await deps.browser.observe(sid)
            if mismatched:
                return {**await _fail_step(state, em, idx, sid, ActionResult(ok=False, verified=False, observation=obs), "REVIEW_MISMATCH",
                                           "The portal's review page does not show the approved values for: " + ", ".join(mismatched)), **base_update}
            last = ActionResult(ok=True, verified=True, observation=obs)

        assert last is not None
        if not last.ok or not last.verified:
            code = last.error.code if last.error else "EXPECTATION_FAILED"
            return {**await _fail_step(state, em, idx, sid, last, code, f"'{step.title}': {last.error.message if last.error else 'unexpected page'}"),
                    **{k: v for k, v in base_update.items() if k != "browser_session_id"}}
        obs = await _obs_summary(deps, state["task_id"], last.observation)
        md: dict[str, Any] = {"action": step.action, "url": obs["url"] if obs else None, "title": obs["title"] if obs else None,
                              "screenshotId": obs["screenshotId"] if obs else None}
        if step.fields:
            md["fields"] = [f.label for f in step.fields]
        if step.documents:
            md["documents"] = [d.category.value for d in step.documents]
        if approval_id:
            md["approvalId"] = approval_id
        await em.emit(EventType.BROWSER_ACTION_COMPLETED, AgentName.BROWSER_AGENT, f"Completed: {step.title}", step=step.id, metadata=md,
                      current_step=plan.steps[min(idx + 1, len(plan.steps) - 1)].id, observation=obs)
        attempts = dict(state.get("step_attempts") or {})
        attempts.pop(step.id, None)
        return em.update(**{**base_update, "current_step": idx + 1}, step_attempts=attempts, browser_observation=obs, last_error=None)

    async def _citizen_input_step(state, em, plan, idx, step, sid, overrides, base_update) -> dict[str, Any]:  # noqa: ANN001
        pending = state.get("pending_citizen_input")
        ready = state.get("citizen_input_ready")
        if step.input_kind is CitizenInputKind.PAYMENT:
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.ACTION_REQUIRED, code="PAYMENT_NOT_SUPPORTED",
                                          message="This portal asks for a payment. CIVIX never makes payments; complete it yourself on the official portal.",
                                          required_action={"type": "CITIZEN_INPUT", "message": "Complete the payment yourself on the portal."}))
        if not pending or pending.get("stepId") != step.id or ready != pending.get("requestId"):
            obs = await deps.browser.observe(sid)
            alert = f" The portal says: {obs.alerts[0]}" if obs.alerts else ""
            summary = await _obs_summary(deps, state["task_id"], obs)
            req = {"requestId": str(uuid.uuid4()), "kind": step.input_kind.value, "prompt": (step.prompt or "") + alert,
                   "screenshotId": summary["screenshotId"] if summary else None, "stepId": step.id}
            public = {k: v for k, v in req.items() if k != "stepId"}
            await em.emit(EventType.CITIZEN_INPUT_REQUIRED, AgentName.BROWSER_AGENT, f"Your input is needed: {step.title}.", step=step.id,
                          metadata={"requestId": req["requestId"], "kind": req["kind"], "prompt": req["prompt"], "screenshotId": req["screenshotId"]},
                          task_status=TaskStatus.WAITING_CITIZEN, application_status=ApplicationStatus.ACTION_REQUIRED,
                          pending_citizen_input=public, observation=summary, current_step=step.id)
            return em.update(**base_update, pending_citizen_input=req, citizen_input_ready=None, browser_observation=summary)
        value = deps.inbox.take(pending["requestId"])
        if value is None:  # lost (restart) or expired: ask again
            return em.update(**base_update, pending_citizen_input=None, citizen_input_ready=None)
        await em.emit(EventType.EXECUTION_RESUMED, AgentName.BROWSER_AGENT, "Thanks — continuing with the value you provided.", step=step.id,
                      task_status=TaskStatus.RUNNING, application_status=ApplicationStatus.IN_PROGRESS, pending_citizen_input=None)
        tgt = _target(step.input_field, overrides.get("__input__"))
        if step.input_kind is CitizenInputKind.DECLARATION:
            if value.strip().upper() != "ACCEPT":
                return em.update(pending_citizen_input=None, citizen_input_ready=None, final=_final(
                    TaskStatus.CANCELLED, ApplicationStatus.CANCELLED, code="DECLARATION_DECLINED",
                    message="You did not accept the portal's declaration, so CIVIX stopped."))
            res = await _act(sid, ActionRequest(action="CHECK", target=tgt, capture_screenshot=False))
        else:
            res = await _act(sid, ActionRequest(action="FILL", target=tgt, value=value, capture_screenshot=False))
        value = ""  # drop the secret as early as possible
        if res.ok:
            res = await _act(sid, ActionRequest(action="CLICK", target=_target(step.continue_button, overrides.get("__continue__")), expected=step.expected))
        attempts = dict(state.get("citizen_input_attempts") or {})
        if not res.ok or not res.verified:
            alerts = res.observation.alerts if res.observation else []
            if plan.session_expired_markers and await deps.browser.matches(sid, plan.session_expired_markers):
                out = await _fail_step(state, em, idx, sid, res, "SESSION_EXPIRED", f"'{step.title}': the portal session expired")
                return {**out, "pending_citizen_input": None, "citizen_input_ready": None, "current_step": idx}
            attempts[step.id] = attempts.get(step.id, 0) + 1
            if alerts and attempts[step.id] < 3:  # e.g. "Incorrect OTP": ask the citizen again rather than retrying blindly
                return em.update(**base_update, pending_citizen_input=None, citizen_input_ready=None, citizen_input_attempts=attempts)
            code = res.error.code if res.error else "EXPECTATION_FAILED"
            if alerts:
                code = "CITIZEN_INPUT_REJECTED"
            out = await _fail_step(state, em, idx, sid, res, code, f"'{step.title}': {alerts[0] if alerts else 'not accepted'}")
            return {**out, "pending_citizen_input": None, "citizen_input_ready": None, "citizen_input_attempts": attempts, "current_step": idx}
        obs = await _obs_summary(deps, state["task_id"], res.observation)
        await em.emit(EventType.BROWSER_ACTION_COMPLETED, AgentName.BROWSER_AGENT, f"Completed: {step.title} (value entered as you provided it)",
                      step=step.id, metadata={"action": "CITIZEN_INPUT", "kind": step.input_kind.value, "url": obs["url"] if obs else None,
                                              "screenshotId": obs["screenshotId"] if obs else None},
                      observation=obs, current_step=plan.steps[min(idx + 1, len(plan.steps) - 1)].id)
        return em.update(**{**base_update, "current_step": idx + 1}, pending_citizen_input=None, citizen_input_ready=None, browser_observation=obs,
                         last_error=None, citizen_input_attempts=attempts)

    async def citizen_input_wait(state: CivixState) -> dict[str, Any]:
        pending = state.get("pending_citizen_input") or {}
        resume = interrupt({"type": "CITIZEN_INPUT", "requestId": pending.get("requestId"), "kind": pending.get("kind")})
        if not isinstance(resume, dict) or resume.get("kind") == "CANCEL":
            return {"cancel_requested": True}
        if resume.get("requestId") != pending.get("requestId"):
            return {}
        return {"citizen_input_ready": pending.get("requestId")}

    async def recovery(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        plan = _plan(state)
        err = state.get("last_error") or {}
        idx = int(err.get("stepIndex", state.get("current_step", 0)))
        step = plan.steps[idx]
        sid = state.get("browser_session_id")
        obs: Observation | None = None
        if sid and err.get("code") not in ("BROWSER_CRASHED", "SESSION_NOT_FOUND"):
            try:
                obs = await deps.browser.observe(sid, screenshot=False)
            except BrowserUnavailable:
                err = {**err, "code": "BROWSER_CRASHED"}
        attempts = dict(state.get("step_attempts") or {})
        step_attempts = attempts.get(step.id, 0) + 1
        task_attempts = state.get("recovery_attempts", 0) + 1
        decision = recovery_agent.decide(
            error=err, step_index=idx, plan=plan, step_attempts=step_attempts - 1, task_attempts=task_attempts - 1,
            max_per_step=s.max_recovery_attempts_per_step, max_per_task=s.max_recovery_attempts_per_task,
            observation=obs, session_expired=bool(err.get("sessionExpired")),
        )
        if decision.action == "RETRY_STEP" and not decision.selector_overrides and obs is not None and deps.llm.available \
                and err.get("code") in ("SELECTOR_NOT_FOUND", "EXPECTATION_FAILED"):
            missing = [f.label for f in step.fields if f.label not in [x.label for x in obs.form_fields]]
            mapped = await recovery_agent.llm_remap(deps.llm, step, obs, missing) if missing else {}
            if mapped:
                by_label = {f.label: f.key for f in step.fields}
                decision.selector_overrides = {step.id: {by_label[k]: v for k, v in mapped.items() if k in by_label}}
        RECOVERIES.labels(decision.reason.value, decision.action).inc()
        await em.emit(EventType.RECOVERY_STARTED, AgentName.RECOVERY_AGENT, decision.explanation, step=step.id,
                      metadata={"reason": decision.reason.value, "attempt": task_attempts, "maxAttempts": s.max_recovery_attempts_per_task,
                                "errorCode": err.get("code"), "action": decision.action})
        if decision.action == "FAIL":
            return em.update(last_error=None, recovery_attempts=task_attempts, final=_final(
                TaskStatus.FAILED, ApplicationStatus.FAILED, code=err.get("code") or "RECOVERY_FAILED", retryable=decision.retryable,
                message=f"{decision.explanation} Last error: {err.get('message', '')}"))
        attempts[step.id] = step_attempts
        upd = em.update(last_error=None, recovery_attempts=task_attempts, step_attempts=attempts)
        if decision.selector_overrides:
            merged = {k: dict(v) for k, v in (state.get("selector_overrides") or {}).items()}
            for sid_, ov in decision.selector_overrides.items():
                merged.setdefault(sid_, {}).update(ov)
            upd["selector_overrides"] = merged
        if decision.action == "REAUTHENTICATE":
            if state.get("reauth_count", 0) >= 2:
                upd["final"] = _final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="SESSION_EXPIRED", retryable=True,
                                      message="The portal session kept expiring.")
                return upd
            upd.update(current_step=0, reauth_count=state.get("reauth_count", 0) + 1, step_attempts={})
            await em.emit(EventType.EXECUTION_PAUSED, AgentName.RECOVERY_AGENT, "Signing in again; you will be asked for a new OTP.", step=step.id)
            upd["event_counter"] = em.counter
        elif decision.action == "RESTART_SESSION":
            if sid:
                await deps.browser.close_session(sid)
            upd.update(current_step=0, browser_session_id=None)
        else:
            upd["current_step"] = idx
            if decision.backoff_seconds:
                await asyncio.sleep(min(decision.backoff_seconds, 15))
        return upd

    # --------------------------------------------------------------------- submission & verification
    async def submission(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        plan = _plan(state)
        idx = plan.index_of("SUBMIT")
        step = plan.steps[idx]
        aid = (state.get("granted_approvals") or {}).get(ApprovalKind.FINAL_SUBMISSION.value)
        token = deps.vault.get(aid or "")
        if aid is None or token is None:
            granted = dict(state.get("granted_approvals") or {})
            granted.pop(ApprovalKind.FINAL_SUBMISSION.value, None)
            return em.update(granted_approvals=granted, current_step=idx)
        sid = state.get("browser_session_id")
        if not sid or not await deps.browser.has_session(sid):
            # Nothing consequential has happened yet: safe to rebuild the session and replay reversible steps.
            return em.update(browser_session_id=None, current_step=0, recovery_attempts=state.get("recovery_attempts", 0) + 1)
        first = await deps.db.begin_submission(aid, state["task_id"]) if deps.db else _mem_begin(deps, aid)
        if not first:
            await em.emit(EventType.SUBMISSION_UNCERTAIN, AgentName.VERIFICATION_AGENT,
                          "A submission was already attempted for this approval; CIVIX will not click submit again and is checking the outcome.")
            return em.update(submission={"approvalId": aid, "attempted": True, "clicked": None}, current_step=plan.index_of("VERIFY_RECEIPT"))
        res = await _act(sid, ActionRequest(action="SUBMIT", target=step.continue_button, approval_token=token, value=plan.submission_digest,
                                            expected=step.expected, timeout_ms=int(s.browser_step_timeout_seconds * 1000)))
        deps.vault.discard(aid)
        if res.error and res.error.code == "APPROVAL_INVALID":
            if deps.db:
                await deps.db.finish_submission(aid, "REFUSED")
            return em.update(final=_final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="APPROVAL_INVALID",
                                          message="The browser worker refused the submission because the approval could not be verified. Nothing was submitted."))
        clicked = res.ok
        if deps.db:
            await deps.db.finish_submission(aid, "CLICKED" if clicked else (res.error.code if res.error else "UNKNOWN"))
        obs = await _obs_summary(deps, state["task_id"], res.observation)
        if clicked:
            await em.emit(EventType.SUBMISSION_COMPLETED, AgentName.BROWSER_AGENT, "Clicked the portal's submit button once. Verifying the acknowledgement…",
                          step=step.id, metadata={"approvalId": aid, "navigationVerified": res.verified,
                                                  "screenshotId": obs["screenshotId"] if obs else None}, observation=obs)
        return em.update(submission={"approvalId": aid, "attempted": True, "clicked": clicked, "error": res.error.code if res.error else None},
                         current_step=plan.index_of("VERIFY_RECEIPT"), browser_observation=obs or state.get("browser_observation"))

    async def verification(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        svc = deps.registry().get(state["service_code"])
        assert svc is not None and svc.workflow is not None
        spec = svc.workflow.receipt
        sid = state.get("browser_session_id")
        reference: str | None = None
        ack: str | None = None
        cross_checked = False
        obs_summary = None
        try:
            if sid and await deps.browser.has_session(sid):
                raw = await deps.browser.read_text(sid, spec.target)
                if raw and re.fullmatch(spec.pattern, raw.strip()):
                    reference = raw.strip()
                if spec.acknowledgement_target:
                    ack = await deps.browser.read_text(sid, spec.acknowledgement_target)
                obs_summary = await _obs_summary(deps, state["task_id"], await deps.browser.observe(sid))
                if reference and spec.status_url_template:
                    res = await _act(sid, ActionRequest(action="NAVIGATE", url=spec.status_url_template.replace("{reference}", reference),
                                                        expected=None, capture_screenshot=False))
                    if res.ok and spec.status_text_visible:
                        from app.contracts.registry import Expectation
                        cross_checked = await deps.browser.matches(sid, Expectation(text_visible=spec.status_text_visible))
        except BrowserUnavailable:
            pass
        if reference is None:
            await em.emit(EventType.SUBMISSION_UNCERTAIN, AgentName.VERIFICATION_AGENT,
                          "CIVIX could not observe an acknowledgement with a reference number. Do NOT resubmit; check the portal yourself first.",
                          metadata={"clicked": (state.get("submission") or {}).get("clicked")},
                          application_status=ApplicationStatus.SUBMISSION_UNCERTAIN)
            return em.update(final=_final(TaskStatus.COMPLETED, ApplicationStatus.SUBMISSION_UNCERTAIN, code="SUBMISSION_UNCERTAIN",
                                          message="Submission outcome is uncertain — no acknowledgement was observed.",
                                          required_action={"type": "REVIEW_UNCERTAIN", "message": "Check the portal for your application status before trying again."}))
        receipt_body = {
            "taskId": state["task_id"], "applicationId": state["application_id"], "referenceNumber": reference,
            "acknowledgementText": (ack or "")[:4000], "portalUrl": (obs_summary or {}).get("url") or "",
            "verificationMethod": "DOM_TEXT_MATCH+STATUS_PAGE" if cross_checked else "DOM_TEXT_MATCH",
            "evidence": {"pattern": spec.pattern, "target": spec.target.wire(), "statusPageConfirmed": cross_checked},
            "screenshotId": (state.get("browser_observation") or {}).get("screenshotId"),
            "isTestEnvironment": svc.is_test_service,
        }
        receipt_id = await deps.api.post_receipt(receipt_body)
        await em.emit(EventType.RECEIPT_VERIFIED, AgentName.VERIFICATION_AGENT,
                      f"Acknowledgement verified. Reference number {reference}." + (" (Test environment — not a government submission.)" if svc.is_test_service else ""),
                      metadata={"receiptId": receipt_id, "referenceNumber": reference, "isTestEnvironment": svc.is_test_service,
                                "statusPageConfirmed": cross_checked}, application_status=ApplicationStatus.SUBMITTED)
        return em.update(receipt={"receiptId": receipt_id, "referenceNumber": reference, "crossChecked": cross_checked},
                         final=_final(TaskStatus.COMPLETED, ApplicationStatus.SUBMITTED, message=f"Submitted. Reference {reference}."))

    async def application_result(state: CivixState) -> dict[str, Any]:
        em = Emitter(deps, state)
        final = state.get("final")
        if state.get("cancel_requested") and (final is None or final.get("taskStatus") not in ("COMPLETED",)):
            final = _final(TaskStatus.CANCELLED, ApplicationStatus.CANCELLED, code="CANCELLED", message="Cancelled at your request. CIVIX stopped.")
        if final is None:
            final = _final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="INTERNAL_ERROR", message="The workflow ended unexpectedly.", retryable=True)
        ts = TaskStatus(final["taskStatus"])
        ev = {TaskStatus.COMPLETED: EventType.TASK_COMPLETED, TaskStatus.CANCELLED: EventType.TASK_CANCELLED}.get(ts, EventType.TASK_FAILED)
        err = None if ts is TaskStatus.COMPLETED and final.get("errorCode") is None else {"code": final.get("errorCode") or "UNKNOWN", "message": final["message"]}
        await em.emit(ev, AgentName.ORCHESTRATOR, final["message"],
                      metadata={"errorCode": final.get("errorCode"), "retryable": final.get("retryable", False),
                                "requiredAction": final.get("requiredAction"), "outcome": final["applicationStatus"]},
                      task_status=ts, application_status=ApplicationStatus(final["applicationStatus"]), error=err,
                      pending_citizen_input=None, current_step="application_result")
        sid = state.get("browser_session_id")
        if sid:
            try:
                await deps.browser.close_session(sid)
            except BrowserUnavailable:
                pass
        TASK_OUTCOMES.labels(ts.value, final["applicationStatus"]).inc()
        return em.update(final=final, execution_status=ts.value, browser_session_id=None, pending_approval=None, pending_citizen_input=None)

    # --------------------------------------------------------------------- wiring
    browser_nodes = {"browser_execution", "submission", "verification"}
    wait_nodes = {"human_approval_wait", "final_submission_wait", "citizen_input_wait"}

    def wrap(name: str, fn: Callable[[CivixState], Awaitable[dict[str, Any]]]) -> Callable[[CivixState], Awaitable[dict[str, Any]]]:
        async def node(state: CivixState) -> dict[str, Any]:
            if name != "application_result" and deps.is_cancelled(state["task_id"]):
                return {"cancel_requested": True}
            if name in wait_nodes:
                with llm_context(task_id=state["task_id"], user_id=state["user_id"]):
                    return await fn(state)
            start = time.perf_counter()
            timeout = s.browser_step_timeout_seconds * 4 if name in browser_nodes else s.node_timeout_seconds
            try:
                with llm_context(task_id=state["task_id"], user_id=state["user_id"]):
                    return await asyncio.wait_for(fn(state), timeout=timeout)
            except GraphBubbleUp:
                raise
            except TimeoutError:
                NODE_ERRORS.labels(name, "TIMEOUT").inc()
                if name == "browser_execution":
                    return {"last_error": {"code": "TIMEOUT", "message": "step timed out", "stepIndex": state.get("current_step", 0)}}
                if name == "submission":
                    return {"submission": {"attempted": True, "clicked": None, "error": "TIMEOUT"}, "current_step": _plan(state).index_of("VERIFY_RECEIPT")}
                return {"final": _final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="NODE_TIMEOUT", retryable=True, message=f"{name} timed out.")}
            except BrowserUnavailable as exc:
                NODE_ERRORS.labels(name, "BROWSER_UNAVAILABLE").inc()
                if name == "browser_execution":
                    return {"last_error": {"code": "BROWSER_CRASHED", "message": str(exc)[:200], "stepIndex": state.get("current_step", 0)},
                            "browser_session_id": None}
                if name == "submission":
                    return {"submission": {"attempted": True, "clicked": None, "error": "BROWSER_CRASHED"}, "current_step": _plan(state).index_of("VERIFY_RECEIPT")}
                return {"final": _final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="BROWSER_UNAVAILABLE", retryable=True, message="Browser worker unavailable.")}
            except ApiCallError as exc:
                NODE_ERRORS.labels(name, "API").inc()
                return {"final": _final(TaskStatus.FAILED, ApplicationStatus.FAILED, code="API_UNAVAILABLE", retryable=exc.retryable,
                                        message="CIVIX could not reach its core service.")}
            finally:
                NODE_SECONDS.labels(name).observe(time.perf_counter() - start)

        node.__name__ = name
        return node

    nodes: dict[str, Callable[[CivixState], Awaitable[dict[str, Any]]]] = {
        "intent_analysis": intent_analysis, "profile_retrieval": profile_retrieval, "service_discovery": service_discovery,
        "eligibility_evaluation": eligibility_evaluation, "document_validation": document_validation,
        "execution_planning": execution_planning, "human_approval": human_approval, "human_approval_wait": human_approval_wait,
        "browser_execution": browser_execution, "citizen_input_wait": citizen_input_wait, "recovery": recovery,
        "final_submission_approval": final_submission_approval, "final_submission_wait": final_submission_wait,
        "submission": submission, "verification": verification, "application_result": application_result,
    }
    g = StateGraph(CivixState)
    for name, fn in nodes.items():
        g.add_node(name, wrap(name, fn))

    linear = ["intent_analysis", "profile_retrieval", "service_discovery", "eligibility_evaluation", "document_validation",
              "execution_planning", "human_approval"]

    def stop(state: CivixState) -> bool:
        return bool(state.get("cancel_requested") or state.get("final"))

    def after_linear(nxt: str) -> Callable[[CivixState], str]:
        return lambda st: "application_result" if stop(st) else nxt

    for a, b in zip(linear, linear[1:], strict=False):
        g.add_conditional_edges(a, after_linear(b), [b, "application_result"])

    def r_human_approval(st: CivixState) -> str:
        if stop(st):
            return "application_result"
        return "human_approval_wait" if st.get("pending_approval") else "browser_execution"

    def r_human_wait(st: CivixState) -> str:
        if stop(st):
            return "application_result"
        return "human_approval_wait" if st.get("pending_approval") else "human_approval"

    def r_browser(st: CivixState) -> str:
        if stop(st):
            return "application_result"
        pending = st.get("pending_citizen_input")
        if pending and st.get("citizen_input_ready") != pending.get("requestId"):
            return "citizen_input_wait"
        if st.get("last_error"):
            return "recovery"
        plan = _plan(st)
        idx = st.get("current_step", 0)
        step = plan.steps[idx]
        if _step_needs_token(deps, st, step):
            return "human_approval"
        if step.action == "SUBMIT":
            aid = (st.get("granted_approvals") or {}).get(ApprovalKind.FINAL_SUBMISSION.value)
            return "submission" if aid and deps.vault.get(aid) else "final_submission_approval"
        if step.action == "VERIFY_RECEIPT":
            return "verification"
        return "browser_execution"

    def r_final_approval(st: CivixState) -> str:
        if stop(st):
            return "application_result"
        return "final_submission_wait" if st.get("pending_approval") else "submission"

    def r_final_wait(st: CivixState) -> str:
        if stop(st):
            return "application_result"
        return "final_submission_wait" if st.get("pending_approval") else "submission"

    def r_submission(st: CivixState) -> str:
        if stop(st):
            return "application_result"
        plan = _plan(st)
        step = plan.steps[st.get("current_step", 0)]
        if step.action == "VERIFY_RECEIPT":
            return "verification"
        if step.action == "SUBMIT":
            return "final_submission_approval"
        return "browser_execution"

    g.add_edge(START, "intent_analysis")
    g.add_conditional_edges("human_approval", r_human_approval, ["human_approval_wait", "browser_execution", "application_result"])
    g.add_conditional_edges("human_approval_wait", r_human_wait, ["human_approval_wait", "human_approval", "application_result"])
    g.add_conditional_edges("browser_execution", r_browser,
                            ["citizen_input_wait", "recovery", "human_approval", "submission", "final_submission_approval", "verification",
                             "browser_execution", "application_result"])
    g.add_conditional_edges("citizen_input_wait", lambda st: "application_result" if stop(st) else "browser_execution",
                            ["browser_execution", "application_result"])
    g.add_conditional_edges("recovery", lambda st: "application_result" if stop(st) else "browser_execution",
                            ["browser_execution", "application_result"])
    g.add_conditional_edges("final_submission_approval", r_final_approval, ["final_submission_wait", "submission", "application_result"])
    g.add_conditional_edges("final_submission_wait", r_final_wait, ["final_submission_wait", "submission", "application_result"])
    g.add_conditional_edges("submission", r_submission, ["verification", "final_submission_approval", "browser_execution", "application_result"])
    g.add_conditional_edges("verification", lambda st: "application_result", ["application_result"])
    g.add_edge("application_result", END)
    return g.compile(checkpointer=checkpointer)


def _mem_begin(deps: GraphDeps, approval_id: str) -> bool:
    if deps.submissions_seen is None:
        deps.submissions_seen = set()
    if approval_id in deps.submissions_seen:
        return False
    deps.submissions_seen.add(approval_id)
    return True
