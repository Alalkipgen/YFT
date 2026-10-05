# P18 — Download before qualities arrive

**ရည်ရွယ်ချက်:** Quality တွေ မပေါ်ခင် Download နှိပ်ထားလို့ ရမယ်၊ lookup ပြီးတာနဲ့ ရွေးထားတဲ့ quality (မရှိရင် အနီးဆုံး အောက်) နဲ့ စ download မယ်။ ပြီးရင် Preview #3 APK link ပေးမယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 2 (last) · Medium–Hard · AI agent အချိန် 4–7 နာရီ · လိုအပ်ချက်: P16

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P18](../FIX_ADD_PLAN.md#p18--download-before-qualities-arrive)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P18 — Download before qualities arrive
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P18 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P16 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P18 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P18 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- While the sheet waits, Download is enabled for the default choice (720p or the user's
  Default quality; Audio when an Audio placeholder was picked); a tap queues it and shows
  "Starts when ready…".
- When rows arrive: the chosen quality, else the nearest lower, else the nearest higher;
  start and close as today, saying which quality was taken ("Downloading 480p — 720p not
  available").
- A failed lookup drops the queued choice and shows the error with Retry; closing cancels.
- Every choice still passes today's checks (storage, Wi-Fi only, mobile-data prompt).
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Queued, then started with the exact, lower and higher pick; failure; closing cancels;
  checks still apply (fails on the old code: Download disabled while waiting).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the task's Result and status in
   FIX_ADD_PLAN (P18 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P18, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P18: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: paste a link and tap Download at once -> the download starts with the right
   quality.
   Then Preview #3 (FIX_ADD_PLAN P18 "After P18"): full validation, a green Preview APK run of
   this commit, its link and the FIX_ADD_PLAN section 6 Preview #3 list to the owner, in
   Burmese. Then stop and wait for his phone test; P8 only with his OK.
```
