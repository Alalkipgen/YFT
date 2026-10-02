# YFT — Video Downloader

YFT is an ad-free Android application for detecting and, in later phases, downloading authorized non-DRM media from direct links, HTML5 players, HLS and DASH streams.

**Phase 5: isolated TikTok, Facebook and Vimeo extractors behind the extractor API, with offline fixtures and a clean fallback to generic detection, are complete on `work/phase-5-site-adapters`.** No YouTube adapter ships; the reasons are documented in [`docs/YOUTUBE_RISK_REVIEW.md`](docs/YOUTUBE_RISK_REVIEW.md). Hardening, privacy, performance and UI polish begin in Phase 6.

## Current state

- A production Android app with application ID `com.alal.yft` and a `.debug` debug suffix.
- A hardened HTTPS WebView with address, back/forward, reload/stop, progress and safe error states.
- Generic detection from `DownloadListener`, read-only DOM inspection and observed HTTP(S) request URLs/headers.
- Direct media, HLS and DASH classification plus bounded, cancellable header probes with credential-safe redirect handling.
- Page-scoped candidate normalization, signed-URL deduplication, limits, debounce and immediate navigation cleanup.
- A media-found bottom sheet whose Preview action passes sensitive candidate context through memory only, never through a route or database.
- Bounded direct metadata validation plus HLS/DASH parsing for real video/audio variants, separate tracks, resolution, FPS, codec, bitrate, duration and exact/estimated/unknown size.
- Explicit expired-link, DRM, unsupported-codec, malformed-manifest, unsafe-redirect and network failures without inventing metadata.
- HTTPS-only Media3 progressive/HLS/DASH sources with same-origin browser context and cross-origin credential stripping.
- A lifecycle-safe preview screen with video/audio tabs, selectable variants, player controls, retry states and explicit unknown/estimated labels.
- Committed MP4/WebM/audio/HLS/DASH/blob fixtures and MockWebServer/Robolectric/Compose coverage.
- Site adapters for TikTok, Facebook and Vimeo, each isolated behind `:extractor-api`, selected by host, and failing with their own reason for private, login-required, region-blocked, DRM, expired, media-free or changed pages. Supported link patterns and limits are listed in [`docs/SUPPORT_MATRIX.md`](docs/SUPPORT_MATRIX.md).
- Any other site, including YouTube, stays on the generic detection path; no adapter signs in, stores credentials or bypasses an access control.
- The Phase 1 Compose, Hilt, Room, DataStore, OkHttp, Media3, redaction, CI and release-build foundations.
- The Phase 0 feasibility harness remains under [`spikes/phase0-media`](spikes/phase0-media/).

## Cross-chat continuity

Development happens on `work/phase-*` branches. Agents must read [`AGENTS.md`](AGENTS.md), update [`docs/SESSION_STATE.md`](docs/SESSION_STATE.md), and create a remote checkpoint after every logical milestone.

See [`docs/CONTINUITY_PROTOCOL.md`](docs/CONTINUITY_PROTOCOL.md). Local commits and stashes are not durable handoffs.

## Product boundaries

YFT will not attempt to bypass DRM, payment protection, private access controls or authentication restrictions. Website-specific support remains isolated behind adapters and may only be claimed when backed by tests.

A literal `blob:` URL is never treated as a downloadable file. Detection must find its underlying HTTP(S) request, source or manifest.

## Reference implementation

The existing [AlalDownloader](https://github.com/Alalkipgen/AlalDownloader) project is an MIT-licensed reference for browser request context, segmented direct downloads, queueing, pause/resume and recovery. YFT remains independently buildable and does not depend on that repository at runtime.

## Validation

Use JDK 17 and Android SDK 35:

```bash
./gradlew --no-daemon \
  lintDebug \
  testDebugUnitTest \
  :core-model:test \
  :extractor-api:test \
  :extractor-generic:test \
  :extractor-sites:test \
  :app:assembleDebug
```

A local release check can additionally run `:app:assembleRelease`; the output is intentionally unsigned until Phase 7.

The Phase 5 completion matrix passed 293 tests, Android lint with no errors, debug assembly and the minified unsigned release build. `lintDebug` needs network access the first time, because `lint-gradle` is not in the offline cache; every other task in the matrix runs with `--offline`. No device/emulator was available, so real Android WebView rendering, Media3 playback, `MediaExtractor`/`MediaMuxer` behavior, foreground-service lifecycle and `MediaStore` publication remain explicit runtime verification items. Browser policy, bounded network behavior, manifests, transfer/recovery logic, state and Compose surfaces are covered locally.

See [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md), [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) and [`docs/HANDOFF.md`](docs/HANDOFF.md) before continuing.

On machines with about 4 GiB RAM and no swap, run the fresh lint/test/debug matrix and `:app:assembleRelease` as separate Gradle invocations to avoid R8 competing with test/compiler workers.
