# P38 — Merge A → B → C and Preview #6 (integrator: Agent A)

**ရည်ရွယ်ချက်:** Agent A၊ B၊ C သုံးယောက်ရဲ့ branch တွေကို `work/phase-14-integration` ထဲ အစဉ်လိုက်
(A → B → C) ပေါင်းပြီး full validation စစ်၊ CI အစိမ်းဖြစ်ရင် **Preview #6** APK link နဲ့ ဖုန်းမှာ
စစ်ရမယ့်စာရင်းကို owner ဆီ ပို့မယ်။ `main` နဲ့ signed release ကို owner OK မပေးမချင်း မထိပါ။

**အချက်အလက်:** Phase 14 · **Agent A (integrator)** · Medium · AI agent အချိန် 2–3 နာရီ · လိုအပ်ချက်:
A၊ B၊ C သုံးယောက်လုံး `READY FOR MERGE` (သူတို့ report မှာ ပြောပါတယ်)

**သုံးနည်း:** A၊ B၊ C သုံးယောက်လုံး `READY FOR MERGE` ဖြစ်ပြီဆိုမှ Agent A ရဲ့ chat ထဲ (သို့ chat အသစ်)
code block ကို paste လုပ်ပါ။ Preview #6 စမ်းပြီး `main` ကို update လုပ်စေချင်ရင်
`OWNER ANSWERS: MAIN=OK` ထည့်ပါ။ Signed beta.4 က [`P8-signed-beta4.md`](P8-signed-beta4.md) ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P38](../FIX_ADD_PLAN.md#p38--merge-and-preview-6)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: A as integrator
Task: P38 — Merge A -> B -> C, full validation, Preview #6
Branch: work/phase-14-integration
Merges (in this order): origin/work/phase-14-background (A), origin/work/phase-14-sites (B),
  origin/work/phase-14-fast-merge (C; two-agent mode: C's work is already on A's branch)
ALLOW_PUSH: true          (checkpoint pushes to work/phase-14-integration only)
ALLOW_MERGE_MAIN: false   (MAIN=OK in OWNER ANSWERS allows only a fast-forward of main to the
                           validated merge after the owner's Preview #6 test)
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: MAIN=OK)

The repository is the source of truth; do not rely on chat history.

START
1. Work in your own folder /data/YFT-A (Agent A's prompt, "COMPUTER, FOLDER AND PUSH": SSH
   push; if /data/.ssh/id_ed25519 is missing make a NEW key, never search for old ones, show
   the owner the public line and wait for his OK).
   Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   git switch work/phase-14-integration && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 1 and P38, then the agent sections of
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
1. git merge --no-ff origin/work/phase-14-background -m "P38: merge Agent A (P34)"
2. git merge --no-ff origin/work/phase-14-sites -m "P38: merge Agent B (P36, P37)"
3. git merge --no-ff origin/work/phase-14-fast-merge -m "P38: merge Agent C (P35)"
   Conflicts: shared docs (SESSION_STATE, CHANGELOG, TEST_MATRIX) -> keep both sides, each in its
   own section. A conflict in code or in a file only one agent owns is an ownership slip: run
   git merge --abort, report the files in Burmese and stop. Hand-offs listed in the agents'
   sections that were not done: do the small ones (say so in the Result), list the rest.
   Check after the three merges: A's background tests and C's merge tests both pass on the
   emulator (a stream-copy merge keeps running after Home), and B's TikTok and P37 tests pass.
4. Full validation:
   ./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:compileDebugAndroidTestKotlin
   ./gradlew --no-daemon :app:assembleRelease
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'   (must print nothing)
   A failure: fix it in the smallest way on this branch (say which agent's area) or report it.
5. Docs (the integrator may edit them all now): docs/FIX_ADD_PLAN.md section 1 statuses and the
   P34-P37 Results copied from the agent sections, P38 Result; docs/SESSION_STATE.md Overview
   (last checkpoint, next action); CHANGELOG.md (fold the three "Phase 14 — Agent" sections into
   Added / Changed / Fixed under Unreleased); docs/HANDOFF.md; docs/PHASE_STATUS.md (Phase 14:
   merged, Preview #6 sent); docs/SUPPORT_MATRIX.md (from B's Results); docs/TEST_MATRIX.md (a
   Phase 14 summary row).
Rules: ADR-006; never log or commit cookies, tokens, signed URLs or keys; never undo work with
git reset --hard, git clean or git stash; do not edit .github/workflows/.

FINISH
1. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the full validation above>" bash scripts/checkpoint.sh "P38: merge A, B, C — full validation green"
2. CI for the pushed commit: checkpoint validation, emulator smoke (the new P34, P35, P36 and P37
   instrumented tests included) and Preview APK (test key). Fix a red run before reporting.
3. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5): the Preview #6 link (Preview APK run
   > Artifacts > yft-preview-apk; uninstall the older YFT Preview first) and the FIX_ADD_PLAN
   section 6 Preview #6 list. Then stop and wait for his phone test.
4. Only with MAIN=OK after his test: git fetch origin; git merge-base --is-ancestor origin/main
   HEAD (else stop and report); git push origin HEAD:main. No tag, no signed release: that is
   P8 (docs/prompts/P8-signed-beta4.md) with the owner's OK.
```
