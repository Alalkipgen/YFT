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

- Status: NOT STARTED
- P20 — Video downloads save again: TODO
- P21 — Retry and failure details: TODO
- Hand-offs: none

## Agent B — `work/phase-12-site-qualities` (P22, P23)

- Status: IN PROGRESS — P23 next (started 2026-10-06 in `/data/YFT-B`; base `f434724` =
  `origin/work/phase-12-integration`)
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
- P23 — Facebook: every quality: TODO
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
