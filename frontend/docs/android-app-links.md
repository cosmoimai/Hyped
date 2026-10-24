# Android invitation App Links

The contract remains `https://<invite-domain>/invite/<token>` (HLD section 11).
No production domain has been selected. Default debug builds use the reserved
`unconfigured.invalid` manifest host and an empty runtime allowlist: external
links are disabled. Release builds fail when `INVITE_HOST` is unset. Room-code
entry and existing internal `/invite/:token` routes are unchanged. No iOS
Universal Links or Firebase Dynamic Links are implemented.

## Choose and configure the domain

Own one HTTPS hostname and serve it with a valid TLS certificate. Use exactly
that lowercase ASCII hostname, without scheme, port, path, or trailing dot:

```sh
cd frontend
INVITE_HOST=YOUR_OWNED_HOST flutter build apk --debug
INVITE_HOST=YOUR_OWNED_HOST flutter build appbundle --release \
  --dart-define=HYPED_API_BASE_URL=https://YOUR_API_HOST/api/v1
```

Release signing must also be supplied by the existing controlled signing setup.
Gradle accepts `-PINVITE_HOST=...` for direct Gradle builds. The environment or
Gradle value drives both the manifest placeholder and native BuildConfig;
Flutter receives the same allowlist through the platform channel. There is no
independent Dart hostname setting. Set backend `INVITATION_PUBLIC_BASE_URL` to
`https://YOUR_OWNED_HOST` so generated invitations match. A hostname configuration
alone does not prove ownership or complete Android domain verification.

## Digital Asset Links and signing

Serve this JSON at `https://YOUR_OWNED_HOST/.well-known/assetlinks.json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.hyped.hyped",
    "sha256_cert_fingerprints": ["REPLACE_WITH_PLAY_APP_SIGNING_SHA256"]
  }
}]
```

Serve HTTP 200 with `Content-Type: application/json`, without redirects or
login. Replace the placeholder with the certificate that signs the installed
APK. For Play distribution use the **Play app signing certificate**, not the
upload certificate. Obtain it from Play Console > App integrity. Include all
current installed signing certificates when performing a controlled key rotation.
No release certificate is configured or invented here. Keep keystores and private
keys out of Git; public SHA-256 fingerprints are safe to publish.

Get local debug/release signing reports with:

```sh
cd frontend/android
./gradlew :app:signingReport
```

This workstation's debug SHA-256 at implementation time:
`4A:54:5B:1C:F6:1D:3F:8B:03:20:FE:25:63:1C:36:45:21:FC:54:35:BB:52:A9:A3:19:BC:FC:E8:D4:E4:82:69`.
It changes when the debug keystore changes. Verify again before use. Prefer a
separate owned test hostname for debug builds; publishing a debug fingerprint
on production authorizes apps signed with that debug key to handle production
links. Do not publish the reserved automated-test host `links.hyped.example`.

## Validation, navigation, and privacy

The exported singleTop activity has an HTTPS VIEW/BROWSABLE/DEFAULT filter with
`autoVerify=true` for the exact configured host and `/invite/` prefix. Both the
native validator and Flutter validator narrow this to exactly
`/invite/[A-Za-z0-9_-]{43}`, matching the backend's 32-byte base64url tokens.
They reject userinfo, ports, alternate/sub/lookalike hosts, trailing dots,
non-ASCII/control characters, query strings, fragments, percent encoding,
traversal, extra path segments, unexpected schemes, and URLs over 512 characters.
Scheme and host casing are canonicalized; token casing is preserved.

Flutter automatic deep-link processing is disabled and go_router ignores the
platform default location. The native channel is the only external-link ingress.
Native handling consumes and clears VIEW intent data before forwarding to engine
plugins, validates cold and warm deliveries, and consumes the initial mailbox
once. Flutter validates again, fences delayed deliveries during cleanup, and
routes only the current pending intent. Public preview remains accessible before
authentication and consent; the same in-memory invite resumes through both gates.
Links never automatically join rooms. Explicit joining retains authenticated Dio,
refresh handling, and existing per-attempt idempotency keys. The controller also
reuses a successful join result instead of repeating the request.

