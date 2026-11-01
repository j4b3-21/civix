"use client";

import * as React from "react";
import Link from "next/link";
import { Bot } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Pager } from "@/components/common/pager";
import { EmptyState, ErrorState, ListSkeleton } from "@/components/common/states";
import { TaskStatusPill } from "@/components/common/status-badges";
import { useTasks } from "@/features/agent/queries";
import { formatDateTime } from "@/lib/format";

export function AgentRunsSection() {
  const [page, setPage] = React.useState(0);
  const tasks = useTasks(page);
  if (tasks.isPending) return <ListSkeleton rows={2} />;
  if (tasks.isError) return <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />;
  if (tasks.data.items.length === 0) {
    return <EmptyState icon={Bot} title="The agent has not run for you yet" description="Every run is listed here with its outcome and full event log." />;
  }
  return (
    <>
      <div className="rounded-2xl border bg-card shadow-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Service</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Started</TableHead>
              <TableHead>Finished</TableHead>
              <TableHead>
                <span className="sr-only">Open</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tasks.data.items.map((t) => (
              <TableRow key={t.id}>
                <TableCell className="font-medium">{t.serviceName}</TableCell>
                <TableCell>
                  <TaskStatusPill status={t.status} className="px-2 py-0.5 text-xs" />
                </TableCell>
                <TableCell className="whitespace-nowrap text-muted-foreground">{formatDateTime(t.startedAt ?? t.createdAt)}</TableCell>
                <TableCell className="whitespace-nowrap text-muted-foreground">{t.completedAt ? formatDateTime(t.completedAt) : "Not finished"}</TableCell>
                <TableCell className="text-right">
                  <Link href={`/agent/${t.id}`} className="text-sm font-medium text-brand-strong hover:underline">
                    Event log
                  </Link>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <Pager page={page} totalPages={tasks.data.totalPages} onPage={setPage} />
    </>
  );
}
