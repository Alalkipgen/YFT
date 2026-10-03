# Phase Status

| Phase | Status | Summary |
| --- | --- | --- |
| 0 — Discovery and feasibility | COMPLETE | Architecture, support matrix, ADRs and compileable Media3/WebView spike validated locally and in CI |
| 1 — Foundation | COMPLETE | Production modules, Compose navigation shell, DI/data/media foundations, tests and CI validated on the work branch |
| 2 — Browser/detection | COMPLETE | Secure browser, layered generic detection, bounded probes, page-scoped normalization, fixtures and candidate UI validated |
| 3 — Preview/variants | COMPLETE | Bounded direct/HLS/DASH resolution, honest variants and secure Media3 preview validated |
| 4 — Download engines | COMPLETE | Typed download plans, direct/HLS/DASH transfer, mux compatibility, foreground execution, Room recovery and safe export validated |
| 5 — Site adapters | COMPLETE | TikTok, Facebook and Vimeo adapters behind the extractor API with offline fixtures, registry fallback to generic detection, and the YouTube blocker reported |
| 5E — YouTube (owner override) | COMPLETE | YouTube adapter by owner decision (ADR-005): embedded-first lookup, progressive MP4 plus M4A audio, bundled ejs solver in a sandboxed WebView, offline fixtures and tests |
| 6 — Hardening/UI | COMPLETE | Privacy and backup hardening, download preferences and Wi-Fi-only policy, Library, Detected Media, Home link entry, About notices, original palette, free-space check and storage janitor; every audit finding closed or device-only (`docs/HARDENING_AUDIT.md`) |
| 7 — Signed beta/release | COMPLETE | Version `1.0.0-beta.1`, original launcher icon and launch screen, release signing from env/untracked properties, verification/checksum/device scripts, draft-only release workflow, changelog, release notes and release process; signed by the release workflow with the owner's key and published as a pre-release on 2026-10-02. Device checks still wait for a device |
| 6R — UI redesign | COMPLETE | Every screen rebuilt to the owner's design images (`docs/design/DESIGN-NOTES.md`) on `work/phase-6-ui-redesign`, Tasks 0–9, with every feature kept; merged with Phase 7 into `main` and released as `1.0.0-beta.2` |

## Current phase state

The UI redesign (Phase 6 follow-up) is complete on `work/phase-6-ui-redesign`, created from the Phase 6 head `4bdad07`, and merged with Phase 7 (`main` at `a6bd059`) into `main`. `1.0.0-beta.1` was published on 2026-10-02; `1.0.0-beta.2` (versionCode 2) is the first build with the redesign, signed by the release workflow as a draft pre-release for the owner to review and publish. No device or emulator was available, so device checks remain open (`docs/TEST_MATRIX.md`).

## Phase 6 active scope

> Complete: 6A audit, 6B security/privacy, 6C settings/reliability, 6D UI finalization and 6E free-space/pruning are implemented with tests; the full validation matrix is recorded below.

- 6A Kickoff: branch, audit and plan (this checkpoint).
- 6B Security and privacy: backup and device-transfer exclusion, network security configuration, browsing-data clearing, notification privacy and runtime notification permission, file-name hardening.
- 6C Settings: default quality, destination, Wi-Fi-only, concurrency, mobile-data warning, theme, and data/history clearing, persisted in DataStore and applied to the queue.
- 6D UI: Home, Browser, floating detector, candidate sheet, Preview and quality picker, Downloads, Library, Settings and About, with light/dark themes, empty/loading/error states, accessibility labels and confirmations.
- 6E Reliability and performance: free-space pre-checks, network policy, recovery and list/DB behavior.
- No new download sources and no new site adapters; the download engines change only where a hardening finding requires it.

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

## Completed in Phase 5

