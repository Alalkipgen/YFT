# Handoff

## Current handoff

- Date: 2026-10-01
- Phase: 2 — Built-in browser and generic media detection
- Status: COMPLETE — local and remote completion validation green
- Active branch: `work/phase-2-browser-detection`
- Phase 2 base: Phase 1 completion commit `4f5e06a`
- Target repository: `Alalkipgen/YFT`
- Reference repository: `Alalkipgen/AlalDownloader`

## Work completed

- Preserved the complete Phase 1 app, module graph, DI/data/media foundations and CI checks.
- Added shared media-candidate models for source, kind, MIME, title, thumbnail, duration, size, request context, confidence, expiry and DRM hints.
- Added replay-safe browser request context that strips hop-by-hop and app-owned range headers and redacts sensitive `toString()` output.
- Added direct-media, HLS and DASH URL/MIME classification.
- Added page-scoped candidate normalization with:
  - signed/volatile query deduplication without mutating the real request URL
  - metadata, context and source merging
  - 200 raw-observation and 50 normalized-candidate limits
  - debounced publication
  - tiny/tracking/non-media rejection
  - immediate navigation cleanup and stale-page rejection
- Added hardened browser policy:
  - HTTPS-only top-level addresses plus `about:blank`
  - safe browsing and mixed-content blocking
  - file/content/universal-file access disabled
  - JavaScript popups and multiple windows disabled
  - first-party cookies accepted and third-party cookies blocked
  - default TLS validation preserved; SSL errors are explicitly cancelled
- Added browser observations from `DownloadListener`, HTTP(S) GET requests and a read-only DOM probe covering `video`, `audio`, nested `source` and common social media metadata.
- Literal `blob:` URLs are rejected; their underlying HTTP(S) request, source or manifest can still produce a candidate.
- Added a cancellable metadata probe using shared OkHttp:
  - `HEAD` first with one-byte range `GET` fallback
  - 10-second call timeout and five-redirect limit
  - MIME/content-length enrichment
  - same-origin browser-context replay
  - cross-origin cookie, authorization, referer and custom-header stripping
- Added a per-page probe budget of 20 deduplicated strongly hinted URLs and two concurrent probes; navigation cancels active probe coroutines.
- Replaced the Browser placeholder with a production Compose/WebView screen containing address, back/forward, reload/stop, progress, page-title and safe error controls.
- Added a media-found floating button and bottom sheet. No preview or download action was added; unavailable metadata is shown as unknown.
- Added committed generic HTML/golden fixtures and end-to-end coverage for MP4, WebM, audio, HLS, DASH, redirects, blob-backed playback, duplicates, navigation cleanup and cookie/header context.
- Updated user-facing copy and Phase 2 architecture, support, status and test documentation.

## Important decisions

- Phase 2 remains generic. No website-specific adapters, preview resolver or download engine was introduced.
- A literal `blob:` URL is never a downloadable candidate.
- WebView traffic is observed, not proxied. Only known or strongly hinted URLs receive a bounded metadata probe.
- Browser credentials may be replayed only to the original media origin. Cross-origin redirects retain only safe negotiation headers.
- Metadata probing never intentionally consumes a media body; it closes after headers and uses `Range: bytes=0-0` only as fallback.
- Candidate URLs keep their real query values for later use, while deduplication keys omit volatile signing fields.
- Candidate/UI logging uses redacted representations; cookies, tokens, full signed URLs, keys and credentials are not logged.
- Candidate state is page-scoped and intentionally not persisted because browser context and signed links can be sensitive or stale.
- Android WebView runtime behavior is not claimed as device-tested because no emulator/device is available in this environment.

## Main files and areas

