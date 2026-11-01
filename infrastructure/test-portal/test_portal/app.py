"""CIVIX Test Portal — an AUTHORIZED LOCAL TEST ENVIRONMENT. NOT A GOVERNMENT WEBSITE.

It imitates the *shape* of a typical scholarship portal so the CIVIX browser engine can be exercised end-to-end:
OTP sign-in (OTP is delivered to a visible "test inbox" instead of SMS), multi-page form, document uploads, a declaration,
a review page, a final submission and an acknowledgement with a reference number.

Fault injection endpoints under /test-control let automated tests simulate a changed layout, expired sessions, slow or
failing responses and an acknowledgement without a reference number.
"""

from __future__ import annotations

import html
import itertools
import secrets
import threading
import time
from dataclasses import dataclass, field
from typing import Any

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response

STATES = [
    ("IN-AP", "Andhra Pradesh"), ("IN-AR", "Arunachal Pradesh"), ("IN-AS", "Assam"), ("IN-BR", "Bihar"),
    ("IN-CT", "Chhattisgarh"), ("IN-GA", "Goa"), ("IN-GJ", "Gujarat"), ("IN-HR", "Haryana"), ("IN-HP", "Himachal Pradesh"),
    ("IN-JH", "Jharkhand"), ("IN-KA", "Karnataka"), ("IN-KL", "Kerala"), ("IN-MP", "Madhya Pradesh"), ("IN-MH", "Maharashtra"),
    ("IN-MN", "Manipur"), ("IN-ML", "Meghalaya"), ("IN-MZ", "Mizoram"), ("IN-NL", "Nagaland"), ("IN-OR", "Odisha"),
    ("IN-PB", "Punjab"), ("IN-RJ", "Rajasthan"), ("IN-SK", "Sikkim"), ("IN-TN", "Tamil Nadu"), ("IN-TG", "Telangana"),
    ("IN-TR", "Tripura"), ("IN-UP", "Uttar Pradesh"), ("IN-UT", "Uttarakhand"), ("IN-WB", "West Bengal"),
    ("IN-AN", "Andaman and Nicobar Islands"), ("IN-CH", "Chandigarh"), ("IN-DH", "Dadra and Nagar Haveli and Daman and Diu"),
    ("IN-DL", "Delhi"), ("IN-JK", "Jammu and Kashmir"), ("IN-LA", "Ladakh"), ("IN-LD", "Lakshadweep"), ("IN-PY", "Puducherry"),
]
SESSION_TTL_SECONDS = 1800


@dataclass
class Session:
    mobile: str
    verified: bool = False
    created: float = field(default_factory=time.time)
    data: dict[str, Any] = field(default_factory=dict)
    files: dict[str, dict[str, Any]] = field(default_factory=dict)
    declared: bool = False


@dataclass
class Faults:
    variant: str = "A"           # "B" = changed layout (different labels, ids and button text)
    slow_ms: int = 0             # delay every page by this many ms
    fail_next: int = 0           # next N page requests return HTTP 503
    omit_reference: bool = False  # acknowledgement page without reference number
    captcha: bool = False        # sign-in requires a text CAPTCHA


class PortalState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.reset()

    def reset(self) -> None:
        self.sessions: dict[str, Session] = {}
        self.pending_otp: dict[str, tuple[str, str]] = {}  # pending sid -> (mobile, otp)
        self.inbox: dict[str, list[dict[str, Any]]] = {}
        self.applications: dict[str, dict[str, Any]] = {}
        self.faults = Faults()
        self.counter = itertools.count(1)
        self.captchas: dict[str, str] = {}


STATE = PortalState()
app = FastAPI(title="CIVIX Test Portal (test environment)", docs_url=None, redoc_url=None)


# ------------------------------------------------------------------ helpers

def _page(title: str, body: str, *, status: int = 200) -> HTMLResponse:
    doc = f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><title>{html.escape(title)} — CIVIX Test Portal</title>
