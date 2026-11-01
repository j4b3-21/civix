"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteDocument,
  getDocumentAccessLog,
  listDocuments,
  reExtractDocument,
  reviewDocument,
  uploadDocument,
} from "@/lib/api/documents";
import { queryKeys } from "@/lib/query-keys";
import type { DocumentCategory, DocumentReviewRequest, Page, DocumentDto } from "@/types/api";

export const EXTRACTION_POLL_MS = 3000;

export function needsPolling(page: Page<DocumentDto> | undefined): boolean {
  return Boolean(page?.items.some((d) => d.extractionStatus === "PENDING" || d.extractionStatus === "PROCESSING"));
}

export function useDocuments(params: { page?: number; category?: DocumentCategory } = {}) {
  return useQuery({
    queryKey: queryKeys.documents.list(params),
    queryFn: () => listDocuments({ ...params, size: 20 }),
    refetchInterval: (query) => (needsPolling(query.state.data) ? EXTRACTION_POLL_MS : false),
  });
}

function useInvalidateDocuments() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.documents.all });
    void queryClient.invalidateQueries({ queryKey: queryKeys.profile });
    void queryClient.invalidateQueries({ queryKey: ["audit"] });
  };
}

export function useUploadDocument() {
  const invalidate = useInvalidateDocuments();
  return useMutation({
    mutationFn: ({ file, category }: { file: File; category: DocumentCategory }) => uploadDocument(file, category),
    onSuccess: invalidate,
  });
}

export function useDeleteDocument() {
  const invalidate = useInvalidateDocuments();
  return useMutation({ mutationFn: (id: string) => deleteDocument(id), onSuccess: invalidate });
}

export function useReExtractDocument() {
  const invalidate = useInvalidateDocuments();
  return useMutation({ mutationFn: (id: string) => reExtractDocument(id), onSuccess: invalidate });
}

export function useReviewDocument() {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateDocuments();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: DocumentReviewRequest }) => reviewDocument(id, body),
    onSuccess: () => {
      invalidate();
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: queryKeys.applications.all });
    },
  });
}

export function useDocumentAccessLog(id: string, enabled: boolean) {
  return useQuery({ queryKey: queryKeys.documents.accessLog(id), queryFn: () => getDocumentAccessLog(id), enabled });
}
