# Android closed-testing preparation

This is a local preparation package, not approval to distribute a build. Nothing
here publishes to Play, uploads symbols, recruits testers, creates accounts, or
contacts anyone. Closed testing has **not started**. Do not enter tester names,
emails, phone numbers, Google accounts, serial numbers, or credentials into Git.

## Reproduce automated verification

Run from a checkout with Python 3, Java 21, the repository Maven wrapper, the
project's Flutter/Dart toolchain, Android SDK, Docker running, gitleaks and trivy:

```sh
python3 scripts/verify-pre-release.py
# Once real excluded signing + domain/API config are supplied:
python3 scripts/verify-pre-release.py --require-aab --last-play-version-code LAST_USED_CODE
```

The script runs its evidence-validation tests, locked Flutter dependency resolution,
backend `./mvnw clean verify`, formatting, analysis, full Flutter tests, Android
unit tests/lint, debug APK, merged-manifest audit, release/AAB configuration checks,
local security scans and Git checks. Zero tests, failures, skips, missing XML,
incomplete Flutter machine reports, and missing Docker fail verification. It
serializes runs with a local process lock and clears only generated Maven/test
outputs. Backend/dev subprocesses strip deployed provider/database/signing
variables; only an explicitly configured release build receives signing values.

Each run creates a private ignored `build/verification/android-<UTC timestamp>/`
with tool versions, stage logs, `summary.json`, test counts, source commit/dirty
status, and copied build artifacts with SHA-256 hashes. Never commit or publicly
upload these logs: test fixtures, local paths and diagnostic data can appear in
them. Rerun from the clean candidate commit before handing off an actual release.
Use the same lockfiles, recorded tool versions and candidate source to reproduce;
do not run dependency upgrades during candidate verification. Security database
updates can legitimately change scan results between runs.

`automated_checks_passed=true` is **not** permission to start closed testing.
`ready_to_start_closed_testing` and `ready_to_request_production_access` stay false
until their distinct human gates are evidenced. Physical-device, cloud, legal,
domain, crash-reporting and initial tester/authorization gates block entry. The
full 14-day/continuous-opt-in evidence is a later production-access gate, not a
prerequisite for starting the test. An unavailable
AAB is explicitly BLOCKED; default local checks can pass without production
credentials. `--require-aab` makes that blocker fail the run.

## Play submission handoff

Production submission gates, listing assets and release configuration are tracked in
[Play Store submission](play-store-submission.md), the
[Data Safety worksheet](play-data-safety.md), and the
[rollout/rollback runbook](android-rollout.md). Release builds also require the
public `HYPED_FIREBASE_PROJECT_ID` environment value to match the local Android
Firebase client file; presence of a Firebase file alone is insufficient.

## Candidate version and signing

`frontend/pubspec.yaml` is the version source; current initial candidate is
`0.1.0+1`. Do not infer that code 1 is unused in Play. The script passes explicit
`--build-name`/`--build-number` to both APK and AAB builds and validates AAB metadata.
Android `versionName` is `0.1.0`; `versionCode` is `1`. Before each candidate:

1. Inspect the highest versionCode across **all** Play tracks, including drafts
   and prior uploads. Record that number privately and pass it to the script.
2. Increase the pubspec build number to a unique integer above that number
   (maximum 2,100,000,000). Never reuse an uploaded code, even for a withdrawn build.
3. Keep the semantic version for compatible fixes during this test; change it
   deliberately when product scope/version changes. Do not auto-bump on every
   local verification. The release owner reserves each code in the private ledger.
4. Record source commit, versionName+versionCode, backend revision, dependency/tool
   versions, artifact SHA-256 and symbol/mapping archive location. Preserve the
   exact artifact and symbols; do not rebuild a binary under the same code later.
5. Test upgrade from the previous candidate without clearing app data. A necessary
   rollback is a new build code containing the known-good source, not a downgrade.

