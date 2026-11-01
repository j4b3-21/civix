# apps/agent — CIVIX agent service + browser worker

Python 3.12+, FastAPI, Pydantic v2, LangGraph 1.2 (PostgreSQL checkpointer), Playwright 1.63, Tesseract (eng+hin).

| Module | Responsibility |
|---|---|
| `app/api/` | Internal API (`/internal/v1/*`, bearer `AGENT_INTERNAL_TOKEN`) — docs/AGENT_CONTRACTS.md §3 |
| `app/contracts/` | Typed Pydantic contracts (camelCase on the wire) |
| `app/retrieval/registry.py` | Loads + validates `service-registry/` (JSON Schema + Pydantic + catalog cross-checks) |
| `app/policies/` | Deterministic eligibility engine, approval-token verification, domain/SSRF policy, action classes |
| `app/agents/` | Profile, Discovery, Planning, Document, Recovery agents |
| `app/document_intelligence/` | Validation → text layer / OCR → classification → extraction → consistency |
| `app/orchestrator/` | LangGraph graph, runner (start/resume/cancel/restart recovery), events + outbox, secret holders |
| `app/browser/` | Browser worker: `manager.py` (isolated contexts, allowlist, staging, gated submit), `worker_api.py`, clients |
| `app/persistence/` | Schema `agent`: migrations, task runs, outbox, submission markers, checkpointer |
| `app/services/` | LLM provider abstraction (none / Anthropic / OpenAI-compatible local), S3 storage |

## Run
```bash
python3.12 -m venv .venv && . .venv/bin/activate && pip install -e ".[test]" && playwright install chromium
uvicorn app.api.main:app --port 8000          # agent (needs env from /.env.example)
uvicorn app.browser.worker_api:app --port 8001  # browser worker
```

## Test
```bash
# needs: tesseract-ocr (+hin), Chromium via Playwright, and for 4 tests a PostgreSQL database:
export CIVIX_TEST_DATABASE_URL=postgresql://user:pw@localhost:5432/civix_test
python -m pytest -q            # 116 tests; -m "not browser" for the fast subset
```
The browser and E2E tests start the CIVIX Test Portal (`infrastructure/test-portal`) in-process on a free port.

Regenerate contracts: `python -m scripts.export_contracts`.
