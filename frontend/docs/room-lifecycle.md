# Android room lifecycle

Room details expose **Room actions** at `/rooms/{roomId}/lifecycle`. Active owners can archive; members/co-hosts can leave. Owners see ownership-transfer guidance and must transfer from Members before leaving. Archived owners must restore before transferring.

Archive preserves member-readable data while stopping joins, event edits and active reminder delivery. During the next 24 elapsed hours only the owner can restore. At exact server `deleteAfter`, restore disappears and permanent deletion becomes available. The page uses synchronized `serverNow` plus monotonic elapsed time, rather than trusting the phone's wall clock, and updates at the boundary. The backend rechecks eligibility and revision for every mutation. The owner may restore an ended event within the window, but existing invite rules still reject joining an event whose instant has passed. Editing a restored event follows the existing future-event validation; no recurrence is added.

The Home list includes active rooms and recent archives (bounded by the existing 50-item API limit), features only active countdowns and labels archives/recovery/deletion eligibility. Archived details hide edit/reminder actions and retain the member view. Missing and inaccessible rooms share a safe message; their protected widget fields clear after synchronization.

Every lifecycle action requires an impact-specific confirmation. Leave warns about loss of access, reminders and widget content. Archive explains recovery/deletion timing. Restore explains quota checks and future reminders. Permanent deletion explains irreversible loss for everyone. Revision/authorization/conflict errors disable actions until reload; permission visibility never replaces server authorization.

Successful leave/delete clears the room and invalidates room/member lists and detail controllers, marks selected widgets removed, and clears their room mappings. An old response cannot reattach a removed native selection; explicit selection is required. Archive/restore updates existing widgets to archived/active state. Offline failures preserve the last successful widget snapshot; logout/account change still clears all protected data.

The existing protected backend tick removes up to 100 eligible archives per invocation. Deployment must retain its scheduler/OIDC setup. Current database cascades remove all room relationships, invitation credentials, reminder jobs/deliveries, preferences and themes, and cleanup also deletes protected room replay snapshots. There are no room media objects in the current implementation. Database backup expiry remains governed by the database documentation.

See [API lifecycle contracts](../../docs/07-api-spec.md#1011-leave-restore-and-permanent-deletion) and [database cleanup](../../docs/06-database-design.md#14-room-deletion).
