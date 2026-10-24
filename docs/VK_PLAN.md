# VK adapter — plan (VK0–VK4)

Status: **VK1 + VK2 done** (the embed adapter, §4; the owner's VK0 answers, §12) — STOP after
CI; VK3 (the phone) only with the owner's OK. The owner's request: 2026-10-24, in chat. Branch
`work/phase-vk-extractor`, made from PR #2's head `8dc46ff` (`spike/master-extractor-backup`).
VK goes to **beta.5** by default; beta.4 only by the owner's word (§7).

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

- VK ကို main ထဲမှာ adapter သီးသန့် (`extractor-sites/.../sites/vk/`) အဖြစ် ရေးမယ်။ Master၊ R9၊
  remote config မသုံး၊ `extractor-master` ကို import မလုပ်။ File ဟောင်းထဲမှာ `SiteAdapterModule.kt`
  ရဲ့ register line တစ်ကြောင်းပဲ ပြင်မယ်။ Backup finder ပိတ်ထားလည်း VK အလုပ်လုပ်ရမယ်။
- ဒီနေ့ VK video ဖွင့်ရင် generic detection က Audio row ပဲ ပြတယ်။ ပန်းတိုင်က source မှာ တကယ်ရှိတဲ့
  video quality တွေ။ Audio-only ကို အောင်မြင်တယ်လို့ မယူ၊ row ၃ ခု အမြဲရမယ်လို့ ကတိမပေး။
- လမ်း (GET သာ၊ §4.1၊ ပိုင်ရှင့်အဖြေ §12): embed `video_ext.php` ကို VK host နှစ်ခုမှာ မေးမယ် —
  `*.vk.ru` page ဆို `vk.ru` (အဲဒီ page ရဲ့ cookie နဲ့)၊ မဟုတ်ရင် `vk.com` (`*.vk.com` page ရဲ့
  cookie သာ)၊ ပြီးရင် တခြား host (cookie မပါ)။ SPA shell ဆို host တူကို တစ်ခါ ထပ်မေး၊ embed
  request အများဆုံး ၃ ခု။ Page (A) နဲ့ `al_video` မသုံး။ Parser တစ်ခုတည်း: `apiPrefetchCache`
  ထဲက video ID ကိုက်တဲ့ item → key table၊ မရမှ shape scan။
- Floor: Browser မှာ adapter ရှိတဲ့ site ဆို adapter ရဲ့ video ပဲ ပြတယ် (P12) — lookup fail ရင်
  player ရဲ့ file (ဒီနေ့ရဲ့ Audio row) ကို မပြတော့ဘူး။ ဒါကြောင့် video မတွေ့ဘဲ VK ရဲ့ audio-only
  rendition ပဲ တွေ့ရင် partial Success (Audio row)၊ ဘာမှ မတွေ့ရင် message + Try again။
  `PlayerFileFallback` ကို v1 မှာ မထည့် (ပိုင်ရှင်၊ VK3 ရဲ့ evidence နဲ့ ပြန်စဉ်းစား)။ Home မှာတော့
  generic fallback ဆက်ရှိတယ်။
- `al_video` (form POST) က backlog (§8)။ ရုရှား title အတွက် shared client (`OkHttpExtractorClient`)
  ကို ပြင်ပြီး (ပိုင်ရှင် OK၊ commit သီးသန့်): UTF-8 နဲ့ မဖတ်နိုင်မှ response ရဲ့ charset
  (windows-1251) နဲ့ ဖတ်။
- In-tab page data (P40) ကို `BrowserViewModel` က TikTok အတွက်ပဲ ချိတ်ထားလို့ VK-T (tab) က backlog။
- အချိန်: lookup တစ်ခုလုံးအတွက် deadline တစ်ခု၊ Cancel က လက်ရှိ request ကိုပါ ရပ်။ Login/private/region
  ကို ဖြေတဲ့ path အားလုံး တူညီမှ ရပ်။
- S5 run 3 count မဝင်မချင်း VK APK ကို ဖုန်းမှာ မတင်။ PR #2၊ main၊ label၊ R9 ကို မထိ။ Shared docs ကို
  PR #2 merge ပြီးမှ ပြင်။
- အဆင့်: VK0 recon (STOP) → VK1 code → VK2 tests → VK3 CI + preview + ဖုန်း (S5 ပြီးမှ၊ STOP) →
  VK4 PR #2 merge ပြီး main ယူ၊ Preview #13၊ docs၊ PR။ Default က beta.5။
- **VK0 ရလဒ် (§11):** page (A) မှာ player data မပါ (redirect + SPA)။ Embed (B) က hash မလိုဘဲ
  public video ၈ ခုလုံး အလုပ်လုပ် (2160p ထိ)၊ data က `playerParams` မဟုတ်တော့ဘဲ
  `window.cur` → `apiPrefetchCache`။ v1 လမ်း: B vk.com → B2 vk.ru။ `al_video` မလို။ Embed က
  windows-1251 — app ရဲ့ client က UTF-8 နဲ့ ဖတ်လို့ ရုရှား title ပျက် (ပိုင်ရှင် ဆုံးဖြတ်)။
- **VK1 + VK2 (2026-10-24):** adapter (`VkUrls`, `VkEmbedParser`, `VkJudge`, `VkExtractor`)၊
  register line၊ VK test ၅၂ ခု + client test ၂ ခု။ CI အစိမ်းရင် STOP၊ VK3 (ဖုန်း) က ပိုင်ရှင် OK
  ပြီးမှ။

## 1. Goal and scope

**Goal:** a VK link opened in YFT's browser (or pasted on Home) gives the video's real qualities,
not only the Audio row the generic detector finds today. The adapter reads what VK's own web
player gets; it never signs in and never bypasses an access rule.

**In v1:**
- Links on `vk.com`, `m.vk.com`, `vk.ru`, `m.vk.ru`, `vkvideo.ru`, `m.vkvideo.ru` (and `www.`):
  `/video{oid}_{id}`, `/clip{oid}_{id}`, any page with `z=video{oid}_{id}` (feeds, walls,
  albums), `/video_ext.php?oid=…&id=…[&hash=…]`; `list=` kept (an access key VK uses for some
  videos). VK0 confirms the list.
- Public videos and clips, and videos the user's own browser session opens (the cookie of the
  page's own host only; Home's lookups carry no session, so there public only).
