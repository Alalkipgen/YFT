# Master Key — Phase 1.2 continuation prompt (after N5a: start N5b)

**ရည်ရွယ်ချက်:** N5a (ladder rule + reply ထဲ init errors + M9c) ပြီးပါပြီ။ Agent အသစ်က ဒီ prompt နဲ့
N5b (page-P budget, circuit breaker, ladder ကို ownSolverNext နောက်မှာ ချိတ်) ကို စပါ။
- Agent chat အသစ်ထဲ အောက်က code block ကို paste လုပ်ပါ။
- Task ပြီးတိုင်း push + မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
FIRST read, in this order:
  1. MASTER_KEY_PHASE1_2_PROMPT.md    (goal, HARD RULES, T1-T4, per-task routine, stop rules)
  2. MASTER_KEY_PHASE1_2_PLAN.md      (section 1, section 2, section 3 N5a and N5b, section 4
                                       note "N5a", section 5)
  3. PROGRESS.md top sections "Phase 1.2 — N5a ..." and "Phase 1.2 — N4b ..."
  4. this file
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
START_AT: N5b, then stop and report; N6 only after the owner says "Next".

OWNER: reports in Burmese, short, with ✅/❌. The owner has no computer, mobile data only (no Wi-Fi).

STATE (verify against the repo; the repo wins)
- N5a done: worker and page-frame replies carry init_errors + init_error_classes (classes only);
  OwnSolverProtocol.Decoded.Solved.initErrors (null = unknown = untrusted). OwnSolverLadderRule
  (extractor-master, pure) = the rule; scripts/own-solver/ladder.mjs mirrors it; both read
  extractor-master/src/test/resources/own-solver/ladder-rule-cases.json. Nothing in the app calls
  the rule yet. Results: PROGRESS.md "N5a", plan section 4 note "N5a".
- The rule: a worker step (P, then B) answers a kind only with init errors 0 and every asked input
  answered; the page (E) runs only if a worker step needs it (environment failure, init errors > 0,
  or answers without a count) and answers only with its own init errors 0; an E signature also
  needs V = pass. Anything else fails the kind; unverified answers are never cached.
- Runners: verify-own-solver.mjs --ladder [--dyncode], --faults --ladder (lazy, as the app will);
  verify-own-solver-mutations.mjs --strategy P,B --ladder (columns PB+V, PB off, B+V, B off);
  M9c-nav / M9c-loc / M9c-mix classes (environment classes, outside the 448 baseline).

N5b SCOPE (agreed in N5a; write "Plan adapted: ..." where you change it)
1. Page-P step budget (page mode only; worker P needs none: worker.terminate() stops it):
   budget = min(corpus max steps x 10, a time cap in ms measured on the corpus); Date.now() checked
   every N steps. Handle eval / new Function per N5a's --dyncode data (0 on the corpus: a player
   that starts using them must not slip past the budget; decide: refuse in page mode, or count).
   Verify: page P M9 / M9b / M9bs 32/32 with the budget, corpus = ejs 260/260.
2. Kotlin timeouts: split "ready" from "job"; only a job timeout trips the breaker.
3. Circuit breaker: a job timeout or onRenderProcessGone in E -> E off until the process restarts.
   A persistent player-hash marker is written before E and cleared after; E is disabled for a
   hash only after the marker was left behind 2 times in a row (1 -> retry once).
   Fake-engine tests for each path.
4. Wire the ladder (OwnSolverLadderRule) in OwnPlayerScriptRunner behind ownSolverNext =
   BuildConfig OWN_SOLVER_NEXT_ENABLED + an `own_solver_next` workflow input, default false.
   Flag off = main; ownSolverNext off = Phase 1.1 (P only, worker). Job fields: strategy B (+hint)
   for the worker and the frame.
5. Do NOT enable ownSolverNext by default, and never in a public release before N8 (E in its own
   process, plan section 5); owner test APKs only.

M9C FIXES (owner-approved 2026-10-12; plan N5 "Plan adapted (N5a results)"; code first, then one
test cycle)
0. Evidence first (test scripts): environment reads during a solved sig/n call (corpus 0, the
   five M9c B wrong values > 0); which step exhausts the heap on 631d3938 main M9c-nav.
6. V on every signature (worker P/B too); a pass is kept per player version + strategy.
7. Environment tripwire in worker and frame: reads during a call (Proxy), and once per player
   version a second context with changed environment values must give the same answers; a trip
   = an init error. B guard (no sig when n has no candidates) only if step 0 needs it.
8. Breaker (3) also for worker jobs, a gone renderer and V = fail.
9. Test cycle once: unit, M9c 96 (one process per player: one process for all of M9c runs out
   of heap), corpus (= ejs 260/260, tripwire false positives 0), M9b/M9bs, faults, M9, 448
   suite; push after each; done when ladder wrong = 0 everywhere.

RULES LEARNED (keep them)
- Player code runs only through the repo's harness (verify-own-solver*.mjs, harness.mjs, canary,
  mutation runner) and static parsing. No ad-hoc execution of YouTube player code.
- Never commit player text; never log solver inputs/outputs/stream URLs (counts, lengths and class
  names only). Run logs (stderr) contain player-code lines: never print raw stderr; grep the
  result lines only.
- Keep each command short (<= 25 s); start long runs with nohup and a done file, then one blocking
  wait; never combine `pkill -f` with other commands in one shell.
- ONE whole-corpus heavy run at a time (4 GB sandbox; a ladder run peaks at ~2 GB; two at once got
  one killed in N4b). Runs call settle() after every solve (harness.mjs).
- No Android SDK in the sandbox: rely on CI for Kotlin compile and tests (CI ~7-15 min, two
  workflows); docs-only pushes start no CI.
- If the sandbox has no git credentials, push one commit with the GitHub tool's push_files
  (this branch only; large payloads as a JSON arguments file), then compare trees and CI.
```
