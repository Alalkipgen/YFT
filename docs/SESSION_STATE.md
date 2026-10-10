# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

## Master backup prototype — `spike/master-extractor-backup`

- Owner-approved isolated code spike, not a Phase 14/15 task or a merge candidate.
- Base: stable `origin/main` `a9eea7ba8d9f3d67442ffc3a51f2ad9e00c4a6a9`; Phase 1 R1 merged
  `origin/main` `34a4189002b7c9aaf6085ae4adab27b343ca28dc` into the spike (true merge; main
  itself untouched). R1/R2 records: `PROGRESS.md`.
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
- Stdin-only live harness milestone (2026-10-09): recompiled 46 unchanged pure production
  sources, including CaptureFrameReader, MasterBrowserSession, MasterFallbackEngine and the
  real OkHttpMediaValidator, with repo Kotlin 2.0.21 / Java 17. All 46 files were byte-compared
  to b07bd5d2 and matched. Thirteen Maven artifacts were checked against published hashes.
  A missing compiler-host annotation dependency was fixed only in the sandbox classpath.
  No Gradle/app/production/CI file changed. This is an internal diagnostic, not a new APK
  or full Android WebView caller test. Its recoverable RESPONSE_CHANGED input is test-only.
  Raw collector packets and original monotonic sample times pass over stdin to the unchanged
  production frame reader/session. No manual store.playing authorization, JS play/seek,
  altered frame time, fake playing address or persisted packet/body/cookie is used.
  Native Pause -> Play and read-only natural readiness precede a fresh <=2.5-second capture;
  readiness reads are not submitted to session.accept. Visible optional login-teaser Close
  is dismissed natively, never by bypassing an overlay or signing in.
  Instagram repeated native resume produced one trusted receipt and 1080x1920/readyState 4.
  Fresh samples advanced 1.609261 -> 1.966620 s in 359.8 ms, generation 1, no DRM.
  Real production session accepted two raw frames: authorizedPlayback=true, 68 bounded
  requests and eight payloads. Real Master ran for 111 ms and returned NEEDS_SELECTION with
  the diagnostic's requested public shortcode Dcwk7e1yHaY. It refused to guess: zero probes,
  validated candidates, TLS handshakes or body bytes. This proves live capture/session
  authorization and engine selection refusal, NOT extraction success, HTTPS prefix,
  Android caller integration or full download. Inspect actual caller expected-ID/focus
  mapping before interpreting this refusal; do not delete an expected ID or weaken selection.
  TikTok native main-surface Pause was observed at 720x1280/readyState 4. The first diagnostic
  stopped at native_resume_not_observed; after test-only tap spacing/actual-resume waiting,
  another attempt timed out before capture at decoded-player readiness (25 s). Neither
  supplied live frames to the engine. P40/live Master Play-and-Capture proof remains pending.
  Facebook's second non-teaser-caption NASA sample (1414737229683186) rendered 640x360,
  duration metadata 83.283 s, readyState 4. Native Pause selection stopped the diagnostic.
  Inspection found a visible role-button named Play video, not Pause. Activate it natively
  and inspect the resulting controls before retrying; this is not full-play/download proof.
  Compiled classes, verified runtime, driver, source hashes and sanitized failures were
  backed up as session files. The passed same-APK two-run Android gate remains unchanged.
- Caller-faithful live diagnosis (2026-10-09): the actual app factory registers TikTok,
  Facebook, Vimeo and YouTube only. Their four unchanged URL helpers were compiled with the
  existing harness (50 unchanged production files). Runtime probes returned no registry
  match/expected ID for Instagram or X, TikTok ID 7670337149526379789 and Facebook ID
  1414737229683186. This parameter now comes from those production delegates, not removal
  of a requested ID to force success. Runtime flags/full Android caller remain unverified.

  Instagram generic mode: actual native Pause/Play, one trusted receipt, decoded 1080x1920,
  readyState 4. Fresh samples 2.277588 -> 2.633725 s in 357.9 ms. Session authorized playback
  with 71 requests/eight payloads. Unchanged reader discovered 71 candidates, zero MAIN-role
  candidates/groups and no secure playing address (blob). Real Master returned NEEDS_SELECTION
  in 59 ms. Zero probes, TLS handshakes, body bytes or validated candidates. The measured
  limitation is focused-address/main-role correlation, not absent or fabricated playback.
  No playing address or MAIN role was invented; no Instagram extraction/download pass.
  The earlier manual-shortcode result remains a separate diagnostic, not actual caller policy.

  Facebook screenshot and accessibility confirmed the optional See more on Facebook login
  teaser over the public video. The apparent Play video was aria-hidden fallback, correctly
  not clicked. The real Close is dismissible natively. Subsequent native Pause actionability
  still timed out; no FB live packets/engine proof. No sign-in or force-click was attempted.
  Source/compiled runtime, caller probe and sanitized results are backed up. Production,
  existing extractors, downloads, CI and security guards remain unchanged. TLS/full download,
  mux, all resolutions, Android live caller, Master CI green and merge readiness are unverified.
- Native public-player diagnostic checkpoint (2026-10-09): test-only native mouse hover
  resolved Facebook's hidden playback controls after the optional login teaser was closed
  normally. No force-click, login, JS play/seek, fabricated MAIN role or fake playing URL.
  The observer now forwards every actually observed native GET response, without a media-MIME
  prefilter; unchanged production MasterBrowserSession performs its own URL classification.

  Facebook NASA 1414737229683186: actual native Pause/Play, one trusted receipt, 640x360,
  readyState 4, sourceObjectType=MediaSourceHandle, no currentSrc/src address. Fresh samples
  2.871860 -> 3.225809 s in 355.4 ms, generation 1, protected=false. Production authorized
  playback and accepted eight payloads but zero media requests/candidates. Real Master returned
  NEEDS_PLAYBACK in 30 ms; zero probes/TLS/body bytes. Here the result does NOT mean playback
  was absent: no usable media address reached this page-level producer. Worker/opaque-handle
  coverage remains unverified; do not invent a URL or declare universal FB incompatibility.

  TikTok NASA 7670337149526379789: native Pause/Play, one trusted receipt, 720x1280/readyState 4,
  blob source. Fresh samples 3.766892 -> 4.196915 s in 434.6 ms, generation 1, protected=false.
  Production authorized playback, accepted three media requests/eight payloads and discovered
  three candidates, zero MAIN-role candidates/groups, no secure playing address. Real Master
  returned NEEDS_SELECTION in 38 ms; zero probes/TLS/body bytes. This is genuine live capture
  and a safe selection refusal, not a Master extraction pass or a latest-P40 app/phone proof.

  X's fresh caller-faithful repeat reached native Play selection but stopped before capture
  when actual unpaused/decoded readiness did not arrive within ten seconds. Its earlier
  native-click/two-progress desktop collector proof remains valid; no new live-engine pass.
  YouTube/Reddit public access gates and Vimeo/Dailymotion/Threads unverified player paths
  remain as recorded in the nine-site preflight. No supported/unsupported blanket claim.
  The untouched producer's focus/address/MAIN-role limitations are recorded, not bypassed.
  Production changes to address those limits are outside the frozen scope. The same-APK
  Android two-pass gate remains passed. Default/release capture, 2.5-s bound, separate progress,
  DRM and generation checks, existing extractors, downloads and CI remain unchanged. No TLS
  media-prefix, full download/mux, all-quality, Master CI-green or merge-readiness claim.
