"""End-to-end: the full CIVIX workflow against the local CIVIX Test Portal (authorized test environment) in real Chromium.

Covers the happy path and the failure catalogue from the build brief: network timeout, changed layout, expired
session, browser crash, wrong OTP, rejected approval, uncertain submission / missing acknowledgement, data change.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

import pytest
import pytest_asyncio

from app.browser.client import LocalBrowserClient
from app.browser.manager import BrowserManager

from .fakes import SECRET
from .harness import Harness, citizen, documents, make_harness, start
from .portal_fixture import PortalServer

pytestmark = [pytest.mark.browser, pytest.mark.e2e]
MOBILE = "9876543210"


@pytest.fixture(scope="module")
def portal():
    p = PortalServer().start()
    yield p
    p.stop()


@pytest_asyncio.fixture
async def browser():
    m = BrowserManager(approval_secret=SECRET, private_host_allowlist=frozenset({"localhost", "127.0.0.1"}))
    await m.start()
    yield m
    await m.stop()


@pytest.fixture
def registry_for(portal):
    from app.retrieval.registry import load_registry

    from .conftest import REGISTRY_PATH

    return load_registry(REGISTRY_PATH, env={"TEST_PORTAL_URL": portal.base_url})


Hook = Callable[[Harness, str, dict[str, Any]], Any]


async def drive(h: Harness, tid: str, portal: PortalServer, *, max_rounds: int = 25, on_interrupt: Hook | None = None,
                decisions: dict[str, str] | None = None, otp_override: str | None = None, declaration: str = "ACCEPT") -> dict[str, Any]:
    decisions = decisions or {}
    for _ in range(max_rounds):
        st = await h.runner.status(tid)
        pend = st["pendingInterrupt"]
        if st["status"] not in ("WAITING_APPROVAL", "WAITING_CITIZEN") or not pend:
            return st
        if on_interrupt:
            r = on_interrupt(h, tid, pend)
            if hasattr(r, "__await__"):
                await r
        if pend["type"] == "APPROVAL":
            appr = h.api.approvals[pend["approvalId"]]
            decision = decisions.get(pend["kind"], "APPROVE")
            await h.runner.resume_approval(tid, approval_id=appr["approvalId"], decision=decision,
                                           token=h.api.token_for(appr) if decision == "APPROVE" else None)
        else:
            if pend["kind"] == "OTP":
                value = otp_override or portal.latest_otp(MOBILE)
                otp_override = None  # wrong OTP only once
            else:
                value = declaration
            await h.runner.resume_citizen_input(tid, request_id=pend["requestId"], value=value)
        await h.runner.wait_idle(tid, timeout=180)
    raise AssertionError("workflow did not finish")


def harness(registry_for, browser) -> Harness:
    return make_harness(registry_for, browser=LocalBrowserClient(browser), browser_step_timeout_seconds=15)


async def test_happy_path_submits_and_verifies_receipt(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)
    st = await drive(h, tid, portal)
    assert st["status"] == "COMPLETED", h.api.types()
    types = h.api.types()
    for t in ("PLAN_CREATED", "APPROVAL_REQUIRED", "APPROVAL_GRANTED", "BROWSER_STARTED", "CITIZEN_INPUT_REQUIRED",
              "SUBMISSION_APPROVAL_REQUIRED", "SUBMISSION_COMPLETED", "RECEIPT_VERIFIED", "TASK_COMPLETED"):
        assert t in types, t
    assert types.index("SUBMISSION_APPROVAL_REQUIRED") < types.index("SUBMISSION_COMPLETED")
    assert [a["kind"] for a in h.api.approvals.values()] == ["PREPARATION", "DOCUMENT_UPLOAD", "FINAL_SUBMISSION"]
    assert h.api.pending("FINAL_SUBMISSION")["actionClass"] == "CONSEQUENTIAL"
    # the receipt is the portal's real reference number
    apps = portal.applications()
    assert len(apps) == 1
    ref = next(iter(apps))
    assert h.api.receipts[0]["referenceNumber"] == ref and h.api.receipts[0]["isTestEnvironment"] is True
    assert h.api.receipts[0]["verificationMethod"] == "DOM_TEXT_MATCH+STATUS_PAGE"
    submitted = apps[ref]["data"]
    assert submitted["full_name"] == "Asha Verma" and submitted["income"] == 240000 and submitted["state"] == "IN-KA"
    assert submitted["gender"] == "Female" and submitted["course_level"] == "Undergraduate"
    # screenshots were stored for the control room
    assert any(k.startswith(f"tasks/{tid}/screenshots/") for k in h.storage.objects)
    # OTP never appears in events
    import json
    otp = portal.latest_otp(MOBILE)
    assert otp not in json.dumps(h.api.events)
    final = h.api.last("TASK_COMPLETED")
    assert final["applicationStatus"] == "SUBMITTED" and final["taskStatus"] == "COMPLETED"


async def test_final_submission_rejected_submits_nothing(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)
    st = await drive(h, tid, portal, decisions={"FINAL_SUBMISSION": "REJECT"})
    assert st["status"] == "CANCELLED"
    assert portal.applications() == {}
    assert "SUBMISSION_COMPLETED" not in h.api.types()


async def test_changed_layout_is_recovered(portal, browser, registry_for):
    portal.reset()
    portal.faults(variant="B")
    h = harness(registry_for, browser)
    tid = await start(h)
    st = await drive(h, tid, portal)
    assert st["status"] == "COMPLETED", h.api.events[-3:]
    reasons = [e["metadata"]["reason"] for e in h.api.events if e["type"] == "RECOVERY_STARTED"]
    assert "LAYOUT_CHANGED" in reasons
    data = next(iter(portal.applications().values()))["data"]
    assert data["full_name"] == "Asha Verma" and data["dob"] == "2005-08-14"


async def test_wrong_otp_asks_citizen_again(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)
    st = await drive(h, tid, portal, otp_override="000000")
    assert st["status"] == "COMPLETED"
    otp_requests = [e for e in h.api.events if e["type"] == "CITIZEN_INPUT_REQUIRED" and e["metadata"]["kind"] == "OTP"]
    assert len(otp_requests) == 2 and "Incorrect OTP" in otp_requests[1]["metadata"]["prompt"]


async def test_declaration_declined_cancels(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)
    st = await drive(h, tid, portal, declaration="no")
    assert st["status"] == "CANCELLED" and portal.applications() == {}


async def test_expired_session_reauthenticates(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)
    expired = {"done": False}

    def hook(h_: Harness, tid_: str, pend: dict[str, Any]) -> None:
        if pend["type"] == "CITIZEN_INPUT" and pend["kind"] == "DECLARATION" and not expired["done"]:
            portal.expire_sessions()
            expired["done"] = True

    st = await drive(h, tid, portal, on_interrupt=hook)
    assert st["status"] == "COMPLETED", h.api.events[-4:]
    reasons = [e["metadata"]["reason"] for e in h.api.events if e["type"] == "RECOVERY_STARTED"]
    assert "SESSION_EXPIRED" in reasons
    otp_requests = [e for e in h.api.events if e["type"] == "CITIZEN_INPUT_REQUIRED" and e["metadata"]["kind"] == "OTP"]
    assert len(otp_requests) == 2


async def test_transient_server_errors_are_retried(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)

    def hook(h_: Harness, tid_: str, pend: dict[str, Any]) -> None:
        if pend["type"] == "APPROVAL" and pend["kind"] == "DOCUMENT_UPLOAD":
            portal.faults(fail_next=1)

    st = await drive(h, tid, portal, on_interrupt=hook)
    assert st["status"] == "COMPLETED"
    assert any(e["type"] == "RECOVERY_STARTED" and e["metadata"]["reason"] == "NETWORK" for e in h.api.events)


async def test_browser_crash_restarts_session(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)
    crashed = {"done": False}

    async def hook(h_: Harness, tid_: str, pend: dict[str, Any]) -> None:
        if pend["type"] == "CITIZEN_INPUT" and pend["kind"] == "DECLARATION" and not crashed["done"]:
            snap = await h_.graph.aget_state(h_.runner._config(tid_))
            await browser.crash_session_for_test(snap.values["browser_session_id"])
            crashed["done"] = True

    st = await drive(h, tid, portal, on_interrupt=hook)
    assert st["status"] == "COMPLETED", h.api.events[-4:]
    assert any(e["type"] == "RECOVERY_STARTED" and e["metadata"]["reason"] in ("BROWSER_CRASH", "INTERRUPTED") for e in h.api.events)
    assert len(portal.applications()) == 1


async def test_missing_acknowledgement_is_uncertain_not_success(portal, browser, registry_for):
    portal.reset()
    portal.faults(omit_reference=True)
    h = harness(registry_for, browser)
    tid = await start(h)
    st = await drive(h, tid, portal)
    assert st["status"] == "COMPLETED"
    assert "SUBMISSION_UNCERTAIN" in h.api.types() and "RECEIPT_VERIFIED" not in h.api.types()
    assert h.api.receipts == []
    assert h.api.last("TASK_COMPLETED")["applicationStatus"] == "SUBMISSION_UNCERTAIN"
    assert len(portal.applications()) == 1  # submitted exactly once, never retried


async def test_data_change_before_submission_blocks_submit(portal, browser, registry_for):
    portal.reset()
    h = harness(registry_for, browser)
    tid = await start(h)

    def hook(h_: Harness, tid_: str, pend: dict[str, Any]) -> None:
        if pend["type"] == "CITIZEN_INPUT" and pend["kind"] == "DECLARATION":
            h_.api.data_version = 2

    st = await drive(h, tid, portal, on_interrupt=hook)
    assert st["status"] == "FAILED"
    assert h.api.last("TASK_FAILED")["metadata"]["errorCode"] == "DATA_CHANGED"
    assert portal.applications() == {}


async def test_unrecoverable_missing_selector_fails_cleanly(portal, browser, registry_for):
    """A layout where the needed field does not exist at all must fail, not guess."""
    portal.reset()
    h = harness(registry_for, browser)
    from app.contracts.registry import FormField

    svc = registry_for.get("IN_TEST_PORTAL_SCHOLARSHIP")
    step = next(s for s in svc.workflow.steps if s.id == "personal_details")
    step.fields.append(FormField(key="passport_number", constant="X1234567", label="Passport number", input_type="text", required=True))
    try:
        tid = await start(h, ctx=citizen(), docs=await documents(h.storage))
        st = await drive(h, tid, portal)
    finally:
        step.fields.pop()
    assert st["status"] == "FAILED"
    assert portal.applications() == {}
    assert sum(1 for e in h.api.events if e["type"] == "RECOVERY_STARTED") >= 1
