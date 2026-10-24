# Android and backend production hardening

## Rollout and trust boundary

Play Integrity supplements JWT verification, current account/session/device checks,
room authorization, ETags, idempotency, and rate limits. It does not identify a
user, replace room authorization, prove that all app behaviour is safe, or provide
an iOS attestation implementation. No certificate pinning is introduced (security
design section 14 retains operating-system trust and safe certificate rotation).

Backend `PLAY_INTEGRITY_MODE` accepts exactly `disabled`, `observe`, `enforce`:

| Mode | Behaviour |
| --- | --- |
| disabled | No attestation verification, replay access, or integrity rate access |
| observe (default) | Evaluate submitted evidence and count bounded outcomes; allow missing/failed evidence. Distributed authenticated-write rate limits still apply. |
| enforce | Require verified evidence for protected writes; reject missing/invalid/replayed evidence with safe 403 and dependency failures with safe 503. |

Start in observe. Deploy Android acquisition to an internal Play track, configure
Google access and signing allowlists, measure verdict/outage/latency rates, then
switch a staging deployment to enforce. Promote selected production traffic via
normal revision/traffic rollout only after testing real licensed devices. Keep
all revisions serving the same cohort in the same mode; an observing replica can
otherwise accept a request that an enforcing replica rejects. Roll back to
observe during a provider incident, not a client-controlled bypass. There is no
permanent account ban based on attestation. Metrics use only mode/outcome labels,
never user/session identifiers, tokens, verdict JSON, or request bodies.

Protected operations are authenticated non-GET/HEAD/OPTIONS requests under
`/api/v1/`, excluding `/auth/**`, `/public/**`, and `/me/deletion-requests`.
This covers room creation/edit/join/membership/lifecycle actions, invitation
rotation, policy acceptance and device push settings. Public previews and reads
remain usable. Authentication bootstrap/refresh, logout/revocation, and account
cleanup remain protected by their existing authentication/recent-auth rules and
are exempt from integrity enforcement so users can recover and remove access
when Google is unavailable. Attestation for unauthenticated authentication
bootstrap is not claimed by this implementation; ingress abuse limits remain
required. Old clients work in observe and cannot perform protected writes in
enforce until upgraded.

## Android acquisition and request binding

Native `AndroidIntegrityBridge` uses official Play Integrity **Standard requests**,
SDK 1.6.0. It prepares a token provider on first use, shares that in-memory provider,
and clears it after failure so a later explicit request can prepare again. No
integrity token or provider verdict is persisted. `PLAY_INTEGRITY_PROJECT_NUMBER`
is a Gradle property or build environment variable, default `0` (acquisition off).
This public project number is not a service credential. Flutter's platform call
has an eight-second timeout; evidence failure does not invent a passing verdict.
The backend alone chooses observe/enforce behaviour.

The authenticated Dio pipeline adds evidence after authentication, offline write
checks and cache invalidation. Public preview Dio remains interceptor-free.
Every retry creates a fresh UUID and obtains fresh evidence, including after JWT
refresh. Business Idempotency-Key and revision headers remain unchanged. The pipeline
captures the authenticated session and rechecks it after evidence acquisition;
logout/account change cancels a waiting write before transport. Its retry is
fenced to that session so a failed write cannot execute as the next account.
Auth-token diagnostics redact the session identifier as well as credentials.
The exact UTF-8 JSON body is frozen before transport. Binding is SHA-256, encoded
as unpadded base64url, over newline-separated fields in this exact order:

```text
hyped-integrity-v1
UPPERCASE_HTTP_METHOD
raw request path (including /api/v1)
raw query or empty
SHA256_BASE64URL(exact body bytes; empty if no body)
SHA256_BASE64URL(exact Authorization header)
X-Integrity-Id (canonical UUID v4 from the app)
X-Integrity-Time (Unix milliseconds as canonical decimal)
SHA256_BASE64URL(Idempotency-Key or empty)
SHA256_BASE64URL(If-Match or empty)
```

The digest is passed to Standard API `requestHash`. Only that digest is shared
with Google; no JWT, invite credential or request body is in the hash input sent
to Google. `X-Play-Integrity` carries the encrypted opaque token to Hyped, with
`X-Integrity-Id` and `X-Integrity-Time`. Method/path/query, authentication, body,
idempotency and revision are recomputed on the backend. Existing routes use ASCII
paths and no encoding aliases; keep this contract when adding routes. Evidence
is bounded to 32 KiB and bodies to 256 KiB, including chunked requests.

## Official backend validation and replay handling

`GoogleIntegrityDecoder` calls only
`https://playintegrity.googleapis.com/v1/com.hyped.hyped:decodeIntegrityToken`,
using Application Default Credentials and OAuth scope
`https://www.googleapis.com/auth/playintegrity`. It follows no redirects and uses
bounded connection/request timeouts. Provider exceptions and responses are never
logged or returned to clients. No client-decoded JSON or local token parser can
satisfy verification.

