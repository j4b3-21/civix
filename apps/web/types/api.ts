/**
 * Wire types for the CIVIX public API (`/api/v1`).
 * Source of truth: docs/API.md and docs/AGENT_CONTRACTS.md §1–§2.
 * Enum spelling must stay identical to Java and Python.
 */

// ---------------------------------------------------------------------------
// Enums (AGENT_CONTRACTS §1)
// ---------------------------------------------------------------------------

export const ROLES = ["CITIZEN", "ADMIN"] as const;
export type Role = (typeof ROLES)[number];

export const PROFILE_FIELD_STATUSES = ["MISSING", "PROVIDED", "EXTRACTED", "USER_CONFIRMED", "VERIFIED"] as const;
export type ProfileFieldStatus = (typeof PROFILE_FIELD_STATUSES)[number];

export const FIELD_SOURCES = ["USER_INPUT", "DOCUMENT_EXTRACTION", "VERIFIED_SOURCE"] as const;
export type FieldSource = (typeof FIELD_SOURCES)[number];

export const PROFILE_FIELD_TYPES = ["STRING", "NUMBER", "INTEGER", "BOOLEAN", "DATE", "ENUM"] as const;
export type ProfileFieldType = (typeof PROFILE_FIELD_TYPES)[number];

export const SENSITIVITIES = ["LOW", "MEDIUM", "HIGH"] as const;
export type Sensitivity = (typeof SENSITIVITIES)[number];

export const CONSENT_PURPOSES = [
  "PROFILE_PROCESSING",
  "DOCUMENT_PROCESSING",
  "ELIGIBILITY_EVALUATION",
  "AGENT_EXECUTION",
  "PORTAL_DOCUMENT_UPLOAD",
] as const;
export type ConsentPurpose = (typeof CONSENT_PURPOSES)[number];

export const DOCUMENT_CATEGORIES = [
  "IDENTITY_PROOF",
  "ADDRESS_PROOF",
  "INCOME_CERTIFICATE",
  "CASTE_CERTIFICATE",
  "ACADEMIC_RECORD",
  "BANK_DOCUMENT",
  "RESIDENCE_CERTIFICATE",
  "DISABILITY_CERTIFICATE",
  "PHOTOGRAPH",
  "OTHER",
] as const;
export type DocumentCategory = (typeof DOCUMENT_CATEGORIES)[number];

export const EXTRACTION_STATUSES = ["PENDING", "PROCESSING", "COMPLETED", "FAILED"] as const;
export type ExtractionStatus = (typeof EXTRACTION_STATUSES)[number];

export const REVIEW_STATUSES = ["NOT_REVIEWED", "REVIEWED"] as const;
export type ReviewStatus = (typeof REVIEW_STATUSES)[number];

export const SERVICE_CATEGORIES = [
  "EDUCATION",
  "SCHOLARSHIPS",
  "CERTIFICATES",
  "IDENTITY",
  "WELFARE",
  "AGRICULTURE",
  "BUSINESS",
  "LICENSES",
  "EMPLOYMENT",
  "UTILITIES",
  "OTHER",
] as const;
export type ServiceCategory = (typeof SERVICE_CATEGORIES)[number];

export const JURISDICTION_LEVELS = ["CENTRAL", "STATE"] as const;
export type JurisdictionLevel = (typeof JURISDICTION_LEVELS)[number];

export const SERVICE_VERIFICATION_STATUSES = ["DRAFT", "SOURCE_VERIFIED", "WORKFLOW_VERIFIED", "DEPRECATED"] as const;
export type ServiceVerificationStatus = (typeof SERVICE_VERIFICATION_STATUSES)[number];

export const AUTOMATION_STATUSES = ["NOT_AUTOMATED", "GUIDED", "TEST_ONLY", "ACTIVE"] as const;
export type AutomationStatus = (typeof AUTOMATION_STATUSES)[number];

export const RULE_OPERATORS = [
  "EQUALS",
  "NOT_EQUALS",
  "GREATER_THAN",
  "GREATER_THAN_OR_EQUAL",
  "LESS_THAN",
  "LESS_THAN_OR_EQUAL",
  "IN",
  "NOT_IN",
  "EXISTS",
  "DATE_BEFORE",
  "DATE_AFTER",
] as const;
export type RuleOperator = (typeof RULE_OPERATORS)[number];

