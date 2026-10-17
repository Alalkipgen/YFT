# Master Key — Next level for Instagram, TikTok, Facebook and X (final plan)

Status: **final plan** — the owner's Plan Mode request (2026-10-17), reviewed and approved by the
owner as the final plan the same day. This document changes no code. Decided: routing, lookup
scope and the R8 ladder (§2.1, §8 items 7–9). Still open: §8 items 1–6. Nothing here is built
until the owner says go, one step at a time (§7, §8).

## 0. အကျဉ်းချုပ်

- **အဖြေ: သုံးသင့်ပါတယ်။** ဒါပေမဲ့ YouTube လောက်ကြီးတဲ့ extractor ၄ ခုကို သီးသန့် မရေးပါဘူး။
  **Next Level kit တစ်ခု (site အားလုံး မျှသုံး)** နဲ့ site တစ်ခုစီအတွက် **site pack ပါးပါး** (contract
  recipe၊ answer judge rule၊ post နဲ့ ချိတ်တဲ့ shape anchor၊ row check host — အများစု data) ကို generic
  engine ထဲမှာ ထည့်ပါမယ်။ main adapter ကို မကူးပါ (rung-0 copy ~4,700 lines မလုပ်တော့ပါ)၊ site ကို
  သိမ်းယူတဲ့ (claiming) module လည်း မလုပ်ပါ။
