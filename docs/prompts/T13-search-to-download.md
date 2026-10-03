# T13 — "Search to download" page

**ရည်ရွယ်ချက်:** 'Search to download' page — link ဒါမှမဟုတ် စကားလုံး ရိုက်ပြီး ဖွင့်/ရှာ၊ copy ထားတဲ့ link card နဲ့ View sites grid ထည့်ပါမယ်။

**အချက်အလက်:** Phase 9 · P2 · Medium · ၁ ရက် · လိုအပ်ချက်: T03 နဲ့ T09 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T13](../FIX_PLAN.md#t13--search-to-download-page)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T13 — "Search to download" page
Branch: work/phase-8-field-fixes   (owner change 2026-10-03)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it (owner change 2026-10-03: nothing is merged
   or released before T19, so every task stays on this branch).
2. Read docs/FIX_PLAN.md §0, §3, task T13; then docs/SESSION_STATE.md and
   every file under "Read first" in T13.
3. T03, T09 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug
5. Set T13 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T13 in order; stay inside the task (anything else -> FIX_PLAN §9).
- Home entry 'Search to download' opens the browser start page (T03) in search mode.
- Field: a link opens it; words give two rows: Search YouTube for "..." ->
  https://m.youtube.com/results?search_query=... and Search the web for "..." ->
  https://duckduckgo.com/?q=... (URL-encode the words).
- 'Link you copied' card with Download (T11's rules). 'View sites' grid (YouTube, Facebook,
  TikTok, Instagram, X with T09 logos) + View all (full Your sites list with Add/Edit).
- WhatsApp Status stays in the backlog.

TESTS (a regression test must fail on the old code)
- Compose tests for each row and its navigation; accessibility audit; renders.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update DESIGN-NOTES (Browser start page / search), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T13 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T13, next action T14).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T13: Search to download page"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   Home > Search to download: type words -> YouTube/web search rows; type a link ->
   it opens; View sites opens each site.
   Then stop. Do not start T14.
```
