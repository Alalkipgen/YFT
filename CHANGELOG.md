# Changelog

All notable changes to Video Downloader (YFT) are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). `yft.versionCode` in `gradle.properties` rises by one
for every APK given to users, because Android refuses to install a lower one.

## [Unreleased]

### Added

- TikTok lists every height its pages give (P47): the phone page's (or the tab's) file and the
  desktop page's files of other heights are joined, so a video can list 720p and 480p; the
  desktop page is also asked when one height comes in two codecs.

### Fixed

- Download as names a phone video after its short side (P47): 720 × 1280 is "720p · HD", not
  "1280p · Full HD".

### Added (P46)

- TikTok in the browser lists every quality (P46): when the video's data in the tab opens one
  file, YFT also reads TikTok's desktop page and lists its qualities (as Home does).
- TikTok's public posts that its phone page calls private (P46): YFT asks TikTok's desktop page
  and its hidden page too, then a public download service (only the post's address, never a
  cookie). Details name TikTok's status and each step.
- Show check (P46): when TikTok asks YFT's hidden page for a check, the browser's notice offers
  **Show check** — TikTok's page for the user to answer — and Done looks again. YFT never
  answers a check itself.

- Browser reads (P45): when a site refuses YFT's request of a link a browser page names
  (HTTP 403, 410, 412 or 452–499, such as 474) at the file check, the list of qualities or a
  quality's playlist, YFT asks the same link once more through Android's WebView — a hidden
  blank page of the page's origin and the page's own `fetch`, with the tab's agent — and uses
  its answer; HLS downloads ask a refused playlist the same way. The sheet's Details add
  "Request: …" and "Browser check: …".

- TikTok from TikTok's own page (P40): in YFT's browser, Download on a TikTok video or on For
  You takes the video's data from the page being watched — TikTok's own page data and the
  answers TikTok's own feed got (kept by a small script that never changes TikTok's requests) —
  so a video that plays can be downloaded whatever TikTok answers YFT's own requests. Its files
  are checked with the tab's TikTok cookies.
- Home's TikTok links (P40): Home's lookup carries the TikTok cookies YFT's browser already has,
  and when TikTok's page cannot be read YFT opens the video's TikTok page out of sight (desktop
  Chrome, no pictures or video, at most 15 s, one at a time) and takes the post from it. A check
  that needs a person says "TikTok wants a check. Open the video in YFT's browser, then tap
  Download."; YFT never answers checks.
- The browser never dead-ends on TikTok (P39): when TikTok's page cannot be read, the sheet
  offers the file TikTok's player is playing ("TikTok's page could not be read — showing the
  file its player is playing.").
