import { NextResponse, type NextRequest } from "next/server";

/**
 * UX-only gate: send visitors without any session cookie to the sign-in page.
 * Real authorization is enforced by the API on every request.
 *
 * Note: `civix_rt` is scoped to Path=/api/v1/auth, so browsers usually only send `civix_at`
 * here. When the short-lived access cookie has expired, /login silently calls
 * POST /api/v1/auth/refresh (which does receive civix_rt) and returns the user to `next`.
 */
const SESSION_COOKIES = ["civix_at", "civix_rt"] as const;

export function proxy(request: NextRequest) {
  const hasSession = SESSION_COOKIES.some((name) => Boolean(request.cookies.get(name)?.value));
  if (hasSession) return NextResponse.next();
  const url = request.nextUrl.clone();
  const next = `${request.nextUrl.pathname}${request.nextUrl.search}`;
  url.pathname = "/login";
  url.search = `?next=${encodeURIComponent(next)}`;
  return NextResponse.redirect(url);
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/assistant/:path*",
    "/applications/:path*",
    "/documents/:path*",
    "/agent/:path*",
    "/trust-center/:path*",
    "/settings/:path*",
    "/admin/:path*",
    "/onboarding/:path*",
  ],
};