export const RULE_RESULTS = ["PASS", "FAIL", "UNKNOWN"] as const;
export type RuleResult = (typeof RULE_RESULTS)[number];

export const ELIGIBILITY_OUTCOMES = ["ELIGIBLE", "INELIGIBLE", "NEEDS_MORE_INFORMATION", "UNVERIFIED"] as const;
export type EligibilityOutcome = (typeof ELIGIBILITY_OUTCOMES)[number];

export const APPLICATION_STATUSES = [
  "DRAFT",
  "IN_PROGRESS",
  "ACTION_REQUIRED",
  "SUBMITTED",
  "SUBMISSION_UNCERTAIN",
  "FAILED",
  "CANCELLED",
] as const;
export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number];

export const TASK_STATUSES = [
  "QUEUED",
  "RUNNING",
  "WAITING_APPROVAL",
  "WAITING_CITIZEN",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
] as const;
export type TaskStatus = (typeof TASK_STATUSES)[number];

export const APPROVAL_KINDS = ["PREPARATION", "DOCUMENT_UPLOAD", "FINAL_SUBMISSION"] as const;
export type ApprovalKind = (typeof APPROVAL_KINDS)[number];

export const ACTION_CLASSES = ["READ_ONLY", "REVERSIBLE", "SENSITIVE", "CONSEQUENTIAL"] as const;
export type ActionClass = (typeof ACTION_CLASSES)[number];

export const APPROVAL_STATUSES = ["PENDING", "APPROVED", "REJECTED", "EXPIRED", "INVALIDATED", "CONSUMED"] as const;
export type ApprovalStatus = (typeof APPROVAL_STATUSES)[number];

export const APPROVAL_DECISIONS = ["APPROVE", "REJECT"] as const;
export type ApprovalDecision = (typeof APPROVAL_DECISIONS)[number];

export const CITIZEN_INPUT_KINDS = ["LOGIN", "OTP", "CAPTCHA", "DECLARATION", "PAYMENT"] as const;
export type CitizenInputKind = (typeof CITIZEN_INPUT_KINDS)[number];

export const AGENT_NAMES = [
  "ORCHESTRATOR",
  "PROFILE_AGENT",
  "DISCOVERY_AGENT",
  "ELIGIBILITY_AGENT",
  "DOCUMENT_AGENT",
  "PLANNING_AGENT",
  "BROWSER_AGENT",
  "RECOVERY_AGENT",
  "VERIFICATION_AGENT",
] as const;
export type AgentName = (typeof AGENT_NAMES)[number];

/** AGENT_CONTRACTS §2.1 */
export const EXECUTION_EVENT_TYPES = [
  "TASK_CREATED",
  "TASK_STARTED",
  "PROFILE_VALIDATED",
  "SERVICE_DISCOVERED",
  "ELIGIBILITY_EVALUATED",
  "DOCUMENT_REQUIRED",
  "DOCUMENT_EXTRACTED",
  "PLAN_CREATED",
  "APPROVAL_REQUIRED",
  "APPROVAL_GRANTED",
  "APPROVAL_REJECTED",
  "BROWSER_STARTED",
  "BROWSER_ACTION_COMPLETED",
  "RECOVERY_STARTED",
  "EXECUTION_PAUSED",
  "CITIZEN_INPUT_REQUIRED",
  "EXECUTION_RESUMED",
  "SUBMISSION_APPROVAL_REQUIRED",
  "SUBMISSION_COMPLETED",
  "SUBMISSION_UNCERTAIN",
  "RECEIPT_VERIFIED",
  "TASK_COMPLETED",
  "TASK_FAILED",
  "TASK_CANCELLED",
] as const;
export type ExecutionEventType = (typeof EXECUTION_EVENT_TYPES)[number];

export const AUDIT_CATEGORIES = ["AUTH", "PROFILE", "CONSENT", "DOCUMENT", "APPLICATION", "AGENT", "APPROVAL", "PRIVACY"] as const;
export type AuditCategory = (typeof AUDIT_CATEGORIES)[number];

