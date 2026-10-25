# Hyped! MVP API Specification

**Status:** Draft for review  
**Last updated:** 2026-09-14  
**API style:** HTTPS REST with JSON  
**Backend:** Spring Boot modular monolith  
**Related documents:** [`02-requirements.md`](./02-requirements.md), [`03-user-flows.md`](./03-user-flows.md), [`05-hld.md`](./05-hld.md), [`06-database-design.md`](./06-database-design.md)

## 1. Purpose

This document defines the contract between the Hyped! Flutter clients and the Spring Boot backend. It covers resource paths, authentication, authorization, headers, request and response bodies, validation, status codes, pagination, optimistic concurrency, idempotency, rate limiting, uploads, errors, and external-client integrations.

This is the human-readable contract for the MVP. An `openapi.yaml` file should become the machine-readable source once implementation starts. Contract tests must verify that the OpenAPI definition and deployed API remain compatible with the decisions here.

## 2. API principles

- APIs are resource-oriented and versioned under `/api/v1`.
- All production traffic uses HTTPS.
- JSON property names use `camelCase`.
- Public identifiers are UUID strings; secrets are never used as resource identifiers.
- The server is authoritative for membership, roles, limits, invitations, room revision, and event instant.
- Countdown ticks happen on the device and do not call the API every second.
- Mutations are safe to retry through idempotency and optimistic concurrency.
- Error codes are stable and localizable; server messages are safe fallbacks, not final UI copy.
- Responses expose only fields needed by the requesting flow.
- External-service payloads are normalized so Firebase, R2, and database details do not leak into the client contract.

## 3. Base URLs and environments

| Environment | Base URL pattern | Purpose |
|---|---|---|
| Local | `http://localhost:8080/api/v1` | Local development only |
| Development | `https://api.dev.<domain>/api/v1` | Shared development |
| Staging | `https://api.staging.<domain>/api/v1` | Release validation |
| Production | `https://api.<domain>/api/v1` | Public mobile applications |

The final owned domain is selected before link association and release configuration. Clients never construct provider URLs from the API base URL.

## 4. Common protocol rules

### 4.1 Content types

- Standard requests and responses: `application/json`
- Partial room/profile updates: `application/merge-patch+json`
- Direct R2 upload: provider-authorized content type returned by the upload API
- Error responses: `application/problem+json`

### 4.2 Dates and times

- Instants use RFC 3339 UTC strings, such as `2026-12-31T18:30:00Z`.
- Event creation and edits send `eventLocalDate`, `eventLocalTime`, and `eventTimeZone` so the backend can validate the intended civil time.
- The response returns normalized `eventAt` plus the selected IANA zone.
- Local dates use `YYYY-MM-DD`.
- Local times use 24-hour `HH:mm[:ss]`.
- Clients never send a numeric UTC offset as a substitute for an IANA zone.

### 4.3 Common request headers

| Header | Required | Purpose |
|---|---:|---|
| `Authorization: Bearer <access-token>` | Authenticated routes | One-hour Hyped! access token |
| `X-Request-Id` | Recommended | Client-generated UUID for support correlation |
| `Idempotency-Key` | Required on designated mutations | Opaque unique key, maximum 128 characters |
| `If-Match` | Required on revision-protected updates | Expected strong ETag |
| `Accept-Language` | Optional | Preferred error fallback language |
| `X-App-Version` | Required | Mobile semantic version/build |
| `X-Platform` | Required | `android` or `ios` |

The backend creates a request ID if the client does not supply a valid one and returns it as `X-Request-Id`.

### 4.4 Common response headers

| Header | Purpose |
|---|---|
| `X-Request-Id` | Correlation ID |
| `ETag` | Current resource revision where supported |
| `RateLimit-Limit` | Applicable request limit |
| `RateLimit-Remaining` | Remaining requests in the current window |
| `RateLimit-Reset` | Seconds until reset |
| `Retry-After` | Delay for `429` or temporary `503` responses |

### 4.5 Identifier and enum representation

- UUIDs are lowercase canonical strings.
- API enum values use `UPPER_SNAKE_CASE`.
- Unknown enum values must not be treated as a successful known state.
- Database enum/check values may differ internally and are not part of this contract.

## 5. Authentication and sessions

### 5.1 Token model

- Firebase Authentication completes Google or Apple sign-in.
- Flutter sends the Firebase ID token once to the exchange endpoint.
- Spring Boot verifies it and returns a Hyped! access token plus refresh token.
- Access tokens are **RS256 JWTs** and last **one hour**.
- Refresh tokens last **30 days**, rotate on every successful refresh, and are stored in platform secure storage.
- Reuse of an already rotated refresh token revokes only that device's token family.
- One account supports at most **five active device installations**.
- Normal logout/revocation disables refresh immediately; an issued access JWT may remain valid until expiry. Suspended or compromised accounts are denied immediately.

### 5.2 Authentication endpoints

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| `POST` | `/auth/exchange` | Firebase ID token in body | Create or restore Hyped! account/session |
| `POST` | `/auth/refresh` | Refresh token in body | Rotate session tokens |
| `POST` | `/auth/logout` | Access token | Revoke current session |
| `GET` | `/auth/sessions` | Access token | List active devices/sessions |
| `DELETE` | `/auth/sessions/{sessionId}` | Access token | Revoke another session |
| `POST` | `/auth/devices/{deviceId}/revoke` | Firebase ID token in body | Free a device slot during sign-in |
| `POST` | `/auth/link/prepare` | Access token | Start confirmed provider linking |
| `POST` | `/auth/link/confirm` | Access token plus proof | Confirm Google/Apple account link |

### 5.3 Exchange identity

`POST /auth/exchange`

```json
{
  "firebaseIdToken": "<token>",
  "installationId": "019b1f1d-48f0-7b33-99da-4a498fe22d11",
  "platform": "ANDROID",
  "deviceName": "Pixel 10",
  "appVersion": "1.0.0+1"
}
```

Success: `200 OK` for an existing account or `201 Created` for a new account.

```json
{
  "accessToken": "<hyped-access-token>",
  "accessTokenExpiresAt": "2026-09-09T11:30:00Z",
  "refreshToken": "<rotating-refresh-token>",
  "refreshTokenExpiresAt": "2026-10-09T10:30:00Z",
  "sessionId": "019b1f21-31ca-749e-b9b9-96d4c0efec40",
  "user": {
    "id": "019b1f20-4152-7ce8-ae9e-b12b87fe8a31",
    "displayName": "Aarav",
    "photo": null,
    "profileRevision": 1
  },
  "isNewAccount": false
}
```

Validation and errors:

- Missing, expired, wrong-project, wrong-audience, wrong-issuer, or invalid Firebase token: `401 IDENTITY_TOKEN_INVALID`.
- Sixth device: `409 DEVICE_LIMIT_REACHED`, with a safe list of current sessions so the user can revoke one.
- Matching verified email on another account: `409 ACCOUNT_LINK_CONFIRMATION_REQUIRED`; accounts are never auto-merged.
- Deleted account without a permitted new registration state: `410 ACCOUNT_DELETED`.
- `deviceName` is normalized and limited to 80 characters; it is display metadata and never used as an authentication factor.

### 5.4 Refresh tokens

`POST /auth/refresh`

```json
{
  "refreshToken": "<current-refresh-token>",
  "installationId": "019b1f1d-48f0-7b33-99da-4a498fe22d11"
}
```

Success `200` returns a new access token and new refresh token. The submitted refresh token becomes invalid immediately.

