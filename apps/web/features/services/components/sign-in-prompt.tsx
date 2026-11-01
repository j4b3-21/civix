import Link from "next/link";
import { LockKeyhole } from "lucide-react";
import { Button } from "@/components/ui/button";

export function SignInPrompt({ next }: { next: string }) {
  return (
    <div className="flex flex-col items-start gap-4 rounded-2xl border bg-card p-8 shadow-card">
      <span className="flex size-10 items-center justify-center rounded-xl bg-brand-soft text-brand-strong">
        <LockKeyhole className="size-5" aria-hidden="true" />
      </span>
      <div className="max-w-lg space-y-2">
        <h2 className="text-title font-semibold">Sign in to browse the service catalog</h2>
        <p className="leading-relaxed text-muted-foreground">
          The catalog is available to signed-in citizens because each entry can show your own eligibility result next to it. Creating an
          account is free, and you choose what CIVIX may do with your data.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link href={`/login?next=${encodeURIComponent(next)}`}>Sign in</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/register">Create an account</Link>
        </Button>
      </div>
    </div>
  );
}
