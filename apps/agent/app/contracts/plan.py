"""Typed agent outputs and the execution plan (all agents exchange these Pydantic contracts)."""

from __future__ import annotations

from typing import Any

from pydantic import Field

from app.contracts.common import ActionClass, ApprovalKind, CamelModel, CitizenInputKind, DocumentCategory, RecoveryReason
from app.contracts.registry import Expectation, RetryPolicy, StepAction, Target


class ProfileReport(CamelModel):
    available: list[str]
    missing: list[str]
    unverified: list[str]
    provenance: dict[str, dict[str, Any]]


class ServiceCandidate(CamelModel):
    service_code: str
    name: str
    department: str
    jurisdiction: dict[str, Any]
    sources: list[dict[str, str]]
    retrieved_at: str
    score: float = 0.0
    match_reasons: list[str] = Field(default_factory=list)


class DocumentCheck(CamelModel):
    satisfied: list[dict[str, Any]]
    missing_mandatory: list[dict[str, Any]]
    unreviewed: list[str]
    review_required: bool


class PlannedField(CamelModel):
    key: str
    label: str
    alternate_labels: list[str] = Field(default_factory=list)
    name: str | None = None
    input_type: str
    value: str
    profile_field: str | None = None


class PlannedDocument(CamelModel):
    document_id: str
    category: DocumentCategory
    label: str
    alternate_labels: list[str] = Field(default_factory=list)
    file_name: str
    storage_key: str
    mime_type: str


class PlannedStep(CamelModel):
    id: str
    title: str
    action: StepAction
    action_class: ActionClass
    approval: ApprovalKind | None
    url: str | None = None
    fields: list[PlannedField] = Field(default_factory=list)
    documents: list[PlannedDocument] = Field(default_factory=list)
    input_kind: CitizenInputKind | None = None
    prompt: str | None = None
    input_field: Target | None = None
    continue_button: Target | None = None
    precondition: Expectation | None = None
    expected: Expectation | None = None
    review_fields: list[str] = Field(default_factory=list)
    retry: RetryPolicy = Field(default_factory=RetryPolicy)


class ExecutionPlan(CamelModel):
    service_code: str
    service_version: int
    data_version: int
    allowed_domains: list[str]
    steps: list[PlannedStep]
    preparation_digest: str
    upload_digest: str
    submission_digest: str
    session_expired_markers: Expectation | None = None

    def fill_values(self) -> dict[str, dict[str, str]]:
        return {s.id: {f.key: f.value for f in s.fields} for s in self.steps if s.action == "FILL_FORM"}

    def document_ids(self) -> list[str]:
        return sorted({d.document_id for s in self.steps for d in s.documents})

    def requires(self, kind: ApprovalKind) -> bool:
        return any(s.approval is kind for s in self.steps)

    def index_of(self, action: str) -> int:
        return next(i for i, s in enumerate(self.steps) if s.action == action)


class PlanningResult(CamelModel):
    plan: ExecutionPlan | None
    missing_fields: list[str]
    missing_documents: list[str]


class RecoveryDecision(CamelModel):
    reason: RecoveryReason
    action: str  # RETRY_STEP | RESTART_SESSION | REAUTHENTICATE | FAIL
    retryable: bool
    restart_from_step: int | None = None
    selector_overrides: dict[str, dict[str, str]] = Field(default_factory=dict)
    backoff_seconds: float = 0.0
    explanation: str
