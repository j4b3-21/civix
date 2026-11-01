"""Pydantic model of a service definition (mirrors service-registry/schemas/service-definition.schema.json)."""

from __future__ import annotations

import datetime as dt
import re
from typing import Any, Literal

from pydantic import Field, field_validator, model_validator

from app.contracts.common import (
    AutomationStatus,
    CamelModel,
    CitizenInputKind,
    DocumentCategory,
    JurisdictionLevel,
    ProfileFieldType,
    RuleOperator,
    Sensitivity,
    ServiceCategory,
    ServiceVerificationStatus,
)

_SNAKE = re.compile(r"^[a-z0-9_]+$")
_STATE = re.compile(r"^IN-[A-Z]{2}$")


class Jurisdiction(CamelModel):
    level: JurisdictionLevel
    states: list[str] = Field(default_factory=list)

    @field_validator("states")
    @classmethod
    def _states(cls, v: list[str]) -> list[str]:
        for s in v:
            if not _STATE.match(s):
                raise ValueError(f"invalid ISO 3166-2:IN code {s!r}")
        if len(set(v)) != len(v):
            raise ValueError("duplicate state codes")
        return v

    @model_validator(mode="after")
    def _level_states(self) -> "Jurisdiction":
        if self.level is JurisdictionLevel.STATE and not self.states:
            raise ValueError("STATE services must list at least one state")
        return self


class Deadline(CamelModel):
    date: dt.date | None
    note: str
    source_url: str
    verified_at: dt.date


class Source(CamelModel):
    id: str
    url: str
    title: str
    kind: Literal["OFFICIAL_PORTAL", "NOTIFICATION", "GUIDELINES", "FAQ", "TEST_ENVIRONMENT"]
    retrieved_at: dt.date
    publisher: str | None = None
    note: str | None = None


class EligibilityRule(CamelModel):
    id: str
    version: int = Field(ge=1)
    field: str
    operator: RuleOperator
    value: Any = None
    description: str
    source_id: str
    excerpt: str | None = Field(default=None, max_length=300)

    @model_validator(mode="after")
    def _value_shape(self) -> "EligibilityRule":
        if not _SNAKE.match(self.id) or not _SNAKE.match(self.field):
            raise ValueError("rule id and field must be snake_case")
        op = self.operator
        if op in (RuleOperator.IN, RuleOperator.NOT_IN):
            if not isinstance(self.value, list) or not self.value:
                raise ValueError(f"{op} requires a non-empty list value")
        elif op is RuleOperator.EXISTS:
            if self.value not in (None, True, False):
                raise ValueError("EXISTS value must be omitted or boolean")
        elif op in (RuleOperator.DATE_BEFORE, RuleOperator.DATE_AFTER):
            if not isinstance(self.value, (str, dt.date)):
                raise ValueError(f"{op} requires an ISO date value")
            dt.date.fromisoformat(str(self.value))
        elif self.value is None:
            raise ValueError(f"{op} requires a value")
        return self


class Eligibility(CamelModel):
    rules: list[EligibilityRule]
    not_evaluated: list[str] = Field(default_factory=list)


class RequiredDocument(CamelModel):
    category: DocumentCategory
    description: str
    mandatory: bool
    condition: str | None = None


class ProcedureStep(CamelModel):
    order: int = Field(ge=1)
    title: str
    description: str


# ---------------------------------------------------------------- workflow


class Target(CamelModel):
    label: str | None = None
    alternate_labels: list[str] = Field(default_factory=list)
    role: str | None = None
    name: str | None = None
    placeholder: str | None = None
    test_id: str | None = None
    css: str | None = None


class Expectation(CamelModel):
    url_contains: str | None = None
    text_visible: str | None = None
    heading_visible: str | None = None

    def is_empty(self) -> bool:
        return not (self.url_contains or self.text_visible or self.heading_visible)


class FormField(CamelModel):
    key: str
    profile_field: str | None = None
    constant: str | int | float | bool | None = None
    label: str
    alternate_labels: list[str] = Field(default_factory=list)
    name: str | None = None
    input_type: Literal["text", "number", "date", "select", "radio", "checkbox", "email", "tel"]
    format: str | None = None
    value_map: dict[str, str] = Field(default_factory=dict)
    required: bool

    @model_validator(mode="after")
    def _source(self) -> "FormField":
        if (self.profile_field is None) == (self.constant is None):
            raise ValueError(f"form field {self.key!r} needs exactly one of profileField or constant")
        return self


