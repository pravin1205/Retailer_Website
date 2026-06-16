# API Contracts — Marketly Backend Platform

## Design Standards

- **Versioning**: All APIs are prefixed `/api/v1/`. Breaking changes increment to `/api/v2/`.
- **Response wrapper**: Every response (success or error) is wrapped in a standard envelope.
- **Pagination**: Cursor or offset-based, consistent across all list endpoints.
- **Date format**: ISO 8601 — `2026-06-16T10:30:00Z`.
- **UUID format**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.
- **Auth**: `Authorization: Bearer <accessToken>` on all protected endpoints.
- **Tenant context**: Resolved from subdomain/JWT; available in gateway-injected `X-Tenant-ID` header.

---

## Standard Response Envelope

### Success

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-06-16T10:30:00Z",
    "requestId": "uuid",
    "version": "v1"
  }
}
```

### Paginated List

```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  },
  "meta": {
    "timestamp": "2026-06-16T10:30:00Z",
    "requestId": "uuid",
    "version": "v1"
  }
}
```

### Error

```json
{
  "success": false,
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "Product with id 'abc-123' not found.",
    "details": [],
    "timestamp": "2026-06-16T10:30:00Z",
    "requestId": "uuid",
    "path": "/api/v1/products/abc-123"
  }
}
```

### Validation Error (HTTP 422)

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed.",
    "details": [
      { "field": "email", "message": "must be a valid email address" },
      { "field": "password", "message": "must be at least 8 characters" }
    ]
  }
}
```

---

## HTTP Status Code Convention

| Status | Meaning | When |
|---|---|---|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST that creates a resource |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Malformed request body or query params |
| 401 | Unauthorized | Missing or invalid JWT |
| 403 | Forbidden | Valid JWT but insufficient permissions |
| 404 | Not Found | Resource does not exist (or tenant-scoped not visible) |
| 409 | Conflict | Duplicate resource (e.g. duplicate email, duplicate SKU) |
| 422 | Unprocessable Entity | Validation errors on fields |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unhandled server exception |

---

## Common Query Parameters (list endpoints)

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | int | 1 | Page number (1-indexed) |
| `size` | int | 20 | Items per page (max 100) |
| `sort` | string | `createdAt,desc` | Field + direction |
| `search` | string | — | Full-text search |
| `status` | string | — | Filter by status enum |

---

# Identity Service — `/api/v1/auth`

Base URL: `http://identity-service:8081` (internal) / routed via gateway at `/api/v1/auth`

---

## POST `/api/v1/auth/register`

Register a new user. Creates an `identity.users` record and sends a verification email.

**Request Body**
```json
{
  "email": "jane@freshmart.in",
  "password": "Secure@1234",
  "phone": "+919876543210",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

**Response 201**
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "jane@freshmart.in",
    "message": "Verification email sent."
  }
}
```

**Errors**
- `409 CONFLICT` — email already registered
- `422 VALIDATION_FAILED` — invalid email / weak password

---

## POST `/api/v1/auth/login`

Authenticate a user. Returns short-lived access token and long-lived refresh token.

**Request Body**
```json
{
  "email": "jane@freshmart.in",
  "password": "Secure@1234",
  "tenantSlug": "freshmart"
}
```

