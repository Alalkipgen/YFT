# Master Key — Phase 1 Plan (R1–R9)

Status: **plan + prompt.** No code is changed by this document.
Date: 2026-10-10. Branch: `spike/master-extractor-backup` only.
Reads: `MASTER_KEY_PLAN.md` (MK-1) and `MASTER_KEY_NOTES.md` (§8 recommended plan).

Phase 1 turns the §8 recommendation into one ordered, buildable plan. It keeps every Master Key rule: flag off = main, no bypass, no fake qualities, copy-not-move, YouTube JS stays in its own module.

## မြန်မာ အနှစ်ချုပ်

- Phase 1 မှာ R1 ကနေ R9 အထိ အဆင့် ၉ ခု ရှိပါတယ်။
- R1 (base sync + harness) ပြီးမှ ကျန်တာ လုပ်လို့ရပါတယ်။
- R2 (safety) ကို ဘယ်အရာမှ မလုပ်ခင် အရင်ဆုံး လုပ်ရပါမယ်။
- R3 ကနေ R9 အထိ တစ်ဆင့်ချင်း လုပ်ပြီး အဆင့်တိုင်း flag-off gate နဲ့ parity test အောင်မှ ဆက်သွားပါမယ်။
- Flag ပိတ်ထားရင် app က main နဲ့ အတူတူပါပဲ။ DRM၊ bot check နဲ့ login မကျော်ပါ။ Quality အတု မပြပါ။

## 0. Guardrails (R1–R9 အားလုံး)

1. **Flag off = main.** `-Pyft.masterCapture=false` (default). CI နဲ့ `MasterMainMoreSheetTest` (flag-off) အစဉ် စိမ်း။
2. **Copy, never move.** main's extractors မပြင်ရ။ Pure helper တွေကိုပဲ Master toolkit ထဲ provenance header နဲ့ copy; whole extractor ကို `SiteExtractor` interface ကနေ ခေါ်။
3. **No bypass.** DRM (EME)၊ bot check၊ login၊ age gate၊ paywall မကျော်ရ။ တွေ့ရင် structured reason နဲ့ ရပ်။
4. **No fake qualities.** Row တစ်ခုက stated/header-read size **ရော** probe (one-byte check / manifest) အောင်မှ ဖြစ်ရ။ SABR-only / URL-less format = row မဟုတ်။
5. **Spike only.** `spike/master-extractor-backup` branch တစ်ခုတည်း။ main merge/release/tag မလုပ်။
6. **Download honesty.** Download ပြီးတိုင်း merge → normal MP4 remux → duration ±2 s + track နှစ်ခု စစ်။
7. **Licences.** Adapt (notice နဲ့) ကို Unlicense/MIT/BSD/ISC ကနေပဲ။ GPL/AGPL = idea only.

## 1. Status board

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| A0 | Architecture step A: packages/layers, same behavior ([MASTER_KEY_ARCHITECTURE.md](MASTER_KEY_ARCHITECTURE.md)) | — | Medium | Done |
| R1 | Base sync + parity harness + canary | — | Medium | Done |
| R2 | Safety: terminal bot-check/DRM; YouTube payload off | R1 | Low–Medium | Done |
| R3 | Toolkit T1/T2 + L3 shape search + ID anchoring + ladder/probe/policy | R2 | Medium | Done |
| R4 | C: keyframe/duration fingerprint | R3 | Low–Medium | Done |
| R5 | E: codec steering | R3 | Low | Done |
| R6 | Own YouTube module (YT-1/YT-2/YT-4) | R2, R3 | Medium | Done |
| R7 | Generic: capture + MSE/EME metadata hooks | R3, R4 | Medium | Done |
| R8 | Per site: Vimeo → X → Facebook → TikTok → Instagram | R3, R4, R5 | Medium–High | Done (offline; live owner-run) |
| R9 | Signed recipe config (optional) | R8 | Medium | Done (offline; key + hosted config owner-run) |

