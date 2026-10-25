# VK Master census & recon (agent A)

Status: recon done, 2026-10-25 (the owner's go, `docs/VK_MASTER_PLAN.md`). Branch
`work/phase-vk-master-census` from `origin/work/phase-vk-master` (`50f2e9fb`). Docs and sanitized
fixtures only — no code. This file answers the seven recon questions of the plan's §11 and §Appendix
A and proposes VK's census slots, W slots and budget. The owner gates V2's live source and Vx
(§8, §Vx).

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

- VK5 path D ရဲ့ လမ်းကြောင်း (anonymous token `login.vk.ru` → `video.get` `api.vkvideo.ru`) တစ်ခုတည်းကိုပဲ
  သုံးစရာ ရှိပါတယ်။ **သီးခြား လမ်းကြောင်း (Vx) မတွေ့ပါ** — api.vk.com / api.vk.ru တွေက token endpoint နဲ့
  method endpoint အတူတူကိုပဲ မှီခိုတယ်။ mobile page တွေမှာ signed-out video data မပါဘူး။ ⇒ Vx မဆောက်သင့်။
- web client ရဲ့ values (client id, secret, app id, scopes, api version) က vkvideo.ru ရဲ့ public script
  (`core_spa` နဲ့ `common` chunk) ထဲ တည်ငြိမ်စွာ ရှိတယ် — load နှစ်ခု၊ static host sharding
  ပြောင်းပေမဲ့ script content hash အတူတူ ⇒ **V2 self-heal source အဖြစ် သုံးလို့ရတယ်** (owner go လိုသေး)။
  **yt-dlp က second source မဖြစ်ဘူး** — သူက login လိုတဲ့ `al_video.php` ကို သုံးတယ်၊ anonymous values
  မသယ်ဘူး။
- error codes၊ media hosts (18+ → `vk{N}-{N}.vkuser.net`, normal → `vkvd{N}.okcdn.ru`)၊ file expiry
  (~72 h)၊ 403 သည် `srcIp` **နှင့်** `srcAg` နှစ်ခုလုံးနဲ့ bind ဖြစ်တာ (track E) — အကုန် အောက်မှာ။
- VK census slots ၁၀ ခု၊ W slots ၃ ခု၊ budget (၄ req / 1 MB / 15 s) ကို အဆိုပြုထားတယ်။

## 1. How this recon was run (and its limits)

Live, signed out, on the owner's own public example links (loaded from the repo's own docs at run
time; never typed, never printed). ADR-006 decision 1 allows VK's guest API and the web client's
anonymous identity for public videos; this recon used exactly the request kinds VK5's recon used —
the page and its public script, one anonymous token (in memory only), `video.get`, HLS/DASH
manifests, and 1-byte `Range` checks. **No sign-in, no captcha or age-gate answer, no whole-file
download.** Nothing printed a token, cookie, signed URL, body, IP, real id or title; only statuses,
error codes, key names, counts, sizes and host *families* were recorded. Every raw capture was
deleted after the shapes were noted.

- **Network:** a US data-centre IP (the sandbox), IPv4 only. VK answered it for every public example,
  so a cloud IP is **not** blocked for these lookups today — but it is not a phone, so the canary
  question (§10) stays the owner's. B0 and S5 are the phone's job (plan §12).
- **One run each** for every "plainly-wrong value" probe (§3), as the plan requires.

## 2. Q1 — Independence of other ways (the basis for Vx)

**No independent route was found. Vx should not be built now.**

| Probe (once each) | Result | Reading |
|---|---|---|
| `video.get` on `api.vk.com` and `api.vk.ru` with the same anonymous token | both `200` with a `response` | same API, a host alias — **not** independent of `api.vkvideo.ru` |
| `get_anonym_token` on `login.vk.com` (vs `login.vk.ru`) | `200 {"type":"okay"}` | a host alias of the **same** token endpoint |
| `m.vkvideo.ru/video-…` and `m.vk.com/video_ext.php` for an 18+ video, signed out | SPA/redirect shell, `apiPrefetchCache:0`, `files:0` | no inline media signed out; nothing to parse without the token |
| yt-dlp's VK extractor (`al_video.php`, `vk.com`) | code `3` → "login required" for signed-out adult; carries **no** anonymous client values | not an anonymous route, not a value source |

