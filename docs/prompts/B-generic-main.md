# Agent B — Other sites: the page's video, not the ad (P28 → P29)

**ရည်ရွယ်ချက်:** Adapter မရှိတဲ့ video site (Preview #4 က site လို) မှာ ကြော်ငြာ (pre-roll ad, 0:30)
ပြနေတုန်း Download နှိပ်ရင်တောင် ကြော်ငြာ မဟုတ်ဘဲ page ရဲ့ video အမှန် (ဥပမာ 16:24၊ 480p/720p) ကို
title၊ ပုံ၊ အရှည်နဲ့ ဖွင့်ပေးမယ်၊ ကြော်ငြာက "Other videos on this page" အောက်မှာပဲ (P28)။ ဖိုင်တစ်ခု
မရတော့ရင် (HTTP 410/404/403) နောက် video ကို ကိုယ်တိုင် ပြင်ပေးပြီး "Try again" က လိပ်စာ အသစ်နဲ့
ပြန်စမယ် (P29)။

**အချက်အလက်:** Phase 13 · **Agent B** · branch `work/phase-13-generic-main` · P28 (Hard, 6–10 နာရီ)
→ P29 (Medium, 2–4 နာရီ) · Agent A နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ ပြီးရင် `READY FOR MERGE`
လို့ပြောပြီး ရပ်ပါမယ်။ Default က `GENERIC=A` (page က ပြောတဲ့ အရှည်/title/ပုံ ကို ယုံ၊ player setup
တွေ ဖတ်၊ ကြော်ငြာ server စာရင်း၊ ၆ စက္ကန့်အထိ စောင့်)။ ကြော်ငြာ ပြီးတဲ့အထိ စောင့်တဲ့ နည်း လိုချင်ရင်
`OWNER ANSWERS: GENERIC=B` ပါ (နှေးပါတယ်)။ Agent ၂ ယောက်ပဲ သုံးလည်း B ကတော့ ဒီ prompt အတိုင်းပါ။
Computer မှာ SSH key မရှိရင် agent က key အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — Deploy keys မှာ
"Allow write access" နဲ့ ထည့်ပြီး "SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P28](../FIX_ADD_PLAN.md#p28--other-sites-the-pages-video-not-the-ad-before-it),
[P29](../FIX_ADD_PLAN.md#p29--other-sites-the-next-video-when-one-fails)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Agent: B — other sites' detection, page facts and the sheet's choice of video (one of three
       agents working at once)
Tasks: P28 — Other sites: the page's video, not the ad before it; then P29 — the next video
       when one fails
Branch: work/phase-13-generic-main (first start: create it from origin/work/phase-13-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: GENERIC=B — instead of P28 step 5's 6 s wait, keep the sheet
                           on "Waiting for the ad to end…" until the player's length matches the
                           page or 60 s pass; STOP_AFTER=P28)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-B (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-B
- Push with SSH. If /data/.ssh/id_ed25519 is missing, make a NEW key (never search the
  computer for old keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/id_ed25519 -C "yft-b-<date>"
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of /data/.ssh/id_ed25519.pub and wait until he says he added it
  (GitHub > YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i /data/.ssh/id_ed25519 -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-model/src/main/kotlin/com/alal/yft/core/model/media/**;
  core-browser/src/main/java/com/alal/yft/core/browser/{detection,session}/**; core-media/**;
  extractor-generic/**; app/src/main/java/com/alal/yft/feature/{quickdownload,home,
  detectedmedia}/**; in app/.../feature/browser/ only BrowserViewModel.kt, BrowserUiState.kt and
  BrowserDownloadFab.kt; app/src/main/java/com/alal/yft/ui/format/**;
  app/src/androidTest/java/com/alal/yft/browser/detection/** (new);
  core-browser/src/test/resources/fixtures/p28-*; the tests beside them;
  docs/design/DESIGN-NOTES.md, docs/SUPPORT_MATRIX.md.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent B ...", CHANGELOG.md
  "### Phase 13 — Agent B (P28, P29)", docs/TEST_MATRIX.md "### Agent B — P28, P29".
- Never: core-browser/.../{webview,policy}/**, core-data, core-model/.../settings/**, the other
  app/.../feature/browser/** files and feature/settings (Agent C); core-download,
  app/.../download/**, app/.../feature/downloads/** (Agent A); extractor-sites;
  docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**, res/ and the manifest. Agents A
  and C push their own branches at the same time. Need a change outside your files? Do not
  make it: write "Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section
  and report it.
- Contracts: BrowserViewModel's public functions, BrowserUiState, MediaCandidate, MediaGroups
  and BrowserRequestContext are used by C's BrowserScreen and A's plan factory: change them only
  by adding, with defaults. You use C's BrowserObservationSink, SecureBrowserWebViewClient's
  calls and BrowserSearch.webUrl(words) as they are. The media ad list in
  BrowserObservationMapper is yours; C writes a separate navigation list in policy/.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-13-generic-main origin/work/phase-13-integration
   Later:       git switch work/phase-13-generic-main && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2, 3 (F1), 4 (R7–R11) and tasks P28 and P29
   with their "Read first" files, docs/design/DESIGN-NOTES.md (download sheet, found list),
   then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent B scope):
   ./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P28 IN PROGRESS, the start date and your base commit.

WORK P28 — the page's video, not the ad (FIX_ADD_PLAN P28, findings R7, R8, R9, R11)
Owner's case (Preview #4): a free video site without an adapter; during the pre-roll the sheet
opened the ad (0:30, 1080p MP4) as the main video, the real video (16:24, 720p HLS) only under
"Other videos"; Snaptube showed the page's title, picture, Fast 480p and High 720p.
Root cause: MediaGroups.mainVideo takes the playing element's address first, so the playing ad
wins; the page's stated length (JSON-LD VideoObject.duration naming an embed page, not a file)
is dropped, and the DOM probe reads no meta or JSON-LD; the ad list lacks the ad networks of
free video sites and VAST/VMAP is unused; no title or picture reaches the sheet.
1. Page facts: new PageVideoFacts (core-model media, all optional): stated length (JSON-LD
   duration even without a media URL, og:video:duration, video:duration, itemprop=duration),
   title (og:title, JSON-LD name, else <title> without the site name), picture (og:image,
   JSON-LD thumbnailUrl). Home reads them in HtmlMediaScanner; the browser's DOM probe script
   returns them (meta and JSON-LD only, never page text); PageCandidateStore keeps them.
2. Common player setups (player names, never site names): JW Player setup(file|sources|
   playlist), Video.js data-setup and <source> lists, Flowplayer / Clappr / Plyr source(s),
   KVS-style flashvars (video_url, video_alt_url and their _text labels), generic quality
   lists (objects with a media URL and quality/label/res/height, incl. mediaDefinitions and
   sources). Each URL -> a MAIN candidate with its height. Read escaped JSON (\/); do not chase
   URLs built by obfuscated script (the browser's requests find those).
3. mainVideo, new order: (a) length matches the stated length (+-2 s, +-1% over 10 min);
   (b) MAIN (page or player setup) before the rest; (c) the playing element only when its
   length is unknown or matches — a 0:30 element on a 16:24 page is a pre-roll; (d) P24's
   rules. looksLikePreview also treats as an ad a video shorter than half the stated length
   (or under 60 s when the page states >= 2 min). Adapter videos and P24's behaviour without
   page facts stay as they are.
4. Ads: extend the media ad list only with hosts that the live check or the networks' public
   documentation confirm (candidates: TrafficJunky, adtng, ExoClick/exosrv, JuicyAds,
   TrafficStars, tsyndicate, realsrv, magsrv, Adsterra). A media file first seen right after a
   VAST/VMAP request (address words vast/vmap or an XML answer) in the same frame is an ad.
5. Waiting (GENERIC=A): page states >= 2 min and every known video is far shorter -> the sheet
   opens at once with the page's title and picture and "Finding the page's video…" for up to
   6 s while requests and manifests keep coming, then fills in by itself; if nothing comes,
   the best other video with "This may be an ad. Play the video for a moment, or see Other
   videos." (testTag quick-maybe-ad). GENERIC=B: see OWNER ANSWERS above.
6. Header: the sheet's title and picture come from the page facts when the video has none.
TESTS P28 (fixtures neutral: plain text, blank images, no adult words or pictures)
- core-browser/src/test/resources/fixtures/p28-preroll.html: JSON-LD duration PT16M24S with an
  embed URL, og:title, og:image, a 30 s ad MP4 in the player, the HLS master loaded later ->
  main = 16:24 HLS with title and picture; the ad under Other videos (must fail on the old code).
- Player setups (JW Player, Video.js, KVS flashvars, quality list) -> MAIN with heights;
  escaped JSON; a page without them keeps P24's result.
- mainVideo table: stated length beats the playing ad; without a stated length the playing
  element still wins (P24 guard); adapter videos untouched.
- Waiting: only an ad -> "Finding the page's video…" -> real video arrives -> the sheet switches
  without a tap; nothing in 6 s -> the ad with quick-maybe-ad.
- Instrumented (app/src/androidTest/.../browser/detection/, CI emulator): a local fixture page
  plays a 30 s ad, then the main video; Download during the ad picks the main video.
LIVE CHECK P28: one public page of the owner's kind (a free video site with a pre-roll) only if
the sandbox reaches it without an age or identity gate — never click or automate one; report
hosts, lengths, heights, counts and which host served the ad, nothing else. Otherwise fixtures
and the owner's phone.

WORK P29 — the next video when one fails (FIX_ADD_PLAN P29, finding R10)
1. Preparing the main video of a page without an adapter fails with 403, 404, 410 or
   INVALID_URL and the page has other non-ad videos -> prepare the next one in P28's order once,
   by itself; line "The first file is gone — showing the next video" (testTag
   quick-next-video); Details lists both attempts (step, host, status).
2. "Try again" re-reads the page's current candidates (newest addresses from the page store, or
   a new read of the page for Home) instead of the same dead address.
3. Every entry (Home, browser, found list, Other videos) uses the page facts' title and picture
   for the header when the video has none.
TESTS P29: first video 410 -> the second is prepared and shown (must fail on the old code); an ad
is never the fallback; Try again uses the store's newest address; Details lists both attempts.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in; never automate a page's age or identity check); never log or commit cookies, tokens,
signed media or image URLs or keys; WebView and WebSettings calls on the main thread
(shouldInterceptRequest and @JavascriptInterface on other threads); Kotlin lines within 100
characters; keep every testTag; app text stays English; temporary files and backups outside
the repository (/data/tmp, /data/bak); never undo work with git reset --hard, git clean or
git stash; do not edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P28 (or P29), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result.

VALIDATE (after each task; report only what you ran)
   ./gradlew --no-daemon --continue :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :extractor-generic:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status OWNER
   CHECK; Result with any "Plan adapted"; validation numbers; regression proof; live check; CI
   links; hand-offs); docs/design/DESIGN-NOTES.md (sheet header, "Finding the page's video…");
   docs/SUPPORT_MATRIX.md (other sites' row).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P28: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the run links and the task's
   owner check (P28: the site from Preview #4, Download while the ad plays -> the page's title,
   picture and length with 480p/720p, the ad only under Other videos; P29: the page that showed
   "HTTP 410" opens a working video). Then continue P28 -> P29 without waiting.
After P29: set your SESSION_STATE section to READY FOR MERGE (last commit, green CI links),
report, and stop. Agent A merges (P33).
```
