# Architecture Overview — Marketly Backend Platform

## 1. System Context

Marketly is a **multi-tenant retail commerce SaaS platform**. A single deployment serves
many independent retail businesses (tenants). Each tenant has its own isolated data,
branding, and configuration while sharing the same underlying infrastructure.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL ACTORS                                │
│                                                                             │
│  Browser / Mobile App        Store Owner App         Super Admin Portal     │
│  (Customer Storefront)       (Tenant Admin)          (Platform Management)  │
└──────────────┬───────────────────────┬──────────────────────┬──────────────┘
               │                       │                      │
               ▼                       ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY  :8080                                │
│   Spring Cloud Gateway — JWT validation, tenant resolution, rate limiting,  │
│   request routing, CORS, logging, circuit breaking                          │
└──────────────┬───────────────────────┬──────────────────────┬──────────────┘
               │                       │                      │
       ┌───────┴──────┐        ┌───────┴──────┐      ┌───────┴──────┐
       │   Identity   │        │    Tenant    │      │   Product    │
       │   Service    │        │   Service    │      │   Service    │
       │    :8081     │        │    :8082     │      │    :8083     │
       └──────────────┘        └──────────────┘      └──────────────┘
       ┌───────────────┐       ┌───────────────┐     ┌───────────────┐
       │   Customer   │        │    Order      │     │ Notification  │
       │   Service    │        │   Service     │     │   Service     │
       │    :8084     │        │    :8085      │     │    :8086      │
       └──────────────┘        └──────────────┘     └───────────────┘
                               ┌───────────────┐
                               │  Analytics    │
                               │   Service     │
                               │    :8087      │
                               └───────────────┘
               │                       │                      │
               ▼                       ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           INFRASTRUCTURE LAYER                              │
│                                                                             │
│   PostgreSQL (per-schema isolation)   Redis Cluster   Apache Kafka          │
│   :5432                               :6379           :9092                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Microservices Map

| Service | Port | Responsibility | DB Schema |
|---|---|---|---|
| `api-gateway` | 8080 | Routing, auth filter, rate limiting, CORS | — |
| `identity-service` | 8081 | Auth, JWT, refresh tokens, RBAC, roles | `identity` |
| `tenant-service` | 8082 | Tenant CRUD, config, branding, subscriptions, domain mapping | `tenant` |
| `product-service` | 8083 | Products, categories, inventory, images, SKU/barcode | `product` |
| `customer-service` | 8084 | Customer profiles, addresses, loyalty, preferences | `customer` |
| `order-service` | 8085 | Cart, checkout, order lifecycle, payments, returns | `order` |
| `notification-service` | 8086 | Email, SMS, push, WebSocket real-time events | `notification` |
| `analytics-service` | 8087 | Sales reports, KPIs, dashboard metrics, store insights | `analytics` |

---

## 3. Inter-Service Communication

### Synchronous (OpenFeign over HTTP)

Used for request-time data lookups where the caller needs the response immediately.

```
API Gateway ──────► Identity Service      (JWT validation)
Order Service ─────► Product Service      (price/stock check at checkout)
Order Service ─────► Customer Service     (address/loyalty fetch)
Order Service ─────► Tenant Service       (tenant config/coupon rules)
Analytics Service ─► Order Service        (historical order data queries)
```

### Asynchronous (Kafka)

Used for cross-service side effects that do not block the primary flow.

```
Order Service ─────── OrderCreatedEvent ──────► Notification Service
                                          ──────► Analytics Service
                                          ──────► Inventory (Product Service)

Order Service ─────── PaymentCompletedEvent ───► Notification Service
                                          ──────► Loyalty (Customer Service)

Product Service ───── LowStockAlertEvent ──────► Notification Service

Identity Service ──── CustomerRegisteredEvent ─► Notification Service
                                          ──────► Customer Service
```

---

## 4. Layered Architecture (per service)

Every service uses a straightforward **Controller → Service → Repository** layered structure.
No ports/adapters, no domain/application/infrastructure separation.

```
┌─────────────────────────────────────────────────────────┐
│                      SERVICE INTERNALS                   │
│                                                          │
│  ┌─── CONTROLLER LAYER ──────────────────────────────┐  │
│  │  @RestController classes                          │  │
│  │  Input validation, HTTP request/response mapping  │  │
│  └─────────────────┬─────────────────────────────────┘  │
│                    │  calls                              │
│  ┌─── SERVICE LAYER ─────────────────────────────────┐  │
│  │  @Service classes                                 │  │
│  │  Business logic, transaction boundaries           │  │
│  │  Kafka producers/consumers                        │  │
│  │  Redis cache interactions                         │  │
│  │  OpenFeign client calls                           │  │
│  └─────────────────┬─────────────────────────────────┘  │
│                    │  calls                              │
│  ┌─── REPOSITORY LAYER ──────────────────────────────┐  │
│  │  Spring Data JPA @Repository interfaces           │  │
│  │  Custom JPQL / native queries                     │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Package layout (per service)

```
com.marketly.{service}/
  controller/     ← @RestController — HTTP endpoints
  service/        ← @Service — business logic, orchestration
  repository/     ← Spring Data JPA repositories
  entity/         ← @Entity JPA classes
  dto/            ← Request/Response DTOs (records or classes)
  mapper/         ← MapStruct @Mapper interfaces
  event/          ← Kafka producer and consumer classes
  exception/      ← Custom exceptions + @RestControllerAdvice handler
  config/         ← Spring @Configuration classes
  client/         ← OpenFeign clients (services that call other services)
  security/       ← JWT, Spring Security (identity-service and gateway only)
