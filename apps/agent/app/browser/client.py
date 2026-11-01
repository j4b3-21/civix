"""BrowserClient abstraction used by the Browser Agent. Production: HTTP to the isolated browser-worker container.
Tests and single-process development: in-process LocalBrowserClient over the same BrowserManager."""

from __future__ import annotations

from typing import Protocol

import httpx

from app.browser.manager import BrowserActionFailure, BrowserManager
from app.contracts.browser import ActionRequest, ActionResult, Observation
from app.contracts.registry import Expectation, Target


class BrowserUnavailable(Exception):
    pass


class BrowserClient(Protocol):
    async def create_session(self, task_id: str, allowed_domains: list[str]) -> str: ...
    async def has_session(self, session_id: str) -> bool: ...
    async def observe(self, session_id: str, screenshot: bool = True) -> Observation: ...
    async def act(self, session_id: str, req: ActionRequest) -> ActionResult: ...
    async def matches(self, session_id: str, exp: Expectation) -> bool: ...
    async def read_text(self, session_id: str, target: Target) -> str | None: ...
    async def stage_file(self, session_id: str, document_id: str, file_name: str, content: bytes, approval_token: str, digest: str) -> None: ...
    async def close_session(self, session_id: str) -> None: ...


class LocalBrowserClient:
    def __init__(self, manager: BrowserManager) -> None:
        self.manager = manager

    async def create_session(self, task_id: str, allowed_domains: list[str]) -> str:
        return await self.manager.create_session(task_id, allowed_domains)

    async def has_session(self, session_id: str) -> bool:
        if not self.manager.has_session(session_id):
            return False
        try:
            self.manager.get(session_id)
            return True
        except BrowserActionFailure:
            return False

    async def observe(self, session_id: str, screenshot: bool = True) -> Observation:
        try:
            return await self.manager.observe(session_id, screenshot=screenshot)
        except BrowserActionFailure as f:
            raise BrowserUnavailable(f.code) from f

    async def act(self, session_id: str, req: ActionRequest) -> ActionResult:
        return await self.manager.act(session_id, req)

    async def matches(self, session_id: str, exp: Expectation) -> bool:
        try:
            return await self.manager.matches(session_id, exp)
        except BrowserActionFailure as f:
            raise BrowserUnavailable(f.code) from f

    async def read_text(self, session_id: str, target: Target) -> str | None:
        try:
            return await self.manager.read_text(session_id, target)
        except BrowserActionFailure as f:
            raise BrowserUnavailable(f.code) from f

    async def stage_file(self, session_id: str, document_id: str, file_name: str, content: bytes, approval_token: str, digest: str) -> None:
        self.manager.stage_file(session_id, document_id, file_name, content, approval_token, digest)

    async def close_session(self, session_id: str) -> None:
        await self.manager.close_session(session_id)


class HttpBrowserClient:
    def __init__(self, base_url: str, token: str, timeout: float = 130.0) -> None:
        self._http = httpx.AsyncClient(base_url=base_url, timeout=timeout, headers={"Authorization": f"Bearer {token}"})

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _req(self, method: str, url: str, **kw) -> httpx.Response:
        try:
            r = await self._http.request(method, url, **kw)
        except httpx.HTTPError as exc:
            raise BrowserUnavailable(f"browser worker unreachable: {exc.__class__.__name__}") from exc
        if r.status_code == 404:
            raise BrowserUnavailable("SESSION_NOT_FOUND")
        if r.status_code >= 500:
            raise BrowserUnavailable(f"browser worker error {r.status_code}")
        return r

    async def create_session(self, task_id: str, allowed_domains: list[str]) -> str:
        r = await self._req("POST", "/internal/v1/sessions", json={"taskId": task_id, "allowedDomains": allowed_domains})
        r.raise_for_status()
        return r.json()["sessionId"]

    async def has_session(self, session_id: str) -> bool:
        try:
            r = await self._req("GET", f"/internal/v1/sessions/{session_id}")
        except BrowserUnavailable:
            return False
        return r.status_code == 200

    async def observe(self, session_id: str, screenshot: bool = True) -> Observation:
        r = await self._req("GET", f"/internal/v1/sessions/{session_id}/observe", params={"screenshot": str(screenshot).lower()})
        r.raise_for_status()
        return Observation.model_validate(r.json())

    async def act(self, session_id: str, req: ActionRequest) -> ActionResult:
        r = await self._req("POST", f"/internal/v1/sessions/{session_id}/actions", json=req.wire())
        r.raise_for_status()
        return ActionResult.model_validate(r.json())

    async def matches(self, session_id: str, exp: Expectation) -> bool:
        r = await self._req("POST", f"/internal/v1/sessions/{session_id}/matches", json=exp.wire())
        r.raise_for_status()
        return bool(r.json()["matches"])

    async def read_text(self, session_id: str, target: Target) -> str | None:
        r = await self._req("POST", f"/internal/v1/sessions/{session_id}/text", json=target.wire())
        r.raise_for_status()
        return r.json().get("text")

    async def stage_file(self, session_id: str, document_id: str, file_name: str, content: bytes, approval_token: str, digest: str) -> None:
        r = await self._req(
            "POST", f"/internal/v1/sessions/{session_id}/files",
            data={"documentId": document_id, "approvalToken": approval_token, "digest": digest},
            files={"file": (file_name, content)},
        )
        if r.status_code == 403:
            raise BrowserUnavailable("APPROVAL_INVALID")
        r.raise_for_status()

    async def close_session(self, session_id: str) -> None:
        try:
            await self._req("DELETE", f"/internal/v1/sessions/{session_id}")
        except BrowserUnavailable:
            pass
