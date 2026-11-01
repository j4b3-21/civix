"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  checkEligibility,
  discoverServices,
  getJurisdictions,
  getService,
  getServiceCategories,
  listServices,
} from "@/lib/api/services";
import { queryKeys } from "@/lib/query-keys";
import type { DiscoveryRequest, ServiceListParams } from "@/types/api";

export function useServices(params: ServiceListParams) {
  return useQuery({
    queryKey: queryKeys.services.list(params),
    queryFn: () => listServices({ ...params, size: params.size ?? 12 }),
    placeholderData: (previous) => previous,
  });
}

export function useService(id: string) {
  return useQuery({ queryKey: queryKeys.services.detail(id), queryFn: () => getService(id) });
}

export function useServiceCategories(enabled = true) {
  return useQuery({ queryKey: queryKeys.services.categories, queryFn: () => getServiceCategories(), staleTime: 10 * 60_000, enabled });
}

export function useJurisdictions(enabled = true) {
  return useQuery({ queryKey: queryKeys.services.jurisdictions, queryFn: () => getJurisdictions(), staleTime: 60 * 60_000, enabled });
}

export function useDiscover() {
  return useMutation({ mutationFn: (body: DiscoveryRequest) => discoverServices(body) });
}

export function useCheckEligibility(serviceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => checkEligibility(serviceId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.services.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}
