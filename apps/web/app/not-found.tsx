import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main id="main" className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center gap-6 px-6">
      <Logo />
      <div className="space-y-2">
        <h1 className="text-headline font-semibold">This page does not exist</h1>
        <p className="leading-relaxed text-muted-foreground">The address may be mistyped, or the item may have been deleted.</p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link href="/dashboard">Go to dashboard</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/">Home</Link>
        </Button>
      </div>
    </main>
  );
}