- 5A TikTok: implemented in `:extractor-sites` with offline fixtures for the current and legacy payloads, photo posts, private/login/region outcomes, DRM flags and changed markup.
- 5B Facebook: implemented for watch, `video.php`, `/{handle}/videos/{id}`, reels, `fb.watch` and `/share/v|r/` links. Progressive MP4 renditions and the page's own DASH manifest URL are returned with CDN expiry attached; a page whose links already expired fails as expired. The parser prefers the video node whose ID matches the requested page, so a suggested video on the same page is never returned instead.
- Both adapters are registered explicitly in `SiteAdapterModule` and covered by `ShippedAdaptersTest`, which asserts that each adapter claims only its own pages, that unclaimed pages fall through to the generic detector, and that a disabled adapter reports itself.
- 5C Vimeo: implemented for `vimeo.com/{id}`, unlisted `vimeo.com/{id}/{hash}`, channel, group, album/showcase and `player.vimeo.com/video/{id}` links. The adapter reads the clip page, follows the page's own `config_url` on `player.vimeo.com` only when the page does not inline the configuration, and falls back to that same configuration when the markup changes. Progressive MP4 files are returned with the exact stated size, together with the HLS and DASH manifests of the configuration's default CDN; password-protected, private, deleted, region-blocked, DRM-protected, file-less and expired configurations each fail with their own reason. On-demand, event and other paid or live surfaces are deliberately not claimed.
- 5D YouTube: reported as a blocker instead of an adapter. `docs/YOUTUBE_RISK_REVIEW.md` and `docs/decisions/ADR-004-youtube-adapter.md` record why page-only extraction is not available (player-JavaScript transforms, per-session proof-of-origin tokens and server-driven streaming), why the behavior cannot be pinned by committed fixtures, and why the distribution exposure is the project's highest. The support-matrix row is Blocked, YouTube links stay on the generic detector, and `ShippedAdaptersTest` pins both the shipped adapter set and the unclaimed YouTube hosts.

## Phase 5 validation

- Full command passed:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

- Result: **BUILD SUCCESSFUL** in 7m; 505 tasks (200 executed, 41 from cache, 264 up-to-date); 293 tests, 0 failures, 0 errors and 0 skipped.
- Test totals by module: app 64, core-browser 27, core-data 9, core-download 79, core-media 14, core-model 22, extractor-api 19, extractor-generic 7, extractor-sites 52.
- Lint: 0 errors, 65 warnings, all dependency-version or toolchain advisories (`GradleDependency` 57, `AndroidGradlePluginVersion` 6, `DataExtractionRules` 1, `KaptUsageInsteadOfKsp` 1).
- Debug APK: 15,507,771 bytes; SHA-256 `a5666d135ad4a26b485f1a10ba99d3580d8a440fed8f95e8ce3477f5cf35d84e`.
- Unsigned minified release APK: 2,769,634 bytes; SHA-256 `5bb335218d971e351f6d03f27e41f870588307ef630d78384ad3113a5d6faf79`.
- The command needs network access because `lintDebug` resolves `com.android.tools.lint:lint-gradle`, which is not in the offline cache; every other task in it passes with `--offline`.
- Every adapter test runs offline against committed fixtures; no test performs a live network call.
- Phase 5 definition of done: registry and generic fallback work, each claimed site has real fixtures and tests, broken pages fail clearly and independently, the YouTube blocker is reported with sources, and tests, lint, debug and release builds pass. Phase 6 is not started.

## Completed in Phase 5E

- Recorded the owner's decision to support YouTube downloads (GitHub-only distribution) in `docs/decisions/ADR-005-youtube-owner-override.md`, superseding ADR-004, and added an "Owner override" section to `docs/YOUTUBE_RISK_REVIEW.md`, which stays the risk record.
- `:extractor-api`: `SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED`, `ExtractorHttpClient.postJson` and the `PlayerScriptRunner` boundary.
- `:extractor-sites` `youtube/`: single-video URL matching, one-file client profiles, a player-response parser and an embedded-first extractor that returns progressive MP4 streams with audio plus one AAC M4A stream, with tiered verdicts, expiry checks and plausibility checks on solved values. Covered by 13 player-response fixtures, two page fixtures and 39 tests.
- App: `YouTubePlayerScriptRunner`, `EjsSolverProtocol`, `SolverPageRoutes` and `WebViewSolverEngine` run the unmodified, SHA-256-pinned yt-dlp ejs 0.8.0 solver in a fresh, sandboxed offscreen WebView; `OkHttpExtractorClient` drops credentials on cross-site redirects; the adapter is registered behind `BuildConfig.YOUTUBE_ADAPTER_ENABLED`.
- `scripts/update-youtube-solver.sh` and `scripts/verify-youtube-solver.mjs` keep the solver current; `docs/THIRD_PARTY_NOTICES.md` lists its licenses and hashes.
- The checkpoint workflow uploads the debug APK as `yft-debug-apk` so the owner can sideload-test.
- `:core-browser`, `:core-download` and the download engines are unchanged.

## Phase 5E validation

- Full command passed:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

