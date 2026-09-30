CURRENT TASK: PHASE 6 — HARDENING, PRIVACY, PERFORMANCE AND UI POLISH

Do not add major new download sources.

RELIABILITY
Test/fix backgrounding, screen off, process death, network switching/slow network, timeouts, redirects, expiry, low storage, invalid folder, large files, many candidates, concurrency, repeated pause/resume, cancel during merge, cleanup and database migration/recovery.

SECURITY/PRIVACY
Keep TLS verification enabled; restrict WebView file/content access and JS bridges; prevent path traversal; redact cookies/tokens/signed URLs; provide browser/history clearing; request only needed permissions; read clipboard only after user action; reject DRM without bypass.

PERFORMANCE
Profile startup, page responsiveness, debounce/deduplication, memory, preview buffering, concurrency, battery, DB queries, thumbnails, lists and temporary storage.

UI
Finalize Home, Browser, floating detector, candidate sheet, Preview, quality picker, Downloads, Library, Settings, About, light/dark themes, empty/loading/error states, accessibility and confirmations. Keep an original Material 3 design.

SETTINGS
Default quality, destination, Wi-Fi-only, concurrency, mobile-data warning, theme and data/history clearing.

DEFINITION OF DONE
- Critical flows pass on supported Android versions.
- Recovery/privacy/security checks pass with no high-severity issue.
- UI states are complete.
- Lint/tests/release build pass.
- TEST_MATRIX/docs/handoff updated; phase-6 commit created.

Do not publish or start Phase 7 automatically.