Errors:

- Invalid or expired: `401 SESSION_EXPIRED`.
- Previously rotated token reused: `401 REFRESH_TOKEN_REUSE_DETECTED`; the token family is revoked.
- Installation mismatch: `401 SESSION_DEVICE_MISMATCH`.

### 5.5 Session list

`GET /auth/sessions` returns active, unrevoked sessions ordered by `lastUsedAt desc`:

```json
{
  "items": [
    {
      "sessionId": "019b1f21-31ca-749e-b9b9-96d4c0efec40",
      "deviceName": "Pixel 10",
      "platform": "ANDROID",
      "createdAt": "2026-09-09T10:30:00Z",
      "lastUsedAt": "2026-09-09T11:30:00Z",
      "isCurrent": true
    }
  ],
  "nextCursor": null
}
```

Session responses expose only:

- `sessionId`
- `deviceName`
- `platform`
- `createdAt`
- `lastUsedAt`
- `isCurrent`

They do not expose FCM tokens, provider tokens, IP history, or refresh-token hashes.

`DELETE /auth/sessions/{sessionId}` returns `204` and revokes that session's refresh tokens. It also invalidates
the associated device registration so the installation no longer consumes a device slot. Unknown sessions and
sessions owned by another user have the same idempotent `204` response.

Logout invalidates the current installation immediately. Before enforcing the five-device limit, identity exchange
also reclaims registrations that have no unrevoked, unexpired session, so expired sessions cannot permanently consume
slots. The session-management routes require an existing Hyped! access token.

A sixth installation that receives `DEVICE_LIMIT_REACHED` can select a `deviceId` from that error's safe device list
and call `POST /auth/devices/{deviceId}/revoke` with a fresh Firebase ID token in this body:

```json
{
  "firebaseIdToken": "<token>"
}
```

The server verifies the Firebase token, resolves the exact provider identity, and atomically revokes sessions and
refresh tokens for that user's selected device before invalidating its registration. It always returns `204`, whether
the device is already invalid, unknown, or belongs to another account. The sixth installation can then retry exchange.

## 6. Error contract

All API errors use a stable problem envelope.

```json
{
  "type": "https://api.<domain>/problems/room-full",
  "title": "Room is full",
  "status": 409,
  "code": "ROOM_FULL",
  "detail": "This countdown already has 25 members.",
  "requestId": "019b1f26-729d-777c-971a-0a7a6d62d691",
  "fieldErrors": [],
  "retryable": false
}
```

Field validation example:

```json
{
  "type": "https://api.<domain>/problems/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "requestId": "019b1f26-729d-777c-971a-0a7a6d62d691",
  "fieldErrors": [
    {
      "field": "title",
      "code": "TOO_LONG",
      "message": "Title must contain at most 80 characters."
    }
  ],
  "retryable": false
}
```

### 6.1 Status-code rules

| Status | Use |
|---:|---|
| `200` | Successful read/update/action with body |
| `201` | Resource created |
| `202` | Destructive or asynchronous work accepted |
| `204` | Successful action without response body |
| `400` | Malformed JSON, header, cursor, or syntax |
| `401` | Missing, invalid, or expired authentication |
| `403` | Authenticated account suspended/compromised, or action forbidden |
| `403` | Authenticated but not permitted |
| `404` | Resource unavailable to caller; avoids private-resource enumeration |
| `409` | State conflict, limit reached, duplicate/replayed condition |
| `410` | Previously valid invitation/account/resource intentionally ended where safe to reveal |
| `412` | `If-Match` revision precondition failed |
| `413` | Upload/request payload too large |
| `415` | Unsupported media/content type |
| `422` | Semantically invalid field values |
| `429` | Rate limit exceeded |
| `500` | Unexpected server failure |
| `502` | Required upstream service failed |
| `503` | Temporary dependency or capacity failure |

Stack traces, SQL errors, provider bodies, secret values, and internal class names are never returned.

## 7. Pagination and collection responses

Collections use opaque cursor pagination even where current product limits are small.

Query parameters:

- `limit`: default 20, maximum 50
- `cursor`: opaque server value

```json
{
  "items": [],
  "nextCursor": null
}
```

Cursors are scoped to the endpoint, filter, authenticated user, and ordering. A cursor used with different filters returns `400 CURSOR_INVALID`.

Room/member ordering is stable:

- Active room list: `eventAt asc, id asc`
- Archived room list: `archivedAt desc, id desc`
- Member list: role priority then `joinedAt asc, userId asc`

## 8. Idempotency

The following routes require `Idempotency-Key`:

- Create room
- Join using invitation or code
- Rotate invitation
- Transfer ownership
- Remove member
- Leave room
- Delete room
- Request account deletion
- Create report
- Initiate/finalize media upload

Rules:

- Keys are scoped to authenticated user and operation.
- Records are retained for 24 hours by default.
- Retrying the same key and same canonical request returns the original terminal result.
- Reusing a key with a different body returns `409 IDEMPOTENCY_KEY_REUSED`.
- An in-progress duplicate returns `409 REQUEST_IN_PROGRESS` with a short `Retry-After`.
- Public preview endpoints do not use idempotency because they do not mutate state.

## 9. Profile, devices, and account endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/me` | Get current profile and account limits |
| `PATCH` | `/me` | Edit display name or profile photo reference |
| `GET` | `/me/deletion-readiness` | List blockers before deletion |
| `POST` | `/me/deletion-requests` | Request irreversible account deletion |
| `GET` | `/me/policy-status` | Get current Terms/Privacy/rules/18+ acceptance state |
| `POST` | `/me/policy-acceptances` | Explicitly accept the current policies and affirm 18+ |
| `GET` | `/me/blocks` | List accounts the current user blocked |
| `PUT` | `/me/blocks/{userId}` | Block an account with safe-room handling |
| `DELETE` | `/me/blocks/{userId}` | Unblock an account without restoring memberships |
| `PUT` | `/devices/{installationId}` | Register/refresh an FCM token |
| `DELETE` | `/devices/{installationId}` | Invalidate device push registration |

### 9.1 Get profile

`GET /me`

```json
{
  "id": "019b1f20-4152-7ce8-ae9e-b12b87fe8a31",
  "displayName": "Aarav",
  "photo": null,
  "profileRevision": 3,
  "limits": {
    "activeDevices": 2,
    "maximumDevices": 5,
    "ownedActiveRooms": 4,
    "maximumOwnedActiveRooms": 10,
    "joinedActiveRooms": 9,
    "maximumJoinedActiveRooms": 25
  }
}
```

The implemented read contract returns the fields above with `photo: null` (Flutter uses initials), `Cache-Control: private, no-store`, and live device/active-room counts. Provider-photo URLs, provider subjects, security state and session/refresh credentials are excluded. The JWT's verified subject/session/installation must identify an unrevoked, unexpired session on an active registration; otherwise `401 SESSION_EXPIRED` applies. Profile editing/media remains governed by its separate contract.

### 9.2 Edit profile

`PATCH /me` with `Content-Type: application/merge-patch+json` and `If-Match: "profile-3"`.

```json
{
  "displayName": "Aarav S.",
  "photoMediaAssetId": "019b1f2b-d5e2-7d46-9165-a37ed59d6080"
}
```

- Display name is trimmed, Unicode-normalized, non-blank, and at most 80 characters.
- `photoMediaAssetId: null` removes a custom photo.
- Photo asset must be a ready profile image owned by the user.
- Stale ETag returns `412 PROFILE_REVISION_MISMATCH`.

