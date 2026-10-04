# YFT design notes

The owner's redesign brief, the reference images and the decisions taken while implementing it.
Reference images (sample content only — titles, thumbnails, sizes and sites are placeholders)
live in [`reference/`](reference/):

| # | File | Screen |
| --- | --- | --- |
| 01 | `01-home-light.jpg` | Home, light |
| 02 | `02-browser-found-media.jpg` | Browser with the "Found on this page" sheet |
| 03 | `03-download-as-sheet.jpg` | "Download as" sheet over the player (Preview) |
| 04 | `04-downloads.jpg` | Downloads |
| 05 | `05-library.jpg` | Library with the mini player |
| 06 | `06-settings.jpg` | Settings |
| 07 | `07-home-dark.jpg` | Home on Night |
| 08 | `08-theme-and-icon.jpg` | App icon, palette, type and component samples |
| 09 | `09-promptbox-states.jpg` | Promptbox states |

Goal: restyle the whole app to match the images while keeping every feature and all logic.

## Tokens

| Token | Hex | Use |
| --- | --- | --- |
| Mint Teal | `#19C9A9` | Primary fill: buttons, selected chips/tabs, switches, progress |
| Ink | `#0F1C1E` | Main text; text and icons on Mint and on Coral |
| Deep Teal | `#007A6E` | Icons, links, text buttons (light) |
| Slate | `#5B6B6E` | Secondary text |
| Surface | `#F2F7F7` | Soft page background, tonal chips |
| Border | `#E2EAEA` | 1dp card borders and dividers — no heavy shadows |
| Coral | `#FF7452` | Count badges, Failed status |
| Night | `#0B1416` | Dark background |

Code: `ui/theme/YftPalette.kt` (raw tokens), `ui/theme/YftColors.kt` (semantic `YftColors`,
read as `YftTheme.colors`, plus the Material light/dark schemes).

Derived colors (not in the brief, added for contrast):

- **Coral Deep `#B93A1A`** — error text on light (Coral itself is 2.7:1 on white). Night keeps
  Coral for text (7.0:1).
- **Mint Soft `#D2F3ED`** — tinted selected rows and the light navigation indicator.
- **Surface Muted `#ECF2F2`** — Material's muted surface roles; Deep Teal stays ≥ 4.5:1 on it.
- **Outline `#7A8A8D`** (light) / `#62777A` (dark) — 3:1 non-text outlines.
- **Success `#178A55`** / `#5BD69A` — the "found" and "completed" check.
- Night surfaces: card `#121E21`, raised `#162427`, chip `#1C2A2D`, border `#22302F`, text
  `#E8F1F1`, secondary text `#9DB0B2`.

Rules enforced by `YftThemeContrastTest`: every text pair ≥ 4.5:1 in both themes, outlines and
status icons ≥ 3:1, Ink on Mint (8.3:1) and Ink on Coral (6.5:1). White is never used on Mint
(2.1:1) or Coral (2.7:1).

Material roles: light `primary` is Deep Teal (so any stock text button or focused label stays
readable) and `primaryContainer` is Mint; dark `primary` and `primaryContainer` are Mint. YFT
components paint Mint explicitly through `YftTheme.colors.accent`.

Mint on light is a fill only and never the sole carrier of meaning: selected chips and segments
also turn bold, switches show a check in the thumb, progress bars have a percentage label.

## Type

Plus Jakarta Sans (SIL OFL 1.1), bundled as Latin subsets in `app/src/main/res/font/`
(Regular, Medium, SemiBold, Bold — about 40 KB each). Roles (`ui/theme/YftType.kt`):

| Brief | Material role | Use |
| --- | --- | --- |
| Display 32 Bold | `headlineMedium` 28 Bold | Screen titles, Home headline (28 as measured) |
| Title 22 SemiBold | `titleLarge` | Sections ("Your sites", "Recent"), sheet titles |
| Body 16 Regular | `bodyLarge` | Body text, fields |
| Label 14 Medium | `labelLarge` | Buttons, chips, captions |

## Shapes

