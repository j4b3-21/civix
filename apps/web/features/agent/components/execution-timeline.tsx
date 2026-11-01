"use client";

import * as React from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import {
  Ban,
  BadgeCheck,
  CheckCircle2,
  CircleHelp,
  CirclePlus,
  FileText,
  FileWarning,
  Globe,
  ListChecks,
  LifeBuoy,
  MousePointerClick,
  Pause,
  Play,
  Scale,
  Search,
  Send,
  ShieldCheck,
  ShieldX,
  UserCheck,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { EligibilityBadge } from "@/components/common/status-badges";
import { GATE_EVENTS, metaBoolean, metaNumber, metaString, metaStringArray } from "@/features/agent/event-meta";
import { formatTime } from "@/lib/format";
import { AGENT_LABELS, CITIZEN_INPUT_LABELS, EVENT_LABELS, RECOVERY_REASON_LABELS, humanize } from "@/lib/labels";
import { cn } from "@/lib/utils";
import {
  CITIZEN_INPUT_KINDS,
  ELIGIBILITY_OUTCOMES,
  RECOVERY_REASONS,
  type CitizenInputKind,
  type EligibilityOutcome,
  type ExecutionEventDto,
  type ExecutionEventType,
  type RecoveryReason,
} from "@/types/api";

const ICONS: Record<ExecutionEventType, LucideIcon> = {
  TASK_CREATED: CirclePlus,
  TASK_STARTED: Play,
  PROFILE_VALIDATED: UserCheck,
  SERVICE_DISCOVERED: Search,
  ELIGIBILITY_EVALUATED: Scale,
  DOCUMENT_REQUIRED: FileWarning,
  DOCUMENT_EXTRACTED: FileText,
  PLAN_CREATED: ListChecks,
  APPROVAL_REQUIRED: ShieldCheck,
  APPROVAL_GRANTED: ShieldCheck,
  APPROVAL_REJECTED: ShieldX,
  BROWSER_STARTED: Globe,
  BROWSER_ACTION_COMPLETED: MousePointerClick,
  RECOVERY_STARTED: LifeBuoy,
  EXECUTION_PAUSED: Pause,
  CITIZEN_INPUT_REQUIRED: UserCheck,
  EXECUTION_RESUMED: Play,
  SUBMISSION_APPROVAL_REQUIRED: ShieldCheck,
  SUBMISSION_COMPLETED: Send,
  SUBMISSION_UNCERTAIN: CircleHelp,
  RECEIPT_VERIFIED: BadgeCheck,
  TASK_COMPLETED: CheckCircle2,
  TASK_FAILED: XCircle,
  TASK_CANCELLED: Ban,
};

type Tone = "default" | "gate" | "recovery" | "error" | "success" | "warning" | "muted";

function toneOf(type: ExecutionEventType): Tone {
  if (GATE_EVENTS.has(type)) return "gate";
  if (type === "RECOVERY_STARTED") return "recovery";
  if (type === "TASK_FAILED" || type === "APPROVAL_REJECTED") return "error";
  if (type === "RECEIPT_VERIFIED" || type === "TASK_COMPLETED" || type === "APPROVAL_GRANTED" || type === "SUBMISSION_COMPLETED") return "success";
  if (type === "SUBMISSION_UNCERTAIN" || type === "DOCUMENT_REQUIRED" || type === "EXECUTION_PAUSED") return "warning";
  if (type === "TASK_CANCELLED") return "muted";
  return "default";
}

const NODE: Record<Tone, string> = {
  default: "rounded-full border bg-card text-muted-foreground",
  gate: "rounded-[10px] bg-brand-soft text-brand-strong ring-1 ring-brand/40",
  recovery: "rounded-full bg-warning-soft text-warning ring-1 ring-warning/40",
  error: "rounded-full bg-danger-soft text-danger ring-1 ring-danger/40",
  success: "rounded-full bg-success-soft text-success ring-1 ring-success/30",
  warning: "rounded-full bg-warning-soft text-warning",
  muted: "rounded-full bg-muted text-muted-foreground",
};

const ROW: Partial<Record<Tone, string>> = {
  recovery: "rounded-xl border border-warning/30 bg-warning-soft/70 px-3 py-2.5",
  error: "rounded-xl border border-danger/30 bg-danger-soft px-3 py-2.5",
};

function pathOf(url: string): string {
  try {
    const u = new URL(url);
    return `${u.hostname}${u.pathname === "/" ? "" : u.pathname}`;
  } catch {
    return url;
  }
}

function EventDetails({ event }: { event: ExecutionEventDto }) {
  const m = event.metadata;
  switch (event.type) {
    case "RECOVERY_STARTED": {
      const reason = metaString(m, "reason");
      const attempt = metaNumber(m, "attempt");
      const max = metaNumber(m, "maxAttempts");
      return (
        <p className="text-[0.8125rem] text-foreground/80">
          {reason && RECOVERY_REASONS.includes(reason as RecoveryReason) ? RECOVERY_REASON_LABELS[reason as RecoveryReason] : reason ? humanize(reason) : null}
          {attempt !== null ? (
            <span className="tabular-nums">
              {reason ? ". " : ""}Attempt {attempt}
              {max !== null ? ` of ${max}` : ""}
            </span>
          ) : null}
        </p>
      );
    }
    case "TASK_FAILED": {
      const code = metaString(m, "errorCode");
      const retryable = metaBoolean(m, "retryable");
      return (
        <p className="text-[0.8125rem] text-foreground/80">
          {code ? <span className="font-medium">{humanize(code)}. </span> : null}
          {retryable === true ? "This run can be retried." : retryable === false ? "This run cannot be retried as is." : null}
        </p>
      );
    }
    case "ELIGIBILITY_EVALUATED": {
      const outcome = metaString(m, "outcome");
      return outcome && ELIGIBILITY_OUTCOMES.includes(outcome as EligibilityOutcome) ? <EligibilityBadge outcome={outcome as EligibilityOutcome} /> : null;
    }
    case "PLAN_CREATED": {
      const steps = metaNumber(m, "steps");
      return steps !== null ? <p className="text-[0.8125rem] text-muted-foreground tabular-nums">{steps} steps planned</p> : null;
    }
    case "CITIZEN_INPUT_REQUIRED": {
      const kind = metaString(m, "kind");
      return kind && CITIZEN_INPUT_KINDS.includes(kind as CitizenInputKind) ? (
        <p className="text-[0.8125rem] text-brand-strong">{CITIZEN_INPUT_LABELS[kind as CitizenInputKind]} needed from you</p>
      ) : null;
    }
    case "RECEIPT_VERIFIED": {
      const ref = metaString(m, "referenceNumber");
      return ref ? (
        <p className="text-[0.8125rem]">
          Reference <span className="font-medium tabular-nums">{ref}</span>
          {metaBoolean(m, "isTestEnvironment") ? " (test environment)" : ""}
        </p>
      ) : null;
    }
    case "BROWSER_STARTED":
    case "BROWSER_ACTION_COMPLETED": {
      const url = metaString(m, "url");
      const fields = metaStringArray(m, "fields");
      if (!url && fields.length === 0) return null;
      return (
        <div className="space-y-1.5">
          {url ? <p className="truncate text-[0.8125rem] text-muted-foreground">{pathOf(url)}</p> : null}
          {fields.length > 0 ? (
            <ul className="flex flex-wrap gap-1" aria-label="Fields">
              {fields.map((f) => (
                <li key={f} className="rounded-md bg-muted px-1.5 py-0.5 text-xs">
                  {f}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      );
    }
    default:
      return null;
  }
}

export function ExecutionTimeline({ events }: { events: ExecutionEventDto[] }) {
  const reduceMotion = useReducedMotion();
  const ordered = React.useMemo(() => [...events].reverse(), [events]);
  const latest = events.at(-1);

  return (
    <div>
      <div aria-live="polite" aria-atomic="true" className="sr-only">
        {latest ? `${EVENT_LABELS[latest.type]}. ${latest.message}` : ""}
      </div>
      {ordered.length === 0 ? (
        <p className="py-6 text-sm text-muted-foreground">Waiting for the first step. Events appear here as the agent works.</p>
      ) : (
        <ol role="log" aria-label="Execution timeline, newest first" className="relative">
          <AnimatePresence initial={false}>
            {ordered.map((event, index) => {
              const tone = toneOf(event.type);
              const Icon = ICONS[event.type];
              const isLast = index === ordered.length - 1;
              return (
                <motion.li
                  key={event.eventId}
                  layout={reduceMotion ? false : "position"}
                  initial={reduceMotion ? false : { opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.22, ease: "easeOut" }}
                  className="relative flex gap-3 pb-5"
                >
                  {!isLast ? <span aria-hidden="true" className="absolute top-8 bottom-0 left-[15px] w-px bg-border" /> : null}
                  <span aria-hidden="true" className={cn("relative z-10 flex size-8 shrink-0 items-center justify-center", NODE[tone])}>
                    <Icon className="size-4" />
                  </span>
                  <div className={cn("min-w-0 flex-1 space-y-1 pt-0.5", ROW[tone])}>
                    <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
                      <p className={cn("text-sm font-medium", tone === "gate" && "text-brand-strong", tone === "error" && "text-danger")}>
                        {EVENT_LABELS[event.type]}
                      </p>
                      <time dateTime={event.timestamp} className="text-xs text-muted-foreground tabular-nums">
                        {formatTime(event.timestamp)}
                      </time>
                    </div>
                    {event.message ? <p className="text-sm leading-relaxed text-foreground/85">{event.message}</p> : null}
                    <EventDetails event={event} />
                    {event.agent ? (
                      <p className="text-xs text-muted-foreground">
                        {AGENT_LABELS[event.agent]} agent
                        {event.step ? <>, step {humanize(event.step).toLowerCase()}</> : null}
                      </p>
                    ) : null}
                  </div>
                </motion.li>
              );
            })}
          </AnimatePresence>
        </ol>
      )}
    </div>
  );
}
