"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CalendarClock, FileStack, Hand, KeyRound, Landmark, MessageSquareText, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, SectionHeading } from "@/components/common/states";
import { ApplicationStatusBadge } from "@/components/common/status-badges";
import { useAuth } from "@/features/auth/auth-provider";
import { RequiredActionLink } from "@/features/applications/components/required-action";
import { useDashboard } from "@/features/dashboard/queries";
import { ServiceCard } from "@/features/services/components/service-card";
import { useNow } from "@/hooks/use-now";
import { formatCountdown, formatDate, formatRelative } from "@/lib/format";
import { APPROVAL_KIND_LABELS, CITIZEN_INPUT_LABELS } from "@/lib/labels";
import type { DashboardDto, ProfileCompleteness } from "@/types/api";

function completenessOf(value: DashboardDto["profileCompleteness"]): ProfileCompleteness | null {
  if (typeof value === "number") return { percent: value, coreFieldCount: 0, coreFilledCount: 0, missingCoreFields: [] };
  return value ?? null;
}

function greeting(name: string): string {
  const first = name.trim().split(/\s+/)[0] ?? name;
  return `Welcome back, ${first}`;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const dashboard = useDashboard();
  return (
    <div className="space-y-10">
      <div className="space-y-6">
        <h1 className="text-headline font-semibold">{greeting(user.fullName)}</h1>
        <ServiceSearch />
      </div>
      {dashboard.isPending ? (
        <DashboardSkeleton />
      ) : dashboard.isError ? (
        <ErrorState error={dashboard.error} title="Your dashboard could not be loaded" onRetry={() => void dashboard.refetch()} />
      ) : (
        <DashboardBody data={dashboard.data} />
      )}
    </div>
  );
}

function ServiceSearch() {
  const router = useRouter();
  const [q, setQ] = React.useState("");
  const trimmed = q.trim();
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (trimmed.length >= 3) router.push(`/assistant?q=${encodeURIComponent(trimmed)}`);
      }}
      className="rounded-2xl border bg-card p-4 shadow-card sm:p-5"
    >
      <Label htmlFor="dashboard-search" className="mb-2 block text-[0.9375rem]">
        What do you need help with?
      </Label>
      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input
            id="dashboard-search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            maxLength={500}
            className="h-11 pl-9"
            placeholder="A scholarship for my daughter, an income certificate, a pension for my mother"
          />
        </div>
        <Button type="submit" size="lg" disabled={trimmed.length < 3}>
          <MessageSquareText aria-hidden="true" />
          Ask the assistant
        </Button>
        <Button
          type="button"
          size="lg"
          variant="outline"
          onClick={() => router.push(trimmed ? `/services?q=${encodeURIComponent(trimmed)}` : "/services")}
        >
          <Landmark aria-hidden="true" />
          Search catalog
        </Button>
      </div>
    </form>
  );
}

function DashboardSkeleton() {
  return (
    <div className="grid gap-6 lg:grid-cols-3" role="status" aria-label="Loading dashboard">
      <Skeleton className="h-48 rounded-2xl lg:col-span-2" />
      <Skeleton className="h-48 rounded-2xl" />
      <Skeleton className="h-64 rounded-2xl lg:col-span-2" />
      <Skeleton className="h-64 rounded-2xl" />
    </div>
  );
}

