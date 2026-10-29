# Android Play submission candidate

**Status: BLOCKED. Nothing submitted or published.** Automated checks do not
establish production cloud readiness, legal approval, or completion of closed
testing. Never upload a fixture-signed, debug, or unconfigured build.

## Identity audit

| Item | Candidate / decision |
|---|---|
| Android package / namespace | `com.hyped.hyped`; confirm ownership and intended Play application before first upload; immutable application identity thereafter |
| Display name | `Hyped!` (launcher and Flutter application) |
| Version source | `frontend/pubspec.yaml`: `0.1.0+1`; versionName `0.1.0`, versionCode `1` |
| Build-number gate | Confirm highest used code across all Play tracks; reserve a higher unused code if necessary; do not assume 1 is available |
| Launcher assets | Original vector H/countdown mark in existing soft-blue colors; legacy vector for API 24–25 and adaptive foreground/background for API 26+; stock Flutter PNGs removed |
| Icon approval | Release owner must review adaptive masking and launcher appearance on physical devices; store PNG export/approval still required |
| SDK / device | Candidate targets API 36, minimum API 24; recheck actual release manifest and current Play target-API requirements before submission |

## Production configuration gates

Supply through excluded local configuration or restricted CI secrets; never paste
credentials into commands, logs, issues, or this document:

- Four signing environment values: `HYPED_KEYSTORE_PATH`,
  `HYPED_KEYSTORE_PASSWORD`, `HYPED_KEY_ALIAS`, `HYPED_KEY_PASSWORD`. Keystore must
  be outside Git or explicitly ignored and untracked. Release has no debug-key
  fallback; validation opens the key and rejects Android Debug certificates.
- `HYPED_API_BASE_URL`: actual deployed canonical `https://HOST/api/v1` with no
  credentials, port, query, or fragment. Localhost, IP and reserved/example hosts
  are rejected. Verify HTTPS, health, backend revision and authentication against
  that deployment. A plausible hostname passing validation is not proof it works.
- `INVITE_HOST`: owned HTTPS invite domain, currently unavailable. Publish the
  existing assetlinks contract with **Play app-signing** SHA-256 plus any approved
  test certificates, and verify links on a Play-installed build. No Dynamic Links.
- Excluded `frontend/android/app/google-services.json` plus public environment
  `HYPED_FIREBASE_PROJECT_ID`. Release validation requires a matching selected
  project, package `com.hyped.hyped`, Android app ID/project number and an API-key
  entry. This does not verify API-key restrictions or designate a project as
  production automatically. Owner must approve the project and compare backend
  Firebase project, OAuth clients, Play signing SHA-1/SHA-256, consent screen,
  enabled Google sign-in, FCM HTTP v1/service-account IAM and device delivery.
- Play Integrity Cloud project/linking: follow `production-security.md`; start
  observe mode only after live verification. Missing production credentials must
  never silently become enforce mode. Do not log attestation/token material.
- Confirm deployed key-registry destruction, scheduler operation, encrypted
  backups/retention and restore/deletion-journal procedures. Repository policy is
  not evidence that cloud retention has been configured.

Reproduce locally (no upload tasks are called):

```sh
python3 scripts/verify-pre-release.py --require-aab --last-play-version-code LAST_USED_CODE
python3 scripts/review-release-dependencies.py
```

The AAB, checksum and verification receipt are stored in ignored
`build/verification/android-<UTC>/`. Release verification checks signature,
certificate, bundle metadata and merged manifest using bundletool. Do not use
release-test fixture keys as real credentials. Archive the exact verified binary,
SHA-256, source commit, backend revision and symbols privately. See
`android-crash-symbols.md`: Crashlytics is not integrated; no mapping upload has
been performed. Do not claim Crashlytics coverage in a release review.

## Store listing and Console worksheet

All unchecked items below require the release owner's review and later manual
Console work. This preparation grants no authority to upload or edit Console.

