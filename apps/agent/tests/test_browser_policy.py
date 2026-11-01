"""Browser worker security policy against a real Chromium and the local test portal."""

from __future__ import annotations

import time
import uuid

import pytest
import pytest_asyncio

from app.browser.manager import BrowserActionFailure, BrowserManager
from app.contracts.browser import ActionRequest
from app.contracts.common import ApprovalKind
from app.contracts.registry import Expectation, Target
from app.policies.approval_tokens import ApprovalClaims, sign

from .portal_fixture import PortalServer

pytestmark = pytest.mark.browser
SECRET = "test-approval-secret-0123456789abcdef"


@pytest.fixture(scope="module")
def portal():
    p = PortalServer().start()
    yield p
    p.stop()


@pytest_asyncio.fixture
async def manager():
    m = BrowserManager(approval_secret=SECRET, private_host_allowlist=frozenset({"localhost", "127.0.0.1"}))
    await m.start()
    yield m
    await m.stop()


def token(task_id: str, kind: ApprovalKind, digest: str, exp: int | None = None, aid: str | None = None) -> str:
    return sign(SECRET, ApprovalClaims(aid or str(uuid.uuid4()), task_id, "app", "user", kind, digest, exp or int(time.time()) + 600))


async def signed_in(manager: BrowserManager, portal: PortalServer, task_id: str) -> str:
    portal.reset()
    sid = await manager.create_session(task_id, ["localhost"])
    r = await manager.act(sid, ActionRequest(action="NAVIGATE", url=f"{portal.base_url}/apply", expected=Expectation(heading_visible="Sign in")))
    assert r.ok and r.verified, r
    await manager.act(sid, ActionRequest(action="FILL", target=Target(label="Mobile number"), value="9876543210"))
    r = await manager.act(sid, ActionRequest(action="CLICK", target=Target(role="button", label="Send OTP"), expected=Expectation(heading_visible="Verify OTP")))
    assert r.verified
    await manager.act(sid, ActionRequest(action="FILL", target=Target(label="One-time password"), value=portal.latest_otp("9876543210")))
    r = await manager.act(sid, ActionRequest(action="CLICK", target=Target(role="button", label="Verify OTP"), expected=Expectation(heading_visible="Personal details")))
    assert r.verified
    return sid


async def test_observe_reports_structure_without_values(manager, portal):
    sid = await signed_in(manager, portal, "t-observe")
    await manager.act(sid, ActionRequest(action="FILL", target=Target(label="Full name"), value="Asha Verma"))
    obs = await manager.observe(sid)
    assert "Personal details" in obs.headings
    labels = {f.label: f for f in obs.form_fields}
    assert labels["Full name"].has_value is True
    assert "Asha Verma" not in obs.model_dump_json(exclude={"aria_snapshot", "screenshot_png"})
    assert obs.screenshot_png and len(obs.screenshot_png) > 1000
    assert "Save and continue" in obs.buttons


async def test_navigation_outside_allowlist_is_blocked(manager, portal):
    sid = await manager.create_session("t-nav", ["localhost"])
    r = await manager.act(sid, ActionRequest(action="NAVIGATE", url="https://example.com/"))
    assert not r.ok and r.error.code == "NAVIGATION_BLOCKED"
    r = await manager.act(sid, ActionRequest(action="NAVIGATE", url="file:///etc/passwd"))
    assert not r.ok and r.error.code == "NAVIGATION_BLOCKED"


async def test_subresource_requests_outside_allowlist_are_aborted(manager, portal):
    sid = await manager.create_session("t-sub", ["localhost"])
    await manager.act(sid, ActionRequest(action="NAVIGATE", url=f"{portal.base_url}/"))
    s = manager.get(sid)
    result = await s.page.evaluate("() => fetch('https://example.com/collect').then(() => 'loaded', () => 'blocked')")
    assert result == "blocked"
    assert any("example.com" in u for u in s.blocked_requests)


async def test_metadata_ip_blocked_even_if_listed(manager):
    sid = await manager.create_session("t-ssrf", ["169.254.169.254"])
    r = await manager.act(sid, ActionRequest(action="NAVIGATE", url="http://169.254.169.254/latest/meta-data"))
    assert not r.ok and r.error.code == "NAVIGATION_BLOCKED"


