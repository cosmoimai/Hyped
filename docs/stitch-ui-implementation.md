# Stitch UI implementation

The Flutter Android UI follows `stitch_hyped_ui_ux_specifications/anticipation_studio/DESIGN.md` and the supplied Home, room, create, sign-in and widget reference screens.

## Applied design

- Pale blue canvas, navy text, white surfaces, 20 dp hero corners, 16 dp compact cards, 12 dp filled inputs and pill buttons. Dark mode uses the existing dark navy palette.
- Manrope body text and Outfit headings/countdown numerals. Both font families are bundled from the Google Fonts repository under SIL Open Font License 1.1. Their full license texts ship as assets and are registered with Flutter's license registry. Rendering does not request fonts over the network.
- Home has a Join entry, Home/Create/Profile dock, featured countdown, compact secondary rooms and expandable archived rooms. Notifications, widgets and sign-out remain under the clearly labeled More options menu; Profile retains the existing settings actions.
- Room detail uses the room's actual supported theme with a navy scrim, live device-clock countdown, device-local event date/time, membership and personal-reminder navigation. Owner/co-host edit visibility and offline read-only rules remain enforced.
- Create/edit uses Details / Style / Review progress, labeled accessible theme options and a persistent Continue/Save action. Backend field limits and theme keys are unchanged.
- Notifications explain only the existing 24-hour, 1-hour and event-time reminders. Widget setup explains Android placement and independent room selection per widget.
- Back/Home navigation stays available on supporting screens, including load failures and direct launches.

## Deliberate adaptations

Reference images are visual specifications, not runtime assets. No remote stock photos, fictional avatars, member identities, progress percentages or "Live sync" claims are displayed. Decorative countdown rings are brand marks, not measured event progress. No Apple/iOS tab, unsupported reminders, audio, poster editing, or extra backend features were added.

The in-app countdown reads the device clock once per second while mounted and refreshes on resume. It stops at event time, cleans up its timer on disposal and makes no network requests. Native home-screen widget countdowns still update only when widgets update; they do not tick continuously.

At narrow widths or large text sizes, countdown cells wrap into two columns. Forms and sign-in remain scrollable, and room titles wrap. Semantics expose the full remaining duration without announcing every decorative numeral separately.

## Verification

Run Flutter formatting, analysis, tests and a debug APK build. On a physical device check Home, an active room, archived rooms, create/edit steps, notification permission, widget selection and all Back/Home controls in light/dark themes and with large text. Authentication, consent, invites, cached data and destructive confirmations continue through existing controllers.
