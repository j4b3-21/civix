"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cancelTask, getTask, listTasks, retryTask } from "@/lib/api/agent";
import { queryKeys } from "@/lib/query-keys";
import type { TaskStatus } from "@/types/api";

export const TERMINAL_TASK_STATUSES: ReadonlySet<TaskStatus> = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

export function useTask(taskId: string, options: { liveConnected?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.tasks.detail(taskId),
    queryFn: () => getTask(taskId),
    staleTime: 2_000,
    // While the live stream is down, poll so the control room never goes stale.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status && TERMINAL_TASK_STATUSES.has(status)) return false;
      return options.liveConnected ? false : 5_000;
    },
  });
}

export function useTasks(page = 0) {
  return useQuery({
    queryKey: queryKeys.tasks.list({ page }),
    queryFn: () => listTasks({ page, size: 20 }),
    placeholderData: (previous) => previous,
  });
}

function useInvalidateTask(taskId: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.detail(taskId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.applications.all });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
  };
}

export function useCancelTask(taskId: string) {
  const invalidate = useInvalidateTask(taskId);
  return useMutation({ mutationFn: (reason?: string) => cancelTask(taskId, reason), onSuccess: invalidate });
}

export function useRetryTask(taskId: string) {
  const invalidate = useInvalidateTask(taskId);
  return useMutation({ mutationFn: () => retryTask(taskId), onSuccess: invalidate });
}
