# Onboarding Impact Analysis

## 1. Existing Assets Reused

### Backend — No Changes Required

| Asset | Service | Endpoint / Class | Reuse Reason |
|---|---|---|---|
| User registration | identity-service | `POST /api/v1/auth/register` | Supports email+password; reused as fallback and for seller account creation |
| JWT login | identity-service | `POST /api/v1/auth/login` | Issues access+refresh tokens after OTP verify |
| Token refresh | identity-service | `POST /api/v1/auth/refresh` | Unchanged |
| Customer profile update | customer-service | `PATCH /api/v1/customers/me` | `CustomerRequest` already has firstName, lastName, email, phone, dateOfBirth |
| Address management | customer-service | `POST/PUT/DELETE /api/v1/customers/me/addresses` | `Address` entity has line1, line2, city, state, pincode, lat, lng, isDefault |
| Tenant creation | tenant-service | `POST /api/v1/tenants` | `CreateTenantRequest` has slug, name, category, ownerEmail |
| Tenant profile update | tenant-service | `PUT /api/v1/tenants/{slug}` | logoUrl, bannerUrl, accentColor already supported |
| Tenant settings | tenant-service | `PATCH /api/v1/tenants/{slug}/settings` | Key-value store for deliveryRadius, phone, address, hours |
| Tenant status change | tenant-service | `PATCH /api/v1/tenants/{slug}/status` | PENDING→ACTIVE already wired with Kafka event |
| Notification dispatch | notification-service | `NotificationService.dispatch()` | Send OTP/welcome/approval notifications |
| Welcome email | notification-service | `NotificationEventConsumer.onUserRegistered()` | Fires on `identity.user.registered` Kafka topic |
| Tenant activated email | notification-service | `NotificationEventConsumer` (tenant.tenant.activated) | Fires on store approval |
| Kafka user event | identity-service | `UserEventProducer.publishUserRegistered()` | Customer profile auto-created in customer-service |
| Kafka tenant events | tenant-service | `TenantEventProducer` | TenantCreated + TenantActivated already fire |
| Redis client | identity-service | Spring Data Redis (configured in application.yml) | Used for OTP storage with TTL |
| RBAC roles | identity-service | `identity.roles` table (V4__seed_roles.sql) | CUSTOMER, TENANT_OWNER, SUPER_ADMIN already seeded |
| `UserTenantRole` | identity-service | `user_tenant_roles` table | Seller gets TENANT_OWNER role inserted here |
| `TenantAwareEntity` | common | `TenantAwareEntity.java` | Tenant-scoped entities extend this; no change |
| `BaseEvent` | common | `BaseEvent.java` | All new Kafka events extend this |

### Frontend — No Changes Required

| Asset | File | Reuse Reason |
|---|---|---|
| `useAuthStore` | `src/stores/index.ts` | login(), register() work as-is |
| `http` axios client | `src/lib/api/http-client.ts` | Bearer token, refresh, envelope unwrap |
| `withTenant()` | `src/lib/api/http-client.ts` | X-Tenant-ID header injection |
| `PageTransition`, `FadeIn` | `src/components/motion/PageTransition.tsx` | Step animations |
| All shadcn/ui components | `src/components/ui/` | Button, Input, Label, Textarea, Select, etc. |
| Card/Field pattern | `auth.login.tsx`, `settings.tsx` | `rounded-3xl border bg-card p-7 shadow-card` |
| Form validation pattern | `auth.login.tsx`, `auth.signup.tsx` | useState + onSubmit + toast pattern |
| `input-otp` package | `package.json` | Already installed — used for 6-digit OTP input |
| `sonner` toasts | Throughout | `toast.success()`, `toast.error()` |
| `framer-motion` | Throughout | Already installed for step transitions |

---

## 2. New Backend Required

### identity-service

| Item | Type | Reason |
|---|---|---|
| `OtpService.java` | Service | OTP generation, Redis storage (SHA-256 hashed, 10-min TTL), validation |
| `OtpController.java` | Controller | `POST /api/v1/auth/otp/send` and `POST /api/v1/auth/otp/verify` |
| `OtpRequest.java` | DTO | `{ phone }` |
| `OtpVerifyRequest.java` | DTO | `{ phone, otp, tenantSlug?, role? }` |
| `OtpVerifyResponse.java` | DTO | `{ verified, isNewUser, accessToken?, refreshToken?, userId? }` |
| `UserEventProducer.publishOtpRequested()` | Method addition | Publishes `identity.user.otp-requested` → notification-service sends OTP |

### tenant-service

| Item | Type | Reason |
|---|---|---|
| `V7__tenant_onboarding.sql` | Flyway migration | Adds `onboarding_step VARCHAR(50)` to `tenant.tenants` |
| `KycRequest.java` | DTO | `{ aadhaarNumber, panNumber, gstNumber?, documentUrls[], storeImageUrl? }` |
| `SellerOnboardingController.java` | Controller | `POST /api/v1/tenants/{slug}/kyc` + `PATCH /api/v1/tenants/{slug}/approve` |
| `TenantEventProducer.publishKycSubmitted()` | Method addition | Publishes `tenant.seller.kyc-submitted` |
| `TenantEventProducer.publishSellerApproved/Rejected()` | Method addition | Approval/rejection Kafka events |

