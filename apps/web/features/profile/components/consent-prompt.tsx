"use client";

import { KeyRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { InlineError } from "@/components/common/states";
import { useSetConsent } from "@/features/profile/queries";
import { CONSENT_COPY } from "@/lib/labels";
import { cn } from "@/lib/utils";
import type { ConsentPurpose } from "@/types/api";

/** Shown when the API answers 403 CONSENT_REQUIRED: explains the purpose and grants it in place. */
export function ConsentPrompt({
  purpose,
  onGranted,
  className,
}: {
  purpose: ConsentPurpose;
  onGranted?: () => void;
  className?: string;
}) {
  const setConsent = useSetConsent();
  const copy = CONSENT_COPY[purpose];
  return (
    <div className={cn("rounded-2xl border border-warning/25 bg-warning-soft px-4 py-4", className)} role="region" aria-label="Permission needed">
      <div className="flex gap-3">
        <KeyRound className="mt-0.5 size-4.5 shrink-0 text-warning" aria-hidden="true" />
        <div className="min-w-0 space-y-2 text-sm">
          <p className="font-medium text-foreground">Permission needed: {copy.title.toLowerCase()}</p>
          <p className="leading-relaxed text-foreground/85">{copy.description}</p>
          <p className="text-muted-foreground">You can withdraw this at any time in the Trust Center.</p>
          <div className="flex flex-wrap items-center gap-3 pt-1">
            <Button
              size="sm"
              loading={setConsent.isPending}
              onClick={() => setConsent.mutate({ purpose, granted: true }, { onSuccess: () => onGranted?.() })}
            >
              Grant and continue
            </Button>
            <InlineError error={setConsent.error} />
          </div>
        </div>
      </div>
    </div>
  );
}

/** Extracts the purpose to ask for when an error is CONSENT_REQUIRED, falling back to `fallback`. */
export function consentPurposeFromError(error: unknown, fallback: ConsentPurpose): ConsentPurpose | null {
  if (!error || typeof error !== "object" || !("code" in error) || (error as { code: unknown }).code !== "CONSENT_REQUIRED") return null;
  const detail = String((error as { detail?: unknown }).detail ?? "");
  const named = (["PORTAL_DOCUMENT_UPLOAD", "PROFILE_PROCESSING", "DOCUMENT_PROCESSING", "ELIGIBILITY_EVALUATION", "AGENT_EXECUTION"] as const).find((p) =>
    detail.includes(p),
  );
  return named ?? fallback;
}
