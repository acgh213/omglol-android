# omg.lol pocket: next features and implementation plan

> **For agentic workers:** Use superpowers:executing-plans for the next approved slice. This document is a proposal for review, not authorization to implement every release below.

**Goal:** grow the working account client into a useful native omg.lol companion, one testable feature at a time.

**Architecture:** retain the existing account/session owner for the next read-only feature. Add address-scoped feature state and repositories without sharing credentials with composables. Introduce a shared authenticated-session boundary only when adding the first authenticated write feature.

**Tech stack:** existing Kotlin 2.1.20, Compose, AGP 8.9.2, Gradle 8.13, OkHttp, Gson and coroutines; JDK 17, compile/target SDK 35, minimum API 26. No dependency upgrades required for the next slice.

**Spec:** the “Next release requirements” section below; initial foundation remains documented in `docs/DESIGN.md`.

## Starting point and intent

Baseline commit: `1afa44d31ab43ee25c5e75ce468286404318ff0d`.

The initial app builds and passes 17 JVM tests. Cassie reports successful email/API-key login and loading both owned addresses on her Android phone. Automated device tests are compiled but have not been executed. Preserve that working flow as the baseline.

Cassie wants site features added gradually and requested a plan before further implementation. This proposal prioritizes daily usefulness, visible progress and a small next release. Statuslog-first is a recommendation, not a previously selected user preference.

## Options considered

| Direction | Benefit | Cost | Recommendation |
|---|---|---|---|
| Statuslog first | Useful on a phone; straightforward read-only start, then posting | Needs address-aware feature navigation | Start here |
| Profile editor first | Directly manages the user's public site | Source content, CSS, metadata and publish semantics need careful preservation | Follow after the first write workflow |
| Pastebin first | Bounded list/detail/editor workflow | Less central to the account-to-site experience | Follow profile, or move earlier if preferred |

## Proposed sequence

| Slice | User outcome | Deliberate boundary |
|---|---|---|
| 0.2: browsing | Account + Statuslog, global address picker, screenshots outside sign-in | Read-only; no composer or editor |
| 0.3: posting | Write a status for the selected address, submit, see the result | Create only initially; editing/deletion and social feed later |
| 0.4: profile | Retrieve site source, edit, explicitly save or publish | Preserve CSS/head/theme/metadata; no lossy structured form or executable preview |
| 0.5: Pastebin | List, view, create and edit pastes | Delete only with a separate explicit confirmation flow |
| 0.6: /now | Read and edit the selected address’s /now page | Explicit save, preserve page source, handle unsaved edits |
| 0.7: weblog | Browse entries and create/edit weblog posts | Draft/publish behavior and configuration preservation verified before writes |
| Later | PURLs, pictures; account settings and DNS when useful | DNS and forwarding need purpose-built validation and change review |

Each slice gets its own acceptance check and installable APK. Future slices need their own API contract inspection and detailed plan before implementation. These are sequencing proposals, not delivery-date promises.

## Next release requirements: 0.2

### Product behavior

- Keep sign-in, optional encrypted persistence, account information, address details, refresh/retry and sign-out working.
- Add two destinations: **Account** and **Statuslog**. Do not add disabled placeholders for later features.
- Put a labeled address selector above the destination content. It is the single selection shared by account details and Statuslog. Keep selection across destination changes and account refresh while the address remains owned.
- Account with zero addresses: retain account access, show “No addresses belong to this account yet”, and show an empty Statuslog explanation without making a request.
- Statuslog displays entries for the selected owned address, newest first, with emoji, content and a localized date/time. Render content as plain text initially; do not execute HTML or load it in a WebView.
- Support loading, genuine empty list, refresh, first-load failure/retry, and refresh failure that retains existing same-address results with a visible stale-data message. Never show one address's results beneath another address's label.
- Do not invent pagination: the documented route returns an entire address Statuslog. Use a lazy list for rendering. Retain a bounded response size (1 MiB for this slice); an oversized response produces a distinct explanatory error, not an empty list or silent truncation. Supporting larger histories/pagination is follow-up work if needed.
- Invalid/missing timestamps must not crash the list: place undated entries after dated entries and label “Date unavailable”. Accept documented numeric timestamps represented as strings or numbers. Preserve the server order for equal/invalid timestamps.
- Support demo Statuslog data for both demo addresses, with clearly distinct entries and no networking.
- Allow screenshots of signed-in screens. Protect credential entry and restoration with FLAG_SECURE. Reapply protection on sign-out/auth expiry before credentials can be entered. Do not persist API-key input or introduce a credential-reveal screen.
- Add a simple vector launcher icon, keeping the unofficial client identity clear.

