# Data Safety review worksheet — Android MVP

This is a source-based draft, not a submitted form or certification. The release
owner must verify the deployed backend, providers, retention, all active Play
versions and current Console definitions. “Collected” includes transmission off
device by an SDK, even before app authentication. Local-only cache is distinct
from collection of the same room data by the backend. No advertising/marketing
consent is bundled with legal consent.

## Observed data and processing

| Flow / likely Console category | Observed fields and destination | Purpose / user choice / deletion |
|---|---|---|
| Google/Firebase authentication: personal info, user IDs | Google sign-in/Firebase receives authentication data; backend maps verified provider subject/Firebase UID, name, email and optional provider photo URL. Firebase Auth also handles IP, user agent and app identifiers for service security. Passwords are not collected by Hyped's Google-only UI. | Account access and security. Required to create/join rooms, optional for public invite preview. Profile/provider identifiers encrypted in backend design; do not expose raw provider IDs in UI. Firebase cleanup is part of account deletion. |
| Account/device identifiers | Backend session/device identifiers, installation UUID, bounded device manufacturer/model label and app version/build; Google/Firebase app/project identifiers. No IMEI, contacts, advertising ID or GPS collection found. Credential libraries declare biometric permissions; no Hyped custom biometric prompt or raw biometric access was found. | Session management, device limit/revocation and fraud prevention; session/device ownership derives from verified JWT. Installation/session values are not displayable user profile fields. Remove/revoke on logout, revocation and deletion as implemented. |
| User-generated content and personal info | Room title, description, event instant, safe theme, memberships/roles and legal version/consent acceptance. Users may type personal information into room text; no GPS permission or dedicated location tracking. Room participants receive authorized safe member profile fields. | Shared countdown functionality and compliance. Creating/joining is optional; authenticated functions require consent. Sharing with invited participants is user-directed but still assess Console's sharing rules. Never promise private room text contains no personal data. |
| Photos / user-generated images | Optional selected room-cover photos are prepared on device, uploaded to private Cloudflare R2 and processed by Google Vision SafeSearch before publication when media is enabled. No broad gallery/GPS permission, camera collection or persisted source URI is added. | Optional room customization and safety. Disclose image contents/provider processing before enabling the feature; new legal drafts/required versions are supplied. Removed members cannot request new delivery URLs; old signed URLs expire within 15 minutes or object deletion. Object cleanup participates in room/account deletion with durable retries. |
| Cloud messaging / device or other IDs | Per-session FCM registration token; Firebase Installations IDs and SDK/app metadata used by messaging. Authenticated backend sends generic notification content with routing room ID, session ID, notification ID and reminder occurrence/kind. No room title/description in visible push. No BigQuery delivery export configured in source. | Optional reminders after explicit notification enablement, with per-room controls. Disable/revoke on logout, session/device revoke, replacement and deletion. Expired tokens invalidated. Pending notifications are cleared/identity-gated on account change; generic text alone does not mean no metadata is transmitted. |
| Play Integrity / app info and performance, device IDs or security signals | Opaque Google Integrity evidence and request binding are obtained through Google Play; authenticated backend verifies application/device verdicts and binds session/request identity. Exact Google device/security processing must be reviewed against its terms and configured Cloud project. | Fraud prevention/security, configurable disabled/observe/enforce; observe is gradual rollout, not a guarantee that no evidence is processed. Never log raw evidence or credentials. |
| Network metadata / fonts | Manrope and Outfit are bundled with their SIL OFL 1.1 notices. UI rendering makes no font-service network requests. Backend/Google infrastructure still processes transport/security metadata. | Service operation. Evaluate provider collection, retention and applicable sharing exceptions. Bundled-font notices are available through Flutter’s license registry. |
| Diagnostics / security records | Backend safe error classes/request IDs, bounded operational metrics and redacted security events; infrastructure access logs may include IP/metadata depending on deployment. No raw JWT, refresh/FCM/invite token, room code or request body logging is permitted. | Operations and abuse prevention. Verify actual log sinks, sampling/redaction and retention before declaring collected diagnostic categories. Test logs are not production telemetry. |
| Crash reports / analytics | No Crashlytics, Firebase Analytics or ads integration in this source. A transitive Firebase measurement-connector adapter is present; no firebase-analytics or Google measurement implementation is resolved. Analytics collection is explicitly deactivated and ad-ID permissions removed. Play Android vitals/Console diagnostics and voluntarily provided support reports are separate from an installed crash-report SDK. | Do not declare Crashlytics stack traces/device UUIDs as active Hyped SDK collection. If Crashlytics is later enabled, review automatic device/app/stack data, installation/session IDs, consent and retention before shipping; follow `android-crash-symbols.md`. No symbols uploaded here. |
| Offline SQLite / widgets / secure storage | Account-partitioned safe room UI cache and safe per-widget room mapping/time/theme/last-sync metadata; credentials remain in secure storage, not ordinary widget preferences or Drift. | Local read-only display. Clear protected content on logout/account deletion/auth expiry/account change; inaccessible rooms invalidated at next successful sync. A remote removal cannot erase an offline phone instantly. Local widget copies are not separate backend private-response stores. |