Cards 20dp (24dp for the Promptbox card), Promptbox field 28dp, buttons and chips are pills,
bottom sheets have 28dp top corners, thumbnails 16dp (12dp in lists), duration badges 8dp
(`ui/theme/YftShapes.kt`).

## Icons

Material Symbols Rounded (Apache License 2.0) Android vector drawables in
`app/src/main/res/drawable/ic_*.xml`, listed in `ui/theme/YftIcons.kt`. They are white masks
tinted at draw time. The app icon (image 08) is YFT's own artwork: a Mint → Deep Teal
squircle with a white play triangle and a down-arrow cutout.

## Components (`ui/components/`)

`YftPrimaryButton` (Mint pill, Ink text), `YftOutlinedButton` ("Pause all"), `YftTonalButton`
(Paste, Open browser), `YftTextButton` (Edit, See all, Retry), `YftCircleButton` (Promptbox
arrow, pause/play), `YftIconButton`, `YftFilterChip` (with count bubble), `YftStatusChip`
(Active Mint / Waiting Slate / Failed Coral), `YftMetaChip` (MP4 · 1080p · 186 MB),
`YftOutlinedChip` ("No ads"), `YftCountBadge` (Coral, Ink digits), `YftSegmentedControl`,
`YftCard` (bordered), `YftScreenHeader`, `YftSectionHeader`, `YftGroupLabel`, `YftDivider`,
`YftSheetHandle`, `YftThumbnail` (image or gradient tile with duration badge), `YftSwitch`,
`YftProgressBar`, `YftSeekBar` (Mint line with an optional thumb, seeks on tap or drag),
`YftStepper`, `YftRadioMark` and `YftPromptbox`. `YftFilterChip` also comes outlined, as the
Library draws it. `YftSegmentedControl` has a compact form for a row's end (a 36dp bordered
track with a Mint pill that fills it, inside 48dp touch areas), `YftStepper` draws its grey pill
inside its two 48dp buttons, and `YftGroupLabel` is drawn in small grey capitals but read out in
normal case as a heading.

Touch targets are at least 48dp; small visuals (40dp chips, 36dp compact pills) sit inside a
48dp touch area.

## Navigation

Bottom bar with Home · Downloads · Library · Settings. The selected tab has a Mint pill behind
the icon (Mint Soft on light, Mint on Night) and a bold label; Downloads carries a Coral badge
with the number of active downloads. Browser, Detected media, About and the video player open
as full screens without the bottom bar; Download as (Preview) rises as a sheet over the screen
that opened it. While audio plays, the mini player sits right above the bottom bar on every tab.

## Screens

- **Home (01, 07):** logo + "No ads" outlined shield chip; headline "Download from any link";
  subtitle "Paste a link or open a site. YFT finds the media you can save."; the Promptbox card
  (link field with the Mint arrow, Paste and Open browser chips); "Your sites" (letter avatars,
  dashed Add circle, Edit); "Recent" (two latest Library items, See all → Library).
- **Promptbox (09):** Empty, Clipboard ("Use copied link" + Use), Typing (Mint outline, clear
  X), Searching ("Looking for media…", spinner, linear progress), Found (check, "3 media
  found", View), Error (warning, "No downloadable media on this page", Open in browser).
- **Browser (02):** close X, address pill (lock + URL), reload; "Found on this page" sheet with a
  Coral count, items with thumbnail, format chips and a Mint Preview pill; footer "Only media
  you're allowed to save is shown. No DRM."; bottom toolbar back / forward / reload / home.
  Before a page is opened, a Compose start page shows "Link you copied" and the saved Your
  sites instead of a native WebView. Paste reads only on tap; there is no clipboard preview.
- **Download as (03):** handle, "Download as", thumbnail with duration, title, "site · Video +
  audio", Video/Audio segmented control, quality list with sizes (selected row tinted), Wi-Fi
  only switch, "Download · 96 MB" Mint button, caption "Saves to Download/YFT".
- **Video you copied (12, no image):** a sheet over Home after a lookup found one video:
  placeholder thumbnail, title and length; Music "M4A · Fast"; Video Fast (≤ 480p) and High
  (≤ 720p) with real labels and sizes; More formats; one Mint Download button.
- **Downloads (04):** title + "Pause all"; All / Active / Queued / Done (+ Failed) filters with
  counts; cards with thumbnail, title, format, percentage, progress, "61 of 96 MB · 2.4 MB/s ·
  15 s left" and a Mint pause button; Waiting for Wi-Fi and "Failed · reason" chips with Retry;
  "Completed today"; storage pill "Download/YFT · 18 GB free" (tap → Settings).
- **Library (05):** title + search and sort; All / Video / Audio; two-column grid with the
  file's own frame, duration badges, "720p · 96 MB" and a ⋯ menu (Play, Open with…, Share,
  Delete); mini player above the bottom bar; videos play full screen.
