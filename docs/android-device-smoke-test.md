# Physical Android smoke-test record (blank template)

Status: **PENDING — not executed by creating this checklist.** Use a dedicated
non-production test account and disposable rooms. Do not put completed personal
records or secrets into Git. No physical device was connected during initial
closed-test preparation. Automated unit/controller coverage does not check these
real Google/FCM/launcher/browser interactions.

## Record header

- Anonymous slot: T__ (allocation label only; no name/email)
- Candidate versionName + versionCode: pending
- Source commit / artifact SHA-256 / backend revision: pending
- Installation source: closed Play track / engineering debug (debug is not release proof)
- Device model / Android version / API level: pending (no serial, IMEI or advertising ID)
- OEM launcher version / RAM class / screen size: pending
- Date/time in UTC and device timezone: pending
- Connectivity / battery saver / notification permission and channel state: pending
- Fresh install or upgrade from versionName+versionCode: pending
- Overall: PENDING / PASS / FAIL / BLOCKED

Mark each row PASS/FAIL/BLOCKED with UTC time and a sanitized issue reference.
Blank and BLOCKED never count as PASS. Repeat critical rows on the final fixed
candidate and on both required minimum/target/OEM coverage.

| ID | Action | Expected outcome / evidence | Result/time/issue |
| --- | --- | --- | --- |
| S01 | Fresh Play install; cold launch; sign-in via Google; cancel once; retry | No crash or loop; cancellation is recoverable; authenticated safe profile; required consent has unchecked explicit boxes | PENDING |
| S02 | Existing accepted account reopens; consent-required account follows an invite | No unnecessary repeated consent; new required version prompts; same pending public invite survives onboarding/sign-in/consent | PENDING |
| S03 | Let access token expire naturally (or controlled test-environment expiry); load and edit | One refresh succeeds; intended operation completes once with same business idempotency; expired/revoked refresh returns sign-in and clears protected content | PENDING |
| S04 | Reach documented device limit using dedicated test installations; revoke one in recovery | Safe device names only; recovery permits a new session; correct current-device marker; another account's device cannot be revoked | PENDING |
| S05 | Revoke current and another device; retry the revoked device's protected load | Correct confirmation; revoked session cannot access rooms; local cache/widgets/pending intents clear after expiry/sync | PENDING |
| S06 | Logout online; repeat offline/failed backend logout; reopen and switch accounts | Guaranteed local cleanup; no old room/cache/widget/notification/invite content; delayed writes cannot retry as the new account | PENDING |
| S07 | Create/edit event room and theme, then concurrent edits from two devices | Correct local date/time/instant; safe validation; clean stale-revision conflict; no duplicate create from repeated tap | PENDING |
| S08 | Open owned verified HTTPS invite signed out/in, app cold/warm; try room code | Correct public preview; explicit join; same pending invite after gates; correct room; repeated delivery does not repeat membership | PENDING |
| S09 | Invalid, expired, rotated invite; full room; joined-room limit; already-member; offline/timeout | Safe messages with no credential echo; no automatic joins; existing member can open room; room-code entry remains usable | PENDING |
| S10 | Browser/OS verifies owned host and Play installed certificate; try foreign/lookalike/malformed links | Actual host reports verified; valid external click opens preview without package-forced adb bypass; invalid links do not enter privileged routes | PENDING |
| S11 | OWNER promotes/demotes/removes and transfers; MEMBER/CO_HOST try unavailable actions | Correct role-specific UI and confirmations; backend denies unauthorized operations; exactly one owner; repeated removal safe | PENDING |
| S12 | Removed member refreshes detail/list, cache and widgets | Access denied safely; reminders no longer eligible; protected cached/widget room data invalidated on sync | PENDING |
| S13 | MEMBER/CO_HOST leave; OWNER attempts leave, then transfers ownership | Non-owner can leave; owner must transfer first; no access after leave/sync; repeat leave safe | PENDING |
| S14 | OWNER archives; try joining/editing; restore before 24-hour recovery ends | Archived state accurate; joins and active reminders stop; restore available only within server-documented period; revision errors clean | PENDING |
| S15 | Keep separate room archived past documented deletion eligibility; permanently delete | Restore/deletion action visibility follows server time; confirmation; repeated safe result; invites/membership/reminders/cache/widgets removed/revoked | PENDING |
| S16 | Deny notifications then enable via settings; toggle per-room fixed settings | Denial is recoverable, no misleading enabled indication; only 24h/1h/event settings; permission and channel state respected | PENDING |
| S17 | Event scheduled early enough for 24h, 1h and event-time reminders; observe all three | One visible reminder per enabled occurrence under supported conditions; record event/scheduled/received instants, UTC and device local time | PENDING |
| S18 | Edit event time; archive/delete; remove member; disable room reminder before boundary | Old occurrence canceled; no reminders to removed membership; rescheduled enabled occurrences follow new instant; no duplicate retry presentation | PENDING |
| S19 | Background app, restart device, battery saver; tap cold/warm notification; logout/change account with delayed message | Correct authenticated room; generic tray text reveals no room data; old-session message ignored; distinguish OS force-stop suppression | PENDING |
| S20 | Add small and medium widgets; select different rooms; resize/delete/re-add | Independent appWidgetId selections; safe stale mapping handling; local date/time, countdown/event equality, started state, last-sync and tap mapping correct | PENDING |
| S21 | Edit mapped room; lose network; archive/remove/delete room then sync; logout | Latest successful response refreshes widget; offline retains last successful safe data; clear unavailable/archived/removed state; logout removes protected text | PENDING |
| S22 | Load rooms online; force-close; airplane mode cold launch; read list/detail | Cached read-only content appears with accurate last successful sync; no create/edit/join/member writes; reconnect uses server authority and removes inaccessible data | PENDING |
| S23 | Change device timezone/DST boundary, 12/24-hour format, font scale/TalkBack and themes | Same event instant; correct local date/time and accessible controls; widgets update on system/app update events, not continuous network ticks | PENDING |
| S24 | Upgrade previous candidate without clearing data; reopen widgets/settings/offline cache | No startup crash, schema/data corruption or account leakage; widget mappings and safe preferences remain valid or clearly invalidated | PENDING |
| S25 | Dedicated disposable account: deletion readiness, deliberate confirmation, recent auth cancellation/failure, then successful deletion | Failure retains correct recoverable state; ownership blockers explained; successful backend deletion, Firebase account cleanup and local credentials/cache/widgets/pending intents cleared under documented retention rules | PENDING |
| S26 | Reopen deleted account's stale session and delayed notifications/invites | No restored private content or unauthorized room access; no room text in old notifications; correct sign-in/account state | PENDING |
| S27 | Controlled diagnostic build crash after reviewed Crashlytics setup; relaunch and inspect private dashboard | Readable Dart and Kotlin/Java frames for exact build, correct symbols/mapping and no private data/URLs/tokens/custom identifiers | PENDING |

