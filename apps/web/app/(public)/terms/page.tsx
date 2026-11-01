import type { Metadata } from "next";
import Link from "next/link";
import { ContentPage } from "@/components/marketing/content-page";

export const metadata: Metadata = { title: "Terms" };

export default function TermsPage() {
  return (
    <ContentPage
      title="Terms of use"
      lead="The plain-language rules for using CIVIX."
      updated="Status: working draft pending legal review."
    >
      <h2>CIVIX is an independent tool</h2>
      <p>
        CIVIX is not a government service and does not act for any ministry, department or portal. Using CIVIX does not create, approve or
        prioritise an application. The issuing authority decides every application under its own rules.
      </p>
      <h2>Eligibility results are indications</h2>
      <p>
        An eligibility result reflects the rules recorded in CIVIX and the information you provided. &quot;Appears eligible&quot; is not a
        guarantee. Rules marked as coming from an unverified source may be incomplete or out of date; check the official source linked next
        to each rule.
      </p>
      <h2>Your responsibilities</h2>
      <ul>
        <li>Provide accurate information, and review values read from your documents before accepting them.</li>
        <li>Read each approval request before approving it. Approving a final submission sends the application on your behalf.</li>
        <li>Handle OTPs, CAPTCHAs, portal sign-ins and payments yourself. CIVIX will not do these for you.</li>
        <li>Upload only documents that belong to you or that you are entitled to use.</li>
      </ul>
      <h2>Automation limits</h2>
      <p>
        Supervised automation is available only for workflows that have been verified on the real, permitted portal. Portals change; if a
        step fails, CIVIX may retry reversible steps and will otherwise stop and tell you. If CIVIX cannot confirm a submission reached the
        portal, it says so and never resubmits automatically.
      </p>
      <h2>Test environment</h2>
      <p>
        Services and receipts labelled as test environment belong to the CIVIX Test Portal. They are not government services and nothing
        submitted there has any legal effect.
      </p>
      <h2>Acceptable use</h2>
      <p>
        Do not use CIVIX to submit false information, to access another person&apos;s data, or to probe or overload government portals or
        CIVIX itself.
      </p>
      <h2>Privacy</h2>
      <p>
        How CIVIX handles your data is described in the <Link href="/privacy">privacy notice</Link>.
      </p>
    </ContentPage>
  );
}
