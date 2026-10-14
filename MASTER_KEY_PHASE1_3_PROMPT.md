# Master Key — Phase 1.3 agent prompt v2: own client ladder at own-solver level (D0–D8)

**ရည်ရွယ်ချက်:** `MASTER_KEY_PHASE1_3_PLAN.md` (v2) ထဲက D0–D8 ကို agent တစ်ယောက်က တစ်ဆင့်ချင်း လုပ်ဖို့
prompt ပါ။ own solver က player တစ်ခုတည်းထဲမှာ n/sig ကို ပြန်ရှာသလို YouTube က visionOS ကို ပြောင်းရင်
visionOS ကိုယ်တိုင်ကို အရင်ပြင်မယ် (A version၊ S own solver၊ Z size၊ H HLS)၊ ဖုန်းပေါ်က live census
က သက်သေပြထားတဲ့ client တွေကိုပဲ နောက်ထပ် rung အဖြစ် သုံးမယ်၊ မှားတဲ့ row လုံးဝ မပေးဘဲ။

**သုံးနည်း:**
- Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။
- D0 ကို အရင်လုပ်ပါ။ D0 ပြီးရင် owner က ဖုန်းထဲမှာ "YFT client census" ကို ၃ ကြိမ် run ပြီး report ကို
  ပြန်ပေးရပါမယ် (computer မလို)။ ဘယ် rung live ဖြစ်လဲကို အဲဒီ report က ဆုံးဖြတ်ပါတယ်။
- D3၊ D5၊ D6 မှာ Reasoning Level အမြင့်ဆုံး သုံးပါ။ D8 က owner "yes" ပြောမှ လုပ်ပါ။
- Task တစ်ခု ပြီးတိုင်း push လုပ်ပြီး မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ ဆက်လုပ်ခိုင်းချင်ရင် "Next" လို့ ပြောပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
Plan: MASTER_KEY_PHASE1_3_PLAN.md v2 (read it first; context: PROGRESS.md "R6", "R9",
      "Phase 1.2 — N2" and "Phase 1.2 — N7"; MASTER_KEY_PHASE1_2_PLAN.md sections 0-4)
ALLOW_PUSH: true          (checkpoint pushes to the spike branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
START_AT: D0              (owner may change)
D8_APPROVED: false        (owner sets true to allow D8)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the report.

GOAL
Master's YouTube module keeps giving downloads when YouTube changes its visionOS client, without
an APK update and without a wrong row, at the level of the own solver (Phase 1.2):
  R0        visionOS exactly as the R6 chain asks it; a healthy answer stays a 1-request lookup
  A S Z H   requirement rungs on visionOS itself: alternate public version, player script via
            the own solver, sizes from the one-byte probe, HLS delivery from the same answer
  O         other no-token clients (embedded, MWEB, Android) ONLY when the D0 census proved them
  judge     each answer's class from what it contains (plan section 3)
  health v2 per client class, cooldown and the working variant, on the phone
  checks    V (StreamCheck), H check (playlists + one segment), Z (probe size)
  proof     parity gate + change classes V1-V9, T1-T3, H1-H2, F1-F4; wrong rows = 0
NEVER A WRONG ROW: rows only from the copied parser + MasterSiteModule's row check; S values only
after SelfCheck; V or H pass when required; rows V proved refused are removed whoever produced
them. Never guess.

HARD RULES (never break)
1. Flag off = R6. -Pyft.clientLadder (default false, BuildConfig CLIENT_LADDER_ENABLED, never in
   release) acts only with -Pyft.masterCapture; S rungs only with -Pyft.ownSolver too.
   MasterYouTubeParityTest and MasterMainMoreSheetTest stay unchanged and green after every task.
2. Copy, never edit. main's five copied YouTube files stay byte-identical (git fetch origin main,
   then python3 scripts/master-toolkit-drift.py). New code only in modules/youtube/ladder/.
3. No token, no cookie, no account. NoPoTokenProvider stays; no Cookie or Authorization header to
   any ladder client or media host; no sign-in. The TV client is NOT added (the safety review
   stopped it twice); only the owner can reopen it.
4. No bypass; the ladder never decides a refusal. Age check (ladder rows dropped), video verdict
   or DRM -> stop, the R6 chain decides. Bot check -> stop, 30 min ladder-wide cooldown, no
   further ladder request. Ladder rows merge only when the chain did not end in a terminal
   verdict. SABR-only formats are never rows.
5. Identities: at most 4, all already in main's code (visionOS, Android, embedded, MWEB), each
   with at most 2 alternate versions listed by a public reference (yt-dlp, YouTube.js, NewPipe)
   with source and date. Never invented or brute-forced; no TLS/fingerprint tricks. References
   give values and ideas only: copy no file and no code.
