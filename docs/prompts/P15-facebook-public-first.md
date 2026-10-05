# P15 — Facebook public page first

**ရည်ရွယ်ချက်:** Public Facebook video ကို session မပါဘဲ Safari page တစ်ကြိမ်တည်းနဲ့ ရအောင် လုပ်မယ် (request ၂ ကြိမ် → ၁ ကြိမ်)။

**အချက်အလက်:** Phase 11 part 2 · Group 2 · Medium–Hard · AI agent အချိန် 4–7 နာရီ · လိုအပ်ချက်: P10, P11

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P15](../FIX_ADD_PLAN.md#p15--facebook-public-page-first)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P15 — Facebook public page first
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H4) and task P15 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P10, P11 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
4. Set P15 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P15 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Live check first: the Safari page without a session for two public reels, a /watch video
  and a share/r/ link (status, final host and path, video ID match, track heights).
- Ask first as Safari (FacebookPageIdentity.AVC_LADDER_USER_AGENT) without cookies.
- That page has the video (same ID) with AVC tracks or browser_native_sd/hd -> done, one
  request.
- Otherwise (login wall, no video of that ID, unresolved share link): today's path - the
  session page as the Chrome desktop identity, then the AVC ladder only when needed.
- The session never travels with the Safari identity.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Public reel fixture -> one request without Cookie (fails on the old code: two requests,
  the first with the session); a video only the session sees -> the session request;
  share link; login wall.

LIVE CHECK (scripts/live-check.sh; report status, host, path, sizes and markers only)
   step 1 again after the change, with the number of requests (no bodies or media URLs)

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (Facebook), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased],
   the task's Result and status in FIX_ADD_PLAN (P15 -> DONE (date), or OWNER CHECK when only
   the phone check is left) and docs/SESSION_STATE.md (last task P15, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P15: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: a Facebook reel on the slow line opens faster, with every quality.
   Then continue with P16 without waiting (FIX_ADD_PLAN section 3, E7).
```