The verifier checks both package fields, exact requestHash, Google timestamp and
client timestamp (at most 120 seconds old and ten seconds ahead),
`PLAY_RECOGNIZED`, an approved `certificateSha256Digest`,
`MEETS_DEVICE_INTEGRITY`, and `LICENSED`. Enforce refuses startup without an
approved certificate allowlist. `PLAY_INTEGRITY_CERTIFICATE_DIGESTS` is a
comma-separated list of **unpadded base64url SHA-256 certificate digests**, not
colon-separated Android fingerprint text. `PLAY_INTEGRITY_PACKAGE` defaults to
`com.hyped.hyped`. Do not add debug certificates to a production allowlist.

Google Standard requests have provider replay protection. V14 adds PostgreSQL
`ops.integrity_replay` with unique token and request digests, consumed atomically
before a protected action. Only digests and expiry are stored. This also prevents
concurrent replay between replicas and different tokens repeating the same bound
request. A failed business action needs fresh attestation on retry; retain its
business idempotency key. Entries expire after ten minutes and are pruned on
subsequent verified requests; stale evidence is already rejected after two
minutes. `ops.integrity_rate` bounds all protected writes to 60 per minute per
verified user/session digest across replicas, before spending Google quota. It
retains only a bounded count/minute and prunes old windows on use. In observe,
a replay-store outage permits business requests; enforce fails closed. Back up
neither short-lived operational table for security decision restoration; Google
freshness/replay checks and server recomputation always remain mandatory.

## Other audited boundaries and fixes

- Session verification now checks live owner-scoped session, revocation, expiry,
  linked device invalidation and JWT `did` installation match. Revoked JWTs cannot
  retain room access for their remaining token lifetime. Room services still
  enforce membership/role from verified subject and preserve transaction/row-lock
  ordering. Existing foreign-room and member authorization tests remain required.
- API responses retain Spring Security no-cache/no-store headers and explicit
  private/public route cache policies. API boundary adds nosniff, frame denial,
  no-referrer and a restrictive CSP; payload errors are safe and contain no
  rejected values. Default Spring error message/stack/binding details and request
  detail logging are explicitly disabled. Unexpected failures log a correlation
  UUID and fixed code only.
- Mobile-only CORS has no allowed origins or wildcard/credentialed policy; no
  `@CrossOrigin` exceptions are introduced. `forward-headers-strategy: none`
  remains deliberate. Never trust client X-Forwarded-For/Proto for identity,
  source throttles or redirects. At the trusted ingress enforce TLS/HSTS,
  distributed IP-prefix/global/auth-bootstrap limits, connection timeouts and
  request/header-size limits. Invitation local 10/minute limits are not a
  replacement for ingress throttling. Cloud Run direct access must not bypass
  that ingress policy. No unverified forwarded header enables HTTPS trust here.
- The only first-party exported activity is MainActivity (launcher and strict
  verified invite ingress). Widget receivers and FCM service remain private.
  SDK-exported Firebase identity callback activities are retained for SDK flows;
  Firebase FCM receiver and Google revocation service require their SDK signature
  permissions. Profile installer receiver requires `android.permission.DUMP`.
  Merged manifests, not just source declarations, must be audited after SDK updates.
- Widget and notification PendingIntents remain explicit and immutable; widget
  IDs and notification IDs/data distinguish intents. No mutable PendingIntent or
  arbitrary incoming room URL is introduced. Existing native parsing/routing tests
  cover untrusted inputs. Authenticated backend authorization remains mandatory.
- Unused Firebase Analytics dependency and transitive advertising permissions are
  removed. Analytics deactivation stays explicit; invite data is scrubbed before
  engine/plugin handling. Do not add token-bearing HTTP/SDK/router debug logging.
- Main/release network configuration denies cleartext and trusts system CAs only.
  Debug has an explicit development-only cleartext override for emulator/adb
  access. Release validates canonical HTTPS API `/api/v1`, owned-looking hostname,
  no credentials/ports/query/fragments, no IPs or reserved development hosts.
  Flutter repeats runtime validation. DNS ownership/TLS must still be verified.
- Release requires `INVITE_HOST`, non-debug signing key/certificate and local
  Firebase Android client configuration. Configure signing only via
  `HYPED_KEYSTORE_PATH`, `HYPED_KEYSTORE_PASSWORD`, `HYPED_KEY_ALIAS`,
  `HYPED_KEY_PASSWORD` in controlled local/CI environment. Validation loads the
  keystore without displaying its values; Android Debug certificates are rejected.
  There is no debug signing fallback. This does not certify Play App Signing or
  publish an artifact.
- Existing flutter_secure_storage Android options use the plugin's Android
  Keystore-backed encrypted storage, not plain preferences. Both cloud backup and
  device-transfer rules now exclude all app root/files/databases/preferences/
  external data, including secure storage and SQLite sidecars. `allowBackup=false`
  remains set. Logout/account change/deletion cleanup wrappers are retained.

