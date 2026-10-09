# YFT Master Extractor backup

## Hooked selection and native live checkpoint

Dedicated, independently revertible hook/wiring commit:
`2b76e4d75136ff4a3e27f590e1e140a3570c9e6a`.
It contains exactly three production hook files plus two wiring/flag-off test files;
the selection/metadata/presentation algorithm remains entirely in the Android module.

| Hook file | Added | Removed |
| --- | ---: | ---: |
| `MasterFallbackEngine.kt` | 12 | 0 |
| `BrowserMasterFallback.kt` | 10 | 1 |
| `BrowserViewModel.kt` | 19 | 7 |

No existing identifiers were renamed or unrelated blocks reformatted. The ViewModel edits
are confined to existing selection call sites and opt-in presentation routing. App models,
Generic files, existing site extractors, download code and CI workflows are unchanged.

### Fresh checks

- ✅ Original seven selection contracts and five safety guards: all 12 pass.
- ✅ Combined policy/parser/transport-frame/opt-in unit suite: `OK (33 tests)`.
- ✅ Disabled policy skips hook/capture/probe for every primary failure enum. A disabled
  selector equals the legacy engine; a null hook retains legacy refusal and exact-address success.
- ✅ Real flag-off app factory returns the original `None`; every primary object passes through
  unchanged for both generic modes. `OK (1 test)`, isolated caller unit test with fail-fast
  dependency doubles, not an Android UI/device validation.
- ✅ JavaScript transport: 13/13; repository scripts: 30/30.
- ✅ Fragment-index/movie-extends duration tests were first run red (2 failures), then fixed
  using actual `sidx`/`mehd` bytes. No DOM-to-candidate duration assignment.
- ✅ Passive-candidate/primary-result UI isolation tests were first run red (2 failures), then
  fixed with owned presentation keys and recorded ranking.
- ✅ Large delivered TikTok DOM-state capture/refusal-field tests were first run red
  (2 failures), then fixed with bounded focused-item projection. GET/API response-clone limits
  stay unchanged; projected bodies remain at most 64 KiB; access/DRM fields are preserved.

### Latest completed native diagnostics

These use actual desktop native Pause/Play, trusted click receipts, fresh production collector
frames, the hooked production selection policy, normal HTTPS validation and the unchanged
main/More presentation models. They do not prove Android APK sheet rendering.

- ✅ Instagram `Dcwk7e1yHaY`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  More list count 8. Native trusted click 1, capture 358 ms, authorized playback true.
  DOM duration 61,966 ms; independently read selected media duration 61,966 ms;
  selected dimensions 1080x1920. Nine validated candidates; TLSv1.3; 2,363,904 bounded
  response bytes. No invented playing URL, MAIN role or requested ID.
- ❌ TikTok `7670337149526379789`: latest completed pre-focused-state retry is HTTP_STATUS,
  no main/More. Native trusted click 1, capture 353.3 ms, authorized playback true.
  DOM duration 26,166 ms; the one accessible file is only 2,067 ms/720x816 and is correctly
  rejected instead of being labelled the main video. Other media checks fail HTTP status.
  TLSv1.3, 198,483 bounded response bytes. A retry with the focused DOM-state fix is pending.

Overall completion is still ❌: TikTok positive live main/More and actual Android app UI
validation are not yet proven. The old two-run APK gate does not validate this changed code.
No main merge, release/tag, phone APK delivery, full download/mux, all-resolution,
Master-CI-green or merge-readiness claim.

## Authorized Android-owned policy checkpoint

The user authorized minimal hook/wiring edits in the three files listed below, in a separate
revertible commit. The selection algorithm, Generic ad-rule reuse, bounded independent MP4
metadata reads and main/More presentation mapping are implemented only in
`extractor-master-android`. Module policy/parser/frame tests: `OK (13 tests)`; JS: 11/11.
The original seven red contracts remain red until the separate hook commit is applied.

The projection keeps original source identities in the memory-only capture record. Only UI
copies use reserved presentation keys and no claimed post ID, so alternatives are not falsely
declared qualities of one post. The existing MediaCandidate/MediaGroup/otherVideos app models
are unchanged. Duration is read from delivered metadata or independently validated MP4 bytes,
never copied from the DOM. Default/release flags and all original security guards are unchanged.
Live main/More and full Android UI validation are still pending.

## Automatic main/More selection — test-first checkpoint

Date: 2026-10-09. Baseline: `ac9695ce6758fbcace6ea1381f185a9e6b5a8278`.
Branch: `spike/master-extractor-backup` only. No merge, release, tag or phone APK delivery.

### Status

- ✅ A compile-valid failing contract was written and executed before any production fix.
- ✅ 12 offline JVM tests ran: 7 expected selection failures, 5 safety tests passed.
- ✅ All seven failures report `NeedsSelection`, not compilation or fixture setup errors.
- ❌ Automatic TikTok/Instagram main selection is not implemented or live-verified.
- ❌ Main sheet plus More integration is not complete.
- ⏸ Production changes await clarification of the module-only boundary described below.

