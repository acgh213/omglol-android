# 0.4 profile source editor

Goal: extend the profile viewer with explicit Save draft and Publish actions. The user approved continued feature work; this slice implements the next roadmap feature. Base de381ea (0.3).

## Scope and behavior

- Edit only page content as raw source. CSS, head, theme, metadata, branding and picture stay viewable and are never sent back or changed by the editor. Native text editor; no executable HTML preview.
- Full-screen modal editor keeps one target address and blocks normal tab/address navigation. Back/Close asks before discarding changed text. Rotation retains process-memory drafts; sign-out/session change clears them. No autosave/background writes.
- Save draft: documented POST /address/{address}/web with content only (publish omitted). Publish: same endpoint with publish:true, only after a separate confirmation naming the public target. Empty content requires deliberate confirmation too.
- Before either write, authenticated GET retrieves current source/config. If it differs from the editor baseline, preserve edits and show a conflict. Offer loading latest (discards local edits only with confirmation), or explicitly replacing page content after review. Re-check before replacement; another remote change causes a new conflict. Never claim this is atomic compare-and-swap; the API does not document version preconditions.
- Use the existing session-owned capability, one-shot HTTP request body and safe error mapping. No automatic POST retry. A failure during the preflight GET has submitted nothing; an ambiguous failure after starting POST locks the draft until a read-back check and explicit acknowledgement. Matching source does not establish publication state.
- Success updates the local content baseline and clearly distinguishes saved-unpublished versus published. User can publish after saving without changing text again.
- Demo saves and publishes are local; preserve every other configuration field and keep separate content per address. Reset when the demo session ends. No live mutations by the builder.
- Existing status composer, auth, screenshot policy and browsing remain intact. Version 0.4.0/code 4; toolchain versions unchanged.

## Files and tasks

1. data/OmgApi.kt, Models.kt, FeatureRepository.kt: add ProfileSaved and saveProfile contract; documented minimal JSON payload, acknowledgement validation, shared demo source storage/reset. HTTP tests: content escaping, omitted/true publish, no config round-trip, no POST replay, rejection/malformed acknowledgements.
2. ui/ProfileViewModel.kt: editor baseline/draft, one-shot preflight/save, conflict and uncertain-outcome state. JVM tests: unchanged config, save then publish, double taps, preflight failure/no write, remote changes, stale response/session loss, ambiguity/readback/manual acknowledgement and demo isolation.
3. ui/ProfileEditorDialog.kt, ProfileScreen.kt, PocketApp.kt: editor entry point, source field, separate actions, Publish/empty-source confirmations, discard/conflict/unknown-outcome flows. Compile device tests for demo save/publish, discard, rotation, source preservation and account navigation.
4. Independent source review, regression fixes, clean full JVM suite, app/device-test builds and lint. Record actual device limitations. Publish source/APK and signing compatibility notes.

## Review focus

No publication through Save draft; no settings overwritten; no silent draft loss; conflict retry cannot skip a newer remote change; failures after POST are never auto-resubmitted; read-back cannot claim proof of publishing; credentials/drafts remain out of saved state and logs.

## Constraints and verification

Official API inspected 2026-10-18: https://api.omg.lol/, Web retrieve/update sections. Save example omits publish; publishing example sets publish:true. No conditional revision token is documented. Reuse the existing HTTPS-only, encoded, bounded and cancellable transport.

Environment reset removed the previous toolchain and debug key. Rebuild with JDK17/SDK35/Gradle8.13, generate a new debug key and retain a private development signing backup. New certificate cannot update older installed APKs; report the required reinstall rather than claiming in-place compatibility. No signing keys go into GitHub.
