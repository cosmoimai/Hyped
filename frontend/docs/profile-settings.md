# Android Profile and Settings

Open the person icon on Home. `/settings` is authenticated; `/settings/rooms` lists active rooms and links to their existing three fixed reminder settings. The profile shows the authenticated display name, initials and active-device/room counts from `GET /api/v1/me`. No provider identifiers, raw session IDs, refresh tokens, email, internal keys or private provider-photo URLs are rendered or copied into preferences. The current profile photo projection is null; initials are shown.

App theme supports System, Light and Dark. The safe enum string is stored under `app.theme_preference.v1` in the existing platform secure storage, independently of auth tokens. It survives restarts and sign-out as a device preference. Invalid/missing values use System; failed writes retain the previous choice. No new Android dependency or iOS-specific configuration is introduced.

Notifications opens the existing Android permission/onboarding page. Per-room reminders opens active rooms and their existing 24-hour, 1-hour and event-time toggles. Archived rooms are omitted. These settings do not add new reminder offsets or local/background scheduling.

Active devices use `GET /auth/sessions`, ordered by last-used time descending. The current marker is the server's `isCurrent` calculated against the verified JWT session claim; Flutter does not decode an unverified JWT or compare an installation ID to a session ID. Opaque IDs stay private to the API model/path. Revocation requires confirmation. Revoking the current session, a missing current marker, or expiry ends the local session and redirects to sign-in. Returning to Settings from Android background/settings reloads the list.

## Logout and local cleanup

Logout uses the existing backend revocation endpoint and always attempts local cleanup in `finally`, including when the backend is offline or native cleanup fails. Native notification session state is cleared before revocation; the token-store cleanup also deletes the FCM SDK token and protected widget content. Google/Firebase SDK sign-out, secure token deletion, pending-invite removal and Flutter image-cache cleanup are independent cleanup steps. Private room/account controllers reset on authentication changes; theme choice is retained. An old-session cleanup cannot clear a replacement account.

A direct account switch clears old credentials and pending state before new sign-in. A signed-out pending invite still survives normal sign-in, preserving the existing invite flow. Remote `ACCOUNT_DELETION_PENDING`/`ACCOUNT_DELETED` responses additionally clear pending invites and cached images before authentication invalidation.

## Permanent account deletion

1. Readiness checks require no owned rooms and no joined memberships, including archived rooms. Transfer ownership, leave, or explicitly complete eligible room deletion first. The app does not silently delete other participants' rooms.
2. The warning states irreversibility, no recovery period, immediate loss of access, the 24-hour personal-data purge deadline and seven-day encrypted-backup expiry. The user must type `DELETE` and explicitly confirm with Google.
3. Firebase reauthenticates the current user with fresh Google credentials. Choosing another account cannot replace the Firebase principal. The backend independently verifies the token, requires `auth_time` within five minutes, and matches its exact provider identity to the authenticated Hyped user. Client-supplied ownership fields are rejected.
4. Backend acceptance commits `deletion_pending`, session/refresh revocation, push-token invalidation and a durable cleanup job atomically. This is irreversible. Flutter clears credentials, widgets, notifications, private caches and pending invites and redirects to sign-in. Failure before acceptance preserves the signed-in account and allows retry. A recent-authentication error does not rotate or invalidate the Hyped session.
5. The backend resolves the Firebase UID from the verified Firebase token, not from a client UID or Google provider subject. It deletes that Firebase user, durably checkpoints success, destroys the external per-user key, and purges personal rows. The Google account itself is not deleted. Database failure after key destruction can safely resume without decrypting the old UID again.

External failures retain a disabled `deletion_pending` account and durable retry job; they do not restore access or report cleanup complete. The protected scheduler must run and overdue jobs must be alerted/remediated to meet the documented 24-hour purge deadline. A later sign-in can create a new empty account after deletion; it does not recover the deleted account.

For staging cleanup-age signals and alert setup, see the
[account-deletion monitoring runbook](../../docs/12-observability-runbook.md#staging-account-deletion-monitoring).

## Backend/Firebase setup and operations

- Reuse the existing Firebase project, Android `google-services.json`, ADC/workload identity, token signing, KMS and Firestore external personal-key registry. Do not commit credentials or service-account files.
- Apply Flyway V12 (`ops.account_deletion_job`). Only the verified Firebase UID is stored as ciphertext under the existing per-user cipher with a distinct `ACCOUNT_DELETION_FIREBASE_UID` authenticated context. No Firebase ID token, Google credential or refresh token is persisted in this job.
- The runtime service account needs `firebaseauth.users.get` for revocation-aware verification and `firebaseauth.users.delete` for Firebase cleanup, plus existing KMS/Firestore key-management permissions. Grant the least required permissions; no client Firebase-admin credentials are introduced.
- Configure the existing OIDC-protected minute scheduler for `POST /internal/jobs/tick` with its exact `PUSH_SCHEDULER_AUDIENCE` and `PUSH_SCHEDULER_EMAIL`. After the scheduler, Firebase/KMS/Firestore and IAM are ready, enable `ACCOUNT_DELETION_ENABLED=true`. This defaults to false and fails closed before acceptance. Account deletion enables the protected worker independently of `ANDROID_PUSH_ENABLED`; notifications may remain disabled. The same tick now retries up to 20 deletion jobs after reminders/room cleanup. Jobs use five-minute claims, one-minute failure retry and a durable Firebase-deleted checkpoint. Do not enable account deletion in a deployment without scheduling the worker; the flag enables the endpoint/security chain but does not create a Cloud Scheduler job.
- Monitor `ops.account_deletion_job.created_at` and alert before `created_at + interval '24 hours'`; alert/remediate any overdue row. Restore Firebase/KMS/Firestore availability/permissions and invoke the protected tick to retry. Jobs are never silently abandoned. Inspect counts/timestamps, not raw identifiers or provider exception text.
- Successful cleanup removes sessions/refresh records, device/FCM registrations, identity/profile rows, reminder preferences and replay snapshots. A minimal non-identifying Hyped UUID/timestamp tombstone remains for backup restore safety and existing shared-room author foreign keys. The job is removed. The external destroyed-key registry remains outside PostgreSQL backups and must be replayed/checked before restored traffic is enabled. Encrypted database backups expire normally within seven days.

Live Firebase deletion requires a disposable test account, configured backend and runtime permissions. Automated tests mock Firebase/KMS failures and exercise the real PostgreSQL revocation/cleanup flow; they do not delete a real Firebase account.

References: [Firebase reauthentication](https://firebase.google.com/docs/auth/flutter/manage-users#re-authenticate_a_user), [Admin user deletion](https://firebase.google.com/docs/auth/admin/manage-users#delete_a_user), [Firebase session lifetime](https://firebase.google.com/docs/auth/admin/manage-sessions). API details are in [the account API contract](../../docs/07-api-spec.md#9-account-and-profile).
