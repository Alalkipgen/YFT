# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

## Master backup prototype — `spike/master-extractor-backup`

- Owner-approved isolated code spike, not a Phase 14/15 task or a merge candidate.
- Base: stable `origin/main` `a9eea7ba8d9f3d67442ffc3a51f2ad9e00c4a6a9`.
- Workspace: `/data/YFT-Master`; original `/data/YFT` and TikTok work remain untouched.
- Status: ANDROID FOUR-FIXTURE GATE PASSED; nine-site public preflight recorded.
  Live app/engine validation remains incomplete; NOT APPROVED FOR MERGE.
  Owner explicitly approved continuing Android Play & Capture connection after the JVM backup.
  New `:extractor-master-android` producer and browser-only build-opt-in caller.
  `yft.masterCapture` defaults false; debug/preview may opt in, release remains false.
  Existing site-extractor modules, download engine and CI workflows remain unchanged.
- Baseline: `:core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test`
  passed: 360 tests, zero failures/errors/skips.
- Branch-name exception: the owner explicitly approved `spike/master-extractor-backup`.
  `scripts/checkpoint.sh` only accepts `work/phase-*`; this spike uses equivalent manual
  staged-file/secret/diff/test checks and a direct push, without changing that script.
- Initial remote checkpoint: `f8e7451804c5d297dc4b9b2672d5493793de980d` (56 module tests),
  pushed by SSH. The sandbox later reset; recovered that exact checkpoint by HTTPS and
  verified it with the GitHub MCP. Further pushes use the connected GitHub MCP, no new SSH key.
- Hardened code checkpoint: `35213502d29690903e9cbd0dc3d68bf3aaab989d`, pushed and verified
  through GitHub MCP. The final checkpoint changes only this section and the module README.
- Hardened validation with a real browser snapshot: `:extractor-master:test :core-model:test
  :extractor-api:test :extractor-generic:test :extractor-sites:test` passed: 437 tests,
  zero failures/errors/skips (Master 77, unchanged baseline 360).
- Hardening: independent companion probes/cache within the shared budget, preview veto,
  private/regional payload gates, monotonic navigation generations, bounded request context,
  safe debug wrappers, preserved grouping IDs, origin-only cross-origin referers, live-stream
  refusal and stricter Content-Range validation. Existing adapters remain unchanged.
- Real smoke: MDN neutral CC0 flower video played; 960 x 540, duration 5.055 s, two successful
  HTTP 206 video/mp4 requests. Actual capture -> Master fallback -> real HTTPS prefix probe
  passed. This is not Instagram/X, Android WebView or full download/mux validation.
- Regression proof: the compatible pre-hardening engine failed five targeted tests; a
  file-signature bypass failed the intended false-MIME test. Both are deliberate negative
  checks, not unresolved hardened-code failures. Both source files were restored byte-for-byte.
- Final restored-code offline validation: 436 passed, zero failures/errors, one intentional
  optional live-smoke skip (Master 76 passed + one skip; unchanged baseline 360 passed).
  Repository script tests: 30 passed. Diff, scope and sensitive-file checks passed.
- CI: `spike/**` is not an automatic CI trigger; local JVM evidence only. No preview APK.
- Android pre-edit baseline: Master 76 passed + optional skip; core-browser 138 passed;
  app 807 cases, zero failures/errors, 66 existing skips; Android test Kotlin compiled.
- Android module milestone: 15 unit tests passed, zero failures/errors/skips; lint no issues.
  Native request context + bounded top-frame JS/API data, navigation generations, two-sample
  playback evidence, DRM refusal and main-thread WebView boundary. App wiring not yet present.
- Module checkpoint: `22d1ff6c94a4a69c8608e02342feb5b9b4ae5ee0`. A later sandbox reset
  discarded unpushed wiring; the exact authored patches were recovered from session events.
- App wiring checkpoint: `eddfa48b73655a28610430b62cb9f8b6ccedc89e`.
  Initial full app-wiring validation passed: Android module 18 tests; app 826 cases, zero
  failures/errors, 66 existing skips; both lints passed; four Android test methods compiled.
  Opt-in debug/test flags true, release flag false. Command used `-Pyft.masterCapture=true`
  and the Android module/app unit tests, lint, Android test compile and release BuildConfig.
- Follow-on hardening: production focus-probe identity guard, GET-only observed media replay,
  known VAST preview-veto forwarding. Code checkpoint `1e15d0f4f30b327ff9d2cd7ee3ed69e58c4374b4`.
  Final offline run: 1,422 cases, zero failures/errors, 67 existing/optional skips; Android module
  21, app 826. JS 11 and repository script 30 tests passed. Both lints have zero errors; app lint
  has 96 dependency/API/vector warnings. Debug/test opt-in true, release false.
