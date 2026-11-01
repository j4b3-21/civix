"use client";

import * as React from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { register as registerAccount } from "@/lib/api/auth";
import { isApiError } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { applyServerFieldErrors } from "@/lib/forms";
import { queryKeys } from "@/lib/query-keys";
import { registerSchema, type RegisterValues } from "@/features/auth/schemas";
import type { UserDto } from "@/types/api";

export function RegisterForm({ onSuccess }: { onSuccess: (user: UserDto) => void }) {
  const queryClient = useQueryClient();
  const [formError, setFormError] = React.useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" },
  });

  const onSubmit = handleSubmit(async ({ fullName, email, password }) => {
    setFormError(null);
    try {
      const user = await registerAccount({ fullName, email, password });
      queryClient.setQueryData(queryKeys.me, user);
      onSuccess(user);
    } catch (error) {
      if (applyServerFieldErrors(error, setError, ["fullName", "email", "password"])) return;
      if (isApiError(error) && error.status === 409) {
        setError("email", { type: "server", message: "An account with this email already exists." }, { shouldFocus: true });
        return;
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {formError ? (
        <div role="alert" className="flex gap-2.5 rounded-xl border border-danger/25 bg-danger-soft px-3.5 py-3 text-sm">
          <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
          <span>{formError}</span>
        </div>
      ) : null}
      <FormField id="register-name" label="Full name" error={errors.fullName?.message}>
        {(a11y) => <Input autoComplete="name" {...a11y} {...register("fullName")} />}
      </FormField>
      <FormField id="register-email" label="Email" error={errors.email?.message}>
        {(a11y) => <Input type="email" autoComplete="email" inputMode="email" {...a11y} {...register("email")} />}
      </FormField>
      <FormField id="register-password" label="Password" hint="12 to 128 characters. A short sentence works well." error={errors.password?.message}>
        {(a11y) => <Input type="password" autoComplete="new-password" {...a11y} {...register("password")} />}
      </FormField>
      <FormField id="register-confirm" label="Confirm password" error={errors.confirmPassword?.message}>
        {(a11y) => <Input type="password" autoComplete="new-password" {...a11y} {...register("confirmPassword")} />}
      </FormField>
      <p className="text-[0.8125rem] leading-relaxed text-muted-foreground">
        By creating an account you agree to the{" "}
        <Link href="/terms" className="font-medium text-brand-strong underline underline-offset-4">
          terms
        </Link>{" "}
        and acknowledge the{" "}
        <Link href="/privacy" className="font-medium text-brand-strong underline underline-offset-4">
          privacy notice
        </Link>
        . You choose what CIVIX may do with your data on the next screen.
      </p>
      <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
        Create account
      </Button>
    </form>
  );
}
