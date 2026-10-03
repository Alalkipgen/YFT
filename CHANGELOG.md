# Changelog

All notable changes to Video Downloader (YFT) are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). `yft.versionCode` in `gradle.properties` rises by one
for every APK given to users, because Android refuses to install a lower one.

## [Unreleased]

### Fixed

- Browser page-load crash caused by background request interception reading `WebView.url` and
  `WebSettings`. Navigation now supplies an atomic page-URL snapshot and the configured
  User-Agent is cached on the main thread. Concurrent observations are serialized with
  navigation in the ViewModel; stale requests cannot populate the next page.

### Changed

- Documentation: `docs/FIX_PLAN.md` plans Phases 8–10 task by task after the beta.2 phone test,
  with ready-to-paste prompts in `docs/prompts/`. The old phase prompts, the hardening audit and
  the continuity protocol were removed (they stay in Git history); `PHASE_STATUS.md` and
  `HANDOFF.md` were condensed.

## [1.0.0-beta.2] - 2026-10-03

Second beta, with the redesigned interface from the owner's design images (`docs/design/`).
Features and download behaviour are unchanged. Installs over 1.0.0-beta.1 (same key, versionCode
2). Release notes: `docs/release/1.0.0-beta.2.md`.

### Changed

- New look on every screen: Mint and Deep Teal on a light surface or the Night theme, Plus
  Jakarta Sans, rounded Material Symbols, bordered cards and pill buttons; a new app icon (Mint
  to Deep Teal squircle with a play-and-download mark) and a Deep Teal launch screen.
- Bottom bar with Home, Downloads (badge with the active downloads), Library and Settings.
- Home: "Download from any link" with the Promptbox (Paste, Use copied link, searching, found
  and error states) that checks a link for media without opening the browser; Your sites
  shortcuts (add and remove) and the two newest files under Recent.
- Browser: address pill with the lock, host and path (never the query), and a "Found on this
  page" sheet that peeks above the toolbar. DRM-protected media are no longer listed or counted;
  a note says how many were left out.
- Download as (Preview) is a sheet over the page: small preview player, Video/Audio, qualities
  named "1080p · Full HD" with exact or estimated sizes, a details button, Wi-Fi only and the
  size on the Download button.
- Downloads: All/Active/Queued/Done/Failed filters with counts, speed and time left while the
  screen is open, status chips with the reason, Retry or Remove, a menu on each card, Completed
  today and Earlier, and a storage pill with the free space.
- Library: two-column grid with each file's own frame, length and "720p · 96 MB", search,
  sort, a ⋯ menu (Play, Open with…, Share, Delete), a mini player for audio above the bottom bar
  and full-screen video.
- Settings regrouped into Appearance, Downloads, Privacy and About, with a System/Light/Dark
  control, a 1–4 stepper for downloads at the same time and choice lists for location and
  quality; About and a separate Licenses page.

### Accessibility

- Every control has a TalkBack name and a touch target of at least 48dp, checked by an
  automated audit on every screen in both themes; text up to 200% wraps instead of clipping.

### Known issues

- Everything listed for 1.0.0-beta.1 below still applies.
- Playback in the Library and Downloads stops when YFT leaves the screen (no background
  playback service).
- Speed and time left are measured only while the Downloads screen is open.
- Remaining differences from the design images are listed in `docs/design/DESIGN-NOTES.md`.

### Testing status

- Automated: JVM, Robolectric and fixture tests, Android lint, the minified release build and
  signature verification in the release workflow; Robolectric renders of every screen (light,
  Night, 130% and 200% text) compared with the design images. Results: `docs/TEST_MATRIX.md`.
- Not yet run on a phone or emulator, as for beta.1, plus the new icon, launch screen, Night
  theme and the update from beta.1.

## [1.0.0-beta.1] - 2026-10-02

First beta for sideload testing. Runs on Android 7.0 (API 24) or newer and targets Android 15
(API 35). Distributed only through GitHub. Release notes: `docs/release/1.0.0-beta.1.md`.

### Added

- Paste a link on Home, or open any HTTPS page in a hardened in-app browser with address bar,
  back/forward, reload/stop and progress.
- Generic media detection from downloads, page sources and observed requests: direct files
  (MP4, WebM, audio), HLS and DASH; `blob:` players are resolved to their real HTTP(S) source.
- Detected Media list and a media-found sheet; Preview shows real video/audio variants with
  resolution, frame rate, codec, bitrate, duration and exact, estimated or unknown size.
- Downloads for direct, HLS and DASH media with pause, resume, retry, a foreground service and
  recovery after a restart; finished files go to `Download/YFT` on Android 10+ and to app
  storage on older versions.
- Site adapters for TikTok, Facebook and Vimeo, and for single YouTube videos (progressive MP4,
  usually up to 360p, plus M4A audio) by owner decision (ADR-005). Unsupported sites fall back
  to generic detection.
- Library to play, open, share and delete finished files (deletion asks first).
- Settings: default quality, download location, Wi-Fi only, mobile-data confirmation,
  concurrent downloads (1–4), light/dark theme, clear browsing data and clear download history.
- Free-space check before downloads of known size, a "Waiting for Wi-Fi"/"No connection"
  banner, and one cleanup pass per launch for leftover partial files.
- About screen with version, scope, privacy promises and third-party license texts.
- Original YFT launcher icon (adaptive with a themed monochrome layer, plus Android 7.x PNGs)
  and launch screen.
- Release tooling: signing from environment variables or an untracked `keystore.properties`,
  `scripts/release-prep.sh`, `scripts/verify-release-apk.sh` (signature, certificate,
  alignment, upgrade compatibility, SHA-256), `scripts/device-smoke-test.sh` and a draft-only
  release workflow.

### Privacy and security

- No ads, analytics, accounts or cloud backup; app data is excluded from backup and device
  transfer.
- HTTPS only (no cleartext traffic). Browser context such as cookies is sent only to the same
  origin, and credentials are dropped on cross-origin redirects.
- Candidate links stay in memory; logs never contain cookies, tokens or signed links; download
  notifications hide titles on a secure lock screen.
- The clipboard is read only when Paste is tapped. No DRM, payment, login or other access
  control is bypassed.

### Known issues

- YouTube: progressive MP4 (usually up to 360p) plus one M4A audio stream only. Without PO
  tokens some videos fail with HTTP 403, some networks get bot checks, and the player-script
  solver and client profiles need regular maintenance.
- Separate video and audio tracks can be combined only when they are AVC/AAC in MP4/fMP4. DASH
  `SegmentBase`/SIDX and live streams are not supported.
- Choosing an arbitrary folder through the system picker (SAF) is not available yet.
- An unfinished download whose signed link expired while the app was closed needs a fresh link
  from the page; there is no in-place link refresh.
- The free-space check covers only exact sizes; estimated or unknown sizes start and fail
  cleanly if storage fills up.
- TikTok, Facebook and Vimeo support is verified against saved page fixtures; site changes can
  break it until the adapter is updated.

### Testing status

- Automated: JVM, Robolectric and fixture tests, Android lint, minified release build and the
  signing/verification pipeline (with a throwaway key). Exact results are in
  `docs/PHASE_STATUS.md` and `docs/TEST_MATRIX.md`.
- Not yet run on a phone or emulator: WebView rendering, YouTube on a real network, Media3
  playback, muxing, the foreground service, MediaStore publication, Library sharing, Wi-Fi
  switching, notification permission, and fresh-install/upgrade of the signed APK.
