import type { Metadata } from "next";
import Link from "next/link";
import { ContentPage } from "@/components/marketing/content-page";
import { CONSENT_COPY } from "@/lib/labels";
import { CONSENT_PURPOSES } from "@/types/api";

export const metadata: Metadata = { title: "Privacy" };

export default function PrivacyPage() {
  return (
    <ContentPage
      title="Privacy notice"
      lead="What CIVIX stores, why, who can see it, and how you remove it."
      updated="Status: working notice. A formal legal review has not been completed yet."
    >
      <h2>Data CIVIX handles</h2>
      <ul>
        <li><strong>Account:</strong> your name, your email address, and your password stored only as a one-way hash.</li>
        <li>
          <strong>Profile:</strong> the fields you choose to fill in (for example age, state, income or education), with where each value
          came from: typed by you, read from a document, or confirmed by you.
        </li>
        <li>
          <strong>Documents:</strong> the files you upload, encrypted at rest in private storage, and the values CIVIX read from them.
          Extracted values stay suggestions until you accept them.
        </li>
        <li><strong>Eligibility results:</strong> the outcome of each check and the rules it was based on.</li>
        <li>
          <strong>Applications and agent runs:</strong> application status, the step-by-step event log, approval decisions, screenshots
          of portal pages taken during a run, and receipts.
        </li>
        <li><strong>Activity records:</strong> sign-ins, permission changes and every access to your documents, including by the agent.</li>
      </ul>
      <p>
        CIVIX never stores the OTPs, CAPTCHA answers or portal credentials you type in the control room. They are passed to the running
        task once and discarded. Event logs contain field labels, not your field values.
      </p>

      <h2>Permissions you grant</h2>
      <p>Each purpose is a separate permission. You can grant or withdraw any of them in the Trust Center.</p>
      <ul>
        {CONSENT_PURPOSES.map((purpose) => (
          <li key={purpose}>
            <strong>{CONSENT_COPY[purpose].title}.</strong> {CONSENT_COPY[purpose].description}
          </li>
        ))}
      </ul>

      <h2>Who can see your data</h2>
      <p>
        Only you. Every request is checked against your account on the server. Administrators can see failed agent runs to diagnose
        problems. Data reaches a government portal only during a run you approved, and only the values and documents shown in that
        approval.
      </p>

      <h2>Retention</h2>
      <ul>
        <li>Profile values, documents and applications are kept until you delete them or close your account.</li>
        <li>When you delete a document, the file is removed from storage. Its access history is kept for accountability.</li>
        <li>Sign-in sessions expire after 14 days without use. Approval requests expire after 15 minutes by default.</li>
      </ul>

      <h2>Deleting your data</h2>
      <p>
        From the <Link href="/trust-center">Trust Center</Link> you can delete all your documents, clear your profile, or delete your
        account. Each needs you to type DELETE to confirm, and takes effect immediately.
      </p>

      <h2>Legal basis and review</h2>
      <p>
        Alignment with India&apos;s Digital Personal Data Protection Act, 2023 (purpose-specific consent, data minimisation, the right to
        erasure) is a design goal of CIVIX. A formal legal review is pending, and CIVIX does not claim compliance with the Act or any other
        law at this stage.
      </p>
    </ContentPage>
  );
}
