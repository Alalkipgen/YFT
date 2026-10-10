# YFT Master Extractor backup

## R6 — Own YouTube module (YT-1 / YT-2 / YT-4)

- ✅ YT-1: main's five YouTube files copied whole into `extractor-master/.../modules/youtube/`
  (provenance header; only the package differs) with their five tests and test support
  (`Fixtures` reads main's fixtures via `yft.siteFixtures`). Whole-file rows (`*`) in
  `toolkit-provenance.tsv`: the drift check hashes each file after its package line on main
  and also fails when the Master copy itself was edited. 45 rows, 0 drift vs `34a41890` and
  `origin/main`.
- ✅ `modules/MasterSiteModule` + `SiteExtractorModule` (row check: HTTPS media and companion,
  no `sabr` parameter, not protected; rows tagged `site:contentId` like the app coordinator).
  `MasterYouTubeModule`: the copy with **no** player-script runner and **no** PoToken provider
  (n/sig streams are never offered by Master; main, flag off, still covers them), asked
  without the user's cookie/authorization header.
- ✅ YT-2: `MasterYouTubeClients` (table version 1, source "main 34a41890 / yt-dlp
  2026.08.19", visionOS first, no client needs a script or token). Canary:
  `MasterYouTubeCanaryTest` (skipped unless `-Pyft.youtubeCanary=<id>`) +
  `bash scripts/master-youtube-canary.sh <id>` (owner-run, never CI): passes while visionOS alone
  gives a complete answer ("visionOS first: complete, no watch page").
- ✅ YT-4: no SABR code; SABR-only and cipher fixtures give 0 rows.
- ✅ Engine slot: `MasterFallbackEngine(modules = …)`, stage `SITE_MODULE`. A claimed page never
  reaches layers, capture or probes; the module asks only when no site adapter answered
  (`primaryFailure == UNSUPPORTED_URL`), otherwise `Skipped(primaryFailure)` (P12, one lookup
  per video). Terminal rules and the off switch come first; a different requested video →
  `RESPONSE_CHANGED`. App: `AndroidBrowserMasterFallback.create` passes
  `MasterYouTubeModule(OkHttpExtractorClient(client))`; with main's adapter present YouTube
  watch pages are therefore never captured by Master.
- ✅ Tests: copied YouTube tests (87), `MasterYouTubeParityTest` (6: main vs Master on 20
  fixture scenarios — same requests, posts, headers, rows, order, labels, sizes and details;
  cipher/SABR → 0 rows; bot check → `BOT_CHECK` with 0 capture/probe; engine answers through
  the module only; table order), `MasterSiteModuleTest` (7). `extractor-sites` is a
  test-only dependency for the parity comparison. `:extractor-master:test` 242 tests,
  0 failures (2 skipped: canary, live smoke).

## R5 — E: codec steering

- ✅ `assets/yft-master-codecs.js` (document start, `addDocumentStartJavaScript`, opt-in builds
  only; nothing on WebViews without the feature): `MediaSource.isTypeSupported` (also
  `ManagedMediaSource`/`WebKitMediaSource`) and `mediaCapabilities.decodingInfo` answer only
  what `DeviceMergeSupport` merges — AVC/AAC always, VP9/Opus/Vorbis WebM from Android 10, AV1
  only while AV1 merges are on (off), HEVC/Dolby/E-AC-3 never, VP8 never. It only narrows (the
  browser is still asked), never touches DRM (`keySystemConfiguration`) queries, is off on
  YouTube hosts and can be disposed; unknown codecs pass through.
- ✅ `CodecSteering` (policy → script) and `WebViewPlaybackCapture(codecs = …)`; the app hook
  builds it with `AndroidBrowserMasterFallback.codecSteering(sdk)` from
  `AudioVideoMuxCompatibility` (same rules as `DeviceMergeSupport`). Main's Facebook Safari AVC
  agent stays the site-specific case (untouched).
- ✅ Tests: `codecs.test.cjs` (5: AVC/AAC on, AV1/HEVC/E-AC-3 off, VP9/Opus by Android
  version, never widens, decodingInfo + DRM pass-through, YouTube off/idempotent/dispose),
  `CodecSteeringTest` (3), app `BrowserMasterFallbackTest.codecSteeringFollowsTheDeviceMergeRules`.
  Spike CI runs both JS files. Live parity (AVC ladder where offered; 1440p/2160p kept with
  VP9) is owner-run on a phone.

## R4 — C: keyframe / duration fingerprint

- ✅ `verify/SegmentIndexReader`: DASH/ISO-BMFF `sidx` (v0/v1, media references only) and ended
  HLS `#EXTINF` playlists → length + keyframe cues (segment starts); HLS SAMPLE-AES/FairPlay
  keys → protected. `CapturedMp4Facts` uses it (same duration rule as before) and keeps cues.
- ✅ `verify/MediaFingerprint` (length + cues): `keyframesAgree` (≥ 90 % within 120 ms → yes,
  < 50 % → no, too few cues → cannot tell), `sameVideo` (agreeing cues and length within
  250 ms; 40 ms when both are a fixed grid), `otherVideo`. Length alone never groups.
- ✅ `CapturedMediaMetadata.inspect` returns the fingerprint from the same bounded reads (MP4
  prefix `sidx`; HLS playlist, then a master's first playlist only on the same origin). A
  protected HLS playlist sets `drmHint` → `DRM_PROTECTED`. `enrich` keeps its contract.
- ✅ `verify/FingerprintGroups` + `MasterMainSelection`: one video's qualities form main's group
  (one `MediaGroup` with several files, not extra "More" videos); a lone same-length file whose
  cues disagree with a main confirmed by two agreeing files is dropped (ad/related/preview);
  a second agreeing ladder or a file without cues stays its own group (before-R4 behaviour).
  No row is created or changed; ranking is unchanged inside each group.
- ✅ Tests: `SegmentIndexReaderTest` (7), `MediaFingerprintTest` (8, HLS fixtures under
  `src/test/resources/fingerprint/`), `CapturedMediaInspectTest` (5),
  `MasterFingerprintSelectionTest` (5). `:extractor-master:test` 141 tests, 0 failures.

## R3 — Toolkit + L3 shape search (content-ID anchoring)

- ✅ `toolkit/` copies (provenance header in each file, base main `34a41890`): `PageScripts`
  (T1: scripts by their own `id`/`type`, `var x = {…}`, JSON inside strings, `contextJSON`,
  `__additionalDataLoaded`), `BalancedJson` (T2: string-aware object/array reads, size cap,
  HTML-entity retry), `AnchoredMediaWalk` (T3: bounded walk carrying the nearest content ID),
  `MediaKeyTable` (T4 data: address/version/MIME/ID/hint keys, DRM statement, skip subtrees),
  `QualityLadder` (T7), `ProbeRounds` (T8), `RequestPolicy` (T9), `UrlPolicy`
  `PLAUSIBLE_EXPIRY_SECONDS` (T10). T7–T9 are ready for the per-site steps (R8); no
  behaviour uses them yet.
- ✅ `layers/ShapeLayer` (L3) runs last and only adds: a row an earlier layer found is dropped,
  earlier rows reach the normalizer unchanged, and it is skipped once a layer gave a terminal
  verdict. Never on a YouTube host, never inside `streamingData`/ad subtrees. A row is `MAIN`
  only when anchored to the page's content ID, `PREVIEW` for preview keys, otherwise no role
  (so a related video cannot become main). DRM statements (`drm`, licence objects, Widevine/
  FairPlay/PlayReady, FB `video_license_uri_map`) end in `DRM_PROTECTED`; a document's past
  `expires` drops its files at the gate.
- ✅ Drift check: `extractor-master/toolkit-provenance.tsv` (40 main symbols + SHA-256 at the
  base) and `scripts/master-toolkit-drift.py`; spike CI self-checks the base (must be 0) and
  reports changes on `origin/main` as warnings. Today: 0 drifted.
- ✅ Tests: `ShapeLayerTest` (shape ⊇ fixed-key on every committed fixture, renamed keys keep
  hits, related video rejected by anchoring, finders, DRM/expiry, all negative fixtures offer
  0, L3 only adds, YouTube/`streamingData` off), `ToolkitFindersTest`, `QualityLadderTest`,
  `ProbeRoundsTest`, `RequestPolicyTest`; `:extractor-master:test` 121 tests, 0 failures.
  Fixtures are read in place from `extractor-sites/src/test/resources/fixtures` (no copy, so
  no fixture drift) plus 3 new ones under `extractor-master/src/test/resources/shape/`.
- ✅ Fixture baseline rewritten on purpose (rows only grow): Facebook `changed_markup` 1→2,
  `watch_progressive` 3→6, `expired_links` 0→2 (expired, offered 0); Instagram `api_info_reel`
  2→7, `graphql_reel` 1→6, `page_signed_in` 3→8, `embed_captioned` 0→1 (heights gain 1920);
  Vimeo `player_config` 0→6, `player_page_inline` 0→2, `config_expired` 0→1 (expired),
  `config_drm` now ends `DRM_PROTECTED` (L3 reads `files.drm`; safety gain).

## R2 — Safety gaps (terminal walls, DRM stop, YouTube payload off)

- ✅ `policy/TerminalRules`: `BOT_CHECK`, `LOGIN_REQUIRED`, `PLAYER_SCRIPT_REQUIRED` are final on
  `youtube.com`, `youtu.be`, `youtube-nocookie.com`, `reddit.com`, `redd.it` and their
  subdomains (`blocksFallback(failure, pageUrl)`; host read as a browser reads it, so
  `youtube.com@evil.test` or `evil.test\@youtube.com` do not match). The engine checks
  `request.pageUrl`; the app hook checks the lookup link before any capture request and the
  tab page after it. Elsewhere the same failures still need the user's own playback.
- ✅ YouTube `streamingData` gives no Master rows (standard stack; never on a YouTube host even
  when opted in) until the R6 module. Its DRM statement still stops Master everywhere, with
  main's signals: `drmParams`, `playbackTracking.drmSessionId`, per-format `drmFamilies` /
  `drmTrackType` (main's `youtube/player_drm.json` now ends `DRM_PROTECTED`).
- ✅ EME stop: `yft-master-capture.js` marks the page protected when any visible video has
  media keys or an `encrypted` event (before: only the selected video). The session keeps it
  until navigation; the engine returns `DRM_PROTECTED` before any media check.
- ✅ Tests: `policy/SafetyGapsTest` (walled bot check → 0 capture, 0 probe; YouTube payload →
  0 rows, 0 probe; EME and stated DRM → `DRM_PROTECTED`, 0 probe), `TerminalRulesTest` host
  cases, `LayerStackTest` R2 cases, app `BrowserMasterFallbackTest.walledSites…` (0 request
  factory, 0 capture, 0 probe), 3 new JS capture tests. `MasterHardeningTest`'s companion
  budget fixture moved from YouTube JSON to an inline MPD (same assertions).
- ✅ Spike CI now also runs `node --test …/capture.test.cjs`.
- ✅ CI on `a2862abd`: spike `Master opt-in debug APK` run 38068967732 — success (capture JS
  tests, Master tests, opt-in APK; artifact `yft-master-optin-debug-apk`, 18.3 MB, 14 days).
  Flag-off `Work-branch checkpoint validation` run 38068970580 (PR #1) — success (artifact
  `yft-debug-apk`). Older runs on `314c97a`/`48915f3` are stale.

## R1 — Base sync + parity harness + canary

- ✅ Merged `origin/main` `34a41890` (61 commits since `a9eea7ba`). Conflicts only in
  `BrowserScreen.kt`, `BrowserViewModel.kt`, `docs/SESSION_STATE.md`: main's code kept, the
  Master hook re-applied around it. Main's P45 ("the tapped video only") removed the sheet's
  "Other videos on this page" row, so Master's other videos now stay in the browser's found
  list; `MasterMainMoreSheetTest` asserts exactly that (and flag-off = legacy).
- ✅ Frozen list: `app/src/androidTest/assets/parity/parity-urls.json` (32 cases from
  MASTER_KEY_PLAN §4.3; 14 public links; owner-picked slots stay `null`, never committed).
- ✅ Pure JVM harness `extractor-master/.../parity/`: `ParityUrls` (rejects http, user info,
  fragments, signed/session queries), `ParityReport` (host + content ID only; rows keep shape;
  titles hashed; fails closed on any `://`), `ParityVerdict` (§4.2 checks). Unit-tested,
  including a leak test with signed links, cookies and URL-bearing titles.
- ✅ Offline baseline: `FixtureBaselineTest` runs Master's stack over main's 84 site fixtures
  (`extractor-sites/src/test/resources/fixtures`, passed by Gradle) against
  `parity/fixture-baseline.json`: rows and heights may only grow, terminal verdicts must stay.
  A main sync that adds or removes fixtures fails until `-Pyft.parityBaseline=write`.
- ✅ Live: opt-in `MasterParityLiveTest` (`-e yft.parity 1`; arm A = main's adapters as the app
  wires them, arm B = Master in a visible WebView with real one-byte checks; attended mode
  waits for the owner's Play). `scripts/canary.sh` installs, runs and pulls the report. Not
  in CI. Spike CI now compiles `:app:compileDebugAndroidTestKotlin`.
- ✅ Local Gradle: `extractor-master` 101 tests (1 opt-in skip), Android module 45, app Master
  tests 23 (sheet 3, flow 9, fallback 11), androidTest compiles, 0 failures.
- ✅ The merge was made on GitHub through draft PR #1 (spike → main, "[DO NOT MERGE]"), used
  only for that merge: `314c97a` pre-merge → `48915f3` merge of main `34a4189` → `a2862ab` hook
  re-applied. PR #1 was closed unmerged after CI; main is untouched.
- ✅ CI on `a2862abd`: spike run 38068967732 and flag-off run 38068970580 both success (see R2).

## A0 — Architecture step A (structure only, same behavior)

- ✅ Map: [MASTER_KEY_ARCHITECTURE.md](MASTER_KEY_ARCHITECTURE.md). `extractor-master` now has
  `policy/`, `layers/` (L1 `CaptureLayer`, L2 `ContractLayer`, L4 `RecipeLayer`, L3 slot),
  `recipes/` (data-only key tables), `toolkit/`, `verify/`, `present/`, `capture/`; the engine
  is orchestration only.
- ✅ One stop-rule source: `policy/TerminalRules`. The app hook's own `NEVER_CAPTURE` copy is
  gone; `BrowserMasterFallback` calls `TerminalRules.blocksFallback`. Same sets as before.
- ✅ Android-free files moved to the JVM module: `CapturedMediaMetadata`, `CapturedMp4Facts`
  (`verify/`), `MasterMainPresentation` (`present/`). Public API names unchanged; only imports
  moved.
- ✅ Local JVM run: `extractor-master` 88 tests and 39 non-WebView Android-module tests, 0
  failures. Old-vs-new differential runs: reader 5,372 cases and engine 10,368 cases, 0
  differences.
- ✅ CI `master-optin-debug-apk` run `38064367003` on `fcceaec4` (final commit of the step):
  success — Master, Android-module and app Master tests plus `:app:assembleDebug`.
  Run: https://github.com/Alalkipgen/YFT/actions/runs/38064367003
  Artifact `yft-master-optin-debug-apk` (17,990,603 bytes, expires 2026-10-24):
  https://github.com/Alalkipgen/YFT/actions/runs/38064367003/artifacts/11674876484
  Pushed as `ef63a60a` (new layout; old paths as one-line placeholders) followed by 11
  placeholder deletions (the GitHub tool cannot delete in the same commit); the 11
  intermediate CI runs were cancelled by the workflow's concurrency group.
- No policy, site, Generic, model or download behavior changed. No main merge, release or tag.

## M2 — main/More sheet, expected TikTok More, opt-in CI APK

- ✅ TikTok More = 0 recorded as expected (single-video post page); no more alternative probes.
- ✅ `app/src/test/.../feature/browser/MasterMainMoreSheetTest.kt` (Robolectric, Compose, JVM).
  It drives the real `BrowserViewModel` hook path on a TikTok-shaped site page, the real
  `QuickDownloadViewModel`/`QuickDownloadRoute` sheet and the real `BrowserScreen` found list:
  - main + 7 More: the sheet shows only the main video; the existing More button
    "Other videos on this page (7)" closes the sheet and opens the found list with all seven.
  - main + 0 More: the main video and Download show; no More button exists.
  - flag off: the real factory with `false` is `BrowserMasterFallback.None`; selection, count and
    the full sheet state equal the pre-Master default (only the wall-clock resolve time is
    masked); no More button; a failing adapter gives the same lookup failure with no capture.
  - The main/More presentation is a fixture with the module's shape (public constructor, one
    group per verified file, owned UI keys, no post ID). Selection policy remains covered by the
    module tests. No production, model, Generic, extractor or download file changed.
- ✅ Local run of the exact CI test set with `-Pyft.masterCapture=true`: 18 suites, 153 tests,
  0 failures (`MasterMainMoreSheetTest` 3/3, `BrowserMasterFlowTest` 9, `BrowserMasterFallbackTest`
  10, `extractor-master-android` 54, `extractor-master` 77).
- CI: new spike-only workflow `.github/workflows/master-optin-debug-apk.yml` (existing workflows
  untouched) runs the Master tests and builds `:app:assembleDebug` with
  `-Pyft.masterCapture=true`, uploading artifact `yft-master-optin-debug-apk`.
  ✅ Run `37997610268` on `4ceab089`: success (tests and build steps all green).
  Run: https://github.com/Alalkipgen/YFT/actions/runs/37997610268
  Artifact `yft-master-optin-debug-apk` (zip, 17,969,814 bytes, expires 2026-10-23):
  https://github.com/Alalkipgen/YFT/actions/runs/37997610268/artifacts/11648655411
  APK SHA-256 is in the run summary. Debug key is the runner's throwaway key: uninstall an
  older `com.alal.yft.debug` signed by another key before installing.
- ⏳ Owner phone check of that APK is pending. No main merge, release or tag.

## Hooked selection and native live checkpoint

Dedicated, independently revertible hook/wiring commit:
`2b76e4d75136ff4a3e27f590e1e140a3570c9e6a`.
It contains exactly three production hook files plus two wiring/flag-off test files;
the selection/metadata/presentation algorithm remains entirely in the Android module.

| Hook file | Added | Removed |
| --- | ---: | ---: |
| `MasterFallbackEngine.kt` | 12 | 0 |
| `BrowserMasterFallback.kt` | 10 | 1 |
| `BrowserViewModel.kt` | 19 | 7 |

No existing identifiers were renamed or unrelated blocks reformatted. The ViewModel edits
are confined to existing selection call sites and opt-in presentation routing. App models,
Generic files, existing site extractors, download code and CI workflows are unchanged.

### Fresh checks

- ✅ Original seven selection contracts and five safety guards: all 12 pass.
- ✅ Combined policy/parser/transport-frame/opt-in/metadata unit suite: `OK (39 tests)`.
- ✅ Disabled policy skips hook/capture/probe for every primary failure enum. A disabled
  selector equals the legacy engine; a null hook retains legacy refusal and exact-address success.
- ✅ Real flag-off app factory returns the original `None`; every primary object passes through
  unchanged for both generic modes. `OK (1 test)`, isolated caller unit test with fail-fast
  dependency doubles, not an Android UI/device validation.
- ✅ JavaScript transport: 14/14; repository scripts: 30/30.
- ✅ Fragment-index/movie-extends duration and passive-result UI isolation were first run red,
  then fixed using actual container bytes and owned presentation keys respectively.
- ✅ TikTok focused DOM state at index 8 was actually omitted by the eight-script limit.
  The new regression failed first, then passed after prioritizing the two known delivered
  DOM-state nodes within the unchanged eight-node/eight-payload budget. No endpoint, URL,
  ID or playback evidence is invented. Projected bodies remain at most 64 KiB; refusal/DRM
  fields and all response-clone limits are preserved.
- ✅ Two audio-track/presentation tests failed first, then passed. Actual `hdlr=soun` with
  no video handler corrects a misleading video MIME; audio-only entries cannot become main
  or More. Missing decoded dimensions never reject a verified video; mixed tracks stay video.
- ✅ A slow-alternative test failed first, then passed. Android-owned validation has a 15-second
  sub-window inside the unchanged 20-second engine timeout, preserving an already verified main
  and eligible alternatives if a later check stalls. Caller cancellation, DRM refusal, probe
  budget and navigation-generation checks are not bypassed. Capture remains 2.5 seconds.

### Latest completed native diagnostics

These use actual desktop native Pause/Play, trusted click receipts, fresh production collector
frames, the hooked production selection policy, normal HTTPS validation and the unchanged
main/More presentation models. They do not prove Android APK sheet rendering.

- ✅ Instagram `Dcwk7e1yHaY`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  More list count 7. Native trusted click 1, capture 361.2 ms, authorized playback true.
  DOM duration 61,966 ms; independently read selected media duration 61,966 ms;
  selected dimensions 1080x1920. Nine validated inputs; one independently identified audio-only
  input excluded; eight video outputs. TLSv1.3; 2,363,904 bounded response bytes.
  An earlier retry exceeded the engine timeout; it is retained as a failure, not a pass.
- ✅ TikTok `7670337149526379789`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  no NEEDS_SELECTION. Latest repeat: native trusted click 1, capture 358.7 ms,
  authorized playback true. DOM duration 26,166 ms; independently read selected media
  duration 25,169 ms (within the exact 2-second tolerance); selected dimensions 720x1280.
  TLSv1.3; 461,139 bounded response bytes. The repeat uses actual browser-owned cookies
  scoped to real captured addresses, RAM/stdin only, matching existing Android CookieManager
  wiring. No cookie value, signed address or raw response is persisted.
- ✅ TikTok More count 0 is **expected** (owner decision): the public post page has one video.
  The accessible unrelated 2,067 ms file is correctly rejected; the four alternative probes that
  reported HTTP_STATUS are not pursued further and statuses were never overridden. No further
  TikTok alternative probing. One earlier retry blocked by a translation tip is not evidence.
- ✅ Fresh two-run Android gate for this changed tree passed (see below).
- ✅ Main/More sheet rendering is proven by a JVM Compose UI test (see M2 below). Live emulator
  sheet checks are intentionally skipped (no KVM); the owner checks the CI APK on a phone.

M2 status: see the M2 section below. Owner phone validation of the CI APK is pending.
No main merge, release/tag, full download/mux, all-resolution,
Master-CI-green or merge-readiness claim. Only the spike branch is used.

### Android environment recovery

- ✅ Official-index size/SHA-1 checks verified six SDK archives before extraction.
  JDK 17 is retained; API 35/build-tools 35.0.0, platform-tools 37.0.1, emulator exactly
  37.2.12.0 and API29/default/x86_64 image revision 8 are restored. Runtime libraries restored.
- ✅ Emulator package was registered through the official stable-channel SDK manager after
  manual extraction alone failed AVD-manager preflight. Strict yft-master29 AVD recreated:
  480x854, density 240, RAM 1536 MiB, 2 cores. Recovered focus guard/runner is not weakened.
- ✅ The sandbox was later wiped; the spike branch was re-cloned at `def91b91` (production code
  identical to `005fd0f6`), and the same verified SDK packages, Corretto JDK 17 (SHA-256 checked),
  emulator 37.2.12 and strict AVD were restored again.
- ✅ Fresh internal app/androidTest pair built from `def91b91` with `-Pyft.masterCapture=true`
  and `-Pandroid.injected.build.abi=x86_64`: BUILD SUCCESSFUL.
  App SHA-256 `fee5c66f7cedc231f8c69c28ba13a0ecc713e2b89f748d50d2a712b762630e90`;
  test SHA-256 `9c6d8f88f178fbc0efa10640f4a932c21d93d4d04d18c233258a8141b5c106aa`;
  both signed by the same debug certificate
  `ef6a9e9817aba79e5416d41ede1e183e2d3d1ace6c6c094b333f43ddc72b9609`.
- ✅ Emulator cold boot completed (boot_completed=1). A SystemUI ANR dialog was cleared with its
  native Wait button only; focus then Launcher, device-side screenshot 192,040 bytes.
- ✅ Strict gate `gate-def91b91`: two consecutive full-class
  `MasterCaptureInstrumentedTest` runs, each `OK (4 tests)` (172 s and 140 s), identical
  head/APK/test/certificate/harness hashes, installed hashes verified. Focus guard not weakened.
- ❌ Android main-sheet/More rendering is not covered by this gate and remains unproved.
  No owner phone delivery, release, tag or main merge.

### Secret-safe latest native log excerpts

```json
{"site":"tiktok","result":"SUCCESS","stage":"PLAYBACK_CAPTURE","nativeTrustedClicks":1,"captureMs":358.7,"authorizedPlayback":true,"mainSelected":true,"moreCount":0,"domDurationMillis":26166,"selectedDurationMillis":25169,"androidAppLiveProved":false}
{"site":"instagram","result":"SUCCESS","stage":"PLAYBACK_CAPTURE","nativeTrustedClicks":1,"captureMs":361.2,"authorizedPlayback":true,"mainSelected":true,"moreCount":7,"domDurationMillis":61966,"selectedDurationMillis":61966,"androidAppLiveProved":false}
```

## Authorized Android-owned policy checkpoint

The user authorized minimal hook/wiring edits in the three files listed below, in a separate
revertible commit. The selection algorithm, Generic ad-rule reuse, bounded independent MP4
metadata reads and main/More presentation mapping are implemented only in
`extractor-master-android`. Module policy/parser/frame tests: `OK (13 tests)`; JS: 11/11.
The original seven red contracts remain red until the separate hook commit is applied.

The projection keeps original source identities in the memory-only capture record. Only UI
copies use reserved presentation keys and no claimed post ID, so alternatives are not falsely
declared qualities of one post. The existing MediaCandidate/MediaGroup/otherVideos app models
are unchanged. Duration is read from delivered metadata or independently validated MP4 bytes,
never copied from the DOM. Default/release flags and all original security guards are unchanged.
Live main/More and full Android UI validation are still pending.

## Automatic main/More selection — test-first checkpoint

Date: 2026-10-09. Baseline: `ac9695ce6758fbcace6ea1381f185a9e6b5a8278`.
Branch: `spike/master-extractor-backup` only. No merge, release, tag or phone APK delivery.

### Status

- ✅ A compile-valid failing contract was written and executed before any production fix.
- ✅ 12 offline JVM tests ran: 7 expected selection failures, 5 safety tests passed.
- ✅ All seven failures report `NeedsSelection`, not compilation or fixture setup errors.
- ❌ Automatic TikTok/Instagram main selection is not implemented or live-verified.
- ❌ Main sheet plus More integration is not complete.
- ⏸ Production changes await clarification of the module-only boundary described below.

The only new code is
`extractor-master-android/src/test/kotlin/com/alal/yft/extractor/master/android/MasterMainSelectionContractTest.kt`.
This progress document is the explicitly requested documentation exception.
No unused production selector, fake HTTPS playing URL, fabricated MAIN role or guessed
content identity was added.

### Red contract

The test exercises the existing bounded frame reader, browser session and Master engine.
Independent validator fixtures supply candidate duration/dimensions; the DOM duration is
never copied onto candidates. Instagram uses the generic/no-ID request shape. TikTok keeps
its requested content ID instead of deleting it to force a result.

The seven deliberately failing behaviors are:

1. Known DOM duration: independent candidate duration must be within the inclusive 2-second
   tolerance; a candidate 2.001 seconds away must not qualify.
2. TikTok: a different-duration file must not receive the player's duration.
3. A missing candidate duration must not be treated as a known-duration match.
4. Closest decoded dimensions rank matching candidates; smaller matching files remain for More.
5. Equal candidates choose a stable main and retain the other eligible candidate for More.
6. Unknown DOM duration chooses the longest non-ad candidate and retains alternatives.
7. Ad-domain and far-shorter-duration candidates must not become main or More.

Passing guard cases: paused playback, only one progress sample, DRM evidence, stale navigation
and the disabled default policy. These are explicitly offline unit fixtures, not native
Android gesture evidence, live media validation, or a promise that the sheet already works.
No JS play/seek is used. No live-manifest or protected-media refusal is overridden.

Execution: JDK 17.0.20.1, Kotlin 2.0.21/JVM target 17, JUnit 4.13.2.
The standalone runner compiled 50 unchanged production source files and this one new test.
It did not run Gradle/Android instrumentation or the full application regression suite.
Equivalent module test command, once the Android toolchain is available:

```sh
./gradlew :extractor-master-android:testDebugUnitTest \
  --tests com.alal.yft.extractor.master.android.MasterMainSelectionContractTest
```

JUnit result: `Tests run: 12, Failures: 7`; exit 1 is intentional for this red checkpoint.
Do not describe this checkpoint as green CI or as the completed fix.

Fresh ancillary checks: JS transport 11/11 and repository script tests 30/30 passed.
Exact two-file allowlist, Kotlin lines at most 100 characters and the local equivalent
secret/sensitive-path scan passed. The 50 compiled production-source hashes and the capture
JS hash match the unchanged baseline. No GitHub Advanced Security scan or Android build is
claimed by these local checks.

### Confirmed production boundary

The requested full behavior cannot currently be wired through the Android capture module's
public contract alone:

- `CaptureFrame.kt` does not retain the player's duration or decoded dimensions yet. This
  part can be changed inside the allowed module.
- `WebViewPlaybackCapture` returns only `PageSnapshot`. That contract has no explicit selected
  main/More result or duration/dimension selection policy.
- `MasterFallbackEngine.kt` owns a private `select`. It runs before media validation, so
  independently probed candidate durations never reach selection when the player uses a blob
  URL. It accepts an exact HTTPS playing-address match or one explicit MAIN group; otherwise
  it produces `NeedsSelection`. There is no injected selection hook.
- The engine probes and returns only selected candidates; unselected alternatives are not
  passed through as More.
- `BrowserMasterFallback.kt` applies the caller's content-identity filter.
- `BrowserViewModel.kt`'s generic Master path requires `groups.singleOrNull()` and selects the
  group without an other-video count. Returning several legitimate video groups alone does
  not implement the requested main/More sheet.

Rewriting a blob address as a supposedly observed HTTPS playing URL, inventing page-owned
MAIN evidence or assigning different posts one fabricated identity would bypass these checks
instead of implementing the requested policy. Those workarounds were not used.

The smallest proposed scope clarification is permission for selection-hook/result-routing
edits in these three files, with the selection algorithm and new capture facts remaining in
`extractor-master-android`:

- `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/MasterFallbackEngine.kt`
- `app/src/main/java/com/alal/yft/detection/master/BrowserMasterFallback.kt`
- `app/src/main/java/com/alal/yft/feature/browser/BrowserViewModel.kt`

Existing reusable Generic-workflow rules were located: `BrowserObservationMapper.adRole`,
`MediaGroups.isPreviewAddress` and `PageVideoFacts.isFarShorter`. Reuse them without changing
their files. The requested duration-match tolerance remains exactly 2 seconds, rather than
the Generic long-video percentage tolerance.

### Live acceptance is still pending

No new live test is presented as a fix validation in this checkpoint. The latest previously
recorded native public-player diagnostics remain:

- ❌ Instagram: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.
- ❌ TikTok: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.

The prior two-run Android gate passed with the same old APK pair. It does not validate a
future changed implementation. Default/release opt-out, 2.5-second capture timeout, independent
playback-progress authorization, DRM rejection and navigation-generation checks are unchanged.
Existing site extractors, Generic files, download code and CI workflows are untouched.

Next, after the boundary is clarified: make the red contracts pass through real production
wiring, rerun relevant guards/device checks, then perform native TikTok and Instagram live
validation. Completion requires logs showing automatic main selection without
`NEEDS_SELECTION` and actual main/More routing. TLS/media-byte, full download/mux, all
resolutions, Master CI green and merge readiness remain unverified.