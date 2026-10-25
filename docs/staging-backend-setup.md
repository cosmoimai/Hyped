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

## Private staging credentials

The staging bootstrap generates fresh RSA-3072 signing keys, independent 256-bit
identity/invitation keys, and independent database passwords. Local preparation
files live under `~/.config/hyped/staging` with directory mode 0700 and file mode
0600. They are never part of the Docker context or Git. The original local backend
credentials and Firebase Admin configuration are not copied to staging.

Secret Manager uses the `hyped-staging-` prefix and replication in Singapore:

| Secret suffixes | Reader |
|---|---|
| `db-migration-url`, `db-migration-username`, `db-migration-password` | Migration identity only |
| `db-api-url`, `db-api-username`, `db-api-password` | API identity only |
| `token-private-pem`, `token-public-pem`, `identity-hmac` | API identity only |
| `invite-encryption`, `invite-hmac` | API identity only |

Deployments reference numeric versions. Identity HMAC is stored as raw bytes;
invitation keys are base64 values; signing keys are PEM resources. Secret values
are passed to Secret Manager through stdin, never CLI arguments or deployment logs.
Initial versions are checked against their private sources without printing values.
Rotate through new versions and an explicit deployment change; never regenerate
keys merely to restart a deployment.

Database roles are created with SQL, not the Neon role-creation console, which
grants elevated Neon role membership. `hyped_staging_migrator` can connect and
create application schemas in the staging database. `hyped_staging_api` can connect;
after migration it receives only schema usage, table CRUD, and sequence permissions
for `app` and `ops`. Neither role has superuser, database-creation, role-creation,
replication, or RLS-bypass privileges. Runtime must not receive membership in the
migration role or access to the Flyway schema. Revoke public schema creation.

JDBC connections use `sslmode=verify-full` and
`org.postgresql.ssl.DefaultJavaSSLFactory` so both the certificate chain and host
are verified against the Java trust store. Migration uses the direct Neon endpoint;
the API uses its pooled endpoint. Test those connections using the actual image
and service identities before directing an Android build to staging.

Container upload uses a private Docker configuration. If an older Docker credential
helper fails, a short-lived OAuth login can be used in a temporary, restricted
directory that is removed after upload. Never download a service account JSON key
or copy a long-lived credential into a build context as a workaround.

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

- Verify Firestore and KMS access using the running API service identity. Deny-all
  Firestore client rules were confirmed manually by the operator on 2026-10-26;
  the local CLI could not independently read the Firebase Rules configuration.
- Deploy the API with its restricted database role and independent secret references.
- Configure Firebase permissions, protected scheduling, and deletion cleanup.
- Configure media/moderation providers separately; keep unavailable features disabled.
- Import infrastructure into Terraform and complete backup/restore and monitoring setup.
- Publish reviewed legal documents and configure an invite domain before public release.

Never include credentials in screenshots, logs, deployment evidence, or support reports.

## Staging checkpoint: 2026-10-26

- All 11 initial secret versions were uploaded and verified without printing payloads.
- Separate migration/runtime database roles were created and their administrative
  privilege flags checked. Runtime cannot create objects in `app`/`ops`, access the
  Flyway schema, or inherit the migration role. Its application CRUD permissions
  and future-object default grants were verified.
- The three staging service accounts have no user-managed keys or broad project roles.
- The private image from source commit `a55e6a6` was uploaded with its full commit
  tag and selected by digest for job `hyped-staging-migrate`.
- Execution `hyped-staging-migrate-qvxfb` completed successfully. All 16 migrations
  are applied, and the staging application user table is empty.
- The actual Java 21 container connected through the pooled Neon endpoint with
  runtime credentials and verified certificate/hostname configuration.
- These infrastructure checks do not establish end-to-end authentication,
  reminders, media, deletion, backups, or production readiness. No Cloud Run API
  service or Android staging build was deployed at this checkpoint.
