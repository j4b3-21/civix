"use client";

import { ImageOff } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { screenshotPath } from "@/lib/api/agent";
import { useAuthedObjectUrl } from "@/hooks/use-authed-object-url";
import { cn } from "@/lib/utils";

export function TaskScreenshot({ taskId, screenshotId, alt, className }: { taskId: string; screenshotId: string; alt: string; className?: string }) {
  const { url, error, loading } = useAuthedObjectUrl(screenshotPath(taskId, screenshotId));
  if (loading) return <Skeleton className={cn("aspect-[16/10] w-full rounded-xl", className)} />;
  if (error || !url) {
    return (
      <div className={cn("flex aspect-[16/10] w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed text-sm text-muted-foreground", className)}>
        <ImageOff className="size-5" aria-hidden="true" />
        Screenshot unavailable
      </div>
    );
  }
  // eslint-disable-next-line @next/next/no-img-element -- authenticated blob URL
  return <img src={url} alt={alt} className={cn("w-full rounded-xl border bg-white", className)} />;
}
