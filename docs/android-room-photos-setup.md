# Android room photos and cloud setup

Room photos are implemented using the documented private R2 upload contract. **Cloud services are not provisioned or enabled by this change.** Without configuration, Android can select and preview a photo, then shows a setup-required message. Continue remains disabled for that unsaved photo; discard it to keep using the original theme. No local-only image is silently presented as shared room data.

## What the app does

Create/edit a room → **Style** → **Custom memories** → **Choose a room photo**. Android's system Photo Picker is used on Android 13+, with the system document picker on older supported versions. No camera, broad storage, or photo-library permission is added. Cancellation leaves the current theme alone.

The phone reads at most 5 MB, rejects animation, respects EXIF orientation, downsizes to at most 2048 × 2048 and re-encodes JPEG at at most 1.5 MB. Original filenames, paths, URI grants and metadata are not persisted. The transient selected preview disappears on discard, navigation, logout or account/session replacement.

Uploads use authenticated API authorization followed by an **interceptor-free** Dio PUT to the exact HTTPS R2 endpoint. JWTs never go to R2. The backend checks actual size/type/hash/dimensions, decodes in a bounded two-worker pool, strips metadata by re-encoding, and calls Google Vision SafeSearch. Approved photos become READY; uncertain photos remain hidden in MANUAL_REVIEW; rejected photos cannot replace a room cover. There is no reviewer console or approval bypass in this change. Pending/error/review photos must be discarded or replaced before saving.

Only an uploader's READY asset can be attached to a new/edited room they control. Co-hosts can retain the existing room cover while editing other fields. Current room members can request authorized 15-minute delivery URLs; removed users and outsiders cannot. The Flutter UI downloads bounded bytes without forwarding authentication to R2 and renders memory images with safe errors. Signed URLs and image bytes are not stored in Drift or widget snapshots. Offline views use the existing safe color fallback.

## 1. Create a private R2 media bucket

In Cloudflare → R2, create a dedicated bucket, for staging, use `hyped-staging-room-photos`. Keep public access and the `r2.dev` public URL disabled. Do not configure a public custom domain for these objects.

Create bucket-scoped S3 API credentials allowing object read/write/delete for this bucket only. Keep the access key and secret on the backend; never put them in Flutter, `--dart-define`, Android resources or Git. Record the S3 endpoint, in the form `https://<32-character-account-id>.r2.cloudflarestorage.com`.

Add a lifecycle rule deleting the **`quarantine/` prefix after one day**. Ready objects live under `ready/` and must not have that one-day rule. Quarantine and ready keys are separate, so replaying an unexpired upload URL cannot overwrite a published cover. Keep object versioning disabled; old object versions would require additional purge handling. Android's native HTTP client does not need browser CORS; a future web client requires its own reviewed origin allowlist.

