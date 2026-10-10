# Master Key — Architecture (step A)

Status: **step A done: structure only, same behavior.** No feature or policy changed.
Since then: **R1** (main sync, `parity/` harness) and **R2** (walled hosts, DRM stop, YouTube
payload off) — see PROGRESS.md.

- Master ကို layer/package အလိုက် ခွဲလိုက်ပါပြီ။ အလုပ်လုပ်ပုံ (behavior) မပြောင်းပါ။
- Terminal rule ကို `policy/TerminalRules` တစ်နေရာတည်းမှာ ထားလိုက်ပြီး app ထဲက ထပ်နေတဲ့ set ကို ဖျက်လိုက်ပါတယ်။
- `PayloadMediaReader` ကို L1/L2/L4 layer သုံးခုနဲ့ data-only recipe table အဖြစ် ခွဲလိုက်ပါတယ်။
- Android မလိုတဲ့ ဖိုင် ၃ ခုကို JVM module ထဲ ရွှေ့လိုက်လို့ JVM မှာ test လုပ်လို့ ရသွားပါပြီ။

## 1. Layout

```
extractor-master/ (pure JVM)            com.alal.yft.extractor.master
  MasterContracts.kt                    public API: request, snapshot, result, provider, validator
  MasterFallbackEngine.kt               orchestrator only (stages, budget, result)
  policy/   TerminalRules               single source: NEVER_FALLBACK, BROWSER_REQUIRED,
                                        R2 WALLED_HOSTS (YouTube/Reddit walls are final)
            SnapshotBudget              snapshot read limits
  layers/   MasterLayer, Evidence, LayerStack (order + shared raw-candidate cap)
            CaptureLayer   L1           requests the visible browser made
            ContractLayer  L2           HTML5 <video>/<source>, OpenGraph; R8: reads a site
                                        endpoint's answer (ContractReader, site key table)
            ShapeLayer     L3           R3: shape search with content-ID anchoring (adds only)
            RecipeLayer    L4           bounded JSON walk applying recipes
  recipes/  PayloadRecipes, NodeRule    data-only site key tables (one line per key)
            ContractRecipes             R8: per-site endpoint, headers, host locks, key table
            TikTokStatusRecipe          private/regional status codes
            YoutubeStreamingRecipe      rows off since R2 (DRM signal kept); module in R6
  contract/ SiteContracts               R8: the engine's one bounded ask (not a layer)
            WidgetToken                 R8: X's embed-widget token (main's arithmetic)
  toolkit/  UrlPolicy, CandidateFactory, HtmlScan, InlineDashReader
            R3 copies (provenance + drift check): PageScripts, BalancedJson,
            AnchoredMediaWalk, MediaKeyTable, QualityLadder, ProbeRounds, RequestPolicy
  verify/   OkHttpMediaValidator, ProbeSession, CandidateGate, FocusSelection,
            CapturedMediaMetadata, CapturedMp4Facts
            R4: SegmentIndexReader (sidx/EXTINF), MediaFingerprint, FingerprintGroups
  modules/  MasterSiteModule, SiteExtractorModule   R6: a claimed page is the module's alone
            youtube/ copied main files (whole-file drift rows) + MasterYouTubeModule,
            MasterYouTubeClients (versioned table, visionOS first; canary owner-run)
  present/  MasterMainPresentation      main + More groups for the existing sheet
  capture/  InMemoryCaptureStore        per-tab, memory-only snapshot store
  parity/   ParityUrls, ParityReport,   R1: frozen list parser, host + content-ID report,
            ParityVerdict               §4.2 pass checks (offline baseline test in tests)
extractor-master-android/               com.alal.yft.extractor.master.android
  WebViewPlaybackCapture, MasterBrowserSession, CaptureFrame, CaptureFocusGuard,
  MasterMainSelection, assets/yft-master-capture.js
  R5: CodecSteering + assets/yft-master-codecs.js (document-start codec steering)
  R7: assets/yft-master-mse.js (document-start MSE facts + EME stop) → fed CapturedRequest
app/ BrowserMasterFallback              hook; reads TerminalRules (no own copy)
app/src/androidTest/.../MasterParityLiveTest, assets/parity/parity-urls.json; scripts/canary.sh
scripts/master-youtube-canary.sh (R6 YouTube client-table canary, owner-run)
extractor-master/toolkit-provenance.tsv; scripts/master-toolkit-drift.py (R3 copy drift)
```

## 2. Flow

`TerminalRules` → R6 module claim (claimed page: module only when no adapter answered, else
skipped; never layers/capture) → stages: R8 `CONTRACT` (the identified video's own endpoint,
asked once by the engine) → `PAGE_DATA` → `PLAYBACK_CAPTURE`; per stage: snapshot gates (generation, same page, access failure, `SnapshotBudget`,
authorized playback) → `LayerStack` (L2 → L4 → L1 → L3; L3 adds only) → `CandidateNormalizer` →
`CandidateGate` → capture-stage selection hook → `FocusSelection` → `ProbeSession`
(budget, companion audio) → `MasterResult`.

