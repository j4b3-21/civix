from __future__ import annotations

from typing import Any
from urllib.parse import quote

import httpx


class DataGovClient:
    """Read-only client for a specific data.gov.in resource."""

    def __init__(self, api_key: str, *, base_url: str = "https://api.data.gov.in", timeout: float = 15) -> None:
        self._api_key = api_key
        self._http = httpx.AsyncClient(base_url=base_url.rstrip("/"), timeout=timeout)

    @property
    def available(self) -> bool:
        return bool(self._api_key)

    async def fetch(
        self,
        resource_id: str,
        *,
        filters: dict[str, str] | None = None,
        limit: int = 10,
        offset: int = 0,
    ) -> dict[str, Any]:
        if not self._api_key:
            raise RuntimeError("DATA_GOV_API_KEY is not configured")
        if not resource_id or "/" in resource_id or "\\" in resource_id:
            raise ValueError("resource_id must be a non-empty resource identifier")
        if limit < 1 or limit > 100:
            raise ValueError("limit must be between 1 and 100")
        if offset < 0:
            raise ValueError("offset must be non-negative")
        params: list[tuple[str, str | int]] = [
            ("api-key", self._api_key),
            ("format", "json"),
            ("limit", limit),
            ("offset", offset),
        ]
        params.extend((f"filters[{key}]", value) for key, value in (filters or {}).items())
        response = await self._http.get(f"/resource/{quote(resource_id, safe='-')}", params=params)
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, dict):
            raise ValueError("data.gov.in returned a non-object response")
        return data

    async def close(self) -> None:
        await self._http.aclose()
