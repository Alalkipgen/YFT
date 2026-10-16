# Handoff — Phase 1.4 backlog items 1–3: WebM merge (2K/4K), h-only through H, visionOS 1.04

Written 2026-10-16 (the owner's order; docs only when written) on `spike/master-extractor-backup`
at `5a389f1`. For a fresh agent: every code fact below was checked on that commit; re-read only
the files you edit. Order: items 1 and 3, then item 2, then the `[d7]` APKs (run 4, §5).

**Status (2026-10-16):** built — item 3 `f02070a`, item 1 `ec1f24e`, item 2 `7c7a72a`; run 4
APKs `[d7]` from `1d3f41c` (CI green, `d7-matrix` 7/7, run 38940779817); the phone checks (each
item's Done): items 1 and 3 ✅ on the phone (run 4); item 2 showed the adapter's rows again
(the engine skips a claimed page unless no adapter answered) → fixed, new `[d7]` APKs. Run 4b
(2026-10-17): H ran but gave no row ("H: SEGMENT"): YouTube's segment ignores the range (fixed:
the ladder's reader keeps 64 KiB) and is `application/octet-stream` (named no container, read as
unknown) → the owner's (က): a generic type lets the start decide; Try again's adapter rows fixed;
new `[d7]` APKs (run 4b). Run 4b on the phone: H gives rows, but HLS sound stopped after the
first piece (packed audio: an ID3 tag before every piece's ADTS frames) → fixed (the tags are
dropped when the pieces are joined); retest with the next `[d7]` APKs. Owner item 6: a recently
ended live stream failed every download (`INTEGRITY_MISMATCH`, CONNECT) → live-origin files trust
the first range and take short answers, other files stay strict; `[d7]` run 4c.

## 0. Rules (every item)

- Work only on `spike/master-extractor-backup`; PR #2 (spike → `main`) stays a draft. Never
  touch `main`; no separate PR to `main` (`main` has the WebM bug too; the fix lives here).
- Push only with the GitHub MCP `push_files` tool (server "GitHub") and `arguments_file_path`
  (JSON built by a script; the commit message without `'`, backtick or `$`). After a push: fetch,
  compare each file, reset to `FETCH_HEAD`. Run `git log -1` first (the sandbox may have been
  restored from an old backup).
- Never edit main's five copied files (`YouTubeClientProfile`, `YouTubeExtractor`,
  `YouTubePlayerResponseParser`, `YouTubeSessionAuth`, `YouTubeUrls`);
  `python3 scripts/master-toolkit-drift.py` stays at 0 drifted.
- The parallel agent's files are read-only: `InitCheck`, `Rungs.kt`, `LookupMemo`,
  `ShapeLocator`, `ShapeNormalizer`, `HlsLeg.kt`, `HlsLegTest.kt`, `PROGRESS_PARALLEL.md`.
- Docs: no video IDs, media addresses or visitor data; say "census video 1/2"
  (`ClientCensus.FIXED_VIDEOS`).
- The owner: replies in Burmese, short and fast; a pause is one line
  "⏸ ရပ်ထားတယ် — <why>; လိုတာ: <what>".
- One item per commit (item 1 may be two: diagnostics, then the fix); CI green before the next.
- **Stop rules (every item):** stop, write PROGRESS and ask the owner when
  - a parity test or its data would change: `MasterYouTubeParityTest`, `CensusR6ParityTest`,
    `FixtureBaselineTest` with `extractor-master/src/test/resources/parity/fixture-baseline.json`,
    `ParityHarnessTest`, `MasterParityLiveTest` (androidTest, `parity-urls.json`);
  - a guardrail (`MASTER_KEY_PHASE1_3_PLAN.md` §2) would change;
  - CI is still red after 3 fixes;
  - a protected file above would change.

### CI

