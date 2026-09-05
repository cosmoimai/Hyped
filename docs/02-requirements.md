# Hyped! MVP Requirements

**Status:** Draft for review  
**Last updated:** 2026-09-05  
**Related document:** [`01-problem-statement.md`](./01-problem-statement.md)

## 1. Purpose

This document defines the functional and non-functional requirements for the first usable version of Hyped!, a private shared-countdown application for Android and iOS.

The MVP is intended to validate one core behavior: one person creates a countdown, shares it with a group, and multiple people join and keep the same countdown visible in the app or on their home screens.

## 2. Confirmed product decisions

| Area | MVP decision |
|---|---|
| Client platforms | Android and iOS |
| Authentication | Google Sign-In and Sign in with Apple |
| Room visibility | Private and not publicly discoverable |
| Ways to join | HTTPS invite link or human-enterable room code |
| Editing permissions | Creator and creator-selected co-hosts |
| Room capacity | 25 people, including the creator |
| Per-user limits | Create 10 active rooms; join 25 additional active rooms |
| Event lifecycle | Celebrate at zero, archive, then delete after 24 hours |
| Reminders | 24 hours before, 1 hour before, and at event time; configurable per member |
| Primary business goal | Successful group adoption |
| Link technology | iOS Universal Links and Android App Links; Firebase Dynamic Links will not be used |

## 3. User roles and permissions

### 3.1 Roles

- **Invite recipient:** Has an invite link or room code but has not joined the room.
- **Member:** Can view the room and its synchronized countdown, use its widget, leave the room, and report inappropriate content.
- **Co-host:** A member promoted by the creator. Has the member permissions plus permission to edit the room's event details and visual style.
- **Creator:** Creates and owns the room. Has all co-host permissions plus permission to manage roles, rotate or disable invitations, and delete the room.

### 3.2 Permission matrix

| Action | Invite recipient | Member | Co-host | Creator |
|---|:---:|:---:|:---:|:---:|
| Preview permitted invite information | Yes | Yes | Yes | Yes |
| Join room | Yes | N/A | N/A | N/A |
| View countdown and members | No | Yes | Yes | Yes |
| Add room widget | No | Yes | Yes | Yes |
| Leave room | No | Yes | Yes | Yes, after transferring ownership or deleting the room |
| Edit event details and theme | No | No | Yes | Yes |
| Invite other people | No | Yes | Yes | Yes |
| Promote or remove co-hosts | No | No | No | Yes |
| Remove regular members | No | No | Yes | Yes |
| Regenerate or disable invite access | No | No | No | Yes |
| Delete room | No | No | No | Yes |

## 4. Functional requirements

### 4.1 Authentication and accounts

- **FR-AUTH-01:** A user shall be able to sign in with Google.
- **FR-AUTH-02:** A user shall be able to sign in with Apple on supported platforms.
- **FR-AUTH-03:** The system shall associate a stable internal user ID with each authenticated account and shall not use an email address as the primary identifier.
- **FR-AUTH-04:** The system shall restore a valid authenticated session when the application is reopened.
- **FR-AUTH-05:** A user shall be able to sign out. Signing out shall remove local authentication credentials and private room data from the device.
- **FR-AUTH-06:** A user shall be able to request account deletion from within the application.
- **FR-AUTH-07:** Account deletion shall require re-authentication or an equivalent recent-authentication check before destructive processing begins.
- **FR-AUTH-08:** If the same verified identity signs in again, the system shall restore access to rooms that remain associated with that identity.
- **FR-AUTH-09:** Authentication failures, cancellation, loss of connectivity, and unavailable providers shall produce clear and recoverable error states.
- **FR-AUTH-10:** The user profile shall use the provider display name and photo when available and an initials-based avatar when a photo is unavailable.
- **FR-AUTH-11:** A user shall be able to edit their Hyped! display name and profile photo without changing the provider account.

### 4.2 Countdown room creation

