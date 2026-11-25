#!/usr/bin/env bash
# Creates deploy/.env with fresh random secrets. Run once on the server:
#   ./init-env.sh <public-ip>        e.g. ./init-env.sh 129.146.10.20
# The site will be https://129-146-10-20.sslip.io (sslip.io is a free DNS name for any IP).
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  echo ".env already exists; not overwriting it (the database password and keys must stay the same)." >&2
  exit 1
fi
if [ $# -ne 1 ] || ! [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "usage: $0 <public-ipv4>" >&2
  exit 1
fi

umask 077 # only this user can read the file
cat > .env <<ENV
SITE_ADDRESS=${1//./-}.sslip.io
DB_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET_BASE64=$(openssl rand -base64 48)
TOKEN_KEK_BASE64=$(openssl rand -base64 32)
GOOGLE_CLIENT_ID=
ENV
echo "Created deploy/.env. Your site: https://${1//./-}.sslip.io"
echo "Back up this file somewhere safe: without TOKEN_KEK_BASE64, encrypted data can't be read."
