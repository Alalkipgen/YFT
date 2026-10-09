# Master Extractor backup prototype

**Android connection in progress — build opt-in only; not approved for merge.**

The owner approved a separate backup branch, `spike/master-extractor-backup`, based on stable
`main` `a9eea7ba8d9f3d67442ffc3a51f2ad9e00c4a6a9`. The later owner-approved Android follow-on
adds an Android producer and a build-opt-in browser caller. Existing site-extractor modules,
download engine, release identity, CI workflows and main/Phase 15 branches stay untouched.

## What exists

```text
recoverable primary extraction failure
  -> provided current-page HTML/player/API data
  -> at most one browser-capture-provider request
  -> current-video selection, normalization and bounded HTTPS media checks
  -> ordinary SiteExtractionResult.Success / MediaCandidate values
```

- `MasterFallbackEngine` is a second-choice service, **not** a `SiteExtractor` that competes
  for the same URLs in `SiteExtractorRegistry`. It never invokes the failed adapter.
- `PayloadMediaReader` reads a small set of already-delivered field shapes: direct YouTube
  player formats, Facebook progressive/inline-DASH data, TikTok play/bitrate addresses,
  Instagram `video_versions`/`video_url`, X `video_info.variants`, HTML video/source and
  Open Graph video metadata. These are discovery readers, not complete new site adapters.
- `InMemoryCaptureStore` is a per-tab producer/consumer boundary with navigation generation
  checks. A future host can feed real observed requests, raw page/API data and playback focus.
- `OkHttpMediaValidator` checks HTTPS media using 512 direct-file bytes or a bounded manifest.
  It checks response status, media MIME/file headers, known expiry, manifest DRM hints,
  redirect budget and origin-scoped credentials, without a cookie jar or TLS bypass.
- Video companions are independently checked within the same total probe budget. Successful
  audio checks can be reused; a failed audio link is never offered as a verified merged row.
- Explicit captured preview evidence vetoes page metadata for the same normalized file.
  Known site content IDs retain their existing grouping namespaces.
- `Success` means **probe-validated candidates**, not a completed playable download. Final
  variant resolution, codec compatibility, DRM checks, track validation and export still
  belong to the existing production media/download pipeline.

## Reuse and provenance

Actual shared code is reused through module dependencies, not copied:

- `:extractor-api`: `BoundedJsonParser`, JSON accessors and `SiteExtractionResult`.
- `:core-model` via the API: `MediaCandidate`, `BrowserRequestContext`, `CompanionAudio`,
  and `DiagnosticTextSanitizer`.
- `:extractor-generic`: `MediaUrlClassifier`, `MediaFileUrls`, `CandidateNormalizer`,
  and `ManifestReader`.

The prototype adapts mechanisms already used by the site adapters:

| Source | Adapted idea | Kept site-specific / not copied |
| --- | --- | --- |
| YouTube | Ordered bounded fallback, direct player formats, AVC/AAC companions, safe Details | Client/API requests, PoToken, player-script/cipher execution, SABR |
| Facebook | Progressive fields and secure inline whole-file MPD tracks | Public/session page strategy, Facebook endpoints and account behavior |
| TikTok | Current content-ID match, alternate page-payload shapes, request identity | TikTok endpoint/cookie acquisition and active hidden-page logic |
| Generic | URL/MIME classification, normalization and playback focus | Full production browser/player/ad interpretation |

Relevant originals: `extractor-sites/.../{youtube,facebook,tiktok}`, the shared helpers above,
and `app/.../detection/OkHttpExtractorClient.kt` for cancellation/redirect boundaries.
Existing internal site-parser visibility is unchanged.

## Deliberately not implemented

- No registry entry, Home/pasted-link capture, or default-enabled feature. The new browser
  caller is gated by `-Pyft.masterCapture=true` in debug/preview; release always disables it.
- No document-start injection. The new Android host observes visible-WebView requests and
  bounded delivered page/API data; the JVM store itself still does not collect or play.
- No unattended sign-in, access-control/age-gate bypass, DRM decryption or live recording.
- No guessed/synthetic download URL, signature solver or blanket “supports every site” claim.
- No guarantee of every quality, carousel/feed selection, ad exclusion or live Instagram/X
  operation. The Instagram/X fixtures prove only the stated delivered-data shapes.
- No new APK/release/tag and no merge into `main` or the ongoing TikTok/Phase 15 work.

## Host boundary example — future integration only

```kotlin
val store = InMemoryCaptureStore()
store.navigate(pageUrl, generation)
// Browser host: obtain headers/cookies for this exact observed media request origin.
store.recordRequest(
    generation,
    CapturedRequest(mediaUrl, mimeType = mime, context = mediaRequestContext),
)
store.playing(generation, playingMediaUrl, authorized = true)

val engine = MasterFallbackEngine(
    validator = OkHttpMediaValidator(okHttpClient),
    capture = store,
    policy = MasterPolicy(enabled = true),
)
val result = engine.extract(
    MasterRequest(
        pageUrl = pageUrl,
        generation = generation,
        nowEpochMs = now,
        primaryFailure = failure.reason,
        expectedContentId = identity?.contentId,
    ),
)
```

The integration host must:

