# Local Development Setup — Marketly Backend

## Prerequisites

Install and verify all tools before proceeding.

| Tool | Minimum Version | Verify |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker Desktop | 4.x | `docker --version` |
| Docker Compose | v2 (bundled with Docker Desktop) | `docker compose version` |
| Git | 2.x | `git --version` |

> Java 21 LTS is required. Use [SDKMAN](https://sdkman.io/) on macOS/Linux or [Adoptium](https://adoptium.net/) on Windows.

---

## 1. Clone and enter the repository

```bash
git clone https://github.com/your-org/marketly-backend.git
cd marketly-backend
```

---

## 2. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` with your local values. Defaults work for local Docker Compose:

```dotenv
# PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_NAME=marketly
DB_USERNAME=marketly
DB_PASSWORD=marketly_dev

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT (change in production!)
JWT_SECRET=dev-secret-key-change-this-in-production-min-256-bits
JWT_ACCESS_TTL_SECONDS=900
JWT_REFRESH_TTL_DAYS=7

# Eureka
EUREKA_SERVER_URL=http://localhost:8761/eureka

# App
SPRING_PROFILES_ACTIVE=local
```

---

## 3. Start infrastructure services

Start only PostgreSQL, Redis, Kafka, and Zookeeper — without the Java services:

```bash
docker compose -f docker-compose.infra.yml up -d
```

Verify all infrastructure is healthy:
```bash
docker compose -f docker-compose.infra.yml ps
```

Expected output — all services `Up (healthy)`:
```
NAME                STATUS
marketly-postgres   Up (healthy)
marketly-redis      Up (healthy)
marketly-zookeeper  Up (healthy)
marketly-kafka      Up (healthy)
marketly-kafka-ui   Up (healthy)
```

---

## 4. Build all modules

```bash
mvn clean install -DskipTests
```

This compiles and packages all 9 modules. On first run, Maven downloads ~300 MB of dependencies.

---

## 5. Start services (Option A — Docker Compose, all services)

Run everything in Docker (easiest, matches production topology):

```bash
docker compose up -d
```

This starts:
- All 8 Java services (api-gateway through analytics-service)
- All infrastructure (Postgres, Redis, Kafka, Eureka, Zipkin)

Access:
| URL | What |
|---|---|
| `http://localhost:8080` | API Gateway (all API calls go here) |
| `http://localhost:8761` | Eureka service registry UI |
| `http://localhost:8090` | Kafka UI (topic browser) |
| `http://localhost:9411` | Zipkin distributed tracing UI |

---

## 5. Start services (Option B — Run locally for development)

For faster development iteration, run infrastructure in Docker and Java services on your machine.

**Terminal 1 — Identity Service**
```bash
cd identity-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 2 — Tenant Service**
```bash
cd tenant-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 3 — Product Service**
```bash
cd product-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 4 — API Gateway**
```bash
cd api-gateway
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

> Each service has live-reload enabled via `spring-boot-devtools` in local profile.

---

## 6. Verify services are registered

Open Eureka UI: `http://localhost:8761`

You should see all running services listed:
- `IDENTITY-SERVICE`
- `TENANT-SERVICE`
- `PRODUCT-SERVICE`
- etc.

---

## 7. Seed test data

A data seeder runs automatically on startup in `local` profile. It creates:

| Entity | Test data |
|---|---|
| Super Admin | `superadmin@marketly.in` / `Admin@1234` |
| Tenant Owner | `owner@freshmart.in` / `Owner@1234` |
| Customer | `jane@customer.in` / `Customer@1234` |
| Tenant | `freshmart` (Grocery, ACTIVE) |
| Categories | Fruits & Vegetables, Dairy, Beverages, Snacks |
| Products | 20 sample products across categories |

---

## 8. Test the API

### Step 1 — Login as Tenant Owner

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@freshmart.in",
    "password": "Owner@1234",
    "tenantSlug": "freshmart"
  }'
```

Copy the `accessToken` from the response.

### Step 2 — List products

```bash
curl http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer {accessToken}"
```

### Step 3 — Create a product

```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Organic Bananas",
    "price": 49.00,
    "mrp": 60.00,
    "unit": "dozen",
    "initialStock": 50
  }'
```

---

## 9. OpenAPI / Swagger UI

Each service exposes its own Swagger UI:

| Service | URL |
|---|---|
| API Gateway (aggregated) | `http://localhost:8080/swagger-ui.html` |
| Identity Service | `http://localhost:8081/swagger-ui.html` |
| Tenant Service | `http://localhost:8082/swagger-ui.html` |
| Product Service | `http://localhost:8083/swagger-ui.html` |
| Order Service | `http://localhost:8085/swagger-ui.html` |

