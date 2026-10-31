# YFT Fix & Add Plan — Phase 15 (Preview #6 field fixes: TikTok, YouTube start, Delete file, ads)

Owner phone test of **Preview #6** (2026-10-09, Xiaomi phone with HyperOS, TikTok through a VPN;
Preview APK run https://github.com/Alalkipgen/YFT/actions/runs/37811403962, merge commit `4da3e61`;
`main` = `a9eea7b`). Phase 14 (P34–P38: downloads and merges in the background with speed in the
notification, a stream-copy merge, TikTok's For You feed, fresh links instead of HTTP 410) is merged
into `main`. The owner found four things to fix or add (seven items in §2): **TikTok** says "TikTok
changed its page format. Falling back to generic detection." on every video in YFT's browser (Try
again fails, nothing can be downloaded) and on many links on Home (Home then shows "Quality unknown"
rows, and a TikTok row can fail with "This quality is not available now"); a **YouTube live
recording** waits a long time at "Downloading" before the % moves; Downloads needs **Delete** for
the saved file, not only "Remove from list"; and on other sites (an adult site) the sheet
**sometimes shows the pre-roll ad** (0:30) instead of the page's video. Phase 15 fixes these with
**three agents working at the same time** (A, B, C, §0.7), then one merge and Preview #7. Written in
Plan Mode on 2026-10-09; work starts when the owner pastes the prompts in
[`prompts/`](prompts/README.md). Phase 14's plan and prompts stay in Git history: `git show
a9eea7b:docs/FIX_ADD_PLAN.md` and `git show a9eea7b:docs/prompts/` (summary in
[§8](#8-done-before-phase-15)).

## Contents

0. [How to work](#0-how-to-work) (0.7: [three agents in parallel](#07-three-agents-in-parallel))
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Tasks](#5-tasks)
6. [Owner phone checklist](#6-owner-phone-checklist)
7. [Backlog](#7-backlog)
8. [Done before Phase 15](#8-done-before-phase-15)

## 0. How to work

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out **your agent's branch** (§0.7) in **your own folder** (Notion sandbox:
   `/data/YFT-A`, `/data/YFT-B` or `/data/YFT-C`, see the prompts) and pull it. On the first
   start create it from `origin/work/phase-15-integration` (the plan commit on top of `main`
   `a9eea7b`). Never work on another agent's branch, on `work/phase-15-integration` (only P44
   does) or on `main`.
3. Read §0, §3, §4, your tasks in §5 with their **Read first** files, and your own section of
   `docs/SESSION_STATE.md`.
4. Set up the environment when it is missing (§0.3), then run your scope's validation before
   editing, so you know the starting state.
5. Record the task as `IN PROGRESS` in **your section** of `docs/SESSION_STATE.md`. Agents A, B
   and C never edit this plan file (`docs/FIX_ADD_PLAN.md`) or `docs/prompts/`: P44 copies
   status and Results from SESSION_STATE into §1 and §5.
6. Do the **Steps** in order. Stay inside the task and inside your files (§0.7); anything else
   goes to your SESSION_STATE section as a hand-off or a backlog note. When the code shows that a
   step is wrong, adapt it and say so in the Result ("Plan adapted: …").
7. Add the listed tests. A regression test must fail on the old code (§0.3, regression proof).
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists (only your sections of shared docs, §0.7), write the Result in
   your SESSION_STATE section (status `DONE (date)` or `OWNER CHECK`) and checkpoint with
   `scripts/checkpoint.sh "P4x: summary"`. Check CI for the pushed commit and fix a red run.
10. Report to the owner in Burmese (§0.5), then continue with your next task without waiting.
    After your last task set your section to `READY FOR MERGE` and stop. Stop earlier only for a
    failure you cannot fix, a decision §3 marks `PENDING`, or a hand-off that blocks you.

### 0.2 Rules for every task

- Product rules ([ADR-006](decisions/ADR-006-owner-override-any-working-method.md), owner
  2026-10-03, confirmed for TikTok on 2026-10-09: "bypass it like YouTube, whatever works"):
  **any working technique for public videos** — a Chrome identity, the site's own scripts and
  pages in a WebView (as YouTube's proof-of-origin token already does), the cookies the user's
  own browser already has, the site's own media requests. Still out: DRM, paywalls, private or
  login-only posts and age gates; adapters never sign in. A page's age or identity check is the
  user's own tap in the browser; agents never automate one, not even in a live check.
- Never log, print, commit or put into fixtures: cookies, tokens, visitor data, `Authorization`
  values, signed media or image URLs (CDN query strings), keystores, `local.properties` or
  `.env`. Fixtures keep hosts and paths and replace signed query values with `REDACTED`; logs and
  lookup details name hosts, not full addresses.
- Kotlin lines stay within 100 characters. Keep every existing `testTag` (new tags are named in
  the task). No unrelated refactors. Public functions and types that another agent's code uses
  change only by addition (§0.7 "Contracts").
- `WebView` and `WebSettings` methods run on the main thread only; `shouldInterceptRequest` and
  `@JavascriptInterface` methods run on other threads.
- Site tasks need a live check of a public page (`scripts/live-check.sh` or a short script in
  `/data/tmp`); report status, host, path, sizes and markers only. The sandbox is a US
  data-centre network: TikTok answers it normally (live checks 2026-10-09, §4), but the owner's
  phone reaches TikTok through a VPN outside the US and gets other answers, so **the owner's
  phone is the final proof for TikTok** and every TikTok failure must explain itself in Details
  (P39). YouTube often answers the sandbox with a bot check.
- Sites for adults (P43): public pages only. An age or identity notice is the user's own tap on
  his phone; an agent never clicks one, not even in a live check, and skips a page that shows
  one (many adult sites block US data-centre addresses: use fixtures). Fixtures keep the page
  structure (players, scripts, ad requests, links) but replace titles, names, descriptions and
  pictures with neutral text and blank images; nothing explicit enters the repository, logs or
  reports (report hosts, lengths, heights, statuses and counts only).
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
install what is missing (an empty `/data/gradle-home` only means the first build downloads its
dependencies). Agents may share one computer: each works in its own folder (`/data/YFT-A`,
`-B`, `-C`; run `./gradlew` in your folder), stops only Gradle daemons it started (no `pkill` of
another agent's build) and, on a 4 GiB machine, waits until no other Gradle build runs
(`pgrep -af "[G]radleDaemon"`): one Gradle command at a time on the computer. The full
validation needs Gradle metaspace 640 MiB:
`export GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"` or
`-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=640m"`.

**SSH key.** Pushes use SSH. `/data/.ssh/CURRENT_KEY` holds the path of the computer's working
deploy key (Plan Mode wrote it; the key pushed on 2026-10-09). Use that key; when the file or
the key is missing, or a push says "Permission denied", make a **new** key
(`ssh-keygen -t ed25519 -N "" -f /data/.ssh/yft_<agent>_<date> -C "yft-<agent>-<date>"`, then
write its path into `/data/.ssh/CURRENT_KEY`), show the public line to the owner and wait until
he says he added it as a deploy key with write access. Never search the computer for other
keys. Then, in your folder:
`git remote set-url origin git@github.com:Alalkipgen/YFT.git` and
`git config core.sshCommand "ssh -i $(cat /data/.ssh/CURRENT_KEY) -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"`.