Order: **R1 → R2 → R3 → (R4, R5 parallel) → R6 → R7 → R8 → R9.** R6 may run beside R4/R5 after R3.

## 2. Tasks

Each task below lists scope, the main files it touches, done criteria and tests. All work is behind the capture flag and leaves the flag-off path identical to main.

### R1 — Base sync + parity harness + canary

- **Scope:** merge `origin/main` into the spike (spike only); resolve conflicts in the hook files only; freeze the test-URL list; add the parity harness and the canary runner.
- **Files:** whole tree (merge); `extractor-master*/**` for the harness; new `parity-urls.json`; a new opt-in instrumented test `MasterParityLiveTest` (runs only with `-e yft.parity 1`); optional `scripts/canary.*`.
- **Conflicts to expect:** `app/.../detection/master/BrowserMasterFallback.kt`, `app/.../feature/browser/BrowserViewModel.kt`, `BrowserScreen.kt` (the spike hooks). Keep main's new code; re-apply the Master hook around it.
- **Done:** spike builds; flag-off CI + `MasterMainMoreSheetTest` green; harness writes a JSON report (host + content ID only, no signed URLs); `git rev-list --count a9eea7ba..HEAD` matches the new main.
- **Note:** the canary runs on a phone or emulator only (server IPs hit login walls). Not in default CI.

### R2 — Safety gaps (do before any capture widening)

- **Scope:** make `BOT_CHECK`, `LOGIN_REQUIRED`, `PLAYER_SCRIPT_REQUIRED` terminal for `youtube.com`/`youtu.be` and `reddit.com`; stop when an EME/DRM signal appears; disable `recipes/YoutubeStreamingRecipe` for YouTube hosts (only the module answers YouTube).
- **Files:** `extractor-master/.../policy/TerminalRules.kt` (`BROWSER_REQUIRED`, host rules; the engine and `BrowserMasterFallback` both read it since architecture step A), `extractor-master/.../recipes/YoutubeStreamingRecipe.kt` (host gate).
- **Done:** unit tests — a bot-check request makes 0 extra site requests and 0 capture; a YouTube payload yields 0 Master rows; a DRM signal returns `DRM_PROTECTED`.

### R3 — Toolkit + L3 shape search

- **Scope:** copy pure helpers with provenance headers; add the shape-based media search with **content-ID anchoring**; JSON finders (scripts by `id`/type, `var x = {…}`, JSON inside strings, HTML-entity retry); quality ladder (best bitrate per size, AVC first, best AAC, de-dupe, join by height+codec), probe rounds, request policy (same-origin cookie, re-anchored Referer/Origin, strip on cross-origin, answer cookies to their media origin only).
- **Files:** new `extractor-master/.../layers/ShapeLayer.kt` + `toolkit/**` (PageScripts, BalancedJson, AnchoredMediaWalk, MediaKeyTable, QualityLadder, ProbeRounds, RequestPolicy); copy fixtures under `extractor-master/src/test/resources/`.
- **Done:** on committed fixtures, shape results ⊇ fixed-key results; renamed keys do not reduce hits; the "suggested/related" video is rejected by anchoring; all negative fixtures give 0 media; a drift-check script compares each copy against main.

### R4 — C: keyframe / duration fingerprint

- **Scope:** read DASH `sidx` and HLS `#EXTINF` segment timing; for progressive files compare duration ±2 s; group same-video qualities; reject ads/related/previews by fingerprint.
- **Files:** extend `Mp4HeaderParser` copy (add `sidx`) or a new `SegmentIndexReader`; `extractor-master-android/.../MasterMainSelection.kt` already has duration ±2 s; wire the fingerprint into selection.
- **Done:** fixtures where several files are one video's qualities are grouped; an ad/related fixture is rejected; no synthesized row.

### R5 — E: codec steering

