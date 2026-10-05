# P16 — Sheet opens at once

**ရည်ရွယ်ချက်:** Snaptube လို နှိပ်တာနဲ့ sheet ချက်ချင်း ပွင့်ပြီး "Getting qualities…" ပြမယ်၊ lookup ပြီးတာနဲ့ row တွေ ဖြည့်မယ် (Home၊ browser ခလုတ်၊ feed)။

**အချက်အလက်:** Phase 11 part 2 · Group 2 · Hard · AI agent အချိန် 8–12 နာရီ · လိုအပ်ချက်: P9, P11, P12

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P16](../FIX_ADD_PLAN.md#p16--sheet-opens-at-once)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P16 — Sheet opens at once
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H7) and task P16 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P9, P11, P12 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P16 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P16 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Sheet waiting state: header from what is known (page title or link, site, YouTube's
  picture of the video ID), 2 Audio + 2 Video placeholder rows, "Getting qualities…".
- Home: a supported site's link opens the sheet at once and runs the lookup in it; other
  links keep today's check and open the sheet as soon as it answers.
- Browser: round and wide buttons (P12, P13) and a feed's focused link open it at once.
- Errors in the sheet with Retry (P10); closing the sheet stops its lookup.
- Keep quick-loading, quick-error, quick-retry and every other tag.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- ViewModel states (open -> waiting header -> rows; error -> Retry; close cancels); Home
  opens the sheet before the lookup ends (fails on the old code); feed path; no second
  lookup (P12).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/design/DESIGN-NOTES.md (sheet), docs/TEST_MATRIX.md, CHANGELOG.md ##
   [Unreleased], the task's Result and status in FIX_ADD_PLAN (P16 -> DONE (date), or OWNER
   CHECK when only the phone check is left) and docs/SESSION_STATE.md (last task P16, next
   action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P16: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: paste a YouTube link -> the sheet opens at once with the link and "Getting
   qualities…", then the rows; the same for a Facebook reel in the browser.
   Then continue with P17 without waiting (FIX_ADD_PLAN section 3, E7).
```
