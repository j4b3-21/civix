import {
  AlertTriangle,
  Ban,
  BadgeCheck,
  CheckCircle2,
  CircleDashed,
  CircleHelp,
  Clock,
  FlaskConical,
  Hand,
  Loader2,
  ShieldAlert,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import {
  APPLICATION_STATUS_LABELS,
  APPROVAL_STATUS_LABELS,
  AUTOMATION_LABELS,
  ELIGIBILITY_LABELS,
  EXTRACTION_LABELS,
  PROVENANCE_LABELS,
  TASK_STATUS_LABELS,
  VERIFICATION_LABELS,
} from "@/lib/labels";
import { cn } from "@/lib/utils";
import type {
  ApplicationStatus,
  ApprovalStatus,
  AutomationStatus,
  EligibilityOutcome,
  ExtractionStatus,
  ProfileFieldStatus,
  ServiceVerificationStatus,
  TaskStatus,
} from "@/types/api";

const ELIGIBILITY_VARIANT: Record<EligibilityOutcome, BadgeVariant> = {
  ELIGIBLE: "success",
  INELIGIBLE: "danger",
  NEEDS_MORE_INFORMATION: "warning",
  UNVERIFIED: "info",
};
const ELIGIBILITY_ICON = { ELIGIBLE: CheckCircle2, INELIGIBLE: XCircle, NEEDS_MORE_INFORMATION: CircleHelp, UNVERIFIED: ShieldAlert };

export function EligibilityBadge({ outcome, className }: { outcome: EligibilityOutcome; className?: string }) {
  const Icon = ELIGIBILITY_ICON[outcome];
  return (
    <Badge variant={ELIGIBILITY_VARIANT[outcome]} className={className}>
      <Icon aria-hidden="true" />
      {ELIGIBILITY_LABELS[outcome]}
    </Badge>
  );
}

export function VerificationBadge({ status }: { status: ServiceVerificationStatus }) {
  if (status === "DRAFT")
    return (
      <Badge variant="warning">
        <ShieldAlert aria-hidden="true" />
        {VERIFICATION_LABELS.DRAFT}
      </Badge>
    );
  if (status === "DEPRECATED")
    return (
      <Badge variant="muted">
        <Ban aria-hidden="true" />
        {VERIFICATION_LABELS.DEPRECATED}
      </Badge>
    );
  return (
    <Badge variant="brand">
      <ShieldCheck aria-hidden="true" />
      {VERIFICATION_LABELS[status]}
    </Badge>
  );
}

export function AutomationBadge({ status }: { status: AutomationStatus }) {
  return (
    <Badge variant={status === "NOT_AUTOMATED" ? "outline" : "secondary"} className="whitespace-normal">
      {status === "NOT_AUTOMATED" ? <Hand aria-hidden="true" /> : <BadgeCheck aria-hidden="true" />}
      {AUTOMATION_LABELS[status]}
    </Badge>
  );
}

export function TestServiceBadge({ className }: { className?: string }) {
  return (
    <Badge variant="info" className={cn("whitespace-normal", className)}>
      <FlaskConical aria-hidden="true" />
      Test environment — not a government service
    </Badge>
  );
}

const APPLICATION_VARIANT: Record<ApplicationStatus, BadgeVariant> = {
  DRAFT: "muted",
  IN_PROGRESS: "brand",
  ACTION_REQUIRED: "warning",
  SUBMITTED: "success",
  SUBMISSION_UNCERTAIN: "warning",
  FAILED: "danger",
  CANCELLED: "muted",
};

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  return <Badge variant={APPLICATION_VARIANT[status]}>{APPLICATION_STATUS_LABELS[status]}</Badge>;
}

const TASK_VARIANT: Record<TaskStatus, BadgeVariant> = {
  QUEUED: "muted",
  RUNNING: "brand",
  WAITING_APPROVAL: "warning",
  WAITING_CITIZEN: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "muted",
};
const TASK_ICON = {
  QUEUED: Clock,
  RUNNING: Loader2,
  WAITING_APPROVAL: Hand,
  WAITING_CITIZEN: Hand,
  COMPLETED: CheckCircle2,
  FAILED: AlertTriangle,
  CANCELLED: Ban,
};

export function TaskStatusPill({ status, className }: { status: TaskStatus; className?: string }) {
  const Icon = TASK_ICON[status];
  return (
    <Badge variant={TASK_VARIANT[status]} className={cn("px-3 py-1 text-[0.8125rem]", className)}>
      <Icon className={cn(status === "RUNNING" && "animate-spin motion-reduce:animate-none")} aria-hidden="true" />
      {TASK_STATUS_LABELS[status]}
    </Badge>
  );
}

const APPROVAL_VARIANT: Record<ApprovalStatus, BadgeVariant> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger",
  EXPIRED: "muted",
  INVALIDATED: "muted",
  CONSUMED: "secondary",
};

export function ApprovalStatusBadge({ status }: { status: ApprovalStatus }) {
  return <Badge variant={APPROVAL_VARIANT[status]}>{APPROVAL_STATUS_LABELS[status]}</Badge>;
}

const PROVENANCE_VARIANT: Record<ProfileFieldStatus, BadgeVariant> = {
  MISSING: "muted",
  PROVIDED: "secondary",
  EXTRACTED: "warning",
  USER_CONFIRMED: "brand",
  VERIFIED: "success",
};

export function ProvenanceBadge({ status }: { status: ProfileFieldStatus }) {
  return <Badge variant={PROVENANCE_VARIANT[status]}>{PROVENANCE_LABELS[status]}</Badge>;
}

const EXTRACTION_VARIANT: Record<ExtractionStatus, BadgeVariant> = {
  PENDING: "muted",
  PROCESSING: "brand",
  COMPLETED: "secondary",
  FAILED: "danger",
};

export function ExtractionBadge({ status }: { status: ExtractionStatus }) {
  const Icon = status === "PROCESSING" ? Loader2 : status === "PENDING" ? CircleDashed : status === "FAILED" ? XCircle : CheckCircle2;
  return (
    <Badge variant={EXTRACTION_VARIANT[status]}>
      <Icon className={cn(status === "PROCESSING" && "animate-spin motion-reduce:animate-none")} aria-hidden="true" />
      {EXTRACTION_LABELS[status]}
    </Badge>
  );
}
