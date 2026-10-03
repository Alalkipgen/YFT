# ADR-006: Owner override — use whatever works for public videos

- Status: Accepted. Supersedes item 4 ("What YFT does not do") of
  [ADR-005](ADR-005-youtube-owner-override.md) and the limits on client identities, proof-of-origin
  tokens, SABR and internal APIs in [YOUTUBE_RISK_REVIEW.md](../YOUTUBE_RISK_REVIEW.md),
  [SUPPORT_MATRIX.md](../SUPPORT_MATRIX.md) and [FIX_PLAN.md](../FIX_PLAN.md) §0.2.
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
test (FIX_PLAN F4) and T08's lookup details show that most YouTube lookups fail under those rules.

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
