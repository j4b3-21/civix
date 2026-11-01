"use client";

import * as React from "react";
import { AlertTriangle, Clock, FileText, ShieldCheck, ShieldX } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useDecideApproval } from "@/features/approvals/queries";
import { useNow } from "@/hooks/use-now";
import { isApiError } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { formatCountdown, formatValue } from "@/lib/format";
import { ACTION_CLASS_LABELS, APPROVAL_KIND_LABELS, DOCUMENT_CATEGORY_LABELS } from "@/lib/labels";
import { cn } from "@/lib/utils";
import type { ApprovalDecision, ApprovalDto, DocumentCategory } from "@/types/api";

type Outcome =
  | { kind: "decided"; decision: ApprovalDecision }
  | { kind: "expired" }
  | { kind: "dataChanged" }
  | { kind: "notPending" }
  | { kind: "error"; message: string };

function outcomeFromError(error: unknown): Outcome {
  if (isApiError(error)) {
    if (error.code === "APPROVAL_EXPIRED" || error.status === 410) return { kind: "expired" };
    if (error.code === "APPROVAL_DATA_CHANGED") return { kind: "dataChanged" };
    if (error.code === "APPROVAL_NOT_PENDING") return { kind: "notPending" };
  }
  return { kind: "error", message: errorMessage(error) };
}

const OUTCOME_COPY: Record<Exclude<Outcome["kind"], "decided" | "error">, { title: string; body: string }> = {
  expired: {
    title: "This approval request expired",
    body: "Nothing was done with it. If the run continues, CIVIX will ask again with a fresh request.",
  },
  dataChanged: {
    title: "Your information changed",
    body: "Your profile or documents changed after this request was prepared, so it was withdrawn. CIVIX will prepare a new request with the current information.",
  },
  notPending: {
    title: "This request was already decided",
    body: "It was approved, rejected or withdrawn elsewhere. The control room has been refreshed.",
  },
};

