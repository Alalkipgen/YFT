# T06 — Facebook public reels and videos without sign-in

**ရည်ရွယ်ချက်:** Facebook public reel/video တွေကို sign-in မလိုဘဲ ရှာတွေ့ပြီး ဒေါင်းလို့ရအောင် (P1) DRM အမှားသတ်မှတ်ချက်၊ share link redirect၊ ခေါင်းစဉ်ထဲက HTML entity နဲ့ HD/SD label တွေကို ပြင်ပါမယ်။

**အချက်အလက်:** Phase 8 · P1 · Medium · ၁ ရက် · လိုအပ်ချက်: T05 ပြီးရမယ်

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_PLAN.md` › T06](../FIX_PLAN.md#t06--facebook-public-reels-and-videos-without-sign-in)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Do exactly one task, then stop.

Repository: https://github.com/Alalkipgen/YFT
Task: T06 — Facebook public reels and videos without sign-in
Branch: work/phase-8-field-fixes
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree (moved lines, renamed files), the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it; if it does not exist, create it from origin/main.
2. Read docs/FIX_PLAN.md §0, §3, finding F3, task T06; then docs/SESSION_STATE.md and
   every file under "Read first" in T06.
3. T05 must be DONE or OWNER CHECK in the status board; otherwise stop and
   report in Burmese which task is missing.
4. Starting state, before any edit (Notion sandbox: `source /data/yft-env.sh` first and stop
   stale daemons with pkill -f "[G]radleDaemon"):
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
5. Set T06 to IN PROGRESS in the FIX_PLAN status board.

WORK — do the Steps of T06 in order; stay inside the task (anything else -> FIX_PLAN §9).
- DRM only when is_drm_protected is true, or drm_info (a JSON string) has a non-empty
  video_license_uri_map or a non-null graph_api_video_license_uri
  (FacebookPageParser.kt:253-256 today flags an empty map). Unreadable drm_info -> details line.
- Sanitized fixture from a live public reel: only the JSON blocks the parser reads, CDN query
  strings -> REDACTED, no cookies, no user data beyond the public title.
- /share/v/{code} and /share/r/{code}: follow redirects (T05 headers) to /reel/{id} or
  /watch/?v= and accept the resolved id.
- Decode HTML entities (decimal, hex, basic named) in title/owner; trim trailing ' | Facebook'.
- Labels '720p · HD' / '360p · SD' only when DASH or a probe gives the height; never invent one.
- LOGIN_REQUIRED only for real login walls seen with T05's identity.

TESTS (a regression test must fail on the old code)
- Public reel -> HD, SD and DASH; licence map present -> DRM_PROTECTED; entity decoding;
  share redirect through a fake client.

LIVE CHECK (public pages only; never paste bodies, cookies or signed URLs)
- Home lookup of the owner's link (or live-check.sh + a parser run on the live page) finds
  HD and SD; a ranged GET of the SD file returns 206 (print the status only).

VALIDATE (report only what you ran)
   ./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug
   New Kotlin lines must stay within 100 characters (this must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/SUPPORT_MATRIX.md (Facebook row: field result and fix), docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased] (Fixed), the FIX_PLAN status board
   (T06 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task T06, next action T07).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   test command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :extractor-sites:test :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T06: Facebook public reels without sign-in"
3. Check CI for the pushed commit (FIX_PLAN §0.3). Fix a red run before reporting.
4. Report to the owner in Burmese with the FIX_PLAN §0.5 template, including the run link for
   the yft-debug-apk artifact and this owner check:
   paste the Facebook link on Home -> found -> download -> it plays.
   Then stop. Do not start T07.
```
