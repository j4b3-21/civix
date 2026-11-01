-- V6: eligibility evaluations, applications, application status history
CREATE TABLE eligibility_evaluations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    service_id        UUID        NOT NULL REFERENCES service_definitions (id) ON DELETE CASCADE,
    service_version   INTEGER     NOT NULL,
    outcome           VARCHAR(24) NOT NULL CHECK (outcome IN ('ELIGIBLE','INELIGIBLE','NEEDS_MORE_INFORMATION','UNVERIFIED')),
    result            JSONB       NOT NULL,
    engine_version    VARCHAR(16) NOT NULL,
    evaluated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_eligibility_user_service ON eligibility_evaluations (user_id, service_id, evaluated_at DESC);

CREATE TABLE applications (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    service_id           UUID        NOT NULL REFERENCES service_definitions (id) ON DELETE RESTRICT,
    service_code         VARCHAR(80) NOT NULL,
    service_version      INTEGER     NOT NULL,
    status               VARCHAR(24) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','IN_PROGRESS','ACTION_REQUIRED','SUBMITTED','SUBMISSION_UNCERTAIN','FAILED','CANCELLED')),
    data_version         INTEGER     NOT NULL DEFAULT 1,
    reference_number     VARCHAR(128),
    eligibility_outcome  VARCHAR(24),
    required_action      JSONB,
    is_test_environment  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at         TIMESTAMPTZ,
    version              BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_applications_user ON applications (user_id, last_activity_at DESC);
CREATE UNIQUE INDEX ux_applications_one_open_per_service ON applications (user_id, service_id)
    WHERE status NOT IN ('SUBMITTED','FAILED','CANCELLED');

CREATE TABLE application_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status     VARCHAR(24),
    to_status       VARCHAR(24) NOT NULL,
    reason          VARCHAR(255),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_application_status_history_app ON application_status_history (application_id, changed_at);
