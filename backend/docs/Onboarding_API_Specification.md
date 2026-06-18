# Onboarding API Specification

Base URL: `http://localhost:8080/api/v1`
All responses follow the envelope: `{ "success": true, "data": { ... } }`

---

## Authentication — OTP Flow

### Send OTP
```
POST /api/v1/auth/otp/send
Authorization: none (public)
```
**Request**
```json
{ "phone": "9876543210" }
```
**Response 200**
```json
{ "success": true, "data": { "message": "OTP sent", "expiresInSeconds": 600 } }
```
**Errors**
- `400` — phone missing or invalid format
- `429` — rate limit exceeded (5 attempts per 10 min)

---

### Verify OTP
```
POST /api/v1/auth/otp/verify
Authorization: none (public)
```
**Request**
```json
{
  "phone":      "9876543210",
  "otp":        "482931",
  "tenantSlug": "freshmart",   // optional — scopes CUSTOMER role to this store
  "role":       "CUSTOMER"     // optional — "CUSTOMER" (default) or "TENANT_OWNER"
}
```
**Response 200**
```json
{
  "success": true,
  "data": {
    "verified":     true,
    "isNewUser":    true,
    "accessToken":  "eyJ...",
    "refreshToken": "uuid-v4",
    "expiresIn":    900,
    "user": {
      "id":         "uuid",
      "phone":      "9876543210",
      "roles":      ["CUSTOMER"],
      "tenantSlug": "freshmart"
    }
  }
}
```
**Errors**
- `400` — OTP incorrect or expired
- `410` — OTP already used

---

## Customer Onboarding

### Complete Customer Profile
```
PATCH /api/v1/customers/me
Authorization: Bearer <accessToken>
X-Tenant-ID: <tenantId>
```
**Request**
```json
{
  "firstName":   "Jane",
  "lastName":    "Doe",
  "email":       "jane@example.com",
  "dateOfBirth": "1995-06-15"
}
```
**Response 200**
```json
{
  "success": true,
  "data": {
    "id":          "uuid",
    "userId":      "uuid",
    "firstName":   "Jane",
    "lastName":    "Doe",
    "email":       "jane@example.com",
    "phone":       "9876543210",
    "dateOfBirth": "1995-06-15",
    "loyaltyPoints": 0,
    "tier":        "BRONZE"
  }
}
```

---

### Add Delivery Address
```
POST /api/v1/customers/me/addresses
Authorization: Bearer <accessToken>
X-Tenant-ID: <tenantId>
```
**Request**
```json
{
  "label":     "Home",
  "line1":     "12, Lotus Apartments",
  "line2":     "Near City Mall",
  "city":      "Mumbai",
  "state":     "Maharashtra",
  "pincode":   "400001",
  "isDefault": true
}
```
**Response 201**
```json
{
  "success": true,
  "data": {
    "id":        "uuid",
    "label":     "Home",
    "line1":     "12, Lotus Apartments",
    "city":      "Mumbai",
    "state":     "Maharashtra",
    "pincode":   "400001",
    "isDefault": true
  }
}
```

---

## Seller Onboarding

### Create Store (Step 3 — Business Info)
```
POST /api/v1/tenants
Authorization: Bearer <accessToken>
X-User-ID:    <userId>    (injected by gateway from JWT)
```
**Request**
```json
{
  "slug":        "janes-bakery",
  "name":        "Jane's Bakery",
  "tagline":     "Fresh bakes every morning",
  "description": "Handcrafted cakes, cookies and breads since 2018.",
  "category":    "Bakery",
  "ownerEmail":  "jane@example.com"
}
```
**Response 201**
```json
{
  "success": true,
  "data": {
    "id":     "uuid",
    "slug":   "janes-bakery",
    "name":   "Jane's Bakery",
    "status": "PENDING",
    "onboardingStep": "BUSINESS"
  }
}
```

---

### Save Store Address (Step 4)
```
PATCH /api/v1/tenants/{slug}/settings
Authorization: Bearer <accessToken>
```
**Request**
```json
{
  "address":         "42, Baker Street, Andheri West",
  "city":            "Mumbai",
  "state":           "Maharashtra",
  "pincode":         "400053",
  "latitude":        "19.1197",
  "longitude":       "72.8464",
  "deliveryRadiusKm":"5",
  "onboardingStep":  "ADDRESS"
}
```
**Response 200** — `{ "success": true, "data": null }`

