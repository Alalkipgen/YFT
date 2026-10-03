# ADR-005: Ship a YouTube adapter by owner decision

- Status: Accepted for Phase 5E; supersedes [ADR-004](ADR-004-youtube-adapter.md). Item 4 and the
  bot-check consequence are superseded by [ADR-006](ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03).
- Date: 2026-10-02
- Decided by: the project owner

## Context

ADR-004 recorded YouTube as a blocker: stream URLs need transforms from YouTube's own player
script, the browser clients increasingly need per-session proof-of-origin (PO) tokens, behavior
cannot be pinned by offline fixtures, and distribution exposure is the project's highest. The
analysis remains accurate and is kept as the risk record in
[YOUTUBE_RISK_REVIEW.md](../YOUTUBE_RISK_REVIEW.md).

The owner has decided that YFT must download YouTube videos for offline viewing, comparable to
Snaptube or VidMate, and that YFT is distributed through GitHub only. The owner accepts the
maintenance cadence and the distribution exposure described in the risk review. This ADR records
that decision and the constraints the implementation keeps; it does not re-argue it.

## Decision

Ship a `youtube` adapter in `:extractor-sites`, enabled by default behind
`BuildConfig.YOUTUBE_ADAPTER_ENABLED` so a release can switch it off without code changes.

1. **Scope.** Single-video pages only: `youtube.com/watch?v=`, `/shorts/`, `/embed/`, `/live/`,
   `/v/`, `youtu.be/`, on `www.`, `m.`, `music.` and `youtube-nocookie.com`. Channels, playlists
   and search results are not claimed.
2. **Embedded player first.** The adapter reads the canonical watch page with the user's own
   browser session, then asks YouTube's embedded-player client (`WEB_EMBEDDED_PLAYER`) without the
   user's cookie, because it is the one browser client YouTube serves stream URLs to without a PO
   token. The page's own client (`WEB` or `MWEB`, as the page reports itself) is asked second with
   the user's session; its verdict about a video (private, sign-in, region, removed) is
   authoritative, but its links may be refused at download time because YFT has no PO token.
3. **Bundled solver, sandboxed.** Signature and `n`-parameter transforms are computed by the
   unmodified [yt-dlp ejs](https://github.com/yt-dlp/ejs) solver 0.8.0, bundled as app assets and
   pinned by SHA-256 (see [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)). The solver runs
   YouTube's player script, fetched only from `https://www.youtube.com/s/player/<id>/…/base.js`,
   inside a fresh offscreen WebView per run that:
   - loads only pages and scripts the app serves itself from the reserved
     `appassets.androidplatform.net` host and refuses every other request with HTTP 403;
   - carries a content security policy that allows no other origin, and runs the solver in a
     dedicated blob worker;
   - has file and content access, DOM storage, third-party cookies and navigation disabled and
     never sees the browser's cookies or storage;
   - returns one size-limited string through one bridge method, and is destroyed after the run
     or on timeout (45 s).
   Results are checked for plausibility before use, and a failed solve fails the candidate with
   `PLAYER_SCRIPT_REQUIRED` instead of returning an unusable link.
4. **What YFT does not do.** No PO-token generation, no `SAPISIDHASH` or other account
   authorization headers, no content-gate acknowledgements (age or sensitivity checks stay
   enforced and fail with their own reason), no impersonation of the Android, iOS or TV apps or of
   any device, no DRM circumvention, no SABR/UMP streaming, and no remote code other than
   YouTube's own player script inside the sandbox above.
   *Superseded on 2026-10-03 by ADR-006: the owner allows device clients, PO tokens, SABR and
   bot-check workarounds; DRM, paid, private and age-restricted content stay out of scope.*
5. **Credential scope.** The user's cookie is sent to `www.youtube.com` page and page-client
   requests only. It is never sent to the embedded client, to `googlevideo.com` stream hosts, or
   across sites: `OkHttpExtractorClient` drops `Cookie` and `Authorization` on any redirect hop
   that leaves the original site.
6. **Offered streams.** Progressive MP4 streams that carry audio (usually up to 360p), highest
   first, plus one AAC M4A audio-only stream. Adaptive video-only streams are not offered until the
   download pipeline can pair them with audio.

## Consequences

- The Phase 5 rule against remote executable extractor code is relaxed for exactly one case:
  YouTube's player script, executed only inside the sandbox above. The solver code itself is
  bundled and signed with the APK, never downloaded.
- Offline fixtures pin the adapter's parsing, verdict ordering, cipher handling and solver
  protocol, but cannot pin YouTube's live behavior. The adapter is the project's least stable
  component, and breakage is expected whenever YouTube changes its player, client requirements
  or PO-token enforcement. `scripts/verify-youtube-solver.mjs` checks the bundled solver against
  public vectors and the current live player; `scripts/update-youtube-solver.sh` refreshes it.
- Videos whose owners disable embedding fall back to the page client and may fail at download
  time with HTTP 403. Some networks receive "Sign in to confirm you're not a bot" for every
  request; YFT reports that as sign-in required and does not work around it. (Since T08 it is
  reported as a bot check; ADR-006 allows working around it.)
- `ShippedAdaptersTest` pins the shipped adapter set as `tiktok`, `facebook`, `vimeo`, `youtube`
  and pins that channel, playlist and search URLs stay unclaimed.
- Distribution stays GitHub-only. Google Play's Device and Network Abuse policy would reject this
  component; publishing on Play would require a new ADR and building with the flag set to
  `false`.