- **Final ဆုံးဖြတ်ချက် (ပိုင်ရှင်၊ 2026-10-17; §2.1၊ §8 #7–#9):**
  - **#7 Routing = B:** site ၄ ခုလုံးမှာ main က အရင်ရှာပြီး **main မအောင်မြင်မှ** Master လုပ်ပါတယ်။
    main အောင်မြင်ရင် main ရဲ့ row တွေ ဒီအတိုင်း ထွက်ပါတယ်။ နောက်မှ **B+**: main ရဲ့ အဖြေ အားနည်းရင်
    (HD မပါ၊ အသံမပါ စတဲ့ တိုင်းလို့ရတဲ့ signal) Master က မပါတဲ့ format ကိုပဲ ဖြည့်ပြီး main row တွေကို
    မထိပါ။ **A** (Master က main နေရာ အစားထိုး) ကို B+ မလုံလောက်ကြောင်း တိုင်းပြနိုင်မှသာ လုပ်ပါမယ်။
  - **#8 Lookup = user ရဲ့ ရှာမှု တစ်ခုလုံး (main + Master):** app ဘက်က per-lookup request recorder
    (memory ထဲမှာပဲ) က main မေးခဲ့တာနဲ့ 429/bot check ပြန်တဲ့ host ကို မှတ်ထားလို့ Master က
    ထပ်မမေးပါ၊ အဲဒီ host ကိုလည်း ဆက်မမေးပါ။ main adapter code ကို မပြင်ပါ။
  - **#9 R8 ladder:** "တစ်ခါပဲ မေး" အစား budget ပါတဲ့ လှေကားသေးသေး ဖြစ်ပါမယ်။ rung အသစ်တိုင်း
    ပိုင်ရှင် go လိုပါတယ်။
  - **"main ထက် ဘယ်တော့မှ မညံ့" ကို အာမမခံတော့ပါ:** policy တူရင် regression မရှိပါ (main အောင်မြင်ရင်
    မထိလို့)။ ရည်ရွယ်ချက်ရှိရှိ ကွဲတဲ့ policy (third party မသုံး၊ V မအောင်ရင် row မပြ၊ 429 ပြီးရင် ရပ်)
    ကို site တစ်ခုစီအလိုက် စာရင်းပြပါမယ် (§4 S1)။
- **ဒီနေ့ အခြေအနေ:** main adapter ၄ ခုက လမ်းတွေကို အစဉ်တစ်ခုတည်းနဲ့ အမြဲ မေးပါတယ်။ ဘယ်လမ်း
  အလုပ်ဖြစ်ခဲ့လဲ မမှတ်ပါဘူး။ row တွေ ဖွင့်လို့ရမရကို TikTok တစ်ခုပဲ စစ်ပါတယ်။ Master generic က
  main ပျက်မှ embed endpoint တစ်ခု မေးပြီး page/capture layer တွေကို ဖတ်ပါတယ်။ **IG နဲ့ X မှာ
  Master က main မေးပြီးသား endpoint ကိုပဲ ထပ်မေးနေလို့ တကယ့် ဒုတိယလမ်း မရှိပါဘူး။**
- **YouTube ပုံစံနဲ့ ယှဉ်ရင် မရှိသေးတာ:** answer judge၊ ladder + health (နောက်ဆုံး အလုပ်ဖြစ်ခဲ့တဲ့လမ်းကို
  မှတ်ခြင်း၊ cooldown)၊ own parser (key နာမည် ပြောင်းသွားလည်း တန်ဖိုးပုံစံနဲ့ ရှာခြင်း)၊ row တိုင်း V
  check၊ census နဲ့ weekly check (မပျက်ခင် ကြိုသိခြင်း)၊ self-healing value (IG `doc_id`၊ X token)၊
  APK အသစ်မထုတ်ဘဲ ပြင်နိုင်တဲ့ remote recipe (တည်ဆောက်ပြီးသား၊ live မဖြစ်သေး)။
- **ပြန်သုံးလို့ရတာ (§3):** လိုအပ်တာရဲ့ **သုံးပုံနှစ်ပုံလောက် (ခန့်မှန်း)** က ရှိပြီးသားပါ — main adapter
  ၄ ခု (မကူးဘဲ main အဖြစ် အရင် run)၊ FB DASH reader၊ embed recipe တွေ၊ capture၊ download engine
  (DASH/HLS/merge)၊ `InitCheck`၊ probe/V စနစ်၊ R9၊ parity/drift check၊ D7 APK matrix။ အသစ်ရေးရမှာက
  site judge rule တွေ၊ post နဲ့ ချိတ်တဲ့ parser binding၊ site census၊ request recorder နဲ့ ချိတ်ဆက်တဲ့
  code ပါ။ YouTube ဖိုင်တွေကို မပြင်ပါ။
- **Layer 0–8 (§5):** policy → site table → answer judge → own parser → ladder/health → row check →
  self-heal → early warning → repair channel။
- **အစဉ် (§7):** S0 recorder + census (တိုင်းတာမှုသက်သက်) → S1 kit (ပထမ site အတွက် လိုတာပဲ) → pilot
  site pack (Facebook သို့ TikTok — တကယ့် ဒုတိယလမ်း ရှိပြီးသား၊ S0 ရဲ့ ပြန်ကယ်လို့ရတဲ့ failure
  အချိုးနဲ့ ရွေး) → ကျန် site (IG/X က ထပ်မေးတာ အရင်ရပ်) → weekly check → remote recipe live →
  နောက်မှ B+။
- **ဆုံးဖြတ်ပြီး:** §8 #7–#9။ **ပိုင်ရှင် ဖြေရန် ကျန်:** §8 #1–#6။

## 1. Audit — what exists today

### 1.1 The model: Master YouTube's layers

| Layer | Master YouTube piece |
| --- | --- |
| Parity floor | main's five YouTube files copied byte for byte (drift check) behind `MasterYouTubeModule` |
| Identity table (data) | `ladder/ClientTable`: ≤ 4 identities, status per identity, ≤ 2 alternates with source and date |
| Answer judge | `ladder/AnswerJudge` + `CensusModel` classes (`DIRECT`, `BOT_CHECK`, `VERSION_REJECTED`, …) |
| Ladder | `ladder/ClientLadder` (rungs and variants A, S, H, L, Z), debug flag `-Pyft.clientLadder` |
| Health | `ladder/ClientHealth`: last class, last pass, cooldown, learned version, shape map (stored through `OwnSolverTextFile`) |
| Own parser | `ShapeLocator` (value-shape scan) → `ShapeNormalizer` (bind to the video → canonical JSON → copied parser), `ShapeFingerprint`, `LocatedShapeMap` |
| Own solver | `solver/` (player script; YouTube only) |
| Self-healing values | `VersionSource`, `CensusSources` (public source, strict pattern, once a day, kept after OK + V) |
| Row checks | `StreamCheck` (V), `InitCheck`, `HlsCheck` / `HlsLeg`, rung Z (sizes) |
| Budget and memo | `LookupMemo`, budgets, stop rules after a bot check |
| Early warning | `ClientCensus` (full census, weekly check W, A check) + `CensusDiff` (CHANGED / BREAKING lines, runbook) |
| Live proof | `LadderTrial` (skip / mutate switches) + the D7 phone APK matrix |

### 1.2 Main's four adapters (`extractor-sites`)

| Site | Lines | Ways asked, always in this order | Row check | Hard-coded values | Weak point |
| --- | --- | --- | --- | --- | --- |
| Instagram | 740 | app API (only with the browser's session) → web GraphQL → post page → public embed | none | the GraphQL `doc_id`, app ID, ASBD ID | the `doc_id` rotates; sign-in walls; 429; the same order every lookup |
| TikTok | 1,585 | the tab's (or hidden page's) post data → phone page → desktop page → hidden page → `tikwm.com` (public download service, last) | one-byte check per quality (≤ 8) | page-data block and key names, status codes | a renamed data block; bot checks; a third-party service as the last way |
| Facebook | 1,842 | public Safari page (reels) → session page → AVC ladder | none (link expiry is read) | media key lists (`MEDIA_KEYS`, `LEGACY_FIELDS`) | renamed page-data keys; sign-in walls |
| X | 523 | the embed widget's answer (`tweet-result` with the widget token) | none | the token arithmetic, one endpoint | one way only: if it breaks, main has nothing |

### 1.3 Master today for the four ("generic")

- No site module: `modules/` holds YouTube only.
- After a main failure that is not final (`policy/TerminalRules`), `MasterFallbackEngine` runs the R8
  contract stage — one ask per site from `recipes/ContractRecipes`:
  - **X:** the same `tweet-result` endpoint main already asked (no second way).
  - **Instagram:** the same embed page as main's last way (no second way).
  - **Facebook:** `plugins/video.php` (a real second way).
  - **TikTok:** `embed/v2` (a real second way).
- Then layers L2 → L4 → L1 → L3 on page data and on the user's own playback in the visible browser
  (capture), then `CandidateGate` and `ProbeSession`.
- Sign-in or bot check (`BROWSER_REQUIRED`): Master reads only what the user's own playback delivered.
- R9 remote recipes are built (signed, data only), but no hosted config exists yet, so they are not live.
- R1 live parity list (owner-run): Facebook 5, Instagram 3, TikTok 4, X 4 links.
- **Routing today:** for these sites Master runs only after main fails (`BrowserViewModel` sends a
  `SiteAdapterOutcome.Failed` to `BrowserMasterFallback.recover`). A page claimed by a module
  (YouTube) is answered only when main returned `UNSUPPORTED_URL` (P12, `MasterFallbackEngine`), which
  YouTube's main returns only for an invalid video ID. Phone run 3 hit this (`PROGRESS.md`, "Run 3 on
  the phone": "Master runs only when the primary adapter fails"); the fix was the debug-only
  `masterFirstSite` (h-only APK), which hands a found YouTube page to Master as `UNSUPPORTED_URL`
  (two lookups). A module with a copy of main as rung 0 would never answer these four sites in normal
  builds.
- **Stop rules today:** `TerminalRules.NEVER_FALLBACK` (`RATE_LIMITED`, `NETWORK`, …) reads only main's
  final reason, and `MasterRequest` carries only `primaryFailure`. Instagram's main asks its next way
  after a 429; a later 404 or 5xx makes the final reason `RESPONSE_CHANGED`, so Master never sees the
  429.

### 1.4 Gaps: the YouTube layers against the four sites

| Layer | Instagram | TikTok | Facebook | X |
| --- | --- | --- | --- | --- |
| Answer judge (class → next step) | failure reason only | status codes (`TikTokStatusRecipe`) | failure reason only | failure reason only |
| Ladder + health (last good way first, cooldown) | no | no (fixed order) | no | no (one way) |
| Own parser (find by value shape, bound to the post) | no (generic L3 only) | no | no | no |
| V on every offered row | no | yes (one byte) | no | no |
| Init check / size from the check | no | no | no | no |
| Self-healing values | `doc_id`: no | data block found by content: no | n/a | token: no |
| Census + weekly check W | no | no | no | no |
| Repair without an APK (R9 live) | no | no | no | no |
| Second independent way in Master | no | yes (embed) | yes (embed) | no |

## 2. The answer and the shape of the build

**Yes**, as one shared **Next Level kit** and four **thin site packs inside the generic engine** — not
four YouTube-size copies, and no module that claims a site.

1. Each site pack (mostly data): the site's R8 contract recipes (data, fixable through R9), its
   judge rules, L3 anchors bound to the post, the media hosts for row checks, and its extra rungs
   (§6). Main's adapter is **not copied** (no rung 0, no ~4,700 copied lines): main answers first,
   as today, and Master acts only after main fails (decision 7, §2.1). The generic layers and capture
   stay after the pack. Repeats inside one lookup are stopped by the per-lookup request recorder, the
   ledger and the stop rules (decision 8); the memo only replays successes. (YouTube's module, R6, is
   not the model: it answers only after main returned `UNSUPPORTED_URL`, so in normal builds it
   practically never answers, §1.3.)
2. **Measured first** (S0 census), as YouTube's D0 was.
3. **Behind a debug flag first** (`-Pyft.socialLadder`), proven on the phone with a D7-style APK matrix,
   then promoted per site only on the owner's go.
4. **Not ported:** the own solver (these sites do not cipher their media links, and signing their APIs
   — e.g. TikTok's request signatures — is excluded), client versions (their counterpart here is a
   self-healed request value), SABR / PO tokens.

### 2.1 Final decisions (the owner, 2026-10-17)

**Decision 7 — routing: B first, B+ later, A only if measured.**

| Route | When Master acts | Main's rows | Status |
| --- | --- | --- | --- |
| **B** | only after main fails with a reason that is not final (`TerminalRules`), all four sites | returned untouched when main succeeds | decided: build first |
| **B+** | also when main's answer is weak by an objective signal (no HD row, no sound row) | untouched; Master adds only the formats main lacks (YouTube's merge rule, guardrail 9 in `ClientLadder`), inside the budget | later, on the owner's go, after B's phone proof |
| **A** | in place of main | replaced | only if B+ is measured insufficient |

