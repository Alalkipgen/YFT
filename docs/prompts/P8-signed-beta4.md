# P8 — Signed release 1.0.0-beta.4

**ရည်ရွယ်ချက်:** Owner က preview APK ကို စမ်းပြီး stable ဖြစ်ကြောင်း OK ပေးမှ release key နဲ့ signed 1.0.0-beta.4 ထုတ်မယ်။

**အချက်အလက်:** Phase 11 · Easy · AI agent အချိန် 1–2 နာရီ · လိုအပ်ချက်: P7 + owner OK

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P8](../FIX_ADD_PLAN.md#p8--signed-release-100-beta4)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P8 — Signed release 1.0.0-beta.4
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false   (true only after the owner's OK for P8)
ALLOW_RELEASE: false      (true only after the owner's OK for P8: release-key signing)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P8 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   full validation (FIX_ADD_PLAN 0.3)
4. Set P8 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P8 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Stop unless the owner has written OK for P8 (record it in FIX_ADD_PLAN section 3 with the date).
- gradle.properties: yft.versionName=1.0.0-beta.4, yft.versionCode=4; CHANGELOG section
  "## [1.0.0-beta.4] - <date>" under an empty Unreleased; docs/release/1.0.0-beta.4.md
  (template: docs/release/1.0.0-beta.3.md; phone checklist = FIX_ADD_PLAN section 6).
- Full validation; checkpoint; CI green.
- git fetch origin; git merge-base --is-ancestor origin/main HEAD (else stop and report);
  git push origin HEAD:main; git tag -a v1.0.0-beta.4 -m "YFT 1.0.0-beta.4";
  git push origin v1.0.0-beta.4. Watch release-draft.yml; record APK size, SHA-256 and
  certificate (must equal beta.3's) in HANDOFF, TEST_MATRIX and FIX_ADD_PLAN section 8.
- Publishing stays with the owner (Releases > draft > Publish release).
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- The release workflow repeats lint, unit tests, the signed build and APK verification.

VALIDATE (report only what you ran)
   full validation (FIX_ADD_PLAN 0.3)
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update README.md, docs/HANDOFF.md, docs/PHASE_STATUS.md (Phase 11 COMPLETE), docs/SUPPORT_MATRIX.md, docs/TEST_MATRIX.md, CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P8 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P8, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P8: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: install the signed beta.4 over beta.3 (keeps settings and downloads) and run FIX_ADD_PLAN section 6.
   Then stop: Phase 11 is finished; ask the owner what comes next.
```
