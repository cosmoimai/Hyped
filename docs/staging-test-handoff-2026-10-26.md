# Staging testing handoff — 2026-10-26

Manual testing is deferred at the owner's request. Resume from the unchecked items
below. This records engineering staging checks, not Play release approval or a
completed closed-testing period. Dates use Asia/Kolkata.

## Candidate and evidence

- Android: engineering debug APK installed on Samsung SM-M336BU, Android 15 / API 35.
- Source: `a676bfe` (room edit media type and time-zone fix).
- Backend: Cloud Run `hyped-staging-api-00005-c76`, project `hyped-41a30`.
- Latest automated verification: 646 backend tests and 421 Flutter tests passed;
  Flutter formatting/analyze, debug APK build, staged secret scan, image scan under
  existing reviewed exceptions, and Git whitespace checks passed.
- Manual passes below were reported by the owner during this conversation. They
  establish the exercised paths only; they do not imply every row in the full
  smoke-test template was executed. Earlier passes span earlier staging revisions.
- The final room-edit fix was retested on the phone; the server returned HTTP 200.
  Repeat critical flows on the final signed release candidate.

Do not put room codes, invitation links/tokens, FCM or authentication tokens,
account emails, raw session IDs, private room content, credentials, or unsanitized
phone/server logs in this record. Keep artifact hashes and non-personal evidence
in excluded verification output or the approved issue system.

## Owner-reported manual passes

| Area | Exercised result |
|---|---|
| Authentication and consent | Google sign-in, required staging consent and room creation succeeded |
| Session continuity | Close/reopen, logout and sign-in succeeded; this does not prove timed token expiry or device-limit recovery |
| Invite preview/join | Staging room-code preview and join succeeded after the preview transaction fix; verified App Links remain blocked |
| Member management | Promote/demote, action visibility, removal and loss of access passed the supplied two-account checklist |
| Room lifecycle | Leave/rejoin, ownership transfer, archive, join rejection and restoration passed the supplied checklist |
| Offline cache | Cached reading, offline edit rejection, reconnect refresh and account isolation passed |
| Android widgets | Independent selections, taps, edit synchronization, offline display and logout content cleanup passed |
| Reporting/blocking | Room/account reports, block persistence and unblock passed; photo reports and operator moderation review are not verified |
| Event-time notification | Real phone received the reminder and its tap opened the correct room |
| Room time editing | Updated time displayed after the request content-type and room-time-zone fixes |
| Edited reminder | No reminder at the old time; reminder at the updated time and correct routing passed |
| Archived reminder | No reminder after archiving before the event; restoration passed |

## Remaining focused phone checks

Use disposable rooms and dedicated testing accounts. Register the device and enable
the relevant room preference before the boundary occurs. Past occurrences are not
sent retroactively. Treat FCM acceptance separately from actual phone receipt.

### R01 — Logout/account switch and delayed reminders

- [ ] Create an event about 10 minutes ahead; enable its event-time reminder.
- [ ] Sign out before event time and leave the app in the background.
- [ ] Confirm no reminder appears for the signed-out session.
- [ ] Sign in with the other account; confirm no previous-account rooms, widgets,
      pending navigation, or protected data appear.
- [ ] Check a previously displayed notification cannot reopen protected content
      after logout/account change.

This is still pending: normal logout and widget cleanup passed earlier, but the
specific delayed-push exercise was not completed before testing was deferred.

### R02 — Notification permission denial and later enablement

- [ ] Deny/disable notifications using Android settings as needed.
- [ ] Confirm room reading and authorized room operations remain usable.
- [ ] Re-enable permission, return to Hyped and retry registration if necessary.
- [ ] Confirm a fresh future event reminder arrives and routes correctly.
- [ ] Check app and reminder-channel settings separately; do not reset app data
      merely to repeat a permission dialog.

### R03 — Removed-member cancellation

- [ ] Join a disposable room with a second account and enable its reminders.
- [ ] Remove that membership before a future event-time boundary.
- [ ] Confirm the removed account receives no reminder and loses room/widget
      access after synchronization. Confirm eligible remaining members still work.

### R04 — Exactly one hour before

- [ ] Create an event at least 70 minutes ahead and enable the one-hour preference
      before that boundary. Record event time and expected reminder time locally.
- [ ] Confirm receipt around event instant minus 60 minutes, exactly one visible
      notification, and the correct room on tap.
- [ ] Record Android battery saver/network/channel conditions if delayed.

### R05 — Exactly 24 hours before

- [ ] Create an event at least 24 hours 15 minutes ahead and enable the 24-hour
      preference before that boundary. This requires a later-day/overnight check.
- [ ] Confirm receipt around event instant minus 24 elapsed hours, one visible
      notification, and correct routing.
- [ ] Verify displayed device-local time agrees with the event instant. Automated
      DST/UTC-offset tests do not replace this real-device delivery observation.

R04 and R05 may run in parallel using separate disposable rooms. Schedule them
before the expected boundaries; enabling a preference afterward is not a valid test.

### R06 — Device/session revocation

- [ ] Use two authenticated devices if available and identify the current marker.
- [ ] Revoke the other device with confirmation; confirm it loses protected API
      access and future reminder eligibility after reconnecting.
- [ ] Verify the revoked device clears local protected content and returns to
      sign-in when it learns of revocation.
- [ ] Exercise device-limit recovery and timed authentication/refresh expiry using
      the full smoke checklist; these have not been established by normal sign-in.

### R07 — Cold-start notification routing