Signing reads **only** these process environment variables from excluded local/CI
configuration: `HYPED_KEYSTORE_PATH`, `HYPED_KEYSTORE_PASSWORD`, `HYPED_KEY_ALIAS`,
`HYPED_KEY_PASSWORD`. Existing `android/key.properties` is ignored but **not read**
by this Gradle setup. No secret belongs in pubspec, Gradle source, command-line
password arguments or committed shell scripts. Keep the keystore outside Git or
at an explicitly ignored untracked path. The verifier checks exclusion and actual
AAB signer identity; Gradle rejects Android Debug certificates and missing keys.
Play App Signing's installed certificate differs from the upload key: configure
Firebase OAuth fingerprints, Integrity allowlists and assetlinks for the installed
Play certificate. A debug APK is not a closed-track candidate.

For a real AAB, also provide `HYPED_API_BASE_URL` (canonical HTTPS `/api/v1`),
`INVITE_HOST` (owned host), local ignored Firebase Android client configuration,
and official `bundletool` on PATH or a verified local `BUNDLETOOL_JAR`. The script
builds `frontend/build/app/outputs/bundle/release/app-release.aab`, checks the JAR
signature, runs bundletool validation, checks package/version/non-debuggable HTTPS
manifest and matching invite host, and compares the signer to the configured
upload certificate. It preserves Dart symbols and R8 mapping when generated.
It never uses its disposable release-configuration fixture to produce a candidate.

## Feature verification matrix

Full automated suites exercise contracts/controllers/security against fakes and
isolated PostgreSQL; they do not prove live Google, FCM, OS or OEM behaviour.
Use [physical-device smoke tests](android-device-smoke-test.md) for every row.

| Feature | Automated evidence (frontend tests; backend test classes) | Physical/cloud gate |
| --- | --- | --- |
| Google sign-in | google_identity_provider, auth_*; FirebaseIdentityTokenVerifier, AuthenticationExchangeServiceIntegration | Play-signed OAuth, cancellation, clean/new and existing account |
| Token refresh | authentication_interceptor, production_security; RefreshSessionServiceIntegration | Valid refresh after access expiry; expired/revoked session clears private data |
| Logout | auth_repository, settings, offline_room_cache; AuthenticationController | Failed network logout still clears local tokens, cache, widgets, pending invite/tap |
| Device limit recovery | auth_api, auth_screens, settings; ActiveDeviceManagementIntegration | Reach documented active-device limit; revoke one safely; sign in again |
| Room create/edit | room_api/controllers/widgets; RoomController, RoomRepositoryIntegration | Local timezone/date, stale ETag, offline write denial |
| Invite preview/join | invitation_*, invite_app_links; InvitationIntegration | Public preview, sign-in/consent return, exactly one explicit membership |
| Members | member_management; RoomRepositoryIntegration, RoomController | OWNER/CO_HOST/MEMBER visibility, confirmation, foreign room denial |
| Lifecycle | room_lifecycle, router; RoomLifecycleIntegration | Leave, transfer, archive, restore/deletion eligibility and cleanup |
| Reminders | reminder_*, push_notifications; RoomReminderIntegration, PushDeliveryIntegration | Real FCM, all 24h/1h/event boundaries, retries/cancellation, no duplicate presentation |
| Widgets | android_widget; Android WidgetSnapshot tests | Small/medium, independent mappings, local time, launcher/resize, offline/logout cleanup |
| Offline cache | offline_room_cache; server membership/lifecycle tests | Cold offline read, last-sync, no writes, account isolation and access-loss sync |
| App Links | invite_app_links; Android InviteAppLink tests | Owned verified host, Play certificate association, cold/warm/auth/consent routes |
| Account deletion | settings, auth_repository; AccountDeletionIntegration, AccountController | Disposable account only, recent auth, Firebase cleanup and backend retention rules |

Use the detailed filenames in `frontend/test/` and classes in
`backend/src/test/java/`; Android JVM tests are in `frontend/android/app/src/test/`.
Physical outcomes stay PENDING until observed; never promote mocked coverage to
a live provider result. Latest generated summary is the automated evidence source.

## Device and Android coverage

