# Master Key — Phase 1.2 agent prompt: next-level YouTube n/sig solver (N1–N7)

**ရည်ရွယ်ချက်:** `MASTER_KEY_PHASE1_2_PLAN.md` ထဲက N1–N7 ကို agent တစ်ယောက်က တစ်ဆင့်ချင်း လုပ်ဖို့
prompt ပါ။ Phase 1.1 ရဲ့ ကိုယ်ပိုင် solver ကို အခြေခံပြီး YouTube က code ရေးပုံ ပြောင်းလည်း ဆက် resolve
လုပ်နိုင်အောင် တစ်ဆင့်မြှင့်မှာပါ: အလုပ်လုပ်ပုံနဲ့ ရှာ (B)၊ YouTube ကိုယ်တိုင်နဲ့ စစ် (V)၊ browser page
အစစ်ထဲ run (E)၊ ပြီးတော့ "YouTube ပြောင်းလဲမှု အတု" တွေနဲ့ ejs ထက် သာကြောင်း ဂဏန်းနဲ့ သက်သေပြ။

**သုံးနည်း:**
- Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။
- Phase 1.1 (S1–S6) ပြီးမှ စပါ။ N1 ကို အရင်လုပ်ပါ (baseline တိုင်းဖို့)။ N2 ကို N1 နဲ့ ပြိုင်တူ လုပ်လို့ရပါတယ်။
- N2 နဲ့ N7 မှာ owner က ဖုန်းနဲ့ စစ်ရမယ့် အပိုင်း ပါပါတယ်။
- Task တစ်ခု ပြီးတိုင်း push လုပ်ပြီး မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်လုပ်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
Plan: MASTER_KEY_PHASE1_2_PLAN.md (read it first; context: MASTER_KEY_PHASE1_1_PLAN.md,
      MASTER_KEY_NOTES.md sections 1.3, 3 and 4, PROGRESS.md "Phase 1.1" sections)
ALLOW_PUSH: true          (checkpoint pushes to the spike branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
START_AT: N1              (owner may change)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the report.

GOAL
Make the own YouTube n/sig solver keep working when YouTube changes the SHAPE of its player
code, without an APK update, and prove it with numbers:
  B  behaviour discovery: register every function the player creates, probe it with test
     inputs, keep the ones that act like n or like sig (not found by how the code is written)
  V  live check: ask YouTube (one small Range request) whether a solved URL is accepted
  E  isolated page environment (real DOM) when the worker environment is not enough
  ladder: locator cache -> P (the Phase 1.1 pattern core) -> B -> E; every answer verified
  change simulator: behaviour-keeping mutations of real players (M1-M10) measure ejs vs own
NEVER A WRONG VALUE: an answer needs SelfCheck and, when found by B, agreement or a V pass;
otherwise the kind fails and its streams are dropped. Never guess.

HARD RULES (never break)
1. Flag off = main. New flag -Pyft.ownSolverNext (default false) acts only when -Pyft.ownSolver
   and -Pyft.masterCapture are on too. With it off the own solver is exactly Phase 1.1. CI and
   MasterMainMoreSheetTest (flag-off case) stay green after every task.
2. Copy, never move. main's YouTubePlayerScriptRunner, EjsSolverProtocol, WebViewSolverEngine and
   app/src/main/assets/youtube-solver/ stay unchanged; Phase 1.1 behaviour stays unchanged with
   ownSolverNext off.
3. No third-party extractor on the own-solver path. meriyah (ISC) stays the parser. astring
   (MIT) only at test time, from the copy inside main's yt.solver.lib.min.js. yt-dlp/ejs is an
   idea reference only: copy no file. No own JS parser.
4. Sandboxes only. Player code runs only in the app's worker/page sandboxes and, on the
   computer, inside the harness's bare V8 context (scripts/own-solver/harness.mjs: no require,
   process or network). Do not write ad-hoc scripts that run player code anywhere else.
5. Page environment (E): a separate WebView with NO addJavascriptInterface (Android exposes it to
   every frame); replies through androidx.webkit addWebMessageListener limited to the app's own
   origin (feature-checked; without the feature E is skipped); the player runs in a
   sandbox="allow-scripts" iframe; every request the app does not serve is refused; CSP
   connect-src 'none'; navigation and window.open refused.
