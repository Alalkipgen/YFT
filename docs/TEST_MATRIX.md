# Test Matrix

## Phase 0 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Preview kind recognizes `.m3u8` with query | JVM unit test | HLS | PASS |
| Preview kind recognizes `.mpd` with fragment | JVM unit test | DASH | PASS |
| Other HTTPS media defaults to direct | JVM unit test | Direct | PASS |
| Replay headers strip Range/Connection | JVM unit test | Stripped | PASS |
| Replay headers preserve safe browser context | JVM unit test | Cookie/UA/HTTPS Referer available | PASS |
| DOM probe targets HTML media elements | JVM unit test | Read-only script structure | PASS |
| Media3 direct/HLS/DASH sources compile | Android debug build | Build succeeds | PASS |
| Android lint | Local Android toolchain | No blocking errors | PASS |
| Phase 0 CI workflow | GitHub Actions, JDK 17, SDK 35, Gradle 8.9 | Lint, tests and debug build pass | PASS — run 36783628412 |
| Checkpoint safeguards | Bash/temp repositories/YAML parser | Branch, secret and syntax checks behave correctly | PASS |

## Phase 1 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Production module graph | Gradle 8.9 | All nine modules configure | PASS |
| App starts at Home route | Robolectric Compose | Home semantics visible | PASS |
| Required destinations | Robolectric Compose | Browser, Detected Media, Preview, Downloads, Library, Settings and About open | PASS |
| Back navigation | Robolectric Compose | Every destination returns to Home | PASS |
| Theme state reducer | JVM unit test | System/light/dark actions are deterministic | PASS |
| Theme persistence | DataStore unit test | System default and saved selection | PASS |
| Room baseline DAO | Robolectric Room test | Upsert/count/find operate | PASS |
| Room migration 1→2 | MigrationTestHelper | Row preserved; nullable error code added | PASS |
| Result/error model | JVM unit test | Success maps; failure is preserved | PASS |
| Secret redaction | JVM unit test | Cookie/auth/password/token/signature values removed | PASS |
| OkHttp foundation | JVM unit test | Bounded timeouts, redirects, no logging interceptors | PASS |
| Android lint | Local JDK 17/SDK 35 | No blocking findings across Android modules | PASS |
| Debug build | Local JDK 17/SDK 35 | APK assembled | PASS |
| Minified release build | Local JDK 17/SDK 35 | Unsigned release APK assembled | PASS |
| Full work-branch CI | GitHub Actions | Lint, all Android/JVM tests and debug build pass | PASS — final run 36793372961 |

## Phase 2 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| HTTPS address policy | JVM/Robolectric | Auto-prefix HTTPS; reject HTTP, scripts, malformed/user-info URLs | PASS |
| Hardened WebView settings | Robolectric | Mixed content/file access/popups/third-party cookies disabled | PASS |
| Main-frame navigation/errors | Robolectric | Block insecure URLs; surface HTTP/TLS/network failures safely | PASS |
| Read-only DOM probe structure | Robolectric | Read `video`/`audio`/`source`/metadata without page mutation | PASS |
| Production DOM script vs committed page | Headless Chromium | All fixture MP4/WebM/audio/HLS/DASH/blob URLs observed | PASS — 10 observations, 6 unique URLs, 0 missing |
| DownloadListener mapping | JVM | MIME/name/size/context become a high-confidence candidate | PASS |
| Request URL/header mapping | JVM | GET media/manifests map; ordinary assets and POST are ignored | PASS |
| Opaque request probe gate | JVM | Strong media hints accepted; deduplicated 20-URL page budget | PASS |
| Metadata MIME and size | MockWebServer | HEAD or one-byte range fallback enriches without body download | PASS |
| Redirect safety | MockWebServer | Five-hop bound; same-origin context retained; cross-origin secrets stripped | PASS |
| Probe cancellation | MockWebServer/coroutines | Navigation cancellation closes an in-flight call | PASS |
| Direct/HLS/DASH classification | JVM | Extension and MIME hints classify accurately | PASS |
| Blob-backed playback rule | Fixture pipeline | Literal blob rejected; underlying request/manifest wins | PASS |
| Candidate normalization | JVM/coroutines | Dedupe, limits, debounce, tiny/tracking rejection and metadata merge | PASS |
| Navigation cleanup | JVM/ViewModel | Candidate/probe state clears immediately; stale observations ignored | PASS |
| Generic pipeline fixture | Robolectric | MP4/WebM/audio/HLS/DASH/redirect/blob/context merge to five candidates | PASS |
| Browser UI state | Robolectric Compose | Media button only with candidates; sheet labels unknown data honestly | PASS |
| Browser app integration | Hilt/Kotlin/Android build | Shared OkHttp injection and WebView route compile | PASS |
| Phase 2 core-browser tests | JUnit/Robolectric/MockWebServer | No failures | PASS — 27 tests |
| Phase 2 app tests | JUnit/Robolectric Compose | No failures | PASS — 10 tests |
| Android lint | Local JDK 17/SDK 35 | No blocking errors | PASS |
| Debug build | Local JDK 17/SDK 35 | APK assembled | PASS |
| Fresh full lint/tests/debug matrix | Local JDK 17/SDK 35 | Every task executes successfully | PASS — 294 tasks in 3m 39s; 56 tests, 0 failures |
| Fresh minified release build | Local JDK 17/SDK 35 | Unsigned release APK assembled | PASS — 207 tasks in 4m 27s |
| Phase 2 completion CI | GitHub Actions, JDK 17/SDK 35 | Work-branch lint/tests/debug workflow passes | PASS — run 36799479295 on `d7d25b6` |

