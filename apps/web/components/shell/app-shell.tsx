"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/common/states";
import { NotificationsPopover } from "@/components/shell/notifications-popover";
import { UserMenu } from "@/components/shell/user-menu";
import { APP_NAV, isActive } from "@/components/shell/nav-items";
import { AuthProvider, useMe } from "@/features/auth/auth-provider";
import { isApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import type { UserDto } from "@/types/api";

function SidebarNav({ user, onNavigate }: { user: UserDto; onNavigate?: () => void }) {
  const pathname = usePathname();
  const isAdmin = user.roles.includes("ADMIN");
  return (
    <nav aria-label="App" className="flex flex-col gap-0.5">
      {APP_NAV.filter((item) => !item.adminOnly || isAdmin).map((item) => {
        const active = isActive(pathname, item.href);
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavigate}
            aria-current={active ? "page" : undefined}
            className={cn(
              "group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-[0.9375rem] font-medium text-sidebar-foreground transition-colors",
              "hover:bg-sidebar-active hover:text-sidebar-strong focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-[#22d3ee]",
              active && "bg-sidebar-active text-sidebar-strong",
            )}
          >
            <span
              aria-hidden="true"
              className={cn("absolute top-2 bottom-2 left-0 w-[3px] rounded-full bg-[#0EA5A4] opacity-0 transition-opacity", active && "opacity-100")}
            />
            <Icon className="size-[1.125rem] shrink-0" aria-hidden="true" />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}

function SidebarBody({ user, onNavigate }: { user: UserDto; onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col bg-sidebar px-3 py-5 text-sidebar-foreground">
      <div className="px-2 pb-7">
        <Logo href="/dashboard" inverted />
      </div>
      <SidebarNav user={user} onNavigate={onNavigate} />
      <div className="mt-auto rounded-xl border border-white/10 px-3 py-3 text-xs leading-relaxed text-sidebar-foreground/90">
        CIVIX never submits anything without your approval, and never handles OTPs, CAPTCHAs or payments for you.
      </div>
    </div>
  );
}

export function AppFrame({ user, children, focus = false }: { user: UserDto; children: React.ReactNode; focus?: boolean }) {
  const [open, setOpen] = React.useState(false);
  if (focus) {
    return (
      <AuthProvider user={user}>
        <div className="flex min-h-dvh flex-col">
          <header className="border-b bg-card/70">
            <div className="mx-auto flex h-16 max-w-4xl items-center justify-between px-5">
              <Logo href="/dashboard" />
              <UserMenu />
            </div>
          </header>
          <main id="main" className="mx-auto w-full max-w-4xl flex-1 px-5 py-10">
            {children}
          </main>
        </div>
      </AuthProvider>
    );
  }
  return (
    <AuthProvider user={user}>
      <div className="flex min-h-dvh">
        <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 lg:block">
          <SidebarBody user={user} />
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-30 border-b bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75">
            <div className="flex h-16 items-center gap-2 px-4 sm:px-6 lg:px-10">
              <Sheet open={open} onOpenChange={setOpen}>
                <SheetTrigger asChild>
                  <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation">
                    <Menu />
                  </Button>
                </SheetTrigger>
                <SheetContent side="left" className="border-r-0 bg-sidebar p-0 text-sidebar-foreground [&>button]:text-white">
                  <SheetTitle className="sr-only">Navigation</SheetTitle>
                  <SidebarBody user={user} onNavigate={() => setOpen(false)} />
                </SheetContent>
              </Sheet>
              <div className="lg:hidden">
                <Logo href="/dashboard" />
              </div>
              <div className="ml-auto flex items-center gap-1">
                <NotificationsPopover />
                <UserMenu />
              </div>
            </div>
          </header>
          <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-10 lg:py-10">
            {children}
          </main>
        </div>
      </div>
    </AuthProvider>
  );
}

function ShellSkeleton() {
  return (
    <div className="flex min-h-dvh" role="status" aria-label="Loading your account">
      <div className="hidden w-64 bg-sidebar lg:block" />
      <div className="flex-1 space-y-6 px-6 py-10 lg:px-10">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
        <div className="grid gap-4 md:grid-cols-2">
          <Skeleton className="h-40 rounded-2xl" />
          <Skeleton className="h-40 rounded-2xl" />
        </div>
      </div>
    </div>
  );
}

/** Authenticated layout: loads GET /auth/me, redirects to /login on 401 (via the API client). */
export function AppShell({ children, focus = false }: { children: React.ReactNode; focus?: boolean }) {
  const me = useMe("redirect");
  if (me.data) {
    return (
      <AppFrame user={me.data} focus={focus}>
        {children}
      </AppFrame>
    );
  }
  if (me.isError && !(isApiError(me.error) && me.error.status === 401)) {
    return (
      <main id="main" className="mx-auto max-w-lg px-5 py-24">
        <ErrorState error={me.error} title="Your account could not be loaded" onRetry={() => void me.refetch()} />
      </main>
    );
  }
  return <ShellSkeleton />;
}
