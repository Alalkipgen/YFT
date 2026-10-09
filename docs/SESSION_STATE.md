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
- Last pushed checkpoint: PLAN: Phase 15 (docs only, no CI) on `work/phase-15-integration`.
- Next: the owner pastes `A-tiktok.md`, `B-downloads.md` and `C-ads.md` into three agent chats;
  when all three are `READY FOR MERGE`, `M-merge-preview7.md` in Agent B's chat (P44).
- Last updated: 2026-10-09 (plan)

## Agent A — `work/phase-15-tiktok` (P39, P40)

- Status: P39 DONE (2026-10-09), P40 TODO (not started: the owner asked this chat to stop after
  P39).
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
- Hand-offs: Hand-off to C: core-model/src/main/kotlin/com/alal/yft/core/model/media/MediaAsset.kt
  — add `VariantResolutionResult.Failure.error: String? = null` (exception class name) and show it
  in QuickDownloadFailures details as "Error: <Class>" — so the resolver's failure names the
  class as P39 step 7 asks (the resolver already returns the step; core-model media is C's).
- Next: P40 (TikTok from TikTok's own page), when the owner starts it.

## Agent B — `work/phase-15-downloads` (P41, P42; later P44)

- Status: P41 TODO, P42 TODO.
- Base commit: —
- Results, validation, regression proof, measured start times, CI links, hand-offs: —

## Agent C — `work/phase-15-ads` (P43)

- Status: P43 TODO.
- Base commit: —
- Results, validation, regression proof, CI links, hand-offs: —
