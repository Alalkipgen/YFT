# Master Key — Architecture (step A)

Status: **step A done: structure only, same behavior.** No feature or policy changed.

- Master ကို layer/package အလိုက် ခွဲလိုက်ပါပြီ။ အလုပ်လုပ်ပုံ (behavior) မပြောင်းပါ။
- Terminal rule ကို `policy/TerminalRules` တစ်နေရာတည်းမှာ ထားလိုက်ပြီး app ထဲက ထပ်နေတဲ့ set ကို ဖျက်လိုက်ပါတယ်။
- `PayloadMediaReader` ကို L1/L2/L4 layer သုံးခုနဲ့ data-only recipe table အဖြစ် ခွဲလိုက်ပါတယ်။
- Android မလိုတဲ့ ဖိုင် ၃ ခုကို JVM module ထဲ ရွှေ့လိုက်လို့ JVM မှာ test လုပ်လို့ ရသွားပါပြီ။

## 1. Layout

```
extractor-master/ (pure JVM)            com.alal.yft.extractor.master
  MasterContracts.kt                    public API: request, snapshot, result, provider, validator
  MasterFallbackEngine.kt               orchestrator only (stages, budget, result)
  policy/   TerminalRules               single source: NEVER_FALLBACK, BROWSER_REQUIRED
            SnapshotBudget              snapshot read limits
  layers/   MasterLayer, Evidence, LayerStack (order + shared raw-candidate cap)
            CaptureLayer   L1           requests the visible browser made
            ContractLayer  L2           HTML5 <video>/<source>, OpenGraph
            (ShapeLayer    L3)          slot — R3
            RecipeLayer    L4           bounded JSON walk applying recipes
  recipes/  PayloadRecipes, NodeRule    data-only site key tables (one line per key)
            TikTokStatusRecipe          private/regional status codes
            YoutubeStreamingRecipe      the one code recipe; off in R2, module in R6
  toolkit/  UrlPolicy, CandidateFactory, HtmlScan, InlineDashReader
  verify/   OkHttpMediaValidator, ProbeSession, CandidateGate, FocusSelection,
            CapturedMediaMetadata, CapturedMp4Facts
  present/  MasterMainPresentation      main + More groups for the existing sheet
  capture/  InMemoryCaptureStore        per-tab, memory-only snapshot store
extractor-master-android/               com.alal.yft.extractor.master.android
  WebViewPlaybackCapture, MasterBrowserSession, CaptureFrame, CaptureFocusGuard,
  MasterMainSelection, assets/yft-master-capture.js
app/ BrowserMasterFallback              hook; reads TerminalRules (no own copy)
```

## 2. Flow

`TerminalRules` → snapshot gates (generation, same page, access failure, `SnapshotBudget`,
authorized playback) → `LayerStack` (L2 → L4 → L1 today) → `CandidateNormalizer` →
`CandidateGate` → capture-stage selection hook → `FocusSelection` → `ProbeSession`
(budget, companion audio) → `MasterResult`.

## 3. Rules

- `extractor-master` depends on `extractor-api`/`extractor-generic` only; never on
  `extractor-sites`. Android-only code stays in `extractor-master-android`.
- A layer only reads delivered material: no fetch, signing or login. New layers implement
  `MasterLayer` and are added to `LayerStack`; the engine does not change.
- Layer order is configuration. It decides probe order under the budget, so changing it is a
  behavior change and needs the R1 parity harness.
- Recipes are data. A renamed site key is a one-line edit in `PayloadRecipes.NODE_RULES`.
- Stop rules live only in `TerminalRules`; the app hook calls `TerminalRules.blocksFallback`.

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

- R2: host rules in `TerminalRules`; `YoutubeStreamingRecipe` off.
- R3: `layers/ShapeLayer` (L3) + content-ID anchoring; ladder/request policy in `toolkit/`.
- R4: fingerprint in `verify/`. R5: codec steering in the Android capture.
- R6: `modules/youtube`. R8: L2 contract endpoints per site as `ContractLayer` recipes.
