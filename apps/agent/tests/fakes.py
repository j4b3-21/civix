"""Test doubles for the Spring callbacks. Used ONLY inside the automated test suite."""

from __future__ import annotations

import datetime as dt
import time
import uuid
from typing import Any

from app.clients.api_client import CreatedApproval
from app.contracts.common import ApprovalKind
from app.policies.approval_tokens import ApprovalClaims, sign

SECRET = "test-approval-secret-0123456789abcdef"


class FakeApi:
    def __init__(self) -> None:
        self.events: list[dict[str, Any]] = []
        self.approvals: dict[str, dict[str, Any]] = {}
        self.receipts: list[dict[str, Any]] = []
        self.data_version = 1
        self.fail_events = False

    async def post_events(self, events: list[dict[str, Any]]) -> None:
        from app.clients.api_client import ApiCallError

        if self.fail_events:
            raise ApiCallError(503, "down")
        seen = {e["eventId"] for e in self.events}
        self.events.extend(e for e in events if e["eventId"] not in seen)

    async def create_approval(self, body: dict[str, Any]) -> CreatedApproval:
        aid = str(uuid.uuid4())
        exp = dt.datetime.now(dt.UTC) + dt.timedelta(seconds=body["expiresInSeconds"])
        self.approvals[aid] = {**body, "approvalId": aid, "expiresAt": exp.isoformat()}
        return CreatedApproval(aid, exp.isoformat())

    async def post_receipt(self, body: dict[str, Any]) -> str:
        rid = str(uuid.uuid4())
        self.receipts.append({**body, "receiptId": rid})
        return rid

    async def get_task_context(self, task_id: str) -> dict[str, Any]:
        return {"dataVersion": self.data_version}

    # helpers ---------------------------------------------------------------
    def types(self) -> list[str]:
        return [e["type"] for e in self.events]

    def pending(self, kind: str) -> dict[str, Any]:
        return [a for a in self.approvals.values() if a["kind"] == kind][-1]

    def token_for(self, approval: dict[str, Any], *, exp_offset: int = 600, digest: str | None = None) -> str:
        return sign(SECRET, ApprovalClaims(approval["approvalId"], approval["taskId"], approval["applicationId"], "user-1",
                                           ApprovalKind(approval["kind"]), digest or approval["payloadDigest"], int(time.time()) + exp_offset))

    def last(self, type_: str) -> dict[str, Any]:
        return [e for e in self.events if e["type"] == type_][-1]
