"use client";

import { AppFrame } from "@/components/shell/app-shell";
import { PublicShell } from "@/components/shell/public-shell";
import { Skeleton } from "@/components/ui/skeleton";
import { useMe } from "@/features/auth/auth-provider";

/** Pages that work signed-in and signed-out (the service catalog) pick their chrome from /auth/me. */
export function AdaptiveShell({ children }: { children: React.ReactNode }) {
  const me = useMe("throw");
  if (me.data) return <AppFrame user={me.data}>{children}</AppFrame>;
  if (me.isPending) {
    return (
      <div className="mx-auto max-w-6xl space-y-4 px-5 py-16" role="status" aria-label="Loading">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
    );
  }
  return (
    <PublicShell>
      <div className="mx-auto w-full max-w-6xl px-5 py-10 sm:px-8">{children}</div>
    </PublicShell>
  );
}
