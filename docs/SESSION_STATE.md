# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- 1.0.2 is published (2026-11-03). Work continues on `work/phase-v102-easy`, and no pull request is
  open.
- VK: the embed page loads VK's own app and carries no media addresses, so an embed that lists no
  file opens the API path (`vk D`). The API answered for the owner's three links; the fourth, a
  clip, is one VK says it does not play. Its files on `vk6-13.vkuser.net` are read by the rule
  already there.
- Sizes: a row shows a size only when the source states one, with `~` when the source marks it an
  estimate; a size YFT would have to guess is not shown and the row reads "Size unknown". A
  progress total that is an estimate says "(estimated)" under the bar. The ended live that read
  ~515 MB for a 371 MB file is one of the rows that now reads "Size unknown".

## Validation and next steps

- Local: `:app:testDebugUnitTest` and `:extractor-sites:test` pass; script tests 87 run, 4 skipped,
  0 failures.
- Next is the phone run: the checklist in `docs/TESTING.md` (VK's three links, the ended live, the
  size lines). Both fixes stay unpassed until it passes.
- Then VK's promotion runs: three full census runs on a census APK built from the final code, at
  least 6 hours apart, the first and the last at least 24 hours apart.
- YouTube refuses this machine's lookups with a bot check, so the ended-live numbers have to come
  from the phone.
- Open backlog findings: the Wikimedia page address ends in `.webm` so the page itself can be read
  as a file (needs the README run's Details line); Android 9's save path needs an Android 9 device
  or emulator; a resume after Android killed the app needs a phone run that kills it.
