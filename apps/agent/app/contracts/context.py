"""Citizen context, documents and eligibility result contracts (docs/AGENT_CONTRACTS.md §3.1)."""

from __future__ import annotations

import datetime as dt
from typing import Any

from pydantic import Field

from app.contracts.common import (
    CamelModel,
    ConsentPurpose,
    DocumentCategory,
    EligibilityOutcome,
    FieldSource,
    ProfileFieldStatus,
    RuleOperator,
    RuleResult,
)


class ContextField(CamelModel):
    value: Any = None
    status: ProfileFieldStatus
    source: FieldSource | None = None
    source_document_id: str | None = None
    confidence: float | None = None


class CitizenContext(CamelModel):
    user_id: str | None = None
    fields: dict[str, ContextField] = Field(default_factory=dict)
    consents: list[ConsentPurpose] = Field(default_factory=list)

    def value(self, key: str) -> Any:
        f = self.fields.get(key)
        if f is None or f.status is ProfileFieldStatus.MISSING:
            return None
        return f.value

    def has_consent(self, purpose: ConsentPurpose) -> bool:
        return purpose in self.consents


class TaskDocument(CamelModel):
    document_id: str
    category: DocumentCategory
    storage_key: str
    mime_type: str
    sha256: str | None = None
    file_name: str | None = None
    reviewed: bool = False


class RuleEvidence(CamelModel):
    source_url: str
    source_title: str
    retrieved_at: str
    excerpt: str | None = None


class RuleEvaluation(CamelModel):
    rule_id: str
    rule_version: int
    field: str
    operator: RuleOperator
    expected: Any = None
    actual: Any = None
    result: RuleResult
    field_status: str
    explanation: str
    evidence: RuleEvidence


class EligibilityResult(CamelModel):
    service_code: str
    service_version: int
    outcome: EligibilityOutcome
    rules: list[RuleEvaluation]
    matched: list[str]
    failed: list[str]
    unknown: list[str]
    missing_fields: list[str]
    not_evaluated: list[str] = Field(default_factory=list)
    explanation: str
    evaluated_at: dt.datetime
    engine_version: str
