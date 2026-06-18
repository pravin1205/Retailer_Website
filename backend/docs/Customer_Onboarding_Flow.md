# Customer Onboarding Flow

**Platform:** Marketly — Multi-Tenant Retail SaaS  
**Version:** 2.0  
**Date:** 2026-06-17  
**Status:** Authoritative Architecture Document

> This document supersedes the earlier draft. It is grounded in the actual codebase: `identity-service`, `customer-service` (`customer.customers` table, `UNIQUE(tenant_id, user_id)` constraint), `UserTenantRole`, and the multi-tenant JWT model.

---

## 1. Design Principles

### 1.1 Storefront Isolation

When a customer arrives at `freshmart.marketly.com`, they must ONLY see:
- FreshMart products
- FreshMart categories
- FreshMart offers
- FreshMart loyalty program

They must NOT see:
- A marketplace of all stores
- Any mention of other sellers
- "Nearby stores" from the platform

The storefront behaves like a **dedicated website** for that seller. The customer has no awareness of the broader Marketly platform unless the seller explicitly communicates it.

### 1.2 Reusable Identity, Per-Store Profile

A customer's identity (phone, email, password) is shared across the platform. Their profile data (name, loyalty points, order history) is **scoped per store**. This is enforced by the `UNIQUE(tenant_id, user_id)` constraint on `customer.customers`.

### 1.3 Tenant Context from URL

The customer's storefront URL is the source of truth for `tenantSlug`. The frontend reads the subdomain or path parameter and passes it as `tenantSlug` in every auth request. This ensures the resulting JWT is scoped to the correct tenant.

---

## 2. Entry Points

| How customer arrives | URL | Tenant Context |
|---|---|---|
| Seller shares link | `freshmart.marketly.com` | `tenantSlug = "freshmart"` (from subdomain) |
| Path-based URL | `marketly.com/store/freshmart` | `tenantSlug = "freshmart"` (from path) |
| Direct navigation | `freshmart.marketly.com/auth/signup` | `tenantSlug = "freshmart"` |
| Returning customer | `freshmart.marketly.com` (bookmarked) | `tenantSlug = "freshmart"` |

The customer **never** starts from `marketly.com` to find a store — they are always given the store-specific URL by the seller.

---

## 3. Full Journey Overview

```
freshmart.marketly.com
  │
  ├── [Public browsing — no login required]
  │   ├── View products (GET /api/v1/products — public)
  │   ├── View categories (GET /api/v1/categories — public)
  │   └── View product details
  │
  └── [Customer registration / login]
      ├── Step 1: Mobile Number Entry
      ├── Step 2: OTP Verification
      │   ├── isNewUser = true  → Step 3 (Profile Setup)
      │   └── isNewUser = false → Step 4 (Address) or → Storefront directly
      ├── Step 3: Profile Completion (name, email)
      ├── Step 4: Delivery Address
      └── Step 5: Onboarding Complete → storefront homepage
```

---

## 4. Step-by-Step Flow

### Step 1 — Mobile Number Entry

**URL:** `freshmart.marketly.com/auth/signup` or `freshmart.marketly.com/onboarding`

**What the customer sees:**
- A registration form styled as FreshMart's dedicated signup page (uses the seller's accent color and logo).
- Single input for mobile number.
- No mention of "Marketly platform" — it looks like FreshMart's own app.

**What happens:**

```http
POST /api/v1/auth/otp/send
{
  "phone": "9876543210"
}
```

- OTP stored in Redis with 10-minute TTL.
- SMS dispatched: "Your FreshMart OTP is XXXXXX. Valid for 10 minutes."
- UI advances to Step 2.

---

### Step 2 — OTP Verification

**What the customer sees:**
- 6-digit OTP input (auto-advance on complete fill).
- 30-second resend countdown.
- "Resend OTP" link after timer expires.

**What happens:**

```http
POST /api/v1/auth/otp/verify
{
  "phone":      "9876543210",
  "otp":        "482931",
  "tenantSlug": "freshmart"
}
```

**Identity Service behavior:**

1. Validates OTP against Redis.
2. **New user path:** Creates `identity.users` record (`is_verified = true`). Assigns `CUSTOMER` role for `freshmart` tenant in `user_tenant_roles`.
3. **Returning user path:** Looks up existing user. Checks if `(userId, freshMartId, CUSTOMER)` row exists in `user_tenant_roles`:
   - If yes → existing customer, login.
   - If no → first visit to this store → inserts the role row → creates a `customer.customers` profile record.
