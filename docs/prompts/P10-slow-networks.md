# P10 — Slow networks

**ရည်ရွယ်ချက်:** Line နှေးတဲ့အခါ (5–10 KB/s) lookup က 15 စက္ကန့် / 25 စက္ကန့်မှာ မပြတ်တော့ဘဲ data ဝင်နေသရွေ့ စောင့်မယ်၊ ခဏပြတ်ရင် ၂ ကြိမ် ပြန်ကြိုးစားမယ်။ Home မှာ 90 စက္ကန့်အထိ စောင့်ပြီး "Slow connection — still looking…" နဲ့ Cancel ပြမယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 1 · Medium · AI agent အချိန် 4–6 နာရီ · လိုအပ်ချက်: —

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P10](../FIX_ADD_PLAN.md#p10--slow-networks)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P10 — Slow networks
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H1) and task P10 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: — (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P10 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P10 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Adapter requests (OkHttpExtractorClient) and headless page requests (HeadlessPageFetcher)
  fail only after 20 s without data (connect and read) or 60 s in total, not after 15 s in
  total. Body size limits stay.
- Up to two automatic retries after 1 s and 3 s for timeouts, connection failures and HTTP
  502/503/504; never for other HTTP answers, login walls or a cancelled lookup. YouTube
  player POSTs (read-only) may be retried. Retry delays injectable for tests.
- Home (LinkInspector): 90 s for the whole lookup instead of 25 s; after 10 s the status says
  "Slow connection — still looking…" with Cancel, which stops the lookup and its requests.
- Browser: a background site lookup failing with NETWORK sets no siteNotice; the Download
  button stays and the sheet (or the next tap) shows "Couldn't reach <Site>." with Retry.
- All other messages stay.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- MockWebServer, policy scaled down in the test: a slow body that keeps sending succeeds
  where the old total limit failed; no data for the idle limit fails; a dropped first
  connection succeeds on the retry with two requests (fails on the old code); 404 is not
  retried.
- Home with virtual time: an answer after 60 s succeeds (fails on the old code); the slow
  status after 10 s; Cancel. Browser: NETWORK sets no siteNotice; Retry runs again.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-browser:testDebugUnitTest :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (network limits), docs/TEST_MATRIX.md, CHANGELOG.md ##
   [Unreleased], the task's Result and status in FIX_ADD_PLAN (P10 -> DONE (date), or OWNER
   CHECK when only the phone check is left) and docs/SESSION_STATE.md (last task P10, next
   action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P10: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: on a slow line (or the phone's 2G/3G setting) paste a YouTube link -> "Slow
   connection — still looking…" -> the sheet opens; the browser shows no "could not be reached".
   Then continue with P11 without waiting (FIX_ADD_PLAN section 3, E7).
```
