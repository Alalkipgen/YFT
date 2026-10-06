# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 12 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P26.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P26 only)

- Phase: 12 — Preview #3 field fixes (saving, every quality, one sheet). Plan:
  `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-12-integration` = `main` `4db6c2b` + the plan commit. Agent A
  `work/phase-12-download-fix` (P20, P21, later P26), Agent B `work/phase-12-site-qualities`
  (P22, P23), Agent C `work/phase-12-generic-sheet` (P24, P25); all start from
  `origin/work/phase-12-integration`. Merge order A → B → C (P26), then Preview #4; P8 (signed
  `1.0.0-beta.4`) only with the owner's OK. Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #3 (2026-10-06, run 37455870506, `4db6c2b`): video downloads fail
  "Storage unavailable" at 0 B (Retry too), YouTube only 360p, Facebook HD/SD without Audio
  (Home) or 360p only (browser), another site opens a 29 s preview and says "50 media found",
  the sheet differs per site. Root causes R1–R6 in FIX_ADD_PLAN §4.
- Rules: ADR-006 public videos only; no DRM/paywall/private/age-gate bypass; adapters never sign
  in. Never print/commit cookies, tokens, visitor data, signed media/image URLs or keys. Keep
  testTags, Kotlin lines ≤ 100, WebView on the main thread. No reset --hard/clean/stash. One
  Gradle command at a time; temporary files outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK `/data/toolchains/android-sdk`
  (platform 35, NDK 27.3.13750724, CMake 3.22.1); `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with the deploy key
  configured in `core.sshCommand`; never print it.
- Phase 11 record (per-task Results, validation, CI runs, Track A/B notes):
  `git show 4db6c2b:docs/SESSION_STATE.md`.
- Last pushed checkpoint: PLAN — prompts: own folder per agent, push access, no killing another
  agent's Gradle (this commit); plan `b097094`.
- Next: the owner pastes the prompts for Agents A, B and C (three chats, at the same time).
- Last updated: 2026-10-06 (Phase 12 plan, Plan Mode; no app code changed)

## Agent A — `work/phase-12-download-fix` (P20, P21; later P26)

- Status: NOT STARTED
- P20 — Video downloads save again: TODO
- P21 — Retry and failure details: TODO
- Hand-offs: none

## Agent B — `work/phase-12-site-qualities` (P22, P23)

- Status: NOT STARTED
- P22 — YouTube: every quality: TODO
- P23 — Facebook: every quality: TODO
- Hand-offs: none

## Agent C — `work/phase-12-generic-sheet` (P24, P25)

- Status: P24 OWNER CHECK (2026-10-06); P25 IN PROGRESS (base commit `f434724` =
  `origin/work/phase-12-integration`; folder `/data/YFT-C`)
- Environment (Notion sandbox, fresh after a reset): JDK 17 `/data/toolchains/jdk17`, SDK
  `/data/toolchains/android-sdk` (platform 35, build-tools 35.0.0, NDK 27.3.13750724, CMake
  3.22.1), `GRADLE_USER_HOME=/data/gradle-home` with `org.gradle.jvmargs=-Xmx1536m
  -XX:MaxMetaspaceSize=640m`, a 4 GiB swap file against out-of-memory kills.
- Starting state (before any edit, `f434724`): Agent C scope validation `./gradlew --no-daemon
  --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest
  :extractor-generic:test :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL (9 m 48 s):
  848 tests, 0 failures, 66 skipped (app 663, core-browser 81, core-media 25, core-model 65,
  extractor-generic 14); lint 0 errors, 95 warnings.
