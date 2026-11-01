/**
 * Live execution events for one agent task (AGENT_CONTRACTS §2.1–2.2).
 *
 * Two pieces, both framework-free so they can be unit tested:
 * - {@link taskEventsReducer}: merges events idempotently (dedupe by `eventId` and `sequence`)
 *   and keeps them ordered by `sequence`, plus connection bookkeeping.
 * - {@link TaskEventStream}: owns an EventSource, reconnects with backoff, and after every
 *   (re)connect backfills gaps from `GET /agent/tasks/{id}/events/history?afterSequence=<last>`.
 *   Sequences are strictly increasing but not contiguous per task (they are assigned per
 *   database), so gaps cannot be detected locally; backfilling on every open is the safe rule.
 */
import { executionEventSchema } from "@/lib/schemas/api";
import { EXECUTION_EVENT_TYPES, type ExecutionEventDto, type ExecutionEventType } from "@/types/api";

export type ConnectionState = "connecting" | "open" | "reconnecting" | "closed";

export interface TaskEventsState {
  events: ExecutionEventDto[];
  eventIds: Readonly<Record<string, true>>;
  lastSequence: number;
  connection: ConnectionState;
  reconnectAttempt: number;
}

export type TaskEventsAction =
  | { type: "received"; events: readonly ExecutionEventDto[] }
  | { type: "connection"; state: ConnectionState; attempt: number }
  | { type: "reset" };

export const initialTaskEventsState: TaskEventsState = {
  events: [],
  eventIds: {},
  lastSequence: 0,
  connection: "connecting",
  reconnectAttempt: 0,
};

export function mergeEvents(state: TaskEventsState, incoming: readonly ExecutionEventDto[]): TaskEventsState {
  if (incoming.length === 0) return state;
  const sequences = new Set(state.events.map((e) => e.sequence));
  const eventIds: Record<string, true> = { ...state.eventIds };
  const fresh: ExecutionEventDto[] = [];
  for (const event of incoming) {
    if (eventIds[event.eventId] || sequences.has(event.sequence)) continue;
    eventIds[event.eventId] = true;
    sequences.add(event.sequence);
    fresh.push(event);
  }
  if (fresh.length === 0) return state;
  const lastKnown = state.events.at(-1)?.sequence ?? -1;
  const appendOnly = fresh.every((e) => e.sequence > lastKnown) && isSorted(fresh);
  const events = appendOnly
    ? [...state.events, ...fresh]
    : [...state.events, ...fresh].sort((a, b) => a.sequence - b.sequence);
  return {
    ...state,
    events,
    eventIds,
    lastSequence: Math.max(state.lastSequence, ...fresh.map((e) => e.sequence)),
  };
}

function isSorted(events: readonly ExecutionEventDto[]): boolean {
  for (let i = 1; i < events.length; i += 1) {
    const prev = events[i - 1];
    const cur = events[i];
    if (prev && cur && cur.sequence < prev.sequence) return false;
  }
  return true;
}

export function taskEventsReducer(state: TaskEventsState, action: TaskEventsAction): TaskEventsState {
  switch (action.type) {
    case "received":
      return mergeEvents(state, action.events);
    case "connection":
      if (state.connection === action.state && state.reconnectAttempt === action.attempt) return state;
      return { ...state, connection: action.state, reconnectAttempt: action.attempt };
    case "reset":
      return initialTaskEventsState;
  }
}

/** Parses one SSE `data:` payload; returns null for anything that does not match the envelope. */
export function parseEventData(data: unknown): ExecutionEventDto | null {
  if (typeof data !== "string" || data.length === 0) return null;
  let json: unknown;
  try {
    json = JSON.parse(data);
  } catch {
    return null;
  }
  const parsed = executionEventSchema.safeParse(json);
  return parsed.success ? (parsed.data as ExecutionEventDto) : null;
}

export function parseEventList(items: unknown): ExecutionEventDto[] {
  if (!Array.isArray(items)) return [];
  const out: ExecutionEventDto[] = [];
  for (const item of items) {
    const parsed = executionEventSchema.safeParse(item);
    if (parsed.success) out.push(parsed.data as ExecutionEventDto);
  }
  return out;
}

export const TERMINAL_EVENT_TYPES: ReadonlySet<ExecutionEventType> = new Set(["TASK_COMPLETED", "TASK_FAILED", "TASK_CANCELLED"]);

/** Events after which the task resource (status, pending approval/input, observation) changed. */
export const STATUS_CHANGING_EVENT_TYPES: ReadonlySet<ExecutionEventType> = new Set([
  "TASK_STARTED",
  "APPROVAL_REQUIRED",
  "APPROVAL_GRANTED",
  "APPROVAL_REJECTED",
  "RECOVERY_STARTED",
  "EXECUTION_PAUSED",
  "CITIZEN_INPUT_REQUIRED",
  "EXECUTION_RESUMED",
  "SUBMISSION_APPROVAL_REQUIRED",
  "SUBMISSION_COMPLETED",
  "SUBMISSION_UNCERTAIN",
  "RECEIPT_VERIFIED",
  "DOCUMENT_REQUIRED",
  "TASK_COMPLETED",
  "TASK_FAILED",
  "TASK_CANCELLED",
]);

// ---------------------------------------------------------------------------
// EventSource abstraction + controller
// ---------------------------------------------------------------------------

export interface MessageLike {
  data: unknown;
  lastEventId?: string;
}

