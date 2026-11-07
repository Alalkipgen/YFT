# Testing

How YFT is tested: the commands, the CI workflows, the phone tools and the runbooks. Feature names
are as in [GLOSSARY.md](GLOSSARY.md).

## Run the tests on a computer

You need JDK 17 and the Android SDK (`sdk.dir` in `local.properties`, or `ANDROID_HOME`).

| What | Command |
|---|---|
| Lint, unit tests and the debug APK | `./gradlew lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug` |
| Master engine tests | `./gradlew :extractor-master:test` |
| Script tests | `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` |
| One test class | `./gradlew :extractor-master:test --tests '*SiteWeeklyTest*'` |
| Capture script and own-solver tests | `node --test extractor-master-android/src/test/js/*.test.cjs scripts/own-solver/tests/*.test.mjs` |
| Toolkit drift check | `python3 scripts/master-toolkit-drift.py --ref <base>` (`<base>` is the `# base` line of `extractor-master/toolkit-provenance.tsv`) |
| Comment-only check | `python3 scripts/check-comment-only.py <base> [<head>] [--allow <path>]` |
| Unsigned release APK | `./gradlew :app:assembleRelease`, then `bash scripts/verify-release-apk.sh --allow-unsigned --expected-version <version> app/build/outputs/apk/release/app-release-unsigned.apk` |

The checkpoint check runs the first three rows and the last row.

## CI workflows

| Workflow (name in Actions) | Starts on | What it runs | Artifacts |
|---|---|---|---|
| Checkpoint check (`checkpoint-validation.yml`, "Work-branch checkpoint validation") | Push to `work/**`, pull request to `main`, Run workflow | Lint, unit tests, debug APK; `:extractor-master:test`; script tests; the unsigned release APK and its check | `yft-debug-apk` (14 days) |
| Emulator smoke test (`emulator-smoke.yml`, "Android emulator smoke") | Push to `work/phase-*` that changes app, core, extractor or Gradle files; Run workflow | `scripts/ci-emulator-smoke.sh` on an API 34 emulator: `:app:connectedDebugAndroidTest` | `yft-emulator-smoke` |
| README screenshots (`readme-screenshots.yml`, "README screenshots") | Push to `work/phase-*` that changes `ReadmeScreenshotsTest`, its script or the workflow | `scripts/readme-screenshots.sh` on a Pixel 6-sized API 34 emulator; commits changed pictures to `docs/screenshots/` | `yft-readme-screenshots` |
| Preview APK (`preview-apk.yml`, "Preview APK (test key)") | Push to `work/phase-*` that changes code; Run workflow | `:app:assemblePreview :app:assemblePreviewTrial`, signed with a new test key, checked with `verify-release-apk.sh` | `yft-preview-apk`, `yft-preview-trial-apk` |
| Shrunk build check (`minified-check.yml`, "Minified build check (release branch)") | Push that changes code on the release work branches listed in the file, `work/phase-v102-easy` among them | The shrunk previews and the debug test APK on an API 34 emulator (`scripts/ci-minified-check.sh`, `MinifiedBuildCheckTest`) | `yft-minified-check` |
| Owner test APKs (`master-optin-debug-apk.yml`, "Master opt-in debug APK (spike)") | Push to `spike/master-extractor-backup`; Run workflow; label `build-optin-apk` on a pull request | Toolkit drift check, the capture script and own-solver tests, a debug APK with the chosen switches | `yft-master-optin-debug-apk` |
| Recipe publish workflow (`master-recipes-publish.yml`, "Master recipes publish (spike)") | Run workflow (input `config`, default `defaults`); label `recipes:<name>` on a pull request | Signs `extractor-master/recipes/configs/<name>.json` with the secret `YFT_RECIPE_KEY` and writes `master-recipes.json` (schema 1) and `master-recipes-2.json` (schema 2) to the `recipes` branch | — |
| Own-solver canary (`own-solver-canary.yml`, "Own solver canary (spike)") | Push to `spike/master-extractor-backup` that changes solver files; Mondays 03:17 UTC; Run workflow; label `run-canary` | `node scripts/own-solver-canary.mjs`, `verify-own-solver.mjs` (both strategies), `verify-own-solver-mutations.mjs --quick`, a summary | — |
| Spike validation (`phase0-validation.yml`) | Push to `main` or a pull request that changes `spikes/phase0-media/**`; Run workflow | Builds and tests the spike project | — |
| Release draft (`release-draft.yml`, "Release draft") | Tag `v*`; Run workflow (input `publish`) | Builds the signed release and makes a draft release ([RELEASE.md](RELEASE.md)) | — |

