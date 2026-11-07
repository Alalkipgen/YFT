# YFT design

The UI design system and the rules the screens follow. The reference images in
[`design/reference/`](design/reference/) show sample content only: titles, pictures, sizes and
sites are placeholders.

| # | Image | Shows |
|---|---|---|
| 01 | [01-home-light.jpg](design/reference/01-home-light.jpg) | Home, light |
| 02 | [02-browser-found-media.jpg](design/reference/02-browser-found-media.jpg) | Browser, "Found on this page" |
| 03 | [03-download-as-sheet.jpg](design/reference/03-download-as-sheet.jpg) | "Download as" sheet |
| 04 | [04-downloads.jpg](design/reference/04-downloads.jpg) | Downloads |
| 05 | [05-library.jpg](design/reference/05-library.jpg) | Library with the mini player |
| 06 | [06-settings.jpg](design/reference/06-settings.jpg) | Settings |
| 07 | [07-home-dark.jpg](design/reference/07-home-dark.jpg) | Home on Night |
| 08 | [08-theme-and-icon.jpg](design/reference/08-theme-and-icon.jpg) | Icon, palette, type, components |
| 09 | [09-promptbox-states.jpg](design/reference/09-promptbox-states.jpg) | Promptbox states |

## Product rules

- No ads, no trending feed, no cleaner tools. Only media the user may save is shown; no DRM bypass.
- Touch targets are at least 48dp. Contrast stays readable in both themes.
- The launcher label is "Video Downloader"; the Home wordmark is "YFT".

## Colors

| Token | Hex | Use |
|---|---|---|
| Mint Teal | `#19C9A9` | Primary fill: buttons, selected chips and tabs, switches, progress |
| Ink | `#0F1C1E` | Main text; text and icons on Mint and on Coral |
| Deep Teal | `#007A6E` | Icons, links, text buttons (light) |
| Slate | `#5B6B6E` | Secondary text |
| Surface | `#F2F7F7` | Soft page background, tonal chips |
| Border | `#E2EAEA` | 1dp card borders and dividers; no heavy shadows |
| Coral | `#FF7452` | Count badges, Failed status |
| Night | `#0B1416` | Dark background |

Colors added for contrast: Coral Deep `#B93A1A` (error text on light; Night keeps Coral),
Mint Soft `#D2F3ED` (selected rows, light navigation pill), Surface Muted `#ECF2F2`, Outline
`#7A8A8D` / `#62777A` (3:1 outlines), Success `#178A55` / `#5BD69A` (the "found" and "completed"
check). Night surfaces: card `#121E21`, raised `#162427`, chip `#1C2A2D`, border `#22302F`, text
`#E8F1F1`, secondary text `#9DB0B2`.

- Code: `ui/theme/YftPalette.kt` (raw tokens), `ui/theme/YftColors.kt` (`YftColors`, read as
  `YftTheme.colors`, and the Material light and dark schemes).
- `YftThemeContrastTest`: every text pair is at least 4.5:1 in both themes; outlines and status
  icons at least 3:1; Ink on Mint 8.3:1, Ink on Coral 6.5:1.
- Text on Mint and Coral is Ink, never white (white is 2.1:1 on Mint and 2.7:1 on Coral).
- Material roles: light `primary` is Deep Teal, so stock text buttons stay readable;
  `primaryContainer` is Mint. Dark `primary` and `primaryContainer` are Mint. YFT components
  paint Mint through `YftTheme.colors.accent`.
- Mint is never the only sign of a state: selected chips and segments also turn bold, switches
  show a check in the thumb, progress bars have a percent label.

## Type, shapes and icons

- Font: Plus Jakarta Sans (SIL OFL 1.1), Latin subsets in `app/src/main/res/font/` (Regular,
  Medium, SemiBold, Bold). Roles in `ui/theme/YftType.kt`: screen titles `headlineMedium` 28 Bold
  (the images measure 28, not 32); sections and sheet titles `titleLarge` 22 SemiBold; body
  `bodyLarge` 16; buttons, chips and captions `labelLarge` 14 Medium.
- Shapes (`ui/theme/YftShapes.kt`): cards 20dp (Promptbox card 24dp), Promptbox field 28dp, pill
  buttons and chips, sheets with 28dp top corners, thumbnails 16dp (12dp in lists), badges 8dp.
- Icons: Material Symbols Rounded (Apache 2.0) vector drawables `res/drawable/ic_*.xml`, listed
  in `ui/theme/YftIcons.kt`, white masks tinted when drawn. The app icon is YFT's own: a Mint to
  Deep Teal squircle with a white play triangle and a down-arrow cutout.
- Site logos (YouTube, Facebook, TikTok, Instagram, X) are bundled Simple Icons (CC0) in the
  brand color, lightened on Night, at least 3:1 on their circle. Logos are never fetched.

