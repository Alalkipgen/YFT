# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 15 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P44.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P44 only)

- Phase: 15 — Preview #6 field fixes (TikTok from TikTok's own page in the browser and on Home,
  with every failure explained in Details; YouTube % and speed within seconds; Delete file in
  Downloads; the page's own video, never the pre-roll ad, on other sites). Plan:
  `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-15-integration` = `main` `a9eea7b` + the plan commit. Agent A
  `work/phase-15-tiktok` (P39, P40), Agent B `work/phase-15-downloads` (P41, P42, later P44),
  Agent C `work/phase-15-ads` (P43); all start from `origin/work/phase-15-integration`. Merge
  order B → C → A (P44), then Preview #7; P8 (signed `1.0.0-beta.4`) only with the owner's OK.
  Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #6 (2026-10-09, run 37811403962, `4da3e61`): TikTok (VPN) "changed
  its page format" on every browser video and many Home links (Quality unknown rows, a
  "Download · 1.4 MB" row that fails without a step); "bypass TikTok like YouTube, whatever
  works"; a YouTube live recording waits long before the % moves; Delete for the file itself;
  an adult site's sheet sometimes shows the 0:30 pre-roll ad after "The first link is gone".
  Root causes R25–R34 in FIX_ADD_PLAN §4; owner decisions G1–G8 in §3 (defaults: three agents,
  Chrome agents for TikTok, the browser's TikTok cookies on Home, watermark hidden, hidden
  TikTok page on, fast start on, delete with a dialog, strict ad rule).
- Live checks in Plan Mode (2026-10-09, US sandbox): TikTok answers normally (phone page
  `webapp.reflow.video.detail` with one quality; desktop and headless pages `webapp.video-detail`
  with 4 qualities); media files need the same answer's `tt_chain_token` and a `www.tiktok.com`
  Referer, except the cookie-free `aweme/v1/play` address. The owner's VPN country gets other
  answers: his phone is the final proof, so P39 adds Details to every TikTok failure.
- CI: pushes that change only `docs/**` or `*.md` start no checkpoint validation
  (`paths-ignore`); "green CI" means the newest commit that changed code.
