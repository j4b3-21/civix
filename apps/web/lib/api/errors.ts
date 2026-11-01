import { isApiError } from "@/lib/api/client";

const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: "CIVIX could not reach the server. Check your connection and try again.",
  INVALID_CREDENTIALS: "The email or password is incorrect.",
  ACCOUNT_LOCKED: "This account is locked for 15 minutes after repeated failed sign-ins.",
  CONSENT_REQUIRED: "This needs a permission you have not granted yet.",
  APPLICATION_EXISTS: "You already have an open application for this service.",
  AUTOMATION_UNAVAILABLE: "CIVIX cannot run this service on the portal. Apply on the official portal instead.",
  APPROVAL_EXPIRED: "This approval request expired before a decision was recorded.",
  APPROVAL_NOT_PENDING: "This approval was already decided or withdrawn.",
  APPROVAL_DATA_CHANGED: "Your information changed after this request was prepared, so it was withdrawn.",
};

const STATUS_MESSAGES: Record<number, string> = {
  400: "Some of the information sent was not valid.",
  401: "Your session ended. Sign in again to continue.",
  403: "You do not have permission to do that.",
  404: "This item was not found, or it does not belong to your account.",
  409: "This conflicts with the current state. Refresh and try again.",
  410: "This request is no longer available.",
  413: "The file is too large.",
  415: "This file type is not supported.",
  422: "The request could not be processed.",
  429: "Too many requests. Wait a moment and try again.",
};

/** Human-readable message for any thrown value. */
export function errorMessage(error: unknown, fallback = "Something went wrong. Try again."): string {
  if (isApiError(error)) {
    if (error.code === "HTTP_429" || error.status === 429) {
      return error.retryAfterSeconds
        ? `Too many requests. Try again in ${error.retryAfterSeconds} seconds.`
        : (STATUS_MESSAGES[429] ?? fallback);
    }
    return MESSAGES[error.code] ?? error.detail ?? STATUS_MESSAGES[error.status] ?? (error.status >= 500 ? "The server had a problem. Try again shortly." : fallback);
  }
  if (error instanceof Error && error.message) return fallback;
  return fallback;
}

/** Correlation id to quote to support, when present. */
export function errorReference(error: unknown): string | undefined {
  return isApiError(error) ? error.correlationId : undefined;
}