export interface EventSourceLike {
  onopen: ((event: Event) => void) | null;
  onerror: ((event: Event) => void) | null;
  addEventListener(type: string, listener: (event: MessageLike) => void): void;
  close(): void;
}

export type EventSourceFactory = (url: string) => EventSourceLike;

export const browserEventSourceFactory: EventSourceFactory = (url) =>
  new EventSource(url, { withCredentials: true }) as unknown as EventSourceLike;

/** 1s, 2s, 4s … capped at 30s, plus up to 30% jitter. */
export function defaultBackoff(attempt: number, random: () => number = Math.random): number {
  const base = Math.min(30_000, 1_000 * 2 ** Math.max(0, attempt - 1));
  return Math.round(base + base * 0.3 * random());
}

export interface TaskEventStreamOptions {
  url: string;
  createEventSource: EventSourceFactory;
  fetchHistory: (afterSequence: number, limit: number) => Promise<unknown>;
  onEvents: (events: ExecutionEventDto[]) => void;
  onConnectionChange: (state: ConnectionState, attempt: number) => void;
  /** Called before each reconnect; resolve `false` to stop (e.g. the task reached a terminal state). */
  beforeReconnect?: () => Promise<boolean>;
  backoff?: (attempt: number) => number;
  historyPageSize?: number;
  initialSequence?: number;
}

export class TaskEventStream {
  private source: EventSourceLike | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private attempt = 0;
  private stopped = true;
  /** Set by stop(): the consumer is gone, so nothing may be delivered any more. */
  private disposed = false;
  private lastSequence: number;
  private backfilling: Promise<void> | null = null;
  private backfillAgain = false;
  private readonly options: Required<Omit<TaskEventStreamOptions, "beforeReconnect" | "initialSequence">> &
    Pick<TaskEventStreamOptions, "beforeReconnect">;

  constructor(options: TaskEventStreamOptions) {
    this.options = {
      backoff: (attempt) => defaultBackoff(attempt),
      historyPageSize: 200,
      ...options,
    };
    this.lastSequence = options.initialSequence ?? 0;
  }

  get currentSequence(): number {
    return this.lastSequence;
  }

  start(): void {
    if (!this.stopped) return;
    this.stopped = false;
    this.disposed = false;
    this.attempt = 0;
    void this.backfill();
    this.connect();
  }

  stop(): void {
    this.stopped = true;
    this.disposed = true;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.closeSource();
  }

  private closeSource(): void {
    if (!this.source) return;
    this.source.onopen = null;
    this.source.onerror = null;
    this.source.close();
    this.source = null;
  }

  private connect(): void {
    if (this.stopped) return;
    this.options.onConnectionChange(this.attempt === 0 ? "connecting" : "reconnecting", this.attempt);
    const source = this.options.createEventSource(this.options.url);
    this.source = source;
    const listener = (event: MessageLike) => this.handleMessage(event);
    for (const type of EXECUTION_EVENT_TYPES) source.addEventListener(type, listener);
    source.addEventListener("message", listener);
    source.onopen = () => {
      if (this.stopped || this.source !== source) return;
      this.attempt = 0;
      this.options.onConnectionChange("open", 0);
      void this.backfill();
    };
    source.onerror = () => {
      if (this.stopped || this.source !== source) return;
      this.closeSource();
      this.scheduleReconnect();
    };
  }

  private scheduleReconnect(): void {
    this.attempt += 1;
    const attempt = this.attempt;
    this.options.onConnectionChange("reconnecting", attempt);
    this.timer = setTimeout(() => {
      this.timer = null;
      void this.reconnect();
    }, this.options.backoff(attempt));
  }

  private async reconnect(): Promise<void> {
    if (this.stopped) return;
    if (this.options.beforeReconnect) {
      let proceed = true;
      try {
        proceed = await this.options.beforeReconnect();
      } catch {
        proceed = true;
      }
      if (this.stopped) return;
      if (!proceed) {
        await this.backfill();
        this.finish();
        return;
      }
    }
    this.connect();
  }

  private finish(): void {
    if (this.disposed) return;
    this.stopped = true;
    this.closeSource();
    this.options.onConnectionChange("closed", 0);
  }

  private handleMessage(event: MessageLike): void {
    if (this.stopped) return;
    const parsed = parseEventData(event.data);
    if (!parsed) return;
    this.accept([parsed]);
    if (TERMINAL_EVENT_TYPES.has(parsed.type)) {
      // The run is over; pick up anything still in flight, then stop listening.
      void this.backfill().then(() => this.finish());
    }
  }

  private accept(events: ExecutionEventDto[]): void {
    if (events.length === 0 || this.disposed) return;
    for (const e of events) if (e.sequence > this.lastSequence) this.lastSequence = e.sequence;
    this.options.onEvents(events);
  }

  /** Pages through history after the last known sequence. Concurrent calls coalesce. */
  backfill(): Promise<void> {
    if (this.backfilling) {
      this.backfillAgain = true;
      return this.backfilling;
    }
    const run = async () => {
      do {
        this.backfillAgain = false;
        try {
          for (;;) {
            const batch = parseEventList(await this.options.fetchHistory(this.lastSequence, this.options.historyPageSize));
            const before = this.lastSequence;
            this.accept(batch);
            if (batch.length < this.options.historyPageSize || this.lastSequence === before) break;
          }
        } catch {
          // History is best-effort; the next (re)connect backfills again.
          break;
        }
      } while (this.backfillAgain && !this.disposed);
    };
    this.backfilling = run().finally(() => {
      this.backfilling = null;
    });
    return this.backfilling;
  }
}