- **FR-ROOM-01:** An authenticated user shall be able to create a private countdown room.
- **FR-ROOM-02:** Room creation shall require an event title, future event date, event time, and IANA time zone.
- **FR-ROOM-03:** The creator may optionally add a location, short description, and supported theme.
- **FR-ROOM-04:** The system shall validate that the normalized event timestamp is in the future at the time of creation.
- **FR-ROOM-05:** The application shall show the selected local date, local time, time zone, and calculated instant for confirmation before creation.
- **FR-ROOM-06:** A successfully created room shall assign the creating user the creator role.
- **FR-ROOM-07:** A room shall receive a non-guessable internal identifier and a separate human-enterable room code.
- **FR-ROOM-08:** The newly created room shall not appear in public listings or searches.
- **FR-ROOM-09:** If creation fails, the application shall not show a room as successfully created and shall allow the user to retry safely.
- **FR-ROOM-10:** A user shall not own more than 10 active rooms; archived rooms shall not count toward this limit.
- **FR-ROOM-11:** The MVP shall support one-time events only and shall not expose recurrence controls.

### 4.3 Room viewing and countdown calculation

- **FR-COUNT-01:** All members shall see countdown values derived from the room's single stored event instant.
- **FR-COUNT-02:** The foreground application shall update the visible countdown at least once per second while the countdown screen is active.
- **FR-COUNT-03:** The countdown shall display days, hours, minutes, and seconds when supported by the current surface.
- **FR-COUNT-04:** The application shall display the event's configured local date, time, and time zone so members can understand the source event time.
- **FR-COUNT-05:** The calculation shall remain correct across device time zones and daylight-saving changes.
- **FR-COUNT-06:** When the event instant is reached, the room shall enter the completed state and display a celebration or completed-event message rather than negative time.
- **FR-COUNT-07:** The application shall identify unavailable, deleted, invalid, and access-denied rooms with distinct user-facing states.
- **FR-COUNT-08:** Members shall see the event converted to their device time zone while retaining access to the configured event time and IANA time-zone identifier.

### 4.4 Editing and role management

- **FR-EDIT-01:** The creator and co-hosts shall be able to edit the event title, location, description, future date, time, time zone, and supported theme.
- **FR-EDIT-02:** Ordinary members shall not be able to edit the fields listed in FR-EDIT-01.
- **FR-EDIT-03:** The system shall validate edits using the same timestamp and media rules used during room creation.
- **FR-EDIT-04:** A successful edit shall update the room revision and synchronize the new values to all members.
- **FR-EDIT-05:** Conflicting edits shall not silently overwrite a newer room revision. The later editor shall be prompted to reload or intentionally retry.
- **FR-ROLE-01:** Only the creator shall be able to promote a member to co-host or demote a co-host to member.
- **FR-ROLE-02:** The creator and co-hosts shall be able to remove regular members. Only the creator shall be able to demote or remove a co-host.
- **FR-ROLE-03:** Removing or demoting a user shall take effect on the server immediately and on connected clients without requiring a new login.
- **FR-ROLE-04:** The creator shall not be removable by a co-host or member.
- **FR-ROLE-05:** The creator shall be able to transfer ownership to an existing member or co-host after explicit confirmation.
- **FR-ROLE-06:** A removed user may rejoin with a currently valid invite link or room code.

### 4.5 Invitations and joining

- **FR-INV-01:** A room member shall be able to share an HTTPS invite link using the device share sheet.
- **FR-INV-02:** A room member shall be able to view and share the room code.
- **FR-INV-03:** When the application is installed, a valid supported invite link shall open the intended room join screen through an iOS Universal Link or Android App Link.
- **FR-INV-04:** An unauthenticated invite recipient shall be asked to sign in and shall then be returned to the intended room join flow.
- **FR-INV-05:** Before authentication or joining, the recipient shall see a safe preview containing the event title, cover image or theme, current countdown, and inviter identity. The full member list and private controls shall not be exposed.
- **FR-INV-06:** A valid invite shall show an authenticated recipient a **Join countdown** confirmation. After confirmation, the user shall join immediately without creator approval. Joining shall be idempotent.
- **FR-INV-07:** Submitting a valid room code shall show the same safe preview and require the same **Join countdown** confirmation before immediate joining.
- **FR-INV-08:** Invalid, disabled, expired, or deleted-room invitations shall not grant room membership and shall show an appropriate explanation.
- **FR-INV-09:** The creator shall be able to regenerate the invite link and room code together. Both previously issued values shall stop granting new access immediately.
- **FR-INV-10:** When the app is not installed, the invite URL shall redirect directly to the platform-appropriate app-store listing. Automatic invite restoration after installation is not required for the MVP.
- **FR-INV-11:** The system shall preserve the invite intent through sign-in during the same installed-app session.
- **FR-INV-12:** Invite links and room codes shall remain valid until the creator regenerates them or the room ends.