### 9.3 Device registration

`PUT /devices/{installationId}`

```json
{
  "platform": "ANDROID",
  "fcmToken": "<fcm-registration-token>",
  "notificationsEnabled": true,
  "appVersion": "1.0.0+1"
}
```

Android delivery implementation: PUT and DELETE return `204` with no token response. Both require the
current authenticated session. `installationId` must equal the JWT `did` installation claim, and that
session must own the active Android device. Ownership is derived solely from JWT `sub`/`sid`; unknown
fields including `userId` are rejected. `platform` must be `ANDROID`; `appVersion` is required (1–64
characters). FCM tokens are 1–4096 non-whitespace characters when enabled. `notificationsEnabled: false`
allows a null token and clears registration; DELETE has the same clearing effect without ending login.

The FCM token is sensitive, encrypted at rest with the existing per-user DEVICE_FCM_TOKEN cipher, and
never returned after registration. Registration is bound to a session; token replacement, session/device
revocation, account deactivation/deletion and expiry invalidate delivery eligibility. Reusing the same
FCM token/installation on another account removes the previous binding. See
[`android-notifications.md`](../frontend/docs/android-notifications.md) for setup and delivery guarantees.

### 9.4 Account deletion readiness

`GET /me/deletion-readiness`

```json
{
  "ready": false,
  "ownedRooms": [
    {
      "roomId": "019b1f33-e664-7ef4-985e-76b3ac298620",
      "title": "Goa trip"
    }
  ],
  "joinedRoomCount": 2
}
```

### 9.5 Request account deletion

`POST /me/deletion-requests`

Headers: access token and `Idempotency-Key`.

```json
{
  "recentFirebaseIdToken": "<recently-issued-token>"
}
```

Success: `202 Accepted`.

```json
{
  "status": "DELETION_PENDING",
  "irreversible": true,
  "purgeBy": "2026-09-10T10:30:00Z"
}
```

Errors:

- Owned rooms remain: `409 OWNERSHIP_TRANSFER_REQUIRED`.
- Joined rooms remain: `409 ROOMS_MUST_BE_LEFT`.
- Recent authentication invalid: `401 RECENT_AUTHENTICATION_REQUIRED`.

Implementation and retry rules:

- Readiness includes all owned rooms and joined memberships, including archived rooms. The deletion transaction locks the account before rechecking these blockers, matching join/ownership-transfer account-first ordering. No implicit room deletion or membership changes are performed.
- The client reauthenticates the current Firebase user and sends only `recentFirebaseIdToken`; unknown ownership/UID fields are rejected. The backend requires verified `auth_time` within five minutes (inclusive), no future authentication time, and the exact provider-subject HMAC mapped to the JWT owner. Refreshing a token alone does not prove recent login. Invalid/stale/mismatched proof returns `401 RECENT_AUTHENTICATION_REQUIRED`; verification outage returns a safe dependency failure.
- Flutter supplies a fresh UUID `Idempotency-Key`. Irreversibility/idempotence is enforced by account status and a single durable job per user, without retaining authentication proof in a response replay. Once accepted, subsequent authenticated calls return the existing `ACCOUNT_DELETION_PENDING`/`ACCOUNT_DELETED` denial; the client treats those during deletion as already accepted and clears local state. Acceptance always returns the pending envelope even when immediate cleanup has completed.
- Acceptance atomically sets `deletion_pending`, revokes all sessions/refresh credentials and invalidates all device/push registrations before any external deletion. There is no recovery/cancel endpoint. Failure before this commit leaves the account unchanged; failures afterward cannot roll back acceptance.
- A verified Firebase UID is encrypted with the existing external per-user key and distinct deletion-job cipher context. The backend deletes the Firebase user, checkpoints success durably, destroys the per-user key and purges personal rows. This is not deletion of the underlying Google account. A crash after key destruction resumes from the checkpoint without trying to decrypt the destroyed UID.
- `ACCOUNT_DELETION_ENABLED` defaults false; readiness/request fail with `503 ACCOUNT_DELETION_UNAVAILABLE` before acceptance until enabled. Configure the protected scheduler and Firebase permissions first. This flag enables the shared OIDC worker independently of the Android push flag.
- The existing protected `POST /internal/jobs/tick` additionally claims at most 20 due deletion jobs with five-minute leases. Failures retry after one minute without exposing provider exceptions or identifiers. Operators must schedule this tick and monitor/remediate overdue jobs to meet `purgeBy`; external outages remain disabled pending jobs, never false completion. Its existing reminder-counter response is unchanged.
- Successful cleanup removes device/session/refresh data, identity/profile ciphertext, personal reminder preferences and private replay snapshots. Only a minimal non-identifying account tombstone remains for seven-day backup restore safety and shared-room foreign keys. The destroyed-key journal remains outside database backups. This implementation has no profile-media, moderation or analytics tables requiring additional external cleanup.
- Flutter clears auth tokens, SDK sign-in state, FCM registration state, widget content/selections, private account/room/image caches and pending invites after acceptance or explicit logout. Opaque session IDs are used only for API revocation, never displayed. Pre-acceptance deletion failures retain the current session and allow retry.

Setup, IAM permissions and operational monitoring are documented in [Android Profile and Settings](../frontend/docs/profile-settings.md).

### 9.6 Policy status and acceptance

`GET /public/policies` is a public no-store read returning `required` (Terms, Privacy and content-rules versions), `documents` (relative public document paths) and `documentsAvailable`. `GET /public/legal/{kind}/{version}` returns `{title, version, content, published}` for bundled `terms` or `privacy` documents. Draft documents are readable but cannot authorize acceptance. Unsupported kinds/versions return `404 LEGAL_DOCUMENT_NOT_FOUND`.

`GET /me/policy-status` requires a live verified JWT session and returns:

```json
{
  "required": {"termsVersion":"2026-10-22","privacyVersion":"2026-10-22","contentRulesVersion":"2026-10-22"},
  "documents": {"termsUrl":"/public/legal/terms/2026-10-22","privacyUrl":"/public/legal/privacy/2026-10-22"},
  "documentsAvailable": true,
  "accepted": null,
  "currentAccepted": false
}
```

After acceptance, `accepted` contains `versions`, `adultAffirmed:true` and the first server-generated `acceptedAt`. It may contain an older acceptance while `currentAccepted:false`; only an exact current version tuple satisfies the guard. No raw identity/session/security fields are returned. All policy responses are `Cache-Control: private, no-store`.

`POST /me/policy-acceptances` requires an `Idempotency-Key` (1–128 characters) and an explicit allowlisted body:

```json
{
  "termsVersion": "2026-10-22",
  "privacyVersion": "2026-10-22",
  "contentRulesVersion": "2026-10-22",
  "accepted": true,
  "privacyAccepted": true,
  "adultAffirmed": true
}
```

Success is `200` with the status shape above. Terms includes the content rules. Ownership comes only from the verified JWT; `userId`, `acceptedAt` and all unknown body fields are rejected. The backend supplies Clock timestamps and locks the account before insertion. The unique user/version tuple makes repeated/concurrent acceptance return the same original timestamp; it does not infer consent from sign-in or a new idempotency key.

False/missing confirmations return `422 EXPLICIT_CONSENT_REQUIRED`; blank/missing versions return validation failure. Stale/unsupported version combinations return `409 POLICY_VERSION_CHANGED`, requiring fresh review. Unpublished/missing legal documents return `503 LEGAL_DOCUMENTS_UNAVAILABLE`. The generated initial versions are `2026-10-22`; `LEGAL_PUBLICATION_READY` defaults false, and enabling publication requires real operator/contact details and matching bundled documents.

