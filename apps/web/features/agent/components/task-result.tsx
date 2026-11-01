"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Ban, BadgeCheck, CircleHelp, FlaskConical, RotateCw, XCircle } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { lastEventOf, metaBoolean, metaString } from "@/features/agent/event-meta";
import { useRetryTask } from "@/features/agent/queries";
import { useExecuteApplication } from "@/features/applications/queries";
import { isApiError } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { humanize } from "@/lib/labels";
import type { AgentTaskDto, ExecutionEventDto } from "@/types/api";

function errorNextStep(code: string | null): { href: string; label: string } | null {
  if (code === "NEEDS_PROFILE_INFORMATION") return { href: "/settings", label: "Complete your profile" };
  if (code === "DOCUMENT_REQUIRED") return { href: "/documents", label: "Add documents" };
  return null;
}

export function TaskResult({ task, events, onRetried }: { task: AgentTaskDto; events: ExecutionEventDto[]; onRetried: () => void }) {
  const retry = useRetryTask(task.id);
  const execute = useExecuteApplication();
  const router = useRouter();
  const runAgain = () =>
    execute.mutate(task.applicationId, {
      onSuccess: (newTask) => {
        toast.success("Started a new run");
        router.push(`/agent/${newTask.id}`);
      },
      onError: (e) => toast.error("Could not start a new run", { description: errorMessage(e) }),
    });
  const receipt = lastEventOf(events, "RECEIPT_VERIFIED");
  const uncertain = lastEventOf(events, "SUBMISSION_UNCERTAIN");
  const failed = lastEventOf(events, "TASK_FAILED");

  if (task.status === "COMPLETED" && receipt) {
    const reference = metaString(receipt.metadata, "referenceNumber");
    const isTest = metaBoolean(receipt.metadata, "isTestEnvironment") === true;
    return (
      <section className="rounded-2xl border border-success/30 bg-card p-5 shadow-card" aria-labelledby="result-heading">
        <p id="result-heading" className="flex items-center gap-2 font-semibold text-success">
          <BadgeCheck className="size-5" aria-hidden="true" />
          Receipt verified on the portal
        </p>
        {reference ? <p className="mt-3 text-headline font-semibold tabular-nums">{reference}</p> : null}
        {isTest ? (
          <p className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-info">
            <FlaskConical className="size-4" aria-hidden="true" />
            Test environment — not a government service
          </p>
        ) : null}
        <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
          CIVIX found this reference on the portal&apos;s acknowledgement page. Keep it for any follow-up with the department.
        </p>
        <Button asChild variant="outline" className="mt-4">
          <Link href={`/applications/${task.applicationId}#receipts`}>View receipt</Link>
        </Button>
      </section>
    );
  }

  if (uncertain && (task.status === "COMPLETED" || task.status === "FAILED")) {
    return (
      <section className="rounded-2xl border border-warning/30 bg-warning-soft p-5" aria-labelledby="result-heading">
        <p id="result-heading" className="flex items-center gap-2 font-semibold">
          <CircleHelp className="size-5 text-warning" aria-hidden="true" />
          Submission not confirmed
        </p>
        <div className="mt-2 space-y-2 text-sm leading-relaxed">
          <p>The submit step ran, but CIVIX could not find the portal&apos;s acknowledgement. It will not submit again automatically.</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>Do not resubmit until you have checked the portal.</li>
            <li>Sign in to the portal and look for an acknowledgement or application number.</li>
            <li>Check for an email or SMS from the department.</li>
          </ul>
        </div>
        <Button asChild variant="outline" className="mt-4">
          <Link href={`/applications/${task.applicationId}`}>Open application</Link>
        </Button>
      </section>
    );
  }

  if (task.status === "FAILED") {
    const code = task.error?.code ?? (failed ? metaString(failed.metadata, "errorCode") : null);
    const retryable = failed ? metaBoolean(failed.metadata, "retryable") === true : false;
    const next = errorNextStep(code);
    return (
      <section className="rounded-2xl border border-danger/30 bg-card p-5 shadow-card" aria-labelledby="result-heading">
        <p id="result-heading" className="flex items-center gap-2 font-semibold text-danger">
          <XCircle className="size-5" aria-hidden="true" />
          The run stopped
        </p>
        <p className="mt-2 text-sm leading-relaxed">{task.error?.message ?? failed?.message ?? "The agent could not finish this run."}</p>
        {code ? <p className="mt-1 text-[0.8125rem] text-muted-foreground">Reason: {humanize(code)}</p> : null}
        <div className="mt-4 flex flex-wrap gap-2">
          {retryable ? (
            <Button
              onClick={() =>
                retry.mutate(undefined, {
                  onSuccess: () => {
                    toast.success("Retrying the run");
                    onRetried();
                  },
                  onError: (e) => {
                    // The agent can only resume runs that crashed mid-way; finished runs need a fresh run.
                    if (isApiError(e) && (e.code === "TASK_NOT_RESUMABLE" || e.status === 409)) runAgain();
                    else toast.error("Could not retry", { description: errorMessage(e) });
                  },
                })
              }
              loading={retry.isPending || execute.isPending}
            >
              <RotateCw aria-hidden="true" />
              Retry run
            </Button>
          ) : null}
          {next ? (
            <Button asChild variant={retryable ? "outline" : "default"}>
              <Link href={next.href}>{next.label}</Link>
            </Button>
          ) : null}
          <Button asChild variant="ghost">
            <Link href={`/applications/${task.applicationId}`}>Open application</Link>
          </Button>
        </div>
      </section>
    );
  }

  if (task.status === "CANCELLED") {
    return (
      <section className="rounded-2xl border bg-card p-5 shadow-card" aria-labelledby="result-heading">
        <p id="result-heading" className="flex items-center gap-2 font-semibold">
          <Ban className="size-5 text-muted-foreground" aria-hidden="true" />
          Run cancelled
        </p>
        <p className="mt-2 text-sm text-muted-foreground">Nothing further will happen in this run.</p>
        <Button asChild variant="outline" className="mt-4">
          <Link href={`/applications/${task.applicationId}`}>Open application</Link>
        </Button>
      </section>
    );
  }

  if (task.status === "COMPLETED") {
    return (
      <section className="rounded-2xl border bg-card p-5 shadow-card">
        <p className="font-semibold">Run completed</p>
        <Button asChild variant="outline" className="mt-4">
          <Link href={`/applications/${task.applicationId}`}>Open application</Link>
        </Button>
      </section>
    );
  }
  return null;
}
