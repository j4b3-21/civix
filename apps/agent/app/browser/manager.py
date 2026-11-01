"""Isolated Playwright browser sessions with domain allowlisting, upload staging and approval-gated submission.

Runs inside the browser-worker process. Every citizen task gets its own BrowserContext (separate cookies, storage, cache).
Policies enforced here are independent of the agent: even a buggy or compromised agent cannot navigate outside the
allowlist, upload a file that was not staged under a DOCUMENT_UPLOAD approval, or click a consequential control
without a valid FINAL_SUBMISSION token.
"""

from __future__ import annotations

import asyncio
import base64
import contextlib
import re
import shutil
import tempfile
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

import structlog
from playwright.async_api import Browser, BrowserContext, Error as PlaywrightError, Locator, Page, Playwright, async_playwright
from playwright.async_api import TimeoutError as PlaywrightTimeout

from app.contracts.browser import ActionError, ActionRequest, ActionResult, FormFieldObservation, Observation
from app.contracts.common import ApprovalKind
from app.contracts.registry import Expectation, Target
from app.policies import approval_tokens
from app.policies.actions import is_consequential_control
from app.policies.domains import DomainPolicy, DomainPolicyError

log = structlog.get_logger("civix.browser")

_OBSERVE_JS = r"""
() => {
  const clean = s => (s || '').replace(/\s+/g, ' ').trim();
  const textOf = el => clean(el.innerText || el.textContent);
  const labelFor = el => {
    if (el.labels && el.labels.length) return textOf(el.labels[0]);
    const aria = el.getAttribute('aria-label'); if (aria) return clean(aria);
    const lb = el.getAttribute('aria-labelledby');
    if (lb) { const t = document.getElementById(lb); if (t) return textOf(t); }
    return clean(el.getAttribute('placeholder') || el.name || '');
  };
  const visible = el => {
    const s = getComputedStyle(el); const r = el.getBoundingClientRect();
    return s.visibility !== 'hidden' && s.display !== 'none' && (r.width > 0 || r.height > 0);
  };
  const fields = [...document.querySelectorAll('input, select, textarea')]
    .filter(el => el.type !== 'hidden' && visible(el))
    .slice(0, 80)
    .map(el => ({
      label: labelFor(el).slice(0, 200), name: el.name || null, id: el.id || null,
      tag: el.tagName.toLowerCase(), type: el.type || null, required: !!el.required,
      hasValue: el.type === 'file' ? el.files.length > 0 : (el.type === 'checkbox' || el.type === 'radio') ? el.checked : !!el.value,
      options: el.tagName === 'SELECT' ? [...el.options].map(o => clean(o.text)).slice(0, 60) : null,
    }));
  const buttons = [...document.querySelectorAll('button, input[type=submit], input[type=button], [role=button]')]
    .filter(visible).map(b => textOf(b) || clean(b.value)).filter(Boolean).slice(0, 40);
  const headings = [...document.querySelectorAll('h1, h2, h3')].map(textOf).filter(Boolean).slice(0, 20);
  const alerts = [...document.querySelectorAll('[role=alert]')].map(textOf).filter(Boolean).slice(0, 10);
  return {fields, buttons, headings, alerts};
}
"""


class BrowserActionFailure(Exception):
    def __init__(self, error_code: str, message: str, **details: Any) -> None:
        super().__init__(message)
        self.code = error_code
        self.details = details


