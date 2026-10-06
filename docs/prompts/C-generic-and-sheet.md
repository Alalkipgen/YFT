# Agent C — Other sites' main video and one sheet everywhere (P24 → P25)

**ရည်ရွယ်ချက်:** Adapter မရှိတဲ့ site တွေမှာ preview clip (29 စက္ကန့်) မဟုတ်ဘဲ တကယ့် video ကို ဖွင့်ပေးမယ်၊
"50 media found" အစား video အရေအတွက် အမှန်ကို ပြမယ်၊ "could not be reached" ရဲ့ အကြောင်းရင်း အမှန်ကို
ပြမယ် (P24)။ Site တိုင်းမှာ sheet တစ်မျိုးတည်း — Audio (M4A, MP3)၊ Video (720p + အောက်တစ်ဆင့်)၊ More
formats၊ row တိုင်းမှာ size နဲ့ ရှင်းလင်းချက် တစ်ကြောင်း — ဖြစ်အောင် လုပ်မယ် (P25)။

**အချက်အလက်:** Phase 12 · **Agent C** · branch `work/phase-12-generic-sheet` · P24 (Hard, 6–10 နာရီ)
→ P25 (Medium–Hard, 5–8 နာရီ) · Agent A နဲ့ B နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ ပြီးရင် `READY FOR MERGE`
လို့ပြောပြီး ရပ်ပါမယ်။ Row နာမည်ကို Snaptube အတိုင်း ("Fast", "High quality") လိုချင်ရင်
`OWNER ANSWERS: SHEET_NAMES=SNAPTUBE` လို့ ပြောင်းပါ။ Agent ၂ ယောက်ပဲ သုံးရင် Agent A ရဲ့ chat ထဲမှာ
`BRANCH_OVERRIDE: work/phase-12-download-fix` နဲ့ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P24](../FIX_ADD_PLAN.md#p24--other-sites-main-video),
[P25](../FIX_ADD_PLAN.md#p25--one-sheet-for-every-site)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3).

Repository: https://github.com/Alalkipgen/YFT
Agent: C — other sites' detection, the download sheet, Home and browser flow (one of three
       agents working at once)
Tasks: P24 — Other sites: main video; then P25 — One sheet for every site
Branch: work/phase-12-generic-sheet (first start: create it from origin/work/phase-12-integration)
BRANCH_OVERRIDE: none     (two-agent mode: work/phase-12-download-fix, after Agent A's P21)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: SHEET_NAMES=SNAPTUBE, STOP_AFTER=P24)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-C (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-C
  and, when /data/YFT exists with push access, copy it:
    git -C /data/YFT-C remote set-url origin "$(git -C /data/YFT remote get-url origin)"
    git -C /data/YFT-C config core.sshCommand "$(git -C /data/YFT config core.sshCommand)"
  Otherwise push with the access your environment provides (deploy key or token); never print
  it. A failed push: stop and tell the owner (a local commit is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-browser/**; core-media/**; extractor-generic/**;
  core-model/src/main/kotlin/com/alal/yft/core/model/media/** and .../settings/**;
  app/src/main/java/com/alal/yft/feature/quickdownload/**, .../feature/browser/**,
  .../feature/home/**, .../feature/detectedmedia/**; app/src/main/java/com/alal/yft/ui/**;
  the tests beside them; docs/design/DESIGN-NOTES.md.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent C ...", CHANGELOG.md
  "### Phase 12 — Agent C (P24, P25)", docs/TEST_MATRIX.md "### Agent C — P24, P25".
- Never: extractor-sites, app/.../detection/** (Agent B), core-download, app/.../download/**,
  app/.../feature/downloads/** (Agent A), docs/FIX_ADD_PLAN.md, docs/prompts/**,
  .github/workflows/**, res/ and the manifest. Agents A and B push their own branches at the
  same time. Need a change outside your files? Do not make it: write "Hand-off to <agent>:
  <file> — <change> — <why>" in your SESSION_STATE section and report it.
- Contracts: MediaCandidate, MediaAsset, MediaVariant, MediaGroups, AudioFromVideo and
  BrowserRequestContext are built or read by Agent B's extractors and Agent A's plan factory:
  change them only by adding, with defaults; never rename or remove. You show the data B's
  adapters give (height, codecs, contentLengthBytes or bitrate + length); never parse site data
  in the sheet. Use A's DownloadEnqueuer/DownloadPlanFactory as they are.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-12-generic-sheet origin/work/phase-12-integration
   Later:       git switch work/phase-12-generic-sheet && git pull --ff-only
   (With BRANCH_OVERRIDE: switch to that branch and pull it instead.)
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2, 3 (E13), 4 (R5, R6) and tasks P24 and
   P25 with their "Read first" files, docs/design/DESIGN-NOTES.md (download sheet), then your
   section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started (never another agent's); one Gradle command at a time.
   Starting state before any edit (Agent C scope):
   ./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug
4. In your SESSION_STATE section: P24 IN PROGRESS, the start date and your base commit.

WORK P24 — Other sites: main video (FIX_ADD_PLAN P24, finding R5)
Root cause: Home reads the page without a player (HtmlMediaScanner) and lists every preview
and ad MP4; without an adapter MediaGroups.pageVideos keeps every group -> "50 media found".
In the browser openMainVideo -> MediaGroups.mainVideo(videos, playingUrl): the page plays
through MSE (blob: source), so PlayingVideoProbe's address never matches, and the fallback
"largest stated size, then height, then length" picks a 29 s preview with a known size over the
HLS main video without one. "The media could not be reached" is unproven:
QuickDownloadViewModel.resolveSafely turns ANY exception into VariantResolutionFailure.NETWORK.
1. PlayingVideoProbe also reports the playing element's length, picture size, muted/looping/
   autoplay state and main document vs frame; with a blob: source mainVideo matches by length
   (+-2 s; HLS/DASH from the playlist, MP4 from the metadata probe), else the manifest the page
   loaded when that player started.
2. Ranking without a match: a known long length first, then height, then size. Demote
   previews: under 60 s while a longer video exists, muted looping autoplay, inside links to
   other pages or thumbnail boxes, preview addresses (preview, thumb, teaser, sprite),
   third-party ad frames.
3. Home (no player): JSON-LD VideoObject (contentUrl, embedUrl, duration), og:video /
   og:video:url / twitter:player:stream and a manifest (.m3u8, .mpd) in the page's own scripts
   mark the main video; MP4s from thumbnail attributes (data-preview*, data-mediabook, data-src
   on thumbnails) are previews.
4. Found count: Home's "N media found" and the browser's list count videos (a main video with
   its qualities is one); previews go under "Other videos on this page (N)"
   (quick-other-videos); Home with one main video opens its sheet at once (P16 flow).
5. Honest failure: resolveSafely maps exceptions by type (IOException -> NETWORK, parser ->
   MALFORMED_MANIFEST, unsupported or blob: address -> INVALID_URL, HTTP statuses as today; 403
   -> "The site refused this video (HTTP 403)"); the sheet's Details name the step, host and
   status. Preparing an HLS master found on a page replays its Referer and Origin (and, for
   browser candidates, the site's cookies as today).
TESTS P24 (fixtures copy the owner's case; no signed values)
- One HLS master (several variants, >10 min) played through MSE + 40 preview MP4s (5-30 s,
  muted loops in thumbnail links) + one ad frame -> main video = the HLS video in Home and in
  the browser; count 1 + "Other videos" (must fail on the old code: the 29 s preview wins, 50
  media found).
- The probe's length matches a blob: player; a parser exception is not "could not be reached"
  (must fail on the old code); the master request carries Referer and Origin.
LIVE CHECK P24: one public page of the same kind (HLS player + several short preview MP4s, no
sign-in, no age or identity check) if reachable from the sandbox; never automate an age or
identity check. Otherwise fixtures + the owner's phone. Report hosts, counts, lengths, heights.

WORK P25 — One sheet for every site (FIX_ADD_PLAN P25, finding R6)
The layout exists (QuickDownloadChoices.compact: 2 Audio + 2 Video rows, More formats, Details,
pinned Download); the data and names differ per site.
1. Names by height everywhere: "2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD",
   "480p", "360p", "240p", "144p"; an HD/SD file without a height keeps "HD"/"SD" until its
   height is known, then is renamed in place without moving (P11). Audio short view: "M4A" (the
   original sound, copied — fastest) and "MP3 · 128 kbps"; MP3 320/192 under More formats.
2. Descriptions on a second line (testTag quick-row-description): 144p "Low quality, smallest
   file"; 240p "Low quality for quick play"; 360p/480p "Normal quality for quick play"; 720p
   "Clear view and quick play"; 1080p "High details for full screen play"; 2K/4K "High details
   for big screen play"; M4A "Original sound, fastest"; MP3 "Plays everywhere".
   SHEET_NAMES=SNAPTUBE: titles "Fast" / "High quality" / "Classic MP3" with the quality beside.
3. Sizes: stated size, else bitrate x length as "~54 MB", else "Size unknown"; a row never
   disappears for lack of a size.
4. Audio on every source with sound: AudioFromVideo.canExtract also accepts an MP4 with sound
   whose codecs are unknown; check (read only) that core-download's AudioTrackExtractor fails a
   file without AAC with a clear message; if not, write a hand-off to Agent A.
5. The same order and short view on every site and entry (Home, browser, feed, found list);
   Home's other-site path opens the sheet (P24 step 4). Keep every existing testTag.
TESTS P25
- Choices table for four fixtures (YouTube full ladder, Facebook DASH + HD/SD, Facebook HD/SD
  only, other site HLS + MP4): the same sections, names and order; descriptions; "~" estimates.
- Audio from an MP4 with unknown codecs (must fail on the old code: no Audio section).
- Compose: descriptions show and Download stays visible without scrolling.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView and
WebSettings calls on the main thread (shouldInterceptRequest and @JavascriptInterface on other
threads); Kotlin lines within 100 characters; keep every testTag; app text stays English;
temporary files and backups outside the repository (/data/tmp, /data/bak); never undo work with
git reset --hard, git clean or git stash.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P24 (or P25), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result.

VALIDATE (after each task; report only what you ran)
   ./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status DONE
   (date) or OWNER CHECK; Result with any "Plan adapted"; validation numbers; regression proof;
   live check; CI links; hand-offs); docs/design/DESIGN-NOTES.md (found list, download sheet).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P24: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the run links and the task's
   owner check (P24: the site from Preview #3 -> Home and the browser open the main video with
   its real length and "Other videos on this page", the download finishes, else a Details
   screenshot; P25: YouTube, Facebook and that site show the same sheet with descriptions and
   sizes). Then continue P24 -> P25 without waiting.
After P25: set your SESSION_STATE section to READY FOR MERGE (last commit, green CI links),
report, and stop. Agent A merges (P26).
```
