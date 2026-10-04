# Changelog

All notable changes to Video Downloader (YFT) are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). `yft.versionCode` in `gradle.properties` rises by one
for every APK given to users, because Android refuses to install a lower one.

## [Unreleased]

### Added

- Audio from MP4: when a video has no separate audio file (Facebook, TikTok), the sheet's Audio
  section keeps the video's own sound as M4A (copied, not re-encoded) and MP3 converts it. MP3
  offers 320, 192 and 128 kbps.
- Facebook posts: `story.php`, `permalink.php`, a page's or group's `/posts/` links with a video
  are looked up like a reel, so the browser's Download button opens the sheet for the post's
  video.
- Facebook qualities: every picture size of the video's DASH tracks (for example 360p and 720p)
  is a Video row, merged on the phone with its sound into one MP4, and the sound alone is the
  Audio row (M4A, MP3). When Facebook's page lists only AV1/VP9, YFT asks once more for the AVC
  tracks without your session. AV1 rows stay off until the merge is proven on a phone.

### Changed

- One download sheet: View, Download and the browser's Download button all open the same
  "Download" sheet with exactly two sections: Audio (M4A, then MP3 320/192/128 kbps) and Video
  (one row per resolution — "480p", "720p · HD", "1080p · Full HD" — with the real picture,
  frame rate and size). The default quality is preselected; Details opens Download as. The
  former Music rows, Fast/High names and More formats list are gone (they repeated the same
  formats).
- One row per video: Facebook's HD, SD and DASH items, and a YouTube video's qualities, show as
  one video everywhere (Home, the found lists, the Download button count).
- Real qualities: the sheet shows a video's real resolution, frame rate and size (read from the
  stream data or the MP4 header) or the site's own "HD"/"SD", never the page title.
- Documentation: `docs/FIX_ADD_PLAN.md` plans Phase 11 (download flow like Snaptube) task by task
  after the beta.3 phone test, with one prompt per task (P1–P8) and a generic master prompt in
  `docs/prompts/`. The Phases 8–10 plan and the T01–T19 prompts were removed (they stay in Git
  history).

### Fixed

- Audio files saved from an MP4 track (Facebook, YouTube M4A) are named `.m4a` instead of `.mp4`.
- Merged Facebook rows show the real video size from the server plus the sound's estimate
  instead of a peak-bitrate guess (720p showed 219 MB for a 63 MB file).
- Browser: a page's video player that fetches one file in byte-range pieces no longer fills the
  Found list with dozens of "Video file" rows; the pieces count as one file, and a page whose
  video a site adapter found counts that video once.
