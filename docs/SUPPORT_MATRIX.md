# Support Matrix

Status values:

- **Planned** — architecture includes the capability, not yet implemented
- **Spike** — compile-time feasibility harness exists
- **Foundation** — production infrastructure is wired and tested, but the user-facing capability is not implemented yet
- **Blocked** — known unresolved blocker
- **Unsupported** — intentionally outside product scope

| Source or capability | Phase 0 status | Target phase | Notes |
| --- | --- | --- | --- |
| Production Compose app shell/navigation | Foundation | 1 | Eight required routes, theme state and back navigation validated |
| Room/DataStore/OkHttp/Media3 wiring | Foundation | 1 | Hilt providers, schemas/migration and foundation tests pass |
| Secret-redacting application logging | Foundation | 1 | Sensitive headers, credentials and signed query values are redacted |
| HTTPS direct MP4/WebM/audio | Spike | 3–4 | Media3 preview source and direct engine plan |
| HTML5 `video` / `audio` / `source` | Spike | 2 | Read-only DOM probe exists |
| WebView DownloadListener | Reference verified | 2 | Adapt from AlalDownloader design |
| WebView request URL/headers | Spike | 2 | `shouldInterceptRequest` design; response MIME needs probing |
| Literal `blob:` URL | Unsupported as a file | 2 | Resolve underlying request or manifest |
| HLS `.m3u8` preview | Spike | 3 | Media3 HLS source compiles; device playback remains to be exercised |
| HLS download/export | Planned | 4 | Non-DRM only |
| DASH `.mpd` preview | Spike | 3 | Media3 DASH source compiles; device playback remains to be exercised |
| DASH download/export | Planned | 4 | Non-DRM only |
| Separate video/audio tracks | Planned | 3–4 | Requires track selection and muxing |
| Signed/expiring URL | Planned | 3–5 | Resolver refresh callback |
| Cookie/header-protected media | Spike | 2–4 | Safe replay-header builder exists |
| DRM/Widevine | Unsupported | — | Detect and reject; no circumvention |
| TikTok adapter | Planned | 5A | Public/authorized non-DRM media only |
| Facebook adapter | Planned | 5B | Public/authorized non-DRM media only |
| Other websites | Planned | 5C | Add only with fixtures |
| YouTube adapter | Risk review required | 5D | Isolated adapter, maintenance and distribution constraints |
| Live-stream recording | Deferred | Post-MVP | Not part of initial release |
| Playlist/batch download | Deferred | Post-MVP | Not part of initial release |
