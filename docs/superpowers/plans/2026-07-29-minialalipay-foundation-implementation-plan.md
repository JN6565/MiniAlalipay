# MiniAlalipay Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a compilable Java 21 Monorepo foundation with Maven modules, shared technical contracts, a production-shaped API gateway, domain-local enums, deployment dependencies, and verification tests.

**Architecture:** The repository contains an independently built Maven backend and reserved frontend/contracts/tests/deploy areas. Backend services follow the bounded contexts in the system analysis; `platform-common` contains only technical types, while business enums remain in their owning service. Gateway behavior and common contracts are implemented test-first.

**Tech Stack:** Java 21, Maven 3.9, Spring Boot 3.3.4, Spring Cloud 2023.0.3, Spring Cloud Gateway, JUnit 5, AssertJ, Reactor Test, Docker Compose, MySQL 8, Redis 7.

## Global Constraints

- Use Java 21 and UTF-8.
- Keep `gateway`, `user-center`, `business-center`, `account-center`, and `ai-service` as independently executable modules.
- Keep business entities, enums, Mapper types, and repositories out of `platform-common`.
- Use `long` fen for monetary API values; do not use floating-point money.
- Redis is infrastructure state only and is never a source of truth for balances or ledgers.
- Do not implement real payment-channel integration.

---

### Task 1: Maven Reactor and Repository Skeleton

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/*/pom.xml`
- Create: `.gitignore`, `README.md`

**Interfaces:**
- Produces: a Java 21 Maven reactor containing six modules.

- [ ] Create the parent POM with pinned Spring Boot and Spring Cloud BOMs.
- [ ] Create module POMs with only the dependencies required by each runtime.
- [ ] Run `mvn -f backend/pom.xml validate` using JDK 21 and expect `BUILD SUCCESS`.

### Task 2: Technical Common Contracts

**Files:**
- Test: `backend/platform-common/src/test/java/com/minialalipay/common/api/ApiResponseTest.java`
- Test: `backend/platform-common/src/test/java/com/minialalipay/common/trace/RequestIdGeneratorTest.java`
- Create: `backend/platform-common/src/main/java/com/minialalipay/common/api/ApiResponse.java`
- Create: `backend/platform-common/src/main/java/com/minialalipay/common/error/CommonErrorCode.java`
- Create: `backend/platform-common/src/main/java/com/minialalipay/common/error/BusinessException.java`
- Create: `backend/platform-common/src/main/java/com/minialalipay/common/trace/RequestIdGenerator.java`

**Interfaces:**
- Produces: `ApiResponse.success`, `ApiResponse.failure`, `BusinessException`, and `RequestIdGenerator.resolve`.

- [ ] Write tests proving response envelopes and request-ID preservation/generation behavior.
- [ ] Run module tests and verify compilation fails because the production types are absent.
- [ ] Implement the smallest immutable technical types that satisfy the tests.
- [ ] Run module tests and expect all tests to pass.

### Task 3: Gateway Foundation

**Files:**
- Test: `backend/gateway/src/test/java/com/minialalipay/gateway/filter/RequestIdGlobalFilterTest.java`
- Create: `backend/gateway/src/main/java/com/minialalipay/gateway/GatewayApplication.java`
- Create: `backend/gateway/src/main/java/com/minialalipay/gateway/filter/RequestIdGlobalFilter.java`
- Create: `backend/gateway/src/main/java/com/minialalipay/gateway/filter/SecurityHeadersGlobalFilter.java`
- Create: `backend/gateway/src/main/java/com/minialalipay/gateway/error/GatewayExceptionHandler.java`
- Create: `backend/gateway/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `RequestIdGenerator`, `ApiResponse`, and `CommonErrorCode` from `platform-common`.
- Produces: `X-Request-Id` propagation, baseline security headers, JSON gateway failures, and environment-driven routes.

- [ ] Write WebFlux filter tests for preserved and generated request IDs.
- [ ] Run gateway tests and verify the filter type is missing.
- [ ] Implement filters, exception handling, route configuration, and the boot entry point.
- [ ] Run gateway tests and expect all tests to pass.

### Task 4: Bounded-Context Service Skeletons

**Files:**
- Create: boot applications and `application.yml` for `user-center`, `business-center`, `account-center`, `ai-service`.
- Test/Create: status behavior tests and enums in each owning module.
- Create: `package-info.java` files for interfaces/application/domain/infrastructure boundaries.

**Interfaces:**
- Produces: independently executable service modules and explicit status behavior such as `TransactionStatus.isTerminal()` and `AccountStatus.allowsDebit()`.

- [ ] Write failing tests for meaningful enum behavior in each context.
- [ ] Verify tests fail because enums do not exist.
- [ ] Implement domain-local enums and service startup/configuration files.
- [ ] Run each module test suite and expect success.

### Task 5: Local Infrastructure and Contracts

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `deploy/mysql/init/00-create-schemas.sql`
- Create: `contracts/openapi/minialalipay-api.yaml`
- Create: `frontend/README.md`, `tests/README.md`, `deploy/README.md`

**Interfaces:**
- Produces: local MySQL/Redis dependencies, owned schemas, a minimal health/error OpenAPI contract, and reserved Monorepo boundaries.

- [ ] Define MySQL schemas and least-surprise local credentials through environment defaults.
- [ ] Define Redis health checks and persistent volumes.
- [ ] Define a valid OpenAPI 3.1 baseline for health and standard errors.
- [ ] Validate Compose configuration and inspect the OpenAPI document as YAML.

### Task 6: Full Verification

**Files:**
- Modify: `README.md` with exact setup and verification commands.

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: reproducible local build instructions.

- [ ] Run `mvn -f backend/pom.xml test` under JDK 21.
- [ ] Run `docker compose -f deploy/docker-compose.yml config` when Docker is available.
- [ ] Scan for forbidden cross-module persistence dependencies and misplaced common-domain classes.
- [ ] Record prerequisites and any unavailable external-runtime checks in `README.md`.
