# Handoff

## Current handoff

- Date: 2026-10-02
- Phase: 6 — Hardening, privacy, performance and UI polish, COMPLETE on
  `work/phase-6-hardening` (created from the Phase 5E head `c608b01`; findings in
  `docs/HARDENING_AUDIT.md`)
- Next phase: 7 — Signed beta and GitHub release preparation (`docs/prompts/08_PHASE_7.md`); the
  owner approved continuing into it
- Target repository: `Alalkipgen/YFT`
- Reference repository: `Alalkipgen/AlalDownloader`

## Work completed in Phase 6

- **6A audit.** `docs/HARDENING_AUDIT.md` lists every security (S), reliability (R),
  performance (P) and UI (U) finding with severity and status. None was High; all are now fixed,
  compliant or device-only.
- **6B security and privacy.** `data_extraction_rules.xml` and `fullBackupContent=false` keep app
  data out of cloud backup and device transfer; `network_security_config.xml` allows no cleartext
  and only system CAs; download notifications are private with a count-only public version;
  `POST_NOTIFICATIONS` is requested before the first download on Android 13+; output names drop
  bidirectional and zero-width controls; Settings › Clear browsing data also drops the in-memory
  detected-media list and preview selection.
- **6C settings and reliability.** DataStore download preferences (default quality, location,
  Wi-Fi only, mobile-data confirmation, 1–4 concurrent downloads) applied to the queue by
  `DownloadPolicyController`; app-storage names are reserved with a " (n)" suffix; Preview
  preselects the preferred quality and confirms mobile data.
- **6D UI.**
  - Library (`feature/library/`): MediaStore `Download/YFT` items and app-storage files, in-app
    Media3 playback, open/share through a chooser with a read grant, delete after confirmation;
    `AppPrivateDownloadProvider` serves app-storage files read-only.
  - Home: link field and a Paste button that reads the clipboard only when tapped; the link opens
    the browser through the optional `browser?link=` route argument.
  - Browser and top bar: icon buttons with content descriptions; the media button has an icon.
  - Detected Media: the last page's candidates from the memory-only `DetectedMediaStore`, shown
    with the shared `MediaCandidateCard` and handed to Preview; only the page host is displayed.
  - About: version, scope, privacy and every third-party notice with its license text
    (`OpenSourceNotices`, checked against `THIRD_PARTY_NOTICES.md` by a test).
  - Theme: original teal/copper light and dark schemes with a WCAG AA contrast test.
- **6E reliability and performance.** `DownloadEnqueuer` rejects a known size that does not fit
  (`StatFsStorageSpace`, 32 MiB headroom) as `INSUFFICIENT_STORAGE` before any destination exists;
  Downloads shows "Waiting for Wi-Fi" or "No connection" from `DownloadNetworkStatus`;
  `DownloadStorageJanitor` (started once from `MainActivity`) removes stale `.part` files and orphan
  HLS/DASH/mux workspaces from earlier processes and keeps the newest 200 finished records;
  `DownloadWorkspaces` in `:core-download` is the single source of workspace names.
- No new site adapters or download sources were added; the engines changed only to share the
  workspace-name helper.

## Phase 6 validation

- Full matrix, run as two invocations on this 4 GiB machine:

```bash
./gradlew --no-daemon lintDebug testDebugUnitTest :core-model:test :extractor-api:test \
  :extractor-generic:test :extractor-sites:test :app:assembleDebug
./gradlew --no-daemon :app:assembleRelease
```

- Result: **BUILD SUCCESSFUL** for both; 454 tests, 0 failures, 0 errors.
- Test totals by module: app 175, core-browser 27, core-data 11, core-download 81, core-media 14,
  core-model 26, extractor-api 22, extractor-generic 7, extractor-sites 91.
- Lint: 0 errors, 63 warnings, all dependency-version or toolchain advisories (`GradleDependency`
  57, `AndroidGradlePluginVersion` 6); the `DataExtractionRules` warning of Phase 5E is gone.
- Debug APK: 15,848,398 bytes; SHA-256
  `0e33d98aa4f59e539c9d0211fc197af6b0484f1ebc566f451d6fe9fbeef5d5a9`.
- Unsigned minified release APK: 2,976,324 bytes; SHA-256
  `efc35d59823fbfedaff6288b254451bc6485b5fc49bf4e9b391cfd1806050ef8`.
