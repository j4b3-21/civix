import { apiFetch } from "@/lib/api/client";
import type { AdminAgentTaskDto, ExecutionEventDto, Page, RegistrySyncResult, ServiceSummaryDto, TaskStatus } from "@/types/api";

export const listAdminTasks = (params: { status?: TaskStatus; page?: number; size?: number } = {}) =>
  apiFetch<Page<AdminAgentTaskDto>>("/admin/tasks", { query: params });

export const getAdminTaskEvents = (taskId: string) =>
  apiFetch<ExecutionEventDto[]>(`/admin/tasks/${encodeURIComponent(taskId)}/events`);

export const listAdminServices = (params: { page?: number; size?: number } = {}) =>
  apiFetch<Page<ServiceSummaryDto>>("/admin/services", { query: params });

export const syncRegistry = () => apiFetch<RegistrySyncResult>("/admin/registry/sync", { method: "POST" });
