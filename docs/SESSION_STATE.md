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

- Status: READY FOR MERGE (P20, P21) — last code commit `8cb0b74`, CI green (started 2026-10-06,
  base `f434724` = `origin/work/phase-12-integration`)
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
  - CI (`8cb0b74`): emulator smoke success, "Instrumentation results: tests=23 failures=0"
    https://github.com/Alalkipgen/YFT/actions/runs/37510578505; Preview APK success
    https://github.com/Alalkipgen/YFT/actions/runs/37510578383; checkpoint validation success
    https://github.com/Alalkipgen/YFT/actions/runs/37510578152.
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

- Status: READY FOR MERGE (2026-10-06) — P22 and P23 OWNER CHECK; last task commit `c53128c`,
  CI green (links under P23); base `f434724` = `origin/work/phase-12-integration`; worked in
  `/data/YFT-B`
- P22 — YouTube: every quality: OWNER CHECK (2026-10-06)
  - Result: visionOS is asked once more with the watch page's visitor data (client context and
    `X-Goog-Visitor-Id`) when its first answer refused the request; a lookup ends only with a
    separate video merged with its audio track plus that track (`Offers.isComplete`), so
    `ANDROID`'s 360p file no longer ends it before the page client. Order: visionOS → watch
    page → visionOS again → embedded → page client → `MWEB` (desktop page) → `ANDROID`. Merged
    AVC rows 144p–1080p, one row per quality, Audio = `itag 140` with its size. Details: visionOS
    again, "N formats via player script", "N adaptive formats only through SABR".
  - Plan adapted: (1) the embedded player stays before the page client: yt-dlp gives it no
    proof-of-origin policy, so it costs no BotGuard mint and sends no cookie; (2) `ANDROID` is
    last, after `MWEB` too; (3) a visionOS request that failed (network, HTTP error) is not asked
    again (P10 retried it), only a refusal is; (4) visionOS again uses the endpoint without the
    page key, like yt-dlp; (5) a merged row replaces a progressive file from any client (not
    only `ANDROID`'s), since YouTube sizes separate streams but often not progressive ones;
    (6) `isComplete` is now a merged video plus the audio track (was: a video with sound plus
    audio); (7) validation runs the same tasks as four sequential Gradle invocations (memory).
  - Validation: `:extractor-api:test` 32, `:extractor-sites:test` 185, `:app:testDebugUnitTest`
    663 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings; line check empty.
  - Regression proof: old extractor, parser and client profile from `/data/bak/P22/orig` with
    the new tests → 22 of 52 `YouTubeExtractorTest` failed, among them "visionOS asked again with
    the page's visitor data gives every quality with its size" and "refused twice, visionOS
    leaves the ladder to the page client's script-signed formats" (both `[360p]` only); new
    files restored with `cp`, `cmp` equal. Details: `docs/TEST_MATRIX.md` › Agent B.
  - Live (sandbox, 2026-10-06): `dQw4w9WgXcQ` → 9 rows 2160p–144p + Audio with sizes from one
    visionOS request; `4pKpLX9NG_k` → bot check from every client in the new order;
    `8Mw9bwLTQFk`, `jNQXAC9IVRw` → bot check from visionOS with and without visitor data. The
    data-centre IP is flagged: the owner's phone Details decide.
  - Owner check: `4pKpLX9NG_k` in Home and the browser → 144p…1080p (+2K/4K) with sizes close
    to Snaptube's, M4A ≈ 12 MB, 720p/1080p play with sound, a Details screenshot.
  - Commit `0e0ed15`, CI green: checkpoint validation
    https://github.com/Alalkipgen/YFT/actions/runs/37499414146 , emulator smoke
    https://github.com/Alalkipgen/YFT/actions/runs/37499414119 , Preview APK
    https://github.com/Alalkipgen/YFT/actions/runs/37499414196
- P23 — Facebook: every quality: OWNER CHECK (2026-10-06)
  - Result: the AVC ladder (desktop Safari, no cookie) is asked when a page lists no AVC video,
    or AVC only below another track or a whole file (`needsAvcLadder`), on the final reel or
    `/{page}/videos/{id}/` address, else the post's permalink; tracks of every page read merge
    without repeats (`FacebookDashOffers.merged`). A share link to a page with HD and SD files
    only now lists AVC 360p/720p and Audio. The public page is the whole lookup only for a reel
    with AVC video and an AAC track. HD and SD files state the picture and codecs of their
    track; bitrates come from the media address. At most 2 page requests per lookup.
  - Plan adapted: (a) `/watch/`, `video.php` and `/{page}/videos/` links skip the public page
    (Safari got about 1 KB without the video), so `/watch/?v=` costs 1 request instead of 2;
    (b) the ladder is never asked on a `/watch/` link (the same 1 KB page): the final address
    when it is a reel or videos page, else the permalink the page states, else skipped; (c) an
    HD or SD file states its size and codecs only with its manifest track and an AAC track's
    codec (else the resolver reads the file's header, P3), page metadata sizes stay label-only;
    (d) bitrates are the average stated in the address (`bitrate`, or `bitrate` in the Base64
    `efg` label): the sheet estimates bitrate × duration, and the manifest's peak bandwidth
    made those estimates 3.5–4.4 × too high; a merged row without one has none; (e) a failed
    session page keeps the public page's files; (f) an HD file whose picture is unknown counts
    as 720p for the ladder rule, as the sheet ranks it.
  - Validation: `:extractor-api:test` 32, `:extractor-sites:test` 196, `:app:testDebugUnitTest`
    663 (66 skipped), 0 failures; `:app:lintDebug` 0 errors, 95 warnings; line check empty.
  - Regression proof: the five old Facebook files from `/data/bak/P23/orig` with the new tests
    → 18 of 52 failed, among them "a share link to a page with HD and SD only gets the reel's
    AVC sizes and audio" and "AVC at 360p below AV1 sizes asks the ladder, which adds AVC 720p
    once"; new files restored with `cp`, `cmp` equal. Details: `docs/TEST_MATRIX.md` › Agent B.
  - Live (sandbox, no session, 2026-10-06): reel `1545617074260365` → 1 request, 720p · HD
    (1280×720, est. 88.5 MB, CDN 88.9 MB), SD 31.8 MB, 720p 88.5 MB, 360p 34.4 MB, Audio
    14.4 MB; `share/r/` → 1 request, ladder not needed; `/watch/?v=452499129200583` → 1
    request; phone pages with AV1 or VP9 only → the ladder added AVC 360p/720p (2 requests).
  - Owner check: reel `1545617074260365` (and the owner's share link) in Home and the browser
    → the same rows in both: 720p and 360p with sizes (720p ≈ 88.5 MB, 360p ≈ 34.4 MB),
    M4A ≈ 14 MB, MP3; 720p plays with sound; a Details screenshot.
  - Commit `c53128c`, CI green: checkpoint validation
    https://github.com/Alalkipgen/YFT/actions/runs/37516957311 , emulator smoke
    https://github.com/Alalkipgen/YFT/actions/runs/37516957297 , Preview APK
    https://github.com/Alalkipgen/YFT/actions/runs/37516957409
- Starting state before any edit (2026-10-06, `f434724`): `:extractor-api:test` 32 tests,
  `:extractor-sites:test` 181, `:app:testDebugUnitTest` 663 (66 skipped), 0 failures;
  `:app:lintDebug` 0 errors, 95 warnings. Run as four sequential `./gradlew --no-daemon
  --continue --max-workers=1` invocations with the same tasks (Gradle heap 1280/1536/768/1536 MiB,
  metaspace 768 MiB, Kotlin in-process): one invocation with every task was OOM-killed on the
  4 GiB sandbox (Gradle daemon and the Robolectric test JVM together).
- Hand-offs: none

## Agent C — `work/phase-12-generic-sheet` (P24, P25)

- Status: NOT STARTED
- P24 — Other sites: main video: TODO
- P25 — One sheet for every site: TODO
- Hand-offs: none
