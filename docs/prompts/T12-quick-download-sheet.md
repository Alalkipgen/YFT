# T12 — "Video you copied" quick download sheet

**ရည်ရွယ်ချက်:** Home မှာ video တွေ့ရင် SnapTube လို 'Video you copied' sheet ပေါ်လာပြီး Music (M4A) / Video (Fast၊ High) ကို ရွေးပြီး တန်းဒေါင်းလို့ရအောင် လုပ်ပါမယ်။

**အချက်အလက်:** Phase 9 · P2 · Medium–Hard · ၁.၅–၂ ရက် · လိုအပ်ချက်: မရှိ (T11 မလိုဘဲ Go နှိပ်ပြီး အလုပ်လုပ်တယ်)

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T12](../FIX_PLAN.md#t12--video-you-copied-quick-download-sheet)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T12 — "Video you copied" quick download sheet
Branch: work/phase-9-copied-link-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-9-copied-link-flow and pull it. If it does not exist, create it from origin/main,
   but only when `git merge-base --is-ancestor v1.0.0-beta.3 origin/main` succeeds; otherwise
   stop and report that the previous release is not merged yet.
2. Read docs/FIX_PLAN.md §0, §3, task T12; then docs/SESSION_STATE.md and
   every file under "Read first" in T12.
3. No task or decision has to come first.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug
5. Set T12 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T12 in order; stay inside the task (anything else -> FIX_PLAN §9).
- Sheet 'Video you copied': thumbnail, title, length. Music: 'M4A · Fast' only when an
  audio-only stream exists. Video: Fast = highest variant <= 480p; High = highest <= 720p
  when different. Real labels and sizes ('480p · 18 MB'), never a made-up height.
- More formats -> the existing 'Download as' sheet. Download queues the selected row and
  respects Wi-Fi only, the metered confirmation and the default-quality preselection.
- Row selection = a pure, unit-tested function; QuickDownloadSheet = a dialog destination
  like 'Download as' (YftNavHost). Home opens it on Found with exactly one video.

TESTS (a regression test must fail on the old code)
- Selection table: only 360p -> one row; 1080/720/480/360 -> Fast 480p + High 720p; audio
  only -> Music only. Compose tests; accessibility audit; renders.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update DESIGN-NOTES (new screen + decision), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T12 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T12, next action T11).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T12: Video you copied quick download sheet"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   paste a link -> Go -> the sheet shows Fast/High/Music rows -> Download -> it plays.
   Then stop. Do not start T11.
```