Create and join (including internal add-member and previously successful join replays) return `403 CURRENT_POLICY_ACCEPTANCE_REQUIRED` until current acceptance exists. Authentication, public invite preview, legal reads, sign-out, account deletion and existing-room/lifecycle operations remain available. A future upload implementation must use the same guard. Historical acceptances are removed during account deletion under existing retention rules.

See [Android onboarding/consent setup](../frontend/docs/onboarding-consent.md) for publication, draft review, required-version rollover and router behavior.

### 9.7 Public web deletion entry

The public Cloudflare Pages flow uses `POST /public/account-deletion/readiness` and `POST /public/account-deletion-requests`. Each request carries a freshly issued Firebase Google/Apple identity token in the JSON body and is `Cache-Control: no-store`; it does not require or return a general Hyped! access/refresh token.

The backend verifies provider proof, applies exact-origin CORS for the production Hyped! domain, rate-limits the identity/IP combination, and invokes the same readiness/deletion application service as the in-app endpoints. Readiness returns only blocker counts and safe next steps. The request endpoint returns the same `202`, `409`, and recent-proof semantics as Section 9.5.

### 9.8 Account blocking

`PUT /me/blocks/{userId}` is idempotent and requires an `Idempotency-Key`. Its transaction creates the directional block, removes the target from blocker-owned rooms, makes the blocker leave target-owned rooms using safe ownership rules, cancels affected reminders, and emits access-invalidation work. Success returns counts only, never hidden room/account relationships.

Future joins are denied with the normal safe unavailable response when either account owns the target room. In a third-party-owned shared room, member projections substitute `Blocked account` without a photo and direct role operations between the pair return `403 ACTION_UNAVAILABLE`.

`DELETE /me/blocks/{userId}` removes the directional block after confirmation. It does not recreate memberships, restore roles, or notify the other account. `GET /me/blocks` lists only accounts blocked by the current user so they can manage their own choices; the reverse relationship is never disclosed.

## 10. Room resource

### 10.1 Room endpoints

| Method | Path | Authorization | Purpose |
|---|---|---|---|
| `GET` | `/rooms` | Signed-in user | List user's rooms |
| `POST` | `/rooms` | Signed-in user | Create room |
| `GET` | `/rooms/{roomId}` | Current member | Get room snapshot |
| `PATCH` | `/rooms/{roomId}` | Creator or co-host | Edit room/theme |
| `DELETE` | `/rooms/{roomId}` | Owner | Archive room for delayed deletion |
| `POST` | `/rooms/{roomId}/ownership-transfer` | Owner | Transfer ownership |
| `POST` | `/rooms/{roomId}/leave` | Member or co-host | Leave active or archived room |
| `POST` | `/rooms/{roomId}/restore` | Owner | Restore before recovery expiry |
| `DELETE` | `/rooms/{roomId}/permanent` | Owner | Permanently delete eligible archive |

### 10.2 Room summary

```json
{
  "id": "019b1f33-e664-7ef4-985e-76b3ac298620",
  "title": "Goa trip",
  "eventAt": "2026-12-20T04:30:00Z",
  "eventTimeZone": "Asia/Kolkata",
  "status": "ACTIVE",
  "role": "MEMBER",
  "memberCount": 8,
  "revision": 4,
  "updatedAt": "2026-09-09T10:30:00Z"
}
```

### 10.3 List rooms

`GET /rooms?status=ACTIVE&limit=20`

Allowed status filters: `ACTIVE`, `ARCHIVED`, or omitted for both. Archived rooms remain readable to current participants until physical cleanup; restoration is strictly limited to the 24-hour window. Omitted status orders active rooms first (event time ascending), then recent archives (archive time descending), so archived history cannot displace active rooms from a bounded list.
The initial room foundation returns at most 50 records. Cursor pagination is introduced with the broader room lifecycle.

### 10.4 Create room

`POST /rooms`

Headers: `Idempotency-Key`.

```json
{
  "title": "Goa trip",
  "eventLocalDate": "2026-12-20",
  "eventLocalTime": "10:00",
  "eventTimeZone": "Asia/Kolkata",
  "location": "North Goa",
  "description": "Our first group trip"
}
```

Success: `201 Created`, `Location: /api/v1/rooms/{roomId}`, and `ETag: "room-1"`.

The room-foundation response includes:

- Full authorized room snapshot
- Owner membership role

Invitation credentials and reminder preferences are added by their respective modules; room creation never returns
placeholder or unprotected invitation material when those modules are unavailable.

### 10.5 Event validation

- Title: required, trimmed, 1 to 80 Unicode characters.
- Date: valid calendar date.
- Time: valid local time.
- Time zone: supported IANA identifier.
- Resolved `eventAt`: strictly in the future when the transaction validates it.
- Ambiguous daylight-saving local time: `422 EVENT_TIME_AMBIGUOUS` with valid offset choices.
- Nonexistent daylight-saving local time: `422 EVENT_TIME_NONEXISTENT`.
- Location: optional, at most 120 characters.
- Description: optional, at most 500 characters.
- Recurrence fields: rejected as unknown/unsupported.
- User at ten active owned rooms: `409 OWNED_ROOM_LIMIT_REACHED`.

### 10.6 Theme input variants (deferred)

Theme persistence is outside the room-and-membership foundation. Until the theme module is implemented, room create and
update requests reject theme fields as unsupported rather than silently discarding them.

Preset:

```json
{
  "kind": "PRESET",
  "presetKey": "soft-blue-01",
  "overlayKey": "dark-soft"
}
```

Gradient:

```json
{
  "kind": "GRADIENT",
  "presetKey": "blue-lilac-02",
  "overlayKey": "dark-soft"
}
```

Uploaded image:

```json
{
  "kind": "UPLOADED_IMAGE",
  "mediaAssetId": "019b1f2b-d5e2-7d46-9165-a37ed59d6080",
  "overlayKey": "image-dark-40"
}
```

GIPHY:

```json
{
  "kind": "GIPHY",
  "providerAssetId": "3o7aD2saalBwwftBIY",
  "overlayKey": "gif-dark-45"
}
```

Only server-approved preset and overlay keys are accepted. A GIPHY asset ID is at most 255 characters and contains only provider-supported identifier characters. GIPHY media URLs are not submitted or stored.

### 10.7 Get room detail

`GET /rooms/{roomId}` returns `ETag: "room-{revision}"` and:

- Room fields
- Current user's role
- Total member count
- Archive/delete timing where applicable

The API does not send a ticking countdown value. It sends `eventAt` and `serverNow`, allowing the client to detect unreasonable device-clock drift while rendering locally.

### 10.8 Edit room

`PATCH /rooms/{roomId}` with `Content-Type: application/merge-patch+json` and `If-Match: "room-4"`.

```json
{
  "eventLocalDate": "2026-12-21",
  "eventLocalTime": "11:00",
  "eventTimeZone": "Asia/Kolkata",
  "location": "South Goa"
}
```

- Omitted fields remain unchanged.
- `null` clears only optional `location` or `description`.
- Title, date, time, and time zone cannot be cleared.
- A theme object replaces the whole current theme after validation.
- `If-Match` mismatch returns `412 ROOM_REVISION_MISMATCH` with `currentRevision`, but does not return private fields unless caller remains authorized.
- Success increments revision once, even if several fields change.
- Important field changes create member notification work.

