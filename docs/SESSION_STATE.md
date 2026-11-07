# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`; remote HEAD `c733fd5a`. The equal-duration grouping draft is
  local only, unfinished and not pushed. GitHub CLI sign-in is needed for `scripts/checkpoint.sh`.
- Files without a shared video ID or page-player key now stay separate even at equal duration.
  This avoids combining distinct videos, but unlinked qualities can appear as separate items.
  Fresh-link retry still has a duration-only fallback and can choose a different video; investigate.
- Owner phone observations on a Preview: Roshy showed matching 1080p/720p/480p rows, sizes unknown;
  Jav.guru showed one 480p row and a player had three options, but server correspondence is unknown.
  Found counts 12/29 may accumulate across servers. Candidate identities and ads are unproven.
  Javtiful found 5/10 s ad candidates and later a 30:53, 720p item; main identity is unproven.
  YouTube replay HKlRo-QdaRY opened with five size-unknown rows; no completed download. The installed
  APK identity is not independently confirmed. The last APK supplied was Preview 207 from `3cea4b9b`.
- Owner reports Preview downloads slower than v1.0.2 Release (`5fda5c59`). Source and quality are
  not yet known. On a Linux host the public JW 142p HLS (38,312,896 bytes) produced identical hashes
  in four runs per build, no remux. After one warm-up, release total times were 1127/822/754 ms;
  current times were 1164/716/581 ms. Network/cache can differ; this is not phone throughput.
- A local synthetic five-quality average-rate HLS master, 150 ms per response, took 305–310 ms and
  two requests on Release versus 1067–1070 ms and seven on current code. Peak-only took 305–308 ms
  and two requests on both. This is sheet metadata latency, not proven phone transfer slowdown.
- HLS uses a three-slot piece pool, bounded playlist-order assembly and existing retry rules. Recent
  HLS estimates require ended video/audio playlists, close durations and source average bitrate;
  no segment sampling. Five-HEAD experiments are not in production. Unsigned YouTube replay host
  checks returned login required; no current estimate-versus-completed measurement.
- Phase 0 cleanup is complete. All items handles video only. Do not add photo/slideshow or VK census.
  AV1 release merge remains off pending phone evidence. Wikimedia 4K original preview and 2.8 GB
  download remain untested. Earlier Wikimedia/direct/YouTube phone passes were on earlier builds.
## Validation and next steps
- Local grouping draft: model 158 (including two external cases), browser 158, media 62 and focused
  app grouping passed; full app test failed 3 of 1,624 with 66 skips. The failures include a master
  and its quality playlist and MP4/OGG alternatives. Do not ship this draft; preserve proven
  relationships without grouping unrelated videos. Full baseline hit the 4 GiB memory limit; split
  core tests and 87 script tests passed.
- Baseline app had 1,624 tests, 66 opt-in skips, and debug build passed. Last code CI on `3cea4b9b`:
  validation 42415423330, emulator 42415423334, minified 42415423320, preview 42415423432 passed.
- Continue review of retry identity, app regressions, generic player provenance, ads and reliable
  size estimates. Do not infer main/ad status from count, origin or timing. No site-specific lists.
- Check remote HEAD and sign in to GitHub CLI, run checkpoint validation and push; then run CI and
  produce one final Preview/checklist only after all safe work. Never merge or publish without approval.
