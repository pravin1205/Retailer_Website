# Seller Onboarding Flow

**Platform:** Marketly — Multi-Tenant Retail SaaS  
**Version:** 2.0  
**Date:** 2026-06-17  
**Status:** Authoritative Architecture Document

> This document supersedes the earlier draft. It is grounded in the actual codebase: `identity-service`, `tenant-service`, `SellerOnboardingController`, `TenantService`, `TenantEventProducer`, and all Flyway migrations.

---

## 1. Entry Point

```
https://seller.marketly.com
```

The seller navigates to this URL to register a new store. There is no invitation required. Any user can initiate seller onboarding.

---

## 2. Full Journey Overview

```
seller.marketly.com
  │
  ├── Step 1: Mobile Number Entry
  ├── Step 2: OTP Verification → account created, JWT issued
  ├── Step 3: Business Information → tenant record created (status=DRAFT)
  ├── Step 4: Store Address → tenant settings updated
  ├── Step 5: Branding Setup → tenant branding updated
  ├── Step 6: KYC Submission → status=PENDING_VERIFICATION
  ├── Step 7: Submitted Screen → seller waits for admin review
  │
  └── [Super Admin side]
      ├── Super Admin sees new application in admin.marketly.com
      ├── PATCH /api/v1/tenants/{slug}/review → status=UNDER_REVIEW
      ├── Super Admin reviews KYC documents
      ├── PATCH /api/v1/tenants/{slug}/approve (APPROVE / REJECT)
      │     ├── APPROVE → status=ACTIVE, TENANT_OWNER role assigned, store URL live
      │     └── REJECT  → status=REJECTED, seller resubmits KYC
      │
      └── On ACTIVE → Kafka: tenant.seller.approved + tenant.tenant.activated
```

---

## 3. Step-by-Step Flow

### Step 1 — Mobile Number Entry

**URL:** `seller.marketly.com/onboarding/seller`

**What the seller sees:**
- Single input for mobile number (10-digit, Indian format).

**What happens:**

```http
POST /api/v1/auth/otp/send
{
  "phone": "9876543210"
}
```

- OTP service generates a 6-digit code, stores it in Redis with 10-minute TTL.
- SMS dispatched via notification-service.
- UI advances to Step 2.

---

### Step 2 — OTP Verification

**What the seller sees:**
- 6-digit OTP input field.
- 30-second resend countdown.

**What happens:**

```http
POST /api/v1/auth/otp/verify
{
  "phone":  "9876543210",
  "otp":    "482931",
  "role":   "TENANT_OWNER",
  "surface": "seller"
}
```

**Identity Service behavior:**

1. Validates OTP against Redis.
2. If phone has no existing account → creates `identity.users` record (`is_verified = true`, no password yet — password set optionally later).
3. Does NOT assign any role yet (no tenant exists yet to bind the role to).
4. Issues a short-lived JWT with empty roles (or a `PENDING_SELLER` marker — implementation choice).
5. Returns `accessToken` + `refreshToken` + `isNewUser` flag.

**Frontend stores:**
- `accessToken`, `refreshToken` in auth store.
- `phone` in onboarding store.
- Advances to Step 3.

**If phone already registered as a seller:**
- Identity service returns existing user JWT.
- Frontend checks `tenantSlug` in onboarding store.
- If tenant already exists → show "Resume application" banner and jump to last incomplete step.

---

### Step 3 — Business Information

**What the seller sees:**
- Form with store details.

**Fields collected:**

| Field | Required | Notes |
|---|---|---|
| Store Name | Yes | Max 200 chars |
| Store Slug | Yes | Auto-generated from name; validated for uniqueness |
| Store Category | Yes | Grocery / Organic / Bakery / Electronics / Pharmacy / Fashion / Other |
| Business Type | Yes | Individual / Partnership / Private Limited / LLP |
| Store Description | No | Free text |
| Contact Number | Pre-filled | From OTP step |
| Business Email | Yes | Used for approval notifications |

