import * as React from "react";
import { http, HttpResponse } from "msw";
import { QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useTaskEvents } from "@/hooks/use-task-events";
import { queryKeys } from "@/lib/query-keys";
import type { EventSourceLike, MessageLike } from "@/lib/sse/task-events";
import type { ExecutionEventDto } from "@/types/api";
import { API, server } from "./msw/server";
import { createTestQueryClient } from "./utils";

class HookEventSource implements EventSourceLike {
  onopen: ((e: Event) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  listeners = new Map<string, ((e: MessageLike) => void)[]>();
  constructor(readonly url: string) {}
  addEventListener(type: string, l: (e: MessageLike) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), l]);
  }
  close() {}
  emit(e: ExecutionEventDto) {
    for (const l of this.listeners.get(e.type) ?? []) l({ data: JSON.stringify(e) });
  }
}

const ev = (sequence: number, type: ExecutionEventDto["type"]): ExecutionEventDto => ({
  eventId: `e${sequence}`,
  taskId: "t-9",
  sequence,
  type,
  timestamp: "2026-11-01T09:30:00Z",
  agent: "ORCHESTRATOR",
  step: null,
  message: type,
  metadata: {},
});

describe("useTaskEvents", () => {
  it("opens the task stream, backfills history and invalidates the task on status events", async () => {
    const afterSequences: string[] = [];
    server.use(
      http.get(`${API}/agent/tasks/t-9/events/history`, ({ request }) => {
        afterSequences.push(new URL(request.url).searchParams.get("afterSequence") ?? "");
        return HttpResponse.json(afterSequences.length === 1 ? [ev(1, "TASK_CREATED")] : []);
      }),
    );
    const sources: HookEventSource[] = [];
    const client = createTestQueryClient();
    client.setQueryData(queryKeys.tasks.detail("t-9"), { id: "t-9" });
    const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
    const { result } = renderHook(
      () =>
        useTaskEvents("t-9", {
          createEventSource: (url) => {
            const s = new HookEventSource(url);
            sources.push(s);
            return s;
          },
        }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.events).toHaveLength(1));
    expect(sources[0]?.url).toBe("/api/v1/agent/tasks/t-9/events");
    expect(afterSequences[0]).toBe("0");

    act(() => sources[0]?.onopen?.(new Event("open")));
    await waitFor(() => expect(result.current.connection).toBe("open"));
    await waitFor(() => expect(afterSequences[1]).toBe("1"));

    act(() => sources[0]?.emit(ev(5, "APPROVAL_REQUIRED")));
    await waitFor(() => expect(result.current.events.map((e) => e.sequence)).toEqual([1, 5]));
    expect(client.getQueryState(queryKeys.tasks.detail("t-9"))?.isInvalidated).toBe(true);
  });
});
