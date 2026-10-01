# YFT — Video Downloader

YFT is an ad-free Android application foundation for detecting and downloading authorized, non-DRM media from direct links, HTML5 players, HLS and DASH streams.

**Phase 1: production foundation is complete on `work/phase-1-foundation`.** Media detection and browser behavior begin in Phase 2; full download engines are not implemented yet.

## Current state

- A production Android app with application ID `com.alal.yft` and a debug suffix of `.debug`.
- Nine modules matching the architecture in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- A Compose/Material 3 app shell with Home, Browser, Detected Media, Preview, Downloads, Library, Settings and About routes.
- ViewModel/StateFlow UI state with a persisted light/dark/system theme preference.
- Hilt foundations for Room, DataStore, OkHttp and Media3.
- Room schema export plus tested baseline DAO and v1→v2 migration.
- Structured result/error types and a logger that redacts sensitive headers, credentials and signed query values.
- Gradle 8.9 wrapper, debug/release variants and GitHub Actions validation.
- The Phase 0 feasibility harness remains under [`spikes/phase0-media`](spikes/phase0-media/).

## Cross-chat continuity

Development happens on `work/phase-*` branches. Agents must read [`AGENTS.md`](AGENTS.md), update [`docs/SESSION_STATE.md`](docs/SESSION_STATE.md), and create a remote checkpoint after every logical milestone.

See [`docs/CONTINUITY_PROTOCOL.md`](docs/CONTINUITY_PROTOCOL.md). Local commits and stashes are not durable handoffs.

## Product boundaries

YFT will not attempt to bypass DRM, payment protection, private access controls or authentication restrictions. Website-specific support remains isolated behind adapters and may only be claimed when backed by tests.

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

See [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md), [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) and [`docs/HANDOFF.md`](docs/HANDOFF.md) before continuing.

On machines with about 4 GiB RAM and no swap, run the fresh lint/test/debug matrix and `:app:assembleRelease` as separate Gradle invocations to avoid R8 competing with test/compiler workers.
