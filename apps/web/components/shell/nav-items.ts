import {
  FileStack,
  FolderLock,
  Landmark,
  LayoutDashboard,
  MessageSquareText,
  Settings,
  ShieldCheck,
  Wrench,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  adminOnly?: boolean;
}

export const APP_NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/assistant", label: "Assistant", icon: MessageSquareText },
  { href: "/services", label: "Services", icon: Landmark },
  { href: "/applications", label: "Applications", icon: FileStack },
  { href: "/documents", label: "Documents", icon: FolderLock },
  { href: "/trust-center", label: "Trust Center", icon: ShieldCheck },
  { href: "/settings", label: "Settings", icon: Settings },
  { href: "/admin", label: "Admin", icon: Wrench, adminOnly: true },
];

export function isActive(pathname: string, href: string): boolean {
  if (href === "/applications") return pathname.startsWith("/applications") || pathname.startsWith("/agent");
  return pathname === href || pathname.startsWith(`${href}/`);
}