`tenantSlug` is optional. When present, the resulting JWT will carry `tenant_id` and the user's role within that tenant.

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "email": "jane@freshmart.in",
      "roles": ["CUSTOMER"],
      "tenantId": "uuid",
      "tenantSlug": "freshmart"
    }
  }
}
```

**Errors**
- `401` — invalid credentials
- `403` — account disabled or not verified

---

## POST `/api/v1/auth/refresh`

Exchange a refresh token for a new access + refresh token pair.

**Request Body**
```json
{
  "refreshToken": "eyJ..."
}
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 900
  }
}
```

**Errors**
- `401` — token expired or revoked

---

## POST `/api/v1/auth/logout`

Revoke the current refresh token.

**Headers**: `Authorization: Bearer <accessToken>`

**Request Body**
```json
{
  "refreshToken": "eyJ..."
}
```

**Response 204** — No content.

---

## POST `/api/v1/auth/password/change`

**Headers**: `Authorization: Bearer <accessToken>`

**Request Body**
```json
{
  "currentPassword": "OldPass@1",
  "newPassword": "NewPass@2"
}
```

**Response 204**

---

## POST `/api/v1/auth/password/forgot`

**Request Body**
```json
{ "email": "jane@freshmart.in" }
```

**Response 200**
```json
{ "success": true, "data": { "message": "Password reset email sent." } }
```

---

## POST `/api/v1/auth/password/reset`

**Request Body**
```json
{
  "token": "reset-token-from-email",
  "newPassword": "NewPass@2"
}
```

**Response 204**

---

## GET `/api/v1/auth/me`

Get current user profile.

**Headers**: `Authorization: Bearer <accessToken>`

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "jane@freshmart.in",
    "phone": "+919876543210",
    "isVerified": true,
    "roles": [
      { "tenantId": "uuid", "tenantSlug": "freshmart", "role": "CUSTOMER" }
    ],
    "createdAt": "2026-01-15T08:00:00Z"
  }
}
```

---

## GET `/api/v1/admin/users` (Super Admin only)

List all platform users with pagination.

**Required role**: `SUPER_ADMIN`

**Query params**: `page`, `size`, `sort`, `search` (email/phone), `role`

**Response 200** — paginated list of user summaries.

---

# Tenant Service — `/api/v1/tenants`

---

## GET `/api/v1/tenants`

List all tenants (super admin) or public tenant directory.

**Query params**: `page`, `size`, `sort`, `search`, `category`, `status`

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "slug": "freshmart",
      "name": "FreshMart",
      "tagline": "Farm-fresh, delivered in 10 minutes.",
      "category": "Grocery",
      "status": "ACTIVE",
      "logoUrl": "https://cdn.marketly.in/tenants/freshmart/logo.png",
      "accentColor": "emerald",
      "subscriptionPlan": "GROWTH",
      "rating": 4.7,
      "deliveryMinutes": 10,
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "pagination": { ... }
}
```

---

## POST `/api/v1/tenants`

Onboard a new tenant (store registration).

**Required role**: `SUPER_ADMIN` or self-registration endpoint.

**Request Body**
```json
{
  "slug": "freshmart",
  "name": "FreshMart",
  "tagline": "Farm-fresh, delivered in 10 minutes.",
  "description": "We source directly from local farms.",
  "category": "Grocery",
  "ownerEmail": "owner@freshmart.in",
  "subscriptionPlan": "FREE"
}
```

**Response 201**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "slug": "freshmart",
    "status": "PENDING",
    "message": "Tenant created. Awaiting activation."
  }
}
```

**Errors**
- `409` — slug already taken
- `422` — validation errors

---

## GET `/api/v1/tenants/{slug}`

