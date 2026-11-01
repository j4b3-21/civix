import Link from "next/link";
import { ServiceBadges } from "@/features/services/components/service-badges";
import { JURISDICTION_LABELS, SERVICE_CATEGORY_LABELS } from "@/lib/labels";
import type { ServiceSummaryDto } from "@/types/api";

export function jurisdictionText(service: Pick<ServiceSummaryDto, "jurisdiction">, stateNames?: Map<string, string>): string {
  const { level, states } = service.jurisdiction;
  if (level === "CENTRAL" || states.length === 0) return level === "CENTRAL" ? "Central, all India" : JURISDICTION_LABELS[level];
  const names = states.map((code) => stateNames?.get(code) ?? code);
  return names.length > 2 ? `${names.slice(0, 2).join(", ")} and ${names.length - 2} more` : names.join(", ");
}

export function ServiceCard({ service, stateNames, children }: { service: ServiceSummaryDto; stateNames?: Map<string, string>; children?: React.ReactNode }) {
  return (
    <article className="group relative flex flex-col rounded-2xl border bg-card p-5 shadow-card transition-colors hover:border-brand/40">
      <p className="text-[0.8125rem] text-muted-foreground">
        {SERVICE_CATEGORY_LABELS[service.category]} <span aria-hidden="true">/</span> {jurisdictionText(service, stateNames)}
      </p>
      <h3 className="mt-1.5 text-[1.0625rem] leading-snug font-semibold">
        <Link href={`/services/${service.id}`} className="rounded focus-visible:outline-2 focus-visible:outline-ring after:absolute after:inset-0 after:rounded-2xl">
          {service.name}
        </Link>
      </h3>
      {service.department || service.ministry ? (
        <p className="mt-0.5 text-sm text-muted-foreground">{service.department ?? service.ministry}</p>
      ) : null}
      <p className="mt-3 line-clamp-3 text-sm leading-relaxed text-foreground/85">{service.summary}</p>
      <div className="relative mt-4">
        <ServiceBadges service={service} />
      </div>
      {children ? <div className="relative z-10 mt-4">{children}</div> : null}
    </article>
  );
}
