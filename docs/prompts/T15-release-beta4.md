# T15 — Release 1.0.0-beta.4

> **SKIPPED — owner ပြောင်းခိုင်းချက် (2026-10-03).** ဒီ release ကို မထုတ်တော့ပါ။ Release တစ်ခုတည်းကို
> T19 ([`T19-release-beta5.md`](T19-release-beta5.md)) မှာပဲ main merge + tag + signing key နဲ့ ထုတ်ပါမယ်။
> English: T15 is SKIPPED by the owner; do not run this prompt. The only release is T19.

**ရည်ရွယ်ချက်:** Phase 9 ပြီးရင် 1.0.0-beta.4 ကို ပြင်ဆင်ပြီး signed draft release ထုတ်ပါမယ်။ main ကို merge လုပ်ဖို့နဲ့ tag တင်ဖို့ owner ခွင့်ပြုချက် (`ALLOW_MERGE_MAIN: true`) လိုပါတယ်။

**အချက်အလက်:** Phase 9 · Easy · ၀.၅ ရက် · လိုအပ်ချက်: T11–T14 ပြီးရမယ် (DONE / OWNER CHECK / SKIPPED)

**သုံးနည်း:**
- ဒီအတိုင်း paste လုပ်ရင် agent က release ပြင်ဆင်မှု (version, changelog, notes, full test) ကို
  phase-completion commit အထိ လုပ်ပြီး merge ခွင့်ပြုချက် တောင်းပါမယ်။
- Merge + tag + signed draft ပါ တစ်ခါတည်း လုပ်စေချင်ရင် `ALLOW_MERGE_MAIN: true` လို့ ပြောင်းပြီး paste လုပ်ပါ။
- Draft ကို public အဖြစ် publish လုပ်တာကို ဖုန်းမှာ စစ်ပြီးမှ ကိုယ်တိုင်လုပ်ပါ (`docs/RELEASE.md` §4–§5)။

အသေးစိတ်: [`docs/FIX_PLAN.md` › T15](../FIX_PLAN.md#t15--release-100-beta4)

```text
You are the release agent for YFT, an ad-free Android video downloader.

Repository: https://github.com/Alalkipgen/YFT
Task: T15 — Release 1.0.0-beta.4
Branch: work/phase-9-copied-link-flow
Version: 1.0.0-beta.4, versionCode 4 (previous: 1.0.0-beta.3)
ALLOW_PUSH: true          (checkpoint pushes to this branch)
ALLOW_MERGE_MAIN: false   (the owner sets true to approve the merge and the tag)
ALLOW_RELEASE: false      (publishing; only the owner sets this)

The repository is the source of truth; do not rely on chat history.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-9-copied-link-flow and pull it. If it does not exist, create it from origin/main,
   but only when `git merge-base --is-ancestor v1.0.0-beta.3 origin/main` succeeds; otherwise
   stop and report that the previous release is not merged yet.
2. Read docs/FIX_PLAN.md §0, §1, §3, §8 and task T15; docs/RELEASE.md (all);
   docs/release/1.0.0-beta.3.md (template for the notes); docs/SESSION_STATE.md.
3. T11–T14 must each be DONE, OWNER CHECK or SKIPPED in the status board. Otherwise stop
   and report in Burmese what is missing. CI must be green for the branch head.
4. Set T15 to IN PROGRESS.

PREPARE
1. gradle.properties: yft.versionName=1.0.0-beta.4, yft.versionCode=4.
2. CHANGELOG.md: rename ## [Unreleased] to ## [1.0.0-beta.4] - <today>, keep an empty
   ## [Unreleased] above it.
3. docs/release/1.0.0-beta.4.md: fixes and features of the phase, known issues, the FIX_PLAN §8
   checklist for this beta, install notes (installs over 1.0.0-beta.3; same signing key).
4. Update README.md (status), docs/HANDOFF.md, docs/PHASE_STATUS.md (phase 9 COMPLETE),
   docs/TEST_MATRIX.md, docs/SUPPORT_MATRIX.md, the FIX_PLAN status board and
   docs/SESSION_STATE.md.
5. Full validation, as separate Gradle calls on a small machine (Notion sandbox:
   `source /data/yft-env.sh` first):
   ./gradlew --no-daemon --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug
   ./gradlew --no-daemon :app:assembleRelease
   bash scripts/verify-release-apk.sh --allow-unsigned --expected-version 1.0.0-beta.4 app/build/outputs/apk/release/app-release-unsigned.apk
6. Phase-completion checkpoint:
   CHECKPOINT_TEST_COMMAND="./gradlew --no-daemon -q :app:testDebugUnitTest :app:lintDebug" \
     bash scripts/checkpoint.sh "T15: Phase 9 complete - 1.0.0-beta.4 ready"
   Check CI (FIX_PLAN §0.3) until it is green.

MERGE AND TAG — only when ALLOW_MERGE_MAIN is true; otherwise report and ask for approval
1. git fetch origin; git merge-base --is-ancestor origin/main HEAD (if main moved, stop and
   report); git push origin HEAD:main.
2. git tag -a v1.0.0-beta.4 -m "YFT 1.0.0-beta.4"; git push origin v1.0.0-beta.4.
3. The tag runs .github/workflows/release-draft.yml, which builds the signed APK and creates the
   draft pre-release (docs/RELEASE.md §3). Watch the run through the Actions API until it ends
   and report its result; record the APK size, SHA-256 and certificate that the run reports.
4. Set T15 to DONE (date) and checkpoint the record on the branch.
5. Never publish unless ALLOW_RELEASE is true; then follow docs/RELEASE.md §5 exactly.

REPORT in Burmese with the FIX_PLAN §0.5 template: validation results, commits, CI, the draft
release (or the approval question), the §8 checklist for the owner's phone. Then stop; the
next task is T16 (Phase 10; needs D2).
```
