"""Browser worker contracts (docs/AGENT_CONTRACTS.md §5)."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import Field

from app.contracts.common import CamelModel
from app.contracts.registry import Expectation, Target

BrowserActionType = Literal["NAVIGATE", "FILL", "SELECT", "CHECK", "CLICK", "UPLOAD", "SUBMIT", "WAIT_FOR"]


class CreateSessionRequest(CamelModel):
    task_id: str
    allowed_domains: list[str] = Field(min_length=1)
    viewport_width: int = 1280
    viewport_height: int = 900


class CreateSessionResponse(CamelModel):
    session_id: str


class FormFieldObservation(CamelModel):
    label: str
    name: str | None = None
    id: str | None = None
    tag: str
    type: str | None = None
    required: bool = False
    has_value: bool = False
    options: list[str] | None = None


class Observation(CamelModel):
    url: str
    title: str
    headings: list[str]
    alerts: list[str]
    form_fields: list[FormFieldObservation]
    buttons: list[str]
    aria_snapshot: str
    screenshot_png: str | None = None  # base64
    http_status: int | None = None


class ActionRequest(CamelModel):
    action: BrowserActionType
    target: Target | None = None
    value: str | None = None
    url: str | None = None
    document_id: str | None = None
    approval_token: str | None = None
    expected: Expectation | None = None
    timeout_ms: int = Field(default=15000, ge=100, le=120000)
    capture_screenshot: bool = True


class ActionError(CamelModel):
    code: Literal[
        "SELECTOR_NOT_FOUND",
        "AMBIGUOUS_SELECTOR",
        "TIMEOUT",
        "NETWORK_ERROR",
        "HTTP_ERROR",
        "NAVIGATION_BLOCKED",
        "EXPECTATION_FAILED",
        "VALUE_NOT_APPLIED",
        "SESSION_NOT_FOUND",
        "POLICY_VIOLATION",
        "APPROVAL_INVALID",
        "UPLOAD_NOT_STAGED",
        "BROWSER_CRASHED",
        "INVALID_REQUEST",
    ]
    message: str
    details: dict[str, Any] = Field(default_factory=dict)


class ActionResult(CamelModel):
    ok: bool
    verified: bool
    resolved_by: str | None = None
    observation: Observation | None = None
    error: ActionError | None = None
