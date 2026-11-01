"use client";

import * as React from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { AlertTriangle, ArrowLeft, Bot, ExternalLink, FlaskConical, Hand, MonitorPlay, XCircle } from "lucide-react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, InlineError, ListSkeleton, Notice } from "@/components/common/states";
import { ApplicationStatusBadge, EligibilityBadge, TestServiceBadge } from "@/components/common/status-badges";
import { ReceiptCard } from "@/features/applications/components/receipt-card";
import { RequiredActionLink } from "@/features/applications/components/required-action";
import { StatusTrack } from "@/features/applications/components/status-track";
import { TERMINAL_APPLICATION_STATUSES, useApplication, useCancelApplication, useExecuteApplication, useReceipts } from "@/features/applications/queries";
import { useAuditEvents } from "@/features/dashboard/queries";
import { ConsentPrompt, consentPurposeFromError } from "@/features/profile/components/consent-prompt";
import { hasErrorCode, isApiError } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { formatDateTime } from "@/lib/format";
import type { ApplicationDto } from "@/types/api";

const RUNNABLE = new Set(["DRAFT", "ACTION_REQUIRED", "FAILED"]);

export default function ApplicationDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>();
  const application = useApplication(applicationId);

  if (application.isPending) {
    return (
      <div className="space-y-4" role="status" aria-label="Loading application">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-24 w-full rounded-2xl" />
        <Skeleton className="h-48 w-full rounded-2xl" />
      </div>
    );
  }
  if (application.isError) {
    return (
      <div className="space-y-6">
        <BackLink />
        <ErrorState error={application.error} title="This application could not be loaded" onRetry={() => void application.refetch()} />
      </div>
    );
  }
  return <ApplicationDetail application={application.data} />;
}

function ApplicationDetail({ application: a }: { application: ApplicationDto }) {
  const terminal = TERMINAL_APPLICATION_STATUSES.has(a.status);
  return (
    <div className="space-y-8">
      <BackLink />
      <header className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-headline font-semibold">{a.service.name}</h1>
          <ApplicationStatusBadge status={a.status} />
        </div>
        <p className="text-sm text-muted-foreground">
          Started {formatDateTime(a.createdAt)}
          {a.submittedAt ? <>. Submitted {formatDateTime(a.submittedAt)}</> : null}
          {a.referenceNumber ? (
            <>
              . Reference <span className="font-medium text-foreground tabular-nums">{a.referenceNumber}</span>
            </>
          ) : null}
        </p>
        <div className="max-w-xl">
          <StatusTrack status={a.status} />
        </div>
      </header>

      {a.isTestEnvironment || a.service.isTestService ? (
        <Notice tone="info" icon={FlaskConical} title="Test environment">
          <TestServiceBadge className="mb-1" />
          <p>This application runs against the CIVIX Test Portal. It is not sent to any government department and has no legal effect.</p>
        </Notice>
      ) : null}

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-8">
          {a.requiredAction ? (
            <section aria-labelledby="action-heading" className="rounded-2xl border border-warning/30 bg-warning-soft p-5">
              <h2 id="action-heading" className="flex items-center gap-2 font-semibold">
                <AlertTriangle className="size-4.5 text-warning" aria-hidden="true" />
                Action required
              </h2>
              <p className="mt-2 text-sm leading-relaxed">{a.requiredAction.message}</p>
              {a.requiredAction.type === "REVIEW_UNCERTAIN" ? <UncertainGuidance officialUrl={a.service.officialUrl} /> : null}
              <div className="mt-4">
                <RequiredActionLink action={a.requiredAction} applicationId={a.id} size="default" />
              </div>
            </section>
          ) : null}

          {a.status === "SUBMISSION_UNCERTAIN" && a.requiredAction?.type !== "REVIEW_UNCERTAIN" ? (
            <section className="rounded-2xl border border-warning/30 bg-warning-soft p-5">
              <h2 className="font-semibold">Submission not confirmed</h2>
              <UncertainGuidance officialUrl={a.service.officialUrl} />
            </section>
          ) : null}

          <Receipts application={a} />
          <History application={a} />
        </div>

        <aside className="space-y-4 lg:sticky lg:top-24 lg:h-fit">
          {a.eligibilityOutcome ? (
            <div className="rounded-2xl border bg-card p-5 shadow-card">
              <h2 className="mb-2 text-sm font-semibold">Eligibility at start</h2>
              <EligibilityBadge outcome={a.eligibilityOutcome} />
            </div>
          ) : null}
          <RunPanel application={a} />
          {!terminal ? <CancelApplication application={a} /> : null}
        </aside>
      </div>
    </div>
  );
}

