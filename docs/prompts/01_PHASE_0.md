CURRENT TASK: PHASE 0 — TECHNICAL DISCOVERY, ARCHITECTURE AND FEASIBILITY

Do not build the complete application.

OBJECTIVES
- Inspect YFT and AlalDownloader.
- Identify reusable concepts for WebView request context, direct/segmented transfers, queueing, pause/resume and recovery.
- Decide app identity, SDK/toolchain, dependencies, modules, security rules, testing and signing strategy.
- Implement only the smallest compileable feasibility harness.
- Create durable documentation for future chats.

REQUIRED SPIKES
1. Direct HTTPS Media3 preview-source construction.
2. Non-DRM HLS preview-source construction.
3. Non-DRM DASH preview-source construction.
4. Read-only DOM detection of video/audio/source URLs.
5. Capture Cookie, Referer, User-Agent and safe observed request headers.
6. Define DRM/unsupported rejection behavior.

Do not approve yt-dlp, embedded Python, FFmpeg or another large extractor without documenting license, APK size, maintenance, Android compatibility and alternatives.

REQUIRED DOCS
- PROJECT_CONTEXT.md
- ARCHITECTURE.md
- PHASE_STATUS.md
- SUPPORT_MATRIX.md
- TEST_MATRIX.md
- HANDOFF.md
- architecture decision records for project structure, detection and engines

DEFINITION OF DONE
- Minimal tests and debug build pass, or the exact environment blocker is documented.
- Spike results are honest and reproducible.
- Architecture, boundaries, risks and Phase 1 prerequisites are explicit.
- Continuity documents exist.
- A phase-0 commit is created.

Do not start Phase 1.
