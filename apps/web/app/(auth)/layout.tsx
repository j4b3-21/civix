import Link from "next/link";
import { Logo } from "@/components/brand/logo";

const SAFEGUARDS = [
  "Nothing is submitted to a portal without your explicit approval.",
  "OTPs, CAPTCHAs, portal sign-ins and payments always stay with you.",
  "Every eligibility answer cites the published rule it comes from.",
  "You can see who accessed each document, and delete your data at any time.",
];

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <aside className="relative hidden flex-col justify-between bg-sidebar px-12 py-12 text-sidebar-foreground lg:flex">
        <Logo inverted />
        <div className="max-w-md space-y-8">
          <p className="text-headline font-semibold text-white">You stay in charge of every step.</p>
          <ul className="space-y-4">
            {SAFEGUARDS.map((line) => (
              <li key={line} className="flex gap-3 text-[0.9375rem] leading-relaxed">
                <span aria-hidden="true" className="mt-2 size-2.5 shrink-0 rounded-[3px] bg-[#0EA5A4]" />
                {line}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-sidebar-foreground/80">CIVIX is an independent tool, not a government website.</p>
      </aside>
      <div className="flex flex-col">
        <div className="flex items-center justify-between px-6 py-5 lg:hidden">
          <Logo />
        </div>
        <main id="main" className="flex flex-1 items-center justify-center px-6 py-10">
          <div className="w-full max-w-sm">{children}</div>
        </main>
        <footer className="flex flex-wrap justify-center gap-x-5 gap-y-2 px-6 pb-8 text-xs text-muted-foreground">
          <Link href="/" className="hover:text-foreground">Home</Link>
          <Link href="/privacy" className="hover:text-foreground">Privacy</Link>
          <Link href="/terms" className="hover:text-foreground">Terms</Link>
          <Link href="/how-it-works" className="hover:text-foreground">How it works</Link>
        </footer>
      </div>
    </div>
  );
}
