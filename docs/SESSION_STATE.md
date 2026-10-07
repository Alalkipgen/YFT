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

- Status: NOT STARTED
- P28 — Other sites: the page's video, not the ad before it: TODO
- P29 — Other sites: the next video when one fails: TODO
- Hand-offs: none

## Agent C — `work/phase-13-browser` (P30, P31, P32)

- Status: P30, P31, P32 OWNER CHECK
- Started 2026-10-07 in `/data/YFT-C` from `origin/work/phase-13-integration` `7873d51`.
  Starting state (`/data/tmp/validate-c.sh`: core-model, core-data, core-browser and app unit
  tests, `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`): core-model 80, core-data 18,
  core-browser 88, app 690 (66 skipped), 0 failures; lint 0 errors, 95 warnings.
- Environment after a sandbox reset: NDK 27.3.13750724 and CMake 3.22.1 reinstalled with
  `sdkmanager`; a new deploy key `/data/.ssh/id_ed25519` (added by the owner).
- P30 — Browser: Google search: OWNER CHECK
  - Result: words typed in the address bar or on the start page search Google
    (`https://www.google.com/search?q=…`, every character encoded). Settings › Browser ›
    Search engine offers Google (default), DuckDuckGo and Bing, stored in DataStore
    (`browser_search_engine`; a missing or unknown value reads as Google, so an old install
    gets Google). The start page's row says "Search Google for “…”" (or the chosen engine).
  - Plan adapted: the plan put the engine in `BrowserViewModel` (Agent B's file). Instead a
    small `BrowserSettingsViewModel` (Agent C) gives the route the preferences and the route
    sets `BrowserSearch.engine`; `BrowserSearch.webUrl(words)` keeps its signature and uses that
    engine, so the view model's search follows the setting without changing B's file. One line
    of B's test `BrowserViewModelTest` (expected DuckDuckGo address → Google) had to change in
    this branch, because that test checks the default. The Browser group sits between Downloads
    and Privacy in Settings.
  - Validation: see the checkpoint line below.
  - Regression proof: the old `BrowserSearch.kt`, `BrowserStartPage.kt` and `BrowserScreen.kt`
    (`/data/bak/P30/orig`) with the new tests (those needing the new API held aside): 5 of 75
    fail — `BrowserRouteTest.typedWordsSearchGoogleByDefault`,
    `BrowserScreenTest.wordsOfferYouTubeAndGoogleSearchRows`,
    `BrowserSearchTest.theWebSearchIsGoogleWithEveryCharacterEncoded`,
    `BrowserSearchTest.searchAddressesEncodeTheWords`,
    `BrowserViewModelTest.wordsInTheAddressFieldSearchTheWebInsteadOfFailing`; new files
    restored with `cp`, `cmp` equal.
  - Owner check: type words (for example `myanmar news`) in the address bar → Google results;
    the start page shows "Search Google for “…”"; Settings › Browser › Search engine →
    DuckDuckGo → the next search opens DuckDuckGo; back to Google.
  - Checkpoint `521b0b6`; CI green: checkpoint validation
    https://github.com/Alalkipgen/YFT/actions/runs/37551748282, emulator smoke
    https://github.com/Alalkipgen/YFT/actions/runs/37551748382, Preview APK
    https://github.com/Alalkipgen/YFT/actions/runs/37551748312. P30 validation:
    core-model 81, core-data 20, core-browser 88, app 698 (66 skipped), 0 failures; lint
    0 errors, 95 warnings (unchanged); `:app:compileDebugAndroidTestKotlin` OK.
- P31 — Browser history: OWNER CHECK
  - Result: Room 6 adds `browser_history` (`url` key, `title`, `host`, `last_visited_at`
    with an index, `visit_count`); `MIGRATION_5_6` only creates the table and its index
    (schema `core-data/schemas/…/6.json`). `BrowserHistoryDao` (visit = update, else insert,
    in one transaction — Android 7's SQLite has no UPSERT; newest, search by title, host or
    address with `%` and `_` taken literally, rename, delete, delete all, prune). Rules in
    `BrowserHistoryAddress`: HTTPS only (never `about:`, `data:`, HTTP, files or the start
    page), no fragment, no user name or password, no `utm_*`, `fbclid`, `gclid`, `dclid`,
    `gbraid`, `wbraid`, `msclkid`, `igshid`, `mc_eid`, `yclid`; 90 days and 5,000 pages kept.
    `BrowserHistoryRecorder` decides what a visit is: a page that finished without a
    main-frame error (once per document), and a single-page site's own address change after
    it finished (YouTube's videos); redirects while loading, fragments and tracking parameters
    are no new visit; the title follows from the new `onPageTitle` (`onReceivedTitle`).
    `HistoryRecordingSink` wraps the browser's view model as the WebView's sink (every event
    passed on unchanged, same order and thread). `BrowserHistoryViewModel` checks Settings ›
    Browser › Save browser history at each visit and runs writes in order. UI: the toolbar's
    new menu button (`browser-menu`) › History (`browser-menu-history`) opens a full-screen
    list inside the browser screen (`browser-history`: search box, Today / Yesterday /
    Earlier, a tap opens the page, a row's menu deletes it, Clear history with a
    confirmation, Back closes it); the start page shows the last six pages under Recent
    (`browser-recent`, "History" opens the list). Settings › Browser: "Save browser history"
    (`settings-save-history`, default on) and "Clear browser history"
    (`settings-clear-history`, with a confirmation); "Clear browsing data" clears the history
    too (`BrowserHistoryCleaner` in the composite cleaner; its dialog says so).
  - Plan adapted: the browser had no menu; the menu button is the toolbar's fifth button (the
    top row keeps its address field width). The recorder is a sink wrapper in Agent C's files
    instead of a change in `BrowserViewModel`; one new sink method with a default body
    (`onPageTitle`).
  - Validation: see Last validation below.
  - Regression proof (the new tests need the new API, so the behaviour was broken on purpose
    in a copy and restored): the WebView's sink back to the view model, tracking parameters
    kept, the switch ignored, the migration's index left out, the count not raised → 8 tests
    fail (both migration tests, the address rules, the title and count tests, the recorder's
    single-page test, the switch test, the route's WebView-to-history test); files restored
    from `/data/bak/P31/new` with `cp`, `cmp` equal.
  - Owner check: open three sites in the browser → menu (⋯ at the bottom right) › History
    lists them, newest first, under Today; a tap opens one; a row's ⋯ › Delete removes it;
    Clear history › Clear empties the list; the start page shows them under Recent;
    Settings › Browser › Save browser history off → new pages are not added.
  - Checkpoint `e9899f2`; CI: checkpoint validation
    https://github.com/Alalkipgen/YFT/actions/runs/37555932478 and emulator smoke
    https://github.com/Alalkipgen/YFT/actions/runs/37555932456 green. Preview APK
    https://github.com/Alalkipgen/YFT/actions/runs/37555932462 failed 21 s into its build step,
    before Gradle compiled anything (a runner problem; logs need a token). The same commit's
    preview build passed here (`:app:assemblePreview`, `verify-release-apk.sh`: VERIFIED), and
    P32's push builds the preview again.
- P32 — Block pop-ups and ad redirects: OWNER CHECK
  - Result: `AdRedirectPolicy` (pure, `core-browser/.../policy/`) decides where a page may
    send the tab. Blocked: a host on YFT's own list `AdNetworks` (20 pop-up and redirect ad
    networks, 32 hosts and their subdomains, each family with its reason; written for YFT, no
    copied filter list) as a page navigation, a redirect hop or a new window; and a navigation
    to another site that the page started without the user's tap (`!hasGesture()`, not a
    server redirect) once the page has opened (finished 1.5 s ago or loading for 8 s, so a
    page that forwards while it opens — `l.facebook.com`, `t.co`, Google's `/url` — still
    forwards). Allowed: taps on links (also to other sites), the same site (`m.youtube.com` =
    `youtube.com`; `bbc.co.uk`, `….com.mm`, `….github.io` known), server redirects, and what the
    user typed or picked (`loadPage` → `BrowserNavigationGuard.userNavigation()`: that page, its
    redirects and forwards until it opened, at most 10 s); app links as before. New windows:
    multiple windows on (scripts still need a tap) and `SecureBrowserChromeClient.onCreateWindow`
    hands the window a hidden WebView (`PopupWindowCatcher`: no scripts, gone after its first
    web address or 10 s); a tap's window to the same site opens in the current tab, every
    other window is blocked. Scripts, frames and images of the listed networks get an empty
    answer in `shouldInterceptRequest` after the sink saw the request; ExoClick and
    TrafficStars keep theirs because they also serve players' ads (F4: a site's own video ads
    stay). The browser shows "Pop-up blocked" or "Blocked a redirect to `host`" with Open
    (`browser-blocked-notice`, `browser-blocked-open`; 4 s, under the address bar); Open loads
    it in this tab as the user's choice. Settings › Browser › "Block pop-ups and ad redirects"
    (`settings-block-popups`, default on, DataStore `browser_block_popups`); off → windows
    open in the current tab and every address loads as before P32. A blocked page never
    loads, so P31 never records it.
  - Plan adapted: contracts kept — both WebView clients take an optional `guard` (null = the
    old behaviour), the sink gets one method with a default body (`onNavigationBlocked`), the
    sink's calls keep their order and threads (the guard is asked after them). The emulator
    test hosts the real WebView, policy, clients, guard and notice in a Compose test instead
    of the whole browser screen (local fixture pages, no network page needed).
  - Validation: see Last validation below.
  - Regression proof: the old behaviour put back in copies (navigations never blocked, scripts
    never emptied, windows always opened here, multiple windows off, the switch not stored)
    → 7 tests fail: `SecureBrowserWebViewClientTest` (the page's own redirect, listed networks,
    listed scripts), `SecureBrowserChromeClientTest` (windows), `SecureWebViewPolicyTest`,
    `DataStoreBrowserPreferencesRepositoryTest` (pop-up switch), `BrowserRouteTest` (notice and
    Open); files restored from `/data/bak/P32/new` with `cp`, `cmp` equal.
  - Owner check: on the sites where ads used to jump to spam pages, taps on the page and its
    ads no longer leave it; "Pop-up blocked · Open" opens the blocked page when wanted; a
    normal link to another site still opens; videos still play and Download still works;
    Settings › Browser › Block pop-ups and ad redirects off → pages behave as before.
- Last validation (P32, 2026-10-07): `/data/tmp/validate-c.sh` → core-model 83, core-data 33,
  core-browser 107, app 724 (66 skipped), 0 failures; lint 0 errors, 95 warnings (unchanged);
  `:app:compileDebugAndroidTestKotlin` OK (the new emulator test runs in CI); line check empty.
- Hand-offs: none. Note for Agent B: this branch changes line 80 of `BrowserViewModelTest`
  (expected search address DuckDuckGo → Google); keep Google when merging.