See [R2 presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/) and [R2 CORS](https://developers.cloudflare.com/r2/buckets/cors/).

## 2. Enable Vision in the backend's Google Cloud project

Enable billing and **Cloud Vision API** (`vision.googleapis.com`) in the intended Google Cloud project. Keep budget alerts enabled. Configure the backend's Application Default Credentials with permission to consume Vision in that project. In production use the backend's managed service identity; do not download a production service-account key into the repository.

The existing local Firebase Admin credential can be used through `GOOGLE_APPLICATION_CREDENTIALS` only if its service account is permitted to call Vision in the enabled/billed project. If authentication reports a quota-project issue, follow Google's ADC quota-project setup and grant the service identity the required Service Usage Consumer role. Do not grant broad project Owner/Editor just to enable photo moderation.

See [Vision setup](https://docs.cloud.google.com/vision/docs/setup), [Vision authentication](https://docs.cloud.google.com/vision/docs/authentication) and [SafeSearch](https://docs.cloud.google.com/vision/docs/detecting-safe-search).

## 3. Supply backend-only configuration

Set these through your excluded local configuration or deployment secret manager:

```text
MEDIA_ENABLED=true
TERMS_VERSION=2026-10-26
PRIVACY_VERSION=2026-10-26
R2_MEDIA_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_MEDIA_BUCKET=hyped-staging-room-photos
R2_MEDIA_ACCESS_KEY=<private bucket-scoped access key>
R2_MEDIA_SECRET_KEY=<private bucket-scoped secret>
```

On this Mac, store the `MEDIA_` and `R2_MEDIA_` values outside the checkout, such as an owner-readable `~/.config/hyped/local-backend/media.env`, and export them in the terminal that runs `python3 scripts/local-backend.py start`. Set `TERMS_VERSION` and `PRIVACY_VERSION` to `2026-10-26` in the existing private `~/.config/hyped/local-backend/environment.json`: the launcher intentionally takes policy versions from that private configuration rather than inheriting shell overrides. Keep the current private Firebase/identity/signing configuration. Restart the local backend; do not recreate its keys or reset the database. Existing local setup remains usable with `MEDIA_ENABLED=false`, the default.

Photo/storage/moderation disclosures were introduced in the 2026-10-25 drafts. Staging now uses owner-approved Terms/Privacy version `2026-10-26`; preserve that version and the existing content-rules version when enabling media. Approval is for staging only; production publication still requires owner/legal review. Enabling media requires these disclosures (or newer reviewed versions), and existing users must explicitly accept the current required documents before uploading.

The new V15 migration creates `app.media_asset` and the nullable room-theme media reference. Flutter's derived cache moves to schema v2; rebuilding it discards old cached projections, not server rooms.

## 4. Enable and monitor existing protected cleanup

Configure the existing authenticated OIDC `POST /internal/jobs/tick` scheduler before enabling production photos. It already handles room/account lifecycle work and now also attempts at most 20 orphan-photo cleanups per tick. Schedule it at least every minute, keep it authenticated, and alert on persistent storage failures. Never expose an unauthenticated cleanup endpoint.

Unattached uploads are eligible for cleanup after 24 hours. Permanent room deletion marks its last referenced cover for cleanup. Account deletion first switches any remaining shared-room references to the safe preset and deletes the user's objects; a storage failure keeps the existing deletion job pending for retry, rather than falsely reporting completion. A minimal DELETING metadata tombstone remains until at least one hour after the original upload grant expires, and cleanup rechecks deletion during that interval. This handles late/replayed quarantine writes; the bucket's quarantine lifecycle is an additional safeguard. Keep storage credentials available for cleanup even if new photo uploads are disabled.

## 5. Test manually

1. Verify ordinary color themes still create/edit without cloud configuration.
2. With configuration enabled, select a static photo, confirm the preview, save, reopen and view the same room on a second authenticated membership.
3. Confirm a canceled picker or rejected/review photo preserves the previous saved cover.
4. Try an oversized source and an animated image; expect a clean error, no partial room edit.
5. Confirm a member can view/share the room but cannot edit its cover; owners and co-hosts may edit under existing rules/ETags.
6. Remove the viewing member and refresh: room and photo access must fail safely. Logout/account change must remove old image displays.
7. Test offline: cached room text remains readable with a color fallback; selecting/saving an upload cannot bypass server authorization.
8. Delete a test room/account through the normal lifecycle, run protected cleanup, and confirm private objects disappear. Test failed R2 deletion and the durable retry before production.
9. Do not publish until R2, Vision, cleanup scheduling, privacy disclosures and end-to-end cloud delivery have been checked with the real configured services.

## Android edit-picker return regression — 2026-10-29

Returning from the system photo picker triggers the app's normal room refresh.
The edit route retains its photo-selection controller while room loading
briefly replaces the form. This prevents discarding the chosen photo before
upload begins. Authentication/session checks and cancellation on route disposal
remain in place.

The regression test delays the picker result, invalidates the room-detail
provider, completes selection during the reload, then checks that upload occurs
and the refreshed form's save draft contains the new media reference. It fails
against the previous implementation. On a device, replace a room photo, wait
for “Photo ready”, continue to Review and save; verify Home/detail and reopen.
This check does not establish external object cleanup or moderation outcomes.
