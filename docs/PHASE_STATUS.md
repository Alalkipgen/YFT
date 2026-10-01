# Phase Status

| Phase | Status | Summary |
| --- | --- | --- |
| 0 — Discovery and feasibility | COMPLETE | Architecture, support matrix, ADRs and compileable Media3/WebView spike validated locally and in CI |
| 1 — Foundation | COMPLETE | Production modules, Compose navigation shell, DI/data/media foundations, tests and CI validated on the work branch |
| 2 — Browser/detection | IN PROGRESS | Secure browser and generic detection work started from the green Phase 1 completion commit |
| 3 — Preview/variants | NOT STARTED | — |
| 4 — Download engines | NOT STARTED | — |
| 5 — Site adapters | NOT STARTED | — |
| 6 — Hardening/UI | NOT STARTED | — |
| 7 — Signed beta/release | NOT STARTED | — |

## Current phase

Phase 2 is active on `work/phase-2-browser-detection`, created from Phase 1 completion commit `4f5e06a`. Work is limited to the secure browser and generic detection scope. Do not start Phase 3 or merge a work branch into `main` without explicit approval and green validation.

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


## Phase 2 active scope

- Secure WebView URL/address controls, navigation, loading/error state and page-scoped sessions.
- Generic observations from DownloadListener, read-only DOM probing and HTTP(S) request URLs/headers.
- Direct-media, HLS and DASH hints plus bounded metadata probes.
- Candidate normalization, deduplication, limits, debounce and navigation cleanup.
- Floating media-found affordance and an honest candidate bottom sheet.
- No site adapters, preview/variant implementation or download engines.
