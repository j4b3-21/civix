"""Browser worker service (separate container). Internal only: bearer-token protected, never exposed publicly.

Run: uvicorn app.browser.worker_api:app --host 0.0.0.0 --port 8001
"""

from __future__ import annotations

import hmac
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from app.browser.manager import BrowserActionFailure, BrowserManager
from app.config.logging import configure_logging
from app.config.settings import get_settings
from app.contracts.browser import ActionRequest, CreateSessionRequest, CreateSessionResponse
from app.contracts.registry import Expectation, Target

MAX_UPLOAD_BYTES = 10 * 1024 * 1024


def _manager() -> BrowserManager:
    return app.state.manager


@asynccontextmanager
async def lifespan(app: FastAPI):
    s = get_settings()
    configure_logging("browser-worker")
    if s.civix_env != "test":
        s.require_secrets("browser_worker_token", "approval_signing_secret")
    app.state.manager = BrowserManager(
        approval_secret=s.approval_signing_secret.get_secret_value(),
        private_host_allowlist=s.private_host_allowlist,
        headless=s.browser_headless,
        max_sessions=s.browser_max_sessions,
        idle_timeout_seconds=s.browser_session_idle_timeout_seconds,
    )
    await app.state.manager.start()
    yield
    await app.state.manager.stop()


app = FastAPI(title="CIVIX browser worker", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


def require_token(authorization: Annotated[str | None, Header()] = None) -> None:
    expected = get_settings().browser_worker_token.get_secret_value()
    given = (authorization or "").removeprefix("Bearer ").strip()
    if not expected or not hmac.compare_digest(given.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="invalid service token")


Auth = Depends(require_token)


@app.get("/internal/v1/health")
def health() -> dict[str, str]:
    return {"status": "ok" if _manager().healthy else "degraded"}


@app.post("/internal/v1/sessions", dependencies=[Auth], response_model=CreateSessionResponse)
async def create_session(req: CreateSessionRequest) -> CreateSessionResponse:
    try:
        sid = await _manager().create_session(req.task_id, req.allowed_domains, (req.viewport_width, req.viewport_height))
    except BrowserActionFailure as f:
        raise HTTPException(status_code=429, detail=str(f)) from f
    return CreateSessionResponse(session_id=sid)


@app.get("/internal/v1/sessions/{session_id}", dependencies=[Auth])
def get_session(session_id: str) -> dict[str, str]:
    try:
        s = _manager().get(session_id)
    except BrowserActionFailure as f:
        raise HTTPException(status_code=404, detail=f.code) from f
    return {"sessionId": s.id, "taskId": s.task_id}


@app.get("/internal/v1/sessions/{session_id}/observe", dependencies=[Auth])
async def observe(session_id: str, screenshot: bool = True):
    try:
        return (await _manager().observe(session_id, screenshot=screenshot)).wire()
    except BrowserActionFailure as f:
        raise HTTPException(status_code=404, detail=f.code) from f


@app.post("/internal/v1/sessions/{session_id}/actions", dependencies=[Auth])
async def act(session_id: str, req: ActionRequest):
    result = await _manager().act(session_id, req)
    if result.error and result.error.code == "SESSION_NOT_FOUND":
        return JSONResponse(result.wire(), status_code=404)
    return result.wire()


@app.post("/internal/v1/sessions/{session_id}/matches", dependencies=[Auth])
async def matches(session_id: str, exp: Expectation):
    try:
        return {"matches": await _manager().matches(session_id, exp)}
    except BrowserActionFailure as f:
        raise HTTPException(status_code=404, detail=f.code) from f


@app.post("/internal/v1/sessions/{session_id}/text", dependencies=[Auth])
async def text(session_id: str, target: Target):
    try:
        return {"text": await _manager().read_text(session_id, target)}
    except BrowserActionFailure as f:
        raise HTTPException(status_code=404, detail=f.code) from f


@app.post("/internal/v1/sessions/{session_id}/files", dependencies=[Auth])
async def stage_file(
    session_id: str,
    file: Annotated[UploadFile, File()],
    documentId: Annotated[str, Form()],  # noqa: N803 - wire name
    approvalToken: Annotated[str, Form()],  # noqa: N803
    digest: Annotated[str, Form()],
):
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES or not content:
        raise HTTPException(status_code=413, detail="file empty or too large")
    try:
        _manager().stage_file(session_id, documentId, file.filename or "document", content, approvalToken, digest)
    except BrowserActionFailure as f:
        status = 404 if f.code == "SESSION_NOT_FOUND" else 403 if f.code == "APPROVAL_INVALID" else 422
        raise HTTPException(status_code=status, detail=f.code) from f
    return {"documentId": documentId, "staged": True}


@app.delete("/internal/v1/sessions/{session_id}", dependencies=[Auth], status_code=204)
async def close(session_id: str) -> None:
    await _manager().close_session(session_id)
