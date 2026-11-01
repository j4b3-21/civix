import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Enter your email address").pipe(z.email("Enter a valid email address")),
  password: z.string().min(1, "Enter your password"),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(1, "Enter your full name").max(120, "Use 120 characters or fewer"),
    email: z.string().trim().min(1, "Enter your email address").pipe(z.email("Enter a valid email address")),
    password: z.string().min(12, "Use at least 12 characters").max(128, "Use 128 characters or fewer"),
    confirmPassword: z.string().min(1, "Enter your password again"),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "The passwords do not match" });
export type RegisterValues = z.infer<typeof registerSchema>;
