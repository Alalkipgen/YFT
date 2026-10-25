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

## Download fixes agent — `work/phase-download-fixes` (D1–D4)

- Task (the owner, 2026-10-24): four fixes, one commit per item with its JVM tests, in order —
  D1 HLS never saved as `.m3u8`; D2 audio rows (YouTube 4, every other site 2); D3 Myanmar titles
  keep their marks; D4 Instagram and X icons beside the other sites'. No PR until the owner says
  (after PR #2's merge: main merged in with a merge commit, no rebase).
- Base: `8dc46ff404436703f9b588eeee193b2a084f15c1` (`spike/master-extractor-backup`'s head).
- Rules: commits on this branch only (never main, `spike/master-extractor-backup` or another
  work branch); `recipes` untouched, master-recipes-publish never run; PROGRESS.md, CHANGELOG.md
  and docs/*MATRIX*.md not edited; no APK on the owner's phone before he says S5's run 3 is
  counted, then only with his OK.
- State: D1 committed; D1 follow-up (`f5effc1`) red on Emulator smoke (its type rule wrong); D1
  follow-up 2 (`d1bd432`, either MPEG-TS type) green on CI, the bytes check included; D2
  (`835af64`), D3 (`cfa39c7`) and D4 (`abac20e`) green on CI. The owner's phone test of
  `abac20e`'s preview (2026-10-25): D1–D4 pass, one D1 bug (two sizes for a remuxed HLS
  download); D1 follow-up 3 (`0e4a59b`) green on CI and accepted; D1 follow-up 4 (every
  completed download shows its saved file's size) committed (this checkpoint). Next: its CI green
  by 2026-10-26 24:00 MMT (else it is dropped to v1.0.1), then a short Burmese report; no merge,
  no PR, no phone APK (the owner checks it at #12).

### D1 — HLS saved as `.m3u8` (Pornhub, JAV)

- Cause: `DownloadPlanFactory.extensionFor` took the variant's container (`m3u8`, `hls`) as the
  file's extension and its type (`application/vnd.apple.mpegurl`) as the file's; the engine then
  joined the MPEG-TS pieces into that file, so a TS file was saved as `.m3u8`.
- Fix: a playlist container (m3u8, m3u, mpd, hls, dash) is never an extension (the media type
  says it; `video/mp2t` → `ts`, no longer `mp4`). An HLS download is planned as `.mp4`
  (`video/mp4`), `.m4a` (`audio/mp4`) for sound alone, with `HlsDownloadPlan.remuxToMp4`. The
  engine sniffs the pieces (`HlsMediaSniffer`): MPEG-TS (H.264 + AAC) and packed AAC are remuxed
  on the phone by `TsMp4Remuxer` (plain Kotlin, no re-encode; samples straight into the file,
  `moov` from P35's `StreamCopyMoov`, edit list like ffmpeg's for the first frame's delay);
  fragmented MP4 is joined as before. When the remux is not possible (another codec, broken
  data) the pieces are joined and published in their own format through the new
  `DownloadDestination.renameExtension` (`.ts` `video/mp2t`, `.aac`, `.mp3`: app storage a free
  "name (n).ext", MediaStore's pending row DISPLAY_NAME + MIME_TYPE in one update, SAF at
  commit); the queue shows that name (`HlsTransferResult.Completed.publishedAs`). A TS file is
  never named `.mp4`. MP4, WebM and merged downloads unchanged.
- Tests: `DownloadPlanFactoryTest` (an HLS `.m3u8` variant → `.mp4` `video/mp4`; HLS sound alone
  → `.m4a`; playlist containers never an extension, MP4/WebM unchanged), `TsMp4RemuxerTest`
  (real ffmpeg-made TS pieces in `core-download/src/test/resources/hls/*.b64`: an MP4 whose H.264
  and AAC samples equal the source's, golden MD5s; sound alone → M4A; packed AAC; times that
  start again; image prefix; MPEG-2 not remuxed), `HlsMediaSnifferTest`, `HlsTransferEngineTest`
  (7 D1 tests: TS → real MP4, sound → M4A, kept `.ts` never under the MP4's name, a remux failing
  halfway leaves exactly the TS bytes, storage error fails, fMP4/plain joins unchanged, MP3 kept
  `.mp3`), `PublicDownloadDestinationTest` (3), `StreamDownloadQueueTest` (1); instrumented
  `HlsRemuxInstrumentedTest` (emulator smoke: MediaStore row is an MP4 that MediaExtractor
  reads; MPEG-2 pieces → a `.ts` row, its type MediaStore's: see D1 follow-up 2).
- Offline proof (scratch, not committed): the remuxed MP4 decodes frame for frame like the
  source (ffmpeg framemd5), AAC sample bytes equal ffmpeg's own remux, `elst` equal ffmpeg's.
- Backlog (not in D1's scope): a site's container `matroska`/`quicktime` still taken as an
  extension; a direct (non-HLS) `.ts` link stays `.ts` (no remux); a task's total bytes after a
  remux stay the pieces' bytes (fixed by D1 follow-up 3); HEVC or MP3 inside TS → kept `.ts`;
  packed MP3 pieces keep their inner ID3 tags (as before); a crash between rename and commit may
  resume a renamed `.ts` row under the planned name.
- D1 validation (local, 2026-10-24): `:core-model:test :core-download:testDebugUnitTest` →
  core-download 265 tests, 0 failures after one D1 test's expectation was corrected (packed MP3
  pieces are joined whole); `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` → 1118
  tests (1115 before + 3), 0 failures, 66 skipped; `lintDebug` → 0 errors (no warning in a D1
  file); no new line over 100 characters; the checkpoint runs `testDebugUnitTest`. CI
  (`b7edd6b`): checkpoint validation run 40083006744 and Preview APK run 40083006431 green;
  Emulator smoke run 40083006457 red, 1 of 56 tests (3 skipped, no crash): see D1 follow-up.

### D1 follow-up — the emulator test's `.ts` type (`f5effc1`, wrong rule)

- Cause (the test's expectation, not the app): `HlsRemuxInstrumentedTest`'s
  `piecesThatCannotBeRemuxedAreKeptAsATsRowOfTypeMp2t` expected the row's MIME_TYPE
  `video/mp2t`; the API 34 emulator stored `video/mp2ts`. The name (`.ts`), the published (not
  pending) row and the app's own record (`publishedAs`, `.ts` `video/mp2t`) were right; the
  bytes check after the type check never ran.
- Fix tried (the owner's choice A, 2026-10-24: a separate commit, the test and this section
  only): expect the given type when `MimeTypeMap.hasMimeType`, else the extension's. Wrong:
  publishing (IS_PENDING 0) makes MediaProvider scan the file and set the type itself (API 34:
  `.ts` → `video/mp2ts`), whatever the app gave.
- CI: checkpoint validation run 40087033078 and Preview APK run 40087033092 green; Emulator
  smoke run 40087033113 red, the same 1 of 56 (3 skipped, no crash), the bytes check not run.

### D1 follow-up 2 — the emulator test accepts either MPEG-TS type

- Fix (the owner, 2026-10-24: MediaStore's type is not the app's to control and differs across
  Android versions; the test and this section only, no app code): the row's type must be
  `video/mp2t` or `video/mp2ts`, MediaProvider's rule not re-implemented. Order: Completed, the
  bytes check, the app's own record (`.ts` `video/mp2t`), the row's name (`.ts`), published.
- Tests: the instrumented test only (Emulator smoke); no JVM test changes.
- Validation (local, 2026-10-24): `:app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (JDK
  17); the checkpoint runs it; no new line over 100 characters. CI (`d1bd432`): checkpoint
  validation run 40090783594, Preview APK run 40090783622 and Emulator smoke run 40090783588
  green (56 tests, 0 failures, the same 3 skipped: this test ran whole, its bytes check
  passed); D2 pushed after it.
- Backlog: the app records a kept `.ts` as `video/mp2t` (queue, library) while MediaStore says
  `video/mp2ts`; players match `video/*`, so no change.

### D2 — audio rows (YouTube 4, every other site 2)

- Cause: `QuickDownloadChoices.audioRows` offered the M4A and an MP3 at every
  `Mp3Variants.BITRATES_KBPS` (320, 192, 128) on every site, `Mp3Variants.addTo` (Preview) added
  the three too, and `compact` (P9's short view) picked the MP3 at exactly 128 kbps.
- Fix: `Mp3Variants.bitratesFor(pageUrl)`: all three when `SiteBrand.of(pageUrl)` is YouTube
  (youtube.com and its subdomains, youtu.be, youtube-nocookie.com), else only 192
  (`OTHER_SITE_KBPS`). The rule is "not YouTube", so Facebook, Instagram, X, TikTok, VK and the
  web get it with no VK code. `QuickDownloadChoices.of` passes the group's `pageUrl`, `addTo`
  the asset's `sourcePageUrl`; `compact` keeps the M4A and the lowest MP3 offered (128 on
  YouTube, 192 elsewhere). A chosen row is still rebuilt from its own bitrate
  (`QuickDownloadViewModel` unchanged).
- Tests: `QuickDownloadChoicesTest` (YouTube www, m, music, youtu.be → M4A + MP3 320/192/128,
  short view M4A + 128; Facebook (facebook.com, fb.watch), Instagram, X (x.com, twitter.com),
  TikTok, VK (vk.com, vkvideo.ru) and a generic site → M4A + MP3 192, the short view the same),
  `Mp3VariantsTest` (`bitratesFor` per site, youtube-nocookie.com too; `addTo` adds only 192 on
  the other sites), `PreviewViewModelTest` (a Facebook page's M4A offered as MP3 192 only); the
  expectations of tests on `OTHER_PAGE` or a Facebook page updated (`QuickDownloadChoicesTest`,
  `QuickDownloadViewModelTest`, `PreviewViewModelTest`; fixture `youtubeOn(page)`).
- Validation (local, 2026-10-24): `lintDebug` → 0 errors, no issue in a D2 file;
  `testDebugUnitTest -x :app:testDebugUnitTest` (core-model 127, core-download 265, core-data 33,
  core-media 39, extractor-api 36, extractor-generic 21, extractor-sites 293 tests) and
  `:app:testDebugUnitTest` (1120 tests: 1118 + 2, 66 skipped) → 0 failures; `:app:assembleDebug
  :app:assembleRelease` → BUILD SUCCESSFUL; no new line over 100 characters. CI (`835af64`):
  checkpoint validation run 40093269320, Preview APK run 40093269382 and Emulator smoke run
  40093269421 green.
- Backlog: none in D2's scope. The rule reads the page address the sheet shows as its source
  (Preview: the asset's `sourcePageUrl`), not the media file's own host.

### D3 — Myanmar titles keep their marks

- Cause: `DownloadPlanFactory.sanitizeForFileSystem` kept only `Char.isLetterOrDigit()` and
  "-_()[] ", so every mark (`\p{M}`: Myanmar ါ ာ ိ ု ေ ် ျ း, Thai and Hindi vowel signs) became
  a space; `fileName` cut with `take()` at 96 UTF-16 units: about 288 bytes in Myanmar, over the
  255-byte limit, and it could split a stacked consonant after ္. core-download's
  `sanitizePublicFileName` and `DirectRangeProbe.sanitizeFileName` (Content-Disposition names)
  cut with `take(180)`: graphemes split, no byte limit, the extension could be cut off.
- Fix: one shared object, core-model `download/FileNames.kt`. `keepWords(title, allowed)`: NFC;
  letters, digits, the allowed punctuation and the marks after a kept letter or digit; a mark
  with no kept base is dropped (at the start, U+FE0F left by a removed emoji), so a Latin title
  with emoji gains no invisible character; everything else a space, runs of spaces collapsed,
  ends trimmed. `cut(text, maxChars, maxBytes)`: only at a grapheme boundary (never before a
  mark, ZWJ/ZWNJ, an emoji modifier or a tag character; never after ZWJ, or after a stacking
  virama when a letter follows: Myanmar U+1039, Khmer U+17D2, Tai Tham U+1A60, Indic U+094D,
  09CD, 0ACD, 0B4D, 0C4D, 0D4D; regional-indicator pairs and Hangul jamo together); a cut
  between code points only when not even the first grapheme fits (Zalgo). `fit(name, maxChars,
  maxBytes)` keeps a short extension (≤ 10 ASCII letters and digits). Byte budget: the whole
  name, extension included, at most 255 − 32 = 223 UTF-8 bytes (`FileNames.MAX_NAME_BYTES`);
  the 32 leave room for app storage's `<name>.<id ≤16>.part` and " (n)", and for MediaStore's
  pending `.pending-<10 digits>-<name>` (Android 11+), which MediaProvider would otherwise
  shorten in the middle with "...". `DownloadPlanFactory`: `keepWords`, then `cut` at 96
  characters (Latin titles as before) and 223 bytes; `sanitizePublicFileName` and
  `DirectRangeProbe`: `fit` at 180 characters and 223 bytes; `Id3v2Tag.clean`: `cut` (its
  surrogate check folded in). Saved files are not renamed; no migration.
- For the owner: two choices of mine. (1) Myanmar's word signs ၌ ၍ ၎ ၏ (U+104C–104F, Po) are
  kept like letters (၊ and ။ still become spaces). (2) A spacing mark (Mc) with no base is kept,
  not dropped: Zawgyi text types ေ and ျ before their consonant, so dropping them would change
  the word; Mc marks are visible, so no invisible character comes from it (U+FE0F is Mn and is
  dropped). Also: a title the cut leaves empty (e.g. "___") is now `yft-download.<ext>`, no
  longer the hidden file `.mp4`.
- Tests: `FileNamesTest` (11: the four titles အချစ်ရူး, ထွဏ်းထွဏ်း, ဒါဟာအချစ်လား,
  ဇော်ပိုင် ချောစုခင် and Thai สวัสดีครับ, Hindi नमस्ते दुनिया come back exactly; word signs;
  marks with no base, VS16 and a skin tone leave nothing; Zawgyi; the reserved \ / : * ? " < > |
  and invisible or direction-changing characters; သတ္တဝါ cut at every length, never after ္ or
  before ါ, and स्ते kept whole; a long Myanmar title cut at a grapheme within 219 bytes; flags,
  skin tones, a ZWJ family, a tag flag and Hangul jamo whole; the Zalgo fallback; `fit`; the
  byte counts), `DownloadPlanFactoryTest` (6: the six titles → `<title>.mp4`; a long Myanmar
  title → 12 × သတ္တဝါ + သ + `.mp4`, exactly 223 bytes; a long Latin title → 96 characters as
  before; ❤️ leaves no U+FE0F; reserved characters → spaces; "___" → `yft-download.mp4`),
  `PublicDownloadDestinationTest` (1: a long Myanmar display name keeps `.mp4` within 223
  bytes), `DirectRangeProbeTest` (1: the same through `filename*=UTF-8''`), `Id3v2TagTest` (1:
  ကျ kept whole or not at all). The existing "invisible and direction-changing characters cannot
  disguise the extension" still gives "clip gnp exe.mp4".
- Validation (local, 2026-10-24): `lintDebug` → 0 errors, no issue in a D3 file;
  `testDebugUnitTest -x :app:testDebugUnitTest` (core-model 138 = 127 + 11, core-download 268 =
  265 + 3, the other modules as in D2) and `:app:testDebugUnitTest` (1126 = 1120 + 6, 66
  skipped) → 0 failures; `:app:assembleDebug :app:assembleRelease` → BUILD SUCCESSFUL; no new
  line over 100 characters. CI (`cfa39c7`): checkpoint validation run 40098830060, Preview APK
  run 40098830721 and Emulator smoke run 40098830051 green.
- Backlog: a ZWJ or ZWNJ inside a word (some Indic and Persian spellings) still becomes a space,
  as the rule says (everything else a space); Thai ำ and Lao ຳ (letters, not marks) can be cut
  from their consonant at the very end of a long title; Zawgyi text is kept as typed, not
  converted; `DirectRangeProbe` keeps its own unsafe-character rule (only its cut changed).

### D4 — Instagram and X icons beside the other sites'

- Cause: the logos were there (`ic_site_instagram.xml`, `ic_site_x.xml`, `SiteBrand.INSTAGRAM`
  and `X`, Browser's View sites), but Home's "Your sites" defaults (`HomeSites.DEFAULTS`,
  version 2) were YouTube, Facebook and TikTok only, and a stored list never got a new default.
- Fix: `DEFAULTS` + Instagram (`https://www.instagram.com`) and X (`https://x.com`),
  `DEFAULTS_VERSION = 3`, `ADDED_IN_VERSION_3` (the two). `migrateDefaults(stored,
  storedVersion)`: a version 1 list is moved as before (its old defaults replaced by the five,
  in order, a site of the same brand taking its default's place; a list without an old default
  stays as it is); a version 2 list gets Instagram and X at its end, each only when no site of
  its brand is there (twitter.com counts as X) and while there is room under `MAX_SITES` (12);
  an empty list stays empty; with no stored list the five defaults are shown.
  `DataStoreHomeSitesRepository.migrateDefaultsOnce` passes the stored version (none = 1) and
  stores 3, so it runs once: a site the user removes later stays removed. Home's tile already
  says the site's name ("Instagram", "X") as its description; the logo is decorative
  (`site-logo-instagram`, `site-logo-x`). No VK code: the owner's VK is his own site, with its
  letter.
- Logos checked: both paths are identical to Simple Icons 16.34.0's `instagram.svg` and `x.svg`
  (and to 16.35.0, the latest), which take Instagram's from Meta's brand resources and X's from
  x.com (the current X mark); the colours (FF0069, 000000) are Simple Icons'.
  `docs/THIRD_PARTY_NOTICES.md` already lists both (Simple Icons 16.34.0, CC0-1.0); no logo or
  notice change.
- Tests: `HomeSitesMigrationTest` (the five defaults and version 3; version 1 cases now with the
  five; version 2: the owner's list with VK → + Instagram, X; a site of the same brand already
  there (twitter.com, m.instagram.com); a full list (12: none; 11: Instagram only; 10: both); an
  empty list; a current list unchanged; a second run changes nothing), `HomeSitesTest`
  (initials Y F T I X), `DataStoreHomeSitesRepositoryTest` (a version 2 list gets both once and
  X removed afterwards stays removed; an empty version 2 list stays empty, version 3 stored; the
  version 1 move with the five), `HomeScreenTest` (the five logos by tag and each tile's
  description, "Instagram" and "X", scrolled into view; a site without a brand shows its letter).
- Validation (local, 2026-10-24): `lintDebug` → 0 errors, no issue in a D4 file;
  `testDebugUnitTest -x :app:testDebugUnitTest` (core-model 142 = 138 + 4, core-data 35 =
  33 + 2, the other modules as in D3) and `:app:testDebugUnitTest` (1126, the HomeScreen logo
  test widened, 66 skipped) → 0 failures; `:app:assembleDebug :app:assembleRelease` → BUILD
  SUCCESSFUL; no new line over 100 characters. CI (`abac20e`): checkpoint validation run
  40100415386, Preview APK run 40100415355 and Emulator smoke run 40100415357 green.
- Backlog: Home shows four tiles and scrolls sideways for the rest, so on the owner's phone
  (YouTube, Facebook, TikTok, VK) Instagram and X come fifth and sixth, after a sideways swipe;
  a version 1 list the user made without an old default is moved as before (not at all), so it
  gets neither.

### D1 follow-up 3 — one size for a remuxed HLS download

- Found (the owner's phone test of `abac20e`'s preview, 2026-10-25: D1–D4 pass, one D1 bug): an
  HLS download remuxed to MP4 showed two sizes for the same file, the Downloads card "480p ·
  223 MB" (the downloaded TS pieces) and Home's Recent card "480p · 213 MB" (the saved MP4); it
  was in D1's backlog ("a task's total bytes after a remux stay the pieces' bytes").
- Cause: `DownloadQueue.completeTask` set `totalBytes` to `(current.totalBytes ?: bytesWritten)
  .coerceAtLeast(checkpoint.downloadedBytes)` and `downloadedBytes` to the checkpoint's, so the
  pieces' size won over the published file's; Recent and Library read the saved file itself.
- Fix: a completed HLS task's size is the file it published (`bytesWritten`: the MP4 or M4A
  made from its pieces, or the kept `.ts`, `.aac` or `.mp3`), as both its total and its
  downloaded bytes (downloaded ≤ total kept), so every screen that shows the task (the
  Downloads card and details, the notifications) says what Home and Library say; its checkpoint
  keeps the pieces' bytes. `StoredDownloadTask` accepts that for a completed HLS task only
  (`showsPublishedBytes`; every other task still shows exactly its checkpoint's bytes) and
  `RoomDownloadTaskStore` keeps it across a restart (a completed HLS row whose stored downloaded
  bytes equal its total). Pieces that are only joined (fragmented MP4) write their own bytes, so
  they show the same as before; DIRECT, DASH and merged downloads unchanged.
- Tests: `StreamDownloadQueueTest` (+3: a remux, pieces 10 and MP4 8 → total and downloaded 8,
  the checkpoint 10, the store the same; a kept `.ts` of 9 → 9 and 9; DASH and merged downloads
  unchanged), `RoomDownloadTaskStoreTest` (+3: a completed HLS task keeps 8 after a restart; a
  row that is not a completed HLS one shows its checkpoint's bytes; a completed HLS row saved
  before keeps its size). Without the fix four of them fail (the two HLS queue tests and the
  first two store tests).
- Validation (local, 2026-10-25): `lintDebug` → 0 errors, no issue in a changed file;
  `testDebugUnitTest -x :app:testDebugUnitTest` (core-download 274 = 268 + 6, the other modules
  as in D4) and `:app:testDebugUnitTest` (1126, 66 skipped) → 0 failures; `:app:assembleDebug
  :app:assembleRelease` → BUILD SUCCESSFUL; no new line over 100 characters. CI (`0e4a59b`):
  checkpoint validation run 40124048608, Preview APK run 40124048654 and Emulator smoke run
  40124048689 green (56 tests, 0 failures, 3 skipped).
- Backlog: a converted MP3 or a sound-only M4A (DIRECT, T18 and P3) showed its downloaded
  source's size and a merged download its tracks' bytes, not the saved file's (fixed by D1
  follow-up 4); a completed HLS task saved before this fix keeps the pieces' size it showed (no
  migration).

### D1 follow-up 4 — every completed download shows its saved file's size

- Found (the owner, 2026-10-25, from follow-up 3's backlog): a converted MP3 or a sound-only M4A
  made from a DIRECT download showed its source's size (for an M4A the whole MP4's), a merged
  download its tracks' bytes and no total, a live DASH file its pieces' bytes; Home's Recent card
  and Library read the saved file itself, so the sizes differed.
- Cause: `DownloadQueue.completeTask` took the published size (`bytesWritten`) for HLS only;
  every other plan type kept `(current.totalBytes ?: bytesWritten).coerceAtLeast(checkpoint
  bytes)` as its total (null for a merged one) and the checkpoint's bytes as its downloaded.
- Fix: a completed task's total and downloaded bytes are the file it published (`bytesWritten`)
  for every plan type: the MP3 or M4A made from a direct download, the merged video, the DASH
  file, the HLS MP4 (downloaded ≤ total kept); its checkpoint keeps what was downloaded. A plain
  direct download publishes exactly its checkpoint's bytes, so it shows the same as before.
  `showsPublishedBytes` is now the completed status, whatever the plan type; a completed DASH or
  merged task keeps those bytes when it is saved and shown (`withCheckpointBytes` only resets a
  stopped one). `RoomDownloadTaskStore` keeps them across a restart (a completed row whose stored
  downloaded bytes equal its total); a completed DIRECT row's checkpoint total, which shares the
  row's total column, comes back as its segments' bytes (what the finished download verified),
  so the checkpoint is the same too. Rows saved before keep what they showed (they saved their
  checkpoint's bytes; no migration).
- Tests: `Mp3ConvertingTransferDispatcherTest` (+3, through a download queue: an MP3 of 14 bytes
  from a 4,096-byte AAC → total and downloaded 14, the checkpoint 4,096, the store the same; the
  M4A of a video → 14 and 14; a plain direct download → 4,096 as before),
  `StreamDownloadQueueTest` (+1 net: a merged video of 8 from tracks of 10 → 8 and 8, the
  checkpoint 10; a DASH file of 12 from pieces of 10 → 12 and 12; follow-up 3's "DASH and merged
  downloads unchanged" test replaced), `RoomDownloadTaskStoreTest` (+3: an MP3 of 7 or 25 from a
  source of 10 keeps its size and its checkpoint after a restart; a merged video of 8 and a DASH
  file of 12 the same; completed direct and merged rows saved before keep their sizes). Without
  the fix six of them fail (all but the plain direct download and the rows saved before).
- Validation (local, 2026-10-25): `lintDebug` → 0 errors, no issue in a changed file;
  `testDebugUnitTest -x :app:testDebugUnitTest` (core-download 281 = 274 + 7, the other modules
  as in D4) and `:app:testDebugUnitTest` (1126, 66 skipped) → 0 failures; `:app:assembleDebug
  :app:assembleRelease` → BUILD SUCCESSFUL; no new line over 100 characters. CI: after the push.
- Backlog: a direct source of unknown size whose MP3 or M4A conversion was cut by the app's end
  and finished after a refresh keeps a checkpoint without a total until the next restart, which
  gives it the source's bytes (a completed task's checkpoint is not read again).

## VK agent — `work/phase-vk-extractor` (VK0–VK4), `work/phase-vk-path-d` (VK5)

- Plan: `docs/VK_PLAN.md` (the owner's request, 2026-10-24). Branch made from PR #2's head
  `8dc46ff` (`spike/master-extractor-backup`); it never merges into PR #2; after PR #2's merge,
  `main` is merged into it (VK4).
- State (2026-10-25): **VK5 built** on `work/phase-vk-path-d` (made from the VK3 record commit
  `8bd64d0c`): path D for public 18+ videos (`docs/VK_PLAN.md` §13; below). Next: CI green (by
  2026-10-26 24:00 MMT, else STOP — D moves to v1.0.1 and v1.0.0 keeps `f2433a1`'s code), then the
  owner's phone check (§13.6). **VK3 PASS** (below): `work/phase-vk-extractor`'s code (`f2433a1`)
  stays the fallback, nothing more on that branch. VK4 is now only a merge, at the owner's word,
  into `spike/master-extractor-backup` after `work/phase-download-fixes` and before
  `work/phase-in-app-update` — no `main` merge, no Preview #13, no PR (§13.1).
- VK0 (done): results in `docs/VK_PLAN.md` §11, sanitized fixtures in
  `extractor-sites/src/test/resources/fixtures/vk/` (README there). Found: the page (A) carries no
  player data (VK's login redirect + SPA shell); the embed `video_ext.php` (B) works without a
  hash for every public video tried (up to 2160p), data in `window.cur` → `apiPrefetchCache` (not
  `playerParams`); B2 = `vk.ru` (not `vkvideo.ru`); embeds are windows-1251.
- **The owner's VK0 answers** (2026-10-24): (a) drop A, v1 = the embed on vk.ru/vk.com; (b) one
  shared charset fix; (c) no `PlayerFileFallback` in v1; (d) no `al_video`; (e) Master's debug
  unchanged. Recorded in `docs/VK_PLAN.md` §12; the design as built is §4.
- VK1, commit 1 `a176667a` (the owner's OK, its own commit): the shared client's charset fix in
  `OkHttpExtractorClient` + `OkHttpExtractorClientTest` — text read with `readUtf8()` as before;
  only when that yields U+FFFD, the bytes are not UTF-8 and the response names another known
  charset (VK: windows-1251) are the same bytes read in that charset. Size, timeout and Cancel
  unchanged.
- VK1 + VK2, commit 2: the adapter in `extractor-sites/.../sites/vk/` — `VkUrls` (links, video
  ids, the embed URLs), `VkEmbedParser` (`apiPrefetchCache` → the item with the link's id → key
  table, else shape scan), `VkJudge` (a manifest: video, audio-only or not judged; read only
  when there is no MP4), `VkExtractor` (B on `vk.ru` for a `*.vk.ru` page with its cookie, else
  `vk.com` with a `*.vk.com` page's cookie only; the same host once more on an SPA shell; then
  B2 on the other host, never with a cookie; at most 3 embed requests, one 15 s deadline, Cancel
  stops the request); 52 VK tests on the fixtures (no network) incl. a fixture-hygiene test; the
  fixtures' URL keys; the registration line in `SiteAdapterModule.kt`. Plan: §4 as built, §5.3
  the tests, §12 the owner's answers.
- The owner's sign-in links (VK messenger channel posts, the plan's §5.1): only on his phone at VK3.
- **VK3 (2026-10-25 MMT): PASS** — the owner's phone, `f2433a1`'s preview (Preview APK run #81).
  - Public, signed out: links 1–5 found, Russian titles correct — `video-50883936_456244102` up
    to 2160p; `video-18403220_456239696` 1080p/720p/480p; `clip30014565_456240946`
    1080p/720p/480p; `video-140332_456239111` 720p MP4 + 480p HLS, no thumbnail;
    `vkvideo.ru/video-18403220_456239696` the same as on `vk.com`.
  - Downloads on the clip: 1080p, 480p and an Audio row play with sound. No other downloads (rows
    0.5–3.5 GB on mobile data); no HLS download (this preview lacks the D1 fix on
    `work/phase-download-fixes`; checked at #12).
  - Sign-in part, with his own links instead of cmid=2/3: the VHS Video channel page
    (`m.vk.ru/mail/channels/…`) has no video id, not claimed (§8); the generic finder shows
    "Messenger · MP3 104 KB". Its video on `m.vkvideo.ru/video-5…`: VK's own page shows "Video
    unavailable"; YFT `NO_MEDIA_FOUND` (B `vk.com` 200 not found, B2 `vk.ru` 200 not found), the
    "isn't available" message and Try again — correct.
  - 18+ videos (`video-215889201_456243439` and others): public, they play signed out on
    `vkvideo.ru`, but the embed answers "may contain adult content … can't be viewed on external
    sites"; YFT `NO_MEDIA_FOUND`, "isn't available". Expected for v1; VK5 (path D) handles them.
  - TikTok, Facebook, Instagram, X: one lookup each, video rows as before; 4 Audio rows on each
    (M4A + MP3 320/192/128), expected without D2 on `work/phase-download-fixes` (after the merge:
    M4A + MP3 192).
  - Later (no fix now): the finished live's thumbnail; the Messenger page's MP3 row.
- **VK5 (2026-10-25, the owner; `docs/VK_PLAN.md` §13), on `work/phase-vk-path-d`:**
  - Recon (live, signed out, metadata and 1-byte checks only): the embed answers "may contain
    adult content … can't be viewed on external sites" (error callback 9) on `vk.com` and `vk.ru`,
    with and without `list`. vkvideo.ru's signed-out flow works for all three examples: an
    anonymous token from `login.vk.ru` (the web client's id and public secret; no cookie; 24 h),
    then `video.get` on `api.vkvideo.ru` (v5.289, `Authorization: Bearer`) — `…_456243446` MP4
    144p–720p, `…_456243466` and `…_456243439` 144p–1080p, each with `hls`, `hls_fmp4` and
    `dash_sep`, files on `vk{N}-{N}.vkuser.net` (bound to the IP and the user agent).
  - Code (one commit; no shared-code change — `postJson` already exists): `VkWebClientProfile`
    (the web client's identifiers, one file), `VkApiToken` (the token in memory only, until 60 s
    before VK's expiry), `VkUrls` (`vkuser.net` for D only), `VkEmbedParser` (the adult answer;
    `video.get`'s item), `VkExtractor` (D only after B/B2's adult answer; only `login.vk.ru` and
    `api.vkvideo.ru`, never a cookie; v1's 15 s deadline, Cancel and message). 4 sanitized
    fixtures (README there); 73 VK tests (21 new: `VkPathDParserTest` 9, `VkPathDTest` 12).
  - Phone check after CI (§13.6): the three examples (rows; one small row plays with sound);
    VK3's `video-18403220_456239696` and the clip unchanged; no other site needed.
- Rules: the plan's §2 (no push to `main` or PR #2, no label, no `recipes` branch; VK5: no merge,
  no PR, no shared docs).
- Validation: `./gradlew --no-daemon :extractor-sites:test` (VK only:
  `--tests 'com.alal.yft.extractor.sites.vk.*'`); before a push the CI set: `lintDebug
  testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test
  :extractor-sites:test :app:assembleDebug :app:assembleRelease`.

## In-app update agent — `work/phase-in-app-update` (U0–U3)

- Plan: `docs/IN_APP_UPDATE_PLAN.md` (the owner's request, 2026-10-24: the in-app update ships in
  v1.0.0, the first stable release, with A and VK; revised after his review the same day). Branch
  made from PR #2's head `8dc46ff` (`spike/master-extractor-backup`).
- State (2026-10-25 MMT): **U1 green** — the U1 cut `e615a96` (CI all success: checkpoint validation
  40123514346, Emulator smoke 40123514304, Preview APK 40123514313). **U2 green** — `d5f36f8`
  (`checkpoint(phase-in-app-update): U2: wait for Android's installer through Compose`, on U2's
  `6883e4d`; CI all success: checkpoint validation 40133089357, Emulator smoke 40133089369 (59
  tests, 0 failures), Preview APK 40133089364): Download APK to update runs in YFT
  (`UpdateInstallFlow`, one for the process): allowed to install? → free space → the release's
  `SHA256SUMS` line → the APK into the cache (check 1, the checksum) → check 2, the signature →
  check 3, the package and version → Android's installer, where the user confirms (YFT never
  installs by itself); Cancel; every failure says which step, "Nothing was installed." and Download
  in browser; Reinstall this build (test) in debug and preview only. The manifest:
  `REQUEST_INSTALL_PACKAGES` and the unexported `InstallStatusReceiver`. **U3 PASS** on the owner's
  phone (the preview of `d5f36f8`, 2026-10-25 10:39–10:53 MMT): every step of plan §5.4; recorded in
  plan §12 (the release commit puts it in `docs/PHONE_TEST_NOTES.md`). **U3 fixes** — checkpoint
  `checkpoint(phase-in-app-update): U3 fixes: the button's two lines, the notes as plain text` (its
  SHA and CI in plan §12's next entry): **Download APK to update** wraps to two lines at large text
  sizes instead of being cut (`YftPrimaryButton`'s optional `maxLines`, default 1), and the notes
  drop Markdown's marks (`ReleaseNotes`). The reinstall test's "Update cancelled." without Download
  in browser is by design (that link is the found release's APK; the test is no release). Beside the
  plan's letter: plan §12's U2 and U3 entries. Next: CI green, then STOP with a short Burmese
  report; no new phone test — the owner checks both fixes in his #12. Time box: CI green by
  2026-10-26 24:00 MMT.
- The owner's review: no Home card, no About change, no dialog; the Version row's version on its
  second line; every text a true claim (U1 never says "verified").
- Merge (the owner's order, only when he says): A → VK → this branch into
  `spike/master-extractor-backup` with merge commits, then the release commit (`1.0.0` / `1000000`,
  the shared docs) on top. No U4: `main` is never merged into this branch, no pull request. Expected
  conflict: this file only (every section kept). U2 late → `work/phase-in-app-update-u1` (the U1
  cut) instead.
- Phone (U3): PASS on 2026-10-25, every step (plan §5.4, §12); the U3 fixes are checked in the
  owner's #12.
- Found at U0: every release made by `release-draft.yml` carries `SHA256SUMS` (`release-prep.sh` →
  `verify-release-apk.sh`; uploaded on create and refresh), so no workflow change. Published today:
  only `v1.0.0-beta.1` (prerelease, versionCode 1); beta.2 and beta.3 are drafts.
- Version code rule (plan §4.2): from the tag, with or without `v`, `X.Y.Z` → X·1000000 + Y·10000 +
  Z·100 (v1.0.0 = 1000000); the release commit sets it, a JVM test guards it.
- For the release commit (plan §11): About's first privacy line
  ("YFT only contacts the sites you open…") must name the update check.
- Rules: the plan's §2 (no push to `main` or the spike except the merge the owner calls, no pull
  request or label, no `recipes` branch, no release or tag, no APK on the owner's phone before S5's
  run 3 is counted).
- Validation: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.alal.yft.update.*'`; before
  a push, CI's set (plan §7); U2 also the Emulator smoke (`UpdateInstallInstrumentedTest`, plan
  §6.2: GitHub played by an in-test interceptor; Android's installer always cancelled).

## Release agent — `work/phase-release-v1` (the v1.0.0 release commit, #12)

- Task (the owner, 2026-10-25): the v1.0.0 release commit per decision 30 (iv)
  (`MASTER_KEY_SOCIAL_PLAN.md` §8) and its two #12 preview APKs, then STOP with a short Burmese
  report. Branch made from `origin/spike/master-extractor-backup` `038c1cd4` (PR #2's branch after
  the download-fixes, VK and in-app-update merges; its CI green: Work-branch checkpoint
  validation 40171179191, Master opt-in debug APK 40171175629). One commit or a few, the release
  commit last; the owner (2026-10-25): push a checkpoint as soon as a part is green.
- Built (checkpoint 1, this commit):
  - `gradle.properties`: `1.0.0` / `1000000` (the version code rule, `VersionCodeRuleTest`).
  - `app/build.gradle.kts`: `releaseMaster(key, url, trials)` — the release, #12 normal
    (`preview`) and #12 trial (`previewTrial`) never read a `-Pyft.*` switch: Master on (route B:
    the own solver, the client ladder; the four sites' ladders with L3; the capture; R9 with the
    committed key `extractor-master/recipes/master-recipes.pub.hex` and the `recipes` branch's
    URL), never the test hooks, the own solver's next ladder or Instagram's tab-fetch rung; the
    trials only in `previewTrial` (`.trial`, "YFT Trial", `-trial.<run>`, the test key); quiet
    (`QUIET_RELEASE`) in the release and `preview` only.
  - (i) **Backup finder** (`detection/master/BackupFinder.kt`, on by default, app-private prefs):
    off → no lookup asks Master (`SiteAdapterModule`'s registry and recovery, the browser's
    fallback and capture, YouTube's own solver via `BackupFinderPlayerScripts`) and Master's
    client refuses every request before it is sent (GitHub raw: R9, the version tables);
    Settings row `settings-backup-finder`.
  - (ii) About › Privacy's first line names GitHub (raw.githubusercontent.com for the backup
    finder, api.github.com for Check automatically).
  - (v) The quiet release (`ui/QuietRelease.kt`: `LocalQuietRelease`, `ShowDetails`, `QuietLines`):
    the browser's lookup notice and Try again hidden (the focus notices stay; a failed one says
    the quiet line); the sheet's one line ("Couldn't get this video.", TikTok's check with Show
    check, "Play the video, then tap Download again."; Download again looks up again); Home's
    sheet the same, Home's Copy details and Downloads' failure details hidden; a failed
    download's retry stays; About's version line tapped 7 times turns Show details on or off.
  - (iii) `.github/workflows/preview-apk.yml`: one run builds `:app:assemblePreview
    :app:assemblePreviewTrial`, verifies both (`com.alal.yft.preview`, `com.alal.yft.trial`) and
    uploads `yft-preview-apk` and `yft-preview-trial-apk`.
  - Tests: `BackupFinderTest` (4), `QuietReleaseTest`, and new cases in `BrowserViewModelTest`,
    `BrowserScreenTest`, `QuickDownloadScreenTest`, `AboutScreenTest`, `SettingsScreenTest`,
    `SettingsViewModelTest`, `DownloadsScreenTest`; `MasterParityLiveTest` passes the switch.
  - Docs so far: `CHANGELOG.md` `## [1.0.0]` (the Unreleased section becomes 1.0.0; the header's
    version code rule), `docs/release/1.0.0.md`.
- Local validation (2026-10-25): `testDebugUnitTest` → app 1278 (66 skipped), core-browser 150,
  core-data 35, core-download 281, core-media 39, extractor-master-android 70: 0 failures;
  `:app:compileDebugAndroidTestKotlin` OK.
- Checkpoint 1 `1244f80b`: CI green (Work-branch checkpoint validation #540 40184741769,
  Android emulator smoke #134 40184741829, Preview APK (test key) #101 40184741778 with
  `yft-preview-apk` 12596296561 and `yft-preview-trial-apk` 12596046940). Local: `lintDebug`,
  `:app:assembleRelease` (R8), `:app:assemblePreview`, `:app:assemblePreviewTrial`,
  `:extractor-master:test`, `:core-model:test :extractor-api:test :extractor-generic:test
  :extractor-sites:test`: all green.
- R8 (the owner, 2026-10-25: "decide by running the minified build … not by compiling alone —
  YouTube route B, one social ladder and the capture must work in it"): this agent's computer has
  no KVM, so checkpoint 2 adds `.github/workflows/minified-check.yml` (pushes to
  `work/phase-release-*` only): it builds #12 normal and #12 trial as Preview APK does (R8, a fresh
  test key), runs both on the emulator smoke's API 34 emulator and drives them through their
  screens (`scripts/ci-minified-check.sh`, the opt-in `MinifiedBuildCheckTest`, `-e yft.minified
  <package>`; the smoke skips it): YouTube (route B's notice in the trial), X then Facebook (the
  ladder's notice), Facebook's video with the capture's "Play the video…" answered by a tap on the
  video. Each answer is an annotation; a crash or a run that cannot finish fails the job. Static
  review so far: no reflection, ServiceLoader or serialization in Master; `@JavascriptInterface`
  kept (the own solver's bridge); the capture talks by `evaluateJavascript` and `org.json`; assets
  by name; only details lines carry a class name (`ClientLadder`'s `L: <outcome>`, the trial
  check's failure), shown renamed in a shrunk build.
- Checkpoint 2 `29fd1ba9`: Preview APK (test key) #102 40187305143 green — the #12 links the
  owner asked for: `yft-preview-apk` 12596686333 (#12 normal), `yft-preview-trial-apk`
  12596591356 (#12 trial). Minified build check #1 40187305152 and Android emulator smoke #135
  40187305135 red for one cause, the new test class: `fun youtubeRouteB() = check(…)` returned a
  `Boolean`, so JUnit refused the whole class (`initializationError`; the smoke runs every
  androidTest class, and the opt-in check skips itself only inside a test). Fixed in checkpoint 3:
  every `@Test` a block body returning `Unit` (javap: all `public void`); route B's page waits for
  the browser ("Close browser") before it types the address. Work-branch checkpoint validation
  #541 40187305159: green.
- rc.1 (the owner, 2026-10-25): the release-key update test (`IN_APP_UPDATE_PLAN.md` §5.5) WILL
  run in P8 with `1.0.0-rc.1`. This agent prepares the docs only (checkpoint 3): `CHANGELOG.md`
  `## [1.0.0-rc.1]`, `docs/release/1.0.0-rc.1.md`, `docs/RELEASE.md` (the identity table, the
  version code rule, the preview table, the Release draft facts, the test's five steps). What rc.1
  still needs and this agent does not make (a build-config change, the owner's): a commit whose
  `gradle.properties` says `yft.versionName=1.0.0-rc.1` / `yft.versionCode=999901`
  (`VersionCodeRuleTest` accepts it) — the Release draft workflow has no version input, it reads
  the run's commit. No tag, no Release draft run, no publish.
- Also fixed in checkpoint 3: CHANGELOG's "Check automatically is off until you turn it on" — in
  the release it is on by default (`defaultAutoCheck` is the release mode; off in the test mode
  and the previews); `docs/release/1.0.0.md`'s privacy line says the same.
- Checkpoint 3 `b90c955d` (rc.1's docs, the wording fix, the check's test): all green —
  Work-branch checkpoint validation #544 40188796317, Android emulator smoke #138 40188796316,
  Preview APK #105 40188796270, Minified build check #2 40188796284.
- **R8, decided by the run:** the shrunk builds ran through their screens with no crash and no
  R8-type error. #12 normal: X rows, Facebook rows, YouTube's quiet line "Play the video, then tap
  Download again.". #12 trial: `master: x ladder after RESPONSE_CHANGED; main's answer replayed,
  no new ask; trial: Master's rows 3, main's 3; the same item: yes; duration within 1 s: yes` (one
  social ladder works shrunk); Facebook rows; YouTube `master: route B not run: BOT_CHECK is final`
  (YouTube's bot check on CI's address; final by design, so route B cannot be reached there); the
  capture not asked (Facebook gave rows in both builds). No keep rule added (the existing rules
  keep the own solver's `@JavascriptInterface` bridge; no reflection, ServiceLoader or
  serialization in Master). Route B and the capture in the shrunk builds: #12 on the owner's
  phone; a failure there that the debug APK does not show is an R8 fix before the merge.
- Checkpoint 4 (this one, docs only): the release commit's shared docs — `PROGRESS.md`,
  `docs/TEST_MATRIX.md` (S5's run 3, VK1–VK3, VK5, D1–D4, U1–U3, the release commit),
  `docs/PHONE_TEST_NOTES.md` (S5's run 3, VK3, VK5, D1–D4, U3, the U3 fixes),
  `docs/SUPPORT_MATRIX.md` (VK, the ladders in the release, the backup finder, the in-app
  update), README (1.0.0, the install notes, `yft-preview-trial-apk`), the status lines of
  `MASTER_KEY_SOCIAL_PLAN.md`, `docs/IN_APP_UPDATE_PLAN.md` (§0, §5.5, §12) and
  `docs/VK_PLAN.md`.
- The #12 APKs (the owner asked for `29fd1ba9`'s; later commits changed only the check's test and
  docs, so the app is the same): Preview APK #102 40187305143 — `yft-preview-apk` 12596686333
  (#12 normal), `yft-preview-trial-apk` 12596591356 (#12 trial).
- Next: STOP and report (Burmese, short); then the owner's #12, PR #2's merge, R9's first Run
  workflow, P8 with rc.1's update test.
- Rules: no site pack, engine or main lookup change; rc.1 docs only (no version change, tag,
  Release draft run or publish); never `main`, the spike, PR #2, a label,
  `master-recipes-publish`, `release-draft`, signing, the `recipes` branch, a tag or a release,
  `work/phase-vk-master*`; no APK on the owner's phone.
