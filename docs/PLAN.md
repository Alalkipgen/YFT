# Plan

What is open now: where things stand, the owner's open decisions, the backlog and the risks. Decisions already made are in [DECISIONS.md](DECISIONS.md). Tests and runbooks are in
[TESTING.md](TESTING.md). The steps for a release are in [RELEASE.md](RELEASE.md).

## Where things stand

- 1.0.1 is released (2026-10-29) and 1.0.2 is released (2026-11-03).
- `main` holds 1.0.2 and the README screenshots change. Work continues on `work/phase-v102-easy`,
  and PR #16 is open for 1.0.3.
- Weak-answer fill and Instagram's best-first order are enabled in release and previews by the
  owner. Facebook, Instagram and X passed promotion on the census build.
- VK passed three owner-reported promotion census runs for slots 1–6. See [SESSION_STATE.md](SESSION_STATE.md)
  for the results and recipe note. The VK fixes, size and Wikimedia fixes, download recovery after
  app closure, and Android 9's save path in CI are on `work/phase-v102-easy`. The owner reports
  the requested phone tests passed and approved merge and release as 1.0.3. Keep the existing
  release configuration; verify CI and the signed release draft before publishing.

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
- **Merge fallback:** Android's MediaMuxer path with an AAC track that has an edit list is not yet
  tested on a phone.
- **VK fresh link:** a 400 on the first request shows "Failed · Unexpected HTTP status". Fix it, or
  accept it.
- **Ended YouTube live:** a file up to 2 minutes short is saved, with a Details line. Keep this
  limit, or change it.
- **Lookup details wording:** the line `master: <site> ladder after NO_MEDIA_FOUND` (cosmetic).
- **VK value heal:** six qualities came through the heal in one run. Keep watching it.
- **Two servers of one video:** jav.guru's Stream VI and Stream VO are separate videos to YFT
  because equal length alone no longer groups files, so VO's sheet shows VO's own 480p only.
  1.0.2 showed VI's 1080p and 720p beside it. Group two finished playlists of one page whose
  lengths agree, or keep them apart (the rule that keeps an ad of the same length out)?
- **Javtiful:** the page's video is found only after its ad is skipped. Accept, or look further.
- **Address pill at large text:** it overlapped once. Send a screenshot if it shows again.

## Backlog

Not planned for the next release. Advice in brackets.

- A Download button inside YouTube's page (not now).
- YFT's own YouTube page (no).
- Facebook formats from the browser page (only if needed).
- TikTok photo posts. TikTok without the watermark.
- Android 14+ user-initiated data transfer jobs for downloads. Downloads stay in a foreground
  service by decision ([DECISIONS.md](DECISIONS.md)); Android 15 caps a `dataSync` service at 6
  hours in 24, and the service reports that as a notice. [A phone run of a download longer than 6
  hours says whether the notice is enough.]
- Fast merge (Matroska stream copy) for 2K and 4K.
- Background playback in Library.
- A shared ad host list.
- A private tab, and allowing pop-ups.
- Adult-site adapters (only if the owner chooses).
- VAST ads found by a document-start script.
- Cronet as the HTTP stack (not now).
- TikTok's app API (don't).
- The page player's PoToken for YouTube.
- X protected posts. Instagram stories.
- X's guest-token API.
- A VK recipe that carries the web client's values.
- A daily VK canary in CI.
- Instagram's `doc_id`: look again if `VALUE_REJECTED` shows.
- Size estimates for peak-only HLS: finished playlists now have the Finished stream size sample
  (48 pieces, host-measured within 4.9% of every piece on Roshy, jav.guru VO and JW Tears of
  Steel). Pornhub answers the host with an age check, so whether its servers honour the one-byte
  range is seen only on the phone. A server that ignores the range keeps "Size unknown". Ended
  YouTube lives have the Ended live size sample (owner's choice, 2026-11-08). Time and data of
  both samples on a phone are not measured.
- During a download whose total is "(estimated)", the progress total read ~74 MB and ~142 MB for
  files of 98 and 172 MB (Preview 213, ended YouTube lives). It is measured from the pieces done;
  the sheet's sample was closer. Not changed yet.
- Investigate reported single quality and high Found counts using generic player/manifest evidence.
  Observed thumbnail players are not confirmed main ladders; do not infer every extra item is an ad
  or merge distinct video items without identity evidence. A download-listener-only candidate
  carries no ad-address sign. Adding one blindly can hide a file the page names as its own after
  observations merge; resolve conflicting provenance before changing this path.
  The WebView request hook has no reliable initiating frame or element ID. A Referer origin and
  close request timing are clues, not proof that a candidate is the player or an ad. The ad-break
  tracker uses these clues and can mark an unrelated CDN file; the normal browser does not call its
  VAST-answer method. Equal duration alone no longer groups files. When a browser manifest read
  succeeds, an HLS master can group with exact variant addresses it names. Reads are limited to
  candidates without a duration and a page-wide cap, so other qualities may still appear separately.
  Fresh-link retry now uses the Same-file check: the same host and path, with known signing and
  expiry values ignored and content values compared. Length alone never picks a file. A file that
  moves to another path, or a path shared by several page files, now stops with retry guidance
  instead of renewing. Unknown query names count only as a path match. After an ad, the page's
  player video is still chosen by the page-stated length; that path is unproven on real sites.
  The Stream piece filter keeps the pieces a read playlist names out of the Found list, and a
  playlist with no end moves under "Other videos on this page" and is never the player's video
  after an ad. A master whose read was refused (403) still cannot group its qualities, and the
  Found count on a phone is not checked yet.
- The owner reports slower downloads in Preview than in v1.0.2 Release. Host checks in TESTING
  time sheet lookup, transfer and finalization apart. Average-rate HLS sheets made five more
  playlist reads; Playlist read-ahead cut that fixture from about 1,060 to 460 ms (Release: 306 ms
  without estimates). Host transfer and finalization were not slower. The phone cause is unknown:
  the source, quality and phone timings are needed.

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
