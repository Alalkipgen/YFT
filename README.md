# YFT — Video Downloader

YFT is an ad-free Android application for detecting, previewing and downloading authorized non-DRM media from direct links, HTML5 players, HLS and DASH streams.

**Phase 5 and Phase 5E are complete.** TikTok, Facebook, Vimeo and — by owner decision ([ADR-005](docs/decisions/ADR-005-youtube-owner-override.md)) — YouTube extractors run behind the extractor API with offline fixtures and a clean fallback to generic detection. YouTube support covers single videos as progressive MP4 (usually up to 360p) plus M4A audio; its risks and limits are recorded in [`docs/YOUTUBE_RISK_REVIEW.md`](docs/YOUTUBE_RISK_REVIEW.md). YFT is distributed through GitHub only.

**Phase 6 — hardening, privacy, performance and UI polish — is complete**; every finding in [`docs/HARDENING_AUDIT.md`](docs/HARDENING_AUDIT.md) is fixed, compliant or device-only.

**Phase 7 — signed beta and GitHub release preparation — is prepared** on `work/phase-7-release`: version `1.0.0-beta.1`, an original launcher icon and launch screen, release signing from environment variables or an untracked `keystore.properties`, APK verification and checksum scripts, a device install/upgrade script and a draft-only release workflow. The owner's permanent release keystore is not configured yet, so **no signed beta APK exists and nothing has been published**; [`docs/RELEASE.md`](docs/RELEASE.md) lists the owner steps. All behavior below is verified with JVM, Robolectric and fixture tests; on-device checks that this environment cannot run are listed in [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md).

## Install the beta

Requires Android 7.0 (API 24) or newer; the app targets Android 15 (API 35). Once the owner publishes a release, download `video-downloader-<version>.apk` and `SHA256SUMS` from the repository's GitHub Releases page, check the file with `sha256sum -c SHA256SUMS` (or `Get-FileHash` on Windows), open the APK on the phone and allow your browser or file manager to install unknown apps when Android asks. Later releases signed with the same key update in place. Details and known issues: [`docs/release/1.0.0-beta.1.md`](docs/release/1.0.0-beta.1.md) and [`CHANGELOG.md`](CHANGELOG.md).

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

A local release check can additionally run `:app:assembleRelease`; the output is `app-release-unsigned.apk` unless release signing is configured as described in [`docs/RELEASE.md`](docs/RELEASE.md). CI builds that unsigned release APK on every work-branch push and checks it with `scripts/verify-release-apk.sh --allow-unsigned`.

The Phase 6 completion matrix passed 454 tests, Android lint with no errors, debug assembly and the minified unsigned release build. `lintDebug` needs network access the first time, because `lint-gradle` is not in the offline cache; every other task in the matrix runs with `--offline`. No device/emulator was available, so real Android WebView rendering, Media3 playback, `MediaExtractor`/`MediaMuxer` behavior, foreground-service lifecycle, `MediaStore` publication, Library sharing and Wi-Fi switching remain explicit runtime verification items. Browser policy, bounded network behavior, manifests, transfer/recovery logic, state and Compose surfaces are covered locally. The YouTube path still needs a device check on a residential or mobile network; the owner test steps are in [`docs/HANDOFF.md`](docs/HANDOFF.md), and each work-branch CI run uploads the debug APK as the `yft-debug-apk` artifact.

See [`docs/PHASE_STATUS.md`](docs/PHASE_STATUS.md), [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) and [`docs/HANDOFF.md`](docs/HANDOFF.md) before continuing.

On machines with about 4 GiB RAM and no swap, run the fresh lint/test/debug matrix and `:app:assembleRelease` as separate Gradle invocations to avoid R8 competing with test/compiler workers.
