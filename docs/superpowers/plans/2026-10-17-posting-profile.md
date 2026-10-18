# 0.3: status posting and profile source viewing

Goal: make Statuslog useful for publishing, then add a read-only Profile destination. Builds on fc98731 (0.2, user-confirmed working on both addresses and screenshots).

## Requirements

- Native composer: target address, emoji, multiline content, explicit Mastodon cross-post choice (off by default), deliberate Post action. Reject blank content and duplicate taps. Preserve drafts through transient failures and rotation, in process memory only. Closing a nonempty draft asks whether to discard. Scope changes/sign-out remove old session data; a modal composer prevents accidental address changes during editing/submission.
- An authenticated feature boundary holds credentials outside composables/feature state. Every authenticated operation checks session generation and owned address before and after requests; stale results cannot affect newer sessions. Auth rejection invalidates only the matching active session.
- POST /address/{address}/statuses/ sends emoji, content and skip_mastodon_post. Disable OkHttp transport retries; never automatically re-submit a POST. Success requires a valid response ID, shows a receipt and refreshes history.
- Ambiguous POST failures (network, server, malformed/oversized success) retain the draft in an uncertain state. Block posting until the user explicitly checks latest history and acknowledges duplicate risk before enabling another deliberate attempt. No matching-text heuristic claims proof of success. Definite 4xx rejection/rate limit can be corrected/retried manually. No live test posts from the builder.
- Demo posting is local, isolated from credentials/network, and immediately appears in the shared demo history. No persistent demo data.
- Profile tab: authenticated GET /address/{address}/web, display page content, type/theme and source/config fields as selectable plain text. No HTML execution, no edits, no publishing. Keep raw source/config strings; empty content is valid. Address/session switch and logout clear stale profile data; errors/refresh/retry work.
- Preserve existing account flows, screenshot policy and public reads without bearer headers. Keep credentials and drafts out of saved-instance state/backups/logs. No new library dependencies required. Minimum API 26, toolchain unchanged.
- Version 0.3.0 / code 3, signed with existing debug identity. Weblog and /now remain later roadmap items.

## Architecture and files

- security/AccountSession.kt: in-memory owned-address/session capability; no StateFlow of credentials. AccountViewModel activates/clears it and handles matching-session auth rejection.
- data/OmgApi.kt and Models.kt: JSON POST, authenticated profile GET, models and safe rejection mapping. Reuse bounded/cancellable transport; redirects and transport retries disabled.
- data/FeatureRepository.kt: authenticated posting/profile facade; demo routing reuses the same DemoStatusRepository as StatusViewModel.
- ui/ComposerViewModel.kt and ComposerDialog.kt: draft lifecycle, submission, uncertainty/history check, discard UX, receipt. Feature models receive scope, never raw credentials.
- ui/ProfileViewModel.kt and ProfileScreen.kt: scoped fetch/read-only source display.
- MainActivity/PocketApp/StatusScreen: inject shared session/repositories, third destination, compose entry point and post-success history refresh.

## Tasks and verification

1. Write HTTP tests for JSON POST/body escaping, bearer placement, one request only on disconnect/redirect, error envelopes and missing receipt; profile source/empty fields/auth headers. Implement transport/models. Run JVM suite.
2. Write session tests for wrong generation, unowned address, logout during request, old auth failures and no credential rendering. Implement session facade and integrate with account lifecycle.
3. Write composer tests for blank input, double taps, draft preservation, uncertain outcome/check/explicit retry, session change, demo-local history and cross-post default. Write profile state tests for switching/error/stale responses. Implement state and feature repos.
4. Wire Compose UI; add compiled device tests for demo posting/receipt/history, discard confirmation and profile viewing. Verify app/device-test builds, JVM suite and lint. Request independent source review and fix important findings with regression tests.
5. Record actual checks and limitations, compare APK signing cert with 0.2, publish source and APK. Live posting and device runtime acceptance remain for Cassie; builder uses mocks/demo only.

## Review focus

One POST per tap sequence; unknown outcomes never trigger automatic retries; correct account/address capability; matching-session expiry only; demo never sends credentials; source is displayed without execution; drafts never leak across sessions or get silently discarded by normal navigation.

## API grounding

https://api.omg.lol/ inspected 2026-10-17: Statuslog create uses JSON with emoji/content/skip_mastodon_post and returns response.id. Web retrieval returns content plus type/theme/css/head/metadata/branding/pfp. OAuth public-client support remains unconfirmed; personal keys remain the initial auth method.

No status or profile mutation is performed by the agent during development. Profile editing, status editing/deletion, offline draft persistence, /now and weblog writes are outside this slice.