6. Live check (V): only during a lookup the user started; only on the stream host YouTube
   returned; the same HTTP client and headers as a download; no cookies; a Range read of at most
   64 KiB; at most 4 checks per player version per day; never on bot-check, login, age, private
   or DRM states. "unknown" is never "pass". Only the owner-run measurement test may read up to
   1 MiB.
7. No bypass (bot check, login, age gate, DRM); those stay terminal. SABR-only formats are never
   rows.
8. Never commit YouTube's player text or mutated players (build/ only; store IDs and hashes).
   Never log solver inputs, outputs or stream URLs (session secrets); log only keys, indexes,
   classes and counts.
9. No code and no patterns are ever loaded from a remote source. The locator cache is data the
   app makes itself.

TASKS (details, files and done criteria: MASTER_KEY_PHASE1_2_PLAN.md section 3)
N1  Change simulator: scripts/verify-own-solver-mutations.mjs (M1-M10, truth hook, report
    build/own-solver/mutation-report.json, --quick for CI). Baseline for ejs and own P written
    into plan section 4. Own P must give wrong = 0 on every class.
N2  Live check V: StreamCheck in extractor-master (pass/fail/unknown, control, budget, cache;
    fake-HTTP unit tests) + opt-in phone test StreamCheckMeasureTest (owner runs it; the result
    sets the n rule in plan section 2).
N3  Strategy B in own.solver.core.js (register by AST text splicing, step-counted loops, probe
    under budgets, classify, decide); harness --strategy B; B alone = ejs on 32/32 corpus files;
    clearly better than P on the mutation suite with wrong = 0; JS tests per classifier rule.
N4  Strategy E: WebViewPageSolverEngine + page-env.html with a sandboxed iframe (rule 5); used
    only after an environment error; route/protocol unit tests; M9 on the computer with a DOM
    stub.
N5  Ladder in OwnPlayerScriptRunner (cache -> P -> B -> E, each verified), locator cache
    (memory + files/own-solver/locators.json, keyed by player ID + script hash), BuildConfig
    OWN_SOLVER_NEXT_ENABLED, opt-in workflow input own_solver_next (default false until N7);
    fake-engine unit tests.
N6  Workflow own-solver-canary.yml (workflow_dispatch + pushes touching the solver files):
    today's player, ejs, P, B, vectors, --quick mutations; fail on any wrong value or a P/B
    miss. schedule runs only from the default branch: leave the daily schedule to the owner.
N7  Phone canary with P, B, E and V verdicts (extend OwnSolverCanaryTest); the owner's live
    check with all flags on; runbook (plan section 6) updated.

PER TASK
- Before coding: read the task in the plan; list the files you will touch.
- Tests first where possible (committed fixtures and fake players; no live calls in unit tests).
- Build and run the CI test set locally; keep the flag-off gate green. New JS test files must be
  added to the JS step of .github/workflows/master-optin-debug-apk.yml.
- Always re-run: node scripts/verify-own-solver.mjs (corpus parity), --faults, the mutation suite
  (from N1 on) and the JS tests.
- Checkpoint-push to spike/master-extractor-backup with a clear message (for example
  "feat(master): N3 behaviour discovery (strategy B)"). Docs-only pushes start no CI; the newest
  code commit carries the CI result.
- Update the status board in MASTER_KEY_PHASE1_2_PLAN.md (TODO -> Done, with the commit sha) and
  add a PROGRESS.md section; N1 fills the plan's section 4 baseline table, N3-N5 its other columns.
- Report to the owner in short Burmese with ✅/❌: what changed; corpus parity; mutation survival
  per class (ejs / own P / ladder offline / ladder + simulated V); the wrong count; faults; tests;
  timings; commit sha and CI run; risks. Then STOP and wait for "Next".

STOP AND ASK THE OWNER WHEN
- Any mutation class, fault or corpus file gives a WRONG value (not only a safe failure) and the
  cause is not clear.
- The N1 baseline shows that the plan's section 4 targets are unrealistic (propose new targets
  with numbers).
- V would need cookies, another host, more than its budget, or anything that looks like a bypass.
- E would need a JavascriptInterface in a page that runs player code, or any network access.
- A change would alter main's behaviour, or Phase 1.1 behaviour with ownSolverNext off.
- A task would need copying ejs files, a GPL/AGPL source, an own JS parser or remote code.
- A push fails (a local commit is not a handoff).
```