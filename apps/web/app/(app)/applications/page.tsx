"use client";

import * as React from "react";
import Link from "next/link";
import { FileStack, FlaskConical, Receipt } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Pager } from "@/components/common/pager";
import { EmptyState, ErrorState, ListSkeleton, PageHeader } from "@/components/common/states";
import { ApplicationStatusBadge } from "@/components/common/status-badges";
import { RequiredActionLink } from "@/features/applications/components/required-action";
import { useApplications } from "@/features/applications/queries";
import { formatRelative } from "@/lib/format";
import { APPLICATION_STATUS_LABELS } from "@/lib/labels";
import { APPLICATION_STATUSES, type ApplicationStatus } from "@/types/api";

const ALL = "ALL";

export default function ApplicationsPage() {
  const [status, setStatus] = React.useState<ApplicationStatus | typeof ALL>(ALL);
  const [page, setPage] = React.useState(0);
  const applications = useApplications({ page, ...(status === ALL ? {} : { status }) });

  return (
    <>
      <PageHeader
        title="Applications"
        description="Every application you started with CIVIX, what it is waiting for, and its receipts."
        actions={
          <Button asChild variant="outline">
            <Link href="/services">Find a service</Link>
          </Button>
        }
      />
      <Tabs
        value={status}
        onValueChange={(v) => {
          setStatus(v as ApplicationStatus | typeof ALL);
          setPage(0);
        }}
      >
        <TabsList className="mb-6 max-w-full">
          <TabsTrigger value={ALL}>All</TabsTrigger>
          {APPLICATION_STATUSES.map((s) => (
            <TabsTrigger key={s} value={s}>
              {APPLICATION_STATUS_LABELS[s]}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {applications.isPending ? (
        <ListSkeleton rows={4} />
      ) : applications.isError ? (
        <ErrorState error={applications.error} onRetry={() => void applications.refetch()} />
      ) : applications.data.items.length === 0 ? (
        <EmptyState
          icon={FileStack}
          title={status === ALL ? "No applications yet" : `No applications are ${APPLICATION_STATUS_LABELS[status].toLowerCase()}`}
          description="Find a service, check your eligibility, then start an application from its page."
          action={
            <Button asChild size="sm">
              <Link href="/assistant">Ask the assistant</Link>
            </Button>
          }
        />
      ) : (
        <>
          <ul className="divide-y rounded-2xl border bg-card shadow-card">
            {applications.data.items.map((a) => (
              <li key={a.id} className="flex flex-col gap-3 px-5 py-4 md:flex-row md:items-center md:gap-6">
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link href={`/applications/${a.id}`} className="font-medium hover:underline focus-visible:outline-2 focus-visible:outline-ring">
                      {a.service.name}
                    </Link>
                    <ApplicationStatusBadge status={a.status} />
                    {a.isTestEnvironment ? (
                      <span className="inline-flex items-center gap-1 text-xs font-medium text-info">
                        <FlaskConical className="size-3.5" aria-hidden="true" />
                        Test environment
                      </span>
                    ) : null}
                  </div>
                  <p className="text-[0.8125rem] text-muted-foreground">
                    {a.referenceNumber ? <>Reference {a.referenceNumber}. </> : null}
                    Last activity {formatRelative(a.lastActivityAt ?? a.updatedAt)}
                  </p>
                  {a.requiredAction ? <p className="text-sm text-warning">{a.requiredAction.message}</p> : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  {a.requiredAction ? <RequiredActionLink action={a.requiredAction} applicationId={a.id} /> : null}
                  {a.status === "SUBMITTED" ? (
                    <Button asChild size="sm" variant="ghost">
                      <Link href={`/applications/${a.id}#receipts`}>
                        <Receipt aria-hidden="true" />
                        Receipt
                      </Link>
                    </Button>
                  ) : null}
                  <Button asChild size="sm" variant="ghost">
                    <Link href={`/applications/${a.id}`}>Details</Link>
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <Pager page={page} totalPages={applications.data.totalPages} onPage={setPage} />
        </>
      )}
    </>
  );
}
