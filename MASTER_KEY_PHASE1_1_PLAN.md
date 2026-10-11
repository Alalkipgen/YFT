# Master Key — Phase 1.1 Plan: own YouTube n/sig solver (option A, built general from the start)

Status: **plan + prompt.** No code is changed by this document.
Date: 2026-10-10. Branch: `spike/master-extractor-backup` only.
Reads: `MASTER_KEY_PHASE1_PLAN.md` (R6), `MASTER_KEY_NOTES.md` §1.3 and §8.

Phase 1.1 replaces the bundled third-party ejs **core** with YFT's own solver core inside Master.

- It is built from the start with the **4 general methods** (one for each class of past break) plus **self-verification**.
- It keeps a general JS parser library (meriyah, ISC; a parser, not an extractor) and the existing sandbox.
- It is option A. Option B (own parser) is out of scope: it adds work without adding durability (§7).

## မြန်မာ အနှစ်ချုပ်

- ကိုယ်ပိုင် n/sig solver (option A) ကို ရေးမယ်။ ejs ပျက်ခဲ့တဲ့ ပြဿနာ အမျိုးအစား ၄ မျိုးကို အစကတည်းက ယေဘုယျနည်းနဲ့ ဖြေရှင်းထားမယ်:
  - Player ကို အပြည့် run ပြီး multiTry
  - Code ပုံစံကွဲတွေကို normalize
  - Encoding စစ်ချက်
  - Browser နဲ့တူတဲ့ sandbox ပတ်ဝန်းကျင်
- ရလာတဲ့ အဖြေကို ကိုယ့်ဘာသာ ပြန်စစ်မယ်။ မသေချာရင် fail လို့ သတ်မှတ်မယ်၊ မှန်းပြီး မသုံးဘူး။
- main ရဲ့ ejs ကို မထိဘူး။ Flag ပိတ်ထားရင် app က main နဲ့ အတူတူပါပဲ။ ejs နဲ့ parity test အောင်မှ ယုံမယ်။
- visionOS ကို ဦးစားပေးတဲ့ အစဉ် (R6) မပြောင်းပါ။ ဒါကြောင့် solver ပျက်ရင် quality နည်းသွားရုံပဲ ဖြစ်ပါမယ်။

## 0. Why (evidence)

- **ejs** (`yt-dlp/ejs`, Unlicense; main bundles 0.8.0):
  - The core is about 606 TypeScript lines (`solvers.ts`, `nsig.ts`, `setup.ts`, `main.ts`, `utils.ts`).
  - It parses with meriyah and generates code with astring. meriyah stayed at 6.1.4 throughout, and **no** fix touched the parser.
- **The 4 logic fixes** (Feb–Mar 2026), each one class of break:

| ejs fix | Class of break | General method (this plan) |
|---|---|---|
| #53 "Solve new player variants" | YouTube ships several player builds | **G1** run the player's whole top-level code in the sandbox and try every candidate URL-transform call (multiTry); do not extract one internal function. ejs dropped ~938 lines with this change. |
| #47 "Fix sig solving for tce and es6 player variants" | Different code generation (`var` vs `let`/`const`, `function` vs arrow, sequence expressions) | **G2** structure patterns with alternatives, plus AST normalization before matching |
| #54 "Fix sig value encoding" | How the result is applied to the URL (`encodeURIComponent`) | **G3** encoding-aware application with edge-case vectors (`%`, `/`, `=`, control characters) |
| #56 "Expose `window` global as `self`" | The sandbox environment differs from a browser | **G4** a full browser-like global environment (`window` = `self` = `globalThis`, `location`, `navigator`, `document` stub) |

- **Result:** after #53–#56, ejs needed no solver fix from 2026-03-17 to at least 2026-09-27 (more than 6 months). Before, it needed 4 fixes in about 5 months.
- **Estimate:** about 1–3 breaks a year instead of 2–6. Never 0.

## 1. Guardrails

1. **Flag off = main.** A new flag `-Pyft.ownSolver` (default `false`) and the existing `-Pyft.masterCapture`. With both off, the app is main. CI and `MasterMainMoreSheetTest` stay green.
2. **Copy, never move.**
   - main's `YouTubePlayerScriptRunner`, `EjsSolverProtocol`, `WebViewSolverEngine` and `assets/youtube-solver/` stay unchanged.
   - The own solver is new code under Master. It plugs into the existing `PlayerScriptRunner` interface through a new runner.