- Next: complete native-play observation for the remaining accessible public players, then
  continue the recovered test-only harness through focus selection/HTTPS-prefix checks.
  Keep bot/login/private/DRM failures terminal and distinguish teaser/iframe/MSE limitations.
  TLS/full download/mux, all-resolution coverage, Master CI green and merge readiness remain
  unverified. Run builds and emulator separately. Main/Phase 15/TikTok code remains untouched.
  No merge, tag, release or owner-phone APK handout approval.

Phase 15 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P44.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P44 only)

- Phase: 15 — Preview #6 field fixes (TikTok from TikTok's own page in the browser and on Home,
  with every failure explained in Details; YouTube % and speed within seconds; Delete file in
  Downloads; the page's own video, never the pre-roll ad, on other sites). Plan:
  `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-15-integration` = `main` `a9eea7b` + the plan commit. Agent A
  `work/phase-15-tiktok` (P39, P40), Agent B `work/phase-15-downloads` (P41, P42, later P44),
  Agent C `work/phase-15-ads` (P43); all start from `origin/work/phase-15-integration`. Merge
  order B → C → A (P44), then Preview #7; P8 (signed `1.0.0-beta.4`) only with the owner's OK.
  Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #6 (2026-10-09, run 37811403962, `4da3e61`): TikTok (VPN) "changed
  its page format" on every browser video and many Home links (Quality unknown rows, a
  "Download · 1.4 MB" row that fails without a step); "bypass TikTok like YouTube, whatever
  works"; a YouTube live recording waits long before the % moves; Delete for the file itself;
  an adult site's sheet sometimes shows the 0:30 pre-roll ad after "The first link is gone".
  Root causes R25–R34 in FIX_ADD_PLAN §4; owner decisions G1–G8 in §3 (defaults: three agents,
  Chrome agents for TikTok, the browser's TikTok cookies on Home, watermark hidden, hidden
  TikTok page on, fast start on, delete with a dialog, strict ad rule).
- Live checks in Plan Mode (2026-10-09, US sandbox): TikTok answers normally (phone page
  `webapp.reflow.video.detail` with one quality; desktop and headless pages `webapp.video-detail`
  with 4 qualities); media files need the same answer's `tt_chain_token` and a `www.tiktok.com`
  Referer, except the cookie-free `aweme/v1/play` address. The owner's VPN country gets other
  answers: his phone is the final proof, so P39 adds Details to every TikTok failure.
- CI: pushes that change only `docs/**` or `*.md` start no checkpoint validation
  (`paths-ignore`); "green CI" means the newest commit that changed code.