Duplicate pending deliveries do not interrupt sign-in or consent. After completion,
a bounded in-memory set (at most 16 SHA-256 digests, 30-second suppression window)
prevents immediate OS replay from reopening the preview. Intentional later reopens
are allowed. Logout/account deletion/account change clear the pending credential,
native mailbox and digests; completion removes the raw credential. Generation
checks prevent older joins/navigation from consuming a newer pending invite.
No invite token is persisted in preferences, Drift, or disk. Process termination
may require reopening the original invitation; pending intent is not restored
from disk. Expired/rotated tokens use existing safe preview errors.

No raw links are logged or placed in exceptions or diagnostics by this bridge.
Firebase Analytics collection and automatic screen reporting are deactivated in
the manifest to prevent automatic URL/campaign capture. Keep HTTP logging,
router diagnostic logging, and third-party URL analytics disabled. Android itself
and adb may show launch arguments: use synthetic tokens for testing, never real
invitation credentials in shared terminal logs or screenshots.

## Web and Play Store fallback design

Host a generic invitation landing page at `/invite/<token>` for visitors without
the app or without verified handling. It should offer an installed-app/open-link
instruction and a fixed Play Store link:
`https://play.google.com/store/apps/details?id=com.hyped.hyped` (publish the listing
before enabling it). Do not put the token in the store URL, install referrer,
clipboard, analytics, or third-party requests. After installation users reopen the
original invitation, or enter their room code. No deferred-link service is used.

The page must not reveal a room title or private metadata in HTML, previews, or
Open Graph tags. Use first-party resources, no trackers, `Referrer-Policy:
no-referrer`, `Cache-Control: no-store`, `X-Robots-Tag: noindex, nofollow`, and a
restrictive Content Security Policy. Configure CDN/origin/access/error logging to
redact the `/invite/` token path before storage; never record the complete request
URL. Asset Links JSON remains publicly cacheable independently. Website hosting,
DNS, TLS and the Play listing are manual deployment tasks; no fallback website
is published by this branch.

## Device verification

Install a build configured for your owned test host. Publish its certificate
association first. Android 12+ verification commands:

```sh
adb shell pm set-app-links --package com.hyped.hyped 0 all
adb shell pm verify-app-links --re-verify com.hyped.hyped
adb shell pm get-app-links com.hyped.hyped
```

Allow verification time and check that the exact host reports `verified`. Launch
without specifying a package to test OS association. These commands intentionally
use a synthetic 43-character token, which should produce a safe invalid preview:

```sh
adb shell am force-stop com.hyped.hyped
adb shell am start -W -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'https://YOUR_OWNED_HOST/invite/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
```

Repeat while the app is running for warm launch. To test routing separately from
domain verification, append `com.hyped.hyped` to the `am start` command; this does
**not** prove App Links verification. With a private valid invitation, check cold
and warm preview, sign-in, consent, explicit join, correct room destination, and
repeated delivery during each gate. Check foreign hosts, malformed paths, expired
and rotated invitations, logout cleanup, and existing room-code entry. Never put
real tokens into committed scripts. OS verification cannot be certified until an
owned domain and deployed association file exist.

Automated coverage includes native parsing/mailbox tests, Flutter channel cold/
warm handoff, hostile URLs, duplicate suppression, cleanup races, expired/rotated
preview errors, explicit join deduplication, and router sign-in/consent return.
Run Flutter format/analyze/test/build and Android `:app:testDebugUnitTest
:app:lintDebug`. Native tests use a reserved example domain, not a network service.

References: [Android association files](https://developer.android.com/training/app-links/configure-assetlinks),
[Android verification](https://developer.android.com/training/app-links/verify-applinks),
[Flutter App Links](https://docs.flutter.dev/cookbook/navigation/set-up-app-links).
