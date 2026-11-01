"use client";

import { Globe, MonitorSmartphone } from "lucide-react";
import { TaskScreenshot } from "@/features/agent/components/screenshot";
import { latestObservation, metaString, metaStringArray } from "@/features/agent/event-meta";
import { formatTime } from "@/lib/format";
import { humanize } from "@/lib/labels";
import type { AgentTaskDto, ExecutionEventDto } from "@/types/api";

export function BrowserView({ task, events }: { task: AgentTaskDto; events: ExecutionEventDto[] }) {
  const fromEvents = latestObservation(events);
  const observation = fromEvents ?? (task.lastObservation ? { ...task.lastObservation, title: task.lastObservation.title } : null);
  const actions = events.filter((e) => e.type === "BROWSER_ACTION_COMPLETED").slice(-5).reverse();
  const latestFields = actions[0] ? metaStringArray(actions[0].metadata, "fields") : [];

  return (
    <section aria-labelledby="browser-heading" className="rounded-2xl border bg-card shadow-card">
      <div className="flex items-center justify-between gap-3 border-b px-5 py-3.5">
        <h2 id="browser-heading" className="flex items-center gap-2 text-sm font-semibold">
          <MonitorSmartphone className="size-4 text-muted-foreground" aria-hidden="true" />
          Portal view
        </h2>
        {observation?.at ? <span className="text-xs text-muted-foreground tabular-nums">Captured {formatTime(observation.at)}</span> : null}
      </div>
      <div className="space-y-4 p-5">
        {observation ? (
          <>
            <div className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-[0.8125rem]">
              <Globe className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
              <span className="min-w-0 truncate" title={observation.url ?? undefined}>
                {observation.url ?? "Address not reported"}
              </span>
            </div>
            {observation.title ? <p className="text-sm font-medium">{observation.title}</p> : null}
            {observation.screenshotId ? (
              <TaskScreenshot taskId={task.id} screenshotId={observation.screenshotId} alt={`Latest portal screenshot${observation.title ? `: ${observation.title}` : ""}`} />
            ) : (
              <p className="text-sm text-muted-foreground">No screenshot for this page.</p>
            )}
          </>
        ) : (
          <p className="text-sm leading-relaxed text-muted-foreground">
            The portal has not been opened yet. Screenshots appear here once the browser agent starts.
          </p>
        )}

        {latestFields.length > 0 ? (
          <div>
            <h3 className="mb-1.5 text-sm font-medium">Fields on the latest step</h3>
            <ul className="flex flex-wrap gap-1.5">
              {latestFields.map((f) => (
                <li key={f} className="rounded-md bg-muted px-2 py-1 text-xs">
                  {f}
                </li>
              ))}
            </ul>
            <p className="mt-1.5 text-xs text-muted-foreground">Labels only. Values are shown in approval requests.</p>
          </div>
        ) : null}

        {actions.length > 0 ? (
          <div>
            <h3 className="mb-1.5 text-sm font-medium">Recent portal actions</h3>
            <ul className="space-y-1.5">
              {actions.map((e) => {
                const action = metaString(e.metadata, "action");
                return (
                  <li key={e.eventId} className="flex gap-2 text-[0.8125rem]">
                    <span className="shrink-0 text-muted-foreground tabular-nums">{formatTime(e.timestamp)}</span>
                    <span>
                      {action ? <span className="font-medium">{humanize(action)}: </span> : null}
                      {e.message}
                    </span>
                  </li>
                );
              })}
            </ul>
          </div>
        ) : null}
      </div>
    </section>
  );
}
