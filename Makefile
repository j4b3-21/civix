SHELL := /bin/bash
COMPOSE ?= docker compose
AGENT_DIR := apps/agent
WEB_DIR := apps/web
API_DIR := apps/api

.PHONY: test-otp test-fault test-expire-sessions help up up-test down logs build test test-agent test-agent-fast test-web test-api migrate clean env registry-check

help:
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-16s %s\n", $$1, $$2}'

env: ## Create .env from the template with freshly generated secrets (never overwrites)
	@if [ -f .env ]; then echo ".env already exists (not overwritten)"; else ./infrastructure/scripts/generate-env.sh > .env && echo "wrote .env"; fi

up: ## Start the stack (docker compose up -d --build)
	$(COMPOSE) up -d --build

up-test: ## Start the stack plus the local CIVIX Test Portal (authorized test environment)
	$(COMPOSE) --profile test up -d --build

down: ## Stop the stack
	$(COMPOSE) --profile test --profile proxy down

logs: ## Follow logs
	$(COMPOSE) logs -f --tail=200

build: ## Build all images
	$(COMPOSE) --profile test build

migrate: ## Run Flyway migrations (the API also migrates on startup)
	$(COMPOSE) --profile tools run --rm flyway

test: test-agent test-web test-api ## Run every test suite

test-agent: ## Python agent tests (needs PostgreSQL via CIVIX_TEST_DATABASE_URL for checkpoint tests, Chromium, Tesseract)
	cd $(AGENT_DIR) && python -m pytest -q

test-agent-fast: ## Python tests without browser/e2e
	cd $(AGENT_DIR) && python -m pytest -q -m "not browser"

test-web: ## Frontend unit/component tests
	cd $(WEB_DIR) && npm run test

test-api: ## Spring Boot tests (Testcontainers needs Docker)
	cd $(API_DIR) && ./mvnw -B verify

registry-check: ## Validate every service definition
	cd $(AGENT_DIR) && python -m pytest -q tests/test_registry.py

clean: ## Stop and delete volumes and build outputs (DESTROYS LOCAL DATA)
	$(COMPOSE) --profile test --profile proxy down -v --remove-orphans
	rm -rf $(WEB_DIR)/.next $(API_DIR)/target $(AGENT_DIR)/.pytest_cache

# ---- CIVIX Test Portal helpers (authorized local test environment) ----------------------------------
PORTAL_PY = $(COMPOSE) exec -T test-portal python -c

test-otp: ## Print the latest OTP the test portal "sent" to MOBILE (its test inbox, not SMS)
	@$(PORTAL_PY) "import urllib.request,json;print(json.load(urllib.request.urlopen('http://127.0.0.1:8090/test-inbox?mobile=$(MOBILE)'))['latestOtp'])"

test-fault: ## Inject a test-portal fault, e.g. make test-fault FAULT='{"variant":"B"}'
	@$(PORTAL_PY) "import sys,urllib.request;r=urllib.request.Request('http://127.0.0.1:8090/test-control/faults',data=sys.argv[1].encode(),headers={'Content-Type':'application/json'});print(urllib.request.urlopen(r).read().decode())" '$(FAULT)'

test-expire-sessions: ## Expire every test-portal session
	@$(PORTAL_PY) "import urllib.request;print(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8090/test-control/expire-sessions',method='POST')).read().decode())"