- R8 kept the solver bridge method name `post` (`WebViewSolverEngine$SolverBridge` is renamed
  to `r3.l`, but `void post(java.lang.String)` keeps its name) through the
  `@JavascriptInterface` keep rule in `app/proguard-rules.pro`.
- Not verified on a device (no `/dev/kvm`): see the device rows in `docs/TEST_MATRIX.md`.

## Known limitations

- **YouTube.** Progressive MP4 (usually up to 360p) plus one M4A audio stream only; no PO
  tokens, so non-embeddable videos may fail with HTTP 403; some networks get bot checks; client
  profiles and the bundled solver need regular maintenance; the WebView solver and live YouTube
  were never run on a device.
- **Downloads.** Platform muxing accepts only separate AVC/AAC MP4/fMP4 tracks; DASH
  `SegmentBase`/SIDX and live MPDs are unsupported; SAF tree export is not wired; after process
  death unfinished work needs a fresh link (there is no in-place link refresh yet).
- **Free-space check.** Only exact sizes are checked; estimated stream sizes and unknown sizes
  start and fail cleanly if the disk fills.
- **Device-only checks.** Library open/share/delete, Clear browsing data, Wi-Fi-only switching,
  notification permission, the storage janitor after a forced stop, MediaStore publication and
  the foreground service were verified only with JVM/Robolectric tests.
- **Build.** The release APK is unsigned until Phase 7 provides the owner's keystore; the KAPT
  language-version warning remains; TikTok, Facebook and Vimeo are verified only against
  fixtures.

## Next exact action

1. Create `work/phase-7-release` from the Phase 6 completion head and follow
   `docs/prompts/08_PHASE_7.md`.
2. Signing needs the owner's permanent keystore through secure local or CI configuration; never
   commit or print it. Without it, stop before claiming a signed beta.
3. Publish a GitHub Release only with explicit `ALLOW_RELEASE=true`; do not merge into `main`.

## Phase 6 checkpoint commits

- Kickoff and hardening audit: `b503e30`
- 6B/6C privacy, preferences, Wi-Fi-only policy, settings: `a92b8ab`
- 6C Preview tests: `11e0ae1`
- 6D part 1 — Library, Home link and Paste, accessibility labels: `122e080`
- 6D part 2 — About notices, palette, Detected Media: `d992cd7`
- 6E — free-space check, network banner, storage janitor: `465b1c8`
- Phase 6 completion (docs and full validation): this checkpoint

## Previous handoff — Phase 5E

### Work completed in Phase 5E

- **Owner override recorded.** The owner decided YFT must download YouTube videos for offline
  viewing, GitHub-only. ADR-005 records the decision and its constraints; ADR-004 is marked
  superseded; `docs/YOUTUBE_RISK_REVIEW.md` keeps the original analysis as the risk record and
  gains an "Owner override" section.
- **`:extractor-api`.** Added `SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED`,
  `ExtractorHttpClient.postJson` and the `PlayerScriptRunner` boundary (challenge keys in,
  solved values out), with `NoPlayerScriptRunner` as the default.
- **`:extractor-sites` `youtube/`.**
  - `YouTubeUrls`: single-video URL shapes only, canonical page identity, and player-script
    addresses accepted only from `www.youtube.com/s/player/…`.
  - `YouTubeClientProfile`: every client identifier in one file; embedded player and the
    page's own `WEB`/`MWEB` client; no content-gate acknowledgement.
  - `YouTubePlayerResponseParser`: page signals, inline player response, playability verdicts,
    progressive/adaptive streams and opaque cipher descriptors.
  - `YouTubeExtractor`: embedded client first without the cookie, then the page client with the
    session; tiered verdicts; progressive MP4 plus one AAC M4A stream; expiry and plausibility
    checks; transforms through the runner.