### Data and state boundaries

- Existing `AccountViewModel` remains the owner of credentials and owned-address selection. Statuslog reads are public, so this release does not need to extract a new credential/session framework.
- Introduce `AddressScope(sessionGeneration: Int, address: String, demo: Boolean)`. Expose only this non-secret context to the Statuslog feature; scope is null while signed out, restoring, or without an owned selected address. Advance generation when sessions change.
- `StatusRepository.statuses(address: String): List<StatusEntry>` has live and demo implementations. `StatusEntry(id: String, address: String, emoji: String, content: String, createdEpochSeconds: Long?)` is immutable.
- `StatusViewModel.setScope(scope: AddressScope?)` cancels old work, immediately clears old content and loads the new scope. `refresh()` and `retry()` act only on the current scope. Every completion checks the captured scope/request generation, including non-cooperative stale responses.
- Repository implementation is chosen from the scope's demo flag; demo must never call the live repository. An entry with a different address from the requested one is a format error.
- Add `OmgApi.statuses(address)` using the documented public GET `/address/{address}/statuses/` and `response.statuses` envelope. Do not send Authorization on this public request. Preserve HTTPS-only production URL, encoded segments, disabled redirects, cancellation, timeouts and safe error messages.
- Public Statuslog failures do not invalidate the authenticated account session. Only authenticated account failures retain the existing session-expiry behavior.
- Extract a small common response transport if required for reuse; do not duplicate the entire HTTP client or relax existing account endpoint tests. Add a distinct response-too-large failure category/message.
- Use one bounded scrolling surface per destination. The existing outer `verticalScroll` must not wrap an unbounded `LazyColumn`.

### Scope exclusions

No posting, profile writes, background sync, notifications, offline database, feeds/follows, multi-account switching, analytics, OAuth, dependency refresh or networking/backend service. Existing local key storage remains unchanged except where regression tests show a defect.

## Global constraints

- No secrets in UI state, saved instance state, logs, URLs or backups.
- No embedded OAuth client secret. Public-client PKCE support remains unconfirmed.
- Only GET network operations in 0.2; no API mutations in tests against the live service.
- Keep existing account API response limits and error/cancellation behavior.
- Only publish the new APK after unit tests, app build and lint; report device-test execution separately.

## Review focus

1. Rapid address switches and logout during loads must never publish obsolete results.
2. Public endpoint failures must not unexpectedly sign the user out.
3. Empty, malformed, oversized and undated responses must be distinguishable and usable.
4. Returning to sign-in must protect credentials again; ordinary account screenshots must work.
5. Switching Account/Statuslog must retain one address context and avoid nested-scroll failures.

## Implementation tasks for 0.2

### Task 1: Statuslog data contract

**Files:** modify `data/OmgApi.kt`, `data/Models.kt`; create `data/StatusRepository.kt`, `OmgStatusApiTest.kt`, `StatusRepositoryTest.kt` in the existing source/test packages.

**Consumes:** existing safe HTTP/envelope handling. **Produces:** `StatusEntry`, `StatusRepository.statuses(address)`, `OmgApi.statuses(address)` and demo/live repositories.

- [ ] Write and run failing contract tests: statuses array maps correctly; empty array is valid; missing/wrong-shaped array and mismatched address fail; string/numeric/invalid timestamps follow the ordering requirements; emoji and Unicode survive unchanged.
- [ ] Add tests asserting GET, encoded address segment, documented trailing slash and absence of Authorization. Cover redirect refusal, offline, HTTP 429/5xx and >1 MiB responses.
- [ ] Implement only the contract above. Keep transport error messages free of raw response data and credentials.
- [ ] Run `./gradlew testDebugUnitTest`; require all old and new tests to pass. Commit the data slice.

### Task 2: Address-scoped feature state

**Files:** modify `ui/AccountViewModel.kt`; create `ui/StatusViewModel.kt`; extend `AccountViewModelTest.kt` and add `StatusViewModelTest.kt`.

