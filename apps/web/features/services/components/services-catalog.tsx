"use client";

import * as React from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Landmark, Search, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Pager } from "@/components/common/pager";
import { EmptyState, ErrorState, ListSkeleton, PageHeader } from "@/components/common/states";
import { ServiceCard } from "@/features/services/components/service-card";
import { SignInPrompt } from "@/features/services/components/sign-in-prompt";
import { useJurisdictions, useServiceCategories, useServices } from "@/features/services/queries";
import { isApiError } from "@/lib/api/client";
import { SERVICE_CATEGORY_LABELS } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { SERVICE_CATEGORIES, type JurisdictionLevel, type ServiceCategory, type ServiceListParams } from "@/types/api";

function readParams(search: URLSearchParams): ServiceListParams {
  const category = search.get("category");
  const jurisdiction = search.get("jurisdiction");
  const page = Number(search.get("page") ?? "0");
  return {
    q: search.get("q") ?? undefined,
    category: SERVICE_CATEGORIES.includes(category as ServiceCategory) ? (category as ServiceCategory) : undefined,
    jurisdiction: jurisdiction === "CENTRAL" || jurisdiction === "STATE" ? jurisdiction : undefined,
    state: search.get("state") ?? undefined,
    page: Number.isInteger(page) && page > 0 ? page : 0,
  };
}

const ANY_STATE = "__any__";

export function ServicesCatalog() {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const params = readParams(search);
  const [query, setQuery] = React.useState(params.q ?? "");
  const services = useServices(params);
  const unauthorized = isApiError(services.error) && services.error.status === 401;
  const categories = useServiceCategories(!unauthorized);
  const jurisdictions = useJurisdictions(!unauthorized);
  const stateNames = React.useMemo(() => new Map((jurisdictions.data ?? []).map((j) => [j.code, j.name])), [jurisdictions.data]);

  function update(next: Partial<ServiceListParams>) {
    const merged = { ...params, page: 0, ...next };
    const sp = new URLSearchParams();
    if (merged.q) sp.set("q", merged.q);
    if (merged.category) sp.set("category", merged.category);
    if (merged.jurisdiction) sp.set("jurisdiction", merged.jurisdiction);
    if (merged.jurisdiction === "STATE" && merged.state) sp.set("state", merged.state);
    if (merged.page) sp.set("page", String(merged.page));
    const qs = sp.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  const nextPath = `${pathname}${search.toString() ? `?${search.toString()}` : ""}`;
  const counts = new Map((categories.data ?? []).map((c) => [c.category, c.count]));
  const visibleCategories = categories.data ? SERVICE_CATEGORIES.filter((c) => (counts.get(c) ?? 0) > 0) : [];
  const filtered = Boolean(params.q || params.category || params.jurisdiction || params.state);

  return (
    <>
      <PageHeader
        title="Services"
        description="Central and state services in the CIVIX registry. Each one shows whether its sources have been verified and whether CIVIX can help apply."
      />
      {unauthorized ? (
        <SignInPrompt next={nextPath} />
      ) : (
        <>
          <div className="space-y-4 rounded-2xl border bg-card p-4 shadow-card sm:p-5">
            <form
              role="search"
              onSubmit={(e) => {
                e.preventDefault();
                update({ q: query.trim() || undefined });
              }}
              className="flex gap-2"
            >
              <Label htmlFor="service-search" className="sr-only">
                Search services
              </Label>
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <Input
                  id="service-search"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search by name, benefit or department"
                  className="pl-9"
                />
              </div>
              <Button type="submit">Search</Button>
            </form>

            {visibleCategories.length > 0 ? (
              <div role="group" aria-label="Category" className="flex flex-wrap gap-2">
                <Chip active={!params.category} onClick={() => update({ category: undefined })}>
                  All categories
                </Chip>
                {visibleCategories.map((c) => (
                  <Chip key={c} active={params.category === c} onClick={() => update({ category: params.category === c ? undefined : c })}>
                    {SERVICE_CATEGORY_LABELS[c]}
                    <span className="text-xs opacity-70 tabular-nums">{counts.get(c)}</span>
                  </Chip>
                ))}
              </div>
            ) : null}

            <div className="flex flex-wrap items-center gap-3">
              <div role="radiogroup" aria-label="Jurisdiction" className="inline-flex rounded-xl bg-muted p-1">
                {([undefined, "CENTRAL", "STATE"] as (JurisdictionLevel | undefined)[]).map((level) => {
                  const active = params.jurisdiction === level;
                  return (
                    <button
                      key={level ?? "ALL"}
                      type="button"
                      role="radio"
                      aria-checked={active}
                      onClick={() => update({ jurisdiction: level, state: level === "STATE" ? params.state : undefined })}
                      className={cn(
                        "rounded-lg px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring",
                        active && "bg-card text-foreground shadow-sm",
                      )}
                    >
                      {level === undefined ? "All" : level === "CENTRAL" ? "Central" : "State"}
                    </button>
                  );
                })}
              </div>
              {params.jurisdiction === "STATE" ? (
                <div className="w-64">
                  <Select value={params.state ?? ANY_STATE} onValueChange={(v) => update({ jurisdiction: "STATE", state: v === ANY_STATE ? undefined : v })}>
                    <SelectTrigger aria-label="State or union territory">
                      <SelectValue placeholder="Any state" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={ANY_STATE}>Any state or union territory</SelectItem>
                      {(jurisdictions.data ?? []).map((j) => (
                        <SelectItem key={j.code} value={j.code}>
                          {j.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              ) : null}
              {filtered ? (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setQuery("");
                    router.replace(pathname, { scroll: false });
                  }}
                >
                  <X aria-hidden="true" />
                  Clear filters
                </Button>
              ) : null}
            </div>
          </div>

          <div className="mt-8" aria-live="polite" aria-busy={services.isFetching}>
            {services.isPending ? (
              <ListSkeleton rows={3} />
            ) : services.isError ? (
              <ErrorState error={services.error} onRetry={() => void services.refetch()} />
            ) : services.data.items.length === 0 ? (
              <EmptyState
                icon={Landmark}
                title="No services match"
                description={
                  filtered
                    ? "Try fewer words, another category, or ask the assistant to search by describing your situation."
                    : "The registry has no published services yet."
                }
              />
            ) : (
              <>
                <p className="mb-4 text-sm text-muted-foreground tabular-nums">
                  {services.data.totalItems} {services.data.totalItems === 1 ? "service" : "services"}
                </p>
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                  {services.data.items.map((s) => (
                    <ServiceCard key={s.id} service={s} stateNames={stateNames} />
                  ))}
                </div>
                <Pager page={services.data.page} totalPages={services.data.totalPages} onPage={(page) => update({ ...params, page })} />
              </>
            )}
          </div>
        </>
      )}
    </>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-ring",
        active ? "border-primary bg-primary text-primary-foreground" : "bg-card text-foreground hover:bg-accent",
      )}
    >
      {children}
    </button>
  );
}