Every signed-out route that returns an 18+ video's files shares V2's token endpoint
(`login.vk.{ru,com}`) and `video.get` (`api.{vkvideo.ru,vk.com,vk.ru}`). The host **aliases** are a
small resilience note for `VkClient` (if one host is blocked, another spelling of the same API may
answer), **not** a second way. → Backlog item Vx stays closed unless VK ships a genuinely different
signed-out delivery (plan §15).

## 3. Q3 — Error taxonomy (the judge's table, §5)

Each row tried once with one plainly-wrong value. HTTP is almost always `200`; the verdict is in the
JSON (`error.error_code`, or `restriction`). VK's own error names come from its public script
(`common` chunk, `API_ERROR_*`); the numbers match.

### 3.1 `video.get` (`api.vkvideo.ru`)

| Input (one wrong value) | `error_code` / shape | VK's name | Judge class (§5) | Next |
|---|---|---|---|---|
| no token | `5` "User authorization failed" | `API_ERROR_AUTH` | `VALUE_REJECTED` | V2 (heal), then end |
| garbled token (reversed) | `5` | `API_ERROR_AUTH` | `VALUE_REJECTED` | as above |
| wrong API `v` (`2.0`) | `8` "Invalid request" | `API_ERROR_REQUEST` | `VALUE_REJECTED` | V2 / end |
| nonexistent video id | `200`, item with `restriction.can_play=0`, `content_restricted=1`, `restriction.title`/`content_restricted_message` "Video deleted", `icon_name=delete_outline`, no `files` | — | `REMOVED` | final (VK's "isn't available") |
| (not reproducible signed out, from the script's codes) | `6` `API_ERROR_TOO_MANY`, `9` `API_ERROR_FLOOD`, `29` `API_ERROR_RATE_LIMIT` | — | `RATE_LIMITED` | stop site + 30-min cooldown |
| (from the script) | `14` `API_ERROR_CAPTCHA` (carries `captcha_sid`, `captcha_img`) | — | `BOT_CHECK` | stop site + cooldown; only the user answers it in the browser |
| (from the script) | `13300` `API_ERROR_ACCESS_GEO` | — | `REGION_LIMITED` | final |
| (from the script) | `15` `API_ERROR_ACCESS`, `17` `API_ERROR_AUTH_VALIDATION`, `204` `API_ERROR_ACCESS_VIDEO` | — | treat as `PRIVATE`/`AGE_LIMITED` only with other positive evidence; else `TRANSIENT` | next rung |

VK's full `API_ERROR_*` map (from the public script, for B's constant names): `3` METHOD, `5` AUTH,
`6` TOO_MANY, `8` REQUEST, `9` FLOOD, `10` SERVER, `14` CAPTCHA, `15` ACCESS, `17` AUTH_VALIDATION,
`18` USER_DELETED, `23` METHOD_DISABLED, `24` NEED_CONFIRMATION, `28` APP_AUTH, `29` RATE_LIMIT,
`30` PRIVATE_PROFILE, `43` SECTION_DISABLED, `100` PARAM, `104` NOT_FOUND, `200` ACCESS_ALBUM,
`201` ACCESS_AUDIO, `204` ACCESS_VIDEO, `504` BALANCE, `13300` ACCESS_GEO.

### 3.2 `login.vk.ru` `?act=get_anonym_token`

Body is the web client's JSON form; it needs `Content-Type: application/json` (main's `postJson`
already sends it — a plain form body without it gets `401 invalid_request`).

| Input (one wrong value) | Shape | Reading |
|---|---|---|
| all values correct | `200 {"type":"okay","data":{access_token, expires}}`: a ~324-char token, `expires` = epoch seconds ~86 400 s ahead (24 h) | the token |
| wrong `client_id` | `401 {"error":"invalid_client","error_description":"invalid app entity…"}` | the client id is the identity the endpoint checks ⇒ a rotated **client_id** is the breaking value; class `VALUE_REJECTED` |
| **wrong `client_secret`** | `200 type=okay`, a usable token | **the mint does not verify the secret** (see §4a) |
| empty `scopes` | `200`, a shorter token (~290 chars) | scopes shape the token, do not gate it |
| wrong `version` / `isApiOauthAnonymEnabled=true` | `200`, a usable token | neither gates minting |

