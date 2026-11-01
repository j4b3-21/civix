"use client";

import * as React from "react";
import Link from "next/link";
import { CheckCircle2, Eraser, FileText } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { InlineError } from "@/components/common/states";
import { ProvenanceBadge } from "@/components/common/status-badges";
import { ConsentPrompt, consentPurposeFromError } from "@/features/profile/components/consent-prompt";
import { ProfileFieldInput } from "@/features/profile/components/profile-field-input";
import { groupFields, groupLabel, mapProfileFieldErrors, parseInputString, toInputString } from "@/features/profile/field-values";
import { useUpdateProfile } from "@/features/profile/queries";
import { isApiError } from "@/lib/api/client";
import { formatPercent } from "@/lib/format";
import type { ProfileFieldDto, ProfileValue } from "@/types/api";

function initialDraft(fields: readonly ProfileFieldDto[]): Record<string, string> {
  return Object.fromEntries(fields.map((f) => [f.key, toInputString(f.value, f.type)]));
}

/**
 * Edits one group of catalog fields and saves only the changed ones with PUT /profile.
 * With `showProvenance`, each field shows its status, source document and confirm/clear actions.
 */
export function ProfileGroupEditor({
  fields,
  showProvenance = false,
  submitLabel = "Save changes",
  onSaved,
  idPrefix = "profile",
  grouped = false,
}: {
  fields: ProfileFieldDto[];
  grouped?: boolean;
  showProvenance?: boolean;
  submitLabel?: string;
  onSaved?: () => void;
  idPrefix?: string;
}) {
  const update = useUpdateProfile();
  const baseline = React.useMemo(() => initialDraft(fields), [fields]);
  const [draft, setDraft] = React.useState<Record<string, string>>(baseline);
  const [errors, setErrors] = React.useState<Record<string, string>>({});
  const [lastBaseline, setLastBaseline] = React.useState(baseline);

  // When the server copy changes (after a save or refetch), reset untouched inputs to it.
  if (lastBaseline !== baseline) {
    setLastBaseline(baseline);
    setDraft((current) => {
      const next = { ...baseline };
      for (const [key, value] of Object.entries(current)) {
        if (value !== lastBaseline[key]) next[key] = value;
      }
      return next;
    });
  }

  const changedKeys = fields.map((f) => f.key).filter((key) => (draft[key] ?? "") !== (baseline[key] ?? ""));

  function save(event: React.FormEvent) {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    const payload: { key: string; value: ProfileValue }[] = [];
    for (const key of changedKeys) {
      const field = fields.find((f) => f.key === key);
      if (!field) continue;
      const parsed = parseInputString(draft[key] ?? "", field.type, field.options);
      if (parsed.ok) payload.push({ key, value: parsed.value });
      else nextErrors[key] = parsed.message;
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0 || payload.length === 0) return;
    update.mutate(
      { fields: payload },
      {
        onSuccess: () => {
          toast.success(payload.length === 1 ? "Profile field saved" : `${payload.length} profile fields saved`);
          onSaved?.();
        },
        onError: (error) => {
          if (isApiError(error)) setErrors(mapProfileFieldErrors(error.fieldErrors, payload.map((p) => p.key)));
        },
      },
    );
  }

  function confirmField(key: string) {
    update.mutate({ fields: [], confirmKeys: [key] }, { onSuccess: () => toast.success("Value confirmed") });
  }

  function clearField(key: string) {
    update.mutate({ fields: [{ key, value: null }] }, { onSuccess: () => toast.success("Value cleared") });
  }

  const consentPurpose = consentPurposeFromError(update.error, "PROFILE_PROCESSING");
  const sections: [string | null, ProfileFieldDto[]][] = grouped ? groupFields(fields) : [[null, fields]];

  const renderField = (field: ProfileFieldDto) => {
          const id = `${idPrefix}-${field.key}`;
          const error = errors[field.key];
          const hintId = `${id}-hint`;
          const errorId = `${id}-error`;
          const canConfirm = showProvenance && (field.status === "EXTRACTED" || field.status === "PROVIDED") && draft[field.key] === baseline[field.key];
          return (
            <div key={field.key} className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Label htmlFor={id}>{field.label}</Label>
                {showProvenance ? <ProvenanceBadge status={field.status} /> : null}
              </div>
              <ProfileFieldInput
                field={field}
                id={id}
                value={draft[field.key] ?? ""}
                onChange={(value) => setDraft((d) => ({ ...d, [field.key]: value }))}
                invalid={Boolean(error)}
                describedBy={error ? errorId : hintId}
                disabled={update.isPending}
              />
              {error ? (
                <p id={errorId} className="text-[0.8125rem] font-medium text-danger">
                  {error}
                </p>
              ) : (
                <p id={hintId} className="text-[0.8125rem] leading-relaxed text-muted-foreground">
                  {field.purpose}
                </p>
              )}
              {showProvenance && (field.sourceDocumentId || field.confidence !== null || canConfirm || field.value !== null) ? (
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[0.8125rem] text-muted-foreground">
                  {field.sourceDocumentId ? (
                    <Link href={`/documents#doc-${field.sourceDocumentId}`} className="inline-flex items-center gap-1 font-medium text-brand-strong hover:underline">
                      <FileText className="size-3.5" aria-hidden="true" />
                      Source document
                    </Link>
                  ) : null}
                  {field.confidence !== null && field.status === "EXTRACTED" ? <span>Read with {formatPercent(field.confidence)} confidence</span> : null}
                  {canConfirm ? (
                    <Button type="button" variant="link" size="sm" className="h-auto text-[0.8125rem]" onClick={() => confirmField(field.key)} disabled={update.isPending}>
                      <CheckCircle2 aria-hidden="true" />
                      Confirm value
                    </Button>
                  ) : null}
                  {field.value !== null && field.status !== "VERIFIED" ? (
                    <Button type="button" variant="link" size="sm" className="h-auto text-[0.8125rem] text-muted-foreground" onClick={() => clearField(field.key)} disabled={update.isPending}>
                      <Eraser aria-hidden="true" />
                      Clear
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>
          );
  };

  return (
    <form onSubmit={save} noValidate className="space-y-6">
      {sections.map(([group, groupFieldsList]) => (
        <fieldset key={group ?? "all"} className="space-y-4">
          {group ? <legend className="mb-4 text-sm font-semibold text-foreground">{groupLabel(group)}</legend> : null}
          <div className="grid gap-x-6 gap-y-6 md:grid-cols-2">{groupFieldsList.map(renderField)}</div>
        </fieldset>
      ))}
      {consentPurpose ? (
        <ConsentPrompt purpose={consentPurpose} onGranted={() => update.reset()} />
      ) : update.isError && Object.keys(errors).length === 0 ? (
        <InlineError error={update.error} />
      ) : null}
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={update.isPending} disabled={changedKeys.length === 0}>
          {submitLabel}
        </Button>
        {changedKeys.length > 0 ? (
          <span className="text-sm text-muted-foreground" aria-live="polite">
            {changedKeys.length === 1 ? "1 unsaved change" : `${changedKeys.length} unsaved changes`}
          </span>
        ) : null}
      </div>
    </form>
  );
}