- Rules: ADR-006 any working technique for public videos (owner, confirmed for TikTok on
  2026-10-09); no DRM/paywall/private/age-gate bypass; adapters never sign in; agents never
  automate a page's age or identity check or a puzzle. Never print/commit cookies, tokens,
  visitor data, signed media/image URLs or keys. Keep testTags, Kotlin lines ≤ 100, WebView on
  the main thread. No reset --hard/clean/stash. One Gradle command at a time; temporary files
  outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK
  `/data/toolchains/android-sdk` (platform 35, NDK 27.3.13750724, CMake 3.22.1);
  `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with SSH using the key
  named in `/data/.ssh/CURRENT_KEY` (pushed on 2026-10-09); when it is missing or refused make
  a **new** key (never search for old keys), show the owner the public line and wait until he
  adds it as a deploy key with write access.
- Phase 14 record (per-task Results, validation, CI runs, P38 merge notes):
  `git show a9eea7b:docs/SESSION_STATE.md`. Phase 13: `git show 5a5bddb:docs/SESSION_STATE.md`.
- Phase 15 start: P38's full validation on `4da3e61` — 1532 tests, 0 failures, 66 skipped; lint
  0 errors; `:app:assembleRelease` OK.
- P44 (2026-10-09, Agent B as integrator, `/data/YFT-B`): B → C → A merged with `--no-ff`
  (`2661062`, `f3f80ce`, `257af94`); the only conflict, `DefaultVariantResolver.kt` and its test
  (C's owner-override change of A's files), kept C's side (= A's `8b0bc93` file plus the `error`
  lines). C → A hand-offs (a) and (b) open: (a) was tried and reverted (it breaks P24's
  owner-case test that counts an ad as another video), (b) `vastAds.onAnswer` in the browser. Full
  validation: 1717 tests, 0 failures, 66 skipped (app 900, core-browser 149, core-data 33,
  core-download 184, core-media 33, core-model 122, extractor-api 36, extractor-generic 21,
  extractor-sites 239; 1532 at the start); lint 0 errors (98 warnings: A's 3 androidx.webkit
  notices); `:app:assembleRelease`: OK (lint vital needed a 2.5 GiB Gradle heap on the 4 GiB
  sandbox); line check clean after wrapping one line of B's `DeleteFileScreenTest`.
- Last pushed checkpoint: `1ef8c86` P44 merge B, C, A — full validation green, on
  `work/phase-15-integration`; CI of `1ef8c86`: checkpoint validation
  [37893874474](https://github.com/Alalkipgen/YFT/actions/runs/37893874474), emulator smoke
  [37893874480](https://github.com/Alalkipgen/YFT/actions/runs/37893874480), Preview APK = **Preview #7**
  [37893874478](https://github.com/Alalkipgen/YFT/actions/runs/37893874478) (Artifacts › `yft-preview-apk`), all green. This
  docs commit adds the links; `main` is fast-forwarded to it at the owner's request (no tag).
- P45 (2026-10-09, Agent B alone at the owner's request, `/data/YFT-B` re-cloned after a
  sandbox reset, new key `yft_b_202610092044`): the owner's Preview #7 test of the adult site
  (Other videos row with ads, HTTP 474 at the file check, HTTP 410 at the manifest and "Next
  video"). Done: the sheet shows only the tapped video; a page's links are asked like the
  browser's; any HEAD error gets the range GET; refused links (403, 410, 412, 452–499) are asked
  again through WebView (`BrowserReads`, also for HLS playlists); new messages and Details
  lines; proven ads out of the browser's lists. Live checks of the site were refused by the
  sandbox's reviewer, so public reports (yt-dlp #17642, PR #16794) back R37. Open: hand-off (b)
  and a Cronet option (FIX_ADD_PLAN §7). Full validation: 1729 tests, 0 failures, 66 skipped (app 903, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 239; 1717 before P45); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean.
- Last pushed checkpoint: `1d2f27a` P45 on `work/phase-15-integration`; CI: checkpoint validation
  [37955002943](https://github.com/Alalkipgen/YFT/actions/runs/37955002943), emulator smoke [37955002962](https://github.com/Alalkipgen/YFT/actions/runs/37955002962), Preview APK =
  **Preview #8** [37955002856](https://github.com/Alalkipgen/YFT/actions/runs/37955002856) (Artifacts › `yft-preview-apk`), all green. `main` stays at `69bf022` until the owner says `MAIN=OK`.
- P46 (2026-10-10, Agent B alone; `/data/YFT-B` re-cloned after a sandbox reset, new key
  `yft_b_202610091709`, JDK 17 and SDK 35 in `/data/sdk`): the owner's TikTok report (one quality
  in the browser, public posts called private, check messages). Owner chose A, then B. Done: a
  page's TikTok status is not final; the browser joins the desktop page's qualities; Show check;
  the public download service after TikTok's pages. Not done: TikTok's app API. Full validation: 1746 tests, 0 failures, 66 skipped (app 909, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 250; 1729 before P46); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean.
- Last pushed checkpoint: `462d508` P46 on `work/phase-15-integration`; CI: checkpoint validation [37971914024](https://github.com/Alalkipgen/YFT/actions/runs/37971914024), emulator smoke [37971914007](https://github.com/Alalkipgen/YFT/actions/runs/37971914007), Preview APK = **Preview #9** [37971914134](https://github.com/Alalkipgen/YFT/actions/runs/37971914134) (Artifacts › `yft-preview-apk`), all green.
  P45 + P46 on `main`: `main` fast-forwarded to the P46 docs commit with the owner's approval (2026-10-09: "merge P45 & P46 to main"), `69bf022` → this commit (fast-forward, no merge commit). Preview #9 is the same code.
- P47 (2026-10-10, Agent B alone; sandbox reset again: re-cloned, new key, JDK 17 and SDK 35
  reinstalled): the owner's Preview #9 test (one TikTok quality on most videos; "1280p · Full
  HD" on Download as for 720 × 1280). Done: TikTok's answers are joined by height; the desktop
  page is asked when one height comes in two codecs; Download as names the short side.
  Full validation: 1750 tests, 0 failures, 66 skipped (app 910, core-browser 150, core-data 33, core-download 185, core-media 37, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 253; 1746 before P47); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; Kotlin line check clean.
- Last pushed checkpoint (P47): `0bfb437` on `work/phase-15-integration`; CI: checkpoint validation [37993854819](https://github.com/Alalkipgen/YFT/actions/runs/37993854819), emulator smoke [37993854829](https://github.com/Alalkipgen/YFT/actions/runs/37993854829), Preview APK = **Preview #10** [37993854810](https://github.com/Alalkipgen/YFT/actions/runs/37993854810) (Artifacts › `yft-preview-apk`), all green.
  `main` fast-forwarded from `2eafd62` to this P47 docs commit with the owner's approval (2026-10-10: "Main ကို Push & Commit"); Preview #10 is the same code.
- P48–P50 (2026-10-10, Agent B alone, the owner's request after Preview #10; sandbox reset:
  new key `yft_b_20261009225413`): X adapter (embed answer, every MP4 with sound), Instagram
  adapter (app API / web query / page / embed; Home with YFT's browser's Instagram cookies),
  HLS qualities with their sound apart merged into one MP4 (`PlaylistTrackTransferRunner`).
  Full validation: 1784 tests, 0 failures, 66 skipped (app 913, core-browser 150, core-data 33, core-download 188, core-media 38, core-model 125, extractor-api 36, extractor-generic 21, extractor-sites 280; 1750 before P48–P50); lint 0 errors (98 warnings, as before); `:app:assembleRelease` OK; `:app:compileDebugAndroidTestKotlin` OK; Kotlin line check clean.
- Last pushed checkpoint (P50): `fdb3769` on `work/phase-15-integration`; CI: checkpoint validation [38009471238](https://github.com/Alalkipgen/YFT/actions/runs/38009471238), emulator smoke [38009471040](https://github.com/Alalkipgen/YFT/actions/runs/38009471040), Preview APK = **Preview #11** [38009471066](https://github.com/Alalkipgen/YFT/actions/runs/38009471066) (Artifacts › `yft-preview-apk`), all green. `main` fast-forwarded from `8284d80` to this P50 docs commit with the owner's approval
  (2026-10-10: "Main ကို Push လိုက်"); Preview #11 is the same code.
- Next: the owner tests Preview #11 (FIX_ADD_PLAN §6 "Preview #11") and sends Details of
  anything that fails; P8 (signed `1.0.0-beta.4`) only with his OK.
- Last updated: 2026-10-10 (P50)

## Agent A — `work/phase-15-tiktok` (P39, P40)

- Status: READY FOR MERGE — P39 DONE (2026-10-09), P40 DONE (2026-10-09; base commit
  `8b0bc93`, the P39 checkpoint).
- P40 set-up: clone `/data/YFT-A`, key `/data/.ssh/yft_a_202610090329` (added by the owner),
  toolchain under `/data/opt`; starting state green (1254 JVM tests, 0 failures).
- Base commit: `d0bc7f7` (= `origin/work/phase-15-integration`, the plan commit); folder
  `/data/YFT-A`; push over SSH with the key named in `/data/.ssh/CURRENT_KEY` (sandbox reset on
  2026-10-09: new key `/data/.ssh/yft_a_20261009`, added by the owner as a deploy key; JDK 17,
  SDK 35, NDK and CMake reinstalled under `/data/tools`; a 4 GiB swap file keeps the full
  validation from being killed for memory).
- Starting state (Agent A scope, before any edit; the full command of FIX_ADD_PLAN 0.3 for
  Agent A): BUILD SUCCESSFUL — 1207 JVM tests, 0 failures, 66 skipped (app 807, core-browser
  138, core-media 28, extractor-api 32, extractor-sites 202); lint 0 errors, 95 warnings;
  androidTest Kotlin compiles.
- P39 Result (all 8 steps; last code commit `49fa1b7`):
  - Details (step 1): every TikTok lookup keeps Details lines, success or failure — page agent,
    HTTP status, KB, landed on video/home/challenge/login/other page, data key, JSON read or
    `error <Class> at char N`, post id, qualities, play/download address, file checks and
    `error: <Class> at step <step>`; hosts only. They fill `SiteAdapterOutcome.Failed.details`
    and the new `PageVideoLookup.details` (default empty), which `lookupState` shows as the
    sheet's Details (browser page and focused lookups; Home shows the adapter's Details).
  - Reasons (step 2): a link landing on TikTok's home page or a page without a post id →
    `PRIVATE_OR_UNAVAILABLE` "This TikTok link does not open a video. It may be removed or
    private — open it in YFT's browser to check."; a challenge page → `BOT_CHECK`;
    `RESPONSE_CHANGED` only for an unknown data shape, text "<Site>'s page could not be read.
    Tap Details to see why, or Try again." (no "Falling back to generic detection").
  - Requests (step 3): `OkHttpExtractorClient` leaves out cookie pairs and headers OkHttp refuses
    ("session pairs left out: N", "headers left out: N"), carries a per-lookup cookie jar across
    short-link redirects, ends too many redirects as `HTTP_STATUS` and any other error as
    "error: <Class> at step <step>".
  - Agents (step 4, `TT_AGENT=CHROME`): the phone page with the WebView's own agent, then the
    desktop page (Windows Chrome with the WebView's Chrome version, never "YFT") when the phone
    page fails for a non-final reason or lists fewer than 2 qualities; the answer with more
    working qualities wins.
  - Data (step 5): the data script found by its `id` attribute (also when an earlier script
    names it), text after the data object cut, any `__DEFAULT_SCOPE__` key holding
    `itemStruct`, one lenient read of entity-encoded JSON, another post's id skipped.
  - Files (step 6): rows from every `bitrateInfo` address, `playAddr` and `aweme/v1/play`, each
    checked with `Range: bytes=0-0`, the answer's cookies and Referer `https://www.tiktok.com/`
    (at most 8 checks, 3 at a time, 5 s each): 206 → exact size, a refusal → the next address,
    none → the quality is left out; labels 1080p/720p/540p, "H.265", H.264 first, duplicates
    once; `TT_WATERMARK=HIDE`: `downloadAddr` only when no other file opens, "With TikTok
    watermark".
  - Resolver (step 7): root cause of the owner's "Download · 1.4 MB" row — P38 handled the file
    check's answers on the caller's thread, the Download sheet's main thread; closing a range
    answer reads its unread bytes, which Android forbids on main, and P38 caught only
    IOException/IllegalArgumentException, so the error escaped without a step (a TikTok-shaped
    MP4 itself resolves on the JVM). Now `resolve` runs in `withContext(Dispatchers.IO)`, any
    unexpected error → a failure at its step, MP4 header-probe errors leave the row unmeasured,
    and a HEAD answer with 5xx is asked again with the range GET like 405/501 (4xx still fails).
  - Browser (step 8): `FocusedVideoProbe` returns the on-screen video's https `currentSrc`
    (at most 2048 characters); new `PlayerFileFallback`: when the TikTok adapter fails on a
    TikTok page, the player's file (the request matching `currentSrc`, else the newest TikTok
    media request) becomes the MAIN row with its cookies and Referer, banner "TikTok's page
    could not be read — showing the file its player is playing."; DRM or no player file → the
    failure with Details and Try again. `BrowserViewModel` uses it for page and focused lookups.
  - Plan adapted: the resolver's failure names its step and host; the exception class needs
    `VariantResolutionResult.Failure.error` in core-model media (Agent C's), see Hand-offs. The
    HEAD 5xx retry came from the live check (the 540p H.265 file's HEAD answered 504).
    `extractor-sites/build.gradle.kts` (A's folder) gained `kotlinx.coroutines.core` for the
    3-at-a-time file checks.
- Tests (P39, JVM, +47): extractor-sites `TikTokExtractorTest` 35 (+17), `TikTokRegressionTest`
  5 (new), `TikTokAgentsTest` 4 (new), `TikTokUrlsTest` 6 (+1), `SiteNavigationHeadersTest` 4
  (updated); extractor-api `BoundedJsonParserTest` 10 (+1); app `OkHttpExtractorClientTest` 13
  (+4), `OkHttpExtractorClientRegressionTest` 4 (new), `OkHttpExtractorNetworkTest` 6 (+1),
  `SiteAdapterCoordinatorTest` 11 (+1), `BrowserPlayerFileFallbackTest` 3 (new),
  `QuickDownloadViewModelTest` 45 (+1: `aFailedPageLookupShowsTheLookupsStepsAsDetails`, the
  "Done when" item "a forced failure shows its Details lines in the sheet"); core-browser
  `FocusedVideoProbeTest` 13 (+1); core-media `DefaultVariantResolverTest` 22 (+4).
- Regression proof: on main's `TikTokExtractor`, `TikTokPageParser`, `OkHttpExtractorClient`,
  `SiteAdapterCoordinator`, `SiteAdapterModule`, `BrowserViewModel` and `DefaultVariantResolver`
  (the tests that use the new APIs moved aside; restored with `cp`, checked with `cmp`)
  `TikTokRegressionTest` 5/5, `OkHttpExtractorClientRegressionTest` 4/4,
  `BrowserPlayerFileFallbackTest` 3/3 and the resolver's "responses are never read on the
  caller's thread, which Android forbids on main" and "an unexpected error ends as a failure at
  the step it stopped at" fail, and pass on the new code. "a file server that answers HEAD with
  a server error is asked for the file itself" fails on main's resolver with `HTTP_STATUS` 504
  at `FILE_CHECK` (backup `/data/bak/P39-head5xx/`).
- Live check (2026-10-09, US sandbox, polite, hosts only): video `@scout2015/6718335390845095173` —
  phone page 200 · 150 KB · video page · `webapp.reflow.video.detail` · 1 quality (540p, playAddr
  only); desktop page 200 · 387 KB · `webapp.video-detail` · 3 qualities, all 3 file checks 206
  (`v16-webapp-prime.us.tiktok.com`): 720p H.265 720×1280 hvc1 2,004,627 B · 540p 576×1024 avc1
  2,953,029 B · 540p H.265 576×1024 hvc1 1,728,265 B. Download sheet resolver: 720p and 540p OK with
  the same exact sizes; 540p H.265 → HTTP 504 at FILE_CHECK, because the file host answered the HEAD
  with 504. Re-check after the HEAD 5xx fix (same day; one page, 3 files, temporary code not
  committed): the file host still answers that HEAD with 504 (and `lower_540_0`, which YFT does not
  offer, with 404), and the real resolver now gives all three rows Success with exact sizes: 540p
  2,953,029 B, 720p H.265 2,004,627 B, 540p H.265 1,728,265 B. Short link `vt.tiktok.com/ZS2ueXNrd/`
  and dead link `vt.tiktok.com/ZSqq9zz9qq/` → `PRIVATE_OR_UNAVAILABLE` with the new "does not open a
  video" text (both 200 · 167 KB · landed on the home page · no data; the short link also seems
  gone). No live-check code was committed.
- Validation (2026-10-09, `49fa1b7`): Agent A command (FIX_ADD_PLAN 0.3) → BUILD SUCCESSFUL — 1254
  JVM tests, 0 failures, 66 skipped (app 821, core-browser 139, core-media 32, extractor-api 33,
  extractor-sites 229; +47 from 1207); `:app:lintDebug` 0 errors, 95 warnings (unchanged);
  `:app:compileDebugAndroidTestKotlin` OK; line check empty. The first run was killed for memory
  during the app tests (no swap on the new machine); the rerun with swap ran the rest, the finished
  tasks UP-TO-DATE.
- CI (`49fa1b7`, the last code commit; the docs-only P39 checkpoint starts no CI): checkpoint
  validation [37865869227](https://github.com/Alalkipgen/YFT/actions/runs/37865869227)
  (`yft-debug-apk`), emulator smoke API 34
  [37865869239](https://github.com/Alalkipgen/YFT/actions/runs/37865869239), Preview APK
  [37865869254](https://github.com/Alalkipgen/YFT/actions/runs/37865869254) (`yft-preview-apk`) —
  all green.
- Owner check (pending): Agent A's Preview APK (run 37865869254 › `yft-preview-apk`, with the
  VPN) — TikTok in YFT's browser → Download on a video page and on For You → qualities with
  sizes → the file plays; a `vt.tiktok.com` link on Home → qualities; anything that fails → a
  screenshot of Details.
- Backlog: live 2026-10-09, TikTok's file host answered HEAD 404 for `lower_540_0` while a range GET
  of it gave 206. YFT does not offer that file today (the `normal_540_0` row wins at 540p H.264),
  but a row with such an address would fail at FILE_CHECK with 404, because a 4xx HEAD answer still
  fails (the owner's rule for the 5xx fix); P40 or later could skip the HEAD for TikTok rows whose
  file the adapter already checked.
- P40 Result (all 7 steps; code in the P40 checkpoint, base `8b0bc93`):
  - Page data (step 1): `SiteExtractionRequest.pageData: SitePageData? = null` (extractor-api,
    additive): one post's JSON text, at most 64 KB (UTF-8), and its source — `TAB_SCRIPT`
    "tab · page script", `TAB_API_ANSWER` "tab · API answer", `HIDDEN_PAGE` "hidden page";
    `toString` names the source and size only. The TikTok adapter builds rows from data whose
    id is the link's post id without asking for the page (Details `data: tab · API answer ·
    JSON: read`, `post id: matches`, `answer: tab · 3 working qualities`) and checks the files
    as in P39 step 6 with the lookup's cookies and Referer `https://www.tiktok.com/`. Another
    post's id, data that is not JSON or no file that opens → P39's page read (`data: … not used
    · page read next`); a short link has no post id yet, so its page is read. Data from the
    hidden page is the last word (its page was read before).
  - Tab data (step 2): one asset, `core-browser/src/main/assets/tiktok/page-data.js` (modes
    `store` and `item`). Every TikTok lookup in the browser (the page's own lookup, Download,
    For You's video on screen, Try again) asks the screen to run `item` for the post id (P36's
    on-screen id, else the address) with `evaluateJavascript` on the main thread and waits at
    most 1 s. The compact item (P40's shape) comes from `__UNIVERSAL_DATA_FOR_REHYDRATION__`
    (any `__DEFAULT_SCOPE__` key), `SIGI_STATE`, then the store; the reply carries the tab's
    `tiktok.com` cookies from `CookieManager` (never logged). Without the browser screen (no
    collector) there is no tab to read and no wait.
  - API answers (step 3): `store` runs at document start through androidx.webkit 1.12.1
    `addDocumentStartJavaScript` for `https://www.tiktok.com` and `https://m.tiktok.com`, on
    the tab and on the hidden page. It wraps `fetch` and `XMLHttpRequest` for same-site `/api/`
    paths, reads a clone after TikTok's code got its answer and keeps up to 200 compact items
    by id in a page-local object; TikTok's requests are never changed, delayed or repeated,
    its own errors are swallowed, nothing is sent to the app or the network. Without
    `DOCUMENT_START_SCRIPT` the store runs in `onPageStarted` (new
    `SecureBrowserWebViewClient.pageStartScript`; the hidden page's client does the same):
    answers TikTok's code got before that are missed.
  - Hidden page (step 4, `TT_HIDDEN_PAGE=ON`): `TikTokPageEngine` with `WebViewHiddenPages`
    (`app/.../detection/tiktok/`): an offscreen WebView on the main thread, never attached
    (1280×800), desktop Chrome agent with the WebView's Chrome version, JavaScript and DOM
    storage on, images blocked, media requests and media file types answered empty (204), only
    `https` pages of `tiktok.com` (another site or an app link stays closed), the shared cookie
    store. It loads the link (short links follow their redirects), polls `item` every 300 ms
    until the post appears or 15 s pass, then destroys the WebView (also when the lookup is
    cancelled). One at a time (`Mutex`; "hidden page: waited 0.9 s for the lookup before"). A
    check still shown after 3 s → `BOT_CHECK` "TikTok wants a check. Open the video in YFT's
    browser, then tap Download." (a check TikTok's own script passes by itself passes; YFT
    never answers one); TikTok's status for the post unchanged for 2 s is its answer (10231
    region, 10222/10223 sign-in, else private or removed); a main-frame error ends the read.
    `TT_HOME_COOKIES=OFF`: the TikTok cookies the page set are cleared when it finishes
    ("hidden page: TikTok cookies it set cleared: N") and Home stays cookie-free.
  - Order (step 5): `SiteAdapterCoordinator.inspect(…, tab)`. Browser: tab data → P39's page
    read with the tab's cookies → the tab once more (a loading page may hold the post by then)
    → hidden page → P39's player file. Home: page read (with the browser's TikTok cookies, G3)
    → hidden page → today's generic scan. Details list every step: `tab data: …`, `page read:
    RESPONSE_CHANGED · HTTP 200` and the page read's lines, `hidden page: found in N s · API
    answer · API answers kept: N` (or why not), `hidden page's data: …`. Try again runs the
    whole order; the tab is read again on every lookup, and Download after a failed TikTok
    lookup runs it again.
  - Downloads (step 6): rows from tab data carry the cookies taken with it, rows from the hidden
    page its agent and the cookies of its store at the moment the post appeared (never logged);
    P39's file checks and next-address rule stay.
  - Messages (step 7): no "Falling back to generic detection" anywhere (checked with `grep`); a
    TikTok notice shows only after every step failed, with the next thing to do (Try again, the
    check text above, P39's "does not open a video" text).
  - Plan adapted (safety review): the hidden page opens only after YFT's own request's failures
    (`RESPONSE_CHANGED`, `MALFORMED_RESPONSE`, `RESPONSE_TOO_LARGE`, `HTTP_STATUS`, `NETWORK`,
    `NO_MEDIA_FOUND`, `EXPIRED_LINK`), never after the site's own answer (private, sign-in,
    region, DRM, a check, rate limit); no check or puzzle is ever automated.
  - Plan adapted: the rows' Referer stays P39's page address on `www.tiktok.com`
    (`BrowserRequestContext` sends `pageUrl` as Referer; core-model is C's); the file checks
    send `https://www.tiktok.com/`.
- Tests (P40, JVM, +59): extractor-api `SitePageDataTest` 3 (new); extractor-sites
  `TikTokPageDataTest` 10 (new; fixtures `page_data_script_item.json` and
  `page_data_api_item.json` are what the asset returned on the instrumented fixture page in
  Chromium); app `SiteAdapterOrderTest` 13, `TikTokPageScriptTest` 10, `TikTokPageEngineTest`
  14, `TikTokHomeSessionTest` 3, `BrowserTikTokTabDataTest` 5 (all new),
  `HeadlessLinkInspectorTest` 20 (+1). Instrumented (CI emulator): `TikTokPageDataInstrumentedTest`
  7 on `app/src/androidTest/assets/tiktok/` served by the test at `https://fixture.yft.test`
  (allowed through constructor parameters only): (a) the tab gives the page script's item;
  (b) after the page's own `/api/recommend/item_list/` fetch, an API item by id; (c) the page
  got its answer unchanged; (d) the hidden page finds the post within 15 s and is destroyed,
  also for an API item; (e) a page without the post → timeout (3 s in the test), destroyed;
  another site's address never loads.
- Regression proof: with the old behaviour put back in `TikTokExtractor` (page data ignored)
  `TikTokPageDataTest` 10/10 fail; with the old order in `SiteAdapterCoordinator` (no tab step,
  no hidden page) `SiteAdapterOrderTest` 10/13 and `BrowserTikTokTabDataTest` 3/5 fail (the 5
  that pass check what stays as before: the site's own answer, other sites, no browser screen,
  the site's name); all pass on the new code (backup `/data/bak/P40/`, restored with `cp`,
  checked with `cmp`).
- Live check (2026-10-09, US sandbox, Playwright Chromium, desktop agent, images, media and
  fonts blocked; the same asset; counts and hosts only): video page
  `@scout2015/video/6718335390845095173` → found from the page's script in 0.7 s, id matches,
  5 qualities (heights 1280 and 1024), hosts `v16-webapp-prime.us.tiktok.com`,
  `v19-webapp-prime.us.tiktok.com`, `www.tiktok.com`; For You after scrolling → 4 API answers
  kept, 32 posts in the store; the first 5 by id all found (`api`), ids match, 3–5 qualities
  each (heights up to 1920), the same hosts. For You's address has no post id, as expected (the
  on-screen id comes from P36's probe). No live-check code was committed. A wider check (one
  range request per file) was stopped by the automated safety review and not repeated; the plan's
  live check asks only for the above.
- Validation (P40, 2026-10-09): Agent A command (FIX_ADD_PLAN 0.3) → BUILD SUCCESSFUL — 1313
  JVM tests, 0 failures, 66 skipped (app 867, core-browser 139, core-media 32, extractor-api 36,
  extractor-sites 239; +59 from 1254); `:app:lintDebug` 0 errors, 98 warnings (+3: lint's notice
  that a newer androidx.webkit exists; 1.12.1 is the plan's version for compile SDK 35);
  `:app:compileDebugAndroidTestKotlin` OK; line check empty.
- CI (P40, `ce04711`, the P40 checkpoint; this docs-only commit starts no CI): checkpoint
  validation [37886371700](https://github.com/Alalkipgen/YFT/actions/runs/37886371700)
  (`yft-debug-apk`), emulator smoke API 34 with `TikTokPageDataInstrumentedTest`
  [37886371675](https://github.com/Alalkipgen/YFT/actions/runs/37886371675), Preview APK
  [37886371676](https://github.com/Alalkipgen/YFT/actions/runs/37886371676) (`yft-preview-apk`)
  — all green (the WIP `53a0359` with the same tests: green too).
- Owner check (P40, pending): Agent A's Preview APK (run 37886371676 › `yft-preview-apk`, with
  the VPN) — TikTok in YFT's browser, For You: scroll through 5 videos, Download on each → the
  on-screen video's qualities every time; a video page → qualities; a `vt.tiktok.com` link on
  Home → qualities within about 10 s; a screenshot of Details for anything that fails.
- Hand-offs: Hand-off to C: core-model/src/main/kotlin/com/alal/yft/core/model/media/MediaAsset.kt
  — add `VariantResolutionResult.Failure.error: String? = null` (exception class name) and show it
  in QuickDownloadFailures details as "Error: <Class>" — so the resolver's failure names the
  class as P39 step 7 asks (the resolver already returns the step; core-model media is C's).
- Next: none for Agent A — P44 merges B → C → A, then Preview #7.

## Agent B — `work/phase-15-downloads` (P41, P42; later P44)

- Status: READY FOR MERGE (2026-10-09) — P41 and P42 done; last code commit `8ba7259`, CI green.
  OWNER ANSWERS: none (defaults `FAST_START=ON`, `DELETE_CONFIRM=ON`).
- Base commit: `d0bc7f7` (`origin/work/phase-15-integration`); folder `/data/YFT-B`.
- **P41 Result:** YouTube's whole-file tracks show bytes and speed from the first range.
  `DashTransferEngine` runs a pool of workers (the next range starts as soon as one ends),
  counts bytes as they are written (at most 4 updates a second, a retried range takes its bytes
  back, never backwards or past the total; a range is done in the checkpoint only after its
  sync), fetches a 1 MiB first range then 10 MiB ranges with 4 at once on YouTube's media hosts
  (`FAST_START`, `Policy.fastStart = true`), skips the 1-byte length probe when the length is
  known and otherwise takes it from the first range's `Content-Range`. A checkpoint saved with
  the old layout resumes with it (old fingerprint wins; the new layout has `whole-file-v2`).
  `AudioVideoMuxEngine` passes both tracks' in-range bytes on (4 a second); `DownloadQueue`
  shows a running DASH or merged task's bytes in flight while the store keeps checkpoint bytes.
  One line per download in the log (`YftDownloads`: `DASH start: plan 0.0 s · length 0.0 s
  (from first range) · first byte 0.0 s · first progress 0.0 s`, `Merged download start: …`)
  and a `Start:` line in a failure's Details. YouTube plans carry the length from `clen` or the
  player's `contentLength`.
  - Plan adapted: (1) step 6 needed no new label — since P34 the row and the notification show
    "12.3 MB · 1.2 MB/s" when the total is unknown; they stayed at 0 B because no bytes came
    before the first 10 MiB range, which P41 fixes (the merge's % appears once both sizes are
    known). (2) A resume of an unknown-length track that already has finished ranges still
    probes, so its saved layout is kept. (3) `clen`/`contentLength` are trusted only on
    `googlevideo.com` addresses. (4) The start times are stored after the detail in
    `last_error_detail` (`…|start: …`, no database change; old rows read as before).
    (5) `HlsTransferEngine` unchanged. (6) `StoredDownloadTask` may show more bytes than its
    checkpoint only for a running DASH or merged task (additive, P41).
  - Validation (2026-10-09, `--no-daemon --continue`, the five tasks): core-download 184,
    core-model 106, app 809 (66 skipped) = 1099 tests, 0 failures (+20 from 1079); `:app:lintDebug`
    0 errors (95 warnings, as before); `:app:compileDebugAndroidTestKotlin` OK; line check empty.
  - Regression proof (old `DashTransferEngine`, `AudioVideoMuxEngine`, `DownloadQueue`,
    `DownloadTaskStore`, `DownloadModels`, `DownloadPlanFactory`, `DownloadLabels` from
    `/data/bak/P41/orig`; restored with cp, cmp equal): 11 of 38 failed — DashTransferStartTest
    (first range of an unknown length is 1 MiB; a short file takes one request; a stated length
    needs no request; progress within 1 s at 64 KB/s and at 1 MB/s; never back, ends at the
    total, ≤ 4 a second; one slow range does not hold back the others), AudioVideoMuxProgressTest
    (both), StreamDownloadQueueTest "a merged task shows the bytes its tracks wrote between
    checkpoints", DashTransferEngineTest "whole file track … reports its length" (expects no
    probe now). DashFastStartLayoutTest, the new FailureDetailCodecTest/DownloadLabelsTest cases
    and DownloadPlanFactoryTest "YouTube tracks carry the length …" use the new API (do not
    compile on the old code). "a retried range is counted once" passes on both (guard).
  - Start times (throttled local server): see TEST_MATRIX "Agent B — P41, P42".
  - CI `6257307` (last P41 code commit): [checkpoint
    validation](https://github.com/Alalkipgen/YFT/actions/runs/37861112002), [emulator
    smoke](https://github.com/Alalkipgen/YFT/actions/runs/37861112033), [Preview
    APK](https://github.com/Alalkipgen/YFT/actions/runs/37861112027) all green. On `e50789c`
    the checkpoint validation stopped after 36 s before any test (the same command passed here;
    smoke and Preview APK green), so `6257307` (stopped tasks publish checkpoint bytes in one
    place) ran it again.
  - Hand-offs: none. P44 (merge) note: `DownloadFailure` gained `startTimeline` (default null)
    and `DashTransferRunner` a 6-parameter `transfer` with a default.

- **P42 Result:** finished downloads get "Delete file" (`download-menu-delete-file-<id>`) right
  after "Remove from list" (kept: it removes the row only). With `DELETE_CONFIRM` (ON) the
  question `download-delete-file-dialog` asks "Delete this file?" — "“<file name>” will be
  removed from Download/YFT and from this list. This can't be undone." — with Delete
  (`download-delete-file-confirm`) and Cancel (`download-delete-file-cancel`). The new
  `DownloadedFileDeleter` (`app/.../download/`) deletes by the record: YFT's MediaStore item
  through the content resolver (a `SecurityException` becomes `MediaStore.createDeleteRequest`
  on Android 11+, the `RecoverableSecurityException` action on Android 10; the screen launches
  it and YFT deletes again once allowed), a SAF document through `DocumentsContract`, an
  app-private file directly; a file already gone counts as deleted. Then `deleteRecord`, the
  Library refreshes (its finished-download count drops) and the snackbar says "File deleted";
  a failure says "Could not delete the file" and the row stays. `LibraryRepository.delete`
  uses the same deleter. Unfinished rows keep their menus.
  - Plan adapted: (1) "Delete file" is a menu entry of its own, not a new `DownloadAction`, so
    the existing actions and their tests stay as they are. (2) The Library's delete now says
    "deleted" for a file that is already gone (`AndroidLibraryRepositoryTest` updated; before it
    said false). (3) A record without an address is not deleted ("Could not delete the file").
    (4) `DELETE_CONFIRM` is a constant (ON); a test constructor turns it off.
  - Validation (2026-10-09, the five tasks): core-download 184, core-model 106, app 826
    (66 skipped) = 1116 tests, 0 failures (+17 from P41's 1099); lint 0 errors (95 warnings);
    androidTest compiles (new `delete/DeleteFileInstrumentedTest`); line check empty.
  - Regression proof (old `DownloadsScreen`, `DownloadsViewModel`, `DownloadLabels`,
    `LibraryRepository`, `LibraryModule`, no deleter; restored with cp, cmp equal): 2 of 5
    failed — DeleteFileMenuTest "only a finished row's menu offers Delete file beside Remove
    from list", AndroidLibraryRepositoryTest "deleting an app storage item removes only that
    file" (a gone file is deleted). DownloadsViewModelDeleteFileTest (confirm deletes the file
    then the record, cancel, `DELETE_CONFIRM=OFF`, a kept file keeps its row, Android's request
    allowed or refused, unfinished rows), DownloadedFileDeleterTest and DeleteFileScreenTest
    use the new API (do not compile on the old code). The instrumented test's JVM twin is
    DownloadsViewModelDeleteFileTest.
  - CI `8ba7259` (last code commit): [checkpoint
    validation](https://github.com/Alalkipgen/YFT/actions/runs/37862940024), [emulator
    smoke](https://github.com/Alalkipgen/YFT/actions/runs/37862940003) (runs
    `delete/DeleteFileInstrumentedTest`), [Preview
    APK](https://github.com/Alalkipgen/YFT/actions/runs/37862940023) all green.
  - Hand-offs: none.

## Agent C — `work/phase-15-ads` (P43)

- Status: P43 READY FOR MERGE (2026-10-09; `AD_RULE=STRICT`: the owner gave no answer). Last code
  commit `85f0998` (P39 resolver, owner override); docs after it only.
- Hand-off from A done: `Failure.error` + Details "Error: <Class>" (`175eca0`): the exception's
  simple class name only, set by `QuickDownloadViewModel.resolveSafely`; adding error in
  `DefaultVariantResolver`'s catch blocks: done by C (owner override), `85f0998` — A's P39
  files of `8b0bc93` plus `error = error.errorClass()` in `resolve()`'s three catch blocks
  (IOException, IllegalArgumentException, Exception); the header-probe and playlist-length
  catches still return null. Regression proof: A's `8b0bc93` resolver put back → 2 of 23
  `DefaultVariantResolverTest` failed (error null); restored with `cp`, `cmp` equal.
- P44 merge note: when `work/phase-15-tiktok` is merged (last), `DefaultVariantResolver.kt` and
  `DefaultVariantResolverTest.kt` may conflict: keep the integration (C) version. Check with
  `git diff origin/work/phase-15-tiktok -- <both files>`: the only difference must be C's error
  lines. If A changes either file after `8b0bc93`, take A's newest version and add the error
  lines again.
- Base commit: `d0bc7f7` (`origin/work/phase-15-integration`, the plan commit).
- Starting state (before edits, 2026-10-09): `./gradlew --no-daemon --continue :core-model:test
  :extractor-generic:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (9 min 34 s): 1071 tests, 0 failures,
  66 skipped (core-model 106, extractor-generic 20, core-browser 138, app 807); lint 0 issues.
- Result: on a site without an adapter one rule (`PageVideoProof`, core-model) decides at every
  step of the sheet (first choice, page's newest link, player's link, page read again, next
  video) whether a file is the page's video: named by the player setup, or its length (measured
  first when unknown) matches the page's or the failed video's. Ads: `AdHosts` (own list, network
  names under any suffix, IMA/VAST/pre-roll requests, VAST/VMAP answers), `AdSign` on candidates
  (mapper, `VastAdTracker` incl. `onAnswer`), short files on long pages. Skipped files: "That was
  an ad — showing the page's video"; nothing left: "Only an ad was found, not the page's video."
  with Reload (stand-in of the page's length). Proven ads not counted in the sheet's other videos.
- Plan adapted: P28's test "the sheet waiting for the page's video shows … then its line" offered
  a 0:30 ad on a 16:24 page; it now sets `adRule = LENIENT` (LENIENT keeps the browser's first
  choice) and a STRICT twin expects no ad offered. The size check of a stated file now keeps the
  length it read (the header shows the proven length).
- Validation (2026-10-09, after the P39 resolver): `./gradlew --no-daemon --continue
  :core-model:test :core-media:testDebugUnitTest :extractor-generic:test
  :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL: 1145 tests, 0 failures, 66 skipped
  (core-model 122, core-media 33 — new in the list for the resolver, extractor-generic 21,
  core-browser 148, app 821; 1112 without core-media, +41 from 1071); lint 0 errors (95
  warnings, unchanged); androidTest compiles; long-line check empty.
- Regression proof: the 7 files before P43 + `QuickDownloadPrerollTest` → 4 of 5 failed, each
  offering the 0:30 file; restored with `cp`, `cmp` equal (TEST_MATRIX "Agent C — P43").
- CI (`4be6563`, the last code commit): checkpoint validation success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957302), emulator smoke success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957303; 38 instrumented tests, 0 failures,
  0 FATAL — 37 before, `AdPrerollInstrumentedTest` added), Preview APK success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957301). `175eca0` (P39 hand-off):
  validation success (https://github.com/Alalkipgen/YFT/actions/runs/37869539298), emulator
  success (https://github.com/Alalkipgen/YFT/actions/runs/37869539313; tests=38 failures=0,
  0 FATAL), Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37869539343).
  `85f0998` (P39 resolver, last code commit): validation success
  (https://github.com/Alalkipgen/YFT/actions/runs/37877862671), emulator success
  (https://github.com/Alalkipgen/YFT/actions/runs/37877862760; tests=38 failures=0, 0 FATAL),
  Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37877862659).
- Owner check: the adult site of item 7 — Download on 10 videos, several with a pre-roll: the
  page's own length every time, never 0:30 ("That was an ad — showing the page's video" may
  show); Javtiful and the HTTP-410 site still download; a Details screenshot for any miss.
- Hand-offs to Agent A: (a) `BrowserScreen.kt:509` and `DetectedMediaScreen.kt:169` call
  `MediaGroups.ofPage(…, facts, hideAds = true)` so proven ads are not listed under Other videos,
  and `BrowserViewModel`'s `otherVideos` leaves out `PageVideoProof.isProvenAd` groups; (b)
  `BrowserViewModel` calls `vastAds.onAnswer(observation, contentType, bodyStart)` where the
  page's answers are read (e.g. `MediaMetadataProbe`'s non-media XML answers), so a VAST/VMAP
  body starts an ad break when the request's address says nothing.
