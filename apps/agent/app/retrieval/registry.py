"""Loads, validates and indexes the service registry (service-registry/)."""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import jsonschema
import yaml

from app.contracts.common import ProfileFieldType, RuleOperator
from app.contracts.registry import Jurisdiction36, ProfileFieldDefinition, ServiceDefinition

_PLACEHOLDER = re.compile(r"\$\{([A-Z0-9_]+)\}")
_ALLOWED_PLACEHOLDERS = {"TEST_PORTAL_URL"}


class RegistryError(Exception):
    """Raised when the registry fails validation. The message lists every problem found."""


@dataclass(frozen=True)
class Registry:
    profile_fields_version: str
    profile_fields: dict[str, ProfileFieldDefinition]
    jurisdictions: list[Jurisdiction36]
    services: dict[str, ServiceDefinition]
    content_hashes: dict[str, str] = field(default_factory=dict)
    loaded_at: dt.datetime = field(default_factory=lambda: dt.datetime.now(dt.UTC))

    def get(self, code: str) -> ServiceDefinition | None:
        return self.services.get(code)

    def visible_services(self, include_test: bool) -> list[ServiceDefinition]:
        return [s for s in self.services.values() if include_test or not s.is_test_service]

    def field_options(self, key: str) -> list[str] | None:
        f = self.profile_fields.get(key)
        if f is None:
            return None
        if f.options_from == "jurisdictions":
            return [j.code for j in self.jurisdictions]
        return f.options

    def profile_fields_view(self) -> list[dict[str, Any]]:
        out = []
        for f in self.profile_fields.values():
            d = f.wire()
            d.pop("optionsFrom", None)
            d["options"] = self.field_options(f.key)
            out.append(d)
        return out


def _expand(value: Any, env: dict[str, str]) -> Any:
    if isinstance(value, str):
        def repl(m: re.Match[str]) -> str:
            name = m.group(1)
            if name not in _ALLOWED_PLACEHOLDERS:
                raise RegistryError(f"placeholder ${{{name}}} is not allowed")
            if name not in env:
                raise RegistryError(f"placeholder ${{{name}}} has no value")
            return env[name].rstrip("/")

        return _PLACEHOLDER.sub(repl, value)
    if isinstance(value, list):
        return [_expand(v, env) for v in value]
    if isinstance(value, dict):
        return {k: _expand(v, env) for k, v in value.items()}
    return value


def _jsonable(value: Any) -> Any:
    """YAML parses dates into datetime.date; JSON Schema validation needs strings."""
    if isinstance(value, (dt.date, dt.datetime)):
        return value.isoformat()
    if isinstance(value, list):
        return [_jsonable(v) for v in value]
    if isinstance(value, dict):
        return {k: _jsonable(v) for k, v in value.items()}
    return value


def _check_rule_against_catalog(svc: ServiceDefinition, reg_fields: dict[str, ProfileFieldDefinition], options: Any) -> list[str]:
    errors: list[str] = []
    for rule in svc.eligibility.rules:
        fd = reg_fields.get(rule.field)
        if fd is None:
            errors.append(f"{svc.code}: rule {rule.id} uses unknown profile field {rule.field!r}")
            continue
        values = rule.value if isinstance(rule.value, list) else [rule.value]
        if rule.operator is RuleOperator.EXISTS:
            continue
        for v in values:
            if fd.type is ProfileFieldType.ENUM:
                opts = options(rule.field) or []
                if v not in opts:
                    errors.append(f"{svc.code}: rule {rule.id} value {v!r} not in options of {rule.field}")
            elif fd.type in (ProfileFieldType.NUMBER, ProfileFieldType.INTEGER):
                if isinstance(v, bool) or not isinstance(v, (int, float)):
                    errors.append(f"{svc.code}: rule {rule.id} needs a numeric value for {rule.field}")
            elif fd.type is ProfileFieldType.BOOLEAN:
                if not isinstance(v, bool):
                    errors.append(f"{svc.code}: rule {rule.id} needs a boolean value for {rule.field}")
            elif fd.type is ProfileFieldType.DATE:
                try:
                    dt.date.fromisoformat(str(v))
                except ValueError:
                    errors.append(f"{svc.code}: rule {rule.id} needs an ISO date for {rule.field}")
        if rule.operator in (RuleOperator.DATE_BEFORE, RuleOperator.DATE_AFTER) and fd.type is not ProfileFieldType.DATE:
            errors.append(f"{svc.code}: rule {rule.id} uses a date operator on non-date field {rule.field}")
        if rule.operator in (
            RuleOperator.GREATER_THAN, RuleOperator.GREATER_THAN_OR_EQUAL, RuleOperator.LESS_THAN, RuleOperator.LESS_THAN_OR_EQUAL
        ) and fd.type not in (ProfileFieldType.NUMBER, ProfileFieldType.INTEGER):
            errors.append(f"{svc.code}: rule {rule.id} uses a numeric operator on non-numeric field {rule.field}")
    if svc.workflow:
        for step in svc.workflow.steps:
            for f in step.fields:
                if f.profile_field and f.profile_field not in reg_fields:
                    errors.append(f"{svc.code}: step {step.id} maps unknown profile field {f.profile_field!r}")
    return errors


