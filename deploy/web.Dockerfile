# syntax=docker/dockerfile:1
# Builds the React app, then serves it with Caddy. Build context: the repository root.

FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/web/package.json frontend/web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/web/ ./
RUN npm run build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
