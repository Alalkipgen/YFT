# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

- Phase/branch: 11, Group A, `work/phase-11-download-flow` (main base `2f6284f`). P9 `13bbf05` and P10 `bdc9f88` pushed; P11 completed locally (1124 tests, 66 render skips; zero failures/errors, lint zero errors), CI retry for the final checkpoint pending. STOP AFTER P11. No main merge or signed release/P8 without owner approval.
- Owner request (2026-10-05): start P9/Group A; do not edit workflows or Plan Doc. `.github/workflows/`, `docs/FIX_ADD_PLAN.md` and prompts remain byte-identical to `d642607`; progress is recorded here, not the protected status board. Latest owner instruction: finish P11 and stop; do not start P12.
- Rules: ADR-006 public videos only; no DRM/paywall/private/age-gate bypass; adapters never sign in. Never print/commit cookies, tokens, signed media/image URLs or keys. Keep testTags, new Kotlin lines <=100, WebView main-thread only. No reset --hard/clean/stash. One Gradle command at a time; temporary files outside the repo.
- Last completed task: P11 — metadata-first rows, honest estimates, two background size checks, stable IDs/order/selection even after failure, measured ladder compact and native HD/SD under More, final selected-file check with 1/3 s transient retries and unavailable-quality error. Focused and full root validation pass; phone check remains OWNER CHECK.
- Recovery: sandbox reset during P11 full validation. P9/P10 were safely pulled from origin. P11 source/test scripts restored from session events 11542/11549/11560; original regression proof is event 11646. SDK NDK/CMake and local low-memory helper re-created. Do not claim the interrupted root build passed.
- Validation: P11 full validation: core-model 63 tests, core-media 25 tests, extractor-sites 168 tests, app 614 tests (66 skipped); 0 failures/errors; :app:lintDebug 0 errors. Restored P10 baseline passed: model 63, data 17, browser 79, media 25, sites 168, app 607 (66 skipped); zero failures/errors. Root app/browser/data/download/media lint reports each have zero errors. Initial one-worker combined root command stalled and was stopped; explicit module tasks and the final two-worker CI-equivalent root command both pass.
- Live P11: production parser of public Facebook reel, no media HEAD: HTTP 200, www.facebook.com, /reel/1603698891196107/, 136045 bytes; AVC heights 358/720, AAC 1, native files 2 (five candidates). No signed URL/body/session output. One earlier public response was RESPONSE_CHANGED; navigation headers + trailing slash returned parseable public media.
- CI: P9 validation passed 37363825709; preview/emulator never acquired hosted runners. P10 emulator passed 37367554221; validation failed 37367554173 (logs require authentication; exact cause not verified); preview 37367554281 never acquired a runner. P11 f82427b validation failed 37377253562 at Validate production checkpoint; local full root equivalent passes. Public CI failure summary is being inspected. P11 f82427b preview 37377253600 and emulator 37377254034 both passed. Final CI retry uses deterministic verified localhost TLS fixtures and wider streaming/cancellation test headroom; production budgets and TLS checks unchanged. The original validation failure cause is not verified because full logs require authentication. Workflows unchanged.
- CI fix (owner log of run 37380484743, job 112000878738): validation failed only in `HeadlessPageFetcherTest.droppedConnectionAndTransientStatusesRetryBut404DoesNot` (ClassCastException: the first fetch returned Failed). Cause: GitHub's runner maps `localhost` to 127.0.0.1 and ::1; after the dropped connection OkHttp postpones the failed 127.0.0.1 route and the explicit retries (implicit retries are off since P10) went to ::1, where MockWebServer was not listening. Reproduced locally with `JAVA_TOOL_OPTIONS=-Djdk.net.hosts.file=<CI-like hosts>`. Fix (test only): the server binds 127.0.0.1 and the test client resolves its host only there. With the CI-like hosts file: core-browser 79 pass; every unit-test task re-run with `--rerun` passes (1124 tests, 66 skipped, 0 failures); lint 0 errors. Production code unchanged. Earlier P10/P11 validation runs stopped at this task, so other modules' tests had not run on CI since P9.
- Environment: `/data/YFT`; JDK17 `/data/toolchains/jdk17`; SDK `/data/toolchains/android-sdk`, platforms 35/36, NDK 27.3.13750724, CMake 3.22.1. Source `/data/yft-env.sh`. `/data/gw-safe.sh`: no daemon, one worker, Gradle heap 768 MiB/metaspace 384 MiB, in-process Kotlin compiler. `/data/gw-ci-safe.sh` completed full root validation with two workers, 1024 MiB heap, 384 MiB metaspace, ActiveProcessorCount=2, TieredStopAtLevel=1 and in-process Kotlin. After another reset, recreate helpers and re-install missing SDK parts; git pull --ff-only first. Push uses the existing SSH deploy key via core.sshCommand, never print it.
- Limits: no local KVM/emulator; 66 native renders skipped, no native pixel-review claim. Datacenter YouTube bot checks require owner phone verification. Plan/status/prompts/workflows remain untouched.
- Next: owner (2026-10-06) asked to continue with P12 while P11 CI finishes. P12 is IN PROGRESS: design only, no P12 code committed yet. Design notes: site video page (adapter `handles`) -> button action OPEN_PAGE_VIDEO with spinner while the page lookup runs; one page-scoped lookup per video key (site:contentId, new `SiteAdapterCoordinator.videoKey`) shared by taps and feed focused links; `DetectedMediaStore` gets a pending page video (loading/failure/Retry) that `QuickDownloadViewModel` waits on; probes await the page lookup and run only if it found nothing; `MediaGroups.pageVideos(adapterSite=true)` returns named videos only; generic pages with several videos open `MediaGroups.mainVideo` (playing via a tap-time script, else largest) with an "Other videos on this page (N)" row. Regression proof idea: BrowserRouteTest taps the FAB twice during a gated lookup and answers any evaluated script via ShadowWebView `getLastEvaluatedJavascriptCallback` (old code: two adapter calls).
- Last pushed checkpoint: P11 CI fix (this checkpoint); final P11 cfad7b7; P11 feature f82427b; P10 bdc9f88; P9 13bbf05; PLAN d642607.
- Last updated: 2026-10-06

