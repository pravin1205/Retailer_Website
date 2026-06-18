# User Role Matrix

**Platform:** Marketly — Multi-Tenant Retail SaaS  
**Version:** 2.0  
**Date:** 2026-06-17  
**Status:** Authoritative Architecture Document

---

## 1. Roles in the System

The platform seeds five roles in `identity.roles`. These are fixed platform roles — there are no dynamic or custom roles in Phase 1.

| Role Name | DB Name | Description |
|---|---|---|
| Super Admin | `SUPER_ADMIN` | Platform-wide administrator with full cross-tenant access |
| Tenant Owner | `TENANT_OWNER` | Owner of a specific store — full access within their tenant |
| Store Manager | `STORE_MANAGER` | Can manage products, orders, and customers within their store |
| Store Staff | `STORE_STAFF` | Can view and process orders; adjust inventory; no customer data access |
| Customer | `CUSTOMER` | End shopper — can browse, place orders, manage own profile |

> **Business naming note:** The business requirement uses `SELLER` as the seller-surface role name. In the database and JWT, this maps to `TENANT_OWNER`. When the frontend Seller Portal displays the user's role, it should present `TENANT_OWNER` as "Seller" to avoid exposing internal naming.

---

## 2. Portal Access Matrix

### 2.1 Top-Level Surface Access

| Role | Admin Portal (`admin.marketly.com`) | Seller Portal (`seller.marketly.com`) | Customer Storefront (`{slug}.marketly.com`) |
|---|---|---|---|
| `SUPER_ADMIN` | Full access | Full access (troubleshooting) | Full access (any store) |
| `TENANT_OWNER` | No access | Full access (own store only) | Access (as customer of any store) |
| `STORE_MANAGER` | No access | Partial access (own store only) | Access (as customer of any store) |
| `STORE_STAFF` | No access | Limited access (own store only) | Access (as customer of any store) |
| `CUSTOMER` | No access | No access | Access (stores they are registered at) |

---

## 3. Feature-Level Permission Matrix

### 3.1 Admin Portal Features

| Feature | `SUPER_ADMIN` | `TENANT_OWNER` | `STORE_MANAGER` | `STORE_STAFF` | `CUSTOMER` |
|---|---|---|---|---|---|
| View all tenants/stores | Yes | No | No | No | No |
| View all sellers | Yes | No | No | No | No |
| View all customers (platform-wide) | Yes | No | No | No | No |
| View onboarding requests | Yes | No | No | No | No |
| Approve seller KYC | Yes | No | No | No | No |
| Reject seller KYC | Yes | No | No | No | No |
| Suspend a store | Yes | No | No | No | No |
| Manage subscriptions | Yes | No | No | No | No |
| View platform analytics | Yes | No | No | No | No |
| Impersonate / access any tenant context | Yes | No | No | No | No |

### 3.2 Seller Portal Features

| Feature | `SUPER_ADMIN` | `TENANT_OWNER` | `STORE_MANAGER` | `STORE_STAFF` | `CUSTOMER` |
|---|---|---|---|---|---|
| Register seller account | Via Admin | Yes (own) | No | No | No |
| Submit store onboarding | Via Admin | Yes (own) | No | No | No |
| View onboarding status | Via Admin | Yes (own) | No | No | No |
| Edit store profile/branding | Via Admin | Yes (own) | Yes (own) | No | No |
| Manage products | Via Admin | Yes (own) | Yes (own) | View only | No |
| Manage categories | Via Admin | Yes (own) | Yes (own) | No | No |
| Manage inventory | Via Admin | Yes (own) | Yes (own) | Yes (own) | No |
| View orders | Via Admin | Yes (own) | Yes (own) | Yes (own) | No |
| Update order status | Via Admin | Yes (own) | Yes (own) | Yes (own) | No |
| Manage coupons | Via Admin | Yes (own) | Yes (own) | No | No |
| View store customers | Via Admin | Yes (own) | Yes (own) | No | No |
| View store analytics | Via Admin | Yes (own) | Yes (own) | No | No |
| Manage store settings | Via Admin | Yes (own) | No | No | No |
| Add/remove store staff | Via Admin | Yes (own) | No | No | No |
| View own store's subscription | Via Admin | Yes (own) | No | No | No |

### 3.3 Customer Storefront Features

| Feature | `SUPER_ADMIN` | `TENANT_OWNER` | `STORE_MANAGER` | `STORE_STAFF` | `CUSTOMER` |
|---|---|---|---|---|---|
| Browse products (public) | Yes | Yes | Yes | Yes | Yes (no auth) |
| Browse categories (public) | Yes | Yes | Yes | Yes | Yes (no auth) |
| View product details (public) | Yes | Yes | Yes | Yes | Yes (no auth) |
| Register / create account | Yes | Yes | Yes | Yes | Yes |
| Login to storefront | Yes | Yes | Yes | Yes | Yes |
| View own profile | Yes | Yes | Yes | Yes | Yes |
| Update own profile | Yes | Yes | Yes | Yes | Yes |
| Add delivery address | Yes | Yes | Yes | Yes | Yes |
| Add to cart | Yes | Yes | Yes | Yes | Yes |
| Apply coupon | Yes | Yes | Yes | Yes | Yes |
| Place order | Yes | Yes | Yes | Yes | Yes |
| View own orders | Yes | Yes | Yes | Yes | Yes |
| Cancel own order | Yes | Yes | Yes | Yes | Yes |
| View loyalty points | Yes | Yes | Yes | Yes | Yes |
| View other customers' orders | No | No | No | No | No |
| View other stores' products | No | No | No | No | No |

