# T16 — YouTube client strategy

**ရည်ရွယ်ချက်:** YouTube ကို တကယ်ဒေါင်းလို့ရအောင် client strategy ကို ပြောင်းပါမယ် — D2 မှာ owner ရွေးတဲ့ A (PO token) ဒါမှမဟုတ် B (device client) အတိုင်း။

**အချက်အလက်:** Phase 10 · P3 · Hard (B) / Very hard (A) · ၂–၃ ရက် (B) / ၅–၈ ရက် (A) · လိုအပ်ချက်: D2 (A သို့ B) နဲ့ T08 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ D2 ကို ဖြေပြီးသားမဟုတ်ရင် `OWNER ANSWERS:` line မှာ ဖြေချက်ထည့်ပါ (ဥပမာ `D2=B`)။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T16](../FIX_PLAN.md#t16--youtube-client-strategy)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T16 — YouTube client strategy
Branch: work/phase-10-formats
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none   (example: D1=YES — record answers in FIX_PLAN §3 first)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-10-formats and pull it. If it does not exist, create it from origin/main,
   but only when `git merge-base --is-ancestor v1.0.0-beta.4 origin/main` succeeds; otherwise
   stop and report that the previous release is not merged yet.
2. Read docs/FIX_PLAN.md §0, §3, finding F4, task T16; then docs/SESSION_STATE.md and
   every file under "Read first" in T16.
3. T08 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
   D2 must be A or B; build only that option. PENDING -> ask the D2 question
   (FIX_PLAN §3) in Burmese and stop.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T16 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T16 in order; stay inside the task (anything else -> FIX_PLAN §9).
- Option B: a visionos-style profile in YouTubeClientProfile.kt (all client identifiers stay
  in that file; copy values from yt-dlp INNERTUBE_CLIENTS at the time and note the yt-dlp
  version), asked first, then WEB_EMBEDDED_PLAYER, then the page's client. Direct URLs, no
  player script. Progressive formats are usually missing: HD needs T17; until then offer
  audio + whatever progressive exists. Write ADR-006 (overrides 'no device impersonation'
  for YouTube only) and update docs/YOUTUBE_RISK_REVIEW.md.
- Option A: offscreen WebView runs YouTube's BotGuard like the web player, mints the
  GVS/player PO token bound to visitor data, attaches it to MWEB/WEB requests and media URLs
  (pot=). Write it independently: NewPipe's version is GPL-3.0 (reference only, never copy).
- Both: keep T08's bot-check and SABR handling; refresh fixtures (sanitized); keep
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
1. Update docs/SUPPORT_MATRIX.md, docs/RISKS.md, docs/YOUTUBE_RISK_REVIEW.md, ADR-006 (option B),
   docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Changed), the FIX_PLAN status board
   (T16 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T16, next action T17).
   Run `node scripts/verify-youtube-solver.mjs` too and report its result.
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T16: YouTube client strategy (option from D2)"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   paste two YouTube links: found + download plays, or Copy details and send them.
   Then stop. Do not start T17.
```
