# ADR-006: Owner override — use whatever works for public videos

- Status: Accepted. Supersedes item 4 ("What YFT does not do") of
  [ADR-005](ADR-005-youtube-owner-override.md) and the limits on client identities, proof-of-origin
  tokens, SABR and internal APIs in [YOUTUBE_RISK_REVIEW.md](../YOUTUBE_RISK_REVIEW.md),
  [SUPPORT_MATRIX.md](../SUPPORT_MATRIX.md) and the former `FIX_PLAN.md` §0.2 (Git history; now
  [FIX_ADD_PLAN.md](../FIX_ADD_PLAN.md) §0.2).
- Date: 2026-10-03
- Decided by: the project owner

## Context

During Phase 8 the owner asked whether YouTube's bot check can be handled. After the agent
answered that the project rules did not allow it, the owner changed the rules (owner's words):

> Rule & Policy ကိုပြင်လိုက် အရင် Agent လျှောက်ရေးထားတာ အဆင်ပြေဖို့ပဲလိုတယ် ဘယ်နည်းသုံးရသုံးရ
>
> A ကော B ကော လိုအပ်လို့ Option ၂ခု သုံးရမယ်ဆိုရင်လဲရတယ် ခဏ Play ပြီးမှ Download မယ့် Option
> လဲထည့်လို့ရတယ် Option ၃ ခုလုံး အဆင်ပြေသလိုသာလုပ်

In English: "Change the rules and policy. What the earlier agents wrote only has to make things
work — use whatever method works. If both A and B are needed, use both; an option that plays the
video for a moment and then downloads can be added too. Do all three, whatever works best."

The earlier rules (ADR-005 item 4) forbade proof-of-origin (PO) token generation, app or device
client identities and SABR, and reported YouTube's bot check without working around it. The field
test (former FIX_PLAN F4, Git history) and T08's lookup details show that most YouTube lookups fail under those rules.

## Decision

1. **The goal is that public videos download.** For content that anyone can watch without paying,
   adapters may use any technique that works, including:
   - app and device client identities (Android, iOS, visionOS, TV, VR and similar), with the
     identifiers copied from yt-dlp (Unlicense) and kept in one profile file per site;
   - proof-of-origin tokens and similar attestation minted by the site's own script (for YouTube:
     BotGuard) inside an offscreen WebView;
   - the user's own session from YFT's browser: cookies, visitor data and tokens seen while the
     page plays there;
   - working around bot checks on public videos (they are not account restrictions);
   - other delivery formats (HLS, SABR/UMP) and internal or guest APIs (for example for X) when a
     task needs them;
   - acknowledging content warnings that every viewer can click through.
2. **YouTube: D2 = A + B + C.** All three options are built and tried as one fallback chain,
   ordered by what works live: **B** device clients, then the embedded player, then the page's web
   client with **A**'s PO token; **C** — play the video for a moment in YFT's browser, then tap
   Download — supplies the browser's session and tokens when headless lookups meet a bot check.
   T16 implements the chain and records the client order and the yt-dlp version it copied.