The only new code is
`extractor-master-android/src/test/kotlin/com/alal/yft/extractor/master/android/MasterMainSelectionContractTest.kt`.
This progress document is the explicitly requested documentation exception.
No unused production selector, fake HTTPS playing URL, fabricated MAIN role or guessed
content identity was added.

### Red contract

The test exercises the existing bounded frame reader, browser session and Master engine.
Independent validator fixtures supply candidate duration/dimensions; the DOM duration is
never copied onto candidates. Instagram uses the generic/no-ID request shape. TikTok keeps
its requested content ID instead of deleting it to force a result.

The seven deliberately failing behaviors are:

1. Known DOM duration: independent candidate duration must be within the inclusive 2-second
   tolerance; a candidate 2.001 seconds away must not qualify.
2. TikTok: a different-duration file must not receive the player's duration.
3. A missing candidate duration must not be treated as a known-duration match.
4. Closest decoded dimensions rank matching candidates; smaller matching files remain for More.
5. Equal candidates choose a stable main and retain the other eligible candidate for More.
6. Unknown DOM duration chooses the longest non-ad candidate and retains alternatives.
7. Ad-domain and far-shorter-duration candidates must not become main or More.

Passing guard cases: paused playback, only one progress sample, DRM evidence, stale navigation
and the disabled default policy. These are explicitly offline unit fixtures, not native
Android gesture evidence, live media validation, or a promise that the sheet already works.
No JS play/seek is used. No live-manifest or protected-media refusal is overridden.

Execution: JDK 17.0.20.1, Kotlin 2.0.21/JVM target 17, JUnit 4.13.2.
The standalone runner compiled 50 unchanged production source files and this one new test.
It did not run Gradle/Android instrumentation or the full application regression suite.
Equivalent module test command, once the Android toolchain is available:

```sh
./gradlew :extractor-master-android:testDebugUnitTest \
  --tests com.alal.yft.extractor.master.android.MasterMainSelectionContractTest
```

JUnit result: `Tests run: 12, Failures: 7`; exit 1 is intentional for this red checkpoint.
Do not describe this checkpoint as green CI or as the completed fix.

Fresh ancillary checks: JS transport 11/11 and repository script tests 30/30 passed.
Exact two-file allowlist, Kotlin lines at most 100 characters and the local equivalent
secret/sensitive-path scan passed. The 50 compiled production-source hashes and the capture
JS hash match the unchanged baseline. No GitHub Advanced Security scan or Android build is
claimed by these local checks.

### Confirmed production boundary

The requested full behavior cannot currently be wired through the Android capture module's
public contract alone:

- `CaptureFrame.kt` does not retain the player's duration or decoded dimensions yet. This
  part can be changed inside the allowed module.
- `WebViewPlaybackCapture` returns only `PageSnapshot`. That contract has no explicit selected
  main/More result or duration/dimension selection policy.
- `MasterFallbackEngine.kt` owns a private `select`. It runs before media validation, so
  independently probed candidate durations never reach selection when the player uses a blob
  URL. It accepts an exact HTTPS playing-address match or one explicit MAIN group; otherwise
  it produces `NeedsSelection`. There is no injected selection hook.
- The engine probes and returns only selected candidates; unselected alternatives are not
  passed through as More.
- `BrowserMasterFallback.kt` applies the caller's content-identity filter.
- `BrowserViewModel.kt`'s generic Master path requires `groups.singleOrNull()` and selects the
  group without an other-video count. Returning several legitimate video groups alone does
  not implement the requested main/More sheet.

Rewriting a blob address as a supposedly observed HTTPS playing URL, inventing page-owned
MAIN evidence or assigning different posts one fabricated identity would bypass these checks
instead of implementing the requested policy. Those workarounds were not used.

The smallest proposed scope clarification is permission for selection-hook/result-routing
edits in these three files, with the selection algorithm and new capture facts remaining in
`extractor-master-android`:

- `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/MasterFallbackEngine.kt`
- `app/src/main/java/com/alal/yft/detection/master/BrowserMasterFallback.kt`
- `app/src/main/java/com/alal/yft/feature/browser/BrowserViewModel.kt`

Existing reusable Generic-workflow rules were located: `BrowserObservationMapper.adRole`,
`MediaGroups.isPreviewAddress` and `PageVideoFacts.isFarShorter`. Reuse them without changing
their files. The requested duration-match tolerance remains exactly 2 seconds, rather than
the Generic long-video percentage tolerance.

### Live acceptance is still pending

No new live test is presented as a fix validation in this checkpoint. The latest previously
recorded native public-player diagnostics remain:

- ❌ Instagram: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.
- ❌ TikTok: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.

The prior two-run Android gate passed with the same old APK pair. It does not validate a
future changed implementation. Default/release opt-out, 2.5-second capture timeout, independent
playback-progress authorization, DRM rejection and navigation-generation checks are unchanged.
Existing site extractors, Generic files, download code and CI workflows are untouched.

Next, after the boundary is clarified: make the red contracts pass through real production
wiring, rerun relevant guards/device checks, then perform native TikTok and Instagram live
validation. Completion requires logs showing automatic main selection without
`NEEDS_SELECTION` and actual main/More routing. TLS/media-byte, full download/mux, all
resolutions, Master CI green and merge readiness remain unverified.