Current Flutter defaults compile this project with minimum API 24 and target API
36; verify actual APK/AAB manifest for the candidate because Flutter upgrades can
change these. Aim for at least these physical combinations across 12 slots:

| Slots (anonymous allocation only) | Android/API band | Device/condition focus |
| --- | --- | --- |
| T01–T02 | 7–9 / API 24–28, including minimum where obtainable | Older low-memory ARM device, small screen, slow network |
| T03–T04 | 10–12L / API 29–32 | Samsung and another OEM; background/process restrictions |
| T05–T06 | 13 / API 33 | Notification allow/deny/re-enable; Pixel and OEM |
| T07–T08 | 14–15 / API 34–35 | Samsung/Xiaomi or similar battery restrictions; launcher widgets |
| T09–T10 | 16 / API 36, plus newer stable Android if available | Current Play target, OAuth/links/permissions and gesture navigation |
| T11 | Supported tablet/large-screen physical device | Widget sizing, rotation, font scale, multi-window |
| T12 | Supported spare/second-account device | Device-limit recovery, session revocation, upgrades and account isolation |

These are slots, not actual recruited people or claimed device results. Use
available supported devices and record gaps; emulators supplement rather than
replace the required physical/OEM/cloud checks. Every tester needs an actual
supported Android device and the correct Play-installed build. Include at least
one 2–3 GB RAM device, one compact display, large text/TalkBack, light/dark themes,
Wi-Fi/mobile transitions, battery saver and a non-UTC timezone (including a DST
zone). Multi-device tasks may use additional test installations under dedicated
accounts; never use someone's personal Hyped account for destructive tests.

## Fourteen consecutive days for twelve testers

For affected new personal Play developer accounts, Google's production-access
gate is at least 12 testers opted in continuously for the preceding 14 days.
Confirm the account's actual Play Console requirements before starting. This
plan uses 12 anonymous slots regardless; approval is not automatic at day 14.
Each slot represents a distinct eligible tester; devices or duplicate installs do
not replace people. The real roster/opt-in identities stay outside this repo.
Measure from the last required tester's opt-in timestamp: fourteen activity dates
are not necessarily fourteen fully elapsed days. Day 14 is the final planned
activity day; request eligibility review only after the full continuous period
has elapsed for at least 12 testers and Play Console confirms it (potentially on
the next calendar day). Do not start the clock until the entry gates below pass and a separately authorized release
owner has distributed the intended closed-track build. A person who opts out
cannot count their interrupted period; verify continuous eligibility in Console.

| Day | Planned focus for all 12 slots (short actual sessions) | Evidence/release-owner action |
| --- | --- | --- |
| 0 (preflight) | Not counted; physical smoke on candidate and cloud setup | Resolve blockers, select version, verify artifact/certificates; distribution requires separate authorization |
| 1 | Fresh Play install, sign-in/cancel, consent, create a disposable room | Record successful opt-in/install counts and anonymized build/device matrix |
| 2 | Create/edit, timezone/date display, text scale/themes | Triage UI/ETag failures; schedule a day-4 event whose 24-hour reminder falls on day 3 |
| 3 | Device limit recovery and other/current-device revocation | Confirm day-3 24h reminder, count arrivals and presentation separately |
| 4 | Public invite preview, signed-out join, consent return | Test invalid/expired/rotated links and duplicate link deliveries |
| 5 | Promote/demote/remove, role/foreign-room protection | Record safe authorization errors and removed-user sync behaviour |
| 6 | Small/medium widgets and different rooms per widget | Launcher resize/delete/re-add; compare last-sync and event equality |
| 7 | Offline cold launch, reconnect, account change | Verify stale cache/widget removal and no protected data from prior account |
| 8 | Leave, ownership transfer, archive and restore within recovery | Schedule a separate archived room for day-9 deletion eligibility |
| 9 | All three reminders; edits, archived/removed-member cancellation | Observe actual 24h/1h/event boundaries; record UTC/device times and permission state |
| 10 | Battery saver/background/restart, notification taps | Distinguish Android force-stop suppression from an app delivery defect |
| 11 | Upgrade candidate if a reviewed fix exists; regression | Higher versionCode only, preserve data/widgets/settings, track which build each result covers |
| 12 | Disposable-account deletion and delayed-message isolation | Verify recent auth, deletion readiness, backend/Firebase/local cleanup |
| 13 | Retest every issue/fix and cross-device regression | Any blocker reopens the affected gate; no unchanged-build retest assumption |
| 14 | Final regression on intended candidate and gap review | Verify 12 continuous opt-ins for the full period, summarize engagement/fixes/remaining risks |

