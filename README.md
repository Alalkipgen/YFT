# YFT — Video Downloader

YFT is an ad-free Android application for detecting and, in later phases, downloading authorized non-DRM media from direct links, HTML5 players, HLS and DASH streams.

**Phase 2: the secure browser and generic media detection are complete on `work/phase-2-browser-detection`.** Preview, variant resolution and download engines are not implemented yet; Phase 3 has not started.

## Current state

- A production Android app with application ID `com.alal.yft` and a `.debug` debug suffix.
- A hardened HTTPS WebView with address, back/forward, reload/stop, progress and safe error states.
- Generic detection from `DownloadListener`, read-only DOM inspection and observed HTTP(S) request URLs/headers.
- Direct media, HLS and DASH classification plus bounded, cancellable header probes with credential-safe redirect handling.
- Page-scoped candidate normalization, signed-URL deduplication, limits, debounce and immediate navigation cleanup.
- A media-found floating button and bottom sheet that keeps unavailable MIME, size, duration and DRM data explicitly unknown.
- Committed MP4/WebM/audio/HLS/DASH/blob fixtures and MockWebServer/Robolectric/Compose coverage.
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

No device/emulator was available for Phase 2, so real Android WebView rendering remains an explicit runtime verification item. The exact production DOM script was also executed against the committed HTML fixture in headless Chromium, while browser policy, parser, pipeline, ViewModel and Compose behavior are covered locally.

See [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md), [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) and [`docs/HANDOFF.md`](docs/HANDOFF.md) before continuing.

On machines with about 4 GiB RAM and no swap, run the fresh lint/test/debug matrix and `:app:assembleRelease` as separate Gradle invocations to avoid R8 competing with test/compiler workers.
