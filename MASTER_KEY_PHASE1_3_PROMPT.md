# Master Key — Phase 1.3 agent prompt v3: own client ladder at own-solver level (G0 done, D0–D8)

**ရည်ရွယ်ချက်:** `MASTER_KEY_PHASE1_3_PLAN.md` (v3) ထဲက D0–D8 ကို agent တစ်ယောက်က တစ်ဆင့်ချင်း လုပ်ဖို့
prompt ပါ။ v2 အပြင် L (own parser)၊ self-healing version၊ HLS leg သီးသန့်၊ weekly check (early warning)
ပါပါတယ်။ ရည်မှန်းချက်: Master-caused failure ၀၊ wrong row ၀။

**သုံးနည်း:**
- Phase 1.2 ပိတ်ပြီးပါပြီ (G0 ✅ `a99e0fb`: PROGRESS.md "Phase 1.2 — done")။ D0 ကနေ စပါ။
- Agent chat အသစ်ကို ဖွင့်ပြီး Reasoning Level ကို အစကတည်းက အမြင့်ဆုံး ထားပါ (D3၊ D5a–D5c၊ D6 အတွက်)။
  ပြီးရင် အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။
- **မရပ်ဘဲ ဆက်လုပ်မယ် (`RUN_MODE: continuous`):** task တစ်ခု ပြီးတိုင်း agent က push လုပ်ပြီး
  "✅ D1 ပြီးပြီ — …" လို စာတို တစ်ကြောင်း ရေးပြီး နောက် task ကို ချက်ချင်း ဆက်လုပ်ပါမယ် ("Next" မစောင့်)။
- **"⏸" နဲ့ ရပ်တာ** ဖုန်းစစ်ဖို့ လိုတဲ့အခါ (D0 census၊ D7)၊ owner ဆုံးဖြတ်ဖို့ လိုတဲ့အခါ ဒါမှမဟုတ်
  ပြဿနာ (STOP AND ASK) ပေါ်တဲ့အခါမှပဲ ဖြစ်ပြီး ဘာလိုလဲ ပြောပါမယ်။ Agent ရပ်သွားရင် "ဆက်လုပ်" လို့ ရိုက်ပါ။
- D0 code ပြီးရင် ဖုန်း (mobile data) ထဲက "YFT client census" → Full census ကို ၃ ကြိမ် (အနည်းဆုံး
  ၁ နာရီခြား) run ပြီး report ၃ ခုကို chat ထဲ ပို့ပေးပါ။ အဲဒီအတောအတွင်း agent က D1 ကို ဆက်လုပ်နေပါမယ်။
  Weekly check ကို အပတ်တိုင်း ၁ မိနစ် run ပါ။
- **Phase 1.3 ပိတ်ပြီ (owner (ခ)၊ 2026-10-16၊ plan §9 item 13):** target အသစ် (plan §5.1)။ နောက်ဆက်တွဲ:
  A (D5a) ကို မရပ်ဘဲ ဆက်လုပ်မယ်။
- **A (D5a) ✅ (2026-10-16၊ plan §4 D5a "As built (A)"):** visionOS version ပယ်ခံရရင် yt-dlp ရဲ့ table
  ကနေ version အသစ်နဲ့ ပြန်မေး (V pass မှ သုံး)။ own solver off target = MCF ≤ 65 (plan §5.1)။ ဖုန်းစစ် မလို။
- **Owner order (2026-10-16): A check → D5c → D5b steps 7–8 → `[d7]` APK တစ်ခု။ A check (plan §9
  item 14):** visionOS version 1.02 ထက် အဟောင်း public reference မှာ မရှိ (guardrail 5) → owner ရွေးချယ်မှု
  (2): census "A check" ခလုတ် ✅ (video 2: 1.02 → DIRECT ဆိုမှ NewPipe ရဲ့ 1.04 တစ်ခါ)။ နောက် `[d7]` APK မှာ
  ဖုန်းနဲ့ run ပါ — 1.04 DIRECT + V pass ဆိုရင် visionOS ရဲ့ bundled alternate ဖြစ်မယ် (R0 = 1.02 မပြောင်း)။
