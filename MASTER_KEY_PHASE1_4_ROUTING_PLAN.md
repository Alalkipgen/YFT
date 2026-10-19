# Master Key — Phase 1.4 backlog item 7: YouTube routing B (plan)

**Status:** R1–R6 done (§7 "As built"): built behind the debug flag, **not promoted**. R1–R4
(2026-10-17): the per-lookup recorder, route B's Master wrappers (replay, refusals, 429 stop), the
engine's claim rule (in a route-B build Master's YouTube module answers after main's recoverable
failure) and the trial switch with the D7 APK `routeb`; flag off = today. R5 phone check (owner,
2026-10-18), **partial**: `normal` unchanged; in `routeb` one video of four got Master's rows with
the counts (`replayed 1; new 1`) and downloads that play; on two the replayed visionOS answer was a
bot check, so route B gave no rows and main's failure stayed (as §2 wants); a first open needed Try
again (why route B did not run there is not logged), and a 429 or bot check of main's was not met.
R6 docs 2026-10-18. Review follow-ups (§8): F1 done 2026-10-18 (the player-script client: a 429 on a
script fetch stops route B, Master's script fetches are counted); F2 done 2026-10-18 (a lookup where
route B gives no answer says why in Details: `master: route B not run: …`). D7 run 8 (owner's phone,
2026-10-18, §7): V1 Master's rows after Try again (`replayed 1; new 0`); V2 / V3 no rows — the
replayed visionOS answer was a bot check and the module's cookie-less GET of the watch page was
refused (main had read that page with the tab's cookie, an answer never kept: rule 4), limit (a);
first opens skipped on a remembered answer (P17, limit (b), trial only); the joined lookup (F3)
never showed. **Final plan (§9, 2026-10-18):** F3 deferred; **F5** (the owner's cookie decision:
Master reads main's watch page from the lookup, never sends a cookie) — **done 2026-10-18** (§7:
main's cookie GET of the looked-up video's page answers Master's identical cookie-less GET once,
replayed, never sent; ≤ 1 watch page per lookup) → **F4** (a real failure midway, offline) —
**done 2026-10-18** (§7: tests only, no bug found, no code changed, no `[d7]`) → D7 run 9 —
**done 2026-10-19** (§7: `normal` ✅; `routeb` Master's rows on V1–V3 after Try again, V1 / V2
download, V3's links all "Link expired" before any byte) → **F6** (diagnostics: a "Link expired"
download shows its reason and its link's facts, `[d7]` run 10) → D7 run 10 — **done 2026-10-19**
(§7: V3's link is visionOS's, fresh, refused at once with 403, `pot no`; run 9's token reading was
wrong — main never adds a token to visionOS links either) → **F7** (the route-B trial's notice
checks Master's files once and compares Master's rows with main's set-aside rows, `[d7]` run 11)
→ D7 run 11 (⏸ owner) → the owner's choice for V3 (§9) → promotion (flag on in normal builds),
the owner's decision (§5.5).
Backlog pointer: `MASTER_KEY_PHASE1_3_PLAN.md` §10 item 7. Same contract as the social sites:
`MASTER_KEY_SOCIAL_PLAN.md` §2.1 decisions #7 (routing B / B+ / A) and #8 (lookup = main + Master,
per-lookup request recorder). Agent prompt: `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md`.

**မြန်မာ အကျဉ်း:** ယနေ့ YouTube Master ladder (A/S/Z/L/H/O, V) က user ရဲ့ lookup တွေကို မဖြေရသေး —
main က page ကို တွေ့ရင် (UNSUPPORTED_URL မဟုတ်ရင်) engine က Skipped ပေးတယ် (P12 "video တစ်ခု lookup
တစ်ခု")။ ဒီ plan: main ကျရှုံးပြီး "ပြန်ကြိုးစားလို့ရတဲ့" အကြောင်းပြချက် (ဥပမာ RESPONSE_CHANGED) ဆိုမှ Master
ဖြေမယ် (route B)၊ debug flag နောက်မှာ (default off = ယနေ့နဲ့ အတူတူ)။ main အောင်မြင်ရင် မထိ၊ final
အကြောင်းပြချက် (BOT_CHECK၊ LOGIN၊ 429 …) တွေ final ဆက်ဖြစ်။ main မေးပြီးသား အဖြေကို ထပ်မမေးဘဲ memory ထဲက
ပြန်သုံး (recorder)။ အဆင့် R1–R6၊ commit တစ်ခု တစ်ဆင့်၊ CI green။ ဖုန်းစစ်ပြီးမှ promote။

**R5 ရလဒ် (owner, 2026-10-18):** `normal` ယခင်အတိုင်း ✅။ `routeb`: video 1 မှာ Try again နှိပ်ပြီးနောက်
Master rows ရ (`replayed 1; new 1`၊ 720p / 360p ဖိုင် ဖွင့်လို့ ရ၊ ကြာချိန် 3:33၊ 360p ဖိုင် 640×360)။ video 2–3 မှာ main ဆီက ပြန်သုံးတဲ့ visionOS
အဖြေက bot check ဖြစ်ပြီး နောက်တစ်ဆင့် watch page မှာ browser cookie ပါလို့ route B က မပို့ — Master rows မရ၊
main ရဲ့ failure ပဲ ကျန် (rule အတိုင်း)။ ပထမအကြိမ် ဖွင့်ရင် Try again လိုတတ်။ Flag off ဆက်ထား၊ promote
မလုပ်ရသေး (owner ဆုံးဖြတ်ရန်)။

**D7 run 8 ရလဒ်နဲ့ Final plan (owner, 2026-10-18):** V1 — ပထမဖွင့်ရင် route B မ run (main ရဲ့ မှတ်ထားတဲ့
အဖြေကို ပြန်သုံး၊ P17)၊ Try again နှိပ်မှ Master rows ရ (`replayed 1; new 0`)။ V2/V3 — route B run တယ်၊
ပြန်သုံးတဲ့ visionOS အဖြေက bot check ဖြစ်ပြီး tab cookie ပါတဲ့ watch page ကို မပို့လို့ (`refused 1`) rows မရ၊
main ရဲ့ failure ပဲ ကျန် (rule အတိုင်း)။ Wrong rows 0၊ cookie ပါတဲ့ request 0၊ JOINED မတွေ့။ **ဆုံးဖြတ်ချက် (§9):**
F3 ရွှေ့ထား (JOINED တွေ့မှ ပြန်ဖွင့်)။ Trial ရဲ့ ပထမဖွင့်တဲ့ကိစ္စ မပြင် (Try again)။ Cookie rule ကို owner က
agent ဆုံးဖြတ်ခွင့်ပေး → **F5:** Master က cookie ဘယ်တော့မှ မပို့၊ main ဖတ်ပြီးသား watch page အဖြေကို lookup
အတွင်း memory ကနေ တစ်ကြိမ် ပြန်ဖတ်ခွင့် (request အသစ် 0)။ Cookie မပါတဲ့ watch page ကို ထပ်တောင်းတာ မလုပ်။
ပြီးရင် **F4** (main တကယ်ကျတဲ့ case တွေ offline tests) → **D7 run 9** ဖုန်းစစ် → promote လုပ်မလား owner ဆုံးဖြတ်။

**F5 ပြီး (2026-10-18):** main က tab cookie နဲ့ ဖတ်ခဲ့တဲ့ ဒီ video ရဲ့ watch page အဖြေကို lookup record က
cookie မပါဘဲ key လုပ်ပြီး တစ်ကြိမ်စာ သိမ်းထား (Set-Cookie ဖယ်၊ cookie တန်ဖိုး မသိမ်း မနှိုင်းယှဉ်)။ Master ရဲ့ cookie
မပါတဲ့ တူညီ GET ကို အဲဒီကနေ ပြန်ဖြေ (replayed၊ မပို့)။ Master က cookie / authorization ဘယ်တော့မှ မပို့၊ lookup
တစ်ခုမှာ watch page တစ်ခုပဲ။ Simulator: bot check ဖြစ်တဲ့ visionOS နောက် Master rows complete (page အသစ် 0၊
player request အသစ် 0၊ credential 0)။ ပြင်ချက်: run 8 ရဲ့ `refused 1` က rule 1 မဟုတ် — Master ရဲ့ page GET မှာ
cookie မပါ (module က cookie ဖယ်ထား)၊ main က cookie နဲ့ မေးပြီးသား (အဖြေ မသိမ်း) endpoint မို့ rule 4 က ငြင်းတာ။

**F4 ပြီး (2026-10-18):** main တကယ်ကျတဲ့ case တွေ (main မေးပြီးမှ recoverable reason နဲ့ ကျ) ကို offline tests နဲ့
စစ်ပြီး — bug မတွေ့လို့ code မပြင်၊ `[d7]` မလို (D7 run 9 APK တွေ ဆက်သုံး)။ (1) Master rows က main ရဲ့ replayed
အဖြေတွေကနေ sheet ထိ ရောက် (`master: route B after <REASON>; replayed N; new 0`)။ (2) Master ဘာမှ မတွေ့ရင် main ရဲ့
failure (reason၊ message၊ Try again) ကျန်ပြီး Details မှာ Master ရဲ့ lines က main ရဲ့နောက်မှာ။ (3) final reasons မှာ route B
မ run၊ 429 နောက် request အသစ် 0၊ main အောင်မြင်ရင် မထိ၊ flag off = ယနေ့။ တွေ့ချက်: bot check ဖြစ်တဲ့ visionOS နောက်
page ရဲ့ အဖြေကို main မဖတ်နိုင်ရင် main ရဲ့ failure က `BOT_CHECK` (final) ဖြစ်လို့ route B မ run (rule အတိုင်း)၊ ဒါကြောင့်
F5 ရဲ့ page replay က အများအားဖြင့် trial (main တွေ့တဲ့ page) မှာ အသုံးဝင် — run 9 `routeb` မှာ စစ်ရန်။

**D7 run 9 ဖုန်းစစ် (2026-10-19):** `normal` V1–V3 ✅ (V3 ရဲ့ 720p / 480p / 360p download ရ)။ `routeb`: Try again
နောက် Master rows ရ (V2 / V3 `replayed 3; new 0`)၊ V1 360p နဲ့ V2 240p download ရ၊ V3 ရဲ့ quality အားလုံး byte
မရခင် "Link expired" (ပြန်စမ်းလည်း အတူတူ)။ ဖြစ်နိုင်ချေ: main က page / mweb client ရဲ့ media link မှာ
proof-of-origin token (`pot`, BotGuard) ထည့်တယ်၊ Master မှာ token maker မပါ (`NoPoTokenProvider`) — YouTube
က token မပါတဲ့ link ကို 403 နဲ့ ငြင်းရင် app က "Link expired" ပြ။ **F6** (debug diagnostics, `[d7]` run 10):
"Link expired" item မှာလည်း Details ပြ (reason၊ HTTP status၊ link facts: client၊ itag၊ `pot` ရှိ/မရှိ၊ `sig`၊
ကုန်ချိန်)၊ link ကိုယ်တိုင် မပါ။ run 10 ရလဒ်ပေါ်မူတည်ပြီး owner ရွေးရန်: A Master ကို main ရဲ့ token maker
သုံးခွင့် (scope change)၊ B route B က token လိုတဲ့ rows ကို ဖယ်၊ C known limit အဖြစ်ထား။ (run 10: ဒီ token
မှန်းချက် မှား — အောက်မှာ။)

**D7 run 10 ဖုန်းစစ် (2026-10-19):** `routeb` V3 360p (134+140) "Link expired" ရဲ့ Details: `ACCESS_DENIED`၊
`CONNECT`၊ HTTP 403၊ `VISIONOS itag 134+140 · pot no · sig yes · ends 359 min (app 358) · 0 KB · 0 s`။ link က
visionOS client ရဲ့ link၊ အသစ် (၃၅၉ မိနစ် ကျန်)၊ YouTube က ချက်ချင်း ငြင်း။ main ကလည်း visionOS link မှာ token
မထည့် (page / mweb မှာပဲ) — ဒါကြောင့် token မှန်းချက် မှား။ code ဖတ်ချက်: Master ရဲ့ rows ကို main ရဲ့ answer
တွေ (replay) ကနေ main နဲ့ တူတဲ့ code နဲ့ လုပ် — main ဘေးဖယ်ထားတဲ့ link တွေနဲ့ တူဖို့ များ။ **F7** (trial APK
မှာပဲ၊ `[d7]` run 11): route B ရဲ့ notice စာကြောင်းမှာ (1) Master ရဲ့ file တွေကို YouTube ယူမယူ ၁ byte စစ်
(`check: 206×N` ယူ / `403×N` ငြင်း) (2) Master ရဲ့ rows က main ရဲ့ rows နဲ့ link / header တူမတူ
(`same as main's: X of Y rows`)၊ မတူရင် ကွာတဲ့ နာမည်တွေ (တန်ဖိုး မပါ)။

## 1. Today (verified 2026-10-17, spike `4235716`)

- `BrowserViewModel` (L967-972) sends only `SiteAdapterOutcome.Failed` (or `Detected` when the debug
  `masterFirstSite` is on, h-only APK only) to `masterFallback.recover`.
- `BrowserMasterFallback.recover` (`app/.../detection/master/BrowserMasterFallback.kt` L83-112):
  disabled → primary; `Detected && !masterFirst` → primary; `sites.allowsMasterFallback`; failure =
  `Failed.reason ?: (Detected → UNSUPPORTED_URL else NO_MEDIA_FOUND)`;
  `TerminalRules.blocksFallback(failure, url)` → unanswered; then the request factory.
- `MasterFallbackEngine.kt` L68-74: a page a module claims (`identify`) → `Skipped(primaryFailure)`
  unless `primaryFailure == UNSUPPORTED_URL` (P12, comment L42; `MasterSiteModule.kt` L14). YouTube
  main returns `UNSUPPORTED_URL` only for an invalid ID (`YouTubeExtractor.kt` L88-89). Evidence: run 3,
  `PROGRESS.md`. So the ladder answers only the census and the h-only trial, never a user lookup.
- `TerminalRules`: NEVER_FALLBACK = ADAPTER_DISABLED, DRM_PROTECTED, PRIVATE_OR_UNAVAILABLE,
  GEO_RESTRICTED, NETWORK, RATE_LIMITED; BROWSER_REQUIRED = LOGIN_REQUIRED, BOT_CHECK,
  PLAYER_SCRIPT_REQUIRED (final on WALLED_HOSTS, YouTube included).
- Wiring: `MasterLadderSwitch.kt` L57-73 builds `MasterYouTubeModule(http, scripts, ladder(...))`;
  `AndroidBrowserMasterFallback.create` passes `OkHttpExtractorClient(client)`; main's client comes
  from `SiteAdapterModule.kt` (own solver for main through `OwnSolverSelection.runner(...)`).

## 2. Goal and rules

- **Route B for YouTube:** Master answers only after main failed with a **recoverable** reason, behind
  a debug flag (proposed `-Pyft.youtubeRouteB`, default **off**; flag off = today and P12 exactly).
- Recoverable set (proposed, owner decides): NO_MEDIA_FOUND, EXPIRED_LINK, RESPONSE_CHANGED,
  HTTP_STATUS, MALFORMED_RESPONSE, RESPONSE_TOO_LARGE (+ UNSUPPORTED_URL as today). Named in one
  place (`TerminalRules` or a policy object), tested per reason.
- Never: main success touched; NEVER_FALLBACK or BROWSER_REQUIRED reasons answered on YouTube;
  `masterFirstSite` changed (stays debug only); a found page relabeled `UNSUPPORTED_URL`.
- One lookup = main + Master (decision #8). Main's identical unauthenticated answers are replayed
  from memory, not asked again; authorized (cookie) answers are never kept or replayed — one
  exception from F5 (§9, the owner's cookie decision 2026-10-18): main's watch page answer, read
  once by Master from the lookup instead of asking again; Master never sends a cookie or another
  authorization; after a 429 in the lookup no new YouTube request; new requests only from rungs main
  lacks (A/O/H/V) within existing budgets (guardrail 7; D1 target H2 ≤ 1 player request, complete).
- Out of scope (Phase 1.3 §8 kept): SABR, PO tokens, MSE, TV, bypass, cookies / sign-in (Master
  never sends them; F5 only reads main's page), main merge and release. **B+** (Master adds formats
  main lacks) and **A** (Master primary, separate flag, ground rule 2) are later items, not this one.
- Locked and unchanged: the 5 copied YouTube files (byte-for-byte, drift 0), `ShapeLocator`,
  `ShapeNormalizer`, `Rungs`; `extractor-sites` untouched (tests-only dependency stays).

## 3. Steps (one per commit, tests + CI green before the next)

- **R1 — per-lookup request recorder (app side, shared with social S0).** Wraps main adapters' HTTP
  client; lookup-scoped (coroutine context element or OkHttp interceptor tag); memory only, dropped
  at lookup end. Records request hash (method + URL + body + authorization state), status, 429 seen,
  time left; may keep unauthenticated answer bodies (size-capped) for replay. Never logged (S8).
- **R2 — Master HTTP wrapper for the YouTube module.** Replays main's identical unauthenticated
  answers; refuses authorized ones; stops new requests after a 429; counts replayed / new.
- **R3 — engine / policy claim rule.** With the flag on, a claimed YouTube page is not `Skipped` for
  a recoverable reason; Details line `master: route B after <reason>; replayed N; new M`.
  Flag off = byte-identical behavior to today.
- **R4 — debug trial switch + APK.** A debug switch forces main's YouTube result to
  `Failed(RESPONSE_CHANGED)` after main asked (recorder kept); new D7 entry `routeb` in
  `.github/d7-apks.txt`; push with `[d7]` in the commit message. ⏸ owner phone check.
- **R5 — phone proof (owner).** Normal APK: unchanged. `routeb` APK: Master rows download and play,
  replayed / new counts shown, wrong rows 0, 429 / bot check stays final.
- **R6 — docs.** `PROGRESS.md`, this plan "As built", `MASTER_KEY_ARCHITECTURE.md`, §10 item 7 status.

## 4. Tests

- Engine claim rule per reason, flag off and on (off = P12 exactly).
- Recorder: isolation across two concurrent lookups; replay only identical unauthenticated
  requests; authorized never replayed; 429 stop; nothing logged.
- App flow (`BrowserMasterFlowTest`, `BrowserMasterFallbackTest`): Failed(RESPONSE_CHANGED) →
  Master answers; BOT_CHECK / LOGIN_REQUIRED / PLAYER_SCRIPT_REQUIRED / RATE_LIMITED → not.
- Simulator: shape moved → L locates from the replayed answer with 0 new player requests.
- Unchanged: parity tests, drift 0, `FixtureBaselineTest`, extractor-sites tests.

## 5. Owner decisions (before R1)

1. Flag name and default (proposed `-Pyft.youtubeRouteB`, off).
2. Recoverable set (§2).
3. Is keeping main's unauthenticated answers in memory for replay allowed?
4. Budget numbers (new requests per lookup; H2 ≤ 1 player request).
5. Promotion (flag on in normal builds) only after the R5 phone runs.
6. **Cookie rule (owner, 2026-10-18, after D7 run 8):** the agent's call ("do what works",
   approved). Chosen (§9, F5): Master never sends a cookie; main's watch page answer may be read once
   from the lookup's memory. Not chosen: a cookie-less watch page asked again.
7. **F3 and the trial's first open (2026-10-18):** F3 deferred until a joined lookup shows; the
   trial's first open stays (Try again). F4 is needed before promotion (§9).

## 6. Stop rules

STOP AND ASK when: a locked file or `extractor-sites` would change; flag-off behavior would differ
from today; a final reason would be answered; a test needs weakening; CI fails twice on the same step.

## 7. As built

- **R1 (2026-10-17).** Owner decisions (§5): the proposed values are used — `-Pyft.youtubeRouteB`
  default off (debug only; release and preview false); the §2 recoverable set; main's unauthenticated
  answers kept in memory per lookup (size-capped); guardrail 7's budgets, no new numbers; no promotion
  before R5. **Plan adapted:** the record and the recorder live in `extractor-master`
  (`lookup` package: `LookupRecord`, `LookupAsk`, `LookupRecorder`; plain Kotlin), not in `app`, so
  R2's Master wrapper and the simulator test use the same object. The app installs them:
  `SiteAdapterModule` wraps main adapters' shared client in `LookupRecorder` (`YouTubeRouteB.mainClient`,
  route-B build only), and `BrowserViewModel.lookUp` runs each YouTube browser lookup (main's adapter,
  then `recover`) with a new `LookupRecord` in its coroutine context
  (`BrowserMasterFallback.lookupContext`; every other build and site: nothing). Record per ask: who
  asked (main / Master), a 12-hex hash of method + address + body + authorization state, status or
  failure, 429 seen, start and duration from the lookup's start; `asked` (endpoint = method, address,
  body), `rateLimited`. "Time left" = those times plus `timeLeftMillis(budget)` against an existing
  budget; the record has no budget of its own. Kept for one replay: main's unauthenticated, non-media
  HTTPS answers (cookies dropped; ≤ 16 answers, ≤ 8 MiB), served only to an identical request
  (method, address, body, headers in order). Authorized answers (non-blank `Cookie`,
  `Authorization`, `Proxy-Authorization`) and file checks are listed but never kept. Nothing is
  logged; `toString` shows counts only. Limits: only the shared adapter client is recorded (the
  player-script, PO-token and TikTok clients are separate); a lookup that joins another caller's
  running shared lookup (P17) records into that caller's record; home and pasted-link lookups carry
  none. Nothing reads the record yet (R2, R3).
- **R2 (2026-10-17).** `RouteBLookup` (`extractor-master` `lookup`): one route-B run of Master's
  YouTube module over the lookup's record, a coroutine context element the engine adds (R3); it counts
  replayed / new / refused (numbers only; `summary()` = `replayed N; new M` and `; refused K` when any,
  for the Details line). Without it the wrappers only pass through: `RouteBHttpClient` (the module's
  client, so the copied chain, the ladder's memo, A, O, V, Z and H's playlists), `RouteBMediaStartReader`
  (L's V and init reads, H's first segment) and `RouteBPlayerScripts.wrap` (the own solver; keeps
  `SignatureVerdicts`, so `SignatureGate.of` sees the same kind). Rules per request, in order:
  (1) authorized (non-blank `Cookie` / `Authorization` / `Proxy-Authorization`) → refused, never sent;
  (2) identical to main's kept unauthenticated answer → replayed, once; (3) after a 429 in the lookup,
  main's or Master's → refused `RATE_LIMITED`; (4) endpoint (method, address, body; not a media host)
  already asked in the lookup — main's answer authorized, not kept or already replayed, or Master's own
  — → refused; (5) otherwise new: sent, listed as Master's, never kept. File checks and media reads:
  never replayed (as in the ladder's memo), rules 1, 3 and 5. **Plan adapted:** a refusal reads as a
  request without an answer (`NETWORK`, no status; never `UNSUPPORTED_URL`), not a new reason; the
  watch page falls under rule 4, so main's page stays the lookup's one (guardrail 7); an O rung's
  request that differs from main's in its body (main's carried a PO token) is new, one that differs
  only in headers (main's carried the cookie) is refused; Z's file checks are new requests of a rung
  main lacks, inside its bound; the own solver fetches its script with its own client, so it is only
  refused after a 429 (its fetches are not counted; **superseded by F1**: that client follows route
  B's rules and its fetches are counted); A's version source (D5a, not YouTube, at most once
  a day) is outside the rules. App: `MasterLadderSwitch.youTube(..., routeB)` gives the module
  `YouTubeRouteB.masterClient` / `masterScripts` / `masterReader` in a route-B build (the process's
  ladder makes its reader once); every other build: the same objects as before. Simulator
  (`RouteBSimTest`, §4): main (`extractor-sites`, test only) then Master in the same world, every class
  and base, own solver off and on, Master after every main result as R4's trial runs it (990 runs):
  0 wrong rows, 0 credentials, ≤ 1 watch page per lookup (main + Master), Master ≤ 3 player requests
  and never more than the ladder alone; after a recoverable failure (230 runs: L2, L4, L7, L10, L13,
  F3) never worse than the ladder alone; moved shape (L2, L4, L7): complete from main's replayed
  visionOS answer, 0 player requests, 0 pages; after main's 429 nothing sent. Note: L1 (streaming data
  moved) ends main with `LOGIN_REQUIRED` in the simulator (final, so no route B there). Limit: a
  refused visionOS request would count as a refusal in the ladder's client health (practically never:
  main asks visionOS first, unauthenticated, so Master's identical request is replayed).
- **R3 (2026-10-17).** `TerminalRules.ROUTE_B_RECOVERABLE` (`routeBRecoverable`) is §2's set, the one
  place the engine, the app and the simulator read; it never overlaps `NEVER_FALLBACK` /
  `BROWSER_REQUIRED`. `MasterPolicy.youtubeRouteB` (default false; `AndroidBrowserMasterFallback.create`
  sets it from `YouTubeRouteB.enabled`). Engine: a claimed page whose primary failure is not
  `UNSUPPORTED_URL` is skipped (P12) unless the switch is on, the module is YouTube's (`siteId`
  `youtube`), the reason is recoverable and the lookup's record holds at least one request of main's;
  then the module runs once in a `RouteBLookup` over that record, inside the engine's own timeout, and
  its rows or failure carry the Details line first: `master: route B after <reason>; replayed N; new M`
  (`; refused K` when any; also on a timeout and for a different requested video). **Plan adapted:**
  (1) a lookup whose record has no request of main's (no record: home, pasted link; a lookup that
  joined another caller's shared lookup, P17; a cached answer) stays skipped — Master would otherwise
  ask again what main asked; (2) when Master's module gives no rows (its failure, no focused row, the
  merge gate, a timeout), the app keeps main's failure — reason, message, Try again — and adds
  Master's details after main's, so route B never turns main's answer into another reason (never
  `UNSUPPORTED_URL`, never a final one); (3) the same video still open after a reload during the lookup
  keeps the module's rows (as the h-only trial, item 2 run 4). Limit: found rows carry no details, so
  the counts show only in a failure's Details; R4 shows them on the phone.
