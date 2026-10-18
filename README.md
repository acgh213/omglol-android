# omg.lol pocket

A native Kotlin / Jetpack Compose Android client, initially focused on **read-only account access**. This is an independent, unofficial client.

## Open and run

1. Extract this folder and open it in Android Studio (Meerkat or newer).
2. Use JDK 17 for Gradle. Install Android SDK Platform 35 and Build Tools 35.0.0 when prompted. Android Studio creates your local SDK path.
3. Sync Gradle, choose the `app` configuration, and run on Android 8.0 / API 26 or newer.
4. Tap **Try demo** to explore without credentials, or enter your omg.lol account email and API key. The app never asks for your password.

CLI: `./gradlew testDebugUnitTest assembleDebug lintDebug`. Device checks: `./gradlew connectedDebugAndroidTest`. Windows: `gradlew.bat`.

## Included

- Account name, email and creation age; owned address list and selection; authenticated address registration, expiry and verification details.
- Loading, empty, retry, refresh, authentication-expired, rate-limit, service and offline states.
- Optional encrypted sign-in persistence and sign-out. Nothing is persisted by default.
- Explicit demo mode, isolated from live networking and storage.
- MockWebServer API tests, coroutine ViewModel tests, and on-device vault / Compose smoke tests.

## Authentication decision

Reviewed the Python reference `acgh213/omgloltui/omglol_api.py` (blob `7015722c2219953730e35548feb036badcfc6f62`) and https://api.omg.lol/ on 2026-10-17.

The Python wrapper uses bearer authentication and the `/account/{email}/info`, `/addresses`, `/sessions` and `/settings` endpoints. Its mutable class-level headers, synchronous networking and printed HTTP exceptions are not carried over. No Python code is shipped.

The official OAuth exchange example includes `client_secret`; the inspected API documentation does not establish a public-client flow with PKCE. This does **not** prove the service cannot support one. It means safe standalone Android OAuth is unconfirmed. No OAuth client secret, callback flow, or token exchange is included. Initial authentication uses a personal API key supplied by its owner. Before adding consumer OAuth, confirm authorization code + S256 PKCE without a client secret, redirect registration and token lifecycle with omg.lol, or design a secure backend broker.

The UI is read-only, but **the API key itself may have broader permissions**. It is sent only in the Authorization header to `https://api.omg.lol`, never in URLs. HTTP redirects are disabled. There is no analytics, network logging, disk response cache, or background work. TLS uses platform trust; no custom permissive trust manager.

**Remember on this device** encrypts email and key together with AES-256-GCM and a non-exportable Android Keystore key. Ciphertext lives in private preferences, excluded from cloud backup/device transfer. Corruption or missing Keystore material returns to sign-in. Sign-out removes local ciphertext and the Keystore alias; revoke the API key at omg.lol if needed. Device compromise is outside Keystore's protection boundary. The app obscures screenshots/recents via FLAG_SECURE. Credentials remain necessary in process memory while signed in; they are never in saved-instance state.

## Structure and extension

- `data/OmgApi.kt`: GET-only transport, envelope validation and safe error mapping.
- `data/Models.kt`: immutable domain models; redacted credential representation.
- `data/AccountRepository.kt`: account feature interface and live/demo implementations.
- `security/CredentialStore.kt`: injected storage interface and Android Keystore implementation.
- `ui/AccountViewModel.kt`: session lifecycle, state and race/cancellation protection.
- `ui/PocketApp.kt`: stateless Compose flows backed by lifecycle-aware state collection.

Add profile, statuslog and Pastebin as separate feature repositories and ViewModels that share a session/transport boundary. Add explicit mutation operations only when implementing those features; the current API exposes GET operations only. Do not attach bearer credentials to public/external links or make the API base URL user-configurable. Add feature navigation when a second real feature exists.

See `docs/DESIGN.md`, `docs/PLAN.md` and `docs/VERIFICATION.md` for scope and verification status. Cassie has confirmed successful live sign-in and loading of both owned addresses on her Android phone. Automated device tests remain unexecuted; see the verification notes.
