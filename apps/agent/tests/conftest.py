from __future__ import annotations

import os
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
REGISTRY_PATH = REPO / "service-registry"

# Deterministic, test-only configuration. Never used outside the test suite.
os.environ.setdefault("CIVIX_ENV", "test")
os.environ.setdefault("APPROVAL_SIGNING_SECRET", "test-approval-secret-0123456789abcdef")
os.environ.setdefault("AGENT_INTERNAL_TOKEN", "test-agent-token")
os.environ.setdefault("API_INTERNAL_TOKEN", "test-api-token")
os.environ.setdefault("BROWSER_WORKER_TOKEN", "test-worker-token")
os.environ.setdefault("SERVICE_REGISTRY_PATH", str(REGISTRY_PATH))
os.environ.setdefault("CIVIX_ENABLE_TEST_SERVICES", "true")


@pytest.fixture(scope="session")
def registry():
    from app.retrieval.registry import load_registry

    return load_registry(REGISTRY_PATH, env={"TEST_PORTAL_URL": "http://localhost:8090"})


def database_url() -> str | None:
    return os.environ.get("CIVIX_TEST_DATABASE_URL")


requires_postgres = pytest.mark.skipif(database_url() is None, reason="CIVIX_TEST_DATABASE_URL not set")
