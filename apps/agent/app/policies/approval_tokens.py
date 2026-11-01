"""Approval token verification (docs/AGENT_CONTRACTS.md §4). Spring signs; the agent and the browser worker verify."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass

from app.contracts.common import ApprovalKind


class ApprovalTokenError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class ApprovalClaims:
    approval_id: str
    task_id: str
    application_id: str
    user_id: str
    kind: ApprovalKind
    digest: str
    expires_at: int


def _b64e(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64d(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def sign(secret: str, claims: ApprovalClaims) -> str:
    """Used by tests and tooling; production tokens are issued by apps/api."""
    payload = {
        "aid": claims.approval_id,
        "tid": claims.task_id,
        "app": claims.application_id,
        "uid": claims.user_id,
        "kind": claims.kind.value,
        "dig": claims.digest,
        "exp": claims.expires_at,
    }
    p = _b64e(json.dumps(payload, separators=(",", ":")).encode())
    sig = _b64e(hmac.new(secret.encode(), p.encode(), hashlib.sha256).digest())
    return f"{p}.{sig}"


def verify(
    secret: str,
    token: str,
    *,
    expected_task_id: str,
    expected_kind: ApprovalKind,
    expected_digest: str,
    expected_approval_id: str | None = None,
    now: float | None = None,
) -> ApprovalClaims:
    if not secret:
        raise ApprovalTokenError("APPROVAL_SECRET_MISSING", "approval signing secret is not configured")
    try:
        p, sig = token.split(".", 1)
    except ValueError as exc:
        raise ApprovalTokenError("APPROVAL_MALFORMED", "malformed approval token") from exc
    expected_sig = _b64e(hmac.new(secret.encode(), p.encode(), hashlib.sha256).digest())
    if not hmac.compare_digest(expected_sig, sig):
        raise ApprovalTokenError("APPROVAL_SIGNATURE_INVALID", "approval token signature is invalid")
    try:
        data = json.loads(_b64d(p))
        claims = ApprovalClaims(
            approval_id=str(data["aid"]),
            task_id=str(data["tid"]),
            application_id=str(data["app"]),
            user_id=str(data["uid"]),
            kind=ApprovalKind(data["kind"]),
            digest=str(data["dig"]),
            expires_at=int(data["exp"]),
        )
    except (KeyError, ValueError, TypeError) as exc:
        raise ApprovalTokenError("APPROVAL_MALFORMED", "approval token payload is invalid") from exc
    if claims.expires_at <= (now if now is not None else time.time()):
        raise ApprovalTokenError("APPROVAL_EXPIRED", "approval has expired")
    if claims.task_id != expected_task_id:
        raise ApprovalTokenError("APPROVAL_TASK_MISMATCH", "approval belongs to a different task")
    if claims.kind is not expected_kind:
        raise ApprovalTokenError("APPROVAL_KIND_MISMATCH", f"approval is for {claims.kind}, not {expected_kind}")
    if expected_approval_id is not None and claims.approval_id != expected_approval_id:
        raise ApprovalTokenError("APPROVAL_ID_MISMATCH", "approval id does not match the pending approval")
    if not hmac.compare_digest(claims.digest, expected_digest):
        raise ApprovalTokenError("APPROVAL_DIGEST_MISMATCH", "the data to be submitted differs from what was approved")
    return claims


def payload_digest(*, fields: dict[str, dict[str, object]], documents: list[str], service_code: str, data_version: int) -> str:
    canonical = json.dumps(
        {"fields": fields, "documents": sorted(documents), "serviceCode": service_code, "dataVersion": data_version},
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        default=str,
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()
