# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 15 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P44.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P44 only)

- Phase: 15 — Preview #6 field fixes (TikTok from TikTok's own page in the browser and on Home,
  with every failure explained in Details; YouTube % and speed within seconds; Delete file in
  Downloads; the page's own video, never the pre-roll ad, on other sites). Plan:
  `docs/FIX_ADD_PLAN.md`; prompts: `docs/prompts/README.md`.
- Branches: integration `work/phase-15-integration` = `main` `a9eea7b` + the plan commit. Agent A
  `work/phase-15-tiktok` (P39, P40), Agent B `work/phase-15-downloads` (P41, P42, later P44),
  Agent C `work/phase-15-ads` (P43); all start from `origin/work/phase-15-integration`. Merge
  order B → C → A (P44), then Preview #7; P8 (signed `1.0.0-beta.4`) only with the owner's OK.
  Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #6 (2026-10-09, run 37811403962, `4da3e61`): TikTok (VPN) "changed
  its page format" on every browser video and many Home links (Quality unknown rows, a
  "Download · 1.4 MB" row that fails without a step); "bypass TikTok like YouTube, whatever
  works"; a YouTube live recording waits long before the % moves; Delete for the file itself;
  an adult site's sheet sometimes shows the 0:30 pre-roll ad after "The first link is gone".
  Root causes R25–R34 in FIX_ADD_PLAN §4; owner decisions G1–G8 in §3 (defaults: three agents,
  Chrome agents for TikTok, the browser's TikTok cookies on Home, watermark hidden, hidden
  TikTok page on, fast start on, delete with a dialog, strict ad rule).
- Live checks in Plan Mode (2026-10-09, US sandbox): TikTok answers normally (phone page
  `webapp.reflow.video.detail` with one quality; desktop and headless pages `webapp.video-detail`
  with 4 qualities); media files need the same answer's `tt_chain_token` and a `www.tiktok.com`
  Referer, except the cookie-free `aweme/v1/play` address. The owner's VPN country gets other
  answers: his phone is the final proof, so P39 adds Details to every TikTok failure.
- CI: pushes that change only `docs/**` or `*.md` start no checkpoint validation
  (`paths-ignore`); "green CI" means the newest commit that changed code.
- Rules: ADR-006 any working technique for public videos (owner, confirmed for TikTok on
  2026-10-09); no DRM/paywall/private/age-gate bypass; adapters never sign in; agents never
  automate a page's age or identity check or a puzzle. Never print/commit cookies, tokens,
  visitor data, signed media/image URLs or keys. Keep testTags, Kotlin lines ≤ 100, WebView on
  the main thread. No reset --hard/clean/stash. One Gradle command at a time; temporary files
  outside the repo.
- Environment: each agent in its own folder (`/data/YFT-A`, `/data/YFT-B`, `/data/YFT-C`; the
  plan was written in `/data/YFT`); JDK 17 `/data/toolchains/jdk17`; SDK
  `/data/toolchains/android-sdk` (platform 35, NDK 27.3.13750724, CMake 3.22.1);
  `source /data/yft-env.sh`; full validation with
  `GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m"`. After a sandbox reset recreate the helper
  files, reinstall missing SDK parts and `git pull --ff-only` first. Push with SSH using the key
  named in `/data/.ssh/CURRENT_KEY` (pushed on 2026-10-09); when it is missing or refused make
  a **new** key (never search for old keys), show the owner the public line and wait until he
  adds it as a deploy key with write access.
- Phase 14 record (per-task Results, validation, CI runs, P38 merge notes):
  `git show a9eea7b:docs/SESSION_STATE.md`. Phase 13: `git show 5a5bddb:docs/SESSION_STATE.md`.
- Phase 15 start: P38's full validation on `4da3e61` — 1532 tests, 0 failures, 66 skipped; lint
  0 errors; `:app:assembleRelease` OK.
- Last pushed checkpoint: PLAN: Phase 15 (docs only, no CI) on `work/phase-15-integration`.
- Next: the owner pastes `A-tiktok.md`, `B-downloads.md` and `C-ads.md` into three agent chats;
  when all three are `READY FOR MERGE`, `M-merge-preview7.md` in Agent B's chat (P44).
- Last updated: 2026-10-09 (plan)

## Agent A — `work/phase-15-tiktok` (P39, P40)