### 10.9 Archive room and delayed deletion

`DELETE /rooms/{roomId}` returns `202 Accepted`.

```json
{
  "roomId": "019b1f33-e664-7ef4-985e-76b3ac298620",
  "status": "ARCHIVED",
  "archivedAt": "2026-09-09T10:30:00Z",
  "deleteAfter": "2026-09-10T10:30:00Z",
  "revision": 5,
  "serverNow": "2026-09-09T10:30:00Z"
}
```

Only the current owner can archive. The response retains its existing fields and adds `revision`, `serverNow`, the strong room ETag and `Cache-Control: private, no-store`. The first archive increments revision, cancels pending/claimed reminder jobs and pending/sending device deliveries in the same transaction. Retries preserve the original archive instant, deadline and revision even if an older ETag is supplied; they cannot extend recovery. Event edits, membership/role changes and invitation joins/rotation are blocked while archived. Participants may still leave; the owner may restore within the recovery window or permanently delete after eligibility. Archived snapshots remain readable until physical cleanup, and existing invitation credentials remain unusable until an authorized restoration.

### 10.10 Transfer ownership

`POST /rooms/{roomId}/ownership-transfer`

```json
{
  "newOwnerUserId": "019b1f40-84fc-70da-848e-e67fe8d32f50"
}
```

The target must be a current member or co-host. Success returns the updated room and both affected memberships. The previous owner becomes a co-host by default.

### 10.11 Leave, restore, and permanent deletion

| Method | Path | Authorization | Success |
|---|---|---|---|
| `POST` | `/rooms/{roomId}/leave` | Current MEMBER or CO_HOST | `204 No Content` |
| `POST` | `/rooms/{roomId}/restore` | Current OWNER | `200 OK`, full authorized room snapshot and ETag |
| `DELETE` | `/rooms/{roomId}/permanent` | Current OWNER, eligible archived room | `204 No Content` |

All identity/authorization comes from the verified JWT subject. These actions have no client ownership/user ID field. Lifecycle mutations accept a strong room `If-Match` and Flutter always supplies it; omission remains compatible with the original archive contract. A supplied malformed ETag returns `428 ROOM_REVISION_REQUIRED`; stale state returns `412 ROOM_REVISION_MISMATCH`. Role/ownership checks remain authoritative after waiting for row locks.

- MEMBER and CO_HOST may leave active or archived rooms. OWNER receives `409 OWNERSHIP_TRANSFER_REQUIRED` and must transfer first using the existing ownership-transfer contract (previous owner becomes CO_HOST). An archived owner must restore before transferring. Leave removes only the caller's membership/preferences, cancels their pending/sending deliveries, decrements member count and increments revision once.
- A repeated leave, a leave of a missing room, or a nonmember's leave returns `204` without disclosing relationships. An absent membership ignores an old revision because the desired state already holds.
- Recovery is allowed only while server UTC `now < deleteAfter`. At exact equality or later, restore returns `409 ROOM_RECOVERY_EXPIRED`. Owner restore of an already active room is a no-op `200`, including a retry with the pre-restore ETag. Successful restore clears both archive timestamps, increments revision and rebuilds the existing three reminder jobs. Past boundaries do not generate catch-up notifications; durable occurrence/device records prevent repeat delivery.
- Restoration rechecks the 10 active owned-room and 25 active joined-room limits for every affected participant. Sorted account locks precede the room lock. A membership-set race returns `409 ROOM_MEMBERS_CHANGED` for a reload; quota failures use the existing distinct room-limit codes and roll back the whole restore.
- Permanent deletion is allowed only for ARCHIVED rooms with `now >= deleteAfter`; active or early requests return `409 ROOM_DELETE_NOT_ELIGIBLE`. There is no immediate-delete bypass. Current nonowner participants receive `403 ROOM_ACTION_FORBIDDEN`.
- Repeated permanent deletion and nonmember requests return the same `204` no-op to avoid disclosing whether a foreign room exists. They never modify a room without current owner authorization. No tombstone containing room content or former membership is retained.
- The deletion transaction briefly marks the row `deleting`, clears encrypted room-linked join/rotation replays and physically deletes the room. Existing FKs cascade memberships, invitation credentials, reminder preferences/jobs/device deliveries and themes. The current implementation has preset/gradient themes and no room media objects requiring external deletion.
- The existing OIDC-protected `POST /internal/jobs/tick` also purges up to 100 eligible archives after processing reminder deliveries, using `FOR UPDATE SKIP LOCKED`. Repeated/concurrent owner and worker deletion is safe. The response's existing reminder counters are unchanged. Deployments must keep the documented protected scheduler enabled for automatic cleanup; eligibility and restore limits are enforced independently of worker timing.
- Successful Flutter leave/delete clears room/member caches and revokes every associated widget selection. Other devices clear protected fields after their next authoritative synchronization; offline widgets retain the last successful snapshot until then. Known removed selections are cleared and cannot be resurrected by a late response. Logout/account change continues to clear all protected widget data.

The Android Room actions page shows the current state and server-time-based eligibility, with action-specific confirmations, ownership-transfer guidance, reload on conflicts, and a removed state after leave/delete. Inaccessible and missing room IDs share a safe unavailable message. No recurring lifecycle or recovery extension is introduced.

## 11. Members and roles

| Method | Path | Authorization | Purpose |
|---|---|---|---|
| `GET` | `/rooms/{roomId}/members` | Current member | Safe member list, including archived rooms |
| `PATCH` | `/rooms/{roomId}/members/{userId}` | Owner | Promote/demote permitted user |
| `DELETE` | `/rooms/{roomId}/members/{userId}` | Owner; co-host for regular members | Remove member, `204 No Content` |

Role-change response:

```json
{
  "userId": "019b1f40-84fc-70da-848e-e67fe8d32f50",
  "role": "MEMBER",
  "joinedAt": "2026-09-09T10:30:00Z",
  "updatedAt": "2026-09-09T11:30:00Z"
}
```

Role update:

```json
{
  "role": "CO_HOST"
}
```

Rules:

- Only the owner can promote or demote co-hosts.
- All current participants can read the member list. Outsiders receive `404 ROOM_UNAVAILABLE` for list and mutation attempts.
- Owners may remove members or co-hosts. Co-hosts may remove only regular members. Regular members cannot manage others.
- No caller may remove themselves or change/remove the owner through these endpoints. Ownership changes use the existing transfer endpoint, which demotes the previous owner to co-host.
- Archived rooms remain readable but reject member-management mutations with `409 ROOM_ARCHIVED`; self-leave uses the lifecycle endpoint.
- Removal is idempotent for an already absent target after caller authorization, returning `204` even with an older revision; it does not reveal whether the target belongs to another room. There is no permanent ban: valid invitations can permit re-entry.
- A successful role change, removal, or ownership transfer increments room revision once. A no-op role change does not increment it. These mutations accept the current strong room ETag in `If-Match`; Flutter always supplies it. Omission remains supported for compatibility with the existing role/transfer contract. A supplied malformed ETag returns `428 ROOM_REVISION_REQUIRED`; a stale ETag returns `412 ROOM_REVISION_MISMATCH`. Authorization precedes revision evaluation.
- Room and user locks serialize membership/ownership writes. Existing unique owner and deferred membership/count consistency constraints enforce exactly one owner and the 25-member limit at transaction commit. Transfers lock both users in stable ID order before the room and re-evaluate permissions after waiting.
- Removal transactionally deletes the membership and personal reminder preferences, cancels pending/sending device reminders, and updates member count/revision. Delivery eligibility is also rechecked before sending. A message already accepted by FCM cannot be recalled and uses generic notification text.
- Removed users lose REST access immediately. On the next successful foreground synchronization, Flutter clears protected widget snapshots on the safe unavailable response. Offline cached widgets remain subject to the existing documented offline behavior; there is no new background synchronization.

