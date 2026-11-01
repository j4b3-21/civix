# CIVIX architecture

```
 Browser ──HTTPS──▶ web (Next.js 16, :3000) ──/api/v1 rewrite──▶ api (Spring Boot 4, :8080) ──▶ PostgreSQL (schema civix)
                                                                 │  ▲                          ├─▶ Redis (rate limits)
                                                SSE ◀────────────┘  │ /internal/v1 callbacks    └─▶ S3/MinIO (SSE-S3)
                                                                 ▼  │ (events, approvals, receipts)
                                                     agent (FastAPI + LangGraph, :8000) ──▶ PostgreSQL (schema agent: checkpoints, outbox)
                                                                 │                         └─▶ S3 (read documents, write screenshots)
                                                                 ▼ bearer token
                                                     browser-worker (Playwright/Chromium, :8001) ──▶ approved portal domains only
                                                                                                    (local: test-portal :8090)
```

## Responsibilities
- **web** — UI only. Same-origin calls to `/api/v1` (cookies are first-party). SSE for the Agent Control Room.
- **api** — system of record: auth (JWT access cookie + rotating refresh token), profile with field provenance,
  consent, documents (encrypted object storage), registry mirror, eligibility history, applications, agent tasks,
  execution events (persisted, replayable SSE), approvals (signs approval tokens), receipts, audit, notifications.
  Validates every task/application state transition (`docs/WORKFLOWS.md`).
- **agent** — registry validation (Pydantic + JSON Schema), deterministic eligibility engine, discovery,
  document intelligence (pypdf / pypdfium2 / Tesseract eng+hin), LangGraph orchestration with PostgreSQL
  checkpoints, durable event outbox. Never writes business tables — it reports to the API, which decides.
- **browser-worker** — one isolated BrowserContext per task; allowlist enforced on navigation *and* on every
  request (`context.route`); private-IP/SSRF blocking; popups closed, downloads/service workers disabled; uploads only
  from files staged with a `DOCUMENT_UPLOAD` token; `SUBMIT` only with a single-use `FINAL_SUBMISSION` token;
  consequential controls (submit/pay) cannot be clicked via `CLICK`.

## Agents (typed contracts in `apps/agent/app/contracts`)
| Agent | Module | Output |
|---|---|---|
| Profile | `agents/profile_agent.py` | `ProfileReport` (available / missing / unverified / provenance) |
| Discovery | `agents/discovery_agent.py` | ranked registry candidates + sources + retrieval dates |
| Eligibility | `policies/eligibility.py` | `EligibilityResult` per rule with evidence |
| Document | `agents/document_agent.py`, `document_intelligence/pipeline.py` | `DocumentCheck`, extraction result |
| Planning | `agents/planning_agent.py` | `ExecutionPlan` with digests per approval kind |
| Browser | `orchestrator/graph.py::browser_execution` + `browser/` | observe → act → verify per step |
| Recovery | `agents/recovery_agent.py` | `RecoveryDecision` (retry / re-auth / restart session / fail) |
| Verification | `orchestrator/graph.py::verification` | receipt from DOM + status-page cross-check, or *uncertain* |

## Key design decisions
1. **Determinism where it matters.** Eligibility, planning, approval gating and recovery limits are code. An LLM (optional)
   may interpret discovery queries and propose label mappings during recovery, but its answer is validated against the
   observed page and can never add services, change rules or widen permissions.
2. **Approvals are cryptographic, not UI state.** The API signs `HMAC(aid,tid,app,uid,kind,digest,exp)`; the agent and
   the browser worker verify it independently. The digest covers the exact values to be filled/uploaded/submitted, and
   the API invalidates approvals when profile or documents change (`data_version`).
3. **Exactly-once submission.** A DB marker (`agent.submission_attempts`) is written before the submit click. After a
   crash the workflow goes to verification, never back to submit.
4. **Secrets never checkpointed.** OTPs and approval tokens live only in process memory (`SecretInbox`, `TokenVault`);
   LangGraph resume payloads carry ids only. After a restart the citizen is asked again.
5. **Idempotent events.** Event ids are `uuid5(task, counter)` from checkpointed state, so a node re-executed after a crash
   re-emits the same ids and Spring de-duplicates. Events go through an outbox so an API outage loses nothing.
6. **Registry-driven services.** Adding a service = one YAML file (+ an adapter only if the portal needs custom handling).
   The orchestrator has no service-specific code.

## Networks (docker-compose)
`backend` is `internal: true` (no internet). Only `browser-worker` joins `browser-egress`; only `agent` joins
`agent-egress` (LLM / official source fetches). Only `web` publishes a port (127.0.0.1:3000).
