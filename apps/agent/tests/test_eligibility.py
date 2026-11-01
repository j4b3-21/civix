from __future__ import annotations

import pytest

from app.contracts.common import EligibilityOutcome, ProfileFieldType, RuleOperator, RuleResult
from app.contracts.context import CitizenContext, ContextField
from app.contracts.registry import EligibilityRule
from app.policies.eligibility import evaluate_rule, evaluate_service


def ctx(**fields) -> CitizenContext:
    out = {}
    for k, v in fields.items():
        if isinstance(v, tuple):
            out[k] = ContextField(value=v[0], status=v[1])
        else:
            out[k] = ContextField(value=v, status="USER_CONFIRMED", source="USER_INPUT")
    return CitizenContext(fields=out)


def rule(op, value=None, field="x") -> EligibilityRule:
    return EligibilityRule(id="r", version=1, field=field, operator=op, value=value, description="d", source_id="s")


@pytest.mark.parametrize(
    "op,value,actual,ftype,expected",
    [
        (RuleOperator.EQUALS, "SC", "SC", ProfileFieldType.ENUM, RuleResult.PASS),
        (RuleOperator.EQUALS, "SC", "ST", ProfileFieldType.ENUM, RuleResult.FAIL),
        (RuleOperator.NOT_EQUALS, "SC", "ST", ProfileFieldType.ENUM, RuleResult.PASS),
        (RuleOperator.GREATER_THAN, 80, 80, ProfileFieldType.NUMBER, RuleResult.FAIL),
        (RuleOperator.GREATER_THAN, 80, 80.5, ProfileFieldType.NUMBER, RuleResult.PASS),
        (RuleOperator.GREATER_THAN_OR_EQUAL, 40, 40, ProfileFieldType.NUMBER, RuleResult.PASS),
        (RuleOperator.LESS_THAN, 250000, 250000, ProfileFieldType.INTEGER, RuleResult.FAIL),
        (RuleOperator.LESS_THAN_OR_EQUAL, 450000, "4,50,000", ProfileFieldType.INTEGER, RuleResult.PASS),
        (RuleOperator.IN, ["UNDERGRADUATE", "POSTGRADUATE"], "DIPLOMA", ProfileFieldType.ENUM, RuleResult.FAIL),
        (RuleOperator.NOT_IN, ["OTHER"], "INDIAN", ProfileFieldType.ENUM, RuleResult.PASS),
        (RuleOperator.EXISTS, None, "x", ProfileFieldType.STRING, RuleResult.PASS),
        (RuleOperator.EXISTS, None, None, ProfileFieldType.STRING, RuleResult.UNKNOWN),
        (RuleOperator.DATE_BEFORE, "2010-01-01", "2005-06-01", ProfileFieldType.DATE, RuleResult.PASS),
        (RuleOperator.DATE_AFTER, "2010-01-01", "2005-06-01", ProfileFieldType.DATE, RuleResult.FAIL),
        (RuleOperator.EQUALS, False, False, ProfileFieldType.BOOLEAN, RuleResult.PASS),
        (RuleOperator.EQUALS, True, "false", ProfileFieldType.BOOLEAN, RuleResult.FAIL),
        # missing or incomparable data is never a PASS
        (RuleOperator.LESS_THAN_OR_EQUAL, 450000, None, ProfileFieldType.INTEGER, RuleResult.UNKNOWN),
        (RuleOperator.LESS_THAN_OR_EQUAL, 450000, "about two lakh", ProfileFieldType.INTEGER, RuleResult.UNKNOWN),
        (RuleOperator.DATE_BEFORE, "2010-01-01", "yesterday", ProfileFieldType.DATE, RuleResult.UNKNOWN),
    ],
)
def test_operators(op, value, actual, ftype, expected):
    assert evaluate_rule(rule(op, value), actual, ftype) is expected


def full_csss_context(**overrides):
    base = dict(
        class12_board_percentile=92,
        course_type="REGULAR",
        education_level="UNDERGRADUATE",
        annual_family_income=300000,
        receiving_other_scholarship=False,
    )
    base.update(overrides)
    return ctx(**base)


def test_csss_eligible(registry):
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), full_csss_context(), registry.profile_fields)
    assert r.outcome is EligibilityOutcome.ELIGIBLE
    assert r.failed == [] and r.unknown == []
    assert all(rule.evidence.source_url.startswith("https://") for rule in r.rules)
    assert "₹3,00,000" in next(x for x in r.rules if x.rule_id == "family_income_limit").explanation


def test_csss_ineligible_on_income(registry):
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), full_csss_context(annual_family_income=500000), registry.profile_fields)
    assert r.outcome is EligibilityOutcome.INELIGIBLE and r.failed == ["family_income_limit"]


def test_missing_information_is_unknown_not_fail(registry):
    c = full_csss_context()
    del c.fields["class12_board_percentile"]
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), c, registry.profile_fields)
    assert r.outcome is EligibilityOutcome.NEEDS_MORE_INFORMATION
    assert r.missing_fields == ["class12_board_percentile"]


def test_explicit_missing_status_is_unknown(registry):
    c = full_csss_context(class12_board_percentile=(95, "MISSING"))
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), c, registry.profile_fields)
    assert r.outcome is EligibilityOutcome.NEEDS_MORE_INFORMATION


def test_fail_beats_unknown(registry):
    c = full_csss_context(annual_family_income=900000)
    del c.fields["course_type"]
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), c, registry.profile_fields)
    assert r.outcome is EligibilityOutcome.INELIGIBLE


def test_extracted_unconfirmed_values_make_result_unverified(registry):
    c = full_csss_context(annual_family_income=(300000, "EXTRACTED"))
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), c, registry.profile_fields)
    assert r.outcome is EligibilityOutcome.UNVERIFIED
    assert "not yet confirmed" in r.explanation


def test_draft_service_is_unverified_even_when_all_rules_pass(registry):
    c = ctx(social_category="SC", annual_family_income=200000, education_level="UNDERGRADUATE", nationality="INDIAN")
    r = evaluate_service(registry.get("IN_CENTRAL_MSJE_PMS_SC"), c, registry.profile_fields)
    assert r.outcome is EligibilityOutcome.UNVERIFIED
    assert "not yet been verified" in r.explanation


def test_state_rule(registry):
    c = ctx(domicile_state_code="IN-KA", social_category="SC", annual_family_income=100000)
    r = evaluate_service(registry.get("IN_HR_SEWA_PMS_SC"), c, registry.profile_fields)
    assert r.outcome is EligibilityOutcome.INELIGIBLE and r.failed == ["haryana_domicile"]


def test_result_is_wire_serialisable(registry):
    r = evaluate_service(registry.get("IN_CENTRAL_MOE_CSSS"), full_csss_context(), registry.profile_fields)
    w = r.wire()
    assert w["outcome"] == "ELIGIBLE" and "serviceCode" in w and "missingFields" in w and w["rules"][0]["ruleId"]
