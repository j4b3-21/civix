import Link from "next/link";
import { cn } from "@/lib/utils";

/** The mark: a path that pauses at a checkpoint (the teal gate) before continuing. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn("size-8", className)}>
      <rect width="32" height="32" rx="9" fill="#0B1B3B" />
      <path d="M21.5 10.2A8 8 0 1 0 21.5 21.8" fill="none" stroke="#F7F7F5" strokeWidth="2.6" strokeLinecap="round" />
      <rect x="20" y="13.6" width="4.8" height="4.8" rx="1.4" fill="#0EA5A4" />
    </svg>
  );
}

export function Logo({ href = "/", inverted = false, className }: { href?: string; inverted?: boolean; className?: string }) {
  return (
    <Link
      href={href}
      className={cn("inline-flex items-center gap-2.5 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring", className)}
    >
      <LogoMark />
      <span className={cn("text-[1.0625rem] font-semibold tracking-[0.14em]", inverted ? "text-white" : "text-foreground")}>CIVIX</span>
    </Link>
  );
}
