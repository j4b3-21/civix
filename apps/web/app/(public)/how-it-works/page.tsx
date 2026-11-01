import type { Metadata } from "next";
import Link from "next/link";
import { CheckpointRail } from "@/components/marketing/checkpoint-rail";
import { Button } from "@/components/ui/button";

export const metadata: Metadata = { title: "How it works" };

const AGENTS = [
  { name: "Profile", body: "Reads the profile fields you consented to share for this run and notes which ones are unconfirmed." },
  { name: "Discovery", body: "Matches your request to services in the CIVIX registry." },
  { name: "Eligibility", body: "Runs the deterministic rules engine. A failed rule stops the run; a missing value asks you for it." },
  { name: "Documents", body: "Checks that each mandatory document is uploaded and reviewed by you." },
  { name: "Planning", body: "Turns the verified workflow into concrete form steps and the exact values it intends to enter." },
  { name: "Browser", body: "Works in an isolated browser limited to the service's allowed portal domains." },
  { name: "Recovery", body: "Retries a failed step a limited number of times, replaying only reversible actions." },
  { name: "Verification", body: "Looks for the portal's acknowledgement and reference number before calling a submission complete." },
];

const APPROVALS = [
  {
    title: "Preparation approval",
    body: "Before any form field is filled, you see every value CIVIX plans to enter and approve or reject the plan.",
  },
  {
    title: "Document upload approval",
    body: "Uploading a document to a portal needs a separate approval scoped to exactly those documents.",
  },
  {
    title: "Final submission approval",
    body: "Submitting is consequential and cannot be undone. It needs its own approval, which is checked again by the agent and by the browser worker at the moment of submission.",
  },
];

const NEVER = [
  "Bypass or solve an OTP. When a portal sends one, CIVIX pauses and you type it in. The value is passed through once and never stored.",
  "Bypass or solve a CAPTCHA. You see the challenge and answer it yourself.",
  "Sign in to a portal on your behalf without you, or keep your portal password.",
  "Make a payment. When a portal asks for payment, you complete it on the portal yourself.",
  "Submit without your approval, or reuse an approval after your information changed.",
  "Retry a submission whose outcome is unclear. If the portal's acknowledgement cannot be found, CIVIX marks the submission as unconfirmed and tells you how to check.",
];

export default function HowItWorksPage() {
  return (
    <article className="mx-auto max-w-6xl px-5 pt-16 pb-24 sm:px-8 lg:pt-20">
      <header className="max-w-2xl">
        <h1 className="text-[2.25rem] leading-[1.1] font-semibold tracking-[-0.03em] sm:text-[2.75rem]">How it works</h1>
        <p className="mt-5 text-lg leading-relaxed text-muted-foreground">
          CIVIX is a pipeline of small, single-purpose agents. They can explain and prepare. Consequential actions wait for you.
        </p>
      </header>

      <div className="mt-14 grid gap-14 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <section aria-labelledby="pipeline">
          <h2 id="pipeline" className="mb-6 text-title font-semibold">The pipeline</h2>
          <div className="rounded-2xl border bg-card p-6 shadow-card">
            <CheckpointRail />
          </div>
        </section>
        <section aria-labelledby="agents">
          <h2 id="agents" className="mb-6 text-title font-semibold">The agents</h2>
          <dl className="divide-y rounded-2xl border bg-card shadow-card">
            {AGENTS.map((a) => (
              <div key={a.name} className="grid gap-1 px-5 py-4 sm:grid-cols-[8rem_1fr] sm:gap-4">
                <dt className="font-medium">{a.name}</dt>
                <dd className="text-sm leading-relaxed text-muted-foreground">{a.body}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
            Every step is recorded as an event you can follow live in the control room. Event messages contain field labels, never
            your field values, passwords or OTPs.
          </p>
        </section>
      </div>

      <section aria-labelledby="approvals" className="mt-20">
        <h2 id="approvals" className="text-headline font-semibold">Three approvals, each for one thing</h2>
        <ol className="mt-8 grid gap-4 md:grid-cols-3">
          {APPROVALS.map((a, i) => (
            <li key={a.title} className="rounded-2xl border bg-card p-6 shadow-card">
              <span className="text-sm font-medium text-brand-strong tabular-nums">Approval {i + 1}</span>
              <h3 className="mt-2 font-semibold">{a.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{a.body}</p>
            </li>
          ))}
        </ol>
        <p className="mt-6 max-w-2xl text-sm leading-relaxed text-muted-foreground">
          Approvals expire after a short time (15 minutes by default). If your profile or documents change after an approval was
          requested, the request is withdrawn and CIVIX asks again with the new information.
        </p>
      </section>

      <section aria-labelledby="never" className="mt-20 grid gap-10 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
        <div>
          <h2 id="never" className="text-headline font-semibold">What CIVIX never does</h2>
          <p className="mt-4 leading-relaxed text-muted-foreground">
            Live portal automation is limited to verified workflows. Everywhere else CIVIX is a guide.
          </p>
        </div>
        <ul className="divide-y rounded-2xl border bg-card shadow-card">
          {NEVER.map((line) => (
            <li key={line} className="flex gap-3 px-5 py-4 text-[0.9375rem] leading-relaxed">
              <span aria-hidden="true" className="mt-[0.7rem] h-0.5 w-3 shrink-0 rounded-full bg-danger" />
              {line}
            </li>
          ))}
        </ul>
      </section>

      <div className="mt-16 flex flex-wrap gap-3">
        <Button asChild size="lg">
          <Link href="/register">Create an account</Link>
        </Button>
        <Button asChild size="lg" variant="outline">
          <Link href="/privacy">Read the privacy notice</Link>
        </Button>
      </div>
    </article>
  );
}
