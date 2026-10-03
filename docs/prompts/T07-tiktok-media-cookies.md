# T07 — TikTok media cookies for Home lookups

**ရည်ရွယ်ချက်:** TikTok video ဒေါင်းရင် 403 ဖြစ်နေတာ (P5) ကို ပြင်ပါမယ်။ Page က ပေးတဲ့ TikTok cookie တွေကို memory ထဲမှာပဲထားပြီး video ဖိုင်ဒေါင်းတဲ့အခါ သုံးပါမယ်။

**အချက်အလက်:** Phase 8 · P1 · Medium · ၀.၅ ရက် · လိုအပ်ချက်: T05 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T07](../FIX_PLAN.md#t07--tiktok-media-cookies-for-home-lookups)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T07 — TikTok media cookies for Home lookups
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F5, task T07; then docs/SESSION_STATE.md and
   every file under "Read first" in T07.
3. T05 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T07 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T07 in order; stay inside the task (anything else -> FIX_PLAN §9).
- ExtractorHttpResult.Success exposes the response's Set-Cookie pairs (name=value only,
  domain-checked), in memory only. OkHttpExtractorClient has no cookie jar today.
- TikTok: when the request context has no cookie, build the media context's cookie from the
  page response's cookies for .tiktok.com (tt_chain_token, ttwid, tt_csrf_token...).
  The browser path keeps the WebView's cookie.
- Download rule unchanged: cookies only to the media URL's own origin, dropped on
  cross-origin redirects; follow the existing stored-task policy, do not widen it.
- toString() and logs never show cookie values.

TESTS (a regression test must fail on the old code)
- Fake client with Set-Cookie -> candidate carries only TikTok's cookies; none when the page
  set none; redaction.

LIVE CHECK (public pages only; never paste bodies, cookies or signed URLs)
- Home lookup of https://www.tiktok.com/@scout2015/video/6718335390845095173, then a ranged
  GET of the candidate with its request context -> 206 (print the status only).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (TikTok row), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Fixed), the FIX_PLAN status board
   (T07 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T07, next action T08).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-api:test :extractor-sites:test :core-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T07: TikTok media cookies for Home lookups"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   paste a TikTok link -> download -> it plays; the same from the browser.
   Then stop. Do not start T08.
```
