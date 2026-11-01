import type {
  ActionClass,
  AgentName,
  ApplicationStatus,
  ApprovalKind,
  ApprovalStatus,
  AutomationStatus,
  CitizenInputKind,
  ConsentPurpose,
  DocumentCategory,
  EligibilityOutcome,
  ExecutionEventType,
  ExtractionStatus,
  JurisdictionLevel,
  ProfileFieldStatus,
  RecoveryReason,
  RuleOperator,
  RuleResult,
  ServiceCategory,
  ServiceVerificationStatus,
  TaskStatus,
} from "@/types/api";

export function humanize(value: string): string {
  const lower = value.replace(/_/g, " ").toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export const SERVICE_CATEGORY_LABELS: Record<ServiceCategory, string> = {
  EDUCATION: "Education",
  SCHOLARSHIPS: "Scholarships",
  CERTIFICATES: "Certificates",
  IDENTITY: "Identity",
  WELFARE: "Welfare",
  AGRICULTURE: "Agriculture",
  BUSINESS: "Business",
  LICENSES: "Licences",
  EMPLOYMENT: "Employment",
  UTILITIES: "Utilities",
  OTHER: "Other",
};

export const JURISDICTION_LABELS: Record<JurisdictionLevel, string> = { CENTRAL: "Central", STATE: "State" };

export const DOCUMENT_CATEGORY_LABELS: Record<DocumentCategory, string> = {
  IDENTITY_PROOF: "Identity proof",
  ADDRESS_PROOF: "Address proof",
  INCOME_CERTIFICATE: "Income certificate",
  CASTE_CERTIFICATE: "Caste certificate",
  ACADEMIC_RECORD: "Academic record",
  BANK_DOCUMENT: "Bank document",
  RESIDENCE_CERTIFICATE: "Residence certificate",
  DISABILITY_CERTIFICATE: "Disability certificate",
  PHOTOGRAPH: "Photograph",
  OTHER: "Other document",
};

export const ELIGIBILITY_LABELS: Record<EligibilityOutcome, string> = {
  ELIGIBLE: "Appears eligible",
  INELIGIBLE: "Not eligible",
  NEEDS_MORE_INFORMATION: "Needs more information",
  UNVERIFIED: "Unverified result",
};

export const ELIGIBILITY_HINTS: Record<EligibilityOutcome, string> = {
  ELIGIBLE: "Based on the information you provided, you appear to meet every rule. The department makes the final decision.",
  INELIGIBLE: "At least one rule is not met with the information you provided.",
  NEEDS_MORE_INFORMATION: "Some rules could not be checked because information is missing from your profile.",
  UNVERIFIED:
    "Every rule passed, but the result relies on unconfirmed extracted data or on a service whose sources are not yet verified.",
};

export const RULE_RESULT_LABELS: Record<RuleResult, string> = { PASS: "Met", FAIL: "Not met", UNKNOWN: "Unknown" };

export const OPERATOR_LABELS: Record<RuleOperator, string> = {
  EQUALS: "equals",
  NOT_EQUALS: "is not",
  GREATER_THAN: "more than",
  GREATER_THAN_OR_EQUAL: "at least",
  LESS_THAN: "less than",
  LESS_THAN_OR_EQUAL: "at most",
  IN: "is one of",
  NOT_IN: "is not one of",
  EXISTS: "is provided",
  DATE_BEFORE: "is before",
  DATE_AFTER: "is after",
};

export const VERIFICATION_LABELS: Record<ServiceVerificationStatus, string> = {
  DRAFT: "Unverified source",
  SOURCE_VERIFIED: "Sources verified",
  WORKFLOW_VERIFIED: "Workflow verified",
  DEPRECATED: "Deprecated",
};

export const AUTOMATION_LABELS: Record<AutomationStatus, string> = {
  NOT_AUTOMATED: "Guided only: apply on the official portal",
  GUIDED: "Guided with CIVIX",
  TEST_ONLY: "Automation in test environment only",
  ACTIVE: "Supervised automation available",
};

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  DRAFT: "Draft",
  IN_PROGRESS: "In progress",
  ACTION_REQUIRED: "Action required",
  SUBMITTED: "Submitted",
  SUBMISSION_UNCERTAIN: "Submission unconfirmed",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
};

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  QUEUED: "Queued",
  RUNNING: "Running",
  WAITING_APPROVAL: "Waiting for your approval",
  WAITING_CITIZEN: "Waiting for your input",
  COMPLETED: "Completed",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
};

export const APPROVAL_KIND_LABELS: Record<ApprovalKind, string> = {
  PREPARATION: "Fill the application form",
  DOCUMENT_UPLOAD: "Upload documents to the portal",
  FINAL_SUBMISSION: "Submit the application",
};

export const APPROVAL_STATUS_LABELS: Record<ApprovalStatus, string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  EXPIRED: "Expired",
  INVALIDATED: "Withdrawn",
  CONSUMED: "Used",
};

