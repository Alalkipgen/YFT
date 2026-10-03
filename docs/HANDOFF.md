# Handoff

## Current handoff (2026-10-03)

- **Phase:** 8 — field fixes, in progress. T01/T03/T04 are implemented; their phone checks
  remain. T01/T03 and the T04 milestone pass real-WebView CI. T02 is DONE. Plan, status board and decisions:
  [`FIX_PLAN.md`](FIX_PLAN.md). Prompts: [`prompts/`](prompts/README.md).
- **Branch:** `work/phase-8-field-fixes`, created from `main` at `28930cf`.
- **Releases:** `1.0.0-beta.1` published 2026-10-02. `1.0.0-beta.2` (versionCode 2) is a signed
  draft pre-release: tag `v1.0.0-beta.2` on `39ea049`, `video-downloader-1.0.0-beta.2.apk`
  3,376,449 bytes, SHA-256 `3f5b4c74b02e61bf2572a7248aef3a35b92ece3c6f7cf8e0770bc661cee53a95`,
  certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (same key as beta.1).
- **Owner's phone test of beta.2 (2026-10-03):** P1 a public Facebook reel asks to sign in; P2 the
  empty browser hides the address bar; P3 every page load in the browser closes the app; P4 YouTube
  fails with a sign-in message; P5 a TikTok video is found but the download fails. Causes with
  evidence: FIX_PLAN §4 (F1–F8).
- **Owner change (2026-10-03, recorded at his request):** rules and policy changed —
  [ADR-006](decisions/ADR-006-owner-override-any-working-method.md) allows any working technique
  for public videos (device clients, PO tokens, bot-check workarounds, the browser session); DRM,
  paid, private and age-restricted content stay out. D2 = A + B + C. T10 and T15 are SKIPPED: no
  merge or release between phases; all tasks stay on `work/phase-8-field-fixes`. After T19: merge
  into `main`, push, tag `v1.0.0-beta.3` and release the APK signed with the release key
  (approved for T19 only). Order: T16 → T17 → T09 → T12 → T11 → T13 → T14 → T18 → T19, task
  after task without waiting.
- **Next action — agent:** T16 — YouTube client strategy A + B + C
  ([`prompts/T16-youtube-client-strategy.md`](prompts/T16-youtube-client-strategy.md)), IN
  PROGRESS; then continue in the owner's order. T08 is OWNER CHECK (CI green: checkpoint
  https://github.com/Alalkipgen/YFT/actions/runs/37159270955, emulator
  https://github.com/Alalkipgen/YFT/actions/runs/37159270920; phone: paste a YouTube link →
  found or the bot-check message → Copy details → send them); T07 is OWNER CHECK (TikTok Home
  and browser download/playback on the phone); T06 is OWNER CHECK (Facebook Home
  download/playback on the phone); T05 is DONE; T04 is OWNER CHECK: final tests, all 9
  individually inspected renders and the actual release debug-action guard pass.
- **Next action — owner:** answer D1 (clipboard check on open, T11) and D3 (MP3, T18) in
  FIX_PLAN §3; D2 is answered. beta.2 stays a draft; the T19 release replaces it.
- **T01 validation (2026-10-03):** core-browser 53 tests, app 386 (41 renders skipped), 0 failures;
  lint 0 errors, 83 existing warnings. The strict off-main regression failed on the old code and
  passed on the fix. Use the memory-safe command in `TEST_MATRIX.md` on this 4 GiB sandbox.
  Phone check: Your sites, Open in browser and typed Go load without closing the app.
- **T01 CI:** `7179637` passed the full checkpoint workflow:
  https://github.com/Alalkipgen/YFT/actions/runs/37139803672 (`yft-debug-apk`).
- **T02 implementation:** instrumentation APK compiled; app 386 tests, 0 failures, lint 0 errors
  (95 warnings); diagnostic/collector tests 10/10 and workflow/collector lint pass.
  First emulator run on `8151813` passed 3 tests with 0 fatal exceptions but failed collection:
  https://github.com/Alalkipgen/YFT/actions/runs/37141758594. Keep APKs installed until pull,
  then uninstall; regression FAILED on the old collector, PASSED on the repair.
- **T02 CI GREEN (`e9e1a09`):** https://github.com/Alalkipgen/YFT/actions/runs/37143005734 —
  3/3 real-WebView tests, 0 fatal exceptions, 3 required PNGs collected. Debug APK:
  https://github.com/Alalkipgen/YFT/actions/runs/37143005667. Native PNG download needs
  authentication (HTTP 401); no pixel-review claim. Empty-page controls pass on API 34;
  loaded/found address bounds are missing from UIAutomator, so T03 must retain field semantics.

- **T03 OWNER CHECK (`9be6b43`):** final core-browser 53/app 405 (45 render skips),
  0 failures/errors, lint 0 errors/95 warnings; instrumentation APK compiled. Empty-surface
  and 5 px largest-text count regressions failed on old code and pass on the fix.
  All 8 final browser PNGs passed individual visual inspection after the heading repair.
  Native CI GREEN: https://github.com/Alalkipgen/YFT/actions/runs/37146164024 — 3/3 tests,
  0 fatal exceptions, 3 PNGs; empty native WebView absent, Example Domain content loaded,
  loaded/found address bounds restored above the WebView. Debug APK:
  https://github.com/Alalkipgen/YFT/actions/runs/37146164049. Phone checks and native pixel
  review remain; artifact download still requires authentication.

