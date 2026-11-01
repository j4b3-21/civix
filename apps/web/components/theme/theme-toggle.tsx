"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme, type ThemePreference } from "@/components/theme/theme-provider";

const OPTIONS: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
];

/** Segmented control; each option is a real radio for keyboard and screen-reader users. */
export function ThemeToggle({ className, compact = false }: { className?: string; compact?: boolean }) {
  const { theme, setTheme } = useTheme();
  return (
    <div role="radiogroup" aria-label="Colour theme" className={cn("inline-flex rounded-xl bg-muted p-1", className)}>
      {OPTIONS.map(({ value, label, icon: Icon }) => {
        const active = theme === value;
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => setTheme(value)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring",
              active && "bg-card text-foreground shadow-sm",
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            <span className={cn(compact && "sr-only")}>{label}</span>
          </button>
        );
      })}
    </div>
  );
}