**Slug uniqueness check (on blur):**

```http
GET /api/v1/tenants/{slug}
→ 200 = taken, 404 = available
```

**Tenant creation on submit:**

```http
POST /api/v1/tenants
Authorization: Bearer <sellerToken>
{
  "slug":        "freshmart",
  "name":        "Fresh Mart",
  "tagline":     "Farm-fresh daily",
  "description": "Premium organic grocery store",
  "category":    "Grocery",
  "ownerEmail":  "owner@freshmart.com"
}
```

**What `TenantService.createTenant()` does:**

1. Validates slug uniqueness.
2. Creates `tenant.tenants` record with `status = DRAFT`, `onboarding_step = MOBILE`.
3. Automatically creates `tenant.tenant_domains` record: `freshmart.marketly.com` (primary=true, verified=true).
4. Publishes `tenant.tenant.created` Kafka event.
5. Returns the created tenant.

**Frontend stores** `tenantSlug = "freshmart"` in onboarding store. Advances to Step 4.

---

### Step 4 — Store Address

**What the seller sees:**
- Address form.

**Fields collected:**

| Field | Required |
|---|---|
| Address Line 1 | Yes |
| Address Line 2 | No |
| City | Yes |
| State | Yes |
| Pincode | Yes |
| GPS Coordinates (lat/lng) | No |
| Delivery Radius (km) | Yes — default 5 |

**API call:**

```http
PATCH /api/v1/tenants/{slug}/settings
Authorization: Bearer <sellerToken>
{
  "address_line1":     "12 Market Road",
  "address_line2":     "Near Bus Stand",
  "city":              "Pune",
  "state":             "Maharashtra",
  "pincode":           "411001",
  "latitude":          "18.5204",
  "longitude":         "73.8567",
  "delivery_radius_km": "5"
}
```

All address fields are stored as key-value pairs in `tenant.tenant_settings` — no schema change needed.

Advances to Step 5.

---

### Step 5 — Branding Setup

**What the seller sees:**
- Emoji logo picker.
- Banner gradient selector (6 presets).
- Accent color selector (4 options: Emerald / Sunset / Royal / Rose).
- Store tagline input.
- Live preview of the store card.

**API calls:**

```http
PUT /api/v1/tenants/{slug}
Authorization: Bearer <sellerToken>
{
  "accentColor": "emerald"
}

PATCH /api/v1/tenants/{slug}/settings
Authorization: Bearer <sellerToken>
{
  "logo_emoji":      "🥦",
  "banner_gradient": "green-teal",
  "tagline":         "Farm-fresh daily"
}
```

Advances to Step 6.

---

### Step 6 — KYC Submission

**What the seller sees:**
- Document upload form.

**Fields collected:**

| Field | Required | Validation |
|---|---|---|
| Aadhaar Number | Yes | 12 digits: `^\d{4}\s?\d{4}\s?\d{4}$` |
| PAN Number | Yes | `^[A-Z]{5}[0-9]{4}[A-Z]$` |
| GST Number | No | `^\d{2}[A-Z]{5}\d{4}[A-Z]\d[Z][A-Z\d]$` |
| Aadhaar Front + Back URLs | Yes | Document upload URLs |
| PAN Card URL | Yes | Document upload URL |
| Store Image / Photo | No | Optional photo URL |

**API call:**

```http
POST /api/v1/tenants/{slug}/kyc
Authorization: Bearer <sellerToken>
{
  "aadhaarNumber":  "1234 5678 9012",
  "panNumber":      "ABCDE1234F",
  "gstNumber":      "29ABCDE1234F1Z5",
  "documentUrls":   ["https://cdn/.../aadhaar-front.jpg", "https://cdn/.../pan.jpg"],
  "storeImageUrl":  "https://cdn/.../store-photo.jpg"
}
```