- Result: **BUILD SUCCESSFUL**; 505 tasks (fresh run 8m 52s); 359 tests, 0 failures, 0 errors and 0 skipped.
- Test totals by module: app 88, core-browser 27, core-data 9, core-download 79, core-media 14, core-model 22, extractor-api 22, extractor-generic 7, extractor-sites 91.
- Lint: 0 errors, 64 warnings, all dependency-version or toolchain advisories (`GradleDependency` 57, `AndroidGradlePluginVersion` 6, `DataExtractionRules` 1).
- Debug APK: 15,617,519 bytes; SHA-256 `9a3394cca543008c4b66e78a6df46a75e6c523c757597cdc3e6f260162fdc3f9`.
- Unsigned minified release APK: 2,837,516 bytes; SHA-256 `aaed2108ef23afc173cd847c5213ab9aa257743bc00aa4cfbd3a40b49c393d3a`.
- Bundled solver: 34 public vectors passed and today's live player was solved by `scripts/verify-youtube-solver.mjs`; the exact solver page ran in headless Chromium 153 with no outside request.
- Not verified: the solver WebView and live YouTube stream URLs on a device. This sandbox has no `/dev/kvm`, and its datacenter IP receives bot checks. Owner test steps are in `docs/HANDOFF.md`.

## Completed in Phase 6

- 6A: hardening audit with severities and statuses in `docs/HARDENING_AUDIT.md`; no High finding.
- 6B security and privacy: backup and device-transfer exclusion, no-cleartext network security configuration, private notifications with a public count-only version, runtime notification permission, bidi-safe file names, Clear browsing data (also clearing in-memory media lists).
- 6C settings and reliability: DataStore download preferences (quality, location, Wi-Fi only, mobile-data confirmation, 1–4 concurrent downloads) applied by `DownloadPolicyController`; unique app-storage names; Preview quality preselection and mobile-data confirmation.
- 6D UI: Library with in-app playback, open/share and confirmed delete through a read-only app-storage provider; Home link field with tap-only Paste; icon buttons with content descriptions; Detected Media backed by a memory-only store; About with scope, privacy and third-party license texts; an original light/dark palette with a contrast test.
- 6E reliability and performance: free-space pre-check for known sizes, Downloads network banner, a once-per-process storage janitor for stale `.part` files, orphan workspaces and finished-record growth, and a shared workspace-name helper.

## Phase 6 validation

- `./gradlew --no-daemon lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` (separate invocations on a 4 GiB machine): **BUILD SUCCESSFUL**.
- 454 tests, 0 failures: app 175, core-browser 27, core-data 11, core-download 81, core-media 14, core-model 26, extractor-api 22, extractor-generic 7, extractor-sites 91.
- Lint: 0 errors, 63 warnings (`GradleDependency` 57, `AndroidGradlePluginVersion` 6).
- Debug APK 15,848,398 bytes, SHA-256 `0e33d98aa4f59e539c9d0211fc197af6b0484f1ebc566f451d6fe9fbeef5d5a9`; unsigned minified release APK 2,976,324 bytes, SHA-256 `efc35d59823fbfedaff6288b254451bc6485b5fc49bf4e9b391cfd1806050ef8`; R8 kept the `@JavascriptInterface` solver bridge method name `post` (class renamed to `r3.l`).
- No device/emulator was available (`/dev/kvm` missing); device-only checks are listed in `docs/TEST_MATRIX.md`.

## Completed in Phase 7

- Phase 6 re-verified first (full local matrix; CI `validate` success on `4bdad07`); its known issues are carried into `CHANGELOG.md` and the release notes.
- Release identity: `yft.versionName=1.0.0-beta.1`, `yft.versionCode=1` in `gradle.properties`, validated by `app/build.gradle.kts`; display name `Video Downloader` and application ID `com.alal.yft` unchanged (the owner must reconfirm the ID before the first publication).
- Original launcher icon: adaptive icon with a themed monochrome layer, `roundIcon`, legacy PNGs for Android 7.x at five densities from `scripts/generate-launcher-icons.py`; launch window `Theme.Yft.Launch` with Android 12+ splash attributes in `values-v31`; `MainActivity` switches to `Theme.Yft`. No new dependency.
- Release signing from `YFT_RELEASE_*` environment variables or the untracked `keystore.properties` (`keystore.properties.example`): partial configuration and a missing keystore fail, `-Pyft.requireReleaseSigning=true` fails without signing, no debug-key fallback, v2 + v3 signatures.
- `scripts/verify-release-apk.sh`, `scripts/release-prep.sh` (fail-fast signing check, uncached clean/lint/tests/signed release, verification and `dist/<version>/` staging) and `scripts/device-smoke-test.sh` (adb install, launch and upgrade checks).
- `.github/workflows/release-draft.yml` (signed build from secrets, certificate pin, draft pre-release, publish only with `publish` and `ALLOW_RELEASE=true`); `checkpoint-validation.yml` now builds and checks the unsigned release APK.
- `CHANGELOG.md`, `docs/release/1.0.0-beta.1.md`, `docs/RELEASE.md`, README install section, `AppIdentityTest`.

