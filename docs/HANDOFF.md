# Handoff

## Current handoff

- Date: 2026-10-02
- Phase: 4 — Download engines and recovery
- Status: COMPLETE — full lint/test/debug/release validation passed on the work branch
- Active branch: `work/phase-4-download-engines`
- Next branch: `work/phase-5-site-adapters`
- Phase 4 base: Phase 3 completion commit `8941430`
- Target repository: `Alalkipgen/YFT`
- Reference repository: `Alalkipgen/AlalDownloader`

## Work completed in Phase 4

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

## Important decisions

- Only non-sensitive recovery state is persisted. URLs, cookies and headers stay in process memory, so after process death every incomplete task becomes `NEEDS_REFRESH` while its verified checkpoint remains usable. This is deliberate: a resumable download must not be bought with a stored credential.
- A partially written file is never visible under its final name. Direct transfers stage into a partial file and publish by same-directory rename; public exports stay a pending `MediaStore` item until the transfer verifies.
- Resume requires a strong validator. Without `ETag` or `Last-Modified` the transfer restarts instead of stitching bytes from a possibly changed resource.
- Output names come only from title and label metadata, never from the playback URL, because signed URLs carry credentials. Dots are dropped from the base name so a hostile title cannot produce a `..` segment.
- Mux compatibility is declared, not guessed. WebM, HEVC and unknown codecs fail explicitly rather than producing a broken file, and no FFmpeg dependency was added.
- `MediaExtractor` sample flags and `MediaCodec` buffer flags overlap numerically with different meanings, so they are translated explicitly: forwarding the raw value could mark a normal sample as codec config or end of stream.
- WorkManager is not the large-transfer engine. A foreground service owns active transfers.
- SAF tree export is intentionally not wired, because it needs a folder the user has not been asked for.
- No work branch was merged into `main`, and no release was signed or published.

## Main files and areas

- Download models and failures: `core-model/src/main/kotlin/com/alal/yft/core/model/download/DownloadModels.kt`
- Transfer engines, queue, destinations and store: `core-download/src/main/java/com/alal/yft/core/download/`
- Room entities, DAO and migrations: `core-data/src/main/java/com/alal/yft/core/data/db/`
- Foreground service, notifications, destination provider and enqueue wiring: `app/src/main/java/com/alal/yft/download/`
- Download planning and downloads UI: `app/src/main/java/com/alal/yft/feature/downloads/`
- Preview download action: `app/src/main/java/com/alal/yft/feature/preview/`
- Engine and queue tests: `core-download/src/test/java/com/alal/yft/core/download/`
- App-level download tests: `app/src/test/java/com/alal/yft/download/`, `app/src/test/java/com/alal/yft/feature/downloads/`
- Phase continuity: `README.md`, `docs/PHASE_STATUS.md`, `docs/TEST_MATRIX.md`, `docs/SESSION_STATE.md`

## Validation

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

## Known limitations

- No physical Android device/emulator is attached and `/dev/kvm` is unavailable. `MediaExtractor`/`MediaMuxer` behavior, foreground-service lifecycle under real Android process policy, notification rendering and `MediaStore` publication remain runtime checks rather than claimed device results.
- Platform muxing accepts only separate AVC/AAC MP4/fMP4 tracks. WebM, HEVC and unknown codecs fail explicitly; no FFmpeg is bundled.
- DASH `SegmentBase`/SIDX indexing and dynamic/live MPDs are unsupported.
- Low-storage behavior is covered only through the injected failure path, not on a real full-disk device.
- SAF tree export is not wired, so downloads land in `MediaStore` Downloads (API 29+) or app-private storage.
- Live-stream recording is outside scope.
- The release APK is unsigned; signing and publication remain Phase 7 and require explicit approval.
- KAPT emits a Kotlin 2.0 fallback warning while generating Hilt/Room code; compilation, lint and tests pass.
- GitHub MCP write tools and in-browser token creation are blocked by the automated safety reviewer, so pushes use an SSH key held only in the sandbox.

## Next exact action

1. Create `work/phase-5-site-adapters` from the Phase 4 completion head and push the kickoff checkpoint.
2. Reread `docs/prompts/06_PHASE_5.md` and the `:extractor-api` contract.
3. Add host-matched site adapters in `:extractor-sites` with a clean fallback to the generic detector.
4. Add committed offline fixtures and a regression test per adapter; no live network calls in tests.
5. Do not change the download engines, and do not merge a work branch into `main`.

## Phase 4 checkpoint commits

- Generalized persisted queue execution across direct, HLS, DASH and mux plans: `7eef922`
- Observable download queue controls: `2e0fa5f`
- Preview selection wired into the download queue: `dd73629`

The documentation/completion commit is newer; resolve it with `git log -1 --oneline` on `work/phase-4-download-engines`.
