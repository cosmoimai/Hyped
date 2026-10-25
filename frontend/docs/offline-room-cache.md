# Android read-only room cache

## Data and security boundary

`RoomCacheDatabase` uses Drift 2.31.0 and SQLite in the Android app-private application-support file `hyped_room_cache.sqlite`. Drift runtime/generator versions are pinned together for this repository's Dart 3.10.8 / Flutter test dependency constraints. `sqlite3_flutter_libs` supplies the native SQLite library for this compatible Drift version. The executor runs on a Drift database isolate; no background networking or write queue is added.

Schema version **1** has two tables:

- `cached_rooms`: composite primary key `(account_id, room_id)`, title, event instant and time zone, optional location and description, safe theme kind/preset/overlay keys, caller role, member count, revision, creation/update/server timestamps and last successful synchronization time.
- `cache_lists`: account primary key and last successful list synchronization time, including for an authoritative empty list.

Only currently active rooms are persisted. There are no arbitrary JSON/private-response blobs, member profiles, ownership/provider identifiers, images, URLs, session IDs, access/refresh credentials, invitation tokens, preview references or room codes in SQLite. Cached objects have no reusable ETag and are marked read-only. Room role/revision are display snapshots, never authority to mutate. Timestamps persist as UTC SQLite epoch seconds and render in device-local time.

The cache does contain protected room content, including optional location/description needed by the existing detail UI. It is app-private SQLite, not an encrypted database. Do not add fields merely because they appear in an API response; expand the allowlist only for a reviewed offline UI need. Before publishing legal documents/store disclosures, include this on-device room caching in the deployment's privacy review.

The account/session binding lives separately under `room_cache.session_owner.v1` in the existing platform secure storage. It contains only the backend-issued account ID and session ID. Sign-in uses `exchange.user.id`; pre-cache credentials discover the account through authenticated `/me` with an expected-session fence. No unsigned JWT payload or client-supplied account selector is used to establish ownership. Restoring a matching secure binding allows offline reads without a network identity lookup.

Every query includes the bound account partition. A session replacement or account switch erases the old cache. Cache ownership changes, reads/writes and erasure share a serial queue. Epoch checks fence in-flight server responses; a separate read generation discards requests started before a known room mutation/archive/access-loss invalidation, so a late active snapshot cannot resurrect removed data; once a scope is cleared, a late room or `/me` response cannot reopen it. Only a newly authenticated exchange can explicitly start a cleared scope.

## Repository and screen flow

1. Authentication restores valid stored credentials and their cache binding. No credentials means protected cache erasure. An old binding with a different/missing session is discarded.
2. List/detail controllers request an account-scoped cached projection first, publish it immediately and show **Cached room data · Read only** and the previous **Last synced** timestamp. The existing server request follows; cached data is not returned as a successful network response.
3. A successful server response is authoritative. The repository records its receive time, updates the active-room projection and returns a live model. Controllers remove the read-only indication and synchronize widgets from this successful response. Detail synchronization can be newer than the last complete list synchronization.
4. Transport failures retain the last successful display and timestamp. Other server failures preserve a cached read-only display with a retry message; they do not invent new synchronization success. A first load with no cache shows the normal safe error/retry state. Pull-to-refresh and foreground resume recheck visible room controllers.
5. An authoritative list replaces the account's cached working set. Omitted rows are evicted; this can evict an accessible detail beyond the current 50-row list cap, which is safe cache loss rather than access revocation. The widget coordinator separately checks omitted selected rooms before marking them removed, preserving the existing capped-list contract. Archived/deleting rooms are shown from live responses but are never retained as active offline snapshots.
6. Successful detail archive/delete/inaccessibility responses evict stale projections. Leave/permanent-delete remove the row; archive removes it; restore caches the new active server projection. Member/ownership mutation responses invalidate the corresponding room because their narrower payload does not provide a complete fresh room projection. Member-read access loss also invalidates it. No member list is cached.

