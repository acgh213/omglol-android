# omg.lol pocket implementation plan

Goal: deliver an importable Android Studio project implementing docs/DESIGN.md.
Architecture: Compose → AccountViewModel → AccountRepository → OmgApi; CredentialStore is injected. Demo repository makes no network requests.
Stack: Kotlin 2.1.20, AGP 8.9.2, Gradle 8.13, Compose, OkHttp, Gson, coroutines. JDK 17, SDK 35, minSdk 26.

1. Write API contract tests for account info, array-shaped addresses, address detail, encoded path segments and error mapping. Implement models and read-only transport. Run JVM tests.
2. Write state tests for invalid input, successful sign-in, selection, retries, auth expiry, logout, vault failure and stale requests. Implement repository and ViewModel. Run JVM suite.
3. Implement AES/GCM Android Keystore vault with optional persistence; compose sign-in/account/address/error screens and accessible scrolling layouts. Add device tests for vault and demo navigation.
4. Generate standard Gradle wrapper; assemble debug, run JVM tests and lint. Run device tests if a device is available. Record any unavailable verification. Package source with setup, security decisions and extension notes.

Review focus: no secret in saved state/logs/URLs/backups; malformed API data cannot appear as valid empty data; address changes cannot show stale details; logout cannot restore old credentials; storage failure never silently persists plaintext.
