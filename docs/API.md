# CIVIX public API — `apps/api` (Spring Boot), base path `/api/v1`

The browser reaches this API **same-origin** through Next.js rewrites (`/api/v1/*` → `http://api:8080/api/v1/*`), so auth cookies are first-party. OpenAPI is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` (disabled when `CIVIX_OPENAPI_ENABLED=false`).
Enums and shared objects: `docs/AGENT_CONTRACTS.md`.

## Conventions

- **Auth**: short-lived JWT access token (HS256, 15 min) in httpOnly cookie `civix_at` (Path `/`, SameSite=Lax, Secure in prod). Also accepted as `Authorization: Bearer`. Refresh token: opaque 256-bit random, stored SHA-256-hashed, rotated on every use with reuse detection (family revocation), httpOnly cookie `civix_rt` (Path `/api/v1/auth`, 14 days).
- **CSRF**: cookie `XSRF-TOKEN` (readable), client echoes it in header `X-XSRF-TOKEN` on POST/PUT/PATCH/DELETE. `/api/v1/auth/login`, `/auth/register`, `/auth/refresh` are exempt (SameSite=Lax protects them). `/internal/**` is exempt (bearer service token).
- **Errors**: RFC 9457 `application/problem+json`:
  `{ "type": "https://civix.dev/problems/<code>", "title": "...", "status": 409, "detail": "...", "code": "APPROVAL_EXPIRED", "correlationId": "...", "errors": [{"field": "email", "message": "must be a well-formed email address"}] }`
- **Pagination**: `?page=0&size=20` (max 100) → `{ "items": [...], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }`.
- **Correlation**: every response carries `X-Request-Id` (echoed from request or generated).
- **Rate limits** (Redis, per IP + per user): auth endpoints 10/min, discovery 20/min, uploads 30/hour. `429` with `Retry-After`.
- Ownership: every `{id}` resource is looked up by `(id, currentUserId)`; not-owned returns `404` (never `403`, to avoid leaking existence).

---

## Auth

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/auth/register` | `{email, password (12–128 chars), fullName (1–120)}` | `201 UserDto` + cookies |
| POST | `/auth/login` | `{email, password}` | `200 UserDto` + cookies; `401 INVALID_CREDENTIALS`; `423 ACCOUNT_LOCKED` after 5 failures (15 min) |
| POST | `/auth/refresh` | – (cookie) | `200 UserDto` + rotated cookies; `401` |
| POST | `/auth/logout` | – | `204`, revokes refresh family, clears cookies |
| GET | `/auth/me` | – | `UserDto` |

`UserDto = {id, email, fullName, roles: Role[], createdAt}`

## Profile & consent

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/profile` | – | `ProfileDto` |
| PUT | `/profile` | `{fields: [{key, value}], confirmKeys?: string[]}` | `ProfileDto` |
| GET | `/profile/consents` | – | `ConsentDto[]` (latest per purpose; purposes never granted appear with `granted:false, grantedAt:null`) |
| POST | `/profile/consents` | `{purpose: ConsentPurpose, granted: boolean}` | `ConsentDto` |
| POST | `/privacy/deletion` | `{scope: "DOCUMENTS"\|"PROFILE"\|"ACCOUNT", confirm: "DELETE"}` | `200 {scope, deletedDocuments, deletedProfileFields, accountDeleted}` |

```
ProfileDto = {
  userId, updatedAt,
  completeness: {percent: 0-100, coreFieldCount, coreFilledCount, missingCoreFields: string[]},
  fields: [{
    key, label, type: ProfileFieldType, group, purpose, sensitivity, core: boolean, options?: string[], unit?,
    value: any|null, status: ProfileFieldStatus, source: FieldSource|null,
    sourceDocumentId: uuid|null, confidence: number|null, updatedAt: string|null
  }]
}
ConsentDto = {purpose, granted, noticeVersion, grantedAt, revokedAt, description}
```
PUT semantics: setting a value → status `PROVIDED`, source `USER_INPUT`. `confirmKeys` moves `EXTRACTED`/`PROVIDED` fields to `USER_CONFIRMED`. `value: null` clears the field (→ `MISSING`). Values are validated against the field definition (type, enum options, min/max). `VERIFIED` can only be set by a verification adapter (none is operational yet). Any change bumps `data_version` of the user's non-terminal applications and invalidates their `PENDING`/unconsumed `APPROVED` approvals (reason `DATA_CHANGED`). Requires consent `PROFILE_PROCESSING` (403 `CONSENT_REQUIRED` otherwise).

## Documents

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/documents` | `?page&size&category` | `Page<DocumentDto>` |
| POST | `/documents` | multipart `file` (PDF/PNG/JPEG, ≤10 MB, checked by magic bytes), `category: DocumentCategory` | `201 DocumentDto` (extraction starts asynchronously) |
| GET | `/documents/{id}` | – | `DocumentDto` |
| GET | `/documents/{id}/content` | – | file bytes (`Content-Disposition: attachment`), logs access |
| DELETE | `/documents/{id}` | – | `204` (object deleted from storage, row soft-deleted, profile fields sourced from it keep value but `sourceDocumentId=null`) |
| POST | `/documents/{id}/extract` | – | `202 DocumentDto` (re-run) |
| POST | `/documents/{id}/review` | `{accept: [{key, value}], reject: string[]}` | `DocumentDto`; accepted fields are written to the profile with status `USER_CONFIRMED`, source `DOCUMENT_EXTRACTION`, `sourceDocumentId=id` |
| GET | `/documents/{id}/access-log` | – | `DocumentAccessDto[]` |

```
DocumentDto = {id, fileName, category, mimeType, sizeBytes, sha256, uploadedAt,
  extractionStatus: ExtractionStatus, reviewStatus: ReviewStatus,
  extraction: null | {classification: {category, confidence, declaredCategoryMatches}, fields: [{key, label, value, confidence, profileField: string|null}],
                      discrepancies: [{field, message, severity: "INFO"|"WARNING"|"ERROR"}], warnings: string[], engine, completedAt, errorMessage: string|null}}
DocumentAccessDto = {id, actorType: "USER"|"AGENT"|"SYSTEM", actorId, taskId, action: "UPLOAD"|"VIEW"|"DOWNLOAD"|"EXTRACT"|"PORTAL_UPLOAD"|"DELETE", purpose, occurredAt}
```
Upload requires consent `DOCUMENT_PROCESSING`. Files are stored with SSE (MinIO KMS / S3 SSE) at `users/{userId}/documents/{documentId}`.

## Services, discovery, eligibility

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/services` | `?q&category&jurisdiction&state&page&size` | `Page<ServiceSummaryDto>` |
| GET | `/services/{id}` | – | `ServiceDetailDto` |
| POST | `/services/discover` | `{query (3–500 chars), state?, category?}` | `DiscoveryResponseDto` |
| POST | `/services/{id}/eligibility` | – | `EligibilityResult` (persisted; requires `ELIGIBILITY_EVALUATION` consent) |
| GET | `/services/categories` | – | `[{category, count}]` |
| GET | `/services/jurisdictions` | – | `[{code, name, type: "STATE"\|"UNION_TERRITORY"}]` (36 entries) |

```
ServiceSummaryDto = {id, code, version, name, summary, department, ministry, category,
  jurisdiction: {level: JurisdictionLevel, states: string[]},   // ISO 3166-2:IN codes, [] for all-India
  verificationStatus, automationStatus, officialUrl, isTestService,
  deadline: null | {date: "YYYY-MM-DD"|null, note, sourceUrl, verifiedAt},
  lastVerifiedAt, eligibility: null | {outcome, evaluatedAt}}     // latest evaluation for current user
ServiceDetailDto = ServiceSummaryDto + {description, benefits: string[],
  eligibilityRules: [{ruleId, ruleVersion, description, field, operator, value, sourceUrl}],
  requiredDocuments: [{category, description, mandatory}],
  procedure: [{order, title, description}],
  sources: [{url, title, kind, retrievedAt}], portalDomains: string[], disclaimer}
DiscoveryResponseDto = {interpretation: {summary, categories, keywords, state, usedLlm},
  results: [{service: ServiceSummaryDto, score, matchReasons: string[], eligibility: EligibilityResult|null}],
  generatedAt, notice}
```

## Applications

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/applications` | `{serviceId}` | `201 ApplicationDto`; `409 APPLICATION_EXISTS` if a non-terminal one exists |
| GET | `/applications` | `?page&size&status` | `Page<ApplicationDto>` |
| GET | `/applications/{id}` | – | `ApplicationDto` |
| POST | `/applications/{id}/execute` | – | `202 AgentTaskDto`; requires `AGENT_EXECUTION` consent; `409` if a task is active; `422 AUTOMATION_UNAVAILABLE` if service `automationStatus` is `NOT_AUTOMATED` |
| POST | `/applications/{id}/cancel` | – | `ApplicationDto` |
| GET | `/applications/{id}/receipts` | – | `ReceiptDto[]` |
| GET | `/applications/{id}/receipts/{receiptId}/artifact` | – | `image/png` |

```
ApplicationDto = {id, service: ServiceSummaryDto, status: ApplicationStatus, dataVersion,
  referenceNumber, eligibilityOutcome, createdAt, updatedAt, lastActivityAt, submittedAt,
  requiredAction: null | {type: "APPROVAL"|"CITIZEN_INPUT"|"DOCUMENT"|"PROFILE"|"REVIEW_UNCERTAIN", message, approvalId?, taskId?},
  latestTaskId, isTestEnvironment}
ReceiptDto = {id, applicationId, taskId, referenceNumber, acknowledgementText, portalUrl,
  verificationMethod, verifiedAt, isTestEnvironment, artifactAvailable}
```

## Agent tasks & live events

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/agent/tasks` | `?page&size` | `Page<AgentTaskDto>` |
| GET | `/agent/tasks/{id}` | – | `AgentTaskDto` |
| GET | `/agent/tasks/{id}/events` | header `Last-Event-ID?` | `text/event-stream` (see AGENT_CONTRACTS §2.2) |
| GET | `/agent/tasks/{id}/events/history` | `?afterSequence=0&limit=200` | `ExecutionEventDto[]` |
| POST | `/agent/tasks/{id}/cancel` | `{reason?}` | `202 AgentTaskDto` |
| POST | `/agent/tasks/{id}/citizen-input` | `{requestId, value (1–200 chars)}` | `202` — value is forwarded to the agent and never stored or logged |
| POST | `/agent/tasks/{id}/retry` | – | `202 AgentTaskDto`. Only for runs that crashed mid-way (`FAILED`, retryable); otherwise `409 TASK_NOT_RESUMABLE` — start a new run with `POST /applications/{id}/execute` |
| GET | `/agent/tasks/{id}/screenshots/{screenshotId}` | – | `image/png` |

```
AgentTaskDto = {id, applicationId, serviceName, status: TaskStatus, currentStep, currentAgent,
  createdAt, startedAt, updatedAt, completedAt, recoveryAttempts,
  pendingApproval: ApprovalDto|null,
  pendingCitizenInput: null | {requestId, kind: CitizenInputKind, prompt, screenshotId},
  lastObservation: null | {url, title, screenshotId, at},
  error: null | {code, message}, lastSequence}
ExecutionEventDto = AGENT_CONTRACTS §2.2 envelope
```

## Approvals

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/approvals` | `?status&page&size` | `Page<ApprovalDto>` |
| GET | `/approvals/{id}` | – | `ApprovalDto` |
| POST | `/approvals/{id}/decision` | `{decision: "APPROVE"\|"REJECT", dataVersion: int, reason?}` | `ApprovalDto`; `409 APPROVAL_NOT_PENDING`, `409 APPROVAL_DATA_CHANGED` (dataVersion mismatch → approval INVALIDATED), `410 APPROVAL_EXPIRED` |

```
ApprovalDto = {id, taskId, applicationId, serviceName, kind: ApprovalKind, actionClass: ActionClass,
  summary, details: {fields: [{label, value}], documents: [{documentId, category, fileName?}], portal},
  dataVersion, status: ApprovalStatus, createdAt, expiresAt, decidedAt, decisionReason}
```
On APPROVE, Spring signs an approval token (AGENT_CONTRACTS §4) and calls agent `POST /internal/v1/tasks/{taskId}/resume`. The token is never returned to the browser.

## Trust center, audit, notifications

| Method | Path | Response |
|---|---|---|
| GET | `/audit/events?page&size&category` | `Page<AuditEventDto>` — own events only. `category`: `AUTH`, `PROFILE`, `CONSENT`, `DOCUMENT`, `APPLICATION`, `AGENT`, `APPROVAL`, `PRIVACY` |
| GET | `/notifications?unreadOnly` | `Page<NotificationDto>` |
| POST | `/notifications/{id}/read` | `204` |
| GET | `/dashboard` | `{profileCompleteness, activeApplications: ApplicationDto[], pendingApprovals: ApprovalDto[], pendingCitizenInputs: AgentTaskDto[], recentActivity: AuditEventDto[], recommended: ServiceSummaryDto[], upcomingDeadlines: ServiceSummaryDto[]}` — recommended = services whose latest evaluation for the user is `ELIGIBLE`/`UNVERIFIED`/`NEEDS_MORE_INFORMATION`; deadlines only where `deadline.date` is set and `verifiedAt` present |

```
AuditEventDto = {id, category, action, resourceType, resourceId, outcome: "SUCCESS"|"DENIED"|"FAILURE", occurredAt, summary}
NotificationDto = {id, type, title, body, link, readAt, createdAt}
```

## Admin (role `ADMIN`)

| Method | Path | Response |
|---|---|---|
| GET | `/admin/tasks?status=FAILED` | `Page<AgentTaskDto + userId>` |
| GET | `/admin/tasks/{id}/events` | `ExecutionEventDto[]` |
| GET | `/admin/services` | `Page<ServiceSummaryDto>` incl. drafts and test services |
| POST | `/admin/registry/sync` | `{synced, deactivated, errors}` |

## Internal (agent → api), `Authorization: Bearer ${API_INTERNAL_TOKEN}`

`POST /internal/v1/agent-events`, `POST /internal/v1/approvals`, `POST /internal/v1/receipts`, `GET /internal/v1/tasks/{id}/context` — see `docs/AGENT_CONTRACTS.md §2`.

## Health

`GET /actuator/health/liveness`, `GET /actuator/health/readiness` (DB, Redis, storage, agent reachability as non-fatal detail), `GET /actuator/prometheus` (internal network only).
