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

- Status: READY FOR MERGE (last code commit `a7e3a73`, CI green; base `c8fcd33` =
  `origin/work/phase-14-integration`; started and finished 2026-10-08). Owner check pending.
- P34 — Downloads and merges keep going in the background; speed in the notification: DONE,
  owner check pending. OWNER ANSWERS: none → BATTERY_CARD on, DONE_NOTICE on.
- Folder `/data/YFT-A`; push over SSH with `/data/.ssh/id_ed25519` (new key, owner added it).
- Starting state (`c8fcd33`, Agent A scope): `./gradlew --no-daemon --continue
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; app unit tests 746, 0
  failures, 66 skipped; lint 0 errors (95 warnings).
- Result:
  - Service (`download/DownloadForegroundService.kt`, decisions in `DownloadServiceController`):
    foreground while any task is queued, waits or runs, at every stage; partial wake lock
    `yft:downloads` (10-minute timeout, renewed every minute) while anything runs, Wi-Fi lock
    `yft:downloads-wifi` (`WIFI_MODE_FULL_HIGH_PERF`) while bytes move, both released when
    nothing runs and in `onDestroy` (`BackgroundLocks.kt`); `ServiceCompat.startForeground`
    with `dataSync`, plus `mediaProcessing` on API 35+ while a merge, MP3 conversion or save runs;
    `FOREGROUND_SERVICE_IMMEDIATE`; `onTimeout(startId, fgsType)` pauses all, stops and posts
    "Android paused downloads after 6 hours. Open YFT to resume."; a refused start (service or
    `startForeground`) keeps the queue and posts "Android paused downloads in the background.
    Open YFT to resume."; START_STICKY restarts still restore the queue. Manifest: WAKE_LOCK,
    FOREGROUND_SERVICE_MEDIA_PROCESSING, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    `foregroundServiceType="dataSync|mediaProcessing"`.
  - Freeze detector (`FreezeDetector.kt`): a 1-s tick on `elapsedRealtime`; more than 10 s late
    with the wake lock held → `BackgroundHealthStore.recordFreeze` (count, length, battery card
    shown again) and one log line "Background freeze: N s lost while <stage>".
  - Notification (`DownloadNotificationText.kt`, `DownloadNotificationFactory.kt`): texts as in
    the plan, InboxStyle up to 5 lines, at most one update a second, speeds from the new
    `DownloadSpeedMeter` (one `TransferRateTracker` shared with the Downloads cards); speed
    format G8 in `speedLabel`. Finished notices on the new "Finished downloads" channel
    (tag = task id, only for tasks the service saw run; no path or address).
  - Cards: `BackgroundCards.kt` / `BackgroundCardsUi.kt` on Downloads (under the network
    notice): `downloads-notifications-card` (Turn on → the app's notification settings, Dismiss
    until notifications were on again) and `downloads-battery-card` (Allow →
    `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, falling back to the optimization list, then
    app details; Not now until the next freeze; Xiaomi, Redmi, POCO steps; App settings when
    limits are already off but the phone froze YFT). State read again on every resume.
    Settings › Downloads › Background downloads (`settings-background-downloads`): Allowed /
    Limited, a dialog with the same text, Allow and the Xiaomi steps.
  - Fixed on the way: Resume and Retry on Downloads now start the service (after Pause all had
    stopped it, a resumed download ran without it).
  - Plan adapted: (1) the old code has no way to run the service on a test's queue, so step 1's
    emulator tests run on the new code (`DownloadForegroundService.testQueue`, null in the app);
    the old behaviour is recorded from the code in TEST_MATRIX. (2) A process under
    instrumentation keeps foreground priority and is never frozen, so the emulator proves the
    service, the notification and the finished notice; HyperOS's freezer is the owner's check.
    (3) The slow server is an OkHttp interceptor in the test (the app's network rules forbid a
    local cleartext or self-signed server), and the merge test paces the first half of the
    merge's progress over 20 s around the real MediaMuxer instead of a 20-minute input.
    (4) Android's pause notices use the "Finished downloads" channel, which alerts.
- Validation (2026-10-08): `./gradlew --no-daemon --continue :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; app unit tests 790 (746 + 44 new), 0
  failures, 66 skipped; `:app:lintDebug` 0 errors, 95 warnings (unchanged; the intended
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` request is marked `@SuppressLint("BatteryLife")`, YFT is
  not on Google Play); `:app:compileDebugAndroidTestKotlin` OK; line check empty.
- Regression proof: The new tests need the new API, so the old behaviour was put back in copies (old
  `DownloadLabels.kt` from `c8fcd33`; the controller without locks, with `dataSync` only, no freeze
  check, no notices and no speed; the notification text without speed or time left; Resume without
  the service; no battery card; no Settings row) → 21 tests fail: `DownloadServiceControllerTest` 9
  of 10 (all but "no freeze while nothing runs"), `DownloadNotificationTextTest` (one download,
  unknown size), `DownloadNotificationFactoryBackgroundTest` (one and several),
  `BackgroundCardsTest` 4 (battery card, Xiaomi steps, freeze, BATTERY_CARD off),
  `DownloadsViewModelBackgroundTest` 2 (Resume starts the service, battery card), `SpeedLabelTest`
  (format), `BackgroundDownloadsSettingTest` 2 (Limited, Allowed); files restored from
  `/data/bak/P34/new` with `cp`, `cmp` equal. The emulator tests' JVM twins are
  `DownloadServiceControllerTest` and `DownloadNotificationTextTest`.
- CI (`a7e3a73`, all green): checkpoint validation
  https://github.com/Alalkipgen/YFT/actions/runs/37793747249, emulator smoke on API 34 (all
  instrumented tests, with the two new background tests)
  https://github.com/Alalkipgen/YFT/actions/runs/37793747385, Preview APK (test key, artifact
  `yft-preview-apk`) https://github.com/Alalkipgen/YFT/actions/runs/37793747336.
- Owner check: (1) a large download, Facebook for 2 minutes → "N% · speed · … left" and it keeps
  going; screen off a minute → still going; (2) a long YouTube video: when "Merging … %" starts,
  switch to Facebook → the % keeps moving and "Downloaded · …" arrives without opening YFT;
  (3) if the card says the phone paused YFT, follow its Xiaomi steps once and repeat (2).
- Sandbox note: on the 4 GiB machine the single-use Gradle daemon (~2.5 GB) plus the Kotlin
  daemon (~1.1 GB) left about 40 MB free while the unit tests ran; stopping the idle Kotlin daemon
  (mine) after compilation freed 700 MB. Compile first, then test and lint.
- Hand-offs: none

## Agent B — `work/phase-14-sites` (P36, P37)

- Status: NOT STARTED
- P36 — TikTok: Download on the For You feed and video pages: TODO
- P37 — Other sites: fresh links instead of HTTP 410: TODO
- Hand-offs: none

## Agent C — `work/phase-14-fast-merge` (P35)

- Status: NOT STARTED
- P35 — Faster merge for long videos: TODO
- Hand-offs: none