class StepDocument(CamelModel):
    category: DocumentCategory
    label: str
    alternate_labels: list[str] = Field(default_factory=list)
    required: bool


class RetryPolicy(CamelModel):
    max_attempts: int = Field(default=3, ge=1, le=5)
    backoff_seconds: float = Field(default=1.0, ge=0, le=60)


StepAction = Literal["NAVIGATE", "FILL_FORM", "CITIZEN_INPUT", "UPLOAD_DOCUMENTS", "REVIEW", "SUBMIT", "VERIFY_RECEIPT"]


class WorkflowStep(CamelModel):
    id: str
    title: str
    action: StepAction
    url: str | None = None
    fields: list[FormField] = Field(default_factory=list)
    documents: list[StepDocument] = Field(default_factory=list)
    input_kind: CitizenInputKind | None = None
    prompt: str | None = None
    input_field: Target | None = None
    continue_button: Target | None = None
    precondition: Expectation | None = None
    expected: Expectation | None = None
    review_fields: list[str] = Field(default_factory=list)
    retry: RetryPolicy = Field(default_factory=RetryPolicy)

    @model_validator(mode="after")
    def _shape(self) -> "WorkflowStep":
        a = self.action
        if a == "NAVIGATE" and not self.url:
            raise ValueError(f"step {self.id}: NAVIGATE requires url")
        if a == "FILL_FORM" and not self.fields:
            raise ValueError(f"step {self.id}: FILL_FORM requires fields")
        if a == "UPLOAD_DOCUMENTS" and not self.documents:
            raise ValueError(f"step {self.id}: UPLOAD_DOCUMENTS requires documents")
        if a == "CITIZEN_INPUT" and (self.input_kind is None or not self.prompt):
            raise ValueError(f"step {self.id}: CITIZEN_INPUT requires inputKind and prompt")
        if a == "CITIZEN_INPUT" and self.input_kind is not CitizenInputKind.PAYMENT and self.input_field is None:
            raise ValueError(f"step {self.id}: CITIZEN_INPUT requires inputField")
        if a in ("FILL_FORM", "UPLOAD_DOCUMENTS", "CITIZEN_INPUT", "SUBMIT") and self.continue_button is None:
            raise ValueError(f"step {self.id}: {a} requires continueButton")
        return self


class ReceiptSpec(CamelModel):
    target: Target
    pattern: str
    acknowledgement_target: Target | None = None
    status_url_template: str | None = None
    status_text_visible: str | None = None

    @field_validator("pattern")
    @classmethod
    def _regex(cls, v: str) -> str:
        re.compile(v)
        return v


class Workflow(CamelModel):
    adapter: str
    entry_url: str
    allowed_domains: list[str] = Field(min_length=1)
    session_expired_markers: Expectation | None = None
    receipt: ReceiptSpec
    steps: list[WorkflowStep] = Field(min_length=1)

    @model_validator(mode="after")
    def _steps(self) -> "Workflow":
        ids = [s.id for s in self.steps]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate workflow step ids")
        submits = [s for s in self.steps if s.action == "SUBMIT"]
        if len(submits) != 1:
            raise ValueError("a workflow must contain exactly one SUBMIT step")
        idx_submit = ids.index(submits[0].id)
        if not any(s.action == "REVIEW" for s in self.steps[:idx_submit]):
            raise ValueError("a REVIEW step must precede SUBMIT")
        if self.steps[-1].action != "VERIFY_RECEIPT":
            raise ValueError("the last workflow step must be VERIFY_RECEIPT")
        return self