- [ ] App title: proposed **Hyped!** (6 characters, limit 30); trademark/ownership review.
- [ ] Short description (draft, below 80): **Private shared countdowns, event reminders and home-screen widgets.**
- [ ] Full description (draft, below 4,000):

  Plan something worth looking forward to with Hyped! Create a private countdown
  room, invite people with a link or room code, and keep the event in view.

  See event dates in your local time, manage room participants, and choose event
  reminders for 24 hours before, one hour before, and event time. Add Android
  home-screen widgets for your selected rooms. Previously synchronized room data
  remains available for read-only viewing offline.

  Sign in with Google to create or join rooms. An internet connection is required
  to synchronize changes and deliver reminders. Notification delivery depends on
  permission, connectivity and device settings. Widget countdown text refreshes
  when the widget updates; it is not continuously network-synchronized.

  Hyped! is for adults aged 18 and over. Manage your profile, devices and account
  deletion in Settings.

- [ ] Use screenshots from the final configured candidate on physical/emulated
  Android phones; remove personal profiles, invite tokens/codes and unrelated
  notifications. Capture room list, countdown detail, member controls, widgets and
  settings using non-personal staged data. Do not show unimplemented features.
  Provide at least 2 phone screenshots (up to 8 per device type; dimensions
  320–3,840 px with longest side no more than twice shortest); review current
  Console requirements and accessibility/localization.
- [ ] Feature graphic: original approved 1,024 × 500 JPEG or 24-bit PNG without
  alpha. No stock Flutter logo, misleading rankings, pricing badges or fake UI.
- [ ] Store icon: approved 512 × 512 32-bit PNG, maximum 1,024 KB, matching native
  countdown mark. Do not upload adaptive XML as the store image.
- [ ] Public monitored support email/contact and developer identity supplied by
  owner; none has been invented or placed in Git.
- [ ] Public HTTPS Privacy Policy and Terms URLs, readable without authentication.
  Current generated legal drafts/versioning require owner/legal approval and
  real contact/controller details; placeholders are not publication-ready.
- [ ] Public account-deletion request URL: implement/publish a working web request
  resource with Hyped/developer identity, deletion steps, data/retention details
  and identity-verification process. In-app instructions alone are insufficient.
  Do not ask users to send passwords, JWTs or invite tokens. Support verification
  must not permit deletion by knowing an email address alone.
- [ ] Account deletion instructions: Settings → Delete account → deliberate
  confirmation/recent sign-in when requested. Transfer/delete owned rooms and
  leave joined rooms first. Explain pending deletion and provider-cleanup retry;
  see `play-data-safety.md`. Verify both web and in-app paths live.
- [ ] App access/review instructions: Google sign-in, adult consent, permissions
  and room setup. Provide any review-only credentials through Console privately
  if required; no embedded test accounts or committed credentials.
- [ ] Content rating questionnaire: answer for actual private user-generated
  room titles/descriptions and participant interaction. No chat, public feed or
  member search in MVP. Do not guess or preassign an IARC rating.
- [ ] Target audience: adults 18+ consistent with consent and product policy;
  not designed for children. Complete regional/audience questions truthfully.
- [ ] Ads declaration: current code has no ads SDK or advertising identifiers;
  proposed answer **No**, subject to final resolved-SDK audit.
- [ ] Data Safety: owner reviews `play-data-safety.md`, actual cloud/provider
  configuration and **all active versions/tracks**; do not simply answer “no data
  collected.” Confirm encryption in transit, deletion access and sharing exceptions.
- [ ] Permissions: INTERNET and contextual POST_NOTIFICATIONS plus SDK network,
  wake-lock, credential and FCM delivery permissions in merged manifest (see
  audited list below). No photo/media,
  storage, camera, contacts or location permission. No exact-alarm/full-screen
  intent requirement. Verify Console's generated declarations against final AAB.
- [ ] Privacy/license notices and runtime downloaded fonts reviewed; resolve
  dependency-license inventory gaps before approval.
- [ ] Closed-testing eligibility and physical matrix completed; see
  `android-closed-testing.md` and `android-device-smoke-test.md`. No testers
  contacted and no claim of a completed 14-day test.