- **Why B:** "no regression" becomes structural (main's success is never touched), the copies are not
  needed, and today's engine order already fits (R8 contract → page data → capture,
  `MasterFallbackEngine.run`). Facebook's `plugins/video.php` and TikTok's `embed/v2` are real second
  ways today; X's `tweet-result` and Instagram's embed repeat main's ask, so under B their first fix
  is to stop that repeat.
- **If A is ever needed:** a real "Master-owned" route state in the app and the engine. Never through
  `ADAPTER_DISABLED` (it is in `TerminalRules.NEVER_FALLBACK`, so Master would never run) and never by
  relabeling a found page as `UNSUPPORTED_URL`, as the debug-only `masterFirstSite` does.
- **One project-level contract:** the routing rule covers YouTube too (its `masterFirstSite` stays a
  debug-only trial); a change to it is one decision for all sites, not a per-site trick.

**Decision 8 — "lookup" = the user's whole lookup (main + Master), seen by a request recorder.**

- An app-side **per-lookup request recorder** wraps the HTTP client that main's adapters already use
  (no main adapter code change; scoped to the lookup, e.g. through the coroutine context). Memory
  only (S8): the hosts that answered 429 or a bot check, short hashes of the requests asked, the time
  left.
- Master's contract stage reads it: an endpoint main already asked is not asked again; a host that
  answered 429 or a bot check gets no new request in this lookup (S7).
