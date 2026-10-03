# T19 — Signed release 1.0.0-beta.3

**ရည်ရွယ်ချက်:** Phase 8–10 ပြီးရင် (T10 နဲ့ T15 ကို owner က skip ခိုင်းထား) branch ကို `main` ထဲ merge၊
push၊ tag လုပ်ပြီး signing key နဲ့ release APK ထုတ်ပါမယ်။ Owner ခွင့်ပြုပြီး (2026-10-03)။

**အချက်အလက်:** Phase 10 · Easy · ၀.၅ ရက် · လိုအပ်ချက်: T09 နဲ့ T11–T18 ပြီးရမယ် (DONE / OWNER CHECK /
SKIPPED)

**သုံးနည်း:** code block တစ်ခုလုံးကို paste လုပ်ပါ။ Merge၊ tag နဲ့ signed release ကို owner က ကြိုခွင့်ပြုထားပြီး
ဖြစ်လို့ agent က validation အစိမ်းဖြစ်ရင် အဆုံးထိ လုပ်ပါမယ် (`docs/RELEASE.md` §3–§5)။

အသေးစိတ်: [`docs/FIX_PLAN.md` › T19](../FIX_PLAN.md#t19--signed-release-100-beta3)

```text
You are the release agent for YFT, an ad-free Android video downloader.

Repository: https://github.com/Alalkipgen/YFT
Task: T19 — Signed release 1.0.0-beta.3 (owner change 2026-10-03: T10 and T15 skipped)
Branch: work/phase-8-field-fixes   (owner change 2026-10-03)
Version: 1.0.0-beta.3, versionCode 3 (previous release: 1.0.0-beta.2)
ALLOW_PUSH: true          (checkpoint pushes to this branch)
ALLOW_MERGE_MAIN: true    (owner-approved 2026-10-03 for T19: merge, push, tag)
ALLOW_RELEASE: true       (owner-approved 2026-10-03 for T19: the signed release APK)

The repository is the source of truth; do not rely on chat history.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-8-field-fixes and pull it (owner change 2026-10-03: nothing is merged
   or released before T19, so every task stays on this branch).
2. Read docs/FIX_PLAN.md §0, §1, §3, §8 and task T19; docs/RELEASE.md (all);
   docs/release/1.0.0-beta.2.md (template for the notes); docs/SESSION_STATE.md.
3. T09 and T11–T18 must each be DONE, OWNER CHECK or SKIPPED in the status board. Otherwise stop
   and report in Burmese what is missing. CI must be green for the branch head.
4. Set T19 to IN PROGRESS.

PREPARE
1. gradle.properties: yft.versionName=1.0.0-beta.3, yft.versionCode=3.
2. CHANGELOG.md: rename ## [Unreleased] to ## [1.0.0-beta.3] - <today>, keep an empty
   ## [Unreleased] above it.
3. docs/release/1.0.0-beta.3.md: fixes and features of Phases 8–10, known issues, the three
   FIX_PLAN §8 checklists, install notes (installs over 1.0.0-beta.2; same signing key).
4. Update README.md (status), docs/HANDOFF.md, docs/PHASE_STATUS.md (phases 8–10 COMPLETE),
   docs/TEST_MATRIX.md, docs/SUPPORT_MATRIX.md, the FIX_PLAN status board and
   docs/SESSION_STATE.md.
5. Full validation, as separate Gradle calls on a small machine (Notion sandbox:
   `source /data/yft-env.sh` first):
   ./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug
   ./gradlew --no-daemon :app:assembleRelease
   bash scripts/verify-release-apk.sh --allow-unsigned --expected-version 1.0.0-beta.3 app/build/outputs/apk/release/app-release-unsigned.apk
6. Phase-completion checkpoint:
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T19: Phases 8-10 complete - 1.0.0-beta.3 ready"
   Check CI (FIX_PLAN §0.3) until it is green.

MERGE AND TAG — approved by the owner (2026-10-03); still only after green validation and CI
1. git fetch origin; git merge-base --is-ancestor origin/main HEAD (if main moved, stop and
   report); git push origin HEAD:main.
2. git tag -a v1.0.0-beta.3 -m "YFT 1.0.0-beta.3"; git push origin v1.0.0-beta.3.
3. The tag runs .github/workflows/release-draft.yml, which builds the signed APK and creates the
   draft pre-release (docs/RELEASE.md §3). Watch the run through the Actions API until it ends
   and report its result; record the APK size, SHA-256 and certificate that the run reports.
4. Set T19 to DONE (date) and checkpoint the record on the branch.
5. Never publish unless ALLOW_RELEASE is true; then follow docs/RELEASE.md §5 exactly.

REPORT in Burmese with the FIX_PLAN §0.5 template: validation results, commits, CI, the draft
release (or the approval question), the §8 checklist for the owner's phone. Then stop; the
next task is none: the plan is finished; ask the owner.
```
