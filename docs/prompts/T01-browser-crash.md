# T01 — Browser crash: WebView used off the main thread

**ရည်ရွယ်ချက်:** Browser မှာ ဆိုက်တစ်ခုခု ဖွင့်လိုက်တိုင်း app ပိတ်သွားတဲ့ ပြဿနာ (P3) ကို ပြင်ပါမယ်။ WebView ကို background thread ကနေ ခေါ်နေတာက အကြောင်းရင်းပါ။

**အချက်အလက်:** Phase 8 · P0 · Easy–Medium · ၀.၅ ရက် · လိုအပ်ချက်: မရှိ

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T01](../FIX_PLAN.md#t01--browser-crash-webview-used-off-the-main-thread)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T01 — Browser crash: WebView used off the main thread
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F1, task T01; then docs/SESSION_STATE.md and
   every file under "Read first" in T01.
3. No task or decision has to come first.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T01 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T01 in order; stay inside the task (anything else -> FIX_PLAN §9).
- The crash: `view.url` (SecureBrowserWebViewClient.kt:46) and `userAgentProvider()` ->
  `browser.settings.userAgentString` (BrowserScreen.kt:812) run inside
  `shouldInterceptRequest`, which Android calls on a background thread.
- Cache the main-frame URL in an AtomicReference written on the main thread (onPageStarted,
  doUpdateVisitedHistory, load start). Read the User-Agent once when the WebView is set up.
- Audit every @JavascriptInterface method and other background callbacks; post WebView work
  to the main thread. Make the observation sink safe for parallel calls.
- Behaviour stays the same: observation, header capture, blocking rules, DRM hints.

TESTS (a regression test must fail on the old code)
- Robolectric WebView subclass whose getUrl()/getSettings() throw off the main looper;
  call shouldInterceptRequest from a background executor; it must fail on the old code.
- The URL holder follows navigation start, history updates and redirects.
- All existing browser tests stay green.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/TEST_MATRIX.md, docs/SUPPORT_MATRIX.md (browser row), CHANGELOG.md ## [Unreleased] (Fixed), the FIX_PLAN status board
   (T01 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T01, next action T02).
   Name the grep you used to prove no WebView/WebSettings call is left in background
   callbacks in the commit message.
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T01: browser no longer calls WebView off the main thread"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   Your sites > any site; Home > Open in browser; type an address and Go. Pages load,
   the app stays open and found media still appear.
   Then stop. Do not start T02.
```
