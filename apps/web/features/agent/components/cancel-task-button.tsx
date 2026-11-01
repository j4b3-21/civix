"use client";

import * as React from "react";
import { Square } from "lucide-react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useCancelTask } from "@/features/agent/queries";
import { errorMessage } from "@/lib/api/errors";

export function CancelTaskButton({ taskId }: { taskId: string }) {
  const cancel = useCancelTask(taskId);
  const [reason, setReason] = React.useState("");
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="outline" size="sm" loading={cancel.isPending}>
          <Square aria-hidden="true" />
          Stop run
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Stop this run?</AlertDialogTitle>
          <AlertDialogDescription>
            The agent stops at the next safe point and pending approvals are withdrawn. Anything already submitted to the portal is not
            withdrawn there.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="space-y-1.5">
          <Label htmlFor="cancel-reason">Reason (optional)</Label>
          <Textarea id="cancel-reason" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={500} />
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>Keep running</AlertDialogCancel>
          <AlertDialogAction
            destructive
            onClick={() =>
              cancel.mutate(reason.trim() || undefined, {
                onSuccess: () => toast.success("Stopping the run"),
                onError: (e) => toast.error("Could not stop the run", { description: errorMessage(e) }),
              })
            }
          >
            Stop run
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
