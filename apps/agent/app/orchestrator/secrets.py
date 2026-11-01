"""In-memory holders for values that must never be checkpointed, logged or emitted.

- SecretInbox: citizen-entered OTP / CAPTCHA / login values, consumed exactly once.
- TokenVault: approval tokens (signed by Spring) keyed by approval id; resume payloads carry only the id.

If the process restarts, these are lost by design: the workflow asks the citizen again or requests a fresh approval.
"""

from __future__ import annotations

import time
from dataclasses import dataclass


@dataclass
class _Entry:
    value: str
    expires: float


class SecretInbox:
    def __init__(self, ttl_seconds: float = 600) -> None:
        self._ttl = ttl_seconds
        self._items: dict[str, _Entry] = {}

    def put(self, request_id: str, value: str) -> None:
        self._gc()
        self._items[request_id] = _Entry(value, time.monotonic() + self._ttl)

    def take(self, request_id: str) -> str | None:
        self._gc()
        e = self._items.pop(request_id, None)
        return e.value if e else None

    def _gc(self) -> None:
        now = time.monotonic()
        for k in [k for k, e in self._items.items() if e.expires < now]:
            del self._items[k]


class TokenVault:
    def __init__(self) -> None:
        self._tokens: dict[str, str] = {}

    def put(self, approval_id: str, token: str) -> None:
        self._tokens[approval_id] = token

    def get(self, approval_id: str) -> str | None:
        return self._tokens.get(approval_id)

    def discard(self, approval_id: str) -> None:
        self._tokens.pop(approval_id, None)