export const DELETION_SCOPES = ["DOCUMENTS", "PROFILE", "ACCOUNT"] as const;
export type DeletionScope = (typeof DELETION_SCOPES)[number];

export const RECOVERY_REASONS = [
  "MISSING_SELECTOR",
  "TIMEOUT",
  "NETWORK",
  "SESSION_EXPIRED",
  "LAYOUT_CHANGED",
  "BROWSER_CRASH",
  "INTERRUPTED",
] as const;
export type RecoveryReason = (typeof RECOVERY_REASONS)[number];

// ---------------------------------------------------------------------------
// Shared shapes
// ---------------------------------------------------------------------------

export type Uuid = string;
/** ISO-8601 UTC timestamp */
export type Timestamp = string;

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ProblemFieldError {
  field: string;
  message: string;
}

/** RFC 9457 problem+json as emitted by Spring */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  errors?: ProblemFieldError[];
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export interface UserDto {
  id: Uuid;
  email: string;
  fullName: string;
  roles: Role[];
  createdAt: Timestamp;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

// ---------------------------------------------------------------------------
// Profile & consent
// ---------------------------------------------------------------------------

export type ProfileValue = string | number | boolean | null;

export interface ProfileFieldDto {
  key: string;
  label: string;
  type: ProfileFieldType;
  group: string;
  purpose: string;
  sensitivity: Sensitivity;
  core: boolean;
  options?: string[];
  unit?: string;
  value: ProfileValue;
  status: ProfileFieldStatus;
  source: FieldSource | null;
  sourceDocumentId: Uuid | null;
  confidence: number | null;
  updatedAt: Timestamp | null;
}

export interface ProfileCompleteness {
  percent: number;
  coreFieldCount: number;
  coreFilledCount: number;
  missingCoreFields: string[];
}

export interface ProfileDto {
  userId: Uuid;
  updatedAt: Timestamp | null;
  completeness: ProfileCompleteness;
  fields: ProfileFieldDto[];
}

export interface ProfileUpdateRequest {
  fields: { key: string; value: ProfileValue }[];
  confirmKeys?: string[];
}

export interface ConsentDto {
  purpose: ConsentPurpose;
  granted: boolean;
  noticeVersion: string | null;
  grantedAt: Timestamp | null;
  revokedAt: Timestamp | null;
  description: string | null;
}

export interface DeletionRequest {
  scope: DeletionScope;
  confirm: "DELETE";
}

export interface DeletionResult {
  scope: DeletionScope;
  deletedDocuments: number;
  deletedProfileFields: number;
  accountDeleted: boolean;
}

// ---------------------------------------------------------------------------
// Documents
// ---------------------------------------------------------------------------

export type DiscrepancySeverity = "INFO" | "WARNING" | "ERROR";

export interface ExtractedFieldDto {
  key: string;
  label: string;
  value: ProfileValue;
  confidence: number;
  profileField: string | null;
}

export interface DocumentExtractionDto {
  classification: { category: DocumentCategory; confidence: number; declaredCategoryMatches: boolean };
  fields: ExtractedFieldDto[];
  discrepancies: { field: string; message: string; severity: DiscrepancySeverity }[];
  warnings: string[];
  engine: string | null;
  completedAt: Timestamp | null;
  errorMessage: string | null;
}

export interface DocumentDto {
  id: Uuid;
  fileName: string;
  category: DocumentCategory;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  uploadedAt: Timestamp;
  extractionStatus: ExtractionStatus;
  reviewStatus: ReviewStatus;
  extraction: DocumentExtractionDto | null;
}

export interface DocumentReviewRequest {
  accept: { key: string; value: ProfileValue }[];
  reject: string[];
}

export type DocumentAccessActor = "USER" | "AGENT" | "SYSTEM";
export type DocumentAccessAction = "UPLOAD" | "VIEW" | "DOWNLOAD" | "EXTRACT" | "PORTAL_UPLOAD" | "DELETE";

export interface DocumentAccessDto {
  id: Uuid;
  actorType: DocumentAccessActor;
  actorId: string | null;
  taskId: Uuid | null;
  action: DocumentAccessAction;
  purpose: string | null;
  occurredAt: Timestamp;
}

// ---------------------------------------------------------------------------
// Services, discovery, eligibility
// ---------------------------------------------------------------------------

export interface ServiceDeadline {
  date: string | null;
  note: string | null;
  sourceUrl: string | null;
  verifiedAt: Timestamp | null;
}

export interface ServiceSummaryDto {
  id: Uuid;
  code: string;
  version: number;
  name: string;
  summary: string;
  department: string | null;
  ministry: string | null;
  category: ServiceCategory;
  jurisdiction: { level: JurisdictionLevel; states: string[] };
  verificationStatus: ServiceVerificationStatus;
  automationStatus: AutomationStatus;
  officialUrl: string | null;
  isTestService: boolean;
  deadline: ServiceDeadline | null;
  lastVerifiedAt: Timestamp | null;
  eligibility: { outcome: EligibilityOutcome; evaluatedAt: Timestamp } | null;
}

export interface EligibilityRuleDefinition {
  ruleId: string;
  ruleVersion: number;
  description: string;
  field: string;
  operator: RuleOperator;
  value: unknown;
  sourceUrl: string | null;
}

export interface ServiceSource {
  url: string;
  title: string;
  kind: string;
  retrievedAt: string | null;
}

export interface ServiceDetailDto extends ServiceSummaryDto {
  description: string;
  benefits: string[];
  eligibilityRules: EligibilityRuleDefinition[];
  requiredDocuments: { category: DocumentCategory; description: string; mandatory: boolean }[];
  procedure: { order: number; title: string; description: string }[];
  sources: ServiceSource[];
  portalDomains: string[];
  disclaimer: string | null;
}

export interface EligibilityRuleEvaluation {
  ruleId: string;
  ruleVersion: number;
  field: string;
  operator: RuleOperator;
  expected: unknown;
  actual: unknown;
  result: RuleResult;
  fieldStatus: ProfileFieldStatus | null;
  explanation: string;
  evidence: { sourceUrl: string | null; sourceTitle: string | null; retrievedAt: string | null } | null;
}

export interface EligibilityResult {
  serviceCode: string;
  serviceVersion: number;
  outcome: EligibilityOutcome;
  rules: EligibilityRuleEvaluation[];
  matched: string[];
  failed: string[];
  unknown: string[];
  missingFields: string[];
  explanation: string;
  evaluatedAt: Timestamp;
  engineVersion: string;
}

export interface DiscoveryRequest {
  query: string;
  state?: string;
  category?: ServiceCategory;
}

export interface DiscoveryResultItem {
  service: ServiceSummaryDto;
  score: number;
  matchReasons: string[];
  eligibility: EligibilityResult | null;
}

export interface DiscoveryResponseDto {
  interpretation: {
    summary: string;
    categories: ServiceCategory[];
    keywords: string[];
    state: string | null;
    usedLlm: boolean;
  };
  results: DiscoveryResultItem[];
  generatedAt: Timestamp;
  notice: string | null;
}

export interface CategoryCount {
  category: ServiceCategory;
  count: number;
}

export interface JurisdictionDto {
  code: string;
  name: string;
  type: "STATE" | "UNION_TERRITORY";
}

export interface ServiceListParams {
  q?: string;
  category?: ServiceCategory;
  jurisdiction?: JurisdictionLevel;
  state?: string;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Applications & receipts
// ---------------------------------------------------------------------------

export type RequiredActionType = "APPROVAL" | "CITIZEN_INPUT" | "DOCUMENT" | "PROFILE" | "REVIEW_UNCERTAIN";

export interface RequiredAction {
  type: RequiredActionType;
  message: string;
  approvalId?: Uuid | null;
  taskId?: Uuid | null;
}

export interface ApplicationDto {
  id: Uuid;
  service: ServiceSummaryDto;
  status: ApplicationStatus;
  dataVersion: number;
  referenceNumber: string | null;
  eligibilityOutcome: EligibilityOutcome | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
  lastActivityAt: Timestamp | null;
  submittedAt: Timestamp | null;
  requiredAction: RequiredAction | null;
  latestTaskId: Uuid | null;
  isTestEnvironment: boolean;
}

export interface ReceiptDto {
  id: Uuid;
  applicationId: Uuid;
  taskId: Uuid;
  referenceNumber: string;
  acknowledgementText: string | null;
  portalUrl: string | null;
  verificationMethod: string;
  verifiedAt: Timestamp;
  isTestEnvironment: boolean;
  artifactAvailable: boolean;
}

// ---------------------------------------------------------------------------
// Approvals
// ---------------------------------------------------------------------------

export interface ApprovalDetails {
  fields: { label: string; value: string | number | boolean | null }[];
  documents: { documentId: Uuid; category: DocumentCategory; fileName?: string | null }[];
  portal: string | null;
}

export interface ApprovalDto {
  id: Uuid;
  taskId: Uuid;
  applicationId: Uuid;
  serviceName: string;
  kind: ApprovalKind;
  actionClass: ActionClass;
  summary: string;
  details: ApprovalDetails;
  dataVersion: number;
  status: ApprovalStatus;
  createdAt: Timestamp;
  expiresAt: Timestamp;
  decidedAt: Timestamp | null;
  decisionReason: string | null;
}

export interface ApprovalDecisionRequest {
  decision: ApprovalDecision;
  dataVersion: number;
  reason?: string;
}

// ---------------------------------------------------------------------------
// Agent tasks & events
// ---------------------------------------------------------------------------

export interface PendingCitizenInput {
  requestId: string;
  kind: CitizenInputKind;
  prompt: string;
  screenshotId: Uuid | null;
}

export interface TaskObservation {
  url: string;
  title: string | null;
  screenshotId: Uuid | null;
  at: Timestamp;
}

export interface AgentTaskDto {
  id: Uuid;
  applicationId: Uuid;
  serviceName: string;
  status: TaskStatus;
  currentStep: string | null;
  currentAgent: AgentName | null;
  createdAt: Timestamp;
  startedAt: Timestamp | null;
  updatedAt: Timestamp;
  completedAt: Timestamp | null;
  recoveryAttempts: number;
  pendingApproval: ApprovalDto | null;
  pendingCitizenInput: PendingCitizenInput | null;
  lastObservation: TaskObservation | null;
  error: { code: string; message: string } | null;
  lastSequence: number;
}

export interface AdminAgentTaskDto extends AgentTaskDto {
  userId: Uuid;
}

/** AGENT_CONTRACTS §2.2 envelope */
export interface ExecutionEventDto {
  eventId: Uuid;
  taskId: Uuid;
  sequence: number;
  type: ExecutionEventType;
  timestamp: Timestamp;
  agent: AgentName | null;
  step: string | null;
  message: string;
  metadata: Record<string, unknown>;
}

export interface CitizenInputRequest {
  requestId: string;
  value: string;
}

// ---------------------------------------------------------------------------
// Audit, notifications, dashboard, admin
// ---------------------------------------------------------------------------

export type AuditOutcome = "SUCCESS" | "DENIED" | "FAILURE";

export interface AuditEventDto {
  id: Uuid;
  category: AuditCategory;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  outcome: AuditOutcome;
  occurredAt: Timestamp;
  summary: string;
}

export interface NotificationDto {
  id: Uuid;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  readAt: Timestamp | null;
  createdAt: Timestamp;
}

export interface DashboardDto {
  /** docs/API.md does not spell out the shape; the UI accepts the ProfileDto completeness object or a bare percent. */
  profileCompleteness: ProfileCompleteness | number;
  activeApplications: ApplicationDto[];
  pendingApprovals: ApprovalDto[];
  pendingCitizenInputs: AgentTaskDto[];
  recentActivity: AuditEventDto[];
  recommended: ServiceSummaryDto[];
  upcomingDeadlines: ServiceSummaryDto[];
}

export interface RegistrySyncResult {
  synced: number;
  deactivated: number;
  /** List of error entries (or a count, depending on the API version). */
  errors: unknown[] | number;
}
