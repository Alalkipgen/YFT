# P7 — Preview APK for the owner's test

**ရည်ရွယ်ချက်:** Owner စမ်းဖို့ release build ကို test key နဲ့ ထုတ်မယ် (com.alal.yft.preview)။ Signature လုံးဝမပါတဲ့ APK ကို Android က install မပေးလို့ပါ။ beta.3 ဘေးမှာ သီးသန့် install ရမယ်။

**အချက်အလက်:** Phase 11 · Easy · AI agent အချိန် 1–2 နာရီ · လိုအပ်ချက်: P1–P6

**သုံးနည်း:** အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P7](../FIX_ADD_PLAN.md#p7--preview-apk)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3).

Repository: https://github.com/Alalkipgen/YFT
Task: P7 — Preview APK for the owner's test
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt and correct the plan.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   Check out work/phase-11-download-flow and pull it.
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and task P7 (with its "Read first" files),
   then docs/SESSION_STATE.md.
3. Starting state before any edit (Notion sandbox: `source /data/yft-env.sh` first; stop stale
   daemons with pkill -f "[G]radleDaemon"):
   full validation (FIX_ADD_PLAN 0.3)
4. Set P7 to IN PROGRESS in the FIX_ADD_PLAN status board.

WORK — the Steps of P7 in order; anything outside the task -> FIX_ADD_PLAN section 7.
- Add a "preview" build type: initWith(release), minified, not debuggable, applicationId
  suffix ".preview", signed with a test key generated inside CI (never the release key, never
  committed).
- CI uploads the artifact yft-preview-apk with its SHA-256; scripts/verify-release-apk.sh
  checks it (version, not debuggable, no debug crash action).
- Run the full validation (FIX_ADD_PLAN 0.3).
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed URLs or keys; WebView calls on the main
thread; Kotlin lines within 100 characters; keep every testTag.

TESTS (a regression test must fail on the old code)
- Build-logic/script tests for the preview variant (app ID, signing source, artifact name).

VALIDATE (report only what you ran)
   full validation (FIX_ADD_PLAN 0.3)
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | awk 'length > 101'

FINISH
1. Update docs/RELEASE.md (preview builds), docs/TEST_MATRIX.md, README.md (install), CHANGELOG.md ## [Unreleased], the FIX_ADD_PLAN
   status board (P7 -> DONE (date), or OWNER CHECK when only the phone check is left) and
   docs/SESSION_STATE.md (last task P7, next action).
2. Keep temporary files outside the repository, then checkpoint (Notion sandbox: start the
   command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P7: <summary>"
3. Check CI for the pushed commit (FIX_ADD_PLAN 0.3); fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the yft-debug-apk run link and
   this owner check: install yft-preview-apk next to beta.3 and run FIX_ADD_PLAN section 6.
   Then stop: the owner tests the preview APK. P8 starts only after his OK.
```
