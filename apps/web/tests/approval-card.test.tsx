import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ApprovalCard } from "@/features/agent/components/approval-card";
import { queryKeys } from "@/lib/query-keys";
import { approvalFixture } from "./fixtures";
import { API, problem, server } from "./msw/server";
import { renderWithClient } from "./utils";

function captureDecision(response: () => Response = () => HttpResponse.json({ ...approvalFixture(), status: "APPROVED" })) {
  const calls: unknown[] = [];
  server.use(
    http.post(`${API}/approvals/ap-1/decision`, async ({ request }) => {
      calls.push(await request.json());
      return response();
    }),
  );
  return calls;
}

describe("ApprovalCard", () => {
  it("shows the summary, values, documents and a live expiry countdown", () => {
    renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    expect(screen.getByRole("heading", { name: "Fill the application form" })).toBeInTheDocument();
    expect(screen.getByText("Fill the personal details form on the CIVIX Test Portal")).toBeInTheDocument();
    expect(screen.getByRole("rowheader", { name: "Full name" })).toBeInTheDocument();
    expect(screen.getByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByText("2,50,000")).toBeInTheDocument();
    expect(screen.getByText("income.pdf")).toBeInTheDocument();
    expect(screen.getByText(/Expires in (9|10):\d\d/)).toBeInTheDocument();
  });

  it("approves with the approval's dataVersion", async () => {
    const calls = captureDecision();
    const { client } = renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    client.setQueryData(queryKeys.tasks.detail("t-1"), { id: "t-1" });
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    await waitFor(() => expect(calls).toEqual([{ decision: "APPROVE", dataVersion: 7 }]));
    expect(await screen.findByText("Approved. The agent will continue.")).toBeInTheDocument();
    // The task is refetched so the control room reflects the decision.
    await waitFor(() => expect(client.getQueryState(queryKeys.tasks.detail("t-1"))?.isInvalidated).toBe(true));
  });

  it("rejects with an optional reason", async () => {
    const calls = captureDecision(() => HttpResponse.json({ ...approvalFixture(), status: "REJECTED" }));
    renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    await userEvent.type(screen.getByLabelText("Note for the record (optional)"), "Income value is outdated");
    await userEvent.click(screen.getByRole("button", { name: "Reject" }));
    await waitFor(() => expect(calls).toEqual([{ decision: "REJECT", dataVersion: 7, reason: "Income value is outdated" }]));
    expect(await screen.findByText(/Rejected\. The agent will not perform this step\./)).toBeInTheDocument();
  });

  it("labels the final submission clearly", () => {
    renderWithClient(<ApprovalCard approval={approvalFixture({ kind: "FINAL_SUBMISSION", actionClass: "CONSEQUENTIAL" })} taskId="t-1" />);
    expect(screen.getByRole("button", { name: "Approve and submit" })).toBeInTheDocument();
    expect(screen.getByText(/This cannot be undone from CIVIX/)).toBeInTheDocument();
  });

  it("disables decisions once the request has expired", async () => {
    const calls = captureDecision();
    renderWithClient(<ApprovalCard approval={approvalFixture({ expiresAt: new Date(Date.now() - 1000).toISOString() })} taskId="t-1" />);
    expect(screen.getByText("This approval request expired")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it("handles 410 APPROVAL_EXPIRED from the server", async () => {
    captureDecision(() => problem(410, "APPROVAL_EXPIRED"));
    renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(await screen.findByText("This approval request expired")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("handles 409 APPROVAL_DATA_CHANGED and APPROVAL_NOT_PENDING", async () => {
    captureDecision(() => problem(409, "APPROVAL_DATA_CHANGED"));
    const { unmount } = renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Your information changed")).toBeInTheDocument();
    unmount();

    captureDecision(() => problem(409, "APPROVAL_NOT_PENDING"));
    renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Reject" }));
    expect(await screen.findByText("This request was already decided")).toBeInTheDocument();
  });

  it("keeps the buttons available after an unexpected error", async () => {
    captureDecision(() => problem(500, "INTERNAL_ERROR", { detail: "Database unavailable" }));
    renderWithClient(<ApprovalCard approval={approvalFixture()} taskId="t-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Database unavailable");
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
  });
});
