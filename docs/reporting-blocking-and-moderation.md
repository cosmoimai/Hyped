# Android reporting, blocking and moderation

## Member controls

Room actions → Report room or Report photo or style. Members → shield button → Report account or Block account. Settings → Blocked accounts → Unblock. Reports and blocks are separate explicit actions. Blocking and unblocking explain consequences and require confirmation; reports require an unselected reason. Optional details are limited to 500 characters. The report form warns against including credentials or invitation secrets.

A report never automatically removes content or suspends an account. Five new reports per authenticated account per rolling 24 hours are permitted; idempotent retries do not spend another allowance. The server derives reporter identity from JWT, rechecks current membership, and resolves an account/photo only through the room context. Arbitrary target accounts, foreign rooms and caller-supplied reporter identities cannot create reports.

## Block transaction and privacy

Both user rows are locked in stable UUID order, then all shared rooms in stable order. The context is revalidated after waiting. The directional block, membership/count/revision changes, reminder-preference removal, cancellation of queued reminder deliveries and durable access invalidations commit together.

- Remove the target from rooms owned by the blocker, including archived rooms.
- Make the blocker leave rooms owned by the target, preserving the target owner.
- Retain both memberships in third-party-owned rooms; replace the other account's name with `Blocked account`, omit their photo and prohibit direct role/remove/transfer operations between the pair.
- Refuse a future join when either directional block exists between candidate and current room owner. Existing join replay records do not bypass this check.
- Refuse an ownership transfer that would make a blocked pair co-resident in one account's owned room.
- Return only aggregate removal/leave counts. Never return reverse block records, blocker identities, other room IDs, or send a block notification.
- Unblocking removes only the caller's directional record. A reciprocal block remains authoritative. No memberships or roles are restored.

Own block lists return only safe display names, opaque target IDs needed for unblock and block time, with cursor pagination. Internal security/provider identifiers and emails are not returned. List names are resolved outside DB transactions; unavailable/deleted profiles use a generic label.

## Persistence and cleanup

Flyway V16 adds `app.user_block`, `app.report`, `ops.safety_request`, `ops.room_access_invalidation`, `ops.moderation_audit`, `ops.moderation_rate`, and `app.room.moderation_hold`. Authenticated room responses expose only the boolean `moderationHold`, allowing Flutter to hide restore while a hold applies; no reporter or reviewer identity is exposed. Archived rooms are not retained in the offline cache.

Report details use the existing per-user cipher with `REPORT_DETAILS` field binding. External key access occurs outside DB transactions. No free text is stored in replay/audit records. Reports retain a controlled category, status and subject fingerprint for at most 90 days. Account cleanup removes reporter/subject IDs, encrypted details, all directional blocks, replay records and pending account invalidations. Physical user deletion also strips report detail through a defensive DB trigger. Report expiry cascades its reviewer audit rows. Replay records expire after 24 hours.

The existing protected job tick runs retention cleanup. Configure it and monitor overdue report expiry before production; simply declaring `retain_until` does not perform deletion. Reviewer identity is stored as a SHA-256 digest, never an ID token or email.

Access invalidations contain only recipient account, room UUID, version UUID and time. They expose no block reason or initiator. They survive room deletion and are acknowledged only after local protected projections are removed. A stale acknowledgment cannot erase a newer version. Flutter consumes pending invalidations before online room-list/detail refresh; it removes cached room content, marks widget snapshots unavailable and evicts in-memory images. Failed local removal leaves the invalidation pending and disables cached reads rather than acknowledging stale content. Successful local blocking clears the derived account cache and widget snapshots before refreshing. Unblocking does not erase unrelated widget snapshots. An acknowledgment failure leaves server work pending for retry without blocking authoritative room reads after successful local removal. Logout/account-change fences prevent a late response from touching another account.

This is synchronization on refresh, not an immediate remote wipe or new background push service. An offline device cannot learn of a remote block until it reconnects. Other participants' old open screens refresh through the existing reload/resume paths; no claim of live room-change push delivery is made.

## Protected reviewer API

The member app has no moderator controls. The operational review API is disabled by default:

```text
MODERATION_REVIEW_ENABLED=false
```

