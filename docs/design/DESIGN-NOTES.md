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
| Display 32 Bold | `headlineLarge` | Screen titles: Downloads, Library, Settings, Home headline |
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
`YftProgressBar`, `YftStepper`, `YftRadioMark` and `YftPromptbox`.

Touch targets are at least 48dp; small visuals (40dp chips, 36dp compact pills) sit inside a
48dp touch area.

## Navigation

Bottom bar with Home · Downloads · Library · Settings. The selected tab has a Mint pill behind
the icon (Mint Soft on light, Mint on Night) and a bold label; Downloads carries a Coral badge
with the number of active downloads. Browser, Download as (Preview), Detected media and About
open as full screens without the bottom bar.

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
- **Download as (03):** handle, "Download as", thumbnail with duration, title, "site · Video +
  audio", Video/Audio segmented control, quality list with sizes (selected row tinted), Wi-Fi
  only switch, "Download · 96 MB" Mint button, caption "Saves to Download/YFT".
- **Downloads (04):** title + "Pause all"; All / Active / Queued / Done filters with counts;
  cards with thumbnail, title, format, percentage, progress, "61 of 96 MB · 2.4 MB/s · 15 s
  left" and a Mint pause button; Waiting for Wi-Fi and "Failed · reason" chips with Retry;
  "Completed today"; storage pill "Download/YFT · 18.2 GB free".
- **Library (05):** title + search and sort; All / Video / Audio; two-column grid with duration
  badges and a ⋯ menu (Play, Share, Delete); mini player above the bottom bar.
- **Settings (06):** grouped bordered cards under APPEARANCE / DOWNLOADS / PRIVACY / ABOUT,
  rows with leading icons; footer "No ads · No tracking · No account".

Product rules: no ads, no trending feed, no cleaner tools; only media the user is allowed to
save is shown, no DRM bypass; 48dp touch targets; readable contrast.

## Decisions taken during implementation

1. **Clipboard privacy.** YFT never reads the clipboard by itself. The "Use copied link" row
   appears only when the clip *description* says it holds text (on Android 12+ the system's
   URL-confidence score is used when available), and it shows no preview of the copied text.
   The text is read only when the user taps **Use** or **Paste**, through
   `HomeLinks.fromClipboard`.
2. **Your sites** is a list stored on the device (DataStore), preloaded with Archive
   (archive.org), Wikimedia (commons.wikimedia.org) and NASA (images.nasa.gov). Add asks for a
   name and an HTTPS address; Edit mode removes sites; tapping a site opens it in the Browser.
3. **Thumbnails.** Saved files use local thumbnails (MediaStore `loadThumbnail`, or
   `MediaMetadataRetriever` for app-private files). Remote thumbnails are shown only for
   `thumbnailUrl`s that detection already found, fetched with the existing hardened OkHttp
   client (HTTPS only, size-capped, memory cache) — no new image library. Everything else uses
   the gradient placeholder tile with a video or music glyph.
4. **Text on Coral and Mint is Ink**, never white.
5. The launcher label stays "Video Downloader" (the documented working name); the Home
   wordmark shows "YFT" as in the images.