- [ ] Enable a fresh future reminder, dismiss Hyped from Recents and wait.
- [ ] Tap the reminder; confirm launch, authentication checks and correct room.
- [ ] Ensure repeated taps/delivery do not create duplicate navigation or joins.

Dismissing from Recents differs from Android Settings → Force stop. Do not use a
force-stopped application as proof that ordinary background delivery is broken.

## Setup blockers and deferred feature tests

| Area | Work before testing | Required manual evidence |
|---|---|---|
| Shared room photos | Staging media configuration is enabled; live upload/moderation/storage authorization checks remain deferred (see checkpoint below) | Upload, SafeSearch outcomes, authorized viewing, removed-member denial, offline fallback and cleanup; see [photo setup](android-room-photos-setup.md) |
| Account deletion | Firebase deletion permission assigned; cleanup monitoring being configured; verify live heartbeat and feature flag before tests | Disposable-account success/failure/retry, Firebase cleanup and local cache/widget/invite/token cleanup; see [profile/settings setup](../frontend/docs/profile-settings.md) |
| Android App Links | Owned HTTPS invite domain, assetlinks, signing fingerprints and configured host | Cold/warm link delivery, sign-in/consent continuation and browser fallback; room-code success does not cover App Links |
| Operator moderation | Configure restricted reviewer identity/access and enable the review module | Authorized review/resolve, unauthorized rejection, holds/appeals and audit/retention behavior; report submission alone does not prove review handling |
| Closed testing / release | Final signed candidate, broader device/Android coverage and required Play Console setup | Execute the [closed-testing plan](android-closed-testing.md), full physical smoke matrix and submission gates; do not publish from this handoff |

Push delivery and its authenticated minute scheduler have been enabled and real
event-time delivery has passed. Do not treat the old initial-staging push-disabled
checkpoint as current status. At the original testing handoff, media, account deletion and operator review were
not enabled. Media was subsequently enabled as recorded below; account deletion
and operator review remain disabled at that checkpoint. Verify live configuration when resuming;
this document is a dated snapshot, not an infrastructure inventory API.

## Media configuration checkpoint — 2026-10-26

- Owner reported creating private R2 bucket `hyped-staging-room-photos`, saving a
  `quarantine/` one-day expiration rule, creating bucket-scoped Object Read & Write
  credentials, and enabling Vision with API service-identity access.
- Owner stored both R2 credentials in Secret Manager and granted the API identity
  access. Credential values were not read or copied into this record.
- Cloud Run revision `hyped-staging-api-00006-nxz` is ready. Read-only configuration
  inspection confirmed `MEDIA_ENABLED=true`, the expected bucket, a valid S3
  endpoint format and two secret references pinned to numeric versions.
- Readiness returned HTTP 200. Push remained enabled and Terms/Privacy remained
  `2026-10-26`. The scheduler was enabled with no reported status error at inspection.
- This verifies configuration/startup only. It does not prove R2 upload/read/delete,
  Vision authorization/decisions, lifecycle execution, or end-to-end room photos.
  The manual photo checklist remains deferred. No photo flow is marked PASS.
- Account deletion remains disabled; Firebase permission and cleanup monitoring
  are the next setup work. No account was deleted during this configuration step.

## Account-deletion monitoring checkpoint

- Firebase deletion permission was confirmed on the API identity; no account was deleted.
- Read-only inspection found zero pending/overdue deletion jobs before setup.
- An owner-configured operations email channel is referenced by three staging
  policies; its recipient is not recorded in Git. Repeated configuration/read-back
  confirmed three enabled policies, without duplicates.
- Runtime instrumentation reports aggregate backlog age/count, warns at 12 hours,
  and marks 24 hours overdue. Tests cover threshold equality, retry age retention,
  safe errors, empty queues, and monitoring during cleanup failure.
- Backend source `c005805` is deployed as `hyped-staging-api-00007-5pz`.
  Readiness returned HTTP 200; push and media remained enabled.
- Scheduled worker logs reported `OK`, pending count zero and oldest age zero.
  Cloud Monitoring returned three real heartbeat data points at inspection, so
  the absence policy has an observed time series rather than only an empty metric.
- All 657 backend tests and 18 setup-script tests passed. Policy configuration was
  reapplied and read back without duplicates. Email delivery and recovery exercises
  remain deferred; no test email or account deletion was triggered.
- Account deletion stays disabled during this setup. Check the current flag and
  [monitoring runbook](12-observability-runbook.md#staging-account-deletion-monitoring)
  before enabling or testing it. Use only a disposable Firebase test account.

## How to resume and record results

1. Record the installed version/build, source commit, backend revision, device model,
   Android version and device time zone. Keep device serials and personal data out.
2. Resume R01–R07. Schedule R04/R05 early and run other checks while waiting.
3. Configure each blocked service before its feature-specific tests. Keep unresolved
   items BLOCKED rather than marking them passed.
4. For each result record: ID, PASS/FAIL/BLOCKED, local timestamp/time zone, expected
   versus actual result, sanitized reproduction steps and issue reference.
5. After any related fix, repeat the failed case and the affected critical flows.
6. Before release, use the full [physical-device template](android-device-smoke-test.md),
   [closed-testing plan](android-closed-testing.md) and
   [submission checklist](play-store-submission.md). This partial staging record
   does not replace them.

Template:

```text
Case: R__
Result: PENDING / PASS / FAIL / BLOCKED
App version/build and source commit:
Backend revision:
Device model / Android version:
Timestamp / time zone:
Permission / channel / network / battery saver state:
Reproduction steps:
Expected result:
Actual result:
Sanitized issue/evidence reference:
```
