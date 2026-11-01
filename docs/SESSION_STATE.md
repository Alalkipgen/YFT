# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- Branch `work/phase-v102-easy`. Version 1.0.2 is not released yet. Its steps are in `docs/PLAN.md`.
- The docs were cleaned up on 2026-11-01. The current docs are `README.md`, `AGENTS.md`,
  `CHANGELOG.md`, this file, `extractor-master/README.md`, `extractor-master-android/README.md`
  and, in `docs/`: `ARCHITECTURE.md`, `DECISIONS.md`, `DESIGN.md`, `GLOSSARY.md`, `PLAN.md`,
  `RELEASE.md`, `SUPPORT_MATRIX.md`, `TESTING.md` and `THIRD_PARTY_NOTICES.md`.
- 51 old Markdown files were deleted. `docs/GLOSSARY.md` (Old documents) says where each one's
  facts are now. Their old text is in git, at commit `631d05d4`.
- The weekly site check's notes now point to `docs/TESTING.md` (Weekly site check runbook).
  `SiteWeeklyTest` reads that section, so keep its step lines as they are.
- Other agents were still removing old names from code comments on this branch.

## Tests

- Passed on `6c486060` (the docs before this file): `SiteWeeklyTest` (9 tests),
  `ReleaseNotesTest` (13) and `OpenSourceNoticesTest` (3). No failures.
- No CI ran for the docs commits: they were docs-only or marked `[skip ci]`.

## Open items

- Run "Work-branch checkpoint validation" by hand (Actions → Run workflow). Tests read
  `docs/TESTING.md`, and no workflow started for these pushes.
- Some code comments point engine features (YouTube ladder, Weak-answer fill, YouTube backup route,
  Client version source, Row check) to `docs/DESIGN.md`. That file is the UI design doc. They
  should point to `docs/ARCHITECTURE.md` or `docs/GLOSSARY.md`.
- Printed text still names deleted docs: `scripts/own-solver-canary.mjs` and `.sh` (the runbook is
  now `docs/TESTING.md`, Own-solver canary) and the `source` field of
  `app/src/androidTest/assets/parity/parity-urls.json`.
- The `CHANGELOG.md` header and `gradle.properties` still name the deleted
  `docs/IN_APP_UPDATE_PLAN.md`. The version code rule is now in `docs/RELEASE.md`.

## Next steps

1. Run checkpoint validation and check that it passes.
2. Follow `docs/PLAN.md` (Steps to release 1.0.2).