**What `SellerOnboardingController.submitKyc()` does:**

1. Validates only the tenant owner can submit (checks `X-User-ID` against `tenant.owner_user_id`).
2. Stores all KYC fields as `tenant_settings` key-value entries:
   - `kyc_aadhaar`, `kyc_pan`, `kyc_gst`, `kyc_status = SUBMITTED`
   - `kyc_document_urls`, `kyc_store_image`
3. Sets `tenant.status = PENDING_VERIFICATION`, `onboarding_step = KYC_SUBMITTED`.
4. Publishes `tenant.seller.kyc-submitted` Kafka event.

Advances to Step 7.

---

### Step 7 — Submitted / Awaiting Approval

**What the seller sees:**
- "Application submitted!" success screen.
- Onboarding timeline: Submitted → Under Review → Approved → Active.
- "We'll email you at {email} within 1-2 business days."
- Link to status page: `seller.marketly.com/onboarding/seller/status`.

**Status page polling:**

```http
GET /api/v1/tenants/{slug}
```

Polled every 30 seconds.

**Status display mapping:**

| `tenant.status` | Badge | UI |
|---|---|---|
| `PENDING_VERIFICATION` | Yellow "Pending Review" | Timeline with first step active |
| `UNDER_REVIEW` | Blue "Under Review" | Timeline with second step active |
| `ACTIVE` | Green "Store Live" | "Go to Dashboard" → `seller.marketly.com/s/{slug}/admin` |
| `REJECTED` | Red "Rejected" | Reason shown + "Resubmit KYC" button |

---

## 4. Draft Save & Resume

All seller onboarding state is persisted to `localStorage` via Zustand persist. This allows closing the browser and resuming later.

**State shape (`useOnboardingStore`):**

```typescript
interface SellerOnboardingState {
  step: 1 | 2 | 3 | 4 | 5 | 6 | 7;
  phone: string;
  tenantSlug: string | null;
  businessForm: {
    name: string; slug: string; category: string;
    businessType: string; description: string; email: string;
  };
  addressForm: {
    line1: string; line2: string; city: string; state: string;
    pincode: string; lat: string; lng: string; deliveryRadius: number;
  };
  brandingForm: {
    logoEmoji: string; bannerGradient: string; accent: string; tagline: string;
  };
  kycForm: {
    aadhaar: string; pan: string; gst: string;
    documentUrls: string[]; storeImageUrl: string;
  };
}
```

**Resume behavior:**

- If `tenantSlug != null` and `step > 1` → show "Resume application" banner at Step 1.
- "Start fresh" → clears store, restarts.
- On resume → skip to last incomplete step.

---

## 5. Super Admin Approval Workflow

### Admin Portal View

URL: `admin.marketly.com/tenants`  
Filter: `status = PENDING_VERIFICATION` or `UNDER_REVIEW`

Admin sees per application:
- Store name, slug, category, owner email.
- Status badge.
- KYC fields (Aadhaar masked, PAN, GST).
- Document links.
- Timeline of status changes.

### Mark Under Review

```http
PATCH /api/v1/tenants/{slug}/review
Authorization: Bearer <adminToken>
X-Roles: SUPER_ADMIN
```

Sets `status = UNDER_REVIEW`. Notifies seller in-app.

### Approve

```http
PATCH /api/v1/tenants/{slug}/approve
Authorization: Bearer <adminToken>
X-Roles: SUPER_ADMIN
{
  "action": "APPROVE",
  "reviewNotes": "All documents verified."
}
```

**Result chain:**
1. `tenant.status = ACTIVE`, `onboarding_step = COMPLETE`.
2. `kyc_status` setting set to `APPROVED`.
3. Kafka: `tenant.seller.approved` → identity-service consumes → inserts `TENANT_OWNER` role in `user_tenant_roles`.
4. Kafka: `tenant.tenant.activated` → notification-service sends approval email.
5. Store URL `freshmart.marketly.com` becomes live.
6. Seller's next login token includes `TENANT_OWNER` role.

