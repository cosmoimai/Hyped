# Android push notifications and fixed event reminders

## Scope and contracts

Only the existing fixed reminders are delivered: 24 hours before (`day_before`), 1 hour before (`hour_before`), and event time (`event_time`). Offsets are elapsed durations from the UTC event instant, not calendar-day arithmetic, so DST does not change the 24-hour or 1-hour offsets. Existing `GET/PUT /api/v1/rooms/{roomId}/reminder-preferences` and its three booleans remain unchanged. Each room's Reminders action already exposes those settings.

The documented `PUT/DELETE /api/v1/devices/{installationId}` and `POST /internal/jobs/tick` are now implemented. No arbitrary scheduling, client user ID, new identity provider, or iOS delivery is added.

## Firebase and Android setup

1. Keep using the same Firebase project/package `com.hyped.hyped` as Google sign-in. Enable the Firebase Cloud Messaging HTTP v1 API in that Google Cloud project.
2. Download the matching `google-services.json` to ignored `frontend/android/app/google-services.json`. Do not commit generated Firebase config, a service-account JSON, signing keys, or ADC credentials. Android uses the Firebase Android SDK's token lifecycle, not a copied token in app SharedPreferences.
3. Run the backend with its existing Firebase ADC/workload identity and per-user personal-data encryption setup. Required existing configuration includes `FIREBASE_IDENTITY_ENABLED=true`, `FIREBASE_PROJECT_ID`, token signing, KMS and Firestore personal-data key registry. Grant the runtime service account the least privileges required to send FCM (`cloudmessaging.messages.create`), in addition to its existing key/data permissions. Use workload identity in deployed environments; local ADC/service-account material must stay outside the repository.
4. Apply Flyway migration V10, then enable `ANDROID_PUSH_ENABLED=true`. Push fails closed without its existing Firebase app and PersonalDataCipher. There is no plaintext-token fallback.
5. Set `PUSH_SCHEDULER_AUDIENCE` to the exact job audience (normally the full HTTPS `/internal/jobs/tick` URL) and `PUSH_SCHEDULER_EMAIL` to the exact dedicated scheduler service-account email. Configure a Google Cloud Scheduler HTTPS POST every minute using that account's OIDC token and audience. Grant Cloud Run invocation permission if the deployment uses an IAM invocation gate. Do not use a mobile bearer token, shared unauthenticated URL, or Firebase ID token for this worker.
6. Build/run Android, sign in, and open **Notifications** from Home. Read the explanation and tap **Enable notifications**. Android 13/API 33+ requests `POST_NOTIFICATIONS` only after this action. Older Android uses the OS app/channel notification setting without a runtime prompt. Denial does not block any room action. **Open Android settings** supports changing app/channel permission later; returning to the app rechecks permission and registers/removes the token.
7. Real delivery requires a Google Play services device/emulator, reachable backend, correct Firebase project, enabled personal-data encryption, worker invocation, and OS permission. Test both foreground and background receipt. Do not use the Firebase Console's notification-composer default message for privacy validation: this implementation sends **data-only** messages, never an FCM `notification` payload.

## Registration and cleanup

Registration is a PUT with `platform: ANDROID`, `fcmToken`, `notificationsEnabled`, and `appVersion`. Both PUT and DELETE return 204 without echoing a token. The authenticated JWT supplies the user and session. The path must match the JWT installation claim, and the server checks that the active session actually owns that Android installation. Unknown ownership fields are rejected. False `notificationsEnabled` or DELETE removes the token without revoking the login itself.

Registration validates the authenticated user/session/installation in a short database transaction,
then releases its locks before calling the external personal-data encryption service. A second transaction
revalidates the same active binding and key reference before replacing the token. Logout, expiry,
device revocation or key replacement during encryption prevents the registration from being saved.
Removal stays a single database transaction and requires no external encryption call.

Tokens are encrypted with the existing per-user cipher under `DEVICE_FCM_TOKEN`. A SHA-256 fingerprint of the high-entropy FCM token supports replacement/uniqueness; neither value is returned. A replacement takes effect for that device and removes registrations for the same token/installation on previous accounts. A late UNREGISTERED response for an old fingerprint cannot remove its replacement. A token rotation racing an already claimed send cancels that stale snapshot rather than sending it to the old token.

