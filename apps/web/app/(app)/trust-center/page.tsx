"use client";

import { PageHeader } from "@/components/common/states";
import { AgentRunsSection } from "@/features/trust/components/agent-runs-section";
import { ApprovalsSection } from "@/features/trust/components/approvals-section";
import { DeletionSection } from "@/features/trust/components/deletion-section";
import { DocumentAccessSection } from "@/features/trust/components/document-access-section";
import { PermissionsSection } from "@/features/trust/components/permissions-section";

const SECTIONS = [
  { id: "permissions", title: "Permissions", description: "What CIVIX may do with your data. Withdrawing a permission stops that use from now on." },
  { id: "documents", title: "Document access", description: "Who opened, read, downloaded or uploaded your documents, grouped by document." },
  { id: "runs", title: "Agent runs", description: "Every time the CIVIX agent worked on an application for you." },
  { id: "approvals", title: "Approvals", description: "Requests for your decision, and what you decided." },
  { id: "deletion", title: "Delete your data", description: "Takes effect immediately. Document access history is kept for accountability." },
] as const;

export default function TrustCenterPage() {
  return (
    <>
      <PageHeader title="Trust Center" description="See and control what CIVIX does with your information." />
      <div className="grid gap-10 lg:grid-cols-[12rem_minmax(0,1fr)]">
        <nav aria-label="Trust Center sections" className="hidden lg:block">
          <ul className="sticky top-24 space-y-1 text-sm">
            {SECTIONS.map((s) => (
              <li key={s.id}>
                <a href={`#${s.id}`} className="block rounded-lg px-3 py-2 text-muted-foreground hover:bg-accent hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring">
                  {s.title}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="min-w-0 space-y-14">
          {SECTIONS.map((s) => (
            <section key={s.id} id={s.id} aria-labelledby={`${s.id}-heading`} className="scroll-mt-24">
              <div className="mb-4 space-y-1">
                <h2 id={`${s.id}-heading`} className="text-title font-semibold">
                  {s.title}
                </h2>
                <p className="text-sm text-muted-foreground">{s.description}</p>
              </div>
              {s.id === "permissions" ? (
                <PermissionsSection />
              ) : s.id === "documents" ? (
                <DocumentAccessSection />
              ) : s.id === "runs" ? (
                <AgentRunsSection />
              ) : s.id === "approvals" ? (
                <ApprovalsSection />
              ) : (
                <DeletionSection />
              )}
            </section>
          ))}
        </div>
      </div>
    </>
  );
}
