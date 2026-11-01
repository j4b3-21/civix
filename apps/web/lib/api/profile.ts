import { apiFetch } from "@/lib/api/client";
import type {
  ConsentDto,
  ConsentPurpose,
  DeletionResult,
  DeletionScope,
  ProfileDto,
  ProfileUpdateRequest,
} from "@/types/api";

export const getProfile = () => apiFetch<ProfileDto>("/profile");

export const updateProfile = (body: ProfileUpdateRequest) => apiFetch<ProfileDto>("/profile", { method: "PUT", body });

export const getConsents = () => apiFetch<ConsentDto[]>("/profile/consents");

export const setConsent = (purpose: ConsentPurpose, granted: boolean) =>
  apiFetch<ConsentDto>("/profile/consents", { method: "POST", body: { purpose, granted } });

export const requestDeletion = (scope: DeletionScope) =>
  apiFetch<DeletionResult>("/privacy/deletion", { method: "POST", body: { scope, confirm: "DELETE" } });
