# Android Crashlytics symbols and mapping handoff

Current state: this repository uses Firebase Core/Auth and native FCM, but has
**no firebase_crashlytics Flutter dependency or Crashlytics Gradle plugin**.
Therefore there is no active Crashlytics upload task or verified crash pipeline.
This document confirms the official procedure; it does not claim SDK integration,
a readable live crash, or a symbol upload has happened. Do not add a new telemetry
SDK during release preparation without its own privacy-reviewed implementation.
Readable crash evidence is a closed-testing readiness gate, currently pending.

## Exact artifact pairing

Archive the AAB/APK SHA-256, source commit, app versionName/versionCode, Flutter and
Dart versions, Android Gradle/plugin versions, build flags, backend revision, and
the **same build's** symbol/mapping files in restricted storage. Keep them at least
for the lifetime of supported test/release builds and the crash-triage retention
policy; delete according to the approved retention process. Do not commit them,
post them to public issues, or regenerate mappings later from similar source.
The pre-release verifier's ignored output directory is local evidence, not a
backup or an uploaded crash dashboard.

The configured AAB build uses `--split-debug-info=<run>/dart-symbols`. If obfuscation
is enabled in a separately reviewed build configuration, retain that same output
as well. The initial preparation does not introduce Dart obfuscation. R8 Java/
Kotlin mappings, when minification runs, are at
`frontend/build/app/outputs/mapping/release/mapping.txt`; no mapping file is
expected when R8 minification is disabled. The verifier preserves an existing
mapping beside the AAB. Dart symbols, R8 mappings and native NDK symbols solve
different stack types; one cannot replace another.

## Future configured Crashlytics workflow (manual, not executed)

1. In a separate approved change, integrate the current supported Flutter
   Crashlytics package and Android Gradle plugin against the intended Firebase
   Android app, initialize it with privacy-safe handlers and explicit collection
   rules, and run Android-only `flutterfire configure` as required by that SDK.
   Review generated changes; keep provider configuration and credentials excluded.
   Do not copy old version numbers from documentation snippets without checking
   compatibility with this project's current Flutter/Gradle toolchain.
2. Preserve the exact candidate artifacts and generated symbols. Ensure
   `mappingFileUploadEnabled` is enabled when using the Crashlytics plugin and R8.
   A configured plugin normally uploads Java/Kotlin mapping as part of release
   build integration. Inspect its actual release task and successful result;
   merely having `mapping.txt` does not prove that Firebase received it.
3. For Flutter split-debug-info symbols, the official Firebase command is below.
   `FIREBASE_ANDROID_APP_ID` is the Firebase app ID, **not** `com.hyped.hyped` and
   not a service-account secret. Use protected CLI authentication/CI identity,
   never a token pasted into a command, committed file or issue.

   ```sh
   # Only after separate authorization and SDK/privacy setup:
   firebase crashlytics:symbols:upload --app="$FIREBASE_ANDROID_APP_ID" /ABSOLUTE/CANDIDATE/dart-symbols
   # After the Crashlytics Gradle plugin exists, inspect/run its actual task:
   cd frontend/android
   ./gradlew :app:uploadCrashlyticsMappingFileRelease -Pdart-defines="$CANDIDATE_DART_DEFINES"
   ```

   `CANDIDATE_DART_DEFINES` is the exact base64-encoded Gradle `dart-defines`
   value from the approved candidate build, including its HTTPS API URL. Keep
   the same excluded signing/domain environment; this project's release validator
   also runs for upload tasks. Never put credentials in Dart defines.

   These commands perform external uploads and are documentation only here.
   The named Gradle task is **absent today**. Some configured release builds can
   upload automatically; review/disable upload tasks for local dry verification
   before enabling the integration. The pre-release script currently executes no
   explicit upload command; revisit this guarantee if an uploading SDK/plugin is
   introduced. Play mapping upload is a different destination and is not proof
   of Crashlytics symbolication.
4. Native NDK/C++ crashes require Crashlytics NDK configuration and matching native
   symbols/unstripped libraries. If adopted later, enable and verify native-symbol
   upload separately; do not claim Kotlin mapping or Dart symbols cover Flutter
   engine/native frames automatically. Follow current Firebase NDK instructions.
5. After authorization, trigger a controlled crash only on a disposable internal
   test build/device, relaunch, and confirm readable Dart and Kotlin/Java frames,
   app/build matching and no missing-symbol warnings in the private dashboard.
   Verify privacy: no user identifiers, emails, room titles/content, invitation
   credentials, JWTs, refresh/FCM tokens, provider identifiers or raw session IDs
   in exception text, breadcrumbs, attachments or custom keys. Safe keys are the
   allowlist in `12-observability-runbook.md`. Do not add Analytics for breadcrumbs;
   automatic Analytics stays deactivated.

Official references checked 2026-10-24:
[Flutter symbol upload](https://firebase.google.com/docs/crashlytics/flutter/get-deobfuscated-reports),
[Android mapping deobfuscation](https://firebase.google.com/docs/crashlytics/android/get-deobfuscated-reports),
[Crashlytics setup](https://firebase.google.com/docs/crashlytics/get-started?platform=flutter),
[native symbols](https://firebase.google.com/docs/crashlytics/ndk/get-deobfuscated-reports).
