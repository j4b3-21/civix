"use client";

import * as React from "react";
import { useQueryClient } from "@tanstack/react-query";
import { CreditCard, Eye, EyeOff, KeyRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TaskScreenshot } from "@/features/agent/components/screenshot";
import { submitCitizenInput } from "@/lib/api/agent";
import { errorMessage } from "@/lib/api/errors";
import { CITIZEN_INPUT_LABELS } from "@/lib/labels";
import { queryKeys } from "@/lib/query-keys";
import type { PendingCitizenInput } from "@/types/api";

/** Values sent for confirmation-only kinds (the API needs 1–200 characters). */
export const DECLARATION_ACCEPTED_VALUE = "ACCEPTED";
export const PAYMENT_CONFIRMED_VALUE = "PAYMENT_COMPLETED_BY_CITIZEN";

/**
 * Collects OTP / CAPTCHA / sign-in / declaration input for a paused run.
 * The value lives only in this component's state: it is not written to any storage, not put
 * in the query cache (the API is called directly, not through useMutation), and is cleared
 * as soon as it has been sent.
 */
export function CitizenInputCard({ taskId, input }: { taskId: string; input: PendingCitizenInput }) {
  const queryClient = useQueryClient();
  const [value, setValue] = React.useState("");
  const [declared, setDeclared] = React.useState(false);
  const [reveal, setReveal] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [sent, setSent] = React.useState(false);
  const inputId = `citizen-input-${input.requestId}`;
  const masked = input.kind === "OTP" || input.kind === "LOGIN";

  async function send(payload: string) {
    setSubmitting(true);
    setError(null);
    try {
      await submitCitizenInput(taskId, { requestId: input.requestId, value: payload });
      setSent(true);
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.detail(taskId) });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setValue("");
      setSubmitting(false);
    }
  }

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (input.kind === "DECLARATION") {
      if (!declared) {
        setError("Tick the box to confirm the declaration.");
        return;
      }
      void send(DECLARATION_ACCEPTED_VALUE);
      return;
    }
    const trimmed = value.trim();
    if (trimmed.length < 1 || trimmed.length > 200) {
      setError("Enter between 1 and 200 characters.");
      return;
    }
    void send(trimmed);
  }

  if (sent) {
    return (
      <section className="rounded-2xl border border-brand/30 bg-card p-5 shadow-card" role="status">
        <p className="font-medium">Sent to the agent</p>
        <p className="mt-1 text-sm text-muted-foreground">CIVIX passed it to the portal once and did not keep a copy. The run will resume shortly.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby={`${inputId}-title`} className="rounded-2xl border border-brand/30 bg-card shadow-raised">
      <form onSubmit={onSubmit} className="space-y-4 p-5" autoComplete="off" noValidate>
        <div className="space-y-2">
          <span className="inline-flex items-center gap-2 text-sm font-medium text-brand-strong">
            <span aria-hidden="true" className="size-2.5 rounded-[3px] bg-brand" />
            The portal needs you
          </span>
          <h2 id={`${inputId}-title`} className="flex items-center gap-2 text-title font-semibold">
            {input.kind === "PAYMENT" ? <CreditCard className="size-5" aria-hidden="true" /> : <KeyRound className="size-5" aria-hidden="true" />}
            {CITIZEN_INPUT_LABELS[input.kind]}
          </h2>
          <p className="text-sm leading-relaxed text-foreground/85">{input.prompt}</p>
        </div>

        {input.screenshotId ? (
          <TaskScreenshot taskId={taskId} screenshotId={input.screenshotId} alt={`Portal page asking for ${CITIZEN_INPUT_LABELS[input.kind].toLowerCase()}`} />
        ) : null}

        {input.kind === "PAYMENT" ? (
          <div className="space-y-3">
            <p className="rounded-xl bg-warning-soft px-3 py-2.5 text-sm leading-relaxed">
              CIVIX never automates payments — complete payment on the portal yourself. When you have paid, let the agent know so it can check
              the portal and continue.
            </p>
            <Button type="button" onClick={() => void send(PAYMENT_CONFIRMED_VALUE)} loading={submitting}>
              I have completed payment on the portal
            </Button>
          </div>
        ) : input.kind === "DECLARATION" ? (
          <div className="flex items-start gap-3">
            <Checkbox id={inputId} checked={declared} onCheckedChange={(c) => setDeclared(c === true)} />
            <Label htmlFor={inputId} className="leading-relaxed font-normal">
              I have read the declaration above and confirm it is true.
            </Label>
          </div>
        ) : (
          <div className="space-y-1.5">
            <Label htmlFor={inputId}>{input.kind === "OTP" ? "Code from your SMS or email" : input.kind === "CAPTCHA" ? "Characters shown in the image" : "Value requested by the portal"}</Label>
            <div className="relative">
              <Input
                id={inputId}
                name={`civix-${input.kind.toLowerCase()}-${input.requestId}`}
                type={masked && !reveal ? "password" : "text"}
                inputMode={input.kind === "OTP" ? "numeric" : undefined}
                autoComplete={input.kind === "OTP" ? "one-time-code" : "off"}
                autoCorrect="off"
                autoCapitalize="off"
                spellCheck={false}
                maxLength={200}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                aria-describedby={`${inputId}-hint`}
                className={masked ? "pr-11" : undefined}
                disabled={submitting}
              />
              {masked ? (
                <button
                  type="button"
                  onClick={() => setReveal((r) => !r)}
                  className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-xl text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
                  aria-label={reveal ? "Hide value" : "Show value"}
                  aria-pressed={reveal}
                >
                  {reveal ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
                </button>
              ) : null}
            </div>
            <p id={`${inputId}-hint`} className="text-[0.8125rem] text-muted-foreground">
              Sent once to the running task and never stored by CIVIX.
            </p>
          </div>
        )}

        {error ? (
          <p role="alert" className="text-sm text-danger">
            {error}
          </p>
        ) : null}

        {input.kind !== "PAYMENT" ? (
          <Button type="submit" variant="brand" loading={submitting}>
            {input.kind === "DECLARATION" ? "Confirm declaration" : "Send to portal"}
          </Button>
        ) : null}
      </form>
    </section>
  );
}