The adult message itself is V0's trigger (main's embed), **not** a Master class (plan §5).

### 3.2a Finding for V2 / self-heal (important)

A token minted with a **wrong `client_secret` still worked** for `video.get` on a normal public
video (and so did tokens from empty scopes / wrong app id). So, today, the value the flow actually
depends on is the **`client_id`** (a wrong one → `invalid_client` at mint; and `client_id` is also a
`video.get` query param). This means:

- V2's self-heal should prioritise healing **`client_id`** (and the API `v`), not the secret.
- A `video.get` `error_code=5`/`8` after a *successfully minted* token points at `client_id`/`v`/the
  token flow, not the secret — the judge's `VALUE_REJECTED` → V2 is right.
- C's open question (R9 carrying the values, plan §2/§9): the secret is the least load-bearing of
  them today; if R9 ever carries a value, `client_id` + `v` matter most. (Recorded for C; the owner
  decides — A does not build it.)

## 4. Q4 — Media hosts, 403, srcIp, srcAg (track E evidence)

| Thing | Finding |
|---|---|
| **Media hosts** | 18+ videos: files on `vk{N}-{N}.vkuser.net`. Normal videos and clips: `vkvd{N}.okcdn.ru`. `failover_host` is always `vkvd{N}.okcdn.ru`; a 1-byte `Range` on the failover host (same path+query) also answered `206`. Images/first-frame/trailer previews: `iv.okcdn.ru` — **never** a row. |
| **Manifests** | `hls` master (6 variants, no audio renditions), `hls_fmp4` master (5 `TYPE=AUDIO` + 6 variants), `dash_sep` (avc1+mp4a, **no `ContentProtection`**). Normal videos also offer `dash_webm`, `dash_webm_av1`. |
| **Expiry window** | `expires` on the file URLs is **~72 h** ahead for every example measured (plan's "72–127 h" is the outer bound; V's lifetime check should assume ~72 h as the floor). `expires` is epoch **seconds** on these API files. |
| **403 / 400 by `srcAg`** | A 1-byte `Range` with the lookup's own user agent → `206`; the **same URL with a different user agent → `400`**. So the file binds the user agent (`srcAg`): the download must send the lookup's agent (exactly V0's rule). `Referer` (vk.com) is **optional** — `206` with or without it, no cookie. |
| **403 / 400 by `srcIp`** | `srcIp` is a **signed** query param (changing its value → `400`), and `sig` and `expires` are signed too (tampering any → `400`). The signature is the IP binding; a lookup and its download from the **same** IP pass (a phone's lookup and download are the same device). A Wi-Fi↔mobile switch between lookup and download changes the IP → the file 403/400s → **track E** (a fresh lookup). |
| **Evidence labels for track E** | `vk: file 400 srcAg` (wrong agent), `vk: file 400 srcIp/sig` (IP or signature mismatch / expired), `vk: file 206` (good). Never log the URL, the IP or the query values — only the host family and the status, as `SiteRowCheck` does. |

So: the file's 403 today is **both** `srcIp` (IP, via the signature) **and** `srcAg` (agent). Track E
must re-run the whole lookup (new signed URL for the current IP + agent); a byte-range resume is only
safe when the file is provably the same (plan §13 track E).

## 5. Q2 — The web client's values in the public script (V2 self-heal source)

**The source exists and is stable; yt-dlp is not a second source.**

- **Where:** vkvideo.ru's public JavaScript. The values sit in two chunks: the big
  `entrypoints/core_spa.<hash>.js` (~2.96 MB) and `web/chunks/common.<hash>.js` (~1.82 MB); a small
  `error_monitoring.<hash>.js` (~40 KB) carries the same client block and is the **cheapest** file
  to read.
- **The pattern (shape, values redacted):** an object literal
  `{clientSecret:<secret>,clientId:<id>,authClientId:<appId>}` built by a host-conditional
  (`...&&(clientId=…,clientSecret=…)` branches, one branch per VK host; the `vkvideo.ru` branch is
  the one selected by a `isVkVideo()`-style check). The API version is a separate export
  `{API_VERSION:()=>"<x.y.z>"}` in `common`; the scopes string
  (`audio_anonymous,video_anonymous,photos_anonymous,profile_anonymous`) and
  `isApiOauthAnonymEnabled` sit beside the token call (`get_anonym_token`) in a `~19 KB`
  `web/chunks/<hash>.js` login-config chunk.
- **Stability:** across two loads the static host **sharded** (`st.vkvideo.ru` →
  `st{N}-{N}.vkvideo.ru`) and the file names are content-hashed, but the **script content hashes were
  identical** and the values unchanged. So a self-heal must (a) discover the current script URLs from
  the page (they change names), then (b) match a strict pattern (`clientId:` / `API_VERSION:` /
  `get_anonym_token` nearby). A fixed script URL must **not** be bundled.
- **Strict pattern for `ValueSource`/`VkClientSource` (C):** read **`client_id`** as the digits after
  `clientId:` in the client block (and the API version after `API_VERSION:"`), each a short
  pattern-matched value, at most once a day, kept only after an OK token + a V pass (S5). Do **not**
  try to self-heal the secret from the page unless `client_id` healing proves insufficient (§3.2a:
  the secret is not load-bearing today).
- **yt-dlp as a second source: no.** yt-dlp's VK extractor uses `vk.com/al_video.php` with an `al=1`
  web request and **raises "login required" (code 3)** for signed-out adult videos; it carries no
  anonymous `client_id`/`client_secret`/token flow. So it is neither an independent route (Q1) nor a
  value mirror (Q2). The vkvideo.ru script is the only proven source. (If the owner wants a bundled
  alternate, it must be a dated copy from the script itself, per S5.)

A made-up snippet in the fixture (`script/web-client-snippet.js`) reproduces this pattern with fake
values so C can write the `ValueSource` matcher without a live script.

## 6. Q5 — Budgets (VK's `SocialBudget`, §6)

Measured per-request (US data-centre IP, warm): token `~350 ms` / `~0.4 KB`; `video.get`
`~0.35–0.5 s` / `≤ 22 KB` (largest seen 21 KB for a normal video with 5 manifests); one HLS master
`~0.5 s` / `~2 KB`; one DASH `~0.35 s` / `~7 KB`; the value-source script read (if V2 reads it live)
`~40 KB` for the cheap `error_monitoring` chunk (a self-heal should prefer it over the 3 MB
`core_spa`). All well under VK5's single 15 s deadline.

**Proposed `VkClient` budget** (placeholder for B; the owner/§12 B0 confirms on the phone):

| | requests | bytes | millis |
|---|---|---|---|
| VK | **4** | **1 MiB** | **15 000** |

Rationale: V1 sends **0** requests (re-reads main's kept pages). V2 sends **≤ 2** (one token, one
`video.get`) + **≤ 1** script read for self-heal = 3; plus one row's V check = 4. Bytes: `video.get`
≤ ~64 KB + a 40 KB script + manifests ≤ ~16 KB ≪ 1 MiB (main's token/`video.get` reads are bounded
to 16 KB/1 MiB). Time 15 s matches V0's single deadline so a Master lookup is never slower than main
(plan §3, decision 30). `SocialStops.kt` is a shared file (agent D adds the `VK` value); this is a
**hand-off**, not an edit — see §9.

## 7. Q6 — Census slots and W slots (§10)

### 7.1 VK census slots (10) — the owner keeps the links on the phone; this file holds only kinds

| Slot | Kind | Expect |
|---|---|---|
| 1 | 18+ public video (with `?list=`) | rows (V2/path-D) |
| 2 | 18+ public video (with `?list=`) | rows |
| 3 | 18+ public video (no `list`) | rows |
| 4 | normal public video | rows |
| 5 | normal public video | rows |
| 6 | clip (`short_video`) | rows |
| 7 | finished live (`postlive`) | rows |
| 8 | removed / deleted | no rows (`PRIVATE_OR_UNAVAILABLE`) |
| 9 | private / sign-in-only | no rows signed out; ambiguous without the user's session |
| 10 | region-limited (if the owner has one) | no rows (`GEO_RESTRICTED`); else a second normal video |

Slot 1 is `MASTER_SLOT` (W runs Master's part on it). This mirrors `SiteWays.kt`'s per-site `slots`
list and `Expect` shape; agent D registers VK in that shared table (hand-off, §9).

### 7.2 W slots (3) — `[MASTER_SLOT, …, …]`, by case kind, never a link

`vk → [1, 4, 8]`: slot 1 (an 18+ video — main's path D, V, Master's V1/V2 part), slot 4 (a normal
video main answers), slot 8 (removed — main's final answer). Caps: YouTube's W caps (≤ 60 s, ≤ 2 MB,
≤ the shared request cap). This mirrors `SiteWeekly.SLOTS` (hand-off for agent D).

## 8. Q7 — Sanitized fixtures B and C need

Written under `extractor-master/src/test/resources/fixtures/vk-master/` (README there). All values
made up; host families and key/param names kept; no token answer stored (built in tests). Summary:

- **API answers** (`api/`): `video-get-direct-18plus`, `-normal`, `-clip`, `-postlive`
  (`DIRECT`/rows, 18+ on `vkuser.net`, normal on `okcdn.ru`); `video-get-deleted` (`REMOVED`),
  `video-get-age-restricted` (synthetic `AGE_LIMITED`), `video-get-shape-changed` (`SHAPE_CHANGED`);
  `error-auth` (5), `error-request` (8), `error-captcha` (14), `error-too-many` (6), `error-flood`
  (9), `error-rate-limit` (29), `error-geo` (13300); `token-error` (`invalid_client`).
- **Embed** (`embed/`): `embed-adult-content.html` (V0's trigger: the adult message +
  `embedErrorCallback(9)` + empty `apiPrefetchCache`), `embed-spa-shell.html`.
- **Manifests** (`manifests/`): `hls-master.m3u8`, `hls-fmp4-master.m3u8` (5 audio renditions),
  `hls-audio-rendition.m3u8` (synthetic), `dash-sep.mpd` (no `ContentProtection`).
- **Script** (`script/`): `web-client-snippet.js` — the web client's value pattern with fake values,
  for C's `ValueSource`/`VkClientSource` matcher.

B should add a VK fixture-hygiene test like main's `VkFixtureHygieneTest` (no IP, signed value,
long hex/key-like run, or session name); a draft rule set is in the fixtures README.

## 9. Hand-offs to agent D (shared files A must not edit)

These are the shared-file changes VK needs; wave-1 records them, agent D (wiring) makes them (plan
§11, §13 wave 2):

- `social/SocialStops.kt`: add `SocialBudget.VK = (4, 1 MiB, 15 000)` (§6).
- `social/SiteWays.kt`: add VK's `Site` (id `vk`, media hosts `vk{N}-{N}.vkuser.net` +
  `vkvd{N}.okcdn.ru`, **never** `iv.okcdn.ru`; the 10 slots of §7.1) — B defines VK's media hosts in
  its own `Vk*` file; D registers the `Site`.
- `social/SiteWeekly.kt`: add `"vk" to listOf(1, 4, 8)` (§7.2).
- `SiteCensus*` / the debug census screen: VK joins the census (D).
- `recipes/ContractRecipes.kt`: add `VkRecipe` to `ALL` (D, after B defines it).
- The schema-2 recipe URL + the "Backup finder" gate + the debug mutate hook: D (plan §9, §13).

## 10. Canary (open, the owner's call)

A cloud IP (the sandbox, US data-centre) **did** get answers for every public VK example today, so a
daily CI canary is technically possible. But it is not a phone, and VK may treat a data-centre IP
differently over time (rate limits, a future bot check). Recommendation: **leave the canary off**
for now (plan §15 backlog); rely on W on the owner's phone. Revisit only if VK starts blocking the
cloud IP (W would still catch breaks on the phone).

## Vx — recommendation

**Do not build Vx.** No independent signed-out route exists (§2). Keep the host **aliases**
(`api.vk.com`/`api.vk.ru`, `login.vk.com`) as a resilience note inside `VkClient` (one may answer if
another is blocked), not as a separate rung. Reopen only if VK ships a genuinely different signed-out
delivery.
