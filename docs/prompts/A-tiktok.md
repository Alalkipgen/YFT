# Agent A — TikTok: read every answer and say why (P39) → TikTok from its own page, like YouTube (P40)

**ရည်ရွယ်ချက်:** YFT browser (VPN နဲ့) နဲ့ Home မှာ TikTok video တိုင်း Download ရအောင် — "TikTok changed
its page format" မပြတော့ဘဲ quality (1080p/720p/540p) + size အမှန် နဲ့ sheet ပွင့်မယ်။ (P39) TikTok
ပေးတဲ့ page ပုံစံ အားလုံး ဖတ်၊ ဖုန်း/desktop agent နှစ်မျိုးလုံး စမ်း၊ file တကယ်ရမရ စစ်ပြီးမှ ပြ၊ watermark
file ဖျောက်၊ မရရင် Details မှာ ဘာကြောင့်လဲ အတိအကျပြ၊ browser မှာ player ဖွင့်နေတဲ့ file ကို အနည်းဆုံး
ပေး (dead end မရှိ)။ (P40) YouTube လိုပဲ TikTok ရဲ့ ကိုယ်ပိုင် page/script ကနေ data ယူ — ကြည့်နေတဲ့ tab
ထဲက data၊ For You ရဲ့ API answer (TikTok player ကိုယ်တိုင်ရတဲ့ data)၊ Home link အတွက် နောက်ကွယ်က hidden
TikTok page — ဒါကြောင့် browser မှာ ဖွင့်လို့ရတဲ့ video ဆို download ရမယ်။

**အကြောင်းရင်း:** adapter fail တိုင်း (header error၊ redirect၊ ဘယ် exception မဆို) "changed its page format"
တစ်ခုတည်းပြ၊ Details မရှိ။ ဖုန်း page ဖတ်မရရင် desktop page မစမ်း၊ desktop agent မှာ "YFT" စာလုံးပါ။
TikTok media file က page answer ရဲ့ cookie (token) + Referer မပါရင် 403 ("Size unknown")။ Browser မှာ adapter
fail ရင် page ရဲ့ file တွေကို မပြဘဲ error ပဲပြ။ VPN country ရဲ့ TikTok answer ကို sandbox (US) က မမြင်ရလို့
TikTok page ကိုယ်တိုင်ရဲ့ data ကို ယူတာ အသေချာဆုံး။

