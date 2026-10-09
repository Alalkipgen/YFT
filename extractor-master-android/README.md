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

App wiring and real Android playback validation are the next milestone. This module checkpoint
alone is not a working app feature or Instagram/X support proof.