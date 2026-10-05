# P12 — One sheet and one lookup on site pages

**ရည်ရွယ်ချက်:** YouTube/Facebook/TikTok/Vimeo video page ပေါ်မှာ Download ခလုတ်က အဲဒီ video ရဲ့ sheet ကိုပဲ ဖွင့်မယ် ("Found on this page 4" list မဖွင့်တော့)၊ video တစ်ခုကို lookup တစ်ကြိမ်ပဲ လုပ်မယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 1 · Medium–Hard · AI agent အချိန် 5–8 နာရီ · လိုအပ်ချက်: P9

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P12](../FIX_ADD_PLAN.md#p12--one-sheet-and-one-lookup-on-site-pages)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P12 — One sheet and one lookup on site pages
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H6) and task P12 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P9 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-model:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P12 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P12 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- On a site video page (watch, shorts, reel, video, post) the button always means "this
  video": a spinner while the page's lookup runs; a tap opens the sheet in its loading
  state (quick-loading) and fills it when that same lookup ends. No second lookup.
- A feed's focused link with the video ID of a running or finished lookup joins it.
- Generic metadata probes wait while a site lookup runs and run only if it found nothing.
- MediaGroups.pageVideos never falls back to every group on an adapter site. Other pages
  with several videos: the main video's sheet (playing, else largest) plus a row "Other
  videos on this page (N)" that opens the list.
- A failed site lookup shows its message with Retry (P10) in the sheet, not the list.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Action table (site video page with 0, 1 or 4 found -> this video's sheet; generic page
  with 4 -> main sheet + others row).
- One adapter call for two taps during a lookup (fails on the old code: two calls);
  probes wait; pageVideos on an adapter site; failure -> sheet with Retry.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-model:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (browser), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased],
   the task's Result and status in FIX_ADD_PLAN (P12 -> DONE (date), or OWNER CHECK when only
   the phone check is left) and docs/SESSION_STATE.md (last task P12, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P12: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: a YouTube watch page and a Facebook reel in the browser -> Download -> that
   video's sheet; never "Found on this page 4".
   Then continue with P13 without waiting (FIX_ADD_PLAN section 3, E7).
```