1. Leave successful primary results untouched and respect disabled site feature flags.
2. Call this service outside the registry, once per bounded lookup.
3. Increment the generation on full/SPA/focus navigation and discard old requests.
4. Keep page/API text, signed URLs and credentials in memory only.
5. Bind captured context to the actual request origin; never populate it from page JSON.
6. Provide explicit current-video evidence. Ambiguous results return `NeedsSelection`.
7. Handle `NeedsPlayback` honestly; no capture implementation is silently substituted.
8. Preserve ordinary production resolver/planner gates before preview/download.
9. Keep parsing/probing off the UI thread; marshal actual WebView producer operations to the
   main thread. This pure JVM spike does not supply Android thread/permission handling.

DRM, private/unavailable, geo, disabled-adapter, network and rate-limit primary failures are
skipped. Login/bot-check/player-script failures require evidence of successful authorized
browser playback; the service does not solve those checks.

## Bounds

- Default disabled; explicit `MasterPolicy(enabled = true)` is required.
- Snapshot: 2 MiB of characters, 200 request observations, 16 API documents.
- Store: 64 KiB per request/context; reject oversize values rather than truncating credentials.
  Navigation generations cannot be reused, even after an explicit clear.
- Discovery: 200 raw candidates, 30,000 visited JSON nodes, depth 48.
- Lookup: at most one capture request and 16 media-check slots shared by both stages.
- Engine deadline: 20 seconds; a media check has a 10-second total call/redirect deadline.
- HTTPS redirects: at most five, no downgrade, no credentials restored after leaving origin.
- Application file-prefix read: 512 bytes. Manifest: 256 KiB. No intentional whole-video
  read; network buffers and browser playback are separate from this validation budget.
- `NeedsSelection`/`NeedsPlayback` are not success. There is no screen recording.

## Validation

Use the normal repository JDK 17/Android SDK environment:

```bash
./gradlew --no-daemon :extractor-master:test \
  :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test
```

Validated hardening milestone: 77 module tests, zero failures/errors/skips, with the opt-in
real capture smoke supplied. The unchanged baseline's four JVM suites passed 360 tests with
zero failures/errors/skips. TLS tests trust an explicit fixture certificate; verification is
never disabled. Initial pushed checkpoint: `f8e7451` (56 module tests).

### Final regression proof

Code checkpoint: `35213502d29690903e9cbd0dc3d68bf3aaab989d`.

- After restoring the hardened sources, all five JVM suites passed offline: 436 passed,
  zero failures/errors and one intentional optional live-smoke skip (Master 76 passed,
  baseline 360 passed). The earlier real-capture run passed all 437 with no skips.
- Temporarily substituting the compatible pre-hardening engine made five regression tests
  fail as expected: shared companion budget, preview veto, failed-audio rejection, multiple
  formats for one HTML player, and checking explicitly identified small media.
- Temporarily bypassing direct-file signature classification made the intended false-MIME
  test fail. These expected failures prove the checks detect the tested bad behavior; they
  are not unresolved failures in the hardened code.
- Both temporary substitutions were restored byte-for-byte before the final passing run.
  No mutation, capture snapshot, build output or temporary artifact is committed.
- Repository script tests passed: 30 tests. Diff, scope and sensitive-file checks passed.

Leave this branch as an inactive backup. This validation does not approve integration or
establish live YouTube/Facebook/TikTok/Instagram/X fallback coverage.

### Real browser-to-fallback smoke

A neutral MDN CC0 flower example was played in the shared Chromium browser. The observed
MP4 response was HTTP 206; playback reported 960 x 540 and a 5.055-second duration.
Two successful requests for the playing media were captured. A fresh in-memory store then
supplied that evidence to `MasterFallbackEngine` after a deliberately empty primary payload.
The real `OkHttpMediaValidator` independently checked the public media, and the result was
`PLAYBACK_CAPTURE` success. This is a generic sample smoke, **not** a live site-adapter,
Android WebView, full download/mux, or Instagram/X support proof.

The live snapshot is outside the repository; no browser bodies, live session or signed URLs
are committed. Optional smoke invocation:

```bash
YFT_MASTER_SMOKE_SNAPSHOT=/path/to/public-browser-capture.json \
  ./gradlew --no-daemon :extractor-master:test
```

Without that variable, the 76 offline tests run and the one optional smoke is skipped.
The environment/file are declared Gradle test inputs, so changing the capture reruns tests.

`spike/**` does not match the existing CI push filters. Do not claim automatic green CI or
a preview APK for this branch. No workflows are changed. This owner-approved branch also
does not match `scripts/checkpoint.sh`; checkpoints use equivalent manual staged-file,
secret, diff and test checks followed by an explicit push to this branch only.

The sandbox reset after the initial SSH checkpoint. The pushed branch was recovered by HTTPS
and verified through the connected GitHub MCP; further file-backed pushes use that connection
without another SSH key enrollment.

Before considering merge: finish real Android playback and lifecycle validation, check current
public site pages and focused selection, verify companion tracks and signed-link refresh, then
obtain separate owner approval for merge.

## Owner-approved Android follow-on

The owner subsequently approved Android Play & Capture connection on this same backup branch.
`:extractor-master-android` now supplies a visible-WebView capture boundary; see its README for
bounds, tests and limitations. Its initial module milestone passed 15 unit tests and lint.
Build-opt-in app wiring is now present; full validation and actual Android playback tests are
in progress. Merge/release remains unapproved. See `extractor-master-android/README.md`.

Initial app-wiring validation passed 18 Android module tests and 826 app cases, zero
failures/errors and 66 existing app skips; lint and Android test compilation passed. The
opt-in property enabled debug/test flags but did not enable release. Focus/GET hardening and
actual Android playback validation are in progress, not yet an end-to-end site-support claim.