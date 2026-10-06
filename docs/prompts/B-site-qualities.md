# Agent B — YouTube and Facebook: every quality (P22 → P23)

**ရည်ရွယ်ချက်:** YouTube မှာ 360p တစ်ခုတည်း မဟုတ်တော့ဘဲ Snaptube လို 144p–1080p၊ 2K/4K အထိ size နဲ့တကွ
ပေါ်အောင် (P22)၊ Facebook မှာ Home ကနေဖြစ်ဖြစ် browser ကနေဖြစ်ဖြစ် 720p၊ 360p၊ Audio M4A (size ပါ)၊ MP3
အတူတူ ပေါ်အောင် (P23) ပြင်မယ်။

**အချက်အလက်:** Phase 12 · **Agent B** · branch `work/phase-12-site-qualities` · P22 (Hard, 8–12 နာရီ)
→ P23 (Medium–Hard, 5–8 နာရီ) · Agent A နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ ပြီးရင် `READY FOR MERGE`
လို့ပြောပြီး ရပ်ပါမယ်။ YouTube အတွက် ဖုန်းက sheet › **Details** screenshot ရှိရင် ဒီ chat ထဲ ထည့်ပေးပါ
(sandbox က YouTube bot check ခံရလို့ ဖုန်းရဲ့ Details က အကောင်းဆုံး သက်သေပါ)။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P22](../FIX_ADD_PLAN.md#p22--youtube-every-quality),
[P23](../FIX_ADD_PLAN.md#p23--facebook-every-quality)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3).

Repository: https://github.com/Alalkipgen/YFT
Agent: B — YouTube and Facebook adapters (one of three agents working at once)
Tasks: P22 — YouTube: every quality; then P23 — Facebook: every quality
Branch: work/phase-12-site-qualities (first start: create it from origin/work/phase-12-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: STOP_AFTER=P22; pasted phone Details screenshots count as evidence)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-B (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-B
  and, when /data/YFT exists with push access, copy it:
    git -C /data/YFT-B remote set-url origin "$(git -C /data/YFT remote get-url origin)"
    git -C /data/YFT-B config core.sshCommand "$(git -C /data/YFT config core.sshCommand)"
  Otherwise push with the access your environment provides (deploy key or token); never print
  it. A failed push: stop and tell the owner (a local commit is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- extractor-sites/**; extractor-api/** (additions only, with defaults);
  app/src/main/java/com/alal/yft/detection/** (SiteAdapterCoordinator, MergeSupport, potoken/,
  script/, SiteLookupCache); the tests beside them; scripts/live-check.*;
  docs/YOUTUBE_RISK_REVIEW.md and docs/SUPPORT_MATRIX.md (YouTube and Facebook rows).
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent B ...", CHANGELOG.md
  "### Phase 12 — Agent B (P22, P23)", docs/TEST_MATRIX.md "### Agent B — P22, P23".
- Never: core-model (MediaCandidate, AudioFromVideo, MediaGroups belong to Agent C), the sheet,
  Home, the browser, core-download, docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**.
  Agent A (saving files) and Agent C (other sites, the sheet) push their own branches at the
  same time. Need a change outside your files? Do not make it: write "Hand-off to <agent>:
  <file> — <change> — <why>" in your SESSION_STATE section and report it.
- Contracts: SiteAdapterCoordinator (inspect, handles, videoKey, messageFor), SiteLookupCache
  and the extractor-api types change only by adding, with defaults. Data you give Agent C's
  sheet: every video candidate has height (and width), container or MIME type, codecs (video
  and, for a file with sound, audio), contentLengthBytes when the site states it, else bitrate
  and length; adaptive video names its audio companion; HD/SD files carry their real height
  when the page tells it. Never invent a codec, height or size.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-12-site-qualities origin/work/phase-12-integration
   Later:       git switch work/phase-12-site-qualities && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2, 3, 4 (R3, R4) and tasks P22 and P23 with
   their "Read first" files, docs/YOUTUBE_RISK_REVIEW.md, ADR-006, then your section of
   docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started (never another agent's); one Gradle command at a time.
   Starting state before any edit (Agent B scope):
   ./gradlew --no-daemon --continue :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
4. In your SESSION_STATE section: P22 IN PROGRESS, the start date and your base commit.

WORK P22 — YouTube: every quality (FIX_ADD_PLAN P22, finding R3)
Root cause: YouTubeExtractor asks VISIONOS first without visitor data (NO_PAGE signals); from a
flagged network it answers LOGIN_REQUIRED "Sign in to confirm you're not a bot". That failure is
kept in lookup.visionOsAnswer and askPlayer reuses it, so visionOS is never asked again with the
watch page's visitorData. askFallbacks then asks ANDROID (only itag 18, progressive 360p without
contentLength; adaptive formats SABR-only) and the embedded client, whose rule
"enough = offers.hasVideo" is already true from ANDROID's 360p, so extract returns before the
page client (WEB/MWEB with the BotGuard PO token and the player-script solver) is collected:
360p + M4A from that file (size unknown) + MP3. yt-dlp: default clients visionos,web; it reads
the web page first and sends its visitorData with every client, visionOS included.
1. visionOS with visitor data: when the first visionOS answer is not complete (bot check,
   LOGIN_REQUIRED that is not an age check, missing formats) and the watch page gave
   visitorData, ask visionOS once more with it (request context and X-Goog-Visitor-Id) and use
   that answer instead of the kept one. A failed first answer is never reused by the chain.
   Visitor data stays in memory for this lookup only: never logged, never in details, fixtures
   or the lookup cache key.
2. No early end with 360p: the embedded client's "enough" becomes offers.isComplete (adaptive
   video with an audio track), like the device clients; the page client's answer (inline or
   asked, with PO token and player script) is collected before the lookup ends without adaptive
   formats; MWEB as today; ANDROID's itag 18 stays a 360p row only when no adaptive 360p exists.
3. Order: compare the chain with yt-dlp (visionOS with visitor data -> page client -> MWEB ...);
   move ANDROID behind the page client if it only adds 360p; record the evidence.
4. Sizes: contentLength -> contentLengthBytes for every adaptive format; a format without one
   carries its bitrate and the video's length (the sheet shows "~54 MB").
5. Audio: the M4A row comes from the adaptive AAC track (itag 140, with its size), not from the
   360p file; MP3 converts that track.
6. Details name each step's result ("visionOS again with visitor data: 27 formats", "ANDROID:
   1 progressive file, adaptive SABR only", "page client: 22 formats via player script").
TESTS P22 (fixtures: hosts and paths kept, signed values REDACTED, no visitor data values)
- visionOS bot check -> watch page with visitorData -> visionOS again complete -> rows 144p to
  1080p and 2K/4K (must fail on the old code: 360p only).
- visionOS bot-checked twice -> ANDROID 18-only + SABR -> the page client's ciphered formats via
  the solver -> full ladder (must fail on the old code: ends after the embedded client).
- The age-restricted case still ends LOGIN_REQUIRED; the M4A row has itag 140's size.
LIVE CHECK P22 (sandbox; a temporary script in /data/tmp that prints only status, client,
number of formats, heights and sizes): 4pKpLX9NG_k, 8Mw9bwLTQFk, jNQXAC9IVRw, dQw4w9WgXcQ. The
sandbox is often bot-checked: say so plainly; the owner's phone Details are the proof.

WORK P23 — Facebook: every quality (FIX_ADD_PLAN P23, finding R4)
Root cause: (a) since P15 the public page (Safari, no session) is the whole lookup when it has
"AVC tracks or a whole file", so a page with browser_native_hd/sd but no readable DASH ends with
HD/SD only; (b) share/short/post links skip the public page, the page read instead had only
HD/SD, and avcLadder runs only when FacebookDashOffers.lacksAvcVideo(tracks) is true, which is
false for an empty list. HD/SD carry no codecs, so AudioFromVideo offers no Audio. In the browser
the session page had AVC only at 360p (AV1/VP9 above, dropped while AV1 merges are off) and the
ladder was not asked because some AVC existed.
1. The public page is the whole lookup only with AVC DASH video and an audio track for the
   requested video; a page with only HD/SD goes on to the session page and the ladder (P10
   limits).
2. "Needs the AVC ladder": no AVC video at all (an empty list too), or the best AVC height below
   the best height the page offers (any codec or the HD file). Ask it once (Safari, no session)
   on the final video URL: share/short/post links after their redirect; /watch/?v= -> the
   redirected /<page>/videos/<id>/ URL.
3. Merge tracks from every page read for the same video ID without duplicates (same
   representation ID, or same codec family + height + bandwidth).
4. HD/SD: height and width from the matching DASH representation or the page's own fields;
   codecs only when the page states them (the manifest's avc1... and mp4a.40.x), so Agent C's
   sheet can offer their sound; bitrate + length for an estimate when no size is stated.
   Manifest bandwidths are peaks (~3.5x the average): never compute sizes from them.
5. Details: which pages were read and what each added.
TESTS P23 (fixtures with REDACTED signed values)
- Share link -> page with only HD/SD -> the final reel's Safari page with DASH -> 720p/360p +
  Audio (must fail on the old code).
- A public page with only HD/SD is not the whole lookup (must fail on the old code).
- Session page AVC 360 + AV1 720/1080 -> the ladder adds AVC 720 (must fail on the old code).
- /watch/?v= redirect -> the ladder on the final URL; tracks from two pages are not duplicated.
LIVE CHECK P23 (sandbox): bash scripts/live-check.sh https://www.facebook.com/reel/1545617074260365/
and one share link, plus a temporary parser run in /data/tmp: rows, heights, codecs, the audio track, sizes
and markers only (expected AVC 360/720 and mp4a.40.5 ~60.6 kbps on that reel).

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, visitor data, signed media or image URLs or keys;
WebView calls on the main thread; Kotlin lines within 100 characters; keep every testTag;
polite networking (no more requests than the task needs, never two lookups of one video at
once); temporary files and backups outside the repository (/data/tmp, /data/bak); never undo
work with git reset --hard, git clean or git stash.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P22 (or P23), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result.

VALIDATE (after each task; report only what you ran)
   ./gradlew --no-daemon --continue :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md, CHANGELOG.md, docs/SESSION_STATE.md (status DONE
   (date) or OWNER CHECK; Result with any "Plan adapted"; validation numbers; regression proof;
   live check; CI links; hand-offs); P22 also docs/YOUTUBE_RISK_REVIEW.md (client order) and the
   YouTube row of docs/SUPPORT_MATRIX.md; P23 the Facebook row.
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P22: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the run links and the task's
   owner check (P22: 4pKpLX9NG_k in Home and the browser -> 144p...1080p (+2K/4K) with sizes
   close to Snaptube's, M4A ~12 MB, 720p/1080p play with sound, a Details screenshot; P23: one
   reel in Home and the browser -> the same rows 720p/360p with sizes, M4A ~14 MB, MP3). Then
   continue P22 -> P23 without waiting.
After P23: set your SESSION_STATE section to READY FOR MERGE (last commit, green CI links),
report, and stop. Agent A merges (P26).
```
