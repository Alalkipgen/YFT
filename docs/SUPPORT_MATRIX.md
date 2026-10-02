# Support Matrix

Status values:

- **Planned** — architecture includes the capability, not yet implemented
- **Spike** — compile-time feasibility harness exists
- **Foundation** — production infrastructure is wired and tested, but the user-facing capability is not implemented yet
- **Implemented** — production behavior and automated coverage exist for the stated scope
- **Blocked** — known unresolved blocker
- **Unsupported** — intentionally outside product scope

| Source or capability | Current status | Target phase | Notes |
| --- | --- | --- | --- |
| Production Compose app shell/navigation | Foundation | 1 | Eight required routes, theme state and back navigation validated |
| Room/DataStore/OkHttp/Media3 wiring | Foundation | 1 | Hilt providers, schemas/migration and foundation tests pass |
| Secret-redacting application logging | Foundation | 1 | Sensitive headers, credentials and signed query values are redacted |
| HTTPS direct MP4/WebM/audio detection | Implemented | 2 | Extension/MIME/DOM/request/download observations normalize per page |
| HTML5 `video` / `audio` / `source` | Implemented | 2 | Production read-only DOM probe plus committed Chromium-validated fixture |
| WebView DownloadListener | Implemented | 2 | Captures MIME, filename, size and secure request context |
| WebView request URL/headers | Implemented | 2 | Observes GET requests; strongly hinted URLs receive bounded metadata probes |
| Literal `blob:` URL | Unsupported as a file | 2 | Literal is rejected; underlying HTTP(S) source/manifest observations are detected |
| Direct MP4/WebM/audio preview | Implemented | 3 | Bounded metadata validation and explicit Media3 progressive source; device playback remains to be exercised |
| HLS `.m3u8` preview | Implemented | 3 | Bounded master/media parsing, real variants and explicit Media3 HLS source; device playback remains to be exercised |
| HLS download/export | Planned | 4 | Non-DRM only |
| DASH `.mpd` preview | Implemented | 3 | Secure MPD parsing, real representations and explicit Media3 DASH source; device playback remains to be exercised |
| DASH download/export | Planned | 4 | Non-DRM only |
| Separate video/audio tracks | Implemented | 3–4 | Resolution and preview tabs are explicit; offline muxing remains Phase 4 |
| Signed/expiring URL | Implemented | 3–5 | Known expiry fails clearly; refresh callback remains Phase 4/5 work |
| Cookie/header-protected detection | Implemented | 2 | Same-origin probe replay is tested; cross-origin credentials are stripped |
| Cookie/header-protected preview | Implemented | 3 | Same-origin replay and cross-origin stripping are tested; device playback remains to be exercised |
| DRM/Widevine | Unsupported | — | Hints and manifest keys are detected and rejected; no circumvention |
| TikTok adapter | Implemented | 5A | `tiktok.com/@user/video/{id}`, `tiktok.com/video/{id}`, `m.tiktok.com/v/{id}.html`, `vm.tiktok.com/{code}`, `vt.tiktok.com/{code}`. Progressive MP4 only, every exposed bitrate rendition, exact size when the page states it. Photo posts, private/removed posts, login walls, region blocks, DRM flags and changed markup fail with their own reason. No sign-in is performed; the user's own browser session is replayed to tiktok.com. Verified 2026-10-02 against committed offline fixtures |
| Facebook adapter | Implemented | 5B | `facebook.com/watch/?v={id}`, `facebook.com/video.php?v={id}`, `facebook.com/{handle}/videos/{id}` with or without a title slug, `facebook.com/reel/{id}`, `fb.watch/{code}` and `facebook.com/share/v|r/{code}`, also on the `m.`, `web.` and `mbasic.` hosts. Progressive MP4 renditions (Full HD/HD/SD, current and legacy delivery fields) plus the page's own DASH manifest URL, which the existing resolver splits into separate tracks; inline DASH XML without a URL is not used. CDN expiry (`oe`, `_nc_exp`, `expire`) is attached to every candidate, and a page whose links already expired fails as expired instead of being queued. Login walls and checkpoint redirects, private/removed content, region blocks, DRM flags, media-free posts and changed markup fail with their own reason. No sign-in is performed; the user's own browser session is replayed to facebook.com only, and the page read is capped at 6 MiB. Verified 2026-10-02 against committed offline fixtures |
| Vimeo adapter | Implemented | 5C | `vimeo.com/{id}`, `vimeo.com/{id}/{hash}` for unlisted clips, `vimeo.com/channels/{name}/{id}`, `vimeo.com/groups/{name}/videos/{id}`, `vimeo.com/album|showcase/{id}/video/{id}` and `player.vimeo.com/video/{id}` with `?h=`. Reads the clip page and, when the page only names it, follows the `config_url` on `player.vimeo.com` and nowhere else; a changed page falls back to that same player configuration. Returns progressive MP4 files from the highest resolution down with the exact size the configuration states, plus the HLS and DASH manifests of the CDN the configuration marks as default. The single configuration expiry is attached to every candidate and an expired configuration fails as expired. Password-protected clips, private or deleted clips, region blocks, DRM-protected files, file-less configurations and changed markup fail with their own reason. On-demand, event and other paid or live surfaces are intentionally not claimed. No sign-in is performed; the user's own browser session is replayed to Vimeo hosts only. Verified 2026-10-02 against committed offline fixtures |
| Instagram, X (Twitter) and adult or pirate aggregators | Generic detection only | — | No shipped adapter. Page-only extraction is not possible without impersonating an internal API (X) or risking a checkpoint on the user's own account (Instagram), and aggregator sites fail the authorized-media scope in `docs/RISKS.md`. Whatever the user's own browser loads is still detected by the generic pipeline |
| Other websites | Planned | Post-5 | Add only with committed fixtures and a justification row here |
| YouTube adapter | Blocked | 5D | No adapter ships. Page-only extraction is not possible: stream URLs require transforms from the site's own player JavaScript, per-session proof-of-origin tokens and, increasingly, a server-driven streaming protocol, so a working adapter would need remote code execution or client impersonation, could not be pinned by committed fixtures, and would carry the project's highest distribution risk. YouTube links stay on the generic detector. Reviewed 2026-10-02 in `docs/YOUTUBE_RISK_REVIEW.md` and `docs/decisions/ADR-004-youtube-adapter.md` |
| Live-stream recording | Deferred | Post-MVP | Not part of initial release |
| Playlist/batch download | Deferred | Post-MVP | Not part of initial release |