- A push that changes only `docs/**` or `*.md` files starts no build workflow.
- A commit whose message has `[skip ci]` starts no workflow on push.
- To run a workflow by hand: Actions → the workflow → Run workflow → choose the branch.
- The owner test APK workflow's manual run has no social switches, so it cannot build the census
  APK. Build that on a computer (see "Build the census APK").
- A push to `spike/master-extractor-backup` whose message has `[d7]` also builds one test APK per
  test mode (artifacts `yft-d7-<mode>`, for example `yft-d7-social-trial`).

## Tests that read docs

Keep these files and headings as the tests expect.

| Test or script | Reads | Needs |
|---|---|---|
| `SiteWeeklyTest` (extractor-master) | `docs/TESTING.md` | The heading `## Weekly site check runbook`, its eight step lines word for word, and the walled note line. Use only `###` headings inside that section. |
| `ReleaseNotesTest` (app) | `docs/release/1.0.1.md` | The file as released |
| `OpenSourceNoticesTest` (app) | `docs/THIRD_PARTY_NOTICES.md` | The notices file |
| `VkFixtureHygieneTest`, `VkPackFixtureHygieneTest` | VK fixture folders | Only the listed fixtures plus `README.md` |
| `scripts/release-prep.sh` | `CHANGELOG.md`, `docs/release/<version>.md` | A `## [<version>]` section and the notes file |
| `scripts/tests/test_spike_label_triggers.py` | The workflow files | `paths-ignore` of `docs/**` and `**/*.md` |
| `scripts/device-smoke-test.sh` | — | Its last line points to the manual checklist in `docs/RELEASE.md` |

## Design tests

`DesignRenderTest`, `LargeTextRenderTest` and `AccessibilityAuditTest`
(`app/src/test/java/com/alal/yft/design/`) and `YftThemeContrastTest` check the screens against
[DESIGN.md](DESIGN.md). They run with `testDebugUnitTest`.

## Phone and emulator tests

- **Emulator smoke test:** runs every instrumented test. The opt-in tests below skip themselves
  unless their instrumentation argument is set (for example `-e yft.parity 1`).
- **Android 9 save path:** the same workflow's `android-9-save-path` job runs
  `AppPrivateSaveInstrumentedTest` on an API 28 emulator through
  `bash scripts/ci-android9-save-path.sh`: a real download into YFT's own storage, its file and
  bytes, Android's own reader opening it, and the partial file opened again after the app closed.
- **Master parity check:** `bash scripts/canary.sh` runs `MasterParityLiveTest` on a connected
  phone or emulator and pulls its report. `ATTENDED=1 PLAY_SEC=60` lets you tap Play on each page;
  `ONLY=<case>,<case>` picks cases; `LOCAL_URLS=<file>` reads links from a local file that is never
  committed.
- **Own-solver phone check:** `bash scripts/own-solver-canary.sh` (or
  `PLAYER=<player ID> bash scripts/own-solver-canary.sh`) runs `OwnSolverCanaryTest`.
- **Stream check meter:** `bash scripts/stream-check-measure.sh` (or `VIDEO=<video ID> …`) runs
  `StreamCheckMeasureTest`.
