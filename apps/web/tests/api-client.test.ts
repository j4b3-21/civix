import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, setUnauthenticatedHandler } from "@/lib/api/client";
import { API, problem, server } from "./msw/server";

describe("api client", () => {
  it("echoes the XSRF-TOKEN cookie as X-XSRF-TOKEN on mutating requests only", async () => {
    document.cookie = "XSRF-TOKEN=abc%20123; path=/";
    const seen: Record<string, string | null> = {};
    server.use(
      http.get(`${API}/profile`, ({ request }) => {
        seen.get = request.headers.get("X-XSRF-TOKEN");
        return HttpResponse.json({ ok: true });
      }),
      http.put(`${API}/profile`, ({ request }) => {
        seen.put = request.headers.get("X-XSRF-TOKEN");
        return HttpResponse.json({ ok: true });
      }),
    );
    await apiFetch("/profile");
    await apiFetch("/profile", { method: "PUT", body: { fields: [] } });
    expect(seen.get).toBeNull();
    expect(seen.put).toBe("abc 123");
  });

  it("sends credentials and JSON bodies", async () => {
    let body: unknown;
    let contentType: string | null = null;
    server.use(
      http.post(`${API}/applications`, async ({ request }) => {
        body = await request.json();
        contentType = request.headers.get("Content-Type");
        return HttpResponse.json({ id: "a1" }, { status: 201 });
      }),
    );
    await expect(apiFetch("/applications", { method: "POST", body: { serviceId: "s1" } })).resolves.toEqual({ id: "a1" });
    expect(body).toEqual({ serviceId: "s1" });
    expect(contentType).toBe("application/json");
  });

  it("parses RFC 9457 problem+json into ApiError", async () => {
    server.use(
      http.post(`${API}/approvals/ap1/decision`, () =>
        problem(409, "APPROVAL_DATA_CHANGED", {
          detail: "Data version mismatch",
          correlationId: "corr-1",
          errors: [{ field: "dataVersion", message: "must match" }],
        }),
      ),
    );
    const error = await apiFetch("/approvals/ap1/decision", { method: "POST", body: {} }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe("APPROVAL_DATA_CHANGED");
    expect(apiError.detail).toBe("Data version mismatch");
    expect(apiError.correlationId).toBe("corr-1");
    expect(apiError.fieldErrors).toEqual([{ field: "dataVersion", message: "must match" }]);
  });

  it("falls back to HTTP_<status> and the X-Request-Id header for non-problem errors", async () => {
    server.use(http.get(`${API}/dashboard`, () => new HttpResponse("upstream down", { status: 502, headers: { "X-Request-Id": "req-9" } })));
    const error = (await apiFetch("/dashboard").catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe("HTTP_502");
    expect(error.correlationId).toBe("req-9");
  });

  it("reads Retry-After on 429", async () => {
    server.use(
      http.post(
        `${API}/services/discover`,
        () => new HttpResponse(JSON.stringify({ status: 429, code: "RATE_LIMITED" }), { status: 429, headers: { "Content-Type": "application/problem+json", "Retry-After": "30" } }),
      ),
    );
    const error = (await apiFetch("/services/discover", { method: "POST", body: {} }).catch((e: unknown) => e)) as ApiError;
    expect(error.retryAfterSeconds).toBe(30);
  });

  it("refreshes once on 401 and retries the original request", async () => {
    let calls = 0;
    let refreshes = 0;
    server.use(
      http.get(`${API}/dashboard`, () => {
        calls += 1;
        return calls === 1 ? problem(401, "UNAUTHORIZED") : HttpResponse.json({ ok: true });
      }),
      http.post(`${API}/auth/refresh`, () => {
        refreshes += 1;
        return HttpResponse.json({ id: "u1", email: "a@b.in", fullName: "A", roles: ["CITIZEN"], createdAt: "2026-01-01T00:00:00Z" });
      }),
    );
    await expect(apiFetch("/dashboard")).resolves.toEqual({ ok: true });
    expect(calls).toBe(2);
    expect(refreshes).toBe(1);
  });

  it("shares one refresh between concurrent 401s", async () => {
    let refreshes = 0;
    const attempts = new Map<string, number>();
    const handler = (name: string) => () => {
      const n = (attempts.get(name) ?? 0) + 1;
      attempts.set(name, n);
      return n === 1 ? problem(401, "UNAUTHORIZED") : HttpResponse.json({ name });
    };
    server.use(
      http.get(`${API}/profile`, handler("profile")),
      http.get(`${API}/documents`, handler("documents")),
      http.post(`${API}/auth/refresh`, async () => {
        refreshes += 1;
        await new Promise((r) => setTimeout(r, 20));
        return HttpResponse.json({});
      }),
    );
    const [a, b] = await Promise.all([apiFetch("/profile"), apiFetch("/documents")]);
    expect(a).toEqual({ name: "profile" });
    expect(b).toEqual({ name: "documents" });
    expect(refreshes).toBe(1);
  });

  it("redirects to login with the current path when refresh fails", async () => {
    window.history.replaceState(null, "", "/applications?status=DRAFT");
    const onUnauthenticated = vi.fn();
    setUnauthenticatedHandler(onUnauthenticated);
    server.use(
      http.get(`${API}/applications`, () => problem(401, "UNAUTHORIZED")),
      http.post(`${API}/auth/refresh`, () => problem(401, "INVALID_REFRESH_TOKEN")),
    );
    const error = (await apiFetch("/applications").catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(401);
    expect(onUnauthenticated).toHaveBeenCalledWith("/applications?status=DRAFT");
    window.history.replaceState(null, "", "/");
  });

  it("does not redirect in throw mode, and never refreshes for auth endpoints", async () => {
    const onUnauthenticated = vi.fn();
    setUnauthenticatedHandler(onUnauthenticated);
    let refreshes = 0;
    server.use(
      http.get(`${API}/services`, () => problem(401, "UNAUTHORIZED")),
      http.post(`${API}/auth/login`, () => problem(401, "INVALID_CREDENTIALS")),
      http.post(`${API}/auth/refresh`, () => {
        refreshes += 1;
        return problem(401, "INVALID_REFRESH_TOKEN");
      }),
    );
    await expect(apiFetch("/services", { auth: "throw" })).rejects.toMatchObject({ status: 401 });
    expect(refreshes).toBe(1);
    await expect(apiFetch("/auth/login", { method: "POST", body: {} })).rejects.toMatchObject({ code: "INVALID_CREDENTIALS" });
    expect(refreshes).toBe(1);
    expect(onUnauthenticated).not.toHaveBeenCalled();
  });

  it("returns undefined for 204 and maps network failures to NETWORK_ERROR", async () => {
    server.use(
      http.delete(`${API}/documents/d1`, () => new HttpResponse(null, { status: 204 })),
      http.get(`${API}/profile/consents`, () => HttpResponse.error()),
    );
    await expect(apiFetch("/documents/d1", { method: "DELETE" })).resolves.toBeUndefined();
    await expect(apiFetch("/profile/consents")).rejects.toMatchObject({ code: "NETWORK_ERROR", status: 0 });
  });

  it("serialises query parameters and skips empty ones", async () => {
    let url = "";
    server.use(
      http.get(`${API}/services`, ({ request }) => {
        url = request.url;
        return HttpResponse.json({ items: [] });
      }),
    );
    await apiFetch("/services", { query: { q: "scholarship", category: undefined, state: "", page: 0 } });
    const parsed = new URL(url);
    expect(parsed.searchParams.get("q")).toBe("scholarship");
    expect(parsed.searchParams.get("page")).toBe("0");
    expect(parsed.searchParams.has("category")).toBe(false);
    expect(parsed.searchParams.has("state")).toBe(false);
  });
});