## Track B (extractors)

Agent B, branch `work/phase-11-extractors` (from `work/phase-11-download-flow` `2071342`), only
P14 then P15; Agent A works on P12, P13, P19, P16, P17, P18. The Plan's status board is not
edited; status is recorded here.

- **P14 — YouTube asks visionOS first: OWNER CHECK (2026-10-05).** Result: the lookup asks
  `VISIONOS` before the watch page (no cookie, authorization, visitor data or page key) and
  stops when the answer is complete (playable, title and length, every format with a direct
  address and `contentLength`, an AVC video with sound): one request of about 17 KB. Anything
  else runs the T16 chain unchanged; the page's verdict stays final. Plan adapted: (1) visionOS
  alone has no progressive 360p, so merged AVC + AAC rows now include 360p; (2) visionOS is asked
  once per lookup and the chain reuses that answer; (3) the first request has no visitor data,
  because no page was read. Regression proof: pre-P14 extractor and parser put back → 17 of 48
  `YouTubeExtractorTest` tests failed (e.g. `a complete visionOS answer is the whole lookup,
  without the watch page`); restored, `cmp` identical. Live (sandbox): `dQw4w9WgXcQ` 1 request,
  16,677 B, 180 ms (was 4 requests, 185,088 B), rows 4K/2K VP9 + 1080–360 AVC merged + AAC;
  4K and Short videos bot-checked on this IP (chain unchanged); age-restricted stays
  `LOGIN_REQUIRED`. Validation: extractor-sites 173, app 614 (66 skipped), 0 failures; lint 0
  errors. Owner check: on the slow line a YouTube link opens clearly faster; 720p, 1080p and 4K
  download and play.
- Shared files: none.
- Next: P15 (Facebook public page first), then merge `origin/work/phase-11-download-flow`.
