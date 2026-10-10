# Agent C — Other sites: the page's video, never the ad (P43)

**ရည်ရွယ်ချက်:** Adapter မရှိတဲ့ site တွေ (Pornhub လို adult site ပါ) မှာ Download sheet ရဲ့ main video က
**အမြဲ page ရဲ့ video အစစ်** ဖြစ်ရမယ် — pre-roll ကြော်ငြာ (0:30) ကို ဘယ်တော့မှ မပြရ၊ "The first link is
gone — using a fresh link" ဖြစ်တဲ့အခါမှာလည်း မပြရ (ရာနှုန်းပြည့်)။ "Other videos on this page" ထဲမှာလည်း
သေချာတဲ့ ကြော်ငြာ မပါရ။

**အကြောင်းရင်း:** Main link က 410 (gone) ဖြစ်ရင် P37 ရဲ့ fresh-link chain (NEWEST → PLAYER → REREAD →
NEXT) က player request ထဲက video ကို ယူတာ — length မသိတဲ့ video ကို "page ရဲ့ video" လို့ လက်ခံလိုက်လို့
(`length == null || video.durationMillis == null`) ကြော်ငြာ (VAST request ကို မသိလိုက်ရင် PREVIEW
မဖြစ်) က ဝင်လာတာ။ ပြင်နည်း — "page ရဲ့ video လား" စည်းမျဉ်း တစ်ခုတည်း (page/player က နာမည်ပေးထား၊ page
ပြောတဲ့ length နဲ့ ကိုက်၊ သို့ ပျက်သွားတဲ့ video length နဲ့ ကိုက်)၊ length မသိရင် အရင်တိုင်း (မှန်းမထား)၊
resolve ပြီးရင် length ကို ထပ်စစ်၊ ad host list + VAST/VMAP ကို ပိုသိ၊ ဘာမှမကိုက်ရင် ad မပြဘဲ "Reload page
and try again"။

