"use client";

import * as React from "react";
import Link from "next/link";
import { Hand } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Pager } from "@/components/common/pager";
import { EmptyState, ErrorState, ListSkeleton } from "@/components/common/states";
import { ApprovalStatusBadge } from "@/components/common/status-badges";
import { useApprovals } from "@/features/approvals/queries";
import { formatDateTime } from "@/lib/format";
import { APPROVAL_KIND_LABELS, APPROVAL_STATUS_LABELS } from "@/lib/labels";
import type { ApprovalStatus } from "@/types/api";

const DECIDED: ApprovalStatus[] = ["APPROVED", "CONSUMED", "REJECTED", "EXPIRED", "INVALIDATED"];

function ApprovalList({ status }: { status: ApprovalStatus }) {
  const [page, setPage] = React.useState(0);
  const approvals = useApprovals({ status, page });
  if (approvals.isPending) return <ListSkeleton rows={2} />;
  if (approvals.isError) return <ErrorState error={approvals.error} onRetry={() => void approvals.refetch()} />;
  if (approvals.data.items.length === 0) {
    return (
      <EmptyState
        icon={Hand}
        title={status === "PENDING" ? "No approvals are waiting" : `No ${APPROVAL_STATUS_LABELS[status].toLowerCase()} approvals`}
        description="Approvals are requested before CIVIX fills a form, uploads a document or submits an application."
      />
    );
  }
  return (
    <>
      <ul className="divide-y rounded-2xl border bg-card shadow-card">
        {approvals.data.items.map((a) => (
          <li key={a.id} className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:gap-6">
            <div className="min-w-0 flex-1 space-y-0.5">
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-medium">{APPROVAL_KIND_LABELS[a.kind]}</p>
                <ApprovalStatusBadge status={a.status} />
              </div>
              <p className="text-sm text-muted-foreground">
                {a.serviceName}. {a.summary}
              </p>
              <p className="text-[0.8125rem] text-muted-foreground">
                Requested {formatDateTime(a.createdAt)}
                {a.decidedAt ? `, decided ${formatDateTime(a.decidedAt)}` : `, expires ${formatDateTime(a.expiresAt)}`}
                {a.decisionReason ? `. Note: ${a.decisionReason}` : ""}
              </p>
            </div>
            <Link href={`/agent/${a.taskId}`} className="shrink-0 text-sm font-medium text-brand-strong hover:underline">
              {a.status === "PENDING" ? "Review in control room" : "View run"}
            </Link>
          </li>
        ))}
      </ul>
      <Pager page={page} totalPages={approvals.data.totalPages} onPage={setPage} />
    </>
  );
}

export function ApprovalsSection() {
  const [decided, setDecided] = React.useState<ApprovalStatus>("APPROVED");
  return (
    <Tabs defaultValue="pending">
      <TabsList>
        <TabsTrigger value="pending">Pending</TabsTrigger>
        <TabsTrigger value="decided">Completed</TabsTrigger>
      </TabsList>
      <TabsContent value="pending" className="mt-4">
        <ApprovalList status="PENDING" />
      </TabsContent>
      <TabsContent value="decided" className="mt-4 space-y-4">
        <div className="w-56">
          <Select value={decided} onValueChange={(v) => setDecided(v as ApprovalStatus)}>
            <SelectTrigger aria-label="Approval outcome">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {DECIDED.map((s) => (
                <SelectItem key={s} value={s}>
                  {APPROVAL_STATUS_LABELS[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <ApprovalList key={decided} status={decided} />
      </TabsContent>
    </Tabs>
  );
}
