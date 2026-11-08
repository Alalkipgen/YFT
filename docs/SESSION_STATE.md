# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`. Owner tested Preview 212 (`b9b3bcf2`) and set three tasks: Found
  list ads, ended-live sizes, HLS capture and quality rows. This checkpoint does all three on host.
- Stream piece filter (`StreamPieces`, `PageCandidateStore`): pieces a read HLS playlist names are
  not listed or probed; full lists drop pieces first; `_HLS_msn`/`_HLS_part` asks of one playlist
  are one playlist (`MediaFileUrls.wholeFile`). A live master's named qualities spend no reads.
- Live stream set aside: a playlist with no end moves under "Other videos on this page" while the
  page has another video ("Live" chip) and is never `playerVideoAfterAd`'s pick. The Found list
  now passes the page's facts, so a page-length video an ad list matches stays listed.
- Ended live size sample (owner's choice B, by itself, 2026-11-08): `EndedLiveSampler` reads 8 KB
  of a spread sample of the replay's pieces (piece boxes give exact lengths) and the sheet shows
  "Estimating size…" then "~X MB · Rough estimate". The variant's size is never changed. Limits:
  400 asks, 5 MB, 6 at a time; WebM and too-varied files stay unknown.
- Host evidence (not phone): owner's replay, 189 asks, 1.55 MB, 32.8 s; 144p +0.2%, sound
  +0.03%, 144p with sound 63.4 MiB as the owner's 63 MB download. Camera strip: 91–268 media
  requests a minute; low-latency playlist 61 addresses in 77 s, 7 without directives.
- Unproven: phone Found counts, a 403 master's grouping (still separate), the sample's time and
  data on a phone, other replays.
## Validation and next steps
- `a89a472b`, local split run: model 179, api 36, generic 28, browser 161, media 72, download 396,
  data 38, sites 375, master 1,134 (2 skipped), master-android 85, app 1,634 (66 skipped) passed,
  0 failed; lint and debug build passed. CI on `a89a472b`: validation 42484817047, emulator
  42484816979, minified 42484817054, preview 42484816985 passed. Preview 213 APK (`a89a472b`,
  sha256 0d54b365…6dffbb3) is the one supplied for phone checks.
- Next: the owner's phone checklist for Preview 213; ask before data-heavy phone tests.
  No merge or release; quality rows, ads and size estimates stay unverified until seen on a phone.
