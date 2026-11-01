# CIVIX — Implementation status (truthful)

Last updated: 2026-11-01. "Tested" means actually executed in the authoring environment; nothing below is assumed.

## Authoring-environment constraints
- The anonymous `minio/minio` image pull is denied in this environment. Compose uses a digest-pinned,
  MinIO-compatible `coollabsio/minio` image instead; no official government workflow was executed.
- Local JDK was 21; the code targets Java 25.

## Definition of complete — checklist

| Item | Status | Evidence / gap |
|---|---|---|
| Frontend builds and starts | ✅ builds | `npm run build` → 21 routes; `typecheck`, `lint` clean. Not started against a live API. |
| Backend builds and starts | ✅ API verify | `cd apps/api && ./mvnw -B verify` → 17 unit tests and 2 `ApiLifecycleIT` tests passed. |
| Agent service starts | ✅ (in tests) | FastAPI app exercised via TestClient; full lifespan with real Postgres/S3 not run in compose. |
| PostgreSQL migrations succeed | ✅ | V1–V9 applied on PostgreSQL 16 as the least-privileged `civix_api` role; agent schema + LangGraph tables created as `civix_agent`; cross-schema access denied (verified). |
| Redis works | ⚠️ untested | Used only for API rate limiting (fails open). Not exercised. |
| Object storage works | ✅ compose verified | `make clean && make up-test`; bucket `civix-private` exists and an uploaded object reported `X-Amz-Server-Side-Encryption: AES256`. |
| Registration and login work | ❌ not verified | Implemented in `apps/api/auth`; covered by the (unrun) integration test. |
| Citizen profile works | ❌ not verified | API implemented; UI built and unit-tested. |
| Consent system works | ⚠️ partial | Enforcement in the agent tested (`CONSENT_REQUIRED`); API side unrun. |
| Document upload works | ❌ not verified | API implemented; UI precheck tested. |
| Document extraction works | ✅ | 10 tests: PDF text layer, scanned PDF → Tesseract, PNG OCR, classification, misfiling, Aadhaar suppression, invalid files. |
| Service registry works | ✅ | 7 definitions validated (JSON Schema + Pydantic + catalog cross-checks); 11 registry tests. |
| Official service discovery works | ✅ (registry-backed) | Discovery ranks registry entries backed by cited official sources; optional Brave live sources and a read-only data.gov.in resource client are wired, but no external call was made without credentials. |
| Eligibility engine works | ✅ | 30 tests: all 11 operators, 4 outcomes, UNKNOWN handling, provenance-based UNVERIFIED. |
| Application creation works | ❌ not verified | API implemented. |
| LangGraph orchestration works | ✅ | 12 routing tests + 11 end-to-end tests. |
| Checkpoint persistence works | ✅ | 4 PostgreSQL tests: resume after "restart", crash re-drive, outbox survives API outage, double-submit marker. |
| Browser agent executes actual actions | ✅ | Real Chromium against the local CIVIX Test Portal: OTP sign-in, form fill, select, uploads, declaration, review check, approval-gated submit. |
| Browser recovery works | ✅ | E2E: changed layout, expired session (re-auth), browser crash (new session), HTTP 503, wrong OTP, unrecoverable missing field. |
| Approval system works | ✅ agent / ❌ API | Agent + browser worker verification tested (forged, wrong kind, wrong task, expired, digest mismatch, reuse). Java signer matches the cross-language vector (executed). API decision flow unrun. |
| SSE updates work | ❌ not verified | API `SseHub` + replay implemented; web SSE reducer/reconnect/backfill unit-tested. |
| Agent Control Room works | ⚠️ partial | Built and component-tested; never connected to a live stream. |
| Actual outcome verification works | ✅ (test portal) | Reference read from the DOM, regex-validated, cross-checked on the status page; missing acknowledgement → `SUBMISSION_UNCERTAIN`, no receipt. |
| Receipt storage works when receipts exist | ⚠️ partial | Agent posts receipts (tested with fake API); API storage path unrun. |
| Authorization tests pass | ⚠️ partial | Agent internal-token and browser-worker policy tests pass. API authorization tests written (`ApiLifecycleIT`), not run. |
| Critical end-to-end tests pass | ⚠️ partial | Agent ↔ browser ↔ test portal E2E pass (with a fake API). Full web ↔ API ↔ agent E2E not run. |
| Docker Compose works | ✅ stack verified | `make clean && make up-test`; PostgreSQL, Redis, storage, API, agent, browser worker, test portal and web were healthy. API registry sync and agent readiness were verified. |
| Documentation is complete | ✅ | This folder. |
| No fake integrations represented as complete | ✅ | This table. |

