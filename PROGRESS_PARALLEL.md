# PROGRESS_PARALLEL — Phase 1.3, parallel agent

Notes of the agent that builds D4 / D5b / D5c pieces next to the main Phase 1.3 agent (owner,
2026-10-15). Rules: new files only under
`extractor-master/src/{main,test}/kotlin/com/alal/yft/extractor/master/modules/youtube/ladder/`;
no existing file is edited (copied files, census, `ShapeFingerprint`, `StreamCheck`, `HlsCheck`,
`SimCases`, `r6-baseline.txt`, app, workflows, Gradle, `PROGRESS.md`, plan, prompt); D0 and D2
belong to the main agent; nothing is wired (D6); offline only (fakes, fixtures). Newest first.
No video ID, address or visitor data in these notes.

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
