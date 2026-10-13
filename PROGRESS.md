# YFT Master Extractor backup

## Phase 1.2 — N5b — in progress

- ⏳ Plan adapted (owner-approved 2026-10-12; plan §3 N5 "Plan adapted (N5a results)"): V on every
  signature, environment tripwire, breaker also for worker jobs / gone renderer / V = fail, then
  the wiring behind `ownSolverNext` (default off); one test cycle at the end.
- ✅ Tripwire + V on every signature (this commit; app unchanged: the core checks only when a
  request asks `tripwire: true`, which only N5b's ladder will send, and nothing calls the rule
  yet). Plan adapted: no separate read counting; the second instance measures the dependence
  itself (a read that does not change the answer does no harm). Core: after worker P or worker B
  solves, the answered kinds run once more on a second instance with other environment values
  (page, user agent, language, platform, cookie setting, ...); a changed answer = one more init
  error (class `Tripwire`) and no answer for that kind (`kind:environment`). Page mode skips it
  (the window's own objects stay). Rule (Kotlin + JS, 22 shared cases, new `candidate()`): a
  signature from any step needs V. Runners: `--ladder` sends the tripwire (`--no-tripwire`: as
  N5a). JS 173/173 (171 + 2 tripwire tests).
- ✅ Step 0 evidence. Corpus `--ladder` (363 s, peak RSS 1.95 GB): 32/32, init errors 0/32 in all
  four steps (tripwire false positives 0); PB+V = ejs 260/260, B+V 260/260; PB off and B off: n =
  ejs 132/132, sig no value 128 (no signature without V, by design). M9c-nav 21cd2156 es6 (N5a's
  ladder wrong value): worker B `sig:environment` {init errors 1}, ladder safe in all four options.
- ✅ CI for `9f9eb95`: build 38270898966 ✅, validate 38270906278 ✅.
- ✅ 631d3938 main M9c-nav heap (N5a's out-of-memory line, mutation runner with `YFT_STAGE=1`
  stage/heap lines and the new `--skip-ejs`): main's ejs runs out of heap first (217 MB before it,
  exit 134 at ~37 s, ❗ a main risk, main untouched); with ejs skipped, own P (worker) runs out of
  heap too: an M9c-nav literal turns a player loop into one that never ends and grows memory.
- ✅ P step budget (core; Plan adapted: in the worker too, not only the page: the heap ran out
  before any timeout, and a renderer out of memory is gone, not terminated). Each loop body of
  P's program counts a step (B's counter); init and every candidate attempt get their own budget;
  a time cap is checked every 4096 steps; over budget, every later step throws
  `RangeError("steps")` until the next attempt. Budgets from the corpus (`--ladder`, 32/32
  files, new line "ladder P steps"): largest init 2814 steps / 196 ms, largest attempt 1005 steps /
  27 ms → init 30,000 steps / 20 s, attempt 12,000 steps / 3 s (steps ×10, time ×10 ×10 for a
  slow phone). Corpus with these budgets (`--ladder`, 384 s): init errors 0/32 in all four steps,
  PB+V = ejs 260/260, B+V 260/260, wrong 0. 631d3938 main M9c-nav with them: exit 0, all four
  steps safe (P: init errors 4), ladder wrong 0, own P heap +100 MB (was +1.4 GB at 50 M steps).
- ✅ eval / new Function (plan item 1: "refuse in page mode, or count"): refused in page mode.
  While a page job runs, `eval` and the Function constructors (also through `.constructor`, async
  and generator ones) throw `EvalError("dyncode")`: an init error in top-level code, no answer
  where n or sig needs it. Corpus: 0 such calls (N5a `--dyncode`). The worker runs them (the app's
  job timeout stops it).
- ✅ Job fields (worker and page frame): `"strategy":"B"` with `"hint"` (from the player text,
  nothing kept; a prepared B job is `BadJob`), `"tripwire":true` (worker). Fault fields stay
  blocked. JS 179/179 (173 + 4 step-budget tests, 1 worker, 1 frame).
- ✅ Kotlin, first part (not called yet; CI compiles it): `OwnSolverProtocol.Options` (strategy B
  + hint, tripwire) in `encode`; `OwnSolverEngine.Events` (`started` = the page read the job,
  `gone` = onRenderProcessGone) with `reportsReady`, in both WebView engines.
- ✅ CI for `cb75f1b`: build 38278036731 ✅, validate 38278041351 ✅.
- ✅ Kotlin, second part: the ladder behind `ownSolverNext` (`-Pyft.ownSolverNext=true`, debug
  builds only; workflow input `own_solver_next`, default off = Phase 1.1 unchanged).
  `OwnPlayerScriptRunner.Next`: worker P (kept program first, tripwire) → worker B (player text,
  no hint, tripwire) for kinds still open → page P, then page B, only when a worker needs the
  page; per kind the shared rule's `candidate()`. Ready timeout 20 s (engines that report it; no
  strike), job timeout 45 s. `OwnSolverBreaker`: a job timeout or a gone renderer turns that step
  off until the app restarts (both page steps together: one page engine); a marker per player
  version + step is on disk (`commit`) before each job and cleared after it; two left behind in a
  row (the process died twice) = that step off for that player version. V: a signature from any
  step is used only after one stream URL carrying it opens (`SignatureGate` around the reader:
  the reader is main's whole-file copy and stays main's text, R3; `StreamCheck`, control = a
  stream URL of the same lookup without an own signature); pass kept per player version + step (64), fail = that step's
  signatures off (the ladder looks further down), unknown = nothing kept and the signed streams of
  that lookup dropped. Plan adapted: the lookup counts as user-started (the extractor runs only for
  the page the user opened). Local JVM check (Kotlin 2.0.21): 69/69 (solver, own-solver YouTube,
  StreamCheck, canary; new: ladder runner 11, breaker 4, protocol options 2, V in the reader 2);
  Android parts compile in CI only.
