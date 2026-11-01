"use client";

import * as React from "react";
import { useSearchParams } from "next/navigation";
import { SendHorizontal, SearchX } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { ErrorState, PageHeader } from "@/components/common/states";
import { DiscoveryResult } from "@/features/services/components/discovery-result";
import { useJurisdictions } from "@/features/services/queries";
import { discoverServices } from "@/lib/api/services";
import { formatTime } from "@/lib/format";
import { SERVICE_CATEGORY_LABELS } from "@/lib/labels";
import { SERVICE_CATEGORIES, type DiscoveryRequest, type DiscoveryResponseDto, type ServiceCategory } from "@/types/api";

interface Turn {
  id: string;
  request: DiscoveryRequest;
  askedAt: string;
  status: "pending" | "done" | "error";
  response?: DiscoveryResponseDto;
  error?: unknown;
}

const ANY = "__any__";
const EXAMPLES = [
  "I am a first-year engineering student from a family earning under 3 lakh a year",
  "My father is a small farmer in Maharashtra and needs support for crop insurance",
  "I need an income certificate for a scholarship application",
];

let counter = 0;
const nextId = () => `turn-${Date.now()}-${(counter += 1)}`;

function newTurn(request: DiscoveryRequest): Turn {
  return { id: nextId(), request, askedAt: new Date().toISOString(), status: "pending" };
}

/**
 * Conversational discovery. History lives in component state for this visit only; nothing
 * is persisted in the browser. Only interpretations and results are shown, never model reasoning.
 */
