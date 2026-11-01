# CIVIX security & privacy

## Threat model highlights
| Threat | Control | Where |
|---|---|---|
| Account takeover | PBKDF2-HMAC-SHA256 (310k iterations; BCrypt accepted for legacy hashes), lockout after 5 failures/15 min, 15-min JWT access cookie, rotating opaque refresh token with family reuse detection, rate limits | `apps/api/auth` |
| CSRF | SameSite=Lax cookies + `XSRF-TOKEN` double-submit header on all mutations | `SecurityConfig` |
| IDOR | Every resource loaded by `(id, currentUser)`; not-owned → 404 | all services |
| Frontend-only approval | Approval tokens signed server-side; verified by agent **and** browser worker; digest-bound; expiring; single-use for submit | `ApprovalTokenSigner`, `policies/approval_tokens.py`, `browser/manager.py` |
| Stale approval | Profile/document change bumps `data_version` → approvals INVALIDATED; agent re-checks `dataVersion` before requesting final approval | api `ProfileService`, graph `final_submission_approval` |
| Prompt injection from portals/documents | Page text, OCR and search results are data. Policies are code. LLM outputs are schema-validated and constrained to observed labels | `recovery_agent.llm_remap`, `discovery_agent` |
| SSRF via browser | Domain allowlist at navigation + request level, DNS → private/loopback/link-local/metadata IPs blocked (explicit allowlist only for the local test portal), credentials-in-URL blocked, non-http(s) schemes blocked | `policies/domains.py` |
| Malicious uploads | Magic-byte type check, 10 MB cap, PDF encryption refused, processing in agent sandbox, no execution | api documents, `document_intelligence` |
| Secret leakage | Structured logging with key/token redaction; OTPs never logged/stored/checkpointed; events carry labels not values | `config/logging.py`, `orchestrator/events.py` |
| Lateral movement | Separate DB roles (`civix_api` owns `civix`, `civix_agent` owns `agent`, no cross access — verified); internal network without internet; distinct service tokens; browser-worker read-only FS, no capabilities, no-new-privileges | compose, init SQL |
| Duplicate submission | Pre-click DB marker, never retry SUBMIT, crash → verification → `SUBMISSION_UNCERTAIN` | graph `submission` |

## Encryption
- In transit: TLS at the edge (nginx profile or platform LB). Internal traffic is on an isolated Docker network; for multi-host
  deployments enable TLS between services (documented in DEPLOYMENT.md).
- At rest: object storage with SSE-S3 (MinIO KMS key `MINIO_KMS_SECRET_KEY`, or AWS S3 SSE); PostgreSQL volume encryption
  is the platform's responsibility (e.g. RDS encryption).

## Data minimisation & retention
- Profile fields each declare a purpose and sensitivity (`service-registry/profile-fields.yaml`). Aadhaar numbers are never
  extracted or stored. Bank account numbers are not collected.
- Field history stores provenance transitions, not values.
- Screenshots are stored under `tasks/{taskId}/` and are owner-only.
- Deletion: `POST /api/v1/privacy/deletion` (documents / profile / account). Audit events are retained pseudonymously.
- Recommended retention (configure lifecycle rules): task screenshots 90 days, agent checkpoints 180 days after completion.

## DPDP Act 2023
Consent is purpose-specific, recorded append-only with notice version, revocable, and enforced in code (profile, documents,
eligibility, agent execution, portal upload). This is **alignment by design, not a compliance claim** — a legal review of
notices, retention periods, grievance redressal and cross-border processing (e.g. a cloud LLM) is required before launch.

## Known gaps
- DNS-rebinding TOCTOU between the allowlist check and Chromium's own resolution: mitigate in production with an egress proxy
  that enforces the same allowlist at the network layer.
- No malware scanning of uploads (add ClamAV or a cloud scanner before production).
- No MFA for citizen accounts yet.
