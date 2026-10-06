# YouTube Adapter Risk Review (Phase 5D)

- Status: **Overridden by the owner — a YouTube adapter ships (Phase 5E,
  [ADR-005](decisions/ADR-005-youtube-owner-override.md))**. This review is kept unchanged below
  as the project's risk record. Since 2026-10-03 the owner also allows device clients, PO tokens
  and bot-check workarounds ([ADR-006](decisions/ADR-006-owner-override-any-working-method.md));
  the first containment table below describes the adapter before T16, and the T16 section after
  it describes the current one.
- Reviewed: 2026-10-02
- Scope: whether `:extractor-sites` should contain a `youtube` adapter in Phase 5
- Phase 5 required this review to either produce an adapter or report a blocker honestly. This is
  the blocker report.

## Owner override

On 2026-10-02 the project owner decided that YFT must download YouTube videos for offline
viewing, comparable to Snaptube or VidMate, with distribution on GitHub only, and accepted the
maintenance and distribution exposure this review describes. ADR-005 records the decision and
supersedes ADR-004. The decision is final for this project; the analysis below is not withdrawn,
and every risk in it still applies.

Against the reopening conditions at the end of this review:

1. **Not met.** The adapter executes YouTube's own player script, and it presents itself to
   YouTube as YouTube's embedded web player. It does not impersonate a phone app, a TV or any
   device, and it always sends the user's own user agent.
2. **Partly met.** Committed fixtures pin URL matching, page parsing, verdict ordering, cipher
   handling and the solver protocol. They cannot pin YouTube's live behavior, so breakage is
   detected by `scripts/verify-youtube-solver.mjs` against the live player and by users, not by
   the test suite.
3. **Met.** The owner accepted the release cadence and the distribution exposure in ADR-005.

How the implementation contains the risks that remain:

| Risk from this review | Containment in Phase 5E |
| --- | --- |
| Remote executable code (item 1) | The solver is bundled, unmodified and SHA-256 pinned. Only YouTube's player script is fetched, only from `www.youtube.com/s/player/…/base.js`, and it runs only in a fresh offscreen WebView with app-served pages, a strict CSP, a blob worker, no cookies, storage, files or navigation, and one size-limited string output. Implausible results are discarded |
| PO tokens (item 2) | No token is generated. The embedded-player client, which YouTube currently serves without a token, is asked first and without the user's cookie. The page client's links can be refused at download time; videos that disallow embedding are the ones affected |
| SABR/UMP (item 3) | Not implemented. Only progressive MP4 streams with audio (usually up to 360p) and one AAC M4A audio stream are offered |
| Client impersonation (item 4) | Browser clients only (`WEB_EMBEDDED_PLAYER`, and `WEB`/`MWEB` as the page reports itself). No Android, iOS or TV client, no `SAPISIDHASH`, no content-gate acknowledgement: age, private, region and DRM gates fail with their own reason |
| Maintenance cadence | Client identifiers live in one file (`YouTubeClientProfile.kt`); `scripts/update-youtube-solver.sh` refreshes the solver from its pinned wheel; `BuildConfig.YOUTUBE_ADAPTER_ENABLED` switches the adapter off without code changes |
| Breakage invisible to fixtures | `scripts/verify-youtube-solver.mjs` runs the bundled solver against public vectors and today's live player; failures surface as `PLAYER_SCRIPT_REQUIRED`, `LOGIN_REQUIRED` or `RATE_LIMITED`, not as a generic error |
| Credential exposure | The user's cookie goes only to `www.youtube.com` page and page-client requests, never to the embedded client or `googlevideo.com`, and is dropped on any redirect that leaves the site |
| Distribution and legal exposure | Accepted by the owner. GitHub-only distribution; the Play policy conflict stands and a Play build would need the flag off and a new ADR. The 2020 youtube-dl DMCA precedent applies to the GitHub repository and releases |
| Authorized-media scope (`RISKS.md`) | Unchanged in principle: YFT does not unlock private, age-gated, region-blocked, paid or DRM content. Whether a given download is permitted remains the user's responsibility |

Observed while building Phase 5E from a datacenter network on 2026-10-02: the `WEB` and `MWEB`
clients answered "Sign in to confirm you're not a bot" and the embedded client answered "This
video is unavailable" for every video tried, including with the reference client versions. The
end-to-end stream path is therefore verified only against fixtures and the solver against live
player scripts, not against live stream URLs; it must be checked on a phone on a residential or
mobile network.

