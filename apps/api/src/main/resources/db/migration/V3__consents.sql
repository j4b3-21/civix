-- V3: consent records (append-only; the latest row per (user, purpose) is authoritative)
CREATE TABLE consent_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose         VARCHAR(32) NOT NULL CHECK (purpose IN ('PROFILE_PROCESSING','DOCUMENT_PROCESSING','ELIGIBILITY_EVALUATION','AGENT_EXECUTION','PORTAL_DOCUMENT_UPLOAD')),
    granted         BOOLEAN     NOT NULL,
    notice_version  VARCHAR(16) NOT NULL,
    scope           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_consent_records_user_purpose ON consent_records (user_id, purpose, created_at DESC);