6. Checks. V: the shared StreamCheck, only in a user-started lookup, only the stream host
   YouTube returned, the download's headers without cookies, Range <= 64 KiB, <= 1 per client
   per lookup, "unknown" is never "pass". H check: only hosts the answer named (googlevideo.com),
   master + one variant playlist <= 256 KiB each, first segment Range <= 64 KiB, content type and
   start bytes must match, playlist duration within +-2 s of the answer's length, <= 1 per client
   per lookup. Z: the one-byte probe's total size, rows to offer only, <= 8 per lookup.
7. Budget: a healthy, complete visionOS answer is the whole lookup (1 request). Per lookup the
   ladder makes <= 3 player requests, <= 1 watch page fetch, <= 1 V and <= 1 H check per client,
   <= 8 Z probes. The same-lookup memo answers byte-identical R6-chain requests once from memory;
   never across lookups, never for media hosts.
8. Health (files/master/client-health.json): client ID, class, time, counts, working variant
   only; no address, token, visitor data or video ID. No remote code. A signed identity section
   (D8) only when D8_APPROVED is true.
9. Merge rule: a client whose rows V proved refused (403 with an open control) in this lookup
   loses them, whoever produced them (matched by address in memory, never logged); otherwise the
   R6 chain's rows win and the ladder adds only formats the chain did not give.
10. Never log stream URLs, tokens, visitor data, solver values or player text; log only client
    IDs, classes and counts (the existing details sanitizer). No live calls in unit tests or CI.

TASKS (details, files and done criteria: plan section 4)
D0  ClientCensus (extractor-master, offline-tested) + debug-only owner tool "YFT client census"
    (Start -> report -> Copy result), reusing StreamCheckMeasurement's steps for script clients.
    Hand the APK to the owner; after the owner's 3 runs, apply the gate (live = V/H-passed rows
    for >= 6/10 videos in >= 2/3 runs) and write the census table into plan section 5.
D1  MasterClientLadderSimTest: 5 base fixtures x V1-V9, plus T1-T3, H1-H2, F1-F4; columns R6
    (own solver off / on), ladder live-backed, ladder all simulated; parity gate on the 20
    MasterYouTubeParityTest scenarios. Baseline into plan section 5.
D2  MasterClientTable v2 (data, sources, census status, variants, limits) + AnswerJudge (every
    class unit-tested from main's fixtures; never DIRECT for protected, SABR-only or cipher).
D3  ClientHealth v2 (memory + file, fake clock, learned variant), order, budget, stop rules.
D4  Row checks: StreamCheck reuse (client ID in its key), HlsCheck, Z probe; fake-HTTP tests.
D5  visionOS rungs A, S (watch page signals, re-ask with signature timestamp + visitor data, own
    solver, SelfCheck, V), Z, H (HLS rows via the existing HLS engine). Reuse the copied code
    where reachable; else a Master-only equivalent tested on player_cipher*.json.
D6  O rungs (census-live only), LookupMemo, MasterYouTubeModule(http, playerScripts, ladder),
    merge rule, flag in app/build.gradle.kts, workflow input client_ladder (default false until
    D7), AndroidBrowserMasterFallback wiring, debug-only -Pyft.ladderSkip=visionos|visionos-adaptive.
D7  Canary = the census tool; owner live check (10 videos: normal, visionos-adaptive skipped,
    visionos skipped); runbook (plan section 7).
D8  Only when D8_APPROVED: signed clientIdentities section over R9's envelope (plan section 4 D8),
    tests like RemoteRecipesTest.

PER TASK
- Before coding: read the task in the plan; list the files you will touch.
- Tests first (committed fixtures and fakes; no live calls in unit tests).
- Run locally: ./gradlew :extractor-master:test (JVM module, JDK 17) and the drift check; CI runs
  the full set and builds the APK. Keep the flag-off gate green.
- From D1 on, always re-run MasterClientLadderSimTest and report its table.
- Checkpoint-push to spike/master-extractor-backup with a clear message (for example
  "feat(master): D2 client table v2 + answer judge"). Docs-only pushes start no CI; the newest
  code commit carries the CI result.
- Update the status board in MASTER_KEY_PHASE1_3_PLAN.md (TODO -> Done, with the commit sha) and
  add a PROGRESS.md section; D0 fills the census table, D1 the baseline, D6 the ladder columns.
- Report to the owner in short Burmese with ✅/❌: what changed; the census or simulator table
  (R6 / ladder live-backed / ladder all); wrong rows; requests and checks used; tests; commit sha
  and CI run; risks. Then STOP and wait for "Next".

STOP AND ASK THE OWNER WHEN
- D0's gate finds neither H nor any O rung live (plan section 9, decision 4).
- Any case gives a WRONG row, or the ladder is worse than the R6 chain, and the cause is not clear.
- The D1 baseline shows that the plan's section 5 targets are unrealistic (propose new targets
  with numbers).
- A rule would need a token, a cookie, a sign-in, another host for V or H, the TV client, or
  anything that looks like a bypass (including asking more clients after a bot check).
- A copied main file would have to change, or main's behaviour would change.
- An identity is not listed by a public reference, or the table would exceed rule 5's limits.
- The budget (rule 7) cannot be kept.
- A push fails (a local commit is not a handoff).
```
