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

Only explicitly implemented authenticated routes are allowed by SecurityConfiguration. Enable the corresponding token, invitation and Android push configuration before using those features; all other routes remain denied.

## Firebase identity verification

Firebase is disabled by default. To enable the verification adapter, set
`FIREBASE_IDENTITY_ENABLED=true` and `FIREBASE_PROJECT_ID` to the expected Firebase
project. The adapter uses Application Default Credentials and a managed, named
Firebase app; no credentials belong in this repository or `application.yml`.

Verification checks revocation and accepts only Google and Apple sign-in identities.
`FIREBASE_IDENTITY_CLOCK_SKEW` defaults to `60s` and accepts zero to 300 whole seconds.
The authentication exchange/refresh endpoints reuse this verification boundary and the configured backend session-token and personal-data protection services.
Firebase unit tests mock the SDK and do not contact Google.

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

## Android push reminders

See [Android notification setup and delivery guarantees](../frontend/docs/android-notifications.md) for FCM, authenticated device registration, scheduler OIDC, fixed reminder preferences, privacy handling and live-device checks.

## Android Profile and Settings

See [Profile and Settings setup](../frontend/docs/profile-settings.md) for profile/device contracts, theme persistence, account deletion, Firebase permissions and cleanup-job operations.

Android onboarding and versioned legal consent: [setup and publication](../frontend/docs/onboarding-consent.md).

Android request attestation defaults to `PLAY_INTEGRITY_MODE=observe` so local
verification needs no Google credentials. See [production security setup](../docs/production-security.md)
for official decoding, protected write scope, replay migration V14, signing
allowlists, enforcement rollout and ingress requirements.

Android candidate verification: run `python3 scripts/verify-pre-release.py` from
the repository root. It requires running Docker and rejects skipped integration
tests; see [closed-testing preparation](../docs/android-closed-testing.md).
