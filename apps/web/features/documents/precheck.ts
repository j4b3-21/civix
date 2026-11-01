/**
 * Client-side upload precheck (docs/API.md: PDF/PNG/JPEG, ≤ 10 MB).
 * This is a courtesy to catch obvious mistakes before uploading; the server checks magic bytes
 * and is authoritative.
 */
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
export const ACCEPTED_MIME_TYPES = ["application/pdf", "image/png", "image/jpeg"] as const;
export const ACCEPT_ATTRIBUTE = ".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg";
const EXTENSIONS: Record<string, string> = { pdf: "application/pdf", png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg" };

export type PrecheckResult = { ok: true } | { ok: false; reason: "EMPTY" | "TOO_LARGE" | "UNSUPPORTED_TYPE"; message: string };

export function precheckFile(file: Pick<File, "name" | "size" | "type">): PrecheckResult {
  if (file.size === 0) return { ok: false, reason: "EMPTY", message: "This file is empty." };
  if (file.size > MAX_UPLOAD_BYTES) {
    return { ok: false, reason: "TOO_LARGE", message: "This file is larger than 10 MB. Upload a smaller scan or a compressed PDF." };
  }
  const extension = file.name.split(".").at(-1)?.toLowerCase() ?? "";
  const byExtension = EXTENSIONS[extension];
  const declared = file.type.toLowerCase();
  const typeOk = (ACCEPTED_MIME_TYPES as readonly string[]).includes(declared);
  if (!byExtension || (declared !== "" && !typeOk)) {
    return { ok: false, reason: "UNSUPPORTED_TYPE", message: "Upload a PDF, PNG or JPEG file." };
  }
  return { ok: true };
}
