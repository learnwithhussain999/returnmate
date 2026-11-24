#!/usr/bin/env bash
# Start the backend locally with the keys from .env.local.
# Creates .env.local with fresh random keys the first time. That file is gitignored: never commit it.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env.local ]; then
  echo "Creating .env.local with new random keys..."
  {
    echo "export JWT_SECRET_BASE64=$(openssl rand -base64 48)"
    echo "export TOKEN_KEK_BASE64=$(openssl rand -base64 32)"
  } > .env.local
fi

source .env.local
if [ -z "${JWT_SECRET_BASE64:-}" ] || [ -z "${TOKEN_KEK_BASE64:-}" ]; then
  echo "JWT_SECRET_BASE64 or TOKEN_KEK_BASE64 is empty in .env.local" >&2
  exit 1
fi

exec ./mvnw spring-boot:run "$@"
