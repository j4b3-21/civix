-- V9: audit events and notifications
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users (id) ON DELETE SET NULL,   -- retained (pseudonymous) after account deletion
    category        VARCHAR(16) NOT NULL CHECK (category IN ('AUTH','PROFILE','CONSENT','DOCUMENT','APPLICATION','AGENT','APPROVAL','PRIVACY','ADMIN')),
    action          VARCHAR(64) NOT NULL,
    actor_type      VARCHAR(8)  NOT NULL CHECK (actor_type IN ('USER','AGENT','SYSTEM','ADMIN')),
    actor_id        VARCHAR(64),
    resource_type   VARCHAR(32),
    resource_id     VARCHAR(64),
    outcome         VARCHAR(8)  NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILURE')),
    summary         VARCHAR(500) NOT NULL,
    correlation_id  VARCHAR(64),
    ip_hash         CHAR(64),                                         -- SHA-256(ip + pepper); raw IPs are not stored
    metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_events_user ON audit_events (user_id, occurred_at DESC);
CREATE INDEX ix_audit_events_category ON audit_events (category, occurred_at DESC);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    link        VARCHAR(500),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
