import Link from "next/link";
import { Button } from "@/components/ui/button";
import type { RequiredAction } from "@/types/api";

/** Where to go to resolve an application's required action. */
export function requiredActionHref(action: RequiredAction, applicationId: string): { href: string; label: string } {
  switch (action.type) {
    case "APPROVAL":
      return action.taskId ? { href: `/agent/${action.taskId}`, label: "Review approval" } : { href: `/applications/${applicationId}`, label: "Open application" };
    case "CITIZEN_INPUT":
      return action.taskId ? { href: `/agent/${action.taskId}`, label: "Provide input" } : { href: `/applications/${applicationId}`, label: "Open application" };
    case "DOCUMENT":
      return { href: "/documents", label: "Add documents" };
    case "PROFILE":
      return { href: "/settings", label: "Complete profile" };
    case "REVIEW_UNCERTAIN":
      return { href: `/applications/${applicationId}`, label: "Check submission" };
  }
}

export function RequiredActionLink({ action, applicationId, size = "sm" }: { action: RequiredAction; applicationId: string; size?: "sm" | "default" }) {
  const { href, label } = requiredActionHref(action, applicationId);
  return (
    <Button asChild size={size} variant="outline">
      <Link href={href}>{label}</Link>
    </Button>
  );
}
