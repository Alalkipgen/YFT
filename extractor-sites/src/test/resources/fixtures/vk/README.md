# VK fixtures (VK0, 2026-10-24; VK5, 2026-10-25)

Real anonymous responses to public links, fetched by the VK agent at VK0 (no sign-in, no
cookies sent except VK's own anonymous redirect cookies on the vkvideo.ru page), then sanitized.

| File | Source (public) | Shows |
|---|---|---|
| `embed-video-public.html` | `https://vk.com/video_ext.php?oid=-50883936&id=456244102` (no hash) | `window.cur` → `apiPrefetchCache`; item `type=video`, `files` with `mp4_144`…`mp4_2160` (8), `hls`, `hls_fmp4`, `dash_sep`, `dash_webm`, `dash_webm_av1`, `failover_host`; `trailer` (a preview) |
| `embed-clip-public.html` | `https://vk.com/video_ext.php?oid=30014565&id=456240946` | a clip: `type=short_video`; `files` with `mp4_144`…`mp4_1080` (6), `hls`, `hls_fmp4`, `dash_sep`, `dash_webm`, `failover_host` |
| `embed-postlive.html` | `https://vk.com/video_ext.php?oid=-140332&id=456239111` | a finished live: `live_status=postlive`; `files` with `mp4_720`, `hls_ondemand`, `dash_ondemand`, `failover_host` |
| `embed-not-found.html` | the embed of a deleted video | `<div id="video_ext_msg">Video not found.</div>` (VK shows the same text for deleted, removed and list-only videos) |
| `embed-spa-shell-vkvideo.html` | `https://vkvideo.ru/video_ext.php?…` (also seen now and then on vk.com) | an SPA shell (`spa_root`, `isRedirectChallenge`), no player data |
| `page-vkvideo-desktop.html` | `https://vkvideo.ru/video-50883936_456244102` (path A, after VK's anonymous redirects) | an SPA shell; its prefetch cache holds only ads/config, no video |
| `hls-master-video.m3u8` | the `hls` master of the first embed | `EXT-X-STREAM-INF` 144p–2160p |
| `hls-fmp4-master-video.m3u8` | the `hls_fmp4` master | video variants + 5 `TYPE=AUDIO` renditions |
| `hls-audio-rendition.m3u8` | one audio rendition of `hls_fmp4` | an audio-only media playlist |
| `dash-sep-video.mpd` | the `dash_sep` manifest | avc1/mp4a representations, no `ContentProtection` |

No real audio-only *master* was found at VK0; a test that needs one builds it from these files and
says so (synthetic).

### VK5 (2026-10-25): path D

Fetched by the VK agent at VK5, signed out (no cookie of any kind sent), then sanitized the same way.

| File | Source (public) | Shows |
|---|---|---|
| `embed-adult-content.html` | `https://vk.com/video_ext.php?oid=-215889201&id=456243439` (the third of the owner's examples; vk.ru and `?list=` give the same page) | `<div id="video_ext_msg">` "…may contain adult content. Such videos can't be viewed on external sites", `window.embedErrorCallback?.(9)` (not found is `7`), `"apiPrefetchCache":[]` |
| `api-video-get-adult.json` | `api.vkvideo.ru` `video.get` (v `5.289`, the anonymous web token) for `-215889201_456243439` | one item `type=video`, 1078×1920; `files` with `mp4_144`…`mp4_1080` (6), `hls`, `hls_fmp4`, `dash_sep`, `failover_host` on `vk{N}-{N}.vkuser.net`; `trailer`; `groups` |
| `api-video-get-deleted.json` | the same call for a video id that does not exist | an item with no `files` and `restriction.can_play=0` ("Video deleted") |
| `api-error-auth.json` | the same call with no token | `error.error_code=5` ("User authorization failed") |

The token answer of `login.vk.ru` (`act=get_anonym_token`) is **not** a fixture: it is a token. The
tests build that answer themselves with made-up values (`VkApiPages.token`).

## Sanitization

- Every query value of a media, image or player address is `FIXTURE` (`expires`, `srcIp`, `srcAg`,
  `sig`, `id`, `hash`, …); hosts and paths stay (`vkvd{N}.okcdn.ru`, `sun{N}-{N}.userapi.com`,
  `iv.okcdn.ru`).
- VK2: in the three embeds, the first path segment of each `files` and `trailer` address is its
  key (`mp4_720`, `hls`, `dash_sep`, `trailer_mp4_240`, …) so tests can tell the renditions apart;
  `expires=FIXTURE` stays, and a test that reads the expiry puts its own number in.
- No IP address, cookie, user id of the fetcher, access token or hex/base64 run that looks like a
  secret; sensitive keys (tokens, hashes, user ids) replaced; scripts that are not page data removed.
- VK5: in `api-video-get-adult.json` each `files` address is `/{key}/FIXTURE` and each `trailer`
  address `/trailer_{key}/FIXTURE` (the signed path is gone); images are
  `iv.okcdn.ru/getVideoPreview/{w}x{h}`; `ov_id`, the `player` query and every other query value
  are `FIXTURE`; `description`, `track_code`, `tracking_info`, `ads` and `stats_pixels` removed;
  `groups` keep only id, name, screen name, `is_closed` and type. In the embed, the page scripts
  are removed and only the message, the error callback and the empty prefetch cache stay.
- Titles stay.

## Encoding

VK's embed and desktop pages are served as **windows-1251**. These files are stored **UTF-8**
(decoded once). A test of the charset path must re-encode the text as windows-1251 and give the
fake client the matching `Content-Type`.
