"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { refresh } from "@/lib/api/auth";
import { safeNextPath } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import { LoginForm } from "@/features/auth/components/login-form";

export function LoginView() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const next = safeNextPath(searchParams.get("next"));
  const [checking, setChecking] = React.useState(true);

  // The refresh cookie is scoped to /api/v1/auth, so page navigation cannot see it.
  // Try a silent refresh once; if the session is still valid, continue to `next`.
  React.useEffect(() => {
    let cancelled = false;
    refresh()
      .then((user) => {
        if (cancelled) return;
        queryClient.setQueryData(queryKeys.me, user);
        router.replace(next);
      })
      .catch(() => {
        if (!cancelled) setChecking(false);
      });
    return () => {
      cancelled = true;
    };
  }, [next, queryClient, router]);

  if (checking) {
    return (
      <div role="status" className="flex items-center gap-3 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        Checking your session
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h1 className="text-headline font-semibold">Sign in</h1>
        <p className="text-[0.9375rem] text-muted-foreground">
          New to CIVIX?{" "}
          <Link href={`/register${next !== "/dashboard" ? `?next=${encodeURIComponent(next)}` : ""}`} className="font-medium text-brand-strong underline underline-offset-4">
            Create an account
          </Link>
        </p>
      </div>
      <LoginForm onSuccess={() => router.replace(next)} />
    </div>
  );
}
