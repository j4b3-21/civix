import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { isApiError } from "@/lib/api/client";

/**
 * Copies RFC 9457 `errors[]` onto react-hook-form fields.
 * Field names from Spring may be plain (`email`) or nested (`request.email`); the last
 * segment is matched against the form's field names. Returns true if any field was mapped.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): boolean {
  if (!isApiError(error) || error.fieldErrors.length === 0) return false;
  let mapped = false;
  for (const fieldError of error.fieldErrors) {
    const name = fieldError.field.split(".").at(-1) ?? fieldError.field;
    const match = fields.find((f) => f === name);
    if (match) {
      setError(match, { type: "server", message: capitalise(fieldError.message) }, { shouldFocus: !mapped });
      mapped = true;
    }
  }
  return mapped;
}

function capitalise(message: string): string {
  return message ? message.charAt(0).toUpperCase() + message.slice(1) : message;
}
