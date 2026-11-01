"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getConsents, getProfile, requestDeletion, setConsent, updateProfile } from "@/lib/api/profile";
import { queryKeys } from "@/lib/query-keys";
import type { ConsentDto, ConsentPurpose, DeletionScope, ProfileUpdateRequest } from "@/types/api";

export function useProfile(enabled = true) {
  return useQuery({ queryKey: queryKeys.profile, queryFn: getProfile, enabled });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ProfileUpdateRequest) => updateProfile(body),
    onSuccess: (profile) => {
      queryClient.setQueryData(queryKeys.profile, profile);
      // Profile changes bump application data versions and withdraw pending approvals.
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: queryKeys.applications.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.approvals.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.all });
    },
  });
}

export function useConsents() {
  return useQuery({ queryKey: queryKeys.consents, queryFn: getConsents });
}

export function useSetConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ purpose, granted }: { purpose: ConsentPurpose; granted: boolean }) => setConsent(purpose, granted),
    onSuccess: (consent) => {
      queryClient.setQueryData<ConsentDto[]>(queryKeys.consents, (current) =>
        current ? current.map((c) => (c.purpose === consent.purpose ? consent : c)) : current,
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.consents });
      void queryClient.invalidateQueries({ queryKey: ["audit"] });
    },
  });
}

export function useRequestDeletion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (scope: DeletionScope) => requestDeletion(scope),
    onSuccess: (result) => {
      if (!result.accountDeleted) void queryClient.invalidateQueries();
    },
  });
}
