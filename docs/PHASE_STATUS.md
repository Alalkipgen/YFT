# Phase Status

| Phase | Status | Summary |
| --- | --- | --- |
| 0 — Discovery and feasibility | COMPLETE | Architecture, support matrix, ADRs and compileable Media3/WebView spike validated locally and in CI |
| 1 — Foundation | COMPLETE | Production modules, Compose navigation shell, DI/data/media foundations, tests and CI validated on the work branch |
| 2 — Browser/detection | COMPLETE | Secure browser, layered generic detection, bounded probes, page-scoped normalization, fixtures and candidate UI validated |
| 3 — Preview/variants | COMPLETE | Bounded direct/HLS/DASH resolution, honest variants and secure Media3 preview validated |
| 4 — Download engines | COMPLETE | Typed download plans, direct/HLS/DASH transfer, mux compatibility, foreground execution, Room recovery and safe export validated |
| 5 — Site adapters | IN PROGRESS | Per-site extractors behind the extractor API, started from the green Phase 4 head |
| 6 — Hardening/UI | NOT STARTED | — |
| 7 — Signed beta/release | NOT STARTED | — |

## Current phase state

Phase 5 is active on `work/phase-5-site-adapters`, created from the Phase 4 completion head. Work is limited to site adapters behind `:extractor-api`, adapter selection/fallback to the generic detector, and adapter fixtures/tests; do not change the download engines and do not merge a work branch into `main`.

## Completed in Phase 1

- Added the Gradle 8.9 wrapper with a pinned distribution checksum.
- Created the production modules defined in `ARCHITECTURE.md`:
  - `:app`
  - `:core-model`
  - `:core-data`
  - `:core-browser`
  - `:core-media`
  - `:core-download`
  - `:extractor-api`
  - `:extractor-generic`
  - `:extractor-sites`
- Added the `com.alal.yft` production app and `.debug` debug application suffix.
- Implemented a Compose/Material 3 shell with all eight required routes and back navigation.
- Added light/dark/system themes with ViewModel/StateFlow events and DataStore persistence.
- Wired Hilt providers and boundaries for Room, DataStore, OkHttp and Media3.
- Added Room schema export, a DAO baseline test and a validated v1→v2 migration.
- Added structured `AppResult`/`AppError` types and secret-redacting logging.
- Added a Robolectric Compose smoke test for Home, every destination and back navigation.
- Expanded GitHub Actions to run Android lint, all Android/JVM unit tests and the debug build for work branches and pull requests.
- Configured and locally validated debug and minified release builds.

## Phase 1 validation

- Full local command passed with JDK 17.0.20.1, Android SDK 35 and Gradle 8.9:

```bash
./gradlew --no-daemon \
  lintDebug \
  testDebugUnitTest \
  :core-model:test \
  :extractor-api:test \
  :extractor-generic:test \
  :extractor-sites:test \
  :app:assembleDebug \
  :app:assembleRelease
```

- Result: **BUILD SUCCESSFUL** in 4m 31s; debug and unsigned minified release APKs assembled.
- GitHub Actions final checkpoint validation passed in run `36793372961`.
- A forced fresh split validation also passed: lint/tests/debug ran 279 tasks with 20 tests and zero failures; release assembly ran 207 tasks.
- The Compose navigation smoke test is included in `testDebugUnitTest`.
- No physical Android device/emulator was available, so direct/HLS/DASH playback and on-device rendering remain tracked runtime tests rather than claimed support.


## Completed in Phase 2

- Added a hardened WebView with HTTPS-only top-level navigation, safe browsing, mixed-content blocking, first-party cookies, file/content access disabled and explicit TLS/HTTP/insecure-navigation error states.
- Added address, back/forward, reload/stop, progress and page-title controls with ViewModel/StateFlow state.
- Added generic observations from `DownloadListener`, a read-only DOM script and HTTP(S) GET request URLs/headers.
- Added direct-media, HLS and DASH URL/MIME classification while rejecting literal `blob:` URLs.
- Added bounded/cancellable metadata probing:
  - `HEAD` first and one-byte range fallback
  - 10-second call timeout
  - five redirects maximum
  - 20 unique strongly hinted URLs and two concurrent probes per page
  - same-origin browser context replay with cross-origin credential stripping
- Added candidate models with source, kind, MIME, title, thumbnail, duration, size, request context, confidence, expiry and DRM hints.
- Added page-scoped normalization with signed-URL deduplication, a 200-observation/50-candidate bound, debounce, tiny/tracking asset rejection and immediate navigation cleanup.
- Added the media-found floating button and an honest bottom sheet; unavailable metadata remains visibly unknown.
- Added committed generic HTML/golden fixtures, MockWebServer redirect/header fixtures, a complete detector-pipeline regression and Compose/ViewModel tests.
- Kept site adapters, preview/variant resolution and download engines out of Phase 2.

