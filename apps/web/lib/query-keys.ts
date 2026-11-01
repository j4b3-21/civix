/** Query key factory: every cache entry in the app is addressed through here. */
import type { ApplicationStatus, ApprovalStatus, AuditCategory, DocumentCategory, ServiceListParams, TaskStatus } from "@/types/api";

export const queryKeys = {
  me: ["auth", "me"] as const,
  profile: ["profile"] as const,
  consents: ["profile", "consents"] as const,
  dashboard: ["dashboard"] as const,
  services: {
    all: ["services"] as const,
    list: (params: ServiceListParams) => ["services", "list", params] as const,
    detail: (id: string) => ["services", "detail", id] as const,
    categories: ["services", "categories"] as const,
    jurisdictions: ["services", "jurisdictions"] as const,
  },
  applications: {
    all: ["applications"] as const,
    list: (params: { page?: number; status?: ApplicationStatus }) => ["applications", "list", params] as const,
    detail: (id: string) => ["applications", "detail", id] as const,
    receipts: (id: string) => ["applications", "receipts", id] as const,
  },
  documents: {
    all: ["documents"] as const,
    list: (params: { page?: number; category?: DocumentCategory }) => ["documents", "list", params] as const,
    accessLog: (id: string) => ["documents", "access-log", id] as const,
  },
  tasks: {
    all: ["tasks"] as const,
    list: (params: { page?: number }) => ["tasks", "list", params] as const,
    detail: (id: string) => ["tasks", "detail", id] as const,
  },
  approvals: {
    all: ["approvals"] as const,
    list: (params: { status?: ApprovalStatus; page?: number }) => ["approvals", "list", params] as const,
  },
  audit: (params: { category?: AuditCategory; page?: number; size?: number }) => ["audit", params] as const,
  notifications: {
    all: ["notifications"] as const,
    list: ["notifications", "list"] as const,
    unread: ["notifications", "unread"] as const,
  },
  admin: {
    tasks: (params: { status?: TaskStatus; page?: number }) => ["admin", "tasks", params] as const,
    taskEvents: (id: string) => ["admin", "task-events", id] as const,
    services: (params: { page?: number }) => ["admin", "services", params] as const,
  },
};