- [ ] Release approval, rollout and two-hour monitoring owner assigned privately;
  follow `android-rollout.md`. Do not publish as part of these scripts.

## Source and permission audit scope

`debugShowCheckedModeBanner` is false. Release API selection has no development
fallback, release network security denies cleartext and release is not debuggable.
Test fixtures and emulator endpoints exist in test/development source; they are
not production endpoints. A real release-binary manifest/resource audit is still
required once configuration exists. No embedded test-account credentials were
identified. An absent AAB cannot be certified free of every debug artifact.

Notifications request Android 13+ permission only following the explicit Enable
notifications action; initialization is not an automatic prompt. No photo picker
or upload UI is implemented, so there is no photo permission request. If added
later, use the system picker without broad library access and update disclosures.

## Official references (recheck before manual submission)

- [Store assets and limits](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Firebase Android disclosure guide](https://firebase.google.com/docs/android/play-data-disclosure)

## Local audit evidence — 2026-10-24

- Full pre-release run: `build/verification/android-20261024T143512Z/summary.json`.
  Backend 567, Flutter 322, Android JVM 31: all passed, zero skips/failures.
  Seven verifier tests and 16 Gradle release-configuration checks passed.
  Flutter 3.38.9 / Dart 3.10.8 / Java 21.0.12; formatting, analysis, Android lint,
  debug APK, merged-manifest audit, local source/history secret and high/critical
  dependency scans passed. Receipts are private ignored working-tree evidence,
  not evidence of a production-configured or Play-installed release.
- Release `flutter build appbundle --release` was attempted and failed closed
  with the secure production API configuration requirement. **No release AAB,
  release checksum or live Firebase verification is available.** Do not substitute
  the debug APK or a disposable configuration-test certificate.
- Dependency/license inventory: 443 entries (140 Flutter packages including dev
  tools, 117 Android release graph modules, 186 backend runtime dependencies).
  Local report: `build/verification/licenses-20261024T144126Z/review.md` and
  `inventory.json` (license texts/hashes); raw resolved graphs alongside it.
  Public POM downloads use official Maven/Google/Flutter repositories and send
  artifact coordinates only, with no source/configuration upload.
- Five Android metadata gaps remain: `javax.inject:javax.inject:1` and the four
  Flutter engine/embedding modules at engine revision
  `587c18f873b8ab57330422bce09047420d9c7f42`. Review their source/distribution
  licenses and bundled engine third-party notices; do not infer approval from
  absent POM fields. Flutter package LICENSE files were found, using SDK root
  license for SDK packages where appropriate.
- Observed Android metadata includes Apache-2.0/BSD/public-domain licenses,
  Android SDK license and Play Integrity/Play Core terms. Backend additionally
  contains Hibernate LGPL-2.1-or-later, Logback EPL/LGPL dual metadata,
  Jakarta/JAXB EDL/EPL/GPL-with-classpath-exception and AspectJ EPL metadata.
  Backend service dependencies are not automatically Android-distributed code.
  Owner must review selected licenses, modifications, attribution/NOTICE and
  source/relinking obligations as applicable; no legal clearance is claimed.
  Runtime-downloaded Manrope requires its font license review separately from
  the `google_fonts` package code. Verify actual Flutter/native bundled notices
  in the signed release and include required distribution notices before release.
- Vulnerability scan has two existing scoped Spring applicability exceptions,
  CVE-2026-47884 and CVE-2026-47890, expiring 2026-11-24: no XSLT views or SSE/view
  fragments in this REST backend. Reassess before expiry/framework changes.
  Gitleaks only exempts previously reviewed exact public Firebase client-key
  fingerprints; private service credentials have no exemption.
- Merged debug permissions also include transitive `USE_BIOMETRIC`,
  `USE_FINGERPRINT`, `READ_GSERVICES` and an app signature-protected dynamic-receiver
  permission. Credential SDK dependencies declare biometric capability; no custom
  biometric prompt or raw biometric collection was found in Hyped. Do not equate
  a transitive declaration with collection of biometric templates. Re-audit the
  actual release manifest and SDK disclosure guidance before Console attestation.
