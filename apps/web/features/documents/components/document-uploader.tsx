"use client";

import * as React from "react";
import { FileUp, UploadCloud } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { InlineError } from "@/components/common/states";
import { ConsentPrompt, consentPurposeFromError } from "@/features/profile/components/consent-prompt";
import { ACCEPT_ATTRIBUTE, precheckFile } from "@/features/documents/precheck";
import { useUploadDocument } from "@/features/documents/queries";
import { formatBytes } from "@/lib/format";
import { DOCUMENT_CATEGORY_LABELS } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { DOCUMENT_CATEGORIES, type DocumentCategory, type DocumentDto } from "@/types/api";

export function DocumentUploader({ onUploaded, defaultCategory }: { onUploaded?: (doc: DocumentDto) => void; defaultCategory?: DocumentCategory }) {
  const upload = useUploadDocument();
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [category, setCategory] = React.useState<DocumentCategory | "">(defaultCategory ?? "");
  const [file, setFile] = React.useState<File | null>(null);
  const [precheckError, setPrecheckError] = React.useState<string | null>(null);
  const [categoryError, setCategoryError] = React.useState<string | null>(null);
  const [dragging, setDragging] = React.useState(false);

  function choose(candidate: File | undefined) {
    upload.reset();
    if (!candidate) return;
    const result = precheckFile(candidate);
    if (!result.ok) {
      setFile(null);
      setPrecheckError(result.message);
      return;
    }
    setPrecheckError(null);
    setFile(candidate);
  }

  function submit() {
    if (!category) {
      setCategoryError("Choose what kind of document this is");
      return;
    }
    if (!file) {
      setPrecheckError("Choose a file to upload");
      return;
    }
    upload.mutate(
      { file, category },
      {
        onSuccess: (doc) => {
          toast.success("Document uploaded", { description: "CIVIX is reading it now. Review the values before they reach your profile." });
          setFile(null);
          if (inputRef.current) inputRef.current.value = "";
          onUploaded?.(doc);
        },
      },
    );
  }

  const consentPurpose = consentPurposeFromError(upload.error, "DOCUMENT_PROCESSING");

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="upload-category">Document type</Label>
        <Select
          value={category}
          onValueChange={(v) => {
            setCategory(v as DocumentCategory);
            setCategoryError(null);
          }}
        >
          <SelectTrigger id="upload-category" aria-invalid={categoryError ? true : undefined} aria-describedby={categoryError ? "upload-category-error" : undefined}>
            <SelectValue placeholder="Choose a type" />
          </SelectTrigger>
          <SelectContent>
            {DOCUMENT_CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>
                {DOCUMENT_CATEGORY_LABELS[c]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {categoryError ? (
          <p id="upload-category-error" className="text-[0.8125rem] font-medium text-danger">
            {categoryError}
          </p>
        ) : null}
      </div>

      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          choose(e.dataTransfer.files[0]);
        }}
        className={cn(
          "flex flex-col items-center gap-3 rounded-2xl border-2 border-dashed px-6 py-8 text-center transition-colors",
          dragging ? "border-brand bg-brand-soft" : "border-input bg-card",
        )}
      >
        <UploadCloud className="size-7 text-brand-strong" aria-hidden="true" />
        <div className="space-y-1">
          <p className="text-sm font-medium">{file ? file.name : "Drop a file here"}</p>
          <p className="text-[0.8125rem] text-muted-foreground">{file ? formatBytes(file.size) : "PDF, PNG or JPEG, up to 10 MB"}</p>
        </div>
        <input
          ref={inputRef}
          id="upload-file"
          type="file"
          accept={ACCEPT_ATTRIBUTE}
          className="sr-only"
          aria-describedby={precheckError ? "upload-file-error" : undefined}
          onChange={(e) => choose(e.target.files?.[0])}
        />
        <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()}>
          <FileUp aria-hidden="true" />
          {file ? "Choose a different file" : "Choose file"}
        </Button>
        <label htmlFor="upload-file" className="sr-only">
          Document file
        </label>
      </div>
      {precheckError ? (
        <p id="upload-file-error" role="alert" className="text-[0.8125rem] font-medium text-danger">
          {precheckError}
        </p>
      ) : null}

      {consentPurpose ? <ConsentPrompt purpose={consentPurpose} onGranted={submit} /> : <InlineError error={upload.error} />}

      <Button type="button" onClick={submit} loading={upload.isPending} disabled={!file}>
        Upload document
      </Button>
    </div>
  );
}
