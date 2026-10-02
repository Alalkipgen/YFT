# Phase 0 Risks

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
| YouTube adapter (owner override, ADR-005) | Frequent breakage; PO-token 403s; bot checks; GitHub DMCA exposure; Play policy conflict | Embedded client first, sandboxed bundled solver, one-file client profiles, update/verify scripts, build flag to disable, GitHub-only distribution, structured failure reasons |
