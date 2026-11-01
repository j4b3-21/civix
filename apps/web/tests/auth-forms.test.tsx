import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LoginForm } from "@/features/auth/components/login-form";
import { RegisterForm } from "@/features/auth/components/register-form";
import { queryKeys } from "@/lib/query-keys";
import { API, problem, server } from "./msw/server";
import { renderWithClient } from "./utils";

const user = { id: "u1", email: "asha@example.in", fullName: "Asha Rao", roles: ["CITIZEN"], createdAt: "2026-11-01T09:00:00Z" };

describe("LoginForm", () => {
  it("validates required fields and email format before calling the API", async () => {
    const onSuccess = vi.fn();
    renderWithClient(<LoginForm onSuccess={onSuccess} />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Enter your email address")).toBeInTheDocument();
    expect(screen.getByText("Enter your password")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");

    await userEvent.type(screen.getByLabelText("Email"), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Enter a valid email address")).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("shows a form-level message for INVALID_CREDENTIALS", async () => {
    server.use(http.post(`${API}/auth/login`, () => problem(401, "INVALID_CREDENTIALS", { detail: "Bad credentials" })));
    renderWithClient(<LoginForm onSuccess={vi.fn()} />);
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.in");
    await userEvent.type(screen.getByLabelText("Password"), "wrong password!");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("The email or password is incorrect.");
  });

  it("explains ACCOUNT_LOCKED", async () => {
    server.use(http.post(`${API}/auth/login`, () => problem(423, "ACCOUNT_LOCKED")));
    renderWithClient(<LoginForm onSuccess={vi.fn()} />);
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.in");
    await userEvent.type(screen.getByLabelText("Password"), "whatever");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/locked for 15 minutes/);
  });

  it("maps server field errors onto inputs", async () => {
    server.use(
      http.post(`${API}/auth/login`, () => problem(400, "VALIDATION_FAILED", { errors: [{ field: "email", message: "must be a well-formed email address" }] })),
    );
    renderWithClient(<LoginForm onSuccess={vi.fn()} />);
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.in");
    await userEvent.type(screen.getByLabelText("Password"), "whatever");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Must be a well-formed email address")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
  });

  it("signs in, caches the user and calls onSuccess", async () => {
    let body: unknown;
    server.use(
      http.post(`${API}/auth/login`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(user);
      }),
    );
    const onSuccess = vi.fn();
    const { client } = renderWithClient(<LoginForm onSuccess={onSuccess} />);
    await userEvent.type(screen.getByLabelText("Email"), "  asha@example.in ");
    await userEvent.type(screen.getByLabelText("Password"), "correct horse battery");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(expect.objectContaining({ id: "u1" })));
    expect(body).toEqual({ email: "asha@example.in", password: "correct horse battery" });
    expect(client.getQueryData(queryKeys.me)).toMatchObject({ email: "asha@example.in" });
  });
});

describe("RegisterForm", () => {
  async function fill(values: { name?: string; email?: string; password?: string; confirm?: string }) {
    if (values.name) await userEvent.type(screen.getByLabelText("Full name"), values.name);
    if (values.email) await userEvent.type(screen.getByLabelText("Email"), values.email);
    if (values.password) await userEvent.type(screen.getByLabelText("Password"), values.password);
    if (values.confirm) await userEvent.type(screen.getByLabelText("Confirm password"), values.confirm);
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
  }

  it("requires 12 to 128 character passwords that match", async () => {
    renderWithClient(<RegisterForm onSuccess={vi.fn()} />);
    await fill({ name: "Asha Rao", email: "asha@example.in", password: "short", confirm: "short" });
    expect(await screen.findByText("Use at least 12 characters")).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText("Password"));
    await userEvent.click(screen.getByLabelText("Password"));
    await userEvent.paste("x".repeat(129));
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Use 128 characters or fewer")).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText("Password"));
    await userEvent.type(screen.getByLabelText("Password"), "a long enough passphrase");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("The passwords do not match")).toBeInTheDocument();
  });

  it("requires a name of at most 120 characters", async () => {
    renderWithClient(<RegisterForm onSuccess={vi.fn()} />);
    await fill({ email: "asha@example.in", password: "a long enough passphrase", confirm: "a long enough passphrase" });
    expect(await screen.findByText("Enter your full name")).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Full name"));
    await userEvent.paste("n".repeat(121));
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Use 120 characters or fewer")).toBeInTheDocument();
  });

  it("maps server field errors (including nested paths) to the right inputs", async () => {
    server.use(
      http.post(`${API}/auth/register`, () =>
        problem(400, "VALIDATION_FAILED", {
          errors: [
            { field: "request.password", message: "is too common" },
            { field: "fullName", message: "contains unsupported characters" },
          ],
        }),
      ),
    );
    renderWithClient(<RegisterForm onSuccess={vi.fn()} />);
    await fill({ name: "Asha Rao", email: "asha@example.in", password: "password1234", confirm: "password1234" });
    expect(await screen.findByText("Is too common")).toBeInTheDocument();
    expect(screen.getByText("Contains unsupported characters")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Full name")).toHaveAttribute("aria-invalid", "true");
  });

  it("maps a 409 conflict to the email field", async () => {
    server.use(http.post(`${API}/auth/register`, () => problem(409, "EMAIL_ALREADY_REGISTERED")));
    renderWithClient(<RegisterForm onSuccess={vi.fn()} />);
    await fill({ name: "Asha Rao", email: "asha@example.in", password: "a long enough passphrase", confirm: "a long enough passphrase" });
    expect(await screen.findByText("An account with this email already exists.")).toBeInTheDocument();
  });

  it("sends only the API fields and calls onSuccess", async () => {
    let body: unknown;
    server.use(
      http.post(`${API}/auth/register`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(user, { status: 201 });
      }),
    );
    const onSuccess = vi.fn();
    renderWithClient(<RegisterForm onSuccess={onSuccess} />);
    await fill({ name: "Asha Rao", email: "asha@example.in", password: "a long enough passphrase", confirm: "a long enough passphrase" });
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
    expect(body).toEqual({ fullName: "Asha Rao", email: "asha@example.in", password: "a long enough passphrase" });
  });
});
