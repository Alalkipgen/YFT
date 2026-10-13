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
- ပိုတင်းကျပ်ချက် ၄ ခု (2026-10-11 ထည့်):
  - T1 sig decoy: output တူတဲ့ candidate တွေကို အုပ်စုဖွဲ့၊ အထောက်အထားနဲ့ အစဉ်စီ၊ V ကို အုပ်စု ၃ ခုထိပဲ ပို့၊
    ပိုများရင် fail။
  - T2 ဘေးထွက်ဆိုးကျိုး: အရင် စစ်ထုတ်ပြီး ရွေးထားတာကို instance အသစ်တစ်ခုစီမှာ ပြန်အတည်ပြု။
  - T3 N1 အတွက် scope analysis အရင်လုပ် (နာမည်ပြောင်း M1၊ control flow ပြောင်း M7 အတွက်)။ N1 = Medium–High။
  - T4 n အတွက် V: n မှားရင် နှေးရုံပဲဆိုရင် V ကို sig အတွက်ပဲ သုံး၊ n က candidate တွေ သဘောတူမှ လက်ခံ။
- N2 ပြီးပါပြီ (`9e15101`): `StreamCheck` (YouTube ကို 1 byte တောင်းစစ်) ရှိပြီ။ n စည်းမျဉ်းအတွက် ဖုန်းတိုင်းတာမှု
  မရသေး (PENDING) — owner က debug app ထဲက "YFT stream check" ကို ဖွင့်ပြီး Start နှိပ်ပေးရန် (computer မလို)။
- N4 ပြီးပါပြီ (`a7c3dd9`): page ပတ်ဝန်းကျင် (E) — JS interface မပါတဲ့ WebView + network ပိတ်ထားတဲ့ sandbox
  iframe။ app ထဲမှာ မသုံးသေးပါ (N5 မှ ချိတ်မယ်)။ Computer စစ်ချက်: M9 page mode P/B 32/32၊ အဖြေမှား ၀။
- N4b တိုင်းပြီး ❌: M9bs (DOM တန်ဖိုး တိတ်တိတ်ပြောင်း) မှာ worker B က ဖိုင် ၁ ခု (631d3938 es6) sig အဖြေမှား
  (init error ၃ ခု ရှိ — အဆိုပြု init-error rule နဲ့ ဖမ်းနိုင်)။ ejs မမှား (main မထိ)။ page P 32/32။
  Fix မလုပ်ရသေး — owner ဆုံးဖြတ်ရန် (N5 မတိုင်ခင်)။
- N5a ပြီးပါပြီ (`a78626f`): ladder rule ကို computer ပေါ်မှာ တိုင်းပြီး — corpus = ejs 260/260၊
  faults / M9 / M9b / M9bs / M9c-loc / M9c-mix အဖြေမှား ၀။ ❌ M9c-nav မှာ ၁ ခု (21cd2156 es6: worker B
  sig မှား၊ init error မရှိ)။ ❗ ejs မှား ၁ (main ပါ)။ ⚠️ 631d3938 main က memory ကုန်။
- N5b (စနေပြီ): signature တိုင်း V + environment tripwire (environment ပြောင်းရင် အဖြေပြောင်းတဲ့
  function ကို မယုံ) + breaker + wiring (flag default off)။ code အကုန်ပြီးမှ test တစ်ခါတည်း (~၅ နာရီ)။

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
  - **No side effects (T2):** a probe may change the player's shared state (a cached array, a
    counter, a patched prototype), so later probes would see a different player. Two stages:
    1. **Screen** every function on one instantiation of the program. These results are only used
       to make a shortlist, never to decide.
    2. **Confirm** each shortlisted function on its **own fresh instantiation** (the prepared text
       is re-run; the parse is kept): call it twice, and once more on a second fresh
       instantiation. Only results that are the same on fresh instances count.
    - Built-in prototypes are frozen in the probe context before the first call, so a probe
      cannot patch `String.prototype` or `Array.prototype` for the next one; a function that
      needs that fails safely.
- **Classify:**
  - sig-like: the output uses only the input's characters; its length is the input's or a little
    shorter; the same index map holds for two different inputs of the same length; deterministic.
  - n-like: URL-safe output that differs from the input; deterministic; distinct inputs give
    distinct outputs; it depends on the whole input; not a trivial transform (identity, reverse,
    rotation, base64 or URL encoding).
- **Decide:**
  - One candidate, or several that agree → the answer (then V when available).
  - Several that disagree → V picks the one YouTube accepts. Without a V `pass` → fail.
- **Rank and cap (T1, sig decoys):** a player has many small functions that move characters
  around (reverse, swap, splice helpers), so B can find many sig-like candidates, while V has only
  4 checks per player version per day. So:
  - Candidates that give the **same output** on every probe are one group (one V check covers
    them).
  - Groups are ranked by evidence, strongest first:
    1. the group contains P's answer (P ran but SelfCheck refused only one condition);
    2. it is reached from a function that reads the stream's `s`/`sp` fields or builds the stream
       URL (data flow from the format object, by AST, names not used);
    3. it composes several primitive steps (reverse, splice, swap) rather than being one primitive;
       a single primitive alone (only reverse, only a swap) is never accepted;
    4. its creation order is closer to the URL-building code.
  - At most **3 groups** go to V; the rest are dropped. More than 3 equally ranked groups →
    `fail(ambiguous)`, never a pick.
  - The same rule applies to n, where decoys are rarer (the n transform is large and specific).
- **Remember:** the winning function's creation order and source hash go into the locator cache.
  The next lookup on the same player version calls it directly.
- **Plan adapted (N3, from the 32 corpus files):**
  - **Probe by call shape.** The real n transform is often one branch of a dispatcher
    (`Gn(17,369,n)`: one function, many behaviours chosen by constant keys), so probing each
    function alone is not enough. Each function with 1–3 parameters is probed plain, and a
    function the player calls with constant arguments is also probed in that call shape: constant
    arguments and one hole (`rb(6,3439,_)`), found by AST only. Keys are propagated: a call with ≥ 2 constant arguments binds the callee's
    parameters; in the callee body (nested functions skipped) locals set once from constants
    (`var v = w ^ F`) and expressions of literals, known names, unary `- ~ +` and
    `^ | & << >> >>> + - *` are evaluated; a name that is assigned again anywhere in the body is
    never a constant. Chains (`a(5, b(9, _))`, or through a local set once, ≤ 4 links) are one
    shape.
  - **n rules (measured on all 32 files):** besides the rules above, the output length follows
    the input length (the same shift for all 3 probes; hashes and hex/base64 encodings fail), the
    output for the input's first 12 characters is not a prefix of the output for the whole input
    (encodings fail), and at least half of 16 single-character changes alter the output. That replaces "depends on the
    whole input": real transforms change for 11–16 of 16 positions.
  - **sig weak tier:** on one player (`74edf1a3`) the real sig nets out to a single swap at the
    probe length. A single-swap map is kept only as a weak candidate, only when the function is
    named by the stream-URL builder code (a name the builder calls through a plain alias counts,
    M5), and weak candidates count only when no strong one is confirmed. Identity, cut, reverse
    and rotation never count.
  - **Evidence:** "builder" = names found by P's two builder patterns (AST; evidence only, never
    a decision). Score: P's hint +1000000, builder +1000, composed (loop steps) +100, one
    parameter +10; creation order only breaks ties (earlier first).
  - **P's hint is optional:** a request with `hint: false` switches it off. "B alone" in the
    corpus and mutation runs is measured with it off; the ladder (N5) may use it.
  - **Skipped:** the whole-program wrapper and functions over 250k characters (re-running the
    wrapper as a probe hung > 60 s).

### Live check V in more detail

- A wrong sig gives 403 (reliable). A wrong n gives 403 or a slow (throttled) download, depending
  on the client and the date.
  - N2 first **measures** this on the owner's phone (right n, unchanged n, wrong n, wrong sig) and
    writes the decision rule into this section.
  - Until then a 2xx counts as `pass` for sig only; n relies on SelfCheck and agreement.
- **When V cannot judge n (T4):** if the measurement shows that a wrong n is served (2xx) and
  only slowed down, a short Range read cannot tell right from wrong. Then:
  - V is used for **sig only**; for n, V's verdict is always `unknown`.
  - An n answer from B is accepted only when B's n candidates **agree** (one group after T1) and
    pass SelfCheck; disagreement → `fail` (no V tie-break for n).
  - The optional speed check (time to read the first 1 MiB, compared with the control) is **not**
    used to decide; it is only logged as a class (`fast`/`slow`) for the owner, because network
    speed varies too much.
  - The N1 report then counts n by the offline column only.
