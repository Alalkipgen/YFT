# YFT — Video Downloader

YFT is an ad-free Android application for detecting, previewing and downloading authorized non-DRM media from direct links, HTML5 players, HLS and DASH streams.

## Status

- `1.0.0-beta.1` is published (2026-10-02). `1.0.0-beta.2`, the first build with the redesigned
  interface, is a signed draft pre-release (2026-10-03).
- `1.0.0-beta.3` (2026-10-04, versionCode 3) completes Phases 8–10: the fixes from the owner's
  beta.2 phone test (browser crash, start page, Facebook, TikTok, YouTube messages), the
  copied-link flow ("Video you copied", Search to download, the floating Download button) and
  YouTube 480p–1080p with sound plus MP3. Notes and the phone checklist:
  [`docs/release/1.0.0-beta.3.md`](docs/release/1.0.0-beta.3.md); causes and tasks:
  [`docs/FIX_PLAN.md`](docs/FIX_PLAN.md).
- Next: the owner's phone check of beta.3; no further tasks are planned.
- Complete: Phases 0–10, 5E (YouTube by owner decision,
  [ADR-005](docs/decisions/ADR-005-youtube-owner-override.md)) and the UI redesign
  ([`docs/design/DESIGN-NOTES.md`](docs/design/DESIGN-NOTES.md)); see
  [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md). YouTube risks and limits:
  [`docs/YOUTUBE_RISK_REVIEW.md`](docs/YOUTUBE_RISK_REVIEW.md). YFT is distributed through GitHub
  only.

## Install the beta

Requires Android 7.0 (API 24) or newer; the app targets Android 15 (API 35). Once the owner publishes a release, download `video-downloader-<version>.apk` and `SHA256SUMS` from the repository's GitHub Releases page, check the file with `sha256sum -c SHA256SUMS` (or `Get-FileHash` on Windows), open the APK on the phone and allow your browser or file manager to install unknown apps when Android asks. Later releases signed with the same key update in place. Details and known issues: [`docs/release/1.0.0-beta.3.md`](docs/release/1.0.0-beta.3.md) and [`CHANGELOG.md`](CHANGELOG.md).

## Current state

Verified with JVM, Robolectric and fixture tests; on-device checks that the agent environment cannot run are listed in [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md). The beta.2 field problems are fixed in `1.0.0-beta.3`; its phone checks are still open.

- A production Android app with application ID `com.alal.yft` and a `.debug` debug suffix.
- A hardened HTTPS WebView with address, back/forward, reload/stop, progress and safe error states.
- Generic detection from `DownloadListener`, read-only DOM inspection and observed HTTP(S) request URLs/headers.
- Direct media, HLS and DASH classification plus bounded, cancellable header probes with credential-safe redirect handling.
- Page-scoped candidate normalization, signed-URL deduplication, limits, debounce and immediate navigation cleanup.
- A media-found bottom sheet whose Preview action passes sensitive candidate context through memory only, never through a route or database.
- Bounded direct metadata validation plus HLS/DASH parsing for real video/audio variants, separate tracks, resolution, FPS, codec, bitrate, duration and exact/estimated/unknown size.
- Explicit expired-link, DRM, unsupported-codec, malformed-manifest, unsafe-redirect and network failures without inventing metadata.
- HTTPS-only Media3 progressive/HLS/DASH sources with same-origin browser context and cross-origin credential stripping.
- A lifecycle-safe "Download as" preview sheet over the page with video/audio tabs, a quality list with sizes, a small player with play/pause and seek, Wi-Fi only, retry states and explicit unknown/estimated labels.
- Committed MP4/WebM/audio/HLS/DASH/blob fixtures and MockWebServer/Robolectric/Compose coverage.
- Site adapters for TikTok, Facebook and Vimeo, each isolated behind `:extractor-api`, selected by host, and failing with their own reason for private, login-required, region-blocked, DRM, expired, media-free or changed pages. Supported link patterns and limits are listed in [`docs/SUPPORT_MATRIX.md`](docs/SUPPORT_MATRIX.md).
- A YouTube adapter for single videos: YouTube's embedded-player client is asked first, then the page's own client with the user's session; signature and `n` transforms are solved by the bundled yt-dlp ejs solver in a sandboxed offscreen WebView. Private, age-gated, region-blocked and DRM videos fail with their own reason. Third-party code and licenses are listed in [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md).
- Any other site stays on the generic detection path; no adapter signs in, stores credentials or bypasses an access control.
- Direct, HLS and DASH downloads with pause, resume, retry, a foreground service, Room recovery and verified publication to `Download/YFT` (Android 10+) or app storage.
- Settings for default quality, download location, Wi-Fi only, mobile-data confirmation, concurrent downloads (1–4), theme, clearing browsing data and clearing download history.
- A Library that plays finished files in the app and opens, shares or deletes them (deletion asks first); a Detected Media screen for the last page's candidates; a Home link field whose Paste button reads the clipboard only when tapped.
- Privacy hardening: no cloud backup or device transfer of app data, no cleartext traffic, private download notifications, memory-only candidate URLs and redacted logs.
- A free-space check before downloads of known size, a "Waiting for Wi-Fi"/"No connection" banner, and one cleanup pass per launch for leftover partial files and finished-record growth.
- An About screen with version, scope, privacy and the third-party license texts, and an original light/dark palette that meets WCAG AA contrast.
- An original adaptive launcher icon with a themed monochrome layer and Android 7.x PNGs, plus a launch screen (system splash on Android 12+).
- Release tooling: versioning in `gradle.properties`, signing that never falls back to the debug key, `scripts/release-prep.sh`, `scripts/verify-release-apk.sh` (signature, certificate fingerprint, alignment, upgrade compatibility, SHA-256), `scripts/device-smoke-test.sh` and a draft-only `release-draft` workflow gated by `ALLOW_RELEASE`.
- The Phase 1 Compose, Hilt, Room, DataStore, OkHttp, Media3, redaction, CI and release-build foundations.
- The Phase 0 feasibility harness remains under [`spikes/phase0-media`](spikes/phase0-media/).

