# extractor-master-android

Master's Android parts: browser capture in YFT's visible browser tab, codec steering, playback hooks
and the WebViews of the YouTube own solver. The plain Kotlin engine is in
[extractor-master](../extractor-master/README.md). Names follow [GLOSSARY.md](../docs/GLOSSARY.md).

## Dependencies

`:extractor-master`, `:core-browser`, `:extractor-generic`, AndroidX WebKit, OkHttp and kotlinx
coroutines. Android library, `minSdk` 24.

## Browser capture

`WebViewPlaybackCapture` attaches to the browser's visible WebView. It adds to the browser's
observation sink and does not replace its clients, security policy or downloads. It only watches:
no JavaScript bridge, no network proxy, no automatic play, no sign-in, nothing stored on disk.

- `MasterBrowserSession`: one tab, in memory. Each page load or in-page navigation gets a new
  generation, and older observations are dropped.
- `yft-master-capture.js`, the collector, runs in the top frame only and is bound once per lookup.
  On VK pages it is also installed at document start, so the player's first manifest is kept.
- `CaptureFrame` reads the collector's packet: at most 256 KiB, each body 64 KiB, each address
  8 KiB.
- A capture runs on the main thread for at most 2.5 s, one sample every 200 ms (at most 10).
  Playback counts only after progress is seen twice. Paused or preloaded media never counts.
- On YouTube, Facebook and TikTok feeds, `CaptureFocusGuard` checks that the focused video is still
  the one asked for. If the focus moved, the lookup starts over.
- Headers are kept only with the exact address the browser saw. Page scripts cannot supply cookies
  or headers.
- A licence key or encrypted media stops the lookup as DRM until the next page.
- `MasterMainSelection` picks the main video and its qualities. Media fingerprints group one
  video's files. A lone file that disagrees with a confirmed main is an ad or a clip and is
  dropped. A piece of a file is never a row.

## Page scripts

| File (`src/main/assets/`) | What it does | When |
| --- | --- | --- |
| `yft-master-capture.js` | Browser capture's collector | Bound per lookup; also at document start on VK |
| `yft-master-codecs.js` | Codec steering: tells the page's player only the codecs this phone can merge (AVC and AAC always; VP9, Opus and Vorbis while WebM merges are on; AV1 while AV1 merges are on; HEVC and Dolby never). Never touches DRM queries | Document start; not on YouTube |
| `yft-master-mse.js` | Playback hooks: notes MSE source buffers and the addresses fed to them, and marks EME use as DRM. It only observes | Document start; not on YouTube |

`CodecSteering` builds the codec policy from the same rules as the app's merge support. A WebView
without document-start scripts gets no steering or hooks; capture still works.

## YouTube own solver

The `solver/` package, with its files in `src/main/assets/yft-own-solver/`:

- `WebViewOwnSolverEngine` runs `own.solver.core.js` in a worker, in a fresh offscreen WebView
  that is destroyed after each run. The answer comes back through one narrow bridge.
- `WebViewPageSolverEngine` is the own-solver ladder's page step, for players that need a real
  page. The player's code runs in a sandboxed frame (`page-frame.html`) with no cookies or storage.
- `OwnSolverPageRoutes` and `OwnSolverPageEnvRoutes` serve only the app's own files from Android's
  reserved asset host and refuse every other address. `OwnSolverPageReplyGate` decides which
  message ends a page run.
- `OwnSolverAppBreaker` is the breaker the app and the solver canary share.
  `SharedPreferencesBreakerMarkers` keeps its markers across a crash: a player ID and a step name
  only.
- The solver WebViews never use the browser's cookies, storage or network, and never load main's
  ejs files. Output is capped at 12 MiB of text.

## Tests

- `./gradlew :extractor-master-android:testDebugUnitTest` (Robolectric).
- `node --test extractor-master-android/src/test/js/*.test.cjs` tests the three page scripts. The
  owner test APK workflow runs it too.
- The own solver's computer tests and canary: [TESTING.md](../docs/TESTING.md), "Own-solver
  canary".
