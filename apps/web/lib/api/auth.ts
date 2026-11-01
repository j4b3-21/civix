import { apiFetch, type AuthMode } from "@/lib/api/client";
import { userSchema } from "@/lib/schemas/api";
import type { LoginRequest, RegisterRequest, UserDto } from "@/types/api";

export async function register(body: RegisterRequest): Promise<UserDto> {
  return userSchema.parse(await apiFetch<unknown>("/auth/register", { method: "POST", body })) as UserDto;
}

export async function login(body: LoginRequest): Promise<UserDto> {
  return userSchema.parse(await apiFetch<unknown>("/auth/login", { method: "POST", body })) as UserDto;
}

export async function refresh(): Promise<UserDto> {
  return userSchema.parse(await apiFetch<unknown>("/auth/refresh", { method: "POST" })) as UserDto;
}

export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}

export async function getMe(auth: AuthMode = "redirect"): Promise<UserDto> {
  return userSchema.parse(await apiFetch<unknown>("/auth/me", { auth })) as UserDto;
}
