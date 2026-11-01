import { cn } from "@/lib/utils";

export interface RailStep {
  title: string;
  detail: string;
  /** A point where CIVIX stops and the citizen decides or acts. */
  gate?: boolean;
}

export const PIPELINE: RailStep[] = [
  { title: "Describe what you need", detail: "In your own words, or browse the catalog." },
  { title: "Matching services", detail: "Central and state services that fit your situation." },
  { title: "Eligibility, rule by rule", detail: "Each rule checked against your profile, with its official source." },
  { title: "Documents ready", detail: "Uploaded, read, and reviewed by you before anything is used." },
  { title: "You approve the form", detail: "See every value CIVIX will enter before it starts.", gate: true },
  { title: "CIVIX fills the portal form", detail: "Only on verified workflows. You watch it live." },
  { title: "You handle OTP, CAPTCHA and payment", detail: "CIVIX pauses and hands control back to you.", gate: true },
  { title: "You approve the final submission", detail: "A separate, explicit approval for the one irreversible step.", gate: true },
  { title: "Receipt checked on the portal", detail: "A submission counts only when the portal's acknowledgement is found." },
];

/** The CIVIX motif: a path with teal checkpoints where control returns to the citizen. */
export function CheckpointRail({ steps = PIPELINE, className }: { steps?: RailStep[]; className?: string }) {
  const numbers = steps.map((_, index) => steps.slice(0, index + 1).filter((s) => !s.gate).length);
  return (
    <ol className={cn("relative", className)}>
      {steps.map((step, index) => {
        const isLast = index === steps.length - 1;
        const n = numbers[index];
        return (
          <li key={step.title} className="relative flex gap-4 pb-6 last:pb-0">
            {!isLast ? <span aria-hidden="true" className="absolute top-8 bottom-0 left-[15px] w-px bg-border" /> : null}
            {step.gate ? (
              <span
                aria-hidden="true"
                className="relative z-10 mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-[10px] bg-brand-soft ring-1 ring-brand/40"
              >
                <span className="size-3 rounded-[3px] bg-brand" />
              </span>
            ) : (
              <span
                aria-hidden="true"
                className="relative z-10 mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full border bg-card text-xs font-semibold text-muted-foreground tabular-nums"
              >
                {n}
              </span>
            )}
            <div className="min-w-0 pt-1">
              <p className={cn("text-[0.9375rem] font-medium leading-snug", step.gate ? "text-brand-strong" : "text-foreground")}>
                {step.gate ? <span className="sr-only">Your checkpoint: </span> : null}
                {step.title}
              </p>
              <p className="mt-0.5 text-sm leading-relaxed text-muted-foreground">{step.detail}</p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
