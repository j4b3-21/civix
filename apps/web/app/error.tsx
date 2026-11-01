"use client";

import { useEffect } from "react";
import { Button } from "@/components/ui/button";

export default function RouteError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <main id="main" className="mx-auto flex min-h-[60dvh] max-w-lg flex-col justify-center gap-5 px-6">
      <h1 className="text-headline font-semibold">This page stopped working</h1>
      <p className="leading-relaxed text-muted-foreground">
        An unexpected error occurred while showing this page. Your data has not changed.
        {error.digest ? ` Reference: ${error.digest}.` : ""}
      </p>
      <div>
        <Button onClick={reset}>Try again</Button>
      </div>
    </main>
  );
}
