# CIVIX deployment

## Local (single machine)
```bash
make env                         # .env with generated secrets (never commit it)
docker compose up -d --build     # or: make up
# with the local test portal (authorized test environment) for the end-to-end demo:
docker compose --profile test up -d --build
open http://localhost:3000
```
Requirements: Docker 24+ with Compose v2, ~6 GB RAM. First build downloads Maven, npm and pip dependencies.

## Production topology (recommended)
| Component | Target |
|---|---|
| web | Vercel (or any Node host / the same Docker image) |
| api, agent, browser-worker | Containers on a private network (ECS/Fargate, Kubernetes, or a VM with compose) |
| PostgreSQL | Managed PostgreSQL 16 with pgvector, encryption at rest, PITR backups |
| Object storage | S3 (SSE-S3 or SSE-KMS) — set `S3_ENDPOINT` empty, `S3_FORCE_PATH_STYLE=false` |
| Redis | Managed Redis with AUTH + TLS |
| Edge | nginx profile (`infrastructure/nginx/civix.conf`) or a cloud load balancer with TLS |

### Vercel (frontend)
- Project root: `apps/web`. Build: `npm run build`. Output: Next.js.
- Env: `API_PROXY_TARGET=https://api.internal.example.gov` (the rewrite target must be reachable from Vercel; keep the API
  private behind an authenticated ingress or use a private connection). Cookies are set by the API on the web origin via the
  same-origin rewrite, so the browser never talks to the API host directly.

## Free-tier deployment

The repository includes `vercel.json` and `render.yaml` as deployment starting points.
This stack cannot run completely on free tiers with all features enabled: Chromium needs
more memory than typical free containers, and free hosted databases may pause or expire.

Recommended hobby topology:

| Component | Provider |
|---|---|
| Next.js web | Vercel Hobby, project root `apps/web` |
| Spring API + FastAPI agent | Render Docker services from `render.yaml` |
| PostgreSQL 16 | Supabase free database; use its pooled connection URL |
| Redis | Upstash Redis free tier, or disable rate limiting only for a local demo |
| S3-compatible storage | Cloudflare R2 free tier |
| Browser worker | Paid Render service or a small Docker VM; not free-tier safe |

1. Push this repository to GitHub. It currently has no commit or remote, so provider
   dashboards cannot import it yet.
2. Create the Supabase, Upstash, and R2 resources and copy their connection details.
3. In Render, choose **New → Blueprint**, select the repository, and apply `render.yaml`.
   Set every `sync: false` value; generate secrets with `openssl rand -base64 48`.
4. Set `CIVIX_PUBLIC_URL` to the final Vercel URL and set Vercel's
   `API_PROXY_TARGET` to the public API/edge URL. The target must be reachable by Vercel.
5. Deploy `apps/web` in Vercel with Node 22 and `API_PROXY_TARGET` configured before build.
6. Do not set `CIVIX_ENABLE_TEST_SERVICES=true` or `automationStatus: ACTIVE` in a public deployment.

The free-tier deployment is suitable for service discovery, eligibility, profile, and
document flows. Real browser submissions require a paid/VM browser worker, portal-owner
authorization, and a supervised verification run.

### Environment variables
See `.env.example` — every variable is documented there. Mandatory in production: all `*_TOKEN`, `*_SECRET`, `*_PASSWORD`,
`JWT_SECRET`, `APPROVAL_SIGNING_SECRET`, `MINIO_KMS_SECRET_KEY` (or S3 KMS), `COOKIE_SECURE=true`,
`CIVIX_ENABLE_TEST_SERVICES=false`, `BROWSER_PRIVATE_HOST_ALLOWLIST=` (empty), `CIVIX_OPENAPI_ENABLED=false`.

### Database
The init script must run once as the superuser (roles + schemas). Then the API runs Flyway on startup (or `make migrate`).

## Production security checklist
- [ ] TLS everywhere at the edge; HSTS enabled; `COOKIE_SECURE=true`
- [ ] Distinct random values for API_INTERNAL_TOKEN, AGENT_INTERNAL_TOKEN, BROWSER_WORKER_TOKEN, APPROVAL_SIGNING_SECRET, JWT_SECRET
- [ ] Secrets in a secret manager, not in `.env` files on disk
- [ ] Test portal not deployed; `CIVIX_ENABLE_TEST_SERVICES=false`
- [ ] Only web/edge publicly reachable; `/internal/**`, `/actuator/**` blocked at the edge
- [ ] Egress proxy for browser-worker enforcing the portal allowlist (defence in depth vs DNS rebinding)
- [ ] Object storage encryption + lifecycle rules (screenshots 90 days)
- [ ] DB backups + restore drill; Redis AUTH/TLS
- [ ] Upload malware scanning
- [ ] Legal review (DPDP Act notices, retention, grievance officer) before onboarding real citizens
- [ ] Written authorization from each portal owner before setting any real service to `ACTIVE`

## Activating a real government workflow
1. Obtain permission (or use the portal's official API/sandbox) and confirm the terms allow assisted submission.
2. Add `workflow:` to the service YAML (steps, labels, receipt pattern) and an adapter only if needed.
3. Run it against the real portal under supervision; record evidence; set `verificationStatus: WORKFLOW_VERIFIED`.
4. Only then set `automationStatus: ACTIVE` (the registry validator refuses `ACTIVE` without `WORKFLOW_VERIFIED`).