- **T04 OWNER CHECK:** local no-backup 64 KiB crash report, previous-handler delegation,
  About manual View/Copy/Share/Delete, debug-only confirmed long press, release DEX guard,
  optional adapter failure steps and memory-only Home Copy details. Final tests: core-model 35,
  extractor-api 24, extractor-sites 91, app 434 (54 render skips), 0 failures/errors; app lint
  0 errors/95 warnings; instrumentation APK compiled. Python 13/13, shell checks and new Kotlin
  line audit pass. Copy details regression failed old/passes fixed. All 9 final native-canvas
  PNGs pass individual visual inspection; 200% Home actions wrap, and dialog fontScale is real.
  Separate final unsigned release build passes metadata/alignment/debug-action checks; not
  installable or published. Final ce881f7 CI GREEN: emulator
  https://github.com/Alalkipgen/YFT/actions/runs/37151155280 (3 tests, 0 failures/fatal exceptions)
  and checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37151155259.
  Phone: debug About version long press → confirm crash → restart → View/Copy/Share/Delete;
  on an unexpected app closure, explicitly Share the local report. No automatic upload.

- **T05 DONE:** shared honest desktop YFT identity, navigation-only header defaults, no borrowed
  browser cookies, and the same identity on direct/scanned candidates. JSON/API and browser
  session headers are unchanged. Old non-null-UA regression failed, fixed passes. Full tests:
  core-model 38, core-browser 54, extractor-api 24, extractor-generic 7, extractor-sites 95,
  app 436 (54 render skips), 0 failures/errors; lint 0 errors/95 warnings; instrumentation APK
  compiled; Python 22/22, shell/style checks pass. Public live check: HTTP 200 on
  www.facebook.com/reel/1603698891196107/, 609875 bytes, HD marker present, no cookies or query
  output. This proves page delivery, not Facebook extraction/download (T06). No phone check yet.
  cbc92e5 CI GREEN: emulator https://github.com/Alalkipgen/YFT/actions/runs/37152854933 and
  checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37152854989.
- **T06 OWNER CHECK:** Facebook public reels no longer fail as DRM when `drm_info` carries only a
  certificate; licence maps, graph licence URIs and explicit flags still block. Share redirects,
  single entity decoding, the Facebook suffix trim and DASH/rendition-only heights are tested.
  The old certificate-only regression failed, fixed passes. Full tests: extractor-api 26,
  extractor-sites 109 (110 after the og:image fix), app 437 (54 render skips), 0 failures;
  lint 0 errors/95 warnings; instrumentation APK compiled. Live: the owner's share link parsed
  to HD, SD and DASH; ranged SD GET 206. Phone: paste the link on Home → download → it plays.
  af55f34 CI GREEN: checkpoint/debug APK https://github.com/Alalkipgen/YFT/actions/runs/37155542368
  and emulator https://github.com/Alalkipgen/YFT/actions/runs/37155542372.
- **T07 OWNER CHECK:** a Home lookup keeps the cookies its own responses set (domain-checked,
  in memory only, no cookie jar). Without a WebView cookie, TikTok media gets the page's
  `tiktok.com` cookies that a browser would send to that media address; the browser path keeps
  the WebView cookie, and downloads still send cookies only to the media URL's own origin.
  The old code failed the regression, fixed passes. Full tests: extractor-api 29,
  extractor-sites 113, core-download 81, app 439 (54 render skips), 0 failures; lint 0
  errors/95 warnings; instrumentation APK compiled. Live: the public scout2015 video gave 5
  candidates; ranged media GET 206 with the cookies, 403 without. Phone: paste a TikTok link →
  download → it plays; the same from the browser. ba165c5 CI GREEN: checkpoint/debug APK
  https://github.com/Alalkipgen/YFT/actions/runs/37156848673 and emulator
  https://github.com/Alalkipgen/YFT/actions/runs/37156848739.
- **T08 OWNER CHECK:** YouTube's "confirm you're not a bot" is `BOT_CHECK` (not definite, no
  generic fallback) with the message to open the video in YFT's browser, let it play, then tap
  Download; real sign-in, age and private verdicts are unchanged. SABR-only answers are
  NO_MEDIA_FOUND with "SABR only". Copy details lists each client asked (name, status, reason,
  formats with URLs, protected count, SABR flag, outcome), sanitized. The old code failed 8 tests;
  fixed: extractor-api 29, extractor-sites 123, app 441 (54 render skips), lint 0 errors. Live
  (sandbox IP): dQw4w9WgXcQ needs the player script (WEB: 1 protected format + SABR, embedded
  refused 152-18); aqz-KE-bpKQ gets the bot check and the new message. aa915ff CI GREEN
  (links above). The owner then allowed working around bot checks (ADR-006); that is T16.

## Known limitations (beta.2)

- The in-app browser closes the app on every page load (T01) and hides its address bar while
  empty (T03).
- Home lookups: Facebook public reels hit a login wall (T05, T06), TikTok media answers 403
  without TikTok's cookies (T07), YouTube answers bot checks and SABR-only streams (T08, T16).
- YouTube offers progressive MP4 (usually up to 360p) plus M4A audio; merged HD needs T17.
- No playlists or batch downloads, background playback or folder export; an expired link cannot
  be refreshed in place after the process was killed.

## Device testing

The agent sandbox has no emulator (`/dev/kvm` is missing) and Robolectric does not run a real
WebView. Owner checks are listed per task and per beta in FIX_PLAN §8; device-only checks are in
[`TEST_MATRIX.md`](TEST_MATRIX.md). Every green checkpoint run uploads a debug APK
(FIX_PLAN §0.6). T02 adds an emulator job on GitHub Actions.

## History

Detailed handoffs for Phases 0–7, 5E and the UI redesign (work done, validation, decisions and
checkpoint commits) were condensed on 2026-10-03. Read them with
`git show 28930cf:docs/HANDOFF.md`.