- Internal x86_64 debug/test APKs built. First actual API 29 software-emulator run: paused
  preload, navigation/disposal and DRM refusal passed; playback success returned NeedsPlayback.
  Test-only change waits for read-only evidence that the actual user tap started playback,
  rather than a fixed 500 ms delay, and asserts collector installation. Repeat run pending.
  Production capture budgets and authorization rules unchanged.
- Readiness-only device retest: 2/4 passed; DRM waited unnecessarily for playback; positive
  playback reached readiness but capture still returned NeedsPlayback. Current follow-on uses
  a 10-second neutral fixture, taps its actual visible control, and binds the collector once
  per lookup. DRM must refuse probing even without playback. The 2.5-second capture deadline
  and two-sample authorization remain unchanged. Build/device validation pending.
- Optimization/fixture checkpoint: `b07bd5d2c05ebc2660f3c9431bd13e648e82ce50`.
  Android module 21 tests passed; debug APK assembled. The combined build stopped because
  app unit-test lint's Kotlin FIR resolver crashed on unchanged AppIdentityTest.kt (RAW_FIR
  to TYPES), not a reported app lint violation. Test APK/final app-unit execution unfinished.
  Next attempt separates compile/test assembly from lint and uses one Gradle worker, with
  every lint check still enabled.
- Serial retry passed without disabling checks: app unit tests 826 cases, zero failures/errors,
  66 existing skips; Android module 21 passed. Internal app/test APKs rebuilt. Fresh module/app
  lint passed with zero errors/fatal issues, zero module warnings and 96 app dependency/API/vector
  warnings. Debug opt-in true and release false. Code under validation remains `b07bd5d`.
- Current rebuilt-code device run: 3/4 passed (paused preload, navigation/disposal, DRM refusal).
  Positive playback stopped before capture because WebView 74 did not expose the fixture button
  through UiSelector text. No green positive-capture claim. Test now measures its known visible
  DOM button rectangle and injects a real screen tap; no JavaScript play/seek/evidence forgery.
  Production code remains `b07bd5d`; only test code/docs changed. Rebuild/retest pending.
- Recovery retest after another sandbox restore: recovered exact branch `57a0654`, plus the
  matching internal app/test APK session backups. Restored API 29 x86_64 / WebView 74.0.3729.185.
  The unchanged full class again passed 3/4 and failed before capture at user-playback readiness;
  the positive case alone passed `OK (1 test)`. Full-class logcat records SystemUI/input-channel
  failure and an ANR window during the positive test. This is observed interference, not proof
  that test ordering is the sole cause. JS transport 11/11 and repository scripts 30/30 passed.
- Current test-only repair explicitly removes the fixture composition and verifies WebView/
  capture-scope disposal before activity teardown. It waits for visible native window focus
  before the one actual screen tap, and separately checks that a trusted click reached the
  fixture. That receipt is not playback authorization. Production files, 2.5-second capture
  deadline, two-progress-sample requirement, DRM refusal and navigation guards are unchanged.
  Harness checkpoint `0a3416746232e5f48ac3e8913c3064727519f422` compiled successfully:
  module 21/21 passed, internal x86_64 app/test APKs assembled, debug/test capture flags true,
  release false. The guarded full class passed 3/4 and refused the tap because native window
  focus never arrived; the same guarded positive case alone also refused while the dialog
  remained. WindowManager and a screenshot directly confirmed the foreground window was
  "Application Not Responding: com.android.systemui", not the fixture. Native Wait cleared it.
  A subsequent repeat stopped before instrumentation because API 29 logcat could not clear
  its main buffer; that diagnostic-only action is now bounded/best-effort, not a pass criterion.
  After focus was restored, the unchanged full class reached trusted user playback but the
  positive case returned NeedsPlayback (capture elapsed 2,949 ms); 3/4 passed. The identical
  positive case alone then passed. Decoder creation and skipped frames are recorded in logcat;
  emulator load/test readiness remain under investigation, not an established production fix.
  Reduced-load repeats (480x854, density 240, identical APKs) produced `OK (4 tests)` once;
  the next full class passed 3/4 with NeedsPlayback (capture elapsed 3,660 ms). Logs/metadata:
  `lowres-1` and `lowres-2` under `/data/tmp/master-android/`. This is one aggregate pass, not
  two consecutive passes and not a stable device-green claim.