3. **No third-party extractor.**
   - Allowed: meriyah (ISC, parser) and astring (MIT, code generator) as general libraries, with `THIRD_PARTY_NOTICES` entries.
   - ejs (Unlicense) is an **idea and structure reference only**: no file copied.
4. **Sandbox only.** The player runs with no network, no cookies and no storage, in the existing WebView worker model (`SolverEngine`). It never navigates the host page.
5. **No bypass, no fake rows.**
   - A stream whose value cannot be verified is dropped.
   - `BOT_CHECK`, `LOGIN_REQUIRED` and DRM stay terminal.
   - SABR-only formats are never rows.
6. **Spike only.** No main merge, tag or release.

## 2. Architecture

```
YouTube module (R6) ── needs n/sig ──> OwnPlayerScriptRunner : PlayerScriptRunner
                                         │ fetch base.js (same as main's runner: phone variant, Referer)
                                         │ cache: playerId -> prepared program (memory)
                                         ▼
                                       OwnSolverCore (JS, bundled asset)
                                         1 parse (meriyah)          G2 normalize AST
                                         2 unwrap player (G1)       keep declarations/assignments only
                                         3 find candidates (G2)     URL-transform call sites, n & sig
                                         4 build multiTry (G1)      one generator per candidate
                                         5 environment (G4)         window/self/globalThis/location/...
                                         ▼
                                       SolverEngine (existing WebView worker; no network/cookies)
                                         ▼
                                       SelfCheck (G3 + verification)
                                         - all candidates agree, else FAIL
                                         - vectors pass (encoding edge cases), else FAIL
                                         - n output differs from input & is URL-safe
                                         - optional: probe 1 byte; throttled/403 -> FAIL
                                         ▼
                                       resolved values  |  drop stream (never guess)
```

Order inside the YouTube module (unchanged from R6):

1. VISIONOS direct URLs (no solver).
2. Own solver (when `ownSolver` is on).
3. main's ejs path stays the app's normal path with the flag off.

## 3. Tasks

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| S1 | Harness: vectors, parity runner against ejs, player corpus | R1 | Medium | Done (`491dc0e`) |
| S2 | Core: parse + G1 unwrap + G2 normalize/candidates | S1 | Medium–High | Done (`f0d7be4`) |
| S3 | G4 environment + multiTry runner in the existing sandbox | S2 | Medium | Done (`ace7276`) |
| S4 | G3 encoding + SelfCheck (agreement, vectors, probe) | S3 | Medium | Done |
| S5 | `OwnPlayerScriptRunner` wiring (flag), cache, failure mapping | S4, R6 | Medium | TODO |
| S6 | Canary + update runbook | S5 | Low | TODO |

### S1 — Harness and corpus

- **Vectors:** start from `scripts/youtube-solver-vectors.json`. It holds 7 players (`74edf1a3`, `901741ab`, `e7573094`, `9fcf08e8`, `21cd2156`, `76ad2fe8`, `631d3938`) from ejs tests (Unlicense) and is not shipped.
- **Corpus:** add more players, covering main, tce, es6 and phone variants. Store player IDs and hashes only, and download them at test time. Never commit YouTube's player text.
- **Parity runner:** a Node script (beside `scripts/verify-youtube-solver.mjs`) that runs the own core and ejs on the same player and inputs, and compares the outputs.
- **Done:** the harness runs; ejs passes all vectors; the report shows per-player pass/fail.

### S2 — Core: parse, unwrap, normalize, candidates

- **Parse:** meriyah gives the ESTree AST.
- **G1 unwrap:**
  - Accept the known top-level wrapper shapes (IIFE / `.call(this)` / `var window = this` prelude).
  - Keep declarations and assignments; drop side-effect statements.
  - An unknown wrapper → `FAIL(unexpected structure)`, never a guess.
- **G2 normalize** (only for matching; the program is not rewritten):
  - Fold `let`/`const` into `var`; treat arrow and function forms alike.
  - Flatten sequence expressions; accept both computed and dotted member access.
- **G2 candidates:** structure patterns with alternatives for the URL-transform call sites of `n` and `sig`, for example `(url, "n"|"s", value)` shaped calls and the helper object they use. Collect **all** matches, not the first one.
- **Done:** on the corpus, every player yields ≥ 1 candidate for n and for sig; an unknown wrapper fails cleanly.

### S3 — Environment and multiTry

