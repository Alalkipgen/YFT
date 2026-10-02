# Phase 6 Hardening Audit

Started 2026-10-02 on `work/phase-6-hardening` from the Phase 5E completion head `c608b01`.
Each finding has a severity (High, Medium, Low, Info) and a status. Phase 6 is done only when no
High finding is open. Device-only checks that this sandbox cannot run (no `/dev/kvm`) are listed
as such rather than claimed.

## Security and privacy

| ID | Finding | Severity | Status |
| --- | --- | --- | --- |
| S1 | `allowBackup="false"` stops cloud backup, but Android 12+ device-to-device transfer still copies app data (WebView cookies, the download database) unless `dataExtractionRules` excludes it. Lint reports `DataExtractionRules` | Medium | Fixed — `data_extraction_rules.xml` excludes every domain; `fullBackupContent=false` |
| S2 | No way to clear browsing data: cookies, web storage, cache, form data and the browser's back stack persist until the app is uninstalled | Medium | Fixed — Settings › Privacy › Clear browsing data (cookies, web storage, cache, HTTP auth, geolocation, plus the in-memory detected-media list and preview selection); device check pending |
| S3 | Cleartext is disabled only through the manifest flag; there is no network security configuration pinning "system CAs only, no cleartext" for every build type | Low | Fixed — `network_security_config.xml`: no cleartext, system CAs only |
| S4 | Download notifications show media titles without a redacted public version for secure lock screens | Low | Fixed — `VISIBILITY_PRIVATE` + count-only public version (test) |
| S5 | `POST_NOTIFICATIONS` is declared but never requested, so on Android 13+ download progress is invisible unless the user enables notifications manually | Medium | Fixed — runtime request before the first download (Android 13+) |
| S6 | Clipboard is never read. Any paste feature must read it only after an explicit tap | Info | Compliant — Home › Paste reads the clipboard only when tapped (test) |
| S7 | JavaScript bridges: the browser WebView exposes none; the YouTube solver WebView exposes one string-only method and loads only app-served pages (ADR-005) | Info | Compliant |
| S8 | TLS: no custom `TrustManager`, `HostnameVerifier` or socket factory anywhere; every engine and probe refuses non-HTTPS | Info | Compliant |
| S9 | Output file names come from page titles. Dots are dropped (Phase 4), but Unicode bidirectional controls (for example U+202E) could still make a name display with a spoofed extension | Low | Compliant — sanitizer maps bidi/zero-width controls to spaces (test) |
| S10 | Logging goes through `AndroidAppLogger`, which redacts cookies, tokens and signed query values; there are no direct `android.util.Log` calls elsewhere | Info | Compliant |
| S11 | Exported components: only the launcher activity; the download service is not exported | Info | Compliant |
| S12 | WebView remote debugging is off in release builds (platform default for non-debuggable apps) | Info | Compliant |
| S13 | The Library must open and share files kept in app storage without exposing the app's private directory | Low | Compliant — `AppPrivateDownloadProvider`: not exported, read-only, per-item URI grants only, paths confined to `noBackupFilesDir/downloads`, `.part` staging files never served (tests) |
| S14 | Detected media can carry signed URLs once it outlives the browser page | Low | Compliant — `DetectedMediaStore` is memory-only (never routed, saved or logged), shows only the page host, and is cleared with browsing data (tests) |

## Reliability

| ID | Finding | Severity | Status |
| --- | --- | --- | --- |
| R1 | Wi-Fi-only downloading is not available; transfers run on any validated network, including metered mobile data | Medium | Fixed — Wi-Fi-only preference via `DownloadPolicyController` (tests) |
| R6 | App-private destination: a second download with an already-used name failed at publish time (`Completed destination already exists`) | Medium | Fixed — names are reserved with a " (n)" suffix (tests) |
| R2 | The queue supports 1–10 concurrent transfers, but the limit is fixed at 2 and not user-configurable | Low | Fixed — Settings › Downloads at the same time (1–4) |
| R3 | Free space is not checked before a transfer of known size starts; a full disk is detected only when a write fails | Low | Fixed — `DownloadEnqueuer` measures the target volumes (`StatFsStorageSpace`) before any destination exists and rejects a probed or exactly known size that does not fit with 32 MiB headroom as `INSUFFICIENT_STORAGE`; estimates and unmeasurable volumes still start (tests) |
| R4 | Process death, network loss, redirects, expiry, range refusal, validator mismatch, cancellation cleanup and migrations are covered by Phase 4 JVM tests | Info | Covered (JVM) |
| R5 | Backgrounding, screen-off, real process kills, real network switching and a nearly full device need a device | Info | Device-only |
| R7 | The Downloads screen did not say why queued work was not moving while Wi-Fi-only held it back or the device was offline | Low | Fixed — banner from `DownloadNetworkStatus` ("Waiting for Wi-Fi", "No connection"); the policy is applied when the screen opens (tests) |

## Performance

| ID | Finding | Severity | Status |
| --- | --- | --- | --- |
| P1 | Candidate detection is debounced, deduplicated and capped per page (Phase 2) | Info | Compliant |
| P2 | The download list observes every record with its segments; acceptable for the expected record counts, but completed records are never pruned | Low | Fixed — `DownloadStorageJanitor` runs once per process from `MainActivity`: it keeps the newest 200 finished records and removes `.part` staging files and HLS/DASH/mux workspaces left by earlier processes (workspaces of unfinished records are kept); workspace names come from the shared `DownloadWorkspaces` helper (tests) |
| P3 | Startup creates no WebView, player or solver eagerly; the solver WebView exists only during a YouTube solve | Info | Compliant |
| P4 | Startup, frame timing, memory and battery profiling need a device | Info | Device-only |

## UI

| ID | Finding | Severity | Status |
| --- | --- | --- | --- |
| U1 | Library and Detected Media are placeholder screens | Medium | Fixed — Library lists `Download/YFT` (MediaStore) and app-storage files with in-app playback, open, share and confirmed delete; Detected Media lists the last page's candidates with a Preview hand-off (tests) |
| U2 | Settings offers only the theme; download preferences and data clearing are missing | Medium | Fixed (6C) — quality, location, Wi-Fi only, mobile-data confirmation, concurrency, theme, clear browsing data and clear history (tests) |
| U3 | Home has no link entry; About lacks licenses, third-party notices and privacy details | Low | Fixed — Home link field with tap-only Paste opens the browser on that link; About shows version, scope, privacy and every third-party notice with its license text, checked against `THIRD_PARTY_NOTICES.md` (tests) |
| U4 | Browser controls are text glyphs without accessible labels; the media button has no icon | Low | Fixed — icon buttons with content descriptions (Navigate back, Previous page, Next page, Stop loading, Reload page), a media button with an icon, headings marked for screen readers (tests) |
| U5 | The theme uses default Material colors rather than an original YFT palette | Low | Fixed — original teal/copper light and dark schemes; every text/background pair meets 4.5:1 (test) |

## Phase 6 result

No High finding was raised and every Medium and Low finding is fixed or compliant. What remains is
device-only (R5, P4 and the device checks noted for S2 and R1) and is listed in
`docs/TEST_MATRIX.md` under the runtime tests that need a device.
