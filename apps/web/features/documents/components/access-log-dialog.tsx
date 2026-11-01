"use client";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ErrorState, ListSkeleton } from "@/components/common/states";
import { useDocumentAccessLog } from "@/features/documents/queries";
import { formatDateTime } from "@/lib/format";
import { humanize } from "@/lib/labels";
import type { DocumentDto } from "@/types/api";
import Link from "next/link";

const ACTOR = { USER: "You", AGENT: "CIVIX agent", SYSTEM: "CIVIX system" } as const;

export function AccessLogDialog({ document, open, onOpenChange }: { document: DocumentDto; open: boolean; onOpenChange: (open: boolean) => void }) {
  const log = useDocumentAccessLog(document.id, open);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Access history</DialogTitle>
          <DialogDescription>Every time {document.fileName} was uploaded, read, downloaded or sent to a portal.</DialogDescription>
        </DialogHeader>
        {log.isPending ? (
          <ListSkeleton rows={2} />
        ) : log.isError ? (
          <ErrorState error={log.error} onRetry={() => void log.refetch()} />
        ) : log.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">No access has been recorded yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>When</TableHead>
                <TableHead>Who</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Purpose</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {log.data.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="whitespace-nowrap">{formatDateTime(entry.occurredAt)}</TableCell>
                  <TableCell>
                    {ACTOR[entry.actorType]}
                    {entry.taskId ? (
                      <Link href={`/agent/${entry.taskId}`} className="block text-xs font-medium text-brand-strong hover:underline">
                        View run
                      </Link>
                    ) : null}
                  </TableCell>
                  <TableCell>{humanize(entry.action)}</TableCell>
                  <TableCell className="text-muted-foreground">{entry.purpose ? humanize(entry.purpose) : "Not recorded"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  );
}
