# PROGRESS_PARALLEL — Phase 1.3, parallel agent

Notes of the agent that builds D4 / D5b / D5c pieces next to the main Phase 1.3 agent (owner,
2026-10-15). Rules: new files only under
`extractor-master/src/{main,test}/kotlin/com/alal/yft/extractor/master/modules/youtube/ladder/`;
no existing file is edited (copied files, census, `ShapeFingerprint`, `StreamCheck`, `HlsCheck`,
`SimCases`, `r6-baseline.txt`, app, workflows, Gradle, `PROGRESS.md`, plan, prompt); D0 and D2
belong to the main agent; nothing is wired (D6); offline only (fakes, fixtures). Newest first.
No video ID, address or visitor data in these notes.

## 2026-10-15 — HlsLeg, the H leg (D5c, §3 H steps 1–3)

- `HlsLeg.kt` (new; nothing wired):
  1. Address (`address`): the answer's `streamingData.hlsManifestUrl` when it is HTTPS on
     `googlevideo.com` (another host → no H), else ShapeLocator's one located HLS address (none
     or several → no H).
  2. Length (`length`): the copied parser's when its answer is for the requested video, else L's
     bound length, else the page's; none → no H rows (guardrail 6). A verdict from L
     (`ShapeNormalizer.Stop`: not `OK`, DRM, live) → no H.
  3. The lookup's H check (`lookup().run`): D0's `HlsCheck`, unchanged (master → highest
     variant → its playlist, ≤ 256 KiB each; a key other than `METHOD=NONE` → fail; the variant's
     duration within ±2 s of the length), given a client whose one file check reads the first
     segment's start instead (`Range: bytes=0-65535`, ≤ 64 KiB). Content type and container
     start must agree: `video/mp2t` + the sync byte; `video/mp4` / `audio/mp4` + `ftyp`, `styp`,
     `moof`, `sidx`, `emsg` or `prft`; WebM + EBML. No type or `octet-stream` → unknown;
     another type → fail. Statuses: 403, 4xx, `host`, `not-m3u8`, `no-variant`, `no-segment` →
     fail; 5xx, `net`, `too-large` → unknown; a fail wins. Pass → `Row` (the master address; the
     master's variants, highest resolution and audio form, the segment's container; `detail()`
     for the details line; `candidate()` = a `MediaCandidate` of kind HLS,
     `application/vnd.apple.mpegurl`, the length, the expiry from `/expire/`, no cookie, height
     from the master) for the app's existing HLS path; fail or unknown → `None(why, verdict)`.
  - One H check per lookup: the same address again returns the first outcome (no request),
    another address → `SPENT`. ≤ 3 requests (master, variant, segment read; no probe). Headers
    without `Cookie`, `Cookie2`, `Authorization`, `Proxy-Authorization`, `X-Goog-AuthUser`,
    `Range`. Nothing logged; outcomes print verdicts, reasons and counts only.
