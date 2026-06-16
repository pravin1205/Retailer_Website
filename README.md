# Marketly — Multi-Tenant Retail Commerce

A multi-tenant retail storefront built with TanStack Start, React 19, Tailwind CSS v4, and shadcn/ui.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | [TanStack Start](https://tanstack.com/start) (SSR + file-based routing) |
| UI Library | React 19 |
| Styling | Tailwind CSS v4 |
| Component Library | shadcn/ui (New York style) |
| State Management | Zustand |
| Server State | TanStack Query v5 |
| Forms | React Hook Form + Zod |
| Charts | Recharts |
| Animations | Framer Motion |
| Package Manager | Bun |
| Build Tool | Vite 8 |

---

## Prerequisites

Install the following before proceeding. Verify each with the version command shown.

### 1. Node.js (v18 or higher)

Download from https://nodejs.org

```bash
node --version
# Expected: v18.x.x or higher
```

### 2. Bun (v1.0 or higher)

**Windows (PowerShell):**
```powershell
irm https://bun.sh/install.ps1 | iex
```

**macOS / Linux:**
```bash
curl -fsSL https://bun.sh/install | bash
```

After installation, restart your terminal, then verify:
```bash
bun --version
# Expected: 1.x.x
```

> If `bun` is still not recognized after restarting, add `C:\Users\<YourUsername>\.bun\bin` to your system PATH manually.

---

## Project Setup

Follow these steps in order.

### Step 1 — Clone or copy the project

If you are cloning from a repository:
```bash
git clone <repository-url>
cd Retailer_Website
```

If you already have the project folder, open a terminal and navigate into it:
```bash
cd path/to/Retailer_Website
```

### Step 2 — Install dependencies

```bash
bun install
```

This installs all ~493 packages defined in `package.json` including:
- All Radix UI primitives
- TanStack Router, Start, and Query
- Tailwind CSS v4 + shadcn/ui
- Zustand, Recharts, Framer Motion, and more

> The first install may take 1–3 minutes depending on your network speed.

### Step 3 — Start the development server

```bash
bun run dev
```

The app will be available at:
```
http://localhost:8080
```

> If port 8080 is occupied, Vite will automatically try 8081, 8082, etc. Check the terminal output for the exact URL.

---

## Available Scripts

| Command | Description |
|---|---|
| `bun run dev` | Start the development server with hot reload |
| `bun run build` | Build for production |
| `bun run build:dev` | Build in development mode |
| `bun run preview` | Preview the production build locally |
| `bun run lint` | Run ESLint across the project |
| `bun run format` | Auto-format all files with Prettier |

---

## Project Structure

```
Retailer_Website/
├── public/                     # Static assets served as-is
│   ├── icon-192.svg
│   ├── icon-512.svg
│   ├── manifest.webmanifest    # PWA manifest
│   └── sw.js                   # Service worker
├── src/
│   ├── components/
│   │   ├── admin/              # Admin panel components
│   │   │   ├── AdminSidebar.tsx
│   │   │   ├── AdminTopbar.tsx
│   │   │   ├── EmptyState.tsx
│   │   │   ├── KpiCard.tsx
│   │   │   ├── LazyCharts.tsx
│   │   │   ├── OrdersTable.tsx
│   │   │   ├── OrderStatusBadge.tsx
│   │   │   ├── ProductForm.tsx
│   │   │   └── RevenueChart.tsx
│   │   ├── layout/             # Shared layout components
│   │   │   ├── MobileBottomNav.tsx
│   │   │   └── TenantHeader.tsx
│   │   ├── motion/             # Animation wrappers
│   │   │   └── PageTransition.tsx
│   │   ├── notifications/      # Notification UI
│   │   │   ├── NotificationBell.tsx
│   │   │   └── NotificationList.tsx
│   │   ├── pwa/                # Progressive Web App support
│   │   │   └── PWAProvider.tsx
│   │   ├── storefront/         # Customer-facing components
│   │   │   ├── ProductCard.tsx
│   │   │   └── StoreCard.tsx
│   │   ├── superadmin/         # Super-admin panel components
│   │   │   ├── SuperAdminSidebar.tsx
│   │   │   └── SuperAdminTopbar.tsx
│   │   └── ui/                 # shadcn/ui primitives (46 components)
│   ├── hooks/                  # Custom React hooks
│   │   ├── use-media.ts
│   │   ├── use-mobile.tsx
│   │   └── use-tenant-orders.ts
│   ├── lib/
│   │   ├── api/                # API function definitions
│   │   │   ├── example.functions.ts
│   │   │   └── index.ts
│   │   ├── mock/               # Mock/seed data
│   │   │   ├── products.ts
│   │   │   ├── reviews.ts
│   │   │   ├── seedOrders.ts
│   │   │   └── tenants.ts
│   │   └── types/              # Shared TypeScript types
│   │       └── index.ts
│   ├── routes/                 # File-based routes (TanStack Router)
│   │   ├── __root.tsx          # Root layout + providers
│   │   ├── index.tsx           # Home page — store directory
│   │   ├── stores.index.tsx    # All stores listing
│   │   ├── admin.tsx           # Super-admin layout
│   │   ├── admin.analytics.tsx
│   │   ├── admin.index.tsx
│   │   ├── admin.notifications.tsx
│   │   ├── admin.orders.tsx
│   │   ├── admin.tenants.tsx
│   │   ├── auth.login.tsx
│   │   ├── auth.signup.tsx
│   │   ├── s.$tenant.tsx               # Tenant storefront layout
│   │   ├── s.$tenant.index.tsx         # Tenant home page
│   │   ├── s.$tenant.account.tsx
│   │   ├── s.$tenant.cart.tsx
│   │   ├── s.$tenant.checkout.tsx
│   │   ├── s.$tenant.search.tsx
│   │   ├── s.$tenant.wishlist.tsx
│   │   ├── s.$tenant.c.$category.tsx   # Category browse
│   │   ├── s.$tenant.p.$productId.tsx  # Product detail
│   │   ├── s.$tenant.orders.$id.tsx
│   │   ├── s.$tenant.orders.index.tsx
│   │   ├── s.$tenant.admin.tsx         # Tenant admin layout
│   │   ├── s.$tenant.admin.index.tsx
│   │   ├── s.$tenant.admin.categories.tsx
│   │   ├── s.$tenant.admin.coupons.tsx
│   │   ├── s.$tenant.admin.customers.tsx
│   │   ├── s.$tenant.admin.notifications.tsx
│   │   ├── s.$tenant.admin.orders.$id.tsx
│   │   ├── s.$tenant.admin.orders.index.tsx
│   │   ├── s.$tenant.admin.products.$id.tsx
│   │   ├── s.$tenant.admin.products.index.tsx
│   │   ├── s.$tenant.admin.products.new.tsx
│   │   ├── s.$tenant.admin.reports.tsx
│   │   └── s.$tenant.admin.settings.tsx
│   ├── stores/                 # Zustand global stores
│   │   ├── admin.ts
│   │   └── index.ts
│   ├── router.tsx              # Router + QueryClient factory
│   ├── routeTree.gen.ts        # Auto-generated by TanStack Router (do not edit)
│   ├── server.ts               # SSR server entry with error handling
│   ├── start.ts                # TanStack Start instance + middleware
│   └── styles.css              # Global styles + Tailwind v4 theme
├── .gitignore
├── .prettierignore
├── .prettierrc
├── bunfig.toml                 # Bun install config (24h package age guard)
├── components.json             # shadcn/ui config
├── eslint.config.js
├── package.json
├── tsconfig.json
└── vite.config.ts              # Vite config (TanStack Start + React + Tailwind v4)
```

---

## Routing Convention

This project uses **TanStack Router** with file-based routing. Route filenames map directly to URL paths:

| File | URL |
|---|---|
| `routes/index.tsx` | `/` |
| `routes/stores.index.tsx` | `/stores` |
| `routes/auth.login.tsx` | `/auth/login` |
| `routes/admin.tsx` | `/admin` (layout) |
| `routes/s.$tenant.tsx` | `/s/:tenant` (layout) |
| `routes/s.$tenant.index.tsx` | `/s/:tenant` |
| `routes/s.$tenant.cart.tsx` | `/s/:tenant/cart` |
| `routes/s.$tenant.admin.index.tsx` | `/s/:tenant/admin` |

> `routeTree.gen.ts` is **auto-generated** by the TanStack Router Vite plugin on every dev/build run. Do not edit it manually.

---

## Path Aliases

The `@/` alias maps to `src/`. Use it for all internal imports:

```ts
import { Button } from "@/components/ui/button";
import { useMediaQuery } from "@/hooks/use-media";
import type { Product } from "@/lib/types";
```

---

## Adding shadcn/ui Components

The project is pre-configured with shadcn/ui (New York style, Tailwind v4). To add a new component:

```bash
bunx shadcn@latest add <component-name>
```

Example:
```bash
bunx shadcn@latest add data-table
```

Components are placed in `src/components/ui/`.

---

## Troubleshooting

### `bun` not recognized after install
Restart your terminal. On Windows, also check that `C:\Users\<YourName>\.bun\bin` is in your system PATH (`System Properties > Environment Variables`).

### Port already in use
Vite will automatically try the next available port (8080 → 8081 → 8082 ...). Check the terminal output for the actual URL.

### `routeTree.gen.ts` errors
This file is auto-generated. Run `bun run dev` once and the file will be regenerated correctly. Never edit it manually.

### Type errors on fresh clone
Run `bun install` first to ensure all `@types/*` packages are present, then restart your editor's TypeScript server (`Ctrl+Shift+P` → "TypeScript: Restart TS Server" in VS Code).

### `vite-tsconfig-paths` warning
You may see this warning on startup:
```
The plugin "vite-tsconfig-paths" is detected. Vite now supports tsconfig paths natively...
```
This is harmless. The plugin provides tsconfig path aliases and does not affect functionality.
