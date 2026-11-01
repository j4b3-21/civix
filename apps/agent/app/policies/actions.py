"""Action classification and gating (docs/AGENT_CONTRACTS.md §1 action class policy).

These rules are code, not prompt text: no page content, document or model output can change them.
"""

from __future__ import annotations

import re

from app.contracts.common import ActionClass, ApprovalKind, ConsentPurpose

STEP_ACTION_CLASS: dict[str, ActionClass] = {
    "NAVIGATE": ActionClass.READ_ONLY,
    "REVIEW": ActionClass.READ_ONLY,
    "VERIFY_RECEIPT": ActionClass.READ_ONLY,
    "FILL_FORM": ActionClass.REVERSIBLE,
    "CITIZEN_INPUT": ActionClass.SENSITIVE,  # value always comes from the citizen
    "UPLOAD_DOCUMENTS": ActionClass.SENSITIVE,
    "SUBMIT": ActionClass.CONSEQUENTIAL,
}

STEP_REQUIRED_APPROVAL: dict[str, ApprovalKind | None] = {
    "NAVIGATE": None,
    "REVIEW": None,
    "VERIFY_RECEIPT": None,
    "CITIZEN_INPUT": None,  # the citizen performs it directly
    "FILL_FORM": ApprovalKind.PREPARATION,
    "UPLOAD_DOCUMENTS": ApprovalKind.DOCUMENT_UPLOAD,
    "SUBMIT": ApprovalKind.FINAL_SUBMISSION,
}

STEP_REQUIRED_CONSENT: dict[str, ConsentPurpose | None] = {
    "FILL_FORM": ConsentPurpose.AGENT_EXECUTION,
    "UPLOAD_DOCUMENTS": ConsentPurpose.PORTAL_DOCUMENT_UPLOAD,
    "SUBMIT": ConsentPurpose.AGENT_EXECUTION,
}

# Clicking any control whose accessible name matches these is consequential and only allowed through SUBMIT.
_CONSEQUENTIAL_NAME = re.compile(
    r"\b(submit|final\s*submit|pay|payment|make\s*payment|proceed\s*to\s*pay|confirm\s*and\s*submit|e-?sign|sign\s*and\s*submit|lock\s*application|freeze)\b",
    re.IGNORECASE,
)
_PAYMENT_NAME = re.compile(r"\b(pay|payment|proceed\s*to\s*pay|make\s*payment|debit|upi)\b", re.IGNORECASE)


def is_consequential_control(accessible_name: str) -> bool:
    return bool(_CONSEQUENTIAL_NAME.search(accessible_name or ""))


def is_payment_control(accessible_name: str) -> bool:
    return bool(_PAYMENT_NAME.search(accessible_name or ""))
