"""Wires a real LangGraph + TaskRunner with test doubles for Spring and storage."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.memory import InMemorySaver

from app.browser.client import BrowserClient
from app.config.settings import Settings
from app.orchestrator.events import EventSink
from app.orchestrator.graph import GraphDeps, build_graph
from app.orchestrator.runner import TaskRunner
from app.orchestrator.secrets import SecretInbox, TokenVault
from app.services.storage.s3 import MemoryStorage

from .fakes import SECRET, FakeApi


@dataclass
class Harness:
    api: FakeApi
    storage: MemoryStorage
    runner: TaskRunner
    deps: GraphDeps
    graph: Any


def make_settings(**kw: Any) -> Settings:
    base = dict(civix_env="test", civix_enable_test_services=True, approval_signing_secret=SECRET, approval_ttl_seconds=600,
                node_timeout_seconds=60, browser_step_timeout_seconds=20, max_recovery_attempts_per_step=3, max_recovery_attempts_per_task=8)
    base.update(kw)
    return Settings(**base)


class _NoBrowser:
    async def has_session(self, session_id: str) -> bool:  # pragma: no cover - should not be reached in routing tests
        raise AssertionError("browser must not be used")

    def __getattr__(self, name: str) -> Any:
        raise AssertionError(f"browser.{name} must not be used")


def make_harness(registry, browser: BrowserClient | None = None, checkpointer: Any = None, db: Any = None, **settings: Any) -> Harness:
    api = FakeApi()
    storage = MemoryStorage()
    deps = GraphDeps(settings=make_settings(**settings), registry=lambda: registry, api=api, sink=EventSink(api, db), browser=browser or _NoBrowser(),
                     storage=storage, inbox=SecretInbox(), vault=TokenVault(), db=db)
    graph = build_graph(deps, checkpointer or InMemorySaver())
    runner = TaskRunner(graph, deps)
    return Harness(api, storage, runner, deps, graph)


def field(value: Any, status: str = "USER_CONFIRMED") -> dict[str, Any]:
    return {"value": value, "status": status, "source": "USER_INPUT"}


def citizen(**overrides: Any) -> dict[str, Any]:
    fields = {
        "full_name": field("Asha Verma"),
        "date_of_birth": field("2005-08-14"),
        "gender": field("FEMALE"),
        "email": field("asha@example.org"),
        "mobile_number": field("9876543210"),
        "state_code": field("IN-KA"),
        "education_level": field("UNDERGRADUATE"),
        "course_name": field("B.Sc. Physics"),
        "institution_name": field("Government Science College"),
        "class12_percentage": field(88.4),
        "annual_family_income": field(240000),
    }
    for k, v in overrides.items():
        if v is None:
            fields.pop(k, None)
        else:
            fields[k] = v
    return {"userId": "user-1", "fields": fields,
            "consents": ["PROFILE_PROCESSING", "DOCUMENT_PROCESSING", "ELIGIBILITY_EVALUATION", "AGENT_EXECUTION", "PORTAL_DOCUMENT_UPLOAD"]}


async def documents(storage: MemoryStorage) -> list[dict[str, Any]]:
    out = []
    for cat, name in (("INCOME_CERTIFICATE", "income.pdf"), ("ACADEMIC_RECORD", "marksheet.pdf")):
        doc_id = str(uuid.uuid4())
        key = f"users/user-1/documents/{doc_id}"
        await storage.put(key, b"%PDF-1.4\n% test fixture document\n", "application/pdf")
        out.append({"documentId": doc_id, "category": cat, "storageKey": key, "mimeType": "application/pdf", "fileName": name, "reviewed": True})
    return out


async def start(h: Harness, *, service: str = "IN_TEST_PORTAL_SCHOLARSHIP", ctx: dict[str, Any] | None = None,
                docs: list[dict[str, Any]] | None = None) -> str:
    task_id = str(uuid.uuid4())
    await h.runner.start(task_id=task_id, application_id=str(uuid.uuid4()), user_id="user-1", service_code=service,
                         intent={"type": "APPLY"}, citizen_context=ctx or citizen(), documents=docs if docs is not None else await documents(h.storage),
                         data_version=1)
    await h.runner.wait_idle(task_id)
    return task_id