| Check | Workflow | When | Runs |
|---|---|---|---|
| `build` | `master-optin-debug-apk.yml` | a push to the spike that is not docs only (paths-ignore `docs/**`, `**/*.md`) | the drift script, the own-solver node tests, `:extractor-master:test`, `:extractor-master-android:testDebugUnitTest`, a filtered `:app:testDebugUnitTest` list (`--tests …`), the opt-in debug APK (artifact) |
| `validate` | `checkpoint-validation.yml` | PR #2 updates, not docs only | `lintDebug`, `testDebugUnitTest` of every module (so `core-download` and every `app` unit test), `assembleDebug`, the release APK check |
| `d7-matrix` | `master-optin-debug-apk.yml` | a push whose message has `[d7]`; edit `.github/d7-apks.txt` | the seven phone-check APKs |
| `emulator-smoke` | `emulator-smoke.yml` | a push to `work/phase-*` or **Run workflow** only | `:app:connectedDebugAndroidTest` (with `AudioVideoMuxerInstrumentedTest`) |

`emulator-smoke` never runs by itself on the spike. Do not change its triggers; ask the owner to
press **Run workflow** with the spike branch selected. Locally only the JVM modules build
(`:extractor-master:test`); `app`, `core-download` and `core-media` compile on CI only.

## 1. Item 1 — WebM merge fix (2K/4K VP9 + Opus fails at MERGE)

### Evidence (owner, phone, 2026-10-16)

- Android 16 (API 36), app `1.0.0-beta.3-debug`, mobile data, census video 2 (3:33, a 4K
  remaster). 720p MP4 (AVC + AAC) downloads and plays with sound.
- 1440p (VP9 WebM + Opus WebM, about 147 MB) reaches 99 % (the tracks are downloaded) and fails
  while merging:
  - run 3 `d7` APK, 3 of 3: `Reason: Incompatible tracks (INCOMPATIBLE_TRACKS) · Stage: Merging
    video and sound (MERGE) · Detail: IllegalArgumentException; took video 1 min 2 s, merge 2.4 s
    · Download type: Merge (audio + video)`;
  - Copy details of another try: `Detail: IllegalArgumentException; took video 46 s, merge 557 ms
    · Start: plan 0.0 s · length 0.0 s (known) · first byte 1.2 s · first progress 1.2 s · Saved
    to: Downloads (MediaStore)`.
- The same on preview.78 (`main`): `AudioVideoMuxEngine.kt` and `StreamCopyAudioVideoMuxer.kt` do
  not differ between `main` and the spike.
- No 2K/4K WebM merge was ever proven on a phone. CI's
  `AudioVideoMuxerInstrumentedTest.aVp9VideoAndItsOpusSoundBecomeOneWebm` merges
  `app/src/androidTest/assets/mux/video-vp9.webm` (5,863 bytes) with `audio-opus.webm`
  (8,047 bytes). 2160p (about 345 MB) is expected to fail the same way.

### The code today (`core-download/src/main/java/com/alal/yft/core/download/AudioVideoMuxEngine.kt`)

- `AudioVideoMuxCompatibility.outputMimeTypeFor`: WebM video → `video/webm`; `evaluateWebm`
  needs VP9 with Opus or Vorbis from `WEBM_OPUS_MIN_SDK` (29).
- `AndroidMp4AudioVideoMuxer` (`FAST_MERGE_ENABLED = true`) → `StreamCopyAudioVideoMuxer`, which
  copies **MP4 only**: a WebM output goes straight to `MediaMuxerAudioVideoMuxer`, so the Detail
  has no "stream copy …" note (as on the phone). This is why 720p works and 2K does not.
- `MediaMuxerAudioVideoMuxer.mux`: two `MediaExtractor`s (`setDataSource`, `findTrack`,
  `selectTrack`), `MediaMuxer(…, MUXER_OUTPUT_WEBM)`, `addTrack` video, `addTrack` audio, `start`,
  `copySamples`, `stop`.
- `copySamples`: one direct buffer of `sampleBufferBytes(video, audio)` — the larger
  `KEY_MAX_INPUT_SIZE` of the two formats within `MIN_SAMPLE_BUFFER_BYTES` (256 KiB) …
  `maxSampleBufferBytes` (`DEFAULT_MAX_SAMPLE_BUFFER_BYTES` 8 MiB; `MAX_SAMPLE_BUFFER_BYTES`
  32 MiB); **without `KEY_MAX_INPUT_SIZE`: 256 KiB**. Per sample: `readSampleData`,
  `writeSampleData`, `advance`; its own `sampleBytes > capacity` check throws
  `IllegalArgumentException("Invalid or oversized media sample")` (with a message).
