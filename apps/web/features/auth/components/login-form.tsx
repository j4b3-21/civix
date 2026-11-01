"use client";

import * as React from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { login } from "@/lib/api/auth";
import { isApiError } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { applyServerFieldErrors } from "@/lib/forms";
import { queryKeys } from "@/lib/query-keys";
import { loginSchema, type LoginValues } from "@/features/auth/schemas";
import type { UserDto } from "@/types/api";

export function LoginForm({ onSuccess }: { onSuccess: (user: UserDto) => void }) {
  const queryClient = useQueryClient();
  const [formError, setFormError] = React.useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const user = await login(values);
      queryClient.setQueryData(queryKeys.me, user);
      onSuccess(user);
    } catch (error) {
      if (applyServerFieldErrors(error, setError, ["email", "password"])) return;
      if (isApiError(error) && error.code === "INVALID_CREDENTIALS") {
        setFormError("The email or password is incorrect.");
      } else if (isApiError(error) && (error.code === "ACCOUNT_LOCKED" || error.status === 423)) {
        setFormError("This account is locked for 15 minutes after 5 failed sign-in attempts. Try again later.");
      } else {
        setFormError(errorMessage(error));
      }
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5" aria-describedby={formError ? "login-error" : undefined}>
      {formError ? (
        <div id="login-error" role="alert" className="flex gap-2.5 rounded-xl border border-danger/25 bg-danger-soft px-3.5 py-3 text-sm">
          <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
          <span>{formError}</span>
        </div>
      ) : null}
      <FormField id="login-email" label="Email" error={errors.email?.message}>
        {(a11y) => <Input type="email" autoComplete="email" inputMode="email" {...a11y} {...register("email")} />}
      </FormField>
      <FormField id="login-password" label="Password" error={errors.password?.message}>
        {(a11y) => <Input type="password" autoComplete="current-password" {...a11y} {...register("password")} />}
      </FormField>
      <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
        Sign in
      </Button>
    </form>
  );
}