## Components (`ui/components/`)

`YftPrimaryButton` (Mint pill, Ink text), `YftOutlinedButton`, `YftTonalButton`,
`YftTextButton`, `YftCircleButton`, `YftIconButton`, `YftFilterChip` (count bubble; also
outlined), `YftStatusChip` (Active Mint, Waiting Slate, Failed Coral), `YftMetaChip`,
`YftOutlinedChip`, `YftCountBadge` (Coral, Ink digits), `YftSegmentedControl` (also a compact
36dp form), `YftCard` (bordered), `YftScreenHeader`, `YftSectionHeader`, `YftGroupLabel` (small
grey capitals, read in normal case as a heading), `YftDivider`, `YftSheetHandle`,
`YftThumbnail` (picture or gradient tile with a length badge), `YftSwitch`, `YftProgressBar`,
`YftSeekBar` (tap or drag), `YftStepper`, `YftRadioMark`, `YftPromptbox`. Small visuals (40dp
chips, 36dp pills) sit inside a 48dp touch area. `YftQualityNames` holds the quality names.

## Navigation

Bottom bar: Home · Downloads · Library · Settings. The selected tab has a Mint pill behind the
icon (Mint Soft on light) and a bold label; Downloads has a Coral badge counting moving
downloads. Browser, Detected media, About and the player open full screen without the bar. While
audio plays, the mini player sits above the bar on every tab. Tabs have no back arrow.

## Screens

- **Home (01, 07):** logo and "No ads" chip; "Download from any link"; the Promptbox card (link
  field with the Mint arrow, Paste and Open browser); Your sites; Recent (two newest Library
  items, See all).
