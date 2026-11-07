# Session state
Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.
## Now
- Branch `work/phase-v102-easy`; Phase 0 feasibility cleanup complete.
- All items queues selected post videos at one default quality each; no photos or playlists. X's
  public four-video post yielded four videos and nine quality rows; one file per item was fetched
  and its first five seconds decoded on the host. This was not an app or phone download. A public
  mixed Instagram example returned login required without a session. Fixture tests cover mixed posts.
- HLS uses a bounded piece pool and playlist-order assembly. Its 387 host tests passed. A live HLS
  window is not a complete size: average-rate estimates now require each quality's finished playlist
  and a finished sound playlist with at most 50 ms and 0.1% duration difference. The resolver reads
  up to six playlist lookups under a five-second coroutine timeout; blocking reads may outlast it.
  Normal reads cap at 256 KiB plus one byte; redirects/browser retries add traffic; no media sampled.
- Apple BipBop 540p separate AVC/AAC: 600.000/600.043 s, average estimate 164,300,475 bytes,
  host ffmpeg MP4 164,190,619 (+0.067%); not an app/device test. JW 286p/142p five-HEAD samples
  erred -22.54%/-19.44%, alternate positions +11.99%/+11.69% versus completed TS. Sampling is
  not in production; see TESTING. More cases do not establish a general accuracy or range.
- Five unsigned YouTube replay pages (including HKlRo-QdaRY and cy1BQSHYtZg) returned
  login required; no current estimate/completed comparison. Do not bypass restrictions. The old
  240p guess was ~251 MB for a 145 MB file; another ~390 MB was progress, not a completed file.
- Adult pages loaded in the host browser. Javtiful's Close redirected away; Play was covered.
  Jav.guru and Roshy interactions produced single-rendition HLS near preview players, not verified
  main ladders. Observed playlists were live windows. No YFT Found count or playback was verified.
- Earlier owner phone checks covered Wikimedia 1080p from Home and Browser, pause/resume, direct
  MP4/WebM and YouTube size display. They are not current-code tests. The 4K original preview and
  2.8 GB download remain untested. AV1 remains off in release pending the owner's phone test.
## Validation and next steps
- Local `c205527a`: browser 158, media 60, app 1,624 tests (66 opt-in render skips), and
  assembleDebug passed. Scripts: 87 run, 4 skipped. CI runs 42410587214 and 42410587180 are
  running; minified 42410587158 and preview 42410587181 passed.
- HLS host synthetic mixed-delay runs: 2029/2024/2025/2070 ms before and 1081/1080/1081/1123 ms
  after. Uniform runs: 287/334/324/318 and 270/309/314/312 ms. Not media or a device benchmark.
- Emulator on `05e5316f` passed AV1 stream-copy and forced AAC edit-list seek/audio checks; neither
  is a phone or full-stream pass. Host six-second H.264/AAC MP4 stream-copy: 1440p 66.6/14.7/9.7 ms,
  2160p 32.5/25.1/26.2 ms; Android readback was stubbed. No format or merge path changed.
- MP3 API 34 emulator: 4 min AAC to MP3, 6.3 s; 60 min, 126.1 s. Timers do not isolate CPU or
  I/O; no optimization. A local software emulator did not boot without KVM.
- Browser fixture protects two named videos, their qualities and unknowns while hiding a known ad.
  Download-listener ad signs and main-player provenance need more work. Finish CI; no APK.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
