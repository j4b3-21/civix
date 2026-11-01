import type { ProfileFieldDto, ProfileFieldType, ProfileValue } from "@/types/api";

export type ParseResult = { ok: true; value: ProfileValue } | { ok: false; message: string };

/** Profile value → the string held by an input control. */
export function toInputString(value: ProfileValue | undefined, type: ProfileFieldType): string {
  if (value === null || value === undefined) return "";
  if (type === "BOOLEAN") return value === true || value === "true" ? "true" : value === false || value === "false" ? "false" : "";
  return String(value);
}

/** Input string → typed profile value (client-side precheck; the server validates again). */
export function parseInputString(raw: string, type: ProfileFieldType, options?: string[]): ParseResult {
  const text = raw.trim();
  if (text === "") return { ok: true, value: null };
  switch (type) {
    case "NUMBER": {
      const n = Number(text.replace(/,/g, ""));
      return Number.isFinite(n) ? { ok: true, value: n } : { ok: false, message: "Enter a number" };
    }
    case "INTEGER": {
      const cleaned = text.replace(/,/g, "");
      return /^-?\d+$/.test(cleaned) ? { ok: true, value: Number.parseInt(cleaned, 10) } : { ok: false, message: "Enter a whole number" };
    }
    case "BOOLEAN":
      return text === "true" ? { ok: true, value: true } : text === "false" ? { ok: true, value: false } : { ok: false, message: "Choose yes or no" };
    case "DATE":
      return /^\d{4}-\d{2}-\d{2}$/.test(text) && !Number.isNaN(Date.parse(text))
        ? { ok: true, value: text }
        : { ok: false, message: "Enter a valid date" };
    case "ENUM":
      return !options || options.includes(text) ? { ok: true, value: text } : { ok: false, message: "Choose one of the options" };
    case "STRING":
      return { ok: true, value: text };
  }
}

export function groupFields<T extends Pick<ProfileFieldDto, "group">>(fields: readonly T[]): [string, T[]][] {
  const groups = new Map<string, T[]>();
  for (const field of fields) {
    const list = groups.get(field.group);
    if (list) list.push(field);
    else groups.set(field.group, [field]);
  }
  return [...groups.entries()];
}

export function groupLabel(group: string): string {
  const text = group.replace(/[_-]+/g, " ").trim();
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
}

/**
 * Maps server validation errors for PUT /profile onto field keys. Spring may report either the
 * key itself or the request path (`fields[2].value`), which indexes into the submitted list.
 */
export function mapProfileFieldErrors(
  fieldErrors: { field: string; message: string }[],
  submittedKeys: readonly string[],
): Record<string, string> {
  const out: Record<string, string> = {};
  for (const { field, message } of fieldErrors) {
    const indexed = /fields\[(\d+)\]/.exec(field);
    const key = indexed ? submittedKeys[Number(indexed[1])] : submittedKeys.find((k) => k === field || field.endsWith(`.${k}`));
    if (key) out[key] = message.charAt(0).toUpperCase() + message.slice(1);
  }
  return out;
}
