# YFT — Video Downloader

YFT is the repository for an ad-free Android video downloader that detects and downloads authorized, non-DRM media from direct links, HTML5 players, HLS and DASH streams.

**Phase 0: technical discovery and feasibility is complete.** Production features begin in Phase 1 and are not implemented yet.

## Current state

- Architecture and support boundaries are documented under [`docs/`](docs/).
- A compileable Media3/WebView feasibility harness lives in [`spikes/phase0-media`](spikes/phase0-media/).
- Phase progress and cross-chat handoff details are recorded in:
  - [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md)
  - [`docs/HANDOFF.md`](docs/HANDOFF.md)

## Product boundaries

YFT will not attempt to bypass DRM, payment protection, private access controls or authentication restrictions. Website-specific support will be isolated behind adapters and claimed only when backed by tests.

## Reference implementation

The existing [AlalDownloader](https://github.com/Alalkipgen/AlalDownloader) project is an MIT-licensed reference for browser request context, segmented direct downloads, queueing, pause/resume and recovery. YFT will remain an independent application and repository.

## Validation

Phase 0 CI runs:

```bash
gradle -p spikes/phase0-media --no-daemon lintDebug testDebugUnitTest assembleDebug
```

See [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) before continuing development.
