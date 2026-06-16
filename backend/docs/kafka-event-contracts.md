# Kafka Event Contracts — Marketly Backend Platform

## Naming Conventions

### Topic names

Pattern: `{domain}.{entity}.{event}`

- All lowercase, dot-separated.
- `{domain}` = bounded context / service domain.
- `{entity}` = aggregate root being acted upon.
- `{event}` = past-tense event name.

Examples:
```
identity.user.registered
identity.user.password-changed
tenant.tenant.created
tenant.tenant.activated
tenant.tenant.suspended
product.product.created
product.product.updated
product.product.deleted
product.inventory.updated
product.inventory.low-stock-alert
order.cart.checkout-started
order.order.created
order.order.confirmed
order.order.status-changed
order.payment.completed
order.payment.failed
order.order.cancelled
order.order.returned
customer.customer.created
notification.notification.requested
```

### Consumer group names

Pattern: `{service-name}-{purpose}-group`

```
notification-service-order-group
analytics-service-order-group
product-service-order-group        (inventory deduction)
customer-service-order-group       (loyalty points)
notification-service-identity-group
notification-service-inventory-group
```

---

## Infrastructure Config

```
Kafka brokers:       kafka:9092
Replication factor:  3 (production), 1 (local dev)
Min ISR:             2 (production)
Retention:           7 days (default)
Partitions:          12 (default — tuned per topic)
Compression:         snappy
```

### Topic-specific config

| Topic | Partitions | Retention | Notes |
|---|---|---|---|
| `order.order.created` | 24 | 30 days | High volume, fan-out to 4 consumers |
| `order.payment.completed` | 12 | 90 days | Financial audit trail |
| `product.inventory.low-stock-alert` | 6 | 7 days | Low volume |
| `notification.notification.requested` | 12 | 1 day | Transient — processed quickly |

---

## Envelope (all events)

Every event is serialized as JSON and shares this outer envelope:

```json
{
  "eventId": "uuid-v4",
  "eventType": "order.order.created",
  "eventVersion": "1.0",
  "occurredAt": "2026-06-16T10:30:00.000Z",
  "tenantId": "uuid",
  "correlationId": "uuid",
  "causationId": "uuid-of-command-that-caused-this",
  "producedBy": "order-service",
  "payload": { ... }
}
```

| Field | Type | Description |
|---|---|---|
| `eventId` | UUID | Unique per event — used for idempotency |
| `eventType` | string | Exact topic name |
| `eventVersion` | string | Payload schema version (semver) |
| `occurredAt` | ISO 8601 | When the domain event occurred |
| `tenantId` | UUID | Always present for tenant-scoped events |
| `correlationId` | UUID | Trace ID propagated from the originating HTTP request |
| `causationId` | UUID | ID of the event or command that triggered this one |
| `producedBy` | string | Originating service name |
| `payload` | object | Event-specific data (see below) |

---

## Event Payloads

---

### `identity.user.registered`

Produced by: **Identity Service**
Consumed by: **Notification Service**, **Customer Service**

```json
{
  "eventType": "identity.user.registered",
  "payload": {
    "userId": "uuid",
    "email": "jane@example.com",
    "phone": "+919876543210",
    "firstName": "Jane",
    "lastName": "Doe",
    "tenantId": "uuid",
    "tenantSlug": "freshmart",
    "registrationSource": "WEB"
  }
}
```

**Consumer actions**:
- `notification-service`: Send welcome email.
- `customer-service`: Create `customer.customers` record for this tenant.

---

### `identity.user.password-changed`

Produced by: **Identity Service**
Consumed by: **Notification Service**

```json
{
  "payload": {
    "userId": "uuid",
    "email": "jane@example.com",
    "ipAddress": "103.x.x.x",
    "changedAt": "2026-06-16T10:00:00Z"
  }
}
```

**Consumer action**: Send security alert email.

---

### `tenant.tenant.created`

Produced by: **Tenant Service**
Consumed by: **Notification Service**

```json
{
  "payload": {
    "tenantId": "uuid",
    "slug": "freshmart",
    "name": "FreshMart",
    "category": "Grocery",
    "ownerUserId": "uuid",
    "ownerEmail": "owner@freshmart.in",
    "subscriptionPlan": "FREE"
  }
}
```

**Consumer action**: Send onboarding email to owner.

---

### `tenant.tenant.activated`

Produced by: **Tenant Service**
Consumed by: **Notification Service**

```json
{
  "payload": {
    "tenantId": "uuid",
    "slug": "freshmart",
    "ownerEmail": "owner@freshmart.in",
    "activatedAt": "2026-06-16T12:00:00Z"
  }
}
```

