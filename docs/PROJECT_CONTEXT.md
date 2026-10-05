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
- No DRM, payment, private-access, age-check or account sign-in bypass. For public videos any
  working technique is allowed (owner, [ADR-006](decisions/ADR-006-owner-override-any-working-method.md), 2026-10-03).
- Browser cookies and signed URLs are sensitive and must never be logged.

## Privacy diagnostics

- A crash handler keeps only the last redacted report in the app's private
  `noBackupFilesDir/diagnostics/last-crash.txt`, capped at 64 KiB. It records UTC time, app
  version, SDK, manufacturer/model (no unique device identifiers), thread and exception frames.
  Saving is best effort; Android's previous handler still receives the original exception.
- Reports are outside backups. No report is uploaded, attached to a request or sent on startup.
  About shows **Last crash report** only when one exists: View/select text, Copy, Share text
  through the system chooser, or Delete. Export happens only after the owner's explicit tap.
- Diagnostic text passes through `SensitiveValueRedactor` and the stricter
  `DiagnosticTextSanitizer`: URLs become origins, queries disappear, and credential-bearing
  lines are omitted. Raw page bodies, cookies and response headers are not diagnostic steps.
- Home's **Copy details** uses bounded, sanitized lookup steps kept only in memory. Editing,
  cancelling or starting a new lookup clears the old steps; neither DataStore nor Room stores
  them. The clipboard is read on Paste or Use, or once per new clip when Settings › Privacy ›
  "Check copied links when YFT opens" is on (default, T11); only a hash of the last clip is
  kept in memory.
- Only debug builds have a confirmed crash-test action on a long press of the About version.
  The release source set has no such action; APK verification rejects its debug-only markers.

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

Phases 8–10 are complete and released as the `1.0.0-beta.3` draft (2026-10-04): field fixes from
the beta.2 phone test, the copied-link flow ("Video you copied" sheet, Search to download, the
floating Download button) and formats (YouTube client strategy, merged video and audio, MP3).

Planned, task by task in `docs/FIX_ADD_PLAN.md`: Phase 11 — download flow like Snaptube. Part 1
(P1–P7, with a test-key preview APK) is done on the branch; part 2 (P9–P19: short sheet, slow
networks, stable quality rows, one lookup per page, wide Download button, real thumbnails,
faster YouTube and Facebook lookups, a sheet that opens at once) comes next, then the signed
`1.0.0-beta.4` (P8).

## Out of scope for the MVP

- DRM/Widevine circumvention
- Paid/private content bypass
- Live-stream recording
- Playlist/batch downloads
- Audio transcoding
- Cloud accounts or sync
- Remote executable extractor scripts. The YouTube solver is bundled in the APK, not downloaded; only YouTube's own player script is fetched, and it runs only in a sandboxed offscreen WebView ([ADR-005](decisions/ADR-005-youtube-owner-override.md)); from T16 also YouTube's own BotGuard script for proof-of-origin tokens ([ADR-006](decisions/ADR-006-owner-override-any-working-method.md))
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
