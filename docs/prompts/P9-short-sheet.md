# P9 — Short download sheet

**ရည်ရွယ်ချက်:** Download sheet ကို Snaptube လို တစ်မျက်နှာထဲ ဆံ့အောင် တိုမယ် — Audio ၂ ခု (M4A, MP3 128)၊ Video ၂ ခု (720p ကို default ရွေးထား၊ 480p)၊ ကျန်တာ "More formats" ထဲမှာ၊ Download ခလုတ်က အောက်ခြေမှာ အမြဲ မြင်ရမယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 1 · Medium · AI agent အချိန် 4–6 နာရီ · လိုအပ်ချက်: —

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P9](../FIX_ADD_PLAN.md#p9--short-download-sheet)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P9 — Short download sheet
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H2) and task P9 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: — (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug
4. Set P9 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P9 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Short view: Audio = "M4A" (original sound, copied) and "MP3 · 128 kbps"; Video = the
  preferred quality and the next lower one (720p · HD and 480p by default, 360p when there
  is no 480p); a missing quality falls back to the nearest lower, then the nearest higher one.
  Row names stay as in P3-FIX (no "Fast"/"High" titles).
- "More formats · N" expands the same sheet to today's full list (every row once, today's
  order); "Fewer formats" goes back; the selection stays. No new screen.
- Pin Download and the actions beside it below the scrolling rows, in both views.
- DownloadPreferences.defaultQuality -> UP_TO_720P for anyone who never chose one; Settings
  keeps every choice (Highest too); a saved choice stays.
- New tags quick-more-formats, quick-fewer-formats; every existing tag stays.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Choices table: 720p + 480p default; 720p missing -> nearest lower; only 1080p and 4K ->
  1080p first; Audio M4A + MP3 128; More formats lists every row once.
- Compose: quick-download is displayed without scrolling with 12 rows expanded (fails on
  the old code); the new default (fails on the old code); Settings still offers Highest.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/design/DESIGN-NOTES.md (sheet), docs/TEST_MATRIX.md, CHANGELOG.md ##
   [Unreleased], the task's Result and status in FIX_ADD_PLAN (P9 -> DONE (date), or OWNER CHECK
   when only the phone check is left) and docs/SESSION_STATE.md (last task P9, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P9: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: a YouTube 4K video -> 2 Audio + 2 Video rows, 720p selected, Download
   visible; More formats -> 2K/4K rows; a 720p and an MP3 download work.
   Then continue with P10 without waiting (FIX_ADD_PLAN section 3, E7).
```
