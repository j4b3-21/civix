-- V1: extensions, users, refresh tokens
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- pgvector is optional at migration time (present in the pgvector/pgvector image used by docker compose).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END $$;

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(320) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(120) NOT NULL,
    role                VARCHAR(16)  NOT NULL DEFAULT 'CITIZEN' CHECK (role IN ('CITIZEN', 'ADMIN')),
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'DELETED')),
    failed_login_count  INTEGER      NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    CHAR(64)     NOT NULL UNIQUE,         -- SHA-256 hex of the opaque token
    family_id     UUID         NOT NULL,                 -- rotation family for reuse detection
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,
    replaced_by   UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