## Phase 3 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Asset/variant invariants | JVM | Unique IDs, valid numeric metadata and paired size accuracy | PASS |
| Sensitive model rendering | JVM | Signed playback URLs and cookies absent from `toString()` | PASS |
| Direct MP4 validation | MockWebServer/coroutines | HEAD metadata produces direct variant and exact size | PASS |
| Range/no-total fallback | MockWebServer/coroutines | One-byte GET used; total size remains unknown | PASS |
| Redirect context safety | Two MockWebServer origins | Same-origin cookie retained; cross-origin cookie removed | PASS |
| Expired candidate | MockWebServer/coroutines | Structured expiry failure before network access | PASS |
| Cookie-protected candidate | MockWebServer | Browser cookie replayed only to original media origin | PASS |
| Multi-variant HLS | MockWebServer/parser | Real resolution/FPS/bitrate variants and separate audio track | PASS |
| DASH representations | MockWebServer/secure XML parser | Video/audio tracks, duration, FPS and estimated sizes | PASS |
| Unsupported codec | HLS fixture | Explicit unsupported-codec failure | PASS |
| DRM manifest | HLS fixture | Explicit DRM failure; no playback source | PASS |
| Malformed manifest | HLS fixture | Explicit malformed-manifest failure; no invented variant | PASS |
| Explicit Media3 source kind | Robolectric | Progressive, HLS and DASH source classes selected correctly | PASS |
| Preview request policy | JVM/Robolectric | HTTPS only; cross-origin browser credentials stripped | PASS |
| In-memory selection boundary | ViewModel/Robolectric | Current candidate selected without route/database persistence; stale candidate rejected | PASS |
| Preview state/errors | ViewModel/coroutines | Resolution, retry, tab switching and DRM/network messages deterministic | PASS |
| Preview Compose surface | Robolectric Compose | Honest metadata, estimated/unknown labels, audio/video tabs and DRM state | PASS |
| Player lifecycle/build integration | Hilt/Android build | Player is lazy, non-autoplaying and released with composition | PASS |
| Phase 3 core-media tests | JUnit/Robolectric/MockWebServer | No failures | PASS — 14 tests |
| Phase 3 app tests | JUnit/Robolectric Compose | No failures | PASS — 16 tests |
| Full Phase 3 matrix | Local JDK 17/SDK 35 | Lint, Android/JVM tests, debug and minified release build pass | PASS — 497 tasks in 4m 12s; 79 tests, 0 failures |

