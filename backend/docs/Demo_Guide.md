# Marketly — Client Demo Guide

**Date:** 2026-06-18  
**Environment:** Local Development  
**Platform:** Multi-Tenant Retail SaaS

---

## Services Required Before Demo

Ensure all of the following are running:

| Service | Port | How to start |
|---|---|---|
| PostgreSQL (Docker) | 5432 | `docker compose -f backend/docker-compose.infra.yml up -d` |
| Redis (Docker) | 6379 | Included in docker-compose.infra.yml |
| Kafka (Docker) | 9092 | Included in docker-compose.infra.yml |
| identity-service | 8081 | IntelliJ → Run `IdentityServiceApplication` |
| tenant-service | 8082 | IntelliJ → Run `TenantServiceApplication` |
| product-service | 8083 | IntelliJ → Run `ProductServiceApplication` |
| customer-service | 8084 | IntelliJ → Run `CustomerServiceApplication` |
| order-service | 8085 | IntelliJ → Run `OrderServiceApplication` |
| api-gateway | 8080 | IntelliJ → Run `ApiGatewayApplication` |
| Frontend (Vite) | 3000 | `npm run dev` in project root |

Startup order matters: start infrastructure (Docker) first, then services, then frontend.

---

## Part 1 — Super Admin Login

### URL
```
http://localhost:3000/admin
```

### Credentials
| Field | Value |
|---|---|
| Email | `admin@marketly.com` |
| Password | `Admin@1234` |

### What to show the client
1. Open `http://localhost:3000/admin` — the sign-in wall appears.
2. Enter the credentials above and click **Sign in to admin**.
3. The platform dashboard loads showing all tenants and platform-wide metrics.
4. Navigate to **Tenants** → shows all registered stores with their status (DRAFT, PENDING_VERIFICATION, UNDER_REVIEW, ACTIVE, REJECTED).
5. Point out the **KYC review panel** — admin can see Aadhaar, PAN, GST, and approve or reject.
6. Show the **approve / reject flow** with review notes.
7. On approval the store becomes ACTIVE and the seller gets the TENANT_OWNER role automatically.

### Admin portal pages
| Page | URL |
|---|---|
| Dashboard | `http://localhost:3000/admin` |
| Tenant / Seller list | `http://localhost:3000/admin/tenants` |
| Orders (platform-wide) | `http://localhost:3000/admin/orders` |
| Analytics | `http://localhost:3000/admin/analytics` |
| Notifications | `http://localhost:3000/admin/notifications` |

---

## Part 2 — Seller Onboarding

### URL to start
```
http://localhost:3000/onboarding/seller
```

### What the seller does (Step by Step)

#### Step 1 — Enter Mobile Number
- Seller opens `http://localhost:3000/onboarding/seller`
- Enters a 10-digit mobile number
- Clicks **Send OTP**

> **Demo note (no SMS in local):** After clicking Send OTP, manually seed the OTP into Redis so the seller can proceed:
> ```bash
> docker exec marketly-redis redis-cli SET "otp:{phone}" "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92" EX 600
> ```
> Replace `{phone}` with the number entered. This seeds OTP = `123456`.

#### Step 2 — Verify OTP
- Enter `123456` in the OTP field
- Account is created, wizard advances automatically

#### Step 3 — Business Information
Fill in:
- **Store Name** — e.g. `Fresh Mart`
- **Store Slug** — auto-generated as `freshmart` (unique URL identifier)
- **Store Category** — Grocery / Bakery / Pharmacy / etc.
- **Business Type** — Individual / Partnership / Pvt. Ltd.
- **Business Email**

> The slug uniqueness is checked live on blur — a tick/cross shows availability.

#### Step 4 — Store Address
Fill in:
- Address Line 1, City, State, Pincode
- Delivery Radius (km)

#### Step 5 — Branding
- Pick an emoji for the store logo
- Select a banner gradient (6 presets)
- Select an accent colour (Emerald / Sunset / Royal / Rose)
- Live preview of the store card updates in real time

#### Step 6 — KYC Submission
Fill in:
- Aadhaar Number (12 digits)
- PAN Number
- GST Number (optional)

Click **Submit for Review** → store status becomes `PENDING_VERIFICATION`.

#### Step 7 — Awaiting Approval
- The seller sees a status timeline: Submitted → Under Review → Approved → Active
- Shows "We'll notify you within 1–2 business days"
- The status page auto-polls every 30 seconds

**Status page URL:**
```
http://localhost:3000/onboarding/seller/status
```

> Switch to the Admin tab, find the new application, click **Mark Under Review** then **Approve**. Return to the seller tab — the status updates to ACTIVE within 30 seconds.

---

## Part 3 — Seller Login (Existing Seller)

The `bakery` store is already approved and active in this environment.

### Option A — OTP Login (recommended for demo)

**URL:** `http://localhost:3000/s/bakery/admin`

1. The sign-in wall appears with **Mobile OTP** tab selected by default.
2. Enter phone: `9629138542`
3. Click **Send OTP**
4. Seed the OTP into Redis (no SMS in local):
   ```bash
   docker exec marketly-redis redis-cli SET "otp:9629138542" "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92" EX 600
   ```
5. Enter OTP: `123456`
6. Click **Verify & Sign in**
7. Seller dashboard loads for the **bakery** store

