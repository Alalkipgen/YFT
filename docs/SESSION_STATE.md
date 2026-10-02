# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 5 — Website-specific extractor adapters (COMPLETE)
- Current branch: `work/phase-5-site-adapters`
- Last completed task: Closed Phase 5. 5A TikTok, 5B Facebook and 5C Vimeo adapters are implemented in `:extractor-sites` with committed offline fixtures, registered in `SiteAdapterModule` and covered by `ShippedAdaptersTest`; 5D YouTube is reported as a blocker in `docs/YOUTUBE_RISK_REVIEW.md` and `docs/decisions/ADR-004-youtube-adapter.md` instead of shipping an adapter, with the support-matrix row set to Blocked. `docs/PHASE_STATUS.md`, `docs/TEST_MATRIX.md`, `docs/SUPPORT_MATRIX.md` and `docs/HANDOFF.md` carry the Phase 5 results
- Work in progress: none; Phase 6 has not started
- Build status: PASS — `./gradlew --no-daemon lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:assembleRelease` (BUILD SUCCESSFUL in 7m, 505 tasks, 293 tests, 0 failures, lint 0 errors, debug and unsigned release APKs built)
- Known failure/blocker: No YouTube adapter, by decision. Adapters are verified only against committed fixtures, so any site markup change surfaces as a structured changed-markup failure. Platform muxing accepts only separate AVC/AAC MP4/fMP4 tracks; WebM, HEVC and unknown codecs fail explicitly and no FFmpeg is bundled. DASH SegmentBase/SIDX and dynamic/live MPDs are unsupported. No device/emulator is attached (`/dev/kvm` unavailable), so WebView, Media3 playback, MediaExtractor/MediaMuxer, foreground service, notifications and MediaStore publish need later device confirmation. SAF tree export is not wired. Release APK is unsigned (Phase 7). Checkpoints are pushed with `git push` over SSH (`git@github.com:Alalkipgen/YFT.git`, ed25519 key in `~/.ssh`); HTTPS push stays unauthenticated in this sandbox. Build environment must be set explicitly because the default JDK is 25, which Gradle 8.9 cannot run: `export JAVA_HOME=/data/.tools/jdk17 ANDROID_SDK_ROOT=/data/.android-sdk ANDROID_HOME=/data/.android-sdk`. `lintDebug` needs network access because `com.android.tools.lint:lint-gradle` is not in the offline cache; everything else builds with `--offline`. Stale Gradle daemons can exhaust the sandbox's 4 GB of memory and kill a build with "Gradle build daemon disappeared unexpectedly"; kill leftover daemons before a full run. Build directories carried over from an older sandbox path fail dexing with an "outside the root directory" error; delete the module `build/` directories once after a sandbox change
- Next exact action: Start Phase 6 per `docs/prompts/07_PHASE_6.md` — create `work/phase-6-hardening` from the Phase 5 completion head, push the kickoff checkpoint, and work reliability, privacy/security, performance and UI polish without adding new download sources
- Last pushed checkpoint: `2705bb1` — the YouTube blocker report; the Phase 5 completion commit follows
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