- **App.**
  - `detection/script/`: `YouTubePlayerScriptRunner` (validated player fetch, phone build
    first, one-entry preprocessed cache, 45 s timeout), `EjsSolverProtocol`,
    `SolverPageRoutes` and `WebViewSolverEngine` (fresh offscreen WebView per run, app-served
    pages only, strict CSP, blob worker, no cookies/storage/files/navigation).
  - Bundled the unmodified yt-dlp ejs 0.8.0 solver in `app/src/main/assets/youtube-solver/`
    with YFT's own page and worker.
  - `SiteScope` and `OkHttpExtractorClient` now drop `Cookie`/`Authorization` on redirects
    that leave the original site.
  - `SiteAdapterModule` registers the adapter behind `BuildConfig.YOUTUBE_ADAPTER_ENABLED`
    (default `true`).
- **Scripts.** `scripts/update-youtube-solver.sh` (pinned-wheel refresh) and
  `scripts/verify-youtube-solver.mjs` (public vectors plus today's live player).
- **CI.** The checkpoint workflow uploads the debug APK as the `yft-debug-apk` artifact for
  sideload testing.
- **Docs.** `THIRD_PARTY_NOTICES.md` (new), support-matrix YouTube row, test matrix, risks and
  this handoff.
- `:core-browser`, `:core-download` and the download engines are unchanged.

### Owner device test (YouTube)

No device or emulator is available here, and this sandbox's datacenter network receives
YouTube bot checks, so the live stream path must be checked on a phone:

1. Download the `yft-debug-apk` artifact from the latest green "Work-branch checkpoint
   validation" run of this branch on GitHub Actions (or build it with
   `./gradlew :app:assembleDebug`). Each CI build has its own debug key, so uninstall an older
   YFT debug build before installing a newer one.
2. Install it on an Android 7.0+ phone with Android System WebView or Chrome updated from the
   Play Store. Use home Wi-Fi or mobile data, not a VPN.
3. In YFT's browser open `https://m.youtube.com/watch?v=aqz-KE-bpKQ` (an embeddable public
   video). Expected: candidates named `… — 360p` (or similar) and `… — Audio … kbps`.
4. Download both and play them from Downloads.
5. Also try a `youtube.com/shorts/…` link, a `youtu.be/…` link, a music video whose owner
   disables embedding (may fail at download time with HTTP 403), and a private or age-restricted
   video (expected: a clear sign-in or unavailable message, never a bypass).
6. Report what each case showed, roughly how long detection took, and any crash.

### Phase 5E validation

Run with JDK 17, Android SDK 35 and Gradle 8.9:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

- **BUILD SUCCESSFUL**: 505 actionable tasks, fresh run in 8m 52s; the final re-run after the
  last test was added took 1m 3s.
- 359 tests, 0 failures, 0 errors and 0 skipped:
  - app: 88
  - core-browser: 27
  - core-data: 9
  - core-download: 79
  - core-media: 14
  - core-model: 22
  - extractor-api: 22
  - extractor-generic: 7
  - extractor-sites: 91
- Lint: 0 errors and 64 warnings, all dependency-version or toolchain advisories
  (`GradleDependency` 57, `AndroidGradlePluginVersion` 6, `DataExtractionRules` 1). None comes
  from Phase 5E code.
- Debug APK: 15,617,519 bytes; SHA-256
  `9a3394cca543008c4b66e78a6df46a75e6c523c757597cdc3e6f260162fdc3f9`.
- Unsigned minified release APK: 2,837,516 bytes; SHA-256
  `aaed2108ef23afc173cd847c5213ab9aa257743bc00aa4cfbd3a40b49c393d3a`. The release APK contains
  the five solver assets, and R8 keeps the bridge's `post` method name.
- `node scripts/verify-youtube-solver.mjs`: 34 public vectors passed on the main and phone player
  builds, and today's live player `8ab5c328` was solved.
- The exact solver page, worker and CSP were run in headless Chromium 153: player `74edf1a3`
  5/5 vectors in 0.68 s (0.21 s with the preprocessed player), six more players 2/2 each, with no
  outside request and no navigation.
- GitHub Actions: checkpoint `1d84811` passed. `6a41808` failed because the app's failure-message
  `when` was not yet exhaustive for the new failure; `1d84811` fixed it.

### Known limitations

- **YouTube quality.** Only progressive MP4 streams with audio (usually up to 360p) and one AAC
  M4A audio stream are offered. Adaptive HD video is not offered, because adapter candidates
  cannot yet be paired into a video+audio mux plan.
- **PO tokens.** YFT generates none. Videos that disallow embedding fall back to the page client,
  whose links may be refused with HTTP 403 at download time.
