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
progress samples within 2.5 seconds. The `3acfd4f8` harness compiled successfully. Before
another sandbox reset, a fresh offline run passed 1,422 cases with zero failures/errors and
67 skips; JS 11/11, repository scripts 30/30 and both lints passed with zero errors (96 app
warnings). Debug/test opt-in was true and release false; these are previous-run results.

The next device attempt stopped before tests: SystemUI ANR owned focus after boot and the
APK update timed out. Temporary display-size override was lost on reboot. The next attempt
sets AVD hardware permanently to 480x854, density 240, with 1,536 MiB guest RAM rather than
1,024 MiB. After another restore, the exact source and matching prebuilt APK session backups
were recovered and hash-checked, avoiding a new build. Production code/security limits are
unchanged. Two aggregate repeat passes remain pending; one earlier 4/4 pass is not the gate.

### New-chat rebuild checkpoint

The previous session's binary attachments were unavailable in the new chat. Source was cloned
at `23f0c7e` and verified code-identical to `3acfd4f8`; only recovery documentation differs.
A fresh Java 17/SDK 35 x86_64 debug/test rebuild succeeded with `-Pyft.masterCapture=true`.
Fresh Android-module unit tests passed 21/21, JavaScript transport 11/11 and repository scripts
30/30. Generated debug capture is true and release false. The full 1,422-case regression and
lint totals above remain earlier-run results, not new runs.

- App SHA-256: `1ba2334f1c4d7bff4c3118d08e2485540115f22275a5d666d39beca46373c90c`.
- Test SHA-256: `bb27795923ebb6148476dd583268e8418d267923f39618fc8961677608fb4ce4`.
- Shared debug certificate SHA-256: `369d93b78f43a8252d45de7200e1173748bc55339cb88f3a3947cb4d0156b172`.

The local runner requires explicit APK paths, verifies installed APK bytes before skipping
reinstallation, rejects native ANR/focus blocks and only accepts exact `OK (4 tests)`. The new
480x854/density-240, 1,536-MiB, two-core API 29 AVD is cold-booting separately from Gradle.
That preflight and the two-consecutive-pass gate are now complete; see the finalization below.
Production capture, adapters, downloads and CI workflows are unchanged. No Instagram/X live
result, full download/mux proof or merge readiness is claimed.

### Android fixture gate finalized — 2026-10-09

After another reset, the identical signed app/test binaries and manifest were restored from
this session's backups, not rebuilt. SHA-256, signer and source/harness hashes were verified.
API 29 x86_64 / WebView 74.0.3729.185 ran at 480x854, density 240, 1,536 MiB RAM and two
software-emulated cores. Device-side screencap/pull returned a valid 122,310-byte SystemUI ANR
image; the visible native Wait action restored Launcher focus, verified by WindowManager and
a 191,958-byte screenshot. No focus guard was relaxed.

The same `3acfd4f8` native-tap/sustained-readiness harness on source checkpoint `dbeee695`
passed both consecutive complete class runs with one unchanged APK pair:

| Run | Required aggregate | JUnit time | Instrumentation wall time |
| --- | --- | --- | --- |
| restored-pair1-1 | OK (4 tests) | 103.893 s | 127 s |
| restored-pair1-2 | OK (4 tests) | 78.278 s | 94 s |

Installed APK bytes matched before and after; reinstall was skipped on the second run only
after checking its hashes. Both runs passed actual trusted native playback, paused-preload
refusal, DRM refusal before probing and old-generation navigation/disposal rejection.
Readiness observations never authorized capture; production still collected its own fresh
progress samples within its unchanged 2.5-second timeout. Instrumentation never played,
sought or forged playback evidence. Fresh JS 11/11 and repo scripts 30/30 passed again.

The APKs, strict runner, native setup and redacted two-run instrumentation/logcat metadata
were backed up as session files. Earlier full regression/lint results remain earlier-run
evidence. This milestone is the offline Android fixture gate only, not live Instagram/X,
TLS/full download/mux, all resolutions, universal player coverage, CI green or merge approval.

### Public social-video live preflight — 2026-10-09

The owner requested nine sites. This first pass uses real public desktop-browser pages and
browser-native controls, not an Android live result or a full Master/download support claim.
No login, bot/age restriction or DRM bypass was performed; production remained unchanged.

| Site | Observed public result | Boundary still unverified |
| --- | --- | --- |
| Instagram | Native player-surface resume; 720x1280, readyState 4; fresh progress in 360.7 ms; 60 request observations, eight bounded payload observations | Full Master engine, HTTPS prefix, rendition list, app/download |
| X | Main-video native Play, one trusted receipt; 720x1280, fresh progress in 352.6 ms | Resumed buffered video supplied zero new requests/payloads; media/prefix/app validation pending |
| Facebook | Public 20.921-s ISS teaser played at 1280x720 from HTTPS | Teaser is not full/4K/main-video proof; check the discovered non-teaser sample |
| YouTube | Public page demanded sign-in to confirm not a bot | Access-blocked here; no login or workaround attempted |
| TikTok | Public NASA video visibly played at 720x1280, duration 26.166 s | Native-Play capture, P40 extraction and download remain unverified |
| Reddit | Public page displayed Prove your humanity | Access-blocked here; target native video was not verified |
| Vimeo | Public page rendered a readyState-0 video placeholder | Native playback, rendition and capture remain unverified |
| Dailymotion | Public caption and embedded iframe player rendered | Iframe playback and top-frame capture boundary remain unverified |
| Threads | Public caption rendered; no top-frame video in the sample | Player availability and capture remain unverified |