---

### `product.product.created`

Produced by: **Product Service**
Consumed by: **Analytics Service** (catalog size metric)

```json
{
  "payload": {
    "productId": "uuid",
    "tenantId": "uuid",
    "name": "Organic Apples",
    "categoryId": "uuid",
    "categoryName": "Fruits & Vegetables",
    "sku": "FF-APPLE-001",
    "price": 129.00,
    "initialStock": 100,
    "createdBy": "uuid"
  }
}
```

---

### `product.inventory.updated`

Produced by: **Product Service** (on any stock change)
Consumed by: **Analytics Service**, **Notification Service** (low-stock check)

```json
{
  "payload": {
    "inventoryId": "uuid",
    "productId": "uuid",
    "variantId": null,
    "tenantId": "uuid",
    "warehouseCode": "DEFAULT",
    "previousQuantity": 15,
    "newQuantity": 8,
    "delta": -7,
    "reason": "SALE",
    "referenceId": "order-uuid",
    "referenceType": "ORDER"
  }
}
```

---

### `product.inventory.low-stock-alert`

Produced by: **Product Service** (when `quantity_on_hand <= low_stock_threshold` after an update)
Consumed by: **Notification Service**

```json
{
  "payload": {
    "productId": "uuid",
    "productName": "Organic Apples",
    "tenantId": "uuid",
    "warehouseCode": "DEFAULT",
    "currentQuantity": 4,
    "threshold": 5,
    "sku": "FF-APPLE-001",
    "ownerEmail": "owner@freshmart.in"
  }
}
```

**Consumer action**: Send low-stock alert to store owner (email + in-app notification).

---

### `order.order.created`

Produced by: **Order Service** immediately after order row is persisted.
Consumed by: **Notification Service**, **Analytics Service**, **Product Service** (inventory deduction)

```json
{
  "payload": {
    "orderId": "uuid",
    "orderNumber": "FM-20260616-0042",
    "tenantId": "uuid",
    "tenantSlug": "freshmart",
    "customerId": "uuid",
    "customerEmail": "jane@example.com",
    "customerPhone": "+919876543210",
    "status": "PLACED",
    "items": [
      {
        "productId": "uuid",
        "variantId": null,
        "productName": "Organic Apples",
        "sku": "FF-APPLE-001",
        "quantity": 2,
        "unitPrice": 129.00,
        "lineTotal": 258.00
      }
    ],
    "subtotal": 258.00,
    "discountAmount": 0,
    "deliveryCharge": 30.00,
    "taxAmount": 0,
    "totalAmount": 288.00,
    "couponCode": null,
    "paymentMethod": "UPI",
    "deliverySlot": "Today, 6 PM – 8 PM",
    "deliveryAddress": {
      "line1": "42, MG Road",
      "city": "Bangalore",
      "pincode": "560001"
    },
    "placedAt": "2026-06-16T10:30:00Z"
  }
}
```

**Consumer actions**:
- `notification-service` (group `notification-service-order-group`): Send "Order placed" confirmation to customer.
- `notification-service`: Send new order alert to store owner.
- `product-service` (group `product-service-order-group`): Deduct `quantity_reserved` in inventory for each item.
- `analytics-service` (group `analytics-service-order-group`): Increment daily order count and revenue aggregates.

---

### `order.order.confirmed`

Produced by: **Order Service** when store confirms the order.
Consumed by: **Notification Service**

```json
{
  "payload": {
    "orderId": "uuid",
    "orderNumber": "FM-20260616-0042",
    "tenantId": "uuid",
    "customerId": "uuid",
    "customerEmail": "jane@example.com",
    "confirmedAt": "2026-06-16T10:45:00Z",
    "estimatedDeliveryAt": "2026-06-16T18:00:00Z"
  }
}
```

---

### `order.order.status-changed`

Produced by: **Order Service** on every status transition.
Consumed by: **Notification Service**, **Analytics Service**

```json
{
  "payload": {
    "orderId": "uuid",
    "orderNumber": "FM-20260616-0042",
    "tenantId": "uuid",
    "customerId": "uuid",
    "customerEmail": "jane@example.com",
    "previousStatus": "PACKING",
    "newStatus": "OUT_FOR_DELIVERY",
    "changedAt": "2026-06-16T17:00:00Z",
    "note": "Out for delivery with rider Ravi."
  }
}
```

---

### `order.payment.completed`

Produced by: **Order Service** (payment gateway webhook handler).
Consumed by: **Notification Service**, **Customer Service** (loyalty points)

