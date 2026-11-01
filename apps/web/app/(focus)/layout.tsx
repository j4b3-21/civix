import { AppShell } from "@/components/shell/app-shell";

export default function FocusLayout({ children }: { children: React.ReactNode }) {
  return <AppShell focus>{children}</AppShell>;
}
