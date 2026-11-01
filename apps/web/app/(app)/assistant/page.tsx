import type { Metadata } from "next";
import { Suspense } from "react";
import { Assistant } from "@/features/services/components/assistant";

export const metadata: Metadata = { title: "Assistant" };

export default function AssistantPage() {
  return (
    <Suspense>
      <Assistant />
    </Suspense>
  );
}
