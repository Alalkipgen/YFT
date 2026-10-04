# YFT Architecture

## Architectural style

YFT will use feature-first packages over a small set of Gradle modules, with unidirectional UI state and interface boundaries around detection, extraction and downloading.

```text
Compose UI
  -> ViewModel / StateFlow
  -> Use cases
  -> Detector and Extractor Registry
  -> Candidate Normalizer / Variant Resolver
  -> Download Planner
  -> Direct / HLS / DASH / Mux engines
  -> Room, OkHttp, Media3, WebView, MediaStore
```

The planned production modules are:

```text
:app
:core-model
:core-data
:core-browser
:core-media
:core-download
:extractor-api
:extractor-generic
:extractor-sites
```

UI features remain packages under `:app` until their size justifies separate modules:

```text
feature/home
feature/browser
feature/detectedmedia
feature/preview
feature/downloads
feature/library
feature/settings
feature/about
```

## Technology decisions

| Area | Decision |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| UI state | ViewModel + StateFlow, unidirectional events/state/effects |
| Dependency injection | Hilt, unless Phase 1 identifies a build blocker |
| HTTP | OkHttp |
| Database | Room |
| Preferences | DataStore |
| Playback/manifest support | AndroidX Media3 |
| Active downloads | Foreground service |
| Maintenance work | WorkManager only where appropriate |
| Public storage | MediaStore |
| User-selected storage | Storage Access Framework |
| CI toolchain | JDK 17, Gradle 8.9, Android SDK 35 |

## Domain model

### MediaCandidate

A raw observation from DOM, WebView requests, DownloadListener, manifest discovery or a site adapter.

Required fields:

- page URL and candidate URL
- detector source
- MIME/manifest hint
- title, thumbnail and duration when known
- replayable request context or secure session reference
- confidence and expiry hints
- DRM hint

### MediaAsset

A normalized logical media item, grouping candidates from the same page/content identity.

### MediaVariant

A real selectable representation such as 1080p MP4, 720p HLS or audio-only M4A. Unknown metadata stays unknown.

### DownloadPlan

A sealed plan type:

- `Direct`
- `SegmentedDirect`
- `Hls`
- `Dash`
- `AudioVideoMux`

### DownloadTask

Persisted transfer state including progress, destination, retry information and structured failure reason.

## Core interfaces

```kotlin
interface MediaDetector {
    suspend fun detect(context: DetectionContext): List<MediaCandidate>
}

interface MediaExtractor {
    fun supports(pageUrl: String): Boolean
    suspend fun extract(context: ExtractionContext): ExtractionResult
}

interface VariantResolver {
    suspend fun resolve(candidate: MediaCandidate): ResolutionResult
}

interface DownloadPlanner {
    suspend fun plan(asset: MediaAsset, variant: MediaVariant): DownloadPlan
}

interface DownloadEngine<P : DownloadPlan> {
    suspend fun run(plan: P, observer: ProgressObserver): DownloadResult
}
```

## Detection pipeline

1. `DownloadListenerDetector` observes browser-declared downloads.
2. `DomMediaDetector` reads `video`, `audio` and `source` elements without modifying the page.
3. `RequestUrlDetector` observes HTTP(S) GET URLs and request headers.
4. `ManifestDetector` recognizes HLS and DASH URLs.
5. `SiteHintDetector` routes known pages to an isolated adapter.
6. `CandidateNormalizer` removes stale, duplicate and obvious non-media entries.

A literal `blob:` URL is not a downloadable file. The detector must find the underlying request, manifest or page-provided source.

Android WebView exposes observed request URLs and request headers through `shouldInterceptRequest`, but not a convenient passive response-body/MIME stream. Phase 2 should combine URL/DOM observations with bounded OkHttp probes rather than proxying every WebView response.

The Phase 2 implementation applies those bounds explicitly:

- at most 200 raw observations and 50 normalized candidates are retained per page;
- metadata probing is limited to 20 unique strongly hinted URLs per page and two concurrent calls;
- each probe has a 10-second call timeout and at most five manual redirects;
- `HEAD` is preferred, with a one-byte range `GET` fallback only when necessary;
- navigation cancels in-flight probes and immediately clears stale candidates;
- browser credentials are replayed only on the original origin; cross-origin redirects retain only non-sensitive negotiation headers.

## Request context

Preview and download must be able to replay only the required values:

- Cookie
- Referer
- User-Agent
- Accept
- Site-required non-hop-by-hop headers

The application owns Range and If-Range headers. It must strip connection-specific headers and never log cookies, access tokens or full signed URLs.

## Preview and stream handling

Media3 is the production preview engine for direct and non-DRM HLS/DASH media. Phase 3 implements:

- a 10-second bounded resolver with at most five manual redirects;
- direct `HEAD` metadata with a one-byte range fallback that never intentionally reads the full media body;
- HLS master/media parsing for real streams, audio renditions, resolution, FPS, codec and bitrate;
- secure, external-entity-disabled DASH parsing for real video/audio representations and duration;
- exact direct sizes and bitrate/duration estimates explicitly marked as estimated; absent data remains unknown;
- preflight expiry, DRM, malformed-manifest and unsupported-codec failures;
- explicit Progressive, HLS and DASH Media3 sources over HTTPS;
- an origin-aware OkHttp policy that replays browser context to the credential origin and removes credential/custom context headers cross-origin.

