# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`. Equal duration alone no longer groups files; HLS masters group with
  the variant addresses they name. CI on `d985914e` passed all four workflows.
- Fresh-link retry now uses the Same-file check (`SameFile`): same host and path; known signing and
  expiry values are ignored, changed content values (id, v, quality, itag…) mean another file.
  Length alone never picks a file. A unique same-path file needs a known length within 2 s.
  `MediaGroups.refreshed`, `FreshLinks.playerFirst`, the sheet's newest-link retry and Downloads'
  page reread use it. Unconfirmed matches stop with "open the page and download it again".
- Downloads cards save query names and value hashes (`mediaQuery`); old cards still read and
  count as a path-only match. After an ad, `playerVideoAfterAd` still picks by page-stated length.
- Owner phone observations on a Preview: Roshy showed matching 1080p/720p/480p rows, sizes unknown;
  Jav.guru showed one 480p row; Found counts may accumulate across servers. Candidate identities and
  ads are unproven. YouTube replay HKlRo-QdaRY opened with five size-unknown rows; no download.
  The installed APK identity is not confirmed; the last APK supplied was Preview 207 (`3cea4b9b`).
- Owner reports Preview downloads slower than v1.0.2 Release (`5fda5c59`); source and quality
  unknown. Host checks: the public JW 142p HLS took similar times on both builds, no remux. A
  synthetic five-quality average-rate master, 150 ms per response, took 305–310 ms and two requests
  on Release versus 1067–1070 ms and seven on current code. The earlier concurrent prototype
  lost estimates after a failure and is not in the app.
- Phase 0 cleanup is complete. All items handles video only. No photo/slideshow or VK census.
  AV1 release merge stays off. Wikimedia 4K preview and 2.8 GB download remain untested.
## Validation and next steps
- Same-file check, local split run: model 170, api 36, generic 24, browser 159, media 62,
  download 386, data 38, sites 375, master 1,134 (2 skipped), master-android 85 and app 1,631
  (66 skipped) passed, 0 failed; lint and debug build passed. New identity tests were red first.
- Next: read-ahead for HLS size playlists that keeps the one-at-a-time requests and budget; then
  measure lookup, transfer and finalization apart on both builds. Do not infer the phone cause.
- Then CI, the Preview APK and one phone checklist; ask before data-heavy phone tests. No merge or
  release; quality rows, ads and size estimates stay unverified until shown on a phone.