export function ApprovalCard({ approval, taskId }: { approval: ApprovalDto; taskId: string }) {
  const decide = useDecideApproval(taskId);
  const [reason, setReason] = React.useState("");
  const [outcome, setOutcome] = React.useState<Outcome | null>(null);
  const [pending, setPending] = React.useState<ApprovalDecision | null>(null);
  const expiresAt = Date.parse(approval.expiresAt);
  const now = useNow(1000, approval.status === "PENDING" && outcome === null);
  const secondsLeft = Number.isNaN(expiresAt) ? Infinity : Math.max(0, (expiresAt - now) / 1000);
  const expired = approval.status === "EXPIRED" || secondsLeft <= 0 || outcome?.kind === "expired";
  const finalSubmission = approval.kind === "FINAL_SUBMISSION";
  const closed = expired || approval.status !== "PENDING" || (outcome !== null && outcome.kind !== "error");
  const reasonId = `approval-${approval.id}-reason`;

  function submit(decision: ApprovalDecision) {
    setPending(decision);
    setOutcome(null);
    const trimmed = reason.trim();
    decide.mutate(
      { approvalId: approval.id, body: { decision, dataVersion: approval.dataVersion, ...(trimmed ? { reason: trimmed } : {}) } },
      {
        onSuccess: () => {
          setOutcome({ kind: "decided", decision });
          toast.success(decision === "APPROVE" ? "Approved" : "Rejected", {
            description: decision === "APPROVE" ? "The agent will continue." : "The agent will stop this step.",
          });
        },
        onError: (error) => setOutcome(outcomeFromError(error)),
        onSettled: () => setPending(null),
      },
    );
  }

  const notice = outcome && outcome.kind !== "decided" && outcome.kind !== "error" ? OUTCOME_COPY[outcome.kind] : expired ? OUTCOME_COPY.expired : null;

  return (
    <section
      aria-labelledby={`approval-${approval.id}-title`}
      className={cn("rounded-2xl border bg-card shadow-raised", finalSubmission ? "border-brand/50 ring-1 ring-brand/30" : "border-brand/30")}
    >
      <div className="space-y-3 border-b px-5 pt-5 pb-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="inline-flex items-center gap-2 text-sm font-medium text-brand-strong">
            <span aria-hidden="true" className="size-2.5 rounded-[3px] bg-brand" />
            Your approval is needed
          </span>
          {!closed ? (
            <span
              className={cn("inline-flex items-center gap-1.5 text-[0.8125rem] font-medium tabular-nums", secondsLeft < 120 ? "text-danger" : "text-muted-foreground")}
              aria-label={`Expires in ${formatCountdown(secondsLeft)}`}
            >
              <Clock className="size-3.5" aria-hidden="true" />
              Expires in {formatCountdown(secondsLeft)}
            </span>
          ) : null}
        </div>
        <h2 id={`approval-${approval.id}-title`} className="text-title font-semibold">
          {APPROVAL_KIND_LABELS[approval.kind]}
        </h2>
        <p className="text-sm leading-relaxed text-foreground/85">{approval.summary}</p>
        <div className="flex flex-wrap gap-1.5">
          <Badge variant={approval.actionClass === "CONSEQUENTIAL" ? "danger" : approval.actionClass === "SENSITIVE" ? "warning" : "secondary"}>
            {ACTION_CLASS_LABELS[approval.actionClass]}
          </Badge>
          {approval.details.portal ? <Badge variant="outline">Portal: {approval.details.portal}</Badge> : null}
        </div>
        {finalSubmission ? (
          <p className="flex gap-2 rounded-xl bg-brand-soft px-3 py-2.5 text-sm leading-relaxed">
            <AlertTriangle className="mt-0.5 size-4 shrink-0 text-brand-strong" aria-hidden="true" />
            Approving submits this application on the portal. This cannot be undone from CIVIX.
          </p>
        ) : null}
      </div>

      {approval.details.fields.length > 0 ? (
        <div className="px-5 py-4">
          <h3 className="mb-2 text-sm font-medium">Values the agent will use</h3>
          <div className="max-h-72 overflow-y-auto rounded-xl border">
            <table className="w-full text-sm">
              <caption className="sr-only">Values the agent will enter on the portal</caption>
              <thead className="sr-only">
                <tr>
                  <th scope="col">Field</th>
                  <th scope="col">Value</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {approval.details.fields.map((f, i) => (
                  <tr key={`${f.label}-${i}`}>
                    <th scope="row" className="w-2/5 px-3 py-2 text-left font-normal text-muted-foreground">
                      {f.label}
                    </th>
                    <td className="px-3 py-2 break-words">{formatValue(f.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {approval.details.documents.length > 0 ? (
        <div className="px-5 pb-4">
          <h3 className="mb-2 text-sm font-medium">Documents</h3>
          <ul className="space-y-1.5">
            {approval.details.documents.map((d) => (
              <li key={d.documentId} className="flex items-center gap-2 text-sm">
                <FileText className="size-4 text-muted-foreground" aria-hidden="true" />
                {d.fileName ?? DOCUMENT_CATEGORY_LABELS[d.category as DocumentCategory] ?? d.category}
                {d.fileName ? <span className="text-muted-foreground">({DOCUMENT_CATEGORY_LABELS[d.category as DocumentCategory] ?? d.category})</span> : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="space-y-3 border-t px-5 py-4">
        {notice ? (
          <div role="status" className="rounded-xl bg-muted px-3 py-2.5 text-sm">
            <p className="font-medium">{notice.title}</p>
            <p className="mt-0.5 leading-relaxed text-muted-foreground">{notice.body}</p>
          </div>
        ) : outcome?.kind === "decided" ? (
          <p role="status" className="text-sm font-medium text-success">
            {outcome.decision === "APPROVE" ? "Approved. The agent will continue." : "Rejected. The agent will not perform this step."}
          </p>
        ) : (
          <>
            <div className="space-y-1.5">
              <Label htmlFor={reasonId}>Note for the record (optional)</Label>
              <Textarea id={reasonId} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} rows={2} disabled={decide.isPending} />
            </div>
            {outcome?.kind === "error" ? (
              <p role="alert" className="text-sm text-danger">
                {outcome.message}
              </p>
            ) : null}
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button className="flex-1" variant="brand" onClick={() => submit("APPROVE")} loading={pending === "APPROVE"} disabled={decide.isPending || closed}>
                <ShieldCheck aria-hidden="true" />
                {finalSubmission ? "Approve and submit" : "Approve"}
              </Button>
              <Button className="flex-1" variant="outline" onClick={() => submit("REJECT")} loading={pending === "REJECT"} disabled={decide.isPending || closed}>
                <ShieldX aria-hidden="true" />
                Reject
              </Button>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