- **Master YouTube canary:** `bash scripts/master-youtube-canary.sh <public video id>` (exit 0 =
  pass). Change a client table value only after it fails.
- **Live site check:** `bash scripts/live-check.sh <public-https-url>`.
- **Device smoke test:** `bash scripts/device-smoke-test.sh [--fresh] [--upgrade-from OLD.apk] <apk>`
  installs, starts and (with `--upgrade-from`) upgrades the app. Then do the manual checklist in
  [RELEASE.md](RELEASE.md).
- **README screenshots:** `bash scripts/readme-screenshots.sh` runs `ReadmeScreenshotsTest` on a clean
  emulator and copies Home, Found on this page, the Download sheet, Downloads, Library, Settings
  and dark Home to `docs/screenshots/`. The test downloads the public Big Buck Bunny sample.
- `AudioVideoMuxerInstrumentedTest` checks merges on a device.
- None of these reports may hold a link, a post ID or an address.

## Debug screens

Debug builds have four extra launcher icons (`app/src/debug/AndroidManifest.xml`). Each needs
`yft.masterCapture` and `yft.ownSolver`; the solver canary also needs `yft.ownSolverNext`.

| Launcher label | Feature | What it does |
|---|---|---|
| YFT stream check | Stream check meter | Measures the row check on one YouTube video |
| YFT client census | Client census | Asks each YouTube client identity on the owner's videos |
| YFT site census | Site census, weekly site check | Asks each site's slots and ladder steps |
| YFT solver canary | Solver canary | Runs the YouTube solvers on the phone: Run, then Copy report |

## Build the census APK

The census APK is the owner test APK with the social ladders on. Build it on a computer from the
commit you want to test:

```sh
base64 -d app/keystore/spike-debug.keystore.b64 > app/keystore/spike-debug.keystore
./gradlew :app:assembleDebug -Pyft.masterCapture=true -Pyft.ownSolver=true \
  -Pyft.ownSolverNext=true -Pyft.clientLadder=true -Pyft.socialLadder=true -Pyft.socialTrial=true
```

- The decoded keystore is never committed (`.gitignore`). The build signs debug APKs with it
  (password `android`, alias `androiddebugkey`), so a new census APK installs over the old one.
  Certificate SHA-256: `a2696dff62331776dee9baf34a150ffcec19e1a358656bc65517d165cc88bbbe`.
- Rename `app/build/outputs/apk/debug/app-debug.apk` to `yft-vk-census-<short commit hash>.apk`.
- `yft.socialFill` is true by default, so weak-answer fill and the best-first order are on.
- Latest: `yft-vk-census-854bee67.apk` (22,905,816 bytes, SHA-256
  `a9a86489bb071fd17a47e10a51303f739a29175eac407b1190fcb98ecfdef1bd`).

## Site census

"YFT site census" asks each site's slots. The links stay on the phone; the report and the JSON hold
no link, post ID, name or address.

- For each link: YFT's own lookup as Home runs it (fresh, with the agent and cookies of YFT's
  browser, each request once, at most 90 s), a one-byte check of its best quality, and the backup's
  way once (after a failure the backup would take, or as a trial).
- Per site, every link's own lookup and check come first, then the backup's ways (after failures,
  then the trials). A sign-in-only slot downloads only when you are signed in to the site in YFT's
  browser.
- An HTTP 429 or a bot check in YFT's own lookups stops that site for the run and for 30 min. In the
  backup's ways it stops only the backup's ways.
- At most 150 requests and 40 MB per site.
- Each site is compared with its own last run of the same kind (VPN on or off, signed in or not).
- It runs only after a tap and only while the screen stays open.
- Buttons: **Run all sites**, **Run <Site>**, **Copy report**, **Copy JSON**, and in a build with the
  social ladders **Weekly check W (all sites)**.
