# Glossary

Plain names for YFT's features and tools, and what the old work labels in code comments and docs
meant. Code comments and docs use the names in the first table exactly. "Lookup details" is the
download sheet's list of lines from the lookup that found the rows; "—" means none.

## Feature names

| Name | What it does (one line) | Code (main file or class) | Build flag | In Lookup details |
|---|---|---|---|---|
| YouTube adapter | Main's YouTube reader: asks YouTube's player API and lists the video's qualities | `YouTubeExtractor` (extractor-sites) | `YOUTUBE_ADAPTER_ENABLED` | — |
| Facebook adapter | Main's Facebook reader: the post's page and its DASH qualities with sound | `FacebookExtractor` | — | — |
| Instagram adapter | Main's Instagram reader for reels and video posts, signed in or not | `InstagramExtractor` | — | — |
| TikTok adapter | Main's TikTok reader: every answer TikTok gives, joined, with the reason when it fails | `TikTokExtractor` | — | — |
| TikTok page reader | Reads a TikTok video from TikTok's own page: the open tab's data, then the page, then a hidden page | `TikTokPageEngine`, `PageDataSources` (app) | — | `tab data: …` |
| X adapter | Main's X (Twitter) reader: every MP4 quality of a post, with sound | `XExtractor` | — | — |
| Vimeo adapter | Main's Vimeo reader from the player config | `VimeoExtractor` | — | — |
| VK adapter | Main's VK reader: the embed answer, the API token path and the web client for 18+ public videos | `VkExtractor`, `VkWebClientProfile` | — | — |
| Page video finder | Finds the page's own video files on other sites (tags, scripts, manifests, the playing video) | `GenericExtractorBoundary`, `HtmlMediaScanner`, `FocusedVideoProbe` | — | — |
| Ad filter | Keeps the page's own video and drops ads (prerolls, VAST, ad hosts) | `PageVideoProof`, `VastAdTracker`, `AdHosts` | — | — |
| Download sheet | One sheet per video with its qualities, Download, Try again and Lookup details | `QuickDownloadScreen`, `QuickDownloadViewModel` | — | (shows the lines) |
| All items | Select and queue available video items of one post, one default-quality video per item | `QuickDownloadScreen`, `QuickDownloadViewModel`, `PostVideoItem` | — | — |
| Lookup reuse | Reuses a recent lookup's rows for the same video instead of asking again | `SiteLookupCache` | — | — |
| YouTube session reset | Settings action that forgets YouTube's remembered session and lookups | `YouTubeSessionReset` | — | — |
| YouTube ejs solver | Main's n/sig solver: runs yt-dlp's ejs scripts on the player script in a WebView | `YouTubePlayerScriptRunner`, `WebViewSolverEngine` | — | — |
| YouTube PoToken maker | Makes YouTube's proof-of-origin token in a WebView (BotGuard) | `BotGuardPoTokenProvider`, `WebViewBotGuardEngine` | — | — |
| Backup finder | Master: looks for rows only after main fails, while Settings' "Backup finder" is on | `MasterFallbackEngine`, `BrowserMasterFallback`, `BackupFinder` | `yft.masterCapture` (`MASTER_CAPTURE_ENABLED`) | `master: …` |
| Browser capture | Reads the media addresses of the user's own playback in the visible tab | `CaptureLayer`, `WebViewPlaybackCapture`, `yft-master-capture.js` | with `yft.masterCapture` | `master: one browser-assisted capture requested` |
| Playback hooks | Notes MSE/EME use in the page (fed addresses, and a stop on licence keys) | `yft-master-mse.js` | with `yft.masterCapture` | — |
| Codec steering | Asks the page's player for codecs the phone can save and merge | `CodecSteering`, `yft-master-codecs.js` | with `yft.masterCapture` | — |
| Media fingerprint | Groups captured files of one video by keyframes and length | `MediaFingerprint`, `FingerprintGroups`, `MasterMainSelection` | with `yft.masterCapture` | `master: fingerprint qualities=…` |
| Site contract reader | Asks the identified video's own public player endpoint once and reads its answer | `ContractReader`, `ContractRecipes`, `ContractLayer` | with `yft.masterCapture` | `master: <site> contract` |
| Shape search | Finds media objects in page data by their shape, anchored to the video's ID | `ShapeLayer` | with `yft.masterCapture` | — |
| Recipe reader | Reads page data with per-site recipes (key paths kept as data) | `RecipeLayer`, `PayloadRecipes` | with `yft.masterCapture` | — |
| Signed recipe config | Downloads and checks the signed recipe file, so site fixes need no new APK | `RemoteRecipes`, `RemoteRecipeConfig2`, `RemoteRecipeSignature` | `yft.masterRecipeKey`, `yft.masterRecipeUrl` | — |
| Recipe publish workflow | Signs the recipe file and puts it on the recipes branch | `master-recipes-publish.yml`, `scripts/master-recipes-publish.py`, `scripts/master-recipe-sign.sh` | — | — |
| Master toolkit | Shared readers copied from main: data scripts, balanced JSON, media walk, key table, quality ladder, probes, request and URL policy | `toolkit/` (`BalancedJson`, `AnchoredMediaWalk`, `QualityLadder`, `ProbeRounds`, `RequestPolicy`, `UrlPolicy`) | — | — |
| Toolkit drift check | Checks that the toolkit's copies still match main's originals | `scripts/master-toolkit-drift.py` | — | — |
| Master YouTube module | Master's own YouTube reader (copied files) that answers YouTube pages | `MasterYouTubeModule`, `modules/youtube/` | with `yft.masterCapture` | `master: youtube module` |
| Lookup record | Records each lookup's requests (main's and Master's), hosts that refused, and time left | `LookupRecord`, `LookupRecorder`, `LookupAsk` | — | the lookup's lines |
| Row check | Checks each offered row's first bytes before it is shown | `StreamCheck`, `SiteRowCheck`, `SocialRowCheck` | — | `master: rows checked=…; passed=…` |
| Answer judge | Sorts one site answer into a class (rows, login wall, bot check, removed …) | `AnswerJudge`, `SiteAnswerJudge`, per-site judges | — | — |
| YouTube backup route | Master answers a YouTube page only after main failed with a recoverable reason | `YouTubeRouteB` (app), `RouteBLookup`, `RouteBHttpClient` | `yft.youtubeRouteB` (`YOUTUBE_ROUTE_B`) | `master: route B after …`, `master: route B not run: …` |
| YouTube backup trial | Test switch: main's YouTube answer counts as a failure so Master answers | `RouteBTrialCheck` | `yft.routeBTrial` (`YOUTUBE_ROUTE_B_TRIAL`) | `master: route B trial: main's …` |
| YouTube ladder | Asks YouTube as several client identities in order, checks the rows, keeps health | `ClientLadder`, `ClientTable`, `ClientHealth`, `Rungs` | `yft.clientLadder` (`CLIENT_LADDER_ENABLED`) | `ladder <rung>` |
| YouTube HLS rung | The YouTube ladder's HLS step, checked on its own | `HlsLeg`, `HlsCheck` | with `yft.clientLadder` | `ladder H: …` |
| YouTube shape reader | Finds and binds moved or renamed fields in YouTube's answer, then reads them with the copied parser | `ShapeLocator`, `ShapeNormalizer`, `LocatedShapeMap` | with `yft.clientLadder` | — |
| Client version source | Learns a newer client version from a public source when YouTube retires one | `VersionSource`, `CensusSources` | with `yft.clientLadder` | — |
| Ladder test switches | The owner's test modes for the YouTube ladder: skip a rung or change the answer | `MasterLadderSwitch`, `LadderTrial` | `yft.ladderSkip`, `yft.ladderMutate` | `master: h-only: …` |
| Answer change test | Simulates YouTube answer changes (moved, renamed, decoys) against the ladder | `SimCases`, `ClientLadderSimTest`, `AnswerJudgeSimTest` | — | — |
| Client census | Debug screen "YFT client census": asks each identity on the owner's videos and saves a report | `ClientCensusActivity`, `ClientCensus` | debug builds | — |
| YouTube own solver | Master's own n/sig solver, used instead of the ejs solver | `OwnPlayerScriptRunner`, `own.solver.core.js`, `WebViewOwnSolverEngine` | `yft.ownSolver` (`OWN_SOLVER_ENABLED`) | — |
| Own-solver ladder | Tries the own solver in steps (worker, second strategy, page, live check) with a breaker and a locator cache | `OwnSolverLadderRule`, `OwnSolverBreaker`, `OwnSolverLocator`, `OwnSolverVLedger` | `yft.ownSolverNext` (`OWN_SOLVER_NEXT_ENABLED`) | — |
| Own-solver harness | Computer-side tests of the own solver: vectors, parity with ejs, player changes | `scripts/own-solver/*.mjs`, `scripts/verify-own-solver*.mjs` | — | — |
| Own-solver canary | Workflow that runs the own solver on YouTube's current player and reports | `own-solver-canary.yml`, `scripts/own-solver-canary.mjs` | — | — |
| Solver canary | Debug screen that runs the solvers on the phone and reports | `SolverCanaryActivity`, `SolverCanary` | debug builds | — |
| Master parity check | Runs the opt-in parity test on a phone and pulls its report | `scripts/canary.sh`, `MasterParityLiveTest` | — | — |
| Master YouTube canary | Runs Master's YouTube checks against live pages | `scripts/master-youtube-canary.sh` | — | — |
| Solver update | Updates the bundled ejs solver files | `scripts/update-youtube-solver.sh` | — | — |
| Solver check scripts | Check the bundled solver and PoToken files | `scripts/verify-youtube-solver.mjs`, `scripts/verify-youtube-potoken.mjs` | — | — |
| Stream check meter | Debug screen that measures the row check on the phone | `StreamCheckMeasureActivity`, `scripts/stream-check-measure.sh` | debug builds | — |
| Social ladders | Master's backup steps for Facebook, Instagram, TikTok, X and VK after main fails | `SocialLadder` (app), `SocialStage`, `LadderRungs`, `SiteHealth`, `RungsOff` | `yft.socialLadder` (`SOCIAL_LADDER`) | `master: <site> ladder`, `rungs: …` |
| Facebook ladder | Facebook's steps: the plugin player, main's pages, the tab's playback | `FacebookLadder` | with `yft.socialLadder` | `master: facebook F1 plugin player`, `… F2 main's pages`, `… F3` |
| Instagram ladder | Instagram's steps: main's answers judged, the tab-fetch step, the tab's playback | `InstagramLadder`, `InstagramAnswerJudge` | with `yft.socialLadder` | `master: instagram I2`, `master: instagram I3` |
| TikTok ladder | TikTok's steps: the embed player, main's pages, the tab's playback | `TikTokLadder`, `TikTokAnswerJudge` | with `yft.socialLadder` | `master: tiktok T1 embed player`, `… T2 main's pages`, `… T3` |
| X ladder | X's steps: main's answer replayed and judged, the tab's playback | `XLadder`, `XAnswerJudge` | with `yft.socialLadder` | `master: x X1 main's answer`, `master: x X3` |
| VK ladder | VK's steps: main's answers re-read, the value heal, the tab's playback | `VkLadder`, `VkClientSource`, `VkAnswerJudge` | with `yft.socialLadder` | `master: vk V1 main's answers`, `… V2 heal`, `… V3` |
| Social backup trial | Test switch: main's social answer counts as a failure so the ladder answers | `SocialLadder` (app), `TrialCompare` | `yft.socialTrial` (`SOCIAL_TRIAL`) | `master: facebook trial: main's …` |
| Weak-answer fill | Adds the ladder's missing rows to a weak main answer (no HD row, no sound row); main's rows stay first | `SocialFill` | `yft.socialFill` (`SOCIAL_FILL`) | `master: <site> B+` |
| Instagram tab-fetch step | Instagram step that fetches the post's data in the user's tab; its rows come best first per height | `InstagramProbe`, `InstagramProbeSwitch`, `RowPick` | `INSTAGRAM_TAB_RUNG` (off); best-first order with `yft.socialFill` | `master: instagram probe`, `master: each height's best first` |
| Social limits | Request budgets, gaps and stops for social lookups (429, bot check, cooldown) | `SocialStops`, `SocialBudget` | with `yft.socialLadder` | — |
| VK trial break | Test switch in the trial preview: main asks VK with a broken value so the heal runs | `VkTrialBreak` | trial preview only | — |
| Site census | Debug screen "YFT site census": asks each site's slots and ladder steps and saves a report | `SiteCensusActivity`, `SiteCensus`, `SiteCensusReport` | debug builds | — |
| Weekly site check | The census's weekly run: compares each site with its baseline and lists CHANGED or BREAKING | `SiteWeekly`, `SiteCensusDiff` | debug builds | report mode `weekly` |
| Promotion runs | Three passing census runs per site before a release counts that site's backup steps | `SiteCensus` (census APK); rules in `docs/TESTING.md` | debug builds | — |
| Download queue | Runs downloads and merges in the background with a notification | `DownloadQueue`, `DownloadForegroundService` | — | — |
| Direct download | Downloads one file with resume | `DirectTransferEngine` | — | — |
| DASH download | Downloads a DASH quality's pieces and joins them | `DashTransferEngine`, `DashPieceIndex` | — | — |
| HLS download | Downloads an HLS playlist and turns it into MP4 | `HlsTransferEngine`, `HlsRemux`, `TsMp4Remuxer` | — | — |
| Live download | Saves an ended YouTube live from its pieces, with an estimated size | `LivePieces`, `LiveOriginFile`, `DashCheckpointTracker` | — | — |
| Fresh link renewal | Gets a fresh link for a download whose link expired, then resumes | `SiteLinkRenewal`, `DownloadLinkRenewal`, `FreshLinks` | — | — |
| Same-file check | Decides from addresses alone whether a fresh link is the file whose link failed; a similar length never proves it | `SameFile`, `FileKey`, `SameVideo` | — | `Status: same video not confirmed` |
| Playlist read-ahead | Reads up to three HLS quality playlists at once for `~` size estimates; a read starts early only when the one-at-a-time order is sure to make it | `PlaylistReadAhead` (core-media) | — | — |
| MP3 conversion | Turns a sound track into MP3 | `Mp3Transcoder`, `LameMp3Encoder` | — | — |
| Delete file | Deletes saved files from Downloads and Library | `DownloadedFileDeleter` | — | — |
| Save folder | The folder downloads are saved to, chosen by the user | `SaveFolders`, `PublicDownloadDestination` | — | — |
| Merge | Joins a video track and a sound track into one MP4 | `AudioVideoMuxEngine` | — | — |
| Fast merge | Joins tracks by stream copy (no re-encode) for long videos | `StreamCopyAudioVideoMuxer`, `Mp4StreamCopyWriter` | — | — |
| AV1 merge switch | Test switch that lets AV1 videos merge (off) | `Av1Merges` | `AV1_MERGE_SWITCH` (off); census box "AV1 merges (test)" | — |
| Sound pairing | Gives a video row its sound from the same manifest or playlist | `DashSoundPairing`, `HlsAudioPairing` | — | — |
| In-app update | Checks GitHub for a new release, downloads it, checks it and installs it | `UpdateChecker`, `UpdateController`, `ApkCheck` | — | — |
| Zawgyi titles | Turns Zawgyi video titles into Unicode | `ZawgyiTitles` | — | — |
| Crash reports | Saves a crash report the user can copy | `CrashReportHandler`, `CrashReportStore` | — | — |
| Quiet details | Release and normal preview show fewer detail lines | `QUIET_RELEASE` users | `QUIET_RELEASE` | — |
| Release build | The signed release: main with every fix and Master on, flags fixed in the build file | `releaseMaster` (app/build.gradle.kts), `release-draft.yml` | fixed (no `-P` flag) | — |
| Release prep | Prepares and checks a release APK | `scripts/release-prep.sh`, `scripts/verify-release-apk.sh` | — | — |
| Release debug-hook check | Checks that release code never reaches debug-only hooks | `scripts/verify-release-debug-actions.py` | — | — |
| Preview APK | CI build signed with a test key, normal and trial | `preview-apk.yml` | `yft.previewBuild` | — |
| Trial preview | The preview with the trials and every detail, installed as "YFT Trial" | `preview-apk.yml` (trial) | `yft.trial` | — |
| Owner test APKs | Debug APKs with Master switches for the owner's phone tests | `master-optin-debug-apk.yml` | `yft.*` switches | — |
| Census APK | Owner test APK with the census screens | `master-optin-debug-apk.yml` | `yft.socialLadder` + `yft.masterCapture` | — |
| Shrunk build check | Builds the shrunk (R8) release and previews and checks them | `minified-check.yml`, `scripts/ci-minified-check.sh` | — | — |
| Emulator smoke test | Runs the app and its device tests on a CI emulator | `emulator-smoke.yml`, `scripts/ci-emulator-smoke.sh`, `scripts/ci-smoke-diagnostics.py` | — | — |
| README screenshots | Takes the README's app pictures on an emulator and commits them | `readme-screenshots.yml`, `scripts/readme-screenshots.sh`, `ReadmeScreenshotsTest` | — | — |
| Checkpoint check | The main CI check: build, tests, lint | `checkpoint-validation.yml`, `scripts/checkpoint.sh` | — | — |
| CI Gradle memory | Sets the runner's Gradle memory in one place | `scripts/ci-gradle-memory.sh` | — | — |
| Live site check | Checks live sites from a computer | `scripts/live-check.py`, `scripts/live-check.sh` | — | — |
| Device smoke test | Installs and smoke-tests the app on a connected phone | `scripts/device-smoke-test.sh` | — | — |
| Comment-only check | Fails unless code files changed only in their comments | `scripts/check-comment-only.py` | — | — |
| Icon generator | Makes the launcher icons | `scripts/generate-launcher-icons.py` | — | — |

## Old labels

What each work label in code comments meant. One label can mean different things in different
plans; each table is one source. A row's "Feature name now" is the name to use instead; "history
only" means the label names past work, a test round or a person and needs no feature name.

### Master plan (MASTER_KEY_PLAN.md, MASTER_KEY_PHASE1_PLAN.md, MASTER_KEY_ARCHITECTURE.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| A0 | Architecture step A: packages and layers, same behaviour | history only |
| R1 | Phase 1 row 1: base sync, parity harness and canary | Master parity check |
| R2 | Phase 1 row 2: safety (terminal bot check and DRM stop; YouTube payload off) | Backup finder |
| R3 | Phase 1 row 3: toolkit T1/T2, shape search, ID anchoring, ladder, probe and request policy | Shape search |
| R4 | Phase 1 row 4 ("C"): keyframe and length fingerprint | Media fingerprint |
| R5 | Phase 1 row 5 ("E"): codec steering | Codec steering |
| R6 | Phase 1 row 6: Master's own YouTube module (YT-1, YT-2, YT-4); "R6's chain", "R6's headers", "= R6" mean that module's normal path | Master YouTube module |
| R7 | Phase 1 row 7: generic capture with MSE/EME hooks | Playback hooks |
| R8 | Phase 1 row 8: per-site contracts (Vimeo, X, Facebook, TikTok, Instagram) | Site contract reader |
| R9 | Phase 1 row 9: signed recipe config | Signed recipe config |
| YT-1, YT-2, YT-4 | Parts of the own YouTube module (answer reader, signature gate, player-script client) | Master YouTube module |
| C, E (Phase 1 steps) | The fingerprint step and the codec steering step | Media fingerprint; Codec steering |
| L1 (layer) | Capture layer: the tab's own playback | Browser capture |
| L2 (layer) | Contract layer: page video tags and the site's public endpoint | Site contract reader |
| L3 (layer) | Shape layer: media objects found by shape | Shape search |
| L4 (layer) | Recipe layer: per-site key paths | Recipe reader |
| T1 | Toolkit: find data scripts by id or type | Master toolkit |
| T2 | Toolkit: string-aware balanced JSON reader | Master toolkit |
| T3 | Toolkit: media walk anchored to the content ID | Master toolkit |
| T4 | Toolkit: media-key table as data | Master toolkit |
| T5–T6, T11–T12 | Toolkit items named only in the docs | Master toolkit |
| T7 | Toolkit: quality ladder (best rate per size, AVC first) | Master toolkit |
| T8 | Toolkit: one-byte check rounds | Master toolkit |
| T9 | Toolkit: request policy (cookies, Referer, Origin) | Master toolkit |
| T10 | Toolkit: expiry read from the URL | Master toolkit |
| §4.1 | MASTER_KEY_PLAN section 4.1: the canary | Master parity check |

### Phase 1.1 plan (MASTER_KEY_PHASE1_1_PLAN.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| Phase 1.1 | The own n/sig solver plan | YouTube own solver |
| S1 | Harness: vectors, parity with ejs, player corpus | Own-solver harness |
| S2 | Solver core: parse, G1 unwrap, G2 normalize and candidates | YouTube own solver |
| S3 | G4 environment and the multi-try runner | YouTube own solver |
| S4 | G3 encoding and SelfCheck | YouTube own solver |
| S5 | `OwnPlayerScriptRunner` wiring behind the flag, cache, failure mapping | YouTube own solver |
| S6 | Canary and update runbook | Solver canary |
| G1 | Solver group 1: unwrap the player's wrapper | YouTube own solver |
| G2 | Solver group 2: normalize the code and find candidates | YouTube own solver |
| G3 | Solver group 3: encode the sig input once | YouTube own solver |
| G4 | Solver group 4: the sandbox environment | YouTube own solver |
| SelfCheck | The solver's own agreement and vector check | YouTube own solver |

### Phase 1.2 plan (MASTER_KEY_PHASE1_2_PLAN.md and its CONTINUE files)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| Phase 1.2 | The own solver's ladder plan | Own-solver ladder |
| N1 | Change simulator: mutation suite and baseline (ejs against own P) | Own-solver harness |
| N2 | Live check V, and measuring n on the owner's phone | Own-solver ladder |
| N3 | Strategy B: register, probe, classify | Own-solver ladder |
| N4 | Strategy E: isolated page environment | Own-solver ladder |
| N4b | Environment-dependent values and init errors by class | Own-solver ladder |
| N5 | Ladder, locator cache and flag (split into N5a and N5b) | Own-solver ladder |
| N5a | Ladder rule, shared case table, harness runners `--ladder` and `--dyncode` | Own-solver harness |
| N5b | Page-P step budget, breaker, ladder and locator cache behind `yft.ownSolverNext` | Own-solver ladder |
| N5b A.1–A.6 | N5b's follow-up items (A.6: the navigation gap) | Own-solver ladder |
| N6 | Canary workflow on the computer | Own-solver canary |
| N7, round 2 | Owner's live check and runbook; "N7 round 2": its second phone round | history only |
| N8 | Not yet: the own-solver ladder in a public release | Own-solver ladder |
| P (worker P) | The ladder's first step: the solver in a worker | Own-solver ladder |
| B (worker B) | Strategy B: register, probe, classify | Own-solver ladder |
| E (page E) | Strategy E: the solver in an isolated page | Own-solver ladder |
| V (live check V) | The live check of a solved value | Own-solver ladder |
| option 3 | N5b A.1's choice: keep B's prepared program in memory | Own-solver ladder |
| M1 | Player change: locals renamed in minifier style | Own-solver harness |
| M2 | Player change: function and arrow, declaration and expression swapped | Own-solver harness |
| M3 | Player change: another top-level wrapper form | Own-solver harness |
| M4 | Player change: the sig helper object split into functions | Own-solver harness |
| M5 | Player change: calls through fresh aliases | Own-solver harness |
| M6 | Player change: strings from a shuffled split table | Own-solver harness |
| M7 | Player change: control-flow flattening | Own-solver harness |
| M8, M8t | Player change: encode and decode reached through wrappers (M8t: wrappers first) | Own-solver harness |
| M9 | Player change: the init reads browser objects | Own-solver harness |
| M9b | Player change: environment-dependent values | Own-solver harness |
| M9c | Player change: values from the page's navigation | Own-solver harness |
| M10 | Player change: decoy functions | Own-solver harness |
| M11 | Player change: a new harmless prelude | Own-solver harness |
| #47, #54, #56, fix #54 | ejs issue numbers that named a class of player change | history only |
| plan section 1 rule 4 | Rule: the page gets the browser objects the worker lacks | Own-solver ladder |

### Phase 1.3 plan (MASTER_KEY_PHASE1_3_PLAN.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| Phase 1.3 | The YouTube client ladder plan | YouTube ladder |
| D0 | Census tool, weekly check W, version sources | Client census |
| D1 | Change simulator, parity gate, L round-trip, baseline | Answer change test |
| D2 | Client table v2 and answer judge | YouTube ladder |
| D3 | Health v2, order, budget, stop rules | YouTube ladder |
| D4 | Row checks: V, init check, H check, Z | Row check |
| D5a | visionOS rungs A (self-healed versions), S and Z | YouTube ladder |
| D5b | Own parser L: locate, bind, read | YouTube shape reader |
| D5c | H leg: HLS on its own | YouTube HLS rung |
| D6 | O rungs, memo, wiring, flag, merge rule | YouTube ladder |
| D7 | Owner's live check with L changes, weekly check, runbook; "D7 APK": an owner test APK; "D7 run N": the owner's Nth phone run | history only |
| D8 | Signed identity data over R9 (dropped) | history only |
| A (rung) | visionOS with an alternate, self-healed version | YouTube ladder |
| S (rung) | The rung that needs the player script (signature) | YouTube ladder |
| Z (rung) | The rung that fills missing sizes | YouTube ladder |
| H (rung) | The HLS rung | YouTube HLS rung |
| O (rung) | Live-only rungs | YouTube ladder |
| L (rung) | The own parser's rows | YouTube shape reader |
| V (check) | The row check | Row check |
| R0, R0+vd | Main's first visionOS ask (+vd: with visitor data) | YouTube ladder |
| W | The weekly check | Weekly site check |
| V1 | Change row: the base version gets HTTP 400 | Answer change test |
| V2 | Change row: formats carry `signatureCipher` | Answer change test |
| V3 | Change row: `contentLength` removed | Answer change test |
| V4 | Change row: adaptive is SABR-only, HLS kept | Answer change test |
| V5 | Change row: adaptive rows refused (403), HLS kept | Answer change test |
| V7 | Change row: refusal that is not final | Answer change test |
| V8 | Change row: formats under a moved key | Answer change test |
| V11 | Change row: HLS refused (403), adaptive fine | Answer change test |
| T1 | Stop row: bot check on every identity | Answer change test |
| T1b, T1c, T1d | Stop rows: bot check only for visionOS without visitor data (c: moved key, d: SABR-only) | Answer change test |
| T2 | Stop row: age check | Answer change test |
| T3 | Stop row: private, removed, geo, DRM | Answer change test |
| T4 | Stop row: a wrong `n` was served | Answer change test |
| H2 | Health row: visionOS needed S or H on the last lookup | YouTube ladder |
| F1 | Fault row: wrong or unsolved S value | Answer change test |
| F2 | Fault row: V gives unknown (timeout) | Answer change test |
| F3 | Fault row: HLS faults (403, wrong type, wrong start, wrong length) | Answer change test |
| F4 | Fault row: an answer about another video | Answer change test |
| L1 | Layout change: `streamingData` moved | Answer change test; also a Ladder test switches mode |
| L2 | Layout change: format lists renamed | Answer change test; also a Ladder test switches mode |
| L3 | Layout change: the address key renamed | Answer change test; also a Ladder test switches mode |
| L4 | Layout change: mime renamed or split | Answer change test |
| L5 | Layout change: `signatureCipher` renamed | Answer change test; also a Ladder test switches mode |
| L6 | Layout change: types changed (string and number) | Answer change test |
| L7 | Layout change: list turned into a map by itag | Answer change test; also a Ladder test switches mode |
| L9 | Layout change: hard decoy | Answer change test |
| L10 | Layout change: `playabilityStatus` moved | Answer change test |
| L11 | Layout change: final answer with format-like objects | Answer change test |
| L12 | Layout change: protection marker renamed | Answer change test |
| L13 | Layout change: `hlsManifestUrl` moved | Answer change test |
| L14 | Layout change: page signals moved | Answer change test |
| L15 | Layout change: half old layout, half new | Answer change test |
| L16 | Layout change: 10 000 unrelated objects | Answer change test |
| R0, S, A1, A2, SH (census steps) | Census ask steps: R0 (asked as R6 asks it), S (with the page's timestamp and visitor data), A1 and A2 (visionOS with other versions), SH (version acceptance: a newer valid version tried once) | Client census |
| Y1–Y6 | Version-source rows (Y1: a newer valid version; Y6: the source names other fields too) | Client version source |
| guardrail 1 | Flag: debug builds only, off by default | YouTube ladder |
| guardrail 2 | Copy main's YouTube files, never edit them | Master YouTube module |
| guardrail 3 | No token, no cookie, no account | YouTube ladder |
| guardrail 4 | No bypass; the ladder never decides a refusal | YouTube ladder |
| guardrail 5 | At most 4 identities; versions only by a strict pattern | Client version source |
| guardrail 6 | Row checks: L rows, init check, H binding | Row check |
| guardrail 7 | Budget: one page per lookup, a complete visionOS answer ends the lookup | YouTube ladder |
| guardrail 8 | Health saved on the phone | YouTube ladder |
| guardrail 9 | Never a wrong row; the merge rule | YouTube ladder |
| guardrail 10 | Spike only: no main merge, tag or release | history only |
| guardrail 11 | No background work: the census runs only after a tap | Client census |
| guardrail 12 | Mobile data only for live steps | history only |
| (က) | Owner's option (2026-10-15, §9 item 9): count the gate with a minimum sample; also (2026-10-17) the file's start bytes decide when its type names no container | YouTube HLS rung |
| (က+) | Owner's option (2026-10-15, §9 item 10): after a bot check, re-ask and use a version source | YouTube ladder |
| §9 item 3 | Owner item: A's version source | Client version source |
| §9 item 4 | Owner item: D8 dropped | history only |
| §9 item 9 | Owner item: when H goes live | YouTube HLS rung |
| §9 item 10 | Owner item: T1c and T1d | Answer change test |
| §5.1, §5.2 | Plan sections: the answer judge and the budget | YouTube ladder |
| §7 (Phase 1.3) | The runbook: each census change line names its step | Client census |
| option 2 | D5a's A check (owner, 2026-10-16) | YouTube ladder |
| item 2, item 3 (owner's order) | The owner's orders of 2026-10-16: the h-only trial (2), D5b steps 7–8 (3) | history only |
| run 2, run 3, run 4, run 5, tests 1–3 | Census and phone runs | history only |

### Phase 1.4 plan (MASTER_KEY_PHASE1_4_ROUTING_PLAN.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| Phase 1.4 | The YouTube routing plan | YouTube backup route |
| route A, route B | Route A: Master first; route B: Master only after main fails | YouTube backup route |
| R1 | Per-lookup request recorder | Lookup record |
| R2 | Master's HTTP wrapper for the YouTube module (replays main's asks) | YouTube backup route |
| R3 | The claim rule: Master answers after main's recoverable failure | YouTube backup route |
| R4 | Debug trial switch and the "routeb" APK | YouTube backup trial |
| R5 | The owner's phone proof | history only |
| R6 | Docs | history only |
| F1 | Review fix: the player-script client | YouTube backup route |
| F2 | Review fix: why route B did not run | YouTube backup route |
| F3 | Review fix: the joined lookup | YouTube backup route |
| F4 | Review fix: a failure midway (tests) | YouTube backup route |
| F5 | Review fix: main's watch page for route B (one page per lookup) | YouTube backup route |
| F6 | Review fix: what a "Link expired" download was | YouTube backup route |
| F7 | Review fix: Master's rows against main's set-aside rows | YouTube backup trial |
| F8 | Review fix: the one-byte check against the real download | Row check |
| item 1 | Plan item 1: 2K and 4K VP9 merges | Merge |
| item 2 | Plan item 2: which site's found pages still go through Master | Backup finder |
| item 7 | Plan item 7: YouTube route B | YouTube backup route |
| rule 1, rule 3, rule 4, rule 5 | Route B's rules: the cookie (1), a 429 stays (3), only asks named in the record (4), each ask new and recorded (5) | YouTube backup route |
| routing decision #7 | Master answers only after main fails | YouTube backup route |
| §8, §9, §10 | Review follow-up (§8), promotion (§9 item 4), status list (§10) | history only |
| [d7] run N, run 4b, run 8, run 10–14 | The owner's phone runs | history only |

### Social plan (MASTER_KEY_SOCIAL_PLAN.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| S0 | Step 0: the request recorder and the site census | Lookup record; Site census |
| S0a | The recorder's labels and the site table | Lookup record |
| S0b | The census engine and the one-row check | Site census |
| S0c | The debug screen "YFT site census" | Site census |
| S0d | The owner's census run | history only |
| S1 (step) | First draft's step 1 (became S1a) | history only |
| S1a | The ladder's minimum with Facebook first, behind `yft.socialLadder`; "S1a (N)": its Nth fix | Social ladders |
| S1b | After the release: more request steps per site (not built) | history only |
| S2b | TikTok's steps | TikTok ladder |
| S2c | X's steps | X ladder |
| S2d | Instagram's steps; part 1: the ladder, I2 and the tab-fetch probe; part 2: the tab-fetch step | Instagram ladder; Instagram tab-fetch step |
| S3 | Weekly check W for all sites and its runbook | Weekly site check |
| S4 | The signed config hosted (R9 live) | Signed recipe config |
| S5 | Promote a site after three phone runs (decision 26) | Promotion runs |
| S6 | B+: fill a weak main answer with the ladder's missing rows | Weak-answer fill |
| S7 | Budgets and stop rules | Social limits |
| S1 (guardrail) | Flag and parity: flag off is today; main's adapters not copied or changed | Social ladders |
| S2 (guardrail) | The user's own session only; nothing sent to a media host | Social ladders |
| S3 (guardrail) | No signing or tricks | Social ladders |
| S4 (guardrail) | No third-party services | Social ladders |
| S5 (guardrail) | Self-healed values only from a proven source | VK ladder |
| S6 (guardrail) | Row checks on every row Master offers | Row check |
| S7 (guardrail) | Budgets and stop rules | Social limits |
| S8 (guardrail) | Privacy: no IDs, links, titles or names in docs, logs or exports | history only |
| S9 (guardrail) | Data over code: endpoints and key paths are data | Recipe reader |
| S10 (guardrail) | Owner gates for each site, step and heal source | history only |
| B+ | Weak-answer fill (S6) | Weak-answer fill |
| B (adapters) | Option B: no adapter copies; main's rows stay main's | Social ladders |
| B0 | Main's baseline census run | Site census |
| L0 (layer) | Policy: Master only after main fails, final answers, walls | Backup finder |
| L1 (layer) | Site table: each site's steps and their status | Social ladders |
| L2 (layer) | Answer judge | Answer judge |
| L3 (layer) | Own parser: media objects found by shape | Shape search |
| L4 (layer) | Ladder and health | Social ladders |
| L1 (backlog) | Facebook's trial line could not compare heights | Social backup trial |
| L2 (backlog) | Instagram's step picked a lower-rate version of a height; fixed with best first per height | Instagram tab-fetch step |
| L3 (backlog) | The open read took a player's stand-in file | Site contract reader |
| F1 (rung) | Facebook: the plugin player | Facebook ladder |
| F2 (rung) | Facebook: main's pages | Facebook ladder |
| F3 (rung) | Facebook: the tab's own playback | Facebook ladder |
| I2 | Instagram: main's answers judged | Instagram ladder |
| I3 | Instagram: the tab's own playback | Instagram ladder |
| T1 (rung) | TikTok: the embed player | TikTok ladder |
| T2 (rung) | TikTok: main's pages | TikTok ladder |
| T3 (rung) | TikTok: the tab's own playback | TikTok ladder |
| X1 | X: main's answer replayed and judged | X ladder |
| X2 | X: a widget token re-ask (not built) | history only |
| X3 | X: the tab's own playback | X ladder |
| F1 (retest) | Instagram slot 5 signed out, step on: the step's rows | Instagram tab-fetch step |
| F2 (retest) | Step off: the sign-in words | Instagram tab-fetch step |
| F3, F3b (retest) | The failure sheet scrolls; a final answer offers no Try again | Download sheet |
| F4 (retest) | Signed in, the step and its proof on | Instagram tab-fetch step |
| F5 (retest) | A weekly run where every ask was walled | Weekly site check |
| F6 (retest) | Facebook link 1: both sides' heights in the trial line | Social backup trial |
| A1 | Facebook access fix in main | Facebook adapter |
| A2 | An Instagram reel's `video: null` is final (closed in main) | Instagram adapter |
| O1 | S4 session's observation: the open read took the player's stand-in file | Site contract reader |
| decision 2 | No token self-check and no X2 | X ladder |
| decision 5 | The committed recipe key | Signed recipe config |
| decision 7 | Master acts only after main fails; B+ is the one exception for weak answers | Backup finder |
| decision 8 | One lookup = main + Master, with one request record | Lookup record |
| decision 10 | Main's code and reasons stay | Social ladders |
| decision 11 | Rows of the link's item only | Social ladders |
| decision 18 | A Page post's desktop address | Facebook adapter |
| decision 19 | Instagram's tab-fetch probe; (iv): a carousel's linked item | Instagram tab-fetch step |
| decision 20 | Removed, private or gated: main's words | Social ladders |
| decision 22 | The census works as the app does, with no download service | Site census |
| decision 24 | Instagram's tab-fetch step | Instagram tab-fetch step |
| decision 26 | The promote rule: three phone runs | history only |
| decision 28 | The tab-fetch step stays off by default | Instagram tab-fetch step |
| decision 30 | The release carries main and Master with fixed flags; (i) the Backup finder switch, (iii) the trial app, (v) quiet details, (vi) the publish run | Release build |
| owner's input 2 | Same as decision 11 | Social ladders |
| task 0 | X's widget 404 is never final | X ladder |
| task 6a, task 6b | The social-trial APK (6a); the census per ladder site (6b) | Owner test APKs; Site census |
| task 7 | A Page post's desktop form | Facebook adapter |
| task 8 | W's button in the census | Weekly site check |
| task 9 | One S6 check and reader for every site | Weak-answer fill |
| task 10 | TikTok's download service in lookups and the census | TikTok adapter |
| task 12 | The trial's social lookups ask main every time | Social backup trial |
| task 13 | The tab-fetch step's own switches | Instagram tab-fetch step |
| task 14 | The lookup's tab and words follow the page | Social ladders |
| task 15 | Retest fixes F1, F2, F3b | Social ladders |
| task 16 | Retest fix F6 (heights) | Social backup trial |
| task 17 | Backlog fixes L1 and L3 | Social backup trial; Site contract reader |
| task 18 | Fix for O1 | Site contract reader |
| part 1, part 2 | S2d's two parts | Instagram ladder; Instagram tab-fetch step |
| 0.2, 0.3, 0.4 | Section 0's owner inputs (the Instagram check, the access fix, the census order) | history only |
| observation (d), (e), (f) | Phone-session observations | history only |
| slot N | A census link slot | history only |
| §3.1, §3.2 | Reusable parts | Social ladders |
| §4 | Steps table and guardrails S1–S10 | Social ladders |
| §5 | Layers L0–L4 | Social ladders |
| §6.1, §6.2, §6.3, §6.4 | Each site's signals | Answer judge |
| §7, §7.1, §7.2, §7.3, §7.4, §7.5 | Census and packs: census (7.1, 7.2), packs (7.3), retest (7.4), weekly check (7.5) | Site census |
| §8 | Decisions | history only |

### VK plans (docs/VK_PLAN.md, docs/VK_MASTER_PLAN.md, docs/VK_MASTER_CENSUS.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| VK0 | VK step 0: no token or cookie needed for the canonical page | VK adapter |
| VK1–VK4 | VK steps named only in the docs | VK adapter |
| VK5 | Path D for public 18+ videos (the web client) | VK adapter |
| path B | Ask `video_ext.php` on the embed host | VK adapter |
| path D | The web client for 18+ videos | VK adapter |
| B, B again, B2 | Ask names in the lookup record for VK's embed: the page's own host (B), the same host once more after VK's app shell (B again), the other host without a cookie (B2) | VK adapter |
| V1 (VK) | VK: main's answers re-read, no request | VK ladder |
| V2 (VK) | VK: the heal (new values, at most two requests) | VK ladder |
| V3 (VK) | VK: the tab's own playback | VK ladder |
| wave 2 | VK-Master wave 2 (agent D): VK's ladder, heal and Lookup details lines | VK ladder |
| VK value break | The trial's broken VK value | VK trial break |
| Q1–Q7 | Census questions | history only |
| §3.2a, §5.5, §7.2, §9, §11, §13 | Plan sections: value sources (3.2a), fixture hygiene (5.5, 11), census (7.2), schema-2 config (9), 18+ (13) | VK ladder; Signed recipe config |

### Track E (docs/TRACK_E_PLAN.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| Track E | Fresh links for expired downloads | Fresh link renewal |
| E0 | The study: questions and answers (Q3: the facts that name a quality; Q4: who looked the video up) | Fresh link renewal |
| E1 | A download whose link died is renewed and resumed | Fresh link renewal |
| E2 | The site adapter that named the video renews it | Fresh link renewal |
| E3 | Renewal rules per answer (VK 400 or 403) | Fresh link renewal |
| E4 | Step named only in the doc | Fresh link renewal |
| G1 (Track E) | Gap: the resume point was lost | Fresh link renewal |
| G2 (Track E) | Gap: no cleanup for a "Link expired" row | Fresh link renewal |
| G3 (Track E) | Gap: a merge kept a finished track unchecked | Merge |
| G4 (Track E) | Gap: renewal re-queued at the end | Download queue |
| G5 (Track E) | Gap: the service watched too few states | Download queue |
| §4, §4.5, §6 | Plan sections: files, lookup senders, quality facts | Fresh link renewal |

### v1.0.2 fix/add plan (docs/FIX_ADD_PLAN.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| v1.0.2 | The 1.0.2 release's fixes and additions | history only |
| item 0 | Runner's Gradle memory from one place | CI Gradle memory |
| item 1, item 2, item 3 | Owner items 1–3 (several plans number their own) | history only |
| item 4 | Another app's Share reaches YFT | Download sheet |
| item 5 | VP9 HLS video and its AAC sound merged into one MP4 | Merge |
| item 5b | Delete several files at once | Delete file |
| item 6 | Owner item 6 after run 4f: live files | Live download |
| item 11 | Page-data files admitted once | Media fingerprint |
| item 13 | A failed lookup's lines in Lookup details | Download sheet |
| item 17 | The weekly schedule: one weekday, off the hour | Weekly site check |
| item 18 | Least time between two social asks | Social limits |
| item 25 | Zawgyi titles to Unicode | Zawgyi titles |
| item 26 | AV1 merge switch | AV1 merge switch |
| item 28 | The chosen save folder | Save folder |
| item 50 | The quality each download was picked at | Download queue |
| item 51 | A post's video items ("Video 2 of 4") | Download sheet |
| item 59 | DASH sound pairing on pages without a site reader | Sound pairing |
| item 70b | An Instagram lookup that ends unread | Instagram adapter |
| F1 (v1.0.2) | Fix for test case 59 (a DASH test manifest; inline DASH readers) | DASH download |
| F2 (v1.0.2) | Second fix of that round | DASH download |
| N1–N12 | Phone checklist rows (large text, release notes, folder line, names) | history only |
| N9 | Checklist row: a long name keeps its quality | Download queue |
| N12 | Checklist row: Settings' folder line | Save folder |
| M1 (v1.0.2) | Checklist row: the Downloads folder chip | Save folder |
| M2 (v1.0.2) | Checklist row: the sheet's More after P45 | Download sheet |
| B1–B5 | Owner ideas: Download button in YouTube's page (B1), own YouTube page (B2), Facebook formats from the page (B3), site fixes without a new APK (B5) | history only; B5 became Signed recipe config |
| G1 (v1.0.2) | Agent choice: number of agents | history only |
| G2 (v1.0.2) | Agent choice: TikTok's view of YFT (Chrome agent) | TikTok adapter |
| G3 (v1.0.2) | Agent choice: TikTok cookies on Home | TikTok page reader |
| G4 (v1.0.2) | Agent choice: TikTok's watermarked file | TikTok adapter |
| G5 (v1.0.2) | Agent choice: the hidden TikTok page | TikTok page reader |
| G7 (v1.0.2) | Agent choice: confirm before Delete file | Delete file |
| G8 (v1.0.2) | Agent choice: the ad rule | Ad filter |
| R16–R39 | Review findings of prompts P34–P50 (R16, R18: P37 links; R19: P36; R20: TikTok's chain token; R22, R24: P34; R25–R31: P39; R34: P43) | the prompt's feature (see Release prompts) |
| step 1–step 9 | Steps of one prompt's plan (P24, P37, P40) | the prompt's feature |
| phase-v102-easy task 1–4 | The four tasks after R10 (task 3: live files) | Live download |
| run 4, run 4f | The owner's live runs | history only |
| §6 | The fix rounds' results | history only |

### Release prompts (docs/TEST_MATRIX.md, docs/PHASE_STATUS.md, docs/prompts/)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| P1 | Browser follows in-page navigation | Page video finder |
| P2 | Facebook and TikTok black page in the browser | Page video finder |
| P3 | One download sheet with real qualities | Download sheet |
| P4 | Facebook: every quality with sound | Facebook adapter |
| P5 | Download button on feeds | Page video finder |
| P6 | 2K and 4K | Merge |
| P7 | Preview APK with a test key | Preview APK |
| P8 | Signed beta 4 | Release build |
| P9 | Short sheet and pinned Download | Download sheet |
| P10 | Slow networks | Download queue |
| P11 | Rows never vanish | Download sheet |
| P12 | One sheet and one lookup on site pages | Download sheet |
| P13 | Wide Download button | Download sheet |
| P14 | YouTube asks visionOS first | YouTube adapter |
| P15 | Facebook public page first | Facebook adapter |
| P16 | Sheet opens at once | Download sheet |
| P17 | Reuse lookup results | Lookup reuse |
| P18 | Download before qualities arrive | Download sheet |
| P19 | Real thumbnails | Download sheet |
| P20 | Video downloads save again | Direct download |
| P21 | Retry and failure details | Download queue |
| P22 | YouTube: every quality | YouTube adapter |
| P23 | Facebook: every quality | Facebook adapter |
| P24 | Other sites: the page's videos found, ranked and counted | Page video finder |
| P25 | One sheet for four sites; heights measured later | Download sheet |
| P26, P33, P38 | Merge of the agents' branches (A → B → C) | history only |
| P27 | Faster merge into the saved file | Merge |
| P28 | The ad before the video | Ad filter |
| P29 | Try again after a fresh link | Fresh link renewal |
| P30 | The browser's start page | Page video finder |
| P31 | Browser history | history only |
| P32 | A blocked navigation or new window: the page stays | Page video finder |
| P34 | Downloads and merges go on in the background; speed in the notification | Download queue |
| P35 | Faster merge for long videos (stream copy) | Fast merge |
| P36 | TikTok: Download on the For You feed and video pages | TikTok adapter |
| P37 | Other sites: fresh links instead of HTTP 410 | Fresh link renewal |
| P39 | TikTok: read every answer TikTok gives, and say why it fails | TikTok adapter |
| P40 | TikTok from TikTok's own page | TikTok page reader |
| P41 | YouTube: progress and speed from the first seconds | DASH download |
| P42 | Downloads: Delete file | Delete file |
| P43 | Other sites: the page's video, never the ad | Ad filter |
| P44 | Merge for preview 7 | history only |
| P45 | Adult-site sheet: the tapped video only | Download sheet |
| P46 | TikTok: every quality in the browser, "private" public posts | TikTok adapter |
| P47 | TikTok: every height joined | TikTok adapter |
| P48 | X adapter: every MP4 quality with sound | X adapter |
| P49 | Instagram adapter | Instagram adapter |
| P50 | HLS video with separate sound merged | Sound pairing |
| Preview #N (#4, #5, #149, #161 …) | A numbered Preview APK build | history only |
| #12 normal, #12 trial | The two release-candidate previews of PR #12 | Preview APK; Trial preview |

### Phone rounds (docs/PHONE_TEST_NOTES.md, docs/FIX_ADD_PLAN.md §6)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| R1–R8 | v1.0.2 fix rounds: a preview tested on the phone, then fixed ("fix R3": the fix from round 3) | history only |
| R9, R10 | The last two fix rounds | history only |
| phone test 59, test N | A numbered phone test case | history only |
| V1–V12 (phone) | Videos of the D7 phone check | history only |
| B1–B8 (phone) | Phone check runs and census checks (B5: H on the R0+vd answer; B6: video 2 on Android and MWEB; B7: version acceptance; B8: the videos a run asked about) | history only; B5–B8 live on in Client census |
| run N, run #202, run 200, emulator #31, #179, smoke #27, #28 | CI or phone run numbers | history only |
| OWNER CHECK | A check the owner did on the phone | history only |

### Owner items

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| owner item N, owner item 6 | A numbered request from the owner in a session (for example item 6: live files) | history only |
| the owner's items 1 and 2 after R10 | The owner's requests after round R10 | history only |
| owner's input N | The owner's numbered answer in the social plan | history only |

### Agents (docs/prompts/, PROGRESS_PARALLEL.md)

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| A, agent A | Agent A: TikTok work (docs/prompts/A-tiktok.md), integrator of some merges, VK census | history only |
| B, agent B | Agent B: downloads work (docs/prompts/B-downloads.md) | history only |
| C, agent C | Agent C: ads work (docs/prompts/C-ads.md) | history only |
| D, agent D | Agent D: VK-Master wave 2 | history only |
| M | The merge prompt for preview 7 (docs/prompts/M-merge-preview7.md) | history only |
| Track B, Track B3 | An owner work track; B3: Reset YouTube session | YouTube session reset |
| G1–G8 (prompts README) | The agents' choices (same as the v1.0.2 G rows) | see v1.0.2 fix/add plan |
| G4 (Phase 14) | Default of the battery card in Downloads | Download queue |
| G8 (Phase 14) | How speed is written on cards and in the notification | Download queue |

### Other sources

| Label | Meaning | Feature name now (or "history only") |
|---|---|---|
| ADR-001 | Modular feature-first architecture | history only |
| ADR-002 | Layered generic media detection | Page video finder |
| ADR-003 | Separate direct and adaptive download engines | Direct download; DASH download |
| ADR-004 | Ship no YouTube adapter | history only |
| ADR-005 | Ship a YouTube adapter by owner decision | YouTube adapter |
| ADR-006, decision 1 (ADR-006) | Owner override: use whatever works for public videos; its decision 1: VK's web client | history only |
| Phase 1–Phase 15 (app) | App build phases (Phase 12: P20–P26, 13: P27–P33, 14: P34–P38, 15: P39–P44) | history only |
| Phase 1 (Master) | Master's first plan (rows R1–R9) | Backup finder |
| U0, U1 | In-app update: the plan and the check | In-app update |
| U2 | In-app update: download, SHA-256 check, install | In-app update |
| U3 | In-app update: phone test; notes never open links | In-app update |
| §4.1, §4.2, §4.3, §4.4, §4.5, §4.6, §6.1, §6.2, §10 (update) | In-app update plan sections and install steps 1–9 | In-app update |
| T09, T13, T14, T17, T18 | Owner checks: Your sites logos (T09), Search to download (T13), floating Download button (T14), merged video and audio (T17), MP3 (T18) | Download sheet; Merge; MP3 conversion |
| D1 (defect) | HLS saved as `.m3u8` | HLS download |
| D2 (defect) | Audio rows per site | Download sheet |
| D3 (defect) | Myanmar titles keep their marks | Zawgyi titles |
| D4 (defect) | Instagram and X icons | history only |
| §1, §2, §3 | Section numbers of the file named in the same comment | the section's topic |
| PR #2, PR #12, PR #13 | Pull requests: the draft spike PR (#2), the release-candidate PR (#12), Track E (#13) | history only |

### Tokens that look like labels but are not

| Token | What it is |
|---|---|
| H264, AV1, VP9, MP4, M4A, MP3, ID3, TIT2, AAC, ADTS | Codec, container or tag names |
| ES256, SHA256, MD5, CC0 | Signature, hash or licence names |
| E402, F401, SC… | Linter codes |
| FE0F, VS16 | Unicode characters |
| A3 (in `1A 45 DF A3`) | Bytes of the WebM magic |
| H2 (release notes) | A heading level |
| #1, case 0, track 312, #16729, #17642 | Ordinary numbers, code, a track ID, yt-dlp issue numbers |
| (ဒါဟာအချစ်လား) | A Burmese song title in a test |

## Old documents

The docs cleanup deleted these files; git history keeps them. "History only" means the file held
no current facts.

| Old document | Where its current facts are now |
|---|---|
| `MASTER_KEY_PLAN.md` | History only (the first Master analysis). Master today: `docs/ARCHITECTURE.md`, `extractor-master/README.md` |
| `MASTER_KEY_ARCHITECTURE.md` | `docs/ARCHITECTURE.md` (Main and Master), `extractor-master/README.md`, `extractor-master-android/README.md` |
| `MASTER_KEY_NOTES.md` | History only (solution options). The options chosen: `docs/DECISIONS.md` |
| `MASTER_KEY_PHASE1_PLAN.md` | `docs/ARCHITECTURE.md` (Master's layers, signed recipe config), `docs/DECISIONS.md` |
| `MASTER_KEY_PHASE1_1_PLAN.md` | `docs/TESTING.md` (Own-solver canary), `extractor-master-android/README.md` (YouTube own solver) |
| `MASTER_KEY_PHASE1_2_PLAN.md` | `docs/TESTING.md` (Own-solver canary), `extractor-master/README.md`, `extractor-master-android/README.md` |
| `MASTER_KEY_PHASE1_3_PLAN.md` | `docs/ARCHITECTURE.md`, `docs/TESTING.md` (Debug screens), this glossary (YouTube ladder, Client census) |
| `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` | `docs/ARCHITECTURE.md` (YouTube backup route), `docs/DECISIONS.md` |
| `MASTER_KEY_SOCIAL_PLAN.md` | `docs/TESTING.md` (Site census, Weekly site check runbook, Promotion runs), `docs/PLAN.md` (open decisions), `docs/DECISIONS.md` |
| `MASTER_KEY_PHASE1_PROMPT.md`, `MASTER_KEY_PHASE1_1_PROMPT.md`, `MASTER_KEY_PHASE1_2_PROMPT.md`, `MASTER_KEY_PHASE1_3_PROMPT.md`, `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md` | History only (agent prompts) |
| `MASTER_KEY_PHASE1_2_CONTINUE.md`, `MASTER_KEY_PHASE1_2_CONTINUE_N3.md`, `MASTER_KEY_PHASE1_2_CONTINUE_N4.md`, `MASTER_KEY_PHASE1_2_CONTINUE_N4B.md`, `MASTER_KEY_PHASE1_2_CONTINUE_N5.md`, `MASTER_KEY_PHASE1_2_CONTINUE_N5B.md`, `MASTER_KEY_PHASE1_2_CONTINUE_N5B_A4.md`, `MASTER_KEY_SOCIAL_S0_CONTINUE.md` | History only (continuation prompts) |
| `PROGRESS.md`, `PROGRESS_PARALLEL.md` | History only (work logs). Where the work stands: `docs/SESSION_STATE.md` |
| `docs/PROJECT_CONTEXT.md` | `README.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md` (Product rule) |
| `docs/PHASE_STATUS.md` | History only. Status now: `README.md` (Status), `docs/PLAN.md` |
| `docs/HANDOFF.md` | `docs/SESSION_STATE.md`, `docs/TESTING.md` |
| `docs/HANDOFF_WEBM_HONLY_104.md` | History only. The WebM merge and HLS facts: `docs/SUPPORT_MATRIX.md` |
| `docs/FIX_ADD_PLAN.md` | `docs/TESTING.md` (Phone checklist for a release), `docs/PLAN.md` (open decisions, backlog), `docs/DECISIONS.md` |
| `docs/TRACK_E_PLAN.md` | `docs/ARCHITECTURE.md` (Downloads), `docs/SUPPORT_MATRIX.md` (Signed or expiring links), `CHANGELOG.md` (1.0.2) |
| `docs/IN_APP_UPDATE_PLAN.md` | `docs/RELEASE.md` (Version code rule), `docs/ARCHITECTURE.md` (In-app update), `docs/DECISIONS.md` |
| `docs/RISKS.md`, `docs/YOUTUBE_RISK_REVIEW.md` | `docs/PLAN.md` (Risks), `docs/SUPPORT_MATRIX.md` (YouTube adapter), `docs/DECISIONS.md` (YouTube) |
| `docs/TEST_MATRIX.md`, `docs/PHONE_TEST_NOTES.md` | History only (test results). How to test now: `docs/TESTING.md` |
| `docs/VK_PLAN.md` | `docs/SUPPORT_MATRIX.md` (VK adapter), `docs/DECISIONS.md` (VK) |
| `docs/VK_MASTER_PLAN.md` | `docs/ARCHITECTURE.md`, `docs/DECISIONS.md` (VK), this glossary (VK ladder) |
| `docs/VK_MASTER_CENSUS.md` | `docs/TESTING.md` (VK census rules) |
| `docs/decisions/` (`ADR-001-project-architecture.md`, `ADR-002-media-detection.md`, `ADR-003-download-engines.md`, `ADR-004-youtube-adapter.md`, `ADR-005-youtube-owner-override.md`, `ADR-006-owner-override-any-working-method.md`) | `docs/DECISIONS.md` |
| `docs/prompts/` (`README.md`, `A-tiktok.md`, `B-downloads.md`, `C-ads.md`, `M-merge-preview7.md`, `P8-signed-beta4.md`) | History only (agent prompts) |
| `docs/design/DESIGN-NOTES.md` | `docs/DESIGN.md` |
