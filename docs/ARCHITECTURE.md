# Architecture

How YFT is built: the modules, the way a link becomes a saved file, main and Master, the build
flags and the security rules. Feature names are as in [GLOSSARY.md](GLOSSARY.md); the GLOSSARY also
names each feature's main class.

## Technology

| Area | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 ([DESIGN.md](DESIGN.md)) |
| UI state | ViewModel + StateFlow, one-way events and state |
| Dependency injection | Hilt |
| HTTP | OkHttp |
| Database | Room |
| Preferences | DataStore |
| Preview and manifests | AndroidX Media3 |
| Active downloads | A foreground service |
| Saving | MediaStore and the Storage Access Framework |
| Toolchain | JDK 17, Gradle 8.9, Android SDK 35 (minSdk 24) |

## Modules

| Module | What it holds |
| --- | --- |
| `:app` | Screens, ViewModels, Hilt wiring, site adapter runs, Master's app side, the download service, in-app update; owner tools in `src/debug` |
| `:core-model` | Shared models: media candidates and qualities, download plans, results and errors, settings, log redaction |
| `:core-data` | Room database, DataStore preferences, the shared HTTP client |
| `:core-browser` | WebView policy and clients, page detection (DOM probes, HTML scan, the playing video, metadata probes, ad hosts and VAST tracking), the per-page candidate store and probe budget |
| `:core-media` | The Media3 preview player, HLS, DASH and MP4 parsing, the quality resolver, HLS sound pairing |
| `:core-download` | Download queue and engines (direct, DASH, HLS, live), merge and fast merge, MP3 conversion, save destination, link renewal |
| `:extractor-api` | The site extractor interface and registry, the HTTP client, player script runner and PoToken interfaces, JSON helpers |
| `:extractor-generic` | The page video finder's boundary, media URL classifier, manifest facts, candidate normalizer |
| `:extractor-sites` | Main's site adapters: `facebook`, `instagram`, `tiktok`, `vimeo`, `vk`, `x`, `youtube` |
| `:extractor-master` | Master, the backup engine, in plain Kotlin ([README](../extractor-master/README.md)) |
| `:extractor-master-android` | Master's Android parts: WebView capture, solver engines, page scripts ([README](../extractor-master-android/README.md)) |

Dependencies point one way:

```text
:app -> every module (Master through :extractor-master-android)
:core-data, :core-media -> :core-model
:core-download -> :core-data, :core-model
:core-browser -> :core-model, :extractor-generic
:extractor-api -> :core-model
:extractor-generic -> :extractor-api, :core-model
:extractor-sites -> :extractor-api, :core-model
:extractor-master -> :extractor-api, :extractor-generic   (:extractor-sites in tests only)
:extractor-master-android -> :extractor-master, :core-browser, :extractor-generic
```

Master never depends on main's adapters. Code Master shares with main is copied into its toolkit and
checked by the toolkit drift check.

### App packages

`app/src/main/java/com/alal/yft/`:

- `detection/`: runs main's adapters (`SiteAdapterCoordinator`), lookup reuse, sound pairing, the
  AV1 merge switch; `detection/master/` (Master in the app), `potoken/`, `reads/`, `script/`,
  `tiktok/`.
- `download/`: the foreground service, save folders, fresh link renewal, delete file.
- `feature/`: `about`, `browser`, `detectedmedia`, `downloads`, `home`, `library`, `preview`,
  `quickdownload` (the Download sheet), `settings`.
- `update/`: in-app update. `diagnostics/`: crash reports. `thumbnail/`. `ui/`: theme, components,
  quiet details.
- `app/src/debug/java/com/alal/yft/master/`: the owner tools (client census, site census, solver
  canary, stream check meter).

## From a link to a saved file

1. **The user gives a link.** Paste it on Home, share it to YFT, or open the page in YFT's browser.
2. **Main looks it up.**
   - A known site: `SiteAdapterCoordinator` runs main's adapter for it through the site extractor
     registry. Lookup reuse shares one answer between Home, the browser and a reopened sheet.
   - Any other page: the page video finder reads the page's own video (tags, scripts, manifests,
     the playing video) and the ad filter drops ads.
   - In the browser, `core-browser` watches the page and keeps candidates per page (at most 200 raw
     observations; metadata probes for at most 20 different addresses per page).
3. **Master may answer** if main fails or fill a weak answer (see "Main and Master").
4. **The Download sheet** shows the qualities. The row check tests a row's first bytes before it is
   shown. The user picks a row; Lookup details shows how the rows were found.
   When a site's answer identifies several post video items, All items lets the user select and
   queue them in post order at the default quality. Each becomes its own download; photos are not
   downloaded. Items without a usable video row are not offered.