- Boxes, off by default: "Instagram's tab-fetch rung (signed out)" lets the browser's Instagram
  lookups use the Instagram tab-fetch step. "Instagram rung proof: set I2's rows aside" makes that
  step answer even when the step that judges main's answers (I2 in the box's text) has rows. "AV1
  merges (test, Android 14 or newer)" is the AV1 merge switch. The census itself never uses the
  tab-fetch step.
- The `recipes:` line names the signed recipe config's version.

### VK census rules

- Live, signed out, on the owner's public links. Links are never typed into docs or printed.
- Only the request kinds of VK's web client path: the page and its public script, one anonymous
  token kept in memory, `video.get`, the HLS and DASH manifests, and one-byte Range checks.
- No sign-in, no captcha or age answer, no whole-file download.
- Record only statuses, error codes, key names, counts, sizes and host families. Delete raw captures.
- One run per wrong-value test.
- VK's slots: 1–2 18+ video with list, 3 18+ video, 4–5 video, 6 clip, 7 finished live, 8 removed,
  9 private or sign-in-only, 10 region-limited.
- Media hosts seen: 18+ videos on `vk<N>-<N>.vkuser.net`, normal videos on `vkvd<N>.okcdn.ru`, never
  `iv.okcdn.ru`.
- The VK ladder's budget is `VkSite.BUDGET` (time: main's one 15 s deadline).
- There is no daily VK canary; the weekly site check covers VK.

## Weekly site check runbook

The weekly site check runs the site census's own steps on three slots per site and compares the
result with the site's last weekly run. Its lines call it W, and they call the row check V.

- **Where:** "YFT site census" → **Weekly check W (all sites)**. Debug builds with the social
  ladders only (the census APK); other builds' census screen has no such button. It runs only on
  the owner's tap (`SiteWeekly`; `SiteCensus.run` with `ClientCensus.Mode.WEEKLY`). It works like
  the client census's weekly mode: the same caps, and diff lines that each name a runbook step.
