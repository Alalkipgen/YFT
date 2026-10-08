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

## Phase 7 automated checks (release preparation)

Throwaway keys were generated in `/tmp` for these checks (`CN=YFT THROWAWAY TEST KEY A/B - NOT
FOR DISTRIBUTION`, 30-day validity), never committed and deleted afterwards. No APK built with
them is a release artifact.

| Check | Type | Success criterion | Result |
| --- | --- | --- | --- |
| App identity | Robolectric | Label "Video Downloader"; `@mipmap/ic_launcher` and `ic_launcher_round` are adaptive icons on API 28 and 35; legacy PNGs exist at 48/72/96/144/192 px; `MainActivity` starts on `Theme.Yft.Launch` while the app theme is `Theme.Yft`; semver versionName | PASS (`AppIdentityTest`, 5 tests) |
| Launcher/launch resources | Lint | Adaptive, monochrome, legacy and `values-v31` splash resources add no lint error or warning | PASS |
| Version source | Gradle | `yft.versionName`/`yft.versionCode` from `gradle.properties` are validated; `-Pyft.versionCode=2` overrides for tests | PASS |
| Unsigned default | Gradle + script | Without configuration `assembleRelease` writes `app-release-unsigned.apk` (no debug-key fallback); `verify-release-apk.sh --allow-unsigned` reports UNSIGNED and the strict mode fails | PASS |
| Signing required but missing | Gradle | `-Pyft.requireReleaseSigning=true` fails during configuration with a clear message; `release-prep.sh` stops after 9 s, before clean and tests | PASS |
| Partial signing / missing keystore | Gradle | "only partly configured" and "Release keystore not found" failures | PASS |
| `keystore.properties` | Gradle `signingReport` | The file is ignored by Git; the release config uses its keystore and alias (key A fingerprint); no password in the output; file removed afterwards | PASS |
| Signed release via environment | `scripts/release-prep.sh` | Uncached clean, lint, tests, signed release; `apksigner` verifies v2 + v3 with one signer and the expected certificate; staging writes the APK, `SHA256SUMS`, `release-info.txt` and `release-notes.md`; no password in the log | PASS (key A) |
| Checksum file | `sha256sum -c` | `SHA256SUMS` verifies the staged APK | PASS |
| R8 bridge in the signed APK | dexdump + mapping | `WebViewSolverEngine$SolverBridge` → `r3.l` keeps `post` | PASS |
| Upgrade compatibility | `verify-release-apk.sh --previous-apk` | versionCode 2 over versionCode 1 with the same package and signer passes | PASS — static check only (signed versionCode 2 build with `-Pyft.versionCode=2`, 210 s); on-device upgrade NOT RUN |
| Verification negatives | Shell | A lower or equal versionCode, a previous APK re-signed with key B, an unexpected certificate, an APK re-signed with the Android debug key, the debug package, a wrong versionName, and an unsigned APK with `--allow-unsigned` plus certificate or upgrade checks all fail | PASS — all 9 (a first run fed a signed APK to the `--allow-unsigned` case, which correctly verified it; the case was rerun with an unsigned copy) |
| Workflow and script lint | actionlint 1.7.7 + shellcheck 0.10.0 | `release-draft.yml`, `checkpoint-validation.yml` and every script are clean | PASS |
| CI unsigned release check | GitHub Actions `validate` | `assembleRelease` + `verify-release-apk.sh --allow-unsigned` after the full matrix | PASS on `b5797de` |
| Release workflow run | GitHub Actions `Release draft` | Signed draft pre-release with APK, `SHA256SUMS` and notes | PASS — the `v1.0.0-beta.1` run on `a6bd059` signed it with the owner's key; the owner published it on 2026-10-02 |

## UI redesign automated checks

Robolectric (SDK 35) with Compose UI tests; class names and test counts in brackets.

| Check | Type | Success criterion | Result |
| --- | --- | --- | --- |
| Design system | Robolectric/Compose + JVM | Segmented control, filter chips, count badge, Promptbox states, thumbnails and status chips behave and keep full-size targets; Plus Jakarta Sans is bundled and used by every role (`YftComponentsTest`, 7); light and Night text pairs meet WCAG AA and the brand tokens match the brief (`YftThemeContrastTest`, 3); sizes, lengths, formats and titles (`YftFormatTest`, 4) | PASS |
| Home and Promptbox | Robolectric/Compose + JVM | Promptbox states; the clipboard is read only on Paste or Use; links are checked without the browser; Your sites; Recent (`HomeScreenTest` 12, `HomeViewModelTest` 16, `HeadlessLinkInspectorTest` 11, `CopiedLinkHintTest` 2) | PASS |
| Browser and "Found on this page" | Robolectric/Compose | The sheet peeks with the savable count, expands, collapses on the scrim or a drag and previews the tapped media; protected-only pages explain instead; the address shows host and path but never the query (`BrowserScreenTest` 8, `DetectedMediaScreenTest` 4) | PASS |
| Download as | Robolectric/Compose + JVM | Sheet states, quality names and sizes, Wi-Fi only and the save-location caption (`PreviewScreenTest` 10, `PreviewViewModelTest` 11, `PreviewLabelsTest` 6) | PASS |
| Downloads | Robolectric/Compose + JVM | Filters and counts, status chips with reasons, card menu actions, speed and time left, storage pill (`DownloadsScreenTest` 18, `DownloadsUiStateTest` 14, `DownloadsViewModelTest` 8, `DownloadLabelsTest` 7, `TransferRateTrackerTest` 5, `DownloadStorageSourceTest` 3) | PASS |
| Library and playback | Robolectric/Compose + JVM | Grid, search, sort, ⋯ menu, mini player, full-screen player and file details (`LibraryScreenTest` 9, `LibraryViewModelTest` 8, `LibraryArrangementTest` 4, `MediaDetailsTest` 3, `PlayerTest` 7) | PASS |
| Settings, About and Licenses | Robolectric/Compose | Every setting shown and forwarded, About content, every notice listed under its group with its text on demand (`SettingsScreenTest` 10, `AboutScreenTest` 2, `LicensesScreenTest` 3, `OpenSourceNoticesTest` 2) | PASS |
| Navigation | Robolectric | Bottom bar, Downloads badge and full-screen routes (`YftNavigationSmokeTest` 10, `YftDestinationTest` 3, `AppUiStateTest` 3) | PASS |
| Accessibility audit | Robolectric/Compose | On 11 screens in light and Night every control TalkBack reaches has a name and a touch target of at least 48dp; Night at 200% text keeps every label (`AccessibilityAuditTest`, 22) | PASS |
| Renders | Robolectric native graphics | PNGs of every screen in light and Night (`DesignRenderTest`, 19) and at 130% and 200% text (`LargeTextRenderTest`, 22) with `YFT_RENDER_DIR` set, compared by eye with `docs/design/reference/01`–`09` | Reviewed locally on 2026-10-03; skipped in CI |
| Signed release `1.0.0-beta.2` | GitHub Actions `Release draft` | Tag `v1.0.0-beta.2` on `39ea049`: `release-prep.sh` runs the full matrix, signs with the owner's key from the secrets, checks the certificate pin and stages the APK, `SHA256SUMS` and notes; a draft pre-release is created and nothing is published | PASS — run 37129636676 (2026-10-03, 9 min): `video-downloader-1.0.0-beta.2.apk` 3,376,449 bytes, SHA-256 `3f5b4c74b02e61bf2572a7248aef3a35b92ece3c6f7cf8e0770bc661cee53a95`, certificate `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F` (the same as beta.1); artifact `yft-release-1.0.0-beta.2`; draft not published |
| Full matrix after merging `main` | Gradle | `testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:assembleRelease`, then `verify-release-apk.sh --allow-unsigned`: every test passes (app 384 including `AppIdentityTest` 5, 41 render tests skipped without `YFT_RENDER_DIR`; core-browser 46, core-data 12, core-download 81, core-media 14, core-model 30, extractor-api 22, extractor-generic 7, extractor-sites 91), lint has no error, and the minified `1.0.0-beta.2` (versionCode 2) APK passes the metadata checks | PASS — 687 tests; lint 0 errors (app 83 warnings: `GradleDependency` 57, `VectorPath` 20, `AndroidGradlePluginVersion` 6; core-data 1); unsigned release APK 3,360,065 bytes |

## Phase 8 automated checks

### T01 — Browser main-thread access (2026-10-03)

| Check | Type | Success criterion | Result |
| --- | --- | --- | --- |
| Off-main request interception | Robolectric + background executor | A WebView subclass rejects `getUrl` and `getSettings` off the main looper; interception still records the cached page URL and User-Agent | PASS — regression failed on the old client, then passed on the fix |
| Navigation snapshot | Robolectric | Explicit load before callbacks, page start, redirect, history update and cleared history use the correct atomic URL | PASS |
| Request headers | Robolectric | A case-insensitive observed User-Agent overrides the cached default; request headers are preserved | PASS |
| Parallel request callbacks | Robolectric + four workers | 32 requests return normally without WebView access and keep the same page snapshot | PASS |
| Observation ownership | ViewModel/coroutines + four workers | 32 download observations are retained; a queued request from the previous page is ignored after navigation | PASS |
| Background entry-point audit | Source inspection | `git grep -n -E '@JavascriptInterface|shouldInterceptRequest' -- '*.kt'` identifies every callback; browser, solver bridge/client and prototype use no off-main WebView/WebSettings method | PASS |
| Task validation | JDK 17 / SDK 35 | Core-browser/app tests and app lint remain green | PASS — core-browser 53, app 386 (41 render tests skipped), 0 failures/errors; app lint 0 errors, 83 existing warnings |
| Kotlin line width / whitespace | Git diff + untracked-source check | Added Kotlin lines at most 100 characters; `git diff --check` clean | PASS |
| Real browser on a phone | Owner device | Your sites, Home's Open in browser and typed Go load without closing the app; found media still appear | OWNER CHECK — emulator coverage comes in T02 |

Exact validation:

```bash
source /data/yft-env.sh
./gradlew --no-daemon -q --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
```

The unchanged baseline passed with core-browser 46 and app 384 tests, lint 0 errors. The first
default-memory attempt lost its Gradle daemon (the 4 GiB sandbox recorded an OOM kill); no
complete result was claimed. The low-memory retry passed, and the orphaned worker was stopped.
The old-code regression command used the same memory flags and
`--tests 'com.alal.yft.core.browser.webview.SecureBrowserWebViewClientTest.interceptsRequestsOffMainWithoutTouchingWebViewOrSettings'`;
it failed with the expected main-looper guard, not a compilation failure.

### T02 — Real-WebView CI smoke (DONE, 2026-10-03)