<meta name="robots" content="noindex">
<style>
body{{font-family:system-ui,sans-serif;max-width:760px;margin:0 auto;padding:0 16px 48px;color:#1f2937}}
.banner{{background:#fef3c7;border:2px solid #d97706;padding:10px 14px;margin:16px 0;font-weight:600}}
label{{display:block;margin-top:14px;font-weight:600}} input,select{{display:block;margin-top:4px;padding:8px;width:100%;max-width:420px}}
input[type=checkbox]{{display:inline;width:auto}} button{{margin-top:20px;padding:10px 18px;background:#1e3a8a;color:#fff;border:0;border-radius:6px;cursor:pointer}}
dl{{display:grid;grid-template-columns:220px 1fr;gap:6px 12px}} dt{{font-weight:600}} .error{{color:#b91c1c}}
</style></head><body>
<div class="banner" role="note" data-testid="test-environment-banner">CIVIX TEST PORTAL — authorized local test environment. This is NOT a government website. Nothing submitted here reaches any government system.</div>
{body}</body></html>"""
    return HTMLResponse(doc, status_code=status)


def _session(request: Request) -> Session | None:
    sid = request.cookies.get("tp_session")
    if not sid:
        return None
    s = STATE.sessions.get(sid)
    if s is None:
        return None
    if time.time() - s.created > SESSION_TTL_SECONDS:
        STATE.sessions.pop(sid, None)
        return None
    return s


def _expired_page() -> HTMLResponse:
    return _page("Session expired", '<h1>Sign in</h1><p class="error" role="alert">Your session has expired. Please sign in again.</p>'
                 + _login_form(), status=401)


def _login_form(error: str = "") -> str:
    err = f'<p class="error" role="alert">{html.escape(error)}</p>' if error else ""
    captcha = ""
    if STATE.faults.captcha:
        cid = secrets.token_hex(4)
        code = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(5))
        STATE.captchas[cid] = code
        captcha = (f'<p>Type the characters shown: <span data-testid="captcha-text" style="letter-spacing:6px;font-family:monospace">{code}</span></p>'
                   f'<input type="hidden" name="captcha_id" value="{cid}"><label for="captcha">Captcha</label><input id="captcha" name="captcha" required>')
    return f"""{err}<form method="post" action="/login">
<label for="mobile">Mobile number</label><input id="mobile" name="mobile" type="tel" inputmode="numeric" pattern="[6-9][0-9]{{9}}" required>
{captcha}<button type="submit">Send OTP</button></form>"""


@app.middleware("http")
async def _faults(request: Request, call_next):
    path = request.url.path
    if not path.startswith("/test-control") and not path.startswith("/test-inbox") and path != "/health":
        if STATE.faults.slow_ms:
            import asyncio
            await asyncio.sleep(STATE.faults.slow_ms / 1000)
        with STATE.lock:
            if STATE.faults.fail_next > 0:
                STATE.faults.fail_next -= 1
                return Response("Service temporarily unavailable", status_code=503)
    return await call_next(request)


# ------------------------------------------------------------------ pages

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "environment": "TEST"}


@app.get("/", response_class=HTMLResponse)
def home() -> HTMLResponse:
    return _page("Home", '<h1>CIVIX Test Portal</h1><p>Demo Merit Scholarship (test environment).</p><p><a href="/apply">Apply for the demo scholarship</a></p>')


@app.get("/apply")
def apply(request: Request) -> Response:
    s = _session(request)
    if s is None or not s.verified:
        return RedirectResponse("/login", status_code=303)
    return RedirectResponse("/apply/personal", status_code=303)


@app.get("/login", response_class=HTMLResponse)
def login_page() -> HTMLResponse:
    return _page("Sign in", "<h1>Sign in</h1>" + _login_form())


@app.post("/login")
def login(mobile: str = Form(...), captcha: str | None = Form(None), captcha_id: str | None = Form(None)) -> Response:
    if not (len(mobile) == 10 and mobile.isdigit() and mobile[0] in "6789"):
        return _page("Sign in", "<h1>Sign in</h1>" + _login_form("Enter a valid 10-digit mobile number."), status=422)
    if STATE.faults.captcha:
        expected = STATE.captchas.pop(captcha_id or "", None)
        if not expected or (captcha or "").strip().upper() != expected:
            return _page("Sign in", "<h1>Sign in</h1>" + _login_form("The characters did not match. Try again."), status=422)
    pending = secrets.token_urlsafe(16)
    otp = f"{secrets.randbelow(1_000_000):06d}"
    STATE.pending_otp[pending] = (mobile, otp)
    STATE.inbox.setdefault(mobile, []).append({"otp": otp, "at": time.time()})
    resp = RedirectResponse("/login/otp", status_code=303)
    resp.set_cookie("tp_pending", pending, httponly=True, samesite="lax")
    return resp


@app.get("/login/otp", response_class=HTMLResponse)
def otp_page(request: Request) -> HTMLResponse:
    pending = STATE.pending_otp.get(request.cookies.get("tp_pending", ""))
    if pending is None:
        return _page("Sign in", "<h1>Sign in</h1>" + _login_form("Please request an OTP first."))
    masked = "******" + pending[0][-4:]
    return _page("Verify OTP", f"""<h1>Verify OTP</h1><p>Enter the one-time password sent to {masked}. (Test environment: see the test inbox.)</p>
<form method="post" action="/login/otp"><label for="otp">One-time password</label><input id="otp" name="otp" inputmode="numeric" autocomplete="one-time-code" required>
<button type="submit">Verify OTP</button></form>""")


@app.post("/login/otp")
def verify_otp(request: Request, otp: str = Form(...)) -> Response:
    key = request.cookies.get("tp_pending", "")
    pending = STATE.pending_otp.get(key)
    if pending is None or otp.strip() != pending[1]:
        return _page("Verify OTP", '<h1>Verify OTP</h1><p class="error" role="alert">Incorrect OTP.</p>'
                     '<form method="post" action="/login/otp"><label for="otp">One-time password</label><input id="otp" name="otp" required>'
                     '<button type="submit">Verify OTP</button></form>', status=422)
    STATE.pending_otp.pop(key, None)
    sid = secrets.token_urlsafe(24)
    STATE.sessions[sid] = Session(mobile=pending[0], verified=True)
    resp = RedirectResponse("/apply/personal", status_code=303)
    resp.set_cookie("tp_session", sid, httponly=True, samesite="lax")
    resp.delete_cookie("tp_pending")
    return resp


def _labels() -> dict[str, str]:
    if STATE.faults.variant == "B":
        return {"full_name": "Applicant full name", "dob": "Birth date", "gender": "Gender of applicant", "email": "E-mail",
                "state": "State / UT", "next": "Next", "course_level": "Level of course", "course_name": "Name of course",
                "institution": "Name of institution", "class12": "Percentage in Class XII", "income": "Total annual family income"}
    return {"full_name": "Full name", "dob": "Date of birth", "gender": "Gender", "email": "Email address", "state": "State",
            "next": "Save and continue", "course_level": "Course level", "course_name": "Course name",
            "institution": "Institution name", "class12": "Class XII percentage", "income": "Annual family income"}


def _id(name: str) -> str:
    return f"fld_{name}_{STATE.faults.variant.lower()}" if STATE.faults.variant == "B" else name


@app.get("/apply/personal", response_class=HTMLResponse)
def personal_page(request: Request) -> HTMLResponse:
    s = _session(request)
    if s is None:
        return _expired_page()
    L = _labels()
    opts = "".join(f'<option value="{c}">{html.escape(n)}</option>' for c, n in STATES)
    gender = "".join(f"<option>{g}</option>" for g in ("Female", "Male", "Transgender", "Other"))
    return _page("Personal details", f"""<h1>Personal details</h1><form method="post" action="/apply/personal">
<label for="{_id('full_name')}">{L['full_name']}</label><input id="{_id('full_name')}" name="full_name" required>
<label for="{_id('dob')}">{L['dob']}</label><input id="{_id('dob')}" name="dob" type="date" required>
<label for="{_id('gender')}">{L['gender']}</label><select id="{_id('gender')}" name="gender" required><option value="">Select</option>{gender}</select>
<label for="{_id('email')}">{L['email']}</label><input id="{_id('email')}" name="email" type="email">
<label for="{_id('state')}">{L['state']}</label><select id="{_id('state')}" name="state" required><option value="">Select</option>{opts}</select>
<button type="submit">{L['next']}</button></form>""")


@app.post("/apply/personal")
def personal_submit(request: Request, full_name: str = Form(...), dob: str = Form(...), gender: str = Form(...),
                    state: str = Form(...), email: str = Form("")) -> Response:
    s = _session(request)
    if s is None:
        return _expired_page()
    s.data.update(full_name=full_name.strip(), dob=dob, gender=gender, email=email.strip(), state=state)
    return RedirectResponse("/apply/academic", status_code=303)


@app.get("/apply/academic", response_class=HTMLResponse)
def academic_page(request: Request) -> HTMLResponse:
    s = _session(request)
    if s is None:
        return _expired_page()
    L = _labels()
    levels = "".join(f"<option>{x}</option>" for x in ("Diploma", "Undergraduate", "Postgraduate"))
    return _page("Academic and income details", f"""<h1>Academic and income details</h1><form method="post" action="/apply/academic">
<label for="{_id('course_level')}">{L['course_level']}</label><select id="{_id('course_level')}" name="course_level" required><option value="">Select</option>{levels}</select>
<label for="{_id('course_name')}">{L['course_name']}</label><input id="{_id('course_name')}" name="course_name" required>
<label for="{_id('institution')}">{L['institution']}</label><input id="{_id('institution')}" name="institution" required>
<label for="{_id('class12')}">{L['class12']}</label><input id="{_id('class12')}" name="class12" type="number" step="0.01" min="0" max="100" required>
<label for="{_id('income')}">{L['income']}</label><input id="{_id('income')}" name="income" type="number" min="0" required>
<button type="submit">{L['next']}</button></form>""")


@app.post("/apply/academic")
def academic_submit(request: Request, course_level: str = Form(...), course_name: str = Form(...), institution: str = Form(...),
                    class12: float = Form(...), income: int = Form(...)) -> Response:
    s = _session(request)
    if s is None:
        return _expired_page()
    s.data.update(course_level=course_level, course_name=course_name.strip(), institution=institution.strip(), class12=class12, income=income)
    return RedirectResponse("/apply/documents", status_code=303)


@app.get("/apply/documents", response_class=HTMLResponse)
def documents_page(request: Request) -> HTMLResponse:
    s = _session(request)
    if s is None:
        return _expired_page()
    return _page("Upload documents", """<h1>Upload documents</h1><form method="post" action="/apply/documents" enctype="multipart/form-data">
<label for="income_certificate">Income certificate</label><input id="income_certificate" name="income_certificate" type="file" accept=".pdf,.png,.jpg,.jpeg" required>
<label for="marksheet">Class XII marksheet</label><input id="marksheet" name="marksheet" type="file" accept=".pdf,.png,.jpg,.jpeg" required>
<button type="submit">Upload and continue</button></form>""")


@app.post("/apply/documents")
async def documents_submit(request: Request, income_certificate: UploadFile = File(...), marksheet: UploadFile = File(...)) -> Response:
    s = _session(request)
    if s is None:
        return _expired_page()
    for key, f in (("income_certificate", income_certificate), ("marksheet", marksheet)):
        content = await f.read()
        if not content:
            return _page("Upload documents", '<h1>Upload documents</h1><p class="error" role="alert">Empty file.</p>', status=422)
        s.files[key] = {"name": f.filename, "size": len(content)}
    return RedirectResponse("/apply/declaration", status_code=303)


@app.get("/apply/declaration", response_class=HTMLResponse)
def declaration_page(request: Request) -> HTMLResponse:
    s = _session(request)
    if s is None:
        return _expired_page()
    return _page("Declaration", """<h1>Declaration</h1><form method="post" action="/apply/declaration">
<p><input id="declare" name="declare" type="checkbox" value="yes" required> <label for="declare" style="display:inline">I declare that the information provided is true and correct to the best of my knowledge.</label></p>
<button type="submit">Continue to review</button></form>""")


@app.post("/apply/declaration")
def declaration_submit(request: Request, declare: str = Form("")) -> Response:
    s = _session(request)
    if s is None:
        return _expired_page()
    if declare != "yes":
        return _page("Declaration", '<h1>Declaration</h1><p class="error" role="alert">You must accept the declaration.</p>', status=422)
    s.declared = True
    return RedirectResponse("/apply/review", status_code=303)


@app.get("/apply/review", response_class=HTMLResponse)
def review_page(request: Request) -> HTMLResponse:
    s = _session(request)
    if s is None:
        return _expired_page()
    rows = [("Full name", s.data.get("full_name")), ("Date of birth", s.data.get("dob")), ("Gender", s.data.get("gender")),
            ("State", s.data.get("state")), ("Course level", s.data.get("course_level")), ("Course name", s.data.get("course_name")),
            ("Institution name", s.data.get("institution")), ("Class XII percentage", s.data.get("class12")),
            ("Annual family income", s.data.get("income")),
            ("Documents", ", ".join(f"{k} ({v['size']} bytes)" for k, v in s.files.items()) or "none"),
            ("Declaration", "Accepted" if s.declared else "Not accepted")]
    dl = "".join(f"<dt>{html.escape(k)}</dt><dd>{html.escape(str(v))}</dd>" for k, v in rows)
    return _page("Review your application", f"""<h1>Review your application</h1><dl data-testid="review-summary">{dl}</dl>
<form method="post" action="/apply/submit"><button type="submit">Submit application</button></form>""")


@app.post("/apply/submit")
def submit(request: Request) -> Response:
    s = _session(request)
    if s is None:
        return _expired_page()
    missing = [k for k in ("full_name", "dob", "course_name", "income") if not s.data.get(k)]
    if missing or len(s.files) < 2 or not s.declared:
        return _page("Review your application", '<h1>Review your application</h1><p class="error" role="alert">Application incomplete.</p>', status=422)
    with STATE.lock:
        ref = f"TP-{time.gmtime().tm_year}-{next(STATE.counter):06d}"
        STATE.applications[ref] = {"mobile": s.mobile, "data": dict(s.data), "files": dict(s.files), "status": "Received", "at": time.time()}
    resp = RedirectResponse(f"/apply/acknowledgement?ref={ref}", status_code=303)
    return resp


@app.get("/apply/acknowledgement", response_class=HTMLResponse)
def acknowledgement(ref: str) -> HTMLResponse:
    if ref not in STATE.applications:
        return _page("Not found", "<h1>Not found</h1>", status=404)
    ref_html = "" if STATE.faults.omit_reference else f'<p>Reference number: <strong data-testid="reference-number">{html.escape(ref)}</strong></p>'
    return _page("Application submitted", f"""<h1>Application submitted</h1>
<p data-testid="acknowledgement">Your application for the Demo Merit Scholarship (test environment) has been received.</p>{ref_html}""")


@app.get("/status", response_class=HTMLResponse)
def status_page(ref: str) -> HTMLResponse:
    a = STATE.applications.get(ref)
    if a is None:
        return _page("Application status", f"<h1>Application status</h1><p>No application found for {html.escape(ref)}.</p>", status=404)
    return _page("Application status", f'<h1>Application status</h1><p>Reference {html.escape(ref)}: <span data-testid="status">{a["status"]}</span></p>')


# ------------------------------------------------------------------ test inbox (simulated SMS) & controls

@app.get("/test-inbox")
def test_inbox(mobile: str) -> JSONResponse:
    msgs = STATE.inbox.get(mobile, [])
    return JSONResponse({"mobile": mobile, "messages": msgs[-5:], "latestOtp": msgs[-1]["otp"] if msgs else None})


@app.post("/test-control/reset")
def control_reset() -> dict[str, str]:
    STATE.reset()
    return {"status": "reset"}


@app.post("/test-control/faults")
async def control_faults(request: Request) -> dict[str, Any]:
    body = await request.json()
    for k, v in body.items():
        if not hasattr(STATE.faults, k):
            return {"error": f"unknown fault {k}"}
        setattr(STATE.faults, k, v)
    return {"faults": STATE.faults.__dict__}


@app.post("/test-control/expire-sessions")
def control_expire() -> dict[str, int]:
    n = len(STATE.sessions)
    STATE.sessions.clear()
    return {"expired": n}


@app.get("/test-control/applications")
def control_applications() -> dict[str, Any]:
    return {"applications": STATE.applications}
