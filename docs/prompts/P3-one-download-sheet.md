# P3 — One download sheet, Snaptube style

**ရည်ရွယ်ချက်:** Snaptube လို sheet တစ်ခုတည်း — Music (M4A, MP3) နဲ့ Video (Fast/High) ကို resolution + size နဲ့ ပြမယ်။ More formats ကို sheet ထဲမှာပဲ ဖွင့်မယ်၊ Audio အမြဲရွေးလို့ရမယ်။ Home View၊ Preview၊ Download ခလုတ် အားလုံးက ဒီ sheet ကို ဖွင့်မယ်။

**အချက်အလက်:** Phase 11 · Hard · AI agent အချိန် 10–14 နာရီ · လိုအပ်ချက်: P1

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P3](../FIX_ADD_PLAN.md#p3--one-download-sheet-snaptube-style)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P3 — One download sheet, Snaptube style
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P3 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug
4. Set P3 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P3 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Group a page's candidates of the same video (site video ID, or same page and duration)
  into one item; the found list shows one row per video.
- One sheet (the "Video you copied" sheet grows into it): thumbnail, title, source; Music rows
  (M4A bitrate + size; MP3 128/192/320, estimated size); Video rows (Fast ~480p, High ~720p)
  with resolution, frame rate, size; More formats expands inside the sheet with every quality
  and audio option (chips "Video + audio", "Slow" for conversions); one Download button.
- Every row shows a quality: probe a video variant without height (MP4 tkhd or the DASH
  manifest); never use the page title as a quality label.
- Audio whenever the video has sound: audio-only variant, else extract the MP4's AAC track
  into M4A (MP3 from it).
- Home "View", Preview in the found list, the browser Download button and shared links open
  this sheet; the old Download as screen only from More formats > Details.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- Grouping (Facebook's three items -> one), labels (no title fallback), audio for an
  MP4-only video, More formats inside the sheet, navigation from View/Preview/FAB.
- Update the existing quick sheet and preview tests.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-model:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md, docs/TEST_MATRIX.md, docs/design/DESIGN-NOTES.md, CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P3 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P3, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P3: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: Facebook and YouTube: View or Download -> one sheet with Music/Video rows, real resolutions and sizes; More formats inside it; Audio works.
   Then continue with P4 without waiting (owner, 2026-10-04).
```