### 4.6 Room list and membership

- **FR-MEM-01:** An authenticated user shall see the active rooms they created or joined.
- **FR-MEM-02:** The room list shall show the event title, next meaningful countdown unit, role, and completed status.
- **FR-MEM-03:** A member or co-host shall be able to leave a room after confirmation.
- **FR-MEM-04:** Leaving a room shall remove its private data and widget eligibility from that user's devices.
- **FR-MEM-05:** A creator shall not be able to leave without first transferring ownership or deleting the room.
- **FR-MEM-06:** A room member shall be able to report a room or theme for review.
- **FR-MEM-07:** A room shall allow no more than 25 active memberships, including the creator.
- **FR-MEM-08:** A user shall not join more than 25 active rooms in addition to rooms they created; archived rooms shall not count toward this limit.

### 4.7 Synchronization and offline behavior

- **FR-SYNC-01:** Connected clients shall receive room changes without requiring manual recreation of the countdown.
- **FR-SYNC-02:** After reconnecting, a client shall fetch and apply the newest authorized room revision.
- **FR-SYNC-03:** Previously loaded room details may be displayed while offline with a clear offline or last-updated indicator.
- **FR-SYNC-04:** Offline countdown calculations shall continue from the last trusted event instant using the device clock.
- **FR-SYNC-05:** Room creation, joining, editing, leaving, role changes, invitation rotation, room deletion, and account deletion shall require connectivity.
- **FR-SYNC-06:** The client shall not reveal cached room data after the user has signed out, left the room, been removed, or lost authorization once that state is known locally.

### 4.8 Themes and media

- **FR-THEME-01:** A creator or co-host shall be able to select a supported preset colour or gradient.
- **FR-THEME-02:** A creator or co-host shall be able to search for a GIF through an approved provider or upload a supported static cover image. User-uploaded GIF files are not supported in the MVP.
- **FR-THEME-03:** The application shall validate media type, dimensions, and file size before upload.
- **FR-THEME-04:** Countdown text shall remain readable over every supported theme through contrast controls, overlays, or validated combinations.
- **FR-THEME-05:** If animated media cannot run on a home-screen widget, the widget shall use a safe static preview frame or theme color without changing the room's in-app theme.
- **FR-THEME-06:** A failed or rejected upload shall not replace the last valid theme.

### 4.9 Home-screen widgets

- **FR-WIDGET-01:** A signed-in member shall be able to add a Hyped! widget manually through the operating-system widget flow and select one joined room for that widget.
- **FR-WIDGET-02:** The widget shall show at least the event title and a countdown value appropriate to the remaining duration.
- **FR-WIDGET-03:** Tapping the widget shall open the associated room in the application.
- **FR-WIDGET-04:** The widget shall refresh within the limits and scheduling behavior allowed by the operating system.
- **FR-WIDGET-05:** The widget shall display its last refreshed countdown state when temporarily offline and refresh after data or scheduling becomes available.
- **FR-WIDGET-06:** Deleted rooms, lost membership, and signed-out sessions shall produce a safe unavailable state without exposing stale private details indefinitely.
- **FR-WIDGET-07:** Widget requirements shall not promise continuous per-second background updates because Android and iOS control background refresh frequency.

### 4.10 Room lifecycle

- **FR-LIFE-01:** At the event instant, the room shall show a celebration, enter the archived state, remain accessible for 24 hours, and then be permanently deleted with its associated room media, memberships, and invitations.
- **FR-LIFE-02:** The creator shall be able to delete a room after explicit destructive-action confirmation.
- **FR-LIFE-03:** Room deletion shall revoke member access, invalidate invitations, and move installed widgets to an unavailable state.
- **FR-LIFE-04:** Before account deletion, a user shall transfer ownership of every owned room and leave every joined room. The system shall prevent deletion until these conditions are met.
- **FR-LIFE-05:** Destructive operations shall be idempotent and shall not produce partially visible room states.
- **FR-LIFE-06:** Only the creator shall be able to delete an active room; deletion shall require confirmation and notify all current members.

