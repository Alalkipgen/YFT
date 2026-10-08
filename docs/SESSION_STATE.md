# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 14 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P38.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P38 only)

- Phase: 14 — Preview #5 field fixes (downloads and merges that keep going in the background
  with %, speed and time left in the notification; a faster merge; TikTok on the For You feed;
  fresh links instead of HTTP 410 on other sites). Plan: `docs/FIX_ADD_PLAN.md`; prompts:
  `docs/prompts/README.md`.
- Branches: integration `work/phase-14-integration` = `main` `5a5bddb` + the plan commit. Agent A
  `work/phase-14-background` (P34, later P38), Agent B `work/phase-14-sites` (P36, P37), Agent C
  `work/phase-14-fast-merge` (P35); all start from `origin/work/phase-14-integration`. Merge
  order A → B → C (P38), then Preview #6; P8 (signed `1.0.0-beta.4`) only with the owner's OK.
  Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #5 (2026-10-08, run 37575586233, `436aa90`): some pages of a site
  without an adapter answer HTTP 410 (manifest and MP4) until a manual reload, and Try again
  repeats it; a 1-hour YouTube merge takes about 2 minutes and stops while YFT is in the
  background; TikTok's For You feed says "No video on screen to download"; downloads should go on
  in the background with % and speed in the notification. Root causes R16–R24 in FIX_ADD_PLAN
  §4; owner decisions G1–G8 in §3 (defaults: three agents, stream-copy merge, wake lock, battery
  card, finished notice, TikTok qualities from the desktop page, two quiet re-reads).
- CI: since the plan commit, pushes that change only `docs/**` or `*.md` start no checkpoint
  validation (`paths-ignore`); "green CI" means the newest commit that changed code.
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
- Phase 13 record (per-task Results, validation, CI runs, P33 merge notes):
  `git show 5a5bddb:docs/SESSION_STATE.md`. Phase 12: `git show bc806f9:docs/SESSION_STATE.md`.
- Last pushed checkpoint: PLAN: Phase 14 (this commit, docs and the CI trigger, on
  `work/phase-14-integration`).
- Next: the owner pastes `docs/prompts/A-background.md`, `B-tiktok-fresh-links.md` and
  `C-fast-merge.md` into three new agent chats; when all three are READY FOR MERGE, Agent A runs
  `M-merge-preview6.md` (P38, Preview #6); P8 only with the owner's OK after Preview #6.
- Last updated: 2026-10-08 (Phase 14 plan)

## Agent A — `work/phase-14-background` (P34; later P38)

- Status: NOT STARTED
- P34 — Downloads and merges keep going in the background; speed in the notification: TODO
- Hand-offs: none

## Agent B — `work/phase-14-sites` (P36, P37)

- Status: P36 OWNER CHECK; P37 IN PROGRESS (started 2026-10-08; base commit `c8fcd33` =
  `origin/work/phase-14-integration`; folder `/data/YFT-B`). OWNER ANSWERS: none (defaults
  `TIKTOK_QUALITIES=DESKTOP`, `REREAD=2`).
- Starting state (2026-10-08, `c8fcd33`, Agent B scope command): 1220 tests, 0 failures, 66
  skipped (extractor-sites 196, extractor-generic 19, core-model 98, core-browser 133, core-media
  28, app 746/66 skipped); lint 0 errors (95 warnings); `:app:compileDebugAndroidTestKotlin` OK.
- P36 — TikTok: Download on the For You feed and video pages: OWNER CHECK
  - Result: `FocusedVideoProbe` reads the focused video's TikTok card when no video link is
    beside it (id from `xgwrapper-<n>-<15–22 digits>`, author from the card's `/@` link, else
    `https://www.tiktok.com/@/video/<id>`); `TikTokPageParser` reads `webapp.video-detail`, else
    `webapp.reflow.video.detail`; `TikTokExtractor` asks a phone page without qualities once
    more with `HeadlessIdentity`'s desktop agent (fallback: the phone page's address as the one
    quality; `PAGE`/Home desktop lookups ask once); the media request keeps the WebView cookie
    with the page answer's TikTok cookies replacing same-named ones and added when missing, and
    the agent that fetched that page.
  - Plan adapted: (1) live, `tiktok.com/video/<id>` without `@` redirects to `/404`, so
    `TikTokUrls` also makes author-less links (`/video/<id>`, `m.tiktok.com/v/<id>.html`)
    canonical as `/@/video/<id>`. (2) The For You card has no `/@` link today (desktop layout),
    and the phone layout the app's Chrome-like phone identity gets has no `xgwrapper` at all:
    there the script reads the id from the active slide's own page data (read only; verified
    live that its author is the slide's `/@` link). (3) A video link beside the focused video
    wins only when it names the card's id (feeds keep links of other videos nearby).
  - Live check (2026-10-08, markers only): `/foryou` 200, `__UNIVERSAL_DATA_FOR_REHYDRATION__`,
    feed drawn by script (desktop: `recommend-list-item-container` ×7–9, `feed-video` ×2,
    `xgwrapper-0-<19 digits>`, 0 `/video/` links; phone: `video-slide-active`, no
    `xgwrapper`); video page phone agent → `webapp.reflow.video.detail`, `bitrateInfo` 0;
    desktop → `webapp.video-detail`, `bitrateInfo` 5; media host 206 with the page answer's
    cookies, 403 without or with a stale `tt_chain_token`.
  - Validation (2026-10-08): 1229 tests, 0 failures, 66 skipped (extractor-sites 202,
    core-browser 136, app 746; +9); lint 0 errors (95 warnings); androidTest compiles; line
    check empty.
  - Regression proof: old four main files with the new tests → 11 failures (TEST_MATRIX
    "Agent B — P36, P37"); restored from `/data/bak/P36`, `cmp` equal.
  - CI: (after the push)
  - Owner check (VPN; TikTok is banned in India): `/foryou` → Download → qualities → the file
    plays; a profile's video; a pasted link on Home.
- P37 — Other sites: fresh links instead of HTTP 410: IN PROGRESS
- Hand-offs: none

## Agent C — `work/phase-14-fast-merge` (P35)

- Status: NOT STARTED
- P35 — Faster merge for long videos: TODO
- Hand-offs: none