Member reads capture an authorized membership snapshot under a short room transaction,
decrypt approved profile fields outside any database transaction, then reacquire the
room lock and recheck caller access, membership, roles, status and revision. No room
locks are retained across local/cloud key resolution. A concurrent change returns
`412 ROOM_REVISION_MISMATCH` with no member list; reload to obtain the current view.
A caller removed during decryption receives `404 ROOM_UNAVAILABLE` instead. The
final authorized snapshot supplies both action visibility and the response ETag.

`GET /rooms/{roomId}/members` returns `Cache-Control: private, no-store` and `ETag: "room-{revision}"`:

```json
{
  "items": [{
    "userId": "019b1f40-84fc-70da-848e-e67fe8d32f50",
    "displayName": "Mira",
    "photo": null,
    "role": "MEMBER",
    "joinedAt": "2026-09-09T10:30:00Z",
    "isCurrentUser": true
  }],
  "callerRole": "MEMBER",
  "status": "ACTIVE",
  "revision": 4
}
```

Ordering is owner, co-hosts, then members, with case-insensitive display name and user ID tie-breaker. The bounded list has at most 25 entries. Only display names are projected from encrypted profiles; no email, provider identity, raw photo URL, storage key, device/session data, encryption reference, or private account response is returned. `photo` is currently null and Flutter renders initials; media delivery is outside this contract. Role-change responses retain the existing `userId`, `role`, `joinedAt`, `updatedAt` shape; ownership transfer retains its room and two membership responses.

## 12. Invitations and joining

### 12.1 Endpoints

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| `GET` | `/rooms/{roomId}/invitation` | Current member | Get active share link and room code |
| `POST` | `/rooms/{roomId}/invitation/rotate` | Creator | Rotate link and code atomically |
| `POST` | `/public/invitations/preview` | None | Safe preview by link token |
| `POST` | `/public/room-codes/preview` | None | Safe preview by room code |
| `POST` | `/invitations/join` | Signed-in user | Join using link token |
| `POST` | `/room-codes/join` | Signed-in user | Join using room code |

### 12.2 Get active invitation

`GET /rooms/{roomId}/invitation`

```json
{
  "inviteUrl": "https://<invite-domain>/invite/Vm9y...",
  "roomCode": "7K4M-P9QX",
  "generation": 2,
  "validUntil": "ROOM_END"
}
```

The response is `Cache-Control: no-store`. Raw credentials are decrypted only for an authorized current member and never logged.

### 12.3 Rotate invitation

`POST /rooms/{roomId}/invitation/rotate`

Headers: `Idempotency-Key`.

Success returns the new invitation object. Both previous credentials stop resolving as soon as the transaction commits.

### 12.4 Preview by link

`POST /public/invitations/preview`

```json
{
  "token": "<opaque-link-token>"
}
```

### 12.5 Preview by code

`POST /public/room-codes/preview`

```json
{
  "roomCode": "7K4M-P9QX"
}
```

Code canonicalization removes the hyphen and converts lowercase to uppercase. Valid characters exclude `0`, `O`, `1`, and `I`.

### 12.6 Safe preview response

```json
{
  "previewReference": "<short-lived-single-purpose-reference>",
  "expiresAt": "2026-09-09T10:40:00Z",
  "event": {
    "title": "Goa trip",
    "eventAt": "2026-12-20T04:30:00Z",
    "eventTimeZone": "Asia/Kolkata",
    "theme": {
      "kind": "PRESET",
      "presetKey": "soft-blue-01"
    }
  },
  "inviter": {
    "displayName": "Aarav"
  },
  "memberCount": 8,
  "requiresAuthentication": true
}
```

The preview excludes location, description, member list, profile photo by default, user IDs, room ID, roles, invitation generation, internal media keys, and management controls. A cover is returned only as a preview-safe representation or authorized 15-minute delivery URL. Public preview responses are `Cache-Control: no-store`.

### 12.7 Join

`POST /invitations/join` or `POST /room-codes/join`

```json
{
  "previewReference": "<short-lived-single-purpose-reference>"
}
```

Headers: access token and `Idempotency-Key`.

Success: `200 OK` if already a member, otherwise `201 Created`. Both return the authorized room snapshot.

Errors:

- Invalid or rotated credential: `410 INVITATION_INVALID`.
- Deleted/ended room: `410 ROOM_ENDED`.
- Room has 25 members: `409 ROOM_FULL`.
- User has joined 25 active non-owned rooms: `409 JOINED_ROOM_LIMIT_REACHED`.
- Preview reference expired: `410 PREVIEW_EXPIRED`; client repeats preview.

No creator approval state exists.

## 13. Reminder preferences

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/rooms/{roomId}/reminder-preferences` | Get personal reminder toggles |
| `PUT` | `/rooms/{roomId}/reminder-preferences` | Replace personal reminder toggles |

`PUT /rooms/{roomId}/reminder-preferences`

```json
{
  "remind24HoursBefore": true,
  "remind1HourBefore": true,
  "remindAtEventTime": false
}
```

The MVP supports only these three fixed times. Preferences apply only to the current user. If a selected reminder time is already past, no notification is scheduled for that occurrence.

## 14. Uploaded media

### 14.1 Constraints

- Client source selection: maximum 5 MB before processing.
- Final uploaded file: maximum 1.5 MB (`1,572,864` bytes).
- Maximum decoded width: 2048 pixels.
- Maximum decoded height: 2048 pixels.
- Accepted final MIME types: `image/jpeg`, `image/png`, `image/webp`.
- HEIC/HEIF is converted on device and is not accepted by R2 finalization.
- Animated uploads are rejected; GIF animation is available only through GIPHY.

### 14.2 Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/media/uploads` | Create upload authorization |
| `POST` | `/media/uploads/{mediaAssetId}/complete` | Validate/finalize uploaded object |
| `GET` | `/media/{mediaAssetId}` | Get authorized media metadata/delivery URL |
| `DELETE` | `/media/{mediaAssetId}` | Delete unused user-owned media |

### 14.3 Create upload authorization

`POST /media/uploads`

```json
{
  "scope": "ROOM_COVER",
  "fileName": "goa.webp",
  "contentType": "image/webp",
  "sizeBytes": 812441,
  "widthPx": 1600,
  "heightPx": 1200,
  "contentSha256": "<base64-sha256>"
}
```

Success `201`:

```json
{
  "mediaAssetId": "019b1f2b-d5e2-7d46-9165-a37ed59d6080",
  "upload": {
    "method": "PUT",
    "url": "<short-lived-r2-presigned-url>",
    "headers": {
      "Content-Type": "image/webp"
    },
    "expiresAt": "2026-09-09T10:40:00Z",
    "maximumBytes": 1572864
  }
}
```

- Presigned upload authorization expires within 10 minutes.
- It permits one object key and expected content type/size only.
- The backend never accepts raw image bytes on this route.

### 14.4 Finalize upload

`POST /media/uploads/{mediaAssetId}/complete`

```json
{
  "contentSha256": "<base64-sha256>"
}
```

