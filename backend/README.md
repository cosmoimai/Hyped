# Hyped! backend

Spring Boot modular-monolith skeleton for the Hyped! MVP.

## Requirements

- Java 21
- Docker, for PostgreSQL integration tests
- No globally installed Maven is required; use the Maven Wrapper

## Verify

```bash
./mvnw verify
```

The build compiles MapStruct processors, runs unit and architecture tests, starts PostgreSQL with Testcontainers for the integration test, applies Flyway from an empty database, and runs Checkstyle.

If Docker is unavailable, the Testcontainers integration test is skipped. It must pass on a Docker-enabled machine before release.

## Run locally

Start PostgreSQL and create a database and user matching the defaults in `application.yml`, or set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

```bash
./mvnw spring-boot:run
```

The public probes are:

- `GET /api/v1/system/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

All other routes are denied until their authentication and authorization contracts are implemented.

## Package boundaries

Feature modules live directly below `com.hyped.app` and expand only when implementation begins:

```text
feature/
├── api/
├── application/
├── domain/
└── infrastructure/
```

ArchUnit enforces the internal dependency direction. Cross-feature access must use an application facade/port or durable outbox event.