- Delete file (P42): a finished download's menu offers "Delete file" beside "Remove from list";
  after "Delete this file?" it deletes the saved file (Download/YFT, a chosen folder or app
  storage, asking Android's permission when needed) and the row, and the Library follows.
  The Library's delete uses the same path and treats a file that is already gone as deleted.
- Background downloads and merges (P34): downloads, merges, MP3 conversions and saves keep
  going while YFT is in the background — the download service holds a partial wake lock
  (renewed with a 10-minute timeout) while any task runs and a Wi-Fi lock while bytes are
  downloaded, declares `dataSync` (plus `mediaProcessing` on Android 15 while merging,
  converting or saving), handles Android 15's `onTimeout` with "Android paused downloads after
  6 hours. Open YFT to resume.", and a refused start keeps the downloads queued with a notice
  instead of crashing.
- Speed in the notification (P34): one download shows its title and
  "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left"; several show "Downloading 3 videos · 45%",
  "2.4 MB/s · 1 min left" and one line each; merges and saves keep P27's stages; at most one
  update a second, from the same speed the Downloads cards show.
- "Downloaded · <title>" and "Download failed · <title> — <reason>" notices on a new
  "Finished downloads" channel (P34).
- Downloads cards (P34): "Turn on notifications to see download progress outside YFT" when
  notifications are off, and the battery card ("Downloads and merges may stop when YFT is in the
  background…", Allow, Not now, HyperOS steps on Xiaomi, Redmi and POCO), shown again with "Your
  phone paused YFT in the background for 1 min 40 s." after a freeze; Settings › Downloads ›
  Background downloads (Allowed / Limited).
- Faster merge for long videos (P35): an MP4 video track and its MP4 or M4A sound are joined by
  copying their samples in large blocks into the final MP4 (stream copy) instead of passing
  every sample through Android's MediaMuxer. On the CI emulator a 1-hour-sized merge (265,410
  samples) takes 1.0–1.8 s instead of 15–21 s, and P27's 20-minute input 0.25–0.5 s instead of
  3.7–6.5 s. The new file is read back with Android's MediaExtractor before it counts. Today's
  MediaMuxer way stays as the safety net and runs once when the stream copy cannot be used or
  its check fails (encrypted tracks, HEVC or AV1, two tracks in one file, broken boxes); WebM
  merges (VP9/Opus, 2K/4K) keep it.
- Fresh links instead of HTTP 410 (P37): on other sites (no adapter), when the page's link
  answers HTTP 410 (or 401, 403, 404), the sheet looks for a fresh link of the same video by
  itself: the address the page's player asked for, then the page's newest copy, then the page
  read again quietly (at most twice, with the tab's browser identity and cookies, no cache),
  and only then the page's next video ("The first link is gone — using a fresh link"). When
  nothing fresh comes, "Reload page and try again" reloads the tab once without its cache and
  opens the sheet again with the new link. Details say where each link came from, how old it
  is and whether its expiry passed — never the address.

- Browser history: the browser's menu (⋯ in its toolbar) › History lists the pages you opened
  by Today, Yesterday and Earlier, with a search box; a tap opens a page, a row's menu deletes
  it, and Clear history empties the list. The start page shows the last six pages under Recent.
  Only HTTPS pages are kept, without tracking parameters, for 90 days and at most 5,000 pages.
  Settings › Browser › Save browser history (on by default) and Clear browser history; Clear
  browsing data clears the history too. Database version 6 (migration 5 → 6 keeps every
  download).
- Pop-up and ad-redirect blocking in the browser: a page can no longer open new windows to
  other sites, send the tab to another site by itself after it opened, or send you to one of
  about 20 pop-up and redirect ad networks YFT lists (also after a tap); their scripts are not
  loaded. Taps on normal links, the same site, sign-in redirects and pages you type or pick
  still open. A notice says "Pop-up blocked" or "Blocked a redirect to …" (the site) for
  4 seconds, with Open to go there anyway. Settings › Browser › Block pop-ups and ad redirects
  (on by default).
- Settings › Browser › Search engine: Google (default), DuckDuckGo or Bing.
- Other sites, the next video when one fails: when the sheet cannot prepare the video of a page
  without an adapter because its file is gone (HTTP 403, 404 or 410, or an address YFT can't
  fetch) and the page has another video that is not an ad or a preview, it prepares that one
  once, by itself, with "The first file is gone — showing the next video"; its Details list
  both attempts. Try again asks for the page's current files instead of the same dead address.
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
- Download button on feeds: on YouTube, Facebook and TikTok the browser's Download button shows
  even on a feed. Tapping it finds the video on screen (the one playing, else the one in the
  middle), looks it up and opens its Download sheet; with no video on screen it says so.
- YouTube 2K and 4K: when a video has 1440p or 2160p, the sheet offers "1440p · 2K" and
  "2160p · 4K". YouTube has no AVC above 1080p, so these are its VP9 video merged on the phone
  with the Opus sound into one `.webm` (Android 10 and later). A row this phone has no decoder
  for at that size still downloads and says "May not play on this phone".
- Preview APK for phone tests: the **Preview APK (test key)** workflow builds the release code as
  "YFT Preview" (`com.alal.yft.preview`, version `…-preview.<run>`), signed with a test key made
  in the job (never the release key), checked by `verify-release-apk.sh --package` and uploaded
  as `yft-preview-apk` with its SHA-256. It installs next to the release app.

### Changed

- The Download sheet is the tapped video's only (P45): no "Other videos on this page" row and
  no "Next video" step; only a proven ad gives way, once, to the page's own video behind it
  ("Page's video" in Details). The browser's and Detected media's lists leave proven ads out.
- Links a page names in its markup or scripts are asked like the page's player in Chromium
  (P45): the tab's agent instead of OkHttp's, `Origin` and the page origin as `Referer` for
  another origin; what the browser itself sent always stands.
- TikTok reads every answer it gives (P39): YFT reads TikTok's phone page
  (`webapp.reflow.video.detail`) and desktop page (`webapp.video-detail`), finds the data script
  by its `id`, reads any `__DEFAULT_SCOPE__` key holding the post and entity-encoded data, and
  skips another post's data. When the phone page fails or lists fewer than 2 qualities, the
  desktop page is asked with desktop Chrome's identity (the WebView's Chrome version, never
  "YFT").
- Only TikTok files that open are offered (P39): each quality's file is checked with a one-byte
  request carrying TikTok's cookies and Referer (at most 8 checks, 3 at a time): exact sizes,
  a refused address moves to the next one, a quality none opens is left out; labels 1080p,
  720p, 540p with "H.265"; the watermarked file only when no other file opens ("With TikTok
  watermark").
- Every TikTok failure explains itself (P39): the sheet's Details (and Home's) list the page
  agent, HTTP status, size, page kind, data key, JSON read, post id, qualities, file checks and
  the error class — hosts only. A link that lands on TikTok's home page says "This TikTok link
  does not open a video. It may be removed or private — open it in YFT's browser to check."; a
  check page is a bot check; "changed its page format. Falling back to generic detection" is
  gone: an unknown page shape says "<Site>'s page could not be read. Tap Details to see why, or
  Try again."
- Every TikTok step in Details (P40): the tab's data, the page read, the hidden page and what
  each gave.
- YouTube downloads move from the first seconds (P41): DASH tracks count bytes as they are
  written (at most 4 updates a second, never backwards), ranges start as soon as one ends
  instead of in batches, a whole-file track starts with a 1 MiB range (`FAST_START`, 4 ranges at
  once on YouTube's media hosts) and takes its length from that range or the address instead of
  a separate probe; merged videos show both tracks' bytes and speed in Downloads and the
  notification. A checkpoint saved before resumes with its old layout. The log and a failure's
  Details show the start times ("start: plan · length · first byte · first progress").
- The page's video, never the ad (P43, FIX_ADD_PLAN item 7): on a site without an adapter the
  Download sheet takes a file for the page's video only when the page's player names it or its
  length matches the length the page states (within 5 s or 5 %) or the failed video's; a file of
  unknown length is measured before its rows are shown. An ad network's file (YFT's own short
  list, now with more video-ad networks under any suffix), a file fetched right after an ad break
  (Google IMA's and DoubleClick's ad requests, a VAST query, a pre-roll or `/ads/` request, or an
  answer that is a VAST or VMAP document) and a short file on a long page are skipped at every
  step: the first choice, the page's newest link, the player's link, the page read again and the
  next video. The sheet says "That was an ad — showing the page's video"; Details tell why
  ("skipped: 0:30 ad (short)", "length 10:03 matches the page (10:05)"). When nothing passes no ad
  is offered: "Only an ad was found, not the page's video." with Reload page and try again, which
  looks for the page's length, not the ad. Proven ads are not counted as other videos. G8: STRICT
  (the default); LENIENT keeps the browser's first choice as before.
- A failed quality lookup's Details end with "Error: <class>" (e.g. `SocketTimeoutException`),
  the error's class name only, never its message or address (P39 hand-off; the resolver's catch
  blocks by C on the owner's override).
- TikTok (P36): when TikTok gives the phone version of a video page (no quality list), YFT
  asks the same page once more as a desktop browser for its qualities; else the phone page's
  video is the one quality. Video requests keep the browser's cookies but take TikTok's fresh
  cookies from the page answer and the identity that fetched that page.
- Merges (P35) keep every sample's time as the tracks give it (MediaMuxer moved the start of a
  video with B-frames by about 59 ms); today's way reads each sample's time and flags once and
  reports progress at most every 250 ms; "Merging audio and video · N%" moves on with the bytes
  copied; the merge's log line says how it merged, each phase's time and the CPU time.
