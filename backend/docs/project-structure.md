# Project Structure — Marketly Backend Platform

## Architectural Style

Standard **layered architecture** per service:

```
Controller → Service → Repository
```

No hexagonal ports/adapters. No domain/application/infrastructure split. Each service is a straightforward Spring Boot application with four layers:

| Layer | Package | Annotation | Responsibility |
|---|---|---|---|
| Controller | `controller/` | `@RestController` | HTTP request/response, input validation |
| Service | `service/` | `@Service` | Business logic, transaction boundaries |
| Repository | `repository/` | `@Repository` | Data access via Spring Data JPA |
| Entity | `entity/` | `@Entity` | JPA-mapped database tables |

Supporting packages (same level, not layers):

| Package | Contents |
|---|---|
| `dto/` | Request/Response DTOs (records or classes) |
| `mapper/` | MapStruct mappers (entity ↔ DTO) |
| `event/` | Kafka event producer/consumer classes |
| `exception/` | Custom exception classes + global handler |
| `config/` | Spring `@Configuration` classes |
| `security/` | JWT, Spring Security config (identity-service / gateway) |
| `client/` | OpenFeign clients (services that call other services) |

---

## Maven Multi-Module Layout

```
backend/
├── pom.xml                          ← Parent POM (BOM, plugin management)
├── docker-compose.yml               ← All infrastructure + services
├── docker-compose.infra.yml         ← Infrastructure only (Postgres, Redis, Kafka)
├── .env.example                     ← Environment variable template
├── docs/                            ← Architecture documentation
│
├── common/                          ← Shared library (no Spring Boot main class)
│   ├── pom.xml
│   └── src/main/java/com/marketly/common/
│       ├── dto/
│       │   ├── ApiResponse.java
│       │   ├── PageResponse.java
│       │   └── ErrorResponse.java
│       ├── exception/
│       │   ├── BusinessException.java
│       │   ├── ResourceNotFoundException.java
│       │   ├── DuplicateResourceException.java
│       │   ├── UnauthorizedException.java
│       │   └── TenantNotFoundException.java
│       ├── event/
│       │   └── BaseEvent.java       ← Kafka event envelope
│       ├── tenant/
│       │   └── TenantContext.java   ← ThreadLocal tenant holder
│       └── util/
│           ├── UuidUtil.java
│           └── DateUtil.java
│
├── api-gateway/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/marketly/gateway/
│       │   ├── GatewayApplication.java
│       │   ├── config/
│       │   │   ├── RouteConfig.java
│       │   │   ├── SecurityConfig.java
│       │   │   └── RateLimitConfig.java
│       │   └── filter/
│       │       ├── TenantResolutionFilter.java
│       │       ├── JwtAuthFilter.java
│       │       └── CorrelationIdFilter.java
│       └── resources/
│           └── application.yml
│
├── identity-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/marketly/identity/
│       │   │   ├── IdentityServiceApplication.java
│       │   │   ├── controller/
│       │   │   │   ├── AuthController.java
│       │   │   │   └── UserController.java
│       │   │   ├── service/
│       │   │   │   ├── UserService.java
│       │   │   │   ├── AuthService.java
│       │   │   │   └── TokenService.java
│       │   │   ├── repository/
│       │   │   │   ├── UserRepository.java
│       │   │   │   ├── RoleRepository.java
│       │   │   │   ├── UserTenantRoleRepository.java
│       │   │   │   └── RefreshTokenRepository.java
│       │   │   ├── entity/
│       │   │   │   ├── User.java
│       │   │   │   ├── Role.java
│       │   │   │   ├── UserTenantRole.java
│       │   │   │   └── RefreshToken.java
│       │   │   ├── dto/
│       │   │   │   ├── RegisterRequest.java
│       │   │   │   ├── LoginRequest.java
│       │   │   │   ├── LoginResponse.java
│       │   │   │   ├── TokenRefreshRequest.java
│       │   │   │   └── UserProfileResponse.java
│       │   │   ├── mapper/
│       │   │   │   └── UserMapper.java
│       │   │   ├── event/
│       │   │   │   └── UserEventProducer.java
│       │   │   ├── exception/
│       │   │   │   ├── InvalidCredentialsException.java
│       │   │   │   └── GlobalExceptionHandler.java
│       │   │   ├── security/
│       │   │   │   ├── JwtService.java
│       │   │   │   ├── JwtProperties.java
│       │   │   │   └── SecurityConfig.java
│       │   │   └── config/
│       │   │       └── AppConfig.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           ├── V1__create_schema.sql
│       │           ├── V2__create_users.sql
│       │           ├── V3__create_roles.sql
│       │           ├── V4__seed_roles.sql
│       │           ├── V5__create_refresh_tokens.sql
│       │           └── V6__create_audit_log.sql
│       └── test/java/com/marketly/identity/
│           ├── service/AuthServiceTest.java
│           └── controller/AuthControllerIntegrationTest.java
│
├── tenant-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/marketly/tenant/
│       │   ├── TenantServiceApplication.java
│       │   ├── controller/
│       │   │   └── TenantController.java
│       │   ├── service/
│       │   │   └── TenantService.java
│       │   ├── repository/
│       │   │   ├── TenantRepository.java
│       │   │   ├── TenantDomainRepository.java
│       │   │   └── TenantSettingRepository.java
│       │   ├── entity/
│       │   │   ├── Tenant.java
│       │   │   ├── TenantDomain.java
│       │   │   └── TenantSetting.java
│       │   ├── dto/
│       │   │   ├── CreateTenantRequest.java
│       │   │   ├── UpdateTenantRequest.java
│       │   │   └── TenantResponse.java
│       │   ├── mapper/
│       │   │   └── TenantMapper.java
│       │   ├── event/
│       │   │   └── TenantEventProducer.java
│       │   ├── exception/
│       │   │   └── GlobalExceptionHandler.java
│       │   └── config/
│       │       └── CacheConfig.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│               ├── V1__create_schema.sql
│               ├── V2__create_tenants.sql
│               ├── V3__create_tenant_domains.sql
│               ├── V4__create_tenant_settings.sql
│               └── V5__create_subscription_plans.sql
│
├── product-service/
│   ├── pom.xml
│   └── src/main/java/com/marketly/product/
│       ├── ProductServiceApplication.java
│       ├── controller/
│       │   ├── ProductController.java
│       │   ├── CategoryController.java
│       │   └── InventoryController.java
│       ├── service/
│       │   ├── ProductService.java
│       │   ├── CategoryService.java
│       │   └── InventoryService.java
│       ├── repository/
│       │   ├── ProductRepository.java
│       │   ├── CategoryRepository.java
│       │   ├── InventoryRepository.java
│       │   └── InventoryAdjustmentRepository.java
│       ├── entity/
│       │   ├── Product.java
│       │   ├── ProductVariant.java
│       │   ├── Category.java
│       │   ├── Inventory.java
│       │   └── InventoryAdjustment.java
│       ├── dto/
│       │   ├── CreateProductRequest.java
│       │   ├── UpdateProductRequest.java
│       │   ├── ProductResponse.java
│       │   ├── CreateCategoryRequest.java
│       │   ├── CategoryResponse.java
│       │   └── InventoryAdjustmentRequest.java
│       ├── mapper/
│       │   ├── ProductMapper.java
│       │   └── CategoryMapper.java
│       ├── event/
│       │   ├── ProductEventProducer.java
│       │   └── OrderEventConsumer.java
│       ├── exception/
│       │   └── GlobalExceptionHandler.java
│       └── config/
│           └── CacheConfig.java
│
├── customer-service/
│   ├── pom.xml
│   └── src/main/java/com/marketly/customer/
│       ├── CustomerServiceApplication.java
│       ├── controller/
│       │   └── CustomerController.java
│       ├── service/
│       │   └── CustomerService.java
│       ├── repository/
│       │   ├── CustomerRepository.java
│       │   └── AddressRepository.java
│       ├── entity/
│       │   ├── Customer.java
│       │   └── Address.java
│       ├── dto/
│       │   ├── UpdateCustomerRequest.java
│       │   ├── CustomerResponse.java
│       │   ├── CreateAddressRequest.java
│       │   └── AddressResponse.java
│       ├── mapper/
│       │   └── CustomerMapper.java
│       └── event/
│           ├── UserRegisteredConsumer.java
│           └── OrderCompletedConsumer.java
│
├── order-service/
│   ├── pom.xml
│   └── src/main/java/com/marketly/order/
│       ├── OrderServiceApplication.java
│       ├── controller/
│       │   ├── CartController.java
│       │   ├── OrderController.java
│       │   └── CouponController.java
│       ├── service/
│       │   ├── CartService.java
│       │   ├── CheckoutService.java
│       │   ├── OrderService.java
│       │   └── CouponService.java
│       ├── repository/
│       │   ├── CartRepository.java
│       │   ├── CartItemRepository.java
│       │   ├── OrderRepository.java
│       │   ├── OrderItemRepository.java
│       │   ├── PaymentRepository.java
│       │   └── CouponRepository.java
│       ├── entity/
│       │   ├── Cart.java
│       │   ├── CartItem.java
│       │   ├── Order.java
│       │   ├── OrderItem.java
│       │   ├── Payment.java
│       │   └── Coupon.java
│       ├── dto/
│       │   ├── AddToCartRequest.java
│       │   ├── CartResponse.java
│       │   ├── CheckoutRequest.java
│       │   ├── CreateOrderResponse.java
│       │   ├── OrderResponse.java
│       │   └── UpdateOrderStatusRequest.java
│       ├── mapper/
│       │   └── OrderMapper.java
│       ├── event/
│       │   └── OrderEventProducer.java
│       └── client/
│           ├── ProductServiceClient.java   ← OpenFeign
│           └── CustomerServiceClient.java  ← OpenFeign
│
├── notification-service/
│   ├── pom.xml
│   └── src/main/java/com/marketly/notification/
│       ├── NotificationServiceApplication.java
│       ├── controller/
│       │   └── NotificationController.java
│       ├── service/
│       │   └── NotificationService.java
│       ├── repository/
│       │   └── NotificationRepository.java
│       ├── entity/
│       │   └── Notification.java
│       ├── dto/
│       │   └── NotificationResponse.java
│       ├── event/
│       │   └── NotificationEventConsumer.java
│       └── channel/
│           ├── EmailChannel.java        ← SMTP via JavaMailSender
│           ├── SmsChannel.java          ← Twilio / AWS SNS
│           ├── PushChannel.java         ← Firebase FCM
│           └── WebSocketChannel.java    ← Spring WebSocket
│
└── analytics-service/
    ├── pom.xml
    └── src/main/java/com/marketly/analytics/
        ├── AnalyticsServiceApplication.java
        ├── controller/
        │   └── AnalyticsController.java
        ├── service/
        │   └── DashboardService.java
        ├── repository/
        │   └── OrderSummaryRepository.java
        ├── entity/
        │   └── OrderSummary.java
        ├── dto/
        │   └── DashboardResponse.java
        ├── event/
        │   └── OrderEventConsumer.java
        └── config/
            └── CacheConfig.java
```