- Current follow-on is test readiness only: after the actual trusted tap, wait for at least
  0.5 seconds of naturally observed video progress with future decoded data, handling the real
  fixture loop. It never plays/seeks by instrumentation JS, changes currentTime, or feeds those
  reads into session.accept. Production capture must still independently collect two fresh
  progress samples within its unchanged 2.5-second timeout. Harness checkpoint `3acfd4f8`
  compiled successfully. Before the following sandbox reset, the fresh full offline suite
  passed 1,422 cases with zero failures/errors and 67 skips; JS 11/11 and repository scripts
  30/30 passed. Both lints had zero errors/fatal issues, zero module warnings, 96 app warnings.
  Debug/test capture was true and release false. These are verified previous-run results,
  not a claim that restored tools reran the suite.
  A subsequent device attempt stopped before tests: SystemUI ANR owned focus after boot and
  the test APK update timed out. Temporary `wm size` did not persist on reboot. The next
  attempt uses permanent 480x854/density 240 AVD hardware and 1,536 MiB guest RAM instead of
  1,024 MiB, without Gradle/emulator overlap or changes to production security.
  Another restore discarded the local workspace/tools; source was recovered at `3acfd4f8`
  and the already-built internal app/test APK session backups were restored and SHA-256 checked.
  App APK SHA-256: fa421fad73f356a770d1143044346189c01773ffe522d8f628738b9f796d417d.
  Test APK SHA-256: 7e5f0e3e8d2dc9204bbecbe4e50167e6050e49b759ca4f62b76dea4488d1d39d.
  Aggregate repeats remain PENDING; do not finalize on the earlier single 4/4 pass.
- Latest restore (2026-10-09): old-session APK/runner attachments were unavailable in this
  new chat. Cloned and verified `23f0c7e`; only the two documentation files differ from
  `3acfd4f8`, and production remains byte-identical to `b07bd5d2`. Rebuilt the x86_64 debug/test
  pair with `-Pyft.masterCapture=true`, Java 17 and SDK 35. Gradle build succeeded; APK archival
  uses the signed package-task outputs under `app/build/intermediates/apk/`. Fresh module unit
  tests 21/21, JS transport 11/11 and repository scripts 30/30 passed. Debug capture is true;
  release is false. The earlier 1,422-case/lint totals remain previous-run evidence.
  New app SHA-256: 1ba2334f1c4d7bff4c3118d08e2485540115f22275a5d666d39beca46373c90c.
  New test SHA-256: bb27795923ebb6148476dd583268e8418d267923f39618fc8961677608fb4ce4.
  Matching debug certificate SHA-256: 369d93b78f43a8252d45de7200e1173748bc55339cb88f3a3947cb4d0156b172.
  Restored the strict local runner: explicit APK paths, installed-byte SHA comparison before
  skipping reinstall, native ANR/focus rejection and exact `OK (4 tests)` aggregate criteria.
  Targeted GitHub secret scanning was unavailable (Advanced Security is not enabled); the
  equivalent local sensitive-path/added-line secret checks passed. No security setting changed.
  Permanent AVD hardware is 480x854/density 240, RAM 1,536 MiB, two cores; cold boot is pending
  preflight completion, without Gradle/emulator overlap. No restored device test has started.
- Additional owner request: read-only P40 review at main `69bf022`. Latest TikTok branch
  `1c4206e` is already included there; CI runs 37886371700, 37886371675 and 37886371676 were
  queried directly and all report success for `ce04711`. P40 collects TikTok page/API data,
  not the Master's native-Play/two-progress-sample proof. No TikTok or main code was changed.
- Android finalization (2026-10-09): after the following reset, the same chat's saved APKs,
  manifest and strict runner were recovered without rebuilding. Both APK SHA-256 values and
  shared certificate matched the new-chat rebuild above; source stayed at `dbeee695`, with the
  unchanged `3acfd4f8` harness. Fresh JS transport 11/11 and repo scripts 30/30 passed again.
  API 29 x86_64 / WebView 74.0.3729.185 booted at 480x854, density 240, RAM 1,536 MiB, two
  software-emulated cores. Device-side screencap/pull produced a valid 122,310-byte PNG of the
  SystemUI ANR; the inspected native Wait action restored Launcher focus, confirmed by
  WindowManager and a valid 191,958-byte screenshot. The focus guard was not weakened.
  The exact same signed APK pair then passed two consecutive complete four-test classes:
  `restored-pair1-1`: `OK (4 tests)`, JUnit 103.893 s (instrumentation wall 127 s);
  `restored-pair1-2`: `OK (4 tests)`, JUnit 78.278 s (instrumentation wall 94 s).
  Before/after installed APK bytes matched; the second run skipped reinstall only after its
  hashes matched. Both metadata files identify the same source, harness, APKs and signer.
  The positive fixture used its actual native tap, trusted-click receipt and sustained natural
  playback; production collected its own fresh progress samples. No JS play/seek invocation
  by instrumentation or forged playback evidence was added. Paused preload, DRM refusal and
  navigation/disposal-generation cases also passed in both aggregates.
  Portable APK/runner/setup/gate evidence backups were saved in this session. Production code,
  the 2.5-second capture budget, independent progress authorization, DRM and navigation guards
  remain unchanged. This proves the offline Android fixture gate only: no Master CI green,
  live Instagram/X support, full TLS/download/mux or merge readiness claim.
