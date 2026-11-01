"use client";

import { toast } from "sonner";
import { Switch } from "@/components/ui/switch";
import { ErrorState, ListSkeleton } from "@/components/common/states";
import { useConsents, useSetConsent } from "@/features/profile/queries";
import { errorMessage } from "@/lib/api/errors";
import { formatDateTime } from "@/lib/format";
import { CONSENT_COPY } from "@/lib/labels";
import { CONSENT_PURPOSES, type ConsentDto } from "@/types/api";

export function PermissionsSection() {
  const consents = useConsents();
  const setConsent = useSetConsent();
  if (consents.isPending) return <ListSkeleton rows={2} />;
  if (consents.isError) return <ErrorState error={consents.error} onRetry={() => void consents.refetch()} />;
  const byPurpose = new Map(consents.data.map((c) => [c.purpose, c]));
  const granted = CONSENT_PURPOSES.filter((p) => byPurpose.get(p)?.granted);
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground tabular-nums">
        {granted.length} of {CONSENT_PURPOSES.length} permissions granted
      </p>
      <ul className="divide-y rounded-2xl border bg-card shadow-card">
        {CONSENT_PURPOSES.map((purpose) => {
          const consent: ConsentDto | undefined = byPurpose.get(purpose);
          const copy = CONSENT_COPY[purpose];
          const on = consent?.granted ?? false;
          const id = `permission-${purpose}`;
          const busy = setConsent.isPending && setConsent.variables?.purpose === purpose;
          return (
            <li key={purpose} className="flex items-start gap-4 px-5 py-4">
              <div className="min-w-0 flex-1 space-y-1">
                <label htmlFor={id} className="font-medium">
                  {copy.title}
                </label>
                <p className="text-sm leading-relaxed text-muted-foreground">{consent?.description || copy.description}</p>
                <p className="text-[0.8125rem] text-muted-foreground">
                  {on && consent?.grantedAt
                    ? `Granted ${formatDateTime(consent.grantedAt)}`
                    : consent?.revokedAt
                      ? `Withdrawn ${formatDateTime(consent.revokedAt)}`
                      : "Never granted"}
                  {consent?.noticeVersion ? `, notice version ${consent.noticeVersion}` : ""}
                </p>
              </div>
              <Switch
                id={id}
                checked={on}
                disabled={busy}
                aria-describedby={undefined}
                onCheckedChange={(next) =>
                  setConsent.mutate(
                    { purpose, granted: next },
                    {
                      onSuccess: () => toast.success(next ? "Permission granted" : "Permission withdrawn", { description: copy.title }),
                      onError: (e) => toast.error("Could not update the permission", { description: errorMessage(e) }),
                    },
                  )
                }
              />
            </li>
          );
        })}
      </ul>
    </div>
  );
}
