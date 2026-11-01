"""PostgreSQL-backed checkpointing: interrupt/resume across process restarts, crash recovery, durable event outbox."""

from __future__ import annotations

import uuid

import pytest_asyncio

from app.persistence.db import AgentDatabase

from .conftest import database_url, requires_postgres
from .harness import citizen, documents, make_harness

pytestmark = [requires_postgres]


@pytest_asyncio.fixture
async def db():
    schema = f"agent_t{uuid.uuid4().hex[:8]}"
    d = await AgentDatabase.open(database_url(), schema=schema)
    yield d
    async with d.pool.connection() as conn:
        await conn.execute(f"DROP SCHEMA {schema} CASCADE")
    await d.close()


async def _new_process(registry, db):
    """Everything in-memory is discarded; only the database survives (like a container restart)."""
    saver = await db.checkpointer()
    return make_harness(registry, checkpointer=saver, db=db)


async def test_resume_after_restart_from_postgres_checkpoint(registry, db):
    h1 = await _new_process(registry, db)
    tid = str(uuid.uuid4())
    await h1.runner.start(task_id=tid, application_id=str(uuid.uuid4()), user_id=str(uuid.uuid4()), service_code="IN_TEST_PORTAL_SCHOLARSHIP",
                          intent={"type": "APPLY"}, citizen_context=citizen(), documents=await documents(h1.storage), data_version=1)
    await h1.runner.wait_idle(tid)
    assert (await db.get_run(tid))["status"] == "WAITING_APPROVAL"
    approval = h1.api.pending("PREPARATION")

    h2 = await _new_process(registry, db)  # "restart"
    h2.api.approvals = h1.api.approvals
    st = await h2.runner.status(tid)
    assert st["pendingInterrupt"]["kind"] == "PREPARATION" and st["plan"]["steps"][0]["id"] == "open_portal"
    await h2.runner.resume_approval(tid, approval_id=approval["approvalId"], decision="APPROVE", token=h1.api.token_for(approval))
    await h2.runner.wait_idle(tid)
    st = await h2.runner.status(tid)
    assert st["pendingInterrupt"]["kind"] == "DOCUMENT_UPLOAD"
    assert "APPROVAL_GRANTED" in h2.api.types()
    # Event counter continued from the checkpoint: ids never collide across processes
    all_ids = [e["eventId"] for e in h1.api.events + h2.api.events]
    assert len(all_ids) == len(set(all_ids))


async def test_crashed_run_is_redriven_on_startup(registry, db):
    h1 = await _new_process(registry, db)

    async def boom(body):
        raise RuntimeError("process died")

    h1.api.create_approval = boom  # type: ignore[assignment]
    tid = str(uuid.uuid4())
    await h1.runner.start(task_id=tid, application_id=str(uuid.uuid4()), user_id=str(uuid.uuid4()), service_code="IN_TEST_PORTAL_SCHOLARSHIP",
                          intent={"type": "APPLY"}, citizen_context=citizen(), documents=await documents(h1.storage), data_version=1)
    await h1.runner.wait_idle(tid)
    await db.set_run_status(tid, "RUNNING")  # the process died before it could record anything

    h2 = await _new_process(registry, db)
    assert await h2.runner.recover_on_startup() == 1
    await h2.runner.wait_idle(tid)
    st = await h2.runner.status(tid)
    assert st["pendingInterrupt"]["kind"] == "PREPARATION"
    # planning was NOT re-run: it was checkpointed before the crash
    assert "PLAN_CREATED" not in h2.api.types()


async def test_outbox_survives_api_outage(registry, db):
    h = await _new_process(registry, db)
    h.api.fail_events = True
    tid = str(uuid.uuid4())
    await h.runner.start(task_id=tid, application_id=str(uuid.uuid4()), user_id=str(uuid.uuid4()), service_code="IN_TEST_PORTAL_SCHOLARSHIP",
                         intent={"type": "APPLY"}, citizen_context=citizen(annual_family_income=None), documents=[], data_version=1)
    await h.runner.wait_idle(tid)
    assert h.api.events == []
    assert len(await db.pending_events()) >= 4
    h.api.fail_events = False
    assert await h.deps.sink.flush()
    assert h.api.types()[0] == "TASK_STARTED" and h.api.types()[-1] == "TASK_FAILED"
    assert await db.pending_events() == []


async def test_submission_marker_prevents_double_click(db):
    aid = str(uuid.uuid4())
    assert await db.begin_submission(aid, str(uuid.uuid4())) is True
    assert await db.begin_submission(aid, str(uuid.uuid4())) is False