- Public live preflight (2026-10-09): owner expanded the read-only matrix to Instagram, X,
  Facebook, YouTube, TikTok, Reddit, Vimeo, Dailymotion and Threads. All nine public pages
  were rendered without signing in; no bot/age/DRM restriction was bypassed. This is desktop
  browser observation, not Android WebView or final Master-engine/download validation.
  The exact unmodified capture asset (SHA-256
  13fefa36d643ea2b339d8db5a8c0de6c0f57b2cd3d5a098240831fc4fe0817dc) was exercised:
  Instagram native player-surface click resumed visible natural playback, 720x1280/readyState 4.
  Fresh collector samples advanced 0.444536 -> 0.802345 s in 360.7 ms; generation 1, no DRM,
  60 request observations and eight bounded payload observations. Earlier autoplay was
  1080x1920; these are adaptive displayed sizes, not a proved downloadable rendition list.
  The Instagram diagnostic click listener missed the separate overlay ancestor and recorded
  zero receipts; the actual browser-native click occurred. That zero was not rewritten.
  X main-video native Play produced one trusted receipt; 720x1280/readyState 4 progressed
  10.880927 -> 11.232045 s in 352.6 ms, generation 1, no DRM. This resumed buffered playback:
  zero new request/payload observations. The offscreen 320x568 recommendation was excluded.
  Both bounded observations fit 2.5 seconds, but neither invoked the full Master engine or
  its independent HTTPS-prefix validator. Both clips were natively paused and hooks disposed.
  TikTok's public NASA clip rendered an active visible 720x1280 video (26.166 s); the paused
  offscreen recommendation was excluded. P40/native-Play Master capture is not yet verified.
  Facebook rendered a public 20.921-s 1280x720 HTTPS video, explicitly an ISS teaser; it is
  not full-length/4K/main-video proof. A non-teaser NASA sample was discovered for follow-up.
  YouTube required sign-in to confirm the browser was not a bot; Reddit showed Prove your
  humanity. These are access limitations in this environment, not universal incompatibility.
  Vimeo rendered a video placeholder with readyState 0; Dailymotion exposed an iframe player;
  Threads rendered the public caption without a top-frame video. Those playback/capture paths
  remain unverified, rather than being declared supported or unsupported.
  Sanitized summaries retain only page URLs, dimensions, states, counts and media hosts;
  no cookies, signed CDN URLs or raw API response bodies were exported.
- Multi-resolution feasibility: delivered progressive/HLS/DASH variants can be collected;
  absent 240p/360p/480p/720p/1080p variants cannot be invented from one MP4. Existing readers
  cover Instagram versions, X variants, YouTube formats, Facebook DASH and TikTok bitrateInfo.
  TikTok nested PlayAddr Width/Height still needs reviewed dimension mapping/tests before
  precise rendition labels; portrait sizes must not be mislabeled from height alone.
  No production quality mapping/transcoding/download change was made under the frozen scope.
- Next: complete native-play observation for the remaining accessible public players, then
  use a reviewed test-only live harness to exercise the real Master engine/HTTPS-prefix path.
  Keep bot/login/private/DRM failures terminal and distinguish teaser/iframe/MSE limitations.
  TLS/full download/mux, all-resolution coverage, Master CI green and merge readiness remain
  unverified. Run builds and emulator separately. Main/Phase 15/TikTok code remains untouched.
  No merge, tag, release or owner-phone APK handout approval.

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
- P38 (2026-10-08, Agent A in `/data/YFT-A` after a sandbox reset: new SSH key, JDK 17, SDK 35,
  NDK and CMake reinstalled, a 4 GiB swap file): merged A (`6325f37`) → B (`fd66038`) → C
  (`1f70d26`) with `--no-ff`, no conflicts, no open hand-offs. Full validation: 1532 tests, 0
  failures, 66 skipped (app 807, core-browser 138, core-data 33, core-download 166, core-media
  28, core-model 106, extractor-api 32, extractor-generic 20, extractor-sites 202); lint 0
  errors (app 95 warnings); `:app:assembleRelease` OK; line check clean. The owner asked to
  push `main` after the merge (no tag): CI of `4da3e61` green, `main` fast-forwarded.
