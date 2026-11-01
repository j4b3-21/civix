"""Internal FastAPI surface: auth, registry, eligibility, discovery, extraction, task lifecycle."""

from __future__ import annotations

import hashlib
import io
import uuid

import pytest
from fastapi.testclient import TestClient
from reportlab.pdfgen import canvas

from app.api.main import Container, create_app
from app.services.llm.base import NullProvider

from .harness import citizen, make_harness

AUTH = {"Authorization": "Bearer test-agent-token"}


@pytest.fixture
def setup(registry):
    h = make_harness(registry)
    h.deps.settings.agent_internal_token = type(h.deps.settings.agent_internal_token)("test-agent-token")
    c = Container(settings=h.deps.settings, registry=registry, runner=h.runner, storage=h.storage, llm=NullProvider())
    with TestClient(create_app(c)) as client:
        yield client, h


def test_requires_service_token(setup):
    client, _ = setup
    assert client.get("/internal/v1/registry/services").status_code == 401
    assert client.get("/internal/v1/registry/services", headers={"Authorization": "Bearer wrong"}).status_code == 401
    assert client.get("/internal/v1/health").status_code == 200


def test_registry_endpoints(setup):
    client, _ = setup
    data = client.get("/internal/v1/registry/services", headers=AUTH).json()
    codes = {s["code"] for s in data["services"]}
    assert "IN_CENTRAL_MOE_CSSS" in codes and "IN_TEST_PORTAL_SCHOLARSHIP" not in codes
    with_test = client.get("/internal/v1/registry/services?includeTest=true", headers=AUTH).json()
    assert "IN_TEST_PORTAL_SCHOLARSHIP" in {s["code"] for s in with_test["services"]}
    svc = next(s for s in data["services"] if s["code"] == "IN_CENTRAL_MOE_CSSS")
    assert svc["eligibility"]["rules"][0]["sourceUrl"].startswith("https://") and len(svc["contentHash"]) == 64
    fields = client.get("/internal/v1/registry/profile-fields", headers=AUTH).json()
    state = next(f for f in fields["fields"] if f["key"] == "state_code")
    assert len(state["options"]) == 36 and len(fields["jurisdictions"]) == 36


def test_eligibility_and_discovery(setup):
    client, _ = setup
    ctx = citizen()
    r = client.post("/internal/v1/eligibility/evaluate", headers=AUTH, json={"serviceCode": "IN_CENTRAL_MOE_CSSS", "citizenContext": ctx})
    assert r.status_code == 200 and r.json()["outcome"] == "NEEDS_MORE_INFORMATION"
    assert client.post("/internal/v1/eligibility/evaluate", headers=AUTH, json={"serviceCode": "NOPE", "citizenContext": ctx}).status_code == 404
    d = client.post("/internal/v1/discovery", headers=AUTH, json={"query": "scholarship for undergraduate students", "citizenContext": ctx}).json()
    assert d["results"] and d["interpretation"]["usedLlm"] is False and "official sources" in d["notice"]


def test_document_extraction(setup):
    client, h = setup
    buf = io.BytesIO()
    c = canvas.Canvas(buf)
    c.drawString(60, 800, "INCOME CERTIFICATE")
    c.drawString(60, 780, "The annual income of the family from all sources is Rs. 1,80,000 only.")
    c.save()
    pdf = buf.getvalue()
    uid, did = str(uuid.uuid4()), str(uuid.uuid4())
    key = f"users/{uid}/documents/{did}"
    import asyncio
    asyncio.run(h.storage.put(key, pdf, "application/pdf"))
    body = {"documentId": did, "storageKey": key, "mimeType": "application/pdf", "declaredCategory": "INCOME_CERTIFICATE",
            "sha256": hashlib.sha256(pdf).hexdigest()}
    r = client.post("/internal/v1/documents/extract", headers=AUTH, json=body)
    assert r.status_code == 200, r.text
    out = r.json()
    assert out["requiresReview"] is True and out["classification"]["category"] == "INCOME_CERTIFICATE"
    assert any(f["key"] == "annual_family_income" and f["value"] == 180000 for f in out["fields"])
    assert client.post("/internal/v1/documents/extract", headers=AUTH, json={**body, "sha256": "0" * 64}).status_code == 409
    assert client.post("/internal/v1/documents/extract", headers=AUTH, json={**body, "storageKey": "../../etc/passwd"}).status_code == 422


def test_task_lifecycle_endpoints(setup):
    client, h = setup
    tid = str(uuid.uuid4())
    body = {"taskId": tid, "applicationId": str(uuid.uuid4()), "userId": str(uuid.uuid4()), "serviceCode": "IN_TEST_PORTAL_SCHOLARSHIP",
            "intent": {"type": "APPLY"}, "citizenContext": citizen(), "documents": [], "dataVersion": 1}
    assert client.post("/internal/v1/tasks", headers=AUTH, json=body).status_code == 202
    import time
    for _ in range(50):
        st = client.get(f"/internal/v1/tasks/{tid}", headers=AUTH).json()
        if st.get("status") == "FAILED":
            break
        time.sleep(0.05)
    assert st["final"]["errorCode"] == "DOCUMENT_REQUIRED"
    assert client.post("/internal/v1/tasks", headers=AUTH, json=body).status_code == 409
    assert client.post(f"/internal/v1/tasks/{tid}/resume", headers=AUTH, json={"kind": "RETRY"}).status_code == 409
    r = client.post(f"/internal/v1/tasks/{tid}/resume", headers=AUTH,
                    json={"kind": "CITIZEN_INPUT", "citizenInput": {"requestId": "x", "value": "123456"}})
    assert r.status_code == 409
    assert client.get(f"/internal/v1/tasks/{uuid.uuid4()}", headers=AUTH).status_code == 404
