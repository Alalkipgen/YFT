# YFT Fix & Add Plan — Phase 14 (Preview #5 field fixes: background downloads, faster merge, TikTok, fresh links)

Owner phone test of **Preview #5** (2026-10-08, Xiaomi phone with HyperOS; Preview APK run
https://github.com/Alalkipgen/YFT/actions/runs/37575586233, merge commit `436aa90`; `main` =
`5a5bddb`). Phase 13 (P27–P33: merge progress, the page's video instead of the pre-roll ad, the
next video when one fails, Google search, history, pop-up blocking) is merged into `main`. The
owner found five things to fix or add: on some pages of a site without an adapter the sheet
says **"The site no longer has this video (HTTP 410)"** until he reloads the page by hand; a
1-hour YouTube live recording still spends **about 2 minutes merging**, and the **merge stops
when he switches to another app** until he opens YFT again; **TikTok** (with a VPN) says "No
video on screen to download" on the For You feed; and downloads should **keep going in the
background** with **% and speed in the notification**. Phase 14 fixes these with **three
agents working at the same time** (A, B, C, §0.7), then one merge and Preview #6. Written in
Plan Mode on 2026-10-08; work starts when the owner pastes the prompts in
[`prompts/`](prompts/README.md). Phase 13's plan and prompts stay in Git history:
`git show 5a5bddb:docs/FIX_ADD_PLAN.md` and `git show 5a5bddb:docs/prompts/` (summary in
[§8](#8-done-before-phase-14)).

## Contents

0. [How to work](#0-how-to-work) (0.7: [three agents in parallel](#07-three-agents-in-parallel))
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Tasks](#5-tasks)
6. [Owner phone checklist](#6-owner-phone-checklist)
7. [Backlog](#7-backlog)
8. [Done before Phase 14](#8-done-before-phase-14)

## 0. How to work

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out **your agent's branch** (§0.7) in **your own folder** (Notion sandbox:
   `/data/YFT-A`, `/data/YFT-B` or `/data/YFT-C`, see the prompts) and pull it. On the first
   start create it from `origin/work/phase-14-integration` (the plan commit on top of `main`
   `5a5bddb`). Never work on another agent's branch, on `work/phase-14-integration` (only P38
   does) or on `main`.
3. Read §0, §3, §4, your tasks in §5 with their **Read first** files, and your own section of
   `docs/SESSION_STATE.md`.
4. Set up the environment when it is missing (§0.3), then run your scope's validation before
   editing, so you know the starting state.
5. Record the task as `IN PROGRESS` in **your section** of `docs/SESSION_STATE.md`. Agents A, B
   and C never edit this plan file (`docs/FIX_ADD_PLAN.md`) or `docs/prompts/`: P38 copies
   status and Results from SESSION_STATE into §1 and §5.
6. Do the **Steps** in order. Stay inside the task and inside your files (§0.7); anything else
   goes to your SESSION_STATE section as a hand-off or a backlog note. When the code shows that a
   step is wrong, adapt it and say so in the Result ("Plan adapted: …").
7. Add the listed tests. A regression test must fail on the old code (§0.3, regression proof).
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists (only your sections of shared docs, §0.7), write the Result in
   your SESSION_STATE section (status `DONE (date)` or `OWNER CHECK`) and checkpoint with
   `scripts/checkpoint.sh "P3x: summary"`. Check CI for the pushed commit and fix a red run.
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
  with a bot check, so the owner's phone is the final proof for YouTube. **TikTok:** the owner
  now checks TikTok on his phone with a VPN (2026-10-08); agents still use fixtures, the CI
  emulator and sandbox live checks.
- Sites for adults (P37): public pages only. An age or identity notice is the user's own tap on
  his phone; an agent never clicks one, not even in a live check, and skips a page that shows
  one. Fixtures keep the page structure (players, scripts, links) but replace titles, names,
  descriptions and pictures with neutral text and blank images; nothing explicit enters the
  repository, logs or reports (report hosts, lengths, heights, statuses and counts only).
- Push access: the Notion sandbox loses its deploy key on every reset. Never search for an old
  key: make a new one (`ssh-keygen -t ed25519`), give the owner the public line for the
  repository's Deploy keys (write access) and wait for his OK before the first push.
- The agent sandbox has no emulator; use the CI emulator job (`emulator-smoke.yml`, API 34) for
  real WebView, MediaStore, MediaCodec/MediaMuxer, notification and foreground-service checks.
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
`GRADLE_USER_HOME` (`/data/gradle-home`) and `PATH`. After a sandbox reset, recreate it and
install what is missing (NDK and CMake went missing twice in Phase 11; an empty
`/data/gradle-home` only means the first build downloads its dependencies). Agents may share
one computer: each works in its own folder (`/data/YFT-A`, `-B`, `-C`; run `./gradlew` in your
folder), stops only Gradle daemons it started (no `pkill` of another agent's build) and, on a
4 GiB machine, waits until no other Gradle build runs (`pgrep -af "[G]radleDaemon"`): one Gradle
command at a time on the computer. The full validation needs Gradle metaspace 640 MiB:
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
| Agent A (P34) | `./gradlew --no-daemon --continue :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent B (P36, P37) | `./gradlew --no-daemon --continue :extractor-sites:test :extractor-generic:test :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent C (P35) | `./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Full (P38, P8) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` |
| Scripts, docs-only checkpoints | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests` |
| Line length (must print nothing) | `git diff -U0 origin/main -- '*.kt' '*.kts' \| grep '^+[^+]' \| LC_ALL=C.UTF-8 awk 'length > 101'` |

Phase 14 start (P33, 2026-10-07, `436aa90`; `main` `5a5bddb` adds docs only): full validation
1435 tests, 0 failures, 66 skipped; lint 0 errors; `:app:assembleRelease` OK.

**Regression proof.** Copy the files you changed to `/data/bak/<task>/`, put the old version
back (for example `git show HEAD:<path> > <path>` before your commit), run the new tests and see
them fail, then restore your version with `cp` and check it with `cmp`. Name the tests that
failed on the old code in the Result. An instrumented test (`app/src/androidTest`) cannot run in
the sandbox: its JVM twin is the regression proof, and the CI emulator run must pass with the fix.

**CI.** `checkpoint-validation` runs on every `work/**` push **that changes more than
documentation**: since the Phase 14 plan commit, a push whose files are all under `docs/` or end
in `.md` starts no CI (`paths-ignore`), so a docs-only checkpoint (a status line, `READY FOR
MERGE`) has no run. "Green CI" therefore means the runs of your newest commit that changed code,
scripts or Gradle files. `emulator-smoke` (API 34, `:app:connectedDebugAndroidTest`) and
`preview-apk` run on `work/phase-*` pushes that change code (`app/**`, `core-*/**`,
`extractor-*/**`, Gradle files). Status for a branch:

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
`git log --oneline --grep "P34:"` finds a task's commits.

### 0.5 Report to the owner (Burmese, short)

```text
Agent: A / B / C — Task: P3x — <title> — Level: Easy / Medium / Hard — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run links> (debug APK: Artifacts › yft-debug-apk;
  preview: Preview APK run › yft-preview-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: … (hand-offs to other agents, if any)
နောက်တစ်ဆင့်: P3y / READY FOR MERGE
```

### 0.6 Phone builds for the owner

Every green checkpoint run uploads `yft-debug-apk` (14 days; app ID `com.alal.yft.debug`). Every
push that changes code also runs **Preview APK (test key)**, which uploads `yft-preview-apk`: the
minified release build as "YFT Preview" (`com.alal.yft.preview`), signed with a test key made in
that job, so the owner uninstalls the older YFT Preview before installing a newer one. Each
agent's branch gets its own preview runs; the owner may try one early, but the phone test that
counts is **Preview #6** = the Preview APK run of P38's merge commit on
`work/phase-14-integration` (§6). Only P8 signs with the release key.

### 0.7 Three agents in parallel

**Branches.** All three start from `origin/work/phase-14-integration` at the plan commit.

| Agent | Tasks (in order) | Branch | Area |
| --- | --- | --- | --- |
| A | P34, later P38 (integrator) and P8 | `work/phase-14-background` | Foreground service, notification, Downloads and Settings screens, manifest |
| B | P36 → P37 | `work/phase-14-sites` | TikTok adapter and feed detection; other sites' links, Try again and reload in the browser |
| C | P35 | `work/phase-14-fast-merge` | Download engines: the merge (`core-download`) |
| A (P38) | merge A → B → C, Preview #6 | `work/phase-14-integration` | Integration only |

Agent A has the shortest track (P34); it then waits for B and C and does P38. B (P36 + P37) and
C (P35) are the long tracks of about the same length. In two-agent mode (below) A also does C's
task.

**Files each agent may change** (tests beside them under `src/test/` or `src/androidTest/`
included). Everything not listed belongs to nobody: change it only through a hand-off.

| Agent | Owns |
| --- | --- |
| A | `app/src/main/java/com/alal/yft/download/**`; `app/src/main/java/com/alal/yft/feature/downloads/**`; `app/src/main/java/com/alal/yft/feature/settings/**`; `app/src/main/java/com/alal/yft/ui/components/NotificationPermission.kt`; `app/src/main/AndroidManifest.xml` (permissions and the download service's entry only); `app/src/androidTest/java/com/alal/yft/background/**` (new); `app/build.gradle.kts` only for an `androidTestImplementation` line |
| B | `extractor-sites/**` (TikTok; other adapters only for a shared helper TikTok needs); `app/src/main/java/com/alal/yft/detection/**`; `core-browser/**`; `core-model/src/main/kotlin/com/alal/yft/core/model/media/**`; `core-media/**`; `extractor-generic/**`; `app/src/main/java/com/alal/yft/feature/{quickdownload,detectedmedia,home,browser}/**`; `app/src/androidTest/java/com/alal/yft/browser/**` and `app/src/androidTest/assets/{focused-video,browser-detection}/**`; docs `SUPPORT_MATRIX.md` |
| C | `core-download/**`; `core-model/src/main/kotlin/com/alal/yft/core/model/download/**`; `app/src/androidTest/java/com/alal/yft/download/**` and `app/src/androidTest/assets/{mux,mp4,mp3}/**` |
| Nobody (P38/P8 only) | `.github/workflows/**`, `gradle.properties`, `gradle/libs.versions.toml`, root and module Gradle files (except A's line above), `app/src/main/res/**`, other `app` packages (`feature/library`, `thumbnail`, `diagnostics`, `ui/navigation`, `ui/format`, `ui/theme`, `ui/components` except A's file, `MainActivity`, `YftApplication`), `core-data/**`, `core-model/.../settings/**`, `smoke/**` androidTests, shared test helpers (`app/src/test/java/com/alal/yft/testing/**`: add new helpers in your own test folders), `AGENTS.md`, `README.md`, `docs/FIX_ADD_PLAN.md`, `docs/prompts/**`, `docs/HANDOFF.md`, `docs/PHASE_STATUS.md`, `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROJECT_CONTEXT.md`, ADRs |

**Shared docs — own section only.** Each agent edits only the section with its letter, which
the plan commit created; nothing above or below it:

- `docs/SESSION_STATE.md`: `## Agent A …`, `## Agent B …`, `## Agent C …` (status, Results,
  validation, CI links, hand-offs). The `## Overview` section is P38's.
- `CHANGELOG.md` under `## [Unreleased]`: `### Phase 14 — Agent A (P34)`, `### Phase 14 — Agent
  B (P36, P37)` and `### Phase 14 — Agent C (P35)`. Replace the placeholder line, then add
  bullets below it. P38 folds them into Added/Changed/Fixed.
- `docs/TEST_MATRIX.md`: `### Agent A — P34` (and B, C) under `## Phase 14`.

**Contracts** (code other agents use; change only by adding, with defaults; never rename,
remove or change a meaning):

- C → A, B: the public `DownloadQueue` API, `DownloadTask`, `DownloadProgress`,
  `DownloadFailure`, `DownloadFailureReason`, `AudioVideoMuxStage`, `DownloadDestination` (A's
  service and notification read the stages and progress; B's sheet enqueues).
- A → B: `DownloadEnqueuer` and `DownloadPlanFactory` (B's sheet calls them).
- B → A: `YftFormat` stays as it is (nobody's file this phase); A writes its speed and time-left
  texts in its own packages.
- B alone owns the browser and detection this phase; A and C use none of it.

**Hand-offs.** When you need a change in a file you do not own, do not make it. Write
"Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and in your
report; the owner passes it on. Prefer a fix inside your own files when one exists.

**Merge (P38).** Agent A merges the three branches into `work/phase-14-integration`
(`--no-ff`) in this order: `work/phase-14-background` (A), then `work/phase-14-sites` (B), then
`work/phase-14-fast-merge` (C); it runs one full validation after the three merges (it covers
every scope). Shared docs conflict only if a section rule was broken: keep both sides. A code
conflict means an ownership slip: stop and report the files.

**Two agents instead of three** (if the owner prefers): Agent A owns A's and C's files and does
P34 → P35 → P38; Agent B does P36 → P37. The prompts work unchanged: the owner pastes
[`A-background.md`](prompts/A-background.md) into Agent A's chat and, after P34,
[`C-fast-merge.md`](prompts/C-fast-merge.md) with `BRANCH_OVERRIDE: work/phase-14-background`;
P38 then merges A's branch (with C's work) and B's. Wall time grows from about 9–14 h to about
13–20 h.

## 1. Status board

Agents A, B and C record status in their own section of `docs/SESSION_STATE.md`; P38 copies it
here. AI agent time includes builds and CI waits on a 4 GiB sandbox.

**Agent A — `work/phase-14-background`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P34 | [Downloads and merges keep going in the background; speed in the notification](#p34--downloads-and-merges-keep-going-in-the-background) | Medium | 5–7 h | — | DONE — OWNER CHECK (merged by P38) |

**Agent B — `work/phase-14-sites`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P36 | [TikTok: Download on the For You feed and video pages](#p36--tiktok-download-on-the-for-you-feed-and-video-pages) | Medium | 3–5 h | — | DONE — OWNER CHECK (merged by P38) |
| P37 | [Other sites: fresh links instead of HTTP 410](#p37--other-sites-fresh-links-instead-of-http-410) | Medium | 4–6 h | — | DONE — OWNER CHECK (merged by P38) |

**Agent C — `work/phase-14-fast-merge`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P35 | [Faster merge for long videos](#p35--faster-merge-for-long-videos) | Hard | 6–10 h | — | DONE — OWNER CHECK (merged by P38) |

**Integration — Agent A, `work/phase-14-integration`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P38 | [Merge A → B → C, full validation, Preview #6](#p38--merge-and-preview-6) | Medium | 2–3 h | P34–P37 READY FOR MERGE | DONE — Preview #6 sent, OWNER CHECK |
| P8 | [Signed release 1.0.0-beta.4](#p8--signed-release-100-beta4) | Easy | 1–2 h | P38, Preview #6, owner OK | TODO |

In parallel the wall time is about 9–14 h (B's 7–11 h and C's 6–10 h are the long tracks, then
P38); two agents need about 13–20 h; one agent alone 20–31 h.

## 2. What the owner saw

Preview #5 (2026-10-08), owner's words in short:

1. **Other site without an adapter (browser), some videos only.** The sheet shows the page's
   title with "The site no longer has this video (HTTP 410)". Details: first video — step "list
   of qualities (manifest)", host `hm-h…`/`km-h…` of the site's video CDN, HTTP 410; next video
   — step "file check", host `ev…` of the same CDN, HTTP 410. Try again fails the same way.
   Reloading the page in the browser by hand (one to five times) fixes it. Other videos on the
   same site and another site (Javtiful) work. "A minor bug."
2. **YouTube merge.** A 1-hour live recording now shows "Merging … %", but merging still takes
   about 2 minutes after the tracks. Faster, if it is really possible.
3. **TikTok (with a VPN), `tiktok.com/foryou`.** Download → "No video on screen to download.
   Scroll to a video and tap Download again." while a video is on screen ("Found on this page
   1/2").
4. **Background downloads.** Downloads should keep going while he uses other apps (Facebook),
   and the notification bar should show the % and the speed — KB/s below 1,024 KB/s, MB/s from
   1 MB/s — like Snaptube.
5. **The merge stops in the background** (added the same day). When he leaves YFT for another app
   while a download is merging (or converting), the merge stops and goes on only when he opens
   YFT again.

## 3. Owner decisions

Defaults below are what the agents do unless `OWNER ANSWERS` in the prompt says otherwise.

| ID | Question | Options | Default |
| --- | --- | --- | --- |
| G1 | Number of agents | 3 at once (A, B, C) or 2 (A takes C's task) | 3 |
| G2 | Faster merge (P35) | Copy the MP4 and M4A tracks' data in large blocks into the final MP4 (stream copy, no per-sample calls), checked afterwards, with today's MediaMuxer way as the safety net (`FAST_MERGE=ON`); or only today's way made a little faster (`FAST_MERGE=OFF`) | `ON` |
| G3 | Keep downloads and merges working in the background (P34) | Wake lock (and Wi-Fi lock while downloading) as long as a download, merge, MP3 conversion or save runs; the foreground service also declares media processing (Android 15) while merging; released as soon as nothing runs | On |
| G4 | Battery card (P34) | A card in Downloads (and an entry in Settings) that asks once to let YFT run without battery limits, with Xiaomi's steps (`BATTERY_CARD=ON`); or no card (`OFF`) | `ON` |
| G5 | Finished notice (P34) | A notification when a download finishes or fails (`DONE_NOTICE=ON`); or only the progress notification (`OFF`) | `ON` |
| G6 | TikTok qualities (P36) | Ask TikTok's desktop page for the quality list when the phone page has none (`TIKTOK_QUALITIES=DESKTOP`); or only what the phone page gives, usually one quality (`PAGE`) | `DESKTOP` |
| G7 | Fresh links on other sites (P37) | Read the page again quietly up to 2 times before showing an error (`REREAD=2`); or only the "Reload page and try again" button (`REREAD=0`) | `2` |
| G8 | Speed units | KB/s below 1,024 KB/s ("850 KB/s"), MB/s from 1 MB/s with one decimal ("1.2 MB/s"), 1,024-based like the Downloads screen | owner's rule |

## 4. Findings and root causes

Code read on `5a5bddb` and live checks in Plan Mode (2026-10-08). Confidence in brackets; agents
confirm each finding before they change code.

- **R16 — Try again in the browser asks the same dead links (item 1, high).**
  `QuickDownloadViewModel.readPageAgainThenLoad` (about line 537) reads the page again only when
  Home found it (`DetectedMediaStore.readPageAgain` → Home's `LinkInspector`); for a page the
  browser owns it takes the store's page as it is, so `MediaGroups.refreshed` finds the same
  addresses and Try again repeats the same 410.
- **R17 — the page's own links can be dead for YFT (item 1, medium; cause not proven).** P28's
  `PlayerSetupScanner` takes the signed CDN links written into the page's player script
  (`mediaDefinitions`, flashvars). On some page loads all of them answer 410 to YFT (manifest
  and MP4 alike) while the site's player plays, and a manual reload sometimes gives working ones.
  Likely causes: links that expired or were signed for another IP (a VPN that changed its exit,
  a page served from a cache), or links the player itself never uses (it fetches fresh ones by
  script). The live probe was skipped: the site shows an age notice (§0.2). P37 step 1 adds the
  evidence that tells the causes apart.
- **R18 — the page's named links outrank the player's own requests (item 1, medium).** In
  `MediaGroups.mainVideo` a link the page or its player setup names (`PageMediaRole.MAIN`) comes
  before the addresses the player actually requested (seen by `BrowserObservationMapper`), which
  are the freshest proof of a working link for this phone.
- **R19 — TikTok's feed has no video links (item 3, high, confirmed live).** On
  `tiktok.com/foryou` (phone user agent) a card is `article[data-e2e="recommend-list-item-container"]`
  → `section[data-e2e="feed-video"]` → `div` with id `xgwrapper-<n>-<19-digit video id>` →
  `<video src="blob:…">`, plus the author's `/@handle` link; no `/@user/video/<id>` link exists.
  `FocusedVideoProbe` needs a video-shaped link beside the focused video, finds none, and
  `BrowserViewModel` shows `NO_FOCUSED_VIDEO_NOTICE`.
- **R20 — TikTok's phone page has other data (item 3, high, confirmed live).** A video page asked
  with a phone user agent (the WebView's) is a "reflow" page: its data sit under
  `webapp.reflow.video.detail` (play and download addresses, no `bitrateInfo`); with a desktop
  user agent the page has `webapp.video-detail` with `bitrateInfo`. `TikTokPageParser` reads only
  `webapp.video-detail`, so the browser's lookup finds nothing. The media host answers 206 only
  with the cookies of the page answer (`tt_chain_token`), 403 without, whatever the user agent;
  `TikTokExtractor.mediaContext` keeps the WebView's cookie header when there is one and drops
  the page answer's cookies.
- **R21 — background work has no guard against sleep and limits (item 4, medium).**
  `DownloadForegroundService` (type `dataSync`, `START_STICKY`, started when a download is
  queued) keeps the process alive, but holds no wake lock or Wi-Fi lock (the manifest has no
  `WAKE_LOCK`), does not handle Android 15's `dataSync` time limit (`onTimeout`, 6 h a day;
  targetSdk 35), and when the notification permission was denied (asked once, at the first
  download) its notification is hidden. Xiaomi's HyperOS battery saver may stop such apps. What
  happens on the phone today is to be measured (P34 step 1).
- **R22 — the notification shows no numbers (item 4, high).** `DownloadNotificationFactory` builds
  one ongoing notification (id 4001: the number of downloads, a name or P27's merge stage, a
  progress bar, Pause all) with no % text, no speed and no time left. The Downloads screen
  already computes speed (`TransferRateTracker`, `YftFormat.bytes`: KB below 1,024 KB, MB above).
- **R23 — merging is slow per sample (item 2, medium–high).** P27 merges with MediaExtractor →
  MediaMuxer, sample by sample (`copySamples`, about 8 JNI calls per sample), straight into the
  destination. Measured on the CI emulator: about 95 µs per sample (reading 1/3, MediaMuxer 2/3).
  A 1-hour 720p recording has about 260,000 samples (30 fps video + AAC audio), so the per-sample
  work, not the bytes, decides the time (about 2 minutes on the owner's phone). Copying the
  tracks' data in large blocks and writing new sample tables avoids it.
- **R24 — the merge stops when YFT is in the background (item 5, medium; cause on the phone to
  be confirmed).** In the code the task stays `RUNNING` through the download, the merge and the
  save (`DownloadQueue.runTask` → `transferDispatcher.transfer`), and `RUNNING` is one of
  `DownloadNotificationFactory.FOREGROUND_STATUSES`, so the foreground service is not stopped
  when the merge starts, and the queue's scope is the app's (`DownloadRuntimeModule`,
  `Dispatchers.IO`), not a screen's. The stop therefore most likely comes from the phone: Xiaomi's
  HyperOS freezes background apps that only use the CPU (a merge has no network traffic, a
  download has), unless the app is allowed to run without battery limits; YFT also holds no wake
  lock, declares only the `dataSync` service type (Android 15 adds `mediaProcessing` for exactly
  this work) and its notification is hidden when notifications are off. P34 removes every cause
  on YFT's side, detects a freeze afterwards (the service's clock jumps) and then shows the
  phone's setting that stops it; P35 makes the merge itself short.

## 5. Tasks

Each task: level · AI agent time · needs · agent · prompt; then **Goal**, **Read first**,
**Steps**, **Tests**, **Docs**, **Done when**, **Owner check** and **Result** (filled by P38
from the agent's SESSION_STATE section).

### P34 — Downloads and merges keep going in the background

Medium · 5–7 h · needs — · **Agent A** · prompt [`A-background.md`](prompts/A-background.md)

**Goal:** downloads, merges, MP3 conversions and saves keep running while the owner uses other
apps or the screen is off, and the notification shows what Snaptube shows: "45% · 1.2 MB/s ·
61 MB of 96 MB · 15 s left" (and "Merging audio and video · 45%" while merging); when Android
or the phone limits YFT (notifications off, battery saver, HyperOS freezing, Android 15's time
limit) the app says so and shows the fix (R21, R22, R24).

**Read first:** `app/.../download/DownloadForegroundService.kt` (`onStartCommand` →
`startForeground`, `START_STICKY`, the `queue.tasks` observer that stops the service when no
task is in `FOREGROUND_STATUSES`), `DownloadNotificationFactory.kt` (`active`, `preparing`,
`FOREGROUND_STATUSES`, channel `active_downloads`, id 4001, Pause all, P27's merge stage from the
task's `AudioVideoMuxCheckpoint`), `DownloadRuntimeAdapters.kt` (starts the service when a
download is queued), `DownloadRuntimeModule.kt` (the queue's app scope), `app/.../feature/
downloads/TransferRateTracker.kt`, `DownloadLabels.kt`, `DownloadsScreen.kt`,
`app/.../ui/components/NotificationPermission.kt`, `app/.../ui/format/YftFormat.kt` (`bytes`,
`duration`; read only), `app/.../feature/settings/SettingsScreen.kt`,
`app/src/main/AndroidManifest.xml`, and (read only) `core-download/.../DownloadQueue.kt`
(`runTask`: the task stays `RUNNING` through download, merge and save).

**Steps**
1. **Measure first (CI emulator).** Instrumented tests (`app/src/androidTest/java/com/alal/yft/
   background/`): (a) a real download from a small slow server inside the test (about 200 KB/s
   for about a minute) → press Home (UiAutomator `pressHome()`) → for 20 s the stored bytes keep
   rising and the ongoing notification exists (`NotificationManager.activeNotifications`);
   (b) a merged download whose tracks come from the test server (the androidTest `mux` assets,
   or P27's long fragmented input from `LongFragmentedMp4.kt` copied into your folder) → press
   Home as soon as the stage is `MUXING` → the merge's progress keeps rising and the task
   completes without the app coming back. If the emulator allows it, repeat (a) with the device
   idle (`dumpsys deviceidle force-idle` through `UiAutomation.executeShellCommand`, then
   `unforce`). Record what the old code does in the Result (stock Android may not freeze like
   HyperOS; the tests guard YFT's side).
2. **Keep working with the screen off or in another app (G3).** While any task runs — download,
   merge, MP3 conversion or save — the service holds a partial wake lock (tag `yft:downloads`,
   renewed with a timeout), plus a Wi-Fi lock (`WIFI_MODE_FULL_HIGH_PERF`) while bytes are
   downloaded; both are released as soon as nothing runs (paused, finished, failed, cancelled)
   and in `onDestroy`. Manifest: `WAKE_LOCK`. The service never stops while a task is
   `RUNNING` in any stage (a JVM test pins this for the merge and save stages).
3. **Service types and Android's limits.** Start the foreground service with its type
   (`ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on API 29+); on
   Android 15+ (API 35) add `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` while a merge, conversion
   or save runs (manifest: `foregroundServiceType="dataSync|mediaProcessing"` and the
   `FOREGROUND_SERVICE_MEDIA_PROCESSING` permission). Show the notification at once
   (`FOREGROUND_SERVICE_IMMEDIATE`). Handle `onTimeout(startId, fgsType)` (each type may run
   6 h a day on Android 15): pause what runs, stop the service and post "Android paused
   downloads after 6 hours. Open YFT to resume." A start the system refuses
   (`ForegroundServiceStartNotAllowedException`, API 31+, for example a restart from the
   background) leaves the downloads queued with that notice instead of a crash. Keep today's
   resume after a `START_STICKY` restart.
4. **Freeze detector (R24).** While a task runs, the service ticks once a second on its own
   clock (`SystemClock.elapsedRealtime`). A tick that comes more than 10 s late while YFT holds
   its wake lock means the phone froze or stopped YFT in the background: count it, log one line
   (time lost, stage; no names or addresses), and show the battery card (step 8) again even if it
   was dismissed, with "Your phone paused YFT in the background for 1 min 40 s."
5. **The notification (R22)**, one ongoing notification, updated at most once a second:
   - one download: title = the video's title (shortened); text "45% · 1.2 MB/s · 61 MB of 96 MB ·
     15 s left"; the progress bar; Pause all (as today); a tap opens YFT;
   - size unknown (HLS without sizes): "61 MB · 1.2 MB/s", with the fraction the Downloads card
     uses when it has one;
   - several downloads: title "Downloading 3 videos · 45%", text "2.4 MB/s · 1 min left", and one
     line per download (up to 5, `InboxStyle`): "<title> — 45% · 1.2 MB/s";
   - merging, converting and saving: P27's "Merging audio and video · 45%" and "Saving to
     Download/YFT · 80%" (no speed), moving while YFT is in the background;
   - waiting: "Waiting for network" / "Waiting for Wi-Fi" when the queue says so.
   Speed = bytes per second over the last few seconds, computed once for the card and the
   notification (share `TransferRateTracker`'s logic, do not write it twice); format G8: below
   1,024 KB/s "850 KB/s" (whole numbers), from there "1.2 MB/s" (one decimal), 1,024-based. Time
   left = remaining bytes ÷ smoothed speed: "15 s left", "3 min left", "1 h 5 min left"; hidden
   when the size is unknown or the speed is 0. The Downloads card uses the same speed text.
6. **Finished notice (G5).** A second channel "Finished downloads": "Downloaded · <title>" (a tap
   opens YFT) and "Download failed · <title> — <short reason>"; no file path or address.
   `DONE_NOTICE=OFF` skips it.
7. **When notifications are off.** If the Android 13+ permission was denied or the channel is
   off, the Downloads screen shows a one-line card "Turn on notifications to see download
   progress outside YFT" → the app's notification settings (`ACTION_APP_NOTIFICATION_SETTINGS`);
   it can be dismissed (testTag `downloads-notifications-card`).
8. **Battery card (G4).** When `PowerManager.isIgnoringBatteryOptimizations` is false and a
   download has been started (or step 4 saw a freeze), the Downloads screen shows "Downloads and
   merges may stop when YFT is in the background. Allow YFT to run without battery limits." →
   `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for YFT (manifest
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; YFT is distributed through GitHub, not Google Play),
   falling back to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` or the app's details. On Xiaomi,
   Redmi and POCO phones (`Build.MANUFACTURER`) it adds the HyperOS steps: "Settings › Apps ›
   Manage apps › YFT › Battery saver › No restrictions; turn on Autostart; in Recents, hold YFT's
   card and tap the lock." Not now hides it until the next freeze; Settings › Downloads ›
   "Background downloads" shows Allowed / Limited with the same button and steps. testTags
   `downloads-battery-card`, `settings-background-downloads`. `BATTERY_CARD=OFF` keeps only the
   Settings entry and the freeze message.
9. Keep the Wi-Fi-only policy, Pause all, P27's stages and every testTag. C's P35 changes the
   merge inside `core-download`; the service runs it as today.

**Tests**
- JVM: notification text for one download "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left" (must
  fail on the old code: no % text, no speed); several downloads (title with count and total %,
  one line each); unknown size; merge and save stages; speed boundaries (1,023 KB/s → "1023
  KB/s", 1,024 KB/s → "1.0 MB/s", 12.3 MB/s); time-left texts; at most one update a second; the
  service stays in the foreground while a task is `RUNNING` at `MUXING` or `SAVING`; wake lock
  held through download, merge and save and released when paused, finished or failed, Wi-Fi lock
  only while downloading (a fake lock; must fail on the old code: no lock); the service type
  adds media processing while merging on API 35 only; the freeze detector (a late tick → count,
  card shown again, message; must fail on the old code); `onTimeout` pauses and posts the
  notice; the battery card by state, with the HyperOS steps only on Xiaomi; the notifications
  card when notifications are off.
- Instrumented (CI emulator, `app/src/androidTest/java/com/alal/yft/background/`): step 1's
  tests (bytes rise for 20 s after Home and the notification's text contains "%" and "/s"; a
  merge started before Home completes in the background); the finished notice is posted.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX (with step 1's findings).

**Done when:** both background tests are green on the CI emulator; the notification shows %,
speed, time left and the merge's progress; the locks are released when nothing runs; the freeze
detector, notices and cards work.

**Owner check:** (1) start a large download (300 MB or more), switch to Facebook for 2 minutes →
the notification shows "N% · speed · … left" (KB/s under 1 MB/s, MB/s above) and the download
keeps going; screen off for a minute → still going. (2) A long YouTube video: when "Merging … %"
starts, switch to Facebook → the notification's % keeps moving and "Downloaded · …" arrives
without opening YFT. (3) If the card says the phone paused YFT, follow its Xiaomi steps once
and repeat (2).

**Result:** DONE — OWNER CHECK (Agent A, 2026-10-08, `a7e3a73`; merged by P38). The service
is in the foreground while any task is queued, waits or runs, at every stage (download, merge,
MP3, save); partial wake lock `yft:downloads` (10-minute timeout, renewed every minute) while
anything runs, Wi-Fi lock while bytes move; `dataSync`, plus `mediaProcessing` on API 35+ while
a merge, conversion or save runs; `onTimeout` pauses all with a notice; a refused start keeps the
queue with a notice. `FreezeDetector` (1-s tick, more than 10 s late with the wake lock held)
records freezes for the battery card. Notification: "N% · speed · X of Y · … left" (one) or one
line each (several), at most one update a second; "Downloaded · …" / "Download failed · …" on the
"Finished downloads" channel. Downloads: notification and battery cards (Xiaomi, Redmi, POCO
steps); Settings › Background downloads. Fixed on the way: Resume and Retry start the service.
Plan adapted: the emulator tests run on the new code (the old service can't run a test's queue);
a process under instrumentation is never frozen, so HyperOS's freezer is the owner's check; the
slow server is an OkHttp interceptor; Android's pause notices use the "Finished downloads"
channel. Validation: app 790 tests (+44), 0 failures, 66 skipped; lint 0 errors. Regression
proof: 21 new tests fail on the old behaviour. CI green: validation 37793747249, emulator
37793747385 (`BackgroundDownloadInstrumentedTest`, `BackgroundMergeInstrumentedTest`), Preview
APK 37793747336. Details: `docs/SESSION_STATE.md` › Agent A, TEST_MATRIX "Agent A — P34".

### P35 — Faster merge for long videos

Hard · 6–10 h · needs — · **Agent C** · prompt [`C-fast-merge.md`](prompts/C-fast-merge.md)

**Goal:** merging the video and audio tracks of a long recording takes seconds instead of
minutes — for a 1-hour 720p recording from about 2 minutes (Preview #5) to about 10–20 s
(estimate, to be measured) — with the same playable MP4, and today's way as the safety net
(R23).

**Read first:** `core-download/.../AudioVideoMuxEngine.kt` (`AndroidLocalMuxer.mux`,
`copySamples`, P27's merge straight into the destination's file descriptor, the space check,
`MuxCheckpointTracker`, the step-time log line), `DownloadDestination.kt`
(`openFileDescriptorOutput`), `PublicDownloadDestination.kt`, `core-model/.../download/
DownloadModels.kt` (`AudioVideoMuxStage`, `DownloadProgress`), `app/src/androidTest/.../download/
AudioVideoMuxerInstrumentedTest.kt`, `MergeSpeedInstrumentedTest.kt` and `LongFragmentedMp4.kt`
(P27's 20-minute input). Track formats: YouTube's and Facebook's DASH files are fragmented MP4
(`ftyp`, `moov` with `mvex/trex`, `sidx`, then `moof` + `mdat` pairs); some audio and progressive
files are plain MP4 (`moov/…/stbl`).

**Steps**
1. **Measure first** (CI emulator; the same log line on the phone): P27's path split into
   reading (MediaExtractor), writing (MediaMuxer) and file work, per sample, for P27's 20-minute
   input and a 1-hour-sized input (repeat its fragments to about 260,000 samples). Record.
2. **Quick wins on today's path** (it stays for WebM and as the fallback): read a sample's time,
   flags and size once; one reusable buffer sized to the largest sample; progress at most every
   250 ms (no per-sample state or database writes). Measure again.
3. **Fast path: stream copy (G2, `FAST_MERGE=ON`)** for an MP4 video track with an MP4/M4A audio
   track (codecs the merge accepts today; AV1 stays off as today):
   1. Read both inputs in plain Kotlin over `FileChannel` (no Android API, so JVM tests run it):
      the init part (`ftyp`; `moov` › `trak` › `tkhd`, `mdhd` timescale, `hdlr`, `stsd` kept byte
      for byte, `edts/elst`, `mvex/trex` defaults) and the samples — fragmented (`moof` › `traf` ›
      `tfhd`, `tfdt`, `trun`: offset, size, duration, composition offset and sync flag of every
      sample) or plain (`stsz`/`stz2`, `stco`/`co64`, `stsc`, `stts`, `ctts`, `stss`). Unknown
      boxes are skipped. Anything unexpected — encryption (`encv`, `enca`, `senc`, `pssh`: DRM
      stays out), more than one track in a file, broken sizes — means "not supported": use
      today's path.
   2. Write a progressive MP4 straight into the destination (P27's file descriptor, or today's
      temporary file): `ftyp`, one `mdat` (64-bit size above 4 GiB) holding the samples in
      chunks of about one second per track, alternating video and audio, copied in large blocks
      (`FileChannel.transferTo`/`transferFrom` or 1–4 MiB buffers; a whole fragment at once when
      its samples are contiguous), then `moov`: `mvhd`; per track `tkhd`, `edts/elst` (the
      offset between the tracks' starts and the first composition offset, as MediaMuxer writes
      them), `mdia` (`mdhd` with the input's timescale, `hdlr`, `minf` with `vmhd`/`smhd`,
      `dinf`, `stbl` = the input's `stsd` + new `stts`, `ctts` (version 1 when offsets are
      negative), `stss` (video), `stsc`, `stsz`, `stco` or `co64`). All sizes are known before
      writing, so nothing needs a seek.
   3. Check the result with MediaExtractor: two tracks with the inputs' formats (MIME, size,
      sample rate, channels), the same sample counts, durations within one frame, and the first,
      last and a few random samples equal byte for byte to the inputs'. A failed check deletes
      the output and runs today's path once; the log and a later failure's detail keep the
      reason.
   4. Progress: bytes copied ÷ total → P27's "Merging audio and video · N%" (same stage).
4. Space check as in P27 (in place: about video + audio). Track files are deleted only after a
   good merge, as today.
5. WebM (VP9/Opus, 2K/4K) keeps today's MediaMuxer path with the quick wins; a Matroska stream
   copy is backlog (§7).
6. One log line per merge: path (stream copy or today's), sample counts, time per phase, and the
   merge thread's CPU time beside the wall time (a large gap means the phone paused YFT, R24); no
   addresses.

**Tests**
- JVM (`core-download/src/test`, plain Kotlin): small fragmented and plain MP4 inputs built in
  the test → the written tables (`stts`, `ctts`, `stss`, `stsc`, `stsz`, `stco`) match the
  samples; offsets above 4 GiB → `co64` and a 64-bit `mdat` (a virtual source, no real 4 GiB
  file); chunk order alternates by time; encrypted or odd input → "not supported" → today's path
  once; MP4 + M4A uses the stream copy (must fail on the old code: no fast path); progress rises
  to 100%.
- Instrumented (CI emulator, `app/src/androidTest/.../download/`): stream copy against today's
  path on P27's 20-minute input and the 1-hour-sized input → the same tracks, the same sample
  count, every sample byte-equal, times within one tick, and the file plays in Media3 ExoPlayer
  (prepare, duration, seek to the middle); print both times; assert the stream copy is at least
  3× faster on the long input.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX (with the measured times
before and after).

**Done when:** on the CI emulator the stream copy gives identical, playable files at least 3×
faster on the long input; WebM merges unchanged; the fallback works.

**Owner check:** the same 1-hour YouTube live recording at 720p → "Merging … %" ends in about
10–20 s after the tracks (was about 2 minutes); it plays and seeks in the Library and in another
player; a 1080p video and a 2K/4K WebM still save.

**Result:** DONE — OWNER CHECK (Agent C, 2026-10-08, `5f62962`; merged by P38).
`FAST_MERGE=ON`: `AndroidMp4AudioVideoMuxer` merges an MP4 video (`avc1`/`avc3`) and an MP4/M4A
sound (`mp4a`) by `StreamCopyAudioVideoMuxer` (plain-Kotlin `Mp4TrackReader`, chunks of about a
second per track, one 4 MiB buffer, `moov` at the end with all sizes known; 64-bit `mdat` above
4 GiB). `MediaExtractorStreamCopyCheck` reads the file back; a failed check or an unsupported
input runs today's MediaMuxer way once (also for WebM). Plan adapted: negative composition
offsets go into `ctts` v0 and the edit list (Android 7 rejects v1); sample times are checked
against the inputs (MediaMuxer moved a B-frame video's start by 59 ms). CI emulator: 1-hour-sized
input 14.7 s → 1.03 s (14.3×), 20 minutes 3.74 s → 0.25 s. Validation: core-download 166, app 746,
0 failures. Regression proof: 13 of 16 new JVM tests fail without the fast path. CI green:
validation 37801106992, emulator 37801106973, Preview APK 37801107005. Details:
`docs/SESSION_STATE.md` › Agent C.

### P36 — TikTok: Download on the For You feed and video pages

Medium · 3–5 h · needs — · **Agent B** · prompt
[`B-tiktok-fresh-links.md`](prompts/B-tiktok-fresh-links.md)

**Goal:** on tiktok.com in YFT's browser (the owner uses a VPN), Download on the video on screen
— For You, Following, Explore, a profile or a video page — opens the sheet with the video's
qualities and sizes; a TikTok link pasted on Home keeps working (R19, R20).

**Read first:** `core-browser/.../detection/FocusedVideoProbe.kt` (`linkBeside` and the page
script that looks for a video link beside the focused `<video>`) and `FocusedVideoProbeTest.kt`,
`app/.../feature/browser/BrowserViewModel.kt` (the focused lookup, about lines 420–575,
`NO_FOCUSED_VIDEO_NOTICE`), `extractor-sites/.../tiktok/TikTokPageParser.kt` (`VIDEO_DETAIL_KEY`),
`TikTokExtractor.kt` (`mediaContext`), `TikTokUrls.kt`, `app/.../detection/SiteAdapterCoordinator.kt`
and `HeadlessIdentity.kt` (which user agent and cookies reach the adapter),
`extractor-sites/src/test/resources/fixtures/tiktok/`, `app/src/androidTest/.../browser/
FocusedVideoProbeInstrumentedTest.kt` and `app/src/androidTest/assets/focused-video/tiktok-feed.html`
(the old feed layout).

**Steps**
1. **Live check first** (`scripts/live-check.sh`; TikTok changes often): the For You page with a
   phone user agent (R19's card structure, the `xgwrapper-<n>-<id>` id, no `/video/` links) and
   one public video page with a phone and a desktop user agent (`webapp.reflow.video.detail` vs
   `webapp.video-detail`; the media host's answer with and without the page answer's cookies).
   Report markers, statuses and counts only. Confirmed in Plan Mode on 2026-10-08.
2. **The focused video without a link.** When no video-shaped link is beside the focused video,
   FocusedVideoProbe takes the video id from the nearest ancestor whose id is
   `xgwrapper-<n>-<15–22 digits>` and the handle from the card's `a[href^="/@"]` (the card is the
   nearest `[data-e2e="recommend-list-item-container"]`, `[data-e2e="feed-video"]` or
   `article`) → `https://www.tiktok.com/@<handle>/video/<id>`; without a handle →
   `https://www.tiktok.com/@/video/<id>` (the page answered 200 in Plan Mode; `TikTokUrls` must
   accept it). Only on TikTok's hosts; the page's own `/video/<id>` address (a video page or its
   modal) still comes first; other sites are unchanged.
3. **The phone page's data.** `TikTokPageParser` reads `webapp.video-detail` and, when it is
   missing, `webapp.reflow.video.detail`: title, author, length, picture, play and download
   addresses; quality rows from `bitrateInfo` when present.
4. **Qualities (G6, `TIKTOK_QUALITIES=DESKTOP`).** When the page has no `bitrateInfo` (the phone
   page), the adapter asks the same video page once more with the desktop user agent
   (`HeadlessIdentity`) and uses its `bitrateInfo` and its answer's cookies; if that fails, the
   phone page's play or download address is the one quality. `PAGE` skips the second request.
5. **Media cookies (R20).** `mediaContext` keeps the WebView's cookie header, but the cookies of
   the page answer whose addresses are used replace same-named ones and are added when missing;
   the media request uses the user agent that fetched that page.
6. The "No video on screen to download" notice stays only for pages that really have no video
   on screen; Home's lookups of pasted links keep working.

**Tests**
- JVM: a For You card (structure only, neutral text, blob video inside `xgwrapper-…`) → the link
  `https://www.tiktok.com/@handle/video/<id>` (must fail on the old code); no handle → `/@/video/<id>`;
  a reflow page fixture (redacted) → title, length and play address (must fail on the old code);
  the desktop fixtures still parse; phone page without qualities → the desktop page's qualities;
  the cookie merge (a stale same-named WebView cookie loses; must fail on the old code).
- Instrumented (CI emulator): `FocusedVideoProbeInstrumentedTest` with a new feed page in
  today's layout (`app/src/androidTest/assets/focused-video/tiktok-foryou.html`) → the link is
  built; the old layout still works.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX; `SUPPORT_MATRIX.md` TikTok
row.

**Done when:** the feed card and the reflow page tests pass; the live check shows today's
markers; CI emulator green.

**Owner check (VPN):** tiktok.com/foryou → let a video play → Download → the sheet with
qualities and sizes → the download plays; a video from a profile; Home: paste a TikTok link →
the sheet.

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-08, `9689062`; merged by P38).
`FocusedVideoProbe` reads the focused For You video's id from its player box (`xgwrapper-…`,
desktop) or the active slide's page data (phone layout) and the author from the card when shown,
else `https://www.tiktok.com/@/video/<id>` (author-less links are made canonical that way: live,
`/video/<id>` without `@` goes to `/404`). `TikTokPageParser` reads `webapp.video-detail`, else
`webapp.reflow.video.detail`; a phone page without qualities is asked once more with the desktop
agent; media requests take the page answer's fresh TikTok cookies. Validation 1229 tests, 0
failures. Regression proof: 11 failures on the old files. CI green: validation 37790464506,
emulator 37790464540, Preview APK 37790464487. Details: `docs/SESSION_STATE.md` › Agent B.

### P37 — Other sites: fresh links instead of HTTP 410

Medium · 4–6 h · needs — (after P36 on B's branch) · **Agent B** · prompt
[`B-tiktok-fresh-links.md`](prompts/B-tiktok-fresh-links.md)

**Goal:** on a site without an adapter, when the page's links answer 401, 403, 404 or 410, YFT
finds fresh ones by itself — the player's own requests, a quiet second read of the page — before
it shows an error; Try again really reads the page again; "Reload page and try again" does in
one tap what the owner did by hand; the Details say which link failed and why (R16–R18).

**Read first:** `app/.../feature/quickdownload/QuickDownloadViewModel.kt` (`retry` →
`readPageAgainThenLoad`, about line 537; P29's next video), `QuickDownloadFailures.kt` (Details
lines: step, host, status), `QuickDownloadScreen.kt`, `app/.../feature/detectedmedia/
DetectedMediaStore.kt` (`readPageAgain`, Home only), `app/.../feature/home/LinkInspector.kt`,
`core-browser/.../detection/HeadlessPageFetcher.kt` (no cookies, by design),
`PlayerSetupScanner.kt` (page-script links), `BrowserObservationMapper.kt` (requests the player
made), `core-browser/.../session/PageCandidateStore.kt`, `core-model/.../media/MediaGroups.kt`
(`mainVideo`, `refreshed`, `PageMediaRole.MAIN`), `app/.../feature/browser/BrowserViewModel.kt` and
`BrowserScreen.kt` (`onReload = { webView?.reload() }`), `app/src/androidTest/.../browser/
detection/PrerollInstrumentedTest.kt`.

**Steps**
1. **Evidence in Details first.** For every attempt add, beside step, host and status: "Link
   from: page script / player request / page read again", "Link age: 12 min" (since the page or
   the request was seen) and "Link expiry: passed / not passed / none" (a numeric expiry-like
   query value such as `validto`, `expires`, `exp`, `e`, `x-expires`, compared with the phone's
   clock; never the value or the address). The owner's next screenshot then tells R17's causes
   apart.
2. **The player's fresh address first (R18).** For the same video (the same path without its
   query, or the same quality in the same group) an address the page's player itself requested
   (newest first) comes before a page-script link of that quality; when a page-script link
   answers 401/403/404/410, the player's address for that video is tried before P29's next
   video.
3. **Quiet re-read (G7, `REREAD=2`).** When the links answer 401/403/404/410 and no fresh player
   address exists, read the page again in the background — the same address, `Cache-Control:
   no-cache`, the WebView's user agent and the tab's cookies for that site (CookieManager, never
   logged) — up to 2 times, find the same video in it (`MediaGroups.refreshed`: path, title,
   length) and prepare its new addresses; then P29's next video. Build this as a separate mode
   beside `HeadlessPageFetcher` (Home's cookie-free fetch stays as it is). The tab's cookies carry
   only what the user did on the site himself (for example his own tap on an age notice); YFT
   never taps, skips or fakes a notice. A page that answers with a notice or a check (no player
   data) is not used: go to step 5.
4. **Try again in the browser** runs step 3 (R16: today it reuses the same stale page).
5. **"Reload page and try again"** (testTag `quick-reload-retry`), shown with the error when steps
   2–4 found nothing fresh: it closes the sheet, reloads the browser tab once without the cache
   (main thread: `cacheMode = LOAD_NO_CACHE` for that load, back to the default when it
   finished), and reopens the sheet for the same video when the page's video is found again
   (P28's rules, within about 15 s); otherwise "The site gave no new link. Play the video for a
   moment, then tap Download again." Pages Home found keep Home's re-read.
6. Keep P28 and P29 (the page's video before ads, the next video), sites with adapters
   (`adapterSite` pages skip all this) and Home's lookups as they are.

**Tests**
- JVM: the Details lines (source, age, expiry passed / not passed / none) never contain an
  address or a query value; a player request outranks a page-script link of the same quality
  (must fail on the old code); after a 410 the player's address is tried before the next video;
  browser Try again reads the page again and prepares the new addresses (must fail on the old
  code: same page reused); at most 2 re-reads; a notice page is not used and the reload button
  shows; the reload flow in the view model (reload asked, the sheet reopens when the video
  appears, the message after the timeout); `REREAD=0`.
- Instrumented (CI emulator, `app/src/androidTest/.../browser/detection/`): a fixture site served
  by the test: its player script names a link that answers 410 while the player requests a
  working one → the sheet prepares the player's; the second page load gives a new working link →
  Try again prepares it.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX; `SUPPORT_MATRIX.md` row for
other sites.

**Done when:** the tests pass, the CI emulator test is green, and Javtiful-like pages (P28's
fixtures) behave as before.

**Owner check:** the site from Preview #5 where some videos showed HTTP 410 → Download opens a
working video without manual reloads (or Try again does); if not, "Reload page and try again"
works in one tap; a screenshot of Details when it still fails; Javtiful still works.

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-08, `10957b7`; merged by P38). Candidates
carry `LinkOrigin` and `LinkExpiry`; the player's own request wins over the script's link. On a
gone link (HTTP 410/403/404) Quick Download tries the page's newest link of the same video, the
player's link, a quiet re-read of the page (`TabPageReader`, `REREAD=2`, the tab's agent and
same-site cookies, no cache), then the next video; "Reload page and try again" reloads without
cache and waits up to 15 s for a new link. A stated row whose file check says "gone" is not
offered; a link gone at Download runs the same chain. Details list every attempt (origin, age,
expiry), never the address. Validation 1257 tests, 0 failures. Regression proof: 18 + 2
failures on the old files. CI: two emulator runs failed on `FreshLinkInstrumentedTest`
(a test wait, then a real gap: the stated row) and were fixed; `10957b7` green: validation
37804865935, emulator 37804865954 (32 tests, 0 failures), Preview APK 37804865937. Details:
`docs/SESSION_STATE.md` › Agent B.

### P38 — Merge and Preview #6

Medium · 2–3 h · needs P34–P37 `READY FOR MERGE` · **Agent A (integrator)** · prompt
[`M-merge-preview6.md`](prompts/M-merge-preview6.md)

**Goal:** one branch with A, B and C, fully validated, and Preview #6 on the owner's phone.

**Steps**
1. Start only when the SESSION_STATE sections of A, B and C say `READY FOR MERGE` and their
   newest code commits have green CI (checkpoint validation, emulator smoke, Preview APK; later
   docs-only commits have no runs, §0.3). Otherwise list what is missing and stop.
2. On `work/phase-14-integration` (pull it first): `git merge --no-ff` A's branch, then B's,
   then C's. Doc conflicts: keep both sides. A code conflict: stop and report the files.
   Hand-offs left open: do them when they are small and say so, else list them.
3. Full validation (640 MiB metaspace, §0.3), `:app:assembleRelease`, line check.
4. Docs: copy each agent's status and Results into §1 and §5; `docs/HANDOFF.md`,
   `docs/PHASE_STATUS.md`, the SESSION_STATE Overview, CHANGELOG (fold the three agent sections
   into Added/Changed/Fixed), SUPPORT_MATRIX, TEST_MATRIX (a Phase 14 summary row).
5. Checkpoint; CI: checkpoint validation, emulator smoke and the Preview APK run →
   **Preview #6**: its link and the §6 list to the owner in Burmese. Then stop. `main` is
   fast-forwarded only with `MAIN=OK`; P8 only with the owner's OK.

**Result:** DONE (Agent A, 2026-10-08). A (`6325f37`, code `a7e3a73`) → B (`fd66038`, code
`10957b7`) → C (`1f70d26`, code `5f62962`) merged into `work/phase-14-integration` with
`--no-ff`; no conflicts (only CHANGELOG, SESSION_STATE and TEST_MATRIX are changed by more than
one agent); no open hand-offs (A's `DownloadRuntimeModule` keeps `AndroidMp4AudioVideoMuxer()`,
which now stream-copies). Full validation: 1532 tests, 0 failures, 66 skipped (was 1435 at P33);
lint 0 errors; `:app:assembleRelease` OK; line check clean. The owner asked to push `main` after
the merge and to build the test-key Preview: `main` is fast-forwarded to the validated merge
after its CI was green (no tag, no signed release). CI of `4da3e61`: checkpoint validation
37811403961, emulator smoke 37811403940, Preview APK 37811403962 = **Preview #6**, all green.

### P8 — Signed release 1.0.0-beta.4

Easy · 1–2 h · needs P38, Preview #6 and the owner's OK · prompt
[`P8-signed-beta4.md`](prompts/P8-signed-beta4.md)

`yft.versionName=1.0.0-beta.4`, `yft.versionCode=4`, `docs/release/1.0.0-beta.4.md`, CHANGELOG
section; full validation; fast-forward `main` to `work/phase-14-integration`, tag
`v1.0.0-beta.4`; `release-draft.yml` builds the APK signed with the release key; record size,
SHA-256 and certificate. Merge, tag and signing need the owner's OK for this task
(`docs/RELEASE.md`).

**Result:** —

## 6. Owner phone checklist

Install `yft-preview-apk` from the Preview APK run the agent sends; uninstall the older YFT
Preview first (each run has a new test key).

**Preview #6 (after P38)**

1. **Background (P34):** a large download (300 MB or more) → Facebook for 2 minutes → the
   notification shows "N% · speed · … left" (KB/s under 1 MB/s, MB/s above) and the download
   keeps going; screen off for a minute → still going; "Downloaded · …" at the end.
2. **Merge in the background (P34):** a long YouTube video → when "Merging … %" starts, switch to
   Facebook → the % in the notification keeps moving and the download finishes without opening
   YFT. If YFT's card says the phone paused it, follow the Xiaomi steps once and try again.
3. **Faster merge (P35):** a 1-hour YouTube live recording at 720p → "Merging … %" ends in about
   10–20 s after the tracks (was about 2 minutes); the file plays and seeks; a 1080p video and a
   2K/4K WebM still save.
4. **TikTok with a VPN (P36):** tiktok.com/foryou → let a video play → Download → the sheet with
   qualities and sizes → the file plays; a video from a profile; Home: paste a TikTok link.
5. **Fresh links (P37):** the site where some videos showed HTTP 410 → Download opens a working
   video without manual reloads; Try again works; if not, "Reload page and try again" works in
   one tap; Javtiful still works.
6. **Phase 13 still works:** YouTube 1080p with sound, a Facebook reel, other sites' video
   instead of the ad, Google search, History, pop-up blocking.
7. About › Last crash report: none.

When something fails, a screenshot of the sheet's **Details** (lookups) or of the download's
**Details** (saving) tells the agents what happened.

## 7. Backlog

Advice on the items the owner asked about; each waits for his decision:

- **B1 — Download button inside YouTube's page** (under the video, beside Like and Share).
  Advice: not now. P13's wide button gives the same tap without touching YouTube's page. Later,
  if the owner still wants it: a page script adds a button to YouTube's action row and talks to
  the app only through an origin-restricted `WebMessageListener` (`https://m.youtube.com`); when
  YouTube changes its page and the spot is gone, P13's button stays. About 4–6 h plus upkeep.
  Owner decision: —
- **B2 — a YouTube page of YFT's own, like Snaptube's** (own player, search and comments).
  Advice: no. It means rebuilding YouTube's player, search and comments on YouTube's internal
  interface, which changes often: weeks of work and frequent breakage. Owner decision: —
- **B3 — Facebook formats from the page in YFT's browser.** Advice: only if needed. If a Facebook
  lookup still misses qualities the browser page has, a spike reads only the format markers from
  the page the browser already loaded → parser + fixtures. About 6–10 h. Owner decision: —
- **B4 — TikTok on the owner's phone.** Settled on 2026-10-08: the owner tests TikTok with a VPN
  (P36). Agents keep fixtures, the CI emulator and sandbox live checks.
- **B5 — Site fixes without a new APK** (owner's idea, 2026-10-08). Advice: later, and only for
  data, not code: a small signed rules file on GitHub (site patterns, ad hosts, player-script
  names) that YFT downloads and checks with a key built into the app, so a site's changed page
  can be followed without a new APK; changes in Kotlin code still need an update. About 1–2 days
  with tests. Owner decision: —

Other items:

- TikTok: when the adapter finds nothing, offer the file the browser saw playing (blob videos
  cannot be saved; a direct media request can).
- Long downloads on Android 14+: a user-initiated data transfer job (`JobScheduler`) instead of
  the `dataSync` foreground service (no 6-hour limit), if P34's `onTimeout` notice appears.
- Refresh a download's link in the middle of the download (a 403/410 after an hour) by reading
  its page again, like P37 does for the sheet.
- A Matroska (WebM) stream copy for 2K/4K merges, like P35's MP4 path.
- Open the Downloads tab from the notification (needs `MainActivity` and navigation).
- Share target: open links shared from other apps (Android share sheet) in Home's lookup, so
  they open the download sheet like a pasted link.
- AV1 merges: off since P4 (the API 34 emulator's muxer failed), so Facebook's AV1-only sizes and
  YouTube's AV1-only 2K/4K stay hidden until an AV1 merge is proven on a phone.
- YouTube: use the page player's own proof-of-origin token from the browser (ADR-006, not done);
  SABR streaming is not planned.
- Android 9 and older save through the legacy public folder; P20's instrumented test covers the
  API 34 MediaStore path only.
- Instagram and X adapters (generic detection only today).
- Background playback in the Library; saving to a folder chosen with the system picker.
- One shared ad list for media detection and pop-ups/redirects, updated from one place.
- A private tab (no history, no cookies kept) and a per-site "allow pop-ups" list.
- Adapters for named adult sites (Phase 13 F1 option C) only if the owner chooses it.

## 8. Done before Phase 14

**Phase 13** (P27–P33, 2026-10-07) is merged into `main` (`436aa90`, docs `5a5bddb`; no tag).
Full plan, Results and findings: `git show 5a5bddb:docs/FIX_ADD_PLAN.md`; prompts:
`git show 5a5bddb:docs/prompts/`; per-task Results and validation:
`git show 5a5bddb:docs/SESSION_STATE.md`. Owner test: Preview #5 (run
https://github.com/Alalkipgen/YFT/actions/runs/37575586233, 2026-10-08) → the five items of
§2, Phase 14.

| ID | Task | Commits | Status |
| --- | --- | --- | --- |
| P27 | YouTube: no long wait at 99% (merge progress, merge straight into the file) | `d978201`, `83478f0`, `89e985a` | Preview #5: progress shown; a 1-hour merge still takes about 2 min → P35; stops in the background → P34 |
| P28 | Other sites: the page's video, not the ad before it | `679ec78`, `f334f55`, `4f9447b`, `a47926d` | Preview #5 OK; some pages' links answer HTTP 410 → P37 |
| P29 | Other sites: the next video when one fails | `32d2472`, `0b1a11a` | Preview #5: on those pages the next video answers 410 too and Try again repeats it → P37 |
| P30 | Browser: Google search | `521b0b6` | Preview #5: no problem reported |
| P31 | Browser history | `e9899f2` | Preview #5: no problem reported |
| P32 | Block pop-ups and ad redirects | `d69811a`, `81c3e97` | Preview #5: no problem reported |
| P33 | Merge A → B → C, Preview #5 | `cd34c6c`, `2a874be`, `fd0a04e`, `436aa90`, `5a5bddb` | DONE (2026-10-07): 1435 tests, 0 failures, 66 skipped; `main` fast-forwarded |
| P8 | Signed `1.0.0-beta.4` | — | moved behind Phase 14 ([§1](#1-status-board)) |

**Phase 12** (P20–P26, 2026-10-06) is merged into `main` at `bc806f9` (no tag): saving video
files, Retry and failure details, every YouTube and Facebook quality, other sites' main video,
one sheet everywhere, Preview #4. Plan, Results and prompts: `git show bc806f9:docs/FIX_ADD_PLAN.md`,
`git show bc806f9:docs/prompts/`, `git show bc806f9:docs/SESSION_STATE.md`.

**Phase 11** (P0–P19, 2026-10-04 to 2026-10-06) is merged into `main` at `4db6c2b` (no tag).
Plan, Results and prompts: `git show 4db6c2b:docs/FIX_ADD_PLAN.md`,
`git show 4db6c2b:docs/prompts/`, `git show 4db6c2b:docs/SESSION_STATE.md`.

**Phases 8–10** (T01–T19, 2026-10-03 to 2026-10-04) are complete and released as
`1.0.0-beta.3`. Full plan: `git show 2f6284f:docs/FIX_PLAN.md`; prompts:
`git show 2f6284f:docs/prompts/`.

**T19 — release record (DONE, 2026-10-04):** `main` fast-forwarded to `2f6284f`; tag
`v1.0.0-beta.3`; Release draft https://github.com/Alalkipgen/YFT/actions/runs/37204457527 created
the draft pre-release: `video-downloader-1.0.0-beta.3.apk`, 6,334,176 bytes, SHA-256
`8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
`3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
(same key as beta.1 and beta.2), source commit `2f6284f`.