Get tenant profile by slug.

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "slug": "freshmart",
    "name": "FreshMart",
    "tagline": "Farm-fresh, delivered in 10 minutes.",
    "description": "...",
    "category": "Grocery",
    "status": "ACTIVE",
    "logoUrl": "...",
    "bannerUrl": "...",
    "accentColor": "emerald",
    "subscriptionPlan": "GROWTH",
    "subscriptionExpiresAt": "2027-01-01T00:00:00Z",
    "settings": {
      "deliveryMinutes": 30,
      "deliveryRadiusKm": 5,
      "minOrderValue": 99,
      "currency": "INR",
      "phone": "+911234567890",
      "address": "123 MG Road, Bangalore",
      "hours": "8 AM – 10 PM"
    },
    "domains": [
      { "domain": "freshmart.marketly.com", "isPrimary": true }
    ]
  }
}
```

---

## PUT `/api/v1/tenants/{slug}`

Update tenant profile.

**Required role**: `TENANT_OWNER` (own tenant) or `SUPER_ADMIN`

**Request Body** — partial update allowed (all fields optional):
```json
{
  "name": "FreshMart Plus",
  "tagline": "Updated tagline",
  "description": "...",
  "logoUrl": "...",
  "bannerUrl": "...",
  "accentColor": "orange"
}
```

**Response 200** — full updated tenant object.

---

## PATCH `/api/v1/tenants/{slug}/settings`

Update key-value settings.

**Required role**: `TENANT_OWNER` or `STORE_MANAGER`

**Request Body**
```json
{
  "settings": {
    "deliveryMinutes": 20,
    "minOrderValue": 149,
    "deliveryCharge": 30
  }
}
```

**Response 200**

---

## POST `/api/v1/tenants/{slug}/domains`

Add a custom domain.

**Required role**: `TENANT_OWNER`

**Request Body**
```json
{ "domain": "shop.freshmart.in", "isPrimary": false }
```

**Response 201**

---

## PATCH `/api/v1/tenants/{slug}/status`

Activate, suspend, or close a tenant.

**Required role**: `SUPER_ADMIN`

**Request Body**
```json
{ "status": "ACTIVE", "reason": "Verification complete." }
```

**Response 200**

---

# Product Service — `/api/v1/products`, `/api/v1/categories`

---

## GET `/api/v1/categories`

List categories for the current tenant (resolved from JWT / subdomain).

**Query params**: `parentId` (filter by parent; omit for roots)

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Fruits & Vegetables",
      "slug": "fruits-vegetables",
      "description": "Fresh produce",
      "imageUrl": "...",
      "parentId": null,
      "sortOrder": 1,
      "isActive": true,
      "children": [
        { "id": "uuid", "name": "Leafy Greens", "slug": "leafy-greens", "parentId": "uuid", ... }
      ]
    }
  ]
}
```

---

## POST `/api/v1/categories`

**Required role**: `TENANT_OWNER` or `STORE_MANAGER`

**Request Body**
```json
{
  "name": "Fruits & Vegetables",
  "slug": "fruits-vegetables",
  "description": "Fresh produce daily",
  "imageUrl": "...",
  "parentId": null,
  "sortOrder": 1
}
```

**Response 201** — created category object.

---

## PUT `/api/v1/categories/{id}`

**Required role**: `TENANT_OWNER` or `STORE_MANAGER`

**Request Body** — same as POST. **Response 200**.

---

## DELETE `/api/v1/categories/{id}`

Soft-delete. Will fail (409) if active products reference this category.

**Required role**: `TENANT_OWNER` or `STORE_MANAGER`

**Response 204**

---

## GET `/api/v1/products`

List products for the current tenant.