## Form decisions requiring approval

- Identify all transmitted personal-info/user-ID, user-content, device-ID,
  diagnostics and app/security categories from the final SDK graph and deployed
  logs. Distinguish required account processing from optional reminders/profile
  details and public preview. Do not mark all processing optional because users
  can uninstall the app.
- Cloud/Firebase/Google processing may qualify as service-provider processing or
  another defined exception to “sharing”; do not automatically mark every SDK
  transfer either “shared” or “not shared.” Review contracts, instructions,
  provider independent purposes and user-directed room sharing using current form
  definitions. Confirm no sale, ads targeting or tracking in actual deployment.
- Proposed purposes: app functionality, account management, security/fraud
  prevention and appropriate diagnostics. No analytics/marketing claim without
  an implemented, reviewed collection flow.
- Verify all off-device transports are HTTPS, database/key-registry isolation and
  provider safeguards. Encryption-at-rest design does not justify an unverified
  end-to-end-encryption claim; Hyped's server processes room data.
- Confirm working in-app and public-web deletion requests. Form answers apply to
  every active version of this package, not just the current candidate.

## Account deletion and retention — precise scope

In-app deletion requires deliberate confirmation and recent authentication where
required. Backend refuses deletion while the user owns rooms or remains a member:
transfer ownership/permanently delete eligible owned rooms, then leave other
rooms first. Do not advertise automatic deletion of other participants' rooms.

Once accepted, the backend moves the account to deletion-pending and immediately
revokes sessions/devices. A durable job deletes the Firebase account, destroys
the external per-user key capability, removes profile/identity/consent/device/
session/reminder-preference data and leaves a minimal pseudonymous deleted-user
tombstone. Provider failures are retried; acceptance is not proof that external
cleanup has already finished. Flutter clears credentials, caches, widgets and
pending invite/notification intents on the controlled cleanup path.

Repository security policy targets completion within 24 hours after preconditions
pass, seven-day encrypted backup expiry, 30-day anonymized security/audit records
and at most 90 days for pseudonymized abuse records. Product analytics retention
policy does not imply an analytics SDK exists. External key deletion, backup/log
lifetimes, minimal deletion-journal/tombstone handling and provider retention must
be verified in deployment and reflected in approved legal text before promising
those timings. Google services have their own documented retention. Do not claim
instant deletion of every backup, security record or Google's independent data.

The public deletion resource must identify the app/developer, explain these
preconditions and retention, offer an actual request route, and verify identity
without requesting secrets. No production domain/support contact or web deletion
service has been supplied; this remains a submission blocker.

## Evidence and references

Review `frontend/pubspec.lock`, Android `releaseRuntimeClasspath`, backend runtime
dependency tree, `AccountService`/`JdbcAccountStore`, native `AndroidPushBridge`/
`HypedMessagingService`, and the cache/widget cleanup coordinators. Generate the
local license/SDK inventory with `scripts/review-release-dependencies.py`.

Use the [Play Data Safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469),
[Firebase disclosure guide](https://firebase.google.com/docs/android/play-data-disclosure)
and [Firebase privacy information](https://firebase.google.com/support/privacy)
for the final provider review. This worksheet is based on observed code; it does
not replace the developer's form attestation or legal approval.

## V16 safety controls

Optional report reasons/details and room/account/photo context are transmitted to the backend for abuse handling. Free text uses reporter-bound encryption; authorized dedicated reviewers may inspect it and current reported content. Directional block records enforce access and profile masking; other users do not receive blocker identity or a notification. Reports/audit expire after at most 90 days, and account deletion strips reporter/subject IDs and details and purges blocks/replays/access work. Disclose these optional user-content/security categories and reviewer access in the final approved privacy text and form. Review/activate a new document version before production; historical accepted legal resources are not edited by this change. See [moderation setup](reporting-blocking-and-moderation.md).
