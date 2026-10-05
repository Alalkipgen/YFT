# P19 — Real thumbnails

**ရည်ရွယ်ချက်:** Download sheet၊ "Found on this page" list နဲ့ Downloads မှာ video ရဲ့ တကယ့် thumbnail ပုံကို ပြမယ်။ ပြီးရင် Preview #2 APK link ပေးမယ်။

**အချက်အလက်:** Phase 11 part 2 · Group 1 (last) · Medium · AI agent အချိန် 5–8 နာရီ · လိုအပ်ချက်: P9

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P19](../FIX_ADD_PLAN.md#p19--real-thumbnails)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P19 — Real thumbnails
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 (finding H9) and task P19 (with its "Read
   first" files), then docs/SESSION_STATE.md. Needs: P9 (DONE or OWNER CHECK).
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop stale daemons with pkill -f "[G]radleDaemon". Starting state before any edit:
   ./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
4. Set P19 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P19 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- RemoteThumbnailLoader (app, no new library) on the shared OkHttp client: HTTPS only, no
  cookies or session, Accept image/*, max 2 MB, two at a time, started after the lookup;
  decoded with inSampleSize to max 480 px wide; memory cache (about 8 MB) and a disk cache
  in cacheDir/thumbnails/ (max 20 MB, oldest removed first).
- YouTube: https://i.ytimg.com/vi/<id>/hqdefault.jpg may load before the lookup ends;
  other sites use the thumbnailUrl the lookup found (MediaAsset.thumbnailUrl).
- Show it in the sheet header (16:9, placeholder until loaded), the found list
  (YftFoundMedia) and running downloads. At download start save a small JPEG (max 320 px,
  about 50 KB) as filesDir/thumbnails/<downloadId>.jpg; Downloads and the Library use it
  until the file's own frame exists; deleted with the record. No Room migration.
- Failures stay silent; logs name the host only.
- Update texts saying YFT never fetches remote images (QuickDownloadScreen comment,
  DESIGN-NOTES section 3, PROJECT_CONTEXT privacy, About if it says so).
- New tag quick-thumbnail; every existing tag stays.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView calls
on the main thread; Kotlin lines within 100 characters; keep every testTag; temporary files and
backups outside the repository; never undo work with git reset --hard, git clean or git stash.

TESTS (a regression test must fail on the old code: FIX_ADD_PLAN 0.3 "Regression proof")
- Loader: HTTP refused, no Cookie header, the size cap stops the read, decode bound, a cache
  hit makes no second request, two at a time.
- The header shows the loaded image (fails on the old code: always the placeholder); the
  saved JPEG is used by Downloads and deleted with the record.

LIVE CHECK (scripts/live-check.sh; report status, host, path, sizes and markers only)
   one public YouTube, Facebook and Vimeo thumbnail: status, host, content type and size only

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update docs/design/DESIGN-NOTES.md (section 3), docs/PROJECT_CONTEXT.md (privacy),
   docs/SUPPORT_MATRIX.md, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the task's Result
   and status in FIX_ADD_PLAN (P19 -> DONE (date), or OWNER CHECK when only the phone check is
   left) and docs/SESSION_STATE.md (last task P19, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P19: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: YouTube and Facebook sheets show the picture; the found list and a running
   download show it.
   Then Preview #2 (FIX_ADD_PLAN P19 "After P19"): run the full validation (FIX_ADD_PLAN 0.3),
   check that the Preview APK run of this commit is green, and send the owner its link
   (Artifacts > yft-preview-apk) with the FIX_ADD_PLAN section 6 Preview #2 list, in
   Burmese. Then continue with P14 unless the owner says stop.
```
