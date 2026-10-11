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
| N3 | Strategy B: register, probe, classify (core + harness) | N1 | High | TODO |
| N4 | Strategy E: isolated page environment | N3 | Medium–High | TODO |
| N5 | Ladder, locator cache, flag wiring | N2, N3 | Medium | TODO |
| N6 | Canary workflow (computer) | N1, N3 | Low–Medium | TODO |
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
| M1 | 32/32 | 32/32 | — | — |
| M1w | 32/32 | 32/32 | — | — |
| M2 (member access part) | 32/32 | 32/32 | — | — |
| M3 | 0/32 (safe) | 0/32 (safe) | — | — |
| M4 | 32/32 | 32/32 | — | — |
| M5 | 24/32 (8 safe) | 32/32 | — | — |
| M6 | 0/32 (safe) | 32/32 | — | — |
| M7 | 11/32 (21 safe) | 31/32 (1 safe) | — | — |
| M8 | 32/32 | 32/32 | — | — |
| M8t | 0/32 (safe) | 32/32 | — | — |
| M9 | 0/32 (safe) | 32/32 | — | — |
| M10 | 32/32 | 32/32 | — | — |
| M11 | 32/32 | 32/32 | — | — |
| M11f | 32/32 | 32/32 | — | — |
| **all 448 cases** | **291 (65 %)** | **415 (93 %)** | — | — |

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