class ServiceDefinition(CamelModel):
    schema_version: Literal["1.0"]
    code: str = Field(pattern=r"^IN_[A-Z0-9]+(_[A-Z0-9]+)+$", max_length=80)
    version: int = Field(ge=1)
    name: str = Field(min_length=3, max_length=255)
    summary: str = Field(min_length=10, max_length=500)
    description: str
    department: str
    ministry: str | None = None
    category: ServiceCategory
    tags: list[str] = Field(default_factory=list)
    jurisdiction: Jurisdiction
    official_url: str
    portal_domains: list[str]
    verification_status: ServiceVerificationStatus
    verification_note: str | None = None
    automation_status: AutomationStatus
    automation_note: str | None = None
    is_test_service: bool
    last_verified_at: dt.date | None = None
    deadline: Deadline | None = None
    benefits: list[str] = Field(default_factory=list)
    sources: list[Source] = Field(min_length=1)
    eligibility: Eligibility
    required_documents: list[RequiredDocument]
    procedure: list[ProcedureStep]
    workflow: Workflow | None = None

    @model_validator(mode="after")
    def _integrity(self) -> "ServiceDefinition":
        source_ids = {s.id for s in self.sources}
        if len(source_ids) != len(self.sources):
            raise ValueError("duplicate source ids")
        rule_ids = [r.id for r in self.eligibility.rules]
        if len(rule_ids) != len(set(rule_ids)):
            raise ValueError("duplicate rule ids")
        for r in self.eligibility.rules:
            if r.source_id not in source_ids:
                raise ValueError(f"rule {r.id} cites unknown source {r.source_id!r}")
        # Verification gates (AGENTS.md §1.2)
        if self.verification_status in (ServiceVerificationStatus.SOURCE_VERIFIED, ServiceVerificationStatus.WORKFLOW_VERIFIED):
            if self.last_verified_at is None:
                raise ValueError("verified services need lastVerifiedAt")
        if self.automation_status is AutomationStatus.ACTIVE:
            if self.verification_status is not ServiceVerificationStatus.WORKFLOW_VERIFIED:
                raise ValueError("ACTIVE automation requires WORKFLOW_VERIFIED")
            if self.is_test_service:
                raise ValueError("test services can never be ACTIVE")
        if self.automation_status in (AutomationStatus.ACTIVE, AutomationStatus.TEST_ONLY) and self.workflow is None:
            raise ValueError("automated services need a workflow")
        if self.automation_status is AutomationStatus.TEST_ONLY and not self.is_test_service:
            raise ValueError("TEST_ONLY automation is reserved for test services")
        if self.is_test_service:
            if not self.code.startswith("IN_TEST_"):
                raise ValueError("test service codes must start with IN_TEST_")
            if not any(s.kind == "TEST_ENVIRONMENT" for s in self.sources):
                raise ValueError("test services must cite a TEST_ENVIRONMENT source")
        elif any(s.kind == "TEST_ENVIRONMENT" for s in self.sources):
            raise ValueError("only test services may cite TEST_ENVIRONMENT sources")
        if self.workflow:
            for step in self.workflow.steps:
                for f in step.fields:
                    if f.profile_field and not _SNAKE.match(f.profile_field):
                        raise ValueError(f"bad profileField {f.profile_field}")
        return self

    @property
    def is_executable(self) -> bool:
        return self.automation_status in (AutomationStatus.ACTIVE, AutomationStatus.TEST_ONLY) and self.workflow is not None

    def source(self, source_id: str) -> Source:
        return next(s for s in self.sources if s.id == source_id)

    def api_view(self) -> dict[str, Any]:
        """Serialized form returned by GET /internal/v1/registry/services (rules carry resolved sourceUrl)."""
        data = self.wire()
        for rule in data["eligibility"]["rules"]:
            src = self.source(rule["sourceId"])
            rule["sourceUrl"] = src.url
            rule["sourceTitle"] = src.title
            rule["retrievedAt"] = src.retrieved_at.isoformat()
        return data


class ProfileFieldDefinition(CamelModel):
    key: str
    label: str
    type: ProfileFieldType
    group: str
    purpose: str
    sensitivity: Sensitivity
    core: bool
    options: list[str] | None = None
    options_from: Literal["jurisdictions"] | None = None
    unit: str | None = None
    min: float | None = None
    max: float | None = None
    max_length: int | None = None
    pattern: str | None = None

    @model_validator(mode="after")
    def _enum(self) -> "ProfileFieldDefinition":
        if not _SNAKE.match(self.key):
            raise ValueError("field key must be snake_case")
        if self.type is ProfileFieldType.ENUM and not (self.options or self.options_from):
            raise ValueError(f"ENUM field {self.key} needs options")
        if self.pattern:
            re.compile(self.pattern)
        return self


class Jurisdiction36(CamelModel):
    code: str
    name: str
    type: Literal["STATE", "UNION_TERRITORY"]