## Phase 7 validation

- `bash scripts/release-prep.sh --expected-cert-sha256 <throwaway key A> --out-dir /tmp/…` with throwaway key A in `/tmp` (source commit `36f3395`): signing pre-check, `clean` (11 s), `lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test` (**BUILD SUCCESSFUL** 4m 5s, 270 tasks executed, none from cache) and `:app:assembleRelease -Pyft.requireReleaseSigning=true` (**BUILD SUCCESSFUL** 4m 40s); 550 s in total.
- 459 tests, 0 failures: app 180, core-browser 27, core-data 11, core-download 81, core-media 14, core-model 26, extractor-api 22, extractor-generic 7, extractor-sites 91.
- Lint: 0 errors; warnings unchanged from Phase 6 (app: `GradleDependency` 57, `AndroidGradlePluginVersion` 6; core-data: `KaptUsageInsteadOfKsp` 1).
- Signed test APK: 3,011,624 bytes, SHA-256 `77a5fde4f37efcc9d596cc5277adbd056cab3244952e94361a021344de89d60f`; `apksigner` verified v2 + v3, one signer, expected certificate, not debuggable, zip-aligned; `sha256sum -c SHA256SUMS` OK; R8 kept the solver bridge method `post`. Unsigned release APK 2,999,336 bytes. These test-key APKs are not release artifacts.
- Upgrade compatibility (static): a signed versionCode 2 build (`-Pyft.versionCode=2`) passes `verify-release-apk.sh --previous-apk` against versionCode 1; a lower or equal versionCode, a previous APK re-signed with throwaway key B, an unexpected certificate and an APK re-signed with the Android debug key all fail. On-device upgrade NOT RUN.
- Gradle signing paths, verification negatives, actionlint 1.7.7 and shellcheck 0.10.0: see the Phase 7 table in `docs/TEST_MATRIX.md`. CI `validate` (now including the unsigned release check) passed on `b5797de`.
- Not run: the owner-signed build, the `Release draft` workflow and every device check (no `/dev/kvm`, no device).

## Phase 7 owner actions

1. Reconfirm `com.alal.yft`, create the permanent keystore and back it up (`docs/RELEASE.md` §1).
2. Add the four `YFT_RELEASE_*` secrets and the `YFT_RELEASE_CERT_SHA256` variable, or sign locally with `scripts/release-prep.sh`.
3. Run the device checks in `docs/RELEASE.md` §4 with the signed APK.
4. Push tag `v1.0.0-beta.1` (or run `Release draft`) to create the draft; publish only with `ALLOW_RELEASE=true`.
5. Merge the work branches into `main` when satisfied.

## Completed in the UI redesign

- Task 0 `81592fd` Home re-checked against renders · 1 `2ce6e4d` design system (tokens, Plus Jakarta Sans, Material Symbols Rounded, `ui/components/`) · 2 `3673eb2` app icon and shell (bottom bar with the Downloads badge) · 3 `9fa41c2` Home, Promptbox states, Your sites, Recent and the headless link inspector · 4 `548d205` Browser and the "Found on this page" sheet · 5 `dcd1019` Download as sheet · 6 `4a64606` Downloads · 7 `59b88fb` Library, mini player, full-screen player and file thumbnails · 8 `e7df19e` Settings, About and Licenses · 9 `328d2fb` Night, large-text and accessibility pass with the final renders.
- Decisions, the reference images and the remaining differences: `docs/design/DESIGN-NOTES.md`.
- Merge with `main`: the redesign's app icon replaced Phase 7's (same resource names, adaptive and legacy PNGs, `scripts/generate-launcher-icons.py` now reads both vectors); Phase 7's launch theme, signing, scripts and workflows are unchanged, the launch color is Deep Teal; version `1.0.0-beta.2` / versionCode 2.

## UI redesign validation

- See the "UI redesign automated checks" table in `docs/TEST_MATRIX.md` for the per-screen tests, the accessibility audit and the renders.
- Not run: every device check (no `/dev/kvm`, no device), including the new icon, launch screen, Night theme, TalkBack and large text on a phone.