## Test inventory (executed)

| Suite | Count | Command |
|---|---|---|
| Agent (pytest) | **97 passed**, 4 skipped, 20 deselected | `cd apps/agent && .venv/bin/pytest -q -m 'not postgres and not browser and not e2e'` |
| Web (vitest) | **54 passed**, 7 files | `cd apps/web && npm test` |
| Web build | ✅ | `npm run typecheck && npm run lint && npm run build` |
| Data.gov resource client | ✅ | 3 targeted tests; request construction, pagination validation, missing-key handling, and official-domain recognition |
| API pure-Java harness | 20 checks passed | ad-hoc `javac` run of the classes with no Spring dependency (not committed; the same checks exist as JUnit tests) |
| API JUnit + Testcontainers | **19 passed** | `cd apps/api && ./mvnw -B verify` |
| Compose smoke checks | ✅ | `docker compose --profile test ps`; web health, agent readiness, registry sync and MinIO SSE object metadata verified. |
| Live prompt section 1 — LLM layer | ✅ partial | Anthropic default, schema-constrained tool calls, Anthropic streaming, per-task/per-user cost limits, and production readiness failure on a missing key implemented; 34 targeted agent tests pass. No live model call was made because `ANTHROPIC_API_KEY` is not configured. |

## Government services
| Code | Verification | Automation | Note |
|---|---|---|---|
| IN_CENTRAL_MOE_CSSS | SOURCE_VERIFIED | GUIDED | Rules from PM-USP CSSS guidelines (2022-23 onwards). |
| IN_CENTRAL_AICTE_SAKSHAM | SOURCE_VERIFIED | GUIDED | Rules from 2020-21 guidelines; current notice not retrieved. |
| IN_CENTRAL_AICTE_PRAGATI | DRAFT | GUIDED | Editions disagree on amount and income wording → results UNVERIFIED. |
| IN_CENTRAL_MSJE_PMS_SC | DRAFT | GUIDED | Guidelines cover up to 2025-26; a revision is being considered. |
| IN_HR_SEWA_PMS_SC | DRAFT | NOT_AUTOMATED | State scheme; current route not verified. |
| IN_CENTRAL_MSME_UDYAM | SOURCE_VERIFIED | GUIDED | Official portal; Aadhaar OTP required. |
| IN_TEST_PORTAL_SCHOLARSHIP | WORKFLOW_VERIFIED (test only) | TEST_ONLY | Local test portal; hidden unless `CIVIX_ENABLE_TEST_SERVICES=true`. |

**No real portal workflow is `ACTIVE`.** Automating NSP or any other portal needs the owner's authorization and a supervised
verified run (see DEPLOYMENT.md → "Activating a real government workflow").

## What requires external credentials or access
- `ANTHROPIC_API_KEY` (or a local OpenAI-compatible model) — optional. Provider abstraction, startup validation and
  structured-output calls are implemented; **no live model call was made**. Everything works deterministically without it.
- Live discovery additionally requires a search-provider API key (Brave, Bing, Google Programmable Search, or Tavily);
  data.gov.in resource calls require `DATA_GOV_API_KEY` and a resource ID.
- Maven Central / Docker Hub access to build the API and images.
- Portal-owner authorization for any real workflow.

## Known limitations & next steps
1. Add and run the missing full web↔API↔agent Playwright E2E scenarios from `docs/COPILOT_PROMPT.md`.
2. SSE across multiple API instances relies on a 5-second DB poll fallback; add Redis pub/sub for lower latency.
3. Egress proxy for the browser worker (DNS-rebinding defence), upload malware scanning, citizen MFA.
4. Discovery does not crawl portals live; `OFFICIAL_SOURCE_FETCH_ENABLED` is reserved for a future official-source refresher.
5. pgvector column exists but no embedding provider is wired; ranking is lexical (BM25-style) + category/state/eligibility signals.
6. Legal review (DPDP Act notices, retention, grievance officer) before real citizen data.
