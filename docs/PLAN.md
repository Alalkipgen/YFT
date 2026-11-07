# Plan

What is open now: where things stand, the owner's open decisions, known issues, the backlog and the
risks. Decisions already made are in [DECISIONS.md](DECISIONS.md). Tests and runbooks are in
[TESTING.md](TESTING.md). The steps for a release are in [RELEASE.md](RELEASE.md).

## Where things stand

- 1.0.1 is released (2026-10-29) and 1.0.2 is released (2026-11-03).
- `main` holds 1.0.2 and the README screenshots change. Work continues on `work/phase-v102-easy`,
  and no pull request is open.
- Weak-answer fill and Instagram's best-first order are enabled in release and previews by the
  owner. Facebook, Instagram and X passed promotion on the census build.
- VK has not passed promotion: its blank embed and moved clip host need fixes and fresh promotion
  runs before VK's fill is released. The embed page now loads a JavaScript app and carries no media
  addresses, so the embed read finds nothing on any link. The API path still answers, and its rules
  already take `vk{N}-{N}.vkuser.net`, where the moved clips' files sit.
- The fixes open now are VK's two, then the ended-live size estimate under known issues.

## Open owner decisions

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
  The sheet showed an estimate (`~`), so the file's own length was not known, and the
  estimate is bitrate × length.

## Backlog

Not planned for the next release. Advice in brackets.

- Investigate why the public Wikimedia Commons Big Buck Bunny page returned an invalid address
  during the README screenshots run.
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
