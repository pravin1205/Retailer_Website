# Marketly — Complete Local Deployment Guide

**Platform:** Marketly Multi-Tenant Retail SaaS  
**Target:** Windows local machine (the same machine where the code lives)  
**Audience:** Anyone — no prior deployment knowledge assumed  
**Purpose:** Take this codebase from zero to fully running in Docker containers using Jenkins as the automated build and deploy tool

---

## How This Guide Works

Every section tells you:
1. **What** you are doing and **why**
2. **Exactly which command** to run — copy-paste ready
3. **Where each value comes from** — nothing is left as "fill this in yourself"

Follow the sections in order. Do not skip ahead.

---

## Table of Contents

1. [Understanding the Architecture](#1-understanding-the-architecture)
2. [Install Prerequisites](#2-install-prerequisites)
3. [Understanding the Dockerfiles](#3-understanding-the-dockerfiles)
4. [Understanding the docker-compose.yml](#4-understanding-the-docker-composeyml)
5. [Create the .env File](#5-create-the-env-file)
6. [Install and Configure Jenkins](#6-install-and-configure-jenkins)
7. [Generate Required Secrets](#7-generate-required-secrets)
8. [Add Credentials to Jenkins](#8-add-credentials-to-jenkins)
9. [Configure Jenkins Tools](#9-configure-jenkins-tools)
10. [Understanding the Jenkinsfile](#10-understanding-the-jenkinsfile)
11. [Update the Jenkinsfile for Local Deploy](#11-update-the-jenkinsfile-for-local-deploy)
12. [Create the Jenkins Pipeline Job](#12-create-the-jenkins-pipeline-job)
13. [Run the Pipeline](#13-run-the-pipeline)
14. [Verify the Deployment](#14-verify-the-deployment)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Understanding the Architecture

Before touching anything, understand what you are deploying.

### What the application is

Marketly is a multi-tenant retail SaaS with two parts:

**Backend** — 8 Spring Boot microservices + 1 API gateway, all written in Java 21:

| Service | Port | What it does |
|---|---|---|
| `api-gateway` | 8080 | Single entry point — routes all `/api/v1/*` requests to the right service |
| `identity-service` | 8081 | Authentication, OTP, JWT tokens, user management |
| `tenant-service` | 8082 | Seller onboarding, store management, KYC |
| `product-service` | 8083 | Products, categories, inventory |
| `customer-service` | 8084 | Customer profiles, addresses, loyalty points |
| `order-service` | 8085 | Cart, orders, coupons, payments |
| `notification-service` | 8086 | Email/SMS notifications via Kafka events |
| `analytics-service` | 8087 | Dashboard metrics, order summaries |

**Frontend** — 1 Node.js SSR application (TanStack Start / React):

| Service | Port | What it does |
|---|---|---|
| `frontend` | 3000 | Admin portal, Seller portal, Customer storefront |

**Infrastructure** — 4 supporting services:

| Service | Port | What it does |
|---|---|---|
| PostgreSQL | 5432 | Main database (all services share one DB, separate schemas) |
| Redis | 6379 | OTP cache, session rate limiting, tenant slug cache |
| Kafka | 9092 | Async events between services (OTP sent, seller approved, order placed) |
| Zookeeper | 2181 | Required by Kafka to manage its cluster state |

### How they connect

```
Browser
  ↓ port 3000
Frontend (Node.js SSR)
  ↓ /api/v1/* → port 8080
API Gateway
  ↓ routes by path
  ├── /api/v1/auth/**        → identity-service:8081
  ├── /api/v1/tenants/**     → tenant-service:8082
  ├── /api/v1/products/**    → product-service:8083
  ├── /api/v1/customers/**   → customer-service:8084
  ├── /api/v1/orders/**      → order-service:8085
  ├── /api/v1/notifications/**→ notification-service:8086
  └── /api/v1/analytics/**   → analytics-service:8087
```

Inside Docker, services talk to each other using the container name as hostname (e.g. `http://postgres:5432`, `http://kafka:29092`). Outside Docker, your browser talks to `localhost:3000`.

### What Jenkins does

Jenkins is the automation tool that:
1. Takes your code from Git
2. Compiles all Java services into JAR files
3. Builds the frontend
4. Packages each service into a Docker image
5. Pushes those images to Docker Hub
6. Runs `docker compose up` to start all containers

Without Jenkins you would have to do all of those steps manually every time you change code.

---

## 2. Install Prerequisites

Install each tool in this exact order. Each one is needed before the next.

### 2.1 Git

**Check if already installed:**
```powershell
git --version
```
If you see `git version 2.x.x`, skip to 2.2.

**Install:**
- Download from: https://git-scm.com/download/win
- Run the installer, accept all defaults

**Verify:**
```powershell
git --version
# Expected: git version 2.45.0 (or similar)
```

---

### 2.2 Java Development Kit (JDK) 21

**Why:** Maven needs JDK to compile the Spring Boot services. JDK 21 specifically because that is what is declared in `backend/pom.xml` and the Dockerfiles.

**Where this value comes from:** Look at `backend/pom.xml` lines 28-30:
```xml
<java.version>17</java.version>
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```
The pom.xml says 17 but the services actually use Java 21 features and the Dockerfile uses `eclipse-temurin:21`. Install JDK 21.

**Install:**
- Download Eclipse Temurin JDK 21 from: https://adoptium.net/temurin/releases/?version=21
- Choose: Windows x64, JDK, .msi installer
- Run the installer, accept all defaults
- During installation: check **"Set JAVA_HOME"** and **"Add to PATH"**

**Verify:**
```powershell
java -version
# Expected: openjdk version "21.x.x"

javac -version
# Expected: javac 21.x.x
```

---

### 2.3 Maven 3.9

**Why:** Maven compiles all 9 Java modules into runnable JAR files. The command `mvn package` builds them.

**Where this value comes from:** `backend/Dockerfile` line 13:
```dockerfile
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
```
Maven 3.9 is what the Dockerfile uses, so your local Maven should match.

**Install:**
1. Download from: https://maven.apache.org/download.cgi → Binary zip archive → `apache-maven-3.9.x-bin.zip`
2. Extract to `C:\Program Files\Apache\maven`
3. Add to PATH:
   - Search Windows → "Environment Variables" → "Edit the system environment variables"
   - Click "Environment Variables"
   - Under "System variables" → find `Path` → click Edit → click New
   - Add: `C:\Program Files\Apache\maven\bin`
   - Click OK on all dialogs
4. Open a **new** PowerShell window (must be new for PATH to update)

**Verify:**
```powershell
mvn -version
# Expected: Apache Maven 3.9.x
# Expected: Java version: 21.x.x
```

---

### 2.4 Node.js 22

**Why:** Needed to build the frontend. `npm run build` uses Vite + TanStack Start to compile the React app into a Node.js SSR server.

**Where this value comes from:** `Dockerfile` (root) line 9:
```dockerfile
FROM node:22-alpine AS deps
```
Node 22 is what the frontend Dockerfile uses.

**Install:**
- Download from: https://nodejs.org/en/download → Windows Installer → version 22.x LTS
- Run the installer, accept all defaults
- Make sure "Add to PATH" is checked

**Verify:**
```powershell
node --version
# Expected: v22.x.x

npm --version
# Expected: 10.x.x
```

---

### 2.5 Docker Desktop

**Why:** Docker runs all the containers (PostgreSQL, Redis, Kafka, all microservices, frontend). It is the core of the entire deployment.

**Install:**
- Download from: https://www.docker.com/products/docker-desktop/
- Run the installer, accept all defaults
- **Restart your computer** after installation
- After restart, Docker Desktop will start automatically
- Wait for the Docker Desktop window to show "Engine running" (green icon in system tray)

**Verify:**
```powershell
docker --version
# Expected: Docker version 26.x.x

docker compose version
# Expected: Docker Compose version v2.x.x
```

**Important:** Docker Desktop must be running (green icon in system tray) every time you use Docker commands or run the Jenkins pipeline.

---

### 2.6 Jenkins

**Why:** Jenkins automates the entire build-test-package-deploy process. Every time you push code, Jenkins handles everything.

**Jenkins runs on Java.** You already installed JDK 21 in step 2.2.

**Install Jenkins:**

1. Download Jenkins installer for Windows from: https://www.jenkins.io/download/ → Windows → `jenkins.msi`

2. Run the installer:
   - Click Next on the welcome screen
   - Install location: keep the default `C:\Program Files\Jenkins`
   - Service account: select **"Run service as LocalSystem"**
   - Port: keep **8080** (this is Jenkins' web UI port — different from the api-gateway's 8080, they will not conflict because Jenkins runs on your host and the api-gateway runs inside Docker on port 8080 too — see note below)

   > **Port conflict note:** Both Jenkins and api-gateway use port 8080. To avoid this:
   > - Jenkins listens on `localhost:8080` on your host machine
   > - When you run docker compose, change api-gateway's host port to 8081:
   >   In `backend/docker-compose.yml`, find api-gateway and change `"8080:8080"` to `"8081:8080"`
   >   Then access the API at `localhost:8081` instead

   Alternatively, change Jenkins to port 9090 during installation to avoid the conflict entirely.

3. Click Install → Finish

4. Jenkins starts automatically as a Windows service. Open your browser and go to:
   ```
   http://localhost:8080
   ```
   (or http://localhost:9090 if you changed the port)

5. **Unlock Jenkins:**
   The screen asks for an initial admin password. Get it by running:
   ```powershell
   type "C:\Program Files\Jenkins\secrets\initialAdminPassword"
   ```
   Copy the long string that appears and paste it into the browser.

6. Click **"Install suggested plugins"** — wait for all plugins to install (takes 2-3 minutes)

7. Create your admin account:
   - Username: `admin` (or any name you choose)
   - Password: choose a password you will remember
   - Full name: your name
   - Email: your email
   - Click "Save and Continue"

8. Jenkins URL: keep `http://localhost:8080` → click "Save and Finish"

9. Click "Start using Jenkins"

**Verify Jenkins is running:**
```powershell
# Check the Jenkins Windows service is running
Get-Service -Name "Jenkins"
# Expected: Status = Running
```

---

## 3. Understanding the Dockerfiles

There are **two Dockerfiles** in this project. Each one builds a different part of the application.

### 3.1 Backend Dockerfile — `backend/Dockerfile`

**Location in project:** `d:\Retailer_Website\backend\Dockerfile`

**Purpose:** Builds any one of the 8 backend microservices into a Docker image. One file serves all 8 services — the `SERVICE_NAME` build argument tells it which one to build.

**Full file with explanation of every line:**

```dockerfile
# ARG defines a variable you can pass at build time.
# Default is identity-service. When building tenant-service, you pass
# --build-arg SERVICE_NAME=tenant-service
ARG SERVICE_NAME=identity-service

# ── STAGE 1: Builder ──────────────────────────────────────────────────────────
# Uses the official Maven 3.9 + JDK 21 image (Alpine = tiny Linux)
# Named "builder" so Stage 2 can copy files from it
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

# Redeclare ARG inside this stage (Docker requirement — ARGs reset between stages)
ARG SERVICE_NAME

# All commands run from /build inside the container
WORKDIR /build

# Copy the entire multi-module Maven project into the container.
# We copy pom.xml first (the parent POM), then all module folders.
# WHY THIS ORDER: Docker caches each COPY layer. If only source code changed
# but pom.xml didn't, Maven dependency downloads are skipped (faster builds).
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

# Run Maven to build ONLY the requested service and its dependencies.
# -pl ${SERVICE_NAME}  = build this module only
# -am                  = also build modules this one depends on (e.g. common)
# -DskipTests          = skip running tests (tests run in a separate Jenkins stage)
# --no-transfer-progress = cleaner log output
# Result: a fat JAR in /build/{SERVICE_NAME}/target/*.jar
RUN mvn -pl ${SERVICE_NAME} -am package -DskipTests --no-transfer-progress

# Find the fat JAR (the one without .original or -sources in the name)
# and copy it to /app.jar so Stage 2 can find it at a fixed path
RUN find /build/${SERVICE_NAME}/target -maxdepth 1 -name "*.jar" \
    ! -name "*.original" ! -name "*-sources.jar" \
    -exec cp {} /app.jar \;

# Safety check: print the JAR file size. If it's only a few KB, something went wrong.
# A correct fat JAR is 50-120 MB.
RUN ls -lh /app.jar

# ── STAGE 2: Runtime ──────────────────────────────────────────────────────────
# Uses a minimal JRE (not full JDK) — no compiler, no Maven, just enough to run Java.
# This makes the final image ~100 MB instead of ~500 MB.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security.
# Running as root inside a container is a security risk.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy ONLY the fat JAR from Stage 1 (the builder stage).
# Everything else (source code, Maven cache, JDK) is left behind — not in the final image.
COPY --from=builder /app.jar app.jar

# Switch to the non-root user
USER appuser

# Start the Spring Boot application.
# -XX:+UseContainerSupport    = lets the JVM see Docker's memory limits (not the host's RAM)
# -XX:MaxRAMPercentage=75.0   = JVM heap uses max 75% of container memory
# -Djava.security.egd=...     = faster random number generation (speeds up startup)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**How to build one service manually (for testing):**
```powershell
# From the backend/ directory
cd d:\Retailer_Website\backend
docker build --build-arg SERVICE_NAME=identity-service -t marketly/identity-service .
```
Replace `identity-service` with any of: `tenant-service`, `product-service`, `customer-service`, `order-service`, `notification-service`, `analytics-service`, `api-gateway`

---

### 3.2 Frontend Dockerfile — `Dockerfile` (root)

**Location in project:** `d:\Retailer_Website\Dockerfile`

**Purpose:** Builds the React/TanStack Start frontend into a Node.js SSR server Docker image.

**Full file with explanation of every line:**

```dockerfile
# ── STAGE 1: Install dependencies ────────────────────────────────────────────
# Node 22 Alpine = small Node.js image
# Named "deps" so Stage 2 can copy node_modules from it
FROM node:22-alpine AS deps

WORKDIR /app

# Copy ONLY package.json and package-lock.json first.
# WHY: If source code changes but package.json doesn't,
# Docker uses the cached node_modules layer (much faster).
COPY package.json package-lock.json ./

# npm ci = clean install (uses exact versions from package-lock.json)
# --ignore-scripts = don't run post-install scripts (faster, more secure)
RUN npm ci --ignore-scripts

# ── STAGE 2: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

# Bring in the installed node_modules from Stage 1
COPY --from=deps /app/node_modules ./node_modules

# Copy the entire frontend source code
COPY . .

# ARG = build-time variable (can be overridden when building the image)
# VITE_API_URL is embedded into the JavaScript bundle at build time.
# Value /api/v1 means the frontend calls /api/v1/* which Vite/Nginx
# proxies to the api-gateway at runtime.
ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=${VITE_API_URL}

# Run the build. This calls vite build which:
# 1. Compiles TypeScript → JavaScript
# 2. Bundles React components
# 3. Creates a Node.js SSR server in .output/
RUN npm run build

# ── STAGE 3: Production runtime ──────────────────────────────────────────────
# Final image — only has the built output, not the source code or node_modules
FROM node:22-alpine AS runner

WORKDIR /app

# Tell Node.js this is production (disables debug logging, enables optimizations)
ENV NODE_ENV=production
# Port the server listens on inside the container
ENV PORT=3000

# Copy ONLY the built output from Stage 2 (not source code, not node_modules)
# .output/ is created by TanStack Start/Nitro and contains everything needed to run
COPY --from=builder /app/.output ./.output
COPY --from=builder /app/package.json ./package.json

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Tell Docker which port this container uses (documentation only — doesn't open firewall)
EXPOSE 3000

# Start the SSR server. index.mjs is the compiled Node.js server entry point.
CMD ["node", ".output/server/index.mjs"]
```

**How to build the frontend manually (for testing):**
```powershell
# From the project root (not backend/)
cd d:\Retailer_Website
docker build -t marketly/frontend .
```

---

## 4. Understanding the docker-compose.yml

**Location in project:** `d:\Retailer_Website\backend\docker-compose.yml`

**Purpose:** Defines all 13 containers and how they connect. One command starts everything.

### Key concepts

**Service name vs container name:**
- Service name (e.g. `identity-service`) is used by other containers to reach it over the network: `http://identity-service:8081`
- Container name (e.g. `marketly-identity-service`) is what shows in `docker ps`

**Ports — `"HOST:CONTAINER"`:**
- `"8081:8081"` means your browser can reach it at `localhost:8081`
- The left number is your machine's port, the right is inside the container
- Services talk to each other using container-internal ports only

**Environment variables:**
- Values like `${JWT_SECRET:-default}` mean: read from `.env` file, or use the default after `:-` if not set
- The `.env` file sits next to `docker-compose.yml` (in `backend/`)

**depends_on with healthcheck:**
- `condition: service_healthy` means the service won't start until the dependency's healthcheck passes
- This prevents `identity-service` from starting before PostgreSQL is ready to accept connections

**Networks:**
- All services share `marketly-net` so they can talk to each other by name
- Nothing outside Docker can reach a service unless a port is exposed

### Service startup order (enforced by depends_on)

```
postgres ─────────────────────────────────────────────────────→ all services
redis ────────────────────────────────────────────────────────→ gateway, identity, tenant, product, order
kafka ←── zookeeper ──────────────────────────────────────────→ all services
                                                                      ↓
identity-service (waits for postgres + kafka + redis)
                                                                      ↓
api-gateway (waits for identity-service + redis)
                                                                      ↓
frontend (waits for api-gateway)
```

---

## 5. Create the .env File

The `.env` file holds secret values that docker-compose reads. It sits in `backend/` next to `docker-compose.yml`. It is **gitignored** — never committed to Git.

### Step 1 — Generate the JWT secret

The JWT secret is a random 256-bit (32-byte) string used to sign authentication tokens. You generate it once and never change it (changing it invalidates all active user sessions).

**Run this command in PowerShell:**
```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

**Example output (your value will be different):**
```
8n6UAqt0PrPJnOS9Denw8HKNfDtUAUWGPufT7WetD50=
```

Copy this value. You will use it in two places:
1. The `.env` file (right now)
2. Jenkins credentials (in section 8)

### Step 2 — Create the .env file

Create a new file at `d:\Retailer_Website\backend\.env` with this content.

Replace `PASTE_YOUR_GENERATED_SECRET_HERE` with the value from Step 1.

```env
# ── PostgreSQL ────────────────────────────────────────────────────────────────
# These values match what docker-compose.yml uses for the postgres container.
# Where they go: every Spring Boot service reads DB_HOST, DB_PORT, DB_NAME,
# DB_USERNAME, DB_PASSWORD from environment variables defined in docker-compose.yml.
# Source: docker-compose.yml lines 17-20 (postgres service environment block)
DB_HOST=postgres
DB_PORT=5432
DB_NAME=marketly
DB_USERNAME=marketly
DB_PASSWORD=marketly_dev

# ── Redis ─────────────────────────────────────────────────────────────────────
# Redis container name is "redis" — that's the hostname inside Docker network.
# Where it goes: identity-service uses Redis for OTP storage and rate limiting.
#                api-gateway uses Redis for rate limiting.
# Source: identity-service/src/main/resources/application.yml lines 37-39
REDIS_HOST=redis
REDIS_PORT=6379

# ── Kafka ─────────────────────────────────────────────────────────────────────
# kafka:29092 is the INTERNAL Kafka listener (for container-to-container communication).
# localhost:9092 is the EXTERNAL listener (for tools running on your host machine).
# All Spring Boot services use the internal address because they are inside Docker.
# Source: docker-compose.yml kafka environment → KAFKA_ADVERTISED_LISTENERS
KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# ── JWT Secret ────────────────────────────────────────────────────────────────
# REPLACE THIS with the value you generated in Step 1 above.
# Where it goes:
#   - identity-service uses it to SIGN tokens (application.yml line 60)
#   - api-gateway uses it to VERIFY tokens (application.yml line 83)
# Both must have the SAME value or logins will fail.
# Source: generate with: node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
JWT_SECRET=PASTE_YOUR_GENERATED_SECRET_HERE

# ── Platform Domain ───────────────────────────────────────────────────────────
# Used by api-gateway's TenantResolutionFilter to extract the store slug
# from the subdomain (e.g. freshmart.marketly.com → slug = "freshmart").
# For local development, keep this as marketly.com.
# Source: backend/api-gateway/src/main/resources/application.yml line 84
PLATFORM_DOMAIN=marketly.com

# ── SMTP (Email) ─────────────────────────────────────────────────────────────
# Used by notification-service to send emails (OTP, approval notifications).
# For local dev: leave blank — notification-service will log emails instead of sending.
# For production: get these values from your email provider (Mailtrap, SendGrid, etc.)
SMTP_HOST=smtp.mailtrap.io
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
```

**Save the file at:** `d:\Retailer_Website\backend\.env`

**Verify the file exists:**
```powershell
Test-Path "d:\Retailer_Website\backend\.env"
# Expected: True
```

---

## 6. Install and Configure Jenkins

Jenkins is already installed from section 2.6. This section configures it for this project.

### 6.1 Install required plugins

Jenkins needs extra plugins to work with Docker, SSH, and Node.js.

1. Open Jenkins at `http://localhost:8080`
2. Go to: **Manage Jenkins → Plugins → Available plugins**
3. Search and install each of these (check the checkbox, then click "Install"):

| Plugin name | What it does |
|---|---|
| `Pipeline` | Enables the Jenkinsfile declarative pipeline syntax |
| `Git` | Lets Jenkins clone your Git repository |
| `SSH Agent` | Lets Jenkins SSH into servers using a stored key |
| `Docker Pipeline` | Adds `docker.build()` and registry push helpers |
| `Credentials Binding` | Injects secrets into build steps as environment variables |
| `NodeJS` | Lets Jenkins use a specific Node.js version |
| `JUnit` | Displays test results from Maven's surefire reports |

4. Check **"Restart Jenkins when installation is complete"** at the bottom
5. Wait for Jenkins to restart, then log back in

---

## 7. Generate Required Secrets

You need two secrets before configuring Jenkins credentials.

### 7.1 JWT Secret (same one from section 5)

You already generated this in section 5. It is:
```
8n6UAqt0PrPJnOS9Denw8HKNfDtUAUWGPufT7WetD50=
```
(Your value will be different — use what you generated, not this example)

Keep it ready — you will paste it into Jenkins in section 8.

### 7.2 SSH Key Pair for Jenkins Deploy

**What this is:** A pair of cryptographic keys. Jenkins holds the private key and uses it to authenticate when connecting to servers. Since you are deploying locally (Jenkins and Docker are on the same machine), this key lets Jenkins run Docker commands.

**Generate the key pair. Run in PowerShell:**
```powershell
# This creates two files in C:\Users\Pravin.n\.ssh\
ssh-keygen -t ed25519 -C "jenkins-deploy" -f "$env:USERPROFILE\.ssh\jenkins_deploy_key" -N ""
```

**What each flag means:**
- `-t ed25519` = key type (modern, secure algorithm)
- `-C "jenkins-deploy"` = comment/label to identify the key
- `-f "$env:USERPROFILE\.ssh\jenkins_deploy_key"` = save to `C:\Users\Pravin.n\.ssh\jenkins_deploy_key`
- `-N ""` = no passphrase (Jenkins needs to use it automatically without prompting)

**Expected output:**
```
Generating public/private ed25519 key pair.
Your identification has been saved in C:\Users\Pravin.n\.ssh\jenkins_deploy_key
Your public key has been saved in C:\Users\Pravin.n\.ssh\jenkins_deploy_key.pub
The key fingerprint is:
SHA256:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx jenkins-deploy
```

**Read the private key (you will paste this into Jenkins):**
```powershell
type "$env:USERPROFILE\.ssh\jenkins_deploy_key"
```

The output looks like this (your actual key will be different):
```
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtz...
[more lines]
-----END OPENSSH PRIVATE KEY-----
```

Copy **everything** including the `-----BEGIN...` and `-----END...` lines.

**Read the public key (you will add this to authorized_keys):**
```powershell
type "$env:USERPROFILE\.ssh\jenkins_deploy_key.pub"
```

Output looks like:
```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx jenkins-deploy
```

Copy this one line.

---

## 8. Add Credentials to Jenkins

Credentials are stored securely in Jenkins. The Jenkinsfile references them by ID — the actual secret values are never written in code.

Go to: **Manage Jenkins → Credentials → System → Global credentials (unrestricted) → Add Credentials**

### Credential 1 — Docker Hub Login

**What it is:** Your Docker Hub username and password. Jenkins uses this to push built Docker images to Docker Hub so docker-compose can pull them during deploy.

**First, create a Docker Hub account if you don't have one:**
1. Go to: https://hub.docker.com/signup
2. Choose a username (e.g. `yourname`) — this becomes your registry prefix
3. Verify your email

**Add in Jenkins:**
- Kind: **Username with password**
- Scope: Global
- Username: `your Docker Hub username` (e.g. `yourname`)
  - **Where to find it:** Your Docker Hub account username shown at hub.docker.com after login
- Password: `your Docker Hub password`
  - **Where to find it:** The password you set when creating your Docker Hub account
- ID: `docker-registry-credentials`
  - **Important:** This exact ID is referenced in `Jenkinsfile` line 256. Do not change it.
- Description: `Docker Hub credentials`
- Click **Create**

### Credential 2 — SSH Deploy Key (Private Key)

**What it is:** The private half of the SSH key pair you generated in section 7.2. Jenkins uses this to authenticate when deploying.

**Add in Jenkins:**
- Kind: **SSH Username with private key**
- Scope: Global
- ID: `deploy-server-ssh`
  - **Important:** This exact ID is referenced in `Jenkinsfile` line 290. Do not change it.
- Description: `Jenkins deploy key`
- Username: `pravin.n`
  - **Where to find it:** Run `whoami` in PowerShell → the part after the backslash. Your output was `simplify3x\pravin.n` so the username is `pravin.n`
- Private Key: select **Enter directly**
  - Click **Add**
  - Paste the **entire private key** from section 7.2 (including `-----BEGIN...` and `-----END...` lines)
- Click **Create**

### Credential 3 — JWT Secret

**What it is:** The 256-bit random string you generated in section 7.1. Jenkins injects this into docker-compose as the `JWT_SECRET` environment variable.

**Add in Jenkins:**
- Kind: **Secret text**
- Scope: Global
- Secret: paste the JWT secret you generated in section 7.1
  - **Where to find it:** The value you generated with `node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"` — same value that is in your `.env` file
- ID: `marketly-jwt-secret`
  - **Important:** This exact ID is referenced in `Jenkinsfile` line 51. Do not change it.
- Description: `JWT signing secret`
- Click **Create**

**After adding all three, verify they appear in the list:**

Go to **Manage Jenkins → Credentials → System → Global credentials** and confirm you see:
- `docker-registry-credentials`
- `deploy-server-ssh`
- `marketly-jwt-secret`

---

## 9. Configure Jenkins Tools

Jenkins needs to know where JDK, Maven, and Node.js are installed on your machine.

Go to: **Manage Jenkins → Tools**

### 9.1 JDK Configuration

Scroll to **JDK installations** → click **Add JDK**

- Name: `JDK-21`
  - **Important:** This exact name is in `Jenkinsfile` line 28: `jdk 'JDK-21'`
- Uncheck **"Install automatically"**
- JAVA_HOME: the directory where you installed JDK 21
  - **How to find it:** Run in PowerShell:
    ```powershell
    (Get-Command java).Source
    # Example output: C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot\bin\java.exe
    # JAVA_HOME = everything before \bin\java.exe
    # So: C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot
    ```
  - Or check the installer default: `C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot`

### 9.2 Maven Configuration

Scroll to **Maven installations** → click **Add Maven**

- Name: `Maven-3.9`
  - **Important:** This exact name is in `Jenkinsfile` line 27: `maven 'Maven-3.9'`
- Uncheck **"Install automatically"**
- MAVEN_HOME: the directory where you extracted Maven
  - **How to find it:** Run in PowerShell:
    ```powershell
    (Get-Command mvn).Source
    # Example output: C:\Program Files\Apache\maven\bin\mvn
    # MAVEN_HOME = everything before \bin\mvn
    # So: C:\Program Files\Apache\maven
    ```

### 9.3 Node.js Configuration

Scroll to **NodeJS installations** → click **Add NodeJS**

- Name: `Node-22`
  - **Important:** This exact name is in `Jenkinsfile` line 29: `nodejs 'Node-22'`
- Uncheck **"Install automatically"**
- Installation directory: where Node.js is installed
  - **How to find it:** Run in PowerShell:
    ```powershell
    (Get-Command node).Source
    # Example output: C:\Program Files\nodejs\node.exe
    # Use: C:\Program Files\nodejs
    ```

Click **Save** at the bottom.

---

## 10. Understanding the Jenkinsfile

**Location in project:** `d:\Retailer_Website\Jenkinsfile`

**Purpose:** Defines the full automated build-deploy process. Jenkins reads this file and executes it as a pipeline.

### Structure overview

```
pipeline {
    agent any              ← run on any Jenkins agent (your local machine)
    tools { ... }          ← use JDK-21, Maven-3.9, Node-22 (names must match section 9)
    environment { ... }    ← variables used across all stages
    options { ... }        ← build settings (timeout, log rotation)
    stages {
        stage('Checkout')           ← pulls code from Git
        stage('Backend: Test')      ← runs mvn test
        stage('Backend: Build JARs')← runs mvn package -DskipTests
        stage('Frontend: Build')    ← runs npm ci && npm run build
        stage('Docker: Build')      ← builds 9 Docker images (in parallel)
        stage('Docker: Push')       ← pushes images to Docker Hub
        stage('Deploy')             ← runs docker compose up on your machine
    }
    post { ... }           ← cleanup after build
}
```

### The environment block — what each variable means

```groovy
environment {
    REGISTRY   = 'docker.io/marketly'
    IMAGE_TAG  = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
    DEPLOY_USER = 'ubuntu'
    DEPLOY_HOST = '192.168.1.100'
    DEPLOY_PATH = '/opt/marketly'
    JWT_SECRET  = credentials('marketly-jwt-secret')
}
```

| Variable | What it is | Where the value comes from |
|---|---|---|
| `REGISTRY` | Docker Hub registry prefix for image names | Your Docker Hub username (changed in next section) |
| `IMAGE_TAG` | Tag applied to all Docker images | Auto-generated from Git commit hash — no action needed |
| `DEPLOY_USER` | SSH username for deploy server | For local deploy: your Windows username (changed in next section) |
| `DEPLOY_HOST` | IP of the deploy server | For local deploy: `localhost` (changed in next section) |
| `DEPLOY_PATH` | Path on server where docker-compose.yml lives | Full path to backend/ folder (changed in next section) |
| `JWT_SECRET` | JWT signing secret | Read from Jenkins credential `marketly-jwt-secret` — automatically injected |

---

## 11. Update the Jenkinsfile for Local Deploy

The Jenkinsfile currently has placeholder values. You need to replace them with values specific to your machine.

### Change 1 — Set your Docker Hub username

**Find in Jenkinsfile line 36:**
```groovy
REGISTRY = 'docker.io/marketly'
```

**Change to:**
```groovy
REGISTRY = 'docker.io/YOUR_DOCKER_HUB_USERNAME'
```

**Where to find your Docker Hub username:**
1. Go to https://hub.docker.com
2. Log in
3. Your username appears in the top-right corner
4. Example: if your profile URL is `hub.docker.com/u/john123`, your username is `john123`

**After change:**
```groovy
REGISTRY = 'docker.io/john123'
```

### Change 2 — Remove SSH, use local docker compose instead

Since Jenkins and Docker are on the **same machine**, you don't need SSH to deploy. Replace the entire Deploy stage with a simpler direct docker-compose call.

**Find in Jenkinsfile lines 286-322 (the Deploy stage):**
```groovy
stage('Deploy') {
    steps {
        sshagent(credentials: ['deploy-server-ssh']) {
            sh """
                ssh -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} '
                    cd ${DEPLOY_PATH}
                    docker compose pull
                    docker compose up -d ...
                    docker image prune -f
                '
            """
        }
    }
}
```

**Replace with:**
```groovy
stage('Deploy') {
    steps {
        dir('backend') {
            sh '''
                docker compose pull
                docker compose up -d --remove-orphans
                docker image prune -f
            '''
        }
    }
}
```

**Also remove the unused environment variables** from the environment block (lines 42-48):
```groovy
// DELETE these three lines:
DEPLOY_USER   = 'ubuntu'
DEPLOY_HOST   = '192.168.1.100'
DEPLOY_PATH   = '/opt/marketly'
```

**Open the Jenkinsfile and make these two changes now:**
```powershell
# Open in Notepad
notepad d:\Retailer_Website\Jenkinsfile
```

---

## 12. Create the Jenkins Pipeline Job

This creates the job in Jenkins that ties everything together.

1. Open Jenkins at `http://localhost:8080`

2. Click **"New Item"** (top left)

3. Enter item name: `marketly`

4. Select **"Pipeline"**

5. Click **OK**

6. In the configuration page:

   **General section:**
   - Check **"Discard old builds"**
   - Max # of builds to keep: `10`

   **Build Triggers section:**
   - Check **"Poll SCM"**
   - Schedule: `H/5 * * * *`
     - This means: check for new commits every 5 minutes. When there is a new commit, run the pipeline automatically.

   **Pipeline section:**
   - Definition: **"Pipeline script from SCM"**
   - SCM: **Git**
   - Repository URL: the path to your project on disk
     ```
     file:///d:/Retailer_Website
     ```
     - **Where this comes from:** The full path to the root of this codebase on your machine
   - Branch: `*/main` (or `*/master` depending on your default branch name)
     - **How to check your branch name:**
       ```powershell
       cd d:\Retailer_Website
       git branch
       # The branch with * is your current branch
       ```
   - Script Path: `Jenkinsfile`
     - **Where this comes from:** The Jenkinsfile is in the root of the project at `d:\Retailer_Website\Jenkinsfile`

7. Click **Save**

---

## 13. Run the Pipeline

### Before running — ensure Docker Desktop is running

Check the system tray (bottom right of Windows taskbar). Docker Desktop shows a whale icon. It must be green/running.

```powershell
# Verify Docker is running
docker ps
# If Docker is running: shows a table (possibly empty)
# If Docker is not running: shows "error during connect"
```

### Trigger the first build

1. In Jenkins, click on your `marketly` job
2. Click **"Build Now"** (left sidebar)
3. A build appears under **"Build History"** (bottom left) — click the `#1` link
4. Click **"Console Output"** to watch the build in real time

### What you will see in the console output

```
[Checkout] Checking out from Git...
[Backend: Test] Running mvn test...
[Backend: Build JARs] Running mvn package -DskipTests...
[Frontend: Build] Running npm ci && npm run build...
[Docker: Build] Building 9 images in parallel...
  Building identity-service...
  Building tenant-service...
  ...
[Docker: Push] Pushing to Docker Hub...
[Deploy] Running docker compose up...
```

### Expected build times (first run)

| Stage | Time | Why |
|---|---|---|
| Backend: Test | 3-5 min | Maven downloads all Java dependencies first time |
| Backend: Build JARs | 5-8 min | Compiles all 9 modules |
| Frontend: Build | 3-5 min | npm downloads packages, Vite compiles TypeScript |
| Docker: Build | 8-15 min | Maven runs again inside Docker, downloads dependencies again |
| Docker: Push | 2-5 min | Uploads ~1 GB of images to Docker Hub |
| Deploy | 1-2 min | Docker pulls images and starts containers |
| **Total first run** | **~30-40 min** | Subsequent builds: ~10-15 min (Docker layer caching) |

---

## 14. Verify the Deployment

After the pipeline shows **"SUCCESS"** (green), verify everything is running.

### Check all containers are running

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

**Expected output:**
```
NAMES                        STATUS                    PORTS
marketly-frontend            Up 2 minutes (healthy)    0.0.0.0:3000->3000/tcp
marketly-api-gateway         Up 3 minutes (healthy)    0.0.0.0:8080->8080/tcp
marketly-identity-service    Up 4 minutes (healthy)    0.0.0.0:8081->8081/tcp
marketly-tenant-service      Up 4 minutes (healthy)    0.0.0.0:8082->8082/tcp
marketly-product-service     Up 4 minutes (healthy)    0.0.0.0:8083->8083/tcp
marketly-customer-service    Up 4 minutes (healthy)    0.0.0.0:8084->8084/tcp
marketly-order-service       Up 4 minutes (healthy)    0.0.0.0:8085->8085/tcp
marketly-notification-service Up 4 minutes (healthy)  0.0.0.0:8086->8086/tcp
marketly-analytics-service   Up 4 minutes (healthy)    0.0.0.0:8087->8087/tcp
marketly-postgres            Up 8 minutes (healthy)    0.0.0.0:5432->5432/tcp
marketly-redis               Up 8 minutes (healthy)    0.0.0.0:6379->6379/tcp
marketly-kafka               Up 7 minutes (healthy)    0.0.0.0:9092->9092/tcp
marketly-zookeeper           Up 8 minutes              2181/tcp
```

All containers should show `(healthy)` in the STATUS column. If any show `(unhealthy)` or `Exiting`, see section 15.

### Check the API gateway is responding

```powershell
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Test admin login

```powershell
curl -s -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"admin@marketly.com","password":"Admin@1234"}'
```

**Expected response contains:**
```json
{"success":true,"data":{"accessToken":"...","user":{"roles":["SUPER_ADMIN"]}}}
```

### Open the application

Open your browser and go to:

| URL | What you should see |
|---|---|
| `http://localhost:3000/admin` | Super Admin sign-in page |
| `http://localhost:3000/onboarding/seller` | Seller registration wizard |
| `http://localhost:3000/s/bakery/admin` | Bakery store owner sign-in |
| `http://localhost:3000/s/bakery` | Bakery customer storefront |

### Login credentials

| Who | URL | Email | Password |
|---|---|---|---|
| Super Admin | `http://localhost:3000/admin` | `admin@marketly.com` | `Admin@1234` |
| Bakery Seller | `http://localhost:3000/s/bakery/admin` | `seller@bakery.com` | `Admin@1234` |

---

## 15. Troubleshooting

### Problem: Container shows `(unhealthy)` or `Exiting`

**Diagnose:**
```powershell
# See the last 50 lines of logs for a specific container
docker logs --tail 50 marketly-identity-service

# Replace "marketly-identity-service" with the name of the failing container
```

**Common causes:**

| Symptom in logs | Cause | Fix |
|---|---|---|
| `Connection refused` to postgres | Postgres not ready yet | Wait 60 seconds, run `docker compose restart identity-service` |
| `JWT_SECRET too short` | .env file not loaded | Check `.env` file exists in `backend/` and JWT_SECRET is set |
| `Port already in use` | Something else using port 8080 | Stop the other process or change the port in docker-compose.yml |

---

### Problem: Jenkins build fails at "Backend: Build JARs"

**Diagnose:** Look at the console output in Jenkins — it shows the exact Maven error.

**Common cause:** Maven can't download dependencies (no internet connection, or corporate proxy blocking Maven Central).

**Fix:** Check your internet connection and retry:
```powershell
# In the Marketly job: click "Build Now" again
```

---

### Problem: Docker build fails — "no space left on device"

Docker images accumulate over time and fill disk space.

```powershell
# See how much space Docker is using
docker system df

# Remove all unused images, stopped containers, and unused networks
docker system prune -a
```

---

### Problem: Frontend shows blank page or "Cannot connect to API"

The frontend can't reach the API gateway.

```powershell
# Check api-gateway is running
docker ps | findstr api-gateway

# Check api-gateway logs
docker logs --tail 30 marketly-api-gateway
```

If the api-gateway container is healthy but the frontend can't reach it, check that `API_GATEWAY_URL=http://api-gateway:8080` is set in the frontend container's environment (it is in docker-compose.yml).

---

### Problem: "Invalid email or password" when logging in as admin

The V8 database migration may not have run, or ran with the wrong password hash.

```powershell
# Check if the admin user exists with the correct hash
docker exec marketly-postgres psql -U marketly -d marketly -c "SELECT email, substring(password_hash,1,7) as hash_start FROM identity.users WHERE email='admin@marketly.com';"
```

**Expected:** `hash_start = $2b$12$`

If the row doesn't exist, the V8 migration didn't run. Restart identity-service:
```powershell
docker compose -f backend/docker-compose.yml restart identity-service
```

---

### Useful commands reference

```powershell
# Start everything (from backend/ directory)
cd d:\Retailer_Website\backend
docker compose up -d

# Stop everything
docker compose down

# Stop and delete all data (nuclear option — resets database)
docker compose down -v

# View logs of all services at once
docker compose logs -f

# View logs of one service
docker logs -f marketly-identity-service

# Restart one service after a code change
docker compose restart identity-service

# Check resource usage
docker stats
```

---

## Quick Reference — File Locations

| File | Path | Purpose |
|---|---|---|
| Backend Dockerfile | `d:\Retailer_Website\backend\Dockerfile` | Builds any Spring Boot service into a Docker image |
| Frontend Dockerfile | `d:\Retailer_Website\Dockerfile` | Builds the React/Node.js SSR app into a Docker image |
| docker-compose.yml | `d:\Retailer_Website\backend\docker-compose.yml` | Defines and connects all 13 containers |
| .env | `d:\Retailer_Website\backend\.env` | Secret values — you create this, never commit it |
| Jenkinsfile | `d:\Retailer_Website\Jenkinsfile` | Jenkins pipeline definition |
| .env.example | `d:\Retailer_Website\backend\.env.example` | Template for the .env file |

---

## Quick Reference — All Ports

| Port | Service | Access URL |
|---|---|---|
| 3000 | Frontend | `http://localhost:3000` |
| 5432 | PostgreSQL | (internal only) |
| 6379 | Redis | (internal only) |
| 8080 | API Gateway | `http://localhost:8080` |
| 8081 | Identity Service | `http://localhost:8081/actuator/health` |
| 8082 | Tenant Service | `http://localhost:8082/actuator/health` |
| 8083 | Product Service | `http://localhost:8083/actuator/health` |
| 8084 | Customer Service | `http://localhost:8084/actuator/health` |
| 8085 | Order Service | `http://localhost:8085/actuator/health` |
| 8086 | Notification Service | `http://localhost:8086/actuator/health` |
| 8087 | Analytics Service | `http://localhost:8087/actuator/health` |
| 8090 | Kafka UI | `http://localhost:8090` |
| 9090 | Prometheus | `http://localhost:9090` |
| 9092 | Kafka | (internal only) |
