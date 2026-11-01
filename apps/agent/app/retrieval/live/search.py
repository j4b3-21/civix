from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import urlparse

import httpx

_OFFICIAL_SUFFIXES = (".gov.in", ".nic.in")
_OFFICIAL_HOSTS = {"myscheme.gov.in", "scholarships.gov.in", "india.gov.in", "services.india.gov.in", "umang.gov.in",
                   "digilocker.gov.in", "data.gov.in", "api.data.gov.in"}


@dataclass(frozen=True)
class SearchResult:
    title: str
    url: str
    description: str


def is_official_url(url: str) -> bool:
    try:
        host = (urlparse(url).hostname or "").lower().rstrip(".")
    except ValueError:
        return False
    return host in _OFFICIAL_HOSTS or host.endswith(_OFFICIAL_SUFFIXES)


class BraveSearch:
    def __init__(self, api_key: str, *, timeout: float = 15) -> None:
        self._key = api_key
        self._http = httpx.AsyncClient(timeout=timeout)

    @property
    def available(self) -> bool:
        return bool(self._key)

    async def search(self, query: str, *, count: int = 10) -> list[SearchResult]:
        if not self._key:
            return []
        restricted = f"{query} (site:gov.in OR site:nic.in OR site:myscheme.gov.in OR site:scholarships.gov.in)"
        response = await self._http.get(
            "https://api.search.brave.com/res/v1/web/search",
            params={"q": restricted, "count": min(count, 20), "safesearch": "strict"},
            headers={"Accept": "application/json", "X-Subscription-Token": self._key},
        )
        response.raise_for_status()
        results = []
        for item in response.json().get("web", {}).get("results", []):
            url = item.get("url", "")
            if is_official_url(url):
                results.append(SearchResult(item.get("title", "")[:300], url, item.get("description", "")[:1000]))
        return results
