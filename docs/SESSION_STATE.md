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

- Status: IN PROGRESS (P28) — started 2026-10-07 in `/data/YFT-B`, base commit `7873d51`
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
- P28 — Other sites: the page's video, not the ad before it: IN PROGRESS
- P29 — Other sites: the next video when one fails: TODO
- Hand-offs: none

## Agent C — `work/phase-13-browser` (P30, P31, P32)

- Status: NOT STARTED
- P30 — Browser: Google search: TODO
- P31 — Browser history: TODO
- P32 — Block pop-ups and ad redirects: TODO
- Hand-offs: none