- Last pushed checkpoint: P38: merge A, B, C — full validation green (`4da3e61` on
  `work/phase-14-integration`), CI all green: checkpoint validation https://github.com/Alalkipgen/YFT/actions/runs/37811403961,
  emulator smoke (P34–P37 instrumented tests together) https://github.com/Alalkipgen/YFT/actions/runs/37811403940, Preview APK (test key)
  = **Preview #6** https://github.com/Alalkipgen/YFT/actions/runs/37811403962 (Artifacts › `yft-preview-apk`). Then this docs commit;
  `main` fast-forwarded to it at the owner's request (no tag).
- Next: the owner installs Preview #6 (Preview APK run of the P38 merge commit › Artifacts ›
  `yft-preview-apk`; uninstall the older YFT Preview first) and tests FIX_ADD_PLAN §6
  "Preview #6"; P8 (signed `1.0.0-beta.4`) only with his OK after that.
- Last updated: 2026-10-08 (P38)

## Agent A — `work/phase-14-background` (P34; later P38)

- P38: merged A → B → C on `work/phase-14-integration` (2026-10-08; Overview).
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

- Status: READY FOR MERGE — P36 OWNER CHECK, P37 OWNER CHECK; last code commit `10957b7` on
  `work/phase-14-sites`, all three CI runs green (below). Started 2026-10-08; base commit
  `c8fcd33` = `origin/work/phase-14-integration`; folder `/data/YFT-B`. OWNER ANSWERS: none
  (defaults `TIKTOK_QUALITIES=DESKTOP`, `REREAD=2`). Not merged to main; P38 merges.
- Starting state (2026-10-08, `c8fcd33`, Agent B scope command): 1220 tests, 0 failures, 66
  skipped (extractor-sites 196, extractor-generic 19, core-model 98, core-browser 133, core-media
  28, app 746/66 skipped); lint 0 errors (95 warnings); `:app:compileDebugAndroidTestKotlin` OK.
