import type { ApprovalDto } from "@/types/api";

export function approvalFixture(overrides: Partial<ApprovalDto> = {}): ApprovalDto {
  return {
    id: "ap-1",
    taskId: "t-1",
    applicationId: "app-1",
    serviceName: "CIVIX Test Portal Scholarship",
    kind: "PREPARATION",
    actionClass: "REVERSIBLE",
    summary: "Fill the personal details form on the CIVIX Test Portal",
    details: {
      fields: [
        { label: "Full name", value: "Asha Rao" },
        { label: "Annual family income", value: 250000 },
      ],
      documents: [{ documentId: "doc-1", category: "INCOME_CERTIFICATE", fileName: "income.pdf" }],
      portal: "test-portal",
    },
    dataVersion: 7,
    status: "PENDING",
    createdAt: new Date(Date.now() - 60_000).toISOString(),
    expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
    decidedAt: null,
    decisionReason: null,
    ...overrides,
  };
}