Raw OpenAPI JSON: `http://localhost:{port}/v3/api-docs`

---

## 10. Running Tests

### Unit tests only
```bash
mvn test
```

### Integration tests (requires Docker for Testcontainers)
```bash
mvn verify -P integration-tests
```

Integration tests use **Testcontainers** to spin up a real PostgreSQL, Redis, and Kafka instance per test class. No external setup required.

### Single service tests
```bash
cd identity-service
mvn test
```

---

## 11. Database Access

### Connect via psql
```bash
docker exec -it marketly-postgres psql -U marketly -d marketly
```

### List schemas
```sql
\dn
```

Expected schemas: `identity`, `tenant`, `product`, `customer`, `order`, `notification`, `analytics`.

### View Flyway migration history
```sql
SELECT * FROM identity.flyway_schema_history ORDER BY installed_rank;
```

---

## 12. Kafka Management

Open Kafka UI: `http://localhost:8090`

From here you can:
- Browse topics and partitions
- View messages (JSON rendered)
- Monitor consumer group lag
- Produce test messages

### CLI access
```bash
docker exec -it marketly-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## 13. Redis Inspection

```bash
docker exec -it marketly-redis redis-cli

# Scan all marketly keys
SCAN 0 MATCH "marketly:*" COUNT 100

# View tenant config cache
GET "marketly:tenant:config:freshmart"

# View rate limit counters
SCAN 0 MATCH "marketly:ratelimit:*" COUNT 50
```

---

## 14. Common Troubleshooting

### Service fails to start: "Connection refused to PostgreSQL"
PostgreSQL is not ready yet. Wait 10 seconds after `docker compose up` and retry. Or check:
```bash
docker compose logs marketly-postgres
```

### Eureka shows service as DOWN
The service failed health check. Check its logs:
```bash
docker compose logs identity-service
```

### Flyway migration failed
Check the migration error:
```bash
docker compose logs identity-service | grep -i flyway
```
Common cause: residual schema from a previous run with a different migration. To reset:
```bash
docker compose down -v    # removes volumes — drops all data
docker compose up -d
```

### JWT token invalid at gateway
Ensure all services use the same `JWT_SECRET` value in `.env`. Check:
```bash
grep JWT_SECRET .env
```

### Port conflict
If port 8080 is in use:
```bash
# Windows
netstat -ano | findstr :8080
# macOS/Linux
lsof -i :8080
```
Edit `docker-compose.yml` to change the host port mapping.

---

## 15. Docker Compose File Reference

### `docker-compose.infra.yml` — infrastructure only

```yaml
version: "3.9"
services:
  postgres:
    image: postgres:16-alpine
    container_name: marketly-postgres
    environment:
      POSTGRES_DB: marketly
      POSTGRES_USER: marketly
      POSTGRES_PASSWORD: marketly_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U marketly"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: marketly-redis
    command: redis-server --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.1
    container_name: marketly-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.6.1
    container_name: marketly-kafka
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 10

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: marketly-kafka-ui
    depends_on: [kafka]
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092

volumes:
  postgres-data:
  redis-data:
```

---

## 16. Recommended IDE Setup (IntelliJ IDEA)

1. **Import** as Maven project (File → Open → select `backend/pom.xml`).
2. **Enable annotation processing** (for MapStruct): Settings → Build → Compiler → Annotation Processors → Enable.
3. **Install plugins**: Lombok, MapStruct support, Spring Boot.
4. **Run configurations**: Create a Spring Boot run config per service with `VM options: -Dspring.profiles.active=local`.
5. **Database tool**: Add a PostgreSQL data source pointing to `localhost:5432/marketly` for schema browsing.

---

## Phase 1 Completion Checklist

Before moving to Phase 2, confirm all of the following:

- [ ] All 4 Phase 1 services start without errors
- [ ] Flyway migrations applied cleanly to all schemas
- [ ] All services visible in Eureka
- [ ] `POST /api/v1/auth/login` returns JWT
- [ ] `GET /api/v1/tenants/freshmart` returns tenant (with Redis caching)
- [ ] `GET /api/v1/products` returns seeded products
- [ ] `POST /api/v1/products` creates a product and publishes Kafka event
- [ ] Low stock alert event visible in Kafka UI
- [ ] Swagger UI loads for all services
- [ ] Unit tests pass: `mvn test`
- [ ] Integration tests pass: `mvn verify -P integration-tests`
