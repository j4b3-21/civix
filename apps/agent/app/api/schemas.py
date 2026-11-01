"""Request bodies of the agent's internal API (docs/AGENT_CONTRACTS.md §3)."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import Field

from app.contracts.common import ApprovalDecision, ApprovalKind, CamelModel, DocumentCategory, JurisdictionLevel, ServiceCategory
from app.contracts.context import CitizenContext, TaskDocument


class Intent(CamelModel):
    type: Literal["APPLY", "DISCOVER"] = "APPLY"
    text: str | None = Field(default=None, max_length=1000)


class StartTaskRequest(CamelModel):
    task_id: str = Field(pattern=r"^[0-9a-fA-F-]{36}$")
    application_id: str = Field(pattern=r"^[0-9a-fA-F-]{36}$")
    user_id: str = Field(pattern=r"^[0-9a-fA-F-]{36}$")
    service_code: str = Field(max_length=80)
    intent: Intent = Field(default_factory=Intent)
    citizen_context: CitizenContext
    documents: list[TaskDocument] = Field(default_factory=list, max_length=30)
    data_version: int = Field(ge=1)


class ApprovalResume(CamelModel):
    approval_id: str
    approval_kind: ApprovalKind
    decision: ApprovalDecision
    token: str | None = Field(default=None, max_length=4096)
    payload_digest: str | None = None
    expires_at: str | None = None


class CitizenInputResume(CamelModel):
    request_id: str
    value: str = Field(min_length=1, max_length=200)


class ResumeRequest(CamelModel):
    kind: Literal["APPROVAL", "CITIZEN_INPUT", "RETRY"]
    approval: ApprovalResume | None = None
    citizen_input: CitizenInputResume | None = None


class CancelRequest(CamelModel):
    reason: str | None = Field(default=None, max_length=300)


class DiscoveryFilters(CamelModel):
    state: str | None = Field(default=None, pattern=r"^IN-[A-Z]{2}$")
    category: ServiceCategory | None = None
    jurisdiction: JurisdictionLevel | None = None


class DiscoveryRequest(CamelModel):
    query: str = Field(min_length=3, max_length=500)
    filters: DiscoveryFilters = Field(default_factory=DiscoveryFilters)
    citizen_context: CitizenContext | None = None
    include_test: bool = False
    limit: int = Field(default=10, ge=1, le=50)


class EligibilityRequest(CamelModel):
    service_code: str
    citizen_context: CitizenContext


class OtherDocument(CamelModel):
    document_id: str
    category: DocumentCategory
    fields: list[dict[str, Any]] = Field(default_factory=list)


class ExtractRequest(CamelModel):
    document_id: str
    storage_key: str = Field(pattern=r"^users/[0-9a-fA-F-]{36}/documents/[0-9a-fA-F-]{36}$")
    mime_type: Literal["application/pdf", "image/png", "image/jpeg"]
    declared_category: DocumentCategory
    sha256: str | None = None
    citizen_context: CitizenContext | None = None
    other_documents: list[OtherDocument] = Field(default_factory=list)
