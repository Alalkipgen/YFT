# P4 — Facebook: one video, every quality

**ရည်ရွယ်ချက်:** Facebook item ၃ ခုကို video တစ်ခုအဖြစ် ပေါင်းပြီး ရှိသမျှ quality (360p–1080p၊ ရှိရင် အထက်) ကို resolution မှန်မှန်နဲ့ ပြမယ်၊ audio track ကနေ M4A/MP3 ရမယ်။

**အချက်အလက်:** Phase 11 · Medium–Hard · AI agent အချိန် 5–8 နာရီ · လိုအပ်ချက်: P3

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P4](../FIX_ADD_PLAN.md#p4--facebook-one-video-every-quality)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P4 — Facebook: one video, every quality
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P4 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-sites:test :core-media:testDebugUnitTest :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P4 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P4 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Read Facebook DASH representations as whole files (BaseURL; the SegmentBase index is not
  needed) with height, width, frame rate, codecs and bandwidth (FIX_ADD_PLAN G3).
- Offer every video height (360p ... 1080p and higher when present) merged with the AAC audio
  representation (AVC + AAC -> MP4, existing muxer); keep SD/HD progressive MP4 rows with their
  probed height.
- Music rows from the audio representation (M4A, MP3).
- Sizes: Content-Length from a ranged request, else bandwidth x duration (estimated).
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- Parser tests on a sanitized Facebook DASH fixture (heights, audio, whole-file URLs).
- Plan tests (merge choice). Live check of a public reel: status, heights, markers only.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-sites:test :core-media:testDebugUnitTest :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (Facebook), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P4 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P4, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P4: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: Facebook link -> sheet shows 360p/720p/1080p (when present) and Music -> each downloads and plays with sound.
   Then continue with P5 without waiting (owner, 2026-10-04).
```
