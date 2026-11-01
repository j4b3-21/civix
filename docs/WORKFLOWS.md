# CIVIX workflows and state machines

## 1. Orchestration graph (`apps/agent/app/orchestrator/graph.py`)

```
START → intent_analysis → profile_retrieval → service_discovery → eligibility_evaluation
      → document_validation → execution_planning → human_approval (interrupt: PREPARATION)
      → browser_execution ⇄ recovery
      → final_submission_approval (interrupt: FINAL_SUBMISSION) → submission → verification → application_result → END
```

Conditional routing:

| From | Condition | To |
|---|---|---|
| `service_discovery` | service not found / not automatable | `application_result` (FAILED `SERVICE_UNAVAILABLE`) |
| `eligibility_evaluation` | `INELIGIBLE` | `application_result` (task FAILED `INELIGIBLE`, non-retryable; application `FAILED`) |
| `eligibility_evaluation` | `NEEDS_MORE_INFORMATION` | `application_result` (application `ACTION_REQUIRED`, requiredAction PROFILE) |
| `document_validation` | mandatory document missing / unreviewed | `application_result` (`ACTION_REQUIRED`, requiredAction DOCUMENT, event `DOCUMENT_REQUIRED`) |
| `execution_planning` | profile values missing for mandatory form fields | `application_result` (`ACTION_REQUIRED`, PROFILE) |
| `human_approval` | REJECT | `application_result` (CANCELLED) |
| `browser_execution` | step error | `recovery` |
| `browser_execution` | portal requires login/OTP/CAPTCHA | interrupt `CITIZEN_INPUT` → `browser_execution` |
| `browser_execution` | all pre-submit steps done | `final_submission_approval` |
| `recovery` | recoverable & attempts < max (3 per step, 8 per task) | `browser_execution` |
| `recovery` | unrecoverable | `application_result` (FAILED) |
| `final_submission_approval` | REJECT / token invalid | `application_result` (CANCELLED / FAILED) |
| `submission` | submit click outcome unknown (timeout, crash after click) | `verification` (never re-click) |
| `verification` | reference found & matched | `application_result` (SUBMITTED + receipt) |
| `verification` | no acknowledgement | `application_result` (`SUBMISSION_UNCERTAIN`) |

When a run ends because the citizen must act outside the run (missing profile data, missing document), the task ends with `TASK_FAILED` (`errorCode` `NEEDS_PROFILE_INFORMATION` / `DOCUMENT_REQUIRED`, `retryable:false`) and the application moves to `ACTION_REQUIRED` with a `requiredAction`. Re-running `POST /applications/{id}/execute` creates a fresh task.

Every node: timeout (`NODE_TIMEOUT_SECONDS`, default 120 s; browser steps 60 s), checks cancellation flag before running, emits events, and the state is checkpointed by `AsyncPostgresSaver` after each node (schema `agent`). Interrupts use LangGraph `interrupt()`; resume uses `Command(resume=...)` with **non-secret** payloads only.

Restart behaviour: on boot the agent lists `agent.task_runs` with status `RUNNING` and re-invokes them from their last checkpoint. Browser sessions do not survive restarts; `browser_execution` detects the missing session, emits `RECOVERY_STARTED (INTERRUPTED)`, opens a new session and replays only `REVERSIBLE` steps. If the crash happened during `submission` (flag `submission_attempted=true` in state), the task goes to `verification` and then `SUBMISSION_UNCERTAIN` if no acknowledgement can be observed — it is never re-submitted automatically.

## 2. Task status transitions (enforced by Spring)

```
QUEUED → RUNNING | CANCELLED | FAILED
RUNNING → WAITING_APPROVAL | WAITING_CITIZEN | COMPLETED | FAILED | CANCELLED
WAITING_APPROVAL → RUNNING | CANCELLED | FAILED
WAITING_CITIZEN → RUNNING | CANCELLED | FAILED
FAILED → QUEUED (retry, only if error retryable)
COMPLETED, CANCELLED → (terminal)
```

## 3. Application status transitions (enforced by Spring)

```
DRAFT → IN_PROGRESS | CANCELLED
IN_PROGRESS → ACTION_REQUIRED | SUBMITTED | SUBMISSION_UNCERTAIN | FAILED | CANCELLED
ACTION_REQUIRED → IN_PROGRESS | CANCELLED | FAILED | DRAFT
SUBMISSION_UNCERTAIN → SUBMITTED (citizen-provided reference verified later) | FAILED
FAILED → IN_PROGRESS (retry) | CANCELLED
SUBMITTED, CANCELLED → (terminal)
```
Same-state "transitions" are no-ops and allowed.

## 4. Approval lifecycle

```
PENDING → APPROVED (decision, dataVersion matches, not expired)
PENDING → REJECTED
PENDING → EXPIRED   (scheduled job every 60 s, or lazily on decision)
PENDING|APPROVED → INVALIDATED (profile/document change bumps data_version; superseded by new request; task cancelled)
APPROVED → CONSUMED (agent reports BROWSER_ACTION_COMPLETED/SUBMISSION_COMPLETED with metadata.approvalId)
```

## 5. Document lifecycle

`upload → extractionStatus PENDING → PROCESSING → COMPLETED|FAILED`, `reviewStatus NOT_REVIEWED → REVIEWED` (on `/review`). Extracted values never reach the profile until the citizen accepts them.
