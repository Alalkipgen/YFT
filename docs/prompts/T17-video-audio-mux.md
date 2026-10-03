# T17 — Higher qualities: merge video and audio

**ရည်ရွယ်ချက်:** 720p/1080p လို quality မြင့်တွေအတွက် video နဲ့ audio ကို သီးခြားဒေါင်းပြီး ဖုန်းပေါ်မှာ ပေါင်း (mux) ပါမယ် (AVC + AAC)။

**အချက်အလက်:** Phase 10 · P3 · Hard · ၂–၃ ရက် · လိုအပ်ချက်: T16 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T17](../FIX_PLAN.md#t17--higher-qualities-merge-video-and-audio)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T17 — Higher qualities: merge video and audio
Branch: work/phase-8-field-fixes   (owner change 2026-10-03)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it (owner change 2026-10-03: nothing is merged
   or released before T19, so every task stays on this branch).
2. Read docs/FIX_PLAN.md §0, §3, finding F4, task T17; then docs/SESSION_STATE.md and
   every file under "Read first" in T17.
3. T16 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-model:test :core-download:testDebugUnitTest :core-media:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T17 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T17 in order; stay inside the task (anything else -> FIX_PLAN §9).
- Let AudioVideoMuxDownloadPlan accept a video-only MP4 (AVC) + an audio-only M4A (AAC):
  DashDownloadPlan with one whole-file segment, or a direct-track variant.
- YouTube candidates carry their audio companion (MediaCandidate field or a pair type)
  through Detected media, Preview, 'Download as' and the quick sheet. Offer 480p/720p/1080p
  AVC; skip VP9 and AV1 (the mux engine is AVC/AAC only).
- googlevideo.com files in ranged chunks of at most 10 MB; resume each track separately.
- AudioVideoMuxEngine.evaluate before queueing.

TESTS (a regression test must fail on the old code)
- Plan building, compatibility refusals, per-track resume, failure mapping.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-model:test :core-download:testDebugUnitTest :core-media:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (YouTube + separate tracks), docs/ARCHITECTURE.md (download plans),
   docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T17 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T17, next action T18).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-model:test :core-download:testDebugUnitTest :core-media:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T17: merge video and audio for higher qualities"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   download YouTube 720p -> the merged file plays with sound.
   Then stop. Do not start T18.
```
