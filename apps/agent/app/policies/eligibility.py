"""Deterministic eligibility engine.

The LLM never participates here. Missing information stays UNKNOWN; it is never guessed.
Outcome rules are documented in docs/AGENT_CONTRACTS.md §1.
"""

from __future__ import annotations

import datetime as dt
from decimal import Decimal, InvalidOperation
from typing import Any

from app.contracts.common import (
    EligibilityOutcome,
    ProfileFieldStatus,
    ProfileFieldType,
    RuleOperator,
    RuleResult,
    ServiceVerificationStatus,
)
from app.contracts.context import CitizenContext, EligibilityResult, RuleEvaluation, RuleEvidence
from app.contracts.registry import EligibilityRule, ProfileFieldDefinition, ServiceDefinition

ENGINE_VERSION = "1.0.0"

_OP_TEXT = {
    RuleOperator.EQUALS: "equal to",
    RuleOperator.NOT_EQUALS: "not equal to",
    RuleOperator.GREATER_THAN: "greater than",
    RuleOperator.GREATER_THAN_OR_EQUAL: "at least",
    RuleOperator.LESS_THAN: "less than",
    RuleOperator.LESS_THAN_OR_EQUAL: "at most",
    RuleOperator.IN: "one of",
    RuleOperator.NOT_IN: "none of",
    RuleOperator.EXISTS: "provided",
    RuleOperator.DATE_BEFORE: "before",
    RuleOperator.DATE_AFTER: "after",
}


class RuleTypeError(ValueError):
    """The citizen value cannot be compared with the rule value (treated as UNKNOWN, never as PASS)."""


def _num(v: Any) -> Decimal:
    if isinstance(v, bool):
        raise RuleTypeError("boolean is not a number")
    try:
        return Decimal(str(v).replace(",", "").strip())
    except (InvalidOperation, AttributeError) as exc:
        raise RuleTypeError(f"not a number: {v!r}") from exc


def _date(v: Any) -> dt.date:
    if isinstance(v, dt.datetime):
        return v.date()
    if isinstance(v, dt.date):
        return v
    try:
        return dt.date.fromisoformat(str(v)[:10])
    except ValueError as exc:
        raise RuleTypeError(f"not an ISO date: {v!r}") from exc


def _bool(v: Any) -> bool:
    if isinstance(v, bool):
        return v
    if isinstance(v, str) and v.strip().lower() in ("true", "false"):
        return v.strip().lower() == "true"
    raise RuleTypeError(f"not a boolean: {v!r}")


def _normalise(v: Any, ftype: ProfileFieldType | None) -> Any:
    if ftype in (ProfileFieldType.NUMBER, ProfileFieldType.INTEGER):
        return _num(v)
    if ftype is ProfileFieldType.DATE:
        return _date(v)
    if ftype is ProfileFieldType.BOOLEAN:
        return _bool(v)
    if isinstance(v, str):
        return v.strip()
    return v


def evaluate_rule(rule: EligibilityRule, actual: Any, ftype: ProfileFieldType | None) -> RuleResult:
    op = rule.operator
    if op is RuleOperator.EXISTS:
        present = actual is not None and actual != ""
        want = True if rule.value is None else bool(rule.value)
        return RuleResult.PASS if present == want else (RuleResult.FAIL if present else RuleResult.UNKNOWN)
    if actual is None or actual == "":
        return RuleResult.UNKNOWN
    try:
        a = _normalise(actual, ftype)
        if op in (RuleOperator.IN, RuleOperator.NOT_IN):
            expected = {_normalise(x, ftype) for x in rule.value}
            ok = a in expected
            return RuleResult.PASS if ok == (op is RuleOperator.IN) else RuleResult.FAIL
        if op in (RuleOperator.DATE_BEFORE, RuleOperator.DATE_AFTER):
            a_d, e_d = _date(actual), _date(rule.value)
            ok = a_d < e_d if op is RuleOperator.DATE_BEFORE else a_d > e_d
            return RuleResult.PASS if ok else RuleResult.FAIL
        e = _normalise(rule.value, ftype)
        ok = {
            RuleOperator.EQUALS: lambda: a == e,
            RuleOperator.NOT_EQUALS: lambda: a != e,
            RuleOperator.GREATER_THAN: lambda: a > e,
            RuleOperator.GREATER_THAN_OR_EQUAL: lambda: a >= e,
            RuleOperator.LESS_THAN: lambda: a < e,
            RuleOperator.LESS_THAN_OR_EQUAL: lambda: a <= e,
        }[op]()
        return RuleResult.PASS if ok else RuleResult.FAIL
    except (RuleTypeError, TypeError):
        return RuleResult.UNKNOWN


