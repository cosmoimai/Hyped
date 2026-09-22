# Hyped! Flutter app

## Run locally

```bash
flutter pub get
flutter run --dart-define=HYPED_API_BASE_URL=http://10.0.2.2:8080/api/v1
```

Use an HTTPS local proxy URL for an iOS simulator; App Transport Security is
kept enabled. Staging and production builds must supply their HTTPS URL with
`HYPED_API_BASE_URL`. Release startup rejects the development URL and every
non-HTTPS URL.

Release signing is intentionally absent. Create an untracked
`android/key.properties`, reference an externally stored upload keystore from a
local or CI Gradle configuration, and verify the release certificate matches
the selected Firebase project. The project has no debug-signing fallback for
release builds.

## Android Firebase and Google sign-in setup

No credentials or generated Firebase configuration are committed.

1. Create a Firebase project for the target environment. In **Project settings
   > Your apps**, add an Android app whose package name is exactly
   `com.hyped.hyped`.
2. Get the debug certificate fingerprints from `frontend/android/`:

   ```bash
   ./gradlew signingReport
   ```

   Add the debug variant's SHA-1 and SHA-256 values to the Firebase Android
   app. Add the controlled release certificate fingerprints separately for
   staging or production.
3. In **Authentication > Sign-in method**, enable the Google provider and pick
   the support email. Confirm the generated Android and web OAuth clients are
   in the same Google Cloud/Firebase project.
4. Download the refreshed `google-services.json` and place it at
   `frontend/android/app/google-services.json`. The file is ignored by Git.
   The Google Services Gradle plugin is applied only when this file exists, so
   an unconfigured checkout still builds and shows a configuration message if
   Google sign-in is selected.
5. Configure backend verification with Application Default Credentials for the
   same project. For local development, keep the downloaded service-account
   JSON outside this repository and run:

   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/absolute/private/path/firebase-admin.json
   export FIREBASE_IDENTITY_ENABLED=true
   export FIREBASE_PROJECT_ID=your-firebase-project-id
   cd ../backend && ./mvnw spring-boot:run
   ```

   In deployed environments, use the workload identity/service account of the
   runtime instead of a JSON key.
6. Start the Android emulator and backend, then run from `frontend/`:

   ```bash
   flutter run -d emulator-5554 \
     --dart-define=HYPED_API_BASE_URL=http://10.0.2.2:8080/api/v1
   ```

   Select **Continue with Google**, choose an account, confirm the app reaches
   Home, then sign out and confirm it returns to sign-in. For a physical device,
   use an HTTPS URL reachable by the phone, install a build signed by a
   certificate registered in Firebase, and repeat the same sign-in/sign-out
   check.

### iOS Google and Apple sign-in

1. Add the reversed Google client ID URL scheme from the selected
   `GoogleService-Info.plist` to the Runner target.
2. In Xcode, add the **Sign in with Apple** capability to Runner.
3. Add **Keychain Sharing** to Runner so `flutter_secure_storage` can use the
   iOS Keychain; use an environment-specific access group when configured.
4. In the Apple Developer portal, enable Sign in with Apple for the App ID and
   create the Service ID/key required by Firebase. Store the Apple private key
   only in Firebase/controlled secret storage.
5. Configure the Apple team ID, key ID, Service ID, and private key in Firebase.
6. Add the Firebase OAuth return URL to the Apple Service ID and verify the iOS
   bundle ID for each environment.

Tokens are held only in Keychain or Android encrypted secure storage. Never
enable Dio body/header logging for authentication calls.

Install full Xcode and a working CocoaPods toolchain before building iOS:
`sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer`, then
`sudo xcodebuild -runFirstLaunch` and `pod setup`.

## Verification

```bash
cd frontend
flutter pub get
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

A production build must include its HTTPS endpoint explicitly:

```bash
flutter build appbundle --release \
  --dart-define=HYPED_API_BASE_URL=https://api.example.com/api/v1
```
