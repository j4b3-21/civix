"use client";

import Link from "next/link";
import { ExternalLink } from "lucide-react";
import { StartApplicationButton } from "@/features/applications/components/start-application-button";
import { EligibilityCheck } from "@/features/services/components/eligibility-check";
import { ServiceBadges } from "@/features/services/components/service-badges";
import { jurisdictionText } from "@/features/services/components/service-card";
import { hostname } from "@/lib/format";
import { SERVICE_CATEGORY_LABELS } from "@/lib/labels";
import type { DiscoveryResultItem } from "@/types/api";

export function DiscoveryResult({ item, stateNames }: { item: DiscoveryResultItem; stateNames: Map<string, string> }) {
  const s = item.service;
  return (
    <article className="space-y-4 rounded-2xl border bg-card p-5 shadow-card" aria-labelledby={`result-${s.id}`}>
      <div className="space-y-1">
        <p className="text-[0.8125rem] text-muted-foreground">
          {SERVICE_CATEGORY_LABELS[s.category]} <span aria-hidden="true">/</span> {jurisdictionText(s, stateNames)}
        </p>
        <h3 id={`result-${s.id}`} className="text-[1.0625rem] font-semibold">
          <Link href={`/services/${s.id}`} className="hover:underline focus-visible:outline-2 focus-visible:outline-ring">
            {s.name}
          </Link>
        </h3>
        {s.department || s.ministry ? <p className="text-sm text-muted-foreground">{s.department ?? s.ministry}</p> : null}
      </div>
      <p className="text-sm leading-relaxed text-foreground/85">{s.summary}</p>
      <ServiceBadges service={s} showEligibility={false} />
      {item.matchReasons.length > 0 ? (
        <div>
          <p className="text-[0.8125rem] font-medium">Why this matched</p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5 text-[0.8125rem] text-muted-foreground marker:text-brand">
            {item.matchReasons.map((r) => (
              <li key={r}>{r}</li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="border-t pt-4">
        <EligibilityCheck serviceId={s.id} initialResult={item.eligibility} />
      </div>
      <div className="flex flex-wrap items-center gap-2 border-t pt-4">
        <StartApplicationButton serviceId={s.id} variant="outline" />
        {s.officialUrl ? (
          <a href={s.officialUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 px-2 text-sm font-medium text-brand-strong hover:underline">
            Official source: {hostname(s.officialUrl)}
            <ExternalLink className="size-3.5" aria-hidden="true" />
            <span className="sr-only">(opens in a new tab)</span>
          </a>
        ) : null}
      </div>
    </article>
  );
}
