# Room members on Android

Open a room and tap **Members**. All current participants can see the safe list of display names, initials and roles. Archived rooms show the list without management actions. The backend returns no email, raw photo URL or account/session details; the current photo projection is null.

Owners can make members co-hosts, demote co-hosts, remove members/co-hosts and transfer ownership. Co-hosts can remove regular members only, following the existing product permission matrix. Owner and self rows expose no management actions. Every change requires an action-specific confirmation; removal explains possible invitation re-entry, and transfer explains loss of owner permissions.

Flutter uses authenticated Dio with existing refresh handling, supplies the member snapshot's room ETag, disables actions during requests, and reloads permissions after every successful mutation. Conflicts/revision errors require a reload before further actions. Backend authorization remains authoritative. The room detail/list providers refresh after mutations, including ownership changes.

Removed users immediately lose backend access and reminder eligibility. On synchronization, forbidden/unavailable responses clear the member view and protected Android widget content through the existing widget coordinator. Room detail also discards its previously loaded room on access denial. Offline widgets retain their last synchronized data until an authoritative synchronization or session cleanup, as documented by the widget MVP. No background jobs or new notification payloads are introduced.

See [API contract](../../docs/07-api-spec.md#11-members-and-roles). No new migration is needed; existing membership constraints and durable reminder delivery records are reused.