| Scope | Command |
| --- | --- |
| Agent A (P39, P40) | `./gradlew --no-daemon --continue :extractor-sites:test :extractor-api:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent B (P41, P42) | `./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent C (P43) | `./gradlew --no-daemon --continue :core-model:test :extractor-generic:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Full (P44, P8) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` |
| Scripts, docs-only checkpoints | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests` |
| Line length (must print nothing) | `git diff -U0 origin/main -- '*.kt' '*.kts' \| grep '^+[^+]' \| LC_ALL=C.UTF-8 awk 'length > 101'` |

Phase 15 start (P38, 2026-10-08, `4da3e61`; `main` `a9eea7b` adds docs only): full validation
1532 tests, 0 failures, 66 skipped; lint 0 errors; `:app:assembleRelease` OK.

**Regression proof.** Copy the files you changed to `/data/bak/<task>/`, put the old version
back (for example `git show HEAD:<path> > <path>` before your commit), run the new tests and see
them fail, then restore your version with `cp` and check it with `cmp`. Name the tests that
failed on the old code in the Result. An instrumented test (`app/src/androidTest`) cannot run in
the sandbox: its JVM twin is the regression proof, and the CI emulator run must pass with the fix.

**CI.** `checkpoint-validation` runs on every `work/**` push **that changes more than
documentation**: a push whose files are all under `docs/` or end in `.md` starts no CI
(`paths-ignore`), so a docs-only checkpoint (a status line, `READY FOR MERGE`) has no run.
"Green CI" therefore means the runs of your newest commit that changed code, scripts or Gradle
files. `emulator-smoke` (API 34, `:app:connectedDebugAndroidTest`) and `preview-apk` run on
`work/phase-*` pushes that change code (`app/**`, `core-*/**`, `extractor-*/**`, Gradle files).
Status for a branch:

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
`git log --oneline --grep "P39:"` finds a task's commits.

### 0.5 Report to the owner (Burmese, short)

```text
Agent: A / B / C — Task: P4x — <title> — Level: Easy / Medium / Hard — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run links> (debug APK: Artifacts › yft-debug-apk;
  preview: Preview APK run › yft-preview-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: … (hand-offs to other agents, if any)
နောက်တစ်ဆင့်: P4y / READY FOR MERGE
```

### 0.6 Phone builds for the owner

Every green checkpoint run uploads `yft-debug-apk` (14 days; app ID `com.alal.yft.debug`). Every
push that changes code also runs **Preview APK (test key)**, which uploads `yft-preview-apk`: the
minified release build as "YFT Preview" (`com.alal.yft.preview`), signed with a test key made in
that job, so the owner uninstalls the older YFT Preview before installing a newer one. Each
agent's branch gets its own preview runs; the owner may try one early (Agent A's TikTok preview
is worth trying before the merge, since only his phone and VPN show TikTok's real answer), but
the phone test that counts is **Preview #7** = the Preview APK run of P44's merge commit on
`work/phase-15-integration` (§6). Only P8 signs with the release key.

### 0.7 Three agents in parallel

**Branches.** All three start from `origin/work/phase-15-integration` at the plan commit.

| Agent | Tasks (in order) | Branch | Area |
| --- | --- | --- | --- |
| A | P39 → P40 | `work/phase-15-tiktok` | TikTok: the adapter, TikTok's own page in a WebView, the browser's and Home's TikTok lookups, the media resolver |
| B | P41 → P42, later P44 (integrator) and P8 | `work/phase-15-downloads` | Download engines (YouTube's fast start), Delete file in Downloads and the Library |
| C | P43 | `work/phase-15-ads` | Ads: recognizing a pre-roll and the sheet's choice of the page's video |
| B (P44) | merge B → C → A, Preview #7 | `work/phase-15-integration` | Integration only |

Agent A has the long track (P39 + P40, about 13–19 h); B (P41 + P42, about 7–11 h) then waits
for A and C and does P44; C (P43, about 5–7 h) is the shortest. In two-agent mode (below) B
also does C's task.

**Files each agent may change** (tests beside them under `src/test/` or `src/androidTest/`
included). Everything not listed belongs to nobody: change it only through a hand-off.

| Agent | Owns |
| --- | --- |
| A | `extractor-sites/**` (TikTok; other adapters only for a shared helper TikTok needs); `extractor-api/**` (additions only, §0.7 "Contracts"); `app/src/main/java/com/alal/yft/detection/**`; `core-browser/**` except C's files below; `core-media/**`; `app/src/main/java/com/alal/yft/feature/{browser,home,detectedmedia}/**`; in C's `app/.../feature/quickdownload/QuickDownloadViewModel.kt` only the function `lookupState` (P39: a failed lookup's Details); `app/src/androidTest/java/com/alal/yft/tiktok/**` and `app/src/androidTest/assets/tiktok/**` (new), `app/src/androidTest/java/com/alal/yft/browser/FocusedVideoProbeInstrumentedTest.kt` and `app/src/androidTest/assets/focused-video/**`; `gradle/libs.versions.toml`, `app/build.gradle.kts` and `core-browser/build.gradle.kts` only for the `androidx.webkit` lines of P40; docs `SUPPORT_MATRIX.md` |
| B | `core-download/**`; `core-model/src/main/kotlin/com/alal/yft/core/model/download/**`; `app/src/main/java/com/alal/yft/download/**`; `app/src/main/java/com/alal/yft/feature/{downloads,library}/**`; `app/src/androidTest/java/com/alal/yft/{download,background,delete}/**` (`delete` is new) and `app/src/androidTest/assets/{mux,mp4,mp3}/**` |
| C | `core-browser/src/main/java/com/alal/yft/core/browser/detection/{VastAdTracker,BrowserObservationMapper,PageFactsReader,PlayerSetupScanner,HtmlMediaScanner}.kt`, plus new ad files beside them (names starting with `Ad`); `core-model/src/main/kotlin/com/alal/yft/core/model/media/**`; `extractor-generic/**`; `app/src/main/java/com/alal/yft/feature/quickdownload/**` except the function `lookupState` (A's); `app/src/androidTest/java/com/alal/yft/browser/**` except A's `FocusedVideoProbeInstrumentedTest.kt`, and `app/src/androidTest/assets/{browser-detection,ads}/**` (`ads` is new) |
| Nobody (P44/P8 only) | `.github/workflows/**`, `gradle.properties`, Gradle files except A's lines above, `app/src/main/AndroidManifest.xml`, `app/src/main/res/**`, other `app` packages (`feature/settings`, `thumbnail`, `diagnostics`, `ui/**`, `MainActivity`, `YftApplication`), `core-data/**`, `core-model/.../settings/**`, `smoke/**` androidTests, shared test helpers (`app/src/test/java/com/alal/yft/testing/**`: add new helpers in your own test folders), `AGENTS.md`, `README.md`, `docs/FIX_ADD_PLAN.md`, `docs/prompts/**`, `docs/HANDOFF.md`, `docs/PHASE_STATUS.md`, `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROJECT_CONTEXT.md`, ADRs |

**Shared docs — own section only.** Each agent edits only the section with its letter, which
the plan commit created; nothing above or below it:

- `docs/SESSION_STATE.md`: `## Agent A …`, `## Agent B …`, `## Agent C …` (status, Results,
  validation, CI links, hand-offs). The `## Overview` section is P44's.
- `CHANGELOG.md` under `## [Unreleased]`: `### Phase 15 — Agent A (P39, P40)`, `### Phase 15 —
  Agent B (P41, P42)` and `### Phase 15 — Agent C (P43)`. Replace the placeholder line, then add
  bullets below it. P44 folds them into Added/Changed/Fixed.
- `docs/TEST_MATRIX.md`: `### Agent A — P39, P40` (and B, C) under `## Phase 15`.

**Contracts** (code other agents use; change only by adding, with defaults; never rename,
remove or change a meaning):

- A → C: `SiteExtractionRequest`, `SiteExtractionResult`, `SiteExtractionFailure` and the
  adapter outcome the sheet reads (`extractor-api`, `app/.../detection`). P40 adds
  `SiteExtractionRequest.pageData` (default `null`); new failure reasons are added at the end of
  the enum, and the sheet shows an unknown reason with the adapter's own message.
- C → A: the media model in `core-model/.../media/` (`MediaCandidate` and its fields,
  `MediaGroups`, `FreshLinks`, `PageMediaRole`, `BrowserRequestContext`) and the browser
  candidates that `BrowserObservationMapper` makes; A's TikTok rows use them as they are. C's ad
  rule (P43) leaves pages of sites with adapters (`adapterSite`, P37) as they are, so TikTok's
  rows (P39 step 8) never meet it; A does not call it.
- A → C: `PageVideoLookup` and `DetectedMediaStore` (A adds `PageVideoLookup.details`, default
  empty); the sheet keeps reading them as today.
- B → A, C: the public `DownloadQueue` API, `DownloadTask`, `DownloadProgress`,
  `DownloadFailure`, `DownloadFailureReason`, `AudioVideoMuxStage`, `DownloadDestination`,
  `DownloadEnqueuer` and `DownloadPlanFactory` (C's sheet enqueues; A's TikTok variants carry
  their request headers, Referer and the cookies of TikTok's answer, through the existing
  variant headers, so B needs no change for them).
- `YftFormat` (`ui/format`) stays as it is (nobody's file this phase).

**Hand-offs.** When you need a change in a file you do not own, do not make it. Write
"Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and in your
report; the owner passes it on. Prefer a fix inside your own files when one exists.

**Merge (P44).** Agent B merges the three branches into `work/phase-15-integration`
(`--no-ff`) in this order: `work/phase-15-downloads` (B), then `work/phase-15-ads` (C), then
`work/phase-15-tiktok` (A, the largest, last); it runs one full validation after the three
merges (it covers every scope). Shared docs conflict only if a section rule was broken: keep
both sides. A code conflict means an ownership slip: stop and report the files.

**Two agents instead of three** (if the owner prefers): Agent B owns B's and C's files and does
P41 → P42 → P43 → P44; Agent A does P39 → P40. The prompts work unchanged: the owner pastes
[`B-downloads.md`](prompts/B-downloads.md) into Agent B's chat and, after P42,
[`C-ads.md`](prompts/C-ads.md) with `BRANCH_OVERRIDE: work/phase-15-downloads`; P44 then merges
B's branch (with C's work) and A's. The wall time stays about the same (about 15–22 h with
three agents, 16–24 h with two), because A's TikTok track is the long one.

## 1. Status board

Agents A, B and C record status in their own section of `docs/SESSION_STATE.md`; P44 copies it
here. AI agent time includes builds and CI waits on a 4 GiB sandbox.

**Agent A — `work/phase-15-tiktok`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P39 | [TikTok: read every answer TikTok gives, and say why when it fails](#p39--tiktok-read-every-answer-tiktok-gives-and-say-why-when-it-fails) | Medium | 5–7 h | — | DONE — OWNER CHECK (2026-10-09; merged by P44) |
| P40 | [TikTok from TikTok's own page, like YouTube](#p40--tiktok-from-tiktoks-own-page-like-youtube) | Hard | 8–12 h | P39 (same branch) | DONE — OWNER CHECK (2026-10-09; merged by P44) |

**Agent B — `work/phase-15-downloads`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P41 | [YouTube: progress and speed from the first seconds](#p41--youtube-progress-and-speed-from-the-first-seconds) | Medium | 4–6 h | — | DONE — OWNER CHECK (2026-10-09; merged by P44) |
| P42 | [Downloads: Delete file](#p42--downloads-delete-file) | Easy–Medium | 3–5 h | — | DONE — OWNER CHECK (2026-10-09; merged by P44) |

**Agent C — `work/phase-15-ads`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P43 | [Other sites: the page's video, never the ad](#p43--other-sites-the-pages-video-never-the-ad) | Medium | 5–7 h | — | DONE — OWNER CHECK (2026-10-09; merged by P44) |

**Integration — Agent B, `work/phase-15-integration`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P44 | [Merge B → C → A, full validation, Preview #7](#p44--merge-and-preview-7) | Medium | 2–3 h | P39–P43 READY FOR MERGE | DONE (2026-10-09) — Preview #7 sent |
| P45 | [Adult-site sheet: the tapped video only, its links asked like the browser](#p45--the-tapped-video-only-its-links-asked-like-the-browser) | Medium–Hard | 7–8 h | P44, owner's Preview #7 test | DONE — OWNER CHECK (2026-10-09) — Preview #8 |
| P46 | [TikTok: every quality in the browser, "private" public posts, Show check](#p46--tiktok-every-quality-in-the-browser-private-public-posts-show-check) | Medium–Hard | 8–10 h | P45, owner's TikTok test | DONE — OWNER CHECK (2026-10-10) — Preview #9 |
| P47 | [TikTok: every height TikTok's pages give, joined; "1280p" on Download as](#p47--tiktok-every-height-tiktoks-pages-give-joined-1280p-on-download-as) | Medium | 3–4 h | P46, owner's Preview #9 test | DONE — OWNER CHECK (2026-10-10) — Preview #10 |
| P48 | [X (Twitter) adapter: every MP4 quality of a post, with sound](#p48--x-twitter-adapter-every-mp4-quality-of-a-post-with-sound) | Medium | 4–5 h | P47, owner's request | DONE — OWNER CHECK (2026-10-10) — Preview #11 |
| P49 | [Instagram adapter: reels and video posts, signed in or not](#p49--instagram-adapter-reels-and-video-posts-signed-in-or-not) | Medium–Hard | 5–6 h | P47, owner's request | DONE — OWNER CHECK (2026-10-10) — Preview #11 |
| P50 | [HLS video with separate sound: merged, not "No sound"](#p50--hls-video-with-separate-sound-merged-not-no-sound) | Medium | 3–4 h | P47, owner's request | DONE — OWNER CHECK (2026-10-10) — Preview #11 |
| P8 | [Signed release 1.0.0-beta.4](#p8--signed-release-100-beta4) | Easy | 1–2 h | P44–P50, Preview #11, owner OK | TODO |

In parallel the wall time is about 15–22 h (A's TikTok track of 13–19 h, then P44); two agents
need about 16–24 h; one agent alone 27–40 h.

## 2. What the owner saw

Preview #6 (2026-10-09), owner's words in short:

1. **TikTok in YFT's browser (with a VPN), every video.** A banner says "TikTok changed its page
   format. Falling back to generic detection."; Download shows the same reason with Try again;
   Try again fails the same way and nothing can be downloaded (the generic rows the banner
   promises never appear).
2. **TikTok links on Home, many of them** (short `vt.tiktok.com` links and video pages). The same
   "changed its page format" message, then rows marked "Quality unknown" / "Size unknown"; some
   links work.
3. **A TikTok row on Home ("Download · 1.4 MB")** fails with "This quality is not available now";
   the Details say the list of qualities was not readable, without a step.
4. **"Bypass TikTok like YouTube"** (2026-10-09): ignore policy and rules, it only has to work;
   do not stop.
5. **YouTube live recording.** After Download it stays at "Downloading" with no % and no speed
   for a long time before the numbers move.
6. **Downloads: Delete.** He wants a Delete that removes the saved file itself, not only "Remove
   from list".
7. **Other sites (an adult site), sometimes — a minority of videos.** The sheet shows the
   pre-roll ad instead of the page's video: the page's real title, "0:30", "The first link is
   gone — using a fresh link", "Other videos on this page (3)", M4A ~512 KB, MP3 ~469 KB, 1080p
   MP4 9.4 MB. He wants ads skipped 100% of the time: the main sheet always shows the page's own
   video. (Planned after TikTok, as item 7.)

## 3. Owner decisions

Defaults below are what the agents do unless `OWNER ANSWERS` in the prompt says otherwise.

| ID | Question | Options | Default |
| --- | --- | --- | --- |
| G1 | Number of agents | 3 at once (A, B, C) or 2 (B also does C's task) | 3 |
| G2 | TikTok's view of YFT (P39) | The adapter asks TikTok as Chrome on the phone (the WebView's own agent) and as desktop Chrome, without the "YFT" word (`TT_AGENT=CHROME`); or today's headless agent for the desktop page (`HEADLESS`) | `CHROME` |
| G3 | TikTok cookies on Home (P40) | Home's TikTok lookups and the hidden TikTok page use the cookies YFT's own browser already has for `tiktok.com` (from the user's own visits; never logged or stored elsewhere) (`TT_HOME_COOKIES=ON`); or Home stays cookie-free and the hidden page keeps its own (`OFF`) | `ON` |
| G4 | TikTok's watermarked file (P39) | Hide TikTok's "Download" file (with the TikTok watermark) when a clean quality exists, and show it as "With TikTok watermark" only when it is the only file (`TT_WATERMARK=HIDE`); or always show it (`SHOW`) | `HIDE` |
| G5 | Hidden TikTok page (P40) | When reading the page fails, open the video's TikTok page in a hidden WebView (as YouTube's BotGuard token already does) and take TikTok's own data from it — at most 15 s, one at a time (`TT_HIDDEN_PAGE=ON`); or never (`OFF`: the browser tab's own data and the player's requests only) | `ON` |
| G6 | YouTube fast start (P41) | A small first piece (1 MiB), no waiting for a whole batch, progress inside each piece, 4 pieces at once per track (`FAST_START=ON`); or only progress inside each piece (`OFF`) | `ON` |
| G7 | Delete file (P42) | Ask "Delete this file?" first (`DELETE_CONFIRM=ON`); or delete at once (`OFF`; a deleted file cannot come back) | `ON` |
| G8 | Ad rule (P43) | A video counts as the page's own only when the page or an adapter names it, or its length matches the length the page states; a link of unknown length is measured first; when nothing passes, no ad is offered: "Reload page and try again" (`AD_RULE=STRICT`); or today's rule plus the length check (`LENIENT`) | `STRICT` |

## 4. Findings and root causes

Code read on `a9eea7b` and live checks in Plan Mode (2026-10-09, US data-centre network; only
statuses, hosts, sizes and markers recorded). Confidence in brackets; agents confirm each
finding before they change code.

- **R25 — the browser's TikTok failure is a dead end (item 1, high).**
  `BrowserViewModel.runSiteAdapters` (about line 705), on an adapter failure, calls
  `detectedMediaStore.showLookup(PageVideoLookup(failure = …))` ("P12: the sheet shows why, with
  Try again, instead of the page's files"). So the sheet shows only the failure, never the files
  the page's player loaded, while the banner text from
  `SiteAdapterCoordinator.messageFor` (about line 286) promises "Falling back to generic
  detection". Try again repeats the same adapter call.
- **R26 — "changed its page format" hides several different failures (items 1–2, high).** The
  text belongs to `SiteExtractionFailure.RESPONSE_CHANGED`. It comes from `TikTokPageParser` when
  the page has no `__UNIVERSAL_DATA_FOR_REHYDRATION__` video detail and no `SIGI_STATE` item and no
  status code, or the item has no id or no video; from `OkHttpExtractorClient` after more than 5
  redirects; and from the coordinator for **any exception** inside an adapter (`catch
  Exception`). `OkHttpExtractorClient.fetch` passes every header straight to OkHttp's
  `header(name, value)`, which throws `IllegalArgumentException` for a non-ASCII or control
  character (possible in a browser cookie or agent), and `request()` catches only `IOException`,
  so such a header also ends as "changed its page format". A TikTok failure carries no details
  beyond "adapter tiktok: RESPONSE_CHANGED", so the owner's screenshots cannot say which one.
- **R27 — TikTok answers differ by agent and by country (items 1–2, medium).** Live from the US
  (2026-10-09): a phone Chrome agent → HTTP 200, about 155 KB, `__UNIVERSAL_DATA_FOR_REHYDRATION__`
  with `webapp.reflow.video.detail` (only `playAddr`, host `v16-webapp-prime.us.tiktok.com`, no
  `bitrateInfo`, empty `downloadAddr`); today's headless agent
  (`Mozilla/5.0 (X11; Linux x86_64) YFT/1.0.0-beta.3`) and desktop Chrome → `webapp.video-detail`
  with 4 `bitrateInfo` qualities (`adapt_lowest_1080_1`, `normal_720_0`, `adapt_lower_720_1`,
  `adapt_540_1`; hosts `v16-…`, `v19-…` and `www.tiktok.com/aweme/v1/play/`). The JSON fits
  `BoundedJsonParser`'s limits (depth 15–16, 3.6k–6.8k nodes) and is strictly valid; the same
  with cookies from a first visit to the home page. So the adapter works from the US, while the
  owner's VPN country (his media host `v16-webapp-prime.tiktok.com`, an `alisg` path) gets
  another answer that Plan Mode cannot see. The fix must not depend on one page shape: read the
  data the way TikTok's own page script does (P40), try the other agent on every failure, and
  explain each failure in Details (P39).
- **R28 — the desktop retry runs only in one case (item 1, high).** `TikTokExtractor` asks for
  the desktop page only when the phone page was read but listed no qualities; a phone page that
  cannot be read returns the failure at once. The desktop agent is `HeadlessIdentity.USER_AGENT`
  (with the "YFT" word, `SiteAdapterModule`, about line 106). `TikTokPageParser.scriptJson`
  takes the first quoted occurrence of the script id in the HTML, which a page that names the id
  earlier (in a preload or another script) breaks.
- **R29 — TikTok's media files need the token of the same answer (items 2–3, high).** Live: a
  `v16-…` media address answers 206 only with the `tt_chain_token` cookie of the same page
  answer and a `www.tiktok.com` Referer (no Referer → 403 on `v16`; `v19` works without one);
  another answer's token → 403; no cookie → 403; the agent does not matter. A client that sends
  its token back gets the same value again, so the WebView's own token keeps its addresses
  working. The `www.tiktok.com/aweme/v1/play/?…` address works with **no cookie** (302 to
  `v16m-default.tiktokcdn-us.com`, then 206). TikTok answers `HEAD` with `Content-Length` and a
  `Range: bytes=0-0` with `Content-Range`; the files are plain MP4 (`ftyp`, `moov` first,
  `avc1` + `mp4a`). Home's generic rows come from `HeadlessPageFetcher` without cookies, so their
  file checks get 403 → "Size unknown"; the 1.5 MB row that worked was the cookie-free
  `aweme/v1/play` address.
- **R30 — a dead short link looks like a format change (item 2, high).** A `vt.tiktok.com` /
  `vm.tiktok.com` link of a removed or private video redirects to TikTok's home page `/`, which
  has no video detail → `RESPONSE_CHANGED`.
- **R31 — "not readable" without a step (item 3, high).** `DefaultVariantResolver.resolve`
  catches only `IOException` and `IllegalArgumentException`; any other exception escapes to
  `QuickDownloadViewModel.resolveSafely` (about line 928), where `QuickDownloadFailures.reasonOf`
  turns it into `MALFORMED_MANIFEST` without a step. The owner's "Download · 1.4 MB" row is
  TikTok's watermarked `downloadAddr` (label "Download"); a site video that fails this way shows
  "This quality is not available now". The exception's class is not known yet: P39 logs it.
- **R32 — YouTube's % waits for the first 10 MiB (item 5, high).** `DashTransferEngine` fetches
  YouTube's whole-file tracks in 10 MiB ranges (`WholeFileTrack.DEFAULT_MAX_REQUEST_BYTES`,
  `googlevideo` only), 3 at a time (`maxConcurrentChunks`), in batches (`chunked(3)` +
  `awaitAll`: the next batch starts only when the slowest range of the batch ends). Progress is
  reported only by `markCompleted` when a whole range is written (nothing inside a range), each
  range ends with `fd.sync`, and a track of unknown length first runs `probeLength` (a 1-byte GET,
  up to 3 attempts). Both tracks run at once in `AudioVideoMuxEngine` and the total % needs both
  tracks' sizes. So % and speed stay at 0 until the first 10 MiB range ends — long on a slow
  network or VPN.
- **R33 — Delete only removes the record (item 6, high).** `DownloadAction.DELETE` is labelled
  "Remove from list" (`DownloadLabels.kt`) and calls `DownloadQueue.deleteRecord` (the file
  stays). The record keeps `destinationUri` (the published content URI).
  `LibraryRepository.delete` already deletes YFT's own MediaStore item (`ContentResolver.delete`)
  or an app-private file; destinations are MediaStore `Download/YFT` (API 29+), a SAF tree, and
  the app-private provider. An item YFT does not own (after a reinstall) needs the system's
  delete request (`MediaStore.createDeleteRequest`, API 30+) or `RecoverableSecurityException`
  (API 29).
- **R34 — the fresh-link chain can pick the ad (item 7, high).** P37's chain in
  `QuickDownloadViewModel.nextAttempt` (about line 596) goes NEWEST (the page's newest address on
  the same path) → PLAYER (`FreshLinks.playerVideo`) → REREAD → NEXT (`MediaGroups.nextVideo`).
  `playerVideo` accepts a candidate whose length is unknown (`length == null ||
  video.durationMillis == null`), and a candidate counts as an ad only when all candidates are
  `PREVIEW` (marked by `VastAdTracker` only after a recognized VAST/VMAP request in its time
  window, whole files from another site) or `facts.isFarShorter`. When the first link is gone, a
  pre-roll whose length is unknown passes — the 0:30 sheet of item 7.

## 5. Tasks

Each task: Goal, Read first, Steps, Tests, Docs, Done when, Owner check, Result. Line numbers
are from `a9eea7b` and move; search for the names.

### P39 — TikTok: read every answer TikTok gives, and say why when it fails

Medium · 5–7 h · needs — · **Agent A** · prompt [`A-tiktok.md`](prompts/A-tiktok.md)

**Goal:** the TikTok adapter reads every page shape TikTok sends (phone or desktop page, either
data script, `SIGI_STATE`), tries the other agent before it gives up, never fails on a header,
offers only qualities whose files really answer (exact sizes, no watermark when a clean file
exists), and when it still fails the Details say exactly where (R25–R31). The browser never
dead-ends: when the adapter fails, the sheet offers the file TikTok's player is playing.

**Read first:** `extractor-sites/.../tiktok/{TikTokExtractor,TikTokPageParser,TikTokUrls}.kt`
and `src/test/resources/fixtures/tiktok/`; `extractor-api/.../SiteExtractor.kt` (failures) and
`json/BoundedJson.kt`; `app/.../detection/{OkHttpExtractorClient,SiteAdapterCoordinator,
SiteAdapterModule,HeadlessIdentity}.kt` (`messageFor` about line 253, the adapter's `catch
Exception`, `desktopUserAgent` about line 106); `app/.../feature/browser/BrowserViewModel.kt`
(`runSiteAdapters` about line 705); `app/.../feature/home/LinkInspector.kt`;
`app/.../feature/detectedmedia/DetectedMediaStore.kt` (`PageVideoLookup`);
`QuickDownloadViewModel.lookupState` (about line 440; the only function of that file you edit);
`core-media/.../resolver/DefaultVariantResolver.kt`; `QuickDownloadFailures.kt` (read only);
`core-browser/.../detection/FocusedVideoProbe.kt`; `core-model/.../media/BrowserRequestContext.kt`.

**Steps**
1. **Details first (R26).** Every TikTok lookup keeps Details lines, success or failure, one per
   request and step: `page: phone · HTTP 200 · 155 KB · landed on: video page` (or `home page`,
   `challenge page`, `login page`, `other page`), `data: universal webapp.video-detail` (or
   `webapp.reflow.video.detail`, `SIGI_STATE`, `none`) `· JSON: read` (or `error <Class> at
   char N`), `post id: matches` (or `other id`, `missing`), `qualities: 4 (1080p, 720p, 720p,
   540p) · play address: yes · download address: yes`, `file check: 1080p 206 · 540p 403`, and
   for any exception `error: <ExceptionClass>` (the class only; a message can hold an
   address). Hosts only, never addresses, query values, cookie names or values (pass them
   through `DiagnosticTextSanitizer`). They fill `SiteAdapterOutcome.Failed.details` (Home
   shows them today) and a new `PageVideoLookup.details` (default empty), which `lookupState`
   shows as the sheet's Details (`failureDetails`).
2. **Honest reasons (R26, R30).** A short link that lands on TikTok's home page, or any page
   without a post id → `PRIVATE_OR_UNAVAILABLE` with "This TikTok link does not open a video.
   It may be removed or private — open it in YFT's browser to check."; a challenge page (little
   HTML, no data script, a script or form for a check such as `verify`, `captcha`, `waf`,
   "Please wait") → `BOT_CHECK` (P40's pages pass TikTok's own script checks); a login wall →
   `LOGIN_REQUIRED` as today. `RESPONSE_CHANGED` stays only for a video page whose data has an
   unknown shape, and its text changes everywhere (`messageFor`) from "… changed its page
   format. Falling back to generic detection." to "<Site>'s page could not be read. Tap Details
   to see why, or Try again." An exception inside an adapter keeps its reason but adds
   `error: <Class>` and the step to the Details.
3. **Safe requests (R26).** `OkHttpExtractorClient` builds headers safely: a cookie pair or a
   header that OkHttp would reject (non-ASCII or control characters) is left out (Details
   `cookie pairs left out: 2`, never names or values) instead of throwing; anything else it
   throws becomes a failure with `error: <Class>`, not a crash. A **per-lookup cookie jar**: the
   `Set-Cookie` answers of each hop (redirects of `vt.`/`vm.tiktok.com` included) go with the
   next requests of the same lookup — the desktop retry included — like a browser; nothing is
   kept after the lookup. More than 5 redirects → `HTTP_STATUS` with `too many redirects`.
4. **Both agents, every time (R28, G2).** Phone page first, then the desktop page whenever the
   phone page fails for any reason that is not final (final: private, login, region, DRM) or
   lists fewer than 2 qualities; the answer with more working qualities wins. Agents
   (`TT_AGENT=CHROME`): the phone agent is the WebView's own (`BrowserRequestContext.userAgent`
   in the browser, `WebSettings.getDefaultUserAgent` on Home); the desktop agent is desktop
   Chrome with the WebView's Chrome version (`Mozilla/5.0 (Windows NT 10.0; Win64; x64)
   AppleWebKit/537.36 (KHTML, like Gecko) Chrome/<major>.0.0.0 Safari/537.36`), never with the
   "YFT" word. `HEADLESS` keeps today's `HeadlessIdentity.USER_AGENT` for the desktop page.
5. **Read every data shape (R27, R28).** Find a data script by its `id="…"` attribute inside a
   `<script>` tag (not the first quoted id in the HTML); take any `__DEFAULT_SCOPE__` key that
   holds `itemInfo.itemStruct` (today's two names and future ones); then `SIGI_STATE`
   (`ItemModule[<id>]`); when strict JSON fails, one lenient read (HTML entities such as
   `&quot;` decoded, text after the closing brace cut) before failing. A post id that differs
   from the link's → the next shape or the other agent, never another video's files. Raise
   `BoundedJsonParser`'s limits only if a real page needs it (2026-10-09 pages: depth 16, 6.8k
   nodes, 155 KB).
6. **Qualities that really download (R29, R31, G4).** Rows from `bitrateInfo` (every address in
   each `PlayAddr.UrlList`, every host), `playAddr`, and the cookie-free
   `www.tiktok.com/aweme/v1/play/` address when it appears. Check each quality with
   `Range: bytes=0-0`, the cookies of the answer that listed it and Referer
   `https://www.tiktok.com/` — at most 8 checks per lookup, 3 at a time, 5 s each: 206 → the
   exact size from `Content-Range`; 401/403/404/410 → the next address of the same quality; none
   answers → the quality is left out (Details `file check: 720p 403`). Labels from the gear's
   height (`adapt_lowest_1080_1`, `normal_720_0` or `PlayAddr.Height`): "1080p", "720p",
   "540p"; "H.265" beside the label for `h265`/`bytevc1` gears; same height → H.264 first, then
   the higher bitrate; a duplicate (same height, codec and size) once. Watermark
   (`TT_WATERMARK=HIDE`): TikTok's `downloadAddr` only when no other file answers, labelled
   "With TikTok watermark"; `SHOW` lists it last with that label.
7. **The resolver always names its step (R31).** First reproduce the owner's "Download · 1.4 MB"
   row: a TikTok-shaped site video (one direct MP4 file on a TikTok host, the answer's cookie)
   through `DefaultVariantResolver` in a JVM test, and name the exception it throws. Then
   `resolve` catches every exception except `CancellationException` and returns a failure with
   its step and `error: <Class>`; a TikTok file needs no manifest step (it is one MP4).
8. **The browser never dead-ends (R25).** When the TikTok adapter fails on a page whose player
   loaded TikTok media files, the sheet offers the on-screen video's file instead of a failure:
   `FocusedVideoProbe` also returns the on-screen `<video>`'s `currentSrc` when it is `https`
   (new field, default null); the page's TikTok media request with that address (else the
   newest TikTok media request) becomes a `PageMediaRole.MAIN` row with the request's cookies
   and Referer, and the banner says "TikTok's page could not be read — showing the file its
   player is playing." Only a page without such files shows the failure with Details and Try
   again. Other adapters keep P12's behaviour.

**Tests**
- JVM (`extractor-sites`): fixtures shaped like the 2026-10-09 live answers (phone
  `webapp.reflow.video.detail` with only `playAddr`; desktop `webapp.video-detail` with 4
  gears; addresses kept as host + path, query values `REDACTED`) → the right qualities and
  labels; the script id named earlier in the HTML (must fail on the old code); an unknown
  `__DEFAULT_SCOPE__` key with `itemStruct`; entity-encoded JSON (lenient read); an unreadable
  phone page → the desktop page is tried (must fail on the old code); a short link landing on
  `/` → `PRIVATE_OR_UNAVAILABLE` with the new text (must fail on the old code); a challenge
  page → `BOT_CHECK`; another post's id → not used; file checks (206 → exact size, 403 → next
  address, all 403 → quality left out, at most 8 checks); the watermark rules; no Details line
  contains `http`, `?`, `=` or a cookie value.
- JVM (`app`): a cookie with a non-ASCII character → the request goes out without that pair
  (must fail on the old code: `IllegalArgumentException` → "changed its page format"); the
  cookie jar carries a `Set-Cookie` across the short-link redirect (MockWebServer); an exception
  in an adapter → Details `error: <Class>`; `BrowserViewModel`: adapter failure on a page with a
  TikTok media request → a MAIN row, not a failure (must fail on the old code); `lookupState`
  shows `PageVideoLookup.details`; the resolver with an unexpected exception → a failure with
  its step (must fail on the old code).

**Live check** (sandbox, polite: one video page, one short link, one dead short link): run the
adapter against a public TikTok video (both agents) and report statuses, page kinds, data keys,
qualities with exact sizes and the file-check statuses (hosts only); a dead short link → the
new "does not open a video" text.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX; `SUPPORT_MATRIX.md` TikTok
row.

**Done when:** the tests pass, the live check lists at least 2 qualities with exact sizes, and a
forced failure shows its Details lines in the sheet.

**Owner check:** Agent A's preview APK (worth trying before the merge) or Preview #7: TikTok in
YFT's browser → Download on a video page and on For You → qualities with sizes → the file plays;
a `vt.tiktok.com` link on Home → qualities; anything that still fails → a screenshot of Details.

**Result:** DONE — OWNER CHECK (Agent A, 2026-10-09, code `49fa1b7`, docs `8b0bc93`; merged by
P44). YFT reads TikTok's phone page (`webapp.reflow.video.detail`) and desktop page
(`webapp.video-detail`), any `__DEFAULT_SCOPE__` key holding the post and entity-encoded data;
the desktop page (desktop Chrome agent, the WebView's Chrome version) is asked when the phone
page fails or lists fewer than 2 qualities. Each quality's file is checked with
`Range: bytes=0-0`, the answer's cookies and Referer `https://www.tiktok.com/` (exact sizes, a
refused address → the next one, none → left out, at most 8 checks); the watermarked file only
when nothing else opens. Every failure has Details (hosts only); a link landing on TikTok's home
page → "This TikTok link does not open a video …"; "changed its page format" is gone; in the
browser a failed lookup offers the file TikTok's player is playing. Requests: refused cookie
pairs and headers are left out, a per-lookup cookie jar follows short-link redirects, too many
redirects is an HTTP status, any other error names its class and step. The resolver never reads
answers on the caller's thread, ends an unexpected error at its step and asks with a range GET
when HEAD answers 5xx (live: 504). Live (US sandbox): 3 qualities with exact sizes, every file
check 206. Agent A scope 1254 JVM tests, 0 failures; CI of `49fa1b7` green (37865869227,
37865869239, 37865869254).

### P40 — TikTok from TikTok's own page, like YouTube

Hard · 8–12 h · needs P39 (same branch) · **Agent A** · prompt [`A-tiktok.md`](prompts/A-tiktok.md)

**Goal:** YFT takes the video's data from **TikTok's own page** — the tab the user is watching,
or a hidden TikTok page for Home's links — the way TikTok's player gets it, so a TikTok video
that plays in YFT's browser can always be downloaded, whatever TikTok answers YFT's own
requests (the owner's VPN country, check pages; R27, item 4, G3, G5). This is the same idea as
YouTube's proof-of-origin token, which YFT already makes by running YouTube's own script in a
hidden WebView (`WebViewBotGuardEngine`, ADR-006).

**Read first:** `app/.../detection/potoken/WebViewBotGuardEngine.kt` (hidden WebView: main
thread, timeouts, clean-up) and `detection/script/WebViewSolverEngine.kt`;
`core-browser/.../webview/SecureBrowserWebViewClient.kt` and `policy/SecureWebViewPolicy.kt`;
`core-browser/.../detection/FocusedVideoProbe.kt` (`evaluateJavascript` on the tab);
`app/.../feature/browser/BrowserScreen.kt` (the tab's WebView set-up, about line 1288) and
`BrowserViewModel.kt`; `app/.../feature/home/LinkInspector.kt`; `app/.../detection/
{SiteAdapterCoordinator,BrowserPageReader}.kt` (the tab's cookies); `extractor-api/.../
SiteExtractor.kt` (`SiteExtractionRequest`); P39's TikTok adapter.

**Steps**
1. **Page data in the request (contract, additive).** `SiteExtractionRequest.pageData:
   SitePageData? = null` in `extractor-api`: the JSON text of one post's data that a page
   already holds, at most 64 KB, and where it came from (`TAB_SCRIPT`, `TAB_API_ANSWER`,
   `HIDDEN_PAGE`). The TikTok adapter, given `pageData` whose `itemStruct` id matches the link's
   post id, builds its rows from it **without fetching the page** (Details `data: tab · API
   answer`), checks the files as in P39 step 6 with the tab's cookies for the media host and
   Referer `https://www.tiktok.com/`, and falls back to P39's page read only when that gives no
   working file.
2. **The tab's own data (browser).** A small script (an asset file, run with
   `evaluateJavascript` on the main thread when Download is tapped on a TikTok tab, at most 1 s)
   returns for the on-screen post id (P36's `FocusedVideoProbe`, else the address) a compact
   item — `id`, `desc`, `author.uniqueId`, `video` {`duration`, `width`, `height`, `cover`,
   `playAddr`, `downloadAddr`, `bitrateInfo` [{`GearName`, `Bitrate`, `CodecType`, `PlayAddr`
   {`UrlList`, `DataSize`, `Width`, `Height`}}]} — taken from (a) the page's
   `__UNIVERSAL_DATA_FOR_REHYDRATION__` script (`JSON.parse` of its text; any
   `__DEFAULT_SCOPE__` key), (b) `SIGI_STATE`, (c) step 3's store; null when none has that id.
3. **TikTok's own API answers (a document-start script).** With androidx.webkit
   `WebViewCompat.addDocumentStartJavaScript` for `https://www.tiktok.com` and
   `https://m.tiktok.com` (check `WebViewFeature.DOCUMENT_START_SCRIPT`; without it, inject in
   `onPageStarted` and note in the Result that early answers can be missed), a script wraps
   `fetch` and `XMLHttpRequest` for same-site `/api/` paths and, after TikTok's own code got its
   answer, reads a **clone** of any JSON that holds `itemList`, `itemStruct` or `aweme_list`
   and keeps up to 200 compact items (step 2's shape) by id in a page-local object. It never
   changes, delays or repeats TikTok's requests, swallows its own errors, sends nothing to the
   app or the network. The For You feed's later videos (loaded by
   `/api/recommend/item_list/`) are then found by id. Add `androidx.webkit` to
   `gradle/libs.versions.toml` and the app (and core-browser, if the code lives there) build
   files — a version whose AAR accepts compile SDK 35 (for example 1.12.1).
4. **Hidden TikTok page (G5, `TT_HIDDEN_PAGE=ON`).** `TikTokPageEngine` (new,
   `app/.../detection/tiktok/`, built like `WebViewBotGuardEngine`): an offscreen WebView on the
   main thread, never attached to the screen; desktop Chrome agent with the WebView's Chrome
   version (TikTok then sends every quality); JavaScript on; images blocked; video and audio
   requests answered empty by `shouldInterceptRequest`; the shared cookie store
   (`TT_HOME_COOKIES=ON`, G3), so a check cookie and the media token stay for the next lookups;
   step 3's script added. It loads the link (short links follow their redirects), polls step 2's
   script every 300 ms until the item for the post id appears or 15 s pass, then destroys the
   WebView. One at a time (a `Mutex`; a second lookup waits). A check that TikTok's own script
   passes by itself passes here; a check that needs a person (a puzzle) → `BOT_CHECK`: "TikTok
   wants a check. Open the video in YFT's browser, then tap Download." Never automated.
   `TT_HOME_COOKIES=OFF`: the engine clears TikTok's cookies it set when it finishes and Home's
   lookups stay cookie-free.
5. **Order (Details list every step and its result).** Browser: tab data → P39's page read
   with the tab's cookies → hidden page (only when the tab holds no item for this post) → the
   player's file (P39 step 8). Home: P39's page read → hidden page → today's generic scan. Try
   again runs the whole order again; a tab's data is read again on every tap.
6. **Downloads carry what the page used.** Rows from tab or hidden-page data carry the
   `tiktok.com` cookies from `CookieManager` (taken at the lookup, never logged) and Referer
   `https://www.tiktok.com/` in their `BrowserRequestContext`; at download time a 403 tries the
   same quality's other addresses (P39), then the sheet's Try again reads the page data again.
7. **Messages.** No "Falling back to generic detection" anywhere. The TikTok banner shows only
   when every step failed, with the next thing the user can do (Try again, open in YFT's
   browser, the check text of step 4).

**Tests**
- JVM: `pageData` with a matching item → rows without any page request (must fail on the old
  code: the adapter fetched the page); another id → page read; the compact item made by the
  script on the instrumented fixture (saved as a JVM fixture) parses to the same rows as the
  page read; the browser and Home orders with fakes; the hidden page's timeout → the next step;
  two hidden-page lookups at once → the second waits; `TT_HIDDEN_PAGE=OFF`,
  `TT_HOME_COOKIES=OFF`.
- Instrumented (CI emulator, `app/src/androidTest/.../tiktok/`, assets in
  `app/src/androidTest/assets/tiktok/`): a TikTok-like fixture page served by the test (its
  origin is allowed for the scripts only in the test, through a constructor parameter) with an
  SSR data script and a page script that fetches an `/api/recommend/item_list/` JSON: (a) the
  tab script returns the SSR item; (b) after the page's fetch, it returns an API item by id;
  (c) the page's own code still gets its answer unchanged; (d) `TikTokPageEngine` on the fixture
  returns the item within 15 s and destroys its WebView; (e) a page that never shows the item →
  timeout, the WebView destroyed.

**Live check** (sandbox Chromium through Playwright, desktop agent, polite): load one public
TikTok video page and `https://www.tiktok.com/foryou` (one scroll), run the same asset scripts,
report: item found, id matches, qualities, hosts, API answers seen (count). Nothing else.

**Docs:** your sections; `SUPPORT_MATRIX.md` TikTok row (the order of step 5).

**Done when:** the tests pass, the CI emulator tests are green, and the live check finds the
on-screen item on both pages.

**Owner check:** Preview #7 (or Agent A's preview): TikTok in YFT's browser, For You: scroll
through 5 videos, Download on each → the on-screen video's qualities every time; a video page →
qualities; a `vt.tiktok.com` link on Home → qualities within about 10 s; a screenshot of Details
for anything that fails.

**Result:** DONE — OWNER CHECK (Agent A, 2026-10-09, `ce04711`; merged by P44).
`SiteExtractionRequest.pageData` (additive): the TikTok adapter builds rows from one post's data
whose id matches, without asking for the page. Tab data: `core-browser/src/main/assets/tiktok/
page-data.js` — `item` reads the post from the tab (`__UNIVERSAL_DATA_FOR_REHYDRATION__`,
`SIGI_STATE`, then the store) within 1 s; `store` runs at document start (androidx.webkit
1.12.1 `addDocumentStartJavaScript`, else `onPageStarted`) and keeps up to 200 compact items
from TikTok's own `/api/` answers, never changing, delaying or repeating TikTok's requests.
Hidden page (`TT_HIDDEN_PAGE=ON`): `TikTokPageEngine`, an offscreen WebView (desktop Chrome
agent, images and media blocked, only `https` TikTok pages, at most 15 s, one at a time), only
after YFT's own request failed (never after the site's own answer); a check still shown after
3 s → `BOT_CHECK` "TikTok wants a check …" (never automated). Order: browser — tab data → page
read with the tab's cookies → the tab again → hidden page → the player's file; Home — page read
with the browser's TikTok cookies (G3) → hidden page → generic scan; Details list every step.
Live (US sandbox): a video page found from the page's script in 0.7 s (5 qualities); For You → 4
API answers kept, 32 posts, the first 5 found. Agent A scope 1313 JVM tests, 0 failures; CI of
`ce04711` green (37886371700, 37886371675, 37886371676).

### P41 — YouTube: progress and speed from the first seconds

Medium · 4–6 h · needs — · **Agent B** · prompt [`B-downloads.md`](prompts/B-downloads.md)

**Goal:** after Download, a YouTube video (a 1-hour live recording too) shows a moving % and
speed within about 3 seconds, in the Downloads screen and the notification, and the whole
download is not slower than today (R32, item 5, G6).

**Read first:** `core-download/.../DashTransferEngine.kt` (`Policy` about line 67, the batch
loop with `chunked(policy.maxConcurrentChunks)` + `awaitAll` about line 145, `wholeFileLayout`
and its fingerprint about line 234, `probeLength` about line 266, the tracker's
`markCompleted` about line 819, the checkpoint calls); `core-model/.../download/
DownloadModels.kt` (`WholeFileTrack`, `DEFAULT_MAX_REQUEST_BYTES`); `AudioVideoMuxEngine.kt`
(both tracks at once, the total %); `HlsTransferEngine.kt` (the same batch pattern; leave it as
it is unless the shared helper covers it at no risk); `app/.../download/` (the notification
reads `DownloadProgress`); `app/.../feature/downloads/` (the row's % and speed).

**Steps**
1. **Measure first.** One log line per download (and in a failure's Details): `start: plan
   0.0 s · length 0.4 s (probe | known | from first range) · first byte 0.9 s · first progress
   1.0 s` (times only, no addresses). A JVM test with a slow server prints today's numbers.
2. **Progress inside a range.** Report bytes as they are written, at most 4 times a second per
   download; a range that is retried takes back what it had counted, so the % never goes back
   and never passes 100%; a range counts as done in the checkpoint only after its sync, as
   today.
3. **No batch barrier.** A pool of N workers takes the next range as soon as one finishes
   (today the next 3 start only when the slowest of the last 3 ended). Same file layout, same
   retries and back-off, same checkpoint records.
4. **Fast first range (`FAST_START=ON`).** The first range of a whole-file track is 1 MiB, the
   others 10 MiB; YouTube's media hosts get 4 ranges at once per track (today 3). The new layout
   gets its own fingerprint; a download whose checkpoint was saved with today's layout resumes
   with today's layout (both fingerprints are computed; the stored one wins).
5. **No separate length probe.** When the length is known (`clen` in the address, the player's
   `contentLength`, the plan's `totalBytes`) no probe runs; when it is not, the first range's
   answer gives it (`Content-Range: bytes 0-1048575/<total>`) instead of today's 1-byte probe
   plus a second request.
6. **The total before both sizes are known.** While one track's size is still unknown, the row
   and the notification show bytes and speed ("12.3 MB · 1.2 MB/s") instead of a 0% that does
   not move; the % appears as soon as both sizes are known.
7. `FAST_START=OFF`: only steps 1, 2 and 6.

**Tests**
- JVM (`core-download`, MockWebServer with a throttled body, e.g. 64 KB/s): progress above 0
  within 1 s (must fail on the old code: nothing until a whole 10 MiB range ends); one slow
  range does not hold back the others (must fail on the old code); the first range is 1 MiB;
  no probe request when the length is known; the length taken from the first range's
  `Content-Range`; a checkpoint written by today's layout resumes without downloading its done
  ranges again; progress never goes back, ends at exactly 100%, at most 4 updates a second; a
  retried range is not counted twice.
- The existing engine and mux tests stay green (P27, P34, P35).

**Live check:** none in the sandbox (YouTube answers data-centre networks with a bot check); a
local throttled server and the CI emulator are the proof.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX (start times before and
after).

**Done when:** the tests pass and the timeline line shows the first progress within 1 s in the
throttled test.

**Owner check:** a 1-hour YouTube live recording at 720p → % and speed move within about 3 s of
Download, in the app and the notification; the file plays and seeks; a paused and resumed
download still completes.

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-09, `6257307`; merged by P44).
`DashTransferEngine` runs a pool of workers (the next range starts as soon as one ends), counts
bytes as they are written (at most 4 updates a second, never backwards or past the total) and,
with `FAST_START`, starts YouTube's whole-file tracks with a 1 MiB range, then 10 MiB ranges, 4
at once, taking the length from the first range's `Content-Range` or the plan (`clen` /
`contentLength`, trusted on `googlevideo.com` only) instead of a probe; a checkpoint saved with
the old layout resumes with it. Merged downloads show both tracks' bytes in flight; the log and
a failure's Details show the start times (`start: plan · length · first byte · first
progress`).

### P42 — Downloads: Delete file

Easy–Medium · 3–5 h · needs — (after P41 on B's branch) · **Agent B** · prompt
[`B-downloads.md`](prompts/B-downloads.md)

**Goal:** a finished download's menu has **Delete file**, which deletes the saved file itself
(from `Download/YFT`, the Library and the Files app) and removes the row; "Remove from list"
stays for keeping the file (R33, item 6, G7).

**Read first:** `app/.../feature/downloads/{DownloadsUiState,DownloadLabels,DownloadsScreen,
DownloadsViewModel}.kt` (`DownloadAction.DELETE` = "Remove from list", the menu's `CardAction`
list about line 509); `core-download/.../DownloadQueue.kt` (`deleteRecord` about line 483) and
`DownloadDestination.kt`; the record's `destinationUri`; `app/.../feature/library/
LibraryRepository.kt` (`delete` about line 46) and the Library screen.

**Steps**
1. **One deleter.** `DownloadedFileDeleter` (in `app/.../download/`) deletes a saved file by its
   record's destination: YFT's MediaStore item → `ContentResolver.delete`; a
   `SecurityException` (an item YFT no longer owns, for example after a reinstall) → on API 30+
   `MediaStore.createDeleteRequest` (the system asks the user; the screen launches its
   `IntentSender`), on API 29 `RecoverableSecurityException.userAction`; a SAF tree document →
   `DocumentsContract.deleteDocument`; an app-private file → `File.delete`; a file that is
   already gone counts as deleted. `LibraryRepository.delete` uses it too (one code path).
2. **Menu and dialog (`DELETE_CONFIRM=ON`).** COMPLETED rows get "Delete file" (testTag
   `download-menu-delete-file-<id>`) beside "Remove from list" (kept as it is). The dialog
   (`download-delete-file-dialog`): "Delete this file?" — "“<file name>” will be removed from
   Download/YFT and from this list. This can't be undone." — Delete
   (`download-delete-file-confirm`) / Cancel (`download-delete-file-cancel`). `OFF` deletes at
   once. After a delete: the record is removed (`deleteRecord`), the Library refreshes, a
   snackbar says "File deleted"; a failure says "Could not delete the file" and keeps the row.
3. Rows that are not finished keep their menus as they are; partial files are handled as today.

**Tests**
- JVM: each destination (fake resolver and documents), a missing file → deleted, a
  `SecurityException` → the system request (API 30) or the recoverable action (API 29), a
  failure → the row stays; the view model: confirm → file deleted and record removed (must fail
  on the old code: only the record), cancel → nothing; `DELETE_CONFIRM=OFF`.
- Instrumented (CI emulator API 34, `app/src/androidTest/.../delete/`): save a small file
  through the real `Download/YFT` MediaStore path, run Delete file → a MediaStore query finds
  nothing and the record is gone.

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX.

**Done when:** the tests pass and the CI emulator test is green.

**Owner check:** Downloads → ⋮ on a finished video → Delete file → Delete → the row is gone, the
Library no longer shows it, the Files app's `Download/YFT` no longer has it; "Remove from list"
still keeps the file.

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-09, `8ba7259`; merged by P44). A finished
download's menu has "Delete file" beside "Remove from list"; with `DELETE_CONFIRM=ON` "Delete
this file?" asks first (Delete / Cancel). `DownloadedFileDeleter` deletes by the record: YFT's
MediaStore item (Android 11+ `createDeleteRequest`, Android 10 the
`RecoverableSecurityException` action when needed), a SAF document or an app-private file; a
file already gone counts as deleted; then the record goes and the Library follows ("File
deleted" / "Could not delete the file"). `LibraryRepository.delete` uses the same deleter. Agent
B scope 1116 tests, 0 failures; CI of `8ba7259` green (37862940024, 37862940003,
37862940023).

### P43 — Other sites: the page's video, never the ad

Medium · 5–7 h · needs — · **Agent C** · prompt [`C-ads.md`](prompts/C-ads.md)

**Goal:** on sites without an adapter, the sheet's main video is **always the page's own
video** — never the pre-roll ad, also not after "The first link is gone — using a fresh link",
in the first choice, in every fresh-link step (NEWEST, PLAYER, REREAD, NEXT) and in "Other
videos on this page" (R34, item 7, G8).

**Read first:** `app/.../feature/quickdownload/QuickDownloadViewModel.kt` (`nextAttempt` about
line 596, `sameVideo`, `reread`, the first choice with `MediaGroups.pageVideos` about line 175,
the attempt notes and Details); `QuickDownloadFailures.kt`; `core-model/.../media/
{FreshLinks,MediaGroups,PageVideoFacts,MediaCandidate}.kt` (`playerVideo` about line 123 and
its `length == null || video.durationMillis == null`, `looksLikeAd`, `isFarShorter`,
`nextVideo`, `refreshed`); `core-browser/.../detection/{VastAdTracker,BrowserObservationMapper,
PageFactsReader,PlayerSetupScanner}.kt` (`PREVIEW` marks, `looksLikeAd` about line 51, the
page's stated length); `app/src/androidTest/.../browser/detection/{PrerollInstrumentedTest,
FreshLinkInstrumentedTest}.kt` and `assets/browser-detection/`.

**Steps**
1. **Reproduce item 7 first.** A JVM test (and an instrumented fixture page, step 7) shaped
   like the owner's page: the page states its length (for example 10:05 in its metadata or
   player setup); the player script names the main video (HLS and MP4 qualities) whose first
   link answers 410; before the main video the player fetched a 30 s MP4 pre-roll from an ad
   host after an ad request that `VastAdTracker` does not recognize today. Today's chain offers
   the 30 s file (must fail on the old code).
2. **One rule: is this the page's video?** A new type in `core-model/.../media/` (for example
   `PageVideoProof`), used by the first choice and by NEWEST, PLAYER, REREAD and NEXT:
   - **The page's video** when (a) the page's player setup or a `PageMediaRole.MAIN` mark names
     it (P28's page-script links, the same path family), or (b) its length matches the page's
     stated length (within 5 s or 5%), or (c) its length matches the video whose link failed.
   - **An ad** when it is marked `PREVIEW`, comes from an ad host (step 4), was fetched inside an
     ad break (from an ad request until the main video's first request), or is at most 60 s
     while the page states (or the named video has) a length more than twice as long.
   - Otherwise **not proven**.
3. **Unknown length is not proof (`AD_RULE=STRICT`).** Before a not-proven candidate becomes
   the main video, its length is measured (HLS: the playlist's total; MP4: `mvhd`'s duration
   from the first bytes when `moov` comes first — one ranged read); after the resolver read the
   chosen video, its real length is checked against the page's stated length again (a mismatch
   → treated as an ad, next step of the chain). When the page states a length, only a matching
   video is offered as the main one. When nothing passes: no ad is offered; the sheet goes on
   with REREAD and then shows "Reload page and try again" (P37). Proven ads are not listed in
   "Other videos on this page" either. A page with no ad sign at all (no ad request, no
   `PREVIEW` mark, no stated length that a candidate misses) keeps today's choice, so simple
   sites work as before. `LENIENT`: today's rule plus the measured length and the check after
   the resolver.
4. **Recognize more ads.** `VastAdTracker` and `BrowserObservationMapper` also recognize an ad
   request by its answer (content type `application/xml`/`text/xml` whose body starts with
   `<VAST` or `<VMAP`), by IMA hosts, and by paths such as `/vast`, `vast.xml`, `preroll`,
   `/ads/`; a list of common video-ad hosts in a new `AdHosts.kt` (for example ad networks
   often used by adult and tube sites: `trafficjunky`, `exoclick`, `juicyads`, `tsyndicate`,
   `magsrv`, `adtng`, `realsrv`, plus Google's IMA and DoubleClick hosts). An MP4 or HLS from
   such a host is always an ad.
5. **Details say why.** `chosen: named by the page's player` / `length 10:03 matches the page
   (10:05)` / `skipped: 0:30 ad (ad host)` / `skipped: length unknown` (no addresses).
6. The header's length always comes from the chosen video, never from an ad. Sites with
   adapters (`adapterSite`, P37) keep their own choice; P28's, P29's and P37's behaviour stays
   for pages without ads.
7. **Instrumented fixture** (`app/src/androidTest/.../browser/detection/`, assets in
   `app/src/androidTest/assets/ads/`): a fixture site served by the test with an IMA-like
   pre-roll (an ad request, then a 30 s MP4 from a second host named like an ad host) and a
   main video whose first link answers 410 → the sheet offers the main video with the page's
   length.

**Tests**
- JVM: the item-7 repro (must fail on the old code); each proof and ad rule; an unknown length
  measured, then the ad skipped; the check after the resolver (a 0:30 result on a 10:05 page →
  next step; must fail on the old code); "Reload page and try again" when nothing passes;
  proven ads not in "Other videos"; a page without ad signs keeps today's choice; `LENIENT`;
  P28's, P29's and P37's tests stay green.
- Instrumented: step 7's fixture (CI emulator).

**Live check:** adult sites block US data-centre addresses and show age notices (§0.2): use
fixtures only. Optionally one public non-adult page with an IMA pre-roll (hosts, lengths and
counts only).

**Docs:** your sections of SESSION_STATE, CHANGELOG and TEST_MATRIX.

**Done when:** the tests pass, the CI emulator test is green, and Javtiful-like pages (P28's and
P37's fixtures) behave as before.

**Owner check:** the adult site of item 7: Download on 10 different videos (some with a
pre-roll) → the sheet shows the page's own length every time, never 0:30; a screenshot of
Details for any miss.

**Result:** DONE — OWNER CHECK (Agent C, 2026-10-09, `85f0998`; merged by P44;
`AD_RULE=STRICT`). On a site without an adapter one rule (`PageVideoProof`, core-model) decides
at every step of the sheet (first choice, the page's newest link, the player's link, the page
read again, the next video) whether a file is the page's video: the player setup names it, or
its length (measured first when unknown) matches the page's or the failed video's. Ads
(`AdHosts`, `AdSign` from the mapper and `VastAdTracker`, short files on long pages) are
skipped: "That was an ad — showing the page's video"; nothing left → "Only an ad was found, not
the page's video." with Reload. Proven ads are not counted in the sheet's other videos. P39
hand-off: `VariantResolutionResult.Failure.error` and Details "Error: <Class>" (`175eca0`) and,
on the owner's override, the resolver's three catch blocks (`85f0998`). Agent C scope 1145
tests, 0 failures; CI of `85f0998` green (37877862671, 37877862760, 37877862659).

### P44 — Merge and Preview #7

Medium · 2–3 h · needs P39–P43 `READY FOR MERGE` · **Agent B (integrator)** · prompt
[`M-merge-preview7.md`](prompts/M-merge-preview7.md)

**Goal:** one branch with B, C and A, fully validated, and Preview #7 on the owner's phone.

**Steps**
1. Start only when the SESSION_STATE sections of A, B and C say `READY FOR MERGE` and their
   newest code commits have green CI (checkpoint validation, emulator smoke, Preview APK; later
   docs-only commits have no runs, §0.3). Otherwise list what is missing and stop.
2. On `work/phase-15-integration` (pull it first): `git merge --no-ff` B's branch, then C's,
   then A's. Doc conflicts: keep both sides. A code conflict: stop and report the files (the
   only shared Kotlin file is `QuickDownloadViewModel.kt`, where A changed only `lookupState`).
   Hand-offs left open: do them when they are small and say so, else list them.
3. Full validation (640 MiB metaspace, §0.3), `:app:assembleRelease`, line check.
4. Docs: copy each agent's status and Results into §1 and §5; `docs/HANDOFF.md`,
   `docs/PHASE_STATUS.md`, the SESSION_STATE Overview, CHANGELOG (fold the three agent sections
   into Added/Changed/Fixed), SUPPORT_MATRIX, TEST_MATRIX (a Phase 15 summary row).
5. Checkpoint; CI: checkpoint validation, emulator smoke and the Preview APK run →
   **Preview #7**: its link and the §6 list to the owner in Burmese. Then stop. `main` is
   fast-forwarded only with `MAIN=OK`; P8 only with the owner's OK.

**Result:** DONE (Agent B, 2026-10-09). `git merge --no-ff` B (`b42865c`, code `8ba7259`) →
C (`8c8182d`, code `85f0998`) → A (`1c4206e`, code `ce04711`) on `work/phase-15-integration`
(merges `2661062`, `f3f80ce`, `257af94`). B and C merged without conflicts (CHANGELOG,
SESSION_STATE and TEST_MATRIX by sections). A: `DefaultVariantResolver.kt` and its test
conflicted because C changed them on the owner's override (A's `8b0bc93` files plus the
`error` lines); C's side was kept — it equals A's file plus C's lines, and A's P40 did not touch
them. Hand-offs C → A left open (backlog, §7): (a) leaving proven ads out of the browser's and
Detected media's lists and `BrowserViewModel`'s other-videos count was tried and reverted — it
breaks `BrowserViewModelTest.aVideoPageWithPreviewsAndAnAdOpensItsStreamByItsLengthWithTheRest
CountedAsOthers` (P24, the owner's case counts an ad as another video), so it needs Agent A and
the owner's word; (b) `vastAds.onAnswer` from the browser's own reads of a page's answers. Line check: B's `DeleteFileScreenTest` had one 101-character
line (wrapped). Full validation: 1717 tests, 0 failures, 66 skipped (app 900, core-browser 149, core-data 33, core-download 184, core-media 33, core-model 122, extractor-api 36, extractor-generic 21, extractor-sites 239; 1532 at the start); lint 0 errors (98 warnings: A's 3 androidx.webkit notices); `:app:assembleRelease`: OK (lint vital needed a 2.5 GiB Gradle heap on the 4 GiB sandbox).
The owner asked to push `main` after the merge and to build the test-key Preview: `main` is
fast-forwarded to the validated merge after its CI was green (no tag, no signed release). CI of
`1ef8c86`: checkpoint validation 37893874474, emulator smoke 37893874480, Preview APK 37893874478
= **Preview #7**, all green.

### P45 — The tapped video only, its links asked like the browser

Medium–Hard · 7–8 h · needs P44 and the owner's Preview #7 test · **Agent B alone** (the owner
asked for no A/B/C split, 2026-10-09)

**What the owner saw (Preview #7, an adult site, 2026-10-09):** (1) the sheet still showed
"Other videos on this page (3)" with ads and previews in that list; he wants it gone and only the
source video, 100% stable. (2) "The site answered HTTP 474. Try again or pick another format."
with Details "First video · skipped: ad (ad host)", then three "Page read again · Step: file
check · Host: ev.<cdn> · Status: HTTP 474" (links under 1 min old, expiry not passed). (3) "The
site no longer has this video (HTTP 410)." with "First video · Step: list of qualities
(manifest) · Host: hm-h.<cdn> · Status: HTTP 410 · Link from: page script", two "Page read
again · no new link", then "Next video · Step: file check · HTTP 410" — another video. Plus
the C → A hand-offs (a) and (b) left open by P44.

**Root causes (code, the owner's Details, public reports; live checks of the site were not
allowed in the sandbox):**
- R35 — Links a page names in its markup or scripts ("Link from: page script", the DOM probe)
  were asked with no `User-Agent` at all — OkHttp's own `okhttp/4.12.0` went out —, the whole
  page address as `Referer` and no `Origin`, unlike the page's player in Chromium.
- R36 — The file check's HEAD failed at once on any 4xx except 405 (474 never got its range
  GET), and its refusals 412 and 452–499 counted as "Try again or pick another format".
- R37 — 410 said "The site no longer has this video" while the page played it. Public reports
  (yt-dlp #17642, Sep–Oct 2026; yt-dlp PR #16794, merged 2026-06-09) show this site's CDN
  answering 410 (and 412 without `Origin`/`Referer`) to requests that are not a browser's — by
  TLS fingerprint and IP reputation, not by the link's age; after one or two such requests a
  link stays refused for minutes. The page re-read (OkHttp) gets the same links, so "no new
  link".
- R38 — The fresh-link chain's "Next video" (P29) moved to another video of the page, and the
  sheet's "Other videos on this page (N)" row listed and counted the page's ads.
- R39 — Proven ads (`PageVideoProof.isProvenAd`: an ad network, an ad address, an ad break)
  were still listed under the browser's and Detected media's "Other videos" (hand-off (a)).

**Steps (done)**
1. The sheet is the tapped video's only: no "Other videos on this page" row, no
   `otherVideos`/found-list plumbing (`DetectedMediaStore`, `BrowserViewModel`, `HomeViewModel`,
   `YftNavHost`), no "Next video" step. Only a proven ad gives way, once, to the page's own video
   behind it ("Page's video", `MediaGroups.pageVideoAfterAd`); a video that failed is never
   followed by another one. The maybe-ad note says "This may be an ad. Play the video for a
   moment, then open Download again."
2. Requests like the browser's (`BrowserRequestContext`): what the browser itself sent stands
   (its `Referer` wins over the page address); DOM and page-script links get the tab's agent and
   `pageLink(...)`: a file on another origin gets `Origin: <page origin>` and
   `Referer: <page origin>/` (Chromium's `strict-origin-when-cross-origin`), never a cookie.
3. The file check: any non-2xx HEAD is followed by the range GET, whose answer decides.
4. Browser reads (`BrowserReads`, app `WebViewBrowserReads`): a refusal (403, 410, 412,
   452–499) of a file check, a manifest or a quality playlist of a page's link is asked once
   more by Android's WebView — one offscreen WebView, a blank page of the page's origin, the
   page's own `fetch` (CORS, no cache, cookies only for its own origin), the tab's agent; 10 s
   at most, one at a time, destroyed after 60 s idle; never a site adapter's file, never a 404.
   Its answer stands in when it got the text or the file's length. The HLS download engine asks
   a refused playlist the same way; its pieces are still downloaded by YFT.
5. Messages and Details: 410, 412 and 452–499 say "The site refused this link (HTTP n). Play the
   video for a moment, then try again."; 404 keeps "no longer has this video". Details add
   "Request: HEAD / range GET / GET" and "Browser check: HTTP n" (or "not answered"). Refusals
   count as dead links for the same video's fresh-link chain.
6. Hand-off (a): the browser's found list and Detected media leave proven ads out
   (`MediaGroups.ofPage(hideAds = true)`); the sheet no longer counts other videos at all.
7. Tests: sheet never shows another video (`aGoneFirstFileNeverShowsThePagesOtherVideo`), the
   sheet has no Other-videos row, refusal messages (410/412/474), Details lines, `pageLink`
   headers, observed Referer wins, HEAD 474 → range GET, a refused manifest read by the browser,
   a refusal the browser gets too, no browser read after a 404/for an adapter's file, the HLS
   engine's browser-read playlist, the read script's escaping and answers, the CDN hosts of the
   owner's Details are never ad hosts.

**Not done (backlog, §7):** hand-off (b) `vastAds.onAnswer` — WebView shows the browser no
answer bodies, so it needs a document-start hook of the page's XHR/fetch (a new page script in
every tab); and Chromium's network stack for the downloads themselves (Cronet), if the CDN also
refuses YFT's media requests.

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-09, `1d2f27a`). Full validation: 1729 tests, 0 failures, 66 skipped (app 903, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 239; 1717 before P45); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean. CI of
`1d2f27a`: checkpoint validation 37955002943, emulator smoke 37955002962, Preview APK 37955002856
= **Preview #8**, all green.

### P46 — TikTok: every quality in the browser, "private" public posts, Show check

Medium–Hard · 8–10 h · Agent B alone (`work/phase-15-integration`) · the owner's TikTok test of
Preview #8 (Pornhub: more than 10 videos all fine).

**Owner's report:** (1) a TikTok link pasted on Home lists 2–3 qualities (480p, 720p, 1080p),
the same video's Download in YFT's browser lists one; (2) some public posts say "This TikTok
post is private or no longer available" on Home (Details: `page: phone · HTTP 200 · landed on:
video page · data: universal webapp.reflow.video.detail · JSON: read · post id: missing`), and
in the browser "TikTok wants to check that this is not a bot…" or "TikTok wants a check…" —
TikTok's own phone page shows "This page isn't available — Use the app"; (3) Snaptube shows
the same TikTok page but downloads the post; (4) is a bot-check bypass like YouTube's needed?

**Why:** (1) the browser's lookup took the tab's data (TikTok's phone player gets one file) and
stopped once that file opened; Home reads the desktop page when the phone page lists fewer than
two. (2) TikTok's status for the post on the phone page ended the lookup (`final`), so neither
the desktop page nor the hidden page was asked; the browser's check message told the user to
open the browser they were in. (3) Snaptube does not read TikTok's web page. (4) No: YouTube
needs a per-video token; TikTok's wall here is "app only on phones", and its real check is a
slider a person answers.

**Done (owner chose A, then B):**

1. TikTok's status on a page is not final: the desktop page is asked too, Details name it
   ("… · TikTok status 10216"), a check on one page wins over the other page's status, and a
   "private" from the page data alone (no HTTP status, no adapter message) also asks the hidden
   page (`SiteAdapterCoordinator.isStatusOnly`).
2. The browser: a tab's data with one working file is joined by the desktop page's qualities
   ("desktop page: asked for more qualities (tab: 1 working)"); the answer with more working
   files wins, else the tab's row stays.
3. **Show check:** when TikTok's lookup ends on a check, the browser's notice says "TikTok wants
   to check that this is not a bot. Tap Show check, answer it, then tap Done: YFT looks again."
   with **Show check**; it opens the video's TikTok page (desktop agent, the browser's shared
   cookies, TikTok's https pages only, zoomable) for the user to answer; Done asks again. Home's
   message: "TikTok wants a check. Open the video in YFT's browser and tap Show check." YFT never
   answers a check itself.
4. **B — download service** (`TT_SERVICE=ON`): when TikTok's own pages give no file (not for a
   photo post, DRM or a link that lands on the home page), YFT asks a public download service
   (`tikwm.com`) for the post — only the post's address, a plain Chrome agent, never a cookie;
   its HD and normal files (and the watermarked one last) are checked like a page's files and
   their requests carry no TikTok cookie or Referer. Details: "download service: found ·
   qualities: n …" or why not ("answer code -1", "other post", "HTTP n").
5. **Not done — TikTok's app API** (what Snaptube-like apps use): it needs a registered device
   and TikTok's request signing; without them it answers nothing, and both change often. The
   download service covers the same posts.

**Tests:** `TikTokExtractorTest` (status asks the desktop page, private on the phone → desktop
answer, a check wins over a status), `TikTokPageDataTest` (one quality → desktop qualities, no
more → the tab's row, every quality → no desktop read), `TikTokDownloadServiceTest` (6: rows
without cookies, the service's own paths, nothing found, another post, off by default, never for
a photo post), `SiteAdapterOrderTest` (status-only private → hidden page; adapter words or HTTP
status → not), `SiteCheckTest` (when Show check is offered, TikTok's https pages only),
`BrowserScreenTest` (Show check instead of Try again).

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-10, `462d508`). Full validation: 1746 tests, 0 failures, 66 skipped (app 909, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 250; 1729 before P46); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean. CI of
`462d508`: checkpoint validation 37971914024, emulator smoke 37971914007, Preview APK 37971914134
= **Preview #9**, all green.

### P47 — TikTok: every height TikTok's pages give, joined; "1280p" on Download as

Medium · 3–4 h · Agent B alone (`work/phase-15-integration`) · the owner's Preview #9 test.

**Owner's report (Preview #9):** Home and the browser now list the same TikTok rows, but most
videos give one video quality (480p; 720p on about one in ten; 1080p on some); More formats holds
audio only. The owner wants a choice of quality, without TikTok's app API (fragile: signing and
device registration change with TikTok's app) and without sending more to `tikwm.com`.
Download as named a 720 × 1280 video "1280p · Full HD".

**Why:** the lookup took one answer: the phone page (or the tab's data) and the desktop page
were compared and the answer with more working files won, so the phone page's 540p and the
desktop page's 720p never stood side by side; a phone page with 540p in H.264 and H.265 counted
as two qualities, so the desktop page was not asked. Download as named a row after the file's
height, not its short side.

**Done:**

1. Answers are joined: a later answer adds its working files of a height (and codec) the
   earlier ones do not list; files already listed are not checked again. Details: "joined:
   desktop adds 720p", "answer: phone + desktop · 2 working qualities".
2. The desktop page is asked when the phone page or the tab's data gives one height, however
   many codecs of it.
3. Download as names a video after its short side like the Download sheet ("720p · HD").
4. Not done: the download service is not asked for more qualities (owner: not `tikwm.com`);
   making smaller files on the phone was not chosen (it saves no data).

**Tests:** `TikTokExtractorTest` (the phone page's height joins the desktop page's other
heights; two codecs of one height still ask the desktop page), `TikTokPageDataTest` (the tab's
540p stays and the desktop page adds 1080p and 720p; a tab's two codecs of one height ask the
desktop page), `PreviewLabelsTest` (720 × 1280 → "720p · HD").

**Result:** DONE — OWNER CHECK (Agent B, 2026-10-10, `0bfb437`). Full validation: 1750 tests, 0 failures, 66 skipped (app 910, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 253; 1746 before P47); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean. CI of
`0bfb437`: checkpoint validation 37993854819, emulator smoke 37993854829, Preview APK 37993854810
= **Preview #10**, all green. `main` fast-forwarded to P47 with the owner's approval
(2026-10-10: "Main ကို Push & Commit").

### P48 — X (Twitter) adapter: every MP4 quality of a post, with sound

Medium · 4–5 h · Agent B alone (`work/phase-15-integration`) · the owner's request after
Preview #10 (§7 backlog: "Instagram and X adapters").

**Why:** X links had generic detection only: X's page is built by script, so Home had no video
to read, and the browser offered only what X's player was streaming.

**Done:**

1. `XUrls`: `x.com`, `twitter.com` (`www.`, `mobile.`, `m.`) `/{user}/status/{id}`, `/statuses/`,
   `/i/status/{id}`, `/i/web/status/{id}` and `/video/N` (the post's N-th video). Canonical
   `https://x.com/{user}/status/{id}`.
2. `XExtractor` asks X's public embed answer (`cdn.syndication.twimg.com/tweet-result`, the
   address and token X's own embed uses; no cookie, no sign-in, no internal API) and lists
   every MP4 of the video (`video.twimg.com` only) highest first, with its picture size from
   the address (`/vid/avc1/1280x720/`), so each row has sound; a GIF as a silent MP4; the
   playlist only when no MP4 is given; a quoted post's video when the post has none.
3. Failures with their own message and Details: a removed, protected or age-limited post
   ("X shows this post only to signed-in viewers (it may be protected, age-restricted or
   deleted). Open it in YFT's browser, sign in to X and play the video.") and a photo-only post
   ("This post has photos only, no video."); then, like a changed answer, the generic scan.
   Details: "embed answer GET 200 (N characters)", "answer: video 1, 2 in the post · 3 MP4
   files (720/360/270)" (the files' heights).

**Tests:** `XUrlsTest` (3), `XSyndicationTest` (5), `XExtractorTest` (7) on committed fixtures
(`fixtures/x/`: video, mixed media, GIF, quoted, photos, tombstone, HLS only); the token is the
same as X's embed script for 20,008 post ids (checked once with Node, not committed).

### P49 — Instagram adapter: reels and video posts, signed in or not

Medium–Hard · 5–6 h · Agent B alone · same request.

**Why:** Instagram links had generic detection only: Home often got Instagram's sign-in page,
and the browser offered only what Instagram's player was loading.

**Done:**

1. `InstagramUrls`: `instagram.com` (`www.`, `m.`) `/p|reel|reels|tv/{code}` and
   `/{user}/p|reel/{code}`; `?img_index=N` keeps the carousel item; the media id from the code.
2. `InstagramExtractor` asks, in order: the app API (`/api/v1/media/{id}/info/`, only when the
   YFT browser is signed in to Instagram), the web GraphQL query, the post page, then the public
   embed page (no cookie). Each answer's video files are listed (progressive MP4 with picture
   size and expiry) and its DASH qualities as video + sound merged into one MP4, highest first.
3. Home sends the Instagram cookies of YFT's own browser (`InstagramHomeSession`, like TikTok's
   Home cookies); YFT never signs in and never sends them to another host.
4. Failures: a sign-in wall → "Instagram shows this post only to signed-in viewers. Log in to
   Instagram in YFT's browser, then try again." (signed in and still refused: "Instagram did
   not show this post to your account…"); photos only → "This post has photos only, no
   video."; expired link, rate limit and network with their own reason; a changed answer falls
   back to the generic scan. Details: the session, one line per source, "item 1 of 3: …".

**Tests:** `InstagramUrlsTest` (4), `InstagramExtractorTest` (8) on committed fixtures
(`fixtures/instagram/`: app API reel, GraphQL reel / null / login / photo / carousel, signed-in
page, captioned embed, embed without video); `InstagramHomeSessionTest` (cookies for Instagram
links only).

### P50 — HLS video with separate sound: merged, not "No sound"

Medium · 3–4 h · Agent B alone · same request (X's HLS and many players' masters).

**Why:** an HLS master that names its sound apart (`#EXT-X-MEDIA:TYPE=AUDIO,URI=…`) gave video
qualities without sound ("No sound") and the sound as an audio row; there was no way to get one
file with both.

**Done:**

1. The resolver gives such a quality its group's first audio rendition as its companion (only
   AVC video with AAC sound, both stated in the master's `CODECS`); the row reads like any
   merged one (no "No sound"), its size from the quality's bitrate. Other codecs stay as before.
2. The download fetches the quality's playlist and the audio playlist as HLS, segment by segment
   with the same checkpoints and resume as any HLS download (`PlaylistTrackTransferRunner`),
   then merges them into one MP4 (MediaExtractor reads MPEG-TS and fragmented MP4 pieces).
3. A playlist is never merged with a single file; the phone's codec check still refuses what it
   cannot write.

**Tests:** `DefaultVariantResolverTest` (a master's 720p and 1080p get the English rendition;
HEVC, no `CODECS`, E-AC-3 and a missing group stay silent), `DownloadPlanFactoryTest` (an HLS
quality with its rendition → two playlist tracks into one MP4; mixed / HEVC refused),
`PlaylistTrackTransferRunnerTest` (playlist tracks go to the HLS engine with their checkpoints,
others to DASH; codec check for playlist tracks).

**Result (P48–P50):** DONE — OWNER CHECK (Agent B, 2026-10-10, `fdb3769`).
Full validation: 1784 tests, 0 failures, 66 skipped (app 913, core-browser 150, core-data 33, core-download 188, core-media 38, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 280; 1750 before P48–P50); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; `:app:compileDebugAndroidTestKotlin` OK; Kotlin line check clean. CI of `fdb3769`: checkpoint validation 38009471238, emulator smoke 38009471040, Preview APK 38009471066 = **Preview #11**, all green. `main` fast-forwarded to P50 with the owner's approval
(2026-10-10: "Main ကို Push လိုက်").

### P8 — Signed release 1.0.0-beta.4

Easy · 1–2 h · needs P44, Preview #7 and the owner's OK · prompt
[`P8-signed-beta4.md`](prompts/P8-signed-beta4.md)

`yft.versionName=1.0.0-beta.4`, `yft.versionCode=4`, `docs/release/1.0.0-beta.4.md`, CHANGELOG
section; full validation; fast-forward `main` to `work/phase-15-integration`, tag
`v1.0.0-beta.4`; `release-draft.yml` builds the APK signed with the release key; record size,
SHA-256 and certificate. Merge, tag and signing need the owner's OK for this task
(`docs/RELEASE.md`).

**Result:** —

## 6. Owner phone checklist

Install `yft-preview-apk` from the Preview APK run the agent sends; uninstall the older YFT
Preview first (each run has a new test key).

**Preview #149 results and the v1.0.2 fix batch** (2026-10-31; branch `work/phase-v102-easy`;
the owner's Xiaomi, HyperOS, large system font):

- **#149** (`52a5a7ef`, run 41106177311): PASS 1, 2, 3, 4, 5, 5b, 6, 7, 10, 15, 25, 28, 50, 51.
  22 (a measurement): a 5:13 2160p WebM (904 MB) merged in 20–30 s; the owner: OK, no change.
  11: six qualities, but through V2 heal ("V2 heal client_id: learned from vkvideo-script" …
  "6 checked candidates via CONTRACT"); the "VK page data" step was never reached; no change for
  now. Not reached, no change needed: 13 (w3schools' plain video needs no lookup, so no Lookup
  details, as designed), 17a (the X post gave MP4 only), 18 (TikTok's BOT_CHECK before T1), 8
  and 9 (their conditions did not come up), 19 and 26 (they need the census APK). FAIL: 59 and
  70b (F1, F2). A YouTube M4A once showed "1 B" (a bad connection; G1 now reads such a size as
  unknown).
- **The fixes**, one commit each (CI: checkpoint / emulator smoke / Preview APK / minified):
  F1 `d65ba4f7` (#595 / #183 / #150 / #41), F2 `65079282` (#596 / #184 / #151 / #42), N1
  `04dfa890` (#597 / #185 / #152 / #43), N2 `12d07b85` (checkpoint #598 red only because
  Robolectric could not fetch its Android jar in `:core-browser`, `MavenArtifactFetcher`
  `IOException`, not the code; emulator #186 green; not re-run, the later heads' runs cover
  it), N3 `52858877` (#599), N4 `eee5aef9` (#600), N5 `feee3aae` (#601), N6 `b581de13` (#602),
  N7 `0b87d2cd` (#603), N8 no change (the owner's decision, §7), N9 `e09097a0` (#604 / #192 /
  #159 / #50), N10 `5682ea46`, N11 `15a1dddb`, N12 `a716faf9` and M1 `40cbce9c` (pushed with
  `[skip ci]`, so CI ran once on G1's head), G1 `361e4222` (#605 / #193 / #160; minified #51
  cancelled by G2's push), G2 `e29fcdbb` (#606 / #194 / #161 / #52). All green except N2's
  checkpoint; the other Preview, minified and emulator runs between were cancelled by the next
  push.
- **G2 on the emulator** (#194's "MP3 timing" notices; API 34, 128 kbps): a 4-minute source —
  after "MP3: 4:01 of sound, decode 3.8 s, encode 2.6 s" (6.4 s in all), before (the old loop,
  decode only) decode 4.3 s; a 1-hour source — after "MP3: 1:00:00 of sound, decode 1 min 26 s,
  encode 37 s" (124.8 s), before decode 84.9 s. The emulator decodes at the same speed either
  way (its decoder sets the time there, not the waits); the phone's Details line decides.
- **Checklist** (Preview #161 / its Trial; 26 with the census APK):
  1. F1 (59): `https://dash.akamaized.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd` → Video
     360p (480 × 360 · 24 fps · MP4) without "No sound", ~64 MB, and Audio M4A (72 kbps · en,
     ~5.6 MB); Download → one MP4 with sound (~44 MB); the M4A ~5.1 MB. `bbb_30fps.mpd` still
     lists up to 2160p.
  2. F2 (70b): the long-code reel `https://www.instagram.com/reel/C8xYzAbCdEfGhIjKlMnOpQrStUvWxYz01234/`
     → "This Instagram post is private or no longer available." with Try again.
  3. N1: Home's Your sites at 130 % and 150 % text: "YouTube", "Facebook", "Instagram" whole.
  4. N2: Updates' release notes: each heading clearly bigger and bolder than bold body text,
     with a little space above.
  5. N3: Downloads' select mode at 130 % and 150 %: the buttons whole, no "…".
  6. N4: the bottom bar's "Downloads" whole at 130 % and 150 % (a little smaller).
  7. N5: delete in Downloads and in Library: "This can't be undone.", "Deleted 1 file." /
     "Deleted N files.", the same red Delete button; Library's empty text names the chosen
     folder.
  8. N6: a TikTok 720 × 1280 video: the row "720p", the file name "… 720p".
  9. N7: `https://vkvideo.ru/video-212508059_456245329`: the sheet says "14:05", whole at 150 %.
  10. N8: no change: Instagram's 640 × 640 video stays "720p · HD".
  11. N9: `https://www.youtube.com/watch?v=Wq5sQRj1LvQ` (Zawgyi): the file name ends with a whole
      word and " 480p.mp4".
  12. N10: `https://www.w3schools.com/html/html5_video.asp`: the sheet ends on the MP4 ("144p ·
      320 × 176 · 25 fps · MP4") once it is read; an OGG you tap yourself stays.
  13. N11: on that page the collapsed sheet always shows the checked row.
  14. N12: Settings' folder line: "Saving to <folder>. Tap to change."
  15. M1: pick Download/YFT again → the Downloads chip "Download/YFT · N GB free"; pick
      Movies/Test → "Movies/Test · N GB free".
  16. G1: no row, Download button or download total shows a size too small for its length
      (such as "1 B" for a long M4A): it shows the "~" estimate or "Size unknown"; a clip of a
      few seconds keeps its size.
  17. G2: the 3:19 YouTube song as MP3 128 kbps: note how long "Converting" takes; the MP3's row →
      Details: "MP3: 3:19 of sound, decode … s, encode … s" (send the line).
  18. 26 (census APK, Android 14 or newer): on the census screen tick "AV1 merges (test,
      Android 14 or newer)"; a Facebook reel whose 1080p is AV1 only (or a YouTube video whose
      2K/4K is AV1 only), Try again → a row "… · AV1" (e.g. "1080p · Full HD · AV1"); download →
      one MP4 with sound that plays, its Details end "AV1 decoder: …"; untick → the AV1 row is
      gone.

**Preview #161 results and the R1–R5 fixes** (2026-10-31; branch `work/phase-v102-easy`; the
owner's Redmi Note 13 Pro, Android 16 (API 36), large system font):

- **#161** (`e29fcdbb`, run 41172470902): the owner's list R1–R5, one commit each. R1: on
  Updates the release notes' `###` drew as large as bold body text (63 vs 62 px). R2: a DASH
  row downloaded another track: `exMPD_BIP_TC1.mpd`'s 360p saved its sound alone ("Video 1 ·
  DASH · M4A · 5.1 MB"), `tears-of-steel-av1`'s 240p started `audio_eng=64008`. R3:
  `bbb_30fps.mpd` listed video rows up to 2160p, every one "No sound", and no Audio section. R4:
  at the large text size the browser's address pill drew its host and path over each other
  (`w3schools.com/html/html5_video.asp`). R5: MP3 still slow, "MP3: 3:18 of sound, decode 16 s,
  encode 7.2 s" (23 s in all; ~20 s on #149). Item 26 needs the census APK (below).
- **The fixes**, one commit each (CI: checkpoint / emulator smoke / Preview APK / minified):
  R1 `d8012334` (#607 / #195 / #162 / #53), R2 `e57d931c` (#608 / #196 / #163 / #54), R3
  `676202d5` (#609 / #197 / #164 / #55), R4 `3fe97504` (#610 / #198 / #165 / #56), R5
  `c3522f95` (#611 / #199 / #166 / #57). All green except two runs, neither the code: R1's
  minified #53 failed in "Build the shrunk previews with a fresh test key, and the check's test
  APK" (its log no longer readable; #54 to #57, with R1's code, passed); R4's emulator smoke
  #198 failed 54 s into the emulator step, before `scripts/ci-emulator-smoke.sh` made its first
  folder (the emulator never started; no diagnostics; #199, with R4's code, passed). Neither was
  re-run.
- **Causes and fixes**:
  - R1: `###` was titleSmall (15 sp) and body text bodyMedium (14 sp), and Android 14 and newer
    grow large text less than small text, so at the owner's size they drew 63 and 62 px. Now
    `#` headlineSmall (24 sp), `##` titleLarge (22 sp), `###` and below titleMedium (17 sp),
    all bold, the room above them as N2's (`ReleaseNotesView.headingStyle`).
  - R2: the sheet's Download looked its row up again by the file's address, and every quality
    of a DASH manifest has the manifest's address, so every row queued the manifest's first
    Representation (A's sound "1", B's `audio_eng=64008`). Now `DashRowFile` finds the row by
    its Representation's id, its kind (merged, video only, audio) and its sound's id; none →
    "This quality is not available now — choose another" and the Details line "Not in the
    manifest now: Representation 5 (video) with sound 1" (new); nothing else is queued.
  - R3: item 59 gave a manifest's sound only to one-file qualities (`SegmentBase`), and each of
    bbb_30fps's qualities is in pieces (`SegmentTemplate`), so every video stayed "No sound".
    Now `DashSoundPairing` pairs pieces (`MediaVariant.dashPieces`) as one-file tracks; a live
    manifest's and a site reader's rows stay as they were. The missing Audio section was not
    reproduced: Robolectric's and Android 16's own XML parser, the pasted link and the browser's
    request all give the M4A "67 kbps" (~5.1 MB) above the videos. Not a regression: v1.0.1 read
    no DASH manifest on a phone (F1's parser settings), and no build paired a manifest's pieces.
  - R4: the pill drew two texts in one place: the field's own text (the whole address, hidden
    only by a transparent ink) and over it the styled host and path. Not reproduced off the
    phone (Robolectric's native graphics at 100, 130, 150 and 200 % draw the styled address
    alone), so the cause is inferred. Now the field draws nothing while the styled address
    shows (its semantics stay: TalkBack reads and edits it as before), and the styled address
    is one `Text` (`AddressDisplayText`: an `AnnotatedString`, one line, no soft wrap, "…").
    `BrowserAddressLargeTextTest` fails at every size with the field's text drawn (6,923–7,837
    pixels); the pill's pixels before and after the change are the same.
  - R5 (the root cause): one thread did everything in turn. Each MediaCodec call of G2's loop
    (each dequeue, also one that finds nothing, each queue, each release) is a round trip to
    MediaCodec's own thread, and the decoder runs in another process (Codec2's
    `media.swcodec`): about five round trips for each of the song's 8,527 AAC frames, and
    while LAME encoded the decoder got no input, so the two never overlapped. On the phone's
    slow, low-clocked cores (LAME alone 3.4 times slower than on the emulator, 0.84 vs 0.25 ms
    a frame) a frame's round trips took ~1.9 ms: "decode 16 s" was mostly waiting. The
    emulator's cores took ~0.37 ms, so it did not show there. Now the decoder runs in
    MediaCodec's asynchronous mode on its own thread (`AsyncAacDecoder`, "yft-mp3-decode"),
    and LAME encodes on the conversion's thread from `PcmHandoff` (new: at most 32 buffers,
    ~0.7 s of sound); LAME and its settings unchanged. The Details line keeps its words; its
    "decode" is now the time LAME waited for sound. Expected on the phone: about LAME's time
    (~7 s or less) instead of the sum. `Mp3TranscoderDurationInstrumentedTest` (new): the MP3
    keeps its source's length (LAME gets exactly the decoded samples; the MP3 decodes to as
    many plus LAME's delay and padding, under 3 × 1,152).
- **R5 on the emulator** (#199's "MP3 timing" notices; API 34, 128 kbps; both loops on the same
  source in one run): a 4-minute source — R5 "MP3: 4:01 of sound, decode 995 ms, encode 4.1 s"
  (5.1 s in all), G2's one-thread loop decode 4.2 s, encode 2.5 s (6.7 s); a 1-hour source — R5
  "MP3: 1:00:00 of sound, decode 21 s, encode 1 min 4 s" (86.6 s), G2's loop stopped after 60 s
  at 30:51 of sound (#194: 124.8 s in all). LAME's own time grows there (2.5 → 4.1 s) because
  the emulator's two virtual cores now run the decoder and LAME at once; the phone has eight.
  The MP3 keeps its length: a 20-second mono source — LAME got all 983,040 decoded samples in
  order, the MP3 decodes to 984,960 (+1,920, 40 ms); a 60-second stereo one — 2,703,360 →
  2,704,896 a channel (+1,536, 34 ms). Instrumented tests: 81, 0 failures (6 skipped).
- **UI text**: R2's Details line "Not in the manifest now: Representation 5 (video) with sound
  1" (new; the failure uses the sheet's existing words). None for R1, R3, R4 and R5 (R5's
  Details line keeps its words).
- **The census APK** for item 26: `yft-vk-census-c3522f95.apk` (22,856,672 bytes, SHA-256
  `54b8ca5c…7a1d8025`): `:app:assembleDebug` of `c3522f95` with masterCapture, ownSolver,
  ownSolverNext, clientLadder, socialLadder and socialTrial on (ladderSkip and ladderMutate
  blank, routeBTrial off), signed with the shared test key (certificate SHA-256
  `a2696dff…bbbe`), so it installs over the old census APK; sent as a chat attachment.
- **Checklist** (Preview #166, run 41197965104, `yft-preview-apk`; 7 with the census APK):
  1. R1: Updates' release notes at the large text size: `#` larger than `##`, `##` larger than
     `###`, and `###` clearly larger than bold body text.
  2. R2 A: `https://dash.akamaized.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd` → 360p
     → Download → one MP4 with sound (~44 MB) that plays; its M4A → the sound alone (~5.1 MB).
  3. R2 B: `https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel-av1.ism/.mpd`
     → 240p ("No sound") → Download → a 640 × 284 video alone (`video_eng=208000`, not
     `audio_eng=64008`); its M4A → the 128 kbps sound (`audio_eng=128002`).
  4. R3: `https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd` → Audio M4A "67 kbps"
     (~5.1 MB) and 7 video rows, 2160p · 4K to 144p, none "No sound"; a video's Download → one
     MP4 with sound.
  5. R4: `https://www.w3schools.com/html/html5_video.asp` at the large and the largest text
     size: the address once, on one line, ending in "…" when too long; tap it → the whole
     address to edit.
  6. R5: the 3:18 YouTube song as MP3 128 kbps: "Converting" about 8 s or less; the MP3's row
     → Details "MP3: 3:18 of sound, decode … s, encode … s" (send the line).
  7. 26 (census APK, Android 14 or newer): on the census screen tick "AV1 merges (test,
     Android 14 or newer)"; the tears-of-steel-av1 link above → rows "… · AV1" without "No
     sound"; download → one MP4 with sound that plays, its Details end "AV1 decoder: …";
     untick → the rows are as in 3 again ("No sound", no "· AV1").

**Preview #166 results and the R6–R8 fixes** (2026-10-31; branch `work/phase-v102-easy`; the
owner's Redmi Note 13 Pro, Android 16 (API 36), large system font, a hardware AV1 decoder):

- **#166** (`c3522f95`, run 41197965104) and the census APK of `c3522f95`: R1–R5 passed. The
  owner's list R6–R8, one commit each. R6: a merged DASH video was cut at 7:11:
  `exMPD_BIP_TC1.mpd`'s 360p saved only 28 MB and played 7:11 of 10:54. R7: a video-only DASH
  file's length grew while it played (`tears-of-steel-av1`'s 240p "No sound"). R8 (item 26, the
  census APK with "AV1 merges (test, Android 14 or newer)" on): `tears-of-steel-av1`'s rows were
  right, none "No sound" ("720p · HD · AV1" 1920 × 856 ~267 MB, "480p · AV1" 1280 × 570 ~81 MB,
  "240p · AV1" 640 × 284 ~29 MB), but "240p · AV1" saved 18 MB, the video alone
  (`video_eng=208000`, ~18.2 MiB; the sound `audio_eng=128002`, ~11.2 MiB, was not written);
  YFT's player said "Video 240p can't be played here. Try Open with…", another player showed
  the picture ("HW AV1", the whole 12:14) and "This audio format (Unknown) is not supported".
- **The fixes**, one commit each (CI: checkpoint / emulator smoke / Preview APK / minified): R6
  `a11ef08b` (#612 / #200 / #167 / #58), R7 `a356c95c` (#613 / #201 / #168 / #59), R8 `01e84080`
  (#614 / #202 / #169 / #60) and R8's CI fix `8fec3139` (#615 / #203 / #170 / #61). All green except
  two emulator smokes, neither for a fix's app code:
  - #200 (R6): `DashPiecesInstrumentedTest` failed with "Cannot create DASH workspace", an older
    race in `prepareWorkspace`: when a merge's video and sound start at once, both see no folder,
    one makes it and the other's `mkdirs()` returns false. v1.0.1 had this race too; on a phone it
    can happen only while the folder does not exist yet (the first DASH download after install, or
    after clearing the app's data). R7 fixed it (`mkdirs()` first, then whether the folder is
    there); #201 passed.
  - #202 (R8): of 83 tests (6 skipped) only the AV1 case of R8's new
    `EditListedSoundMergeInstrumentedTest` failed: "INCOMPATIBLE_TRACKS, IllegalArgumentException at
    open; stream copy check failed, video unreadable". The test's file, not the app: P4's
    `mux/video-av1.mp4` has an empty `av1C` box (8 bytes, no payload), and Android reads no AV1
    track without one (Android 14's `MPEG4Extractor::getTrack` wants at least 4 bytes starting
    0x81), so `selectTrack` threw IllegalArgumentException in both ways. Real AV1 files carry it
    (`tears-of-steel-av1`'s pieces: 17 bytes, starting 0x81; the stream copy keeps it as it is). The
    AVC case passed ("r8-merge avc sdk=34 samples=45/45 inputStart=-46439us mergedStart=-46439us").
    The owner chose a second R8 commit: `8fec3139`, test only, gives the test an AV1 video with an
    `av1C` (Tests, below); the app is unchanged (its census APK is byte for byte `01e84080`'s). #203
    passed: 83 tests, 0 failures, 6 skipped.
- **Preview #168** (`a356c95c`, run 41218265624) on the owner's phone, all passed: R6 —
  `exMPD_BIP_TC1` 360p saved 44 MB, plays to 10:54, a seek to 8:10 works; `bbb_30fps` 144p
  saved 35 MB, 10:35. R7 — `tears-of-steel-av1` 240p "No sound" saved 18 MB, shows 12:14 at
  once and plays (seen at 10:25 of 12:14).
- **Causes and fixes**:
  - R6, two faults in a row: (1) Android 16 states the video's length as 10:52.652, but its
    samples run to 10:54.445; the old stream-copy check took this for a fault, so the merge fell
    back to the MediaMuxer way; (2) in the MediaMuxer way Android's reader wraps its time at
    7:09.5 (sample 10,298): the file came out short but was marked done. Now the stream-copy
    check has a new length rule (`streamCopyDurationMismatch`), and the MediaMuxer way's MP4 is
    read back for its length (`LengthCheckedAudioVideoMuxer`): a short one fails as "Failed ·
    Incompatible tracks", with Details such as "merged video ends at 7:09 of 10:54, 10298 of
    15691 samples; stream copy check failed, video duration". v1.0.1 had the same merge code
    but could not read this manifest (F1), so the problem never showed.
  - R7: the manifest's pieces were joined as they came. Each piece has its own `sidx` (4 s) and
    nothing states the whole length; Media3 takes each `sidx` for a new seek map, so the length
    grew while it played and a seek worked only in the part read so far. Now one `sidx` with
    the whole length goes before the first `moof` (`DashPieceIndex`), and the pieces' own `sidx`
    become `free` boxes; unusual files are joined as before. v1.0.1 had the same join. Checks: 4
    new tests, 2 of them fail on the old engine; all 2,254 tests and lint passed; with the real
    Tears of Steel pieces the old file had 184 seek maps, the first covering 4 s, and the new
    one shows 12:14 from the start, a seek to 10:00 landing right. A joined piece file is a
    little larger (+2,248 bytes at 240p).
  - R8 (the root cause), two in a row: (1) the stream copy took `avc1`, `avc3` and `vp09` video
    only, so an AV1 merge went the MediaMuxer way ("stream copy not supported, codec av01");
    (2) that way's reader (`SampleSource.load()`) took any sample time below 0 for the end of
    its track. From Android 11 Android's MP4 reader (MPEG4Extractor) applies a sound's edit list
    too, so a sound's first samples can come before 0 (AOSP: "cts can be negative … initial
    audio samples for gapless playback"); Android 7–10 shift video only, never below 0. This
    sound's edit list starts at media time 2,048 of 48 kHz (the AAC priming), so its first two
    samples read −42,667 and −21,333 µs: the sound ended at its first sample and the merge wrote
    an empty sound track. `c3522f95` counted that as done (the 18 MB file); with R6's read-back
    it fails as "Incompatible tracks" ("merged file has no audio"). v1.0.1 had the same reader
    and the same stream-copy codecs but no AV1 merges (no switch); a merge that went the
    MediaMuxer way with such a sound on Android 11 or newer (the stream copy refused or its
    check failed, or an HEVC video) was silent there too. Now an AV1 merge takes the **stream
    copy** (`av01` copied as VP9 is: its entry and `av1C` as they are), because it keeps every
    sample byte for byte and the sound's edit list as it came, as AVC merges do; it needs no
    Android AV1 MP4 writer (whether the API 34 emulator's MediaMuxer writes AV1 is open, §7); it is
    fast; and the same MediaExtractor read-back checks it. The MediaMuxer way stays the safety net,
    fixed: a track ends only when MediaExtractor has no sample left (time −1 and no track,
    `endsTrack`), so a sample before 0 is written as it comes, and Android 11 and newer's MediaMuxer
    starts that track at 0 and writes the edit list itself: every sample kept, in sync. The three
    AV1 rows share the codec and the sound, so the fix covers each. **Details** now show on a
    finished merged row that has the "AV1 decoder: …" line (only a debug build with the switch on:
    `showsDetails(av1Decoder)`); the release and both previews are unchanged (no switch; a quiet
    build hides it too).
- **R8 on the real files** (the demo server's 240p and sound pieces, 185 files each, joined as
  R7 joins them, merged by a throwaway JVM probe, not committed): the stream copy made
  31,098,138 bytes (29.7 MiB: the row's "30 MB", the sheet's "~29 MB"), 17,616 video and 34,406
  sound samples, each byte for byte and at its time as in the joined files (ffmpeg's frame
  checksums), the sound's edit list at 2,048, video 12:13.999 and sound 12:13.936 as presented;
  ffmpeg (dav1d) decodes the whole file without an error. The first three pieces of 480p
  (`video_eng=793000`) and 720p (`video_eng=2928000`) merge the same way. Tests: 2 new JVM
  tests, both fail on the old code (`StreamCopyAudioVideoMuxerTest`: an AV1 video and an AAC
  whose edit list starts at 2,048, every sample at its time, the first at −42,667 µs;
  `AudioVideoMuxEngineTest`: `endsTrack`); `DownloadLabelsTest` and `DownloadsScreenTest`
  (Details on a finished merge only while AV1 merges are on); on the CI emulator (API 34)
  `EditListedSoundMergeInstrumentedTest` (new): the test sound with an edit list at 2,048 merged
  with the AV1 test video (stream copy, every sample, Media3 prepares its sound) and with the
  AVC test video the MediaMuxer way (every sample, in time); since `8fec3139` the AV1 video is
  `mux/video-av1-with-av1c.mp4.b64` (Base64 text, as the VP9 big keyframe's): P4's file remuxed by
  FFmpeg with `-c copy`, its 15 samples byte for byte, with a 16-byte `av1C`. Local:
  `:core-download` (351) and `:app` (1,589) unit tests, 0 failures; lint.
- **UI text**: R6's Details line "merged video ends at 7:09 of 10:54, …" (new; the failure uses
  the existing "Incompatible tracks"). None for R7 and R8: R8's **Details** menu item and its
  "Download details" title already existed; they now show on a finished merged row in a debug
  build with the switch on. CHANGELOG `[1.0.2]`: one line under Fixed for each (the app does not
  show it).
- **The census APK** for R8: `yft-vk-census-8fec3139.apk` (22,873,056 bytes, SHA-256
  `b8fa890c…acedaea0`, byte for byte the census APK of `01e84080`, since `8fec3139` changed a test
  only): `:app:assembleDebug` of `8fec3139` with masterCapture, ownSolver, ownSolverNext,
  clientLadder, socialLadder and socialTrial on (ladderSkip and ladderMutate blank, routeBTrial
  off), signed with the shared test key (certificate SHA-256 `a2696dff…bbbe`), so it installs over
  the old census APK; sent as a chat attachment.
- **Checklist** (R8: the census APK, Android 14 or newer; 8 on Preview #170, run 41231991943,
  `yft-preview-apk`). YFT shows sizes in MiB and calls them "MB"; a census (debug) build is
  never quiet:
  1. Install the census APK over the old one. Open **YFT site census** and tick "AV1 merges
     (test, Android 14 or newer)".
  2. In YFT, `https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel-av1.ism/.mpd`
     → "720p · HD · AV1" (~267 MB), "480p · AV1" (~81 MB), "240p · AV1" (~29 MB), none "No
     sound".
  3. "240p · AV1" → Download → "Merging audio and video" → the finished row "240p · 30 MB" (a
     file manager: 31,098,138 bytes, the stream copy's file).
  4. Play it in YFT's player: sound from the start, 12:14 long; a seek to 10:00 keeps the sound
     with the picture.
  5. Tap the row → **Details** → "Download details": "Status: COMPLETED", "Download type: Merge
     (audio + video)", "Android: 16 (API 36)" and last "AV1 decoder: hardware (…)"; **Copy
     details** copies them.
  6. "480p · AV1" and "720p · HD · AV1": each one MP4 with sound (about 81 MB and 267 MB) that
     plays to 12:14.
  7. Untick the box; the link again → its video rows "No sound", without "· AV1"; the finished
     merged rows have no **Details** now.
  8. Preview #170: a finished merge (for example `bbb_30fps` 144p) still has no **Details**, also
     with Show details on (Settings → About → tap the version line 7 times; a reinstall turns it
     off again).

**The owner's items 1 and 2 after R8: R9 and R10** (2026-10-31; branch `work/phase-v102-easy`;
the owner's Redmi Note 13 Pro, Android 16 (API 36), large system font):

- **The items**, one commit each: item 1 (R9), §7's hand-off on `extractor-sites`
  `FacebookDashManifest.document` and `extractor-master` `InlineDashReader.read` (the owner's
  decision: fix it as F1 did, their XML parser settings only; lookup files); item 2 (R10), §7's
  "Refresh a download's link in the middle of the download" and VK_PLAN §8's "Re-extract once on
  a media 403" (the existing lookups called, no site lookup changed).
- **The fixes** (CI: checkpoint / emulator smoke / Preview APK / minified): R9 `f34de6c1` (#616 /
  #204 / #171 / #62), R10 `e58f9c81` (#617 / #205 / #172 / #63). All green; the emulator ran 87
  (#204) and 89 (#205) tests, 0 failures, 6 skipped. **Preview #172** (`e58f9c81`, run
  41243200016): `yft-preview-apk` and `yft-preview-trial-apk`.
- **Causes and fixes**:
  - R9: F1's cause, in the two inline DASH readers F1 left. Android's own XML parser
    (`org.apache.harmony.xml.parsers`) throws for `setXIncludeAware`, for every `setFeature` but
    namespaces and validation and for every `setAttribute`; both readers set them all in one
    `runCatching`, so on a phone every inline manifest read as nothing. The JVM's parser knows
    every setting, so every test passed. On the phone Facebook's page gave its HD and SD files and
    its manifest's address only (no file of the manifest, no sound); an Instagram reel its whole
    file only, without its manifest's merged sizes; Master's layers (Facebook's and Instagram's
    contracts), VK's page manifest (`VkPageDash`) and the census's row check (`SiteRowCheck`)
    found no file in one. **v1.0.1 had it**: both readers are unchanged since v1.0.0. Fix, as F1:
    each setting asked on its own (`ifKnown`: one the parser refuses is skipped), so every
    protection the parser has stays (on Android namespaces and no entity expansion, on the JVM
    all), and a manifest with a document type or an entity declaration (`<!DOCTYPE`, `<!ENTITY`,
    any case; Facebook's after its URL-decoding) is refused before any parser reads it.
  - R10: Track E (PR #13) already asks the site's own lookup for the same video's fresh link in
    the same quality (`SiteLinkRenewal`; a merge's fresh plan has both tracks' links) on a 401,
    403, 410 or an expired link, and on VK's 400 after the link gave bytes; the download then goes
    on from its checkpoint. Once per failure: a fresh link that gives no byte and fails stops as
    **Link expired**; at most 3 fresh links per download without a tap (`MAX_AUTOMATIC_RENEWALS`);
    Retry asks once more. Two gaps in `core-download`, fixed:
    (1) `DirectTransferEngine.reconcileCheckpoint` dropped the checkpoint of a file that states
    neither an ETag nor a Last-Modified, so such a download started again at 0 B on its fresh link
    ("started over (the file changed)") and on every run of the same link (Pause and Resume, a lost
    network, Retry). Such files: VK's (`okcdn.ru`, measured 2026-10-31: neither) and Facebook's HD
    file (`fbcdn.net`: no ETag; its SD and manifest files state one). Track E's phone test (#123,
    2026-10-29) showed it: Facebook (Direct, 43 MB) and VK 18+ (Direct, 22 MB) "Fresh link (403
    at …): started over (the file changed)". **v1.0.1 had it** (since phase 4; with no fresh link
    there, Pause and Resume or a lost network started such a file again at 0 B). Now such a file
    resumes when its length and segment layout are the saved ones, as a DASH whole file does; a
    validator on one side only, or another one, still starts over, and every range must state the
    same length.
    (2) `DownloadQueue.failTask` counted a link's bytes from its own run only. VK's srcIp case (the
    network goes and comes back, as on a Wi-Fi ↔ mobile data switch, and the same link answers 400
    at the run's first request) failed as **Failed · Unexpected HTTP status** (HTTP 400), and a
    fresh link that gave bytes before a lost network and then refused at once stopped as **Link
    expired** without a lookup. Now a link's bytes are the larger of its run's and of the
    checkpoint's gain since the link came (`linkStartBytes`, 0 again when a Retry starts over).
    v1.0.1 had no renewal at all (a 403 or 410 → **Link expired**, VK's 400 → **Failed**).
  - R10 and F8, both unchanged: a googlevideo whole file's range refused with 403 is asked again
    once after 3 s inside `DashTransferEngine` (within 5 s); only when that is refused too does the
    queue see it, and it names a 403 with time left on the link **YouTube refused** (B1), which
    `SiteLinkRenewal` renews only after the phone's network changed or on Retry, once per download
    (E-D5'). An expired YouTube link gets its fresh link as any site's.
- **Tests**: R9 — `InlineManifestOnAndroidTest` (extractor-sites) and
  `InlineDashReaderOnAndroidTest` (extractor-master), with a JVM parser that refuses what
  Android's refuses (F1's `AndroidLikeDocumentBuilderFactory`): Facebook's page (its 5 rows), an
  Instagram reel (its merged sizes and sound), Master's reader, VK's page manifest and the row
  check read as on the JVM; a document type, an external entity, a lower-case doctype and a
  URL-encoded one refused on both parsers; all 7 fail on `33ce8f21`. Emulator:
  `InlineManifestInstrumentedTest` (4, API 34's own parser). R10 — `FreshLinkResumeTest` (7, the
  real engines and a local host whose files state no validator): a 403 and a 410, then a fresh
  link (every byte; the fresh link asked from 128 KiB); a second 403 (**Link expired** after one
  lookup, no loop; Retry goes on from 128 KiB); VK's 400 after a lost network, direct and merged;
  a merge's track 403 (passed before too: Track E did it); a merge's fresh link refused after a
  lost network. 6 of 7 fail on the old code. Emulator: `FreshLinkResumeInstrumentedTest` (2: the
  queue and the direct engine on Android, a 403 and VK's 400 after a lost network). Local:
  `:extractor-sites` (372), `:extractor-master` (1,123), `:core-download` (358) and the app's
  download tests (335), 0 failures.
- **UI text**: none new or changed (CHANGELOG `[1.0.2]`: one line under Fixed for each). What
  shows changes: R9 — more rows (the manifest's sizes with sound, its sound alone under Audio);
  R10 — on a VK file or Facebook's HD file "Fresh link (403 at 41.0 MB): resumed at 41.0 MB" where
  it said "…: started over (the file changed)", Pause and Resume going on from the same per cent,
  and after a Wi-Fi ↔ mobile data switch "Getting a fresh link…" and on, where it said "Failed ·
  Unexpected HTTP status".
- **S5** (decision 26: per site 3 runs, each at least 6 h after the previous, the first and the
  last at least 24 h apart, on the final code's APK, wrong rows 0, no regression against B0): R9
  changed main's Facebook and Instagram lookups and Master's engine (the reader that Facebook's and
  Instagram's contracts and VK's pack use), so **Facebook, Instagram and VK start runs 1–3
  again**, on the census APK below; a later code change restarts them. R10 changes the download
  side only, no count (E-D7).
- **The census APK**: `yft-vk-census-e58f9c81.apk` (22,856,664 bytes, SHA-256
  `8f7d2e42…679380ba`): `:app:assembleDebug` of `e58f9c81` with masterCapture, ownSolver,
  ownSolverNext, clientLadder, socialLadder and socialTrial on (ladderSkip and ladderMutate
  blank, routeBTrial off), signed with the shared test key (certificate SHA-256
  `a2696dff…bbbe`), so it installs over the old census APK; sent as a chat attachment.
- **Checklist** (the census APK; Preview #172's `yft-preview-apk`, quiet, and
  `yft-preview-trial-apk`). YFT shows sizes in MiB and calls them "MB"; a census (debug) build is
  never quiet; in `yft-preview-apk` Details need Settings → About → tap the version line 7 times
  → Show details (a reinstall turns it off again). "Break this download's link" (Downloads ⋮, on
  a running or paused download) is in the census APK and the trial only. Keep YFT open in 6–11:
  1. Install the census APK over the old one; uninstall the older YFT Preview and YFT Trial, then
     install #172's two (each run has a new test key).
  2. R9, Facebook (the census APK and `yft-preview-apk`): a public video made in 1080p, pasted on
     Home → "1080p · Full HD" (the manifest's, merged with its sound) beside the HD and SD files,
     and an M4A under Audio; none "No sound". Download "1080p · Full HD" → "Merging audio and
     video" → one MP4 that plays with sound to its end. (A 1080p that is AV1 only shows in 4.)
  3. R9, Instagram: signed in to Instagram in YFT's browser, a reel pasted on Home → its file and
     the manifest's sizes, each with sound (such as "1080p · Full HD"), and an M4A (before R9: its
     file only); download a manifest size → one MP4 with sound.
  4. R9, the census APK, "AV1 merges (test, Android 14 or newer)" ticked: a Facebook reel whose
     1080p is AV1 only, Try again → "1080p · Full HD · AV1"; download → one MP4 with sound;
     untick → the row is gone.
  5. R9, VK: S5's VK links give their rows as before.
  6. R10 (the census APK or the trial): a long VK video's "720p · HD" (100 MB or more) → Download
     → **Pause** at about 30 % → **Resume** → it goes on from about 30 % (before R10: from 0 %).
  7. While it runs: Downloads ⋮ → "Break this download's link" → "Getting a fresh link…" → it
     goes on from the same per cent; ⋮ → **Details** → "Fresh link (403 at … MB): resumed at …
     MB" (on #123: "…: started over (the file changed)", from 0 %). The same with a Facebook
     video's HD file.
  8. No loop: break it again each time after it went on; breaks 2 and 3 get fresh links as in 7;
     break 4 → "Link expired" at once (no "Getting a fresh link…"); **Retry** → "Getting a fresh
     link…" → on from the same per cent; ⋮ → Details: three "Fresh link (403 at …): resumed at …"
     lines, then "Fresh link on Retry (403 at …): resumed at …".
  9. A real refusal (any of the three APKs; Settings → "Download over Wi-Fi only" off, as by
     default): on Wi-Fi start a long VK video; at about 30 % turn Wi-Fi off, mobile data on →
     maybe "Waiting for network" a moment, then "Getting a fresh link…" → it goes on from about
     30 % on mobile data; Details "Fresh link (400 at … MB): resumed at … MB" (or 403). Before
     R10: "Failed · Unexpected HTTP status", Details "HTTP status: 400". If VK's link still works
     on the new network, it simply goes on: a pass too. Then the same from mobile data to Wi-Fi.
  10. A merge: a Facebook manifest size (2) → while it downloads, "Break this download's link" →
      "Getting a fresh link…" → both tracks go on → one MP4 with sound.
  11. YouTube, as on #123: "Break this download's link" on a YouTube 1080p download (video and
      sound merged) → after a few seconds "YouTube refused" (F8 asked once more after 3 s; no
      fresh link without a network change) → **Retry** → "Getting a fresh link…" → on; Details
      "Fresh link on Retry (403 at …): resumed at …".
  12. Anything that fails: send its Details (**Copy details**).

**Preview #11 (after P48–P50)** — X, Instagram and HLS sound:

1. **An X post with a video**, pasted on Home: the sheet lists its MP4 qualities (e.g. 720p
   and lower), none "No sound"; download the highest to the end and play it with sound.
   Details: "embed answer GET 200 …" and "answer: video 1 …".
2. **An X post with two videos**: its link with `/video/2` at the end gives the second video.
3. The same X post in **YFT's browser**: Download lists the same rows.
4. **An Instagram reel**, on Home and in YFT's browser, before signing in: its rows, or "Instagram
   shows this post only to signed-in viewers…". Then sign in to Instagram in YFT's browser and
   paste it on Home again: rows up to the highest (e.g. 1080p, video + audio); download it and
   play it with sound.
5. **An Instagram post with several items** where a later item is a video (`?img_index=2`): that
   video's rows.
6. **A video whose site gives HLS with the sound apart** (e.g. an X post X gives as a playlist
   only, or a player site whose rows said "No sound" before): rows without "No sound";
   download one and play it with sound.
7. Anything that fails: send its **Details**.

**Preview #10 (after P47)** — TikTok (with and without the VPN):

1. **10 TikTok videos** on Home and in YFT's browser: where TikTok's pages give more than one
   height, the sheet lists each (e.g. 720p and 480p); Details show "joined: … adds …".
2. **`vt.tiktok.com/ZSbn2abY2/`** (720 × 1280): Download as says "720p · HD", not "1280p".
3. A video that still lists one quality: send its **Details** (the sheet's Details link, not
   Download as) — its "qualities:" and "file check" lines show what TikTok's pages gave.
4. Download one joined lower row (e.g. 480p) to the end and play it.

**Preview #9 (after P46)** — TikTok (with and without the VPN):

1. **The video that listed 480p only in the browser** (and 720p/480p on Home): Download in YFT's
   browser → the same qualities as Home; Details: "desktop page: asked for more qualities".
2. **The public posts that said "private"** (e.g. `vt.tiktok.com/ZSbtnhwM8/`) pasted on Home:
   qualities now (Details: "TikTok status …", then "download service: found …"), or the reason
   with those lines — send a screenshot of **Details**.
3. **The same posts in YFT's browser:** Download → qualities; if TikTok asks for a check, the
   notice has **Show check** → answer the slider → Done → YFT looks again.
4. **Download** one such video to the end and play it.
5. Preview #8's list below still holds (the adult site; owner: 10+ videos fine).

**Preview #8 (after P45)** — the adult site of Preview #7's item 6 (owner, 2026-10-10: more
than 10 Pornhub videos, all fine):

1. **Download on 10 videos** (some with a pre-roll): the sheet shows the tapped video only —
   no "Other videos on this page" row, never another video's title or length; after an ad the
   Details say "skipped: ad (…)" then "Page's video".
2. **The videos that said HTTP 474 or 410:** qualities now, or "The site refused this link
   (HTTP n). Play the video for a moment, then try again." → play a few seconds → Try again.
   Anything that still fails: a screenshot of **Details** — the new "Request:" and
   "Browser check:" lines say whether the browser itself was refused too.
3. **Download a quality** of such a video to the end; if the download fails, a screenshot of
   the download's **Details** (it tells whether the CDN refuses YFT's own file requests too).
4. **Browser's Download list** on a page with ads: ads no longer appear under "Other videos on
   this page".
5. Preview #7's list below still holds (TikTok, YouTube's start, Delete file).

**Preview #7 (after P44)**

1. **TikTok in YFT's browser, with the VPN (P39, P40):** tiktok.com/foryou → let a video play →
   Download → the on-screen video's qualities with sizes (1080p/720p/540p) → the file plays;
   scroll through 5 videos and Download on each → each time the video on screen; a video page
   from a profile → qualities.
2. **TikTok on Home (P39, P40):** paste a `vt.tiktok.com` link and a video page link → qualities
   within about 10 s (no "Quality unknown" rows, no "changed its page format"); a removed
   video's link → "This TikTok link does not open a video …".
3. **TikTok Details:** anything that still fails → a screenshot of the sheet's **Details** (it
   now says which page, data and file check failed).
4. **YouTube start (P41):** a 1-hour live recording at 720p → the % and the speed move within
   about 3 s of Download, in the app and the notification; the file plays and seeks.
5. **Delete file (P42):** Downloads → ⋮ on a finished video → Delete file → Delete → gone from
   Downloads, the Library and the Files app (`Download/YFT`); "Remove from list" still keeps
   the file.
6. **Ads (P43):** the adult site of item 7 → Download on 10 different videos (some with a
   pre-roll) → the page's own length every time, never 0:30.
7. **Preview #6 still works:** background downloads and merges with % and speed in the
   notification, the fast merge, fresh links instead of HTTP 410, YouTube 1080p with sound, a
   Facebook reel, Google search, History, pop-up blocking.
8. About › Last crash report: none.

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
  the page the browser already loaded → parser + fixtures. About 6–10 h. (P40's tab-data script
  is the same pattern and can be reused.) Owner decision: —
- **B4 — TikTok on the owner's phone.** Settled on 2026-10-08: the owner tests TikTok with a VPN.
  Since 2026-10-09 (P39, P40) TikTok's own page is the main source and every failure explains
  itself in Details, because the sandbox (US) sees other answers than the owner's VPN country.
- **B5 — Site fixes without a new APK** (owner's idea, 2026-10-08). Advice: later, and only for
  data, not code: a small signed rules file on GitHub (site patterns, ad hosts — P43's
  `AdHosts.kt` would move there —, player-script names) that YFT downloads and checks with a
  key built into the app, so a site's changed page can be followed without a new APK; changes
  in Kotlin code still need an update. About 1–2 days with tests. Owner decision: —

Other items:

- TikTok photo posts (slideshows with music): pictures and the sound as separate files.
- TikTok without a watermark for the "Download" file when TikTok offers only that one.
- Long downloads on Android 14+: a user-initiated data transfer job (`JobScheduler`) instead of
  the `dataSync` foreground service (no 6-hour limit), if P34's `onTimeout` notice appears.
- Refresh a download's link in the middle of the download (a 403/410 after an hour) by reading
  its page again, like P37 does for the sheet — done in v1.0.2: Track E (PR #13) and R10
  (`e58f9c81`, §6).
- A Matroska (WebM) stream copy for 2K/4K merges, like P35's MP4 path.
- HLS downloads: P41's rolling pool and progress inside a segment for long HLS videos.
- Open the Downloads tab from the notification (needs `MainActivity` and navigation) — done in
  v1.0.2 (`68105531`).
- Share target: open links shared from other apps (Android share sheet) in Home's lookup, so
  they open the download sheet like a pasted link — done in v1.0.2 (`1f861096`).
- Delete several finished downloads at once (select mode) — done in v1.0.2 (`a22d8442`), and
  delete a file from the Library's own menu with the same dialog.
- AV1 merges: off since P4 (the API 34 emulator's muxer failed), so Facebook's AV1-only sizes and
  YouTube's AV1-only 2K/4K stay hidden until an AV1 merge is proven on a phone — test switch in
  v1.0.2 (`26ccd4c0`); the release unchanged.
- YouTube: use the page player's own proof-of-origin token from the browser (ADR-006, not done);
  SABR streaming is not planned.
- Android 9 and older save through the legacy public folder; P20's instrumented test covers the
  API 34 MediaStore path only.
- Instagram and X adapters: done by P48 and P49. Not done: X's internal API for protected,
  age-limited and sensitive posts (they need the user's X sign-in); Instagram stories and
  highlights; the preview of an HLS quality with sound apart plays without sound (P50) — done in
  v1.0.2 (`c0923b46`).
- Background playback in the Library; saving to a folder chosen with the system picker — done in
  v1.0.2 (`9d1dcb3c`: the folder only).
- One shared ad list for media detection and pop-ups/redirects (P43's `AdHosts.kt` and P32's
  list), updated from one place.
- A private tab (no history, no cookies kept) and a per-site "allow pop-ups" list.
- Adapters for named adult sites (Phase 13 F1 option C) only if the owner chooses it.
- P43 hand-off (b), left open by P45: call `vastAds.onAnswer` with the page's own answers, so a
  VAST/VMAP body starts an ad break when the address says nothing. WebView shows the browser no
  answer bodies (`shouldInterceptRequest` sees requests only) and `MediaMetadataProbe` probes
  media-like addresses only, so it needs a document-start script that wraps the page's
  XHR/fetch and reports VAST/VMAP answers through an origin-restricted `WebMessageListener`.
  About 4–6 h; owner decision: — ((a) was done by P45.)
- **Option — Chromium's network stack for downloads (Cronet).** If the CDN of P45's site
  also refuses YFT's own file and piece requests (TLS fingerprint, R37), downloads would go
  through Cronet (`play-services-cronet` or the embedded Cronet library, the same stack as
  Chrome) instead of OkHttp. A new dependency (APK size, Play services), so it needs the
  owner's OK. About 1–2 days with tests. Owner decision (2026-10-10): not now — Preview #8 had
  no refused download; reopen with a download's Details that show a refusal.
- **TikTok's app API** (P46 item 5): needs a registered device and TikTok's request signing;
  not done while the download service gives the same posts. Agent's advice (2026-10-10, P47):
  do not build it — the signing and device registration change with TikTok's app, so it breaks
  often and each break needs a YFT update.

- **v1.0.2 batch after Preview #149** (2026-10-31): the owner's decisions and the agent's
  hand-offs.
  - Item 22 (owner): a 5:13 2160p WebM (904 MB) merged in 20–30 s on the phone: OK, no change.
  - N8 (owner): no change. A 640 × 640 video keeps "720p · HD" under the shared nearest-height
    rule (the same rule gives a 1920 × 960 file "1080p", item 9); "640p" would need another
    rule for one site.
  - Item 11: the six qualities came through V2 heal ("V2 heal client_id: learned from
    vkvideo-script" … "6 checked candidates via CONTRACT"); the "VK page data" step was never
    reached on the phone. No change for now; look again if V2 heal stops answering.
  - Hand-off (lookup files, not changed): `extractor-sites` `FacebookDashManifest.document` and
    `extractor-master` `InlineDashReader.read` set the same XML parser settings in one
    `runCatching` as F1's reader did, so on a phone Facebook's and Master's inline DASH
    manifests go unread the same way. Owner decision (2026-10-31): fix it — R9 (`f34de6c1`, §6).
  - Hand-off: the preview (Media3) plays a manifest as it is, so an http `BaseURL` (F1's MPD)
    is refused by Android's cleartext rule; the download works (F1). Owner decision: —
  - G2: the emulator decodes at the same speed with the new loop; if the phone's Details line
    still shows a long decode, the decoder itself is next. Owner decision: —

- **After Preview #161, R1–R5** (2026-10-31): the agent's hand-offs.
  - R4: the overlap was not reproduced off the phone (Robolectric's native graphics at 100, 130,
    150 and 200 % drew the styled address alone, the same pixels before and after the change);
    the cause is inferred from the code. If the phone still shows two addresses, a screenshot at
    its text size is next. Owner decision: —
  - R5: if the phone's Details line still shows a long "decode" (LAME waiting for sound), the
    decoder's round trips are next: Android 15's large audio frames (several AAC frames in one
    buffer, fewer calls a second) or an AAC decoder inside the app (a new library and its
    licence). Owner decision: —
  - CI: R1's minified #53 and R4's emulator smoke #198 failed outside the code and were not
    re-run; the later heads' runs, with their code, passed (§6).

- **After Preview #166, R6–R8** (2026-10-31): the agent's hand-offs.
  - R8: the AV1 merge is a stream copy, checked by Android's MediaExtractor as AVC merges are;
    if that check fails on a phone, the fixed MediaMuxer way makes the file (it wrote AV1 on the
    owner's phone in `c3522f95`, only without the sound). A finished row's Details do not say
    which way merged; the stream copy's 240p file is 31,098,138 bytes. Owner decision: —
  - R8: AV1 merges stay a debug build's test switch, off by default; turning them on for the
    release (`AV1_MP4_ENABLED`) is its own later commit after the owner's phone run. Owner
    decision: —
  - R8: the MediaMuxer way's fix also keeps the sound of a non-AV1 merge that falls back to it
    with an edit-listed AAC on Android 11 or newer (an HEVC video, a refused stream copy); no
    such case was tried on a phone. The live join's checks read with the stream copy's codec
    list, so they now take AV1 pieces too. Owner decision: —
  - R8: P4's probe (`av1MergesStayOffWhileThisPhoneCannotMakeThem`) merges P4's `mux/video-av1.mp4`,
    the file with the empty `av1C`. Its "result=Failure step=samples" (#203 too) means MediaMuxer
    took the AV1 track (`addTrack`, `start`) and the merge then failed on that file, which Android
    cannot read (`selectTrack`), so it does not show that the API 34 emulator's MediaMuxer writes no
    AV1 (the owner's phone wrote AV1 with it in `c3522f95`); `AV1_MP4_ENABLED`'s KDoc (P4: emulator
    smoke #27 and #28) rests on the same file. Giving the probe `8fec3139`'s file would tell, a test
    change of its own. Owner decision: —
  - CI: R6's emulator smoke #200 failed for the older `prepareWorkspace` race, fixed in R7; R8's
    #202 for its test's AV1 file, fixed in `8fec3139`; the later runs passed (§6).

- **After R9–R10** (2026-10-31): the agent's hand-offs.
  - R10: a VK fresh link that answers 400 at its own first request (it gave no byte) ends as
    **Failed · Unexpected HTTP status**, not **Link expired**: VK's 400 counts as a dead link only
    after that link gave bytes (E-D1), and Retry runs the same link again. Not seen on a phone (it
    needs a fresh link refused at once); opening the video again gives a new link. Owner
    decision: —
  - R9: decision 26 restarts a site's count on a change to its pack, the engine or main. R9's
    reader is used by Facebook's and Instagram's contracts and VK's pack (§6's S5); X's and
    TikTok's contracts name no inline manifest (`ContractRecipes`), so §6 restarts only those
    three counts. Owner decision: —
  - R9's CHANGELOG line names "Facebook's 1080p AV1" as an example; AV1 merges stay off in the
    release (`AV1_MP4_ENABLED`, R6–R8's hand-off), so the release offers Facebook's AVC sizes and
    the sound, and AV1 sizes only once AV1 merges are on. Owner decision: —

## 8. Done before Phase 15

**Phase 14** (P34–P38, 2026-10-08) is merged into `main` (`4da3e61`, docs `a9eea7b`; no tag).
Full plan, Results and findings: `git show a9eea7b:docs/FIX_ADD_PLAN.md`; prompts:
`git show a9eea7b:docs/prompts/`; per-task Results and validation:
`git show a9eea7b:docs/SESSION_STATE.md`. Owner test: Preview #6 (run
https://github.com/Alalkipgen/YFT/actions/runs/37811403962, 2026-10-09) → the seven items of
§2, Phase 15.

| ID | Task | Commits | Status |
| --- | --- | --- | --- |
| P34 | Downloads and merges keep going in the background; speed in the notification | `3157f94`, `a7e3a73`, `6325f37` | Preview #6: no problem reported |
| P35 | Faster merge for long videos (stream copy) | `5940288`, `ae9e67c`, `ff59e2c`, `0a18c19`, `5f62962`, `1f70d26` | Preview #6: no problem reported; the % waits at the start of the download → P41 |
| P36 | TikTok: Download on the For You feed and video pages | `9689062` | Preview #6 (VPN): "changed its page format" on every video in the browser and on many Home links → P39, P40 |
| P37 | Other sites: fresh links instead of HTTP 410 | `1186e3a`, `0144c92`, `10957b7`, `fd66038` | Preview #6: after "The first link is gone" the chain sometimes picked the pre-roll ad → P43 |
| P38 | Merge A → B → C, Preview #6 | `13d46bc`, `1b89041`, `098b12b`, `4da3e61`, `a9eea7b` | DONE (2026-10-08): 1532 tests, 0 failures, 66 skipped; `main` fast-forwarded |
| P8 | Signed `1.0.0-beta.4` | — | moved behind Phase 15 ([§1](#1-status-board)) |

**Phase 13** (P27–P33, 2026-10-07) is merged into `main` at `436aa90` (docs `5a5bddb`; no tag):
YouTube merge progress, other sites' video before the ad and the next video, Google search,
History, pop-up blocking, Preview #5. Plan, Results and prompts:
`git show 5a5bddb:docs/FIX_ADD_PLAN.md`, `git show 5a5bddb:docs/prompts/`,
`git show 5a5bddb:docs/SESSION_STATE.md`.

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
