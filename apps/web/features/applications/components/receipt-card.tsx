"use client";

import * as React from "react";
import { CheckCircle2, ExternalLink, FlaskConical, ImageIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { InlineError } from "@/components/common/states";
import { receiptArtifactPath } from "@/lib/api/applications";
import { formatDateTime, hostname } from "@/lib/format";
import { humanize } from "@/lib/labels";
import { useAuthedObjectUrl } from "@/hooks/use-authed-object-url";
import type { ReceiptDto } from "@/types/api";

function Artifact({ receipt }: { receipt: ReceiptDto }) {
  const { url, error, loading } = useAuthedObjectUrl(receiptArtifactPath(receipt.applicationId, receipt.id));
  if (loading) return <Skeleton className="aspect-video w-full rounded-xl" />;
  if (error) return <InlineError error={error} />;
  if (!url) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- authenticated blob URL, not optimisable
    <img src={url} alt={`Acknowledgement page for reference ${receipt.referenceNumber}`} className="w-full rounded-xl border" />
  );
}

export function ReceiptCard({ receipt }: { receipt: ReceiptDto }) {
  const [showArtifact, setShowArtifact] = React.useState(false);
  return (
    <div className="space-y-4 rounded-2xl border bg-card p-5 shadow-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="flex items-center gap-2 text-sm font-medium text-success">
            <CheckCircle2 className="size-4" aria-hidden="true" />
            Receipt verified on the portal
          </p>
          <p className="text-title font-semibold tabular-nums">{receipt.referenceNumber}</p>
          <p className="text-[0.8125rem] text-muted-foreground">
            Verified {formatDateTime(receipt.verifiedAt)} by {humanize(receipt.verificationMethod).toLowerCase()}
          </p>
        </div>
        {receipt.isTestEnvironment ? (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-info-soft px-2.5 py-1 text-xs font-medium text-info">
            <FlaskConical className="size-3.5" aria-hidden="true" />
            Test environment receipt
          </span>
        ) : null}
      </div>
      {receipt.acknowledgementText ? (
        <blockquote className="border-l-2 border-brand pl-3 text-sm leading-relaxed text-foreground/85">{receipt.acknowledgementText}</blockquote>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {receipt.artifactAvailable ? (
          <Button variant="outline" size="sm" onClick={() => setShowArtifact((v) => !v)} aria-expanded={showArtifact}>
            <ImageIcon aria-hidden="true" />
            {showArtifact ? "Hide acknowledgement page" : "Show acknowledgement page"}
          </Button>
        ) : null}
        {receipt.portalUrl ? (
          <Button asChild variant="ghost" size="sm">
            <a href={receipt.portalUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink aria-hidden="true" />
              {hostname(receipt.portalUrl)}
              <span className="sr-only">(opens in a new tab)</span>
            </a>
          </Button>
        ) : null}
      </div>
      {showArtifact ? <Artifact receipt={receipt} /> : null}
    </div>
  );
}
