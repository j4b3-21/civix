"""Runs the CIVIX Test Portal (authorized local test environment) in a background thread for browser tests."""

from __future__ import annotations

import socket
import sys
import threading
import time
from pathlib import Path

import httpx
import uvicorn

from .conftest import REPO

sys.path.insert(0, str(REPO / "infrastructure" / "test-portal"))


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class PortalServer:
    def __init__(self) -> None:
        from test_portal.app import app  # noqa: PLC0415

        self.port = _free_port()
        self.base_url = f"http://localhost:{self.port}"
        self._server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=self.port, log_level="warning"))
        self._thread = threading.Thread(target=self._server.run, daemon=True)

    def start(self) -> "PortalServer":
        self._thread.start()
        for _ in range(100):
            try:
                if httpx.get(f"{self.base_url}/health", timeout=0.5).status_code == 200:
                    return self
            except httpx.HTTPError:
                time.sleep(0.05)
        raise RuntimeError("test portal did not start")

    def stop(self) -> None:
        self._server.should_exit = True
        self._thread.join(timeout=5)

    # convenience controls
    def reset(self) -> None:
        httpx.post(f"{self.base_url}/test-control/reset")

    def faults(self, **kw) -> None:
        httpx.post(f"{self.base_url}/test-control/faults", json=kw).raise_for_status()

    def expire_sessions(self) -> None:
        httpx.post(f"{self.base_url}/test-control/expire-sessions")

    def latest_otp(self, mobile: str) -> str | None:
        return httpx.get(f"{self.base_url}/test-inbox", params={"mobile": mobile}).json()["latestOtp"]

    def applications(self) -> dict:
        return httpx.get(f"{self.base_url}/test-control/applications").json()["applications"]


_ = Path
