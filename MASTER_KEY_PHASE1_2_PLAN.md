# Master Key — Phase 1.2 Plan: next-level YouTube n/sig solver (behaviour first, YouTube-verified)

Status: **plan + prompt.** No code is changed by this document.
Date: 2026-10-11. Branch: `spike/master-extractor-backup` only.
Reads: `MASTER_KEY_PHASE1_1_PLAN.md` (the own solver this phase builds on), `MASTER_KEY_NOTES.md`
§1.3, §3 and §4. Prompt: `MASTER_KEY_PHASE1_2_PROMPT.md`.

Phase 1.1 gave YFT its own n/sig solver. It equals ejs on every corpus input, but it finds the
code the same way ejs does: by the **shape** of the code. So a YouTube change that breaks ejs most
likely breaks it too. Phase 1.2 makes the solver keep working when YouTube changes the shape of its
code, without an APK update:

- Find n and sig by what the code **does** (behaviour), not by how it is written.
- Ask **YouTube itself** whether an answer is right (a small live check), so no other solver is
  needed as the judge.
- Run the player in a **real browser environment** when the worker is not enough.
- **Prove it:** simulate YouTube changes on real players and count how often ejs and the own solver
  still give the right answer, and that neither ever gives a wrong one.

## မြန်မာ အနှစ်ချုပ်

- ရည်မှန်းချက်: YouTube က code ရေးပုံ ပြောင်းလည်း APK update မလိုဘဲ ဆက် resolve လုပ်နိုင်တဲ့ solver
  (ejs ထက် တစ်ဆင့်မြင့်) ဖြစ်အောင် လုပ်မယ်။
- နည်းလမ်း ၄ ခု:
  - code ရေးပုံ (shape) အစား အလုပ်လုပ်ပုံ (behaviour) နဲ့ n/sig function ကို ရှာမယ် (B)။
  - အဖြေမှန်လားကို YouTube ကိုယ်တိုင်ဆီက byte အနည်းငယ် တောင်းပြီး စစ်မယ် (V)။ ejs ကို
    အဖြေမှန် ဆုံးဖြတ်သူအဖြစ် မလိုတော့ပါ။
  - worker ပတ်ဝန်းကျင် မလုံလောက်ရင် network/cookie ပိတ်ထားတဲ့ browser page အစစ်ထဲမှာ run မယ် (E)။
  - နည်းလမ်းတွေကို အစဉ်လိုက် သုံးမယ် (ရေးပုံနဲ့ရှာ → အလုပ်လုပ်ပုံနဲ့ရှာ → page)။ တွေ့တာကို player
    version အလိုက် ဖုန်းထဲမှာ မှတ်ထားမယ်။
- သက်သေ: player အစစ်တွေကို အလုပ်မပြောင်းဘဲ ရေးပုံပဲ ပြောင်းထားတဲ့ "YouTube ပြောင်းလဲမှု အတု"
  (M1–M10) တွေနဲ့ ejs နဲ့ own ဘယ်နှစ်ခု ဆက်မှန်လဲ တိုင်းမယ်။ အဖြေမှား ၀ ဖြစ်ရမယ်။
- Task: N1 ပြောင်းလဲမှုအတု + baseline → N2 YouTube နဲ့ စစ်ခြင်း → N3 behaviour ရှာနည်း → N4 page
  ပတ်ဝန်းကျင် → N5 အစဉ် + မှတ်ဉာဏ် + flag → N6 နေ့စဉ် canary → N7 owner ဖုန်းစစ် + runbook။
- မပြောင်းတာ: Flag ပိတ်ရင် app = main၊ remote code မရှိ၊ sandbox ထဲပဲ၊ bypass မရှိ၊ VISIONOS
  ဦးစားပေး။
- မကယ်နိုင်တာ: SABR-only၊ PoToken/attestation၊ bot check၊ DRM — solver ပြဿနာ မဟုတ်ပါ။

## 0. Why (evidence)