- **R4 (2026-10-17).** Debug switch `-Pyft.routeBTrial` (true / false, default false; acts only with
  `-Pyft.youtubeRouteB` and `-Pyft.masterCapture`; `BuildConfig.YOUTUBE_ROUTE_B_TRIAL`, debug only,
  release and preview false; `YouTubeRouteB.trial`). `BrowserMasterFallback.trialPrimary`: in the trial a
  YouTube page main **found** is answered as main's `RESPONSE_CHANGED` (main's adapter, Try again, no
  page files, Details `master: route B trial: main's N rows set aside`) inside the lookup, after main
  asked, so its requests stay in the lookup's record and R3's rule lets Master's module answer; main's
  failures go on as they are (a bot check, login, player script or 429 stays final), another site's
  page and every other build: main's answer itself (`BrowserViewModel.lookUp`). **Counts on the
  phone (R3's limit):** `SiteAdapterOutcome.Detected` carries `details` (default none); only route B's
  rows fill it (the engine's details, its line first), and the browser's page lookup shows route B's
  line as the page notice (`YouTubeRouteB.notice`: `master: route B after <reason>; replayed N; new M`).
  When Master gives no rows the trial's message stays (R3) with Master's Details. **Plan adapted:**
  the D7 modes live in the workflow's matrix, so the new APK `routeb` is a matrix entry in
  `.github/workflows/master-optin-debug-apk.yml` (route B and the trial on; every other mode: off) and
  `.github/d7-apks.txt` says `run 7`; pushed with `[d7]` (eight APKs). Limits: the notice is shown for
  the page lookup only (a feed's focused lookup shows the rows without it); a lookup answered from
  main's shared cache (P17) has no request of main's in its record, so the trial's message comes
  back — tap Try again (a fresh lookup asks main again). Tests: `BrowserMasterFallbackTest`
  (`theRouteBTrialAnswersMainsFoundYouTubePageAsMainsFailure`, route B's rows carry the line, other
  rows none), `BrowserMasterFlowTest` (`theRouteBTrialShowsMastersRowAndRouteBsCounts`: trial →
  Master's row, notice `replayed 1; new 0`, main asked once; route B alone → main's row, no notice),
  `YouTubeRouteBTest` (trial needs route B; the notice is route B's line only).
- **R5 phone check (owner, 2026-10-18; D7 run 39084859828 on `44493f8`).** From the `[d7]` run's artifacts: (1) `yft-d7-normal`: a YouTube
  video's Download shows main's rows as before, no `master: route B` notice. (2) `yft-d7-routeb`
  (installs over it): open a YouTube watch page (not the Shorts feed) and wait; the notice reads
  `master: route B after RESPONSE_CHANGED; replayed N; new M` — send the line; Download → download one
  AVC quality with sound (720p or 1080p): the file plays, picture and sound, duration ±2 s of the
  page's; one more row (another height): its height is what the row says (wrong rows 0). Two or three
  videos. (3) If the sheet says "Route B trial: main's rows were set aside and Master found none":
  send Details, then tap Try again once. (4) A bot check, sign-in or "try again later" in `routeb`
  stays as in `normal` (no Master rows).
  **Results** (owner's screenshots; V1 `dQw4w9WgXcQ`, V2 `jNQXAC9IVRw`, V3 `aqz-KE-bpKQ`, V4 the
  Short `BGQWPY4IigY`, each opened from the address bar): (1) `normal`: pass — main's rows as before
  on all four (V1 2160p / 1440p WebM, 1080p / 720p MP4; V2 M4A / MP3, 240p / 144p; V3 2160p60 /
  1440p60 / 1080p60; V4 720p 608×1080, 360p), no notice. (2) `routeb`, first open of each video
  (V1 also re-opened twice): the trial's message with Details only `master: route B trial: main's N
  rows set aside` (V1 9, V2 3, V3 9) — route B did not run (no Master line: the lookup's record held
  no request of main's, R4's limit, or the fallback stopped early; for V1's re-opens a reused answer,
  P17; for the first opens not logged). (3) Try again: V1 → Master's rows (MP3 192 / 128 made on the
  phone, 2160p / 1440p WebM, 1080p, 720p, 360p …) and the notice `master: route B after
  RESPONSE_CHANGED; replayed 1; new 1`; 720p and 360p downloaded: they play with picture and sound
  and seek, the duration is 3:33 (the page's) and the 360p file is 640×360 (as its row says); a
  retest gave the same. V2
  and V3 → no rows, main's failure kept, Details `master: route B after RESPONSE_CHANGED; replayed 1;
  new 0; refused 1`, `client VISIONOS: LOGIN_REQUIRED; with URLs: 0 progressive, 0 adaptive; SABR
  no`, `client VISIONOS reason: Sign in to confirm you're not a bot`, `visionOS first: no playable
  answer, so the watch page is read`, `watch page GET failed (NETWORK)`, `ladder H: no HLS address`,
  `master: youtube module: NETWORK`. V4 (the Short): the same message, no Details sent. Reading (the
  code): replayed 1 = the visionOS player request, main's own answer (here a bot check); refused 1 =
  the module's watch-page GET: main had asked the same page with the tab's `Cookie` (`YouTubeExtractor`
  `pageHeaders(request.requestContext)`), an authorized answer that is never kept, so `RouteBLookup`
  rule 4 never sends Master's GET of it (which carries no cookie: `MasterYouTubeModule` asks with
  `cookie = null`) and it reads as `NETWORK` (corrected in F5; this reading first said rule 1). (4) Main gave no bot check, sign-in or try-later on these
  videos (`normal` found all four); the bot check Master met inside route B gave no rows and main's
  failure stayed, so §2's rule held. Wrong rows: 0 (the 360p row checked). **R5 is partial**
  (review, 2026-10-18): check (4) — a 429 or bot check of main's stays final — was not exercised on
  the phone (main met none), and why route B did not run on the first opens is not known (not
  logged; §8 F2).
- **R6 (2026-10-18).** Docs only, no code: R5's results above, this plan's status,
  `PROGRESS.md`, `MASTER_KEY_ARCHITECTURE.md` (layout `lookup/` and `YouTubeRouteB`, flow, the R8
  note, next) and `MASTER_KEY_PHASE1_3_PLAN.md` §10 item 7.
- **F1 — the player-script client (2026-10-18, review follow-up §8).** Gap found in review: the
  runners (the own solver, main's ejs runner) fetch the player script with the player client
  (`SiteAdapterModule.providePlayerScriptRunner`), not the adapters' client, so neither the
  recorder nor `RouteBHttpClient` saw those fetches. Under route B a 429 on Master's script fetch did
  not stop the runner's second address (`OwnPlayerScriptRunner.fetchPlayer`: the page's player, then
  the desktop build of it) nor Master's next requests, and main's script 429 was not in the record
  (an offline probe: 2 script fetches, `rateLimited` false, the next request sent) — against §2's
  "after a 429 in the lookup no new YouTube request". Fix: `RouteBScriptClient` (`lookup`), the
  player client in a route-B build (`YouTubeRouteB.scriptClient`; every other build: the same client,
  so flag off = today). In Master's route-B run each script fetch follows rules 1, 3 and 5
  (`RouteBLookup.script`: never replayed, not refused as asked before — a later run of the ladder
  fetches its script again), so it counts in `new M` and a 429 stops what follows, the second
  address included; in main's lookup each fetch goes out unchanged and a 429 or rate limit is marked
  in the record (`LookupRecord.unlisted`: not listed, counted or kept); outside a lookup it only
  passes through. `RouteBPlayerScripts`' gate (the runner not asked after a 429) stays. So R5's
  `new 1` (V1) counted no script fetch; the same lookup now shows each one in `new M`. Tests:
  `RouteBScriptClientTest` (5, the probe's case first), `YouTubeRouteBTest`
  (`thePlayerScriptClientIsRouteBsOnlyInARouteBBuild`); the lookup tests (46), `RouteBSimTest` and
  `MasterFallbackEngineTest` pass. Limit: the PO-token client (main's BotGuard, with its own
  transport too) stays outside the record; Master's module asks no PO token.
- **F2 — why route B did not run (2026-10-18, review follow-up §8).** R5's first opens showed the
  trial's message with no Master line, and nothing said why. Now, in a route-B build, route B's page
  (a YouTube page main failed) that gets no answer from route B keeps main's failure — reason,
  message, retry — with one Details line after main's (`AndroidBrowserMasterFallback`):
  `master: route B not run: <REASON> is final` (R2's final reasons); `… no page to ask` (no capture
  request); `… another page` (the page to ask shows another video); `… skipped (<REASON>); no lookup
  record`, or `… skipped (<REASON>); main's requests in this lookup: N` with, when main's answer
  reached the lookup without asking, `; main's answer reused: its remembered answer (P17)` or
  `… another caller's running lookup (P17)` (the engine's skip, read from the lookup's record; N > 0
  means the engine's own rule declined); and `master: route B answer dropped: the page changed during
  the lookup` (route B answered for a video the page no longer shows). The reuse is noted by the
  shared lookup (`SiteAdapterCoordinator.inspect`: a remembered answer, or joining another caller's
  running lookup, whose requests go to that caller's record) in the lookup's record
  (`LookupRecord.reuse`, `noteReuse`); only a route-B build's YouTube lookup has a record, so every
  other lookup is unchanged. Flag off (and the h-only APK): every step answers exactly as before
  (main's answer itself; `master: h-only: <step>`). Tests: `BrowserMasterFallbackTest`
  (`routeBNeverAnswersAFinalReason`, `routeBWithoutMainsRequestsKeepsMainsAnswer`,
  `routeBKeepsTheRowsOfTheSameVideoReloadedMeanwhile` now expect main's failure with the line;
  new `routeBSaysWhyItNeverAskedMaster`, flag off: main's answer itself), `SiteLookupCacheTest`
  (`aLookupsRecordNotesAnAnswerItDidNotAskFor`), `LookupRecordTest` (the note); the lookup tests
  (47) and `MasterFallbackEngineTest` pass. D7 run 8 builds `routeb` with it for the phone run.
- **D7 run 8 phone check (owner, 2026-10-18; run 39095306817 on `8584519`, `routeb`).** V1
  `dQw4w9WgXcQ`: first open → the trial's message with `master: route B not run: skipped
  (RESPONSE_CHANGED); main's requests in this lookup: 0; main's answer reused: its remembered answer
  (P17)`; Try again → Master's rows (MP3 192 / 128 made on the phone, 2160p / 1440p WebM, 1080p,
  720p, 480p, 360p, 240p MP4; 3:33) and the notice `master: route B after RESPONSE_CHANGED; replayed
  1; new 0`. V2 `jNQXAC9IVRw` (main's 3 rows set aside) and V3 `aqz-KE-bpKQ` (9): route B ran and
  gave no rows, the trial's message kept, the same on Try again — Details `master: route B after
  RESPONSE_CHANGED; replayed 1; new 0; refused 1`, `client VISIONOS: LOGIN_REQUIRED; with URLs: 0
  progressive, 0 adaptive; SABR no`, `client VISIONOS reason: Sign in to confirm you're not a bot`,
  `visionOS first: no playable answer, so the watch page is read`, `watch page GET failed (NETWORK)`,
  `ladder H: no HLS address`, `master: youtube module: NETWORK`; V2 re-opened: the remembered-answer
  line. **Reading:** limit (b) is P17's cache — main's success is remembered and a lookup in its
  window asks nothing, so the record holds no request of main's and the engine skips; trial only (a
  real failure is never remembered, and Try again asks afresh). The joined lookup (F3) never showed.
  Limit (a) as in R5: main read the watch page with the tab's cookie (never kept), so rule 4 refuses
  the module's cookie-less GET of it (corrected in F5; first read as rule 1). §2
  held: wrong rows 0, nothing with a cookie sent, main's failure kept whenever Master had no rows.
  V1's `new 0` (R5: `new 1`): Master's rows came from the replayed visionOS answer alone.
- **Item 7 status (2026-10-18): built behind the flag, not promoted.** `-Pyft.youtubeRouteB` stays
  off in every normal build (flag off = today, P12); promotion is the owner's decision (§5.5).
  Limits seen on the phone: (a) when the replayed visionOS answer is a bot check (or another
  unplayable answer), the module's next step is the watch page, which main asked with the tab's
  cookie (an answer never kept), so Master's GET of it is never sent (rule 4) and route B gives no
  rows and main's failure stays (F5, below, answers it from main's page); (b) a lookup answered from
  main's reused answer (P17's shared lookup or cache) has no request of main's in its record, so
  route B is skipped and the trial needs Try again (F2: Details now say so, and which reuse). Next (§9, final plan): (a) → F5, Master
  reads main's watch page from the lookup (no cookie-less page; done, below); (b) stays (trial only,
  Try again); then F4 (done, below) and D7 run 9.
- **F5 — main's watch page for route B (2026-10-18, review follow-up §8; the owner's cookie
  decision, §5.6).** `extractor-master` `lookup` and the app's record (no locked file, no
  `extractor-sites` change). The app's route-B build names the lookup's video in its record
  (`BrowserMasterFallback.lookupContext`: `LookupRecord(watchPageVideoId = …)` from
  `sites.videoKey`, a YouTube lookup only). Such a record also keeps main's first GET of that video's
  page (`YouTubeUrls.watchPageFetchUrl`, no body) when it carries the tab's `Cookie` and no other
  credential (`LookupAsk.cookieOnly`): the success without the cookies it set, inside the same caps
  (16 answers, 8 Mi characters), dropped at the lookup's end, keyed by the same request without its `Cookie`
  header (`LookupAsk.withoutCookie`), so the cookie's value is neither kept nor compared and only a
  request with no credential is answered from it. Master's GET of the page (same method, address,
  body; same headers in order but `Cookie`) is answered from it once by rule 2: replayed, counted,
  never sent. Every other authorized answer is still never kept (the page client's POST with the
  cookie included), and Master still never sends a cookie or another authorization (rule 1;
  `MasterYouTubeModule` drops the cookie). One watch page per lookup (guardrail 7):
  `LookupAsk.watchPage` (an HTTPS GET of a youtube.com host's `/watch` or `/embed/…`) and
  `LookupRecord.watchPageAsked`; `RouteBLookup`'s rule 4 also refuses Master's page GET once any page
  was asked in the lookup (main's or Master's; another address or video included), so Master's own
  page goes out only when main asked none (as before F5), once. Correction: Master's page GET never
  carried the cookie; R5's and run 8's `refused 1` was rule 4 (readings above corrected).
  **Simulator** (`RouteBSimTest`): the sweep over every class with the page named
  (`build/routeb-sim/routeb-f5-lines.txt`, 1,090 steps) keeps every rule of R2's sweep (wrong rows 0,
  credentials 0, ≤ 1 page, ≤ 3 player requests and never more than the ladder alone, every request
  listed); where route B answers in the app (230 recoverable steps, 560 of the trial's found page)
  none ends worse than before F5 and 226 end better (STOP → COMPLETE or PARTIAL, as the ladder alone:
  T1b–T1d, V7, V9, Y2–Y4, H1, F1, F2, F4); after a final reason (route B never runs there; the sweep
  runs Master after every main result) L9 (main `LOGIN_REQUIRED`; a decoy of the same length) now
  reads the page as the ladder alone does and stops as designed (10 lines COMPLETE → STOP, the
  ladder alone STOP). Totals: replayed 1,090 → 3,504, refused 920 → 0, new 2,918 → 3,591 (player
  166 → 315, all inside the bounds above), Master's own pages 4 → 4. After a bot-checked visionOS
  answer (T1b; a V2-like page whose visionOS answer is always a bot check; the same signed in: the
  page names an account, the cookie holds a session) Master completes from main's replayed page with
  0 pages, 0 player requests, 0 credentials and `replayed` ≥ 2; before F5 it did not complete. Signed
  in over every class, Master's requests carry no cookie, authorization, session header or account
  identity. Tests: `LookupRecordTest` (+7), `RouteBHttpClientTest` (+4), `RouteBSimTest` (+3),
  `BrowserMasterFlowTest` (`theRouteBTrialOnAV2LikePageShowsMastersRowFromMainsWatchPage`: Master's
  row with `master: route B after RESPONSE_CHANGED; replayed 2; new 0`), `BrowserMasterFallbackTest`
  (`aRouteBRecordKeepsMainsCookieGetOfTheLookedUpVideosPageForOneReplay`); the lookup tests (58),
  `extractor-master` 727 (2 skipped), `extractor-sites` 280 and the app's unit tests 989 (66 skipped) pass.
  Flag off and every other build: no record, unchanged (P12). D7 run 9 (`[d7]`) builds `routeb` with
  it for the phone check (§9 item 3).
- **F4 — a failure midway, offline (2026-10-18, review follow-up §8).** Tests only: they found no
  bug, so no app or extractor code changed (no locked file, no `extractor-sites` change) and there is
  no `[d7]`; D7 run 9's APKs (F5) stay the latest for the phone check. Main asks and then fails with
  a recoverable reason (§2's set), not the trial's found page. (1) **Master's rows reach the sheet.**
  `BrowserMasterFlowTest` (the app as built: main's adapter in the coordinator, Master's engine with
  the module, `AndroidBrowserMasterFallback` with route B on and the trial off, the view model and
  the store): for each of the six recoverable reasons, with main's player request alone and with
  main's watch page read with the tab's cookie after it, Master's module asks the same requests
  without the cookie and is answered from the lookup's record (`replayed 1` or `2`, `new 0`; only
  main's page carried the cookie), and Master's row reaches the sheet with the notice `master: route
  B after <REASON>; replayed N; new 0`. `RouteBSimTest` (main, then Master's engine with the YouTube
  module over the same record, which names the page as the app's route-B build does): a moved shape
  main cannot read (L2, L4, L7; both own-solver columns) — complete from main's replayed visionOS
  answer, wrong rows 0, 0 player requests, 0 pages, nothing with a credential, and the engine's first
  line is route B's with the record's counts (observed `replayed 1`–`2; new 3`–`6`, none a player
  request or a page); after a bot-checked visionOS answer (every identity asked without the cookie
  meets the bot check; main reads the page with the cookie and its script fetch fails,
  `HTTP_STATUS`) — Master's own solver completes from main's replayed page with 0 new pages and 0
  player requests (observed `master: route B after HTTP_STATUS; replayed 4; new 1`, the new request
  to the media host). (2) **Master finds nothing.** `BrowserMasterFlowTest`: main's failure stays —
  the same reason, message, notice and Try again as in a build without route B (there main's answer
  itself, no record, Master never asked: P12) — with Master's details after main's (`master: route B
  after RESPONSE_CHANGED; replayed 2; new 0`, the module's own, `master: youtube module:
  BOT_CHECK`); nothing of Master's is sent; Try again asks afresh in a new record and shows Master's
  row. `RouteBSimTest`: where Master's engine finds nothing (F3; L10 and L13 when V fails or cannot
  answer) its failure names route B first with the record's counts (observed `replayed 5; new
  1`–`9`) and its own reason last; wrong rows 0, ≤ 1 page, nothing with a credential. (3) **Final
  reasons, a 429, main's success, flag off.** `BrowserMasterFlowTest`: each final reason
  (`NEVER_FALLBACK` and `BROWSER_REQUIRED`, nine) keeps main's failure with `master: route B not run:
  <REASON> is final`; Master's module never runs and nothing of Master's goes out (main's 429 answers
  `RATE_LIMITED`); a 429 midway in a lookup main then failed recoverably: Master's identical request
  is answered with main's 429 (replayed), its other request is refused, nothing new goes out, and
  `master: route B after RESPONSE_CHANGED; replayed 1; new 0; refused 1` and `master: youtube
  module: RATE_LIMITED` follow main's details (main's failure and Try again kept); main's success is
  untouched (main's row, no notice, Master never asked). `RouteBSimTest`: L11 (`LOGIN_REQUIRED`) and
  a moved shape after a bot-checked visionOS answer (main's `BOT_CHECK`): the engine skips, nothing
  is logged; a 429 in Android's answer while main's failure is recoverable (L2): route B runs and
  sends nothing new (`replayed 5; new 0; refused 3`–`9`); flag off: skipped as today (P12).
  `BrowserMasterFallbackTest`'s route-B tests (the bridge alone, R3–F5) are unchanged. This covers
  R5's open check (4) — a 429 or bot check of main's stays final — offline. **Finding (no bug; §2
  holds):** main ranks a bot check as the page's own verdict; only a delivery verdict (streams found
  but no download: an expired address, or its player-script step refused, including a recoverable
  failure of its script fetch, `HTTP_STATUS` or `RESPONSE_TOO_LARGE`) outranks it. So after a
  bot-checked visionOS answer a moved or empty answer makes main fail `BOT_CHECK` (final) and route B
  never runs, and main's app runner fails `PLAYER_SCRIPT_REQUIRED` (final) when it cannot solve.
  F5's page replay therefore serves a real failure only where main's verdict outranks the bot check,
  and otherwise the trial's found page (run 9's `routeb` check, §9 item 3). Tests: `RouteBSimTest`
  +4, `BrowserMasterFlowTest` +3 (its stand-ins extended, nothing removed); `extractor-master` 731
  (2 skipped), `extractor-sites` 280 and the app's unit tests 992 (66 skipped) pass locally.
- **D7 run 9 phone check (owner, 2026-10-19; run 39107280386).** V1 Rick Astley `dQw4w9WgXcQ`, V2
  Me at the zoo `jNQXAC9IVRw`, V3 Big Buck Bunny 60fps 4K `aqz-KE-bpKQ`. `normal`: V1–V3 rows, no
  notice; downloads V1 360p, V2 240p and V3 720p, 480p, 360p complete (twice). `routeb`: each first
  open is the trial's P17 skip (`main's answer reused: its remembered answer (P17)`, limit (b)); Try
  again gives Master's rows (V2 / V3 `master: route B after RESPONSE_CHANGED; replayed 3; new 0`).
  V1 360p (11 MB) and V2 240p (725 KB) download; V3 720p, 480p and 360p all end "Link expired"
  (`NEEDS_REFRESH`) before any byte, again on a second try. The Downloads screen showed no reason
  (Details was offered for `FAILED` only). Reading of the code: "Link expired" is a link that expired
  by the clock or a 401 / 403 / 410 answer, or a link lost on a restart. Master's rows come from
  main's replayed answers (0 new requests) and the same request context; the one difference found is
  the proof-of-origin token: main's extractor adds `pot` (BotGuard, `BotGuardPoTokenProvider`) to the
  media links of the page and mweb clients, Master's module has `NoPoTokenProvider` (Phase 1.4 scope:
  no tokens). V1's rows likely come from visionOS (run 8: `replayed 1`; no token needed); V2's
  725 KB file may finish before YouTube refuses a link without a token. Likely V3's rows are page or
  mweb links YouTube refuses (403) without the token; F6 shows it. (Run 10: wrong — V3's link is
  visionOS's; see below.)
- **F6 — what a "Link expired" download was (2026-10-19, debug diagnostics, `[d7]` run 10).** (1)
  `DownloadQueue.failTask`: when a download of a YouTube media link (host `googlevideo.com`) fails,
  its failure detail starts with the link's facts (`GoogleVideoLinkFacts`), then the engine's detail:
  `WEB itag 136+140 · pot no · sig yes · ends 352 min (app 352) · 0 KB · 3 s; 403 text/plain len 0`
  — the client (`c`), the stream numbers (video first), whether the link carries `pot` and a solved
  signature, the minutes the link's `expire` and the app's plan had left, the bytes and the task's
  age. Names and numbers only: never the link, its host or a value (sanitized, 120 characters). (2)
  The app offers Details (button and menu) for a `NEEDS_REFRESH` download too: reason, stage, HTTP
  status, detail. One whose link was never tried (lost on a restart) says `Reason: Link not tried
  (…)`. No extractor or locked file changed; the flag, the trial and the rows are unchanged. Tests:
  `GoogleVideoLinkFactsTest` (4), `DownloadQueueTest` +1, `DownloadLabelsTest` +1,
  `DownloadsScreenTest` +1.
- **D7 run 10 phone check (owner, 2026-10-19; run 39233263437).** `routeb` V3 (Big Buck Bunny) 360p
  (134+140), "Link expired" → Details: `Reason: Access denied (ACCESS_DENIED)`, `Stage: CONNECT`,
  `HTTP status: 403`, `Detail: VISIONOS itag 134+140 · pot no · sig yes · ends 359 min (app 358) ·
  0 KB · 0 s`, first byte 0.4 s. A fresh visionOS link, refused at once. Run 9's token reading was
  wrong: main adds `pot` only to the page and mweb clients' links, never to visionOS's, so main's
  own visionOS links have none either; `sig yes` says nothing (every direct link carries `sig`).
  Code reading: V3's `replayed 3; new 0` is visionOS refused, the watch page, then visionOS again
  with the page's visitor data; Master makes its rows from those replayed answers with the same
  candidate and media-context code as main's (diffed; same headers: User-Agent, Referer, Accept).
  So Master's V3 links are likely main's own set-aside links of that lookup, which YouTube would
  refuse as well; `normal`'s V3 rows may come from another answer (a normal build shows no details
  for rows). F7 checks this on the phone.
- **F7 — Master's rows against main's set-aside rows (2026-10-19, route-B trial APK only, `[d7]` run
  11).** `AndroidBrowserMasterFallback.trialPrimary` keeps the rows it sets aside (memory, by video,
  that lookup only); when route B answers, `RouteBTrialCheck` appends to route B's line (the
  browser's notice) `; check: <status>×<count>` — one one-byte ranged GET of each distinct file of
  Master's rows (≤ 8; the download's first request: the row's headers, `Accept-Encoding: identity`,
  `Range: bytes=0-0`, 8 s) — then `; same as main's: X of Y rows, headers same` (or `headers differ
  (<names>)`), and for a link that is not main's: `; differ: <query names>`, `; Master's only: …`,
  `; main's only: …` (against main's link of the same `itag`), `; streams not in main's: N` and
  `; main's check: …`. Names, statuses and counts only: never a link, a host or a value (odd names
  `?`, ≤ 6 names). Every other build: nothing set aside, no check, the line as before. No extractor
  or locked file changed. Tests: `RouteBTrialCheckTest` (4), `BrowserMasterFallbackTest` +1,
  `BrowserMasterFlowTest` (its 2 trial notices carry the comparison); the app's `detection.*` and
  `feature.browser.*` tests (332) pass locally.

## 8. Review follow-ups (2026-10-18)

A review of R1–R6 kept route B behind the flag and found one gap and two open questions. One step per
commit, tests + CI green before the next; §6's stop rules hold.

- **F1 — script 429 (done, §7).** The player-script client follows route B's rules; a 429 on a
  script fetch, main's or Master's, stops route B.
- **F2 — why route B was skipped (done, §7).** A lookup where route B does not run gets a Details
  line saying why (no record; a record without main's requests: a reused cached answer or a joined
  shared lookup, P17; a final reason; no page to ask or another page; the page changed), shown in the
  trial's sheet. D7 run 8 (§7): first opens said `main's answer reused: its remembered answer (P17)`;
  the joined lookup never showed.
- **F3 — the joined lookup (deferred, 2026-10-18).** A lookup that joined another caller's shared
  lookup records into that caller's record, so its own record has no request of main's and route B is
  skipped, also after a real failure. D7 run 8 showed only the remembered answer, so F3 is not built
  now; not seen is no proof of no risk — reopen when a Details line says `another caller's running
  lookup (P17)`.
- **F4 — a failure midway (offline; after F5; done 2026-10-18, §7: tests only, no bug found).**
  App-flow tests (`BrowserMasterFlowTest`, `BrowserMasterFallbackTest`; the engine and the
  simulator where they need it) where main asks and then fails with a recoverable reason (§2's set)
  — not the trial's found page — check three things:
  (1) Master's rows reach the sheet with route B's notice and counts: from main's replayed visionOS
  answer (a moved shape: L, 0 new player requests) and, with F5, from main's replayed watch page
  after a bot-checked visionOS answer (0 new pages); (2) when Master finds nothing, main's own
  failure stays (reason, message, Try again) with Master's details after main's; (3) no route B on
  main's final reasons (`BOT_CHECK`, `LOGIN_REQUIRED`, `PLAYER_SCRIPT_REQUIRED`, `RATE_LIMITED`, …),
  nothing new after main's 429, main's success untouched, flag off = today (P12). This covers R5's
  open check (4) offline. Tests first; a bug they find is fixed in the same step (§6).
- **F5 — main's watch page for route B (done 2026-10-18, §7; the owner's cookie decision; before
  F4).** Limit (a): after a bot-checked visionOS answer (run 8: V2, V3) main's rows come from the
  watch page main read with the tab's cookie; that answer is never kept and Master's own (cookie-less)
  GET of the page is refused (rule 4: main asked it), so route B can never help there, also after a
  real failure. Change
  (`extractor-master` `lookup` only; no locked file): the lookup's record also keeps main's answer to
  the lookup's watch page GET (the looked-up video's page address, one per lookup) when main's
  request carried the cookie — kept as rule 2 keeps main's other answers (Set-Cookie dropped, inside
  the caps, dropped at the lookup's end) but keyed without the cookie (its value is neither kept nor
  compared); Master's GET of that address (same method, address and body; same headers but
  `Cookie`) is answered from it once — replayed, counted, never sent. Unchanged: Master never sends a
  cookie or another authorization (rule 1 refuses every other authorized request); no other
  authorized answer is kept; Master's new requests carry at most what main's own requests from that
  page carried (its visitor data, P22), never the cookie, an authorization, the account's data-sync
  identifier or a PO token; a 429 stops new requests; ≤ 1 watch page per lookup (guardrail 7);
  nothing logged; flag off and every other build unchanged (no record). Not done: a cookie-less
  watch page asked again (a second page per lookup, more 429 risk, and likely the same bot check
  the cookie-less visionOS request met). Tests: `LookupRecordTest` and `RouteBHttpClientTest` (main's
  page kept for main only, once, within the caps and the lookup; other authorized answers never kept;
  another address refused; no cookie in any text), `RouteBSimTest` (bot-checked visionOS: Master
  complete from main's page, 0 credentials sent, ≤ 1 page), `BrowserMasterFlowTest` (the trial on a
  V2-like page: Master's row and `replayed 2`). Push with `[d7]` (`.github/d7-apks.txt` `run 9`).

## 9. Final plan (2026-10-18, after D7 run 8)

The owner (2026-10-18): the cookie rule is the agent's call ("do what works", approved); this plan is
pushed docs only and the next agent goes on from it (prompt: `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md`,
follow-ups). Decisions:

| Item | Decision | Why |
|---|---|---|
| F3, the joined lookup | Deferred; reopen on an `another caller's running lookup (P17)` line | Run 8 showed only the remembered answer |
| Limit (b), the trial's first open | Unchanged: tap Try again; no cache change | Trial only: a real failure is never remembered, and Try again asks afresh |
| Limit (a), the cookie rule | F5: Master reads main's watch page from the lookup and never sends a cookie (done, §7) | 0 new requests, ≤ 1 page per lookup; Master's request context holds the tab's cookie, its module drops it (`cookie = null`) and never sends it |
| A cookie-less watch page | Not done | A second page per lookup (guardrail 7), more 429 risk, likely the same bot check |
| F4 | Done 2026-10-18 (§7): tests only, no bug found, no `[d7]` | Proves the real-failure path and R5's open check (4), offline |
| Promotion (§5.5) | The owner's, after F5, F4 and run 9 | Flag on in normal builds changes real lookups |

Order (one step per commit, tests + CI green before the next; §6's stop rules hold):

1. **F5** (§8; done 2026-10-18, §7): push with `[d7]` (`.github/d7-apks.txt` `run 9`), so the APKs
   are ready for the owner.
2. **F4** (§8; done 2026-10-18, §7): tests; fix only what they find. No `[d7]` unless app or
   extractor code changed (then the run number moves on and the owner tests the latest run). The
   tests found nothing to fix: no code changed, no `[d7]`, so run 9 (F5's APKs) is the latest.
3. **D7 run 9 (⏸ owner):** (1) `normal`: V1–V3 as before, no notice. (2) `routeb`: V1–V3 from the
   address bar; if Details say `main's answer reused: its remembered answer (P17)`, tap Try again
   once. Expected: Master's rows with `master: route B after RESPONSE_CHANGED; replayed N; new M` on
   all three (V2 / V3 now N ≥ 2: visionOS and the page); per video download one AVC row with sound:
   it plays, duration ±2 s of the page's, height as its row says (wrong rows 0). (3) No rows: send
   Details; an `another caller's running lookup` line reopens F3. **Done 2026-10-19 (§7):** `normal`
   ✅; `routeb` Master's rows on V1–V3 after Try again, V1 / V2 download, V3's 720p / 480p / 360p all
   "Link expired" before any byte → F6.
3a. **F6** (§7; done 2026-10-19): Details on a "Link expired" download, with the link's facts; `[d7]`
   (`.github/d7-apks.txt` `run 10`).
3b. **D7 run 10 (⏸ owner):** `routeb`: V3 (Try again if asked) → download any quality → Downloads →
   the "Link expired" item → Details → screenshot. `pot no` with HTTP 403 on a page / mweb link
   confirms the missing token; then the owner chooses: **A** Master uses main's token maker (scope
   change), **B** route B drops rows that need a token it does not have, **C** a known limit. Another
   reading (the clock, a restart, 410) is fixed in the app or Master first. **Done 2026-10-19 (§7):**
   a visionOS link, 403 at once, `pot no` — not the token (visionOS links never get one) → F7.
3c. **F7** (§7; done 2026-10-19): the trial's notice checks Master's files once and compares Master's
   rows with main's set-aside rows; `[d7]` (`.github/d7-apks.txt` `run 11`).
3d. **D7 run 11 (⏸ owner):** `routeb`: V3, then V1 (Try again if asked) → screenshot the notice
   (`master: route B after …; check: …; same as main's: …`). Readings: (a) `same as main's: Y of Y
   rows, headers same` with `check: 403×…` — Master's links are main's own and YouTube refuses that
   lookup's links (main's visionOS-again answer): not route B; main's extractor is a STOP
   (extractor-sites), so the owner chooses (a known limit, or a main fix later). (b) `check: 206×…`
   but the download still fails — a download-time difference, fixed in the app. (c) not the same
   (`differ: …`) — Master's links differ from main's: fixed in Master (the names say where; `main's
   check` says whether main's would download).
4. **Promotion (owner, §5.5).** Suggested gate: F5 and F4 green; run 9 / 10 give Master's rows on V1–V3
   that play, wrong rows 0, `normal` unchanged; Master sent nothing with a cookie (tests). Promotion
   turns `-Pyft.youtubeRouteB` on in normal builds; the trial switch stays debug only.
