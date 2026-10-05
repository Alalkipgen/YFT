# P13 — Wide Download button

**ရည်ရွယ်ချက်:** Video page ပေါ်မှာ Snaptube လို အကျယ် "Download" ခလုတ်ကြီး ပြမယ် (YFT ကိုယ်တိုင် ဆွဲတာ၊ site ရဲ့ page ထဲ မထည့်ဘူး)။

**အချက်အလက်:** Phase 11 part 2 · Group 1 · Easy · AI agent အချိန် 2–4 နာရီ · လိုအပ်ချက်: P12

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P13](../FIX_ADD_PLAN.md#p13--wide-download-button)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P13 — Wide Download button
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H8) and task P13 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P12 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug
4. Set P13 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P13 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- On a site video page (and a generic page with one main video) a full-width "Download"
  button at the bottom of the page area, above the bottom bar; the WebView gets matching
  bottom padding so the page's own controls stay reachable.
- It does what the round button does there (P12) and shows a spinner while the lookup runs.
- One button at a time: the round button hides while the wide one shows; feeds and other
  pages keep the round button (P5).
- Hidden in full screen, while the sheet is open and while the keyboard is up.
- New tag browser-download-wide; content description "Download this video".
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Visibility table (video page, feed, generic page with one or several videos, full screen,
  sheet open, keyboard); a tap sends the round button's event; the round button hides
  (fails on the old code: no wide button).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/design/DESIGN-NOTES.md (browser), docs/TEST_MATRIX.md, CHANGELOG.md ##
   [Unreleased], the task's Result and status in FIX_ADD_PLAN (P13 -> DONE (date), or OWNER
   CHECK when only the phone check is left) and docs/SESSION_STATE.md (last task P13, next
   action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P13: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: a YouTube watch page and a Facebook reel -> the wide Download button -> the
   video's sheet.
   Then continue with P19 without waiting (FIX_ADD_PLAN section 3, E7).
```