function DashboardBody({ data }: { data: DashboardDto }) {
  const completeness = completenessOf(data.profileCompleteness);
  const deadlines = data.upcomingDeadlines.filter((s) => s.deadline?.date && s.deadline.verifiedAt);
  const pendingCount = data.pendingApprovals.length + data.pendingCitizenInputs.length;
  return (
    <div className="grid gap-x-8 gap-y-10 lg:grid-cols-[minmax(0,1fr)_20rem]">
      <div className="min-w-0 space-y-10">
        <section aria-labelledby="pending-heading">
          <SectionHeading id="pending-heading" title="Waiting for you" description={pendingCount ? undefined : "Approvals and portal inputs show up here."} />
          {pendingCount === 0 ? (
            <EmptyState title="Nothing needs your decision" description="When a run needs an approval, a one-time password or a CAPTCHA, it pauses and appears here." />
          ) : (
            <PendingActions data={data} />
          )}
        </section>

        <section aria-labelledby="active-heading">
          <SectionHeading
            id="active-heading"
            title="Active applications"
            action={
              <Button asChild variant="link" size="sm">
                <Link href="/applications">All applications</Link>
              </Button>
            }
          />
          {data.activeApplications.length === 0 ? (
            <EmptyState
              icon={FileStack}
              title="No active applications"
              description="Ask the assistant about a service, check your eligibility, and start an application from there."
            />
          ) : (
            <ul className="divide-y rounded-2xl border bg-card shadow-card">
              {data.activeApplications.map((a) => (
                <li key={a.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center">
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link href={`/applications/${a.id}`} className="font-medium hover:underline">
                        {a.service.name}
                      </Link>
                      <ApplicationStatusBadge status={a.status} />
                    </div>
                    <p className="text-[0.8125rem] text-muted-foreground">
                      {a.requiredAction ? a.requiredAction.message : `Last activity ${formatRelative(a.lastActivityAt ?? a.updatedAt)}`}
                    </p>
                  </div>
                  {a.requiredAction ? <RequiredActionLink action={a.requiredAction} applicationId={a.id} /> : null}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="recommended-heading">
          <SectionHeading
            id="recommended-heading"
            title="Services you may qualify for"
            description="Based on your latest eligibility checks. The department makes the final decision."
          />
          {data.recommended.length === 0 ? (
            <EmptyState
              icon={Landmark}
              title="No recommendations yet"
              description="Recommendations appear after you check your eligibility for a service."
              action={
                <Button asChild size="sm" variant="outline">
                  <Link href="/services">Browse services</Link>
                </Button>
              }
            />
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {data.recommended.map((s) => (
                <ServiceCard key={s.id} service={s} />
              ))}
            </div>
          )}
        </section>
      </div>

      <aside className="space-y-8">
        <section aria-labelledby="profile-heading" className="rounded-2xl border bg-card p-5 shadow-card">
          <h2 id="profile-heading" className="font-semibold">Profile</h2>
          {completeness ? (
            <>
              <div className="mt-3 flex items-baseline justify-between">
                <span className="text-sm text-muted-foreground">Core details filled</span>
                <span className="font-semibold tabular-nums">{Math.round(completeness.percent)}%</span>
              </div>
              <Progress value={completeness.percent} className="mt-2" aria-label="Profile completeness" />
              {completeness.coreFieldCount > 0 ? (
                <p className="mt-2 text-[0.8125rem] text-muted-foreground tabular-nums">
                  {completeness.coreFilledCount} of {completeness.coreFieldCount} core fields
                </p>
              ) : null}
            </>
          ) : null}
          <p className="mt-3 text-[0.8125rem] leading-relaxed text-muted-foreground">
            A fuller profile lets more eligibility rules be checked instead of left unknown.
          </p>
          <Button asChild variant="outline" size="sm" className="mt-4">
            <Link href="/settings">Edit profile</Link>
          </Button>
        </section>

        <section aria-labelledby="deadlines-heading">
          <SectionHeading id="deadlines-heading" title="Verified deadlines" />
          {deadlines.length === 0 ? (
            <p className="text-sm text-muted-foreground">No verified deadlines for services you are tracking.</p>
          ) : (
            <ul className="space-y-3">
              {deadlines.map((s) => (
                <li key={s.id} className="flex gap-3">
                  <CalendarClock className="mt-0.5 size-4 shrink-0 text-brand-strong" aria-hidden="true" />
                  <div className="text-sm">
                    <Link href={`/services/${s.id}`} className="font-medium hover:underline">
                      {s.name}
                    </Link>
                    <p className="text-muted-foreground">
                      Apply by {formatDate(s.deadline?.date)}
                      {s.deadline?.note ? `. ${s.deadline.note}` : ""}
                    </p>
                    {s.deadline?.sourceUrl ? (
                      <a href={s.deadline.sourceUrl} target="_blank" rel="noopener noreferrer" className="text-[0.8125rem] text-brand-strong hover:underline">
                        Source, verified {formatDate(s.deadline.verifiedAt)}
                        <span className="sr-only"> (opens in a new tab)</span>
                      </a>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="activity-heading">
          <SectionHeading
            id="activity-heading"
            title="Recent activity"
            action={
              <Button asChild variant="link" size="sm">
                <Link href="/trust-center">Trust Center</Link>
              </Button>
            }
          />
          {data.recentActivity.length === 0 ? (
            <p className="text-sm text-muted-foreground">No activity yet.</p>
          ) : (
            <ol className="space-y-3">
              {data.recentActivity.slice(0, 8).map((e) => (
                <li key={e.id} className="text-sm">
                  <p className={e.outcome === "SUCCESS" ? undefined : "text-danger"}>{e.summary}</p>
                  <p className="text-xs text-muted-foreground">{formatRelative(e.occurredAt)}</p>
                </li>
              ))}
            </ol>
          )}
        </section>
      </aside>
    </div>
  );
}

function PendingActions({ data }: { data: DashboardDto }) {
  const now = useNow(1000);
  return (
    <ul className="space-y-3">
      {data.pendingApprovals.map((ap) => {
        const left = (Date.parse(ap.expiresAt) - now) / 1000;
        return (
          <li key={ap.id} className="flex flex-col gap-3 rounded-2xl border border-brand/30 bg-card px-5 py-4 shadow-card sm:flex-row sm:items-center">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-brand-soft text-brand-strong">
              <Hand className="size-4.5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-medium">
                {APPROVAL_KIND_LABELS[ap.kind]}: {ap.serviceName}
              </p>
              <p className="text-[0.8125rem] text-muted-foreground tabular-nums">
                {left > 0 ? `Expires in ${formatCountdown(left)}` : "Expired"}
              </p>
            </div>
            <Button asChild size="sm" variant="brand">
              <Link href={`/agent/${ap.taskId}`}>Review</Link>
            </Button>
          </li>
        );
      })}
      {data.pendingCitizenInputs.map((t) => (
        <li key={t.id} className="flex flex-col gap-3 rounded-2xl border border-brand/30 bg-card px-5 py-4 shadow-card sm:flex-row sm:items-center">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-brand-soft text-brand-strong">
            <KeyRound className="size-4.5" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-medium">
              {t.pendingCitizenInput ? CITIZEN_INPUT_LABELS[t.pendingCitizenInput.kind] : "Input"} needed: {t.serviceName}
            </p>
            {t.pendingCitizenInput ? <p className="line-clamp-1 text-[0.8125rem] text-muted-foreground">{t.pendingCitizenInput.prompt}</p> : null}
          </div>
          <Button asChild size="sm" variant="brand">
            <Link href={`/agent/${t.id}`}>Open control room</Link>
          </Button>
        </li>
      ))}
    </ul>
  );
}
