"""Document Agent (orchestration side): checks the citizen's documents against what the service requires.
Extraction itself lives in app.document_intelligence and is invoked through POST /internal/v1/documents/extract."""

from __future__ import annotations

from app.contracts.context import TaskDocument
from app.contracts.plan import DocumentCheck
from app.contracts.registry import ServiceDefinition


def check_documents(service: ServiceDefinition, documents: list[TaskDocument]) -> DocumentCheck:
    satisfied, missing = [], []
    needed = [d for d in service.required_documents if d.mandatory]
    if service.workflow:  # the portal workflow is authoritative for what will actually be uploaded
        needed_cats = {d.category for step in service.workflow.steps for d in step.documents if d.required}
        needed = [d for d in service.required_documents if d.category in needed_cats] or needed
    for req in needed:
        match = next((d for d in documents if d.category is req.category), None)
        if match:
            satisfied.append({"category": req.category.value, "documentId": match.document_id})
        else:
            missing.append({"category": req.category.value, "description": req.description})
    unreviewed = [d.document_id for d in documents if not d.reviewed]
    return DocumentCheck(satisfied=satisfied, missing_mandatory=missing, unreviewed=unreviewed, review_required=bool(unreviewed))
