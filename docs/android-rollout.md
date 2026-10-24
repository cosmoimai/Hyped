# Android phased rollout, rollback and monitoring

Preparation only: no track, release, tester list or production traffic was changed.
A release owner and an on-call backend owner must explicitly approve each future
step and have access to monitoring, Console and incident communication privately.

## Gates before any rollout

- All automated candidate checks pass from the clean committed revision; signed
  AAB signature/metadata and SHA-256 recorded. No unresolved security, data-loss,
  authentication, invite, reminder-duplication or widget usability defect.
- Manual Google sign-in, refresh/logout/revocation, account switching, device-limit
  recovery, create/edit/member/lifecycle operations, invites/consent, reminders,
  widgets, offline recovery and account deletion pass on the physical matrix.
- Closed-testing eligibility/evidence approved separately; listing, legal/support/
  deletion URLs, Data Safety, rating, audience and permissions complete. Live
  Firebase/FCM/Integrity, HTTPS and assetlinks verified using Play signing identity.
- Baselines and alert access exist for authentication/refresh errors, backend
  latency/5xx, reminder queue age/delivery outcomes/duplicates, deletion-job age,
  and Android crash/ANR reports. Do not claim Crashlytics dashboards exist here;
  see `android-crash-symbols.md`. Establish an approved diagnostics mechanism and
  a privacy-safe manual reporting path before release; provider reports can lag.
- Backend/database compatibility with current and previous Android versions tested;
  backups and isolated restore/deletion-journal process proven. Record last good
  backend revision and candidate, retain deployment and signing access privately.

## Proposed production phases (manual approval at every step)

For an eligible update, begin at 1%, then 5%, 10%, 25%, 50% and 100%. Hold at least
24 hours at each early phase (longer if sample too small); do not advance because
no reports arrived. Complete the first two-hour checks below, inspect at least
one real reminder boundary and scheduled-job cycle, and compare baseline metrics
before each expansion. Never force exact alarm timing to pass a marketing claim.

Staged rollouts apply to updates. If Console requires a first production release
to be full availability, do not describe it as a 1% staged rollout: complete
internal/closed testing first, confirm country/device availability and require a
separate owner decision. Do not change audience or track here.

Stop expansion for any confirmed data exposure, authorization bypass, account
cross-contamination, auth/refresh failure, unusable invite, duplicate/missed
scheduled reminder caused by the service, unusable widget or blocking lifecycle/
deletion error. Any reproducible new crash/ANR on a core flow blocks advancement.
For aggregate metrics, compare with the approved test baseline and current Play
quality thresholds; lack of a baseline is a gate failure, not a zero-error result.
Separate denied notifications/offline delivery delay from scheduler defects.

## Rollback procedure

1. Halt an ongoing staged rollout in Console (future manual operation only).
   Halting prevents further distribution to new recipients; it does not uninstall
   the update or downgrade users who already received it.
2. Assess scope and preserve sanitized timestamps/build/backend revision/error
   classes. Never capture JWTs, raw invites, room content, FCM tokens or tester
   personal details. For exposure/security incidents follow the security runbook.
3. Disable the affected feature only using existing safe server configuration
   where appropriate. Integrity may move from enforce to observe by approved
   configuration to recover false positives; never weaken authentication/consent.
   Do not disable reminders as a silent permanent workaround for duplicates.
4. Roll backend traffic to the last compatible deployed revision if schema and
   client compatibility permit. Do not reverse migrations/destructively restore
   production data to undo a client bug. Preserve deletion/key-destruction effects.
5. Ship known-good compatible client code with a **new higher versionCode**,
   perform full verification/signature checks and the focused physical regression,
   then seek owner approval for distribution. Play cannot roll users back to a
   lower code. Retain old and replacement artifact hashes and release receipts.
6. Reconcile durable reminder records and deletion jobs idempotently using
   existing operational procedures. Never bulk replay all reminders or recreate
   revoked identities/keys. Verify recovery before resuming expansion.

## First two hours after a future release

| Time | Checks and evidence |
|---|---|
| Before start | Record approved version/code/AAB SHA-256/backend revision/rollout scope and baseline dashboard values; assign two-hour on-call owner and escalation route |
| T+0–15 min | Confirm Play-installed identity/version and HTTPS production target; sign in, consent return, refresh, logout and second-account isolation; inspect crash/ANR reports and auth/5xx spikes |
| T+15–30 min | Cold/warm App Links and room-code preview/join; expired/repeated invite handling; create/edit, member removal/access denial, ownership and archive/restore visibility on controlled non-personal data |
| T+30–60 min | Verify FCM registration/rotation and generic taps to correct room; scheduler due-record processing, idempotency and failure/retry/expired-token metrics; verify near event-time boundary, not just FCM acceptance. Use closed-test evidence for 1h/24h boundaries |
| T+60–90 min | Multiple small/medium widgets, correct room selection, offline cached view/last-sync, online refresh and cleanup after removal/logout/account switch; verify no stale protected content |
| T+90–120 min | Deliberate account-deletion test with prerequisites satisfied, revoked-session denial and durable provider/key cleanup; inspect deletion/reminder queue age, error rates, crash/ANR signals and support reports; record go/hold/halt decision |
| Throughout | Any blocking signal stops expansion immediately; monitor infrastructure without recording room names, invitation secrets or personal screenshots. Late Google delivery/crash reports remain subject to later review |

A two-hour pass is an initial observation window, not permission to bypass
24-hour phase holds or claim long-period reminders proven. Continue daily checks
through expansion and retention/deletion deadlines. Use the physical smoke-test
report template for version, device model, Android version, reproduction steps,
expected/actual behavior and sanitized evidence.

References: [staged rollouts and halting](https://support.google.com/googleplay/android-developer/answer/6346149),
[Android versioning](https://developer.android.com/studio/publish/versioning).
