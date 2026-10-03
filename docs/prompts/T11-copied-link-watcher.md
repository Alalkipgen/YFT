# T11 — Check the copied link when YFT opens

**ရည်ရွယ်ချက်:** တခြား app မှာ link ကို copy ပြီး YFT ဖွင့်လိုက်တာနဲ့ အလိုအလျောက် စစ်ပေးပါမယ်။ D1 = YES ဖြစ်မှ လုပ်ပါမယ်။

**အချက်အလက်:** Phase 9 · P2 · Medium · ၁ ရက် · လိုအပ်ချက်: D1 = YES (owner အဖြေ)

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ D1 ကို ဖြေပြီးသားမဟုတ်ရင် `OWNER ANSWERS:` line မှာ ဖြေချက်ထည့်ပါ (ဥပမာ `D1=YES`)။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T11](../FIX_PLAN.md#t11--check-the-copied-link-when-yft-opens)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T11 — Check the copied link when YFT opens
Branch: work/phase-8-field-fixes   (owner change 2026-10-03)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none   (example: D1=YES — record answers in FIX_PLAN §3 first)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it (owner change 2026-10-03: nothing is merged
   or released before T19, so every task stays on this branch).
2. Read docs/FIX_PLAN.md §0, §3, task T11; then docs/SESSION_STATE.md and
   every file under "Read first" in T11.
3. D1 must be YES. PENDING -> ask the D1 question (FIX_PLAN §3) in Burmese and stop.
   NO -> set T11 to SKIPPED (D1), keep tap-to-paste, checkpoint the docs and stop.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T11 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T11 in order; stay inside the task (anything else -> FIX_PLAN §9).
- checkCopiedLinks setting (DataStore, default from D1) + Settings > Privacy switch
  "Check copied links when YFT opens" with one line about Android's paste message.
- CopiedLinkWatcher: on foreground with window focus (Android 10+) and the setting on: check
  the clip description (text; API 31+ skip clips with known low URL confidence), read the
  clip, first http(s) URL via HomeLinks, emit once per distinct clip. Keep only a hash of the
  last clip, in memory. Never store or log the text.
- Home: an emitted link fills the Promptbox and starts the lookup like Use; found -> the T12
  sheet (if T12 is done).

TESTS (a regression test must fail on the old code)
- Setting off -> no read; non-link clip -> no read on API 31+; same clip twice -> one
  lookup; no read without window focus; no text stored.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update DESIGN-NOTES decision 1, docs/PROJECT_CONTEXT.md privacy, the About privacy text, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T11 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T11, next action T13).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T11: check the copied link when YFT opens"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   copy a link in another app -> open YFT -> the lookup starts by itself (Android shows
   its paste message); the Settings switch turns it off.
   Then stop. Do not start T13.
```
