# T08 — YouTube: honest messages, identity and details

**ရည်ရွယ်ချက်:** YouTube မှာ 'sign in လိုတယ်' ဆိုတဲ့ မှားတဲ့ message အစား bot check message အမှန်ကို ပြပါမယ်။ SABR-only အဖြေကို သိအောင်လုပ်ပြီး client တစ်ခုချင်းရဲ့ ရလဒ်ကို Copy details ထဲ ထည့်ပါမယ်။

**အချက်အလက်:** Phase 8 · P1 · Easy · ၀.၅ ရက် · လိုအပ်ချက်: T04 နဲ့ T05 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T08](../FIX_PLAN.md#t08--youtube-honest-messages-identity-and-details)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T08 — YouTube: honest messages, identity and details
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F4, task T08; then docs/SESSION_STATE.md and
   every file under "Read first" in T08.
3. T04, T05 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T08 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T08 in order; stay inside the task (anything else -> FIX_PLAN §9).
- SiteExtractionFailure.BOT_CHECK (extractor-api). LOGIN_REQUIRED whose reason/error screen
  says "confirm you're not a bot" (straight or curly apostrophe) -> BOT_CHECK, not definite.
- Message: "YouTube wants to check that this is not a bot. Open the video in YFT's browser,
  let it play for a moment, then tap Download." + Open in browser. Real sign-in and age
  gates keep their own messages.
- streamingData with only serverAbrStreamingUrl (SABR) -> NO_MEDIA_FOUND + details 'SABR only'.
- T04 details per client: name, playability status + reason, progressive/adaptive formats
  with URLs, SABR flag. Home lookups use T05's identity for the watch page and player calls.
- Do not change the client order or add clients (that is T16).

TESTS (a regression test must fail on the old code)
- Bot-check fixture -> BOT_CHECK + the new message; SABR-only fixture; details redaction.

LIVE CHECK (public pages only; never paste bodies, cookies or signed URLs)
- bash scripts/live-check.sh https://www.youtube.com/watch?v=dQw4w9WgXcQ (the sandbox IP
  often gets the bot check; that is expected and useful).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (YouTube row: the 2026 limits), docs/RISKS.md, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Changed), the FIX_PLAN status board
   (T08 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T08, next action T09).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T08: YouTube bot-check message, SABR detection and lookup details"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   paste a YouTube link: found, or the new message -> Copy details -> send them
   (T16 needs what the phone receives).
   Then stop. Do not start T09.
```
