"use client";

import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { InlineError, Notice } from "@/components/common/states";
import { useReviewDocument } from "@/features/documents/queries";
import { formatPercent, formatValue } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { DocumentDto, ProfileValue } from "@/types/api";
import { ShieldAlert } from "lucide-react";

type Choice = "accept" | "edit" | "reject" | "skip";

export function ReviewDialog({ document, open, onOpenChange }: { document: DocumentDto; open: boolean; onOpenChange: (open: boolean) => void }) {
  const review = useReviewDocument();
  const fields = React.useMemo(() => document.extraction?.fields ?? [], [document.extraction]);
  const [choices, setChoices] = React.useState<Record<string, Choice>>({});
  const [edits, setEdits] = React.useState<Record<string, string>>({});

  function submit() {
    const accept: { key: string; value: ProfileValue }[] = [];
    const reject: string[] = [];
    for (const f of fields) {
      const choice = choices[f.key] ?? "skip";
      if (choice === "accept") accept.push({ key: f.key, value: f.value });
      if (choice === "edit") accept.push({ key: f.key, value: (edits[f.key] ?? "").trim() || null });
      if (choice === "reject") reject.push(f.key);
    }
    review.mutate(
      { id: document.id, body: { accept, reject } },
      {
        onSuccess: () => {
          toast.success("Review saved", {
            description: accept.length ? `${accept.length} value${accept.length === 1 ? "" : "s"} added to your profile as confirmed.` : undefined,
          });
          onOpenChange(false);
        },
      },
    );
  }

  const decided = fields.filter((f) => (choices[f.key] ?? "skip") !== "skip").length;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Review values from {document.fileName}</DialogTitle>
          <DialogDescription>
            Accepted values are saved to your profile as confirmed by you. Rejected values are discarded. Anything you leave undecided stays
            here for later.
          </DialogDescription>
        </DialogHeader>
        <Notice tone="warning" icon={ShieldAlert}>
          Extraction is not verification. Check each value against the document itself.
        </Notice>
        <ul className="divide-y rounded-xl border">
          {fields.map((f) => {
            const choice = choices[f.key] ?? "skip";
            const linkable = f.profileField !== null;
            const group = `review-${document.id}-${f.key}`;
            return (
              <li key={f.key} className="space-y-3 px-4 py-4">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <div>
                    <p className="font-medium">{f.label}</p>
                    <p className="text-sm text-foreground/85">{formatValue(f.value)}</p>
                  </div>
                  <span className={cn("text-xs tabular-nums", f.confidence < 0.6 ? "font-medium text-warning" : "text-muted-foreground")}>
                    {formatPercent(f.confidence)} confidence
                  </span>
                </div>
                {linkable ? (
                  <RadioGroup
                    aria-label={`Decision for ${f.label}`}
                    value={choice}
                    onValueChange={(v) => setChoices((c) => ({ ...c, [f.key]: v as Choice }))}
                    className="flex flex-wrap gap-x-5 gap-y-2"
                  >
                    {(["accept", "edit", "reject", "skip"] as const).map((option) => (
                      <div key={option} className="flex items-center gap-2">
                        <RadioGroupItem value={option} id={`${group}-${option}`} />
                        <Label htmlFor={`${group}-${option}`} className="font-normal">
                          {option === "accept" ? "Accept" : option === "edit" ? "Edit, then accept" : option === "reject" ? "Reject" : "Decide later"}
                        </Label>
                      </div>
                    ))}
                  </RadioGroup>
                ) : (
                  <p className="text-[0.8125rem] text-muted-foreground">Not linked to a profile field, so there is nothing to accept.</p>
                )}
                {choice === "edit" ? (
                  <div className="space-y-1.5">
                    <Label htmlFor={`${group}-value`}>Corrected value</Label>
                    <Input
                      id={`${group}-value`}
                      defaultValue={f.value === null ? "" : String(f.value)}
                      onChange={(e) => setEdits((ed) => ({ ...ed, [f.key]: e.target.value }))}
                    />
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
        <InlineError error={review.error} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button onClick={submit} loading={review.isPending} disabled={decided === 0}>
            Save review
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