- **D5c h-only trial ✅ (2026-10-16၊ plan §4 D5c "As built (h-only trial)"):** နောက် `[d7]` APK ၇ ခုထဲက
  `h-only` မှာ H ရဲ့ HLS row တစ်ခုတည်း ပြမယ် — AVC quality (အသံပါ) တစ်ခုကို download လုပ်ပြီး MP4 ဖွင့်လို့ရ၊
  ပုံ + အသံ ပါ၊ ကြာချိန် ±2 s စစ်ပါ။ H row မှာ video key ပါအောင် ပြင်ပြီ (အရင်က app က ဖယ်ထုတ်ခဲ့)။
- **D5b steps 7–8 — code မရေးဘဲ ပိတ် (owner (1)၊ 2026-10-16၊ plan §9 item 15):** နောက်ဆက်တွဲ ၃ ခု plan §10
  "Phase 1.4" (trigger ပေါ်မှ step 7/8၊ shape map မှတ်ရုံ၊ နောက်ဆုံး player ID)။ VP9 HLS = "No sound" (plan §10 item 5)။
- **Run 3 APK ၇ ခု (`[d7]`၊ plan §4 D7 "Run 3"):** `normal` → census tool → "A check (visionOS 1.04)" → ပထမ
  စာကြောင်း ပို့ပါ။ `h-only` → video 1 → "No sound" မပါတဲ့ AVC row တစ်ခု download → MP4 ပုံ + အသံ၊ ±2 s။
  ကျန် ၅ ခု optional။
- D8 က owner "yes" ပြောမှ လုပ်ပါမယ်။ ဆုံးဖြတ်ချက် (source hosts၊ D8) ကို chat ထဲ အချိန်မရွေး ပြောနိုင်ပါတယ်။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
Plan: MASTER_KEY_PHASE1_3_PLAN.md v3 (read it first; context: PROGRESS.md "R6", "R9",
      "Phase 1.2 — done", "Phase 1.2 — N2" and "Phase 1.2 — N7"; MASTER_KEY_PHASE1_2_PLAN.md
      sections 0-4)
