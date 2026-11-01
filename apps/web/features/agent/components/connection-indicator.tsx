import { cn } from "@/lib/utils";
import type { ConnectionState } from "@/lib/sse/task-events";

const COPY: Record<ConnectionState, { label: string; dot: string }> = {
  connecting: { label: "Connecting to live updates", dot: "bg-muted-foreground/60" },
  open: { label: "Live", dot: "bg-live" },
  reconnecting: { label: "Reconnecting", dot: "bg-warning" },
  closed: { label: "Live updates ended", dot: "bg-muted-foreground/40" },
};

export function ConnectionIndicator({ state, attempt }: { state: ConnectionState; attempt: number }) {
  const { label, dot } = COPY[state];
  return (
    <span className="inline-flex items-center gap-2 text-[0.8125rem] font-medium text-muted-foreground" role="status">
      <span className="relative flex size-2.5" aria-hidden="true">
        {state === "open" ? <span className="absolute inline-flex size-full animate-ping rounded-full bg-live opacity-50 motion-reduce:animate-none" /> : null}
        <span className={cn("relative inline-flex size-2.5 rounded-full", dot)} />
      </span>
      {label}
      {state === "reconnecting" && attempt > 1 ? <span className="tabular-nums">(attempt {attempt})</span> : null}
    </span>
  );
}
