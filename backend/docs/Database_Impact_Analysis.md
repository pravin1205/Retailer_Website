# Database Impact Analysis

**Platform:** Marketly — Multi-Tenant Retail SaaS  
**Version:** 2.0  
**Date:** 2026-06-17  
**Status:** Authoritative Architecture Document

---

## 1. Purpose

This document catalogs every database table relevant to the three-surface access model and seller/customer onboarding. For each table it states:

- Whether it exists today and what shape it is in.
- Whether it can be reused as-is, extended, or must be created new.
- Justification for any new table or column.

**Principle:** Do not create a new table unless absolutely necessary. Prefer extending existing structures.

---

## 2. Current Table Inventory

### 2.1 Schema: `identity`

#### `identity.users`

```sql
id            UUID  PK
email         VARCHAR(320) NOT NULL UNIQUE
phone         VARCHAR(20)
password_hash VARCHAR(255) NOT NULL
is_verified   BOOLEAN DEFAULT false
is_active     BOOLEAN DEFAULT true
last_login_at TIMESTAMPTZ
created_at, updated_at, created_by, updated_by, deleted_at
```

**Assessment: REUSE AS-IS**

- Supports both sellers and customers — any user regardless of role lives here.
- `phone` column exists — supports OTP-based registration.
- `email` is required at the DB level but `RegisterRequest.java` has `@NotBlank @Email` on email. For mobile-only OTP registration (customers who don't give email), this will cause a constraint violation.

**Required change:**
- Make `email` column NULLABLE for users who register via mobile OTP without providing email.
- Alternatively: generate a placeholder email like `+91{phone}@noemail.marketly.com` at registration time and replace it later when email is collected. This avoids a schema migration.

**Recommendation:** Make `email` nullable to be honest about the data model.

```sql
-- Migration required
ALTER TABLE identity.users ALTER COLUMN email DROP NOT NULL;
DROP INDEX IF EXISTS identity.uq_users_email;
CREATE UNIQUE INDEX uq_users_email ON identity.users (email) WHERE email IS NOT NULL AND deleted_at IS NULL;
```

---

#### `identity.roles`

```sql
id          UUID  PK
name        VARCHAR(50) NOT NULL UNIQUE
description VARCHAR(255)
created_at, updated_at, deleted_at
```

**Seeded values:**
- `SUPER_ADMIN`
- `TENANT_OWNER`
- `STORE_MANAGER`
- `STORE_STAFF`
- `CUSTOMER`

**Assessment: REUSE AS-IS**

All required roles are already seeded. No new roles needed for Phase 1.

---

#### `identity.user_tenant_roles`

```sql
id          UUID  PK
user_id     UUID  NOT NULL FK → identity.users(id)
tenant_id   UUID  NOT NULL
role_id     UUID  NOT NULL FK → identity.roles(id)
created_at, updated_at, created_by, updated_by, deleted_at
UNIQUE (user_id, tenant_id, role_id)
```

**Assessment: REUSE AS-IS — this is the core of the entire multi-role model**

This table already supports:
- One user holding multiple roles across multiple tenants.
- A seller having `TENANT_OWNER` at FreshMart and `CUSTOMER` at OrganicBasket.
- A customer having `CUSTOMER` at multiple stores.
- `SUPER_ADMIN` having a platform-wide assignment (tenant_id can be a reserved sentinel UUID like `00000000-0000-0000-0000-000000000000`).

**Required change for SUPER_ADMIN:**
The `tenant_id` column is `NOT NULL`. For `SUPER_ADMIN`, there is no tenant. Options:
1. Use a reserved sentinel UUID (`00000000-0000-0000-0000-000000000001`) representing "platform-wide".
2. Make `tenant_id` nullable for platform-wide roles.

**Recommendation:** Use sentinel UUID. This avoids nullable FK complexity and keeps query patterns uniform. Seed:

```sql
INSERT INTO identity.user_tenant_roles (user_id, tenant_id, role_id)
SELECT '<adminUserId>', '00000000-0000-0000-0000-000000000001',
       (SELECT id FROM identity.roles WHERE name = 'SUPER_ADMIN');
```

The `JwtService` already handles `tenantId == null` — it omits `tenant_id` from the JWT when null. So: when generating the SUPER_ADMIN token, pass `tenantId = null` to `generateAccessToken()` regardless of what sentinel is stored.

---

#### `identity.refresh_tokens`

```sql
id          UUID  PK
user_id     UUID  FK → identity.users(id)
tenant_id   UUID  (nullable)
token_hash  VARCHAR(255) UNIQUE
expires_at  TIMESTAMPTZ
revoked_at  TIMESTAMPTZ
created_at  TIMESTAMPTZ
user_agent  VARCHAR(512)
```

**Assessment: REUSE AS-IS**

- `tenant_id` is already nullable — handles SUPER_ADMIN.
- SHA-256 hashed raw token — secure.
- Rotation-on-use already implemented.

---

#### `identity.audit_log`

```sql
id          UUID PK
user_id     UUID (nullable — pre-auth failures)
tenant_id   UUID (nullable)
event_type  VARCHAR(80)
ip_address  INET
user_agent  VARCHAR(512)
metadata    JSONB
occurred_at TIMESTAMPTZ
```

**Assessment: REUSE AS-IS**

Records every auth event across all surfaces. Surface context can be added to `metadata` JSONB without schema change:
```json
{ "surface": "seller", "tenantSlug": "freshmart" }
```

---

### 2.2 Schema: `tenant`

#### `tenant.tenants`

```sql
id                      UUID  PK
slug                    VARCHAR(100) UNIQUE
name                    VARCHAR(200)
tagline                 VARCHAR(500)
description             TEXT
category                VARCHAR(100)
status                  VARCHAR(30) DEFAULT 'PENDING'
owner_user_id           UUID
subscription_plan       VARCHAR(50) DEFAULT 'FREE'
subscription_expires_at TIMESTAMPTZ
logo_url                VARCHAR(2048)
banner_url              VARCHAR(2048)
accent_color            VARCHAR(30)
onboarding_step         VARCHAR(50) DEFAULT 'MOBILE'  ← added in V4 migration
created_at, updated_at, created_by, updated_by, deleted_at
```

**Assessment: REUSE AS-IS**

- `status` field drives the lifecycle: `DRAFT → PENDING_VERIFICATION → UNDER_REVIEW → ACTIVE / REJECTED / SUSPENDED`.
- `onboarding_step` tracks wizard progress for resume-later.
- `owner_user_id` links to `identity.users.id` — correct FK semantics.

**Minor gap:** The `status` column defaults to `'PENDING'` in the SQL migration but `TenantService.createTenant()` sets it to `'DRAFT'`. The Java entity also defaults to `'DRAFT'`. This is a migration vs. Java discrepancy — low risk since Java wins on insert, but the migration default should be updated for consistency:

```sql
ALTER TABLE tenant.tenants ALTER COLUMN status SET DEFAULT 'DRAFT';
```

---

#### `tenant.tenant_domains`

```sql
id          UUID  PK
tenant_id   UUID  FK → tenant.tenants(id)
domain      VARCHAR(253) UNIQUE
is_primary  BOOLEAN DEFAULT false
is_verified BOOLEAN DEFAULT false
created_at, updated_at, deleted_at
```

**Assessment: REUSE AS-IS**

- Auto-populated with `{slug}.marketly.com` on tenant creation.
- Supports custom domains in the future via additional rows.
- `is_verified` flag supports custom domain DNS verification flow.

---

#### `tenant.tenant_settings`

Used as a key-value store for all flexible tenant configuration (address, KYC docs, branding settings, delivery radius).

**Assessment: REUSE AS-IS**

Current usage — KYC fields stored here:
- `kyc_aadhaar`, `kyc_pan`, `kyc_gst`
- `kyc_document_urls`, `kyc_store_image`
- `kyc_status`, `kyc_review_notes`

Address fields:
- `address_line1`, `address_line2`, `city`, `state`, `pincode`
- `latitude`, `longitude`, `delivery_radius_km`

Branding:
- `logo_emoji`, `banner_gradient`, `tagline`

No new columns needed. The key-value model handles all onboarding fields without migrations.

**Limitation:** KYC data in tenant_settings has no type enforcement. This is acceptable for Phase 1 — type validation happens in `KycRequest` Java DTO before storage.

---

### 2.3 Schema: `customer`

#### `customer.customers`

```sql
id             UUID  PK
tenant_id      UUID  NOT NULL
user_id        UUID  NOT NULL
first_name     VARCHAR(100)
last_name      VARCHAR(100)
phone          VARCHAR(20)
email          VARCHAR(320)
date_of_birth  DATE
loyalty_points INT  DEFAULT 0
tier           VARCHAR(30) DEFAULT 'BRONZE'
total_orders   INT  DEFAULT 0
total_spent    NUMERIC(14,2) DEFAULT 0
created_at, updated_at, created_by, updated_by, deleted_at
UNIQUE (tenant_id, user_id)
```

**Assessment: REUSE AS-IS — perfectly models the multi-store customer**

- `UNIQUE(tenant_id, user_id)` → exactly one profile per customer per store.
- `loyalty_points`, `total_orders`, `total_spent` are all per-store (correct isolation).
- `tier` is per-store (a customer can be GOLD at FreshMart and BRONZE at OrganicBasket).

No schema changes needed.

---

#### `customer.addresses`

```sql
id           UUID  PK
customer_id  UUID  FK → customer.customers(id)
...address fields...
is_default   BOOLEAN
```

**Assessment: REUSE AS-IS**

Addresses are linked to `customer.customers` (which is already tenant-scoped). A customer can have different default addresses per store.

---

### 2.4 Schema: `product`

#### `product.categories`, `product.products`, `product.product_variants`, `product.inventory`

All extend `TenantAwareEntity` — they carry `tenant_id` and are automatically filtered by the Hibernate tenant filter.

**Assessment: REUSE AS-IS**

No changes needed. Tenant isolation is already enforced at the ORM level.

---

### 2.5 Schema: `order_data`

#### `order_data.carts`, `order_data.orders`, `order_data.order_items`, `order_data.coupons`, `order_data.payments`

All extend `TenantAwareEntity`.

**Assessment: REUSE AS-IS**

`order_data.orders` additionally filters by `user_id` so a customer only sees their own orders within a store.

---

### 2.6 Schema: `notification`

#### `notification.notifications`

**Assessment: REUSE AS-IS**

Notifications are sent per user + per tenant context. The notification-service already consumes Kafka events from `tenant.seller.approved`, `tenant.seller.rejected`, etc.

---

## 3. Gap Analysis: What Needs to Change

### 3.1 Identity Service — `email` Column Nullable (REQUIRED)

**Problem:** `identity.users.email` is `NOT NULL`. Customers registering via mobile OTP without providing email will cause a DB constraint violation.

**Solution — Migration V7:**

```sql
-- V7__allow_nullable_email.sql
ALTER TABLE identity.users ALTER COLUMN email DROP NOT NULL;

-- Replace unique constraint to exclude NULLs
ALTER TABLE identity.users DROP CONSTRAINT IF EXISTS uq_users_email;
CREATE UNIQUE INDEX uq_users_email
    ON identity.users (email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;
```

**Java changes required:**
- `User.java`: Remove `nullable = false` from `@Column(name = "email")`.
- `RegisterRequest.java`: Make `@NotBlank @Email` conditional on `phone`-only registrations.
- `AuthService.register()`: Handle the case where `email` is null.
- `UserRepository.findByEmailWithRoles()`: Handle null email lookup gracefully.

---

### 3.2 Identity Service — SUPER_ADMIN Tenant Assignment (REQUIRED)

**Problem:** `user_tenant_roles.tenant_id` is `NOT NULL`. SUPER_ADMIN has no tenant.

**Solution:** Use a reserved sentinel UUID for platform-wide roles:

```sql
-- Convention: 00000000-0000-0000-0000-000000000001 = PLATFORM sentinel
-- Document this in a constants class, not hardcoded inline.
```

No migration needed — just a convention enforced in the code.

**Java changes required:**
- Create `PlatformConstants.java` in the `common` module:
  ```java
  public final class PlatformConstants {
      public static final UUID PLATFORM_TENANT_ID =
          UUID.fromString("00000000-0000-0000-0000-000000000001");
  }
  ```
- `AuthService`: When issuing SUPER_ADMIN token, pass `tenantId = null` to `JwtService.generateAccessToken()` (already handled — null omits `tenant_id` from JWT).
- `JwtAuthFilter`: When `tenant_id` claim is absent, inject `X-Tenant-ID: ""` (already handled).

---

### 3.3 Tenant Service — `TenantEventConsumer` Audit (REQUIRED for Phase 3)

**Problem:** The identity-service must assign `TENANT_OWNER` role when a seller is approved. This is documented in comments (`Seller_Onboarding_Flow.md:196`) and in the Kafka event flow, but `TenantEventConsumer.java` must be verified to implement this.

**Action:** During Phase 3, audit `TenantEventConsumer.java` and confirm it handles `tenant.seller.approved` by inserting into `user_tenant_roles`. If not, implement it.

No migration needed — `user_tenant_roles` already exists with the right schema.

---

### 3.4 API Gateway — `TenantResolutionFilter` (REQUIRED for Phase 2)

**Problem:** Unauthenticated public requests to `freshmart.marketly.com/products` have no JWT and therefore no `tenant_id`. The current `JwtAuthFilter` only extracts `tenant_id` from the JWT — it cannot resolve tenant for public requests.

**Solution:** Add a new `TenantResolutionFilter` at order `-200` (before JwtAuthFilter at `-100`):

```java
// New filter: extract slug from Host header, resolve to tenantId, inject X-Tenant-ID
// Uses Redis cache: tenant-slug:{slug} → uuid (TTL 30 min)
// Falls back to tenant-service HTTP call on cache miss
```

No DB migration needed. Redis and tenant-service are already in place.

---

### 3.5 JWT — Add `phone` Claim (MINOR — optional Phase 1)

**Problem:** Business requirement specifies JWT should contain `mobile` field.

**Solution:** One-line change to `JwtService.generateAccessToken()`:
```java
if (user.getPhone() != null) {
    builder.claim("phone", user.getPhone());
}
```

No DB migration needed.

---

## 4. New Table Justification

**No new tables are needed for this architecture.** The existing schema fully supports the three-surface access model:

| Business Need | Satisfied By |
|---|---|
| User identity (all surfaces) | `identity.users` |
| Role assignments per tenant | `identity.user_tenant_roles` |
| Multi-role per user | `user_tenant_roles` unique constraint |
| Customer profile per store | `customer.customers` UNIQUE(tenant_id, user_id) |
| Seller store record | `tenant.tenants` |
| KYC document storage | `tenant.tenant_settings` (key-value) |
| Store URL / domain | `tenant.tenant_domains` |
| Tenant-scoped products | `product.products` (TenantAwareEntity) |
| Tenant-scoped orders | `order_data.orders` (TenantAwareEntity) |
| Auth events audit trail | `identity.audit_log` |
| Session management | `identity.refresh_tokens` |

---

## 5. Proposed New Migrations

| Migration | Service | File Name | Change |
|---|---|---|---|
| V7 | identity-service | `V7__allow_nullable_email.sql` | Make `email` nullable with partial unique index |
| V8 | identity-service | `V8__seed_platform_constants.sql` | Insert documentation comment for sentinel UUID (optional) |
| *(none)* | tenant-service | — | All tenant tables are sufficient as-is |
| *(none)* | customer-service | — | `customers` table is sufficient as-is |

---

## 6. Summary Table

| Table | Schema | Status | Action |
|---|---|---|---|
| `users` | identity | Exists | Make `email` nullable (V7 migration) |
| `roles` | identity | Exists | Reuse — all 5 roles already seeded |
| `user_tenant_roles` | identity | Exists | Reuse — already supports multi-role/multi-tenant |
| `refresh_tokens` | identity | Exists | Reuse — `tenant_id` already nullable |
| `audit_log` | identity | Exists | Reuse — add surface to `metadata` JSONB |
| `tenants` | tenant | Exists | Fix `status` default to `DRAFT` |
| `tenant_domains` | tenant | Exists | Reuse — subdomain auto-created on tenant create |
| `tenant_settings` | tenant | Exists | Reuse — KYC and address stored here |
| `customers` | customer | Exists | Reuse — per-store profile already modeled |
| `addresses` | customer | Exists | Reuse |
| `categories` | product | Exists | Reuse — TenantAwareEntity |
| `products` | product | Exists | Reuse — TenantAwareEntity |
| `orders` | order_data | Exists | Reuse — TenantAwareEntity |
| `notifications` | notification | Exists | Reuse |
| **New** | — | — | None required |