---

## Parent POM Key Sections

```xml
<!-- backend/pom.xml (summary) -->
<groupId>com.marketly</groupId>
<artifactId>marketly-backend</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.2</spring-boot.version>
    <spring-cloud.version>2023.0.2</spring-cloud.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <testcontainers.version>1.19.8</testcontainers.version>
</properties>

<modules>
    <module>common</module>
    <module>api-gateway</module>
    <module>identity-service</module>
    <module>tenant-service</module>
    <module>product-service</module>
    <module>customer-service</module>
    <module>order-service</module>
    <module>notification-service</module>
    <module>analytics-service</module>
</modules>
```

---

## Service Port Map

| Service | HTTP Port | Debug Port |
|---|---|---|
| api-gateway | 8080 | 5080 |
| identity-service | 8081 | 5081 |
| tenant-service | 8082 | 5082 |
| product-service | 8083 | 5083 |
| customer-service | 8084 | 5084 |
| order-service | 8085 | 5085 |
| notification-service | 8086 | 5086 |
| analytics-service | 8087 | 5087 |
| PostgreSQL | 5432 | — |
| Redis | 6379 | — |
| Kafka | 9092 | — |
| Kafka UI | 8090 | — |
| Eureka (service discovery) | 8761 | — |
| Zipkin (tracing) | 9411 | — |

