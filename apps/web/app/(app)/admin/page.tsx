"use client";

import * as React from "react";
import { RefreshCcw, ShieldOff } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Pager } from "@/components/common/pager";
import { EmptyState, ErrorState, ListSkeleton, PageHeader } from "@/components/common/states";
import { AutomationBadge, TestServiceBadge, VerificationBadge } from "@/components/common/status-badges";
import { useAuth } from "@/features/auth/auth-provider";
import { ExecutionTimeline } from "@/features/agent/components/execution-timeline";
import { useAdminFailedTasks, useAdminServices, useAdminTaskEvents, useSyncRegistry } from "@/features/admin/queries";
import { isApiError } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { parseEventList } from "@/lib/sse/task-events";
import { formatDateTime } from "@/lib/format";
import { humanize } from "@/lib/labels";
import type { RegistrySyncResult } from "@/types/api";

function Forbidden() {
  return (
    <EmptyState
      icon={ShieldOff}
      title="Administrators only"
      description="Your account does not have the administrator role. Access is checked by the server on every request."
    />
  );
}

export default function AdminPage() {
  const { user } = useAuth();
  const isAdmin = user.roles.includes("ADMIN");
  return (
    <>
      <PageHeader title="Admin" description="Failed agent runs and the service registry, including drafts and test services." />
      {!isAdmin ? (
        <Forbidden />
      ) : (
        <Tabs defaultValue="tasks">
          <TabsList>
            <TabsTrigger value="tasks">Failed runs</TabsTrigger>
            <TabsTrigger value="registry">Service registry</TabsTrigger>
          </TabsList>
          <TabsContent value="tasks">
            <FailedTasks />
          </TabsContent>
          <TabsContent value="registry">
            <Registry />
          </TabsContent>
        </Tabs>
      )}
    </>
  );
}

function FailedTasks() {
  const [page, setPage] = React.useState(0);
  const [selected, setSelected] = React.useState<string | null>(null);
  const tasks = useAdminFailedTasks(page);
  const events = useAdminTaskEvents(selected);
  if (tasks.isPending) return <ListSkeleton rows={3} />;
  if (isApiError(tasks.error) && tasks.error.status === 403) return <Forbidden />;
  if (tasks.isError) return <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />;
  if (tasks.data.items.length === 0) return <EmptyState title="No failed runs" description="Runs that end in FAILED appear here for diagnosis." />;
  const current = tasks.data.items.find((t) => t.id === selected);
  return (
    <>
      <div className="rounded-2xl border bg-card shadow-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Service</TableHead>
              <TableHead>Error</TableHead>
              <TableHead>Agent and step</TableHead>
              <TableHead>Failed</TableHead>
              <TableHead>User</TableHead>
              <TableHead>
                <span className="sr-only">Events</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tasks.data.items.map((t) => (
              <TableRow key={t.id}>
                <TableCell className="font-medium">{t.serviceName}</TableCell>
                <TableCell className="max-w-72">
                  <p className="font-medium text-danger">{t.error ? humanize(t.error.code) : "Not reported"}</p>
                  {t.error?.message ? <p className="line-clamp-2 text-xs text-muted-foreground">{t.error.message}</p> : null}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {t.currentAgent ? humanize(t.currentAgent) : "None"}
                  {t.currentStep ? <span className="block text-xs">{t.currentStep}</span> : null}
                </TableCell>
                <TableCell className="whitespace-nowrap text-muted-foreground">{formatDateTime(t.completedAt ?? t.updatedAt)}</TableCell>
                <TableCell className="font-mono text-xs text-muted-foreground">{t.userId.slice(0, 8)}</TableCell>
                <TableCell className="text-right">
                  <Button variant="outline" size="sm" onClick={() => setSelected(t.id)}>
                    Events
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <Pager page={page} totalPages={tasks.data.totalPages} onPage={setPage} />
      <Sheet open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <SheetContent side="right" className="w-full max-w-xl overflow-y-auto sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>{current?.serviceName ?? "Run"} events</SheetTitle>
            <SheetDescription>Full event log for run {selected?.slice(0, 8)}.</SheetDescription>
          </SheetHeader>
          <div className="px-5 pb-6">
            {events.isPending ? (
              <ListSkeleton rows={3} />
            ) : events.isError ? (
              <ErrorState error={events.error} onRetry={() => void events.refetch()} />
            ) : (
              <ExecutionTimeline events={parseEventList(events.data)} />
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}

function syncErrorCount(errors: RegistrySyncResult["errors"]): number {
  return typeof errors === "number" ? errors : Array.isArray(errors) ? errors.length : 0;
}

function Registry() {
  const [page, setPage] = React.useState(0);
  const services = useAdminServices(page);
  const sync = useSyncRegistry();
  const [result, setResult] = React.useState<RegistrySyncResult | null>(null);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button
          onClick={() =>
            sync.mutate(undefined, {
              onSuccess: (r) => {
                setResult(r);
                toast.success("Registry synced");
              },
              onError: (e) => toast.error("Sync failed", { description: errorMessage(e) }),
            })
          }
          loading={sync.isPending}
        >
          <RefreshCcw aria-hidden="true" />
          Sync registry
        </Button>
        {result ? (
          <p role="status" className="text-sm text-muted-foreground tabular-nums">
            {result.synced} synced, {result.deactivated} deactivated, {syncErrorCount(result.errors)} errors
          </p>
        ) : null}
      </div>
      {result && Array.isArray(result.errors) && result.errors.length > 0 ? (
        <ul className="space-y-1 rounded-xl border border-danger/25 bg-danger-soft px-4 py-3 text-sm">
          {result.errors.map((e, i) => (
            <li key={i} className="break-words">
              {typeof e === "string" ? e : JSON.stringify(e)}
            </li>
          ))}
        </ul>
      ) : null}
      {services.isPending ? (
        <ListSkeleton rows={3} />
      ) : isApiError(services.error) && services.error.status === 403 ? (
        <Forbidden />
      ) : services.isError ? (
        <ErrorState error={services.error} onRetry={() => void services.refetch()} />
      ) : services.data.items.length === 0 ? (
        <EmptyState title="The registry is empty" description="Run a sync to load service definitions from the agent registry." />
      ) : (
        <>
          <div className="rounded-2xl border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Service</TableHead>
                  <TableHead>Code</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last verified</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {services.data.items.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell className="min-w-56 font-medium">{s.name}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {s.code} v{s.version}
                    </TableCell>
                    <TableCell>
                      <div className="flex min-w-64 flex-wrap gap-1.5">
                        {s.isTestService ? <TestServiceBadge /> : null}
                        <VerificationBadge status={s.verificationStatus} />
                        <AutomationBadge status={s.automationStatus} />
                      </div>
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{s.lastVerifiedAt ? formatDateTime(s.lastVerifiedAt) : "Never"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={page} totalPages={services.data.totalPages} onPage={setPage} />
        </>
      )}
    </div>
  );
}
