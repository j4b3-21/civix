"""Regenerates packages/contracts/{openapi,events} from the agent code.  Run: cd apps/agent && python -m scripts.export_contracts"""

from __future__ import annotations

import json
from pathlib import Path

from fastapi.openapi.utils import get_openapi

from app.api.main import create_app
from app.browser import worker_api
from app.contracts.common import AgentName, ApplicationStatus, EventType, TaskStatus

OUT = Path(__file__).resolve().parents[3] / "packages" / "contracts"


def main() -> None:
    app = create_app(container=object())  # type: ignore[arg-type] - routes only, lifespan is not started
    (OUT / "openapi").mkdir(parents=True, exist_ok=True)
    (OUT / "events").mkdir(parents=True, exist_ok=True)
    spec = get_openapi(title="CIVIX agent internal API", version="0.1.0", routes=app.routes,
                       description="Internal only. Authorization: Bearer AGENT_INTERNAL_TOKEN. See docs/AGENT_CONTRACTS.md §3.")
    (OUT / "openapi" / "agent-internal.openapi.json").write_text(json.dumps(spec, indent=2))
    spec2 = get_openapi(title="CIVIX browser worker internal API", version="0.1.0", routes=worker_api.app.routes,
                        description="Internal only. Authorization: Bearer BROWSER_WORKER_TOKEN. See docs/AGENT_CONTRACTS.md §5.")
    (OUT / "openapi" / "browser-worker.openapi.json").write_text(json.dumps(spec2, indent=2))
    schema = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "https://civix.dev/schemas/execution-event/1.0.json",
        "title": "CIVIX execution event (agent → API ingestion form)",
        "description": "docs/AGENT_CONTRACTS.md §2. Never contains secrets, document text or field values.",
        "type": "object",
        "required": ["eventId", "taskId", "type", "timestamp", "agent", "message", "metadata"],
        "properties": {
            "eventId": {"type": "string", "format": "uuid"}, "taskId": {"type": "string", "format": "uuid"},
            "type": {"enum": [e.value for e in EventType]}, "timestamp": {"type": "string", "format": "date-time"},
            "agent": {"enum": [a.value for a in AgentName]}, "step": {"type": ["string", "null"], "maxLength": 128},
            "message": {"type": "string", "maxLength": 1000}, "metadata": {"type": "object"},
            "taskStatus": {"enum": [t.value for t in TaskStatus]}, "applicationStatus": {"enum": [s.value for s in ApplicationStatus]},
            "currentStep": {"type": "string"}, "currentAgent": {"enum": [a.value for a in AgentName]},
            "pendingCitizenInput": {"type": ["object", "null"]}, "observation": {"type": ["object", "null"]},
            "error": {"type": ["object", "null"], "properties": {"code": {"type": "string"}, "message": {"type": "string"}}},
        },
    }
    (OUT / "events" / "execution-event.schema.json").write_text(json.dumps(schema, indent=2))
    print(f"wrote contracts to {OUT}")


if __name__ == "__main__":
    main()