## Phase 4 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Download plan/task invariants | JVM | Valid byte counts, segment ranges, checkpoint/plan-type pairing and redacted plan rendering | PASS |
| Direct ranged transfer | MockWebServer/coroutines | Parallel ranged segments write the exact verified length | PASS |
| Range refusal fallback | MockWebServer/coroutines | Single-stream transfer completes without ranges | PASS |
| Validator mismatch | MockWebServer/coroutines | Resume is refused and the transfer restarts instead of stitching bytes | PASS |
| Length/integrity mismatch | MockWebServer/coroutines | Explicit integrity failure; partial file never published | PASS |
| HLS segment transfer and resume | MockWebServer/parser | Selected track downloads and resumes from the last verified segment | PASS |
| DASH segment transfer and resume | MockWebServer/secure XML parser | Selected representation downloads and resumes from its checkpoint | PASS |
| Mux compatibility gate | JVM/Robolectric | AVC+AAC MP4/fMP4 accepted; WebM, HEVC and unknown codecs fail explicitly | PASS |
| Encrypted sample rejection | JVM | Encrypted extractor samples are refused before muxing | PASS |
| Bounded concurrency | Coroutines/test scheduler | Capacity respected and the queue drains as slots free | PASS |
| Pause/resume/retry/cancel | Coroutines/test scheduler | Deterministic status transitions and correct action sets | PASS |
| Cancellation cleanup | Coroutines/test scheduler | Engine workspace and destination discarded; record stays removable | PASS |
| Network-change behavior | Coroutines/test scheduler | Active work suspends on loss and resumes on validated connectivity | PASS |
| Process restart recovery | Room/in-memory store | Incomplete tasks become `NEEDS_REFRESH` with the verified checkpoint retained | PASS |
| Duplicate URLs | Coroutines/test scheduler | Independent queue records, no silent merge | PASS |
| Room v3→v4 migration | Room/Robolectric | Schema migrates with checkpoint and segment data preserved | PASS |
| Incomplete-file safety | Robolectric | Bytes stage in a partial file; final name appears only after commit | PASS |
| Low storage | JVM/injected failure | Storage failure reported as `STORAGE_UNAVAILABLE`; nothing queued | PASS |
| Download plan rejection | JVM | Unsupported codec, non-HTTPS, expired and unaddressable DASH rejected explicitly | PASS |
| File-name safety | JVM | Names built only from title/label metadata; no URL fragments and no traversal segment | PASS |
| Preview enqueue path | ViewModel/Robolectric | Direct sources probed first; repeat taps cannot double-queue; rejections surface their reason | PASS |
| Downloads Compose surface | Robolectric Compose | Honest determinate/indeterminate progress and per-status actions | PASS |
| Notification content | Robolectric | Progress, paused and failed notifications carry no sensitive URL | PASS |
| Phase 4 core-download tests | JUnit/MockWebServer/coroutines | No failures | PASS — 79 tests |
| Phase 4 app tests | JUnit/Robolectric Compose | No failures | PASS — 58 tests |
| Full Phase 4 matrix | Local JDK 17/SDK 35 | Lint, Android/JVM tests, debug and minified release build pass | PASS — 500 tasks in 16m 57s; 216 tests, 0 failures |

## Runtime tests still requiring a device/emulator

| Test | Required environment | Success criterion | Current result |
| --- | --- | --- | --- |
| On-device app launch/navigation | Android API 24+ device/emulator | App launches to Home and routes render | NOT RUN — no device/KVM; Robolectric navigation smoke test passes |
| Direct HTTPS MP4 preview | Android API 24+ device/emulator | Player reaches ready and renders | NOT RUN |
| Non-DRM HLS preview | Android API 24+ device/emulator | Selected stream reaches ready | NOT RUN |
| Non-DRM DASH preview | Android API 24+ device/emulator | Selected stream reaches ready | NOT RUN |
| Secure browser load/history | Android API 24+ device/emulator | HTTPS page loads; history/progress/errors behave | NOT RUN — no device/KVM; policy and UI tests pass |
| DOM candidate extraction | Android WebView test page | URLs returned without page mutation | NOT RUN on Android — exact script passes committed fixture in headless Chromium |
| Cookie/header preview | Controlled authenticated fixture | Preview succeeds with session context | NOT RUN |
| DRM fixture | Known encrypted manifest | Structured unsupported result | NOT RUN |
| Audio/video mux on device | Android API 24+ device/emulator | `MediaExtractor`/`MediaMuxer` produce a playable MP4 | NOT RUN — compatibility gate and recovery logic covered locally |
| Foreground download lifecycle | Android API 24+ device/emulator | Service survives backgrounding, shows progress and self-stops | NOT RUN |
| MediaStore publication | Android 10+ device/emulator | Pending item becomes visible in Downloads only after verification | NOT RUN — app-private staging covered locally |
| Real low-storage transfer | Device with a nearly full volume | Transfer fails cleanly without a corrupt published file | NOT RUN — covered only through an injected failure |

## Phase 5 and later regression categories

- Site-adapter fixture drift
- Adapter-to-generic fallback
- Secret-redaction tests
- Re-run of the Phase 4 transfer and recovery suite on every change
