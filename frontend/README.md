# Hyped! Flutter app

## Run locally

```bash
flutter pub get
flutter run --dart-define=HYPED_API_BASE_URL=http://10.0.2.2:8080/api/v1
```

Use an HTTPS local proxy URL for an iOS simulator; App Transport Security is
kept enabled. Staging and production builds must supply their HTTPS URL with
`HYPED_API_BASE_URL`.

## Firebase and provider setup

No credentials or generated Firebase configuration are committed.

1. Install FlutterFire CLI: `dart pub global activate flutterfire_cli`.
2. Create separate Firebase projects for development, staging, and production.
3. From `frontend/`, run `flutterfire configure` for the selected environment.
   Select Android package `com.hyped.hyped` and iOS bundle `com.hyped.hyped`.
4. Place the generated Android `google-services.json` in `android/app/` and
   iOS `GoogleService-Info.plist` in `ios/Runner/`. Keep environment-specific
   files outside Git and inject them in local or CI build setup.
5. Enable Google and Apple providers in Firebase Authentication.

### Android Google sign-in

1. Register local debug and release signing-certificate SHA-1 and SHA-256
   fingerprints in the matching Firebase Android app.
2. Download the refreshed `google-services.json`.
3. Configure the Google Services Gradle plugin as directed by FlutterFire.
4. Verify the application ID and OAuth client belong to the same environment.

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
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```