5. **The download queue** plans and runs the download in the foreground service: direct, DASH, HLS
   or live. Separate video and sound tracks are merged (fast merge for long videos). Sound can be
   turned into MP3. An expired link gets a fresh link and resumes.
6. **The file is saved** to the save folder (MediaStore or a folder the user chose) and shows in
   Downloads and the Library.

## Main and Master

- **Main** is YFT's normal path: the site adapters, the TikTok page reader, the page video finder,
  the YouTube ejs solver and the YouTube PoToken maker.
- **Master** is the backup engine. It looks for rows when main fails with a reason it can recover
  from, or fills a weak Facebook, Instagram or VK browser answer, and only while Settings'
  "Backup finder" is on.
  - YouTube: the YouTube backup route (the YouTube ladder, the YouTube own solver, the Master
    YouTube module).
  - Facebook, Instagram, TikTok, X and VK: the social ladders, within the social limits.
  - Any page: browser capture, the site contract reader, shape search and the recipe reader.
- Site data that changes often (keys, paths, headers, a step's off switch) comes from the signed
  recipe config, so many site fixes need no new APK.
- Lookup details lines that start with `master:` come from Master.

## Build flags

Debug builds read the feature flags below. The release and both previews ignore them:
`releaseMaster()` in `app/build.gradle.kts` fixes their values.

| Flag (`-P`) | BuildConfig | Debug default | Release and previews | Feature |
| --- | --- | --- | --- | --- |
| `yft.masterCapture` | `MASTER_CAPTURE_ENABLED` | false | true | Backup finder, browser capture |
| `yft.ownSolver` | `OWN_SOLVER_ENABLED` | false | true | YouTube own solver |
| `yft.ownSolverNext` | `OWN_SOLVER_NEXT_ENABLED` | false | false | Own-solver ladder |
| `yft.clientLadder` | `CLIENT_LADDER_ENABLED` | false | true | YouTube ladder (needs `yft.masterCapture`) |
| `yft.ladderSkip`, `yft.ladderMutate` | `LADDER_SKIP`, `LADDER_MUTATE` | empty | empty | Ladder test switches |
| `yft.youtubeRouteB` | `YOUTUBE_ROUTE_B` | true | true | YouTube backup route (needs `yft.masterCapture`) |
| `yft.routeBTrial` | `YOUTUBE_ROUTE_B_TRIAL` | false | trial preview only | YouTube backup trial |
| `yft.socialLadder` | `SOCIAL_LADDER` | false | true | Social ladders (needs `yft.masterCapture`) |
| `yft.socialTrial` | `SOCIAL_TRIAL` | false | trial preview only | Social backup trial |
| `yft.socialFill` | `SOCIAL_FILL` | true | true | Weak-answer fill and best-first order (with the social ladders) |
| `yft.masterRecipeKey` | `MASTER_RECIPE_KEY` | empty; the committed key in a social-ladder build | the committed key | Signed recipe config |
| `yft.masterRecipeUrl` | `MASTER_RECIPE_URL` | `https://raw.githubusercontent.com/Alalkipgen/YFT/recipes/master-recipes-2.json` | the same | Signed recipe config |

Two build settings are not features: `yft.previewBuild` (the CI run number in a preview's
version) and `yft.requireReleaseSigning=true` (release prep: fail a release build without signing).

Other BuildConfig switches:

- `YOUTUBE_ADAPTER_ENABLED` (true): turns main's YouTube adapter off per build.
- `INSTAGRAM_TAB_RUNG`: the Instagram tab-fetch step; built into social-ladder debug builds (off
  until its census box is ticked), never in the release or previews.
- `AV1_MERGE_SWITCH`: the AV1 merge switch; debug builds only (off until its census box is ticked).
  In the release and previews AV1 merges stay off (`AudioVideoMuxCompatibility.AV1_MP4_ENABLED`).
- `QUIET_RELEASE`: quiet details in the release and the normal preview; the trial preview shows
  every detail.

The owner tools' launcher icons show only in a debug build with `yft.masterCapture` and
`yft.ownSolver` (the solver canary also needs `yft.ownSolverNext`).

## Build variants

| Build type | Application ID | Signing | Notes |
| --- | --- | --- | --- |
| `debug` | `com.alal.yft.debug` | The fixed owner debug key if `app/keystore/spike-debug.keystore` exists, else the default debug key | Reads the `-P` flags |
| `release` | `com.alal.yft` | The release key (or unsigned) | Shrunk; `releaseMaster(trials = false)` |
| `preview` | `com.alal.yft.preview` | A CI test key | The release code, quiet |
| `previewTrial` | `com.alal.yft.trial` | A CI test key | The release code with the trials and every detail |

Version, signing and publishing are in [RELEASE.md](RELEASE.md).

## Data and state

- `AppDatabase` (Room, version 6, exported schemas, explicit migrations, no destructive fallback):
  download records, download segments and browser history. It holds no cookie or signed address.
- Settings live in Preferences DataStore behind `SettingsRepository`.
- Lookup reuse is memory only: at most 20 videos, each kept until its links expire or 10 minutes.
  A failure is never kept; Try again always asks the site.
- The selected candidate is kept in memory only. Signed addresses, cookies and browser context
  never go into navigation routes, saved state or Room.

## Request context

Preview and download replay only the values a site needs: Cookie, Referer, User-Agent, Accept and
site-required headers that are not hop-by-hop. YFT owns the Range and If-Range headers and strips
connection headers. Browser credentials are replayed only to their own origin. Cookies, tokens and
full signed addresses are never logged.

## Preview

Media3 previews direct files and non-DRM HLS and DASH. The resolver reads real streams, sound
tracks, resolution, frame rate, codec and bitrate; sizes it cannot know stay unknown or are marked
as estimates. A video-only stream with a separate sound track plays as a merged pair. The preview
player is created lazily and released with its screen.

An HLS average-rate estimate needs that quality's finished playlist and duration. Separate sound
also needs a finished playlist of the same duration. The resolver reads at most six such playlists,
at most 256 KiB each, within one five-second deadline; unknown or unfinished cases stay unknown.
It does not sample or download media pieces just to estimate a size.

## Downloads

- The download queue runs transfers in a foreground service with a notification. Room keeps task
  and recovery state, so a restarted process resumes safely and never marks an unfinished file as
  done.
- Direct files download in resumable ranges. DASH and HLS download the chosen tracks' pieces. A
  video-only file with a sound file is planned as a merge of two tracks, each cut into resumable
  chunks of at most 10 MiB.
- HLS keeps a bounded pool of piece downloads. Each finished piece is synced and checkpointed;
  the pieces are assembled in playlist order, not in the order they finish.
- Requests to `googlevideo.com` are kept small, because larger single requests slow down.
- Merge uses Android's media APIs; long videos use fast merge (stream copy, no re-encode). There is
  no FFmpeg.

## In-app update

- Only release-key builds check. They ask
  `https://api.github.com/repos/Alalkipgen/YFT/releases?per_page=10` at most once a day when YFT
  opens, or on Check now.
- The checker talks only to `api.github.com`, `github.com`, `objects.githubusercontent.com` and
  `release-assets.githubusercontent.com` (at most 5 redirects), over HTTPS, with no token or
  cookie.
- Before installing, YFT checks the APK against the release's `SHA256SUMS`, its signature, its
  package and its version, then hands it to Android's PackageInstaller. Any failed check stops the
  update.

## Security rules

- HTTPS by default; TLS checks are never turned off.
- WebView file and content access is off unless a feature needs it; mixed content is blocked.
- JavaScript bridges are avoided, or kept narrow when needed.
- File names are cleaned against path tricks and reserved names.
- Sensitive request values are redacted from logs, crash reports and owner reports
  (`SensitiveValueRedactor`).
- DRM manifests give a clear "not supported" result.
- No remote code is ever run. The signed recipe config carries data only.

## Where to look

| Area | Where |
| --- | --- |
| Main's site adapters | `extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/<site>/` |
| YouTube ejs solver, PoToken maker, session reset | `app/…/detection/script/`, `detection/potoken/`, `feature/settings/` |
| TikTok page reader | `app/…/detection/tiktok/`, `detection/PageDataSources.kt` |
| Page video finder, ad filter | `extractor-generic`, `core-browser/…/core/browser/detection/`, `core-model/…/core/model/media/PageVideoProof.kt` |
| Adapter runs, lookup reuse, sound pairing | `app/…/detection/` |
| Download sheet | `app/…/feature/quickdownload/` |
| Master in the app (Backup finder, YouTube backup route, social ladders' app side, test switches) | `app/…/detection/master/` |
| Master engine | `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/`: `layers/`, `recipes/`, `lookup/`, `verify/`, `toolkit/`, `modules/youtube/`, `solver/`, `social/` |
| Master on Android | `extractor-master-android/…/extractor/master/android/`, `src/main/assets/` |
| Owner tools | `app/src/debug/java/com/alal/yft/master/` |
| Downloads, merge, MP3 | `core-download/…/core/download/`, `app/…/download/` |
| Preview and parsing | `core-media/…/core/media/` |
| In-app update | `app/…/update/` |
| Crash reports | `app/…/diagnostics/` |
| Theme and components | `app/…/ui/` |

`…` is the module's source root: `src/main/java/com/alal/yft/` in `app`, `core-data`,
`core-browser`, `core-media` and `core-download`; `src/main/kotlin/com/alal/yft/` in the others.