---

### Save Branding (Step 5)
```
PUT /api/v1/tenants/{slug}
Authorization: Bearer <accessToken>
```
**Request**
```json
{
  "logoUrl":    "data:image/svg+xml;base64,...",
  "bannerUrl":  "https://cdn.marketly.com/banners/uuid.jpg",
  "accentColor": "orange"
}
```
plus PATCH `/settings` for:
```json
{ "logoEmoji": "🍰", "bannerGradient": "linear-gradient(135deg, ...)", "onboardingStep": "BRANDING" }
```

---

### Submit KYC (Step 6)
```
POST /api/v1/tenants/{slug}/kyc
Authorization: Bearer <accessToken>
```
**Request**
```json
{
  "aadhaarNumber":  "1234 5678 9012",
  "panNumber":      "ABCDE1234F",
  "gstNumber":      "27ABCDE1234F1Z5",
  "documentUrls":   ["https://...", "https://..."],
  "storeImageUrl":  "https://..."
}
```
**Response 200**
```json
{
  "success": true,
  "data": {
    "slug":            "janes-bakery",
    "status":          "PENDING_VERIFICATION",
    "onboardingStep":  "KYC_SUBMITTED",
    "message":         "KYC submitted. Review takes 1-2 business days."
  }
}
```

---

### Get Seller Status
```
GET /api/v1/tenants/{slug}
Authorization: Bearer <accessToken>
```
**Response 200** — Full Tenant object including `status` and `onboardingStep`.

Possible `status` values during onboarding:
| Value | Meaning |
|---|---|
| `DRAFT` | Seller started but did not submit KYC |
| `PENDING_VERIFICATION` | KYC submitted, awaiting assignment |
| `UNDER_REVIEW` | Super admin is reviewing |
| `APPROVED` | Approved, activating store |
| `ACTIVE` | Store live at `/s/{slug}` |
| `REJECTED` | KYC failed — can resubmit |

---

## Admin — Seller Approval

### Approve / Reject Tenant
```
PATCH /api/v1/tenants/{slug}/approve
Authorization: Bearer <superAdminToken>
X-Roles: SUPER_ADMIN
```
**Request**
```json
{
  "action":       "APPROVE",
  "reviewNotes":  "Documents verified successfully."
}
```
or
```json
{
  "action":       "REJECT",
  "reviewNotes":  "PAN number mismatch. Please resubmit."
}
```
**Response 200**
```json
{
  "success": true,
  "data": {
    "slug":   "janes-bakery",
    "status": "ACTIVE"
  }
}
```
**Notes:**
- `APPROVE` → sets status = `ACTIVE`, fires `tenant.seller.approved` Kafka event, fires `TenantActivatedEvent`
- `REJECT` → sets status = `REJECTED`, fires `tenant.seller.rejected` Kafka event

---

## Kafka Event Topics (New)

### identity.user.otp-requested
```json
{
  "eventType": "identity.user.otp-requested",
  "eventVersion": "1.0",
  "occurredAt": "2026-06-16T10:00:00Z",
  "producedBy": "identity-service",
  "payload": {
    "phone":    "9876543210",
    "otpCode":  "482931",
    "channel":  "SMS",
    "expiresAt": "2026-06-16T10:10:00Z"
  }
}
```

### tenant.seller.kyc-submitted
```json
{
  "eventType": "tenant.seller.kyc-submitted",
  "payload": {
    "tenantId":   "uuid",
    "slug":       "janes-bakery",
    "ownerEmail": "jane@example.com",
    "submittedAt": "2026-06-16T10:00:00Z"
  }
}
```

### tenant.seller.approved
```json
{
  "eventType": "tenant.seller.approved",
  "payload": {
    "tenantId":    "uuid",
    "slug":        "janes-bakery",
    "storeName":   "Jane's Bakery",
    "ownerEmail":  "jane@example.com",
    "storeUrl":    "/s/janes-bakery",
    "approvedAt":  "2026-06-16T11:00:00Z"
  }
}
```

### tenant.seller.rejected
```json
{
  "eventType": "tenant.seller.rejected",
  "payload": {
    "tenantId":    "uuid",
    "slug":        "janes-bakery",
    "ownerEmail":  "jane@example.com",
    "reason":      "PAN number mismatch. Please resubmit.",
    "rejectedAt":  "2026-06-16T11:00:00Z"
  }
}
```
