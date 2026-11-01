"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listNotifications, markNotificationRead } from "@/lib/api/activity";
import { queryKeys } from "@/lib/query-keys";

export function useNotifications() {
  return useQuery({ queryKey: queryKeys.notifications.list, queryFn: () => listNotifications({ size: 15 }), refetchInterval: 60_000 });
}

export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: queryKeys.notifications.unread,
    queryFn: () => listNotifications({ unreadOnly: true, size: 1 }),
    select: (page) => page.totalItems,
    refetchInterval: 30_000,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  });
}