| Check | Environment | Result |
| --- | --- | --- |
| Starting-state validation | JDK 17 / SDK 35 | PASS — `:app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` before changes |
| Instrumentation compilation | JDK 17 / SDK 35 | PASS — three production-activity smoke scenarios; `app-debug-androidTest.apk` 1,098,734 bytes |
| App regression tests | Robolectric/Compose | PASS — 386 tests, 0 failures/errors, 41 render tests skipped |
| Android lint | SDK 35 | PASS — 0 errors, 95 warnings (`GradleDependency` 69, `VectorPath` 20, `AndroidGradlePluginVersion` 6); adding instrumentation dependencies adds 12 version warnings |
| Diagnostic redaction / artifacts | Python unittest | PASS — 6 tests: credential and URL redaction, coordinate-only annotations, fatal exceptions, slow-site warnings, valid redacted JUnit XML, missing-screenshot gate |
| Collector / APK cleanup | Python unittest with fake Gradle/adb | PASS — 4 tests: retain external captures until pull, collect after test failure, surface pull failure, surface logcat failure. External-file regression FAILED on the old collector and PASSED on the fix |
| Workflow / collector lint | actionlint 1.7.7 / shellcheck 0.10.0 / `bash -n` | PASS — initial SC2164 collector warning fixed with guarded `cd` |
| Kotlin width / whitespace | Git diff and untracked-source check | PASS — new Kotlin lines at most 100 characters |
| Public HTML5 fixture check | HTTPS GET, 2026-10-03 | HTTP 200; host `commons.wikimedia.org`; path `/wiki/File:Big_Buck_Bunny_4K.webm`; markers `video`, `source`, `webm` found. No body/cookies/complete media addresses retained |
| Actual Chromium / first run | GitHub API 34 emulator, `8151813` | 3/3 instrumentation tests PASS, 0 fatal exceptions; job RED because APK cleanup removed all 3 screenshots before pull. [Run](https://github.com/Alalkipgen/YFT/actions/runs/37141758594). Repaired collector subsequently GREEN (row below) |
| Actual Chromium / repaired run | GitHub API 34 emulator, `e9e1a09` | PASS — 3/3 instrumentation tests, 0 failures/errors/skips, 0 fatal exceptions, all 3 required screenshots collected. [Emulator run](https://github.com/Alalkipgen/YFT/actions/runs/37143005734); [checkpoint GREEN / debug APK](https://github.com/Alalkipgen/YFT/actions/runs/37143005667) |
| Native visual review | `yft-emulator-smoke`, 1,039,015 bytes | Not reviewed by agent: download requires authentication (HTTP 401). Empty address bounds `[92,33][254,81]`, WebView `[0,90][320,583]`; loaded/found address bounds missing, not proof of hidden pixels. Owner can review artifact |

Local Gradle validation uses the T01 memory flags with
`:app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`. Other checks:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s scripts/tests -p 'test_ci_*smoke*.py' -v
bash -n scripts/ci-emulator-smoke.sh
shellcheck scripts/ci-emulator-smoke.sh
actionlint .github/workflows/emulator-smoke.yml
```

The independent `Android emulator smoke` workflow boots API 34 (`google_apis`, x86_64), then runs
`./gradlew --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest`.
The collector executes while the emulator
is still running and uninstalls the APKs after pulling their external captures.
The `yft-emulator-smoke` artifact must contain three required PNGs, coordinate-only
bounds, sanitized logcat and text/XML reports. Raw logcat, UI hierarchy and binary test-result
payloads are not uploaded. A missing screenshot or any `FATAL EXCEPTION` fails the job. The AVD
cache is saved before test browsing; adb keys and app-session data are not cached.
Slow media discovery on the public Wikimedia page emits a warning; the HTTPS navigation check
remains mandatory. T02 is DONE on `e9e1a09`; F2 records the result without claiming phone/pixel proof.

### T03 — Deferred browser/start page (OWNER CHECK, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — core-browser 53, app 386 (41 render skips), 0 failures/errors; lint 0 errors / 95 warnings |
| Empty-page regression | FAILED on the old code: `browser-surface` existed when no page was open. PASSED in the first repaired browser run |
| First browser run / APK compile | PASS — 26 tests, 0 failures/errors; native-view creation/identity, valid/invalid/blank navigation, initial-link readiness, metadata-only clipboard hint/tap, saved-site navigation and empty saved-list propagation |
| First full validation | PASS — core-browser 53, app 403 (45 render skips), 0 failures/errors; lint 0 errors / 95 warnings |
| Largest-text badge regression | FAILED on the old header: badge bounds were only 5 px wide. PASSED after reserving heading/count space |
| Final full validation / native APK compile | PASS — core-browser 53, app 405 (45 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled |
| Rendering / visual inspection | PASS — all 8 final PNGs inspected individually: start and loaded Day/Night/130%/200%. Loaded 200% first exposed the count bug; all 4 replacement loaded images passed after repair |
| Native Chromium | PASS — `9be6b43`, API 34 CI: 3/3 tests, 0 fatal exceptions, 3 PNGs collected; empty WebView absent; real Example Domain content and native top-control bounds asserted |
| Phone / native pixels | OWNER CHECK — no local KVM; native PNG download requires authentication, so no native pixel-review claim |

CI: https://github.com/Alalkipgen/YFT/actions/runs/37146164024 (emulator) and
https://github.com/Alalkipgen/YFT/actions/runs/37146164049 (checkpoint/debug APK).
Address bounds in all three cases are `[92,33][254,81]`; loaded/found WebView bounds
are `[0,90][320,583]`; the empty WebView is absent.

Commands use the T01 memory flags:
`:core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`,
plus `:app:assembleDebugAndroidTest`. Rendering:

```bash
YFT_RENDER_DIR=/data/yft-t03-renders ./gradlew --no-daemon -q \
  --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest --rerun \
  --tests 'com.alal.yft.design.DesignRenderTest.browser*' \
  --tests 'com.alal.yft.design.LargeTextRenderTest.*02-browser*'
```

`DESIGN_SCREENS` now includes the start page, so its Day target-size/labels and Night 200%
labels are audited. Native smoke keeps the three screenshot names and asserts top controls
in UIAutomator, no empty WebView, Example Domain page content, and page bounds below the bar.

### T04 — Local crash report / lookup diagnostics (OWNER CHECK, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — core-model 30, extractor-api 22, extractor-sites 91, app 405 (45 render skips); lint 0 errors / 95 warnings |
| Copy details regression | FAILED before implementation (control absent), PASSED in the full repaired run |
| Handler/store | PASS — metadata/chain/frames, overwrite/delete, 64 KiB UTF-8 cap, redaction, cycle limit, IO-failure delegation and idempotent install |
| About / Home diagnostics | PASS — report visibility, explicit View/Copy/Share/Delete, safe text chooser (no file URI), confirmed debug-only trigger, bounded step snapshots and clearing on edit/new lookup |
| Initial failures | Wrong theme field caused a compile failure, repaired. Focused run: 75/76 passed; chooser text ClipData null assertion corrected to sanitized text/no URI; full run passes |
| First full validation / instrumentation APK | PASS — core-model 35, extractor-api 24, extractor-sites 91, app 432 (53 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled |
| Script tests / style | PASS — 13 Python tests; release verifier bash-n/shellcheck; all new Kotlin lines <=100 at implementation checkpoint |
| Release APK safety | PASS on the final T04 working tree — separate unsigned beta.2 build; package/version/not-debuggable/alignment and debug-action DEX guard pass; SHA-256 16e14897a6e1444a0da8946a250535c48edefd8e481dd226f714e69913076f10; not published or installable |
| First visual QA | 8 captures inspected: Home 200% truncates Open browser (responsive flow repair added); dialog's nominal 200% capture was actually 100% (native resource font-scale guard added); largest About actions need a scrolled capture |
| Replacement renders / final validation | PASS — all 9 final PNGs individually inspected; full command: core-model 35, extractor-api 24, extractor-sites 91, app 434 (54 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled |
| Largest text | PASS — full Open browser label wraps below Paste; all four About report actions have a scrolled 200% capture; dialog uses real Android resource fontScale=2, not just the parent composition density. The layout test initially failed with legacy Robolectric text metrics; Native graphics fixes the test environment and it passes |
| Native CI | PASS on final ce881f7 — both workflows green; native API 34: 3 tests, 0 failures/errors, 0 fatal exceptions, 3 PNGs; empty WebView absent and loaded/found address bounds above the WebView |

Full command: T01 memory flags plus
`:core-model:test :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v`.
Release check: `:app:assembleRelease` in a separate call, then
`bash scripts/verify-release-apk.sh --allow-unsigned --expected-version 1.0.0-beta.2 app/build/outputs/apk/release/app-release-unsigned.apk`.
Final CI: https://github.com/Alalkipgen/YFT/actions/runs/37151155280 (emulator) and
https://github.com/Alalkipgen/YFT/actions/runs/37151155259 (checkpoint/debug APK).
Reports stay local; About Share opens a text chooser only after a tap, with no file-provider grant.

### T05 — Headless identity / navigation headers (DONE, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — core-model 35, core-browser 53, extractor-generic 7, extractor-sites 91, app 434 (54 render skips), no failures/errors; lint 0 errors / 95 warnings |
| User-Agent regression | FAILED old: Home adapter context had no user-agent; PASSED fixed in the full run |
| Identity propagation | PASS — adapter context, production generic fetcher and direct/markup candidates use the same honest non-Android desktop YFT identity with no browser cookie |
| Navigation-only defaults | PASS — all four adapters supply page GET headers; explicit casing/values preserved, mutable input snapshotted, redirects retain defaults; generic fetcher cannot inject account headers |
| JSON/API and browser identity | PASS — Vimeo config and YouTube player requests keep their JSON Accept and no navigation mode; existing browser user-agent/cookie replay fixture tests pass |
| Full validation / instrumentation APK | PASS — core-model 38, core-browser 54, extractor-api 24, extractor-generic 7, extractor-sites 95, app 436 (54 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled |
| Script checks / style | PASS — 22 Python tests; live checker bash-n/shellcheck; diff-check and new Kotlin lines <=100 |
| Public Facebook live check | PASS — HTTP 200, host www.facebook.com, path /reel/1603698891196107/, 609875 bytes; browser_native_hd_url=yes, TikTok/YouTube markers=no; no cookies or raw page/query output |
| CI / owner check | PASS on cbc92e5 — both workflows green; emulator https://github.com/Alalkipgen/YFT/actions/runs/37152854933 and checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37152854989. No separate phone check yet (T06–T08 consume this); native pixel review requires authentication |

Full command: T01 memory flags plus
`:core-model:test :core-browser:testDebugUnitTest :extractor-api:test :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
Script command: `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -q`.
Live command: `bash scripts/live-check.sh https://www.facebook.com/share/v/1Q3kAyptrS/`.
Public page delivery is not yet successful Facebook extraction: its DRM parser is T06.

### T06 — Facebook public reels without sign-in (OWNER CHECK, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — extractor-api 24, extractor-sites 95, app 436 (54 render skips), no failures/errors; lint 0 errors / 95 warnings |
| Public-reel regression | FAILED old: a certificate-only `drm_info` with an empty licence map returned DRM_PROTECTED (1 test, 1 failure); PASSED fixed with HD, SD and DASH candidates |
| Real DRM | PASS — explicit flags (direct or legacy node), a non-empty licence map and a graph licence URI still fail as DRM_PROTECTED; unreadable `drm_info` adds only `drm_info: unreadable metadata` and never copies its value |
| Share links | PASS — `/share/v/` and `/share/r/` accept the redirected reel/watch ID without selecting a suggested video; the OkHttp redirect chain keeps navigation headers and adds no cookies |
| Metadata | PASS — decimal, hex and named entities decoded once in title, owner and meta fallbacks; trailing Facebook suffix trimmed; the `og:image` fallback decodes `&amp;` once; heights come only from DASH or rendition metadata, never from HD/SD |
| Login walls | PASS — a login/checkpoint redirect or a login form fails as LOGIN_REQUIRED; a login phrase cannot downgrade a playable public page |
| Full validation / instrumentation APK | PASS — extractor-api 26, extractor-sites 109, app 437 (54 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled; 22 Python tests; diff-check and new Kotlin lines <=100. After the `og:image` decoding fix: extractor-sites 110, 0 failures |
| Public live check | PASS — owner's share link → HTTP 200, `www.facebook.com/reel/1603698891196107/`, 610752 bytes; parser Success with 2 progressive (HD, SD) and 1 DASH; ranged SD GET → 206. No body, cookie or signed URL printed |
| CI / owner check | PASS on af55f34 — both workflows green; checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37155542368 and emulator https://github.com/Alalkipgen/YFT/actions/runs/37155542372. Owner check: paste the Facebook link on Home → found → download → it plays |

Full command: T01 memory flags plus
`:extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
Live parser run: the sanitized-output harness outside the repository on the page fetched by
`scripts/live-check.sh`; it prints only the result type, counts and the ranged-GET status.

### T07 — TikTok media cookies for Home lookups (OWNER CHECK, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — core-download 81 on this branch; extractor-api 26, extractor-sites 110, app 437 (54 render skips), lint 0 errors / 95 warnings from the T06 checkpoint run |
| Media-cookie regression | FAILED old: with only the new cookie field, a Home lookup whose page set TikTok cookies still gave the media request no cookie (`TikTokExtractorTest`, 12 tests, 1 failure); PASSED fixed |
| Lookup cookies | PASS — `OkHttpExtractorClient` reports the `Set-Cookie` pairs of every hop in memory only, after OkHttp's domain and public-suffix checks; deletions and expired values remove earlier copies; oversized (>4096 chars) and surplus (>50) cookies are ignored; no cookie jar, so no hop sends a cookie it was not given |
| TikTok scoping | PASS — only `tiktok.com` and subdomain cookies that a browser would send to that media address (host-only, domain and path rules) join the media context; none when the page set none or the media is not on TikTok; the browser path keeps the WebView's cookie |
| Download rule / storage | Unchanged — the resolver and downloader send the cookie only to the media URL's own origin and drop it on cross-origin redirects; stored tasks keep no URL, cookie or header (runtime-only, NEEDS_REFRESH after process death) |
| Redaction | PASS — `ResponseCookie` and `BrowserRequestContext` `toString()` show `[REDACTED]`, never the value |
| Full validation / instrumentation APK | PASS — extractor-api 29, extractor-sites 113, core-download 81, app 439 (54 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled; 22 Python tests; new Kotlin lines <=100 |
| Public live check | PASS — Home lookup of `@scout2015/video/6718335390845095173` → Success, 5 candidates on a `tiktok.com` media host; media cookie names `ttwid`, `tt_csrf_token`, `tt_chain_token`; ranged GET through `SecureDownloadHttp` with the candidate context → 206; the same request without the cookie → 403. No body, cookie value or signed URL printed |
| CI / owner check | PASS on ba165c5 — both workflows green; checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37156848673 and emulator https://github.com/Alalkipgen/YFT/actions/runs/37156848739 (the owner also reported the GitHub runs green). Owner check still open: paste a TikTok link on Home → download → it plays; the same from the browser |

Full command: T01 memory flags plus
`:extractor-api:test :extractor-sites:test :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
Live run: a sanitized-output harness outside the repository using the app's `OkHttpExtractorClient`,
`TikTokExtractor` and `SecureDownloadHttp`; it prints only the result type, counts, cookie names
and HTTP status codes.

### T08 — YouTube bot check, SABR and lookup details (OWNER CHECK, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T07 checkpoint run on the same tree `ba165c5`: extractor-api 29, extractor-sites 113, app 439 (54 render skips), 0 failures; lint 0 errors / 95 warnings |
| Regression | FAILED old: with only the new `BOT_CHECK` reason added, 8 YouTube tests failed (bot checks returned LOGIN_REQUIRED, SABR-only returned PLAYER_SCRIPT_REQUIRED, details were empty); PASSED fixed |
| Bot check | PASS — `LOGIN_REQUIRED` whose reason or error screen says "confirm you're not a bot" (straight or curly apostrophe, any case, split text runs) → `BOT_CHECK`, not definite, no generic fallback. Message: "YouTube wants to check that this is not a bot. Open the video in YFT's browser, let it play for a moment, then tap Download." (T16 changed the ending to "then tap Try again."); Home keeps **Open in browser**. "Please sign in", age gates and private videos keep their own reasons and messages |
| SABR only | PASS — `streamingData` with only `serverAbrStreamingUrl` → `NO_MEDIA_FOUND` (not definite); the client's details line ends "SABR only" |
| Lookup details | PASS — watch page GET status and size, whether an inline response exists, then for each client asked: name, status, reason (markup tags removed, whitespace collapsed, bounded), formats with direct URLs (progressive/adaptive), protected-format count and SABR flag, plus each client's download outcome. Sanitized in the adapter and again by the coordinator |
| Redaction | PASS — details never contain the cookie, visitor data, API key, media host, SABR address or any query; a URL inside YouTube's reason becomes its origin; the summary's `toString()` prints no reason text or address |
| Home identity | PASS — with Home's context the watch page GET carries the desktop YFT user agent and navigation headers without a cookie; both player POSTs carry the same user agent with JSON `Accept`, no cookie and no navigation mode. Client order unchanged (embedded, then the page's own client) |
| Full validation / instrumentation APK | PASS — extractor-api 29, extractor-sites 122 (123 after the markup-tag fix), app 441 (54 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled; 22 Python tests; new Kotlin lines <=100 |
| Public live check | `scripts/live-check.sh` on `dQw4w9WgXcQ` → HTTP 200, `www.youtube.com/watch`, 1352799 bytes, `playabilityStatus` marker. Adapter harness with Home's identity through the real coordinator (no player-script host on the JVM): `dQw4w9WgXcQ` → PLAYER_SCRIPT_REQUIRED; the watch page's `WEB` response was OK with 0 direct-URL formats, 1 protected and SABR; `WEB_EMBEDDED_PLAYER` answered ERROR "This video is unavailable Error code: 152 - 18". `aqz-KE-bpKQ` → BOT_CHECK with the new message ("Sign in to confirm you’re not a bot"). Only outcome, message and sanitized details printed |
| CI / owner check | PASS on aa915ff — both workflows green; checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37159270955 and emulator https://github.com/Alalkipgen/YFT/actions/runs/37159270920. T08 DONE (2026-10-03) |

Full command: T01 memory flags plus
`:extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
Live run: `bash scripts/live-check.sh https://www.youtube.com/watch?v=dQw4w9WgXcQ`, then a harness outside
the repository using the app's `OkHttpExtractorClient`, `YouTubeExtractor` and `SiteAdapterCoordinator`.

### T16 — YouTube client strategy A + B + C (OWNER CHECK, 2026-10-03)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T08 checkpoint run on `aa915ff`: extractor-api 29, extractor-sites 123, app 441 (54 render skips), 0 failures; lint 0 errors / 95 warnings |
| Regression | The new tests cover behaviour that did not exist before (client order, device-client requests, token binding, browser retry); they were not re-run against the old code |
| Client chain (B) | PASS — `YouTubeExtractorTest`/`YouTubeClientProfileTest`: device clients (`VISIONOS`, `ANDROID`) are asked after the watch page with their app user agent and device fields and without cookie, referer or authorization; the chain stops once a video with sound and an audio track are found; the embedded player runs until there is any video; an age check from a fallback clears the fallbacks' offers; a definite page verdict asks no other client; `MWEB` only when the page was not `MWEB` |
| Session (page client) | PASS — `YouTubeSessionAuthTest` (5): `SAPISIDHASH` and the 1P/3P variants from the browser cookie's own session values, with the page's session index for a secondary channel; no authorization without a session cookie. The extractor sends it only with the page's client |
| PO token use (A) | PASS — player token in `serviceIntegrityDimensions.poToken` bound to the video ID; media token as `pot=` bound to the video when the page sets `html5_generate_content_po_token`, else to the data sync ID when signed in, else to the visitor data; one mint per binding and lookup; missing host/player version/binding and failures appear in details without values (`PoTokenProviderTest`, parser flag test) |
| Token host | PASS — `BotGuardProtocolTest` (8): attestation key from either player build, scrambled and plain challenges, refusal of challenges that would load code from elsewhere, integrity lifetime and refresh margin, JSON bodies and page documents, plain identifiers only, bounded replies; `PoTokenPageRoutesTest` (3): only the app's own page, script and challenge are served, YouTube's own addresses included are refused, the CSP admits no other origin; `BotGuardPoTokenProviderTest` (7): one page per refresh period with a token per video, a stale integrity token starts a new page with the key kept, idle close, structured failures that never leave a page open, a fresh start after a failed mint, nothing fetched without an engine/player/plain binding |
| Browser retry (C) | PASS — `SiteAdapterCoordinatorTest`, `BrowserViewModelTest` (3 new), `BrowserScreenTest`: a bot check offers **Try again** (`browser-site-retry`) only when asking again can help; one automatic retry after the site's player requests media; other sites' and cookieless requests never replace the page's kept cookie or user agent |
| Full validation / instrumentation APK | PASS — extractor-api 32, extractor-sites 149, app 465 (54 render skips), 0 failures/errors; lint 0 errors / 95 warnings; instrumentation APK compiled; 22 Python tests; new Kotlin lines <=100 |
| Live: token mint | PASS — `node scripts/verify-youtube-potoken.mjs` (Playwright, Chromium on the app's origin): player 8ab5c328, token minted in 549 ms, page requests refused: 0. GenerateIT answered ttl 43200 s, refresh 100 s |
| Live: solver | PASS — `node scripts/verify-youtube-solver.mjs`: 34 vectors passed, today's player 8ab5c328 solved |
| Live: clients (sandbox data-centre IP) | `dQw4w9WgXcQ`: `VISIONOS` OK (27 adaptive, direct), `ANDROID` OK (itag 18 direct), `MWEB` OK (cipher), `WEB_EMBEDDED_PLAYER` ERROR unavailable; `MWEB` media itags 18/140/134 downloaded with and without `pot=`. `aqz-KE-bpKQ`: LOGIN_REQUIRED bot check from every client, also with a minted token (IP-level). `scripts/live-check.sh` → HTTP 200 with the `playabilityStatus` marker for both. Only status, counts and sanitized details printed |
| CI / owner check | PASS on `30cefd8`: checkpoint validation https://github.com/Alalkipgen/YFT/actions/runs/37169226943 and emulator smoke https://github.com/Alalkipgen/YFT/actions/runs/37169226959. Owner check: paste two YouTube links on Home → found → download plays (or **Copy details**); play a bot-checked video in YFT's browser → it retries by itself, or tap **Try again** |

Full command: T01 memory flags plus
`:extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
Live runs: `PLAYWRIGHT_MODULE=<playwright> CHROMIUM=<chromium> node scripts/verify-youtube-potoken.mjs`,
`node scripts/verify-youtube-solver.mjs`, `bash scripts/live-check.sh <watch URL>` and a harness outside the
repository that asks each client and prints only verdicts and counts.

### T17 — Merged video and audio for higher qualities (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T16 checkpoint `30cefd8` with both CI workflows green (runs 37169226943 and 37169226959) |
| Regression | `CandidateNormalizerTest.mergeKeepsTheAudioCompanionAndCodecsOfAnOlderObservation` FAILED on the old `CandidateNormalizer.merge` (a newer browser observation of the same address dropped `audioCompanion` and `codecs`) and PASSES on the fix. The other new tests cover behaviour that did not exist before |
| Model | PASS — `MediaAssetTest`, `DownloadModelsTest`: `CompanionAudio` validation and redacted `toString()`; `MediaCandidate.codecs`/`audioCompanion`, `MediaVariant.audioCompanion`; `DashDownloadPlan.wholeFile` tracks; `DirectDownloadPlan.maxRequestBytes` |
| Transfers | PASS — `DirectTransferEngineTest`, `DashTransferEngineTest`: `googlevideo.com` files in ranged requests of at most 10 MiB; a whole-file track resumes from its own offset with an address-independent fingerprint; each track resumes separately; refusals and failures map to their reasons |
| Mux and queue | PASS — `AudioVideoMuxEngineTest`, `StreamDownloadQueueTest`: per-track totals; the compatibility gate refuses non-AVC/AAC pairs before any transfer |
| Variants and preview | PASS — `DefaultVariantResolverTest`: one merged "Video + audio" variant per paired row with the combined size; `PreviewSourceFactoryTest`: a merged variant previews through `MergingMediaSource` with both tracks |
| Plans and queueing | PASS — `DownloadPlanFactoryTest`, `DownloadEnqueuerTest`: a merged variant becomes an `AudioVideoMuxDownloadPlan` of two whole-file tracks after `AudioVideoMuxCompatibility` passes; refusals queue nothing |
| YouTube rows | PASS — `YouTubeExtractorTest`: 480p, 720p and 1080p `avc1` video-only rows are paired with the same answer's AAC audio; VP9 and AV1 are skipped; progressive and audio-only rows are kept |
| Full validation / instrumentation APK | PASS — core-model 40, core-download 89, core-media 17, extractor-api 32, extractor-generic 8, extractor-sites 150, app 470 (54 render skips), 0 failures; lint 0 errors / 95 warnings; instrumentation APK compiled; Python 22/22; new Kotlin lines <=100 |
| Not verified | No live or on-device mux: no merged file has been produced from real YouTube tracks on a phone or emulator yet |
| CI | PASS on `210814b` — checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37178339187 (yft-debug-apk), emulator https://github.com/Alalkipgen/YFT/actions/runs/37178339186 |
| Owner check | On YouTube, download a 720p row marked "Video + audio" → the saved MP4 plays with sound; Preview of that row plays with sound |

### T09 — Your sites: YouTube, Facebook, TikTok with logos (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T17 checkpoint `210814b` with both CI workflows green |
| CI | GREEN for `b4ea4cc` — [checkpoint validation](https://github.com/Alalkipgen/YFT/actions/runs/37179635595) and [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37179635656) |
| Regression | `HomeSitesMigrationTest` and `DataStoreHomeSitesRepositoryTest` fail on the old code: it kept Internet Archive, Wikimedia Commons and NASA and had no `home_sites_defaults_version` migration (the new tests do not even compile against it). They pass on the fix |
| Migration | PASS — untouched old defaults → YouTube, Facebook, TikTok; the owner's case (old defaults + his YouTube) → his YouTube first, then Facebook and TikTok, no duplicate; a custom list stays unchanged; an empty list stays empty; a second run changes nothing; `MAX_SITES` is kept; an unreadable store still shows the defaults |
| Logos | PASS — `SiteBrand.of` matches hosts and subdomains only (no look-alike domains); `SiteLogosTest`: every bundled logo tint has at least 3:1 contrast on the tile in Day and Night; Home and the browser start page show the logo with tag `site-logo-<slug>`, the tile stays described by the site name, other sites keep the letter |
| Notices | PASS — `OpenSourceNoticesTest`: Simple Icons 16.34.0 (CC0-1.0) is in About licences and `docs/THIRD_PARTY_NOTICES.md`, with "Site names and logos belong to their owners; YFT is not affiliated with them." |
| Full validation / instrumentation APK | PASS — core-model 49, core-data 15, app 474 (54 render skips), 0 failures; lint 0 errors / 95 warnings; instrumentation APK compiled; other modules unchanged since T17 (core-download 89, core-media 17, extractor-api 32, extractor-generic 8, extractor-sites 150); Python 22/22; new Kotlin lines <=100 |
| Not verified | Pixel renders were not regenerated in the sandbox (render tests skip without `YFT_RENDER_DIR`) |
| Owner check | Home shows YouTube, Facebook and TikTok with logos, and his own YouTube entry is not duplicated |

### T12 — "Video you copied" quick download sheet (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T09 checkpoint `b4ea4cc` |
| Regression | `HomeViewModelTest.oneVideoFoundOpensVideoYouCopiedAndSeveralKeepTheList` and `YftNavigationSmokeTest.oneCopiedVideoOpensVideoYouCopiedOverHomeAndMoreFormatsOpensTheList` fail on the old code (a Found lookup only offered View → the list; no sheet opened) and pass now |
| Row selection | PASS — `QuickDownloadChoicesTest`: only 360p → one Fast row "360p · 11 MB"; 1080/720/480/360 → Fast 480p and High 720p; audio only → Music "M4A · Fast" only; YouTube rows keep `audioCompanion` and Music picks the best M4A; HD/SD rank without a made-up height; only tall videos → one Video row; one unlabelled file → its format; several videos, unlabelled pairs, HLS/DASH and DRM-only keep the Found list; preselection follows the default quality |
| Download | PASS — `QuickDownloadViewModelTest`: a merged YouTube 720p row is resolved and queued with its `audioCompanion`; mobile data asks first and locks the selection; Wi-Fi only queues as waiting; a resolver failure queues nothing; More formats opens the list, or Download as for a single file; an empty store shows no rows |
| Screen | PASS — `QuickDownloadScreenTest` (Day and Night): title, length, rows with radio role and selection, More formats, Download, Queueing lock, Queued status with View downloads, metered dialog, empty state with Close; `YftDestinationTest`; the accessibility audit includes `12-quick-download` |
| Full validation | PASS — core-model 49, core-download 89, core-media 17, core-data 15, extractor-api 32, extractor-generic 8, extractor-sites 150, app 500 (58 render skips), 0 failures; lint 0 errors / 95 warnings; instrumentation APK compiled; Python 22/22; new Kotlin lines <=100 |
| Not verified | Pixel renders not regenerated in the sandbox (render tests skip without `YFT_RENDER_DIR`) |
| Owner check | Paste a YouTube link → Go → the sheet shows Music, Fast and High rows → Download → it plays with sound |

### T11 — Check the copied link when YFT opens (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T12 checkpoint `1f39a67` |
| Regression | `HomeViewModelTest.aCopiedLinkIsLookedUpOnceWhenTheCheckIsOnAndTheWindowHasFocus` and `CopiedLinkWatcherTest` fail on the old code (Home never read the clipboard by itself) and pass now |
| Watcher | PASS — `CopiedLinkWatcherTest`: setting off or no window focus → nothing read; non-text clips and clips Android 12+ classifies as not a link → not read; the first web link is emitted once per clip (same clip again → nothing; a new clip → emitted); text without a link is read once and never looked up; only the clip's timestamp and a hash are kept, never the text |
| Setting | PASS — `DataStoreSettingsRepositoryTest.copiedLinkCheckIsOnByDefaultAndPersistsWhenTurnedOff`; `SettingsViewModelTest.theCopiedLinkSwitchWritesTheSetting`; `SettingsScreenTest` (Privacy switch on by default, toggles); `AboutScreenTest` privacy line |
| Full validation | PASS — core-model 49, core-download 89, core-media 17, core-data 16, extractor-api 32, extractor-generic 8, extractor-sites 150, app 508 (58 render skips), 0 failures; lint 0 errors / 95 warnings; instrumentation APK compiled; Python 22/22; new Kotlin lines <=100 |
| Not verified | The Android 12+ "YFT pasted from your clipboard" message and Android 10+ focus timing need the phone |
| Owner check | Copy a YouTube link, open YFT → the link is looked up and the "Video you copied" sheet opens; Settings › Privacy › Check copied links off → nothing is read |

### T13 — "Search to download" page (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T11 checkpoint `38d2549` |
| Regression | `BrowserViewModelTest.wordsInTheAddressFieldSearchTheWebInsteadOfFailing`, `BrowserScreenTest.wordsOfferYouTubeAndWebSearchRows`, `HomeScreenTest.searchToDownloadOpensTheSearchPage` and the two new `YftNavigationSmokeTest` cases fail on the old code (words gave "Enter a valid HTTPS address"; no entry, rows, Download or View sites existed) and pass now |
| Search | PASS — `BrowserSearchTest`: words (and single words without a dot) search, addresses (`example.test/watch`, `m.youtube.com?v=1`, any scheme) do not; YouTube `https://m.youtube.com/results?search_query=…` and web `https://duckduckgo.com/?q=…` URL-encode the words (`cats+%26+dogs`, `caf%C3%A9+%231%3F`) |
| Start page | PASS — `BrowserScreenTest`: both rows and their URLs; a typed link shows no rows; search mode focuses the address field and Open browser does not; Download on "Link you copied" runs only on tap; View sites shows YouTube, Facebook, TikTok, Instagram and X and opens them; View all shows the saved Your sites, Add or edit sites goes Home |
| Navigation | PASS — `YftNavigationSmokeTest.searchToDownloadOpensTheBrowserStartPageInSearchMode`; `browserDownloadOfTheCopiedLinkIsLookedUpOnHomeAndOpensVideoYouCopied` (the link is handed to Home's entry, looked up, and one video opens the T12 sheet) |
| Accessibility / renders | The audit covers `02-browser-search` with `02-browser-start`; renders `02-browser-search` Day and Night added (skipped without `YFT_RENDER_DIR`) |
| Full validation | PASS — core-model 49, core-download 89, core-media 17, core-data 16, extractor-api 32, extractor-generic 8, extractor-sites 150, app 524 (62 render skips), 0 failures; lint 0 errors / 95 warnings; instrumentation APK compiled; Python 22/22; new Kotlin lines <=100 |
| Owner check | Home › Search to download: type words → the YouTube and web rows; type a link → it opens; View sites opens each site |

### T14 — Floating Download button in the browser (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T13 checkpoint `f627757` (CI green) |
| Regression | `BrowserScreenTest.downloadButtonOpensVideoYouCopiedForOneVideoAndHidesWhileTheSheetIsOpen`, `severalItemsShowACountBadgeAndOpenFoundOnThisPage`, `noDownloadButtonForDrmOnlyPagesOrTheStartPage` and `YftNavigationSmokeTest.browserDownloadButtonRaisesVideoYouCopiedOverTheBrowser` fail on the old code (no `browser-download-fab`) and pass now |
| Visibility | PASS — `BrowserDownloadFabTest`: shown only with a page and at least one savable item; hidden on the start page, for DRM-only pages, while the found sheet is expanded and while the address is edited; label "Download video, N found" |
| Tap | PASS — one video (or one video and its audio) opens T12's "Video you copied" sheet over the browser; several items expand "Found on this page"; the count badge shows only for more than one and is visual only (the count is in the label) |
| Accessibility / renders | The audit covers `02-browser-download`; renders `02-browser-download` Day and Night added (skipped without `YFT_RENDER_DIR`) |
| Full validation | PASS — core-model 49, core-download 89, core-media 17, core-data 16, extractor-api 32, extractor-generic 8, extractor-sites 150, app 536 (66 skipped, render/image-only), 0 failures; lint 0 errors / 95 warnings; `assembleDebugAndroidTest` OK; Python 22/22 |
| Owner check | Open a video page in the browser → the Mint Download button at the bottom right (a count badge when there are several) → tap opens "Video you copied" (one video) or the Found list |

### T18 — MP3 audio (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — the T14 checkpoint `72d98dd` (CI green) |
| Regression | `QuickDownloadChoicesTest.theM4aIsAlsoOfferedAsMp3WithAnEstimatedSize`, `QuickDownloadViewModelTest.theMp3RowQueuesTheM4aConvertedToMp3`, `PreviewViewModelTest.anM4aFileIsAlsoOfferedAsMp3ButTheOriginalStaysTheDefault`, `DownloadPlanFactoryTest` (mp3 choice) and `DownloadQueueTest` (audio/mpeg) fail on the old code (no MP3 row, variant or plan) and pass now |
| Choices | PASS — `Mp3VariantsTest`: only a supported whole-file AAC audio variant converts (Opus/WebM, E-AC-3, HLS, video and unknown types do not); MP3 192 and 128 follow the best AAC file, reuse its address, estimate the size from the length; the original audio stays the default in Download as |
| Control logic | PASS — `Mp3ConvertingTransferDispatcherTest`: other plans go straight through; an MP3 plan downloads the AAC into a private workspace, converts it and publishes only the MP3; a decode failure publishes nothing and frees the space; a full disk keeps the AAC so the retry only converts; a failed download is not converted; a stale checkpoint without its partial file starts over; discard removes the workspace |
| ID3 / PCM | PASS — `Id3v2TagTest` (ID3v2.3, one UTF-16 TIT2 frame, syncsafe size, Burmese text, control characters, surrogate-safe cut); `PcmLayoutTest` (mono/stereo for LAME, 5.1 keeps front left/right) |
| Instrumentation | `Mp3TranscoderInstrumentedTest` (API 34 emulator in CI): a 2 s stereo 44.1 kHz AAC fixture → MPEG-1 Layer III 192 kbps with the ID3 title, read back by `MediaExtractor` as `audio/mpeg`, 2 channels, about 2 s; a 1 s mono 48 kHz fixture → 128 kbps, 1 channel; a non-AAC file fails as incompatible |
| Licence | PASS — `OpenSourceNoticesTest` (LAME 3.100, LGPL-2.0-or-later, row in `THIRD_PARTY_NOTICES.md`, asset with the full text); `LicensesScreenTest.lameShowsItsLgplNoticeSourceAndTheFullLicense` |
| APK size | debug APK 16,708,773 → 19,727,067 bytes (+2.9 MB: unstripped debug `libmp3lame.so` + `libyft_mp3.so` for 3 ABIs; release builds strip them) |
| Full validation | PASS — core-model 54, core-download 104, core-media 17, core-data 16, extractor-api 32, extractor-generic 8, extractor-sites 150, app 542 (66 skipped, render/image-only), 0 failures; lint 0 errors / 95 warnings; `assembleDebug` and `assembleDebugAndroidTest` OK; Python 22/22 |
| Owner check | "Video you copied" or Download as → **MP3** → download → the MP3 plays in another app (with its title) |

### T19 — Release 1.0.0-beta.3 (2026-10-04)

| Check | Result |
| --- | --- |
| Starting state | PASS — T18 checkpoint `0ec1e46`, CI green (checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37199023973, emulator https://github.com/Alalkipgen/YFT/actions/runs/37199023965); T09 and T11–T18 OWNER CHECK, T10 and T15 SKIPPED |
| Version | `yft.versionName=1.0.0-beta.3`, `yft.versionCode=3`; CHANGELOG `[1.0.0-beta.3] - 2026-10-04`; notes `docs/release/1.0.0-beta.3.md` with the three phone-check lists |
| Local validation | PASS (2026-10-04, same version and docs change, run before a sandbox reset) — app 542 (66 skipped), 0 failures; lint 0 errors / 95 warnings; `:app:assembleRelease` OK; `verify-release-apk.sh --allow-unsigned --expected-version 1.0.0-beta.3`: unsigned APK 6,317,824 bytes, versionCode 3, minSdk 24, targetSdk 35, not debuggable, zip-aligned, no debug crash action |
| CI | PASS — checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37203631049 and https://github.com/Alalkipgen/YFT/actions/runs/37203674358, emulator https://github.com/Alalkipgen/YFT/actions/runs/37203631028 (`b0abe05`, `2f6284f`) |
| Signed release | `release-draft.yml` on tag `v1.0.0-beta.3`: lint, unit tests, signed minified build, signature, certificate and checksum checks, draft pre-release — PASS, https://github.com/Alalkipgen/YFT/actions/runs/37204457527: `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256 `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate `3A:EB:30:64:…:EC:78:98:8F` (same key as beta.1/beta.2) |
| Owner check | Release notes Phone checklist: all three lists with the signed APK |

### P1 — Browser follows in-page navigation (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Regression | PASS — with `BrowserViewModel.onUrlChanged` emptied (old behaviour) the in-page tests fail; with the fix they pass |
| WebView client | `SecureBrowserWebViewClientTest`: an in-page address is reported once (not for the document's own commit or a reload), requests then carry the new address, the DOM probe runs 1.5 s later only for the address the page stayed on |
| ViewModel | `BrowserViewModelTest`: feed → video A → video B (address, empty scope, one lookup each after 500 ms, the site's cookie carried over, Download button shown then hidden), observations after the change belong to the new video, a late answer for A never shows on B, quick scrolling looks up only the video that stays, the same post with `?pp=`/`#t=` keeps its candidates and title, a fragment change keeps generic media |
| Local validation | core-browser 57 tests (new `PageCandidateStoreTest` move case), 0 failures; app browser and detection suites 105 tests, 0 failures; `:app:lintDebug`; full app suite in CI |
| CI | PASS — checkpoint run https://github.com/Alalkipgen/YFT/actions/runs/37212263487, emulator run https://github.com/Alalkipgen/YFT/actions/runs/37212263477 |
| Owner check | m.youtube.com → tap a video → the address shows `/watch` → Download button → sheet; scroll to another video → the button follows it |

### P2 — Facebook and TikTok black page in the browser (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Reproduction | `SitePageDiagnosticTest` (CI emulator, API 34, real WebView) logs safe `YFT-DIAG` numbers; `ci-smoke-diagnostics.py` turns them into one notice per case. It never fails a run (live sites change) |
| Cause 1: zero-height page | Emulator run 21 (`9b124df`): `fb-share` video box `320x0`, `doc=320x493`; Compose's default wrap-content parameters gave the WebView a zero viewport height. With `MATCH_PARENT` (run 23, `8023c3b`, https://github.com/Alalkipgen/YFT/actions/runs/37214874452): video box `320x493`, `readyState 4`, playing, dark pixels 64 %, Download button after 1 s, the tap stays on the reel |
| Cause 2: WebView identity | TikTok gave the WebView identity a video error (`MEDIA_ERR_SRC_NOT_SUPPORTED`) and the Chrome-like identity a playing video (`readyState 4`); the browser now uses the Chrome-like identity (`BrowserUserAgent`). The comparison case `tt-video-ua` was dropped because the production browser is that identity now |
| Facebook lookup on phone pages | Owner phone 2026-10-04: the reel plays and the Download button appears, but "Facebook changed its page format" showed. Live check: a phone identity gets the mobile page without JSON payloads; the adapter now asks with a desktop identity. Regression `FacebookExtractorTest` "a phone identity asks for the desktop page" failed on the old code (expected the desktop agent, was the phone agent) and passes |
| Layout regression | `BrowserRouteTest.theBrowserWebViewFillsItsBoxInsteadOfWrappingItsContent` failed without the `MATCH_PARENT` parameters and passes |
| Policy tests | `AppLinkPolicyTest` (http error, `intent://` fallback once, other schemes ignored), `BrowserUserAgentTest`, `SecureBrowserChromeClientTest` (full screen show/hide), `SecureWebViewPolicyTest`, `BrowserRouteTest` full-screen case |
| Local validation | See the P2 checkpoint in SESSION_STATE: extractor-sites, core-browser and app unit tests, app lint, androidTest compile, `scripts/tests` |
| CI | PASS — checkpoint `8421700` run https://github.com/Alalkipgen/YFT/actions/runs/37218051422, emulator run https://github.com/Alalkipgen/YFT/actions/runs/37218051449 (9 instrumented tests; `fb-video` `readyState 4` without the notice, `tt-video` `readyState 4`) |
| Owner check | Facebook share link → Open in browser → the reel shows and plays, full screen works, the Download button appears and no "changed its page format" notice; TikTok on the CI emulator only |

### P3 — One download sheet with real qualities (OWNER CHECK, 2026-10-04)

| Check | Result |
| --- | --- |
| Regression (old behaviour simulated) | With one group per candidate, the candidate's title as its quality and no sound from the MP4 put back into the new code, 19 of the 68 targeted tests fail, among them `HomeViewModelTest.theQualitiesOfOneVideoCountOnceAndViewShowsThatVideoAgain` (Facebook HD, SD and DASH counted 3, expected 1), `DetectedMediaScreenTest`/`BrowserScreenTest` `theQualitiesOfOneVideoAreOneRow…` (3 rows), `QuickDownloadChoicesTest.aQualityLabelNeverComesFromThePageTitle`, `…aVideoWithoutAnAudioFileOffersItsOwnSoundAsM4aAndMp3` and `QuickDownloadViewModelTest.musicOfAnMp4OnlyVideoQueuesTheVideosSoundKeptAsM4a`; with the fix all pass |
| Grouping | `MediaGroupsTest`: groups by the adapter's video id, then by page and length, never merges two videos of one page; the base title drops the " — HD" label |
| Audio for MP4-only videos | `DownloadPlanFactoryTest` plans the MP4 download with `audioOnly`; `Mp3ConvertingTransferDispatcherTest` extracts the AAC track before converting; `AudioFromVideoTest` (only AAC MP4 with sound) |
| Real qualities | `Mp4HeaderParserTest` (fixtures: `moov` at the start/end, `tkhd` size, `stsd` avc1/mp4a, `esds`, truncated files, ≤ 6 ranged reads); `DefaultVariantResolverTest` probes a whole MP4 for height, width, frame rate; YouTube candidates carry height/fps/bitrate (`YouTubeExtractor`) so they show without a request |
| Sheet | `QuickDownloadScreenTest`: header (title, site, length), loading, error + Retry, empty, Music/Video rows, More formats opens inside the sheet (every quality, MP3 320/192/128, chips), Details, Download · size, status, Open Downloads; every testTag kept |
| Navigation | `YftNavigationSmokeTest`: Home View → sheet; Found list Preview → sheet over the list; browser Download (one video) → sheet; sheet Details → Download as |
| Audio extraction | `AudioExtractorInstrumentedTest` (CI emulator): copies the AAC track of a small MP4 asset into an M4A that `MediaExtractor` reads as `audio/mp4a-latm`, same duration |
| Local validation | app 563 tests (66 render tests skipped as usual), core-download 107, core-media 24, core-model 62, extractor-sites 152 — 0 failures; `:app:lintDebug` 0 errors; `:app:compileDebugAndroidTestKotlin` |
| CI | PASS — checkpoint `56f0c79` run https://github.com/Alalkipgen/YFT/actions/runs/37224812846, emulator run https://github.com/Alalkipgen/YFT/actions/runs/37224812796 (includes `AudioExtractorInstrumentedTest`) |
| Owner phone check 2026-10-05 | PARTLY — the sheet opens with real heights, but Music and Audio read as duplicates; the browser Download button on `facebook.com/story.php` opened the old Found list (26 duplicate "Video file" rows); generic web the same → P3-FIX |
| Owner check | Facebook reel and a YouTube video: View (Home) or the browser's Download button → one "Download" sheet with Music and Video rows, real resolutions and sizes; More formats opens inside; Music (M4A) and MP3 play |

### P3-FIX — Two sections, Facebook posts and byte-range pieces (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Causes | (1) Music quick rows, Fast/High and More formats repeated the same formats; (2) `FacebookUrls` did not identify `story.php`/`permalink.php`/posts/group posts, so no adapter ran and no candidate had a video id; (3) the page player's byte-range pieces (`bytestart`/`byteend`) each became a "Video file · MP4", so the browser's button saw many videos and opened the Found list |
| Regression (old code put back) | With the old `FacebookUrls`/`FacebookExtractor`, `CandidateNormalizer`, `BrowserObservationMapper`, `BrowserScreen` (one group per detected file) and the sheet's raw-height labels and one row per file, 10 of 70 targeted tests fail — exactly the new ones: `FacebookUrlsTest` "story, permalink, post and group post pages …", `FacebookExtractorTest` "a story page is asked on the owner's posts path …" and "a post without a video says so …", `CandidateNormalizerTest.byteRangePiecesOfOneFileAreTheWholeFileOnce`, `…requestsForOneOpaqueCdnFileWithOtherPerRequestValuesAreOneFile`, `…hlsPiecesAreOneStreamAndDistinctVideosStayApart`, `BrowserObservationMapperTest.aByteRangeRequestIsTheWholeFileAndItsProbeAsksForTheWholeFile`, `BrowserScreenTest.aFacebookPostsVideoOpensTheSheetAndThePlayersOwnFilesAreNotMoreVideos`, `QuickDownloadChoicesTest.rowsAreNamedAfterTheNearestStandardHeightAndKeepTheRealPicture` and `…oneRowPerResolutionPrefersSoundThenAWholeFileAndKeepsASilentOnlyResolution`; the sheet tests do not compile against the old API (quick rows, More formats); with the fix all pass |
| Sheet | `QuickDownloadChoicesTest` (11): exactly Audio (M4A, MP3 320/192/128) then Video, one row per standard resolution (848 × 478 → "480p", detail "848 × 478 · 30 fps · MP4"), short side and lower name on a tie, sound before silent and whole file before stream at one resolution, HD/SD rank 720/480, never the page title, preselection by default quality. `QuickDownloadScreenTest`: sections `quick-section-audio` above `quick-section-video`, radio rows `quick-option-<id>`, no Music/Fast/More formats text, preselected row, Download · size, MP3 row, Details, "No sound" and "Size unknown", disabled rows while queueing. `QuickDownloadViewModelTest`: Highest preselects 1080p, Up to 480p preselects 480p, MP3 192 queues the M4A converted, the video's own sound as M4A, an unknown id is ignored |
| Facebook posts | `FacebookUrlsTest`: `story.php`/`permalink.php` with a numeric `id`, `/{page}/posts/{id}`, `/groups/{g}/posts/{id}` or `/groups/{g}/permalink/{id}`, `/share/p/` → posts; photos and other pages are not claimed. `FacebookExtractorTest`: a story page is fetched on `https://www.facebook.com/{id}/posts/{story_fbid}` and resolves through the redirect to its video; a post without a video → `NO_MEDIA_FOUND` |
| One file, one video | `MediaFileUrlsTest` (byte-range parameters dropped, everything else kept; opaque CDN names; HLS piece families), `CandidateNormalizerTest` (pieces → one whole file; one opaque file under per-request values → one; three or more HLS pieces → one stream, dropped next to a manifest; distinct videos stay apart), `BrowserObservationMapperTest` (a ranged request and its probe ask for the whole file), `MediaGroupsTest.aNamedVideoIsThePagesOnlyVideoAndThePlayersOwnFilesDoNotCount`, `BrowserScreenTest` (a Facebook post's video plus the player's files → the button opens the sheet) |
| Live check (sandbox, 2026-10-05) | Public reel `1603698891196107`: desktop `story.php` and `permalink.php` → 302 to `/login/`; `/{owner}/posts/{story_fbid}` → 200, redirected to `/{owner}/videos/…/{video_id}/` with `browser_native_hd_url`, `dash_manifest_xml_string`, `videoDeliveryLegacyFields` and the video id 19 times; `m.facebook.com/story.php` with a phone identity → app-login page (status and markers only) |
| Local validation | core-model 63, core-media 24, core-download 107, extractor-sites 155, extractor-generic 14, core-browser 67, app 565 (66 render tests skipped) — 0 failures; `:app:lintDebug` 0 errors, 95 warnings (unchanged) |
| CI | PASS — checkpoint `aad59b3` run https://github.com/Alalkipgen/YFT/actions/runs/37234905286 (#149), emulator run https://github.com/Alalkipgen/YFT/actions/runs/37234905207 (#26) |
| Owner check | Facebook story/post page and reel in the browser: Download → the "Download" sheet (not the Found list) with exactly Audio (M4A, MP3 320/192/128) and Video (one row per resolution, e.g. "480p" with "848 × 478"); YouTube the same; a generic page with one video → the sheet |

### P4 — Facebook: every quality with sound (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Manifest | `FacebookDashManifestTest` (6): every `Representation` a whole file with picture, codec and bitrate; segment lists, templates, relative addresses and unnamed codecs are no whole file; protected, live or foreign documents yield nothing; the older URL-encoded manifest and hour-long durations; best bitrate per picture, AVC before AV1, AAC for the sound; at most six sizes, tallest first |
| Extractor | `FacebookDashExtractorTest` (5): every size is a merged MP4 with the AAC track and the AAC track is Audio; a page without AVC asks Safari's page without the session; a page with AVC needs no second request; a failed AVC page keeps the first page's tracks; an expired audio track leaves only the progressive files. Sizes: a merged row states none (`contentLengthBytes` null), its sound is 57 372 bit/s × 31.5 s / 8 = 225 902 B |
| Merge gate | `AudioVideoMuxEngineTest`: AV1 merges are off by default (refused at API 34/35 before any transfer), on only with `av1Enabled` from API 34; HE-AAC merges with AVC. `MergeSupportTest`: AVC merges everywhere, AV1 never while off, decoder read once only for AV1. `DownloadPlanFactoryTest`: AV1 refused, AVC + HE-AAC → MP4 merge |
| Sheet and files | `QuickDownloadChoicesTest.aCompleteFileBeatsAMergeAtTheSameResolution`; `DefaultVariantResolverTest` "a stated sound track stays audio when the server labels the mp4 as video"; `DownloadPlanFactoryTest` "sound alone in an mp4 container is saved as m4a and a video stays mp4" |
| Regression (old code put back) | `FacebookExtractor` before the size fix → `FacebookDashExtractorTest` "every picture size is a merged MP4 …" fails; `DownloadPlanFactory` without the M4A rule → the new extension test fails; `DefaultVariantResolver` and `QuickDownloadChoices` from `aad59b3` → the resolver and order tests fail; `AudioVideoMuxEngine` from `aad59b3` → `AudioVideoMuxEngineTest` does not compile (no AV1 gate). Restored, all pass |
| Emulator | `AudioVideoMuxerInstrumentedTest` (API 34): AVC + AAC → one MP4 with both tracks and 15 frames passes; AV1 + AAC failed on runs #27 and #28 (`LocalMuxResult.Failure`), so AV1 merges were turned off; the AV1 test is now a probe that logs `YFT-DIAG av1-mux probe … step=…` and requires the gate to stay off while it fails |
| Live check (sandbox, public reel, 2026-10-05) | Chrome page: 9 whole-file DASH tracks, video AV1 and VP9 only, audio HE-AAC `mp4a.40.5`. Safari page: AVC 552×358 and 1108×720 plus the audio. Sheet: HD, SD (progressive), 1080p AV1 (1660×1078), 720p AVC, 360p AVC, Audio 57 kbps. CDN `HEAD` sizes: HD 67 984 016 B, SD 19 895 260 B, 1080p AV1 video 104 714 303 B (bandwidth estimate right), 720p AVC video 63 245 496 B (estimate about 3.5 × too high), 360p AVC video 15 946 354 B, audio 4 489 974 B (estimate exact); the audio-only file is served as `video/mp4`. No URLs recorded |
| Local validation | core-model 63, core-media 25, core-download 109, extractor-sites 166, extractor-generic 14, core-browser 67, app 573 (66 render tests skipped) — 0 failures; `:app:lintDebug` 0 errors (96 warnings: the 95 known plus `OldTargetApi` from the newer local SDK); `:app:compileDebugAndroidTestKotlin`; `scripts/tests` 24 pass |
| CI | PASS — checkpoint `c786929` run https://github.com/Alalkipgen/YFT/actions/runs/37243460818 (#152), emulator run https://github.com/Alalkipgen/YFT/actions/runs/37243460781 (#29; `av1-mux probe sdk=34 decoder=true result=Failure step=samples`, so AV1 stays off) |
| Owner check | Facebook reel → the sheet shows Audio (M4A, MP3) and Video 360p and 720p (AVC, merged) plus the progressive HD/SD; 1080p AV1 is not offered while AV1 merges are off; each row downloads and plays with sound and the sizes look right |

### P5 — Download button on feeds (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Script answers | `FocusedVideoProbeTest` (9, Robolectric): the script only reads the page (no cookies, storage, requests, page text, clicks or playback) and returns one link; `evaluateJavascript`'s quoted answer and a raw object parse; `null`, `undefined`, `"source":"none"`, an empty, missing or non-text link, an unknown source, an array, broken JSON and an answer over 4 096 characters give nothing |
| Links per site | YouTube `/watch?v=` (any parameter order, `m.`/no host prefix) and `/shorts/{id}` keep only the 11-character id on `www.youtube.com`; feeds, channels, playlists and short ids are no video. Facebook `/reel(s)/{id}`, `/watch/?v=`, `/video.php?v=`, `/watch/live/?v=`, `/{page}/videos/[slug/]{id}`, `/share/v\|r\|p/{code}`, `story.php`/`permalink.php` (`story_fbid` + `id`), `/{page}/posts/{id}`, `/groups/{g}/posts\|permalink/{id}` lose tracking parameters (`__cft__[0]` brackets included); photos, pages and incomplete posts are no video. TikTok `/@user/video/{id}` on `www.tiktok.com`; profiles, photos and non-numeric ids are not |
| Own site only | Another site's link, a look-alike host (`youtube.com.fixture.test`, `notyoutube.com`), `http:`, user info, `javascript:` and relative links are refused; only HTTPS YouTube, Facebook and TikTok pages are feeds (`fb.watch`, Vimeo and other sites are not) |
| Real WebView (CI emulator) | `FocusedVideoProbeInstrumentedTest` (6) over sanitized fixtures in `app/src/androidTest/assets/focused-video/` (invented ids, served offline under the feed's own address): YouTube home → the video in the middle (`centre`); a playing inline preview wins over the middle one (`playing`); a short is its page's own video (`page`); Facebook feed → the post beside the playing video, cleaned; TikTok For You → the video in the middle; a page without videos answers `none` |
| Local Chromium run of the script | Headless Chromium (Playwright, 412 × 860 mobile viewport) over the same fixtures: YouTube home → `watch?v=BBBBBBBBBB2` centre; playing preview → `PPPPPPPPPP5` playing; Facebook → `story.php?story_fbid=pfbid0Fixture2Def…` playing; TikTok → `@second_user/video/7300000000000000002` centre; short page → page |
| Button | `BrowserDownloadFabTest` (4): on YouTube, Facebook and TikTok pages the button shows with nothing found (not on the start page, with the sheet open or while typing an address) and reads "Download the video on screen"; a feed always looks for the video on screen, a video page of those sites opens its own video (one found) or the list (several) and looks only while nothing is found; other sites keep P3's rule. `BrowserScreenTest`: a feed shows the button with nothing found and no count badge, a tap calls the focused lookup even when the feed found files, a tap while it looks does nothing, `browser-focus-notice` shows the notice |
| View model | `BrowserViewModelTest` (4 new): feed tap → the script → the adapter looks up `https://www.youtube.com/watch?v=…` with the site's own cookie → the video is selected for the sheet (anchored to its own page) and one open-sheet event is sent, the feed's own list is unchanged; no video on screen and an adapter failure ("This YouTube post has no downloadable video.") show a notice that clears after 4 s, and a page that never answers the script ends after 5 s; no script on other sites and a result there is ignored; a video page of a feed site is no feed, and a feed the user left drops its running lookup and a late script answer |
| Regression (old code put back) | Removing the feed-site check, the page-generation check and the link clean-up → `noScriptRunsOnOtherSites…`, `aVideoPageOfAFeedSiteIsNoFeedAndALeftFeedDropsItsLookup`, `aFeedTapLooksUpTheVideoOnScreen…`, `FocusedVideoProbeTest` `theAnswerOfEvaluateJavascript…` and `onlyAnHttpsLinkOfThePagesOwnSite…` fail (5); P3's button rule (`savableCount > 0`, no focused action) → `BrowserDownloadFabTest` (2), `BrowserScreenTest.aFeedShowsTheButtonWithNothingFound…` and `BrowserViewModelTest.aFeedTapLooksUp…` fail (4). Restored, all pass |
| Local validation | core-model 63, core-media 25, core-download 109, extractor-sites 166, extractor-generic 14, core-browser 76, app 581 (66 render tests skipped) — 0 failures; `:app:lintDebug` 0 errors, 96 warnings (unchanged); `:app:compileDebugAndroidTestKotlin` |
| CI | PASS — checkpoint `737724b` run https://github.com/Alalkipgen/YFT/actions/runs/37245672464 (#153), emulator run https://github.com/Alalkipgen/YFT/actions/runs/37245672489 (#30, `FocusedVideoProbeInstrumentedTest` included) |
| Owner check | YouTube home feed → scroll to a video → Download → the sheet is for that video; the same on a Facebook feed (a video post or reel) and TikTok's For You; a feed with no video on screen → "No video on screen to download…" |

### P6 — 2K and 4K (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Format selection | `YouTubeExtractorTest` (2 new; fixtures `player_4k.json`, `player_4k_av1_only.json` — `player_ok.json` plus VP9, HDR VP9 and AV1 streams): 2160p and 1440p are the 8-bit VP9 WebM streams (`vp9`, itags 313/271) merged with the Opus WebM track (itag 251, its size added to the row's); 1080p stays AVC with AAC; HDR VP9 (`vp09.02…`), AV1 and the VP9 1080p copy are not offered and their `n` values are never solved; the Opus track is no Audio row (Audio stays the AAC original mix); without 8-bit VP9 the 2K/4K rows are AV1 MP4 with the AAC track and Opus is never solved |
| Merge gate | `AudioVideoMuxEngineTest` (3 new): WebM output from Android 10 (refused at API 28 with `ANDROID_VERSION`); a WebM merge takes only WebM video in VP9 and WebM sound in Opus or Vorbis; the engine hands `video/webm` to the muxer. `MergeSupportTest.vp9WebmWithOpusMergesFromAndroid10`: VP9 + Opus/Vorbis WebM merges from API 29 only; VP9 with AAC, AV1 in WebM and VP9 in MP4 never |
| Download plan | `DownloadPlanFactoryTest` "VP9 WebM video with its Opus track becomes a WebM merge from Android 10": `Fixture 2160p.webm`, type `video/webm`, tracks `….video.webm` and `….audio.webm`; refused at API 28 and with AAC sound (`INCOMPATIBLE_TRACKS`) |
| Decoder warning | `VideoPlaybackSupportTest` (3): codecs name their decoder (`vp9`/`vp09.…` → `video/x-vnd.on2.vp9`, `av01` → `video/av01`, AVC, HEVC); a video plays only where a decoder of its type takes its size (either way up; the height alone without a width); an unreadable decoder list (read once), an unknown codec, no codec or no size never warns. `QuickDownloadChoicesTest`, `QuickDownloadViewModelTest`, `QuickDownloadScreenTest` (1 each): a 2K/4K row the phone cannot decode shows "May not play on this phone" on its own row and still downloads; Full HD and lower rows are never asked |
| Facebook | No change needed: `FacebookDashOffers` ranks every height up to 4320 (AVC first), so 1440p/2160p AVC tracks Facebook lists become merged rows like P4's; AV1-only sizes stay off |
| Emulator | `AudioVideoMuxerInstrumentedTest.aVp9VideoAndItsOpusSoundBecomeOneWebm` (API 29+): one-second ffmpeg fixtures `mux/video-vp9.webm` (libvpx-vp9, 160 × 90, 15 fps) and `mux/audio-opus.webm` (libopus, 48 kHz) → one WebM that `MediaExtractor` reads back as `video/x-vnd.on2.vp9` + `audio/opus` with 15 frames (see CI) |
| Regression (old code put back) | `YouTubeExtractor` from `737724b` → both new extractor tests fail; `MergeSupport` and `DownloadPlanFactory` from `737724b` and `QuickDownloadChoices` without the decoder check → 5 app tests fail; the WebM branch of `AudioVideoMuxCompatibility.evaluate` removed → 3 `AudioVideoMuxEngineTest` tests fail. Restored, all pass |
| Local validation | core-model 63, core-media 25, core-download 112, extractor-sites 168, extractor-generic 14, core-browser 76, app 589 (66 render tests skipped) — 0 failures; `:app:lintDebug` 0 errors, 96 warnings (unchanged); `:app:compileDebugAndroidTestKotlin` |
| CI | P6 `a1e99fd`: checkpoint PASS https://github.com/Alalkipgen/YFT/actions/runs/37248234059 (#154); emulator https://github.com/Alalkipgen/YFT/actions/runs/37248234047 (#31) ran 20 tests — the VP9 + Opus WebM merge passed, P5's `FocusedVideoProbeInstrumentedTest.theTikTokVideoInTheMiddleOfTheScreenIsFound` failed: the fixture scrolled while it loaded, before the WebView had its size (`100vh` was 0), so the first video was in the middle. Fix: each feed fixture exposes `window.yftFixtureSettle()` and the test calls it once the WebView is laid out (headless Chromium: all five cases unchanged). Fix `04ef622`: PASS — checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37249191948 (#155), emulator https://github.com/Alalkipgen/YFT/actions/runs/37249192004 (#32, 20 instrumentation tests, 0 failures, incl. `aVp9VideoAndItsOpusSoundBecomeOneWebm`) |
| Owner check | A YouTube video with 4K (e.g. a "4K" nature video) → Download → the sheet shows "2160p · 4K" and "1440p · 2K" (WebM) above 1080p → download 2K → the `.webm` plays with sound in the phone's player or YFT's Preview; on a phone without a VP9 decoder that large the row says "May not play on this phone" |

### P7 — Preview APK with a test key (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Build logic | `scripts/tests/test_preview_build.py` (6): `preview` is `initWith(release)` with `.preview`, `-preview` suffix, not debuggable, release fallbacks and shrinking kept; it signs only with `signingConfigs["preview"]`, whose four settings come from `YFT_PREVIEW_*` only (never `YFT_RELEASE_*`, `keystore.properties` or the release keystore); it uses `src/release/java` (no debug crash action) and is named "YFT Preview"; the workflow makes the key with `keytool`, masks its password, deletes it, reads no secret, verifies the APK against that key with `--package com.alal.yft.preview` and uploads `yft-preview-apk`. With the old build file and script 4 of the 6 fail. All 30 script tests pass |
| Local preview build | `./gradlew :app:assemblePreview -Pyft.previewBuild=1` with a local throwaway key → `app-preview.apk` 6 710 000 B; `verify-release-apk.sh --package com.alal.yft.preview --expected-version 1.0.0-beta.3-preview.1 --expected-cert-sha256 <local key>`: package `com.alal.yft.preview` ("YFT Preview"), versionCode 3, minSdk 24, targetSdk 35, not debuggable, zip-aligned, debug crash action absent, v2 + v3 with exactly the test key → VERIFIED; the default package check refuses it. Only `YFT_PREVIEW_STORE_FILE` set → Gradle stops with "Preview signing is only partly configured" |
| Local validation | core-model 63, core-media 25, core-download 112, extractor-sites 168, extractor-generic 14, core-browser 76, app 589 (66 render tests skipped) — 0 failures; `:app:lintDebug` 0 errors, 96 warnings (unchanged); `:app:compileDebugAndroidTestKotlin`; `scripts/tests` 30 pass |
| CI | PASS on `dacecd8` — Preview APK https://github.com/Alalkipgen/YFT/actions/runs/37294511016 (#1: test key made in the job, APK verified against it, `yft-preview-apk` artifact 11338621241, `1.0.0-beta.3-preview.1`); checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37294510980 (#157); emulator https://github.com/Alalkipgen/YFT/actions/runs/37294510833 (#33, 20 tests, 0 failures) |
| Owner check | Install YFT Preview from `yft-preview-apk` next to beta.3 (both icons stay) and run the part 1 checklist (FIX_ADD_PLAN §6 at `ce3cd25`). Done by the owner on 2026-10-05; findings in FIX_ADD_PLAN §2 (part 2) |

### P9 — Short sheet and pinned Download (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Formats | `QuickDownloadChoicesTest`: compact M4A + MP3 128, preferred 720p + 480p, 360p fallback, nearest lower/higher when 720p is absent, only 1080p/4K starts at 1080p, one available row, audio-only and unknown height, saved Highest/480p choices; full list and option IDs untouched |
| Screen | `QuickDownloadScreenTest`: two sections, full list once after More formats, collapse/expand preserves a chosen off-compact option; Download and Details visible without scrolling with 12 expanded rows and still visible after scrolling to the last row; queue/metered/failure/empty behavior kept |
| Settings | `DownloadPreferencesTest`, `DataStoreDownloadPreferencesRepositoryTest`, `SettingsScreenTest`: unset quality 720p, every saved quality including Highest survives reopening, Highest remains selectable in Settings |
| Regression proof | Original `d642607` screen and preference model put back: `downloadStaysVisibleWithTwelveExpandedRowsWithoutScrolling` fails with "component is not displayed"; `defaults are conservative and concurrency stays in range` fails with HIGHEST instead of UP_TO_720P. New files backed up, restored with `cp`, and checked with `cmp` |
| Local validation | core-model 63 tests (0 skipped); core-data 17 tests (0 skipped); app 594 tests (66 skipped); 0 failures/errors; :app:lintDebug 0 errors (low-memory helper) |
| Device limits | No local emulator/KVM; 66 native design-render tests skipped. No native pixel-review claim |
| Owner check | YouTube 4K video: two Audio + two Video rows, 720p selected, Download visible; More formats shows 2K/4K; a 720p and MP3 download play |

### P10 — Slow networks (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Transport | Verified TLS MockWebServer (`ExtractorTlsFixture`, test-only okhttp-tls): progress beyond scaled old total budget succeeds; idle body fails; first connection drop retries once; 502/503/504 retry at most twice with injected 1000/3000 ms waits; 401/403/404/429/500 never retry; read-only POST body/method kept; cancellation of a body read closes the call and never retries. Existing HTTPS, credentials, redirects, cookies and body caps still tested |
| Headless | `HeadlessPageFetcherTest`: slow body, idle body, dropped first connection, transient statuses, 404, cancellable body read; existing session-free navigation headers, no cookies, redirect/body caps unchanged |
| Home | `HeadlessLinkInspectorTest`: an answer at 60 s succeeds; 90 s overall timeout. `HomeViewModelTest`: Searching before 10 s, SlowSearching at 10 s, Cancel terminates the inspector and cannot publish a late result. `HomeScreenTest`: slow text, visible Cancel and progress; browser action does not start another lookup |
| Browser | NETWORK does not set siteNotice; manual retry calls the adapter again; a focused failure shows the short site message with Retry for that video, keeps Download and forgets the error on navigation; `BrowserScreenTest` tests the focused Retry callback with no top banner |
| Regression proof | Actual `13bbf05` extractor client, Home inspector and Home ViewModel put back: `aDroppedFirstConnectionSucceedsOnTheSecondRequest`, `aPageAnswerAfterSixtySecondsStillFindsItsVideo`, `afterTenSecondsTheLookupIsStillRunningAndCancelStopsIt` all fail. New scaled-policy test class temporarily omitted solely to compile against the old constructor. All source/tests restored with `cp` and `cmp` verified |
| Local validation | core-browser 79 tests; core-data 17 tests; app 607 tests (66 skipped); 0 failures/errors; :app:lintDebug 0 errors |
| P9 CI | Checkpoint `13bbf05` passed: https://github.com/Alalkipgen/YFT/actions/runs/37363825709. Preview and emulator jobs never started: GitHub annotation "The job was not acquired by Runner of type hosted even after multiple attempts". No code-test failure and no workflow changes; next code push retries these jobs automatically |
| Owner check | On a slow mobile line, paste a YouTube link: slow status after 10 s, then formats; Cancel stops it. Browser does not show a background network banner; focused network failure offers Retry |

### P11 — Rows never vanish (OWNER CHECK, 2026-10-05)

| Check | Result |
| --- | --- |
| Metadata-first rows | `QuickDownloadChoicesTest`: every unresolved stated quality appears, honest unknown/estimated size, overflow gives no estimate; real DASH rows stay compact while separate HD/SD hints remain under More formats |
| Size checks | `QuickDownloadViewModelTest`: rows visible before gated checks finish, at most two probes active, failed checks keep rows, and arriving sizes preserve IDs/title/order/selection |
| Final check | 404 queues nothing and never retries; transient NETWORK retries the same chosen quality, queues once and never replaces its selection. Details/metered behavior kept |
| Regression proof | Before sandbox reset, actual pre-P11 `bdc9f88` ViewModel and the original unresolved-source drop were put back: both `unresolvedSourcesStillGiveEveryStatedQualityWithoutASizeCheck` and `everyStatedRowIsVisibleBeforeSizeChecksFinishAndOnlyTwoRunAtOnce` failed with NullPointerException (missing rows). New files restored with cp and cmp verified. The same implementation/tests were recovered from committed session tool events after reset |
| Public live check | Production parser, no HEAD: HTTP 200; host www.facebook.com; path /reel/1603698891196107/; 136045 bytes; AVC heights 358/720, AAC 1, native files 2 — five metadata candidates before size checks. No raw/signed addresses or body output |
| Local validation | 44 recovered QuickDownload tests pass; P11 full validation: core-model 63 tests, core-media 25 tests, extractor-sites 168 tests, app 614 tests (66 skipped); 0 failures/errors; :app:lintDebug 0 errors |
| CI / verified test fixtures | f82427b preview 37377253600 and emulator 37377254034 passed. Validation 37377253562 failed; full logs are not available anonymously, so exact cause unverified. TLS fixture now binds explicit loopback, pins localhost DNS to that address and verifies the localhost certificate normally; slow-stream/cancel tests have more runner headroom. Production deadlines/TLS unchanged. Local full root revalidation: 1124 tests (66 render skips), zero failures/errors, all five lint reports zero errors. Final checkpoint CI retry pending; no workflow changes |
| Owner check | Facebook reel on a slow connection: every quality arrives together; sizes fill later; nothing disappears; a dead quality keeps the sheet and queues nothing |

### P14 — YouTube asks visionOS first (OWNER CHECK, 2026-10-05, Track B)

| Check | Result |
| --- | --- |
| visionOS alone | PASS — `YouTubeExtractorTest`: a complete visionOS fixture is the whole lookup: no watch page GET, one POST without the page key, no `Cookie`, `Authorization`, `X-Goog-Visitor-Id` or `visitorData`; rows 1080p, 720p, Audio with the fixture's sizes; details end "visionOS first: complete, no watch page" |
| Watch page chain | PASS — no status, bot check, age check, unplayable, private, live, SABR only and no answer each read the watch page, whose own streams then count; an age check from visionOS ends `LOGIN_REQUIRED` with visionOS asked once; a missing `contentLength` or a protected (`signatureCipher`) format reads the page; DRM stays `DRM_PROTECTED`. The chain reuses visionOS's answer (asked once) |
| Updated chain tests | Client order now `VISIONOS` first (`[VISIONOS, MWEB, ANDROID, WEB_EMBEDDED_PLAYER]` without an inline response; desktop `[VISIONOS, WEB, ANDROID, WEB_EMBEDDED_PLAYER, MWEB]`); lookup details start with visionOS's verdict and "visionOS first: …"; a failed page GET keeps those lines |
| Regression proof | Pre-P14 `YouTubeExtractor.kt` and `YouTubePlayerResponseParser.kt` (`HEAD` 2071342) put back with the new tests: 17 of 48 failed, among them `a complete visionOS answer is the whole lookup, without the watch page`, `an age check from visionOS first leaves the verdict to the watch page`, `a visionOS answer without a size or with a protected format reads the watch page`, `DRM stays refused when visionOS is asked first`, `any other visionOS answer reads the watch page, whose own streams then count`; restored, `cmp` identical, all pass |
| Live (sandbox, data-centre IP) | Before: `dQw4w9WgXcQ` visionOS alone 1 request 16,725 B, 27 adaptive formats all with URL and size, AVC 1080–144, VP9/AV1 to 2160, AAC + Opus; ranged GET itag 136 → 206, total = stated length; chain 4 requests 185,088 B. After: 1 request 16,677 B, 180 ms; rows 2160/1440 VP9, 1080/720/480/360 AVC merged, AAC audio. `LXb3EKWsInQ` (4K) and `ssRH9gJdJdM` (Short): bot check from every client on this IP → chain 4 requests, `BOT_CHECK`. `HtVdAasjOgU` (age): `LOGIN_REQUIRED`, 2 requests. Only status, host, path, sizes and heights recorded |
| Local validation | `:extractor-sites:test` 173 tests, `:app:testDebugUnitTest` 614 tests (66 skipped), 0 failures; `:app:lintDebug` 0 errors (95 warnings, as before); new Kotlin lines ≤ 100 |
| Owner check | On the slow line a YouTube link opens clearly faster; 720p, 1080p and 4K download and play |

### P15 — Facebook public page first (OWNER CHECK, 2026-10-05, Track B)

| Check | Result |
| --- | --- |
| Public page | PASS — `FacebookPublicPageTest`: a public reel is one GET as Safari with navigation headers and Referer but no `Cookie`; media keep the browser's identity; details "public page: the video, without the session" |
| Session page | PASS — a login wall, an unavailable page, a login page and HTTP 500 on the public page each read the session page (Cookie, browser agent) and succeed; a public page showing another video reads the session page; a login wall on both is `LOGIN_REQUIRED` with both pages in the details; DRM stays `DRM_PROTECTED`; NETWORK and RATE_LIMITED on the public page end the lookup after one request |
| Share links and AVC ladder | PASS — a `share/r/` link is one GET with the session that resolves through the redirect; `FacebookDashExtractorTest`: a share link's AV1-only session page still asks Safari's AVC ladder without the session (2 requests), a failed ladder keeps the first page's tracks, and the public page already asked as Safari is reused instead of asked again (2 requests, not 3) |
| Updated tests | `FacebookExtractorTest` session replay and phone identity use a session-only video; `SiteNavigationHeadersTest` checks both Facebook page requests (navigation headers, no cookie in Home lookups) |
| Regression proof | Pre-P15 `FacebookExtractor.kt` (`57663c0`) put back with the new tests: 8 of 60 failed — `a public reel is one request as Safari without the session`, `a video only the session sees is read with the session`, `a public page showing another video reads the session page`, `a login wall on both pages is login required`, `a line that fails on the public page ends the lookup there`, `the public page already asked as Safari is not asked again for the AVC ladder`, `the page request replays the browser session only to facebook`, `facebookPageHasNavigationHeadersAndNoHeadlessCookie`; restored, `cmp` identical, all pass |
| Live (sandbox, no session) | Before: reels `1603698891196107` / `2192941138213802` 2 requests (283 KB / 259 KB); Safari alone had the same video ID, HD/SD whole files and AVC 358/720 and 640/1024. After: 1 request each (136 KB / 128 KB), rows HD, SD, AVC merged ×2, Audio. `/watch/?v=452499129200583`: 2 requests before and after (Safari 914 characters without the video, then the session page). `share/v/1Q3kAyptrS` and `share/r/1Q3kAyptrS`: Safari got a 623-byte page without the redirect; 2 requests before and after. Only status, host, path, sizes, IDs and heights recorded |
| Local validation | `:extractor-sites:test` 181 tests, `:app:testDebugUnitTest` 614 tests (66 skipped), 0 failures; `:app:lintDebug` 0 errors; new Kotlin lines ≤ 100 |
| Owner check | A Facebook reel on the slow line opens faster, with every quality |

## Runtime tests still requiring a device/emulator

| Test | Required environment | Success criterion | Current result |
| --- | --- | --- | --- |
| On-device app launch/navigation | Android API 24+ device/emulator | App launches to Home and routes render | PARTIAL — T02 real CI API 34 Home → Browser passes; other devices/routes remain OWNER CHECK |
| Direct HTTPS MP4 preview | Android API 24+ device/emulator | Player reaches ready and renders | NOT RUN |
| Non-DRM HLS preview | Android API 24+ device/emulator | Selected stream reaches ready | NOT RUN |
| Non-DRM DASH preview | Android API 24+ device/emulator | Selected stream reaches ready | NOT RUN |
| Secure browser load/history | Android API 24+ device/emulator | HTTPS page loads; history/progress/errors behave | PARTIAL — T02 real CI API 34 public HTTPS load passes without crash; full history/error/device matrix remains OWNER CHECK |
| DOM candidate extraction | Android WebView test page | URLs returned without page mutation | NOT RUN on Android — exact script passes committed fixture in headless Chromium |
| Cookie/header preview | Controlled authenticated fixture | Preview succeeds with session context | NOT RUN |
| DRM fixture | Known encrypted manifest | Structured unsupported result | NOT RUN |
| Audio/video mux on device | Android API 24+ device/emulator | `MediaExtractor`/`MediaMuxer` produce a playable MP4 | NOT RUN — compatibility gate and recovery logic covered locally; T17 YouTube whole-file pairs not verified on a device |
| Foreground download lifecycle | Android API 24+ device/emulator | Service survives backgrounding, shows progress and self-stops | NOT RUN |
| MediaStore publication | Android 10+ device/emulator | Pending item becomes visible in Downloads only after verification | NOT RUN — app-private staging covered locally |
| Real low-storage transfer | Device with a nearly full volume | Transfer fails cleanly without a corrupt published file | NOT RUN — covered only through an injected failure |
| YouTube download on a device | Phone on a residential or mobile network, current Android System WebView | A public video lists progressive and M4A candidates (device clients or the page's client with a minted token), the solver and token WebViews answer within their timeouts, and the file downloads and plays | NOT RUN — no device/KVM; from the sandbox's datacenter IP device clients answer one test video and the other stays bot-checked (T16) |
| YouTube non-embeddable video | Same as above | The page client's streams are offered; a 403 at download time fails clearly instead of hanging | NOT RUN |
| Notification permission | Android 13+ device/emulator | `POST_NOTIFICATIONS` is requested before the first download and progress shows once granted | NOT RUN — request flow in `ui/components/NotificationPermission.kt` has no automated test |
| Library on a device | Android 10+ device/emulator | `Download/YFT` items and app-storage files play in app, open and share in other apps, and delete after confirmation | NOT RUN — Robolectric repository, provider and screen tests pass |
| Clear browsing data on a device | Android API 24+ device/emulator | Sites opened in YFT are signed out and storage/cache are empty afterwards | NOT RUN — cleaner composition covered locally |
| Wi-Fi-only switching on a device | Device with Wi-Fi and mobile data | Transfers pause on mobile data, the banner explains it, and they resume on Wi-Fi | NOT RUN — policy and banner covered locally |
| Storage janitor on a device | Android API 24+ device/emulator | After a forced stop, stale `.part` files and workspaces disappear on the next launch | NOT RUN — covered with temporary folders locally |
| Signed beta fresh install | Android 7.0+ device/emulator, owner-signed APK | `scripts/device-smoke-test.sh --fresh` passes: install, launch screen, Home, no crash | NOT RUN — no device/KVM |
| Signed beta upgrade | Same, with the previous signed beta | `scripts/device-smoke-test.sh --fresh --upgrade-from` keeps the installation, settings and downloads | NOT RUN — static upgrade-compatibility check passes |
| Launcher icon and launch screen | Android 7.x, 8+, 12+ and 13+ (themed icons) launchers | Legacy PNG, adaptive and monochrome icons and the launch/splash screen render correctly | NOT RUN — resources resolve in Robolectric |
| Redesigned screens on a device | Android 7.0+ phone, light and dark system theme | Every screen matches the renders; Night has no white flash; the mini player and sheets behave | NOT RUN — Robolectric renders and tests pass |
| TalkBack and large text on a device | Android device with TalkBack and the largest font size | Every control is announced by name; nothing is cut off at 200% | NOT RUN — `AccessibilityAuditTest` passes in Robolectric |
| Update from 1.0.0-beta.1 | Phone with the published beta.1 | beta.2 installs over it and keeps settings and downloads | NOT RUN — same signing certificate as beta.1 and versionCode 2 over 1 |

## Phase 5 and later regression categories

- Site-adapter fixture drift
- Adapter-to-generic fallback
- Secret-redaction tests
- Re-run of the Phase 4 transfer and recovery suite on every change

### P12 — One sheet and one lookup on site pages (OWNER CHECK, 2026-10-06)

| Check | Result |
| --- | --- |
| Action table | `BrowserDownloadFabTest`: a site video page (YouTube-shaped and Vimeo-shaped) with 0, 1 or 4 found -> OPEN_PAGE_VIDEO, visible, label "Download this video"; a generic page with 4 -> OPEN_MAIN_VIDEO; feeds keep FIND_VIDEO_ON_SCREEN |
| One lookup | `BrowserRouteTest.twoDownloadTapsDuringAWatchPagesLookupAskItsAdapterOnce`: two button taps during a gated watch-page lookup -> one adapter call, sheet opened twice. `BrowserViewModelTest`: the sheet waits on that same lookup and gets its video; a feed's second focused link to the same video takes the found result; Try again is a fresh ask; a new page forgets |
| Probes wait | `BrowserViewModelTest.genericProbesWaitForTheSiteLookupAndRunOnlyWhenItFoundNothing`: no HEAD while the site lookup runs, none after it found the video, probes after NO_MEDIA_FOUND |
| pageVideos | `MediaGroupsTest`: on an adapter site unnamed player files are never videos; main video = playing, else largest by size, height, length, earlier on tie. `QuickDownloadViewModelTest`: the sheet never offers an adapter site's unnamed files |
| Failure in sheet | `QuickDownloadViewModelTest`: a failed page lookup shows its message with Try again, which asks the browser for the same key; protected-only has no Try again. `QuickDownloadScreenTest`: "Looking up this video…", no Retry when it cannot help, "Other videos on this page (N)" row. `BrowserScreenTest`: spinner while the lookup runs, no badge/list on site pages, main video then list on request |
| Owner check | YouTube watch page and Facebook reel in the browser -> Download -> that video's sheet; never "Found on this page 4" |

### P13 — Wide Download button (OWNER CHECK, 2026-10-06)

| Check | Result |
| --- | --- |
| Visibility table | `BrowserDownloadFabTest.theWideButtonShowsWhereATapMeansOneVideoAndNothingCoversThePage`: site video page and generic one-video page -> wide; feed and several videos -> round; full screen, download sheet open, keyboard up, found list/address editing -> hidden |
| Screen | `BrowserScreenTest.theWideButtonSitsUnderThePageAndOnlyOneButtonShowsAtATime`: wide button "Download this video" calls the round button's action, round hidden, page bottom <= button top, hidden in full screen / under the sheet / with the found list; P12 one-video tests now use the wide button |
| Route | `BrowserRouteTest.twoDownloadTapsDuringAWatchPagesLookupAskItsAdapterOnce` taps the wide button on the watch page (it was the round one; the CI failure of `baf5124`) |
| Regression proof | `7953c0d` BrowserScreen.kt/BrowserDownloadFab.kt put back (test helper without the two new params): `theWideButtonSitsUnderThePageAndOnlyOneButtonShowsAtATime` fails "Assert failed: The component is not displayed!" at the first `browser-download-wide` check; the visibility test does not compile (no `wideVisible`). Restored, `cmp` clean |
| Validation | `:core-model:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin`: core-model 65, core-browser 81, app 628 (66 skipped), 0 failures; lint 0 errors; line check printed nothing |
| Owner check | YouTube watch page and Facebook reel -> wide Download button under the page -> the video's sheet |

### P19 — Real thumbnails (OWNER CHECK, 2026-10-06)

| Check | Result |
| --- | --- |
| Loader | `RemoteThumbnailLoaderTest` (real local TLS server): plain HTTP and non-addresses refused with no request; a client with a session sends no `Cookie`, `Accept: image/*`; a chunked answer past 2 MB stops the read and a stated length past 2 MB is refused, nothing decoded or cached; non-image and 404 answers refused; decode bound (`thumbnailSampleSize`: 480 → 1, 481 → 2, 1280 → 4, 3840 → 8; the decoder gets 480); a memory hit and a disk hit (new loader) make no second request, the disk file is named by SHA-256; the disk cache drops the oldest past its size; five loads -> at most two requests at a time |
| Picture choice | `ThumbnailUrlsTest`: `youtube:<11-char id>` -> `https://i.ytimg.com/vi/<id>/hqdefault.jpg`; other sites the lookup's HTTPS `thumbnailUrl`, HTTP ignored; YouTube's own picture wins |
| Sheet | `QuickDownloadViewModelTest.theHeaderNamesTheVideosPictureAndAStartedDownloadSavesIt`: lookup header has YouTube's picture before the lookup ends; Facebook-shaped group uses its HTTPS thumbnail, HTTP none; Download saves it for the started task. `QuickDownloadScreenTest.theHeaderShowsTheVideosPictureOnceItLoads`: `quick-thumbnail` is 16:9, placeholder first, the loaded picture after |
| Saved picture | `SavedDownloadThumbnailsTest`: saved as `<downloadId>.jpg` from the loaded picture, read back, found by the published file's URI, deleted when the record leaves the list; a picture that does not load saves nothing; unsafe IDs name no file; orphans older than the process start go, newer files stay. `DownloadsScreenTest.aRunningDownloadShowsThePictureSavedWhenItStartedUntilTheFileHasAFrame` |
| Regression proof | `7605133` QuickDownloadScreen.kt/DownloadsScreen.kt put back: `theHeaderShowsTheVideosPictureOnceItLoads` fails "The component is not displayed!" (no `quick-thumbnail`), `aRunningDownloadShows…` fails "Condition still not satisfied after 5000 ms" (no picture). Restored, `cmp` clean |
| Live | 2026-10-06, no session: YouTube 200 `i.ytimg.com` image/jpeg 32195 B; Vimeo 200 `i.vimeocdn.com` image/webp 25102 B; Facebook public reel 200 `scontent-iad3-1.xx.fbcdn.net` image/jpeg 223012 B |
| Validation | Full (Preview #2): `--continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug` — app 644 (66 skipped), core-browser 81, core-data 17, core-download 112, core-media 25, core-model 65, extractor-api 32, extractor-generic 14, extractor-sites 168: 1158 tests, 0 failures/errors; lint 0 errors; then `:app:assembleRelease`; line check printed nothing |
| Owner check | YouTube and Facebook sheets show the picture; the found list and a running download show it |

### P16 — Sheet opens at once (OWNER CHECK, 2026-10-06)

| Check | Result |
| --- | --- |
| Sheet states | `QuickDownloadViewModelTest.aSheetOpenedOnALinkShowsTheLinkThenItsRowsAndClosingItEarlyStopsTheLookup`: opened on Home's lookup over a page with a video of its own -> waiting header "youtube.com/watch?v=fixture0001", site, YouTube's picture, loading, no rows, Download off; the lookup answers -> "Ocean waves" with 720p selected; closing after that stops nothing, closing while it waits sends the key to `lookupCloses`. P12's failure/Try again test unchanged |
| Waiting UI | `QuickDownloadScreenTest.aWaitingSheetShowsTheLinkPlaceholderRowsAndGettingQualitiesThenItsRows`: the link, "Getting qualities…" (`quick-loading`), `quick-placeholder-audio`/`-video`, four `quick-placeholder-row`, `quick-download` shown but disabled, no `quick-rows`; then the real rows replace them and Download is enabled. Loading tests now expect the disabled Download and "Getting qualities…" (Plan adapted) |
| Home | `HomeViewModelTest.aSupportedSitesLinkOpensItsSheetBeforeTheLookupEndsAndTheLookupFillsIt`: the sheet request is sent while the lookup still runs (Searching), store lookup owned by HOME and `awaitsLookup`; the browser's clear leaves it; the answer selects the video and clears the lookup with no second request and one lookup. `aFailedLinkLookupShowsInTheSheetTryAgainAsksOnceMoreAndClosingStopsIt`: failure + Try again in the sheet, Try again asks once (not twice while running), closing cancels the lookup and returns to Editing, a protected answer has no Try again, closing after a failure keeps Home's message |
| Link | `HeadlessLinkInspectorTest.aSiteVideoLinkIsNamedBeforeAnyRequestAndAProtectedOneHasNoTryAgain`: adapter page -> `SiteVideoLink("fixture:42", …)` with no fetch or probe; other pages, direct media, HTTP and words -> null; DRM final failure `canRetry = false`, sign-in `true` |
| Feed | `BrowserViewModelTest.aFeedTapOpensTheSheetBeforeTheLookupEndsAndClosingItStopsThatLookup`: one sheet request while the adapter still answers, lookup running, closing cancels the adapter call and clears the lookup, the next tap opens and fills with one more call; `aFocusedNetworkFailureShowsInTheSheetWithTryAgainForTheSameVideo` (was `…OffersRetryForTheSameVideoAndKeepsDownload`): failure in the sheet, Try again asks once for the same video and fills it; `noVideoOnScreen…` and `aVideoPageOfAFeedSite…` now expect the sheet open at once (Plan adapted); `aFeedLinkToAVideoThatWasAlreadyFoundTakesThatLookup` still one adapter call (P12) |
| Regression proof | `03bde3a` `HomeViewModel.kt`, `BrowserViewModel.kt`, `QuickDownloadViewModel.kt` put back (new store/inspector kept): 7 tests fail — Home "expected:<1> but was:<0>" and NPE (no lookup), sheet header "Feed clip — 720p", feed tap/left feed/unreadable "expected:<1> but was:<0>", network failure NPE. Restored, `cmp` clean |
| Validation | `:core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` passed — core-browser 81, app 650 tests (66 skipped), 0 failures/errors; lint 0 errors (96 warnings, as before); line check printed nothing. |
| Owner check | Paste a YouTube link -> the sheet opens at once with the link and "Getting qualities…", then the rows; the same for a Facebook reel in the browser |

### P17 — Reuse lookup results (OWNER CHECK, 2026-10-06)

| Check | Result |
| --- | --- |
| One adapter call | `SiteLookupCacheTest.aSecondLookupOfOneVideoTakesTheFirstAnswerUnderItsOwnAddress`: two addresses of one video -> one adapter call, the second answer re-anchored to its own address with video ID `fixture:42`; another video asks again |
| Expiry | `anAnswerIsKeptUntilItsLinksExpireOrTenMinutesWhicheverComesFirst`: links expiring in 60 s -> asked again at 60 s; links lasting hours -> asked again at 10 minutes |
| Shared running lookup | `aLookupWhileTheFirstRunsWaitsForItAndTheLastToStopStopsIt`: three lookups while one runs -> one call; one waiter cancelled, the others still get the answer and the call is not cancelled; the only waiter cancelled -> the adapter call is cancelled and nothing is kept |
| Session apart | `answersReadWithTheSessionNeverReachALookupWithoutIt`: a session answer is not given to a lookup without the session; Plan adapted: a lookup with the session takes the public answer (cookies not passed on) |
| Try again, failures | `tryAgainAlwaysAsksTheSiteAndAFailureForgetsTheAnswer`: `fresh` asks the site; a failed Try again drops the old answer; failures are never kept |
| 403 / 410 | `aDownloadThatGets403Or410DropsItsVideosAnswer` (download service path: FAILED with ACCESS_DENIED/GONE or NEEDS_REFRESH drops; running, finished and NETWORK do not); `QuickDownloadViewModelTest.linksThatStoppedWorkingDropTheVideosRememberedLookup` (sheet: final check 404 keeps, 403 drops; engine Rejected GONE drops; a started download is remembered and its later failure drops) |
| Limit, clearing | `atMostTwentyVideosAreKeptAndClearingBrowsingDataDropsThemAll`: 21 videos -> the oldest asked again; `clear()` drops all; `SiteLookupKey.toString()` names no video. `PrivacyCleanersTest.sessionMediaCleaner…` clears the cache |
| Browser | `BrowserViewModelTest.aFeedLinkToAVideoThatWasAlreadyFoundTakesThatLookup`: a new page of the same video now takes the remembered answer (1 adapter call instead of 2; Plan adapted) |
| Regression proof | P17 `SiteAdapterCoordinator.inspect` without the cache (straight to the adapter), `QuickDownloadViewModel` without remember/forget and `SessionMediaCleaner` without clearing: 9 tests fail — all 7 `SiteLookupCacheTest` ("expected:<1> but was:<2>" etc.), the sheet's 403 test and the privacy cleaner test ("expected null"). Restored, `cmp` clean |
| Validation | `--continue :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` passed — core-browser 81, app 658 tests (66 skipped), 0 failures/errors; lint 0 errors (96 warnings, as before); line check printed nothing |
| Owner check | Open a YouTube video's sheet from Home, close it, open the same video in the browser -> Download -> the qualities are there at once |

### P18 — Download before qualities arrive (OWNER CHECK, 2026-10-06)

| Check | Result |
| --- | --- |
| Queued, exact pick | `QuickDownloadViewModelTest.aDownloadTappedWhileTheSheetWaitsStartsWithTheDefaultQualityOnceTheRowsCome`: waiting sheet on Home's lookup -> `waitsForQualities`, Download enabled, pick Video; tap -> `startsWhenReady`, Download off, nothing queued; the lookup answers -> 720p queued, 720p selected, note "Downloading 720p", status Queued |
| Lower, higher, own default, audio | `anEarlyDownloadTakesTheNearestLowerElseTheNearestHigherQualityOrTheAudio`: rows 1080/480/360 -> 480p "Downloading 480p — 720p not available"; only 1080 -> 1080p "… — 720p not available"; Default quality 480p (read while waiting) -> 480p "Downloading 480p"; Audio placeholder picked -> the M4A (audio track, Audio row selected) |
| Failure, closing | `aFailedLookupDropsTheEarlyDownloadAndClosingTheSheetCancelsIt`: a failed lookup shows its message, drops the queued Download and turns Download off; after Try again the rows come and nothing starts without a new tap; a sheet closed before the rows queues nothing |
| Checks still apply | `anEarlyDownloadStillPassesTheMobileDataWiFiOnlyAndStorageChecks`: mobile data asks at the tap (yes -> queued, the rows start it once; no -> dropped); Wi-Fi gone between tap and rows -> asked then, yes starts it; Wi-Fi only -> Queued waiting for Wi-Fi; the engine's storage refusal -> Rejected with its message |
| Sheet UI | `QuickDownloadScreenTest.aWaitingSheetOffersM4AOrTheDefaultQualityAndDownloadStartsWhenReady`: waiting rows `quick-early-audio` "M4A" and `quick-early-video` "1080p" (four `quick-placeholder-row` kept), picking Audio calls back, Download enabled and taps; queued -> "Starts when ready…" disabled; started -> `quick-download-note` "Downloading 480p — 720p not available" with the queued status. P16's waiting/loading tests now expect Download enabled (Plan adapted) |
| Regression proof | P18 `QuickDownloadUiState.canDownload` put back to the old rule (a row must be selected; Download disabled while waiting): 8 tests fail — the 4 new view-model tests (AssertionError, "List is empty", "expected:<ConfirmMetered> but was:<Idle>"), P16's link test, and 3 screen tests "(is enabled)". Restored, `cmp` clean |
| Validation | `--continue :core-download:testDebugUnitTest :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` passed — core-download 112, core-browser 81, app 663 tests (66 skipped), 0 failures/errors; lint 0 errors (96 warnings, as before); line check printed nothing |
| Owner check | Paste a link and tap Download at once -> the download starts with the right quality |

## Phase 12 (Preview #3 field fixes)

Plan: `docs/FIX_ADD_PLAN.md` (P20–P26). Three agents work at once; each adds rows only under its
own heading below. Owner's Preview #3 findings (2026-10-06): FIX_ADD_PLAN §2 and §4 (R1–R6).

### Agent A — P20, P21

| Check | Evidence |
| --- | --- |
| P20 fresh direct download into a destination whose length cannot be read before `prepare()` (a new MediaStore row) | `DirectTransferEngineTest` "a fresh download into a destination without a file until prepare completes" (no checkpoint and the queue's empty one); fails on `f434724`: `STORAGE_UNAVAILABLE` at 0 B |
| P20 resume whose length read fails | `DirectTransferEngineTest` "a resume whose length read fails starts again at byte 0 and completes" (asks `bytes=0-9`, `bytes=10-19`; first checkpoint 0 B); fails on `f434724`: `STORAGE_UNAVAILABLE` |
| P20 Android-like fake store (a new MediaStore row has no file until its first "rw" open) | `PublicDownloadDestinationTest` "a new MediaStore item has no file until prepare opens it for writing"; "a direct download into a new MediaStore item completes and is published" (real `MediaStoreDownloadDestination` + `DirectTransferEngine`, 4 ranges, bytes equal; fails on `f434724`) |
| P20 real MediaStore (CI emulator, API 34) | `MediaStoreDownloadInstrumentedTest`: (a) new row → `temporaryLength()` does not throw → `prepare(1 MiB)` → 4 out-of-order writes → `commit()` → 1 MiB, not pending, `Download/YFT/`, bytes equal; (b) real `DirectTransferEngine`, in-memory interceptor (200/206, no network) → 3 MiB in 4 ranges → `Completed`, bytes equal; (c) `discard()` removes the pending row |
| P20 other engines and destinations | HLS, DASH, merge (`AudioVideoMuxEngine`) and MP3 call `prepare()` before any destination read; a SAF temporary document and the app-private `.part` file exist from creation (no change needed) |
| P21 file write failure | `DirectTransferEngineTest` "a failed write is a storage failure of the write step with its detail" → `STORAGE_UNAVAILABLE` + `WRITE_FILE` + "IOException: EIO (I/O error)", 1 request; fails on `13b4576` (no stage) |
| P21 failed close after good writes | `DirectTransferEngineTest` "a failed close after good writes is a storage failure, not a network one" → `STORAGE_UNAVAILABLE` + `WRITE_FILE`, 1 request; fails on `13b4576`: the old engine retried it as a dropped connection and returned `Completed` |
| P21 dropped connection | `DirectTransferEngineTest` "a connection dropped during the body is a network failure of the read step" (`DISCONNECT_DURING_RESPONSE_BODY`) → `NETWORK` + `READ_SOURCE`, 3 attempts, detail without `http`, `127.0.0.1` or the host; fails on `13b4576` (no stage) |
| P21 source error counted as storage | `DirectTransferEngineTest` "an illegal state of the source is a network failure, not a storage one" → `NETWORK`, detail "IllegalStateException: closed"; fails on `13b4576`: `STORAGE_UNAVAILABLE` |
| P21 Retry after a storage failure | `DownloadQueueTest` "retry after a storage failure starts over in a new destination and completes" (failure with stage and detail stored; old destination renewed once; second transfer in the new one from byte 0; `COMPLETED`, failure cleared, new pending URI stored); fails on `13b4576` (no stored failure; the Retry ran in the old destination and failed again) |
| P21 other Retry paths | `DownloadQueueTest` "retry after a network failure resumes its checkpoint in the same destination" (from 4 B), "retry when the partial file is gone starts over at byte 0", "retry that cannot make a new destination stays failed with the new details" (`OPEN_FILE`, "IOException: EACCES (Permission denied)"); all fail on `13b4576` |
| P21 a new destination of the same kind | `PublicDownloadDestinationTest` "renew replaces the MediaStore row with a new empty one of the same name", "renew replaces the SAF temporary document with a new one of the same name", "a reopened destination is kept as it is and nothing is deleted", "a published destination is never renewed", "renew empties the partial file of an app storage download" |
| P21 MP3 stages | `Mp3ConvertingTransferDispatcherTest`: no sound and undecodable file → `INCOMPATIBLE_TRACKS` + `CONVERT`; the converter's message is kept; the download step's own stage is passed through; the first three fail on `13b4576` |
| P21 detail text | `DownloadFailureDetailsTest`: class and message, the first cause once, a coroutine copy once, links, IP addresses, host names, Bearer and key=value secrets and long tokens removed, a content URI named by its scheme, one line, at most 120 characters with "…" |
| P21 the record keeps stage and detail | `FailureDetailCodecTest` (stage, HTTP status and detail in one column; cleaned when stored and when read; unreadable values ignored); `RoomDownloadTaskStoreTest` (round trip through the record; details of another reason not stored); `AppDatabaseMigrationTest` "migrationFrom4To5PreservesRowsAndAddsNullableErrorDetail", "migrationFrom1To5ValidatesTheCompleteChain" |
| P21 Details dialog and Copy details | `DownloadsScreenTest` "failedTaskShowsItsDetailsAndCopiesThemWithoutLinks" (dialog shows reason, stage and destination; the clipboard text has no `http://`, `https://`, host or `content://`), "theMenuOfAFailedTaskOpensItsDetails" (both fail on `13b4576`: no Details), "onlyAFailedTaskOffersDetails"; `DownloadLabelsTest` (exact text; a task saved before P21 shows "—"); `DownloadsUiStateTest` "failedTaskKeepsItsFailureDetailsForTheDialog" |

### Agent B — P22, P23

| Check | Evidence |
| --- | --- |
| P22 visionOS asked again with the page's visitor data → every quality | `YouTubeExtractorTest` "visionOS asked again with the page's visitor data gives every quality with its size": bot check → watch page → visionOS again (visitor data in the client context and `X-Goog-Visitor-Id`, endpoint without the page key, no cookie) → rows 2160p, 1440p (VP9 + Opus), 1080p–144p (AVC + AAC) and Audio with YouTube's sizes, `itag 140` as M4A; `ANDROID` not asked; details hold no visitor data. Old code: `[360p]` only |
| P22 visionOS refused twice → the page client's ciphered ladder | "refused twice, visionOS leaves the ladder to the page client's script-signed formats": `player_cipher_ladder.json` signed by the solver (10 formats, `pot=` token), full ladder, `ANDROID` not asked, detail "10 formats via player script". Old code: `[360p]` only |
| P22 one row per quality | "a merged row takes a progressive file's place, never the other way round" (old code: `[360p]`); "merged rows with their audio track end the lookup before any other client" (old code: 360p from the embedded player added) |
| P22 Android's 360p file | "the Android app's 360p file is a row only when no client streams 360p separately": itag 18 without a size only when nothing else streams; detail "client ANDROID: 2 adaptive formats only through SABR" (old code: no such line, and the 360p row stayed itag 18) |
| P22 gates | "an age-restricted video still asks for a sign-in, and visionOS is not asked again" (guard, passes on old code too); "an age check from any client leaves the answer to the user's own session" (visionOS again and `ANDROID` last) |
| P22 order | "clients are asked in the order the owner chose": inline bot check → `VISIONOS, WEB_EMBEDDED_PLAYER, ANDROID`; refusal → `VISIONOS, VISIONOS, …`; desktop page → `…, MWEB (mobile site), ANDROID`; 15 more chain tests updated to the new order |
| P22 regression proof | Old `YouTubeExtractor.kt`, `YouTubePlayerResponseParser.kt` and `YouTubeClientProfile.kt` from `/data/bak/P22/orig` with the new tests: 22 of 52 `YouTubeExtractorTest` tests failed (the five above that are not guards and 17 order or detail tests); new files restored with `cp`, `cmp` equal |
| P22 validation (2026-10-06) | `:extractor-api:test` 32, `:extractor-sites:test` 185, `:app:testDebugUnitTest` 663 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings (four sequential Gradle invocations, `/data/tmp/validate-b.sh`); line check empty |
| P22 live (sandbox, 2026-10-06) | Adapter itself (temporary test outside the repo): `dQw4w9WgXcQ` → visionOS first complete, 9 rows 2160p–144p + Audio with sizes (360p 11.8 MB, 1080p 84.4 MB, M4A 3.4 MB); `4pKpLX9NG_k` → bot check from every client asked. Temporary script: `8Mw9bwLTQFk`, `jNQXAC9IVRw` → bot check from visionOS with and without visitor data. Data-centre IP flagged |
| P22 owner check | Pending: `4pKpLX9NG_k` in Home and the browser → 144p…1080p (+2K/4K) with sizes close to Snaptube's (360p ≈ 54 MB, 720p ≈ 124 MB, 1080p ≈ 380 MB), M4A ≈ 12 MB, 720p/1080p play with sound, Details screenshot |
| P23 share link → HD and SD files only → the reel's AVC ladder | `FacebookEveryQualityTest` "a share link to a page with HD and SD only gets the reel's AVC sizes and audio": session page `reel_hd_sd_only.html` (HD and SD files, no manifest) → desktop Safari without the cookie on the final `/reel/{id}/` (`reel_safari_ladder.html`) → rows 720p · HD, SD, 720p, 360p (AVC merged with the AAC track), Audio; 2 requests; media requests keep the browser's session. Old code: no ladder (asked only when tracks without AVC were listed) |
| P23 public reel with HD and SD only | `FacebookPublicPageTest` "a public reel with HD and SD files only goes on to the session page" (the public page is the whole lookup only with AVC video and an AAC track); "the public page's files are kept when the session page fails" (detail "…, so the public page's files are kept") |
| P23 AVC below the other sizes | "AVC at 360p below AV1 sizes asks the ladder, which adds AVC 720p once": `reel_avc360_av1.html` (AVC 360p, AV1 720p/1080p, AAC) → the ladder adds AVC 720p, its repeated 360p is not listed twice. Old code: no ladder (AVC was listed), 360p the only merged row; `FacebookDashManifestTest`: `needsAvcLadder` with whole-file sizes, `hasAvcWithSound`, `bestAvc` |
| P23 ladder address | "a watch link is asked for the ladder on the video page it redirected to" (`/watch/?v=` → `/{page}/videos/{id}/`, tracks merged without repeats); "a link that ends on a watch page asks the permalink the page states"; "a watch page without a permalink is never asked as Safari" (detail "ladder: skipped, no reel or video page to ask"); `FacebookUrlsTest` "the ladder page is the video's reel or videos page, never a watch page", "only a resolved reel is asked as Safari first" |
| P23 HD file and bitrates | "the HD file states the picture and sound of the track it was made from": HD → 1108×720, `avc1.64001f` + `mp4a.40.5`, 373 kbit/s from its address; SD without a manifest track → no picture or codecs (the resolver reads it, P3); merged 720p → the 312 kbit/s its video address states, never the manifest's peak. `FacebookUrlsTest` "a media address's own average bitrate is read, never anything else": `bitrate=`, `efg` Base64 JSON (padding, `+` as a space or `%2B`, URL-safe), implausible values, non-Base64 and overlong labels → none |
| P23 regression proof | Old `FacebookDashManifest.kt`, `FacebookDashOffers.kt`, `FacebookExtractor.kt`, `FacebookPageParser.kt`, `FacebookUrls.kt` from `/data/bak/P23/orig` with the new tests (`FacebookDashManifestTest` and `FacebookUrlsTest` held aside, they need the new helpers): 18 of 52 failed — the six `FacebookEveryQualityTest` tests, the two new public page tests and ten updated tests (requests and ladder address, HD file, bitrates); new files restored with `cp`, `cmp` equal |
| P23 validation (2026-10-06) | `:extractor-api:test` 32, `:extractor-sites:test` 196, `:app:testDebugUnitTest` 663 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings (`/data/tmp/validate-b.sh`); line check empty |
| P23 live (sandbox, no session, 2026-10-06) | Adapter itself (temporary test outside the repo): reel `1545617074260365` → 1 request, 720p · HD (1280×720, `avc1.64001f` + `mp4a.40.5`, est. 88.5 MB, CDN 88.9 MB), SD 31.8 MB, 720p 88.5 MB, 360p 34.4 MB, Audio 14.4 MB (60.6 kbit/s); `share/r/` → 1 request, AVC 360p/720p + Audio, "ladder: not needed (AVC 720)"; `/watch/?v=452499129200583` → 1 request, 5 rows; phone pages with AV1 720p/1080p or VP9 360p–1080p only → ladder GET 200 on `/reel/…` or `/NASA/videos/…` added AVC 360p/720p, 2 requests. `scripts/live-check.sh`: reel and share link 200, marker `browser_native_hd_url` |
| P23 owner check | Pending: reel `1545617074260365` (and the owner's share link) in Home and the browser → the same rows in both: 720p and 360p with sizes (720p ≈ 88.5 MB, 360p ≈ 34.4 MB), M4A ≈ 14 MB, MP3; 720p plays with sound; Details screenshot |

### Agent C — P24, P25

| Check | Evidence |
| --- | --- |
| P24 browser, owner's case | `BrowserViewModelTest.aVideoPageWithPreviewsAndAnAdOpensItsStreamByItsLengthWithTheRestCountedAsOthers`: an HLS master (720p first, 1080p) and its playlist (125 × 6 s + 4.5 s) fetched with the page as `Referer`, an ad clip asked for by an ad frame, 40 muted looping thumbnail previews (5–30 s) → both streams read their length (754.5 s), the playing `blob:` player of 754.5 s (beside a playing preview) opens the stream, 41 others; the timer path (no answer) opens the stream too; the master GET carried `Referer` and `Origin: https://videos.example.test` |
| P24 Home, owner's case | `HomeViewModelTest.aPageWithOneVideoAndItsPreviewsOpensThatVideoWithThePreviewsBehindIt`: a page whose player config names the master and 40 `data-mediabook` previews, scanned by `HtmlMediaScanner` → `Found(1)`, the sheet opens once with the master, `otherVideos` 40, View keeps 40 |
| P24 page signals | `HtmlMediaScannerTest.aVideoPageWithPreviewThumbnailsNamesItsStreamAsItsVideo` (fixture `core-browser/src/test/resources/fixtures/p24-preview-grid.html`: master MAIN, 40 PREVIEW, 41 candidates, a cap of 10 keeps the master first), `thePagesOwnWordsMarkItsVideoAndItsPreviews` (og:video, JSON-LD `contentUrl`/`embedUrl` with `PT12M5S`, `data-preview-url`, `data-teaser`, thumbnail `data-src`, muted loop `<video>`), `DomMediaProbeTest.parserMarksPreviewsAndThePagesNamedStream`, `BrowserObservationMapperTest.anAdsFileIsAPreviewByItsServerItsFolderOrTheFrameThatAskedForIt`, `CandidateNormalizerTest.aFileThePageNamesAsItsVideoStaysItsVideoWhereverElseItShows` |
| P24 ranking and found count | `MediaGroupsTest.aPagesPreviewsAreSetApartFromItsVideo`, `aPageBuiltPlayerIsMatchedByItsLengthElseByTheManifestItStartedWith` (±2 s, else the manifest loaded last before the player started), `withoutAMatchALongLengthBeatsASizeAndAPreviewNeverWins`, `aPageThatNamesItsVideoListsTheRestAsItsOtherVideosUnlessOneIsLong`, `theMainVideoIsThePlayingOneElseTheLargest` (height now before size); `PlayingVideoProbeTest.aPageBuiltPlayerIsReportedWithItsLengthPictureAndStartTime`, `aMutedLoopingThumbnailClipIsNotTakenWhileThePagesOwnVideoPlays` |
| P24 lengths | `ManifestReaderTest` (4: HLS master's tallest picture and first video playlist, finished/live media playlists, DASH `mediaPresentationDuration`, ISO lengths); `MediaMetadataProbeTest.aStreamsLengthIsReadFromItsMasterAndItsFirstPlaylistWithThePagesOrigin`; `DefaultVariantResolverTest.a pages HLS master is read with its Referer and Origin and states its length` (754 500 ms, 235 781 250 bytes ESTIMATED at 2.5 Mbit/s, `Origin` on both GETs and in the variants' context) |
| P24 honest failure | `QuickDownloadViewModelTest.aListOfQualitiesThatCannotBeReadIsNotANetworkProblem` (a parser exception → "The site's list of qualities could not be read."; a timeout stays "could not be reached"), `aRefusedVideoSaysSoAndItsDetailsNameTheStepTheHostAndTheStatus` (403 at the manifest, a `blob:` address never fetched, a 404 at Download with its Details), `QuickDownloadFailuresTest` (3), `DefaultVariantResolverTest.a failure names the step and the host it stopped at`, `a file server that answers HEAD with a web page is asked for the file itself`, `QuickDownloadScreenTest.aFailureShowsItsDetailsOnlyWhenAskedFor` |
| P24 lists UI | `BrowserScreenTest.aPagesPreviewsAreNotCountedAndFollowItsVideoUnderOtherVideos` ("Download video, 1 found", no badge, `found-other-videos` "Other videos on this page (3)"), `DetectedMediaScreenTest.aPagesPreviewsAreNotCountedAndFollowItsVideoUnderOtherVideos` (`detected-other-videos`), `QuickDownloadChoicesTest.aQualityPlaylistFoundBesideItsMasterIsNoRowOfItsOwn` |
| P24 regression proof | A copy of the old code (`8ef33fb`, `git archive`) with only the old-API tests added: 7 fail — the browser case (opened `previews/25.mp4`, the 30 s preview), Home (`Found(41)` instead of `Found(1)`), the choices ("Quality unknown" third row), the view model ("The media could not be reached…" for a parser exception), the resolver's HLS length (`null` instead of 754 500) and HEAD answered with a page (`text/html` instead of `video/mp4`), and the ranking (the large 360p file instead of the small 1080p one); the other 121 tests of those classes pass |
| P24 validation | `--continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL (5m 48s): 878 tests, 0 failures, 66 skipped (app 674, core-browser 88, core-media 28, core-model 69, extractor-generic 19; was 848); lint 0 errors, 95 warnings (unchanged) |
| P24 live check | coverr.co video page (public, no sign-in): Home path (first 2 MiB of 6.95 MB, as `HeadlessPageFetcher` reads) → 50 candidates (1 MAIN from og:video/JSON-LD, 24 thumbnail previews, 25 more), 1 video (cdn.coverr.co, 25 s) + 49 others; preparing it: HEAD went to the site's home page (`text/html`), the one-byte GET gave `video/mp4`, 13 631 866 bytes, 1080p. No HLS page with previews was reachable without sign-in or an age check |
| P24 owner check | The site from Preview #3: Home → that video's sheet with its real length and "Other videos on this page"; the browser's Download → the same; the download finishes; else a screenshot of the sheet's Details |
| P25 one sheet, four sites | `QuickDownloadChoicesTest.everySiteGivesTheSameSectionsNamesAndOrder`: YouTube ladder (4K to 144p), Facebook DASH + HD/SD, Facebook HD/SD alone, other site HLS + MP4 → Audio "M4A", "MP3 · 320/192/128 kbps"; Video by height ("1080p · Full HD", "720p · HD", "HD", "480p", "SD", "360p" for Facebook DASH + HD/SD); short view "M4A" + "MP3 · 128 kbps" and "720p · HD" + "480p" (Facebook HD/SD: "HD" + "SD"), the preselected row first; the other site's 720p is its whole MP4 |
| P25 descriptions | `QuickDownloadChoicesTest.everyRowSaysWhatItIsForInOneLine` (2160p to 144p, M4A, MP3, HD/SD as 720p/480p, "As the page plays it", a site's own MP3 file); `QuickDownloadScreenTest.everyRowShowsItsOneLineDescriptionAndDownloadStaysVisible` (native graphics, 360 × 780 dp: four `quick-row-description` lines shown, all rows' lines after More formats, Download shown) and `onASmallPhoneDownloadStaysVisibleWithTheDescriptions` (320 × 568 dp, YouTube ladder expanded and scrolled to 144p: Download and Details shown) |
| P25 names in place | `QuickDownloadChoicesTest.anHdOrSdRowIsRenamedInPlaceOnceItsPictureIsMeasured` ("HD", "SD" → "720p · HD", "360p": same IDs, ranks 720/480, short view, detail "1280 × 720 · 30 fps · MP4", the M4A sized from the measured 96 kbps); `QuickDownloadViewModelTest.aSitesHdAndSdFilesAreRenamedInPlaceOnceMeasuredAndOfferTheirSound` (the size check's heights rename the rows, the selection stays, Audio M4A + MP3) |
| P25 sizes | `QuickDownloadChoicesTest.sizesAreStatedElseEstimatedFromTheBitrateAndLengthElseUnknownAndNoRowIsDropped` (2 Mbit/s × 252 s → "~60 MB", a merge adds its 4 MiB sound, nothing known keeps the row with no size: "Size unknown"); `YftFoundMediaTest.aFoundFileReadsWithTheSheetsQualityNameAndSize` (found list: "MP4 · 720p · HD · 25 MB", a merge "~84 MB", 848 × 478 "480p" with "~14 MB", streams "Auto quality") |
| P25 audio of unstated codecs | `AudioFromVideoTest.onlyAnMp4WithAacSoundOrSoundOfAnUnstatedCodecQualifies` (no codecs or only the video's: offered, MP3 made from it; Opus, AC-3, E-AC-3, FLAC, MP3, Vorbis: not); `AndroidAudioExtractor` (read only) fails a file without AAC as `INCOMPATIBLE_TRACKS` — Downloads says "Failed · Incompatible tracks" (hand-off to Agent A for a clearer line) |
| P25 regression proof | A copy of the old code (`5f61e87`, `git archive`) with the new tests (the descriptions table left out: it needs the new field): all 8 fail — the four sites' table (YouTube's audio "M4A · 128 kbps"; Facebook HD/SD and the other site had no Audio), the rename (`[HD, SD]`), the sizes (`[null, null, null]`), the view model (`[HD, SD]`), both screen tests (no `quick-row-description`), the found list (`[MP4, 25 MB, 0:25]`), `AudioFromVideoTest` (no stated codec → not offered); 7 more fail on purpose (the "M4A" titles); the other 57 of the 72 pass |
| P25 validation | `--continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL (4m 13s): 886 tests, 0 failures, 66 skipped (app 682, core-browser 88, core-media 28, core-model 69, extractor-generic 19; was 878); lint 0 errors, 95 warnings (unchanged) |
| P25 owner check | YouTube, Facebook (Home and browser) and the other site → the same sheet: Audio M4A + MP3 · 128 kbps, Video 720p selected + 480p or 360p, More formats with a description on every row, a size or "~" estimate on every row |

### P26 — merge A → B → C and Agent C's hand-offs (integrator: Agent C)

| Check | Evidence |
| --- | --- |
| Merge | `git merge --no-ff` A (`c10d8c1`) → B (`8119f4e`) → C (`4d04e8b`) on `work/phase-12-integration`: no conflicts; only CHANGELOG, SESSION_STATE and TEST_MATRIX are changed by more than one agent |
| A HEAD answered by the file host's home page | `DirectRangeProbeTest` "a HEAD sent to a web page asks the file itself again with the range GET": HEAD `/videos/clip.mp4` → 302 `/` (`text/html`, ranges) → the range GET asks `/videos/clip.mp4` → `video/mp4`, 13 631 866 bytes, ranges, name `clip.mp4` (old code: stops at HEAD and takes the page `/`, 5 120 bytes, as the file) |
| Another host gets the page's origin | `DirectRangeProbeTest` "another host gets the observed page origin and an origin-only referrer" (same origin: full `Referer` and `Origin`; other host: `Origin` and `https://page.example.test:8443/`, no `Cookie`, no `Authorization`); "cross-origin redirect strips cookie and keeps only the page origin as referrer" (changed expectation: `Referer` was dropped) |
| Audio from a video whose sound is not AAC | `DownloadLabelsTest` "audioMadeFromAVideoWhoseSoundIsNotAacSaysToDownloadTheVideo": M4A and MP3 → "Failed · Sound can't be saved as audio", Details "What to do: … Download the video instead."; an MP4 keeps "Failed · Incompatible tracks" |
| Full validation (2026-10-06) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug` → 1292 tests, 0 failures, 66 skipped; lint 0 errors; `:app:assembleRelease` OK; line check clean |
| Owner check | Preview #4: FIX_ADD_PLAN §6 "Preview #4" items 1–7 |

## Phase 13 (Preview #4 polish)

Plan: `docs/FIX_ADD_PLAN.md` (P27–P33). Each agent fills only its own subsection; P33 adds the
summary row. Starting state (P26, `bc806f9`): 1292 tests, 0 failures, 66 skipped.

### Agent A — P27

| Check | Evidence |
| --- | --- |
| P27 progress after the download | `AudioVideoMuxMergeTest` "progress rises through the merge and the copy to 100 and never stays at 99" (merge 10 %…100 % from samples written / track time, then the copy's byte percent in order up to 100, ≥ 50 steps); fails on `7873d51`: no merge percent at all |
| P27 direct merge (API 26+, seekable read-write descriptor) | `AudioVideoMuxMergeTest` "a destination with a file descriptor gets the merge in one write, no second copy" (one descriptor merge, `open()` never called, length checked, committed); fails on `7873d51`: merged in app storage and copied |
| P27 today's path | "without a file descriptor the merge goes through app storage and is copied" (track files deleted before the copy); Android 7.x (`sdkInt = 25`) the same; 1 MiB copy buffer |
| P27 fallback | "an in-place merge that fails before its first sample falls back once" (descriptor, then file; log line "In-place merge failed before its first sample; merging in app storage"); "an in-place merge that fails after a sample does not fall back" (`Failure`, no second merge) |
| P27 space check | "the space check fails early with insufficient storage at the merge" (`INSUFFICIENT_STORAGE` + `MERGE`, "Merging needs … and … is free", destination untouched, no merge started) |
| P27 step times | "each step's time goes to the log and into a merge failure's details" (video, audio, merge, copy, sync, commit; "Merge done (in place|copy): …; <size>"; a failure's detail ends "took merge …") |
| P27 stop | "a stopped download stops the merge at its next sample" |
| P27 destinations | `PublicDownloadDestinationTest` "an app storage file gives an emptied descriptor and a store without one gives none" (written through the descriptor and committed; MediaStore with a store without descriptors → today's path); `AndroidPublicContentStore` gives a "rw" descriptor of a pending row or a SAF document only when it is seekable |
| P27 labels | `MergeStageLabelsTest`: "Merging audio and video · 45%" (not 99 %); "Merging audio and video" before the first sample (bar indeterminate); "Saving to Download/YFT · 80%", app storage and the chosen folder; downloading tracks, pausing and paused keep their usual line |
| P27 Downloads card and notification | `DownloadsScreenTest` "mergedTaskShowsItsMergeThenItsCopyWithTheirPercent" (testTag `download-stage-<id>`); `DownloadNotificationFactoryTest` "a merged download shows its merge and then its copy with their percent" (text and determinate progress) |
| P27 real MediaStore and MediaMuxer (CI emulator) | `MergeSpeedInstrumentedTest` "aDirectMergeIntoANewMediaStoreItemGivesAPlayableFileWithBothTracks" (in place, no copy, AVC 160×90 15 frames + AAC, `MediaMetadataRetriever` has video, audio and a frame); "aTwentyMinuteInputMergesInPlaceAndThroughAppStorageAndPrintsTheSplit" (20 min made by repeating the test tracks' fragments under one new `sidx`, `LongFragmentedMp4`; read pass, in place, today's path; `YFT-DIAG mux-timing` lines) |
| P27 measured split | CI emulator, API 34, `83478f0` (run 37563836036): 20 min input, 70 785 samples, 17 MB. Read once with `MediaExtractor` alone 2.1 s; in place merge 6.7 s, copy 0, commit 60 ms, total 6.8 s; today's path merge 7.1 s, copy 13 ms, sync 12 ms, commit 36 ms, total 7.2 s; 101 merge percents (0–100) each way. "Instrumentation results: tests=25 failures=0" |
| P27 regression proof | With `AudioVideoMuxEngine.kt`, `DownloadsUiState.kt`, `DownloadLabels.kt`, `DownloadsScreen.kt`, `DownloadNotificationFactory.kt` and `DownloadRuntimeModule.kt` from `7873d51`: all 8 `AudioVideoMuxMergeTest` tests and the 5 label, card and notification tests fail; restored with `cp`, checked with `cmp` |

### Agent B — P28, P29

| Check | Evidence |
| --- | --- |
| P28 owner's case on a fixture page | `PrerollFixtureTest` (`fixtures/p28-preroll.html`: JSON-LD `VideoObject` 16:24 naming an embed page, a 30 s 1080p ad in the player element, the HLS master loaded after it, preview clips): "the HTML states the video's length, title and picture" (no file from the embed page); "download during the ad opens the page's video with its title and picture" → the master (16:24) with the page's title and poster, the ad and previews under Other videos. Old code: the playing 0:30 ad |
| P28 page facts | `PageFactsReaderTest` (7): VideoObject length without a media file; `og:video:duration`, `video:duration`, `itemprop` forms; a related-videos list of different lengths states none; nested VideoObject name and picture; page title without the site's name; HTTPS pictures only; the live probe's parts give the same facts. `PageVideoFactsTest` (5): ±2 s match, 1 % over ten minutes, under half is far shorter, newer facts keep older ones, `toString` names no title or picture |
| P28 player setups | `PlayerSetupScannerTest` (9): JW Player sources with heights, single file and HLS source, KVS `flashvars` files and labels, a KVS address built by script is not chased, escaped quality lists in page data, previews/ads/long lists skipped, labels give a height only when they state one, "the scanner offers a video.js setup as one video with its qualities" (old code: two videos), a page without a setup keeps what it had |
| P28 page roles and main video | `MediaGroupsPageFactsTest` (10): the stated length beats the playing ad; without a stated length the playing element still wins; a playing element of the stated or unknown length is still the player; the page's named video first when no length matches; a file of the stated length is the video even when an ad list marked it; roles unchanged without a length; a site adapter's video never touched; only a far shorter or marked video may be the ad; a player setup's qualities are one video; the page's title and picture fill a video that names none |
| P28 ads | `AdBreakTest` (4): "the confirmed ad networks' files are ads" (ExoClick, `exosrv`, `magsrv`, `realsrv`, TrafficJunky, JuicyAds, `jads.co`, TrafficStars, `tsyndicate`, Adsterra; whole domains only — old code: not ads); an ad break is a VAST/VMAP document by a whole path part; the file fetched within 6 s after an ad break from another site is the ad; only the frame that asked (same `Referer` origin) fetches it |
| P28 live probe | `DomProbeFactsTest` (3, Robolectric): the probe's facts entry gives the page's facts; the player's setup becomes the page's video with its qualities; the probe script reads meta tags, JSON-LD and player scripts but no page text. `PageCandidateStoreTest` +1: the page's facts kept per page, a newer read keeps what an older one found, kept on an in-page move and cleared on a new page |
| P28 browser | `BrowserPageVideoWaitTest` (5, Robolectric): the sheet waits on the ad and switches to the page's video when its stream comes (16:24 from the manifest); nothing in 6 s → the ad with `maybeAd`; "download during the ad opens the page's video at once when it is known" (old code: the ad); closing the waiting sheet stops the wait; a page that states no length opens its only video at once |
| P28 sheet and Home | `QuickDownloadViewModelTest` +2: the waiting sheet shows the page's title and picture, then the ad with its line; a video without title or picture takes the page's. `QuickDownloadScreenTest` +1: "Finding the page's video…" then `quick-maybe-ad` with Other videos. `HomeViewModelTest` +1: a page that states its video opens its player's 480p/720p with the page's title and picture, the ad under Other videos, facts owned by Home |
| P28 instrumented (CI emulator) | `PrerollInstrumentedTest` (asset `browser-detection/p28-preroll.html`, offline: the test answers every request, the page itself at its own address, and checks first that the page, not the WebView's error page, loaded): the DOM probe reads 16:24, title and picture; the playing element reports the 0:30 ad; the main video is the HLS master with the page's title and picture, the ad under Other videos; after the ad the same element plays the page's stream and the choice stays |
| P28 regression proof | Old code (`679ec78`, `git archive` to `/data/tmp/p28-old`) with the new tests written against its API (the new ones need `PageVideoFacts`): 4 of 4 failed — `PrerollFixtureTest` download during the ad (the 0:30 ad instead of the master), `AdBreakTest` "the confirmed ad networks' files are ads" (`magsrv` not an ad), `PlayerSetupScannerTest` video.js setup (2 videos instead of 1), `BrowserPageVideoWaitTest` "download during the ad opens the page's video at once" (the ad); new files backed up in `/data/bak/P28` (38), `cmp` equal |
| P28 validation (2026-10-07) | Full validation (`--no-daemon --continue`, Agent B scope): 955 tests, 0 failures, 66 skipped (app 699, core-browser 114, core-media 28, core-model 95, extractor-generic 19; +50 from 905); `:app:lintDebug` 0 errors; `:app:compileDebugAndroidTestKotlin` OK; line check empty |
| P28 live check | Skipped: the owner's site and similar free video sites put an age gate in front of their pages, which YFT never automates (ADR-006); the fixtures copy the owner's Preview #4 case (stated 16:24, 0:30 pre-roll, HLS after it). The owner's check decides |
| P28 CI | `f334f55`: [checkpoint validation](https://github.com/Alalkipgen/YFT/actions/runs/37560950060) green, [Preview APK](https://github.com/Alalkipgen/YFT/actions/runs/37560950114) green, [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37560950074) red: 24 instrumented tests, 1 failure — `PrerollInstrumentedTest` "the stream was asked for". `32d2472` (P29, the test given the hook's reports): [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37563179756) red again — the same test, the stated length null. Cause, from the test's `YFT-DIAG p28-preroll` lines at `4f9447b` ([emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37567826899)): the WebView showed its own error page ("Webpage not available": 1 meta tag, no scripts, no JSON-LD), because the test answered every request with 404, the WebView's `data:` load of the `loadDataWithBaseURL` page included; the device itself reads the fixture's HTML as 16:24 (`html=984000`), so the app's code was right and both red runs had the same cause. Fix: the test loads the page's address and serves the page for it, and checks first that the fixture loaded. Fix `a47926d`: [checkpoint validation](https://github.com/Alalkipgen/YFT/actions/runs/37569543911) green, [Preview APK](https://github.com/Alalkipgen/YFT/actions/runs/37569543931) green, [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37569543932) green (24 instrumented tests, 0 failures); validation 962 tests, 0 failures, 66 skipped (unit tests unchanged), lint 0 errors, androidTest compiles, line check empty. Regression proof: the same test red on the emulator before the fix (`32d2472`, `4f9447b`), green after |
| P28 owner check | Pending: the site from Preview #4, Download while the ad plays → the page's title, picture and length with 480p/720p; the ad only under Other videos |
| P29 next video | `QuickDownloadViewModelTest` "a gone first file shows the page's next video with both attempts in Details": the first file answers HTTP 410 at the file check → the next video (5:00) is prepared and shown without an error, `nextVideo`, Details `First video / Step: file check / Host / Status: HTTP 410 / Next video / Host / Status: ready`, Download queues the next file (old code: the HTTP 410 error) |
| P29 both fail | "when the next video fails too, the failure's Details list both attempts": 410 then 404 → "The site no longer has this video (HTTP 404).", two requests only (the third video is not tried), Details list both (old code: the first only) |
| P29 never an ad | "an ad or a preview is never the next video": the page's other files are its ad (marked) and a thumbnail clip → the 410 error stays, one request (guard, passes on old code too) |
| P29 Try again | "Try again asks the store's newest address, not the dead one": the browser republished the video with a new signed query → Try again resolves the new address (old code: the dead one again). "Try again on a page Home found waits for Home's new read of the page" (the store's `readPageAgain`). `HomeViewModelTest` "the sheet's Try again reads the page Home found again quietly": `inspectAgain` of the same link, the page republished with its new address (owner Home), the Promptbox status unchanged; another page answers at once |
| P29 sheet | `QuickDownloadScreenTest` "the sheet says it shows the next video and its Details list both attempts": `quick-next-video` text, `quick-next-video-details` shows both attempts, rows and Download stay |
| P29 regression proof | P28 code `f334f55` with the new sheet tests (new-state asserts and the Home-read test held aside): 3 of 4 failed — next video (HTTP 410 error), both attempts (the next video never tried), Try again (the dead `token=old` address again); the ad guard passes. Backup `/data/bak/P29` (8 files), `cmp` equal |
| P29 validation (2026-10-07) | Full validation (`--no-daemon --continue`, Agent B scope): 962 tests, 0 failures, 66 skipped (app 706, core-browser 114, core-media 28, core-model 95, extractor-generic 19; +7 from P28's 955); lint 0 errors; androidTest compiles; line check empty |
| P29 live check | Skipped for the same reason as P28 (age gates; never automated); the tests copy the Preview #4 "HTTP 410" case |
| P29 CI | `32d2472`: [checkpoint validation](https://github.com/Alalkipgen/YFT/actions/runs/37563179633) green, [Preview APK](https://github.com/Alalkipgen/YFT/actions/runs/37563179635) green, [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37563179756) red only in P28's `PrerollInstrumentedTest` (see P28 CI); with the P28 test fix `a47926d`: [checkpoint validation](https://github.com/Alalkipgen/YFT/actions/runs/37569543911) green, [Preview APK](https://github.com/Alalkipgen/YFT/actions/runs/37569543931) green, [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37569543932) green (24 instrumented tests, 0 failures) |
| P29 owner check | Pending: the page that showed "HTTP 410" in Preview #4 → the sheet opens a working video (or the page's own after P28) without the error |

### Agent C — P30, P31, P32

Starting state (`7873d51`, `/data/tmp/validate-c.sh`): core-model 80, core-data 18,
core-browser 88, app 690 (66 skipped), 0 failures; lint 0 errors, 95 warnings.

| Check | Evidence |
| --- | --- |
| P30 search address | `BrowserSearchTest.theWebSearchIsGoogleWithEveryCharacterEncoded` (`cats & dogs at 50%` → `https://www.google.com/search?q=cats+%26+dogs+at+50%25`; `=`, `/`, `?` and Burmese words encoded), `searchAddressesEncodeTheWords`, `duckDuckGoAndBingSearchTheSameWords` (DuckDuckGo `https://duckduckgo.com/?q=…`, Bing `https://www.bing.com/search?q=…`), `theChosenEngineIsWhatTypedWordsSearch` |
| P30 browser | `BrowserRouteTest.typedWordsSearchGoogleByDefault` (address bar → Google address loaded), `typedWordsAndTheStartPageSearchTheEngineSettingsChose` (Bing in the settings → the address bar and the start page row load Bing); `BrowserScreenTest.wordsOfferYouTubeAndGoogleSearchRows` ("Search Google for “cat videos”"), `theWebSearchRowNamesAndSearchesTheChosenEngine`; `BrowserViewModelTest.wordsInTheAddressFieldSearchTheWebInsteadOfFailing` (expects Google now) |
| P30 setting | `BrowserPreferencesTest` (default Google, names), `DataStoreBrowserPreferencesRepositoryTest` "an older settings file without the engine reads Google", "every engine the user picks stays, and an unknown one reads Google"; `SettingsViewModelTest` "the search engine is Google until the user picks another"; `SettingsScreenTest` "the browser searches Google until another engine is picked in its dialog" (`settings-search-engine` → `search-engine-dialog` → `search-engine-DUCKDUCKGO`) |
| P30 regression proof | Old `BrowserSearch.kt`, `BrowserStartPage.kt`, `BrowserScreen.kt` (`/data/bak/P30/orig`) with the new tests (tests needing the new API held aside): 5 of 75 fail — the route's default search, the start page's Google row, both Google address tests and the view model's search address; new files restored with `cp`, `cmp` equal |
| P30 validation (2026-10-07) | `/data/tmp/validate-c.sh` → core-model 81, core-data 20, core-browser 88, app 698 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings (unchanged); `:app:compileDebugAndroidTestKotlin` OK; line check empty |
| P30 owner check | Pending: words in the address bar and on the start page → Google; Settings › Browser › Search engine → DuckDuckGo → the next search opens DuckDuckGo |
| P30 CI (`521b0b6`) | Checkpoint validation [37551748282](https://github.com/Alalkipgen/YFT/actions/runs/37551748282), emulator smoke [37551748382](https://github.com/Alalkipgen/YFT/actions/runs/37551748382), Preview APK [37551748312](https://github.com/Alalkipgen/YFT/actions/runs/37551748312): all green |
| P31 migration 5 → 6 | `AppDatabaseMigrationTest.migrationFrom5To6KeepsEveryDownloadRecordAndAddsAnEmptyBrowserHistory` (three records — completed, paused HLS with its checkpoint and two segments, failed with its code and detail — read back column by column; `browser_history` empty, then takes a row; schema validated against `6.json`), `migrationFrom1To6ValidatesTheCompleteChain` |
| P31 storage | `RoomBrowserHistoryRepositoryTest` (Robolectric, in-memory Room 6): `aPageOpenedAgainIsOneEntryWithAHigherCountAndTheNewTime` (fragment and `utm_source` → one entry, count 2), `pagesTheHistoryDoesNotKeepAreNotSaved`, `aPageWithoutItsOwnTitleIsListedByItsHostUntilItNamesItself` (no title or the address as title → host; rename keeps the count), `searchFindsTitlesHostsAndAddressesWithTheWordsTakenLiterally` (`0%_` matches only "100%_sure deal"), `oneDeleteRemovesOnePageAndClearRemovesAll`, `ninetyDaysAndFiveThousandPagesAreKeptTheOldestGoFirst` (5,001 rows + a 91-day-old one → 5,000 after the next visit, the oldest and the 91-day-old one gone) |
| P31 address rules | `BrowserHistoryAddressTest`: HTTPS only (not `about:blank`, `data:`, `http:`, `file:`, `javascript:`, `blob:`, blank, no host, over 4,096 characters); the fragment, `user:secret@`, port 443, `utm_*` (any case), `fbclid`, `gclid`, `dclid`, `msclkid`, `igshid`, `yclid` dropped, other parameters kept in order (`utm=1`, `gclid_note=2`); the host without `www.` |
| P31 what a visit is | `BrowserHistoryRecorderTest` (5): a finished page once per document, none after a main-frame error, a reload is a new visit; a redirect while loading is one visit of where it landed; a single-page site's own address change after it finished is a visit whose title follows (`onPageTitle`), the old page's title is not taken, a fragment or tracking parameter is the same page; titles before the finish or of another page rename nothing; `HistoryRecordingSink` passes every event on unchanged and in order. `SecureBrowserChromeClientTest.aPagesTitleReachesTheSinkWithItsAddress` |
| P31 switch and order | `BrowserHistoryViewModelTest` (3): visits kept while Save browser history is on, nothing new (visit or rename) after it is off; Recent is the newest six, the words filter the list; delete one, clear all. `DataStoreBrowserPreferencesRepositoryTest` "the history switch is on for an older file and stays as the user sets it"; `BrowserPreferencesTest` |
| P31 browser | `BrowserRouteTest.recentPagesAndTheHistoryListOpenTheirPageInTheBrowser` (start page `browser-recent-1` → the WebView loads it; `browser-menu` › `browser-menu-history` → `browser-history` → a row loads its page and the list closes), `backClosesTheHistoryBeforeItLeavesThePage`, `aPageTheWebViewFinishedIsSavedUnlessSavingIsTurnedOff` (the real WebView client's page callbacks → one entry without `utm_source`; switch off → nothing new, the detection still sees the page) |
| P31 list UI | `BrowserHistoryScreenTest` (5): Today / Yesterday / Earlier, newest first, "example.com · 15:00", "27 Sep", "7 Oct 2025"; a row opens its page, its menu deletes it, the search box filters and its clear button resets; Clear history asks first (Cancel keeps, Clear empties), the empty list says "Pages you open in the browser appear here.", "Saving history is off" note; the start page's Recent rows and History link and the toolbar menu; no Recent without pages |
| P31 settings | `SettingsScreenTest` "the browser history is saved until switched off and is cleared after a question" (`settings-save-history` on → `SetSaveHistory(false)`, `settings-clear-history` → confirmation → Confirm), "clearing browsing data says what goes" (now names the browser's history); `SettingsViewModelTest` "the browser history switch is kept and the history is cleared after the question"; `PrivacyCleanersTest.clearingBrowsingDataClearsTheBrowsersHistoryToo` |
| P31 regression proof | The new tests need the new API, so the behaviour was broken on purpose and restored: the WebView's sink back to the view model, tracking parameters kept, the switch ignored, the migration without its index, the count not raised → 8 tests fail (both migration tests, the tracking-parameter rule, the title and count tests, the recorder's single-page test, the switch test, the route's WebView-to-history test); files restored from `/data/bak/P31/new` with `cp`, `cmp` equal |
| P31 validation (2026-10-07) | `/data/tmp/validate-c.sh` → core-model 82, core-data 32, core-browser 89, app 717 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings (unchanged); `:app:compileDebugAndroidTestKotlin` OK; line check empty |
| P31 owner check | Pending: three sites → menu › History lists them newest first under Today; a tap opens one; Delete removes one; Clear history empties the list; Recent on the start page; Settings › Browser › Save browser history off → new pages are not added |
| P31 CI (`e9899f2`) | Checkpoint validation [37555932478](https://github.com/Alalkipgen/YFT/actions/runs/37555932478), emulator smoke [37555932456](https://github.com/Alalkipgen/YFT/actions/runs/37555932456): green. Preview APK [37555932462](https://github.com/Alalkipgen/YFT/actions/runs/37555932462) failed 21 s into its build step before Gradle compiled anything (runner); the same commit's `:app:assemblePreview` + `verify-release-apk.sh` passed locally (VERIFIED); rebuilt by P32's push |
| P32 policy | `AdRedirectPolicyTest` (5): the table — a tap to another site allowed; the page's own navigation to another site blocked (`NO_TAP`); same site and subdomains allowed; a server redirect allowed; a page that forwards while it opens allowed; a listed network blocked after a tap, as a subdomain, as a redirect hop and while the page opens (`AD_NETWORK`); `notpopads.net` not listed; no page yet, no host allowed; `.com.mm`, `.co.uk`, `github.io` sites told apart. Windows: only a tap to the same site opens here. Scripts: listed networks emptied, ExoClick kept. Every network has a reason, no host twice, 20–60 hosts |
| P32 guard | `BrowserNavigationGuardTest` (5, fake clock): loading < 8 s and < 1.5 s after finishing is forwarding, later it is blocked; the user's choice lets its page, redirects and listed hosts through until that page opened, at most 10 s; windows; switched off nothing is blocked |
| P32 WebView client | `SecureBrowserWebViewClientTest` (+5): without a guard the page's redirect loads as before; with it the page's own redirect to another site → `true`, one `BlockedNavigation`, the page stays, and with the switch off it loads; taps, the same site, server redirects and forwarding pages load; listed networks blocked after a tap, as a hop and as an app link's fallback; a listed script gets an empty answer off the main thread while the sink still sees every request, other scripts, ExoClick and the main frame are not intercepted, switched off nothing is |
| P32 new windows | `SecureBrowserChromeClientTest` (+3): without a guard a window opens in this tab once its hidden WebView gets a web address (`about:blank` ignored) and the hidden WebView is destroyed; with a guard a window to another site (tap) and a window without a tap are blocked, a tap's window to the same site opens here, switched off it opens here; HTTP and `market:` windows never open, a window without an address goes after 10 s, no message → `false`. `SecureWebViewPolicyTest`: multiple windows on, scripts still cannot open windows by themselves |
| P32 notice and browser | `BrowserBlockedNoticeTest` (4): "Blocked a redirect to win.other.test" and Open; "Pop-up blocked" stays 3.5 s and is gone after 4 s; the texts; the screen's sink passes every call on. `BrowserRouteTest.aBlockedPopUpShowsANoticeWhoseOpenLoadsItHereAndTheSwitchLetsItThrough` (the real route: a window → notice, the page stays; Open → loaded here; switch off → the next window opens here without a notice) |
| P32 settings | `SettingsScreenTest` "pop-ups and ad redirects are blocked until switched off" (`settings-block-popups` on → `SetBlockPopups(false)`), `SettingsViewModelTest` "the pop-up blocking switch is kept", `BrowserPreferencesTest` (default on), `DataStoreBrowserPreferencesRepositoryTest` "the pop-up switch is on for an older file and stays as the user sets it" (`browser_block_popups`) |
| P32 emulator | `PopupAndRedirectInstrumentedTest` (3, real WebView with the browser's policy, clients, guard and notice; inline fixture pages at `https://fixture.yft.test/watch`): a timer that sets `location` to another site → blocked, "Blocked a redirect to ads.other.test", the page stays and the WebView never asks for it; a tap (UiAutomator) whose handler calls `window.open()` → blocked window, "Pop-up blocked", the page stays; a tapped link to another site → the WebView asks for it, nothing blocked |
| P32 regression proof | Old behaviour put back in copies (navigations never blocked, scripts never emptied, windows always opened here, multiple windows off, the switch not stored) → 7 tests fail (the client's redirect, listed-network and listed-script tests; the chrome client's window test; the policy test; the DataStore switch test; the route's notice test); files restored from `/data/bak/P32/new` with `cp`, `cmp` equal |
| P32 validation (2026-10-07) | `/data/tmp/validate-c.sh` → core-model 83, core-data 33, core-browser 107, app 724 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings (unchanged); `:app:compileDebugAndroidTestKotlin` OK; line check empty |
| P32 owner check | Pending: on the sites where ads jumped to spam pages, taps no longer leave the page; "Pop-up blocked · Open" opens it when wanted; a normal link to another site opens; videos play and Download works; the switch off → as before |
| P32 CI (`d69811a`) | Checkpoint validation [37560496883](https://github.com/Alalkipgen/YFT/actions/runs/37560496883), emulator smoke with `PopupAndRedirectInstrumentedTest` [37560496916](https://github.com/Alalkipgen/YFT/actions/runs/37560496916), Preview APK [37560496886](https://github.com/Alalkipgen/YFT/actions/runs/37560496886): all green |

### P33 — merge A → B → C (integrator: Agent A)

| Check | Evidence |
| --- | --- |
| Merge | `git merge --no-ff` A (`89e985a`) → B (`0b1a11a`) → C (`81c3e97`) on `work/phase-13-integration`: no conflicts; only CHANGELOG, SESSION_STATE and TEST_MATRIX are changed by more than one agent; Room version 6; `BrowserViewModelTest` expects Google (C) |
| Full validation (2026-10-07) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:compileDebugAndroidTestKotlin` → 1435 tests, 0 failures, 66 skipped (app 746, core-browser 133, core-data 33, core-download 150, core-media 28, core-model 98, extractor-api 32, extractor-generic 19, extractor-sites 196; was 1292 at P26); lint 0 errors; `:app:assembleRelease` OK; line check clean |
| Emulator (B's detection with C's blocking on) | CI emulator smoke of `436aa90` (run 37575586251): "Instrumentation results: tests=29 failures=0" — `MergeSpeedInstrumentedTest` (P27), `PrerollInstrumentedTest` (P28) and `PopupAndRedirectInstrumentedTest` (P32) pass together; 0 FATAL EXCEPTION |
| Owner check | Preview #5: FIX_ADD_PLAN §6 "Preview #5" items 1–7 |

## Phase 14 (Preview #5 field fixes)

Plan: `docs/FIX_ADD_PLAN.md` (P34–P38). Each agent fills only its own subsection; P38 adds the
summary row. Starting state (P33, `436aa90`): 1435 tests, 0 failures, 66 skipped.

### Agent A — P34

| Check | Evidence |
| --- | --- |
| P34 | (Agent A fills this in.) |

### Agent B — P36, P37

| Check | Evidence |
| --- | --- |
| P36, P37 | (Agent B fills this in.) |

### Agent C — P35

| Check | Evidence |
| --- | --- |
| P35 stream copy (JVM, plain Kotlin) | `StreamCopyAudioVideoMuxerTest` (15), inputs built in the test (`Mp4TestFiles`), the output read back by an independent reader (`Mp4Dump`): "an MP4 video and an M4A sound are stream-copied, not merged by today's way" (fragmented, every `stts`/`ctts`/`stss`/`stsc`/`stsz`/`stco` entry matches every sample, bytes equal, `stsd` kept byte for byte, today's way never called); "a plain M4A with uneven samples gives tables that match every sample" (`stsz`/`stts`/`stsc`/`co64` input); "chunks of about a second alternate between video and sound in time order"; "past 4 GiB the chunk offsets go into co64 and the mdat gets a 64-bit size" (a virtual 4 GiB source, no real file); "edit lists keep both tracks where the inputs presented them"; "negative composition offsets are shifted into ctts version 0 and the edit list"; "a fragment whose tfdt leaves a gap lengthens the sample before it" |
| P35 not supported or failed → today's way once | "encrypted or odd tracks are not supported and today's way merges once" (`encv`, `pssh`, `senc`, `sinf`, two tracks in one file, `hvc1`, sound given as video, a broken `moof` size → the exact note each); "sample data outside an mdat is not supported"; "a failed check empties the output and today's way merges once" (file and descriptor); "a failure of today's way after the stream copy keeps both reasons" ("Muxer died; stream copy check failed, audio duration"); "a WebM merge goes straight to today's way" |
| P35 progress, stop, descriptor | "progress moves on by the bytes copied and ends at 100 percent"; "a stopped download stops the stream copy and leaves no output"; "the destination's descriptor gets the same file and stays open" |
| P35 log line (whole engine) | `StreamCopyMergeEngineTest` "the merge line names the stream copy, its samples, phases and CPU time": in place, "Merge done (in place): video …, merge 1.5 s, sync 1.5 s, commit 1.5 s; … KB; stream copy: 90 video + 129 audio samples, parse …, data …, index …, check …; cpu 400 ms, wall 1.5 s", no "/", "https" or "token"; P27's `AudioVideoMuxMergeTest` and `AudioVideoMuxEngineTest` unchanged and green |
| P35 CI emulator | `StreamCopyMergeInstrumentedTest` (3): the test tracks, P27's 20-minute input and a 1-hour-sized input (the test tracks' fragments repeated, 265,410 samples, 67 MB), merged by the stream copy (no note) and by today's way (`AndroidMp4AudioVideoMuxer(fastMerge = false)`, MediaMuxer). The inputs and both files read side by side with `MediaExtractor`: same tracks (MIME, size, sample rate, channels, `csd-0`/`csd-1`), same sample count, every sample's bytes equal in all three, sync flags equal, stream copy times = the inputs' within one tick, today's within one tick after each track's first sample; the stream copy plays in Media3 ExoPlayer (prepare, duration within 1 s of today's file, seek to the middle); ≥ 3× faster on the long input. P27's `MergeSpeedInstrumentedTest` and P4's `AudioVideoMuxerInstrumentedTest` now merge by the stream copy (the default) and still pass. "Instrumentation results: tests=32 failures=0 errors=0 skipped=0" (run 37801106973). ExoPlayer on the 1-hour-sized file: duration 4,500,121 ms (today's file 4,500,143 ms), seek to 2,250,060 ms; the video's first sample at 133,333 µs in the input and the stream copy, 192,777 µs in today's file |
| P35 measured times, before → after | CI emulator, API 34, `5f62962` (run 37801106973), `YFT-DIAG fast-merge`: 1-hour-sized input (265,410 samples, 67 MB): today's way (MediaMuxer with the quick wins) 14.7 s (open 174 ms, read 4.9 s, write 9.6 s, finish 45 ms; CPU 6.8 s) → stream copy 1.03 s (parse 430 ms, data 268 ms, index 92 ms, check 241 ms; CPU 811 ms), 14.3× faster. 20 minutes (70,785 samples, 17 MB): 3.74 s → 254 ms (14.7×). Through the engine (P27's `YFT-DIAG mux-timing`): in place merge 850 ms (before P35: 5.7 s, run 37790464540 on `9689062`), the Android 7.x path (merge in app storage, then copy) merge 704 ms (before: 5.9 s). Earlier runs of the same test: `ff59e2c` hour 16.0 s → 1.33 s (12.1×), 20 min 4.05 s → 0.42 s (9.7×); `0a18c19` 21.3 s → 1.84 s (11.6×), 6.5 s → 0.49 s (13.3×) |
| P35 regression proof | With `StreamCopyAudioVideoMuxer.mux` calling today's way at once (no fast path; backup `/data/bak/P35/`): 13 of the 16 new JVM tests fail — all of `StreamCopyAudioVideoMuxerTest` but "past 4 GiB …", "a WebM merge goes straight to today's way" and "a fragment whose tfdt leaves a gap …" (they test the writer, the WebM path and the reader on their own), and `StreamCopyMergeEngineTest`; restored with `cp`, checked with `cmp`. The instrumented test's JVM twins are these tests |
