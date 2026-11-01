import type { Metadata } from "next";
import Link from "next/link";
import { ContentPage } from "@/components/marketing/content-page";

export const metadata: Metadata = { title: "About" };

export default function AboutPage() {
  return (
    <ContentPage
      title="About CIVIX"
      lead="CIVIX exists because applying for a government service in India often means reading long notifications, guessing whether you qualify, and filling the same details into portal after portal."
    >
      <h2>What we are building</h2>
      <p>
        A single place where a citizen can describe what they need, see the services that apply, understand eligibility rule by rule,
        keep their documents organised, and, where it has been proven safe, have an agent prepare the application while they watch and
        approve.
      </p>
      <h2>How we decide what to show</h2>
      <ul>
        <li>
          A service is marked <strong>Sources verified</strong> only when every one of its eligibility rules cites an official source with
          the date it was retrieved. Until then it is shown as <strong>Unverified source</strong>.
        </li>
        <li>
          Eligibility is computed by a deterministic rules engine. Language models may help interpret your question or explain a result,
          but they never decide whether you are eligible.
        </li>
        <li>
          A workflow is offered for automation only after it has been executed against the real, permitted portal. Today most services
          are guided only.
        </li>
        <li>
          The CIVIX Test Portal is an authorised test environment, not a government site. Anything tied to it is labelled as a test.
        </li>
      </ul>
      <h2>What CIVIX is not</h2>
      <p>
        CIVIX is not a government website and does not represent any ministry, department or portal. It cannot approve, reject or speed up
        an application. The issuing authority makes every decision.
      </p>
      <h2>Read more</h2>
      <p>
        <Link href="/how-it-works">How it works</Link> explains the agent pipeline and its safeguards. The{" "}
        <Link href="/privacy">privacy notice</Link> explains what data CIVIX handles and how to delete it.
      </p>
    </ContentPage>
  );
}
