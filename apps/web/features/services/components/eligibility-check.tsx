"use client";

import { ScanSearch } from "lucide-react";
import { Button } from "@/components/ui/button";
import { InlineError } from "@/components/common/states";
import { ConsentPrompt, consentPurposeFromError } from "@/features/profile/components/consent-prompt";
import { EligibilityPanel } from "@/features/services/components/eligibility-panel";
import { useCheckEligibility } from "@/features/services/queries";
import type { EligibilityResult } from "@/types/api";

/** "Check my eligibility" → POST /services/{id}/eligibility, with consent recovery and drill-down. */
export function EligibilityCheck({ serviceId, initialResult = null }: { serviceId: string; initialResult?: EligibilityResult | null }) {
  const check = useCheckEligibility(serviceId);
  const result = check.data ?? initialResult;
  const consentPurpose = consentPurposeFromError(check.error, "ELIGIBILITY_EVALUATION");
  return (
    <div className="space-y-4">
      {result ? <EligibilityPanel result={result} onRecheck={() => check.mutate()} defaultOpen={Boolean(check.data)} /> : null}
      {consentPurpose ? (
        <ConsentPrompt purpose={consentPurpose} onGranted={() => check.mutate()} />
      ) : (
        <InlineError error={check.error} />
      )}
      <Button variant={result ? "outline" : "default"} onClick={() => check.mutate()} loading={check.isPending}>
        <ScanSearch aria-hidden="true" />
        {result ? "Check again" : "Check my eligibility"}
      </Button>
    </div>
  );
}