- Speeds read "850 KB/s" below 1,024 KB/s and "1.2 MB/s" from there (P34).
- A row a page stated (for example a player setup's "720p") is no longer offered once its file
  check says the link is gone; a link gone only at Download gets a fresh link that downloads
  by itself (P37).

- YouTube merge without a silent 99%: a video with separate picture and sound shows "Merging
  audio and video · 45%", then "Saving to Download/YFT · 80%" (or app storage, or the chosen
  folder) on the Downloads card and in the notification, and the bar keeps moving. On Android
  8.0 and later the merge writes straight into the file in Download/YFT (or app storage, or a
  chosen folder that allows it) with no second copy, about half the storage reads and writes of
  before; Android 7.x and other folders still merge in app storage and copy, now with a 1 MiB
  buffer, and a direct merge that fails before its first sample tries that way once. Each merge
  logs the time of each step; a failed merge's details include them.
- Other sites, the page's video instead of the ad before it: on a site without an adapter the
  browser and Home read the page's stated length, title and picture (meta tags and JSON-LD
  only) and the files its own player is set up with (JW Player, video.js, KVS, quality lists).
  When the page states a video of two minutes or more, Download while the pre-roll ad plays
  opens the page's video with the page's title and picture. A file under half the stated
  length, a file from the ad networks of free video sites and the file a frame fetches within
  6 s after asking another site for a VAST or VMAP ad break are listed under "Other videos on
  this page". With only the ad so far the sheet shows "Finding the page's video…" for up to 6 s
  and switches by itself; when nothing else comes it shows the ad with "This may be an ad."
