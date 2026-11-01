import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  TaskEventStream,
  defaultBackoff,
  initialTaskEventsState,
  mergeEvents,
  parseEventData,
  taskEventsReducer,
  type ConnectionState,
  type EventSourceLike,
  type MessageLike,
} from "@/lib/sse/task-events";
import type { ExecutionEventDto, ExecutionEventType } from "@/types/api";

function event(sequence: number, type: ExecutionEventType = "BROWSER_ACTION_COMPLETED", eventId = `e-${sequence}`): ExecutionEventDto {
  return {
    eventId,
    taskId: "t1",
    sequence,
    type,
    timestamp: "2026-11-01T09:30:00Z",
    agent: "BROWSER_AGENT",
    step: "fill_personal_details",
    message: `event ${sequence}`,
    metadata: {},
  };
}

class FakeEventSource implements EventSourceLike {
  static instances: FakeEventSource[] = [];
  onopen: ((event: Event) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((e: MessageLike) => void)[]>();
  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }
  addEventListener(type: string, listener: (e: MessageLike) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }
  close() {
    this.closed = true;
  }
  listenerTypes() {
    return [...this.listeners.keys()];
  }
  open() {
    this.onopen?.(new Event("open"));
  }
  fail() {
    this.onerror?.(new Event("error"));
  }
  emit(e: ExecutionEventDto | string, type?: string) {
    const data = typeof e === "string" ? e : JSON.stringify(e);
    const name = type ?? (typeof e === "string" ? "message" : e.type);
    for (const l of this.listeners.get(name) ?? []) l({ data, lastEventId: typeof e === "string" ? "" : String(e.sequence) });
  }
  static latest() {
    const last = FakeEventSource.instances.at(-1);
    if (!last) throw new Error("no EventSource created");
    return last;
  }
}

const flush = async () => {
  for (let i = 0; i < 5; i += 1) await Promise.resolve();
};

describe("taskEventsReducer", () => {
  it("dedupes by eventId and by sequence, and orders by sequence", () => {
    let state = taskEventsReducer(initialTaskEventsState, { type: "received", events: [event(5), event(2)] });
    state = taskEventsReducer(state, { type: "received", events: [event(2), event(5, "TASK_STARTED", "other-id"), event(9), event(7)] });
    expect(state.events.map((e) => e.sequence)).toEqual([2, 5, 7, 9]);
    expect(state.lastSequence).toBe(9);
  });

  it("returns the same state object when nothing new arrives", () => {
    const state = taskEventsReducer(initialTaskEventsState, { type: "received", events: [event(1)] });
    expect(mergeEvents(state, [event(1)])).toBe(state);
    expect(taskEventsReducer(state, { type: "received", events: [] })).toBe(state);
  });

  it("tracks connection state and resets", () => {
    let state = taskEventsReducer(initialTaskEventsState, { type: "connection", state: "reconnecting", attempt: 2 });
    expect(state.connection).toBe("reconnecting");
    expect(state.reconnectAttempt).toBe(2);
    state = taskEventsReducer(state, { type: "reset" });
    expect(state).toEqual(initialTaskEventsState);
  });

  it("ignores payloads that do not match the envelope", () => {
    expect(parseEventData("not json")).toBeNull();
    expect(parseEventData(JSON.stringify({ eventId: "x", sequence: 1, type: "NOT_A_TYPE" }))).toBeNull();
    expect(parseEventData(JSON.stringify(event(3)))?.sequence).toBe(3);
  });
});

describe("defaultBackoff", () => {
  it("grows exponentially and caps at 30s plus jitter", () => {
    expect(defaultBackoff(1, () => 0)).toBe(1000);
    expect(defaultBackoff(3, () => 0)).toBe(4000);
    expect(defaultBackoff(10, () => 0)).toBe(30000);
    expect(defaultBackoff(10, () => 1)).toBe(39000);
  });
});

