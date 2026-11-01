# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- Branch `work/phase-v102-easy`, last code commit `811d86e2`. Version 1.0.2 is not released yet.
  Its steps are in `docs/PLAN.md`.
- The cleanup of 2026-11-01 is finished. The docs were rewritten and 51 old Markdown files were
  deleted (`docs/GLOSSARY.md`, Old documents, says where their facts are now; their old text is at
  commit `631d05d4`). Code comments in every module now use the feature names in the glossary.
- Code did not change. Since `854bee67` the only code changes are two strings of the weekly site
  check (they point to its runbook in `docs/TESTING.md`) and two printed lines of the own-solver
  canary scripts. The check in `docs/PLAN.md` (step 3) ends "867 code files changed only in
  comments; 0 failed."
- So the census APK `yft-vk-census-854bee67.apk` runs the final code. Only its weekly check prints
  the old walled note text.

## Tests

- CI on `811d86e2`: Work-branch checkpoint validation, Minified build check, Preview APK and
  Android emulator smoke all passed. Checkpoint validation ran the tests that read the docs.
- Toolkit drift check: "256 copied symbols checked against HEAD: 2 drifted" (TikTok `check`,
  X `extract`), the same as before the cleanup.

## Left as they are

- Old work labels in comments of the `.mjs` and `.js` files, of the five executable scripts in
  `scripts/` (a push through the GitHub connection would drop their mode) and of the ten YouTube
  files the drift check compares (main's five and Master's copies).
- Step names in `.github/workflows/master-optin-debug-apk.yml`; a script test reads one of them.
- The `source` field of `app/src/androidTest/assets/parity/parity-urls.json` names an old doc.

## Next steps

1. The census and promotion runs with `yft-vk-census-854bee67.apk` (`docs/PLAN.md`, steps 3-4).
2. Then the rest of `docs/PLAN.md`, "Steps to release 1.0.2".
