"use client";

import * as React from "react";
import { ChevronDown, CircleCheck, CircleHelp, CircleX, ExternalLink } from "lucide-react";
import { EligibilityBadge } from "@/components/common/status-badges";
import { MissingFieldsEditor } from "@/features/services/components/missing-fields-editor";
import { formatDate, formatDateTime, hostname } from "@/lib/format";
import { ELIGIBILITY_HINTS, RULE_RESULT_LABELS } from "@/lib/labels";
import { cn } from "@/lib/utils";
import type { EligibilityResult, RuleResult } from "@/types/api";

const RESULT_ICON: Record<RuleResult, typeof CircleCheck> = { PASS: CircleCheck, FAIL: CircleX, UNKNOWN: CircleHelp };
const RESULT_TONE: Record<RuleResult, string> = { PASS: "text-success", FAIL: "text-danger", UNKNOWN: "text-warning" };

export function SourceCitation({ url, title, retrievedAt }: { url: string | null | undefined; title?: string | null; retrievedAt?: string | null }) {
  if (!url) return <span className="text-[0.8125rem] text-warning">No official source recorded for this rule</span>;
  return (
    <span className="text-[0.8125rem] text-muted-foreground">
      Source:{" "}
      <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium text-brand-strong underline-offset-4 hover:underline">
        {title || hostname(url)}
        <ExternalLink className="size-3" aria-hidden="true" />
        <span className="sr-only">(opens in a new tab)</span>
      </a>
      {retrievedAt ? <>, retrieved {formatDate(retrievedAt)}</> : null}
    </span>
  );
}

/** Rule-by-rule explanation of a deterministic eligibility result. */
export function EligibilityPanel({
  result,
  onRecheck,
  defaultOpen = false,
}: {
  result: EligibilityResult;
  onRecheck?: () => void;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = React.useState(defaultOpen);
  const panelId = React.useId();
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <EligibilityBadge outcome={result.outcome} />
        <span className="text-[0.8125rem] text-muted-foreground">Checked {formatDateTime(result.evaluatedAt)}</span>
      </div>
      <p className="text-sm leading-relaxed text-foreground/85">{ELIGIBILITY_HINTS[result.outcome]}</p>
      {result.explanation ? <p className="text-sm leading-relaxed text-muted-foreground">{result.explanation}</p> : null}
      <MissingFieldsEditor missingKeys={result.missingFields} onSaved={onRecheck} />
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex items-center gap-1.5 rounded-lg text-sm font-medium text-brand-strong focus-visible:outline-2 focus-visible:outline-ring"
      >
        <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} aria-hidden="true" />
        {open ? "Hide" : "Show"} rule-by-rule explanation ({result.rules.length} {result.rules.length === 1 ? "rule" : "rules"})
      </button>
      {open ? (
        <ul id={panelId} className="divide-y rounded-xl border bg-card">
          {result.rules.map((rule) => {
            const Icon = RESULT_ICON[rule.result];
            return (
              <li key={`${rule.ruleId}-${rule.ruleVersion}`} className="flex gap-3 px-4 py-3.5">
                <Icon className={cn("mt-0.5 size-4.5 shrink-0", RESULT_TONE[rule.result])} aria-hidden="true" />
                <div className="min-w-0 space-y-1">
                  <p className="text-sm">
                    <span className={cn("font-medium", RESULT_TONE[rule.result])}>{RULE_RESULT_LABELS[rule.result]}.</span> {rule.explanation}
                  </p>
                  {rule.fieldStatus === "EXTRACTED" ? (
                    <p className="text-[0.8125rem] text-warning">Uses a value read from a document that you have not confirmed yet.</p>
                  ) : null}
                  <SourceCitation url={rule.evidence?.sourceUrl} title={rule.evidence?.sourceTitle} retrievedAt={rule.evidence?.retrievedAt} />
                </div>
              </li>
            );
          })}
        </ul>
      ) : null}
      <p className="text-xs text-muted-foreground">Rules engine {result.engineVersion}. The department makes the final decision.</p>
    </div>
  );
}
