# Demo walkthrough

Uses the **CIVIX Test Portal** — an authorized local test environment bundled in this repo. It is not a government
website and nothing submitted there reaches any government system. Every screen and receipt for it is labelled.

## 0. Start
```bash
make env
# in .env make sure: CIVIX_ENABLE_TEST_SERVICES=true and BROWSER_PRIVATE_HOST_ALLOWLIST=test-portal
make up-test
open http://localhost:3000
```

## 1. Register and onboard
Register, then in **Onboarding** grant the purposes you want (Profile processing, Document processing, Eligibility
evaluation, Agent execution, Portal document upload) and fill core fields. For the test scholarship you need: full name,
date of birth, gender, mobile number, State, study level (Undergraduate), course name, institution name, Class XII %
(≥ 60) and annual family income (≤ ₹6,00,000).

## 2. Documents
In **Documents** upload any PDF/PNG/JPEG as *Income certificate* and another as *Academic record*. Watch extraction
complete, open **Review**, accept or correct the extracted values. Note the banner: extraction is not verification.

## 3. Discover
In **Assistant** type: *"I am an undergraduate student. Find scholarships and financial assistance programmes I may
qualify for."* You get registry-backed results with per-rule PASS/FAIL/UNKNOWN, citations and retrieval dates. Real
schemes are *Guided only*. Open **CIVIX Test Portal — Demo Merit Scholarship (test environment)** and start an application.

## 4. Run the agent (Agent Control Room)
Click **Run with CIVIX agent**. The control room streams events live:
1. Profile check, service selection, eligibility, document check, 10-step plan.
2. **Approval: Fill N fields** — review the exact values, approve.
3. **Approval: Upload 2 documents** — approve.
4. Browser opens the portal and enters your mobile number. **Citizen input: OTP.** The test portal "sends" the OTP to its
   test inbox instead of SMS. The portal is on an internal network, so read the OTP with
   `make test-otp MOBILE=<your number>` and type it into the OTP card.
5. Forms are filled, files uploaded. **Citizen input: Declaration** — type `ACCEPT` to make the declaration yourself.
6. Review page is checked against the approved values. **Approval: Submit the application** (CONSEQUENTIAL).
7. One click on *Submit application*, then verification: the reference `TP-2026-######` is read from the page and
   confirmed on the portal's status page. A receipt (with screenshot) appears under the application, marked
   *Test environment*.

## 5. Try the failure paths
Use `make test-fault FAULT='{"variant":"B"}'` (posts to the test portal's `/test-control/faults`):
- `{"variant":"B"}` — changed labels/ids/buttons → recovery remaps by visible labels (`LAYOUT_CHANGED`).
- `{"fail_next":1}` — one HTTP 503 → retried (`NETWORK`).
- `{"omit_reference":true}` — acknowledgement without a reference → **Submission not confirmed**, no receipt, no resubmission.
- `make test-expire-sessions` during a pause → `SESSION_EXPIRED` → re-sign-in with a new OTP.
- Reject any approval → the run stops and nothing further happens.
- Edit your profile while a final approval is pending → the approval is withdrawn (`DATA_CHANGED`).

## 6. Trust Center
See granted/revoked permissions, documents accessed by the agent (`PORTAL_UPLOAD` entries), every run and approval, and
delete documents, profile or the whole account.
