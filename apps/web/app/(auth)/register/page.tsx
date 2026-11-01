"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { RegisterForm } from "@/features/auth/components/register-form";

export default function RegisterPage() {
  const router = useRouter();
  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h1 className="text-headline font-semibold">Create your account</h1>
        <p className="text-[0.9375rem] text-muted-foreground">
          Already registered?{" "}
          <Link href="/login" className="font-medium text-brand-strong underline underline-offset-4">
            Sign in
          </Link>
        </p>
      </div>
      <RegisterForm onSuccess={() => router.replace("/onboarding")} />
    </div>
  );
}
