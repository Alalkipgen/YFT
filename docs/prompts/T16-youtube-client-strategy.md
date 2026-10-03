# T16 — YouTube client strategy

**ရည်ရွယ်ချက်:** YouTube ကို တကယ်ဒေါင်းလို့ရအောင် client strategy ကို ပြောင်းပါမယ် — D2 မှာ owner ရွေးတဲ့ A + B + C (PO token၊ device client၊ browser မှာ ခဏ play ပြီးမှ download) သုံးခုလုံးကို fallback chain တစ်ခုအဖြစ် (ADR-006)။

**အချက်အလက်:** Phase 10 · P1 (owner) · Very hard · ၆–၉ ရက် · လိုအပ်ချက်: D2 = A + B + C (ဖြေပြီး၊ 2026-10-03) နဲ့ T08 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ D2 ကို ဖြေပြီးပါပြီ (A + B + C)။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T16](../FIX_PLAN.md#t16--youtube-client-strategy)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T16 — YouTube client strategy
Branch: work/phase-8-field-fixes   (owner change 2026-10-03)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: D2=A+B+C (2026-10-03, already recorded in FIX_PLAN §3)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it (owner change 2026-10-03: nothing is merged
   or released before T19, so every task stays on this branch).
2. Read docs/FIX_PLAN.md §0, §3, finding F4, task T16; then docs/SESSION_STATE.md and
   every file under "Read first" in T16.
3. T08 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
   D2 is A + B + C (owner, 2026-10-03): build all three options as one fallback chain
   (docs/decisions/ADR-006-owner-override-any-working-method.md).
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T16 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T16 in order; stay inside the task (anything else -> FIX_PLAN §9).
- Option B: a visionos-style profile in YouTubeClientProfile.kt (all client identifiers stay
  in that file; copy values from yt-dlp INNERTUBE_CLIENTS at the time and note the yt-dlp
  version), asked first, then WEB_EMBEDDED_PLAYER, then the page's client. Direct URLs, no
  player script. Progressive formats are usually missing: HD needs T17; until then offer
  audio + whatever progressive exists. ADR-006 already allows this: record the client
  order and the yt-dlp version there and update docs/YOUTUBE_RISK_REVIEW.md.
- Option A: offscreen WebView runs YouTube's BotGuard like the web player, mints the
  GVS/player PO token bound to visitor data, attaches it to MWEB/WEB requests and media URLs
  (pot=). Write it independently: NewPipe's version is GPL-3.0 (reference only, never copy).
- Option C: when headless lookups meet a bot check, the user plays the video for a moment in
  YFT's browser and taps Download; the lookup then uses the browser's session (cookies,
  visitor data) and any token the page's player used.
- All: keep T08's bot-check and SABR handling; refresh fixtures (sanitized); keep
  `node scripts/verify-youtube-solver.mjs` passing; never log tokens or signed URLs.

TESTS (a regression test must fail on the old code)
- Client order and fallback; parser on refreshed fixtures; token/URL redaction.

LIVE CHECK (public pages only; never paste bodies, cookies or signed URLs)
- bash scripts/live-check.sh on two public videos (e.g. dQw4w9WgXcQ, aqz-KE-bpKQ); report
  playability per client only. The owner's phone result (Copy details) decides DONE.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md, docs/RISKS.md, docs/YOUTUBE_RISK_REVIEW.md, ADR-006 (client order),
   docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Changed), the FIX_PLAN status board
   (T16 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T16, next action T17).
   Run `node scripts/verify-youtube-solver.mjs` too and report its result.
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T16: YouTube client strategy (A + B + C)"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   paste two YouTube links: found + download plays, or Copy details and send them.
   Then continue with T17 (owner order, FIX_PLAN §3).
```
