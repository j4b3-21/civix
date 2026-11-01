"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/common/states";
import { TaskStatusPill } from "@/components/common/status-badges";
import { ApprovalCard } from "@/features/agent/components/approval-card";
import { BrowserView } from "@/features/agent/components/browser-view";
import { CancelTaskButton } from "@/features/agent/components/cancel-task-button";
import { CitizenInputCard } from "@/features/agent/components/citizen-input-card";
import { ConnectionIndicator } from "@/features/agent/components/connection-indicator";
import { ExecutionTimeline } from "@/features/agent/components/execution-timeline";
import { TaskResult } from "@/features/agent/components/task-result";
import { TERMINAL_TASK_STATUSES, useTask } from "@/features/agent/queries";
import { useTaskEvents } from "@/hooks/use-task-events";
import { formatDateTime } from "@/lib/format";
import { AGENT_LABELS, humanize } from "@/lib/labels";

/** The Agent Control Room. Mount with `key={taskId}`. */
export function ControlRoom({ taskId }: { taskId: string }) {
  const live = useTaskEvents(taskId);
  const task = useTask(taskId, { liveConnected: live.connection === "open" });

  if (task.isPending) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading control room">
        <Skeleton className="h-8 w-1/2" />
        <div className="grid gap-6 lg:grid-cols-2">
          <Skeleton className="h-96 rounded-2xl" />
          <Skeleton className="h-96 rounded-2xl" />
        </div>
      </div>
    );
  }
  if (task.isError) {
    return <ErrorState error={task.error} title="This run could not be loaded" onRetry={() => void task.refetch()} />;
  }

  const t = task.data;
  const terminal = TERMINAL_TASK_STATUSES.has(t.status);
  const approval = t.pendingApproval && t.pendingApproval.status === "PENDING" ? t.pendingApproval : null;
  const citizenInput = t.status === "WAITING_CITIZEN" || t.pendingCitizenInput ? t.pendingCitizenInput : null;

  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={`/applications/${t.applicationId}`}>
          <ArrowLeft aria-hidden="true" />
          Application
        </Link>
      </Button>

      <header className="flex flex-col gap-4 rounded-2xl border bg-card px-5 py-5 shadow-card sm:px-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0 space-y-2">
          <p className="text-sm text-muted-foreground">Agent control room</p>
          <h1 className="text-[1.5rem] leading-tight font-semibold tracking-tight">{t.serviceName}</h1>
          <dl className="flex flex-wrap gap-x-5 gap-y-1 text-[0.8125rem] text-muted-foreground">
            <div className="flex gap-1">
              <dt>Agent:</dt>
              <dd className="font-medium text-foreground">{t.currentAgent ? AGENT_LABELS[t.currentAgent] : "Not started"}</dd>
            </div>
            <div className="flex gap-1">
              <dt>Step:</dt>
              <dd className="font-medium text-foreground">{t.currentStep ? humanize(t.currentStep) : "None yet"}</dd>
            </div>
            {t.recoveryAttempts > 0 ? (
              <div className="flex gap-1">
                <dt>Recoveries:</dt>
                <dd className="font-medium text-warning tabular-nums">{t.recoveryAttempts}</dd>
              </div>
            ) : null}
            <div className="flex gap-1">
              <dt>Started:</dt>
              <dd>{formatDateTime(t.startedAt ?? t.createdAt)}</dd>
            </div>
          </dl>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <ConnectionIndicator state={live.connection} attempt={live.reconnectAttempt} />
          <TaskStatusPill status={t.status} />
          {!terminal ? <CancelTaskButton taskId={t.id} /> : null}
        </div>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
        <div className="order-2 lg:order-1">
          <section aria-labelledby="timeline-heading" className="rounded-2xl border bg-card shadow-card">
            <div className="flex items-center justify-between border-b px-5 py-3.5">
              <h2 id="timeline-heading" className="text-sm font-semibold">
                Execution timeline
              </h2>
              <span className="text-xs text-muted-foreground tabular-nums">{live.events.length} events</span>
            </div>
            <div className="px-5 pt-5">
              <ExecutionTimeline events={live.events} />
            </div>
          </section>
        </div>

        <div className="order-1 space-y-6 lg:sticky lg:top-24 lg:order-2 lg:h-fit">
          {approval ? <ApprovalCard key={approval.id} approval={approval} taskId={t.id} /> : null}
          {citizenInput ? <CitizenInputCard key={citizenInput.requestId} taskId={t.id} input={citizenInput} /> : null}
          {terminal ? <TaskResult task={t} events={live.events} onRetried={live.restart} /> : null}
          {!approval && !citizenInput && !terminal ? (
            <p className="rounded-2xl border border-dashed px-5 py-4 text-sm text-muted-foreground">
              Nothing needs you right now. If the agent needs an approval, a one-time password or a CAPTCHA, it pauses and asks here.
            </p>
          ) : null}
          <BrowserView task={t} events={live.events} />
        </div>
      </div>
    </div>
  );
}
