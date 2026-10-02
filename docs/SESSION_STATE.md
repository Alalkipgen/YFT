# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 5 — Website-specific extractor adapters
- Current branch: `work/phase-5-site-adapters`
- Last completed task: Implemented the Facebook adapter (5B) in `:extractor-sites`. `FacebookUrls` matches watch, `video.php`, `/{handle}/videos/{id}`, reels, `fb.watch` and `/share/v|r/` links offline and collapses them onto one canonical page address; `FacebookPageParser` searches the embedded `application/json` payloads for the node that actually carries delivery fields (current `videoDeliveryResponseFragment` and legacy `playable_url`/`browser_native_*`), prefers the node whose ID matches the requested video so a suggested video is never returned, and reports DRM, login, private, region, media-free and changed-markup outcomes as structured failures; `FacebookExtractor` emits progressive MP4 candidates plus the page's DASH manifest URL with CDN expiry attached and is registered in `SiteAdapterModule`
- Work in progress: 5A TikTok and 5B Facebook are implemented with committed offline fixtures and are live in the app through the registry. 5C another justified public site and 5D the YouTube risk review are still open
- Build status: PASS — `./gradlew --no-daemon --offline :extractor-api:test :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:assembleDebug` (BUILD SUCCESSFUL in 3m 30s, 124 tests, 0 failures, debug APK assembled)
- Known failure/blocker: Platform muxing intentionally accepts only separate AVC/AAC MP4/fMP4 tracks; WebM, HEVC and unknown codecs fail explicitly and no FFmpeg dependency is bundled. DASH SegmentBase/SIDX and dynamic/live MPDs are unsupported. No device/emulator is attached (`/dev/kvm` unavailable), so MediaExtractor/MediaMuxer, foreground service, notifications and MediaStore publish need later device confirmation. SAF tree export is not wired. Release APK is unsigned (Phase 7). Checkpoints are now pushed with `git push` over SSH (`git@github.com:Alalkipgen/YFT.git`, ed25519 key in `~/.ssh`); HTTPS push stays unauthenticated in this sandbox. Build environment must be set explicitly because the default JDK is 25, which Gradle 8.9 cannot run: `export JAVA_HOME=/data/.tools/jdk17 ANDROID_SDK_ROOT=/data/.android-sdk ANDROID_HOME=/data/.android-sdk`. Build directories carried over from an older sandbox path fail dexing with an "outside the root directory" error; delete the module `build/` directories once after a sandbox change
- Next exact action: Choose the Phase 5C site, justify it in `docs/SUPPORT_MATRIX.md` (first candidate: public non-DRM Vimeo pages, whose player config JSON is public and documented), then implement it in `:extractor-sites` with committed offline fixtures and structured failures and register it in `SiteAdapterModule`
- Last pushed checkpoint: `60b1386` — site adapter registry wired into the browser; the Facebook adapter checkpoint follows
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