### api-gateway

| Item | Type | Reason |
|---|---|---|
| `application.yml` route addition | Config | Route `/api/v1/auth/otp/**` → identity-service:8081 |

### notification-service

| Item | Type | Reason |
|---|---|---|
| `NotificationEventConsumer.onOtpRequested()` | Kafka listener | Sends OTP SMS/email from `identity.user.otp-requested` |
| `NotificationEventConsumer.onKycSubmitted()` | Kafka listener | Sends KYC receipt from `tenant.seller.kyc-submitted` |
| `NotificationEventConsumer.onSellerApproved()` | Kafka listener | Sends approval email from `tenant.seller.approved` |
| `NotificationEventConsumer.onSellerRejected()` | Kafka listener | Sends rejection email from `tenant.seller.rejected` |

---

## 3. New Frontend Required

| File | Type | Purpose |
|---|---|---|
| `src/routes/onboarding.tsx` | Route layout | Outlet wrapper (matches `/auth.tsx` pattern) |
| `src/routes/onboarding.customer.tsx` | Route | 5-step customer onboarding wizard |
| `src/routes/onboarding.seller.tsx` | Route | 7-step seller onboarding wizard |
| `src/routes/onboarding.seller.status.tsx` | Route | Post-KYC approval waiting page |
| `src/components/onboarding/OnboardingStepper.tsx` | Component | Step progress indicator |
| `src/components/onboarding/OtpInput.tsx` | Component | 6-digit OTP entry (uses input-otp) |
| `useOnboardingStore` in `src/stores/index.ts` | Zustand store | Seller draft-save and resume |
| `api.sendOtp()`, `api.verifyOtp()`, `api.submitKyc()` | API additions | New endpoint calls |
| `OnboardingStep`, `SellerOnboardingState` types | Type additions | in `src/lib/types/index.ts` |

---

## 4. Modified Files

### Backend
| File | Change |
|---|---|
| `identity-service/SecurityConfig.java` | Add `/api/v1/auth/otp/**` to permitAll |
| `identity-service/UserEventProducer.java` | Add `publishOtpRequested()` |
| `tenant-service/TenantEventProducer.java` | Add seller event publishers |
| `notification-service/NotificationEventConsumer.java` | Add 4 new Kafka listeners |
| `api-gateway/application.yml` | Add OTP route |

### Frontend
| File | Change |
|---|---|
| `src/lib/api/index.ts` | Add sendOtp, verifyOtp, submitKyc, getSellerStatus |
| `src/lib/types/index.ts` | Add onboarding types |
| `src/stores/index.ts` | Add useOnboardingStore |
| `src/routes/index.tsx` | Wire "Become a partner" to /onboarding/seller |
| `src/routes/admin.tenants.tsx` | Add approval action buttons for pending tenants |

---

## 5. Database Impact

### New Column
```sql
-- tenant.tenants
ALTER TABLE tenant.tenants
  ADD COLUMN IF NOT EXISTS onboarding_step VARCHAR(50) DEFAULT 'MOBILE';
```
**Justification:** Tracks seller wizard progress for resume-later. Cannot go into TenantSetting because it needs to survive before slug is known, and it's a first-class lifecycle field.

### Reused Tables (No Schema Change)
| Table | How Reused |
|---|---|
| `identity.users` | phone field already exists; used as OTP identity |
| `identity.user_tenant_roles` | TENANT_OWNER role row inserted on seller approval |
| `tenant.tenants` | status field reused for PENDING/UNDER_REVIEW/APPROVED/REJECTED/ACTIVE |
| `tenant.tenant_settings` | KYC fields stored as key-value (aadhaar_number, pan_number, gst_number, kyc_status) |
| `customer.customers` | Profile completion writes to existing columns |
| `customer.addresses` | Address setup uses existing Address entity |
| `notification.notifications` | All OTP/approval notifications stored here |

### No New Tables Required
OTP storage uses Redis with TTL — no database row needed.

---

## 6. Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| OTP brute-force | Medium | Rate-limit at API gateway (5 attempts per phone per 10 min), lock after 5 failures |
| KYC document storage | Low | Documents stored as URLs (base64 or CDN links in TenantSetting) — no binary in DB |
| Slug collision during seller onboarding | Low | Existing `DuplicateResourceException` in TenantService covers this |
| Guest checkout broken by onboarding flow | None | Onboarding is a separate route tree — no existing routes touched |
| Circular dependency (identity→notification) | Low | Already async via Kafka — OTP event follows same pattern |
| `onboarding_step` column missing in existing tenants | Low | `DEFAULT 'MOBILE'` in migration handles existing rows |
