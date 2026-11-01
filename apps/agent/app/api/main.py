"""CIVIX agent service — internal API only (docs/AGENT_CONTRACTS.md §3). Never exposed to the public internet.

Run: uvicorn app.api.main:app --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Annotated, Any

import structlog
import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse, PlainTextResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app.agents import discovery_agent
from app.api.schemas import CancelRequest, DiscoveryRequest, EligibilityRequest, ExtractRequest, ResumeRequest, StartTaskRequest
from app.browser.client import BrowserClient, HttpBrowserClient
from app.clients.api_client import HttpApiClient
from app.config.logging import configure_logging
from app.config.settings import Settings, get_settings
from app.document_intelligence.pipeline import DocumentRejected, process
from app.orchestrator.events import EventSink
from app.orchestrator.graph import GraphDeps, build_graph
from app.orchestrator.runner import TaskRunner
from app.orchestrator.secrets import SecretInbox, TokenVault
from app.persistence.db import AgentDatabase
from app.policies.eligibility import evaluate_service
from app.retrieval.registry import Registry, load_registry
from app.retrieval.live.data_gov import DataGovClient
from app.retrieval.live.search import BraveSearch
from app.services.llm.base import LLMProvider, build_provider
from app.services.storage.s3 import ObjectStorage, S3Storage

log = structlog.get_logger("civix.agent")


@dataclass
class Container:
    settings: Settings
    registry: Registry
    runner: TaskRunner | None
    storage: ObjectStorage
    llm: LLMProvider
    db: AgentDatabase | None = None
    browser: BrowserClient | None = None
    sink: EventSink | None = None
    live_search: BraveSearch | None = None
    data_gov: DataGovClient | None = None


async def build_container(settings: Settings) -> Container:
    settings.require_secrets("agent_internal_token", "api_internal_token", "browser_worker_token", "approval_signing_secret")
    registry = load_registry(settings.service_registry_path, env={"TEST_PORTAL_URL": settings.test_portal_url})
    db = await AgentDatabase.open(settings.agent_database_url, settings.agent_db_schema)
    saver = await db.checkpointer()
    api = HttpApiClient(settings.api_internal_url, settings.api_internal_token.get_secret_value())
    sink = EventSink(api, db)
    sink.start_background_flush()
    browser = HttpBrowserClient(settings.browser_worker_url, settings.browser_worker_token.get_secret_value())
    storage = S3Storage(endpoint=settings.s3_endpoint, region=settings.s3_region, bucket=settings.s3_bucket,
                        access_key=settings.s3_access_key.get_secret_value(), secret_key=settings.s3_secret_key.get_secret_value(),
                        force_path_style=settings.s3_force_path_style, sse=settings.s3_sse)
    llm = build_provider(settings)
    live_search = BraveSearch(settings.brave_search_api_key.get_secret_value())
    data_gov = DataGovClient(settings.data_gov_api_key.get_secret_value(), base_url=settings.data_gov_base_url)
    if settings.llm_provider != "none" and not llm.available:
        log.warning("llm.not_configured", provider=settings.llm_provider)
    deps = GraphDeps(settings=settings, registry=lambda: registry, api=api, sink=sink, browser=browser, storage=storage,
                     inbox=SecretInbox(), vault=TokenVault(), db=db, llm=llm)
    runner = TaskRunner(build_graph(deps, saver), deps, settings.max_concurrent_tasks)
    return Container(settings=settings, registry=registry, runner=runner, storage=storage, llm=llm, db=db, browser=browser, sink=sink,
                     live_search=live_search, data_gov=data_gov)


def create_app(container: Container | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        configure_logging("agent")
        if container is None:
            c = await build_container(get_settings())
            app.state.container = c
            resumed = await c.runner.recover_on_startup() if c.runner else 0
            log.info("agent.started", status=f"resumed {resumed} run(s)", service_code=f"{len(c.registry.services)} services")
        else:
            app.state.container = container
        yield
        c = app.state.container
        if c.sink:
            await c.sink.stop()
        if c.data_gov:
            await c.data_gov.close()
        if c.db and container is None:
            await c.db.close()

    app = FastAPI(title="CIVIX agent (internal)", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    def ctr(request: Request) -> Container:
        return request.app.state.container

    def require_token(request: Request, authorization: Annotated[str | None, Header()] = None) -> None:
        expected = ctr(request).settings.agent_internal_token.get_secret_value()
        given = (authorization or "").removeprefix("Bearer ").strip()
        if not expected or not hmac.compare_digest(given.encode(), expected.encode()):
            raise HTTPException(status_code=401, detail="invalid service token")

    auth = [Depends(require_token)]

    @app.exception_handler(DocumentRejected)
    async def _rejected(_: Request, exc: DocumentRejected) -> JSONResponse:
        return JSONResponse({"code": exc.code, "detail": str(exc)}, status_code=422)

    @app.get("/internal/v1/health")
    async def health(request: Request) -> dict[str, Any]:
        c = ctr(request)
        db_ok = await c.db.ping() if c.db else True
        storage_ok = await c.storage.ping()
        browser_ok = True
        if isinstance(c.browser, HttpBrowserClient):
            try:
                r = await c.browser._http.get("/internal/v1/health", timeout=3)  # noqa: SLF001
                browser_ok = r.status_code == 200
            except Exception:  # noqa: BLE001 - health probe
                browser_ok = False
        llm_ok = c.llm.available or c.settings.civix_env != "production" or c.settings.llm_provider == "none"
        checks = {"database": "ok" if db_ok else "down", "storage": "ok" if storage_ok else "down",
                  "browserWorker": "ok" if browser_ok else "down",
                  "llm": "configured" if c.llm.available else ("missing_key" if c.settings.civix_env == "production" else "not_configured"),
                  "registry": f"{len(c.registry.services)} services"}
        return {"status": "ok" if db_ok and storage_ok and browser_ok and llm_ok else "degraded", "checks": checks}

    @app.get("/internal/v1/ready")
    async def ready(request: Request) -> JSONResponse:
        h = await health(request)
        return JSONResponse(h, status_code=200 if h["status"] == "ok" else 503)

    @app.get("/internal/v1/metrics", dependencies=auth)
    def metrics() -> PlainTextResponse:
        return PlainTextResponse(generate_latest().decode(), media_type=CONTENT_TYPE_LATEST)

    # ------------------------------------------------------------------ registry
    @app.get("/internal/v1/registry/services", dependencies=auth)
    def registry_services(request: Request, includeTest: bool = False) -> dict[str, Any]:  # noqa: N803
        c = ctr(request)
        include = includeTest and c.settings.civix_enable_test_services
        return {"services": [{**s.api_view(), "contentHash": c.registry.content_hashes[s.code]} for s in c.registry.visible_services(include)],
                "profileFieldsVersion": c.registry.profile_fields_version, "generatedAt": c.registry.loaded_at.isoformat()}

    @app.get("/internal/v1/registry/profile-fields", dependencies=auth)
    def registry_fields(request: Request) -> dict[str, Any]:
        c = ctr(request)
        return {"version": c.registry.profile_fields_version, "fields": c.registry.profile_fields_view(),
                "jurisdictions": [j.wire() for j in c.registry.jurisdictions]}

    # ------------------------------------------------------------------ eligibility & discovery
    @app.post("/internal/v1/eligibility/evaluate", dependencies=auth)
    def eligibility(request: Request, body: EligibilityRequest) -> dict[str, Any]:
        c = ctr(request)
        svc = c.registry.get(body.service_code)
        if svc is None or (svc.is_test_service and not c.settings.civix_enable_test_services):
            raise HTTPException(status_code=404, detail="unknown service")
        return evaluate_service(svc, body.citizen_context, c.registry.profile_fields).wire()

    @app.post("/internal/v1/discovery", dependencies=auth)
    async def discovery(request: Request, body: DiscoveryRequest) -> dict[str, Any]:
        c = ctr(request)
        interp = await discovery_agent.interpret_with_llm(body.query, c.registry, c.llm)
        results = discovery_agent.search(body.query, c.registry, interpretation=interp, context=body.citizen_context,
                                         filters=body.filters.model_dump(mode="json"), limit=body.limit,
                                         include_test=body.include_test and c.settings.civix_enable_test_services)
        live = await c.live_search.search(body.query, count=body.limit) if c.live_search else []
        response = discovery_agent.discover_response(body.query, c.registry, interp, results)
        response["liveSources"] = [{"title": r.title, "url": r.url, "description": r.description} for r in live]
        return response

    @app.get("/internal/v1/data-gov/resources/{resource_id}", dependencies=auth)
    async def data_gov_resource(
        request: Request,
        resource_id: str,
        limit: int = 10,
        offset: int = 0,
    ) -> dict[str, Any]:
        c = ctr(request)
        if c.data_gov is None or not c.data_gov.available:
            raise HTTPException(status_code=503, detail="data.gov.in is not configured")
        try:
            return await c.data_gov.fetch(resource_id, limit=limit, offset=offset)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        except httpx.HTTPStatusError as exc:
            raise HTTPException(status_code=502, detail="data.gov.in request failed") from exc

    # ------------------------------------------------------------------ documents
    @app.post("/internal/v1/documents/extract", dependencies=auth)
    async def extract(request: Request, body: ExtractRequest) -> dict[str, Any]:
        c = ctr(request)
        try:
            data = await c.storage.get(body.storage_key)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=404, detail="document object not found") from exc
        if body.sha256 and hashlib.sha256(data).hexdigest() != body.sha256:
            raise HTTPException(status_code=409, detail="document content does not match its recorded hash")
        cf = {k: v.wire() for k, v in body.citizen_context.fields.items()} if body.citizen_context else {}
        r = await asyncio.to_thread(process, data, body.mime_type, body.declared_category, document_id=body.document_id,
                                    citizen_fields=cf, other_docs=[d.wire() for d in body.other_documents])
        return {
            "documentId": body.document_id,
            "classification": {"category": r.classification.value, "confidence": r.classification_confidence,
                               "declaredCategoryMatches": r.declared_category_matches},
            "fields": [{"key": f.key, "label": f.label, "value": f.value, "confidence": f.confidence, "profileField": f.profile_field} for f in r.fields],
            "discrepancies": r.discrepancies, "warnings": r.warnings, "engine": r.engine, "textCharacters": r.text_characters,
            "requiresReview": True,
        }

    # ------------------------------------------------------------------ tasks
    def runner(request: Request) -> TaskRunner:
        r = ctr(request).runner
        if r is None:
            raise HTTPException(status_code=503, detail="orchestrator unavailable")
        return r

    @app.post("/internal/v1/tasks", dependencies=auth, status_code=202)
    async def start_task(request: Request, body: StartTaskRequest) -> dict[str, Any]:
        r = runner(request)
        if await r.status(body.task_id):
            raise HTTPException(status_code=409, detail="task already exists")
        await r.start(task_id=body.task_id, application_id=body.application_id, user_id=body.user_id, service_code=body.service_code,
                      intent=body.intent.wire(), citizen_context=body.citizen_context.wire(), documents=[d.wire() for d in body.documents],
                      data_version=body.data_version)
        return {"taskId": body.task_id, "status": "QUEUED"}

    @app.get("/internal/v1/tasks/{task_id}", dependencies=auth)
    async def get_task(request: Request, task_id: str) -> dict[str, Any]:
        st = await runner(request).status(task_id)
        if st is None:
            raise HTTPException(status_code=404, detail="unknown task")
        return st

    @app.post("/internal/v1/tasks/{task_id}/resume", dependencies=auth, status_code=202)
    async def resume(request: Request, task_id: str, body: ResumeRequest) -> dict[str, Any]:
        r = runner(request)
        st = await r.status(task_id)
        if st is None:
            raise HTTPException(status_code=404, detail="unknown task")
        pending = st.get("pendingInterrupt") or {}
        if body.kind == "APPROVAL":
            if body.approval is None or pending.get("type") != "APPROVAL" or pending.get("approvalId") != body.approval.approval_id:
                raise HTTPException(status_code=409, detail="task is not waiting for this approval")
            await r.resume_approval(task_id, approval_id=body.approval.approval_id, decision=body.approval.decision.value, token=body.approval.token)
        elif body.kind == "CITIZEN_INPUT":
            if body.citizen_input is None or pending.get("type") != "CITIZEN_INPUT" or pending.get("requestId") != body.citizen_input.request_id:
                raise HTTPException(status_code=409, detail="task is not waiting for this input")
            await r.resume_citizen_input(task_id, request_id=body.citizen_input.request_id, value=body.citizen_input.value)
        else:
            # Only runs that crashed mid-graph can continue from their checkpoint; finished runs need a new task.
            if st.get("status") != "FAILED" or st.get("currentNode") is None:
                raise HTTPException(status_code=409, detail="task cannot be resumed; start a new run for the application")
            await r.retry(task_id)
        return {"taskId": task_id, "accepted": True}

    @app.post("/internal/v1/tasks/{task_id}/cancel", dependencies=auth, status_code=202)
    async def cancel(request: Request, task_id: str, body: CancelRequest | None = None) -> dict[str, Any]:
        await runner(request).cancel(task_id)
        return {"taskId": task_id, "accepted": True}

    return app


app = create_app()