## T16 containment (ADR-006, 2026-10-03)

T16 implements the owner's D2 = A + B + C as one chain (order and binding rules:
[ADR-006 § Implementation](decisions/ADR-006-owner-override-any-working-method.md#implementation-t16-2026-10-03)).
What changed against the table above, and how each new risk is contained:

| Risk | Containment in T16 |
| --- | --- |
| Client impersonation (item 4, now allowed) | `VISIONOS` and `ANDROID` device clients, values copied from yt-dlp 2026.08.19 and kept in `YouTubeClientProfile.kt` only. They never carry the user's cookie, authorization or account identity; only the page's own client does. `SAPISIDHASH` is computed only from the browser session's own cookie, for the page's client |
| PO tokens (item 2, now generated) | YouTube's BotGuard runs in a fresh offscreen WebView on an app-served origin with a strict CSP (`connect-src 'self'`), no cookies, storage, files or navigation, and only the challenge's inline interpreter (no remote script). The app itself calls the two attestation endpoints, without cookies or redirects. Tokens, bindings, visitor data and the attestation key never reach logs, lookup details, fixtures or commits (the checkpoint secret scanner rejects Google API keys) |
| Remote executable code (item 1) | Unchanged for the solver. BotGuard's challenge program is remote code by nature: it runs only inside the sandboxed page above, with a 30-second step timeout and a 60-second budget per mint, and its only output is a bounded token string checked against a strict pattern |
| Bot checks | Not bypassed headlessly when YouTube decides by network: from the sandbox's data-centre IP a bot-checked video stayed bot-checked for every client even with a minted token. Option C sends the user to YFT's browser, where the lookup runs again (by itself once the player starts streaming, or with **Try again**) with the browser's own YouTube session |
| Credential exposure | The browser's YouTube cookie is kept only from YouTube's own requests; requests of other sites and requests without a cookie never replace it. Media addresses carry only the `pot=` token, never a cookie |
| Breakage invisible to fixtures | `scripts/verify-youtube-potoken.mjs` mints a token with today's live BotGuard challenge in Chromium on the app's origin and reports requests the page tried (must be 0); `scripts/verify-youtube-solver.mjs` still checks the solver; lookup details name each client's verdict and the token state (minted, no host, unavailable, failed) |
| Age, private, paid and DRM gates | Unchanged: a definite verdict from the page's client ends the lookup, and an age check from a fallback client clears the fallbacks' results |

## P14: visionOS first (2026-10-05)

The lookup now asks `VISIONOS` before the watch page and stops there when the answer is complete
([ADR-006 § P14](decisions/ADR-006-owner-override-any-working-method.md#p14-visionos-first-2026-10-05)).

| Risk | Containment in P14 |
| --- | --- |
| A gate decided without the page | The visionOS answer counts only when it is playable and complete; every refusal (sign-in, bot or age check, private, region, DRM, live, unplayable) and every incomplete answer runs the T16 chain unchanged, where the page's verdict is final. An age check from visionOS is never answered by another client |
| Session exposure | The first request carries no cookie, authorization, visitor data or page API key; the user's session is still sent only by the page's own client, which runs only after a refused or incomplete visionOS answer |
| More requests to YouTube | None for a complete answer (one request instead of up to four); otherwise visionOS is asked once, and the chain reuses that answer instead of asking again |
| Breakage invisible to fixtures | Lookup details say "visionOS first: complete, no watch page" or why the page was read; the live check counts requests and bytes per video |

## P22: every quality — client order (2026-10-06)

The owner's phone showed only 360p, an M4A without a size and MP3 for `4pKpLX9NG_k` (finding R3).
Cause: visionOS was asked without visitor data and answered with the bot check; the chain reused
that refusal, asked `ANDROID` (only the progressive 360p file `itag 18`, no size; its separate
formats only through SABR) and stopped there because a video with sound counted as enough, so
the page's own client — the one with the BotGuard token and the player script — was never
collected. The chain now runs in this order, each step only while the lookup lacks a separate
video merged with its audio track and the audio track itself:

1. `VISIONOS` before the page (P14, unchanged); a complete answer is the whole lookup.
2. The watch page.
3. `VISIONOS` again when its first answer refused the request (bot check, a sign-in that is not
   an age check, no formats or SABR only) and the page gave visitor data: the same request with
   that visitor data in the client context and as `X-Goog-Visitor-Id`, at the endpoint without
   the page's key. An answer about the video (formats, an age check, private) is used as it is;
   a request that failed before visionOS answered is not asked again (the HTTP client already
   retried it where a retry helps, P10).
4. The embedded player (no cookie).
5. The page's own client (`WEB` or `MWEB`): its inline answer, or asked with the user's session,
   the BotGuard proof-of-origin token and the player script.
6. `MWEB`, when the page was the desktop site.
7. `ANDROID` last; its 360p file stays a row only when no client streams 360p separately.

Every row is one quality: 144p–1080p AVC merged with the AAC track (144p and 240p are new in
P22), 2K/4K VP9 with Opus, each with YouTube's own `contentLength` of both files; a merged row
replaces a progressive file of its quality, never the other way round. The M4A row is the AAC
track `itag 140` with its size, which MP3 converts.

Order evidence — yt-dlp `master` as read on 2026-10-06 (`yt_dlp/extractor/youtube/_base.py`,
`_video.py`): `_DEFAULT_CLIENTS = ('visionos', 'web')`, `('visionos',)` without a JavaScript
runtime and `('web_embedded', 'tv_downgraded', 'web')` with cookies. yt-dlp reads the watch page
first and sends its visitor data with every client as `X-Goog-Visitor-Id`, with no `key` query
parameter. `visionos` needs no player script and has no proof-of-origin policy; `web` and
`mweb` need a GVS token for HTTPS and DASH formats; `web_embedded` has none (the default policy);
`android` needs one for its HTTPS and DASH formats (unless it has a player token) and is not a
default client. YFT therefore keeps visionOS first and asks it again with visitor data as yt-dlp
does, keeps the embedded player before the page client (no token mint and no cookie when the
owner allows embedding, then yt-dlp's `web` step with the session, token and script) and moves
`ANDROID` to the end.

| Risk | Containment in P22 |
| --- | --- |
| Visitor data | Read from the watch page and held for one lookup only; sent only to YouTube's player endpoint, as the page itself does. Never in lookup details, logs, fixtures, commits or the lookup cache key; a test checks the details hold none |
| More requests to YouTube | At most one more: visionOS again, only after a refusal and only when the page gave visitor data. A failed request is not asked again, and the Android app is asked only when everything before it was incomplete. A complete visionOS-first answer is still one request |
| Age, private, paid and DRM gates | Unchanged: the page's verdict about the video ends the lookup before visionOS is asked again (test: an age-restricted video stays `LOGIN_REQUIRED`); an age check from visionOS again or `ANDROID` leaves only the page's own clients and drops what the fallback clients offered |
| Session exposure | visionOS asked again carries no cookie, authorization or page key, like the first request; only the page's own clients carry the session |
| Breakage invisible to fixtures | Details name each step: "client VISIONOS again, with visitor data: …", "…: N formats via player script", "client ANDROID: N adaptive formats only through SABR" |

Live from the sandbox's data-centre IP (2026-10-06, temporary script and the adapter itself, no
session): `dQw4w9WgXcQ` → visionOS first complete, 1 request, 9 rows 2160p–144p plus Audio with
sizes (360p 11.8 MB, 720p 29.9 MB, 1080p 84.4 MB, M4A 3.4 MB). `8Mw9bwLTQFk` and `jNQXAC9IVRw`
→ the bot check from visionOS without and with visitor data (temporary script). `4pKpLX9NG_k` →
the bot check from every client the adapter asked, in the new order: visionOS first, the page's
own `WEB` answer, visionOS again with visitor data, the mobile site and `ANDROID` (embedded:
error 152-18). The sandbox's IP is flagged, so the owner's phone Details are the proof there.

## Original Phase 5D review

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

## What shipped in Phase 5D (superseded by ADR-005)

- No `youtube` package exists in `:extractor-sites`, and `ShippedAdaptersTest` asserts both that
  the shipped adapter set is exactly `tiktok`, `facebook`, `vimeo` and that `youtube.com` and
  `youtu.be` links are unclaimed, so an adapter cannot be added without that test failing and
  this document being revisited.
- YouTube links keep working exactly as far as the generic pipeline reaches on its own: whatever
  the user's browser loads is observed, and DRM-protected or signature-gated streams are rejected
  with the normal failure reasons. No YouTube-specific parsing, no client impersonation and no
  remote code execution are involved.
- [SUPPORT_MATRIX.md](SUPPORT_MATRIX.md) records the YouTube row as Blocked and points here.

## What would reopen the decision (see Owner override for the outcome)

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
