# Redis Caching Strategy — Marketly Backend Platform

## Redis Setup

| Concern | Config |
|---|---|
| Version | Redis 7.x |
| Mode | Redis Cluster (3 shards × 2 replicas) in prod; single node in dev |
| Client | Spring Data Redis + Lettuce |
| Serialization | JSON (Jackson) — human-readable, debuggable |
| Key prefix | `marketly:` global prefix on all keys |

---

## Key Naming Convention

```
marketly:{service}:{tenantId}:{entity}:{identifier}[:{sub-key}]
```

- All lowercase.
- `{tenantId}` is always present on tenant-scoped data (prevents cross-tenant cache poisoning).
- `{identifier}` is the primary key or slug.
- No spaces or special characters — only `a-z`, `0-9`, `-`, `_`, `:`.

---

## Cache Categories & TTL Policy

---

### 1. Tenant Configuration Cache

**Purpose**: Avoid DB hit on every request to resolve tenant metadata.

**Key**: `marketly:tenant:config:{slug}`

**Value** (JSON Hash):
```json
{
  "id": "uuid",
  "slug": "freshmart",
  "name": "FreshMart",
  "status": "ACTIVE",
  "accentColor": "emerald",
  "subscriptionPlan": "GROWTH",
  "settings": {
    "deliveryMinutes": 30,
    "minOrderValue": 99,
    "currency": "INR"
  }
}
```

**TTL**: 30 minutes

**Invalidation triggers**:
- `PUT /api/v1/tenants/{slug}` → `DEL marketly:tenant:config:{slug}`
- `PATCH /api/v1/tenants/{slug}/settings` → `DEL marketly:tenant:config:{slug}`
- `PATCH /api/v1/tenants/{slug}/status` → `DEL marketly:tenant:config:{slug}`
- Kafka `tenant.tenant.activated` → `DEL marketly:tenant:config:{slug}`

---

### 2. Domain → Tenant Mapping Cache

**Purpose**: Fast resolution of custom domains to tenant slugs at the API Gateway.

**Key**: `marketly:tenant:domain:{domain}`

**Value**: `"freshmart"` (tenant slug string)

**TTL**: 60 minutes

**Invalidation triggers**:
- `POST /api/v1/tenants/{slug}/domains` → `DEL marketly:tenant:domain:{domain}`
- Domain removed → `DEL marketly:tenant:domain:{domain}`

---

### 3. Product Cache

**Purpose**: Product listings are read far more often than they are written.

#### Product detail

**Key**: `marketly:product:detail:{tenantId}:{productId}`

**Value** (JSON): Full product object including variants and inventory summary.

**TTL**: 10 minutes

**Invalidation triggers**:
- Any `PUT`, `PATCH`, `DELETE` on the product → `DEL marketly:product:detail:{tenantId}:{productId}`
- Inventory change → `DEL marketly:product:detail:{tenantId}:{productId}`

#### Product listing (per tenant + filter combination)

**Key**: `marketly:product:list:{tenantId}:{fingerprint}`

Where `{fingerprint}` = SHA-256(sorted query params: page, size, sort, categorySlug, tag, search, inStock).

**Value** (JSON): Paginated product list response.

**TTL**: 5 minutes

**Invalidation triggers**:
- Any product CUD in tenant → `DEL marketly:product:list:{tenantId}:*` (pattern delete via SCAN)

> **Note**: Pattern-based deletes (`DEL key:*`) use Redis SCAN to avoid blocking. In high-write stores, product list cache TTL may be reduced to 2 minutes and cache-aside with `stale-while-revalidate` used.

---

### 4. Category Cache

**Purpose**: Categories change infrequently but are fetched on every storefront load.

**Key**: `marketly:category:tree:{tenantId}`

**Value** (JSON): Full category tree array.

**TTL**: 60 minutes

**Invalidation triggers**:
- Any category CUD → `DEL marketly:category:tree:{tenantId}`

---

### 5. Cart Cache

**Purpose**: Avoid DB reads on cart for every item add/remove. Cart is eventually persisted.

**Key**: `marketly:cart:session:{cartId}`

**Value** (JSON): Full cart object (same as GET /api/v1/cart response).

**TTL**: 24 hours (reset on every write)

**Strategy**: Write-through — every cart mutation updates both Redis and DB.
On cache miss, load from DB and re-populate.

**Invalidation triggers**:
- Cart converted to order → `DEL marketly:cart:session:{cartId}`
- Cart explicitly cleared → `DEL marketly:cart:session:{cartId}`
- TTL expiry (abandoned cart)

---

### 6. Session / Auth Cache

**Purpose**: Fast JWT validation status check (for revoked token detection).

**Key**: `marketly:auth:revoked:{tokenHash}` (SHA-256 of the refresh token)