- **G4:**
  - A setup prelude defines `window = self = globalThis`, `location = new URL("https://www.youtube.com/watch?v=…")`, `navigator` (`userAgent` and `language` as the phone sends them) and a minimal `document` stub.
  - Each is an own property inside the worker, so nothing reaches the host page (main's worker already defines `location` this way).
- **G1 multiTry:**
  - Generate one arrow-function generator for each candidate.
  - Run them all on a test URL (`https://www.youtube.com/watch?v=yft-test`) and read the transformed value from the returned URL's query.
  - Collect the results and any errors.
- **Done:** the corpus produces values for every vector input; error messages name only candidate indexes, never values.

### S4 — G3 encoding and SelfCheck

- **G3:** encode the sig input with `encodeURIComponent` before application; decode once on read. Add edge-case vectors with `%`, `/`, `=`, `+` and control characters.
- **SelfCheck** (all must pass, else `FAIL` and the stream is dropped):
  1. **Agreement:** every successful candidate returns the same value; disagreement → FAIL.
  2. **Vectors:** for known players, outputs equal the expected vectors.
  3. **Shape:** the n output is non-empty, differs from the input and is URL-safe; the sig output has a plausible length.
  4. **Probe (optional, live only):** a one-byte check of one transformed URL; 403 or a throttle signal → FAIL.
- **Done:** 100 % agreement with ejs on the corpus; injected faults (a wrong candidate, a broken encoding, a missing global) are detected as FAIL, never as wrong output.

### S5 — Runner wiring

- **`OwnPlayerScriptRunner`** implements the existing `PlayerScriptRunner`:
  - It fetches the player exactly as main's runner does (phone `base.js` variant, page as Referer).
  - It caches the prepared program per player ID, in memory.
  - It maps failures to `PlayerScriptResult.Failed(PLAYER_SCRIPT_REQUIRED)`.
- **Selection:** the own runner is used only when both `ownSolver` and `masterCapture` are on; otherwise main's ejs runner is used.
- **Done:** unit tests (fake engine) for flag selection, cache hit, failure mapping and the dropped-stream path; flag-off gate green.

### S6 — Canary and runbook

- **Canary** (owner-run, phone or emulator, opt-in): fetch today's player ID, run own vs ejs, and report pass/fail per kind (n, sig).
- **Runbook:**
  - When the canary fails, add the new player to the corpus.
  - Find which class failed (G1–G4 or new), fix the core, and re-run parity.
  - Ship a new APK. No remote code.
- **Done:** the canary report is written; the runbook is saved in this file's §6.

## 4. Pass criteria (Phase 1.1 done)

- The own core equals ejs on 100 % of the corpus for n and sig. SelfCheck catches every injected fault.
- Live (owner phone): with the flags on, YouTube rows equal main's on the parity URLs (`MASTER_KEY_PLAN.md` §4.3). No throttled rows. Bot-check, age and private cases stay terminal.
- Flag off: app == main; CI + `MasterMainMoreSheetTest` green.
- The ejs core file is no longer used on the own-solver path (it stays in main for the flag-off path).

## 5. Risks

- **Durability:** still about 1–3 breaks a year (estimate). Mitigations: VISIONOS first, the canary, fast fixes.
- **New break classes:** a wrapper change, anti-tamper or environment checks, or SABR-only delivery. SABR-only would make the solver irrelevant; YouTube would then rely on VISIONOS or later the experimental MSE path.
- **Parser:** a new JS syntax could need a meriyah update. This has not happened in ejs's history.
- **Upkeep owner:** someone must act on canary failures.

## 6. Runbook (when the own solver breaks)

1. The canary fails → note the player ID and the failing kind (n or sig).
2. Add the player to the corpus and reproduce it in the parity runner.
3. Classify the break:
   - G1: wrapper or candidates.
   - G2: a new code shape.
   - G3: encoding.
   - G4: a missing global.
   - New class: add a new general method, not a one-off patch.
4. Fix the core; re-run all corpus players (no regressions); re-run the injected-fault tests.
5. Checkpoint-push on the spike; ship a new APK. Until then VISIONOS keeps working and solver streams are dropped (fewer qualities, not failure).

## 7. Out of scope

- **Option B (own JS parser):** more work, and no more durability (ejs never changed its parser).
- **Copying ejs files:** ideas and structure only.
- **SABR client, PoToken changes:** PoToken is already YFT's own code.
- **Any bypass.**
