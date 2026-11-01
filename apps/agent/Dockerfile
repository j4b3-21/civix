# CIVIX agent service (FastAPI + LangGraph + document intelligence). Browser automation runs in a separate image.
FROM python:3.12.12-slim-bookworm AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-hin \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY apps/agent/pyproject.toml ./
RUN pip install --upgrade pip==25.3 && pip install .  # installs pinned runtime dependencies
COPY apps/agent/app ./app
COPY service-registry /app/service-registry
RUN pip install --no-deps . && useradd --system --uid 10001 --home /app civix && chown -R civix /app
USER civix
ENV SERVICE_REGISTRY_PATH=/app/service-registry
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/internal/v1/health', timeout=4).status == 200 else 1)"
CMD ["uvicorn", "app.api.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--no-server-header"]