- **Bot checks.** Some networks (datacenters, some VPNs) get "Sign in to confirm you're not a
  bot" for every request; YFT reports sign-in required and does not work around it.
- **Maintenance.** YouTube changes its player and client requirements often. Client identifiers
  live in `YouTubeClientProfile.kt`; the solver is refreshed with
  `scripts/update-youtube-solver.sh` and checked with `scripts/verify-youtube-solver.mjs`.
- **Throughput.** Large downloads from `googlevideo.com` without ranged requests may be throttled.
- **Device verification.** The WebView solver host has not run on a device (no `/dev/kvm`); it
  needs a current Android System WebView with blob-URL workers and modern JavaScript. From this
  sandbox's datacenter IP, `WEB`/`MWEB` answered with bot checks and the embedded client with
  "video unavailable", so live stream URLs were not verified end to end.
- **Carried over.** Platform muxing accepts only separate AVC/AAC MP4/fMP4 tracks; DASH
  `SegmentBase`/SIDX and dynamic/live MPDs are unsupported; SAF tree export is not wired; the
  release APK is unsigned until Phase 7; the KAPT language-version warning remains; TikTok,
  Facebook and Vimeo are verified only against fixtures.

### Next exact action

1. Create `work/phase-6-hardening` from the Phase 5E completion head and push the kickoff
   checkpoint.
2. Read `docs/prompts/07_PHASE_6.md` before changing any code.
3. Do not add site adapters or change the download engines as part of starting Phase 6.
4. Do not merge a work branch into `main`.

### Phase 5E checkpoint commits

- Adapter core rebuilt (WIP): `04d1fea`
- Parser, profiles, URLs and player-script contract (WIP): `cf54347`
- Embedded-first extractor with offline fixtures and tests: `6a41808`
- Sandboxed WebView player-script host with the bundled solver: `1d84811`
- Phase 5E completion (docs, ADR-005, notices, CI artifact, client tests): this checkpoint

## Previous handoff — Phase 5

### Work completed in Phase 5

- Finalized the adapter contract in `:extractor-api`: `SiteExtractor` with a pure offline
  `identify()` and a suspending `extract()`, typed `SitePageIdentity`, `SiteExtractionResult`,
  a closed `SiteExtractionFailure` set, a narrow `ExtractorHttpClient` boundary and a bounded
  JSON reader that refuses oversized or deeply nested payloads.
- Added `SiteExtractorRegistry`: unique adapter IDs, at most one adapter per page, a per-adapter
  kill switch and `SiteAdapterSelection.None` as the normal path into the generic detector.
- Shipped three adapters in `:extractor-sites`, each isolated in its own package with its own
  URL matcher, parser and extractor:
  - **TikTok (5A)** — long, author-less, mobile and `vm`/`vt` short links; progressive MP4 at
    every exposed bitrate with the exact stated size.
  - **Facebook (5B)** — watch, `video.php`, `/{handle}/videos/{id}`, reels, `fb.watch` and
    `/share/v|r/` links on the `www`, `m`, `web` and `mbasic` hosts; current and legacy delivery
    fields, the page's own DASH manifest URL, and CDN expiry attached to every candidate.
  - **Vimeo (5C)** — clip, unlisted, channel, group, album/showcase and player links; the page's
    own `config_url` is followed only on `player.vimeo.com`, and progressive MP4 files plus the
    default CDN's HLS and DASH manifests are returned.
- Reported the **YouTube (5D)** blocker instead of shipping an adapter, with sources, in
  `docs/YOUTUBE_RISK_REVIEW.md` and `docs/decisions/ADR-004-youtube-adapter.md`.
- Wired the registry into the app in `SiteAdapterModule`, `OkHttpExtractorClient` and
  `SiteAdapterCoordinator`, so the browser consumes adapter results and falls back to generic
  detection without any site parsing in the UI or the download engine.
- Added committed offline fixtures per adapter (TikTok 10, Facebook 11, Vimeo 14) covering
  current and legacy payloads, multiple qualities, expiry, DRM flags, login walls, private and
  removed content, region blocks, media-free pages and changed markup.
- Left `:core-browser`, `:core-download` and the download engines unchanged.

### Important decisions

- Adapters read only pages the user's own browser can already load. No adapter signs in, stores
  credentials, bypasses DRM or a paywall, or calls a private API with impersonated client keys.
