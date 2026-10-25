# Master Key — Phase 1.2 continuation prompt (after N3: start N4)

**ရည်ရွယ်ချက်:** N3 (Strategy B) ပြီးပါပြီ။ Agent အသစ်က ဒီ prompt နဲ့ N4 (Strategy E: သီးခြား page
environment) ကို စပါ။
- Agent chat အသစ်ထဲ အောက်က code block ကို paste လုပ်ပါ။ N4 မှာ Reasoning Level အမြင့်ဆုံး သုံးပါ။
- Task ပြီးတိုင်း push + မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
FIRST read, in this order:
  1. MASTER_KEY_PHASE1_2_PROMPT.md    (goal, HARD RULES, T1-T4, per-task routine, stop rules)
  2. MASTER_KEY_PHASE1_2_PLAN.md      (section 1 rule 4 "Page environment rules (E)", section 2,
                                       section 3 N4, section 4)
  3. PROGRESS.md top section "Phase 1.2 — N3 ..."
  4. this file
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
START_AT: N4 (strategy E), then stop and report; N5 only after the owner says "Next".

OWNER: reports in Burmese, short, with ✅/❌. The owner has no computer, mobile data only (no Wi-Fi).

STATE (verify against the repo; the repo wins)
- Phase 1.1 S1-S6 done; N1 done (765e91f); N2 done (9e15101; the n rule stays PENDING, plan
  section 2); N3 done (commit 52e1945, CI run 38164686967 green).
- Strategy B lives in extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js
  (B block before `function run(prepared`; exported prepareB/runB/progress + internals). A request
  reaches it only with strategy "B"; the app never sends that before N5, so P and the app are
  unchanged. `hint: false` switches P's hint off ("B alone").
- B alone: 32/32 corpus files = ejs (260/260 inputs), wrong 0; faults 0 wrong / 0 missed; the
  mutation suite per class (ejs | own P | own B): plan section 4. How B works and its budgets:
  plan section 2 "Plan adapted (N3)" and section 3 N3.
- Harness commands: `node scripts/verify-own-solver.mjs` (P; restores the player cache in
  build/own-solver/players, never committed), `--strategy B` (B alone), `--faults`, `--verbose`;
  `node scripts/verify-own-solver-mutations.mjs --strategy P,B` (448 cases, about 1 hour on the
  2-CPU sandbox: start with nohup and poll).
- The CI JS step (.github/workflows/master-optin-debug-apk.yml) runs the own-solver tests
  including scripts/own-solver/tests/core-strategy-b.test.mjs (140 tests locally).

N4 STARTING POINTS
- The own worker engine exists (Phase 1.1 S5): WebViewOwnSolverEngine.kt, OwnSolverPageRoutes.kt
  and assets own-solver.html / own-solver-page.js / own-solver-worker.js. E is a separate engine
  (plan: WebViewPageSolverEngine + page-env.html with a sandbox="allow-scripts" iframe, no
  addJavascriptInterface, replies through addWebMessageListener limited to the app origin).
- E runs only after an environment error class (for example ReferenceError on a missing browser
  object, mutation class M9). In the computer harness own P and B already pass M9 because the
  core's G4 environment (installEnvironment) stubs navigator, location and document; N4's
  harness part is "M9 with a DOM stub".
- Copy, never move: main's WebViewSolverEngine, YouTubePlayerScriptRunner, EjsSolverProtocol and
  the app's youtube-solver assets stay unchanged; flag off = main.

RULES LEARNED (keep them)
- Player code runs only through the repo's harness (verify-own-solver*.mjs, harness.mjs, canary,
  mutation runner) and static parsing. No ad-hoc execution of YouTube player code. Do NOT retry
  adding the TVHTML5 client to the stream-check tool (blocked twice by the safety review).
- Never commit player text; never log solver inputs/outputs/stream URLs (counts, lengths and class
  names only).
- Keep each command short (<= 25 s); start long runs with nohup and poll; never combine `pkill -f`
  with other commands in one shell (it can kill that shell).
- B's probes can overflow the stack inside player code; Node then prints "Exception in
  PromiseRejectCallback" with one player line to the local log. Harmless (caught; local only, not
  in CI, CI has no player files); never paste such lines into commits or reports.
- Long harness runs must call `settle()` (scripts/own-solver/harness.mjs) after every solve:
  Node keeps unhandled player rejections, and with them whole player contexts, until the event
  loop turns (without it the B runs hit the 2 GB heap limit). One B run peaks at ~1.3 GB heap on
  the computer; do not run more than two suites at once on the 4 GB sandbox.
- Gradle needs JDK 17 (`sudo dnf install -y java-17-amazon-corretto-devel`) and an Android SDK in
  local.properties (gitignored). CI takes about 7 minutes; docs-only pushes start no CI.
```
