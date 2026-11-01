import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CitizenInputCard, PAYMENT_CONFIRMED_VALUE } from "@/features/agent/components/citizen-input-card";
import { API, problem, server } from "./msw/server";
import { renderWithClient } from "./utils";

const SECRET = "482913";

function captureInput(response: () => Response = () => new HttpResponse(null, { status: 202 })) {
  const calls: unknown[] = [];
  server.use(
    http.post(`${API}/agent/tasks/t-1/citizen-input`, async ({ request }) => {
      calls.push(await request.json());
      return response();
    }),
  );
  return calls;
}

describe("CitizenInputCard", () => {
  it("masks an OTP, sends it once, and never persists it", async () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem");
    const calls = captureInput();
    const { client, container } = renderWithClient(
      <CitizenInputCard taskId="t-1" input={{ requestId: "req-1", kind: "OTP", prompt: "Enter the OTP sent to your phone", screenshotId: null }} />,
    );
    const field = screen.getByLabelText("Code from your SMS or email");
    expect(field).toHaveAttribute("type", "password");
    expect(field).toHaveAttribute("autocomplete", "one-time-code");

    await userEvent.type(field, SECRET);
    await userEvent.click(screen.getByRole("button", { name: "Send to portal" }));

    await waitFor(() => expect(calls).toEqual([{ requestId: "req-1", value: SECRET }]));
    expect(await screen.findByText("Sent to the agent")).toBeInTheDocument();

    // Not in any browser storage, cookie, the query/mutation cache, or the DOM.
    expect(setItem).not.toHaveBeenCalled();
    expect(JSON.stringify({ ...window.localStorage })).not.toContain(SECRET);
    expect(JSON.stringify({ ...window.sessionStorage })).not.toContain(SECRET);
    expect(document.cookie).not.toContain(SECRET);
    expect(JSON.stringify(client.getQueryCache().getAll().map((q) => q.state.data))).not.toContain(SECRET);
    expect(JSON.stringify(client.getMutationCache().getAll().map((m) => m.state.variables))).not.toContain(SECRET);
    expect(container.innerHTML).not.toContain(SECRET);
  });

  it("clears the value after a failed send so it is not kept around", async () => {
    captureInput(() => problem(409, "INPUT_NOT_EXPECTED", { detail: "This input request is no longer active." }));
    renderWithClient(<CitizenInputCard taskId="t-1" input={{ requestId: "req-2", kind: "OTP", prompt: "Enter OTP", screenshotId: null }} />);
    const field = screen.getByLabelText("Code from your SMS or email");
    await userEvent.type(field, SECRET);
    await userEvent.click(screen.getByRole("button", { name: "Send to portal" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("This input request is no longer active.");
    expect(field).toHaveValue("");
  });

  it("allows revealing a masked value without storing it", async () => {
    renderWithClient(<CitizenInputCard taskId="t-1" input={{ requestId: "req-3", kind: "OTP", prompt: "Enter OTP", screenshotId: null }} />);
    await userEvent.click(screen.getByRole("button", { name: "Show value" }));
    expect(screen.getByLabelText("Code from your SMS or email")).toHaveAttribute("type", "text");
  });

  it("validates length before sending", async () => {
    const calls = captureInput();
    renderWithClient(<CitizenInputCard taskId="t-1" input={{ requestId: "req-4", kind: "CAPTCHA", prompt: "Type the characters", screenshotId: null }} />);
    expect(screen.getByLabelText("Characters shown in the image")).toHaveAttribute("type", "text");
    await userEvent.click(screen.getByRole("button", { name: "Send to portal" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Enter between 1 and 200 characters.");
    expect(calls).toHaveLength(0);
  });

  it("never offers payment entry, only a confirmation that the citizen paid", async () => {
    const calls = captureInput();
    renderWithClient(<CitizenInputCard taskId="t-1" input={{ requestId: "req-5", kind: "PAYMENT", prompt: "Pay the application fee of ₹100", screenshotId: null }} />);
    expect(screen.getByText(/CIVIX never automates payments — complete payment on the portal yourself/)).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "I have completed payment on the portal" }));
    await waitFor(() => expect(calls).toEqual([{ requestId: "req-5", value: PAYMENT_CONFIRMED_VALUE }]));
  });

  it("requires ticking the declaration", async () => {
    const calls = captureInput();
    renderWithClient(<CitizenInputCard taskId="t-1" input={{ requestId: "req-6", kind: "DECLARATION", prompt: "I declare the information is true.", screenshotId: null }} />);
    await userEvent.click(screen.getByRole("button", { name: "Confirm declaration" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Tick the box");
    await userEvent.click(screen.getByRole("checkbox"));
    await userEvent.click(screen.getByRole("button", { name: "Confirm declaration" }));
    await waitFor(() => expect(calls).toHaveLength(1));
  });
});
