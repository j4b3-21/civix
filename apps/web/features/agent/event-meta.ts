import type { ExecutionEventDto, ExecutionEventType } from "@/types/api";

type Meta = Record<string, unknown>;

export const metaString = (meta: Meta, key: string): string | null => {
  const v = meta[key];
  return typeof v === "string" && v.length > 0 ? v : null;
};

export const metaNumber = (meta: Meta, key: string): number | null => {
  const v = meta[key];
  return typeof v === "number" && Number.isFinite(v) ? v : null;
};

export const metaBoolean = (meta: Meta, key: string): boolean | null => {
  const v = meta[key];
  return typeof v === "boolean" ? v : null;
};

export const metaStringArray = (meta: Meta, key: string): string[] => {
  const v = meta[key];
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
};

export function lastEventOf(events: readonly ExecutionEventDto[], ...types: ExecutionEventType[]): ExecutionEventDto | undefined {
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const e = events[i];
    if (e && types.includes(e.type)) return e;
  }
  return undefined;
}

/** The most recent portal observation carried by an event (screenshot, URL, title). */
export function latestObservation(events: readonly ExecutionEventDto[]): {
  screenshotId: string | null;
  url: string | null;
  title: string | null;
  at: string | null;
} | null {
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const e = events[i];
    if (!e) continue;
    const screenshotId = metaString(e.metadata, "screenshotId");
    const url = metaString(e.metadata, "url");
    if (screenshotId || url) return { screenshotId, url, title: metaString(e.metadata, "title"), at: e.timestamp };
  }
  return null;
}

export const GATE_EVENTS: ReadonlySet<ExecutionEventType> = new Set(["APPROVAL_REQUIRED", "SUBMISSION_APPROVAL_REQUIRED", "CITIZEN_INPUT_REQUIRED"]);
