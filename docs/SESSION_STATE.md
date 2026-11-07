# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- Work continues on `work/phase-v102-easy`. The separate Phase 0 media feasibility project and
  its validation workflow were removed; production uses the root Gradle project.
- All items selects and queues video items offered by the existing Instagram and X post models;
  photos and playlists are out of scope. Each selected item uses one default quality. Queueing
  results are separate from later download results; uncertain enqueue results must be checked in
  Downloads before retrying. A closed app may interrupt batch queueing.
- An unsigned live X post returned four distinct videos with nine quality rows; this checked the
  extractor, not full downloads or phone playback. An unsigned mixed Instagram post returned
  login required. Public page HTTP 200 alone is not an extraction pass.
- The owner tested Wikimedia 1080p downloads, pause/resume and playback from Home and Browser,
  and direct MP4 and WebM downloads on Android 16 on an earlier preview. The 4K original preview
  and 2.8 GB download remain untested. The YouTube replay showed unknown size and estimated
  progress; its full download was not retested on that build. None is a current-code phone pass.
- The release AV1 merge switch remains off until the owner tests it on a phone.

## Validation and next steps

- Before the All items changes, script tests ran 87 with 4 skipped; capture and solver tests
  passed 244; app unit tests ran 1,617 with 66 skipped and site tests passed 375. The debug APK
  built using a lower-memory retry. Repeat relevant tests on final code.
- On `05e5316f`, API 34 emulator CI ran 90 tests with 7 skipped. AV1 stream-copy and forced AAC
  edit-list fallback tests passed, including seek and audio playback; the AV1 fallback probe
  completed. Neither is a phone or full-stream pass. CI and Preview passed on that code.
- Host-only 2K/4K timings: JDK 17, Xeon 2.50 GHz, two CPUs, six-second synthetic H.264 plus AAC,
  MP4; production stream-copy with Android readback stubbed took 66.6/14.7/9.7 ms (1440p,
  12.1 MB video) and 32.5/25.1/26.2 ms (2160p, 29.8 MB). Not an Android or phone benchmark.
  A separate FFmpeg Matroska comparison was slower and differed in timing; no format was changed.
- Next: run the final All items tests and CI, measure HLS and MP3 before changing either, and
  record failed or blocked checks. Finish with one Preview APK and one necessary phone checklist.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
