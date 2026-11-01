"""Document intelligence: validation → text (PDF text layer or OCR) → classification → field extraction →
consistency checks. Extraction is never verification: every result requires citizen review, and CIVIX never claims
a certificate is authentic because OCR succeeded."""

from __future__ import annotations

import io
import re
import statistics
from dataclasses import dataclass, field
from typing import Any

import pypdfium2 as pdfium
import pytesseract
from PIL import Image, ImageOps
from pypdf import PdfReader
from rapidfuzz import fuzz

from app.contracts.common import DocumentCategory

MAX_BYTES = 10 * 1024 * 1024
MAX_PAGES = 10
OCR_LANGS = "eng+hin"


class DocumentRejected(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass
class ExtractedField:
    key: str
    label: str
    value: Any
    confidence: float
    profile_field: str | None


@dataclass
class ExtractionResult:
    classification: DocumentCategory
    classification_confidence: float
    declared_category_matches: bool
    fields: list[ExtractedField]
    discrepancies: list[dict[str, Any]]
    warnings: list[str]
    engine: str
    text_characters: int
    text_confidence: float = 0.0
    extra: dict[str, Any] = field(default_factory=dict)


def sniff_mime(data: bytes) -> str:
    if data.startswith(b"%PDF-"):
        return "application/pdf"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    raise DocumentRejected("UNSUPPORTED_TYPE", "only PDF, PNG and JPEG files are accepted")


def validate(data: bytes, declared_mime: str) -> str:
    if not data:
        raise DocumentRejected("EMPTY_FILE", "the file is empty")
    if len(data) > MAX_BYTES:
        raise DocumentRejected("FILE_TOO_LARGE", "the file exceeds 10 MB")
    mime = sniff_mime(data)
    if declared_mime and mime != declared_mime:
        raise DocumentRejected("TYPE_MISMATCH", f"file content is {mime} but was declared as {declared_mime}")
    return mime


def _ocr_image(img: Image.Image) -> tuple[str, float]:
    img = ImageOps.exif_transpose(img).convert("L")
    if max(img.size) < 1200:
        scale = 1200 / max(img.size)
        img = img.resize((int(img.width * scale), int(img.height * scale)))
    data = pytesseract.image_to_data(img, lang=OCR_LANGS, output_type=pytesseract.Output.DICT, config="--psm 6")
    words, confs = [], []
    for w, c in zip(data["text"], data["conf"], strict=False):
        if w.strip():
            words.append(w)
            try:
                if float(c) >= 0:
                    confs.append(float(c))
            except (TypeError, ValueError):
                pass
    text = pytesseract.image_to_string(img, lang=OCR_LANGS, config="--psm 6")
    return text, (statistics.mean(confs) / 100 if confs else 0.0)


def extract_text(data: bytes, mime: str) -> tuple[str, float, str]:
    """Returns (text, confidence 0..1, engine)."""
    if mime == "application/pdf":
        try:
            reader = PdfReader(io.BytesIO(data))
        except Exception as exc:  # noqa: BLE001 - corrupt file
            raise DocumentRejected("UNREADABLE_PDF", "the PDF could not be read") from exc
        if reader.is_encrypted:
            raise DocumentRejected("ENCRYPTED_PDF", "password-protected PDFs are not supported")
        pages = reader.pages[:MAX_PAGES]
        text = "\n".join((p.extract_text() or "") for p in pages)
        if len(text.strip()) >= 40 * max(1, len(pages)) // 2:
            return text, 0.95, "pdf-text-layer"
        pdf = pdfium.PdfDocument(data)
        chunks, confs = [], []
        for i in range(min(len(pdf), MAX_PAGES)):
            img = pdf[i].render(scale=2.5).to_pil()
            t, c = _ocr_image(img)
            chunks.append(t)
            confs.append(c)
        return "\n".join(chunks), (statistics.mean(confs) if confs else 0.0), f"tesseract-{pytesseract.get_tesseract_version()}-pdf"
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception as exc:  # noqa: BLE001
        raise DocumentRejected("UNREADABLE_IMAGE", "the image could not be read") from exc
    t, c = _ocr_image(img)
    return t, c, f"tesseract-{pytesseract.get_tesseract_version()}"


_KEYWORDS: dict[DocumentCategory, list[str]] = {
    DocumentCategory.INCOME_CERTIFICATE: ["income certificate", "annual income", "income from all sources", "आय प्रमाण", "वार्षिक आय", "family income"],
    DocumentCategory.CASTE_CERTIFICATE: ["caste certificate", "scheduled caste", "scheduled tribe", "other backward class", "जाति प्रमाण", "belongs to the"],
    DocumentCategory.ACADEMIC_RECORD: ["statement of marks", "marksheet", "mark sheet", "marks obtained", "senior secondary", "board of", "percentage", "grade", "roll no", "examination"],
    DocumentCategory.IDENTITY_PROOF: ["aadhaar", "unique identification", "आधार", "permanent account number", "income tax department", "voter", "election commission", "passport"],
    DocumentCategory.ADDRESS_PROOF: ["electricity bill", "consumer no", "billing address", "ration card", "address proof"],
    DocumentCategory.BANK_DOCUMENT: ["passbook", "ifsc", "account no", "account number", "branch", "bank"],
    DocumentCategory.RESIDENCE_CERTIFICATE: ["domicile", "residence certificate", "resident of", "निवास प्रमाण", "permanent resident"],
    DocumentCategory.DISABILITY_CERTIFICATE: ["disability certificate", "udid", "divyang", "percentage of disability", "benchmark disability", "दिव्यांग"],
    DocumentCategory.PHOTOGRAPH: [],
    DocumentCategory.OTHER: [],
}


def classify(text: str, declared: DocumentCategory, mime: str) -> tuple[DocumentCategory, float]:
    low = text.lower()
    scores = {cat: sum(low.count(k) for k in kws) for cat, kws in _KEYWORDS.items()}
    best = max(scores, key=lambda c: scores[c])
    total = sum(scores.values())
    if total == 0:
        if mime != "application/pdf" and len(text.strip()) < 20 and declared is DocumentCategory.PHOTOGRAPH:
            return DocumentCategory.PHOTOGRAPH, 0.5
        return DocumentCategory.OTHER, 0.0
    conf = scores[best] / total
    conf = min(0.99, conf * min(1.0, 0.5 + scores[best] / 6))
    return best, round(conf, 3)


_AMOUNT = r"(?:rs\.?|inr|₹|रु\.?)\s*([0-9][0-9,]{2,12})(?:\.\d{1,2})?"
_PATTERNS: list[tuple[str, str, str | None, re.Pattern[str], float, set[DocumentCategory] | None]] = [
    ("full_name", "Name", "full_name",
     re.compile(r"(?:certify that|certified that)\s+(?:shri|smt|kumari|ms|mr|miss|km)?\.?\s*([A-Z][A-Za-z.]+(?:[ \t]+[A-Z][A-Za-z.]+){0,4})[ \t]*,?[ \t]*(?:s/o|d/o|w/o|son|daughter|wife)", re.I), 0.85, None),
    ("full_name", "Name", "full_name", re.compile(r"(?:^|\n)\s*(?:candidate'?s?\s+)?name\s*(?:of\s+(?:the\s+)?(?:candidate|student|applicant))?[ \t]*[:\-][ \t]*([A-Z][A-Za-z.]+(?:[ \t]+[A-Z][A-Za-z.]+){0,4})[ \t]*(?:\n|$)", re.I), 0.8, None),
    ("annual_family_income", "Annual family income", "annual_family_income", re.compile(r"(?:annual|yearly|total)\s+(?:family\s+)?income[^0-9₹]{0,60}" + _AMOUNT, re.I), 0.85,
     {DocumentCategory.INCOME_CERTIFICATE}),
    ("annual_family_income", "Annual family income", "annual_family_income", re.compile(r"income[^0-9₹]{0,40}" + _AMOUNT, re.I), 0.6, {DocumentCategory.INCOME_CERTIFICATE}),
    ("date_of_birth", "Date of birth", "date_of_birth", re.compile(r"(?:date\s+of\s+birth|d\.?o\.?b\.?|जन्म\s+तिथि)\s*[:\-]?\s*(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{4})", re.I), 0.85, None),
    ("class12_percentage", "Class XII percentage", "class12_percentage", re.compile(r"(?:percentage|aggregate|total\s+percentage)\s*(?:of\s+marks)?\s*[:\-]?\s*(\d{2,3}(?:\.\d{1,2})?)\s*%?", re.I), 0.75,
     {DocumentCategory.ACADEMIC_RECORD}),
    ("disability_percentage", "Disability (%)", "disability_percentage", re.compile(r"(\d{2,3})\s*%\s*(?:\(.*?\)\s*)?(?:permanent\s+)?(?:physical\s+)?(?:disab|impair)", re.I), 0.8,
     {DocumentCategory.DISABILITY_CERTIFICATE}),
    ("certificate_number", "Certificate number", None, re.compile(r"(?:certificate|cert\.?)\s*(?:no|number)\.?\s*[:\-]?\s*([A-Z0-9][A-Z0-9/\-]{4,40})", re.I), 0.8, None),
    ("issue_date", "Date of issue", None, re.compile(r"(?:date\s+of\s+issue|issued\s+on|dated?)\s*[:\-]?\s*(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{4})", re.I), 0.7, None),
]
_CATEGORY_WORDS = [("scheduled caste", "SC"), ("scheduled tribe", "ST"), ("other backward class", "OBC"), ("economically weaker section", "EWS")]
_AADHAAR = re.compile(r"\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b")


def _date_iso(s: str) -> str | None:
    m = re.match(r"(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})", s)
    if not m:
        return None
    d, mth, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
    if not (1 <= d <= 31 and 1 <= mth <= 12 and 1900 <= y <= 2100):
        return None
    return f"{y:04d}-{mth:02d}-{d:02d}"


def extract_fields(text: str, category: DocumentCategory, text_conf: float) -> tuple[list[ExtractedField], list[str]]:
    found: dict[str, ExtractedField] = {}
    warnings: list[str] = []
    for key, label, profile_key, pattern, conf, cats in _PATTERNS:
        if key in found or (cats is not None and category not in cats):
            continue
        m = pattern.search(text)
        if not m:
            continue
        raw = m.group(1).strip()
        value: Any = raw
        if key in ("annual_family_income",):
            value = int(raw.replace(",", ""))
        elif key in ("class12_percentage", "disability_percentage"):
            value = float(raw)
            if not 0 <= value <= 100:
                continue
        elif key in ("date_of_birth", "issue_date"):
            value = _date_iso(raw)
            if value is None:
                continue
        elif key == "full_name":
            value = re.sub(r"\s+", " ", raw).strip(" .").title()
            if len(value) < 3:
                continue
        found[key] = ExtractedField(key=key, label=label, value=value, confidence=round(conf * max(text_conf, 0.3), 3), profile_field=profile_key)
    if category in (DocumentCategory.CASTE_CERTIFICATE, DocumentCategory.INCOME_CERTIFICATE):
        low = text.lower()
        for phrase, code in _CATEGORY_WORDS:
            if phrase in low:
                found["social_category"] = ExtractedField("social_category", "Social category", code, round(0.8 * max(text_conf, 0.3), 3), "social_category")
                break
    if _AADHAAR.search(text):
        warnings.append("An Aadhaar-like number was detected. CIVIX does not extract or store Aadhaar numbers.")
    return list(found.values()), warnings


def consistency(fields: list[ExtractedField], citizen_fields: dict[str, Any], other_docs: list[dict[str, Any]], document_id: str) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    by_key = {f.key: f for f in fields}
    if "full_name" in by_key:
        name = str(by_key["full_name"].value)
        prof = (citizen_fields.get("full_name") or {}).get("value")
        if prof and fuzz.token_sort_ratio(name.lower(), str(prof).lower()) < 85:
            out.append({"field": "full_name", "message": f"Name on the document ('{name}') differs from your profile.", "severity": "WARNING", "documentIds": [document_id]})
        for od in other_docs:
            other = next((f for f in od.get("fields", []) if f.get("key") == "full_name"), None)
            if other and fuzz.token_sort_ratio(name.lower(), str(other.get("value", "")).lower()) < 85:
                out.append({"field": "full_name", "message": "Name differs between your documents.", "severity": "WARNING",
                            "documentIds": [document_id, od.get("documentId")]})
    if "annual_family_income" in by_key:
        prof = (citizen_fields.get("annual_family_income") or {}).get("value")
        if prof is not None:
            try:
                if int(prof) != int(by_key["annual_family_income"].value):
                    out.append({"field": "annual_family_income", "message": "Income on the certificate differs from your profile.", "severity": "WARNING", "documentIds": [document_id]})
            except (TypeError, ValueError):
                pass
    if "social_category" in by_key:
        prof = (citizen_fields.get("social_category") or {}).get("value")
        if prof and prof != by_key["social_category"].value:
            out.append({"field": "social_category", "message": "Category on the certificate differs from your profile.", "severity": "ERROR", "documentIds": [document_id]})
    if "date_of_birth" in by_key:
        prof = (citizen_fields.get("date_of_birth") or {}).get("value")
        if prof and str(prof)[:10] != by_key["date_of_birth"].value:
            out.append({"field": "date_of_birth", "message": "Date of birth differs from your profile.", "severity": "WARNING", "documentIds": [document_id]})
    return out


def process(data: bytes, mime: str, declared: DocumentCategory, *, document_id: str, citizen_fields: dict[str, Any] | None = None,
            other_docs: list[dict[str, Any]] | None = None) -> ExtractionResult:
    mime = validate(data, mime)
    text, conf, engine = extract_text(data, mime)
    category, cat_conf = classify(text, declared, mime)
    effective = declared if category is DocumentCategory.OTHER else category
    fields, warnings = extract_fields(text, effective, conf)
    if category is not DocumentCategory.OTHER and category is not declared:
        warnings.append(f"The document looks like {category.value.replace('_', ' ').lower()}, but it was uploaded as {declared.value.replace('_', ' ').lower()}.")
    if conf < 0.6:
        warnings.append("Low text-recognition confidence. Please check every extracted value carefully.")
    if not text.strip():
        warnings.append("No readable text was found in this document.")
    warnings.append("Extraction is not verification: CIVIX does not confirm that this document is authentic.")
    return ExtractionResult(
        classification=category, classification_confidence=cat_conf, declared_category_matches=category in (declared, DocumentCategory.OTHER),
        fields=fields, discrepancies=consistency(fields, citizen_fields or {}, other_docs or [], document_id), warnings=warnings,
        engine=engine, text_characters=len(text), text_confidence=round(conf, 3),
    )
