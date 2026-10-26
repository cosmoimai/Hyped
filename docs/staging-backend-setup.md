# Staging backend setup

For the dated owner-reported passes and deferred checks, see the
[2026-10-26 staging testing handoff](staging-test-handoff-2026-10-26.md).

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

- Complete the real-phone edit/relaunch, invite/join, and logout/sign-in smoke tests.
- Configure notification/deletion permissions, protected scheduling, and deletion cleanup.
- Configure media/moderation providers separately; keep unavailable features disabled.
- Import infrastructure into Terraform and complete backup/restore and monitoring setup.
- Publish reviewed legal documents and configure an invite domain before public release.

Never include credentials in screenshots, logs, deployment evidence, or support reports.

## Staging API configuration

Use `hyped-staging-api` with the dedicated API service identity, pooled runtime
database credentials, numeric secret versions, and Flyway disabled. Set minimum
instances to zero, maximum instances to three, concurrency to 20 and the Hikari
maximum pool size to five. Use the same immutable image digest validated by the
separate migration process; never deploy using the migration database credentials.

The project-level custom role `hypedStagingAuthLookup` contains only
`firebaseauth.users.get`. It lets Firebase Admin check a cryptographically verified
user's disabled/revoked state without granting Firebase configuration or user-write
permissions. Account deletion and notification delivery need additional, separately
reviewed permissions and scheduled cleanup setup before those features are enabled.
The API uses attached service identity, not a downloaded Firebase Admin key.

The operator approved legal version `2026-10-26` **for staging only**. Set Terms,
Privacy and content-rules versions to that same value. Operator/contact details
remain in excluded configuration and render into the bundled templates. This
approval does not establish production legal approval or authorize Play publication.

Keep Play Integrity in `observe` mode. Keep media, notification delivery, account
deletion and dedicated moderation review disabled until their provider/operational
setup is verified. An invite domain is still unavailable: Android verified App Links
remain disabled, and sharing uses room codes. The Cloud Run HTTPS URL serves the
API; it is not an owned invitation web-fallback site.

Check readiness, missing/malformed bearer-token rejection, public legal documents,
and invalid public-preview responses before directing an Android build to staging.
Do not log private request/response bodies while testing authentication.

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

## API and Android checkpoint: 2026-10-26

- Cloud Run service `hyped-staging-api` is healthy, using source commit `604c533`
  and the dedicated runtime identity, secret version 1 references, min 0/max 3
  instances, concurrency 20, 1 CPU/1 GiB, and no API-startup migration.
- Android API base URL:
  `https://hyped-staging-api-jmi2i42hbq-as.a.run.app/api/v1`.
  Cloud Run also provides
  `https://hyped-staging-api-661950448866.asia-southeast1.run.app`.
- The same release image passed the separate migration job before deployment.
- Thirteen HTTP checks passed while Cloud Run access was private: readiness,
  approved policy/version publication, missing/invalid application JWT rejection,
  invalid Firebase proof/refresh rejection, invalid invite/code sanitization, and
  denial of internal/configuration endpoints. Invalid invitation credentials
  correctly return `410 INVITATION_INVALID` under the existing contract.
- A temporary Cloud Run job using the actual API service identity verified Firebase
  user-state lookup, a KMS authenticated encryption/decryption round-trip, and a
  Firestore transaction plus deletion of its synthetic probe document. It needed no
  new impersonation grants, service-account keys, database credentials, or real users.
  The temporary job was deleted after success.
- Deny-all Firestore mobile-client rules were confirmed manually by the operator.
  Runtime server access was independently verified through IAM by the probe above.
- Cloud Run permits public invocation so native Android clients and signed-out
  previews can reach the application. Spring Security still enforces authentication
  and authorization. Public readiness/policies return 200; unauthenticated room
  access and actuator configuration return 401. This is staging API access, not
  app-store publication or production approval.