@dataclass
class BrowserSession:
    id: str
    task_id: str
    context: BrowserContext
    page: Page
    policy: DomainPolicy
    staging_dir: Path
    staged_files: dict[str, Path] = field(default_factory=dict)
    used_approvals: set[str] = field(default_factory=set)
    blocked_requests: list[str] = field(default_factory=list)
    console: list[str] = field(default_factory=list)
    host_cache: dict[str, bool] = field(default_factory=dict)
    last_status: int | None = None
    created_at: float = field(default_factory=time.time)
    last_used: float = field(default_factory=time.time)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class BrowserManager:
    def __init__(
        self,
        *,
        approval_secret: str,
        private_host_allowlist: frozenset[str] = frozenset(),
        headless: bool = True,
        max_sessions: int = 4,
        idle_timeout_seconds: int = 900,
        resolve_dns: bool = True,
    ) -> None:
        self._approval_secret = approval_secret
        self._private_hosts = private_host_allowlist
        self._headless = headless
        self._max_sessions = max_sessions
        self._idle_timeout = idle_timeout_seconds
        self._resolve_dns = resolve_dns
        self._pw: Playwright | None = None
        self._browser: Browser | None = None
        self._sessions: dict[str, BrowserSession] = {}
        self._reaper: asyncio.Task[None] | None = None
        self._start_lock = asyncio.Lock()

    # ------------------------------------------------------------ lifecycle
    async def start(self) -> None:
        async with self._start_lock:
            if self._browser and self._browser.is_connected():
                return
            if self._pw is None:
                self._pw = await async_playwright().start()
            self._browser = await self._pw.chromium.launch(
                headless=self._headless,
                args=["--disable-dev-shm-usage", "--no-first-run", "--disable-extensions", "--disable-background-networking",
                      "--disable-sync", "--metrics-recording-only", "--disable-default-apps"],
            )
            if self._reaper is None:
                self._reaper = asyncio.create_task(self._reap_idle())
            log.info("browser.started", headless=self._headless)

    async def stop(self) -> None:
        for sid in list(self._sessions):
            await self.close_session(sid)
        if self._reaper:
            self._reaper.cancel()
            self._reaper = None
        if self._browser:
            with contextlib.suppress(PlaywrightError):
                await self._browser.close()
            self._browser = None
        if self._pw:
            await self._pw.stop()
            self._pw = None

    @property
    def healthy(self) -> bool:
        return bool(self._browser and self._browser.is_connected())

    async def _reap_idle(self) -> None:
        while True:
            await asyncio.sleep(30)
            now = time.time()
            for sid, s in list(self._sessions.items()):
                if now - s.last_used > self._idle_timeout:
                    log.info("browser.session_reaped", session_id=sid, task_id=s.task_id)
                    await self.close_session(sid)

    # ------------------------------------------------------------ sessions
    async def create_session(self, task_id: str, allowed_domains: list[str], viewport: tuple[int, int] = (1280, 900)) -> str:
        await self.start()
        if len(self._sessions) >= self._max_sessions:
            raise BrowserActionFailure("POLICY_VIOLATION", "maximum concurrent browser sessions reached")
        assert self._browser is not None
        policy = DomainPolicy(frozenset(d.lower() for d in allowed_domains), self._private_hosts, resolve_dns=self._resolve_dns)
        context = await self._browser.new_context(
            viewport={"width": viewport[0], "height": viewport[1]},
            accept_downloads=False,
            service_workers="block",
            java_script_enabled=True,
            permissions=[],
            locale="en-IN",
            timezone_id="Asia/Kolkata",
            user_agent=None,
        )
        sid = str(uuid.uuid4())
        page = await context.new_page()
        session = BrowserSession(
            id=sid, task_id=task_id, context=context, page=page, policy=policy,
            staging_dir=Path(tempfile.mkdtemp(prefix=f"civix-{sid[:8]}-")),
        )

        async def guard(route, request) -> None:  # network-layer allowlist: every request, not only navigations
            url = request.url
            if await self._allowed(session, url):
                await route.continue_()
            else:
                session.blocked_requests.append(url[:300])
                log.warning("browser.request_blocked", session_id=sid, host=urlsplit(url).hostname)
                await route.abort("blockedbyclient")

        await context.route("**/*", guard)
        context.on("page", lambda p: asyncio.ensure_future(self._close_popup(session, p)))
        page.on("dialog", lambda d: asyncio.ensure_future(d.dismiss()))
        page.on("console", lambda m: session.console.append(f"{m.type}: {m.text[:300]}") if len(session.console) < 200 else None)

        def on_response(resp) -> None:
            if resp.request.is_navigation_request() and resp.frame == page.main_frame:
                session.last_status = resp.status

        page.on("response", on_response)
        self._sessions[sid] = session
        log.info("browser.session_created", session_id=sid, task_id=task_id, domains=sorted(policy.allowed_domains))
        return sid

    async def _close_popup(self, session: BrowserSession, p: Page) -> None:
        if p is not session.page:
            log.warning("browser.popup_closed", session_id=session.id)
            with contextlib.suppress(PlaywrightError):
                await p.close()

    async def _allowed(self, session: BrowserSession, url: str) -> bool:
        scheme = url.split(":", 1)[0].lower()
        if scheme in ("data", "blob", "about"):
            return True
        host = (urlsplit(url).hostname or "").lower()
        key = f"{scheme}://{host}"
        if key in session.host_cache:
            return session.host_cache[key]
        try:
            await asyncio.to_thread(session.policy.check_url, url)
            ok = True
        except DomainPolicyError:
            ok = False
        session.host_cache[key] = ok
        return ok

    def get(self, session_id: str) -> BrowserSession:
        s = self._sessions.get(session_id)
        if s is None:
            raise BrowserActionFailure("SESSION_NOT_FOUND", "browser session not found (expired or worker restarted)")
        if s.page.is_closed():
            raise BrowserActionFailure("BROWSER_CRASHED", "browser page is closed")
        s.last_used = time.time()
        return s

    def has_session(self, session_id: str) -> bool:
        return session_id in self._sessions

    async def close_session(self, session_id: str) -> None:
        s = self._sessions.pop(session_id, None)
        if s is None:
            return
        with contextlib.suppress(PlaywrightError):
            await s.context.close()
        shutil.rmtree(s.staging_dir, ignore_errors=True)
        log.info("browser.session_closed", session_id=session_id, task_id=s.task_id, blocked=len(s.blocked_requests))

    async def crash_session_for_test(self, session_id: str) -> None:
        """Simulates a browser crash (used by the automated failure tests only)."""
        s = self._sessions[session_id]
        await s.page.close()

    # ------------------------------------------------------------ uploads
    def stage_file(self, session_id: str, document_id: str, file_name: str, content: bytes, approval_token: str, digest: str) -> None:
        s = self.get(session_id)
        try:
            approval_tokens.verify(self._approval_secret, approval_token, expected_task_id=s.task_id,
                                   expected_kind=ApprovalKind.DOCUMENT_UPLOAD, expected_digest=digest)
        except approval_tokens.ApprovalTokenError as exc:
            raise BrowserActionFailure("APPROVAL_INVALID", str(exc), code=exc.code) from exc
        if not re.fullmatch(r"[0-9a-fA-F-]{36}", document_id):
            raise BrowserActionFailure("INVALID_REQUEST", "documentId must be a UUID")
        suffix = Path(file_name).suffix.lower()
        if suffix not in (".pdf", ".png", ".jpg", ".jpeg"):
            suffix = ".bin"
        path = s.staging_dir / f"{document_id}{suffix}"
        path.write_bytes(content)
        s.staged_files[document_id] = path

    # ------------------------------------------------------------ observation
    async def observe(self, session_id: str, *, screenshot: bool = True) -> Observation:
        s = self.get(session_id)
        async with s.lock:
            return await self._observe(s, screenshot=screenshot)

    async def _observe(self, s: BrowserSession, *, screenshot: bool) -> Observation:
        page = s.page
        with contextlib.suppress(PlaywrightTimeout, PlaywrightError):
            await page.wait_for_load_state("domcontentloaded", timeout=5000)
        data = await page.evaluate(_OBSERVE_JS)
        try:
            aria = await page.locator("body").aria_snapshot(timeout=5000)
        except (PlaywrightTimeout, PlaywrightError):
            aria = ""
        shot = None
        if screenshot:
            shot = base64.b64encode(await page.screenshot(type="png", timeout=10000)).decode("ascii")
        return Observation(
            url=page.url, title=await page.title(), headings=data["headings"], alerts=data["alerts"],
            form_fields=[FormFieldObservation.model_validate(f) for f in data["fields"]], buttons=data["buttons"],
            aria_snapshot=aria[:20000], screenshot_png=shot, http_status=s.last_status,
        )

    # ------------------------------------------------------------ actions
    async def act(self, session_id: str, req: ActionRequest) -> ActionResult:
        try:
            s = self.get(session_id)
        except BrowserActionFailure as f:
            return ActionResult(ok=False, verified=False, error=ActionError(code=f.code, message=str(f)))
        async with s.lock:
            try:
                resolved = await self._dispatch(s, req)
                if s.last_status is not None and s.last_status >= 500:
                    raise BrowserActionFailure("HTTP_ERROR", f"portal responded with HTTP {s.last_status}", status=s.last_status)
                verified = await self._check_expected(s, req.expected, req.timeout_ms) if req.expected else True
                if req.expected and not verified:
                    obs = await self._observe(s, screenshot=req.capture_screenshot)
                    return ActionResult(ok=True, verified=False, resolved_by=resolved, observation=obs,
                                        error=ActionError(code="EXPECTATION_FAILED", message="the page did not reach the expected state",
                                                          details={"expected": req.expected.wire()}))
                obs = await self._observe(s, screenshot=req.capture_screenshot)
                return ActionResult(ok=True, verified=verified, resolved_by=resolved, observation=obs)
            except BrowserActionFailure as f:
                obs = None
                if f.code not in ("BROWSER_CRASHED", "SESSION_NOT_FOUND") and not s.page.is_closed():
                    with contextlib.suppress(Exception):
                        obs = await self._observe(s, screenshot=req.capture_screenshot)
                return ActionResult(ok=False, verified=False, observation=obs, error=ActionError(code=f.code, message=str(f), details=f.details))
            except PlaywrightTimeout as exc:
                return ActionResult(ok=False, verified=False, error=ActionError(code="TIMEOUT", message=str(exc).splitlines()[0][:300]))
            except PlaywrightError as exc:
                msg = str(exc).splitlines()[0][:300]
                if s.page.is_closed() or "Target page, context or browser has been closed" in msg or "crash" in msg.lower():
                    code = "BROWSER_CRASHED"
                elif "net::ERR_BLOCKED_BY_CLIENT" in msg:
                    code = "NAVIGATION_BLOCKED"
                elif "net::" in msg:
                    code = "NETWORK_ERROR"
                else:
                    code = "INVALID_REQUEST"
                return ActionResult(ok=False, verified=False, error=ActionError(code=code, message=msg))

    async def _dispatch(self, s: BrowserSession, req: ActionRequest) -> str | None:
        page = s.page
        t = req.timeout_ms
        if req.action == "NAVIGATE":
            if not req.url:
                raise BrowserActionFailure("INVALID_REQUEST", "url is required")
            try:
                await asyncio.to_thread(s.policy.check_url, req.url)
            except DomainPolicyError as exc:
                raise BrowserActionFailure("NAVIGATION_BLOCKED", str(exc), code=exc.code) from exc
            s.last_status = None
            resp = await page.goto(req.url, wait_until="domcontentloaded", timeout=t)
            if resp is not None:
                s.last_status = resp.status
            return "url"
        if req.action == "WAIT_FOR":
            return None
        if req.target is None:
            raise BrowserActionFailure("INVALID_REQUEST", "target is required")
        loc, how = await self._resolve(page, req.target, t)
        if req.action == "FILL":
            if req.value is None:
                raise BrowserActionFailure("INVALID_REQUEST", "value is required")
            await loc.fill(req.value, timeout=t)
            if (await loc.input_value(timeout=t)) != req.value:
                raise BrowserActionFailure("VALUE_NOT_APPLIED", "the field did not accept the value")
        elif req.action == "SELECT":
            if req.value is None:
                raise BrowserActionFailure("INVALID_REQUEST", "value is required")
            try:
                await loc.select_option(label=req.value, timeout=min(t, 3000))
            except (PlaywrightTimeout, PlaywrightError):
                await loc.select_option(value=req.value, timeout=t)
            selected = await loc.evaluate("el => [el.value, el.options[el.selectedIndex] ? el.options[el.selectedIndex].text.trim() : '']")
            if req.value not in selected:
                raise BrowserActionFailure("VALUE_NOT_APPLIED", "the option was not selected")
        elif req.action == "CHECK":
            await loc.check(timeout=t)
            if not await loc.is_checked():
                raise BrowserActionFailure("VALUE_NOT_APPLIED", "checkbox is not checked")
        elif req.action == "UPLOAD":
            if not req.document_id or req.document_id not in s.staged_files:
                raise BrowserActionFailure("UPLOAD_NOT_STAGED", "document was not staged under a DOCUMENT_UPLOAD approval")
            await loc.set_input_files(str(s.staged_files[req.document_id]), timeout=t)
            n = await loc.evaluate("el => el.files ? el.files.length : 0")
            if n < 1:
                raise BrowserActionFailure("VALUE_NOT_APPLIED", "file was not attached")
        elif req.action in ("CLICK", "SUBMIT"):
            name = (await loc.evaluate("el => (el.innerText || el.value || el.getAttribute('aria-label') || '').trim()")) or ""
            if req.action == "CLICK" and is_consequential_control(name):
                raise BrowserActionFailure("POLICY_VIOLATION", f"'{name}' is a consequential control; it requires an approved SUBMIT")
            if req.action == "SUBMIT":
                self._verify_submit_token(s, req)
            s.last_status = None
            await loc.click(timeout=t)
            with contextlib.suppress(PlaywrightTimeout):
                await page.wait_for_load_state("domcontentloaded", timeout=t)
        else:
            raise BrowserActionFailure("INVALID_REQUEST", f"unsupported action {req.action}")
        return how

    def _verify_submit_token(self, s: BrowserSession, req: ActionRequest) -> None:
        if not req.approval_token or not req.value:
            raise BrowserActionFailure("APPROVAL_INVALID", "SUBMIT requires an approval token and the approved payload digest")
        try:
            claims = approval_tokens.verify(self._approval_secret, req.approval_token, expected_task_id=s.task_id,
                                            expected_kind=ApprovalKind.FINAL_SUBMISSION, expected_digest=req.value)
        except approval_tokens.ApprovalTokenError as exc:
            raise BrowserActionFailure("APPROVAL_INVALID", str(exc), code=exc.code) from exc
        if claims.approval_id in s.used_approvals:
            raise BrowserActionFailure("APPROVAL_INVALID", "this approval was already used", code="APPROVAL_REUSED")
        s.used_approvals.add(claims.approval_id)

    async def _resolve(self, page: Page, target: Target, timeout_ms: int) -> tuple[Locator, str]:
        candidates: list[tuple[str, Locator]] = []
        labels = [x for x in [target.label, *target.alternate_labels] if x]
        if target.test_id:
            candidates.append((f"testId:{target.test_id}", page.get_by_test_id(target.test_id)))
        if target.role:
            for lab in labels or ([target.name] if target.name else []):
                candidates.append((f"role:{target.role}:{lab}", page.get_by_role(target.role, name=lab, exact=True)))  # type: ignore[arg-type]
        else:
            for lab in labels:
                candidates.append((f"label:{lab}", page.get_by_label(lab, exact=True)))
        if target.placeholder:
            candidates.append((f"placeholder:{target.placeholder}", page.get_by_placeholder(target.placeholder, exact=True)))
        if target.name and not target.role:
            candidates.append((f"name:{target.name}", page.locator(f'[name="{_css_attr(target.name)}"]')))
        if target.css:
            candidates.append((f"css:{target.css}", page.locator(target.css)))
        if not candidates:
            raise BrowserActionFailure("INVALID_REQUEST", "target has no usable selector")
        deadline = time.monotonic() + min(timeout_ms, 5000) / 1000
        while True:
            ambiguous = None
            for how, loc in candidates:
                n = await loc.count()
                if n == 1:
                    return loc, how
                if n > 1:
                    vis = loc.filter(visible=True)
                    if await vis.count() == 1:
                        return vis, how + ":visible"
                    ambiguous = how
            if time.monotonic() >= deadline:
                if ambiguous:
                    raise BrowserActionFailure("AMBIGUOUS_SELECTOR", f"more than one element matches {ambiguous}")
                raise BrowserActionFailure("SELECTOR_NOT_FOUND", "no element matches the target", target=target.wire())
            await asyncio.sleep(0.25)

    async def _check_expected(self, s: BrowserSession, exp: Expectation, timeout_ms: int) -> bool:
        page = s.page
        deadline = time.monotonic() + min(timeout_ms, 10000) / 1000
        while True:
            ok = True
            if exp.url_contains and exp.url_contains not in page.url:
                ok = False
            if ok and exp.heading_visible:
                ok = await page.get_by_role("heading", name=exp.heading_visible, exact=True).count() > 0
            if ok and exp.text_visible:
                ok = await page.get_by_text(exp.text_visible, exact=False).count() > 0
            if ok or time.monotonic() >= deadline:
                return ok
            await asyncio.sleep(0.2)

    async def matches(self, session_id: str, exp: Expectation) -> bool:
        s = self.get(session_id)
        async with s.lock:
            return await self._check_expected(s, exp, 500)

    async def read_text(self, session_id: str, target: Target) -> str | None:
        s = self.get(session_id)
        async with s.lock:
            try:
                loc, _ = await self._resolve(s.page, target, 3000)
            except BrowserActionFailure:
                return None
            return (await loc.inner_text(timeout=3000)).strip()


def _css_attr(v: str) -> str:
    return v.replace("\\", "\\\\").replace('"', '\\"')
