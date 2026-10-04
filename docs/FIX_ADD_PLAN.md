# YFT Fix & Add Plan — Phase 11 (Snaptube-style download flow)

Owner phone test of `1.0.0-beta.3` (2026-10-04), compared with Snaptube on the same videos. The
owner approved this plan on 2026-10-04. Prompts for every task: [`prompts/`](prompts/README.md).
The finished Phase 8–10 plan (T01–T19) was removed from this folder; it stays in Git history:
`git show 2f6284f:docs/FIX_PLAN.md` (summary in [§8](#8-done-before-phase-11)).

## Contents

0. [How to work](#0-how-to-work)
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Tasks](#5-tasks)
6. [Owner phone checklist](#6-owner-phone-checklist)
7. [Backlog](#7-backlog)
8. [Done before Phase 11](#8-done-before-phase-11)

## 0. How to work

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out `work/phase-11-download-flow` and pull it (created from `main` at `2f6284f`). Every
   Phase 11 task stays on this branch; nothing is merged into `main` before P8.
3. Read §0, §3, the task's section in §5, the findings it links, its **Read first** files and
   `docs/SESSION_STATE.md`.
4. Run the task's validation before editing, so you know the starting state.
5. Set the task to `IN PROGRESS` in the [status board](#1-status-board).
6. Do the **Steps** in order. Stay inside the task; anything else goes to [§7](#7-backlog).
7. Add the listed tests. A regression test must fail on the old code.
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists and `CHANGELOG.md` (`## [Unreleased]`), set the task to
   `DONE (date)` or `OWNER CHECK`, update `docs/SESSION_STATE.md`, and checkpoint with
   `scripts/checkpoint.sh "Px: summary"`. Check CI for the pushed commit and fix a red run.
10. Report to the owner in Burmese (§0.5), then continue with the next task in the order of §3
    without waiting (owner instruction, 2026-10-04). Stop only for a failure you cannot fix.

### 0.2 Rules for every task

- Product rules ([ADR-006](decisions/ADR-006-owner-override-any-working-method.md), owner
  2026-10-03): any working technique for public videos; no DRM, paywall, private-content or
  age-gate bypass; adapters never sign in.
- Never log, print, commit or put into fixtures: cookies, tokens, `Authorization` values, signed
  media URLs (CDN query strings), keystores, `local.properties` or `.env`. Fixtures keep hosts
  and paths and replace signed query values with `REDACTED`.
- Kotlin lines stay within 100 characters. Keep every existing `testTag`. No unrelated refactors.
- `WebView` and `WebSettings` methods run on the main thread only; `shouldInterceptRequest` and
  `@JavascriptInterface` methods run on other threads.
- Site tasks need a live check of a public page (`scripts/live-check.sh`); report status, host,
  path and markers only. TikTok is banned in India, so the owner cannot check it on his phone:
  TikTok work is verified with fixtures, the CI emulator and sandbox live checks.
- The agent sandbox has no emulator; use the CI emulator job (`emulator-smoke.yml`) for real
  WebView and MediaCodec/MediaMuxer checks.
- Keep temporary files outside the repository: `scripts/checkpoint.sh` stages with `git add -A`.

### 0.3 Validation

Environment: JDK 17 and Android SDK 35 with NDK `27.3.13750724` and CMake `3.22.1` (MP3). In the
Notion sandbox run `source /data/yft-env.sh` first; stop stale daemons with
`pkill -f "[G]radleDaemon"`. On a 4 GiB machine run Gradle tasks as separate calls.

| Scope | Command |
| --- | --- |
| Quick (checkpoint default) | `./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug` |
| Modules (add the ones you touched) | `:core-browser:testDebugUnitTest` `:core-download:testDebugUnitTest` `:core-media:testDebugUnitTest` `:core-data:testDebugUnitTest` `:core-model:test` `:extractor-api:test` `:extractor-generic:test` `:extractor-sites:test` |
| Full (P7, P8) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug`, then `./gradlew --no-daemon :app:assembleRelease` |
| Line length (must print nothing) | `git diff -U0 origin/main -- '*.kt' '*.kts' \| grep '^+[^+]' \| awk 'length > 101'` |

CI status for the branch:

```bash
curl -s "https://api.github.com/repos/Alalkipgen/YFT/actions/runs?branch=work/phase-11-download-flow&per_page=4" \
  | jq -c '.workflow_runs[] | {name, sha: .head_sha[0:7], status, conclusion}'
```

### 0.4 Status values

`TODO` · `IN PROGRESS` · `BLOCKED (reason)` · `OWNER CHECK` (done on the branch, waiting for the
owner's phone) · `DONE (date)` · `SKIPPED (decision)`. Checkpoint messages start with the task
ID, so `git log --oneline --grep "P1:"` finds a task's commits.

### 0.5 Report to the owner (Burmese, short)

```text
Task: Px — <title> — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run link> (debug APK: Artifacts › yft-debug-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: …
နောက်တစ်ဆင့်: Py
```

### 0.6 Phone builds for the owner

Every green checkpoint run uploads `yft-debug-apk` (14 days; app ID `com.alal.yft.debug`, installs
next to the beta). From P7 on, CI also uploads `yft-preview-apk`: a minified release build signed
with a CI test key, app ID `com.alal.yft.preview`. An APK with no signature at all cannot be
installed on Android, so the owner's "unsigned release APK" is this preview build. Only P8 signs
with the release key.

## 1. Status board

AI agent time includes builds and CI waits on a 4 GiB sandbox.

| ID | Task | Level | AI agent time | Needs | Status |
| --- | --- | --- | --- | --- | --- |
| P0 | [Plan, prompts and docs for Phase 11](#p0--plan-prompts-and-docs) | Easy | 1–2 h | owner approval | DONE (2026-10-04) |
| P1 | [Browser follows in-page navigation (YouTube Download button)](#p1--browser-follows-in-page-navigation) | Medium | 3–5 h | — | OWNER CHECK (2026-10-04) |
| P2 | [Facebook/TikTok black page in the browser](#p2--facebook-and-tiktok-black-page-in-the-browser) | Medium–Hard | 4–8 h | P1 | TODO |
| P3 | [One download sheet, Snaptube style](#p3--one-download-sheet-snaptube-style) | Hard | 10–14 h | P1 | TODO |
| P4 | [Facebook: one video, every quality](#p4--facebook-one-video-every-quality) | Medium–Hard | 5–8 h | P3 | TODO |
| P5 | [Download button on feeds (focused video)](#p5--download-button-on-feeds) | Hard | 6–10 h | P1, P3 | TODO |
| P6 | [2K and 4K](#p6--2k-and-4k) | Hard | 8–12 h | P3 | TODO |
| P7 | [Preview APK for the owner's test](#p7--preview-apk) | Easy | 1–2 h | P1–P6 | TODO |
| P8 | [Signed release 1.0.0-beta.4](#p8--signed-release-100-beta4) | Easy | 1–2 h | P7, owner OK | TODO |

Total about 40–65 h of agent time.

## 2. What the owner saw

Phone test of `1.0.0-beta.3`, 2026-10-04 (screenshots in the owner's chat; Snaptube for
comparison):

1. **Facebook page is black.** A `m.facebook.com/share/…` link opened in YFT's browser shows
   only black; no video, no Download button. Snaptube shows the reel playing and a Download
   button on the same kind of page. TikTok could not be tried (banned in India).
2. **Facebook gives 3 separate items.** "Found on this page" lists the same title three times
   (MP4, MP4, DASH "Auto quality"). Download as shows the title instead of a quality, the Audio
   tab cannot be tapped. "Video you copied" works, but More formats opens the 3–5 item list.
   Snaptube shows one sheet: Music (Fast, Classic MP3) and Video (Fast 480p, High 720p) with
   sizes, and More formats inside the same sheet.
3. **YouTube: no Download button in the browser.** The video plays in YFT's browser, but no
   Download button appears, and the address bar shows only `m.youtube.com`. Snaptube shows the
   button on the feed (for the video in focus) and on the watch page.
4. **View opens a file list.** Home "N media found · View" should open the same sheet with Audio
   and Video formats and qualities, not a list of files.
5. **Only up to 1080p.** When the source has 2K or 4K, YFT should offer them.

## 3. Owner decisions

| ID | Decision | Answer |
| --- | --- | --- |
| E1 | Plan P0–P8 approved | **YES** (owner, 2026-10-04) |
| E2 | Order | P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → owner test → P8, task after task without stopping or asking (owner, 2026-10-04) |
| E3 | Test build | After the fixes, a release build without the release key (P7, test-key preview APK); the signed release only once the owner finds it stable (P8) |
| E4 | 2K/4K container | `.webm` (VP9 + Opus), because YouTube serves 2K/4K only as VP9/AV1; AV1 only on Android 14+ (agent default, stated in the approved plan) |
| E5 | Prompts | One prompt per task plus the generic master prompt in `docs/prompts/` |

## 4. Findings and root causes

Checked in the code at `2f6284f` (2026-10-04).

- **G1 — in-page navigation is ignored (item 3).** `SecureBrowserWebViewClient.doUpdateVisitedHistory`
  only updates the internal page URL used for request observations; `BrowserObservationSink`
  has no URL-change callback. `BrowserViewModel` runs the site adapters only from
  `onPageFinished`, and the address pill updates only on `onPageStarted`. `m.youtube.com`
  switches videos with `history.pushState`, so the watch page never gets a lookup, the page has
  no savable candidates and `BrowserDownloadFab.isVisible` stays false.
- **G2 — Facebook results are three items (item 2).** `FacebookExtractor` returns the HD and SD
  progressive MP4s and the page's DASH manifest as separate candidates. The MP4 variants carry
  no height, so `MediaVariant.qualityLabel()` (`PreviewScreen.kt`) falls back to the variant
  label, which is the page title. The Audio tab lists only `MediaTrackType.AUDIO` variants
  (`PreviewUiState.kt`), and only the DASH item has one.
- **G3 — Facebook DASH cannot download.** `DashDownloadManifestParser` returns `Unsupported` for
  `SegmentBase`; Facebook's representations are whole files at their `BaseURL` with a
  `SegmentBase` index, so they can be downloaded as single files without parsing the index.
- **G4 — More formats and View open the item list (items 2, 4).** `QuickDownloadChoices` hands
  every savable candidate to More formats, which opens the detected-media list; Home's View
  opens the same list.
- **G5 — black Facebook page (item 1), not reproduced yet.** Suspects: the default WebView
  user agent (`; wv`) gets an app-redirect page; `fb://`/`intent://` app-open navigations are
  blocked by `shouldOverrideUrlLoading` and leave a blank page; `mediaPlaybackRequiresUserGesture`
  keeps the reel unloaded; `SecureBrowserChromeClient` has no `onShowCustomView` (full screen).
- **G6 — 1080p cap (item 5).** YouTube merged rows are `MERGED_QUALITIES = {480, 720, 1080}` and
  AVC only (`YouTubeExtractor`); `AudioVideoMuxEngine` writes MPEG-4 only. YouTube's 1440p and
  2160p exist only as VP9 or AV1.

## 5. Tasks

### P0 — Plan, prompts and docs

Easy · 1–2 h · **DONE (2026-10-04)**. This file, `docs/prompts/` (README, master prompt, next-task
prompt, P1–P8 prompts), the README status, `AGENTS.md`, HANDOFF, SESSION_STATE, PHASE_STATUS and
the T19 release record. The Phase 8–10 plan and T01–T19 prompts were removed (Git history).

### P1 — Browser follows in-page navigation

Medium · 3–5 h · finding G1 · prompt [`P1-browser-spa-navigation.md`](prompts/P1-browser-spa-navigation.md)

**Read first:** `core-browser/.../webview/SecureBrowserWebViewClient.kt`, `BrowserObservationSink.kt`,
`BrowserPageUrl`, `app/.../feature/browser/BrowserViewModel.kt`, `BrowserScreen.kt`,
`BrowserDownloadFab.kt`, `core-browser/.../session/PageCandidateStore.kt`,
`app/.../detection/SiteAdapterCoordinator.kt`, `extractor-sites/.../youtube/YouTubeUrls.kt`.

**Steps**
1. Add `onUrlChanged(url)` to `BrowserObservationSink`; call it from `doUpdateVisitedHistory`
   (main thread) when the URL differs from the current page URL, ignoring fragment-only changes.
2. In `BrowserViewModel`, treat a changed URL as a new page: update the address, start a new
   candidate scope for that URL, cancel the old page's probes, reset the auto-retry state and run
   the site adapters for the new URL (debounced, about 500 ms, so quick redirects run once).
3. YouTube `/watch`, `/shorts/` and `youtu.be` URLs and Facebook/TikTok video URLs get their
   lookup at once; other in-page changes keep generic detection only.
4. The Download button appears as soon as the adapter returns savable media for the new URL and
   disappears when the user leaves that video.

**Tests:** client test (history update → `onUrlChanged`, fragment change → nothing); ViewModel
tests (feed → watch A → watch B: address, scope, adapter calls, stale results of A dropped);
FAB visibility after an in-page change; the old code fails them.

**Owner check:** m.youtube.com → tap a video → the address shows `/watch` and the Download button
appears → it opens the sheet.

**Result (2026-10-04, OWNER CHECK):** the client reports every address change it did not load
(fragments too, because requests carry the new address); the ViewModel keeps the same page
(fragment, or the same post with another parameter via `SiteAdapterCoordinator.sameContent`) and
starts a new scope for another video: candidates, notice and retry reset, old lookups cancelled
and ignored by page generation, the site's cookie context carried over, adapters after 500 ms of
a stable address, DOM probe after 1.5 s. Tests: core-browser 57, app browser/detection suites 105
(TEST_MATRIX P1).

**Docs:** SUPPORT_MATRIX (browser row), TEST_MATRIX, CHANGELOG, SESSION_STATE, this board.

### P2 — Facebook and TikTok black page in the browser

Medium–Hard · 4–8 h · finding G5 · prompt [`P2-facebook-black-page.md`](prompts/P2-facebook-black-page.md)

**Read first:** `core-browser/.../policy/SecureWebViewPolicy.kt`, `SecureBrowserWebViewClient.kt`,
`SecureBrowserChromeClient.kt`, `BrowserScreen.kt` (`BrowserWebView`), `scripts/ci-emulator-smoke.sh`,
`.github/workflows/emulator-smoke.yml`, the browser tests under `app/src/androidTest`.

**Steps**
1. Reproduce on the CI emulator (real WebView, API 34): open a public Facebook reel, a
   `facebook.com/share/r/…` link and a public TikTok video; capture screenshots, page title,
   main-frame URL chain and redacted console errors.
2. Try the suspects one at a time and keep the smallest change that renders the page and plays
   the video: a Chrome-like user agent without `; wv` for the browser only; app-open
   navigations (`fb://`, `intent://`, `market://`) dropped silently instead of leaving a blank
   page (an `intent://` with `browser_fallback_url` loads that HTTPS URL); media playback
   after the user's tap; `onShowCustomView`/`onHideCustomView` for full-screen video.
3. Keep HTTPS-only, no third-party cookies unless the evidence needs them (record the reason).
4. Make the Download button appear on these pages through the site adapters (P1 path), even
   while the page is still loading.

**Tests:** policy/unit tests for the new rules (UA, app-open handling, full-screen callbacks);
emulator test that the Facebook and TikTok pages are not blank (pixel check) and play.

**Owner check:** paste a Facebook share link → Open in browser → the reel shows and plays,
full screen works, the Download button appears.

**Docs:** SUPPORT_MATRIX (browser, Facebook, TikTok), TEST_MATRIX, CHANGELOG, SESSION_STATE.

### P3 — One download sheet, Snaptube style

Hard · 10–14 h · findings G2, G4 · prompt [`P3-one-download-sheet.md`](prompts/P3-one-download-sheet.md)

**Read first:** `app/.../feature/quickdownload/*`, `feature/preview/*`, `feature/detectedmedia/*`,
`feature/home/HomeScreen.kt`, `HomeViewModel.kt`, `ui/navigation/YftNavHost.kt`,
`core-model/.../media/MediaAsset.kt`, the MP3 choices (`Mp3Variants`).

**Steps**
1. Group a page's candidates of the same video (same site video ID or same source page and
   duration) into one item with all its variants; the found list shows one row per video.
2. One sheet (the "Video you copied" sheet grows into it): thumbnail, title, source; **Music**
   rows (M4A with bitrate and size; MP3 128/192/320 with estimated size); **Video** rows
   (Fast ≈ 480p and High ≈ 720p) with resolution, frame rate and size; **More formats** expands
   inside the sheet with every video quality and audio option (chips: "Video + audio",
   "Slow" for conversions); one Download button with the size.
3. Every row shows a quality: when a video variant has no height, probe it (MP4 `tkhd` or the
   DASH manifest) before showing it; never use the page title as a quality label.
4. Audio is always offered when the video has sound: from an audio-only variant, otherwise by
   extracting the AAC track of the downloaded MP4 into M4A (and MP3 from it).
5. Home "View", the found list's Preview, the browser Download button and shared links open this
   sheet. The old Download as screen stays reachable only from More formats › Details.

**Tests:** grouping (Facebook three items → one), labels (no title fallback), audio offered for an
MP4-only video, More formats inside the sheet, navigation from View/Preview/FAB, existing quick
sheet and preview tests updated.

**Owner check:** Facebook and YouTube: View or Download → one sheet with Music/Video rows, real
resolutions and sizes; More formats opens inside it; Audio works.

**Docs:** SUPPORT_MATRIX, TEST_MATRIX, design notes, CHANGELOG, SESSION_STATE.

### P4 — Facebook: one video, every quality

Medium–Hard · 5–8 h · findings G2, G3 · prompt [`P4-facebook-all-qualities.md`](prompts/P4-facebook-all-qualities.md)

**Read first:** `extractor-sites/.../facebook/*`, `core-media/.../resolver/DashManifestParser.kt`,
`core-download/.../DashDownloadManifestParser.kt`, `DashTransferEngine.kt`,
`AudioVideoMuxEngine.kt`, the T17 merge path.

**Steps**
1. Read the Facebook DASH representations as whole files (`BaseURL`; `SegmentBase` index not
   needed) with height, width, frame rate, codecs and bandwidth.
2. Offer every video height the page has (360p … 1080p and higher when present) merged with the
   AAC audio representation (AVC + AAC → MP4, existing muxer); keep the progressive SD/HD MP4s
   as "Video + audio" rows with their probed height.
3. Music rows from the audio representation (M4A, MP3).
4. Sizes: `Content-Length` from a ranged request, else bandwidth × duration (estimated).

**Tests:** parser tests on a sanitized Facebook DASH fixture (heights, audio, whole-file URLs),
plan tests (merge choice), live check of a public reel (status, heights, markers only).

**Owner check:** Facebook link → sheet shows 360p/720p/1080p (when the video has them) and
Music → each downloads and plays with sound.

**Docs:** SUPPORT_MATRIX (Facebook), TEST_MATRIX, CHANGELOG, SESSION_STATE.

### P5 — Download button on feeds

Hard · 6–10 h · needs P1, P3 · prompt [`P5-feed-focused-video.md`](prompts/P5-feed-focused-video.md)

**Read first:** `core-browser/.../detection/DomMediaProbe.kt`, `BrowserViewModel.kt`,
`BrowserDownloadFab.kt`, `extractor-sites/.../youtube/YouTubeUrls.kt`, `facebook/FacebookUrls.kt`,
the TikTok URL rules.

**Steps**
1. On YouTube, Facebook and TikTok pages show the Download button even without a found file.
2. On tap, a main-thread script finds the video in focus: the playing `<video>` or the item
   closest to the viewport centre, then its link (`/watch?v=`, `/shorts/`, `/reel/`, `/video/`).
3. Look that link up with the site adapter and open the P3 sheet; a page with no video in focus
   shows a short message instead.

**Tests:** script result parsing (fixtures of feed markup), URL extraction per site, ViewModel
flow (tap → lookup → sheet), no script on other sites.

**Owner check:** YouTube home feed → scroll to a video → Download → the sheet is for that video.

**Docs:** SUPPORT_MATRIX, TEST_MATRIX, CHANGELOG, SESSION_STATE.

### P6 — 2K and 4K

Hard · 8–12 h · finding G6 · prompt [`P6-2k-4k-webm.md`](prompts/P6-2k-4k-webm.md)

**Read first:** `extractor-sites/.../youtube/YouTubeExtractor.kt` (`MERGED_QUALITIES`, AVC filter),
`core-download/.../AudioVideoMuxEngine.kt`, the merge dispatcher, `Mp3Variants` (audio choices).

**Steps**
1. Offer 1440p (2K) and 2160p (4K) when YouTube has them: VP9 video-only WebM with the Opus audio
   stream, merged with `MediaMuxer` WebM output (Android 7+) into `.webm`.
2. AV1-only qualities only on Android 14+ (MP4 output), otherwise not offered.
3. Before offering, check `MediaCodecList` for a decoder of that size; when missing, still offer
   it with "may not play on this phone".
4. Facebook heights above 1080p use the P4 AVC path.

**Tests:** format selection (VP9 + Opus pairing, AV1 gating by API level), mux plan for WebM,
instrumentation test on the CI emulator merging a short VP9 + Opus fixture into WebM.

**Owner check:** a YouTube 4K video → 2K/4K rows → download → plays (or shows the warning).

**Docs:** SUPPORT_MATRIX (YouTube), TEST_MATRIX, CHANGELOG, SESSION_STATE.

### P7 — Preview APK

Easy · 1–2 h · prompt [`P7-preview-apk.md`](prompts/P7-preview-apk.md)

**Steps**
1. Add a `preview` build type: `initWith(release)`, minified, not debuggable, app ID suffix
   `.preview`, signed with a test key generated in CI (never the release key, never committed).
2. CI uploads `yft-preview-apk` with its SHA-256; `verify-release-apk.sh` checks it.
3. Full validation; report the artifact link and the phone checklist (§6).

**Owner check:** install `yft-preview-apk` next to beta.3 and run §6.

### P8 — Signed release 1.0.0-beta.4

Easy · 1–2 h · needs the owner's OK after the P7 test · prompt [`P8-signed-beta4.md`](prompts/P8-signed-beta4.md)

`yft.versionName=1.0.0-beta.4`, `yft.versionCode=4`, `docs/release/1.0.0-beta.4.md`, CHANGELOG
section; full validation; merge into `main` (fast-forward), tag `v1.0.0-beta.4`; `release-draft.yml`
builds the APK signed with the release key; record size, SHA-256 and certificate. Merge, tag and
signing need the owner's OK for this task (`docs/RELEASE.md`).

## 6. Owner phone checklist

With `yft-preview-apk` (P7):

1. YouTube in the browser: tap a video → the address shows `/watch`, the Download button appears.
2. YouTube feed: Download on a video in focus → the sheet is for that video.
3. Facebook share link in the browser → the reel plays; full screen; Download button.
4. Facebook and YouTube: Home View / Download → one sheet with Music and Video rows, real
   resolutions and sizes; More formats inside the sheet; Audio (M4A, MP3) works.
5. A Facebook 1080p row and a YouTube 1080p row download and play with sound.
6. A YouTube 2K/4K row downloads and plays (or says it may not play on this phone).
7. About › Last crash report: none.

## 7. Backlog

- Instagram and X adapters (generic detection only today).
- Background playback in the Library.
- Saving to a folder chosen with the system picker.

## 8. Done before Phase 11

Phases 8–10 (T01–T19, 2026-10-03 to 2026-10-04) are complete and released as `1.0.0-beta.3`:
browser crash and start page, CI emulator smoke test, crash report and Copy details, Home lookup
identity, Facebook public videos, TikTok media cookies, YouTube messages and client strategy
(ADR-006), Your sites logos, copied-link check, "Video you copied", Search to download, the
browser Download button, merged 480p–1080p YouTube video, MP3. T10 and T15 were skipped by the
owner. Full plan: `git show 2f6284f:docs/FIX_PLAN.md`; prompts: `git show 2f6284f:docs/prompts/`.

**T19 — release record (DONE, 2026-10-04):** `main` fast-forwarded to `2f6284f`; tag
`v1.0.0-beta.3`; CI green (checkpoint
https://github.com/Alalkipgen/YFT/actions/runs/37203674358, emulator
https://github.com/Alalkipgen/YFT/actions/runs/37203631028); Release draft
https://github.com/Alalkipgen/YFT/actions/runs/37204457527 passed and created the draft
pre-release: `video-downloader-1.0.0-beta.3.apk`, 6,334,176 bytes, SHA-256
`8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
`3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
(same key as beta.1 and beta.2), source commit `2f6284f`.
