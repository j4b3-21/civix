"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Check } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { ErrorState, InlineError, ListSkeleton, Notice } from "@/components/common/states";
import { DocumentUploader } from "@/features/documents/components/document-uploader";
import { ConsentPrompt } from "@/features/profile/components/consent-prompt";
import { ProfileGroupEditor } from "@/features/profile/components/profile-group-editor";
import { useConsents, useProfile, useSetConsent } from "@/features/profile/queries";
import { CONSENT_COPY } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { CONSENT_PURPOSES, type ConsentDto, type ConsentPurpose } from "@/types/api";

const STEPS = ["Permissions", "Your details", "First document"] as const;

export default function OnboardingPage() {
  const [step, setStep] = React.useState(0);
  return (
    <div className="space-y-10">
      <div className="space-y-3">
        <h1 className="text-headline font-semibold">Set up CIVIX</h1>
        <p className="max-w-2xl leading-relaxed text-muted-foreground">
          Three short steps. You can skip any of them and change everything later in Settings and the Trust Center.
        </p>
      </div>
      <ol className="grid grid-cols-3 gap-2" aria-label="Setup steps">
        {STEPS.map((label, i) => (
          <li key={label} aria-current={i === step ? "step" : undefined} className="space-y-2">
            <span className={cn("block h-1 rounded-full", i < step ? "bg-brand" : i === step ? "bg-primary" : "bg-muted")} aria-hidden="true" />
            <span className={cn("flex items-center gap-1.5 text-sm", i === step ? "font-medium text-foreground" : "text-muted-foreground")}>
              {i < step ? <Check className="size-3.5 text-brand-strong" aria-hidden="true" /> : <span className="tabular-nums">{i + 1}.</span>}
              {label}
            </span>
          </li>
        ))}
      </ol>
      {step === 0 ? <ConsentStep onDone={() => setStep(1)} /> : step === 1 ? <ProfileStep onBack={() => setStep(0)} onDone={() => setStep(2)} /> : <DocumentStep onBack={() => setStep(1)} />}
    </div>
  );
}

function ConsentStep({ onDone }: { onDone: () => void }) {
  const consents = useConsents();
  if (consents.isPending) return <ListSkeleton rows={3} />;
  if (consents.isError) return <ErrorState error={consents.error} onRetry={() => void consents.refetch()} />;
  return <ConsentForm consents={consents.data} onDone={onDone} />;
}

function ConsentForm({ consents, onDone }: { consents: ConsentDto[]; onDone: () => void }) {
  const setConsent = useSetConsent();
  const byPurpose = new Map(consents.map((c) => [c.purpose, c]));
  const [selected, setSelected] = React.useState<Record<ConsentPurpose, boolean>>(
    () => Object.fromEntries(CONSENT_PURPOSES.map((p) => [p, byPurpose.get(p)?.granted ?? false])) as Record<ConsentPurpose, boolean>,
  );
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<unknown>(null);

  async function save() {
    setSaving(true);
    setError(null);
    try {
      for (const purpose of CONSENT_PURPOSES) {
        const current = byPurpose.get(purpose)?.granted ?? false;
        if (selected[purpose] !== current) await setConsent.mutateAsync({ purpose, granted: selected[purpose] });
      }
      onDone();
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section aria-labelledby="consent-heading" className="space-y-6">
      <div className="space-y-2">
        <h2 id="consent-heading" className="text-title font-semibold">Choose what CIVIX may do</h2>
        <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">
          Each permission covers one purpose. Nothing is pre-selected, and features that need a permission will ask for it when you use them.
        </p>
      </div>
      <ul className="divide-y rounded-2xl border bg-card shadow-card">
        {CONSENT_PURPOSES.map((purpose) => {
          const copy = CONSENT_COPY[purpose];
          const server = byPurpose.get(purpose);
          const id = `consent-${purpose}`;
          return (
            <li key={purpose} className="flex gap-4 px-5 py-4">
              <Checkbox
                id={id}
                checked={selected[purpose]}
                onCheckedChange={(c) => setSelected((s) => ({ ...s, [purpose]: c === true }))}
                aria-describedby={`${id}-desc`}
                className="mt-0.5"
              />
              <div className="min-w-0 space-y-1">
                <Label htmlFor={id} className="text-[0.9375rem]">
                  {copy.title}
                </Label>
                <div id={`${id}-desc`} className="space-y-1 text-sm leading-relaxed text-muted-foreground">
                  <p>{server?.description || copy.description}</p>
                  <p className="text-[0.8125rem]">
                    {copy.enables}
                    {server?.noticeVersion ? ` Notice version ${server.noticeVersion}.` : ""}
                  </p>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
      <p className="text-sm text-muted-foreground">
        Read the full <Link href="/privacy" className="font-medium text-brand-strong underline underline-offset-4">privacy notice</Link>.
      </p>
      <InlineError error={error} />
      <div className="flex flex-wrap gap-3">
        <Button onClick={() => void save()} loading={saving}>
          Save and continue
        </Button>
      </div>
    </section>
  );
}

function ProfileStep({ onBack, onDone }: { onBack: () => void; onDone: () => void }) {
  const consents = useConsents();
  const profileGranted = consents.data?.find((c) => c.purpose === "PROFILE_PROCESSING")?.granted ?? false;
  const profile = useProfile(profileGranted);
  return (
    <section aria-labelledby="profile-heading" className="space-y-6">
      <div className="space-y-2">
        <h2 id="profile-heading" className="text-title font-semibold">Your core details</h2>
        <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">
          These are the details most eligibility rules depend on. Fill in what you know; anything left blank is treated as unknown, never as
          a no.
        </p>
      </div>
      {consents.isPending ? (
        <ListSkeleton rows={2} />
      ) : !profileGranted ? (
        <ConsentPrompt purpose="PROFILE_PROCESSING" />
      ) : profile.isPending ? (
        <ListSkeleton rows={3} />
      ) : profile.isError ? (
        <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />
      ) : (
        <div className="rounded-2xl border bg-card p-5 shadow-card sm:p-6">
          <ProfileGroupEditor
            fields={profile.data.fields.filter((f) => f.core)}
            grouped
            idPrefix="onboarding"
            submitLabel="Save and continue"
            onSaved={onDone}
          />
        </div>
      )}
      <div className="flex flex-wrap gap-3">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
        <Button variant="outline" onClick={onDone}>
          Continue without saving
        </Button>
      </div>
    </section>
  );
}

function DocumentStep({ onBack }: { onBack: () => void }) {
  const router = useRouter();
  const [uploaded, setUploaded] = React.useState(false);
  return (
    <section aria-labelledby="document-heading" className="space-y-6">
      <div className="space-y-2">
        <h2 id="document-heading" className="text-title font-semibold">Add a first document (optional)</h2>
        <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">
          CIVIX reads it and suggests values for your profile. You review each one before it is used.
        </p>
      </div>
      <Notice tone="warning" title="Extraction is not verification.">
        CIVIX does not verify certificate authenticity.
      </Notice>
      <div className="max-w-md rounded-2xl border bg-card p-5 shadow-card">
        {uploaded ? (
          <p role="status" className="text-sm">
            Uploaded. You can review the values it finds on the Documents page.
          </p>
        ) : (
          <DocumentUploader onUploaded={() => setUploaded(true)} />
        )}
      </div>
      <div className="flex flex-wrap gap-3">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
        <Button
          onClick={() => {
            toast.success("You are set up");
            router.push("/dashboard");
          }}
        >
          {uploaded ? "Go to dashboard" : "Skip and go to dashboard"}
        </Button>
      </div>
    </section>
  );
}
