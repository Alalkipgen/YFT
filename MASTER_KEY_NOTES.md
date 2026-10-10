# MASTER_KEY_NOTES — all solution options (notes)

Status: **notes only.** No code, extractor, workflow or flag is changed.
Date: 2026-10-10. Companion to `MASTER_KEY_PLAN.md` (MK-1).
Sources:

- `main` @ `34a4189`
- `spike/master-extractor-backup` @ `7d18926`
- the open-source projects listed in `MASTER_KEY_PLAN.md` §6

## မြန်မာ အနှစ်ချုပ်

- **YouTube ကိုယ်ပိုင် module:** main ရဲ့ YouTube code အများစုက ကိုယ်ပိုင် code ဖြစ်ပြီးသားပါ။ yt-dlp နဲ့ ပတ်သက်တာ ၂ ခုပဲ ရှိပါတယ်:
  - client တန်ဖိုး data
  - n/sig solver (ejs)
  - နှစ်ခုလုံးကို ကိုယ်ပိုင်နဲ့ အစားထိုးလို့ ရပါတယ် (§1)။
- **JS ပြောင်းလဲလည်း မပျက်မယ့်နည်း:** site ရဲ့ JS ကို မဖတ်ဘဲ browser က လုပ်တာကို ကြည့်ပါမယ်။ Layer ၄ ဆင့် ရှိပါတယ်:
  - Capture
  - Public contract
  - ပုံစံနဲ့ ရှာတာ
  - Recipe
  - layer တစ်ခု ပျက်ရင် ကျန်တဲ့ layer က ဆက်လုပ်ပါမယ် (§2)။
- **YouTube အတွက် JS မမှီတဲ့နည်း ၃ မျိုး (§3):**
  - link တိုက်ရိုက်ပေးတဲ့ client
  - MSE playback record
  - ကိုယ်ပိုင် solver
- **ဆက်ပျက်နိုင်တာ:** login၊ bot check နဲ့ DRM က JS ပြောင်းတာ မဟုတ်ဘဲ policy ပြောင်းတာပါ။ ဒါတွေ တွေ့ရင် ရပ်ပါမယ်။ မကျော်ပါ (§4)။
- **ဆက်လုပ်ဖို့ လိုတာတွေ (§5):**
  - MSE hook နဲ့ EME hook မရှိသေးပါ။
  - ပုံစံနဲ့ ရှာတာ တစ်ဝက်ပဲ ရှိသေးပါတယ်။
  - spike က main ထက် commit ၆၁ ခု နောက်ကျနေပါတယ်။
  - YouTube နဲ့ Reddit မှာ `BOT_CHECK` ကို terminal ပြောင်းဖို့ လိုပါတယ်။

## 0. Rules for every option below

- Copy, never move. main's extractors stay unchanged. With the flag off, the app is main.
- No bypass of bot checks (YouTube, Reddit, TikTok web check), age gates, DRM, logins or paywalls. If one of them is seen, Master **stops**.
- No fake qualities. A row needs a stated or header-read size **and** a probe that opened the file.
- Licences:
  - Ideas, structure and code *reference* are fine from Unlicense, MIT, BSD and ISC projects, with a provenance note.
  - GPL/AGPL projects are ideas only.

## 1. Own YouTube module (no third-party extractor)

### 1.1 What main uses from outside today

| Part | Today | Own replacement | Effort |
|---|---|---|---|
| Client chain, `YouTubePlayerResponseParser`, `Offers`/`Verdicts`, bot-check detection | YFT's own Kotlin | Copy into Master and restructure | Medium |
| PoToken (`BotGuardPoTokenProvider`, `WebViewBotGuardEngine`, `assets/youtube-potoken/`) | YFT's own code. BgUtils (MIT) was a protocol reference only; no code copied. The BotGuard program is YouTube's own and is downloaded at run time. | Nothing to replace | — |
| Device client values (`YouTubeClientProfile`, `DEVICE_VALUES_SOURCE = "yt-dlp 2026.08.19"`: VISIONOS, ANDROID, mweb agent) | Data copied from yt-dlp `INNERTUBE_CLIENTS` (Unlicense) | Own versioned table, refreshed by our own checks. These values are facts about YouTube's apps, so any source must match them. | Low |
| n/sig solver (`assets/youtube-solver/yt.solver.core.min.js` 6,945 B; `yt.solver.lib.min.js` 151,561 B = meriyah 6.1.4 ISC + astring 1.9.0 MIT; yt-dlp ejs 0.8.0 Unlicense) | Bundled third-party JS | Own solver core (§1.3) | High, plus ongoing upkeep |