export const ACTION_CLASS_LABELS: Record<ActionClass, string> = {
  READ_ONLY: "Read only",
  REVERSIBLE: "Reversible",
  SENSITIVE: "Sensitive",
  CONSEQUENTIAL: "Consequential",
};

export const CITIZEN_INPUT_LABELS: Record<CitizenInputKind, string> = {
  LOGIN: "Portal sign-in",
  OTP: "One-time password",
  CAPTCHA: "CAPTCHA",
  DECLARATION: "Declaration",
  PAYMENT: "Payment",
};

export const AGENT_LABELS: Record<AgentName, string> = {
  ORCHESTRATOR: "Orchestrator",
  PROFILE_AGENT: "Profile",
  DISCOVERY_AGENT: "Discovery",
  ELIGIBILITY_AGENT: "Eligibility",
  DOCUMENT_AGENT: "Documents",
  PLANNING_AGENT: "Planning",
  BROWSER_AGENT: "Browser",
  RECOVERY_AGENT: "Recovery",
  VERIFICATION_AGENT: "Verification",
};

export const EVENT_LABELS: Record<ExecutionEventType, string> = {
  TASK_CREATED: "Task created",
  TASK_STARTED: "Task started",
  PROFILE_VALIDATED: "Profile checked",
  SERVICE_DISCOVERED: "Service identified",
  ELIGIBILITY_EVALUATED: "Eligibility evaluated",
  DOCUMENT_REQUIRED: "Document needed",
  DOCUMENT_EXTRACTED: "Document read",
  PLAN_CREATED: "Plan created",
  APPROVAL_REQUIRED: "Approval requested",
  APPROVAL_GRANTED: "Approved",
  APPROVAL_REJECTED: "Rejected",
  BROWSER_STARTED: "Portal opened",
  BROWSER_ACTION_COMPLETED: "Portal step completed",
  RECOVERY_STARTED: "Recovering",
  EXECUTION_PAUSED: "Paused",
  CITIZEN_INPUT_REQUIRED: "Your input needed",
  EXECUTION_RESUMED: "Resumed",
  SUBMISSION_APPROVAL_REQUIRED: "Final approval requested",
  SUBMISSION_COMPLETED: "Submitted",
  SUBMISSION_UNCERTAIN: "Submission unconfirmed",
  RECEIPT_VERIFIED: "Receipt verified",
  TASK_COMPLETED: "Task completed",
  TASK_FAILED: "Task failed",
  TASK_CANCELLED: "Task cancelled",
};

export const RECOVERY_REASON_LABELS: Record<RecoveryReason, string> = {
  MISSING_SELECTOR: "A form element was not where expected",
  TIMEOUT: "The portal took too long to respond",
  NETWORK: "A network error occurred",
  SESSION_EXPIRED: "The portal session expired",
  LAYOUT_CHANGED: "The portal layout changed",
  BROWSER_CRASH: "The browser stopped unexpectedly",
  INTERRUPTED: "The run was interrupted and is resuming",
};

export const PROVENANCE_LABELS: Record<ProfileFieldStatus, string> = {
  MISSING: "Missing",
  PROVIDED: "Provided",
  EXTRACTED: "Extracted",
  USER_CONFIRMED: "Confirmed",
  VERIFIED: "Verified",
};

export const EXTRACTION_LABELS: Record<ExtractionStatus, string> = {
  PENDING: "Waiting to read",
  PROCESSING: "Reading document",
  COMPLETED: "Read",
  FAILED: "Could not read",
};

export interface ConsentCopy {
  title: string;
  description: string;
  enables: string;
}

/** Local copy shown alongside (never instead of) the server's notice `description`. */
export const CONSENT_COPY: Record<ConsentPurpose, ConsentCopy> = {
  PROFILE_PROCESSING: {
    title: "Store and use my profile",
    description: "Keep the details you enter (such as age, state, income and education) in your CIVIX profile.",
    enables: "Needed to save profile details.",
  },
  DOCUMENT_PROCESSING: {
    title: "Read my uploaded documents",
    description:
      "Store the documents you upload, encrypted, and read text from them to suggest profile values. Suggestions only reach your profile after you accept them.",
    enables: "Needed to upload documents.",
  },
  ELIGIBILITY_EVALUATION: {
    title: "Check my eligibility",
    description: "Compare your profile with each service's published rules and keep the result so you can see why.",
    enables: "Needed for eligibility checks.",
  },
  AGENT_EXECUTION: {
    title: "Let CIVIX prepare applications",
    description:
      "Allow the CIVIX agent to open a supported portal and fill in a draft form for you. It stops for your approval before filling, uploading or submitting anything.",
    enables: "Needed to run the agent.",
  },
  PORTAL_DOCUMENT_UPLOAD: {
    title: "Upload my documents to a portal",
    description:
      "Allow the agent to upload specific documents you approve to the portal of the service you are applying for. Each upload is approved separately.",
    enables: "Needed for document upload steps.",
  },
};