- Browser: a video opened inside a page (YouTube's mobile site, Facebook, TikTok) is looked up
  like a new page, so the Download button follows it; the previous video's media no longer shows.
- Browser: Facebook reels and share links no longer show a black page, and TikTok videos play. The
  page fills the browser, the browser introduces itself like Chrome on the phone, "Open app"
  links keep the page, and a video's full-screen button works (Back leaves full screen).
- Facebook in the browser: the lookup asks Facebook for its desktop page, so a reel opened in the
  browser no longer shows "Facebook changed its page format" and finds the video's qualities.

## [1.0.0-beta.3] - 2026-10-04

### Added

- MP3: save a video's audio as MP3 (192 or 128 kbps) from "Video you copied" or Download as. The
  M4A is converted on the phone with LAME 3.100 (LGPL, see About → Licenses) and gets its title.
- Browser Download button: a Mint button appears when the page has a video you can save. One
  video opens "Video you copied"; several open "Found on this page", with a count badge.
- Search to download: Home opens the browser's start page ready to type. Words offer "Search
  YouTube" and "Search the web" (DuckDuckGo); a link opens. "Link you copied" has Download, and
  View sites shows YouTube, Facebook, TikTok, Instagram and X, with View all for your sites.
- Check copied links: when YFT opens with a new copied link, Home looks it up straight away
  (one video opens "Video you copied"). Settings › Privacy has a switch to turn it off; only the
  first web link is used and the copied text is never stored.
- "Video you copied": when a link pasted on Home finds one video, a sheet offers Music
  (M4A), Fast (up to 480p) and High (up to 720p) with real sizes, More formats and one Download
  button. Merged YouTube rows keep their sound; Wi-Fi only and the mobile data question apply.
- One redacted, size-limited local crash report outside backups, with About actions to View,
  Copy, Share text or Delete it. Reports are never sent automatically. Debug builds alone
  have a confirmed long-press crash test; release APK checks reject that action.
- Home **Copy details** for failed lookups: bounded adapter/status/markup/timeout steps kept
  in memory and cleared on edit or a new lookup. Query strings and session values are removed
  before copy/share.
- YouTube 480p, 720p and 1080p downloads with sound (T17). YouTube serves these qualities as a
  video-only MP4 and a separate M4A audio file; YFT downloads both, in requests of at most
  10 MiB, and merges them on the phone into one MP4. Each file resumes on its own. Such rows
  show a **Video + audio** chip and preview with sound. Only AVC video with AAC audio is
  offered, because the phone's muxer writes nothing else.
- A separate Android 14 CI emulator smoke job for the real browser: empty-page controls,
  public HTTPS navigation and best-effort HTML5 media detection. It captures three screenshots,
  safe address/WebView bounds and redacted logcat/test reports without storing UI hierarchies,
  raw request addresses, cookies or tokens in the artifacts.

### Fixed

- A link found twice (for example pasted, then seen again in the browser) keeps its separate
  audio track and codecs, so a merged YouTube row no longer loses its sound.
- TikTok videos found from a pasted link no longer fail with HTTP 403 when the download
  starts. The lookup keeps the cookies TikTok's page sets in memory only and sends them
  with the media request to TikTok's own media address; they are never stored, logged or
  sent to another site. Browser downloads keep the WebView's cookie.
- Facebook public reels and videos no longer fail as DRM-protected when the page carries only
  a playback certificate. DRM needs an explicit flag, a non-empty licence map or a graph
  licence URI; unreadable DRM metadata becomes a lookup details warning. Share links use the
  redirected reel/watch ID, titles decode HTML entities once and drop the trailing Facebook
  suffix, and HD/SD labels gain a height only when the DASH manifest or rendition states it.
- Home Paste/Open browser actions wrap at large text sizes rather than truncating the browser
  label. About privacy text includes both explicit clipboard actions.
- Empty browser now uses a Compose start page with tap-to-paste and saved Your sites.
  The WebView is created on the first valid navigation and retained across later navigation
  and recomposition. Web content is clipped below an opaque, higher-layer address/close bar.
  The idle address stays accessible instead of hiding its entire node with alpha zero.
  Found-media headings wrap to keep their count badge visible with the largest text.
- CI emulator screenshot collection now keeps the test APKs installed until `adb pull`
  finishes, then uninstalls them. Missing captures and pull/logcat failures remain errors;
  a collector regression reproduces the old post-test external-file deletion.
- Browser page-load crash caused by background request interception reading `WebView.url` and
  `WebSettings`. Navigation now supplies an atomic page-URL snapshot and the configured
  User-Agent is cached on the main thread. Concurrent observations are serialized with
  navigation in the ViewModel; stale requests cannot populate the next page.

### Changed

- Your sites now start with YouTube, Facebook and TikTok, shown with their logos (Simple Icons,
  CC0) on Home and the browser start page; other sites keep their letter. A saved list is updated
  once: the old Internet Archive, Wikimedia Commons and NASA defaults are replaced, sites you
  added stay, and a YouTube entry you already had is not duplicated.
- Downloads from YouTube's media servers (`googlevideo.com`) ask for at most 10 MiB per request,
  because YouTube slows down larger requests (T17).
- YouTube lookups ask more of YouTube's clients (T16, owner decision D2 = A + B + C): first
  YouTube's visionOS and Android app clients (values from yt-dlp 2026.08.19, without the user's
  cookie), then the embedded player, then the page's own web client with the user's session
  and a proof-of-origin token minted by YouTube's BotGuard in an offscreen WebView, and finally
  the mobile site. Tokens, visitor data and the attestation key are never logged, shown in Copy
  details or stored. Copy details now also says whether a token was minted.
