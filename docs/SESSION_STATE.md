# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 13 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P33.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P33 only)

- Phase: 13 — Preview #4 polish (other sites' pre-roll ads, YouTube merge at 99%, browser
  search, history and pop-ups). Plan: `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-13-integration` = `main` `bc806f9` + the plan commit. Agent A
  `work/phase-13-merge-speed` (P27, later P33), Agent B `work/phase-13-generic-main` (P28, P29),
  Agent C `work/phase-13-browser` (P30, P31, P32); all start from
  `origin/work/phase-13-integration`. Merge order A → B → C (P33), then Preview #5; P8 (signed
  `1.0.0-beta.4`) only with the owner's OK. Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #4 (2026-10-07, run 37530061595, `bc806f9`): "about 90% fine". Open:
  on a free video site without an adapter the sheet opens the pre-roll ad (0:30, 1080p MP4)
  instead of the page's video (16:24, 720p HLS, only under Other videos) and one page shows
  HTTP 410; long YouTube live recordings wait a long time at 99% (the merge); the browser
  searches DuckDuckGo, has no history, and ads redirect the tab to other sites. Root causes
  R7–R15 in FIX_ADD_PLAN §4; owner decisions F1–F6 in §3 (defaults: A+ generic, Google,
  history on, blocking on, direct mux, three agents).
