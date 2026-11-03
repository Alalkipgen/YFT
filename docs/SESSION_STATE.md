# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- 1.0.2 is published (2026-11-03, tag `v1.0.2`) and merged to `main`. Branch
  `work/phase-v102-easy` carries the work after it.
- The README is being rewritten for users, with real app screenshots in `docs/screenshots/`.
  README screenshots (`readme-screenshots.yml`, `scripts/readme-screenshots.sh`,
  `ReadmeScreenshotsTest`) takes them on a Pixel 6-sized emulator and commits them to the branch.

## Validation and next steps

- Check the README screenshots run on this push and look at each picture before the README
  links them. A missing picture fails the run and commits nothing.
- Then push the new `README.md`, open a pull request to `main` and merge it only with the owner's
  approval and green CI.