export function Assistant() {
  const search = useSearchParams();
  const initialQuery = search.get("q")?.trim() ?? "";
  const [turns, setTurns] = React.useState<Turn[]>(() => (initialQuery.length >= 3 ? [newTurn({ query: initialQuery.slice(0, 500) })] : []));
  const [query, setQuery] = React.useState("");
  const [state, setState] = React.useState<string>(ANY);
  const [category, setCategory] = React.useState<string>(ANY);
  const [validation, setValidation] = React.useState<string | null>(null);
  const jurisdictions = useJurisdictions();
  const stateNames = React.useMemo(() => new Map((jurisdictions.data ?? []).map((j) => [j.code, j.name])), [jurisdictions.data]);
  const started = React.useRef(new Set<string>());
  const endRef = React.useRef<HTMLDivElement>(null);

  const run = React.useCallback((turn: Turn) => {
    if (started.current.has(turn.id)) return;
    started.current.add(turn.id);
    discoverServices(turn.request)
      .then((response) => setTurns((ts) => ts.map((t) => (t.id === turn.id ? { ...t, status: "done", response } : t))))
      .catch((error: unknown) => setTurns((ts) => ts.map((t) => (t.id === turn.id ? { ...t, status: "error", error } : t))));
  }, []);

  React.useEffect(() => {
    for (const t of turns) if (t.status === "pending") run(t);
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [turns.length, turns, run]);

  function ask(text: string) {
    const trimmed = text.trim();
    if (trimmed.length < 3) return setValidation("Describe what you need in at least 3 characters.");
    if (trimmed.length > 500) return setValidation("Keep it under 500 characters.");
    setValidation(null);
    const request: DiscoveryRequest = { query: trimmed };
    if (state !== ANY) request.state = state;
    if (category !== ANY) request.category = category as ServiceCategory;
    setTurns((ts) => [...ts, newTurn(request)]);
    setQuery("");
  }

  function retry(turn: Turn) {
    setTurns((ts) => [...ts, newTurn(turn.request)]);
  }

  const busy = turns.some((t) => t.status === "pending");

  return (
    <div className="flex min-h-[calc(100dvh-10rem)] flex-col">
      <PageHeader
        title="Assistant"
        description="Describe your situation in your own words. CIVIX finds matching services in its registry and checks each published rule against your profile."
      />

      <div className="flex-1 space-y-10 pb-8">
        {turns.length === 0 ? (
          <div className="space-y-3">
            <p className="text-sm font-medium">Try one of these</p>
            <div className="flex flex-col items-start gap-2">
              {EXAMPLES.map((e) => (
                <button
                  key={e}
                  type="button"
                  onClick={() => ask(e)}
                  className="rounded-xl border bg-card px-4 py-2.5 text-left text-sm text-foreground/85 transition-colors hover:border-brand/40 hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
                >
                  {e}
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {turns.map((turn) => (
          <section key={turn.id} aria-label={`Question: ${turn.request.query}`} className="space-y-5">
            <div className="ml-auto max-w-2xl rounded-2xl rounded-br-md bg-primary px-4 py-3 text-primary-foreground">
              <p className="leading-relaxed">{turn.request.query}</p>
              <p className="mt-1 text-xs text-primary-foreground/75">
                {formatTime(turn.askedAt)}
                {turn.request.state ? `, in ${stateNames.get(turn.request.state) ?? turn.request.state}` : ""}
                {turn.request.category ? `, ${SERVICE_CATEGORY_LABELS[turn.request.category].toLowerCase()}` : ""}
              </p>
            </div>
            {turn.status === "pending" ? (
              <div className="space-y-3" role="status" aria-label="Searching services">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-32 w-full rounded-2xl" />
              </div>
            ) : turn.status === "error" ? (
              <ErrorState error={turn.error} title="The search did not complete" onRetry={() => retry(turn)} />
            ) : turn.response ? (
              <TurnAnswer response={turn.response} stateNames={stateNames} />
            ) : null}
          </section>
        ))}
        <div ref={endRef} />
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          ask(query);
        }}
        className="sticky bottom-4 z-10 space-y-3 rounded-2xl border bg-card p-3 shadow-raised"
      >
        <Label htmlFor="assistant-query" className="sr-only">
          Describe what you need
        </Label>
        <Textarea
          id="assistant-query"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              ask(query);
            }
          }}
          rows={2}
          maxLength={500}
          placeholder="For example: scholarship for a girl student in class 11 in Karnataka"
          aria-invalid={validation ? true : undefined}
          aria-describedby={validation ? "assistant-error" : "assistant-hint"}
          className="min-h-14 resize-none border-0 shadow-none focus-visible:outline-none"
        />
        {validation ? (
          <p id="assistant-error" role="alert" className="px-3 text-[0.8125rem] font-medium text-danger">
            {validation}
          </p>
        ) : null}
        <div className="flex flex-wrap items-center gap-2">
          <div className="w-48">
            <Select value={state} onValueChange={setState}>
              <SelectTrigger aria-label="State" className="h-9">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Any state</SelectItem>
                {(jurisdictions.data ?? []).map((j) => (
                  <SelectItem key={j.code} value={j.code}>
                    {j.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-44">
            <Select value={category} onValueChange={setCategory}>
              <SelectTrigger aria-label="Category" className="h-9">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Any category</SelectItem>
                {SERVICE_CATEGORIES.map((c) => (
                  <SelectItem key={c} value={c}>
                    {SERVICE_CATEGORY_LABELS[c]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <p id="assistant-hint" className="hidden flex-1 text-xs text-muted-foreground md:block">
            Enter to send, Shift+Enter for a new line. This conversation is not saved.
          </p>
          <Button type="submit" className="ml-auto" loading={busy} disabled={busy}>
            <SendHorizontal aria-hidden="true" />
            Find services
          </Button>
        </div>
      </form>
    </div>
  );
}

function TurnAnswer({ response, stateNames }: { response: DiscoveryResponseDto; stateNames: Map<string, string> }) {
  const { interpretation } = response;
  return (
    <div className="space-y-5">
      <div className="max-w-3xl space-y-3">
        <p className="leading-relaxed">{interpretation.summary}</p>
        {interpretation.categories.length > 0 || interpretation.keywords.length > 0 || interpretation.state ? (
          <div className="flex flex-wrap gap-1.5" aria-label="How CIVIX read your request">
            {interpretation.categories.map((c) => (
              <Badge key={c} variant="brand">
                {SERVICE_CATEGORY_LABELS[c] ?? c}
              </Badge>
            ))}
            {interpretation.state ? <Badge variant="secondary">{stateNames.get(interpretation.state) ?? interpretation.state}</Badge> : null}
            {interpretation.keywords.map((k) => (
              <Badge key={k} variant="outline">
                {k}
              </Badge>
            ))}
          </div>
        ) : null}
        <p className="text-[0.8125rem] text-muted-foreground">
          {interpretation.usedLlm
            ? "Your request was interpreted with the help of a language model. Eligibility below comes only from the deterministic rules engine."
            : "Your request was interpreted by keyword matching. Eligibility below comes from the deterministic rules engine."}
        </p>
        {response.notice ? <p className="rounded-xl bg-muted px-3 py-2 text-[0.8125rem] leading-relaxed">{response.notice}</p> : null}
      </div>
      {response.results.length === 0 ? (
        <div className="flex gap-3 rounded-2xl border border-dashed px-5 py-5">
          <SearchX className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          <p className="text-sm leading-relaxed text-muted-foreground">
            No services in the CIVIX registry match this. Try naming the benefit you want (scholarship, pension, certificate) or your state. The
            registry does not cover every scheme.
          </p>
        </div>
      ) : (
        <div className="grid gap-4 xl:grid-cols-2">
          {response.results.map((item) => (
            <DiscoveryResult key={item.service.id} item={item} stateNames={stateNames} />
          ))}
        </div>
      )}
    </div>
  );
}