**အချက်အလက်:** Phase 15 · **Agent A** · branch `work/phase-15-tiktok` · P39 (Medium, 5–7 နာရီ) → P40
(**Hard**, 8–12 နာရီ) · Agent B နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ P39 ပြီးရင် P40 ကို
ကိုယ်တိုင် ဆက်လုပ်ပြီး `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။ P39 ပြီးတာနဲ့ Agent A ရဲ့ Preview APK ကို
ဖုန်း (VPN) မှာ စမ်းကြည့်ပြီး Details screenshot ပို့ပေးရင် P40 ပိုမြန်မယ်။ Default: Chrome agent
(`TT_AGENT=CHROME`)၊ Home မှာ browser ရဲ့ TikTok cookie သုံး (`TT_HOME_COOKIES=ON`)၊ watermark file ဖျောက်
(`TT_WATERMARK=HIDE`)၊ hidden TikTok page (`TT_HIDDEN_PAGE=ON`)။ Computer မှာ SSH key မရှိရင် agent က key
အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး "SSH Done"
လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P39](../FIX_ADD_PLAN.md#p39--tiktok-read-every-answer-tiktok-gives-and-say-why-when-it-fails),
[P40](../FIX_ADD_PLAN.md#p40--tiktok-from-tiktoks-own-page-like-youtube)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Agent: A — TikTok (one of three agents working at once)
Tasks: P39 — TikTok: read every answer TikTok gives, and say why when it fails
       P40 — TikTok from TikTok's own page, like YouTube
Branch: work/phase-15-tiktok (first start: create it from origin/work/phase-15-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: TT_AGENT=HEADLESS, TT_HOME_COOKIES=OFF, TT_WATERMARK=SHOW,
                           TT_HIDDEN_PAGE=OFF)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.
The owner's order (2026-10-09): make TikTok work like YouTube, with any working technique for
public videos (ADR-006): Chrome agents, TikTok's own page and scripts in a WebView, the cookies
YFT's own browser already has, the player's own requests. Do not stop at the first obstacle.
Still out: DRM, private or login-only posts, age gates; adapters never sign in; a check that
needs a person (a puzzle) is never automated.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-A (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-A
- Push with SSH, using the key whose path is in /data/.ssh/CURRENT_KEY (it pushed on
  2026-10-09). If that file or key is missing, or a push says "Permission denied", make a NEW
  key (never search the computer for other keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/yft_a_<date> -C "yft-a-<date>"
    echo /data/.ssh/yft_a_<date> > /data/.ssh/CURRENT_KEY
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of the .pub file and wait until he says he added it (GitHub >
  YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i $(cat /data/.ssh/CURRENT_KEY) -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- extractor-sites/** (TikTok; other adapters only for a shared helper TikTok needs);
  extractor-api/** (additions only); app/src/main/java/com/alal/yft/detection/**;
  core-browser/** except core-browser/.../detection/{VastAdTracker,BrowserObservationMapper,
  PageFactsReader,PlayerSetupScanner,HtmlMediaScanner}.kt and new Ad* files (Agent C);
  core-media/**; app/src/main/java/com/alal/yft/feature/{browser,home,detectedmedia}/**; in
  app/.../feature/quickdownload/QuickDownloadViewModel.kt ONLY the function lookupState;
  app/src/androidTest/java/com/alal/yft/tiktok/** and app/src/androidTest/assets/tiktok/** (new),
  app/src/androidTest/java/com/alal/yft/browser/FocusedVideoProbeInstrumentedTest.kt and
  app/src/androidTest/assets/focused-video/**; gradle/libs.versions.toml, app/build.gradle.kts
  and core-browser/build.gradle.kts only for the androidx.webkit lines (P40); docs
  SUPPORT_MATRIX.md; the tests beside your files.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent A ...", CHANGELOG.md
  "### Phase 15 — Agent A (P39, P40)", docs/TEST_MATRIX.md "### Agent A — P39, P40".
- Never: core-download, core-model/.../download, app/.../download, app/.../feature/{downloads,
  library} (Agent B); core-model/.../media, extractor-generic, the rest of feature/quickdownload,
  C's core-browser files, androidTest browser/** other than the probe test (Agent C); the
  manifest, res/, other Gradle lines, docs/FIX_ADD_PLAN.md, docs/prompts/**,
  .github/workflows/**. Agents B and C push their own branches at the same time. Need a change
  outside your files? Do not make it: write "Hand-off to <agent>: <file> — <change> — <why>" in
  your SESSION_STATE section and report it.
- Contracts: SiteExtractionRequest, SiteExtractionResult, SiteExtractionFailure and the
  adapter outcome change only by adding, with defaults (new enum values at the end). You add
  PageVideoLookup.details (default empty) and SiteExtractionRequest.pageData (default null).
  The media model in core-model/.../media (MediaCandidate, MediaGroups, FreshLinks,
  PageMediaRole, BrowserRequestContext) is Agent C's: use it as it is.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-15-tiktok origin/work/phase-15-integration
   Later:       git switch work/phase-15-tiktok && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2 (items 1–4), 3 (G2–G5), 4 (R25–R31) and
   tasks P39 and P40 with their "Read first" files, then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent A scope):
   ./gradlew --no-daemon --continue :extractor-sites:test :extractor-api:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P39 IN PROGRESS, the start date and your base commit.

WORK P39 (FIX_ADD_PLAN P39, findings R25–R31) — do the plan's 8 steps in order:
1. Details first: every TikTok lookup keeps Details lines (page agent, HTTP status, KB, landed
   on video/home/challenge/login/other page; data key; JSON read or "error <Class> at char N";
   post id matches/other/missing; qualities; play/download address; file checks; "error:
   <ExceptionClass>"). Hosts only, never addresses, query values or cookies. They fill
   SiteAdapterOutcome.Failed.details and a new PageVideoLookup.details, which lookupState
   shows as the sheet's Details.
2. Honest reasons: a link landing on TikTok's home page or a page without a post id ->
   PRIVATE_OR_UNAVAILABLE "This TikTok link does not open a video. It may be removed or private
   — open it in YFT's browser to check."; a challenge page -> BOT_CHECK; RESPONSE_CHANGED only
   for an unknown data shape, text "<Site>'s page could not be read. Tap Details to see why, or
   Try again." (no "Falling back to generic detection" anywhere).
3. Safe requests in OkHttpExtractorClient: bad cookie pairs or headers left out (count in
   Details), any thrown error -> a failure with its class, a per-lookup cookie jar across
   redirects and the desktop retry, too many redirects -> HTTP_STATUS.
4. Phone page, then the desktop page whenever the phone page fails for a non-final reason or
   lists fewer than 2 qualities; best answer wins. TT_AGENT=CHROME: phone = the WebView's own
   agent; desktop = desktop Chrome with the WebView's Chrome version, never the "YFT" word.
5. Every data shape: script found by its id attribute; any __DEFAULT_SCOPE__ key with
   itemInfo.itemStruct; SIGI_STATE; one lenient JSON read; another post's id -> next shape.
6. Qualities that really download: rows from every bitrateInfo address, playAddr and the
   cookie-free www.tiktok.com/aweme/v1/play/ address; each checked with Range bytes=0-0, the
   answer's cookies and Referer https://www.tiktok.com/ (at most 8 checks, 3 at a time, 5 s
   each): 206 -> exact size; 401/403/404/410 -> next address; none -> quality left out. Labels
   1080p/720p/540p, "H.265" mark, H.264 first at the same height, duplicates once.
   TT_WATERMARK=HIDE: downloadAddr only when nothing else answers, "With TikTok watermark".
7. Reproduce the owner's "Download · 1.4 MB" row (a TikTok-shaped site video through
   DefaultVariantResolver in a JVM test) and name the exception; then resolve() catches every
   exception except CancellationException and returns a failure with its step and class.
8. Browser never dead-ends: FocusedVideoProbe also returns the on-screen <video>'s https
   currentSrc (new field, default null); when the TikTok adapter fails on a page whose player
   loaded TikTok media files, that file (else the newest TikTok media request) becomes a
   PageMediaRole.MAIN row with the request's cookies and Referer, and the banner says "TikTok's
   page could not be read — showing the file its player is playing." Other adapters keep P12.
TESTS P39 (JVM): fixtures shaped like the 2026-10-09 live answers (phone
webapp.reflow.video.detail with only playAddr; desktop webapp.video-detail with 4 gears; host +
path kept, query values REDACTED); the script id named earlier in the HTML, an unreadable phone
page -> desktop tried, a short link landing on "/" -> the new text, a non-ASCII cookie -> the
request still goes out, BrowserViewModel failure + TikTok media -> a MAIN row, an unexpected
resolver exception -> a failure with its step (each must fail on the old code); unknown
__DEFAULT_SCOPE__ key; entity-encoded JSON; challenge page; another id; file checks (206 size,
403 next, all 403 dropped, <= 8 checks); watermark rules; the cookie jar across a redirect
(MockWebServer); lookupState shows details; no Details line contains http, ?, = or a cookie.
LIVE CHECK P39 (sandbox, polite: one public video page, one vt.tiktok.com link, one dead short
link): run the adapter for real (both agents) and report statuses, page kinds, data keys,
qualities with exact sizes and file-check statuses (hosts only); the dead link -> the new text.
Checkpoint P39 (FINISH steps 1–3), report P39 in Burmese, then go on with P40 at once.

WORK P40 (FIX_ADD_PLAN P40) — the same idea as YouTube's WebViewBotGuardEngine:
1. SiteExtractionRequest.pageData: SitePageData? = null (JSON of one post, <= 64 KB, source
   TAB_SCRIPT / TAB_API_ANSWER / HIDDEN_PAGE). With a matching itemStruct the TikTok adapter
   builds rows from it without fetching the page, checks files as in P39 step 6 with the
   tab's cookies, and falls back to the page read only when no file works.
2. The tab's data: an asset script run with evaluateJavascript (main thread, <= 1 s) when
   Download is tapped on a TikTok tab returns the compact item for the on-screen post id from
   __UNIVERSAL_DATA_FOR_REHYDRATION__ (any __DEFAULT_SCOPE__ key), SIGI_STATE or step 3's store.
3. TikTok's own API answers: androidx.webkit WebViewCompat.addDocumentStartJavaScript for
   https://www.tiktok.com and https://m.tiktok.com (feature check DOCUMENT_START_SCRIPT;
   fallback onPageStarted, noted in the Result) wraps fetch and XMLHttpRequest for same-site
   /api/ paths and reads a CLONE of JSON with itemList / itemStruct / aweme_list after TikTok's
   own code got it; keeps <= 200 compact items by id in a page-local object; never changes,
   delays or repeats TikTok's requests; swallows its own errors; sends nothing anywhere. Add
   androidx.webkit (a version whose AAR accepts compile SDK 35, e.g. 1.12.1) to
   gradle/libs.versions.toml and the build file(s).
4. TikTokPageEngine (app/.../detection/tiktok/, like WebViewBotGuardEngine; TT_HIDDEN_PAGE=ON):
   offscreen WebView on the main thread, desktop Chrome agent, JavaScript on, images blocked,
   video/audio requests answered empty, the shared cookie store (TT_HOME_COOKIES=ON), step 3's
   script; loads the link (redirects followed), polls step 2's script every 300 ms until the
   item for the post id appears or 15 s pass, then destroys the WebView; one at a time (Mutex).
   A check TikTok's own script passes by itself passes; one that needs a person -> BOT_CHECK
   "TikTok wants a check. Open the video in YFT's browser, then tap Download." Never automated.
   TT_HOME_COOKIES=OFF: clear the TikTok cookies it set when it finishes; Home stays
   cookie-free.
5. Order (Details list each step): browser = tab data -> page read with the tab's cookies ->
   hidden page (only when the tab has no item for this post) -> the player's file (P39 step
   8); Home = page read -> hidden page -> today's generic scan. Try again runs the order again.
6. Rows from tab or hidden-page data carry the tiktok.com cookies from CookieManager (taken at
   the lookup, never logged) and Referer https://www.tiktok.com/; a 403 at download time ->
   the same quality's other addresses, then Try again reads the page data again.
7. No "Falling back to generic detection" anywhere; the TikTok banner only when every step
   failed, with the next thing the user can do.
TESTS P40: JVM — pageData with a matching item -> rows without a page request (must fail on
the old code); another id -> page read; the item the script made on the instrumented fixture
(saved as a JVM fixture) -> the same rows as the page read; browser and Home orders with fakes;
hidden-page timeout -> next step; two hidden-page lookups -> the second waits;
TT_HIDDEN_PAGE=OFF; TT_HOME_COOKIES=OFF. Instrumented (CI emulator, androidTest .../tiktok/,
assets/tiktok/): a TikTok-like fixture page served by the test (its origin allowed for the
scripts only in the test, through a constructor parameter) with an SSR data script and a page
script that fetches /api/recommend/item_list/ -> (a) the tab script returns the SSR item;
(b) after the fetch, an API item by id; (c) the page's own code still gets its answer
unchanged; (d) TikTokPageEngine returns the item within 15 s and destroys its WebView; (e) no
item -> timeout, WebView destroyed.
LIVE CHECK P40 (sandbox Chromium through Playwright, desktop agent, polite): one public TikTok
video page and https://www.tiktok.com/foryou after one scroll; run the same asset scripts;
report item found, id matches, qualities, hosts, API answers seen (count). Nothing else.

Rules: ADR-006 as above; never log, print or commit cookies, tokens, signed media or image URLs
(fixtures keep host + path, query values REDACTED; Details and logs name hosts only); WebView
and WebSettings calls on the main thread only; Kotlin lines within 100 characters; keep every
testTag; app text stays English; temporary files and backups outside the repository
(/data/tmp, /data/bak); never undo work with git reset --hard, git clean or git stash; do not
edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P39 (P40), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :extractor-sites:test :extractor-api:test :core-browser:testDebugUnitTest :core-media:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status OWNER
   CHECK; Result with any "Plan adapted"; validation numbers; regression proof; live-check
   markers; CI links; hand-offs); SUPPORT_MATRIX.md TikTok row.
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P39: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting. (Docs-only pushes start no CI.)
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5, with Level), with the run links
   (the Preview APK run is the build he can try with his VPN) and the owner check: TikTok in
   YFT's browser on For You and a video page -> qualities with sizes -> the file plays; a
   vt.tiktok.com link on Home -> qualities; anything that fails -> a screenshot of Details.
After P40: set your SESSION_STATE section to READY FOR MERGE (last code commit, green CI links),
report, and stop.
```
