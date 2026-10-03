# T03 — Browser start page; WebView only when a page is open

**ရည်ရွယ်ချက်:** Browser ကို အလွတ်ဖွင့်ရင် address bar ပျောက်နေတာ (P2) ကို ပြင်ပါမယ်။ Start page အသစ်ပြပြီး page ဖွင့်မှသာ WebView ကို ဖန်တီးပါမယ်။

**အချက်အလက်:** Phase 8 · P0 · Medium · ၁ ရက် · လိုအပ်ချက်: T01 ပြီးရမယ် (T02 screenshot တွေ အထောက်အကူဖြစ်)

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T03](../FIX_PLAN.md#t03--browser-start-page-webview-only-when-a-page-is-open)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T03 — Browser start page; WebView only when a page is open
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F2, task T03; then docs/SESSION_STATE.md and
   every file under "Read first" in T03.
3. T01 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T03 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T03 in order; stay inside the task (anything else -> FIX_PLAN §9).
- No page open -> do not compose BrowserWebView; show BrowserStartPage (browser-start):
  'Link you copied' row (Promptbox clipboard logic, read on tap) + Your sites shortcuts.
- Create the WebView on the first navigation; keep it for the screen's lifetime.
- Top bar above web content: clipToBounds() on the WebView container, higher zIndex for the
  top bar, status-bar insets applied exactly once (edge-to-edge, targetSdk 35).
- X (browser-close) and the address field are always visible. Replace BrowserEmptyHint;
  tests move from browser-empty to browser-start. Keep every other testTag.

TESTS (a regression test must fail on the old code)
- No page: browser-start shown, browser-surface absent. Go with an address: browser-surface
  exists, browser-start gone. X and the address field exist in both states.
- Update the accessibility audit and the Day/Night renders.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update DESIGN-NOTES (Screens > Browser + a new decision), docs/TEST_MATRIX.md, FIX_PLAN F2
   (confirmed cause), CHANGELOG.md ## [Unreleased] (Fixed), the FIX_PLAN status board
   (T03 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T03, next action T04).
   If T02 is done, check its emulator annotations/screenshots show the address bar on
   the empty and the loaded browser.
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T03: browser start page; WebView created on first navigation"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   Home > Open browser shows the start page with the address bar and X; opening a site
   keeps the address bar.
   Then stop. Do not start T04.
```
