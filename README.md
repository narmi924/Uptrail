# Uptrail

**Staff Training & Approvals.** An implementation of the SA63 Course Application Tracking System (CATS).

Employees apply for internal training, external courses and professional certifications; managers approve or reject with a reason; administrators maintain staff, routing, entitlements, the course catalogue and the public holiday calendar. Training days and budgets are tracked in an annual ledger that separates pending reservations, approved commitments and reimbursed claims.

> Status: under active development. `Plan.md` records what is implemented and verified so far.

## Technology

Java 21 · Spring Boot 4.1 (Spring MVC, Thymeleaf, Spring Security, Spring Data JPA) · MySQL 8.4 · Flyway · Bootstrap 5 (served locally) · JUnit, Testcontainers, ArchUnit.

## Prerequisites

- JDK 21
- Docker (for the local MySQL and mail capture services, and for the integration tests)

Maven does not need to be installed: use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Run locally

```bash
docker compose up -d                                        # MySQL on 127.0.0.1:3307, Mailpit on 1025/8025
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev       # http://localhost:8080
```

The `dev` profile uses the Compose database with local-only default credentials. Copy `.env.example` to `.env` to change them.

Health check: `curl http://localhost:8080/actuator/health`.

To start again from an empty database: `docker compose down -v` (removes only this project's volumes), then `docker compose up -d`.

## Build and test

```bash
./mvnw test                   # unit and architecture tests
./mvnw verify                 # plus integration tests against MySQL 8.4 in Testcontainers
./mvnw -DskipTests package    # executable JAR in target/
```

Integration tests need Docker. Without Docker, point them at an isolated, disposable database with `UPTRAIL_TEST_DB_URL`, `UPTRAIL_TEST_DB_USER` and `UPTRAIL_TEST_DB_PASSWORD`; the tests empty its tables.

## Documents

- `Plan.md` — implementation plan, decisions, traceability and verification evidence
- `DESIGN.md` — user interface design system
- `CLAUDE.md` — repository conventions
