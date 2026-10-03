# YFT Fix Plan — Phases 8–10

The owner's phone test of `1.0.0-beta.2` (2026-10-03) found blocking problems, and the owner asked
for a SnapTube-style "copy a link, open YFT, pick a quality" flow. This file is the single source
of truth for that work. It is written so that any coding agent can take **one task**, finish it,
check it in and hand over.

- Plan date: 2026-10-03. Base: `main` at `28930cf` (1.0.0-beta.2 plus its release record).
- Prompts for every task: [`docs/prompts/`](prompts/README.md).
- The owner reads Burmese: every final report to the owner is written in Burmese. The app's own
  text stays English.

## Contents

0. [How to work on this plan](#0-how-to-work-on-this-plan)
1. [Status board](#1-status-board)
2. [What the owner saw](#2-what-the-owner-saw)
3. [Owner decisions](#3-owner-decisions)
4. [Findings and root causes](#4-findings-and-root-causes)
5. [Phase 8 — field fixes → 1.0.0-beta.3](#5-phase-8--field-fixes--100-beta3)
6. [Phase 9 — copied-link flow → 1.0.0-beta.4](#6-phase-9--copied-link-flow--100-beta4)
7. [Phase 10 — formats and YouTube → 1.0.0-beta.5](#7-phase-10--formats-and-youtube--100-beta5)
8. [Owner device checklists](#8-owner-device-checklists)
9. [Backlog](#9-backlog)
10. [References](#10-references)

## 0. How to work on this plan

### 0.1 Session procedure (every task)

1. Follow [`AGENTS.md`](../AGENTS.md): `git fetch --all --prune`, `git status`,
   `git log -5 --oneline`.
2. Check out the task's phase branch (table below) and pull it. If it does not exist yet, create
   it from `origin/main`.
3. Read §0, §3, the task's section, the findings it links and every file under **Read first**.
   Read `docs/SESSION_STATE.md`.
4. Run the task's quick validation before editing, so you know the starting state.
5. Set the task to `IN PROGRESS` in the [status board](#1-status-board).
6. Do the **Steps** in order. Stay inside the task. Anything else you notice goes to
   [§9 Backlog](#9-backlog) instead of into the code.
7. Add the listed tests. A regression test must fail on the old code.
8. Run the validation (§0.3). Never report a result you did not run.
9. Update the docs the task lists and `CHANGELOG.md` (`## [Unreleased]`), set the task to
   `DONE (date)` (or `OWNER CHECK`), update `docs/SESSION_STATE.md` and checkpoint with
   `scripts/checkpoint.sh "T0x: summary"`. Check CI for the pushed commit.
10. Report to the owner in Burmese (§0.5). Stop after the task unless the prompt says otherwise.
    Never start the next phase on your own.

| Phase | Branch | Release |
| --- | --- | --- |
| 8 — Field fixes | `work/phase-8-field-fixes` | `1.0.0-beta.3`, versionCode 3 |
| 9 — Copied-link flow | `work/phase-9-copied-link-flow` (from `main` after beta.3) | `1.0.0-beta.4`, versionCode 4 |
| 10 — Formats and YouTube | `work/phase-10-formats` (from `main` after beta.4) | `1.0.0-beta.5`, versionCode 5 |

### 0.2 Rules for every task

- Product boundaries do not change: no DRM, paywall, private-content or sign-in bypass, and no
  adapter ever signs in.
- Never log, print, commit or put into fixtures: cookies, tokens, `Authorization` values or signed
  media URLs (the query strings of CDN links). Sanitize fixtures: keep hosts and paths, replace
  signed query values with `REDACTED`.
- Kotlin lines stay within 100 characters. Keep every existing `testTag`. No unrelated refactors
  or renames.
- `WebView` and `WebSettings` methods run on the main thread only. `shouldInterceptRequest` and
  `@JavascriptInterface` methods are called on other threads.
- Live checks against public pages with `curl` are allowed (the agent sandbox has internet) and
  expected for site tasks. Report status, host and path, and which markers were found; never
  paste signed URLs or page bodies into the repository.
- An owner decision (§3) that is still `PENDING` blocks the tasks that need it. Do not guess; ask
  the owner the question in Burmese and stop.
- The agent sandbox has no emulator (`/dev/kvm` is missing). Until T02 lands, list device checks
  for the owner. After T02, also use the CI emulator job.
- Keep temporary files (downloaded pages, live-check output, logs) outside the repository:
  `scripts/checkpoint.sh` stages everything with `git add -A`.
- The owner may answer decisions inside a prompt, for example `OWNER ANSWERS: D1=YES D2=B D3=YES`.
  Record such answers in §3 with the date before starting the task.

### 0.3 Validation

Environment: JDK 17 and Android SDK 35. In the Notion agent sandbox run `source /data/yft-env.sh`
first (it sets `JAVA_HOME`, `ANDROID_HOME` and `GRADLE_USER_HOME`). Stop stale daemons with
`pkill -f "[G]radleDaemon"`. On a machine with about 4 GiB of RAM, run `:app:assembleRelease` as a
separate Gradle call.

| Scope | Command |
| --- | --- |
| Quick (checkpoint default) | `./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug` |
| Module tests (add the ones you touched) | `:core-browser:testDebugUnitTest` `:core-download:testDebugUnitTest` `:core-data:testDebugUnitTest` `:core-media:testDebugUnitTest` `:core-model:test` `:extractor-api:test` `:extractor-generic:test` `:extractor-sites:test` |
| Full (end of a phase, every release) | `./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:assembleRelease`, then `bash scripts/verify-release-apk.sh --allow-unsigned --expected-version <version> app/build/outputs/apk/release/app-release-unsigned.apk` |
| Checkpoint | `CHECKPOINT_TEST_COMMAND="<quick plus your modules>" bash scripts/checkpoint.sh "T0x: summary"` |

CI status for a branch:

```bash
curl -s "https://api.github.com/repos/Alalkipgen/YFT/actions/runs?branch=<branch>&per_page=3" \
  | jq '.workflow_runs[] | {name, head_sha, status, conclusion}'
```

### 0.4 Status values

`TODO` · `IN PROGRESS` · `BLOCKED (reason)` · `OWNER CHECK` (done on the branch, waiting for the
owner's phone) · `DONE (date)` · `SKIPPED (decision)`. Checkpoint messages start with the task ID,
so `git log --oneline --grep "T0x:"` finds a task's commits.

### 0.5 Report to the owner (Burmese)

```text
Task: T0x — <title> — DONE / PARTIAL / BLOCKED
လုပ်ခဲ့တာ: …
စမ်းသပ်မှု: <exact commands> → <tests, failures, lint errors>
Commit / branch / CI: <sha> · <branch> · <run link> (debug APK: Artifacts › yft-debug-apk)
ဖုန်းမှာ စစ်ပေးရန်: 1. … 2. …
မပြီးသေးတာ / သတိပြုရန်: …
နောက်တစ်ဆင့်: T0y
```

### 0.6 Phone builds for the owner

Every green checkpoint run on a `work/**` branch uploads the artifact `yft-debug-apk` (kept 14
days). The debug app has its own application id (`com.alal.yft.debug`) and installs next to the
beta, so the owner can do a task's owner check before a release: GitHub › Actions › the run ›
Artifacts › `yft-debug-apk`, unzip, install. Put the run link in the report. Signed betas come only
from the release tasks (T10, T15, T19).

## 1. Status board

Agents update the **Status** column in every task checkpoint.

| ID | Task | Phase | Priority | Difficulty | Estimate | Needs | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T01 | [Browser crash: WebView used off the main thread](#t01--browser-crash-webview-used-off-the-main-thread) | 8 | P0 | Easy–Medium | 0.5 d | — | OWNER CHECK |
| T02 | [Real-WebView smoke test on a CI emulator](#t02--real-webview-smoke-test-on-a-ci-emulator) | 8 | P0 | Medium | 1 d | T01, D4 | DONE (2026-10-03) |
| T03 | [Browser start page; WebView only when a page is open](#t03--browser-start-page-webview-only-when-a-page-is-open) | 8 | P0 | Medium | 1 d | T01 | OWNER CHECK |
| T04 | [Local crash report and lookup details](#t04--local-crash-report-and-lookup-details) | 8 | P0 | Easy–Medium | 0.5–1 d | — | OWNER CHECK |
| T05 | [Browser-like request identity for Home lookups](#t05--browser-like-request-identity-for-home-lookups) | 8 | P1 | Easy–Medium | 0.5 d | — | TODO |
| T06 | [Facebook public reels and videos without sign-in](#t06--facebook-public-reels-and-videos-without-sign-in) | 8 | P1 | Medium | 1 d | T05 | TODO |
| T07 | [TikTok media cookies for Home lookups](#t07--tiktok-media-cookies-for-home-lookups) | 8 | P1 | Medium | 0.5 d | T05 | TODO |
| T08 | [YouTube: honest messages, identity and details](#t08--youtube-honest-messages-identity-and-details) | 8 | P1 | Easy | 0.5 d | T04, T05 | TODO |
| T09 | [Your sites: YouTube, Facebook, TikTok with logos](#t09--your-sites-youtube-facebook-tiktok-with-logos) | 8 | P1 | Easy | 0.5 d | — | TODO |
| T10 | [Release 1.0.0-beta.3](#t10--release-100-beta3) | 8 | P1 | Easy | 0.5 d | T01–T09, owner | TODO |
| T11 | [Check the copied link when YFT opens](#t11--check-the-copied-link-when-yft-opens) | 9 | P2 | Medium | 1 d | D1 | TODO |
| T12 | ["Video you copied" quick download sheet](#t12--video-you-copied-quick-download-sheet) | 9 | P2 | Medium–Hard | 1.5–2 d | — | TODO |
| T13 | ["Search to download" page](#t13--search-to-download-page) | 9 | P2 | Medium | 1 d | T03, T09 | TODO |
| T14 | [Floating Download button in the browser](#t14--floating-download-button-in-the-browser) | 9 | P2 | Easy–Medium | 0.5–1 d | T12 | TODO |
| T15 | [Release 1.0.0-beta.4](#t15--release-100-beta4) | 9 | P2 | Easy | 0.5 d | T11–T14, owner | TODO |
| T16 | [YouTube client strategy](#t16--youtube-client-strategy) | 10 | P3 | Hard (B) / Very hard (A) | 2–3 d (B) / 5–8 d (A) | D2, T08 | TODO |
| T17 | [Higher qualities: merge video and audio](#t17--higher-qualities-merge-video-and-audio) | 10 | P3 | Hard | 2–3 d | T16 | TODO |
| T18 | [MP3 audio](#t18--mp3-audio) | 10 | P3 | Hard | 2–3 d | D3 | TODO |
| T19 | [Release 1.0.0-beta.5](#t19--release-100-beta5) | 10 | P3 | Easy | 0.5 d | T16–T18, owner | TODO |

Estimates are agent working days and leave out the owner's phone checks. Totals: Phase 8 about
6 days, Phase 9 about 4–5 days, Phase 10 about 4–6 days with option B or 7–11 days with option A,
plus 2–3 days if MP3 is wanted.

## 2. What the owner saw

`1.0.0-beta.2` on the owner's phone, 2026-10-03:

| # | Steps | Result |
| --- | --- | --- |
| P1 | Home › paste `https://www.facebook.com/share/v/1Q3kAyptrS/` (a public reel) › Go | "Sign in to Facebook on this page first, then try again." with **Open in browser** |
| P2 | Home › Open browser, no address | White page with "Open a page / Type a web address above…", but no address bar and no close (X); the bottom toolbar is visible |
| P3 | Tap a Your sites shortcut, or **Open in browser** from P1 | The app closes (crash) |
| P4 | A YouTube link on Home | The same "Sign in" failure as P1 |
| P5 | Found during research, not yet seen by the owner: a TikTok link on Home | The lookup works, but the download would fail with HTTP 403 |

The owner also asked for (SnapTube as the model):

- Copy a link in another app, open YFT: it fetches by itself and shows a "Video you copied" sheet
  with Music (Fast / Classic MP3) and Video (Fast 480p / High 720p) rows, More formats and a
  Download button.
- A "Search to download" page with a "Link you copied" card and Download button, plus "View sites"
  (YouTube, Facebook, Status, Twitter/X, View all).
- A page with a playing video shows a floating Download button.
- YouTube, Facebook and other popular sites work.
- Home › Your sites shows YouTube, Facebook and TikTok with their logos instead of Archive,
  Wikimedia and NASA.

## 3. Owner decisions

Agents read this table before starting a task and record the owner's answers here as
`YES/NO (date, note)`.

| ID | Question | Proposal | Blocks | Answer |
| --- | --- | --- | --- | --- |
| D1 | May YFT check the clipboard by itself whenever it opens? Android 12+ then shows "YFT pasted from your clipboard" each time. | Yes, on by default, with a Settings switch; only http(s) links are used and the text is never stored | T11 (until then T12/T13 use tap-to-paste) | PENDING |
| D2 | YouTube strategy. **A**: keep the "no device impersonation" rule and mint PO tokens with YouTube's own BotGuard in a hidden WebView (very hard, fragile). **B**: add a device client like yt-dlp's `visionos` (easier, more formats, overrides the rule in `YouTubeClientProfile.kt`, breaks whenever YouTube changes it) | B first; A only if B stops working | T16, T17 | PENDING |
| D3 | Music: M4A now and MP3 later (LAME, LGPL, about 1 MB more APK)? | M4A now; MP3 in Phase 10 | T18 | PENDING |
| D4 | Run an Android emulator job on GitHub Actions (free for public repositories) | Yes | T02 | Assumed YES unless the owner says no |
| D5 | Release cadence: beta.3 after Phase 8, beta.4 after Phase 9, beta.5 after Phase 10 | Yes; publishing still needs `ALLOW_RELEASE=true` | T10, T15, T19 | Assumed YES |

Session instructions (2026-10-03): `OWNER ANSWERS: none`; D1–D3 remain PENDING. Continue eligible
Phase 8 tasks in plan order, with a checkpoint and CI check per task. `ALLOW_PUSH=true`,
`ALLOW_MERGE_MAIN=false`, `ALLOW_RELEASE=false`; no permission to merge, tag or publish.

## 4. Findings and root causes

### F1 — Every page load crashes the app (P3) — confirmed in code

- Beta.2 (`28930cf`, before T01):
  `core-browser/src/main/java/com/alal/yft/core/browser/webview/SecureBrowserWebViewClient.kt`:
  - line 46, inside `shouldInterceptRequest` (line 42): `val pageUrl = view.url ?: return null`;
  - line 58: `?: userAgentProvider()`, which
    `app/src/main/java/com/alal/yft/feature/browser/BrowserScreen.kt:812` implements as
    `browser.settings.userAgentString`.
- `shouldInterceptRequest` runs on a WebView background thread. `WebView.getUrl()` and
  `WebView.getSettings()` must run on the thread that created the WebView; Chromium throws
  `RuntimeException: A WebView method was called on thread '…'. All WebView methods must be called
  on the same thread.` The first intercepted request of any page therefore kills the process:
  Your sites, **Open in browser** and typed addresses all crash.
- Why the tests passed: Robolectric's shadow WebView does not enforce threads, and the browser has
  never run on a device or an emulator.
- Not affected: the YouTube solver WebView
  (`app/src/main/java/com/alal/yft/detection/script/WebViewSolverEngine.kt`) does not touch the
  WebView in its `shouldInterceptRequest`.
- T01 (2026-10-03, `work/phase-8-field-fixes`): `BrowserPageUrl` uses an `AtomicReference`;
  explicit loads, page starts and history callbacks update it on main. The configured User-Agent
  is read once on main; interception and the DownloadListener read snapshots only. Request and
  download observations are queued on `viewModelScope` so navigation and probe-job ownership are
  serialized. Existing candidate-store and probe-budget locks remain unchanged.
- Regression proof: the strict WebView subclass throws from `getUrl`/`getSettings` off-main;
  the background-executor test failed on the old client and passes on the fix. Navigation,
  redirects, 32 parallel requests/downloads and stale queued observations are covered.
  Validation: core-browser 53 tests, app 386 (41 render tests skipped), 0 failures, lint 0 errors.
  Audit: `git grep -n -E '@JavascriptInterface|shouldInterceptRequest' -- '*.kt'`, then inspect
  each production callback (and the Phase 0 prototype); no off-main WebView/WebSettings call.
  A real-WebView check remains T02 and the owner's phone check; beta.2 itself is unchanged.

### F2 — The empty browser hides the address bar (P2) — likely cause, not yet confirmed

- Before T03, `BrowserScreen.kt` (`BrowserScreen`) is a Column: the top row (close `browser-close`
  and `BrowserAddressField` `browser-address`), progress and notices, then a weighted box that
  **always** composes `BrowserWebView` (an `AndroidView`, lines 798–812) with `BrowserEmptyHint`
  (`browser-empty`, line 536) drawn over it, then `BrowserToolbar` (line 710).
- The hint sits where the weighted box's centre is, so the top row's space is reserved, yet the row
  is not visible on the phone. The most likely explanation is the native WebView surface drawing
  over the Compose top row, because a WebView exists even when no page is open. This cannot be
  reproduced without a device; T02's emulator screenshot of the empty browser decides it.
- T03's fix does not depend on the exact cause: no WebView until a page is opened, a Compose start
  page instead, and a top bar drawn above and outside the WebView.
- T02 first API 34 run (2026-10-03, `8151813`):
  https://github.com/Alalkipgen/YFT/actions/runs/37141758594 — all 3 instrumentation tests
  passed and logcat had 0 `FATAL EXCEPTION`; the empty-page address/close display assertions
  passed. The job was RED because all three screenshot files were missing after AGP's APK
  cleanup. The repaired collector keeps APKs installed until pull, checks pull failures,
  then uninstalls them. A regression fails on the old collector and passes on the repair;
  the repaired emulator run on `e9e1a09` is GREEN:
  https://github.com/Alalkipgen/YFT/actions/runs/37143005734 — 3/3 tests passed, 0 fatal
  exceptions, all 3 required PNGs collected (`yft-emulator-smoke`).
- API 34's empty-page address/close assertions pass; address bounds `[92,33][254,81]`
  are outside WebView bounds `[0,90][320,583]`. The phone's empty-page problem is not
  reproduced by these assertions. Loaded-page/found-sheet dumps omit the address node
  (and the expanded sheet hides the WebView from the accessibility tree); absent bounds are
  not evidence of missing pixels. T03 must retain the field's accessibility semantics.
- The job extracts address/WebView bounds from a temporary UIAutomator hierarchy (then deletes
  it). Semantics/bounds alone do not prove that pixels are unobscured; visual review of the
  screenshot is still required to confirm/reject the native-surface hypothesis.
  Artifact download returned HTTP 401 without authentication, so the agent has not reviewed
  the native PNGs; owner review remains. Do not call the phone cause confirmed.

- T03 implementation (2026-10-03, OWNER CHECK, `9be6b43`): no WebView on `browser-start`; first
  navigation creates it and later updates retain it. The page is clipped below an opaque
  higher-z-index bar. The idle field hides its ink rather than using alpha zero, so native
  accessibility can still find it. CI now checks the real Example Domain content, native
  top-control bounds and the absence of an empty WebView, not just Compose semantics.
  Local empty-surface regression FAILED before the change. Visual QA also found the count
  badge squeezed to 5 px at 200% text; its regression FAILED before the heading-wrap repair.
  Repaired full validation passed: core-browser 53/app 405 (45 render skips), 0 failures/errors,
  lint 0 errors/95 warnings. All 8 final browser renders passed individual visual inspection,
  including the replaced loaded Day/Night/130%/200% images. API 34 emulator CI is GREEN:
  https://github.com/Alalkipgen/YFT/actions/runs/37146164024 — 3/3 tests, 0 fatal exceptions,
  3 PNGs collected. Empty WebView is absent; loaded/found address bounds now appear at
  `[92,33][254,81]`, above the native WebView `[0,90][320,583]`.
  Checkpoint CI is GREEN: https://github.com/Alalkipgen/YFT/actions/runs/37146164049.
  The phone's original pixel-overlay cause remains unconfirmed, not re-labelled as fact.

### F3 — Facebook asks to sign in for a public reel (P1) — confirmed live

1. **Home lookups send no browser identity.**
   `app/src/main/java/com/alal/yft/feature/home/LinkInspector.kt` lines 101 and 174 pass
   `BrowserRequestContext(url, null, null)`. `FacebookExtractor.pageHeaders`
   (`extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/facebook/FacebookExtractor.kt:138–143`)
   adds `User-Agent` only when the context has one, so OkHttp sends `okhttp/4.12.0`. Facebook
   redirects that to `m.facebook.com/login/…`, `FacebookUrls.isAccessWall` reports
   `LOGIN_REQUIRED`, and `SiteAdapterCoordinator`
   (`app/src/main/java/com/alal/yft/detection/SiteAdapterCoordinator.kt:103`) words it as "Sign in
   to Facebook on this page first".
2. **With a good identity the parser still rejects the page.** `FacebookPageParser.isDrmProtected`
   (`extractor-sites/.../facebook/FacebookPageParser.kt:253–256`) treats any non-null `drm_info` as
   DRM. Public reels now carry `drm_info` =
   `{"video_license_uri_map":{},"graph_api_video_license_uri":null,"fairplay_cert":null,"widevine_cert":"…"}`:
   no licence, so not DRM. With that check relaxed in a throw-away local test, the live page parsed
   to HD and SD progressive renditions plus the DASH manifest.
3. **Titles keep HTML entities** such as `&#xb7;`, `&#x2764;&#xfe0f;` and `&#064;`.

Live request matrix (2026-10-03, from the sandbox, with the owner's share link):

| User-Agent | Extra headers | Result |
| --- | --- | --- |
| `okhttp/4.12.0` | any | 3 redirects to `m.facebook.com/login/` |
| `Mozilla/5.0 (Linux; Android 14) YFT/1.0.0-beta.2` (today's Home UA) | none, or navigation | 200 on the share URL, a 51 KB shell without video data |
| `YFT/1.0.0-beta.2 (Android 14)` | navigation | the same 51 KB shell |
| `YFT/1.0` | navigation | redirect to `/reel/1603698891196107/`; `browser_native_hd_url` present |
| `Mozilla/5.0 (X11; Linux x86_64) YFT/1.0.0-beta.2` | navigation | reel page with video data |
| desktop Chrome | navigation | reel page with video data (HTTP 400 without the navigation headers) |

"Navigation headers" means `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`,
`Accept-Language: en-US,en;q=0.9` and `Sec-Fetch-Mode: navigate` (yt-dlp's standard set). Any
User-Agent containing "Android" gets the shell. The HD file (about 68 MB, `video/mp4`) and the SD
file answered byte-range requests (206) without cookies.

Reproduce:

```bash
curl -s -L --max-redirs 5 -o /tmp/fb.html -w '%{http_code} %{num_redirects}\n' \
  -A 'Mozilla/5.0 (X11; Linux x86_64) YFT/1.0.0-beta.2' \
  -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8' \
  -H 'Accept-Language: en-US,en;q=0.9' -H 'Sec-Fetch-Mode: navigate' \
  'https://www.facebook.com/share/v/1Q3kAyptrS/'
grep -c browser_native_hd_url /tmp/fb.html   # 1 or more means the video data is there
```

### F4 — YouTube fails the same way (P4) — three causes

1. Home sends no identity here either (`okhttp/4.12.0`).
2. YouTube's bot check (`playabilityStatus.status` `LOGIN_REQUIRED`, reason "Sign in to confirm
   you're not a bot") is mapped by `YouTubePlayerResponseParser.playability`
   (`extractor-sites/.../youtube/YouTubePlayerResponseParser.kt:217–245`) to `LOGIN_REQUIRED`, which
   the coordinator words as "Sign in to YouTube on this page first". That is misleading.
3. **Structural.** YouTube changed its delivery in 2025–2026. yt-dlp's PO Token Guide lists: `web`
   gives SABR-only formats and needs a GVS PO token; `mweb` needs a GVS PO token; `web_embedded`
   needs no token but only plays embeddable videos; `android_vr` needs no token but has no "made for
   kids" videos. YFT asks exactly `WEB_EMBEDDED_PLAYER`, then `WEB`/`MWEB`
   (`YouTubeClientProfile.kt`), without tokens. So most videos cannot work even when no bot check
   fires. yt-dlp 2026.08.19 uses `visionos` and `web` by default (`_DEFAULT_CLIENTS`), and only
   `visionos` when no JavaScript runtime is available.

Sandbox tests (a datacenter IP, which YouTube treats more strictly than a phone): the watch page's
inline response, `WEB` and `MWEB` got the bot check; `WEB_EMBEDDED_PLAYER` answered "This video is
unavailable" for `aqz-KE-bpKQ`; `VISIONOS` returned `OK` for `dQw4w9WgXcQ` with 27 adaptive formats
(144p–2160p plus AAC and Opus audio, direct URLs, no player script needed) but the bot check for
`aqz-KE-bpKQ`. `VISIONOS` HLS variants are video-only with separate audio renditions (one 720p
segment checked: H.264 only). What the owner's phone receives is unknown; T04 and T08 add copyable
lookup details to find out.

### F5 — TikTok media needs TikTok's cookies (P5) — confirmed live

- The page lookup works with any User-Agent (`__UNIVERSAL_DATA_FOR_REHYDRATION__`, `playAddr`,
  `statusCode` 0).
- The media URL (host `v16-webapp-prime.us.tiktok.com`) answered 403 without cookies and 206 with
  the cookies the page response set for `.tiktok.com` (`tt_chain_token`, `ttwid`, `tt_csrf_token`).
- `app/src/main/java/com/alal/yft/detection/OkHttpExtractorClient.kt` keeps no cookies, and
  `TikTokExtractor.mediaContext`
  (`extractor-sites/.../tiktok/TikTokExtractor.kt:118–135`) forwards `context.cookie`, which is null
  on Home. The browser path forwards the WebView's cookie and should work once F1 is fixed (verify).
- Download replay goes only to the media URL's own origin
  (`core-download/src/main/java/com/alal/yft/core/download/SecureDownloadHttp.kt:85–97`), which is
  the TikTok media host. That rule is correct and stays.

### F6 — Your sites keeps the old defaults

`HomeSites.DEFAULTS` (`core-model/src/main/kotlin/com/alal/yft/core/model/settings/HomeSite.kt:20–24`:
Archive, Wikimedia, NASA) are shown only while nothing is stored
(`core-data/src/main/java/com/alal/yft/core/data/preferences/HomeSitesRepository.kt:43–47`, key
`home_sites`). The owner added YouTube, so his stored list already contains the three old defaults.
Changing `DEFAULTS` alone changes nothing for him; a one-time migration is needed.

### F7 — Building blocks that already exist

- The "Download as" sheet (`app/src/main/java/com/alal/yft/feature/preview/PreviewScreen.kt`,
  design 03): real variants, sizes, the preview player, Wi-Fi only, the metered confirmation.
  Reuse it for the quick sheet.
- The "Found on this page" sheet with its drag handle `media-found-button` (`BrowserScreen.kt`,
  lines 578–620).
- The Promptbox clipboard row: it checks only the clip *description* (and the Android 12+ URL
  confidence) and reads the text on tap (`app/src/main/java/com/alal/yft/feature/home/HomeLinks.kt`,
  DESIGN-NOTES decision 1).
- `DetectedMediaStore` (memory-only candidates), `DownloadEnqueuer`, the settings DataStore
  (`core-data/src/main/java/com/alal/yft/core/data/preferences/SettingsRepository.kt`).
- `AudioVideoMuxEngine` (MediaMuxer, AVC/AAC to MP4) with `AudioVideoMuxDownloadPlan` (two
  `DashDownloadPlan` tracks; queue entry `core-download/.../DownloadQueue.kt:139`),
  `HlsTransferEngine` and `DashTransferEngine`.
- `SensitiveValueRedactor` (`core-model/src/main/kotlin/com/alal/yft/core/model/logging/`) for any
  text that may contain URLs or cookies. T04 adds `DiagnosticTextSanitizer` for bounded
  copy/share steps: origins only, no queries, and no credential-bearing lines.

### F8 — Research notes

- **Android clipboard.** From Android 10, only the app in focus (or the default keyboard) can read
  the clipboard. From Android 12, the system shows a "pasted from your clipboard" message when an
  app reads a clip another app wrote. From Android 12, `ClipDescription.getConfidenceScore`
  with `TextClassifier.TYPE_URL` tells whether a clip is a link without reading it.
- **MP3.** Android has no MP3 encoder (MediaCodec encodes AAC, AMR, Opus and FLAC). FFmpegKit is
  archived (retired), so it must not be added. LAME (LGPL) built with the NDK as a shared library is
  the realistic option.
- **Logos.** Simple Icons (CC0) has `youtube`, `facebook`, `tiktok`, `instagram` and `x`. Brand marks
  remain trademarks: use them only as shortcuts to the site and say that YFT is not affiliated.
- **Emulator in CI.** GitHub's Ubuntu runners run hardware-accelerated Android emulators once KVM
  permissions are enabled; `reactivecircus/android-emulator-runner@v2` (v2.38.0) documents the step.
- **PO tokens.** NewPipe mints YouTube PO tokens in a WebView (`PoTokenWebView.kt`, GPL-3.0). YFT is
  MIT-licensed: do not copy that code; any implementation must be written independently.

## 5. Phase 8 — field fixes → 1.0.0-beta.3

Branch `work/phase-8-field-fixes`. Order: T01 → T02 → T03 → T04 → T05 → T06 → T07 → T08 → T09 →
T10. T04, T05 and T09 do not depend on the browser tasks and may move earlier if the browser work is
blocked.

### T01 — Browser crash: WebView used off the main thread

**Prompt:** [`docs/prompts/T01-browser-crash.md`](prompts/T01-browser-crash.md)

P0 · Easy–Medium · 0.5 day · fixes P3 ([F1](#f1--every-page-load-crashes-the-app-p3--confirmed-in-code))

**Read first:** `SecureBrowserWebViewClient.kt` (whole file); `BrowserScreen.kt` lines 790–840
(`BrowserWebView`); the observation sink or detector the client feeds (follow its constructor
parameters); every `@JavascriptInterface` method (`git grep -n JavascriptInterface`); the tests in
`core-browser/src/test/java/com/alal/yft/core/browser/webview/`.

**Steps**

1. Keep the current main-frame URL in a thread-safe holder (`AtomicReference<String?>`) that is
   written on the main thread in `onPageStarted`, `doUpdateVisitedHistory` and when the browser
   starts a load. `shouldInterceptRequest` only reads the holder.
2. Read the User-Agent once on the main thread when the WebView is configured
   (`webView.settings.userAgentString`) and pass the value (or a provider that returns the cached
   value). Nothing in `shouldInterceptRequest` may call a `WebView` or `WebSettings` method.
3. Check every other off-main-thread entry point: all `@JavascriptInterface` methods (they run on
   the JavaBridge thread) and any callback documented as a background callback. Post WebView work to
   the main thread (`view.post {}` or `Dispatchers.Main`).
4. Make whatever receives observations from `shouldInterceptRequest` safe for parallel calls;
   several requests arrive at the same time.
5. Keep behaviour: request observation, header capture, blocking rules and DRM hints give the same
   results as before.

**Tests**

- A Robolectric test WebView subclass whose `getUrl()` and `getSettings()` throw when called off the
  main looper. Call `shouldInterceptRequest` from a background executor and assert that it returns
  normally and that the observation carries the cached page URL and User-Agent. This test must fail
  on the old code.
- The holder follows navigation start, history updates and redirects.
- All existing browser tests stay green.

**Done when** no `WebView`/`WebSettings` call remains in background callbacks (name the grep you
used in the commit message), the tests pass and `CHANGELOG.md` has a `Fixed` entry.

**Owner check:** Your sites › any site; Home › Open in browser; type an address and Go. Pages load,
the app stays open and found media still appear.

**Docs:** `docs/TEST_MATRIX.md` (new tests), `docs/SUPPORT_MATRIX.md` browser row.

### T02 — Real-WebView smoke test on a CI emulator

**Prompt:** [`docs/prompts/T02-ci-emulator-smoke.md`](prompts/T02-ci-emulator-smoke.md)

P0 · Medium · 1 day · needs T01 and D4

**Why:** Robolectric does not run Chromium. The beta.2 crash and the hidden address bar only show
on a real WebView. GitHub's Ubuntu runners can run an accelerated emulator for this public
repository at no cost.

**Read first:** `.github/workflows/checkpoint-validation.yml`; `app/build.gradle.kts` (androidTest
dependencies around line 177); `gradle/libs.versions.toml`; `scripts/device-smoke-test.sh`; the
browser test tags (`browser-address`, `browser-close`, `browser-surface`, `browser-empty`,
`found-sheet`, `media-found-button`).

**Steps**

1. Add instrumentation dependencies through the version catalog: `androidx.test:runner`,
   `androidx.test:rules`, `androidx.test.ext:junit` and `androidx.test.uiautomator:uiautomator`. Set
   `testInstrumentationRunner` if it is missing.
2. `app/src/androidTest/java/com/alal/yft/smoke/BrowserSmokeTest.kt`, using
   `createAndroidComposeRule<MainActivity>()`:
   - open the browser without an address and assert that `browser-address` and `browser-close` are
     displayed; screenshot `01-browser-empty.png`;
   - open `https://example.com/`, wait for the load to finish and assert that the app is still
     running and the address bar shows the host; screenshot `02-browser-page.png`;
   - open a public page with an HTML5 video (for example a Wikimedia Commons `File:*.webm` page),
     wait up to 20 s for found media and take screenshot `03-found.png`. A slow site is a warning,
     not a failure.
   - Save screenshots with `UiAutomation.takeScreenshot()` in the app's external files folder.
3. New workflow `.github/workflows/emulator-smoke.yml`, triggered by pushes to `work/phase-*` that
   touch `app/**`, `core-*/**`, `extractor-*/**`, `gradle/**` or the workflow file, and by
   `workflow_dispatch`. Steps: checkout, JDK 17, Gradle cache, enable KVM (the udev rule from the
   runner's README), AVD cache, then `reactivecircus/android-emulator-runner@v2` with
   `api-level: 34`, `target: google_apis`, `arch: x86_64`, `disable-animations: true` and the script
   `./gradlew :app:connectedDebugAndroidTest`. Collect screenshots and logcat **inside** the
   runner's script before it shuts down the emulator (`scripts/ci-emulator-smoke.sh`).
   Set `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` for this command:
   otherwise APK cleanup removes the app's external files before `adb pull`. Uninstall the
   test and debug APKs after collection; treat a failed pull as an error, not a silent skip.
   Sanitize logcat and text/XML test reports before upload; never upload the raw log or binary
   result payload. Cache the clean AVD before installing the app, not test browsing data or adb keys.
4. Upload the screenshots, `logcat.txt` and the test report as an artifact (for the owner). Agents
   usually cannot download artifacts, so also print the results as annotations: `::error::` for
   every `FATAL EXCEPTION` in logcat, and `::notice::` with the screen bounds of `browser-address`
   and of the WebView (from `uiautomator dump`). Annotations can be read without a token at
   `https://api.github.com/repos/Alalkipgen/YFT/check-runs/<id>/annotations`.
5. Write the first result into [F2](#f2--the-empty-browser-hides-the-address-bar-p2--likely-cause-not-yet-confirmed):
   is the address bar visible on the emulator or not? This confirms or rejects T03's hypothesis.

**Done when** the job is green on the branch, the artifact holds the screenshots, logcat has no
`FATAL EXCEPTION` and F2 is updated.

**Notes:** the emulator uses a datacenter IP, so never assert YouTube (bot checks), and Facebook
may answer differently. Keep the job under about 20 minutes; it must not block the existing
checkpoint workflow.

### T03 — Browser start page; WebView only when a page is open

**Prompt:** [`docs/prompts/T03-browser-start-page.md`](prompts/T03-browser-start-page.md)

P0 · Medium · 1 day · needs T01 (T02's screenshots help) · fixes P2
([F2](#f2--the-empty-browser-hides-the-address-bar-p2--likely-cause-not-yet-confirmed))

**Read first:** `BrowserScreen.kt` (layout 194–360, address field 364, empty hint 536, toolbar 710,
WebView 798); `BrowserViewModel.kt`; Your sites and the Promptbox clipboard row in `HomeScreen.kt`;
`HomeSitesRepository.kt`; DESIGN-NOTES (Screens › Browser, decision 10); `BrowserScreenTest`.

**Steps**

1. While no page is open (nothing loaded, empty history), do not compose `BrowserWebView`. Show a
   Compose `BrowserStartPage` (`browser-start`) under the top bar: a "Link you copied" row that
   reuses the Promptbox clipboard logic (description check only, read on tap until D1/T11) and the
   Your sites shortcuts (tap opens the site). T13 builds on this page.
2. Create the WebView on the first navigation and keep it for the screen's lifetime: no
   re-creation on recomposition; state is saved as it is today.
3. Draw the top bar above the web content: clip the WebView container to its bounds
   (`clipToBounds()`), give the top bar a higher `zIndex` and apply the status-bar insets exactly
   once (edge-to-edge, targetSdk 35).
4. The close (X) button and the address field are always visible, with and without a page.
5. Replace `BrowserEmptyHint` with the start page and update tests from `browser-empty` to
   `browser-start`.

**Tests:** Compose tests — no page: `browser-start` is shown and `browser-surface` does not exist;
Go with an address: `browser-surface` exists and `browser-start` is gone; X and the address field
exist in both states. Update the accessibility audit and the Day/Night renders.

**Done when** the tests pass and T02's emulator screenshots show the address bar on the empty and
on the loaded browser.

**Owner check:** Home › Open browser shows the start page with the address bar and X; opening a
site keeps the address bar.

**Docs:** DESIGN-NOTES (Browser plus a new decision), `docs/TEST_MATRIX.md`.

### T04 — Local crash report and lookup details

**Prompt:** [`docs/prompts/T04-crash-report-details.md`](prompts/T04-crash-report-details.md)

P0 · Easy–Medium · 0.5–1 day

**Why:** the owner can only describe what happened. We need the stack trace of a crash and the
reason a lookup failed, without uploading anything.

**Read first:** `YftApplication.kt`, `AboutScreen.kt`, `SettingsScreen.kt`,
`SensitiveValueRedactor.kt`, `extractor-api` (`SiteExtractionResult`, `SiteExtractionFailure`),
`SiteAdapterCoordinator.kt`, `HomeViewModel.kt` and the Home not-found card.

**Steps**

1. `CrashReportStore` (app module, for example `diagnostics/`). In `YftApplication.onCreate()`,
   install a default uncaught-exception handler that writes one report to
   `noBackupFilesDir/diagnostics/last-crash.txt` (overwrite, at most 64 KB): UTC time, app version
   and versionCode, Android SDK, manufacturer and model, thread name, and the exception chain with
   stack traces. Pass every message through `SensitiveValueRedactor`. Then call the previous
   handler.
2. About: a "Last crash report" row while a report exists, with View (selectable text), Copy, Share
   (`ACTION_SEND`, text/plain) and Delete. Nothing is sent automatically.
3. Lookup details: add an optional `details: List<String>` to `SiteExtractionResult.Failure`
   (default empty, source-compatible). The coordinator and Home keep the last failure's details in
   memory, and the Home not-found card gets **Copy details** (`home-copy-details`). Details are short
   steps such as `page GET 200 (612 KB)` or
   `client WEB_EMBEDDED_PLAYER: ERROR "This video is unavailable"`, never query strings, cookies or
   tokens.
4. Debug builds only: a hidden "Crash now" action (for example a long press on the About version)
   to test the handler. It must not exist in release builds.

**Tests:** the handler writes, truncates, redacts and delegates; the About row's visibility and
actions; details never contain `?`, `Cookie`, `signature=` or `pot=`.

**Owner check:** whenever YFT closes by itself, About › Last crash report › Share it with the agent.

### T05 — Browser-like request identity for Home lookups

**Prompt:** [`docs/prompts/T05-headless-identity.md`](prompts/T05-headless-identity.md)

P1 · Easy–Medium · 0.5 day · fixes part of P1 and P4
([F3](#f3--facebook-asks-to-sign-in-for-a-public-reel-p1--confirmed-live).1,
[F4](#f4--youtube-fails-the-same-way-p4--three-causes).1)

**Read first:** `LinkInspector.kt` (`USER_AGENT` line 197, contexts at lines 101 and 174);
`HeadlessPageFetcher.kt`; `OkHttpExtractorClient.kt`; the `pageHeaders` of the Facebook (138–143),
TikTok (114–117), YouTube and Vimeo extractors; the F3 matrix.

**Steps**

1. Put the identity Home uses without a browser session in one place, for example
   `app/src/main/java/com/alal/yft/detection/HeadlessIdentity.kt`:
   `USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) YFT/${BuildConfig.VERSION_NAME}"` (honest,
   desktop-class, no "Android" token) and the navigation headers from F3.
2. `LinkInspector`: pass `BrowserRequestContext(url, HeadlessIdentity.USER_AGENT, cookie = null)` to
   the site adapters and give `HeadlessPageFetcher` the same User-Agent.
3. Adapters: top-level page GETs add `Accept` and `Sec-Fetch-Mode: navigate` when they are not set;
   JSON and API requests stay unchanged. `Accept-Language` stays.
4. The browser path keeps the WebView's own User-Agent and cookies.
5. `scripts/live-check.sh <url>`: fetch a public page with the headless identity and print the
   status, the final host and path (no query), the size and known markers (Facebook
   `browser_native_hd_url`, TikTok `__UNIVERSAL_DATA_FOR_REHYDRATION__`, YouTube
   `playabilityStatus`). It never prints bodies or signed URLs.

**Tests:** the inspector passes a non-null User-Agent; a fake `ExtractorHttpClient` sees the
navigation headers on page GETs and not on API calls; no cookie is ever added; the generic fixtures
still pass.

**Live check:** `bash scripts/live-check.sh https://www.facebook.com/share/v/1Q3kAyptrS/` shows the
reel path and the HD marker.

### T06 — Facebook public reels and videos without sign-in

**Prompt:** [`docs/prompts/T06-facebook-public-video.md`](prompts/T06-facebook-public-video.md)

P1 · Medium · 1 day · needs T05 · fixes P1
([F3](#f3--facebook-asks-to-sign-in-for-a-public-reel-p1--confirmed-live).2–3)

**Read first:** `FacebookPageParser.kt` (DRM check 253–256, labels around 245);
`FacebookExtractor.kt`; `FacebookUrls.kt` (share codes 84–98 and 182); the Facebook tests and
fixtures.

**Steps**

1. DRM only when `is_drm_protected` is true (in either place) or when `drm_info` (a JSON string)
   has a non-empty `video_license_uri_map` or a non-null `graph_api_video_license_uri`. An
   unreadable `drm_info` alone is not DRM; add a details line instead.
2. Add a sanitized fixture made from a live public reel page: keep only the JSON blocks the parser
   reads, replace CDN query strings with `REDACTED`, no cookies and no user data beyond the public
   title. Tests: public reel → HD, SD and DASH; a licence map present → `DRM_PROTECTED`.
3. Share links `/share/v/{code}` and `/share/r/{code}`: follow the redirect chain (with T05's
   headers) to `/reel/{id}` or `/watch/?v=` and accept the resolved id for unresolved identities.
4. Decode HTML entities (decimal, hex and the basic named ones) in the title and owner name, and
   trim a trailing " | Facebook".
5. Labels: when the DASH manifest or a probe gives the height, name progressive renditions with it
   ("720p · HD", "360p · SD"); otherwise keep "HD"/"SD". Never invent a height.
6. `LOGIN_REQUIRED` remains only for real login walls seen with T05's identity.

**Tests:** the cases above, entity decoding, and the share redirect through a fake client.

**Live check:** a Home lookup of the owner's link (or `live-check.sh` plus a parser run on the live
page) finds HD and SD; a ranged GET of the SD file returns 206.

**Owner check:** paste the link on Home → found → download → it plays.

**Docs:** `docs/SUPPORT_MATRIX.md` Facebook row (field result and fix), `docs/TEST_MATRIX.md`.

### T07 — TikTok media cookies for Home lookups

**Prompt:** [`docs/prompts/T07-tiktok-media-cookies.md`](prompts/T07-tiktok-media-cookies.md)

P1 · Medium · 0.5 day · needs T05 · fixes P5
([F5](#f5--tiktok-media-needs-tiktoks-cookies-p5--confirmed-live))

**Read first:** `TikTokExtractor.kt` (headers 114–117, `mediaContext` 118–135);
`OkHttpExtractorClient.kt`; the `extractor-api` HTTP result types; `SecureDownloadHttp.kt`; how
download tasks keep request contexts (`core-download/.../DownloadTaskStore.kt`).

**Steps**

1. Let a lookup see the cookies its own responses set: `ExtractorHttpResult.Success` exposes the
   response's `Set-Cookie` pairs (name=value only, domain-checked), in memory only.
2. TikTok: when the request context has no cookie, build the media context's cookie from the page
   response's cookies for `.tiktok.com`. The browser path keeps the WebView's cookie.
3. Keep the download rule: cookies go only to the media URL's own origin and are dropped on
   cross-origin redirects. Follow the existing policy for stored tasks; do not widen it.
4. `toString()` and logs never show cookie values.

**Tests:** a fake client with `Set-Cookie` → the candidate carries only TikTok's cookies; no cookie
when the page set none; redaction.

**Live check:** a Home lookup of a public TikTok video (for example
`https://www.tiktok.com/@scout2015/video/6718335390845095173`, which worked on 2026-10-03), then a
ranged GET of the candidate with its request context → 206.

**Owner check:** paste a TikTok link → download → it plays; the same from the browser.

### T08 — YouTube: honest messages, identity and details

**Prompt:** [`docs/prompts/T08-youtube-messages-details.md`](prompts/T08-youtube-messages-details.md)

P1 · Easy · 0.5 day · needs T04 and T05 · fixes the misleading part of P4
([F4](#f4--youtube-fails-the-same-way-p4--three-causes).1–2)

**Read first:** `YouTubePlayerResponseParser.kt` (`playability` 217–245, streaming data around
160–190); `YouTubeExtractor.kt` (`Verdicts`, headers); `YouTubeClientProfile.kt`; the messages in
`SiteAdapterCoordinator.kt`; the YouTube fixtures.

**Steps**

1. Add `SiteExtractionFailure.BOT_CHECK` (extractor-api). The parser maps a `LOGIN_REQUIRED` whose
   reason or error screen says "confirm you're not a bot" (straight or curly apostrophe) to
   `BOT_CHECK` (not definite). Coordinator message: "YouTube wants to check that this is not a bot.
   Open the video in YFT's browser, let it play for a moment, then tap Download." with
   **Open in browser**. Real sign-in and age gates keep their own messages.
2. A response whose `streamingData` only has `serverAbrStreamingUrl` (SABR) fails as
   `NO_MEDIA_FOUND` with a details line "SABR only".
3. Fill T04's details for every client asked: client name, playability status and reason, how many
   progressive and adaptive formats had URLs, and the SABR flag.
4. Home lookups use T05's identity for the watch page and the player requests.

**Tests:** a bot-check fixture → `BOT_CHECK` and the new message; a SABR-only fixture; details
redaction.

**Owner check:** paste a YouTube link. Either it is found, or the new message appears; then
**Copy details** and send them. This tells T16 what the phone receives.

**Docs:** `docs/SUPPORT_MATRIX.md` YouTube row (the 2026 limits), `docs/RISKS.md`.

### T09 — Your sites: YouTube, Facebook, TikTok with logos

**Prompt:** [`docs/prompts/T09-your-sites-logos.md`](prompts/T09-your-sites-logos.md)

P1 · Easy · 0.5 day ([F6](#f6--your-sites-keeps-the-old-defaults))

**Read first:** `HomeSite.kt`; `HomeSitesRepository.kt` and its tests; Your sites in
`HomeScreen.kt`; `OpenSourceNotices.kt` and `OpenSourceNoticesTest`; `docs/THIRD_PARTY_NOTICES.md`;
DESIGN-NOTES decision 2.

**Steps**

1. `HomeSites.DEFAULTS` becomes YouTube `https://m.youtube.com`, Facebook `https://m.facebook.com`
   and TikTok `https://www.tiktok.com`.
2. A one-time migration in `HomeSitesRepository` with a `home_sites_defaults_version` key (value 2).
   If a stored list exists: remove entries equal to the old defaults (same name and URL), put the
   new defaults that are missing first (match by site: `youtube.com`/`m.youtube.com`/`youtu.be`,
   `facebook.com`/`m.facebook.com`/`fb.watch`, `tiktok.com`), keep every site the user added and
   respect `MAX_SITES`. An empty stored list stays empty. The migration never runs twice.
3. Logos: vector drawables converted from the Simple Icons SVGs (CC0) for YouTube, Facebook, TikTok,
   Instagram and X, plus a host-to-logo map. Unknown sites keep the letter avatar. Never fetch
   favicons from the network. Use the brand colours from the Simple Icons data and check contrast
   in Night.
4. Notices: add Simple Icons (CC0-1.0) to `THIRD_PARTY_NOTICES.md` and to the About licences (the
   test compares the two), and the line "Site names and logos belong to their owners; YFT is not
   affiliated with them."

**Tests:** migration cases — untouched old defaults; the owner's case (old defaults plus YouTube →
YouTube, Facebook, TikTok without duplicates); a custom list stays unchanged; an empty list stays
empty; a second run changes nothing. Home renders and accessibility (each logo is described by the
site name).

**Owner check:** Home shows YouTube, Facebook and TikTok with logos, and his own YouTube entry is
not duplicated.

**Docs:** DESIGN-NOTES decision 2, `docs/THIRD_PARTY_NOTICES.md`.

### T10 — Release 1.0.0-beta.3

**Prompt:** [`docs/prompts/T10-release-beta3.md`](prompts/T10-release-beta3.md)

P1 · Easy · 0.5 day · needs T01–T09 and the owner's approval to merge and tag

**Steps:** full validation (§0.3); in `gradle.properties` set `yft.versionName=1.0.0-beta.3` and
`yft.versionCode=3`; turn `CHANGELOG.md` `[Unreleased]` into `## [1.0.0-beta.3] - <date>`; write
`docs/release/1.0.0-beta.3.md` (fixes, known issues and the §8 checklist); update the README status,
`HANDOFF.md`, `PHASE_STATUS.md`, `TEST_MATRIX.md`, `SUPPORT_MATRIX.md` and `SESSION_STATE.md`;
make the phase-completion commit. With the owner's approval, merge into `main` and push the tag
`v1.0.0-beta.3`, which runs `release-draft.yml` and creates a signed draft. Report the APK size,
SHA-256 and certificate. Only the owner publishes (`ALLOW_RELEASE=true`, `docs/RELEASE.md` §5).

## 6. Phase 9 — copied-link flow → 1.0.0-beta.4

Branch `work/phase-9-copied-link-flow`, created from `main` after beta.3. Order: T12 → T11 → T13 →
T14 → T15 (T12 works without T11, so it can start while D1 is open).

### T11 — Check the copied link when YFT opens

**Prompt:** [`docs/prompts/T11-copied-link-watcher.md`](prompts/T11-copied-link-watcher.md)

P2 · Medium · 1 day · needs D1 = YES

**Read first:** DESIGN-NOTES decisions 1 and 7; `HomeLinks.kt` and its tests; `HomeViewModel.kt`;
`MainActivity.kt`; `SettingsRepository.kt` and `DataStoreSettingsRepository.kt`;
`SettingsScreen.kt`.

**Steps**

1. A `checkCopiedLinks` setting (DataStore, default from D1) and a Settings › Privacy switch "Check
   copied links when YFT opens", with one line about Android's paste message.
2. `CopiedLinkWatcher`: when the app comes to the foreground and its window has focus (required from
   Android 10), and the setting is on, check the clip description (text; from API 31 skip clips whose
   URL confidence is known and low), read the clip, take the first http(s) URL with `HomeLinks`, and
   emit it once per distinct clip. Remember only a hash of the last handled clip, in memory. Never
   store or log the text.
3. Home: an emitted link fills the Promptbox and starts the lookup like **Use**; when media is found,
   the T12 sheet opens.
4. Update DESIGN-NOTES decision 1, the privacy text in `docs/PROJECT_CONTEXT.md` and the About
   privacy text.

**Tests:** setting off → no read; a non-link clip → no read on API 31+; the same clip twice → one
lookup; no read without window focus; no text stored.

### T12 — "Video you copied" quick download sheet

**Prompt:** [`docs/prompts/T12-quick-download-sheet.md`](prompts/T12-quick-download-sheet.md)

P2 · Medium–Hard · 1.5–2 days · works without T11 (after a manual Go)

**Goal:** after a Home lookup finds media, open a sheet titled "Video you copied" (thumbnail, title,
length) with:

- **Music**: "M4A · Fast" when an audio-only stream exists ("MP3" is added by T18);
- **Video**: up to two quick rows — **Fast** is the highest variant at or below 480p, **High** is
  the highest variant at or below 720p when it is a different one — with real labels and sizes
  ("480p · 18 MB"), never a made-up height;
- **More formats** opens the existing "Download as" sheet;
- **Download** queues the selected row and respects Wi-Fi only, the metered confirmation and the
  default-quality preselection.

**Read first:** `PreviewScreen.kt` with its ViewModel and variant resolution; `DownloadEnqueuer`;
`DetectedMediaStore`; the `Found` handling in `HomeViewModel.kt`; DESIGN-NOTES decision 11; the
navigation graph (`YftNavHost`).

**Steps:** the row selection as a pure, unit-tested function; a `QuickDownloadSheet` composable as a
dialog destination like "Download as"; Home opens it on `Found` when exactly one video was found
(several keep today's list); accessibility and renders.

**Tests:** selection table tests (only 360p → one row "360p"; 1080/720/480/360 → Fast 480p and High
720p; audio only → Music only); Compose tests; the accessibility audit.

### T13 — "Search to download" page

**Prompt:** [`docs/prompts/T13-search-to-download.md`](prompts/T13-search-to-download.md)

P2 · Medium · 1 day · needs T03 and T09

**Steps:** a "Search to download" entry on Home opens the browser start page in search mode. The
field takes a link (opens it) or words (two rows: "Search YouTube for “…”" →
`https://m.youtube.com/results?search_query=…` and "Search the web for “…”" →
`https://duckduckgo.com/?q=…`). A "Link you copied" card with **Download** (T11's rules). A
"View sites" grid (YouTube, Facebook, TikTok, Instagram and X with T09's logos) and **View all**
(the full Your sites list with Add and Edit). Status (WhatsApp) stays in the backlog.

**Tests:** Compose tests for each row and its navigation; the accessibility audit; renders.

### T14 — Floating Download button in the browser

**Prompt:** [`docs/prompts/T14-download-fab.md`](prompts/T14-download-fab.md)

P2 · Easy–Medium · 0.5–1 day · needs T12

**Steps:** while the current page has savable candidates, show a floating **Download** button
(`browser-download-fab`, Mint, bottom end above the toolbar, with a count badge when there is more
than one). A tap opens T12's sheet for the page's best video (several videos open "Found on this
page"). Hide it while a new page starts, when only DRM candidates exist and while the found sheet is
expanded. Keep the existing found sheet and its handle.

**Tests:** visibility rules (unit tests), a Compose test, the accessibility label "Download video,
N found".

### T15 — Release 1.0.0-beta.4

**Prompt:** [`docs/prompts/T15-release-beta4.md`](prompts/T15-release-beta4.md)

P2 · Easy · 0.5 day · as T10, with `yft.versionCode=4` and `docs/release/1.0.0-beta.4.md`.

## 7. Phase 10 — formats and YouTube → 1.0.0-beta.5

Branch `work/phase-10-formats`, created from `main` after beta.4.

### T16 — YouTube client strategy

**Prompt:** [`docs/prompts/T16-youtube-client-strategy.md`](prompts/T16-youtube-client-strategy.md)

P3 · Hard (option B) / Very hard (option A) · needs D2 and T08's details from the owner's phone

**Option B — device client.** Add a `visionos`-style profile to `YouTubeClientProfile.kt` (all client
identifiers stay in that one file; copy the values from yt-dlp's `INNERTUBE_CLIENTS` at the time of
the work), ask it first, then `WEB_EMBEDDED_PLAYER`, then the page's client. Its streams carry
direct URLs and need no player script. Progressive formats are usually missing, so HD video needs
T17; until then offer audio plus whatever progressive stream exists. Write ADR-006 (it overrides
"no device impersonation" for YouTube only) and update `docs/YOUTUBE_RISK_REVIEW.md`.

**Option A — PO token.** An offscreen WebView loads YouTube's BotGuard the way the web player does,
mints the GVS/player PO token bound to the visitor data, and attaches it to the `MWEB`/`WEB`
requests and to the media URLs (`pot=`). Write it independently (NewPipe's version is GPL-3.0). It
needs device testing on a home or mobile network.

**Both options:** keep T08's bot-check and SABR handling; refresh the fixtures; keep
`scripts/verify-youtube-solver.mjs` passing; check live on the owner's phone with Copy details;
update `docs/SUPPORT_MATRIX.md` and `docs/RISKS.md`.

### T17 — Higher qualities: merge video and audio

**Prompt:** [`docs/prompts/T17-video-audio-mux.md`](prompts/T17-video-audio-mux.md)

P3 · Hard · 2–3 days · needs T16

**Steps**

1. Model a pair of single-file tracks: let `DashDownloadPlan` take one whole-file segment, or add a
   direct-track variant, so that `AudioVideoMuxDownloadPlan` accepts a video-only MP4 (AVC) and an
   audio-only M4A (AAC).
2. YouTube candidates carry their chosen audio companion (a `MediaCandidate` field or a pair type)
   through Detected media, Preview, "Download as" and the quick sheet. Offer 480p, 720p and 1080p
   AVC rows; skip VP9 and AV1 (the mux engine handles AVC/AAC only).
3. Download `googlevideo.com` files in ranged chunks of at most 10 MB (YouTube throttles large
   single requests) and resume each track separately.
4. Check mux compatibility before queueing (`AudioVideoMuxEngine.evaluate`).

**Tests:** plan building, compatibility refusals, resuming each track, failure mapping. Owner
check: the merged file plays with sound.

### T18 — MP3 audio

**Prompt:** [`docs/prompts/T18-mp3-audio.md`](prompts/T18-mp3-audio.md)

P3 · Hard · 2–3 days · needs D3 = YES

**Steps:** build LAME 3.100 with the NDK (CMake) as a shared library for arm64-v8a, armeabi-v7a and
x86_64 (pin `ndkVersion`; CI installs the NDK). Decode AAC to PCM with MediaCodec and encode MP3
(CBR 128 and 192 kbps) with an ID3 title. Add an "MP3" row to the quick sheet and to "Download as".
Add the LGPL notice, licence text and source location to `THIRD_PARTY_NOTICES.md` and About.
Measure and record the APK size change (about +1 MB expected).

**Tests:** JVM tests for the control logic; an instrumentation test on the T02 emulator that encodes
a short AAC fixture. Owner check: the MP3 plays in another app.

### T19 — Release 1.0.0-beta.5

**Prompt:** [`docs/prompts/T19-release-beta5.md`](prompts/T19-release-beta5.md)

P3 · Easy · 0.5 day · as T10, with `yft.versionCode=5` and `docs/release/1.0.0-beta.5.md`.

## 8. Owner device checklists

### beta.3 (after Phase 8)

1. Install over beta.2 (same signing key); the app opens.
2. Home shows YouTube, Facebook and TikTok with logos. Tapping each opens it in the browser without
   a crash, with the address bar and X visible.
3. Home › Open browser shows the start page with the address bar; typing `example.com` loads it.
4. Paste the Facebook share link → found (HD/SD) → download → it plays in the Library.
5. Paste a TikTok link → found → download → it plays.
6. Paste a YouTube link → found, or the new message → **Copy details** → send them to the agent.
7. About › Last crash report: none (if there is one, Share it).
8. The release checks in `docs/RELEASE.md` §4.

### beta.4 (after Phase 9)

Copy a link in another app → open YFT → the sheet appears by itself (if D1 is YES; Android shows its
paste message) → Fast/High/Music rows → Download. Also the Search to download page, View sites and
the floating Download button on a video page.

### beta.5 (after Phase 10)

A YouTube 720p download plays with sound; an MP3 plays in another app.

## 9. Backlog

Not scheduled. Agents add new items here instead of widening a task.

- X/Twitter adapter: research the public syndication endpoint; many posts need sign-in.
- Instagram: login walls; probably browser-session only.
- WhatsApp Status saver: folder access through the Storage Access Framework.
- Earlier limitations: playlists and batch downloads, background playback, SAF folder export,
  refreshing an expired link in place after the process was killed.
- Remove `spikes/phase0-media` and `.github/workflows/phase0-validation.yml` if the owner agrees.
- SPA history-only navigation: the URL snapshot follows `doUpdateVisitedHistory`, but
  `BrowserViewModel.activePageUrl` changes only on `onPageStarted`. Review history-only page
  changes separately so observations from a new SPA URL are not discarded (existing behaviour;
  found during the T01 audit, not widened into its threading fix).

## 10. References

- yt-dlp YouTube clients and defaults:
  <https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/youtube/_base.py>,
  <https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/youtube/_video.py>
- yt-dlp PO Token Guide: <https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide>
- NewPipe `PoTokenWebView.kt` (GPL-3.0, reference only):
  <https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenWebView.kt>
- FFmpegKit (archived): <https://github.com/arthenica/ffmpeg-kit>
- Simple Icons (CC0): <https://github.com/simple-icons/simple-icons>
- Android emulator runner for GitHub Actions: <https://github.com/ReactiveCircus/android-emulator-runner>
- Android copy and paste: <https://developer.android.com/develop/ui/views/touch-and-input/copy-paste>
- Android `WebView` reference: <https://developer.android.com/reference/android/webkit/WebView>
