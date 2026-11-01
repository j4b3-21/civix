"""Domain allowlist and SSRF protection for browser navigation and every network request the browser makes."""

from __future__ import annotations

import ipaddress
import socket
from dataclasses import dataclass
from urllib.parse import urlsplit

_ALLOWED_SCHEMES = {"http", "https"}
_ALWAYS_ALLOWED_SCHEMES = {"data", "blob", "about"}


class DomainPolicyError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


def _host_matches(host: str, pattern: str) -> bool:
    host, pattern = host.lower().rstrip("."), pattern.lower().rstrip(".")
    return host == pattern or host.endswith("." + pattern)


def _is_private(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> bool:
    return bool(
        ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved or ip.is_unspecified
        or (isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped and _is_private(ip.ipv4_mapped))
    )


@dataclass(frozen=True)
class DomainPolicy:
    """allowed_domains: exact hosts or parent domains (gov.in style suffixes are NOT implied).

    private_host_allowlist: hosts that may resolve to private/loopback IPs (only the local test portal).
    """

    allowed_domains: frozenset[str]
    private_host_allowlist: frozenset[str] = frozenset()
    resolve_dns: bool = True

    def check_url(self, url: str) -> None:
        parts = urlsplit(url)
        scheme = parts.scheme.lower()
        if scheme in _ALWAYS_ALLOWED_SCHEMES:
            return
        if scheme not in _ALLOWED_SCHEMES:
            raise DomainPolicyError("SCHEME_BLOCKED", f"scheme {scheme!r} is not allowed")
        host = (parts.hostname or "").lower()
        if not host:
            raise DomainPolicyError("HOST_MISSING", "URL has no host")
        if parts.username or parts.password:
            raise DomainPolicyError("CREDENTIALS_IN_URL", "URLs with embedded credentials are not allowed")
        if not any(_host_matches(host, d) for d in self.allowed_domains):
            raise DomainPolicyError("DOMAIN_NOT_ALLOWED", f"{host} is not an approved portal domain")
        self._check_ip(host)

    def _check_ip(self, host: str) -> None:
        if host in self.private_host_allowlist:
            return
        try:
            literal = ipaddress.ip_address(host)
        except ValueError:
            literal = None
        if literal is not None:
            if _is_private(literal):
                raise DomainPolicyError("PRIVATE_NETWORK_BLOCKED", f"{host} is a private address")
            return
        if not self.resolve_dns:
            return
        try:
            infos = socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
        except socket.gaierror as exc:
            raise DomainPolicyError("DNS_FAILURE", f"cannot resolve {host}") from exc
        for info in infos:
            ip = ipaddress.ip_address(info[4][0])
            if _is_private(ip):
                raise DomainPolicyError("PRIVATE_NETWORK_BLOCKED", f"{host} resolves to a private address")

    def is_allowed(self, url: str) -> bool:
        try:
            self.check_url(url)
            return True
        except DomainPolicyError:
            return False
