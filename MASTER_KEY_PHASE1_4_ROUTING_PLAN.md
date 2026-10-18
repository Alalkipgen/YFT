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
replayed visionOS answer was a bot check and the module's watch page (with the tab's cookie) was
refused, limit (a); first opens skipped on a remembered answer (P17, limit (b), trial only); the
joined lookup (F3) never showed. **Final plan (§9, 2026-10-18):** F3 deferred; **F5** (the owner's
cookie decision: Master reads main's watch page from the lookup, never sends a cookie) → **F4** (a
real failure midway, offline) → D7 run 9 (⏸ owner) → promotion (flag on in normal builds), the
owner's decision (§5.5).
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
  the module's watch-page GET, which carries the tab's `Cookie` (`YouTubeExtractor`
  `pageHeaders(request.requestContext)` → `LookupAsk.authorized`), so `RouteBLookup` rule 1 never
  sends it and it reads as `NETWORK`. (4) Main gave no bot check, sign-in or try-later on these
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
  Limit (a) as in R5: the module's watch page GET carries the tab's cookie and rule 1 refuses it. §2
  held: wrong rows 0, nothing with a cookie sent, main's failure kept whenever Master had no rows.
  V1's `new 0` (R5: `new 1`): Master's rows came from the replayed visionOS answer alone.
- **Item 7 status (2026-10-18): built behind the flag, not promoted.** `-Pyft.youtubeRouteB` stays
  off in every normal build (flag off = today, P12); promotion is the owner's decision (§5.5).
  Limits seen on the phone: (a) when the replayed visionOS answer is a bot check (or another
  unplayable answer), the module's next step, the watch page, carries the tab's cookie and is never
  sent (rule 1), so route B gives no rows and main's failure stays; (b) a lookup answered from
  main's reused answer (P17's shared lookup or cache) has no request of main's in its record, so
  route B is skipped and the trial needs Try again (F2: Details now say so, and which reuse). Next (§9, final plan): (a) → F5, Master
  reads main's watch page from the lookup (no cookie-less page); (b) stays (trial only, Try again);
  then F4 and D7 run 9.

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
- **F4 — a failure midway (offline; after F5).** App-flow tests (`BrowserMasterFlowTest`,
  `BrowserMasterFallbackTest`; the engine and the simulator where they need it) where main asks and
  then fails with a recoverable reason (§2's set) — not the trial's found page — check three things:
  (1) Master's rows reach the sheet with route B's notice and counts: from main's replayed visionOS
  answer (a moved shape: L, 0 new player requests) and, with F5, from main's replayed watch page
  after a bot-checked visionOS answer (0 new pages); (2) when Master finds nothing, main's own
  failure stays (reason, message, Try again) with Master's details after main's; (3) no route B on
  main's final reasons (`BOT_CHECK`, `LOGIN_REQUIRED`, `PLAYER_SCRIPT_REQUIRED`, `RATE_LIMITED`, …),
  nothing new after main's 429, main's success untouched, flag off = today (P12). This covers R5's
  open check (4) offline. Tests first; a bug they find is fixed in the same step (§6).
- **F5 — main's watch page for route B (the owner's cookie decision, 2026-10-18; before F4).**
  Limit (a): after a bot-checked visionOS answer (run 8: V2, V3) main's rows come from the watch
  page main read with the tab's cookie; that answer is never kept and Master's own GET of the page is
  refused (rule 1), so route B can never help there, also after a real failure. Change
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
| Limit (a), the cookie rule | F5: Master reads main's watch page from the lookup and never sends a cookie | 0 new requests, ≤ 1 page per lookup; Master already holds the tab's cookie in its request context and still never sends it |
| A cookie-less watch page | Not done | A second page per lookup (guardrail 7), more 429 risk, likely the same bot check |
| F4 | Needed, after F5 | Proves the real-failure path and R5's open check (4), offline |
| Promotion (§5.5) | The owner's, after F5, F4 and run 9 | Flag on in normal builds changes real lookups |

Order (one step per commit, tests + CI green before the next; §6's stop rules hold):

1. **F5** (§8): push with `[d7]` (`.github/d7-apks.txt` `run 9`), so the APKs are ready for the
   owner.
2. **F4** (§8): tests; fix only what they find. No `[d7]` unless app or extractor code changed (then
   the run number moves on and the owner tests the latest run).
3. **D7 run 9 (⏸ owner):** (1) `normal`: V1–V3 as before, no notice. (2) `routeb`: V1–V3 from the
   address bar; if Details say `main's answer reused: its remembered answer (P17)`, tap Try again
   once. Expected: Master's rows with `master: route B after RESPONSE_CHANGED; replayed N; new M` on
   all three (V2 / V3 now N ≥ 2: visionOS and the page); per video download one AVC row with sound:
   it plays, duration ±2 s of the page's, height as its row says (wrong rows 0). (3) No rows: send
   Details; an `another caller's running lookup` line reopens F3.
4. **Promotion (owner, §5.5).** Suggested gate: F5 and F4 green; run 9 gives Master's rows on V1–V3
   that play, wrong rows 0, `normal` unchanged; Master sent nothing with a cookie (tests). Promotion
   turns `-Pyft.youtubeRouteB` on in normal builds; the trial switch stays debug only.
