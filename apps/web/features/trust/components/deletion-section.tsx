"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { InlineError } from "@/components/common/states";
import { useRequestDeletion } from "@/features/profile/queries";
import type { DeletionResult, DeletionScope } from "@/types/api";

const SCOPES: { value: DeletionScope; title: string; body: string }[] = [
  {
    value: "DOCUMENTS",
    title: "All documents",
    body: "Deletes every uploaded file from storage. Profile values you accepted from them stay, without the document link.",
  },
  {
    value: "PROFILE",
    title: "Profile details",
    body: "Clears every profile value. Pending approvals that used them are withdrawn.",
  },
  {
    value: "ACCOUNT",
    title: "Entire account",
    body: "Deletes your account, documents, profile, applications and permissions, and signs you out. This cannot be undone.",
  },
];

export function DeletionSection() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const deletion = useRequestDeletion();
  const [scope, setScope] = React.useState<DeletionScope | "">("");
  const [confirm, setConfirm] = React.useState("");
  const [result, setResult] = React.useState<DeletionResult | null>(null);
  const ready = scope !== "" && confirm === "DELETE";

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!ready) return;
    deletion.mutate(scope, {
      onSuccess: (r) => {
        setConfirm("");
        if (r.accountDeleted) {
          queryClient.clear();
          router.replace("/");
          return;
        }
        setResult(r);
      },
    });
  }

  return (
    <form onSubmit={submit} className="space-y-5 rounded-2xl border border-danger/25 bg-card p-5 shadow-card sm:p-6">
      <RadioGroup value={scope} onValueChange={(v) => setScope(v as DeletionScope)} aria-label="What to delete">
        {SCOPES.map((s) => (
          <div key={s.value} className="flex gap-3 rounded-xl border px-4 py-3 has-[[data-state=checked]]:border-danger/50 has-[[data-state=checked]]:bg-danger-soft/50">
            <RadioGroupItem value={s.value} id={`delete-${s.value}`} className="mt-0.5" />
            <Label htmlFor={`delete-${s.value}`} className="block space-y-1 leading-normal">
              <span className="block font-medium">{s.title}</span>
              <span className="block text-sm font-normal text-muted-foreground">{s.body}</span>
            </Label>
          </div>
        ))}
      </RadioGroup>
      <div className="max-w-xs space-y-1.5">
        <Label htmlFor="delete-confirm">Type DELETE to confirm</Label>
        <Input id="delete-confirm" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" spellCheck={false} />
      </div>
      <InlineError error={deletion.error} />
      {result ? (
        <p role="status" className="text-sm text-success">
          Deleted {result.deletedDocuments} {result.deletedDocuments === 1 ? "document" : "documents"} and {result.deletedProfileFields} profile{" "}
          {result.deletedProfileFields === 1 ? "value" : "values"}.
        </p>
      ) : null}
      <Button type="submit" variant="destructive" disabled={!ready} loading={deletion.isPending}>
        <Trash2 aria-hidden="true" />
        {scope === "ACCOUNT" ? "Delete my account" : scope === "PROFILE" ? "Delete profile details" : scope === "DOCUMENTS" ? "Delete all documents" : "Delete"}
      </Button>
    </form>
  );
}
