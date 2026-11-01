#!/usr/bin/env bash
# Prints a .env built from .env.example with every CHANGE_ME replaced by a fresh random secret.
# Usage: ./infrastructure/scripts/generate-env.sh > .env
set -euo pipefail
cd "$(dirname "$0")/../.."
rand() { openssl rand -base64 48 | tr -d '\n/+=' | cut -c1-48; }
kms_key() { echo "civix-key:$(openssl rand -base64 32 | tr -d '\n')"; }
while IFS= read -r line; do
  if [[ "$line" == MINIO_KMS_SECRET_KEY=* ]]; then
    echo "MINIO_KMS_SECRET_KEY=$(kms_key)"
  elif [[ "$line" == *CHANGE_ME* ]]; then
    key="${line%%=*}"
    if [[ "$key" == "MINIO_ROOT_USER" ]]; then echo "MINIO_ROOT_USER=civix-$(openssl rand -hex 4)"; else echo "$key=$(rand)"; fi
  else
    echo "$line"
  fi
done < .env.example