- P36 — TikTok: Download on the For You feed and video pages: OWNER CHECK
  - Result: `FocusedVideoProbe` reads the focused video's TikTok card when no video link is
    beside it (id from `xgwrapper-<n>-<15–22 digits>`, author from the card's `/@` link, else
    `https://www.tiktok.com/@/video/<id>`); `TikTokPageParser` reads `webapp.video-detail`, else
    `webapp.reflow.video.detail`; `TikTokExtractor` asks a phone page without qualities once
    more with `HeadlessIdentity`'s desktop agent (fallback: the phone page's address as the one
    quality; `PAGE`/Home desktop lookups ask once); the media request keeps the WebView cookie
    with the page answer's TikTok cookies replacing same-named ones and added when missing, and
    the agent that fetched that page.
  - Plan adapted: (1) live, `tiktok.com/video/<id>` without `@` redirects to `/404`, so
    `TikTokUrls` also makes author-less links (`/video/<id>`, `m.tiktok.com/v/<id>.html`)
    canonical as `/@/video/<id>`. (2) The For You card has no `/@` link today (desktop layout),
    and the phone layout the app's Chrome-like phone identity gets has no `xgwrapper` at all:
    there the script reads the id from the active slide's own page data (read only; verified
    live that its author is the slide's `/@` link). (3) A video link beside the focused video
    wins only when it names the card's id (feeds keep links of other videos nearby).
  - Live check (2026-10-08, markers only): `/foryou` 200, `__UNIVERSAL_DATA_FOR_REHYDRATION__`,
    feed drawn by script (desktop: `recommend-list-item-container` ×7–9, `feed-video` ×2,
    `xgwrapper-0-<19 digits>`, 0 `/video/` links; phone: `video-slide-active`, no
    `xgwrapper`); video page phone agent → `webapp.reflow.video.detail`, `bitrateInfo` 0;
    desktop → `webapp.video-detail`, `bitrateInfo` 5; media host 206 with the page answer's
    cookies, 403 without or with a stale `tt_chain_token`.
  - Validation (2026-10-08): 1229 tests, 0 failures, 66 skipped (extractor-sites 202,
    core-browser 136, app 746; +9); lint 0 errors (95 warnings); androidTest compiles; line
    check empty.
  - Regression proof: old four main files with the new tests → 11 failures (TEST_MATRIX
    "Agent B — P36, P37"); restored from `/data/bak/P36`, `cmp` equal.
  - CI (`9689062`): checkpoint validation success (https://github.com/Alalkipgen/YFT/actions/runs/37790464506), emulator smoke success
    (https://github.com/Alalkipgen/YFT/actions/runs/37790464540), Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37790464487).
  - Owner check (VPN; TikTok is banned in India): `/foryou` → Download → qualities → the file
    plays; a profile's video; a pasted link on Home.
- P37 — Other sites: fresh links instead of HTTP 410: OWNER CHECK
  - Result: candidates carry where their link came from (`LinkOrigin`: page script, player
    request, page read again) and its age/expiry (`LinkExpiry`); the player's own request of the
    same file wins over the script's link (`FreshLinks.playerFirst`), and the normalizer's merge
    keeps the player's address. On a gone link (HTTP 410/403/404 of a browser video) Quick
    Download tries, in order: the page's newest link of the same video, the player's link, a
    quiet re-read of the page with the tab's agent and same-site cookies, no cache
    (`TabPageReader`, `REREAD=2`), then the next video; the sheet says "The first link is gone —
    using a fresh link". When nothing is left: "Reload page and try again" reloads the tab
    without cache, waits up to 15 s for the same video with a new link and opens Quick Download
    with it (else a notice). Details lists every attempt with link lines (origin, age, expiry),
    never the address. A row the page stated (a setup's "720p") whose file check answers
    "gone" is not offered, so the chain runs at once; a link gone only at Download (a row known
    by its size is not checked) runs the same chain and the fresh row downloads by itself.
  - Plan adapted: (1) the next video excludes the same file under another signature (same
    unsigned path). (2) `CandidateNormalizer.merge` also keeps the player's address; the old
    dedupe expectation changed (token=old kept). (3) The re-read scans the HTML with
    `HtmlMediaScanner`; a page without player data (a notice/error page) is not used. (4) Reload
    waits ≤15 s for the same video with a link not tried and not ad-like; the cache mode returns
    to default after the load finishes. (5) Link expiry reads `expiresAtEpochMs`, else numeric
    `validto`/`valid_to`/`expires`/`expire`/`exp`/`e`/`x-expires` ≥ 1e9.
  - Live check (2026-10-08, markers only, read twice like the quiet re-read):
    `commons.wikimedia.org` file page 200/200, `<video>`, `<source>`, JSON-LD `VideoObject`, 7
    media links (4 with a query), same on both reads, no notice; `archive.org` details 200/200,
    `og:video`, sources list, 1 link, no notice.
  - Validation (2026-10-08): 1255 tests, 0 failures, 66 skipped (extractor-sites 202,
    extractor-generic 20, core-model 106, core-browser 138, core-media 28, app 761; +26); lint 0
    errors (95 warnings); androidTest compiles (`FreshLinkInstrumentedTest`); line check empty.
    After the stated-row fix: 1257 tests, 0 failures, 66 skipped (app 763; +2); lint 0 errors
    (95 warnings); androidTest compiles; line check empty.
  - Regression proof: old nine main files (with a shim for new names) and the new tests → 18
    failures (TEST_MATRIX "Agent B — P36, P37"); restored from `/data/bak/P37`, `cmp` equal.
    Stated-row fix: the old view model with the two new tests → 2 failures; restored, `cmp`
    equal.
  - CI (`1186e3a`): checkpoint validation success (https://github.com/Alalkipgen/YFT/actions/runs/37798710365), Preview APK success
    (https://github.com/Alalkipgen/YFT/actions/runs/37798710411), emulator smoke failure (https://github.com/Alalkipgen/YFT/actions/runs/37798710450): 32 tests, 1 failure —
    `FreshLinkInstrumentedTest` read the sheet while it showed the link's stated row, before
    the file check answered. The test now waits for the attempt's end (its own condition, then
    an unchanged state for 500 ms).
  - CI (`0144c92`, that wait only): checkpoint validation success
    (https://github.com/Alalkipgen/YFT/actions/runs/37801131669), Preview APK success
    (https://github.com/Alalkipgen/YFT/actions/runs/37801131820), emulator smoke failure (https://github.com/Alalkipgen/YFT/actions/runs/37801131854): "the sheet did not
    settle" — an app gap the real WebView showed: the setup's "720p" made a stated row, and a
    410 at its file check kept the row, so no fresh link was looked for. Fixed in
    `QuickDownloadViewModel` (stated row gone, link gone at Download; +2 tests).
  - CI (fix, `10957b7`): checkpoint validation success (https://github.com/Alalkipgen/YFT/actions/runs/37804865935), emulator smoke
    success (https://github.com/Alalkipgen/YFT/actions/runs/37804865954; 32 instrumented tests, 0 failures, `FreshLinkInstrumentedTest`
    included, 0 FATAL), Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37804865937).
  - Owner check: the Preview #5 site whose video answered HTTP 410 → Download → a working video
    without a manual reload (or the fresh-link line); else Try again, then "Reload page and try
    again"; a Details screenshot if it still fails; Javtiful still downloads.
- Hand-offs: none

## Agent C — `work/phase-14-fast-merge` (P35)

- Status: READY FOR MERGE — last code commit `5f62962`, CI green (started 2026-10-08, base
  `c8fcd33`):
  - checkpoint validation https://github.com/Alalkipgen/YFT/actions/runs/37801106992
  - emulator smoke https://github.com/Alalkipgen/YFT/actions/runs/37801106973
  - Preview APK https://github.com/Alalkipgen/YFT/actions/runs/37801107005
- Starting state (2026-10-08, `c8fcd33`, Notion sandbox with Gradle `-Xmx1280m
  -XX:MaxMetaspaceSize=640m` and a 4 GiB swap file): `./gradlew --no-daemon --continue
  :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; core-download 150, core-model 98,
  app 746 tests (66 skipped), 0 failures; lint 0 errors.
- P35 — Faster merge for long videos: DONE (2026-10-08) — OWNER CHECK on the phone
  - Result: `FAST_MERGE=ON` (`AudioVideoMuxCompatibility.FAST_MERGE_ENABLED`).
    `AndroidMp4AudioVideoMuxer` (Agent A's `DownloadRuntimeModule` keeps
    `AndroidMp4AudioVideoMuxer()`, so the app uses it without an app change) merges an MP4 video
    (`avc1`/`avc3`) and an MP4/M4A sound (`mp4a`) by `StreamCopyAudioVideoMuxer`.
    `Mp4TrackReader` + `TrackInit` read each input in plain Kotlin over `FileChannel`
    (fragmented `moof`/`traf`/`tfhd`/`tfdt`/`trun` with `trex` defaults, or plain
    `stsz`/`stz2`/`stts`/`ctts`/`stss`/`stsc`/`stco`/`co64`; unknown boxes skipped; encryption,
    two tracks in a file, another codec, a broken size, sample data outside an `mdat`, an edit
    list other than none/one/empty+one → "not supported"). `StreamCopyLayout` puts the samples
    in chunks of about one second per track, alternating by time; the writer copies them
    through one 4 MiB direct buffer into the destination (the P27 descriptor with `Os.pwrite`,
    or the temporary file): `ftyp`, one `mdat` (64-bit size above 4 GiB), then `moov` (`mvhd`;
    per track `tkhd`, `edts/elst`, `mdhd` with the input's timescale, `hdlr`, `vmhd`/`smhd`,
    `dinf`, `stbl` = the input's `stsd` + new `stts`, `ctts` version 0, `stss` (video),
    `stsc`, `stsz`, `stco` or `co64`); all sizes are known before writing, no seek back.
    `MediaExtractorStreamCopyCheck` reads the result back (two tracks, the inputs' MIME, size,
    sample rate, channels, durations within a frame, the first 8 samples, sync samples at 1/3
    and 2/3, every sample from the last sync sample on: bytes (AVC with start codes) and
    times); a failed check empties the output and today's way runs once; the note ("not
    supported, …", "check failed, …", "failed, <exception>") goes into the log and a later
    failure's detail. Progress = bytes copied ÷ total, per chunk. Today's way
    (`MediaMuxerAudioVideoMuxer`, also for WebM and as the fallback) reads each sample's time
    and flags once (5 calls per sample), one reusable buffer, progress at most every 250 ms.
    Log line: "Merge done (…): …; <size>; stream copy: N video + M audio samples, parse …,
    data …, index …, check …; cpu …, wall …" (or "MediaMuxer (stream copy: <note>): …,
    open …, read …, write …, finish …").
  - Plan adapted: (1) negative composition offsets are shifted into `ctts` version 0 and the
    edit list's `media_time` instead of `ctts` version 1 (Android 7's MediaExtractor rejects
    version 1). (2) The instrumented test checks the stream copy's sample times against the
    inputs (and against today's file after each track's first sample), because MediaMuxer moves
    the start of a video with B-frames: it takes each sample's presentation time as its decode
    time, and the test video starts 59 ms late in today's file (192,777 µs instead of
    133,333 µs); the stream copy keeps the inputs' times. (3) Sound sync flags are compared with
    today's file only: MediaExtractor marks only the first sample of an input fragment as sync.
    (4) Today's way is measured with `AndroidMp4AudioVideoMuxer(fastMerge = false)` on the same
    inputs (and P27's line on the old code); the 1-hour-sized input is P27's tracks repeated to
    265,410 samples.
  - Tests: `StreamCopyAudioVideoMuxerTest` (15), `StreamCopyMergeEngineTest` (1), with the test
    MP4 builder `Mp4TestFiles` and the independent reader `Mp4Dump`; instrumented
    `StreamCopyMergeInstrumentedTest` (3: test tracks, 20 minutes, 1-hour-sized).
  - Validation (2026-10-08, the Agent C command): BUILD SUCCESSFUL; core-download 166 tests,
    core-model 98, app 746 (66 skipped), 0 failures; lint 0 errors; line check clean (printed
    nothing).
  - Regression proof: with `StreamCopyAudioVideoMuxer.mux` calling today's way at once (no fast
    path; backup `/data/bak/P35/`), 13 of the 16 new JVM tests fail: `StreamCopyAudioVideoMuxerTest`
    "an MP4 video and an M4A sound are stream-copied, not merged by today's way", "a plain M4A
    with uneven samples gives tables that match every sample", "chunks of about a second
    alternate between video and sound in time order", "encrypted or odd tracks are not supported
    and today's way merges once", "sample data outside an mdat is not supported", "a failed check
    empties the output and today's way merges once", "a failure of today's way after the stream
    copy keeps both reasons", "progress moves on by the bytes copied and ends at 100 percent", "a
    stopped download stops the stream copy and leaves no output", "the destination's descriptor
    gets the same file and stays open", "edit lists keep both tracks where the inputs presented
    them", "negative composition offsets are shifted into ctts version 0 and the edit list", and
    `StreamCopyMergeEngineTest` "the merge line names the stream copy, its samples, phases and CPU
    time"; restored with `cp`, checked with `cmp`.
  - Measured times (CI emulator, API 34, `5f62962`, `YFT-DIAG fast-merge`): 1-hour-sized input
    (265,410 samples, 67 MB): today's way 14.7 s (open 174 ms, read 4.9 s, write 9.6 s, finish
    45 ms; CPU 6.8 s) → stream copy 1.03 s (parse 430 ms, data 268 ms, index 92 ms, check
    241 ms; CPU 811 ms), 14.3× faster. 20 minutes (70,785 samples, 17 MB): 3.74 s → 254 ms
    (14.7×). Earlier runs: `ff59e2c` 16.0 s → 1.33 s and 4.05 s → 0.42 s; `0a18c19` 21.3 s →
    1.84 s and 6.5 s → 0.49 s. P27's line through the engine: in place merge 850 ms (5.7 s
    before P35), app storage merge 704 ms (5.9 s before). ExoPlayer: 1-hour-sized file
    4,500,121 ms (today's file 4,500,143 ms), seek to the middle at 2,250,060 ms. The emulator
    runs the debug build, where Kotlin loops are slow; the tracks' 67 MB sit in its page cache.
    On a phone a 1-hour 720p merge copies about 1.3 GB, so the wait is the storage's write speed
    plus the check: about 10–20 s expected (owner check).
  - CI (`5f62962`, all green; "Instrumentation results: tests=32 failures=0 errors=0
    skipped=0"):
    - checkpoint validation https://github.com/Alalkipgen/YFT/actions/runs/37801106992
    - emulator smoke https://github.com/Alalkipgen/YFT/actions/runs/37801106973
    - Preview APK https://github.com/Alalkipgen/YFT/actions/runs/37801107005
  - Owner check: the same 1-hour YouTube live recording at 720p → "Merging … %" ends in about
    10–20 s after the tracks (was about 2 minutes); it plays and seeks in the Library and in
    another player; a 1080p video and a 2K/4K WebM still save.
- Hand-offs: none (no app, Gradle or workflow change; Agent A's `DownloadRuntimeModule` keeps
  `AndroidMp4AudioVideoMuxer()`, which now stream-copies by default).
