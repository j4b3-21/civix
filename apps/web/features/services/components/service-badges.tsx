import { CalendarClock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { AutomationBadge, EligibilityBadge, TestServiceBadge, VerificationBadge } from "@/components/common/status-badges";
import { formatDate } from "@/lib/format";
import type { ServiceSummaryDto } from "@/types/api";

/** Deadline chip: shown only for deadlines with a verification timestamp (AGENTS.md §1.1). */
export function VerifiedDeadline({ deadline }: { deadline: ServiceSummaryDto["deadline"] }) {
  if (!deadline?.verifiedAt || !deadline.date) return null;
  return (
    <Badge variant="outline" className="whitespace-normal">
      <CalendarClock aria-hidden="true" />
      Apply by {formatDate(deadline.date)}
    </Badge>
  );
}

export function ServiceBadges({ service, showEligibility = true }: { service: ServiceSummaryDto; showEligibility?: boolean }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {service.isTestService ? <TestServiceBadge /> : null}
      <VerificationBadge status={service.verificationStatus} />
      <AutomationBadge status={service.automationStatus} />
      <VerifiedDeadline deadline={service.deadline} />
      {showEligibility && service.eligibility ? <EligibilityBadge outcome={service.eligibility.outcome} /> : null}
    </div>
  );
}