## Development and Google Cloud deployment

1. Build/test locally with default backend observe and native project `0`, or set
   `PLAY_INTEGRITY_MODE=disabled` explicitly for isolated development. There is no
   production allow-all test token. `HYPED_DISABLE_FIREBASE=true flutter build apk
   --debug` also builds without a Firebase client configuration; Firebase sign-in
   then shows its existing safe configuration error. Production signing/config
   are not required for debug or tests.
2. Enable **Play Integrity API** in your Google Cloud project. Link that project
   to Hyped in Play Console > App integrity / Protected with Play. Supply its
   numeric project number when building Android. Use separate development and
   production projects and approved test responses in Play Console.
3. Use an attached runtime service account/Workload Identity for ADC in the linked
   project, with only the permissions needed for Play Integrity API access. Check
   the official console/API access requirements for that project. Do not create
   or commit downloadable service-account keys. Local ADC is optional and outside
   Git. Verify a real server decode from the staging runtime before enforcement.
4. Register `com.hyped.hyped`, configure Play App Signing, obtain the actual app
   signing certificate and base64url SHA-256 digest (the upload certificate alone
   is insufficient). Configure backend allowlists and observe first. Use real
   Play internal-track installs to test recognition/licensing; sideloaded debug
   builds normally do not satisfy enforce policy. No real Google decode or
   production certificate verification is claimed by local automated tests.
5. Test clean device, missing evidence, offline/provider outage, revoked session,
   account change, edited body, replay and token refresh. Monitor aggregate
   `hyped.integrity.decisions` counters via existing private monitoring; do not
   expose actuator metrics publicly. Confirm quota/budget alerts before ramp-up.
6. Complete the separate [App Links deployment](../frontend/docs/android-app-links.md).
   A domain is still not selected. No release build or domain association can be
   delivered until domain, signing and backend endpoint choices exist.

## Local dependency and secret scanning

Install `gitleaks` 8.30.1 and `trivy` 0.76.1, then run
`scripts/security-scan.sh`. The GitHub workflow invokes the same local tools with
read-only repository permission and checksum-verified, versioned downloads.
Neither scanner uploads source, credential values, token evidence or scan reports.
Trivy downloads a public vulnerability database; `--offline-scan` avoids remote
package-resolution APIs. Secret output is always 100% redacted. Scan full Git
history and the working tree; build/dependency caches are excluded, not source.

The initial scan found 18 high/critical dependency entries. Patch overrides for
Jackson 2.21.7, Netty 4.1.137.Final, HTTP Core 5.4.3, Tomcat 10.1.59 and PostgreSQL
42.7.12 address available patch fixes without migrating Spring's major version.
Two Spring entries are scoped in `.trivyignore.yaml` to `backend/pom.xml`, expiring
2026-11-24: CVE-2026-47884 requires XsltView/view rendering; CVE-2026-47890 requires
SSE view fragments. Hyped has REST JSON controllers and neither feature. Reassess
before adding either feature or at expiry; the supported Spring 6 fix requires
enterprise access, while the OSS fix is Spring 7. No other vulnerability is
blanket-suppressed. Run the scan again after dependency or architecture changes.

Gitleaks identified two historical commits containing a Firebase Android **client
API key**, not a private service credential. The unused tracked root config and
its macOS metadata copies are removed from the current tree; local files stay
available and ignored. `.gitleaksignore` exempts only those exact reviewed history
fingerprints and current local client-key findings; new committed keys and all
private/service credentials still fail. Firebase documents these keys as public
identifiers, but their Cloud API restrictions and quotas still need manual review:
restrict them to the required Firebase APIs, Android package/signing certificate
where supported, and verify no unrelated billable API is enabled. If a key was
used outside those restrictions, rotate it via Cloud and update local client
configuration; Git history rewriting is not performed here.

## Verification

```sh
cd backend && ./mvnw clean verify
cd ../frontend
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug
cd ../..
python3 scripts/check-android-release.py
scripts/security-scan.sh
git diff --check
```

Release checks use a short-lived, locally generated signing fixture in a temporary
directory, test rejection of development URLs, missing/domain/debug signing and
Firebase bypasses, and exercise a valid configuration if local Firebase client
configuration exists. No production key or signed release artifact is required.
Run the merged-manifest assertions in `scripts/check-android-manifest.py` after
building. OS-level malicious intent and Play/GCP verdict behaviour still need
real-device/internal-track checks before enforcement.

Sources: [Standard request flow](https://developer.android.com/google/play/integrity/standard),
[verdict interpretation](https://developer.android.com/google/play/integrity/verdicts),
[Google setup](https://developer.android.com/google/play/integrity/setup),
[Firebase client API keys](https://firebase.google.com/docs/projects/api-keys),
[Spring XsltView advisory](https://spring.io/security/cve-2026-47884),
[Spring SSE advisory](https://spring.io/security/cve-2026-47890).
