# VK adapter — plan (VK0–VK4)

Status: **VK0 done** (recon and fixtures, §11) — waiting for the owner's answers (§11.7); no app
code yet. The owner's request: 2026-10-24, in chat. Branch
`work/phase-vk-extractor`, made from PR #2's head `8dc46ff` (`spike/master-extractor-backup`).
VK goes to **beta.5** by default; beta.4 only by the owner's word (§7).

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

- VK ကို main ထဲမှာ adapter သီးသန့် (`extractor-sites/.../sites/vk/`) အဖြစ် ရေးမယ်။ Master၊ R9၊
  remote config မသုံး၊ `extractor-master` ကို import မလုပ်။ File ဟောင်းထဲမှာ `SiteAdapterModule.kt`
  ရဲ့ register line တစ်ကြောင်းပဲ ပြင်မယ်။ Backup finder ပိတ်ထားလည်း VK အလုပ်လုပ်ရမယ်။
- ဒီနေ့ VK video ဖွင့်ရင် generic detection က Audio row ပဲ ပြတယ်။ ပန်းတိုင်က source မှာ တကယ်ရှိတဲ့
  video quality တွေ။ Audio-only ကို အောင်မြင်တယ်လို့ မယူ၊ row ၃ ခု အမြဲရမယ်လို့ ကတိမပေး။
- လမ်း (GET သာ): A = video page ကိုယ်တိုင် (player data ဒါမှမဟုတ် embed hash)၊ B = embed
  `video_ext.php` ရဲ့ `playerParams`၊ B2 = B ကို တခြား VK host မှာ (cookie မပါ)။ Parser/judge
  တစ်ခုတည်း: key table အရင်၊ မရမှ shape scan၊ video ID ကိုက်မှ လက်ခံ။
- Floor: Browser မှာ adapter ရှိတဲ့ site ဆို adapter ရဲ့ video ပဲ ပြတယ် (P12) — lookup fail ရင်
  player ရဲ့ file (ဒီနေ့ရဲ့ Audio row) ကို မပြတော့ဘူး။ ဒါကြောင့် video မတွေ့ဘဲ audio ပဲ တွေ့ရင် VK
  ကိုယ်တိုင် Audio row ပြန်ပေးမယ်၊ ဘာမှ မတွေ့ရင် message + Try again။ Player file ကို row အဖြစ်
  ပြတဲ့ `PlayerFileFallback` က TikTok အတွက်ပဲ — VK ထည့်ဖို့ shared change → VK0 မှာ ပိုင်ရှင်
  ဆုံးဖြတ်။ Home မှာတော့ generic fallback ဆက်ရှိတယ်။
- `al_video` (yt-dlp ရဲ့ အဓိကလမ်း) က form POST လိုတယ်။ App ရဲ့ HTTP client မှာ `postJson` ပဲ
  ရှိလို့ shared file ပြင်ရမယ် → recon ပြီးမှ ပိုင်ရှင် ဆုံးဖြတ် (VK0 STOP)။
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

### 4.1 Paths (GET only in v1)

