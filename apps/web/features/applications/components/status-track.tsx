import { cn } from "@/lib/utils";
import { APPLICATION_STATUS_LABELS } from "@/lib/labels";
import type { ApplicationStatus } from "@/types/api";

const MAIN: ApplicationStatus[] = ["DRAFT", "IN_PROGRESS", "SUBMITTED"];

/** Where the application sits on the main path, with side states called out. */
export function StatusTrack({ status }: { status: ApplicationStatus }) {
  const position = status === "DRAFT" ? 0 : status === "SUBMITTED" ? 2 : 1;
  const side = !MAIN.includes(status) ? status : null;
  return (
    <div>
      <ol className="flex items-center gap-2" aria-label="Application progress">
        {MAIN.map((step, index) => {
          const done = index < position || (index === position && step === status);
          const current = index === position;
          return (
            <li key={step} className="flex flex-1 items-center gap-2" aria-current={current ? "step" : undefined}>
              <span
                className={cn(
                  "h-1.5 flex-1 rounded-full",
                  done ? "bg-brand" : current ? (side === "FAILED" || side === "CANCELLED" ? "bg-danger/60" : "bg-warning/70") : "bg-muted",
                )}
                aria-hidden="true"
              />
              <span className={cn("text-xs font-medium whitespace-nowrap", current ? "text-foreground" : "text-muted-foreground")}>
                {APPLICATION_STATUS_LABELS[step]}
              </span>
            </li>
          );
        })}
      </ol>
      {side ? <p className="mt-2 text-sm text-muted-foreground">Currently: {APPLICATION_STATUS_LABELS[side]}</p> : null}
    </div>
  );
}
