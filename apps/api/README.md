# apps/api — CIVIX core API (Spring Boot 4.1, Java 25)

System of record: auth (JWT access cookie + rotating refresh tokens), profile with field provenance, consent,
documents (S3/MinIO with SSE), registry mirror (synced from the agent), eligibility history, applications, agent tasks,
execution events with SSE replay, approvals (signs approval tokens), receipts, audit, notifications, dashboard,
privacy deletion, admin. Contracts: `docs/API.md`, `docs/AGENT_CONTRACTS.md`, state machines: `docs/WORKFLOWS.md`.

> **Honest status:** this module was written in a sandbox where Maven Central was blocked, so it has **not been compiled
> or run**. Only its dependency-free classes were compiled and exercised. Expect to fix some Spring Boot 4.1 / Jackson 3 /
> Testcontainers 2 API drift on the first build. See `docs/IMPLEMENTATION_STATUS.md`.

## Build & test
```bash
./mvnw verify          # unit tests + ApiLifecycleIT (Testcontainers: needs Docker)
./mvnw spring-boot:run # needs the env vars from /.env.example (DB, JWT_SECRET, tokens, S3, Redis)
```
Flyway migrations live in `src/main/resources/db/migration` (V1–V9) and were verified on PostgreSQL 16 as the
least-privileged `civix_api` role.

## Layout
`com.civix.<module>.{api,dto,domain,repository,service}` for auth, citizen, consent, documents, services, eligibility,
applications, approvals, agent, receipts, audit, notifications, dashboard, privacy, admin; shared code in `common`, wiring in `config`.
Internal agent callbacks: `agent/api/InternalAgentController` (`/internal/v1/**`, bearer `API_INTERNAL_TOKEN`).
