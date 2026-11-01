# CIVIX database

PostgreSQL 16 (+ pgvector). Two schemas, two roles, created by `infrastructure/docker/postgres/init/01-roles-and-schemas.sh`:

| Schema | Owner role | Managed by | Contents |
|---|---|---|---|
| `civix` | `civix_api` | Flyway (`apps/api/src/main/resources/db/migration`) | all business data |
| `agent` | `civix_agent` | agent startup migrations (`app/persistence/db.py`) + LangGraph `setup()` | checkpoints, task runs, event outbox, submission markers |

`civix_agent` cannot read `civix` (verified: `permission denied for schema civix`).

## Migrations (schema `civix`)
| File | Tables |
|---|---|
| V1 | `users`, `refresh_tokens` (+ pgcrypto, optional pgvector) |
| V2 | `citizen_profiles`, `profile_field_definitions`, `profile_fields` (value + status + source + source document + confidence), `profile_field_history` (no values) |
| V3 | `consent_records` (append-only) |
| V4 | `documents`, `document_extractions`, `document_access_events` |
| V5 | `service_definitions` (jsonb definition, generated tsvector, optional `embedding vector(384)`), `service_sources`, `eligibility_rules` |
| V6 | `eligibility_evaluations`, `applications` (one open per user+service), `application_status_history` |
| V7 | `agent_tasks` (one active per application), `execution_events` (identity `sequence` = SSE id), `agent_checkpoints` (references) |
| V8 | `approvals` (one PENDING per task+kind, FINAL_SUBMISSION ⇒ CONSEQUENTIAL), `application_receipts` |
| V9 | `audit_events` (ip hashed, user SET NULL on deletion), `notifications` |

Conventions: UUID PKs (`gen_random_uuid()`), `timestamptz`, `version` columns for optimistic locking, CHECK constraints for
every enum, partial unique indexes for "one active X", cascading deletes from `users` except audit.

All migrations were applied successfully against PostgreSQL 16 **as the least-privileged `civix_api` role**.

## Agent schema
`task_runs`, `event_outbox`, `submission_attempts`, `civix_agent_migrations`, and LangGraph's `checkpoints`,
`checkpoint_blobs`, `checkpoint_writes`, `checkpoint_migrations`. Checkpoints contain the citizen context needed to resume
(no OTPs, passwords, tokens or cookies).
