# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`. Equal duration alone no longer groups files; HLS masters group with
  the variant addresses they name.
- Fresh-link retry uses the Same-file check (`SameFile`, `adb0d170`): same host and path; known
  signing and expiry values are ignored, changed content values (id, v, quality, itag…) mean another
  file. Length alone never picks a file. Unconfirmed matches stop with "open the page and download
  it again". After an ad, `playerVideoAfterAd` still picks by page-stated length (unproven).
  CI on `adb0d170`: validation 42459862573, emulator 42459862603, minified 42459862543 and
  preview 42459862571 passed.
- Playlist read-ahead (`PlaylistReadAhead`) reads up to three HLS size playlists at once, starting
  only reads the one-at-a-time loop is sure to make. Host fixture, 150 ms per response: Release
  306–309 ms (2 requests, no estimates), sequential 1,060–1,067 ms, read-ahead 460–615 ms (7 each,
  same estimates). `HlsSizeReadsTest` fails on the earlier prototype (3 of 4 estimates).
- Host download phases (local JW 142p copy, 2 MB/s per response, medians): HLS transfer 4,940 ms
  on Release vs 3,562 ms current; finalization 36/42 ms (.ts) and 77/83 ms (remux); direct
  4,716/4,718 ms. Hashes matched. The phone slowdown cause is still unknown.
- Owner phone observations on a Preview: Roshy showed matching 1080p/720p/480p rows, sizes unknown;
  Jav.guru showed one 480p row; Found counts may accumulate across servers. Candidate identities and
  ads are unproven. YouTube replay HKlRo-QdaRY opened with five size-unknown rows; no download.
  The installed APK identity is not confirmed; the last APK supplied was Preview 207 (`3cea4b9b`).
- Phase 0 cleanup is complete. All items handles video only. No photo/slideshow or VK census.
  AV1 release merge stays off. Wikimedia 4K preview and 2.8 GB download remain untested.
## Validation and next steps
- Read-ahead, local split run: model 170, api 36, generic 24, browser 159, media 72, download 386,
  data 38, sites 375, master 1,134 (2 skipped), master-android 85, app 1,631 (66 skipped) passed,
  0 failed; lint and debug build passed. CI on the read-ahead commit is next.
- Then the Preview APK from that CI run and one phone checklist; ask before data-heavy phone tests.
  No merge or release; quality rows, ads and size estimates stay unverified until seen on a phone.