### 4.11 Notifications

- **FR-NOTIFY-01:** When notification permission is available, each member shall receive default reminders 24 hours before, 1 hour before, and at the event instant.
- **FR-NOTIFY-02:** Each member shall be able to enable, disable, or change reminders for themselves without affecting other members.
- **FR-NOTIFY-03:** Members shall be notified when the event title, date, time, time zone, or location changes.
- **FR-NOTIFY-04:** Description and theme changes shall synchronize without a mandatory member notification.
- **FR-NOTIFY-05:** Denied operating-system notification permission shall not block room creation, joining, or viewing.

### 4.12 Analytics and growth measurement

- **FR-AN-01:** The product shall record privacy-safe events for room creation, invite sharing, invite opening, join preview, successful join, widget setup, room completion, room leaving, and later room creation by an invited member.
- **FR-AN-02:** Analytics shall distinguish invite-link joins from room-code joins.
- **FR-AN-03:** Analytics shall support calculating room activation rate, invite conversion rate, average joined members per activated room, time to first join, and widget adoption rate.
- **FR-AN-04:** Analytics shall not store invitation secrets, authentication tokens, uploaded media, or unnecessary personal content.

## 5. Non-functional requirements

### 5.1 Performance

- **NFR-PERF-01:** On a supported device and stable broadband or 4G connection, 95% of room-list and room-detail API requests shall complete within 2 seconds, excluding media transfer time.
- **NFR-PERF-02:** The app shall present a visible loading, cached, empty, or error state within 500 milliseconds of navigation rather than appearing frozen.
- **NFR-PERF-03:** A valid installed-app invite link shall reach the intended preview or sign-in handoff within 3 seconds for 95% of tests on a stable network.
- **NFR-PERF-04:** Countdown rendering shall not depend on a server request for each tick.

### 5.2 Availability and reliability

- **NFR-REL-01:** The MVP backend shall target 99.5% monthly availability, excluding announced maintenance.
- **NFR-REL-02:** Server-side room state shall be the source of truth for event details, membership, roles, and invitation validity.
- **NFR-REL-03:** Event instants shall be stored in UTC together with the selected IANA time-zone identifier.
- **NFR-REL-04:** Retried create, join, leave, delete, and role-change requests shall not create duplicate or contradictory state.
- **NFR-REL-05:** Recoverable failures shall not corrupt the last valid room revision or theme.

### 5.3 Security and privacy

- **NFR-SEC-01:** All client-server communication shall use HTTPS with current platform-supported TLS.
- **NFR-SEC-02:** The backend shall validate provider identity tokens and issue application-specific sessions.
- **NFR-SEC-03:** Authorization shall be enforced by the backend for every private room operation; hiding a control in the client is not sufficient.
- **NFR-SEC-04:** Invite tokens and room codes shall be generated using cryptographically secure randomness and shall be rate-limited against enumeration.
- **NFR-SEC-05:** Authentication tokens and sensitive local data shall use platform-provided secure storage.
- **NFR-SEC-06:** Uploaded media shall be validated, served as non-executable content, and isolated from authentication secrets.
- **NFR-SEC-07:** Logs and analytics shall not contain authentication tokens, full invite secrets, or unnecessary personal data.
- **NFR-SEC-08:** User data export and deletion behavior shall be documented before public release.
- **NFR-SEC-09:** Abuse-sensitive actions, including repeated join attempts, invite creation, reports, and media uploads, shall be rate-limited.

### 5.4 Accessibility and usability

- **NFR-UX-01:** Core flows shall support screen readers, scalable text, and accessible control labels.
- **NFR-UX-02:** Text and essential icons shall meet WCAG 2.2 AA contrast targets where applicable to mobile interfaces.
- **NFR-UX-03:** Meaning shall not be communicated through color alone.
- **NFR-UX-04:** Every loading, empty, offline, success, and error state shall provide a clear next action where recovery is possible.
- **NFR-UX-05:** A first-time authenticated user shall be able to create and share a valid countdown without needing product documentation.

