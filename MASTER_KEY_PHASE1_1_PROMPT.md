# Master Key — Phase 1.1 agent prompt: own YouTube n/sig solver (S1–S6)

**ရည်ရွယ်ချက်:** `MASTER_KEY_PHASE1_1_PLAN.md` ထဲက S1–S6 ကို agent တစ်ယောက်က တစ်ဆင့်ချင်း လုပ်ဖို့ prompt ပါ။ ကိုယ်ပိုင် solver (option A) ကို ယေဘုယျနည်း ၄ ခု + ကိုယ့်ဘာသာ ပြန်စစ်တာနဲ့ အစကတည်းက ရေးမှာပါ။

**သုံးနည်း:**
- Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။
- Phase 1 ရဲ့ R1 (base sync) နဲ့ R6 (YouTube module) ပြီးမှ S5 ကို လုပ်လို့ရပါတယ်။ S1–S4 ကိုတော့ R1 ပြီးတာနဲ့ စလို့ရပါတယ်။
- Task တစ်ခု ပြီးတိုင်း push လုပ်ပြီး မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်လုပ်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
Plan: MASTER_KEY_PHASE1_1_PLAN.md (read it first; context: MASTER_KEY_PHASE1_PLAN.md R6,
      MASTER_KEY_NOTES.md sections 1.3 and 8)
ALLOW_PUSH: true          (checkpoint pushes to the spike branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
START_AT: S1              (owner may change)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the report.

GOAL
Write YFT's own YouTube n/sig solver core (option A) for the Master YouTube module, so the
own-solver path uses no third-party extractor. Build it general from the start:
  G1 run the player's whole top-level code in the sandbox and try every candidate URL-transform
     call (multiTry); never rely on extracting one internal function
  G2 structure patterns with alternatives + AST normalization (var/let/const, function/arrow,
     sequence expressions, computed/dotted members)
  G3 encoding-aware application (encodeURIComponent) with edge-case vectors
  G4 full browser-like globals in the worker (window = self = globalThis, location, navigator,
     document stub), as own properties so nothing reaches the host page
plus SELF-VERIFICATION: all candidates must agree, vectors must pass, n output must differ from
its input and be URL-safe; otherwise FAIL and drop the stream. Never guess.

HARD RULES (never break)
1. Flag off = main. New flag -Pyft.ownSolver (default false) and -Pyft.masterCapture (default
   false). The own runner is used only when both are on. CI and MasterMainMoreSheetTest
   (flag-off case) stay green after every task.
2. Copy, never move. Do not change main's YouTubePlayerScriptRunner, EjsSolverProtocol,
   WebViewSolverEngine or app/src/main/assets/youtube-solver/. Add new code under Master and
   plug in through the existing PlayerScriptRunner interface.
3. No third-party extractor on the own-solver path. Allowed general libraries: meriyah (ISC,
   parser) and astring (MIT, code generator), with THIRD_PARTY_NOTICES entries. yt-dlp/ejs
   (Unlicense) is an idea/structure reference only: copy no file. Option B (own JS parser) is
   out of scope.
4. Sandbox only: the player runs with no network, no cookies, no storage (existing SolverEngine
   worker model) and never navigates the host page.
5. No bypass (bot check, login, age gate, DRM); those stay terminal. No fake rows: a value that
   fails SelfCheck drops its stream. SABR-only formats are never rows.
6. Never commit YouTube's player script text. Store player IDs and hashes; download at test
   time. Never log solver inputs or outputs (they are session secrets); log only keys/indexes.
7. No code is ever loaded from a remote source.

TASKS (details, files and done criteria: MASTER_KEY_PHASE1_1_PLAN.md section 3)
S1  Harness: vectors (scripts/youtube-solver-vectors.json, 7 players), a player corpus (main,
    tce, es6, phone variants; IDs + hashes), a parity runner own-vs-ejs next to
    scripts/verify-youtube-solver.mjs.
S2  Core: meriyah parse, G1 unwrap (known wrappers only; unknown -> FAIL "unexpected
    structure"), G2 normalize for matching, collect ALL candidates for n and sig.
S3  G4 environment prelude + G1 multiTry generators run in the existing worker on a test URL.
S4  G3 encoding + SelfCheck (agreement, vectors, shape, optional 1-byte probe live only);
    injected-fault tests (wrong candidate, broken encoding, missing global) must FAIL, never
    produce a wrong value.
S5  OwnPlayerScriptRunner : PlayerScriptRunner (fetch player like main's runner, memory cache
    per player ID, failures -> PlayerScriptResult.Failed(PLAYER_SCRIPT_REQUIRED)); flag
    selection; unit tests with a fake engine. Needs Phase 1 R6.
S6  Owner-run canary (phone/emulator, opt-in: today's player, own vs ejs) and the runbook
    (plan section 6).

PER TASK
- Before coding: read the task in the plan; list the files you will touch.
- Tests first where possible (committed vectors/fixtures; no live calls in unit tests).
- Build and run the CI test set locally; keep the flag-off gate green.
- Checkpoint-push to spike/master-extractor-backup with a clear message (for example
  "feat(master): S2 own solver core with G1 unwrap and G2 candidates").
- Update the status board in MASTER_KEY_PHASE1_1_PLAN.md (TODO -> DONE, with the commit sha).
- Report to the owner in short Burmese with ✅/❌: what changed, parity numbers (own vs ejs),
  test results, commit sha, risks. Then STOP and wait for "Next".

STOP AND ASK THE OWNER WHEN
- Parity with ejs is below 100 % on the corpus and the cause is not a known class (G1–G4).
- A task would need copying ejs files, a GPL/AGPL source, an own JS parser, or any bypass.
- A change would alter main's behaviour with the flags off.
- A push fails (a local commit is not a handoff).
```