- **Phase 1.1 result:** own = ejs on 260/260 inputs (8 players × 4 builds = 32 files); injected
  faults 320 runs, 0 wrong, 0 missed; canary on today's player `5203c085`: n and sig PASS.
  - The answers are the same because the method is the same: parse with meriyah, run the whole
    top-level code, find the URL-transform call sites by structure patterns, try them all
    (multiTry) and fail when they disagree. ejs checks agreement too.
  - Phase 1.1 adds stricter self-checks (shape, distinct n, sig index map, vectors, stability).
    They make a break fail safely; they do not make the solver work in more cases.
- **ejs's breaks were shape changes, not behaviour changes.** All 4 logic fixes changed how the code
  is written, built or applied, or which globals it reads:
  - #53 new player builds, #47 tce/es6 code generation, #54 sig encoding, #56 `window`/`self`.
  - What sig does (reorder and drop characters of the signature) and what n does (map a short
    URL-safe code to another one) did not change.
- **What the shape method cannot do:**
  - Recognise a function it has no pattern for.
  - Know whether an answer is right without another solver to compare with. When ejs and the own
    solver break on the same day, Phase 1.1 has no judge.
- **Estimate (measured in N1 and N7):** breaks that need an APK update go from about 1–3 a year to
  about 0–1. Never 0: policy changes (§5) are not solver problems.

## 1. Guardrails

All Phase 1.1 guardrails stay (`MASTER_KEY_PHASE1_1_PLAN.md` §1). In addition:

1. **Flag.** A new flag `-Pyft.ownSolverNext` (default `false`). It acts only when `ownSolver` and
   `masterCapture` are on too. With it off, the own solver is exactly Phase 1.1.
2. **No remote code, no remote patterns.** Everything that finds or runs player code ships in the
   APK. What the app learns (which strategy and which function solved a player version) stays on
   the phone as data.
3. **Live check rules (V):**
   - Only during a lookup the user started, only on the stream host YouTube returned, with the
     same HTTP client and headers as a download, and no cookies.
   - A small `Range` request: at most 64 KiB read, at most 4 checks per player version per day.
     The owner-run measurement test (N2) may read up to 1 MiB.
   - Never on bot-check, login, age, private or DRM states (they stay terminal).
   - URLs and values are never logged; only the verdict class (`pass`, `fail`, `unknown`).
   - `unknown` is never treated as `pass`.
4. **Page environment rules (E):**
   - A separate WebView with **no** `addJavascriptInterface` (Android exposes such objects to every
     frame, iframes included). Replies come back through androidx.webkit `addWebMessageListener`
     (already a dependency), limited to the app's own origin and feature-checked. Without the
     feature, E is skipped.
   - The player runs in a `sandbox="allow-scripts"` iframe: opaque origin, no cookies, no storage,
     no access to the host page.
   - Every request the app does not serve itself is refused; CSP `connect-src 'none'`; navigation
     and `window.open` are refused.
5. **Player code runs only in sandboxes:** the app's worker and page, and on the computer the
   harness's bare V8 context (`scripts/own-solver/harness.mjs`: no `require`, `process` or
   network).
6. **Never a wrong value.** Every answer passes SelfCheck. An answer found by behaviour (B) also
   needs agreement or a V `pass`. Otherwise the kind fails and its streams are dropped.
7. **Spike only.** No main merge, tag or release.

## 2. Architecture

```
YouTube module (R6) ── needs n/sig ──> OwnPlayerScriptRunner (ownSolverNext on)
  │ locator cache: player ID + script hash -> strategy, function, encoding (memory + small file)
  ▼
Strategy ladder (the first verified answer wins)
  P  Pattern    Phase 1.1 core (G1–G4): call-site patterns + multiTry        fast (~1–2 s)
  B  Behaviour  register every function the player creates, probe each      slower; only when
                with test inputs, keep the ones that act like n or sig       P fails
  E  Page       run P, then B, again in an isolated page (real DOM)          only after an
                                                                             environment error
  ▼
Verification (every strategy)
  SelfCheck  agreement, shape, distinct n, sig index map (G3), vectors, stability (Phase 1.1)
  V  Live    YouTube answers a small Range request: 2xx = pass, 403 = fail, else unknown
  ▼
resolved values | next strategy | drop the stream (never guess) -> VISIONOS rows stay
```

- The order inside the YouTube module stays R6: VISIONOS direct URLs first; the ladder runs only
  for streams that need n/sig; main's ejs path when the flags are off.
