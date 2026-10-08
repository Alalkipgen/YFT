# Agent B — TikTok feed Download (P36) → fresh links instead of HTTP 410 (P37)

**ရည်ရွယ်ချက်:** (P36) TikTok (VPN နဲ့) `tiktok.com/foryou`၊ Following၊ profile၊ video page မှာ ပြနေတဲ့
video ကို Download နှိပ်ရင် "No video on screen…" မပြတော့ဘဲ quality/size တွေနဲ့ sheet ပွင့်မယ်။
Home မှာ TikTok link paste လုပ်တာလည်း ဆက်ရမယ်။ (P37) Adapter မရှိတဲ့ site တွေမှာ page ရဲ့ link တွေ
သက်တမ်းကုန်/မရတော့ (HTTP 410/404/403) ရင် player ကိုယ်တိုင် တောင်းတဲ့ link အသစ်၊ page ကို နောက်ကွယ်မှာ
ထပ်ဖတ်တာ နဲ့ link အသစ် ကိုယ်တိုင်ရှာမယ်၊ Try again က တကယ် ပြန်ဖတ်မယ်၊ မရသေးရင် "Reload page and try
again" ခလုတ်တစ်ချက်နဲ့ page reload + sheet ပြန်ဖွင့်မယ်၊ Details မှာ link ဘယ်ကလာ၊ ဘယ်လောက်ကြာပြီ၊
သက်တမ်းကုန်/မကုန် ပြမယ်။

**အကြောင်းရင်း:** TikTok — For You page မှာ `/video/<id>` link မရှိတော့ (video id က
`xgwrapper-…-<id>` ထဲမှာ)၊ ဖုန်း page ရဲ့ data က `webapp.reflow.video.detail` ထဲ (YFT က
`webapp.video-detail` ပဲ ဖတ်)၊ media အတွက် page answer ရဲ့ cookie လို။ 410 — browser ထဲက Try again က page
ကို ပြန်မဖတ်ဘဲ link ဟောင်းကိုပဲ ပြန်မေး၊ page script ထဲက link ကို player တောင်းတဲ့ link အသစ်ထက် ဦးစားပေး။

