-- V7: agent tasks, execution events, checkpoint references, agent schema
CREATE TABLE agent_tasks (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id         UUID        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id                UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status                 VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','WAITING_APPROVAL','WAITING_CITIZEN','COMPLETED','FAILED','CANCELLED')),
    intent                 VARCHAR(16) NOT NULL DEFAULT 'APPLY' CHECK (intent IN ('APPLY','DISCOVER')),
    current_step           VARCHAR(128),
    current_agent          VARCHAR(32),
    pending_citizen_input  JSONB,
    last_observation       JSONB,
    error_code             VARCHAR(64),
    error_message          VARCHAR(1000),
    error_retryable        BOOLEAN,
    recovery_attempts      INTEGER     NOT NULL DEFAULT 0,
    last_event_sequence    BIGINT      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at             TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at           TIMESTAMPTZ,
    cancel_requested_at    TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_agent_tasks_user ON agent_tasks (user_id, created_at DESC);
CREATE INDEX ix_agent_tasks_application ON agent_tasks (application_id, created_at DESC);
CREATE INDEX ix_agent_tasks_status ON agent_tasks (status);
CREATE UNIQUE INDEX ux_agent_tasks_one_active_per_application ON agent_tasks (application_id)
    WHERE status IN ('QUEUED','RUNNING','WAITING_APPROVAL','WAITING_CITIZEN');

CREATE TABLE execution_events (
    sequence      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      UUID         NOT NULL UNIQUE,
    task_id       UUID         NOT NULL REFERENCES agent_tasks (id) ON DELETE CASCADE,
    event_type    VARCHAR(40)  NOT NULL,
    agent         VARCHAR(32)  NOT NULL,
    step          VARCHAR(128),
    message       VARCHAR(1000) NOT NULL,
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_execution_events_task_seq ON execution_events (task_id, sequence);

-- References to LangGraph checkpoints (the checkpoints themselves live in schema "agent", owned by the agent role).
CREATE TABLE agent_checkpoints (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id        UUID        NOT NULL REFERENCES agent_tasks (id) ON DELETE CASCADE,
    thread_id      VARCHAR(64) NOT NULL,
    checkpoint_id  VARCHAR(64) NOT NULL,
    node           VARCHAR(64),
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_agent_checkpoints UNIQUE (task_id, checkpoint_id)
);

-- Schema "agent" is created by infrastructure/docker/postgres/init (owned by role civix_agent), not by the API role.
