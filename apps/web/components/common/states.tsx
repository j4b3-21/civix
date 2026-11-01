import * as React from "react";
import { AlertCircle, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { errorMessage, errorReference } from "@/lib/api/errors";
import { cn } from "@/lib/utils";

export function PageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col gap-4 pb-8 sm:flex-row sm:items-end sm:justify-between", className)}>
      <div className="min-w-0 space-y-1.5">
        <h1 className="text-headline font-semibold text-foreground">{title}</h1>
        {description ? <p className="max-w-2xl text-[0.9375rem] leading-relaxed text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function SectionHeading({
  title,
  description,
  action,
  as: Tag = "h2",
  id,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  as?: "h2" | "h3";
  id?: string;
}) {
  return (
    <div className="mb-4 flex items-end justify-between gap-4">
      <div className="space-y-1">
        <Tag id={id} className="text-base font-semibold text-foreground">
          {title}
        </Tag>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: {
  icon?: React.ComponentType<{ className?: string; "aria-hidden"?: boolean | "true" }>;
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-start gap-3 rounded-2xl border border-dashed bg-card/50 px-6 py-8", className)}>
      {Icon ? (
        <span className="flex size-10 items-center justify-center rounded-xl bg-brand-soft text-brand-strong">
          <Icon className="size-5" aria-hidden="true" />
        </span>
      ) : null}
      <div className="space-y-1">
        <p className="font-medium text-foreground">{title}</p>
        {description ? <div className="max-w-prose text-sm leading-relaxed text-muted-foreground">{description}</div> : null}
      </div>
      {action}
    </div>
  );
}

export function ErrorState({
  error,
  title = "This could not be loaded",
  onRetry,
  className,
}: {
  error: unknown;
  title?: string;
  onRetry?: () => void;
  className?: string;
}) {
  const reference = errorReference(error);
  return (
    <div role="alert" className={cn("flex flex-col items-start gap-3 rounded-2xl border border-danger/25 bg-danger-soft px-5 py-4", className)}>
      <div className="flex items-start gap-3">
        <AlertCircle className="mt-0.5 size-5 shrink-0 text-danger" aria-hidden="true" />
        <div className="space-y-1">
          <p className="font-medium text-foreground">{title}</p>
          <p className="text-sm text-foreground/80">{errorMessage(error)}</p>
          {reference ? <p className="text-xs text-muted-foreground">Reference: {reference}</p> : null}
        </div>
      </div>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCw aria-hidden="true" />
          Try again
        </Button>
      ) : null}
    </div>
  );
}

export function InlineError({ error, className }: { error: unknown; className?: string }) {
  if (!error) return null;
  return (
    <p role="alert" className={cn("flex items-start gap-2 text-sm text-danger", className)}>
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{errorMessage(error)}</span>
    </p>
  );
}

export function ListSkeleton({ rows = 3, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn("space-y-3", className)} role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="rounded-2xl border bg-card p-5">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="mt-3 h-3 w-2/3" />
          <Skeleton className="mt-2 h-3 w-1/2" />
        </div>
      ))}
      <span className="sr-only">Loading</span>
    </div>
  );
}

export function Notice({
  tone = "info",
  icon: Icon,
  title,
  children,
  className,
}: {
  tone?: "info" | "warning" | "danger" | "success" | "brand";
  icon?: React.ComponentType<{ className?: string; "aria-hidden"?: boolean | "true" }>;
  title?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
}) {
  const tones = {
    info: "border-info/20 bg-info-soft",
    warning: "border-warning/25 bg-warning-soft",
    danger: "border-danger/25 bg-danger-soft",
    success: "border-success/25 bg-success-soft",
    brand: "border-brand/25 bg-brand-soft",
  } as const;
  const iconTones = { info: "text-info", warning: "text-warning", danger: "text-danger", success: "text-success", brand: "text-brand-strong" };
  return (
    <div className={cn("flex gap-3 rounded-2xl border px-4 py-3.5 text-sm", tones[tone], className)}>
      {Icon ? <Icon className={cn("mt-0.5 size-4.5 shrink-0", iconTones[tone])} aria-hidden="true" /> : null}
      <div className="min-w-0 space-y-1 leading-relaxed text-foreground/90">
        {title ? <p className="font-medium text-foreground">{title}</p> : null}
        {children}
      </div>
    </div>
  );
}
