#!/usr/bin/env bash
# For small servers (like Google's 1 GB e2-micro) that can't build the images themselves:
# build them here, copy them over SSH, and restart the app on the server.
#
#   ./ship-images.sh ubuntu@<server-ip>
#
# The server needs the repo cloned at ~/returnmate and deploy/.env created (./init-env.sh).
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -ne 1 ]; then
  echo "usage: $0 <user@server>" >&2
  exit 1
fi
SERVER="$1"
# Must match the server's CPU. The e2-micro is x86 (amd64); an Oracle A1 would be linux/arm64.
PLATFORM="${PLATFORM:-linux/amd64}"

echo "==> Building images for $PLATFORM"
# Placeholders so compose.yaml's "run ./init-env.sh first" checks pass. Secrets aren't
# baked into images; the server's own .env supplies them when the containers start.
DOCKER_DEFAULT_PLATFORM="$PLATFORM" \
  DB_PASSWORD=build JWT_SECRET_BASE64=build TOKEN_KEK_BASE64=build SITE_ADDRESS=build \
  docker compose --env-file /dev/null build

echo "==> Copying images to $SERVER (compressed)"
docker save returnmate-api returnmate-web | gzip | ssh "$SERVER" 'gunzip | docker load'

echo "==> Restarting the app on $SERVER"
ssh "$SERVER" 'cd ~/returnmate && git pull --ff-only && cd deploy && docker compose up -d --no-build && docker image prune -f'
echo "Done. Logs: ssh $SERVER 'cd ~/returnmate/deploy && docker compose logs -f api'"