## Phase 2 validation

- The exact production DOM script ran against the committed HTML fixture in headless Chromium: 10 observations, 6 unique expected URLs and 0 missing URLs.
- `:core-browser:testDebugUnitTest` currently contains 27 passing tests; `:app:testDebugUnitTest` contains 10 passing tests.
- Core browser lint reports no issues; the app lint task has no errors.
- The debug APK assembles successfully.
- Fresh full lint/tests/debug validation passed in 3m 39s with all 294 tasks executed.
- Fresh minified release assembly passed separately in 4m 27s with all 207 tasks executed.
- Across the executed test suites, 56 tests passed with 0 failures, errors or skips.
- Phase 2 completion commit `d7d25b6` passed GitHub Actions run `36799479295`.
- No physical Android device/emulator was available, so real Android WebView callback/rendering behavior remains an explicit runtime verification item rather than a claimed on-device result.

## Completed in Phase 3

- Added redacting asset/variant/result models with explicit direct/HLS/DASH, video/audio/separate-track, support and size-accuracy state.
- Added a cancellable resolver with a 10-second timeout, five redirects, HTTPS production policy and a 1 MiB manifest bound.
- Added direct HEAD/range validation without consuming full media bodies.
- Added HLS master/media parsing and secure DASH XML parsing for real resolution, FPS, codec, bitrate, duration, language and track data.
- Added exact direct sizes and clearly marked bitrate/duration estimates; unavailable metadata remains unknown.
- Added explicit expiry, DRM, malformed-manifest, oversized-manifest, unsafe-redirect, HTTP/network and unsupported-codec failures.
- Added HTTPS-only Media3 Progressive/HLS/DASH sources with origin-aware browser-context replay and cross-origin credential stripping.
- Added an in-memory-only browser-to-preview selection boundary so sensitive media URLs never enter navigation or persistence.
- Added lifecycle-safe, non-autoplaying preview playback with video/audio tabs, variant selection, retry and safe errors.
- Kept download planning, transfer services, storage export and recovery out of Phase 3.

## Phase 3 validation

- Full command passed:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

- Result: **BUILD SUCCESSFUL** in 4m 12s; 497 tasks, 79 tests, 0 failures, 0 errors and 0 skipped.
- Debug APK: 15,089,630 bytes; SHA-256 `328fd782adf9f3c924b07dba7588b462472f48f85a7c886d2e1fd7edff86b874`.
- Unsigned minified release APK: 2,595,334 bytes; SHA-256 `c51a449fb095d9f04c2fca8ef36b01b106dbfaf9b3e8178aa89b6ae424651c98`.
- No device/emulator was available, so actual Android WebView and Media3 rendering/playback remain explicit runtime checks rather than claimed device validation.

## Phase 4 active scope

- Define durable download-plan, task-state, progress, failure and recovery boundaries using YFT models.
- Implement direct range/no-range transfer first with bounded concurrency, temp parts, pause/resume/retry/cancel and integrity checks.
- Preserve replay-safe request context while handling redirect and expiry failures.
- Add selected-track HLS/DASH transfer and explicit audio/video mux compatibility after the direct engine is green.
- Add foreground execution, Room recovery and MediaStore/SAF export without using WorkManager as the sole large-transfer engine.
- Test incomplete-file safety, process restart, network changes, duplicates, low storage, corruption, cancellation and cleanup.
- Do not start site adapters or Phase 5.

## Completed in Phase 4

