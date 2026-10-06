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

- Status: IN PROGRESS — P21 next (started 2026-10-06, base `f434724` = `origin/work/phase-12-integration`)
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
- P21 — Retry and failure details: TODO
- Hand-offs: none

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
