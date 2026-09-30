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

## Request context

Preview and download must be able to replay only the required values:

- Cookie
- Referer
- User-Agent
- Accept
- Site-required non-hop-by-hop headers

The application owns Range and If-Range headers. It must strip connection-specific headers and never log cookies, access tokens or full signed URLs.

## Preview and stream handling

Media3 is the first choice for direct preview and non-DRM HLS/DASH playback. The Phase 0 spike compiles explicit Progressive, HLS and DASH `MediaSource` creation using replay headers.

Offline/export behavior is separate from preview:

- Direct files use the reusable segmented HTTP design.
- HLS/DASH initially use selected-track segment download.
- Separate audio/video tracks require an explicit mux step.
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

## Reuse strategy

AlalDownloader is MIT licensed and owned by the same GitHub account. YFT may adapt its tested download-engine concepts, preserving applicable license notices. The production YFT code should use YFT models/interfaces and remain independently buildable.
