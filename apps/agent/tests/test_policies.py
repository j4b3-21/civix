from __future__ import annotations

import pytest

from app.contracts.common import ApprovalKind
from app.policies.actions import is_consequential_control, is_payment_control
from app.policies.approval_tokens import ApprovalClaims, ApprovalTokenError, payload_digest, sign, verify
from app.policies.domains import DomainPolicy, DomainPolicyError

SECRET = "test-approval-secret-0123456789abcdef"
VECTOR = (
    "eyJhaWQiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ0aWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJhcHAiOiIzMzMzMzMzMy0zMzMzLTMzMzMtMzMzMy0zMzMzMzMzMzMzMzMiLCJ1aWQiOiI0NDQ0NDQ0NC00NDQ0LTQ0NDQtNDQ0NC00NDQ0NDQ0NDQ0NDQiLCJraW5kIjoiRklOQUxfU1VCTUlTU0lPTiIsImRpZyI6ImFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWEiLCJleHAiOjE4OTM0NTYwMDB9"
    ".akemDwop-UVWo6rtxQVZn0e9pDl8DaFOP1mv8utCLsE"
)
CLAIMS = ApprovalClaims(
    approval_id="11111111-1111-1111-1111-111111111111",
    task_id="22222222-2222-2222-2222-222222222222",
    application_id="33333333-3333-3333-3333-333333333333",
    user_id="44444444-4444-4444-4444-444444444444",
    kind=ApprovalKind.FINAL_SUBMISSION,
    digest="a" * 64,
    expires_at=1893456000,
)


def test_cross_language_vector():
    assert sign(SECRET, CLAIMS) == VECTOR
    c = verify(SECRET, VECTOR, expected_task_id=CLAIMS.task_id, expected_kind=ApprovalKind.FINAL_SUBMISSION, expected_digest="a" * 64, now=1_800_000_000)
    assert c == CLAIMS


@pytest.mark.parametrize(
    "kwargs,code",
    [
        ({"expected_task_id": "other"}, "APPROVAL_TASK_MISMATCH"),
        ({"expected_kind": ApprovalKind.PREPARATION}, "APPROVAL_KIND_MISMATCH"),
        ({"expected_digest": "b" * 64}, "APPROVAL_DIGEST_MISMATCH"),
        ({"expected_approval_id": "x"}, "APPROVAL_ID_MISMATCH"),
        ({"now": 1893456001}, "APPROVAL_EXPIRED"),
    ],
)
def test_rejections(kwargs, code):
    base = dict(expected_task_id=CLAIMS.task_id, expected_kind=ApprovalKind.FINAL_SUBMISSION, expected_digest="a" * 64, now=1_800_000_000)
    base.update(kwargs)
    with pytest.raises(ApprovalTokenError) as e:
        verify(SECRET, VECTOR, **base)
    assert e.value.code == code


def test_tampered_payload_and_wrong_secret():
    p, s = VECTOR.split(".")
    tampered = p[:-2] + ("A" if p[-2] != "A" else "B") + p[-1] + "." + s
    for token, secret in ((tampered, SECRET), (VECTOR, "other-secret")):
        with pytest.raises(ApprovalTokenError) as e:
            verify(secret, token, expected_task_id=CLAIMS.task_id, expected_kind=ApprovalKind.FINAL_SUBMISSION, expected_digest="a" * 64, now=0)
        assert e.value.code == "APPROVAL_SIGNATURE_INVALID"
    with pytest.raises(ApprovalTokenError):
        verify(SECRET, "garbage", expected_task_id="t", expected_kind=ApprovalKind.FINAL_SUBMISSION, expected_digest="a")


def test_digest_is_canonical():
    a = payload_digest(fields={"s1": {"b": 1, "a": "x"}}, documents=["d2", "d1"], service_code="S", data_version=2)
    b = payload_digest(fields={"s1": {"a": "x", "b": 1}}, documents=["d1", "d2"], service_code="S", data_version=2)
    assert a == b and len(a) == 64
    assert a != payload_digest(fields={"s1": {"a": "x", "b": 2}}, documents=["d1", "d2"], service_code="S", data_version=2)
    assert a != payload_digest(fields={"s1": {"a": "x", "b": 1}}, documents=["d1", "d2"], service_code="S", data_version=3)


def test_domain_policy():
    p = DomainPolicy(frozenset({"scholarships.gov.in", "test-portal"}), frozenset({"test-portal"}), resolve_dns=False)
    p.check_url("https://scholarships.gov.in/x")
    p.check_url("https://www.scholarships.gov.in/x")
    p.check_url("http://test-portal:8090/apply")
    p.check_url("data:image/png;base64,AAAA")
    for url, code in [
        ("https://scholarships.gov.in.evil.com/", "DOMAIN_NOT_ALLOWED"),
        ("https://evilscholarships.gov.in/", "DOMAIN_NOT_ALLOWED"),
        ("file:///etc/passwd", "SCHEME_BLOCKED"),
        ("javascript:alert(1)", "SCHEME_BLOCKED"),
        ("https://user:pw@scholarships.gov.in/", "CREDENTIALS_IN_URL"),
    ]:
        with pytest.raises(DomainPolicyError) as e:
            p.check_url(url)
        assert e.value.code == code


def test_private_ips_blocked_unless_allowlisted():
    p = DomainPolicy(frozenset({"127.0.0.1", "169.254.169.254", "10.0.0.5", "localhost"}), frozenset(), resolve_dns=True)
    for url in ("http://127.0.0.1/", "http://169.254.169.254/latest/meta-data", "http://10.0.0.5/", "http://localhost/"):
        with pytest.raises(DomainPolicyError) as e:
            p.check_url(url)
        assert e.value.code == "PRIVATE_NETWORK_BLOCKED"
    DomainPolicy(frozenset({"localhost"}), frozenset({"localhost"})).check_url("http://localhost:8090/")


@pytest.mark.parametrize("name", ["Submit application", "Final Submit", "Proceed to Pay", "Make payment", "Confirm and submit"])
def test_consequential_controls(name):
    assert is_consequential_control(name)


@pytest.mark.parametrize("name", ["Save and continue", "Send OTP", "Verify OTP", "Upload and continue", "Next"])
def test_non_consequential_controls(name):
    assert not is_consequential_control(name)


def test_payment_controls():
    assert is_payment_control("Proceed to Pay") and not is_payment_control("Submit application")