- Browser UI and state: `app/src/main/java/com/alal/yft/feature/browser/`
- Secure WebView policy/callbacks: `core-browser/src/main/java/com/alal/yft/core/browser/policy/`, `core-browser/src/main/java/com/alal/yft/core/browser/webview/`
- Observation and probing: `core-browser/src/main/java/com/alal/yft/core/browser/detection/`
- Page-scoped limits/state: `core-browser/src/main/java/com/alal/yft/core/browser/session/`
- Candidate models/context: `core-model/src/main/kotlin/com/alal/yft/core/model/media/`
- Generic classification/normalization: `extractor-generic/src/main/kotlin/com/alal/yft/extractor/generic/`
- Fixtures: `core-browser/src/test/resources/fixtures/`
- Browser/pipeline/UI tests: `core-browser/src/test/`, `app/src/test/java/com/alal/yft/feature/browser/`
- Phase continuity: `README.md`, `docs/PHASE_STATUS.md`, `docs/TEST_MATRIX.md`, `docs/SESSION_STATE.md`

## Validation

Phase 1 was green before Phase 2 began. The exact production DOM script was executed against the committed HTML fixture in headless Chromium:

```text
10 observations, 6 unique URLs, 0 expected URLs missing
```

Fresh full Phase 2 validation:

```bash
./gradlew --no-daemon --rerun-tasks \
  lintDebug testDebugUnitTest \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test \
  :app:assembleDebug
./gradlew --no-daemon --rerun-tasks :app:assembleRelease
```

Results:

- lint/tests/debug: **BUILD SUCCESSFUL** in 3m 39s; 294/294 tasks executed.
- minified release: **BUILD SUCCESSFUL** in 4m 27s; 207/207 tasks executed.
- 56 tests total, 0 failures, 0 errors and 0 skipped.
- debug APK: 14,785,313 bytes; SHA-256 `3affe513b28daeccc785a2cea3fab19232f9e4f4674c68390e6aaa38b459109a`.
- unsigned minified release APK: 1,650,024 bytes; SHA-256 `3a72f918fd96c049f4e7afc9e6ff18c93088d88de082518ca86c58b4e3e52ec3`.
- Phase 2 completion commit `d7d25b63176236d341310953e489b0c604d37f82` passed GitHub Actions run `36799479295`.

## Known limitations

- No physical Android device/emulator is attached and `/dev/kvm` is unavailable. Real Android WebView rendering, history and callbacks still require on-device confirmation.
- Headless Chromium validates the exact read-only DOM script against the committed page, but it is not a substitute for Android WebView runtime testing.
- Candidate detection does not resolve playable variants or prove that an observed URL remains valid; that is Phase 3.
- Preview, DRM inspection and playback are not implemented in the production flow; Media3 remains foundation/spike code only.
- No download, storage export, background service or recovery engine is implemented; those belong to Phase 4.
- The separate Detected Media route is not a cross-session catalog; Phase 2 candidates are shown from the current browser page.
- The release APK is unsigned; signing and publication remain Phase 7 and require explicit approval.
- GitHub Advanced Security secret scanning is not enabled for this repository. Local structured-secret checks run before every MCP push.
- KAPT emits a Kotlin 2.0 language fallback warning while generating Hilt/Room code; compilation and tests pass.
- No work branch was merged into `main`, and no release was published.

## Next exact action

1. Stop. Phase 3 is not started and requires explicit authorization.
2. If Phase 3 is explicitly authorized later, branch from the final Phase 2 head, reread continuity docs and verify the Phase 2 candidate pipeline before editing.

## Phase 2 checkpoint commits

- Kickoff from the green Phase 1 base: `73d7423`
- Candidate model/classification/normalization foundation: `cb4f3fd`
- Secure WebView policy and observation/session core: `fe91576`
- Bounded metadata probe: `f175fd4`
- Browser UI and bounded request-probe integration: `f91c8d4`
- Generic fixture regression and cancellation/error hardening: `9e4a053`

The final documentation/phase-completion commit is newer; resolve it with `git log -1 --oneline` on `work/phase-2-browser-detection`.
