# Session State

Update this file before every checkpoint push. Keep it factual so another chat can resume.

Phase 14 runs three agents at the same time. **Each agent edits only its own section below**
(`## Agent A …`, `## Agent B …`, `## Agent C …`); `## Overview` belongs to the plan and to P38.
Keep at least the heading and one blank line between sections, so Git merges them cleanly.

## Overview (plan and integration — P38 only)

- Phase: 14 — Preview #5 field fixes (downloads and merges that keep going in the background
  with %, speed and time left in the notification; a faster merge; TikTok on the For You feed;
  fresh links instead of HTTP 410 on other sites). Plan: `docs/FIX_ADD_PLAN.md`; prompts:
  `docs/prompts/README.md`.
- Branches: integration `work/phase-14-integration` = `main` `5a5bddb` + the plan commit. Agent A
  `work/phase-14-background` (P34, later P38), Agent B `work/phase-14-sites` (P36, P37), Agent C
  `work/phase-14-fast-merge` (P35); all start from `origin/work/phase-14-integration`. Merge
  order A → B → C (P38), then Preview #6; P8 (signed `1.0.0-beta.4`) only with the owner's OK.
  Nobody pushes to `main` without the owner's OK.
- Owner's test of Preview #5 (2026-10-08, run 37575586233, `436aa90`): some pages of a site
  without an adapter answer HTTP 410 (manifest and MP4) until a manual reload, and Try again
  repeats it; a 1-hour YouTube merge takes about 2 minutes and stops while YFT is in the
  background; TikTok's For You feed says "No video on screen to download"; downloads should go on
  in the background with % and speed in the notification. Root causes R16–R24 in FIX_ADD_PLAN
  §4; owner decisions G1–G8 in §3 (defaults: three agents, stream-copy merge, wake lock, battery
  card, finished notice, TikTok qualities from the desktop page, two quiet re-reads).
- CI: since the plan commit, pushes that change only `docs/**` or `*.md` start no checkpoint
  validation (`paths-ignore`); "green CI" means the newest commit that changed code.
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
- Phase 13 record (per-task Results, validation, CI runs, P33 merge notes):
  `git show 5a5bddb:docs/SESSION_STATE.md`. Phase 12: `git show bc806f9:docs/SESSION_STATE.md`.
- Last pushed checkpoint: PLAN: Phase 14 (this commit, docs and the CI trigger, on
  `work/phase-14-integration`).