function UncertainGuidance({ officialUrl }: { officialUrl: string | null }) {
  return (
    <div className="mt-2 space-y-2 text-sm leading-relaxed">
      <p>CIVIX could not find the portal&apos;s acknowledgement after submitting. The application may or may not have been received.</p>
      <ul className="list-disc space-y-1 pl-5">
        <li>Do not submit again until you have checked. A duplicate application can delay or void both.</li>
        <li>Sign in to the official portal and look for an acknowledgement, application number or status page.</li>
        <li>Check your email and SMS for a message from the department.</li>
      </ul>
      {officialUrl ? (
        <a href={officialUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium text-brand-strong hover:underline">
          Open the official portal
          <ExternalLink className="size-3.5" aria-hidden="true" />
          <span className="sr-only">(opens in a new tab)</span>
        </a>
      ) : null}
    </div>
  );
}

function RunPanel({ application: a }: { application: ApplicationDto }) {
  const router = useRouter();
  const execute = useExecuteApplication();
  const guidedOnly = a.service.automationStatus === "NOT_AUTOMATED" || a.service.automationStatus === "GUIDED";
  const consentPurpose = consentPurposeFromError(execute.error, "AGENT_EXECUTION");
  const taskActive = isApiError(execute.error) && execute.error.status === 409 && !hasErrorCode(execute.error, "APPLICATION_EXISTS");

  function run() {
    execute.mutate(a.id, {
      onSuccess: (task) => {
        toast.success("CIVIX agent started");
        router.push(`/agent/${task.id}`);
      },
    });
  }

  if (guidedOnly) {
    return (
      <section aria-labelledby="run-heading" className="space-y-3 rounded-2xl border bg-card p-5 shadow-card">
        <h2 id="run-heading" className="flex items-center gap-2 font-semibold">
          <Hand className="size-4.5 text-brand-strong" aria-hidden="true" />
          Guided only: apply on the official portal
        </h2>
        <p className="text-sm leading-relaxed text-muted-foreground">
          CIVIX cannot fill in this portal. Follow the steps on the service page and submit on the official site.
        </p>
        <div className="flex flex-col gap-2">
          <Button asChild variant="outline">
            <Link href={`/services/${a.service.id}`}>View steps and documents</Link>
          </Button>
          {a.service.officialUrl ? (
            <Button asChild>
              <a href={a.service.officialUrl} target="_blank" rel="noopener noreferrer">
                <ExternalLink aria-hidden="true" />
                Official portal
                <span className="sr-only">(opens in a new tab)</span>
              </a>
            </Button>
          ) : null}
        </div>
      </section>
    );
  }

  return (
    <section aria-labelledby="run-heading" className="space-y-3 rounded-2xl border bg-card p-5 shadow-card">
      <h2 id="run-heading" className="flex items-center gap-2 font-semibold">
        <Bot className="size-4.5 text-brand-strong" aria-hidden="true" />
        CIVIX agent
      </h2>
      <p className="text-sm leading-relaxed text-muted-foreground">
        The agent checks your profile and documents, then fills in the portal while you watch. It stops for your approval before
        filling, uploading and submitting.
      </p>
      {a.latestTaskId ? (
        <Button asChild variant="outline" className="w-full">
          <Link href={`/agent/${a.latestTaskId}`}>
            <MonitorPlay aria-hidden="true" />
            Open control room
          </Link>
        </Button>
      ) : null}
      {RUNNABLE.has(a.status) ? (
        <Button className="w-full" onClick={run} loading={execute.isPending}>
          <Bot aria-hidden="true" />
          Run with CIVIX agent
        </Button>
      ) : null}
      {consentPurpose ? (
        <ConsentPrompt purpose={consentPurpose} onGranted={run} />
      ) : taskActive ? (
        <p className="text-sm text-warning">A run is already active for this application. Open the control room to follow it.</p>
      ) : execute.isError ? (
        <InlineError error={execute.error} />
      ) : null}
    </section>
  );
}

function CancelApplication({ application: a }: { application: ApplicationDto }) {
  const cancel = useCancelApplication();
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="ghost" className="w-full text-danger hover:bg-danger-soft hover:text-danger">
          <XCircle aria-hidden="true" />
          Cancel application
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Cancel this application?</AlertDialogTitle>
          <AlertDialogDescription>
            Any run in progress stops and pending approvals are withdrawn. If something was already submitted to the portal, cancelling here
            does not withdraw it there.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Keep application</AlertDialogCancel>
          <AlertDialogAction
            destructive
            onClick={() =>
              cancel.mutate(a.id, {
                onSuccess: () => toast.success("Application cancelled"),
                onError: (e) => toast.error("Could not cancel the application", { description: errorMessage(e) }),
              })
            }
          >
            Cancel application
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function Receipts({ application: a }: { application: ApplicationDto }) {
  const receipts = useReceipts(a.id);
  return (
    <section aria-labelledby="receipts" className="scroll-mt-24 space-y-3">
      <h2 id="receipts" className="text-title font-semibold">Receipts</h2>
      {receipts.isPending ? (
        <ListSkeleton rows={1} />
      ) : receipts.isError ? (
        <ErrorState error={receipts.error} onRetry={() => void receipts.refetch()} />
      ) : receipts.data.length === 0 ? (
        <EmptyState
          title="No receipt yet"
          description="A receipt appears here only after CIVIX finds the portal's acknowledgement and reference number."
        />
      ) : (
        <div className="space-y-3">
          {receipts.data.map((r) => (
            <ReceiptCard key={r.id} receipt={r} />
          ))}
        </div>
      )}
    </section>
  );
}

function History({ application: a }: { application: ApplicationDto }) {
  const audit = useAuditEvents({ category: "APPLICATION", size: 100 });
  const events = (audit.data?.items ?? []).filter((e) => e.resourceId === a.id);
  return (
    <section aria-labelledby="history" className="space-y-3">
      <h2 id="history" className="text-title font-semibold">History</h2>
      {audit.isPending ? (
        <ListSkeleton rows={1} />
      ) : audit.isError ? (
        <ErrorState error={audit.error} onRetry={() => void audit.refetch()} />
      ) : (
        <ol className="relative space-y-5 rounded-2xl border bg-card p-5 shadow-card">
          {events.map((e) => (
            <li key={e.id} className="flex gap-3">
              <span aria-hidden="true" className={e.outcome === "SUCCESS" ? "mt-1.5 size-2 shrink-0 rounded-full bg-brand" : "mt-1.5 size-2 shrink-0 rounded-full bg-danger"} />
              <div>
                <p className="text-sm">{e.summary}</p>
                <p className="text-xs text-muted-foreground">{formatDateTime(e.occurredAt)}</p>
              </div>
            </li>
          ))}
          <li className="flex gap-3">
            <span aria-hidden="true" className="mt-1.5 size-2 shrink-0 rounded-full bg-muted-foreground/40" />
            <div>
              <p className="text-sm">Application started</p>
              <p className="text-xs text-muted-foreground">{formatDateTime(a.createdAt)}</p>
            </div>
          </li>
        </ol>
      )}
      {a.latestTaskId ? (
        <p className="text-sm text-muted-foreground">
          Step-by-step agent activity is in the{" "}
          <Link href={`/agent/${a.latestTaskId}`} className="font-medium text-brand-strong hover:underline">
            control room
          </Link>
          .
        </p>
      ) : null}
    </section>
  );
}

function BackLink() {
  return (
    <Button asChild variant="ghost" size="sm" className="-ml-2">
      <Link href="/applications">
        <ArrowLeft aria-hidden="true" />
        All applications
      </Link>
    </Button>
  );
}