4. Issues JWT scoped to FreshMart:
   ```json
   {
     "sub":       "<userId>",
     "email":     "buyer@gmail.com",
     "roles":     ["CUSTOMER"],
     "tenant_id": "<freshmart-uuid>"
   }
   ```
5. Returns `accessToken`, `refreshToken`, `isNewUser` flag.

**Frontend:**
- Stores tokens in auth store.
- If `isNewUser = true` → advance to Step 3 (Profile Setup).
- If `isNewUser = false` → advance to Step 4 (Address) if no address exists, else go directly to storefront.

---

### Step 3 — Profile Completion (New Users Only)

**What the customer sees:**
- Simple name form.
- All fields optional except First Name.

**Fields collected:**

| Field | Required |
|---|---|
| First Name | Yes |
| Last Name | No |
| Email | No (optional) |
| Date of Birth | No |

**What happens:**

```http
PATCH /api/v1/customers/me
Authorization: Bearer <customerToken>
X-Tenant-ID: <freshmart-uuid>
{
  "firstName":   "Priya",
  "lastName":    "Sharma",
  "email":       "priya@gmail.com",
  "dateOfBirth": "1992-04-15"
}
```

- `customer-service` updates (or creates) the `customer.customers` row for `(tenant_id=freshmart, user_id=userId)`.
- Welcome loyalty points awarded: `loyalty_points += 50`.
- Advances to Step 4.

---

### Step 4 — Delivery Address

**What the customer sees:**
- Address form. First-time customers must save at least one address.
- "Use my location" GPS button.

**Fields collected:**

| Field | Required |
|---|---|
| Address Label | Yes (Home / Office / Other) |
| House No. & Building | Yes |
| Street & Area | Yes |
| Landmark | No |
| City | Yes |
| State | Yes |
| Pincode | Yes |

**What happens:**

```http
POST /api/v1/customers/me/addresses
Authorization: Bearer <customerToken>
X-Tenant-ID: <freshmart-uuid>
{
  "label":      "Home",
  "line1":      "12A, Sunrise Apartments",
  "line2":      "MG Road",
  "landmark":   "Near City Mall",
  "city":       "Pune",
  "state":      "Maharashtra",
  "pincode":    "411001",
  "isDefault":  true
}
```

- First address is automatically set as `isDefault = true`.
- Customer can add more addresses or skip and proceed.
- Advances to Step 5.

---

### Step 5 — Onboarding Complete

**What the customer sees:**
- "Welcome to FreshMart, Priya!" heading.
- Loyalty badge: "🎁 You've earned 50 welcome points."
- "Start Shopping" CTA → redirects to `freshmart.marketly.com` (storefront homepage).

**Background actions:**
- `CustomerProfileCompletedEvent` published via Kafka.
- notification-service sends welcome email (if email was provided).

---

## 5. Returning Customer — Subsequent Visits

### Returning to same store

Customer visits `freshmart.marketly.com` again (bookmarked or link).

- JWT stored in localStorage/cookie for this origin.
- If token valid → auto-login → directly to storefront.
- If token expired → refresh via `POST /api/v1/auth/refresh`.
- If refresh token expired → re-enter mobile + OTP (Step 1 again).

### Visiting a second store (OrganicBasket)

Customer now visits `organicbasket.marketly.com`.

- No JWT for this origin.
- Customer enters mobile number → OTP → `tenantSlug = "organicbasket"`.
- Identity service finds existing user (same phone).
- Checks `(userId, organicBasketId, CUSTOMER)` in `user_tenant_roles` → **does not exist yet**.
- Inserts new `user_tenant_roles` row: `(userId, organicBasketId, CUSTOMER)`.
- Creates new `customer.customers` row: `(tenant_id=organicBasket, user_id=userId)`.
- Issues JWT scoped to OrganicBasket.
- Customer now has TWO separate profiles:
  - FreshMart: 50 loyalty points, 3 orders
  - OrganicBasket: 0 loyalty points, 0 orders (brand new)

The two profiles are completely isolated. The customer sees only OrganicBasket data when on that storefront.

---

## 6. Customer-Tenant Association Model

