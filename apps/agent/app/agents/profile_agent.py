"""Profile Agent: reports which authorized profile fields are available, missing or unverified, with provenance."""

from __future__ import annotations

from app.contracts.common import ProfileFieldStatus
from app.contracts.context import CitizenContext
from app.contracts.plan import ProfileReport
from app.contracts.registry import ServiceDefinition


def required_fields(service: ServiceDefinition) -> list[str]:
    keys = {r.field for r in service.eligibility.rules}
    if service.workflow:
        for step in service.workflow.steps:
            keys.update(f.profile_field for f in step.fields if f.profile_field and f.required)
    return sorted(keys)


def analyze(context: CitizenContext, service: ServiceDefinition | None) -> ProfileReport:
    wanted = required_fields(service) if service else sorted(context.fields)
    available, missing, unverified = [], [], []
    provenance: dict[str, dict] = {}
    for key in wanted:
        f = context.fields.get(key)
        if f is None or f.status is ProfileFieldStatus.MISSING or f.value in (None, ""):
            missing.append(key)
            continue
        available.append(key)
        provenance[key] = {"status": f.status.value, "source": f.source.value if f.source else None, "sourceDocumentId": f.source_document_id}
        if f.status in (ProfileFieldStatus.PROVIDED, ProfileFieldStatus.EXTRACTED):
            unverified.append(key)
    return ProfileReport(available=available, missing=missing, unverified=unverified, provenance=provenance)
