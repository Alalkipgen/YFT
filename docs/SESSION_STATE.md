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

- Status: P39 IN PROGRESS (started 2026-10-09), P40 TODO.
- Base commit: `d0bc7f7` (= `origin/work/phase-15-integration`, the plan commit); folder
  `/data/YFT-A`; push over SSH with the key named in `/data/.ssh/CURRENT_KEY`.
- Starting state (Agent A scope, before any edit; the full command of FIX_ADD_PLAN 0.3 for
  Agent A): BUILD SUCCESSFUL — 1207 JVM tests, 0 failures, 66 skipped (app 807, core-browser
  138, core-media 28, extractor-api 32, extractor-sites 202); lint 0 errors, 95 warnings;
  androidTest Kotlin compiles.
- Progress (WIP checkpoints skip tests; the full validation runs before the P39 checkpoint):
  extractor-api additions (file check `probe`, request `details`, adapter `message`, JSON read
  result); OkHttpExtractorClient safe headers, catch-all, redirect cookie jar, file check;
  TikTok parser (page kinds, data shapes, lenient read, qualities), TikTokAgents, TikTok adapter
  (phone + desktop pages, file checks, watermark rule, Details) — main code compiles; coordinator
  (error class, adapter message, new RESPONSE_CHANGED text); PageVideoLookup.details shown by
  lookupState (browser page/focused lookups, Home). TikTok adapter tests rewritten on live-shaped
  fixtures (phone reflow, desktop video-detail, home page) with file-check fakes; regression,
  agents, URL, JSON-read tests (extractor tests 229 + 33 pass); app tests for the client (safe
  headers, jar over a real TLS redirect, file check, errors), coordinator, lookup details,
  resolver (TikTok-shaped MP4, caller's thread, unexpected error) — all pass. Step 8 code: the
  probe's `currentSrc`, PlayerFileFallback (TikTok player files per page → MAIN row + banner)
  and its tests. Regression proof on main's code done (adapter 5, client 4, fallback 3 and the
  resolver's 2 new tests fail there). Next: live check, validation, docs.
  DefaultVariantResolver: requests on Dispatchers.IO, every unexpected error -> failure at its step.
  Live look 2026-10-09 (one public video, both agents, page answers only): phone 200 · 150 KB ·
  `webapp.reflow.video.detail` with playAddr only (576×1024); desktop 200 · 389 KB ·
  `webapp.video-detail` with 5 gears (540p H.264 ×3, 720p H.265, 540p H.265), each with
  v16/v19-webapp-prime hosts and the www.tiktok.com/aweme/v1/play address; downloadAddr empty.
  File checks (Range bytes=0-0, desktop agent, the page answer's cookies, Referer www.tiktok.com):
  540p H.264 and 720p H.265 first address 206 (exact sizes 2953029 / 2004627 = DataSize); the
  aweme/v1/play address redirects to v16-webapp-prime.us.tiktok.com and answers 206 too.
- Hand-offs: Hand-off to C: core-model/src/main/kotlin/com/alal/yft/core/model/media/MediaAsset.kt
  — add `VariantResolutionResult.Failure.error: String? = null` (exception class name) and show it
  in QuickDownloadFailures details as "Error: <Class>" — so the resolver's failure names the
  class as P39 step 7 asks (the resolver already returns the step; core-model media is C's).

## Agent B — `work/phase-15-downloads` (P41, P42; later P44)

- Status: P41 TODO, P42 TODO.
- Base commit: —
- Results, validation, regression proof, measured start times, CI links, hand-offs: —

## Agent C — `work/phase-15-ads` (P43)

- Status: P43 TODO.
- Base commit: —
- Results, validation, regression proof, CI links, hand-offs: —
