# ─────────────────────────────────────────────────────────────────────────────
# Marketly Multi-Module Maven Build
#
# Usage:
#   docker build --build-arg SERVICE_NAME=identity-service -t marketly/identity-service .
#
# Context: backend/ root (so parent pom.xml and all modules are available)
# ─────────────────────────────────────────────────────────────────────────────

ARG SERVICE_NAME=identity-service

# ── Stage 1: Build with Maven 3.9 + JDK 21 ───────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

ARG SERVICE_NAME

WORKDIR /build

# Copy the entire multi-module project
COPY pom.xml ./
COPY common/             common/
COPY api-gateway/        api-gateway/
COPY identity-service/   identity-service/
COPY tenant-service/     tenant-service/
COPY product-service/    product-service/
COPY customer-service/   customer-service/
COPY order-service/      order-service/
COPY notification-service/ notification-service/
COPY analytics-service/  analytics-service/
COPY whatsapp-service/   whatsapp-service/

# Build the target module + all dependencies
# 'package' phase triggers spring-boot:repackage → executable fat jar
RUN mvn -pl ${SERVICE_NAME} -am package -DskipTests --no-transfer-progress

# The fat jar is the non-.original jar produced by spring-boot:repackage
RUN find /build/${SERVICE_NAME}/target -maxdepth 1 -name "*.jar" \
    ! -name "*.original" ! -name "*-sources.jar" \
    -exec cp {} /app.jar \;

# Sanity check — fat jar should be tens of MB
RUN ls -lh /app.jar

# ── Stage 2: Minimal JRE 21 runtime ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app.jar app.jar

USER appuser

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
