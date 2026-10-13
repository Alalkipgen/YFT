# Master Key — Phase 1.2 continuation prompt (N5b: A.4 → A.7)

**ရည်ရွယ်ချက်:** N5b A.0–A.3 ပြီးပါပြီ (A.3 `09097f9`, CI ✅)။ Agent အသစ်က A.4 (locator cache) →
A.5 (V ledger on disk) → A.6 (docs) → A.7 (TEST_SHA + Test Agent A–D commands) ကို ဆက်လုပ်ပြီး
A.7 report (✅/❌) ပေးကာ ရပ်ပါမယ်။ Agent chat အသစ်ထဲ အောက်က code block ကို paste လုပ်ပါ။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch; never main, merge, release, tag)
FIRST read, in this order:
  1. MASTER_KEY_PHASE1_2_PROMPT.md  (goal, HARD RULES, per-task routine, stop rules)
  2. MASTER_KEY_PHASE1_2_PLAN.md    (sections 1-2; section 3 N5 and "N5b — Final plan for the next
                                     cycle" A.4-A.7 + the Test Agent table; N7 targets; section 5)
  3. PROGRESS.md top section "Phase 1.2 — N5b — in progress" (A.1 numbers + decision, A.2, A.3)
  4. AGENTS.md, then this file
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
START_AT: N5b A.4 → A.5 → A.6 → A.7; stop after the A.7 report (Test Agents A-D run later).
OWNER: reports in Burmese, short, with ✅/❌/⚠️. No computer, mobile data only.

STATE (verify; the repo wins)
- A.1 ✅ c9e9931. Owner decision (1)+(2): no 0.5 s target; N7 phone targets P hit ≤ 2 s same
  process, ≤ 8 s after a restart; B hit = metric only; option 3 (B prepared in memory) waits for
  N7; option 4 refused: the locator file is data only, code from disk never runs.
- A.2 ✅ de83094 (CI 38335870863 / 38335874782): request.deadline (core step counters, between
  phases; unfinished kinds → n:budget / sig:budget), wrapper field deadline_ms, Kotlin Next caps
  worker 40 s / page 35 s / lookup 60 s, budget memory per player|step (≤ 64), protocol
  Options.deadlineMillis + Decoded.Solved.budget; runners --ladder send STEP_CAPS_MS.
- A.3 ✅ 09097f9 (CI 38337512614 / 38337517655): mutation runner --judge ladder|all.
- Tests at 09097f9: JS 194/194, Kotlin solver package 61/61 (local JVM), CI green.

A.4 LOCATOR CACHE (ownSolverNext only; tests first; flag off = main). Previous agent's notes
(starting point; write "Plan adapted: ..." where you change them):
- Locator = data only (numbers/short ids/hashes; no code, no player text, no values).
  P: per kind the candidate indexes (registry.c index, pBuilders() in own.solver.core.js) that
  gave the agreed value; a hit runs only those. B: the winning group's members (static function
  index, shape index, fingerprint = function source hash/size); a hit skips the screen:
  instantiate → bProfile on cached members → bConfirm → bGroups → bSolveKind → tripwire.
- A hit keeps every check: SelfCheck, tripwire for both kinds (n never skips it), V for sig.
  A hit without a clean answer → drop the entry → full run (never worse than a miss).
- Cache only clean answers: init errors 0, all inputs answered, no kind failure/trip/budget;
  sig only after V pass; only the ladder's chosen step writes; M9bs-type standalone wrong never.
- Key: player ID + SHA-256 of the player text (any change → miss, entry dropped). ≤ 32 entries
  (LRU). Interface + memory store in extractor-master; Android file store
  (files/own-solver/locators.json, atomic write, corrupt/unknown version → empty, never crash) in
  extractor-master-android; wire in app SiteAdapterModule.kt next to
  SharedPreferencesBreakerMarkers. Protocol request/reply field; wrappers validate (BadJob).
- §5 risk: restricted candidates weaken n's agreement check. Poisoned-entry tests (index out of
  range, silent candidate, decoy index via faults.decoy, wrong function id/shape, other hash) →
  wrong 0. Done (plan N5): fake-engine tests incl. a hit after a restart, V fail → next step.
- Runners: corpus --ladder "miss + hit" (hit (a) same process, (b) new process reading the
  file), faults --ladder "with cache hit"; report real hit ms vs A.1 (P (a) 478 ms, (b) 1938 ms).
A.5 V LEDGER ON DISK: today `passed` in OwnPlayerScriptRunner (memory, MAX_PASSED 64, key
  player|step) and StreamCheck.Ledger (MemoryLedger; "N5 adds a small file behind the same
  interface"). Key player|step|scriptHash|locatorHash; any hash change → V again. Test: poisoned
  locator + ledger pass → V runs again, wrong 0.
A.6 DOCS: plan §5/§3 per A.6 (regex/wide recursion → N8, main candidate list, M9c-nav gap,
  B-prepared status, caps and why, locator risk).
A.7: all tests pass → push → CI ✅ (both workflows) → TEST_SHA entry in PROGRESS.md with exact
  commands per Test Agent A-D (plan table) and one result file per agent. Each list starts by
  fetching the corpus (node scripts/verify-own-solver.mjs downloads build/own-solver/players;
  check 32 files: the mutation runner silently uses cached files only). Mutation runs:
  --strategy P,B --ladder --judge ladder. M9c: one process per player, 631d3938 one per
  variant (main with --skip-ejs) = 11 per class. 448 classes, ladder seconds (PROGRESS
  "448-suite"): M1 472, M1w 483, M2 447, M3 365, M4 431, M5 483, M6 441, M7 479, M8 437, M8t 447,
  M9 473, M10 431, M11 453, M11f 452; M9c-loc 11 runs 1338 s, M9c-mix 1860 s. Split so C and D
  end near the same time; write exact lists. Report: command + summary + exit code, CI ids, sha,
  TEST_SHA. Stop.

RULES LEARNED
- Player code runs only through the repo's harness/runners. Never commit or print player text,
  solver inputs/outputs, stream URLs, corpus hashes; counts, lengths, class names only. Never
  print raw stderr of runs (player-code lines): grep result lines. Do not print
  scripts/own-solver/tests/fake-player.mjs or its literals.
- 4 GB / 2 CPU: ONE heavy run at a time (corpus ≈ 2 GB, 448 class ≈ 2.4-2.6 GB); commands
  ≤ 25 s, long runs with nohup + done file; never combine `pkill -f` with other commands.
- Tests first; mutation-test new checks (turn the check off → a new test fails).
TOOLING
- JS unit: node --test --test-reporter=tap <16 files listed in
  .github/workflows/master-optin-debug-apk.yml> (CI runs only listed files).
- Kotlin without Android SDK: JDK 17 (kotlinc 2.0.21 fails on JDK 25) + kotlinc 2.0.21 + jars
  (coroutines core/test 1.9.0, junit 4.13.2, hamcrest 1.3, okhttp 4.12.0, okio-jvm 3.6.0); compile
  core-model, extractor-api, extractor-generic, extractor-master; run tests with JUnitCore.
  Android modules + app: CI only.
- No git credentials: GitHub tool push_files (this branch), files from `git show HEAD:<path>`,
  as a JSON arguments file; check remote head = parent first; compare tree sha after; then
  git fetch + reset --hard origin/spike/master-extractor-backup.
- CI "Master opt-in debug APK (spike)" cancels in progress (do not push code while it runs;
  ignores *.md); "Work-branch checkpoint validation" runs on every push. API 60 req/h: poll ~1/min.
```