**အချက်အလက်:** Phase 15 · **Agent C** · branch `work/phase-15-ads` · P43 (Medium, 5–7 နာရီ) · Agent A နဲ့
B နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ ပြီးရင် `READY FOR MERGE`
လို့ပြောပြီး ရပ်ပါမယ်။ Agent ၂ ယောက်ပဲ သုံးရင် Agent B ရဲ့ chat ထဲမှာ (P42 ပြီးမှ)
`BRANCH_OVERRIDE: work/phase-15-downloads` နဲ့ paste လုပ်ပါ။ Default: တင်းကျပ်တဲ့ စည်းမျဉ်း
(`AD_RULE=STRICT`)။ Computer မှာ SSH key မရှိရင် agent က key အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် —
Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး "SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P43](../FIX_ADD_PLAN.md#p43--other-sites-the-pages-video-never-the-ad)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Agent: C — ads: the sheet's choice of the page's video (one of three agents working at once)
Task: P43 — Other sites: the page's video, never the ad
Branch: work/phase-15-ads (first start: create it from origin/work/phase-15-integration)
BRANCH_OVERRIDE: none     (two-agent mode: work/phase-15-downloads, after Agent B's P42)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: AD_RULE=LENIENT keeps today's rule plus the measured length
                           and the check after the resolver)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-C (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-C
- Push with SSH, using the key whose path is in /data/.ssh/CURRENT_KEY (it pushed on
  2026-10-09). If that file or key is missing, or a push says "Permission denied", make a NEW
  key (never search the computer for other keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/yft_c_<date> -C "yft-c-<date>"
    echo /data/.ssh/yft_c_<date> > /data/.ssh/CURRENT_KEY
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of the .pub file and wait until he says he added it (GitHub >
  YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i $(cat /data/.ssh/CURRENT_KEY) -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.
- Two-agent mode (BRANCH_OVERRIDE set): keep working in Agent B's folder /data/YFT-B on that
  branch; you still write only the "## Agent C ..." sections of the shared docs.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-browser/src/main/java/com/alal/yft/core/browser/detection/{VastAdTracker,
  BrowserObservationMapper,PageFactsReader,PlayerSetupScanner,HtmlMediaScanner}.kt and new
  files beside them whose names start with "Ad" (for example AdHosts.kt);
  core-model/src/main/kotlin/com/alal/yft/core/model/media/**; extractor-generic/**;
  app/src/main/java/com/alal/yft/feature/quickdownload/** EXCEPT the function lookupState in
  QuickDownloadViewModel.kt (Agent A's); app/src/androidTest/java/com/alal/yft/browser/** except
  FocusedVideoProbeInstrumentedTest.kt (Agent A's), app/src/androidTest/assets/{browser-detection,
  ads}/** (ads is new); the tests beside them.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent C ...", CHANGELOG.md
  "### Phase 15 — Agent C (P43)", docs/TEST_MATRIX.md "### Agent C — P43".
- Never: extractor-sites, extractor-api, the rest of core-browser, core-media, app/.../detection,
  app/.../feature/{browser,home,detectedmedia} (Agent A); core-download, core-model/.../download,
  app/.../download, app/.../feature/{downloads,library} (Agent B); the manifest, res/, Gradle
  files, docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**. Agents A and B push their
  own branches at the same time. Need a change outside your files? Do not make it: write
  "Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and report it.
- Contracts: the media model in core-model/.../media (MediaCandidate and its fields,
  MediaGroups, FreshLinks, PageMediaRole, BrowserRequestContext) is used by Agent A's TikTok
  code: change it only by adding, with defaults. DetectedMediaStore and PageVideoLookup are
  Agent A's: read them as they are. Pages of sites with adapters (adapterSite, P37) keep their
  own choice: your rule never touches them.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-15-ads origin/work/phase-15-integration
   Later:       git switch work/phase-15-ads && git pull --ff-only
   (With BRANCH_OVERRIDE: switch to that branch and pull it instead.)
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2 (item 7), 3 (G8), 4 (R34) and task P43
   with its "Read first" files, then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent C scope):
   ./gradlew --no-daemon --continue :core-model:test :extractor-generic:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P43 IN PROGRESS, the start date and your base commit.

WORK P43 (FIX_ADD_PLAN P43, finding R34)
Root cause: QuickDownloadViewModel.nextAttempt's chain (NEWEST -> PLAYER -> REREAD -> NEXT)
takes FreshLinks.playerVideo, which accepts a candidate of unknown length
(length == null || video.durationMillis == null); a candidate counts as an ad only when it is
PREVIEW (VastAdTracker marks only after a recognized VAST/VMAP request) or far shorter than the
stated length. When the first link is gone, an unrecognized pre-roll of unknown length passes.
1. Reproduce item 7 first (JVM): the page states 10:05; its player script names the main
   video (HLS and MP4) whose first link answers 410; the player fetched a 30 s MP4 pre-roll from
   an ad host after an ad request VastAdTracker does not recognize -> today's chain offers the
   30 s file (must fail on the old code).
2. One rule (a new type in core-model/.../media, e.g. PageVideoProof), used by the first choice
   and by NEWEST, PLAYER, REREAD and NEXT. Page's video: (a) named by the player setup or a
   PageMediaRole.MAIN mark, (b) length matches the page's stated length (within 5 s or 5%), or
   (c) length matches the video whose link failed. Ad: PREVIEW, an ad host, fetched inside an ad
   break (ad request until the main video's first request), or <= 60 s while the page (or the
   named video) is more than twice as long. Otherwise: not proven.
3. AD_RULE=STRICT: measure a not-proven candidate's length before it becomes the main video
   (HLS playlist total; MP4 mvhd from the first bytes when moov comes first, one ranged read);
   after the resolver, check the real length against the stated length again (mismatch -> ad,
   next step of the chain); when the page states a length, only a matching video is the main
   one; when nothing passes, no ad is offered: REREAD, then "Reload page and try again" (P37).
   Proven ads are not listed in "Other videos on this page". A page with no ad sign (no ad
   request, no PREVIEW mark, no stated length that a candidate misses) keeps today's choice.
4. Recognize more ads (VastAdTracker, BrowserObservationMapper, new AdHosts.kt): XML answers
   whose body starts with <VAST or <VMAP, IMA hosts, paths like /vast, vast.xml, preroll, /ads/,
   and common video-ad hosts (e.g. trafficjunky, exoclick, juicyads, tsyndicate, magsrv, adtng,
   realsrv, Google IMA and DoubleClick); an MP4 or HLS from such a host is always an ad.
5. Details say why: "chosen: named by the page's player" / "length 10:03 matches the page
   (10:05)" / "skipped: 0:30 ad (ad host)" / "skipped: length unknown" (no addresses).
6. The header's length always comes from the chosen video. adapterSite pages and pages without
   ads behave as before (P28, P29, P37).
7. Instrumented fixture (androidTest .../browser/detection/, assets/ads/): a fixture site
   served by the test with an IMA-like pre-roll (ad request, then a 30 s MP4 from a second host
   named like an ad host) and a main video whose first link answers 410 -> the sheet offers the
   main video with the page's length.
TESTS P43: JVM — the item-7 repro and the check after the resolver (a 0:30 result on a 10:05
page -> next step) (both must fail on the old code); each proof and ad rule; an unknown length
measured, then the ad skipped; "Reload page and try again" when nothing passes; proven ads not
in "Other videos"; a page without ad signs keeps today's choice; AD_RULE=LENIENT; P28's, P29's
and P37's tests stay green. Instrumented: step 7 (CI emulator).
LIVE CHECK P43: adult sites block US data-centre addresses and show age notices: fixtures only;
never click an age or identity notice. Fixtures use neutral titles and blank pictures; reports
name hosts, lengths, statuses and counts only. Optionally one public non-adult page with an IMA
pre-roll.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass); never log or
commit cookies, tokens, signed media or image URLs or keys; nothing explicit enters the
repository, logs or reports; Kotlin lines within 100 characters; keep every testTag; app text
stays English; temporary files and backups outside the repository (/data/tmp, /data/bak); never
undo work with git reset --hard, git clean or git stash; do not edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P43, put the old versions
back, run the new tests and see them fail, restore with cp, check with cmp, and name the
failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :core-model:test :extractor-generic:test :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status OWNER
   CHECK; Result with any "Plan adapted"; validation numbers; regression proof; CI links;
   hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P43: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting. (Docs-only pushes start no CI.)
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5, with Level), with the run links and
   the owner check: the adult site of item 7 -> Download on 10 different videos (some with a
   pre-roll) -> the page's own length every time, never 0:30; Javtiful and the HTTP-410 site
   still work; a screenshot of Details for any miss.
After P43: set your SESSION_STATE section to READY FOR MERGE (last code commit, green CI links),
report, and stop.
```
