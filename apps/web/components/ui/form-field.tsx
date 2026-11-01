import * as React from "react";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/** Label + control + hint + error, wired with aria-describedby / aria-invalid. */
export function FormField({
  id,
  label,
  hint,
  error,
  className,
  optional,
  children,
}: {
  id: string;
  label: React.ReactNode;
  hint?: React.ReactNode;
  error?: string | undefined;
  optional?: boolean;
  className?: string;
  children: (props: { id: string; "aria-invalid": boolean | undefined; "aria-describedby": string | undefined }) => React.ReactNode;
}) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  return (
    <div className={cn("space-y-2", className)}>
      <Label htmlFor={id}>
        {label}
        {optional ? <span className="font-normal text-muted-foreground">(optional)</span> : null}
      </Label>
      {children({ id, "aria-invalid": error ? true : undefined, "aria-describedby": describedBy })}
      {hint && !error ? (
        <p id={hintId} className="text-[0.8125rem] leading-relaxed text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-[0.8125rem] font-medium text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