- **Slots** (kinds, never links; the census's own slots, so there is nothing new to fill in):
  Facebook 1 (video), 8 (share link), 13 (removed); TikTok 1 (single video), 6 (photo post), 9
  (removed); Instagram 1 (single video), 8 (photo-only), 10 (removed); X 1 (video), 6 (several
  videos), 8 (removed); VK 1 (18+ video with list), 4 (video), 8 (removed). An empty slot reads
  `W slot N (kind): no link, not run`. A site with no links reads `not run (no link in W's slots)`.
- **Each way at most once:** main's lookup once per slot (no retry), and the row check once on
  main's best row. Master's part runs on slot 1 only: the site's ladder once, each step at most
  once (social limits). Then the row check once on Master's best row. The other slots read
  `W: main only`. Instagram's tab-fetch step is never part of it.
- **Caps per site:** at most 60 s, 2 MB and 24 requests. The request cap comes from main's baseline
  census run: Facebook asks the most, about 5 requests per slot with the row check, so three slots
  and one Master part fit with room. Each cap is checked before every step (main's lookup, the row
  check, Master's part). The time left also bounds each step, so no site runs past 60 s; a step the
  time cuts is not run (`time`). A step that starts under the request and byte caps finishes, so a
  site can pass them by that one step's requests. The header prints each site's spend:
  `ran, N requests, X MB, S s`. A cap reached stops the rest of the site:
  `stopped (requests | bytes | time): the rest not run`. A cap has no cooldown.
- **Stops and cooldowns:** as in the census. A 429 or a bot check stops the site and cools it for
  30 min; in Master's part it stops only Master's parts. A cooling site reads
  `not run (cooldown N min (why))`. It is never waited out, never retried and never a pass. The
  first line names each site not run or stopped.
- **Baselines:** its own, per site, VPN and session (keyed as the census keys them): the last weekly
  run whose main part completed. It never compares with a full census run, and the census never
  compares with it. Its JSON export says `"mode":"weekly"`. The full census's report, export and
  state stay as they are; a state gets its `weekly` list only after a weekly run. A run where every
  request of a site got the site's login wall (the login page, or 401 or 403) is never that site's
  baseline (see "Every ask walled"). A partly walled run counts as usual.
- **Lines:** the first line is
  `W (weekly check): FIRST RUN | NO CHANGE | CHANGED (n) | BREAKING (n) | NOT COMPARED …`, then each
  site not run or stopped. Then one line per change, each ending with `→ W runbook step N (…)`, one
  of the steps below. A slot the check did not look up is a note, never NO CHANGE.
- **TikTok:** on the owner's network TikTok is not reachable without a VPN. There it stops at 60 s
  (`time`). That is not a failure, and the run is never TikTok's baseline.

### Two kinds of fix

**Data.** These change through the signed recipe config, with no new APK: a site's recipe keys and
paths, its endpoint's path and query (same host), its headers, its DRM, no-video and wall paths
(only added), and a ladder step's off switch (`rungsOff`).

1. Change `extractor-master/recipes/configs/<name>.json` and commit it on `main`. The JVM tests read
   the committed configs.
2. Actions → "Master recipes publish (spike)" → Run workflow → branch `main`, config name
   (`defaults`).
3. Wait about 5 minutes. Open the census screen: its `recipes:` line names the new version.

The app takes a new config when a ladder starts (within 6 hours) and at once when the census screen
opens.

**Code.** A commit on the work branch, then the JVM tests, the census, the weekly check again and
the phone. Media hosts and everything not listed under Data are code. Main's adapters are main's
code: a main fix waits for the owner's go. Meanwhile Master's ladder is the backup.

### The steps

1. **W runbook step 1 (the site's stop)** — `BREAKING <Site>: stopped (429 | bot check | rate limit)`
   where the baseline had none; `CHANGED <Site>: stopped (requests | bytes | time)` (a cap);
   `CHANGED <Site>: no stop (the earlier run stopped: …)`.
   - Check: the header's spend line against the baseline's, the network (the VPN exit), the time
     since the last run, and whether the full census on the same network stops too.
   - Fix: a 429 or a bot check is the site's limit, not a fault. The check skips the site for 30 min.
     Run it again after that, or another day if it repeats. Never work around it and never retry. If
     it stops on every run and network, the site got stricter: code (fewer requests: main's order,
     Master's budget). A cap means the site asks or moves more than in the baseline: compare the
     slots' `main:` lines (requests, repeats, variants) with the baseline's. A new request is step
     2's. Bigger pages: code (the cap in `SiteWeekly`, changed only with the owner's OK).
2. **W runbook step 2 (main's answer)** — `<Site> slot N (kind): main <before> → <after>`; BREAKING
   when main's rows are gone.
   - Check: the slot's `main:` lines (each request's way, status, reading and shape fingerprint)
     against the baseline's; the full census and Home's own lookup of the same post.
   - Fix: a status on a public post (403, 404, 410): open the post in a browser. Removed or private:
     give the slot a new link on the census screen (the next run reads `new link`). A new reading or
     shape (the site moved its data): main's adapter, code in `extractor-sites` with a fixture, on
     the owner's go. Meanwhile Master's part covers it (step 6), and a ladder step's key or path is
     data.
3. **W runbook step 3 (the slot's verdict)** — `as expected → not as expected (why)` (BREAKING), the
   reverse, or `ambiguous (no session)`.
   - Check: main's line for the same slot (step 2), the session (step 8), and the slot's kind and
     what it expects (`SiteWays`).
   - Fix: main's answer changed: step 2. The post changed (deleted, private, region-limited): a new
     link for the slot. The site now answers the case another way (for example a removed post's
     reason): code, the slot's expectation in `SiteWays`, with the owner's OK.
4. **W runbook step 4 (V)** — the row check's line: `V pass → fail` (BREAKING), `V … → not run`,
   `V … → unknown`.
   - Check: the row check's status and bytes. `not run` follows a stop or a cap (step 1) or main's
     rows gone (step 2). `fail` with 403 or 410: the file wants the page's cookies or a fresh
     signature. `unknown`: another host (or HLS).
   - Fix: a new media host: code (media hosts are never recipe data). The file's key or path in the
     answer renamed: data (the step's recipe, step 6). A signed file address: code (how the address
     is read). The row check never sends cookies.
5. **W runbook step 5 (the item or the length)** — `item N of M → …`, `best X s → Y s`.
   - Check: the post (a re-upload, a changed carousel or several-video post), X's `/video/N`, and
     Instagram's carousel index.
   - Fix: the post changed: a new link for the slot. Main reads another item: code (main's item
     rule) with a fixture. Master's rows are bound by item and length, so a mismatch there is step
     7's.
6. **W runbook step 6 (Master's part)** — `Master <before> → <after>`; BREAKING when Master's rows
   that passed the row check are lost.
   - Check: the slot's `Master` and `rungs:` lines (each step: rows, run, not run (cooldown,
     budget), not reached) and main's line (step 2).
   - Fix: a cooling host or a spent budget: no fix; run the check again after the cooldown. A step's
     reading moved: data (the step's recipe key or path in a config, and its off switch while it is
     fixed). On a lookup this shows as `master: <site> contract: N files by shape (key table changed)`:
     the key table read nothing and the contract was read by shape, so a key was renamed. Beyond the
     recipe's data: code (the site's ladder in `extractor-master`).
7. **W runbook step 7 (Master's wrong rows)** — `BREAKING … Master … wrong 0 → … wrong N`: a row
   proven to be another post's (another video or item, a length more than 1 s from every one of
   main's rows, or a row where the slot wants none).
   - Check: the slot's `ladder:` and `rungs:` lines (which step gave it) and main's rows.
   - Fix: first, before anything else, turn the step off: `rungsOff` with the step in a config,
     published as in "Two kinds of fix". Its line then reads `<rung>: off (config); not run`. Then
     code: the step's binding to the post (its ID, item or length check). Promotion runs and a
     release need wrong rows 0.
8. **W runbook step 8 (the run's conditions)** — `session no → yes` (or the reverse), `kind A → B`.
   - Check: the header's network, the site's sign-in in YFT's browser, and the build.
   - Fix: none in code. Baselines are per VPN and session, so run the check again in the baseline's
     conditions. A kind change means the census's slot list changed (another build): its baseline
     starts again.

### Every ask walled

`note: <Site>: every ask walled: likely this network's address (Weekly site check runbook); not a baseline`

Every request of the site (main's and Master's, on every slot looked up) got the login page or a
login status (401, 403), and no rows were read. The site's lines stay (BREAKING against a baseline
with data: main's rows gone, the verdicts). But the run is never the baseline, so the next run from
a good address compares with the last good one. A stored baseline that was such a run (an older APK
kept it) reads `note: <Site>: the baseline of <date> (<VPN, session>) had every ask walled: set aside`
(with `; this run starts it` when this run is good). It is not compared, and it is dropped.

- Check: the site's `main:` and Master's lines (each request the login page or 401 or 403, none
  `data`), the header's network (the VPN exit), and the same check without the VPN a few minutes
  later.
- Fix: none in code. The site walls this network's address, not the app. Reconnect the VPN for
  another exit (another city, or the same city's next address) and run the check again. NO CHANGE
  there: done. Walled on every exit and without the VPN, signed out: the site now walls every
  signed-out request. Go to step 2 (main's answer): main's fix on the owner's go, Master's ladder
  meanwhile.

### The first run

The first line is `FIRST RUN`: the baselines start. Look for:

- each site `ran` within its caps (a cap stop or `not run` is not a pass: run again after the
  cooldown);
- main's part of each slot as in the site's census run on the same network and session;
- slot 1's row check passes;
- Master's part on slot 1 with wrong rows 0.

The next run (another day, same network and session) is the first comparison: `NO CHANGE`, or
lines with their steps above.

## Promotion runs

Before a release counts a site's backup steps, the site needs three passing census runs.

- Three full census runs per site with a census APK built from the final code.
- Each run at least 6 hours after the one before; the first and the last at least 24 hours apart.
- A change to the site's ladder, its engine or main's code for that site starts the three runs
  again.
- A run where Master did not run (429, cooldown, not reached) does not count.
- One session may run all sites.
- Pass: wrong rows 0, and nothing worse than main's baseline census run.
- TikTok runs through the VPN's New York exit.
- For 1.0.2: verify the final configuration, including weak-answer fill. VK has not passed.

## Phone checklist for a release

Use the two preview APKs from the Preview APK workflow (and the census APK where marked).
Uninstall an older "YFT Preview" or "YFT Trial" first: each run signs with a new test key. To see
every detail line, turn on Show details: tap the version line in About 7 times (a reinstall turns it
off). If anything fails, open Details → Copy details and send it.

- **Facebook:** a 1080p manifest row downloads merged with sound, and its M4A row works.
- **Instagram:** a signed-in reel shows manifest sizes with sound.
- **AV1 test box** (census APK, Android 14 or newer): tick "AV1 merges (test, Android 14 or
  newer)" and open
  `https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel-av1.ism/.mpd`.
  Rows read "… · AV1"; the manifest states a peak rate, not a size, so each row reads
  "Size unknown". The merged MP4 has sound and runs 12:14. Details ends with "AV1 decoder: …".
  Untick it: the AV1 rows read "No sound".
- **DASH test streams:** `https://dash.akamaized.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd`
  gives a 360p MP4 with sound and an M4A; a DASH manifest states `@bandwidth`, a peak, so the rows
  read "Size unknown" and the download's own total is the first number.
  `https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd` gives an M4A "67 kbps" and seven rows
  from 2160p to 144p, none "No sound".
- **HTML5 page:** the w3schools `html5_video` page. The sheet ends on the MP4 row
  "144p · 320 × 176 · 25 fps · MP4". At large text the address pill shows once.
- **Wikimedia Commons:** in the browser, opening
  `https://commons.wikimedia.org/wiki/File:Big_Buck_Bunny_4K.webm` offers only its
  `upload.wikimedia.org` files, never the page's own address; the top row downloads and plays.
  Pasting the same address on Home reads the page instead of failing as an invalid address.
- **Fresh link renewal** (trial preview or census APK, "Break this download's link"): the download
  shows "Getting a fresh link…" and resumes at the same %. Details: "Fresh link (403 at … MB):
  resumed at … MB". Breaks 2–3 renew too; the 4th shows "Link expired"; Retry gives "Fresh link on
  Retry". A Wi-Fi ↔ mobile switch on a long VK video resumes. In a merge both tracks go on. YouTube
  shows "YouTube refused", then Retry works.
- **Pause and Resume:** the download goes on from the same %.
- **A download longer than 6 hours** (Android 15 or newer, census APK): Android pauses the service
  when its daily `dataSync` time runs out and the notice says so; Resume goes on from the same %.
  Not tested here: no run of that length has been made.
- **A download the app closed** (trial preview or census APK): start a long download, wait for it to
  pass 10% or more, then swipe YFT away from Recents (or force stop it in Settings). Open YFT: the
  row reads **Link expired** with "Tap Retry to get a fresh link…". Tap Retry: the row reads
  "Getting a fresh link…", then goes on from the same % and finishes. Details start with
  "Fresh link (…): resumed at … MB". If the page no longer offers the video, the row says
  "Fresh link: the video is no longer on the page" and keeps the bytes.
- **Ended YouTube live:** it saves. Its row reads "Size unknown" before and after the file is read:
  the sheet used to multiply YouTube's rate for the live format by the replay's length, ~1.7 times
  the file. The owner's replays: `https://www.youtube.com/live/vTaR-KqKUAM` (58:25, 240p: file
  145 MB, sheet read ~251 MB) and `https://www.youtube.com/live/ulRE89KaH5M` (2:51:06, 240p: sheet
  read ~689 MB beside a progress total of ~390 MB). Both rows now read "Size unknown"; the bar
  under the download is the measured total, "(estimated)". If it is up to 2 minutes short, Details
  says "Live video saved N s short of …".
- **VK:** each of the owner's three links opens a sheet with its qualities (one keeps its files on
  `vk6-13.vkuser.net`); the 720p row downloads merged with sound and plays; the clip link says VK
  does not play it. VK stays unpassed until its three promotion runs pass.
- **Weak-answer fill** (census APK or previews with Show details on): lines
  `master: facebook B+ (main's rows: no HD): Master added N: 720p` or `… added none (…)`. VK rows
  without a height get no such line. Strong answers stay unchanged. The best-first order shows
  `master: each height's best first`.
- **Text size 130% and 150%:** the Your sites names, the Downloads select buttons and the bottom bar
  fit. Release notes heading sizes look right.
- **Delete:** the dialog says "This can't be undone."; after it, "Deleted 1 file.".
- **Quality names:** TikTok 720 × 1280 shows "720p"; Instagram 640 × 640 stays "720p · HD".
- **Titles and folders:** a Zawgyi title gives a Unicode file name. Settings' folder line reads
  "Saving to <folder>. Tap to change.". The Downloads chip reads "Download/YFT · N GB free".
- **Sizes:** a row shows a size only when the source states one, an estimate with "~"; a row the
  source states no size for reads "Size unknown" (the ended live below is one). Under the bar of a
  download whose total is an estimate: "205 of ~236 MB (estimated)".
- **MP3:** a 3:18 song at 128 kbps converts in about 8 s. Details: "MP3: … of sound, decode … s,
  encode … s".
- Then the manual device checklist in [RELEASE.md](RELEASE.md).

## Own-solver canary

When the own-solver canary fails (workflow, phone check or "YFT solver canary"), the app stays
safe: the self-check refuses the failing kind (n or sig), that kind's streams are dropped, and the
visionOS rows still work. No remote code is ever used.

1. Note the player ID and the failing kind (n or sig).
2. Reproduce:
   - computer: `node scripts/own-solver-canary.mjs [player ID]` (report in
     `build/own-solver/canary-report.json`), or the workflow "Own solver canary (spike)";
   - phone or emulator: `bash scripts/own-solver-canary.sh` (`OwnSolverCanaryTest`, report in
     `build/own-solver/`);
   - phone only: "YFT solver canary" in the census APK: Run, then Copy report. Do three runs and
     close the app between them.
3. Add the player to the corpus (`scripts/own-solver/corpus.json`):
   `node scripts/verify-own-solver.mjs --add <id>`. Use `--only <id>` to run one player and
   `--prepare` to list the candidates per file.
4. If the computer passes and the phone fails, reproduce in a local headless Chromium outside the
   repository.
5. Find the kind of change: the wrapper or the candidates, a new code shape, an encoding, a missing
   global, or a new class of change (which needs a new general method).
6. Fix `extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js`.
7. Run `node scripts/verify-own-solver.mjs`, `node scripts/verify-own-solver.mjs --faults` and
   `node --test scripts/own-solver/tests/*.test.mjs`.
8. Push, build the census or owner test APK, run the phone check, then ship.

## Raw own-solver test data

`docs/n5b-raw/` holds the raw result files (JSON) of one own-solver ladder test cycle on four
computers: corpus, faults and change tests. It is kept as it is.
