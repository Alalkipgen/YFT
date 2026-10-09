# Android Play & Capture boundary

Owner-approved follow-on work in `spike/master-extractor-backup`. Not approved for merge.

## Module milestone

`WebViewPlaybackCapture` decorates the existing `BrowserObservationSink` and attaches to the
existing visible WebView. It does not replace its clients, security policy or download pipeline.
The producer passes bounded page/player/API data and HTTPS media observations to
`:extractor-master`. There is no `JavascriptInterface`, privileged page-to-native bridge,
response proxy, unattended sign-in, automatic play or screen recording.

- Per-tab navigation generations clear previous document and SPA observations.
- WebView operations and URL-specific CookieManager context acquisition run on the main thread.
- Native observed headers are kept only with the exact observed URL. Script data cannot supply
  cookies or replay headers; newly discovered URLs receive host-acquired origin-specific context.
- Two visible, unpaused progress samples are required for authorized-playback evidence.
  Preload, paused media, one sample and a large seek are insufficient.
- MediaKeys/encrypted evidence is terminal until navigation.
- Script packets: 256 KiB characters, 64 requests, 16 accepted payloads; each body is 64 KiB.
  Page-global cloning is bounded and excludes getters/toJSON.
- JSON fetch clones have a bounded byte read and time limit. Original fetch promises, bodies
  and XHR behavior stay with the page. Hooks are restored on disposal when still owned.
- The collector is top-frame only. Cross-origin iframe-only players and native/MSE opaque
  sources may require a future reviewed producer; no universal playback claim is made.
- Injection uses evaluateJavascript at lifecycle/sample points, not document-start injection.
  Requests are observed natively, but JSON delivered before installation may be unavailable.

## Validation at this milestone

`./gradlew --no-daemon :extractor-master-android:testDebugUnitTest
:extractor-master-android:lintDebug` passed: 15 tests, zero failures/errors/skips, no lint issues.

Existing Android baseline passed before editing: core-browser 138 tests; app 807 tests,
zero failures/errors and 66 existing skips; Android test Kotlin compiled. Master JVM baseline
had 76 offline passes and one optional live-smoke skip.

## App connection — validation in progress

The browser now constructs the producer only in an explicitly enabled debug/preview build:
`-Pyft.masterCapture=true`. Default builds and the release variant remain disabled. Successful
primary adapters are returned untouched. Recoverable failures can request one fresh capture;
generic pages request it only after a user Download tap found no main video. Disabled matches,
DRM/private/geo/network/rate-limit failures remain outside the fallback. Device merge gates
and the normal final resolver/download pipeline still apply.

The initial app-wiring run passed: Android module 18 tests, app 826 cases, zero failures/errors
and 66 existing app skips. Both lints passed and four Android fixture tests compiled. With the
opt-in property true, generated debug/test flags were true and the release flag stayed false.

Follow-on hardening reuses the production focus probe to reject a different feed video instead
of labeling it with the requested ID. POST responses are not turned into GET media observations,
and known VAST preview markings reach the Master's explicit preview veto. Three new focus tests
passed in the final offline run: 1,422 cases, zero failures/errors, 67 existing/optional skips
(21 Android-module cases; 826 app cases). JavaScript transport tests pass 11/11, repository
script tests pass 30/30, and both lints have zero errors; app lint retains 96 dependency/API/vector
warnings. Internal x86_64 debug and test APKs were built; release remained disabled.

The first actual API 29 software-emulator run passed paused-preload, navigation/disposal and DRM
refusal, but the playback-success case returned `NeedsPlayback`. The test tapped Play and sampled
after a fixed 500 ms without verifying that the software decoder had started playback. The test now waits for read-only
proof of user-triggered playback before invoking capture, and verifies collector installation.
Production capture budgets and playback authorization are unchanged. Repeat device validation
is pending. No Instagram/X live support, full download/mux result, universal iframe coverage or
merge approval is claimed.
### Device retest checkpoint

The readiness-only retest passed two fixtures and failed two: the DRM case unnecessarily waited
for playback, while the positive case reached playback readiness but still returned NeedsPlayback.
Current changes remove that unrelated DRM prerequisite, tap the actual visible fixture control,
use a real 10-second neutral clip, and bind the collector once per lookup instead of resending the
entire script before every sample. The 2.5-second budget and two-progress-sample authorization
remain unchanged. Rebuild and repeat device validation are pending; no green device claim yet.

### Restored-device isolation/readiness checkpoint

The unchanged measured-tap harness was reproduced on a restored API 29 x86_64 device with
WebView 74.0.3729.185: the full class passed 3/4, while the positive case alone passed.
Logcat recorded SystemUI/input-channel failure and an ANR window during the full-class
positive test; test ordering alone is not established as the cause.

The test-only follow-on releases its fixture composition before activity teardown, verifies
the WebView and capture scope are gone, waits for visible native window focus, and checks
that the single real tap delivered a trusted click to the fixture. The click receipt is not
used as production playback evidence. Production code and all capture/security limits are
unchanged. The repaired harness (`0a341674`) compiled and the Android module passed 21/21;
internal x86_64 app/test APKs were rebuilt. Debug/test opt-in flags were true and release false.
The first guarded full class passed 3/4 but correctly refused the tap: WindowManager and a
screenshot confirmed a native SystemUI ANR dialog owned foreground focus. The isolated guarded
case also refused while that dialog remained. The inspected native Wait action cleared it.
A repeat then stopped before instrumentation on a diagnostic logcat-clear error; the local
runner now treats that diagnostic action as bounded/best-effort while retaining strict
`OK (4 tests)` assertions. With native focus restored, the unchanged full class reached actual trusted playback but
still passed 3/4: the positive capture returned NeedsPlayback after 2,949 ms. The same positive
case alone passed. Decoder startup/skipped-frame diagnostics suggest testing software-rendering
load and readiness next, not weakening the 2.5-second cap or two-progress-sample authorization.
Reduced display load (480x854, density 240) with the same signed APKs produced one
`OK (4 tests)` aggregate pass; the next full class returned NeedsPlayback in the positive case
after 3,660 ms. That does not satisfy the two-consecutive-pass gate.

The next test-only readiness change observes at least 0.5 seconds of natural video progress
and future decoded data after the real trusted tap, before starting production capture. It
handles the fixture's real loop but never plays, seeks, changes the timeline, or submits those
readiness observations to `session.accept`. Production still requires its own two fresh
progress samples within 2.5 seconds. Rebuild and aggregate repeat validation remain pending.
