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

- Status: NOT STARTED
- P36 — TikTok: Download on the For You feed and video pages: TODO
- P37 — Other sites: fresh links instead of HTTP 410: TODO
- Hand-offs: none

## Agent C — `work/phase-14-fast-merge` (P35)

- Status: IN PROGRESS
- P35 — Faster merge for long videos: IN PROGRESS (started 2026-10-08, base `c8fcd33`)
- Starting state (2026-10-08, `c8fcd33`, Notion sandbox with Gradle `-Xmx1280m
  -XX:MaxMetaspaceSize=640m` and a 4 GiB swap file): `./gradlew --no-daemon --continue
  :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; core-download 150, core-model 98, app 746
  tests (66 skipped), 0 failures; lint 0 errors.
- Work in progress: the stream copy (MP4 reader, writer, MediaExtractor check, MediaMuxer
  fallback), the quick wins on today's path, the per-merge log line and their JVM tests are in
  (core-download 166 tests green); `StreamCopyMergeInstrumentedTest` compares the stream copy
  with today's way on the CI emulator (first run, `ff59e2c`: 1-hour-sized input 16.0 s → 1.33 s,
  20 minutes 4.05 s → 0.42 s; its start check failed because MediaMuxer moves a B-frame video's
  start by 59 ms, so the times are now checked against the inputs; second run `0a18c19`: video
  equal in all three files, the inputs' sound sync flags only mark each fragment's first
  sample, so sound is compared with today's file). Next: a green emulator run, the docs.
- Hand-offs: none
