"use client";

import * as React from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { InlineError } from "@/components/common/states";
import { ProfileGroupEditor } from "@/features/profile/components/profile-group-editor";
import { useProfile } from "@/features/profile/queries";

/** Inline "add to profile" for the fields an eligibility check could not evaluate. */
export function MissingFieldsEditor({ missingKeys, onSaved }: { missingKeys: string[]; onSaved?: () => void }) {
  const [open, setOpen] = React.useState(false);
  const profile = useProfile(open);
  const fields = React.useMemo(() => (profile.data?.fields ?? []).filter((f) => missingKeys.includes(f.key)), [profile.data, missingKeys]);
  const labels = missingKeys.map((key) => profile.data?.fields.find((f) => f.key === key)?.label ?? key.replace(/_/g, " "));

  if (missingKeys.length === 0) return null;
  return (
    <div className="rounded-xl border border-warning/25 bg-warning-soft/60 px-4 py-3.5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm">
          <span className="font-medium">Missing from your profile:</span> {labels.join(", ")}
        </p>
        {!open ? (
          <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
            <Plus aria-hidden="true" />
            Add to profile
          </Button>
        ) : null}
      </div>
      {open ? (
        <div className="mt-4 rounded-xl bg-card p-4">
          {profile.isPending ? (
            <Skeleton className="h-10 w-full" />
          ) : profile.isError ? (
            <InlineError error={profile.error} />
          ) : fields.length === 0 ? (
            <p className="text-sm text-muted-foreground">These fields are not in the profile catalog yet.</p>
          ) : (
            <ProfileGroupEditor
              fields={fields}
              idPrefix={`missing-${missingKeys.join("-")}`}
              submitLabel="Save and check again"
              onSaved={() => {
                setOpen(false);
                onSaved?.();
              }}
            />
          )}
        </div>
      ) : null}
    </div>
  );
}
