# Master Key — Phase 1.4 backlog item 7: YouTube routing B (plan)

**Status:** R1 built (2026-10-17; §7 "As built"): the per-lookup recorder, flag off = today. R2–R6
next, one commit each. Plan written 2026-10-17 (docs only).
Backlog pointer: `MASTER_KEY_PHASE1_3_PLAN.md` §10 item 7. Same contract as the social sites:
`MASTER_KEY_SOCIAL_PLAN.md` §2.1 decisions #7 (routing B / B+ / A) and #8 (lookup = main + Master,
per-lookup request recorder). Agent prompt: `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md`.

**မြန်မာ အကျဉ်း:** ယနေ့ YouTube Master ladder (A/S/Z/L/H/O, V) က user ရဲ့ lookup တွေကို မဖြေရသေး —
main က page ကို တွေ့ရင် (UNSUPPORTED_URL မဟုတ်ရင်) engine က Skipped ပေးတယ် (P12 "video တစ်ခု lookup
တစ်ခု")။ ဒီ plan: main ကျရှုံးပြီး "ပြန်ကြိုးစားလို့ရတဲ့" အကြောင်းပြချက် (ဥပမာ RESPONSE_CHANGED) ဆိုမှ Master
ဖြေမယ် (route B)၊ debug flag နောက်မှာ (default off = ယနေ့နဲ့ အတူတူ)။ main အောင်မြင်ရင် မထိ၊ final
အကြောင်းပြချက် (BOT_CHECK၊ LOGIN၊ 429 …) တွေ final ဆက်ဖြစ်။ main မေးပြီးသား အဖြေကို ထပ်မမေးဘဲ memory ထဲက
ပြန်သုံး (recorder)။ အဆင့် R1–R6၊ commit တစ်ခု တစ်ဆင့်၊ CI green။ ဖုန်းစစ်ပြီးမှ promote။

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
  from memory, not asked again; authorized (cookie) answers are never kept or replayed; after a 429
  in the lookup no new YouTube request; new requests only from rungs main lacks (A/O/H/V) within
  existing budgets (guardrail 7; D1 target H2 ≤ 1 player request, complete).
- Out of scope (Phase 1.3 §8 kept): SABR, PO tokens, MSE, TV, bypass, cookies / sign-in, main merge
  and release. **B+** (Master adds formats main lacks) and **A** (Master primary, separate flag,
  ground rule 2) are later items, not this one.
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
