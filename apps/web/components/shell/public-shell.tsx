"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useMe } from "@/features/auth/auth-provider";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/services", label: "Services" },
  { href: "/how-it-works", label: "How it works" },
  { href: "/about", label: "About" },
  { href: "/privacy", label: "Privacy" },
];

function AccountLinks({ stacked = false }: { stacked?: boolean }) {
  const me = useMe("throw");
  if (me.data) {
    return (
      <Button asChild size={stacked ? "lg" : "default"} className={cn(stacked && "w-full")}>
        <Link href="/dashboard">Open dashboard</Link>
      </Button>
    );
  }
  return (
    <div className={cn("flex items-center gap-2", stacked && "flex-col items-stretch")}>
      <Button asChild variant="ghost" size={stacked ? "lg" : "default"}>
        <Link href="/login">Sign in</Link>
      </Button>
      <Button asChild size={stacked ? "lg" : "default"}>
        <Link href="/register">Create account</Link>
      </Button>
    </div>
  );
}

export function PublicHeader() {
  const pathname = usePathname();
  const [open, setOpen] = React.useState(false);
  return (
    <header className="sticky top-0 z-40 border-b border-transparent bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-6 px-5 sm:px-8">
        <Logo />
        <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring",
                  active && "text-foreground",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="hidden md:block">
          <AccountLinks />
        </div>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open menu">
              <Menu />
            </Button>
          </SheetTrigger>
          <SheetContent side="right">
            <SheetHeader>
              <SheetTitle>Menu</SheetTitle>
            </SheetHeader>
            <nav aria-label="Main" className="flex flex-col gap-1 px-3">
              {NAV.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setOpen(false)}
                  className="rounded-xl px-3 py-3 text-base font-medium hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className="mt-auto p-5">
              <AccountLinks stacked />
            </div>
          </SheetContent>
        </Sheet>
      </div>
    </header>
  );
}

export function PublicFooter() {
  return (
    <footer className="border-t bg-card/60">
      <div className="mx-auto grid max-w-6xl gap-8 px-5 py-12 sm:px-8 md:grid-cols-[1.4fr_1fr]">
        <div className="space-y-3">
          <Logo />
          <p className="max-w-md text-sm leading-relaxed text-muted-foreground">
            CIVIX is an independent tool. It is not a government website and does not act on behalf of any ministry or department.
            Decisions on every application are made by the issuing authority.
          </p>
        </div>
        <nav aria-label="Footer" className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
          {[
            { href: "/services", label: "Service catalog" },
            { href: "/how-it-works", label: "How it works" },
            { href: "/about", label: "About" },
            { href: "/privacy", label: "Privacy" },
            { href: "/terms", label: "Terms" },
            { href: "/login", label: "Sign in" },
          ].map((item) => (
            <Link key={item.href} href={item.href} className="rounded py-1 text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring">
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}

export function PublicShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <PublicHeader />
      <main id="main" className="flex-1">
        {children}
      </main>
      <PublicFooter />
    </div>
  );
}
