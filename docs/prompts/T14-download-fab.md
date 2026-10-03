# T14 — Floating Download button in the browser

**ရည်ရွယ်ချက်:** Browser ထဲမှာ ဒေါင်းလို့ရတဲ့ video တွေ့ရင် floating Download ခလုတ် ပေါ်လာပြီး နှိပ်ရင် quick sheet ပွင့်ပါမယ်။

**အချက်အလက်:** Phase 9 · P2 · Easy–Medium · ၀.၅–၁ ရက် · လိုအပ်ချက်: T12 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T14](../FIX_PLAN.md#t14--floating-download-button-in-the-browser)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T14 — Floating Download button in the browser
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
2. Read docs/FIX_PLAN.md §0, §3, task T14; then docs/SESSION_STATE.md and
   every file under "Read first" in T14.
3. T12 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T14 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T14 in order; stay inside the task (anything else -> FIX_PLAN §9).
- browser-download-fab (Mint, bottom end above the toolbar, count badge when > 1) while the
  page has savable candidates. Tap -> T12 sheet for the best video (several -> 'Found on
  this page').
- Hide while a new page starts, when only DRM candidates exist and while the found sheet is
  expanded. Keep the existing found sheet and its handle.

TESTS (a regression test must fail on the old code)
- Visibility rules (unit tests); a Compose test; accessibility label "Download video, N found".

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update DESIGN-NOTES (Browser), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T14 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T14, next action T15).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T14: floating Download button in the browser"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   open a video page in the browser -> the Download button appears -> tap -> sheet ->
   download plays.
   Then stop. Do not start T15.
```
