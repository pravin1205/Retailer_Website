# Access Control Architecture

**Platform:** Marketly — Multi-Tenant Retail SaaS  
**Version:** 2.0  
**Date:** 2026-06-17  
**Status:** Authoritative Architecture Document

---

## 1. Overview

Marketly is a multi-tenant SaaS platform with three application surfaces, each serving a distinct audience. Access control is centralized through a single Identity Service and enforced at the API Gateway before any downstream service receives a request.

The three surfaces are:

| Surface | Hostname | Audience |
|---|---|---|
| Super Admin Portal | `admin.marketly.com` | Platform operators |
| Seller Portal | `seller.marketly.com` | Store owners and staff |
| Customer Storefront | `{slug}.marketly.com` | End shoppers |

All surfaces share the same backend API at `api.marketly.com:8080` (API Gateway). Separation is achieved via **tenant scoping in the JWT** and **role-based guards at the service layer**, not by separate auth systems.

---

## 2. Authentication Model

### 2.1 Centralized Identity Service

Authentication is handled exclusively by `identity-service` (port 8081). There is **one** auth system for the entire platform. Sellers, customers, and super admins all authenticate through:

```
POST /api/v1/auth/login
POST /api/v1/auth/register
POST /api/v1/auth/otp/send
POST /api/v1/auth/otp/verify
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

The surface a user is on is communicated to the identity service via the `tenantSlug` field in the login/register request body. The identity service uses this to scope the resulting JWT to the correct tenant.

### 2.2 OTP-First Authentication

Customer and seller registration uses a mobile OTP flow. Password-based login is also supported. Both flows produce the same JWT structure.

### 2.3 Token Pair

On successful authentication, the identity service returns:

```json
{
  "accessToken":  "<JWT — 15 minutes>",
  "refreshToken": "<opaque UUID — 7 days, rotated>",
  "tokenType":    "Bearer",
  "expiresIn":    900
}
```

- **Access token** — short-lived JWT, validated stateless at the gateway.
- **Refresh token** — stored as a SHA-256 hash in `identity.refresh_tokens`. Rotation on every use. Revoked on password change and logout.

### 2.4 Password Security

- bcrypt strength 12 for all password hashes.
- Soft delete everywhere — deactivated users cannot log in (`is_active = false`).

---

## 3. JWT Design

### 3.1 Structure

All access tokens are HS256 JWTs signed with the shared secret (`JWT_SECRET`).

**Standard claims:**

```json
{
  "sub":       "<userId>",
  "iat":       1750000000,
  "exp":       1750000900
}
```

**Custom claims injected by identity-service:**

```json
{
  "email":     "user@example.com",
  "roles":     ["TENANT_OWNER", "CUSTOMER"],
  "tenant_id": "a3f8...uuid"
}
```

### 3.2 Role Scoping Rules

The roles embedded in the JWT are **filtered by the `tenant_id`** at issue time:

```java
// JwtService.generateAccessToken(user, tenantId)
List<String> roles = user.getTenantRoles().stream()
    .filter(utr -> tenantId == null || tenantId.equals(utr.getTenantId()))
    .filter(utr -> utr.getDeletedAt() == null)
    .map(utr -> utr.getRole().getName())
    .distinct()
    .toList();
