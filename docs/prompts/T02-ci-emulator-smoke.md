# T02 — Real-WebView smoke test on a CI emulator

**ရည်ရွယ်ချက်:** GitHub Actions ပေါ်မှာ Android emulator နဲ့ တကယ့် WebView ကို စမ်းတဲ့ smoke test ထည့်ပါမယ်။ Crash နဲ့ layout ပြဿနာတွေကို ဖုန်းမရောက်ခင် ဖမ်းနိုင်ဖို့ပါ။

**အချက်အလက်:** Phase 8 · P0 · Medium · ၁ ရက် · လိုအပ်ချက်: T01 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T02](../FIX_PLAN.md#t02--real-webview-smoke-test-on-a-ci-emulator)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T02 — Real-WebView smoke test on a CI emulator
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F2, task T02; then docs/SESSION_STATE.md and
   every file under "Read first" in T02.
3. T01 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
   D4 is assumed YES unless FIX_PLAN §3 says NO (then set T02 to SKIPPED (D4) and stop).
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
5. Set T02 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T02 in order; stay inside the task (anything else -> FIX_PLAN §9).
- androidTest deps through the version catalog: androidx.test runner, rules, ext:junit,
  uiautomator (compose ui-test-junit4 is already there; the runner is already set).
- app/src/androidTest/java/com/alal/yft/smoke/BrowserSmokeTest.kt with
  createAndroidComposeRule<MainActivity>(). MainActivity only has a MAIN/LAUNCHER filter, so
  drive the UI: home-open-browser, browser-address, browser-go, browser-surface,
  browser-close, media-found-button, found-sheet.
- Three scenarios and screenshots (UiAutomation.takeScreenshot(), app external files dir):
  01-browser-empty, 02-browser-page (https://example.com/), 03-found (a Wikimedia Commons
  File:*.webm page; slow site = warning, not failure).
- New .github/workflows/emulator-smoke.yml: push to work/phase-* (paths app/**, core-*/**,
  extractor-*/**, gradle/**, the workflow) and workflow_dispatch; KVM udev rule; AVD cache;
  reactivecircus/android-emulator-runner@v2 (api-level 34, google_apis, x86_64,
  disable-animations) running ./gradlew :app:connectedDebugAndroidTest; adb pull the
  screenshots; adb logcat -d > logcat.txt; upload screenshots, logcat and test report.
- Annotations (agents cannot download artifacts): ::error:: for each FATAL EXCEPTION,
  ::notice:: with the bounds of browser-address and the WebView (uiautomator dump).
  Read them without a token: actions/runs?branch=... -> jobs -> check-runs/<job id>/annotations.
- Never assert YouTube (datacenter IP). Keep the job under ~20 min; do not touch the
  existing checkpoint workflow.

TESTS (a regression test must fail on the old code)
- The instrumentation test compiles locally (:app:assembleDebugAndroidTest).
- The emulator job is green on the branch; logcat has no FATAL EXCEPTION.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/TEST_MATRIX.md (emulator smoke row), FIX_PLAN F2 (first emulator result: is the
   address bar visible?), CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T02 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T02, next action T03).
   The task is DONE only when the emulator job is green. If it is red, read its
   annotations, fix, checkpoint again.
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T02: CI emulator smoke test for the real WebView"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   nothing new on the phone; mention the emulator screenshots artifact.
   Then stop. Do not start T03.
```