The backend verifies R2 object metadata, safely decodes and re-encodes the image, removes metadata, and performs automated moderation before returning `READY`. Processing uses `202` with `VALIDATING`, `MODERATING`, or `MANUAL_REVIEW`; uncertain images remain hidden until manual approval.

Failure codes include:

- `MEDIA_TOO_LARGE`
- `MEDIA_DIMENSIONS_EXCEEDED`
- `MEDIA_TYPE_UNSUPPORTED`
- `MEDIA_ANIMATED_UPLOAD_UNSUPPORTED`
- `MEDIA_HASH_MISMATCH`
- `MEDIA_DECODE_FAILED`
- `UPLOAD_NOT_FOUND`
- `UPLOAD_EXPIRED`

A rejected upload cannot replace the current profile photo or room theme.

`GET /media/{mediaAssetId}` authorizes the caller and returns a private R2 signed GET URL valid for **15 minutes**. The URL is scoped to one object and is never logged or included in analytics.

## 15. GIPHY client integration contract

GIPHY search is not a Spring Boot endpoint.

- Flutter calls GIPHY directly using separate Android and iOS API keys.
- Search and trending requests use `rating=pg`.
- The UI conspicuously displays **Powered by GIPHY** where results appear.
- Search query length is limited to 50 characters.
- Small provider renditions are used in search results.
- The selected asset is represented to Hyped! only by `provider = GIPHY` and `providerAssetId`.
- GIPHY media URLs and files are not proxied, cached, rewritten, or copied to R2.
- Provider analytics/pingbacks required by the approved integration are sent directly according to GIPHY's rules.
- If GIPHY is unavailable or production access is not approved, preset themes and uploaded static images remain available.

The beta allowance is not assumed to be adequate for an unrestricted production launch. Production-key approval, pricing, and terms must be completed before enabling the feature publicly.

## 16. Reports

`POST /reports`

Headers: access token and `Idempotency-Key`.

```json
{
  "roomId": "019b1f33-e664-7ef4-985e-76b3ac298620",
  "subjectType": "THEME",
  "reasonCode": "INAPPROPRIATE_CONTENT",
  "details": "Optional report detail"
}
```

- Reporter must be a current member.
- `reasonCode` must be from an approved catalogue.
- Optional details are at most 500 characters.
- Success returns `201 Created` with report ID and status only.
- Reports are limited separately to five per account per day unless security operations adjust the threshold.
- Eligible records are pseudonymized after account deletion and deleted after 90 days.
- `subjectType` supports `ROOM`, `THEME`, and `ACCOUNT`; an account subject must be resolvable from the reporter's current room/member context.
- Reporting and blocking are independent. The client may offer both actions, but neither silently implies the other.

## 17. Privacy-safe analytics

`POST /analytics/events`

Accepts up to 20 allowlisted client events per request. The endpoint never accepts arbitrary event names or properties.

```json
{
  "events": [
    {
      "eventId": "019b1f65-415b-745f-8ea0-fda0cb96402b",
      "name": "WIDGET_SETUP_COMPLETED",
      "occurredAt": "2026-09-09T10:30:00Z",
      "properties": {
        "platform": "ANDROID",
        "widgetSize": "SMALL"
      }
    }
  ]
}
```

- Event IDs make batch retries idempotent.
- The server drops unknown properties rather than storing arbitrary JSON.
- Raw events expire after 30 days.
- Raw room IDs, invite tokens, search text, event titles, descriptions, media URLs, email, and device tokens are forbidden.

## 18. Rate limiting

### 18.1 General authenticated limits

| Category | Limit | Scope |
|---|---:|---|
| Read requests | 120/minute | Account plus installation |
| Mutation requests | 60/minute | Account plus installation |
| Upload authorizations | 10/hour | Account |
| Active devices | 5 | Account |

### 18.2 Sensitive limits

| Operation | Initial limit | Scope |
|---|---:|---|
| Room-code preview/attempt | 10/minute | Installation and IP risk bucket |
| Invite preview | 30/minute | Installation and IP risk bucket |
| Identity exchange | 10/15 minutes | Installation and IP risk bucket |
| Provider-link attempt | 5/hour | Account |
| Invitation rotation | 5/hour | Room and creator |
| Report creation | 5/day | Account |
| Account deletion request | 3/day | Account |

IP address is a risk signal, not the sole key, because users may share mobile carrier or institutional networks. Limits are initial values and may be tightened during an active abuse incident without changing normal API semantics.

`429` responses include `Retry-After` and code `RATE_LIMITED`; they do not reveal whether a private token, code, room, or account exists.

## 19. Authorization matrix

| Operation | Recipient | Member | Co-host | Creator |
|---|---:|---:|---:|---:|
| Safe invite preview | Yes | Yes | Yes | Yes |
| Join after confirmation | Yes | Already joined | Already joined | Already joined |
| View room/members | No | Yes | Yes | Yes |
| Share current credentials | No | Yes | Yes | Yes |
| Edit room/theme | No | No | Yes | Yes |
| Change personal reminders | No | Yes | Yes | Yes |
| Remove regular member | No | No | Yes | Yes |
| Promote/demote co-host | No | No | No | Yes |
| Remove co-host | No | No | No | Yes |
| Rotate invitation | No | No | No | Yes |
| Transfer ownership | No | No | No | Yes |
| Delete room | No | No | No | Yes |

Authorization is evaluated on every request using current database state.

## 20. FCM payload contract

FCM messages are invalidation/navigation hints, not authoritative room responses.

Data payload example:

```json
{
  "type": "ROOM_CHANGED",
  "roomId": "019b1f33-e664-7ef4-985e-76b3ac298620",
  "roomRevision": "5",
  "notificationId": "019b1f74-b080-743e-847a-657122357f07"
}
```

Implemented Android event reminder data (no FCM `notification` object):

```json
{
  "type": "EVENT_REMINDER",
  "roomId": "019b1f33-e664-7ef4-985e-76b3ac298620",
  "roomRevision": "5",
  "notificationId": "019b1f74-b080-743e-847a-657122357f07",
  "sessionId": "019b1f20-4152-7ce8-ae9e-b12b87fe8a31",
  "reminderKind": "day_before"
}
```

`reminderKind` is exactly `day_before`, `hour_before`, or `event_time`. Android checks the current
session and permission, durably suppresses duplicate IDs and presents only generic text. The original
ROOM_CHANGED example describes future invalidation work, not an implemented notification type.

Rules:

- Payloads contain no invite credential, description, member list, provider token, or media URL.
- On receipt, the installed client fetches the room through the authorized REST API.
- Notification presentation may include the event title where the member has enabled notifications, recognizing that lock-screen visibility is controlled by the user and operating system.
- Duplicate `notificationId` values are ignored for presentation where practical.
- A missing/delayed push is repaired by refresh on app open/resume.

## 21. Internal worker endpoints

Internal routes are outside `/api/v1` and are not reachable with mobile access tokens.

| Method | Path | Caller | Purpose |
|---|---|---|---|
| `POST` | `/internal/jobs/tick` | Cloud Scheduler OIDC identity | Claim due bounded work |
| `GET` | `/actuator/health/liveness` | Cloud Run/platform | Process liveness |
| `GET` | `/actuator/health/readiness` | Cloud Run/platform | Dependency readiness |

