"use client";

import Link from "next/link";
import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/common/states";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { useAuth, useLogout } from "@/features/auth/auth-provider";
import { ConsentPrompt, consentPurposeFromError } from "@/features/profile/components/consent-prompt";
import { ProfileGroupEditor } from "@/features/profile/components/profile-group-editor";
import { groupFields, groupLabel } from "@/features/profile/field-values";
import { useProfile } from "@/features/profile/queries";
import { formatDate } from "@/lib/format";
import { PROVENANCE_LABELS } from "@/lib/labels";

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Your profile, how CIVIX looks, and your account." />
      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="appearance">Appearance</TabsTrigger>
          <TabsTrigger value="account">Account</TabsTrigger>
        </TabsList>
        <TabsContent value="profile">
          <ProfileTab />
        </TabsContent>
        <TabsContent value="appearance">
          <section className="max-w-xl space-y-3 rounded-2xl border bg-card p-6 shadow-card" aria-labelledby="theme-heading">
            <h2 id="theme-heading" className="font-semibold">Theme</h2>
            <p className="text-sm text-muted-foreground">System follows your device setting. Your choice is saved in this browser.</p>
            <ThemeToggle />
          </section>
        </TabsContent>
        <TabsContent value="account">
          <AccountTab />
        </TabsContent>
      </Tabs>
    </>
  );
}

function ProfileTab() {
  const profile = useProfile();
  const consentPurpose = consentPurposeFromError(profile.error, "PROFILE_PROCESSING");
  if (profile.isPending) return <ListSkeleton rows={3} />;
  if (consentPurpose) return <ConsentPrompt purpose={consentPurpose} onGranted={() => void profile.refetch()} />;
  if (profile.isError) return <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />;
  const { completeness, fields } = profile.data;
  return (
    <div className="space-y-6">
      <div className="grid gap-6 rounded-2xl border bg-card p-5 shadow-card md:grid-cols-[1fr_auto] md:items-center">
        <div className="space-y-2">
          <div className="flex items-baseline justify-between gap-4">
            <p className="font-medium">Core details</p>
            <p className="text-sm tabular-nums">
              {completeness.coreFilledCount} of {completeness.coreFieldCount} filled
            </p>
          </div>
          <Progress value={completeness.percent} aria-label="Profile completeness" />
        </div>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-[0.8125rem] text-muted-foreground sm:grid-cols-3 md:max-w-sm">
          {(["PROVIDED", "EXTRACTED", "USER_CONFIRMED", "VERIFIED", "MISSING"] as const).map((status) => (
            <div key={status} className="flex gap-1.5">
              <dt>{PROVENANCE_LABELS[status]}:</dt>
              <dd className="font-medium text-foreground tabular-nums">{fields.filter((f) => f.status === status).length}</dd>
            </div>
          ))}
        </dl>
      </div>
      <p className="max-w-3xl text-sm leading-relaxed text-muted-foreground">
        <strong className="font-medium text-foreground">Provided</strong> means you typed it. <strong className="font-medium text-foreground">Extracted</strong>{" "}
        means it was read from a document and still needs your confirmation. <strong className="font-medium text-foreground">Confirmed</strong> means you
        checked it. <strong className="font-medium text-foreground">Verified</strong> is reserved for values confirmed by an official source; no
        verification source is connected yet. Changing a value withdraws any pending approval that used the old one.
      </p>
      {groupFields(fields).map(([group, groupFieldsList]) => (
        <section key={group} aria-labelledby={`group-${group}`} className="rounded-2xl border bg-card p-5 shadow-card sm:p-6">
          <h2 id={`group-${group}`} className="mb-5 font-semibold">
            {groupLabel(group)}
          </h2>
          <ProfileGroupEditor fields={groupFieldsList} showProvenance idPrefix={`settings-${group}`} />
        </section>
      ))}
    </div>
  );
}

function AccountTab() {
  const { user } = useAuth();
  const logout = useLogout();
  return (
    <div className="max-w-xl space-y-6">
      <section className="rounded-2xl border bg-card p-6 shadow-card" aria-labelledby="account-heading">
        <h2 id="account-heading" className="mb-4 font-semibold">Account</h2>
        <dl className="space-y-3 text-sm">
          <div className="grid grid-cols-[8rem_1fr] gap-2">
            <dt className="text-muted-foreground">Name</dt>
            <dd>{user.fullName}</dd>
          </div>
          <div className="grid grid-cols-[8rem_1fr] gap-2">
            <dt className="text-muted-foreground">Email</dt>
            <dd className="break-all">{user.email}</dd>
          </div>
          <div className="grid grid-cols-[8rem_1fr] gap-2">
            <dt className="text-muted-foreground">Member since</dt>
            <dd>{formatDate(user.createdAt)}</dd>
          </div>
          {user.roles.includes("ADMIN") ? (
            <div className="grid grid-cols-[8rem_1fr] gap-2">
              <dt className="text-muted-foreground">Role</dt>
              <dd>Administrator</dd>
            </div>
          ) : null}
        </dl>
      </section>
      <section className="space-y-3 rounded-2xl border bg-card p-6 shadow-card" aria-labelledby="session-heading">
        <h2 id="session-heading" className="font-semibold">Session</h2>
        <p className="text-sm text-muted-foreground">Logging out ends this session and revokes its refresh token.</p>
        <Button variant="outline" onClick={() => logout.mutate()} loading={logout.isPending}>
          <LogOut aria-hidden="true" />
          Log out
        </Button>
      </section>
      <p className="text-sm text-muted-foreground">
        To delete your documents, profile or account, go to the{" "}
        <Link href="/trust-center#deletion" className="font-medium text-brand-strong underline underline-offset-4">
          Trust Center
        </Link>
        .
      </p>
    </div>
  );
}
