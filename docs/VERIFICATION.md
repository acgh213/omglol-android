# Verification — 0.3.0

Final command: `./gradlew clean testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`.

Result: **BUILD SUCCESSFUL**, 77/77 tasks executed from a clean build.

- **48 JVM tests, zero failures/errors.** Includes 31 original account/Statuslog cases, authenticated feature rejection, JSON POST/body/auth contracts, one-shot status requests, session capabilities, draft/uncertain outcome state, demo posting and profile loading/races.
- Debug APK and device-test APK built successfully.
- Lint: **zero errors, 7 warnings**. Remaining warnings are dependency update notices and the existing SharedPreferences KTX suggestion; details in the XML report. Dependency refresh is deferred; synchronous commit reports storage failure.
- Independent source review identified that OkHttp can follow a 503 Retry-After:0 response despite disabling connection retries. A regression failed on a replayed POST; one-shot request bodies fixed it, and the full suite then passed. No remaining important review findings.
- The final APK signature verifies and its SHA-256 certificate matches the delivered 0.2 APK: d943bdb0aeab77117124dfe74d4d2807e5c337a2c8d8c7eb68b4813f8e8df82e.
- Package lol.omg.pocket; versionCode 3, versionName 0.3.0. Debug signing key is not committed.

Evidence: `docs/verification/0.3.0/`. Previous 0.2 notes and user acceptance: `docs/verification/0.2.0-NOTES.md`.

## Runtime limits

No Android device/emulator was attached (`adb devices` empty). Device tests are **compiled but unexecuted**. They include demo posting/receipt/history, draft discard and recreation, profile source viewing, navigation, screenshot policy and vault checks. Run `./gradlew connectedDebugAndroidTest` on a test device.

No real API credentials or live writes were used during development. HTTP tests use MockWebServer; demo posts are local. Live status creation, configured Mastodon cross-posting, profile retrieval and on-device 0.3 upgrade still need user acceptance. Earlier 0.2 account/history/screenshots were confirmed by Cassie on her phone.

An initial incremental Kotlin compile reported duplicate top-level symbols despite only one declaration per source file. A clean compile removed the stale output; final verification was a complete clean build. No cache workaround is shipped.

## Phone acceptance

Install 0.3 over 0.2. In Try demo, open Statuslog → New status, write text, rotate and post locally; verify the receipt/history, switch addresses, and inspect Profile. Check closing an unfinished draft asks before discarding. Demo posts reset when leaving the demo session.

For a deliberate real post, select the correct owned address, open New status, check the target and Mastodon choice (off by default), then tap Post. Verify the receipt and history. A timeout/server error can leave an unknown outcome: check latest statuses before explicitly allowing another attempt; no automatic re-submit occurs. Do not interpret a matching text entry as server-side idempotency proof.

Profile displays plain source/configuration without executing HTML; editing/publishing is not implemented. Drafts live only in process memory and clear on discard, session change/sign-out, or process death. Large responses remain bounded; there is no offline database.

## 0.4.0

See [0.4 verification](verification/0.4.0-NOTES.md) and [review](review/V04.md). Adds content-only profile editing with separate draft save and confirmed publishing. 57 JVM tests pass; APK/device test builds and lint pass. Device execution and live writes remain unverified. New debug signing identity requires reinstalling earlier builds.
