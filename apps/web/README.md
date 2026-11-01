# apps/web — CIVIX frontend

Next.js 16 (App Router), React 19, strict TypeScript, Tailwind CSS v4, shadcn-style components on Radix, lucide-react,
TanStack Query v5, Zod, React Hook Form, Motion, Server-Sent Events.

```bash
npm ci
API_PROXY_TARGET=http://localhost:8080 npm run dev   # /api/v1/* is rewritten to the API (same-origin cookies)
npm run typecheck && npm run lint && npm test && npm run build
```

- `app/` routes: `(public)` marketing/legal, `(auth)` login/register, `(focus)` onboarding, `(app)` signed-in shell,
  `services/` (works signed in or out).
- `lib/api/` typed client per endpoint (XSRF header, refresh-on-401, RFC 9457 errors); `lib/sse/` event reducer;
  `hooks/use-task-events.ts` EventSource with reconnect + history backfill.
- `features/<module>/` queries and components; `features/agent/components/control-room.tsx` is the Agent Control Room.
- Design tokens in `app/globals.css`: deep navy primary, teal/cyan accent, off-white background, Inter, optional dark mode.
- `proxy.ts` redirects signed-out visitors away from app routes (UX only — authorization is enforced by the API).
