-- V5: service definitions (synced from the validated registry), sources, eligibility rules
CREATE TABLE service_definitions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(80)  NOT NULL,
    version              INTEGER      NOT NULL CHECK (version >= 1),
    name                 VARCHAR(255) NOT NULL,
    summary              VARCHAR(500) NOT NULL,
    description          TEXT         NOT NULL,
    department           VARCHAR(255) NOT NULL,
    ministry             VARCHAR(255),
    category             VARCHAR(16)  NOT NULL CHECK (category IN ('EDUCATION','SCHOLARSHIPS','CERTIFICATES','IDENTITY','WELFARE','AGRICULTURE','BUSINESS','LICENSES','EMPLOYMENT','UTILITIES','OTHER')),
    jurisdiction_level   VARCHAR(8)   NOT NULL CHECK (jurisdiction_level IN ('CENTRAL','STATE')),
    state_codes          TEXT[]       NOT NULL DEFAULT '{}',   -- ISO 3166-2:IN; empty = all-India
    official_url         VARCHAR(500) NOT NULL,
    portal_domains       TEXT[]       NOT NULL DEFAULT '{}',
    verification_status  VARCHAR(20)  NOT NULL CHECK (verification_status IN ('DRAFT','SOURCE_VERIFIED','WORKFLOW_VERIFIED','DEPRECATED')),
    automation_status    VARCHAR(16)  NOT NULL CHECK (automation_status IN ('NOT_AUTOMATED','GUIDED','TEST_ONLY','ACTIVE')),
    is_test_service      BOOLEAN      NOT NULL DEFAULT FALSE,
    deadline             JSONB,
    definition           JSONB        NOT NULL,                -- full validated definition
    content_hash         CHAR(64)     NOT NULL,
    last_verified_at     DATE,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,   -- false when removed from registry or superseded
    search_vector        TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(summary, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'C')
    ) STORED,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_service_definitions_code_version UNIQUE (code, version),
    CONSTRAINT ck_test_service_not_active CHECK (NOT (is_test_service AND automation_status = 'ACTIVE'))
);
CREATE UNIQUE INDEX ux_service_definitions_active_code ON service_definitions (code) WHERE active;
CREATE INDEX ix_service_definitions_category ON service_definitions (category) WHERE active;
CREATE INDEX ix_service_definitions_states ON service_definitions USING GIN (state_codes);
CREATE INDEX ix_service_definitions_search ON service_definitions USING GIN (search_vector);

CREATE TABLE service_sources (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id    UUID         NOT NULL REFERENCES service_definitions (id) ON DELETE CASCADE,
    url           VARCHAR(1000) NOT NULL,
    title         VARCHAR(500) NOT NULL,
    kind          VARCHAR(24)  NOT NULL CHECK (kind IN ('OFFICIAL_PORTAL','NOTIFICATION','GUIDELINES','FAQ','TEST_ENVIRONMENT')),
    retrieved_at  DATE         NOT NULL,
    CONSTRAINT ux_service_sources UNIQUE (service_id, url)
);

CREATE TABLE eligibility_rules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id    UUID         NOT NULL REFERENCES service_definitions (id) ON DELETE CASCADE,
    rule_id       VARCHAR(64)  NOT NULL,
    rule_version  INTEGER      NOT NULL DEFAULT 1,
    field_key     VARCHAR(64)  NOT NULL,
    operator      VARCHAR(24)  NOT NULL CHECK (operator IN ('EQUALS','NOT_EQUALS','GREATER_THAN','GREATER_THAN_OR_EQUAL','LESS_THAN','LESS_THAN_OR_EQUAL','IN','NOT_IN','EXISTS','DATE_BEFORE','DATE_AFTER')),
    value         JSONB,
    description   VARCHAR(1000) NOT NULL,
    source_url    VARCHAR(1000),
    CONSTRAINT ux_eligibility_rules UNIQUE (service_id, rule_id)
);

-- Optional semantic search column when pgvector is installed.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        EXECUTE 'ALTER TABLE service_definitions ADD COLUMN embedding vector(384)';
    END IF;
END $$;
