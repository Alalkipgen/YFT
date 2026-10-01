# Phase Status

| Phase | Status | Summary |
| --- | --- | --- |
| 0 — Discovery and feasibility | COMPLETE | Architecture, support matrix, ADRs and compileable Media3/WebView spike validated locally and in CI |
| 1 — Foundation | COMPLETE | Production modules, Compose navigation shell, DI/data/media foundations, tests and CI validated on the work branch |
| 2 — Browser/detection | COMPLETE | Secure browser, layered generic detection, bounded probes, page-scoped normalization, fixtures and candidate UI validated |
| 3 — Preview/variants | NOT STARTED | — |
| 4 — Download engines | NOT STARTED | — |
| 5 — Site adapters | NOT STARTED | — |
| 6 — Hardening/UI | NOT STARTED | — |
| 7 — Signed beta/release | NOT STARTED | — |

## Current phase state

Phase 2 is complete on `work/phase-2-browser-detection`, created from Phase 1 completion commit `4f5e06a`. Phase 3 has not started. Do not begin preview/variant work or merge a work branch into `main` without explicit approval and green validation.

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
- Completion-commit remote CI is confirmed after the documentation checkpoint is pushed.
- No physical Android device/emulator was available, so real Android WebView callback/rendering behavior remains an explicit runtime verification item rather than a claimed on-device result.
