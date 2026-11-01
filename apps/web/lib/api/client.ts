/**
 * Same-origin fetch wrapper for the CIVIX API (`/api/v1`).
 *
 * - cookies are sent (`credentials: "include"`); auth tokens are httpOnly and never touched here
 * - mutating requests echo the readable `XSRF-TOKEN` cookie as `X-XSRF-TOKEN`
 * - RFC 9457 problem+json is parsed into {@link ApiError}
 * - a 401 triggers one `POST /auth/refresh` (single-flight) and one retry; if that fails the
 *   user is sent to `/login?next=<current path>` (unless the caller opted out)
 */
import { problemSchema } from "@/lib/schemas/api";
import type { ProblemFieldError } from "@/types/api";

export const API_BASE = "/api/v1";
const XSRF_COOKIE = "XSRF-TOKEN";
const XSRF_HEADER = "X-XSRF-TOKEN";
const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);
/** Endpoints that must never trigger the refresh-and-retry dance. */
const NO_REFRESH_PATHS = ["/auth/login", "/auth/register", "/auth/refresh", "/auth/logout"];

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string | undefined;
  readonly detail: string | undefined;
  readonly fieldErrors: ProblemFieldError[];
  readonly correlationId: string | undefined;
  readonly retryAfterSeconds: number | undefined;

  constructor(init: {
    status: number;
    code: string;
    title?: string | undefined;
    detail?: string | undefined;
    fieldErrors?: ProblemFieldError[] | undefined;
    correlationId?: string | undefined;
    retryAfterSeconds?: number | undefined;
  }) {
    super(init.detail ?? init.title ?? init.code);
    this.name = "ApiError";
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.fieldErrors = init.fieldErrors ?? [];
    this.correlationId = init.correlationId;
    this.retryAfterSeconds = init.retryAfterSeconds;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function hasErrorCode(error: unknown, ...codes: string[]): boolean {
  return isApiError(error) && codes.includes(error.code);
}

/**
 * - `redirect` (default): on 401 refresh once, retry, then redirect to login
 * - `throw`: on 401 refresh once, retry, then throw (public pages that work signed-out)
 * - `none`: never refresh (auth endpoints)
 */
export type AuthMode = "redirect" | "throw" | "none";

export type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON body (serialised) or FormData (sent as multipart). */
  body?: unknown;
  query?: Record<string, QueryValue>;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  auth?: AuthMode;
}

type UnauthenticatedHandler = (nextPath: string) => void;

const defaultUnauthenticatedHandler: UnauthenticatedHandler = (nextPath) => {
  if (typeof window === "undefined") return;
  if (window.location.pathname.startsWith("/login")) return;
  // A full navigation (not router.push) drops all in-memory state of the expired session.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`/login?next=${encodeURIComponent(nextPath)}`);
};

let unauthenticatedHandler: UnauthenticatedHandler = defaultUnauthenticatedHandler;
let refreshInFlight: Promise<boolean> | null = null;

/** Test seam: replace what happens when the session cannot be recovered. */
export function setUnauthenticatedHandler(handler: UnauthenticatedHandler | null): void {
  unauthenticatedHandler = handler ?? defaultUnauthenticatedHandler;
}

export function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const prefix = `${name}=`;
  for (const part of document.cookie.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.slice(prefix.length));
    }
  }
  return undefined;
}

function origin(): string {
  return typeof window !== "undefined" ? window.location.origin : "http://localhost";
}

export function apiUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(`${API_BASE}${path.startsWith("/") ? path : `/${path}`}`, origin());
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === "") continue;
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

/** Relative same-origin path (for EventSource / links). */
export function apiPath(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(apiUrl(path, query));
  return `${url.pathname}${url.search}`;
}

function currentLocation(): string {
  if (typeof window === "undefined") return "/";
  return `${window.location.pathname}${window.location.search}`;
}

async function buildError(response: Response): Promise<ApiError> {
  const correlationHeader = response.headers.get("X-Request-Id") ?? undefined;
  const retryAfterHeader = response.headers.get("Retry-After");
  const retryAfterSeconds = retryAfterHeader && /^\d+$/.test(retryAfterHeader) ? Number(retryAfterHeader) : undefined;
  const contentType = response.headers.get("Content-Type") ?? "";
  let problem: ReturnType<typeof problemSchema.safeParse>["data"];
  if (contentType.includes("json")) {
    try {
      const parsed = problemSchema.safeParse(await response.json());
      if (parsed.success) problem = parsed.data;
    } catch {
      // body was not valid JSON: fall through to a status-based error
    }
  }
  return new ApiError({
    status: response.status,
    code: problem?.code ?? `HTTP_${response.status}`,
    title: problem?.title,
    detail: problem?.detail,
    fieldErrors: problem?.errors?.map((e) => ({ field: e.field, message: e.message })),
    correlationId: problem?.correlationId ?? correlationHeader,
    retryAfterSeconds,
  });
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json, application/problem+json;q=0.9, */*;q=0.8");
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body; // the browser sets the multipart boundary
  } else if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(options.body);
  }
  if (MUTATING.has(method)) {
    const xsrf = readCookie(XSRF_COOKIE);
    if (xsrf) headers.set(XSRF_HEADER, xsrf);
  }
  try {
    return await fetch(apiUrl(path, options.query), {
      method,
      headers,
      body,
      credentials: "include",
      cache: "no-store",
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new ApiError({
      status: 0,
      code: "NETWORK_ERROR",
      detail: "CIVIX could not reach the server. Check your connection and try again.",
    });
  }
}

/** Single-flight refresh: concurrent 401s share one `POST /auth/refresh`. */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = send("/auth/refresh", { method: "POST" })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

async function request(path: string, options: RequestOptions = {}): Promise<Response> {
  const auth: AuthMode = options.auth ?? (NO_REFRESH_PATHS.some((p) => path.startsWith(p)) ? "none" : "redirect");
  const xsrfBefore = readCookie(XSRF_COOKIE);
  let response = await send(path, options);

  if (response.status === 401 && auth !== "none") {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await send(path, options);
    }
    if (response.status === 401) {
      const error = await buildError(response);
      if (auth === "redirect") unauthenticatedHandler(currentLocation());
      throw error;
    }
  }

  if (response.status === 403 && MUTATING.has(options.method ?? "GET")) {
    // The CSRF cookie may have been (re)issued by this very response: retry once with it.
    const xsrfAfter = readCookie(XSRF_COOKIE);
    if (xsrfAfter && xsrfAfter !== xsrfBefore) {
      const clone = response.clone();
      const error = await buildError(clone);
      if (/CSRF/i.test(error.code)) {
        response = await send(path, options);
      }
    }
  }

  if (!response.ok) throw await buildError(response);
  return response;
}

/** JSON request. Empty bodies (204/202) resolve to `undefined`. */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await request(path, options);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("json")) return text as T;
  return JSON.parse(text) as T;
}

/** Binary request (screenshots, receipt artifacts, document downloads). */
export async function apiBlob(path: string, options: RequestOptions = {}): Promise<{ blob: Blob; fileName: string | undefined }> {
  const response = await request(path, options);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const fileName = match?.[1] ? decodeURIComponent(match[1]) : undefined;
  return { blob: await response.blob(), fileName };
}