**Query params**:
| Param | Description |
|---|---|
| `page`, `size`, `sort` | Pagination |
| `search` | Full-text on name/brand |
| `categorySlug` | Filter by category |
| `tag` | Filter by tag (trending, new, bestseller) |
| `minPrice`, `maxPrice` | Price range |
| `inStock` | `true` = only in-stock products |
| `isActive` | `true`/`false` |
| `isFeatured` | `true`/`false` |

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Organic Apples",
      "brand": "FarmFresh",
      "description": "...",
      "sku": "FF-APPLE-001",
      "barcode": "8901234567890",
      "unit": "1kg",
      "price": 129.00,
      "mrp": 159.00,
      "taxRate": 0,
      "isActive": true,
      "isFeatured": false,
      "tags": ["organic", "fresh"],
      "attributes": { "origin": "Himachal Pradesh", "organic": true },
      "images": [{ "url": "...", "alt": "Organic Apples", "sortOrder": 1 }],
      "category": { "id": "uuid", "name": "Fruits & Vegetables", "slug": "fruits-vegetables" },
      "inventory": { "quantityOnHand": 45, "quantityReserved": 3, "isInStock": true },
      "variants": [],
      "rating": 4.5,
      "reviewCount": 23,
      "createdAt": "2026-01-10T00:00:00Z"
    }
  ],
  "pagination": { ... }
}
```

---

## POST `/api/v1/products`

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Request Body**
```json
{
  "name": "Organic Apples",
  "brand": "FarmFresh",
  "description": "Hand-picked from Himachal orchards.",
  "categoryId": "uuid",
  "sku": "FF-APPLE-001",
  "barcode": "8901234567890",
  "unit": "1kg",
  "price": 129.00,
  "mrp": 159.00,
  "costPrice": 85.00,
  "taxRate": 0,
  "tags": ["organic", "fresh"],
  "attributes": { "origin": "Himachal Pradesh", "organic": true },
  "images": [{ "url": "https://cdn.../apple.jpg", "alt": "Organic Apples", "sortOrder": 1 }],
  "initialStock": 100,
  "lowStockThreshold": 10
}
```

**Response 201** — full product object.

**Errors**
- `409` — duplicate SKU within tenant
- `422` — price < 0, missing required fields

---

## GET `/api/v1/products/{id}`

**Response 200** — full product object including variants and inventory.

---

## PUT `/api/v1/products/{id}`

Full update. **Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Request Body** — same as POST minus `initialStock`. **Response 200**.

---

## PATCH `/api/v1/products/{id}`

Partial update (e.g. toggle `isActive`). **Response 200**.

---

## DELETE `/api/v1/products/{id}`

Soft-delete. **Required role**: `TENANT_OWNER`, `STORE_MANAGER`. **Response 204**.

---

## POST `/api/v1/products/bulk`

Bulk create/update via JSON array or CSV upload.

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Request Body**
```json
{
  "products": [ { ...same as POST... }, ... ]
}
```

**Response 202** (async processing)
```json
{
  "success": true,
  "data": {
    "jobId": "uuid",
    "message": "Bulk import queued. Check /api/v1/products/bulk/{jobId} for status."
  }
}
```

---

## GET `/api/v1/products/{id}/variants`

**Response 200** — list of variants.

---

## POST `/api/v1/products/{id}/variants`

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Request Body**
```json
{
  "label": "500g",
  "sku": "FF-APPLE-500G",
  "priceDelta": -30.00,
  "attributes": { "weight": "500g" },
  "initialStock": 50
}
```

**Response 201**

---

## GET `/api/v1/products/{id}/inventory`

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`, `STORE_STAFF`

**Response 200**
```json
{
  "success": true,
  "data": {
    "productId": "uuid",
    "warehouses": [
      {
        "warehouseCode": "DEFAULT",
        "quantityOnHand": 45,
        "quantityReserved": 3,
        "quantityAvailable": 42,
        "lowStockThreshold": 10,
        "reorderPoint": 15
      }
    ]
  }
}
```

---

## PATCH `/api/v1/products/{id}/inventory`

Manual inventory adjustment.

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`, `STORE_STAFF`

**Request Body**
```json
{
  "warehouseCode": "DEFAULT",
  "delta": 50,
  "reason": "RESTOCK",
  "note": "Received new stock from supplier."
}
```

**Response 200** — updated inventory.

---

# Order Service — `/api/v1/cart`, `/api/v1/orders`, `/api/v1/coupons`

---

## GET `/api/v1/cart`

Get current customer's active cart.

**Required role**: `CUSTOMER` (or guest with session cookie)

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "items": [
      {
        "id": "uuid",
        "productId": "uuid",
        "variantId": null,
        "productName": "Organic Apples",
        "unitPrice": 129.00,
        "quantity": 2,
        "lineTotal": 258.00,
        "imageUrl": "..."
      }
    ],
    "couponCode": null,
    "subtotal": 258.00,
    "discount": 0,
    "deliveryCharge": 30.00,
    "total": 288.00,
    "itemCount": 2
  }
}
```

---

## POST `/api/v1/cart/items`

Add item to cart. Creates cart if none exists.

**Request Body**
```json
{
  "productId": "uuid",
  "variantId": null,
  "quantity": 2
}
```

**Response 200** — updated cart.

**Errors**
- `422` — quantity exceeds available stock

---

## PATCH `/api/v1/cart/items/{itemId}`

Update quantity.

**Request Body**
```json
{ "quantity": 3 }
```

**Response 200** — updated cart. Setting `quantity: 0` removes the item.

---

## DELETE `/api/v1/cart/items/{itemId}`

**Response 200** — updated cart.

---

## POST `/api/v1/cart/coupon`

Apply a coupon to the cart.

**Request Body**
```json
{ "code": "SAVE20" }
```

**Response 200** — cart with discount applied.

**Errors**
- `422` — invalid coupon, expired, below minimum order, already used