- All kit requests go through one per-lookup client: the memo replays successes; the ledger and the
  stop rules count attempts.
- The same recorder feeds the S0 census (per site: which way failed, how, and how often a failure was
  recoverable).
- For these sites this replaces R8's "Cost accepted: after a main failure the same endpoint may be
  asked a second time (Vimeo, X)" (`MASTER_KEY_ARCHITECTURE.md`).

**Decision 9 — R8 becomes a small bounded ladder, per site pack.**

- R8's single ask ("asks once … never twice") becomes, for these four sites, a small budgeted ladder
  in the contract stage: the engine fetches; the parser layers only read and never fetch.
- Each new rung still needs the owner's go (S10); rungs, endpoints and key paths are data (S9).

## 3. Reuse — what already exists (owner's question, 2026-10-17)

Rule: **no Master YouTube file is edited** (the five copies, `ShapeLocator`, `ShapeNormalizer` and
`Rungs` are locked; the other YouTube files stay as they are so YouTube's proven behaviour cannot
move). Reuse is by calling a piece as it is, by copying it with a provenance row (drift check), or by
following its design in new kit code. Main's code reaches Master only as symbol copies with a
provenance row (R3, `toolkit-provenance.tsv`; `extractor-master` uses `extractor-sites` for tests
only); there is no whole-adapter copy (decision 7).

### 3.1 Reused as it is