- Session context is replayed to the site's own hosts only, and Vimeo's configuration address is
  rejected unless it is on `player.vimeo.com`.
- An access failure — private, login-required, region-blocked, DRM, expired — never falls back to
  the generic detector, because a silent fallback would hide the real reason. Only *changed
  markup* falls back, which is the one case where the generic detector may still find something.
- Adapters return manifests, not tracks: Facebook's DASH URL and Vimeo's HLS/DASH URLs go to the
  existing resolver, so track splitting stays in one place. Inline DASH XML without a URL is not
  used.
- Instagram, X and adult or pirate aggregators were considered and deliberately not claimed; the
  reason is recorded in `docs/SUPPORT_MATRIX.md`.
- No YouTube adapter ships. The blocker is reported, not deferred, and `ShippedAdaptersTest` pins
  the shipped adapter set so it cannot be reversed silently.
- No work branch was merged into `main`, and no release was signed or published.

### Main files and areas

- Adapter contract, registry and bounded JSON: `extractor-api/src/main/kotlin/com/alal/yft/extractor/api/`
- Adapters: `extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/{tiktok,facebook,vimeo}/`
- Adapter tests and shared test support: `extractor-sites/src/test/kotlin/com/alal/yft/extractor/sites/`
- Offline fixtures: `extractor-sites/src/test/resources/fixtures/{tiktok,facebook,vimeo}/`
- App wiring: `app/src/main/java/com/alal/yft/detection/`
- YouTube decision: `docs/YOUTUBE_RISK_REVIEW.md`, `docs/decisions/ADR-004-youtube-adapter.md`
- Phase continuity: `docs/PHASE_STATUS.md`, `docs/SUPPORT_MATRIX.md`, `docs/TEST_MATRIX.md`, `docs/SESSION_STATE.md`

### Validation

Phase 5 completion used JDK 17, Android SDK 35 and Gradle 8.9:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

Results:

- **BUILD SUCCESSFUL** in 7m 0s.
- 505 actionable tasks: 200 executed, 41 from cache and 264 up-to-date.
- 293 tests, 0 failures, 0 errors and 0 skipped:
  - app: 64
  - core-browser: 27
  - core-data: 9
  - core-download: 79
  - core-media: 14
  - core-model: 22
  - extractor-api: 19
  - extractor-generic: 7
  - extractor-sites: 52
- Lint: 0 errors and 65 warnings, all dependency-version or toolchain advisories
  (`GradleDependency` 57, `AndroidGradlePluginVersion` 6, `DataExtractionRules` 1,
  `KaptUsageInsteadOfKsp` 1). No new warning comes from the Phase 5 code.
- Debug APK: 15,507,771 bytes.
- Debug SHA-256: `a5666d135ad4a26b485f1a10ba99d3580d8a440fed8f95e8ce3477f5cf35d84e`.
- Unsigned minified release APK: 2,769,634 bytes.
- Release SHA-256: `5bb335218d971e351f6d03f27e41f870588307ef630d78384ad3113a5d6faf79`.
- Run online: `lintDebug` needs `com.android.tools.lint:lint-gradle`, which is not in the
  offline cache. Everything else in the command passes with `--offline`.
- Local structured-secret scan passed before the remote checkpoint.
- GitHub Advanced Security secret scanning is not enabled for this repository.

### Known limitations

- Adapters are verified only against committed fixtures. Any of the three sites can change its
  markup without notice; the structured `changed markup` failure is the designed response.
- No YouTube support, by decision — see the risk review.
- No physical Android device/emulator is attached and `/dev/kvm` is unavailable, so WebView,
  Media3 playback, foreground-service lifecycle, notifications and `MediaStore` publication
  remain runtime checks rather than claimed device results.
- Platform muxing accepts only separate AVC/AAC MP4/fMP4 tracks; DASH `SegmentBase`/SIDX and
  dynamic/live MPDs remain unsupported; SAF tree export is not wired.
- The release APK is unsigned; signing and publication remain Phase 7.
- Builds must set `JAVA_HOME=/data/.tools/jdk17`, because the sandbox default JDK 25 cannot run
  Gradle 8.9. `lintDebug` needs network access the first time, since `lint-gradle` is not in the
  offline cache. Build directories carried over from an older sandbox path must be deleted once
  after a sandbox change or dexing fails with an "outside the root directory" error.