- In YFT's browser, a YouTube bot check offers **Try again**, and the lookup also runs once by
  itself after the video starts playing, using the browser's own YouTube session. The bot-check
  message now ends "then tap Try again". Cookies from other sites' requests no longer replace
  the session kept for the page.
- Project rules (owner decision, ADR-006, 2026-10-03): for public videos YFT may use any
  technique that works — app or device client identities, proof-of-origin tokens, bot-check
  workarounds and the user's own browser session. DRM, paid, private and age-restricted
  content stay out of scope. The separate Phase 8 and Phase 9 releases are skipped; this one
  signed release follows the last task (T19).
- YouTube lookups tell a bot check from a real sign-in. "Confirm you're not a bot" now says
  that YouTube wants to check that this is not a bot, and suggests opening the video in YFT's
  browser and letting it play for a moment. A response that offers only
  YouTube's SABR streaming fails as no downloadable media with a "SABR only" detail. Copy
  details lists every client asked (name, status, reason, formats with addresses, SABR flag)
  without addresses, cookies or visitor data.
- Home lookups use an honest desktop-class YFT user-agent and shared HTML-navigation headers,
  without borrowing a browser session. Direct/scanned media keep that identity; JSON/API calls
  and the browser's own user-agent/cookies are unchanged. A bounded public-page checker reports
  only status, host/path, size and known markers, never page bodies or signed query strings.
- Documentation: `docs/FIX_PLAN.md` plans Phases 8–10 task by task after the beta.2 phone test,
  with ready-to-paste prompts in `docs/prompts/`. The old phase prompts, the hardening audit and
  the continuity protocol were removed (they stay in Git history); `PHASE_STATUS.md` and
  `HANDOFF.md` were condensed.

## [1.0.0-beta.2] - 2026-10-03

Second beta, with the redesigned interface from the owner's design images (`docs/design/`).
Features and download behaviour are unchanged. Installs over 1.0.0-beta.1 (same key, versionCode
2). Release notes: `docs/release/1.0.0-beta.2.md`.

### Changed

- New look on every screen: Mint and Deep Teal on a light surface or the Night theme, Plus
  Jakarta Sans, rounded Material Symbols, bordered cards and pill buttons; a new app icon (Mint
  to Deep Teal squircle with a play-and-download mark) and a Deep Teal launch screen.
- Bottom bar with Home, Downloads (badge with the active downloads), Library and Settings.
- Home: "Download from any link" with the Promptbox (Paste, Use copied link, searching, found
  and error states) that checks a link for media without opening the browser; Your sites
  shortcuts (add and remove) and the two newest files under Recent.
- Browser: address pill with the lock, host and path (never the query), and a "Found on this
  page" sheet that peeks above the toolbar. DRM-protected media are no longer listed or counted;
  a note says how many were left out.
- Download as (Preview) is a sheet over the page: small preview player, Video/Audio, qualities
  named "1080p · Full HD" with exact or estimated sizes, a details button, Wi-Fi only and the
  size on the Download button.
- Downloads: All/Active/Queued/Done/Failed filters with counts, speed and time left while the
  screen is open, status chips with the reason, Retry or Remove, a menu on each card, Completed
  today and Earlier, and a storage pill with the free space.
- Library: two-column grid with each file's own frame, length and "720p · 96 MB", search,
  sort, a ⋯ menu (Play, Open with…, Share, Delete), a mini player for audio above the bottom bar
  and full-screen video.
- Settings regrouped into Appearance, Downloads, Privacy and About, with a System/Light/Dark
  control, a 1–4 stepper for downloads at the same time and choice lists for location and
  quality; About and a separate Licenses page.

### Accessibility

- Every control has a TalkBack name and a touch target of at least 48dp, checked by an
  automated audit on every screen in both themes; text up to 200% wraps instead of clipping.

### Known issues

- Everything listed for 1.0.0-beta.1 below still applies.
- Playback in the Library and Downloads stops when YFT leaves the screen (no background
  playback service).
- Speed and time left are measured only while the Downloads screen is open.
- Remaining differences from the design images are listed in `docs/design/DESIGN-NOTES.md`.

### Testing status

