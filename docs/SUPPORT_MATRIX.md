# Support matrix

What YFT can find, show and save, per site and per feature. It describes the code on this branch
(1.0.2 is the newest release). Feature names are the ones in
[GLOSSARY.md](GLOSSARY.md). How the parts work is in [ARCHITECTURE.md](ARCHITECTURE.md). How they
are tested is in [TESTING.md](TESTING.md).

Status values:

- **Supported:** main does it in every build, with automated tests.
- **Backup:** the Backup finder (Master) does it only after main finds no video. In the release it
  runs while Settings → Backup finder is on (the default).
- **Owner test builds only:** in owner test APKs such as the census APK. Never in the release or
  the previews.
- **Not a file:** YFT sees it but does not save it as it is.
- **Not supported:** YFT does not do this.

YFT never signs in to a site. The user's own browser session is sent only to that site's hosts.

## Sites

| Site | Status | Notes |
| --- | --- | --- |
| YouTube adapter | Supported | Single videos: `watch?v=`, `/shorts/`, `/embed/`, `/live/`, `/v/` and `youtu.be/`, also on `m.`, `music.` and `youtube-nocookie.com`. Channels, playlists and search are not read. YouTube's visionOS client is asked first; a complete answer is the whole lookup. Else the watch page, visionOS again with the page's visitor data, the embedded player, the page's own client (with the user's session and a PoToken), YouTube's mobile site and the Android app, in that order. Rows: 144p–1080p AVC merged with the AAC sound into MP4; 1440p and 2160p VP9 merged with Opus into WebM (Android 10 and newer); Audio is the AAC track (M4A or MP3). Each media request asks for at most 10 MiB. The YouTube ejs solver computes the signature and `n` values. A private, removed, age-restricted, region-blocked or DRM answer ends the lookup; nothing is bypassed. On a bot check, play the video in YFT's browser and tap Try again. Expect breakage when YouTube changes its player or clients. |
| Facebook adapter | Supported | `watch/?v=`, `video.php?v=`, `/{page}/videos/{id}`, `/reel/{id}`, `fb.watch`, share links (`/share/v/`, `/share/r/`, `/share/p/`) and posts (`story.php`, `permalink.php`, page and group posts), on `www.`, `m.`, `web.` and `mbasic.`. Rows: the HD and SD MP4 files and the page's DASH qualities merged with sound. A signed link's expiry is kept. The page read is capped at 6 MiB. |
| Instagram adapter | Supported | `/p/`, `/reel/`, `/reels/` and `/tv/` links, also under a user name, and `?img_index=N`. Asks the app API (only when YFT's browser has an Instagram session), the web GraphQL query, the post page, then the public embed. Rows: MP4 files and DASH qualities merged with sound, highest first. Signed-in-only posts say: "Instagram shows this post only to signed-in viewers. Log in to Instagram in YFT's browser, then try again." Stories and highlights are not read. |
| TikTok adapter | Supported | `tiktok.com/@user/video/{id}`, `tiktok.com/video/{id}`, `m.tiktok.com/v/{id}.html`, `vm.tiktok.com/{code}` and `vt.tiktok.com/{code}`. The TikTok page reader reads the open tab's data, then the page, then a hidden page. The phone page's and the desktop page's files are joined by height. Show check lets the user answer TikTok's check. Rows: MP4 files with sound. Public download services such as `tikwm.com` are never asked. Photo posts, private or removed posts and region blocks fail with their own reason. |
| X adapter | Supported | `x.com` and `twitter.com` (`www.`, `mobile.`, `m.`) status links, also `/video/N`. Reads X's public embed answer, with no cookie. Rows: every MP4 on `video.twimg.com`, highest first, each with sound. A GIF is a silent MP4. The HLS playlist is used only when there is no MP4. A post without its own video gives its card's videos, else a quoted post's video. |
| Vimeo adapter | Supported | `vimeo.com/{id}`, unlisted `vimeo.com/{id}/{hash}`, channel, group, album and showcase links, and `player.vimeo.com/video/{id}` with `?h=`. Reads the player configuration (its `config_url` only on `player.vimeo.com`). Rows: MP4 files with exact sizes, plus the HLS and DASH manifests. Password-protected, paid and live videos are not read. |
| VK adapter | Supported | `vk.com`, `vk.ru` and `vkvideo.ru` videos and clips, through VK's embed player (`video_ext.php`): up to 2160p, with Audio rows. Public 18+ videos the embed refuses go through vkvideo.ru's signed-out API, with an anonymous token kept in memory only. At most 3 embed requests and one 15 s deadline. Private, sign-in-only or unavailable videos say "isn't available". A messenger channel page without a video id goes to the Page video finder. |
| Other sites (Page video finder) | Supported | Finds the page's own video: tags, scripts, manifests, requests, downloads and the playing video. The Ad filter drops ads. A page with several videos opens its main video (the one playing, else the largest) with "Other videos on this page (N)". A file link the site refuses is asked again through YFT's browser, and the sheet shows only the tapped video. |
| Adult or pirate aggregators | Page video finder only | There is no adapter for them. What the user's own browser plays is still found by the Page video finder. |

## Media and downloads

| Feature | Status | Notes |
| --- | --- | --- |
| Direct MP4, WebM and audio files | Supported | Byte-range pieces of one file count as that one file. Three or more numbered `.ts` or `.aac` pieces count as one stream. |
| HTML5 `video`, `audio` and `source` tags | Supported | A read-only page script lists them. |
| Browser downloads | Supported | The WebView's download events give the type, file name, size and request. |
| HLS (`.m3u8`) | Supported | Preview and HLS download, without DRM. A separate audio playlist is downloaded too and merged into one MP4 (AVC with AAC; VP9 with AAC only at heights with no AVC sound, from Android 10). |
| DASH (`.mpd`) | Supported | Preview and DASH download, without DRM. |
| Separate video and sound tracks | Supported | Merge joins AVC and AAC into MP4. VP9 with Opus or Vorbis becomes WebM from Android 10. A track that is one whole file (as on YouTube) downloads in resumable pieces of at most 10 MiB. Other codecs are refused before the download starts. |
| MP3 conversion | Supported | MP3 320, 192 and 128 kbps from an AAC track, with LAME 3.100. Works on arm64-v8a, armeabi-v7a and x86_64; 32-bit x86 fails with a clear message. Opus, WebM, HLS and DASH sound is not converted. The LGPL notice is in About → Licenses. |
| M4A from an MP4 video | Supported | The MP4's own AAC track is copied without re-encoding. |
| Ended YouTube live (Live download) | Supported | MP4 qualities only: the pieces are joined into one file. WebM qualities say to pick an MP4 quality. Pieces not ready yet: "YouTube still preparing this live video, try later". A file up to 2 minutes short is saved with a note in Details. |
| Signed or expiring links | Supported | A known expiry fails clearly. Fresh link renewal gets a new link for an expired download and resumes it. |
| Cookie- or header-protected media | Supported | Sent again to the same origin only. Cross-origin requests get no credentials. |
| `blob:` addresses | Not a file | The real HTTP(S) file or manifest behind it is found instead. |
| DRM (Widevine) | Not supported | Detected and refused. |
| Recording a live stream | Not supported | |
| Playlists and batch downloads | Not supported | |

## App, browser and backup

| Feature | Status | Notes |
| --- | --- | --- |
| Android versions | Supported | Android 7.0 (API 24) and newer. Target and compile SDK 35. |
| In-app browser | Supported | Introduces itself like Chrome on the same phone. App links (`fb://`, `intent://`, `market://`) keep the page; an `intent://` link's HTTPS fallback page opens once. A video opened inside the page is a new page. Pages a site adapter reads get their lookup 1.5 s after loading starts. |
| Download button on feeds | Supported | On YouTube, Facebook and TikTok feeds a tap finds the video on screen: the page's own video, else the link beside the playing video, else the video nearest the middle. The link is rebuilt on the site's main host without tracking values. No video on screen shows a short notice. |
| In-app update | Supported | Settings → About → Updates: Check now, and once a day in the release (off in the previews). Checks `SHA256SUMS`, the signature, the package and the version, then Android's installer asks the user. Any failure says "Nothing was installed." and offers Download in browser. |
| Signed GitHub release | Supported | GitHub only. The workflow makes a draft; publishing needs `ALLOW_RELEASE`. See [RELEASE.md](RELEASE.md). |
| Backup finder | Backup | On by default. When it is off, no lookup asks Master and the recipe file is not fetched. |
| YouTube backup route | Backup | Master answers a YouTube page only after main failed for a reason that can recover. It uses the YouTube own solver and the YouTube ladder. |
| Social ladders | Backup | Facebook, Instagram, TikTok, X and VK. Each site's steps and limits are in the glossary. |
| Browser capture | Backup | Reads the video the tab plays. If nothing plays yet: "Play the video, then tap Download again." |
| Signed recipe config | Backup | Site data from the signed recipe file, with a kill switch per step. When the file expires, the bundled recipes are used. |
| Quiet details | Supported | The release and the normal preview show one line for a failed lookup. Show details (tap the version line in About 7 times) adds Details and Copy details. |
| Instagram tab-fetch step | Owner test builds only | |
| Weak-answer fill and best-first order | Owner test builds only | See [PLAN.md](PLAN.md) for the open decision. |
| Trials, test switches, census, weekly site check, solver canary, stream check meter | Owner test builds only | See [TESTING.md](TESTING.md). |

## Home lookups

A link pasted on Home is looked up without the browser. Home uses its own honest identity,
`Mozilla/5.0 (X11; Linux x86_64) YFT/<version>`, with plain navigation headers. It does not borrow
the browser's identity or account. Only TikTok and Instagram links carry cookies: the ones YFT's
browser already has for that site, sent to that site only. TikTok media requests also get the
page's own `tiktok.com` cookies, kept in memory and sent only to the media's origin.

## Video pictures

The sheet, the found list and running downloads show the video's picture: YouTube's from the video
ID, other sites from the picture their lookup found. Pictures load over HTTPS only, with no cookies,
up to 2 MB. If a picture fails, the placeholder stays.

## Download button on site pages

On a page a site adapter reads, the floating Download button always means that page's video. It
spins while the lookup runs. A tap opens the Download sheet, which fills when the lookup ends. The
player's own files never count as more videos. A failed lookup shows its message with Try again
(none for protected videos). Details scroll, and Copy details copies the lines with each link as its
site's host only. One lookup runs per video: more taps join it. Size checks wait for the site lookup
and run only when it found nothing.

## Quality rows

A site's own file facts (codec, size, frame rate, length) make a row without a size check. A size
from bitrate × length is marked `~`; unknown inputs give no size. At most two background checks
fill in sizes; a failed check keeps the row. Download asks for the chosen file again. Only network
errors and 502, 503 or 504 are retried, after 1 s and 3 s. A dead file shows "This quality is not
available now — choose another" and starts no download.

## Slow networks

- Each lookup request gets 20 s to connect, 20 s without data and 60 s in total, redirects
  included.
- At most two retries, after 1 s and 3 s, for network errors and 502, 503 or 504 only. Sign-in
  walls, other HTTP errors and certificate failures are not retried.
- Cancel closes the connection at once and never retries.
- Home waits at most 90 s. After 10 s it shows "Slow connection — still looking…" and Cancel.
- In the browser, a failed Download tap shows "Couldn't reach <Site>." with Retry.

To check a public page from a computer, run `bash scripts/live-check.sh <public-https-url>`. It
prints the status, the final host and path, the byte count and known markers, never the page body,
query values or cookies.
