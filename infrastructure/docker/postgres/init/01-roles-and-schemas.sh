#!/usr/bin/env bash
# Creates least-privilege roles and schemas on first database initialisation.
#   civix_api   owns schema "civix" (Flyway migrations, business data)
#   civix_agent owns schema "agent" (LangGraph checkpoints, task runs, event outbox) and cannot read "civix"
set -euo pipefail
: "${CIVIX_DB_PASSWORD:?CIVIX_DB_PASSWORD is required}"
: "${AGENT_DB_PASSWORD:?AGENT_DB_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v api_pw="$CIVIX_DB_PASSWORD" -v agent_pw="$AGENT_DB_PASSWORD" -v db="$POSTGRES_DB" <<'SQL'
CREATE ROLE civix_api LOGIN PASSWORD :'api_pw';
CREATE ROLE civix_agent LOGIN PASSWORD :'agent_pw';
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO civix_api, civix_agent;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA civix AUTHORIZATION civix_api;
CREATE SCHEMA agent AUTHORIZATION civix_agent;
-- extensions are created by the superuser so the API role does not need superuser rights
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA civix;
ALTER ROLE civix_api SET search_path = civix, public;
ALTER ROLE civix_agent SET search_path = agent;
SQL