Candidate selection is kept only in a singleton in-memory flow. Signed URLs, cookies and browser context are not placed in navigation routes, saved state or Room. The "Download as" preview sheet (a navigation dialog destination holding a Material modal bottom sheet) owns a lazy player for the selected representation, disables autoplay, releases it with the composition and exposes generic playback errors without upstream diagnostics.

Offline/export behavior is separate from preview:

- Direct files use the reusable segmented HTTP design.
- HLS/DASH initially use selected-track segment download.
- Separate audio/video tracks require an explicit mux step.
- A video-only file with a companion audio file (YouTube's adaptive streams, T17) is planned as an
  `AudioVideoMux` of two whole-file DASH tracks (`DashDownloadPlan.wholeFile`). Each track is cut
  into byte ranges of at most 10 MiB, kept as resumable chunks whose fingerprint names the track
  and its length but not the address, so a refreshed address keeps finished chunks. Preview plays
  the same pair through a `MergingMediaSource`.
- Direct plans can bound each ranged request (`DirectDownloadPlan.maxRequestBytes`); YFT does so
  for `googlevideo.com`, which slows down larger single requests.
- A large FFmpeg dependency is not approved in Phase 0. Phase 4 must evaluate Android `MediaMuxer`, container compatibility and licensing/size before adding a fallback.

## Background execution

- Active transfers run through a foreground service with a persistent notification.
- Room stores tasks and recovery state.
- WorkManager is reserved for cleanup, bounded retry or link refresh, not as the only large-transfer engine.
- Process restart restores tasks safely; it never marks an incomplete file as completed.

## Security boundaries

- HTTPS by default; TLS verification is never disabled.
- WebView file and content access disabled unless a documented feature requires them.
- Mixed content blocked in production.
- JavaScript bridge avoided by default and narrowly scoped if introduced.
- Filenames sanitized against traversal and reserved names.
- Sensitive request context redacted from logs and crash reports.
- DRM manifests produce a structured unsupported result.
- Site adapters cannot execute unsigned remote code.

## Release build and signing

- `yft.versionName` and `yft.versionCode` in `gradle.properties` are the single version source for Gradle, `scripts/release-prep.sh` and the release workflow; the Git tag is `v<versionName>`.
- The release build type is minified and resource-shrunk; `app/proguard-rules.pro` keeps `@JavascriptInterface` methods so the solver bridge keeps its `post` name.
- Release signing is resolved at configuration time from `YFT_RELEASE_*` environment variables, then per key from the untracked `keystore.properties`. A partial configuration or a missing keystore fails the build, `-Pyft.requireReleaseSigning=true` fails when signing is absent, and an unconfigured build stays unsigned: the debug key is never used for release. Signatures are APK Signature Scheme v2 + v3.
- `scripts/verify-release-apk.sh` is the gate for every distributed APK (package, version, not debuggable, alignment, one non-debug signer, certificate pin, upgrade rules, SHA-256). The `Release draft` workflow only drafts; publishing needs `ALLOW_RELEASE=true` and a manual request. Details: `docs/RELEASE.md`.

## Reuse strategy

AlalDownloader is MIT licensed and owned by the same GitHub account. YFT may adapt its tested download-engine concepts, preserving applicable license notices. The production YFT code should use YFT models/interfaces and remain independently buildable.

## Phase 1 implemented foundation

The production project now implements the planned nine-module graph. Dependency direction is intentionally one-way:

```text
:app
  -> Android core modules and extractor implementations
:core-download
  -> :core-data -> :core-model
:core-browser, :core-media
  -> :core-model
:extractor-generic, :extractor-sites
  -> :extractor-api -> :core-model
```

The current feature packages under `:app` are:

```text
feature/home
feature/browser
feature/detectedmedia
feature/preview
feature/downloads
feature/library
feature/settings
feature/about
```

All eight routes are real Compose destinations. Browser/detection and preview/variant behavior were added in Phases 2 and 3 respectively; download, library export and site-adapter behavior remain phase-scoped.

### State and settings

`AppViewModel` exposes immutable `StateFlow<AppUiState>` and accepts explicit UI actions. The selected system/light/dark theme is persisted through a `SettingsRepository` backed by Preferences DataStore. UI code depends on the repository interface rather than DataStore directly.

### Persistence and migrations

`AppDatabase` is a Room v2 database with exported schemas. Its baseline `download_records` table stores non-sensitive lifecycle metadata. Migration `1→2` adds a nullable structured error code and is validated against the exported schemas. Database construction registers the migration explicitly and does not use destructive fallback.

### Dependency injection and service foundations

Hilt provides the Room database/DAO, singleton Preferences DataStore, default OkHttp client, logger bindings and Media3 player factory. OkHttp retains platform certificate and hostname verification and has no request/response logging interceptor. `MediaPlayerFactory` creates players lazily; it does not keep a process-wide player alive.

### Logging and error boundaries

Shared `AppResult` and `AppError` types provide structured success/failure handling. `SensitiveValueRedactor` removes sensitive headers, bearer credentials, passwords, tokens and signed query values before Android logging. Throwable messages and stack traces are not forwarded by the production logger because they may contain request secrets.

### Build variants and CI

- Debug application ID: `com.alal.yft.debug`
- Release application ID: `com.alal.yft`
- Release minification/resource shrinking: enabled
- Release signing: intentionally absent until Phase 7
- CI: Android lint, Android/JVM unit tests and debug assembly on `work/**` pushes and pull requests to `main`
