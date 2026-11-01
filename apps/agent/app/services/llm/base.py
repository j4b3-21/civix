"""Provider abstraction with structured output, streaming, and in-process cost limits."""

from __future__ import annotations

import contextlib
import contextvars
import datetime as dt
import json
from dataclasses import dataclass
from typing import Any, AsyncIterator, Protocol

import httpx
import jsonschema
from prometheus_client import Counter

LLM_TOKENS = Counter("civix_llm_tokens_total", "LLM tokens", ["provider", "direction", "purpose"])
LLM_COST = Counter("civix_llm_estimated_cost_usd_total", "Estimated LLM cost (USD)", ["provider", "purpose"])
LLM_CALLS = Counter("civix_llm_calls_total", "LLM calls", ["provider", "purpose", "outcome"])


class LLMUnavailable(Exception):
    pass


class LLMBudgetExceeded(LLMUnavailable):
    pass


@dataclass(frozen=True)
class Usage:
    input_tokens: int
    output_tokens: int
    estimated_cost_usd: float


@dataclass(frozen=True)
class _Context:
    task_id: str | None = None
    user_id: str | None = None


_context: contextvars.ContextVar[_Context] = contextvars.ContextVar("civix_llm_context", default=_Context())


@contextlib.contextmanager
def llm_context(*, task_id: str | None = None, user_id: str | None = None):
    token = _context.set(_Context(task_id, user_id))
    try:
        yield
    finally:
        _context.reset(token)


class LLMProvider(Protocol):
    name: str

    @property
    def available(self) -> bool: ...

    async def complete_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> tuple[dict[str, Any], Usage]: ...

    def stream_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> AsyncIterator[dict[str, Any]]: ...


class NullProvider:
    name = "none"

    @property
    def available(self) -> bool:
        return False

    async def complete_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> tuple[dict[str, Any], Usage]:
        raise LLMUnavailable("no LLM provider configured")

    async def stream_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> AsyncIterator[dict[str, Any]]:
        raise LLMUnavailable("no LLM provider configured")


class _Priced:
    def __init__(self, cost_in: float, cost_out: float, max_task: float, max_daily_user: float) -> None:
        self._cin, self._cout = cost_in, cost_out
        self._max_task, self._max_daily_user = max_task, max_daily_user
        self._task_costs: dict[str, float] = {}
        self._daily_costs: dict[tuple[str, str], float] = {}

    def _authorize(self, user: dict[str, Any]) -> None:
        ctx = _context.get()
        task_id, user_id = ctx.task_id or user.get("taskId"), ctx.user_id or user.get("userId")
        if not task_id and not user_id:
            return
        estimate = len(json.dumps(user, ensure_ascii=False)) / 4 / 1000 * self._cin + 1024 / 1000 * self._cout
        day = dt.datetime.now(dt.UTC).date().isoformat()
        if task_id and self._task_costs.get(task_id, 0) + estimate > self._max_task:
            raise LLMBudgetExceeded("LLM task cost budget exceeded")
        if user_id and self._daily_costs.get((user_id, day), 0) + estimate > self._max_daily_user:
            raise LLMBudgetExceeded("LLM daily user cost budget exceeded")

    def _record(self, name: str, purpose: str, tin: int, tout: int) -> Usage:
        cost = tin / 1000 * self._cin + tout / 1000 * self._cout
        ctx, day = _context.get(), dt.datetime.now(dt.UTC).date().isoformat()
        if ctx.task_id:
            self._task_costs[ctx.task_id] = self._task_costs.get(ctx.task_id, 0) + cost
        if ctx.user_id:
            key = (ctx.user_id, day)
            self._daily_costs[key] = self._daily_costs.get(key, 0) + cost
        LLM_TOKENS.labels(name, "input", purpose).inc(tin)
        LLM_TOKENS.labels(name, "output", purpose).inc(tout)
        LLM_COST.labels(name, purpose).inc(cost)
        return Usage(tin, tout, cost)


