# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`; recovered WIP `19b15874` failed 3 app tests on CI 42451656634.
  Code checkpoint `e4675855` adds explicit HLS master-to-variant grouping; CI is still pending.
- Equal duration alone no longer groups files; unlinked qualities can appear as separate items.
  Fresh-link retry can choose a different video via duration-only or query-stripped matching; investigate.
- Owner phone observations on a Preview: Roshy showed matching 1080p/720p/480p rows, sizes unknown;
  Jav.guru showed one 480p row and a player had three options, but server correspondence is unknown.
  Found counts 12/29 may accumulate across servers. Candidate identities and ads are unproven;
  Javtiful found 5/10 s ad candidates and later a 30:53, 720p item of unproven main identity.
  YouTube replay HKlRo-QdaRY opened with five size-unknown rows; no completed download. The installed APK
  identity is not confirmed; the last APK supplied was Preview 207 from `3cea4b9b`.
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
- On `e4675855`, local full app: 1,624 tests, 0 failed, 66 skipped. Model 158, generic 23,
  browser 159, media 62, download 386, API 36, sites 375 and master 1,134 (2 skipped) passed.
  Script tests 87 (4 missing-dependency skips) and JavaScript tests 244 passed. Full one-shot build
  exceeded the 4 GiB host limit on baseline; suites were split. CI for this code is running.
- Two synthetic host cases reproduced wrong retry selection: equal-length unrelated URLs, and same
  path with differing content-ID query values. `MediaGroups.refreshed`, `FreshLinks.playerVideo` and
  `FreshLinks.playerFirst` are affected; Downloads renewal also uses duration/path. No fix yet.
- Investigate a safe retry identity without breaking legitimate signed-link refresh. Check bounded
  metadata concurrency, cancellation, shared trace and WebView's serialized fallback before any
  optimization. Compare lookup and transfer separately; do not infer phone slowdown cause.
- Continue generic player provenance, ads and reliable estimates; don't infer identity from timing.
  Never merge or publish without approval. Final Preview/checklist only after all safe work.
