# Verification — 2026-10-17

## Executed

JDK 17, Gradle 8.13, Android SDK 35 / Build Tools 35.0.0.

Equivalent standard project command:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest
```

Result: **BUILD SUCCESSFUL**, 76 tasks, 47 executed / 29 up-to-date.

- JVM suite: **17 tests, 0 failures, 0 errors** (9 real-HTTP MockWebServer contract tests, 8 ViewModel tests).
- Debug APK: assembled successfully, minimum Android 8.0 / API 26.
- Device test APK: compiled and assembled successfully. Includes demo navigation, encrypted vault round-trip / plaintext exclusion / clearing, and initial-load retry UI checks.
- Android lint: **0 errors, 6 warnings**.
- Independent source review: two findings addressed (expiry/sign-out race and missing initial-load retry). The race regression failed before the correction and passed after it.

The source archive includes XML unit-test evidence under `docs/verification/`.

## Remaining validation

No live account credentials were supplied and no Android device or emulator was attached (`adb devices` returned an empty list). **Device tests have not been executed**. Neither live API acceptance nor visual layout on a device has been verified. Keystore behavior is covered by compiled device tests, not a completed device run.

Run on a disposable emulator or test device:

```sh
./gradlew connectedDebugAndroidTest
```

Then check a real account: memory-only sign-in, owned-address selection, refresh, airplane-mode failure/retry, remembered sign-in after force-stop, sign-out, and rejected/revoked-key behavior. Demo mode is available without credentials.

## Deferred minor findings

- Four dependency-update warnings (Compose BOM appears for main and device tests; ViewModel and lifecycle runtime each have a newer version). Versions are deliberately pinned to the toolchain verified here; these warnings are not vulnerability findings.
- No custom launcher icon yet; Android uses its generic application icon.
- Lint suggests the SharedPreferences KTX helper; explicit synchronous `commit()` remains intentional so storage failure can be reported.

Build notes: AGP reports deprecated Gradle features for a future Gradle 9 migration. One native Compose library was packaged without stripping debug symbols; this does not prevent the debug APK from building. No release signing or publishing was performed.

## User acceptance update — 2026-10-17

Cassie subsequently installed the APK on her Android phone and reported successful email/API-key sign-in and loading of both owned addresses. This establishes user-reported live authentication and account/address loading. The automated on-device tests and broader acceptance checklist above have not been run by the build environment. Screenshot blocking was also observed, matching the current FLAG_SECURE setting.