**အချက်အလက်:** Phase 14 · **Agent B** · branch `work/phase-14-sites` · P36 (Medium, 3–5 နာရီ) → P37
(Medium, 4–6 နာရီ) · Agent A နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ P36 ပြီးရင် P37 ကို
ကိုယ်တိုင် ဆက်လုပ်ပြီး `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။ Default: TikTok quality တွေကို desktop
page ကနေ ယူ (`TIKTOK_QUALITIES=DESKTOP`)၊ page ကို ၂ ကြိမ်အထိ နောက်ကွယ်မှာ ပြန်ဖတ် (`REREAD=2`)။ Agent
၂ ယောက်ပဲ သုံးလည်း B ကတော့ ဒီ prompt အတိုင်းပါ။ Computer မှာ SSH key မရှိရင် agent က key အသစ်လုပ်ပြီး
public key တစ်ကြောင်း ပြပါမယ် — Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး "SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P36](../FIX_ADD_PLAN.md#p36--tiktok-download-on-the-for-you-feed-and-video-pages),
[P37](../FIX_ADD_PLAN.md#p37--other-sites-fresh-links-instead-of-http-410)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Agent: B — TikTok adapter and feed detection; other sites' links, Try again and reload in the
       browser (one of three agents working at once)
Tasks: P36 — TikTok: Download on the For You feed and video pages; then P37 — Other sites:
       fresh links instead of HTTP 410
Branch: work/phase-14-sites (first start: create it from origin/work/phase-14-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: TIKTOK_QUALITIES=PAGE uses only the phone page, usually one
                           quality; REREAD=0 skips the quiet re-read and keeps only the reload
                           button; STOP_AFTER=P36)

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
- extractor-sites/** (TikTok; other adapters only for a shared helper TikTok needs);
  app/src/main/java/com/alal/yft/detection/**; core-browser/**;
  core-model/src/main/kotlin/com/alal/yft/core/model/media/**; core-media/**; extractor-generic/**;
  app/src/main/java/com/alal/yft/feature/{quickdownload,detectedmedia,home,browser}/**;
  app/src/androidTest/java/com/alal/yft/browser/** and
  app/src/androidTest/assets/{focused-video,browser-detection}/**; the tests beside them;
  docs/SUPPORT_MATRIX.md (TikTok and other-sites rows).
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent B ...", CHANGELOG.md
  "### Phase 14 — Agent B (P36, P37)", docs/TEST_MATRIX.md "### Agent B — P36, P37".
- Never: core-download and core-model/.../download (Agent C), app/.../download/**,
  app/.../feature/{downloads,settings}/**, the manifest (Agent A); ui/format (read only), res/,
  MainActivity, ui/navigation, core-data, docs/FIX_ADD_PLAN.md, docs/prompts/**,
  .github/workflows/**. Agents A and C push their own branches at the same time. Need a change
  outside your files? Do not make it: write "Hand-off to <agent>: <file> — <change> — <why>" in
  your SESSION_STATE section and report it.
- Contracts: use A's DownloadEnqueuer and DownloadPlanFactory and C's DownloadQueue,
  DownloadTask and DownloadProgress as they are. Your own public types (MediaCandidate,
  MediaGroups, BrowserRequestContext) change only by adding, with defaults.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-14-sites origin/work/phase-14-integration
   Later:       git switch work/phase-14-sites && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2 (items 1 and 3), 3 (G6, G7), 4 (R16–R20)
   and tasks P36 and P37 with their "Read first" files, then your section of
   docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent B scope):
   ./gradlew --no-daemon --continue :extractor-sites:test :extractor-generic:test :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P36 IN PROGRESS, the start date and your base commit.

WORK P36 — TikTok (FIX_ADD_PLAN P36, findings R19, R20)
Root cause: on tiktok.com/foryou a card is article[data-e2e="recommend-list-item-container"] ->
section[data-e2e="feed-video"] -> div id "xgwrapper-<n>-<19-digit video id>" -> <video
src="blob:...">, plus the author's /@handle link; there is no /video/ link, so FocusedVideoProbe
finds no link and BrowserViewModel shows NO_FOCUSED_VIDEO_NOTICE. A video page asked with a
phone user agent keeps its data under webapp.reflow.video.detail (no bitrateInfo); the desktop
page has webapp.video-detail with bitrateInfo; TikTokPageParser reads only the desktop key. The
media host answers 206 only with the page answer's cookies (tt_chain_token), and
TikTokExtractor.mediaContext prefers the WebView's cookie header and drops them.
1. Live check first (scripts/live-check.sh; markers, statuses and counts only): the For You page
   with a phone user agent; one public video page with a phone and a desktop user agent; the
   media host with and without the page answer's cookies.
2. FocusedVideoProbe: no video-shaped link beside the focused video -> the id from the nearest
   ancestor id "xgwrapper-<n>-<15–22 digits>", the handle from the card's a[href^="/@"] (card =
   nearest [data-e2e="recommend-list-item-container"], [data-e2e="feed-video"] or article) ->
   https://www.tiktok.com/@<handle>/video/<id>; no handle -> https://www.tiktok.com/@/video/<id>
   (TikTokUrls must accept it). TikTok hosts only; the page's own /video/<id> address first;
   other sites unchanged.
3. TikTokPageParser: webapp.video-detail, else webapp.reflow.video.detail (title, author,
   length, picture, play and download addresses; qualities from bitrateInfo when present).
4. TIKTOK_QUALITIES=DESKTOP (default): a page without bitrateInfo -> ask the same video page once
   with HeadlessIdentity's desktop user agent and use its bitrateInfo and its answer's cookies;
   if that fails, the phone page's address is the one quality. PAGE: no second request.
5. mediaContext: keep the WebView's cookie header, but the page answer's cookies replace
   same-named ones and are added when missing; the media request uses the user agent that
   fetched that page.
6. "No video on screen to download" only when the page really has no video on screen; Home's
   pasted-link lookups keep working.
TESTS P36
- JVM: a For You card fixture (structure only, neutral text, blob video in xgwrapper) -> the
  link (must fail on the old code); no handle -> /@/video/<id>; a redacted reflow page -> title,
  length, play address (must fail on the old code); desktop fixtures still parse; phone page ->
  desktop qualities; cookie merge (a stale same-named WebView cookie loses; must fail on the old
  code).
- Instrumented: FocusedVideoProbeInstrumentedTest with a new feed page in today's layout
  (app/src/androidTest/assets/focused-video/tiktok-foryou.html); the old layout still works.
Then checkpoint "P36: ...", check CI, report (owner check with a VPN: /foryou -> Download ->
qualities -> the file plays; a profile video; Home paste), and go on with P37 without waiting.

WORK P37 — Other sites: fresh links instead of HTTP 410 (FIX_ADD_PLAN P37, findings R16–R18)
Root cause: browser Try again (QuickDownloadViewModel.readPageAgainThenLoad) reuses the store's
page for pages the browser owns, so it asks the same dead addresses; page-script links
(PlayerSetupScanner) outrank the addresses the player itself requested; on some page loads all
page-script links answer 410 to YFT while the site's player plays (cause not proven: expiry,
another IP after a VPN change, a cached page, or links the player never uses).
1. Details: for every attempt add "Link from: page script / player request / page read again",
   "Link age: 12 min", "Link expiry: passed / not passed / none" (numeric expiry-like query
   value such as validto, expires, exp, e, x-expires vs the phone's clock; never the value or the
   address).
2. For the same video (same path without query, or same quality in the group) the player's own
   requested address (newest first) comes before a page-script link; after a 401/403/404/410 of
   a page-script link, the player's address is tried before P29's next video.
3. REREAD=2 (default): no fresh player address -> read the page again in the background (same
   address, Cache-Control: no-cache, the WebView's user agent, the tab's cookies for that site
   from CookieManager, never logged) up to 2 times; find the same video (MediaGroups.refreshed)
   and prepare its new addresses; then P29's next video. A separate mode beside
   HeadlessPageFetcher (Home's cookie-free fetch unchanged). The tab's cookies carry only what
   the user did himself; never tap, skip or fake an age notice; a page answering with a notice or
   a check (no player data) is not used -> step 5.
4. Browser Try again runs step 3.
5. "Reload page and try again" (testTag quick-reload-retry) with the error when nothing fresh
   was found: close the sheet, reload the tab once without cache (main thread; cacheMode
   LOAD_NO_CACHE for that load, then back), reopen the sheet for the same video when it is found
   again (P28's rules, ~15 s), else "The site gave no new link. Play the video for a moment,
   then tap Download again." Home pages keep Home's re-read.
6. Keep P28/P29, adapter sites (adapterSite pages skip this) and Home lookups as they are.
TESTS P37
- JVM: Details lines never contain an address or query value; player request outranks a
  page-script link (must fail on the old code); 410 -> player address before the next video;
  browser Try again re-reads and prepares new addresses (must fail on the old code); at most 2
  re-reads; notice page not used -> reload button; reload flow in the view model (reload asked,
  sheet reopens when the video appears, message after the timeout); REREAD=0.
- Instrumented (app/src/androidTest/.../browser/detection/): a fixture site served by the test:
  its player script names a link that answers 410 while the player requests a working one ->
  the player's is prepared; the second page load gives a new working link -> Try again works.
LIVE CHECK P37: a public page of a site without an adapter and without any age or identity
notice (status, host, markers only); never an age-gated page.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in; never automate a page's age or identity check); adult sites: neutral fixtures, nothing
explicit in the repository, logs or reports; never log or commit cookies, tokens, signed media
or image URLs or keys (fixtures: REDACTED query values); WebView and WebSettings calls on the
main thread (shouldInterceptRequest and @JavascriptInterface on other threads); polite network
use (P37: at most 2 re-reads per failure, one at a time); Kotlin lines within 100 characters;
keep every testTag; app text stays English; temporary files and backups outside the repository
(/data/tmp, /data/bak); never undo work with git reset --hard, git clean or git stash; do not
edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): per task, back up your files to /data/bak/P3x, put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :extractor-sites:test :extractor-generic:test :core-model:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status OWNER
   CHECK; Result with any "Plan adapted"; validation numbers; regression proof; live-check
   markers; CI links; hand-offs); docs/SUPPORT_MATRIX.md rows.
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P3x: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting. (Docs-only pushes start no CI.)
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5, with Level), with the run links and
   the owner check (P36: TikTok with a VPN; P37: the site from Preview #5 that showed HTTP 410
   -> a working video without manual reloads, Try again, "Reload page and try again", a Details
   screenshot if it still fails; Javtiful still works).
After P37: set your SESSION_STATE section to READY FOR MERGE (last code commit, green CI links),
report, and stop.
```
