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
| TikTok adapter | Planned | 5A | Public/authorized non-DRM media only |
| Facebook adapter | Planned | 5B | Public/authorized non-DRM media only |
| Other websites | Planned | 5C | Add only with fixtures |
| YouTube adapter | Risk review required | 5D | Isolated adapter, maintenance and distribution constraints |
| Live-stream recording | Deferred | Post-MVP | Not part of initial release |
| Playlist/batch download | Deferred | Post-MVP | Not part of initial release |
