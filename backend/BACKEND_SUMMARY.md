# Marketly Backend — Complete Platform Summary

## Architecture

Multi-tenant retail SaaS platform built with Java 21 + Spring Boot 3.3.
9 Maven modules, layered architecture (Controller → Service → Repository).

## Services

| Service | Port | Schema | Responsibility |
|---|---|---|---|
| api-gateway | 8080 | — | JWT validation, routing, rate limiting, CORS, correlation IDs |
| identity-service | 8081 | identity | Auth, JWT, refresh tokens, RBAC, audit log |
| tenant-service | 8082 | tenant | Tenant CRUD, domain mapping, settings, subscriptions |
| product-service | 8083 | product | Products, categories, inventory, stock management |
| customer-service | 8084 | customer | Profiles, addresses, loyalty points, tier management |
| order-service | 8085 | order_data | Cart, checkout, order lifecycle, payments, coupons |
| notification-service | 8086 | notification | Email, in-app, WebSocket real-time notifications |
| analytics-service | 8087 | analytics | KPI dashboards, daily revenue aggregates |

## Infrastructure

| Tool | Port | Purpose |
|---|---|---|
| PostgreSQL 16 | 5432 | Primary database (8 schemas) |
| Redis 7 | 6379 | Caching, rate limiting, WebSocket pub/sub |
| Apache Kafka | 9092 | Async event bus (10+ topics) |
| Kafka UI | 8090 | Topic browser (dev) |
| Zipkin | 9411 | Distributed tracing |
| Prometheus | 9090 | Metrics scraping |
| Grafana | 3001 | Metrics visualization |

## Quick Start

```bash
# 1. Copy env file
cp .env.example .env

# 2. Start infrastructure only (for local dev)
docker compose -f docker-compose.infra.yml up -d

# 3. Build all modules
mvn clean install -DskipTests

# 4. Run a service
cd identity-service && mvn spring-boot:run

# 5. Or start everything in Docker
docker compose up -d
```

## Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| identity.user.registered | identity-service | customer-service, notification-service |
| tenant.tenant.created | tenant-service | notification-service |
| tenant.tenant.activated | tenant-service | notification-service |
| product.product.created | product-service | analytics-service |
| product.inventory.updated | product-service | notification-service |
| product.inventory.low-stock-alert | product-service | notification-service |
| order.order.created | order-service | product-service, notification-service, analytics-service |
| order.order.status-changed | order-service | notification-service |
| order.order.cancelled | order-service | product-service, customer-service, notification-service, analytics-service |
| order.payment.completed | order-service | customer-service, notification-service |

## Database Schemas

| Schema | Tables |
|---|---|
| identity | users, roles, user_tenant_roles, refresh_tokens, audit_log |
| tenant | tenants, tenant_domains, tenant_settings |
| product | categories, products, product_variants, inventory, inventory_adjustments |
| customer | customers, addresses |
| order_data | coupons, carts, cart_items, orders, order_items, payments |
| notification | notifications |
| analytics | order_summaries |

## API Endpoints (via gateway at :8080)

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/password/change

GET    /api/v1/tenants
POST   /api/v1/tenants
GET    /api/v1/tenants/{slug}
PUT    /api/v1/tenants/{slug}
PATCH  /api/v1/tenants/{slug}/settings
PATCH  /api/v1/tenants/{slug}/status

GET    /api/v1/categories
POST   /api/v1/categories
GET    /api/v1/products
POST   /api/v1/products
GET    /api/v1/products/{id}
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
PATCH  /api/v1/products/{id}/inventory

GET    /api/v1/customers/me
PATCH  /api/v1/customers/me
GET    /api/v1/customers/me/addresses
POST   /api/v1/customers/me/addresses
GET    /api/v1/customers            (admin)

GET    /api/v1/cart
POST   /api/v1/cart/items
PATCH  /api/v1/cart/items/{id}
DELETE /api/v1/cart/items/{id}
POST   /api/v1/cart/coupon
DELETE /api/v1/cart/coupon

POST   /api/v1/orders               (checkout)
GET    /api/v1/orders
GET    /api/v1/orders/{id}
PATCH  /api/v1/orders/{id}/status
POST   /api/v1/orders/{id}/cancel
GET    /api/v1/coupons
POST   /api/v1/coupons

GET    /api/v1/notifications
GET    /api/v1/notifications/unread-count
POST   /api/v1/notifications/mark-all-read

GET    /api/v1/analytics/dashboard
GET    /api/v1/analytics/revenue
```

## WebSocket

Connect to `ws://localhost:8086/ws` (SockJS fallback available).

Subscribe to:
- `/topic/users/{userId}` — your personal notifications
- `/topic/store/{tenantId}` — store-level alerts (low stock, new orders)

## Kubernetes

```bash
kubectl apply -f k8s/base/namespace.yaml
kubectl apply -f k8s/base/configmap.yaml
kubectl apply -f k8s/base/secret.yaml
kubectl apply -f k8s/base/identity-service.yaml
kubectl apply -f k8s/base/services.yaml
```

## Testing

```bash
# Unit tests
mvn test

# Integration tests (Testcontainers — needs Docker running)
mvn verify -P integration-tests

# Single service
cd identity-service && mvn test
```

## Security

- JWT HS256, 15-minute access tokens, 7-day refresh tokens (rotated)
- bcrypt password hashing (strength 12)
- RBAC: SUPER_ADMIN, TENANT_OWNER, STORE_MANAGER, STORE_STAFF, CUSTOMER
- All requests carry X-Correlation-ID for distributed tracing
- Audit log for all auth events (immutable, written async)
- Rate limiting at gateway (Redis-backed)
- Soft delete everywhere — no hard deletes on business data
