CURRENT TASK: PHASE 2 — BUILT-IN BROWSER AND GENERIC MEDIA DETECTION

Verify the Phase 1 app builds before editing.

OBJECTIVES
- Implement a secure browser with URL/address controls, navigation, loading/error state and browser sessions.
- Detect generic media without site-specific extractors.
- Normalize candidates and show a floating media-found button plus bottom sheet.

DETECTORS
1. WebView DownloadListener
2. read-only DOM probe for video/audio/source and common metadata
3. observed HTTP(S) request URLs/headers
4. direct-media MIME/extension hints
5. HLS `.m3u8` and DASH `.mpd`
6. redirect hints
7. blob-backed playback by finding the underlying request/manifest, never downloading the literal blob URL

CANDIDATE DATA
Page/media URLs, source, MIME hint, title/thumbnail/duration when known, secure request context, referer, User-Agent, manifest type, confidence, expiry/DRM hints.

NORMALIZATION
Deduplicate, bound counts, debounce updates, reject obvious tiny/tracking assets and clear page-scoped candidates on navigation.

TESTS
Use local/fixture pages for direct MP4/WebM, HTML5 source, HLS, DASH, redirects, blob-backed playback, duplicates, navigation cleanup and cookie/header context.

DEFINITION OF DONE
- Real test pages produce deduplicated candidates.
- Floating button appears only when candidates exist.
- Bottom sheet shows honest candidate data and clear states.
- Tests/debug build pass; docs/handoff updated; phase-2 commit created.

No site adapters or complete download engine. Do not start Phase 3.
