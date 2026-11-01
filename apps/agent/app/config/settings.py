"""Typed configuration. Every value comes from the environment (see /.env.example)."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

_SOURCE_ROOT = Path(__file__).resolve()
_REPO_ROOT = next(
    (parent for parent in _SOURCE_ROOT.parents if (parent / "service-registry").is_dir()),
    Path("/app"),
)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None, extra="ignore", case_sensitive=False)

    civix_env: Literal["local", "test", "staging", "production"] = "local"
    civix_enable_test_services: bool = False

    # database (schema "agent" only)
    agent_database_url: str = "postgresql://civix_agent:civix_agent@localhost:5432/civix"
    agent_db_schema: str = "agent"

    # service-to-service
    agent_internal_token: SecretStr = SecretStr("")
    api_internal_token: SecretStr = SecretStr("")
    browser_worker_token: SecretStr = SecretStr("")
    approval_signing_secret: SecretStr = SecretStr("")
    api_internal_url: str = "http://api:8080"
    browser_worker_url: str = "http://browser-worker:8001"

    # registry
    service_registry_path: Path = Field(default=_REPO_ROOT / "service-registry")
    test_portal_url: str = "http://test-portal:8090"

    # storage
    s3_endpoint: str | None = "http://storage:9000"
    s3_region: str = "ap-south-1"
    s3_bucket: str = "civix-private"
    s3_access_key: SecretStr = SecretStr("")
    s3_secret_key: SecretStr = SecretStr("")
    s3_force_path_style: bool = True
    s3_sse: str | None = "AES256"

    # orchestration limits
    node_timeout_seconds: float = 120
    browser_step_timeout_seconds: float = 60
    max_recovery_attempts_per_step: int = 3
    max_recovery_attempts_per_task: int = 8
    approval_ttl_seconds: int = 900
    max_concurrent_tasks: int = 4
    official_source_fetch_enabled: bool = False
    brave_search_api_key: SecretStr = SecretStr("")
    data_gov_api_key: SecretStr = SecretStr("")
    data_gov_base_url: str = "https://api.data.gov.in"

    # LLM
    llm_provider: Literal["none", "anthropic", "openai_compatible"] = "openai_compatible"
    anthropic_api_key: SecretStr = SecretStr("")
    anthropic_model: str = "claude-sonnet-5-5"
    openai_compatible_base_url: str = "http://ollama:11434/v1"
    openai_compatible_api_key: SecretStr = SecretStr("")
    openai_compatible_model: str = "llama3.1:8b"
    llm_cost_per_1k_input_tokens_usd: float = 0.003
    llm_cost_per_1k_output_tokens_usd: float = 0.015
    llm_max_cost_per_task_usd: float = 1.0
    llm_max_daily_cost_per_user_usd: float = 5.0

    # browser worker
    browser_headless: bool = True
    browser_max_sessions: int = 4
    browser_session_idle_timeout_seconds: int = 900
    browser_private_host_allowlist: str = ""

    @field_validator("browser_private_host_allowlist")
    @classmethod
    def _strip(cls, v: str) -> str:
        return v.strip()

    @property
    def private_host_allowlist(self) -> frozenset[str]:
        return frozenset(h.strip().lower() for h in self.browser_private_host_allowlist.split(",") if h.strip())

    def require_secrets(self, *names: str) -> None:
        """Fail fast at startup when a required secret is empty."""
        missing = [n for n in names if not getattr(self, n).get_secret_value()]
        if missing:
            raise RuntimeError(f"Missing required secrets: {', '.join(sorted(missing))}")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
