# T05 — Browser-like request identity for Home lookups

**ရည်ရွယ်ချက်:** Home ကနေ link ရှာတဲ့အခါ browser လို request (User-Agent နဲ့ header) သုံးပါမယ်။ Facebook က login page ပြန်ပေးနေတာနဲ့ YouTube bot check ရဲ့ အကြောင်းရင်းတစ်ခုကို ဖြေရှင်းတာပါ။

**အချက်အလက်:** Phase 8 · P1 · Easy–Medium · ၀.၅ ရက် · လိုအပ်ချက်: မရှိ

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T05](../FIX_PLAN.md#t05--browser-like-request-identity-for-home-lookups)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T05 — Browser-like request identity for Home lookups
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, findings F3, F4, task T05; then docs/SESSION_STATE.md and
   every file under "Read first" in T05.
3. No task or decision has to come first.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T05 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T05 in order; stay inside the task (anything else -> FIX_PLAN §9).
- One place for the headless identity, e.g. app/.../detection/HeadlessIdentity.kt:
  USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) YFT/${BuildConfig.VERSION_NAME}" (no
  'Android' token: F3 shows any Android UA gets a 51 KB shell without video) + the F3
  navigation headers (Accept, Accept-Language, Sec-Fetch-Mode: navigate).
- LinkInspector passes BrowserRequestContext(url, HeadlessIdentity.USER_AGENT, cookie = null);
  HeadlessPageFetcher uses the same UA.
- Adapters: top-level page GETs add Accept + Sec-Fetch-Mode: navigate when unset; JSON/API
  requests unchanged. The browser path keeps the WebView's own UA and cookies.
- scripts/live-check.sh <url>: status, final host + path (no query), size, markers
  (browser_native_hd_url, __UNIVERSAL_DATA_FOR_REHYDRATION__, playabilityStatus).
  It never prints bodies or signed URLs.

TESTS (a regression test must fail on the old code)
- The inspector passes a non-null User-Agent; a fake ExtractorHttpClient sees navigation
  headers on page GETs only; no cookie is ever added; generic fixtures still pass.

LIVE CHECK (public pages only; never paste bodies, cookies or signed URLs)
- bash scripts/live-check.sh https://www.facebook.com/share/v/1Q3kAyptrS/
  -> reel path and the HD marker. Report status, host, path and markers only.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (headless identity note), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Changed), the FIX_PLAN status board
   (T05 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T05, next action T06).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T05: browser-like identity for Home lookups and live-check script"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   none yet (T06-T08 use this); say so.
   Then stop. Do not start T06.
```
