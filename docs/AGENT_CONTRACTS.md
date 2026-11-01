# CIVIX — Shared contracts (enums, events, internal APIs, approval tokens)

This is the single source of truth shared by `apps/api` (Java), `apps/agent` (Python) and `apps/web` (TypeScript).
All JSON is camelCase. All timestamps are ISO-8601 UTC strings (`2026-11-01T09:30:00Z`). All ids are UUID strings unless stated.

---

## 1. Enums (identical spelling everywhere)

| Enum | Values |
|---|---|
| `Role` | `CITIZEN`, `ADMIN` |
| `ProfileFieldStatus` | `MISSING`, `PROVIDED`, `EXTRACTED`, `USER_CONFIRMED`, `VERIFIED` |
| `FieldSource` | `USER_INPUT`, `DOCUMENT_EXTRACTION`, `VERIFIED_SOURCE` |
| `ProfileFieldType` | `STRING`, `NUMBER`, `INTEGER`, `BOOLEAN`, `DATE`, `ENUM` |
| `Sensitivity` | `LOW`, `MEDIUM`, `HIGH` |
| `ConsentPurpose` | `PROFILE_PROCESSING`, `DOCUMENT_PROCESSING`, `ELIGIBILITY_EVALUATION`, `AGENT_EXECUTION`, `PORTAL_DOCUMENT_UPLOAD` |
| `DocumentCategory` | `IDENTITY_PROOF`, `ADDRESS_PROOF`, `INCOME_CERTIFICATE`, `CASTE_CERTIFICATE`, `ACADEMIC_RECORD`, `BANK_DOCUMENT`, `RESIDENCE_CERTIFICATE`, `DISABILITY_CERTIFICATE`, `PHOTOGRAPH`, `OTHER` |
| `ExtractionStatus` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `ReviewStatus` | `NOT_REVIEWED`, `REVIEWED` |
| `ServiceCategory` | `EDUCATION`, `SCHOLARSHIPS`, `CERTIFICATES`, `IDENTITY`, `WELFARE`, `AGRICULTURE`, `BUSINESS`, `LICENSES`, `EMPLOYMENT`, `UTILITIES`, `OTHER` |
| `JurisdictionLevel` | `CENTRAL`, `STATE` |
| `ServiceVerificationStatus` | `DRAFT`, `SOURCE_VERIFIED`, `WORKFLOW_VERIFIED`, `DEPRECATED` |
| `AutomationStatus` | `NOT_AUTOMATED`, `GUIDED`, `TEST_ONLY`, `ACTIVE` |
| `RuleOperator` | `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `IN`, `NOT_IN`, `EXISTS`, `DATE_BEFORE`, `DATE_AFTER` |
| `RuleResult` | `PASS`, `FAIL`, `UNKNOWN` |
| `EligibilityOutcome` | `ELIGIBLE`, `INELIGIBLE`, `NEEDS_MORE_INFORMATION`, `UNVERIFIED` |
| `ApplicationStatus` | `DRAFT`, `IN_PROGRESS`, `ACTION_REQUIRED`, `SUBMITTED`, `SUBMISSION_UNCERTAIN`, `FAILED`, `CANCELLED` |
| `TaskStatus` | `QUEUED`, `RUNNING`, `WAITING_APPROVAL`, `WAITING_CITIZEN`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `ApprovalKind` | `PREPARATION`, `DOCUMENT_UPLOAD`, `FINAL_SUBMISSION` |
| `ActionClass` | `READ_ONLY`, `REVERSIBLE`, `SENSITIVE`, `CONSEQUENTIAL` |
| `ApprovalStatus` | `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `INVALIDATED`, `CONSUMED` |
| `ApprovalDecision` | `APPROVE`, `REJECT` |
| `CitizenInputKind` | `LOGIN`, `OTP`, `CAPTCHA`, `DECLARATION`, `PAYMENT` |
| `AgentName` | `ORCHESTRATOR`, `PROFILE_AGENT`, `DISCOVERY_AGENT`, `ELIGIBILITY_AGENT`, `DOCUMENT_AGENT`, `PLANNING_AGENT`, `BROWSER_AGENT`, `RECOVERY_AGENT`, `VERIFICATION_AGENT` |