- **Promptbox (09):** Empty, Clipboard ("Use copied link"), Typing, Searching ("Looking for
  media…"), Found ("3 media found", View), Error ("No downloadable media on this page").
- **Browser (02):** close X, address pill, reload; the "Found on this page" sheet docks above the
  toolbar as a peek with a Coral count and opens with a light scrim; footer "Only media you're
  allowed to save is shown. No DRM."; toolbar back, forward, reload, home (YFT Home).
- **Download sheet:** one sheet per video, from Home, the found list, a feed or the browser.
- **Download as (03):** opened by the sheet's Details; the real preview player, Video/Audio
  control, quality list, Wi-Fi only switch, a Mint "Download · 96 MB" button and "Saves to
  Download/YFT" (or "Saves to app storage").
- **Downloads (04):** "Pause all"; All / Active / Queued / Done / Failed with counts; cards with
  progress and "61 of 96 MB · 2.4 MB/s · 15 s left"; "Completed today" and "Earlier"; storage
  pill "Download/YFT · 18 GB free" (opens Settings).
- **Library (05):** search and sort; All / Video / Audio; two-column grid with the file's own
  frame, length badge, "720p · 96 MB" and a ⋯ menu (Play, Open with…, Share, Delete).
- **Settings (06):** cards Appearance, Downloads, Privacy, About; footer "No ads · No tracking ·
  No account". Version opens About.
- **About and Licenses:** who YFT is, what it does and does not do; each license opens its text.

## Privacy rules in the UI

- Clipboard: "Use copied link" shows only when the clip's description says it holds text (on
  Android 12+ the system's link score is used). No preview of the text. The text is read on Use
  or Paste, and once per new clip when Home is shown, while Settings › Privacy › "Check copied
  links when YFT opens" is on (default on). Only the first http(s) link is used. YFT keeps only
  the clip's time and a hash in memory and never stores or logs the text.
- The address pill shows the host in Ink and the path in Slate, never the query or fragment
  (they can carry tokens). Tapping it edits the full address.
- Thumbnails of web videos use `RemoteThumbnailLoader`: no cookies or session, HTTPS only (also
  after redirects), `Accept: image/*`, at most 2 MB, two at a time, at most 480 px wide, memory
  cache about 8 MB, disk cache `cacheDir/thumbnails/` 20 MB. A YouTube picture comes from its
  ID (`i.ytimg.com/vi/<id>/hqdefault.jpg`). Logs name the host only.
- A started download keeps its picture in `filesDir/thumbnails/<downloadId>.jpg` (small JPEG).
- Checking a link from Home never opens the browser: a direct media or manifest link is probed,
  a supported site goes to its adapter, any other page is fetched once without cookies (YFT
  user agent, at most 5 redirects, never HTTPS to HTTP, 2 MiB, 25 s) and scanned for media.

## Download sheet rules

- When a post identifies several videos, **Select videos to download** opens a selection dialog.
  Select all and Deselect all affect the available items. Queueing takes the default quality for
  each, says that sizes may be unknown and data and storage are used, and reports each queueing
  result. An uncertain result asks the user to check Downloads before trying again.
- It opens at once and fills in. While it waits it shows what is known (page title or the link
  without its scheme, the site, YouTube's picture), "Getting qualities…", two Audio and two
  Video placeholder rows, and Download waits for a row.
- Two sections, Audio then Video. Audio: "M4A" (the original sound; a video's own AAC is copied,
  not re-encoded), then "MP3 · 320/192/128 kbps" with the chip "Slow". MP3 is never preselected.
- Video: one row per standard height of the picture's short side ("2160p · 4K", "1440p · 2K",
  "1080p · Full HD", "720p · HD", "480p", "360p", "240p", "144p"; a tie takes the lower name).
  The detail line keeps the real size, frame rate and format ("848 × 478 · 30 fps · MP4").
- At one height the row is the file with sound, a whole file before a stream, MP4 first, then
  the higher frame rate and bitrate. A file named only "HD" or "SD" keeps that word until its
  size check reads its picture. The page title is never a quality ("Quality unknown").
- Each row has one plain line, for example 720p "Clear view and quick play", M4A "Original
  sound, fastest", MP3 "Plays everywhere". The only video chip is "No sound".
- Sizes: the stated size, else bitrate × length as "~54 MB" (a merge adds its sound), else
  "Size unknown". A row never disappears for lack of a size.
- Short view: M4A, MP3 128 kbps, the default quality and the next lower one (default "Up to
  720p", which picks the highest row at or below 720p, a row with sound first). "More formats ·
  N" shows every row; "Fewer formats" folds them. Only the rows scroll.
- A failed lookup shows its reason and Try again when asking again can help. Closing the sheet
  stops Home's or the feed's lookup. Lookup details lists the lookup's lines.
- When the sheet cannot prepare a video it says why ("The site refused this video (HTTP 403)",
  a removed video, a busy site, an unreadable quality list). Details names the step, host and
  status, never a path or a query.

## Browser and found list rules

- An empty browser shows the start page "Search or enter a web address" (Search to download:
  YouTube and web search rows, "Link you copied", View sites) and creates no WebView until the
  first real address.
- DRM media is never listed or counted; a note says how many protected items were left out.
- Found rows show only what detection knows ("Auto quality" for HLS/DASH, stated size, length).
- The floating Download button (Mint, "Download video, N found") shows while the page has media
  YFT may save. On a site's video page, or a page with one video, a full-width Mint "Download"
  pill sits above the toolbar instead; it spins while the lookup runs.
- On sites without an adapter the page's own videos come first. Previews and ads follow under
  "Other videos on this page (N)".
- If the page states a video of two minutes or more while an ad plays, the sheet shows the
  page's title and picture and waits up to 6 s ("Finding the page's video…"). If only the ad
  comes, it shows "This may be an ad. Play the video for a moment, or see Other videos."
- If a page's file is gone (HTTP 403, 404, 410), the sheet shows the next real video with "The
  first file is gone — showing the next video" and a Details button listing both attempts.

## Downloads, Library and Settings rules

- Downloads cards show what a download records ("MP4", "HLS · MP4", the size), never a guessed
  height. Speed and time left are measured in memory while the screen is open.
- Status chips name the state and why: "Queued", "Waiting for Wi-Fi", "Waiting for network",
  "Failed · Link expired", "Cancelled". Failed rows offer Retry; Remove never deletes the file.
- When the network rule holds work back, one line under the filters says why ("Wi-Fi only is
  on" with a Settings link, or "No connection").
- Library and Recent read each file's own frame (a tenth of the way in, at most 10 s), length and
  height ("4K" from 2160) with `MediaMetadataRetriever`, two at a time, memory cache only. Audio
  and unreadable files show "M4A · 7 MB" on the gradient tile.
- Playback: audio in the mini player, video full screen with system bars hidden. No background
  playback; it pauses when YFT leaves the screen. Files YFT cannot play open in another app.
- Settings: "Ask before using mobile data" is greyed while Wi-Fi only is on; clearing asks first
  and lists what goes; "Download/YFT" needs Android 10 or newer.

## Accessibility

Every screen is rendered on Day and Night at 130% and 200% font size (`DesignRenderTest`,
`LargeTextRenderTest`). `AccessibilityAuditTest` checks that every control TalkBack reaches has
a name and a 48dp target. Text wraps, not clips; long titles shorten with "…" and TalkBack reads
them in full. Card menus are also TalkBack actions.

## Differences from the images

- 48dp rows make Settings, Download as and the found list taller than drawn; they scroll.
- Switches show a check in the thumb while on. Downloads shows "MP4" instead of "1080p".
- Pictures in the images are stand-ins. Bottom-bar labels shorten from about 130% font size.
