# Master Key — Phase 1.2 continuation prompt (N3 in progress)

> **N3 ပြီးပါပြီ (done: `52e1945`, CI 38164686967 ✅).** ဆက်လုပ်ရန် `MASTER_KEY_PHASE1_2_CONTINUE_N4.md` ကို သုံးပါ။ ဒီ file ကို N3 လုပ်လက်စ
> မှတ်တမ်းအဖြစ်သာ ထားပါတယ် (do not use it to start a session).

**ရည်ရွယ်ချက်:** N3 (Strategy B) ကို လုပ်လက်စ (WIP) အဖြစ် push ထားပါတယ်။ Agent အသစ်က ဒီ prompt နဲ့ ဆက်လုပ်ပါ။
- Agent chat အသစ်ထဲ အောက်က code block ကို paste လုပ်ပါ။ Reasoning Level အမြင့်ဆုံး သုံးပါ။
- Task ပြီးတိုင်း push + မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
FIRST read, in this order:
  1. MASTER_KEY_PHASE1_2_PROMPT.md    (goal, HARD RULES, T1-T4, per-task routine, stop rules)
  2. MASTER_KEY_PHASE1_2_PLAN.md      (section 2 "Strategy B in more detail", section 3 N3, section 4)
  3. MASTER_KEY_PHASE1_2_CONTINUE.md  (state after N1/N2: baseline numbers, how N1 gets its truth)
  4. this file (N3 work in progress)
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
START_AT: N3 (continue the WIP below), then stop and report; N4 only after the owner says "Next".

OWNER: reports in Burmese, short, with ✅/❌. The owner has no computer, mobile data only (no Wi-Fi).

STATE (verify against the repo; the repo wins)
- Phase 1.1 S1-S6 done; N1 done (765e91f); N2 done (StreamCheck 9e15101, owner tool 4934fe1/c74df37;
  n rule stays PENDING, see plan section 2). Corpus parity with P: 32/32 files, own = ejs on all inputs.
- N3 WIP pushed in the commit "wip(master): N3 strategy B in progress". NOT done. Strategy B is reached
  only when a request has strategy "B" (the app never sends it), so P and the app are unchanged.

WHAT EXISTS (extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js, B block
before `function run(prepared`; exported as prepareB/runB/progress + internals)
- bPrepare: parse, bInstrument (registers every non-async, non-generator function at creation with
  `(__yftOwnRegistry$.r(fn,id))`; declarations `;REGISTRY.r(name,id);` only in statement lists; loop
  bodies get `{REGISTRY.s();...}` step counting), bBody (known wrapper -> largest top function ->
  flat), bBuilderNames (P's two builder patterns -> names, evidence only), bTemplates (call shapes with
  numeric literal args + one hole, e.g. `rb(6,3439,_)`; aliases followed; caps 6000 / 96 per name),
  meta: name, size, params, builder, unique, skip (the whole-program wrapper and functions > 250k chars;
  re-running the wrapper as a "probe" hung > 60 s).
- bInstantiate (installEnvironment + stubs; init 50M steps, probe 400k steps), bFreezeBuiltins.
- bProfile (sig: S4 map probe + edge probe, not one primitive; n: 3 probes, URL-safe, not trivial,
  distinct, head/tail change, stable) returns {kind,outputs,steps} or {reject:"class"}.
