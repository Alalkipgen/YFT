# Plan

What is open now: the steps to 1.0.2, the owner's open decisions, known issues, the backlog and the
risks. Decisions already made are in [DECISIONS.md](DECISIONS.md). Tests and runbooks are in
[TESTING.md](TESTING.md).

## Where things stand

- 1.0.1 is released (2026-10-29).
- 1.0.2 is built on `work/phase-v102-easy`. Its changes are in [CHANGELOG.md](../CHANGELOG.md)
  under `## [1.0.2]`.
- Main's readers and the Backup finder are in the release. The social ladders' weak-answer fill and
  best-first order are in owner test APKs only.

## Steps to release 1.0.2

1. Confirm CI is green on the last code commit. Commits marked `[skip ci]` start no workflow, so run
   "Work-branch checkpoint validation" by hand on `work/phase-v102-easy` (Actions → Run workflow).
2. Get the owner's answers to the open decisions below.
3. Build the census APK from the final head ([TESTING.md](TESTING.md), "Build the census APK"). The
   owner may reuse `yft-vk-census-854bee67.apk` if this passes:
   `python3 scripts/check-comment-only.py 854bee67 HEAD --allow scripts/check-comment-only.py --allow extractor-master/src/main/kotlin/com/alal/yft/extractor/master/social/SiteWeekly.kt --allow extractor-master/src/test/kotlin/com/alal/yft/extractor/master/social/SiteWeeklyTest.kt --allow scripts/own-solver-canary.sh --allow scripts/own-solver-canary.mjs`.
   That APK still prints the weekly check's old walled note text.
4. Promotion runs for Facebook, Instagram, VK and X. The rules are in
   [TESTING.md](TESTING.md), "Promotion runs".
5. The phone checklist on the two preview APKs ([TESTING.md](TESTING.md)).
6. The release commit: `yft.versionName=1.0.2` and `yft.versionCode=1000200` in
   `gradle.properties`; `docs/release/1.0.2.md` (like `1.0.1.md`, with a "From 1.0.1" part); and
   `## [1.0.2] - <date>` in `CHANGELOG.md`.
7. CI green. Run the Preview APK workflow and install both previews on the phone.
8. Open a pull request from `work/phase-v102-easy` to `main` and merge it with a merge commit.
9. Make the draft: push the tag `v1.0.2`, or Run workflow "Release draft" on `main` with publish
   unticked.
10. `bash scripts/device-smoke-test.sh --upgrade-from <1.0.1 APK> <1.0.2 APK>`, then the manual
    checklist in [RELEASE.md](RELEASE.md).
11. Publish: set `ALLOW_RELEASE` to `true`, Run workflow "Release draft" with publish ticked, then
    set it back.
12. Check that a phone with 1.0.1 offers 1.0.2 in Settings → About → Updates.
13. Start the next `CHANGELOG.md` section.

## Open owner decisions

- **Weak-answer fill in the release and previews:** only after promotion runs pass. Today
  `releaseMaster` sets `SOCIAL_FILL` to false. The same goes for the best-first order.
- **Weak-answer fill rules to confirm:**
  - sites: Facebook, Instagram and VK only;
  - budget: at most 10 s per lookup, 6 s of checks and 4 file checks; in YFT's browser only, not on
    Home;
  - a weak answer is one with no HD row or no sound row;
  - in the social backup trial a weak answer goes to fill;
  - Instagram: added rows must be the same item as main's;
  - owner test APKs get fill by default (`yft.socialFill` is true).
- **AV1 merges in the release:** turn on only after the owner's phone run of the AV1 test box.
- **DASH with an `http` BaseURL:** Android's cleartext rule refuses the preview, but the download
  works. Allow it for the preview, or leave it.
- **MP3 conversion speed:** decoding is the slow part. Choose the next step, or none.
- **Merge fallback:** Android's MediaMuxer path with an AAC track that has an edit list is not yet
  tested on a phone.
- **VK fresh link:** a 400 on the first request shows "Failed · Unexpected HTTP status". Fix it, or
  accept it.
- **Ended YouTube live:** a file up to 2 minutes short is saved, with a Details line. Keep this
  limit, or change it.
- **Lookup details wording:** the line `master: <site> ladder after NO_MEDIA_FOUND` (cosmetic).
- **VK value heal:** six qualities came through the heal in one run. Keep watching it.
- **Address pill at large text:** it overlapped once. Send a screenshot if it shows again.

## Known issues

- **Size estimate for an ended YouTube live is too high.** The Download sheet showed about 511 MB
  for 240p; the file was 371 MB. A normal 1080p video is right (about 346 MB shown, 346 MB saved).
  Fix after 1.0.2.

## Backlog

Not planned for 1.0.2. Advice in brackets.

- A Download button inside YouTube's page (not now).
- YFT's own YouTube page (no).
- Facebook formats from the browser page (only if needed).
- TikTok photo posts. TikTok without the watermark.
- Android 14+ user-initiated data transfer jobs for downloads.
- Fast merge (Matroska stream copy) for 2K and 4K.
- A rolling piece pool for HLS.
- Background playback in Library.
- A shared ad host list.
- A private tab, and allowing pop-ups.
- Adult-site adapters (only if the owner chooses).
- VAST ads found by a document-start script.
- Cronet as the HTTP stack (not now).
- TikTok's app API (don't).
- The page player's PoToken for YouTube.
- A test of the Android 9 save path (no scoped storage).
- X protected posts. Instagram stories.
- "All items" for one link (carousels, several videos).
- X's guest-token API.
- Fresh link renewal after the app's process was killed.
- A VK recipe that carries the web client's values.
- A daily VK canary in CI.
- Instagram's `doc_id`: look again if `VALUE_REJECTED` shows.

## Risks

- Sites change fast. Main's readers and the ladders need fixes; the signed recipe config fixes data
  without a new APK.
- Sign-in walls grow. Signed-out lookups may stop working on some networks.
- More steps mean more requests. The social limits' budgets keep the count down.
- YouTube can break the solver, add bot checks, or send takedowns.
- The lookup record sees only requests made through the shared HTTP client.
- The published recipe config (`defaults`, version 7) expires on 2027-10-23. Run the recipe publish
  workflow before then; otherwise apps go back to the bundled recipes.
- A change to shared code starts the promotion runs again for every site it touches.
- Cookies could leak into reports. Reports are redacted; keep them that way.
