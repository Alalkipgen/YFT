# Master Key — Next level for Instagram, TikTok, Facebook and X (final plan)

Status: **final plan** — the owner's Plan Mode request (2026-10-17), reviewed and approved by the
owner as the final plan the same day. This document changes no code. Decided: routing, lookup
scope and the R8 ladder (§2.1, §8 items 7–9). Nothing here is built until the owner says go, one
step at a time (§7, §8). **S0 started on the owner's go (2026-10-20): §7.1; the owner's run (S0d):
§7.2. The final plan of the steps after S0d (the owner, 2026-10-20): §7.3; decided §8 items 1, 2,
4, 6 and 10–12; open: item 3 (after the Instagram check, §7.3 item 0.2) and item 5 (before S4).
Next: Stage 0 (§7.3).**

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
- **အစဉ် (§7၊ §7.3):** S0 (ပြီးပြီ) → **Stage 0** (0.1 main ရဲ့ X card fix၊ 0.2 Instagram check၊
  0.3 Facebook access check evidence၊ 0.4 census fix + site census diff၊ 0.5 ပိုင်ရှင်ရဲ့ baseline
  B0) → **S1a** kit အနည်းဆုံး + Facebook pack (pilot) → **S2b** TikTok → **S2c** X → **S2d**
  Instagram → S3 weekly check → S4 remote recipe live → S5 site တစ်ခုစီ phone run ၃ ခါ → PR #2
  merge → P8 (beta.4)။ release ပြီးမှ: S1b၊ X2၊ "item အားလုံး" feature၊ B+ (S6)၊ A။
- **ဆုံးဖြတ်ပြီး:** §8 #1၊ #2၊ #4၊ #6–#12။ **ကျန်:** #3 (Instagram check 0.2 ပြီးမှ)၊ #5 (S4
  မတိုင်မီ)။