---

## 4. Multi-Role Scenarios

### Scenario 1: Customer Only

```
Roles: [CUSTOMER]
JWT tenant_id: <freshmart-uuid>
```

- Can only access the FreshMart storefront.
- No Seller Portal access.
- No Admin Portal access.

### Scenario 2: Seller (Tenant Owner) Only

```
Roles: [TENANT_OWNER]
JWT tenant_id: <freshmart-uuid>
```

- Can access Seller Portal for FreshMart.
- Can also shop on any storefront as a customer (with a separate customer-scoped login).
- No Admin Portal access.

### Scenario 3: Same Person is Both Seller and Customer of Their Own Store

```
user_tenant_roles rows:
  (userId, freshMart-uuid, TENANT_OWNER)
  (userId, freshMart-uuid, CUSTOMER)

JWT roles: ["TENANT_OWNER", "CUSTOMER"]
JWT tenant_id: <freshmart-uuid>
```

- Single login. Single JWT.
- Seller Portal shows store management UI (checks for TENANT_OWNER role).
- Storefront shows shopping UI (checks for CUSTOMER role).
- Same credentials for both.

### Scenario 4: Customer of Multiple Stores

```
user_tenant_roles rows:
  (userId, freshMart-uuid,       CUSTOMER)
  (userId, organicBasket-uuid,   CUSTOMER)

Login at freshmart.marketly.com:
  JWT roles: ["CUSTOMER"]
  JWT tenant_id: <freshmart-uuid>

Login at organicbasket.marketly.com:
  JWT roles: ["CUSTOMER"]
  JWT tenant_id: <organicBasket-uuid>
```

- Separate JWT per store session.
- Separate loyalty points and order history per store.
- Same phone/email/password credentials.

### Scenario 5: Super Admin

```
user_tenant_roles rows:
  (adminUserId, NULL, SUPER_ADMIN)   -- tenant_id is a special system UUID or NULL sentinel

JWT roles: ["SUPER_ADMIN"]
JWT tenant_id: (absent)
```

- Access Admin Portal without tenant context.
- Can log into Seller Portal by passing `tenantSlug` in login request → gets scoped token for that store.
- Can log into any Storefront with customer token for support purposes.

### Scenario 6: Store Manager

```
user_tenant_roles rows:
  (managerId, freshMart-uuid, STORE_MANAGER)

JWT roles: ["STORE_MANAGER"]
JWT tenant_id: <freshmart-uuid>
```

- Seller Portal access: can manage products, orders, view customers. Cannot manage settings or staff.
- No Admin Portal access.

---

## 5. Role Assignment Rules

| Role | Who assigns it | When |
|---|---|---|
| `CUSTOMER` | Identity Service (auto) | On OTP verification with `tenantSlug` provided |
| `TENANT_OWNER` | Identity Service (via Kafka event from Tenant Service) | When SUPER_ADMIN approves seller KYC |
| `STORE_MANAGER` | `TENANT_OWNER` via Seller Portal | Staff management screen |
| `STORE_STAFF` | `TENANT_OWNER` or `STORE_MANAGER` via Seller Portal | Staff management screen |
| `SUPER_ADMIN` | Manual DB seed or another SUPER_ADMIN | Platform initialization only |

---

## 6. Role Propagation on Seller Approval

When a SUPER_ADMIN approves a seller (via `PATCH /api/v1/tenants/{slug}/approve`), the following happens:

1. `tenant-service` sets `tenant.status = ACTIVE`.
2. `tenant-service` publishes `tenant.seller.approved` Kafka event with `ownerUserId` and `tenantId`.
3. `identity-service` consumes the event and inserts:
   ```sql
   INSERT INTO identity.user_tenant_roles (user_id, tenant_id, role_id)
   VALUES (<ownerUserId>, <tenantId>, <TENANT_OWNER role id>);
   ```
4. Next login by the seller will include `TENANT_OWNER` in their JWT roles.

> **Note:** This Kafka consumer (`TenantEventConsumer` in identity-service) must be verified to handle the `tenant.seller.approved` event. The current `TenantEventConsumer.java` should be audited during implementation Phase 3.

---

## 7. Token Scope Summary

| Login surface | `tenantSlug` in request? | `tenant_id` in JWT | `roles` in JWT |
|---|---|---|---|
| Admin Portal | No | Absent | `["SUPER_ADMIN"]` |
| Seller Portal | Yes | Present | `["TENANT_OWNER"]` (or `STORE_MANAGER`, `STORE_STAFF`) |
| Customer Storefront | Yes (from subdomain) | Present | `["CUSTOMER"]` |
| Multi-role (seller+customer) | Yes | Present | `["TENANT_OWNER", "CUSTOMER"]` |
