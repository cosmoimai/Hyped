# Private Android testing on a Mac (no KMS/Firestore billing)

This runs the real Spring Boot API and PostgreSQL on the Mac. Google sign-in
still uses real Firebase and needs internet plus a private Firebase Admin service
account credential. No Cloud KMS, Firestore, public backend deployment, or paid
Cloud Scheduler is required for the basic account/room tests below. It is not a
fully offline Google-authentication emulator or a production configuration.

## 1. Prepare local keys

From the repository root:

```sh
python3 scripts/local-backend.py prepare
open "$HOME/.config/hyped/local-backend"
```

The command creates owner-only local files outside Git, preserves existing keys,
and explicitly selects `local` profile, local key provider and `127.0.0.1:8080`.
It generates a matching RSA signing key pair, separate identity/invitation keys
and a 256-bit local wrapping key. No cloud resources are created. An existing
registry with a missing wrapping key fails instead of silently generating a new
key over inaccessible data.

## 2. Supply free Firebase Admin credentials

Use the **same Firebase project as the Android client**:

1. Firebase Console → Settings → Project settings → Service accounts.
2. Under Firebase Admin SDK, Generate new private key and download its JSON.
3. Move it into the folder opened above and rename it `firebase-admin.json`.
4. Do not replace the Android `google-services.json` with this file. Do not put
   the Admin file under the project, paste it into chat, screenshot it, or commit
   it. This is a server credential and must never be installed on the phone.

The launcher checks project identity and restricts file permissions. If an
organization disallows downloadable service-account keys, stop and use its
approved credential mechanism rather than bypassing that policy. Authentication
needs Firebase user-management permissions; the Firebase-generated Admin service
account must be reviewed before sharing/distribution. No Owner/Editor grant or
billing upgrade is required by this local adapter.

The Android client must already have Google enabled, its debug SHA fingerprints
registered, and refreshed `frontend/android/app/google-services.json` with a web
OAuth client. That setup alone does not configure the backend's Admin credentials.

[Firebase Admin setup](https://firebase.google.com/docs/admin/setup)

## 3. Start PostgreSQL

On the current development Mac, the `hyped-postgres` container already exists:

```sh
docker start hyped-postgres
```

For a **new checkout only**, if that container does not exist:

```sh
docker run --name hyped-postgres \
  -e POSTGRES_DB=hyped -e POSTGRES_USER=hyped -e POSTGRES_PASSWORD=hyped \
  -p 127.0.0.1:5432:5432 \
  -v hyped-dev-postgres:/var/lib/postgresql/data \
  -d postgres:17-alpine
```

These are development-only database defaults. The private `environment.json`
contains the connection settings; update it if your existing local database uses
other credentials. Do not reset/delete an existing database or its matching key
registry to troubleshoot a login error. Java 21 and the repository Maven wrapper
are required; Flutter/Android SDK and adb are required for the phone.

## 4. Configure private consent testing

```sh
python3 scripts/local-backend.py consent
```

Enter a responsible operator/name and a real contact email for this **private
local test**, then deliberately confirm. This enables the bundled draft documents
only in the loopback backend so consent/create/join can be tested. It does not
approve the legal text or publish public policies. Leaving this unconfigured
allows sign-in and policy viewing but blocks consent acceptance/create/join.
Never copy these local flags into deployment configuration.

## 5. Run the backend

```sh
python3 scripts/local-backend.py start
```

Leave that terminal open; Ctrl+C stops it. The private log is
`~/.config/hyped/local-backend/backend.log`; never share the whole log. Startup
runs Flyway against the configured local PostgreSQL database. A missing/foreign
Firebase credential is rejected before Maven starts. No raw key, token or private
configuration is printed by the launcher. In another terminal:

```sh
curl --fail http://127.0.0.1:8080/api/v1/system/health
```

Wait for HTTP 200 before using the phone. A healthy skeleton alone does not prove
Firebase permissions or successful session exchange; verify a real app sign-in.
If startup fails, share the sanitized error class/message, not the environment,
credential file, JWTs, FCM tokens, request bodies or full logs.

## 6. Connect and launch the phone

Keep USB debugging enabled and the phone unlocked:

```sh
adb devices
adb reverse tcp:8080 tcp:8080
cd frontend
flutter run -d "$(adb get-serialno)" \
  --dart-define=HYPED_API_BASE_URL=http://127.0.0.1:8080/api/v1
```

These commands assume one connected Android device. For several, explicitly
select the same device in both adb and Flutter. On a real phone `10.0.2.2` does
not refer to the Mac; use the USB reverse mapping and loopback URL above. The
backend stays bound to the Mac's loopback interface. This HTTP override is for
debug builds only; release HTTPS/signing/domain guards are unchanged.

## Manual sequence and remaining boundaries

Use two disposable accounts and ideally a second installation. Follow
[the physical-device checklist](android-device-smoke-test.md):

1. Sign in, cancel/retry, accept explicit consent, restart and inspect Settings.
2. Create/edit rooms; check local event time/theme, revision conflicts and roles.
3. Generate a room code, preview signed out, sign in/consent and explicitly join.
4. Promote/demote/remove/transfer with two accounts; verify lost access on sync.
5. Leave/archive/restore under documented eligibility rules. Real 24-hour windows
   still apply; do not change the Mac clock or add bypass endpoints.
6. Map small/medium widgets to different rooms, edit/refresh and tap each.
7. Load rooms online, go offline, cold-open cached list/detail, reject writes,
   reconnect and verify server authority/last-sync. A USB network reverse tunnel
   may remain usable during airplane mode: stop the backend or remove the adb
   reverse mapping to guarantee the room API is unreachable for this test.
8. Logout and switch accounts; verify no old cache/widget/pending invite content.

The current helper keeps **push delivery and permanent account deletion disabled**
until their credentials and protected worker tick are configured. Do not mark
reminder delivery, archive auto-purge or final deletion cleanup PASS just because
sign-in/room tests work. They use `/internal/jobs/tick` with verified Google OIDC
scheduler identity; do not remove worker authorization to test locally. Enabling
those integrations is a separate setup step, and does not inherently require a
paid Cloud Scheduler deployment. Real FCM needs internet, permissions and a worker;
verified HTTPS App Links additionally need an owned domain. Integrity observe
mode keeps local debug testing usable but is not production attestation evidence.
No account-deletion or reminder success is claimed by creating this setup.

## Local key-storage limits

The production default remains the Cloud KMS/Firestore adapter. Local storage
requires `hyped.personal-data.provider=local`, `hyped.personal-data.enabled=true`,
active `local` profile, no prod/production/staging profile, a private directory
outside Git, a pre-existing 32-byte wrapping key with mode 0600, and server binding
`127.0.0.1`. The directory must be mode 0700. No silent cloud-to-local fallback.

The local registry reuses per-user AES-GCM envelope encryption and account/field
binding. Only wrapped per-user keys and metadata go into atomic versioned files;
profile/room data stays in its normal backend database. A process lock prevents
multiple backend instances from writing the same registry. Destroyed-key records
retain a tombstone and cannot be recreated on restart. Corrupt files or insecure
permissions fail closed with sanitized errors. Authentication verification,
session ownership, roles, consent and API authorization are not bypassed.

This is a single-Mac developer facility, not a KMS security equivalent or a
production cryptographic-erasure/retention guarantee. Local backups, snapshots,
SSD remnants and copies of wrapping keys may retain old capabilities. Keep test
accounts disposable and the local database/keys together; never migrate this key
material into production or upload it with support reports. Losing the wrapping
key makes the matching test profiles inaccessible. No backend schema or public
API contract changes are introduced.
