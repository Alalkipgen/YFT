# YFT Project Context

## Purpose

YFT is a new, independent Android application with the working display name **Video Downloader**. It should let a user paste a URL or browse a website, detect authorized non-DRM media, preview available variants, choose a quality and download the result reliably.

The target repository is `https://github.com/Alalkipgen/YFT`.
The reference repository is `https://github.com/Alalkipgen/AlalDownloader`.

This file and the other files under `docs/` are the continuity source for future coding sessions. A new agent must inspect Git, run the relevant build, and read the documentation rather than relying on prior chat history.

## Product principles

- Clean, original and ad-free UI.
- No content-feed clutter.
- Browser-first and paste-link workflows.
- Honest metadata: never invent file size, quality, codec or support.
- Generic direct/HLS/DASH capability before website-specific adapters.
- Site adapters fail independently and fall back to generic detection.
- No DRM, payment, private-access or authentication-control bypass.
- Browser cookies and signed URLs are sensitive and must never be logged.

## Working identity

| Decision | Phase 0 choice |
| --- | --- |
| Repository | `YFT` |
| Display name | `Video Downloader` |
| Production application ID | `com.alal.yft` |
| Spike application ID | `com.alal.yft.spike` |
| Minimum SDK | 24 |
| Compile SDK | 35 |
| Target SDK | 35 |
| Java toolchain | 17 |

The application ID must be reconfirmed before the first production release because it should not change after publication.

## Primary user flow

1. Paste a URL or open the built-in browser.
2. Load a page in a secure WebView session.
3. Detect direct media, DOM sources and HLS/DASH manifests.
4. Normalize and group candidates.
5. Show a floating media-found button.
6. Resolve real variants and metadata.
7. Preview with the same cookies, referer, User-Agent and required headers.
8. Build a direct/HLS/DASH/mux download plan.
9. Run the plan in a foreground service with persisted state.
10. Export the completed file through MediaStore or the Storage Access Framework.

## Phases

Phases 0–7 are complete: discovery, foundation and CI, browser and generic detection, preview and
variants, download engines and recovery, site adapters (plus 5E, YouTube by owner decision),
hardening and UI polish (plus the UI redesign), and the signed beta. See `docs/PHASE_STATUS.md`.

Planned, task by task in `docs/FIX_PLAN.md`:

- Phase 8 — Field fixes from the beta.2 phone test → `1.0.0-beta.3`
- Phase 9 — Copied-link flow ("Video you copied" sheet, Search to download, floating Download
  button) → `1.0.0-beta.4`
- Phase 10 — Formats and YouTube (client strategy, merged video and audio, MP3) → `1.0.0-beta.5`

## Out of scope for the MVP

- DRM/Widevine circumvention
- Paid/private content bypass
- Live-stream recording
- Playlist/batch downloads
- Audio transcoding
- Cloud accounts or sync
- Remote executable extractor scripts. The YouTube solver is bundled in the APK, not downloaded; only YouTube's own player script is fetched, and it runs only in a sandboxed offscreen WebView ([ADR-005](decisions/ADR-005-youtube-owner-override.md))
- A general-purpose desktop-class browser

## Reference-repository findings

AlalDownloader currently provides proven design material for:

- A WebView session that captures Cookie, Referer, User-Agent and observed request headers.
- Extension-based direct-media and manifest detection.
- A direct HTTP engine with range probing, segmentation, queueing and StateFlow snapshots.
- Pause, resume, cancellation, network gating and replacement of expired links.
- A foreground service, notifications and persisted recovery.
- Unit tests for range handling, segment planning, TLS policy, browser transfer context and recovery.

YFT should adapt the concepts into explicit interfaces. It should not copy the reference application's UI or create a runtime dependency on the reference repository.

## Phase 0 environment note

The initial local sandbox had Java 25 but no Android SDK or Gradle installation. The repository therefore includes a GitHub Actions build using Java 17, Android SDK 35 and Gradle 8.9. Local validation may additionally install an isolated Java 17/Android SDK toolchain; no secrets are required.