- `IllegalArgumentException`, `IllegalStateException` and `SecurityException` →
  `incompatible(error)` → `INCOMPATIBLE_TRACKS` with `DownloadFailureDetails.of(error)`
  ("Class: message"; a bare class name = no message).
- `AudioVideoMuxEngine`: in place first (from `IN_PLACE_MIN_SDK` 26, the destination's
  descriptor); a failure before the first sample falls back once to `mergeThenCopy` (app
  storage); after a sample it fails at once. `withTimes` appends `took …` and keeps the Detail
  within `DownloadFailureDetails.MAX_CHARS` (120): about **84 characters** stay for the reason.

### Inference (not proven; step 1 decides)

A bare `IllegalArgumentException` fits `MediaExtractor`'s native calls: `readSampleData` (also
`getSampleTime` and `getSampleFlags`) throws it without a message on a read error, a buffer
smaller than the sample included. `MediaMuxer`'s own `IllegalArgumentException`s carry
messages, and its native `addTrack` and `writeSampleData` errors are `IllegalStateException`s.
So A is likelier than B; a damaged track file (C) would look the same.

### Step 1 — confirm the cause with logs

- Record in the failure's Detail and in the log **which call threw**: `addTrack video`,
  `addTrack audio`, `readSampleData video|audio`, `writeSampleData video|audio`, `stop`; for a
  read the failing sample's size (`MediaExtractor.getSampleSize()`, API 28+; WebM merges need
  29+); **the largest sample per track**; the buffer's size.
- Fit the ~84 characters (for example
  `IllegalArgumentException at readSampleData video: 347 KiB > buffer 256 KiB`); check the text
  after `DownloadFailureDetails.sanitize` in a test (it rewrites host-like and token-like words).
- Confirm with the new fixture on the emulator (Run workflow) and with the owner's phone (the
  run 4 APK or the `build` job's debug APK): A when the failing read's sample is larger than the
  buffer; B when `addTrack audio` (the Opus format) throws; anything else → stop and report.

### Step 2 — the fix

- **A:** the buffer grows to fit the sample: before `readSampleData`, `getSampleSize()`; when it
  is larger than the buffer, a new direct buffer of that size (rounded up), at most
  `MAX_SAMPLE_BUFFER_BYTES` (32 MiB); a larger sample fails with a clear Detail. Without
  `getSampleSize()` (before API 28) the sizing stays as today. The MP4 stream copy (P35) is not
  touched.
