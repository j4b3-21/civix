-- V4: documents, extractions, access log
CREATE TABLE documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name          VARCHAR(255) NOT NULL,
    category           VARCHAR(32)  NOT NULL CHECK (category IN ('IDENTITY_PROOF','ADDRESS_PROOF','INCOME_CERTIFICATE','CASTE_CERTIFICATE','ACADEMIC_RECORD','BANK_DOCUMENT','RESIDENCE_CERTIFICATE','DISABILITY_CERTIFICATE','PHOTOGRAPH','OTHER')),
    mime_type          VARCHAR(64)  NOT NULL CHECK (mime_type IN ('application/pdf','image/png','image/jpeg')),
    size_bytes         BIGINT       NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    sha256             CHAR(64)     NOT NULL,
    storage_key        VARCHAR(512) NOT NULL UNIQUE,
    encryption         VARCHAR(32)  NOT NULL DEFAULT 'SSE-S3',
    extraction_status  VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (extraction_status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    review_status      VARCHAR(16)  NOT NULL DEFAULT 'NOT_REVIEWED' CHECK (review_status IN ('NOT_REVIEWED','REVIEWED')),
    uploaded_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_documents_user_active ON documents (user_id, uploaded_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE document_extractions (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id                UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    attempt                    INTEGER     NOT NULL,
    status                     VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    classification_category    VARCHAR(32),
    classification_confidence  NUMERIC(4,3),
    declared_category_matches  BOOLEAN,
    fields                     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    discrepancies              JSONB       NOT NULL DEFAULT '[]'::jsonb,
    warnings                   JSONB       NOT NULL DEFAULT '[]'::jsonb,
    review                     JSONB,                    -- {accepted:[keys], rejected:[keys], reviewedAt}
    engine                     VARCHAR(64),
    text_characters            INTEGER,
    error_message              VARCHAR(500),
    started_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at               TIMESTAMPTZ,
    CONSTRAINT ux_document_extractions_attempt UNIQUE (document_id, attempt)
);

-- Kept after document deletion for accountability, hence no FK on document_id.
CREATE TABLE document_access_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_type   VARCHAR(8)  NOT NULL CHECK (actor_type IN ('USER','AGENT','SYSTEM')),
    actor_id     VARCHAR(64),
    task_id      UUID,
    action       VARCHAR(16) NOT NULL CHECK (action IN ('UPLOAD','VIEW','DOWNLOAD','EXTRACT','PORTAL_UPLOAD','DELETE')),
    purpose      VARCHAR(64),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_document_access_document ON document_access_events (document_id, occurred_at DESC);
CREATE INDEX ix_document_access_user ON document_access_events (user_id, occurred_at DESC);

ALTER TABLE profile_fields
    ADD CONSTRAINT fk_profile_fields_source_document
    FOREIGN KEY (source_document_id) REFERENCES documents (id) ON DELETE SET NULL;