- P stays first because it is fast and proven on the corpus. B is the safety net for shapes P does
  not know. E is only for environment errors.
- The ladder never guesses, never tries random values on YouTube and never goes over the V budget.

### Strategy B in more detail

- **Register:** before the run, the player text is instrumented by inserting small calls at AST
  positions (text splicing; no code generator on the phone).
  - Every function the player creates is added to a registry when it is created, with its
    creation order and a hash of its normalized source.
  - Loops get a step counter, so a probe can never hang.
- **Probe:** each registered function with 1–3 parameters is called under a time and step budget:
  - n probes: several URL-safe strings of 11–16 characters;
  - sig probes: long signature-like strings, including the S4 map probe and edge probe.
- **Classify:**
  - sig-like: the output uses only the input's characters; its length is the input's or a little
    shorter; the same index map holds for two different inputs of the same length; deterministic.
  - n-like: URL-safe output that differs from the input; deterministic; distinct inputs give
    distinct outputs; it depends on the whole input; not a trivial transform (identity, reverse,
    rotation, base64 or URL encoding).
- **Decide:**
  - One candidate, or several that agree → the answer (then V when available).
  - Several that disagree → V picks the one YouTube accepts. Without a V `pass` → fail.
- **Remember:** the winning function's creation order and source hash go into the locator cache.
  The next lookup on the same player version calls it directly.

### Live check V in more detail

- A wrong sig gives 403 (reliable). A wrong n gives 403 or a slow (throttled) download, depending
  on the client and the date.
  - N2 first **measures** this on the owner's phone (right n, unchanged n, wrong n, wrong sig) and
    writes the decision rule into this section.
  - Until then a 2xx counts as `pass` for sig only; n relies on SelfCheck and agreement.
- **Control:** a direct URL (no n/sig) from the same lookup, when there is one. If the control
  fails too, the verdict is `unknown`. Without a control, a 403 fails this lookup but does not mark
  the strategy as failed for the player version.