### Reject

```http
PATCH /api/v1/tenants/{slug}/approve
Authorization: Bearer <adminToken>
X-Roles: SUPER_ADMIN
{
  "action": "REJECT",
  "reviewNotes": "PAN document unclear. Please resubmit."
}
```

**Result chain:**
1. `tenant.status = REJECTED`, `onboarding_step = KYC_REJECTED`.
2. Kafka: `tenant.seller.rejected` → notification-service sends rejection email with reason.
3. Seller can resubmit KYC → same `POST /api/v1/tenants/{slug}/kyc` endpoint.

---

## 6. Status Flow Diagram

```
[Seller registers] → Step 1+2 (OTP/Account creation)
                         │
                         ▼
               Step 3 (Business Info)
               POST /api/v1/tenants
               tenant.status = DRAFT
               onboarding_step = MOBILE
                         │
                         ▼
               Step 4 (Address)
               PATCH /api/v1/tenants/{slug}/settings
               onboarding_step = ADDRESS
                         │
                         ▼
               Step 5 (Branding)
               PUT  /api/v1/tenants/{slug}
               PATCH /api/v1/tenants/{slug}/settings
               onboarding_step = BRANDING
                         │
                         ▼
               Step 6 (KYC Submission)
               POST /api/v1/tenants/{slug}/kyc
               tenant.status = PENDING_VERIFICATION
               onboarding_step = KYC_SUBMITTED
                         │
                         ▼
         [Super Admin reviews]
                         │
             ┌───────────┴──────────┐
             ▼                      ▼
         UNDER_REVIEW            (stays PENDING_VERIFICATION)
         (admin marks it)
             │
     ┌───────┴────────┐
     ▼                ▼
  APPROVE           REJECT
  status=ACTIVE     status=REJECTED
  role assigned     seller resubmits
  store live
```

---

## 7. Notifications Sent

| Trigger | Recipient | Channel | Content |
|---|---|---|---|
| Step 1: OTP sent | Seller | SMS | "Your Marketly OTP is XXXXXX. Valid 10 min." |
| Step 2: OTP verified (new user) | Seller | Email | "Your seller account is created. Continue onboarding." |
| Step 6: KYC submitted | Seller | Email | "KYC received. Review takes 1-2 business days." |
| Step 6: KYC submitted | Super Admin | In-App | "New seller application: Fresh Mart (freshmart)" |
| Approved | Seller | Email | "Your store Fresh Mart is live! Visit freshmart.marketly.com" |
| Rejected | Seller | Email | "KYC rejected: {reason}. Please resubmit at seller.marketly.com" |

---

## 8. API Endpoints Summary

| Method | Path | Step | Auth Required |
|---|---|---|---|
| `POST` | `/api/v1/auth/otp/send` | 1 | None |
| `POST` | `/api/v1/auth/otp/verify` | 2 | None |
| `GET` | `/api/v1/tenants/{slug}` | 3 (slug check) | None |
| `POST` | `/api/v1/tenants` | 3 | Bearer (seller JWT) |
| `PATCH` | `/api/v1/tenants/{slug}/settings` | 4, 5 | Bearer (TENANT_OWNER) |
| `PUT` | `/api/v1/tenants/{slug}` | 5 | Bearer (TENANT_OWNER) |
| `POST` | `/api/v1/tenants/{slug}/kyc` | 6 | Bearer (TENANT_OWNER) |
| `GET` | `/api/v1/tenants/{slug}` | 7 (status poll) | Bearer (TENANT_OWNER) |
| `PATCH` | `/api/v1/tenants/{slug}/review` | Admin | Bearer (SUPER_ADMIN) |
| `PATCH` | `/api/v1/tenants/{slug}/approve` | Admin | Bearer (SUPER_ADMIN) |
