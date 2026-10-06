# Agent C — Browser: Google search, history, no ad redirects (P30 → P31 → P32)

**ရည်ရွယ်ချက်:** Browser မှာ စာရိုက် ရှာရင် Google နဲ့ ရှာမယ် (Settings › Browser မှာ DuckDuckGo၊ Bing
ပြောင်းလို့ရ) (P30)။ ဖွင့်ခဲ့တဲ့ page တွေကို History အဖြစ် မှတ်ထား၊ စာရင်းပြ၊ ဖျက်လို့ရ (P31)။ Page
သို့မဟုတ် ကြော်ငြာကို နှိပ်လိုက်တာနဲ့ spam site တွေဆီ ခုန်သွားတာ (pop-up / ad redirect) ကို ပိတ်မယ်၊
"Pop-up blocked · Open" နဲ့ လိုရင် ဖွင့်လို့ရ၊ ပုံမှန် link တွေ ဖွင့်လို့ရဆဲ (P32)။ Page ထဲက video
ကြော်ငြာကိုတော့ မပိတ်ပါ။

**အချက်အလက်:** Phase 13 · **Agent C** · branch `work/phase-13-browser` · P30 (Easy, 1–2 နာရီ) → P31
(Medium, 3–5 နာရီ) → P32 (Medium–Hard, 4–7 နာရီ) · Agent A နဲ့ B နဲ့ **တပြိုင်နက်** လုပ်လို့ရ

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ ပြီးရင် `READY FOR MERGE`
လို့ပြောပြီး ရပ်ပါမယ်။ Default: Google၊ History ဖွင့်၊ pop-up ပိတ်။ ပြောင်းချင်ရင် ဥပမာ
`OWNER ANSWERS: SEARCH=DUCKDUCKGO, HISTORY=OFF` လို့ ရေးပါ။ Agent ၂ ယောက်ပဲ သုံးရင် Agent A ရဲ့ chat
ထဲမှာ (P27 ပြီးမှ) `BRANCH_OVERRIDE: work/phase-13-merge-speed` နဲ့ paste လုပ်ပါ။ Computer မှာ SSH key
မရှိရင် agent က key အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — Deploy keys မှာ "Allow write access"
နဲ့ ထည့်ပြီး "SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P30](../FIX_ADD_PLAN.md#p30--browser-google-search),
[P31](../FIX_ADD_PLAN.md#p31--browser-history),
[P32](../FIX_ADD_PLAN.md#p32--block-pop-ups-and-ad-redirects)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, DataStore, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Agent: C — the browser: search engine, history, pop-up and redirect blocking, Settings ›
       Browser (one of three agents working at once)
Tasks: P30 — Google search; then P31 — Browser history; then P32 — Block pop-ups and ad
       redirects
Branch: work/phase-13-browser (first start: create it from origin/work/phase-13-integration)
BRANCH_OVERRIDE: none     (two-agent mode: work/phase-13-merge-speed, after Agent A's P27)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: SEARCH=DUCKDUCKGO or SEARCH=BING as the default engine;
                           HISTORY=OFF as the switch's default; POPUPS=ALLOW as the blocking
                           switch's default; STOP_AFTER=P31)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-C (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-C
- Push with SSH. If /data/.ssh/id_ed25519 is missing, make a NEW key (never search the
  computer for old keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/id_ed25519 -C "yft-c-<date>"
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of /data/.ssh/id_ed25519.pub and wait until he says he added it
  (GitHub > YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i /data/.ssh/id_ed25519 -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.
- Two-agent mode (BRANCH_OVERRIDE set): keep working in Agent A's folder /data/YFT-A on that
  branch; you still write only the "## Agent C ..." sections of the shared docs.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-browser/src/main/java/com/alal/yft/core/browser/{webview,policy}/**; core-data/** (one
  Room migration: 5 -> 6); core-model/src/main/kotlin/com/alal/yft/core/model/settings/**;
  app/src/main/java/com/alal/yft/feature/browser/** except BrowserViewModel.kt, BrowserUiState.kt
  and BrowserDownloadFab.kt (Agent B); app/src/main/java/com/alal/yft/feature/settings/**;
  app/src/main/java/com/alal/yft/ui/theme/YftIcons.kt (additions only);
  app/src/androidTest/java/com/alal/yft/browser/navigation/** (new); the tests beside them.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent C ...", CHANGELOG.md
  "### Phase 13 — Agent C (P30, P31, P32)", docs/TEST_MATRIX.md "### Agent C — P30, P31, P32".
- Never: core-browser/.../{detection,session}/**, core-media, extractor-*, core-model/.../media/**,
  app/.../feature/{quickdownload,home,detectedmedia}/** and B's three browser files (Agent B);
  core-download, app/.../download/**, app/.../feature/downloads/** (Agent A); MainActivity,
  ui/navigation, docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**, res/ and the
  manifest. Agents A and B push their own branches at the same time. Need a change outside your
  files? Do not make it: write "Hand-off to <agent>: <file> — <change> — <why>" in your
  SESSION_STATE section and report it.
- Contracts: BrowserObservationSink (new methods only with default bodies); the calls
  SecureBrowserWebViewClient makes today (requests, page start and finish, DOM probe, playing
  probe) keep their order and threads, and every request B's detection sees today still reaches
  it (except the scripts P32 step 3 blocks); BrowserSearch.webUrl(words) keeps its signature
  (B's BrowserViewModel calls it); settings models change only by adding fields with defaults.
  Use B's BrowserViewModel and BrowserUiState as they are.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-13-browser origin/work/phase-13-integration
   Later:       git switch work/phase-13-browser && git pull --ff-only
   (With BRANCH_OVERRIDE: switch to that branch and pull it instead.)
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2, 3 (F2–F4), 4 (R13–R15) and tasks P30,
   P31 and P32 with their "Read first" files, then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent C scope):
   ./gradlew --no-daemon --continue :core-browser:testDebugUnitTest :core-data:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P30 IN PROGRESS, the start date and your base commit.

WORK P30 — Google search (FIX_ADD_PLAN P30, finding R13)
Root cause: BrowserSearch.webUrl hard-codes https://duckduckgo.com/?q=… for the start page and
for words typed in the address bar.
1. Setting searchEngine (GOOGLE default, DUCKDUCKGO, BING) in the settings model and its
   DataStore repository (new key; an older install without it reads Google).
2. BrowserSearch.webUrl(words) keeps its signature and uses the current engine
   (https://www.google.com/search?q=…, https://duckduckgo.com/?q=…,
   https://www.bing.com/search?q=…); your code keeps the engine current from the settings flow
   (for example a small holder BrowserSearch reads, updated where BrowserScreen collects
   settings). If that is impossible without B's BrowserViewModel, write a hand-off to Agent B.
3. The start page's search row names the engine ("Search Google"); Settings gets a Browser
   section with "Search engine" (testTag settings-search-engine).
TESTS P30: Google URL with encoding (spaces, &, Burmese text); DuckDuckGo and Bing; an old
settings file reads Google; the start page label (must fail on the old code: DuckDuckGo).

WORK P31 — Browser history (FIX_ADD_PLAN P31, finding R14)
1. Room 6: table browser_history (url unique, title, host, last_visited_at, visit_count),
   MIGRATION_5_6, exported schema 6.json, a DAO (insert-or-update, newest first with a limit or
   paging, search by title or host, delete one, delete all, prune).
2. Record a page when the main frame finished and the title is known: HTTPS only; not YFT's
   start page, about:, data: or a page P32 blocked; without the fragment and tracking
   parameters (utm_*, fbclid, gclid, igshid); never cookies or headers. The same address again
   raises its count and time. Keep 90 days and at most 5,000 pages (oldest pruned).
3. UI: browser menu "History" (testTag browser-menu-history) opens a full-screen list inside
   the browser screen (testTag browser-history): search box, Today / Yesterday / Earlier, a row
   opens its page, a row's menu deletes it, "Clear history" with a confirmation. The start page
   shows the last six pages under "Recent" (testTag browser-recent). No new navigation route.
4. Settings › Browser: "Save browser history" switch (default on) and "Clear browser history"
   with a confirmation; the existing "Clear browsing data" clears the history too.
TESTS P31: migration 5 -> 6 keeps every download record (AppDatabaseMigrationTest); the DAO; the
recording rules (HTTPS only, tracking parameters, count, switch, pruning); the history list
(open, delete, clear, search) and Recent in Compose tests; the privacy cleaner clears it.

WORK P32 — Block pop-ups and ad redirects (FIX_ADD_PLAN P32, finding R15)
Root cause: SecureWebViewPolicy sets setSupportMultipleWindows(false), so window.open() and
target="_blank" load in the same tab, and shouldOverrideUrlLoading lets every HTTPS top-level
navigation through, with or without the user's tap.
1. New windows: setSupportMultipleWindows(true) (javaScriptCanOpenWindowsAutomatically stays
   false) and SecureBrowserChromeClient.onCreateWindow: a window the user's tap opened to the
   same site opens in the current tab; any other window is blocked and a notice "Pop-up
   blocked" with "Open" (opens it in the current tab; testTag browser-blocked-notice) shows
   for 4 s.
2. Top-level redirects: a pure, tested AdRedirectPolicy (core-browser/.../policy/). Block a
   navigation to a host on YFT's own list of pop-up and redirect ad networks (written for YFT,
   a few dozen hosts, the reason per family; no copied third-party filter lists) and a
   navigation to another site that the page started without the user's tap (!hasGesture(),
   not a server redirect of the user's own navigation). Allow typed addresses, the user's taps
   on normal links (also to other sites), same-site navigations, server redirects of the
   user's navigation unless a hop is listed, and app links as today (AppLinkPolicy). A blocked
   navigation shows "Blocked a redirect to <host>" with "Open".
3. Subresources (scripts) from listed hosts get an empty answer in shouldInterceptRequest. The
   page's own video ads stay (F4); B's media detection still sees every other request.
4. Settings › Browser: "Block pop-ups and ad redirects" (default on; testTag
   settings-block-popups). P31 does not record blocked pages.
TESTS P32: the policy table (tap / no tap, same / other site, listed host, listed redirect hop,
typed address, app link); onCreateWindow's decision as a pure function; the notice and "Open"
in a Compose test; the setting off lets everything through; a redirect without a tap is
blocked (must fail on the old code). Instrumented (app/src/androidTest/.../browser/navigation/,
CI emulator): a local fixture page whose tap handler calls window.open() and whose timer sets
location to another host -> the page stays and the notice shows; a normal link to another site
opens.
LIVE CHECK P32: optional, one public page known for pop-unders, only without an age or identity
gate; report hosts and decisions only. Otherwise the policy table and the owner's phone.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in; never automate a page's age or identity check); never log or commit cookies, tokens,
signed media or image URLs or keys; WebView and WebSettings calls on the main thread
(shouldInterceptRequest and @JavascriptInterface on other threads); Kotlin lines within 100
characters; keep every testTag; app text stays English; temporary files and backups outside
the repository (/data/tmp, /data/bak); never undo work with git reset --hard, git clean or
git stash; do not edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P30 (P31, P32), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (after each task; report only what you ran)
   ./gradlew --no-daemon --continue :core-browser:testDebugUnitTest :core-data:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status OWNER
   CHECK; Result with any "Plan adapted"; validation numbers; regression proof; CI links;
   hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P30: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the run links and the task's
   owner check (P30: words in the address bar and on the start page -> Google, the engine
   setting works; P31: three sites -> History lists them newest first, open, delete, Clear
   history, the switch stops recording; P32: taps on pages and their ads stay on the page,
   "Pop-up blocked · Open" works, normal links open, videos and Download still work). Then
   continue P30 -> P31 -> P32 without waiting.
After P32: set your SESSION_STATE section to READY FOR MERGE (last commit, green CI links),
report, and stop. Agent A merges (P33).
```