describe("TaskEventStream", () => {
  let received: ExecutionEventDto[];
  let states: ConnectionState[];
  let history: ReturnType<typeof vi.fn<(after: number, limit: number) => Promise<unknown>>>;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeEventSource.instances = [];
    received = [];
    states = [];
    history = vi.fn(async () => []);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  function createStream(extra: Partial<ConstructorParameters<typeof TaskEventStream>[0]> = {}) {
    return new TaskEventStream({
      url: "/api/v1/agent/tasks/t1/events",
      createEventSource: (url) => new FakeEventSource(url),
      fetchHistory: history,
      onEvents: (events) => received.push(...events),
      onConnectionChange: (s) => states.push(s),
      backoff: (attempt) => attempt * 1000,
      historyPageSize: 2,
      ...extra,
    });
  }

  it("listens for every contract event type and forwards parsed events", async () => {
    const stream = createStream();
    stream.start();
    await flush();
    const es = FakeEventSource.latest();
    expect(es.url).toBe("/api/v1/agent/tasks/t1/events");
    expect(es.listenerTypes()).toEqual(expect.arrayContaining(["TASK_CREATED", "CITIZEN_INPUT_REQUIRED", "RECEIPT_VERIFIED", "TASK_CANCELLED", "message"]));
    es.open();
    expect(states).toEqual(["connecting", "open"]);
    es.emit(event(4, "PLAN_CREATED"));
    es.emit("garbage");
    expect(received.map((e) => e.sequence)).toEqual([4]);
    stream.stop();
  });

  it("reconnects with backoff and backfills history after the last sequence", async () => {
    const stream = createStream();
    stream.start();
    await flush();
    const first = FakeEventSource.latest();
    first.open();
    await flush();
    first.emit(event(10));
    expect(stream.currentSequence).toBe(10);

    history.mockClear();
    first.fail();
    expect(first.closed).toBe(true);
    expect(states.at(-1)).toBe("reconnecting");
    expect(FakeEventSource.instances).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(999);
    expect(FakeEventSource.instances).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(FakeEventSource.instances).toHaveLength(2);

    // Events 11–13 were emitted while disconnected; history pages them in (page size 2).
    history.mockImplementation(async (after: number) => [event(11), event(12), event(13), event(14)].filter((e) => e.sequence > after).slice(0, 2));
    const second = FakeEventSource.latest();
    second.open();
    await flush();
    await vi.runAllTimersAsync();
    expect(history.mock.calls.map((c) => c[0])).toEqual([10, 12, 14]);
    expect(received.map((e) => e.sequence)).toEqual([10, 11, 12, 13, 14]);
    expect(states.at(-1)).toBe("open");
    stream.stop();
  });

  it("increases the delay on consecutive failures and resets after a successful open", async () => {
    const stream = createStream();
    stream.start();
    await flush();
    FakeEventSource.latest().fail();
    await vi.advanceTimersByTimeAsync(1000);
    FakeEventSource.latest().fail();
    await vi.advanceTimersByTimeAsync(1999);
    expect(FakeEventSource.instances).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(FakeEventSource.instances).toHaveLength(3);
    FakeEventSource.latest().open();
    FakeEventSource.latest().fail();
    await vi.advanceTimersByTimeAsync(1000);
    expect(FakeEventSource.instances).toHaveLength(4);
    stream.stop();
  });

  it("stops reconnecting when beforeReconnect reports a terminal task", async () => {
    const beforeReconnect = vi.fn(async () => false);
    const stream = createStream({ beforeReconnect });
    stream.start();
    await flush();
    FakeEventSource.latest().fail();
    await vi.advanceTimersByTimeAsync(1000);
    expect(beforeReconnect).toHaveBeenCalledTimes(1);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(states.at(-1)).toBe("closed");
  });

  it("closes after a terminal event instead of reconnecting", async () => {
    const stream = createStream();
    stream.start();
    await flush();
    const es = FakeEventSource.latest();
    es.open();
    es.emit(event(20, "TASK_COMPLETED"));
    await vi.runAllTimersAsync();
    expect(es.closed).toBe(true);
    expect(states.at(-1)).toBe("closed");
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("delivers nothing and schedules nothing after stop()", async () => {
    let resolveHistory: (v: unknown) => void = () => {};
    history.mockImplementation(() => new Promise((r) => (resolveHistory = r)));
    const stream = createStream();
    stream.start();
    const es = FakeEventSource.latest();
    stream.stop();
    expect(es.closed).toBe(true);
    resolveHistory([event(1)]);
    await flush();
    es.fail();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(received).toEqual([]);
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("feeds overlapping live and backfilled events through the reducer without duplicates", async () => {
    let state = initialTaskEventsState;
    const stream = createStream({
      onEvents: (events) => {
        state = taskEventsReducer(state, { type: "received", events });
      },
    });
    history.mockImplementation(async (after: number) => (after < 3 ? [event(1), event(2)].filter((e) => e.sequence > after) : []));
    stream.start();
    await flush();
    const es = FakeEventSource.latest();
    es.emit(event(2));
    es.emit(event(3));
    es.open();
    await vi.runAllTimersAsync();
    expect(state.events.map((e) => e.sequence)).toEqual([1, 2, 3]);
    stream.stop();
  });
});
