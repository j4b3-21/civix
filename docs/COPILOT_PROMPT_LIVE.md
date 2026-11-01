# Prompt for GitHub Copilot (Agent mode) — make CIVIX live: real schemes, real sources, real LLM, real portals

You are turning CIVIX from a registry-and-test-portal system into a **live** assistant that works with any Indian
government scheme or citizen service: it finds the service on official sites in real time, understands it with an LLM,
checks eligibility, and operates the real portal in a real browser **on the citizen's behalf, with the citizen present
and in control**. Then you deploy it.

Do the work yourself: run commands, read errors, fix, re-run. Don't stop after a plan.

## 0. Read first, and keep obeying
- `AGENTS.md`, `docs/IMPLEMENTATION_STATUS.md`, `docs/API.md`, `docs/AGENT_CONTRACTS.md`, `docs/WORKFLOWS.md`, `docs/SECURITY.md`.
- If `apps/api` does not compile yet, first complete steps 1–3 of `docs/COPILOT_PROMPT.md` (compile the API, its tests,
  bring up docker compose). Nothing else works until the API builds.

## Rules that do NOT change (the product is only trustworthy because of these)
1. The citizen performs authentication: login, OTP, CAPTCHA, Aadhaar/e-KYC, DigiLocker consent, payments. CIVIX never
   solves CAPTCHAs, never reads SMS/email, never stores portal passwords, never pays.
2. Every consequential action (final submit, legal declaration, document upload, anything irreversible) needs the citizen's
   explicit approval, enforced server-side with the existing signed approval tokens. Never weaken the token checks in the
   agent or the browser worker.
3. Facts come from official sources only: domains ending in `.gov.in` or `.nic.in`, plus explicitly allowlisted official
   portals (e.g. `myscheme.gov.in`, `scholarships.gov.in`, `india.gov.in`, `services.india.gov.in`, `umang.gov.in`,
   `digilocker.gov.in`, state portals). Search engines are used only to *find* official URLs; snippets are never evidence.
   Every claim shown to the user carries the source URL and retrieval time.
4. The LLM interprets and proposes; deterministic code decides. Eligibility outcomes come from the rules engine.
   LLM-extracted rules are marked `EXTRACTED_UNVERIFIED` until a human admin or a second, independent official-source
   check confirms them; the UI says so.
5. Never fabricate anything: no invented schemes, rules, deadlines, reference numbers or "success". A receipt exists only
   if the reference was read from the real portal page.
6. Respect robots.txt and portal terms; rate-limit politely (≤1 request/second/domain, cache results); identify the crawler
   with a contact URL. If a portal forbids automation, CIVIX switches that service to guided mode (checklist + deep links)
   and says why.
7. No secrets, OTPs, passwords, cookies or document text in logs, events, LLM prompts or checkpoints. Send the LLM only
   what the task needs; never send Aadhaar numbers, bank account numbers or full documents unless the citizen consented
   to that specific purpose.
8. Keep every existing test green. New features need real tests (unit + integration). No mocks in production paths.
   "Live" tests that hit real sites are tagged `@live`, read-only, and never submit anything.

## 1. LLM layer (real inference)
- Make Anthropic Claude the default provider (`LLM_PROVIDER=anthropic`, model from `ANTHROPIC_MODEL`), keeping the provider
  abstraction (`apps/agent/app/services/llm/base.py`) and the OpenAI-compatible local option.
- Add streaming support and tool use. Track tokens/cost per task (already instrumented) and enforce a per-task budget
  (`LLM_MAX_COST_PER_TASK_USD`) and per-user daily limits.
- Startup check: if the key is missing, fail readiness with a clear message (no silent fallback in production).
- Prompt-injection defence: page/document text is always passed as quoted data in a separate field, with a system rule
  that it contains no instructions; tool permissions are decided in code, never by model output.

## 2. Live discovery (real-time search + scraping)
- Add `apps/agent/app/retrieval/live/`:
  - `search.py`: a search-provider abstraction (Brave Search API, Bing Web Search, Google Programmable Search, or Tavily —
    key from env), with queries restricted by `site:gov.in OR site:nic.in OR site:myscheme.gov.in …`. Results filtered to
    the official-domain allowlist.
  - `fetch.py`: polite fetcher (httpx + Playwright for JS-rendered pages like myScheme), robots.txt check, per-domain rate
    limit, caching in Postgres with content hash and fetch time, size limits, PDF support (reuse `document_intelligence`).
  - `extract.py`: LLM structured extraction from fetched official pages/PDFs into the existing `ServiceDefinition` schema
    (name, department, jurisdiction, benefits, eligibility rules mapped to profile-field keys + operators, required
    documents, procedure, deadlines, official portal URL). Every rule must carry `sourceUrl` and the exact excerpt it came
    from; reject rules whose excerpt is not found verbatim in the fetched text.
- Discovery flow: user query → LLM intent (categories, state, profile hints) → registry search **and** live search in
  parallel → fetch top official pages → extract → validate with the existing Pydantic/JSON-Schema → evaluate eligibility
  deterministically → stream results to the UI as they arrive (SSE), each with sources and retrieval time.