- **Settings (06):** grouped bordered cards under APPEARANCE / DOWNLOADS / PRIVACY / ABOUT,
  rows with leading Deep Teal icons, inset dividers, values in Ink with grey chevrons, a
  compact System / Light / Dark control, switches, a − 3 + stepper; footer "No ads · No
  tracking · No account". Version opens About, Licenses its own page.
- **About and Licenses (no image):** the Settings cards and back arrow; About tells who YFT is
  (icon, name, version), what it does and does not do, and its privacy rules; Licenses lists
  bundled code and libraries, and each row opens its full license text.

Product rules: no ads, no trending feed, no cleaner tools; only media the user is allowed to
save is shown, no DRM bypass; 48dp touch targets; readable contrast.

## Decisions taken during implementation

1. **Clipboard privacy.** YFT never reads the clipboard by itself. The "Use copied link" row
   appears only when the clip *description* says it holds text (on Android 12+ the system's
   URL-confidence score is used when available), and it shows no preview of the copied text.
   The text is read only when the user taps **Use** or **Paste**, through
   `HomeLinks.fromClipboard`.
2. **Your sites** is a list stored on the device (DataStore), preloaded with YouTube
   (m.youtube.com), Facebook (m.facebook.com) and TikTok (www.tiktok.com) since Phase 8 (T09).
   Add asks for a name and an HTTPS address; Edit mode removes sites; tapping a site opens it in
   the Browser. YouTube, Facebook, TikTok, Instagram and X show their bundled Simple Icons logo
   (CC0) in the brand colour (lightened in Night, black marks use the Night text colour, all at
   least 3:1 on the circle); other sites keep their letter. Logos are never fetched. A list saved
   by beta.1/beta.2 moves once (`home_sites_defaults_version` = 2): the old defaults are removed,
   the new ones come first (a site of the same brand the user added takes its place), the user's
   own sites stay and nothing exceeds 12; a list without the old defaults, or empty, stays as is.
3. **Thumbnails.** Saved files use local thumbnails read with `MediaMetadataRetriever`, the
   one reader that works for both shared and app-private files and also gives the length and
   picture size (see 13). Remote thumbnails are shown only for
   `thumbnailUrl`s that detection already found, fetched with the existing hardened OkHttp
   client (HTTPS only, size-capped, memory cache) — no new image library. Everything else uses
   the gradient placeholder tile with a video or music glyph.
4. **Text on Coral and Mint is Ink**, never white.
5. The launcher label stays "Video Downloader" (the documented working name); the Home
   wordmark shows "YFT" as in the images.
6. **Checking a link from Home.** The Promptbox looks for media without opening the browser:
   a direct media or manifest link is probed, a page a site adapter supports goes through that
   adapter, and any other page is fetched once *without* cookies or the browser session (YFT
   user agent, at most 5 redirects, never HTTPS → HTTP, 2 MiB of markup, 25 s overall). The
   markup is scanned for `<video>`/`<audio>`/`<source>`, `og:video`/`og:audio`,
   `twitter:player:stream`, JSON-LD `contentUrl` and plain media addresses inside scripts.
   Results go to the same memory-only Detected media list the browser fills, so **View** opens
   it. Pages that need sign-in or build their player with scripts end in "No downloadable media
   on this page" with **Open in browser**, which opens the same link in the full browser.
7. **Paste** fills the field and waits, so the link can be checked first; **Use** on the
   clipboard row fills the field and searches at once, as the Clipboard state implies.
