# Signed Android internal-testing build

This preparation builds locally only. It does not upload, publish, configure Play
App Signing, contact testers or approve public release.

## Candidate configuration

- Android package: `com.hyped.hyped`; display name: `Hyped!`.
- Initial version: `0.1.0+1`. Hyped is a new Play app; the owner's earlier published
  app is a different application. Confirm code 1 is still unused before upload.
- Dedicated RSA-3072 upload key with 10,000-day validity. It is not the debug key
  and does not change the other app's signing configuration.
- Staging HTTPS API and existing Firebase project `hyped-41a30` only.
- `HYPED_INTERNAL_TESTING=true` explicitly permits an empty invite host only with
  that exact API/Firebase pair. Normal production release validation still
  requires an owned invite domain. No HTTP or signing checks are bypassed.
- Verified App Links are disabled (the inactive manifest host is
  `unconfigured.invalid`); room codes remain available. Do not promote this
  staging-configured AAB to production.

## Rebuild locally

Signing files are outside the repository under
`~/.config/hyped/android-signing/`. The directory is owner-only; keystore and
`internal-testing.json` are owner-readable/writable only. The JSON supplies the
four existing signing configuration values (`HYPED_KEYSTORE_PATH`,
`HYPED_KEYSTORE_PASSWORD`, `HYPED_KEY_ALIAS`, `HYPED_KEY_PASSWORD`); do not print, commit or share it. Make an encrypted offline
backup of the keystore and recoverable passwords before distributing builds.
The PEM certificate is public and contains no private key.

```sh
python3 scripts/check-android-release.py
BUNDLETOOL_JAR=/absolute/path/to/official/bundletool.jar python3 scripts/build-android-internal.py
```

Use the official Google [bundletool release](https://github.com/google/bundletool/releases).
The builder validates the AAB with bundletool, verifies its JAR signature, matches
the signer against the upload key, checks the release manifest and records version,
source, SHA-256 and internal-only scope in ignored
`build/verification/internal-testing/receipt.json`. The artifact is named
`hyped-internal-<version>-<code>.aab` in the same directory. Logs remain excluded.
Flutter version comes from `frontend/pubspec.yaml`; increment the build number
before any subsequent Play upload, across all tracks.

## Manual steps before testers install from Play

1. Back up the upload key securely. Preserve this application's upload identity.
2. Create/select the **Hyped** Play app and verify `com.hyped.hyped`, not the owner's
   other app. Enable Play App Signing with a Google-managed app-signing key when
   prompted. Uploading/distribution requires a separate owner action.
3. In Play Console's app-integrity/app-signing page, copy the **app-signing**
   certificate's SHA-1 and SHA-256. Those differ from the local upload certificate.
4. Add the Play app-signing fingerprints to the matching Android app in Firebase
   project settings. Confirm Google sign-in is enabled. If Firebase changes the
   client configuration/OAuth client, download updated `google-services.json`
   locally and rebuild. Never commit it.
5. For direct sideload testing of a release APK signed with the upload key, also
   register the upload certificate fingerprints. An upload-signed APK cannot
   update the debug-signed installation; preserve data and use Play testing or a
   separate test device rather than uninstalling without a deliberate decision.
6. Complete Console-required declarations/access instructions and configure the
   internal track manually. Do not include credentials in release notes.
7. Test a real Play install: Google sign-in, consent, create/edit, room-code join,
   photo replacement, notification tap and logout. Internal-test distribution is
   not proof of production readiness or completion of the closed-testing plan.

Official references: [Android signing](https://developer.android.com/studio/publish/app-signing),
[Firebase Google sign-in](https://firebase.google.com/docs/auth/android/google-signin).
Public release remains blocked by the existing submission checklist, including
legal publication approval, deferred critical tests and backup/restore readiness.

## Local verification checkpoint — 2026-10-29

- Flutter format/analyze passed; 422 Flutter tests passed.
- 41 Android unit tests passed, zero failures/errors/skips.
- Release configuration matrix passed: 16 original checks plus four internal-mode
  checks (valid staging and rejection of foreign API, nonempty host and wrong
  Firebase project).
- Signed AAB passes bundletool validation, signer comparison and manifest checks.
  The exact artifact checksum is in the excluded `sha256.txt` and `receipt.json`.
- Staging readiness returned HTTP 200. Firebase project/package/web OAuth client
  match the staging build. Play-installed authentication still requires the Play
  app-signing registration and physical verification described above.
- No backend changes or deployment were made; backend tests were not rerun.
  No Play upload or change to the phone's debug installation was performed.
