"""Shared typed workflow state. Only non-secret, JSON-serialisable data lives here (it is checkpointed)."""

from __future__ import annotations

from typing import Any, TypedDict


class CivixState(TypedDict, total=False):
    # identity
    task_id: str
    application_id: str
    user_id: str
    intent: dict[str, Any]
    service_code: str
    data_version: int
    # inputs
    citizen_context: dict[str, Any]
    documents: list[dict[str, Any]]
    # agent outputs
    profile_report: dict[str, Any] | None
    discovered_services: list[dict[str, Any]]
    selected_service: dict[str, Any] | None
    eligibility: dict[str, Any] | None
    missing_information: list[str]
    execution_plan: dict[str, Any] | None
    # execution
    current_step: int
    step_attempts: dict[str, int]
    selector_overrides: dict[str, dict[str, str]]
    browser_session_id: str | None
    browser_observation: dict[str, Any] | None
    pending_approval: dict[str, Any] | None
    approval_status: dict[str, str]
    granted_approvals: dict[str, str]
    pending_citizen_input: dict[str, Any] | None
    citizen_input_ready: str | None
    citizen_input_attempts: dict[str, int]
    execution_status: str
    recovery_attempts: int
    reauth_count: int
    last_error: dict[str, Any] | None
    submission: dict[str, Any] | None
    receipt: dict[str, Any] | None
    errors: list[dict[str, Any]]
    final: dict[str, Any] | None
    cancel_requested: bool
    event_counter: int
