import { apiFetch, type AuthMode } from "@/lib/api/client";
import type {
  CategoryCount,
  DiscoveryRequest,
  DiscoveryResponseDto,
  EligibilityResult,
  JurisdictionDto,
  Page,
  ServiceDetailDto,
  ServiceListParams,
  ServiceSummaryDto,
} from "@/types/api";

/** The catalog pages are reachable signed-out, so callers choose how a 401 is handled. */
export const listServices = (params: ServiceListParams = {}, auth: AuthMode = "throw") =>
  apiFetch<Page<ServiceSummaryDto>>("/services", { query: { ...params }, auth });

export const getService = (id: string, auth: AuthMode = "throw") =>
  apiFetch<ServiceDetailDto>(`/services/${encodeURIComponent(id)}`, { auth });

export const getServiceCategories = (auth: AuthMode = "throw") => apiFetch<CategoryCount[]>("/services/categories", { auth });

export const getJurisdictions = (auth: AuthMode = "throw") => apiFetch<JurisdictionDto[]>("/services/jurisdictions", { auth });

export const discoverServices = (body: DiscoveryRequest) =>
  apiFetch<DiscoveryResponseDto>("/services/discover", { method: "POST", body });

export const checkEligibility = (serviceId: string) =>
  apiFetch<EligibilityResult>(`/services/${encodeURIComponent(serviceId)}/eligibility`, { method: "POST" });