- **Encoding** (G3, the #54 class): when SelfCheck leaves two encodings possible, V decides.

## 3. Tasks

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| N1 | Change simulator: mutation suite + baseline (ejs vs own P) | Phase 1.1 | Medium | TODO |
| N2 | Live check V (+ measure the n behaviour on the owner's phone) | Phase 1.1 S5 | Medium | TODO |
| N3 | Strategy B: register, probe, classify (core + harness) | N1 | High | TODO |
| N4 | Strategy E: isolated page environment | N3 | Medium–High | TODO |
| N5 | Ladder, locator cache, flag wiring | N2, N3 | Medium | TODO |
| N6 | Canary workflow (computer) | N1, N3 | Low–Medium | TODO |
| N7 | Owner live check + runbook update | N4, N5, N6 | Low | TODO |

N1 comes first: it measures the baseline that every later task is judged by. N2 can run beside N1.

### N1 — Change simulator and baseline

- New `scripts/verify-own-solver-mutations.mjs`. It reuses `harness.mjs`, the corpus and its
  player cache.
  - It writes mutated copies of each cached player under `build/own-solver/mutated/` (never
    committed).
  - It uses meriyah + astring at test time only: the copy inside main's `yt.solver.lib.min.js`, so
    no new dependency.
  - Full run on the computer; `--quick` (one build per player, a few classes) for CI.
- **Each mutation keeps the program's behaviour.** A truth hook checks this:
  - Before mutating, a tag statement that exports the original n and sig functions (found from
    P's call sites on the original player) is inserted.
  - The tagged mutated copy must still return the original answers.
  - The solvers only see the untagged copy (the same mutation and seed, without the tag).
- Mutation classes (each one tied to a real or likely YouTube change):

| ID | Mutation (behaviour kept) | Real analogue | Should survive with |
|---|---|---|---|
| M1 | Rename every identifier | every new build | P, B |
| M2 | function ↔ arrow, declaration ↔ expression, sequence ↔ statements, `var` ↔ `let`/`const` where safe | #47 tce/es6 | P (G2), B |
| M3 | Another top-level wrapper (IIFE, `.call(this)`, arrow IIFE, no `var window = this`) | #53 | B |
| M4 | sig helper object → separate functions, an array of functions or class statics | helper reshuffle | B |
| M5 | URL-transform call site through a variable, a wrapper, `.call`/`.apply` or reordered arguments | #53 | B |
| M6 | String literals (`"n"`, `"sig"`, `"split"`, `"join"`, `"set"`) read from a shuffled global table | current obfuscation | B |
| M7 | Control-flow flattening (switch loop) of the n and sig functions, where the body is a plain statement list | heavier obfuscation | B |
| M8 | Result applied raw, with `encodeURIComponent` or through `URLSearchParams.set` | #54 | P/B + V (simulated) |
| M9 | The init reads browser objects (`document.createElement`, `navigator.userAgent`, `location.hostname`, `window.top`) | #56 | E (computer: DOM stub) |
| M10 | Decoys: unused functions that look and act like n/sig but give other values | false positives | must fail or ignore, never wrong |

- **Report** (`build/own-solver/mutation-report.json`; names, classes and counts only): per class
  and strategy — survived (right n and sig), failed safely (a kind refused), **wrong** (any value
  differs from the truth).
  - Two columns for the own solver: offline only, and with a simulated V (the truth hook plays
    YouTube: right → 2xx, wrong → 403).
- **Done:** the suite runs on all 32 files; the baseline for ejs and own P is written into §4;
  wrong = 0 for own P on every class.

### N2 — Live check V

- `extractor-master`: a `StreamCheck` used by the YouTube module after the ladder answers.
  - One small `Range` request on one solved URL, with a control (§2). Add a bounded ranged GET to
    the HTTP client if it is missing.
  - Verdicts `pass` / `fail` / `unknown`; budget and cache per player version (§1.3).
  - A `fail` marks that strategy as failed for the player version; the ladder tries the next one.
- Opt-in phone test `StreamCheckMeasureTest` (`-e yft.streamCheckMeasure 1`) on one public video:
  right n, unchanged n, a wrong n and a wrong sig → status class and time for the first 1 MiB. The
  owner runs it; the result sets the n rule in §2.
- **Done:**
  - Unit tests with a fake HTTP client: pass, 403, timeout, control fails → `unknown`, budget,
    nothing logged.
  - `:app:compileDebugAndroidTestKotlin` OK.
  - The owner's measurement is recorded in §2, or marked pending.

### N3 — Strategy B

- In `own.solver.core.js`: `discover(program, request)` — register, probe, classify, decide (§2).
  The same reply shape and SelfCheck as Phase 1.1; failure reasons are class names only.
- Harness: `node scripts/verify-own-solver.mjs --strategy B` (B alone) and the mutation suite with
  B.
- Budgets (probe calls, steps per call, total time) are measured on the computer and written into
  this plan.
- **Done:**
  - B alone = ejs on 32/32 corpus files.
  - On the mutation suite B survives clearly more cases than P (target in §4), with wrong = 0.
  - JS tests for each classifier rule with fake players: a decoy, a trivial transform, a
    non-deterministic function and a looping function.

### N4 — Strategy E

- `extractor-master-android`: `WebViewPageSolverEngine` (rules in §1.4), a host page
  `yft-own-solver/page-env.html` with a sandboxed iframe, and the same job/reply protocol.
- Used only when the worker run fails with an environment error class (for example
  `ReferenceError` on a missing browser object), as in M9.
- **Done:**
  - Unit tests for routes and protocol: refused requests, no bridge object in the iframe, replies
    only from the app's origin.
  - The computer harness runs M9 with a DOM stub.
  - The phone canary covers E (N7).

### N5 — Ladder, locator cache, flag

- `OwnPlayerScriptRunner` (with `ownSolverNext`): locator cache → P → B → E, each answer verified
  (§2).
- The locator (strategy, creation order, source hash, encoding) is kept in memory and in a small
  app-private file (`files/own-solver/locators.json`, at most 32 entries), keyed by player ID +
  script hash. It is data only and is dropped when the hash differs.
- BuildConfig `OWN_SOLVER_NEXT_ENABLED`. The opt-in workflow gets an `own_solver_next` input
  (default `false` until N7).
- **Done:** unit tests with fake engines (each step, a cache hit after a restart, V `fail` → next
  step, all fail → `PLAYER_SCRIPT_REQUIRED`); the flag-off gate stays green.

### N6 — Canary workflow (computer)

- A workflow `own-solver-canary.yml`: fetch today's player (4 builds), run ejs, P, B, the vectors
  and `--quick` mutations; fail the job on any wrong value or a P/B miss.
- GitHub runs `schedule` only from the default branch. On the spike the workflow has
  `workflow_dispatch` and runs on pushes that touch the solver files. Copying a daily schedule to
  main is the owner's decision (not in this phase).
- **Done:** a manual run passes; the job summary names players and verdicts only.

### N7 — Owner live check and runbook

- Phone canary (extend `OwnSolverCanaryTest`): P, B, E and V verdicts on today's player.
- Live check with the opt-in APK (all flags on): YouTube rows equal main's on the parity URLs, no
  throttled rows, terminal cases stay terminal.
- Update the runbook (§6) for the ladder.
- **Done:** the owner's results are recorded; the board shows N1–N7 with shas.

## 4. Pass criteria (Phase 1.2 done)

- **Corpus:** the ladder = ejs on 100 % of inputs; B alone = ejs on 100 % of files.
- **Change simulator:** **wrong = 0** on every class, with and without V. Survival targets
  (confirmed or changed after the N1 baseline):
  - own ladder, offline only: ≥ 80 % of the M1–M9 cases;
  - own ladder with simulated V: ≥ 95 % of the M1–M9 cases;
  - in every class where ejs is below 100 %, the own ladder survives more cases than ejs.
- **Faults** (the Phase 1.1 set + decoys): 0 wrong, 0 missed.
- **Speed on the phone:** the P path is unchanged; the first B run on a new player version ≤ 10 s;
  a locator cache hit ≤ 0.5 s.
- **Live:** as Phase 1.1 §4, with all flags on.
- **Flag off = main;** CI + `MasterMainMoreSheetTest` green.

Baseline (filled by N1; later columns by N3–N5):

| Class | ejs | own P | own ladder (offline) | own ladder (+ simulated V) |
|---|---|---|---|---|
| M1–M10 | — | — | — | — |

## 5. Risks

- **False positives in B:** a function that acts like n but is not. Mitigated by the classifier
  rules, agreement, the M10 decoys and V. Without a V `pass`, disagreement fails.
- **V ambiguity for n:** a wrong n may be slow instead of 403. Measured first (N2); `unknown` never
  counts as `pass`.
- **Cost:** B is slower and uses more CPU on the first lookup of a new player version (about every
  1–2 weeks). The locator cache makes later lookups fast.
- **Page environment (E):** more surface than a worker. Mitigated by §1.4 and route tests.
- **Anti-tamper:** YouTube could detect the sandbox or the instrumentation. V turns that into a safe
  failure, not a wrong value.
- **Not solver problems:** SABR-only delivery, PoToken/attestation, bot checks and DRM. Then only
  VISIONOS or MSE recording remain (`MASTER_KEY_NOTES.md` §3–§4).
- **More code to maintain:** keep each strategy small and tested; the mutation suite guards it.

## 6. Runbook (when the ladder fails)

1. The canary (workflow or phone) fails → note the player ID, the strategy (P, B or E) and the kind.
2. `node scripts/verify-own-solver.mjs --add <player ID>`, then `--only <player ID>` with
   `--strategy P` and with `--strategy B`.
3. P fails but B passes → nothing urgent (the ladder covers it); add a pattern later.
4. B fails too → classify: a new behaviour (not just shape), a new environment need (E), or a
   policy change (§5). Add a mutation class that reproduces it, fix, then re-run the corpus, the
   mutation suite, `--faults` and the JS tests.
5. Checkpoint-push (CI builds the opt-in APK), run the phone canary, ship. No remote code, ever.

## 7. Out of scope

- Signed pattern hints through the R9 recipe channel (data only): a later, separate decision.
- Feeding download-time 403s back into the ladder (needs the download layer): later.
- SABR client, PoToken changes, any bypass.
- An own JS parser (option B of `MASTER_KEY_NOTES.md` §1.3).