Release owner reviews anonymized issues daily; urgent data exposure or account
misrouting stops testing immediately. Record tester-slot completion counts and
build changes privately without storing contact/opt-in identity data here. The
14-day calendar is a plan, not fabricated execution evidence. No outreach or
track creation is automated. Fixed reminders must be exercised at their real
boundaries; schedule rooms in advance rather than add arbitrary-offset product
APIs or change production clocks. A controlled isolated backend clock may assist
engineering tests but does not replace actual FCM/device timing observations.

## Safe bug report and daily record

Use the template in [device smoke tests](android-device-smoke-test.md). Required:
versionName+versionCode, candidate commit when known, device model (not serial),
Android version/API, steps, expected vs actual, frequency, and safe evidence.
Categorize blocker/high/normal, feature area, UTC/device timezone and connectivity
where relevant. Store real reports in the chosen private issue system after
manual review. Redact invitation URLs/tokens/codes, room/member content, emails,
Google identifiers, JWTs, refresh/FCM tokens and raw session IDs from attachments.
Do not request unfiltered logcat or screenshots of private rooms/sign-in data.

## Entry gates, release blockers and exit criteria

Before entering closed testing, require a clean verified candidate, a real signed
and validated AAB with unused versionCode, configured backend/Firebase/reminders,
verified owned App Links, approved legal publication, readable privacy-safe crash
reporting, representative engineering physical-device critical smoke results,
no open blocker/high defect, and a separately authorized handoff to an eligible
12-person roster. The full planned tester/device coverage is completed during
the test. Fourteen days of continuous opt-in are required later for applicable
production-access review, never as a circular prerequisite for starting.

Any uninvestigated reproducible app crash/ANR, inability to sign in/refresh/logout,
cross-account/unauthorized data exposure, broken valid invite/consent routing,
missed or duplicate enabled reminders in supported conditions, or unusable
widgets blocks the candidate. A single confirmed high/security defect is enough;
percentages and tester majority cannot waive it. Also block on failed/skipped
required automation, missing signed/validated AAB, absent verified domain, wrong
Play/Firebase certificate, unapproved legal publication, unreadable/unverified
crash reporting, untested account deletion, or missing representative engineering device coverage. Uncompleted planned
12-slot coverage blocks the final exit review rather than the first opt-in.

For a reminder complaint record event instant, the three expected schedule
instants, permission/channel state, worker acceptance vs OS presentation, network,
battery/force-stop state and build. Disabled permissions/offline/OS force-stop
must be clearly distinguished, but unexplained loss is a blocker until diagnosed.
Widget countdown text updates when widget updates run; it is not a continuously
network-synchronized clock. Frozen/misleading last-sync, inaccessible room leaks,
or failure to open the mapped room remain blockers.

Exit closed testing / request production-access review only when all required smoke rows pass on recorded physical coverage,
12 continuous opt-ins and the 14-day period are evidenced, no open blocker/high
issue remains, the exact final build and readable symbols are archived, and a
human release owner reviews privacy, Play policy and findings. Applying for
production access, publishing, messaging and symbol uploads require separate
authorization and are not performed by this preparation task.

References checked 2026-10-24: [Google's testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465),
[closed-test setup](https://support.google.com/googleplay/android-developer/answer/9845334),
[Android versioning](https://developer.android.com/studio/publish/versioning),
[bundletool](https://developer.android.com/tools/bundletool).