The implemented reminder tick validates Google-signed OIDC issuer, exact configured audience and verified
scheduler service-account email in a security chain separate from mobile JWTs. It processes bounded
batches and returns `{ "prepared": 0, "sent": 0, "failed": 0 }`, never tokens or personal content.
It is enabled only by `ANDROID_PUSH_ENABLED`; configuration requires `PUSH_SCHEDULER_AUDIENCE` and
`PUSH_SCHEDULER_EMAIL`. Cloud Scheduler invokes it every minute. Durable occurrence/device keys, claim
leases, bounded retries and stable notification IDs prevent duplicate work/presentation; provider transport
itself is not exactly-once. Membership, room state/event instant, preferences and session/device/account
eligibility are rechecked before send. No catch-up is generated for reminder times already past on room
creation or membership join. Setup and failure guarantees are documented in
[`android-notifications.md`](../frontend/docs/android-notifications.md).

## 22. Cache behaviour

| Response | Cache policy |
|---|---|
| Auth/session/profile | `private, no-store` |
| Room/member/invitation | `private, no-store` |
| Public invite preview | `no-store` |
| Preset theme catalogue | Short public cache with versioned ETag |
| Authorized R2 URL | Private, expires with signature |
| Error response | `no-store` unless explicitly safe |

Mobile local caching is governed by authorization and offline requirements, not HTTP shared-cache behaviour.

## 23. API versioning and compatibility

- Breaking changes require a new major path such as `/api/v2`.
- Adding optional response fields is non-breaking; clients ignore unknown fields.
- Adding a new enum value may affect clients and requires capability/version review.
- Fields are deprecated before removal and documented with a minimum supported app version.
- The backend may reject obsolete app versions with `426 APP_UPGRADE_REQUIRED` only when security or contract incompatibility makes continued use unsafe.
- Invite URLs remain stable even if the internal API version changes.

## 24. Endpoint catalogue

| Domain | Method | Endpoint |
|---|---|---|
| Auth | `POST` | `/auth/exchange` |
| Auth | `POST` | `/auth/refresh` |
| Auth | `POST` | `/auth/logout` |
| Auth | `GET` | `/auth/sessions` |
| Auth | `DELETE` | `/auth/sessions/{sessionId}` |
| Auth | `POST` | `/auth/link/prepare` |
| Auth | `POST` | `/auth/link/confirm` |
| Account | `GET` | `/me` |
| Account | `PATCH` | `/me` |
| Account | `GET` | `/me/deletion-readiness` |
| Account | `POST` | `/me/deletion-requests` |
| Account | `GET` | `/me/policy-status` |
| Account | `POST` | `/me/policy-acceptances` |
| Account | `GET` | `/me/blocks` |
| Account | `PUT` | `/me/blocks/{userId}` |
| Account | `DELETE` | `/me/blocks/{userId}` |
| Public deletion | `POST` | `/public/account-deletion/readiness` |
| Public deletion | `POST` | `/public/account-deletion-requests` |
| Device | `PUT` | `/devices/{installationId}` |
| Device | `DELETE` | `/devices/{installationId}` |
| Room | `GET` | `/rooms` |
| Room | `POST` | `/rooms` |
| Room | `GET` | `/rooms/{roomId}` |
| Room | `PATCH` | `/rooms/{roomId}` |
| Room | `DELETE` | `/rooms/{roomId}` |
| Room | `POST` | `/rooms/{roomId}/ownership-transfer` |
| Room | `POST` | `/rooms/{roomId}/leave` |
| Room | `POST` | `/rooms/{roomId}/restore` |
| Room | `DELETE` | `/rooms/{roomId}/permanent` |
| Member | `GET` | `/rooms/{roomId}/members` |
| Member | `PATCH` | `/rooms/{roomId}/members/{userId}` |
| Member | `DELETE` | `/rooms/{roomId}/members/{userId}` |
| Invitation | `GET` | `/rooms/{roomId}/invitation` |
| Invitation | `POST` | `/rooms/{roomId}/invitation/rotate` |
| Invitation | `POST` | `/public/invitations/preview` |
| Invitation | `POST` | `/public/room-codes/preview` |
| Invitation | `POST` | `/invitations/join` |
| Invitation | `POST` | `/room-codes/join` |
| Reminder | `GET` | `/rooms/{roomId}/reminder-preferences` |
| Reminder | `PUT` | `/rooms/{roomId}/reminder-preferences` |
| Media | `POST` | `/media/uploads` |
| Media | `POST` | `/media/uploads/{mediaAssetId}/complete` |
| Media | `GET` | `/media/{mediaAssetId}` |
| Media | `DELETE` | `/media/{mediaAssetId}` |
| Safety | `POST` | `/reports` |
| Analytics | `POST` | `/analytics/events` |

## 25. Contract acceptance checklist

- [ ] Every endpoint has a defined authentication and authorization rule.
- [ ] Access tokens last one hour and rotating refresh tokens last 30 days.
- [ ] An account cannot silently exceed five active devices.
- [ ] Google and Apple identities never auto-link by email.
- [ ] All errors use stable problem codes and correlation IDs.
- [ ] Private resources return enumeration-safe errors.
- [ ] Required mutations implement idempotency.
- [ ] Room edits require a current strong ETag.
- [ ] Event creation validates civil time and returns normalized UTC plus IANA zone.
- [ ] Room, ownership, member, and joined-room limits return distinct conflicts.
- [ ] Public invitation preview exposes only approved safe fields.
- [ ] Joining is explicit, immediate, and idempotent.
- [ ] Rotating an invitation invalidates link and code together.
- [ ] Members can retrieve current share credentials through a no-store response.
- [ ] Reminder API supports only the three approved toggles.
- [ ] Uploaded images use direct R2 transfer and server finalization.
- [ ] Final images are JPEG, PNG, or WebP, no larger than 1.5 MB or 2048 by 2048 pixels.
- [ ] GIPHY search and delivery occur directly from Flutter with required attribution.
- [ ] GIPHY URLs/files are not proxied, cached, rewritten, or copied.
- [ ] FCM payloads are invalidation hints and contain no sensitive credentials.
- [ ] Balanced rate limits return standards-aligned response headers.
- [ ] Raw analytics accepts only an allowlisted schema and expires after 30 days.
- [ ] Account deletion requires recent authentication and no remaining rooms.
- [ ] Internal worker routes reject mobile tokens.
- [ ] OpenAPI and contract tests are generated before implementation release.

## 26. References

- [GIPHY API documentation](https://developers.giphy.com/docs/api/)
- [Tenor API notice for new clients](https://developers.google.com/tenor/guides/quickstart)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
- [IANA Time Zone Database](https://www.iana.org/time-zones)

## Android request-integrity evidence

Authenticated writes use optional `X-Play-Integrity`, `X-Integrity-Id` (UUID), and
`X-Integrity-Time` (Unix milliseconds). The backend rollout mode determines when
evidence is required; default observe preserves existing clients. Protected-route
scope, exact requestHash canonicalization and Google verification policy are in
[production security](production-security.md). Public previews/reads, auth
bootstrap/refresh/revocation/logout and account-deletion cleanup do not require
integrity evidence. These exemptions do not bypass existing authorization.
Enforce returns sanitized `403 INTEGRITY_REQUIRED` for missing/invalid/replayed
evidence and `503 INTEGRITY_UNAVAILABLE` for unavailable verification. Distributed
authenticated-write limits can return `429 RATE_LIMITED` with `Retry-After: 60`.
Retry with fresh evidence and preserve the business Idempotency-Key/revision.
No userId/deviceId ownership field or new public endpoint is introduced.
