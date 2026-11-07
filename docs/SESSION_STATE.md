# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- 1.0.2 is published (2026-11-03). Its changes and the README screenshots change are merged to
  `main`. Work continues on `work/phase-v102-easy`, and no pull request is open.
- The README has a new introduction and real app screenshots. README screenshots runs on an
  emulator and saves seven images to `docs/screenshots/`. The sample is from Internet Archive.
- The screenshot run passed and its images were inspected. The Gradle memory test lists the new
  workflow.

## Validation and next steps

- Local script tests: 87 run, 4 skipped, 0 failures.
- VK's embed page is VK's own app now: an embed that lists no file opens the API path, and its
  files on `vk6-13.vkuser.net` are read. The next step is the phone proof: download and playback on
  the owner's three links, then the promotion runs. Until then VK is not passed.
- The ended-live size estimate is the open question in `docs/PLAN.md`: the sheet counts the files'
  advertised bitrates over the length, the download row counts the pieces served.
- VK's embed page now loads a JavaScript app with no media addresses, so the embed read is blank on
  every link. The API path answered for the owner's three links; one of them keeps all its files on
  `vk6-13.vkuser.net`. YouTube refuses this machine's lookups with a bot check, so the ended-live
  numbers still have to come from the phone.
