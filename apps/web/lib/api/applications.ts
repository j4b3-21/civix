import { apiBlob, apiFetch } from "@/lib/api/client";
import type { AgentTaskDto, ApplicationDto, ApplicationStatus, Page, ReceiptDto } from "@/types/api";

export const createApplication = (serviceId: string) =>
  apiFetch<ApplicationDto>("/applications", { method: "POST", body: { serviceId } });

export const listApplications = (params: { page?: number; size?: number; status?: ApplicationStatus } = {}) =>
  apiFetch<Page<ApplicationDto>>("/applications", { query: params });

export const getApplication = (id: string) => apiFetch<ApplicationDto>(`/applications/${encodeURIComponent(id)}`);

export const executeApplication = (id: string) =>
  apiFetch<AgentTaskDto>(`/applications/${encodeURIComponent(id)}/execute`, { method: "POST" });

export const cancelApplication = (id: string) =>
  apiFetch<ApplicationDto>(`/applications/${encodeURIComponent(id)}/cancel`, { method: "POST" });

export const listReceipts = (id: string) => apiFetch<ReceiptDto[]>(`/applications/${encodeURIComponent(id)}/receipts`);

export const receiptArtifactPath = (applicationId: string, receiptId: string) =>
  `/applications/${encodeURIComponent(applicationId)}/receipts/${encodeURIComponent(receiptId)}/artifact`;

export const getReceiptArtifact = (applicationId: string, receiptId: string) => apiBlob(receiptArtifactPath(applicationId, receiptId));