```

---

## 5. Multi-Tenancy Design

### Tenant Resolution Flow

```
HTTP Request
    │
    ▼
API Gateway — TenantResolutionFilter
    │
    ├─ 1. Subdomain check:   freshmart.marketly.com  → tenant_slug = "freshmart"
    ├─ 2. Custom domain:     shop.freshmart.co.in    → lookup in tenant_domain table
    ├─ 3. JWT claim:         { "tenant_id": "uuid" } → from Bearer token
    └─ 4. X-Tenant-ID header (internal service calls)
    │
    ▼
Inject tenant_id into request context (MDC + ThreadLocal)
    │
    ▼
All DB queries automatically filter by tenant_id (JPA @Filter / custom BaseRepository)
```

### Row-Level Isolation

Every tenant-owned table has a `tenant_id UUID NOT NULL` column.
A Spring `TenantContext` (ThreadLocal) stores the resolved tenant ID for the request.
A JPA `@Filter` named `tenantFilter` is applied to all tenant-aware entities via a
`TenantAwareRepositoryImpl` that activates the filter before queries.

```java
// Every tenant-owned entity extends TenantAwareEntity
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAwareEntity extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
}
```

---

## 6. Authentication & Authorization

### JWT Flow

```
1. POST /api/v1/auth/login  → Identity Service
   Response: { accessToken (15m), refreshToken (7d) }

2. Every subsequent request:
   Authorization: Bearer <accessToken>
   X-Tenant-ID: <tenant_id>   (injected by gateway after resolution)

3. API Gateway validates JWT signature (shared secret / public key)
   Extracts: user_id, tenant_id, roles[]
   Forwards claims as X-User-ID, X-Tenant-ID, X-Roles headers

4. Downstream services trust gateway headers (no re-validation needed)
```

### Roles & Permissions (RBAC)

| Role | Scope | Access |
|---|---|---|
| `SUPER_ADMIN` | Platform | All tenants, all data, platform config |
| `TENANT_OWNER` | Tenant | Full access to own tenant |
| `STORE_MANAGER` | Tenant | Products, orders, customers, reports |
| `STORE_STAFF` | Tenant | Orders, inventory updates |
| `CUSTOMER` | Tenant | Own orders, profile, cart |

---

## 7. Technology Decisions

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21 | LTS, virtual threads (Project Loom) for high I/O concurrency |
| Framework | Spring Boot 3.3 | Mature, enterprise-grade, excellent ecosystem |
| Database | PostgreSQL 16 | ACID, JSONB for flexible config, excellent indexing |
| Cache | Redis 7 | Sub-millisecond reads, pub/sub for WebSocket fan-out |
| Messaging | Apache Kafka 3.7 | High throughput, durable, replayable event log |
| Service mesh | Spring Cloud Gateway | Native Spring, JWT filter, rate limiting, routing |
| Service discovery | Spring Cloud Eureka | Auto-registration, load-balanced Feign clients |
| Config | Spring Cloud Config | Centralized, environment-aware, git-backed |
| Tracing | Micrometer + Zipkin | Distributed trace propagation via B3 headers |
| Metrics | Micrometer + Prometheus | Scraped by Prometheus, visualized in Grafana |
| Logging | SLF4J + Logback + ELK | Structured JSON logs, MDC correlation ID |
| Build | Maven 3.9 | Multi-module, reproducible builds |
| Containerization | Docker + Compose | Dev-parity, all infra in Compose |
| ORM | Spring Data JPA (Hibernate 6) | Type-safe queries, schema migration via Flyway |
| Migrations | Flyway | Version-controlled SQL, per-service schema |
| Mapping | MapStruct | Compile-time, zero-reflection DTO mapping |
| API Docs | SpringDoc OpenAPI 3 | Auto-generated Swagger UI |
| Testing | JUnit 5 + Testcontainers | Real DB/Kafka in integration tests |

---

## 8. Non-Functional Requirements

| NFR | Target | Mechanism |
|---|---|---|
| Availability | 99.9% | Horizontal scaling, health checks, circuit breakers |
| Latency (p99) | < 200ms API | Redis caching, connection pooling (HikariCP) |
| Throughput | 10,000 req/min per tenant | Rate limiting at gateway, Kafka buffering |
| Tenant isolation | Strict | Row-level `tenant_id`, JWT scoping |
| Data durability | RPO < 1min | PostgreSQL WAL, Kafka replication factor 3 |
| Observability | Full | Correlation IDs, distributed tracing, metrics |
| Security | Enterprise | JWT RS256, RBAC, audit log, password bcrypt |