- Next: the owner pastes `docs/prompts/A-background.md`, `B-tiktok-fresh-links.md` and
  `C-fast-merge.md` into three new agent chats; when all three are READY FOR MERGE, Agent A runs
  `M-merge-preview6.md` (P38, Preview #6); P8 only with the owner's OK after Preview #6.
- Last updated: 2026-10-08 (Phase 14 plan)

## Agent A — `work/phase-14-background` (P34; later P38)

- Status: NOT STARTED
- P34 — Downloads and merges keep going in the background; speed in the notification: TODO
- Hand-offs: none

## Agent B — `work/phase-14-sites` (P36, P37)

- Status: NOT STARTED
- P36 — TikTok: Download on the For You feed and video pages: TODO
- P37 — Other sites: fresh links instead of HTTP 410: TODO
- Hand-offs: none

## Agent C — `work/phase-14-fast-merge` (P35)

- Status: READY FOR MERGE — last code commit `5f62962`, CI green (started 2026-10-08, base
  `c8fcd33`):
  - checkpoint validation https://github.com/Alalkipgen/YFT/actions/runs/37801106992
  - emulator smoke https://github.com/Alalkipgen/YFT/actions/runs/37801106973
  - Preview APK https://github.com/Alalkipgen/YFT/actions/runs/37801107005
- Starting state (2026-10-08, `c8fcd33`, Notion sandbox with Gradle `-Xmx1280m
  -XX:MaxMetaspaceSize=640m` and a 4 GiB swap file): `./gradlew --no-daemon --continue
  :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug
  :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; core-download 150, core-model 98,
  app 746 tests (66 skipped), 0 failures; lint 0 errors.
- P35 — Faster merge for long videos: DONE (2026-10-08) — OWNER CHECK on the phone
  - Result: `FAST_MERGE=ON` (`AudioVideoMuxCompatibility.FAST_MERGE_ENABLED`).
    `AndroidMp4AudioVideoMuxer` (Agent A's `DownloadRuntimeModule` keeps
    `AndroidMp4AudioVideoMuxer()`, so the app uses it without an app change) merges an MP4 video
    (`avc1`/`avc3`) and an MP4/M4A sound (`mp4a`) by `StreamCopyAudioVideoMuxer`.
    `Mp4TrackReader` + `TrackInit` read each input in plain Kotlin over `FileChannel`
    (fragmented `moof`/`traf`/`tfhd`/`tfdt`/`trun` with `trex` defaults, or plain
    `stsz`/`stz2`/`stts`/`ctts`/`stss`/`stsc`/`stco`/`co64`; unknown boxes skipped; encryption,
    two tracks in a file, another codec, a broken size, sample data outside an `mdat`, an edit
    list other than none/one/empty+one → "not supported"). `StreamCopyLayout` puts the samples
    in chunks of about one second per track, alternating by time; the writer copies them
    through one 4 MiB direct buffer into the destination (the P27 descriptor with `Os.pwrite`,
    or the temporary file): `ftyp`, one `mdat` (64-bit size above 4 GiB), then `moov` (`mvhd`;
    per track `tkhd`, `edts/elst`, `mdhd` with the input's timescale, `hdlr`, `vmhd`/`smhd`,
    `dinf`, `stbl` = the input's `stsd` + new `stts`, `ctts` version 0, `stss` (video),
    `stsc`, `stsz`, `stco` or `co64`); all sizes are known before writing, no seek back.
    `MediaExtractorStreamCopyCheck` reads the result back (two tracks, the inputs' MIME, size,
    sample rate, channels, durations within a frame, the first 8 samples, sync samples at 1/3
    and 2/3, every sample from the last sync sample on: bytes (AVC with start codes) and
    times); a failed check empties the output and today's way runs once; the note ("not
    supported, …", "check failed, …", "failed, <exception>") goes into the log and a later
    failure's detail. Progress = bytes copied ÷ total, per chunk. Today's way
    (`MediaMuxerAudioVideoMuxer`, also for WebM and as the fallback) reads each sample's time
    and flags once (5 calls per sample), one reusable buffer, progress at most every 250 ms.
    Log line: "Merge done (…): …; <size>; stream copy: N video + M audio samples, parse …,
    data …, index …, check …; cpu …, wall …" (or "MediaMuxer (stream copy: <note>): …,
    open …, read …, write …, finish …").
  - Plan adapted: (1) negative composition offsets are shifted into `ctts` version 0 and the
    edit list's `media_time` instead of `ctts` version 1 (Android 7's MediaExtractor rejects
    version 1). (2) The instrumented test checks the stream copy's sample times against the
    inputs (and against today's file after each track's first sample), because MediaMuxer moves
    the start of a video with B-frames: it takes each sample's presentation time as its decode
    time, and the test video starts 59 ms late in today's file (192,777 µs instead of
    133,333 µs); the stream copy keeps the inputs' times. (3) Sound sync flags are compared with
    today's file only: MediaExtractor marks only the first sample of an input fragment as sync.
    (4) Today's way is measured with `AndroidMp4AudioVideoMuxer(fastMerge = false)` on the same
    inputs (and P27's line on the old code); the 1-hour-sized input is P27's tracks repeated to
    265,410 samples.
  - Tests: `StreamCopyAudioVideoMuxerTest` (15), `StreamCopyMergeEngineTest` (1), with the test
    MP4 builder `Mp4TestFiles` and the independent reader `Mp4Dump`; instrumented
    `StreamCopyMergeInstrumentedTest` (3: test tracks, 20 minutes, 1-hour-sized).
  - Validation (2026-10-08, the Agent C command): BUILD SUCCESSFUL; core-download 166 tests,
    core-model 98, app 746 (66 skipped), 0 failures; lint 0 errors; line check clean (printed
    nothing).
  - Regression proof: with `StreamCopyAudioVideoMuxer.mux` calling today's way at once (no fast
    path; backup `/data/bak/P35/`), 13 of the 16 new JVM tests fail: `StreamCopyAudioVideoMuxerTest`
    "an MP4 video and an M4A sound are stream-copied, not merged by today's way", "a plain M4A
    with uneven samples gives tables that match every sample", "chunks of about a second
    alternate between video and sound in time order", "encrypted or odd tracks are not supported
    and today's way merges once", "sample data outside an mdat is not supported", "a failed check
    empties the output and today's way merges once", "a failure of today's way after the stream
    copy keeps both reasons", "progress moves on by the bytes copied and ends at 100 percent", "a
    stopped download stops the stream copy and leaves no output", "the destination's descriptor
    gets the same file and stays open", "edit lists keep both tracks where the inputs presented
    them", "negative composition offsets are shifted into ctts version 0 and the edit list", and
    `StreamCopyMergeEngineTest` "the merge line names the stream copy, its samples, phases and CPU
    time"; restored with `cp`, checked with `cmp`.
  - Measured times (CI emulator, API 34, `5f62962`, `YFT-DIAG fast-merge`): 1-hour-sized input
    (265,410 samples, 67 MB): today's way 14.7 s (open 174 ms, read 4.9 s, write 9.6 s, finish
    45 ms; CPU 6.8 s) → stream copy 1.03 s (parse 430 ms, data 268 ms, index 92 ms, check
    241 ms; CPU 811 ms), 14.3× faster. 20 minutes (70,785 samples, 17 MB): 3.74 s → 254 ms
    (14.7×). Earlier runs: `ff59e2c` 16.0 s → 1.33 s and 4.05 s → 0.42 s; `0a18c19` 21.3 s →
    1.84 s and 6.5 s → 0.49 s. P27's line through the engine: in place merge 850 ms (5.7 s
    before P35), app storage merge 704 ms (5.9 s before). ExoPlayer: 1-hour-sized file
    4,500,121 ms (today's file 4,500,143 ms), seek to the middle at 2,250,060 ms. The emulator
    runs the debug build, where Kotlin loops are slow; the tracks' 67 MB sit in its page cache.
    On a phone a 1-hour 720p merge copies about 1.3 GB, so the wait is the storage's write speed
    plus the check: about 10–20 s expected (owner check).
  - CI (`5f62962`, all green; "Instrumentation results: tests=32 failures=0 errors=0
    skipped=0"):
    - checkpoint validation https://github.com/Alalkipgen/YFT/actions/runs/37801106992
    - emulator smoke https://github.com/Alalkipgen/YFT/actions/runs/37801106973
    - Preview APK https://github.com/Alalkipgen/YFT/actions/runs/37801107005
  - Owner check: the same 1-hour YouTube live recording at 720p → "Merging … %" ends in about
    10–20 s after the tracks (was about 2 minutes); it plays and seeks in the Library and in
    another player; a 1080p video and a 2K/4K WebM still save.
- Hand-offs: none (no app, Gradle or workflow change; Agent A's `DownloadRuntimeModule` keeps
  `AndroidMp4AudioVideoMuxer()`, which now stream-copies by default).
