# YFT Fix & Add Plan — Phase 12 (Preview #3 field fixes: saving, every quality, one sheet)

Owner phone test of **Preview #3** (2026-10-06, Xiaomi phone with HyperOS; Preview APK run
https://github.com/Alalkipgen/YFT/actions/runs/37455870506, commit `4db6c2b` = `main`), compared
with Snaptube on the same videos. Phase 11 (P0–P19: short sheet, slow networks, one lookup per
video, wide Download button, thumbnails, visionOS first, Facebook public page first, instant
sheet, early Download) is merged into `main`; this test found that **video downloads do not
save**, that YouTube and Facebook offer too few qualities, that other sites open a preview clip,
and that the sheet differs from site to site. Phase 12 fixes these with **three agents working
at the same time** (A, B, C, §0.7), then one merge and Preview #4. Written in Plan Mode on
2026-10-06; work starts when the owner pastes the prompts in [`prompts/`](prompts/README.md).
Phase 11's full plan and prompts stay in Git history: `git show 4db6c2b:docs/FIX_ADD_PLAN.md`
and `git show 4db6c2b:docs/prompts/` (summary in [§8](#8-done-before-phase-12)).

## Contents

0. [How to work](#0-how-to-work) (0.7: [three agents in parallel](#07-three-agents-in-parallel))
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Tasks](#5-tasks)
6. [Owner phone checklist](#6-owner-phone-checklist)
7. [Backlog](#7-backlog)
8. [Done before Phase 12](#8-done-before-phase-12)

## 0. How to work

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out **your agent's branch** (§0.7) and pull it. On the first start create it from
   `origin/work/phase-12-integration` (the plan commit on top of `main` `4db6c2b`). Never work on
   another agent's branch, on `work/phase-12-integration` (only P26 does) or on `main`.
3. Read §0, §3, §4, your tasks in §5 with their **Read first** files, and your own section of
   `docs/SESSION_STATE.md`.
4. Set up the environment when it is missing (§0.3), then run your task's validation before
   editing, so you know the starting state.
5. Record the task as `IN PROGRESS` in **your section** of `docs/SESSION_STATE.md`. Agents A, B
   and C never edit this plan file (`docs/FIX_ADD_PLAN.md`) or `docs/prompts/`: P26 copies
   status and Results from SESSION_STATE into §1 and §5.
6. Do the **Steps** in order. Stay inside the task and inside your files (§0.7); anything else
   goes to your SESSION_STATE section as a hand-off or a backlog note. When the code shows that a
   step is wrong, adapt it and say so in the Result ("Plan adapted: …").
7. Add the listed tests. A regression test must fail on the old code (§0.3, regression proof).
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists (only your sections of shared docs, §0.7), write the Result in
   your SESSION_STATE section (status `DONE (date)` or `OWNER CHECK`) and checkpoint with
   `scripts/checkpoint.sh "P2x: summary"`. Check CI for the pushed commit and fix a red run.
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
`/data/gradle-home` only means the first build downloads its dependencies). Stop stale daemons
with `pkill -f "[G]radleDaemon"`; on a 4 GiB machine run one Gradle command at a time. The full
validation needs Gradle metaspace 640 MiB (Phase 11 merge: the default hit a Metaspace OOM):
`export GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"` or
`-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=640m"`.

| Scope | Command |
| --- | --- |
| Agent A (P20, P21) | `./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-data:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` |
| Agent B (P22, P23) | `./gradlew --no-daemon --continue :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug` |
| Agent C (P24, P25) | `./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug` |
| Full (P26, P8) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` |
| Scripts, docs-only checkpoints | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests` |
| Line length (must print nothing) | `git diff -U0 origin/main -- '*.kt' '*.kts' \| grep '^+[^+]' \| LC_ALL=C.UTF-8 awk 'length > 101'` |

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
`git log --oneline --grep "P20:"` finds a task's commits.

### 0.5 Report to the owner (Burmese, short)

```text
Agent: A / B / C — Task: P2x — <title> — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run links> (debug APK: Artifacts › yft-debug-apk;
  preview: Preview APK run › yft-preview-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: … (hand-offs to other agents, if any)
နောက်တစ်ဆင့်: P2y / READY FOR MERGE
```

### 0.6 Phone builds for the owner

Every green checkpoint run uploads `yft-debug-apk` (14 days; app ID `com.alal.yft.debug`). Every
push that changes code also runs **Preview APK (test key)**, which uploads `yft-preview-apk`: the
minified release build as "YFT Preview" (`com.alal.yft.preview`), signed with a test key made in
that job, so the owner uninstalls the older YFT Preview before installing a newer one. Each
agent's branch gets its own preview runs; the owner may try one early, but the phone test that
counts is **Preview #4** = the Preview APK run of P26's merge commit on
`work/phase-12-integration` (§6). Only P8 signs with the release key.

### 0.7 Three agents in parallel

**Branches.** All three start from `origin/work/phase-12-integration` at the plan commit.

| Agent | Tasks (in order) | Branch | Area |
| --- | --- | --- | --- |
| A | P20 → P21, later P26 (integrator) and P8 | `work/phase-12-download-fix` | Saving files: download engines, storage, Downloads screen |
| B | P22 → P23 | `work/phase-12-site-qualities` | YouTube and Facebook adapters |
| C | P24 → P25 | `work/phase-12-generic-sheet` | Other sites' detection, the download sheet, Home and browser flow |
| A (P26) | merge A → B → C, Preview #4 | `work/phase-12-integration` | Integration only |

**Files each agent may change** (tests beside them under `src/test/` or `src/androidTest/`
included). Everything not listed belongs to nobody: change it only through a hand-off.

| Agent | Owns |
| --- | --- |
| A | `core-download/**`; `core-data/**` (one Room migration at most); `core-model/src/main/kotlin/com/alal/yft/core/model/download/**`; `app/src/main/java/com/alal/yft/download/**`; `app/src/main/java/com/alal/yft/feature/downloads/**`; `app/src/androidTest/java/com/alal/yft/download/**`; `app/build.gradle.kts` only for an `androidTestImplementation` line |
| B | `extractor-sites/**`; `extractor-api/**` (additions only); `app/src/main/java/com/alal/yft/detection/**` (coordinator, merge support, PO token, player script, lookup cache); `scripts/live-check.*`; docs `YOUTUBE_RISK_REVIEW.md`, `SUPPORT_MATRIX.md` |
| C | `core-browser/**`; `core-media/**`; `extractor-generic/**`; `core-model/src/main/kotlin/com/alal/yft/core/model/media/**` and `.../settings/**`; `app/src/main/java/com/alal/yft/feature/{quickdownload,browser,home,detectedmedia}/**`; `app/src/main/java/com/alal/yft/ui/**`; doc `design/DESIGN-NOTES.md` |
| Nobody (P26/P8 only) | `.github/workflows/**`, `gradle.properties`, `gradle/libs.versions.toml`, root and module Gradle files (except A's line above), `app/src/main/res/**`, `app/src/main/AndroidManifest.xml`, other `app` packages (`feature/library`, `feature/settings`, `thumbnail`, `diagnostics`, `MainActivity`), shared test helpers (`app/src/test/java/com/alal/yft/testing/**`: add new helpers in your own test folders), `AGENTS.md`, `README.md`, `docs/FIX_ADD_PLAN.md`, `docs/prompts/**`, `docs/HANDOFF.md`, `docs/PHASE_STATUS.md`, `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROJECT_CONTEXT.md`, ADRs |

**Shared docs — own section only.** Each agent edits only the section with its letter, which
the plan commit created; nothing above or below it:

- `docs/SESSION_STATE.md`: `## Agent A …`, `## Agent B …`, `## Agent C …` (status, Results,
  validation, CI links, hand-offs). The `## Overview` section is P26's.
- `CHANGELOG.md` under `## [Unreleased]`: `### Phase 12 — Agent A (P20, P21)` and the B and C
  headings. Replace the placeholder line, then add bullets below it. P26 folds them into
  Added/Changed/Fixed.
- `docs/TEST_MATRIX.md`: `### Agent A — P20, P21` (and B, C) under `## Phase 12`.

**Contracts** (code other agents use; change only by adding, with defaults; never rename,
remove or change a meaning):

- A → C: `DownloadEnqueuer`, `DownloadPlanFactory`, the public `DownloadQueue` API,
  `DownloadTask`, `DownloadFailure`, `DownloadFailureReason` (new values may be added; the sheet,
  the foreground service and `SiteLookupCache` read them).
- B → C: `SiteAdapterCoordinator` (`inspect`, `handles`, `videoKey`, `messageFor`),
  `SiteLookupCache`, the `extractor-api` types (`SiteExtractionRequest`, `SiteExtractionResult`).
- C → A, B: `MediaCandidate`, `MediaAsset`, `MediaVariant`, `MediaGroups`, `AudioFromVideo`,
  `BrowserRequestContext` (B's extractors build `MediaCandidate`s; A's plan factory reads
  `MediaVariant`s). New fields get defaults, so B's and A's code compiles unchanged.
- **Data B gives C** (P22, P23): every video candidate has its height (and width), container or
  MIME type, codecs (video and, for a file with sound, audio) and `contentLengthBytes` when the
  site states it, else a bitrate and the length so the sheet can estimate the size; adaptive
  video names its audio companion; HD/SD files carry their real height when the page tells it.
  C shows what it gets and never parses site data.

**Hand-offs.** When you need a change in a file you do not own, do not make it. Write
"Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and in your
report; the owner passes it on. Prefer a fix inside your own files when one exists.

**Merge (P26).** Agent A merges the three branches into `work/phase-12-integration`
(`--no-ff`) in this order: `work/phase-12-download-fix` (A), then `work/phase-12-site-qualities`
(B), then `work/phase-12-generic-sheet` (C); it runs that agent's scope validation after each
merge and the full validation at the end. Shared docs conflict
only if a section rule was broken: keep both sides. A code conflict means an ownership slip:
stop and report the files.

**Two agents instead of three** (if the owner prefers): Agent A owns A's and C's files and does
P20 → P21 → P24 → P25 → P26; Agent B does P22 → P23. The prompts work unchanged: the owner pastes
[`A-download-fix.md`](prompts/A-download-fix.md) into Agent A's chat and, after P21,
[`C-generic-and-sheet.md`](prompts/C-generic-and-sheet.md) with
`BRANCH_OVERRIDE: work/phase-12-download-fix`; P26 then merges A's branch (with C's work) and
B's.

## 1. Status board

Agents A, B and C record status in their own section of `docs/SESSION_STATE.md`; P26 copies it
here. AI agent time includes builds and CI waits on a 4 GiB sandbox.

**Agent A — `work/phase-12-download-fix`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P20 | [Video downloads save again (no more "Storage unavailable")](#p20--video-downloads-save-again) | Medium | 3–5 h | — | TODO |
| P21 | [Retry that recovers, honest failure reasons, failure Details](#p21--retry-and-failure-details) | Medium | 4–6 h | P20 | TODO |

**Agent B — `work/phase-12-site-qualities`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P22 | [YouTube: every quality with sizes (144p–4K), like Snaptube](#p22--youtube-every-quality) | Hard | 8–12 h | — | TODO |
| P23 | [Facebook: 720p and Audio from every link, Home and browser alike](#p23--facebook-every-quality) | Medium–Hard | 5–8 h | — | TODO |

**Agent C — `work/phase-12-generic-sheet`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P24 | [Other sites: the main video, not its previews](#p24--other-sites-main-video) | Hard | 6–10 h | — | TODO |
| P25 | [One sheet for every site](#p25--one-sheet-for-every-site) | Medium–Hard | 5–8 h | P24 (Home part) | TODO |

**Integration — Agent A, `work/phase-12-integration`**

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P26 | [Merge A → B → C, full validation, Preview #4](#p26--merge-and-preview-4) | Medium | 3–5 h | P20–P25 READY FOR MERGE | TODO |
| P8 | [Signed release 1.0.0-beta.4](#p8--signed-release-100-beta4) | Easy | 1–2 h | P26, Preview #4, owner OK | TODO |

In parallel the wall time is about 15–25 h (B and C are the long tracks) plus P26; one agent
alone would need 35–55 h.

## 2. What the owner saw

Phone test of Preview #3, 2026-10-06 (screenshots in the owner's chat; Snaptube on the same
videos):

1. **Video downloads fail.** Every video download ends at once as "Failed · Storage
   unavailable" at 0 B; Retry fails the same way. Audio downloads (M4A, MP3) finish.
2. **YouTube offers only 360p.** The owner's test video `4pKpLX9NG_k` (12:39) shows Video
   "360p" only, Audio "M4A · Size unknown" and MP3. Snaptube offers Fast M4A 12.3 MB, MP3
   14.4 MB (320K 28.8 MB), 144p 21.5 MB, 240p 32.3 MB, 360p 54.2 MB, 480p 79.7 MB, 720p
   123.7 MB, 1080p 380.5 MB, 2K 826.7 MB and 4K 1.7 GB.
3. **Facebook from Home** (a Facebook video link pasted): Video "HD · Size unknown" and
   "SD · 30 MB", no Audio section. Snaptube on the same video: Fast M4A 14.4 MB, 360p and 720p
   with sizes.
4. **Facebook in the browser** (the same kind of reel): Video "360p · 640×360 · 24 fps · 30 MB",
   Audio "M4A · 48 kbps" (about 11 MB), "More formats · 3", no 720p.
5. **Another site** (a large video site without an adapter; HLS player, many preview clips):
   Home says "50 media found"; the browser's Download opens "Video · 0:29 · 720p · 5.6 MB", a
   preview clip; the main video's sheet says "The media could not be reached. Check the
   connection and try again."
6. **One sheet everywhere.** The owner wants the same sheet on every site, like Snaptube's:
   Music (Fast M4A, MP3), Video (360p/480p "Fast", 720p "High quality"), "More formats" with
   every quality and a one-line description ("Normal quality for quick play", "Clear view and
   quick play", "High details for full screen play", …) and a Download button that never moves.

## 3. Owner decisions

| ID | Decision | Answer |
| --- | --- | --- |
| E3 | Test build | A release build with a test key (preview APK) for every phone test; the signed release only when the owner finds it stable (P8) |
| E4 | 2K/4K container | `.webm` (VP9 + Opus); AV1 only on Android 14+ and merges stay off until proven on a phone |
| E8 | Thumbnails | Real thumbnails (P19): only addresses detection found (or YouTube's picture of the video ID), HTTPS, no cookies |
| E9 | Default quality | 720p preselected; Settings › Default quality keeps every choice |
| E10 | Backlog advice B1–B4 | Advice given 2026-10-05 (§7); owner decision: — |
| E11 | Phase 12 plan P20–P26 | Recorded in Plan Mode (2026-10-06). Work starts when the owner pastes the agent prompts (`A-download-fix.md`, `B-site-qualities.md`, `C-generic-and-sheet.md`); each agent records its start date in its SESSION_STATE section |
| E12 | Parallel agents | Three agents (A, B, C) at once, each on its own branch and files (§0.7); A merges (P26). Fallback with two agents: A = P20, P21, P24, P25, P26; B = P22, P23 (agent default, owner may change) |
| E13 | Sheet names | One layout on every site (P25). YFT keeps quality-first row names ("720p · HD", "480p", "M4A", "MP3 · 128 kbps") and adds Snaptube-style one-line descriptions under each row. Agent default; the owner may answer `SHEET_NAMES=SNAPTUBE` (rows titled "Fast", "High quality", "Classic MP3" with the quality beside them) |
| E14 | Preview #4, then beta.4 | Preview #4 = the Preview APK run of P26's merge; P8 (signed `1.0.0-beta.4`) only after the owner's OK on Preview #4 |
| E15 | `main` | Agents never push to `main`. P26 fast-forwards `main` to the merge only with the owner's OK (`MAIN=OK`); P8 merges and tags with his OK |

## 4. Findings and root causes

Checked in the code at `4db6c2b` (2026-10-06), with the Android source (AOSP MediaProvider
`android14-release`) and yt-dlp `master` for comparison. Live numbers are from the agent sandbox
(data-centre network, 2026-10-06; status, sizes and markers only).

- **R1 — every direct video download fails before the first byte (item 1, high confidence).**
  `DirectTransferEngine.transfer` reads the destination's current length
  (`runCatching(destination::temporaryLength)`, about line 114) **before**
  `destination.prepare(...)` (about line 137) and turns any exception into
  `STORAGE_UNAVAILABLE`. For downloads into `Download/YFT` (Android 10+) the destination is
  `MediaStoreDownloadDestination`: `temporaryLength()` → `AndroidPublicContentStore.length(uri)`
  → `resolver.openFileDescriptor(uri, "r")`. A freshly inserted pending MediaStore row has no
  file yet: MediaProvider's insert only creates the folder, and opening with `"r"` uses
  `O_RDONLY` without `O_CREAT`, so it throws `FileNotFoundException` (only `"rw"` creates the
  file). So every fresh direct download fails at 0 B. The HLS, DASH, merge
  (`AudioVideoMuxEngine`) and MP3 paths call `prepare()` (which opens `"rw"`) first, so audio
  and merged files work. It shows now because P14 left YouTube with only its progressive 360p
  file (R3), a direct download, and Facebook's HD/SD files are direct too. The unit tests miss it
  because the fake store in `PublicDownloadDestinationTest` returns `outputs[uri]?.length`
  (null, never throws), and no instrumented test touches the real MediaStore
  (`app/src/androidTest` covers the audio extractor, muxer, MP3 and browser only).
- **R2 — Retry repeats the failure, and reasons mislead (item 1).** `DownloadQueue.resume`
  (about line 308) runs the same plan with the same pending row, so it fails the same way.
  `DirectTransferEngine` maps every `IOException` to a storage reason and every
  `IllegalStateException` to `STORAGE_UNAVAILABLE`, whether the source or the file failed;
  `DownloadQueue.runTask` maps an unexpected exception to `NETWORK`. Downloads shows only the
  reason's label ("Storage unavailable", `DownloadLabels`), so a field failure cannot be told
  apart without a debugger. The record keeps only `lastErrorCode` (reason name; Room version 4).
- **R3 — YouTube ends the lookup with the 360p file (item 2, high confidence on the code;
  phone Details will confirm the first step).** The lookup asks `VISIONOS` first **without
  visitor data** (`NO_PAGE` signals). Live in the sandbox, that request is answered
  `LOGIN_REQUIRED` "Sign in to confirm you're not a bot" for `4pKpLX9NG_k`, `8Mw9bwLTQFk` and
  `jNQXAC9IVRw` (only `dQw4w9WgXcQ` answered, with 27 adaptive formats, each with a direct
  address and `contentLength`). The failed answer is kept in `lookup.visionOsAnswer` and
  `askPlayer` reuses it later, so visionOS is never asked again with the watch page's visitor
  data. `askFallbacks` then asks `DEVICE_CLIENTS` (visionOS = the kept failure, then `ANDROID`)
  and the embedded player. `ANDROID` answers only itag 18 (progressive 360p, no
  `contentLength`); all its adaptive formats have neither an address nor a cipher (SABR only).
  For the embedded client `enough = lookup.offers.hasVideo`, which ANDROID's 360p already made
  true, so `extract` returns success before the page client (WEB/MWEB with the BotGuard PO token
  and the player-script solver, both in the app: `app/.../detection/potoken/`,
  `.../detection/script/`) or MWEB are collected. Result: 360p + "M4A" from that file (size
  unknown) + MP3, exactly what the owner saw. yt-dlp's defaults are `visionos,web`; it reads the
  web page first and sends its `visitorData` with every client, including visionOS; visionOS
  needs no GVS PO token, while `android`, `ios` and `mweb` need one for their adaptive formats.
- **R4 — Facebook: Home gets HD/SD only, the browser gets 360p only (items 3, 4).** Two code
  paths end with only the HD/SD files, and Home can take either: (a) since P15 the public page
  (Safari, no session) is the whole lookup when it is the video "with AVC tracks **or a whole
  file**", so a public page that came with `browser_native_hd/sd` but without a readable DASH
  manifest ends the lookup with HD/SD only; (b) shared links (`share/r/`, `share/v/`) and posts
  skip the public page (`requiresCanonicalResolution`), the page read instead had only the legacy
  HD/SD fields, and `avcLadder` runs only when `FacebookDashOffers.lacksAvcVideo(post.dashTracks)`
  is true, which is **false for an empty track list**, so no AVC ladder and no audio track. The
  HD/SD candidates carry no codecs, so `AudioFromVideo.canExtract` (needs an `mp4a` codec) offers
  no Audio, and HD's size check found nothing. Browser: the session page's DASH list had AVC only
  at 360p (plus AV1/VP9 above it); because some AVC existed, the ladder was not asked, and AV1 is
  dropped (merges off), so 720p is missing. Live (sandbox, Safari identity, no session) for reel
  `1545617074260365`: 612 KB page with `browser_native_hd_url`/`sd_url` and a DASH manifest: AVC
  640×360 (`avc1.4d001e`) and 1280×720 (`avc1.64001f`), audio `mp4a.40.5` 60.6 kbps, length
  31:38 → audio ≈ 14.4 MB (= Snaptube's Fast M4A). Manifest bandwidths are peaks (about 3.5 × the
  real average), so sizes come from the files, not from bandwidth × length. Known since P15:
  `/watch/?v=` links ask the ladder on `/watch/?v=`, which Safari answers without the video; the
  redirected `/<page>/videos/<id>/` URL has it.
- **R5 — other sites: previews count as videos and the playing video is never matched
  (item 5).** Home reads the page without a player (`HtmlMediaScanner`), which lists every
  preview and ad MP4; with no adapter `MediaGroups.pageVideos` keeps every group, so "50 media
  found". In the browser `BrowserViewModel.openMainVideo` uses
  `MediaGroups.mainVideo(videos, playingUrl)`; the page plays through MSE (`blob:` source), so
  `PlayingVideoProbe`'s address never matches a candidate, and the fallback "largest stated
  size, then height, then length" picks a short preview with a known size over the HLS main video
  without one. The main video's "could not be reached" is not proven yet:
  `QuickDownloadViewModel.resolveSafely` turns **any** exception (parser error, unsupported
  address, `IllegalStateException`) into `VariantResolutionFailure.NETWORK`, whose message is
  "The media could not be reached…", so it may not be the network at all. Generic requests
  already replay the page as `Referer` (`BrowserRequestContext.replayHeaders`).
- **R6 — one sheet, different data (item 6).** The layout already exists (`QuickDownloadChoices`
  `compact`: 2 Audio + 2 Video rows, "More formats · N", Details, pinned Download). What differs
  per site is the data: HD/SD names instead of heights, no Audio when codecs are unknown, "Size
  unknown" when neither a size nor a bitrate is known, a preview's "Video · 0:29" title, and
  Home's found list instead of the sheet on other sites. There are no row descriptions.

## 5. Tasks

Paths: `app/...` is `app/src/main/java/com/alal/yft/`, `core-browser/...` is
`core-browser/src/main/java/com/alal/yft/core/browser/`, `core-download/...` is
`core-download/src/main/java/com/alal/yft/core/download/`, `core-data/...` and `core-media/...`
follow the same pattern, `core-model/...` is `core-model/src/main/kotlin/com/alal/yft/core/model/`
and `extractor-sites/...` is `extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/`.
Tests sit beside them under `src/test/`. Line numbers are from `4db6c2b` and may drift.

### P20 — Video downloads save again

Medium · 3–5 h · finding R1 · **Agent A** · prompt [`A-download-fix.md`](prompts/A-download-fix.md)

**Goal:** every video download into `Download/YFT` starts, saves and plays: direct files
(YouTube 360p, Facebook HD/SD, other sites' MP4s) as well as merged, HLS and DASH downloads. A
fresh download never fails with "Storage unavailable" at 0 B.

**Read first:** `core-download/.../DirectTransferEngine.kt` (`transfer`: the length read before
`prepare`, the catch blocks near the end); `core-download/.../PublicDownloadDestination.kt`
(`MediaStoreDownloadDestination`, `SafDownloadDestination`, `AndroidPublicContentStore`:
`createPendingMedia`, `length`, `open` with `"rw"`, `storageCall`); `core-download/.../DownloadDestination.kt`
(`FileDownloadDestination.temporaryLength()` returns null when nothing was written — the contract
to match); `HlsTransferEngine.kt`, `DashTransferEngine.kt`, `AudioVideoMuxEngine.kt`,
`Mp3ConvertingTransferDispatcher.kt` (they `prepare()` first); `app/.../download/AndroidDownloadDestinationProvider.kt`;
tests `DirectTransferEngineTest.kt`, `PublicDownloadDestinationTest.kt` (fake store, `length`
near line 249); `app/src/androidTest/java/com/alal/yft/download/AudioVideoMuxerInstrumentedTest.kt`
(instrumented test style); `scripts/ci-emulator-smoke.sh` (CI runs `:app:connectedDebugAndroidTest`).

**Steps**
1. `AndroidPublicContentStore.length(uri)`: a pending row without a file yet returns `null`
   (catch `FileNotFoundException` from `openFileDescriptor(uri, "r")`; then the
   `OpenableColumns.SIZE` query; a missing or 0 size is `null`), like
   `FileDownloadDestination`. A missing row or a `SecurityException` still fails as storage.
2. `DirectTransferEngine.transfer`: a fresh download (`resumeFrom == null`) never reads the
   destination before `prepare()`; a resume reads the length, and when that read fails it drops
   the checkpoint and starts at byte 0 instead of failing.
3. Check every other engine and destination (HLS, DASH, merge, MP3, SAF, app-private file) for
   a read before `prepare()`; fix any you find and list the result in the Result.
4. The JVM fake store behaves like Android: `length()` of a MediaStore row that was created but
   never opened for writing throws `FileNotFoundException` (default on), so destination and
   engine tests see the real order.
5. New instrumented test `app/src/androidTest/java/com/alal/yft/download/MediaStoreDownloadInstrumentedTest.kt`
   (CI emulator, API 34): (a) `MediaStoreDownloadDestination.create(resolver,
   "yft-p20-<random>.mp4", "video/mp4")` → `temporaryLength()` does not throw → `prepare(1 MiB)`
   → writes at four offsets out of order → `commit()` → the published row has 1 MiB, is not
   pending and sits in `Download/YFT/`; (b) the real `DirectTransferEngine` with an `OkHttpClient`
   whose interceptor answers in memory (200 with `Content-Length`, 206 with `Content-Range`; no
   network) downloads 3 MiB in four segments into a fresh MediaStore destination → `Completed`,
   bytes equal; (c) `discard()` removes the pending row. Each test deletes what it created, also
   when it fails.

**Tests:** `DirectTransferEngineTest`: a fresh download into a destination whose length read
throws before `prepare()` completes (fails on the old code: `STORAGE_UNAVAILABLE` at 0 B); a
resume whose length read fails restarts at 0 and completes; `PublicDownloadDestinationTest`
with the Android-like fake. The instrumented test passes on the CI emulator.

**Owner check:** YouTube 360p and a Facebook HD file (direct files), YouTube 720p (merged), an
M4A and an MP3 → all finish and play in the Library.

**Docs:** TEST_MATRIX, CHANGELOG, SESSION_STATE (Agent A sections).

**Result:** —

### P21 — Retry and failure details

Medium · 4–6 h · finding R2 · needs P20 · **Agent A** · prompt [`A-download-fix.md`](prompts/A-download-fix.md)

**Goal:** a failed download says what failed and where, Retry starts over cleanly when the old
partial file cannot be used, and the owner can copy the details for a bug report.

**Read first:** `core-download/.../DownloadQueue.kt` (`resume`, `runTask`, failure mapping);
`DirectTransferEngine.kt` and the other engines' catch blocks; `core-download/.../DownloadTaskStore.kt`
(`RoomDownloadTaskStore`, record mapping); `core-data/.../db/DownloadRecordEntity.kt`
(`lastErrorCode`), `AppDatabase.kt` (version 4, migrations, `exportSchema`), `core-data/schemas/`;
`core-model/.../download/DownloadModels.kt` (`DownloadFailure`, `DownloadFailureReason`);
`app/.../feature/downloads/DownloadLabels.kt`, `DownloadsScreen.kt`, `DownloadsViewModel.kt`
(+ tests); `app/.../download/DownloadForegroundService.kt` (P17 drops a lookup on some reasons).

**Steps**
1. `DownloadFailure` gains `stage` (`CONNECT`, `READ_SOURCE`, `OPEN_FILE`, `WRITE_FILE`,
   `PUBLISH`, `MERGE`, `CONVERT`, `VERIFY`) and `detail` (exception class and a message without
   addresses or tokens, at most 120 characters); both default to null. Every engine sets them
   where it catches. The record keeps them (a nullable `lastErrorDetail` column with a Room
   4 → 5 migration, its schema JSON and a migration test).
2. Honest reasons: destination calls are wrapped, so a file problem is `STORAGE_UNAVAILABLE` or
   `INSUFFICIENT_STORAGE` and a source problem is `NETWORK` or the HTTP reason;
   `IllegalStateException` means storage only when the destination's lifecycle threw it; an
   unexpected exception in `runTask` keeps its class in `detail` (the reason stays as today
   unless a new value reads better; a new `DownloadFailureReason` value needs a label).
3. Retry: after a storage failure, or when the pending row or partial file is gone or shorter
   than the checkpoint, Retry discards the old destination and starts again into a new one from
   byte 0; other failures resume as today. A Retry that fails again shows the new details.
4. Downloads: a failed row shows its reason and a Details action (`download-failure-details`)
   with reason, stage, HTTP status, detail, plan type (direct, HLS, DASH, merge, MP3),
   destination kind (MediaStore, SAF, app) and Android version, and "Copy details"
   (`download-failure-copy`). No addresses, no tokens.

**Tests:** a file write failure → `STORAGE_UNAVAILABLE`/`WRITE_FILE` and a dropped connection →
`NETWORK`/`READ_SOURCE` (fails on the old code: a source `IOException` counts as storage); Retry
after a storage failure uses a new destination and completes (fails on the old code: the same
failure again); migration 4 → 5 keeps old records; the Details dialog shows and copies text
without `http` addresses (Compose test).

**Owner check:** airplane mode during a download → a network failure with Details; Retry after
reconnecting finishes; Copy details pastes text without links.

**Docs:** TEST_MATRIX, CHANGELOG, SESSION_STATE (Agent A sections).

**Result:** —

### P22 — YouTube: every quality

Hard · 8–12 h · finding R3 · **Agent B** · prompt [`B-site-qualities.md`](prompts/B-site-qualities.md)

**Goal:** a YouTube video offers every quality YouTube has for it — 144p to 1080p (AVC merged
with AAC) and 2K/4K (VP9 + Opus) — each with a size, plus Audio M4A with its size, as on
Snaptube. A bot-checked first answer never ends in 360p only.

**Read first:** `extractor-sites/.../youtube/YouTubeExtractor.kt` (`extract`, `visionOsFirst`,
`askFallbacks`, `askPlayer`, `collect`, the offers' `isComplete`/`hasVideo`),
`YouTubeClientProfile.kt` (`DEVICE_CLIENTS`, `VISION_OS`, `ANDROID`, `embedded`, `mobileWeb`,
`page`), `YouTubePlayerResponseParser.kt` (`pageSignals`, `visitorData`, `inspect`),
`YouTubeUrls.kt` and `YouTubeExtractorTest` with its fixtures; `app/.../detection/potoken/` and
`app/.../detection/script/` (PO token and player-script solver for page clients);
`docs/YOUTUBE_RISK_REVIEW.md`; ADR-006; for comparison yt-dlp's YouTube extractor
(`_video.py` `_DEFAULT_CLIENTS`, `_base.py` client table and PO token policies).

**Steps**
1. **visionOS with visitor data.** When the first visionOS answer is not complete (a bot
   check, `LOGIN_REQUIRED` that is not an age check, missing formats) and the watch page gave
   `visitorData`, ask visionOS once more with it (in the request context and as
   `X-Goog-Visitor-Id`) and use that answer instead of the kept one. A failed first answer is
   never reused by the chain. Visitor data stays in memory for this lookup only (never logged,
   never in details or fixtures).
2. **No early end with 360p.** No step ends the lookup while it has only progressive files: the
   embedded client's `enough` becomes `offers.isComplete` (an adaptive video with an audio
   track), like the device clients; the page client's answer (inline or asked, with the PO
   token and the player script) is collected before the lookup ends without adaptive formats;
   MWEB as today. `ANDROID`'s itag 18 stays a 360p row only when there is no adaptive 360p.
3. **Order.** Compare the chain with yt-dlp (visionOS with visitor data → the page client →
   MWEB → …) and move `ANDROID` behind the page client if it only adds 360p; record the
   evidence ("Plan adapted" when you keep the order).
4. **Sizes.** Every adaptive format's `contentLength` becomes `contentLengthBytes`; a format
   without one carries its bitrate and the video's length, so the sheet can show "~54 MB".
5. **Audio.** The M4A row comes from the adaptive AAC track (itag 140, with its size), not from
   the 360p file; MP3 converts that track.
6. **Details** say what each step got ("visionOS again with visitor data: 27 formats",
   "ANDROID: 1 progressive file, adaptive SABR only", "page client: 22 formats via player
   script").

**Tests (fixtures with `REDACTED` signed values):** visionOS bot check → watch page with visitor
data → visionOS again complete → rows 144p–1080p + 2K/4K (fails on the old code: 360p only);
visionOS bot-checked twice → `ANDROID` 18 only with SABR adaptive → the page client's ciphered
formats via the solver → full ladder (fails on the old code: ends after the embedded client);
the age-restricted case stays `LOGIN_REQUIRED`; the M4A row has itag 140's size.

**Live check (sandbox):** `4pKpLX9NG_k`, `8Mw9bwLTQFk`, `jNQXAC9IVRw`, `dQw4w9WgXcQ`: per client
the status, number of formats and heights only. If the sandbox stays bot-checked, say so; the
phone's Details are the proof.

**Owner check:** `4pKpLX9NG_k` in Home and in the browser → Video 144p…1080p (plus 2K/4K when
the video has them) with sizes close to Snaptube's (360p ≈ 54 MB, 720p ≈ 124 MB, 1080p ≈ 380 MB);
Audio M4A ≈ 12 MB; 720p and 1080p download and play with sound; Details lists the steps.

**Docs:** YOUTUBE_RISK_REVIEW (client order), SUPPORT_MATRIX (YouTube row), TEST_MATRIX,
CHANGELOG, SESSION_STATE (Agent B sections).

**Result:** —

### P23 — Facebook: every quality

Medium–Hard · 5–8 h · finding R4 · **Agent B** · prompt [`B-site-qualities.md`](prompts/B-site-qualities.md)

**Goal:** a Facebook video offers the same rows from Home and from the browser: 720p (and every
AVC height it has), 360p, Audio M4A with its size and MP3; HD/SD files show their real heights.

**Read first:** `extractor-sites/.../facebook/FacebookExtractor.kt` (`extract`, `publicPage`,
`avcLadder`, `resolvedIdentity`, `offers`), `FacebookDashOffers.kt` (`lacksAvcVideo`, `isAvc`),
`FacebookPageParser.kt`, `FacebookPageIdentity.kt`, `FacebookUrls.kt`
(`requiresCanonicalResolution`) and their tests and fixtures; `app/.../detection/MergeSupport.kt`
(`DeviceMergeSupport`, AV1 off); `core-model/.../media/AudioFromVideo.kt` (read only: why HD/SD
get no Audio).

**Steps**
1. The public page is the whole lookup only when it is the requested video **with AVC DASH video
   and an audio track**; a page with only HD/SD files goes on to the session page and the
   ladder, within P10's limits.
2. "Needs the AVC ladder" replaces `lacksAvcVideo`: no AVC video at all (an empty list too), or
   the best AVC height below the best height the page offers (any codec, or the HD file). Ask it
   once, as today (Safari, no session), on the **final** video URL: share, short and post links
   after their redirect; `/watch/?v=` → the redirected `/<page>/videos/<id>/` URL.
3. Merge the tracks of every page read for the same video ID (public, session, ladder) without
   duplicates (same representation ID, or same codec family, height and bandwidth).
4. HD/SD files: height and width from the matching DASH representation or the page's own fields;
   codecs when the page states them (for example the manifest's `avc1…` and `mp4a.40.x`), so
   the sheet can offer their sound as M4A; bitrate and length for an estimate when no size is
   stated. Never guess a codec or height the page does not show.
5. Details say which pages were read and what each added ("public page: AVC 360/720 + audio",
   "ladder: not needed (AVC 720)").

**Tests (fixtures with `REDACTED` signed values):** share link → a page with only HD/SD → the
final reel's Safari page with DASH → rows 720p/360p + Audio (fails on the old code); a public
page with only HD/SD is not the whole lookup (fails on the old code); the session page with AVC
360 + AV1 720/1080 → the ladder adds AVC 720 (fails on the old code); `/watch/?v=` redirect → the
ladder on the final URL; tracks from two pages are not duplicated.

**Live check (sandbox):** reel `1545617074260365` and one share link: rows, heights, codecs and
the audio track; sizes and markers only.

**Owner check:** the same reel pasted in Home and opened in the browser → the same rows (720p,
360p with sizes, Audio M4A ≈ 14 MB, MP3); 720p downloads and plays with sound.

**Docs:** SUPPORT_MATRIX (Facebook row), TEST_MATRIX, CHANGELOG, SESSION_STATE (Agent B
sections).

**Result:** —

### P24 — Other sites: main video

Hard · 6–10 h · finding R5 · **Agent C** · prompt [`C-generic-and-sheet.md`](prompts/C-generic-and-sheet.md)

**Goal:** on a site without an adapter, Home and the browser open the page's main video (its full
length), not a preview or an ad; the found count counts real videos; the main video can be
prepared and downloaded, or the sheet says exactly why not.

**Read first:** `core-browser/.../detection/HtmlMediaScanner.kt`, `PlayingVideoProbe.kt`,
`HeadlessPageFetcher.kt` and the rest of `core-browser/.../detection/`; `extractor-generic`;
`core-model/.../media/MediaGroups.kt` (`pageVideos`, `mainVideo`); `app/.../feature/browser/BrowserViewModel.kt`
(`openMainVideo`) and `BrowserDownloadFab.kt`; `app/.../feature/home/HomeViewModel.kt` and
`LinkInspector.kt` (`HeadlessLinkInspector`, the Found count); `app/.../feature/quickdownload/QuickDownloadViewModel.kt`
(`resolveSafely`, `messageFor`); `core-media`'s variant resolver (HLS master reading);
`app/.../ui/components/YftPromptbox.kt` ("N media found").

**Steps**
1. **The playing video.** `PlayingVideoProbe` also reports the playing element's length, picture
   size, muted/looping/autoplay state and whether it is in the main document or a frame. With a
   `blob:` source, `mainVideo` matches candidates by length (±2 s; HLS/DASH lengths from the
   playlist, MP4 lengths from the metadata probe), else takes the manifest the page loaded when
   that player started.
2. **Ranking without a match:** a known long length first (it beats a stated size), then the
   picture height, then the size. Demote what looks like a preview: under 60 s while a longer
   video exists, muted looping autoplay, inside links to other pages or thumbnail boxes, preview
   addresses (`preview`, `thumb`, `teaser`, `sprite`), third-party ad frames.
3. **Page signals for Home** (no player runs there): JSON-LD `VideoObject` (`contentUrl`,
   `embedUrl`, `duration`), `og:video`/`og:video:url`/`twitter:player:stream`, and a manifest
   (`.m3u8`, `.mpd`) named in the page's own scripts mark the main video; MP4s from thumbnail
   attributes (`data-preview*`, `data-mediabook`, `data-src` on thumbnails) are previews.
4. **Found count.** Home's "N media found" and the browser's list count videos (a main video
   with its qualities is one); previews are listed under "Other videos on this page (N)"
   (`quick-other-videos`). Home with one main video opens its sheet at once, like a site video
   link (P16 flow).
5. **Honest failure.** `resolveSafely` maps exceptions by type: `IOException` → NETWORK, parser
   problems → MALFORMED_MANIFEST, an unsupported or `blob:` address → INVALID_URL, HTTP statuses
   as today; a 403 says "The site refused this video (HTTP 403)". The sheet's Details name the
   step, the host and the status. Preparing an HLS master found on a page replays its `Referer`
   and `Origin` (and, for browser candidates, the site's cookies as today).

**Tests:** fixtures that copy the owner's case: one HLS master (several variants, over 10 minutes)
played through MSE, 40 preview MP4s (5–30 s, muted loops in thumbnail links) and one ad frame →
the main video is the HLS video in Home and in the browser, the count is 1 plus "Other videos"
(fails on the old code: the 29 s preview wins, 50 media found); the probe's length matches a
`blob:` player; a parser exception is not "could not be reached" (fails on the old code); the
master request carries `Referer` and `Origin`.

**Live check:** one public page of the same kind (an HLS player plus several short preview MP4s,
no sign-in, no age or identity check) when one is reachable from the sandbox; otherwise fixtures
and the owner's phone. Report hosts, counts, lengths and heights only.

**Owner check:** the site from Preview #3: Home → that video's sheet (its real length) with
"Other videos on this page"; the browser's Download → the same; the download finishes; if it
fails, a screenshot of the sheet's Details.

**Docs:** DESIGN-NOTES (found list), TEST_MATRIX, CHANGELOG, SESSION_STATE (Agent C sections).

**Result:** —

### P25 — One sheet for every site

Medium–Hard · 5–8 h · finding R6 · Home part needs P24 · **Agent C** · prompt [`C-generic-and-sheet.md`](prompts/C-generic-and-sheet.md)

**Goal:** the same sheet on every site and from every entry (Home, browser, feed, found list):
header (thumbnail, title, site, length), Audio (M4A, MP3 128), Video (the Default quality and the
next lower one), More formats with every row, a size or an estimate and a one-line description on
each row, Details and the pinned Download.

**Read first:** `app/.../feature/quickdownload/QuickDownloadChoices.kt` (`of`, `compact`, row
labels), `QuickDownloadScreen.kt`, `QuickDownloadViewModel.kt` (+ tests);
`core-model/.../media/AudioFromVideo.kt`, `MediaGroups.kt`; `core-model/.../settings/DownloadPreferences.kt`;
`docs/design/DESIGN-NOTES.md` (download sheet); `app/.../ui/components/YftFoundMedia.kt`;
`core-download/.../AudioTrackExtractor.kt` (read only).

**Steps**
1. **Names.** Video rows are named by height everywhere: "2160p · 4K", "1440p · 2K",
   "1080p · Full HD", "720p · HD", "480p", "360p", "240p", "144p". A file that says only HD/SD
   and has no height keeps "HD"/"SD" until its height is known (header check), then the row is
   renamed in place without moving (P11). Audio: "M4A" (the original sound, copied — fastest)
   and "MP3 · 128 kbps" in the short view; MP3 320 and 192 under More formats.
2. **Descriptions** (second line, `quick-row-description`): 144p "Low quality, smallest file";
   240p "Low quality for quick play"; 360p and 480p "Normal quality for quick play"; 720p "Clear
   view and quick play"; 1080p "High details for full screen play"; 2K and 4K "High details for
   big screen play"; M4A "Original sound, fastest"; MP3 "Plays everywhere". With
   `SHEET_NAMES=SNAPTUBE` (§3 E13) the titles become "Fast", "High quality" and "Classic MP3"
   with the quality beside them.
3. **Sizes:** the stated size, else an estimate from bitrate × length shown as "~54 MB", else
   "Size unknown"; a row never disappears for lack of a size (P11).
4. **Audio on every source with sound:** `AudioFromVideo.canExtract` also accepts an MP4 with
   sound whose codecs are unknown (other sites' files, Facebook HD/SD without stated codecs).
   Check that the audio extraction fails a file without AAC with a clear message
   (`AudioTrackExtractor`, read only); if it does not, write a hand-off to Agent A.
5. **Same everywhere:** the same order and short view on every site; Home's other-site path
   opens the sheet (P24 step 4); the found list uses the same names and sizes.

**Tests:** the choices table for four fixtures — YouTube full ladder, Facebook DASH + HD/SD,
Facebook HD/SD only, other site HLS + MP4 — gives the same sections, names and order; the
descriptions; "~" estimates; Audio from an MP4 with unknown codecs (fails on the old code: no
Audio section); a Compose test that descriptions show and Download stays visible.

**Owner check:** YouTube, Facebook (Home and browser) and the other site → the same sheet: Audio
M4A + MP3, Video 720p selected + 480p or 360p, More formats with descriptions, sizes on every
row.

**Docs:** DESIGN-NOTES (download sheet), TEST_MATRIX, CHANGELOG, SESSION_STATE (Agent C
sections).

**Result:** —

### P26 — Merge and Preview #4

Medium · 3–5 h · needs P20–P25 `READY FOR MERGE` · **Agent A (integrator)** · prompt
[`M-merge-preview4.md`](prompts/M-merge-preview4.md)

**Goal:** one branch with A, B and C, fully validated, and Preview #4 on the owner's phone.

**Steps**
1. Start only when the SESSION_STATE sections of A, B and C say `READY FOR MERGE` and their last
   commits have green CI (checkpoint validation; emulator smoke and Preview APK where code
   changed). Otherwise list what is missing and stop.
2. On `work/phase-12-integration` (pull it first): `git merge --no-ff` A's branch, run Agent A's
   scope validation; then B's, run B's scope; then C's, run C's scope. Doc conflicts: keep both
   sides. A code conflict: stop and report the files.
3. Full validation (640 MiB metaspace, §0.3), `:app:assembleRelease`, line check.
4. Docs: copy each agent's status and Results into §1 and §5; `docs/HANDOFF.md`,
   `docs/PHASE_STATUS.md`, the SESSION_STATE Overview, CHANGELOG (fold the three agent sections
   into Added/Changed/Fixed), SUPPORT_MATRIX (other sites' row from C's Result), TEST_MATRIX.
5. Checkpoint; CI: checkpoint validation, emulator smoke (with P20's MediaStore test) and the
   Preview APK run → **Preview #4**: its link and the §6 list to the owner in Burmese. Then stop.
   `main` is fast-forwarded only with `MAIN=OK`; P8 only with the owner's OK.

**Result:** —

### P8 — Signed release 1.0.0-beta.4

Easy · 1–2 h · needs P26, Preview #4 and the owner's OK · prompt
[`P8-signed-beta4.md`](prompts/P8-signed-beta4.md)

`yft.versionName=1.0.0-beta.4`, `yft.versionCode=4`, `docs/release/1.0.0-beta.4.md`, CHANGELOG
section; full validation; fast-forward `main` to `work/phase-12-integration`, tag
`v1.0.0-beta.4`; `release-draft.yml` builds the APK signed with the release key; record size,
SHA-256 and certificate. Merge, tag and signing need the owner's OK for this task
(`docs/RELEASE.md`).

**Result:** —

## 6. Owner phone checklist

Install `yft-preview-apk` from the Preview APK run the agent sends; uninstall the older YFT
Preview first (each run has a new test key).

**Preview #4 (after P26)**

1. **Saving (P20, P21):** YouTube 360p and 720p, a Facebook HD file and 720p, the other site's
   video, an M4A and an MP3 → all finish and play in the Library. Airplane mode during a
   download → a network failure with Details; Retry after reconnecting finishes.
2. **YouTube (P22):** `4pKpLX9NG_k` in Home and in the browser → Video 144p…1080p (2K/4K when
   the video has them) with sizes close to Snaptube's; Audio M4A ≈ 12 MB; MP3; Details lists the
   lookup steps.
3. **Facebook (P23):** the same reel pasted in Home and opened in the browser → the same rows:
   720p and 360p with sizes, Audio M4A ≈ 14 MB, MP3.
4. **Other site (P24):** the site from Preview #3 → Home and the browser open the main video
   (its real length) with "Other videos on this page"; the download finishes.
5. **One sheet (P25):** every site shows Audio (M4A, MP3 128), Video (720p selected + 480p or
   360p), More formats with a one-line description per row, sizes, Details and a fixed Download.
6. **Earlier fixes still work:** slow line ("Slow connection — still looking…"), the sheet opens
   at once, Download before the qualities arrive, thumbnails, the wide Download button, feeds,
   2K/4K, MP3.
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

## 8. Done before Phase 12

**Phase 11** (P0–P19, 2026-10-04 to 2026-10-06) is merged into `main` at `4db6c2b` (no tag).
Full plan, Results and findings: `git show 4db6c2b:docs/FIX_ADD_PLAN.md`; prompts:
`git show 4db6c2b:docs/prompts/`; per-task Results and validation:
`git show 4db6c2b:docs/SESSION_STATE.md`. Owner tests: Preview #1 (`1.0.0-beta.3-preview.1`,
run https://github.com/Alalkipgen/YFT/actions/runs/37294511016, 2026-10-05) → part 2; Preview #2
(run https://github.com/Alalkipgen/YFT/actions/runs/37414773994); Preview #3 (run
https://github.com/Alalkipgen/YFT/actions/runs/37455870506, 2026-10-06) → Phase 12.

| ID | Task | Commits | Status |
| --- | --- | --- | --- |
| P0–P7 | Part 1: browser navigation, Facebook pages, one sheet, Facebook qualities, feeds, 2K/4K, preview APK | `99efca6` … `ce3cd25` | DONE (2026-10-05) |
| P9 | Short download sheet | `13bbf05` | OWNER CHECK → Phase 12 |
| P10 | Slow networks | `bdc9f88` | OWNER CHECK → Phase 12 |
| P11 | Quality rows never vanish | `f82427b`, `cfad7b7` | OWNER CHECK → Phase 12 |
| P12 | One sheet and one lookup on site pages | `7953c0d` | OWNER CHECK → Phase 12 |
| P13 | Wide Download button | `7605133` | OWNER CHECK → Phase 12 |
| P19 | Real thumbnails | `03bde3a` | OWNER CHECK → Phase 12 |
| P14 | YouTube asks visionOS first (Track B) | `57663c0` | Preview #3: 360p only → P22 |
| P15 | Facebook public page first (Track B) | `0062660` | Preview #3: HD/SD or 360p only → P23 |
| P16 | Sheet opens at once | `e7a388a` | OWNER CHECK → Phase 12 |
| P17 | Reuse lookup results | `00a17c2` | OWNER CHECK → Phase 12 |
| P18 | Download before qualities arrive | `1f914fa` | OWNER CHECK → Phase 12 |
| — | Merge Track B into Track A, `main` fast-forwarded | `bef1455`, `4db6c2b` | DONE (2026-10-06) |
| P8 | Signed `1.0.0-beta.4` | — | moved behind Phase 12 ([§1](#1-status-board)) |

**Phases 8–10** (T01–T19, 2026-10-03 to 2026-10-04) are complete and released as
`1.0.0-beta.3`. Full plan: `git show 2f6284f:docs/FIX_PLAN.md`; prompts:
`git show 2f6284f:docs/prompts/`.

**T19 — release record (DONE, 2026-10-04):** `main` fast-forwarded to `2f6284f`; tag
`v1.0.0-beta.3`; Release draft https://github.com/Alalkipgen/YFT/actions/runs/37204457527 created
the draft pre-release: `video-downloader-1.0.0-beta.3.apk`, 6,334,176 bytes, SHA-256
`8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
`3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
(same key as beta.1 and beta.2), source commit `2f6284f`.
