# P1 — Browser follows in-page navigation

**ရည်ရွယ်ချက်:** YouTube လို page ထဲမှာပဲ video ပြောင်းတဲ့ site တွေမှာ browser က URL ပြောင်းတာကို သိအောင်လုပ်ပြီး watch/shorts ရောက်တာနဲ့ Download ခလုတ် ပေါ်စေမယ်။ Address bar လည်း မှန်မယ်။

**အချက်အလက်:** Phase 11 · Medium · AI agent အချိန် 3–5 နာရီ · လိုအပ်ချက်: —

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P1](../FIX_ADD_PLAN.md#p1--browser-follows-in-page-navigation)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P1 — Browser follows in-page navigation
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P1 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P1 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P1 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- doUpdateVisitedHistory -> new BrowserObservationSink.onUrlChanged(url) (main thread);
  ignore fragment-only changes and the same URL.
- BrowserViewModel: a changed URL is a new page: address, new candidate scope, cancel old
  probes, reset auto-retry, run the site adapters (debounced ~500 ms).
- YouTube /watch, /shorts/, youtu.be and Facebook/TikTok video URLs get a lookup at once; the
  Download button shows when the adapter returns savable media and hides when the user leaves.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- Client: history update -> onUrlChanged; fragment-only change -> nothing.
- ViewModel: feed -> watch A -> watch B (address, scope, adapter calls, stale A results dropped).
- FAB visible after an in-page change. The old code fails these tests.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (browser row), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P1 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P1, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P1: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: m.youtube.com -> tap a video -> address shows /watch -> Download button -> sheet opens.
   Then continue with P2 without waiting (owner, 2026-10-04).
```
