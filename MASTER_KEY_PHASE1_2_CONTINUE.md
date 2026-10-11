# Master Key — Phase 1.2 continuation prompt (after N1 and N2)

> **N3 ပြီးပါပြီ (done).** N4 ကနေ ဆက်လုပ်ရန် `MASTER_KEY_PHASE1_2_CONTINUE_N4.md` ကို သုံးပါ။

**ရည်ရွယ်ချက်:** Phase 1.2 ကို agent အသစ်တစ်ယောက်က ဆက်လုပ်ဖို့ prompt ပါ။ N1 (mutation class 14 ခုနဲ့ file 32 ခုလုံးရဲ့
baseline) နဲ့ N2 (YouTube နဲ့ စစ်တဲ့ `StreamCheck`) ပြီးပါပြီ။ ကျန်တာက N3–N7 ပါ။ N2 ရဲ့ ဖုန်းတိုင်းတာမှု
(`bash scripts/stream-check-measure.sh`) ကို owner က တစ်ခါ run ပေးဖို့ ကျန်ပါတယ်။

**သုံးနည်း:**
- Agent chat အသစ်ထဲ အောက်က code block ကို paste လုပ်ပါ။ အပြည့်အစုံ စည်းကမ်းတွေက `MASTER_KEY_PHASE1_2_PROMPT.md`
  ထဲမှာ ရှိပြီး ဒီ prompt က အဲဒါကို ဖတ်ခိုင်းပါတယ်။
- N3 နဲ့ N4 မှာ Reasoning Level အမြင့်ဆုံး သုံးပါ (N3 ကို session အသစ်နဲ့ စပါ)။
- Task တစ်ခုပြီးတိုင်း push လုပ်ပြီး မြန်မာလို report ပေးကာ ရပ်ပါမယ်။ ဆက်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
FIRST read, in this order:
  1. MASTER_KEY_PHASE1_2_PROMPT.md   (goal, HARD RULES 1-9, T1-T4, per-task routine, stop rules;
                                      all of it applies here unchanged)
  2. MASTER_KEY_PHASE1_2_PLAN.md     (sections 1-6; the status board in section 3; the baseline
                                      table in section 4)
  3. PROGRESS.md top sections "Phase 1.2 — N2 ..." and "Phase 1.2 — N1 ..."
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
START_AT: N3 (strategy B; use the highest reasoning level, fresh session), then N4
  (or Phase 1.3 D1 if the owner chooses; D4 reuses N2's StreamCheck)

STATE WHEN THIS PROMPT WAS WRITTEN (verify against the repo; the repo wins)
- Phase 1.1 S1-S6 done. Corpus parity: 32/32 files, own = ejs on 260/260 inputs.
- N1 done (commit 765e91f, CI run 38113204972 green): scripts/own-solver/mutate.mjs (scope model T3 + 14 classes: M1, M1w, M2, M3, M4,
  M5, M6, M7, M8, M8t, M9, M10, M11, M11f), scripts/verify-own-solver-mutations.mjs (--quick,
  --only, --class, --seeds; lines show which kind failed and own P's failure class), unit tests
  scripts/own-solver/tests/mutate.test.mjs (47, in the spike CI JS step; the whole step 129).
- Baseline (32 files x 14 classes = 448 cases; wrong = 0 everywhere; survived files of 32,
  ejs | own P): M1, M1w, M2, M4, M8, M10, M11, M11f 32 | 32; M3 (new wrapper) 0 | 0, both fail
  safely; M5 24 | 32; M6 0 | 32; M7 11 | 31 (own P: 5203c085 es6 "no candidates"); M8t 0 | 32;
  M9 0 | 32. All: ejs 65 %, own P 93 %. Exact numbers and reading: plan section 4.

HOW N1 GETS ITS TRUTH (do not change this without asking the owner)
- Truth = ejs on the ORIGINAL player: the right n/sig depends on the input, not on how the code
  is written. Every solve goes through scripts/own-solver/harness.mjs (bare V8 context: no
  require, process or network), exactly like scripts/verify-own-solver.mjs.
- Never write another way to execute player code (a custom sandbox, a Proxy global, calling a
  tagged builder directly, worker dev scripts). Such scripts were rejected by the automated
  safety review several times in the previous session. Static parse-only inspection (meriyah
  AST, no execution) is fine.
- A mutation that changed behaviour (ejs and own agree on a new value) is skipped, never counted
  as wrong; each mutator first gets unit tests on small hand-written programs (behaviour equal
  before/after in a bare vm context).

- N2 done (commit 9e15101, CI run 38117539688 green): extractor-master/.../modules/youtube/StreamCheck.kt
  (one-byte probe = the bounded Range read; PASS/FAIL/UNKNOWN; control; 4 checks per player
  version per UTC day; cache of proven verdicts; no cookies; nothing logged; n rule PENDING /
  JUDGES_N / SIG_ONLY) + StreamCheckTest (13). Not wired yet: N5 wires it behind ownSolverNext.
  Phone measurement StreamCheckMeasureTest + scripts/stream-check-measure.sh: PENDING until the
  owner runs it; then write the result and the chosen n rule into plan section 2.

NEXT STEPS
N3 onward: exactly as MASTER_KEY_PHASE1_2_PROMPT.md TASKS (N2 live check V with the owner's
  phone measurement (done, measurement pending), N3 strategy B with T1/T2, N4 page E, N5 ladder, N6 canary workflow,
  N7 owner live check).
- After N3-N5, fill the empty columns of the plan section 4 table with the same runner
  (node scripts/verify-own-solver-mutations.mjs): own ladder offline, and with a simulated V.
- B's first targets from the baseline: M3 (a new wrapper: ejs and own P both 0/32) and M7 on
  5203c085 es6 (own P "no candidates").
- M8 note: a change in how the result is applied (raw / encoded / URLSearchParams.set) is not
  a behaviour-keeping mutation (it changes the right answer); test it with the simulated V
  (N2/N5), not with mutate.mjs.

ENVIRONMENT NOTES (from the previous session)
- The sandbox can reset and wipe /data: re-clone the branch; the player cache comes back with
  `node scripts/verify-own-solver.mjs` (downloads the 32 corpus files, checks their SHA-256).
- Keep each turn short (long commands in the background with nohup, poll with short sleeps);
  turns that ran too long were cancelled.
- JDK 17 + Android SDK can be installed in the sandbox (sudo dnf install java-17-amazon-corretto-devel;
  cmdline-tools to /data/android-sdk; local.properties sdk.dir; Gradle fetches the NDK itself), so
  ./gradlew :extractor-master:test and :app:compileDebugAndroidTestKotlin run locally; CI still
  builds the APK. JS step locally:
  node --test <the files listed in .github/workflows/master-optin-debug-apk.yml>.
- Docs-only pushes start no CI; the newest code commit carries the CI result.

REPORT after each task: short Burmese, with ✅/❌ — what changed, corpus parity, mutation table per
class (ejs / own P / later: ladder offline / ladder + simulated V), wrong count, tests, commit sha
and CI run, risks. Then STOP and wait for "Next".
```
