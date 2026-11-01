from __future__ import annotations

from app.agents.discovery_agent import interpret, search
from app.contracts.context import CitizenContext, ContextField


def ctx(**kw) -> CitizenContext:
    return CitizenContext(fields={k: ContextField(value=v, status="USER_CONFIRMED") for k, v in kw.items()})


def test_undergraduate_scholarship_query(registry):
    q = "I am an undergraduate student. Find scholarships and financial assistance programmes I may qualify for."
    interp = interpret(q, registry)
    assert "SCHOLARSHIPS" in interp["categories"] and interp["educationLevel"] == "UNDERGRADUATE"
    res = search(q, registry, interpretation=interp, context=ctx(education_level="UNDERGRADUATE", annual_family_income=300000,
                 class12_board_percentile=91, course_type="REGULAR", receiving_other_scholarship=False),
                 filters={}, include_test=False)
    codes = [r["serviceCode"] for r in res]
    assert codes[0] == "IN_CENTRAL_MOE_CSSS"
    assert "IN_CENTRAL_MSME_UDYAM" not in codes
    assert all(not c.startswith("IN_TEST_") for c in codes)
    top = res[0]
    assert top["eligibility"]["outcome"] == "ELIGIBLE" and top["sources"][0]["retrievedAt"] == "2026-11-01"


def test_state_scheme_only_for_that_state(registry):
    q = "post matric scholarship for SC students"
    interp = interpret(q, registry)
    ka = [r["serviceCode"] for r in search(q, registry, interpretation=interp, context=None, filters={"state": "IN-KA"}, include_test=False)]
    hr = [r["serviceCode"] for r in search(q, registry, interpretation=interp, context=None, filters={"state": "IN-HR"}, include_test=False)]
    assert "IN_HR_SEWA_PMS_SC" not in ka and "IN_HR_SEWA_PMS_SC" in hr and "IN_CENTRAL_MSJE_PMS_SC" in ka


def test_business_query(registry):
    q = "register my small business as MSME"
    interp = interpret(q, registry)
    res = search(q, registry, interpretation=interp, context=None, filters={}, include_test=False)
    assert res[0]["serviceCode"] == "IN_CENTRAL_MSME_UDYAM"


def test_state_detected_from_text(registry):
    assert interpret("scholarship schemes in Haryana", registry)["state"] == "IN-HR"


def test_no_match_returns_empty(registry):
    q = "xyzzy plugh"
    assert search(q, registry, interpretation=interpret(q, registry), context=None, filters={}, include_test=False) == []
