# YouTube Adapter Risk Review (Phase 5D)

- Status: **Blocked — no YouTube adapter ships**
- Reviewed: 2026-10-02
- Scope: whether `:extractor-sites` should contain a `youtube` adapter in Phase 5
- Phase 5 required this review to either produce an adapter or report a blocker honestly. This is
  the blocker report.

## Recommendation

Do not ship a YouTube adapter. YouTube cannot be extracted the way the three shipped adapters are
extracted — by reading a public page the user's own browser already loaded — and the workarounds
that do succeed break the Phase 5 rules, cannot be held stable by this project's release cadence,
and carry the highest distribution risk of any source in the support matrix.

YouTube pages keep falling through to the generic detector like any other unclaimed site. The app
claims no YouTube support in its UI, its README or any store listing.

## Why page-only extraction does not work

The three shipped adapters each read one public page and find a playable HTTPS URL inside it.
YouTube deliberately does not expose one:

1. **Signature and `n`-parameter transforms.** Stream URLs carry a `s`/`sig` value and an `n`
   query parameter that are only valid after being transformed by functions that live inside the
   site's own player JavaScript, which is re-minified without notice. Reading them means either
   downloading and executing remote player code at runtime, or shipping an interpreter that
   re-derives the current transform on every player change.
2. **Proof of Origin (PO) tokens.** YouTube increasingly binds playback URLs to a token produced
   by its own attestation JavaScript. Without a valid token, requests are answered with HTTP 403,
   truncated responses or "Sign in to confirm you're not a bot", and repeated attempts attract
   IP-level and account-level blocks. Minting a token requires running that JavaScript in a
   browser-like runtime per session.[^pot][^potfield]
3. **SABR/UMP delivery.** Playback is migrating to a server-driven adaptive protocol in which
   plain progressive and DASH URLs are simply not returned to many clients, so a downloader has to
   speak an undocumented, changing streaming protocol rather than fetch a file.[^pot]
4. **Client impersonation.** The remaining workarounds depend on presenting the app as a TV,
   embedded or mobile client with hardcoded keys and version strings. That is the same
   "impersonate an internal API" pattern already rejected for X in
   [SUPPORT_MATRIX.md](SUPPORT_MATRIX.md), and it is what the policies below describe as
   unauthorized access to a Google service.

Item 1 alone is decisive: the Phase 5 rule *"no unsigned remote executable extractor code"* bans
exactly the mechanism a working YouTube adapter needs.

## Maintenance constraints

- The reference implementations survive because a large contributor base ships a new release within
  days of every player change. YFT is a GitHub-first project with one maintainer; a broken YouTube
  extractor would stay broken for as long as that maintainer is unavailable, while the app would
  already be advertising support.
- Offline fixtures, which are how every other adapter is tested and kept honest, cannot pin this
  behavior. A captured player response freezes one cipher and one token format, so a green test
  suite would describe a protocol that no longer exists in production. The adapter would be the
  only one in the project whose tests cannot detect its own breakage.
- Every other adapter fails into a structured, user-readable reason. A token or SABR failure
  surfaces as a generic 403, so YouTube breakage would mostly be indistinguishable from a network
  problem for the user.

## Distribution constraints

- Google Play's Device and Network Abuse policy prohibits apps that download executable code from
  outside Play and apps that interfere with or access a Google service or property in an
  unauthorized manner; YouTube's own terms allow downloads only through features YouTube itself
  provides.[^play] A shipped YouTube downloader would therefore be the one component that makes the
  app unpublishable rather than merely unpublished.
- Distribution risk is not limited to Play. The RIAA's 2020 DMCA notice had youtube-dl removed from
  GitHub, and the repository returned only after a legal challenge to that reading of the
  DMCA.[^eff] YFT hosts its own releases on GitHub, so the same exposure applies to the
  distribution channel this project actually uses.
- [RISKS.md](RISKS.md) already commits this project to a "truthful support scope, authorized media
  only". General YouTube content is not authorized media, and claiming support for it would make
  that row untrue.

## What ships instead

- No `youtube` package exists in `:extractor-sites`, and `ShippedAdaptersTest` asserts both that
  the shipped adapter set is exactly `tiktok`, `facebook`, `vimeo` and that `youtube.com` and
  `youtu.be` links are unclaimed, so an adapter cannot be added without that test failing and
  this document being revisited.
- YouTube links keep working exactly as far as the generic pipeline reaches on its own: whatever
  the user's browser loads is observed, and DRM-protected or signature-gated streams are rejected
  with the normal failure reasons. No YouTube-specific parsing, no client impersonation and no
  remote code execution are involved.
- [SUPPORT_MATRIX.md](SUPPORT_MATRIX.md) records the YouTube row as Blocked and points here.

## What would reopen the decision

All three conditions would have to hold:

1. Extraction becomes possible without executing remote code and without impersonating another
   client — for example a documented, terms-permitted download mechanism, or an adapter restricted
   to the signed-in user's own uploads through the official YouTube Data API with OAuth.
2. The behavior can be pinned by committed fixtures, so breakage is detectable offline.
3. A maintainer accepts the release cadence and the distribution exposure in writing, as an ADR.

Until then this is a reported blocker, not a backlog item.

## Sources

[^play]: Google Play — Device and Network Abuse policy. <https://support.google.com/googleplay/android-developer/answer/16559646>
[^pot]: yt-dlp — PO Token Guide (proof-of-origin tokens, 403s and SABR streaming). <https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide>
[^potfield]: Field notes on PO tokens, geo blocks and cookies when extraction breaks. <https://renderio.dev/blogs/ytdlp-geo-block-pot-cookies>
[^eff]: EFF — "RIAA Abuses DMCA to Take Down Popular Tool for Downloading Online Video" (2020). <https://www.eff.org/deeplinks/2020/11/riaa-abuses-dmca-take-down-popular-tool-downloading-online-video>
