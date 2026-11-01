/** Container liveness probe (Dockerfile HEALTHCHECK). Does not touch the API. */
export const dynamic = "force-dynamic";

export function GET(): Response {
  return Response.json({ status: "UP" }, { headers: { "Cache-Control": "no-store" } });
}
