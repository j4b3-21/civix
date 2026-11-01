import type { Metadata } from "next";
import { Suspense } from "react";
import { ListSkeleton } from "@/components/common/states";
import { ServicesCatalog } from "@/features/services/components/services-catalog";

export const metadata: Metadata = { title: "Services" };

export default function ServicesPage() {
  return (
    <Suspense fallback={<ListSkeleton rows={3} />}>
      <ServicesCatalog />
    </Suspense>
  );
}
