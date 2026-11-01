"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Bell, CheckCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { InlineError } from "@/components/common/states";
import { useMarkNotificationRead, useNotifications, useUnreadNotificationCount } from "@/features/notifications/queries";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { NotificationDto } from "@/types/api";

function isInternalLink(link: string | null): link is string {
  return Boolean(link && link.startsWith("/") && !link.startsWith("//"));
}

export function NotificationsPopover() {
  const router = useRouter();
  const [open, setOpen] = React.useState(false);
  const unread = useUnreadNotificationCount();
  const list = useNotifications();
  const markRead = useMarkNotificationRead();
  const [markingAll, setMarkingAll] = React.useState(false);
  const count = unread.data ?? 0;
  const unreadItems = list.data?.items.filter((n) => !n.readAt) ?? [];

  async function openNotification(n: NotificationDto) {
    if (!n.readAt) markRead.mutate(n.id);
    if (isInternalLink(n.link)) {
      setOpen(false);
      router.push(n.link);
    }
  }

  async function markAllRead() {
    setMarkingAll(true);
    try {
      await Promise.all(unreadItems.map((n) => markRead.mutateAsync(n.id)));
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={count > 0 ? `Notifications, ${count} unread` : "Notifications"}>
          <Bell />
          {count > 0 ? (
            <span className="absolute top-1.5 right-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-brand-strong px-1 text-[0.625rem] font-semibold text-white tabular-nums">
              {count > 99 ? "99+" : count}
            </span>
          ) : null}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[min(24rem,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between gap-2 border-b px-4 py-3">
          <p className="text-sm font-semibold">Notifications</p>
          {unreadItems.length > 0 ? (
            <Button variant="ghost" size="sm" onClick={markAllRead} loading={markingAll}>
              <CheckCheck aria-hidden="true" />
              Mark all as read
            </Button>
          ) : null}
        </div>
        <ScrollArea className="max-h-[22rem]">
          {list.isPending ? (
            <div className="space-y-3 p-4">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-full" />
              <Skeleton className="h-4 w-1/2" />
            </div>
          ) : list.isError ? (
            <div className="p-4">
              <InlineError error={list.error} />
            </div>
          ) : list.data.items.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-muted-foreground">
              Nothing yet. Approval requests and application updates appear here.
            </p>
          ) : (
            <ul className="divide-y">
              {list.data.items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => void openNotification(n)}
                    className="flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-accent focus-visible:bg-accent focus-visible:outline-none"
                  >
                    <span
                      aria-hidden="true"
                      className={cn("mt-1.5 size-2 shrink-0 rounded-full", n.readAt ? "bg-transparent" : "bg-brand")}
                    />
                    <span className="min-w-0 flex-1">
                      <span className={cn("block text-sm", n.readAt ? "text-foreground/80" : "font-medium text-foreground")}>
                        {n.title}
                        {n.readAt ? null : <span className="sr-only"> (unread)</span>}
                      </span>
                      {n.body ? <span className="mt-0.5 line-clamp-2 block text-[0.8125rem] text-muted-foreground">{n.body}</span> : null}
                      <span className="mt-1 block text-xs text-muted-foreground">{formatRelative(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </ScrollArea>
      </PopoverContent>
    </Popover>
  );
}