def load_registry(root: Path, *, env: dict[str, str] | None = None) -> Registry:
    env = dict(os.environ if env is None else env)
    env.setdefault("TEST_PORTAL_URL", "http://test-portal:8090")
    errors: list[str] = []

    pf_raw = yaml.safe_load((root / "profile-fields.yaml").read_text(encoding="utf-8"))
    profile_fields: dict[str, ProfileFieldDefinition] = {}
    for raw in pf_raw["fields"]:
        try:
            fd = ProfileFieldDefinition.model_validate(raw)
        except Exception as exc:  # noqa: BLE001 - collect all errors
            errors.append(f"profile-fields.yaml {raw.get('key')}: {exc}")
            continue
        if fd.key in profile_fields:
            errors.append(f"duplicate profile field {fd.key}")
        profile_fields[fd.key] = fd

    jur_raw = yaml.safe_load((root / "jurisdictions.yaml").read_text(encoding="utf-8"))
    jurisdictions = [Jurisdiction36.model_validate(j) for j in jur_raw["jurisdictions"]]
    if len(jurisdictions) != 36:
        errors.append(f"expected 36 jurisdictions, found {len(jurisdictions)}")
    state_codes = {j.code for j in jurisdictions}

    schema = json.loads((root / "schemas" / "service-definition.schema.json").read_text(encoding="utf-8"))
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())

    services: dict[str, ServiceDefinition] = {}
    hashes: dict[str, str] = {}
    files = sorted((root / "definitions").rglob("*.yaml"))
    if not files:
        errors.append("no service definitions found")

    def options(key: str) -> list[str] | None:
        f = profile_fields.get(key)
        if f is None:
            return None
        return [j.code for j in jurisdictions] if f.options_from == "jurisdictions" else f.options

    for path in files:
        rel = path.relative_to(root)
        try:
            raw = yaml.safe_load(path.read_text(encoding="utf-8"))
            raw = _jsonable(_expand(raw, env))
        except (yaml.YAMLError, RegistryError) as exc:
            errors.append(f"{rel}: {exc}")
            continue
        schema_errors = sorted(validator.iter_errors(raw), key=lambda e: list(e.path))
        for e in schema_errors:
            errors.append(f"{rel}: schema: {'/'.join(map(str, e.path)) or '<root>'}: {e.message}")
        if schema_errors:
            continue
        try:
            svc = ServiceDefinition.model_validate(raw)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{rel}: {exc}")
            continue
        if svc.code in services:
            errors.append(f"{rel}: duplicate service code {svc.code}")
            continue
        for s in svc.jurisdiction.states:
            if s not in state_codes:
                errors.append(f"{rel}: unknown state code {s}")
        errors.extend(_check_rule_against_catalog(svc, profile_fields, options))
        services[svc.code] = svc
        hashes[svc.code] = hashlib.sha256(json.dumps(raw, sort_keys=True, separators=(",", ":")).encode()).hexdigest()

    if errors:
        raise RegistryError("Service registry validation failed:\n  - " + "\n  - ".join(errors))

    return Registry(
        profile_fields_version=str(pf_raw["version"]),
        profile_fields=profile_fields,
        jurisdictions=jurisdictions,
        services=services,
        content_hashes=hashes,
    )