```

This means:
- A user logging into Seller Portal (`tenantSlug = "freshmart"`) gets a token with `roles: ["TENANT_OWNER"]` and `tenant_id: "<freshmart-uuid>"`.
- The same user logging into a different store as a customer gets `roles: ["CUSTOMER"]` and the other store's `tenant_id`.
- `SUPER_ADMIN` users have `tenant_id` omitted from the token (null). The roles array contains `["SUPER_ADMIN"]`.

### 3.3 Token Variants by Surface

**Super Admin Portal login:**
```json
{
  "sub":    "<adminUserId>",
  "email":  "admin@marketly.com",
  "roles":  ["SUPER_ADMIN"]
  // tenant_id intentionally absent
}
```

**Seller Portal login (tenantSlug provided):**
```json
{
  "sub":       "<sellerUserId>",
  "email":     "owner@freshmart.com",
  "roles":     ["TENANT_OWNER"],
  "tenant_id": "<freshmart-uuid>"
}
```

**Customer Storefront login (tenantSlug provided):**
```json
{
  "sub":       "<customerUserId>",
  "email":     "buyer@gmail.com",
  "roles":     ["CUSTOMER"],
  "tenant_id": "<freshmart-uuid>"
}
```

**Multi-role user (same person is both seller and customer of a store):**
```json
{
  "sub":       "<userId>",
  "email":     "power@user.com",
  "roles":     ["TENANT_OWNER", "CUSTOMER"],
  "tenant_id": "<freshmart-uuid>"
}
```

### 3.4 Phone Claim (Planned Extension)

The business requirement specifies `mobile` in the JWT. The current `User` entity and `RegisterRequest` use the field name `phone`. The JWT currently contains `email` only. Adding `phone` requires a one-line change to `JwtService.generateAccessToken`:

```java
if (user.getPhone() != null) {
    builder.claim("phone", user.getPhone());
}
```

No schema change needed — `phone` column already exists on `identity.users`.

---

## 4. Authorization Model

### 4.1 Gateway-Level: Token Validation

`JwtAuthFilter` in the API Gateway performs:
1. Extract `Bearer` token from `Authorization` header.
2. Validate signature and expiry with the shared `JWT_SECRET`.
3. On success — inject trusted internal headers:
   - `X-User-ID: <userId>`
   - `X-Roles: TENANT_OWNER,CUSTOMER`
   - `X-Tenant-ID: <tenantId>` (empty string for SUPER_ADMIN)
4. Downstream services **trust these headers without re-validating the JWT**.

Public paths bypass the filter entirely:
```
/api/v1/auth/**
/api/v1/tenants          (GET — public listing)
/api/v1/products         (GET — public storefront)
/api/v1/categories       (GET — public storefront)
/actuator/health
```

### 4.2 Service-Level: Role Enforcement

Individual services read the `X-Roles` header and enforce role-based permissions. Examples:

| Endpoint | Required Role | Notes |
|---|---|---|
| `PATCH /api/v1/tenants/{slug}/approve` | `SUPER_ADMIN` | Admin-only approval action |
| `PATCH /api/v1/tenants/{slug}/review` | `SUPER_ADMIN` | Move to UNDER_REVIEW |
| `POST /api/v1/tenants` | `TENANT_OWNER` or any authenticated user | Create own store |
| `POST /api/v1/tenants/{slug}/kyc` | `TENANT_OWNER` (owner check) | Only owner can submit KYC |
| `GET /api/v1/analytics/dashboard` | `TENANT_OWNER`, `STORE_MANAGER`, `SUPER_ADMIN` | Tenant-scoped analytics |
| `GET /api/v1/customers` | `SUPER_ADMIN`, `TENANT_OWNER`, `STORE_MANAGER` | Customer list — scoped by tenant |
| `POST /api/v1/orders` | `CUSTOMER` | Place order |
| `PATCH /api/v1/orders/{id}/status` | `TENANT_OWNER`, `STORE_MANAGER`, `STORE_STAFF` | Update order status |

### 4.3 Tenant Isolation: Hibernate Filter

All business entities that extend `TenantAwareEntity` carry a `tenant_id` column. A Hibernate `@Filter` named `tenantFilter` is applied on every repository query, automatically scoping all reads to the tenant in `TenantContext` (thread-local, set from `X-Tenant-ID` header).

```java
// TenantAwareEntity.java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
```

`SUPER_ADMIN` bypasses the filter — when `X-Tenant-ID` is empty, the filter is not activated, allowing cross-tenant queries.

### 4.4 Customer-Tenant Isolation

The `customer.customers` table has a `UNIQUE(tenant_id, user_id)` constraint. A customer profile is created per store visited. This allows:
- Same user → `customer.customers` row for FreshMart
- Same user → `customer.customers` row for OrganicBasket
- Each profile has independent loyalty points, tier, and order history within that store.

---

## 5. Surface-Specific Access Rules

### 5.1 Super Admin Portal (`admin.marketly.com`)

- Requires `SUPER_ADMIN` role.
- JWT has no `tenant_id` — gateway injects empty `X-Tenant-ID`.
- Tenant filter NOT activated — full cross-tenant visibility.
- Super Admin can also submit a `tenantSlug` at login to obtain a tenant-scoped token for impersonation/troubleshooting.

### 5.2 Seller Portal (`seller.marketly.com`)

- Requires `TENANT_OWNER`, `STORE_MANAGER`, or `STORE_STAFF` role.
- JWT always carries `tenant_id` of the seller's store.
- All data reads are automatically scoped to that tenant.
- Seller cannot access `/api/v1/tenants/{otherSlug}/approve` — blocked by role check.

### 5.3 Customer Storefront (`{slug}.marketly.com`)

- Requires `CUSTOMER` role (or anonymous for public reads).
- JWT carries `tenant_id` of the specific store.
- `GET /api/v1/products`, `/api/v1/categories` are public but the gateway (or reverse proxy) injects `X-Tenant-ID` from the subdomain, so only that store's products are returned.
- Customer cannot access other stores' data even if they forge a different `tenant_id` in a request — the JWT's `tenant_id` is the enforced boundary.

---

## 6. Multi-Role Session Handling

A user can hold multiple roles across multiple tenants. The session model supports this:

| Scenario | How it works |
|---|---|
| Seller only | Single `user_tenant_roles` row: `(userId, freshMartId, TENANT_OWNER)` |
| Customer only | Single row: `(userId, freshMartId, CUSTOMER)` |
| Seller + Customer of own store | Two rows: `TENANT_OWNER` + `CUSTOMER` for same tenant |
| Customer of two stores | Two rows: `CUSTOMER` at FreshMart + `CUSTOMER` at OrganicBasket |
| Super Admin visiting a store | SUPER_ADMIN has a NULL-tenant row; logs in with tenantSlug to get scoped token |

The frontend must request a **new token per surface**. Switching from Seller Portal to Customer Storefront requires a new `POST /api/v1/auth/login` or `POST /api/v1/auth/refresh` call with the target `tenantSlug`.

---

## 7. Audit & Security

- Every auth event (login, logout, password change, registration) is written to `identity.audit_log` — immutable, no deletes.
- Rate limiting at the API Gateway via Redis.
- All requests carry `X-Correlation-ID` for distributed tracing via Zipkin.
- Soft delete on all business entities — suspension sets `is_active = false` or `status = SUSPENDED`, no data loss.
- Refresh tokens are stored as SHA-256 hashes — raw tokens are never persisted.
- Password change revokes all active refresh tokens for the user.