Eligibility outcome rules (deterministic, `apps/agent/app/policies/eligibility.py`):
- any rule `FAIL` → `INELIGIBLE`
- else any rule `UNKNOWN` (field missing) → `NEEDS_MORE_INFORMATION`
- else (all `PASS`) but the service is `DRAFT`, or any rule relied on a field whose status is `EXTRACTED` (not yet confirmed by the citizen) → `UNVERIFIED`
- else → `ELIGIBLE` (always displayed as "appears eligible on the information provided; the department decides").

Action class policy (enforced in `apps/agent/app/policies/actions.py` and the browser worker):

| Action | Class | Gate |
|---|---|---|
| Read public page, observe, screenshot | `READ_ONLY` | automatic |
| Evaluate eligibility, plan | `READ_ONLY` | automatic |
| Fill a draft form field | `REVERSIBLE` | `AGENT_EXECUTION` consent + `PREPARATION` approval |
| Upload a document to a portal | `SENSITIVE` | `PORTAL_DOCUMENT_UPLOAD` consent + `DOCUMENT_UPLOAD` approval scoped to document ids |
| OTP / CAPTCHA / login / legal declaration / payment | citizen-controlled | `CITIZEN_INPUT_REQUIRED`; payment is never automated |
| Final submit | `CONSEQUENTIAL` | `FINAL_SUBMISSION` approval token, verified by agent **and** browser worker |

---

## 2. Execution events

### 2.1 Event types

`TASK_CREATED`, `TASK_STARTED`, `PROFILE_VALIDATED`, `SERVICE_DISCOVERED`, `ELIGIBILITY_EVALUATED`, `DOCUMENT_REQUIRED`, `DOCUMENT_EXTRACTED`, `PLAN_CREATED`, `APPROVAL_REQUIRED`, `APPROVAL_GRANTED`, `APPROVAL_REJECTED`, `BROWSER_STARTED`, `BROWSER_ACTION_COMPLETED`, `RECOVERY_STARTED`, `EXECUTION_PAUSED`, `CITIZEN_INPUT_REQUIRED`, `EXECUTION_RESUMED`, `SUBMISSION_APPROVAL_REQUIRED`, `SUBMISSION_COMPLETED`, `SUBMISSION_UNCERTAIN`, `RECEIPT_VERIFIED`, `TASK_COMPLETED`, `TASK_FAILED`, `TASK_CANCELLED`