**Consumes:** Task 1 repository. **Produces:** non-secret `AddressScope?`, Statuslog state, `setScope`, `refresh`, `retry`.

- [ ] Write failing coroutine tests for scope A→B with A finishing late, logout during request, rapid refresh completions, demo isolation, zero addresses, removed selected address and destination changes.
- [ ] Assert public 401/403 does not clear the live account session. Assert refresh failure retains same-address entries but initial failure exposes retry.
- [ ] Implement cancellation plus generation checks and the single selected-address context. Do not move credentials into feature state.
- [ ] Run full JVM suite; require all tests to pass. Commit state behavior.

### Task 3: Native navigation, Statuslog screen and screenshot behavior

**Files:** modify `MainActivity.kt`, `ui/PocketApp.kt`, manifest; create `ui/StatusScreen.kt`, `ui/AddressSelector.kt`, vector icon resources; extend device UI tests.

**Consumes:** Account state and Task 2 scoped Statuslog state. **Produces:** the two-destination native flow described above.

- [ ] Add UI tests for selecting each address, changing destinations, Statuslog empty/error/retry/refresh states, and sign-out clearing feature content. Include a 100-entry list with long text and large font scale to catch scrolling issues.
- [ ] Add device assertions that FLAG_SECURE is set on credential entry/restoration, cleared on authenticated/demo content, and restored on sign-out. Include an Activity recreation check.
- [ ] Implement the shared address picker, two destinations, lazy list, status display, state messages, screenshot policy and vector icon. Use lifecycle-aware collection. Keep API-key fields in ordinary `remember`, not saved state.
- [ ] Run `./gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`. Require no lint errors and explain any remaining warnings.
- [ ] If a device is attached, run `./gradlew connectedDebugAndroidTest`; otherwise mark those tests compiled/unexecuted. Commit the UI slice.

### Task 4: Release and acceptance

**Files:** update `README.md`, `docs/VERIFICATION.md`, app version to 0.2.0 / versionCode 2.

- [ ] Review the diff for accidental mutations, credential exposure and unrelated refactors.
- [ ] Repeat verification after final code changes, not merely after documentation edits. Record commands and actual results.
- [ ] Produce a debug APK signed with the same debug key as 0.1.0 where that key is available; verify the certificate before claiming it is an in-place update. If the key is unavailable, report that installation may require uninstalling and signing in again. Do not claim to preserve stored credentials across a different signing key.
- [ ] Cassie's acceptance: update/install, sign in, switch both real addresses, browse and refresh each Statuslog, take a screenshot, rotate/recreate, sign out and verify credential-screen protection. Do not require a public status post for this read-only acceptance.
- [ ] Record observed results and outstanding limitations. Publish the APK artifact; do not claim completion based solely on a successful build.

## Design obligations for later writes

Before 0.3, introduce an authenticated-session service shared by feature repositories; keep bearer secrets out of feature UI. Status creation must show the exact target address, preserve a draft through transient failures and use a one-shot submit. Do not automatically retry POST after an ambiguous network failure: re-fetch/check the result first to avoid duplicate public statuses. The provider example supports Mastodon cross-post control; expose an explicit choice and default to skipping cross-posting until the user chooses otherwise. Demo posting remains local.

Before profile editing, inspect retrieval and update semantics again. Preserve every unrelated source/configuration field, distinguish Save from Publish, detect unsaved edits when switching address and avoid claiming conflict protection the API does not document. Prefer a source editor initially over a form that would destroy custom page structure. Do not run arbitrary page source with authenticated app privileges.

Before Pastebin writes, establish private/listed visibility semantics and exact request/response contracts. Keep draft identity tied to account, address and paste name. Destructive changes require a separate explicit action.

## Sources and open decisions

Reviewed 2026-10-17:
- Current repository and baseline commit above: https://github.com/acgh213/omglol-android
- Official API: https://api.omg.lol/ — Statuslog list/create, Web retrieve/save/publish, Pastebin and service index.

Accepted 2026-10-17: Cassie approved 0.2 and requested weblog and /now management later. Implementation proceeds on 0.2 only; later release numbers are provisional. Screenshot scope is proposed in response to her reported screenshot restriction. Broader feature work starts after review of this plan.
