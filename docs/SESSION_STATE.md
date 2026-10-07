# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 13 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P33.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P33 only)

- Phase: 13 — Preview #4 polish (other sites' pre-roll ads, YouTube merge at 99%, browser
  search, history and pop-ups). Plan: `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-13-integration` = `main` `bc806f9` + the plan commit. Agent A
  `work/phase-13-merge-speed` (P27, later P33), Agent B `work/phase-13-generic-main` (P28, P29),
  Agent C `work/phase-13-browser` (P30, P31, P32); all start from
  `origin/work/phase-13-integration`. Merge order A → B → C (P33), then Preview #5; P8 (signed
  `1.0.0-beta.4`) only with the owner's OK. Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #4 (2026-10-07, run 37530061595, `bc806f9`): "about 90% fine". Open:
  on a free video site without an adapter the sheet opens the pre-roll ad (0:30, 1080p MP4)
  instead of the page's video (16:24, 720p HLS, only under Other videos) and one page shows
  HTTP 410; long YouTube live recordings wait a long time at 99% (the merge); the browser
  searches DuckDuckGo, has no history, and ads redirect the tab to other sites. Root causes
  R7–R15 in FIX_ADD_PLAN §4; owner decisions F1–F6 in §3 (defaults: A+ generic, Google,
  history on, blocking on, direct mux, three agents).
- Rules: ADR-006 public videos only; no DRM/paywall/private/age-gate bypass; adapters never sign
  in; agents never automate a page's age or identity check. Never print/commit cookies, tokens,
  visitor data, signed media/image URLs or keys. Keep testTags, Kotlin lines ≤ 100, WebView on
  the main thread. No reset --hard/clean/stash. One Gradle command at a time; temporary files
  outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK
  `/data/toolchains/android-sdk` (platform 35, NDK 27.3.13750724, CMake 3.22.1);
  `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with SSH: when
  `/data/.ssh/id_ed25519` is missing make a **new** key (never search for old keys), show the
  owner the public line and wait until he adds it as a deploy key with write access.
- Phase 12 record (per-task Results, validation, CI runs, P26 merge notes):
  `git show bc806f9:docs/SESSION_STATE.md`. Phase 11: `git show 4db6c2b:docs/SESSION_STATE.md`.
- Last pushed checkpoint: PLAN: Phase 13 (this commit, docs only, on
  `work/phase-13-integration`).
- Next: the owner pastes `docs/prompts/A-merge-speed.md`, `B-generic-main.md` and
  `C-browser.md` into three new agent chats; when all three are READY FOR MERGE, Agent A runs
  `M-merge-preview5.md` (P33, Preview #5); P8 only with the owner's OK after Preview #5.
- Last updated: 2026-10-07 (Phase 13 plan)

## Agent A — `work/phase-13-merge-speed` (P27; later P33)

- Status: NOT STARTED
- P27 — YouTube: no long wait at 99%: TODO
- Hand-offs: none

## Agent B — `work/phase-13-generic-main` (P28, P29)

- Status: IN PROGRESS (P29 checkpoint; P28 OWNER CHECK) — started 2026-10-07 in `/data/YFT-B`, base commit `7873d51`
  (`origin/work/phase-13-integration`, the Phase 13 plan on `main` `bc806f9`).
- Environment: rebuilt after a sandbox reset (JDK 17 `/data/toolchains/jdk17`, SDK 35,
  build-tools 35.0.0, NDK 27.3.13750724, CMake 3.22.1, 4 GiB swap); new SSH deploy key
  (owner added it, push checked).
- Starting state (Agent B scope, before any edit): `./gradlew --no-daemon --continue
  :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest
  :extractor-generic:test :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (9m 22s): 905 tests, 0 failures,
  66 skipped (app 690, core-browser 88, core-media 28, core-model 80, extractor-generic 19);
  lint 0 errors.
