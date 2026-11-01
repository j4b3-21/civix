import * as React from "react";

export function ContentPage({ title, lead, updated, children }: { title: string; lead: string; updated?: string; children: React.ReactNode }) {
  return (
    <article className="mx-auto max-w-6xl px-5 pt-16 pb-24 sm:px-8 lg:pt-20">
      <header className="max-w-2xl">
        <h1 className="text-[2.25rem] leading-[1.1] font-semibold tracking-[-0.03em] sm:text-[2.75rem]">{title}</h1>
        <p className="mt-5 text-lg leading-relaxed text-muted-foreground">{lead}</p>
        {updated ? <p className="mt-4 text-sm text-muted-foreground">{updated}</p> : null}
      </header>
      <div className="prose-civix mt-10">{children}</div>
    </article>
  );
}