| Piece | Where | Used for |
| --- | --- | --- |
| The four site adapters, their URL readers (`InstagramUrls`, `TikTokUrls`, `FacebookUrls`, `XUrls`) | `extractor-sites` (main) | main's own first answer, unchanged and not copied (B); the URL readers for the post's identity |
| Facebook DASH reader and offers (`FacebookDashManifest`, `FacebookDashOffers`) | main (Instagram already uses them) | DASH rows with their sound on Facebook and Instagram |
| `TikTokAgents`, `TikTokStatusRecipe`, `WidgetToken` (X token arithmetic) | main / Master | agents, status codes, X's token |
| Embed recipes and their reader (`ContractRecipes`, `ContractReader`) | Master generic | rung F1 (`plugins/video.php`), T1 (`embed/v2`); key tables, DRM signals |
| Toolkit (`AnchoredMediaWalk`, `MediaKeyTable`, `BalancedJson`, `PageScripts`, `HtmlScan`, `InlineDashReader`, `QualityLadder`, `UrlPolicy`) | Master generic | the base of the own parser (L3): the walk, JSON blocks in pages, inline DASH |
| Row checks: `InitCheck` (a pure check on bytes already read), `ProbeSession`, `OkHttpMediaValidator`, `CandidateGate`, `SegmentIndexReader`, `MediaFingerprint` | Master YouTube / generic | the init check on V's bytes, probe budget, gate, fingerprints |
| Capture: L1 `CaptureLayer`, the Android capture (`yft-master-mse.js`, codec steering, EME stop) | Master generic | rung F3 / T3 / I3 / X3 (the user's own playback after a sign-in wall or a check) |
| Download side: `DashTransferEngine`, `HlsTransferEngine` (audio playlists, packed audio), `AudioVideoMuxEngine`, `StreamCopyAudioVideoMuxer`, `Mp4StreamCopyReader`, `AdtsStreamCopyReader` | core-download | every new row type downloads with no new download code (DASH pairs, HLS, ADTS sound) |
| `TerminalRules`, `SnapshotBudget` | Master policy | L0 |
| R9 remote recipes (`RemoteRecipes`, `RemoteRecipeConfig`, `RemoteRecipeSignature`, `scripts/master-recipe-sign.sh`) | Master | L8 (only hosting is missing) |
| Parity harness (`ParityUrls`, `ParityReport`, `ParityVerdict`, `MasterParityLiveTest`, `parity-urls.json`), `FixtureBaselineTest`, `toolkit-provenance.tsv` + `scripts/master-toolkit-drift.py` | Master | no regression under the same policy (S1), drift of copied symbols |
| App: `SiteAdapterCoordinator`, `PageDataSources` and TikTok's hidden pages (`WebViewHiddenPages`), `InstagramHomeSession` / TikTok's home session (the browser's cookies), Show check, `BrowserMasterFallback` | app | wiring, page data for rungs, the user's own session, the user answering a check; the place of the per-lookup request recorder (decision 8) |
| CI: the D7 APK matrix (`master-optin-debug-apk.yml`, `.github/d7-apks.txt`) | repo | phone proof per rung (new matrix entries only) |

### 3.2 Reused with a small generalization (new kit code; the YouTube file stays unchanged)

| YouTube piece | What is YouTube-only in it | Kit counterpart |
| --- | --- | --- |
| `StreamCheck` (V) | only YouTube's media host (`YouTubeUrls.isMediaServerRequest`) | `SiteRowCheck`: the same rules (Range ≤ 64 KiB, the download's headers, returned hosts only, `unknown` ≠ pass) with the site's media hosts from the site table |
| `HlsCheck` | addresses must be on `googlevideo.com` | the same checks with the site's host (X's HLS) |
| `LookupMemo` | `googlevideo.com` is the media host it never keeps | the same memo with the site's media hosts (it replays successes only); main's asks are seen through the request recorder, attempts through the ledger and the stop rules (decision 8) |
| `ClientHealth` | ids of the YouTube client table only | `SiteHealth`: per site and rung (last class, last pass, cooldown, last good rung, learned value), stored the same way (`OwnSolverTextFile`) |
| `CensusModel` (`CensusJson`), `CensusDiff` | YouTube's classes and steps | the same JSON export (slots only) and diff (CHANGED / BREAKING + runbook) for site rungs |
| `VersionSource` + `CensusSources` | YouTube version headers and sources | `ValueSource`: the same rules (S5) for Instagram's `doc_id` and X's token |
| `LadderTrial` | YouTube rung names | the same skip / mutate switches with site rung names |
| `ClientCensusActivity` (debug screen) | YouTube's census modes | `SiteCensusActivity` (S0), same layout and export |

### 3.3 Design reused, code new (site-specific)

| YouTube piece | Why it cannot be called | New site code |
| --- | --- | --- |
| `AnswerJudge` | reads YouTube's player answer | one small judge per site (§6 signals) over the shared classes (§5 L2) |
| `ShapeLocator` / `ShapeNormalizer` / `ShapeFingerprint` | YouTube format fields (`itag`, `mimeType`, …), locked files | site anchors (post ID, media host, size fields) on top of the generic walk; fingerprint of each site's answer |
| `ClientLadder`, `ClientTable` | YouTube identities and rungs | one small ladder over the site table (rungs per site) |
| `ClientCensus` | YouTube's steps | the site census (S0) and W (S3) |
| `LookupMemo` + budgets (attempts) | they see only Master's own asks | the per-lookup request recorder (app side, memory only): main's and Master's asks, the hosts that answered 429 or a bot check, the time left (decision 8) |

### 3.4 Not reusable

- The own solver (`solver/`), `SignatureGate`, `HlsLeg`, rung Z's YouTube parts, PO-token and SABR
  pieces: they solve YouTube-only problems.

**Estimate:** about two thirds of what this plan needs exists already (3.1 + 3.2). The new code is mainly
the site judges, the post binding of the parser, the site census, the request recorder and the glue.
With no adapter copies (B), the build is also about 4,700 lines smaller than the first draft.

## 4. Guardrails (S1–S10)