3. **Limits that stay.** No DRM circumvention (Widevine, PlayReady, FairPlay). No paid,
   members-only or rented content without the user's own access, and no access to other people's
   private content. Age-restricted videos still need the user's own signed-in session in YFT's
   browser. Adapters never sign in and never ask for or store passwords. Cookies, tokens, visitor
   data and signed media URLs are never logged, printed, committed or put into fixtures. Licences
   are respected: GPL code (for example NewPipe's) is reference only for the MIT-licensed YFT;
   MIT, Apache-2.0 and Unlicense code may be used with a notice in `THIRD_PARTY_NOTICES.md`.
4. **Distribution stays GitHub-only.** Google Play's policies would reject these techniques.

## Consequences

- YouTube support becomes more capable and more fragile: client versions and BotGuard change
  often. Profiles are refreshed from yt-dlp and verified live; failures still surface through
  T08's honest messages and Copy details.
- The owner accepts the extra maintenance and the legal and distribution exposure (the sites'
  terms of service, possible takedown notices against the repository).
- Tests keep pinning behaviour with sanitized fixtures, and site tasks keep their live checks.

## Implementation (T16, 2026-10-03)

The YouTube adapter (`YouTubeExtractor.kt`) asks YouTube's player endpoint as one chain. Every
client identifier lives in `YouTubeClientProfile.kt`; the device values were copied from yt-dlp
2026.08.19 (`INNERTUBE_CLIENTS`, recorded as `YouTubeClientProfiles.DEVICE_VALUES_SOURCE`).

1. **Watch page.** One GET of the watch page (with the `bpctr`/`has_verified` parameters yt-dlp
   uses to pass content warnings every viewer can click through). Its inline player response, or
   else one request as the page's own client, gives the first verdict. A definite verdict about
   the video (private, removed, age-restricted, region-blocked, DRM) ends the lookup.
2. **Option B — device clients**, without the user's cookie: `VISIONOS` 1.02, then `ANDROID`
   21.26.364, each with its app's user agent and device fields. They stop as soon as there is a
   video with sound and an audio track. Their streams carry direct addresses and need no player
   script.
3. **Embedded player** (`WEB_EMBEDDED_PLAYER`, without the cookie) until there is any video.
   An age check from any fallback client clears what the fallbacks found and leaves the answer to
   the user's own session.
4. **The page's client** (`WEB` or `MWEB`, as the page reports itself) with the user's session:
   the cookie and, for a signed-in session, the `SAPISIDHASH` authorization YouTube's web player
   sends. **Option A** supplies its proof-of-origin tokens: a player token in
   `serviceIntegrityDimensions.poToken`, bound to the video ID, and a media token appended as
   `pot=` to its stream addresses. The media token is bound the way the page's player binds it:
   to the video ID when the page sets `html5_generate_content_po_token`, otherwise to the data
   sync ID for a signed-in page, otherwise to the visitor data. A token that cannot be minted is
   reported in the lookup details, and the client is asked without it.
5. **Mobile site** (`MWEB`) when the page's client was not `MWEB`.

Option A runs YouTube's own BotGuard as the web player does (`app/.../detection/potoken/`): an
offscreen WebView loads an app-served page on `appassets.androidplatform.net` (CSP
`default-src 'none'; script-src 'self' 'unsafe-eval'; connect-src 'self'`, no cookies, storage or
navigation) that runs only the challenge's inline interpreter. The app fetches the challenge and
the integrity token from Google's `jnn` attestation endpoints itself (`Create`, `GenerateIT`: no
cookies, no redirects, the API key read from the current player script, never committed). One
BotGuard session is kept until its refresh time, closed after five idle minutes, and every mint
has a 60-second budget; tokens are cached per binding (32 entries). Tokens and bindings never
reach logs or lookup details.

Option C is the browser: when a lookup in YFT's browser meets a bot check, the notice says to let
the video play and offers **Try again**; the lookup also runs once by itself after the page's
player requests media from YouTube's media servers. That lookup carries the browser's own
YouTube cookie and user agent. Cookies from requests of other sites never replace it.

Live result from the agent sandbox (data-centre IP, 2026-10-03): `VISIONOS`, `ANDROID` and `MWEB`
answered for a public video, and `MWEB` downloads succeeded with and without a minted token. A
video that got "confirm you're not a bot" got it from every client even with a minted token, so
the bot check on that network is decided by the IP address, not by the token; a phone on a home
or mobile network is the real test. Not done: the page player's own token is not captured from
the browser ([FIX_ADD_PLAN.md](../FIX_ADD_PLAN.md) §7 Backlog).

### P14: visionOS first (2026-10-05)

The chain gained a step 0 before the watch page; steps 1–5 are unchanged.

0. **visionOS first.** One request as `VISIONOS`, asked the way step 2 asks it: its app's user
   agent and device fields, no cookie, no authorization, no visitor data and no page key. The
   answer is the whole lookup only when it is complete: playable (`playabilityStatus` OK, so not
   private, age-restricted, region-blocked, DRM or live), a title and a length, every listed
   format with a direct address (no `signatureCipher`, none protected) and a `contentLength`,
   and at least one AVC video row with sound (a merged AVC + AAC row). Then the watch page is
   not read: about 17 KB instead of the 166 KB page first.
**Any other answer** (a sign-in, bot or age check, unplayable, an error, a missing size or
address, SABR only) runs the chain from step 1 exactly as before: the watch page first, and the
page's own verdict ends the lookup. visionOS is asked once per lookup; step 2 reuses its answer
instead of asking again, and nothing it refused or offered unlocks what the page refused. An age
check from visionOS is not a verdict on its own: the page decides, and an age-restricted video
stays refused.

Rows are those the chain made from the same answer: merged AVC + AAC rows now include 360p,
because visionOS has no progressive 360p stream (the Android app's progressive 360p still wins
when the chain runs). Live (sandbox, data-centre IP, 2026-10-05): `dQw4w9WgXcQ` took one request
of 16,677 bytes in 180 ms (was 4 requests, 185,088 bytes), with 2160p and 1440p VP9, 1080p, 720p,
480p and 360p AVC merged rows and the AAC track; a ranged GET of the 720p stream (itag 136) returned 206
with the stated length. Bot-checked videos on that network still ran the whole chain (4
requests), and the age-restricted `HtVdAasjOgU` stayed `LOGIN_REQUIRED` (2 requests).
