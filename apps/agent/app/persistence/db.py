"""Agent-owned persistence in PostgreSQL schema "agent": versioned migrations, task runs, event outbox,
submission markers, and the LangGraph checkpointer. The agent role has no access to schema "civix"."""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

MIGRATIONS: list[tuple[int, str]] = [
    (
        1,
        """
        CREATE TABLE IF NOT EXISTS task_runs (
            task_id         UUID PRIMARY KEY,
            application_id  UUID        NOT NULL,
            user_id         UUID        NOT NULL,
            service_code    VARCHAR(80) NOT NULL,
            status          VARCHAR(20) NOT NULL,
            created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE INDEX IF NOT EXISTS ix_task_runs_status ON task_runs (status);
        CREATE TABLE IF NOT EXISTS event_outbox (
            id            BIGSERIAL PRIMARY KEY,
            event_id      UUID        NOT NULL UNIQUE,
            task_id       UUID        NOT NULL,
            payload       JSONB       NOT NULL,
            attempts      INTEGER     NOT NULL DEFAULT 0,
            created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
            delivered_at  TIMESTAMPTZ
        );
        CREATE INDEX IF NOT EXISTS ix_event_outbox_pending ON event_outbox (id) WHERE delivered_at IS NULL;
        CREATE TABLE IF NOT EXISTS submission_attempts (
            approval_id   UUID PRIMARY KEY,
            task_id       UUID        NOT NULL,
            started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
            completed_at  TIMESTAMPTZ,
            outcome       VARCHAR(32)
        );
        """,
    ),
]


@dataclass
class AgentDatabase:
    pool: AsyncConnectionPool
    schema: str

    @classmethod
    async def open(cls, url: str, schema: str = "agent", *, min_size: int = 1, max_size: int = 10) -> "AgentDatabase":
        if not schema.isidentifier():
            raise ValueError("invalid schema name")
        pool = AsyncConnectionPool(
            conninfo=url,
            min_size=min_size,
            max_size=max_size,
            open=False,
            kwargs={"autocommit": True, "prepare_threshold": 0, "row_factory": dict_row, "options": f"-c search_path={schema}"},
        )
        await pool.open(wait=True, timeout=30)
        db = cls(pool=pool, schema=schema)
        await db.migrate()
        return db

    async def close(self) -> None:
        await self.pool.close()

    async def migrate(self) -> None:
        async with self.pool.connection() as conn:
            exists = await (await conn.execute("SELECT 1 FROM pg_namespace WHERE nspname = %s", (self.schema,))).fetchone()
            if not exists:  # in production the init script creates it; tests/dev may create their own schema
                await conn.execute(f"CREATE SCHEMA {self.schema}")
            await conn.execute("CREATE TABLE IF NOT EXISTS civix_agent_migrations (version INTEGER PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())")
            rows = await (await conn.execute("SELECT version FROM civix_agent_migrations")).fetchall()
            applied = {r["version"] for r in rows}
            for version, sql in MIGRATIONS:
                if version in applied:
                    continue
                async with conn.transaction():
                    await conn.execute(sql, prepare=False)
                    await conn.execute("INSERT INTO civix_agent_migrations (version) VALUES (%s)", (version,))

    async def checkpointer(self) -> AsyncPostgresSaver:
        saver = AsyncPostgresSaver(self.pool)  # type: ignore[arg-type]
        await saver.setup()
        return saver

    async def ping(self) -> bool:
        try:
            async with self.pool.connection() as conn:
                await conn.execute("SELECT 1")
            return True
        except Exception:  # noqa: BLE001 - health probe
            return False

    # ---------------------------------------------------------------- task runs
    async def upsert_run(self, task_id: str, application_id: str, user_id: str, service_code: str, status: str) -> None:
        async with self.pool.connection() as conn:
            await conn.execute(
                """INSERT INTO task_runs (task_id, application_id, user_id, service_code, status) VALUES (%s,%s,%s,%s,%s)
                   ON CONFLICT (task_id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()""",
                (task_id, application_id, user_id, service_code, status),
            )

    async def set_run_status(self, task_id: str, status: str) -> None:
        async with self.pool.connection() as conn:
            await conn.execute("UPDATE task_runs SET status=%s, updated_at=now() WHERE task_id=%s", (status, task_id))

    async def get_run(self, task_id: str) -> dict[str, Any] | None:
        async with self.pool.connection() as conn:
            return await (await conn.execute("SELECT * FROM task_runs WHERE task_id=%s", (task_id,))).fetchone()

    async def runs_with_status(self, *statuses: str) -> list[dict[str, Any]]:
        async with self.pool.connection() as conn:
            return await (await conn.execute("SELECT * FROM task_runs WHERE status = ANY(%s)", (list(statuses),))).fetchall()

    # ---------------------------------------------------------------- outbox
    async def enqueue_events(self, events: list[dict[str, Any]]) -> None:
        async with self.pool.connection() as conn:
            async with conn.cursor() as cur:
                await cur.executemany(
                    "INSERT INTO event_outbox (event_id, task_id, payload) VALUES (%s,%s,%s) ON CONFLICT (event_id) DO NOTHING",
                    [(e["eventId"], e["taskId"], json.dumps(e)) for e in events],
                )

    async def pending_events(self, limit: int = 100) -> list[dict[str, Any]]:
        async with self.pool.connection() as conn:
            return await (await conn.execute(
                "SELECT id, payload FROM event_outbox WHERE delivered_at IS NULL ORDER BY id LIMIT %s", (limit,))).fetchall()

    async def mark_delivered(self, ids: list[int]) -> None:
        if ids:
            async with self.pool.connection() as conn:
                await conn.execute("UPDATE event_outbox SET delivered_at=now() WHERE id = ANY(%s)", (ids,))

    async def mark_attempt(self, ids: list[int]) -> None:
        if ids:
            async with self.pool.connection() as conn:
                await conn.execute("UPDATE event_outbox SET attempts = attempts + 1 WHERE id = ANY(%s)", (ids,))

    # ---------------------------------------------------------------- submission markers
    async def begin_submission(self, approval_id: str, task_id: str) -> bool:
        """Returns False if a submission was already attempted for this approval (never click twice)."""
        async with self.pool.connection() as conn:
            row = await (await conn.execute(
                "INSERT INTO submission_attempts (approval_id, task_id) VALUES (%s,%s) ON CONFLICT DO NOTHING RETURNING approval_id",
                (approval_id, task_id))).fetchone()
            return row is not None

    async def finish_submission(self, approval_id: str, outcome: str) -> None:
        async with self.pool.connection() as conn:
            await conn.execute("UPDATE submission_attempts SET completed_at=now(), outcome=%s WHERE approval_id=%s", (outcome, approval_id))
