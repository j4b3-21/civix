import asyncio

import httpx
import pytest

from app.retrieval.live.search import is_official_url
from app.retrieval.live.data_gov import DataGovClient


def test_live_search_accepts_only_official_domains():
    assert is_official_url("https://scholarships.gov.in/scheme")
    assert is_official_url("https://api.data.gov.in/resource/example")
    assert is_official_url("https://dept.nic.in/page")
    assert not is_official_url("https://example.gov.in.attacker.test/page")
    assert not is_official_url("https://example.com/scheme")


def test_data_gov_fetch_uses_resource_api_and_filters():
    client = DataGovClient("secret", base_url="https://api.data.gov.in")
    request_seen = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        request_seen["url"] = request.url
        return httpx.Response(200, json={"records": [{"state": "IN"}]})

    asyncio.run(client._http.aclose())
    client._http = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="https://api.data.gov.in")
    assert asyncio.run(client.fetch("resource-id", filters={"state": "IN"}, limit=5)) == {"records": [{"state": "IN"}]}
    assert request_seen["url"].path == "/resource/resource-id"
    assert request_seen["url"].params["filters[state]"] == "IN"
    assert request_seen["url"].params["limit"] == "5"
    asyncio.run(client.close())


def test_data_gov_requires_key_and_rejects_bad_paging():
    client = DataGovClient("")
    with pytest.raises(RuntimeError):
        asyncio.run(client.fetch("resource-id"))
    asyncio.run(client.close())
