# T18 — MP3 audio

**ရည်ရွယ်ချက်:** MP3 audio ထုတ်ပေးပါမယ် (LAME၊ NDK)။ D3 = YES ဖြစ်မှ လုပ်ပါမယ်။

**အချက်အလက်:** Phase 10 · P3 · Hard · ၂–၃ ရက် · လိုအပ်ချက်: D3 = YES (owner အဖြေ)

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ D3 ကို ဖြေပြီးသားမဟုတ်ရင် `OWNER ANSWERS:` line မှာ ဖြေချက်ထည့်ပါ (ဥပမာ `D3=YES`)။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T18](../FIX_PLAN.md#t18--mp3-audio)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T18 — MP3 audio
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
2. Read docs/FIX_PLAN.md §0, §3, task T18; then docs/SESSION_STATE.md and
   every file under "Read first" in T18.
3. D3 must be YES. PENDING -> ask the D3 question (FIX_PLAN §3) in Burmese and stop.
   NO or LATER -> set T18 to SKIPPED (D3), checkpoint the docs and stop.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T18 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T18 in order; stay inside the task (anything else -> FIX_PLAN §9).
- LAME 3.100 built with the NDK (CMake) as a shared library for arm64-v8a, armeabi-v7a,
  x86_64; pin ndkVersion; CI installs the NDK (both workflows).
- AAC -> PCM with MediaCodec -> MP3 CBR 128 and 192 kbps with an ID3 title.
- 'MP3' row in the quick sheet and 'Download as'.
- LGPL: notice, licence text and source location in THIRD_PARTY_NOTICES.md and About.
- Measure and record the APK size change (about +1 MB expected).

TESTS (a regression test must fail on the old code)
- JVM tests for the control logic; an instrumentation test on the T02 emulator encoding a
  short AAC fixture.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/THIRD_PARTY_NOTICES.md, docs/SUPPORT_MATRIX.md, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Added), the FIX_PLAN status board
   (T18 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T18, next action T19).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T18: MP3 audio"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   download MP3 -> it plays in another app.
   Then stop. Do not start T19.
```
