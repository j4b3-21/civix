# CIVIX — Government services, handled intelligently

CIVIX helps citizens in India **discover** government services, **check eligibility** deterministically against cited
official rules, **manage documents** with OCR extraction, and — where a workflow has been verified and is permitted —
have a **supervised browser agent fill and submit applications**, pausing for every consequential step.

> Other AI assistants explain government processes. CIVIX helps citizens actually complete them — with the citizen
> approving every consequential action, and never bypassing OTP, CAPTCHA, login or payment.

**Read first:** [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) — what is built, what was actually
tested, and what is not done. In short: the agent service and frontend are built and tested; the Spring Boot API now **compiles and passes its Testcontainers integration tests**;
and **no real
government portal is automated yet** — browser automation is proven end-to-end against the bundled, clearly labelled
local **CIVIX Test Portal**.

## Monorepo

```
apps/web        Next.js 16 (App Router, TS strict, Tailwind v4, shadcn-style UI, TanStack Query, SSE control room)
apps/api        Spring Boot 4.1 / Java 25 — auth, profile, consent, documents, services, applications, approvals,
                receipts, audit, notifications, SSE, Flyway migrations (schema "civix")
apps/agent      Python 3.12 / FastAPI — registry validation, deterministic eligibility engine, document intelligence
                (Tesseract OCR), discovery, LangGraph orchestration with PostgreSQL checkpoints (schema "agent"),
                and the isolated Playwright browser worker
service-registry  Versioned YAML service definitions + JSON Schema + profile-field catalog + 36 States/UTs
packages/contracts  OpenAPI (agent, browser worker), event JSON Schema, registry schema
infrastructure  Postgres init (least-privilege roles), test portal, nginx TLS proxy, scripts
docs            PRD, architecture, contracts, API, database, security, workflows, deployment, status, demo
```

## Quick start (local)

Requirements: Docker with Compose v2, ~6 GB RAM, internet access to Docker Hub, Maven Central, npm and PyPI.

```bash
make env                 # writes .env with freshly generated secrets (from .env.example)
make up-test             # docker compose --profile test up -d --build   (includes the CIVIX Test Portal)
open http://localhost:3000
```

`make up` (= `docker compose up -d --build`) starts everything except the test portal. Only the web app is published
(127.0.0.1:3000); Postgres, Redis, MinIO, the API, the agent and the browser worker stay on internal networks.

Try the full agent flow with [`docs/DEMO.md`](docs/DEMO.md).

## Tests

```bash
make test-agent    # 116 pytest tests (needs CIVIX_TEST_DATABASE_URL for the 4 checkpoint tests, Chromium, Tesseract)
make test-web      # 54 vitest tests
make test-api      # mvn verify: 17 unit tests + 2 Testcontainers integration tests (needs Docker)
```

## Principles (binding — see AGENTS.md)

- No fabricated services, rules, deadlines, references or receipts. Every rule cites an official source with a retrieval date.
- Eligibility is deterministic; missing data stays UNKNOWN; an LLM may explain but never decides.
- Approval tokens (HMAC, bound to task, kind, payload digest and expiry) are verified by the agent **and** the browser worker.
- OTP/CAPTCHA/login/declarations are citizen-controlled; payments are never automated.
- Secrets never enter logs, events or checkpoints.

## Documentation

[PRD](docs/PRD.md) · [Architecture](docs/ARCHITECTURE.md) · [Agent contracts](docs/AGENT_CONTRACTS.md) ·
[Public API](docs/API.md) · [Database](docs/DATABASE.md) · [Workflows](docs/WORKFLOWS.md) ·
[Security](docs/SECURITY.md) · [Deployment](docs/DEPLOYMENT.md) · [Implementation plan](docs/IMPLEMENTATION_PLAN.md) ·
[Status](docs/IMPLEMENTATION_STATUS.md) · [Demo](docs/DEMO.md) · [AGENTS.md](AGENTS.md)
