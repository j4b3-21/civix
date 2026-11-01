"""LangGraph routing up to the first human approval (no browser involved)."""

from __future__ import annotations

from .harness import citizen, field, make_harness, start


async def test_ineligible_ends_without_browser(registry):
    h = make_harness(registry)
    tid = await start(h, ctx=citizen(annual_family_income=field(900000)))
    assert h.api.types() == ["TASK_STARTED", "PROFILE_VALIDATED", "SERVICE_DISCOVERED", "ELIGIBILITY_EVALUATED", "TASK_FAILED"]
    last = h.api.last("TASK_FAILED")
    assert last["metadata"]["errorCode"] == "INELIGIBLE" and last["applicationStatus"] == "FAILED"
    assert (await h.runner.status(tid))["status"] == "FAILED"


async def test_missing_profile_information_requires_action(registry):
    h = make_harness(registry)
    await start(h, ctx=citizen(class12_percentage=None))
    last = h.api.last("TASK_FAILED")
    assert last["metadata"]["errorCode"] == "NEEDS_PROFILE_INFORMATION"
    assert last["applicationStatus"] == "ACTION_REQUIRED"
    assert last["metadata"]["requiredAction"]["fields"] == ["class12_percentage"]


async def test_missing_form_field_detected_by_planner(registry):
    h = make_harness(registry)
    await start(h, ctx=citizen(institution_name=None))  # not an eligibility field, but the portal form needs it
    last = h.api.last("TASK_FAILED")
    assert last["metadata"]["errorCode"] == "NEEDS_PROFILE_INFORMATION" and "institution_name" in last["message"]


async def test_missing_document_requires_upload(registry):
    h = make_harness(registry)
    await start(h, docs=[])
    assert "DOCUMENT_REQUIRED" in h.api.types()
    assert h.api.last("TASK_FAILED")["metadata"]["requiredAction"]["type"] == "DOCUMENT"


async def test_consent_is_enforced(registry):
    h = make_harness(registry)
    ctx = citizen()
    ctx["consents"] = ["PROFILE_PROCESSING"]
    await start(h, ctx=ctx)
    assert h.api.last("TASK_FAILED")["metadata"]["errorCode"] == "CONSENT_REQUIRED"


async def test_guided_service_cannot_be_executed(registry):
    h = make_harness(registry)
    await start(h, service="IN_CENTRAL_MOE_CSSS")
    assert h.api.last("TASK_FAILED")["metadata"]["errorCode"] == "AUTOMATION_UNAVAILABLE"


async def test_test_services_disabled(registry):
    h = make_harness(registry, civix_enable_test_services=False)
    await start(h)
    assert h.api.last("TASK_FAILED")["metadata"]["errorCode"] == "SERVICE_UNAVAILABLE"


async def test_pauses_for_preparation_approval_then_reject_cancels(registry):
    h = make_harness(registry)
    tid = await start(h)
    assert h.api.types()[-1] == "APPROVAL_REQUIRED"
    st = await h.runner.status(tid)
    assert st["status"] == "WAITING_APPROVAL" and st["pendingInterrupt"]["kind"] == "PREPARATION"
    appr = h.api.pending("PREPARATION")
    assert appr["actionClass"] == "REVERSIBLE" and any(f["label"] == "Full name" for f in appr["details"]["fields"])
    await h.runner.resume_approval(tid, approval_id=appr["approvalId"], decision="REJECT", token=None)
    await h.runner.wait_idle(tid)
    assert h.api.types()[-2:] == ["APPROVAL_REJECTED", "TASK_CANCELLED"]
    assert (await h.runner.status(tid))["status"] == "CANCELLED"


async def test_forged_or_mismatched_token_is_refused(registry):
    h = make_harness(registry)
    tid = await start(h)
    appr = h.api.pending("PREPARATION")
    bad = h.api.token_for(appr, digest="0" * 64)
    await h.runner.resume_approval(tid, approval_id=appr["approvalId"], decision="APPROVE", token=bad)
    await h.runner.wait_idle(tid)
    last = h.api.last("TASK_FAILED")
    assert last["metadata"]["errorCode"] == "APPROVAL_INVALID"


async def test_stale_resume_keeps_waiting(registry):
    h = make_harness(registry)
    tid = await start(h)
    await h.runner.resume_approval(tid, approval_id="00000000-0000-0000-0000-000000000000", decision="APPROVE", token="x.y")
    await h.runner.wait_idle(tid)
    assert (await h.runner.status(tid))["pendingInterrupt"]["kind"] == "PREPARATION"


async def test_cancel_while_waiting(registry):
    h = make_harness(registry)
    tid = await start(h)
    await h.runner.cancel(tid)
    await h.runner.wait_idle(tid)
    assert h.api.types()[-1] == "TASK_CANCELLED"


async def test_events_carry_no_field_values(registry):
    h = make_harness(registry)
    await start(h)
    import json
    blob = json.dumps(h.api.events)
    for secret in ("Asha Verma", "9876543210", "240000", "asha@example.org"):
        assert secret not in blob
    ids = [e["eventId"] for e in h.api.events]
    assert len(ids) == len(set(ids))