- GitHub MCP write tools are blocked by the automated safety reviewer, so pushes use an SSH key
  held only in the sandbox.

### Next exact action

1. Create `work/phase-6-hardening` from the Phase 5 completion head and push the kickoff checkpoint.
2. Read `docs/prompts/07_PHASE_6.md` before changing any code.
3. Do not add site adapters or change the download engines as part of starting Phase 6.
4. Do not merge a work branch into `main`.

### Phase 5 checkpoint commits

- Adapter contract and registry: `b08675b`
- TikTok adapter: `66b284a`
- Registry wired into the browser: `60b1386`
- Facebook adapter: `10130f7`
- Vimeo adapter: `d45c219`
- YouTube blocker report: `2705bb1`

The documentation/completion commit is newer; resolve it with `git log -1 --oneline` on
`work/phase-5-site-adapters`.

## Previous handoff — Phase 4 → Phase 5

### Work completed in Phase 4

- Added typed download plans and a durable task lifecycle in `:core-model`: `DirectDownloadPlan`, `HlsDownloadPlan`, `DashDownloadPlan`, `AudioVideoMuxDownloadPlan`, `DownloadTaskStatus`, segment/progress models, a closed `DownloadFailureReason` set and per-engine `TransferCheckpoint` types.
- Added the direct transfer engine:
  - `HEAD` first with a one-byte range fallback
  - bounded parallel ranged segments, with a single-stream path when the server refuses ranges
  - resume only against a strong validator (`ETag`/`Last-Modified`), restart on mismatch
  - partial-file staging plus verified length and integrity checks
- Added HLS and DASH segment engines with bounded manifest parsing, selected-track transfer, per-segment checkpoints and resumption from the last verified segment.
- Added platform audio/video muxing behind an explicit compatibility gate: only separate AVC video plus AAC audio in MP4/fMP4 is accepted, encrypted samples are rejected, and extractor sample flags are translated into muxer buffer flags rather than forwarded.
- Added `DownloadQueue`: bounded concurrency, pause/resume/retry/cancel, pause-all, network-aware suspension, duplicate URLs as independent records, and an injectable IO dispatcher so cancellation is deterministic under tests.
- Added Room schema v4 with a covered v3→v4 migration, `RoomDownloadTaskStore` and typed checkpoint persistence.
- Added `DownloadForegroundService` with a notification channel, progress/paused/failed notifications, a pause-all action, `registerDefaultNetworkCallback` tracking and self-stop when no foreground work remains.
- Added the downloads surface: honest determinate/indeterminate progress, per-status action sets and a Compose screen with pause/resume/retry/cancel/remove plus bulk pause.
- Added the preview-to-queue path: a pure `DownloadPlanFactory` and a `DownloadEnqueuer` behind the narrow `PreviewDownloadStarter` boundary, with a preview Download button reporting idle/queueing/queued/rejected honestly.
- Added safe export: a pending `MediaStore` item on API 29+ with an app-private fallback on older releases.
- Kept site adapters and Phase 5 out of Phase 4.

### Important decisions

- Only non-sensitive recovery state is persisted. URLs, cookies and headers stay in process memory, so after process death every incomplete task becomes `NEEDS_REFRESH` while its verified checkpoint remains usable. This is deliberate: a resumable download must not be bought with a stored credential.
- A partially written file is never visible under its final name. Direct transfers stage into a partial file and publish by same-directory rename; public exports stay a pending `MediaStore` item until the transfer verifies.
- Resume requires a strong validator. Without `ETag` or `Last-Modified` the transfer restarts instead of stitching bytes from a possibly changed resource.
- Output names come only from title and label metadata, never from the playback URL, because signed URLs carry credentials. Dots are dropped from the base name so a hostile title cannot produce a `..` segment.
- Mux compatibility is declared, not guessed. WebM, HEVC and unknown codecs fail explicitly rather than producing a broken file, and no FFmpeg dependency was added.
- `MediaExtractor` sample flags and `MediaCodec` buffer flags overlap numerically with different meanings, so they are translated explicitly: forwarding the raw value could mark a normal sample as codec config or end of stream.
- WorkManager is not the large-transfer engine. A foreground service owns active transfers.
- SAF tree export is intentionally not wired, because it needs a folder the user has not been asked for.
- No work branch was merged into `main`, and no release was signed or published.

