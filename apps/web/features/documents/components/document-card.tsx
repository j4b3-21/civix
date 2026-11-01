"use client";

import * as React from "react";
import { AlertTriangle, Download, FileText, History, ListChecks, MoreHorizontal, RefreshCw, Trash2 } from "lucide-react";
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
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { ExtractionBadge } from "@/components/common/status-badges";
import { AccessLogDialog } from "@/features/documents/components/access-log-dialog";
import { ReviewDialog } from "@/features/documents/components/review-dialog";
import { useDeleteDocument, useReExtractDocument } from "@/features/documents/queries";
import { downloadDocument } from "@/lib/api/documents";
import { errorMessage } from "@/lib/api/errors";
import { formatBytes, formatDate, formatPercent, formatValue } from "@/lib/format";
import { DOCUMENT_CATEGORY_LABELS } from "@/lib/labels";
import { cn } from "@/lib/utils";
import type { DocumentDto } from "@/types/api";

const SEVERITY_TONE = { INFO: "text-info", WARNING: "text-warning", ERROR: "text-danger" } as const;

export function DocumentCard({ document }: { document: DocumentDto }) {
  const [reviewOpen, setReviewOpen] = React.useState(false);
  const [logOpen, setLogOpen] = React.useState(false);
  const [deleteOpen, setDeleteOpen] = React.useState(false);
  const [downloading, setDownloading] = React.useState(false);
  const remove = useDeleteDocument();
  const reExtract = useReExtractDocument();
  const extraction = document.extraction;
  const fields = extraction?.fields ?? [];
  const reviewable = document.extractionStatus === "COMPLETED" && fields.length > 0;
  const busy = document.extractionStatus === "PENDING" || document.extractionStatus === "PROCESSING";

  async function download() {
    setDownloading(true);
    try {
      const { blob, fileName } = await downloadDocument(document.id);
      const url = URL.createObjectURL(blob);
      const a = window.document.createElement("a");
      a.href = url;
      a.download = fileName ?? document.fileName;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      toast.error("Download failed", { description: errorMessage(error) });
    } finally {
      setDownloading(false);
    }
  }

  return (
    <article id={`doc-${document.id}`} className="flex scroll-mt-24 flex-col rounded-2xl border bg-card shadow-card" aria-labelledby={`doc-${document.id}-title`}>
      <div className="flex items-start gap-3 px-5 pt-5">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-muted text-muted-foreground">
          <FileText className="size-5" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <h3 id={`doc-${document.id}-title`} className="truncate font-medium" title={document.fileName}>
            {document.fileName}
          </h3>
          <p className="text-[0.8125rem] text-muted-foreground">
            {DOCUMENT_CATEGORY_LABELS[document.category]}, {formatBytes(document.sizeBytes)}, uploaded {formatDate(document.uploadedAt)}
          </p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${document.fileName}`}>
              <MoreHorizontal />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={() => void download()} disabled={downloading}>
              <Download aria-hidden="true" />
              Download
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={() => setLogOpen(true)}>
              <History aria-hidden="true" />
              Access history
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={busy || reExtract.isPending}
              onSelect={() =>
                reExtract.mutate(document.id, {
                  onSuccess: () => toast.success("Reading the document again"),
                  onError: (e) => toast.error("Could not restart reading", { description: errorMessage(e) }),
                })
              }
            >
              <RefreshCw aria-hidden="true" />
              Read again
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onSelect={() => setDeleteOpen(true)}>
              <Trash2 aria-hidden="true" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <div className="flex flex-wrap gap-2 px-5 pt-4">
        <ExtractionBadge status={document.extractionStatus} />
        <Badge variant={document.reviewStatus === "REVIEWED" ? "brand" : "outline"}>
          {document.reviewStatus === "REVIEWED" ? "Reviewed by you" : "Not reviewed"}
        </Badge>
        {extraction && !extraction.classification.declaredCategoryMatches ? (
          <Badge variant="warning">
            <AlertTriangle aria-hidden="true" />
            Looks like {DOCUMENT_CATEGORY_LABELS[extraction.classification.category].toLowerCase()}
          </Badge>
        ) : null}
      </div>

      <div className="flex-1 space-y-3 px-5 py-4 text-sm">
        {document.extractionStatus === "FAILED" ? (
          <p className="text-danger">{extraction?.errorMessage ?? "CIVIX could not read this document. Try reading it again or upload a clearer copy."}</p>
        ) : busy ? (
          <p className="text-muted-foreground" aria-live="polite">
            Reading the document. This page updates automatically.
          </p>
        ) : fields.length === 0 ? (
          <p className="text-muted-foreground">No values were found in this document.</p>
        ) : (
          <dl className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1.5">
            {fields.slice(0, 4).map((f) => (
              <React.Fragment key={f.key}>
                <dt className="truncate text-muted-foreground">
                  {f.label}: <span className="text-foreground">{formatValue(f.value)}</span>
                </dt>
                <dd className={cn("text-right text-xs tabular-nums", f.confidence < 0.6 ? "font-medium text-warning" : "text-muted-foreground")}>
                  {formatPercent(f.confidence)}
                </dd>
              </React.Fragment>
            ))}
            {fields.length > 4 ? <dd className="col-span-2 text-xs text-muted-foreground">and {fields.length - 4} more</dd> : null}
          </dl>
        )}
        {extraction?.discrepancies.length ? (
          <ul className="space-y-1.5 rounded-xl bg-muted/60 px-3 py-2.5">
            {extraction.discrepancies.map((d, i) => (
              <li key={`${d.field}-${i}`} className="flex gap-2 text-[0.8125rem]">
                <AlertTriangle className={cn("mt-0.5 size-3.5 shrink-0", SEVERITY_TONE[d.severity])} aria-hidden="true" />
                <span>
                  <span className="sr-only">{d.severity.toLowerCase()}: </span>
                  {d.message}
                </span>
              </li>
            ))}
          </ul>
        ) : null}
        {extraction?.warnings.length ? (
          <ul className="space-y-1 text-[0.8125rem] text-muted-foreground">
            {extraction.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        ) : null}
      </div>

      {reviewable ? (
        <div className="border-t px-5 py-3">
          <Button variant={document.reviewStatus === "REVIEWED" ? "ghost" : "outline"} size="sm" onClick={() => setReviewOpen(true)}>
            <ListChecks aria-hidden="true" />
            {document.reviewStatus === "REVIEWED" ? "Review again" : "Review values"}
          </Button>
        </div>
      ) : null}

      {reviewable ? <ReviewDialog document={document} open={reviewOpen} onOpenChange={setReviewOpen} /> : null}
      <AccessLogDialog document={document} open={logOpen} onOpenChange={setLogOpen} />
      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {document.fileName}?</AlertDialogTitle>
            <AlertDialogDescription>
              The file is removed from storage. Profile values you accepted from it stay in your profile but lose their link to this
              document. Its access history is kept.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep document</AlertDialogCancel>
            <AlertDialogAction
              destructive
              onClick={() =>
                remove.mutate(document.id, {
                  onSuccess: () => toast.success("Document deleted"),
                  onError: (e) => toast.error("Could not delete the document", { description: errorMessage(e) }),
                })
              }
            >
              Delete document
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </article>
  );
}