**Value**: `"1"` (presence = revoked)

**TTL**: Matches the token's original expiry time.

**On logout**: `SET marketly:auth:revoked:{tokenHash} "1" EX {remainingTTL}`

**On every API request**: Gateway checks `EXISTS marketly:auth:revoked:{tokenHash}` before allowing access.

---

### 7. Rate Limiting

**Purpose**: Per-IP and per-user rate limiting at the API Gateway.

**Key**: `marketly:ratelimit:{type}:{identifier}:{window}`

- `{type}`: `ip` or `user`
- `{identifier}`: IP address or userId
- `{window}`: Unix minute timestamp (truncated to 60s bucket)

**Data structure**: Redis String with INCR and EXPIRE

```
INCR  marketly:ratelimit:ip:103.1.2.3:20260616103
EXPIRE marketly:ratelimit:ip:103.1.2.3:20260616103 60
```

**Thresholds**:
| Endpoint group | Limit |
|---|---|
| Auth endpoints | 10 req/min per IP |
| Public storefront | 300 req/min per IP |
| Authenticated customer | 120 req/min per user |
| Admin endpoints | 60 req/min per user |

---

### 8. Idempotency Keys (Kafka consumer)

**Purpose**: Prevent duplicate event processing.

**Key**: `marketly:idempotency:event:{consumerId}:{eventId}`

**Value**: `"processed"` + processing timestamp

**TTL**: 72 hours (covers Kafka max re-delivery window)

---

### 9. OTP / Verification Codes

**Purpose**: Short-lived codes for email verification, password reset, phone OTP.

**Key**: `marketly:otp:{type}:{userId}`
- `{type}`: `email-verify`, `password-reset`, `phone-otp`

**Value**: SHA-256 of the OTP + expiry timestamp (JSON)

**TTL**: 15 minutes

**Invalidation**: Delete key on first successful use (single-use tokens).

---

### 10. Analytics / Dashboard Metrics Cache

**Purpose**: Dashboard KPIs are expensive to compute; cache aggregated results.

**Key**: `marketly:analytics:{tenantId}:dashboard:{date}`

**Value** (JSON): Dashboard metrics object (revenue, orders, top products).

**TTL**: 5 minutes during business hours; 30 minutes overnight.

**Invalidation**: TTL-based only (eventual consistency acceptable for analytics).

---

## Cache Invalidation Strategy Summary

| Strategy | Used for | How |
|---|---|---|
| **Delete on write** | Tenant config, product detail, categories | `DEL key` immediately after DB write |
| **TTL expiry** | Product listings, analytics | Short TTL; accept brief staleness |
| **Write-through** | Cart | Write Redis + DB atomically |
| **Event-driven** | Cross-service invalidation | Kafka consumer deletes affected keys |
| **Pattern delete** | Product listings per tenant | Redis SCAN + DEL (non-blocking) |

---

## Spring Cache Configuration

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration defaultCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues()
            .prefixCacheNameWith("marketly:");
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer() {
        return builder -> builder
            .withCacheConfiguration("tenant-config",
                defaultCacheConfig().entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("category-tree",
                defaultCacheConfig().entryTtl(Duration.ofMinutes(60)))
            .withCacheConfiguration("product-detail",
                defaultCacheConfig().entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("product-list",
                defaultCacheConfig().entryTtl(Duration.ofMinutes(5)));
    }
}
```

---

## Redis Data Structures Used

| Data | Structure | Why |
|---|---|---|
| Tenant config, products, categories, cart | String (JSON) | Simple get/set, easy to inspect |
| Rate limit counters | String with INCR | Atomic increment + TTL |
| Revoked token set | String (existence check) | O(1) lookup |
| Idempotency keys | String | Presence = processed |
| WebSocket subscriber lists | PubSub channel | Fan-out to connected clients |
| Session data | Hash | Field-level updates without re-serializing full object |

---

## WebSocket Real-Time Cache

Redis Pub/Sub is used to fan-out WebSocket messages across multiple service instances.

**Channel naming**: `marketly:ws:{tenantId}:{topic}`

Topics:
- `order-updates` — new orders, status changes
- `inventory-alerts` — low stock alerts for store owner
- `dashboard` — live metric updates

```
Order Service publishes to: PUBLISH marketly:ws:{tenantId}:order-updates {payload}
WebSocket service subscribes and pushes to all connected clients in that tenant.
```

---

## Cache Monitoring

- Redis INFO stats exposed via Micrometer → Prometheus → Grafana.
- Key alerts:
  - Hit rate < 80% → investigate missing cache patterns
  - Memory > 80% → eviction risk, review TTLs
  - Eviction rate > 0 → policy is `allkeys-lru` in prod; confirm no critical data loss