### 5.5 Compatibility and maintainability

- **NFR-COMP-01:** The release shall define and test an explicit minimum Android API level and iOS version before implementation begins.
- **NFR-COMP-02:** Core room and countdown behavior shall be equivalent across Android and iOS even when widget rendering differs because of platform capabilities.
- **NFR-MAIN-01:** Environment-specific configuration and secrets shall not be hard-coded in the mobile application or committed to source control.
- **NFR-MAIN-02:** Database changes shall use versioned migrations and support rollback or forward recovery.
- **NFR-MAIN-03:** External identity, storage, analytics, and link-domain dependencies shall be documented in the HLD and deployment documents.

### 5.6 Observability

- **NFR-OBS-01:** The backend shall emit structured logs with request and correlation identifiers.
- **NFR-OBS-02:** Metrics shall cover API latency, error rate, authentication failures, invite conversion failures, synchronization failures, and media-upload failures.
- **NFR-OBS-03:** Health checks shall distinguish process health from dependency readiness.
- **NFR-OBS-04:** Alerts shall be configured for sustained availability, error-rate, and latency threshold violations before production launch.

## 6. MVP constraints

- The client will be built for Android and iOS, with Flutter as the current frontend direction.
- The backend platform is not selected by this document. Supabase, Firebase, or a custom service may be evaluated in the HLD against these requirements.
- A full browser-based countdown experience is outside scope. The web surface supports invite handling and store routing only.
- Background execution and widget refresh frequency are controlled by Android and iOS; the product cannot guarantee a continuously ticking seconds display on a home-screen widget.
- App installation and sign-in cannot be silently bypassed for private room membership.
- External GIFs or arbitrary remote URLs shall not be embedded directly without validation and safety controls.
- Payments, subscriptions, public discovery, chat, event booking, and complex planning features are outside the MVP.

## 7. Remaining design inputs

The product behaviour previously listed as provisional has now been confirmed and moved into the functional requirements. The following implementation inputs remain for HLD and deployment planning:

| ID | Open input | Why it matters |
|---|---|---|
| D-01 | Minimum supported Android API level and iOS version | Determines device coverage and available widget APIs. |
| D-02 | GIF search provider and its content-safety configuration | Determines API terms, moderation, attribution, and cost. |
| D-03 | Static image size, dimensions, formats, and retention limits | Determines upload UX, storage cost, and validation rules. |
| D-04 | Notification delivery service and retry policy | Determines reliability and platform integration. |
| D-05 | Initial load-test target beyond the known 25-person room limit | Provides a capacity baseline before real traffic is available. |

## 8. In-scope and out-of-scope summary

### In scope

- Google and Apple authentication
- Private countdown room creation and membership
- Creator, co-host, and member roles
- Invite links and room codes
- Cross-platform synchronized event details
- Basic colors and safe media themes
- Android and iOS home-screen widgets
- Offline viewing of previously loaded countdowns
- Essential moderation entry points, analytics, and operational safeguards
- Default and personal event reminders

### Out of scope

- Public rooms, profiles, feeds, or room search
- Chat, comments, reactions, polls, or collaborative planning
- Payments, subscriptions, or premium themes
- Ticketing, travel, calendar, or commerce integrations
- Full web participation or desktop applications
- Multiple simultaneous room owners
- Guaranteed second-by-second background widget updates
- Recurring events
- User-uploaded animated GIF files
- AI-generated content and unrestricted third-party media embedding

## 9. MVP acceptance criteria

