import { http, HttpResponse } from "msw";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DocumentUploader } from "@/features/documents/components/document-uploader";
import { MAX_UPLOAD_BYTES, precheckFile } from "@/features/documents/precheck";
import { API, problem, server } from "./msw/server";
import { renderWithClient } from "./utils";

const fakeFile = (name: string, size: number, type: string) => ({ name, size, type });

describe("precheckFile", () => {
  it("accepts PDF, PNG and JPEG up to 10 MB", () => {
    expect(precheckFile(fakeFile("income.pdf", 1024, "application/pdf"))).toEqual({ ok: true });
    expect(precheckFile(fakeFile("photo.PNG", 1024, "image/png"))).toEqual({ ok: true });
    expect(precheckFile(fakeFile("scan.jpeg", MAX_UPLOAD_BYTES, "image/jpeg"))).toEqual({ ok: true });
    expect(precheckFile(fakeFile("scan.jpg", 2048, ""))).toEqual({ ok: true });
  });

  it("rejects empty, oversized and unsupported files", () => {
    expect(precheckFile(fakeFile("empty.pdf", 0, "application/pdf"))).toMatchObject({ ok: false, reason: "EMPTY" });
    expect(precheckFile(fakeFile("big.pdf", MAX_UPLOAD_BYTES + 1, "application/pdf"))).toMatchObject({ ok: false, reason: "TOO_LARGE" });
    expect(precheckFile(fakeFile("doc.docx", 100, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))).toMatchObject({
      ok: false,
      reason: "UNSUPPORTED_TYPE",
    });
    expect(precheckFile(fakeFile("fake.pdf", 100, "text/html"))).toMatchObject({ ok: false, reason: "UNSUPPORTED_TYPE" });
    expect(precheckFile(fakeFile("noext", 100, "application/pdf"))).toMatchObject({ ok: false, reason: "UNSUPPORTED_TYPE" });
  });
});

function fileOfSize(name: string, size: number, type: string): File {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("DocumentUploader", () => {
  it("blocks files that fail the precheck without calling the API", async () => {
    let calls = 0;
    server.use(
      http.post(`${API}/documents`, () => {
        calls += 1;
        return HttpResponse.json({});
      }),
    );
    renderWithClient(<DocumentUploader defaultCategory="INCOME_CERTIFICATE" />);
    const input = screen.getByLabelText("Document file");
    fireEvent.change(input, { target: { files: [fileOfSize("huge.pdf", MAX_UPLOAD_BYTES + 5, "application/pdf")] } });
    expect(await screen.findByRole("alert")).toHaveTextContent("larger than 10 MB");
    expect(screen.getByRole("button", { name: "Upload document" })).toBeDisabled();

    fireEvent.change(input, { target: { files: [fileOfSize("notes.txt", 10, "text/plain")] } });
    expect(await screen.findByRole("alert")).toHaveTextContent("Upload a PDF, PNG or JPEG file.");
    expect(calls).toBe(0);
  });

  it("uploads a valid file as multipart with its category and file name", async () => {
    const append = vi.spyOn(FormData.prototype, "append");
    let received: { contentType: string | null; category: FormDataEntryValue | null; hasFile: boolean } | null = null;
    server.use(
      http.post(`${API}/documents`, async ({ request }) => {
        const contentType = request.headers.get("Content-Type");
        const form = await request.formData();
        received = { contentType, category: form.get("category"), hasFile: form.get("file") !== null };
        return HttpResponse.json({ id: "doc-1", fileName: "income.pdf" }, { status: 201 });
      }),
    );
    renderWithClient(<DocumentUploader defaultCategory="INCOME_CERTIFICATE" />);
    await userEvent.upload(screen.getByLabelText("Document file"), new File(["%PDF-1.7"], "income.pdf", { type: "application/pdf" }));
    expect(screen.getByText("income.pdf")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Upload document" }));
    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({ category: "INCOME_CERTIFICATE", hasFile: true });
    expect(received!.contentType).toMatch(/^multipart\/form-data; boundary=/);
    expect(append).toHaveBeenCalledWith("file", expect.objectContaining({ name: "income.pdf" }), "income.pdf");
  });

  it("offers to grant document consent when the server requires it", async () => {
    server.use(http.post(`${API}/documents`, () => problem(403, "CONSENT_REQUIRED", { detail: "Consent DOCUMENT_PROCESSING is required" })));
    renderWithClient(<DocumentUploader defaultCategory="IDENTITY_PROOF" />);
    await userEvent.upload(screen.getByLabelText("Document file"), new File(["png"], "id.png", { type: "image/png" }));
    await userEvent.click(screen.getByRole("button", { name: "Upload document" }));
    expect(await screen.findByRole("region", { name: "Permission needed" })).toHaveTextContent(/read my uploaded documents/i);
    expect(screen.getByRole("button", { name: "Grant and continue" })).toBeInTheDocument();
  });

  it("asks for a document type", async () => {
    renderWithClient(<DocumentUploader />);
    await userEvent.upload(screen.getByLabelText("Document file"), new File(["png"], "id.png", { type: "image/png" }));
    await userEvent.click(screen.getByRole("button", { name: "Upload document" }));
    expect(await screen.findByText("Choose what kind of document this is")).toBeInTheDocument();
  });
});
