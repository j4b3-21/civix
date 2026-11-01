-- V2: citizen profiles, field catalog, field values with provenance, history
CREATE TABLE citizen_profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0
);

-- Synced from the agent registry (service-registry/profile-fields.yaml).
CREATE TABLE profile_field_definitions (
    field_key    VARCHAR(64) PRIMARY KEY,
    label        VARCHAR(120) NOT NULL,
    field_type   VARCHAR(16)  NOT NULL CHECK (field_type IN ('STRING','NUMBER','INTEGER','BOOLEAN','DATE','ENUM')),
    field_group  VARCHAR(32)  NOT NULL,
    purpose      VARCHAR(500) NOT NULL,
    sensitivity  VARCHAR(8)   NOT NULL CHECK (sensitivity IN ('LOW','MEDIUM','HIGH')),
    core         BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INTEGER     NOT NULL DEFAULT 0,
    definition   JSONB        NOT NULL,                 -- options, unit, min, max, pattern
    catalog_version VARCHAR(32) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE profile_fields (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    field_key           VARCHAR(64) NOT NULL,
    value               JSONB,                           -- null only transiently; MISSING rows are deleted
    status              VARCHAR(16) NOT NULL CHECK (status IN ('MISSING','PROVIDED','EXTRACTED','USER_CONFIRMED','VERIFIED')),
    source              VARCHAR(24) CHECK (source IN ('USER_INPUT','DOCUMENT_EXTRACTION','VERIFIED_SOURCE')),
    source_document_id  UUID,                            -- FK added in V4
    confidence          NUMERIC(4,3) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_profile_fields_user_key UNIQUE (user_id, field_key)
);
CREATE INDEX ix_profile_fields_user ON profile_fields (user_id);

CREATE TABLE profile_field_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    field_key       VARCHAR(64) NOT NULL,
    old_status      VARCHAR(16),
    new_status      VARCHAR(16) NOT NULL,
    source          VARCHAR(24),
    source_document_id UUID,
    changed_by      VARCHAR(16) NOT NULL CHECK (changed_by IN ('USER','AGENT','SYSTEM')),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
    -- values are intentionally NOT stored in history (data minimisation); only provenance transitions.
);
CREATE INDEX ix_profile_field_history_user ON profile_field_history (user_id, changed_at DESC);