Database triggers clear push credentials immediately on session revocation/deletion (including refresh-token reuse), device invalidation and account transition out of active state. Account/device/room deletion also cascades delivery rows. Worker polling removes expired-session tokens. Recipients are checked again before sending, including session expiry, account/device status, current room event/status, membership and reminder preference. Removal after FCM acceptance cannot recall a remote message; Android's generic presentation and session gate protect content.

Flutter clears posted notifications, navigation records and the native session binding before logout/account change, and on all credential-deletion paths. It deletes the SDK token on logout/account change. Epoch checks prevent old in-flight registration work from reactivating the old native session. Push registration uses authenticated Dio and its refresh handling, with an expected-session fence to prevent a retry from silently switching owners. A token refresh observed while the process is alive re-registers immediately; refreshes received while the app is not running are reconciled on its next authenticated launch/resume. No authentication token is copied to native preferences or a background isolate.

If remote logout fails offline, local session gating and SDK token deletion still prevent room information from being shown. The backend registration becomes ineligible on server revocation/expiry or replacement. Remote account deletion/revocation cannot instantly clear an offline device's OS tray; the text is deliberately generic and carries no room title, event time, location, description or member information.

## Durable scheduling and delivery

V8's room reminder records and existing rescheduling/archive hooks are reused. Expired room-reminder claims can now be recovered. V10 adds per-device delivery rows unique on `(room_id, kind, event_at, device_id)`, so retrying ticks or editing away and back cannot create a second delivery for the same logical occurrence. Fanout and completion of a claimed room reminder commit together. Past lead times, members joining after a boundary, and settings enabled after an occurrence has already been processed do not generate catch-up reminders.

Worker `sent` counts FCM acceptance, not proof of device receipt or presentation. Delivery claims use `FOR UPDATE SKIP LOCKED`, a claim token and a five-minute lease. Explicit transient FCM errors retry at 1, 2, 4 and 8 minute intervals, up to five attempts. Expired/unregistered or sender-mismatched tokens are removed; permanent/unknown errors become terminal without logging provider exception text. Lease recovery and FCM transport retries may repeat a message: its stable `notificationId` is preserved, and Android durably deduplicates before presentation across process restarts. Recent IDs are retained for 24 hours; an excessive inbox drops new messages rather than evicting recent dedup keys. There is no claim of exactly-once provider transport. A crash between recording/presenting or a terminal/ambiguous provider error can lose a reminder; refresh on app open remains the source of truth.

Android accepts only valid `EVENT_REMINDER` IDs, one of the three fixed kinds, matching active session and current OS/channel permission. It creates a generic local notification with an immutable explicit PendingIntent. Taps resolve the private inbox entry and require the same session on both native and Flutter sides. go_router opens `/rooms/{roomId}`, whose authenticated REST load remains authoritative. Session binding/inbox data is excluded from backups and transfer.

## Verification and live checks

Automated coverage includes JWT ownership and rejected user IDs, token replacement/removal/revocation/deletion/expiry, all three boundaries, DST/UTC offsets, unique scheduling and retry claims, preference cancellation, edit/archive/delete/member removal, late invalid-token results, denied permission, session/account races, safe error text and notification navigation. Android JVM tests cover payload validation, permission/session gates and duplicate IDs.

Before release, use two accounts on a real Android device to verify permission denial/approval, token replacement, background data-only receipt, repeated notification IDs, warm/cold taps, logout/account switch with a delayed message, revoked sessions, edit/archive/member cancellation and worker OIDC rejection of a mobile token. Advance a controlled test clock or schedule events at the fixed boundaries rather than adding a production arbitrary-offset API. No real Firebase credentials or live sends are used by automated tests.

References: [Android FCM setup and permission](https://firebase.google.com/docs/cloud-messaging/android/get-started), [data-message handling](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), [FCM errors](https://firebase.google.com/docs/cloud-messaging/error-codes).