### 1.2 Steps

1. **YT-1:** copy main's YouTube code into Master (`extractor-master/.../youtube`) with provenance headers. Restructure it to the Master layers. Keep main's path untouched.
2. **YT-2:** own client-value table (versioned data). A canary checks every day that VISIONOS still answers with direct URLs. A value is changed only when the canary fails.
3. **YT-3:** own n/sig solver (§1.3), used only as a speed layer (§3).
4. **YT-4:** do not implement SABR. Prefer clients that return direct URLs. A SABR-only format is never a row.

### 1.3 Own n/sig solver: options

| Option | How | Robustness | Note |
|---|---|---|---|
| A. Own core + general JS parser library (recommended) | Parse YouTube's `base.js` with a general parser (meriyah or similar; a parser, not an extractor). Find the sig and n functions by **structure** (data flow), not by names. Extract each function with its helpers. Run it in the existing sandbox (`WebViewSolverEngine`: no network, no cookies). | Good | The core is small (the ejs core is about 7 KB minified) |
| B. Own core + own parser | As A, with a hand-written JS parser | Good, but heavy | Writing and maintaining a full JS grammar is a big job |
| C. Regex/name search (youtube-dl 2015/2021 style) | Find function names with regexes and run them | Weak | This is why yt-dlp moved to ejs; not recommended |

Verification:

- Run the public test vectors (`scripts/youtube-solver-vectors.json`, from ejs tests, not shipped) through `scripts/verify-youtube-solver.mjs`.
- A live canary on the current player.
- Parity: the own solver's output must equal main's ejs output on the same player before the own solver is trusted.

Upkeep: YouTube changes the player every few weeks. When the own solver breaks, §3 (a) and (b) keep working, so a break becomes slower lookups instead of failed ones.

## 2. JS-change-resistant design (all sites)

Principle:

- Sites change their **own JS** often.
- They cannot change **web standards**: fetch/XHR, `<video>`, Media Source Extensions (MSE), Encrypted Media Extensions (EME), HLS, DASH and MP4.
- They rarely change **public contracts** that other websites depend on: embed players, oEmbed, OG tags, JSON-LD and embed widget data.
- The site's own player still runs its own JS correctly in a real browser engine. Master watches what that player does.

### 2.1 Four layers (fall through automatically)

