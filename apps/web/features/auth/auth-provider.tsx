"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getMe, logout as logoutRequest } from "@/lib/api/auth";
import type { AuthMode } from "@/lib/api/client";
import { queryKeys } from "@/lib/query-keys";
import type { UserDto } from "@/types/api";

/** The signed-in user (GET /auth/me). `auth: "throw"` lets public pages render signed-out. */
export function useMe(auth: AuthMode = "redirect") {
  return useQuery<UserDto>({
    queryKey: queryKeys.me,
    queryFn: () => getMe(auth),
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useIsAdmin(): boolean {
  const { data } = useMe();
  return Boolean(data?.roles.includes("ADMIN"));
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: logoutRequest,
    onSettled: () => {
      // Clear every cached resource, signed out or not, so nothing leaks to the next user.
      queryClient.clear();
      router.replace("/login");
    },
  });
}

interface AuthContextValue {
  user: UserDto;
}

const AuthContext = React.createContext<AuthContextValue | null>(null);

export function AuthProvider({ user, children }: { user: UserDto; children: React.ReactNode }) {
  const value = React.useMemo(() => ({ user }), [user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = React.useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside the authenticated app shell");
  return ctx;
}