8. **Recent** shows the two newest Library items with their own frame (or the gradient tile),
   length badge and "720p · 96 MB", read from the file itself (see 13); a file that cannot be
   read, and audio, show "format · size" ("M4A · 7 MB"), so no resolution is ever invented.
9. **Home polish after comparing renders with `01`, `07` and `09`.** Glyphs inside the
   Promptbox and the tonal chips use a new `icon` color (Deep Teal on light, soft grey on
   Night); the link glyph is tilted 45° and the globe is the outlined Material Symbols
   `language`, as drawn. The idle field outline is Deep Teal instead of Ink (the focused Typing
   state stays Mint 2dp), the dashed Add circle uses the grey outline role, Night site letters
   are a paler Mint, and sizes drop a trailing ".0" ("7 MB"). The field's inner padding is a
   little tighter so the whole placeholder fits at 360dp.
10. **Browser and "Found on this page" (`02`).** DRM-protected candidates are never listed or
    counted: the sheet, the Found media screen and the Home "N media found" count only media
    YFT may save, and a short note says how many protected items were left out (a page with
    nothing else shows a notice instead of the sheet, and Home reads "Protected media (DRM)
    can't be saved"). The sheet docks above the toolbar as a peek ("Found on this page" + Coral
    count) so the page stays usable; tapping or dragging it up opens the list with a light
    scrim, as drawn, and tapping the scrim, dragging down or Back closes it. Rows show only
    what detection knows — format, "Auto quality" for HLS/DASH, size and length when the page
    reports them — so "1080p" and "128 kbps" appear later in Download as, once the variants are
    resolved. The address pill shows the host in Ink and the path in Slate but never the query
    or fragment, which can carry tokens; tapping it edits the full address with everything
    selected. The reload button appears in both the pill and the toolbar, as drawn, and the
    toolbar's house returns to YFT Home. The Found media screen reuses the same rows.
11. **Download as (`03`).** Preview opens as a modal sheet over the page that chose it: a
    navigation dialog destination holding Material's bottom sheet, so Back, dragging down and
    tapping the dimmed page close it, and its ViewModel and player are released with it. As
    drawn there is no close X; the empty and error states carry a Close button. The thumbnail
    spot is the real preview player (176dp, 16:9) with YFT's own controls: a play button and
    the length ("4:12") while paused, elapsed / total and a Mint seek line (tap or drag, and a
    progress action for TalkBack) while playing. Qualities run highest first and are named from
    the resolved height ("1080p · Full HD", "720p · HD", "480p", "360p · Data saver", 60 fps as
    "720p60"); audio rows read "128 kbps · English"; rows that would read the same add their
    bitrate. A size appears only when known — exact as "96 MB", estimated as "~96 MB" — and the
    Download button repeats it. Variants the device cannot play stay listed but disabled with
    "Unsupported codec", and Audio is disabled when the page has no separate audio. "Download
    over Wi-Fi only" is the same setting as in Settings, so switching it here changes it
    everywhere. The caption says where the file goes: "Saves to Download/YFT", or "Saves to app
    storage" on Android 9 and older or when Settings picks app storage. The info button beside
    "Quality" shows the chosen variant's details (resolution, fps, codec, bitrate, duration,
    size, format, language) with unknowns spelled out, and after queueing "View downloads" opens
    the Downloads tab. Rows keep 48dp touch targets, so the sheet is a little taller than the
    image; it never grows past 48dp below the status bar and scrolls on short screens.