```
identity.users
  └── id = <userId>
      phone = "9876543210"

identity.user_tenant_roles
  ├── (userId, freshMart-uuid,     CUSTOMER)  ← registered at FreshMart
  └── (userId, organicBasket-uuid, CUSTOMER)  ← registered at OrganicBasket

customer.customers
  ├── (tenant_id = freshMart-uuid,     user_id = userId) ← FreshMart profile
  │   loyalty_points = 120, total_orders = 3
  └── (tenant_id = organicBasket-uuid, user_id = userId) ← OrganicBasket profile
      loyalty_points = 50, total_orders = 0
```

The `UNIQUE(tenant_id, user_id)` constraint on `customer.customers` ensures exactly one profile per customer per store.

---

## 7. What Customers CANNOT See

These access restrictions are enforced at multiple layers:

| Blocked Access | Enforcement Layer |
|---|---|
| Products from other stores | Hibernate `tenantFilter` (tenant_id in JWT) |
| Other customers' orders | `order.orders` filtered by `user_id` AND `tenant_id` |
| Platform store listing | No endpoint serves this to CUSTOMER role |
| Another store's categories | Tenant filter on `product.categories` |
| Another store's loyalty points | `customer.customers` filtered by `tenant_id` |
| Admin Portal | `SUPER_ADMIN` role required; CUSTOMER role rejected |
| Seller Portal | `TENANT_OWNER/STORE_MANAGER/STORE_STAFF` required; CUSTOMER role rejected |

---

## 8. Public vs. Authenticated Endpoints

| Endpoint | Auth Needed | Tenant Resolution |
|---|---|---|
| `GET /api/v1/products` | No | From `X-Tenant-ID` header (set by gateway from subdomain) |
| `GET /api/v1/categories` | No | From `X-Tenant-ID` header |
| `GET /api/v1/products/{id}` | No | From `X-Tenant-ID` header |
| `GET /api/v1/tenants/{slug}` | No | Slug in path |
| `POST /api/v1/auth/otp/send` | No | — |
| `POST /api/v1/auth/otp/verify` | No | `tenantSlug` in body |
| `GET /api/v1/customers/me` | Yes (CUSTOMER) | JWT `tenant_id` |
| `POST /api/v1/cart/items` | Yes (CUSTOMER) | JWT `tenant_id` |
| `POST /api/v1/orders` | Yes (CUSTOMER) | JWT `tenant_id` |
| `GET /api/v1/orders` | Yes (CUSTOMER) | JWT `tenant_id` + `user_id` |

---

## 9. Tenant Resolution for Public Requests

When a customer browses products WITHOUT logging in, the API Gateway must still inject the correct `X-Tenant-ID` so the Hibernate filter scopes results to the right store.

**Mechanism:** The reverse proxy (nginx / CDN) or a gateway filter extracts the subdomain from the `Host` header, resolves it to a `tenantId` via a tenant lookup (Redis cache: `slug → tenantId`), and injects `X-Tenant-ID` before forwarding to the service.

```
Request: GET /api/v1/products
Host: freshmart.marketly.com

Gateway:
  1. Extract slug "freshmart" from Host header.
  2. Cache lookup: freshmart → <freshmart-uuid>.
  3. Inject X-Tenant-ID: <freshmart-uuid>
  4. Forward to product-service.

Product-service:
  1. Read X-Tenant-ID.
  2. Activate Hibernate tenantFilter with tenantId = <freshmart-uuid>.
  3. Return only FreshMart products.
```

This ensures even unauthenticated public browsing is tenant-isolated.

---

## 10. Notifications Sent

| Trigger | Channel | Content |
|---|---|---|
| Step 1: OTP sent | SMS | "Your FreshMart OTP is XXXXXX. Valid 10 minutes." |
| Step 2: OTP verified (new user) | Email | "Welcome! Your FreshMart account is ready." |
| Step 3: Profile complete | In-App | "Profile saved. You've earned 50 loyalty points." |
| Step 5: Onboarding complete | Email | "Welcome to FreshMart, Priya! Start shopping." |

---

## 11. Frontend State

Customer onboarding state is kept in memory (NOT persisted to localStorage) because the OTP expires in 10 minutes — there's no meaningful draft to resume.

```typescript
interface CustomerOnboardingState {
  step: 1 | 2 | 3 | 4 | 5;
  phone: string;
  tenantSlug: string | null;   // resolved from URL on page load
  isNewUser: boolean;
}
```

The `tenantSlug` is resolved from the URL at app initialization and stored in state before any API calls are made.
