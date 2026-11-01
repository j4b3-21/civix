# Implementation plan

| Phase | Scope | State |
|---|---|---|
| 0 Inspection & contracts | Empty repo; AGENTS.md, API.md, AGENT_CONTRACTS.md, WORKFLOWS.md, env template; skills/agents/MCP inspected | done |
| 1 Foundation | Monorepo, Next.js, Spring Boot, FastAPI, Postgres/Redis/MinIO compose, health checks, Flyway | done (compose unrun) |
| 2 Core backend | Auth, profile + provenance, consent, documents, registry mirror, applications, approvals, receipts, audit, notifications | written, uncompiled |
| 3 Frontend | All public/auth/app pages wired to `/api/v1`, SSE control room, tests, build | done |
| 4 Service intelligence | Registry schema + 7 definitions with cited sources, discovery, document intelligence, eligibility engine, provenance | done |
| 5 Agent orchestration | Typed agents, LangGraph graph, PG checkpoints, interrupts, recovery, outbox events | done |
| 6 Browser execution | Playwright worker, allowlist/SSRF, staging, approval-gated submit, test portal E2E | done |
| 7 Human approval | Policies, tokens, invalidation on data change, rejection/expiry/resume | done (API side unrun) |
| 8 Live control room | SSE hub with Last-Event-ID replay, control room UI | written; UI tested |
| 9 Integration | Agent↔browser↔portal E2E done; full web↔API↔agent pending a compiled API | partial |
| 10 Deployment | DEPLOYMENT.md, nginx TLS profile, env guide, checklist | done |
| 11 Quality review | IMPLEMENTATION_STATUS.md | done |

Next: see "Known limitations & next steps" in IMPLEMENTATION_STATUS.md.