---

## DELETE `/api/v1/cart/coupon`

Remove applied coupon. **Response 200**.

---

## POST `/api/v1/orders`

Checkout — convert cart to order.

**Required role**: `CUSTOMER`

**Request Body**
```json
{
  "cartId": "uuid",
  "addressId": "uuid",
  "deliverySlot": "Today, 6 PM – 8 PM",
  "paymentMethod": "UPI",
  "notes": "Please leave at door."
}
```

**Response 201**
```json
{
  "success": true,
  "data": {
    "orderId": "uuid",
    "orderNumber": "FM-20260616-0042",
    "status": "PLACED",
    "total": 288.00,
    "paymentMethod": "UPI",
    "paymentDeadlineSeconds": 300
  }
}
```

---

## GET `/api/v1/orders`

List orders.

- Customer: sees own orders for this tenant.
- `STORE_MANAGER`/`TENANT_OWNER`: sees all tenant orders.
- `SUPER_ADMIN`: sees all orders (must pass `tenantId` query param).

**Query params**: `page`, `size`, `sort`, `status`, `search` (order number), `dateFrom`, `dateTo`

**Response 200** — paginated order list.

---

## GET `/api/v1/orders/{id}`

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "orderNumber": "FM-20260616-0042",
    "status": "OUT_FOR_DELIVERY",
    "items": [
      {
        "productId": "uuid",
        "productName": "Organic Apples",
        "variantLabel": null,
        "sku": "FF-APPLE-001",
        "unitPrice": 129.00,
        "quantity": 2,
        "lineTotal": 258.00,
        "imageUrl": "..."
      }
    ],
    "subtotal": 258.00,
    "discountAmount": 0,
    "deliveryCharge": 30.00,
    "taxAmount": 0,
    "totalAmount": 288.00,
    "couponCode": null,
    "deliverySlot": "Today, 6 PM – 8 PM",
    "deliveryAddress": {
      "label": "Home",
      "line1": "42, MG Road",
      "city": "Bangalore",
      "pincode": "560001"
    },
    "paymentMethod": "UPI",
    "payment": {
      "status": "COMPLETED",
      "paidAt": "2026-06-16T10:35:00Z"
    },
    "placedAt": "2026-06-16T10:30:00Z",
    "deliveredAt": null
  }
}
```

---

## PATCH `/api/v1/orders/{id}/status`

Update order status (store side).

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`, `STORE_STAFF`

**Request Body**
```json
{
  "status": "PACKING",
  "note": "Preparing your order."
}
```

Valid transitions:
- `PLACED → CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED`
- `PLACED | CONFIRMED | PACKING → CANCELLED`

**Response 200**

---

## POST `/api/v1/orders/{id}/cancel`

Customer cancellation.

**Required role**: `CUSTOMER` (own orders, within cancellation window)

**Request Body**
```json
{ "reason": "Changed my mind." }
```

**Response 200**

---

## GET `/api/v1/coupons`

List active coupons for this tenant.

**Response 200** — list of available coupons (public fields only — no usage limits exposed).

---

## POST `/api/v1/coupons`

Create a coupon.

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Request Body**
```json
{
  "code": "SAVE20",
  "description": "20% off on orders above ₹300",
  "type": "PERCENT",
  "value": 20,
  "minOrderValue": 300,
  "maxDiscount": 150,
  "usageLimit": 500,
  "perUserLimit": 1,
  "validFrom": "2026-06-16T00:00:00Z",
  "validUntil": "2026-07-16T23:59:59Z"
}
```

**Response 201**

---

## PATCH `/api/v1/coupons/{id}`

Update or deactivate a coupon. **Required role**: `TENANT_OWNER`, `STORE_MANAGER`. **Response 200**.

---

# Customer Service — `/api/v1/customers`

---

## GET `/api/v1/customers/me`

Get current customer's profile for this tenant.

