# URL Access Model

**Platform:** Marketly — Multi-Tenant Retail SaaS  
**Version:** 2.0  
**Date:** 2026-06-17  
**Status:** Authoritative Architecture Document

---

## 1. URL Structure Overview

The platform exposes three distinct hostname patterns, each serving a different audience:

| Hostname Pattern | Surface | Audience |
|---|---|---|
| `admin.marketly.com` | Super Admin Portal | Platform operators |
| `seller.marketly.com` | Seller Portal | Store owners and staff |
| `{slug}.marketly.com` | Customer Storefront | Shoppers |

All surfaces share a **single API Gateway** at `api.marketly.com` (port 8080). Frontend apps on different subdomains make requests to the same backend.

Alternatively, path-based routing is supported for storefronts:
```
marketly.com/store/freshmart
```

Both subdomain and path variants are supported. Subdomain is the primary model (preferred by sellers for branding). Path-based is used as fallback for environments where wildcard DNS is unavailable.

---

## 2. DNS and Reverse Proxy Layout

```
                    ┌─────────────────────────────────────┐
                    │          DNS / CDN Layer            │
                    │   *.marketly.com → Load Balancer    │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
                    │         nginx / reverse proxy       │
                    │                                     │
                    │  admin.marketly.com  → admin-app    │
                    │  seller.marketly.com → seller-app   │
                    │  *.marketly.com      → storefront   │
                    │  (all)               → api-gateway  │
                    └─────────────────────────────────────┘
```

A wildcard DNS record `*.marketly.com` covers all tenant subdomains. When a new seller is approved, the `tenant_domains` table already contains the entry `freshmart.marketly.com` — no DNS change needed.

---

## 3. URL Routing by Surface

### 3.1 Admin Portal: `admin.marketly.com`

```
admin.marketly.com/                   → Dashboard (all tenants KPI)
admin.marketly.com/tenants            → Tenant list (all statuses)
admin.marketly.com/tenants/pending    → Onboarding queue
admin.marketly.com/tenants/{slug}     → Tenant detail + KYC review
admin.marketly.com/sellers            → All sellers
admin.marketly.com/customers          → Platform-wide customers
admin.marketly.com/analytics          → Platform analytics
admin.marketly.com/subscriptions      → Subscription management
```

**Access rule:** Requires `SUPER_ADMIN` role. Any other role → redirect to login.

**Tenant context:** None. JWT has no `tenant_id`. All API calls go through without a tenant scope — the backend returns cross-tenant data.

### 3.2 Seller Portal: `seller.marketly.com`

```
seller.marketly.com/                          → Landing / Login / Register
seller.marketly.com/onboarding/seller         → Seller onboarding wizard (Steps 1-7)
seller.marketly.com/onboarding/seller/status  → Application status page
seller.marketly.com/s/{slug}/admin            → Store dashboard (after approval)
seller.marketly.com/s/{slug}/products         → Product management
seller.marketly.com/s/{slug}/categories       → Category management
seller.marketly.com/s/{slug}/orders           → Order management
seller.marketly.com/s/{slug}/customers        → Customer list for this store
seller.marketly.com/s/{slug}/analytics        → Store analytics
seller.marketly.com/s/{slug}/settings         → Store settings
seller.marketly.com/s/{slug}/staff            → Staff management
```

**Access rule:** Requires `TENANT_OWNER`, `STORE_MANAGER`, or `STORE_STAFF` role AND `tenant_id` in JWT must match `{slug}`.

**Tenant context:** Resolved from `{slug}` in the URL path. Login request includes `tenantSlug = slug` → JWT is scoped to that tenant.

### 3.3 Customer Storefront: `{slug}.marketly.com`

```
freshmart.marketly.com/                 → Store homepage (products featured)
freshmart.marketly.com/products         → Product listing
freshmart.marketly.com/products/{id}    → Product detail
freshmart.marketly.com/categories       → Category browser
freshmart.marketly.com/categories/{id}  → Category detail (filtered products)
freshmart.marketly.com/cart             → Shopping cart
freshmart.marketly.com/checkout         → Checkout
freshmart.marketly.com/orders           → Customer order history
freshmart.marketly.com/orders/{id}      → Order detail
freshmart.marketly.com/profile          → Customer profile
freshmart.marketly.com/auth/login       → Login page (store-branded)
freshmart.marketly.com/auth/signup      → Register page (store-branded)
freshmart.marketly.com/onboarding       → Customer onboarding wizard
```

**Access rule:**
- Public routes (product browse): No auth required. `X-Tenant-ID` injected from subdomain.
- Authenticated routes (cart, orders, profile): Requires `CUSTOMER` role + `tenant_id` matching the store.

**Tenant context:** Resolved from subdomain at page load.

---

## 4. Tenant Resolution

Tenant resolution is the process of converting a hostname or URL slug into a `tenantId` UUID that can be injected into API requests.

### 4.1 Resolution Chain

```
1. Incoming request: GET /api/v1/products
   Host: freshmart.marketly.com

2. Nginx extracts Host header → passes X-Forwarded-Host: freshmart.marketly.com

3. API Gateway (TenantResolutionFilter):
   a. Extract slug from Host header: "freshmart"
   b. Check Redis cache: tenant-slug:freshmart → <freshmart-uuid>
   c. Cache miss → call tenant-service: GET /api/v1/tenants/freshmart (internal)
   d. Cache result in Redis: tenant-slug:freshmart → <freshmart-uuid> (TTL 30 min)
   e. Inject header: X-Tenant-ID: <freshmart-uuid>

4. Product-service receives request:
   a. Reads X-Tenant-ID
   b. Sets TenantContext.set(<freshmart-uuid>)
   c. Activates Hibernate tenantFilter
   d. All queries WHERE tenant_id = <freshmart-uuid>
```

