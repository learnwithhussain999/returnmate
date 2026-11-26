# syntax=docker/dockerfile:1
# Builds the React app, then serves it with Caddy. Build context: the repository root.

# The build output is plain HTML/JS, the same on every CPU, so build natively
# ($BUILDPLATFORM) instead of emulating the server's CPU. Only the Caddy stage must match it.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY frontend/web/package.json frontend/web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/web/ ./
RUN npm run build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