## For agents

Read [`AGENTS.md`](AGENTS.md) first. Work is planned task by task in
[`docs/FIX_PLAN.md`](docs/FIX_PLAN.md); ready-to-paste prompts are in
[`docs/prompts/`](docs/prompts/README.md) (start with `00_NEXT_TASK.md`). Development happens on
`work/phase-*` branches with a remote checkpoint (`scripts/checkpoint.sh`) after every task; local
commits and stashes are not handoffs.

Docs: [`PROJECT_CONTEXT`](docs/PROJECT_CONTEXT.md) (goals, privacy) ·
[`ARCHITECTURE`](docs/ARCHITECTURE.md) · [`PHASE_STATUS`](docs/PHASE_STATUS.md) ·
[`HANDOFF`](docs/HANDOFF.md) · [`SESSION_STATE`](docs/SESSION_STATE.md) ·
[`SUPPORT_MATRIX`](docs/SUPPORT_MATRIX.md) · [`TEST_MATRIX`](docs/TEST_MATRIX.md) ·
[`RISKS`](docs/RISKS.md) · [`RELEASE`](docs/RELEASE.md) · [`decisions/`](docs/decisions/) ·
[`design/`](docs/design/DESIGN-NOTES.md) · [`THIRD_PARTY_NOTICES`](docs/THIRD_PARTY_NOTICES.md).

## Product boundaries

YFT will not attempt to bypass DRM, payment protection, private access controls, age checks or account sign-in. For public videos the owner allows any technique that works ([ADR-006](docs/decisions/ADR-006-owner-override-any-working-method.md)): app or device client identities, proof-of-origin tokens, bot-check workarounds and the user's own browser session. Website-specific support remains isolated behind adapters and may only be claimed when backed by tests.

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

A local release check can additionally run `:app:assembleRelease`; the output is `app-release-unsigned.apk` unless release signing is configured as described in [`docs/RELEASE.md`](docs/RELEASE.md). CI builds that unsigned release APK on every work-branch push and checks it with `scripts/verify-release-apk.sh --allow-unsigned`.

The Phase 7 release-preparation run (`scripts/release-prep.sh` with a throwaway key, without the build cache) passed 459 tests, Android lint with no errors, the signed minified release build and APK signature, certificate and checksum verification; the Phase 6 completion matrix had passed 454 tests. `lintDebug` needs network access the first time, because `lint-gradle` is not in the offline cache; every other task in the matrix runs with `--offline`. No device/emulator was available, so real Android WebView rendering, Media3 playback, `MediaExtractor`/`MediaMuxer` behavior, foreground-service lifecycle, `MediaStore` publication, Library sharing and Wi-Fi switching remain explicit runtime verification items. Browser policy, bounded network behavior, manifests, transfer/recovery logic, state and Compose surfaces are covered locally. The YouTube path still needs a device check on a residential or mobile network; the owner test steps are in [`docs/HANDOFF.md`](docs/HANDOFF.md), and each work-branch CI run uploads the debug APK as the `yft-debug-apk` artifact.

See [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md), [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) and [`docs/HANDOFF.md`](docs/HANDOFF.md) before continuing.

On machines with about 4 GiB RAM and no swap, run the fresh lint/test/debug matrix and `:app:assembleRelease` as separate Gradle invocations to avoid R8 competing with test/compiler workers.
