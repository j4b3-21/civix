# packages/contracts

Machine-readable contracts shared by the three apps. The prose source of truth is `docs/AGENT_CONTRACTS.md` and `docs/API.md`.

| File | What | Generated from |
|---|---|---|
| `openapi/agent-internal.openapi.json` | Agent internal API (Spring → agent) | `apps/agent/app/api/main.py` (FastAPI) |
| `openapi/browser-worker.openapi.json` | Browser worker API (agent → worker) | `apps/agent/app/browser/worker_api.py` |
| `events/execution-event.schema.json` | Execution event ingestion schema | `apps/agent/app/contracts/common.py` enums |
| `json-schema/service-definition.schema.json` | Service registry definition | copy of `service-registry/schemas/` |

The public API's OpenAPI document is served live by the Spring app at `/v3/api-docs` (springdoc). It is not checked in
here because it could not be generated in the authoring environment (the Java module was not compiled there).

Regenerate the agent specs: `cd apps/agent && python -m scripts.export_contracts`.
