# Plan

What is open now: where things stand, the owner's open decisions, the backlog and the risks. Decisions already made are in [DECISIONS.md](DECISIONS.md). Tests and runbooks are in
[TESTING.md](TESTING.md). The steps for a release are in [RELEASE.md](RELEASE.md).

## Where things stand

- 1.0.1 is released (2026-10-29) and 1.0.2 is released (2026-11-03).
- `main` holds 1.0.2 and the README screenshots change. Work continues on `work/phase-v102-easy`,
  and no pull request is open.
- Weak-answer fill and Instagram's best-first order are enabled in release and previews by the
  owner. Facebook, Instagram and X passed promotion on the census build.
- VK has not passed promotion: it needs fresh promotion runs on a phone before its fill is
  released. The embed page loads VK's own app and carries no media addresses, so an embed that
  lists no file now opens the API path (`vk D`); its rules already take
  `vk{N}-{N}.vkuser.net`, where the moved clips' files sit. The API answered for the three links
  the owner sent; the fourth, a clip, is one VK says it does not play.
- The VK fixes, the size fix (the sheet's own bitrate guess and DASH/HLS peak rates no longer stand
  in for a size), the Wikimedia fixes (the page's own address is no file, the quoted `If-Range`, one
  player's sources as one video's qualities, a WebM's own header naming its row, and the preview
  message for a picture the phone cannot decode), the download that goes on after the app closed,
  and Android 9's save path in CI are on `work/phase-v102-easy`; they wait on a phone run, so none
  is passed. Next is the phone checklist in [TESTING.md](TESTING.md), then VK's promotion runs.

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
- Size estimates for ended YouTube live and peak-only HLS: investigate bounded samples, their
  error, data cost and latency before using them. YouTube's unsigned replay check was blocked;
  public HLS samples show positional bias and are not enough to establish a general accuracy or a
  reliable range. Byte-range sums describe input bytes, not the exact remuxed file.
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
  Fresh-link retry still uses similar duration or a query-stripped address as identity. Either can
  select a different video, since query parameters can identify content. It needs stronger identity
  evidence and regression tests. Keep unknowns retrievable and test with multiple videos.
- The owner reports slower downloads in Preview than in v1.0.2 Release. Separate sheet lookup,
  transfer and finalization before attributing a cause. Host checks in TESTING show an added HLS
  average-rate lookup delay in a controlled fixture, not the cause of the phone observation.

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
