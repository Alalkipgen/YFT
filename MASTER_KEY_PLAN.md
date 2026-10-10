# MASTER_KEY_PLAN — MK-1 analysis (read-only)

Status: **MK-1 done: analysis only.** This step changed no code, extractor, workflow or flag.
Date: 2026-10-10.
Inputs:

- `main` @ `34a4189` (P48–P50), read in a detached read-only worktree. Its extractors were not changed.
- `spike/master-extractor-backup` @ `c138a5b` (Master opt-in, M2).
- Open-source projects listed in §6, read at the commits named there. They were studied only. No code was copied in this step.

Goal: grow Master from a **secondary fallback** into a "master key". A master key is one engine that can **Fetch, Capture & Extract** most public videos without a full per-site extractor. Each site keeps a thin recipe (data), and code modules are kept only where we cannot avoid them (YouTube).

## မြန်မာ အနှစ်ချုပ်

- ✅ main ရဲ့ Facebook, YouTube, TikTok, Instagram, X, Vimeo နဲ့ Generic Web extractor တွေကို ဖတ်ပြီးပါပြီ။ ဗီဒီယိုရှာနည်း၊ API၊ manifest၊ hidden page၊ header/cookie၊ quality ပေါင်းနည်းနဲ့ ကြော်ငြာခွဲနည်းကို §1 မှာ ရေးထားပါတယ်။
- ✅ Master ထဲကို **copy** ယူလို့ရတဲ့ logic (JSON ရှာနည်း၊ video ID နဲ့ ချိတ်ရှာနည်း၊ DASH track ပေါင်းနည်း၊ one-byte check၊ cookie စည်းမျဉ်း) နဲ့ site မှာပဲ ထားရမယ့် logic ကို ခွဲထားပါတယ် (§2)။
- ✅ YouTube ကို Master ထဲကနေ module အဖြစ် ခေါ်ပါမယ် (§3)။ main ရဲ့ `YouTubeExtractor` ကို copy မလုပ်ဘဲ `SiteExtractor` interface ကနေ ခေါ်မှာပါ။ Bot check ဖြစ်ရင် Master က ရပ်ပါမယ်။ ကျော်ဖို့ မကြိုးစားပါ။
- ✅ Parity test အစီအစဉ်၊ URL စာရင်းနဲ့ pass criteria ကို §4 မှာ ရေးထားပါတယ်။ Site အလိုက် migration အစီအစဉ်၊ ခက်ခဲမှုနဲ့ risk ကို §5 မှာ ရေးထားပါတယ်။
- ✅ yt-dlp၊ youtube-dl (အသစ်/အဟောင်း)၊ cobalt၊ NewPipe၊ streamlink၊ gallery-dl၊ lux၊ you-get တို့ကို လေ့လာပြီးပါပြီ။ ဘာတွေ ပြောင်းသွားလဲ၊ ဘာတွေ မပြောင်းလဲ ဆိုတာကို §6 မှာ ရေးထားပါတယ်။
- ⚠️ တွေ့ရှိချက် ၂ ခု ရှိပါတယ်:
  - (က) spike က main ထက် commit ၆၁ ခု နောက်ကျနေပါတယ်။ X နဲ့ Instagram adapter တွေ spike မှာ မရှိသေးပါ။
  - (ခ) spike Master က `BOT_CHECK` ကို "user ကိုယ်တိုင် play ပြီးမှ capture" အဖြစ် ကိုင်တွယ်နေပါတယ်။ YouTube နဲ့ Reddit အတွက် MK-4 မှာ terminal (ချက်ချင်းရပ်) အဖြစ် ပြောင်းရပါမယ်။

## 0. Ground rules (apply to every MK step)

1. **Copy, never move.** main's extractors stay unchanged. Master uses them in one of two ways:
   - Pure helpers are *copied* into a Master toolkit. Each copy carries a provenance header with the source file and the main sha.
   - Whole extractors are *called* through the public `SiteExtractor` interface, as modules.
