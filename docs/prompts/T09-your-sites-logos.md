# T09 — Your sites: YouTube, Facebook, TikTok with logos

**ရည်ရွယ်ချက်:** Home 'Your sites' ကို YouTube၊ Facebook၊ TikTok (logo နဲ့) ပြောင်းပါမယ်။ ရှိပြီးသား site list ကို migration နဲ့ မပျက်အောင် ထိန်းပါမယ်။

**အချက်အလက်:** Phase 8 · P1 · Easy · ၀.၅ ရက် · လိုအပ်ချက်: မရှိ

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T09](../FIX_PLAN.md#t09--your-sites-youtube-facebook-tiktok-with-logos)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T09 — Your sites: YouTube, Facebook, TikTok with logos
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F6, task T09; then docs/SESSION_STATE.md and
   every file under "Read first" in T09.
3. No task or decision has to come first.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
5. Set T09 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T09 in order; stay inside the task (anything else -> FIX_PLAN §9).
- HomeSites.DEFAULTS = YouTube https://m.youtube.com, Facebook https://m.facebook.com,
  TikTok https://www.tiktok.com.
- One-time migration in core-data HomeSitesRepository, key home_sites_defaults_version = 2:
  drop entries equal to the old defaults (same name + URL), put missing new defaults first
  (match by site: youtube.com/m.youtube.com/youtu.be, facebook.com/m.facebook.com/fb.watch,
  tiktok.com), keep user sites, respect MAX_SITES; empty list stays empty; never runs twice.
- Logos: vector drawables from Simple Icons SVGs (CC0; note the version) for YouTube,
  Facebook, TikTok, Instagram, X + a host-to-logo map; unknown sites keep the letter avatar;
  never fetch favicons; brand colours with Night contrast checked.
- Notices: Simple Icons (CC0-1.0) in THIRD_PARTY_NOTICES.md and About licences (the test
  compares them) + "Site names and logos belong to their owners; YFT is not affiliated
  with them."

TESTS (a regression test must fail on the old code)
- Migration: untouched old defaults; owner's case (old defaults + YouTube -> YouTube,
  Facebook, TikTok, no duplicates); custom list unchanged; empty stays empty; second run
  changes nothing. Home renders + accessibility (logo described by the site name).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update DESIGN-NOTES decision 2, docs/THIRD_PARTY_NOTICES.md, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Changed), the FIX_PLAN status board
   (T09 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T09, next action T10).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :core-data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T09: Your sites defaults YouTube, Facebook, TikTok with logos"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   Home shows YouTube, Facebook and TikTok with logos; his own YouTube entry is not
   duplicated.
   Then stop. Do not start T10.
```
