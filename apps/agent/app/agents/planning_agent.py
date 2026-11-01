"""Planning Agent: turns a verified workflow definition + citizen context + documents into a concrete, typed plan.

Deterministic: values come only from the citizen's profile (or constants in the definition). Nothing is invented; a
required value that is missing stops planning and is reported back to the citizen.
"""

from __future__ import annotations

import datetime as dt
from decimal import Decimal, InvalidOperation
from typing import Any

from app.contracts.common import ProfileFieldStatus
from app.contracts.context import CitizenContext, TaskDocument
from app.contracts.plan import ExecutionPlan, PlannedDocument, PlannedField, PlannedStep, PlanningResult
from app.contracts.registry import FormField, ServiceDefinition
from app.policies.actions import STEP_ACTION_CLASS, STEP_REQUIRED_APPROVAL
from app.policies.approval_tokens import payload_digest


class PlanningError(ValueError):
    pass


def _to_text(field: FormField, raw: Any) -> str:
    if field.input_type == "date":
        try:
            d = dt.date.fromisoformat(str(raw)[:10])
        except ValueError as exc:
            raise PlanningError(f"{field.key}: not a date") from exc
        return d.strftime(field.format or "%Y-%m-%d")
    if field.input_type == "checkbox":
        return "true" if raw in (True, "true", "TRUE", "yes") else "false"
    if isinstance(raw, bool):
        return "true" if raw else "false"
    if isinstance(raw, (int, float, Decimal)) or field.input_type == "number":
        try:
            n = Decimal(str(raw))
        except InvalidOperation as exc:
            raise PlanningError(f"{field.key}: not a number") from exc
        return str(n.quantize(Decimal(1))) if n == n.to_integral() else format(n.normalize(), "f")
    text = str(raw).strip()
    return field.value_map.get(text, text)


def build_plan(service: ServiceDefinition, context: CitizenContext, documents: list[TaskDocument], data_version: int) -> PlanningResult:
    wf = service.workflow
    if wf is None:
        raise PlanningError(f"{service.code} has no workflow")
    missing_fields: list[str] = []
    missing_docs: list[str] = []
    steps: list[PlannedStep] = []
    used_docs: set[str] = set()
    for s in wf.steps:
        planned_fields: list[PlannedField] = []
        for f in s.fields:
            if f.constant is not None:
                raw: Any = f.constant
            else:
                assert f.profile_field
                cf = context.fields.get(f.profile_field)
                raw = None if cf is None or cf.status is ProfileFieldStatus.MISSING else cf.value
            if raw in (None, ""):
                if f.required:
                    missing_fields.append(f.profile_field or f.key)
                continue
            try:
                value = _to_text(f, raw)
            except PlanningError:
                missing_fields.append(f.profile_field or f.key)
                continue
            if f.input_type in ("select",) and f.value_map and str(raw) not in f.value_map and value == str(raw):
                # value has no mapping for this portal: report rather than guess
                missing_fields.append(f.profile_field or f.key)
                continue
            planned_fields.append(PlannedField(key=f.key, label=f.label, alternate_labels=f.alternate_labels, name=f.name,
                                               input_type=f.input_type, value=value, profile_field=f.profile_field))
        planned_docs: list[PlannedDocument] = []
        for d in s.documents:
            candidates = [doc for doc in documents if doc.category is d.category and doc.document_id not in used_docs]
            candidates.sort(key=lambda doc: not doc.reviewed)
            if not candidates:
                if d.required:
                    missing_docs.append(d.category.value)
                continue
            doc = candidates[0]
            used_docs.add(doc.document_id)
            planned_docs.append(PlannedDocument(document_id=doc.document_id, category=d.category, label=d.label,
                                                alternate_labels=d.alternate_labels, file_name=doc.file_name or f"{d.category.value.lower()}",
                                                storage_key=doc.storage_key, mime_type=doc.mime_type))
        steps.append(PlannedStep(
            id=s.id, title=s.title, action=s.action, action_class=STEP_ACTION_CLASS[s.action], approval=STEP_REQUIRED_APPROVAL[s.action],
            url=s.url, fields=planned_fields, documents=planned_docs, input_kind=s.input_kind, prompt=s.prompt,
            input_field=s.input_field, continue_button=s.continue_button, precondition=s.precondition, expected=s.expected,
            review_fields=s.review_fields, retry=s.retry,
        ))
    if missing_fields or missing_docs:
        return PlanningResult(plan=None, missing_fields=sorted(set(missing_fields)), missing_documents=sorted(set(missing_docs)))

    fills = {s.id: {f.key: f.value for f in s.fields} for s in steps if s.action == "FILL_FORM"}
    doc_ids = sorted(used_docs)
    plan = ExecutionPlan(
        service_code=service.code,
        service_version=service.version,
        data_version=data_version,
        allowed_domains=list(wf.allowed_domains),
        steps=steps,
        preparation_digest=payload_digest(fields=fills, documents=[], service_code=service.code, data_version=data_version),
        upload_digest=payload_digest(fields={}, documents=doc_ids, service_code=service.code, data_version=data_version),
        submission_digest=payload_digest(fields=fills, documents=doc_ids, service_code=service.code, data_version=data_version),
        session_expired_markers=wf.session_expired_markers,
    )
    return PlanningResult(plan=plan, missing_fields=[], missing_documents=[])