- **Control:** a direct URL (no n/sig) from the same lookup, when there is one. If the control
  fails too, the verdict is `unknown`. Without a control, a 403 fails this lookup but does not mark
  the strategy as failed for the player version.
- **Encoding** (G3, the #54 class): when SelfCheck leaves two encodings possible, V decides.
- **n rule (N2 measurement): PENDING.** The owner's phone measurement has not run yet. Until it
  does, `StreamCheck` runs with `NRule.PENDING`: a 2xx is `pass` for sig and `unknown` for n; a
  proven 403 (the control opens) is `fail` for both (dropping is safe, passing is not). How to
  measure:
  - **No computer (the owner's case: mobile data only, no Wi-Fi):** install the opt-in debug APK
    from the CI artifact, open the launcher entry **"YFT stream check"** (debug build with both
    Master flags only), tap Start, then Copy result. About 10–15 MB of data.
  - With a computer: `bash scripts/stream-check-measure.sh` (phone on adb; `VIDEO=<id>` for
    another public, embeddable video), or the same test from Termux over wireless debugging (the
    CI artifact also holds the test APK).
  The report (`own-solver/stream-check-measure-*.json` in the app files) gives each variant's
  status class, bytes, time and speed class, and the rule it suggests:
  - wrong n → 403 and unchanged n → 403: `JUDGES_N` (V judges n like sig);
  - wrong n → 2xx: `SIG_ONLY` (T4 above: V never judges n);
  - anything else: stays `PENDING`.
  The owner's result and the chosen rule are written here (date, player, video, statuses).

## 3. Tasks

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| N1 | Change simulator: mutation suite + baseline (ejs vs own P) | Phase 1.1 | Medium–High | Done (`765e91f`) |
| N2 | Live check V (+ measure the n behaviour on the owner's phone) | Phase 1.1 S5 | Medium | Done (`9e15101`; measurement pending) |
| N3 | Strategy B: register, probe, classify (core + harness) | N1 | High | Done (`52e1945`) |
| N4 | Strategy E: isolated page environment | N3 | Medium–High | Done (`a7c3dd9`) |
| N4b | Environment-dependent values (M9b/M9bs), init errors by class | N4 | Medium | Built (`bdb45c6`, harness `8140b27`); measured ❌ (worker B: 1 wrong M9bs value; fix: owner decides; §4 note "N4b") |
| N5a | Ladder rule (init errors in replies, shared case table), runners `--ladder`/`--dyncode`, M9c; app unchanged | N4b | Medium | Done (`a78626f`, CI 38189985242); measured (§4 note "N5a") |
| N5b | Page-P step budget, circuit breaker, ladder + locator cache wired behind `ownSolverNext` (default off); M9c fixes: V on every signature, environment tripwire | N5a | Medium–High | Done (TEST_SHA `48dd744`, test cycle 22/22 ✅; A.8 V wiring `df9a1e1`, CI 38413760115 ✅) |
| N6 | Canary workflow (computer) | N1, N3 | Low–Medium | Done (`0714d76`; push run 38424779157 ✅, owner re-run ✅; label triggers: this commit) |
| N7 | Owner live check + runbook update | N4, N5, N6 | Low | TODO |

N1 comes first: it measures the baseline that every later task is judged by. N2 can run beside N1.

Overall difficulty: **High** (Phase 1.1's hardest task was Medium–High). The hardest task is N3
(classifier rules, decoys, speed), then N1 (behaviour-keeping mutations) and N4 (WebView security).
Use the highest reasoning level for N1, N3 and N4; start N3 in a fresh session (the prompt reads the
repository, not chat history).

### N1 — Change simulator and baseline

- New `scripts/verify-own-solver-mutations.mjs`. It reuses `harness.mjs`, the corpus and its
  player cache.
  - It writes mutated copies of each cached player under `build/own-solver/mutated/` (never
    committed).
  - It uses meriyah + astring at test time only: the copy inside main's `yt.solver.lib.min.js`, so
    no new dependency.
  - Full run on the computer; `--quick` (one build per player, a few classes) for CI.
- **Scope analysis first (T3).** M1 (rename) and M7 (control-flow flattening) are only correct
  with a scope model, else a rename can capture or shadow another binding and silently change the
  program:
  - Build a scope tree from meriyah's AST (functions, blocks, `catch`, class bodies; `var` hoisted
    to its function, `let`/`const`/`class` to their block; function declarations per their
    strict/sloppy rules) and resolve every identifier reference to its binding.
  - Rename only bindings whose every reference resolves (never globals, never names read through
    `eval`, `with` or `arguments`, never property names); new names are fresh in every scope they
    enter.
  - M7 flattens only a function body that is a plain statement list (no `let`/`const` captured by
    a closure, no labels, no `try`), and keeps hoisting.
  - Each mutator has its own unit tests (shadowing, hoisting, closures, `catch` parameters,
    default parameters) before it is run on a player.
- **Each mutation keeps the program's behaviour; truth comes from the original player.**
  Plan adapted (N1): the right n/sig for an input depends on the input, not on how the code is
  written, so truth = ejs on the **original** player (the proven oracle of the parity runner).
  Every solve runs through `harness.mjs`'s bare V8 context; nothing runs player code any other
  way (a separate truth hook that executed player code was rejected by the safety review). A
  mutation that changed behaviour shows up as ejs and own agreeing on a new value: that file is
  skipped, never counted as wrong. The mutators' own unit tests (T3) check behaviour on small
  hand-written programs.
- Mutation classes (each one tied to a real or likely YouTube change):

| ID | Mutation (behaviour kept) | Real analogue | Should survive with |
|---|---|---|---|
| M1 | Rename every function/block/catch local with minifier-style names; globals (`_yt_player`), the wrapper parameter and locals deliberately named after browser globals (`var window = this`) stay | every new build | P, B |
| M1w | M1 + the wrapper parameter (`g`) | a wrapper change | P, B |
| M2 | function ↔ arrow, declaration ↔ expression, sequence ↔ statements, `var` ↔ `let`/`const` where safe | #47 tce/es6 | P (G2), B |
| M3 | Another top-level wrapper: `void (fn).apply(receiver, [args])` with the exact receiver (more forms later) | #53 | B |
| M4 | sig helper object (2–4 small methods without `this`) → separate wrapper-level functions; the object keeps its keys (arrays/class statics later) | helper reshuffle | P, B |
| M5 | Every call of a wrapper-level function with 3+ call sites (the URL builder) goes through a fresh alias (`.call`/`.apply`/wrappers later) | #53 | P, B |
| M6 | String literals read from one shuffled `"…".split(sep)` table (the real players' form; directives and keys untouched) | current obfuscation | P (G2 tables), B |
| M7 | Control-flow flattening (`var c = 0; while (c < N) switch (c) { case i: c = i + 1; …; continue; }`) of wrapper-level functions whose body is 3–40 plain statements (no function/class/`let`/`const` at the top, no labels, not async/generator; directives kept); each such function with chance 0.7, so the n and sig functions are flattened in most cases, and so are other functions | heavier obfuscation | B |
| M8 | The global `encodeURIComponent` / `decodeURIComponent` are reached through fresh wrapper functions, appended at the end of the wrapper body | #54 | P/B + V (simulated) |
| M8t | As M8, with the wrapper functions first in the wrapper body (new encode helpers are met first) | #54 | P/B |
| M9 | The init reads browser objects (`navigator.userAgent`, `location.hostname`, `document.createElement("div")`, `window.top`) into unused variables in the middle of the wrapper body | #56 | E (computer: DOM stub) |
| M10 | Decoys: unused functions that look and act like n/sig but give other values | false positives | must fail or ignore, never wrong |
| M11 | Harmless `var` declarations at the top of the wrapper body (a new prelude) | new prelude | P, B |
| M11f | Harmless function declarations at the top of the wrapper body | new prelude | P, B |

Plan adapted (N1 part 2–3):
- M7 flattens every eligible wrapper-level function (chance 0.7 each), not only n and sig: a real
  obfuscator would not spare the rest, and a locator must not find n/sig by "the flattened one".
- M8 keeps behaviour: changing how the builder applies the result (raw, encoded,
  `URLSearchParams.set`) changes the URL the harness reads, i.e. the right answer itself, so it
  cannot be a behaviour-keeping mutation. That part moves to the simulated V (N2/N5). M8 instead
  routes the existing encode/decode calls through new helper functions, the shape of #54.
- M8t, M11 and M11f were added to explain an M8 finding: ejs failed only when the new encode
  helpers came first (M8t), not with them at the end (M8) and not with other new code at the top
  (M11 vars, M11f functions). ejs is sensitive to new encode/decode helpers met first, not to a
  new prelude in general.
- M9 places the reads in the middle of the init (a prelude-only form would mix the M11 effect in);
  roots that the wrapper declares itself (e.g. `var window = this`) are skipped, so the reads
  always reach the global browser objects.

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
- **Result (N2 done, 2026-10-11):**
  - `extractor-master/.../modules/youtube/StreamCheck.kt`: verdicts `PASS`/`FAIL`/`UNKNOWN`,
    `verdictFor(kind)` (sig, n by `NRule`), `marksFailed(kind)`; reasons and status classes only
    (no URL, header or value in any result or `toString`); an in-memory `Ledger` (budget per
    player version per UTC day, verdict cache per answer, bounded to 64 keys).
  - `StreamCheckTest` (13 tests, fake HTTP client): pass, 403 + control opens → fail (strategy
    failed), 403 without a control → fail for this lookup only (not cached), control fails too
    → unknown, timeout / network error / stuck client / thrown error → unknown, other statuses
    and a client without file checks → unknown, 4 checks per player version per day (5th →
    unknown with no request; other version and next day have their own budget; `Policy`
    refuses more than 4), cache (hit = 0 requests; expires after a day; proven fail reused), no
    request when not user-started, on bot/login/age/private/DRM states or with nothing to judge,
    only a returned `googlevideo.com/videoplayback` HTTPS host (a foreign control is ignored),
    cookies/credentials/`Range` stripped from the download headers, nothing printed or logged,
    the n rule.
  - Phone test `app/src/androidTest/.../master/StreamCheckMeasureTest.kt` (opt-in
    `-e yft.streamCheckMeasure 1`) + `scripts/stream-check-measure.sh`: WEB_EMBEDDED_PLAYER
    player request with the watch page's signature timestamp; one adaptive mp4 stream (≥ 1 MiB,
    with `signatureCipher` and `n` preferred); ejs and the own runner must agree on n and sig;
    variants right, wrong sig, unchanged n, wrong n, right again; each reads the first 1 MiB at
    most; the right values must open the stream before and after. Report without addresses.
  - **Plan adapted:**
    - No new HTTP method: the client's existing one-byte `probe` (`Range: bytes=0-0`, same
      HTTPS and credential rules, no retry, time limit) is the bounded Range read.
    - `StreamCheck` is not wired into the YouTube module yet; N5 calls it after the ladder
      answers, behind `ownSolverNext`, so Phase 1.1 stays unchanged.
    - A check = one target request + one control request only after a 403; the budget counts
      checks (4 per player version per day), so T1's 3 groups fit.
    - Only proven verdicts (2xx, or 403 with an opening control) are cached; a lone 403 and
      `unknown` are checked again next time (within the budget).
    - The n rule is a policy value (`PENDING`, `JUDGES_N`, `SIG_ONLY`) set from the measurement;
      the ledger is in memory (N5 adds the small file next to the locator cache).
  - Measurement: **pending** (owner runs it; §2 n rule).
  - Follow-up (owner has no computer and no Wi-Fi): the measurement moved into
    `app/src/debug/.../master/StreamCheckMeasurement.kt`, used by both the phone test and a
    debug-only owner tool `StreamCheckMeasureActivity` (launcher entry "YFT stream check", enabled
    only when `yft.masterCapture` and `yft.ownSolver` are on, via the manifest placeholder
    `yftOwnerTools`; never in release or preview; starts only on a tap). The CI artifact also
    carries the test APK (`f5cff7b`).
  - First owner run (2026-10-11): `not playable for the embedded client: ERROR`. Checked from
    the computer too: **WEB_EMBEDDED_PLAYER answers `ERROR` "This video is unavailable" for every
    video tried** (aqz-KE-bpKQ, jNQXAC9IVRw, dQw4w9WgXcQ; with and without `thirdParty`,
    visitor data, the API key, a `1.x` version, or the embed page's own context with
    `embeddedPlayerEncryptedContext`); TVHTML5 answers `UNPLAYABLE` "The page needs to be
    reloaded"; WEB answers OK but without stream addresses (SABR only); **MWEB answers OK with
    `signatureCipher` streams**. The tool now tries the embedded client, then MWEB, and reports
    which client it used and each client's status and reason. Whether MWEB streams open without a
    token is part of what the next run shows (`right` = 2xx or not).
  - Second owner run (2026-10-11, `c74df37`, player 5203c085, video aqz-KE-bpKQ, mobile data):
    embedded `ERROR`, **MWEB OK**; itag 160 with n only (no sig); ejs = own on n (agree); **all
    four reads 403** (right, unchanged n, wrong n, right again). So MWEB streams are refused
    without a token whatever n is: the run is **invalid** and the n rule stays **PENDING**. (This
    is the case the control rule exists for: StreamCheck would answer `unknown`, never `fail`.)
    Next: a client whose streams open without a token and that needs n (TV after its "reload"
    fix), or keep PENDING (safe: n relies on SelfCheck and agreement).
  - TV follow-up (2026-10-11): with the TV page's own version the "reload" answer goes away, but
    from the computer TV then got the bot check. Adding TV to the tool was **stopped twice by the
    automated safety review** (also after the owner's go-ahead), so it is not done. **Decision:
    the n rule stays PENDING** (safe: V judges sig only; n is accepted only with SelfCheck and
    agreement). Phase 1.3 D1/D2 must settle with the owner how TV may be used before relying on it.

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
- **Budgets (measured on the computer, 2026-10-11; the phone is measured in N5/N7):**
  - Limits: init 50M loop steps; 400k steps per probe call; screen ≤ 40 s (then `budget`, safe);
    ≤ 20000 functions; shapes ≤ 6000 (≤ 96 per name), key bindings ≤ 4000, chains ≤ 400;
    ≤ 3 groups per kind.
  - Time on 5203c085 main (`--strategy B --verbose`): prepare ≈ 2–2.5 s (parse 0.5–0.7,
    instrument 0.6–0.9, shapes 0.5, reparse 0.5), instantiate 0.2 s, screen ≈ 2 s, confirm
    n 0.2 s + sig 0.05 s, SelfCheck 0.02 s: **first B run ≈ 5 s** on the computer (phone target
    ≤ 10 s). About 12.4k functions registered, 7.1k screened, 7.6k probes, ~800 call shapes (~200
    from propagated keys, ~30 chains); 4–5 functions stop at the step limit (worst ≈ 1 s).

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
- **Built (N4, 2026-10-11, `a7c3dd9`):**
  - `extractor-master-android`: `WebViewPageSolverEngine` (an `OwnSolverEngine`; `isAvailable`
    needs `WebViewFeature.WEB_MESSAGE_LISTENER`, else E is skipped), `OwnSolverPageEnvRoutes`
    (its own route table: `page-env.html` main frame only, `page-frame.html` sub frame only, the
    two scripts, parser, core and the job `page-env-input.json` (served once) as sub resources
    only; the worker's files and main's ejs files are refused) and `OwnSolverPageReplyGate` (the
    host page's first string message from the main frame of the app's origin ends the run; ≤ 12
    MiB). No Java object is exposed to page scripts; the listener's object is limited to
    `https://appassets.androidplatform.net`, so the opaque-origin frame never gets it. Fresh
    WebView per run, settings as the worker engine, third-party cookies off, no dialogs, windows,
    permissions or console output, only the frame's own page may navigate (in its frame).
  - Assets: `page-env.html` + `page-env.js` (host page: app script only, CSP `script-src 'self';
    connect-src 'self'; frame-src 'self'`), `page-frame.html` + `page-frame.js` (the frame:
    `sandbox="allow-scripts"` set before it has an address or a parent, and again by the
    response header; CSP `default-src 'none'; script-src <page-frame.js> 'unsafe-eval';
    worker-src 'none'; connect-src 'none'; webrtc 'block'`; DNS prefetch off).
  - Core page mode (`request.page`): `installEnvironment` keeps a real window's own browser
    objects (window, document, location and top cannot be redefined there; the worker mode's
    defines would throw `TypeError`) and adds only missing names; the worker mode is unchanged.
  - `OwnSolverEnvironmentErrors` (`extractor-master`): the rule N5 asks before E
    (`ReferenceError`/`TypeError` in a whole failure, a worker error class or a candidate error).
- **Plan adapted (N4):**
  - **Reply path:** host page → frame: the job with a fresh 128-bit token, only after the frame
    says it is ready (`event.source` checked); frame → host page: `{token, reply}` to the app's
    origin only; the host page passes on the first reply with the token (source + token checked,
    string, ≤ 12 MiB) once. The frame takes one job, only from its parent with the app's origin,
    takes its reply path (host `postMessage`, `JSON`, a timer) before player code and runs the job
    in a later task, so `window.event` never shows the token to player code. Player code can still
    post to the host page; without the token it is dropped.
  - **Same protocol as the worker (P only):** E's B run needs a job field for the strategy and the
    rule needs per-kind error classes in the reply (today a reply names only whole failures such
    as `program:TypeError`; a kind that failed for its environment shows as e.g. `n:probe`). Both
    are protocol changes for the worker and the frame together: N5.
  - **WebRTC** can open connections no CSP directive covers (Chromium: `webrtc 'block'` is set and
    ignored where unsupported). The frame removes `RTCPeerConnection`/`webkitRTCPeerConnection`
    before player code and refuses the job (`WebRtcOpen`) if it cannot. Residual: a frame the
    player creates itself (about:blank, same opaque origin) has its own constructor. Holding all
    of the renderer's WebRTC connections would close that too; not done (unmeasured cost on the
    app's shared renderer, needs the owner's decision). The frame has no cookies, storage or app
    data to send; its inputs are the n/sig challenge values of the user's own lookup.
  - **Shared renderer:** all of the app's WebViews share one renderer main thread, and E runs
    there (a worker runs on its own thread). While E runs, the in-app browser's scripts wait. B
    bounds loops (step limits); P in the page has no bound: a player top level that never ended
    would hold the renderer, which `WebView.destroy()` cannot interrupt. Real players' top levels
    end (browsers run them on every watch page); N5 decides E = B only, or P with a bound.
- **Results (N4, computer):** see section 4 note "N4".

### N5 — Ladder, locator cache, flag (split: N5a, N5b)

**Plan adapted (N5a, owner-approved):** N5 is split. N5a (done) builds and measures the rule on
the computer; the app's behaviour is unchanged (nothing calls the rule; flag off = main). N5b wires
it. What N5a added:

- **Protocol:** worker and page-frame replies carry `init_errors` (the core's
  `report.topLevelErrors`) and `init_error_classes` (class names, at most 9).
  `OwnSolverProtocol.Decoded.Solved.initErrors` is null when a reply has no count (a run that
  never instantiated, an older core): unknown, never trusted.
- **Rule** (`OwnSolverLadderRule`, extractor-master, pure; mirrored by `scripts/own-solver/ladder.mjs`;
  both test suites read `extractor-master/src/test/resources/own-solver/ladder-rule-cases.json`):
  a worker step (P, then B) gives a kind only with init errors 0 and every asked input answered.
  The page (E) runs only when a worker step needs it: an environment failure (ReferenceError /
  TypeError), init errors > 0, or answers without a count. E gives a kind only with its own init
  errors 0, and a signature only when V passes (a wrong n is slow; a wrong sig breaks playback
  silently). Anything else fails the kind; an answer the rule does not return is never cached.
  `OwnSolverEnvironmentErrors.needsPage` is unchanged.
- **Runners:** `verify-own-solver.mjs --ladder` (worker P, worker B alone, page P, page B alone per
  file; the ladder's answer against ejs; page step "PB" = page P, else page B; V simulated = a
  page signature counts only if it equals ejs), `--dyncode` (player `eval` reads and Function
  constructor calls per run, at init and later; harness.mjs), `--faults --ladder` (each fault case
  through the ladder run lazily, as the app will). Mutation runner `--ladder`: columns PB+V,
  PB off (no V: no page signature), B+V, B off.
- **M9c** (environment classes, outside the 448 baseline; `mutate.mjs environmentLiterals`):
  integer literals in functions become `c - d + X`. nav: X reads `navigator.cookieEnabled` (true
  on YouTube; false in the worker's stub and the page frame). loc: X reads `location.hostname`
  (youtube.com in a real browser and the worker's test URL; appassets in the page frame). mix:
  M9bs's DOM value read only on youtube.com (an init error in the worker; none in the page, which
  is not on youtube.com).
- **Stub audit (docs only):** the worker's G4 stubs (`own.solver.core.js environmentGlobals`)
  against a real browser on youtube.com, and what E (the app's page frame) has:

| Object | Worker stub | Real browser on YouTube | Group | E (page frame) |
|---|---|---|---|---|
| `navigator.cookieEnabled` | `false` | `true` (the site needs cookies) | a ⚠️ (M9c-nav) | likely `false` (opaque origin, no cookies); N7 |
| `navigator.webdriver` | missing | `false` in every current browser | a ⚠️ | real (`false`) |
| `navigator.userAgentData`, `plugins`, `mimeTypes`, `mediaDevices`, … | missing | present (`userAgentData`: Chromium only) | a ⚠️ (use → TypeError → init error → E) | real |
| DOM methods (`document.createElement`, …) | missing | present | a ⚠️ (M9b) | present |
| `document.cookie` | `""` | YouTube's cookies | a ⚠️ | throws `SecurityError` (opaque origin) |
| `location.ancestorOrigins` | `[]` (Array) | empty `DOMStringList` | a ⚠️ (type only) | `DOMStringList` with the host origin |
| `navigator.userAgent` | Android 14, Pixel 7, Chrome 129 (or the app's `env.userAgent`) | Chrome ≥ 110 on Android sends the reduced UA (`Android 10; K`) | b ⚠️ impossible pair: Chrome 129 with a full model | the WebView's real UA (`; wv`) |
| `navigator.hardwareConcurrency` | `4` | the device's cores (Pixel 7: 8) | b ⚠️ pair with the Pixel 7 UA | real |
| `navigator.platform`, `language(s)`, `maxTouchPoints`, `onLine` | `Linux armv8l`, `en-US`, `5`, `true` | the device's | b ✅ | real |
| `navigator.vendor` | `Google Inc.` | `Google Inc.` (Chrome) | c ✅ | real |
| `location`, `document.URL`, `document.domain` | the test URL on `www.youtube.com` | the watch page on `www.youtube.com` | c ✅ | `appassets.androidplatform.net` ⚠️ (M9c-loc); `self.origin` `"null"` |
| `document.visibilityState` / `hidden` | `visible` / `false` | `visible` / `false` while watching | c ✅ | unknown for a hidden WebView (N7) |

  Group a: the same in every real browser and the stub differs (a check for it fails silently or
  throws); b: varies by device (⚠️ only for impossible pairs); c: matches. Sources: MDN
  (Navigator, Document.cookie, Location.ancestorOrigins), the HTML standard (sandboxed origin:
  `document.cookie` throws), Chromium's User-Agent Reduction. The E column is from the frame's
  design (opaque origin, appassets host); the unknowns are measured on the phone in N7.

**N5b (next, behind `ownSolverNext`, default off):** a page-P step budget (page mode only; worker
P needs none: `worker.terminate()`): min(corpus max steps × 10, a time cap measured on the corpus),
`Date.now()` checked every N steps; eval / new Function handled per N5a's `--dyncode` data; page P
M9/M9b/M9bs 32/32 with the budget. Kotlin timeouts split ready vs job (only a job timeout trips
the breaker). Circuit breaker: a job timeout or a gone renderer → E off until the process
restarts; a persistent player-hash marker written before E and cleared after; E off for a hash
only after the marker was left 2 times in a row (1 → retry once). Fake-engine tests; then the
ladder in `OwnPlayerScriptRunner` behind `ownSolverNext`, and the items below.

**Plan adapted (N5a results, owner-approved 2026-10-12):** the goal is not a B that is never wrong
(a heuristic over player code cannot be) but a wrong value that never reaches the user. M9c's
wrong values come from player functions that read an environment that differs from youtube.com in
the worker and the frame; P, B and E all run the player's own functions there, so agreement
between them does not help. YouTube's sig and n functions are pure string functions today, so a
function whose answer depends on the environment is not trusted. Added to N5b, in this order,
code first and one test cycle at the end (a cycle is ~5 h):

0. Evidence first (test scripts only): count environment reads while a solved function runs
   (target: 0 on the corpus, > 0 on the five M9c B wrong values); find the step that exhausts the
   heap on 631d3938 main M9c-nav (P, B, ejs alone). Adapt the plan if the data disagree.
1. V on every signature: a worker signature (P or B) is used only after V = pass, like E's; a
   pass is kept per player version and strategy (a sig function does not change within a
   version). Rule cases (Kotlin + JS) updated.
2. Environment tripwire (worker and frame): reads of environment objects during a sig/n call are
   counted (Proxy), and once per player version a second context with changed environment values
   runs the solved functions on 2–3 inputs and must give the same answers. A trip counts as an
   init error (no answer from that step). A B guard (no sig when n has no candidates) only if
   step 0 shows the tripwire misses a case.
3. The breaker also covers worker jobs, a gone renderer (`onRenderProcessGone`, own path only;
   main unchanged) and V = fail (two strikes per player version).
4. Then the wiring above. Tests once: unit → M9c 96 (one process per player) → corpus (= ejs
   260/260, tripwire false positives 0) → M9b/M9bs → faults → M9 → the 448 suite; push after
   each. Done when ladder wrong = 0 in every run, the corpus is unchanged and flag off = main.

V for n stays the N2 phone measurement (owner: debug app "YFT stream check" → Start; at most
1 MiB per variant): if YouTube refuses a wrong n, V checks n as well. Later (N6+): load the frame
with a youtube.com base URL (network still blocked) so `location` and the origin are real, and
mirror that environment into the worker; needs a security spike first.

- `OwnPlayerScriptRunner` (with `ownSolverNext`): locator cache → P → B → E, each answer verified
  (§2).
- The locator (strategy, creation order, source hash, encoding) is kept in memory and in a small
  app-private file (`files/own-solver/locators.json`, at most 32 entries), keyed by player ID +
  script hash. It is data only and is dropped when the hash differs.
- BuildConfig `OWN_SOLVER_NEXT_ENABLED`. The opt-in workflow gets an `own_solver_next` input
  (default `false` until N7).
- **Done:** unit tests with fake engines (each step, a cache hit after a restart, V `fail` → next
  step, all fail → `PLAYER_SCRIPT_REQUIRED`); the flag-off gate stays green.

#### N5b — Final plan for the next cycle (owner-approved, base `f9eaedc`)

Rules: tests first; flag off = main; push only `spike/master-extractor-backup`; never touch main,
merge, release or tag; never print player text or values. Stop points: A.1 (passed: the owner
chose, see A.1) and A.7.

**A — Code (one agent, Max reasoning)**

- **A.0 Sync.** Back up local drafts outside the repo, sync to `f9eaedc` (or a later head that only
  adds docs), check HEAD.
- **A.1 Budget data** on corpus and 631d3938 M9c-nav:
  - ms per phase for P and B: parse/prepare, init, search/screen, confirm, SelfCheck, tripwire.
  - Cache-hit time, P and B separately, (a) same process, (b) after a restart. The cache does not
    exist yet: estimate it as the phase sum without search; measure the real hit after A.4 and
    report it at A.7.
  - Option "keep B's prepared program in memory": today the wrappers reject `strategy:"B"` with
    `type:"prepared"` as `BadJob` (`own-solver-worker.js:67`, `page-frame.js:80`) and
    `OwnSolverProtocol` requires `Player.Raw` for B. Report the protocol/wrapper/core changes, the
    RAM cost (MB for 2 players = `maxCachedPlayers`), the tests to re-run, and that it helps (a)
    only (the memory is gone after a restart).
  - Choose job cap and lookup cap together: on corpus and 631d3938 M9c-nav, B must finish in the
    time left after P, with a written phone margin factor. Job cap < Kotlin job timeout 45 s minus
    a grace, consistent with P_INIT_MS 20 s and B_SCREEN_MS 40 s. Write the numbers and why.
  - A hit was over 0.5 s (numbers in PROGRESS.md), so A.1 stopped and asked: (1) target (a) only;
    (2) relax the (b) target; (3) keep B's prepared program in memory; (4) prepared program on
    disk. **Owner, 2026-10-13: (1) + (2).** The 0.5 s target is removed; the phone targets are in
    §3 N7. (3) waits for N7 (condition there). (4) is refused: the locator file stays data only and
    code from disk never runs.
- **A.2 Deadline and caps** (tests first):
  - Core: deadline = min(job cap, time left in the lookup), checked in the P and B step counters.
    At the deadline keep only kinds whose requested checks all finished (SelfCheck always,
    tripwire when the job asks for it); the rest are `budget`. Tighter caps for page steps.
  - Caps (owner-approved 2026-10-13 from A.1's numbers, phone margin ×4): job cap 40 s (worker
    steps; Kotlin job timeout 45 s − 5 s grace), page cap 35 s (provisional: re-tuned with N7's
    phone numbers, §5), lookup cap 60 s (`P_INIT_MS` 20 s + job cap).
  - Kotlin: `budget` memory keyed `player|step` (≤ 64), no breaker, recorded only when the step
    had the full job cap. When the lookup cap runs out, later steps are not run, not recorded, no
    breaker. A core that ignores the deadline still ends in the job timeout → breaker (unchanged).
  - Short deadline (owner, 2026-10-13): a step whose deadline was below its full cap (job 40 s,
    page 35 s) because the lookup had less time left, and that ended `budget`, is recorded only the
    second time in a row for the same `player|step`; once is not recorded. "In a row" =
    consecutive runs of that `player|step`: a result other than `budget` in between starts the
    count again; a lookup where the step did not run does not count. All four steps; the same
    memory (≤ 64), no breaker, gone after a restart (weakness in §5).
  - Tests: deadline inside tripwire (worker) or SelfCheck (page) → 0 answers; `--no-tripwire`
    worker keeps kinds whose SelfCheck finished; all 4 steps slow → the lookup ends inside the cap
    and the next lookup runs the page steps; slow P → B gets a short deadline → `budget` not
    recorded → the next lookup runs B; full-cap `budget` → recorded → step skipped next lookup;
    two lookups where B gets only a short deadline → the third lookup skips B; short → success →
    short = not recorded.
- **A.3** `--judge ladder` in the mutation runner (corpus still judges B alone).
- **A.4 Locator cache** (behind the flag, tests first): `files/own-solver/locators.json`, ≤ 32
  entries, key player ID + script hash, data only. Cache clean answers only; an M9bs-type wrong is
  never cached; a hit still passes every check; n never skips the tripwire; poisoned-entry test.
  - **Done (A.4; numbers in PROGRESS.md).** A part is data only: P = the candidate indexes that
    answered every input + a hash of the probe outputs; B = the winning group's members (static
    function id, shape index, a hash of the function's source and its size) + the probe hash
    (32-bit FNV-1a: a fit check, not a secret). A P hit runs only the cached candidates on the
    inputs; each must answer every input with the same probe outputs, every candidate runs once
    on one probe and must agree, and the tripwire runs with all candidates. A B hit skips the
    screen: the members must be the same functions (id, size, hash), then profile → confirm →
    groups → solve → tripwire. Any difference is a miss and the kind runs in full in the same job
    (a hit is never worse than a miss). Kotlin keeps a kind's part only from the step the ladder
    chose (a worker step with init errors 0 whose core gave a part; a signature's part only after
    V = pass); a miss, another chosen step or V = fail drops it.
  - **Plan adapted (A.4):** the store (interface, memory LRU and a plain-Java atomic file:
    temp file + rename; unreadable, corrupt, another format or core version → empty; IO never
    throws) is in extractor-master, not extractor-master-android: it needs no Android API and the
    local JVM tests cover it; the app only names the files under `filesDir`
    (`SiteAdapterModule`). Page steps never locate (the page frame refuses a job with a locator):
    they run only for kinds the workers left, always in full. One function can sit in B's winning
    group in two shapes, so B's members are distinct (id, shape) pairs (the corpus found it: B
    never hit before the fix). The runner times hits in a fresh isolate per job, as the app's
    worker (a new Worker per job); a rerun in the same isolate is warm and too fast.
- **A.5 V ledger on disk**: key `player|step|scriptHash|locatorHash`; any hash change → V runs
  again. Test: poisoned locator + ledger pass → V runs again, wrong 0.
  - **Done (A.5).** `OwnSolverVLedger` (≤ 64 keys, oldest first, `files/own-solver/v-ledger.json`
    through the same file store) holds the passes. `locatorHash` is the hash of the part the
    step was sent when the reply says it hit, else `-` (a reply cannot bring a part of its own into
    the key). **Plan adapted (A.5):** the runner's memory `passed` set is what moved to disk;
    `StreamCheck.Ledger` (check budgets per day and remembered verdicts of one stream check)
    stays in memory.
- **A.6 Docs**: §5 residual risks (regex and wide recursion → N8; deep recursion is stopped by the
  stack limit), main candidate list, M9c-nav gap (next cycle 33 runs), B-prepared option status,
  chosen caps and why.
  - **Done (A.6):** §5 "Work the step budget cannot see", "Locator cache", "Caps", "B prepared
    program (option 3)", "M9c-nav gap", "Main candidate list" and "V wiring in the app".
- **A.7** All tests pass → push → CI ✅ → TEST_SHA in PROGRESS.md with the exact commands for each
  Test Agent A–D → ✅/❌ report (command + summary + exit code, CI id + sha + TEST_SHA) → stop.
  - **Done (A.7):** TEST_SHA `48dd744` with the Test Agents' lists and result files in PROGRESS.md.
- **A.8** (owner, after A.7; before N7, tests first) V wiring in the app (§5): the gate around
  every reader that gets the own runner, with `ownSolverNext` on or off. No JS change, so TEST_SHA
  stays `48dd744`.
  - **Done (A.8):** `YouTubeSignatureGuard` around main's reader in the app; Phase 1.1's
    signatures need V too (one pass per player version and process). Tests, mutations and CI in
    PROGRESS.md. Not built: a timeout fallback to main and a V prewarm (decided after N7, §3 N7).

**B — Test (Test Agents A–D, Medium; each on its own computer with its own result file; exact TEST_SHA;
≈ 80 min each by N5b times)**

| Test Agent | Runs | ≈ min |
|---|---|---|
| A | unit → corpus (miss + hit) → faults (with cache hit) → M9b/M9bs | 89 |
| B | M9c-loc 11 → M9c-mix 11 | 70 |
| C | M9c-nav 11 (631d3938 main with `--skip-ejs`) → 448 part 1 | 80 |
| D | 448 part 2 | 80 |

- 448 parts: the 14 classes split by N5b seconds so Test Agents C and D end near 80 min; A.7 writes the
  exact class lists.
- Pass: ladder wrong 0 in every run; corpus = ejs with tripwire false positives 0; faults
  wrong/missed 0; M9c 33/33 runs done; 448 classes wrong 0.
- Retest: core JS fix → unit + corpus + faults + the failed class + 21cd2156 es6 + 631d3938 main;
  tripwire or B-search fix → full M9c.
- **Done (B):** 22/22 result files pass (52 runs, all at TEST_SHA `48dd744`): ladder wrong 0 in
  every run, corpus = ejs with tripwire false positives 0, faults wrong 0 and missed 0, M9c 33/33,
  448 classes wrong 0. Numbers in PROGRESS.md (N5b).

**C** — N6 canary workflow → N7 owner phone check (includes the phone margin).

### N6 — Canary workflow (computer)

- A workflow `own-solver-canary.yml`: fetch today's player (4 builds), run ejs, P, B, the vectors
  and `--quick` mutations; fail the job on any wrong value or a P/B miss.
- GitHub runs `schedule` only from the default branch. On the spike the workflow has
  `workflow_dispatch` and runs on pushes that touch the solver files. Copying a daily schedule to
  main is the owner's decision (not in this phase).
- **Done:** a manual run passes; the job summary names players and verdicts only. ✅ (push run
  38424779157 ✅; the owner's re-run, attempt 2: "Result: pass")
- **Plan adapted (N6 build):**
  - The computer canary (`own-solver-canary.mjs`) judges P and B alone (B as `--strategy B`:
    no hint) on every build. Verdict per strategy and kind, worst first: wrong (a value that
    differs from ejs's) > oracle (ejs failed too) > miss > pass. Any verdict other than pass
    fails the job: an oracle verdict means main's solver fails on the player as well.
  - "The vectors" = the corpus parity run against the recorded vectors, with own P and again with
    B alone (B alone = ejs on every file is a §4 criterion). The quick mutations run ejs, own P and
    own B (`--quick --strategy P,B`); any wrong value fails (`--judge all`).
  - Each check runs even when an earlier one failed. The scripts' stdout (names, counts and
    indexes) goes to the log; their stderr stays on the runner, because Node prints a source line
    under some V8 errors, so stderr can hold player code.
  - The summary is written by `scripts/own-solver-canary-summary.mjs` from the reports; its tests
    are in `canary.test.mjs` (already in the spike CI JS step).
- **Plan adapted (owner 2026-10-13):** GitHub's Run workflow needs the default branch, so the manual
  run = the owner's re-run of the push run. From now on, manual runs = labels on draft PR #2:
  run-canary (canary), build-optin-apk (opt-in APK with own_solver_next on).

### N7 — Owner live check and runbook

- Phone canary (extend `OwnSolverCanaryTest`): P, B, E and V verdicts on today's player.
- Live check with the opt-in APK (all flags on): YouTube rows equal main's on the parity URLs, no
  throttled rows, terminal cases stay terminal.
- Update the runbook (§6) for the ladder.
- **Hit targets (owner, 2026-10-13; replace N5b's 0.5 s):** P hit in the same process ≤ 2 s, P hit
  after a restart ≤ 8 s. A hit is measured from sending the job to its reply, without the player
  download and V, on the phone build (the build the app uses first); the hit after a restart
  includes the engine's cold start. The main build (the fallback) is recorded separately, with no
  target. B hit is a metric only, no target. (N5b A.1 computer numbers × 4: phone build 1.1 s /
  5.4 s, main build 1.9 s / 7.6 s.)
- **Option 3 again** (B's prepared program in memory, N5b A.1) only if, on the current YouTube
  player, P fails and B is really used, and B hit > 5 s.
- **Page cap:** measure how long the in-app browser stalls while a page job runs; re-tune the
  provisional page cap (35 s, N5b A.2) with the phone numbers (§5).
- **P (b) in total, with V (owner, N5b A.8):** besides the hit time, measure on the phone the
  whole lookup after a restart, from its start to the rows: the player download, the engine's
  cold start, the core run and the V request (a restart forgets Phase 1.1's V passes; with
  `ownSolverNext` the ledger file keeps them, so V runs only for new player text). With these
  numbers the owner decides on a timeout fallback to main and a V prewarm (not built, §5 "V
  wiring in the app").
- **Done:** the owner's results are recorded; the board shows N1–N7 with shas.

## 4. Pass criteria (Phase 1.2 done)

- **Corpus:** the ladder = ejs on 100 % of inputs; B alone = ejs on 100 % of files.
- **Change simulator:** **wrong = 0** on every class, with and without V. Survival targets
  (confirmed or changed after the N1 baseline):
  - own ladder, offline only: ≥ 80 % of the M1–M9 cases;
  - own ladder with simulated V: ≥ 95 % of the M1–M9 cases;
  - in every class where ejs is below 100 %, the own ladder survives more cases than ejs.
- **Faults** (the Phase 1.1 set + decoys): 0 wrong, 0 missed.
- **Speed on the phone:** the P path is unchanged; the first B run on a new player version ≤ 10 s
  (N5b A.1: computer max 8.2 s, ≈ 33 s at the ×4 margin; open for N7); a locator cache hit: P ≤ 2 s
  in the same process, ≤ 8 s after a restart, B a metric only (§3 N7; replaces 0.5 s).
- **Live:** as Phase 1.1 §4, with all flags on.
- **Flag off = main;** CI + `MasterMainMoreSheetTest` green.

Baseline (filled by N1; later columns by N3–N5):

| Class | ejs | own P | own B alone (N3) | own ladder (offline) | own ladder (+ simulated V) |
|---|---|---|---|---|---|
| M1 | 32/32 | 32/32 | 32/32 | — | — |
| M1w | 32/32 | 32/32 | 32/32 | — | — |
| M2 (member access part) | 32/32 | 32/32 | 32/32 | — | — |
| M3 | 0/32 (safe) | 0/32 (safe) | 32/32 | — | — |
| M4 | 32/32 | 32/32 | 32/32 | — | — |
| M5 | 24/32 (8 safe) | 32/32 | 32/32 | — | — |
| M6 | 0/32 (safe) | 32/32 | 32/32 | — | — |
| M7 | 11/32 (21 safe) | 31/32 (1 safe) | 32/32 | — | — |
| M8 | 32/32 | 32/32 | 32/32 | — | — |
| M8t | 0/32 (safe) | 32/32 | 32/32 | — | — |
| M9 | 0/32 (safe) | 32/32 | 32/32 | — | — |
| M10 | 32/32 | 32/32 | 32/32 | — | — |
| M11 | 32/32 | 32/32 | 32/32 | — | — |
| M11f | 32/32 | 32/32 | 32/32 | — | — |
| **all 448 cases** | **291 (65 %)** | **415 (93 %)** | **448 (100 %)** | — | — |

Baseline (N1 done, 2026-10-11): all 32 corpus files (8 players × main/tce/es6/phone) × 14 classes
= 448 judged cases; survived / files, the rest failed safely; **wrong = 0** in every case, 0
skipped (a second seed is tried only when the first mutation was a no-op or changed behaviour).
Reading:
- Renames, member access, helper split, decoys and a new prelude (M1, M1w, M2, M4, M10, M11,
  M11f) break neither solver.
- **A new wrapper (M3) breaks both** (safe failure): the case strategy B must fix.
- ejs breaks safely where own P does not: aliased call sites (M5, 8 files), a string table (M6),
  flattening (M7, 21 files), new encode helpers met first (M8t), browser-object reads (M9).
- own P's only other failure: M7 on 5203c085 es6 (n and sig, `no candidates`): a B target.
- Survival over M1–M9 (incl. M1w and M8t; 11 classes, 352 cases): ejs 195 (55 %), own P 319
  (91 %). The targets above stay (own ladder ≥ 80 % offline, ≥ 95 % with simulated V); M3 alone
  is 32 cases (9 %), so the 95 % target needs B to fix M3.

N3 (2026-10-11, `node scripts/verify-own-solver-mutations.mjs --strategy P,B`): **B alone** (P's
hint off) survives all 448 cases (M1–M9: 352/352), 0 skipped, **wrong = 0**; ejs and own P are
unchanged from the baseline. B fixes the new wrapper (M3, 32 files) and own P's M7 failure. The
ladder columns are N5's (P first, then B, each verified).

N4 (2026-10-11, strategy E on the computer: the same core in page mode in the harness's
page-frame DOM stub, `--page`): **M9 in page mode: own P 32/32, own B alone 32/32, wrong = 0**
(`node scripts/verify-own-solver-mutations.mjs --class M9 --strategy P,B --page`; ejs 0/32,
safe). Corpus in page mode: P and B alone 32/32 files, own = ejs on 260/260 inputs; faults in
page mode (320 runs each): P wrong 0, missed 0; B alone not run yet (pending). The worker mode is
unchanged: corpus P and B 32/32, 260/260; the full suite again 448 cases, ejs 291, own P 415,
own B alone 448, **wrong = 0** (as N3); faults (320 runs each) P and B alone wrong 0, missed 0. The worker passes M9 as well (the M9 row above), but not because its stubs answer
what M9 reads: M9's reads are never used, and a failing top-level statement is only a caught init
error (P's G1 program runs each top-level declaration and assignment in its own `try` and only
counts failures; B keeps everything registered before a late throw). So M9 does not show what E
adds. E is for a player whose n or sig needs more of a real page than the stubs give; a JS test
shows one (its n needs `document.createElement`: the worker answers sig and fails n, the page
answers both, with P and with B). N4b measures that case on the corpus (note "N4b").

N4b (2026-10-11, `node scripts/verify-own-solver-mutations.mjs --class M9b,M9bs --strategy P,B
--both`; all 32 files in one process; outside the 448 baseline), survived/files:

| Class | ejs | worker P | worker B alone | page P | page B alone |
|---|---|---|---|---|---|
| M9b (DOM-made value; its use throws without the DOM) | 0/32 (safe) | 0/32 (safe) | 0/32 (safe) | 32/32 | 10/32 (22 safe) |
| M9bs (silent use: another number, no error) | 0/32 (safe) | 0/32 (safe) | ❌ 0/32 (31 safe, **1 wrong**) | 32/32 | 13/32 (19 safe) |

- ❌ **Wrong = 1** (worker B, 631d3938 es6 M9bs): n `no candidates` (safe), but sig answered and
  0/4 were right. The statement that makes the DOM value fails in the worker (init errors 3:
  RangeError + TypeError); B keeps going, the value is then missing and sig silently changes, so
  SelfCheck and agreement cannot see it. The init-error rule proposed in
  `MASTER_KEY_PHASE1_2_CONTINUE_N4B.md` would catch it (init errors > 0 → E; page B has 0 init
  errors there, and page P and page B answer that sig right). Reproduced in a fresh run, also with
  the harness before the change below. ejs fails safely there (main is not affected); the app
  does not send B before N5. Not fixed: the owner decides the rule before N5 uses B.
- Safe failures: worker P `probe` (n and sig) in all 64 cases; worker B `no candidates` 48, step
  budget 12, error 3 (B's call-shape keys are static constants; M9b/M9bs make them DOM-dependent);
  page B mostly n `no candidates` (37 of its 41 safe cases; sig right there).
- Init errors (unmutated corpus): 0/32 files in worker P/B and page P/B; corpus 32/32 = ejs
  260/260 in all four. Faults: worker P and B: the 10 earlier cases wrong 0, missed 0; init-wrong-n
  132 and init-wrong-sig 128 wrong (❌ by construction until a rule keeps such answers unverified).
  Page P and page B (384 runs each): wrong 0, missed 0 (init-wrong is inert in page mode).
- Harness (test scripts only): the earlier hang (74edf1a3 phone M9bs in B) came from player
  promise jobs that default vm contexts run on Node's own queue (during the runner's settle(),
  outside the vm timeout). Contexts now use `microtaskMode: "afterEvaluate"` and keep the solve's
  reply before promise jobs run; when the jobs time out, the kept reply is used, as in the app's
  worker, which posts its reply before later promise jobs run (`{late promise jobs cut}`: 5 of 64
  cases, all worker B, safe; no harness timeouts).

N5a (2026-10-11/12, code `a78626f`; one rule in `OwnSolverLadderRule` and `ladder.mjs`, 18 shared
cases). Options: PB+V = worker P → worker B → E (P, else B) with V simulated; PB off = the same
without V; B+V and B off = E runs B only. survived/safe/wrong per class (M9c: lines = player ×
variant, 2 seeds each):

| Run | PB+V | PB off | B+V | B off | Wrong |
|---|---|---|---|---|---|
| Corpus `--ladder --dyncode` (32 files) | = ejs 260/260 | 260/260 | 260/260 | 260/260 | 0 |
| Faults `--faults --ladder` (384 runs) | — | — | — | — | 0 (missed 0) |
| M9b | 32/0/0 | 0/32/0 | 10/22/0 | 0/32/0 | 0 |
| M9bs | 32/0/0 | 0/32/0 | 13/19/0 | 0/32/0 | 0 |
| M9 | 32/0/0 | 0/32/0 | 32/0/0 | 0/32/0 | 0 |
| M9c-nav (31/32 lines) | 0/30/1 | 0/30/1 | 0/30/1 | 0/30/1 | ❌ 1 |
| M9c-loc | 32/0/0 | 32/0/0 | 32/0/0 | 32/0/0 | 0 |
| M9c-mix | 0/32/0 | 0/32/0 | 0/32/0 | 0/32/0 | 0 |

- ❌ M9c-nav 21cd2156 es6 (reproduced in a fresh run): worker B answers a wrong sig, n `no
  candidates`, init errors 0; the rule returns it in all four options (V checks E's signatures
  only). All five B wrong values in M9c have this shape (a sig while n has no candidates):
  21cd2156 es6 (worker, E), 9fcf08e8 tce M9c-loc (E, init errors 2), e7573094 main M9c-mix
  (worker with init errors 1, E); only the first is returned. Fix: §3 N5 "Plan adapted (N5a
  results)".
- ❗ ejs wrong 1 (74edf1a3 es6 M9c-nav): likely `cookieEnabled` missing from the WebView worker's
  `WorkerNavigator`; main is affected too and is not changed.
- ⚠️ 631d3938 main M9c-nav: one solve exhausts the Node heap (exit 134, 61 s, 2.5 GB), also
  alone; its other variants are safe. One process for all of M9c stopped at 72/96 and M9c-nav
  alone at 9/32, so M9c ran one process per player (631d3938: per variant). In the app this is a
  renderer memory risk (breaker, N5b).
- Cost of the rule: M9 throws once at init in the worker (init errors 1, all 32 files), so the
  worker's right answers are unused; E answers, and E's sig needs V (PB off, B off: n only).
- Corpus init errors 0/32 in all four steps; player `eval` reads and `Function` calls 0.

## 5. Risks

- **False positives in B:** a function that acts like n but is not. Mitigated by the classifier
  rules, agreement, the M10 decoys and V. Without a V `pass`, disagreement fails.
- **Silent environment values (N4b, N5a):** a value made with browser objects the worker lacks: its
  statement fails (an init error), B keeps going and the value silently becomes another number.
  B in the worker then gave a wrong sig on 1 of 32 M9bs files (631d3938 es6) that SelfCheck and
  agreement accept (init errors 3); P failed safely and page mode was right. N5a's rule (init
  errors > 0 → no worker answer; E decides) catches it (§4 note "N5a"). Left: a value the stubs
  give differently with no error (M9c: 1 ladder wrong value in M9c-nav, §4 note "N5a"), fixed in
  N5b by V on every signature and the environment tripwire. B's call-shape keys are
  static constants, so DOM-dependent keys give B `no candidates` (safe). The harness hang from
  player promise jobs is fixed in the harness (§4 note "N4b").
- **E hang (residual after N5b):** the planned page-P step budget counts steps; it cannot stop one
  long regex backtrack or a memory crash inside a step. E runs on the app's single WebView
  renderer main thread, so an E hang holds every WebView engine (main's ejs solver and the
  in-app browser too) until it ends or the process restarts. Main's browser WebViews
  (SecureBrowserWebViewClient, BrowserScreen, PopupWindowCatcher, SiteCheckDialog) do not handle
  `onRenderProcessGone`: if the renderer dies, the app process dies with it.
- **Page job stalls the in-app browser (UX, N5b A.2):** a page step runs on the app's shared
  renderer main thread, so one page job can hold the in-app browser up to the page cap (35 s,
  provisional). N7 measures the stall on the phone and the cap is re-tuned with those numbers.
- **Short-deadline `budget` memory (N5b A.2):** after two short-deadline `budget`s in a row for a
  `player|step`, that step is skipped until the app restarts (or the entry is evicted, ≤ 64), even
  if it would finish with its full cap. The locator cache makes this rarer (a P hit leaves B its
  full cap).
- **Deadline limits (N5b A.2):** the deadline stops player code in the step counters (every 4096
  steps) and between phases; one long native call (a regex backtrack, a huge string or array
  operation), the parse and P's prepare are not cut. Such a job still ends at the Kotlin job
  timeout (45 s → breaker). Each deadline counts from the job's arrival, so a lookup can run past
  its 60 s cap by its last step's ready wait (bounded by the time that was left, ≤ 20 s) plus the
  reply. If N7 shows long ready waits on the phone, send the deadline when the engine is ready
  (an engine interface change). The budget memory is per process: after a restart each slow step
  costs one more full-cap run per player version.
- **Work the step budget cannot see (N5b A.6):** P's and B's step counters count loop bodies only.
  A deep recursion ends at the JS engine's stack limit (a `RangeError`: an init error or a failed
  attempt, safe). A wide recursion (many calls, little depth, no loop) and one long regex
  backtrack are not counted and not cut by the deadline: in the worker the Kotlin job timeout
  (45 s) ends the job and trips the breaker; in the page (E) nothing stops them until they end,
  and the app's shared renderer waits ("E hang" above). Fix → N8 (E in its own process, killed
  on a hang). Counting calls as steps would slow every call of the player's code, need new
  budgets and still miss a regex.
- **Locator cache (N5b A.4):** a hit narrows the work, so some checks are weaker than in a full
  run. P hit: all candidates must agree on one probe only (a full run: on every input), so a
  candidate that differs only on some inputs is not seen; SelfCheck, the probe hash and the
  tripwire (all candidates) still run, and a signature still needs V. B hit: the screen and the
  competition between groups are skipped; an entry naming a group that is consistent with
  itself but not the winner would pass for n (n has no V, "V ambiguity for n" below), while a
  signature needs its own V pass (the ledger key carries the part's hash). Entries come only from
  clean runs the ladder chose, keyed by the player text's SHA-256, in app-private files: a forged
  entry needs the app's own storage. Accepted for the spike (release gate below). A hit from the
  player text still parses and instantiates the player: it saves the search, not the load.
- **Caps (N5b A.2, chosen from A.1's numbers):** job 40 s = the Kotlin job timeout 45 s − 5 s grace
  (the deadline is checked every 4096 steps, then the reply and the bridge); lookup 60 s =
  `P_INIT_MS` 20 s + the job cap, so B keeps its full cap after P; page 35 s, tighter because a
  page step holds the shared renderer (provisional). Phone margin ×4 on the computer's numbers: B
  fits the corpus up to ×4.9, page B up to ×4.6; on the largest players all four steps (≤ 76 s at
  ×4) pass 60 s, so page steps may be cut and run in the next lookup. If N7's phone factor is
  above ×4.9, B on the largest players ends `budget` (no wrong value, fewer answers): re-tune
  with N7's numbers.
- **B prepared program (option 3, N5b A.1):** not built; it waits for N7 and is built only if, at
  N7, P fails on the current player, B is really used and a B hit takes > 5 s. Option 4 (a
  prepared program on disk) is refused: the locator file is data only, code from disk never runs.
- **M9c-nav gap (N5b A.6):** N5a's M9c-nav run had a ladder wrong value (21cd2156 es6) and no
  result for 631d3938 main (out of heap). N5b's fixes were checked on those cases only (tripwire
  + V: 21cd2156 es6 safe; step budget: 631d3938 all four variants exit 0, ladder wrong 0), and
  N5b's test cycle ran 26 of the 33 M9c runs (loc 11, mix 11, nav only 631d3938's 4): M9c-nav on
  the other 7 players has not run with N5b's ladder. The next test cycle runs all 33 (3 classes ×
  11 processes; Test Agents B and C).
- **V wiring in the app (⚠️ found in N5b A.6; ✅ fixed in N5b A.8):** the app gave the own
  runner to main's `YouTubeExtractor` (extractor-sites, `SiteAdapterModule.provideSiteExtractors`,
  Phase 1.1 S5: main's adapter answers YouTube pages before Master is asked), but the V gate
  (`SignatureGate`) sat only in Master's `MasterYouTubeModule`, which `BrowserMasterFallback`
  builds without a runner. So the app used the own solver's signatures without V: with
  `ownSolverNext` the ladder's (no V pass recorded, no signature part cached), and (found in A.8)
  without it Phase 1.1's too. Flag off = main was not affected. A.8: `YouTubeSignatureGuard` puts
  the gate around main's reader for both flags (tests first, PROGRESS.md N5b A.8); Phase 1.1's
  signatures now need one V pass per player version and process. **Not built (owner, A.8):** a
  timeout fallback to main and a V prewarm; both are decided after N7's numbers (§3 N7).
- **Main candidate list (main untouched; the main agent assigns the numbers):** (1) handle
  `onRenderProcessGone` in main's browser WebViews (below); (2) main's ejs solver runs out of heap
  on 631d3938 main M9c-nav (exit 134 at ~37 s, N5b step 0): a changed player can exhaust its
  renderer, and without (1) the app dies with it; (3) main's ejs gave a wrong value on M9c-nav
  74edf1a3 es6 (N5a; likely `navigator.cookieEnabled` in the WebView worker): an environment read
  in a future player could break main's playback silently.
- **Release gate:** `ownSolverNext` is not enabled in a public release before N8 is done; only the
  owner's test APKs use it.
- **N8 candidate (⭐⭐⭐⭐):** E in its own process (`android:process=":solver"` +
  `WebView.setDataDirectorySuffix`), the job passed through a file (Binder's 1 MB limit), no Hilt
  set-up in that process, the process killed on a hang (the app survives). Cost: one more process
  and renderer while solving (RAM) and its cold start per lookup.
- **Main candidate task (⭐⭐; the main agent assigns the number):** handle `onRenderProcessGone` in
  main's browser WebViews, so a renderer crash closes the page instead of the app.
- **V ambiguity for n:** a wrong n may be slow instead of 403. Measured first (N2); `unknown` never
  counts as `pass`.
- **Cost:** B is slower and uses more CPU on the first lookup of a new player version (about every
  1–2 weeks). The locator cache makes later lookups fast.
- **Page environment (E):** more surface than a worker. Mitigated by §1.4, the own route table
  and its tests, the frame's CSP and the token handshake. Left after N4 (§3 N4 "Plan adapted"):
  WebRTC through a frame the player makes itself (no CSP covers it; holding the renderer's
  connections was not done); E runs on the app's shared renderer main thread and P has no step
  bound there (N5: E = B only, or a bound); a hidden WebView may delay the job's start; the
  token only stops posts without it; not run on a phone yet (N7 canary).
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