- Backend clean verification: 642 tests; Flutter: 416 tests; Android JVM: 41 tests;
  no failures/skips. Flutter formatting/analyze and the debug APK build passed.
  The image scan has no unreviewed high/critical findings under the documented,
  expiring applicability exceptions. The staged source secret scan passed.
- The HTTPS staging debug APK was installed with an in-place, data-preserving
  update on the Samsung SM-M336BU and cold-launched. The operator completed real
  Google sign-in, required consent and test-room creation on the phone. A read-only
  staging check independently confirmed one active account, one unrevoked/unexpired
  session, one current-version acceptance and one active room without reading room
  content or credentials. Existing local rooms were not migrated into staging.

The compiled APK remains ignored at `frontend/build/app/outputs/flutter-apk/app-debug.apk`.
Verification evidence is ignored under `build/verification/staging-api/`.
No Git push, Play Console change, tester contact, or app-store upload was performed.

### Invite preview transaction boundary

Public previews capture the room, current invitation generation, and encrypted inviter
profile in a short database transaction. Firestore/KMS decryption runs after that
transaction releases its locks, as required by the personal-data adapter. Joining
always revalidates the invitation generation, so a concurrent rotation invalidates
an earlier preview reference. Integration tests enforce the no-transaction boundary
at every preview decryption and cover preview/rotation ordering.

## Media configuration checkpoint — 2026-10-26

Revision `hyped-staging-api-00006-nxz` enables media with private R2 bucket
`hyped-staging-room-photos`. The runtime uses `R2_MEDIA_ENDPOINT` and
`R2_MEDIA_BUCKET`, plus versioned Secret Manager references:

| Runtime variable | Secret |
|---|---|
| `R2_MEDIA_ACCESS_KEY` | `hyped-staging-r2-media-access-key` |
| `R2_MEDIA_SECRET_KEY` | `hyped-staging-r2-media-secret-key` |

Read-only inspection verified enabled media, endpoint format, bucket name,
numeric secret versions, unchanged push and legal versions, and readiness HTTP 200.
No credential values were retrieved. The owner reported the bucket lifecycle rule,
scoped S3 credentials, Vision enablement and IAM setup; real image upload,
moderation and storage cleanup checks remain deferred in the
[testing handoff](staging-test-handoff-2026-10-26.md#media-configuration-checkpoint--2026-10-26).
Account deletion is still disabled at this checkpoint. Configuration readiness does
not establish photo-flow success or production release readiness.

## Account-deletion monitoring setup

The API identity has the separate owner-assigned Firebase deletion permission.
Account deletion remains disabled until the operational setup is verified; adding
permission does not delete an account or enable the endpoint.

The existing OIDC minute worker now emits aggregate deletion-backlog observations.
The checked-in [staging setup script](../scripts/configure-staging-deletion-alerts.py)
configures the operations channel's three policies: backlog/monitor failure,
scheduler errors, and missing successful heartbeat. It also configures the
aggregate-only log-based heartbeat counter. The recipient and channel resource
are kept outside Git. Reapplication updates the owned policies without duplicates.

The full [monitoring runbook](12-observability-runbook.md#staging-account-deletion-monitoring)
documents 12-hour warning and 24-hour overdue boundaries, missing-heartbeat warm-up,
alert latency and recovery. Policy creation alone is not proof of a live heartbeat
or delivered email. Observe scheduled logs and counter data after deployment;
record email delivery/recovery and disposable-account deletion tests separately.

Runtime verification: source `c005805`, revision `hyped-staging-api-00007-5pz`,
readiness HTTP 200. Ordinary scheduled runs emitted successful aggregate heartbeat
logs with zero pending jobs; Monitoring returned three heartbeat data points at
inspection. Three enabled policies referenced the chosen operations channel,
without duplicates after reapplication. Push/media remained enabled and account
deletion remained disabled. All 657 backend tests and 18 script tests passed.
No account deletion or synthetic email alert was triggered. Email delivery and
recovery exercises remain on the deferred checklist.