- **S0d (2026-10-20, §7.2):** ပိုင်ရှင်ရဲ့ phone run ၂ ခု။ Recoverable failure: Facebook 0/11၊
  TikTok 0/6၊ Instagram 0/7၊ X 2/6 (main မဖတ်တဲ့ card ထဲက video)။ Pilot: Facebook → TikTok →
  X → Instagram (ပိုင်ရှင် အတည်ပြုပြီး၊ 2026-10-20၊ §8 #1)။
- **Final Plan (ပိုင်ရှင်၊ 2026-10-20၊ §7.3):** X2 အခု မလုပ်ပါ (#2)၊ Master မှာ third party
  မသုံးပါ (#4)။ main fix ကို ပိုင်ရှင် ခွင့်ပြုတဲ့ ခြွင်းချက်အဖြစ် fix တစ်ခု commit တစ်ခုနဲ့ပဲ
  လုပ်ပါမယ် (#10): X card (go)၊ Facebook access check (evidence ပြီးမှ)၊ Instagram (check
  ပြီးမှ)။ X ရဲ့ age-limited / removed reason (`NO_MEDIA_FOUND`) မပြောင်းပါ။ link တစ်ခုကို item
  တစ်ခုပဲ ပြပါမယ် (IG `img_index`၊ X `/video/N`၊ မပါရင် ပထမ item; #11)၊ "item အားလုံး" feature
  က release ပြီးမှ။ Release gate (#12): site ၄ ခုလုံး S0–S5 ပြီးမှ PR #2 merge → P8 (beta.4) →
  တခြားသူတွေ။ S1b နဲ့ B+ (S6) က release ပြီးမှ။ Baseline B0 (§9) ထက် main အောင်မြင်တဲ့ lookup
  ရဲ့ request/အချိန် မများရပါ။

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
  **Exception (the owner, 2026-10-20; §8 item 10):** a fix in main's adapter code only as its own
  commit with its own tests, on the owner's go for that fix (X's card: go; Facebook's access check:
  after §7.3 item 0.3; Instagram: after item 0.2); the site is re-baselined after it (§9).
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
| **S0** | M | The per-lookup request recorder (app side, memory only; decision 8) + the site census tool (`SiteCensusActivity`, like "YFT client census"): each way of each site asked once on the owner's test list, judged, shape fingerprint, V on one row; the recorder's counts (which way failed and how, 429 / bot checks, repeated asks); JSON export with slots only | the owner's run gives each site's share of recoverable failures → the pilot and the order (§8 item 1) — **done** (§7.1, §7.2) |
| **Stage 0** | M | Five items, in parallel, before S1a's first phone APK (§7.3): **0.1** X's card fix in main (the owner's go); **0.2** the Instagram check (no code); **0.3** Facebook's access-check evidence (no code); **0.4** the census fixes and the site census diff (debug builds only); **0.5** the owner's baseline run B0 (§9) | 0.1 and 0.4: CI green and the census APK; 0.2 and 0.3: a verdict each (§7.3); 0.5: B0 recorded |
| **S1a** | L | The kit's minimum with the pilot's pack, Facebook (the first draft's S1 and S2a together), flag `-Pyft.socialLadder` (off = today): the recorder in the app's lookup path, judge classes, the site table, one judged request rung + L3 + capture, budgets and the 30-minute cooldown (S7), `SiteRowCheck` + `InitCheck` on every Master row, Facebook's anchors, post-identity mutation tests, a simulator world (§7.3) | JVM tests, simulator and CI green; flag off = today; the D7-style APK's phone proof (§7.3) |
| **S2b** | M | TikTok's pack (§7.3) | phone proof; wrong rows 0; no regression against B0 |
| **S2c** | M | X's pack: main's answer replayed, no new ask (X2 not now; §7.3) | the same |
| **S2d** | M | Instagram's pack by the Instagram check's verdict (0.2); the owner's signed-in census first (§7.3) | the same |
| **S3** | S–M | Weekly check W for all four + runbook (each CHANGED / BREAKING line and its fix: a data change via R9, or code) | the owner's first W run |
| **S4** | S | R9 live: the owner signs and hosts the config (§8 item 5, decided before S4); kill switch per rung; one repair drill (a key changed by config on the phone, no APK) | drill passed |
| **S5** | — | Promote per site after 3 phone runs on different days with 0 wrong rows and no regression against B0; then the release gate (§7.3): all four sites, the first W run and the R9 drill passed, the intended differences in `docs/SUPPORT_MATRIX.md` → PR #2 merged → P8 (beta.4) | the owner's go |
| **S1b** | M | After the release, on the owner's go: the rest of the kit — several request rungs per site with `SiteHealth` (the last good rung first) and self-healed values (L6). Needed only if X2 is allowed or Instagram's `doc_id` needs self-healing: Facebook, TikTok and Instagram each have one request rung in S1a–S2d | JVM tests; phone proof per site |
| **S6** | M | **B+** (the owner's go, after the release and B's phone proof): when main's answer is weak by an objective signal (no HD row, no sound row), Master adds only the missing formats; main's rows stay (merge rule); budgeted | phone proof; **A** only if B+ is measured insufficient (decision 7) |


### 7.1 S0 — as built (started 2026-10-20, the owner's go)

S0 measures only: it changes no lookup. Main's adapters, the app's lookups and Master's engine stay
as they are; Master's way in the census is today's R8 contract of each site; the census screen is
an owner tool of debug builds (nothing in release or preview).

| Sub-step | What | Done when |
| --- | --- | --- |
| **S0a** | The recorder's labels (`LookupRecord`, additive: a record given none is today's, so route B and every lookup stay as they are): each listed ask gets its way, its reading of the answer and a short shape fingerprint; the record counts repeated asks (an endpoint already asked in the lookup), asks after a stop (a host that answered 429 or a bot check, asked again) and bot checks, and names the hosts that stopped. The site table (`SiteWays`): each site's way labels, media hosts and readings, its census kinds and what each kind expects | JVM tests; the route B tests unchanged — **built (2026-10-20)**: 41 slots (Facebook 13, TikTok 10, Instagram 10, X 8), drift tests against main's adapters |
| **S0b** | The census engine (`SiteCensus`) and the one-row check (`SiteRowCheck`, V) | JVM tests with fake sites — **built (2026-10-20)**: the row rules, the stops and budgets and the privacy guard below; V checks a DASH row's tallest video file through its manifest; 24 JVM tests |
| **S0c** | The debug screen "YFT site census" (`SiteCensusActivity`, like "YFT client census"): the owner's links per slot, a run per site or all, the report and its JSON export | CI green; APK — **built (2026-10-20)**: a link field per slot (kept on the phone), "Run <site>" / "Run all sites", "Copy report" / "Copy JSON", the state file `files/master/site-census-last.json`; main as Home runs it (`SiteCensusLookup`: the app's adapter coordinator, fresh, TikTok's hidden page, the browser's identity) on clients with no retry, wire bytes counted; `WebViewAgent` copied from main; 5 JVM tests and the no-background guard |
| **S0d** | The owner's run on the phone; the shares recorded here and in `PROGRESS.md` | the pilot and the order (§8 item 1) — **run (2026-10-20)**: two runs, §7.2; the pilot and the order wait for the owner |

**Row rules** (a row is one link of the owner's list, in its slot):

- **Main:** main's own lookup with the four adapters as the app builds them (TikTok's hidden page
  and `tikwm.com` included), fresh (no remembered answer), with the browser's identity: the agent
  YFT's tab sends and the browser's cookies for the post's link (the report says only whether a
  session was there). There is no tab, so no tab data: the page read, then the hidden page, as main
  does. The census's clients retry nothing (each ask once; main's own order of ways stays); at most
  90 s per row (Home's limit). Every ask is listed with its way, status, reading and shape, and the
  ways main did not reach are named.
- **Master:** today's R8 way of the site, once, under route B's rules in the same record (main's
  identical unauthenticated answer replayed, an endpoint main asked not asked again, nothing new
  after a 429; all counted). After main's failure with a reason Master's engine answers (not final,
  not browser-required), as route B would. After main's success or the site's final answer about
  the post (private or removed, region), once as a trial (main's failure given as `NO_MEDIA_FOUND`;
  marked "trial"). Not after a browser-required reason (the engine asks no contract then), a network
  failure, a rate limit, a bot check or main's time limit, and not for a short link Master's
  contracts do not read.
- **V:** one row of each answer (main's best, Master's best: the tallest, then the highest
  bitrate) gets a one-byte check with the headers the download sends for that row, only on the
  site's media hosts. An HLS row or any other host is `unknown`, never a pass; a DASH row checks its
  video file.
- **Judge:** each ask's reading (data, no post, no data, not JSON, login page, bot check) and each
  row's class from main's reason as Master's engine sees it — **recoverable** (Master's contract
  would be asked), **browser** (`TerminalRules.BROWSER_REQUIRED`), **final**
  (`TerminalRules.NEVER_FALLBACK`) — against what the slot's kind expects (§6's lists: video kinds
  expect rows; removed, private, photo-only and sign-in-only kinds expect a final answer, and a
  different answer is "not as expected"). Each site's number: main's recoverable failures ÷ the
  rows that should download, and how many of them Master's way recovered (rows that passed V).

**Stops and budgets:** a 429 or a bot check (any ask, the hidden page included) stops that site for
the run and for 30 minutes after (the screen's state file); at most 40 MB and 150 requests per site
(the census's own clients; the hidden page's own loads are neither counted nor listed, and the
report says so).

**Privacy (S8):** the links stay on the phone (the screen's own store); the report and the export
hold only sites, slot numbers, kinds, way labels, classes, counts, sizes and short fingerprints; a
last guard masks any address and the given values (the link, the post ID, handles).

**Not in S0** (the owner's decisions): measuring Instagram's `doc_id` sources (§8 item 3; S0 shows
only whether main's GraphQL answers the post), X2 (§8 item 2), any new way.

### 7.2 S0d — the owner's run (2026-10-20)

The owner ran "YFT site census" on the phone (the S0c commit's APK) with the owner's public test
posts (kept on the phone; 40 of the 41 slots, TikTok's private slot empty):

- **Run 1** (02:14 UTC): all four sites, 40 rows, 192 s, mobile data, through the owner's VPN with
  a US exit (TikTok needs a VPN on the owner's network).
- **Run 2** (02:39–02:43 UTC): Facebook, Instagram and X without the VPN; TikTok not run.

No 429, bot check, stop or time limit in either run; the most one site used is 67 requests and
5.9 MB (the budgets: 150 and 40 MB). Every row main gave passed V (Facebook 11, TikTok 6,
Instagram 6, X 4; fail 0, unknown 0).

| Site | Run | Should download | Recoverable | Master recovered | Browser | Not as expected | Route B | Trials (with rows, V pass) | Requests · MB · s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Facebook | 1 | 11 | 0 | 0 | 0 | 1 (slot 12) | 1 | 8 (8, 8) | 67 · 5.6 · 75 |
| Facebook | 2 | 11 | 0 | 0 | 0 | 2 (slots 12, 13) | 1 | 8 (8, 8) | 67 · 5.9 · 74 |
| TikTok | 1 | 6 | 0 | 0 | 0 | 0 | 1 | 8 (6, 6) | 59 · 1.6 · 65 |
| Instagram | 1 | 7 | 0 | 0 | 1 | 2 (slots 5, 10) | 1 | 6 (1, 1) | 38 · 2.1 · 31 |
| Instagram | 2 | 7 | 0 | 0 | 1 | 2 (slots 5, 10) | 1 | 6 (1, 1) | 38 · 2.1 · 33 |
| X | 1 | 6 | 2 | 0 | 0 | 2 (slots 3, 6) | 4 | 4 (4, 4) | 38 · 0.1 · 21 |
| X | 2 | 6 | 2 | 0 | 0 | 2 (slots 3, 6) | 4 | 4 (4, 4) | 38 · 0.1 · 11 |

Findings (both runs, unless said):

1. **X — videos only in the card (slots 3 and 6).** The embed's answer (`tweet-result`, 200)
   carries these posts' videos only in the post's card (`card.binding_values.unified_card.string_value`:
   JSON inside a string; card types `video_website` and `video_multi_dest_carousel_website`), not
   in `mediaDetails`, which main's reader (`XSyndication.parse`) and Master's X contract
   (`mediaDetails/*`, then the quoted post) read; main says `RESPONSE_CHANGED`. Under route B
   Master's engine found rows in main's replayed answer (no new ask): 4 and 8 rows, tallest 720,
   through its shape layer (L3 reads JSON inside strings). Its best row was HLS, so V said
   `unknown` and the census counts no recovery. The card also lists MP4 files up to 720p (checked
   off the phone). The age-limited and removed slots (7, 8) read "no post" → `NO_MEDIA_FOUND`, as
   expected. A small fix in main's X reader (the card's videos) would make both main's rows; only
   on the owner's go, not part of S0.
2. **Instagram — signed out, the embed page is the one way.** Main's GraphQL answered 401 on 10 of
   10 (no session); main gave 6 of the 7 video rows: 5 from the embed page (one shape,
   `hd098d70a`), the carousel's from the post's page. Slot 5 (a reel): the embed page had no data (`h8295e9f2`) →
   `LOGIN_REQUIRED` (browser), so Master is not asked (signed out, off the phone, yt-dlp found it
   as DASH). Slot 10 (removed): the embed page answers as for the photo-only and sign-in-only
   slots (`h484533f7`) → `LOGIN_REQUIRED`, not a final answer. Slot 7 (three videos): main 1 row,
   Master's trial (the embed page) 1 row, tallest 720. Master's other five trials were refused (its
   way is the embed page main had read; one page per lookup), so Master adds nothing on Instagram
   today; a gain needs another way (§8 item 3).
3. **Facebook — every video row; two wrong readings.** Main gave rows on 11 of 11 video slots;
   Master's plugin player gave rows on 8 of 8 trials, V pass 8. Slot 12 (sign-in-only; the link
   itself is not verified, it may be gone) reads as `RESPONSE_CHANGED` (recoverable) on a page of
   the removed slot's shape (`h0cd5c634`); Master's route B (the plugin player) found nothing
   ("needs playback"). Slot 13 (removed): `PRIVATE_OR_UNAVAILABLE` (final) with the VPN,
   `NO_MEDIA_FOUND` (recoverable) without it, the same page shape: main's access markers
   (`FacebookPageParser`) are mostly English phrases and the page's wording likely differed, so a
   removed post shows as "no media". Master's way did not run on slots 8, 9, 10 and 13 (a share
   link, a post with a video, a group post, the removed post): links main resolves first
   (`requiresCanonicalResolution`), which Master's contracts do not read.
4. **TikTok (run 1) — all as expected.** 6 of 6 video rows; main's tallest 1920 on slots 1 and 10
   (the desktop page), Master's embed player at most 1280; region-limited `GEO_RESTRICTED` and
   removed `PRIVATE_OR_UNAVAILABLE` (final; the download service 403, Master's embed player 400);
   the photo post `NO_MEDIA_FOUND` (Master's route B the same). Main's desktop page after the phone
   page counts as a repeated ask (the same endpoint) on 7 of 9 rows.

**Reading for §8 item 1:** the shares do not separate Facebook and TikTok (0 of 11, 0 of 6): main's
own ways answered every video post. S0's only recoverable failures are X's two card posts (a fix in
main's reader). Under route B's rules Master repeated no ask (X: main's answer replayed; Instagram:
refused). Recommended: the pilot **Facebook** (the most kinds and ways; Master's way gives rows on
8 of 8; its gaps are S2a's work (now S1a, §7.3): the links main resolves first, the access check by wording, the
plugin player's empty answer), then TikTok, X, Instagram (after item 3). Waiting for the owner.

### 7.3 The final plan of the steps (the owner, 2026-10-20)

After S0d the owner approved this plan of the steps; it records §8 items 1, 2, 4, 6 and 10–12.
Each step still starts on the owner's go (S10), and a phone proof comes before "live". The first
draft's S1 (the kit) and S2a (the pilot pack) are now one step, S1a; the rest of the kit is S1b,
after the release.

**Stage 0** — five items in parallel, before S1a's first phone APK (S1a's JVM work may start
earlier):

| Item | What | Done when |
| --- | --- | --- |
| **0.1 X's card fix (main; the owner's go)** | In main's X reader (`XSyndication.parse`): the post's own `mediaDetails` first, then the post's own card (`card.binding_values.unified_card.string_value`, JSON in a string → its `media_entities`, read by the existing `videoOf`), then the quoted post as today; the quoted post's card is not read. A card's several videos keep the card's own order (its own media list, not the key order of `media_entities`). The choice (`/video/N`, else the first), MP4 first and the host guard stay as they are. X's age-limited and removed posts keep `NO_MEDIA_FOUND` (by design: the generic detector stays reachable). Synthetic fixtures for both card types (`video_website`, `video_multi_dest_carousel_website`), decoys (a card with no video, a quoted post's card, a media link off X's media hosts) and `/video/2` on a carousel card; `FixtureBaselineTest` holds. Drift: both copies of `parse` (`ContractRecipes`, `SiteReadings`) reviewed in the same commit; the census reading reads a card's video as `data` | CI green; the owner's X-only census run: slots 3 and 6 give MP4 rows with V pass, slots 1, 2, 4 and 5 unchanged |
| **0.2 The Instagram check (no code)** | Locally only, never on public CI: main's GraphQL ask against yt-dlp's current Instagram extractor (its commit and date): endpoint, `doc_id`, variables, headers. The question: does an ask work signed out without TLS impersonation? Verdict: (a) yes → a fix proposal for main (the owner's go, its own commit); (b) only with impersonation → S3 forbids it; Instagram's stage may then use only the visible tab's own fetch (WebView), on the owner's go; (c) 401 signed out always → a documented limitation. Also answered: main's 401 → `LOGIN_REQUIRED` mapping (keep or change) and §8 item 3 (the `doc_id` sources) | the verdict here and in `PROGRESS.md` (structure only; no ID or link) |
| **0.3 Facebook's access-check evidence (no code)** | The "content isn't available" page in English against at least one other language (a made-up video ID, not a real post; the page's structure, not its wording): is there a language-independent signal? Yes → a fix proposal for main's `FacebookPageParser` (the owner's go, its own commit); no → Master's judge stays conservative (a final answer only on positive evidence). Slot 12's expectation after the owner's check (the owner's tasks below) | the verdict here and in `PROGRESS.md` |
| **0.4 The census fixes (debug builds only)** | Per row `setup_ms` and the end-to-end ms; a social report key (method + URL + body + identity class, hashed; a new key: `LookupAsk.endpoint` is route B's R2 key and stays as it is) that splits a "repeat" (the same key) from a "variant" (the same endpoint, another body or identity) — the endpoint rule, its enforcement and the host stops stay as they are; the best row's duration (s) and "item N of M"; Instagram's removed slot signed out reads "ambiguous (no session)"; a photo post stays `NO_MEDIA_FOUND`; the site census diff: two JSON exports → per-slot CHANGED / BREAKING lines | JVM tests; CI green; the census APK with 0.1 |
| **0.5 The baseline B0 (the owner's run)** | §9's fixed config with the APK of 0.1 and 0.4 = §9's "before"; a site is re-baselined after any new main fix | the shares and the requests and time per lookup recorded here and in `PROGRESS.md` |

**S1a — the kit's minimum and Facebook's pack (the pilot)**, flag `-Pyft.socialLadder` (off = today):

- The request recorder in the app's real lookup path, behind the flag.
- Rungs: F1 the plugin player (`plugins/video.php`, today's R8 way), judged; F2 L3 rows on pages
  already read (no fetch); F3 the visible tab's own playback (capture). Master reads the links main
  resolves first (slots 8, 9, 10 and 13) through main's resolved link.
- The judge is conservative: a final answer only on positive evidence; "needs playback" → F3 or
  the browser; no shape hash in the judge.
- S7: no repeated ask; a host stop after a 429 or a bot check; F1's 30-minute cooldown across
  lookups; the budgets.
- S6: V and the init check on every Master row (at most 8 rows); `unknown` is not a pass; the
  link lifetime (`oe`).
- Post identity bound to the Facebook video ID; mutation tests (a missing or renamed ID, a
  related-video decoy, a page with two videos → no rows); a simulator world (a renamed key, a
  moved block, a 429, a sign-in wall, the unavailable page in two languages).
- Flag off: the route B tests, the parity tests, `FixtureBaselineTest` and the drift check as they
  are; no YouTube file changes.
- **Done when:** JVM tests, simulator and CI green; a D7-style APK (main's Facebook skipped or
  mutated): every Master row passes V; wrong rows 0 (the video ID matches; a trial's duration
  within ±1 s of main's); slots 8–10 give Master rows; slot 13 `PRIVATE_OR_UNAVAILABLE` with and
  without the VPN if 0.3 found a signal, else no wrong final answer and a documented limitation;
  slot 12 by the owner's check; requests and time on main-success lookups not above B0 (§9); docs.

**S2b TikTok:** T1 the embed player (`embed/v2`), judged; a bot check stops; a photo post stops
with no request; T2 L3; T3 capture; TikTok's own client (not the shared one) wrapped by the
recorder; V and the init check; the lower quality (at most 1280) documented.
**S2c X:** main's answer replayed, no new ask (X2 not now); a hidden post → the browser; a photo
post stops; an HLS row only with a known duration (V).
**S2d Instagram:** by 0.2's verdict; no repeat of main's embed ask; `RATE_LIMITED` → the cooldown;
`doc_id` self-healing only if 0.2 shows it rotates (then S1b); the owner's signed-in census before
this stage.
Each site: a D7-style phone proof, wrong rows 0, no regression against B0.

**S3 and S4** as in §7: W (≤ 1 min, ≤ 2 MB per site) with the diff and the runbook; R9 live (the
owner signs and hosts the config, a kill switch per rung, one repair drill on the phone).

**The release gate (§8 item 12):** each site's S5 (3 phone runs on different days, wrong rows 0,
no regression against B0), the first W run passed, the R9 drill passed, the intended differences
listed in `docs/SUPPORT_MATRIX.md` → PR #2 merged → P8 (beta.4) → others get the app.

**After the release** (each on the owner's go): S1b, X2, the "all items" feature, B+ (S6), A.

**Not done:** X's reason for the age-limited and removed posts changed; `LookupAsk.endpoint` or a
YouTube file edited; a shape hash in a judge; yt-dlp on public CI; IDs, links or names in docs,
fixtures or logs.

**The owner's tasks:** slot 12 checked signed in (replaced on the phone if it is gone); the weekly
census in the fixed config (§9), the B0 run first; Instagram's signed-in census before S2d; R9's
hosting before S4. Instagram slot 7's check in the app is no longer needed (one item per link).

## 8. Owner decisions

Decided (the owner, 2026-10-17; details in §2.1):

7. **Routing:** B — Master acts only after main fails, for all four sites, as site packs inside the
   generic engine; no claiming module, no rung-0 copy. B+ later (adds only missing formats, main's
   rows untouched). A only if B+ is measured insufficient, through a real Master-owned route state.
8. **Lookup scope:** the user's whole lookup (main + Master), with the per-lookup request recorder
   (memory only) that the contract stage and the S0 census read.
9. **R8 ladder:** a small, budgeted ladder per site pack in the contract stage; each new rung on the
   owner's go.

Decided (the owner, 2026-10-20; §7.3):

- **1 — Pilot and order:** the pilot Facebook, then TikTok, X, Instagram (S1a, S2b–S2d). S0d's
  shares did not separate Facebook and TikTok (0 of 11, 0 of 6); Facebook has the most kinds and
  ways, and Master's way gave rows on 8 of 8 trials.
- **2 — X2** (X's signed-out web API with a guest token): not now; S0d's embed answer carried every
  video post's media (2 of 6 only in the card, fixed in main by §7.3 item 0.1). After the release,
  on the owner's go.
- **4 — Third-party services in Master:** none; main's `tikwm.com` stays main's.
- **6 — Census test lists:** the owner's public posts, kept on the phone only; a gone link is
  replaced on the phone.
- **10 — Main fixes:** exceptions to S1, each its own commit on the owner's go: X's card (go, item
  0.1); Facebook's access check (after item 0.3's evidence); Instagram (after item 0.2's check). X's
  age-limited and removed posts keep main's reason (`NO_MEDIA_FOUND`, by design).
- **11 — One item per link:** Instagram's `img_index`, X's `/video/N`, else the first item; an "all
  items" feature after the release.
- **12 — The release gate:** S0–S5 for all four sites before PR #2's merge and P8 (beta.4); S1b and
  B+ (S6) after the release.

Still open (the owner's answer is needed before the step that uses it):

- **3 — Self-heal sources for Instagram's `doc_id`:** Instagram's own scripts, yt-dlp's raw file, or
  both; decided after the Instagram check (§7.3 item 0.2). S0d (§7.2): signed out, main's GraphQL
  answered 401 on 10 of 10; main gave 6 of 7 video rows (5 from the embed page).
- **5 — R9 hosting:** the owner signs and hosts the recipe config (needed for repair without an
  APK); decided before S4.

## 9. Tests and docs per step

- JVM unit tests; simulator worlds with mutations; post-identity mutation tests (today's generic test
  covers only JSON with an `id`); recorder tests (main's asks seen, no repeated ask, stop after a 429
  or a bot check); parity tests unchanged; drift check for every symbol copy; `FixtureBaselineTest`;
  CI green before any APK.
- No-regression measure (S1): requests and time per lookup on the lookups main answers, before and
  after, on the census list. **Baseline B0** (§7.3 item 0.5) is "before": the owner's run in a fixed
  config — all four sites through the owner's VPN with the same US exit as S0d, and Facebook,
  Instagram and X without the VPN, at the same weekday and time window each time — with the APK of
  items 0.1 and 0.4; "after" is the same config with the step's APK. A site is re-baselined after
  any new main fix. A step's requests and time on main-success lookups may not exceed B0's.
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