class AnthropicProvider(_Priced):
    name = "anthropic"

    def __init__(self, api_key: str, model: str, cost_in: float, cost_out: float, max_task: float = 1.0,
                 max_daily_user: float = 5.0, base_url: str = "https://api.anthropic.com") -> None:
        super().__init__(cost_in, cost_out, max_task, max_daily_user)
        self._key, self._model = api_key, model
        self._http = httpx.AsyncClient(base_url=base_url, timeout=60)

    @property
    def available(self) -> bool:
        return bool(self._key)

    def _body(self, system: str, user: dict[str, Any], schema: dict[str, Any], stream: bool = False) -> dict[str, Any]:
        return {"model": self._model, "max_tokens": 1024, "system": system,
                "messages": [{"role": "user", "content": json.dumps(user, ensure_ascii=False)}],
                "tools": [{"name": "answer", "description": "Return the structured answer.", "input_schema": schema}],
                "tool_choice": {"type": "tool", "name": "answer"}, **({"stream": True} if stream else {})}

    async def complete_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> tuple[dict[str, Any], Usage]:
        if not self._key:
            raise LLMUnavailable("ANTHROPIC_API_KEY not set")
        self._authorize(user)
        try:
            r = await self._http.post("/v1/messages", json=self._body(system, user, schema),
                                      headers={"x-api-key": self._key, "anthropic-version": "2023-06-01"})
            r.raise_for_status()
            data = r.json()
            block = next((c for c in data.get("content", []) if c.get("type") == "tool_use"), None)
            if block is None:
                raise LLMUnavailable("model returned no structured answer")
            out = block["input"]
            jsonschema.validate(out, schema)
        except httpx.HTTPError as exc:
            LLM_CALLS.labels(self.name, purpose, "error").inc()
            raise LLMUnavailable(f"anthropic call failed: {exc.__class__.__name__}") from exc
        usage = data.get("usage", {})
        LLM_CALLS.labels(self.name, purpose, "ok").inc()
        return out, self._record(self.name, purpose, usage.get("input_tokens", 0), usage.get("output_tokens", 0))

    async def stream_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> AsyncIterator[dict[str, Any]]:
        if not self._key:
            raise LLMUnavailable("ANTHROPIC_API_KEY not set")
        self._authorize(user)
        chunks, usage = [], {}
        try:
            async with self._http.stream("POST", "/v1/messages", json=self._body(system, user, schema, True),
                                         headers={"x-api-key": self._key, "anthropic-version": "2023-06-01"}) as r:
                r.raise_for_status()
                async for line in r.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    event = json.loads(line[6:])
                    if event.get("type") == "content_block_delta" and event.get("delta", {}).get("type") == "input_json_delta":
                        chunks.append(event["delta"].get("partial_json", ""))
                    elif event.get("type") == "message_start":
                        usage.update(event.get("message", {}).get("usage", {}))
                    elif event.get("type") == "message_delta":
                        usage.update(event.get("usage", {}))
            out = json.loads("".join(chunks))
            jsonschema.validate(out, schema)
        except (httpx.HTTPError, json.JSONDecodeError, jsonschema.ValidationError) as exc:
            LLM_CALLS.labels(self.name, purpose, "error").inc()
            raise LLMUnavailable(f"anthropic stream failed: {exc.__class__.__name__}") from exc
        LLM_CALLS.labels(self.name, purpose, "ok").inc()
        self._record(self.name, purpose, usage.get("input_tokens", 0), usage.get("output_tokens", 0))
        yield out


class OpenAICompatibleProvider(_Priced):
    name = "openai_compatible"

    def __init__(self, base_url: str, api_key: str, model: str, cost_in: float = 0.0, cost_out: float = 0.0,
                 max_task: float = 1.0, max_daily_user: float = 5.0) -> None:
        super().__init__(cost_in, cost_out, max_task, max_daily_user)
        self._model = model
        self._http = httpx.AsyncClient(base_url=base_url.rstrip("/"), timeout=120,
                                       headers={"Authorization": f"******"} if api_key else {})
        self._configured = bool(base_url and model)

    @property
    def available(self) -> bool:
        return self._configured

    async def complete_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> tuple[dict[str, Any], Usage]:
        self._authorize(user)
        body = {"model": self._model, "messages": [{"role": "system", "content": system + " Respond with JSON only."},
                {"role": "user", "content": json.dumps(user, ensure_ascii=False)}],
                "response_format": {"type": "json_schema", "json_schema": {"name": "answer", "schema": schema}}, "temperature": 0}
        try:
            r = await self._http.post("/chat/completions", json=body)
            r.raise_for_status()
            data = r.json()
            out = json.loads(data["choices"][0]["message"]["content"])
            jsonschema.validate(out, schema)
        except (httpx.HTTPError, KeyError, ValueError, jsonschema.ValidationError) as exc:
            LLM_CALLS.labels(self.name, purpose, "error").inc()
            raise LLMUnavailable(f"local model call failed: {exc.__class__.__name__}") from exc
        usage = data.get("usage", {})
        LLM_CALLS.labels(self.name, purpose, "ok").inc()
        return out, self._record(self.name, purpose, usage.get("prompt_tokens", 0), usage.get("completion_tokens", 0))

    async def stream_json(self, *, system: str, user: dict[str, Any], schema: dict[str, Any], purpose: str) -> AsyncIterator[dict[str, Any]]:
        result, _ = await self.complete_json(system=system, user=user, schema=schema, purpose=purpose)
        yield result


def build_provider(settings) -> LLMProvider:  # noqa: ANN001
    limits = (settings.llm_max_cost_per_task_usd, settings.llm_max_daily_cost_per_user_usd)
    if settings.llm_provider == "anthropic":
        return AnthropicProvider(settings.anthropic_api_key.get_secret_value(), settings.anthropic_model,
                                 settings.llm_cost_per_1k_input_tokens_usd, settings.llm_cost_per_1k_output_tokens_usd, *limits)
    if settings.llm_provider == "openai_compatible":
        return OpenAICompatibleProvider(settings.openai_compatible_base_url, settings.openai_compatible_api_key.get_secret_value(),
                                        settings.openai_compatible_model, settings.llm_cost_per_1k_input_tokens_usd,
                                        settings.llm_cost_per_1k_output_tokens_usd, *limits)
    return NullProvider()
