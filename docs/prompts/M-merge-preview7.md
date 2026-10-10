# P44 — Merge B → C → A and Preview #7 (integrator: Agent B)

**ရည်ရွယ်ချက်:** Agent B၊ C၊ A သုံးယောက်ရဲ့ branch တွေကို `work/phase-15-integration` ထဲ အစဉ်လိုက်
(B → C → A) ပေါင်းပြီး full validation စစ်၊ CI အစိမ်းဖြစ်ရင် **Preview #7** APK link နဲ့ ဖုန်းမှာ
စစ်ရမယ့်စာရင်းကို owner ဆီ ပို့မယ်။ `main` နဲ့ signed release ကို owner OK မပေးမချင်း မထိပါ။

**အချက်အလက်:** Phase 15 · **Agent B (integrator)** · Medium · AI agent အချိန် 2–3 နာရီ · လိုအပ်ချက်:
A၊ B၊ C သုံးယောက်လုံး `READY FOR MERGE` (သူတို့ report မှာ ပြောပါတယ်)

**သုံးနည်း:** A၊ B၊ C သုံးယောက်လုံး `READY FOR MERGE` ဖြစ်ပြီဆိုမှ Agent B ရဲ့ chat ထဲ (သို့ chat အသစ်)
code block ကို paste လုပ်ပါ။ Preview #7 စမ်းပြီး `main` ကို update လုပ်စေချင်ရင်
`OWNER ANSWERS: MAIN=OK` ထည့်ပါ။ Signed beta.4 က [`P8-signed-beta4.md`](P8-signed-beta4.md) ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P44](../FIX_ADD_PLAN.md#p44--merge-and-preview-7)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, Android WebView, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: B as integrator
Task: P44 — Merge B -> C -> A, full validation, Preview #7
Branch: work/phase-15-integration
Merges (in this order): origin/work/phase-15-downloads (B; two-agent mode: C's work is already
  on it), origin/work/phase-15-ads (C), origin/work/phase-15-tiktok (A)
ALLOW_PUSH: true          (checkpoint pushes to work/phase-15-integration only)
ALLOW_MERGE_MAIN: false   (MAIN=OK in OWNER ANSWERS allows only a fast-forward of main to the
                           validated merge after the owner's Preview #7 test)
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: MAIN=OK)

The repository is the source of truth; do not rely on chat history.

START
1. Work in your own folder /data/YFT-B (Agent B's prompt, "COMPUTER, FOLDER AND PUSH": SSH
   push with the key named in /data/.ssh/CURRENT_KEY; if it is missing or refused make a NEW
   key, never search for old ones, show the owner the public line and wait for his OK).
   Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   git switch work/phase-15-integration && git pull --ff-only
   (first time in this folder: git switch -c work/phase-15-integration origin/work/phase-15-integration)
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 1 and P44, then the agent sections of
   docs/SESSION_STATE.md on each agent branch:
   git show origin/<branch>:docs/SESSION_STATE.md
3. Gate: every agent section says READY FOR MERGE and its newest commit that changed code has
   green CI (checkpoint validation, emulator smoke, Preview APK; FIX_ADD_PLAN 0.3 query with the
   branch name). Docs-only commits after it have no runs (paths-ignore) — that is expected.
   Otherwise report in Burmese what is missing and stop.
4. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh`; stop only Gradle daemons you started; one Gradle
   command at a time; full validation with export GRADLE_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=640m".

WORK
1. git merge --no-ff origin/work/phase-15-downloads -m "P44: merge Agent B (P41, P42)"
2. git merge --no-ff origin/work/phase-15-ads -m "P44: merge Agent C (P43)"
   (two-agent mode: skip if C's work is already in B's branch)
3. git merge --no-ff origin/work/phase-15-tiktok -m "P44: merge Agent A (P39, P40)"
   Conflicts: shared docs (SESSION_STATE, CHANGELOG, TEST_MATRIX) -> keep both sides, each in its
   own section. QuickDownloadViewModel.kt is shared by rule (A changed only lookupState, C the
   rest): different hunks merge by themselves; a conflict inside lookupState -> keep A's
   lookupState with C's surrounding code. Any other conflict in code or in a file only one agent
   owns is an ownership slip: run git merge --abort, report the files in Burmese and stop.
   Hand-offs listed in the agents' sections that were not done: do the small ones (say so in
   the Result), list the rest.
   Check after the three merges: A's TikTok rows on an adapter page are not touched by C's ad
   rule (adapterSite), C's ad tests and A's TikTok tests both pass, B's engine and delete tests
   pass.
4. Full validation:
   ./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:compileDebugAndroidTestKotlin
   ./gradlew --no-daemon :app:assembleRelease
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'   (must print nothing)
   A failure: fix it in the smallest way on this branch (say which agent's area) or report it.
5. Docs (the integrator may edit them all now): docs/FIX_ADD_PLAN.md section 1 statuses and the
   P39-P43 Results copied from the agent sections, P44 Result; docs/SESSION_STATE.md Overview
   (last checkpoint, next action); CHANGELOG.md (fold the three "Phase 15 — Agent" sections into
   Added / Changed / Fixed under Unreleased); docs/HANDOFF.md; docs/PHASE_STATUS.md (Phase 15:
   merged, Preview #7 sent); docs/SUPPORT_MATRIX.md (from A's Results); docs/TEST_MATRIX.md (a
   Phase 15 summary row).
Rules: ADR-006; never log or commit cookies, tokens, signed URLs or keys; never undo work with
git reset --hard, git clean or git stash; do not edit .github/workflows/.

FINISH
1. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the full validation above>" bash scripts/checkpoint.sh "P44: merge B, C, A — full validation green"
2. CI for the pushed commit: checkpoint validation, emulator smoke (the new P39-P43 instrumented
   tests included) and Preview APK (test key). Fix a red run before reporting.
3. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5): the Preview #7 link (Preview APK run
   > Artifacts > yft-preview-apk; uninstall the older YFT Preview first) and the FIX_ADD_PLAN
   section 6 Preview #7 list. Then stop and wait for his phone test.
4. Only with MAIN=OK after his test: git fetch origin; git merge-base --is-ancestor origin/main
   HEAD (else stop and report); git push origin HEAD:main. No tag, no signed release: that is
   P8 (docs/prompts/P8-signed-beta4.md) with the owner's OK.
```