To enable on the intended HTTPS backend, configure excluded server/CI values:

```text
MODERATION_REVIEW_ENABLED=true
MODERATION_AUDIENCE=https://YOUR_BACKEND/internal/moderation
MODERATION_REVIEWERS=approved-review-service-account@YOUR_PROJECT.iam.gserviceaccount.com
```

Use a dedicated least-privilege reviewer identity with Google-issued OIDC ID tokens for that exact audience. Enable service-account impersonation only for approved operators. Never grant app users the reviewer identity or use a mobile/Firebase token as reviewer proof. No key or token belongs in Git, shell history, ordinary preferences, screenshots or support tickets. Do not weaken verification to test locally. Missing allowlist/audience fails startup when review is enabled; mobile JWTs cannot use this separate Google-signature/issuer/audience/verified-email allowlist boundary. The scheduler identity is not automatically a reviewer. Review requests are independently limited to 60/minute per reviewer across instances.

Endpoints:

- `GET /internal/moderation/reports?limit=50`: oldest open queue, bounded at 100. Report text is decrypted only for a still-active reporter; unavailable keys/deleted reporters produce no free text. Check missing-context/key failures before deciding.
- `GET /internal/moderation/reports/{id}`: current room title/description/revision and the reported subject reference. Deleted subjects remain safely unavailable. No reporter credentials or provider fields.
- `GET /internal/moderation/reports/{id}/photo`: bounded sanitized JPEG with `private, no-store`, only for the reported uploaded photo. READY content is read from its published private object, so quarantine expiry does not break review. No signed URL is returned.
- `POST /internal/moderation/reports/{id}/decision`: `{ "action": "DISMISS|REMOVE_PHOTO|ARCHIVE_ROOM|RELEASE_ROOM|SUSPEND_ACCOUNT", "expectedRoomRevision": 3 }`, 204.

`DISMISS` records no content action. `REMOVE_PHOTO` switches all references to a safe preset, increments revisions, revokes new delivery authorization and schedules durable external deletion. `ARCHIVE_ROOM` requires the current room revision, cancels reminders and applies a moderation hold: owners cannot bypass it by restoring. The ordinary 24-hour lifecycle remains in effect. `RELEASE_ROOM` applies only to a previously moderation-archived report and releases the hold without restoring the room or extending its recovery deadline. `SUSPEND_ACCOUNT` requires an account report and revokes current sessions, refresh credentials and devices and cancels active reminder deliveries. Suspension is a deliberate operator decision, never a threshold-based automatic ban. No self-service unsuspension path is introduced.

Decisions lock current domain resources, serialize competing reviews, record the reviewer digest and action transactionally, and safely replay the same decision. A different terminal decision returns a conflict. No external storage or key operation runs inside the decision transaction. Previously issued photo delivery URLs may work for up to 15 minutes until object removal, as documented for existing photos.

There is no new graphical admin console or automatic approval path for uncertain uploads. Those photos remain hidden; this change supplies the operational report review queue and enforcement actions. Do not bypass upload moderation with direct status edits.

## Deployment and manual acceptance

1. Review/update approved Privacy Policy, Terms/content rules and support/appeal contact for report text, block records, reviewer access and 90-day retention. Use a new document version; never rewrite previously accepted text.
2. Configure protected retention/media cleanup and alerts for oldest open report, overdue expiry, failed review actions and external photo deletion failures.
3. Configure the dedicated reviewer OIDC identity and exact audience. Keep review disabled until credentials and operator access have been verified. Test member-token denial as well as approved reviewer access.
4. Use two disposable accounts plus a third owner: test both ownership directions, shared-room masking, both invitation types, repeated blocking, reciprocal blocks and unblock without restoration.
5. Confirm reports reach the queue, review a harmless fixture, test revision conflicts, hide a cover and verify storage cleanup, then test suspension on a disposable account.
6. Verify offline cached content, reconnect invalidations, multiple widgets, logout/account switch and report/account-deletion pseudonymization. Never use customer data for these checks.

Home sync feedback is now a five-second snackbar after a successful pull-to-refresh. A persistent last-sync timestamp is removed from Home; offline/error banners remain and room detail still exposes last successful synchronization time.
