-- V8: approvals and application receipts
CREATE TABLE approvals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             UUID        NOT NULL REFERENCES agent_tasks (id) ON DELETE CASCADE,
    application_id      UUID        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind                VARCHAR(24) NOT NULL CHECK (kind IN ('PREPARATION','DOCUMENT_UPLOAD','FINAL_SUBMISSION')),
    action_class        VARCHAR(16) NOT NULL CHECK (action_class IN ('READ_ONLY','REVERSIBLE','SENSITIVE','CONSEQUENTIAL')),
    summary             VARCHAR(500) NOT NULL,
    details             JSONB       NOT NULL DEFAULT '{}'::jsonb,
    payload_digest      CHAR(64)    NOT NULL,
    data_version        INTEGER     NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','INVALIDATED','CONSUMED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    decided_at          TIMESTAMPTZ,
    decision_reason     VARCHAR(500),
    consumed_at         TIMESTAMPTZ,
    invalidated_reason  VARCHAR(64),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_approvals_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_final_submission_is_consequential CHECK (kind <> 'FINAL_SUBMISSION' OR action_class = 'CONSEQUENTIAL')
);
CREATE INDEX ix_approvals_user_status ON approvals (user_id, status, created_at DESC);
CREATE INDEX ix_approvals_task ON approvals (task_id);
CREATE UNIQUE INDEX ux_approvals_one_pending_per_kind ON approvals (task_id, kind) WHERE status = 'PENDING';

CREATE TABLE application_receipts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id        UUID         NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    task_id               UUID         REFERENCES agent_tasks (id) ON DELETE SET NULL,
    reference_number      VARCHAR(128) NOT NULL,
    acknowledgement_text  VARCHAR(4000),
    portal_url            VARCHAR(1000) NOT NULL,
    verification_method   VARCHAR(32)  NOT NULL,
    evidence              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    artifact_storage_key  VARCHAR(512),
    is_test_environment   BOOLEAN      NOT NULL,
    verified_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_application_receipts_reference UNIQUE (application_id, reference_number)
);