- Rules: ADR-006 public videos only; no DRM/paywall/private/age-gate bypass; adapters never sign
  in; agents never automate a page's age or identity check. Never print/commit cookies, tokens,
  visitor data, signed media/image URLs or keys. Keep testTags, Kotlin lines ≤ 100, WebView on
  the main thread. No reset --hard/clean/stash. One Gradle command at a time; temporary files
  outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK
  `/data/toolchains/android-sdk` (platform 35, NDK 27.3.13750724, CMake 3.22.1);
  `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with SSH: when
  `/data/.ssh/id_ed25519` is missing make a **new** key (never search for old keys), show the
  owner the public line and wait until he adds it as a deploy key with write access.
- Phase 12 record (per-task Results, validation, CI runs, P26 merge notes):
  `git show bc806f9:docs/SESSION_STATE.md`. Phase 11: `git show 4db6c2b:docs/SESSION_STATE.md`.
- Last pushed checkpoint: PLAN: Phase 13 (this commit, docs only, on
  `work/phase-13-integration`).
- Next: the owner pastes `docs/prompts/A-merge-speed.md`, `B-generic-main.md` and
  `C-browser.md` into three new agent chats; when all three are READY FOR MERGE, Agent A runs
  `M-merge-preview5.md` (P33, Preview #5); P8 only with the owner's OK after Preview #5.
- Last updated: 2026-10-07 (Phase 13 plan)

## Agent A — `work/phase-13-merge-speed` (P27; later P33)

- Status: OWNER CHECK — P27 (started 2026-10-07, base `7873d51` =
  `origin/work/phase-13-integration`)
- Folder `/data/YFT-A`; push with the deploy key (`origin` = SSH, `/data/.ssh/id_ed25519`).
- Starting state (before any edit, the Agent A command): BUILD SUCCESSFUL; core-download 141
  tests, core-model 80, app 690 (66 skipped), 0 failures; lint 0 errors (95 warnings);
  androidTest Kotlin compiles.
- P27 — YouTube: no long wait at 99%: DONE (2026-10-07) — OWNER CHECK on the phone
  - Result: `AudioVideoMuxEngine` reports the merge (`AudioVideoMuxStage.MUXING`, samples
    written / track time, `AudioVideoMuxCheckpoint.stepDone`/`stepTotal`/`stepPercent`, added
    with defaults, not stored) and the copy (new stage `SAVING`, bytes). `DownloadStage` in
    `DownloadsUiState` turns it into "Merging audio and video · 45%" and "Saving to Download/YFT ·
    80%" (app storage, the chosen folder) on the card (testTag `download-stage-<id>`, the bar
    follows the step; indeterminate before the first sample) and in the notification.
    `DownloadDestination.openFileDescriptorOutput()` (added, default null): app storage gives
    its `.part` file, MediaStore and SAF a "rw" descriptor that must be seekable. On API 26+
    with one, `AndroidMp4AudioVideoMuxer` (now `ProgressAudioVideoMuxer`, `MergeProgress.kt`)
    merges into it with `MediaMuxer(FileDescriptor, …)`; the engine checks the length, syncs and
    commits — no second copy. Without one, or on Android 7.x, today's path with a 1 MiB copy
    buffer; an in-place merge that fails before its first sample falls back once. Space check
    before the destination is touched (in place: tracks + 8 MiB; today's path: twice the tracks +
    8 MiB) → `INSUFFICIENT_STORAGE` at `MERGE`, "Merging needs X and Y is free" (free space from
    `StorageSpace` for Download/YFT). One log line per merge, "Merge done (in place|copy):
    video …, audio …, merge …, copy …, sync …, commit …; <size>" (no addresses); a failure's
    detail gets "took …".
  - Plan adapted: (1) today's path deletes the track files after a good merge, before the copy
    (frees their space at once), so a failure after that starts the download over (empty
    checkpoint) instead of merging again. (2) The emulator's 20-minute input is made by
    repeating the 1-second test tracks' `moof`/`mdat` pairs under one new `sidx`
    (`LongFragmentedMp4`, androidTest) — no encoder on the CI emulator, no large asset in Git.
    (3) The space check runs before the destination is touched, so a failed check leaves no
    empty file in Download/YFT.
  - Tests: `AudioVideoMuxMergeTest` (8), `PublicDownloadDestinationTest` +1, `DownloadModelsTest`
    (step percent), `MergeStageLabelsTest` (4), `DownloadsScreenTest` +1,
    `DownloadNotificationFactoryTest` +1; instrumented `MergeSpeedInstrumentedTest` (2): a direct
    merge into a new MediaStore item → playable (AVC + AAC, 160×90, 15 frames, a decoded frame);
    the 20-minute timing test (read pass, in place, today's path).
  - Validation (2026-10-07, Agent A command): BUILD SUCCESSFUL; core-download 150 tests,
    core-model 80, app 696 (66 skipped), 0 failures; lint 0 errors (95 warnings);
    `:app:compileDebugAndroidTestKotlin` OK; line check clean.
  - Regression proof: with `AudioVideoMuxEngine.kt`, `DownloadsUiState.kt`, `DownloadLabels.kt`,
    `DownloadsScreen.kt`, `DownloadNotificationFactory.kt` and `DownloadRuntimeModule.kt` from
    `7873d51` (backup `/data/bak/P27/`; the test's engine helper without the new parameters),
    13 new tests fail: all 8 `AudioVideoMuxMergeTest` (no merge percent; "file without
    progress" instead of the descriptor; `Completed` instead of the space failure and of the
    failure after a sample; no "Merge done" line; the stop not reached), `MergeStageLabelsTest`
    "a merging download shows the merge and its percent, not 99 percent", "saving names where the
    merged file goes, with its percent", "before its first sample the merge has no percent and
    the bar moves on its own" (all "954 of 954 MB"), `DownloadsScreenTest`
    "mergedTaskShowsItsMergeThenItsCopyWithTheirPercent" (no `download-stage-m`),
    `DownloadNotificationFactoryTest` "a merged download shows its merge and then its copy with
    their percent"; restored with `cp`, checked with `cmp`.
  - Measured split (CI emulator): from this checkpoint's emulator run (next checkpoint).
  - CI (`d978201`): checkpoint validation success
    https://github.com/Alalkipgen/YFT/actions/runs/37562550278; Preview APK success
    https://github.com/Alalkipgen/YFT/actions/runs/37562550232; emulator smoke failure
    https://github.com/Alalkipgen/YFT/actions/runs/37562550248 ("tests=25 failures=2": both
    `MergeSpeedInstrumentedTest` tests stopped at `IllegalArgumentException` — the test's fake
    track checkpoint had a fingerprint that is not hex; a test bug, fixed in the next commit).
    Its read pass: 20 min, 70 785 samples, 17 MB, read once in 1.8 s (sdk 34).
- Hand-offs: none

## Agent B — `work/phase-13-generic-main` (P28, P29)

- Status: NOT STARTED
- P28 — Other sites: the page's video, not the ad before it: TODO
- P29 — Other sites: the next video when one fails: TODO
- Hand-offs: none

## Agent C — `work/phase-13-browser` (P30, P31, P32)

- Status: NOT STARTED
- P30 — Browser: Google search: TODO
- P31 — Browser history: TODO
- P32 — Block pop-ups and ad redirects: TODO
- Hand-offs: none