A cache read/write/open failure cannot turn successful server data into a network failure. Malformed rows are discarded, and storage failures disable cache reuse as needed. The app can continue with live reads without trusting damaged storage.

## Offline operations and routing

There is no offline create, edit, join, membership change, lifecycle change, merge algorithm, mutation replay or write queue. Known transport failures put the authenticated Dio into read-only mode. A mutation then fails before dispatch with `OFFLINE_READ_ONLY` and the message: **You are offline. Rooms are read-only. Refresh when connected before making changes.** A first write attempted before connectivity is known can fail normally as a transport error, mapped to the same safe message; it is not queued or automatically retried. Logout remains best effort remotely and guarantees local cleanup attempts.

Successful authenticated reads clear the known-offline flag; the user must explicitly submit a new mutation. A cached detail never enables editor/member/lifecycle action buttons, and a direct editor entry requires a live room refresh. Backend permissions/revisions and current consent remain authoritative. The public invite-preview Dio stays interceptor-free and previews still work before sign-in.

On cold startup, a valid restored session with a persisted account binding may immediately browse cached `/home` and room-detail routes while policy status is loading or unavailable, rather than waiting for a network timeout. A connectivity failure also permits these read paths. A confirmed missing/current-version change still redirects to consent. This is read access, not inferred legal acceptance: no new acceptance is persisted and create/join remain guarded. Other required-consent failures still show the consent flow. Online policy-version changes continue to require explicit fresh acceptance.

## Cleanup and Android storage

`CacheTokenStore` wraps the existing notification/widget token-store decorators, so logout, accepted/remote account deletion, revoked sessions and failed authentication refreshes clear the cache as part of credential deletion. Cache cleanup fences responses before awaiting disk/native work. Credential cleanup runs in `finally` even if disk cleanup fails. Explicit logout begins cache cleanup before attempting backend revocation. App theme remains a separate safe device preference.

Active-room list/detail controllers are disposed when no longer observed and reset when auth state changes. Account/session binding is removed alongside protected rows. If storage fails, the old scope remains unusable; cleanup never turns a replacement session into the old account. SQLite `secure_delete` is enabled and the device database uses delete journals. These controls provide application cleanup, not a forensic-erasure guarantee on flash storage.

Android cloud backup and device-transfer rules explicitly exclude the cache file and journal/WAL/SHM sidecars; `allowBackup=false` remains in place. Do not move the database to shared/external storage. Cached revocations/room changes can only be learned while connected; until then the clearly marked offline snapshot is the last successfully authorized one. Expired/non-restorable authentication clears it under the existing session rules.

## Schema and generated-code validation

The initial schema snapshot is `frontend/drift_schemas/drift_schema_v1.json`; v2 adds a nullable, account-partitioned `media_asset_id` reference and its snapshot is `drift_schema_v2.json`; generated `room_cache_database.g.dart` is committed. This database is derived data with no unsynchronized writes. A schema-version mismatch rebuilds the two known cache tables instead of migrating arbitrary private data or making lossy guesses. Schema changes must bump `schemaVersion`, regenerate/export the snapshot and test the rebuild/migration path. Never silently reuse incompatible rows. Corruption/read failures fall back to online reads and do not log SQL row values or raw exceptions.

From `frontend/`:

```sh
flutter pub get
dart run build_runner build --force-jit
dart run drift_dev schema dump lib/features/rooms/data/cache/room_cache_database.dart drift_schemas/
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

JIT builder compilation is used for reliable generation with this checkout/SDK; code generation must leave the committed generated file and snapshot unchanged. No backend migration, API contract or deployment change is required. Tests exercise actual SQLite partitioning/reopen/schema rebuild, cold cache display, authoritative refresh/eviction, last-sync preservation, widgets, account/session cleanup and races, offline mutation errors and cold offline routing.

Room photo bytes and signed delivery URLs remain memory-only and are never written into SQLite. An offline photo room uses the safe color fallback. The v1→v2 derived-cache rebuild intentionally discards old projections and reloads them from the server.