- Candidates: progressive MP4 by height; HLS and DASH manifests the app already resolves (DASH
  MP4 before WebM).

**Not in v1** (§8): live streams, whole playlists or albums, subtitles, VK's API (`api.vk.com`,
tokens), short links (`vk.cc`), messenger channel posts (`/im/channels/`), VK's in-tab page data (VK-T), remote recipes or a kill switch,
Master's ladders.

**Why two paths and one judge:** VK changes often — in 2026 alone yt-dlp's VK extractor broke when
the embed page stopped carrying `playerParams` (yt-dlp #16070), on vkvideo.ru (#16530) and with a
wrong "followers only" error (#17608). Two ways in, one strict reader, and an audio floor (§4.3).

## 2. Hard rules

1. **Branch** `work/phase-vk-extractor` only. Never push to `main` or
   `spike/master-extractor-backup`; open no pull request before VK4; add no label to any pull
   request; never run `master-recipes-publish` and never touch the `recipes` branch (S5's run 3
   reads recipes version 7; decision 30 (vi)).
2. **Files:** new files under `extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/vk/`,
   `extractor-sites/src/test/kotlin/com/alal/yft/extractor/sites/vk/` and
   `extractor-sites/src/test/resources/fixtures/vk/`; one line in
   `app/src/main/java/com/alal/yft/detection/SiteAdapterModule.kt` (`VkExtractor(http),` in
   `provideSiteExtractors`); this plan; your own section at the end of `docs/SESSION_STATE.md`
   (`scripts/checkpoint.sh` requires it). Any other file — `extractor-api`,
   `OkHttpExtractorClient`, the coordinator, the browser, Gradle, workflows, `extractor-master` —
   is an owner decision: ask in Burmese and stop.
3. **Shared docs** (`MASTER_KEY_SOCIAL_PLAN.md`, `PROGRESS.md`, `CHANGELOG.md`,
   `docs/PHONE_TEST_NOTES.md`, `docs/TEST_MATRIX.md`, `docs/SUPPORT_MATRIX.md`): not before VK4
   (PR #2 records S5 and its release there). Until then, keep their lines in §9.
4. **Main only:** no `extractor-master` import; no Master, R9, census, W or Backup finder
   dependency.
5. **ADR-006:** no sign-in by the adapter; no DRM, paid, private-content, followers-only or
   age-gate bypass; no API tokens.
6. **Security (AGENTS.md):** never commit, print or report cookies, tokens, signed media addresses
   or response bodies. Fixtures are sanitized (§5.5). Reports: status, host, path, markers only.
7. **The phone:** no VK APK on the owner's phone before S5's run 3 is counted (the owner's run
   after 2026-10-24 17:35 UTC and its PASS); then only with the owner's OK.
8. **Release rules (decision 30):** no debug hook, trial or test switch in the app for VK in v1;
   path failures are proven in JVM tests.
9. **Reports** to the owner: Burmese, short. App text: English.

## 3. What the code already gives (read before VK0)

- `SiteExtractor` (`extractor-api/.../SiteExtractor.kt`): `id`, `displayName`,
  `identify(pageUrl)` (pure, offline), `extract(request)`, `isPlayerMediaRequest(requestUrl)`.
- `SiteExtractionRequest`: `identity`, `requestContext` (the user agent and the page's cookie;
  none on Home), `nowEpochMs`, `pageData` (P40; TikTok's only — `BrowserViewModel`'s
  `TabDataSource` reads TikTok alone). It carries no captured browser requests.
- `SiteExtractionResult.Success(candidates, details)` (at least one candidate, never a guessed
  URL) and `Failure(reason, httpStatusCode, details, message)`. `allowsGenericFallback` is false
  for `DRM_PROTECTED`, `LOGIN_REQUIRED`, `BOT_CHECK`, `PRIVATE_OR_UNAVAILABLE`, `GEO_RESTRICTED`
  and `PLAYER_SCRIPT_REQUIRED`; every other reason lets Home read the page itself (`LinkInspector` →
  `HeadlessPageFetcher`).
- **The browser on an adapter's site (P12):** only the adapter's candidates count, while its
  lookup runs and after it failed (`MediaGroups.pageVideos(…, adapterSite = true)`); the player's
  own files — today's Audio row on VK — are no longer listed. A failure shows its message with
  Try again (`PageVideoLookup`). `PlayerFileFallback` (the player's file as the row after a
  failure) is TikTok's only. So registering VK changes what a VK page shows even when VK fails.
- `BOT_CHECK`: the browser waits for the site's own player to fetch media (the adapter's
  `isPlayerMediaRequest`) and retries once by itself (`SiteAdapterCoordinator`,
  `retriesAfterPlayback`). No new "play the video" flow.
- `ExtractorHttpClient`: `get`, `postJson` (always `application/json`; `OkHttpExtractorClient`)
  and `probe`. No form POST.
- **Template:** the Vimeo adapter (`sites/vimeo/`: `VimeoUrls`, `VimeoConfigParser`,
  `VimeoExtractor`): headers, the cookie only to the matched site, the media context, expiry,
  read limits (page 3 MB, config 1 MB).
- **Downstream:** the app resolves, previews and downloads HLS (separate audio playlists too, P50)
  and non-DRM DASH; AVC/AAC pairs are muxed on the phone.

## 4. Design

As built at VK1: §11.6's changes and the owner's VK0 answers (§12) applied.

### 4.1 Paths (GET only in v1)

| Path | Request | Cookie | When |
|---|---|---|---|
| **B** — the embed, primary host | GET `https://{host}/video_ext.php?oid=…&id=…[&hash=…][&list=…]` | the page's own, if the page is on that site | first |
| **B again** — the same host | the same request | the same | only after VK's SPA shell (`spa_root` / `isRedirectChallenge`, no item) |
| **B2** — the other host | the same request on the other host | never | after any B answer that is not a video or a live video |

- Primary: `vk.ru` on a `vk.ru` / `*.vk.ru` page, with that page's cookie; otherwise `vk.com`,
  with the cookie only from a `vk.com` / `*.vk.com` page (none on Home, `vkvideo.ru` or another
  site). The other host never gets a cookie. `vkvideo.ru` and `m.vk.com` embeds are never asked
  (VK0: the shell or the redirect loop every time).
- At most 3 embed requests. The shell retry is the only retry: it keeps the cookie, so a
  signed-in video is not lost to the other host's "Video not found.". HTTP 429, 5xx, a network
  error or a timeout: no second ask on that host; the other host still gets its one try.
- Path A (the page: VK's login redirect, then an SPA shell) and C (`al_video`, a form POST) are
  out of v1 (§12 (a), (d)); `al_video` stays in §8.

### 4.2 One parser and one judge

1. **The item** (`VkEmbedParser`): every `"apiPrefetchCache": [ … ]` array of the embed (at most
   3, each read by the bounded JSON parser) → the `response.items[]` entry whose own `owner_id`
   and `id` are the link's (the identity check; another video's data is never the video's).
2. **Key table**: that item's `files` — `mp4_{height}` → MP4 (`video/mp4`, that height); `hls`,
   `hls_fmp4`, `hls_ondemand` → HLS (`application/x-mpegURL`); `dash_sep`, `dash_ondemand`,
   `dash_webm`, `dash_webm_av1` → DASH (`application/dash+xml`); `failover_host` and other keys
   are not media. An address counts only if it is HTTPS on a host ending with `.okcdn.ru` (not
   `iv.okcdn.ru`), so a new `vkvd` prefix or failover host still works; other hosts are refused
   and counted. From the item too: `title` (HTML entities decoded; "VK video {oid}_{id}" only
   when it is empty or still holds U+FFFD), the largest HTTPS `image[]`, `duration` (seconds,
   bounded), `live_status`.
3. **Shape scan**, only when the table finds nothing (renamed keys, data moved out of the item):
   a walk of the caches (depth ≤ 48) for objects naming the requested video (`owner_id`/`id` or
   `oid`/`vid`); an object naming another video is skipped whole, and `trailer`, `image`,
   `first_frame`, `timeline_thumbs`, `ads`, `subtitles`, `stats_pixels` are never read. It takes
   `vkvd{N}.okcdn.ru` addresses only, under rendition-like keys (`mp4_720`, `url720`, `cache720`
   → MP4; `hls*` → HLS; `dash*` but `dash_uni` → DASH; a `*live*` key never).
4. **Judge** (`VkJudge`): an MP4 → video. Only when there is no MP4, at most two manifests are
   read (one HLS and one DASH first; ≤ 256 KB and min(3 s, time left) each). HLS: a master with
   a variant that has `RESOLUTION` or a video codec → video; a master whose variants are all
   audio → audio-only; a media playlist or no `#EXTM3U` → not judged. DASH: an `AdaptationSet` or
   `Representation` with a video MIME type, content type, size or codec → video; audio ones only
   → audio-only; else not judged. The first video verdict decides; manifests that cannot be
   judged stay what VK names them (video; `details`: `manifests not judged`).
5. Live (`live_status` `started` / `waiting` / `upcoming`, or only `*live*` files) → not v1
   (§4.3). `postlive` (a finished live with its files) is a video like any other.

### 4.3 When to go on and when to stop

- A video answer (an MP4, or a manifest judged video or not judged) → `Success`; stop.
- A live video → `NO_MEDIA_FOUND` with "VK live streams can't be saved yet."; stop.
- Anything else → the next request of §4.1. At the end, the first that holds:
  1. audio-only manifests from a host → `Success` with them (VK's own, identity-checked
     renditions — not `PlayerFileFallback`); `details`: `audio-only (partial)`; reports count it
     as partial, never as a pass; the browser keeps an Audio row (§3 P12);
  2. VK's `<div id="video_ext_msg">Video not found.</div>` on a host → `NO_MEDIA_FOUND` with
     "VK says this video isn't available. It may be deleted, private or need sign-in." — never
     `PRIVATE_OR_UNAVAILABLE` or `LOGIN_REQUIRED` (it cannot tell deleted, private, sign-in or
     a list apart);
  3. the item without files → `NO_MEDIA_FOUND`;
  4. the shell on every try, or a changed embed (no player data, no item for the video, files
     only on unknown hosts) → `RESPONSE_CHANGED`: a failure that lets Home's generic detector
     read the page, never `BOT_CHECK` (VK0's shells were transient);
  5. the last refusal's reason (`RATE_LIMITED`, `NETWORK`, `HTTP_STATUS`, …): one endpoint's
     401, 403, 404, 410 or 451 is not VK's verdict on the video → `HTTP_STATUS`, never
     sign-in, private or region;
  6. nothing answered in time → `NETWORK` (`details`: `out of time`).
- Free words (`captcha`, `private`, `unavailable`) sit in working embeds' scripts: never
  markers. Only `video_ext_msg`, the shell markers and the item count.

### 4.4 Time and Cancel

- One deadline for the whole lookup (15 s): every embed request and manifest read. An embed
  request gets min(8 s, time left), a manifest min(3 s, time left); with under 250 ms left a
  request is not sent (`details`: `not asked · out of time`).
- The shell retry (§4.1) is VK's only retry; the shared client's own retry of 502/503/504 runs
  inside the same request's time.
- Cancel cancels the lookup's coroutine and the call in flight (`OkHttpExtractorClient` cancels
  it, §11.6); no next request starts.
- `details`: one line per request — path, host, `(page session)` when the cookie went, status,
  ms, then the counts (`video: 8 mp4 up to 2160p, 2 hls, 3 dash (table)`) or the stop reason
  (`shell`, `not found`, `live`, `no files`, `files on unknown hosts: N`, `RATE_LIMITED`, …);
  one closing line only for an outcome no request's line names (`audio-only (partial)`,
  `manifests not judged`, `out of time`). Never a URL, cookie or body.

### 4.5 Candidates and rows

Every real rendition of the requested video is a candidate with its true kind (MP4 `DIRECT`,
`HLS`, `DASH`), MIME type, height (MP4) and the signed address's `expires` as `expiresAtEpochMs`
(query or path; 13 digits as ms, 10 as s); source `MANIFEST`, confidence `HIGH`, `drmHint` false;
the title, thumbnail and duration on each; MP4 from the largest height down, then HLS, then DASH.
The rows stay the app's: row logic, `MergeSupport` (AVC; VP9+Opus WebM from Android 10; AV1 off)
and the planned 2-audio-row rule (a later shared change, not VK's). No change to row logic, the
engine or the browser; no promise of three rows.

### 4.6 Requests, hosts and links

- Embed requests: `PageNavigationHeaders`' defaults, the browser's user agent, and the cookie by
  §4.1's rule only — never across vk.com, vk.ru and vkvideo.ru.
- Manifests and media: the canonical page (`https://vk.com/video{oid}_{id}` or `clip…`, the
  link's `list` and `hash` kept) as `Referer`, the user agent, no cookie (VK0: an MP4 range
  answered 206 without one).
- `isPlayerMediaRequest`: HTTPS `vkvd{N}.okcdn.ru` only. Images (`iv.okcdn.ru`,
  `sun*.userapi.com`), stats (`*.ms.vk.ru`) and trackers never, so an image load never fires the
  `BOT_CHECK` retry. No other families (`mycdn.me`, `vkuser.net`, `userapi.com`) without evidence.
- `identify()` is offline and never throws (the browser calls every adapter's `identify()` on
  each navigation, and two claims fail the registry's `require()`): `/video{oid}_{id}`,
  `/clip{oid}_{id}`, a `z=video…` / `z=clip…` layer on any page and `video_ext.php?oid&id`, on
  `vk.com` (www., m.), `vk.ru` (m.) and `vkvideo.ru` (www., m.). Ids are bounded digit strings
  read with `toLongOrNull`. Look-alike hosts, `http://`, ports, user info, broken `%` escapes,
  addresses over 4 KB, `away.php`, messenger posts (`/im/channels/…`, `/mail/channels/…`) and
  other pages → null: the generic detector keeps them (and `sitePage` stays false).

### 4.7 Files

```
extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/vk/
  VkUrls.kt          identify(), canonical and embed URLs, the cookie's site, media hosts, expiry
  VkEmbedParser.kt   apiPrefetchCache → the item (identity) → key table, shape scan → VkVideo
  VkJudge.kt         an HLS / DASH manifest → video, audio-only or not judged
  VkExtractor.kt     B, B again, B2; deadline, stop rules, reasons, details, candidates
app/src/main/java/com/alal/yft/detection/SiteAdapterModule.kt      + VkExtractor(http), (one line)
app/src/main/java/com/alal/yft/detection/OkHttpExtractorClient.kt  + test: the charset fix
                                                                   (§12 (b), its own commit)
extractor-sites/src/test/kotlin/com/alal/yft/extractor/sites/vk/   VkUrlsTest, VkEmbedParserTest,
  VkJudgeTest, VkExtractorTest, VkAdapterSetTest, VkTestSupport
extractor-sites/src/test/resources/fixtures/vk/                    sanitized embeds, manifests, pages
```

## 5. Steps

### 5.1 VK0 — recon (no app code)

1. **Links:** find public ones yourself (the owner's word, 2026-10-24): a video, a clip and an
   embed link (`video_ext.php`), on vk.com and vkvideo.ru (e.g. yt-dlp's VK test URLs, VK's own
   public pages). The sign-in links are the owner's (2026-10-24): `https://vk.ru/im/channels/-239710230?cmid=2`
   and `…?cmid=3`, posts of a VK messenger channel (VHS Video); signed out they open VK's sign-in
   page (his check). Tried only on his phone (no cookies come to the agent). Their addresses carry
   no video id: v1 does not read them as they are (§5.4, §8).
2. **Live checks** (`scripts/live-check.sh`, public pages only), for each link and host
   (vk.com, vkvideo.ru, m.*): status, final host, path and markers — player data (which keys)?
   embed hash? access text? captcha? Can A work without `al_video`? Does B need `hash`, and does A
   give it? Does `al_video` answer a GET?
3. Encodings (UTF-8 or windows-1251), sizes (page, embed), VK's media hosts, the `oid`/`vid`
   fields, live markers.
4. **Fixtures** (§5.5): a public video's page and embed, a clip, an access wall (whichever is
   reachable: login, private, deleted or region), a manifest with video, an audio-only manifest.
5. **Code checks:** `BrowserMasterFallback` does nothing for an adapter id other than YouTube's and
   the four sites' (VK must not enter Master); the tab reader is TikTok's only (VK-T stays backlog);
   what a VK page shows once VK is registered (§3 P12) and how often the links reach no
   path at all.
6. **STOP — report** (Burmese): a table link × path (works / needs hash / access / changed), the
   v1 path list and, if A and B cannot carry v1, the `al_video` question (shared `postForm`,
   beta.5) and the `PlayerFileFallback` question (VK's player file as the row when every path
   fails; a shared file).

### 5.2 VK1 — code (after the owner's OK on VK0)

The files of §4.7; the registration line last. `identify()` never touches the network. Done
2026-10-24 (§10): commit 1 the shared client's charset fix (§12 (b)); commit 2 the adapter, its
tests and the registration line.

### 5.3 VK2 — tests (JVM; no live network in CI)

As built (52 VK tests, all on VK0's fixtures or synthetic pages in VK's shape):

- `VkUrlsTest` (9): every link form on every host → its identity (canonical
  `https://vk.com/video…`), `z=` layers, embeds with `list` / `hash`; look-alike hosts, `http://`,
  ports, user info, 25-digit ids, broken `%` escapes, a 10 KB URL, `away.php` and messenger posts
  → null; the key table's and the browser's host rules; the expiry from query or path; the
  cookie's site.
- `VkEmbedParserTest` (12): every rendition of each fixture (video, clip, finished live) with its
  kind, MIME, height and expiry; previews, images and first frames never; renamed keys and moved
  data found by the shape scan (still never the preview); another video's data refused; live;
  unknown hosts refused and counted; "Video not found.", the shell and changed pages; titles
  (entities; empty or U+FFFD → the fallback), the thumbnail and the duration.
- `VkJudgeTest` (4): VK's masters and MPD → video; all-audio ones → audio-only (synthetic: VK0
  found none); a video codec without a resolution → video; what it cannot tell → not judged.
- `VkExtractorTest` (21; a scripted `ExtractorHttpClient`, virtual time): the cookie rule per page
  (`vk.com`, `vk.ru`, `vkvideo.ru`, Home); 429 and 5xx → the other host at once; the shell → once
  more, then the other host (3 asks at most); shells everywhere → `RESPONSE_CHANGED`; "Video not
  found." → `NO_MEDIA_FOUND` with the message, never private or sign-in; HTTP statuses are not
  verdicts; live → its message; a finished live; manifests (video, audio-only partial, not
  judged); unknown hosts; another video's embed; no files; the 15 s deadline (8 s + 7 s); Cancel →
  no further request; `details` without a URL, cookie or body; a non-VK identity refused.
- `VkAdapterSetTest` (3): VK's pages go to VK only; the six shipped adapters keep theirs; VK pages
  without a video id stay with the generic detector.
- `VkFixtureHygieneTest` (3): every fixture listed; none holds an IP-like value, a signed value,
  a session name or a key-like run (§5.5); the rules catch a planted leak.
- `OkHttpExtractorClientTest` (+2, commit 1): windows-1251 reads right; UTF-8, no charset, an
  unknown charset and UTF-8 bytes labelled ISO-8859-1 read exactly as before.

### 5.4 VK3 — CI, preview, the phone

- Checkpoints: `bash scripts/checkpoint.sh "…"` on `work/phase-vk-extractor` (your SESSION_STATE
  section each time). CI: `checkpoint-validation` green; `preview-apk` builds a release-flag
  preview (`com.alal.yft.preview`). Only the preview counts for VK, never a debug build (§12 (e)).
- **The phone** (only after S5's run 3 is counted, with the owner's OK): the public links signed
  out, the owner's sign-in links (§5.1) signed in to VK in YFT's browser by him (open
  the post, tap its video: an address with a video id — `video…`, `z=video…` — is read by v1;
  none → §8), and one lookup each on
  TikTok, Facebook, Instagram and X (no change expected). Per VK link: the top row, one lower row
  and an Audio row download, play and have sound (§12). He sends Copy details; the agent judges.
- **STOP — report:** per link the rows (video heights, Audio), the path that answered and the
  time; the four sites unchanged.

### 5.5 Fixtures

Real responses with every signed media address, signature, `expires`/IP-like value, cookie, user
id and hash replaced by fixed placeholders on the same hosts (e.g. `…?sig=FIXTURE`); titles may
stay. A test fails if a fixture holds a real-looking signature or cookie.

### 5.6 VK4 — after PR #2's merge

1. Merge `main` into `work/phase-vk-extractor` (a merge commit, no rebase; PR #2 is merged with a
   merge commit, decision 30 (iv)). Conflicts are not expected (VK's files are new); resolve only
   in VK's own files.
2. CI green; **Preview #13** (the release's flags, with VK): the VK links plus one lookup
   per site.
3. The shared docs from §9: `docs/SUPPORT_MATRIX.md`, `docs/TEST_MATRIX.md`, `PROGRESS.md`,
   `CHANGELOG.md`.
4. A pull request to `main`; the owner merges.

## 6. Validation

- VK work: `./gradlew --no-daemon :extractor-sites:test`.
- Before a push that also changes `SiteAdapterModule.kt`, CI's set: `./gradlew --no-daemon
  lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test
  :extractor-sites:test :app:assembleDebug :app:assembleRelease`.

## 7. Release placement

- Default: **beta.5**. VK never delays beta.4: decision 30's order (S5 → the gate → the release
  commit → #12 → PR #2's merge → R9's first Run workflow → P8) does not wait for VK.
- beta.4 only if VK3 and #13 pass before P8 and the owner says so; S5's count then stands by his
  word (VK adds one site and changes no other site's adapter, the engine or main's other lookups).

## 8. Backlog (after v1)

- **VK-T — VK's in-tab page data** (a VK reader beside TikTok's in `BrowserViewModel` and
  `BrowserScreen`): the strongest way for signed-in videos (no hash, no endpoint); a shared
  change → beta.5 or later.
- `al_video` by form POST (`postForm`): backlog (the owner, §12 (d)).
- VK messenger channel posts (`vk.ru/im/channels/{id}?cmid=…`, `m.vk.ru/mail/channels/…`): no
  video id in the address; the messenger's data needs the session (the owner's links, §5.1).
- VK in `PlayerFileFallback` (the player's file as the row when every path fails): not in v1
  (§12 (c)); revisit after VK3 with evidence.
- Re-extract once on a media 403 (VK's `srcIp`-bound URLs after a Wi-Fi ↔ mobile switch).
- Live streams, playlists, subtitles, `vk.cc` short links.
- Remote recipes or a kill switch for VK (GitHub raw requests: the privacy line and the Backup
  finder switch are affected — an owner decision).

## 9. Lines for the shared docs (written at VK4)

- `docs/SUPPORT_MATRIX.md`: a row "VK videos and clips (vk.com, vk.ru, vkvideo.ru)" — its status,
  phase and notes from VK3 and #13.
- `docs/TEST_MATRIX.md`: one row per VK step with commits and results.
- `PROGRESS.md`, `CHANGELOG.md`: one entry each.

## 10. Status

- 2026-10-24: plan recorded (docs only), branch made from `8dc46ff`. Next: VK0.
- 2026-10-24: the owner — the agent finds public VK links itself; his sign-in link at VK3.
- 2026-10-24: the owner's sign-in links (VK messenger channel posts) recorded in §5.1.
- 2026-10-24: VK0 done — results and fixtures (§11; `fixtures/vk/`); STOP: the owner's answers
  (§11.7) before VK1.
- 2026-10-24: the owner's VK0 answers (§12); §4 rewritten as built.
- 2026-10-24: VK1 + VK2 done in two commits: `a176667a` the shared client's charset fix
  (§12 (b)), then the adapter (§4), its 52 tests, the fixtures' keys and the registration line.
  STOP after CI: VK3 only with the owner's OK.

## 11. VK0 results (2026-10-24)

Anonymous GETs to public pages only (`scripts/live-check.sh` and local trace scripts that print
status, host, path, query names and markers; no body, cookie or signed URL was printed or
committed). User agents: desktop Chrome, Android Chrome, and Home's (`… YFT/…`); all answered alike
unless noted.

### 11.1 Links × paths

| Link (public) | A — page (`vk.com`/`vk.ru`/`vkvideo.ru`/`m.*`) | B — `vk.com/video_ext.php?oid&id` (no hash) | B2 — `vk.ru/video_ext.php` | `vkvideo.ru` / `m.vk.com` embed |
|---|---|---|---|---|
| `video-77521_162222515` (old) | no player data | works, 240p (with or without yt-dlp's public hash) | works (with or without the hash) | redirect loop / SPA shell |
| `video205387401_165548505` | no player data | works | — | — |
| `video-50883936_456244102` | no player data | works, 144p–2160p, HLS, DASH (avc1, vp9, av1) | — | — |
| `video-18403220_456239696` | no player data | works, 1080p (desktop and Android) | works | SPA shell |
| `video-127553155_456242961`, `video-220754053_456242564` | no player data | works | — | — |
| `clip30014565_456240946` | see 11.2 | works (`type=short_video`) | — | — |
| `video-140332_456239111` (finished live) | no player data | works (`live_status=postlive`, files present) | — | — |
| deleted / removed / list-only / an old clip | — | `Video not found.` | — | — |

- **A carries no player data.** `vk.com`/`vk.ru` `/video…` → 301 `vkvideo.ru` → 302 `login.vk.ru`
  (VK's anonymous silent-auth check: `act, app_id, redirect_uri, state, uuid`) → back with
  `errorCode` → the page. Without a cookie jar this loops (the app's `OkHttpExtractorClient` sends
  no response cookies and stops after 5 redirects → "too many redirects"). With a jar the page
  (`vkvideo.ru` ~330 KB windows-1251, `m.vkvideo.ru`) is an SPA shell: its prefetch cache holds
  only ads and config; the video comes later from `api.vkvideo.ru` with an anonymous token (VK's
  API — out of scope, §1). `m.vk.com` (Android, no redirect, ~108 KB): a shell too. No embed hash on
  A.
- **`al_video.php` by GET** → a tiny payload with VK's "Unknown error" (code 8): it does not answer
  a GET. POST not tried (not needed, 11.6).
- **B needs no hash** for any public video tried, on all three user agents; the link's own `hash`
  is accepted when present. `list=` does not open a "Video not found." video.
- **B on `vk.com` sometimes answers an SPA shell** (~22 KB, UTF-8, `spa_root`,
  `isRedirectChallenge=true`) — about 2 in 20 asks; the next ask or B2 on `vk.ru` answered.
  `vkvideo.ru/video_ext.php` and `m.vk.com/video_ext.php` always give the shell (or the redirect
  loop) → B2 is `vk.ru`, never `vkvideo.ru`.
- **Access text:** deleted, removed, list-only and an old clip all give
  `<div id="video_ext_msg">Video not found.</div>` — one text for all, so not a verdict (§4.3: a
  failure that still lets Home read the page). No sign-in text was seen anonymously. Working
  embeds also contain the words `captcha`, `private` and `unavailable` in their scripts → free
  words are never markers; only `video_ext_msg` and the absence of the item count.

### 11.2 The clip page on `m.vk.ru` (not used in v1)

An Android `vk.com/clip…` link → 302 `m.vk.ru/clip…` (~274 KB, UTF-8) carries a clips *feed* in its
prefetch data: 11 `files` objects, only one of them the requested clip (the others are the next
clips). It proves the identity check (§4.2) is required; v1 does not use this page (B works for
clips).

### 11.3 Where the data is (B)

Not `var playerParams` any more (cf. yt-dlp #16070) but
`window.cur = Object.assign(…, {"apiPrefetchCache": [ … ]})`:

- an entry `{request: {videos: "{oid}_{id}", is_embed, …}, response: {items: [item]}}` — the video;
- an entry `{request: {module…}, response: {config…}}` — player config (no media).

| Item key | Meaning | v1 use |
|---|---|---|
| `owner_id`, `id` | the video's `oid`, `id` | identity: must equal the link's |
| `title`, `duration`, `image[]{url,width,height}` | title, seconds, thumbnails | title, duration, the largest thumbnail |
| `type` | `video` or `short_video` (clip) | — |
| `live_status` | e.g. `postlive` (a finished live, has files) | a live one (no VOD files, `hls_live_playback`) → not v1 |
| `files` | the media (below) | candidates |
| `trailer{mp4_240}`, `first_frame[]`, `timeline_thumbs` | preview, frames, sprites | refused |
| `player`, `share_url`, `direct_url`, `width`, `height`, `download`, `can_download` | embed URL with hash, links, size | not needed |

`files`: `mp4_144`, `mp4_240`, `mp4_360`, `mp4_480`, `mp4_720`, `mp4_1080`, `mp4_1440`, `mp4_2160`
(progressive), `hls`, `hls_fmp4`, `hls_ondemand` (HLS masters), `dash_sep`, `dash_ondemand`,
`dash_webm`, `dash_webm_av1` (DASH), `failover_host` (a host name, not a URL).

### 11.4 Hosts, signatures, manifests

- Media: `vkvd{N}.okcdn.ru` only (`isPlayerMediaRequest`); images `sun{N}-{N}.userapi.com`,
  `iv.okcdn.ru`. Signed query names: `expires` (epoch **ms**, ~5 days ahead → `expiresAtEpochMs`),
  `srcIp` (bound to the fetcher's IP: a Home lookup's URLs are fetched from the same phone, so this
  holds), `srcAg`, `sig`, `id`, …
- An MP4 `Range: bytes=0-0` → 206 with the desktop and Android agents, no cookie.
- `hls` and `hls_fmp4` masters list `EXT-X-STREAM-INF` with `RESOLUTION` 144p–2160p; `hls_fmp4` has
  5 `TYPE=AUDIO` renditions (each an audio-only media playlist). DASH: `dash_sep` avc1/mp4a,
  `dash_webm` vp9/opus, `dash_webm_av1` av1/opus; no `ContentProtection`. No real audio-only master
  was found (the VK2 test builds one, marked synthetic).

### 11.5 Encoding and sizes

- Embeds (67–92 KB) and the desktop page are **windows-1251** (`Content-Type: …charset=windows-1251`;
  `Accept-Charset` and `remixlang` do not change it). Cyrillic in the JSON is raw bytes, not `\u`
  escapes. `OkHttpExtractorClient.readBounded` reads every body as UTF-8 (`readUtf8()`), so the
  adapter receives U+FFFD for Russian titles; JSON structure, numbers and URLs survive (ASCII).
  The adapter cannot repair it (it never sees the bytes). → owner question (b).
- Read limits: embed ≤ 1 MB, manifests ≤ 256 KB (largest seen: 18 KB) are ample.

### 11.6 Code checks and the proposed v1

- `OkHttpExtractorClient` cancels the call in flight when the coroutine is cancelled
  (`cancellable`) → §4.4 holds without a change; the per-request time is a `withTimeout` in VK.
- `BrowserMasterFallback` exists only when `BuildConfig.MASTER_CAPTURE_ENABLED` (debug builds); it
  has **no** adapter-id filter (`allowsMasterFallback` = not disabled), so in those builds a VK
  failure that allows fallback reaches Master's capture like any site's. Release and preview: off.
  No change proposed (a shared file); noted for the owner (e).
- The tab reader (`BrowserViewModel`'s `TabDataSource`) and `PlayerFileFallback` are TikTok's only.
  Registering VK hides today's Audio row on VK pages (P12); a failed lookup shows its message with
  Try again.
- Signed-in videos: the page cookie may go only to its own site. A tab on `vkvideo.ru` (where VK
  sends most video links) has no working embed host → signed-in-only videos are expected to fail
  in v1 unless the tab is on `vk.com`/`vk.ru` (a `*.vk.com` page's cookie → `vk.com` embed; a
  `*.vk.ru` page's → `vk.ru`). Checked on the phone at VK3 with the owner's link.
- **Proposed v1 paths:** **B** `https://vk.com/video_ext.php?oid&id[&hash][&list]` → **B2** the same
  on `vk.ru` → one more **B** only if both answered the SPA shell. A tab on `*.vk.ru` with a
  cookie starts with the `vk.ru` embed (its own cookie), then `vk.com` without one. A and C
  (`al_video`) dropped.
- The owner's messenger links (`vk.ru/im/channels/…?cmid=…`, §5.1): `identify()` does not claim
  them (no video id), so the messenger page keeps today's generic rows; once his tap opens the
  video as `video{oid}_{id}` or `z=video…` on `vk.ru`, v1 reads it with that page's cookie.
  The parser reads `apiPrefetchCache` by the key table above, then the shape scan (§4.2), always
  with the identity check. `identify()`: the hosts and link forms of §1 (VK0 found no other).
- Changes to §4 that follow (applied at VK1 after the owner's OK): §4.1's table → B/B2 only;
  §4.2's keys → 11.3; §4.3's access markers → `video_ext_msg` only; `VkPaths.kt` → embed paths.
  Applied (§4).

### 11.7 Questions for the owner (STOP)

- (a) Drop path A (it needs a cookie jar through VK's login redirect and gives an SPA shell)? The
  agent proposes yes.
- (b) Windows-1251 titles: a small shared change in `OkHttpExtractorClient` (decode by the
  response's `Content-Type` charset; affects every site only when a server names a non-UTF-8
  charset), or v1 keeps the adapter-only fallback title ("VK video {oid}_{id}") when the title holds
  U+FFFD? The agent proposes the fallback in v1 and the shared change later (beta.5+).
- (c) `PlayerFileFallback` for VK (a shared file): with it, a failed VK lookup (e.g. a signed-in
  video on a `vkvideo.ru` tab) keeps today's Audio row; without it, the message with Try again.
- (d) `al_video` by POST is not needed for v1 (stays backlog §8).
- (e) Master's capture in debug builds takes failed VK lookups (no adapter filter): fine as it is?

The owner's answers (2026-10-24): §12.

## 12. The owner's VK0 answers (2026-10-24)

His review of VK0, in chat (it replaces any earlier reply): start VK1 and VK2. CI for `f577e074`
(Preview APK, checkpoint validation, emulator smoke): green.

- **(a) Path A dropped; `al_video` out of v1** (its POST stays in §8). v1 = the embed on two
  hosts. Primary: `vk.ru` on a `*.vk.ru` page (with that page's cookie); otherwise `vk.com` (the
  cookie only from a `*.vk.com` page; none on Home or `vkvideo.ru`). The other host never gets a
  cookie. The SPA shell (`spa_root` / `isRedirectChallenge`, no item) is not an answer: the same
  host once more (it keeps the cookie, so a signed-in video is not lost to the other host's
  "Video not found."), then the other host; at most 3 embed requests, the only retry (§4.4
  amended). HTTP 429 or 5xx: no second ask on that host; the other host still gets its one try.
  Shells on every try → a generic-allowed failure (they were transient in VK0), not `BOT_CHECK`.
  All inside the 15 s deadline; each request min(8 s, time left); Cancel → no next request.
  → §4.1, §4.3, §4.4.
- **(b) Titles: the shared client is fixed.** His OK covers `OkHttpExtractorClient.kt` and
  `OkHttpExtractorClientTest.kt` only, in their own commit; any other shared file is still §2.2.
  `readBounded` keeps `readUtf8()`; only when that yields U+FFFD and the response names another
  valid charset are the same bytes decoded with that charset, so no body that reads cleanly today
  can change. Size limit, timeout and Cancel unchanged. Tests: windows-1251 reads right; UTF-8,
  no charset, an unknown charset and UTF-8 bytes labelled ISO-8859-1 read exactly as before.
  "VK video {oid}_{id}" only when the title is empty or still holds U+FFFD. → `a176667a` (one
  more guard: the bytes must also be invalid UTF-8, so a UTF-8 body that really holds U+FFFD
  keeps its reading).
- **(c) `PlayerFileFallback`: not in v1** (it turns the player's newest file into one MP4 —
  TikTok's case; VK's player fetches HLS/DASH pieces). VK's own identity-checked audio-only
  renditions stay a partial `Success` (§4.3); that is not this fallback. Revisit after VK3 with
  evidence (§8).
- **(d) `al_video` POST: backlog** (§8).
- **(e) Master in debug builds: no change.** A debug build's result never counts for VK; VK3
  counts the preview (release flags) only.

Also:

1. "Video not found." (`video_ext_msg`) cannot tell deleted, private, sign-in or a list apart:
   never `PRIVATE_OR_UNAVAILABLE` or `LOGIN_REQUIRED` from it; `NO_MEDIA_FOUND` with "VK says
   this video isn't available. It may be deleted, private or need sign-in." (§4.3).
2. Hosts (VK0: media `vkvd{N}.okcdn.ru`, `failover_host` too; images `iv.okcdn.ru` and
   `sun*.userapi.com`; stats `*.ms.vk.ru`). The key table: HTTPS addresses under the matched
   item's `files` on a host ending with `.okcdn.ru` (a new `vkvd` prefix or failover host still
   works). The shape scan and `isPlayerMediaRequest`: `vkvd{N}.okcdn.ru` only. Image, stats and
   tracker hosts and the `image` / `first_frame` / `timeline_thumbs` / `trailer` data are never
   media (an image load must not fire the `BOT_CHECK` retry). No other families (`mycdn.me`,
   `vkuser.net`, `userapi.com`) without evidence; this replaces his earlier list. Tests: a new
   `*.okcdn.ru` host under `files` accepted; a thumbnail on `iv.okcdn.ru` refused by the shape
   scan; `notokcdn.ru`, `okcdn.ru.example.org` and `http://` refused; `isPlayerMediaRequest`
   false for images and stats (§4.2, §4.6).
3. `identify()` never throws and claims only VK pages: the browser calls every adapter's
   `identify()` (`SiteExtractorRegistry.select`) on each navigation without a try/catch, and two
   claims fail its `require()`. Ids as bounded digit strings; `toLongOrNull`, never `toLong()`.
   Junk tests: 25-digit ids, broken `%` escapes, a 10 KB URL, `vk.com/away.php?to=` a YouTube
   link → null. Messenger links (`/im/channels/…?cmid=…`) → null, so `sitePage` stays false and
   the page keeps today's rows (§4.6).
4. Candidates: every real rendition with its true kind, MIME, height and expiry. The rows stay
   the app's: row logic, `MergeSupport` (AVC; VP9+Opus WebM from Android 10; AV1 off) and the
   planned 2-audio-row rule (a later shared change, not VK's). No change to row logic, the
   engine or the browser (§4.5).
5. `details`: one line per request — path, host, status, ms, video/audio counts or the stop
   reason; never a URL, cookie or body (§4.4).
6. §8, one line: re-extract once on a media 403 (`srcIp`-bound URLs after a Wi-Fi ↔ mobile
   switch).
7. At VK1, §11.6's changes go to §4 and these answers into the plan: done (§4, this section).

Stop: VK1 + VK2 → `bash scripts/checkpoint.sh "…"` → CI green → STOP with a short Burmese report
(commits, tests, CI). No VK APK on the owner's phone before S5's run 3 is counted; VK3 only with
his OK. There, per link: the top row, one lower row and an Audio row download, play and have
sound.

## Appendix — the agent's prompt

```text
You are the VK agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: work/phase-vk-extractor (it exists; made from PR #2's head 8dc46ff). Work only there.

Read first, in this order:
1. AGENTS.md — the repository's rules (checkpoints with scripts/checkpoint.sh).
2. docs/VK_PLAN.md — your plan. Follow it step by step (VK0 → VK4). Its §2 hard rules come first.
3. docs/ARCHITECTURE.md, then the Vimeo adapter
   (extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/vimeo/) as your template.

Start with VK0 (recon). Find public VK links yourself; ask the owner only for a sign-in-only
link, at VK3. Stop at every STOP in the plan and report to the owner in Burmese, short.

Never: push to main or spike/master-extractor-backup; open a pull request before VK4; add a
label; run master-recipes-publish or touch the recipes branch; change a file outside the plan's
§2 rule 2 without the owner's OK; import extractor-master; sign in, use API tokens or bypass
access or DRM; print or commit cookies, tokens, signed URLs or response bodies; install or send a
VK APK for the owner's phone before he says S5's run 3 is counted.
```