### Option B — Email & Password Login

1. Click the **Email & Password** tab on the sign-in wall
2. Enter:
   - Email: `seller@bakery.com`
   - Password: `Admin@1234`
3. Click **Sign in to dashboard**

### Seller dashboard pages
| Page | URL |
|---|---|
| Dashboard | `http://localhost:3000/s/bakery/admin` |
| Products | `http://localhost:3000/s/bakery/admin/products` |
| Orders | `http://localhost:3000/s/bakery/admin/orders` |
| Customers | `http://localhost:3000/s/bakery/admin/customers` |
| Categories | `http://localhost:3000/s/bakery/admin/categories` |
| Coupons | `http://localhost:3000/s/bakery/admin/coupons` |
| Reports | `http://localhost:3000/s/bakery/admin/reports` |
| Settings | `http://localhost:3000/s/bakery/admin/settings` |

---

## Part 4 — Customer Registration and Login

### Entry Point
Customers access the platform through a **store-specific URL** shared by the seller.

For the bakery store:
```
http://localhost:3000/s/bakery
```

This is the dedicated storefront for bakery. The customer sees only bakery products — no other stores, no platform marketplace.

### Customer Onboarding URL
```
http://localhost:3000/onboarding/customer?tenant=bakery
```

### Steps

#### Step 1 — Enter Mobile Number
- Customer opens the onboarding URL above
- Enters a 10-digit mobile number
- Clicks **Send OTP**

> **Demo note:** Seed the OTP after clicking Send OTP:
> ```bash
> docker exec marketly-redis redis-cli SET "otp:{phone}" "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92" EX 600
> ```
> OTP = `123456`

#### Step 2 — Verify OTP
- Enter `123456`
- Account created and scoped to the bakery store
- JWT issued with `roles: ["CUSTOMER"]` and `tenantId: <bakery-uuid>`

#### Step 3 — Profile Completion
- Enter First Name (required), Last Name, Email (optional)

#### Step 4 — Delivery Address
- Enter address details
- First address is auto-set as default

#### Step 5 — Done
- Welcome screen with 50 loyalty points awarded
- Redirects to `http://localhost:3000/s/bakery` — the bakery storefront

### What to show the client on the storefront
1. Browse products — only bakery products visible (tenant-isolated)
2. Add to cart
3. Checkout
4. View order history at `http://localhost:3000/s/bakery/orders`
5. Loyalty points visible in profile

### Returning customer login
If the customer already registered, they log in again via OTP at:
```
http://localhost:3000/s/bakery
```
The storefront shows a "Sign in" option that goes through the same OTP flow.

### Existing customer in this environment
| Phone | Store | Status |
|---|---|---|
| `9876543210` | bakery | Registered (BRONZE tier, 0 points) |

To log in as this existing customer, seed their OTP:
```bash
docker exec marketly-redis redis-cli SET "otp:9876543210" "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92" EX 600
```
Then go to `http://localhost:3000/onboarding/customer?tenant=bakery`, enter `9876543210`, OTP `123456`.

---

## Summary — All Demo URLs

| Who | Action | URL |
|---|---|---|
| Super Admin | Login | `http://localhost:3000/admin` |
| Super Admin | Tenant/KYC review | `http://localhost:3000/admin/tenants` |
| Seller | New seller onboarding | `http://localhost:3000/onboarding/seller` |
| Seller | Check onboarding status | `http://localhost:3000/onboarding/seller/status` |
| Seller | Login + Dashboard (bakery) | `http://localhost:3000/s/bakery/admin` |
| Customer | New customer registration | `http://localhost:3000/onboarding/customer?tenant=bakery` |
| Customer | Bakery storefront | `http://localhost:3000/s/bakery` |
| Customer | Orders | `http://localhost:3000/s/bakery/orders` |
| Customer | Profile | `http://localhost:3000/s/bakery/account` |

---

## Summary — All Demo Credentials

| Role | Identifier | Password / OTP | Notes |
|---|---|---|---|
| Super Admin | `admin@marketly.com` | `Admin@1234` | Email + password |
| Seller (bakery) | Phone `9629138542` | OTP `123456` (seed Redis) | OTP login |
| Seller (bakery) | `seller@bakery.com` | `Admin@1234` | Email + password |
| Customer (bakery) | Phone `9876543210` | OTP `123456` (seed Redis) | OTP login |

---

## Dev OTP Seeding — Quick Reference

Since local environments have no SMS gateway, use this command to inject a valid OTP for any phone number before entering it on screen:

```bash
# Replace 9999999999 with the actual phone number
docker exec marketly-redis redis-cli SET "otp:9999999999" "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92" EX 600
```

This sets OTP = `123456`, valid for 10 minutes.

---

## After the Demo — Cleanup

When the demo is done and services are stopped, run:

```bash
# Delete log files (locked while services run)
del backend\*.log

# Delete Maven build output
rmdir /s /q backend\identity-service\target
rmdir /s /q backend\tenant-service\target
rmdir /s /q backend\customer-service\target
rmdir /s /q backend\product-service\target
rmdir /s /q backend\order-service\target
rmdir /s /q backend\api-gateway\target
rmdir /s /q backend\common\target
rmdir /s /q backend\analytics-service\target
rmdir /s /q backend\notification-service\target
```
