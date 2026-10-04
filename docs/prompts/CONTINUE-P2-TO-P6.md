# Continue Phase 11 Fix & Add: P2 (in progress) to P6 without stopping

## Owner rules

- Do not stop and do not ask questions until P2, P3, P4, P5 and P6 are each at OWNER CHECK
  (or DONE) and pushed. Decide yourself and write the decision and its evidence in the docs.
- The owner speaks Burmese. Every report to the owner is short and in Burmese, using the
  template in `docs/FIX_ADD_PLAN.md` §0.5. After each task: checkpoint push, short report,
  then continue at once with the next task in the same turn.
- Never commit secrets, keystores, `local.properties`, `.env`, cookies, tokens or signed URLs.
  Diagnostics log only safe values.
- Do not start P7 (preview APK) or P8 (signed beta.4); they wait for the owner's test.
- If one item is blocked (live site changed, emulator unavailable), time-box it to about 2
  hours, record the evidence, mark it OWNER CHECK with a note and move on.

## Start

1. Repository `https://github.com/Alalkipgen/YFT`, branch `work/phase-11-download-flow`
   (P2 work in progress up to commit `8023c3b`).
2. Read `docs/prompts/MASTER_PROMPT.md`, `docs/FIX_ADD_PLAN.md`, `docs/SESSION_STATE.md`,
   `docs/HANDOFF.md`, then the task prompt `docs/prompts/Pn-*.md` of the current task.
3. Push often (WIP commits are fine): the sandbox can reset and lose unpushed work.
4. Small machine (4 GB RAM): run one Gradle build at a time with `--no-daemon`. The LAME
   build needs NDK `27.3.13750724` and CMake `3.22.1`.
5. Validation, run separately: `./gradlew :core-browser:testDebugUnitTest`,
   `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`; Kotlin lines stay at most
   100 characters. Checkpoint with `bash scripts/checkpoint.sh "Pn: ..."` (it needs a
   `docs/SESSION_STATE.md` change).
6. CI: `curl -s "https://api.github.com/repos/Alalkipgen/YFT/actions/runs?branch=work/phase-11-download-flow&per_page=4"`.
   Emulator diagnostics are public annotations: `/actions/runs/{run}/jobs`, then
   `/check-runs/{job_id}/annotations` (logs and artifacts need sign-in).

## P2 (Facebook/TikTok black page): state and remaining steps

Done on the branch (`9b124df`, `1d105e1`, `8023c3b`):

- `SitePageDiagnosticTest` (androidTest) logs `YFT-DIAG` lines; `scripts/ci-smoke-diagnostics.py`
  turns them into one "Site page diagnostics" notice. It never fails the run.
- `AppLinkPolicy`: `http` shows the insecure error; `intent://` with an https
  `browser_fallback_url` loads that page once; other app schemes are ignored and the page stays.
- Player full screen: `SecureBrowserChromeClient` + `BrowserFullscreen`, Back exits.
- Early site lookup: `BrowserViewModel` asks the site adapter 1.5 s after `onPageStarted` for
  pages an adapter handles, once per page.
- `BrowserUserAgent`: the browser drops `; wv` and `Version/x` from the WebView user agent.
  Evidence: TikTok gave the WebView agent a video error (`MEDIA_ERR_SRC_NOT_SUPPORTED`) and the
  Chrome-like agent a playing video (`readyState 4`).
- Browser WebView gets `MATCH_PARENT` layout params: with Compose's default `WRAP_CONTENT`
  the WebView lays pages out with zero viewport height; Facebook's reel video box was 320x0.
- Local tests before the last two fixes: core-browser 66 pass, app browser/detection 107 pass.

Remaining:

1. Check both CI runs of the newest commit and read the emulator "Site page diagnostics"
   annotation. Expect `fb-share` video height above 0 and non-zero `doc=` size, and `tt-video`
   `ready=4` without a video error. If Facebook is still blank, find the cause from the
   diagnostics (`fb-share-wide`, `dark`, `center`, video ancestor sizes), fix it, run again.
2. Diagnostics: the production browser is now Chrome-like, so `tt-video-ua` is only a
   comparison; keep or drop it. Keep the diagnostic non-failing.
3. Add a unit test that the browser WebView has `MATCH_PARENT` layout params.
4. Docs: `docs/SUPPORT_MATRIX.md` (browser, Facebook, TikTok rows), `docs/TEST_MATRIX.md` P2
   section with CI run links (also fill the P1 "CI" row: checkpoint run `37212263487`, emulator
   run `37212263477`), `CHANGELOG.md` (Fixed), `docs/FIX_ADD_PLAN.md` board P2 to OWNER CHECK
   with a result note, `docs/SESSION_STATE.md`, `docs/HANDOFF.md` (next P3),
   `docs/PHASE_STATUS.md`.
5. Full validation, checkpoint `P2: ...`, CI green, Burmese report. Owner check: open a
   Facebook share link in the browser, the reel shows and plays, full screen works, the
   Download button appears; a TikTok video plays.

## P3 to P6

Follow in order: `docs/prompts/P3-one-download-sheet.md`, `P4-facebook-all-qualities.md`,
`P5-feed-focused-video.md`, `P6-2k-4k-webm.md`. For each: implement, tests (fail before and
pass after where possible), docs, validation, checkpoint push, CI green, Burmese report, next.

Code pointers from earlier analysis:

- P3: `PreviewScreen` quality label falls back to the title; `PreviewUiState` Audio tab lists
  `AUDIO` candidates only; `QuickDownloadChoices` "More formats" lists every savable candidate.
- P4: `DashDownloadManifestParser` returns Unsupported for `SegmentBase` (around line 68);
  `FacebookExtractor` returns HD/SD and DASH.
- P6: `YouTubeExtractor` `MERGED_QUALITIES` is {480, 720, 1080}, AVC only;
  `AudioVideoMuxEngine` writes MPEG-4 only, so VP9 2K/4K needs a WebM path.

## Finish

After P6, one final Burmese report: what each task changed, CI links, the newest debug APK
(`yft-debug-apk`) run link, what the owner should test, and that P7 and P8 wait for the owner.