- Automated: JVM, Robolectric and fixture tests, Android lint, the minified release build and
  signature verification in the release workflow; Robolectric renders of every screen (light,
  Night, 130% and 200% text) compared with the design images. Results: `docs/TEST_MATRIX.md`.
- Not yet run on a phone or emulator, as for beta.1, plus the new icon, launch screen, Night
  theme and the update from beta.1.

## [1.0.0-beta.1] - 2026-10-02

First beta for sideload testing. Runs on Android 7.0 (API 24) or newer and targets Android 15
(API 35). Distributed only through GitHub. Release notes: `docs/release/1.0.0-beta.1.md`.

### Added

- Paste a link on Home, or open any HTTPS page in a hardened in-app browser with address bar,
  back/forward, reload/stop and progress.
- Generic media detection from downloads, page sources and observed requests: direct files
  (MP4, WebM, audio), HLS and DASH; `blob:` players are resolved to their real HTTP(S) source.
- Detected Media list and a media-found sheet; Preview shows real video/audio variants with
  resolution, frame rate, codec, bitrate, duration and exact, estimated or unknown size.
- Downloads for direct, HLS and DASH media with pause, resume, retry, a foreground service and
  recovery after a restart; finished files go to `Download/YFT` on Android 10+ and to app
  storage on older versions.
- Site adapters for TikTok, Facebook and Vimeo, and for single YouTube videos (progressive MP4,
  usually up to 360p, plus M4A audio) by owner decision (ADR-005). Unsupported sites fall back
  to generic detection.
- Library to play, open, share and delete finished files (deletion asks first).
- Settings: default quality, download location, Wi-Fi only, mobile-data confirmation,
  concurrent downloads (1–4), light/dark theme, clear browsing data and clear download history.
- Free-space check before downloads of known size, a "Waiting for Wi-Fi"/"No connection"
  banner, and one cleanup pass per launch for leftover partial files.
- About screen with version, scope, privacy promises and third-party license texts.
- Original YFT launcher icon (adaptive with a themed monochrome layer, plus Android 7.x PNGs)
  and launch screen.
- Release tooling: signing from environment variables or an untracked `keystore.properties`,
  `scripts/release-prep.sh`, `scripts/verify-release-apk.sh` (signature, certificate,
  alignment, upgrade compatibility, SHA-256), `scripts/device-smoke-test.sh` and a draft-only
  release workflow.

### Privacy and security

- No ads, analytics, accounts or cloud backup; app data is excluded from backup and device
  transfer.
- HTTPS only (no cleartext traffic). Browser context such as cookies is sent only to the same
  origin, and credentials are dropped on cross-origin redirects.
- Candidate links stay in memory; logs never contain cookies, tokens or signed links; download
  notifications hide titles on a secure lock screen.
- The clipboard is read only when Paste is tapped. No DRM, payment, login or other access
  control is bypassed.

### Known issues

- YouTube: progressive MP4 (usually up to 360p) plus one M4A audio stream only. Without PO
  tokens some videos fail with HTTP 403, some networks get bot checks, and the player-script
  solver and client profiles need regular maintenance.
- Separate video and audio tracks can be combined only when they are AVC/AAC in MP4/fMP4. DASH
  `SegmentBase`/SIDX and live streams are not supported.
- Choosing an arbitrary folder through the system picker (SAF) is not available yet.
- An unfinished download whose signed link expired while the app was closed needs a fresh link
  from the page; there is no in-place link refresh.
- The free-space check covers only exact sizes; estimated or unknown sizes start and fail
  cleanly if storage fills up.
- TikTok, Facebook and Vimeo support is verified against saved page fixtures; site changes can
  break it until the adapter is updated.

### Testing status

- Automated: JVM, Robolectric and fixture tests, Android lint, minified release build and the
  signing/verification pipeline (with a throwaway key). Exact results are in
  `docs/PHASE_STATUS.md` and `docs/TEST_MATRIX.md`.
- Not yet run on a phone or emulator: WebView rendering, YouTube on a real network, Media3
  playback, muxing, the foreground service, MediaStore publication, Library sharing, Wi-Fi
  switching, notification permission, and fresh-install/upgrade of the signed APK.
