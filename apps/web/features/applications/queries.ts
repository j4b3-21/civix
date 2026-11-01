"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  cancelApplication,
  createApplication,
  executeApplication,
  getApplication,
  listApplications,
  listReceipts,
} from "@/lib/api/applications";
import { isApiError } from "@/lib/api/client";
import { queryKeys } from "@/lib/query-keys";
import type { ApplicationDto, ApplicationStatus } from "@/types/api";

export const TERMINAL_APPLICATION_STATUSES: ReadonlySet<ApplicationStatus> = new Set(["SUBMITTED", "CANCELLED"]);

export function useApplications(params: { page?: number; status?: ApplicationStatus } = {}) {
  return useQuery({
    queryKey: queryKeys.applications.list(params),
    queryFn: () => listApplications({ ...params, size: 20 }),
    placeholderData: (previous) => previous,
  });
}

export function useApplication(id: string) {
  return useQuery({
    queryKey: queryKeys.applications.detail(id),
    queryFn: () => getApplication(id),
    refetchInterval: (query) => (query.state.data?.status === "IN_PROGRESS" ? 10_000 : false),
  });
}

export function useReceipts(id: string, enabled = true) {
  return useQuery({ queryKey: queryKeys.applications.receipts(id), queryFn: () => listReceipts(id), enabled });
}

function invalidateApplications(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.applications.all });
  void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
  void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.all });
}

/**
 * Starts an application. When one is already open for the service (409 APPLICATION_EXISTS)
 * the existing application is looked up so the caller can navigate to it instead.
 */
export function useStartApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (serviceId: string): Promise<{ application: ApplicationDto; existing: boolean }> => {
      try {
        return { application: await createApplication(serviceId), existing: false };
      } catch (error) {
        if (isApiError(error) && error.code === "APPLICATION_EXISTS") {
          const page = await listApplications({ size: 100 });
          const open = page.items.find((a) => a.service.id === serviceId && !TERMINAL_APPLICATION_STATUSES.has(a.status));
          if (open) return { application: open, existing: true };
        }
        throw error;
      }
    },
    onSuccess: () => invalidateApplications(queryClient),
  });
}

export function useExecuteApplication() {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn: (id: string) => executeApplication(id), onSuccess: () => invalidateApplications(queryClient) });
}

export function useCancelApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cancelApplication(id),
    onSuccess: (application) => {
      queryClient.setQueryData(queryKeys.applications.detail(application.id), application);
      invalidateApplications(queryClient);
    },
  });
}
