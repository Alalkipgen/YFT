# Handoff

## Current handoff

- Date: 2026-10-01
- Phase: 3 — Preview and variant resolution
- Status: COMPLETE — full local lint/test/debug/release validation green
- Active branch: `work/phase-3-preview-variants`
- Phase 3 base: final Phase 2 documentation head `eedae3b`
- Target repository: `Alalkipgen/YFT`
- Reference repository: `Alalkipgen/AlalDownloader`

## Work completed

- Preserved the complete secure Phase 2 browser/detection pipeline.
- Added redacting production models for assets, variants, track type, exact/estimated size, support state and structured resolution failures.
- Added a cancellable resolver with:
  - 10-second network timeout
  - five manual redirects maximum
  - HTTPS-only production URLs, with loopback HTTP allowed only for fixtures
  - 1 MiB bounded manifest reads
  - same-origin browser-context replay and cross-origin credential stripping
- Added direct-media validation using `HEAD` and a one-byte range fallback without intentionally consuming a full media body.
- Added HLS parsing for master/media playlists, video qualities, audio renditions, resolution, FPS, codec, bitrate and duration.
- Added external-entity-disabled DASH parsing for video/audio representations, language, resolution, FPS, codec, bitrate and duration.
- Added exact direct size and clearly marked bitrate/duration estimates; unavailable metadata remains explicitly unknown.
- Added expiry, DRM, unsupported-codec, malformed/oversized-manifest, unsafe-redirect, HTTP and network failures.
- Added explicit Media3 Progressive, HLS and DASH source creation over HTTPS.
- Added an origin-aware OkHttp policy that removes cookie, authorization, referer and custom browser-context headers from cross-origin preview requests.
- Added an in-memory-only selection store so signed URLs and cookies never enter navigation routes, saved state or Room.
- Added the Browser Preview action and navigation into a real preview screen.
- Added resolver loading/retry/error state, lifecycle-safe player creation/release, autoplay disabled, video/audio tabs and selectable variants.
- Added honest format, resolution, FPS, codec, bitrate, duration and exact/estimated/unknown size labels.
- Added model, resolver, redirect, manifest, source-factory, ViewModel and Compose regression coverage.
- Kept download plans, transfer engines, foreground services, muxing, recovery and storage export out of Phase 3.

## Important decisions

- Metadata resolution is bounded. It never fetches an entire direct media file and stops manifest reads after 1 MiB.
- Unknown values remain null/Unknown. URL labels and quality names are not used to invent technical metadata.
- Direct sizes are exact only when response headers expose a total. Bitrate × duration values are marked estimated.
- Any detected HLS key/session key or DASH `ContentProtection` is conservatively rejected as DRM.
- Empty codec data is treated as unknown, not unsupported. Known codec strings outside the supported preview allowlist fail clearly.
- The preview source is always HTTPS and never disables TLS certificate or hostname verification.
- Browser context is replayed only to the credential origin. Context-owned headers are removed before a cross-origin segment or redirect request.
- Candidate and variant `toString()` implementations redact sensitive URLs/context; upstream network/player exception text is not shown to users.
- A selected candidate is process-memory state only. Phase 4 must persist only safe task metadata and protect any transient request context.
- Preview does not imply offline exportability. HLS/DASH track download and audio/video mux compatibility remain Phase 4 decisions.
- No work branch was merged into `main`, and no release was signed or published.

## Main files and areas

- Asset/variant/result models: `core-model/src/main/kotlin/com/alal/yft/core/model/media/MediaAsset.kt`
- Resolver and manifest parsers: `core-media/src/main/java/com/alal/yft/core/media/resolver/`
- Media3 source/header policy: `core-media/src/main/java/com/alal/yft/core/media/player/`
- Ephemeral preview selection: `core-media/src/main/java/com/alal/yft/core/media/session/`
- Browser Preview action: `app/src/main/java/com/alal/yft/feature/browser/`
- Preview state/player/UI: `app/src/main/java/com/alal/yft/feature/preview/`
- Resolver/source fixtures: `core-media/src/test/`
- Preview UI/state tests: `app/src/test/java/com/alal/yft/feature/preview/`
- Phase continuity: `README.md`, `docs/PHASE_STATUS.md`, `docs/TEST_MATRIX.md`, `docs/SESSION_STATE.md`

## Validation

Phase 2 was verified before Phase 3 edits. Phase 3 completion used JDK 17.0.20.1, Android SDK 35 and Gradle 8.9:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

Results:

- **BUILD SUCCESSFUL** in 4m 12s.
- 497 tasks: 209 executed, 43 from cache and 245 up-to-date.
- 79 tests, 0 failures, 0 errors and 0 skipped:
  - app: 16
  - core-browser: 27
  - core-data: 4
  - core-media: 14
  - core-model: 11
  - extractor-generic: 7
- Debug APK: 15,089,630 bytes.
- Debug SHA-256: `328fd782adf9f3c924b07dba7588b462472f48f85a7c886d2e1fd7edff86b874`.
- Unsigned minified release APK: 2,595,334 bytes.
- Release SHA-256: `c51a449fb095d9f04c2fca8ef36b01b106dbfaf9b3e8178aa89b6ae424651c98`.
- Local structured-secret scan passed before each remote checkpoint.
- GitHub Advanced Security secret scanning is not enabled for this repository.

## Known limitations

- No physical Android device/emulator is attached and `/dev/kvm` is unavailable. Actual Android WebView rendering and direct/HLS/DASH Media3 playback still require on-device confirmation.
- The Media3 source classes and header policy are covered, but a controlled HTTPS cookie-protected playback fixture has not run on a device.
- HLS parsing does not recursively fetch every child playlist during metadata resolution; duration/size remain unknown unless available honestly at the parsed level.
- DASH representation metadata is resolved, but Media3 adaptive playback currently opens the MPD rather than forcing a selected representation through track-selection parameters.
- Codec support remains device-dependent even for recognized Media3/Android codec strings; runtime decoder failure is surfaced safely.
- Live-stream recording is outside scope.
- No direct/HLS/DASH download engine, muxer, foreground service, persisted recovery, MediaStore/SAF export or low-storage handling exists yet; all are Phase 4.
- The release APK is unsigned; signing and publication remain Phase 7 and require explicit approval.
- KAPT emits a Kotlin 2.0 fallback warning while generating Hilt/Room code; compilation, lint and tests pass.

## Next exact action

1. Push the final Phase 3 documentation/completion commit and verify the remote head.
2. Create `work/phase-4-download-engines` from that exact Phase 3 completion head.
3. Read `docs/prompts/05_PHASE_4.md` and verify the Phase 3 resolver/preview baseline on the new branch.
4. Inspect the existing `:core-download`/Room foundation and the approved concepts in `Alalkipgen/AlalDownloader`.
5. Design YFT download-plan/task models and direct-engine boundaries first; do not begin Phase 5.

## Phase 3 checkpoint commits

- Kickoff from final Phase 2: `697ffb4`
- Direct/HLS/DASH resolver and models: `f53be1c`
- Secure Media3 source construction: `91ad4b5`
- Browser selection and preview player flow: `caf4962`

The final documentation/completion commit is newer; resolve it with `git log -1 --oneline` on `work/phase-3-preview-variants`.