The unmodified capture asset SHA-256 is
`13fefa36d643ea2b339d8db5a8c0de6c0f57b2cd3d5a098240831fc4fe0817dc`.
Instagram samples progressed 0.444536 to 0.802345 s; its diagnostic receipt listener missed
an overlay ancestor and recorded zero receipts. The actual native click occurred; that zero
was not rewritten or used as authorization. Earlier Instagram autoplay displayed 1080x1920.
X main-video samples progressed 10.880927 to 11.232045 s; its offscreen recommendation was
not selected. Both samples fit the unchanged 2.5-second bound and reported no DRM. These
browser packets did not invoke the full engine or independent HTTPS-prefix validator.
Both clips were paused with native controls and collector hooks were disposed afterward.

Evidence exports contain sanitized states, dimensions, counts and hosts only, never cookies,
signed media addresses or raw API bodies. Public page URLs remain in the recovery bundle.

#### Available-resolution feasibility, not an all-quality support claim

Existing readers can collect supplied Instagram video versions, X variants, YouTube formats,
Facebook progressive/inline DASH and TikTok bitrateInfo URLs. Actual HLS/DASH/progressive
variants may expose several qualities. A single MP4 does not create missing 240p, 360p, 480p,
720p or 1080p encodings; creating those would require separately scoped transcoding.
TikTok nested PlayAddr Width/Height requires reviewed metadata mapping/tests before exact
rendition labels. A portrait 1080x1920 frame must not be called 1920p or proof of all qualities.
No production mapping, existing extractor, download or CI code was modified.

Next: finish the remaining accessible native-player checks, then add a reviewed test-only
live harness for real Master-engine/HTTPS-prefix validation. Full download/mux, all qualities,
Android live-site coverage, Master CI green and merge readiness are still pending.

### Stdin-only live engine diagnostic — 2026-10-09

A sandbox-only Java 17/Kotlin 2.0.21 harness recompiled 46 unchanged production sources,
including the real frame reader, browser session, engine and HTTPS validator. Each source
was byte-compared to b07bd5d2. Published Maven hashes were checked for 13 artifacts. The
initial missing compiler-host annotation dependency was corrected outside the repository.
No production, app, existing extractor, download or CI file changed; no new APK was built.
This diagnostic uses a test-only recoverable RESPONSE_CHANGED input, not an actual Android
primary-adapter failure or full WebView/caller integration result.

Raw packets stay in memory and pass by stdin into the unchanged frame reader/session with
original sample clocks. The driver does not authorize playback manually, invoke JS play/seek,
change frame time/addresses or persist packets, API bodies or cookies. Actual native Pause
and Play plus read-only natural readiness precede a fresh bounded capture. Readiness is not
submitted as authorization. Visible optional Close controls are dismissed natively only.

- Instagram: native resume, one trusted receipt; 1080x1920, readyState 4, no DRM. Fresh
  samples 1.609261 -> 1.966620 s in 359.8 ms, generation 1. Production session accepted
  two raw frames: authorizedPlayback=true, 68 bounded requests and eight payloads.
  Real Master returned NEEDS_SELECTION in 111 ms for requested shortcode Dcwk7e1yHaY.
  Zero probes, validated candidates, TLS handshakes or body bytes: selection refused to
  guess. This is NOT successful extraction, HTTPS-prefix validation or a download result.
- TikTok: native Pause at 720x1280/readyState 4. One attempt stopped at actual resume
  verification; another stopped before capture on a 25-s decoded-player timeout. Neither
  reached live engine validation. No P40/live Play-and-Capture pass is claimed.
- Facebook: second public NASA sample 1414737229683186 rendered 640x360 with 83.283-s
  duration metadata. Native Pause selection stopped the diagnostic. Inspection found a
  visible Play video role-button; native activation/resulting controls are next.

Compiled runtime, driver, source/dependency hashes and sanitized failures were backed up.
Inspect actual expected-ID/focus mapping for Instagram without deleting an ID or weakening
selection; fix remaining native control/readiness paths only in the test harness. The old
same-APK Android two-pass gate remains valid. HTTPS-prefix, full download/mux, all resolutions,
Android live caller, Master CI green and merge readiness remain unverified.

### Caller-faithful focused-media limitation — 2026-10-09

Caller-faithful live diagnosis (2026-10-09): the actual app factory registers TikTok,
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

### Native public-player diagnostic results — 2026-10-09

Native public-player diagnostic checkpoint (2026-10-09): test-only native mouse hover
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
