# P17 — Reuse lookup results

**ရည်ရွယ်ချက်:** Video တစ်ခုတည်းကို မိနစ်ပိုင်းအတွင်း ထပ်မရှာတော့ဘူး — Home၊ browser နဲ့ sheet ပြန်ဖွင့်တာ result တစ်ခုတည်းကို မျှသုံးမယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 2 · Medium · AI agent အချိန် 4–6 နာရီ · လိုအပ်ချက်: P12, P16

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P17](../FIX_ADD_PLAN.md#p17--reuse-lookup-results)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P17 — Reuse lookup results
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P17 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P12, P16 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug
4. Set P17 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P17 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Memory-only lookup cache in SiteAdapterCoordinator: key = site + content ID + whether the
  user's session was used; max 20 entries, each kept until the earliest known link expiry
  (expiresAtEpochMs) or 10 minutes, whichever comes first.
- A second lookup of the same key while the first runs waits for it.
- Retry, a definite failure and a download that gets HTTP 403/410 drop the entry; Retry
  always asks the network.
- Nothing on disk; cache keys and addresses never reach logs.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Two lookups -> one adapter call (fails on the old code); expiry and the 10-minute limit;
  shared running lookup; session and no-session entries apart; Retry skips the cache; a
  403 drops the entry.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/ARCHITECTURE.md (lookup cache), docs/TEST_MATRIX.md, CHANGELOG.md ##
   [Unreleased], the task's Result and status in FIX_ADD_PLAN (P17 -> DONE (date), or OWNER
   CHECK when only the phone check is left) and docs/SESSION_STATE.md (last task P17, next
   action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P17: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: open a YouTube video's sheet from Home, close it, open the same video in
   the browser -> Download -> the qualities are there at once.
   Then continue with P18 without waiting (FIX_ADD_PLAN section 3, E7).
```
