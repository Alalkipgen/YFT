# VK-Master fixtures (agent A, census recon, 2026-10-25)

For agent B's VK pack (`VkAnswerJudge`, `VkLadder` V1/V2) and agent C's kit (`ValueSource`,
`VkClientSource`). **Every value here is made up.** Nothing was copied from a live answer: the
files were written by hand (a generator script) from the live *shapes* only (key names, types,
host families and query-parameter names) that `docs/VK_MASTER_CENSUS.md` records. There is no
real token, id, secret, link, title, group name or response body (S8).

## Rules (match `extractor-sites/.../fixtures/vk/`'s and its `VkFixtureHygieneTest`)

- Ids: owner `-100000001`, video `400000001` (made up; not a VK id). Titles and group names start
  with `FIXTURE`.
- Every media, image and player address: the host *family* stays (`vk6-1.vkuser.net`,
  `vkvd1.okcdn.ru`, `iv.okcdn.ru`) so host rules can be tested; the path is `/{key}/FIXTURE` and
  **every query value is `FIXTURE`** (`expires`, `srcIp`, `srcAg`, `sig`, `id`, …). A test that
  reads an expiry or a size puts its own number in.
- No IP address, cookie, user id, access token, long hex or key-like run.
- **No token answer.** The anonymous token is never a fixture (it is a credential). A test builds
  `login.vk.ru`'s answer itself, with made-up values, as main's tests do (`VkApiPages.token`).
  Only the token endpoint's *error* shape is here (`api/token-error.json`).
- The API version in error echoes is `5.000` and the `client_id` is `100000` (made up). The real
  values live only in the pack's profile (B's `VkClient`, with provenance).
- The script snippet (`script/web-client-snippet.js`) has fake values in a realistic pattern; it
  exists so `ValueSource`/`VkClientSource` can test the strict pattern without a live script.

## Files

| File | Shows | Judge class (§5) |
|---|---|---|
| `api/video-get-direct-18plus.json` | an 18+ item: `files` `mp4_144`…`mp4_1080`, `hls`, `hls_fmp4`, `dash_sep`, `failover_host`; files on `vk{N}-{N}.vkuser.net`; `trailer` (never a row) | `DIRECT` |
| `api/video-get-direct-normal.json` | a normal video: files on `vkvd{N}.okcdn.ru`, also `dash_webm`, `dash_webm_av1` | `DIRECT` |
| `api/video-get-clip.json` | a clip: `type=short_video` | `DIRECT` |
| `api/video-get-postlive.json` | a finished live: `live_status=postlive`, `mp4_720`, `hls_ondemand`, `dash_ondemand` | `DIRECT` |
| `api/video-get-deleted.json` | no `files`; `content_restricted=1`; `restriction.can_play=0`, `icon_name=delete_outline` | `REMOVED` |
| `api/video-get-age-restricted.json` | **synthetic** (not seen live signed out): no `files`; `restriction.can_play=0` with an age icon | `AGE_LIMITED` → browser |
| `api/video-get-shape-changed.json` | an item with neither `files` nor `restriction` | `SHAPE_CHANGED` |
| `api/error-auth.json` | `error_code=5` "User authorization failed" (no/garbled/expired token) | `VALUE_REJECTED` |
| `api/error-request.json` | `error_code=8` "Invalid request" (a bad `v`) | `VALUE_REJECTED` |
| `api/error-captcha.json` | `error_code=14` with `captcha_sid`/`captcha_img` (made up) | `BOT_CHECK` |
| `api/error-too-many.json` | `error_code=6` | `RATE_LIMITED` |
| `api/error-flood.json` | `error_code=9` | `RATE_LIMITED` |
| `api/error-rate-limit.json` | `error_code=29` | `RATE_LIMITED` |
| `api/error-geo.json` | `error_code=13300` (`API_ERROR_ACCESS_GEO` in VK's script) | `REGION_LIMITED` |
| `api/token-error.json` | `login.vk.ru`: `{"error":"invalid_client",…}` (a wrong `client_id`) | `VALUE_REJECTED` |
| `embed/embed-adult-content.html` | the embed's adult message + `embedErrorCallback(9)` + an empty prefetch cache (V0's trigger) | — (main's) |
| `embed/embed-spa-shell.html` | VK's SPA shell embed answer | — (main's) |
| `manifests/hls-master.m3u8` | an `hls` master, 6 variants, 144p–1920 lines | rows |
| `manifests/hls-fmp4-master.m3u8` | an `hls_fmp4` master, 5 `TYPE=AUDIO` + 6 variants | rows |
| `manifests/hls-audio-rendition.m3u8` | **synthetic** audio-only media playlist | — |
| `manifests/dash-sep.mpd` | a `dash_sep` MPD, avc1 ×6 + mp4a, no `ContentProtection` | rows |
| `script/web-client-snippet.js` | the web client's value pattern (fake values) | — (V2 source) |

`synthetic` marks a shape not seen live in this recon (VK showed no age gate signed out; the
audio-only playlist is built, as main's VK2 test does). Their keys follow VK's own names
(`restriction`, `can_play`, `icon_name`), but B must keep the judge conservative for them.

## Encoding

UTF-8. VK's embed pages are served windows-1251; a test of the charset path re-encodes as main's
do (`fixtures/vk/README.md`).