- bConfirm (T2: own fresh instance x2 calls + second fresh instance), bGroups (T1; score: P-hint
  +1000000, builder +1000, composed (loop steps > 0) +100, 1 param +10; top 3; ambiguous rule),
  bSolveKind (SelfCheck), bRun (timings in report.ms, slow list, screen budget 40 s -> "budget"),
  bPHint (runs P on B's probes; its answers rank a group first; never decides alone).
- Harness: scripts/own-solver/harness.mjs (report for strategy runs; progress after a vm timeout; debug
  only with --verbose), scripts/verify-own-solver.mjs (--strategy P|B, --variant <v>, --verbose: stage,
  ms, slow functions, per-group = ejs / != ejs counts and member names).

MEASURED (computer, 5203c085 main, `node scripts/verify-own-solver.mjs --only 5203c085 --variant main
--strategy B --verbose`)
- prepare ~3.3 s (parse 0.8, instrument 1.0-1.6, reparse 0.8); instantiate 0.3 s; screen ~3 s
  (12463 registered, 7153 screened, 7557 shapes, 738 templates); confirm n 0.4 s, sig 0.1 s.
  4-5 functions hit the 400k step limit (worst 1.5 s). Phone target: first B run <= 10 s.
- sig ✅: found through templates `rb(2,3435,_)` and `rb(6,3439,_)` (dispatcher with constant
  arguments): one group, = ejs 3/3.
- n ❌: the real n transform is a dispatcher branch, `Gn(17,369,n)` (Gn has 4 params; it is called as
  `Gn(v^7907, v^8067, a)` inside Jg where `v = w^F`, so literal templates miss it). 5 false n groups
  pass the current n rules (Oxz, R4r, $Qg, g.Xq(_,4), Mg9) -> "n:disagree" (safe, no wrong value).

NEXT STEPS
1. Static constant propagation of dispatcher keys in bPrepare (static AST only): from each literal
   template, bind the callee's params to constants, evaluate single-assignment locals (`var v=w^F`)
   and call arguments (literals, known names, ^ + - * | & << >> >>> ~ unary -) in the callee body
   (skip nested functions), emit new one-hole templates and partial templates to propagate further;
   worklist with caps; measure the template count and time. Check that Gn(17,369,_) appears.
2. Make the n rules strict enough that, WITHOUT P's hint, only the real transform survives (the
   plan's Decide: several disagreeing groups without V -> fail). First read the 5 decoys' source
   statically to see what they are. Add a request flag to switch the P-hint off and use it for the
   "B alone" corpus runs, so B alone is measured honestly; the ladder (N5) may use the hint.
3. `node scripts/verify-own-solver.mjs --strategy B` (32/32 = ejs), also with --faults; P unchanged
   (`node scripts/verify-own-solver.mjs` 32/32). Then wrong = 0 always.
4. Add --strategy B to scripts/verify-own-solver-mutations.mjs; run the suite (M3 and M7 matter most:
   P is 0/32 on M3, P fails 5203c085 es6 on M7); fill the B column in plan section 4.
5. JS tests scripts/own-solver/tests/core-strategy-b.test.mjs with fake players (fake-player.mjs,
   solver-context.mjs): decoy, trivial transform, non-deterministic, looping function, M3-style
   wrapper, dispatcher shape; add it to the CI JS step in .github/workflows/master-optin-debug-apk.yml.
6. Docs: plan board N3 Done + budgets + "Plan adapted" (templates/propagation, P-hint, skip rules),
   PROGRESS section, a continue prompt with START_AT N4. Push, verify the tree, wait for CI green,
   report in Burmese (✅/❌), stop and wait for "Next".

RULES LEARNED IN THIS SESSION (keep them)
- An automated safety reviewer allows player code to run only through the repo's harness
  (verify-own-solver*.mjs, harness.mjs, canary, mutation runner) and static parsing. No ad-hoc
  execution of YouTube player code. Adding the TVHTML5 client to the stream-check tool was blocked
  twice: do NOT retry it.
- Keep each command short (<= 25 s); start long runs with nohup and poll; never combine `pkill -f`
  with other commands in one shell (it can kill that shell).
- Environment: clone the branch; `node scripts/verify-own-solver.mjs` restores the player cache
  (build/own-solver/players, never committed). Gradle needs JDK 17 (`sudo dnf install -y
  java-17-amazon-corretto-devel`) and an Android SDK in local.properties (gitignored).
- CI (.github/workflows/master-optin-debug-apk.yml) takes about 7 minutes; docs-only pushes start no CI.
```