- P24 — Other sites: main video: OWNER CHECK (2026-10-06)
  - Result: on a site without an adapter, Home and the browser open the page's own video, not a
    preview or an ad. core-model: `MediaCandidate.pageRole` (`PageMediaRole` MAIN/PREVIEW,
    default null), `PlayingVideo`, `MediaGroups.ofPage`/`PageVideoList`/`looksLikePreview`,
    `mainVideo(videos, PlayingVideo?)` (its address, else its length ±2 s, else the manifest a
    page-built player started with; without a match: not a preview > a length of a minute or
    more > picture height > stated size > length > earlier), `ResolutionStep` and
    `VariantResolutionResult.Failure.step`/`host` (defaults null). extractor-generic:
    `ManifestReader` (HLS/DASH length, tallest picture, first video playlist); the normalizer
    keeps the page's word. core-browser: `PlayingVideoProbe` reports every playing element
    (length, position, picture, muted/loop/autoplay, frame, thumbnail box); `DomMediaProbe`
    flags; `HtmlMediaScanner` page signals (JSON-LD `VideoObject` with its `duration`,
    `og:video`, `twitter:player:stream`, a manifest in the page's scripts → MAIN; thumbnails'
    `data-preview…`/`data-mediabook`/`data-src` and muted loops → PREVIEW; the cap keeps MAIN
    first); an ad's file by its host, its folder or the ad frame's `Referer`;
    `MediaMetadataProbe.readManifest`. core-media: the resolver reads an HLS master's length
    from its first video playlist (sizes estimated from the bitrate), sends the page's `Origin`
    with its `Referer` and keeps it in the variants' context, names the step and host of a
    failure, asks a file itself again when HEAD gave a web page, and calls an address that only
    gives a web page INVALID_URL. app: the browser reads up to 8 manifests a page for their
    lengths; the found lists, Home's count and the Download button's label count
    `ofPage(…).videos`, the rest under "Other videos on this page (N)" (`found-other-videos`,
    `detected-other-videos`); Home with one video opens its sheet with the others counted;
    `QuickDownloadFailures` (messages by status and reason, Details "Step / Host / Status",
    `quick-error-details`); a master's quality playlist found beside it is no extra row.
  - Plan adapted: (1) MP4 lengths come from the page's `<video>` elements (DOM probe); the
    metadata probe stays header-only for files, and streams' lengths come from their manifests
    (browser: `MediaMetadataProbe.readManifest`; sheet: the resolver). (2) The script can read
    only the page's own frames, so a framed element only ranks after the page's own; third-party
    ad frames are recognised by their files' host, folder or the frame's `Referer` instead.
    (3) The live check showed a page that names its video while listing other videos' files of
    unknown length: those follow under "Other videos" unless one is known to be a minute or
    more long, and its file host sent HEAD to its home page (resolver fix, both tested).
  - Tests: 30 new tests and one changed expectation (TEST_MATRIX "Agent C — P24, P25");
    fixture `core-browser/src/test/resources/fixtures/p24-preview-grid.html`.
  - Regression proof: a copy of the old code (`git archive 8ef33fb`) with only the old-API tests
    added → 7 fail: `BrowserViewModelTest.aVideoPageWithPreviewsAndAnAdOpens…` (opened
    `previews/25.mp4`, the 30 s preview), `HomeViewModelTest.aPageWithOneVideoAndItsPreviews…`
    (`Found(41)`), `QuickDownloadChoicesTest.aQualityPlaylistFoundBesideItsMasterIsNoRowOfItsOwn`
    ("Quality unknown" row), `QuickDownloadViewModelTest.aListOfQualitiesThatCannotBeRead…`
    ("The media could not be reached…"), `DefaultVariantResolverTest` HLS length (`null`) and
    HEAD answered with a page (`text/html`), `MediaGroupsTest.theMainVideoIsThePlayingOneElse…`
    (old size-first ranking); the other 121 tests of those classes pass.
  - Validation: `./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest
    :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug` →
    BUILD SUCCESSFUL (5m 48s): 878 tests, 0 failures, 66 skipped (app 674, core-browser 88,
    core-media 28, core-model 69, extractor-generic 19; was 848); lint 0 errors, 95 warnings
    (unchanged).
  - Live check (2026-10-06): no public HLS page with previews was reachable without sign-in or
    an age check; coverr.co (stock videos, public) instead. Home path on one video page (first
    2 MiB of 6.95 MB): 50 candidates (1 MAIN from og:video/JSON-LD, 24 thumbnail previews,
    25 more) → 1 video (cdn.coverr.co, 25 s) + 49 others; preparing it: HEAD went to the
    site's home page (`text/html`), the one-byte GET gave `video/mp4`, 13 631 866 bytes, 1080p.
  - Owner check: the site from Preview #3: Home → that video's sheet with its real length and
    "Other videos on this page"; the browser's Download → the same; the download finishes; if
    it fails, a screenshot of the sheet's Details.
- P25 — One sheet for every site: IN PROGRESS
- Hand-offs:
  - Hand-off to Agent A: `core-download/.../DirectRangeProbe.kt` — when HEAD lands on a web page
    (`text/html`), ask the original address with the range GET, not HEAD's final address — a
    file host (cdn.coverr.co) sends HEAD to its home page while GET returns the file; the
    sheet's resolver does this since P24, so the download would fail where the sheet works.
  - Hand-off to Agent A: `core-download/.../SecureDownloadHttp.kt` — send `Origin` and an
    origin-only `Referer` (never cookies) to a playlist's or segment's other host too — a page's
    HLS CDN may refuse pieces without them; since P24 the resolver keeps the page's `Origin` in
    each variant's `requestContext.observedHeaders`.
- Next: P25 (names by height, one-line descriptions, Audio "M4A" and "MP3 · 128 kbps", sizes,
  audio from MP4s of unknown codecs), then READY FOR MERGE.