- Added durable download boundaries in `:core-model`: typed `DirectDownloadPlan`, `HlsDownloadPlan`, `DashDownloadPlan` and `AudioVideoMuxDownloadPlan`, a `DownloadTaskStatus` lifecycle, segment/progress models, a closed `DownloadFailureReason` set and per-engine `TransferCheckpoint` types.
- Added a direct transfer engine with HEAD/one-byte-range probing, bounded parallel ranged segments, a single-stream fallback when the server refuses ranges, strong-validator resume (`ETag`/`Last-Modified`), partial-file staging and verified length/integrity checks.
- Added HLS and DASH segment engines with bounded manifest parsing, selected-track transfer, per-segment checkpoints and resume from the last verified segment.
- Added platform audio/video muxing with an explicit compatibility gate: only separate AVC video plus AAC audio in MP4/fMP4 containers are accepted, and encrypted samples are rejected. Extractor sample flags are translated into muxer buffer flags instead of being forwarded, because the two constant sets overlap numerically with different meanings.
- Added `DownloadQueue` with bounded concurrency, pause/resume/retry/cancel, pause-all, network-aware suspension, duplicate-safe independent records and an injectable IO dispatcher so cancellation is deterministic under tests.
- Added Room schema v4 with a covered v3→v4 migration, a `RoomDownloadTaskStore` and typed checkpoint persistence. Only non-sensitive recovery state is stored: URLs, cookies and headers stay in memory, so after process death every incomplete task becomes `NEEDS_REFRESH` while its verified checkpoint remains usable.
- Added `DownloadForegroundService` with a notification channel, progress/paused/failed notifications, a pause-all action, `registerDefaultNetworkCallback` network tracking and self-stop when no foreground work remains.
- Added the downloads surface: `DownloadsUiState`/`DownloadRowUiState` with honest determinate and indeterminate progress, per-status action sets, and a Compose screen with pause/resume/retry/cancel/remove plus bulk pause.
- Added the preview-to-queue path: a pure `DownloadPlanFactory` that produces typed plans and explicitly rejects unsupported codecs, non-HTTPS links, expired links and DASH representations it cannot address, plus a `DownloadEnqueuer` behind a narrow `PreviewDownloadStarter` boundary that probes direct sources before queueing and creates no destination for rejected work.
- Added safe export: `AndroidDownloadDestinationProvider` stages a pending `MediaStore` item on API 29+ and falls back to app-private storage on older releases, so a half-written file is never published under its final name.
- Derived output file names only from title and label metadata, never from the signed playback URL, and dropped dots from the base name so a hostile title cannot produce a traversal segment.
- Kept site adapters and Phase 5 out of Phase 4.

## Phase 4 validation

- Full command passed:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

- Result: **BUILD SUCCESSFUL** in 16m 57s; 500 tasks, 216 tests, 0 failures, 0 errors and 0 skipped.
- Test totals by module: app 58, core-browser 27, core-data 9, core-download 79, core-media 14, core-model 22, extractor-generic 7.
- Debug APK: 15,377,631 bytes; SHA-256 `553c5125b1fb91bde50ca83631281b5c986aec5d5d69993e9d9e2a01411bf944`.
- Unsigned minified release APK: 2,736,866 bytes; SHA-256 `435b0a03981642f8f0555c7b7128e824d6d4bf9cf260bbcd877706f4ce6ed0ed`.
- Covered by tests: incomplete-file safety and partial staging, process-restart recovery to `NEEDS_REFRESH` with a retained checkpoint, network-change suspension and resumption, duplicate URLs as independent records, range refusal fallback, validator mismatch restart, corruption/length mismatch rejection, cancellation cleanup of engine workspaces and destinations, and mux compatibility rejection.
- No device/emulator was available, so `MediaExtractor`/`MediaMuxer` behavior, foreground-service lifecycle under real Android process policy, notification rendering and `MediaStore` publication remain explicit runtime checks rather than claimed device validation. Low-storage behavior is covered only through the injected failure path, not a real full-disk device.

## Phase 5 active scope

- Add per-site extractors behind `:extractor-api` in `:extractor-sites`, selected by host with a clean fallback to the existing generic detector.
- Keep adapters free of credential capture, DRM circumvention and paywall bypass; a site that requires sign-in must reuse the user's own browser session only.
- Add committed offline fixtures and regression tests per adapter; no live network calls in tests.
- Do not change the download engines, and keep any site-specific behavior out of `:core-browser` and `:core-download`.

## Phase 5 progress

- 5A TikTok: implemented in `:extractor-sites` with offline fixtures for the current and legacy payloads, photo posts, private/login/region outcomes, DRM flags and changed markup.
- 5B Facebook: implemented for watch, `video.php`, `/{handle}/videos/{id}`, reels, `fb.watch` and `/share/v|r/` links. Progressive MP4 renditions and the page's own DASH manifest URL are returned with CDN expiry attached; a page whose links already expired fails as expired. The parser prefers the video node whose ID matches the requested page, so a suggested video on the same page is never returned instead.
- Both adapters are registered explicitly in `SiteAdapterModule` and covered by `ShippedAdaptersTest`, which asserts that each adapter claims only its own pages, that unclaimed pages fall through to the generic detector, and that a disabled adapter reports itself.
- Validation after 5B: `./gradlew --no-daemon --offline :extractor-api:test :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL** in 3m 30s; 124 tests, 0 failures; debug APK assembled.
- 5C (another justified public site) and 5D (YouTube risk review) are still open.
