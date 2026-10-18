# omg.lol pocket — initial design

Purpose: a native Android, read-only account companion that can grow to support other omg.lol services. Kotlin, Compose, Android 8+, one activity. No account-changing requests.

Use email and a personal API key, matching the supplied Python reference. Optional persistence encrypts both values using an Android Keystore AES/GCM key; default is memory only. Credentials are never saved in Compose saved state, logs, backups or URLs. Sign-out clears the vault and visible account state. A revoked key returns the user to sign-in. This is a read-only client, not a read-only API key: the provider key may grant write access elsewhere.

The current documentation demonstrates OAuth with client_secret and does not document PKCE/public-client support. Do not ship an embedded secret or assume omission is supported. Personal-key authentication is the supported initial approach. Before consumer OAuth: obtain provider confirmation of public-client authorization code + S256 PKCE, registered redirect handling and token lifecycle, or design a separately secured backend broker. This app has neither an OAuth redirect handler nor a broker.

Data: immutable account, address and address-detail models; AccountRepository interface; live HTTP and demo implementations; one ViewModel exposes StateFlow. Feature repositories can reuse the API transport later without coupling to the account screen. No speculative write interfaces.

Flow: sign-in or explicit demo → account card and owned addresses → select address and retrieve private detail → retry/refresh or sign out. Empty addresses, malformed responses, timeouts, offline, 401/403, 429 and service failures have user-facing states. Refresh preserves current selection if still owned. Cancellation and request generations prevent old responses replacing a new session or address.

Tests: documented JSON envelope contracts with MockWebServer; encoded paths; bearer header; auth/error mapping; ViewModel sign-in, empty list, retry, selection races, logout and persistence failures. Device tests cover Keystore round-trip and the demo UI flow. Build and tests must report actual environmental limitations.
