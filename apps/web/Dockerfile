# syntax=docker/dockerfile:1.7
# CIVIX web (Next.js standalone). Build context: apps/web

ARG NODE_IMAGE=node:22-alpine

# ---- deps: install exactly what package-lock.json pins ----------------------
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
RUN apk add --no-cache libc6-compat
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# ---- build -------------------------------------------------------------------
FROM ${NODE_IMAGE} AS build
WORKDIR /app
# Rewrites are resolved at build time in standalone mode.
ARG API_PROXY_TARGET=http://api:8080
ENV API_PROXY_TARGET=${API_PROXY_TARGET} \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---- runtime -----------------------------------------------------------------
FROM ${NODE_IMAGE} AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN addgroup -S -g 1001 civix && adduser -S -u 1001 -G civix civix
COPY --from=build --chown=civix:civix /app/.next/standalone ./
COPY --from=build --chown=civix:civix /app/.next/static ./.next/static
COPY --from=build --chown=civix:civix /app/public ./public
USER civix
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/healthz || exit 1
CMD ["node", "server.js"]
