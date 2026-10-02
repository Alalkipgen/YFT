# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 5 — Website-specific extractor adapters
- Current branch: `work/phase-5-site-adapters`
- Last completed task: Wired the site adapter registry into the app. `OkHttpExtractorClient` enforces the HTTPS/redirect/body-size boundary, `SiteAdapterCoordinator` runs the matched adapter after page load and maps every structured failure to a distinct user-facing notice, and `BrowserViewModel`/`BrowserScreen` surface adapter candidates and the notice without any site parsing in the UI
- Work in progress: 5A TikTok is implemented with offline fixtures and is live in the app through the registry. 5B Facebook, 5C another justified public site and 5D the YouTube risk review are still open
- Build status: PASS — `./gradlew --no-daemon :app:testDebugUnitTest` (BUILD SUCCESSFUL in 5m 7s, 64 app tests, 0 failures)
- Known failure/blocker: Platform muxing intentionally accepts only separate AVC/AAC MP4/fMP4 tracks; WebM, HEVC and unknown codecs fail explicitly and no FFmpeg dependency is bundled. DASH SegmentBase/SIDX and dynamic/live MPDs are unsupported. No device/emulator is attached (`/dev/kvm` unavailable), so MediaExtractor/MediaMuxer, foreground service, notifications and MediaStore publish need later device confirmation. SAF tree export is not wired. Release APK is unsigned (Phase 7). This sandbox origin is `https://github.com/Alalkipgen/YFT.git`; HTTPS git push is unauthenticated here, so checkpoints are pushed with GitHub MCP `push_files`
- Next exact action: Implement the Facebook adapter (5B) in `:extractor-sites` for `facebook.com/watch/?v=`, `facebook.com/{user}/videos/{id}`, `facebook.com/reel/{id}` and `fb.watch/{code}` with committed offline fixtures and structured failures, then register it in `SiteAdapterModule`
- Last pushed checkpoint: `66b284a` — TikTok adapter with offline fixtures; the app registry wiring checkpoint follows
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