- Status: P39 TODO, P40 TODO.
- Base commit: —
- Results, validation, regression proof, live-check markers, CI links, hand-offs: —

## Agent B — `work/phase-15-downloads` (P41, P42; later P44)

- Status: READY FOR MERGE (2026-10-09) — P41 and P42 done; last code commit `8ba7259`, CI green.
  OWNER ANSWERS: none (defaults `FAST_START=ON`, `DELETE_CONFIRM=ON`).
- Base commit: `d0bc7f7` (`origin/work/phase-15-integration`); folder `/data/YFT-B`.
- **P41 Result:** YouTube's whole-file tracks show bytes and speed from the first range.
  `DashTransferEngine` runs a pool of workers (the next range starts as soon as one ends),
  counts bytes as they are written (at most 4 updates a second, a retried range takes its bytes
  back, never backwards or past the total; a range is done in the checkpoint only after its
  sync), fetches a 1 MiB first range then 10 MiB ranges with 4 at once on YouTube's media hosts
  (`FAST_START`, `Policy.fastStart = true`), skips the 1-byte length probe when the length is
  known and otherwise takes it from the first range's `Content-Range`. A checkpoint saved with
  the old layout resumes with it (old fingerprint wins; the new layout has `whole-file-v2`).
  `AudioVideoMuxEngine` passes both tracks' in-range bytes on (4 a second); `DownloadQueue`
  shows a running DASH or merged task's bytes in flight while the store keeps checkpoint bytes.
  One line per download in the log (`YftDownloads`: `DASH start: plan 0.0 s · length 0.0 s
  (from first range) · first byte 0.0 s · first progress 0.0 s`, `Merged download start: …`)
  and a `Start:` line in a failure's Details. YouTube plans carry the length from `clen` or the
  player's `contentLength`.
  - Plan adapted: (1) step 6 needed no new label — since P34 the row and the notification show
    "12.3 MB · 1.2 MB/s" when the total is unknown; they stayed at 0 B because no bytes came
    before the first 10 MiB range, which P41 fixes (the merge's % appears once both sizes are
    known). (2) A resume of an unknown-length track that already has finished ranges still
    probes, so its saved layout is kept. (3) `clen`/`contentLength` are trusted only on
    `googlevideo.com` addresses. (4) The start times are stored after the detail in
    `last_error_detail` (`…|start: …`, no database change; old rows read as before).
    (5) `HlsTransferEngine` unchanged. (6) `StoredDownloadTask` may show more bytes than its
    checkpoint only for a running DASH or merged task (additive, P41).
  - Validation (2026-10-09, `--no-daemon --continue`, the five tasks): core-download 184,
    core-model 106, app 809 (66 skipped) = 1099 tests, 0 failures (+20 from 1079); `:app:lintDebug`
    0 errors (95 warnings, as before); `:app:compileDebugAndroidTestKotlin` OK; line check empty.
  - Regression proof (old `DashTransferEngine`, `AudioVideoMuxEngine`, `DownloadQueue`,
    `DownloadTaskStore`, `DownloadModels`, `DownloadPlanFactory`, `DownloadLabels` from
    `/data/bak/P41/orig`; restored with cp, cmp equal): 11 of 38 failed — DashTransferStartTest
    (first range of an unknown length is 1 MiB; a short file takes one request; a stated length
    needs no request; progress within 1 s at 64 KB/s and at 1 MB/s; never back, ends at the
    total, ≤ 4 a second; one slow range does not hold back the others), AudioVideoMuxProgressTest
    (both), StreamDownloadQueueTest "a merged task shows the bytes its tracks wrote between
    checkpoints", DashTransferEngineTest "whole file track … reports its length" (expects no
    probe now). DashFastStartLayoutTest, the new FailureDetailCodecTest/DownloadLabelsTest cases
    and DownloadPlanFactoryTest "YouTube tracks carry the length …" use the new API (do not
    compile on the old code). "a retried range is counted once" passes on both (guard).
  - Start times (throttled local server): see TEST_MATRIX "Agent B — P41, P42".
  - CI `6257307` (last P41 code commit): [checkpoint
    validation](https://github.com/Alalkipgen/YFT/actions/runs/37861112002), [emulator
    smoke](https://github.com/Alalkipgen/YFT/actions/runs/37861112033), [Preview
    APK](https://github.com/Alalkipgen/YFT/actions/runs/37861112027) all green. On `e50789c`
    the checkpoint validation stopped after 36 s before any test (the same command passed here;
    smoke and Preview APK green), so `6257307` (stopped tasks publish checkpoint bytes in one
    place) ran it again.
  - Hand-offs: none. P44 (merge) note: `DownloadFailure` gained `startTimeline` (default null)
    and `DashTransferRunner` a 6-parameter `transfer` with a default.

- **P42 Result:** finished downloads get "Delete file" (`download-menu-delete-file-<id>`) right
  after "Remove from list" (kept: it removes the row only). With `DELETE_CONFIRM` (ON) the
  question `download-delete-file-dialog` asks "Delete this file?" — "“<file name>” will be
  removed from Download/YFT and from this list. This can't be undone." — with Delete
  (`download-delete-file-confirm`) and Cancel (`download-delete-file-cancel`). The new
  `DownloadedFileDeleter` (`app/.../download/`) deletes by the record: YFT's MediaStore item
  through the content resolver (a `SecurityException` becomes `MediaStore.createDeleteRequest`
  on Android 11+, the `RecoverableSecurityException` action on Android 10; the screen launches
  it and YFT deletes again once allowed), a SAF document through `DocumentsContract`, an
  app-private file directly; a file already gone counts as deleted. Then `deleteRecord`, the
  Library refreshes (its finished-download count drops) and the snackbar says "File deleted";
  a failure says "Could not delete the file" and the row stays. `LibraryRepository.delete`
  uses the same deleter. Unfinished rows keep their menus.
  - Plan adapted: (1) "Delete file" is a menu entry of its own, not a new `DownloadAction`, so
    the existing actions and their tests stay as they are. (2) The Library's delete now says
    "deleted" for a file that is already gone (`AndroidLibraryRepositoryTest` updated; before it
    said false). (3) A record without an address is not deleted ("Could not delete the file").
    (4) `DELETE_CONFIRM` is a constant (ON); a test constructor turns it off.
  - Validation (2026-10-09, the five tasks): core-download 184, core-model 106, app 826
    (66 skipped) = 1116 tests, 0 failures (+17 from P41's 1099); lint 0 errors (95 warnings);
    androidTest compiles (new `delete/DeleteFileInstrumentedTest`); line check empty.
  - Regression proof (old `DownloadsScreen`, `DownloadsViewModel`, `DownloadLabels`,
    `LibraryRepository`, `LibraryModule`, no deleter; restored with cp, cmp equal): 2 of 5
    failed — DeleteFileMenuTest "only a finished row's menu offers Delete file beside Remove
    from list", AndroidLibraryRepositoryTest "deleting an app storage item removes only that
    file" (a gone file is deleted). DownloadsViewModelDeleteFileTest (confirm deletes the file
    then the record, cancel, `DELETE_CONFIRM=OFF`, a kept file keeps its row, Android's request
    allowed or refused, unfinished rows), DownloadedFileDeleterTest and DeleteFileScreenTest
    use the new API (do not compile on the old code). The instrumented test's JVM twin is
    DownloadsViewModelDeleteFileTest.
  - CI `8ba7259` (last code commit): [checkpoint
    validation](https://github.com/Alalkipgen/YFT/actions/runs/37862940024), [emulator
    smoke](https://github.com/Alalkipgen/YFT/actions/runs/37862940003) (runs
    `delete/DeleteFileInstrumentedTest`), [Preview
    APK](https://github.com/Alalkipgen/YFT/actions/runs/37862940023) all green.
  - Hand-offs: none.

## Agent C — `work/phase-15-ads` (P43)

- Status: P43 READY FOR MERGE (2026-10-09; `AD_RULE=STRICT`: the owner gave no answer). Last code
  commit `85f0998` (P39 resolver, owner override); docs after it only.
- Hand-off from A done: `Failure.error` + Details "Error: <Class>" (`175eca0`): the exception's
  simple class name only, set by `QuickDownloadViewModel.resolveSafely`; adding error in
  `DefaultVariantResolver`'s catch blocks: done by C (owner override), `85f0998` — A's P39
  files of `8b0bc93` plus `error = error.errorClass()` in `resolve()`'s three catch blocks
  (IOException, IllegalArgumentException, Exception); the header-probe and playlist-length
  catches still return null. Regression proof: A's `8b0bc93` resolver put back → 2 of 23
  `DefaultVariantResolverTest` failed (error null); restored with `cp`, `cmp` equal.
- P44 merge note: when `work/phase-15-tiktok` is merged (last), `DefaultVariantResolver.kt` and
  `DefaultVariantResolverTest.kt` may conflict: keep the integration (C) version. Check with
  `git diff origin/work/phase-15-tiktok -- <both files>`: the only difference must be C's error
  lines. If A changes either file after `8b0bc93`, take A's newest version and add the error
  lines again.
- Base commit: `d0bc7f7` (`origin/work/phase-15-integration`, the plan commit).
- Starting state (before edits, 2026-10-09): `./gradlew --no-daemon --continue :core-model:test
  :extractor-generic:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (9 min 34 s): 1071 tests, 0 failures,
  66 skipped (core-model 106, extractor-generic 20, core-browser 138, app 807); lint 0 issues.
- Result: on a site without an adapter one rule (`PageVideoProof`, core-model) decides at every
  step of the sheet (first choice, page's newest link, player's link, page read again, next
  video) whether a file is the page's video: named by the player setup, or its length (measured
  first when unknown) matches the page's or the failed video's. Ads: `AdHosts` (own list, network
  names under any suffix, IMA/VAST/pre-roll requests, VAST/VMAP answers), `AdSign` on candidates
  (mapper, `VastAdTracker` incl. `onAnswer`), short files on long pages. Skipped files: "That was
  an ad — showing the page's video"; nothing left: "Only an ad was found, not the page's video."
  with Reload (stand-in of the page's length). Proven ads not counted in the sheet's other videos.
- Plan adapted: P28's test "the sheet waiting for the page's video shows … then its line" offered
  a 0:30 ad on a 16:24 page; it now sets `adRule = LENIENT` (LENIENT keeps the browser's first
  choice) and a STRICT twin expects no ad offered. The size check of a stated file now keeps the
  length it read (the header shows the proven length).
- Validation (2026-10-09, after the P39 resolver): `./gradlew --no-daemon --continue
  :core-model:test :core-media:testDebugUnitTest :extractor-generic:test
  :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL: 1145 tests, 0 failures, 66 skipped
  (core-model 122, core-media 33 — new in the list for the resolver, extractor-generic 21,
  core-browser 148, app 821; 1112 without core-media, +41 from 1071); lint 0 errors (95
  warnings, unchanged); androidTest compiles; long-line check empty.
- Regression proof: the 7 files before P43 + `QuickDownloadPrerollTest` → 4 of 5 failed, each
  offering the 0:30 file; restored with `cp`, `cmp` equal (TEST_MATRIX "Agent C — P43").
- CI (`4be6563`, the last code commit): checkpoint validation success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957302), emulator smoke success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957303; 38 instrumented tests, 0 failures,
  0 FATAL — 37 before, `AdPrerollInstrumentedTest` added), Preview APK success
  (https://github.com/Alalkipgen/YFT/actions/runs/37860957301). `175eca0` (P39 hand-off):
  validation success (https://github.com/Alalkipgen/YFT/actions/runs/37869539298), emulator
  success (https://github.com/Alalkipgen/YFT/actions/runs/37869539313; tests=38 failures=0,
  0 FATAL), Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37869539343).
  `85f0998` (P39 resolver, last code commit): validation success
  (https://github.com/Alalkipgen/YFT/actions/runs/37877862671), emulator success
  (https://github.com/Alalkipgen/YFT/actions/runs/37877862760; tests=38 failures=0, 0 FATAL),
  Preview APK success (https://github.com/Alalkipgen/YFT/actions/runs/37877862659).
- Owner check: the adult site of item 7 — Download on 10 videos, several with a pre-roll: the
  page's own length every time, never 0:30 ("That was an ad — showing the page's video" may
  show); Javtiful and the HTTP-410 site still download; a Details screenshot for any miss.
- Hand-offs to Agent A: (a) `BrowserScreen.kt:509` and `DetectedMediaScreen.kt:169` call
  `MediaGroups.ofPage(…, facts, hideAds = true)` so proven ads are not listed under Other videos,
  and `BrowserViewModel`'s `otherVideos` leaves out `PageVideoProof.isProvenAd` groups; (b)
  `BrowserViewModel` calls `vastAds.onAnswer(observation, contentType, bodyStart)` where the
  page's answers are read (e.g. `MediaMetadataProbe`'s non-media XML answers), so a VAST/VMAP
  body starts an ad break when the request's address says nothing.
