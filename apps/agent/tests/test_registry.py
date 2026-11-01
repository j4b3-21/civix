from __future__ import annotations

import shutil
from pathlib import Path

import pytest
import yaml

from app.contracts.common import AutomationStatus, ServiceVerificationStatus
from app.retrieval.registry import RegistryError, load_registry

from .conftest import REGISTRY_PATH


def test_all_definitions_validate(registry):
    assert len(registry.services) >= 7
    assert len(registry.jurisdictions) == 36
    assert "annual_family_income" in registry.profile_fields


def test_no_real_service_is_automated(registry):
    for svc in registry.services.values():
        if not svc.is_test_service:
            assert svc.automation_status in (AutomationStatus.NOT_AUTOMATED, AutomationStatus.GUIDED), svc.code
            assert svc.workflow is None, svc.code


def test_verified_services_cite_sources_for_every_rule(registry):
    for svc in registry.services.values():
        if svc.verification_status is ServiceVerificationStatus.SOURCE_VERIFIED:
            assert svc.last_verified_at is not None
            for rule in svc.eligibility.rules:
                assert svc.source(rule.source_id).url.startswith("https://")


def test_test_service_is_flagged(registry):
    svc = registry.get("IN_TEST_PORTAL_SCHOLARSHIP")
    assert svc is not None and svc.is_test_service and svc.automation_status is AutomationStatus.TEST_ONLY
    assert svc.workflow is not None and svc.workflow.entry_url == "http://localhost:8090/apply"
    assert registry.visible_services(include_test=False) == [s for s in registry.services.values() if not s.is_test_service]


def test_api_view_resolves_rule_sources(registry):
    view = registry.get("IN_CENTRAL_MOE_CSSS").api_view()
    rule = view["eligibility"]["rules"][0]
    assert rule["sourceUrl"].startswith("https://") and rule["retrievedAt"] == "2026-11-01"


def test_state_options_expand_to_jurisdictions(registry):
    assert len(registry.field_options("state_code")) == 36


def _copy_registry(tmp_path: Path) -> Path:
    dst = tmp_path / "reg"
    shutil.copytree(REGISTRY_PATH, dst)
    return dst


def test_rejects_unknown_profile_field(tmp_path):
    root = _copy_registry(tmp_path)
    f = root / "definitions/central/in_central_moe_csss.yaml"
    data = yaml.safe_load(f.read_text())
    data["eligibility"]["rules"][0]["field"] = "made_up_field"
    f.write_text(yaml.safe_dump(data, allow_unicode=True))
    with pytest.raises(RegistryError, match="unknown profile field"):
        load_registry(root, env={"TEST_PORTAL_URL": "http://localhost:8090"})


def test_rejects_active_automation_without_workflow_verification(tmp_path):
    root = _copy_registry(tmp_path)
    f = root / "definitions/central/in_central_moe_csss.yaml"
    data = yaml.safe_load(f.read_text())
    data["automationStatus"] = "ACTIVE"
    f.write_text(yaml.safe_dump(data, allow_unicode=True))
    with pytest.raises(RegistryError, match="ACTIVE automation requires WORKFLOW_VERIFIED|automated services need a workflow"):
        load_registry(root, env={"TEST_PORTAL_URL": "http://localhost:8090"})


def test_rejects_enum_value_outside_options(tmp_path):
    root = _copy_registry(tmp_path)
    f = root / "definitions/central/in_central_aicte_pragati.yaml"
    data = yaml.safe_load(f.read_text())
    data["eligibility"]["rules"][0]["value"] = "GIRL"
    f.write_text(yaml.safe_dump(data, allow_unicode=True))
    with pytest.raises(RegistryError, match="not in options"):
        load_registry(root, env={"TEST_PORTAL_URL": "http://localhost:8090"})


def test_rejects_workflow_without_review_before_submit(tmp_path):
    root = _copy_registry(tmp_path)
    f = root / "definitions/test/in_test_portal_scholarship.yaml"
    data = yaml.safe_load(f.read_text())
    data["workflow"]["steps"] = [s for s in data["workflow"]["steps"] if s["action"] != "REVIEW"]
    f.write_text(yaml.safe_dump(data, allow_unicode=True))
    with pytest.raises(RegistryError, match="REVIEW step must precede SUBMIT"):
        load_registry(root, env={"TEST_PORTAL_URL": "http://localhost:8090"})


def test_rejects_disallowed_placeholder(tmp_path):
    root = _copy_registry(tmp_path)
    f = root / "definitions/central/in_central_msme_udyam.yaml"
    f.write_text(f.read_text().replace("https://udyamregistration.gov.in/", "${SECRET_KEY}", 1))
    with pytest.raises(RegistryError, match="not allowed"):
        load_registry(root, env={"TEST_PORTAL_URL": "http://localhost:8090"})
