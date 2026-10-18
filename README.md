# omg.lol pocket

A native Kotlin / Jetpack Compose Android client, with **account access, Statuslog browsing/posting, and a read-only profile source viewer**. This is an independent, unofficial client.

## Open and run

1. Extract this folder and open it in Android Studio (Meerkat or newer).
2. Use JDK 17 for Gradle. Install Android SDK Platform 35 and Build Tools 35.0.0 when prompted. Android Studio creates your local SDK path.
3. Sync Gradle, choose the `app` configuration, and run on Android 8.0 / API 26 or newer.
4. Tap **Try demo** to explore without credentials, or enter your omg.lol account email and API key. The app never asks for your password.

CLI: `./gradlew testDebugUnitTest assembleDebug lintDebug`. Device checks: `./gradlew connectedDebugAndroidTest`. Windows: `gradlew.bat`.

## Included

- Account and Statuslog destinations with one shared owned-address picker.
- Public Statuslog history with emoji, plain-text content, local dates, refresh/retry and bounded response handling.
- Status composer with explicit address, emoji, multiline text and opt-in Mastodon cross-posting. Drafts survive rotation and transient failures in memory; closing a draft requires discard confirmation.
- A Profile destination showing raw page content/configuration as selectable plain text. It does not edit or publish the profile.
- Local-only demo posting appears immediately in demo history; no network or API key is used.
- Account name, email and creation age; owned address list and selection; authenticated address registration, expiry and verification details.
- Loading, empty, retry, refresh, authentication-expired, rate-limit, service and offline states.
- Optional encrypted sign-in persistence and sign-out. Nothing is persisted by default.
- Explicit demo mode, isolated from live networking and storage.
- MockWebServer API tests, coroutine ViewModel tests, and on-device vault / Compose smoke tests.

## Authentication decision

Reviewed the Python reference `acgh213/omgloltui/omglol_api.py` (blob `7015722c2219953730e35548feb036badcfc6f62`) and https://api.omg.lol/ on 2026-10-17.

The Python wrapper uses bearer authentication and the `/account/{email}/info`, `/addresses`, `/sessions` and `/settings` endpoints. Its mutable class-level headers, synchronous networking and printed HTTP exceptions are not carried over. No Python code is shipped.

The official OAuth exchange example includes `client_secret`; the inspected API documentation does not establish a public-client flow with PKCE. This does **not** prove the service cannot support one. It means safe standalone Android OAuth is unconfirmed. No OAuth client secret, callback flow, or token exchange is included. Initial authentication uses a personal API key supplied by its owner. Before adding consumer OAuth, confirm authorization code + S256 PKCE without a client secret, redirect registration and token lifecycle with omg.lol, or design a secure backend broker.

The only implemented account mutation is creating a status after you explicitly tap Post. Account details and profile source remain read-only; **the API key itself may have broader permissions**. It is sent only in the Authorization header to `https://api.omg.lol`, never in URLs. HTTP redirects are disabled. There is no analytics, network logging, disk response cache, or background work. TLS uses platform trust; no custom permissive trust manager.

**Remember on this device** encrypts email and key together with AES-256-GCM and a non-exportable Android Keystore key. Ciphertext lives in private preferences, excluded from cloud backup/device transfer. Corruption or missing Keystore material returns to sign-in. Sign-out removes local ciphertext and the Keystore alias; revoke the API key at omg.lol if needed. Device compromise is outside Keystore's protection boundary. The app protects screenshots/recents on credential entry and restoration via FLAG_SECURE. Signed-in and demo screens allow screenshots. Credentials remain necessary in process memory while signed in; they are never in saved-instance state.

## Structure and extension

- `data/OmgApi.kt`: bounded GET transport and one-shot JSON status creation, envelope validation and safe errors. POST bodies prohibit transport and HTTP follow-up replay, including 503 Retry-After: 0.
- `data/Models.kt`: immutable domain models; redacted credential representation.
- `data/AccountRepository.kt`: account feature interface and live/demo implementations.
- `security/CredentialStore.kt`: injected storage interface and Android Keystore implementation.
- `ui/AccountViewModel.kt`: session lifecycle, state and race/cancellation protection.
- `ui/PocketApp.kt`: Account/Statuslog navigation and lifecycle-aware state collection.
- `ui/StatusViewModel.kt`: address/session/request-scoped Statuslog state, separate from credentials.
- `data/StatusRepository.kt`: public live reads and isolated demo statuses. Public requests do not carry the account API key.
- `ui/StatusScreen.kt`: bounded lazy history list; source content is displayed as plain text.
- `security/AccountSession.kt`: in-memory credential capability scoped to the active session and owned addresses. Feature state does not hold API keys.
- `data/FeatureRepository.kt`: authenticated posting/profile facade with isolated demo routing.
- `ui/ComposerViewModel.kt` / `ComposerDialog.kt`: draft, receipt and uncertain-outcome workflow.
- `ui/ProfileViewModel.kt` / `ProfileScreen.kt`: address-scoped, read-only profile source viewing without a WebView.

Add profile, statuslog and Pastebin as separate feature repositories and ViewModels that share a session/transport boundary. Add explicit mutation operations only when implementing those features; the current API exposes GET operations only. Do not attach bearer credentials to public/external links or make the API base URL user-configurable. Add feature navigation when a second real feature exists.

See `docs/DESIGN.md`, `docs/PLAN.md` and `docs/VERIFICATION.md` for scope and verification status. Cassie has confirmed successful live sign-in and loading of both owned addresses on her Android phone. Automated device tests remain unexecuted; see the verification notes.

## Roadmap

See [the incremental feature plan](docs/superpowers/plans/2026-10-17-next-features.md). 0.3 adds status creation and a read-only profile source viewer. Profile editing, Pastebin, /now page management and weblog management remain planned.

## Posting behavior

The target address is displayed in the composer. Mastodon cross-posting is off by default. A successful response shows a receipt and refreshes history. A network/server failure or malformed receipt can mean the post succeeded even though the app could not confirm it: the draft remains locked in an uncertain state. Check latest statuses, then explicitly acknowledge the duplicate risk before unlocking another deliberate Post action. The app never automatically retries that write and never infers success merely from matching text. This is not a server-side idempotency guarantee.

Drafts are memory-only: they survive Activity recreation while the process lives, but are removed on discard, sign-out/session change, or process death. Demo posts are memory-only and reset when leaving the demo session. No status editing/deletion or profile mutation is implemented in 0.3.
