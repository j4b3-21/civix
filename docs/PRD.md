# CIVIX — Product requirements

## Problem
Citizens face fragmented portals, opaque eligibility rules, repeated data entry and document handling, outdated
interfaces and no visibility into application status. Existing assistants explain; they do not help complete.

## Users
- **Citizen** (primary): students first (scholarships), then welfare, certificates, business registration, licences.
- **Operator/admin**: maintains the service registry, inspects failed runs.

## Goals
1. One interface to discover services across Central and State governments, with official citations and retrieval dates.
2. Deterministic, explainable eligibility with explicit "unknown" handling.
3. Documents uploaded once, extracted with OCR, reviewed by the citizen, reused across applications.
4. Supervised autonomous execution on **verified, permitted** portal workflows, pausing for every consequential action.
5. Verified outcomes only: a receipt exists only when a reference number was observed on the portal.
6. Trust: consent per purpose, access history, approval history, deletion controls.

## Non-goals
- Bypassing OTP, CAPTCHA, login, or rate limits; automating payments; acting without the citizen's approval.
- Claiming universal portal support; claiming certificates are authentic because OCR succeeded.
- Being or impersonating a government website.

## Scope of this release
| Area | Scope |
|---|---|
| Registry | 7 definitions: 6 real services (4 Central scholarships, 1 Haryana State scholarship, Udyam MSME registration) with cited official sources; 1 test service bound to the local CIVIX Test Portal. Real services are `GUIDED`/`NOT_AUTOMATED`. |
| Eligibility | 11 operators, 4 outcomes, per-rule evidence and explanations. |
| Documents | PDF/PNG/JPEG ≤ 10 MB, magic-byte validation, text layer or Tesseract (eng+hin) OCR, classification, extraction of name/DOB/income/category/percentage/disability/certificate no./issue date, cross-checks, citizen review. Aadhaar numbers are never extracted. |
| Agent | Profile, Discovery, Eligibility, Document, Planning, Browser, Recovery, Verification agents in one LangGraph graph with PostgreSQL checkpoints. |
| Browser | Isolated Playwright contexts, domain allowlist at navigation and request level, SSRF/private-IP blocking, approval-gated uploads and submit. |
| UI | Public pages, auth, onboarding, dashboard, assistant, services, applications, documents, agent control room (SSE), trust center, settings, admin. |

## Success measures (to instrument once live)
Time from discovery to submitted application; % of runs needing recovery; % of runs ending SUBMISSION_UNCERTAIN (target: near zero, never silently "success"); approval turnaround; citizen-reported correctness of extracted fields.

## Legal & policy
Designed with the Digital Personal Data Protection Act, 2023 and its rules in mind (purpose-bound consent, data minimisation, access history, deletion). **No legal compliance is claimed**; a legal review is required before handling real citizen data. Automating any real portal requires that portal's permission/terms review first.
