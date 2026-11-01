import { apiFetch } from "@/lib/api/client";
import { approvalSchema } from "@/lib/schemas/api";
import type { ApprovalDecisionRequest, ApprovalDto, ApprovalStatus, Page } from "@/types/api";

export const listApprovals = (params: { status?: ApprovalStatus; page?: number; size?: number } = {}) =>
  apiFetch<Page<ApprovalDto>>("/approvals", { query: params });

export const getApproval = (approvalId: string) => apiFetch<ApprovalDto>(`/approvals/${encodeURIComponent(approvalId)}`);

export async function decideApproval(approvalId: string, body: ApprovalDecisionRequest): Promise<ApprovalDto> {
  const result = await apiFetch<unknown>(`/approvals/${encodeURIComponent(approvalId)}/decision`, { method: "POST", body });
  return approvalSchema.parse(result) as unknown as ApprovalDto;
}
