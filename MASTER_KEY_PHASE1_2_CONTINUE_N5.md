# Master Key — Phase 1.2 continuation prompt (after N4: start N5)

**ရည်ရွယ်ချက်:** N4 (Strategy E: သီးခြား page environment) ပြီးပါပြီ။ Agent အသစ်က ဒီ prompt နဲ့ N5
(Ladder + locator cache + flag) ကို စပါ။
- Agent chat အသစ်ထဲ အောက်က code block ကို paste လုပ်ပါ။
- Task ပြီးတိုင်း push + မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
FIRST read, in this order:
  1. MASTER_KEY_PHASE1_2_PROMPT.md    (goal, HARD RULES, T1-T4, per-task routine, stop rules)
  2. MASTER_KEY_PHASE1_2_PLAN.md      (section 1, section 2, section 3 N4 "Plan adapted (N4)"
                                       and N5, section 4)
  3. PROGRESS.md top sections "Phase 1.2 — N4 ..." and "Phase 1.2 — N3 ..."
  4. this file
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
START_AT: N5 (ladder, locator cache, flag), then stop and report; N6 only after the owner says
"Next".

OWNER: reports in Burmese, short, with ✅/❌. The owner has no computer, mobile data only (no Wi-Fi).

STATE (verify against the repo; the repo wins)
- Phase 1.1 S1-S6 done; N1 done (765e91f); N2 done (9e15101; the n rule stays PENDING, plan
  section 2); N3 done (52e1945, CI 38164686967); N4 done (a7c3dd9, CI 38170962057); N4b in progress
  (bdb45c6, CI 38175876765; PROGRESS.md "N4b" lists what is left).
- Strategy B: own.solver.core.js, reached only with request strategy "B" (`hint: false` = B
  alone). Strategy E (N4): extractor-master-android WebViewPageSolverEngine (an OwnSolverEngine;
  isAvailable needs WebViewFeature.WEB_MESSAGE_LISTENER) with OwnSolverPageEnvRoutes,
  OwnSolverPageReplyGate and assets page-env.html/page-env.js (host page, app script only) +
  page-frame.html/page-frame.js (sandbox="allow-scripts" iframe; the core runs there in page mode,
  request `page: true`). Nothing in the app uses B or E yet: N5 wires them.
- E's job/reply protocol = the worker's (OwnSolverProtocol: player/prepared jobs, P only). The
  frame's runner is a copy of the worker's yftRunJob plus `page: true`.
- OwnSolverEnvironmentErrors (extractor-master) is the "environment error" rule: ReferenceError /
  TypeError in a whole failure ("program:TypeError", a worker error class) or a candidate error
  ("0:ReferenceError"). Today a worker reply names only whole failures; a kind that failed for its
  environment shows only as e.g. "n:probe".
- Harness: `node scripts/verify-own-solver.mjs` (P), `--strategy B`, `--faults`, `--page`
  (strategy E with the page-frame DOM stub, P or B); mutation runner `--strategy P,B`, `--page`.
  Numbers: PROGRESS.md "Phase 1.2 — N4" and plan section 4.
- JS tests: the CI JS step (.github/workflows/master-optin-debug-apk.yml) includes
  scripts/own-solver/tests/page-env.test.mjs (154 tests locally). Kotlin: :extractor-master:test
  326 (2 skipped), :extractor-master-android:testDebugUnitTest 69.

N5 STARTING POINTS (plan section 3 N5; decide and write "Plan adapted: ..." where needed)
- Ladder in OwnPlayerScriptRunner behind ownSolverNext / BuildConfig OWN_SOLVER_NEXT_ENABLED:
  locator cache -> P (worker) -> B (worker) -> E (page, only after an environment error), each
  answer verified (SelfCheck; B also agreement or a V pass, plan section 1 rule 6).
- Protocol additions N5 owns (worker AND page frame together, Kotlin decode + tests):
  1. a job field selecting B (and hint) for the worker and the frame;
  2. the per-kind error classes (deduplicated, at most 8, classes only) in the reply, so
     OwnSolverEnvironmentErrors can see an environment failure of one kind.
- E's open points (plan section 3 N4 "Plan adapted (N4)"): E runs on the app's single WebView
  renderer main thread; P has no step limit there (B has). Decide whether E runs B only (bounded)
  or P with a bound. Residual WebRTC path through a frame the player creates (documented).
- Flag off = main; ownSolverNext off = Phase 1.1 (P only, worker).

RULES LEARNED (keep them)
- Player code runs only through the repo's harness (verify-own-solver*.mjs, harness.mjs, canary,
  mutation runner) and static parsing. No ad-hoc execution of YouTube player code. Do NOT retry
  adding the TVHTML5 client to the stream-check tool (blocked twice by the safety review).
- Never commit player text; never log solver inputs/outputs/stream URLs (counts, lengths and class
  names only).
- Keep each command short (<= 25 s); start long runs with nohup and poll; never combine `pkill -f`
  with other commands in one shell (it can kill that shell).
- B's probes can overflow the stack inside player code; Node then prints "Exception in
  PromiseRejectCallback" or "Maximum call stack size exceeded" with a player line to the local
  log. Harmless (caught; local only); never paste such lines into commits or reports, and filter
  logs (grep the result lines) instead of printing them.
- Long harness runs must call `settle()` (scripts/own-solver/harness.mjs) after every solve. One
  B run peaks at ~1.3 GB heap; do not run more than two suites at once on the 4 GB sandbox.
- Gradle needs JDK 17 (`sudo dnf install -y java-17-amazon-corretto-devel`) and an Android SDK in
  local.properties (gitignored). CI takes about 5-7 minutes; docs-only pushes start no CI.
- If the sandbox has no git credentials, push one commit with the GitHub tool's push_files
  (this branch only; large payloads as a JSON arguments file), then check the commit and CI.
- Browser tests of WebRTC limits were refused by the safety review in N4; do not retry
  "fill the WebRTC connection limit" designs without the owner's go-ahead.
```