For S14–S15, use separate rooms and real elapsed time across the plan, or a
controlled isolated test-clock fixture for engineering verification. Do not alter
production clocks or add product APIs to bypass the recovery window. Account
removal is irreversible: do not use a tester's personal account or delete shared
rooms holding someone else's needed data.

## Bug report template

```text
Issue reference: assigned privately
Anonymous slot: T__
Feature / severity (blocker, high, normal):
Build versionName + versionCode:
Candidate commit / artifact SHA-256 (if supplied):
Install source / previous version if upgrade:
Device model (no serial):
Android version and API:
Launcher / relevant settings (permission, battery saver, timezone):
UTC occurrence time / device timezone:
Network: Wi-Fi / mobile / offline / transition
Preconditions (generic, no account or room identifiers):
Steps:
1.
2.
3.
Expected result:
Actual result:
Frequency / reproduction count:
Safe error code or random request ID (if shown):
Redacted evidence reference (optional):
Related issue / retest build / outcome:
```

Never attach raw invitation URLs/tokens, room codes, room/member details, emails,
auth/refresh/FCM tokens, Firebase identities or raw session IDs. Use synthetic
labels in reproduction steps. Testers can find the installed version in Android
Settings > Apps > Hyped > app details (OEM wording varies), or use the version
shown in the supplied candidate instructions. Engineering can read only version
fields using `adb shell dumpsys package com.hyped.hyped` locally; do not attach
the full dump or unfiltered logcat. No report submission or tester contact is
automated by these instructions.
