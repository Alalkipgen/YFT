# VK fixtures (VK0, 2026-10-24)

Real anonymous responses to public links, fetched by the VK agent at VK0 (no sign-in, no
cookies sent except VK's own anonymous redirect cookies on the vkvideo.ru page), then sanitized.

| File | Source (public) | Shows |
|---|---|---|
| `embed-video-public.html` | `https://vk.com/video_ext.php?oid=-50883936&id=456244102` (no hash) | `window.cur` → `apiPrefetchCache`; item `type=video`, `files` with `mp4_144`…`mp4_2160`, `hls`, `hls_fmp4`, `hls_ondemand`, `dash_sep`, `dash_ondemand`, `dash_webm`, `dash_webm_av1`, `failover_host`; `trailer` (a preview) |
| `embed-clip-public.html` | `https://vk.com/video_ext.php?oid=30014565&id=456240946` | a clip: `type=short_video` |
| `embed-postlive.html` | `https://vk.com/video_ext.php?oid=-140332&id=456239111` | a finished live: `live_status=postlive` |
| `embed-not-found.html` | the embed of a deleted video | `<div id="video_ext_msg">Video not found.</div>` (VK shows the same text for deleted, removed and list-only videos) |
| `embed-spa-shell-vkvideo.html` | `https://vkvideo.ru/video_ext.php?…` (also seen now and then on vk.com) | an SPA shell (`spa_root`, `isRedirectChallenge`), no player data |
| `page-vkvideo-desktop.html` | `https://vkvideo.ru/video-50883936_456244102` (path A, after VK's anonymous redirects) | an SPA shell; its prefetch cache holds only ads/config, no video |
| `hls-master-video.m3u8` | the `hls` master of the first embed | `EXT-X-STREAM-INF` 144p–2160p |
| `hls-fmp4-master-video.m3u8` | the `hls_fmp4` master | video variants + 5 `TYPE=AUDIO` renditions |
| `hls-audio-rendition.m3u8` | one audio rendition of `hls_fmp4` | an audio-only media playlist |
| `dash-sep-video.mpd` | the `dash_sep` manifest | avc1/mp4a representations, no `ContentProtection` |

No real audio-only *master* was found at VK0; a test that needs one builds it from these files and
says so (synthetic).

## Sanitization

- Every query value of a media, image or player address is `FIXTURE` (`expires`, `srcIp`, `srcAg`,
  `sig`, `id`, `hash`, …); hosts and paths stay (`vkvd{N}.okcdn.ru`, `sun{N}-{N}.userapi.com`,
  `iv.okcdn.ru`).
- No IP address, cookie, user id of the fetcher, access token or hex/base64 run that looks like a
  secret; sensitive keys (tokens, hashes, user ids) replaced; scripts that are not page data removed.
- Titles stay.

## Encoding

VK's embed and desktop pages are served as **windows-1251**. These files are stored **UTF-8**
(decoded once). A test of the charset path must re-encode the text as windows-1251 and give the
fake client the matching `Content-Type`.