12. **Downloads (`04`).** Filters are All / Active / Queued / Done plus Failed (the row
    scrolls sideways when it does not fit); Active, Queued and Failed show their counts. Active
    also lists paused downloads, while the Downloads tab badge still counts only moving ones.
    A download does not record its resolution, so the line under the title shows what is known
    — "MP4", "HLS · MP4", and for finished or waiting files the size ("M4A · 12 MB") — instead
    of the image's "1080p". Speed and time left ("61 of 96 MB · 2.4 MB/s · 15 s left") are
    measured in memory from the progress updates while the screen is open (a smoothed average;
    nothing is stored), and are left out until there is enough to measure. Status chips name
    the state and why: "Queued", "Waiting for Wi-Fi" (or "Waiting for network"), "Failed ·
    Link expired", "Link expired", "Cancelled"; failed rows offer Retry, expired-link and
    cancelled rows offer Remove (which removes the entry, never the file). Tapping a card opens
    its menu (Pause, Resume, Retry, Cancel download, Remove from list, Open), and TalkBack gets
    the same choices as custom actions. Finished downloads sit under "Completed today" and
    "Earlier"; their play button plays the file in the app like the Library does (audio in the
    mini player, video full screen) and their menu adds Play and Open with…, which hands the
    file to another app. The storage pill docks above the bottom bar and reads "Download/YFT ·
    18 GB free" (sizes follow the app-wide rule, so not "18.2 GB"); tapping it opens Settings.
    When the network rule holds queued work back, one compact line under the filters says why
    — "Wi-Fi only is on" with a Settings link, or "No connection" — because the Wi-Fi-only
    setting applies to every download at once (the image's running-next-to-waiting mix cannot
    happen otherwise); it is not shown when nothing waits. As drawn, failed cards show only the
    title and the Coral chip, and status chips are small (24dp) grey or Coral pills. When the
    detail line does not fit a narrow card the speed is dropped first, keeping the time left.
    Screen titles measure 28sp in the images (not the brief's 32), so `YftScreenHeader` uses
    `headlineMedium` 28 Bold. Finished downloads show the file's own frame and, for video, its
    picture size ("720p · 96 MB", read from the file as in 13); downloads still in progress
    keep the gradient tile. As a top-level tab the screen has no back arrow.
13. **Library (`05`).** Saved files show what they say about themselves: a frame a tenth of
    the way in (at most 10 s), or an audio file's cover art, the length badge, and
    "720p · 96 MB" from the picture's short side ("4K" from 2160, "8K" from 4320). Audio, and
    files that cannot be read, show "M4A · 7 MB" on the gradient tile. Details are read with
    `MediaMetadataRetriever`, two files at a time, and kept only in a memory cache sized to the
    pictures it holds — no disk cache and no image library. Home's Recent cards and finished
    Downloads use the same reader. The filter chips are outlined as drawn in `05` (Mint Soft
    when selected), where the Downloads chips are filled as in `04`. The magnifier opens a
    search field under the title that matches names as you type; the sort button offers Newest
    first (the default), Oldest first, Name and Largest first. Tapping a tile plays it: audio in
    the mini player above the bottom bar, which stays on every tab while it plays (Pause or
    Play, a seek line with a thumb, and X, which stops it), and video full screen with the
    system bars hidden (tap to pause, a seek line, X or Back closes and stops it). Files YFT
    cannot play open in another app, and a file that fails to play says so and suggests Open
    with…. The ⋯ menu offers Play, Open with…, Share and Delete (after a confirmation), and
    TalkBack gets the same choices as actions on the tile. As drawn, the tile that is playing
    has no badge (the mini player says what plays; TalkBack hears "Playing"), and audio tiles
    use the beamed-notes glyph from the image. There is no background playback
    service, so playback pauses when YFT leaves the screen (not when it rotates). The list
    re-reads itself when a download finishes and whenever the app comes back, so there is no
    refresh button; as a top-level tab it has no back arrow.
14. **Settings, About and Licenses (`06`).** Settings keeps every setting it had, regrouped as
    drawn: Appearance (Theme), Downloads (Save files to, Download over Wi-Fi only, Ask before
    using mobile data, Downloads at the same time, Preferred quality), Privacy (Clear browsing
    data, Clear download history) and About (Version, Licenses). Rows are 48dp tall rather
    than the image's tighter rows, so the page scrolls on small phones and the footer sits
    under the last card. "Save files to" and "Preferred quality" show their value and open a
    short list that applies the tapped choice (Cancel leaves it); on Android 9 and older the
    Download/YFT choice is greyed with "Needs Android 10 or newer". The concurrency stepper
    replaces the old 1–4 chips (same range, TalkBack hears "3 at the same time"). While Wi-Fi
    only is on, "Ask before using mobile data" is greyed with "Not needed while Wi-Fi only is
    on" but keeps its own value, because no download uses mobile data then; the image shows both
    switches on. Clearing still asks first: browsing data now lists what goes (cookies, site
    storage, the cache, saved sign-ins and the found media list), and Clear download history
    is greyed with "No finished downloads in the list" when there is nothing to remove. Version
    shows the app's version with a chevron because it opens About; Licenses opens its own
    page. Settings is a tab, so it has no back arrow. About uses the same grouped cards — who
    YFT is with its version, What YFT does, What YFT does not do, Privacy and a Licenses row —
    and the license list moved to Licenses: Bundled code and Libraries cards whose rows show
    name, version, license and use, and open the full license text on tap.
15. **Night, large text and accessibility.** Every screen is rendered on Day and Night and at
    130% and 200% font size (`DesignRenderTest`, `LargeTextRenderTest`), and
    `AccessibilityAuditTest` checks every screen in both themes: each control TalkBack can reach
    has a name, and each tap target is at least 48dp (the area Compose adds around small
    controls, stopped where a neighbouring control starts); on Night at 200% every label is
    still there. The audit found two problems, both fixed: the Browser address pill's text layer
    was a second, unnamed tap target on top of the address field (TalkBack now reaches only the
    field), and the Library ⋯ button's touch area reached into the next tile (it now ends at the
    grid gap). At large sizes text wraps rather than clips; long titles and the bottom-bar labels
    shorten with "…", and TalkBack still reads them in full. Icons and a text component nothing
    used any more were removed, and the old phase placeholder screen moved to the tests.

