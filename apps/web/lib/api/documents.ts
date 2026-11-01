import { apiBlob, apiFetch } from "@/lib/api/client";
import type { DocumentAccessDto, DocumentCategory, DocumentDto, DocumentReviewRequest, Page } from "@/types/api";

export const listDocuments = (params: { page?: number; size?: number; category?: DocumentCategory } = {}) =>
  apiFetch<Page<DocumentDto>>("/documents", { query: params });

export const getDocument = (id: string) => apiFetch<DocumentDto>(`/documents/${encodeURIComponent(id)}`);

export function uploadDocument(file: File, category: DocumentCategory): Promise<DocumentDto> {
  const form = new FormData();
  form.append("file", file, file.name);
  form.append("category", category);
  return apiFetch<DocumentDto>("/documents", { method: "POST", body: form });
}

export const downloadDocument = (id: string) => apiBlob(`/documents/${encodeURIComponent(id)}/content`);

export const deleteDocument = (id: string) => apiFetch<void>(`/documents/${encodeURIComponent(id)}`, { method: "DELETE" });

export const reExtractDocument = (id: string) =>
  apiFetch<DocumentDto>(`/documents/${encodeURIComponent(id)}/extract`, { method: "POST" });

export const reviewDocument = (id: string, body: DocumentReviewRequest) =>
  apiFetch<DocumentDto>(`/documents/${encodeURIComponent(id)}/review`, { method: "POST", body });

export const getDocumentAccessLog = (id: string) =>
  apiFetch<DocumentAccessDto[]>(`/documents/${encodeURIComponent(id)}/access-log`);
