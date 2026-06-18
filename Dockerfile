# ─────────────────────────────────────────────────────────────────────────────
# Marketly Frontend — TanStack Start (Node.js SSR)
#
# Build:  docker build -t marketly/frontend .
# Run:    docker run -p 3000:3000 marketly/frontend
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Install dependencies ────────────────────────────────────────────
FROM node:22-alpine AS deps

WORKDIR /app

# Copy manifests first for layer caching
COPY package.json package-lock.json ./

RUN npm ci --ignore-scripts

# ── Stage 2: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# VITE_API_URL is baked into the client bundle at build time.
# The SSR server proxies /api/v1 to the gateway at runtime via API_GATEWAY_URL.
ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

# ── Stage 3: Production runtime ──────────────────────────────────────────────
FROM node:22-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

# Only copy what the SSR server needs
COPY --from=builder /app/.output ./.output
COPY --from=builder /app/package.json ./package.json

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 3000

# TanStack Start / Nitro output entrypoint
CMD ["node", ".output/server/index.mjs"]
