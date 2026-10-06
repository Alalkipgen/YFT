# YFT Fix & Add Plan — Phase 13 (Preview #4 polish: other sites' ads, YouTube merge, browser)

Owner phone test of **Preview #4** (2026-10-07, Xiaomi phone with HyperOS; Preview APK run
https://github.com/Alalkipgen/YFT/actions/runs/37530061595, commit `bc806f9` = `main`), compared
with Snaptube on the same pages: "about 90% fine". Phase 12 (P20–P26: video downloads save
again, Retry and failure details, every YouTube and Facebook quality, other sites' main video,
one sheet everywhere) is merged into `main`. The remaining 10% are minor cases: on a video site
without an adapter the sheet opens the **pre-roll ad** instead of the page's video, long YouTube
downloads **wait a long time at 99%**, and the browser needs **Google search, a history and a
stop to ad redirects**. Phase 13 fixes these with **three agents working at the same time**
(A, B, C, §0.7), then one merge and Preview #5. Written in Plan Mode on 2026-10-07; work starts
when the owner pastes the prompts in [`prompts/`](prompts/README.md). Phase 12's plan and
prompts stay in Git history: `git show bc806f9:docs/FIX_ADD_PLAN.md` and
`git show bc806f9:docs/prompts/` (summary in [§8](#8-done-before-phase-13)).

## Contents

0. [How to work](#0-how-to-work) (0.7: [three agents in parallel](#07-three-agents-in-parallel))
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Tasks](#5-tasks)
6. [Owner phone checklist](#6-owner-phone-checklist)
7. [Backlog](#7-backlog)
8. [Done before Phase 13](#8-done-before-phase-13)

## 0. How to work

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out **your agent's branch** (§0.7) in **your own folder** (Notion sandbox:
   `/data/YFT-A`, `/data/YFT-B` or `/data/YFT-C`, see the prompts) and pull it. On the first
   start create it from `origin/work/phase-13-integration` (the plan commit on top of `main`
   `bc806f9`). Never work on another agent's branch, on `work/phase-13-integration` (only P33
   does) or on `main`.
3. Read §0, §3, §4, your tasks in §5 with their **Read first** files, and your own section of
   `docs/SESSION_STATE.md`.
4. Set up the environment when it is missing (§0.3), then run your scope's validation before
   editing, so you know the starting state.
5. Record the task as `IN PROGRESS` in **your section** of `docs/SESSION_STATE.md`. Agents A, B
   and C never edit this plan file (`docs/FIX_ADD_PLAN.md`) or `docs/prompts/`: P33 copies
   status and Results from SESSION_STATE into §1 and §5.
6. Do the **Steps** in order. Stay inside the task and inside your files (§0.7); anything else
   goes to your SESSION_STATE section as a hand-off or a backlog note. When the code shows that a
   step is wrong, adapt it and say so in the Result ("Plan adapted: …").
7. Add the listed tests. A regression test must fail on the old code (§0.3, regression proof).
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists (only your sections of shared docs, §0.7), write the Result in
   your SESSION_STATE section (status `DONE (date)` or `OWNER CHECK`) and checkpoint with
   `scripts/checkpoint.sh "P2x: summary"` (or `P3x:`). Check CI for the pushed commit and fix a
   red run.
10. Report to the owner in Burmese (§0.5), then continue with your next task without waiting.
    After your last task set your section to `READY FOR MERGE` and stop. Stop earlier only for a
    failure you cannot fix, a decision §3 marks `PENDING`, or a hand-off that blocks you.

### 0.2 Rules for every task

- Product rules ([ADR-006](decisions/ADR-006-owner-override-any-working-method.md), owner
  2026-10-03): any working technique for public videos; no DRM, paywall, private-content or
  age-gate bypass; adapters never sign in. A page's age or identity check is the user's own tap
  in the browser; agents never automate one, not even in a live check.
- Never log, print, commit or put into fixtures: cookies, tokens, visitor data, `Authorization`
  values, signed media or image URLs (CDN query strings), keystores, `local.properties` or
  `.env`. Fixtures keep hosts and paths and replace signed query values with `REDACTED`; logs and
  lookup details name hosts, not full addresses.
- Kotlin lines stay within 100 characters. Keep every existing `testTag` (new tags are named in
  the task). No unrelated refactors. Public functions and types that another agent's code uses
  change only by addition (§0.7 "Contracts").
- `WebView` and `WebSettings` methods run on the main thread only; `shouldInterceptRequest` and
  `@JavascriptInterface` methods run on other threads.
- Site tasks need a live check of a public page (`scripts/live-check.sh`); report status, host,
  path, sizes and markers only. The sandbox is a data-centre network: YouTube often answers it
  with a bot check, so the owner's phone is the final proof for YouTube. TikTok is banned in
  India, so the owner cannot check it on his phone (§7 B4).
- Sites for adults (P28, P29, P32): public pages only. An age or identity gate is the user's own
  tap on his phone; an agent never clicks one, not even in a live check, and skips a page that
  shows one. Fixtures keep the page structure (players, scripts, ad frames) but replace titles,
  names, descriptions and pictures with neutral text and blank images; nothing explicit enters
  the repository, logs or reports (report hosts, lengths, heights and counts only).
- Push access: the Notion sandbox loses its deploy key on every reset. Never search for an old
  key: make a new one (`ssh-keygen -t ed25519`), give the owner the public line for the
  repository's Deploy keys (write access) and wait for his OK before the first push.
- The agent sandbox has no emulator; use the CI emulator job (`emulator-smoke.yml`, API 34) for
  real WebView, MediaStore and MediaCodec/MediaMuxer checks.
- Network tasks stay polite: no more requests than the task allows, retries only as P10 defined
  them, never two lookups of the same video at once.
- Keep temporary files and backups outside the repository (`/data/tmp`, `/data/bak`):
  `scripts/checkpoint.sh` stages with `git add -A`. Never undo work with `git reset --hard`,
  `git clean` or `git stash`. Do not edit `.github/workflows/`.

### 0.3 Validation

**Environment.** JDK 17; Android SDK with platform 35, build-tools 35.0.0 and platform-tools;
NDK `27.3.13750724` and CMake `3.22.1` (`core-download` builds LAME for MP3). On a new machine,
with JDK 17 in `JAVA_HOME` and the Android command-line tools in
`$ANDROID_HOME/cmdline-tools/latest`:

```bash
export ANDROID_SDK_ROOT="$ANDROID_HOME"
yes | sdkmanager --licenses > /dev/null
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
  "ndk;27.3.13750724" "cmake;3.22.1"
```

In the Notion sandbox run `source /data/yft-env.sh` first: it sets `JAVA_HOME`
(`/data/toolchains/jdk17`), `ANDROID_HOME` and `ANDROID_SDK_ROOT` (`/data/toolchains/android-sdk`),
`GRADLE_USER_HOME` (`/data/gradle-home`) and `PATH`; `/data/gw.sh <tasks>` runs
`./gradlew --no-daemon --max-workers=2` in `/data/YFT`. After a sandbox reset, recreate both
files and install what is missing (NDK and CMake went missing twice in Phase 11; an empty
`/data/gradle-home` only means the first build downloads its dependencies). Agents may share
one computer: each works in its own folder (`/data/YFT-A`, `-B`, `-C`; `/data/gw.sh` only runs in
`/data/YFT`, so run `./gradlew` in your folder), stops only Gradle daemons it started (no
`pkill` of another agent's build) and, on a 4 GiB machine, waits until no other Gradle build
runs (`pgrep -af "[G]radleDaemon"`): one Gradle command at a time on the computer. The full
validation needs Gradle metaspace 640 MiB (Phase 11 merge: the default hit a Metaspace OOM):
`export GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"` or
`-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=640m"`.


**SSH key.** Pushes use SSH. When the computer has no key in `/data/.ssh/`, make a **new** one
(`ssh-keygen -t ed25519 -N "" -f /data/.ssh/id_ed25519 -C "yft-<agent>-<date>"`), show the
public line to the owner and wait until he says he added it as a deploy key with write access.
Never search the computer for old keys. Then
`git config core.sshCommand "ssh -i /data/.ssh/id_ed25519 -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"`
and `git remote set-url origin git@github.com:Alalkipgen/YFT.git` in your folder.

| Scope | Command |
| --- | --- |
| Agent A (P27) | `./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent B (P28, P29) | `./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent C (P30, P31, P32) | `./gradlew --no-daemon --continue :core-browser:testDebugUnitTest :core-data:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Full (P33, P8) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` |
| Scripts, docs-only checkpoints | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests` |
| Line length (must print nothing) | `git diff -U0 origin/main -- '*.kt' '*.kts' \| grep '^+[^+]' \| LC_ALL=C.UTF-8 awk 'length > 101'` |

Phase 13 start (P26, 2026-10-06, `bc806f9`): full validation 1292 tests, 0 failures, 66 skipped;
lint 0 errors; `:app:assembleRelease` OK.


**Regression proof.** Copy the files you changed to `/data/bak/<task>/`, put the old version
back (for example `git show HEAD:<path> > <path>` before your commit), run the new tests and see
them fail, then restore your version with `cp` and check it with `cmp`. Name the tests that
failed on the old code in the Result. An instrumented test (`app/src/androidTest`) cannot run in
the sandbox: its JVM twin is the regression proof, and the CI emulator run must pass with the fix.

**CI.** `checkpoint-validation` runs on every `work/**` push. `emulator-smoke` (API 34,
`:app:connectedDebugAndroidTest`) and `preview-apk` run on `work/phase-*` pushes that change code
(`app/**`, `core-*/**`, `extractor-*/**`, Gradle files). Status for a branch:

```bash
curl -s "https://api.github.com/repos/Alalkipgen/YFT/actions/runs?branch=<your branch>&per_page=6" \
  | jq -c '.workflow_runs[] | {name, sha: .head_sha[0:7], status, conclusion, url: .html_url}'
```

Three agents push at the same time, so hosted runners may queue for a while; wait for your own
commit's runs (`head_sha`) and never re-run another agent's workflow.


### 0.4 Status values

`TODO` · `IN PROGRESS` · `BLOCKED (reason)` · `OWNER CHECK` (done on the branch, waiting for the
owner's phone) · `DONE (date)` · `SKIPPED (decision)` · `READY FOR MERGE` (an agent's last task
is done and its CI is green). Checkpoint messages start with the task ID, so
`git log --oneline --grep "P27:"` finds a task's commits.

### 0.5 Report to the owner (Burmese, short)

```text
Agent: A / B / C — Task: P2x / P3x — <title> — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run links> (debug APK: Artifacts › yft-debug-apk;
  preview: Preview APK run › yft-preview-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: … (hand-offs to other agents, if any)
နောက်တစ်ဆင့်: P2y / P3y / READY FOR MERGE
```

### 0.6 Phone builds for the owner

Every green checkpoint run uploads `yft-debug-apk` (14 days; app ID `com.alal.yft.debug`). Every
push that changes code also runs **Preview APK (test key)**, which uploads `yft-preview-apk`: the
minified release build as "YFT Preview" (`com.alal.yft.preview`), signed with a test key made in
that job, so the owner uninstalls the older YFT Preview before installing a newer one. Each
agent's branch gets its own preview runs; the owner may try one early, but the phone test that
counts is **Preview #5** = the Preview APK run of P33's merge commit on
`work/phase-13-integration` (§6). Only P8 signs with the release key.

### 0.7 Three agents in parallel

**Branches.** All three start from `origin/work/phase-13-integration` at the plan commit.

| Agent | Tasks (in order) | Branch | Area |
| --- | --- | --- | --- |
| A | P27, later P33 (integrator) and P8 | `work/phase-13-merge-speed` | Download engines (merge), Downloads screen and notification |
| B | P28 → P29 | `work/phase-13-generic-main` | Other sites' detection, page facts, the sheet's choice of video |
| C | P30 → P31 → P32 | `work/phase-13-browser` | Browser: search engine, history, pop-up and redirect blocking, settings |
| A (P33) | merge A → B → C, Preview #5 | `work/phase-13-integration` | Integration only |

Agent A has the shortest track (P27); it then waits for B and C and does P33. In two-agent mode
(below) A also does C's tasks.

**Files each agent may change** (tests beside them under `src/test/` or `src/androidTest/`
included). Everything not listed belongs to nobody: change it only through a hand-off.

| Agent | Owns |
| --- | --- |
| A | `core-download/**`; `core-model/src/main/kotlin/com/alal/yft/core/model/download/**`; `app/src/main/java/com/alal/yft/download/**`; `app/src/main/java/com/alal/yft/feature/downloads/**`; `app/src/androidTest/java/com/alal/yft/download/**`; `app/build.gradle.kts` only for an `androidTestImplementation` line |
| B | `core-model/src/main/kotlin/com/alal/yft/core/model/media/**`; `core-browser/src/main/java/com/alal/yft/core/browser/{detection,session}/**`; `core-media/**`; `extractor-generic/**`; `app/src/main/java/com/alal/yft/feature/{quickdownload,home,detectedmedia}/**`; in `app/.../feature/browser/` only `BrowserViewModel.kt`, `BrowserUiState.kt`, `BrowserDownloadFab.kt`; `app/src/main/java/com/alal/yft/ui/format/**`; `app/src/androidTest/java/com/alal/yft/browser/detection/**` (new); `core-browser/src/test/resources/fixtures/p28-*`; docs `design/DESIGN-NOTES.md`, `SUPPORT_MATRIX.md` |
| C | `core-browser/src/main/java/com/alal/yft/core/browser/{webview,policy}/**`; `core-data/**` (one Room migration: 5 → 6); `core-model/src/main/kotlin/com/alal/yft/core/model/settings/**`; `app/src/main/java/com/alal/yft/feature/browser/**` except B's three files; `app/src/main/java/com/alal/yft/feature/settings/**`; `app/src/main/java/com/alal/yft/ui/theme/YftIcons.kt` (additions only); `app/src/androidTest/java/com/alal/yft/browser/navigation/**` (new) |
| Nobody (P33/P8 only) | `.github/workflows/**`, `gradle.properties`, `gradle/libs.versions.toml`, root and module Gradle files (except A's line above), `app/src/main/res/**`, `app/src/main/AndroidManifest.xml`, other `app` packages (`detection`, `feature/library`, `thumbnail`, `diagnostics`, `ui/navigation`, `ui/components`, `MainActivity`), existing androidTest files outside your folders (`browser/FocusedVideoProbeInstrumentedTest.kt`, `smoke/**`), shared test helpers (`app/src/test/java/com/alal/yft/testing/**`: add new helpers in your own test folders), `AGENTS.md`, `README.md`, `docs/FIX_ADD_PLAN.md`, `docs/prompts/**`, `docs/HANDOFF.md`, `docs/PHASE_STATUS.md`, `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROJECT_CONTEXT.md`, ADRs |

**Shared docs — own section only.** Each agent edits only the section with its letter, which
the plan commit created; nothing above or below it:

- `docs/SESSION_STATE.md`: `## Agent A …`, `## Agent B …`, `## Agent C …` (status, Results,
  validation, CI links, hand-offs). The `## Overview` section is P33's.
- `CHANGELOG.md` under `## [Unreleased]`: `### Phase 13 — Agent A (P27)`, `### Phase 13 — Agent
  B (P28, P29)` and `### Phase 13 — Agent C (P30, P31, P32)`. Replace the placeholder line, then
  add bullets below it. P33 folds them into Added/Changed/Fixed.
- `docs/TEST_MATRIX.md`: `### Agent A — P27` (and B, C) under `## Phase 13`.

**Contracts** (code other agents use; change only by adding, with defaults; never rename,
remove or change a meaning):

- A → B, C: `DownloadEnqueuer`, `DownloadPlanFactory`, the public `DownloadQueue` API,
  `DownloadTask`, `DownloadProgress`, `DownloadFailure`, `DownloadFailureReason`,
  `AudioVideoMuxStage` (new values may be added; the sheet and the foreground service read them).
- B → C: `BrowserViewModel`'s public functions and `BrowserUiState` (C's `BrowserScreen` calls
  and reads them); `MediaCandidate`, `MediaGroups`, `BrowserRequestContext`.
- C → B: `BrowserObservationSink` (new methods only with default bodies), the calls
  `SecureBrowserWebViewClient` makes today (requests, page start and finish, DOM probe, playing
  probe) keep their order and threads; `BrowserSearch.webUrl(words)` keeps its signature (B's
  `BrowserViewModel` calls it for typed words); the settings models (`core-model/.../settings`)
  change only by adding fields with defaults.
- Ad lists: B extends the media ad list in `core-browser/.../detection/BrowserObservationMapper`
  (ad files: never the page's video); C writes a separate navigation list in
  `core-browser/.../policy/` (pop-up and redirect hosts). One shared list is backlog (§7).

**Hand-offs.** When you need a change in a file you do not own, do not make it. Write
"Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and in your
report; the owner passes it on. Prefer a fix inside your own files when one exists.

**Merge (P33).** Agent A merges the three branches into `work/phase-13-integration`
(`--no-ff`) in this order: `work/phase-13-merge-speed` (A), then `work/phase-13-generic-main`
(B), then `work/phase-13-browser` (C); it runs that agent's scope validation after each merge
and the full validation at the end. Shared docs conflict only if a section rule was broken:
keep both sides. A code conflict means an ownership slip: stop and report the files.

**Two agents instead of three** (if the owner prefers): Agent A owns A's and C's files and does
P27 → P30 → P31 → P32 → P33; Agent B does P28 → P29. The prompts work unchanged: the owner pastes
[`A-merge-speed.md`](prompts/A-merge-speed.md) into Agent A's chat and, after P27,
[`C-browser.md`](prompts/C-browser.md) with `BRANCH_OVERRIDE: work/phase-13-merge-speed`; P33
then merges A's branch (with C's work) and B's.

## 1. Status board

Agents A, B and C record status in their own section of `docs/SESSION_STATE.md`; P33 copies it
here. AI agent time includes builds and CI waits on a 4 GiB sandbox.

**Agent A — `work/phase-13-merge-speed`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P27 | [YouTube: no long wait at 99% (merge straight into the file, show Merging · N%)](#p27--youtube-no-long-wait-at-99) | Medium | 3–5 h | — | TODO |

**Agent B — `work/phase-13-generic-main`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P28 | [Other sites: the page's video, not the ad before it](#p28--other-sites-the-pages-video-not-the-ad-before-it) | Hard | 6–10 h | — | TODO |
| P29 | [Other sites: the next video when one fails; the page's title and picture](#p29--other-sites-the-next-video-when-one-fails) | Medium | 2–4 h | P28 | TODO |

**Agent C — `work/phase-13-browser`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P30 | [Browser: Google search by default, engine choice in Settings](#p30--browser-google-search) | Easy | 1–2 h | — | TODO |
| P31 | [Browser history](#p31--browser-history) | Medium | 3–5 h | P30 (settings section) | TODO |
| P32 | [Block pop-ups and ad redirects](#p32--block-pop-ups-and-ad-redirects) | Medium–Hard | 4–7 h | P30 (settings section) | TODO |

**Integration — Agent A, `work/phase-13-integration`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P33 | [Merge A → B → C, full validation, Preview #5](#p33--merge-and-preview-5) | Medium | 2–3 h | P27–P32 READY FOR MERGE | TODO |
| P8 | [Signed release 1.0.0-beta.4](#p8--signed-release-100-beta4) | Easy | 1–2 h | P33, Preview #5, owner OK | TODO |

In parallel the wall time is about 10–14 h (B and C are the long tracks) plus P33; one agent
alone would need 20–33 h.

## 2. What the owner saw

Preview #4 (2026-10-07), owner's words in short: "about 90% fine; the rest are minor cases".

1. **Other site (an adult video site without an adapter, browser).** While the page's pre-roll
   ad plays, Download opens the sheet with the **ad**: title "Video", no picture, length 0:30,
   "1080p · Full HD 9.1 MB" (MP4), Audio M4A ~512 KB, "Other videos on this page (4)". The real
   video (16:24, "720p · HD", HLS, ~213 MB) appears only under Other videos, or as the main
   video after the owner skips the ad and plays the video for a moment. On another page the
   sheet showed the page's title with "The site no longer has this video (HTTP 410)" and
   "Other videos on this page (3)". Snaptube on the same page is slower (YFT is 2–3× faster)
   but opens the real video at once: the page's title and picture, "Fast (480p)" and "High
   quality (720p)" — never the ad.
2. **YouTube downloads wait at 99%.** Long recordings of old live streams (480p 1.3 GB and
   783 MB; 720p 179–460 MB) reach 99% and then stay there for a long time before they finish.
   A video of the same size from the other site does not wait; Snaptube does not either. All
   finished and play (Downloads screenshot, Completed today).
3. **Browser.** The search page is DuckDuckGo; the owner wants Google. There is no browser
   history. On some sites a tap on the page or on an ad sends the tab to spam pages (ad
   redirects); blocking those is wanted — not blocking the video ads themselves.

## 3. Owner decisions

Defaults below are what the agents do unless `OWNER ANSWERS` in the prompt says otherwise.

| ID | Question | Options | Default |
| --- | --- | --- | --- |
| F1 | How should other sites find the page's video instead of the ad? | **A+** — trust the page over the player: the page's stated length, title and picture; common player setups; an ad recognised by its length and its ad server; a short wait (≤ 6 s) for the real video while only an ad is known; the next video when one fails (P28, P29). **B** — wait until the ad ends, like a person (slow, breaks when ads change). **C** — adapters for named adult sites, like Snaptube's (closest to Snaptube, but outside the support scope `SUPPORT_MATRIX.md` gives such sites; several days plus upkeep) | **A+** (`GENERIC=A`) |
| F2 | Search engine | Google default; Settings › Browser › Search engine: Google, DuckDuckGo, Bing | Google |
| F3 | Browser history | Saved by default (90 days, at most 5,000 pages, HTTPS pages only, no cookies); Settings › Browser: "Save browser history" switch and "Clear browser history"; Settings' "Clear browsing data" clears it too | On |
| F4 | Pop-ups and ad redirects | Blocked by default with a small notice and "Open" to allow that one; Settings › Browser: "Block pop-ups and ad redirects" switch; video ads inside pages are not blocked | On |
| F5 | YouTube merge | Write the merged file straight into Download/YFT when Android allows (8.0+), else today's way; the card says "Merging · N%" and "Saving · N%" | Yes |
| F6 | Number of agents | 3 at once (A, B, C) or 2 (A takes C's tasks) | 3 |

## 4. Findings and root causes

Code read on `bc806f9` (Plan Mode, 2026-10-07). Confidence in brackets; agents confirm each
finding before they change code.

- **R7 — the playing ad wins (item 1, high).** `MediaGroups.mainVideo` (core-model,
  `MediaGroups.kt` about lines 134–158) takes the **playing element's address first**, then its
  length, before any page signal. During a pre-roll the playing `<video>` is the ad, so the
  ad's MP4 becomes the main video. A file the page names as its own (`PageMediaRole.MAIN`) only
  protects itself in `looksLikePreview`; it does not outrank the playing element.
- **R8 — the page's stated length is lost (item 1, medium–high).** `HtmlMediaScanner` reads a
  JSON-LD `VideoObject`'s `duration` only for its `contentUrl` / `embedUrl` when that is a media
  file. A page whose VideoObject names an embed page (not a file) — as on the owner's site —
  keeps its 16:24 nowhere. So the ad (0:30) is not recognised as far too short for this page:
  `looksLikePreview` needs another video of known length ≥ 1 min or a MAIN role, and neither
  exists until the real manifest has been read. The DOM probe in the browser does not read the
  page's meta or JSON-LD at all.
- **R9 — ad servers of free video sites are unknown (item 1, medium).** The ad list
  (`BrowserObservationMapper` `AD_HOSTS`, `AD_HOST_LABELS`, `AD_FOLDERS`) knows mainstream
  networks (doubleclick, imasdk, …) but not the networks of free and adult video sites, and an
  ad's VAST/VMAP description is never used to mark the files it names. To be confirmed by a
  live check (which hosts served the 0:30 file).
- **R10 — no second try (item 1, high).** When the chosen video cannot be prepared (HTTP 410,
  404 or 403: an expired ad file or a one-time address), the sheet stops at the error although
  "Other videos" has a working video; "Try again" asks the same dead address again.
- **R11 — no title or picture (item 1, medium).** The sheet shows "Video" and a blank picture
  for a page that states `og:title` and `og:image` (Snaptube shows both); only some paths carry
  the page title.
- **R12 — 99% is the merge, done twice and silently (item 2, high on the mechanism).** After
  both YouTube tracks are downloaded, `AudioVideoMuxEngine` copies every sample into a new file
  in app storage (`mux` → `copySamples`, MediaExtractor → MediaMuxer), then copies that whole
  file again into `Download/YFT` (`publish`, 64 KiB buffer, then `sync`). Nothing of this has
  progress: the `MUXING` stage (`AudioVideoMuxStage`) is shown nowhere in the app, so the card
  sits at 99%. For a 1.3 GB recording that is about 4 GB of storage reads and writes after
  "99%", and up to three copies of the video on the phone at once. The other site's HLS 720p is
  written straight into its final file (no merge), so it ends at once. How the time splits
  between demuxing, muxing and copying is to be measured (P27 step 1).
- **R13 — DuckDuckGo is hard-coded (item 3, high).** `BrowserSearch.webUrl` builds
  `https://duckduckgo.com/?q=…`; the start page and typed words in the address bar both use it.
- **R14 — no history (item 3, high).** The browser keeps only the WebView's own back/forward
  list (`BrowserScreen` `refreshHistoryState`); nothing is saved, listed or cleared.
- **R15 — ads can take over the tab (item 3, high).** `SecureWebViewPolicy` turns multiple
  windows off (`setSupportMultipleWindows(false)`), so a page's `window.open()` and
  `target="_blank"` load **in the same tab**, and `SecureBrowserWebViewClient.
  shouldOverrideUrlLoading` lets every HTTPS top-level navigation through, with or without the
  user's tap. An ad's tap handler or a pop-under script therefore replaces the page with the
  ad's site.

## 5. Tasks

Each task: level · AI agent time · needs · agent · prompt; then **Goal**, **Read first**,
**Steps**, **Tests**, **Docs**, **Done when**, **Owner check** and **Result** (filled by P33
from the agent's SESSION_STATE section).

### P27 — YouTube: no long wait at 99%

Medium · 3–5 h · needs — · **Agent A** · prompt [`A-merge-speed.md`](prompts/A-merge-speed.md)

**Goal:** a merged YouTube download (separate video and audio tracks) finishes soon after its
tracks are downloaded, and the card always says what it is doing — never a silent 99% (R12).

**Read first:** `core-download/.../AudioVideoMuxEngine.kt` (`AndroidLocalMuxer.mux`,
`copySamples`, `transfer`, `publish`, `MuxCheckpointTracker`), `core-model/.../download/
DownloadModels.kt` (`AudioVideoMuxStage`, `DownloadProgress`), `DownloadQueue` (progress into
the record), `PublicDownloadDestination.kt` and `DownloadDestination.kt` (how a destination
opens its file), `app/.../feature/downloads/DownloadLabels.kt` (`progressDetail`),
`DownloadsScreen.kt`, the foreground notification in `app/.../download/**`,
`app/src/androidTest/.../download/AudioVideoMuxerInstrumentedTest.kt`.

**Steps**
1. **Measure first.** Time each step of a merged download — video track, audio track, mux
   (demux + write), copy into the destination, sync, commit — and keep the times in the task's
   log lines (no addresses) and in the failure detail when a merge fails. On the CI emulator,
   an instrumented test merges a long generated input (at least 20 minutes, a small bitrate,
   many samples; fragmented MP4 like YouTube's DASH files if the test can make one) and prints
   the split. Record the numbers in the Result.
2. **Progress after the download.** The mux reports progress (sample time written ÷ the
   tracks' duration) and the copy reports bytes; the record keeps the stage. The Downloads card
   and the notification say "Merging audio and video · 45%" and then "Saving to Download/YFT ·
   80%" (testTag `download-stage-<id>`); the bar keeps moving instead of stopping at 99%.
3. **Merge straight into the final file.** On Android 8.0+ (API 26, `MediaMuxer(FileDescriptor,
   format)`), when the destination can give a seekable read-write file descriptor (a pending
   MediaStore row, a file in app storage; a SAF document only when its descriptor is
   seekable), mux directly into it, then check its length and commit — no second copy. Add this
   to `DownloadDestination` by addition (a default that says "not available"). Otherwise, and
   on Android 7.x, keep today's path with a larger copy buffer (1 MiB). If the direct mux fails
   before writing its first sample, fall back once to today's path.
4. **Space.** Before merging, check the free space the chosen path needs (direct: the output ≈
   video + audio; today's path: twice that) and fail early with `INSUFFICIENT_STORAGE` at stage
   `MERGE` instead of at the end; delete each track file as soon as the merge has succeeded.
5. **Long recordings.** If step 1 shows MediaExtractor's reading itself is the slow part (for
   example on fragmented MP4), note the numbers and add a backlog item for a faster demuxer;
   do not replace the muxer in this task.

**Tests**
- JVM: the tracker's progress rises through `MUXING` and the copy and never stays at 99% (must
  fail on the old code: no progress after the tracks); labels "Merging audio and video · 45%"
  and "Saving to Download/YFT · 80%"; with a destination that offers a file descriptor the
  engine writes the output once and makes no second copy (must fail on the old code: two
  writes); without one, today's path; a direct-mux failure before the first sample falls back
  once; the space check fails early.
- Instrumented (CI emulator, `app/src/androidTest/.../download/`): a direct mux into a new
  MediaStore item gives a playable file with one video and one audio track; the long-input
  timing test from step 1.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX (with the measured times).

**Done when:** a merged download shows its merge and save stages with progress; on Android 8.0+
the merged file is written once; CI emulator smoke green with the new tests.

**Owner check:** a long YouTube live recording (1 h or more) at 480p or 720p → after the tracks
the card shows "Merging … %" and "Saving … %", the wait after 99% is clearly shorter than in
Preview #4, and the file plays in the Library.

**Result:** —

### P28 — Other sites: the page's video, not the ad before it

Hard · 6–10 h · needs — · **Agent B** · prompt [`B-generic-main.md`](prompts/B-generic-main.md)

**Goal:** on a site without an adapter, Download opens the page's own video — with its title,
picture, length and qualities — even while a pre-roll ad plays; the ad stays under "Other
videos on this page" (R7, R8, R9, R11).

**Read first:** `core-model/.../media/MediaGroups.kt` (`mainVideo`, `looksLikePreview`,
`ofPage`), `PlayingVideo`, `PageMediaRole`; `core-browser/.../detection/HtmlMediaScanner.kt`
(JSON-LD, Open Graph, inline scripts), `PlayingVideoProbe.kt`, `DomMediaProbe.kt`,
`DomProbeResultParser.kt`, `BrowserObservationMapper.kt` (`adRole`, ad lists),
`HeadlessPageFetcher.kt`; `core-browser/.../session/PageCandidateStore.kt`;
`app/.../feature/browser/BrowserViewModel.kt` (`openMainVideo`), `app/.../feature/home/`
(other-site path), `app/.../feature/quickdownload/QuickDownloadViewModel.kt`; P24's fixture
`core-browser/src/test/resources/fixtures/p24-preview-grid.html`.

**Steps**
1. **Page facts.** A new `PageVideoFacts` (core-model media, all fields optional): the page's
   stated length (JSON-LD `VideoObject.duration` even when it names no media file,
   `og:video:duration`, `video:duration`, `itemprop="duration"`), title (`og:title`, JSON-LD
   `name`, else `<title>` without the site's name) and picture (`og:image`, JSON-LD
   `thumbnailUrl`). Home reads them from the HTML (`HtmlMediaScanner`); the browser reads them
   from the live page (the DOM probe's script returns them; meta and JSON-LD only, never page
   text). `PageCandidateStore` keeps them per page.
2. **Common player setups** (player names, never site names): JW Player `setup({file | sources
   | playlist})`, Video.js `data-setup` and `<source>` lists, Flowplayer / Clappr / Plyr
   `source(s)`, KVS-style `flashvars` (`video_url`, `video_alt_url` with their `_text`
   labels), and generic quality lists in page scripts (objects with a media URL and a
   `quality` / `label` / `res` / `height` key, including lists named `mediaDefinitions` or
   `sources`). Each URL becomes a MAIN candidate with its height from the label. Escaped JSON
   (`\/`) is read; URLs built by obfuscated script are not chased — the browser's requests find
   those.
3. **Choosing the main video** (`mainVideo`, new order): (a) a video whose length matches the
   page's stated length (± 2 s, or ± 1% when longer than 10 min); (b) videos the page or its
   player setup names (MAIN) before the rest; (c) the playing element only when its length is
   unknown or matches the stated length — a 0:30 element on a 16:24 page is an ad pre-roll, not
   the page's player; (d) P24's rules after that. `looksLikePreview` also counts as an ad a
   video shorter than half of the stated length (or under 60 s when the page states 2 min or
   more). An adapter's video and P24's behaviour without page facts stay as they are.
4. **Ads.** Extend the media ad list with the ad networks of free video sites that the live
   check (or the networks' public documentation) confirms — candidates to check: TrafficJunky,
   adtng, ExoClick / exosrv, JuicyAds, TrafficStars, tsyndicate, realsrv, magsrv, Adsterra;
   keep only confirmed hosts. A media file first seen right after a VAST or VMAP request
   (address words `vast` / `vmap`, or an XML answer) in the same frame is an ad.
5. **Waiting for the real video.** When the page states a length of 2 min or more and every
   known video is far shorter (only ads or previews), the sheet opens at once with the page's
   title and picture and "Finding the page's video…" for up to 6 s while the browser keeps
   watching requests and reading manifests, then fills in by itself. If nothing comes, it shows
   the best of the others with the line "This may be an ad. Play the video for a moment, or see
   Other videos." (testTag `quick-maybe-ad`).
6. **Header.** The sheet's title and picture come from the page facts when the main video has
   none of its own (no more "Video" with a blank picture on a page that names its video).

**Tests**
- Fixture `core-browser/src/test/resources/fixtures/p28-preroll.html` (neutral text, blank
  images): JSON-LD VideoObject with `duration` PT16M24S and an embed URL, `og:title`,
  `og:image`, a 30 s ad MP4 playing in the player element, the real HLS master loaded later →
  main = the 16:24 HLS with the page's title and picture; the ad under Other videos (must fail
  on the old code: the playing 0:30 ad wins).
- Player setups: JW Player, Video.js, KVS flashvars and a quality list → MAIN candidates with
  heights; escaped JSON; a page without any of them keeps P24's result.
- `mainVideo` table: stated length beats the playing ad; the playing element still wins on a
  page without stated length (P24 regression guard); an adapter's video untouched.
- Waiting: only an ad known → "Finding the page's video…" → the real video arrives → the sheet
  switches without a tap; nothing arrives in 6 s → the ad with `quick-maybe-ad`.
- Instrumented (CI emulator, `app/src/androidTest/.../browser/detection/`): a local fixture page
  plays a 30 s ad and then the main video; Download during the ad picks the main video.

**Live check:** one public page of the owner's kind (a free video site with a pre-roll) if the
sandbox reaches it **without an age or identity gate** — never click one; report hosts, lengths,
heights and counts only, and which host served the ad. Otherwise fixtures and the owner's phone.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX; `design/DESIGN-NOTES.md`
(sheet header, "Finding the page's video…"); `SUPPORT_MATRIX.md` (other sites' row).

**Done when:** the fixture page and the instrumented page open the page's video during the ad;
P24's tests still pass; CI green.

**Owner check:** the site from Preview #4: open a video page and tap Download while the ad
plays → the sheet shows the page's title, picture and length (for example 16:24) with its
qualities (480p, 720p); the ad only under Other videos.

**Result:** —

### P29 — Other sites: the next video when one fails

Medium · 2–4 h · needs P28 · **Agent B** · prompt [`B-generic-main.md`](prompts/B-generic-main.md)

**Goal:** a dead address (HTTP 410, 404, 403) no longer ends the sheet when the page has another
working video, and "Try again" asks fresh addresses (R10).

**Read first:** `QuickDownloadViewModel.kt` (resolve, failure, retry), `QuickDownloadFailures`,
`MediaGroups.ofPage`, `PageCandidateStore`, P24's failure Details.

**Steps**
1. When preparing the main video of a page without an adapter fails with 403, 404, 410 or
   `INVALID_URL` and the page has other videos that are not ads, prepare the next one in
   P28's order once, by itself; a line says "The first file is gone — showing the next video"
   (testTag `quick-next-video`) and Details lists both attempts (step, host, status).
2. "Try again" re-reads the page's current candidates (fresh addresses from the page store, or a
   new read of the page for Home) instead of asking the same address again.
3. Every entry (Home, browser, found list, Other videos) uses the page facts' title and picture
   for the header when the video has none (P28 step 6).

**Tests:** the first video fails with 410 → the second is prepared and shown (must fail on the
old code: the error); an ad is never the fallback; "Try again" uses the store's newest address;
Details lists both attempts.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX.

**Done when:** the tests pass; CI green.

**Owner check:** the page that showed "HTTP 410" in Preview #4 → the sheet opens a working video
(or the page's own after P28) without the error.

**Result:** —

### P30 — Browser: Google search

Easy · 1–2 h · needs — · **Agent C** · prompt [`C-browser.md`](prompts/C-browser.md)

**Goal:** words typed in the browser or on its start page search Google; the owner can pick
another engine (R13, F2).

**Read first:** `app/.../feature/browser/BrowserSearch.kt`, `BrowserStartPage.kt`,
`BrowserViewModel.kt` (read only: it calls `BrowserSearch.webUrl(words)` for typed words),
`core-data/.../preferences/` (`SettingsRepository`, `DataStoreSettingsRepository`),
`core-model/.../settings/`, `app/.../feature/settings/SettingsScreen.kt` and
`SettingsViewModel.kt`.

**Steps**
1. A setting `searchEngine` (Google default, DuckDuckGo, Bing) in the settings model and its
   DataStore repository (a new key; an older install without it reads Google).
2. `BrowserSearch.webUrl(words)` keeps its signature and uses the current engine
   (`https://www.google.com/search?q=…`, `https://duckduckgo.com/?q=…`,
   `https://www.bing.com/search?q=…`); C's own code keeps that engine up to date from the
   settings flow (for example a small holder `BrowserSearch` reads, updated where
   `BrowserScreen` collects settings). If that is not possible without B's
   `BrowserViewModel`, write a hand-off to Agent B and keep the holder.
3. The start page's web-search row names the engine ("Search Google"); Settings gets a
   **Browser** section with "Search engine" (testTag `settings-search-engine`).

**Tests:** default Google URL with encoding (spaces, `&`, Burmese text); DuckDuckGo and Bing;
an old settings file reads Google; the start page label (must fail on the old code:
DuckDuckGo).

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX.

**Done when:** tests pass; CI green.

**Owner check:** type words in the browser's address bar and on its start page → Google
results; Settings › Browser › Search engine → DuckDuckGo → the next search uses DuckDuckGo.

**Result:** —

### P31 — Browser history

Medium · 3–5 h · needs P30 (the Settings › Browser section) · **Agent C** · prompt
[`C-browser.md`](prompts/C-browser.md)

**Goal:** the browser remembers the pages the user opened, lists them, and clears them on
request (R14, F3).

**Read first:** `core-data/.../db/AppDatabase.kt` (Room 5, `MIGRATION_4_5`), `DataModule.kt`,
`AppDatabaseMigrationTest.kt`; `BrowserScreen.kt` (WebView set-up, menu, back/forward),
`BrowserStartPage.kt`; `core-browser/.../webview/SecureBrowserWebViewClient.kt`,
`SecureBrowserChromeClient.kt` (page finished, title); `app/.../feature/settings/
PrivacyCleaners.kt` ("Clear browsing data").

**Steps**
1. Room 6: table `browser_history` (`url` unique, `title`, `host`, `last_visited_at`,
   `visit_count`), `MIGRATION_5_6`, schema `6.json`, a DAO (insert-or-update, list newest first
   with paging or a limit, search by title or host, delete one, delete all, prune).
2. Record a page when the user lands on it (main frame finished, title known): HTTPS only; not
   YFT's start page, `about:`, `data:` or a page P32 blocked; without the fragment and without
   tracking parameters (`utm_*`, `fbclid`, `gclid`, `igshid`); never cookies or headers. The
   same address again raises its count and time. Keep 90 days and at most 5,000 pages (oldest
   pruned).
3. UI: the browser's menu gets "History" (testTag `browser-menu-history`), a full-screen list
   inside the browser screen (testTag `browser-history`): a search box, groups Today /
   Yesterday / Earlier, a row opens its page, a row's menu deletes it, "Clear history" with a
   confirmation. The start page shows the last six pages under "Recent" (testTag
   `browser-recent`). No new navigation route (MainActivity and `ui/navigation` stay
   untouched).
4. Settings › Browser: "Save browser history" switch (default on; off stops recording and keeps
   nothing new) and "Clear browser history" with a confirmation; Settings' existing "Clear
   browsing data" clears the history too.

**Tests:** migration 5 → 6 keeps every download record (`AppDatabaseMigrationTest`); the DAO;
recording rules (HTTPS only, tracking parameters, the count, the switch, pruning); the history
list (open, delete, clear, search) and the start page's Recent in Compose tests; the privacy
cleaner clears it.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX.

**Done when:** tests pass; CI green.

**Owner check:** open three sites in the browser → menu › History lists them (newest first), a
tap opens one, delete one, Clear history empties the list; Settings › Browser › Save browser
history off → new pages are not added.

**Result:** —

### P32 — Block pop-ups and ad redirects

Medium–Hard · 4–7 h · needs P30 (the Settings › Browser section) · **Agent C** · prompt
[`C-browser.md`](prompts/C-browser.md)

**Goal:** a tap on a page or on its ads no longer sends the tab to another site the user did not
choose; normal links still work (R15, F4).

**Read first:** `core-browser/.../policy/SecureWebViewPolicy.kt`, `AppLinkPolicy.kt`,
`BrowserAddressNormalizer.kt`; `core-browser/.../webview/SecureBrowserWebViewClient.kt`
(`shouldOverrideUrlLoading`, `shouldInterceptRequest`), `SecureBrowserChromeClient.kt`;
`BrowserScreen.kt`; Android's `WebResourceRequest.hasGesture()` / `isRedirect()` and
`WebChromeClient.onCreateWindow(isDialog, isUserGesture, resultMsg)`.

**Steps**
1. **New windows.** Turn multiple windows on (`setSupportMultipleWindows(true)`;
   `javaScriptCanOpenWindowsAutomatically` stays false) and handle
   `SecureBrowserChromeClient.onCreateWindow`: a window the user's tap opened to the same site
   opens in the current tab; any other window is blocked, and a small notice "Pop-up blocked"
   with "Open" (opens it in the current tab; testTag `browser-blocked-notice`) shows for 4 s.
   So `window.open()` and `target="_blank"` ads can no longer replace the page.
2. **Top-level redirects** (`shouldOverrideUrlLoading`, main frame) — a pure, tested policy
   (`AdRedirectPolicy` in `core-browser/.../policy/`): block a navigation to a host on YFT's
   own list of pop-up and redirect ad networks (written for YFT, a few dozen hosts, with the
   reason for each family; no copied third-party filter lists), and a navigation to another
   site that the page started without the user's tap (`!hasGesture()`, not a server redirect of
   the user's own navigation). Allow typed addresses, the user's taps on normal links (also to
   other sites), same-site navigations, server redirects of the user's navigation unless a hop
   is on the list, and app links as today (`AppLinkPolicy`). A blocked navigation shows
   "Blocked a redirect to <host>" with "Open".
3. **Scripts of the listed networks** (subresources whose host is on the list) get an empty
   answer in `shouldInterceptRequest` before the page sees them. The page's own video ads are
   not the target and stay (F4); B's media detection still sees every other request.
4. Settings › Browser: "Block pop-ups and ad redirects" (default on, testTag
   `settings-block-popups`). P31 does not record blocked pages.

**Tests:** the policy table (tap / no tap, same / other site, listed host, a listed redirect hop,
typed address, app link); `onCreateWindow`'s decision as a pure function; the notice and "Open"
in a Compose test; the setting off lets everything through (must fail on the old code: the
redirect replaces the page). Instrumented (CI emulator, `app/src/androidTest/.../browser/
navigation/`): a local fixture page whose tap handler calls `window.open()` and whose timer sets
`location` to another host → the page stays and the notice shows; a normal link to another site
opens.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX.

**Done when:** tests pass; CI emulator smoke green with the new test.

**Owner check:** on the sites where ads used to jump to spam pages: taps on the page and its
ads no longer leave it; "Pop-up blocked · Open" opens the blocked page when wanted; a normal
link to another site still opens; videos still play and Download still works.

**Result:** —

### P33 — Merge and Preview #5

Medium · 2–3 h · needs P27–P32 `READY FOR MERGE` · **Agent A (integrator)** · prompt
[`M-merge-preview5.md`](prompts/M-merge-preview5.md)

**Goal:** one branch with A, B and C, fully validated, and Preview #5 on the owner's phone.

**Steps**
1. Start only when the SESSION_STATE sections of A, B and C say `READY FOR MERGE` and their last
   commits have green CI (checkpoint validation; emulator smoke and Preview APK where code
   changed). Otherwise list what is missing and stop.
2. On `work/phase-13-integration` (pull it first): `git merge --no-ff` A's branch, run Agent A's
   scope validation; then B's, run B's scope; then C's, run C's scope. Doc conflicts: keep both
   sides. A code conflict: stop and report the files. Hand-offs left open: do them when they
   are small and say so, else list them.
3. Full validation (640 MiB metaspace, §0.3), `:app:assembleRelease`, line check.
4. Docs: copy each agent's status and Results into §1 and §5; `docs/HANDOFF.md`,
   `docs/PHASE_STATUS.md`, the SESSION_STATE Overview, CHANGELOG (fold the three agent sections
   into Added/Changed/Fixed), SUPPORT_MATRIX, TEST_MATRIX (a Phase 13 summary row).
5. Checkpoint; CI: checkpoint validation, emulator smoke and the Preview APK run →
   **Preview #5**: its link and the §6 list to the owner in Burmese. Then stop. `main` is
   fast-forwarded only with `MAIN=OK`; P8 only with the owner's OK.

**Result:** —

### P8 — Signed release 1.0.0-beta.4

Easy · 1–2 h · needs P33, Preview #5 and the owner's OK · prompt
[`P8-signed-beta4.md`](prompts/P8-signed-beta4.md)

`yft.versionName=1.0.0-beta.4`, `yft.versionCode=4`, `docs/release/1.0.0-beta.4.md`, CHANGELOG
section; full validation; fast-forward `main` to `work/phase-13-integration`, tag
`v1.0.0-beta.4`; `release-draft.yml` builds the APK signed with the release key; record size,
SHA-256 and certificate. Merge, tag and signing need the owner's OK for this task
(`docs/RELEASE.md`).

**Result:** —

## 6. Owner phone checklist

Install `yft-preview-apk` from the Preview APK run the agent sends; uninstall the older YFT
Preview first (each run has a new test key).

**Preview #5 (after P33)**

1. **Other site (P28, P29):** the site from Preview #4 → open a video page, tap Download while
   the ad plays → the page's title, picture and length with 480p/720p; the ad only under Other
   videos; the page that showed "HTTP 410" opens a working video.
2. **YouTube merge (P27):** a long live recording (1 h or more) at 480p or 720p → "Merging … %"
   then "Saving … %", a much shorter wait after the tracks, the file plays.
3. **Search (P30):** words in the address bar and on the start page → Google; Settings ›
   Browser › Search engine works.
4. **History (P31):** menu › History lists the pages; open, delete, Clear history; the switch
   in Settings stops recording.
5. **Pop-ups and redirects (P32):** taps on pages and their ads stay on the page; "Pop-up
   blocked · Open" works; normal links open.
6. **Phase 12 still works:** YouTube 144p…1080p (2K/4K) with sizes, Facebook 720p/360p + Audio,
   M4A and MP3, the same sheet on every site, Retry and Details.
7. About › Last crash report: none.

When something fails, a screenshot of the sheet's **Details** (lookups) or of the download's
**Details** (saving) tells the agents what happened.

## 7. Backlog

Advice on the four items the owner asked about (2026-10-05); each waits for his decision:

- **B1 — Download button inside YouTube's page** (under the video, beside Like and Share).
  Advice: not now. P13's wide button gives the same tap without touching YouTube's page. Later,
  if the owner still wants it: a page script adds a button to YouTube's action row and talks to
  the app only through an origin-restricted `WebMessageListener` (`https://m.youtube.com`); when
  YouTube changes its page and the spot is gone, P13's button stays. About 4–6 h plus upkeep.
  Owner decision: —
- **B2 — a YouTube page of YFT's own, like Snaptube's** (own player, search and comments).
  Advice: no. It means rebuilding YouTube's player, search and comments on YouTube's internal
  interface, which changes often: weeks of work and frequent breakage. Owner decision: —
- **B3 — Facebook formats from the page in YFT's browser.** Advice: only if needed. After P23,
  measure on the phone; if a Facebook lookup still misses qualities the browser page has, a
  spike reads only the format markers from the page the browser already loaded → parser +
  fixtures. About 6–10 h. Owner decision: —
- **B4 — TikTok on the owner's phone.** Advice: no phone test (TikTok is banned in India); every
  TikTok change keeps fixtures, the CI emulator and sandbox live checks; no VPN. Owner
  decision: —

Other items:

- Share target: open links shared from other apps (Android share sheet) in Home's lookup, so
  they open the download sheet like a pasted link (the manifest has no share target).
- AV1 merges: off since P4 (the API 34 emulator's muxer failed), so Facebook's AV1-only sizes and
  YouTube's AV1-only 2K/4K stay hidden until an AV1 merge is proven on a phone.
- YouTube: use the page player's own proof-of-origin token from the browser (ADR-006, not done);
  SABR streaming (YouTube's newer delivery for `ANDROID` and others) is not planned.
- Android 9 and older save through the legacy public folder; P20's instrumented test covers the
  API 34 MediaStore path only.
- Instagram and X adapters (generic detection only today).
- Background playback in the Library.
- Saving to a folder chosen with the system picker.

- One shared ad list for media detection (B) and pop-ups/redirects (C), updated from one place.
- A private tab (no history, no cookies kept) and a per-site "allow pop-ups" list.
- Adapters for named adult sites (F1 option C) only if the owner chooses it after Preview #5.
- A faster demuxer for very long recordings if P27's measurements show MediaExtractor is the
  slow part.


## 8. Done before Phase 13

**Phase 12** (P20–P26, 2026-10-06) is merged into `main` at `bc806f9` (no tag). Full plan,
Results and findings: `git show bc806f9:docs/FIX_ADD_PLAN.md`; prompts:
`git show bc806f9:docs/prompts/`; per-task Results and validation:
`git show bc806f9:docs/SESSION_STATE.md`. Owner test: Preview #4 (run
https://github.com/Alalkipgen/YFT/actions/runs/37530061595, 2026-10-07) → "about 90% fine",
Phase 13.

| ID | Task | Commits | Status |
| --- | --- | --- | --- |
| P20 | Video downloads save again (no "Storage unavailable") | `83c9c3f`, `13b4576` | Preview #4 OK |
| P21 | Retry and failure details | `8cb0b74`, `c10d8c1` | Preview #4 OK |
| P22 | YouTube: every quality (144p–4K) | `b6c84b2`, `0e0ed15` | Preview #4 OK; long recordings wait at 99% → P27 |
| P23 | Facebook: every quality | `c53128c`, `8119f4e` | Preview #4 OK |
| P24 | Other sites: main video | `8ef33fb`, `5f61e87` | Preview #4: pre-roll ad wins on some sites → P28, P29 |
| P25 | One sheet for every site | `7ec3884`, `4d04e8b` | Preview #4 OK; title and picture → P29 |
| P26 | Merge A → B → C, Preview #4 | `bc806f9` | DONE (2026-10-06): 1292 tests, 0 failures, 66 skipped |
| P8 | Signed `1.0.0-beta.4` | — | moved behind Phase 13 ([§1](#1-status-board)) |

**Phase 11** (P0–P19, 2026-10-04 to 2026-10-06) is merged into `main` at `4db6c2b` (no tag).
Plan, Results and prompts: `git show 4db6c2b:docs/FIX_ADD_PLAN.md`,
`git show 4db6c2b:docs/prompts/`, `git show 4db6c2b:docs/SESSION_STATE.md`; the Phase 12 plan's
§8 (`git show bc806f9:docs/FIX_ADD_PLAN.md`) lists its tasks and Previews #1–#3.

**Phases 8–10** (T01–T19, 2026-10-03 to 2026-10-04) are complete and released as
`1.0.0-beta.3`. Full plan: `git show 2f6284f:docs/FIX_PLAN.md`; prompts:
`git show 2f6284f:docs/prompts/`.

**T19 — release record (DONE, 2026-10-04):** `main` fast-forwarded to `2f6284f`; tag
`v1.0.0-beta.3`; Release draft https://github.com/Alalkipgen/YFT/actions/runs/37204457527 created
the draft pre-release: `video-downloader-1.0.0-beta.3.apk`, 6,334,176 bytes, SHA-256
`8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
`3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
(same key as beta.1 and beta.2), source commit `2f6284f`.
