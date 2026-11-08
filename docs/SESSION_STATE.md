# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`. Owner tested Preview 213 (`a89a472b`) against the v1.0.2
  release on phone. Results are in TESTING "Phone results: Preview 213".
- Owner's report: Pornhub (two pages), Roshy and jav.guru VO quality rows read "Size unknown"
  in Preview 213; v1.0.2 showed sizes. Cause: `ba1be557` stopped peak-rate (BANDWIDTH) times
  length sizes after a YouTube replay read 1.7 times too high.
- Fix (this checkpoint): `FinishedStreamSampler` asks one byte (Range 0-0) of 48 spread pieces
  of a finished HLS playlist per quality and reads each piece's whole length from the reply;
  BYTERANGE playlists are summed exactly. The sheet shows "Estimating size…" then
  "~X MB · Rough estimate"; the variant's size is never changed. Limits: 400 asks, 8 MB,
  6 at a time; a server that ignores Range, 403 or 429 leaves "Size unknown".
- Host evidence (not phone), every piece's length as the truth: Roshy 480p/720p/1080p
  922/1945/3535 MB, jav.guru VO 895 MB. 48-piece sample: -1.3/-1.3/-0.1/+1.3%; worst of 200
  placements 4.9%. BANDWIDTH times length: -5.7/-4.9/-2.4/-3.3%.
- `YftFormat.bytes` no longer prints "1024 MB"; it reads "1 GB".
- Pornhub cannot be checked on host (age wall), so only the phone can prove those pages.
- Javtiful matches v1.0.2 (ad notice, 720p, sound rows). Wikimedia 4K was not tested (owner).
## Open decision for the owner
- jav.guru VO showed 3 rows in v1.0.2 because VI's 1080p/720p were grouped with VO by equal
  length. Current rule keeps files of equal length apart (PLAN "Two servers of one video").
## Validation and next steps
- Local split run on this checkpoint: model 179, api 36, generic 28, browser 161, media 72,
  download 406, data 38, sites 375, master 1,134 (2 skipped), master-android 85, app 1,640
  (66 skipped) passed, 0 failed; lint and debug build passed. The real sampler on JW's public
  master from a host JVM: 5 files, 245 asks, 0.09 MB, 0.8 s, -0.8% to -2.6%. CI on `734b3639`:
  validation 42498215302, emulator 42498215444, minified 42498215214, preview 42498215280
  passed. Preview 214 is the APK for the phone checks (artifact `yft-preview-apk`; the gh sign-in
  on this host had expired, so its sha256 was not read here).
- Next: short phone checklist for the new Preview APK (sheets only, no large downloads).
  No merge or release; sizes stay unverified until seen on a phone.
