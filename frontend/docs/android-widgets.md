# Android countdown widgets

Hyped uses native `AppWidgetProvider` and `RemoteViews`, with separate small (2 × 2) and medium (4 × 2) layouts. Add either size using the Android launcher's widget picker. Tap an empty widget to open selection for that instance. In the app, open **Countdown widgets**, choose the installed widget from the dropdown, then choose its room. Tap **Hyped!** on a populated widget to reconfigure it. Selecting or clearing one widget does not change another.

Tapping the card opens its selected room in Flutter. Cold launches and taps received by an existing activity use the same validated destination bridge. The destination survives authentication initialization/sign-in in memory and is consumed once. Deleted widget IDs cannot navigate or be selected. There is no public deep-link intent filter for widget actions. Android IDs determine PendingIntent request codes; explicit components, action/data identity, and FLAG_IMMUTABLE separate each instance's room and settings intents. Providers are not exported. This follows Android's [widget guidance](https://developer.android.com/develop/ui/views/appwidgets) and [PendingIntent guidance](https://developer.android.com/reference/android/app/PendingIntent).

## Cache format and privacy

The private `hyped_countdown_widgets` SharedPreferences file contains only selected snapshots keyed by `widget.<appWidgetId>`:

```json
{
  "appWidgetId": 41,
  "roomId": "room-uuid",
  "title": "Trip",
  "eventAt": 1913430600000,
  "presetKey": "mint-sky-01",
  "syncedAt": 1893456000000,
  "state": "active"
}
```

Instants are UTC Unix epoch milliseconds. The last-sync instant comes from the successful room response’s serverNow, so selecting cached data cannot falsely advance it. Selection checks authenticated room detail before writing a new snapshot. The state is a bounded display enum: active, archived, unavailable, or removed. An absent record means empty. Themes are allowlisted bundled color presets; unknown themes fall back to soft blue. Both Flutter and native code reconstruct this payload rather than serializing private room responses. Tokens, user/account IDs, membership data, descriptions, locations, media/URLs, and event timezone identifiers are never persisted in this file. Only room IDs are retained for unavailable/removed rooms; title, event time and theme are scrubbed, while the last successful sync timestamp is preserved.

Snapshots are excluded from cloud backup and device transfer. The former global room catalog and selection are discarded during migration. Widget deletion removes its record; every update and selection prunes stale IDs. Restored widget IDs start empty rather than restoring another account's content.

Logout clears every snapshot before remote revocation. All credential deletion paths (including failed refresh and revoked sessions) also clear snapshots through WidgetTokenStore. Backend ACCOUNT_DELETED and ACCOUNT_DELETION_PENDING responses clear credentials/widgets and invalidate authentication. This repository currently has no account-deletion screen or endpoint to invoke; the cleanup is attached to the shared credential-deletion lifecycle and those backend responses. Account sign-in clears old content before requesting identity proof, and a restored valid session enables synchronization without dropping its offline cache. Foreground room state is recreated when authentication changes. Session epochs fence late list/detail/edit responses from an earlier account.

## Refresh and offline behavior

Successful room-list loads, detail loads and edits send safe snapshots to native code and update installed widgets. A list is active-only and may be limited, so a selected room absent from it is checked using authenticated room detail rather than assumed deleted. Duplicate selections share one detail check. Archived rooms show their archived state. Deleting rooms and confirmed missing/gone responses scrub protected content and show removed. Access-denied and enumeration-safe ROOM_UNAVAILABLE responses show unavailable. A failed widget bridge does not turn a successful room load/edit into a failure.

Offline, timeout and server errors retain the last successfully synchronized snapshot and its timestamp. Widgets perform no network requests. Existing authentication expiry behavior may clear credentials and widgets if the app cannot restore its session; cached display is not retained after local session invalidation.

**Countdown text changes only when widget updates occur. It is not continuously network-synchronized.** There are no timers, periodic network jobs, WorkManager tasks or push refreshes. Native updates also occur on launcher widget updates and device clock, timezone and locale changes. Event dates and last-sync times are formatted using the device timezone, including daylight saving time, independently of the event's original timezone. Event equality and past events show “Event started”; a future event less than one minute away shows “Less than 1m”.

## Verification

Flutter tests exercise payload allowlisting, independent selection, empty/stale hosts, session cleanup and race fencing, deleted-account responses, room refresh coordination, successful edits, missing/archived rooms, cached/offline behavior, countdown boundaries, and pending navigation. JVM tests exercise the native snapshot persistence implementation across a simulated process restart, multiple widgets, deletion/pruning, safe serialization, cache preservation, restricted states, countdown equality/boundaries, local-date rollover, and DST.

Run from `frontend`:

```sh
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

Run `./gradlew :app:testDebugUnitTest` from `frontend/android` for native JVM tests.

Launcher/device acceptance checks: add both sizes and select different rooms; tap each card and its Hyped! heading with the app cold and warm; repeat after sign-out/sign-in; edit/archive/remove a selected room and reload; disconnect networking and verify cached data; delete one widget and verify the other selection survives; change the device timezone. These launcher checks require an Android device/emulator and are separate from JVM/Flutter tests.
