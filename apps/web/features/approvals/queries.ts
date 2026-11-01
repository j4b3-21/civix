"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { decideApproval, listApprovals } from "@/lib/api/approvals";
import { queryKeys } from "@/lib/query-keys";
import type { ApprovalDecisionRequest, ApprovalStatus } from "@/types/api";

export function useApprovals(params: { status?: ApprovalStatus; page?: number } = {}) {
  return useQuery({
    queryKey: queryKeys.approvals.list(params),
    queryFn: () => listApprovals({ ...params, size: 20 }),
    placeholderData: (previous) => previous,
  });
}

export function useDecideApproval(taskId: string) {
  const queryClient = useQueryClient();
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.detail(taskId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.approvals.all });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
    void queryClient.invalidateQueries({ queryKey: queryKeys.applications.all });
  };
  return useMutation({
    mutationFn: ({ approvalId, body }: { approvalId: string; body: ApprovalDecisionRequest }) => decideApproval(approvalId, body),
    // Refetch on both outcomes: 409/410 mean the approval changed underneath us.
    onSettled: refresh,
  });
}
