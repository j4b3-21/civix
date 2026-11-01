"use client";

import { useRouter } from "next/navigation";
import { FilePlus2 } from "lucide-react";
import { toast } from "sonner";
import { Button, type ButtonProps } from "@/components/ui/button";
import { useStartApplication } from "@/features/applications/queries";
import { errorMessage } from "@/lib/api/errors";

export function StartApplicationButton({ serviceId, ...props }: { serviceId: string } & Omit<ButtonProps, "onClick">) {
  const router = useRouter();
  const start = useStartApplication();
  return (
    <Button
      {...props}
      loading={start.isPending}
      onClick={() =>
        start.mutate(serviceId, {
          onSuccess: ({ application, existing }) => {
            if (existing) toast.info("You already have an open application for this service", { description: "Opening it now." });
            else toast.success("Application started");
            router.push(`/applications/${application.id}`);
          },
          onError: (error) => toast.error("Could not start the application", { description: errorMessage(error) }),
        })
      }
    >
      <FilePlus2 aria-hidden="true" />
      Start application
    </Button>
  );
}