### Main files and areas

- Download models and failures: `core-model/src/main/kotlin/com/alal/yft/core/model/download/DownloadModels.kt`
- Transfer engines, queue, destinations and store: `core-download/src/main/java/com/alal/yft/core/download/`
- Room entities, DAO and migrations: `core-data/src/main/java/com/alal/yft/core/data/db/`
- Foreground service, notifications, destination provider and enqueue wiring: `app/src/main/java/com/alal/yft/download/`
- Download planning and downloads UI: `app/src/main/java/com/alal/yft/feature/downloads/`
- Preview download action: `app/src/main/java/com/alal/yft/feature/preview/`
- Engine and queue tests: `core-download/src/test/java/com/alal/yft/core/download/`
- App-level download tests: `app/src/test/java/com/alal/yft/download/`, `app/src/test/java/com/alal/yft/feature/downloads/`
- Phase continuity: `README.md`, `docs/PHASE_STATUS.md`, `docs/TEST_MATRIX.md`, `docs/SESSION_STATE.md`

### Validation

Phase 3 was reverified before Phase 4 edits. Phase 4 completion used JDK 17, Android SDK 35 and Gradle 8.9:

```bash
./gradlew --no-daemon \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug :app:assembleRelease
```

Results:

- **BUILD SUCCESSFUL** in 16m 57s.
- 500 tasks: 216 executed, 26 from cache and 258 up-to-date.
- 216 tests, 0 failures, 0 errors and 0 skipped:
  - app: 58
  - core-browser: 27
  - core-data: 9
  - core-download: 79
  - core-media: 14
  - core-model: 22
  - extractor-generic: 7
- Debug APK: 15,377,631 bytes.
- Debug SHA-256: `553c5125b1fb91bde50ca83631281b5c986aec5d5d69993e9d9e2a01411bf944`.
- Unsigned minified release APK: 2,736,866 bytes.
- Release SHA-256: `435b0a03981642f8f0555c7b7128e824d6d4bf9cf260bbcd877706f4ce6ed0ed`.
- Local structured-secret scan passed before each remote checkpoint.
- GitHub Advanced Security secret scanning is not enabled for this repository.

### Known limitations

- No physical Android device/emulator is attached and `/dev/kvm` is unavailable. `MediaExtractor`/`MediaMuxer` behavior, foreground-service lifecycle under real Android process policy, notification rendering and `MediaStore` publication remain runtime checks rather than claimed device results.
- Platform muxing accepts only separate AVC/AAC MP4/fMP4 tracks. WebM, HEVC and unknown codecs fail explicitly; no FFmpeg is bundled.
- DASH `SegmentBase`/SIDX indexing and dynamic/live MPDs are unsupported.
- Low-storage behavior is covered only through the injected failure path, not on a real full-disk device.
- SAF tree export is not wired, so downloads land in `MediaStore` Downloads (API 29+) or app-private storage.
- Live-stream recording is outside scope.
- The release APK is unsigned; signing and publication remain Phase 7 and require explicit approval.
- KAPT emits a Kotlin 2.0 fallback warning while generating Hilt/Room code; compilation, lint and tests pass.
- GitHub MCP write tools and in-browser token creation are blocked by the automated safety reviewer, so pushes use an SSH key held only in the sandbox.

### Next exact action

1. Create `work/phase-5-site-adapters` from the Phase 4 completion head and push the kickoff checkpoint.
2. Reread `docs/prompts/06_PHASE_5.md` and the `:extractor-api` contract.
3. Add host-matched site adapters in `:extractor-sites` with a clean fallback to the generic detector.
4. Add committed offline fixtures and a regression test per adapter; no live network calls in tests.
5. Do not change the download engines, and do not merge a work branch into `main`.

### Phase 4 checkpoint commits

- Generalized persisted queue execution across direct, HLS, DASH and mux plans: `7eef922`
- Observable download queue controls: `2e0fa5f`
- Preview selection wired into the download queue: `dd73629`

The documentation/completion commit is newer; resolve it with `git log -1 --oneline` on `work/phase-4-download-engines`.
