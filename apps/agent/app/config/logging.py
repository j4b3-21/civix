"""Structured JSON logging with secret redaction. Never log passwords, OTPs, tokens, cookies or document text."""

from __future__ import annotations

import logging
import re
import sys
from typing import Any

import structlog

_SENSITIVE_KEYS = re.compile(r"(pass(word)?|otp|token|secret|authorization|cookie|api[_-]?key|citizen_?input|value|text|content|screenshot)", re.I)
_ALLOWED_KEYS = {"event", "level", "timestamp", "logger", "service", "task_id", "session_id", "node", "step", "code", "duration_ms",
                 "attempt", "status", "event_type", "host", "domains", "blocked", "error_code", "outcome", "service_code"}
_TOKEN_LIKE = re.compile(r"(eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}|Bearer\s+\S+)")


def _redact(_: Any, __: str, event: dict[str, Any]) -> dict[str, Any]:
    for k in list(event):
        if k in _ALLOWED_KEYS:
            continue
        if _SENSITIVE_KEYS.search(k):
            event[k] = "[REDACTED]"
        elif isinstance(event[k], str):
            event[k] = _TOKEN_LIKE.sub("[REDACTED]", event[k])
    if isinstance(event.get("event"), str):
        event["event"] = _TOKEN_LIKE.sub("[REDACTED]", event["event"])
    return event


def configure_logging(service: str, level: str = "INFO") -> None:
    logging.basicConfig(format="%(message)s", stream=sys.stdout, level=level)
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            lambda _l, _m, e: {**e, "service": service},
            _redact,
            structlog.processors.format_exc_info,
            structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(logging.getLevelName(level)),
        cache_logger_on_first_use=True,
    )
