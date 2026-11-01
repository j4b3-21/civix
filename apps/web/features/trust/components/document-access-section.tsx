"use client";

import Link from "next/link";
import { FolderLock } from "lucide-react";
import { EmptyState, ErrorState, ListSkeleton } from "@/components/common/states";
import { useAuditEvents } from "@/features/dashboard/queries";
import { formatDateTime } from "@/lib/format";
import { humanize } from "@/lib/labels";
import type { AuditEventDto } from "@/types/api";

interface Aggregate {
  resourceId: string;
  count: number;
  last: AuditEventDto;
  actions: Set<string>;
  denied: number;
}

/** Aggregates GET /audit/events?category=DOCUMENT per document. */
export function aggregateDocumentAccess(events: readonly AuditEventDto[]): Aggregate[] {
  const map = new Map<string, Aggregate>();
  for (const e of events) {
    const id = e.resourceId ?? "unknown";
    const agg = map.get(id);
    if (!agg) {
      map.set(id, { resourceId: id, count: 1, last: e, actions: new Set([e.action]), denied: e.outcome === "DENIED" ? 1 : 0 });
    } else {
      agg.count += 1;
      agg.actions.add(e.action);
      if (e.outcome === "DENIED") agg.denied += 1;
      if (Date.parse(e.occurredAt) > Date.parse(agg.last.occurredAt)) agg.last = e;
    }
  }
  return [...map.values()].sort((a, b) => Date.parse(b.last.occurredAt) - Date.parse(a.last.occurredAt));
}

export function DocumentAccessSection() {
  const audit = useAuditEvents({ category: "DOCUMENT", size: 100 });
  if (audit.isPending) return <ListSkeleton rows={2} />;
  if (audit.isError) return <ErrorState error={audit.error} onRetry={() => void audit.refetch()} />;
  const rows = aggregateDocumentAccess(audit.data.items);
  if (rows.length === 0) {
    return <EmptyState icon={FolderLock} title="No document access recorded" description="Uploads, downloads, reads and portal uploads of your documents are listed here." />;
  }
  return (
    <div className="space-y-2">
      <ul className="divide-y rounded-2xl border bg-card shadow-card">
        {rows.map((r) => (
          <li key={r.resourceId} className="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
            <div className="min-w-0 space-y-0.5">
              <p className="text-sm font-medium">{r.last.summary}</p>
              <p className="text-[0.8125rem] text-muted-foreground">
                {[...r.actions].map((a) => humanize(a)).join(", ")}
                {r.denied > 0 ? <span className="text-danger">, {r.denied} denied</span> : null}
              </p>
            </div>
            <div className="shrink-0 text-[0.8125rem] text-muted-foreground sm:text-right">
              <p className="tabular-nums">
                {r.count} {r.count === 1 ? "access" : "accesses"}, last {formatDateTime(r.last.occurredAt)}
              </p>
              {r.resourceId !== "unknown" ? (
                <Link href={`/documents#doc-${r.resourceId}`} className="font-medium text-brand-strong hover:underline">
                  Open document
                </Link>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
      {audit.data.totalItems > audit.data.items.length ? (
        <p className="text-[0.8125rem] text-muted-foreground">Showing the latest {audit.data.items.length} of {audit.data.totalItems} access events.</p>
      ) : null}
    </div>
  );
}
