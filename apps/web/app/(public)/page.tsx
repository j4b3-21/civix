import Link from "next/link";
import { BookOpenCheck, FileSearch, FolderLock, MonitorPlay } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CheckpointRail } from "@/components/marketing/checkpoint-rail";

const CAPABILITIES = [
  {
    icon: FileSearch,
    title: "Discovery",
    body: "Describe your situation and CIVIX searches its registry of central and state services. Results explain why each one matched.",
    scope: "Covers the services in the CIVIX registry, not every scheme in India.",
  },
  {
    icon: BookOpenCheck,
    title: "Eligibility",
    body: "A deterministic rules engine compares your profile with each published criterion and shows pass, fail or unknown for every rule, with the source it came from.",
    scope: "An indication on the information you gave. The department decides.",
  },
  {
    icon: FolderLock,
    title: "Documents",
    body: "Upload certificates once. CIVIX reads them and suggests profile values, which reach your profile only after you accept each one.",
    scope: "Reading a document is not verifying it. CIVIX does not check authenticity.",
  },
  {
    icon: MonitorPlay,
    title: "Supervised application execution",
    body: "For verified workflows, the CIVIX agent fills in the portal form while you watch, pausing for your approval before filling, uploading and submitting.",
    scope: "Limited to workflows that have been verified on the real, permitted portal.",
  },
];

const NEVER = [
  "Submit an application without your explicit approval",
  "Enter OTPs, solve CAPTCHAs or sign in to portals for you",
  "Make or automate payments",
  "Invent eligibility rules, deadlines, reference numbers or receipts",
  "Treat a document it has read as verified",
];

export default function HomePage() {
  return (
    <>
      <section className="mx-auto grid max-w-6xl gap-14 px-5 pt-16 pb-20 sm:px-8 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-20 lg:pt-24">
        <div className="max-w-xl">
          <h1 className="text-[2.5rem] leading-[1.05] font-semibold tracking-[-0.035em] text-foreground sm:text-display">
            Government services, handled intelligently.
          </h1>
          <p className="mt-6 text-lg leading-relaxed text-muted-foreground">
            Find the services that fit your situation, see which published rule every eligibility answer comes from, keep your
            documents in one place and, where a workflow has been verified, let CIVIX prepare the application while you approve each
            step.
          </p>
          <div className="mt-9 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href="/register">Create an account</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/how-it-works">See how it works</Link>
            </Button>
          </div>
          <p className="mt-8 max-w-md border-l-2 border-brand pl-4 text-sm leading-relaxed text-muted-foreground">
            Live portal automation is limited to verified workflows. For everything else CIVIX explains the rules and steps, and you
            apply on the official portal.
          </p>
        </div>
        <div className="rounded-2xl border bg-card p-6 shadow-card sm:p-8">
          <p className="mb-6 text-sm font-medium text-foreground">
            From a question to a verified receipt
            <span className="mt-1 block font-normal text-muted-foreground">Teal squares are where CIVIX stops and you decide.</span>
          </p>
          <CheckpointRail />
        </div>
      </section>

      <section className="border-y bg-card/60">
        <div className="mx-auto max-w-6xl px-5 py-20 sm:px-8">
          <h2 className="max-w-xl text-headline font-semibold">What CIVIX does, and how far each part goes</h2>
          <div className="mt-12 grid gap-x-12 gap-y-12 md:grid-cols-2">
            {CAPABILITIES.map(({ icon: Icon, title, body, scope }) => (
              <div key={title} className="flex gap-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <div className="space-y-2">
                  <h3 className="text-title font-semibold">{title}</h3>
                  <p className="leading-relaxed text-foreground/85">{body}</p>
                  <p className="text-sm leading-relaxed text-muted-foreground">{scope}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-6xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-2">
        <div className="space-y-4">
          <h2 className="text-headline font-semibold">What CIVIX never does</h2>
          <p className="max-w-md leading-relaxed text-muted-foreground">
            These are enforced by the system, not just promised: the agent and the browser worker each check your approval before any
            consequential action.
          </p>
        </div>
        <ul className="divide-y rounded-2xl border bg-card shadow-card">
          {NEVER.map((line) => (
            <li key={line} className="flex items-start gap-3 px-5 py-4 text-[0.9375rem]">
              <span aria-hidden="true" className="mt-[0.55rem] h-0.5 w-3 shrink-0 rounded-full bg-danger" />
              {line}
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto max-w-6xl px-5 pb-24 sm:px-8">
        <div className="flex flex-col gap-6 rounded-2xl bg-primary px-8 py-10 text-primary-foreground sm:flex-row sm:items-center sm:justify-between">
          <div className="max-w-lg space-y-2">
            <h2 className="text-title font-semibold">Start with what you already know</h2>
            <p className="text-sm leading-relaxed text-primary-foreground/80">
              Create an account, choose what CIVIX may do with your data, and ask about a service. You can change every permission
              later in the Trust Center.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg" variant="secondary">
              <Link href="/register">Create an account</Link>
            </Button>
            <Button asChild size="lg" variant="ghost" className="text-primary-foreground hover:bg-white/10">
              <Link href="/services">Browse services</Link>
            </Button>
          </div>
        </div>
      </section>
    </>
  );
}
