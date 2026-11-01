const LOCALE = "en-IN";
const TIME_ZONE = "Asia/Kolkata";

function toDate(value: string | number | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === "") return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDate(value: string | null | undefined): string {
  const date = toDate(value);
  if (!date) return "Not available";
  // Plain calendar dates (YYYY-MM-DD) must not shift across time zones.
  const isCalendarDate = typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value);
  return new Intl.DateTimeFormat(LOCALE, {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: isCalendarDate ? "UTC" : TIME_ZONE,
  }).format(date);
}

export function formatDateTime(value: string | null | undefined): string {
  const date = toDate(value);
  if (!date) return "Not available";
  return new Intl.DateTimeFormat(LOCALE, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone: TIME_ZONE,
  }).format(date);
}

export function formatTime(value: string | null | undefined): string {
  const date = toDate(value);
  if (!date) return "";
  return new Intl.DateTimeFormat(LOCALE, { hour: "numeric", minute: "2-digit", second: "2-digit", timeZone: TIME_ZONE }).format(date);
}

const RELATIVE = new Intl.RelativeTimeFormat(LOCALE, { numeric: "auto" });

export function formatRelative(value: string | null | undefined, now: number = Date.now()): string {
  const date = toDate(value);
  if (!date) return "Not available";
  const seconds = Math.round((date.getTime() - now) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) return "just now";
  if (abs < 3600) return RELATIVE.format(Math.round(seconds / 60), "minute");
  if (abs < 86_400) return RELATIVE.format(Math.round(seconds / 3600), "hour");
  if (abs < 7 * 86_400) return RELATIVE.format(Math.round(seconds / 86_400), "day");
  return formatDate(value);
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "Unknown size";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatPercent(fraction: number | null | undefined): string {
  if (fraction === null || fraction === undefined || Number.isNaN(fraction)) return "Not available";
  return `${Math.round(fraction * 100)}%`;
}

/** Render any profile/rule value for display. */
export function formatValue(value: unknown, unit?: string | null): string {
  if (value === null || value === undefined || value === "") return "Not provided";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (Array.isArray(value)) return value.map((v) => formatValue(v)).join(", ");
  if (typeof value === "number") {
    const formatted = unit === "INR" || unit === "₹" ? `₹${value.toLocaleString(LOCALE)}` : value.toLocaleString(LOCALE);
    return unit && unit !== "INR" && unit !== "₹" ? `${formatted} ${unit}` : formatted;
  }
  if (typeof value === "object") return JSON.stringify(value);
  return unit && unit !== "INR" && unit !== "₹" ? `${String(value)} ${unit}` : String(value);
}

/** "Seconds remaining" → "12:05" / "1:02:03". */
export function formatCountdown(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const pad = (n: number) => n.toString().padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
}

export function hostname(url: string | null | undefined): string {
  if (!url) return "";
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

/** Only allow same-origin relative paths as post-login destinations. */
export function safeNextPath(next: string | null | undefined, fallback = "/dashboard"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return fallback;
  if (next.startsWith("/login") || next.startsWith("/register")) return fallback;
  return next;
}
