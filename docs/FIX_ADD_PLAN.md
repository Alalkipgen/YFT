# YFT Fix & Add Plan — Phase 11 part 2 (fast, stable, one sheet)

Owner phone test of Preview APK #1 (`1.0.0-beta.3-preview.1`, 2026-10-05), compared with Snaptube
on the same videos and the same slow line. Part 1 (P0–P7: browser navigation, Facebook pages, one
download sheet, Facebook qualities, feeds, 2K/4K, preview APK) is on this branch and was tested on
the owner's phone; part 2 (P9–P19) fixes what that test found and adds real thumbnails. The plan
was written in "Plan Mode" on 2026-10-05; work starts when the owner asks for it (§3 E6). Prompts
for every task: [`prompts/`](prompts/README.md). Part 1's full plan and prompts stay in Git
history: `git show ce3cd25:docs/FIX_ADD_PLAN.md` and `git show ce3cd25:docs/prompts/` (summary in
[§8](#8-done-before-part-2)).

## Contents

0. [How to work](#0-how-to-work)
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Tasks](#5-tasks)
6. [Owner phone checklist](#6-owner-phone-checklist)
7. [Backlog](#7-backlog)
8. [Done before part 2](#8-done-before-part-2)

## 0. How to work

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out `work/phase-11-download-flow` and pull it (created from `main` at `2f6284f`; part 1
   ends at `ce3cd25`). Every Phase 11 task stays on this branch; nothing is merged into `main`
   before P8.
3. Read §0, §3, the task's section in §5, the findings it links (§4), its **Read first** files
   and `docs/SESSION_STATE.md`.
4. Set up the environment when it is missing (§0.3), then run the task's validation before
   editing, so you know the starting state.
5. Set the task to `IN PROGRESS` in the [status board](#1-status-board).
6. Do the **Steps** in order. Stay inside the task; anything else goes to [§7](#7-backlog). When
   the code shows that a step is wrong, adapt it and say so in the task's **Result**
   ("Plan adapted: …").
7. Add the listed tests. A regression test must fail on the old code (§0.3, regression proof).
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists and `CHANGELOG.md` (`## [Unreleased]`), fill in the task's
   **Result**, set the task to `DONE (date)` or `OWNER CHECK`, update `docs/SESSION_STATE.md`
   and checkpoint with `scripts/checkpoint.sh "Px: summary"`. Check CI for the pushed commit and
   fix a red run.
10. Report to the owner in Burmese (§0.5), then continue with the next task in the order of §3
    (E7) without waiting. Stop only for a failure you cannot fix, a decision §3 marks
    `PENDING`, or P8 without the owner's OK.

### 0.2 Rules for every task

- Product rules ([ADR-006](decisions/ADR-006-owner-override-any-working-method.md), owner
  2026-10-03): any working technique for public videos; no DRM, paywall, private-content or
  age-gate bypass; adapters never sign in.
- Never log, print, commit or put into fixtures: cookies, tokens, `Authorization` values, signed
  media or image URLs (CDN query strings), keystores, `local.properties` or `.env`. Fixtures keep
  hosts and paths and replace signed query values with `REDACTED`; logs and lookup details name
  hosts, not full addresses.
- Kotlin lines stay within 100 characters. Keep every existing `testTag` (new tags are named in
  the task). No unrelated refactors.
- `WebView` and `WebSettings` methods run on the main thread only; `shouldInterceptRequest` and
  `@JavascriptInterface` methods run on other threads.
- Site tasks need a live check of a public page (`scripts/live-check.sh`); report status, host,
  path, sizes and markers only. TikTok is banned in India, so the owner cannot check it on his
  phone: TikTok work is verified with fixtures, the CI emulator and sandbox live checks (§7 B4).
- The agent sandbox has no emulator; use the CI emulator job (`emulator-smoke.yml`) for real
  WebView and MediaCodec/MediaMuxer checks.
- Network tasks stay polite: no more requests than the task allows, retries only as P10 defines
  them, never two lookups of the same video at once.
- Remote images (P19): only thumbnail addresses that detection already found, or YouTube's
  `i.ytimg.com` picture of the video ID; HTTPS only, no cookies, size-capped (§3 E8).
- Keep temporary files and backups outside the repository (`/data/tmp`, `/data/bak`):
  `scripts/checkpoint.sh` stages with `git add -A`. Never undo work with `git reset --hard`,
  `git clean` or `git stash`.

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
files and install what is missing (on 2026-10-05 the NDK and CMake were missing). Stop stale
daemons with `pkill -f "[G]radleDaemon"`; on a 4 GiB machine run one Gradle command at a time.

| Scope | Command |
| --- | --- |
| Quick (checkpoint default) | `./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug` |
| Modules (add the ones you touched) | `:core-browser:testDebugUnitTest` `:core-download:testDebugUnitTest` `:core-media:testDebugUnitTest` `:core-data:testDebugUnitTest` `:core-model:test` `:extractor-api:test` `:extractor-generic:test` `:extractor-sites:test` |
| Full (Preview #2, Preview #3, P8) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` |
| Scripts, docs-only checkpoints | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests` |
| Line length (must print nothing) | `git diff -U0 origin/main -- '*.kt' '*.kts' \| grep '^+[^+]' \| LC_ALL=C.UTF-8 awk 'length > 101'` |

**Regression proof.** Copy the files you changed to `/data/bak/<task>/`, put the old version
back (for example `git show HEAD:<path> > <path>` before your commit), run the new tests and see
them fail, then restore your version with `cp` and check it with `cmp`. Name the tests that
failed on the old code in the task's Result.

**CI.** `checkpoint-validation` runs on every `work/**` push. `emulator-smoke` and `preview-apk`
run only when code changes (`app/**`, `core-*/**`, `extractor-*/**`, Gradle files). Status for
the branch:

```bash
curl -s "https://api.github.com/repos/Alalkipgen/YFT/actions/runs?branch=work/phase-11-download-flow&per_page=6" \
  | jq -c '.workflow_runs[] | {name, sha: .head_sha[0:7], status, conclusion, url: .html_url}'
```

### 0.4 Status values

`TODO` · `IN PROGRESS` · `BLOCKED (reason)` · `OWNER CHECK` (done on the branch, waiting for the
owner's phone) · `DONE (date)` · `SKIPPED (decision)`. Checkpoint messages start with the task
ID, so `git log --oneline --grep "P9:"` finds a task's commits.

### 0.5 Report to the owner (Burmese, short)

```text
Task: Px — <title> — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run link> (debug APK: Artifacts › yft-debug-apk;
  preview: Preview APK run › yft-preview-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: …
နောက်တစ်ဆင့်: Py
```

### 0.6 Phone builds for the owner

Every green checkpoint run uploads `yft-debug-apk` (14 days; app ID `com.alal.yft.debug`,
installs next to the beta). Every push that changes code also runs **Preview APK (test key)**,
which uploads `yft-preview-apk`: the minified release build as "YFT Preview"
(`com.alal.yft.preview`), signed with a test key made in that job. Each run has a new key, so the
owner uninstalls the older YFT Preview before installing a newer one. **Preview #2** is the
preview run of the last Group 1 task (P19), **Preview #3** that of the last Group 2 task (P18);
the agent sends its link with the §6 list. Only P8 signs with the release key.

## 1. Status board

AI agent time includes builds and CI waits on a 4 GiB sandbox. Order: §3 E7.

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| — | **Group 1 — short sheet, slow networks, one lookup, thumbnails → Preview #2** | | | | |
| P9 | [Short download sheet: 2 audio + 2 video rows, More formats, pinned Download, 720p default](#p9--short-download-sheet) | Medium | 4–6 h | — | TODO |
| P10 | [Slow networks: wait while data flows, retry, Home waits 90 s](#p10--slow-networks) | Medium | 4–6 h | — | TODO |
| P11 | [Quality rows never vanish](#p11--rows-never-vanish) | Medium | 4–6 h | P9 | TODO |
| P12 | [Site pages: one sheet, one lookup](#p12--one-sheet-and-one-lookup-on-site-pages) | Medium–Hard | 5–8 h | P9 | TODO |
| P13 | [Wide Download button on video pages](#p13--wide-download-button) | Easy | 2–4 h | P12 | TODO |
| P19 | [Real thumbnails (sheet, found list, Downloads)](#p19--real-thumbnails) | Medium | 5–8 h | P9 | TODO |
| — | **Group 2 — faster lookups, instant sheet → Preview #3** | | | | |
| P14 | [YouTube asks visionOS first](#p14--youtube-asks-visionos-first) | Hard | 6–10 h | P10 | TODO |
| P15 | [Facebook: public page first](#p15--facebook-public-page-first) | Medium–Hard | 4–7 h | P10, P11 | TODO |
| P16 | [The sheet opens at once and fills in](#p16--sheet-opens-at-once) | Hard | 8–12 h | P9, P11, P12 | TODO |
| P17 | [Reuse lookup results](#p17--reuse-lookup-results) | Medium | 4–6 h | P12, P16 | TODO |
| P18 | [Download before the qualities arrive](#p18--download-before-qualities-arrive) | Medium–Hard | 4–7 h | P16 | TODO |
| P8 | [Signed release 1.0.0-beta.4](#p8--signed-release-100-beta4) | Easy | 1–2 h | P9–P19, Preview #3, owner OK | TODO |

Total about 50–80 h of agent time for P9–P19, plus P8.

## 2. What the owner saw

Phone test of Preview APK #1 (`1.0.0-beta.3-preview.1`), 2026-10-05 (screenshots in the owner's
chat; Snaptube on the same videos and the same slow line, about 5–10 KB/s):

1. **Slow networks fail.** YouTube lookups end with "YouTube could not be reached. Check the
   connection and try again."; Home ends with "The page took too long to answer. Try it in the
   browser." Snaptube waits and opens the sheet.
2. **The sheet is too long.** Every audio and video row is listed at once, the Download button
   scrolls off the screen, and 4K (6.5 GB on the test video) is preselected. Snaptube shows two
   Music rows and two Video rows, "More formats" for the rest, and a Download button that never
   moves.
3. **Facebook rows vanish.** Quality rows show up late, disappear or never appear, mostly on the
   slow line.
4. **A list instead of the video.** On a YouTube or Facebook video page the Download button can
   open "Found on this page 4" instead of the video's sheet, and the first tap on a watch page
   waits behind "Finding the video on screen…" for a second lookup.
5. **No wide Download button.** Snaptube shows a wide Download button under the video page; YFT
   has only the small round button.
6. **The sheet waits for the lookup.** Snaptube opens the sheet at once (the link as title, no
   sizes yet) and fills it in; YFT shows nothing until the lookup ends.
7. **Thumbnails** (owner request, 2026-10-05): show the real video picture, like Snaptube, in the
   sheet, the found list and Downloads.

## 3. Owner decisions

| ID | Decision | Answer |
| --- | --- | --- |
| E1 | Part 1 plan P0–P8 | Approved (owner, 2026-10-04); P0–P7 done, P8 waits (§8) |
| E2 | Part 1 order | P0 → P1 → … → P7 → owner test → P8; done up to P7, owner test 2026-10-05 |
| E3 | Test build | A release build with a test key (preview APK) for every phone test; the signed release only when the owner finds it stable (P8) |
| E4 | 2K/4K container | `.webm` (VP9 + Opus), because YouTube serves 2K/4K only as VP9/AV1; AV1 only on Android 14+ (agent default in part 1) |
| E5 | Prompts | One prompt per task plus the generic master prompt in `docs/prompts/` |
| E6 | Part 2 plan P9–P19 | Recorded in Plan Mode (owner, 2026-10-05). Work starts when the owner asks for a task (a prompt with `TASK: auto` or `TASK: P9` is that request); record the start date here |
| E7 | Part 2 order | P9 → P10 → P11 → P12 → P13 → P19 → **Preview #2** (link to the owner) → P14 → P15 → P16 → P17 → P18 → **Preview #3** → owner phone test → P8 with his OK. Task after task without stopping or asking; after Preview #2 continue with P14 unless the owner says stop (`OWNER ANSWERS: STOP_AFTER=P19`) |
| E8 | Thumbnails | Show real thumbnails (owner, 2026-10-05, P19). Only addresses detection found (or YouTube's picture of the video ID), HTTPS, no cookies; replaces the old "YFT never fetches remote images" comment and follows `docs/design/DESIGN-NOTES.md` §3 |
| E9 | Default quality | 720p preselected (P9); Settings › Default quality still offers every choice, a saved choice stays (agent default in this plan) |
| E10 | Backlog advice B1–B4 | Advice given 2026-10-05 (§7); owner decision: — |

## 4. Findings and root causes

Checked in the code at `ce3cd25` (2026-10-05). Live numbers are from the agent sandbox
(data-centre network, 2026-10-05; sizes and markers only).

- **H1 — fixed time limits (item 1).** `OkHttpExtractorClient.Policy.callTimeoutSeconds = 15`
  ends every adapter request after 15 s in total, without a retry; `HeadlessPageFetcher` (generic
  pages) has the same 15 s limit. The client's own connect (20 s) and read (30 s) limits from
  `NetworkConfiguration` never matter. An `IOException` becomes `SiteExtractionFailure.NETWORK`,
  which `SiteAdapterCoordinator.messageFor` shows as "<Site> could not be reached. Check the
  connection and try again." YouTube's watch page is 166 KB compressed (739 KB raw), so it needs
  17–33 s at 5–10 KB/s. Home's `LinkInspector.TIMEOUT_MILLIS = 25_000` ends the whole lookup
  with "The page took too long to answer." A browser lookup's failure shows as the page's top
  notice (`BrowserUiState.siteNotice`).
- **H2 — one long scrolling sheet (item 2).** `QuickDownloadScreen` puts the header, both
  sections and the Download button (`quick-download`) into one `Column(...).verticalScroll(...)`,
  so with 4 audio and 8–10 video rows the button scrolls away.
  `DownloadPreferences.defaultQuality = QualityPreference.HIGHEST` preselects 2160p.
- **H3 — rows wait for size checks (item 3).** Facebook's merged, progressive and audio
  candidates carry no `contentLengthBytes`. When the sheet opens, each one is resolved with
  `DefaultVariantResolver` (HEAD and MP4 header range reads, 10 s each), and
  `QuickDownloadChoices.of` skips a source whose asset is still missing
  (`source.asset ?: return@forEachIndexed`), so rows show up late or never.
- **H4 — Facebook needs two page requests.** With the user's session and a Chrome desktop
  identity Facebook's page (about 130–220 KB) lists AV1 video only, so `FacebookExtractor.avcLadder`
  asks a second time as Safari (`FacebookPageIdentity.AVC_LADDER_USER_AGENT`, no session). The
  Safari page alone already has `browser_native_sd/hd`, the AVC ladder (two heights) and mp4a
  sound (live, two public reels).
- **H5 — YouTube always loads the watch page first.** `YouTubeExtractor.extract` GETs the watch
  page (`YouTubeUrls.watchPageFetchUrl`) before any player request (ADR-006 Implementation step
  1). The `VISIONOS` client's answer alone (16.7 KB) had the title, the length and 27 formats,
  each with `contentLength` (live, 2026-10-05). yt-dlp's default YouTube clients are
  `visionos,web`, and its `player_skip=webpage` skips the page, at the risk of missing formats or
  details (yt-dlp README, YouTube extractor arguments).
- **H6 — second lookups and the list (item 4).** On a watch page with nothing found yet
  (`savableCount == 0`), `BrowserDownloadFab.action` returns `FIND_VIDEO_ON_SCREEN`, which runs a
  second full adapter lookup while the page's own lookup is still running; on a feed every tap
  waits for a full lookup behind `FINDING_NOTICE`. `savableCount > 1` returns `SHOW_LIST`, and
  `MediaGroups.pageVideos` returns every group when no candidate carries the page's video ID, so
  a YouTube or Facebook page shows "Found on this page 4" while its adapter is pending or failed.
  The generic metadata probes (`probePermits = Semaphore(permits = 2)`) also run on adapter pages
  and share the slow line with the adapter.
- **H7 — the sheet opens after the lookup (item 6).** Home and the feed open the sheet only
  after a successful lookup. Snaptube opens it at once (the link as title, no sizes), then fills
  it in. YouTube's player script (552 KB compressed) is needed only on the fallback path and is
  kept in memory only.
- **H8 — only a round button (item 5).** `BrowserDownloadFab` is the browser's only Download
  control.
- **H9 — no remote thumbnails (item 7).** Saved files already show a frame (`MediaDetails.kt`,
  `MediaMetadataRetriever`) in the Library, Home's recent list, the mini player and finished
  downloads. The sheet header draws `YftThumbnail(image = null)` (comment: "YFT never fetches
  remote images"), the found list passes no thumbnail to `YftFoundMedia`, and a running download
  shows the placeholder. Every adapter already sets `MediaCandidate.thumbnailUrl` (YouTube's
  largest `videoDetails` thumbnail, Facebook's post thumbnail, TikTok's cover, Vimeo's thumbs,
  the generic `<video poster>`), and `QuickDownloadViewModel` copies it to
  `MediaAsset.thumbnailUrl`, but nothing loads it. There is no image loader; DESIGN-NOTES §3
  already allows found `thumbnailUrl`s through the hardened OkHttp client.

## 5. Tasks

Paths: `app/...` is `app/src/main/java/com/alal/yft/`, `core-browser/...` is
`core-browser/src/main/java/com/alal/yft/core/browser/`, `core-media/...` and `core-data/...`
follow the same pattern, `core-model/...` is `core-model/src/main/kotlin/com/alal/yft/core/model/`
and `extractor-sites/...` is `extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/`.
Tests sit beside them under `src/test/`.

### P9 — Short download sheet

Medium · 4–6 h · finding H2 · prompt [`P9-short-sheet.md`](prompts/P9-short-sheet.md)

**Goal:** the sheet fits on one screen like Snaptube's: two Audio rows, two Video rows, More
formats for the rest and a Download button that is always visible; 720p is preselected.

**Read first:** `app/.../feature/quickdownload/QuickDownloadScreen.kt`, `QuickDownloadChoices.kt`,
`QuickDownloadViewModel.kt` and their tests; `core-model/.../settings/DownloadPreferences.kt`
(+ test); `app/.../feature/settings/SettingsScreen.kt` (+ test); `docs/design/DESIGN-NOTES.md`
(download sheet).

**Steps**
1. Short view (the default): Audio = "M4A" (the original sound, copied — the fast one) and "MP3 ·
   128 kbps"; Video = the preferred quality and the next lower one (720p · HD and 480p by
   default, 360p when there is no 480p). A missing quality falls back to the nearest lower one,
   then the nearest higher one; a source with fewer rows shows fewer. Row names stay as in
   P3-FIX (no "Fast"/"High" titles).
2. A "More formats · N" row expands the same sheet to today's full list (M4A, MP3 320/192/128 and
   every Video row up to 4K), in today's order and without repeating a row; "Fewer formats" goes
   back. The selected row stays selected. No new screen.
3. Pin the Download button and the actions beside it at the bottom, outside the scrolling list,
   in both views; only the rows scroll.
4. `DownloadPreferences.defaultQuality` becomes `UP_TO_720P` for anyone who never chose one;
   Settings › Default quality keeps every choice (Highest too), and a saved choice stays.
5. New tags `quick-more-formats` and `quick-fewer-formats`; every existing tag stays.

**Tests:** choices table (720p + 480p by default; 720p missing → the nearest lower; only 1080p and
4K → 1080p first; Audio M4A + MP3 128; More formats lists every row once); a Compose test that
`quick-download` is displayed without scrolling with 12 rows expanded (fails on the old code);
the new default (fails on the old code); Settings still offers Highest.

**Owner check:** a YouTube 4K video → two Audio and two Video rows with 720p selected and
Download visible; More formats → 2K/4K rows; a 720p and an MP3 download work.

**Docs:** DESIGN-NOTES (sheet), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P10 — Slow networks

Medium · 4–6 h · finding H1 · prompt [`P10-slow-networks.md`](prompts/P10-slow-networks.md)

**Goal:** on a slow line (5–10 KB/s) a lookup keeps going while data arrives and retries short
drops, instead of failing after 15 s or 25 s.

**Read first:** `app/.../detection/OkHttpExtractorClient.kt`, `SiteAdapterCoordinator.kt`,
`SiteAdapterModule.kt` (+ tests); `core-browser/.../detection/HeadlessPageFetcher.kt` (+ test);
`core-data/.../network/NetworkConfiguration.kt`; `app/.../feature/home/LinkInspector.kt`,
`HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt` (+ tests);
`app/.../ui/components/YftPromptbox.kt` (`PromptboxStatus`);
`app/.../feature/browser/BrowserViewModel.kt` (`siteNotice`).

**Steps**
1. Adapter and headless page requests fail only after 20 s without any data (connect and read)
   or 60 s in total, instead of the 15 s total limit. Body size limits stay.
2. Up to two automatic retries, after 1 s and 3 s, for timeouts, connection failures and HTTP
   502/503/504 — never for other HTTP answers, login walls or a cancelled lookup. YouTube's
   player requests (POST, read-only) may be retried too. Retry delays are injectable for tests.
3. Home: the whole lookup may take 90 s instead of 25 s. After 10 s the status line says "Slow
   connection — still looking…" with Cancel; Cancel stops the lookup and its requests.
4. Browser: a background site lookup that fails with `NETWORK` sets no top notice; the Download
   button stays, and the sheet (or the next tap) shows "Couldn't reach <Site>." with Retry,
   which runs the lookup again.
5. All other messages stay as they are.

**Tests:** extractor client with MockWebServer (the policy scaled down in the test): a slow body
that keeps sending succeeds where the old total limit failed; no data for the idle limit fails;
a dropped first connection succeeds on the retry with two requests (fails on the old code); a
404 is not retried. Home with virtual time: an answer after 60 s succeeds (fails on the old
code), "Slow connection — still looking…" after 10 s, Cancel. Browser: `NETWORK` sets no
`siteNotice`; Retry runs the lookup again.

**Owner check:** on the slow line (or the phone's 2G/3G setting) paste a YouTube link → "Slow
connection — still looking…" → the sheet opens; the browser shows no "could not be reached".

**Docs:** SUPPORT_MATRIX (network limits), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P11 — Rows never vanish

Medium · 4–6 h · finding H3 · needs P9 · prompt
[`P11-rows-never-vanish.md`](prompts/P11-rows-never-vanish.md)

**Goal:** every quality the lookup found is listed at once and stays; sizes fill in later.

**Read first:** `QuickDownloadChoices.kt`, `QuickDownloadViewModel.kt`, `QuickDownloadScreen.kt`
(+ tests); `core-media/.../resolver/DefaultVariantResolver.kt` (+ test);
`core-model/.../media/MediaAsset.kt`; `extractor-sites/.../facebook/FacebookDashOffers.kt`,
`FacebookQualityMetadata.kt`, `FacebookExtractor.kt` (+ test).

**Steps**
1. Build the rows from the adapter's data as soon as the lookup ends: height, codec, frame rate,
   bitrate and length. A row without a known size shows "~<size>" from bitrate × length, or no
   size.
2. Size checks run in the background, two at a time, and only update a row's size; a failed
   check keeps the row ("size unknown"). `QuickDownloadChoices.of` no longer drops a source
   without a resolved asset.
3. The final check runs when Download is tapped (with P10's retries); a link that no longer
   works shows "This quality is not available now — choose another" in the sheet.
4. When a height exists both as a DASH row with a known height and as a progressive file without
   one (`browser_native_sd/hd`), the short view keeps the DASH row; the progressive file stays
   under More formats as "SD"/"HD".
5. Rows keep their order while sizes arrive (no jumping).

**Tests:** unresolved sources still give every row (fails on the old code); a failed size check
keeps the row; the estimated size text; Download on a dead row shows the message and starts
nothing; the order stays stable.

**Live check:** a public Facebook reel — heights and row count straight from the page, before
any size check (heights and counts only).

**Owner check:** a Facebook reel on the slow line → every quality at once; sizes fill in;
nothing disappears.

**Docs:** SUPPORT_MATRIX (Facebook), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P12 — One sheet and one lookup on site pages

Medium–Hard · 5–8 h · finding H6 · needs P9 · prompt
[`P12-one-lookup-per-page.md`](prompts/P12-one-lookup-per-page.md)

**Goal:** on YouTube, Facebook, TikTok and Vimeo video pages the Download button always opens
that video's sheet — never "Found on this page N" — and each video is looked up once.

**Read first:** `app/.../feature/browser/BrowserViewModel.kt`, `BrowserDownloadFab.kt`,
`BrowserUiState.kt`, `BrowserScreen.kt` (`BrowserRoute`) and their tests;
`core-model/.../media/MediaGroups.kt` (+ test); `app/.../feature/detectedmedia/DetectedMediaStore.kt`;
`app/.../detection/SiteAdapterCoordinator.kt`;
`core-browser/.../detection/FocusedVideoProbe.kt`, `MediaMetadataProbe.kt`.

**Steps**
1. On a site video page (watch, shorts, reel, video, post) the button always means "this
   video". While the page's lookup runs, the button shows a small spinner; a tap opens the sheet
   in its loading state (`quick-loading`) and fills it when that same lookup ends. No second
   lookup.
2. On a feed, a focused link with the same video ID as a running or finished lookup joins it
   instead of starting another one.
3. Generic metadata probes wait while a site lookup runs on the page and run afterwards only
   when the adapter found nothing.
4. `MediaGroups.pageVideos` never falls back to "every group" on an adapter site. On other pages
   with several videos, the button opens the main video's sheet (the playing one, else the
   largest) with a row "Other videos on this page (N)" that opens the list.
5. A failed site lookup shows its message with Retry (P10) in the sheet, not the list.

**Tests:** action table (a site video page with 0, 1 or 4 found → this video's sheet; a generic
page with 4 → the main sheet with the others row); one adapter call for two taps during a lookup
(fails on the old code: two calls); probes wait for the site lookup; `pageVideos` on an adapter
site; failure → the sheet with Retry.

**Owner check:** a YouTube watch page and a Facebook reel in the browser → Download → that
video's sheet at once or after one short wait; never "Found on this page 4".

**Docs:** SUPPORT_MATRIX (browser), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P13 — Wide Download button

Easy · 2–4 h · finding H8 · needs P12 · prompt
[`P13-wide-download-button.md`](prompts/P13-wide-download-button.md)

**Goal:** a wide "Download" button on video pages, like Snaptube's, drawn by YFT (nothing is
inserted into the site's page).

**Read first:** `BrowserScreen.kt`, `BrowserDownloadFab.kt`, `BrowserUiState.kt` (+ tests);
`app/.../ui/components/YftButtons.kt` (`YftPrimaryButton`); `docs/design/DESIGN-NOTES.md`.

**Steps**
1. On a site video page (and a generic page with one main video) show a full-width "Download"
   button at the bottom of the page area, above the bottom bar; the WebView gets matching
   bottom padding, so the page's own controls stay reachable.
2. It does what the round button does on that page (P12) and shows a small spinner while the
   lookup runs.
3. One button at a time: the round button hides while the wide one shows; feeds and other pages
   keep the round button (P5).
4. Hidden in full screen, while the sheet is open and while the keyboard is up.
5. New tag `browser-download-wide`; content description "Download this video".

**Tests:** visibility table (video page, feed, generic page with one or several videos, full
screen, sheet open, keyboard); a tap sends the same event as the round button; the round button
hides (fails on the old code: there is no wide button).

**Owner check:** a YouTube watch page and a Facebook reel → the wide Download button under the
page → the video's sheet.

**Docs:** DESIGN-NOTES (browser), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P19 — Real thumbnails

Medium · 5–8 h · finding H9 · needs P9 · prompt [`P19-thumbnails.md`](prompts/P19-thumbnails.md)

**Goal:** the sheet, the found list and Downloads show the video's real picture.

**Read first:** `app/.../ui/components/YftThumbnail.kt`, `YftFoundMedia.kt`;
`QuickDownloadScreen.kt`, `QuickDownloadViewModel.kt`;
`app/.../feature/downloads/DownloadsScreen.kt` (+ test); `app/.../feature/library/MediaDetails.kt`
(cache pattern); `core-model/.../media/MediaAsset.kt`, `MediaCandidate.kt`;
`core-data/.../network/NetworkConfiguration.kt`, `core-data/.../db/DownloadRecordEntity.kt`;
`extractor-sites/.../youtube/YouTubeUrls.kt`; DESIGN-NOTES §3; `docs/PROJECT_CONTEXT.md`
(privacy).

**Steps**
1. `RemoteThumbnailLoader` (app, no new library) on the shared OkHttp client: HTTPS only, no
   cookies or session, `Accept: image/*`, at most 2 MB, two downloads at a time, started after
   the lookup; decoded with `inSampleSize` to at most 480 px wide; a memory cache (about 8 MB)
   and a disk cache in `cacheDir/thumbnails/` (at most 20 MB, oldest removed first).
2. YouTube: the picture of the video ID (`https://i.ytimg.com/vi/<id>/hqdefault.jpg`) may load
   before the lookup ends; other sites use the `thumbnailUrl` the lookup found.
3. Show it in the sheet header (16:9, the placeholder until it loads), the found list
   (`YftFoundMedia`) and running downloads. When a download starts, save a small JPEG (at most
   320 px wide, about 50 KB) as `filesDir/thumbnails/<downloadId>.jpg`; Downloads and the
   Library use it until the file's own frame exists, and it is deleted with the record. No Room
   migration.
4. Failures stay silent (the placeholder stays); logs name the host only, never the address.
5. Update the texts that say YFT never fetches remote images (the QuickDownloadScreen comment,
   DESIGN-NOTES §3, PROJECT_CONTEXT privacy, About if it says so).
6. New tag `quick-thumbnail`; every existing tag stays.

**Tests:** loader (HTTP refused, no `Cookie` header, the size cap stops the read, decode bound, a
cache hit makes no second request, two at a time); the header shows the loaded image (fails on
the old code: always the placeholder); the saved JPEG is used by Downloads and deleted with the
record.

**Live check:** one public YouTube, Facebook and Vimeo thumbnail — status, host, content type
and size only.

**Owner check:** YouTube and Facebook sheets show the picture; the found list and a running
download show it.

**Docs:** DESIGN-NOTES §3, PROJECT_CONTEXT (privacy), SUPPORT_MATRIX, TEST_MATRIX, CHANGELOG,
SESSION_STATE.

**Result:** —

**After P19 — Preview #2.** Run the full validation (§0.3), check that the Preview APK run of the
P19 commit is green, and send the owner its link with the §6 Preview #2 list (Burmese). Then
continue with P14 unless the owner says stop (§3 E7).

### P14 — YouTube asks visionOS first

Hard · 6–10 h · finding H5 · needs P10 · prompt
[`P14-youtube-visionos-first.md`](prompts/P14-youtube-visionos-first.md)

**Goal:** a YouTube lookup needs one small request (about 17 KB) instead of loading the 166 KB
watch page first, with the same rows and the same limits.

**Read first:** `extractor-sites/.../youtube/YouTubeExtractor.kt`, `YouTubeClientProfile.kt`,
`YouTubePlayerResponseParser.kt`, `YouTubeUrls.kt` and their tests; ADR-006 (Implementation,
steps 1–5); `docs/YOUTUBE_RISK_REVIEW.md`; `scripts/live-check.sh`.

**Steps**
1. Live check first (three public videos: 1080p, 4K and a Short): compare the `VISIONOS` answer
   alone with today's chain — heights, codecs, audio and sizes — plus a ranged GET of one stream
   (status and length only). Go on only when visionOS alone gives the same rows; otherwise write
   the gap in the Result and adapt (for example visionOS first, then one more client for the
   missing heights).
2. Ask `VISIONOS` first, without the user's cookie, the way step 2 of the chain asks it today.
3. Accept that answer without the watch page only when it is complete: `playabilityStatus` OK,
   title and length, no DRM, not live, at least one AVC video with an audio track, and every
   format with a direct address (no `signatureCipher`) and a `contentLength`.
4. Any other answer (login or age check, unplayable, error, incomplete) runs today's chain
   unchanged: the watch page first, and the page's own verdict ends the lookup. ADR-006's limits
   stay: no client unlocks what the page refused; private, age-restricted and DRM videos stay
   refused.
5. Update ADR-006's Implementation section (the new step order) and YOUTUBE_RISK_REVIEW.

**Tests:** a sanitized visionOS fixture → success with one request and no watch page (fails on
the old code: the watch page comes first); non-OK answers (login, age, unplayable) → the watch
page chain; an age check never leads to another client's answer; a missing `contentLength` or a
`signatureCipher` → the chain; DRM → refused.

**Live check:** step 1, then the final run: status, host, path, number of requests and bytes,
heights.

**Owner check:** on the slow line a YouTube link opens clearly faster; 720p, 1080p and 4K
download and play.

**Docs:** ADR-006, YOUTUBE_RISK_REVIEW, SUPPORT_MATRIX (YouTube), TEST_MATRIX, CHANGELOG,
SESSION_STATE.

**Result:** —

### P15 — Facebook public page first

Medium–Hard · 4–7 h · finding H4 · needs P10, P11 · prompt
[`P15-facebook-public-first.md`](prompts/P15-facebook-public-first.md)

**Goal:** a public Facebook video needs one page request instead of two.

**Read first:** `extractor-sites/.../facebook/FacebookExtractor.kt`, `FacebookPageIdentity.kt`,
`FacebookPageParser.kt`, `FacebookDashOffers.kt`, `FacebookUrls.kt` and their tests;
`app/.../detection/SiteAdapterCoordinator.kt` (the session the adapter gets).

**Steps**
1. Live check first: the Safari page without a session for two public reels, a `/watch` video
   and a `share/r/` link (status, final host and path, video ID match, track heights).
2. Ask first as Safari (`FacebookPageIdentity.AVC_LADDER_USER_AGENT`) without cookies.
3. When that page has the video (the same video ID) with AVC tracks or `browser_native_sd/hd`,
   the lookup ends there: one request.
4. Otherwise (login wall, no video of that ID, a share link that did not resolve) run today's
   path: the page with the user's session as the Chrome desktop identity, then the AVC ladder
   only when needed.
5. The session never travels with the Safari identity (as today).

**Tests:** a public reel fixture → one request without `Cookie` (fails on the old code: two
requests, the first with the session); a video only the session sees → the session request; a
share link; a login wall.

**Live check:** step 1 again after the change, with the number of requests.

**Owner check:** a Facebook reel on the slow line opens faster, with every quality.

**Docs:** SUPPORT_MATRIX (Facebook), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P16 — Sheet opens at once

Hard · 8–12 h · finding H7 · needs P9, P11, P12 · prompt
[`P16-instant-sheet.md`](prompts/P16-instant-sheet.md)

**Goal:** as in Snaptube, the sheet opens the moment the user asks (Home, the browser's buttons,
a feed) with what is already known, says "Getting qualities…" and fills in when the lookup
answers.

**Read first:** `QuickDownloadViewModel.kt`, `QuickDownloadScreen.kt` (+ tests);
`app/.../feature/home/HomeViewModel.kt`, `LinkInspector.kt`, `HomeScreen.kt` (+ tests);
`BrowserViewModel.kt`, `BrowserScreen.kt` (+ tests); `app/.../ui/navigation/YftNavHost.kt`.

**Steps**
1. The sheet gets a waiting state: the header from what is known (page title or the link, site,
   YouTube's picture of the video ID), two Audio and two Video placeholder rows and "Getting
   qualities…".
2. Home: a link of a supported site opens the sheet at once and runs the lookup in it; other
   links keep today's check and open the sheet as soon as it answers.
3. Browser: the round and wide buttons (P12, P13) and a feed's focused link open the sheet at
   once; the lookup fills it.
4. Errors show in the sheet with Retry (P10); closing the sheet stops its lookup.
5. Keep `quick-loading`, `quick-error`, `quick-retry` and every other tag.

**Tests:** ViewModel states (open → waiting header → rows; error → Retry; close cancels); Home
opens the sheet before the lookup ends (fails on the old code); the feed path; no second lookup
(P12).

**Owner check:** paste a YouTube link → the sheet opens at once with the link and "Getting
qualities…", then the rows; the same for a Facebook reel in the browser.

**Docs:** DESIGN-NOTES (sheet), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P17 — Reuse lookup results

Medium · 4–6 h · needs P12, P16 · prompt [`P17-reuse-lookups.md`](prompts/P17-reuse-lookups.md)

**Goal:** the same video is not looked up again within minutes: Home, the browser and a reopened
sheet share one result.

**Read first:** `app/.../detection/SiteAdapterCoordinator.kt`, `SiteAdapterModule.kt` (+ tests);
`LinkInspector.kt`; `BrowserViewModel.kt`; `QuickDownloadViewModel.kt`;
`core-model/.../download/DownloadModels.kt` (`expiresAtEpochMs`).

**Steps**
1. A memory-only lookup cache in the coordinator: key = site + content ID + whether the user's
   session was used; at most 20 entries, each kept until the earliest known link expiry or 10
   minutes, whichever comes first.
2. A second lookup of the same key while the first runs waits for it (one network lookup).
3. Retry, a definite failure and a download that gets HTTP 403 or 410 drop the entry; Retry
   always asks the network.
4. Nothing is written to disk; cache keys and addresses never reach logs.

**Tests:** two lookups → one adapter call (fails on the old code); the expiry and the 10-minute
limit; a shared running lookup; session and no-session entries stay apart; Retry skips the
cache; a 403 drops the entry.

**Owner check:** open a YouTube video's sheet from Home, close it, open the same video in the
browser → Download → the qualities are there at once.

**Docs:** ARCHITECTURE (lookup cache), TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

### P18 — Download before qualities arrive

Medium–Hard · 4–7 h · needs P16 · prompt [`P18-download-early.md`](prompts/P18-download-early.md)

**Goal:** Download can be tapped while the sheet still says "Getting qualities…"; the download
starts as soon as the lookup answers.

**Read first:** `QuickDownloadViewModel.kt`, `QuickDownloadScreen.kt`, `QuickDownloadChoices.kt`
(+ tests); `core-model/.../settings/DownloadPreferences.kt`; the sheet's download start path
(`core-download` plan factory).

**Steps**
1. While the sheet waits, Download is enabled for the default choice (720p, or the user's Default
   quality; Audio when the user picked an Audio placeholder). A tap queues the choice and shows
   "Starts when ready…".
2. When the rows arrive, take the chosen quality, else the nearest lower one, else the nearest
   higher one; start the download and close the sheet as today, saying which quality was taken
   ("Downloading 480p — 720p not available").
3. A failed lookup drops the queued choice and shows the error with Retry; closing the sheet
   cancels it.
4. Every choice still passes today's checks (storage, Wi-Fi only, the mobile-data prompt).

**Tests:** queued, then started with the exact, the lower and the higher pick; failure; closing
cancels; the checks still apply (fails on the old code: Download is disabled while waiting).

**Owner check:** paste a link and tap Download at once → the download starts with the right
quality.

**Docs:** TEST_MATRIX, CHANGELOG, SESSION_STATE.

**Result:** —

**After P18 — Preview #3.** Full validation, a green Preview APK run of the P18 commit, its link
and the §6 Preview #3 list to the owner; then wait for his phone test. P8 only with his OK.

### P8 — Signed release 1.0.0-beta.4

Easy · 1–2 h · needs P9–P19, Preview #3 and the owner's OK · prompt
[`P8-signed-beta4.md`](prompts/P8-signed-beta4.md)

`yft.versionName=1.0.0-beta.4`, `yft.versionCode=4`, `docs/release/1.0.0-beta.4.md`, CHANGELOG
section; full validation; merge into `main` (fast-forward), tag `v1.0.0-beta.4`;
`release-draft.yml` builds the APK signed with the release key; record size, SHA-256 and
certificate. Merge, tag and signing need the owner's OK for this task (`docs/RELEASE.md`).

## 6. Owner phone checklist

Install `yft-preview-apk` from the Preview APK run the agent sends; uninstall the older YFT
Preview first (each run has a new test key).

**Preview #2 (after P19)**

1. Slow line: paste a YouTube link in Home → "Slow connection — still looking…" → the sheet
   opens; the browser shows no "could not be reached".
2. Sheet: two Audio rows (M4A, MP3 128) and two Video rows (720p selected, 480p); Download
   visible without scrolling; More formats shows every row up to 4K.
3. Facebook reel: every quality at once; sizes fill in; nothing disappears.
4. YouTube watch page and Facebook reel in the browser: the wide Download button → that video's
   sheet; never "Found on this page 4".
5. Real thumbnails in the sheet, the found list and a running download.
6. Part 1 still works: feeds (the video on screen), 1080p with sound, 2K/4K, MP3.
7. About › Last crash report: none.

**Preview #3 (after P18)**

1. YouTube and Facebook lookups are faster on the slow line (P14, P15).
2. The sheet opens at once with "Getting qualities…" and fills in (P16).
3. The same video again (Home, then the browser) → the qualities at once (P17).
4. Download tapped before the qualities arrive → it starts with the right quality (P18).
5. The Preview #2 list again.

## 7. Backlog

Advice on the four items the owner asked about (2026-10-05); each waits for his decision:

- **B1 — Download button inside YouTube's page** (under the video, beside Like and Share).
  Advice: not now. P13's wide button gives the same tap without touching YouTube's page. Later,
  if the owner still wants it: a page script adds a button to YouTube's action row and talks to
  the app only through an origin-restricted `WebMessageListener` (`https://m.youtube.com`); when
  YouTube changes its page and the spot is gone, P13's button stays. About 4–6 h plus upkeep.
  Decide after Preview #2. Owner decision: —
- **B2 — a YouTube page of YFT's own, like Snaptube's** (own player, search and comments).
  Advice: no. It means rebuilding YouTube's player, search and comments on YouTube's internal
  interface, which changes often: weeks of work and frequent breakage. P13 + P16 + P19 give the
  Snaptube-like download experience. Owner decision: —
- **B3 — Facebook formats from the page in YFT's browser.** Advice: only if needed. After P15,
  measure on the phone; if a Facebook lookup still takes more than 5 s on the slow line, a spike
  reads only the format markers from the page the browser already loaded → parser + fixtures.
  About 6–10 h. Owner decision: —
- **B4 — TikTok on the owner's phone.** Advice: no phone test (TikTok is banned in India);
  every TikTok change keeps fixtures, the CI emulator and sandbox live checks; no VPN. Owner
  decision: —

Other items:

- Share target: open links shared from other apps (Android share sheet) in Home's lookup, so
  they open the download sheet like a pasted link (P3 found no share target in the manifest).
- AV1 merges: off since P4 (the API 34 emulator's muxer failed), so Facebook's AV1-only sizes and
  YouTube's AV1-only 2K/4K stay hidden until an AV1 merge is proven on a phone.
- YouTube: use the page player's own proof-of-origin token from the browser (ADR-006, not done).
- Instagram and X adapters (generic detection only today).
- Background playback in the Library.
- Saving to a folder chosen with the system picker.

The former "Download sheet thumbnails" item is P19.

## 8. Done before part 2

**Phase 11 part 1** (P0–P7, 2026-10-04 to 2026-10-05) is on this branch. The owner tested Preview
APK #1 (`1.0.0-beta.3-preview.1`, Preview APK run
https://github.com/Alalkipgen/YFT/actions/runs/37294511016) on 2026-10-05; what he found is part
2. Steps, Results and the findings G1–G7: `git show ce3cd25:docs/FIX_ADD_PLAN.md`; prompts:
`git show ce3cd25:docs/prompts/`.

| ID | Task | Commits | Status |
| --- | --- | --- | --- |
| P0 | Plan, prompts and docs | `99efca6` | DONE (2026-10-04) |
| P1 | Browser follows in-page navigation (YouTube Download button, address bar) | `9afd965` | DONE (2026-10-05) |
| P2 | Facebook and TikTok pages render and play in the browser | `8421700` | DONE (2026-10-05) |
| P3 | One download sheet: Audio (M4A, MP3) and Video; P3-FIX two sections, Facebook posts | `56f0c79`, `aad59b3` | DONE (2026-10-05) |
| P4 | Facebook: every DASH quality merged with its sound | `c786929` | DONE (2026-10-05) |
| P5 | Download button on feeds (the video on screen) | `737724b` | DONE (2026-10-05) |
| P6 | YouTube 2K and 4K (VP9 + Opus `.webm`) | `a1e99fd`, `04ef622`, `4d81acf` | DONE (2026-10-05) |
| P7 | Preview APK with a CI test key | `dacecd8`, `ce3cd25` | DONE (2026-10-05) |
| P8 | Signed `1.0.0-beta.4` | — | moved behind part 2 ([§1](#1-status-board)) |

**Phases 8–10** (T01–T19, 2026-10-03 to 2026-10-04) are complete and released as
`1.0.0-beta.3`: browser crash and start page, CI emulator smoke test, crash report and Copy
details, Home lookup identity, Facebook public videos, TikTok media cookies, YouTube messages and
client strategy (ADR-006), Your sites logos, copied-link check, "Video you copied", Search to
download, the browser Download button, merged 480p–1080p YouTube video, MP3. T10 and T15 were
skipped by the owner. Full plan: `git show 2f6284f:docs/FIX_PLAN.md`; prompts:
`git show 2f6284f:docs/prompts/`.

**T19 — release record (DONE, 2026-10-04):** `main` fast-forwarded to `2f6284f`; tag
`v1.0.0-beta.3`; CI green (checkpoint
https://github.com/Alalkipgen/YFT/actions/runs/37203674358, emulator
https://github.com/Alalkipgen/YFT/actions/runs/37203631028); Release draft
https://github.com/Alalkipgen/YFT/actions/runs/37204457527 passed and created the draft
pre-release: `video-downloader-1.0.0-beta.3.apk`, 6,334,176 bytes, SHA-256
`8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
`3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
(same key as beta.1 and beta.2), source commit `2f6284f`.