| ID | Scenario | Acceptance criterion |
|---|---|---|
| AC-01 | Sign in | Given a new user, when they complete Google or Apple authentication, then an internal account is created and a valid app session begins. |
| AC-02 | Create room | Given an authenticated user and valid future event details, when they create a room, then the room appears in their list with them as creator. |
| AC-03 | Reject invalid date | Given an event instant in the past, when creation or editing is submitted, then the request is rejected with a corrective message. |
| AC-04 | Installed-app invitation | Given a valid invite link and the app installed, when the link is opened, then the app shows the intended room preview rather than its generic home screen. |
| AC-05 | Sign-in handoff | Given a signed-out invite recipient, when they authenticate successfully, then they return to the same room join flow. |
| AC-06 | Join once | Given a valid invitation and an authenticated user, when the user confirms **Join countdown** or the request is retried, then the user joins without creator approval and exactly one membership exists. |
| AC-07 | Room code | Given a valid room code, when an authenticated user submits it, then they reach the matching room preview and can join. |
| AC-08 | Invalid invitation | Given an invalidated invite link or room code, when it is used, then membership is not created and the user sees an invalid-invite state. |
| AC-09 | Co-host edit | Given a member promoted to co-host, when they submit a valid event change, then all connected authorized clients receive the new room revision. |
| AC-10 | Member edit denied | Given an ordinary member, when they attempt an edit through either the UI or direct API call, then the operation is denied without changing the room. |
| AC-11 | Conflict protection | Given two editors working from the same revision, when one saves after the other has already updated it, then the stale write is rejected or explicitly reconciled. |
| AC-12 | Time-zone consistency | Given members in different device time zones, when they view the room, then all countdowns reach zero at the same instant. |
| AC-13 | Offline countdown | Given a previously loaded room and no connectivity, when the app is opened, then the cached countdown continues with an offline indicator. |
| AC-14 | Widget setup | Given a joined room, when a user configures the widget, then it displays that room and opens the correct room when tapped. |
| AC-15 | Completed event | Given the event instant has passed, when the room or widget refreshes, then it shows a celebration or completed state, never displays negative time, archives the room, and deletes it after 24 hours. |
| AC-16 | Member removal | Given the creator or co-host removes a regular member, when authorization is next checked, then that user cannot retrieve room data and their widget becomes unavailable; a valid invitation permits rejoining. |
| AC-17 | Invite rotation | Given the creator rotates invitation access, when an old link or code is used, then it cannot create a new membership; the new values continue to work. |
| AC-18 | Delete room | Given creator confirmation, when a room is deleted, then member access is revoked, invites become invalid, and clients show the deleted-room state. |
| AC-19 | Sign out | Given a signed-in user, when they sign out, then private cached room content and credentials are removed from accessible app surfaces and widgets. |
| AC-20 | Analytics safety | Given all MVP funnel events, when analytics payloads are inspected, then no authentication token or full invite secret is present. |
| AC-21 | Room capacity | Given a room already has 25 people including its creator, when another user attempts to join, then no membership is created and the room-full state is shown. |
| AC-22 | User limits | Given a user owns 10 active rooms or has joined 25 additional active rooms, when they attempt the corresponding excess action, then it is rejected without partial state. |
| AC-23 | Reminders | Given notification permission and default settings, when the event approaches, then the member receives reminders at 24 hours, 1 hour, and event time. |
| AC-24 | Important edit | Given a creator or co-host changes the title, date, time, time zone, or location, when the update succeeds, then current members are notified. |
| AC-25 | Account deletion prerequisites | Given a user still owns or belongs to a room, when account deletion is requested, then deletion is blocked until ownership is transferred and joined rooms are left. |

## 10. Definition of MVP complete

The MVP is ready for release consideration when:

- All confirmed functional requirements are implemented or explicitly deferred through an approved change.
- AC-01 through AC-25 pass on the supported Android and iOS test matrix.
- No open critical security, privacy, data-loss, or authorization defect remains.
- Deep-link association files and production domains are verified on both platforms.
- Widget behavior and limitations are documented and tested on supported OS versions.
- Monitoring, rate limiting, account deletion, data retention, and support paths are operational.
- Analytics can measure the group-adoption funnel without exposing sensitive values.

## 11. Knowledge gaps for later design documents

The remaining questions in Section 7 do not change the approved user journeys. They will be resolved while preparing the UI/UX design, HLD, security design, and deployment plan.

Technology selections, data models, endpoint contracts, UI layouts, and test implementation details belong in the later HLD, database, API, UI/UX, LLD, and test-plan documents.
