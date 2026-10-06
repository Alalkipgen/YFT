# Agent A — Video downloads save again (P20 → P21), later the merge (P26)

**ရည်ရွယ်ချက်:** Video download တိုင်း "Failed · Storage unavailable" နဲ့ 0 B မှာ ချက်ချင်း ပျက်နေတာကို
ပြင်မယ် (P20)။ Retry က တကယ် ပြန်စနိုင်အောင်၊ ပျက်ရင် ဘာကြောင့်/ဘယ်အဆင့်မှာလဲ Details နဲ့ ပြပြီး
Copy လုပ်လို့ရအောင် လုပ်မယ် (P21)။

**အချက်အလက်:** Phase 12 · **Agent A** · branch `work/phase-12-download-fix` · P20 (Medium, 3–5 နာရီ)
→ P21 (Medium, 4–6 နာရီ) · Agent B နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ B
([`B-site-qualities.md`](B-site-qualities.md)) နဲ့ C ([`C-generic-and-sheet.md`](C-generic-and-sheet.md))
ကိုလည်း သီးခြား chat တွေမှာ တပြိုင်နက် စလို့ရပါတယ်။ A ပြီးရင် `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။
သုံးယောက်လုံး ပြီးမှ [`M-merge-preview4.md`](M-merge-preview4.md) ကို A ရဲ့ chat ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P20](../FIX_ADD_PLAN.md#p20--video-downloads-save-again),
[P21](../FIX_ADD_PLAN.md#p21--retry-and-failure-details)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: A — download engines, storage, Downloads screen (one of three agents working at once)
Tasks: P20 — Video downloads save again; then P21 — Retry and failure details
Branch: work/phase-12-download-fix (first start: create it from origin/work/phase-12-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: STOP_AFTER=P20)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-download/**; core-data/** (one Room migration at most);
  core-model/src/main/kotlin/com/alal/yft/core/model/download/**;
  app/src/main/java/com/alal/yft/download/**; app/src/main/java/com/alal/yft/feature/downloads/**;
  app/src/androidTest/java/com/alal/yft/download/**; the tests beside them;
  app/build.gradle.kts only for one androidTestImplementation line if a test needs it.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent A ...", CHANGELOG.md
  "### Phase 12 — Agent A (P20, P21)", docs/TEST_MATRIX.md "### Agent A — P20, P21".
- Never: docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**, other agents' files.
  Agent B (YouTube/Facebook extractors) and Agent C (other sites, the sheet, Home, browser) push
  their own branches at the same time. Need a change outside your files? Do not make it: write
  "Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and report it.
- Contracts other agents use — DownloadEnqueuer, DownloadPlanFactory, the public DownloadQueue
  API, DownloadTask, DownloadFailure, DownloadFailureReason: change only by adding, with
  defaults; never rename or remove.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-12-download-fix origin/work/phase-12-integration
   Later:       git switch work/phase-12-download-fix && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2, 3, 4 (R1, R2) and tasks P20 and P21 with
   their "Read first" files, then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon"; one Gradle command at a time.
   Starting state before any edit (Agent A scope):
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-data:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P20 IN PROGRESS, the start date and your base commit.

WORK P20 — Video downloads save again (FIX_ADD_PLAN P20, finding R1)
Root cause: DirectTransferEngine.transfer reads destination.temporaryLength() before
destination.prepare(). For a fresh MediaStore pending row (Download/YFT, Android 10+)
AndroidPublicContentStore.length() opens it with "r", which throws FileNotFoundException because
the file exists only after an "rw" open, and the engine turns that into STORAGE_UNAVAILABLE at
0 B. HLS, DASH, merge and MP3 call prepare() first, so audio and merged files worked; Retry runs
the same steps and fails the same way.
- AndroidPublicContentStore.length(): a pending row without a file returns null (catch
  FileNotFoundException; then the OpenableColumns.SIZE query; missing or 0 -> null), like
  FileDownloadDestination.temporaryLength(). A missing row or a SecurityException still fails
  as storage.
- DirectTransferEngine: a fresh download (resumeFrom == null) never reads the destination
  before prepare(); a resume whose length read fails drops the checkpoint and restarts at 0.
- Check the other engines and destinations (HLS, DASH, merge, MP3, SAF, app-private file) for a
  read before prepare(); fix what you find and list the result in the Result.
- The JVM fake store in PublicDownloadDestinationTest throws FileNotFoundException from
  length() for a MediaStore row that was never opened for writing (default on).
- New app/src/androidTest/java/com/alal/yft/download/MediaStoreDownloadInstrumentedTest.kt
  (runs on the CI emulator, API 34):
  (a) MediaStoreDownloadDestination.create(resolver, "yft-p20-<random>.mp4", "video/mp4") ->
      temporaryLength() does not throw -> prepare(1 MiB) -> writes at four offsets out of order
      -> commit() -> the published row has 1 MiB, is not pending and sits in Download/YFT/;
  (b) the real DirectTransferEngine with an OkHttpClient whose application interceptor answers
      in memory (200 + Content-Length, 206 + Content-Range for ranges; no network) downloads
      3 MiB in four segments into a fresh MediaStore destination -> Completed, bytes equal;
  (c) discard() removes the pending row.
  Each test deletes the rows it created in a finally block.
TESTS P20
- DirectTransferEngineTest: a fresh download into a destination whose length read throws
  before prepare() completes (must fail on the old code: STORAGE_UNAVAILABLE at 0 B); a resume
  whose length read fails restarts at 0 and completes.
- PublicDownloadDestinationTest with the Android-like fake.
- The instrumented test cannot run in the sandbox: the JVM tests are the regression proof, and
  the emulator-smoke run of your push must pass with MediaStoreDownloadInstrumentedTest in it.

WORK P21 — Retry and failure details (FIX_ADD_PLAN P21, finding R2)
- DownloadFailure gains stage (CONNECT, READ_SOURCE, OPEN_FILE, WRITE_FILE, PUBLISH, MERGE,
  CONVERT, VERIFY) and detail (exception class + message without addresses or tokens, at most
  120 characters), both default null. Every engine sets them where it catches. The record keeps
  them: a nullable lastErrorDetail column, Room 4 -> 5 migration, its schema JSON, a migration
  test.
- Honest reasons: destination calls are wrapped, so a file problem is STORAGE_UNAVAILABLE or
  INSUFFICIENT_STORAGE and a source problem is NETWORK or the HTTP reason; IllegalStateException
  means storage only when the destination lifecycle threw it; an unexpected exception in
  runTask keeps its class in detail (a new DownloadFailureReason value needs a label in
  DownloadLabels).
- Retry: after a storage failure, or when the pending row or partial file is gone or shorter
  than the checkpoint, discard the old destination and start again into a new one from byte 0;
  other failures resume as today.
- Downloads: a failed row shows its reason and a Details action (testTag
  download-failure-details) with reason, stage, HTTP status, detail, plan type (direct, HLS,
  DASH, merge, MP3), destination kind (MediaStore, SAF, app) and Android version, and Copy
  details (download-failure-copy). No addresses, no tokens. Keep every existing testTag.
TESTS P21
- A file write failure -> STORAGE_UNAVAILABLE + WRITE_FILE; a dropped connection -> NETWORK +
  READ_SOURCE (must fail on the old code: a source IOException counts as storage).
- Retry after a storage failure uses a new destination and completes (must fail on the old
  code: the same failure again).
- Migration 4 -> 5 keeps old records (core-data test).
- Compose: the Details dialog shows and copies text without http addresses.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository (/data/tmp, /data/bak); never undo work with git reset --hard,
git clean or git stash.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P20 (or P21), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result.

VALIDATE (after each task; report only what you ran)
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-data:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status DONE
   (date) or OWNER CHECK; Result with any "Plan adapted"; validation numbers; regression proof;
   CI links; hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P20: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke (P20:
   MediaStoreDownloadInstrumentedTest passes) and Preview APK. Fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the run links and the task's
   owner check (P20: YouTube 360p, a Facebook HD file, YouTube 720p, M4A and MP3 finish and
   play; P21: airplane mode during a download -> Details, Retry finishes, Copy details has no
   links). Then continue P20 -> P21 without waiting.
After P21: set your SESSION_STATE section to READY FOR MERGE (last commit, green CI links),
report, and stop. P26 (merge and Preview #4) starts only when the owner pastes
docs/prompts/M-merge-preview4.md.
```
