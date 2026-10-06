# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 12 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P26.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P26 only)

- Phase: 12 — Preview #3 field fixes (saving, every quality, one sheet). Plan:
  `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-12-integration` = `main` `4db6c2b` + the plan commit. Agent A
  `work/phase-12-download-fix` (P20, P21, later P26), Agent B `work/phase-12-site-qualities`
  (P22, P23), Agent C `work/phase-12-generic-sheet` (P24, P25); all start from
  `origin/work/phase-12-integration`. Merge order A → B → C (P26), then Preview #4; P8 (signed
  `1.0.0-beta.4`) only with the owner's OK. Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #3 (2026-10-06, run 37455870506, `4db6c2b`): video downloads fail
  "Storage unavailable" at 0 B (Retry too), YouTube only 360p, Facebook HD/SD without Audio
  (Home) or 360p only (browser), another site opens a 29 s preview and says "50 media found",
  the sheet differs per site. Root causes R1–R6 in FIX_ADD_PLAN §4.
- Rules: ADR-006 public videos only; no DRM/paywall/private/age-gate bypass; adapters never sign
  in. Never print/commit cookies, tokens, visitor data, signed media/image URLs or keys. Keep
  testTags, Kotlin lines ≤ 100, WebView on the main thread. No reset --hard/clean/stash. One
  Gradle command at a time; temporary files outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK `/data/toolchains/android-sdk`
  (platform 35, NDK 27.3.13750724, CMake 3.22.1); `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with the deploy key
  configured in `core.sshCommand`; never print it.
- Phase 11 record (per-task Results, validation, CI runs, Track A/B notes):
  `git show 4db6c2b:docs/SESSION_STATE.md`.
- Last pushed checkpoint: PLAN — prompts: own folder per agent, push access, no killing another
  agent's Gradle (this commit); plan `b097094`.
- Next: the owner pastes the prompts for Agents A, B and C (three chats, at the same time).
- Last updated: 2026-10-06 (Phase 12 plan, Plan Mode; no app code changed)

## Agent A — `work/phase-12-download-fix` (P20, P21; later P26)

- Status: IN PROGRESS — P21 done, its CI next (started 2026-10-06, base `f434724` = `origin/work/phase-12-integration`)
- Folder `/data/YFT-A`; push with the deploy key (`origin` = SSH).
- P20 — Video downloads save again: DONE (2026-10-06) — OWNER CHECK on the phone
  - Result: `DirectTransferEngine.transfer` no longer reads the destination before `prepare()` for
    a fresh download, so a new pending MediaStore row (no file until its first "rw" open) no
    longer fails at 0 B with `STORAGE_UNAVAILABLE`. A resume whose length read fails drops its
    checkpoint, records a 0 B checkpoint and starts again at byte 0.
    `AndroidPublicContentStore.length()` catches `FileNotFoundException` from the "r" open; it
    returns null when the row exists (queried with pending rows included: `QUERY_ARG_MATCH_PENDING`
    on Android 11+, `setIncludePending` on 10) and throws when the row is missing; a
    `SecurityException` still fails as storage. Other engines and destinations checked: HLS,
    DASH, merge and MP3 call `prepare()` before any destination read; the SAF temporary
    document and the app-private `.part` file exist from creation — no change needed.
  - Plan adapted: (1) "fresh" also means the queue's empty checkpoint — `DownloadQueue` always
    passes the stored checkpoint, which is empty (0 B, no segments) for a new task, so
    `resumeFrom == null` alone would have missed every real download; the engine reads the length
    only when the checkpoint has downloaded bytes. (2) A row without a file counts as empty (null)
    whatever its stored `SIZE`, because a resume must not trust bytes that are not there.
  - Tests: `DirectTransferEngineTest` "a fresh download into a destination without a file until
    prepare completes" (no checkpoint and the queue's empty one), "a resume whose length read
    fails starts again at byte 0 and completes"; `PublicDownloadDestinationTest` with the
    Android-like fake (a pending row has no file until its first "rw" open, default on): "a new
    MediaStore item has no file until prepare opens it for writing", "a direct download into a
    new MediaStore item completes and is published"; new
    `app/src/androidTest/.../download/MediaStoreDownloadInstrumentedTest` (a)–(c).
  - Validation (2026-10-06, Agent A command): BUILD SUCCESSFUL; app 663 tests (66 skipped),
    core-data 17, core-download 116, core-model 65, 0 failures; lint 0 errors (95 warnings);
    `:app:compileDebugAndroidTestKotlin` OK; line check clean.
  - Regression proof: with `DirectTransferEngine.kt` and `PublicDownloadDestination.kt` from
    `f434724` (backup `/data/bak/P20/`), 3 new tests fail with `STORAGE_UNAVAILABLE` at 0 B:
    `DirectTransferEngineTest` "a fresh download into a destination without a file until prepare
    completes" and "a resume whose length read fails starts again at byte 0 and completes",
    `PublicDownloadDestinationTest` "a direct download into a new MediaStore item completes and is
    published"; restored with `cp`, checked with `cmp`.
  - CI (`83c9c3f`, same code): emulator smoke success, "Instrumentation results: tests=23
    failures=0" (20 before + the 3 MediaStore tests)
    https://github.com/Alalkipgen/YFT/actions/runs/37488694513; Preview APK success
    https://github.com/Alalkipgen/YFT/actions/runs/37488694310; checkpoint validation
    https://github.com/Alalkipgen/YFT/actions/runs/37488694178.
  - Owner check: YouTube 360p and a Facebook HD file (direct), YouTube 720p (merged), an M4A and an
    MP3 → all finish and play in the Library.
- Starting state (2026-10-06, before any edit, Agent A validation): BUILD SUCCESSFUL; app 663
  tests (66 skipped), core-data 17, core-download 112, core-model 65, 0 failures; lint 0 errors
  (95 warnings); `:app:compileDebugAndroidTestKotlin` OK.
- P21 — Retry and failure details: DONE (2026-10-06) — OWNER CHECK on the phone
  - Result: `DownloadFailure` gains `stage` (`DownloadFailureStage`: `CONNECT`, `READ_SOURCE`,
    `OPEN_FILE`, `WRITE_FILE`, `PUBLISH`, `MERGE`, `CONVERT`, `VERIFY`) and `detail`
    (`DownloadFailureDetails`: class and message with the first cause; links, IP addresses, host
    names, Bearer and key=value secrets and long tokens removed; one line, at most 120
    characters), both default null. Direct, HLS, DASH, merge and MP3 set them where they catch:
    every destination and workspace call is wrapped (storage reason + stage), source errors are
    `NETWORK` or the HTTP reason with `CONNECT`/`READ_SOURCE`, an `IllegalStateException` is
    storage only when a destination call threw it, and a failed close after good writes is a
    `WRITE_FILE` storage failure. `runTask`'s unexpected exception stays `NETWORK` with its class
    in the detail. The record keeps them in `last_error_detail` (stage, HTTP status and detail
    in one column, cleaned again when read; Room version 5, `MIGRATION_4_5`, schema `5.json`).
    Retry (`DownloadQueue.resume`) of a failed task starts over when the reason is
    `STORAGE_UNAVAILABLE` or a direct (not MP3) download's partial file is gone, unreadable or
    shorter than its checkpoint: `DownloadDestination.renew()` makes a new destination of the
    same kind and name (MediaStore: deletes the pending row, makes a new one; SAF: a new
    temporary document; app storage: an empty `.part`; one reopened from a saved address returns
    null and is reused), the engine workspace is discarded, the checkpoint is emptied and the new
    recovery URI is stored. When no new destination can be made, the task stays failed with the
    new details (`OPEN_FILE`). Other failures resume as before. Downloads: a failed card shows
    "Details" (`download-failure-details`) and its menu "Failure details"
    (`download-menu-details-<id>`); the dialog (`download-failure-dialog`, text
    `download-failure-text`) lists reason, stage, HTTP status, detail, download type (Direct,
    MP3, HLS, DASH, Merge), where it is saved (MediaStore, SAF, app storage) and the app and
    Android versions, never the file name or an address; "Copy details"
    (`download-failure-copy`) copies that text; "Close" (`download-failure-close`).
  - Plan adapted: (1) The old engine already called a connection dropped while reading the body
    `NETWORK`; on `13b4576` that test fails on the missing stage, and "a source error counts as
    storage" is shown by an `IllegalStateException` from the source (old: `STORAGE_UNAVAILABLE`)
    in its own test. (2) `INSUFFICIENT_STORAGE` resumes instead of starting over: freeing space
    fixes a full device, and starting over would throw away the bytes already downloaded.
    (3) Found while testing: the old engine retried a failed close after good writes as a dropped
    connection and then published the file; it now fails as `WRITE_FILE`. (4) The Details button
    keeps the plan's tag `download-failure-details` (one per failed card); the menu item has its
    own `download-menu-details-<id>`.
  - Tests: `DirectTransferEngineTest` (write failure, failed close, dropped connection, source
    `IllegalStateException`); `DownloadQueueTest` (Retry after a storage failure, after a network
    failure, with the partial file gone, without a new destination); `PublicDownloadDestinationTest`
    (5 `renew` tests); `Mp3ConvertingTransferDispatcherTest` (stages and the converter's message);
    `FailureDetailCodecTest`, `RoomDownloadTaskStoreTest`; core-model `DownloadFailureDetailsTest`;
    core-data `AppDatabaseMigrationTest` 4 → 5 and the 1 → 5 chain; app `DownloadsScreenTest`
    (Details, Copy details without links, menu, only failed cards), `DownloadLabelsTest`,
    `DownloadsUiStateTest`. The instrumented MP3/AAC tests now read the failure's reason.
  - Validation (2026-10-06, Agent A command): BUILD SUCCESSFUL; app 670 tests
    (66 skipped), core-data 18, core-download 139, core-model 76, 0 failures; lint 0 errors (95
    warnings); `:app:compileDebugAndroidTestKotlin` OK; line check clean.
  - Regression proof: with `DirectTransferEngine.kt`, `DownloadQueue.kt`,
    `Mp3ConvertingTransferDispatcher.kt` and `DownloadsScreen.kt` from `13b4576` (the new model,
    store and destination files kept so the tests compile; backup `/data/bak/P21/`), 13 new or
    changed tests fail: `DirectTransferEngineTest` "a failed write is a storage failure of the
    write step with its detail" (no stage), "a failed close after good writes is a storage
    failure, not a network one" (`Completed`), "a connection dropped during the body is a network
    failure of the read step" (no stage), "an illegal state of the source is a network failure,
    not a storage one" (`STORAGE_UNAVAILABLE`); `DownloadQueueTest` "retry after a storage failure
    starts over in a new destination and completes", "retry after a network failure resumes its
    checkpoint in the same destination", "retry when the partial file is gone starts over at byte
    0", "retry that cannot make a new destination stays failed with the new details";
    `Mp3ConvertingTransferDispatcherTest` "aVideoWithoutSoundPublishesNothing",
    "aFileThePhoneCannotDecodePublishesNothingAndFreesTheSpace",
    "aConversionFailureKeepsTheConverterMessageForTheDetailsDialog"; `DownloadsScreenTest`
    "failedTaskShowsItsDetailsAndCopiesThemWithoutLinks", "theMenuOfAFailedTaskOpensItsDetails"
    (no Details). Restored with `cp`, checked with `cmp`.
  - CI: pending (this checkpoint).
  - Owner check: airplane mode on during a download → the card fails ("Failed · Network error")
    → Details shows the reason and a stage, no link → Copy details and paste: no `http` → airplane
    mode off → Retry → the download finishes and plays.
- Hand-offs (P26): Room version 5 — `MIGRATION_4_5` adds the nullable
  `download_records.last_error_detail` (schema `core-data/schemas/.../5.json`); another branch's
  Room change must come after 5. `DownloadDestination.renew()` (default null: no new destination,
  the old one is reused); `StoredDownloadTask.failure`; `DownloadFailure(stage, detail)` with
  defaults; `DirectDownloadPlan.converts` is internal (was private). New testTags:
  `download-failure-details`, `download-menu-details-<id>`, `download-failure-dialog`,
  `download-failure-text`, `download-failure-copy`, `download-failure-close`; none removed.

## Agent B — `work/phase-12-site-qualities` (P22, P23)

- Status: NOT STARTED
- P22 — YouTube: every quality: TODO
- P23 — Facebook: every quality: TODO
- Hand-offs: none

## Agent C — `work/phase-12-generic-sheet` (P24, P25)

- Status: NOT STARTED
- P24 — Other sites: main video: TODO
- P25 — One sheet for every site: TODO
- Hand-offs: none
