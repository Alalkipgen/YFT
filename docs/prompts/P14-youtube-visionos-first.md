# P14 — YouTube asks visionOS first

**ရည်ရွယ်ချက်:** YouTube lookup မှာ watch page (166 KB) ကို အရင် မယူတော့ဘဲ visionOS client (17 KB ခန့်) ကို အရင် မေးမယ် — line နှေးလည်း မြန်မယ်။ ADR-006 စည်းကမ်း (private/age-gate/DRM မကျော်) မပြောင်းဘူး။

**အချက်အလက်:** Phase 11 part 2 · Group 2 · Hard · AI agent အချိန် 6–10 နာရီ · လိုအပ်ချက်: P10

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P14](../FIX_ADD_PLAN.md#p14--youtube-asks-visionos-first)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P14 — YouTube asks visionOS first
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H5) and task P14 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P10 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
4. Set P14 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P14 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Live check first (3 public videos: 1080p, 4K, a Short): VISIONOS alone vs today's chain
  (heights, codecs, audio, sizes) plus a ranged GET of one stream (status, length only). Go
  on only if visionOS alone gives the same rows; else record the gap and adapt.
- Ask VISIONOS first, without the user's cookie, as chain step 2 does today.
- Accept it without the watch page only when complete: playabilityStatus OK, title and
  length, no DRM, not live, an AVC video with an audio track, every format with a direct
  address (no signatureCipher) and a contentLength.
- Anything else runs today's chain unchanged (watch page first; the page's verdict ends
  the lookup). No client unlocks what the page refused.
- Update ADR-006 Implementation (new step order) and docs/YOUTUBE_RISK_REVIEW.md.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Sanitized visionOS fixture -> success with one request, no watch page (fails on the old
  code); login/age/unplayable -> watch page chain; an age check never leads to another
  client's answer; missing contentLength or a signatureCipher -> chain; DRM -> refused.

LIVE CHECK (scripts/live-check.sh; report status, host, path, sizes and markers only)
   the step 1 comparison, then the final run: status, host, path, number of requests and bytes,
   heights (never bodies or stream URLs)

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/decisions/ADR-006-owner-override-any-working-method.md,
   docs/YOUTUBE_RISK_REVIEW.md, docs/SUPPORT_MATRIX.md (YouTube), docs/TEST_MATRIX.md,
   CHANGELOG.md ## [Unreleased], the task's Result and status in FIX_ADD_PLAN (P14 -> DONE
   (date), or OWNER CHECK when only the phone check is left) and docs/SESSION_STATE.md (last
   task P14, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P14: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: on the slow line a YouTube link opens clearly faster; 720p, 1080p and 4K
   download and play.
   Then continue with P15 without waiting (FIX_ADD_PLAN section 3, E7).
```
