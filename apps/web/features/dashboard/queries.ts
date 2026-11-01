"use client";

import { useQuery } from "@tanstack/react-query";
import { getDashboard, listAuditEvents } from "@/lib/api/activity";
import { queryKeys } from "@/lib/query-keys";
import type { AuditCategory } from "@/types/api";

export function useDashboard() {
  return useQuery({ queryKey: queryKeys.dashboard, queryFn: getDashboard });
}

export function useAuditEvents(params: { category?: AuditCategory; page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: queryKeys.audit(params),
    queryFn: () => listAuditEvents({ size: 20, ...params }),
    placeholderData: (previous) => previous,
  });
}
