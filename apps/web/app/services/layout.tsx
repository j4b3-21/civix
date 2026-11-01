import { AdaptiveShell } from "@/components/shell/adaptive-shell";

export default function ServicesLayout({ children }: { children: React.ReactNode }) {
  return <AdaptiveShell>{children}</AdaptiveShell>;
}
