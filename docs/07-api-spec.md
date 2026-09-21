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
| `GET` | `/me/policy-status` | Get current Terms/rules/18+ acceptance state |
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
  "photo": {
    "mediaAssetId": "019b1f2b-d5e2-7d46-9165-a37ed59d6080",
    "url": "<short-lived-authorized-url>"
  },
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

The FCM token is sensitive, encrypted at rest, and never returned after registration.

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

### 9.6 Policy status and acceptance

`GET /me/policy-status` returns required and accepted Terms/content-rules versions plus whether the adult affirmation is complete. `POST /me/policy-acceptances` requires an `Idempotency-Key` and an explicit body:

```json
{
  "termsVersion": "2026-09-14",
  "contentRulesVersion": "2026-09-14",
  "accepted": true,
  "adultAffirmed": true
}
```

The server rejects missing, false, stale, or unsupported versions. Create, join, and upload return `403 CURRENT_POLICY_ACCEPTANCE_REQUIRED` until the current acceptance exists. Authentication and policy/legal-page access remain available so the user can make a choice or sign out.

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
| `DELETE` | `/rooms/{roomId}` | Creator | Delete room |
| `POST` | `/rooms/{roomId}/ownership-transfer` | Creator | Transfer ownership |
| `POST` | `/rooms/{roomId}/leave` | Member or co-host | Leave room |

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
  "theme": {
    "kind": "PRESET",
    "presetKey": "soft-blue-01",
    "overlayKey": "dark-soft"
  },
  "updatedAt": "2026-09-09T10:30:00Z"
}
```

### 10.3 List rooms

`GET /rooms?status=ACTIVE&limit=20&cursor=<opaque>`

Allowed status filters: `ACTIVE`, `ARCHIVED`, or omitted for both. Archived rooms remain available only during their 24-hour archive window.

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
  "description": "Our first group trip",
  "theme": {
    "kind": "PRESET",
    "presetKey": "soft-blue-01",
    "overlayKey": "dark-soft"
  }
}
```

Success: `201 Created`, `Location: /api/v1/rooms/{roomId}`, and `ETag: "room-1"`.

The response includes:

- Full authorized room snapshot
- Creator membership
- Active invitation link and formatted room code
- Default reminder preferences

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

### 10.6 Theme input variants

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
- Current theme reference
- Personal reminder settings
- Current user permissions
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

### 10.9 Delete room

`DELETE /rooms/{roomId}` returns `202 Accepted`.

```json
{
  "roomId": "019b1f33-e664-7ef4-985e-76b3ac298620",
  "status": "DELETING"
}
```

The room becomes inaccessible immediately. Database/media cleanup and member notifications complete asynchronously and idempotently.

### 10.10 Transfer ownership

`POST /rooms/{roomId}/ownership-transfer`

```json
{
  "newOwnerUserId": "019b1f40-84fc-70da-848e-e67fe8d32f50"
}
```

The target must be a current member or co-host. Success returns the updated room and both affected memberships. The previous creator becomes a co-host by default.

## 11. Members and roles

| Method | Path | Authorization | Purpose |
|---|---|---|---|
| `GET` | `/rooms/{roomId}/members` | Current member | List current members |
| `PATCH` | `/rooms/{roomId}/members/{userId}` | Creator | Promote/demote permitted user |
| `DELETE` | `/rooms/{roomId}/members/{userId}` | Creator/co-host within role rules | Remove member |

Member response:

```json
{
  "userId": "019b1f40-84fc-70da-848e-e67fe8d32f50",
  "displayName": "Mira",
  "photo": null,
  "role": "MEMBER",
  "joinedAt": "2026-09-09T10:30:00Z"
}
```

Role update:

```json
{
  "role": "CO_HOST"
}
```

Rules:

- Only creator can promote/demote co-hosts.
- Creator and co-host can remove regular members.
- Only creator can remove/demote a co-host.
- Creator cannot remove themselves through this route.
- Removed users may immediately rejoin with current valid credentials.
- Removal succeeds immediately on the server and emits invalidation/notification work.

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

The scheduler endpoint validates Google-signed OIDC audience and service-account identity. It returns counts and a correlation ID, never personal content.

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
