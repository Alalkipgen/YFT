# P11 — Rows never vanish

**ရည်ရွယ်ချက်:** Lookup တွေ့တဲ့ quality row အားလုံး ချက်ချင်း ပေါ်ပြီး ဘယ်တော့မှ မပျောက်တော့ဘူး (Facebook row ပျောက်တာ ပြင်)။ Size တွေကို နောက်မှ ဖြည့်မယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 1 · Medium · AI agent အချိန် 4–6 နာရီ · လိုအပ်ချက်: P9

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P11](../FIX_ADD_PLAN.md#p11--rows-never-vanish)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P11 — Rows never vanish
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H3) and task P11 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P9 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-media:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
4. Set P11 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P11 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Build rows from the adapter's data as soon as the lookup ends (height, codec, frame rate,
  bitrate, length); an unknown size shows "~<size>" from bitrate x length, or no size.
- Size checks (DefaultVariantResolver) run in the background, two at a time, and only
  update a row's size; a failed check keeps the row. QuickDownloadChoices.of no longer drops
  a source without a resolved asset.
- Final check when Download is tapped (with P10's retries); a dead link shows "This quality
  is not available now — choose another" in the sheet.
- Same height as a DASH row (known height) and a progressive file without one
  (browser_native_sd/hd): the short view keeps the DASH row; the file stays under More
  formats as "SD"/"HD".
- Rows keep their order while sizes arrive.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Unresolved sources still give every row (fails on the old code); a failed size check keeps
  the row; estimated size text; Download on a dead row shows the message and starts
  nothing; stable order.

LIVE CHECK (scripts/live-check.sh; report status, host, path, sizes and markers only)
   a public Facebook reel: heights and row count straight from the page, before any size check
   (heights and counts only)

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-media:testDebugUnitTest :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (Facebook), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased],
   the task's Result and status in FIX_ADD_PLAN (P11 -> DONE (date), or OWNER CHECK when only
   the phone check is left) and docs/SESSION_STATE.md (last task P11, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P11: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: a Facebook reel on the slow line -> every quality at once; sizes fill in;
   nothing disappears.
   Then continue with P12 without waiting (FIX_ADD_PLAN section 3, E7).
```