2. **Secondary fallback.** `-Pyft.masterCapture` defaults to `false`. With the flag off, the app is main. `MasterMainMoreSheetTest` (flag-off case) and the CI suite already prove this. If a "Master primary" mode is tried later, it gets a *separate* flag that is also off by default.
3. **No bot-check bypass** (YouTube, Reddit, TikTok's web check). `BOT_CHECK` is terminal for Master: no capture, no retry, no solver. The only path forward is the site's own user-visible check that main already shows ("Show check").
4. **No fake qualities.** A row exists only when the site's data or the file's own header states its picture size, *and* a probe (one-byte check or manifest read) opened the file. Labels are never interpolated, upscaled or renamed. SABR-only or URL-less formats are not rows.
5. **ADR-006 limits.** Public videos only. No DRM, paid, private or age-gate bypass. Adapters never sign in. They may replay the user's own browser session only where main already does.
6. **Licences.** YFT is MIT.
   - Code may be *adapted* (with a notice in `docs/THIRD_PARTY_NOTICES.md`) only from Unlicense, MIT or BSD sources: yt-dlp, youtube-dl, lux, you-get, streamlink.
   - GPL/AGPL projects (NewPipeExtractor GPL-3.0, gallery-dl GPL-2.0, cobalt AGPL-3.0) are **ideas only**.

## 1. How each main extractor finds the video

### 1.0 Shared frame (`extractor-api`, app `detection/`)

- **Contract** (`SiteExtractor.kt`):
  - `identify(url)` is pure and offline. It returns `SitePageIdentity(siteId, contentId, canonicalPageUrl, requiresCanonicalResolution)`.
  - `extract(SiteExtractionRequest(identity, requestContext, nowEpochMs, pageData))` does the lookup. `SitePageData` is ≤ 64 KB of JSON from `TAB_SCRIPT`, `TAB_API_ANSWER` or `HIDDEN_PAGE`.
  - Failures form a closed enum, `SiteExtractionFailure`. `allowsGenericFallback` is false for DRM, LOGIN, BOT_CHECK, PRIVATE, GEO and PLAYER_SCRIPT.
- **Transport** (`ExtractorHttpClient`):
  - `get`, `postJson` and `probe` (one-byte check that returns `Answered(totalBytes)`, `Refused` or `Unsupported`).
  - Rules: HTTPS only, a redirect limit, a call timeout and `maxBodyBytes`.
  - `ResponseCookie` keeps response cookies in memory for one lookup, and only same-domain ones.
- **Host services:**
  - `PlayerScriptRunner`: app `YouTubePlayerScriptRunner` with the bundled yt-dlp EJS solver (`script/EjsSolverProtocol`, `WebViewSolverEngine`).
  - `PoTokenProvider`: app `potoken/BotGuardPoTokenProvider` with `WebViewBotGuardEngine`.
- **Orchestration** (app):
  - `SiteAdapterCoordinator` runs one adapter, keeps generic as the fallback, re-anchors candidates to the live page and applies `MergeSupport`/`DeviceMergeSupport`.
  - `SiteAdapterModule` lists adapters explicitly.
  - `SiteLookupCache` (P17) keeps one lookup per video.
  - `TabDataSource` and `HiddenPageReader` (P40) read page data from the tab or a hidden page.
  - `TabPageReader` (P37) quietly re-reads the tab's page.
  - `WebViewBrowserReads` (P45) re-asks refused links through the WebView's own network stack.

### 1.1 Facebook (`extractor-sites/.../facebook`, 7 files, about 1.8k lines)

- **Order** (`FacebookExtractor`):
  1. Reels: `publicPage`/`publicFiles` first asks as **desktop Safari with no session**. This is the AVC ladder (P15/P23); a Chromium user agent gets only AV1/VP9.
  2. Otherwise: one session page GET. It uses `FacebookPageIdentity.forPageRequest` (desktop Chrome UA), the user's Cookie, Referer facebook.com and a 6 MB cap.
  3. `resolvedIdentity(finalUrl)` makes share, fb.watch and post links addressable.
- **Page data** (`FacebookPageParser`):
  - `scriptPayloads` reads bounded `<script type="application/json">` blocks.
  - `mediaNode` finds the node that carries `MEDIA_KEYS` (videoDeliveryResponseFragment, playable_url, playable_url_quality_hd, browser_native_hd_url, browser_native_sd_url) or `LEGACY_FIELDS`. It **prefers the expected video ID**, because related videos share the same payload.
  - `metaContent` reads OG tags.
- **API:** none.
- **Manifest:** `inlineManifests` reads `dash_manifest_xml_string`/`dash_manifest`. `FacebookDashManifests` turns them into whole-file tracks (`FacebookDashTrack`, on-demand BaseURL, `durationMillis`, `frameRate`).
- **Hidden page:** none. Tab data is not used.
- **Headers/cookies:**
  - `publicPageHeaders` builds the page request headers.
  - `mediaContext` re-anchors media requests to the page.
  - The resolver strips cookies on cross-origin hops.
- **Quality merge** (`FacebookDashOffers`):
  - Each picture size keeps its highest-bitrate video. AVC is preferred (`bestAvc`), and each video is merged with the best AAC (`audio`).
  - At most 6 video offers. `merged()` removes duplicates across pages.
  - `encodingOf` maps HD/SD files to tracks. `needsAvcLadder` and `hasAvcWithSound` decide whether the Safari ladder is needed.
  - Bitrate comes from `FacebookUrls.statedBitrate` (`bitrate` or base64 `efg`). `FacebookQualityMetadata` and `estimatedBytes` fill in sizes.
- **Wrong video / ads:** anchoring to the expected ID. `ACCESS_MARKERS` are read **only after no media was found**. `FacebookUrls.isAccessWall` detects access walls.
- **Failures:** `LINE_FAILURES`/`failureFor`. Expiry comes from `FacebookUrls.mediaExpiryEpochMs` (`oe` hex, `_nc_exp`, `expire`).

### 1.2 YouTube (`.../youtube`, 5 files, about 2.2k lines, plus app host services)

- **Order** (`YouTubeExtractor.visionOsFirst`):
  1. The VISIONOS InnerTube client first (P14, about 17 KB, direct URLs, no player script or PoToken).
  2. Otherwise the watch page. `YouTubeUrls.watchPageFetchUrl` adds content-warning parameters only; it is not an age bypass. `YouTubePlayerResponseParser.slicePlayerResponse` slices `ytInitialPlayerResponse` by brace balance. `YouTubePageSignals` gives loggedIn, dataSyncId, sessionIndex and contentBoundPoToken.
  3. Then the client chain (`askFallbacks`, `visionOsInChain`): VISIONOS again with visitor data (`X-Goog-Visitor-Id`), the embedded player (`YFT_EMBED_URL`, `thirdPartyEmbedUrl`), the page's web client with the session (`YouTubeSessionAuth` SAPISID-style headers), mweb, and finally `askAndroidLast` (progressive 360p).
- **API:** `/youtubei/v1/player` through `ExtractorHttpClient.postJson`, with the body from `YouTubeClientProfile.playerRequestBody`. Device values come from yt-dlp 2026.08.19 (`DEVICE_VALUES_SOURCE`, Unlicense).
- **Player JS:** `PlayerScriptRunner` computes n/sig. A stream whose transform fails is dropped.
- **PoToken:** `PoTokenProvider`. The binding is either the content (video ID) or the visitor/dataSync identity, and a token is minted once per lookup (`mintedTokens`, `TokenUse`).
- **Manifest/SABR:** adaptive formats are whole files, downloaded in ranged chunks. `YouTubeResponseSummary.sabr`, `sabrOnly` and `adaptiveWithoutAddress` detect SABR. SABR-only formats are **not rows**.
- **Quality:**
  - `select` and `bestAudio` pick the original, non-DRC AAC.
  - `best` picks AVC for each of `MERGED_QUALITIES` (144–1080).
  - `HIGH_QUALITIES` (1440/2160) use VP9+Opus WebM (`isSdrVp9`), else AV1+AAC.
  - `Offers` removes duplicates (`sameRow`) and tracks `isComplete` and `hasVideoWithSound`.
- **Verdicts:** tiers are `FALLBACK < PAGE < DELIVERY`. Bot check is a PAGE verdict (`YouTubePlayerResponseParser.BOT_CHECK` text) and maps to `BOT_CHECK`. `playability`/`reasonText` map to LOGIN, PRIVATE, GEO and DRM. Age is never acknowledged.
- **Headers/cookies:** `playerHeaders` and `mediaContext`. The media cookie is **never sent to googlevideo**. `YouTubeUrls.isMediaServerRequest` and `expiryOf` handle media hosts and expiry.

### 1.3 TikTok (`.../tiktok`, 5 files, about 1.6k lines, plus app `detection/tiktok`)

- **Order** (`TikTokExtractor`):
  1. (P40) `fromPageData`: the post's data from the tab (`TikTokApiCapture` document-start store).
  2. The phone page with the WebView's own agent (`TikTokAgents.phone`, never "YFT").
  3. The desktop page (`TikTokAgents.desktop`, desktop Chrome with the same major version) when the phone page fails or lists fewer than 2 qualities. `moreQualities` and `joinedTo` join rows by height and codec (P46/P47).
  4. The hidden page (`TikTokPageEngine` with `WebViewHiddenPages`: desktop Chrome, images off, media answered empty, shared cookie store).
  5. Optionally `TikTokDownloadService` (tikwm.com; owner choice B, `askDownloadService`/`TT_SERVICE`). It receives the post address only, never a cookie.
- **Page data** (`TikTokPageParser`):
  - `scripts()` finds scripts by their **`id` attribute**: `__UNIVERSAL_DATA_FOR_REHYDRATION__`, then any `__DEFAULT_SCOPE__` key that holds `itemInfo.itemStruct` (`webapp.video-detail` or `webapp.reflow.video.detail`). `SIGI_STATE` is still read.
  - `lenientRead` decodes HTML entities and uses `balancedObject`.
  - `parseItem` checks the expected video ID.
- **Quality:**
  - `qualities()` reads `bitrateInfo` (every address), then `playAddr`. The same height, codec and size is one file with several addresses.
  - `heightLabel`/`minSide` map 576×1024 to 540p. `TikTokCodec` puts H.264 before H.265.
  - The watermarked `downloadAddr` is used only when nothing else opened (`watermarked`, R31).
- **Checks:** `check()` runs rounds of one-byte probes (R30), capped by `MAX_FILE_CHECKS` and `MAX_PARALLEL_CHECKS`.
- **Cookies:**
  - `jar` (R29) keeps every TikTok cookie the lookup's answers set.
  - `mediaCookie` sends that answer's `tt_chain_token` to the media origin only (R20). `mergedCookie` and `cookieFor` combine tab and answer cookies.
  - Cookies are dropped on cross-origin redirects.
- **Failures:**
  - `STATUS_FAILURES` maps TikTok status codes.
  - `TikTokPageNotes` records dataKey, json, postId, status and final.
  - `kindOf` detects photo posts and home redirects. `GENERAL_REASONS` lets a more specific reason win.
- **Identity:** `TikTokUrls.canonicalUrl` gives `/@/video/<id>`. Short links stay unresolved until `resolvedIdentity`. `isPhotoPost` and `isPlayerMedia` complete the URL helpers.

### 1.4 Instagram (`.../instagram`, 3 files, about 740 lines, P49)

- **Order:**
  1. The app API `/api/v1/media/{id}/info/`, **only when the browser has an Instagram `sessionid`**. `InstagramUrls.mediaIdOf` converts the shortcode to a media ID (base-64 alphabet).
  2. Web GraphQL: `graphql/query/?doc_id=GRAPHQL_DOC_ID`.
  3. The post page, with cookies.
  4. The public embed `/p/{code}/embed/captioned/`, with no cookie.
- **Headers** (`apiHeaders`): `X-IG-App-ID: 936619743392459`, `X-ASBD-ID: 129477`, `X-IG-WWW-Claim: 0`, and `X-CSRFToken` from the `csrftoken` cookie.
- **Page data:**
  - `InstagramPageDocuments` reads `application/json` scripts, the embed's `contextJSON` string and `__additionalDataLoaded` (`stringAt`, `objectAt`).
  - `InstagramMedia.find(root, code)` reads both shapes: the v1 item (`video_versions`, `video_dash_manifest`, `carousel_media`) and GraphQL `shortcode_media` (`video_url`, `dash_info`, `edge_sidecar_to_children`).
- **Quality:**
  - `video_versions` whole MP4s (with sound) become rows.
  - The DASH manifest goes through `trackCandidates`: other picture sizes are merged with the best AAC. A silent video's sizes are left unstated, so the resolver reads them from the header.
  - Carousels: `img_index` selects the item (`offers`).
- **Failures:** `InstagramUrls.isLoginWall` (login/checkpoint). `InstagramItem.isPlayable` catches a signed-out answer that names a video without files.

### 1.5 X / Twitter (`.../x`, 3 files, about 520 lines, P48)

- **Endpoint:** `XSyndication` asks `cdn.syndication.twimg.com/tweet-result` with `token()`. The token is JavaScript's `((id/1e15)*π).toString(36)` without zeros and the point; `radixString` reproduces V8's `DoubleToRadixCString`.
- **What it does not use:** no cookie, no sign-in, no GraphQL, no guest token.
- **Quality:**
  - Every MP4 variant on `video.twimg.com` (the `isMediaUrl` host lock), highest bitrate first.
  - `candidatesOf` states the picture size from `/vid/WxH/` in the address.
  - HLS is a row only when there is no MP4. A GIF has no stated size, so the resolver reads its header (and finds no sound).
- **Identity:** `XUrls` gives the canonical `x.com/{user}/status/{id}`. `videoNumberOf` reads `/video/N`. `XPost.quoted` covers a quoted post's video.
- **Failures:** `XSyndication.Failure(unavailable)` for age-restricted, protected, deleted or photo-only posts.
- **HLS audio:** main's P50 `HlsAudioPairing` pairs HLS video qualities with their separate audio group.

### 1.6 Vimeo (`.../vimeo`, 3 files, about 630 lines)

- **Order:**
  1. The clip page.
  2. `VimeoConfigParser` finds either `InlineConfig` (via `CONFIG_MARKERS` and `objectAfter`, a string-aware brace scan with a size cap) or `ConfigAddress` (`config_url`).
  3. A `config_url` is followed **only on player.vimeo.com** (`VimeoUrls.isConfigUrl`). The fallback address is `VimeoUrls.configUrl(id, hash)`.
- **Quality:**
  - `renditions` lists progressive MP4s from highest to lowest, then HLS/DASH (`manifestUrl` prefers the default CDN).
  - The resolver reads HLS variants and pairs DASH tracks (`VimeoDelivery`).
- **Headers:** `configHeaders` sets the clip page as Referer. `mediaContext` re-anchors media requests.
- **Failures:** `ACCESS_MARKERS` are read only when there is no config. OnDemand, live and paid surfaces are not claimed. Unlisted links keep their hash (`unlistedHashOf`). Expiry uses `PLAUSIBLE_EXPIRY_SECONDS`.

### 1.7 Generic web (no adapter: `core-browser`, `extractor-generic`, `core-media`, app)

- **Home (pasted link):**
  - `LinkInspector` calls `HeadlessPageFetcher.fetch`: no cookies, a plain YFT agent, manual redirects, HTTPS only.
  - `HtmlMediaScanner` finds `<video>`/`<audio>`/`<source>`, `og:video`, JSON-LD `contentUrl` and absolute media links.
  - `PlayerSetupScanner.sources` reads player setups: JW Player, Video.js `data-setup`, Flowplayer, Clappr, Plyr, KVS `flashvars`.
  - `PageFactsReader` (with `MiniJson`) reads duration and title from meta tags and JSON-LD.
- **Browser tab:**
  - Intercepted requests go through `BrowserObservationMapper.forMetadataProbe`; byte-range pieces become one whole file.
  - Results go to `PageCandidateStore.submitFacts`, bounded by `PageProbeBudget`.
  - DOM: the `DomMediaProbe` script and `DomProbeResultParser.facts`.
  - `PlayingVideoProbe.playing` reports the playing element, its length, size and muted state. `FocusedVideoProbe` handles feed sites.
- **Normalizing:**
  - `CandidateNormalizer`: `withoutStreamPieces` and `mergedRole`.
  - `MediaFileUrls`: `wholeFile`, `isOpaqueFile` and `segmentFamily`.
  - `MediaUrlClassifier`.
- **Resolving:**
  - `ManifestReader.hls`/`dash` (`ManifestFacts`) and `MediaMetadataProbe.readManifest`.
  - `DefaultVariantResolver.resolve`: `HlsManifestParser`, `DashManifestParser`, `probeMp4Header` → `Mp4HeaderParser`, `playlistLength`, `withPageOrigin`, and `askBrowser` (P45).
  - `HlsAudioPairing` (P50).
- **Ads:**
  - `AdHosts` (`isAdHost`, `adSign`, `isAdBreakRequest`, `isAdBreakAnswer`).
  - `VastAdTracker`: a whole file that arrives soon after an ad-break request in the same frame is an ad.
  - `BrowserObservationMapper.adRole` marks ad files. Previews, thumbnails and muted loops are dropped.
  - P24/P25 main-video rule: the playing element, else the one of the same length, else the one the page names.
  - Pop-ups and redirects are handled separately (`AdRedirectPolicy`, `AdNetworks`).

### 1.8 Master today (spike, for reference)

- **Engine:**
  - `MasterFallbackEngine` runs the stages `PAGE_DATA` then `PLAYBACK_CAPTURE`.
  - `PayloadMediaReader` walks HTML and JSON with site-shaped keys: `browser_native_*`, `playable_url*`, `video_url`, `contentUrl`, `dash_manifest_url`, `video_versions`, `playAddr`/`downloadAddr`/`bitrateInfo`, `video_info.variants` and `streamingData`. It reads inline DASH through `InlineDashReader`, plus TikTok status codes and `is_private`/`isDrm`.
  - `OkHttpMediaValidator`, `UrlPolicy` and `InMemoryCaptureStore` complete it.
- **Android:**
  - `WebViewPlaybackCapture` with `yft-master-capture.js`.
  - `MasterMainSelection`: duration ±2 s, ad roles, previews, `isFarShorter`, 15 s validation window.
  - `CapturedMp4Facts` reads fragmented MP4 duration.
- **Hook:** `BrowserMasterFallback.recover` runs only when the primary lookup is not `Detected`, and never for `NEVER_CAPTURE` failures.
- **Base gap:** the spike base `a9eea7ba` is **61 commits behind main**: +6,286/−378 lines in the extractor and core modules. The spike has no X (P48) or Instagram (P49) adapter, no `WebViewBrowserReads` (P45), no `HlsAudioPairing` (P50) and no TikTok P46/P47.

## 2. Reusable in Master vs site-specific

### 2.1 Copy into a Master toolkit (`extractor-master/.../toolkit`, pure JVM, copied fixtures)

| # | Technique | Copy from (main file › function) | Master target |
|---|---|---|---|
| T1 | Find data scripts by `id`/type, never by first mention | `TikTokPageParser.scripts`, `FacebookPageParser.scriptPayloads`, `InstagramPageDocuments` (contextJSON, `__additionalDataLoaded`) | `PageScripts` |
| T2 | String-aware balanced object, lenient retry, size cap | `TikTokPageParser.balancedObject`/`lenientRead`, `VimeoConfigParser.objectAfter`, `InstagramPageDocuments.objectAt`/`stringAt`, `YouTubePlayerResponseParser.slicePlayerResponse`, `json/BoundedJson` | `BalancedJson` |
| T3 | Media-node walk **anchored to the expected content ID** | `FacebookPageParser.mediaNode`, `InstagramMedia.find`, `TikTokPageParser.parseItem` (spike `PayloadMediaReader.node` already does a version of this) | `AnchoredMediaWalk` |
| T4 | Media-key table as data | FB `MEDIA_KEYS`/`LEGACY_FIELDS`; IG `video_versions`, `video_dash_manifest`, `video_url`, `dash_info`; TikTok `bitrateInfo`/`PlayAddr.UrlList`/`playAddr`/`downloadAddr`; X `video_info.variants`; Vimeo `files.progressive/hls/dash`; JSON-LD `contentUrl`; `og:video` | `MediaKeyTable` (versioned) |
| T5 | Access verdict **only after no media** | `FacebookPageParser.ACCESS_MARKERS`, `VimeoConfigParser.ACCESS_MARKERS`, `FacebookUrls.isAccessWall`, `InstagramUrls.isLoginWall`, TikTok `STATUS_FAILURES`/`kindOf`, YouTube `playability` | `AccessVerdict` (site tables) |
| T6 | Inline/on-demand DASH → whole-file tracks | `FacebookDashManifests`, `InstagramExtractor.trackCandidates` (and spike `InlineDashReader`) | `OnDemandDash` |
| T7 | Quality ladder: best bitrate per size, AVC first, best AAC, cap, de-dupe across pages, join by height+codec | `FacebookDashOffers` (`merged`, `bestAvc`, `MAX_VIDEO_OFFERS`), `TikTokPageParser.qualities`, `TikTokExtractor.joinedTo`/`lists`/`ROW_ORDER`, `XExtractor.candidatesOf`, `YouTubeExtractor.bestAudio` | `QualityLadder` (gated by `MergeSupport`) |
| T8 | Verified rows: one-byte check rounds with caps | `TikTokExtractor.check` (R30), `ExtractorHttpClient.probe`, spike `OkHttpMediaValidator` | `ProbeRounds` |
| T9 | Request policy: same-origin cookie, re-anchored Referer/Origin, strip on cross-origin hop, answer cookies only to their media origin | `TikTokExtractor.mediaCookie`/`mergedCookie`/`cookieFor`, `ResponseCookie.matches`, FB/Vimeo/YouTube `mediaContext`, `VimeoExtractor.configHeaders`, `DefaultVariantResolver.withPageOrigin` | `RequestPolicy` |
| T10 | Expiry from the URL | `FacebookUrls.mediaExpiryEpochMs`, Vimeo `PLAUSIBLE_EXPIRY_SECONDS`, `YouTubeExtractor.expiryOf` | merge into spike `UrlPolicy.expiry` |
| T11 | Identity from the final URL; short links unresolved, never guessed | FB/TikTok `resolvedIdentity` | keep calling `SiteExtractor.identify()`; copy only `resolvedIdentity` rules |
| T12 | Metadata-only titles | `*Extractor.displayTitle` | `TitleRule` |

These shared libraries are **depended on, not copied**, because they are not extractors and main's generic path already uses them: `HtmlMediaScanner`, `PlayerSetupScanner`, `PageFactsReader`, `CandidateNormalizer`, `MediaFileUrls`, `ManifestReader`, `DefaultVariantResolver`, `Mp4HeaderParser`, `HlsAudioPairing`, `AdHosts`, `VastAdTracker`, `BrowserObservationMapper`.

### 2.2 Must stay site-specific

| Site | Stays in the module/recipe | Why |
|---|---|---|
| YouTube | The whole adapter: `YouTubeClientProfile` (device values, `PAGE_CLIENT_IDS`), the client chain/`Verdicts`/`Offers` in `YouTubeExtractor`, `YouTubeSessionAuth`, `YouTubePlayerResponseParser` (SABR, BOT_CHECK, playability), `PoTokenProvider`/`BotGuardPoTokenProvider`, `PlayerScriptRunner`/`YouTubePlayerScriptRunner`/`EjsSolverProtocol` | Attestation and JS challenges change often. Bot-check risk. SABR. |
| X | `XSyndication.token`/`radixString`, the `isMediaUrl` host lock, the endpoint | Token algorithm; host lock against redirection |
| Instagram | `GRAPHQL_DOC_ID`, `WEB_APP_ID`, `ASBD_ID`, `apiHeaders`, `mediaIdOf`, `embedUrl`, source order and session gating | `doc_id` rotates; session rules |
| TikTok | `TikTokAgents` (agent rules), the `tt_chain_token` media cookie, `TikTokPageEngine`/`WebViewHiddenPages`/`TikTokApiCapture`, `TikTokDownloadService` (owner flag), `STATUS_FAILURES` codes, the watermark rule, the `/@/video/<id>` canonical | Very sensitive to the agent; third-party service needs owner consent |
| Facebook | The AVC-ladder agent (desktop Safari, no session), `FacebookPageIdentity.forPageRequest`, URL surfaces and `pfbid`, `efg` bitrate decoding, `LINE_FAILURES` | Codec ladder depends on the agent |
| Vimeo | The `isConfigUrl` host lock, `configUrl`, the unlisted hash, `CONFIG_MARKERS`, unclaimed paid/live surfaces | Session must never follow a page-supplied host |

**Master Key shape** = universal engine (T1–T12, generic heuristics, capture) + **recipes** per site (data: agents, endpoints, host locks, cookie rules, key tables) + **code modules** only where needed (YouTube, and possibly the X token).

## 3. Calling YouTube as a module inside Master

```
MasterOrchestrator (extractor-master)
 ├─ identify   : SiteExtractorRegistry.select(url)            // pure, unchanged
 ├─ module slot: MasterSiteModule("youtube")
 │               = SiteExtractorModule(delegate = main's YouTubeExtractor instance from SiteAdapterModule)
 │               extract(SiteExtractionRequest) -> SiteExtractionResult   // unchanged result
 ├─ verify     : identity match, every row has an address, no SABR-only row, probe result kept
 └─ present    : MasterMainPresentation (main + More)
```

- **New contracts (MK-4, in extractor-master):** `interface MasterSiteModule { val siteId; fun claims(identity); suspend fun extract(request) }` and `class SiteExtractorModule(delegate: SiteExtractor)`.
  - App DI passes the **same** `YouTubeExtractor` that `SiteAdapterModule` builds, with the same `ExtractorHttpClient`, `PoTokenProvider` and `PlayerScriptRunner`.
  - Nothing from YouTube is copied, so there is no drift. Master imports only the public `SiteExtractor` API.
- **One lookup per video:** share the `SiteLookupCache` key `youtube:<id>`. As a *fallback*, Master never calls a failed YouTube adapter again. This is already the engine rule in the `PlaybackCaptureProvider` doc.
- **Terminal failures for YouTube:** `BOT_CHECK`, `LOGIN_REQUIRED` (age or sign-in), `PRIVATE_OR_UNAVAILABLE`, `GEO_RESTRICTED`, `DRM_PROTECTED` and `PLAYER_SCRIPT_REQUIRED`. None of them leads to capture or a universal retry.
- **Spike gaps to close in MK-4:**
  - `MasterFallbackEngine.BROWSER_REQUIRED` lets BOT_CHECK, LOGIN and PLAYER_SCRIPT continue after the user starts playback (`snapshot.authorizedPlayback`). These failures are also missing from `BrowserMasterFallback.NEVER_CAPTURE`. Fix: add host-level terminal rules for `youtube.com`/`youtu.be` (and `reddit.com`).
  - `PayloadMediaReader.youtube()` turns `streamingData` URLs into rows without the n/sig transform. Such a file can pass a one-byte check and then throttle, which is a fake row. Fix: disable it for YouTube hosts; only the module answers YouTube.
- **Why no universal capture for YouTube:**
  - The web player streams SABR/UMP over POST, so there is no whole-file URL to capture.
  - Formats need a PoToken and n/sig from the player JS.
  - yt-dlp also skips SABR-only formats ("formats have been skipped as they are missing a url"; [issue #12482](https://github.com/yt-dlp/yt-dlp/issues/12482)).
- **Tests (MK-4):**
  - (a) flag-off: app == main.
  - (b) Passthrough: with main's YouTube fixtures and a fake `ExtractorHttpClient`, the Master module result equals the adapter result (same rows, order, labels and sizes).
  - (c) A bot-check fixture → `Failure(BOT_CHECK)`, zero extra requests and no capture.
  - (d) Live parity (§4).

## 4. Parity test plan

### 4.1 Harness

Three arms:

- **A**: main (flag off).
- **B**: Master with recipes and modules (flag on, experimental order).
- **C**: universal only, with no recipe or module. C is a measurement, not a gate.

Setup:

- An opt-in instrumented test (`MasterParityLiveTest`, run only with `-e yft.parity 1`, never in default CI) on the emulator or the owner's phone. It writes a JSON report that holds host and content ID only, never signed URLs.

Recorded for each URL:

- **Main video:** content ID, duration, picture size, title hash.
- **Rows:** label, height, codec, has-audio, bytes, kind; whether each row opened (probe).
- **Download:** the smallest row and the best row, downloaded whole for clips under 3 minutes, else the first 8 MB plus a header check. Duration (±2 s) is checked with `Mp4HeaderParser`/MediaExtractor, both tracks must be present, and merged output must play.
- **Time:** time to first row, time to sheet ready and total time. Medians of 3 runs, cold and warm cache.
- **Requests:** count and bytes. Failure reasons for negative cases.

### 4.2 Pass criteria (per site, before its migration step lands on spike)

- **Main video:** same content ID, and duration within ±2 s, for 100 % of positive URLs.
- **Qualities:**
  - B's row count is at least A's.
  - Every B row opened and has a stated height.
  - 0 synthesized rows.
  - A row that A has and B lacks is a failure.
- **Download:** every row A can download, B can download, and the merged files play.
- **Time:** B's median ≤ A's median × 1.2 + 500 ms. In fallback mode this is counted only where A failed.
- **Negative cases:** B's reason equals A's (`BOT_CHECK`, `LOGIN_REQUIRED`, `PRIVATE_OR_UNAVAILABLE` …). No site request and no capture after a terminal failure.
- **Flag-off:** the full CI suite plus `MasterMainMoreSheetTest` (flag-off case) stay green.

### 4.3 URL list (public; check liveness at MK-2 and replace dead links; never commit private links)

| Site | URL | Purpose / expected |
|---|---|---|
| Facebook | https://www.facebook.com/NASA/videos/seeing-earth-as-only-nasa-can/1414737229683186/ | Page video, HD/SD + DASH (live-checked on spike) |
| Facebook | https://www.facebook.com/share/v/1Q3kAyptrS/ (main docs) | Share link → `resolvedIdentity` |
| Facebook | Public reel `facebook.com/reel/{id}` and `fb.watch/{code}` (owner picks) | Safari AVC ladder; redirect |
| Facebook | Group-only or private video (owner picks) | Negative: LOGIN_REQUIRED/PRIVATE |
| TikTok | https://www.tiktok.com/@nasa/video/7670337149526379789 | Main + 0 More (expected, M2) |
| TikTok | Same post as a `vm.tiktok.com` short link; a photo post; a private post (owner picks) | Unresolved → resolved; not a video; PRIVATE |
| Instagram | https://www.instagram.com/reel/Dcwk7e1yHaY/ | Reel: `video_versions` + DASH (live-checked) |
| Instagram | Carousel `/p/{code}/?img_index=2`; private account post (owner picks) | Item selection; LOGIN_REQUIRED; signed-in vs signed-out runs |
| X | https://x.com/NASA/status/2042316964362698815 | MP4 variants (live-checked) |
| X | `/video/2` multi-video post, GIF post, protected post (owner picks) | Video selection; no sound; unavailable |
| Vimeo | https://vimeo.com/76979871 and https://player.vimeo.com/video/76979871 | Public clip (yt-dlp test); embed form |
| Vimeo | Unlisted `/{id}/{hash}` (owner picks); a `vimeo.com/ondemand/...` page | Hash kept; not claimed |
| YouTube | https://www.youtube.com/watch?v=jNQXAC9IVRw; https://www.youtube.com/watch?v=dQw4w9WgXcQ; https://www.youtube.com/shorts/BGQWPY4IigY | Short progressive; 1080 ladder; Shorts |
| YouTube | A 2160p video; an age-restricted video; a private/members-only video (owner picks) | HIGH_QUALITIES; LOGIN_REQUIRED with no bypass; PRIVATE |
| Generic | https://www.w3schools.com/html/html5_video.asp | Plain `<video>` MP4 |
| Generic | https://hlsjs.video-dev.org/demo/ ; https://reference.dashif.org/dash.js/latest/samples/dash-if-reference-player/index.html | HLS/DASH through MSE (capture + manifest) |
| Generic | https://googleads.github.io/googleads-ima-html5/vsi/ | VAST pre-roll → ad separation |
| Generic | A JW Player or Video.js page and a news page with JSON-LD `VideoObject` (owner picks) | `PlayerSetupScanner` / `PageFactsReader` paths |

Keep the list as data (`parity-urls.json`, no secrets), like yt-dlp's per-extractor `_TESTS`.

## 5. Per-site migration plan

| Step | Scope | Master does (universal) | Stays recipe/module | Difficulty | Main risks |
|---|---|---|---|---|---|
| MK-2 | Base sync + harness | Merge `origin/main` → spike (spike only), freeze the URL list, add the parity harness, re-run the flag-off gate | — | Medium | Conflicts in hook files (`BrowserViewModel`, `BrowserMasterFallback`); 61 commits |
| MK-3 | Toolkit T1–T12 | Copied pure helpers with provenance headers and copied fixtures; a drift-check script against main | — | Medium | Copies drifting from main |
| MK-4 | Module slot + YouTube | `MasterSiteModule`, `SiteExtractorModule`, YouTube passthrough, terminal host rules (YouTube/Reddit), turn off `PayloadMediaReader.youtube()` for YouTube | Whole YouTube adapter | Low–Medium | Double lookups → bot check (shared cache) |
| MK-5 | Generic web | Master capture + `PlayerSetupScanner`/`PageFactsReader` + ad rules as the default for sites with no adapter | — | Medium | Ads, previews, multi-video pages |
| MK-6 | Vimeo | Inline/`config_url` JSON through T1/T2/T4 | Host lock, unlisted hash, unclaimed surfaces | Low | Following a non-Vimeo config host; DRM on OnDemand |
| MK-7 | X | Capture fallback for `video.twimg.com` MP4/HLS; T7 sizes from the path | `XSyndication` (token) as a module | Low–Medium | Syndication endpoint changes; separate HLS audio (P50) |
| MK-8 | Facebook | T3/T4 anchored walk with FB keys, T6 DASH, T7 ladder | Safari AVC-ladder agent, URL surfaces, `efg` | Medium–High | Related video in the same payload; AV1/VP9-only ladder; access walls; cookie leaks |
| MK-9 | Instagram | T1/T3 over v1, GraphQL and embed shapes; T6/T7 | `doc_id`, app ID, CSRF, session gating, `mediaIdOf` | Medium–High | `doc_id` rotation; login wall; carousel index |
| MK-10 | TikTok | T1/T2 scripts by `id`, T7/T8 | Agents, `tt_chain_token`, hidden page, status codes, watermark rule, download service (owner flag) | High | Agent sensitivity; web check (**user's "Show check" only, never solved in code**) |
| MK-11 | Primary experiment | `-Pyft.masterPrimary` (default false): Master (recipes + modules) first, main adapters as fallback | — | High | Owner decision; no merge to main or release without approval |

Every step must pass §4.2 for its site and the flag-off gate. Each step can be rolled back by turning the flag off or reverting its spike commit.

Cross-cutting risks:

1. Copies drift from main.
2. Double requests lead to rate limits or bot checks.
3. Cookies leak across origins.
4. A file opens for one byte and then throttles (fake rows).
5. The wrong video is picked (related video or ad).
6. WebView capture costs seconds.
7. Policy (bot check, DRM, third-party service).
8. Licences (GPL/AGPL are ideas only).
9. Live parity is flaky as sites change, so it stays owner-run and outside CI.

## 6. Open-source study: old and new extractor models

### 6.1 Projects read

| Project (commit, date) | Licence | Model | Lesson for Master |
|---|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) `51bab8a` (2026-09-27) | Unlicense | 941 extractor files on one `InfoExtractor` base (`common.py`: `_search_json`, `_search_nextjs_data`, `_search_nuxt_data`, `_search_json_ld`, `_extract_m3u8_formats`, `_extract_mpd_formats`, `_parse_html5_media_entries`, `_extract_jwplayer_data`, `_EMBED_REGEX`/`_extract_embed_urls`). Central `FormatSorter`. `GenericIE._extract_embeds` tries, in order: every IE's embeds → JW Player → Video.js → KVS → JSON-LD → HTML5 → `twitter:player:stream` → `og:video` → regexes. YouTube is a package: `_base.py` `INNERTUBE_CLIENTS` (web, web_safari, web_embedded, web_music, web_creator, android, android_vr, ios, visionos, mweb, tv, tv_downgraded, tv_simply); `_video.py` defaults `('visionos','web')`, JS-less `('visionos',)`, authed `('web_embedded','tv_downgraded','web')`; `pot/` (PO-token providers); `jsc/` (JS-challenge providers on deno/node/bun/quickjs + EJS). TLS impersonation lives in the networking layer. | Shared helpers on one base; volatile parts behind provider interfaces; ordered generic heuristics |
| [youtube-dl](https://github.com/ytdl-org/youtube-dl) `956b8c5` (2025-11-26), plus the 2015.01.01 and 2021.12.17 tags | Unlicense | 810 single-file extractors. `GenericIE` is one long chain of `XxxIE._extract_url(webpage)` calls. Signatures go through `JSInterpreter` (SWF player in 2015). Master branch later backported `_search_json` and `_search_nextjs_data`. | The core pipeline has not changed in 10 years |
| [cobalt](https://github.com/imputnet/cobalt) `a636575` (2026-04-06) | AGPL-3.0 (ideas only) | `api/src/processing/services/*.js`, one module per service (21), routed by `service-patterns.js`/`match.js`. No generic extractor. YouTube uses `youtubei.js` (IOS client by default, PO token from the session). TikTok reads `__UNIVERSAL_DATA_FOR_REHYDRATION__` playAddr/h265. Twitter: guest token + GraphQL `TweetDetail`, syndication as fallback. Instagram: oembed → media_id → `api/v1/media/{id}/info/`, then embed. Facebook regexes the first `browser_native_hd_url`, **with no video-ID anchoring**. | Endpoint knowledge matches ours; without anchoring, a related video can win |
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) `65cabc2` (2026-10-05) | GPL-3.0 (ideas only) | Java. `StreamingService` (5 services) with a `LinkHandler` (URL→ID) and a `StreamExtractor`. YouTube: `ClientsConstants`, `InnertubeClientRequestInfo`, `YoutubeJavaScriptPlayerManager`, `YoutubeSignatureUtils`, `YoutubeThrottlingParameterUtils`, and a **`PoTokenProvider` interface that the app implements (WebView BotGuard)**. | The same library/app split as YFT's `extractor-api` and app |
| [streamlink](https://github.com/streamlink/streamlink) `e526178` (2026-10-08) | BSD-2 | 136 live-stream plugins (`@pluginmatcher`). `webbrowser/` is a Chromium CDP client used by the twitch and kick plugins. | The closest analogue of Master capture, scoped per plugin |
| [gallery-dl](https://github.com/mikf/gallery-dl) `ef30ea9` (2026-10-09) | GPL-2.0 (ideas only) | 258 extractor modules. The `generic` extractor is off by default (needs a `g:` prefix). | Generic stays explicit and last |
| [lux](https://github.com/iawia002/lux) `dd00f6d` (2025-12-29) | MIT | Go; `extractors.Register(domain)`. `universal` is a direct-file download only (size + content type). | "Universal" without page understanding is weak |
| [you-get](https://github.com/soimort/you-get) `049548f` (2025-04-27) | MIT | `universal.py` regex-scans the page for `og:video:url`, m3u8 and media extensions. No ID anchoring. | Regex scans need anchoring and verification |

### 6.2 How each site's technique moved

| Site | youtube-dl (old) | yt-dlp (middle) | yt-dlp now | YFT main now |
|---|---|---|---|---|
| YouTube | 2015: `get_video_info`, `url_encoded_fmt_stream_map`/`adaptive_fmts`, SWF/JS signature. 2021: `ytInitialPlayerResponse` + `youtubei/v1/player` + n-descramble (`JSInterpreter`) | Multi-client InnerTube, then the PO-token provider framework | Clients as above; EJS needs an external JS runtime ([#15012](https://github.com/yt-dlp/yt-dlp/issues/15012)); SABR-forced formats are skipped ([#12482](https://github.com/yt-dlp/yt-dlp/issues/12482)); PO tokens bound to the video or the session ([PO Token Guide](https://github.com/yt-dlp/yt-dlp-wiki/blob/master/PO%20Token%20Guide.md)) | VISIONOS first + chain; BotGuard PoToken; bundled EJS in a WebView |
| TikTok | 2021: `__NEXT_DATA__` → `itemInfo.itemStruct` | 2022: `SIGI_STATE` + app API `aweme/v1/feed` | `__UNIVERSAL_DATA_FOR_REHYDRATION__` `webapp.video-detail` (SIGI fallback); app API only with a user-supplied `app_info`; solves the web check in code (`_solve_challenge_and_set_cookies`) — **not for YFT** | Phone, desktop, tab and hidden page; scripts by `id`; any `__DEFAULT_SCOPE__` key |
| X | 2021: guest token + REST 1.1 + vmap | 2023: GraphQL `TweetResultByRestId` with a guest token | `api` = graphql (default), legacy or syndication (`_generate_syndication_token`) | Syndication only; no guest token, no sign-in |
| Instagram | 2021: `_sharedData` + GraphQL `query_hash` + `rhx_gis` signature | 2022: `api/v1/media/{pk}/info` (`_id_to_pk`) + `X-IG-App-ID` + embed | v1 when logged in; else an accessibility check (`web/get_ruling_for_content`) for CSRF, then POST `/api/graphql` `doc_id` (RelayModern, impersonation), then the post page `/p/{id}`; embeds only through `_EMBED_REGEX` | v1 (session only) → GraphQL `doc_id` → page → embed |
| Facebook | 2021: tahoe + `jsmods` `videoData` (`hd_src`/`sd_src`), early relay | — | Relay prefetched data (`RelayPrefetchedStreamCache`, `__bbox`, `ScheduledServerJS`) → `parse_graphql_video` (`videoDeliveryLegacyFields`: `playable_url`, `playable_url_quality_hd`, `browser_native_hd_url`) + `dash_manifests[].manifest_xml` | `application/json` scripts, anchored walk, inline DASH, Safari AVC ladder |
| Vimeo | 2021: clip page config / `config_url`, JWT viewer | — | `_CLIENT_CONFIGS` (android, web) on `api.vimeo.com` with OAuth/JWT → `config_url` → `_parse_config` | Inline config or `config_url` on player.vimeo.com only |
| Generic | One giant embed chain in `GenericIE` | Embeds moved into each IE (`_EMBED_REGEX`, `extract_from_webpage`) | Ordered heuristics (§6.1); impersonation is opt-in | Headless scanners plus in-tab observation, DOM probe, playing element, VAST tracking; Master capture on spike |

### 6.3 What changed and what stayed

- **Logic that stayed:**
  - The pipeline is still URL → ID → the site's own embedded JSON or player API → formats → sort → select.
  - Manifests are parsed generically.
  - Generic heuristics come last and run in order of reliability.
  - Errors are structured as expected outcomes (login, geo, private), separate from bugs.
- **Logic that changed:**
  - Data keys and endpoints rotate every 1–3 years (§6.2).
  - Defences moved from URL signatures to attestation (PO token/BotGuard), real JS-engine challenges (EJS), SABR, TLS fingerprinting and TikTok's web check.
  - Public token flows were closed (X guest token).
- **Structure:** monolithic files became packages; embed detection was split out to each extractor; format sorting was centralized (`FormatSorter`).
- **Architecture:** provider frameworks for the volatile parts (`pot/`, `jsc/`, NewPipe's `PoTokenProvider`), external JS runtimes, browser integration (streamlink CDP, NewPipe's app-side WebView), cookies-from-browser.
- **What stays the same for us:** the parts a master key can own are the stable ones: JSON location, anchoring, manifests, ladders, verification and request policy. The parts that change go into **recipes (data)** and **modules (code)**, as yt-dlp did with `pot/` and `jsc/`.

### 6.4 Design principles taken into MK-2+

1. Identify stays pure and offline.
2. Volatile values (keys, `doc_id`, app IDs, agents) live in versioned recipe tables, not in code paths.
3. Always anchor to the expected content ID before taking a media URL.
4. Read access markers only after no media was found.
5. Verify every row with a probe, and never show unverified or SABR-only formats.
6. Isolate attestation and JS challenges behind host providers; Master never runs them by itself.
7. Generic heuristics run in order of reliability, and capture comes last.
8. Bot checks are terminal.