| Layer | Depends on | If site JS changes | Sites |
|---|---|---|---|
| L1 Browser capture | Network requests, `<video>` element, MSE, EME | Keeps working | FB, IG, TikTok, X, Vimeo, Generic (YouTube: §3 b) |
| L2 Public contracts | Embed pages, oEmbed, OG/JSON-LD, embed widget answers | Rarely breaks | All |
| L3 Shape-based search | JSON objects that *look like* media, not fixed key paths | Survives key renames | FB, IG, TikTok, X, Vimeo |
| L4 Site recipe | Exact paths, endpoints, agents (today's adapters) | Can break | Speed-up only |

**L1 details:**

- Already in `yft-master-capture.js`: `fetch`, `XMLHttpRequest`, `performance.getEntries` and `HTMLMediaElement.currentSrc`.
- To add:
  - **MSE hooks:** `MediaSource.addSourceBuffer` gives the mime/codecs. `SourceBuffer.appendBuffer` gives the init segment and metadata, and bytes only for §3 (b).
  - **EME hook:** `navigator.requestMediaKeySystemAccess` means DRM, so stop.
- Playback starts with the standard muted `video.play()` only.
- Byte-range pieces collapse to one whole file with the existing `MediaFileUrls.wholeFile`.

**L2 candidates (check each one live at the start of its MK step):**

- Facebook: OG tags and the embedded video plugin page.
- Instagram: `/p/{code}/embed/captioned/` (main already uses it).
- TikTok: the public embed player page and oEmbed (metadata).
- X: the embed widget's syndication answer (main already uses it).
- Vimeo: `player.vimeo.com` config and oEmbed (metadata).
- YouTube: oEmbed and embed (metadata only).
- Generic: JSON-LD `VideoObject`, `og:video`, `twitter:player:stream`.

**L3 rules:**

- An object counts as a media object when it has:
  - an https URL;
  - a video mime type, a media extension or a known media host;
  - at least one of width, height, bitrate or duration;
  - and the expected content ID in the same object or an ancestor.
- Objects are scored, and only probed results are used.
- This replaces fixed paths such as `__DEFAULT_SCOPE__.webapp.video-detail`, which TikTok has already renamed several times.

**L4:** today's adapters' paths and endpoints, used as a fast path. If they fail or disagree, L1 to L3 decide.

### 2.2 Consensus and canary

- **Consensus:** the main video must be confirmed by at least two layers. Example: the L1 playing element's duration equals the L3/L2 duration within ±2 s and has the same content ID. If not, the result is `NeedsSelection`.
- **Canary:** an opt-in daily run over the parity URL list (`MASTER_KEY_PLAN.md` §4.3). It reports which layer failed for which site. It is owner-run, not part of default CI.

### 2.3 What capture gets per site

| Site | Capture sees | Missing qualities from |
|---|---|---|
| Facebook / Instagram | Byte-range requests of whole MP4 tracks; collapsed to whole files | L3 inline DASH / `video_versions`, then probe |
| TikTok | The whole MP4 with its cookie (live-checked on spike) | L3 `bitrateInfo`, then probe |
| X | HLS playlist / MP4 on `video.twimg.com` | HLS master variants |
| Vimeo | HLS/DASH manifest | The manifest's variants |
| Generic | Files, manifests, playing element | Manifest variants |

Limit: a player fetches only the quality it is playing. Other qualities come from L2/L3 or the manifest, and every one is probed. Nothing is synthesized.

## 3. YouTube without depending on its JS

| Option | How | JS change | SABR | Cost |
|---|---|---|---|---|
| (a) Direct-URL clients | VISIONOS first (already main P14), then other clients that return URLs | Not affected | Not affected while such clients exist | Low |
| (b) MSE playback recording | A hidden WebView plays YouTube's own player (muted). Master records the bytes the player appends to MSE (`SourceBuffer.appendBuffer`). They are reassembled from init + media segments and remuxed to a file. | Not affected | Not affected | Slow (needs playback), quality chosen by the player, battery and storage use |
| (c) Own n/sig solver | §1.3, as a speed layer | Breaks sometimes | — | High upkeep |

Order: (a) → (c) → (b). A failure of (c) never fails the lookup if (a) or (b) works.

Rules for (b):

- Stop at once if EME (DRM), a bot check, a login wall or an age gate appears.
- Record only one continuous playback. Gaps mean failure.
- Check the duration within ±2 s.
- Write to a file, not to memory, with a size cap.
- Playback speed and background throttling must be measured before any promise is made.

## 4. Breaks that are not JS changes (policy changes)

| Change | Master's answer |
|---|---|
| Login required / private | Stop: `LOGIN_REQUIRED` / `PRIVATE_OR_UNAVAILABLE` |
| Bot check / web check | Stop: `BOT_CHECK`. Only the site's own user-visible check ("Show check") |
| DRM (EME) | Stop: `DRM_PROTECTED` |
| WebView blocked or served a broken player | main's `BrowserUserAgent` (drops `; wv`, `Version/4.0`) |
| Every YouTube client becomes SABR-only | Only §3 (b) remains |

## 5. Current gaps and findings (to fix in later MK steps)

1. The spike base `a9eea7ba` is **61 commits behind main**. The spike has no X/Instagram adapters, P45, P50 or TikTok P46/P47. Fix: MK-2 merges main into the spike.
2. `MasterFallbackEngine.BROWSER_REQUIRED` lets `BOT_CHECK`, `LOGIN_REQUIRED` and `PLAYER_SCRIPT_REQUIRED` continue after the user starts playback. They are also missing from `BrowserMasterFallback.NEVER_CAPTURE`. Fix: MK-4 makes them terminal for YouTube and Reddit hosts.
3. `PayloadMediaReader.youtube()` makes rows from `streamingData` URLs without n/sig, so such files may throttle. Fix: MK-4 turns it off for YouTube.
4. The MSE and EME hooks are missing from `yft-master-capture.js`. Fix: new step after MK-5.
5. L3 is only partial: `PayloadMediaReader` still uses fixed keys. Fix: MK-3 toolkit (T3/T4 plus the L3 rules).

## 6. Where each option goes in the MK plan

| Option | MK step |
|---|---|
| Base sync, parity harness, canary list | MK-2 |
| L3 shape search, toolkit | MK-3 |
| YouTube module YT-1/YT-2, terminal bot-check rules, `PayloadMediaReader.youtube()` off | MK-4 |
| L1 MSE/EME hooks, L2 contracts for Generic | MK-5 (+ a new MSE/EME sub-step) |
| L2/L3 per site (Vimeo → X → Facebook → Instagram → TikTok) | MK-6 … MK-10 |
| YT-3 own n/sig solver (parity against ejs) | New track after MK-4, behind a flag |
| §3 (b) YouTube MSE recording | Experimental, last, behind a flag, after measurements |
| Master primary experiment | MK-11 (owner decision) |