- **Scope:** a document-start script that answers `MediaSource.isTypeSupported` / `MediaCapabilities.decodingInfo` to match `DeviceMergeSupport` (AVC/AAC everywhere; VP9/Opus on Android 10+; AV1 off); keep main's Facebook Safari AVC ladder as the site-specific case.
- **Files:** `extractor-master-android/src/main/assets/yft-master-capture.js`; `extractor-master-android/.../WebViewPlaybackCapture.kt`.
- **Done:** parity — the AVC ladder appears where the site supports it; 1440p/2160p are kept when VP9 can be merged; nothing offered that the phone cannot play/merge.

### R6 — Own YouTube module

- **Scope:** YT-1 copy main's YouTube code into Master with provenance; YT-2 own versioned client table (VISIONOS first) + a canary; YT-4 never SABR. Streams needing n/sig are **not offered by Master**; main (flag off) still covers them. No third-party extractor in Master.
- **Files:** new `extractor-master/.../youtube/**`, `MasterSiteModule`, `SiteExtractorModule`; app DI passes the same client/providers.
- **Done:** passthrough parity with main's YouTube fixtures (same rows, order, labels, sizes); SABR/cipher fixtures → 0 rows; bot-check fixture → terminal, 0 capture.

### R7 — Generic web + MSE/EME hooks

- **Scope:** L1 capture as the default for sites with no adapter; add MSE metadata hooks (`addSourceBuffer` codec, init-segment resolution) and the EME hook (`requestMediaKeySystemAccess` → stop); reuse `PlayerSetupScanner`, `PageFactsReader`, ad rules and R4.
- **Files:** `yft-master-capture.js`, `WebViewPlaybackCapture.kt`, `layers/`/`toolkit/` for generic.
- **Done:** parity on the generic URLs (plain `<video>`, HLS, DASH, VAST pre-roll); EME page stops with `DRM_PROTECTED`.

### R8 — Per site (L2 first, then L3 + capture)

Order and the live-verified L2 contract for each:

1. **Vimeo** — player config (`player.vimeo.com/video/{id}/config`); host lock kept.
2. **X** — syndication (`cdn.syndication.twimg.com/tweet-result`, computed token); host lock kept.
3. **Facebook** — the video **plugin page** (`/plugins/video.php?href=`, exposes `hd_src`/`sd_src`/`dash_manifest` with a matching `video_id`) + page data; Safari AVC ladder kept.
4. **TikTok** — **embed v2** (`/embed/v2/{id}`, exposes file URLs under `playUrl`) + page data; `tt_chain_token` and agent rules kept.
5. **Instagram** — page data / v1 / GraphQL as a recipe (`doc_id`, app ID, CSRF, session gating); re-check the embed from a phone.
- **Files:** `extractor-master/.../recipes/**` (data: endpoints, host locks, key tables), toolkit from R3.
- **Done:** each site passes `MASTER_KEY_PLAN.md` §4.2 (main video ±2 s, rows ≥ adapter, every row probed, download+play, time budget, negatives match).

### R9 — Signed recipe config (optional)

- **Scope:** data-only config (keys, endpoints, `doc_id`, agents), signed and schema-checked, with bundled defaults as the fallback, hosted on GitHub. **No code is ever loaded remotely.**
- **Files:** new `extractor-master/.../recipes/RemoteRecipe*.kt`; a config schema; defaults in assets.
- **Done:** a bad or unsigned config is ignored and the bundled defaults keep working; coverage is only the ~30–40% of breakages that are data-only.

## 3. Branch and push

- Work on `spike/master-extractor-backup` only. Checkpoint-push after each task.
- Docs-only pushes (`*.md`, `docs/`) start no CI.
- Never merge to main; never tag or release.

## 4. Done for Phase 1

- R1–R8 merged on the spike, each past §4.2 for its scope.
- Flag-off: app == main; CI + `MasterMainMoreSheetTest` green throughout.
- The canary runs clean on a phone/emulator for the frozen URL list.
- R9 is optional and may be deferred.
