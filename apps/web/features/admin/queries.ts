"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getAdminTaskEvents, listAdminServices, listAdminTasks, syncRegistry } from "@/lib/api/admin";
import { queryKeys } from "@/lib/query-keys";

export function useAdminFailedTasks(page = 0, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.tasks({ status: "FAILED", page }),
    queryFn: () => listAdminTasks({ status: "FAILED", page, size: 20 }),
    enabled,
    placeholderData: (previous) => previous,
  });
}

export function useAdminTaskEvents(taskId: string | null) {
  return useQuery({
    queryKey: queryKeys.admin.taskEvents(taskId ?? ""),
    queryFn: () => getAdminTaskEvents(taskId ?? ""),
    enabled: Boolean(taskId),
  });
}

export function useAdminServices(page = 0, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.services({ page }),
    queryFn: () => listAdminServices({ page, size: 50 }),
    enabled,
    placeholderData: (previous) => previous,
  });
}

export function useSyncRegistry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncRegistry,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "services"] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.services.all });
    },
  });
}