ALLOW_PUSH: true          (checkpoint pushes to the spike branch only)
ALLOW_MERGE_MAIN: false   (draft PR #2 stays a draft: never merge it, never mark it ready)
ALLOW_RELEASE: false
START_AT: D0              (owner may change)
D8_APPROVED: false        (owner, 2026-10-15: D8 dropped; plan section 9 item 4)
SOURCE_HOSTS_APPROVED: raw.githubusercontent.com   (owner, 2026-10-15: only this host, no
                          redirect, <= 64 KiB, text only; in a lookup only after VERSION_REJECTED,
                          <= 1 fetch a day per identity; plan section 9 item 3)
RUN_MODE: continuous      (owner 2026-10-14: after each task push, post the short note and go
                          on at once; "step" = stop after each task and wait for "Next")

PREREQUISITE (plan section 4 G0, done at a99e0fb): PROGRESS.md must have the entry
"Phase 1.2 — done (owner closed it on 2026-10-14)". If it is missing, do not start; report what
G0 still needs and STOP. M5 (Phase 1.2 carry-over) is not part of this plan (plan section 9,
decision 6): do not change the own solver.

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the report.

GOAL
Master's YouTube module keeps giving downloads when YouTube changes its visionOS client, without
an APK update and without a wrong row, at the level of the own solver (Phase 1.2). Target: no
Master-caused failure (plan section 5.1): Master fails only when YouTube closes every allowed
path, and then it stops cleanly with the right verdict.
  R0        visionOS exactly as the R6 chain asks it; a healthy answer stays a 1-request lookup
  A S Z     rungs on visionOS itself: version (learned -> signed -> self-healed -> bundled
            alternates), player script via the own solver, sizes from the one-byte probe
  L         own parser: on SHAPE_CHANGED find format objects by value shape, bind them to the
            video, build canonical JSON, let the UNCHANGED copied parser build the rows
  H         independent HLS leg: one located hlsManifestUrl + the video's length + the H check;
            works when no format can be read
  O         other no-token clients (embedded, MWEB, Android) ONLY when the D0 census proved them
  health v2 per client class, cooldown, working variant, learned version, L shape map
  checks    V (StreamCheck), init check (L rows), H check, Z
  W         weekly check in the census tool: <= 60 s, <= 2 MB, diff against the last run,
            owner-started only
  proof     parity + L round-trip + classes V1-V11, L1-L16, Y1-Y6, T1-T3, H1-H2, F1-F4;
            wrong = 0, MCF = 0 (closed 2026-10-16 on the owner's closing targets, plan
            section 5.1 and section 9 item 13; then A as a follow-up, done 2026-10-16)
NEVER A WRONG ROW: rows only from the copied parser (on the answer or on L's canonical JSON) +
MasterSiteModule's row check; S values only after SelfCheck; every L row needs a V pass and an
init-check pass in the same lookup ("unknown" = not shown); rows V proved refused are removed
whoever produced them. Never guess.

HARD RULES (never break)
1. Flag off = R6. -Pyft.clientLadder (default false, BuildConfig CLIENT_LADDER_ENABLED, never in
   release) acts only with -Pyft.masterCapture; S rungs only with -Pyft.ownSolver too.
   MasterYouTubeParityTest and MasterMainMoreSheetTest stay unchanged and green after every task.
2. Copy, never edit. main's five copied YouTube files (YouTubeClientProfile, YouTubeExtractor,
   YouTubePlayerResponseParser, YouTubeSessionAuth, YouTubeUrls) stay byte-identical (git fetch
   origin main, then python3 scripts/master-toolkit-drift.py). New code only in
   modules/youtube/ladder/. L calls the copied parse() with canonical JSON; it edits no copy.
3. Excluded by the owner: SABR clients (SABR/UMP), PO tokens, MSE recording, the TV client, any
   bypass. No token, no cookie, no account: NoPoTokenProvider stays; no Cookie or Authorization
   header to any ladder client, version source or media host; no sign-in.
4. No bypass; the ladder never decides a refusal. Age check (ladder rows dropped), video verdict
   or DRM -> stop, the R6 chain decides. Bot check -> R6's chain runs as today, and after a bot
   check no InnerTube request (player or page) is sent that R6 would not send (owner,
   2026-10-15; census, per video as R6 answers each lookup: the watch page once + visionOS once
   with the page's visitor data; a bot check on a request that already carried visitor data, or
   HTTP 429, stops the run). (က+) (owner, 2026-10-15; plan section 9 item 10): on R6's R0+vd
   answer the ladder may run L (local), Z, V and the init check (media host, rule 6's budget),
   the H check (<= 1 per lookup) and S locally (own solver + SelfCheck + V) only with the player
   script R6 already fetched; the 30 min cooldown blocks only the request-sending rungs (A, O,
   S's re-ask, a version source); the census likewise runs V and H on an R0+vd answer that is
   DIRECT. Never visitor data kept to send from a lookup's first request (rejected by the owner:
   it avoids the bot check). Ladder rows merge only when the chain did not end in a terminal
   verdict. SABR-only formats are never rows. L never reads past a
   playability it cannot find.
5. Identities: at most 4 (visionOS, Android, embedded, MWEB), all already in main's code.
   Versions: learned -> signed (D8 only) -> self-healed -> <= 2 bundled alternates listed by a
   public reference (yt-dlp, YouTube.js, NewPipe) with source and date. Self-healed only from a
   source D0 proved usable: data only (one pattern-matched value, never code), a strict version
   pattern per client, newer than the bundled one, the version field only (user agent and device
   stay), <= 1 fetch a day, kept only after OK + V; a host Master does not use yet only when
   SOURCE_HOSTS_APPROVED lists it. Never invented, counted up or brute-forced; no TLS or
   fingerprint tricks. References give values and ideas only: copy no file and no code.
6. Checks. V: the shared StreamCheck, only in a user-started lookup, only the stream host
   YouTube returned, the download's headers without cookies, Range <= 64 KiB, <= 1 per client per
   lookup; L rows: one V per offered row (<= 8), first 4 KiB, the init check on the same bytes
   (type, container start, no encryption marker, size); "unknown" is never "pass". H check: only
   googlevideo.com addresses, master playlist <= 256 KiB, one variant playlist <= 1 MiB (owner,
   2026-10-16), first segment Range <= 64 KiB, content type and start bytes must match, no
   EXT-X-KEY or EXT-X-SESSION-KEY other than METHOD=NONE, playlist duration within +-2 s of the
   video's length, no H rows without a known length, <= 1 per client per lookup. Z: the one-byte
   probe's total size, rows to offer only, <= 8 per lookup.
7. Budget: a healthy, complete visionOS answer is the whole lookup (1 request). Per lookup the
   ladder makes <= 3 player requests, <= 1 watch page fetch, <= 1 V and <= 1 H check per client,
   <= 8 V for L rows, <= 8 Z probes; a version source <= 1 fetch a day per identity (<= 64 KiB).
   The same-lookup memo answers byte-identical R6-chain requests once from memory; never across
   lookups, never for media hosts. Census: a full run <= 6 MB; the weekly check <= 60 s, <= 2 MB,
   <= 20 requests.
8. Health (files/master/client-health.json): client ID, class, time, counts, working variant,
   learned version (a public string), L shape map (key paths only); no address, token, visitor
   data or video ID. No remote code. A signed identity section (D8) only when D8_APPROVED is true.
9. Merge rule: a client whose rows V proved refused (403 with an open control) in this lookup
   loses them, whoever produced them (matched by address in memory, never logged); otherwise the
   R6 chain's rows win and the ladder adds only formats the chain did not give.
10. Never log stream URLs, tokens, visitor data, solver values, player text or answer values; log
    only client IDs, classes, counts and key names (the existing details sanitizer). No live calls
    in unit tests or CI.
11. No background work: the census and the weekly check run only after the owner's tap while
    their screen is open; no WorkManager, AlarmManager, JobScheduler, foreground service, push or
    scheduled request.
12. Mobile data only: plan every live step for the owner's phone on mobile data (no Wi-Fi); no
    step needs a computer.

TASKS (details, files and done criteria: plan section 4)
D0  ClientCensus + ShapeFingerprint + CensusDiff (extractor-master, offline-tested) + debug-only
    owner tool "YFT client census" with Full census and Weekly check (Start -> report -> Copy
    result), reusing StreamCheckMeasurement's steps for script clients; H token step, bundled
    alternates on video 1, version-source measurements. Hand the APK to the owner; after 3 full
    runs on mobile data apply the gate; write the census and version-source tables into plan
    section 5.
D1  MasterClientLadderSimTest: 5 base fixtures x V1-V11, L1-L16 (each with V pass / fail /
    unknown), Y1-Y6, plus T1-T3 (T1b-T1d), H1-H2, F1-F4; columns R6 (own solver off / on), ladder
    live-backed, ladder all simulated; parity gate on the 20 MasterYouTubeParityTest scenarios;
    L round-trip. Baseline into plan section 5.
D2  MasterClientTable v2 (+ version patterns and source status) + AnswerJudge (+ SHAPE_CHANGED
    kinds, hls locate, shape fingerprint); every class unit-tested from main's fixtures.
D3  ClientHealth v2 (+ learned version, shape map, source clock), order, budget, stop rules
    ((က+) after a bot check: rule 4).
D4  Row checks: StreamCheck reuse, InitCheck, HlsCheck (+ key and length rules), Z probe.
D5a visionOS rungs A (learned -> signed -> self-healed -> alternates), S, Z.
D5b Own parser L: ShapeLocator + ShapeNormalizer (plan section 3), page locate for S, census
    round-trip and mutation steps, debug-only -Pyft.ladderMutate.
D5c H leg: HlsLeg with HlsCheck and the app's existing HLS path (DefaultVariantResolver,
    HlsAudioPairing, HlsTransferEngine).
D6  O rungs (census-live only), LookupMemo, MasterYouTubeModule(http, playerScripts, ladder),
    merge rule, flag in app/build.gradle.kts, workflow input client_ladder (default false until
    D7), AndroidBrowserMasterFallback wiring, debug-only -Pyft.ladderSkip=visionos|visionos-adaptive.
D7  Owner live check (normal, visionos-adaptive skipped, visionos skipped, ladderMutate), the
    census L steps on live answers, two timed weekly checks, runbook (plan section 7).
D8  Only when D8_APPROVED: signed clientIdentities section over R9's envelope + a phone signing
    path (workflow_dispatch, key as a repository secret), tests like RemoteRecipesTest.

PER TASK
- Before coding: read the task in the plan; list the files you will touch.
- Tests first (committed fixtures and fakes; no live calls in unit tests).
- Run locally: ./gradlew :extractor-master:test (JVM module, JDK 17) and the drift check; CI runs
  the full set and builds the APK. Keep the flag-off gate green.
- From D1 on, always re-run MasterClientLadderSimTest and record its table in PROGRESS.md.
- Checkpoint-push to spike/master-extractor-backup with a clear message (for example
  "feat(master): D5b own parser L"). Docs-only pushes start no CI; the newest code commit carries
  the CI result.
- Update the status board in MASTER_KEY_PHASE1_3_PLAN.md (TODO -> Done, with the commit sha) and
  add a PROGRESS.md section; D0 fills the census and version-source tables, D1 the baseline, D6
  the ladder columns.
- After each task, post ONE short Burmese note, then go on with the next task at once (RUN_MODE
  continuous; do not wait for "Next"):
    ✅ D1 ပြီးပြီ — <what, one line>; wrong <n>, MCF <n>; commit <sha7>; CI <✅/❌/running>; နောက်: D2
  The details (tables, requests, checks, MB, tests, risks) go into PROGRESS.md, not the chat.
- CI: before the next task's first push, check the previous code commit's CI; red -> fix it
  first (a red CI whose cause is unclear is a stop).
- Pause only for PHONE STEPS AND DECISIONS or STOP AND ASK (below). Then post one line
    ⏸ ရပ်ထားတယ် — <why>; လိုတာ: <what the owner does>
  followed by the full report in short Burmese with ✅/❌: what changed; the census or simulator
  table (R6 / ladder live-backed / ladder all); wrong rows and MCF; requests, checks and MB used;
  tests; commit sha and CI run; risks. After the last task post "🏁 Phase 1.3 ပြီးပြီ" with the
  same report and the open owner decisions.
- If your session or context restarts, or the owner says "ဆက်လုပ်" or "Next": read the status
  board and PROGRESS.md and go on with the first task that is not Done.
- RUN_MODE step: after each task the full report, then STOP and wait for "Next".

PHONE STEPS AND DECISIONS (RUN_MODE continuous)
- D0: when the census tool is pushed and CI has built the opt-in APK, post
    ✅ D0 code ပြီးပြီ — APK <name>; ဖုန်းမှာ Full census ၃ ကြိမ် (အနည်းဆုံး ၁ နာရီခြား) run ပြီး report ၃ ခု ပို့ပေးပါ
  and start D1 at once (its live-backed column waits for the census). D2 and later need the
  census and the D0 gate: if the 3 reports have not come when D1 is done, pause (⏸).
- With the 3 reports: finish D0 (tables, gate) and post "✅ D0 ပြီးပြီ — live: <rungs>; usable
  sources: <list>". Ask for SOURCE_HOSTS_APPROVED and D8 in that note, but do not wait: a usable
  source on a host not in SOURCE_HOSTS_APPROVED stays "measured, not active" (census only), and
  D8 is skipped while D8_APPROVED is false. A value the owner sends later applies from the next
  task.
- D7: post the owner's phone checklist (one line per step, mobile data) and pause (⏸) until the
  results come; then finish D7.

STOP AND ASK THE OWNER WHEN (pause ⏸ with the full report)
- PROGRESS.md has no "Phase 1.2 — done" entry (G0), or a task would change the own solver (M5).
- D0's gate finds neither H nor any O rung live (plan section 9, decision 5).
- A version source would need a host not in SOURCE_HOSTS_APPROVED for lookups, a token, a
  cookie, or running anything it returns.
- On a real answer, L cannot bind rows to the video by the plan's rules (no guessing).
- Any case gives a WRONG row or an MCF, or the ladder is worse than the R6 chain, and the cause
  is not clear.
- The D1 baseline shows that the plan's section 5 targets are unrealistic (propose new targets
  with numbers).
- A rule would need a token, a cookie, a sign-in, another host for V or H, a SABR client, MSE
  recording, the TV client, or anything that looks like a bypass (including asking more clients
  after a bot check than R6 asks, or a changed IP or network to avoid one).
- A copied main file would have to change, or main's behaviour would change.
- An identity is not listed by a public reference, or the table would exceed rule 5's limits.
- The budget (rule 7) cannot be kept, or the weekly check cannot stay within 60 s and 2 MB
  without background work.
- A push fails (a local commit is not a handoff).
```
