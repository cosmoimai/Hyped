# Staging backend setup

Staging uses the existing Firebase project `hyped-41a30` for Android authentication,
with separate application data, encryption keys, secrets, and service identities.
Use synthetic staging data. Never copy the local PostgreSQL database or local key
registry to the cloud. Firebase account deletion still affects this shared Firebase
project; use dedicated testing accounts for deletion exercises.

## Resource inventory

| Resource | Staging configuration |
|---|---|
| Google Cloud project | `hyped-41a30` |
| Cloud Run / Artifact Registry region | `asia-southeast1` |
| Artifact Registry | `hyped-staging`, Docker, immutable tags |
| Neon | Dedicated staging project; connection kept outside Git |
| Firestore | `hyped-staging`, Standard, Native, `asia-southeast1` |
| KMS | `hyped-staging/personal-data`, software encrypt/decrypt, 90-day rotation |
| API identity | `hyped-staging-api@hyped-41a30.iam.gserviceaccount.com` |
| Migration identity | `hyped-staging-migrate@hyped-41a30.iam.gserviceaccount.com` |
| Scheduler identity | `hyped-staging-scheduler@hyped-41a30.iam.gserviceaccount.com` |

These resources were bootstrapped through the console/CLI. They are not yet managed
by checked-in Terraform. Import and review them before treating infrastructure as
reproducible or deploying production. No production deployment is authorized by
this staging runbook.

The API identity has `roles/cloudkms.cryptoKeyEncrypterDecrypter` on only the staging
key. Its `roles/datastore.user` project binding has the condition
`resource.name=="projects/hyped-41a30/databases/hyped-staging"`. Mobile clients must
have deny-all Firestore Security Rules for this registry. Database-scoped IAM does
not replace these rules. Migration and scheduler identities do not need key access.
No downloadable service account keys are required: Cloud Run uses service identity.

## Backend image and migration process

Build and verify from the repository root:

```sh
cd backend
./mvnw clean verify
docker build --platform linux/amd64 --tag hyped-backend:local .
```

On older Docker Desktop versions that fail while loading an attestation manifest,
use `docker buildx build --platform linux/amd64 --provenance=false --load --tag hyped-backend:local .`
for the local smoke-test image. Production provenance needs a compatible builder
and registry; this workaround does not provide build attestation.

The Dockerfile accepts only the verified Maven JAR from `backend/target`. Its build
context uses an allowlist; configuration, credentials, and source are excluded.
Both Java 21 base images are pinned by digest. The runtime uses UID/GID 10001 and
executes Java directly so shutdown signals reach Spring Boot. `PORT` selects the
Cloud Run listener, bound to `0.0.0.0`. Images default to disabling Flyway at API
startup. Never enable it on Cloud Run service instances.

The same image runs a separate migration-only job by overriding its command:

```sh
java -cp '/opt/hyped/classes:/opt/hyped/lib/*' com.hyped.app.deployment.DatabaseMigration
```

Required environment variables are `DATABASE_URL` (PostgreSQL JDBC),
`DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Supply them through numeric Secret
Manager version references, not CLI values, image arguments, or Git. Use the direct
Neon endpoint for migration, certificate-verified TLS, and a separate restricted
runtime database role for the API. Never deploy the API using Neon owner credentials.

The migration process starts no Spring context, web server, Firebase, KMS, or
Firestore clients. It validates applied checksums, runs pending versioned migrations,
then validates again. Clean and automatic baseline are disabled. Repeat runs are
safe; database/migration failures return exit code 1 with sanitized diagnostics.
Success returns 0. Deploy application traffic only after the job succeeds.

Integration tests exercise fresh migrations and repeat execution on PostgreSQL 17
and 18, including failed authentication. This is migration compatibility coverage;
the existing application integration suite uses PostgreSQL 17.

The image must be built from a reviewed commit, tagged with its full SHA, scanned,
uploaded privately, and referenced by digest for the migration job and service.
Packaging alone does not deploy the backend. Follow [the deployment plan](11-deployment.md)
for IAM, backups, scheduler protection, smoke testing, and rollback requirements.

The image scan uses `.trivyignore.yaml`. The two existing, expiring Spring MVC
applicability reviews also name the exact packaged `spring-webmvc-6.2.19.jar` path.
The scanner reports these library CVEs; Hyped has neither XSLT view rendering nor
SSE view fragments, which the official advisories require for exploitation. This
is an applicability decision, not a patched-library claim. Reassess before the
2026-11-24 exception expiry, changing the framework version, or adding views/SSE.

## Remaining setup gates

- Create independent staging secrets and the restricted runtime database role.
- Verify deny-all Firestore client rules and runtime IAM using real service identity.
- Validate migrations against the empty staging database before API deployment.
- Configure Firebase permissions, protected scheduling, and deletion cleanup.
- Configure media/moderation providers separately; keep unavailable features disabled.
- Import infrastructure into Terraform and complete backup/restore and monitoring setup.
- Publish reviewed legal documents and configure an invite domain before public release.

Never include credentials in screenshots, logs, deployment evidence, or support reports.