```json
{
  "payload": {
    "paymentId": "uuid",
    "orderId": "uuid",
    "orderNumber": "FM-20260616-0042",
    "tenantId": "uuid",
    "customerId": "uuid",
    "amount": 288.00,
    "currency": "INR",
    "method": "UPI",
    "gateway": "RAZORPAY",
    "gatewayPaymentId": "pay_xyz123",
    "paidAt": "2026-06-16T10:32:00Z"
  }
}
```

**Consumer actions**:
- `notification-service`: Send payment confirmation.
- `customer-service`: Award loyalty points (e.g. 1 point per ₹10 spent).
- `product-service`: Convert `quantity_reserved` → deduct `quantity_on_hand`.

---

### `order.payment.failed`

Produced by: **Order Service**
Consumed by: **Notification Service**, **Product Service** (release reserved stock)

```json
{
  "payload": {
    "paymentId": "uuid",
    "orderId": "uuid",
    "tenantId": "uuid",
    "customerId": "uuid",
    "amount": 288.00,
    "reason": "INSUFFICIENT_FUNDS",
    "failedAt": "2026-06-16T10:33:00Z"
  }
}
```

---

### `order.order.cancelled`

Produced by: **Order Service**
Consumed by: **Notification Service**, **Product Service** (release reserved stock), **Customer Service** (loyalty refund)

```json
{
  "payload": {
    "orderId": "uuid",
    "orderNumber": "FM-20260616-0042",
    "tenantId": "uuid",
    "customerId": "uuid",
    "cancelledBy": "CUSTOMER",
    "reason": "Changed my mind.",
    "refundAmount": 288.00,
    "refundMethod": "ORIGINAL",
    "cancelledAt": "2026-06-16T11:00:00Z",
    "items": [
      { "productId": "uuid", "variantId": null, "quantity": 2 }
    ]
  }
}
```

---

### `notification.notification.requested`

Produced by: any service that needs to notify a user without knowing about notification channels.
Consumed by: **Notification Service**

```json
{
  "payload": {
    "recipientUserId": "uuid",
    "recipientEmail": "jane@example.com",
    "recipientPhone": "+919876543210",
    "tenantId": "uuid",
    "channels": ["EMAIL", "PUSH", "IN_APP"],
    "templateKey": "ORDER_PLACED",
    "data": {
      "orderNumber": "FM-20260616-0042",
      "totalAmount": "₹288",
      "deliverySlot": "Today, 6 PM – 8 PM"
    },
    "referenceId": "uuid",
    "referenceType": "ORDER",
    "priority": "HIGH"
  }
}
```

---

## Event Flow Diagrams

### Order Lifecycle Event Flow

```
Customer places order
        │
        ▼
  Order Service
  - Validates cart, stock, coupon
  - Creates order row (PLACED)
  - Publishes ──► order.order.created
                      │
          ┌───────────┼───────────────────┐
          ▼           ▼                   ▼
  Notification    Product Service    Analytics Service
  Service         - Reserve stock    - Record revenue
  - Email/SMS     - Check low stock  - Update metrics
    to customer         │
  - Alert to      if qty <= threshold
    store owner         │
                        ▼
                 product.inventory.low-stock-alert
                        │
                        ▼
                 Notification Service
                 - Alert store owner

Payment gateway callback
        │
        ▼
  Order Service
  - Updates payment status
  - Updates order status (CONFIRMED)
  - Publishes ──► order.payment.completed
                      │
          ┌───────────┼──────────────┐
          ▼           ▼              ▼
  Notification   Customer Svc   Product Svc
  - Receipt      - Award        - Finalize
  - Order conf.    loyalty pts    stock deduction
```

---

## Idempotency

Every consumer must be idempotent. Pattern:

```java
// Before processing, check if eventId was already processed
if (processedEventStore.exists(event.getEventId())) {
    log.info("Duplicate event {}, skipping", event.getEventId());
    return;
}
// Process...
processedEventStore.markProcessed(event.getEventId());
```

Processed event IDs are stored in Redis with 72-hour TTL (covers Kafka re-delivery window).

---

## Dead Letter Queue (DLQ)

Each consumer topic has a corresponding DLQ:

```
order.order.created              → order.order.created.DLT
notification.notification.requested → notification.notification.requested.DLT
```

After 3 retries (with exponential backoff: 1s, 5s, 30s), failed messages land in the DLT.
A DLT monitor service alerts ops team and provides a replay endpoint.

---

## Schema Registry

All event schemas are registered in **Confluent Schema Registry** (or Apicurio Registry).
Schema evolution follows **backward-compatible** rules:
- New optional fields can be added.
- No field removal.
- No type changes.
- Version bump required for breaking changes (new topic).