- P28 — Other sites: the page's video, not the ad before it: OWNER CHECK (2026-10-07)
  - Result: on a site without an adapter the browser and Home read the page's stated length,
    title and picture (meta tags and JSON-LD only, also a VideoObject naming an embed page) and
    its player's setup (JW Player, video.js, KVS `flashvars`, quality lists). With a stated
    length of 2 min or more, Download during the pre-roll opens the file of that length (else
    the one the page or its player names) with the page's title and picture; a file under half
    the length, the free video sites' ad networks and the file a frame fetches within 6 s after
    asking another site for a VAST/VMAP ad break go under Other videos. With only the ad so far
    the sheet shows "Finding the page's video…" for up to 6 s and switches by itself, else the
    ad with `quick-maybe-ad`. Every entry takes the page's title and picture where its files
    name none. New: `PageVideoFacts`, `PageFactsReader`, `PlayerSetupScanner`, `MiniJson`,
    `VastAdTracker`; `MediaGroups` (`withPageRoles`, `withPageFacts`, `mayBeAdBefore`,
    `mainVideo`/`ofPage`/`looksLikePreview` with facts; `nextVideo`, `refreshed` for P29).
  - Plan adapted: the VAST/VMAP *answer* is not visible to the browser's request hook (it sees
    requests, not responses), so an ad break is known by its address (`vast`/`vmap` as a whole
    path part) and its ad is the next file the same frame (same `Referer` origin) fetches from
    another site within 6 s. `adtng` is not on the ad list (not confirmed); the eleven domains
    listed are the ad networks' own.
  - Validation (2026-10-07): 955 tests, 0 failures, 66 skipped (app 699, core-browser 114,
    core-media 28, core-model 95, extractor-generic 19); lint 0 errors; androidTest compiles;
    line check empty.
  - Regression proof: old code `679ec78` with the new tests in its API: 4 of 4 failed
    (`PrerollFixtureTest` download during the ad → the 0:30 ad; `AdBreakTest` ad networks;
    `PlayerSetupScannerTest` video.js setup → 2 videos; `BrowserPageVideoWaitTest` download
    during the ad → the ad). Backup `/data/bak/P28` (38 files), `cmp` equal.
  - Live check: skipped — the owner's kind of site shows an age gate first, which YFT never
    automates (ADR-006); the fixtures copy the Preview #4 case. Instrumented
    `PrerollInstrumentedTest` runs on the CI emulator.
  - CI (`f334f55`): [checkpoint validation](https://github.com/Alalkipgen/YFT/actions/runs/37560950060)
    green, [Preview APK](https://github.com/Alalkipgen/YFT/actions/runs/37560950114) green,
    [emulator smoke](https://github.com/Alalkipgen/YFT/actions/runs/37560950074) red (1 of 24:
    `PrerollInstrumentedTest`, the page script's `fetch` not reported by the request hook in
    time); fixed in the P29 commit (the test gives the hook's reports itself).
  - Owner check: the site from Preview #4, Download while the ad plays → the page's title,
    picture and length with 480p/720p; the ad only under Other videos.
- P29 — Other sites: the next video when one fails: OWNER CHECK (2026-10-07)
  - Result: when the sheet cannot prepare a page's video (no adapter) because its file is gone
    (HTTP 403, 404, 410 or `INVALID_URL`) and the page has another video that is not an ad or a
    preview, it prepares that one once, by itself (`MediaGroups.nextVideo`, P28's order), with
    "The first file is gone — showing the next video" (`quick-next-video`) and Details
    (`quick-next-video-details`) listing both attempts; when the next one fails too, the
    failure's Details list both. Try again asks for the page's current files first
    (`MediaGroups.refreshed`: same address, same file without its signed query, else same
    length): the browser keeps the store's page current; for a page Home found the store asks
    Home to read it again quietly (`DetectedMediaStore.readPageAgain` / `pageReads`, up to 20 s;
    `DetectedPage.owner`). Every entry keeps P28's page title and picture.
  - Validation (2026-10-07): 962 tests, 0 failures, 66 skipped (app 706, core-browser 114,
    core-media 28, core-model 95, extractor-generic 19); lint 0 errors; androidTest compiles;
    line check empty.
  - Regression proof: P28 code `f334f55` (`git archive` to `/data/tmp/p29-old`) with the new
    sheet tests (the asserts on the new state fields and the Home-read test held aside): 3 of 4
    failed — "a gone first file shows the page's next video…" (the HTTP 410 error), "when the
    next video fails too…" (HTTP 410 shown, the next video never tried), "Try again asks the
    store's newest address…" (the dead `token=old` address again); "an ad or a preview is never
    the next video" passes (guard). Backup `/data/bak/P29` (8 Kotlin files), `cmp` equal.
  - Live check: skipped (same reason as P28); the tests copy the Preview #4 "HTTP 410" case.
  - CI: pending for the P29 checkpoint; links recorded with the READY FOR MERGE commit.
  - Owner check: the page that showed "HTTP 410" in Preview #4 → the sheet opens a working
    video (or the page's own after P28) without the error.
- Hand-offs: none

## Agent C — `work/phase-13-browser` (P30, P31, P32)

- Status: NOT STARTED
- P30 — Browser: Google search: TODO
- P31 — Browser history: TODO
- P32 — Block pop-ups and ad redirects: TODO
- Hand-offs: none