def _fmt(v: Any, fd: ProfileFieldDefinition | None) -> str:
    if v is None:
        return "not provided"
    if isinstance(v, bool):
        return "yes" if v else "no"
    if isinstance(v, list):
        return ", ".join(_fmt(x, fd) for x in v)
    if fd is not None and fd.unit == "INR":
        try:
            n = int(_num(v))
            s = str(n)
            # Indian digit grouping: 4,50,000
            head, tail = s[:-3], s[-3:]
            groups = []
            while len(head) > 2:
                groups.insert(0, head[-2:])
                head = head[:-2]
            if head:
                groups.insert(0, head)
            return "₹" + ",".join(groups + [tail]) if groups else "₹" + tail
        except RuleTypeError:
            return str(v)
    return str(v)


def _explain(rule: EligibilityRule, result: RuleResult, actual: Any, fd: ProfileFieldDefinition | None) -> str:
    label = fd.label if fd else rule.field
    if result is RuleResult.UNKNOWN:
        if actual is None:
            return f"{label} is not in your profile yet, so this rule could not be checked: {rule.description}"
        return f"{label} ({_fmt(actual, fd)}) could not be compared with this rule: {rule.description}"
    verdict = "meets" if result is RuleResult.PASS else "does not meet"
    if rule.operator is RuleOperator.EXISTS:
        return f"{label} {verdict} the requirement: {rule.description}"
    return f"{label} is {_fmt(actual, fd)}, which {verdict} the requirement ({_OP_TEXT[rule.operator]} {_fmt(rule.value, fd)}). {rule.description}"


def evaluate_service(
    service: ServiceDefinition,
    context: CitizenContext,
    profile_fields: dict[str, ProfileFieldDefinition],
    *,
    now: dt.datetime | None = None,
) -> EligibilityResult:
    evaluations: list[RuleEvaluation] = []
    relied_on_unconfirmed = False
    for rule in service.eligibility.rules:
        fd = profile_fields.get(rule.field)
        cf = context.fields.get(rule.field)
        status = cf.status if cf else ProfileFieldStatus.MISSING
        actual = None if cf is None or status is ProfileFieldStatus.MISSING else cf.value
        result = evaluate_rule(rule, actual, fd.type if fd else None)
        if result is not RuleResult.UNKNOWN and status is ProfileFieldStatus.EXTRACTED:
            relied_on_unconfirmed = True
        src = service.source(rule.source_id)
        evaluations.append(
            RuleEvaluation(
                rule_id=rule.id,
                rule_version=rule.version,
                field=rule.field,
                operator=rule.operator,
                expected=rule.value,
                actual=actual,
                result=result,
                field_status=status.value,
                explanation=_explain(rule, result, actual, fd),
                evidence=RuleEvidence(
                    source_url=src.url, source_title=src.title, retrieved_at=src.retrieved_at.isoformat(), excerpt=rule.excerpt
                ),
            )
        )

    matched = [e.rule_id for e in evaluations if e.result is RuleResult.PASS]
    failed = [e.rule_id for e in evaluations if e.result is RuleResult.FAIL]
    unknown = [e.rule_id for e in evaluations if e.result is RuleResult.UNKNOWN]
    missing_fields = sorted({e.field for e in evaluations if e.result is RuleResult.UNKNOWN})

    if failed:
        outcome = EligibilityOutcome.INELIGIBLE
        explanation = f"Based on your profile you do not meet {len(failed)} of {len(evaluations)} rule(s)."
    elif unknown:
        outcome = EligibilityOutcome.NEEDS_MORE_INFORMATION
        explanation = f"{len(unknown)} rule(s) could not be checked because information is missing: {', '.join(missing_fields)}."
    elif service.verification_status is ServiceVerificationStatus.DRAFT or relied_on_unconfirmed or not evaluations:
        outcome = EligibilityOutcome.UNVERIFIED
        reasons = []
        if service.verification_status is ServiceVerificationStatus.DRAFT:
            reasons.append("this scheme's rules have not yet been verified against current official sources")
        if relied_on_unconfirmed:
            reasons.append("some values were extracted from documents and not yet confirmed by you")
        if not evaluations:
            reasons.append("this service has no machine-checkable rules")
        explanation = "All checked rules pass, but the result is unverified because " + " and ".join(reasons) + "."
    else:
        outcome = EligibilityOutcome.ELIGIBLE
        explanation = (
            f"You appear to meet all {len(evaluations)} checked rule(s) on the information provided. "
            "The department makes the final decision."
        )
    if service.eligibility.not_evaluated and outcome in (EligibilityOutcome.ELIGIBLE, EligibilityOutcome.UNVERIFIED):
        explanation += " Some official criteria cannot be checked automatically; review them before applying."

    return EligibilityResult(
        service_code=service.code,
        service_version=service.version,
        outcome=outcome,
        rules=evaluations,
        matched=matched,
        failed=failed,
        unknown=unknown,
        missing_fields=missing_fields,
        not_evaluated=list(service.eligibility.not_evaluated),
        explanation=explanation,
        evaluated_at=now or dt.datetime.now(dt.UTC),
        engine_version=ENGINE_VERSION,
    )