- ❌→✅ CI for `6bc056e`: build 38282262857 ❌ at the R3 toolkit drift check (V was inside the
  reader's whole-file copy); fixed: the copy is main's text again (0 drifted), V moved to
  `SignatureGate`; local 70/70 (+1: an answer outside a lookup loses its signatures).
- ✅ CI for `8153f6c`: build 38284472227 ✅, validate 38284476176 ✅.
- ✅ Test cycle, unit: JS 179/179; Kotlin in CI (`8153f6c`) ✅.
- ✅ M9c-loc (`--strategy P,B --ladder`, tripwire on, one process per player: 11 runs, 1338 s,
  peak RSS 1.7 GB), survived/safe/wrong: ejs 32/0/0, own P 32/0/0, own B 12/20/0, page P
  0/32/0, page B 0/31/1 (9fcf08e8, as in N5a; the ladder does not take it), ladder PB+V 32/0/0,
  PB off 0/32/0, B+V 32/0/0, B off 0/32/0: **ladder wrong 0**.
- ✅ M9c-mix ladder: 11 runs, 1860 s, peak RSS 1749 MB; survived/safe/wrong:
  ejs 0/32/0, own P 0/32/0, own B 0/31/1, page P 0/32/0, page B 0/31/1;
  ladder PB+V, PB off, B+V and B off each 0/32/0: **ladder wrong 0**.
  ❌ Standalone B and page B each produced one wrong value (e7573094); that run exited 1
  because the runner counts standalone wrong values too. Ten other runs exited 0.
  This is not an all-green standalone-strategy result; no code changed.
- ⏳ Owner-approved test-only continuation: finish M9c-nav → corpus → M9b/M9bs → faults →
  M9 → per-class 448 suite. One heavy process at a time; update and push this document
  after each completed test/class before starting the next. Record assertion failures,
  continue the planned evidence collection without code fixes; stop on an incomplete run,
  timeout or infrastructure error. Push only spike/master-extractor-backup; no main,
  merge, release or tag. Code changes wait until testing is finished.
- ✅ M9c-nav: four 631d3938 variant runs, exits 0, 343 s, peak RSS 2222 MB;
  all four ladder columns 0/4/0, standalone wrong 0, **ladder wrong 0**.
  Main variant used --skip-ejs for the previously documented baseline heap issue;
  this does not claim full ejs coverage for that variant.
- ✅ M9c ladder checkpoint complete: 26 runs across loc/mix/nav, 3541 s cumulative,
  peak RSS 2222 MB, ladder wrong 0 in every run. Standalone wrong values remain
  documented separately; M9c is not an all-green standalone-strategy result.
- ✅ Post-M9c corpus --ladder: exit 0, 380 s, peak RSS 2053 MB; 32 files,
  all four steps init errors 0/32 (tripwire false positives 0).
  PB+V and B+V match ejs 260/260, wrong 0; PB off and B off each match 132/260,
  wrong 0, no value 128 (signature withheld without V, as designed).
- ✅ M9b/M9bs ladder checkpoint: 32 files, two seeds; 1828 s, peak RSS 1904 MB,
  **ladder wrong 0**. Survived/safe/wrong:
  M9b PB+V 32/0/0, PB off 0/32/0, B+V 10/22/0, B off 0/32/0;
  M9bs PB+V 32/0/0, PB off 0/32/0, B+V 13/19/0, B off 0/32/0.
  ❌ Standalone own B in M9bs: 0/31/1; total standalone wrong 1, runner exit 1.
  This is an assertion result, not an incomplete run; no code fix applied.
- ✅ Faults --ladder: exit 0, 2444 s, peak RSS 2432 MB; 384 runs (12 fault
  classes × 32 files), wrong values 0, expected FAILs missed 0, files not ok 0
  in every fault class. Baseline PB+V and B+V remain ejs 260/260, wrong 0;
  all four steps init errors 0/32. No code fixes applied.
- ✅ M9: exit 0, 473 s, peak RSS 2399 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M1: exit 0, 472 s, peak RSS 2400 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M1w: exit 0, 483 s, peak RSS 2328 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M2: exit 0, 447 s, peak RSS 2381 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M3: exit 0, 365 s, peak RSS 2018 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 0/32/0, own B 32/0/0, page P 0/32/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M4: exit 0, 431 s, peak RSS 2331 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M5: exit 0, 483 s, peak RSS 2318 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 24/8/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 24/8/0, PB off 0/32/0, B+V 24/8/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M6: exit 0, 441 s, peak RSS 2415 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M7: exit 0, 479 s, peak RSS 2613 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 11/21/0, own P 31/1/0, own B 32/0/0, page P 31/1/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M8: exit 0, 437 s, peak RSS 2357 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M8t: exit 0, 447 s, peak RSS 2354 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M10: exit 0, 431 s, peak RSS 2357 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M11: exit 0, 453 s, peak RSS 2414 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M11f: exit 0, 452 s, peak RSS 2441 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448 baseline suite complete: 14 classes × 32 files = 448 file/class cases,
  each run with two seeds; all 14 processes exited 0, standalone wrong 0 and
  ladder wrong 0. M9 above is included in this baseline, not counted twice.
- ✅ Owner-requested test-only cycle complete: M9c → corpus → M9b/M9bs → faults →
  M9 → remaining 448 classes. Ladder wrong 0 throughout; corpus PB+V/B+V remain
  ejs 260/260 and tripwire false positives 0; faults wrong/missed 0.
  ❌ Standalone-strategy wrong values remain in M9c-loc, M9c-mix and M9bs as
  recorded above. This checkpoint does not claim every standalone strategy passed.
- ⏸️ Paused after tests. No repository code fixes, main changes, merge, release or tag
  during this cycle. N5b implementation is still in progress: locator cache and the
  persistent V/StreamCheck ledger remain deferred; CI for 63758da not yet checked.
  Await owner instructions before any code work or N6. This is a test checkpoint,
  not completion of those deferred implementation items or release acceptance.
- ✅ A.0 (plan §3 N5b "Final plan"): fresh clone at `3300ccb` (= `f9eaedc` + plan docs only);
  no local drafts existed, nothing to back up; clean tree.
- ✅ A.1 Budget data, code (report only, answers unchanged; app behaviour unchanged: the wrappers
  pass only init errors from the report). Core: P `prepare` reports ms parse / find / build; P
  `run` reports ms init, search (attempts of candidates that gave no value), SelfCheck (the rest
  of the kinds' solve) and the tripwire (with its search share); B adds the winning group's
  confirm per kind and the tripwire. New `scripts/own-solver/phases.mjs`: phases per step (P
  prepare = parse + build, P search = find + silent attempts; B prepare = parse, instrument,
  templates, reparse, build; B search = the screen), the hit estimate (sum without search; B
  confirms only the winners), a max/median table and the lookup's time. Harness:
  `prepareWithOwn` returns the kept program's length, new `prepareBWithOwn` B's prepared sizes
  (characters only). Runners: corpus `--ladder` and mutation `--ladder --phases` print wall ms
  per step, the phase table and the lookup's largest time; `--prepare --strategy B` the prepared
  sizes. JS 185/185 (179 + 6 step-budget tests).
- ✅ A.1 corpus `--ladder` (551 s, peak RSS 2018 MB; this computer: Xeon 2.5 GHz, 2 vCPU): exit 0,
  PB+V = ejs 260/260, B+V 260/260, wrong 0, init errors 0/32 in all four steps. Max/median ms:

  | step | prepare | init | search | confirm | SelfCheck | tripwire | wall |
  |---|---|---|---|---|---|---|---|
  | P | 1496/1052 | 239/146 | 601/366 | – | 14/8 | 232/139 | 2501/1683 |
  | B | 3720/2847 | 491/184 | 3076/2735 | 1869/346 | 552/40 | 262/21 | 8167/6693 |
  | pP | 1674/1133 | 275/20 | 607/375 | – | 40/6 | – | 2330/1605 |
  | pB | 3822/2720 | 225/30 | 3540/2905 | 1802/137 | 427/46 | – | 7533/6283 |

  Hit estimate max/median ms (files over 0.5 s): P (a) same process 478/291 (0/32; the first
  run 516/272, at the edge), P (b) after a restart 1938/1329 (32/32); B (a) = (b) 5422/3724
  (32/32); B (a) with option 3 2807/631 (18/32); pP (a) 315/26, (b) 1865/1184; pB (a) = (b)
  4725/3219, option 3 2341/221. Lookup, largest per file: P+B 9872 ms (74edf1a3 main), all four
  steps 18997 ms (74edf1a3 es6).
- ✅ A.1 631d3938 M9c-nav (`--strategy P,B --ladder --phases`, one process per variant, main with
  `--skip-ejs`; 376 s, peak RSS 2202 MB): exits 0, ladder wrong 0, every step safe (P `probe`, B
  `no candidates` or `budget`). Wall ms P/B/pP/pB: main 2010/60059/1899/60088, tce
  1880/44081/2178/44628, es6 1591/9865/1549/9777, phone 1175/60078/1380/60076 (60 s = the
  harness timeout after B's reply: B's phases end at 44.4 s main and 38.6 s phone, then the
  player's late promise jobs; B init 26.0 s on main, the screen 40.7 s on tce = `B_SCREEN_MS`).
- ✅ A.1 prepared sizes (`--prepare --strategy B`, 130 s): B's prepared program + function table +
  templates largest 6,540,135 chars (5203c085 tce; main 6,539,969) → 2 players
  (`maxCachedPlayers`) ≤ 24.9 MB at 2 bytes per char; P's kept program (kept today) largest
  4,547,572 chars → ≤ 17.3 MB for 2.
- ✅ A.1 caps (proposed; numbers for A.2): phone margin ×4 (a mid-range phone runs JS ≈ 2–3× slower
  than this computer; ×4 leaves room; N7 measures the real factor).
  - Job cap 40 s (worker steps) = Kotlin job timeout 45 s − 5 s grace (the deadline is checked
    every 4096 steps, then the reply and the bridge). ≥ `P_INIT_MS` 20 s: P's init can use its
    whole budget and P still has 20 s; `B_SCREEN_MS` 40 s stays, the deadline ends B first.
  - Page cap 35 s (tighter: a page step runs only for kinds the workers left, after a ready wait
    of up to 20 s, in the renderer that can run out of memory).
  - Lookup cap 60 s = `P_INIT_MS` + job cap: B keeps its full cap after P.
  - Corpus at ×4: P ≤ 10.0 s, B ≤ 32.7 s (fits the cap up to ×4.9), P+B ≤ 39.5 s, pB ≤ 30.1 s
    (fits up to ×4.6), all four ≤ 76.0 s > 60 s: on the largest players page steps may be cut
    (not run, not recorded, run in the next lookup). 631d3938 M9c-nav at ×4: es6 P 6.4 s + B
    39.5 s, inside; main, tce, phone: B does not finish even here (44.4 / 44.0 / 38.6 s), it ends
    at its deadline as `budget` (full cap → recorded → skipped in the next lookup); no answer is
    lost (B answers nothing there).
- ✅ A.1 option 3 (B's prepared program in memory): wrappers accept `type:"prepared"` with
  `strategy:"B"` (today `BadJob`: `own-solver-worker.js:67`, `page-frame.js:80`); core runs B from a
  kept prepared object (program, function table, templates; same core version) and returns it
  once (B with `keep_prepared`); protocol drops `require(!options.strategyB || player is
  Player.Raw)` (`OwnSolverProtocol.kt:107`) for a prepared-B player and reply field; Kotlin's
  `prepared` map keeps P's and B's per player (same eviction, `maxCachedPlayers` 2), worker B and
  page B use it first and rebuild once from the player text if it breaks (as worker P). RAM ≤
  24.9 MB for 2 players (+ P's ≤ 17.3 MB today); each B job then carries ≤ 6.5 M chars instead of
  the player text (≤ 2.7 MB). Re-run: JS unit (worker, page frame, B, ladder, tripwire, step
  budget), Kotlin protocol / runner / ladder runner / breaker (CI + local JVM), corpus `--ladder`
  miss + hit, faults `--ladder`, M9b/M9bs, 631d3938 M9c-nav, flag-off gate. Helps (a) only (the
  memory is gone after a restart): B (a) 5422/3724 → 2807/631 ms. Option 4 (prepared program on
  disk) brings (b) near (a) but changes N5b's rule "locator file = data only".
- ✅ CI for `c9e9931`: build 38327508945 ✅, validate 38327512152 ✅.
- ⏸️ A.1 stop point: hits over 0.5 s (B in (a) and (b), P in (b), P (a) at the edge). Waiting for
  the owner's choice: (1) target (a) only, (2) relax the (b) target, (3) keep B's prepared program
  in memory, (4) prepared program on disk. A.2 waits for the answer.

## Phase 1.2 — N5a ladder rule, init errors in replies, M9c — done, ❌ M9c-nav ladder wrong 1 → N5b (code `a78626f`, CI 38189985242 ✅ + checkpoint 38189988228 ✅)

- ✅ Code (app behaviour unchanged: nothing calls the rule yet; flag off = main): worker and
  page-frame replies carry `init_errors` + `init_error_classes` (class names only);
  `OwnSolverProtocol` decodes them (missing → null = unknown = untrusted). `OwnSolverLadderRule`
  (extractor-master, pure) and `scripts/own-solver/ladder.mjs` read one case table
  (`extractor-master/src/test/resources/own-solver/ladder-rule-cases.json`, 18 cases). Runners:
  `verify-own-solver.mjs --ladder [--dyncode]`, `--faults --ladder` (lazy, as the app will),
  mutation runner `--ladder` (columns PB+V, PB off, B+V, B off). Mutators M9c-nav / M9c-loc /
  M9c-mix (environment classes, outside the 448 baseline). JS 171/171 (161 + 10).
- ✅ Corpus `--ladder --dyncode` (367 s, peak RSS 2.0 GB): 32/32 files; ladder = ejs 260/260 in
  all four options (wrong 0, no value 0); init errors 0/32 files in worker P, worker B, page P,
  page B; player `eval` reads and `Function` calls 0 in all four runs (init and later).
- ✅ M9b/M9bs `--strategy P,B --ladder` (all 32 files, 2172 s, peak RSS 1.9 GB), survived/safe/wrong
  per class: ladder PB+V 32/0/0 and 32/0/0 (n and sig from E, V simulated), PB off 0/32/0 both
  (n from E right in all 64; no page signature without V), B+V 10/22/0 and 13/19/0, B off 0/32/0
  both. **Ladder wrong 0.** The N4b wrong value is still there in worker B alone (631d3938 es6
  M9bs sig, init errors 3) and the rule does not return it (that case: n E, sig E, right). ejs,
  worker P, page P and page B as N4b (ejs 0 survived, safe; page P 32/32). 5/64 `{late promise
  jobs cut}`. (A first run was cut at 36/64 when the sandbox restarted; re-run whole.)
- ✅ Faults `--faults --ladder` (each case through the ladder run lazily; 3070 s, peak RSS 2.3 GB):
  384 runs, **wrong 0, missed 0**, 32/32 files 12/12 ok; init-wrong-n / init-wrong-sig wrong 0
  (N4b: 132 / 128 in the worker alone): init errors → worker answers not used → E (n and sig
  from E, right). Sources: decoy-n n none (failed as expected), sig P; decoy-sig and raw-encoding
  sig none, n P; omit-window/self P (1 step); omit-location/navigator/document/XMLHttpRequest/all
  E (3 steps: worker P, worker B, page P). Unfaulted ladder = ejs 260/260.
- ✅/⚠️ M9 `--strategy P,B --ladder` (641 s, peak RSS 2.3 GB): ladder PB+V 32/0/0, B+V 32/0/0,
  **wrong 0**; but PB off and B off 0/32/0 (all safe: n from E right, no signature without V).
  M9's reads throw once at init in the worker (init errors 1 in all 32, worker P and B; page 0):
  the worker's answers are right (32/32) but the rule does not use them, so E answers, and a
  signature from E needs V. Cost of the rule, not a wrong value (§4 note "N5a").
- ❌ M9c-nav `--strategy P,B --ladder`, one process per player (2346 s, peak RSS 2.55 GB; 7/8
  players, 28/32 lines). One process for all of M9c stopped at 72/96 with "heap out of memory"
  (exit 134, 4609 s, 2.6 GB), M9c-nav alone at 9/32; 631d3938 alone also stopped (0/4, rerun one
  variant per process pending). **Ladder wrong 1 in all four options** (PB+V, PB off, B+V, B off):
  21cd2156 es6, worker B and page B give a wrong signature (n: no candidates) with no init errors,
  so the ladder returns worker B's signature; +V checks page signatures only. Own P, page P 0/28/0
  (all safe). ejs wrong 1 (74edf1a3 es6; ❗ separate, also main: likely no cookieEnabled in the
  WebView worker's WorkerNavigator). The silent worker wrong is M9c-nav's design target. Proposed
  fix (not implemented, owner decides): every signature, the worker's too, needs V before use.
- ✅ M9c-loc `--strategy P,B --ladder`, one process per player, 631d3938 one per variant (11
  processes, 1596 s, peak RSS 1.7 GB; after one sandbox restart the unfinished player ran again):
  **ladder 32/0/0 in all four options, wrong 0**. ejs 32/0/0, own P 32/0/0, own B 12/20/0, page P
  0/32/0. Page B wrong 1 (9fcf08e8 tce: signature, n no candidates, init errors 2), not returned:
  worker P answers first, and the page answer had init errors.
- ✅ M9c-mix `--strategy P,B --ladder` (11 processes as M9c-loc, 2176 s, peak RSS 1.6 GB):
  **ladder 0/32/0 in all four options, wrong 0** (all safe; E answers). ejs, own P, page P 0/32/0.
  B wrong 1 in the worker and 1 in the page, both e7573094 main, a signature with n "no
  candidates" (as in M9c-nav): not returned, the worker answer had init errors 1 and a page
  signature needs V.
- ⚠️ M9c-nav 631d3938, one variant per process: es6, tce, phone safe in every step and every
  ladder option; **main stops even alone** with "heap out of memory" (exit 134, 61 s, 2.5 GB): this
  mutated player exhausts the heap in a single solve (which step is not known yet). In the app that
  is a memory risk in the worker or page renderer (onRenderProcessGone candidate). M9c-nav 31/32
  lines: ladder wrong 1 (21cd2156 es6) as above, no new wrong value.

## Phase 1.2 — N4b environment-dependent values (M9b / M9bs) — measured ❌ (code `bdb45c6`, CI 38175876765 ✅; harness fix `8140b27`, CI 38181863054 ✅)

- ✅ Code: mutation classes M9b (DOM-made value; use throws without the DOM) and M9bs (silent
  use), outside the 448 baseline; runner `--both` (worker and page side by side) and `--variant`;
  core init errors by class (`report.topLevelErrorClasses`); harness `--init`, `--fault-cases`;
  fault cases `init-wrong-n` / `init-wrong-sig`.
- ✅ Init errors before M9b: 0/32 files in the worker (P, B) and in page mode (P, B); corpus
  32/32 = ejs 260/260 in all four runs.
- M9b/M9bs `--both` (all 32 files in one process, 1963 s, peak RSS 1.8 GB), survived/files:
  M9b: ejs 0 (safe), worker P 0 (safe), worker B 0 (safe), page P 32, page B 10 (22 safe);
  M9bs: ejs 0, worker P 0, worker B 0 (31 safe, ❌ **1 wrong**), page P 32, page B 13 (19 safe).
  Plan §4 note "N4b".
- ❌ The wrong value: worker B on 631d3938 es6 M9bs, sig (0/4 right; n `no candidates`). Silent:
  the DOM statement fails in the worker (init errors 3: RangeError + TypeError), B keeps going and
  sig changes: SelfCheck and agreement cannot see it; the proposed init-error rule would (init
  errors > 0 → E; page B 0 init errors, page P/B right there). Reproduced in a fresh run and with the harness before the fix below. ejs is safe
  there: main is not affected; the app does not send B before N5. Not fixed (owner's stop rule).
- Faults: worker P (384 runs) and worker B: the 10 earlier cases wrong 0, missed 0; ❌ init-wrong-n
  132 and init-wrong-sig 128 wrong (by construction). Page P and page B (384 runs each): wrong 0,
  missed 0 (init-wrong inert in page mode); N4's page B faults are done.
- ✅ Harness fix (test scripts only; app and core unchanged): `harness.mjs` vm contexts use
  `microtaskMode: "afterEvaluate"` and keep the solve's reply before player promise jobs run; when
  those jobs time out, the kept reply is used (the app's worker also posts its reply before later
  promise jobs run). The hang (74edf1a3 phone M9bs, B) is gone: 5/64 cases `{late promise jobs
  cut}` (the runner marks them; all worker B, safe), 0 harness timeouts. JS 161/161.
- Lesson: two whole-corpus runs at once hit the 4 GB limit (page B faults was killed after 30/32
  files; the last 2 re-run alone). Run one whole-corpus `--page` or `--both` run at a time.
- ⏭ Next: the owner decides the fix (the init-error rule proposed in
  `MASTER_KEY_PHASE1_2_CONTINUE_N4B.md` covers this case); N5 only after "Next".

## Phase 1.2 — N4 strategy E (page environment) — N4 done (`a7c3dd9`, CI 38170962057 ✅)

- ✅ `WebViewPageSolverEngine` (`extractor-master-android`, an `OwnSolverEngine`; nothing calls it
  before N5): a fresh offscreen WebView per run, destroyed afterwards, with **no**
  `addJavascriptInterface`. The reply comes back through androidx.webkit `addWebMessageListener`,
  limited to `https://appassets.androidplatform.net` (the sandboxed frame's opaque origin never
  gets the object): main frame only, the first string reply ends the run
  (`OwnSolverPageReplyGate`, ≤ 12 MiB). `isAvailable` needs `WEB_MESSAGE_LISTENER`, else E is
  skipped. Settings as the worker engine, third-party cookies off; no console, dialogs, windows,
  permissions or location prompts; a page load error or a renderer crash ends the run with no
  answer.
- ✅ Own route table (`OwnSolverPageEnvRoutes`): the host page in the main frame only, the frame's
  page in a sub frame only, its scripts, parser, core and the job (served once) as sub resources
  only; the worker's files, main's ejs files and every other request are refused. Each answer has
  its own CSP, `no-store`, `nosniff` and `no-referrer`.
- ✅ Assets: `page-env.html` + `page-env.js` (host page; CSP `script-src 'self'; connect-src 'self';
  frame-src 'self'`) and `page-frame.html` + `page-frame.js` (the frame: `sandbox="allow-scripts"`
  set before it has an address, and again by the response header; CSP `connect-src 'none'`,
  `worker-src 'none'`, `webrtc 'block'`, scripts only `page-frame.js` + eval). The frame says it
  is ready; the host page then sends the job with a fresh 128-bit token; the frame takes its reply
  path and removes WebRTC before player code (it refuses the job if it cannot), runs the job in a
  later task and replies with the token; the host page passes on only the first reply with it.
- ✅ Core page mode (`request.page`): a real window's browser objects stay (the worker mode's
  defines would throw there); only missing names are added. The worker mode is unchanged.
- ✅ `OwnSolverEnvironmentErrors` (`extractor-master`): the rule N5 asks before E
  (`ReferenceError` or `TypeError` as a whole failure, a worker error class or a candidate error).
- ✅ Harness `--page` (`verify-own-solver.mjs` and the mutation runner): the same solve in page
  mode in a page-frame DOM stub (opaque origin: cookies and storage throw; the frame's location).
- ✅ Results (computer): **M9 in page mode: own P 32/32, own B alone 32/32, wrong = 0** (ejs 0/32,
  safe). Corpus in page mode: P and B 32/32 files, own = ejs on 260/260 inputs; faults in page
  mode (320 runs each): P wrong 0, missed 0; B alone not run yet (pending). Worker mode unchanged:
  corpus P and B 32/32, 260/260; full suite 448 cases: ejs 291, own P 415, own B alone 448,
  wrong 0 (as N3); faults (320 runs each) P and B alone wrong 0, missed 0. Plan §4 note "N4".
- ✅ Tests: `page-env.test.mjs` (14, fake players): page mode keeps a window's objects; on a
  window-shaped global the worker mode fails (`TypeError`) and page mode solves with P and B; a
  player whose n needs the DOM fails n in the worker and passes in the page; the frame stub's
  shape; ready handshake, one job, only from the host page, with the token; player code cannot
  read the token, forge the reply or replace the reply path; WebRTC removed (else no job); error
  classes only; the host page sandboxes the frame first and passes on only the first reply; pages
  and policies match the app's route table. In the CI JS step; locally 154/154. Kotlin: routes
  (6), reply gate (3), engine availability (1, Robolectric), environment errors (3);
  `:extractor-master:test` 326 (2 skipped), `:extractor-master-android:testDebugUnitTest` 69.
- Speed (computer, 32 files, two suites sharing 2 CPUs): page P 60 s, page B 198 s (worker 60 s,
  195 s). Phone: N5/N7.
- Risks: WebRTC through a frame the player makes itself (not closed; a design that holds the
  renderer's WebRTC connections was not built, it needs the owner's go-ahead); E runs on the
  app's shared renderer main thread and P has no step bound there (N5 decides); a hidden WebView
  may delay the job's start; not run on a phone yet (N7); the protocol is P only until N5
  (strategy field, per-kind error classes).
- ⏭ Next: N5 (ladder, locator cache, flag wiring). Continuation prompt for a new agent:
  `MASTER_KEY_PHASE1_2_CONTINUE_N5.md`.

## Phase 1.2 — N3 strategy B (behaviour) — N3 done (`52e1945`, CI 38164686967 ✅)

- ✅ Strategy B in `own.solver.core.js` (reached only with request `strategy: "B"`; the app never
  sends it before N5, so P and the app are unchanged): every function is registered when the
  player creates it, probed on one instance (screen), re-checked on fresh instances (T2),
  classified, grouped and ranked (T1), then SelfCheck as in Phase 1.1.
- ✅ Probe by call shape + static key propagation: the real n transform is a dispatcher branch
  (e.g. `Gn(17,369,_)`, reached only through `v = w ^ F` keys), so constant arguments bind the
  callee's parameters, locals set once from constants are evaluated, a reassigned name is never a
  constant (this bug had hidden one player's n), and chains through nested calls or locals set
  once (≤ 4 links) are one shape. AST only; plan §2 "Plan adapted (N3)".
- ✅ Classifier rules from data (all 32 files): n output length follows the input, not an
  encoding, at least half of 16 single-character changes alter it; trivial sig maps (identity,
  cut, reverse, rotation, swap) refused; a single swap only as a weak candidate with builder
  evidence (needed by `74edf1a3`; a name the builder calls through a plain alias counts, found
  by the first mutation run: M5 on `74edf1a3`) and only when nothing strong is confirmed.
- ✅ `hint: false` in the request switches P's hint off; "B alone" runs use it.
- ✅ Harness: `node scripts/verify-own-solver.mjs --strategy B` (B alone; `--hint` ranks by P;
  `--verbose` shows shapes, key bindings, chains and the builder trace); mutation runner
  `--strategy P|B|P,B` (column "own B"). `--faults` runs the Phase 1.1 set (10 per file) on B
  too; B has its own hooks for the decoy (one more confirmed candidate with other outputs must
  end in `disagree`) and the raw encoding (SelfCheck must refuse).
- ✅ Harness memory fix: player code run by B's probes leaves rejected promises nobody handles;
  Node keeps each one (and with it the whole player context) until the event loop turns, so the
  long synchronous runs hit the 2 GB heap limit (the first B fault run and mutation run stopped).
  The runners now let the event loop turn after every solve (`settle()` in `harness.mjs`); the
  rejections are ignored, never printed.
- ✅ Corpus: B alone 32/32 files, own = ejs on 260/260 inputs, exactly one n group and one sig
  group per file, wrong 0. P unchanged: 32/32, 260/260. Faults (320 runs each): P 0 wrong /
  0 missed; B 0 wrong / 0 missed.
- ✅ Mutation suite (448 cases, 0 skipped, **wrong = 0**), files survived of 32, ejs | own P |
  own B alone: M1, M1w, M2, M4, M8, M10, M11, M11f 32 | 32 | 32 · **M3 0 | 0 | 32** · M5
  24 | 32 | 32 · M6 0 | 32 | 32 · **M7 11 | 31 | 32** · M8t 0 | 32 | 32 · M9 0 | 32 | 32. All:
  ejs 291 (65 %), own P 415 (93 %), **own B alone 448 (100 %)**; B fixes P's only failures (M3,
  M7 on 5203c085 es6). Plan §4.
- ✅ Tests: `core-strategy-b.test.mjs` (11, fake players): known wrappers, an M3-style rewrap
  (P fails, B answers), hash/hex decoys dropped, a second n-like function → `disagree`, trivial
  maps and a reverse/swap-only player → no candidates, a non-deterministic function, a looping
  function, dispatcher keys `dsp(5,98,_)` + the reassigned-local guard, a sig chain, a one-swap
  sig only with builder evidence (also through an M5 alias), no values in the report. In the CI
  JS step; locally 140/140.
- Speed (computer, 5203c085 main): first B run ≈ 5 s (prepare ≈ 2–2.5 s, screen ≈ 2 s,
  confirm ≈ 0.3 s); budgets in plan §3 N3. Phone: measured in N5/N7 (target ≤ 10 s).
- Risks: phone time not measured yet; the weak sig tier depends on builder evidence; a new
  player shape may need a new shape form (the mutation suite and the canary watch for it).
- ⏭ Next: N4 (strategy E, isolated page environment). Continuation prompt for a new agent:
  `MASTER_KEY_PHASE1_2_CONTINUE_N4.md`.

## Phase 1.2 — N2 live check V (`9e15101`, CI 38117539688 ✅; phone measurement pending)

- ✅ `StreamCheck` (`extractor-master/.../modules/youtube/StreamCheck.kt`): asks YouTube whether
  one solved stream URL opens, through the client's existing one-byte probe (`Range: bytes=0-0`).
  Verdicts `PASS` / `FAIL` / `UNKNOWN`; a 403 fails the strategy only when a control (a direct URL
  of the same lookup) opens; control fails too → `UNKNOWN`; no control → `FAIL` for this lookup
  only. At most 4 checks per player version per UTC day (a check = target + control only after a
  403); proven verdicts cached per answer for a day. Never outside a user-started lookup, never on
  bot/login/age/private/DRM states, only on a returned `googlevideo.com/videoplayback` host, the
  download's headers without cookies or credentials, nothing logged, no URL or value in a result.
- ✅ n rule as a policy value: `PENDING` (now: 2xx = pass for sig, unknown for n; a proven 403
  fails both), `JUDGES_N`, `SIG_ONLY` (T4).
- ✅ `StreamCheckTest`: 13 tests with a fake HTTP client (pass, 403 + control, 403 without
  control, control fails, timeout / network / stuck / thrown, other statuses, unsupported client,
  budget, cache, terminal states, host rule, header stripping, nothing logged, n rule).
- ✅ Opt-in phone measurement `StreamCheckMeasureTest` + `bash scripts/stream-check-measure.sh`:
  right n, unchanged n, wrong n, wrong sig, right again → status class, bytes and time for the
  first 1 MiB; ejs and the own runner must agree first; report without addresses; suggests the n
  rule. **Owner action:** run it once on the phone; the result goes into plan §2.
- ✅ Follow-up (owner has no computer, mobile data only): the CI artifact also holds the test APK
  (`f5cff7b`), and the debug app has an owner tool **"YFT stream check"** (launcher entry, only
  with both Master flags on; Start → report → Copy result). Same code as the phone test
  (`StreamCheckMeasurement` in `app/src/debug`).
- Plan adapted (plan §3 N2): no new HTTP method (the probe is the bounded read); not wired into the
  YouTube module yet (N5, behind `ownSolverNext`; Phase 1.1 unchanged); ledger in memory (N5 adds
  the file). Phase 1.3 D4 reuses `StreamCheck`.
- Local check: `./gradlew :extractor-master:test` and `:app:compileDebugAndroidTestKotlin`
  (JDK 17 + Android SDK installed in the sandbox) before the push.

## Phase 1.3 — plan + prompt (no code)

- ✅ `MASTER_KEY_PHASE1_3_PLAN.md` + `MASTER_KEY_PHASE1_3_PROMPT.md` (2026-10-11): an own client
  ladder so that Master keeps working when YouTube's visionOS client fails, as the own solver did
  for ejs. A client table v2 of public app identities that need no token; every answer judged by
  what it contains (D direct, S player script solved by the own solver, never a token client); a
  health cache on the phone; a row check with YouTube (shared with Phase 1.2 N2); a response
  simulator C1–C13 (R6 chain vs ladder, wrong rows 0). Tasks D1–D7, all TODO; START_AT D1 (the
  owner chooses the order against Phase 1.2 N2–N7).
- Why: in yt-dlp's client table (read 2026-10-11) visionOS is the only client that needs neither
  the player script nor a token (android_vr: all formats 403 since 2026.08.17; android/ios need a
  token), so the second leg must be the no-token script clients (TV, embedded) through the own
  solver.

## Phase 1.2 — N1 change simulator (part 2–3: 8 more classes + full baseline) — N1 done (`765e91f`, CI 38113204972 ✅)

- ✅ `mutate.mjs` new classes: **M4** sig helper object → separate functions, **M5** functions
  with 3+ call sites called through a fresh alias, **M7** control-flow flattening (switch in a
  loop) of wrapper-level functions with a plain 3–40 statement body (chance 0.7 each; refuses
  async/generator, labels, function/class/`let`/`const` at the top; keeps directives), **M8**
  global `encodeURIComponent`/`decodeURIComponent` through fresh wrapper functions at the end of
  the wrapper body, **M8t** the same wrappers first, **M9** `navigator.userAgent`,
  `location.hostname`, `document.createElement("div")`, `window.top` read into unused variables in
  the middle of the init, **M11** harmless `var`s at the top, **M11f** harmless function
  declarations at the top.
- Plan adapted: M8 cannot change how the result is applied (that changes the right answer
  itself), so that part moves to the simulated V (N2/N5); M8t, M11 and M11f were added to explain
  the M8 finding (below); M7 flattens other functions too, not only n/sig. Details: plan §3 N1.
- ✅ Runner: report lines now say which kind failed (`safe (n 0/3, sig 3/3)`) and own P's failure
  class (`[no candidates]`); counts only, no player text.
- ✅ Tests: `mutate.test.mjs` 47 (was 29; new: M4, M5, M7 closures / early return / loops /
  hoisting / refusals, M8, M8t, M9, M11, M11f keep behaviour); CI JS step locally 129/129.
- ✅ Full baseline (32 files × 14 classes = 448 cases, 0 skipped; **wrong = 0**); files that
  survived out of 32, as ejs | own P: M1 32 | 32 · M1w 32 | 32 · M2 32 | 32 · **M3 0 | 0** (both
  safe: new wrapper) · M4 32 | 32 · **M5 24 | 32** · **M6 0 | 32** · **M7 11 | 31** ·
  M8 32 | 32 · **M8t 0 | 32** · **M9 0 | 32** · M10 32 | 32 · M11 32 | 32 · M11f 32 | 32.
  All cases: ejs 291 (65 %), own P 415 (93 %).
- Findings: ejs breaks (safely) on aliased call sites, string tables, flattening, new encode
  helpers met first (not at the end, not other new code at the top) and browser-object reads
  (the #56 class); own P survives all of these except M7 on 5203c085 es6 (`no candidates`). Both
  break on a new wrapper (M3): strategy B's first target. Numbers: plan §4.
- ⏭ Next: N2 (live check V + measuring the n behaviour on the owner's phone), then N3 (strategy B).
  Continuation prompt for a new agent: `MASTER_KEY_PHASE1_2_CONTINUE.md`.

## Phase 1.2 — N1 change simulator (part 1: 6 classes + baseline)

- ✅ `scripts/own-solver/mutate.mjs`: behaviour-keeping AST mutators (meriyah + astring from main's
  `yt.solver.lib.min.js`, test time only, never shipped) with a scope model first (T3: hoisting,
  block/catch/switch/class scopes, named function expressions, shorthand keys, `new.target`;
  refuses `eval`/`with`). Classes: **M1** rename function/block/catch locals with minifier-style
  names (globals, the wrapper parameter and locals deliberately named after browser globals such
  as `var window = this` stay), **M1w** M1 + the wrapper parameter, **M2** dotted ↔ computed
  access, **M3** re-wrap as `void (fn).apply(receiver, [args])` (exact receiver), **M6** strings
  into one shuffled `"…".split(sep)` table (directives and keys untouched), **M10** unreachable
  n/sig-like decoys.
- ✅ `scripts/verify-own-solver-mutations.mjs` (`--quick`, `--only`, `--class`, `--seeds`): mutates
  each cached player and judges ejs and own P per class (survived / safe / **wrong**); report
  `build/own-solver/mutation-report.json` (classes and counts only). Plan adapted: truth = ejs on
  the **original** player (answers depend on the input, not on how the code is written); every
  solve goes through `harness.mjs`'s bare V8 context, as the parity runner does — no other way of
  running player code (an ad-hoc truth hook was rejected by the safety review). A mutation that
  changed behaviour (ejs and own agree on a new value) is skipped, never counted as wrong.
- ✅ Tests: `mutate.test.mjs` 29 (scope cases, M1/M1w/M2/M3/M6/M10 keep behaviour, determinism),
  added to the spike CI JS step. Plan adapted: `--quick` needs the player downloads, so it runs in
  N6's canary workflow, not in this CI step.
- ✅ Baseline, full (32 files = 8 players × 4 builds, 2 seeds, 192 cases; wrong = **0**): M1 ejs
  32/32, own 32/32 · M1w 32/32, 32/32 · M2 32/32, 32/32 · **M3 0/32, 0/32** (both fail safely:
  an unknown wrapper) · **M6 ejs 0/32, own 32/32** (own resolves string tables) · M10 32/32,
  32/32. Quick run (8 phone builds) matches.
- Fairness fixes found while measuring: ejs first "failed" M1 only because the draft renamed
  `var window = this` and used `_m$` names; both were unrealistic and are fixed (ejs then 8/8).
- → M4, M5, M7, M8, M9 and the full 32-file baseline: done in part 2–3 (above).

## Phase 1.2 — plan + prompt (no code)

- ✅ `MASTER_KEY_PHASE1_2_PLAN.md` + `MASTER_KEY_PHASE1_2_PROMPT.md` (2026-10-11): next-level own
  solver that keeps working when YouTube changes the shape of its code — behaviour discovery (B),
  a live check with YouTube itself (V), an isolated page environment (E), a strategy ladder with a
  locator cache, and a change simulator (M1–M10) that measures ejs vs own (wrong values must stay
  0). Tasks N1–N7, all TODO; START_AT N1 (baseline).
- Why: Phase 1.1 equals ejs (260/260) because it uses the same shape-based method, so it would
  most likely break together with ejs.

## Phase 1.1 — S6: canary + runbook

- ✅ Computer canary `node scripts/own-solver-canary.mjs [player ID]` (owner-run, never CI):
  today's player from YouTube's embed API, all four builds (main, tce, es6, phone), main's ejs
  and the own core on the same 3 n + 3 sig inputs (one sig with the encoding edge characters),
  pass/fail per kind; report `build/own-solver/canary-report.json` (player ID, counts, timings
  only); exit 0 pass / 1 fail / 2 could not run; on failure it prints the runbook commands.
  Verdict rules tested offline (`canary.test.mjs`, 3, in the JS step).
- ✅ Phone canary `bash scripts/own-solver-canary.sh` → opt-in `OwnSolverCanaryTest`
  (`-e yft.ownSolverCanary 1`, never default CI): today's player through main's ejs runner and
  the own runner in their real WebView engines on the phone; verdict per kind, timings (ejs, own
  first run, own cached run) and the own runner's failure classes; report in the app's files
  `own-solver/` (checked for addresses). Compiled by CI (`compileDebugAndroidTestKotlin`).
- ✅ Canary report written (2026-10-11): today's player `5203c085` (in the corpus) — n PASS,
  sig PASS; 3/3 n and 3/3 sig equal to ejs on main, tce, es6 and phone; own core 1.1–2.1 s per
  build in this computer's Node `vm` (ejs 1.1–2.2 s).
- ✅ Runbook: plan §6 now lists the exact commands (canary → `--add` → `--only`/`--prepare` →
  classify G1–G4/new → fix → full parity + `--faults` + JS tests → push, phone canary, ship).
- ⏳ Owner: the phone canary and the live check with the opt-in APK (both flags on): YouTube rows
  equal main's on the parity URLs, no throttled rows, bot-check/age/private stay terminal (§4).
- ✅ CI (S6, `ab42a22`): Master opt-in debug APK run `38102358850` — success (JS step incl. `worker` +
  `canary` tests; APK built with the own solver on).

## Phase 1.1 — S5: own runner wiring (flag), cache, failure mapping

- ✅ `extractor-master/.../solver/OwnPlayerScriptRunner` implements main's `PlayerScriptRunner`:
  - **Fetch** exactly as main's runner: the address the page named (phone build), then the
    desktop build of the same version; page as Referer; only
    `https://www.youtube.com/s/player/<id>/…/base.js`; 8 MB cap; 45 s engine timeout.
  - **Cache:** the core's prepared program per player ID, in memory (2 players, least recently
    used dropped). A cached program that no longer runs is rebuilt once from the player; a
    SelfCheck refusal is final (a fresh parse would not change it).
  - **Failure mapping:** no engine → `Unavailable`; engine timeout/no reply, worker error,
    unreadable reply, a whole-run core failure, every asked kind refused, or no asked input
    answered → `Failed(PLAYER_SCRIPT_REQUIRED)`; a failed player download keeps its own reason
    (as main's runner). One kind refused → `Success` with the other kind only, and the YouTube
    reader drops the streams that needed the refused kind. `lastFailures` keeps failure
    classes only (for the canary).
  - `OwnSolverProtocol`: hand-encoded job (player escaped once, U+2028/2029 escaped), bounded
    parse of the reply, only asked inputs kept, failure strings cut to short classes.
- ✅ Android `extractor-master-android/.../solver/WebViewOwnSolverEngine` (adapted copy of main's
  `WebViewSolverEngine`; main's file is unchanged) + `OwnSolverPageRoutes`: a fresh offscreen
  WebView per run, reserved asset host, own folder `yft-own-solver/` (`own-solver.html`,
  `own-solver-page.js`, `own-solver-worker.js`, meriyah, core), the same CSP, a dedicated blob
  worker, no cookies/storage/network; main's ejs files are never routed. The worker passes only
  n/sig inputs to the core (a job cannot switch on fault injection or vectors), takes its reply
  path before any player code runs, and answers with error classes only.
- ✅ **Selection:** new flag `-Pyft.ownSolver` (default `false`; BuildConfig
  `OWN_SOLVER_ENABLED` in debug/preview, always `false` in release).
  `SiteAdapterModule.providePlayerScriptRunner` → `OwnSolverSelection.runner`: the own runner
  only when `ownSolver && masterCapture`; otherwise main's `YouTubePlayerScriptRunner`, built
  exactly as on main (only the chosen factory runs). With both flags on, YouTube's adapter
  (main's code, identical to Master's R6 copy) asks visionOS first, then the own runner; the ejs
  core is not loaded on that path. `MasterYouTubeModule(http, playerScripts)` accepts the runner
  too (default none, as in R6); the app leaves it without one because main's adapter answers
  YouTube pages before Master is asked.
- ✅ CI's opt-in APK is built with `-Pyft.ownSolver=true` (the manual run's `own_solver` input
  builds it off); the step summary names the setting.
- ✅ Tests: `OwnPlayerScriptRunnerTest` (12), `OwnSolverProtocolTest` (8), `OwnSolverSelectionTest`
  (3: flags off → main's runner, the own one never built), `OwnSolverYouTubeTest` (3: the real
  reader + runner + protocol with a scripted engine — cipher streams signed with own values; sig
  refused → cipher streams dropped (`PLAYER_SCRIPT_REQUIRED`) while n-only streams stay; broken
  engine → no stream), `OwnSolverPageRoutesTest` (3), JS `worker.test.mjs` (9: raw/prepared jobs
  on the fake player, no program unless asked or after a failure, refused kind, error classes,
  no fault/vector injection, reply path safe from player code, the page loads only its four
  files into a blob worker, routes ↔ files ↔ CSP).
- ✅ Local run (flags on): `:extractor-master:test` 310 (0 failures, 2 skipped: canaries),
  `:extractor-master-android:testDebugUnitTest` 59, app `MasterMainMoreSheetTest`,
  `BrowserMasterFlowTest`, `BrowserMasterFallbackTest` 25, `:app:compileDebugAndroidTestKotlin`
  OK; JS step 82/82; drift 107/0. The APK itself is built by CI (no NDK here).
- ⚠️ Not checked here: the WebView engine on a real phone (no device in this environment) → the
  S6 phone canary and the owner's live check.
- ✅ CI (S5, `ab42a22`, pushed together with S6): Master opt-in debug APK run `38102358850` —
  success.

## Phase 1.1 — S4: G3 encoding checks + SelfCheck + fault injection

- ✅ SelfCheck in `own.solver.core.js`: the values of a kind are returned only when every check
  passes; any failure drops the whole kind (`failed: "<kind>:<reason>"`, no values), so the
  caller falls back (S5) instead of using a doubtful value.
  - **Probes:** n runs two fixed probes; sig runs a map probe (64 distinct URL-safe
    characters) and an edge probe of the same length with `% / = + & ? #`, space, tab, newline
    and a broken `%zz` escape. Probes and the request's vectors go through the same multiTry,
    before the real inputs.
  - **Checks, in order (reason):** one value from every candidate and read path (`disagree`);
    shape (`shape`): n URL-safe, not its input, length ≤ 2 × input + 8; sig only reuses its
    input's characters and keeps a plausible length; distinct n inputs give distinct outputs
    (`not-distinct`); every probe solved (`probe`); **G3 index map:** the positions the map
    probe reveals, applied to the edge probe, must give exactly the edge probe's output
    (`encoding`), which catches a missing or doubled encode/decode of special characters;
    known vectors match (`vector`); the first probe still gives the same value at the end
    (`unstable`).
  - Reports name kinds, counts, candidate/path indexes and error classes, never inputs or
    outputs.
- ✅ Fault injection for tests (`request.faults`, never set by the app): a decoy candidate that
  changes n or sig by one rotated character, sig passed without URI encoding, and each G4
  global (`window`, `self`, `location`, `navigator`, `document`, `XMLHttpRequest`) or all of
  them left out.
- ✅ Harness: a sig edge input with `% / + & ? #`, space, tab and `=` per file (sig lengths
  104/108/107); `node scripts/verify-own-solver.mjs --faults` runs the 10 faults on every file
  against ejs: any wrong value fails, and so does a fault that should FAIL a kind but does not.
- ✅ Corpus: parity 32/32 files PASS, own = ejs on 260/260 inputs. Faults: 320 runs, wrong
  values 0, expected FAILs missed 0 (decoy → `disagree` of the kind it changes, missing
  encoding → sig FAIL; a missing global gave right values or a FAIL, never a wrong value).
- ✅ Offline tests `scripts/own-solver/tests/core-selfcheck.test.mjs` (12): clean pass, a
  wrong candidate inside the player, injected decoy/encoding/global faults, n and sig shape,
  G3 index map, distinct n, vectors, stability, no values in probes or reports; harness test
  for fault verdicts (harness 11). Workflow JS step: 70 tests (own solver 42).
- ⚠️ Limit: a lone candidate that is wrong but plausible (right shape, consistent and stable)
  is caught only by known vectors or a live check; a runner-level live 1-byte probe is planned
  with S5.
- No change in `main`, the app or ejs assets; the core is still unused with the flags off.
- ✅ CI (S4, `00aa458`): Master opt-in debug APK run `38100304754` — success.

## Phase 1.1 — S3: G4 environment + multiTry runner

- ✅ `own.solver.core.js` gains `yftOwnSolver.run(prepared, request)` (`solve` = `prepare` +
  `run`; `prepare` once, `run` many times). It runs inside the existing solver worker; no new
  isolate, no host access.
  - **G4 environment:** `window`, `self`, `location`, `navigator` and `document` are installed
    as own properties of the worker's global object (`Object.defineProperty`), plus an
    `XMLHttpRequest` stand-in without any network when the worker has none. `location` is a
    fixed test page URL with no-op `assign`/`replace`/`reload`: nothing navigates and nothing
    reaches the host page. The user agent and language come from the request (defaults
    otherwise).
  - **Instantiate:** the prepared program is compiled once per run (`Function(registry,
    program)`); failing top-level statements are only counted (`topLevelErrors`).
  - **multiTry:** every candidate builds a URL object for the test page. Its parameter setter
    and getter are found by a marker round-trip (`set`/`get` first, else any two-argument /
    one-argument method pair). n is set and read back through every read path: the getter right
    after building, then every zero-argument method on a fresh object (the getter again and the
    query of a returned URL string). A failing path or candidate only loses its own values;
    errors name candidate/path indexes and error classes, never values.
  - **G3 application:** sig goes in URI-encoded, the way a signatureCipher carries it, and comes
    out decoded exactly once; a query key seen twice or not at all gives no value.
  - A value is kept only when every candidate that produced one produced the same; otherwise
    the input counts as a conflict and gets no value.
- Plan adapted: the G3 *application* (encode in, decode once out) landed with multiTry in S3,
  because a sig cannot be read without it; S4 adds the G3 *checks* and the SelfCheck.
- ✅ Corpus parity (`node scripts/verify-own-solver.mjs`): 32/32 files PASS, own = ejs on
  228/228 inputs (vectors + generated n/sig inputs, every variant of every player).
- ✅ Offline tests `scripts/own-solver/tests/core-run.test.mjs` (9) on the fake player: n and
  sig in every code style and wrapper, sig encoded once / decoded once, a failing statement or
  candidate does not stop the rest, errors carry no values, G4 globals are own properties and
  nothing navigates, worker-shaped context, refusal of a missing/failed preparation, prepared
  program reuse, empty request. In the workflow's JS step (now 29 own-solver tests).
- Timing (desktop Node `vm`): prepare ≈ 0.9–1.8 s per player, run ≈ 0.1–0.3 s; the phone is
  measured in S5.
- No change in `main`, the app or ejs assets; the core is still unused with the flags off.
- ✅ CI (S3, `ace7276`): Master opt-in debug APK run `38099849746` — success.

## Phase 1.1 — S2: own solver core (parse, G1 unwrap, G2 candidates)

- ✅ `extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js` (YFT's own
  code; no ejs file copied or used): `yftOwnSolver.prepare(playerText)`.
  - **Parse:** meriyah (`ranges`, `webcompat`), vendored unmodified from npm `meriyah@6.1.4`
    as `meriyah.umd.min.js` below an ISC header (`docs/THIRD_PARTY_NOTICES.md`). No astring:
    the program is built from source slices, never regenerated.
  - **G1 unwrap:** only the known wrappers (`var ns = {}; (function (g) {...})(ns)`,
    `(function () {...}).call(this)`, an arrow IIFE); anything else → `FAIL unexpected
    structure`. Kept: declarations, assignments and control statements; dropped: other
    expression statements (calls, `new`, `a && b()`). Every `var` declarator and every
    assignment of a sequence runs in its own `try` (failures only counted), so one browser API
    the worker lacks cannot stop the rest. The wrapper text itself is kept verbatim.
  - **G2 normalise (matching only):** string tables (`T = "...".split(";")` / string arrays)
    resolve `x[T[3]]` like `x.name`; dotted, computed and template names alike; sequences
    flattened (also in `return`); `var`/`let`/`const`, function and arrow forms alike; a table
    name the function binds itself is not resolved.
  - **G2 candidates, all matches, two independent patterns:** (A) a definition whose
    unconditional steps mark a local URL object with `("alr", "yes")` and return it; (B) call
    sites `f(<…url…>, <sp>, <x.s>)` (the signatureCipher shape). The candidates are read at the
    end of the player's own top level.
- ✅ Corpus (`node scripts/verify-own-solver.mjs --prepare`): 32/32 files yield candidates
  (each: 1 candidate, found by both patterns; kept ≈ 92–98 % of top-level statements).
- ✅ Offline tests `scripts/own-solver/tests/core-prepare.test.mjs` (10) on a fake player
  written for the tests (`fake-player.mjs`, no YouTube code): known wrappers accepted, unknown
  ones and parse errors fail cleanly, both patterns in es5/es6/sequence styles, all matches
  collected, normalisation, shadowed tables, program keep/drop/guard. In the workflow's JS step.
- Parity runner: the own core is loaded with the vendored meriyah only (no ejs lib).
- No change in `main`, the app or ejs assets; the new asset is unused with the flags off.
- ✅ CI (S2, `f0d7be4`): Master opt-in debug APK run `38099393252` — success.

## Phase 1.1 — S1: own-solver harness and corpus

- ✅ `scripts/verify-own-solver.mjs` (parity runner, beside `verify-youtube-solver.mjs`) +
  `scripts/own-solver/harness.mjs` (pure parts): for every corpus file it runs main's bundled
  ejs core (the oracle) and, once built, the own core
  (`extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js`,
  `yftOwnSolve({player, n, sig})`, written in S2–S4) on the same player and inputs: the ejs
  vectors plus deterministic generated inputs (n 16/18/19, sig 104/108 chars, seeded per
  player). A file passes when ejs meets every vector and solves every input, and (once built)
  the own core equals ejs on all of them. Solvers run in a bare V8 context; the report
  (stdout + `build/own-solver/report.json`) names players, variants, kinds and input indexes
  only, never a solver input or output (checked: 0 of 39 known values in the report/log).
  Exit 0/1/2 = pass/fail/usage. `--only`, `--own <file>`, `--record`, `--add today|<id>`.
- ✅ `scripts/own-solver/corpus.json`: 8 players × 4 variants (main, tce, es6, phone) = 32
  files, player IDs + SHA-256 + size only. The 7 ejs-vector players (`74edf1a3`, `901741ab`,
  `e7573094`, `9fcf08e8`, `21cd2156`, `76ad2fe8`, `631d3938`) + today's player `5203c085`
  (generated inputs only). Player text is downloaded at test time into
  `build/own-solver/players/` (gitignored) and hash-checked; never committed.
- ✅ Live run: 32/32 files pass; ejs meets all vectors (74edf1a3 5/5, the other six 2/2 per
  variant = 68 checks) and solves every input; own core: not built yet (S2–S4).
- ✅ Offline test `scripts/own-solver/tests/harness.test.mjs` (10 tests: committed corpus
  valid + full coverage, bad corpus entries named, deterministic URL-safe inputs, vector
  merge, oracle faults, own disagree/missing/failed, absent vs download faults, summary leaks
  no value), added to the workflow's JS step.
- No change in `main`, the app or assets; flag-off behaviour unchanged.
- ✅ CI (S1, `491dc0e`): Master opt-in debug APK run `38094820752` — success.

## R9 — Signed recipe config (data only)

- ✅ `recipes/RemoteRecipes` (+ `RemoteRecipeConfig`, `RemoteRecipeSignature`): the bundled
  recipes (`ContractRecipes`) stay the defaults and always work. A config replaces their data
  only when it is **signed** (ES256: ECDSA P-256 + SHA-256 over the config text, envelope
  `{"alg","payload","signature"}`, hex because `java.util.Base64` is missing below Android 8),
  passes the **schema** (`extractor-master/recipes/master-recipes.schema.json`; unknown site/key,
  wrong type or out-of-bounds value → the whole config is ignored), is **newer** than the one in
  use (no rollback) and **unexpired** (`expires`; an expired config falls back to the bundled
  recipes). Data only, no code: `endpoint` (same https host, known placeholders; a GraphQL
  `doc_id` goes in its query), `headers` from a fixed list (never Cookie, Authorization,
  User-Agent), `agent`, and the key table (ID fields/paths, item lists, media keys, title,
  duration, thumbnail paths). DRM, no-video and login-wall paths can only be **added**. Hosts,
  cookie domains, body caps, regular expressions, photo/access answers never come from a config.
- ✅ Asked by `SiteContracts` before the site, bounded: HTTPS `raw.githubusercontent.com` only
  (answer host locked), no cookie, 128 KiB, 1.5 s, at most once a day (once an hour after a
  failed ask); L2 reads with the same recipe source (`ContractLayer(recipeOf)`). App: one
  process-wide config (`MasterRecipeConfig`); `-Pyft.masterRecipeKey=<hex>` (public key) and
  `-Pyft.masterRecipeUrl` (default `recipes/master-recipes.json` on this branch). **No key = the
  bundled recipes and no request** (the default build). Flag off = main.
- ✅ Owner tools: `scripts/master-recipe-sign.sh` (`keygen` once → private key stays off the
  repo, prints the public key; `sign <key> <config> <out>` checks the top-level shape and writes
  the envelope); `extractor-master/recipes/master-recipes.example.json`.
- ✅ Tests: `RemoteRecipesTest` (5) — a signed config changes Instagram's endpoint, a header and
  its media key, and the engine reads the renamed embed from the key table via `CONTRACT` (config
  asked first, no cookie, 128 KiB; hosts, cap, cookie domain, photo markers, wall paths
  unchanged; other sites stay bundled); 32 bad envelopes/configs (unsigned, empty signature,
  another key, tampered payload, other `alg`, extra envelope key, not JSON, `answerHosts`/
  `cookieDomain`/`maxBytes`/`itemNumber`/`photoMarkers`, Cookie/User-Agent/CRLF headers, another
  host, http, user part, unknown placeholder, deep path, bad mime, unknown media key, bad wall
  path, unknown site, schema 2, version 0/1.5, expired, no such date, unknown key, oversized) →
  ignored, bundled recipes unchanged and the embed still answers; no rollback, expiry falls back;
  asked once a day / once an hour after a failure / never off GitHub; no key, a bad key, a P-384
  key, http or a non-GitHub address → never asks; an openssl-signed envelope (throwaway key, its
  private half deleted) verifies; schema keys, sites, headers, mimes and orders match the reader;
  the example config is valid.
  Local: `:extractor-master:test` 284 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: `bash scripts/master-recipe-sign.sh keygen ~/yft-recipe-key.pem`, build with
  `-Pyft.masterRecipeKey=<printed hex>`, sign a config and commit it as
  `recipes/master-recipes.json`. Deviations: the bundled defaults stay in code (`ContractRecipes`,
  type-checked) instead of an asset copy; the app keeps the config in memory only (asked again
  once per process start, then daily); coverage is only data-only breakages (~30–40%).
- ✅ CI (R9, `f08b895`): Master opt-in debug APK run `38093579832` — success.

## R8 — Per site (L2 contract first, then L3 + capture)

- ✅ §3 rule conflict, decided (MASTER_KEY_ARCHITECTURE.md §3): layers still never fetch. The
  **engine** asks the identified video's own endpoint once, as stage `CONTRACT` (before page
  data and capture), through the caller's bounded `ExtractorHttpClient`
  (`contract/SiteContracts`: HTTPS, the client's redirect limit and timeouts, a per-site body
  cap, answer host lock, its own 6 s share of the 20 s budget so capture keeps time). L2
  (`ContractLayer` → `ContractReader`) only reads the answer, delivered as
  `PageSnapshot.contract`, which no page or capture can fill. Recipes are data
  (`recipes/ContractRecipes`: endpoint, headers, agent, cookie domain, host locks, key table,
  DRM paths, access phrases, expiry/title/duration paths).
- ✅ When: only with the caller's identity (`MasterRequest.identity`; the app passes its
  adapter's `SitePageIdentity`, `SiteAdapterCoordinator.identity`), never for a short-link
  code, never after `LOGIN_REQUIRED`/`BOT_CHECK`/`PLAYER_SCRIPT_REQUIRED` (those need the
  user's own playback) or a `NEVER_FALLBACK` reason; once, no retry. Engine without
  `contracts` = R7 engine. App: `create` passes `SiteContracts(OkHttpExtractorClient(client))`
  (the same client as the R6 module).
- ✅ Reading: the key table first (one page video, MAIN, rows keyed `site:contentId` = the
  app's key); when it finds nothing (a renamed key) L4 + L3 read the same answer
  (rename-proof). DRM paths → `DRM_PROTECTED`; access phrases count only when no file was found
  (main's rule) and stop with their reason: 0 capture, 0 probes. An answer naming another video
  gives nothing. Rows share the probe budget; success → `MasterStage.CONTRACT`.
- ✅ Performance: a contract answer needs no playback: one request replaces the
  play-and-capture round; a slow endpoint costs at most 6 s, then capture runs as before.
- ✅ **Vimeo**: `player.vimeo.com/video/{id}/config` (`?h=` unlisted hash kept; host lock;
  Referer = clip page; the user's cookie only inside vimeo.com, as main). Key table = main's
  renditions (progressive by height, then HLS/DASH from the default CDN, `url` then
  `avc_url`), `request.files.drm`/`video.drm`, main's access phrases, `request.expires`. One
  request instead of main's page + config. Fix: `UrlPolicy.videoKey` had no Vimeo mapping, so
  Master's Vimeo rows were keyed `master:vimeo.com:…` and the app's focused-key filter dropped
  them; now `vimeo:…`, and with an identity every row is keyed by the identity's site.
- ✅ Vimeo tests: `VimeoContractTest` (10) — parity with main's `VimeoExtractor` on
  `player_config.json` (main's 5 rows ⊆ Master's, sizes 1080/720/540, every row probed, key
  `vimeo:123456789`, 1 request, 0 capture); DRM/private/password/geo = main's reasons with 0
  capture and 0 probes; no files/insecure/malformed/expired → capture; unlisted hash, Referer
  and cookie; cookie never off vimeo.com; another video's answer; renamed keys found by shape;
  login/bot-check/player-script → no request; no contracts or identity → no request; slow
  endpoint → capture (capture rows keyed `vimeo:…`). App:
  `BrowserMasterFallbackTest.contractRowsReachTheAppUnderTheAdaptersKey` (rows reach the app
  under the adapter's key, 0 capture). Drift: 8 new rows (53), 0 drift.
  Local: `:extractor-master:test` 254 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `vimeo-clip`, `vimeo-embed`, `vimeo-unlisted`, `vimeo-ondemand` on a
  phone.
- ✅ **X**: `cdn.syndication.twimg.com/tweet-result?id={id}&token={token}` — the widget's own token
  (`contract/WidgetToken` = main's `XSyndication.token`/`radixString`), host lock, main's headers,
  no X cookie, 2 MiB cap. Key table = main's: `mediaDetails` items (`/video/N` names the item, else
  the first with files), MP4 variants by bitrate (sizes from `/vid/WxH/`), the playlist only
  without MP4s, files only from `video.twimg.com`, the quoted post's media only when the post has
  none (never by number), title = the text's first line without its trailing link, per-item
  duration and poster. Posts the answer does not show (tombstone, `{}`, photos only, 404) go to
  capture: main keeps the generic detector there and the user may be signed in, so X has no
  access phrases. Reader additions (data-driven, all sites): item metadata from the chosen item,
  `postText` titles, `thumbnailPath`.
- ✅ X tests: `XContractTest` (6) — parity with main's `XExtractor`: `tweet_video` (same request
  URL, heights 1280/852/568, title, duration, no cookie, no playlist row, 0 capture),
  `tweet_mixed` (`/video/3` → 720 and the foreign host dropped; no number → 1080/270),
  `tweet_quoted`/`tweet_gif`/`tweet_hls_only` (same kinds and durations; `/video/2` never picks
  a quoted item), `tweet_photos`/`tweet_tombstone`/404 → capture with 0 probes, another post's
  answer → nothing, main's token vectors. Drift: 19 new rows (72), 0 drift.
  Local: `:extractor-master:test` 260 (0 failures, 2 skipped); full flag-off app unit tests 938
  (0 failures, 66 skipped) and `lintDebug` OK.
- ⏳ Owner-run: live parity `x-nasa`, `x-multi-video`, `x-gif`, `x-protected` on a phone.
- ✅ CI (Vimeo + X, `d705e03`): Master opt-in debug APK run `38086897673` — success.
- ✅ **Facebook**: the embedded-video player page `www.facebook.com/plugins/video.php?href={href}`
  (one page for watch, reel and page videos; live check 2026-10-10: `hd_src`/`sd_src` with the
  matching `video_id` inside `s.handle({...})`), asked as main asks its public page: desktop Safari
  (main's `AVC_LADDER_USER_AGENT`), main's navigation headers, **no session cookie**, 6 MiB cap,
  host lock. Key table = main's page parser (`progressive_urls` in the delivery fragment and on
  the node, the legacy fields best first, inline DASH tracks, manifest addresses only without
  inline tracks, `video_id`/`videoId`/`id` anchor, title/thumbnail/duration fields) plus the
  embed's `hd_src`/`sd_src`, so the embed and main's page shapes share one reader. DRM = main's
  `drmAssessment` as paths (a flag only when `true`, a licence map only when non-empty,
  `drm_info` read inside its JSON text) + the embed's `videoLicenseUriMap`/
  `graphApiVideoLicenseUri` → `DRM_PROTECTED`, 0 capture. No access phrases: the embed is an
  anonymous view, so a login/unavailable/region page goes to the user's own playback (main has
  already given its signed-in verdict before Master runs). Share links: never asked (the code
  needs a redirect). Reader additions: loader-call payloads (`callMarkers`) also reach the shape
  fallback; on a page, the shape fallback keeps only rows anchored to the video's ID; title,
  duration (ms or s) and poster as path lists.
- ✅ Facebook tests: `FacebookContractTest` (6) — the embed shape (2 rows, Safari, no cookie,
  Referer, key `facebook:{id}`, 0 capture); parity with main's `FacebookExtractor` on
  `watch_progressive` (the suggested video never), `reel_legacy_fields`, `reel_hd_sd_only`,
  `reel_inline_dash`, `public_reel_empty_licences` (not DRM); `drm_video` and a licensed embed →
  `DRM_PROTECTED`, 0 capture, 0 probes; login/unavailable/region/no media/malformed/insecure →
  capture, 0 probes; renamed embed fields found by shape, main's `changed_markup` (renamed
  delivery key) recovered by shape under its own ID, another video's embed → nothing; share link
  → no request. The saved live embed page (not committed) gave HD + SD via `CONTRACT`. Drift: 11
  new rows (83), 0 drift. Deviation: main's second Safari request for the AVC ladder is not
  repeated (one answer per video); the resolver still reads every file.
  Local: `:extractor-master:test` 266 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `facebook-page-video`, `facebook-reel`, `facebook-fb-watch`,
  `facebook-private`, `facebook-share-link` on a phone.
- ✅ CI (Facebook, `e28f311`): Master opt-in debug APK run `38087871484` — success.
- ✅ **TikTok**: the embed player page `www.tiktok.com/embed/v2/{id}` (live check 2026-10-10:
  `__FRONTITY_CONNECT_STATE__` → `videoData.itemInfos`, `video.urls` + `videoMeta`; its file
  opened with a range request without any cookie or Referer), asked with the tab's own agent
  (main's rule: never YFT's), main's navigation headers + Referer, **no cookie**, main's 3 MiB
  page cap, host lock. Node = the object whose `itemInfos.id` or `id` is the post's ID. Key
  table = the embed's `video.urls` (one file's mirrors → one row; size/duration from
  `videoMeta`) plus main's page shapes: `bitrateInfo` (one row per entry, its first https
  `PlayAddr.UrlList` address; `PlayAddr.Width/Height`, `Bitrate`, `DataSize`, codec from
  `CodecType`/`UrlKey` → `avc1`/`hvc1`, best bitrate first), the play address, and the
  watermarked download address only when nothing else is listed, titled "… — With TikTok
  watermark" (main's label). Caption, cover and duration fields as main's. The music's address
  (`musicInfos.playUrl`) is never a row. Stops: `isDrm` → `DRM_PROTECTED`; a photo post
  (`imagePostInfo`/`imagePost`) → `NO_MEDIA_FOUND`, 0 capture (main's final answer). Anything
  else the anonymous embed says (error page, TikTok status codes) goes to the user's own
  playback. Reader additions: dotted ID/address/field paths, a list = one file's mirrors (first
  absolute https address; an empty field is no file), per-entry field names
  (`ContractFields`) + `metaPath`, row labels, `noVideoPaths`, list indexes in paths
  (`covers.0`); the shape fallback's access verdicts count only for an answer asked with the
  user's cookie (Vimeo); DRM always stops.
- ✅ TikTok tests: `TikTokContractTest` (7) — the embed shape (1 row 720×1280, 26 s, caption,
  cover, key `tiktok:{id}`, the tab's agent, no cookie, Referer, music never a row, 0 capture);
  parity with main's `TikTokExtractor` on `universal_video` (best first: 1080p, bitrate, size,
  `avc1`), `sigi_video`, `universal_reflow_video`, `live_desktop_video_detail`, the watermarked
  file never beside others; only the download address → main's row and "With TikTok watermark"
  title; `drm_video` + a DRM embed → `DRM_PROTECTED`, `universal_photo` + a photo embed on a
  `/photo/` page → `NO_MEDIA_FOUND`, 0 capture, 0 probes; private/login/region/malformed/
  insecure/home page/the embed's error page → capture, 0 probes; renamed `urls` found by shape
  under the post's ID, another post's embed and main's `changed_markup` (no ID) → nothing; short
  link → no request. The saved live embed page (not committed) gave its 720×1280 file via
  `CONTRACT`. Drift: 11 new rows (94), 0 drift. Deviations: one answer, no desktop-page second
  request and no file checks of main's (Master's own probes check every row); the embed gives
  one quality (main's page may list more); main's page-answer cookies (`tt_chain_token`) are
  not replayed, as the embed's files need none.
  Local: `:extractor-master:test` 273 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `tiktok-nasa`, `tiktok-short-link`, `tiktok-photo`, `tiktok-private`
  on a phone.
- ✅ CI (TikTok, `29eb119`): Master opt-in debug APK run `38089590941` — success.
- ✅ **Instagram**: the captioned embed page `www.instagram.com/p/{code}/embed/captioned/` (live
  check 2026-10-10, reel: `contextJSON` → `shortcode_media` with `video_url`, `dimensions`,
  caption and owner; its file opened without any cookie), asked with the tab's own agent, main's
  navigation headers + Referer `https://www.instagram.com/`, **no cookie** (main's `pageHeaders`
  without its cookie), main's 6 MiB page cap, host lock. Embedded JSON documents are read before
  the page. Node = the object whose `shortcode` or `code` is the post's code. Items = main's
  `postOf`: `carousel_media` or the sidecar's `edge_sidecar_to_children.edges.*.node`, else the
  post itself; a carousel link's `img_index` picks its item (main's `IMAGE_INDEX`), else the first
  video item. Key table = main's `itemOf`: `video_versions.*`, `video_url` with `dimensions`,
  both inline DASH manifests; duration (`video_duration`, s), thumbnail
  (`image_versions2.candidates.0.url`, `display_url`). Title = the caption's first line (main's
  `displayTitle`), else "{full_name} on Instagram", else "@{username} on Instagram". Stops: a
  photo post (`is_video` false / `media_type` 1 on every item, as the answer states it, never
  from a missing field) → `NO_MEDIA_FOUND`, 0 capture (main's final answer). A login or
  checkpoint page (`/accounts/login`, `/challenge`, main's `isLoginWall`) answers nothing, so it
  and any other anonymous wording go to the user's own playback. Reader additions: item lists
  as paths with `*` (X's `mediaDetails` now `[mediaDetails, *]`), the node itself when it has no
  list, `photoMarkers`, owner-name titles (`nameTitles`), embedded documents first;
  `SiteContracts.onWall()`.
- ✅ Instagram tests: `InstagramContractTest` (6) — the embed shape (1 row 640×1136, 15.2 s,
  caption, thumbnail, key `instagram:{code}`, the tab's agent, no cookie, Referer, 0 capture);
  parity with main's `InstagramExtractor` on `embed_captioned`, `page_signed_in`,
  `graphql_reel`, `api_info_reel`, `graphql_carousel` (`img_index` 3 and 1), another post's file
  never; a carousel link picks its item, else the first video ("Fixture Creator on Instagram");
  `graphql_photo`, a photo carousel item, an API photo and a photo embed → `NO_MEDIA_FOUND`,
  0 capture, 0 probes; no video/login/null/fileless/a redirect to the login page/another post's
  embed → capture, 0 probes; a renamed `video_url` found by shape. The saved live embed page
  (not committed, reel) gave its 720×1280, 62 s file via `CONTRACT`. Drift: 13 new rows (107), 0 drift. Deviations:
  one answer per post (no signed-in page or app API request; capture decides access, not the
  anonymous embed); a login page is read as no answer; the embed gives one quality; the `efg`
  bitrate inside a file address is not read (Master's probe gives the size).
  Local: `:extractor-master:test` 279 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `instagram-reel`, `instagram-carousel`, `instagram-private` on a
  phone.
- ✅ **R8 done (offline)**: Vimeo, X, Facebook, TikTok and Instagram answer through `CONTRACT`
  first; flag off = main unchanged. Live parity and phone checks stay owner-run
  (`ONLY=<ids> bash scripts/canary.sh`).
- ✅ CI (Instagram + R8 close, `38840b5`): Master opt-in debug APK run `38093084650` — success.

## R7 — Generic web: capture + MSE/EME metadata hooks

- ✅ `assets/yft-master-mse.js` (document start via `addDocumentStartJavaScript`, top frame only,
  off on YouTube hosts; nothing on WebViews without the feature): records each SourceBuffer's
  type (`addSourceBuffer`), the picture size of its init segment (ISO-BMFF `tkhd`, WebM
  `PixelWidth`/`PixelHeight`), and which delivered addresses fed it (the ArrayBuffer the page
  read from XHR `response` or fetch `arrayBuffer()`, matched by identity; bytes are never
  copied or sent). Every original gets its own arguments and returns its own result;
  `dispose` restores them.
- ✅ EME: a key attached to a video (`setMediaKeys`) or a licence request (`generateRequest`)
  marks the page protected → `DRM_PROTECTED`, before any media check. A bare
  `requestMediaKeySystemAccess` is only counted: many players ask it at start-up for clear
  video, so it alone is not protection (R2's `mediaKeys`/`encrypted` checks stay).
- ✅ `yft-master-capture.js` adds the focused video's fed addresses to each sample (`fed`, its
  buffer type, init size); `CaptureFrame` → `MasterBrowserSession` → `CapturedRequest(fedPlayer,
  width, height)`. L1 (`CaptureLayer`) treats a fed address like the playing address (MAIN, the
  requested content ID) and keeps the stated codec and size; one player's fed tracks and
  qualities share one page-video key, so they are one focused video, never rival videos. R4
  grouping, ad/preview rules and the probe budget apply unchanged.
- ✅ L1 capture stays the default answer for sites with no adapter (the user's Download tap →
  one capture); no background capture was added.
- ✅ Tests: `mse.test.cjs` (6: buffer types + MP4 init size + XHR/fetch feeding, WebM init size,
  originals untouched/plain-HTTP ignored, EME query vs keys/licence, capture sample carries fed
  rows and stops on a licence, YouTube off + dispose), `CaptureLayerMseTest` (2),
  `MediaSourceCaptureTest` (3: frame parsing, fed address → MAIN with codec/size through the
  engine, licence → `DRM_PROTECTED` with 0 probes). Spike CI runs all three JS files. Live
  parity on the generic URLs (plain `<video>`, HLS, DASH, VAST pre-roll) is owner-run.
  Local: `:extractor-master:test` 244 (0 failures, 2 skipped), Android 56, app 24, JS 28,
  `:app:assembleDebug` OK.
- ✅ Spike CI run 38081550529 green on `e163ec5` (drift, JS 28 incl. `mse.test.cjs`, Master
  tests, opt-in debug APK).

## R6 — Own YouTube module (YT-1 / YT-2 / YT-4)

- ✅ YT-1: main's five YouTube files copied whole into `extractor-master/.../modules/youtube/`
  (provenance header; only the package differs) with their five tests and test support
  (`Fixtures` reads main's fixtures via `yft.siteFixtures`). Whole-file rows (`*`) in
  `toolkit-provenance.tsv`: the drift check hashes each file after its package line on main
  and also fails when the Master copy itself was edited. 45 rows, 0 drift vs `34a41890` and
  `origin/main`.
- ✅ `modules/MasterSiteModule` + `SiteExtractorModule` (row check: HTTPS media and companion,
  no `sabr` parameter, not protected; rows tagged `site:contentId` like the app coordinator).
  `MasterYouTubeModule`: the copy with **no** player-script runner and **no** PoToken provider
  (n/sig streams are never offered by Master; main, flag off, still covers them), asked
  without the user's cookie/authorization header.
- ✅ YT-2: `MasterYouTubeClients` (table version 1, source "main 34a41890 / yt-dlp
  2026.08.19", visionOS first, no client needs a script or token). Canary:
  `MasterYouTubeCanaryTest` (skipped unless `-Pyft.youtubeCanary=<id>`) +
  `bash scripts/master-youtube-canary.sh <id>` (owner-run, never CI): passes while visionOS alone
  gives a complete answer ("visionOS first: complete, no watch page").
- ✅ YT-4: no SABR code; SABR-only and cipher fixtures give 0 rows.
- ✅ Engine slot: `MasterFallbackEngine(modules = …)`, stage `SITE_MODULE`. A claimed page never
  reaches layers, capture or probes; the module asks only when no site adapter answered
  (`primaryFailure == UNSUPPORTED_URL`), otherwise `Skipped(primaryFailure)` (P12, one lookup
  per video). Terminal rules and the off switch come first; a different requested video →
  `RESPONSE_CHANGED`. App: `AndroidBrowserMasterFallback.create` passes
  `MasterYouTubeModule(OkHttpExtractorClient(client))`; with main's adapter present YouTube
  watch pages are therefore never captured by Master.
- ✅ Tests: copied YouTube tests (87), `MasterYouTubeParityTest` (6: main vs Master on 20
  fixture scenarios — same requests, posts, headers, rows, order, labels, sizes and details;
  cipher/SABR → 0 rows; bot check → `BOT_CHECK` with 0 capture/probe; engine answers through
  the module only; table order), `MasterSiteModuleTest` (7). `extractor-sites` is a
  test-only dependency for the parity comparison. `:extractor-master:test` 242 tests,
  0 failures (2 skipped: canary, live smoke).
- ✅ Spike CI run 38079213716 green on `46ef8ec` (the first run, 38078858636, stopped 15 s into
  the Gradle step before any task ran; job logs need admin rights, so the workflow now
  publishes Gradle failure lines as an annotation).

## R5 — E: codec steering

- ✅ `assets/yft-master-codecs.js` (document start, `addDocumentStartJavaScript`, opt-in builds
  only; nothing on WebViews without the feature): `MediaSource.isTypeSupported` (also
  `ManagedMediaSource`/`WebKitMediaSource`) and `mediaCapabilities.decodingInfo` answer only
  what `DeviceMergeSupport` merges — AVC/AAC always, VP9/Opus/Vorbis WebM from Android 10, AV1
  only while AV1 merges are on (off), HEVC/Dolby/E-AC-3 never, VP8 never. It only narrows (the
  browser is still asked), never touches DRM (`keySystemConfiguration`) queries, is off on
  YouTube hosts and can be disposed; unknown codecs pass through.
- ✅ `CodecSteering` (policy → script) and `WebViewPlaybackCapture(codecs = …)`; the app hook
  builds it with `AndroidBrowserMasterFallback.codecSteering(sdk)` from
  `AudioVideoMuxCompatibility` (same rules as `DeviceMergeSupport`). Main's Facebook Safari AVC
  agent stays the site-specific case (untouched).
- ✅ Tests: `codecs.test.cjs` (5: AVC/AAC on, AV1/HEVC/E-AC-3 off, VP9/Opus by Android
  version, never widens, decodingInfo + DRM pass-through, YouTube off/idempotent/dispose),
  `CodecSteeringTest` (3), app `BrowserMasterFallbackTest.codecSteeringFollowsTheDeviceMergeRules`.
  Spike CI runs both JS files. Live parity (AVC ladder where offered; 1440p/2160p kept with
  VP9) is owner-run on a phone.

## R4 — C: keyframe / duration fingerprint

- ✅ `verify/SegmentIndexReader`: DASH/ISO-BMFF `sidx` (v0/v1, media references only) and ended
  HLS `#EXTINF` playlists → length + keyframe cues (segment starts); HLS SAMPLE-AES/FairPlay
  keys → protected. `CapturedMp4Facts` uses it (same duration rule as before) and keeps cues.
- ✅ `verify/MediaFingerprint` (length + cues): `keyframesAgree` (≥ 90 % within 120 ms → yes,
  < 50 % → no, too few cues → cannot tell), `sameVideo` (agreeing cues and length within
  250 ms; 40 ms when both are a fixed grid), `otherVideo`. Length alone never groups.
- ✅ `CapturedMediaMetadata.inspect` returns the fingerprint from the same bounded reads (MP4
  prefix `sidx`; HLS playlist, then a master's first playlist only on the same origin). A
  protected HLS playlist sets `drmHint` → `DRM_PROTECTED`. `enrich` keeps its contract.
- ✅ `verify/FingerprintGroups` + `MasterMainSelection`: one video's qualities form main's group
  (one `MediaGroup` with several files, not extra "More" videos); a lone same-length file whose
  cues disagree with a main confirmed by two agreeing files is dropped (ad/related/preview);
  a second agreeing ladder or a file without cues stays its own group (before-R4 behaviour).
  No row is created or changed; ranking is unchanged inside each group.
- ✅ Tests: `SegmentIndexReaderTest` (7), `MediaFingerprintTest` (8, HLS fixtures under
  `src/test/resources/fingerprint/`), `CapturedMediaInspectTest` (5),
  `MasterFingerprintSelectionTest` (5). `:extractor-master:test` 141 tests, 0 failures.

## R3 — Toolkit + L3 shape search (content-ID anchoring)

- ✅ `toolkit/` copies (provenance header in each file, base main `34a41890`): `PageScripts`
  (T1: scripts by their own `id`/`type`, `var x = {…}`, JSON inside strings, `contextJSON`,
  `__additionalDataLoaded`), `BalancedJson` (T2: string-aware object/array reads, size cap,
  HTML-entity retry), `AnchoredMediaWalk` (T3: bounded walk carrying the nearest content ID),
  `MediaKeyTable` (T4 data: address/version/MIME/ID/hint keys, DRM statement, skip subtrees),
  `QualityLadder` (T7), `ProbeRounds` (T8), `RequestPolicy` (T9), `UrlPolicy`
  `PLAUSIBLE_EXPIRY_SECONDS` (T10). T7–T9 are ready for the per-site steps (R8); no
  behaviour uses them yet.
- ✅ `layers/ShapeLayer` (L3) runs last and only adds: a row an earlier layer found is dropped,
  earlier rows reach the normalizer unchanged, and it is skipped once a layer gave a terminal
  verdict. Never on a YouTube host, never inside `streamingData`/ad subtrees. A row is `MAIN`
  only when anchored to the page's content ID, `PREVIEW` for preview keys, otherwise no role
  (so a related video cannot become main). DRM statements (`drm`, licence objects, Widevine/
  FairPlay/PlayReady, FB `video_license_uri_map`) end in `DRM_PROTECTED`; a document's past
  `expires` drops its files at the gate.
- ✅ Drift check: `extractor-master/toolkit-provenance.tsv` (40 main symbols + SHA-256 at the
  base) and `scripts/master-toolkit-drift.py`; spike CI self-checks the base (must be 0) and
  reports changes on `origin/main` as warnings. Today: 0 drifted.
- ✅ Tests: `ShapeLayerTest` (shape ⊇ fixed-key on every committed fixture, renamed keys keep
  hits, related video rejected by anchoring, finders, DRM/expiry, all negative fixtures offer
  0, L3 only adds, YouTube/`streamingData` off), `ToolkitFindersTest`, `QualityLadderTest`,
  `ProbeRoundsTest`, `RequestPolicyTest`; `:extractor-master:test` 121 tests, 0 failures.
  Fixtures are read in place from `extractor-sites/src/test/resources/fixtures` (no copy, so
  no fixture drift) plus 3 new ones under `extractor-master/src/test/resources/shape/`.
- ✅ Fixture baseline rewritten on purpose (rows only grow): Facebook `changed_markup` 1→2,
  `watch_progressive` 3→6, `expired_links` 0→2 (expired, offered 0); Instagram `api_info_reel`
  2→7, `graphql_reel` 1→6, `page_signed_in` 3→8, `embed_captioned` 0→1 (heights gain 1920);
  Vimeo `player_config` 0→6, `player_page_inline` 0→2, `config_expired` 0→1 (expired),
  `config_drm` now ends `DRM_PROTECTED` (L3 reads `files.drm`; safety gain).

## R2 — Safety gaps (terminal walls, DRM stop, YouTube payload off)

- ✅ `policy/TerminalRules`: `BOT_CHECK`, `LOGIN_REQUIRED`, `PLAYER_SCRIPT_REQUIRED` are final on
  `youtube.com`, `youtu.be`, `youtube-nocookie.com`, `reddit.com`, `redd.it` and their
  subdomains (`blocksFallback(failure, pageUrl)`; host read as a browser reads it, so
  `youtube.com@evil.test` or `evil.test\@youtube.com` do not match). The engine checks
  `request.pageUrl`; the app hook checks the lookup link before any capture request and the
  tab page after it. Elsewhere the same failures still need the user's own playback.
- ✅ YouTube `streamingData` gives no Master rows (standard stack; never on a YouTube host even
  when opted in) until the R6 module. Its DRM statement still stops Master everywhere, with
  main's signals: `drmParams`, `playbackTracking.drmSessionId`, per-format `drmFamilies` /
  `drmTrackType` (main's `youtube/player_drm.json` now ends `DRM_PROTECTED`).
- ✅ EME stop: `yft-master-capture.js` marks the page protected when any visible video has
  media keys or an `encrypted` event (before: only the selected video). The session keeps it
  until navigation; the engine returns `DRM_PROTECTED` before any media check.
- ✅ Tests: `policy/SafetyGapsTest` (walled bot check → 0 capture, 0 probe; YouTube payload →
  0 rows, 0 probe; EME and stated DRM → `DRM_PROTECTED`, 0 probe), `TerminalRulesTest` host
  cases, `LayerStackTest` R2 cases, app `BrowserMasterFallbackTest.walledSites…` (0 request
  factory, 0 capture, 0 probe), 3 new JS capture tests. `MasterHardeningTest`'s companion
  budget fixture moved from YouTube JSON to an inline MPD (same assertions).
- ✅ Spike CI now also runs `node --test …/capture.test.cjs`.
- ✅ CI on `a2862abd`: spike `Master opt-in debug APK` run 38068967732 — success (capture JS
  tests, Master tests, opt-in APK; artifact `yft-master-optin-debug-apk`, 18.3 MB, 14 days).
  Flag-off `Work-branch checkpoint validation` run 38068970580 (PR #1) — success (artifact
  `yft-debug-apk`). Older runs on `314c97a`/`48915f3` are stale.

## R1 — Base sync + parity harness + canary

- ✅ Merged `origin/main` `34a41890` (61 commits since `a9eea7ba`). Conflicts only in
  `BrowserScreen.kt`, `BrowserViewModel.kt`, `docs/SESSION_STATE.md`: main's code kept, the
  Master hook re-applied around it. Main's P45 ("the tapped video only") removed the sheet's
  "Other videos on this page" row, so Master's other videos now stay in the browser's found
  list; `MasterMainMoreSheetTest` asserts exactly that (and flag-off = legacy).
- ✅ Frozen list: `app/src/androidTest/assets/parity/parity-urls.json` (32 cases from
  MASTER_KEY_PLAN §4.3; 14 public links; owner-picked slots stay `null`, never committed).
- ✅ Pure JVM harness `extractor-master/.../parity/`: `ParityUrls` (rejects http, user info,
  fragments, signed/session queries), `ParityReport` (host + content ID only; rows keep shape;
  titles hashed; fails closed on any `://`), `ParityVerdict` (§4.2 checks). Unit-tested,
  including a leak test with signed links, cookies and URL-bearing titles.
- ✅ Offline baseline: `FixtureBaselineTest` runs Master's stack over main's 84 site fixtures
  (`extractor-sites/src/test/resources/fixtures`, passed by Gradle) against
  `parity/fixture-baseline.json`: rows and heights may only grow, terminal verdicts must stay.
  A main sync that adds or removes fixtures fails until `-Pyft.parityBaseline=write`.
- ✅ Live: opt-in `MasterParityLiveTest` (`-e yft.parity 1`; arm A = main's adapters as the app
  wires them, arm B = Master in a visible WebView with real one-byte checks; attended mode
  waits for the owner's Play). `scripts/canary.sh` installs, runs and pulls the report. Not
  in CI. Spike CI now compiles `:app:compileDebugAndroidTestKotlin`.
- ✅ Local Gradle: `extractor-master` 101 tests (1 opt-in skip), Android module 45, app Master
  tests 23 (sheet 3, flow 9, fallback 11), androidTest compiles, 0 failures.
- ✅ The merge was made on GitHub through draft PR #1 (spike → main, "[DO NOT MERGE]"), used
  only for that merge: `314c97a` pre-merge → `48915f3` merge of main `34a4189` → `a2862ab` hook
  re-applied. PR #1 was closed unmerged after CI; main is untouched.
- ✅ CI on `a2862abd`: spike run 38068967732 and flag-off run 38068970580 both success (see R2).

## A0 — Architecture step A (structure only, same behavior)

- ✅ Map: [MASTER_KEY_ARCHITECTURE.md](MASTER_KEY_ARCHITECTURE.md). `extractor-master` now has
  `policy/`, `layers/` (L1 `CaptureLayer`, L2 `ContractLayer`, L4 `RecipeLayer`, L3 slot),
  `recipes/` (data-only key tables), `toolkit/`, `verify/`, `present/`, `capture/`; the engine
  is orchestration only.
- ✅ One stop-rule source: `policy/TerminalRules`. The app hook's own `NEVER_CAPTURE` copy is
  gone; `BrowserMasterFallback` calls `TerminalRules.blocksFallback`. Same sets as before.
- ✅ Android-free files moved to the JVM module: `CapturedMediaMetadata`, `CapturedMp4Facts`
  (`verify/`), `MasterMainPresentation` (`present/`). Public API names unchanged; only imports
  moved.
- ✅ Local JVM run: `extractor-master` 88 tests and 39 non-WebView Android-module tests, 0
  failures. Old-vs-new differential runs: reader 5,372 cases and engine 10,368 cases, 0
  differences.
- ✅ CI `master-optin-debug-apk` run `38064367003` on `fcceaec4` (final commit of the step):
  success — Master, Android-module and app Master tests plus `:app:assembleDebug`.
  Run: https://github.com/Alalkipgen/YFT/actions/runs/38064367003
  Artifact `yft-master-optin-debug-apk` (17,990,603 bytes, expires 2026-10-24):
  https://github.com/Alalkipgen/YFT/actions/runs/38064367003/artifacts/11674876484
  Pushed as `ef63a60a` (new layout; old paths as one-line placeholders) followed by 11
  placeholder deletions (the GitHub tool cannot delete in the same commit); the 11
  intermediate CI runs were cancelled by the workflow's concurrency group.
- No policy, site, Generic, model or download behavior changed. No main merge, release or tag.

## M2 — main/More sheet, expected TikTok More, opt-in CI APK

- ✅ TikTok More = 0 recorded as expected (single-video post page); no more alternative probes.
- ✅ `app/src/test/.../feature/browser/MasterMainMoreSheetTest.kt` (Robolectric, Compose, JVM).
  It drives the real `BrowserViewModel` hook path on a TikTok-shaped site page, the real
  `QuickDownloadViewModel`/`QuickDownloadRoute` sheet and the real `BrowserScreen` found list:
  - main + 7 More: the sheet shows only the main video; the existing More button
    "Other videos on this page (7)" closes the sheet and opens the found list with all seven.
  - main + 0 More: the main video and Download show; no More button exists.
  - flag off: the real factory with `false` is `BrowserMasterFallback.None`; selection, count and
    the full sheet state equal the pre-Master default (only the wall-clock resolve time is
    masked); no More button; a failing adapter gives the same lookup failure with no capture.
  - The main/More presentation is a fixture with the module's shape (public constructor, one
    group per verified file, owned UI keys, no post ID). Selection policy remains covered by the
    module tests. No production, model, Generic, extractor or download file changed.
- ✅ Local run of the exact CI test set with `-Pyft.masterCapture=true`: 18 suites, 153 tests,
  0 failures (`MasterMainMoreSheetTest` 3/3, `BrowserMasterFlowTest` 9, `BrowserMasterFallbackTest`
  10, `extractor-master-android` 54, `extractor-master` 77).
- CI: new spike-only workflow `.github/workflows/master-optin-debug-apk.yml` (existing workflows
  untouched) runs the Master tests and builds `:app:assembleDebug` with
  `-Pyft.masterCapture=true`, uploading artifact `yft-master-optin-debug-apk`.
  ✅ Run `37997610268` on `4ceab089`: success (tests and build steps all green).
  Run: https://github.com/Alalkipgen/YFT/actions/runs/37997610268
  Artifact `yft-master-optin-debug-apk` (zip, 17,969,814 bytes, expires 2026-10-23):
  https://github.com/Alalkipgen/YFT/actions/runs/37997610268/artifacts/11648655411
  APK SHA-256 is in the run summary. Debug key is the runner's throwaway key: uninstall an
  older `com.alal.yft.debug` signed by another key before installing.
- ⏳ Owner phone check of that APK is pending. No main merge, release or tag.

## Hooked selection and native live checkpoint

Dedicated, independently revertible hook/wiring commit:
`2b76e4d75136ff4a3e27f590e1e140a3570c9e6a`.
It contains exactly three production hook files plus two wiring/flag-off test files;
the selection/metadata/presentation algorithm remains entirely in the Android module.

| Hook file | Added | Removed |
| --- | ---: | ---: |
| `MasterFallbackEngine.kt` | 12 | 0 |
| `BrowserMasterFallback.kt` | 10 | 1 |
| `BrowserViewModel.kt` | 19 | 7 |

No existing identifiers were renamed or unrelated blocks reformatted. The ViewModel edits
are confined to existing selection call sites and opt-in presentation routing. App models,
Generic files, existing site extractors, download code and CI workflows are unchanged.

### Fresh checks

- ✅ Original seven selection contracts and five safety guards: all 12 pass.
- ✅ Combined policy/parser/transport-frame/opt-in/metadata unit suite: `OK (39 tests)`.
- ✅ Disabled policy skips hook/capture/probe for every primary failure enum. A disabled
  selector equals the legacy engine; a null hook retains legacy refusal and exact-address success.
- ✅ Real flag-off app factory returns the original `None`; every primary object passes through
  unchanged for both generic modes. `OK (1 test)`, isolated caller unit test with fail-fast
  dependency doubles, not an Android UI/device validation.
- ✅ JavaScript transport: 14/14; repository scripts: 30/30.
- ✅ Fragment-index/movie-extends duration and passive-result UI isolation were first run red,
  then fixed using actual container bytes and owned presentation keys respectively.
- ✅ TikTok focused DOM state at index 8 was actually omitted by the eight-script limit.
  The new regression failed first, then passed after prioritizing the two known delivered
  DOM-state nodes within the unchanged eight-node/eight-payload budget. No endpoint, URL,
  ID or playback evidence is invented. Projected bodies remain at most 64 KiB; refusal/DRM
  fields and all response-clone limits are preserved.
- ✅ Two audio-track/presentation tests failed first, then passed. Actual `hdlr=soun` with
  no video handler corrects a misleading video MIME; audio-only entries cannot become main
  or More. Missing decoded dimensions never reject a verified video; mixed tracks stay video.
- ✅ A slow-alternative test failed first, then passed. Android-owned validation has a 15-second
  sub-window inside the unchanged 20-second engine timeout, preserving an already verified main
  and eligible alternatives if a later check stalls. Caller cancellation, DRM refusal, probe
  budget and navigation-generation checks are not bypassed. Capture remains 2.5 seconds.

### Latest completed native diagnostics

These use actual desktop native Pause/Play, trusted click receipts, fresh production collector
frames, the hooked production selection policy, normal HTTPS validation and the unchanged
main/More presentation models. They do not prove Android APK sheet rendering.

- ✅ Instagram `Dcwk7e1yHaY`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  More list count 7. Native trusted click 1, capture 361.2 ms, authorized playback true.
  DOM duration 61,966 ms; independently read selected media duration 61,966 ms;
  selected dimensions 1080x1920. Nine validated inputs; one independently identified audio-only
  input excluded; eight video outputs. TLSv1.3; 2,363,904 bounded response bytes.
  An earlier retry exceeded the engine timeout; it is retained as a failure, not a pass.
- ✅ TikTok `7670337149526379789`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  no NEEDS_SELECTION. Latest repeat: native trusted click 1, capture 358.7 ms,
  authorized playback true. DOM duration 26,166 ms; independently read selected media
  duration 25,169 ms (within the exact 2-second tolerance); selected dimensions 720x1280.
  TLSv1.3; 461,139 bounded response bytes. The repeat uses actual browser-owned cookies
  scoped to real captured addresses, RAM/stdin only, matching existing Android CookieManager
  wiring. No cookie value, signed address or raw response is persisted.
- ✅ TikTok More count 0 is **expected** (owner decision): the public post page has one video.
  The accessible unrelated 2,067 ms file is correctly rejected; the four alternative probes that
  reported HTTP_STATUS are not pursued further and statuses were never overridden. No further
  TikTok alternative probing. One earlier retry blocked by a translation tip is not evidence.
- ✅ Fresh two-run Android gate for this changed tree passed (see below).
- ✅ Main/More sheet rendering is proven by a JVM Compose UI test (see M2 below). Live emulator
  sheet checks are intentionally skipped (no KVM); the owner checks the CI APK on a phone.

M2 status: see the M2 section below. Owner phone validation of the CI APK is pending.
No main merge, release/tag, full download/mux, all-resolution,
Master-CI-green or merge-readiness claim. Only the spike branch is used.

### Android environment recovery

- ✅ Official-index size/SHA-1 checks verified six SDK archives before extraction.
  JDK 17 is retained; API 35/build-tools 35.0.0, platform-tools 37.0.1, emulator exactly
  37.2.12.0 and API29/default/x86_64 image revision 8 are restored. Runtime libraries restored.
- ✅ Emulator package was registered through the official stable-channel SDK manager after
  manual extraction alone failed AVD-manager preflight. Strict yft-master29 AVD recreated:
  480x854, density 240, RAM 1536 MiB, 2 cores. Recovered focus guard/runner is not weakened.
- ✅ The sandbox was later wiped; the spike branch was re-cloned at `def91b91` (production code
  identical to `005fd0f6`), and the same verified SDK packages, Corretto JDK 17 (SHA-256 checked),
  emulator 37.2.12 and strict AVD were restored again.
- ✅ Fresh internal app/androidTest pair built from `def91b91` with `-Pyft.masterCapture=true`
  and `-Pandroid.injected.build.abi=x86_64`: BUILD SUCCESSFUL.
  App SHA-256 `fee5c66f7cedc231f8c69c28ba13a0ecc713e2b89f748d50d2a712b762630e90`;
  test SHA-256 `9c6d8f88f178fbc0efa10640f4a932c21d93d4d04d18c233258a8141b5c106aa`;
  both signed by the same debug certificate
  `ef6a9e9817aba79e5416d41ede1e183e2d3d1ace6c6c094b333f43ddc72b9609`.
- ✅ Emulator cold boot completed (boot_completed=1). A SystemUI ANR dialog was cleared with its
  native Wait button only; focus then Launcher, device-side screenshot 192,040 bytes.
- ✅ Strict gate `gate-def91b91`: two consecutive full-class
  `MasterCaptureInstrumentedTest` runs, each `OK (4 tests)` (172 s and 140 s), identical
  head/APK/test/certificate/harness hashes, installed hashes verified. Focus guard not weakened.
- ❌ Android main-sheet/More rendering is not covered by this gate and remains unproved.
  No owner phone delivery, release, tag or main merge.

### Secret-safe latest native log excerpts

```json
{"site":"tiktok","result":"SUCCESS","stage":"PLAYBACK_CAPTURE","nativeTrustedClicks":1,"captureMs":358.7,"authorizedPlayback":true,"mainSelected":true,"moreCount":0,"domDurationMillis":26166,"selectedDurationMillis":25169,"androidAppLiveProved":false}
{"site":"instagram","result":"SUCCESS","stage":"PLAYBACK_CAPTURE","nativeTrustedClicks":1,"captureMs":361.2,"authorizedPlayback":true,"mainSelected":true,"moreCount":7,"domDurationMillis":61966,"selectedDurationMillis":61966,"androidAppLiveProved":false}
```

## Authorized Android-owned policy checkpoint

The user authorized minimal hook/wiring edits in the three files listed below, in a separate
revertible commit. The selection algorithm, Generic ad-rule reuse, bounded independent MP4
metadata reads and main/More presentation mapping are implemented only in
`extractor-master-android`. Module policy/parser/frame tests: `OK (13 tests)`; JS: 11/11.
The original seven red contracts remain red until the separate hook commit is applied.

The projection keeps original source identities in the memory-only capture record. Only UI
copies use reserved presentation keys and no claimed post ID, so alternatives are not falsely
declared qualities of one post. The existing MediaCandidate/MediaGroup/otherVideos app models
are unchanged. Duration is read from delivered metadata or independently validated MP4 bytes,
never copied from the DOM. Default/release flags and all original security guards are unchanged.
Live main/More and full Android UI validation are still pending.

## Automatic main/More selection — test-first checkpoint

Date: 2026-10-09. Baseline: `ac9695ce6758fbcace6ea1381f185a9e6b5a8278`.
Branch: `spike/master-extractor-backup` only. No merge, release, tag or phone APK delivery.

### Status

- ✅ A compile-valid failing contract was written and executed before any production fix.
- ✅ 12 offline JVM tests ran: 7 expected selection failures, 5 safety tests passed.
- ✅ All seven failures report `NeedsSelection`, not compilation or fixture setup errors.
- ❌ Automatic TikTok/Instagram main selection is not implemented or live-verified.
- ❌ Main sheet plus More integration is not complete.
- ⏸ Production changes await clarification of the module-only boundary described below.

The only new code is
`extractor-master-android/src/test/kotlin/com/alal/yft/extractor/master/android/MasterMainSelectionContractTest.kt`.
This progress document is the explicitly requested documentation exception.
No unused production selector, fake HTTPS playing URL, fabricated MAIN role or guessed
content identity was added.

### Red contract

The test exercises the existing bounded frame reader, browser session and Master engine.
Independent validator fixtures supply candidate duration/dimensions; the DOM duration is
never copied onto candidates. Instagram uses the generic/no-ID request shape. TikTok keeps
its requested content ID instead of deleting it to force a result.

The seven deliberately failing behaviors are:

1. Known DOM duration: independent candidate duration must be within the inclusive 2-second
   tolerance; a candidate 2.001 seconds away must not qualify.
2. TikTok: a different-duration file must not receive the player's duration.
3. A missing candidate duration must not be treated as a known-duration match.
4. Closest decoded dimensions rank matching candidates; smaller matching files remain for More.
5. Equal candidates choose a stable main and retain the other eligible candidate for More.
6. Unknown DOM duration chooses the longest non-ad candidate and retains alternatives.
7. Ad-domain and far-shorter-duration candidates must not become main or More.

Passing guard cases: paused playback, only one progress sample, DRM evidence, stale navigation
and the disabled default policy. These are explicitly offline unit fixtures, not native
Android gesture evidence, live media validation, or a promise that the sheet already works.
No JS play/seek is used. No live-manifest or protected-media refusal is overridden.

Execution: JDK 17.0.20.1, Kotlin 2.0.21/JVM target 17, JUnit 4.13.2.
The standalone runner compiled 50 unchanged production source files and this one new test.
It did not run Gradle/Android instrumentation or the full application regression suite.
Equivalent module test command, once the Android toolchain is available:

```sh
./gradlew :extractor-master-android:testDebugUnitTest \
  --tests com.alal.yft.extractor.master.android.MasterMainSelectionContractTest
```

JUnit result: `Tests run: 12, Failures: 7`; exit 1 is intentional for this red checkpoint.
Do not describe this checkpoint as green CI or as the completed fix.

Fresh ancillary checks: JS transport 11/11 and repository script tests 30/30 passed.
Exact two-file allowlist, Kotlin lines at most 100 characters and the local equivalent
secret/sensitive-path scan passed. The 50 compiled production-source hashes and the capture
JS hash match the unchanged baseline. No GitHub Advanced Security scan or Android build is
claimed by these local checks.

### Confirmed production boundary

The requested full behavior cannot currently be wired through the Android capture module's
public contract alone:

- `CaptureFrame.kt` does not retain the player's duration or decoded dimensions yet. This
  part can be changed inside the allowed module.
- `WebViewPlaybackCapture` returns only `PageSnapshot`. That contract has no explicit selected
  main/More result or duration/dimension selection policy.
- `MasterFallbackEngine.kt` owns a private `select`. It runs before media validation, so
  independently probed candidate durations never reach selection when the player uses a blob
  URL. It accepts an exact HTTPS playing-address match or one explicit MAIN group; otherwise
  it produces `NeedsSelection`. There is no injected selection hook.
- The engine probes and returns only selected candidates; unselected alternatives are not
  passed through as More.
- `BrowserMasterFallback.kt` applies the caller's content-identity filter.
- `BrowserViewModel.kt`'s generic Master path requires `groups.singleOrNull()` and selects the
  group without an other-video count. Returning several legitimate video groups alone does
  not implement the requested main/More sheet.

Rewriting a blob address as a supposedly observed HTTPS playing URL, inventing page-owned
MAIN evidence or assigning different posts one fabricated identity would bypass these checks
instead of implementing the requested policy. Those workarounds were not used.

The smallest proposed scope clarification is permission for selection-hook/result-routing
edits in these three files, with the selection algorithm and new capture facts remaining in
`extractor-master-android`:

- `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/MasterFallbackEngine.kt`
- `app/src/main/java/com/alal/yft/detection/master/BrowserMasterFallback.kt`
- `app/src/main/java/com/alal/yft/feature/browser/BrowserViewModel.kt`

Existing reusable Generic-workflow rules were located: `BrowserObservationMapper.adRole`,
`MediaGroups.isPreviewAddress` and `PageVideoFacts.isFarShorter`. Reuse them without changing
their files. The requested duration-match tolerance remains exactly 2 seconds, rather than
the Generic long-video percentage tolerance.

### Live acceptance is still pending

No new live test is presented as a fix validation in this checkpoint. The latest previously
recorded native public-player diagnostics remain:

- ❌ Instagram: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.
- ❌ TikTok: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.

The prior two-run Android gate passed with the same old APK pair. It does not validate a
future changed implementation. Default/release opt-out, 2.5-second capture timeout, independent
playback-progress authorization, DRM rejection and navigation-generation checks are unchanged.
Existing site extractors, Generic files, download code and CI workflows are untouched.

Next, after the boundary is clarified: make the red contracts pass through real production
wiring, rerun relevant guards/device checks, then perform native TikTok and Instagram live
validation. Completion requires logs showing automatic main selection without
`NEEDS_SELECTION` and actual main/More routing. TLS/media-byte, full download/mux, all
resolutions, Master CI green and merge readiness remain unverified.