- **S1 Flag and parity.** Flag off = today. Main's adapters are not copied (B) and not changed;
  parity tests unchanged; `FixtureBaselineTest` holds (rows and heights only grow); no Master YouTube
  file changes. "Never worse than main" is not a promise this plan can make; it is split into:
  (1) **no regression under the same policy and config** — under B this is structural (main's
  success is returned untouched); requests and time per lookup are still measured, and capture stays
  the last fallback; (2) **intended policy differences**, listed per site in each step's docs: no
  third-party service in Master (S4; main's TikTok `tikwm.com` way has no Master counterpart), a row
  only after V passes (S6; `unknown` is not a pass), and the stop rules (S7; e.g. Instagram's main
  keeps asking after a 429, Master does not).
- **S2 The user's own session only.** Only the cookies of YFT's browser, only to the site's own host
  (as main does), never to a CDN. YFT never signs in. No sign-in, age, private or region bypass;
  DRM → stop.
- **S3 No signing or tricks.** No request-signature generation, no device registration, no TLS,
  fingerprint, IP or proxy changes, no automatic captcha answers (the user answers a check in the
  visible browser, as main's "Show check").
- **S4 No third-party services in Master** (download sites, mirror APIs). Main's `tikwm.com` stays
  main's decision.
- **S5 Self-healed values** (guardrail 5's rule): only from a source S0 proved (the site's own page or
  script, or a public raw file such as yt-dlp's); one pattern-matched value, never code; at most once
  a day per value; kept only after an OK answer and a V pass; bundled alternates with source and
  date; never invented, counted up or brute-forced.
- **S6 Row checks.** V on every row Master offers; main's rows stay main's (B). V: Range ≤ 64 KiB,
  the download's own headers, the host the answer gave; the init check on the same bytes (container
  start, no encryption marker, size match); `unknown` is never a pass; an HLS row only with a known
  duration (±2 s).
- **S7 Budgets and stop rules.** "Lookup" = the user's whole lookup, main's asks and Master's
  (decision 8). Per lookup: a request, byte and time budget per site. After a bot check or HTTP 429
  (seen by the per-lookup request recorder, main's asks included): no new request to that host in
  this lookup, and a 30-minute cooldown for the rungs that send requests. No second ask of an
  endpoint already asked in the same lookup, by main or by Master (recorder hashes); the memo only
  replays successes, so the recorder and the ledger enforce this.
- **S8 Privacy.** No post IDs, links, titles or user names in docs, logs or census exports (slots and
  short hashes only, as the YouTube census does).
- **S9 Data over code.** Endpoints, key paths, header values and values like `doc_id` are data (site
  table, `ContractRecipes`, R9), so most fixes are data changes.
- **S10 Owner gates.** Each site pack, each new rung and each self-heal source starts only on the
  owner's go; a phone proof comes before "live".

## 5. Layers L0–L8

| Layer | What it does | Built from (§3) |
| --- | --- | --- |
| **L0 Policy** | routing B (Master only after main fails, decision 7), final answers, `BROWSER_REQUIRED`, walls, S1–S10 | `TerminalRules` + per-site stop classes + the request recorder's stop signals |
| **L1 Site table** (data) | per site: the rungs, each with status (live, provisional, measured but not active), the values it needs (with source and date), the expected class and its budget share | the `ClientTable` design |
| **L2 Answer judge** | one class per answer: `DIRECT`, `PARTIAL` (fewer heights), `LOGIN_WALL`, `BOT_CHECK`, `RATE_LIMITED`, `REMOVED`, `PRIVATE`, `AGE_LIMITED`, `REGION_LIMITED`, `PHOTO_ONLY`, `DRM`, `VALUE_REJECTED` (a stale `doc_id` or token), `SHAPE_CHANGED`, `TRANSIENT`; decides the next rung or the stop | the `AnswerJudge` design; main's reasons and `TikTokStatusRecipe` map in |
| **L3 Own parser** | value-shape scan for media objects (an https link on the site's media host with width, height, bitrate or codec; a DASH representation; an HLS playlist), bound to the post's ID (Instagram shortcode or pk, TikTok item ID, Facebook video ID, X post ID) → canonical rows (main's readers only as symbol copies with a provenance row, R3); reads only, never fetches; shape fingerprint and map recorded | toolkit walk + `InlineDashReader` + the `ShapeNormalizer` design |
| **L4 Ladder + health** | the contract stage's small ladder after main fails (decisions 7, 9): the last good rung first (health), then table order; one try per rung per lookup; cooldowns; budget; recorder + memo; Details lines name each rung's class | `SiteHealth`, memo (3.2), request recorder (3.3) |
| **L5 Row checks** | V + init check + size + link lifetime (Facebook/Instagram `oe`, TikTok `x-expires`); later (app side): find the links again before a download when they expired | `SiteRowCheck`, `InitCheck`, `ProbeSession` |
| **L6 Self-healing values** | Instagram's GraphQL `doc_id`; X's token self-check (X2 only on the owner's go); TikTok and Facebook: the data block found by content, so no value is needed | `ValueSource` (3.2) |
| **L7 Early warning** | **site census** (owner-run, a test list per site kept on the phone, slots only) and **weekly check W** (≤ 1 min, ≤ 2 MB per site) with a diff: CHANGED / BREAKING lines, each with a runbook step | `CensusJson`, `CensusDiff`, `SiteCensusActivity` (3.2) |
| **L8 Repair channel** | R9 remote recipes live (the owner signs and hosts the config), a kill switch per rung; the bundled defaults stay the fallback | R9 (built) |

## 6. Per-site plans

### 6.1 Facebook

- **Rungs:** F0 = main itself, first and not copied (public Safari page → session page → AVC ladder).
  After main fails: F1 `plugins/video.php` (today's R8, now judged and read by L3); F2 L3 rows from
  any page already read when key names moved; F3 the visible tab's own playback (capture) after a
  sign-in wall.
- **Judge signals:** a sign-in or checkpoint redirect → `LOGIN_WALL`; "content isn't available" →
  `REMOVED` / `PRIVATE`; `drm_info` or Widevine → `DRM` (stop); page data with no media object →
  `SHAPE_CHANGED`.
- **Anchors:** the video ID; media objects = a Facebook media-host link with bandwidth, width and
  height; the inline DASH manifest's representations.
- **Row checks:** V and init check on each offered row (≤ 8); lifetime from `oe`.
- **Census list (the owner's, on the phone):** video, reel, watch link, share link, post with a video,
  group post, live replay, sign-in-only, removed.

### 6.2 TikTok

- **Rungs:** T0 = main itself, first and not copied (tab or hidden-page data → phone page → desktop
  page → hidden page → `tikwm.com`; Master has no counterpart of `tikwm.com`, S4). After main fails:
  T1 `embed/v2` (today's R8, judged); T2 L3 rows when the data block or its keys move (the block is
  found by content — the one holding the item ID and a video object — not by its script ID); T3 the
  visible tab's own playback.
- **Judge signals:** the status code (`TikTokStatusRecipe`: removed, private, region); a verify or
  captcha page → `BOT_CHECK` (the user answers it with Show check); a photo post → `PHOTO_ONLY`; 403
  on the files → files refused.
- **Health:** for Master's rungs (T1–T3) only. The phone / desktop page order inside T0 is main's
  (changing it is main's decision, or route A).
- **Row checks:** main's one-byte check stays on main's rows; Master's rows get V, the init check and
  the size; codec label (H.264 / H.265).
- **Census list:** single video, long video, photo post, private, region-limited, removed, a post
  only the desktop page lists in HD.

### 6.3 Instagram

- **Rungs:** I0 = main itself, first and not copied (app API with the session → GraphQL → post page →
  embed). First fix under B: Master no longer repeats main's embed ask (recorder). After main fails:
  I1 GraphQL with a self-healed `doc_id` after `VALUE_REJECTED` (decision 3; S5: from the source S0
  proves — Instagram's own page scripts or yt-dlp's raw file; a strict digits pattern; once a day;
  kept after OK + V); I2 L3 rows when the answer's keys are renamed (`shortcode_media` →
  `xdt_shortcode_media` style moves, `items[]`); I3 the visible tab's own playback.
- **Judge signals:** `require_login` or a checkpoint → `LOGIN_WALL`; 429 or "wait a few minutes" →
  `RATE_LIMITED` (cooldown, no more Instagram requests this lookup); a GraphQL error or empty `data`
  for a valid post → `VALUE_REJECTED`; photos only → `PHOTO_ONLY`.
- **Health:** after `RATE_LIMITED`, Master skips its GraphQL rung (I1) for 30 minutes and goes to
  I2 / I3.
- **Row checks:** each row; DASH rows check the video and its sound.
- **Census list:** single video, reel, carousel with videos, photo-only, sign-in-only, removed.

### 6.4 X

- **Rungs:** X0 = main itself, first and not copied (`tweet-result` + widget token). After main fails:
  X1 the token self-check (the computed token against a census-proven answer; an alternate formula
  only from a public source with a date); **X2 only on the owner's go:** X's signed-out web API
  (`TweetResultByRestId`), which needs the public web bearer and a guest token — against the "no
  token" rule unless the owner allows it; X3 the visible tab's own playback (the user's own X
  sign-in; capture).
- **Judge signals:** tombstone (age or sensitive) → `AGE_LIMITED` (browser required); 404 or an empty
  answer → `REMOVED`; protected → `PRIVATE`; 429 → `RATE_LIMITED`.
- **Row checks:** each MP4 row on X's media host; HLS only with a known duration.
- **Recorder (first fix under B):** Master's R8 for X asks main's endpoint a second time today; the
  site pack stops that first (decision 8). A new way only through decision 2 (X2).
- **Census list:** video, GIF, quoted post's video, several videos, age-limited, removed.

## 7. Steps (each: build → tests → CI → APK → phone → docs)

| Step | Size | What | Done when |
| --- | --- | --- | --- |
| **S0** | M | The per-lookup request recorder (app side, memory only; decision 8) + the site census tool (`SiteCensusActivity`, like "YFT client census"): each way of each site asked once on the owner's test list, judged, shape fingerprint, V on one row; the recorder's counts (which way failed and how, 429 / bot checks, repeated asks); JSON export with slots only | the owner's run gives each site's share of recoverable failures → the pilot and the order (§8 item 1) |
| **S1** | L | The kit, built with the first site only (only the §3.2 counterparts that site needs): judge classes, site table, the contract stage's small ladder (decision 9) / `SiteHealth` / recorder + memo / budget, `SiteRowCheck` + `InitCheck`, site anchors on the toolkit walk, post-identity mutation tests (missing or renamed ID, a related-video decoy, an ambiguous page), a simulator world per site (renamed key, moved block, 429, sign-in wall, stale `doc_id`), flag `-Pyft.socialLadder` | JVM tests and simulator green; flag off = today |
| **S2a** | M | Pilot site pack: Facebook or TikTok (both have a real second way today), picked by S0: rungs, judge, anchors, tests, D7-style APK (skip / mutate per rung) | phone proof |
| **S2b–d** | M each | The other site packs. Instagram and X first: stop Master's repeated ask (recorder); new ways only on the owner's decisions (item 3 `doc_id`, item 2 X2) | phone proof per site |
| **S3** | S–M | Weekly check W for all four + runbook (each CHANGED / BREAKING line and its fix: a data change via R9, or code) | the owner's first W run |
| **S4** | S | R9 live: the owner signs and hosts the config; kill switch per rung; one repair drill (a key changed by config on the phone, no APK) | drill passed |
| **S5** | — | Promote per site to preview / release after 3 phone runs with 0 wrong rows | the owner's go |
| **S6** | M | **B+** (the owner's go, after B's phone proof): when main's answer is weak by an objective signal (no HD row, no sound row), Master adds only the missing formats; main's rows stay (merge rule); budgeted | phone proof; **A** only if B+ is measured insufficient (decision 7) |

## 8. Owner decisions

Decided (the owner, 2026-10-17; details in §2.1):

7. **Routing:** B — Master acts only after main fails, for all four sites, as site packs inside the
   generic engine; no claiming module, no rung-0 copy. B+ later (adds only missing formats, main's
   rows untouched). A only if B+ is measured insufficient, through a real Master-owned route state.
8. **Lookup scope:** the user's whole lookup (main + Master), with the per-lookup request recorder
   (memory only) that the contract stage and the S0 census read.
9. **R8 ladder:** a small, budgeted ladder per site pack in the contract stage; each new rung on the
   owner's go.

Still open (the owner's answer is needed before the step that uses it):

1. **Pilot and order:** recommended: S0 first; the pilot is Facebook or TikTok (both have a real
   second way today), picked by S0's share of recoverable failures; Instagram and X first get the
   repeated-ask stop; or another order.
2. **X2** (X's signed-out web API with a guest token): allow or not. Recommended: not now; decide after
   S0 shows how often the embed answer fails.
3. **Self-heal sources for Instagram's `doc_id`:** Instagram's own scripts, yt-dlp's raw file, or both
   measured in S0 (recommended: measure both, use only the proven one).
4. **Third-party services in Master:** none (recommended); main's `tikwm.com` stays as it is.
5. **R9 hosting:** the owner signs and hosts the recipe config (needed for repair without an APK).
6. **Census test lists:** the owner picks the public posts (kept on the phone only, never in docs).

## 9. Tests and docs per step

- JVM unit tests; simulator worlds with mutations; post-identity mutation tests (today's generic test
  covers only JSON with an `id`); recorder tests (main's asks seen, no repeated ask, stop after a 429
  or a bot check); parity tests unchanged; drift check for every symbol copy; `FixtureBaselineTest`;
  CI green before any APK.
- No-regression measure (S1): requests and time per lookup, before and after, on the census list.
- Docs per change: `PROGRESS.md` entry, a `docs/TEST_MATRIX.md` row, `docs/SUPPORT_MATRIX.md` where
  a site's behaviour changes, and this plan's step status.

## 10. Risks

- Sites change fast. The kit turns most fixes into data changes and W warns early, but a full redesign
  of a site still needs code.
- Sign-in walls (Instagram, Facebook) are not "fixed": next level means a clear reason and the visible
  tab's own playback, never a bypass.
- More rungs means more requests: budgets, cooldowns, the recorder and the memo are required, not
  optional.
- Under B, Master never improves a lookup main answered, even a weak one (no HD, no sound); B+ is the
  planned answer, measured first. Main's own habits (e.g. Instagram asking on after a 429) stay
  main's until the owner decides otherwise.
- The recorder sees only requests through the shared HTTP client, not what a page loads in the
  browser (hidden pages, the visible tab); the census export says so.