- Plan adapted: (a) `HlsCheck`'s segment step is D0's one-byte probe and its result holds no
  address, so the leg's client turns that probe into the segment read (`HlsCheck` not edited;
  still 3 requests). (b) Bodies come as text (the app's client reads UTF-8), so the start is
  checked on the decoded characters: the TS sync byte and an MP4 box type at offset 4 survive;
  a box size that decodes to fewer characters reads fail (no row; safe); D6 may give a byte
  read later, as for InitCheck. (c) `emsg` and `prft` count as fMP4 segment starts (CMAF)
  beside `ftyp` / `styp` / `moof` / `sidx`.
- Tests: `HlsLegTest` (6) — step 1 (the answer's field wins, another host → none, located, two
  located → none); step 2 (parser for the requested video, L's binding, page, a verdict → none);
  step 3 on a good HLS (3 requests, no credentials, `Range` on the segment only, row facts and
  candidate, print hygiene); faults (master / variant 403 fail, unreachable / 503 unknown,
  segment 403 / type / start / host fail, duration +2.5 s fail and +1.5 s pass, key `AES-128`
  fail and `NONE` pass, `octet-stream` unknown, fMP4 pass, an MP4 type with a TS start fail;
  nothing asked off `googlevideo.com`); no address or no length → no request; one H check per
  lookup. H matrix (D5c's list): V4–V6, V10, V11, L1–L13 (each V mode), F3 ×5, an encryption key
  and a missing length × 5 bases = 300 runs, 145 rows, wrong 0, no case with an H path missed.
  Complete through H: V4, V5, V10; L2–L7 in every V mode; L1, L8, L9, L10, L13 with V `pass`.
  Clean: V6, V11, L11 ×3, L12, F3 ×5, key, length; the moved HLS of L1, L8–L10, L13 with V
  `fail` / `unknown` (the world refuses what only L finds).
- For D1 / D6 (no file edited): H also completes L1, L8, L9 and L10 with V `pass` through the
  located address (20 runs; the world's HLS plays there, truth clean), where `SimCases` lists
  no H path (written before §3 H step 1's located address).
- Not done: step 4 (when the leg runs) and the wiring (D6); D5c's owner-phone download (D7).
- Module tests: 558, 0 failures, 2 skipped; drift: 107 copied symbols, 0 drifted.

## 2026-10-15 — ShapeLocator + ShapeNormalizer (D5b, rung L steps 1–6)

- `ShapeLocator.kt` (step 1, the value-shape scan; pure, no request, no log). Read from values,
  key names never trusted:
  - format-like object: exactly one YouTube media-server address with an `itag` parameter
    (1–999), or none and exactly one cipher-like string (form fields `s` and `url`, the `url` such
    an address); an integer field (number or 1–3 digit text) equal to that `itag`; exactly one
    full mime (`video/…` or `audio/…` with `codecs="…"`), or none and one bare type plus one
    known-codec list (rebuilt into one). An address `mime` parameter must name the same type.
    Otherwise the object is rejected (counted), or ignored when it has no media address at all.
  - video object: a direct text equal to the requested video ID and a positive `lengthSeconds`;
    status object: a direct text from the copied parser's status words (in one object a word
    other than `OK` wins); HLS address: https `manifest.googlevideo.com` under
    `/api/manifest/hls_variant/` or `/api/manifest/hls_playlist/` (exactly one → `hls`, §3 H
    step 1); DRM marker: a `drm…` / `…Drm…` / `…DRM…` key with a value, or a `WIDEVINE`,
    `PLAYREADY`, `FAIRPLAY` or `DRM_TRACK_TYPE_…` text.
  - Bounds as ShapeFingerprint: ≤ 4 MiB (parsed with the copied parser, ≤ 400 000 nodes),
    depth 16, ≤ 100 000 visited nodes (more → incomplete). Plan adapted (deep cut): a container
    deeper than 16 is not opened; the scan stays complete only when the raw answer mentions
    `googlevideo.com`, the quoted video ID and the quoted status words no more often than the
    visited part does (else incomplete → no L rows; no raw text → incomplete).
  - For D2: `moved` (format-like objects outside `streamingData.formats[]` /
    `streamingData.adaptiveFormats[]`), `partial` (in those lists, not readable by the copied
    parser as they are), `hls`, `formatPaths` (ShapeFingerprint-style; a video-ID or not
    key-like key reads `*`). The scan binds nothing (another video's formats are listed too).
- `ShapeNormalizer.kt` (steps 2–6): `normalize(answer, videoId, now)` → `Rows` (offer ≤ 8,
  canonical JSON, bound count and paths, `hls`, length) / `Stop` (a verdict other than `OK`: the
  R6 chain decides) / `None(why)`:
  1. Playability: the answer's own `playabilityStatus.status` is the copied parser's verdict.
     Without one, the located status objects: none → no rows; one `OK` → go on; any other word →
     `Stop` with the copied parser's verdict on that object (Plan adapted: also when several are
     located); several, all `OK` → ambiguous, no rows.
  2. Binding: the video objects with the requested ID must agree on one length (Plan adapted:
     copies of the same length, such as a microformat, are fine; different lengths → ambiguous);
     a candidate binds by its address `dur`, else `approxDurationMs`, else an integer field of
     milliseconds, within ±1.5 s; others are dropped; duplicates (same address or cipher) once;
     all bound candidates under one parent, else ambiguous, no rows.
  3. A DRM marker or a live flag → canonical JSON with `drmParams` / `isLive` → the copied
     parser's stop; never rows.
  4. Fields (Plan adapted): `itag`, address or cipher and mime from the value rules; the other
     fields are integers or words whose shape cannot tell them apart, so they are read under
     their canonical names with normalized types (digit text or number), by shape only where it
     is distinctive (`1080p60` for `qualityLabel`, `AUDIO_QUALITY_…`). A renamed size stays
     missing (Z).
  5. Canonical JSON → the copied `YouTubePlayerResponseParser.parse` (unchanged; main's row
     rules apply) → `offerSet`: the copied extractor's private `select`, mirrored, ≤ 8 rows
     (companions, then merged rows by quality, then files).
- `LRows` + `LRowCheck` (guardrail 6): every offered row needs V and the init check in this
  lookup (`LRowCheck` = V plus `InitCheck` on the bytes V read; D6 wires it). Companions first;
  a merged MP4 row only after an AAC companion passed, WebM only after Opus; protected rows
  (cipher, S's work, D5a) are not checked here; an exception is unknown; unknown is never pass;
  ≤ 8 checks; `complete` = a merged row and its companion passed.
- Tests: `ShapeNormalizerTest` (10) — L round-trip on the 5 bases (canonical parse = direct
  parse, offer = the app's selection); L matrix: 19 mutations (SimCases' L1–L16 copied as
  `LShapeCases`, L4 and L11 split) × 5 bases × V pass / fail / unknown = 285 runs, 0 wrong
  rows: L1–L4, L7, L8, L10, L16 give the unchanged rows (28 rows; complete in the 5 V-pass
  runs), L6 84 rows (complete 15 of 15), L15 40 rows (complete 5), L5 and L14 protected only
  (no L row; S), L9 ambiguous, L11 ×3 and L12 stop, L13 HLS located, no formats; a 307 KiB
  answer in 8.0 ms median (budget 20 ms); playability, binding, DRM / live, fields, offer cap,
  LRows order, print hygiene. `ShapeLocatorTest` (10) — value rules under any key names,
  rejected and ignored objects, cipher and split mime, video / status / HLS / DRM, deep cut,
  node / size / depth bounds, `moved` / `partial`, the 5 bases (every format located in the
  canonical lists), codec helpers, print hygiene.
- Test substitution: SimWorld's WebM stand-in is a cut EBML header that InitCheck reads as
  unknown, so the matrix's V reads WebM files as real WebM starts (with the file's encryption).
- Watch: the deep-cut count reads raw text, so an escaped form (`\u…`) is not counted; real
  answers do not escape these ASCII words. A real answer deeper than 16 that repeats the video
  ID below the cut gives no L rows (safe; census L steps will show).
- Not done: L steps 7–8 (shape map memory, page signals), census L steps,
  `-Pyft.ladderMutate`. Not wired (D6).
- Module tests: 552, 0 failures, 2 skipped (`:extractor-master:test`, masterCapture +
  ownSolver); drift: 107 copied symbols, 0 drifted.

## 2026-10-15 — LookupMemo, the same-lookup memo (D6 piece, guardrail 7)

- `LookupMemo.kt`: `LookupMemo(http, maxEntries = 8, maxKeptChars = 8 Mi)` wraps one lookup's
  HTTP client; two sides, both `ExtractorHttpClient`:
  - `ladder`: the ladder's requests go out unchanged; a 2xx answer of a request to a host that is
    not a media host (`memoable`: https, not `googlevideo.com`) is kept for this lookup.
  - `chain`: the R6 chain's requests. A request byte-identical to a ladder request of this
    lookup (method, address, body, headers with names, values and order) is answered from memory
    once (each kept answer serves one chain request, first kept first served); every other
    request goes out unchanged.
  - Never across lookups (`clear()` at the end of the lookup drops all); never media hosts
    (streams, HLS playlists); file checks (`probe`) always go out on both sides. A failed ladder
    answer is not kept, so the chain asks again as today. A kept answer larger than the chain's
    body limit is not served. Bounded: 8 answers, 8 Mi characters. Nothing logged; `toString`
    and the counts (`keptCount`, `servedCount`) show no address, body or header.
- Tests: `LookupMemoTest` (10): byte-identical POST served once, the page GET served once,
  another method / address / body / header value, a header more or less, another header order
  or name case go out, media hosts and probes, failed answers not kept, only ladder answers
  kept and the ladder always asks, never across lookups, body limit, entry and character
  bounds, counts only.
- Not wired (D6 passes `chain` to the R6 chain and `ladder` to the ladder).
- Module tests (Z and LookupMemo): 532, 0 failures, 2 skipped (`:extractor-master:test`,
  masterCapture + ownSolver); drift: 107 copied symbols, 0 drifted.

## 2026-10-15 — Z, sizes from the probe (D4)

- `Rungs.kt`: `ZRung(http, timeoutMillis = 5 s)` — rung Z (§3, §4 D4): a missing
  `contentLength` comes from the existing one-byte probe's total
  (`ExtractorProbeResult.Answered.totalBytes`). A and S (D5a) are not in this file yet.
  - `lookup()` = one budget per lookup, never shared: at most 8 probes over all its `fill`
    calls; the same address is asked once per lookup.
  - `fill(rows, headers)` asks only a row without a size that has a direct YouTube media-server
    address (`YouTubeUrls.isMediaServerRequest`); a row with a size, a protected descriptor or
    another host is not asked. The caller passes the offer set only.
  - The probe carries the download's headers without `Cookie`, `Cookie2`, `Authorization`,
    `Proxy-Authorization`, `X-Goog-AuthUser` and `Range` (the probe sets its own).
  - Z fills a size only: it never judges, adds or drops a row. A refusal, no total or 0, an
    error or no answer in time leaves the size missing (an outcome per row). A client that
    cannot probe (`Unsupported`) is asked once, costs no probe and is not asked again in that
    lookup.
  - Nothing is logged; a result holds rows (`toString` hides addresses), outcomes and a count.
- Tests: `ZRungTest` (9): fill from the total and known sizes not asked, 8 probes per lookup
  over all calls and a new budget per lookup, media-server addresses only, headers without
  cookies / credentials / range, refusal / no total / 0 / error, timeout, `Unsupported` once
  and free, one ask per address, no address in a result.
- Not wired (D6).

## 2026-10-15 — InitCheck (D4, guardrail 6)

- `InitCheck.kt` (pure; no request, no log; reasons only): runs on the bytes V read for an L row.
  - Type: the answer's base content type = the row's (`video/mp4`, `audio/mp4`, `video/webm`,
    `audio/webm`; case and parameters ignored). Another type → `fail`; none or
    `application/octet-stream` → `unknown`; a row type the check cannot read → `unknown`.
  - Container start: MP4 → first box `ftyp` / `styp` / `moof` / `sidx` of ≥ 16 bytes (size 0 and
    64-bit sizes read); WebM → EBML magic `1A 45 DF A3` and a stated doc type `webm` / `matroska`.
    Else `fail`; fewer than 8 bytes → `unknown`.
  - Encryption: MP4 `encv`, `enca`, `sinf`, `schm`, `tenc`, `pssh`, `senc` (CapturedMp4Facts'
    box walk + a scan of every byte read, so a cut box still counts) → `fail`. WebM:
    `ContentEncryption` or `ContentEncodingType` ≠ 0 in a track (bounded EBML walk Segment →
    Tracks → TrackEntry → ContentEncodings → ContentEncoding) → `fail`; "no encryption" only when
    the bytes hold the whole `Tracks` element (a cut header or Tracks, a Cluster first, an
    unknown-size element before Tracks → `unknown`).
  - Size: `Content-Range` total ≠ the row's `contentLength` → `fail`; a row size but no total →
    `unknown`; no row size → no size check. `InitCheck.totalOf(header)` reads only a whole
    `bytes a-b/total`.
  - A `fail` from any step wins over an `unknown`; the caller shows an L row only on `pass`.
- Tests: `InitCheckTest` (19): pass for MP4 / WebM starts, every start box, every MP4 marker,
  a whole CENC init segment, a marker in a cut box, WebM ContentEncryption / type 1 / second
  track, compression is no encryption, wrong types, missing type, wrong start bytes, too few
  bytes, WebM Tracks unread, size mismatch / unknown, fail over unknown, unsupported row types,
  `Content-Range` parsing, no value in `toString`.
- **Plan adapted (for D6 wiring, main agent):** `ExtractorHttpClient.get` returns the body as a
  `String` (the app's client decodes UTF-8) and no `Content-Range`, and `probe` reads no body.
  The init check needs the raw first bytes and the `Content-Range` total, so the L-row V needs a
  byte read (D6 decides how; no copied file changes).
- **Simulator note (D1 world, unchanged):** SimWorld's MP4 stand-in bytes pass and its encrypted
  MP4 stand-in fails, as meant. Its WebM stand-in is a cut EBML header without Tracks, so the
  init check says `unknown` for it (the encrypted WebM stand-in: `fail`, its doc type reads
  `webmCont…`); WebM L rows in the simulator therefore need real EBML start bytes to pass.
- Module tests: 513, 0 failures, 2 skipped (`:extractor-master:test`, masterCapture + ownSolver);
  drift: 107 copied symbols, 0 drifted.
