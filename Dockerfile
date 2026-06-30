# ─────────────────────────────────────────────────────────────────────────────
# Marketly Frontend — React SPA (served by Node.js static server)
#
# Build:  docker build -t marketly/frontend .
# Run:    docker run -p 3000:3000 marketly/frontend
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Install dependencies ────────────────────────────────────────────
FROM node:22-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --ignore-scripts

# ── Stage 2: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

# ── Stage 3: Production runtime ──────────────────────────────────────────────
FROM node:22-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

# Install serve to host the static Vite build
RUN npm install -g serve

# Copy the Vite build output
COPY --from=builder /app/dist ./dist

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 3000

# Serve the static build on port 3000
CMD ["serve", "-s", "dist", "-l", "3000"]