async def test_consequential_click_refused_and_submit_requires_token(manager, portal):
    task = "t-submit"
    sid = await signed_in(manager, portal, task)
    s = manager.get(sid)
    await s.page.goto(f"{portal.base_url}/apply/review")
    btn = Target(role="button", label="Submit application")
    r = await manager.act(sid, ActionRequest(action="CLICK", target=btn))
    assert not r.ok and r.error.code == "POLICY_VIOLATION"
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn))
    assert r.error.code == "APPROVAL_INVALID"
    digest = "d" * 64
    wrong_kind = token(task, ApprovalKind.PREPARATION, digest)
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn, approval_token=wrong_kind, value=digest))
    assert r.error.code == "APPROVAL_INVALID" and r.error.details["code"] == "APPROVAL_KIND_MISMATCH"
    other_task = token("another-task", ApprovalKind.FINAL_SUBMISSION, digest)
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn, approval_token=other_task, value=digest))
    assert r.error.details["code"] == "APPROVAL_TASK_MISMATCH"
    expired = token(task, ApprovalKind.FINAL_SUBMISSION, digest, exp=int(time.time()) - 1)
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn, approval_token=expired, value=digest))
    assert r.error.details["code"] == "APPROVAL_EXPIRED"
    good = token(task, ApprovalKind.FINAL_SUBMISSION, digest)
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn, approval_token=good, value="e" * 64))
    assert r.error.details["code"] == "APPROVAL_DIGEST_MISMATCH"
    # valid token: the click happens (the portal rejects the incomplete application, which is fine here)
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn, approval_token=good, value=digest))
    assert r.ok
    await s.page.goto(f"{portal.base_url}/apply/review")
    r = await manager.act(sid, ActionRequest(action="SUBMIT", target=btn, approval_token=good, value=digest))
    assert r.error.details["code"] == "APPROVAL_REUSED"


async def test_upload_requires_staging_with_document_approval(manager, portal):
    task = "t-upload"
    sid = await signed_in(manager, portal, task)
    s = manager.get(sid)
    await s.page.goto(f"{portal.base_url}/apply/documents")
    doc = str(uuid.uuid4())
    r = await manager.act(sid, ActionRequest(action="UPLOAD", target=Target(label="Income certificate"), document_id=doc))
    assert r.error.code == "UPLOAD_NOT_STAGED"
    with pytest.raises(BrowserActionFailure) as e:
        manager.stage_file(sid, doc, "income.pdf", b"%PDF-1.4 test", token(task, ApprovalKind.FINAL_SUBMISSION, "x" * 64), "x" * 64)
    assert e.value.code == "APPROVAL_INVALID"
    manager.stage_file(sid, doc, "income.pdf", b"%PDF-1.4 test", token(task, ApprovalKind.DOCUMENT_UPLOAD, "x" * 64), "x" * 64)
    r = await manager.act(sid, ActionRequest(action="UPLOAD", target=Target(label="Income certificate"), document_id=doc))
    assert r.ok
    assert any(f.label == "Income certificate" and f.has_value for f in r.observation.form_fields)


async def test_sessions_are_isolated(manager, portal):
    sid_a = await signed_in(manager, portal, "t-a")
    sid_b = await manager.create_session("t-b", ["localhost"])
    r = await manager.act(sid_b, ActionRequest(action="NAVIGATE", url=f"{portal.base_url}/apply/personal"))
    obs = r.observation
    assert "Personal details" not in obs.headings  # B has no session cookie from A
    a_cookies = await manager.get(sid_a).context.cookies()
    b_cookies = await manager.get(sid_b).context.cookies()
    assert any(c["name"] == "tp_session" for c in a_cookies) and not any(c["name"] == "tp_session" for c in b_cookies)


async def test_crashed_session_reports_browser_crash(manager, portal):
    sid = await manager.create_session("t-crash", ["localhost"])
    await manager.act(sid, ActionRequest(action="NAVIGATE", url=f"{portal.base_url}/"))
    await manager.crash_session_for_test(sid)
    r = await manager.act(sid, ActionRequest(action="NAVIGATE", url=f"{portal.base_url}/"))
    assert r.error.code == "BROWSER_CRASHED"


async def test_http_5xx_is_reported(manager, portal):
    portal.reset()
    sid = await manager.create_session("t-503", ["localhost"])
    portal.faults(fail_next=1)
    r = await manager.act(sid, ActionRequest(action="NAVIGATE", url=f"{portal.base_url}/apply"))
    assert r.error.code == "HTTP_ERROR"
    portal.reset()