## 3. Rules

- `extractor-master` depends on `extractor-api`/`extractor-generic` only; never on
  `extractor-sites` (R6 parity tests use it as a test-only dependency). Android-only code stays in `extractor-master-android`.
- A layer only reads delivered material: no fetch, signing or login. New layers implement
  `MasterLayer` and are added to `LayerStack`; the engine does not change.
- **R8 decision (rule conflict):** R8 needs each site's own endpoint, which this rule forbids a
  layer to fetch. Decided: layers stay read-only; the **engine** asks once, as a stage
  (`MasterStage.CONTRACT`, before page data and capture), through the caller's bounded
  `ExtractorHttpClient` (`contract/SiteContracts`: HTTPS, redirect limit, timeouts, per-site
  body cap, answer host lock, its own share of the time budget). The answer reaches L2 only as
  `PageSnapshot.contract`, which no page or capture can fill. Endpoints, headers, agents, host
  locks and key tables are data (`recipes/ContractRecipes`); only a widget's token (X) is
  bundled code, as on main. It asks only for the caller's identity, never after a login,
  bot-check or player-script failure, never twice. Without `contracts` the engine is unchanged.
  Cost accepted: after a main failure the same endpoint may be asked a second time (Vimeo, X).
- Layer order is configuration. It decides probe order under the budget, so changing it is a
  behavior change and needs the R1 parity harness.
- Recipes are data. A renamed site key is a one-line edit in `PayloadRecipes.NODE_RULES`.
- Stop rules live only in `TerminalRules`; the app hook calls `TerminalRules.blocksFallback`
  (with the lookup link and the tab page since R2).
- A main sync that changes main's site fixtures must pass `FixtureBaselineTest` (rows and
  heights only grow, terminal verdicts stay) or rewrite the baseline on purpose.

## 4. Old → new

| Before | After |
| --- | --- |
| `MasterFallbackEngine` (274 lines) | engine 174 + `ProbeSession`, `CandidateGate`, `FocusSelection`, `SnapshotBudget` |
| engine `NEVER_FALLBACK`/`BROWSER_REQUIRED` + app `NEVER_CAPTURE` | `policy/TerminalRules` |
| `PayloadMediaReader` (397 lines) | `ContractLayer`, `RecipeLayer`, `CaptureLayer`, `recipes/*`, `CandidateFactory`, `HtmlScan` |
| `Discovery` | `layers/Evidence` |
| `UrlPolicy`, `InlineDashReader` | `toolkit/` |
| `OkHttpMediaValidator` | `verify/` |
| `InMemoryCaptureStore` | `capture/` |
| android `CapturedMediaMetadata`, `CapturedMp4Facts` | `extractor-master/verify/` |
| android `MasterMainPresentation` | `extractor-master/present/` |

## 5. Evidence that behavior is unchanged

- Local JVM run (Kotlin 2.0.21, JDK 17): `extractor-master` 88 tests (77 before + 2
  `TerminalRulesTest` + 9 moved metadata tests) and 39 non-WebView Android-module tests: 0
  failures.
- Old-vs-new differential run (scratch, not committed): old `PayloadMediaReader` vs
  `LayerStack.standard()` on 94 fixture files (main's site fixtures + Master fixtures) × 4 page
  hosts × html/api/both/requests × content IDs: 5,372 cases, 0 differences (2,774 with
  candidates, 94 terminal). Old vs new engine with seeded validators and capture: 10,368 cases,
  0 differences (all five result kinds).
- CI: `master-optin-debug-apk` on the final commit (see PROGRESS.md).

## 6. Next (inside Phase 1)

- R1, R2: done (PROGRESS.md).
- R3: done — `layers/ShapeLayer` (L3) + content-ID anchoring; ladder/probe/request policy in
  `toolkit/`; `extractor-master/toolkit-provenance.tsv` + `scripts/master-toolkit-drift.py`.
- R4: done — fingerprint in `verify/`, grouping in `MasterMainSelection`.
- R5: done — codec steering in the Android capture (`CodecSteering`, `yft-master-codecs.js`).
- R6: done — `modules/` (`MasterSiteModule`, `SiteExtractorModule`) and `modules/youtube`
  (copies + `MasterYouTubeModule`, `MasterYouTubeClients`, canary script).
- R7: done — `yft-master-mse.js`, fed addresses in L1 (`CaptureLayer`), EME licence/keys stop.
- R8: in progress — contract stage + `ContractReader`; Vimeo, X and Facebook done (see PROGRESS.md).