---

## Shared Base Classes (common module)

### AuditableEntity.java

Base class for every JPA entity. Provides `id`, audit timestamps, soft-delete.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete() { this.deletedAt = Instant.now(); }
}
```

### TenantAwareEntity.java

Extends `AuditableEntity`. All tenant-scoped entities extend this.

```java
@MappedSuperclass
@FilterDef(name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAwareEntity extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void onPrePersist() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getTenantId();
        }
    }
}
```

### TenantContext.java

ThreadLocal holder — set once by the gateway filter, read anywhere in the request.

```java
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    public static void set(UUID tenantId) { CURRENT.set(tenantId); }
    public static UUID get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
```

---

## Environment Variables

| Variable | Used by | Description |
|---|---|---|
| `DB_HOST` | All services | PostgreSQL host |
| `DB_PORT` | All services | PostgreSQL port (default: 5432) |
| `DB_NAME` | All services | Database name |
| `DB_USERNAME` | All services | DB user |
| `DB_PASSWORD` | All services | DB password |
| `REDIS_HOST` | All services | Redis host |
| `REDIS_PORT` | All services | Redis port (default: 6379) |
| `KAFKA_BOOTSTRAP_SERVERS` | All services | Kafka brokers |
| `JWT_SECRET` | Identity, Gateway | HS256 signing secret (min 256 bits) |
| `JWT_ACCESS_TTL_SECONDS` | Identity | Default: 900 (15 min) |
| `JWT_REFRESH_TTL_DAYS` | Identity | Default: 7 |
| `EUREKA_SERVER_URL` | All services | Service discovery URL |
| `SPRING_PROFILES_ACTIVE` | All services | `local` / `staging` / `production` |
