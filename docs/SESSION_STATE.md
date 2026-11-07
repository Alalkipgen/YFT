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
- The API 34 CI emulator passed the existing AV1 stream-copy and AAC edit-list fallback sample
  tests. The AV1 fallback probe completed there. Neither is a phone or full-stream playback pass.
- The AAC fallback sample test now checks audio playback after a seek. Its new CI result is pending.
  A local software emulator could not boot within nine minutes without KVM.
- All items needs the owner's choice of public photo sites and example posts. Next: finish the AV1
  and AAC phone checks and 2K/4K measurement, then all items for the chosen sites. Validate each
  change and run final CI before one phone checklist.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
