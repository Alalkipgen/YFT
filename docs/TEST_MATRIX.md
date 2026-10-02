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

## Phase 5 automated checks

All adapter tests run offline against committed fixtures; no test performs a live network call.

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Standard and short URL matching | JVM | TikTok, Facebook and Vimeo long, mobile, embed and short links are claimed; short links are marked unresolved until the redirect is followed | PASS |
| Canonical page identity | JVM | Variant links for one video collapse onto a single canonical address, and tracking parameters are dropped | PASS |
| Unrelated and malformed URLs | JVM | Non-video, insecure and malformed links are not claimed | PASS |
| Multiple qualities | JVM/fixtures | Every rendition the page exposes is returned with its label, resolution and stated size | PASS |
| Separate tracks | JVM/fixtures | Facebook's DASH manifest and Vimeo's HLS/DASH manifests are handed to the existing resolver; adapters never split tracks themselves | PASS |
| Expired link | JVM/fixtures | A page or configuration whose links already expired fails as expired instead of being queued | PASS |
| No media | JVM/fixtures | Photo posts, media-free posts and file-less configurations fail with their own reason | PASS |
| Deleted, private and login-required | JVM/fixtures | Private, removed, password-protected pages, login walls and checkpoint redirects each fail with their own reason | PASS |
| Region block and DRM | JVM/fixtures | Region blocks and DRM-protected files fail explicitly; no circumvention is attempted | PASS |
| Response changes | JVM/fixtures | Changed markup fails as changed and still allows the generic detector to try; access failures never fall back | PASS |
| Transport failures | JVM | HTTP status codes and I/O failures surface as structured failures | PASS |
| Wrong-video safety | JVM/fixtures | A suggested video on the same Facebook page is never returned instead of the requested one | PASS |
| Request context | JVM | The browser session is replayed only to the site's own hosts; Vimeo follows only `player.vimeo.com` configuration addresses and no sign-in is performed | PASS |
| Generic fallback | JVM | Unclaimed pages return `SiteAdapterSelection.None` so the generic detector runs | PASS |
| Adapter kill switch | JVM | A disabled adapter reports itself instead of silently matching, and other adapters keep working | PASS |
| YouTube decision guard | JVM | The shipped adapter set is exactly `tiktok`, `facebook`, `vimeo`, and `youtube.com`/`youtu.be` links stay unclaimed | PASS at Phase 5; superseded in Phase 5E by the owner override (ADR-005) |
| App-side coordination | Robolectric | The browser surfaces adapter results and falls back to generic detection without site parsing in the UI | PASS |
| Phase 5 extractor-sites tests | JUnit | No failures | PASS — 52 tests |
| Full Phase 5 matrix | Local JDK 17/SDK 35 | Lint, Android/JVM tests, debug and minified release build pass | PASS — 505 tasks in 7m; 293 tests, 0 failures; lint 0 errors |

## Phase 5E automated checks (YouTube, owner override)

| Check | Type | Success criterion | Result |
| --- | --- | --- | --- |
| URL matching | JVM | `watch?v=`, `/shorts/`, `/embed/`, `/live/`, `/v/`, `youtu.be/`, `m.`, `music.` and `youtube-nocookie.com` single-video links are claimed with an 11-character ID; channel, playlist, search, insecure and credential-bearing links are not | PASS |
| Shipped adapter guard | JVM | The shipped set is exactly `tiktok`, `facebook`, `vimeo`, `youtube`; channel/playlist/search URLs stay unclaimed | PASS |
| Client profiles | JVM | Request bodies carry no content-gate acknowledgement, name YFT's own page as the embedder, escape untrusted values, and fall back to a fixed client version only when the page states none | PASS |
| Page and player parsing | JVM/fixtures | Page signals (API key, client, version, visitor data, signature timestamp, player version) and the inline player response are read from committed fixtures; foreign player-script origins are rejected | PASS |
| Embedded-first lookup | JVM/fixtures | The embedded client is asked first without the user's cookie; the page client is asked with the session only when the page has no inline response; an embed refusal falls back to the page's own streams | PASS |
| Verdict ordering | JVM/fixtures | Private, age-gated (sign-in), region, DRM and live verdicts from the watch page are final; a bot check with nothing embeddable asks the user to sign in; SABR-only responses report the player-script requirement | PASS |
| Stream selection | JVM/fixtures | Progressive MP4 streams with audio are returned highest first plus one AAC audio stream (default track, non-DRC, highest bitrate) | PASS |
| Cipher and `n` handling | JVM/fixtures | Signature ciphers are decoded and both transforms applied through the script runner; implausible answers drop the stream; expired links are refused before any script runs | PASS |
| Player-script runner | JVM | Only `www.youtube.com/s/player/…/base.js` is fetched, the phone build first; the preprocessed player is cached per version and rebuilt after a failed run; timeouts and empty answers fail with `PLAYER_SCRIPT_REQUIRED` | PASS |
| Solver protocol | JVM | Requests are ASCII-escaped and ordered; replies keep only answers that were asked for and are size-limited | PASS |
| Solver page routes | JVM | Only the reserved asset host's four files and the input route are served; every other request is refused | PASS |
| Credential scope | JVM | `Cookie` and `Authorization` survive same-site redirects, are dropped on a redirect that leaves the original site and stay dropped for the rest of the chain; IP hosts match exactly | PASS |
| Extractor HTTP client | JVM | HTTPS-only with no inline credentials, downgrade redirects refused, 303 turns a JSON POST into a GET while 307 keeps it, redirect loops and oversized bodies fail instead of truncating, status codes map onto structured failures | PASS |
| Bundled solver vectors | Node (`scripts/verify-youtube-solver.mjs`) | The unmodified bundled solver answers the public ejs vectors on the main and phone player builds, and solves today's live player | PASS — 34 vectors; live player `8ab5c328` solved |
| Solver in a browser engine | Headless Chromium 153 harness | The exact solver page, worker and CSP solve player `74edf1a3` (5/5) and six more players with no outside request and no navigation | PASS — 0.68 s cold, 0.21 s with the preprocessed player |

