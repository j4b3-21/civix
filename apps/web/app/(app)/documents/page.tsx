"use client";

import * as React from "react";
import { FolderLock, ShieldAlert } from "lucide-react";
import { Pager } from "@/components/common/pager";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState, ListSkeleton, Notice, PageHeader } from "@/components/common/states";
import { DocumentCard } from "@/features/documents/components/document-card";
import { DocumentUploader } from "@/features/documents/components/document-uploader";
import { useDocuments } from "@/features/documents/queries";
import { DOCUMENT_CATEGORY_LABELS } from "@/lib/labels";
import { DOCUMENT_CATEGORIES, type DocumentCategory } from "@/types/api";

const ALL = "ALL";

export default function DocumentsPage() {
  const [category, setCategory] = React.useState<DocumentCategory | typeof ALL>(ALL);
  const [page, setPage] = React.useState(0);
  const params = { page, ...(category === ALL ? {} : { category }) };
  const documents = useDocuments(params);

  return (
    <>
      <PageHeader
        title="Documents"
        description="Upload certificates and proofs once. CIVIX reads them and suggests profile values for you to accept or reject."
      />
      <Notice tone="warning" icon={ShieldAlert} title="Extraction is not verification." className="mb-8">
        CIVIX does not verify certificate authenticity. Values read from a document are suggestions until you review them, and the
        department that receives your application performs its own checks.
      </Notice>

      <div className="grid gap-8 lg:grid-cols-[20rem_minmax(0,1fr)]">
        <section aria-labelledby="upload-heading" className="h-fit rounded-2xl border bg-card p-5 shadow-card lg:sticky lg:top-24">
          <h2 id="upload-heading" className="mb-4 font-semibold">
            Upload a document
          </h2>
          <DocumentUploader />
        </section>

        <section aria-labelledby="library-heading" className="min-w-0">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h2 id="library-heading" className="font-semibold">
              Your documents
              {documents.data ? <span className="ml-2 font-normal text-muted-foreground tabular-nums">{documents.data.totalItems}</span> : null}
            </h2>
            <div className="w-56">
              <Select
                value={category}
                onValueChange={(v) => {
                  setCategory(v as DocumentCategory | typeof ALL);
                  setPage(0);
                }}
              >
                <SelectTrigger aria-label="Filter by document type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All types</SelectItem>
                  {DOCUMENT_CATEGORIES.map((c) => (
                    <SelectItem key={c} value={c}>
                      {DOCUMENT_CATEGORY_LABELS[c]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          {documents.isPending ? (
            <ListSkeleton rows={3} />
          ) : documents.isError ? (
            <ErrorState error={documents.error} onRetry={() => void documents.refetch()} />
          ) : documents.data.items.length === 0 ? (
            <EmptyState
              icon={FolderLock}
              title={category === ALL ? "No documents yet" : `No ${DOCUMENT_CATEGORY_LABELS[category].toLowerCase()} uploaded`}
              description="Start with the document a service asks for most often, such as an income certificate or identity proof."
            />
          ) : (
            <>
              <div className="grid gap-4 xl:grid-cols-2">
                {documents.data.items.map((doc) => (
                  <DocumentCard key={doc.id} document={doc} />
                ))}
              </div>
              <Pager page={page} totalPages={documents.data.totalPages} onPage={setPage} />
            </>
          )}
        </section>
      </div>
    </>
  );
}