| Path | Request | What it reads | When it helps |
|---|---|---|---|
| **A** — the page | GET the video or clip page on the host the user opened (that host's cookie only) | the player data the page carries (`playerParams` / `params[0]` / its JSON state) or, failing that, the embed `hash` for B | the usual way (VK0 confirms) |
| **B** — the embed | GET `https://{host}/video_ext.php?oid=…&id=…&hash=…` (the link's hash or A's) | `var playerParams = {…};` → `params[0]` (yt-dlp's embed path) | A changed or carries no player data |
| **B2** — the other host | B on the other VK host (vk.com ↔ vkvideo.ru), with no cookie | as B | the user's host refuses; cookies are per domain |
| **C** — `al_video` (not v1) | form POST `al_video.php` (`act=show`, `al=1`, `video`, `list`) | the payload's `player.params[0]`, `mvData` | only if VK0 shows A and B cannot carry v1: needs a `postForm` in `extractor-api`, `OkHttpExtractorClient` and the clients that wrap it → the owner's decision, likely beta.5 |

Order A → B → B2; the first video answer wins (§4.3). If VK0 finds that `al_video` answers a GET,
it joins v1 as a GET path after A.

### 4.2 One parser and one judge (every path)

1. **Key table first** (`VkPlayerParams`): `url{height}` / `cache{height}` → MP4 at that height;
   `hls*` (not `hls_live_playback`) → HLS; `dash_sep`, `dash_webm`, other `dash*` (not `dash_uni`,
   `dash_live_playback`) → DASH; `md_title`, `md_author`, `jpg`, `duration`, `live`, and the
   video's `oid`/`vid`. VK0's fixtures fix the exact keys.
2. **Shape scan second**, only when the table finds nothing: walk the JSON for objects holding
   media URLs on VK's media hosts (VK0's list, in `VkUrls`) under height-like keys, and accept one
   only if it names the requested video (`oid`/`vid` or `{oid}_{id}`). A URL alone is never
   enough: another video's, a preview's, an ad's or a thumbnail's data is refused.
3. **Judge** (`VkJudge`): an MP4 with a height → video. A manifest → read it (at most 256 KB and
   3 s, inside the deadline): video only if it lists a video variant (HLS `EXT-X-STREAM-INF` with
   `RESOLUTION`; DASH a video `AdaptationSet` or `Representation`); audio-only → partial. A host
   outside the list → refused. `live == 2` → not v1.

### 4.3 When to go on and when to stop

- A video answer (at least one video candidate) → `Success`; stop.
- Audio-only or nothing → the next path.
- An access answer (VK's login wall, private, deleted, copyright, region, followers-only; VK0's
  markers) on one path → noted; the remaining paths still run (one endpoint's text is not a
  verdict, see #17608).
- At the end:
  - every path that answered gave the same access answer → that reason (`LOGIN_REQUIRED`,
    `PRIVATE_OR_UNAVAILABLE`, `GEO_RESTRICTED`); for sign-in, the app's own text unless VK0 shows
    it misleads (then a `message`, like X's and Instagram's own lines);
  - VK's captcha or security check → `BOT_CHECK` (the existing retry after playback);
  - no video on any path, but audio-only renditions on one → `Success` with them (the floor:
    the browser keeps today's Audio row, §3 P12); `details` say `audio-only`; reports count it
    as partial, never as a pass;
  - anything else (live, changed markup, network) → `RESPONSE_CHANGED`, `NO_MEDIA_FOUND` or the
    network reason (Home then reads the page; the browser shows the message with Try again).
- An audio-only `Success` only at the end, never while a path is left.

### 4.4 Time and Cancel

- One deadline for the whole lookup (15 s), shared by every path, host change and manifest read;
  each request gets the time left, at most 8 s; no retries beyond the paths.
- Cancel cancels the lookup's coroutine and the request in flight. Check that
  `OkHttpExtractorClient` cancels its call; if it does not, report it (not a v1 change).
- `details`: one line per path (e.g. `vk: A page 200, params by table, 3 mp4`), never a URL,
  cookie or body.

### 4.5 Candidates and rows

All real renditions as candidates (MP4 per height, manifests); the app's existing row logic picks
what to show (no promise of three rows). Title: `md_title`, else the author "on VK"; the
thumbnail; the duration; a signed URL's `expires` as `expiresAtEpochMs` when present.

### 4.6 Requests and the session

- Headers as Vimeo's: the browser's user agent, `Accept-Language`, `Referer` (the page), the
  cookie only to the host it came from — never across vk.com, vk.ru and vkvideo.ru.
- The media context: the page as referer, the user agent and the cookie (the resolver strips it
  for other origins, as for Vimeo).
- `isPlayerMediaRequest`: VK's media hosts (VK0's list), so the `BOT_CHECK` retry works.

### 4.7 Files

```
extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/vk/
  VkUrls.kt          identify(): hosts, paths, z=, embed, list/hash; media hosts; canonical URL
  VkPlayerParams.kt  key table + shape scan + identity check → VkClip (renditions, title, live)
  VkJudge.kt         video or audio-only (manifest read), hosts, live
  VkPaths.kt         A, B, B2 (GET); access and bot markers per path
  VkExtractor.kt     order, deadline, stop rules, reasons, details, isPlayerMediaRequest
app/src/main/java/com/alal/yft/detection/SiteAdapterModule.kt   + VkExtractor(http),  (the only old file)
extractor-sites/src/test/kotlin/com/alal/yft/extractor/sites/vk/   VkUrlsTest, VkPlayerParamsTest,
                                                                   VkJudgeTest, VkExtractorTest
extractor-sites/src/test/resources/fixtures/vk/                    sanitized pages, embeds, manifests
```

Names may change if VK0 shows a better split; "new files plus one line" may not.

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

The files of §4.7; the registration line last. `identify()` never touches the network.

### 5.3 VK2 — tests (JVM; no live network in CI)

- `VkUrlsTest`: every link form → its identity or null; `list`/`hash` kept; look-alike hosts
  refused (`vk.com.example.org`, `evilvk.com`).
- `VkPlayerParamsTest`: the key table on each fixture; renamed keys → the shape scan finds the
  same video; another video's params, a preview, an ad and a thumbnail refused; live marked.
- `VkJudgeTest`: video and audio-only manifests; unknown hosts refused.
- `VkExtractorTest` (a fake `ExtractorHttpClient`; each path skipped or mutated): A fails → B;
  A and B fail → B2; one path's access text and another's video → video; all paths access →
  `LOGIN_REQUIRED`; captcha → `BOT_CHECK`; audio-only everywhere → `Success` with the audio renditions
  (`details`: `audio-only`); nothing anywhere → a generic-allowed failure;
  a slow client → the deadline holds (virtual time); cancel → no further request; the cookie
  never reaches another host; `details` hold no URL or cookie.

### 5.4 VK3 — CI, preview, the phone

- Checkpoints: `bash scripts/checkpoint.sh "…"` on `work/phase-vk-extractor` (your SESSION_STATE
  section each time). CI: `checkpoint-validation` green; `preview-apk` builds a release-flag
  preview (`com.alal.yft.preview`).
- **The phone** (only after S5's run 3 is counted, with the owner's OK): the public links signed
  out, the owner's sign-in links (§5.1) signed in to VK in YFT's browser by him (open
  the post, tap its video: an address with a video id — `video…`, `z=video…` — is read by v1;
  none → §8), and one lookup each on
  TikTok, Facebook, Instagram and X (no change expected). He sends Copy details; the agent judges.
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
- `al_video` by form POST (`postForm`), if VK0 shows the need.
- VK messenger channel posts (`vk.ru/im/channels/{id}?cmid=…`, `m.vk.ru/mail/channels/…`): no
  video id in the address; the messenger's data needs the session (the owner's links, §5.1).
- VK in `PlayerFileFallback` (the player's file as the row when every path fails), if the owner
  says so at VK0.
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