(The prompt's 18 types plus `TASK_STARTED`, `APPROVAL_REJECTED`, `CITIZEN_INPUT_REQUIRED`, `EXECUTION_RESUMED`, `SUBMISSION_UNCERTAIN`, `TASK_COMPLETED`.)

### 2.2 Envelope (as streamed to the browser over SSE)

```json
{
  "eventId": "6f1c…",            // UUID, idempotency key
  "taskId": "…",
  "sequence": 42,                // assigned by Spring, strictly increasing per DB; SSE `id:` field
  "type": "BROWSER_ACTION_COMPLETED",
  "timestamp": "2026-11-01T09:30:00Z",
  "agent": "BROWSER_AGENT",
  "step": "fill_personal_details", // nullable plan step id
  "message": "Filled 6 fields on 'Personal details'",   // safe display text, no PII beyond field labels
  "metadata": { "url": "http://test-portal:8090/apply/personal", "screenshotId": "…", "fields": ["Full name", "Date of birth"] }
}
```

SSE framing: `id: <sequence>\nevent: <type>\ndata: <json>\n\n`. Reconnect with `Last-Event-ID: <sequence>` replays everything after it from `execution_events`. A heartbeat comment (`: ping`) every 15 s.

Never put in `message`/`metadata`: passwords, OTPs, tokens, cookies, document text, full field **values**. Field labels are fine; values are shown only via authenticated `GET /agent/tasks/{id}` (owner-only).

Metadata conventions by type:

| Type | Metadata keys |
|---|---|
| `APPROVAL_REQUIRED`, `SUBMISSION_APPROVAL_REQUIRED` | `approvalId`, `kind`, `expiresAt` |
| `CITIZEN_INPUT_REQUIRED` | `requestId`, `kind` (`CitizenInputKind`), `prompt`, `screenshotId?` |
| `BROWSER_STARTED`, `BROWSER_ACTION_COMPLETED` | `url`, `title?`, `screenshotId?`, `action`, `fields?` (labels) |
| `RECOVERY_STARTED` | `reason` (`MISSING_SELECTOR`/`TIMEOUT`/`NETWORK`/`SESSION_EXPIRED`/`LAYOUT_CHANGED`/`BROWSER_CRASH`/`INTERRUPTED`), `attempt`, `maxAttempts` |
| `ELIGIBILITY_EVALUATED` | `outcome`, `failed`, `unknown` (rule ids) |
| `PLAN_CREATED` | `steps` (count), `stepIds` |
| `RECEIPT_VERIFIED` | `receiptId`, `referenceNumber`, `isTestEnvironment` |
| `TASK_FAILED` | `errorCode`, `retryable` |
| any | `checkpointId?` |

### 2.3 Agent → Spring: `POST /internal/v1/agent-events`

Auth: `Authorization: Bearer ${API_INTERNAL_TOKEN}`.

```json
{
  "events": [
    {
      "eventId": "uuid", "taskId": "uuid", "type": "PLAN_CREATED",
      "timestamp": "…", "agent": "PLANNING_AGENT", "step": null,
      "message": "Created a 7-step plan", "metadata": {},
      "taskStatus": "RUNNING",            // optional TaskStatus
      "applicationStatus": "IN_PROGRESS", // optional ApplicationStatus
      "currentStep": "navigate_portal",   // optional
      "currentAgent": "PLANNING_AGENT",   // optional
      "pendingCitizenInput": null,        // optional {requestId, kind, prompt, screenshotId}; null clears
      "observation": null,                // optional {url, title, screenshotId, at}
      "error": null                       // optional {code, message}
    }
  ]
}
```
Response `200 {"accepted": n, "duplicates": m, "rejectedTransitions": k}`. Duplicate `eventId`s are ignored (idempotent).
Events are facts and are always recorded; a `taskStatus`/`applicationStatus` that would be an illegal transition
(docs/WORKFLOWS.md) is **ignored**, logged and counted in `rejectedTransitions` — never applied. Unknown event types or
malformed ids are rejected with `422` for the whole batch.

Terminal events (`TASK_COMPLETED`, `TASK_FAILED`, `TASK_CANCELLED`) carry `metadata.errorCode`, `metadata.retryable`,
`metadata.outcome` (an `ApplicationStatus`) and `metadata.requiredAction` (`{type, message, fields?|categories?}` or null).
When the application ends in `ACTION_REQUIRED` or `SUBMISSION_UNCERTAIN`, Spring stores `metadata.requiredAction` (plus `taskId`)
as the application's `requiredAction`.

### 2.4 Agent → Spring: `POST /internal/v1/approvals`

```json
{
  "taskId": "uuid", "applicationId": "uuid",
  "kind": "FINAL_SUBMISSION", "actionClass": "CONSEQUENTIAL",
  "summary": "Submit application to CIVIX Test Portal",
  "details": { "fields": [{"label": "Full name", "value": "…"}], "documents": [{"documentId": "…", "category": "INCOME_CERTIFICATE"}], "portal": "test-portal" },
  "payloadDigest": "sha256-hex of canonical JSON of the data that will be submitted",
  "dataVersion": 3,
  "expiresInSeconds": 900
}
```
Response `201 {"approvalId": "uuid", "status": "PENDING", "expiresAt": "…"}`. Spring creates an in-app notification. Spring does **not** emit the event; the agent emits `APPROVAL_REQUIRED` / `SUBMISSION_APPROVAL_REQUIRED` (with `taskStatus: WAITING_APPROVAL`) after it receives the id, so the event carries `approvalId`. Creating a new `PENDING` approval of the same kind for a task supersedes (sets `INVALIDATED`) the previous pending one.

### 2.5 Agent → Spring: `POST /internal/v1/receipts`

```json
{
  "taskId": "uuid", "applicationId": "uuid",
  "referenceNumber": "TP-2026-000123",
  "acknowledgementText": "Your application has been received…",
  "portalUrl": "http://test-portal:8090/apply/acknowledgement",
  "verificationMethod": "DOM_TEXT_MATCH",
  "evidence": { "matchedSelector": "[data-testid=reference-number]", "pageTitle": "Acknowledgement" },
  "screenshotId": "uuid",
  "isTestEnvironment": true
}
```
Response `201 {"receiptId": "uuid"}`. Spring sets application `SUBMITTED` + `reference_number`.

### 2.6 Agent → Spring: `GET /internal/v1/tasks/{taskId}/context`

Returns the current `{ "dataVersion": 3, "applicationStatus": "IN_PROGRESS", "citizenContext": {…}, "documents": [] }` so the agent can detect material changes before consequential steps.

### 2.7 Screenshots

The agent stores screenshots directly in object storage at `tasks/{taskId}/screenshots/{screenshotId}.png` (bucket `${S3_BUCKET}`) and references them by `screenshotId` only. Spring serves them to the owner at `GET /api/v1/agent/tasks/{taskId}/screenshots/{screenshotId}` after validating ownership and that `screenshotId` is a UUID.

---

## 3. Spring → Agent internal API (`apps/agent`, port 8000)

Auth: `Authorization: Bearer ${AGENT_INTERNAL_TOKEN}`. Never exposed publicly (internal docker network only).

### 3.1 Shared objects

`CitizenContext`
```json
{
  "userId": "uuid",
  "fields": {
    "annual_family_income": {"value": 250000, "status": "USER_CONFIRMED", "source": "USER_INPUT", "sourceDocumentId": null, "confidence": null}
  },
  "consents": ["AGENT_EXECUTION", "ELIGIBILITY_EVALUATION"]
}
```
Only fields the citizen consented to for the purpose are included. Missing fields are simply absent.

`TaskDocument`
```json
{"documentId": "uuid", "category": "INCOME_CERTIFICATE", "storageKey": "users/<uid>/documents/<id>", "mimeType": "application/pdf", "sha256": "…", "fileName": "income.pdf", "reviewed": true}
```

`EligibilityResult`
```json
{
  "serviceCode": "IN_CENTRAL_NSP_CSSS", "serviceVersion": 1,
  "outcome": "NEEDS_MORE_INFORMATION",
  "rules": [
    {"ruleId": "income_limit", "ruleVersion": 1, "field": "annual_family_income", "operator": "LESS_THAN_OR_EQUAL",
     "expected": 450000, "actual": 250000, "result": "PASS", "fieldStatus": "USER_CONFIRMED",
     "explanation": "Annual family income ₹2,50,000 is within the limit of ₹4,50,000.",
     "evidence": {"sourceUrl": "https://…", "sourceTitle": "…", "retrievedAt": "2026-11-01"}}
  ],
  "matched": ["income_limit"], "failed": [], "unknown": ["class12_percentile"],
  "missingFields": ["class12_percentile"],
  "explanation": "…", "evaluatedAt": "…", "engineVersion": "1.0.0"
}
```

### 3.2 Endpoints

| Method | Path | Body → Response |
|---|---|---|
| GET | `/internal/v1/health` | `{status, checks:{database, storage, browserWorker, llm}}` (no auth) |
| GET | `/internal/v1/registry/services` | `{services:[ServiceDefinition…], profileFieldsVersion, generatedAt}` — validated YAML (`service-registry/schemas/service-definition.schema.json`). `?includeTest=true` adds test services |
| GET | `/internal/v1/registry/profile-fields` | `{version, fields:[{key,label,type,group,purpose,sensitivity,core,options?,unit?}]}` |
| POST | `/internal/v1/eligibility/evaluate` | `{serviceCode, citizenContext}` → `EligibilityResult` |
| POST | `/internal/v1/discovery` | `{query, filters:{state?, category?, jurisdiction?}, citizenContext?, includeTest?, limit?}` → `{interpretation:{summary, categories[], keywords[], state?, usedLlm}, results:[{serviceCode, score, matchReasons[], eligibility: EligibilityResult\|null, sources:[{url,title,retrievedAt}]}], generatedAt}` |
| GET | `/internal/v1/data-gov/resources/{resourceId}` | Reads a specific `data.gov.in` resource using the configured `DATA_GOV_API_KEY`; `?limit=1..100&offset>=0`. Returns the upstream JSON object; `503` when unconfigured and `502` when upstream fails |
| POST | `/internal/v1/documents/extract` | `{documentId, storageKey, mimeType, declaredCategory, citizenContext?, otherDocuments?:[{documentId, category, fields:[…]}]}` → `{documentId, classification:{category, confidence, declaredCategoryMatches}, fields:[{key, label, value, confidence, profileField}], discrepancies:[{field, message, severity, documentIds}], warnings[], engine, textCharacters, requiresReview:true}` |
| POST | `/internal/v1/tasks` | `{taskId, applicationId, userId, serviceCode, intent:{type:"APPLY"\|"DISCOVER", text?}, citizenContext, documents:[TaskDocument], dataVersion}` → `202 {taskId, status:"QUEUED"}` |
| GET | `/internal/v1/tasks/{id}` | → `{taskId, status, currentNode, currentStep, plan?, pendingInterrupt?, recoveryAttempts, errors[]}` |
| POST | `/internal/v1/tasks/{id}/resume` | `{kind:"APPROVAL", approval:{approvalId, approvalKind, decision, token?, payloadDigest, expiresAt}}` or `{kind:"CITIZEN_INPUT", citizenInput:{requestId, value}}` or `{kind:"RETRY"}` → `202`; `409` when the task is not waiting for that approval/input, or (RETRY) when the run did not crash mid-graph — finished runs need a new task |
| POST | `/internal/v1/tasks/{id}/cancel` | `{reason?}` → `202` |

`citizenInput.value` is kept only in process memory (`SecretInbox`), typed into the page, then discarded. It is never checkpointed or logged.

---

## 4. Approval tokens

Issued by Spring after an `APPROVE` decision; verified by the agent (resume) and by the browser worker (submit action).

```
token = base64url(payloadJson) + "." + base64url(HMAC_SHA256(APPROVAL_SIGNING_SECRET, base64url(payloadJson)))
payloadJson = {"aid": approvalId, "tid": taskId, "app": applicationId, "uid": userId,
               "kind": "FINAL_SUBMISSION", "dig": payloadDigest, "exp": <epoch seconds>}
```
base64url without padding. `payloadJson` is compact JSON (no spaces) with keys in exactly the order above.
Verification: constant-time HMAC compare; `exp > now`; `tid`, `kind`, `dig` must equal the pending approval and the digest the agent recomputes from the data it is about to submit. Any mismatch → refuse, emit `TASK_FAILED` (`APPROVAL_INVALID`), never submit.

**Cross-language test vector** (both Java and Python test suites assert this):
- secret: `test-approval-secret-0123456789abcdef`
- payload: `{"aid":"11111111-1111-1111-1111-111111111111","tid":"22222222-2222-2222-2222-222222222222","app":"33333333-3333-3333-3333-333333333333","uid":"44444444-4444-4444-4444-444444444444","kind":"FINAL_SUBMISSION","dig":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","exp":1893456000}`
- token: `eyJhaWQiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ0aWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJhcHAiOiIzMzMzMzMzMy0zMzMzLTMzMzMtMzMzMy0zMzMzMzMzMzMzMzMiLCJ1aWQiOiI0NDQ0NDQ0NC00NDQ0LTQ0NDQtNDQ0NC00NDQ0NDQ0NDQ0NDQiLCJraW5kIjoiRklOQUxfU1VCTUlTU0lPTiIsImRpZyI6ImFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWEiLCJleHAiOjE4OTM0NTYwMDB9.akemDwop-UVWo6rtxQVZn0e9pDl8DaFOP1mv8utCLsE`

`payloadDigest` = SHA-256 hex of canonical JSON (`sort_keys=True, separators=(",",":")`, UTF-8) of `{"fields": {stepId: {fieldKey: value}}, "documents": [sorted documentIds], "serviceCode": "...", "dataVersion": n}`.

---

## 5. Browser worker internal API (port 8001, `Authorization: Bearer ${BROWSER_WORKER_TOKEN}`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/internal/v1/sessions` | `{taskId, allowedDomains[], viewport?}` → `{sessionId}` (new isolated context) |
| GET | `/internal/v1/sessions/{id}/observe` | `{url, title, ariaSnapshot, formFields:[{label, name, role, type, required, value?, options?}], headings[], alerts[], screenshotPng(base64)}` |
| POST | `/internal/v1/sessions/{id}/actions` | `{action:"NAVIGATE"\|"FILL"\|"SELECT"\|"CHECK"\|"CLICK"\|"UPLOAD"\|"SUBMIT"\|"WAIT_FOR", target:{label?, role?, name?, placeholder?, testId?, css?}, value?, documentId?, approvalToken?, expected?:{urlContains?, textVisible?}, timeoutMs?}` → `{ok, verified, observation?, error?:{code, message}}` |
| POST | `/internal/v1/sessions/{id}/files` | multipart `file`, `documentId`, `approvalToken` (`DOCUMENT_UPLOAD` token whose digest covers the document) → `{documentId, staged:true}` |
| DELETE | `/internal/v1/sessions/{id}` | close + wipe context and staged files |
| GET | `/internal/v1/health` | |

The worker enforces: domain allowlist at navigation **and** request level (`context.route`), private-IP blocking (configurable for the local test portal only), uploads only from files the agent staged for an approved `documentId`, `SUBMIT` only with a valid `FINAL_SUBMISSION` token, `CLICK` on elements whose accessible name matches submit/pay patterns is refused (must use `SUBMIT`).
