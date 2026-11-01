import { apiFetch } from "@/lib/api/client";
import type { AuditCategory, AuditEventDto, DashboardDto, NotificationDto, Page } from "@/types/api";

export const listAuditEvents = (params: { category?: AuditCategory; page?: number; size?: number } = {}) =>
  apiFetch<Page<AuditEventDto>>("/audit/events", { query: params });

export const listNotifications = (params: { unreadOnly?: boolean; page?: number; size?: number } = {}) =>
  apiFetch<Page<NotificationDto>>("/notifications", { query: params });

export const markNotificationRead = (id: string) =>
  apiFetch<void>(`/notifications/${encodeURIComponent(id)}/read`, { method: "POST" });

export const getDashboard = () => apiFetch<DashboardDto>("/dashboard");
