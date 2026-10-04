# P6 — 2K and 4K

**ရည်ရွယ်ချက်:** Source မှာ 2K/4K ရှိရင် download လို့ရအောင်လုပ်မယ်။ YouTube VP9 + Opus ကို .webm အဖြစ် ပေါင်းမယ်၊ AV1 က Android 14+ မှာပဲ။ ဖုန်းက မဖွင့်နိုင်ရင် သတိပေးမယ်။

**အချက်အလက်:** Phase 11 · Hard · AI agent အချိန် 8–12 နာရီ · လိုအပ်ချက်: P3

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P6](../FIX_ADD_PLAN.md#p6--2k-and-4k)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P6 — 2K and 4K
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P6 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-sites:test :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P6 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P6 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- YouTube 1440p (2K) and 2160p (4K): VP9 video-only WebM + the Opus audio stream, merged
  with MediaMuxer WebM output (Android 7+) into .webm (owner decision E4).
- AV1-only qualities only on Android 14+ (MP4 output); otherwise not offered.
- Check MediaCodecList for a decoder of that size; missing -> still offered with
  "may not play on this phone".
- Facebook heights above 1080p use the P4 AVC path.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- Format selection (VP9 + Opus pairing, AV1 gated by API level), WebM mux plan.
- Instrumentation test on the CI emulator: a short VP9 + Opus fixture merged into WebM.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-sites:test :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (YouTube), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P6 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P6, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P6: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: a YouTube 4K video -> 2K/4K rows -> download -> it plays (or shows the warning).
   Then continue with P7 without waiting (owner, 2026-10-04).
```
