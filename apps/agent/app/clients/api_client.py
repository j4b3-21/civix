"""Callbacks from the agent to the core API (Spring) — docs/AGENT_CONTRACTS.md §2. The agent never writes business tables."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

import httpx
import structlog

log = structlog.get_logger("civix.api_client")


class ApiCallError(Exception):
    def __init__(self, status: int | None, message: str) -> None:
        super().__init__(message)
        self.status = status

    @property
    def retryable(self) -> bool:
        return self.status is None or self.status >= 500 or self.status == 429


@dataclass(frozen=True)
class CreatedApproval:
    approval_id: str
    expires_at: str


class ApiClient(Protocol):
    async def post_events(self, events: list[dict[str, Any]]) -> None: ...
    async def create_approval(self, body: dict[str, Any]) -> CreatedApproval: ...
    async def post_receipt(self, body: dict[str, Any]) -> str: ...
    async def get_task_context(self, task_id: str) -> dict[str, Any]: ...


class HttpApiClient:
    def __init__(self, base_url: str, token: str, timeout: float = 20.0) -> None:
        self._http = httpx.AsyncClient(base_url=base_url, timeout=timeout, headers={"Authorization": f"Bearer {token}"})

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _call(self, method: str, path: str, **kw: Any) -> httpx.Response:
        try:
            r = await self._http.request(method, path, **kw)
        except httpx.HTTPError as exc:
            raise ApiCallError(None, f"api unreachable: {exc.__class__.__name__}") from exc
        if r.status_code >= 400:
            raise ApiCallError(r.status_code, f"{method} {path} -> {r.status_code}: {r.text[:300]}")
        return r

    async def post_events(self, events: list[dict[str, Any]]) -> None:
        await self._call("POST", "/internal/v1/agent-events", json={"events": events})

    async def create_approval(self, body: dict[str, Any]) -> CreatedApproval:
        r = await self._call("POST", "/internal/v1/approvals", json=body)
        data = r.json()
        return CreatedApproval(approval_id=data["approvalId"], expires_at=data["expiresAt"])

    async def post_receipt(self, body: dict[str, Any]) -> str:
        r = await self._call("POST", "/internal/v1/receipts", json=body)
        return r.json()["receiptId"]

    async def get_task_context(self, task_id: str) -> dict[str, Any]:
        r = await self._call("GET", f"/internal/v1/tasks/{task_id}/context")
        return r.json()
