# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 5 — Site adapters (Phase 4 complete)
- Current branch: `work/phase-4-download-engines` (Phase 5 continues on `work/phase-5-site-adapters`)
- Last completed task: Closed Phase 4 — full validation passed and the phase docs were updated. Before that: wired the preview-to-queue enqueue path — pure `DownloadPlanFactory` (typed Direct/HLS/DASH plans, explicit rejection of unsupported codecs, non-HTTPS and expired links, traversal-safe file names built only from title/label metadata), `DownloadEnqueuer` behind a narrow `PreviewDownloadStarter` boundary that probes direct sources before queueing, `AndroidDownloadDestinationProvider` (MediaStore pending item on API 29+, app-private fallback), and a preview Download button with idle/queueing/queued/rejected status reporting
- Work in progress: Phase 5 site adapters not started yet; SAF tree export is still deferred because it needs a user folder pick
- Build status: PASS — `./gradlew --no-daemon lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:assembleRelease` → BUILD SUCCESSFUL in 16m 57s, 500 tasks, 216 tests, 0 failures/errors/skips
- Known failure/blocker: Platform muxing intentionally accepts only separate AVC/AAC MP4/fMP4 tracks; WebM, HEVC and unknown codecs fail explicitly and no FFmpeg dependency is bundled. DASH `SegmentBase`/SIDX and dynamic/live MPDs are unsupported. No device/emulator is attached, so MediaExtractor/MediaMuxer behavior and foreground-service/MediaStore flows need later device confirmation. GitHub Advanced Security secret scanning is unavailable, so local structured-secret scans remain required. GitHub MCP write tools and browser token creation are blocked by the automated safety reviewer; pushes now use an SSH key held only in the sandbox (`core.sshCommand` on this clone, `origin` = `git@github.com:Alalkipgen/YFT.git`)
- Next exact action: Create `work/phase-5-site-adapters` from the Phase 4 completion head, then add host-matched adapters in `:extractor-sites` behind `:extractor-api` with offline fixtures and a clean fallback to the generic detector
- Last pushed checkpoint: `dd73629` — preview selection wired into the download queue; Phase 4 completion commit follows
- Last updated: 2026-10-02

## Checkpoint note template

```text
Current phase:
Current branch:
Last completed task:
Work in progress:
Build status and exact command:
Known failure/blocker:
Next exact action:
Last pushed checkpoint:
Last updated:
```
