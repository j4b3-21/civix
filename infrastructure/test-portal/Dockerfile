# CIVIX Test Portal — authorized local test environment. NOT a government website. Never deploy publicly.
FROM python:3.12.12-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /srv
RUN pip install "fastapi==0.120.4" "uvicorn==0.54.0" "python-multipart==0.0.33" && useradd --system --uid 10003 portal
COPY infrastructure/test-portal/test_portal ./test_portal
USER portal
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=2)"
CMD ["uvicorn", "test_portal.app:app", "--host", "0.0.0.0", "--port", "8090"]