- Browser search: words typed in the address bar or on the start page search Google (was
  DuckDuckGo); the start page's row says "Search Google for “…”".
- One sheet for every site (P25): YouTube, Facebook and other sites give the same Download
  sheet — Audio "M4A" (the original sound) and "MP3 · 128 kbps", Video the Default quality and
  the next lower one, More formats with every row. Video rows are named by height ("1080p · Full
  HD", "720p · HD", "480p", "360p"); a file Facebook names only HD or SD takes its height's name
  in place once its picture is read. Each row has a one-line description ("Clear view and quick
  play", "Original sound, fastest", "Plays everywhere") and a size, an estimate ("~54 MB") or
  "Size unknown". The M4A kept from a video's sound is no longer marked "Slow".
- Audio from more videos (P25): the sound of an MP4 whose codecs the site does not state (other
  sites, Facebook's HD/SD) is offered as M4A and MP3. The found list names a file's quality and
  size the way the sheet does.
- Download before the qualities arrive: while the sheet still says "Getting qualities…",
  Download can be tapped. It takes the Default quality (the first Video row, e.g. "720p") or
  "M4A" when that Audio row is picked, says "Starts when ready…" and starts as soon as the rows
  come — the Default quality, else the nearest lower one, else the nearest higher one — with a
  note such as "Downloading 480p — 720p not available". A failed lookup or closing the sheet
  drops it; the mobile-data question, Wi-Fi only and the storage check apply as before.
- One lookup per video: a video looked up minutes ago (Home's link, the browser's page or feed,
  a reopened sheet) opens its qualities at once instead of asking the site again. Answers are
  kept in memory only, for at most 10 minutes or until the video's links expire, up to 20
  videos; Try again always asks the site, failures are never kept, a download whose link gets
  HTTP 403 or 410 makes the next lookup ask again, and Clear browsing data clears them.
- The download sheet opens at once: a pasted YouTube, Facebook, TikTok or Vimeo link, the
  browser's Download buttons on a site's video page and a feed's video on screen open the sheet
  straight away with the link (or the page title), "Getting qualities…" and placeholder rows;
  the lookup fills it in. A failed lookup shows its message with Try again in the sheet, and
  closing the sheet early stops Home's or the feed's lookup. Other links are checked first, as
  before.
- Real video pictures: the download sheet (16:9 header), the found list and running downloads
  show the video's own picture instead of the placeholder — YouTube's from its ID, other sites'
  from their lookup. Loaded over HTTPS without cookies (at most 2 MB, two at a time, cached up
  to 20 MB); a started download keeps a small copy until its record is deleted, which Downloads
  and the Library show until the file's own frame is read. Failures keep the placeholder.
- Wide Download button: on a site's video page and on a page with one video, a full-width
  "Download" button sits under the page (above the toolbar) instead of the round one; the page
  ends above it, it spins while the lookup runs and hides in full screen, under the download
  sheet and while the keyboard is up. Feeds and pages with several videos keep the round button.
- Site video pages (YouTube, Facebook, TikTok, Vimeo): the browser's Download button always
  opens that video's download sheet, never "Found on this page". It shows a small spinner while
  the page's lookup runs; a tap opens the sheet in its loading state and fills it when that same
  lookup ends. A failed lookup shows its message with Try again in the sheet.
- Pages with several videos open the main video's sheet (the one playing, else the largest) with
  "Other videos on this page (N)", which opens the list.
- YouTube opens faster: a lookup first asks YouTube's visionOS app alone (one request of about
  17 KB, without your session). When that answer is complete it is the whole lookup, so the
  166 KB watch page is not loaded; any other answer reads the watch page as before, and private,
  age-restricted and DRM videos stay refused. 360p is now a merged video + audio row.
- Facebook reels open faster: a reel or video link is first read as a public page, without your
  session. When that page has the video, it is the whole lookup (one page instead of two);
  otherwise YFT reads the page with your session as before. Share links are unchanged.
- Compact download sheet: initially two Audio rows (M4A, MP3 128 kbps) and two Video rows
  (720p and the next lower quality by default). More formats expands the full list in place,
  once per format; Fewer formats preserves the selection. Only the rows scroll: Download,
  Details and the format toggle stay visible below them. The unset preferred quality is now
  Up to 720p instead of Highest; saved choices and every Settings option stay unchanged.
- One download sheet: View, Download and the browser's Download button all open the same
  "Download" sheet with exactly two sections: Audio (M4A, then MP3 320/192/128 kbps) and Video
  (one row per resolution — "480p", "720p · HD", "1080p · Full HD" — with the real picture,
  frame rate and size). The default quality is preselected; Details opens Download as. The
  former duplicate Music rows and Fast/High names are gone; More formats now expands
  the single full list instead of repeating it.
- One row per video: Facebook's HD, SD and DASH items, and a YouTube video's qualities, show as
  one video everywhere (Home, the found lists, the Download button count).
- Real qualities: the sheet shows a video's real resolution, frame rate and size (read from the
  stream data or the MP4 header) or the site's own "HD"/"SD", never the page title.
- Documentation: `docs/FIX_ADD_PLAN.md` plans Phase 11 (download flow like Snaptube) task by task:
  part 1 (P0–P7) after the beta.3 phone test, part 2 (P9–P19) after the owner's test of Preview
  APK #1, with one prompt per task and a generic master prompt in `docs/prompts/`. The Phases
  8–10 plan, the T01–T19 prompts and the part 1 prompts were removed (they stay in Git history).
- Documentation: after the owner's test of Preview #3, `docs/FIX_ADD_PLAN.md` plans Phase 12
  (P20–P26: saving video files, YouTube and Facebook qualities, other sites' main video, one
  sheet everywhere, the merge and Preview #4) for three agents working at once, each with its
  own branch, files and prompt (`docs/prompts/A-download-fix.md`, `B-site-qualities.md`,
  `C-generic-and-sheet.md`, `M-merge-preview4.md`). The Phase 11 prompts (P9–P19,
  `00_NEXT_TASK.md`, `MASTER_PROMPT.md`) were removed; they stay in Git history (`4db6c2b`).
- Documentation: after the owner's test of Preview #4, `docs/FIX_ADD_PLAN.md` plans Phase 13
  (P27–P33: the YouTube merge without a silent 99%, other sites' video instead of the pre-roll
  ad, the next video when one fails, Google search, browser history, pop-up and ad-redirect
  blocking, the merge and Preview #5) for three agents working at once, each with its own
  branch, files and prompt (`docs/prompts/A-merge-speed.md`, `B-generic-main.md`,
  `C-browser.md`, `M-merge-preview5.md`). The Phase 12 prompts were removed; they stay in Git
  history (`bc806f9`).
- Documentation: after the owner's test of Preview #5, `docs/FIX_ADD_PLAN.md` plans Phase 14
  (P34–P38: downloads and merges that keep going in the background with %, speed and time left
  in the notification, a stream-copy merge for long videos, TikTok Download on the For You feed,
  fresh links instead of HTTP 410 on other sites, the merge and Preview #6) for three agents
  working at once, each with its own branch, files and prompt (`docs/prompts/A-background.md`,
  `B-tiktok-fresh-links.md`, `C-fast-merge.md`, `M-merge-preview6.md`). The Phase 13 prompts were
  removed; they stay in Git history (`5a5bddb`).
- CI: pushes that change only documentation (`docs/**`, `*.md`) no longer start the checkpoint
  validation workflow.

### Fixed

- A file check that answers HEAD with any error (such as HTTP 474) is asked by its range GET
  before failing (P45), and HTTP 410, 412 and 452–499 say "The site refused this link (HTTP n).
  Play the video for a moment, then try again." instead of "no longer has this video".
- Safer site requests (P39): a cookie pair or header OkHttp refuses is left out instead of
  failing the lookup, a per-lookup cookie jar carries cookies across short-link redirects, too
  many redirects is an HTTP status, and any other error names its class and step.
- The Download sheet's file check (P39): its requests never run on the sheet's main thread (the
  cause of the "Download · 1.4 MB" row that failed without a step), any unexpected error ends as
  a failure at its step, and a file host that answers HEAD with a server error (5xx) is asked
  with a range request instead.
- TikTok's For You feed: the browser's Download button finds the video on screen again
  (Preview #5 said "No video on screen to download"); without an author the address is
  `tiktok.com/@/video/<number>`, which TikTok opens like the full one (P36).
- A 1-hour YouTube merge no longer stops while YFT is in the background (P34, P35).
- Resume and Retry on Downloads start the download service again, so a download resumed after
  Pause all no longer runs without it (P34).

- A merge without enough free space fails at once with "Insufficient storage" at the merge step
  ("Merging needs … and … is free"), before the file in Download/YFT is touched; the track
  files are deleted as soon as the merge has succeeded.
- **P20 — Video downloads save again (R1).** A fresh direct download (no checkpoint, or the
  queue's empty one) no longer reads the destination before `prepare()`. A new pending
  MediaStore row in `Download/YFT` has no file until its first "rw" open, so that read failed and
  every direct video (YouTube 360p, Facebook HD/SD, other sites' MP4s) ended at once as "Failed ·
  Storage unavailable" at 0 B; Retry failed the same way. A resume whose length cannot be read
  now drops its checkpoint and starts again at byte 0 instead of failing.
  `AndroidPublicContentStore.length()` returns null for a row without a file yet (a missing row or
  a `SecurityException` still fails as storage). HLS, DASH, merge and MP3 already prepared first.
  New `MediaStoreDownloadInstrumentedTest` checks the real MediaStore on the CI emulator.
- **P21 — Retry and failure details (R2).** A failed download says what failed and where:
  `DownloadFailure` has a `stage` (connecting, reading from the server, opening or writing the
  file, saving the finished file, merging, converting, checking) and a short `detail` (the
  error's class and message without links, addresses, host names or tokens; at most 120
  characters). Every engine sets them, and the record keeps them (`last_error_detail`, Room 5,
  migration 4 → 5). Reasons are honest: every file call is wrapped, so a file problem is
  `STORAGE_UNAVAILABLE` or `INSUFFICIENT_STORAGE` and a source problem `NETWORK` or the HTTP
  reason; a failed close after good writes now fails the download (the old engine retried it as
  a dropped connection and published the file). Retry after a storage failure, or when the
  partial file is gone or shorter than its checkpoint, discards the old file and starts again at
  byte 0 in a new one; other failures resume as before. Downloads: a failed card has "Details"
  (and "Failure details" in its menu) with the reason, stage, HTTP status, detail, download type,
  where it is saved and the app and Android versions, and "Copy details".
- P22 — YouTube: every quality (OWNER CHECK). visionOS is asked once more with the watch page's
  visitor data (client context and `X-Goog-Visitor-Id`, no page key) when its first answer
  refused the request; a refusal is never reused, a failed request is not asked again.
- P22: a lookup ends only with a separate video merged with its audio track plus that track, so
  the Android app's progressive 360p file no longer ends it before the page's own client (BotGuard
  token, player script) is collected; `ANDROID` is asked last, after the mobile site.
- P22: merged AVC + AAC rows cover 144p–1080p; one row per quality, where a merged row (with
  YouTube's sizes of both files) replaces a progressive file; Audio is `itag 140` with its size.
- P22: details name visionOS asked again, formats signed by the player script and adaptive
  formats only available through SABR; never visitor data.
- P23 — Facebook: every quality (OWNER CHECK). The AVC ladder (desktop Safari, no cookie) is
  asked when a page lists no AVC video or AVC only below another track or a whole file, on the
  final reel or `/{page}/videos/{id}/` address, else the post's permalink — never `/watch/`; a
  share link to a page with HD and SD files only now gets AVC 360p/720p and the audio track.
- P23: the public page is the whole lookup only for a reel with AVC video and an AAC track;
  `/watch/`, `video.php` and `/{page}/videos/` links read the session page first, and the
  public page's files are kept when the session page fails. Tracks of every page read merge
  without repeats; at most 2 page requests.
- P23: an HD or SD file states the picture and AVC + AAC codecs of the track it was made from
  when that track and an AAC track are listed; bitrates are the average stated in the media
  address (`bitrate`, `efg`), never the manifest's peak, so size estimates match the files.
- P23: details name each page read and the ladder ("public page added: …", "ladder: not needed
  (AVC 720)", "ladder GET 200 (N characters): added …").
- Other sites, main video (P24): on a site YFT has no adapter for, Home and the browser open the
  page's own video, not a preview or an ad around it. The browser matches the page's player by
  its length, so a stream the page builds itself (a `blob:` player) is found, and reads a
  stream's length from its list of qualities; without a match a long video beats a large file
  and the picture height beats the size. Home reads the page's own words: `og:video`, JSON-LD
  `VideoObject`s and a stream named in the page's scripts are its video, thumbnails' clips are
  previews.
- Found count (P24): "N media found", "Found on this page" and the Download button count the
  page's videos; previews, ads and the other files a page lists follow under "Other videos on
  this page (N)". Home with one video opens its sheet at once.
- Honest failures (P24): when a video cannot be prepared the sheet says why ("The site refused
  this video (HTTP 403)", a video the site no longer has, a busy site, "The site's list of
  qualities could not be read.", an address YFT can't download) instead of "The media could not
  be reached", and Details names the step, the host and the status. A page's HLS stream is read
  with the page's `Referer` and `Origin`, and its sheet shows the video's length and estimated
  sizes. A file server that answers HEAD with a web page is asked for the file itself.
- **P26 — Agent C's hand-offs (integrator).** A direct download whose HEAD lands on the file
  host's home page (`text/html`) asks the file's own address again with the range GET, as the
  sheet already did, so it no longer fails where the sheet works; another host (a playlist's CDN,
  a redirect's file host) gets the observed page `Origin` and an origin-only `Referer`, never
  cookies; an M4A or MP3 made from a video whose sound is not AAC says "Failed · Sound can't be
  saved as audio" and its Details add "This video's sound can't be saved as audio. Download the
  video instead."
- One lookup per video: Download taps during a page's lookup and a feed's link to a video that
  was already looked up join that lookup instead of asking the site again; generic size probes
  wait for the site's lookup and run only when it found nothing.
- Quality rows appear from the site's whole-file metadata immediately, before size probes.
  Unknown sizes are estimated from bitrate and length when possible. Two background probes
  fill sizes without dropping rows, moving them or changing the chosen format; a failed probe
  keeps the row. Measured DASH qualities stay in the compact view while unmeasured HD/SD
  files stay under More formats. Download checks the selected file again with bounded retries;
  an unavailable quality queues nothing and asks the user to choose another.
- Slow-network lookups: 20 s idle/60 s per-attempt limits instead of 15 s total, with two
  bounded retries after 1 s and 3 s for connection/timeouts and 502/503/504 only. Home waits
  90 s overall, shows "Slow connection — still looking…" with Cancel after 10 s, and Cancel
  closes pending requests/body reads. Background network failures no longer put a banner
  above the browser page; a focused Download failure keeps the button and offers Retry.
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
