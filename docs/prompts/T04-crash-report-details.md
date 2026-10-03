# T04 — Local crash report and lookup details

**ရည်ရွယ်ချက်:** App ပိတ်သွားရင် crash report ကို ဖုန်းထဲမှာပဲ သိမ်းပြီး About ကနေ Share လုပ်လို့ရအောင်၊ Home မှာ ရှာမတွေ့ရင် အကြောင်းရင်းကို 'Copy details' နဲ့ ကူးလို့ရအောင် လုပ်ပါမယ်။

**အချက်အလက်:** Phase 8 · P0 · Easy–Medium · ၀.၅–၁ ရက် · လိုအပ်ချက်: မရှိ

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T04](../FIX_PLAN.md#t04--local-crash-report-and-lookup-details)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T04 — Local crash report and lookup details
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, findings F7, F8, task T04; then docs/SESSION_STATE.md and
   every file under "Read first" in T04.
3. No task or decision has to come first.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T04 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T04 in order; stay inside the task (anything else -> FIX_PLAN §9).
- CrashReportStore (app, diagnostics/): default uncaught-exception handler installed in
  YftApplication.onCreate() writes noBackupFilesDir/diagnostics/last-crash.txt (overwrite,
  max 64 KB: UTC time, version + versionCode, SDK, maker/model, thread, exception chain),
  every message through SensitiveValueRedactor, then calls the previous handler.
- About: 'Last crash report' row while a report exists: View, Copy, Share (ACTION_SEND
  text/plain), Delete. Nothing is sent automatically.
- SiteExtractionResult.Failure gets `details: List<String> = emptyList()` (source compatible).
  Coordinator/Home keep the last failure's details in memory; the not-found card gets
  Copy details (home-copy-details). Short steps only, e.g. `page GET 200 (612 KB)`.
- Debug builds only: hidden 'Crash now' (e.g. long press on the About version).

TESTS (a regression test must fail on the old code)
- Handler writes, truncates, redacts and delegates; About row visibility and actions.
- Details never contain `?`, `Cookie`, `signature=` or `pot=`.
- 'Crash now' does not exist in the release variant.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/TEST_MATRIX.md, docs/PROJECT_CONTEXT.md privacy (local report, never uploaded), CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T04 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T04, next action T05).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T04: local crash report and lookup details"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   whenever YFT closes by itself: About > Last crash report > Share it with the agent.
   Then stop. Do not start T05.
```