## Phase 6 automated checks (hardening and UI)

| Check | Type | Success criterion | Result |
| --- | --- | --- | --- |
| Backup and transfer exclusion | Lint/resources | `data_extraction_rules.xml` excludes every domain, `fullBackupContent=false`, lint reports no `DataExtractionRules` issue | PASS |
| Network security | Manifest review | No cleartext and system CAs only for every build type | Reviewed — no automated test |
| Notification privacy | Robolectric | Download notifications are `VISIBILITY_PRIVATE` with a count-only public version | PASS |
| File-name hardening | JVM | Bidirectional and zero-width controls never reach an output name | PASS |
| Download preferences | JVM/DataStore | Quality, location, Wi-Fi only, mobile-data confirmation and concurrency persist and fall back to defaults | PASS |
| Wi-Fi-only policy | JVM | Mobile data holds work as waiting, Wi-Fi releases it, the first enqueue applies the policy synchronously | PASS |
| Unique app-storage names | JVM/Robolectric | A taken name gets a " (n)" suffix that follows the task into the queue and result | PASS |
| Settings screen | Robolectric/Compose | Every preference is shown and forwarded; clearing data asks first | PASS |
| Preview defaults | Robolectric/Compose | The preferred quality is preselected; mobile data asks for confirmation; Wi-Fi only explains the wait | PASS |
| Library | Robolectric/Compose | MediaStore `Download/YFT` items and app-storage files are listed (no `.part`), play in app, open/share through a chooser with a read grant, delete only after confirmation | PASS |
| App-storage provider | Robolectric | Read-only, not exported, confined to app-storage downloads, refuses staging files and writes | PASS |
| Home link entry | Robolectric/Compose | The clipboard is read only on Paste; the first HTTPS link is extracted; the link reaches the browser route intact | PASS |
| Accessibility labels | Robolectric/Compose | Back, history, reload/stop and media buttons expose content descriptions; existing test tags still resolve | PASS |
| About and notices | Robolectric/Compose + JVM | Version, scope and privacy shown; every bundled notice matches `THIRD_PARTY_NOTICES.md` and opens its license text | PASS |
| Palette contrast | JVM | Light and dark text/background pairs ≥ 4.5:1, outline ≥ 3:1, not the Material default | PASS |
| Detected Media | JVM/Robolectric/Compose | The browser publishes the current page; only listed, non-DRM candidates reach Preview; only the page host is shown; clearing browsing data empties it | PASS |
| Free-space pre-check | JVM | A known size that cannot fit (with 32 MiB headroom) is rejected as `INSUFFICIENT_STORAGE` before a destination exists; estimates and unmeasurable volumes start | PASS |
| Network banner | JVM/Robolectric/Compose | "Waiting for Wi-Fi" and "No connection" follow the policy and disappear when transfers may run | PASS |
| Storage janitor | JVM | Stale `.part` files and orphan workspaces from earlier processes are removed, current and unfinished work is kept, finished records are capped at 200, one pass per process | PASS |
| Workspace naming | JVM | Workspace names are stable SHA-256 prefixes that never contain the task id | PASS |

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
| YouTube download on a device | Phone on a residential or mobile network, current Android System WebView | An embeddable public video lists progressive and M4A candidates, the solver WebView answers within the timeout, and the file downloads and plays | NOT RUN — no device/KVM; the sandbox's datacenter IP gets bot checks and "video unavailable" |
| YouTube non-embeddable video | Same as above | The page client's streams are offered; a 403 at download time fails clearly instead of hanging | NOT RUN |
| Notification permission | Android 13+ device/emulator | `POST_NOTIFICATIONS` is requested before the first download and progress shows once granted | NOT RUN — request flow in `ui/components/NotificationPermission.kt` has no automated test |
| Library on a device | Android 10+ device/emulator | `Download/YFT` items and app-storage files play in app, open and share in other apps, and delete after confirmation | NOT RUN — Robolectric repository, provider and screen tests pass |
| Clear browsing data on a device | Android API 24+ device/emulator | Sites opened in YFT are signed out and storage/cache are empty afterwards | NOT RUN — cleaner composition covered locally |
| Wi-Fi-only switching on a device | Device with Wi-Fi and mobile data | Transfers pause on mobile data, the banner explains it, and they resume on Wi-Fi | NOT RUN — policy and banner covered locally |
| Storage janitor on a device | Android API 24+ device/emulator | After a forced stop, stale `.part` files and workspaces disappear on the next launch | NOT RUN — covered with temporary folders locally |

## Phase 5 and later regression categories

- Site-adapter fixture drift
- Adapter-to-generic fallback
- Secret-redaction tests
- Re-run of the Phase 4 transfer and recovery suite on every change
