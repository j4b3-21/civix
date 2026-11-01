# Prompt for GitHub Copilot (Agent mode) — finish CIVIX

You are completing CIVIX, a government-services platform monorepo. Most of it is built; your job is to make the
**whole stack compile, run and pass end-to-end tests**, without changing its design or weakening its safety rules.

## Read before doing anything
1. `AGENTS.md` — binding rules. Obey every "non-negotiable".
2. `docs/IMPLEMENTATION_STATUS.md` — the truthful status table. This is your work list.
3. `docs/API.md`, `docs/AGENT_CONTRACTS.md`, `docs/WORKFLOWS.md` — contracts. Implementations must match these; if
   you must change a contract, change the doc first and update all three apps in the same change.
4. `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/DEMO.md`.

## Current state
- `apps/agent` (Python/FastAPI/LangGraph/Playwright): complete, 116 tests passing. Treat it as the reference implementation.
- `apps/web` (Next.js 16): complete; typecheck, lint, 54 tests and `next build` pass. Never run against a live API.
- `apps/api` (Spring Boot 4.1, Java 25): ~230 files written but **never compiled** (Maven was unavailable when it was written).
  Expect API drift. Flyway migrations V1–V9 are verified on PostgreSQL 16 — do not edit them; add V10+ if needed.
- `docker-compose.yml`: written, never run.

## Hard rules
- Never fabricate services, rules, deadlines, reference numbers or receipts. Never set any real service to `automationStatus: ACTIVE`.
- Never automate a real government portal (NSP, Udyam, etc.). Browser automation is only for the local CIVIX Test Portal
  (`infrastructure/test-portal`), which must stay labelled as a test environment.
- Never bypass OTP/CAPTCHA/login/payment. Keep approval tokens verified in the agent AND the browser worker.
- Never log or persist OTPs, passwords, tokens, cookies or document text. Never return approval tokens to the browser.
- Don't "fix" a failing test by deleting it, skipping it or loosening a security assertion. Fix the code.
- Don't add stubs/mocks to production code paths to make something look like it works.
- Keep `apps/agent` tests green (`cd apps/agent && python -m pytest -q`) and `apps/web` checks green after every change.
- Small, focused commits with clear messages. Update `docs/IMPLEMENTATION_STATUS.md` truthfully as items are verified.

## Work plan — do these in order; each has an exit criterion

### 1. Make the Spring Boot API compile and its tests pass
- `cd apps/api && ./mvnw -B verify`. Fix compile errors one module at a time.
- Likely drift points: Spring Boot 4 package moves (health contributors `org.springframework.boot.health.contributor`,
  `@AutoConfigureMockMvc`, `EntityScan`), Jackson 3 (`tools.jackson.*`; annotations stay `com.fasterxml.jackson.annotation`),
  Spring Security 7 DSL, Hibernate 7 JSON/array mappings, Testcontainers 2 (`org.testcontainers.postgresql.PostgreSQLContainer`),
  springdoc 3. If a pinned version in `pom.xml` doesn't exist, choose the nearest stable compatible one and note it.
- Hibernate must pass `ddl-auto: validate` against the migrations: fix entity mappings, not the SQL.
- Exit: `./mvnw verify` green, including `ApprovalTokenSignerTest` (cross-language vector) and `ApiLifecycleIT`.

### 2. Add the missing API tests
- MockMvc/WebMvc authorization tests: unauthenticated → 401; another user's resource → 404; `/internal/**` without or with a wrong token → 401;
  missing CSRF header → 403; ADMIN-only endpoints → 403 for citizens.
- Service tests: approval decide (expired → 410, data-version mismatch → 409 + INVALIDATED, double decide → 409),
  event ingest (duplicate eventId ignored; illegal transition ignored and counted), refresh-token rotation + reuse detection,
  profile change bumps `data_version` and invalidates approvals, receipt is always `isTestEnvironment` for test services.
- Exit: all green.

### 3. Bring up the stack with Docker Compose
- `make env && make up-test`. Fix image tags, Dockerfiles, health checks, env wiring and network issues until every
  service is healthy (`docker compose ps`). Only `web` may publish a port.
- Verify: Flyway ran as `civix_api`; agent created schema `agent` tables as `civix_agent`; MinIO bucket exists with
  SSE; API registry sync loaded the services from the agent (`GET /api/v1/services`).
- Exit: `docker compose ps` all healthy, API and agent readiness endpoints OK.

### 4. Full-stack end-to-end test
- Create `e2e/` (Playwright Test, TypeScript) that runs against the compose stack with the `test` profile and automates the
  flow in `docs/DEMO.md` through the **web UI**: register → consents → profile → upload two documents → review extraction →
  assistant discovery → start the test-portal application → run agent → approve preparation and upload → enter OTP
  (read via the test portal's `/test-inbox`, e.g. with `make test-otp`) → type ACCEPT for the declaration → approve final
  submission → see the verified `TP-YYYY-NNNNNN` receipt marked "Test environment".
- Add scenarios: reject final approval (nothing submitted), `omit_reference` fault (shows "Submission not confirmed", no
  receipt), profile edit while an approval is pending (approval withdrawn), SSE reconnect (reload the control room mid-run,
  timeline is complete with no duplicates).
- Add `make test-e2e`. Exit: all scenarios pass twice in a row from a clean `make clean && make up-test`.

### 5. Fix every contract mismatch you find
- Wherever the web, API and agent disagree on a field, status code or enum, align them with `docs/API.md` /
  `docs/AGENT_CONTRACTS.md`, and add a test that would have caught it.

### 6. Hardening (only after 1–5 are green)
- SSE fan-out across API instances via Redis pub/sub (keep the DB replay for `Last-Event-ID`).
- Browser-worker egress proxy that enforces the portal allowlist at the network layer (DNS-rebinding defence).
- Upload malware scanning (ClamAV container) before extraction; reject infected files.
- Retention jobs: delete task screenshots after 90 days, finished-run checkpoints after 180 days.
- Optional: TOTP MFA for citizen accounts.
- CI: GitHub Actions workflow running agent pytest (with a Postgres service, Tesseract and Playwright Chromium),
  web checks, `./mvnw verify`, and the compose E2E.

### 7. Finish
- Update `docs/IMPLEMENTATION_STATUS.md`: mark only what you actually ran, with commands and results.
- Update README test counts. List anything still not done and why.

When you're blocked by something external (a credential, a portal's permission, a paid service), stop that item,
document it in IMPLEMENTATION_STATUS.md, and continue with the next one. Do not simulate it.
