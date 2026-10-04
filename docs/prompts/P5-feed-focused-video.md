# P5 — Download button on feeds

**ရည်ရွယ်ချက်:** YouTube/Facebook/TikTok feed မှာလည်း Download ခလုတ် ပေါ်မယ်။ နှိပ်ရင် screen မှာ focus ဖြစ်နေတဲ့ video ကို ရှာပြီး အဲဒီ video ရဲ့ sheet ကို ဖွင့်မယ်။

**အချက်အလက်:** Phase 11 · Hard · AI agent အချိန် 6–10 နာရီ · လိုအပ်ချက်: P1, P3

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P5](../FIX_ADD_PLAN.md#p5--download-button-on-feeds)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P5 — Download button on feeds
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P5 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
4. Set P5 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P5 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- On YouTube, Facebook and TikTok pages show the Download button even without a found file.
- On tap a main-thread script finds the video in focus (the playing <video>, or the item
  closest to the viewport centre) and its link (/watch?v=, /shorts/, /reel/, /video/).
- Look that link up with the site adapter and open the P3 sheet; no video in focus -> a short
  message.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- Script result parsing (sanitized feed markup fixtures), URL extraction per site.
- ViewModel flow (tap -> lookup -> sheet); no script on other sites.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P5 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P5, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P5: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: YouTube home feed -> scroll to a video -> Download -> the sheet is for that video.
   Then continue with P6 without waiting (owner, 2026-10-04).
```
