"use client";

import { Toaster as Sonner, type ToasterProps } from "sonner";
import { useTheme } from "@/components/theme/theme-provider";

function Toaster(props: ToasterProps) {
  const { resolvedTheme } = useTheme();
  return (
    <Sonner
      theme={resolvedTheme}
      position="bottom-right"
      toastOptions={{
        classNames: {
          toast: "!rounded-xl !border !border-border !bg-popover !text-popover-foreground !shadow-raised",
          description: "!text-muted-foreground",
        },
      }}
      {...props}
    />
  );
}

export { Toaster };
export { toast } from "sonner";
