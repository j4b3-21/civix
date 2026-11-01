"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ExternalLink, FlaskConical, Hand, ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState, ErrorState, Notice } from "@/components/common/states";
import { EligibilityBadge } from "@/components/common/status-badges";
import { StartApplicationButton } from "@/features/applications/components/start-application-button";
import { EligibilityCheck } from "@/features/services/components/eligibility-check";
import { SourceCitation } from "@/features/services/components/eligibility-panel";
import { ServiceBadges } from "@/features/services/components/service-badges";
import { jurisdictionText } from "@/features/services/components/service-card";
import { SignInPrompt } from "@/features/services/components/sign-in-prompt";
import { useJurisdictions, useService } from "@/features/services/queries";
import { isApiError } from "@/lib/api/client";
import { formatDate, formatDateTime, formatValue, hostname } from "@/lib/format";
import { DOCUMENT_CATEGORY_LABELS, OPERATOR_LABELS, SERVICE_CATEGORY_LABELS, humanize } from "@/lib/labels";

export default function ServiceDetailPage() {
  const { serviceId } = useParams<{ serviceId: string }>();
  const service = useService(serviceId);
  const unauthorized = isApiError(service.error) && service.error.status === 401;
  const jurisdictions = useJurisdictions(Boolean(service.data));
  const stateNames = new Map((jurisdictions.data ?? []).map((j) => [j.code, j.name]));

  if (service.isPending) {
    return (
      <div className="space-y-4" role="status" aria-label="Loading service">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }
  if (unauthorized) return <SignInPrompt next={`/services/${serviceId}`} />;
  if (service.isError) {
    return (
      <div className="space-y-6">
        <BackLink />
        <ErrorState error={service.error} title="This service could not be loaded" onRetry={() => void service.refetch()} />
      </div>
    );
  }

  const s = service.data;
  return (
    <div className="space-y-8">
      <BackLink />
      <header className="space-y-4">
        <p className="text-sm text-muted-foreground">
          {SERVICE_CATEGORY_LABELS[s.category]} <span aria-hidden="true">/</span> {jurisdictionText(s, stateNames)}
        </p>
        <h1 className="max-w-3xl text-headline font-semibold">{s.name}</h1>
        {s.department || s.ministry ? (
          <p className="text-[0.9375rem] text-muted-foreground">{[s.department, s.ministry].filter(Boolean).join(", ")}</p>
        ) : null}
        <ServiceBadges service={s} showEligibility={false} />
      </header>

      <div className="space-y-3">
        {s.isTestService ? (
          <Notice tone="info" icon={FlaskConical} title="Test environment, not a government service">
            This service runs on the CIVIX Test Portal, an authorised test environment. Nothing submitted here has any legal effect.
          </Notice>
        ) : null}
        {s.verificationStatus === "DRAFT" ? (
          <Notice tone="warning" icon={ShieldAlert} title="Unverified source">
            The rules for this service have not yet been checked against official sources. Treat eligibility results as unverified and
            confirm on the official site.
          </Notice>
        ) : null}
        {s.automationStatus === "NOT_AUTOMATED" || s.automationStatus === "GUIDED" ? (
          <Notice tone="brand" icon={Hand} title="Guided only: apply on the official portal">
            CIVIX can explain the rules and steps, and track your application, but it will not fill in this portal for you.
          </Notice>
        ) : null}
      </div>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-10">
          <section aria-labelledby="about-heading" className="space-y-3">
            <h2 id="about-heading" className="text-title font-semibold">About this service</h2>
            <p className="max-w-[68ch] leading-relaxed whitespace-pre-line text-foreground/90">{s.description || s.summary}</p>
            {s.benefits.length > 0 ? (
              <>
                <h3 className="pt-2 font-semibold">Benefits</h3>
                <ul className="max-w-[68ch] list-disc space-y-1.5 pl-5 leading-relaxed marker:text-brand">
                  {s.benefits.map((b) => (
                    <li key={b}>{b}</li>
                  ))}
                </ul>
              </>
            ) : null}
          </section>

          <section aria-labelledby="rules-heading" className="space-y-3">
            <h2 id="rules-heading" className="text-title font-semibold">Eligibility rules</h2>
            {s.eligibilityRules.length === 0 ? (
              <p className="text-muted-foreground">No eligibility rules are recorded for this service.</p>
            ) : (
              <div className="rounded-2xl border bg-card shadow-card">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Rule</TableHead>
                      <TableHead>Condition</TableHead>
                      <TableHead>Source</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {s.eligibilityRules.map((r) => (
                      <TableRow key={`${r.ruleId}-${r.ruleVersion}`}>
                        <TableCell className="min-w-56">{r.description}</TableCell>
                        <TableCell className="min-w-44 text-muted-foreground">
                          <span className="text-foreground">{humanize(r.field)}</span> {OPERATOR_LABELS[r.operator]}
                          {r.operator !== "EXISTS" ? <> {formatValue(r.value)}</> : null}
                        </TableCell>
                        <TableCell className="min-w-40">
                          <SourceCitation url={r.sourceUrl} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </section>

          <section aria-labelledby="docs-heading" className="space-y-3">
            <h2 id="docs-heading" className="text-title font-semibold">Required documents</h2>
            {s.requiredDocuments.length === 0 ? (
              <p className="text-muted-foreground">No documents are listed for this service.</p>
            ) : (
              <ul className="divide-y rounded-2xl border bg-card shadow-card">
                {s.requiredDocuments.map((d, i) => (
                  <li key={`${d.category}-${i}`} className="flex flex-wrap items-start justify-between gap-2 px-5 py-3.5">
                    <div>
                      <p className="font-medium">{DOCUMENT_CATEGORY_LABELS[d.category]}</p>
                      <p className="text-sm text-muted-foreground">{d.description}</p>
                    </div>
                    <span className="text-[0.8125rem] font-medium text-muted-foreground">{d.mandatory ? "Required" : "If applicable"}</span>
                  </li>
                ))}
              </ul>
            )}
            <Button asChild variant="link">
              <Link href="/documents">Manage your documents</Link>
            </Button>
          </section>

          {s.procedure.length > 0 ? (
            <section aria-labelledby="procedure-heading" className="space-y-3">
              <h2 id="procedure-heading" className="text-title font-semibold">How to apply</h2>
              <ol className="space-y-4">
                {[...s.procedure].sort((a, b) => a.order - b.order).map((p) => (
                  <li key={p.order} className="flex gap-4">
                    <span className="flex size-7 shrink-0 items-center justify-center rounded-full border bg-card text-xs font-semibold tabular-nums">
                      {p.order}
                    </span>
                    <div className="pt-0.5">
                      <p className="font-medium">{p.title}</p>
                      <p className="mt-0.5 max-w-[68ch] text-sm leading-relaxed text-muted-foreground">{p.description}</p>
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          ) : null}

          <section aria-labelledby="sources-heading" className="space-y-3">
            <h2 id="sources-heading" className="text-title font-semibold">Official sources</h2>
            {s.sources.length === 0 ? (
              <p className="text-muted-foreground">No official sources are recorded yet.</p>
            ) : (
              <ul className="space-y-2">
                {s.sources.map((src) => (
                  <li key={src.url} className="text-sm">
                    <a href={src.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium text-brand-strong hover:underline">
                      {src.title || hostname(src.url)}
                      <ExternalLink className="size-3.5" aria-hidden="true" />
                      <span className="sr-only">(opens in a new tab)</span>
                    </a>
                    <span className="text-muted-foreground">
                      {" "}
                      {humanize(src.kind)}, {src.retrievedAt ? `retrieved ${formatDate(src.retrievedAt)}` : "retrieval date not recorded"}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {s.lastVerifiedAt ? <p className="text-[0.8125rem] text-muted-foreground">Last verified {formatDateTime(s.lastVerifiedAt)}</p> : null}
            {s.disclaimer ? <p className="max-w-[68ch] text-[0.8125rem] leading-relaxed text-muted-foreground">{s.disclaimer}</p> : null}
          </section>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-24 lg:h-fit">
          <section aria-labelledby="eligibility-heading" className="rounded-2xl border bg-card p-5 shadow-card">
            <h2 id="eligibility-heading" className="mb-1 font-semibold">Your eligibility</h2>
            {s.eligibility ? (
              <p className="mb-4 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                Last result <EligibilityBadge outcome={s.eligibility.outcome} /> on {formatDate(s.eligibility.evaluatedAt)}
              </p>
            ) : (
              <p className="mb-4 text-sm text-muted-foreground">Compare your profile with each rule above. Nothing is shared with the department.</p>
            )}
            <EligibilityCheck serviceId={s.id} />
          </section>
          <section aria-labelledby="apply-heading" className="space-y-3 rounded-2xl border bg-card p-5 shadow-card">
            <h2 id="apply-heading" className="font-semibold">Apply</h2>
            <p className="text-sm leading-relaxed text-muted-foreground">
              {s.automationStatus === "NOT_AUTOMATED" || s.automationStatus === "GUIDED"
                ? "Starting an application lets CIVIX track it and keep your documents ready. You submit it on the official portal."
                : "Starting an application creates a draft. Nothing is sent to the portal until you run the agent and approve each step."}
            </p>
            <StartApplicationButton serviceId={s.id} className="w-full" />
            {s.officialUrl ? (
              <Button asChild variant="outline" className="w-full">
                <a href={s.officialUrl} target="_blank" rel="noopener noreferrer">
                  <ExternalLink aria-hidden="true" />
                  Official portal
                  <span className="sr-only">(opens in a new tab)</span>
                </a>
              </Button>
            ) : null}
          </section>
          {s.portalDomains.length > 0 ? (
            <p className="px-1 text-[0.8125rem] leading-relaxed text-muted-foreground">
              The agent may only open: {s.portalDomains.join(", ")}
            </p>
          ) : null}
        </aside>
      </div>
      {s.eligibilityRules.length === 0 && s.requiredDocuments.length === 0 && s.procedure.length === 0 ? (
        <EmptyState title="Details are still being added" description="Check the official sources for the full scheme guidelines." />
      ) : null}
    </div>
  );
}

function BackLink() {
  return (
    <Button asChild variant="ghost" size="sm" className="-ml-2">
      <Link href="/services">
        <ArrowLeft aria-hidden="true" />
        All services
      </Link>
    </Button>
  );
}
