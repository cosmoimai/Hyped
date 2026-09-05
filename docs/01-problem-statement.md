# Hyped! Problem Statement

**Status:** Draft for MVP  
**Last updated:** 2026-09-05

## 1. Product summary

Hyped! is a shared event-countdown app for Android and iOS. It lets one person create a countdown for a meaningful upcoming event, invite friends through a link or room code, and let the group follow the same countdown from the app and their home-screen widgets.

Examples include:

- Days until graduation
- Time until a concert or festival
- Days until a group trip
- Time until an exam or result
- Days until a birthday, wedding, or reunion

The product should make anticipation feel shared, visible, and easy to revisit without requiring repeated messages in a group chat.

## 2. Problem being solved

Friends often anticipate the same event together, but the event information and excitement are fragmented across WhatsApp, Discord, calendars, screenshots, and individual countdown apps.

Existing approaches create several problems:

- Each person may create and maintain a separate countdown.
- Event changes, such as a new date or time, do not automatically reach every participant.
- Calendar entries are useful for scheduling but do not create an engaging shared experience.
- Group-chat messages disappear quickly as new messages arrive.
- Many countdown tools are designed for one person, not a group.
- Joining a shared countdown often involves unnecessary setup or manual searching.

Hyped! solves this by giving the group one synchronized source of truth for an event countdown and a fast path from invitation to participation.

## 3. Target users

### Primary users

- Friend groups planning trips, concerts, festivals, graduations, birthdays, weddings, reunions, or similar events
- Students counting down to exams, results, holidays, or graduation
- Couples, families, clubs, and small communities anticipating a shared occasion

### User roles

- **Creator:** Creates and styles a countdown room, shares it, and manages its core event details.
- **Member:** Joins through an invite link or room code and follows the synchronized countdown.

Detailed permissions will be defined in `02-requirements.md` and the security design.

## 4. Business goal

The primary MVP goal is **group adoption**: a creator should be able to bring multiple people into the same countdown with minimal friction.

The product's initial growth loop is:

1. A creator makes a countdown for an event.
2. The creator shares an invite link or room code in an existing group chat.
3. Multiple friends join the same countdown.
4. Members place the countdown widget on their home screens.
5. Members later create and share countdowns for other events.

Initial success should be measured using signals such as:

- Percentage of created rooms that gain at least one member
- Average number of joined members per activated room
- Invite-link and room-code conversion rates
- Time from room creation to the first successful member join
- Percentage of joined members who add the countdown widget
- Percentage of members who later create another room

Exact targets will be defined after baseline usage data is available.

## 5. Current difficulties

Users currently rely on combinations of tools that solve only part of the problem:

| Current approach | Difficulty |
|---|---|
| Group chats | The event date and countdown become buried in later messages. |
| Calendar events | Good for reminders, but weak for visual anticipation and group identity. |
| Personal countdown apps | Each person maintains a separate copy that can become inconsistent. |
| Screenshots or social posts | Static, quickly outdated, and not synchronized. |
| Manual reminders | Depend on one person repeatedly updating the group. |

The central gap is not simply calculating time. It is making one live countdown easy for an entire group to join, personalize, and keep visible.

## 6. Expected outcome

For the MVP, users should be able to:

- Create a countdown room for a future event.
- Set the event name, date, time, and time zone.
- Apply simple visual styling, such as colors or a supported image/GIF background.
- Share the room using an invite link or human-enterable room code.
- Open an invitation and reach the relevant room with minimal interruption.
- Join the room on Android or iOS.
- See a synchronized countdown based on the same event timestamp.
- Add the shared countdown as a home-screen widget.
- Receive updated event details when the creator changes the event.
- Understand clear states for invalid invitations, expired or deleted rooms, past events, connectivity problems, and unsupported links.

The expected product outcome is that a new user can move from receiving an invitation to viewing the shared countdown quickly, without needing to search for the room or recreate the event manually.

## 7. In scope for the MVP

- Android and iOS mobile applications built around shared countdown rooms
- Account creation or sign-in sufficient to identify creators and members
- Countdown creation and basic management
- Shared membership through invite links and room codes
- Universal Links on iOS and Android App Links for supported HTTPS invitations
- A fallback web page or store-routing experience when the app is not installed
- Synchronized event details across members
- Home-screen countdown widgets on both supported platforms
- Basic visual customization with controlled colors and supported media
- Essential privacy, abuse prevention, error handling, and room lifecycle behaviour
- Basic analytics needed to measure the group-adoption funnel

## 8. Outside the MVP scope

- Public discovery or search for countdown rooms
- Open social-network features, public profiles, follower graphs, or feeds
- Direct messaging or a full group-chat replacement
- Ticket booking, travel booking, or event-commerce integrations
- Payment processing, subscriptions, or paid themes
- Complex event planning, task assignment, shared expenses, or itineraries
- Desktop applications
- Full-featured web participation equivalent to the mobile apps
- Arbitrary user-uploaded executable or interactive content
- Advanced AI-generated themes or event recommendations
- Enterprise administration and organization-wide access controls

These features may be reconsidered after the core group-invite loop is validated.

## 9. Product principles

- **Fast to join:** An invitation should take the user directly toward the intended countdown.
- **One shared truth:** Every member should see countdown data derived from the same stored event timestamp.
- **Private by default:** Rooms are not publicly discoverable; access is granted through an invitation or room code.
- **Visible without effort:** Widgets should make the countdown useful without requiring the app to be opened repeatedly.
- **Delight without clutter:** Customization should create group identity while keeping the countdown easy to read.
- **Cross-platform consistency:** Android and iOS users in the same group should receive equivalent core behaviour.

## 10. Key risks to validate

- Whether recipients install or open the app after receiving an invitation
- Whether room codes provide a useful fallback without weakening privacy
- Whether users consistently add and retain the home-screen widget
- Whether widget refresh limits on Android and iOS meet user expectations for countdown precision
- Whether GIF or media customization is practical within widget platform limitations
- Whether supporting both platforms in the MVP slows validation of the core sharing loop

These risks should become explicit assumptions, experiments, and acceptance criteria in the requirements and test-plan documents.

## 11. Confirmed decisions

| Decision | MVP choice |
|---|---|
| Supported platforms | Android and iOS |
| Room entry | Invite link or room code |
| Room visibility | Private and not publicly discoverable |
| Primary business outcome | Group adoption |
| Deprecated link technology | Firebase Dynamic Links will not be used |

## 12. Working assumptions for the next document

The following assumptions are intentionally not final requirements yet:

- A creator can update or delete a room.
- Members see creator changes automatically when connectivity is available.
- Invite links use standard HTTPS Universal Links and Android App Links.
- Authentication should introduce as little friction as practical while still protecting room membership.
- Countdown calculations use an absolute event timestamp plus an explicit time zone.
- Media customization will be constrained by platform widget capabilities and safety limits.

These assumptions must be confirmed or revised while preparing `02-requirements.md`.