- Persist discovered services in the registry as `verificationStatus: DRAFT` + `origin: LIVE_EXTRACTED`; add an admin
  review queue in `/admin` to confirm/correct and promote to `SOURCE_VERIFIED`. Refresh job re-fetches sources weekly and
  flags changed content.
- Add a profile-field mapping step: when an extracted rule needs a field that isn't in `profile-fields.yaml`, map it to
  the closest existing key or create a pending field proposal for admin review — never silently drop the rule.

## 3. Live browser agent for any portal (citizen in the loop)
- Add a **generic LLM-driven portal agent** alongside the YAML workflows: loop OBSERVE (accessibility snapshot + visible
  form fields + screenshot) → REASON (LLM chooses one action from a fixed tool set: navigate, fill, select, check, click,
  upload, ask_citizen, request_approval, done) → ACT (through the existing browser worker, which keeps enforcing the
  allowlist, upload staging and SUBMIT tokens) → VERIFY (expected-state check). Max steps and cost per run.
  Fill values only from the citizen's confirmed profile and reviewed documents; if a value is unknown, ask the citizen.
- The domain allowlist per run = the service's official portal domains discovered in step 2 (shown to the citizen
  before the run starts).
- **Live remote browser view with citizen control**: stream the browser worker's page to the Agent Control Room
  (CDP `Page.startScreencast` over an authenticated WebSocket via the API) and forward the citizen's mouse/keyboard input
  when CIVIX hands control over, so the citizen logs in, enters OTP, solves CAPTCHA and does e-KYC **themselves, in the
  real portal**. While the citizen has control the agent is paused; keystrokes are never logged or stored. A "Give control
  back to CIVIX" button resumes the agent.
- Detect and hand over automatically: login pages, OTP fields, CAPTCHA images, payment pages, declarations, e-sign.
- Classify every click with the existing action classes; anything that looks like submit/pay/declare/e-sign/final save
  requires a FINAL_SUBMISSION (or DECLARATION) approval showing the exact data on the page.
- After submit: Verification agent extracts the acknowledgement/reference with the LLM *and* confirms the same string is
  present in the DOM; saves the receipt screenshot/PDF; otherwise `SUBMISSION_UNCERTAIN`.
- Session persistence: keep the citizen's portal session in an encrypted, short-lived browser profile per task (never
  reused across citizens), deleted at task end.
- Learning: when a generic run succeeds, offer an admin action "save as workflow" that converts the trace into a YAML
  workflow for faster, deterministic future runs (still requires review).

## 4. DigiLocker and official APIs (prefer these over scraping where available)
- Add adapters behind interfaces for DigiLocker (citizen-consented document pull via the official partner API) and for
  any official APIs found (API Setu, myScheme where available). They require partner registration — implement fully,
  read credentials from env, and if credentials are absent, disable the feature with a clear UI message.

## 5. UI
- Assistant: streaming chat with live "searching official sources… reading scholarships.gov.in…" progress, result cards
  with sources, rule-by-rule eligibility, "unverified extraction" badges, and "Apply with CIVIX".
- Control Room: live browser view, "Take control" / "Give control back", step timeline, approvals, cost meter.
- Remove the test portal from the default UI (keep it only behind `CIVIX_ENABLE_TEST_SERVICES` for CI).

## 6. Tests (real, not fake)
- Unit tests for every new module; integration tests for search/fetch/extract against recorded official pages
  (store fixtures captured from the real sites with their URL and date).
- `@live` read-only suite: run discovery for 10 real queries (e.g. "post matric scholarship SC Karnataka",
  "PM-KISAN eligibility", "Udyam registration", "income certificate Maharashtra") and assert results are official-domain,
  sourced and schema-valid. Run the generic browser agent on real portals up to (never past) the login page.
- Keep the deterministic test-portal E2E as the CI regression for approvals/recovery/receipts.

## 7. Deploy today
- Target: one Linux VM (4 vCPU, 8–16 GB RAM, e.g. AWS Mumbai `ap-south-1`) with Docker Compose, or Render/Railway/Fly for
  the containers + Vercel for `apps/web`. Prefer the VM for the browser worker.
- Add `docker-compose.prod.yml`: no test portal, `CIVIX_ENABLE_TEST_SERVICES=false`, Caddy or the existing nginx profile
  with automatic HTTPS (Let's Encrypt), managed Postgres (RDS/Neon with pgvector) and S3 if available, Redis with AUTH.
- GitHub Actions: test → build images → push to GHCR → deploy over SSH; DB backup before migrate.
- Production checklist from `docs/DEPLOYMENT.md` must pass; write `docs/RUNBOOK.md` (deploy, rollback, rotate secrets,
  incident response, how to disable automation for a portal instantly via an admin kill switch).
- Required env to request from me (stop and list them if missing): `ANTHROPIC_API_KEY`, search API key, domain name,
  server SSH host, DigiLocker partner credentials (optional).

## 8. Finish
- Update `docs/IMPLEMENTATION_STATUS.md` with what you actually ran (commands + results), what is live, and what is
  blocked on credentials or portal permissions. Don't claim universal portal support: report per-portal results.