- **B:** what the logs show (for example the Opus format's keys or CSD the WebM muxer needs). A
  new container, FFmpeg or a Matroska writer → stop and ask.

### Test and fixture

- Fixture (tested in the sandbox; ffmpeg with libvpx-vp9):

  ```sh
  ffmpeg -f lavfi -i "nullsrc=s=800x450,geq=random(1)*255:128:128" -frames:v 1 n.png
  ffmpeg -loop 1 -i n.png -t 1 -r 25 -c:v libvpx-vp9 -crf 10 -b:v 0 -g 250 \
    -pix_fmt yuv420p video-vp9-big-keyframe.webm
  ```

  About 357 KB, 25 frames, the keyframe about 355,713 bytes (over 262,144 = 256 KiB; random
  noise, so sizes vary a little). Saved as
  `app/src/androidTest/assets/mux/video-vp9-big-keyframe.webm`, merged with the existing
  `audio-opus.webm` (1 s).
- `AudioVideoMuxerInstrumentedTest`: a VP9 keyframe over 256 KiB merges into one WebM — the
  phone's failure before the fix, green after; `emulator-smoke` only.
- `core-download` JVM tests (in `validate`): the buffer rule (grows to the sample, never above
  32 MiB, never below today's size) and the Detail text.

### Files to touch

- `core-download/src/main/java/com/alal/yft/core/download/AudioVideoMuxEngine.kt`
  (`MediaMuxerAudioVideoMuxer`: `mux`, `copySamples`, `sampleBufferBytes`, `SampleSource`).
- `core-download/src/test/java/com/alal/yft/core/download/` (a new test).
- `app/src/androidTest/java/com/alal/yft/download/AudioVideoMuxerInstrumentedTest.kt` and
  `app/src/androidTest/assets/mux/video-vp9-big-keyframe.webm`.
- Docs: `PROGRESS.md`, `docs/TEST_MATRIX.md`, `docs/SUPPORT_MATRIX.md` (P6: proven on a phone, once
  it is).

### Out of scope

The MP4 stream copy (`StreamCopyAudioVideoMuxer`, P35); a Matroska stream copy
(`docs/FIX_ADD_PLAN.md`); sound for VP9 over HLS (plan §10 backlog item 5); AV1 merges
(`AV1_MP4_ENABLED = false`); `DownloadFailureDetails.MAX_CHARS`; failure reasons and their
mapping; which formats are offered; `main`.

### Stop rules

§0, and: the logs show neither A nor B; the fix needs FFmpeg, a new container or a Matroska
writer; an MP4 merge test would change.

### Done

`build` and `validate` green; the new emulator test green (Run workflow); on the phone, census
video 2's 1440p and 2160p merge and play with picture and sound (owner, run 4).

### Review checklist (a separate reviewer agent)

- [ ] The cause is named by the logs (the PROGRESS entry quotes the Detail or the test).
- [ ] The buffer only grows, to the sample's size, never above 32 MiB; no allocation per sample;
      `getSampleSize()` only from API 28.
- [ ] A sample above the cap fails with a clear, sanitized Detail within 120 characters.
- [ ] The fixture's keyframe is over 256 KiB; the old fixtures and tests are unchanged.
- [ ] MP4 merges (stream copy and fallback) behave as before.
- [ ] Only the files above changed; protected files and parity tests untouched; drift 0.

## 2. Item 3 — visionOS 1.04 as a bundled alternate (guardrail 5)

### Evidence

The census "A check (visionOS 1.04)" (`ClientCensusActivity`, debug source set), owner,
2026-10-16 15:41 UTC, mobile data: `visionOS 1.02 DIRECT; visionOS 1.04 DIRECT, V PASS: 1.04 can
be visionOS's bundled alternate (NewPipe, 2026-08-06)` — 3 requests, 37 KB, 1 s, no bot check;
both answers the same shape (27 adaptive formats, 144p–2160p, av01/avc1/vp9, mp4a/opus, HLS);
V `PASS` on avc1 144p (2xx). Source: NewPipe Extractor `ClientsConstants.java`, 2026-08-06.

### The change

- `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/modules/youtube/ladder/ClientTable.kt`
  (`MasterClientTable`): the visionOS entry's `alternates = emptyList()` → one
  `Alternate("1.04", "NewPipe Extractor ClientsConstants.java", "2026-08-06")`; the KDoc's "none
  today … only after the owner's census A check" → listed after the A check passed.
- One definition, shared with `ClientCensus.A_CHECK_ALTERNATE` (`…/ladder/ClientCensus.kt`).
- R0 stays 1.02 (`profile.clientVersion` unchanged).

### How it is used (code unchanged)

`ClientLadder.versionRung` (A) runs only with the trial `NONE`, when health allows a request,
after visionOS's answer is judged `VERSION_REJECTED`; order learned → self-healed → bundled
alternates (`VersionSource.alternates`: at most `MAX_ALTERNATES`, the version pattern, not the
bundled version), at most `MAX_A_PER_LOOKUP` requests; a bot check or 429 stops A. A healthy
answer never reaches A, so parity cannot change. Guardrail 5 (plan §2 item 5): at most 4
identities; versions in the order learned, signed (D8), self-healed, at most 2 bundled
alternates listed by a public reference (yt-dlp, YouTube.js or NewPipe) with source and date;
never invented or counted up. `ClientTableTest` checks it.

### Tests

`ClientTableTest` (visionOS has exactly this alternate; the guardrail 5 test unchanged);
`ClientCensusACheckTest` (the same constant); a ladder test: 1.02 judged `VERSION_REJECTED` →
1.04 asked once, judged and checked by V; after a bot check 1.04 is not asked. A test expecting
"no alternates" is updated (not a parity test).

### Files to touch

`ClientTable.kt`, `ClientCensus.kt` (the constant only), `ClientTableTest.kt`,
`ClientCensusACheckTest.kt`, a ladder test; docs: `PROGRESS.md`, the plan's status notes.

### Out of scope

(ဂ), A after R0's routine bot check, stays deferred until W shows 1.02 rejected; alternates of
other identities; R0, the self-heal source, D8; the census tool.

### Stop rules

§0, and: guardrail 5's text would change; a request is added to the healthy path.

### Done

`build` and `validate` green; parity tests unchanged; run 4's `normal` A check still passes.

### Review checklist

- [ ] R0 1.02 unchanged; exactly one visionOS alternate; version, source and date exact.
- [ ] One shared constant (no second "1.04").
- [ ] No new request on the healthy path; A still stops on a bot check or 429.
- [ ] Parity tests and `fixture-baseline.json` untouched; drift 0.

## 3. Item 2 — h-only skips the normal path so H really runs

### Evidence

Run 3's `h-only` APK listed "MP4"/"WebM" rows with sizes (census video 2: 2160p WebM about
345 MB … 720p MP4 about 29 MB; census video 1: 240p and 144p MP4) — the app's primary YouTube
adapter's rows. H's rows are HLS (`QuickDownloadChoices.formatName` shows "HLS"). So the trial
never went through H.

### Why

`app/src/main/java/com/alal/yft/detection/master/BrowserMasterFallback.kt`,
`AndroidBrowserMasterFallback.recover`: `if (!enabled || primary is SiteAdapterOutcome.Detected)
return primary` — Master (`MasterYouTubeModule` → `MasterClientLadder` → `ClientLadder.merge` →
`hOnly`) runs only when the primary adapter fails. Callers: `BrowserViewModel.kt`
(`masterFallback.recover(…)`). The trial: `MasterLadderSwitch.kt`
(`LadderTrial.of(BuildConfig.LADDER_SKIP, BuildConfig.LADDER_MUTATE)`); `app/build.gradle.kts`
`LADDER_SKIP` (default `""`, `-Pyft.ladderSkip`); `LadderTrial.hOnly`; `ClientLadder.hOnly`
already returns H's one HLS row alone.

### The change

Only with the h-only trial (the `d7` h-only APK): the primary's `Detected` result is not taken;
the Master path runs (as after a primary `NO_MEDIA_FOUND`), so the list is H's rows. Check that
`capture.appCandidates(…, key)` and `mergeSupport::canMerge` keep the HLS row and that
`TerminalRules.blocksFallback` still ends on a real bot check or login (R2).

### Tests

`app/src/test/java/com/alal/yft/detection/master/BrowserMasterFallbackTest.kt`: h-only with a
`Detected` primary → Master runs, the result is H's HLS row only; trial `NONE` with a
`Detected` primary → the primary unchanged. The class runs in `validate` (and in `build` when
listed there).

### Files to touch

`BrowserMasterFallback.kt`, where the fallback is built (`MasterLadderSwitch.kt` or its caller),
`BrowserMasterFallbackTest.kt`; docs: `PROGRESS.md`, plan §4 D5c.

### Out of scope

Builds with the trial `NONE` (every release): unchanged; the ladder's order and rungs; sound for
VP9 over HLS; the parallel agent's files; the other five trial modes.

### Stop rules

§0, and: any change visible with the trial `NONE`; a new request in normal mode.

### Done

Tests green; on the phone (run 4 `h-only`) census videos 1 and 2 list HLS rows only, and an AVC
row downloads and plays with picture and sound.

### Review checklist

- [ ] Gated by the trial only; a release build cannot reach the new branch.
- [ ] Trial `NONE`: the same outcome and the same requests (test).
- [ ] A bot check or login stays final (R2).
- [ ] Only the listed files changed; parity tests untouched; drift 0.

## 4. Then — the `[d7]` APKs (run 4)

Edit `.github/d7-apks.txt` ("run 4") in a commit whose message has `[d7]`; `d7-matrix` builds
the seven APKs (no-login links through nightly.link for the run's `yft-d7-<mode>.zip`). Phone
steps (mobile data):

1. `normal`: census video 2 → 1440p and 2160p download, merge and play with picture and sound;
   the census A check still passes.
2. `h-only`: census videos 1 and 2 → HLS rows only → an AVC row downloads and plays with sound,
   its length the video's ±2 s.
3. Optional: the other five APKs, the lists as in run 3.