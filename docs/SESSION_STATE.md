# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- Work continues on `work/phase-v102-easy`. The separate Phase 0 media feasibility project and
  its validation workflow are removed. Production uses the root Gradle project.
- The owner tested the Wikimedia 1080p download, pause/resume and playback from Home and Browser,
  and direct MP4 and WebM downloads on Android 16. The 4K original preview and 2.8 GB download
  remain untested. The YouTube replay showed an unknown size and estimated progress; its full
  download was not retested on this build.
- The release AV1 merge switch remains off until the owner tests it on a phone.

## Validation and next steps

- Script tests: 87 run, 4 skipped, 0 failures. Capture and solver tests: 244 passed. App unit
  tests: 1,617 run, 66 skipped, 0 failures; site tests: 375 passed. The first Android run ran
  out of memory; a lower-memory retry passed. The production debug APK built after cleanup.
- On code `05e5316f`, API 34 emulator CI ran 90 tests with 7 skipped and 0 failures. The AV1
  stream-copy and forced MediaMuxer AAC edit-list tests passed, including the new seek and audio
  playback check. The AV1 fallback probe completed there. Neither is a phone or full-stream pass.
- Checkpoint validation, minified-build check and Preview APK CI passed on `05e5316f`. A local
  software emulator could not boot within nine minutes without KVM.
- Exploratory host-only 2K/4K measurement: JDK 17, Xeon 2.50 GHz, two CPUs, six-second synthetic
  H.264 video plus AAC, MP4 output. Production stream-copy with Android readback stubbed took
  66.6/14.7/9.7 ms at 2560x1440 (12.1 MB video) and 32.5/25.1/26.2 ms at 3840x2160 (29.8 MB).
  This is not the end-to-end Android path or a phone benchmark; no optimization was made.
- A separate host FFmpeg comparison was slower for Matroska than MP4 on these tiny fixtures;
  Matroska timing differed and Android/player compatibility was not established. No format changed.
- All items needs the owner's choice of public photo sites and example posts. Next: finish the AV1
  and AAC phone checks and 2K/4K measurement, then all items for the chosen sites. Validate each
  change and run final CI before one phone checklist.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
