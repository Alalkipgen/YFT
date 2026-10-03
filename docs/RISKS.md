# Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Website layout/API changes | Site adapter breaks | Isolated adapters, fixtures and structured failures |
| Signed URL expiry | Download fails mid-transfer | Preserve page context and support re-resolution |
| Separate audio/video | Final file unusable without mux | Explicit plan type and compatibility checks |
| HLS/DASH DRM | Cannot legally/technically export | Detect and reject clearly |
| WebView lacks passive response MIME access | False positives/negatives | DOM + URL hints + bounded OkHttp probes |
| Cookies/tokens leak to logs | Account/privacy compromise | Central redaction and no sensitive test fixtures |
| Large FFmpeg dependency | APK size/license/maintenance cost | Do not add without Phase 4 ADR |
| Embedded yt-dlp/Python | Size, updates and distribution risk | Not embedded. Native isolated adapters; only the yt-dlp ejs JavaScript solver is bundled, pinned by SHA-256, for YouTube (ADR-005) |
| Over-modularization | Slow and fragile builds | Small core-module set; UI features remain packages |
| Background restrictions | Transfers stop | Foreground service and persisted recovery |
| Store policy/website terms | Distribution risk | GitHub-first beta, truthful support scope, authorized media only |
| YouTube adapter (owner override, ADR-005; widened by ADR-006 on 2026-10-03: device clients, PO tokens, bot-check workarounds) | Frequent breakage; PO-token 403s; bot checks; GitHub DMCA exposure; Play policy conflict; terms-of-service exposure accepted by the owner | Embedded client first, sandboxed bundled solver, one-file client profiles, update/verify scripts, build flag to disable, GitHub-only distribution, structured failure reasons |
| YouTube bot checks, PO tokens and SABR-only responses (2025–2026) | Lookups fail or offer few formats | Honest bot-check message, SABR-only detail and per-client lookup details (T08, Phase 8 branch; live from the sandbox IP: one of two public videos got the bot check, the embedded client was refused for both); client strategy D2 = A + B + C (owner, 2026-10-03; T16); support claimed only after a phone check |
| Real WebView behaviour is not covered by Robolectric | Crashes and layout bugs reach the phone (beta.2) | WebView calls on the main thread only (T01); CI emulator smoke test (T02) |
| Reading the clipboard when the app opens | Privacy concern; Android 12+ shows a paste message | Owner decision D1, a Settings switch, only http(s) links used, the text never stored (T11) |
| MP3 encoding (LAME, LGPL) and the archived FFmpegKit | Licence duties, APK size, maintenance | M4A first; MP3 only with D3, as a shared library with notices (T18); FFmpegKit is not used |
| Brand logos in Your sites | Trademark complaints | Simple Icons (CC0) shapes, no affiliation claim, notice line (T09) |
| Facebook and TikTok anti-bot answers | Login walls or 403 for public videos | Honest desktop-class identity with navigation headers (T05); page cookies in memory and same-origin only (T07); live checks before claiming support |
