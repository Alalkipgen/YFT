# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Work continues on `work/phase-v102-easy`. Phase 0 media feasibility cleanup is complete.
- All items queues selected post videos at one default quality each; no photos or playlists. X's
  public four-video post yielded four videos and nine quality rows; one file per item was fetched
  and its first five seconds decoded on the host. This was not an app or phone download. A public
  mixed Instagram example returned login required without a session. Fixture tests cover mixed posts.
- HLS uses a bounded piece pool and playlist-order assembly. Its 387 host tests passed. A live HLS
  window is not a complete size: average-rate estimates now require each quality's finished playlist
  and, when sound is separate, a finished sound playlist of the same duration. The resolver reads
  at most six playlists, 256 KiB each, within five seconds. It does not sample media in the app.
- Public HLS sample study: five HEADs per quality before downloading; Mux Big Buck Bunny 240 and
  380 estimates were 20,593,570 and 38,890,947 bytes, joined TS was 22,418,060 and 39,392,016
  bytes (errors -8.14% and -1.27%). Apple BipBop estimated 52,420,935 versus 52,352,924 bytes
  (+0.13%). These are three host cases, not evidence of a general accuracy or of final MP4 size.
- The three unsigned YouTube replay pages returned login required, so no current replay estimate
  or completed-size comparison was possible. Do not bypass access restrictions. The old owner's
  240p replay guess was ~251 MB for a 145 MB file. A second ~390 MB was progress, not completed size.
- The reported adult-page single quality and high Found count need a reproducible page before a
  cause can be identified. Do not change quality detection or ad filtering for this report.
- Earlier owner phone checks covered Wikimedia 1080p from Home and Browser, pause/resume, direct
  MP4/WebM and YouTube size display. They are not current-code tests. The 4K original preview and
  2.8 GB download remain untested. AV1 remains off in release pending the owner's phone test.

## Validation and next steps
- All items focused tests: 39 passed. Core download: 387 passed; core media: 59 passed. Script
  tests: 87 run, 4 skipped. On `4ccbbb44`, checkpoint validation, minified and Preview CI passed;
  emulator CI is running. Repeat CI after the size change.
- HLS host synthetic mixed-delay runs: 2029/2024/2025/2070 ms before and 1081/1080/1081/1123 ms
  after. Uniform runs: 287/334/324/318 and 270/309/314/312 ms. Not media or a device benchmark.
- Emulator on `05e5316f` passed AV1 stream-copy and forced AAC edit-list seek/audio checks; neither
  is a phone or full-stream pass. Host six-second H.264/AAC MP4 stream-copy: 1440p 66.6/14.7/9.7 ms,
  2160p 32.5/25.1/26.2 ms; Android readback was stubbed. No format or merge path changed.
- MP3 API 34 emulator baseline: 4 min AAC to 128 kbps MP3, 6.3 s wall; 60 min, 126.1 s. The
  displayed decode time includes waiting and encode includes writes, so they do not isolate CPU or
  I/O. No MP3 optimization was made. A local software emulator did not boot without KVM.
- Finish testing and push the size change, then report status and evidence. The owner will decide
  whether more tasks are needed before requesting one final APK. Do not send an APK or checklist yet.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