- Rules: ADR-006 any working technique for public videos (owner, confirmed for TikTok on
  2026-10-09); no DRM/paywall/private/age-gate bypass; adapters never sign in; agents never
  automate a page's age or identity check or a puzzle. Never print/commit cookies, tokens,
  visitor data, signed media/image URLs or keys. Keep testTags, Kotlin lines ≤ 100, WebView on
  the main thread. No reset --hard/clean/stash. One Gradle command at a time; temporary files
  outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK
  `/data/toolchains/android-sdk` (platform 35, NDK 27.3.13750724, CMake 3.22.1);
  `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with SSH using the key
  named in `/data/.ssh/CURRENT_KEY` (pushed on 2026-10-09); when it is missing or refused make
  a **new** key (never search for old keys), show the owner the public line and wait until he
  adds it as a deploy key with write access.
- Phase 14 record (per-task Results, validation, CI runs, P38 merge notes):
  `git show a9eea7b:docs/SESSION_STATE.md`. Phase 13: `git show 5a5bddb:docs/SESSION_STATE.md`.
- Phase 15 start: P38's full validation on `4da3e61` — 1532 tests, 0 failures, 66 skipped; lint
  0 errors; `:app:assembleRelease` OK.
- P44 (2026-10-09, Agent B as integrator, `/data/YFT-B`): B → C → A merged with `--no-ff`
  (`2661062`, `f3f80ce`, `257af94`); the only conflict, `DefaultVariantResolver.kt` and its test
  (C's owner-override change of A's files), kept C's side (= A's `8b0bc93` file plus the `error`
  lines). C → A hand-offs (a) and (b) open: (a) was tried and reverted (it breaks P24's
  owner-case test that counts an ad as another video), (b) `vastAds.onAnswer` in the browser. Full
  validation: 1717 tests, 0 failures, 66 skipped (app 900, core-browser 149, core-data 33,
  core-download 184, core-media 33, core-model 122, extractor-api 36, extractor-generic 21,
  extractor-sites 239; 1532 at the start); lint 0 errors (98 warnings: A's 3 androidx.webkit
  notices); `:app:assembleRelease`: OK (lint vital needed a 2.5 GiB Gradle heap on the 4 GiB
  sandbox); line check clean after wrapping one line of B's `DeleteFileScreenTest`.
- Last pushed checkpoint: `1ef8c86` P44 merge B, C, A — full validation green, on
  `work/phase-15-integration`; CI of `1ef8c86`: checkpoint validation
  [37893874474](https://github.com/Alalkipgen/YFT/actions/runs/37893874474), emulator smoke
  [37893874480](https://github.com/Alalkipgen/YFT/actions/runs/37893874480), Preview APK = **Preview #7**
  [37893874478](https://github.com/Alalkipgen/YFT/actions/runs/37893874478) (Artifacts › `yft-preview-apk`), all green. This
  docs commit adds the links; `main` is fast-forwarded to it at the owner's request (no tag).
- P45 (2026-10-09, Agent B alone at the owner's request, `/data/YFT-B` re-cloned after a
  sandbox reset, new key `yft_b_202610092044`): the owner's Preview #7 test of the adult site
  (Other videos row with ads, HTTP 474 at the file check, HTTP 410 at the manifest and "Next
  video"). Done: the sheet shows only the tapped video; a page's links are asked like the
  browser's; any HEAD error gets the range GET; refused links (403, 410, 412, 452–499) are asked
  again through WebView (`BrowserReads`, also for HLS playlists); new messages and Details
  lines; proven ads out of the browser's lists. Live checks of the site were refused by the
  sandbox's reviewer, so public reports (yt-dlp #17642, PR #16794) back R37. Open: hand-off (b)
  and a Cronet option (FIX_ADD_PLAN §7). Full validation: 1729 tests, 0 failures, 66 skipped (app 903, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 239; 1717 before P45); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean.
- Last pushed checkpoint: `1d2f27a` P45 on `work/phase-15-integration`; CI: checkpoint validation
  [37955002943](https://github.com/Alalkipgen/YFT/actions/runs/37955002943), emulator smoke [37955002962](https://github.com/Alalkipgen/YFT/actions/runs/37955002962), Preview APK =
  **Preview #8** [37955002856](https://github.com/Alalkipgen/YFT/actions/runs/37955002856) (Artifacts › `yft-preview-apk`), all green. `main` stays at `69bf022` until the owner says `MAIN=OK`.
- P46 (2026-10-10, Agent B alone; `/data/YFT-B` re-cloned after a sandbox reset, new key
  `yft_b_202610091709`, JDK 17 and SDK 35 in `/data/sdk`): the owner's TikTok report (one quality
  in the browser, public posts called private, check messages). Owner chose A, then B. Done: a
  page's TikTok status is not final; the browser joins the desktop page's qualities; Show check;
  the public download service after TikTok's pages. Not done: TikTok's app API. Full validation: 1746 tests, 0 failures, 66 skipped (app 909, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 250; 1729 before P46); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean.
- Last pushed checkpoint: the P46 checkpoint P46 on `work/phase-15-integration`; CI: CI links in the next docs commit.
  `main` stays at `69bf022` until the owner says `MAIN=OK`.
- Next: the owner tests Preview #9 (FIX_ADD_PLAN §6 "Preview #9") and sends Details of anything
  that fails; P8 (signed `1.0.0-beta.4`) only with his OK.
- Last updated: 2026-10-10 (P46)

## Agent A — `work/phase-15-tiktok` (P39, P40)

- Status: READY FOR MERGE — P39 DONE (2026-10-09), P40 DONE (2026-10-09; base commit
  `8b0bc93`, the P39 checkpoint).
- P40 set-up: clone `/data/YFT-A`, key `/data/.ssh/yft_a_202610090329` (added by the owner),
  toolchain under `/data/opt`; starting state green (1254 JVM tests, 0 failures).
- Base commit: `d0bc7f7` (= `origin/work/phase-15-integration`, the plan commit); folder
  `/data/YFT-A`; push over SSH with the key named in `/data/.ssh/CURRENT_KEY` (sandbox reset on
  2026-10-09: new key `/data/.ssh/yft_a_20261009`, added by the owner as a deploy key; JDK 17,
  SDK 35, NDK and CMake reinstalled under `/data/tools`; a 4 GiB swap file keeps the full
  validation from being killed for memory).
- Starting state (Agent A scope, before any edit; the full command of FIX_ADD_PLAN 0.3 for
  Agent A): BUILD SUCCESSFUL — 1207 JVM tests, 0 failures, 66 skipped (app 807, core-browser
  138, core-media 28, extractor-api 32, extractor-sites 202); lint 0 errors, 95 warnings;
  androidTest Kotlin compiles.
- P39 Result (all 8 steps; last code commit `49fa1b7`):
  - Details (step 1): every TikTok lookup keeps Details lines, success or failure — page agent,
    HTTP status, KB, landed on video/home/challenge/login/other page, data key, JSON read or
    `error <Class> at char N`, post id, qualities, play/download address, file checks and
    `error: <Class> at step <step>`; hosts only. They fill `SiteAdapterOutcome.Failed.details`
    and the new `PageVideoLookup.details` (default empty), which `lookupState` shows as the
    sheet's Details (browser page and focused lookups; Home shows the adapter's Details).
  - Reasons (step 2): a link landing on TikTok's home page or a page without a post id →
    `PRIVATE_OR_UNAVAILABLE` "This TikTok link does not open a video. It may be removed or
    private — open it in YFT's browser to check."; a challenge page → `BOT_CHECK`;
    `RESPONSE_CHANGED` only for an unknown data shape, text "<Site>'s page could not be read.
    Tap Details to see why, or Try again." (no "Falling back to generic detection").
  - Requests (step 3): `OkHttpExtractorClient` leaves out cookie pairs and headers OkHttp refuses
    ("session pairs left out: N", "headers left out: N"), carries a per-lookup cookie jar across
    short-link redirects, ends too many redirects as `HTTP_STATUS` and any other error as
    "error: <Class> at step <step>".
  - Agents (step 4, `TT_AGENT=CHROME`): the phone page with the WebView's own agent, then the
    desktop page (Windows Chrome with the WebView's Chrome version, never "YFT") when the phone
    page fails for a non-final reason or lists fewer than 2 qualities; the answer with more
    working qualities wins.
  - Data (step 5): the data script found by its `id` attribute (also when an earlier script
    names it), text after the data object cut, any `__DEFAULT_SCOPE__` key holding
    `itemStruct`, one lenient read of entity-encoded JSON, another post's id skipped.
  - Files (step 6): rows from every `bitrateInfo` address, `playAddr` and `aweme/v1/play`, each
    checked with `Range: bytes=0-0`, the answer's cookies and Referer `https://www.tiktok.com/`
    (at most 8 checks, 3 at a time, 5 s each): 206 → exact size, a refusal → the next address,
    none → the quality is left out; labels 1080p/720p/540p, "H.265", H.264 first, duplicates
    once; `TT_WATERMARK=HIDE`: `downloadAddr` only when no other file opens, "With TikTok
    watermark".
  - Resolver (step 7): root cause of the owner's "Download · 1.4 MB" row — P38 handled the file
    check's answers on the caller's thread, the Download sheet's main thread; closing a range
    answer reads its unread bytes, which Android forbids on main, and P38 caught only
    IOException/IllegalArgumentException, so the error escaped without a step (a TikTok-shaped
    MP4 itself resolves on the JVM). Now `resolve` runs in `withContext(Dispatchers.IO)`, any
    unexpected error → a failure at its step, MP4 header-probe errors leave the row unmeasured,
    and a HEAD answer with 5xx is asked again with the range GET like 405/501 (4xx still fails).
  - Browser (step 8): `FocusedVideoProbe` returns the on-screen video's https `currentSrc`
    (at most 2048 characters); new `PlayerFileFallback`: when the TikTok adapter fails on a
    TikTok page, the player's file (the request matching `currentSrc`, else the newest TikTok
    media request) becomes the MAIN row with its cookies and Referer, banner "TikTok's page
    could not be read — showing the file its player is playing."; DRM or no player file → the
    failure with Details and Try again. `BrowserViewModel` uses it for page and focused lookups.
  - Plan adapted: the resolver's failure names its step and host; the exception class needs
    `VariantResolutionResult.Failure.error` in core-model media (Agent C's), see Hand-offs. The
    HEAD 5xx retry came from the live check (the 540p H.265 file's HEAD answered 504).
    `extractor-sites/build.gradle.kts` (A's folder) gained `kotlinx.coroutines.core` for the
    3-at-a-time file checks.
- Tests (P39, JVM, +47): extractor-sites `TikTokExtractorTest` 35 (+17), `TikTokRegressionTest`
  5 (new), `TikTokAgentsTest` 4 (new), `TikTokUrlsTest` 6 (+1), `SiteNavigationHeadersTest` 4
  (updated); extractor-api `BoundedJsonParserTest` 10 (+1); app `OkHttpExtractorClientTest` 13
  (+4), `OkHttpExtractorClientRegressionTest` 4 (new), `OkHttpExtractorNetworkTest` 6 (+1),
  `SiteAdapterCoordinatorTest` 11 (+1), `BrowserPlayerFileFallbackTest` 3 (new),
  `QuickDownloadViewModelTest` 45 (+1: `aFailedPageLookupShowsTheLookupsStepsAsDetails`, the
  "Done when" item "a forced failure shows its Details lines in the sheet"); core-browser
  `FocusedVideoProbeTest` 13 (+1); core-media `DefaultVariantResolverTest` 22 (+4).
- Regression proof: on main's `TikTokExtractor`, `TikTokPageParser`, `OkHttpExtractorClient`,
  `SiteAdapterCoordinator`, `SiteAdapterModule`, `BrowserViewModel` and `DefaultVariantResolver`
  (the tests that use the new APIs moved aside; restored with `cp`, checked with `cmp`)
  `TikTokRegressionTest` 5/5, `OkHttpExtractorClientRegressionTest` 4/4,
  `BrowserPlayerFileFallbackTest` 3/3 and the resolver's "responses are never read on the
  caller's thread, which Android forbids on main" and "an unexpected error ends as a failure at
  the step it stopped at" fail, and pass on the new code. "a file server that answers HEAD with
  a server error is asked for the file itself" fails on main's resolver with `HTTP_STATUS` 504
  at `FILE_CHECK` (backup `/data/bak/P39-head5xx/`).
- Live check (2026-10-09, US sandbox, polite, hosts only): video `@scout2015/6718335390845095173` —
  phone page 200 · 150 KB · video page · `webapp.reflow.video.detail` · 1 quality (540p, playAddr
  only); desktop page 200 · 387 KB · `webapp.video-detail` · 3 qualities, all 3 file checks 206
  (`v16-webapp-prime.us.tiktok.com`): 720p H.265 720×1280 hvc1 2,004,627 B · 540p 576×1024 avc1
  2,953,029 B · 540p H.265 576×1024 hvc1 1,728,265 B. Download sheet resolver: 720p and 540p OK with
  the same exact sizes; 540p H.265 → HTTP 504 at FILE_CHECK, because the file host answered the HEAD
  with 504. Re-check after the HEAD 5xx fix (same day; one page, 3 files, temporary code not
  committed): the file host still answers that HEAD with 504 (and `lower_540_0`, which YFT does not
  offer, with 404), and the real resolver now gives all three rows Success with exact sizes: 540p
  2,953,029 B, 720p H.265 2,004,627 B, 540p H.265 1,728,265 B. Short link `vt.tiktok.com/ZS2ueXNrd/`
  and dead link `vt.tiktok.com/ZSqq9zz9qq/` → `PRIVATE_OR_UNAVAILABLE` with the new "does not open a
  video" text (both 200 · 167 KB · landed on the home page · no data; the short link also seems
  gone). No live-check code was committed.
- Validation (2026-10-09, `49fa1b7`): Agent A command (FIX_ADD_PLAN 0.3) → BUILD SUCCESSFUL — 1254
  JVM tests, 0 failures, 66 skipped (app 821, core-browser 139, core-media 32, extractor-api 33,
  extractor-sites 229; +47 from 1207); `:app:lintDebug` 0 errors, 95 warnings (unchanged);
  `:app:compileDebugAndroidTestKotlin` OK; line check empty. The first run was killed for memory
  during the app tests (no swap on the new machine); the rerun with swap ran the rest, the finished
  tasks UP-TO-DATE.
- CI (`49fa1b7`, the last code commit; the docs-only P39 checkpoint starts no CI): checkpoint
  validation [37865869227](https://github.com/Alalkipgen/YFT/actions/runs/37865869227)
  (`yft-debug-apk`), emulator smoke API 34
  [37865869239](https://github.com/Alalkipgen/YFT/actions/runs/37865869239), Preview APK
  [37865869254](https://github.com/Alalkipgen/YFT/actions/runs/37865869254) (`yft-preview-apk`) —
  all green.
- Owner check (pending): Agent A's Preview APK (run 37865869254 › `yft-preview-apk`, with the
  VPN) — TikTok in YFT's browser → Download on a video page and on For You → qualities with
  sizes → the file plays; a `vt.tiktok.com` link on Home → qualities; anything that fails → a
  screenshot of Details.
- Backlog: live 2026-10-09, TikTok's file host answered HEAD 404 for `lower_540_0` while a range GET
  of it gave 206. YFT does not offer that file today (the `normal_540_0` row wins at 540p H.264),
  but a row with such an address would fail at FILE_CHECK with 404, because a 4xx HEAD answer still
  fails (the owner's rule for the 5xx fix); P40 or later could skip the HEAD for TikTok rows whose
  file the adapter already checked.
- P40 Result (all 7 steps; code in the P40 checkpoint, base `8b0bc93`):
  - Page data (step 1): `SiteExtractionRequest.pageData: SitePageData? = null` (extractor-api,
    additive): one post's JSON text, at most 64 KB (UTF-8), and its source — `TAB_SCRIPT`
    "tab · page script", `TAB_API_ANSWER` "tab · API answer", `HIDDEN_PAGE` "hidden page";
    `toString` names the source and size only. The TikTok adapter builds rows from data whose
    id is the link's post id without asking for the page (Details `data: tab · API answer ·
    JSON: read`, `post id: matches`, `answer: tab · 3 working qualities`) and checks the files
    as in P39 step 6 with the lookup's cookies and Referer `https://www.tiktok.com/`. Another
    post's id, data that is not JSON or no file that opens → P39's page read (`data: … not used
    · page read next`); a short link has no post id yet, so its page is read. Data from the
    hidden page is the last word (its page was read before).
  - Tab data (step 2): one asset, `core-browser/src/main/assets/tiktok/page-data.js` (modes
    `store` and `item`). Every TikTok lookup in the browser (the page's own lookup, Download,
    For You's video on screen, Try again) asks the screen to run `item` for the post id (P36's
    on-screen id, else the address) with `evaluateJavascript` on the main thread and waits at
    most 1 s. The compact item (P40's shape) comes from `__UNIVERSAL_DATA_FOR_REHYDRATION__`
    (any `__DEFAULT_SCOPE__` key), `SIGI_STATE`, then the store; the reply carries the tab's
    `tiktok.com` cookies from `CookieManager` (never logged). Without the browser screen (no
    collector) there is no tab to read and no wait.
  - API answers (step 3): `store` runs at document start through androidx.webkit 1.12.1
    `addDocumentStartJavaScript` for `https://www.tiktok.com` and `https://m.tiktok.com`, on
    the tab and on the hidden page. It wraps `fetch` and `XMLHttpRequest` for same-site `/api/`
    paths, reads a clone after TikTok's code got its answer and keeps up to 200 compact items
    by id in a page-local object; TikTok's requests are never changed, delayed or repeated,
    its own errors are swallowed, nothing is sent to the app or the network. Without
    `DOCUMENT_START_SCRIPT` the store runs in `onPageStarted` (new
    `SecureBrowserWebViewClient.pageStartScript`; the hidden page's client does the same):
    answers TikTok's code got before that are missed.
  - Hidden page (step 4, `TT_HIDDEN_PAGE=ON`): `TikTokPageEngine` with `WebViewHiddenPages`
    (`app/.../detection/tiktok/`): an offscreen WebView on the main thread, never attached
    (1280×800), desktop Chrome agent with the WebView's Chrome version, JavaScript and DOM
    storage on, images blocked, media requests and media file types answered empty (204), only
    `https` pages of `tiktok.com` (another site or an app link stays closed), the shared cookie
    store. It loads the link (short links follow their redirects), polls `item` every 300 ms
    until the post appears or 15 s pass, then destroys the WebView (also when the lookup is
    cancelled). One at a time (`Mutex`; "hidden page: waited 0.9 s for the lookup before"). A
    check still shown after 3 s → `BOT_CHECK` "TikTok wants a check. Open the video in YFT's
    browser, then tap Download." (a check TikTok's own script passes by itself passes; YFT
    never answers one); TikTok's status for the post unchanged for 2 s is its answer (10231
    region, 10222/10223 sign-in, else private or removed); a main-frame error ends the read.
    `TT_HOME_COOKIES=OFF`: the TikTok cookies the page set are cleared when it finishes
    ("hidden page: TikTok cookies it set cleared: N") and Home stays cookie-free.
  - Order (step 5): `SiteAdapterCoordinator.inspect(…, tab)`. Browser: tab data → P39's page
    read with the tab's cookies → the tab once more (a loading page may hold the post by then)
    → hidden page → P39's player file. Home: page read (with the browser's TikTok cookies, G3)
    → hidden page → today's generic scan. Details list every step: `tab data: …`, `page read:
    RESPONSE_CHANGED · HTTP 200` and the page read's lines, `hidden page: found in N s · API
    answer · API answers kept: N` (or why not), `hidden page's data: …`. Try again runs the
    whole order; the tab is read again on every lookup, and Download after a failed TikTok
    lookup runs it again.
  - Downloads (step 6): rows from tab data carry the cookies taken with it, rows from the hidden
    page its agent and the cookies of its store at the moment the post appeared (never logged);
    P39's file checks and next-address rule stay.
  - Messages (step 7): no "Falling back to generic detection" anywhere (checked with `grep`); a
    TikTok notice shows only after every step failed, with the next thing to do (Try again, the
    check text above, P39's "does not open a video" text).
  - Plan adapted (safety review): the hidden page opens only after YFT's own request's failures
    (`RESPONSE_CHANGED`, `MALFORMED_RESPONSE`, `RESPONSE_TOO_LARGE`, `HTTP_STATUS`, `NETWORK`,
    `NO_MEDIA_FOUND`, `EXPIRED_LINK`), never after the site's own answer (private, sign-in,
    region, DRM, a check, rate limit); no check or puzzle is ever automated.
  - Plan adapted: the rows' Referer stays P39's page address on `www.tiktok.com`
    (`BrowserRequestContext` sends `pageUrl` as Referer; core-model is C's); the file checks
    send `https://www.tiktok.com/`.
- Tests (P40, JVM, +59): extractor-api `SitePageDataTest` 3 (new); extractor-sites
  `TikTokPageDataTest` 10 (new; fixtures `page_data_script_item.json` and
  `page_data_api_item.json` are what the asset returned on the instrumented fixture page in
  Chromium); app `SiteAdapterOrderTest` 13, `TikTokPageScriptTest` 10, `TikTokPageEngineTest`
  14, `TikTokHomeSessionTest` 3, `BrowserTikTokTabDataTest` 5 (all new),
  `HeadlessLinkInspectorTest` 20 (+1). Instrumented (CI emulator): `TikTokPageDataInstrumentedTest`
  7 on `app/src/androidTest/assets/tiktok/` served by the test at `https://fixture.yft.test`
  (allowed through constructor parameters only): (a) the tab gives the page script's item;
  (b) after the page's own `/api/recommend/item_list/` fetch, an API item by id; (c) the page
  got its answer unchanged; (d) the hidden page finds the post within 15 s and is destroyed,
  also for an API item; (e) a page without the post → timeout (3 s in the test), destroyed;
  another site's address never loads.
- Regression proof: with the old behaviour put back in `TikTokExtractor` (page data ignored)
  `TikTokPageDataTest` 10/10 fail; with the old order in `SiteAdapterCoordinator` (no tab step,
  no hidden page) `SiteAdapterOrderTest` 10/13 and `BrowserTikTokTabDataTest` 3/5 fail (the 5
  that pass check what stays as before: the site's own answer, other sites, no browser screen,
  the site's name); all pass on the new code (backup `/data/bak/P40/`, restored with `cp`,
  checked with `cmp`).
- Live check (2026-10-09, US sandbox, Playwright Chromium, desktop agent, images, media and
  fonts blocked; the same asset; counts and hosts only): video page
  `@scout2015/video/6718335390845095173` → found from the page's script in 0.7 s, id matches,
  5 qualities (heights 1280 and 1024), hosts `v16-webapp-prime.us.tiktok.com`,
  `v19-webapp-prime.us.tiktok.com`, `www.tiktok.com`; For You after scrolling → 4 API answers
  kept, 32 posts in the store; the first 5 by id all found (`api`), ids match, 3–5 qualities
  each (heights up to 1920), the same hosts. For You's address has no post id, as expected (the
  on-screen id comes from P36's probe). No live-check code was committed. A wider check (one
  range request per file) was stopped by the automated safety review and not repeated; the plan's
  live check asks only for the above.
- Validation (P40, 2026-10-09): Agent A command (FIX_ADD_PLAN 0.3) → BUILD SUCCESSFUL — 1313
  JVM tests, 0 failures, 66 skipped (app 867, core-browser 139, core-media 32, extractor-api 36,
  extractor-sites 239; +59 from 1254); `:app:lintDebug` 0 errors, 98 warnings (+3: lint's notice
  that a newer androidx.webkit exists; 1.12.1 is the plan's version for compile SDK 35);
  `:app:compileDebugAndroidTestKotlin` OK; line check empty.
- CI (P40, `ce04711`, the P40 checkpoint; this docs-only commit starts no CI): checkpoint
  validation [37886371700](https://github.com/Alalkipgen/YFT/actions/runs/37886371700)
  (`yft-debug-apk`), emulator smoke API 34 with `TikTokPageDataInstrumentedTest`
  [37886371675](https://github.com/Alalkipgen/YFT/actions/runs/37886371675), Preview APK
  [37886371676](https://github.com/Alalkipgen/YFT/actions/runs/37886371676) (`yft-preview-apk`)
  — all green (the WIP `53a0359` with the same tests: green too).
- Owner check (P40, pending): Agent A's Preview APK (run 37886371676 › `yft-preview-apk`, with
  the VPN) — TikTok in YFT's browser, For You: scroll through 5 videos, Download on each → the
  on-screen video's qualities every time; a video page → qualities; a `vt.tiktok.com` link on
  Home → qualities within about 10 s; a screenshot of Details for anything that fails.
- Hand-offs: Hand-off to C: core-model/src/main/kotlin/com/alal/yft/core/model/media/MediaAsset.kt
  — add `VariantResolutionResult.Failure.error: String? = null` (exception class name) and show it
  in QuickDownloadFailures details as "Error: <Class>" — so the resolver's failure names the
  class as P39 step 7 asks (the resolver already returns the step; core-model media is C's).
- Next: none for Agent A — P44 merges B → C → A, then Preview #7.

## Agent B — `work/phase-15-downloads` (P41, P42; later P44)

- Status: READY FOR MERGE (2026-10-09) — P41 and P42 done; last code commit `8ba7259`, CI green.
  OWNER ANSWERS: none (defaults `FAST_START=ON`, `DELETE_CONFIRM=ON`).
- Base commit: `d0bc7f7` (`origin/work/phase-15-integration`); folder `/data/YFT-B`.
- **P41 Result:** YouTube's whole-file tracks show bytes and speed from the first range.
  `DashTransferEngine` runs a pool of workers (the next range starts as soon as one ends),
  counts bytes as they are written (at most 4 updates a second, a retried range takes its bytes
  back, never backwards or past the total; a range is done in the checkpoint only after its
  sync), fetches a 1 MiB first range then 10 MiB ranges with 4 at once on YouTube's media hosts
  (`FAST_START`, `Policy.fastStart = true`), skips the 1-byte length probe when the length is
  known and otherwise takes it from the first range's `Content-Range`. A checkpoint saved with
  the old layout resumes with it (old fingerprint wins; the new layout has `whole-file-v2`).
  `AudioVideoMuxEngine` passes both tracks' in-range bytes on (4 a second); `DownloadQueue`
  shows a running DASH or merged task's bytes in flight while the store keeps checkpoint bytes.
  One line per download in the log (`YftDownloads`: `DASH start: plan 0.0 s · length 0.0 s
  (from first range) · first byte 0.0 s · first progress 0.0 s`, `Merged download start: …`)
  and a `Start:` line in a failure's Details. YouTube plans carry the length from `clen` or the
  player's `contentLength`.
  - Plan adapted: (1) step 6 needed no new label — since P34 the row and the notification show
    "12.3 MB · 1.2 MB/s" when the total is unknown; they stayed at 0 B because no bytes came
    before the first 10 MiB range, which P41 fixes (the merge's % appears once both sizes are
    known). (2) A resume of an unknown-length track that already has finished ranges still
    probes, so its saved layout is kept. (3) `clen`/`contentLength` are trusted only on
    `googlevideo.com` addresses. (4) The start times are stored after the detail in
    `last_error_detail` (`…|start: …`, no database change; old rows read as before).
    (5) `HlsTransferEngine` unchanged. (6) `StoredDownloadTask` may show more bytes than its
    checkpoint only for a running DASH or merged task (additive, P41).
  - Validation (2026-10-09, `--no-daemon --continue`, the five tasks): core-download 184,
    core-model 106, app 809 (66 skipped) = 1099 tests, 0 failures (+20 from 1079); `:app:lintDebug`
    0 errors (95 warnings, as before); `:app:compileDebugAndroidTestKotlin` OK; line check empty.
  - Regression proof (old `DashTransferEngine`, `AudioVideoMuxEngine`, `DownloadQueue`,
    `DownloadTaskStore`, `DownloadModels`, `DownloadPlanFactory`, `DownloadLabels` from
    `/data/bak/P41/orig`; restored with cp, cmp equal): 11 of 38 failed — DashTransferStartTest
    (first range of an unknown length is 1 MiB; a short file takes one request; a stated length
    needs no request; progress within 1 s at 64 KB/s and at 1 MB/s; never back, ends at the
    total, ≤ 4 a second; one slow range does not hold back the others), AudioVideoMuxProgressTest
    (both), StreamDownloadQueueTest "a merged task shows the bytes its tracks wrote between
    checkpoints", DashTransferEngineTest "whole file track … reports its length" (expects no
    probe now). DashFastStartLayoutTest, the new FailureDetailCodecTest/DownloadLabelsTest cases
    and DownloadPlanFactoryTest "YouTube tracks carry the length …" use the new API (do not
    compile on the old code). "a retried range is counted once" passes on both (guard).
  - Start times (throttled local server): see TEST_MATRIX "Agent B — P41, P42".
  - CI `6257307` (last P41 code commit): [checkpoint
    validation](https://github.com/Alalkipgen/YFT/actions/runs/37861112002), [emulator
    smoke](https://github.com/Alalkipgen/YFT/actions/runs/37861112033), [Preview
    APK](https://github.com/Alalkipgen/YFT/actions/runs/37861112027) all green. On `e50789c`
    the checkpoint validation stopped after 36 s before any test (the same command passed here;
    smoke and Preview APK green), so `6257307` (stopped tasks publish checkpoint bytes in one
    place) ran it again.
  - Hand-offs: none. P44 (merge) note: `DownloadFailure` gained `startTimeline` (default null)
    and `DashTransferRunner` a 6-parameter `transfer` with a default.

- **P42 Result:** finished downloads get "Delete file" (`download-menu-delete-file-<id>`) right
  after "Remove from list" (kept: it removes the row only). With `DELETE_CONFIRM` (ON) the
  question `download-delete-file-dialog` asks "Delete this file?" — "“<file name>” will be
  removed from Download/YFT and from this list. This can't be undone." — with Delete
  (`download-delete-file-confirm`) and Cancel (`download-delete-file-cancel`). The new
  `DownloadedFileDeleter` (`app/.../download/`) deletes by the record: YFT's MediaStore item
  through the content resolver (a `SecurityException` becomes `MediaStore.createDeleteRequest`
  on Android 11+, the `RecoverableSecurityException` action on Android 10; the screen launches
  it and YFT deletes again once allowed), a SAF document through `DocumentsContract`, an
  app-private file directly; a file already gone counts as deleted. Then `deleteRecord`, the
  Library refreshes (its finished-download count drops) and the snackbar says "File deleted";
  a failure says "Could not delete the file" and the row stays. `LibraryRepository.delete`
  uses the same deleter. Unfinished rows keep their menus.
  - Plan adapted: (1) "Delete file" is a menu entry of its own, not a new `DownloadAction`, so
    the existing actions and their tests stay as they are. (2) The Library's delete now says
    "deleted" for a file that is already gone (`AndroidLibraryRepositoryTest` updated; before it
    said false). (3) A record without an address is not deleted ("Could not delete the file").
    (4) `DELETE_CONFIRM` is a constant (ON); a test constructor turns it off.
  - Validation (2026-10-09, the five tasks): core-download 184, core-model 106, app 826
    (66 skipped) = 1116 tests, 0 failures (+17 from P41's 1099); lint 0 errors (95 warnings);
    androidTest compiles (new `delete/DeleteFileInstrumentedTest`); line check empty.
  - Regression proof (old `DownloadsScreen`, `DownloadsViewModel`, `DownloadLabels`,
    `LibraryRepository`, `LibraryModule`, no deleter; restored with cp, cmp equal): 2 of 5
    failed — DeleteFileMenuTest "only a finished row's menu offers Delete file beside Remove
    from list", AndroidLibraryRepositoryTest "deleting an app storage item removes only that
    file" (a gone file is deleted). DownloadsViewModelDeleteFileTest (confirm deletes the file
    then the record, cancel, `DELETE_CONFIRM=OFF`, a kept file keeps its row, Android's request
    allowed or refused, unfinished rows), DownloadedFileDeleterTest and DeleteFileScreenTest
    use the new API (do not compile on the old code). The instrumented test's JVM twin is
    DownloadsViewModelDeleteFileTest.
  - CI `8ba7259` (last code commit): [checkpoint
    validation](https://github.com/Alalkipgen/YFT/actions/runs/37862940024), [emulator
    smoke](https://github.com/Alalkipgen/YFT/actions/runs/37862940003) (runs
    `delete/DeleteFileInstrumentedTest`), [Preview
    APK](https://github.com/Alalkipgen/YFT/actions/runs/37862940023) all green.
  - Hand-offs: none.

## Agent C — `work/phase-15-ads` (P43)

- Status: P43 READY FOR MERGE (2026-10-09; `AD_RULE=STRICT`: the owner gave no answer). Last code
  commit `85f0998` (P39 resolver, owner override); docs after it only.
- Hand-off from A done: `Failure.error` + Details "Error: <Class>" (`175eca0`): the exception's
  simple class name only, set by `QuickDownloadViewModel.resolveSafely`; adding error in
  `DefaultVariantResolver`'s catch blocks: done by C (owner override), `85f0998` — A's P39
  files of `8b0bc93` plus `error = error.errorClass()` in `resolve()`'s three catch blocks
  (IOException, IllegalArgumentException, Exception); the header-probe and playlist-length
  catches still return null. Regression proof: A's `8b0bc93` resolver put back → 2 of 23
  `DefaultVariantResolverTest` failed (error null); restored with `cp`, `cmp` equal.
- P44 merge note: when `work/phase-15-tiktok` is merged (last), `DefaultVariantResolver.kt` and
  `DefaultVariantResolverTest.kt` may conflict: keep the integration (C) version. Check with
  `git diff origin/work/phase-15-tiktok -- <both files>`: the only difference must be C's error
  lines. If A changes either file after `8b0bc93`, take A's newest version and add the error
  lines again.
- Base commit: `d0bc7f7` (`origin/work/phase-15-integration`, the plan commit).
- Starting state (before edits, 2026-10-09): `./gradlew --no-daemon --continue :core-model:test
  :extractor-generic:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (9 min 34 s): 1071 tests, 0 failures,
  66 skipped (core-model 106, extractor-generic 20, core-browser 138, app 807); lint 0 issues.
- Result: on a site without an adapter one rule (`PageVideoProof`, core-model) decides at every
  step of the sheet (first choice, page's newest link, player's link, page read again, next
  video) whether a file is the page's video: named by the player setup, or its length (measured
  first when unknown) matches the page's or the failed video's. Ads: `AdHosts` (own list, network
  names under any suffix, IMA/VAST/pre-roll requests, VAST/VMAP answers), `AdSign` on candidates
  (mapper, `VastAdTracker` incl. `onAnswer`), short files on long pages. Skipped files: "That was
  an ad — showing the page's video"; nothing left: "Only an ad was found, not the page's video."
  with Reload (stand-in of the page's length). Proven ads not counted in the sheet's other videos.
- Plan adapted: P28's test "the sheet waiting for the page's video shows … then its line" offered
  a 0:30 ad on a 16:24 page; it now sets `adRule = LENIENT` (LENIENT keeps the browser's first
  choice) and a STRICT twin expects no ad offered. The size check of a stated file now keeps the
  length it read (the header shows the proven length).
- Validation (2026-10-09, after the P39 resolver): `./gradlew --no-daemon --continue
  :core-model:test :core-media:testDebugUnitTest :extractor-generic:test
  :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL: 1145 tests, 0 failures, 66 skipped
  (core-model 122, core-media 33 — new in the list for the resolver, extractor-generic 21,
  core-browser 148, app 821; 1112 without core-media, +41 from 1071); lint 0 errors (95
  warnings, unchanged); androidTest compiles; long-line check empty.
- Regression proof: the 7 files before P43 + `QuickDownloadPrerollTest` → 4 of 5 failed, each
  offering the 0:30 file; restored with `cp`, `cmp` equal (TEST_MATRIX "Agent C — P43").
- CI (`4be6563`, the last code commit): checkpoint validation success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957302), emulator smoke success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957303; 38 instrumented tests, 0 failures,
  0 FATAL — 37 before, `AdPrerollInstrumentedTest` added), Preview APK success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957301). `175eca0` (P39 hand-off):
  validation success (https://github.com/Alalkipgen/YFT/actions/runs/37869539298), emulator
  success (https://github.com/Alalkipgen/YFT/actions/runs/37869539313; tests=38 failures=0,
  0 FATAL), Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37869539343).
  `85f0998` (P39 resolver, last code commit): validation success
  (https://github.com/Alalkipgen/YFT/actions/runs/37877862671), emulator success
  (https://github.com/Alalkipgen/YFT/actions/runs/37877862760; tests=38 failures=0, 0 FATAL),
  Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37877862659).
- Owner check: the adult site of item 7 — Download on 10 videos, several with a pre-roll: the
  page's own length every time, never 0:30 ("That was an ad — showing the page's video" may
  show); Javtiful and the HTTP-410 site still download; a Details screenshot for any miss.
- Hand-offs to Agent A: (a) `BrowserScreen.kt:509` and `DetectedMediaScreen.kt:169` call
  `MediaGroups.ofPage(…, facts, hideAds = true)` so proven ads are not listed under Other videos,
  and `BrowserViewModel`'s `otherVideos` leaves out `PageVideoProof.isProvenAd` groups; (b)
  `BrowserViewModel` calls `vastAds.onAnswer(observation, contentType, bodyStart)` where the
  page's answers are read (e.g. `MediaMetadataProbe`'s non-media XML answers), so a VAST/VMAP
  body starts an ad break when the request's address says nothing.