16. **Browser start page (Phase 8, T03).** An empty browser never composes a WebView.
    "Link you copied" uses Home's description-only clipboard hint and link extraction;
    Paste/Use copied link reads the text only after a tap, never stores or previews it,
    and normalizes it with the browser's HTTPS policy. Your sites observes the same saved
    repository as Home, including an intentionally empty list, and reuses its tiles.
    The first valid navigation creates the WebView; a remembered latch retains it through
    navigation, progress/error updates and recomposition. A recreated screen loads the
    current URL retained in its ViewModel; this is not native-history persistence.
    The opaque top bar draws above the clipped page container, with top/safe insets once.
    The idle field hides only its text ink under the styled host/path, not its accessibility
    node. At 200% text the found heading wraps before its reserved count/chevron rather than
    squeezing the badge out of the row. `browser-start` intentionally replaces
    `browser-empty`; all other existing tags stay.

17. **Video you copied (Phase 9, T12).** A Home lookup that found one video (every savable
    candidate is a whole file sharing one title) opens the sheet and View reopens it; several
    videos, unlabelled pairs and HLS/DASH keep the Found list. Rows come from the extractor's
    " — label": Fast is the highest at or below 480p, High the highest above 480p up to 720p,
    HD/SD rank as 720/480 without inventing a height, and the lowest is offered as Video when
    every height is above 720p. The default quality preselects High (Fast for 480p/Lowest).
    Download resolves the candidate, keeping `audioCompanion`, and uses the Download as queue
    rules (Wi-Fi only, the mobile data question). More formats opens the Found list, or Download
    as for a single file.

## Remaining differences from the images

- Rows keep 48dp touch targets, so Settings, Download as and the "Found on this page" list are
  taller than drawn and scroll on small phones; at 360dp "Downloads at the same time" wraps.
- Settings: while Wi-Fi only is on, "Ask before using mobile data" is greyed (the image shows
  both switches on); Version has a chevron because it opens About.
- Switches show a check in the white thumb while on — `08` marks the thumb "ON", `06` draws it
  plain — so on and off do not depend on colour alone.
- Downloads shows what a download records ("MP4", "HLS · MP4") instead of "1080p" while it
  runs, and speed and time left only while the screen is open (see 12).
- Thumbnails and posters in the renders are drawn stand-ins; the app shows the file's own
  frame, the page's thumbnail or the gradient tile (see 3 and 13).
- Bottom-bar labels shorten with "…" from about 130% font size; the images show only the
  default size.
