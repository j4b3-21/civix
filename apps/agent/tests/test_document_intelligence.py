from __future__ import annotations

import io

import pytest
from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

from app.contracts.common import DocumentCategory
from app.document_intelligence.pipeline import DocumentRejected, process


def make_pdf(lines: list[str]) -> bytes:
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    y = 800
    for line in lines:
        c.drawString(60, y, line)
        y -= 22
    c.save()
    return buf.getvalue()


def make_png(lines: list[str]) -> bytes:
    img = Image.new("RGB", (1400, 120 + 60 * len(lines)), "white")
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=36)
    for i, line in enumerate(lines):
        d.text((40, 40 + 60 * i), line, fill="black", font=font)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


INCOME = [
    "GOVERNMENT OF KARNATAKA - REVENUE DEPARTMENT",
    "INCOME CERTIFICATE",
    "Certificate No: KA-INC-2026-004512",
    "This is to certify that Kumari Asha Verma D/o Ramesh Verma resident of Mysuru",
    "belongs to the Scheduled Caste community.",
    "The annual income of the family from all sources is Rs. 2,40,000 (Two lakh forty thousand only).",
    "Date of issue: 12/06/2026",
]


def test_pdf_text_layer_income_certificate():
    r = process(make_pdf(INCOME), "application/pdf", DocumentCategory.INCOME_CERTIFICATE, document_id="d1",
                citizen_fields={"full_name": {"value": "Asha Verma"}, "annual_family_income": {"value": 300000}})
    assert r.classification is DocumentCategory.INCOME_CERTIFICATE and r.declared_category_matches
    assert r.engine == "pdf-text-layer"
    vals = {f.key: f.value for f in r.fields}
    assert vals["annual_family_income"] == 240000
    assert vals["full_name"] == "Asha Verma"
    assert vals["certificate_number"] == "KA-INC-2026-004512"
    assert vals["issue_date"] == "2026-06-12"
    assert vals["social_category"] == "SC"
    assert any(d["field"] == "annual_family_income" for d in r.discrepancies)  # profile says 3,00,000
    assert not any(d["field"] == "full_name" for d in r.discrepancies)
    assert any("not verification" in w for w in r.warnings)


def test_ocr_on_scanned_image_marksheet():
    png = make_png(["CENTRAL BOARD OF SECONDARY EDUCATION", "STATEMENT OF MARKS - SENIOR SECONDARY EXAMINATION",
                    "Name: Asha Verma", "Roll No 2611045", "Percentage: 88.40"])
    r = process(png, "image/png", DocumentCategory.ACADEMIC_RECORD, document_id="d2")
    assert r.engine.startswith("tesseract")
    assert r.classification is DocumentCategory.ACADEMIC_RECORD
    vals = {f.key: f.value for f in r.fields}
    assert vals.get("class12_percentage") == pytest.approx(88.4)
    assert vals.get("full_name", "").lower() == "asha verma"


def test_scanned_pdf_falls_back_to_ocr():
    png = make_png(["DISABILITY CERTIFICATE", "UDID card", "Name: Ravi Kumar", "He has 55 % permanent disability (locomotor)."])
    img = Image.open(io.BytesIO(png))
    buf = io.BytesIO()
    img.convert("RGB").save(buf, format="PDF")
    r = process(buf.getvalue(), "application/pdf", DocumentCategory.DISABILITY_CERTIFICATE, document_id="d3")
    assert r.engine.endswith("-pdf")
    vals = {f.key: f.value for f in r.fields}
    assert vals.get("disability_percentage") == 55


def test_misfiled_document_is_flagged():
    r = process(make_pdf(INCOME), "application/pdf", DocumentCategory.ACADEMIC_RECORD, document_id="d4")
    assert not r.declared_category_matches
    assert any("looks like income certificate" in w for w in r.warnings)


def test_aadhaar_numbers_are_never_extracted():
    r = process(make_pdf(["Unique Identification Authority of India", "Aadhaar", "Name: Asha Verma", "4123 5678 9012"]),
                "application/pdf", DocumentCategory.IDENTITY_PROOF, document_id="d5")
    assert "4123" not in repr(r.fields)
    assert any("Aadhaar" in w for w in r.warnings)


@pytest.mark.parametrize(
    "data,mime,code",
    [
        (b"", "application/pdf", "EMPTY_FILE"),
        (b"MZ\x90\x00 not a document", "application/pdf", "UNSUPPORTED_TYPE"),
        (b"%PDF-1.4 broken", "image/png", "TYPE_MISMATCH"),
        (b"%PDF-1.4 broken garbage", "application/pdf", "UNREADABLE_PDF"),
        (b"\x89PNG\r\n\x1a\nbroken", "image/png", "UNREADABLE_IMAGE"),
    ],
)
def test_invalid_documents_rejected(data, mime, code):
    with pytest.raises(DocumentRejected) as e:
        process(data, mime, DocumentCategory.OTHER, document_id="x")
    assert e.value.code == code
