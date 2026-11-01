import { setupServer } from "msw/node";

/** Shared msw server; each test registers its own handlers with server.use(...). */
export const server = setupServer();

export const API = "http://localhost:3000/api/v1";

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return new Response(JSON.stringify({ type: `https://civix.dev/problems/${code}`, title: code, status, code, ...extra }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });
}
