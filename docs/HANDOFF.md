# Handoff

## Current handoff (2026-10-03)

- **Phase:** 8 — field fixes, in progress. T01 is implemented and locally validated; real-WebView
  and owner checks remain. Plan, status board and decisions:
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
- **Next action — agent:** T02 is DONE; T03's starting core-browser/app tests and lint passed.
  Implement the browser start page ([`prompts/T03-browser-start-page.md`](prompts/T03-browser-start-page.md)),
  or [`prompts/00_NEXT_TASK.md`](prompts/00_NEXT_TASK.md) for whatever is next.
- **Next action — owner:** answer D1 (clipboard check on open), D2 (YouTube strategy A or B) and D3
  (MP3) in FIX_PLAN §3. beta.2 can stay a draft; beta.3 replaces it.
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
