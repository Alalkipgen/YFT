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
- Adult host browser: Javtiful Close redirected away; Play was covered. Jav.guru and Roshy had
  single-rendition live HLS near previews, not verified main ladders. No YFT count or playback verified.
- Mux public VOD has finished video/audio durations 734.169/734.113 s (56 ms apart). Its master
  also names a subtitle codec that YFT does not support. Host MP4 577,995,046 bytes; combined-rate
  arithmetic 577,855,885 (-0.0241%), not a YFT estimate. Apple HEVC: all 54 variants had <=50 ms.
  Check the case where separate audio cannot be paired: combined rate may size a silent row.
- Earlier owner phone checks covered Wikimedia 1080p from Home and Browser, pause/resume, direct
  MP4/WebM and YouTube size display. They are not current-code tests. The 4K original preview and
  2.8 GB download remain untested. AV1 remains off in release pending the owner's phone test.
## Validation and next steps
- Local `c205527a`: browser 158, media 60, app 1,624 tests (66 opt-in render skips), and
  assembleDebug passed; scripts 87 passed with separately installed jsonschema. CI validation
  42410587214, emulator 42410587180, minified 42410587158 and preview 42410587181 passed; API 34
  ran 90 tests (7 skipped), API 28 ran 2 (0 skipped); neither tests these sites.
- AV1/AAC tests on `05e5316f` were emulator checks, not phone or full-stream passes.
- Browser fixture protects two named videos, qualities and unknowns while hiding a known ad.
  Download-listener ad signs and main-player provenance need more work. A low-memory software
  emulator boot attempt is running; preserve privacy when testing sites.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