**Required role**: `CUSTOMER`

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane@example.com",
    "phone": "+919876543210",
    "loyaltyPoints": 250,
    "tier": "SILVER",
    "addresses": [
      {
        "id": "uuid",
        "label": "Home",
        "line1": "42, MG Road",
        "city": "Bangalore",
        "pincode": "560001",
        "isDefault": true
      }
    ]
  }
}
```

---

## PATCH `/api/v1/customers/me`

Update profile.

**Request Body**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "phone": "+919876543210",
  "dateOfBirth": "1995-04-12"
}
```

**Response 200**

---

## POST `/api/v1/customers/me/addresses`

**Request Body**
```json
{
  "label": "Office",
  "line1": "Brigade Road",
  "city": "Bangalore",
  "pincode": "560025",
  "isDefault": false
}
```

**Response 201**

---

## PUT `/api/v1/customers/me/addresses/{id}`

**Response 200**

---

## DELETE `/api/v1/customers/me/addresses/{id}`

Soft-delete. **Response 204**.

---

## GET `/api/v1/customers` (Manager/Owner view)

List all customers for the tenant.

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Query params**: `page`, `size`, `search`, `tier`

**Response 200** — paginated customer list.

---

## GET `/api/v1/customers/{id}` (Manager view)

**Required role**: `TENANT_OWNER`, `STORE_MANAGER`

**Response 200** — full customer profile + order summary.

---

# OpenAPI 3.0 Specification (excerpt)

```yaml
openapi: "3.0.3"
info:
  title: "Marketly API"
  version: "1.0.0"
  description: "Multi-tenant retail commerce platform API"
  contact:
    name: "Marketly Engineering"
    email: "eng@marketly.in"

servers:
  - url: "https://api.marketly.in/api/v1"
    description: "Production"
  - url: "http://localhost:8080/api/v1"
    description: "Local development"

security:
  - bearerAuth: []

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    ApiResponse:
      type: object
      properties:
        success: { type: boolean }
        data: { type: object }
        meta:
          type: object
          properties:
            timestamp: { type: string, format: date-time }
            requestId: { type: string, format: uuid }
            version: { type: string }

    ApiError:
      type: object
      properties:
        success: { type: boolean, example: false }
        error:
          type: object
          properties:
            code: { type: string }
            message: { type: string }
            details: { type: array, items: { type: object } }
            timestamp: { type: string, format: date-time }
            requestId: { type: string, format: uuid }

    Pagination:
      type: object
      properties:
        page: { type: integer }
        size: { type: integer }
        totalElements: { type: integer, format: int64 }
        totalPages: { type: integer }
        hasNext: { type: boolean }
        hasPrevious: { type: boolean }

    Product:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        brand: { type: string }
        description: { type: string }
        sku: { type: string }
        barcode: { type: string }
        unit: { type: string }
        price: { type: number, format: double }
        mrp: { type: number, format: double }
        taxRate: { type: number, format: double }
        isActive: { type: boolean }
        isFeatured: { type: boolean }
        tags: { type: array, items: { type: string } }
        attributes: { type: object, additionalProperties: true }
        images:
          type: array
          items:
            type: object
            properties:
              url: { type: string }
              alt: { type: string }
              sortOrder: { type: integer }

paths:
  /products:
    get:
      summary: List products
      operationId: listProducts
      tags: [Products]
      parameters:
        - name: page
          in: query
          schema: { type: integer, default: 1 }
        - name: size
          in: query
          schema: { type: integer, default: 20, maximum: 100 }
        - name: search
          in: query
          schema: { type: string }
        - name: categorySlug
          in: query
          schema: { type: string }
        - name: tag
          in: query
          schema: { type: string }
        - name: inStock
          in: query
          schema: { type: boolean }
      responses:
        "200":
          description: Paginated product list
          content:
            application/json:
              schema:
                allOf:
                  - $ref: "#/components/schemas/ApiResponse"
                  - type: object
                    properties:
                      data:
                        type: array
                        items:
                          $ref: "#/components/schemas/Product"
                      pagination:
                        $ref: "#/components/schemas/Pagination"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema: { $ref: "#/components/schemas/ApiError" }
```

> The full OpenAPI spec will be auto-generated at runtime via SpringDoc OpenAPI at:
> `http://localhost:{port}/v3/api-docs` (JSON) and `http://localhost:{port}/swagger-ui.html` (UI)