### 4.2 Resolution Modes

| Request source | Slug source | Resolution mechanism |
|---|---|---|
| Unauthenticated storefront | Subdomain `freshmart.marketly.com` | Gateway extracts from Host header |
| Authenticated customer | JWT `tenant_id` claim | Gateway extracts from validated JWT |
| Authenticated seller | JWT `tenant_id` claim + URL `{slug}` param | Both must match; JWT takes precedence |
| Super Admin | No tenant | No resolution; filter not activated |
| Path-based: `/store/freshmart` | URL path parameter | Gateway extracts from path |

### 4.3 Conflict Resolution

If the `X-Tenant-ID` from the subdomain **differs** from the `tenant_id` in the JWT, the JWT wins. The JWT was cryptographically issued and cannot be forged. A request to `organicbasket.marketly.com` with a FreshMart-scoped JWT returns FreshMart data, not OrganicBasket data. This is expected behavior for cross-store links.

### 4.4 Redis Cache Structure

```
tenant-slug:{slug}     → UUID string (TTL 1800s / 30 min)
tenant-domain:{domain} → UUID string (TTL 1800s / 30 min)
tenant-config:{slug}   → Tenant JSON blob (TTL 1800s / 30 min)
```

The `@Cacheable(value = "tenant-config", key = "#a0")` annotation on `TenantService.getBySlug()` already manages this.

---

## 5. Role Validation at URL Level

Beyond token validation at the gateway, the frontend performs surface-level role checks at the route level. These are pre-emptive UX checks — the backend enforces them again at the service layer.

### 5.1 Admin Portal Route Guard

```typescript
// Route guard pseudocode
if (!user.roles.includes("SUPER_ADMIN")) {
  redirect("/auth/login?surface=admin");
}
```

### 5.2 Seller Portal Route Guard

```typescript
// After login
const sellerRoles = ["TENANT_OWNER", "STORE_MANAGER", "STORE_STAFF"];
if (!user.roles.some(r => sellerRoles.includes(r))) {
  redirect("/auth/login?surface=seller");
}
// Also validate JWT tenant_id matches the store in the URL
if (user.tenantId !== resolvedTenantId) {
  redirect("/unauthorized");
}
```

### 5.3 Storefront Route Guard (Protected Routes)

```typescript
// Only for authenticated routes (cart, checkout, profile)
if (!user.roles.includes("CUSTOMER")) {
  redirect("/auth/login?tenantSlug=freshmart");
}
// Public routes (products, categories) — no guard
```

---

## 6. URL Generation on Seller Approval

When a seller is approved:

1. `tenant.tenants` → `status = ACTIVE`.
2. `tenant.tenant_domains` record `freshmart.marketly.com` already exists (created in `TenantService.createTenant()`).
3. The Redis cache for `tenant-slug:freshmart` is populated on first access.
4. The seller receives an email: "Your store is live at `https://freshmart.marketly.com`."
5. The URL is immediately accessible — no DNS provisioning needed because the wildcard `*.marketly.com` DNS is already in place.

**Custom domain support (future):**

The `tenant_domains` table supports multiple domain entries per tenant. A seller can add `freshmart.in` as a custom domain by inserting a row with `is_verified = false`. Verification is done via DNS TXT record. Once verified, the CDN routes `freshmart.in` to the same storefront.

---

## 7. Path-Based URL Variant

For environments where wildcard subdomains are not available (some CDNs, some localhost configs), path-based routing is supported:

```
marketly.com/store/freshmart/          → FreshMart storefront homepage
marketly.com/store/freshmart/products  → FreshMart product listing
marketly.com/store/freshmart/cart      → Cart (requires auth)
```

Tenant resolution in this mode:

```
1. Nginx rewrites: /store/{slug}/* → / (passes X-Store-Slug: {slug})
2. Gateway reads X-Store-Slug header and resolves to tenantId.
3. Same injection of X-Tenant-ID downstream.
```

---

## 8. Forbidden Access Scenarios

| Scenario | Outcome |
|---|---|
| Customer visits `admin.marketly.com` | Redirected to login; `SUPER_ADMIN` role check fails → 403 |
| Seller visits `organicbasket.marketly.com` with FreshMart JWT | Returns FreshMart data (JWT tenant scope wins) |
| Customer tries `GET /api/v1/customers` (admin endpoint) | 403 Forbidden — role check in customer-service |
| Unauthenticated user visits `GET /api/v1/cart` | 401 Unauthorized — gateway blocks |
| SUPER_ADMIN visits any storefront | Allowed — SUPER_ADMIN bypass in role checks |
| Seller tries to access `otherstore` admin | 403 — `tenant_id` in JWT does not match path slug |

---

## 9. API Gateway: Summary of Filters

The API Gateway applies filters in this order:

```
1. CorrelationIdFilter     → inject X-Correlation-ID on every request
2. TenantResolutionFilter  → extract slug from Host/path → inject X-Tenant-ID (NEW — to be implemented)
3. JwtAuthFilter           → validate Bearer token → inject X-User-ID, X-Roles, X-Tenant-ID (from JWT)
4. RateLimitFilter         → Redis-backed rate limiting per IP / per user
5. Route matching          → forward to correct microservice
```

> **Implementation note:** `TenantResolutionFilter` is currently a planned addition. The current `JwtAuthFilter` only reads `tenant_id` from the JWT. The new filter must resolve the tenant from the subdomain for unauthenticated requests (public product browsing). This is Phase 2 work.
