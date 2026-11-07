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

- Baseline script tests: 87 run, 4 skipped, 0 failures. Capture and solver tests: 244 passed.
- The first local Android baseline run ran out of memory; a lower-memory retry is in progress.
- Next: complete the owner's selected work on all items, AV1 and AAC merge checks, and 2K/4K
  merge measurement. Validate each change and run final CI before one phone checklist.
- Do not run VK promotion, merge to `main`, publish, or turn on AV1 release merging.
