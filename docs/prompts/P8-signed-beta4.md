# P8 — Signed release 1.0.0-beta.4

**ရည်ရွယ်ချက်:** Phase 13 (P27–P33) ပြီးလို့ owner က **Preview #5** ကို ဖုန်းမှာ စမ်းပြီး stable ဖြစ်ကြောင်း
OK ပေးမှ release key နဲ့ signed `1.0.0-beta.4` ထုတ်မယ်။

**အချက်အလက်:** Phase 13 · Easy · AI agent အချိန် 1–2 နာရီ · လိုအပ်ချက်: P33 + Preview #5 + owner OK

**သုံးနည်း:** Owner OK ရပြီဆိုမှ အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပြီး
`OWNER ANSWERS: P8=OK` နဲ့ `ALLOW_MERGE_MAIN: true`၊ `ALLOW_RELEASE: true` ပြောင်းပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P8](../FIX_ADD_PLAN.md#p8--signed-release-100-beta4)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P8 — Signed release 1.0.0-beta.4
Branch: work/phase-13-integration
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false   (true only after the owner's OK for P8)
ALLOW_RELEASE: false      (true only after the owner's OK for P8: release-key signing)
OWNER ANSWERS: none       (P8=OK after the owner's phone test of Preview #5)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the task's Result.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   git switch work/phase-13-integration && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0, 1, 3 and task P8, docs/RELEASE.md, then
   docs/SESSION_STATE.md. P33 must be DONE and Preview #5 sent.
3. Stop and report in Burmese unless the owner has written OK for P8 after his test of
   Preview #5 (OWNER ANSWERS or FIX_ADD_PLAN section 3); record the OK there with the date.
4. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first; stop only Gradle daemons you started; full
   validation with 640 MiB metaspace. Push with SSH: if /data/.ssh/id_ed25519 is missing make a
   NEW key (never search for old ones), show the owner the public line and wait for his OK. Starting state: the full
   validation below.
5. Set P8 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK
- gradle.properties: yft.versionName=1.0.0-beta.4, yft.versionCode=4; CHANGELOG section
  "## [1.0.0-beta.4] - <date>" under an empty Unreleased; docs/release/1.0.0-beta.4.md
  (template: docs/release/1.0.0-beta.3.md; phone checklist = FIX_ADD_PLAN section 6).
- Full validation; checkpoint; CI green.
- git fetch origin; git merge-base --is-ancestor origin/main HEAD (else stop and report);
  git push origin HEAD:main; git tag -a v1.0.0-beta.4 -m "YFT 1.0.0-beta.4";
  git push origin v1.0.0-beta.4. Watch release-draft.yml; record APK size, SHA-256 and
  certificate (must equal beta.3's) in HANDOFF, TEST_MATRIX and the P8 Result.
- Publishing stays with the owner (Releases > draft > Publish release).
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs, keystores or passwords; never undo
work with git reset --hard, git clean or git stash.

TESTS
- The release workflow repeats lint, unit tests, the signed build and APK verification.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug
   ./gradlew --no-daemon :app:assembleRelease
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Update README.md, docs/HANDOFF.md, docs/PHASE_STATUS.md (Phases 11, 12 and 13 COMPLETE),
   docs/SUPPORT_MATRIX.md, docs/TEST_MATRIX.md, CHANGELOG.md, the P8 Result and status in
   FIX_ADD_PLAN (P8 -> DONE (date)) and docs/SESSION_STATE.md (last task P8, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P8: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the release draft link and
   this owner check: install the signed beta.4 over beta.3 (keeps settings and downloads) and
   run FIX_ADD_PLAN section 6. Then stop: ask the owner what comes next.
```
