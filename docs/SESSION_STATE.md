# Session state

Overwrite this file at each checkpoint (40 lines at most). Never append. History is in git.

## Now

- Branch `work/phase-v102-easy`; 1.0.2 is not released. The owner chose to turn on weak-answer
  fill and Instagram's best-first order in the release and both previews. `releaseMaster` now
  enables `SOCIAL_FILL`; debug builds retain their existing flag behavior.
- Facebook, Instagram and X passed three promotion runs on the earlier census build. VK was
  dropped from those runs and has not passed. Enabling fill includes VK, so do not publish on
  the strength of the earlier results. VK's blank embed and moved clip host need fixes and proof.
- The release commit sets version 1.0.2 / 1000200 and adds `docs/release/1.0.2.md`. The
  changelog remains undated until publication.

## Validation and next steps

- The pre-change comment-only check passed: 867 code files changed only in comments; 0 failed.
- Script tests: `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` passed (87,
  4 skipped). Android builds require JDK 17 and the Android SDK; this computer has neither
  configured. Check CI on this commit; CI must validate and build the previews.
- The owner requested release. Build a census APK from the final code, resolve VK's known failures,
  and run the required
  promotion checks. Do the phone checklist on both previews of the final release commit. Follow
  `docs/PLAN.md`; merging and publishing require the owner's approval.
