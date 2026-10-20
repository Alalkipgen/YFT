# YFT Master Extractor backup

## Social plan S1a (1) — the answer judge, Facebook's rules (2026-10-20), JVM only

- `SiteAnswerJudge.kt` (`extractor-master`, JVM): L2's classes (plan §5) — `DIRECT`, `PARTIAL`
  (fewer heights), `LOGIN_WALL`, `BOT_CHECK`, `RATE_LIMITED`, `REMOVED`, `PRIVATE`,
  `AGE_LIMITED`, `REGION_LIMITED`, `PHOTO_ONLY`, `DRM`, `VALUE_REJECTED`, `SHAPE_CHANGED`,
  `TRANSIENT` — and S1a's `NEEDS_PLAYBACK`, each with its next step (V and the init check on the
  rows, the next rung, the tab's playback or the browser, the browser, the host stop, final) and
  the reason given when the lookup ends on it. A judged answer's evidence is a short label (a
  status, the final address's kind, the route's kind), never the page's words, a link or an ID.
- `FacebookAnswerJudge` (the pilot), conservative: final only on 0.3's language-independent
  evidence, the page's own `initialRouteInfo.route`: `comet.fbweb.CometErrorRoute` with the
  root's `privacy: true` (a JSON true only) → `PRIVATE`;
  `comet.fbweb.CometVideoHomeVideoNotFoundRoute` → `REMOVED` (both `PRIVATE_OR_UNAVAILABLE`).
  No evidence, so `SHAPE_CHANGED` (the next rung): the page's words, the error route without the
  flag, a broken or renamed route, two routes that disagree, the reel's `video: null`, a login
  form inside a page; no shape hash.
- The order: a failed answer (a 429 or the client's rate limit → `RATE_LIMITED`, a bot check →
  `BOT_CHECK`, both the host stop; any other → `TRANSIENT` with its own reason); the final address
  (Facebook's login path → `LOGIN_WALL`, the browser; its checkpoint → `BOT_CHECK`; another host
  → `SHAPE_CHANGED`); rows bound to the video ID (`DIRECT`, `PARTIAL`) over any page signal; DRM
  (the reader's, never guessed); the plugin player's "needs playback"; the route. Region and age:
  no such page seen yet, so no Facebook rule. Reads only: no request, no log.
- Not wired: no caller yet; the flag `-Pyft.socialLadder` (off = today) comes with the first
  slice that wires the judge in. Route B, the parity tests, `FixtureBaselineTest`, the drift check
  and the YouTube files unchanged.
- Tests: `SiteAnswerJudgeTest` 7 (new; synthetic pages of 0.3's route structure with the test's
  own words, the unavailable page in English and in Burmese; mutations and decoys never final;
  each class's next step against `TerminalRules`). Local (JDK 17): `:extractor-master:test` 805
  tests (2 skipped) passed.
- Next in S1a, each its own commit: the recorder in the app's lookup path behind the flag, F1
  judged, F2 L3 rows, the budgets and F1's cooldown, V and the init check, the post-identity
  mutation tests, the simulator world. S1a's phone APK: the owner's go.

## Social plan 0.3 — Facebook's access-check evidence (2026-10-20), docs only

- From the sandbox, signed out, with main's page headers (its desktop agent, `Accept-Language`
  en-US unless noted): Facebook's pages for made-up IDs (no real post; the IDs are not kept) in seven
  link forms, each in English and in Burmese (by `Accept-Language`, and by the `locale` cookie,
  which wins over main's en-US); the /watch/ form also in German (the cookie). The pages'
  structure was compared, not their wording; main's `FacebookPageParser` was run locally over the
  saved pages (a throwaway test; no page kept as a fixture).

| Link form | The page, the same in every language | Main today: English | Main today: Burmese |
|---|---|---|---|
| A group's post | "This content isn't available right now": the page's route (`canonicalRouteName`) `comet.fbweb.CometErrorRoute`, the error root's props `privacy: true`, `tracePolicy` `comet.error`; only the title and body are translated | `PRIVATE_OR_UNAVAILABLE` (the English title) | `RESPONSE_CHANGED` |
| /watch/, a page's video | the video's not-found route `comet.fbweb.CometVideoHomeVideoNotFoundRoute` (the title "popular videos", translated) | `RESPONSE_CHANGED` | `RESPONSE_CHANGED` |
| A reel | the reels route; its root query's answer `data.video: null`, final | `RESPONSE_CHANGED` | `RESPONSE_CHANGED` |
| A share link | the older login page (its login form) | `LOGIN_REQUIRED` ("You must log in to continue") | `RESPONSE_CHANGED` |
| video.php, permalink.php | a redirect to the login page | the login wall (main's final-address check) | the same |

- **Verdict: yes**, a language-independent signal: the Comet router's route in the page's own
  JSON (`canonicalRouteName`, with the error root's `privacy` flag). It matches S0d's slot 13 (the
  removed post: `PRIVATE_OR_UNAVAILABLE` with the VPN, a recoverable reading without it): only the
  English title matches main's phrase.
- **The fix proposal for main's `FacebookPageParser`** (the owner's go, its own commit; not
  built): after the existing markers (the English readings stay), and still only on a page
  without playable media, the page's route `comet.fbweb.CometErrorRoute` with its root's
  `privacy: true` → `PRIVATE_OR_UNAVAILABLE`, and `comet.fbweb.CometVideoHomeVideoNotFoundRoute` →
  `PRIVATE_OR_UNAVAILABLE`. Tests on synthetic pages: the error route in two languages, the
  not-found route; decoys (unchanged): the error route without `privacy: true`, a route name on a
  page with media. Not proposed: the reel's `video: null` (no real reel was asked, so no control)
  and the share link's login form (the same form sits on other signed-out pages).
- Until then Master's judge stays conservative (a final answer only on positive evidence); the
  two routes are such evidence for S1a's judge. Slot 12 (sign-in-only, its link unverified, a page
  of the removed slot's shape): its expectation waits for the owner's check of the link; if it is
  gone, it is a removed slot (`PRIVATE_OR_UNAVAILABLE` with the fix).
- Also seen: main's login-form markers (`id="loginform"`, `login_form_container`) are not on
  today's login page (its form is `login_form`); main's final-address check catches the login
  redirect, so nothing changes.
- No code and no test kept. Next: S1a, JVM only, behind `-Pyft.socialLadder` (off by default).

## Social plan 0.2 — the Instagram check (2026-10-20), docs only

- Locally and statically; nothing was asked of Instagram. Main's GraphQL ask
  (`InstagramExtractor`) against yt-dlp's current Instagram extractor: the file as of yt-dlp's
  commit `1f1101d0` (2026-07-21; the release 2026.08.19, `594bd50c`, holds the same file), its
  signed-out ask from `f49b551a` (2026-06-28, the extractor's rework) and its fallback without
  impersonation from `8b8e3e3c` (2026-07-01). Structure only: no `doc_id`, app ID or header value.

| | Main (the GraphQL source) | yt-dlp now |
|---|---|---|
| When | signed in and out, after the app API (signed in only) | signed out only (signed in: the app API, as main) |
| Before it | nothing | the home page (its LSD token) and the access check `GET /api/v1/web/get_ruling_for_content/` (the CSRF cookie, used only on `status: ok`) |
| Endpoint | `GET /graphql/query/` (a query string) | `POST /api/graphql` (a form body) |
| `doc_id` | yt-dlp's previous one (its file 2024-10-07 to 2026-06-28) | another (since 2026-06-28) |
| Variables | `shortcode` and three nulls | `media_id` (the shortcode's number) |
| Body | — | `lsd`, `fb_api_caller_class`, `fb_api_req_friendly_name` (the signed-out post query's name), `server_timestamps` |
| Headers | the web app ID (the same), `X-ASBD-ID` (another value), `X-IG-WWW-Claim`, `X-Requested-With`, `Origin`, `Referer`, `Accept`, `Accept-Language`; `X-CSRFToken` only from the user's cookie | the same app ID, another `X-ASBD-ID`, `X-IG-WWW-Claim`, `Origin`, `Accept`, and `X-FB-Friendly-Name`, `X-FB-LSD`, `X-CSRFToken` (from the access check), `X-Requested-With`, `Referer` |
| Answer read | `data.xdt_shortcode_media` (or `data.shortcode_media`) | `data.xig_polaris_media.if_not_gated_logged_out` (the app API's item shape) |
| TLS | the app's own client | **sent only with TLS impersonation** (`impersonate=True`; not sent when it is unavailable) |
| Without it | — | the post's page: its `data-sjs` script JSON (`RelayPrefetchedStreamCache` → `xig_polaris_media`); a login redirect → login required (the signed-out rate limit) |

- yt-dlp's history (the file's 54 commits since 2021): the post's query changed twice, a
  `query_hash` (2022-07), a `doc_id` (2024-10, main's), another `doc_id` (2026-06), each time with
  a new request (endpoint, variables or the answer's path), never the value alone.
- **Verdict (b): only with impersonation.** yt-dlp sends today's signed-out GraphQL ask only with
  TLS impersonation and, without it, reads the post's page instead; main's ask (the previous
  endpoint and `doc_id`, other variables, no access check before it) answered 401 signed out on 10
  of 10 (S0d). S3 forbids impersonation, so there is no fix for main's GraphQL ask, and signed out
  it is a documented limitation: main's signed-out ways are the post's page (its JSON scripts,
  read by shape) and the embed page (S0d: 6 of 7 video rows). Instagram's stage (S2d) may use
  only the visible tab's own fetch (the WebView's own requests), on the owner's go.
- Main's 401 → `LOGIN_REQUIRED`: **keep.** Signed in, a 401 or 403 is the session's refusal (the
  signed-in message). Signed out, the browser class sends the user to YFT's browser, where signing
  in opens the app API and where the only allowed further way (the tab's own fetch) would run;
  it is not a final answer, and the census reads the removed slot's as "ambiguous (no session)"
  (0.4 a). Optional, on the owner's go (its own commit): the signed-out wording, since a 401 alone
  does not show that a post is for signed-in viewers only (S0d's slot 5, a reel, read so).
- §8 item 3 (the `doc_id` sources), the recommendation: no `doc_id` self-healing now (no S1b for
  it), since the value never changed alone and today's request needs impersonation; if the owner
  allows S2d's tab fetch, the source is Instagram's own scripts as the tab loads them, and
  yt-dlp's raw file stays the owner's local reference, never read by the app. The owner decides.
- No code and no test. Next: 0.3, Facebook's access-check evidence.

## Social plan 0.4 (b) — the site census diff (2026-10-20)

- `SiteCensusDiff` (`extractor-master`, JVM): two exports of "YFT site census" (the earlier run's
  and the later's, as texts or parsed, bounded) → a headline and, per site, one line per slot that
  changed. **BREAKING** is a worsening: main's rows gone, a row as expected that no longer is,
  main's V `pass` → `fail`, a row Master recovered (route B, V pass) that it no longer recovers,
  or a site stopped by the site (a 429, a bot check, its rate limit) where it was not.
  **CHANGED** is any other difference: main's answer, class, reason, status, rows or tallest row;
  the judge (as expected, not as expected, ambiguous); V; the post's item; the best row's
  duration; Master's way, answer, rows or V; the session; a stop gone, or the census's own budget.
- Headlines: "FIRST RUN — nothing to compare yet"; "NO CHANGE", "CHANGED (n)" or "BREAKING (n)" —
  "against the run of" the earlier run's date; "NOT COMPARED — the earlier (or later) file is not
  a site census export" (not JSON, another tool's export, another format).
- Not compared, a note each: a site or a slot in one run only, a skipped site, a row not looked
  up. A field an older export lacks (before 0.4: the times, the best row's duration, the item) is
  compared only when both runs hold it; times and request counts move from run to run and are
  not compared. The lines hold sites, slots, kinds, labels, reasons and numbers only, as the
  exports do.
- The screen (debug only): after its rules the report holds the diff against the last run the
  state kept (the state's stored export; its format stays 1); the export holds no diff. The
  runbook step per line is W's (S3), not 0.4's.
- Tests: `SiteCensusDiffTest` 5 (new), `SiteCensusTest` +1 (21). Local (JDK 17):
  `:extractor-master:test` 798 tests (2 skipped) passed. Next: the census APK (0.1 with 0.4).

## Social plan 0.4 (a) — the census fixes (2026-10-20)

- Times: each looked-up row holds its setup time (main's identity of the link) and its end-to-end
  time (main, V, Master) beside main's own time; the report's row line says "N ms (setup N ms,
  row N ms)", the export `setupMs` and `ms`.
- A report key of the record's own (labelled records only, so the census only): the method,
  address, body and identity class (signed in or not, and the agent named; never a cookie's
  value), hashed and never exported. It splits main's asks sent again into repeats (the same key)
  and variants (the same method and address with another body or identity class): per ask
  (`repeat`, `variant`), per row (`repeats`, `variants`; the report's "repeats N; variants N").
  `LookupAsk` (its `endpoint`, route B's R2 key) is unchanged, and so are the endpoint rule, its
  enforcement, the host stops and the endpoint's own count (`repeated`, still in the export).
- The best row's duration (seconds, one decimal; main's and Master's, `bestS`) and the post's
  item main's answer names ("item N of M", `item` / `items`): X's answer line (the place a
  `/video/N` link names, of the post's videos; the card's or the quoted post's included) and
  Instagram's carousel item of the post's items. The screen's main (debug only) taps the four
  adapters, each as it is, so a lookup gets its answer's lines; main's code is unchanged.
- The judge: Instagram's removed slot, signed out, with main's sign-in answer reads "ambiguous (no
  session)" (`ambiguous`; neither as expected nor not, counted apart in the site's number and the
  report's "also" line); with the session it stays "not as expected". The photo posts keep
  `NO_MEDIA_FOUND`. The state's format stays 1; an older export only lacks the new fields.
- Tests: `SiteCensusTest` +4 (20), `LookupRecordLabelsTest` +1 (12), `SiteWaysTest` (Instagram's
  removed kind), `SiteCensusLookupTest` +1 (6, the app's debug tests). Local (JDK 17):
  `:extractor-master:test` 792 tests (2 skipped) and `SiteCensusLookupTest` 6 passed. Next: 0.4
  (b), the site census diff, then the census APK.

## Social plan 0.1 — X's card fix in main (2026-10-20)

- Main's X reader (`XSyndication.parse`) reads the post's own media (`mediaDetails`), then the
  post's own card (`card.binding_values.unified_card.string_value`, JSON in a string: its
  `media_entities`, each read by the existing `videoOf`), then the quoted post's media as before;
  the quoted post's card is not read. A card's videos keep the card's own order (its `components`:
  a `media` component's item, a `swipeable_media` component's list in order), not the key order of
  `media_entities`; an item the list does not name is not read. The choice (`/video/N`, else the
  first), MP4 first and the host guard (`video.twimg.com`) are unchanged; the answer line says
  "the card's video N, M in the post". A tombstone or an empty answer keeps `NO_MEDIA_FOUND`; a
  card without a video, or with files only off X's video host, keeps "no video"
  (`RESPONSE_CHANGED`), as before.
- Tests: 5 synthetic fixtures (made-up IDs and hosts): `tweet_card_video` (`video_website`),
  `tweet_card_carousel` (`video_multi_dest_carousel_website`: two videos that `media_entities`
  holds the other way round, one file off X's video host) and the decoys `tweet_card_no_video`,
  `tweet_card_off_host`, `tweet_quoted_card`. `XSyndicationTest` +3 (the card's order, also
  without its `components` list; the post's own media before its card, the card before the quoted
  post; the decoys), `XExtractorTest` +2 (MP4 rows from a card, `/video/2` on the carousel; the
  decoys keep `RESPONSE_CHANGED` and the generic detector).
- Master: the census reading (`SiteReadings.tweetResult`) reads the post's own card's video as data
  (not the quoted post's card); `SiteWaysTest` lists the five fixtures; `FixtureBaselineTest`'s
  baseline +5 entries (only additions). The X contract recipe stays as it is, documented in its
  provenance header (under route B Master runs only after main fails; its shape layer reads JSON
  in strings). The provenance table keeps its rows and base hashes: the self-check against the
  base reports 0 drifted; against this branch only `parse` (its 2 reviewed rows) differs, which the
  check against main will report once the fix reaches main. `docs/SUPPORT_MATRIX.md`'s X row
  names the card.
- Local (JDK 17): `:extractor-sites:test` 285 and `:extractor-master:test` 787 passed. Next: the
  census fixes (0.4), then the census APK; the owner's X-only check after it (slots 3 and 6 → MP4
  rows with V pass; slots 1, 2, 4 and 5 unchanged).

## Social plan — the final plan of the steps after S0d (the owner, 2026-10-20), docs only

- The owner approved the plan of the steps (plan §7.3, §8): the pilot Facebook, then TikTok, X,
  Instagram (item 1); X2 not now (item 2); no third-party service in Master (item 4); the test
  lists stay on the phone (item 6); main fixes as the owner's exceptions, one commit each (item 10:
  X's card go; Facebook's access check after its evidence; Instagram after its check; X's
  age-limited and removed posts keep `NO_MEDIA_FOUND`); one item per link, an "all items" feature
  after the release (item 11); the release gate S0–S5 on all four sites before PR #2 and P8 (item
  12). Open: item 3 (after the Instagram check), item 5 (before S4).
- Steps: Stage 0 (0.1 X's card fix in main, 0.2 the Instagram check, 0.3 Facebook's access-check
  evidence, 0.4 the census fixes and the site census diff, 0.5 the owner's baseline B0) → S1a (the
  kit's minimum + Facebook's pack, flag `-Pyft.socialLadder`) → S2b TikTok → S2c X → S2d
  Instagram → S3 → S4 → S5 per site → PR #2 → P8 (beta.4). After the release: S1b, X2, the "all
  items" feature, B+ (S6), A.
- The plan's §4 S1 gets the exception for main fixes, §9 defines B0 (a fixed config: the VPN with
  S0d's US exit for all four sites, and Facebook, Instagram, X without it, at the same weekday and
  time); `MASTER_KEY_SOCIAL_S0_CONTINUE.md`'s next steps point to §7.3. No code changed. Next:
  Stage 0.

## Social plan S0d — the owner's census run (2026-10-20)

- Two runs of "YFT site census" on the owner's phone (the S0c commit's APK; the owner's public
  posts kept on the phone, 40 of 41 slots): run 1 all four sites through the owner's VPN with a US
  exit (TikTok needs it on the owner's network), 40 rows, 192 s; run 2 Facebook, Instagram and X
  without the VPN. No 429, bot check, stop or time limit; at most 67 requests and 5.9 MB per site.
- Shares (main's recoverable failures ÷ rows that should download), the same in both runs:
  Facebook 0 of 11, TikTok 0 of 6 (run 1), Instagram 0 of 7, X 2 of 6; Master recovered 0. Every
  row main gave passed V. Master's trials after main's rows: Facebook 8 (8 with rows, V pass 8),
  TikTok 8 (6, 6), Instagram 6 (1, 1), X 4 (4, 4).
- X: the 2 are posts whose videos sit only in the card (`unified_card`, JSON in a string), which
  main's `XSyndication.parse` and Master's X contract do not read; Master's engine found rows in
  main's replayed answer (4 and 8, tallest 720; its shape layer), but its best row was HLS (V
  unknown). Instagram: GraphQL 401 on 10 of 10 signed out, main gave 6 of 7 (5 from the embed page); a reel
  (slot 5) and the removed post (slot 10) read as sign-in (browser); Master's trials were refused
  (the embed page main had read), so Master adds nothing today. Facebook: the sign-in-only slot
  reads as `RESPONSE_CHANGED`; the removed post was final with the VPN and `NO_MEDIA_FOUND`
  without it (main's access markers are English phrases); Master's contracts do not read the four
  links main resolves first. TikTok: all as expected; main's tallest 1920, Master's 1280.
- Recorded in the plan §7.2 and §8 items 1–3 (counts only; no link, ID or name). No code changed.
  Next: the owner's answer on §8 item 1 (the pilot and the order; recommended Facebook, then
  TikTok, X, Instagram), then S1 with the pilot site.

## Social plan S0c — the screen "YFT site census" (2026-10-20)

- `SiteCensusActivity` (debug builds only; its launcher entry "YFT site census" exists only with
  `yft.masterCapture` and `yft.ownSolver` on, placeholder `yftOwnerTools`, like "YFT client
  census"): a link field per slot (41: Facebook 13, TikTok 10, Instagram 10, X 8; kept in the
  screen's own store on the phone, empty slots skipped, HTTPS links only), "Run <site>" or "Run all
  sites" on a tap, then the report with "Copy report" and "Copy JSON" (the export). The status line
  shows the last run and any cooldown; the state (cooldowns, the last run) is the file
  `files/master/site-census-last.json`, written whole and then moved. The screen stays on while it
  runs; leaving the screen stops the run (turning the phone does not); the report notes the network
  type (Wi-Fi or mobile data).
- `SiteCensusLookup` (main as Home runs it): the app's `SiteAdapterCoordinator` over the four
  adapters as `SiteAdapterModule` builds them (TikTok on its own client that sends a short link's
  redirect cookies, with the WebView's agent and the public download service; Facebook, X,
  Instagram), the phone's merge rule, a new answer cache per lookup (fresh), TikTok's hidden page
  (`TikTokPageEngine`, the owner's settings) after a failed page read, its answer as the row's mark.
  Identity: the agent YFT's tab sends and the browser's cookies for the link. Main's failure keeps
  its reason and the adapter's HTTP status.
- Clients: the app's `NetworkConfiguration` client under `OkHttpExtractorClient` with no retry (each
  ask once, at most 60 s), its wire bytes counted for the 40 MB budget; main's asks go through
  `LookupRecorder` into each row's record; Master's contracts and V use the same client, Master's
  media checks are the app's (`OkHttpMediaValidator`).
- Copied from main (provenance header, a row in `toolkit-provenance.tsv`): `WebViewAgent` from
  `SiteAdapterModule.kt` (private there). Self-check against `34a41890`: 131 symbols, 0 drifted.
  Main, the app's lookups, Master's engine, `RouteBLookup` and `RouteBHttpClient` unchanged.
- Tests: `SiteCensusLookupTest` (5, the app's debug unit tests; also in the Master APK workflow's
  list); `CensusNoBackgroundTest` +1 (the site census's sources and screen name no background or
  scheduling API, as the client census). Next: S0d (the owner's run on the phone).

## Social plan S0b — the census engine and the one-row check (2026-10-20)

- `social/SiteRowCheck` (V): one row of each answer (the best: the tallest, then the highest
  bitrate) gets a one-byte file check with the headers the download sends for that row, only on
  the site's media hosts. An HLS row or any other host is `unknown`, never a pass; an expired row
  fails unasked; a DASH row's manifest is read (one bounded ask on a media host, Master's own
  `InlineDashReader`) and its tallest video file is checked; a 429 is a rate limit.
- `social/SiteCensus` (`SiteCensusReport`: rows, each site's number, the report, the JSON export,
  the state): per row, main's own lookup (`MainLookup`: fresh, the browser's identity, at most
  90 s) inside the row's labelled `LookupRecord`; Master's R8 way once in the same record under
  route B's rules (`RouteBLookup`: main's identical unauthenticated answer replayed, an endpoint
  main asked not asked again) after main's recoverable failure, or once as a marked trial after
  main's rows or the site's final answer; never after a browser-required reason, a network failure,
  a rate limit, a bot check, main's time limit, nor for a short link. V on main's and Master's best
  rows; each row judged (recoverable, browser, final) against its slot's kind. Each site's number:
  main's recoverable failures of the rows that should download, and how many Master's way
  recovered (route B rows that passed V).
- Stops and budgets: a 429 or a bot check (any ask, the hidden page and V included) stops the site
  for the run and for 30 minutes (the state, the screen's file in S0c); at most 150 requests and
  40 MB per site (a budget stop has no cooldown). Privacy (S8): the report, the export and the
  state hold only sites, slots, kinds, way labels, classes, counts, sizes and short fingerprints; a
  last guard masks any address and the link's values (the link, the post ID, handles, codes).
- Nothing copied from main (self-check against `34a41890`: 0 drifted); main, the app's lookups,
  Master's engine, `RouteBLookup` and `RouteBHttpClient` unchanged.
- Tests: `SiteRowCheckTest` (8), `SiteCensusTest` (16: main's real adapters over fixtures or a
  scripted main, Master's real contracts). Next: S0c (the screen "YFT site census" and its APK).

## Social plan S0a — the recorder's labels and the site table (2026-10-20)

- `LookupRecord` takes labels (`AskLabels`, additive): a record given none is today's, so route B
  and every lookup stay as they are. A labelled ask gets its way, its reading of the answer and a
  short shape fingerprint; the record counts repeated asks (the same endpoint, any identity: a
  Facebook public page asked again as a page and TikTok phone then desktop count as repeated), asks
  after a stop (a host that answered 429 or a bot check, asked again) and bot checks, and names the
  hosts that stopped.
- The site table (`social/SiteWays`, `SiteLabels`, `SiteReadings`): each site's way labels, media
  hosts and readings, copied from main with provenance headers; 41 census slots (Facebook 13,
  TikTok 10, Instagram 10, X 8), each with its kind and what it expects.
- `toolkit-provenance.tsv`: 23 new rows for the copies (old rows unchanged; self-check against
  `34a41890`: 0 drifted).
- Tests: `LookupRecordLabelsTest` (11), `SiteWaysTest` (13), `SiteWaysDriftTest` (7: main's real
  adapters and Master's contracts under route B). Next: S0b (`SiteRowCheck`, `SiteCensus`).

## Social plan S0 started (measuring only); Phase 1.3 backlog item 6 parked (2026-10-20)

- The owner (2026-10-20): item 6 (live WebM pieces, one-file live formats) is parked, built later
  and only when it is needed; until then such a stream's MP4 qualities keep working and its WebM
  qualities keep the clear message (`MASTER_KEY_PHASE1_3_PLAN.md` §10 item 6).
- The owner's go for the social plan's S0 (`MASTER_KEY_SOCIAL_PLAN.md` §7.1, new): it measures
  only. Main, the app's lookups and Master's engine stay as they are; the census screen is an owner
  tool of debug builds. Sub-steps: S0a the recorder's labels and the site table, S0b the census
  engine and the one-row check, S0c the screen "YFT site census" and its APK, S0d the owner's run
  and the pilot.
- Row rules (§7.1): main's own lookup, fresh, with the browser's identity (the tab's agent and the
  browser's cookies for the post; no tab data), no retry, 90 s; Master's R8 way once under route B's
  rules after main's recoverable failure, or once as a marked trial after main's success or a final
  answer about the post; V on one row of each answer; each row judged against its slot's kind. A 429
  or a bot check stops the site for the run and 30 minutes; at most 40 MB and 150 requests per site;
  the links stay on the phone.
- Docs only: the social plan's status, §7.1 and §8 item 1; the Phase 1.3 plan's item 6. Next: S0a.

## Phase 1.4 item 7 — D7 run 14 phone check: no refusal as the app was; item 7 closed (2026-10-20)

- D7 run 14 (owner, run 39309047458; plan §9 3h's test rule). (a) As the app was, no clear:
  `normal`, installed as an update, downloaded V3 (Big Buck Bunny) 360p (27 MB, 02:22) and again
  after a Force stop only (02:24); `routeb`, installed as an update, the same (02:27, and again
  after a Force stop). No row said "YouTube refused" and none had a 403: runs 12–13b's "first
  1 MiB, then 403" did not come back after run 13c's Clear browsing data, so step (b) (Preview,
  Clear cache, Reset YouTube session, Clear browsing data) was not needed; B1's chip and B3's reset
  were not met on the phone.
- A later open of V3 in `routeb` (02:28): "Route B trial: main's rows were set aside and Master
  found none. Tap Try again.", Details `master: route B trial: main's 9 rows set aside` and
  `master: route B not run: skipped (RESPONSE_CHANGED); main's requests in this lookup: 0; main's
  answer reused: its remembered answer (P17)`. Run 8's limit (b), the trial's only: a remembered
  answer asks nothing, so route B has nothing to replay, and F2's line says so; Try again skips the
  remembered answer and a Force stop drops it. `normal` has no trial.
- (c) After Clear browsing data: `routeb`'s first open of V3, without Try again (02:40):
  `master: route B after RESPONSE_CHANGED; replayed 3; new 0; check: 206×8; 2nd range: 206×8;
  same as main's: 9 of 9 rows, headers same`; 360p downloaded. Then `normal`, installed as an
  update, downloaded V3 360p. The owner played both start to end (10:35).
- Reading: in a working session `normal` downloads with main's rows (route B, now on, answers only
  main's failures), and route B's rows in `routeb` are main's own links (9 of 9, headers same)
  with nothing new asked (`new 0`; run 13c's `new 1` came on the first lookup after an install, as
  read there). Which part of the stored data caused run 13's refusal (B2) stays open; the test
  rule stays for when it comes back.
- Item 7 closed: route B is on in every debug build with Master capture (the trial only in
  `routeb`); release and preview unchanged. Docs only (the routing plan's status, §7 and §9 3h,
  `MASTER_KEY_ARCHITECTURE.md`, `MASTER_KEY_PHASE1_3_PLAN.md` §10 item 7); no code changed.
- Next: none in item 7. B2 / B4 only if the refusal comes back (plan §9 3h); merge and release stay
  the owner's.

## Phase 1.4 item 7 — D7 run 13b / 13c: V3's 403 followed the debug app's browser data; the owner's decisions; F8 trim; promotion; B1; B3 (2026-10-19)

- D7 run 13b (owner, run 13's APKs, no new build): run 13's `normal` APK as it was fails V3 360p
  like `routeb`: `VISIONOS itag 134+140 · pot no · sig yes · ends 359 min (app 358) · 2048 KB · 45 s;
  403×8 at 11 MiB, 3 at once, 43 s`, chip "Link expired". Two minutes before, on the same phone and
  5G, the main Preview app (`com.alal.yft.preview`, its own data) downloaded V3 360p (27 MB).
- D7 run 13c: after the debug app's Clear browsing data the same `normal` APK downloads V3 360p and
  720p; run 13's `routeb` APK (an update, then Clear browsing data): `… replayed 3; new 1; check:
  206×8; 2nd range: 206×8; same as main's: 9 of 9 rows, headers same`, 360p downloads, and plays
  start to end with YouTube's duration (owner).
- Reading: the failure followed the debug app's stored browser data (every D7 APK is
  `com.alal.yft.debug`, so runs 9–13 shared one session); not route B, not the build, not the
  network. Not isolated: Clear browsing data resets cookies (the visitor ID), web storage, the HTTP
  cache and the remembered lookups at once; the visitor ID is the most likely (V1, which takes no
  visitor data from the tab, downloaded in that state; V3's lookup reads the watch page with the
  tab's cookie). `new 1`: likely Master's own player-script fetch after the install (≤ 1 player
  request). F8's wait never helped (3 of 3 tries).
- The owner's decisions (plan §9 3h): promotion approved; F8 trim (one re-ask after 3 s; one at a
  time and the Detail stay); Track B, main's stale session: B1 a 403 with time left on the link is
  no longer "Link expired", B3 "Reset YouTube session" (YouTube's cookies and storage only), B4
  extractor-side only with evidence (STOP). D7 test rule from run 14: V3 first as the app is; on
  "1 MiB, then 403": Preview, then Force stop + App info Clear cache, then Reset YouTube session,
  then Clear browsing data, noting which step fixed it; then `normal` / `routeb` on one clean state.
- F8 trim (every build's YouTube downloads): a 403 at CONNECT from YouTube's media host now asks
  the refused range again once, 3 s after the refusal (`mediaHostRefusalWaitMillis` 45 s → 5 s,
  first wait 3 s unchanged); refused again, the download fails as before, about 3 s after its first
  403, Details `403×N at X MiB, K at once, 3 s`. One range at a time after a refusal stays. Tests:
  `MediaHostRefusalsTest` +2, `DashMediaHostRefusalTest` +1; that test's fixture now answers no 403
  before the ranges asked at once came in (a race seen on a slow machine; no assertion changed).
- Promotion (the owner's decision, plan §9 3h / 4): `-Pyft.youtubeRouteB` is on by default, so every
  debug build with Master capture has route B: the CI's Master build (its tests run with it) and
  all eight D7 APKs (`routeb` adds only the trial, whose default stays off). Release, preview and
  builds without Master capture: off as before; `-Pyft.youtubeRouteB=false` gives P12 as before.
  Test: `YouTubeRouteBTest` +1 (route B goes with Master capture). No route-B code changed.
- B1 (Track B, main's stale YouTube session; every build): a 403 at CONNECT from YouTube's media
  host while every link still has a minute or more left (its own `expire`, and the app's end when
  known) is now `YOUTUBE_REFUSED`, not `ACCESS_DENIED`: chip "YouTube refused" (was "Link
  expired"), the row's note and Details' "What to do:" say reopening the page with the same session
  may not help — try again later, or Clear browsing data (signs out of every site in YFT), then
  reopen the page; notice "— YouTube refused". It still needs a fresh link (`NEEDS_REFRESH`), F6's
  facts lead the Detail; every other refresh failure still says "Link expired". Tests:
  `GoogleVideoLinkFactsTest` +2, `DownloadQueueTest` +1, `DownloadLabelsTest` +1,
  `DownloadsScreenTest` +1, `DownloadNotificationTextTest` +1; local download tests 108 pass, and
  the queue's 19 (`DownloadQueueTest`, `StreamDownloadQueueTest`, without the store's Room part).
- B3 (Track B; every build): "Reset YouTube session" on a `YOUTUBE_REFUSED` download (a button on
  its card and an item in its menu), asked first: YFT's browser forgets YouTube's cookies
  (`www.`, `m.`, `music.`, `youtube.com`; each as the host's own, for `youtube.com` and for the
  host), those origins' site storage and YouTube's remembered lookups; other sites' sign-ins,
  Google's included, the HTTP cache, downloads and files stay. The snackbar says how many cookies
  went and to reopen the page (any that stayed: Clear browsing data removes them). The refused
  row's hint: try again later, or Reset YouTube session, then reopen the page; still refused,
  Clear browsing data. Tests: `YouTubeSessionResetTest` +6 (local pass without WebView),
  `DownloadsViewModelYouTubeResetTest` +4, `DownloadsScreenTest` +1, `DownloadLabelsTest` (hint).
  `[d7]` run 14 builds the D7 APKs with promotion, B1 and B3.
- Next: D7 run 14 (owner; plan §9 3h test rule), which closes item 7.

## Phase 1.4 item 7 — D7 run 10 phone check, F7 (Master's rows against main's) and its crash fix, D7 run 12, F8, D7 run 13 (2026-10-19)

- D7 run 10 (owner, run 39233263437): `routeb` V3 360p "Link expired" → Details: `ACCESS_DENIED`,
  `CONNECT`, HTTP 403, `VISIONOS itag 134+140 · pot no · sig yes · ends 359 min (app 358) · 0 KB ·
  0 s`. A fresh visionOS link refused at once. Run 9's token reading was wrong: main never adds
  `pot` to visionOS links either. Code reading: Master's rows are made from main's replayed answers
  by the same code, so they are likely main's own set-aside links of that lookup.
- F7 (route-B trial APK only, `[d7]` run 11): route B's notice also checks Master's files once
  (`check: 403×2`: a one-byte ranged GET like the download's first) and compares Master's rows with
  main's set-aside rows (`same as main's: X of Y rows, headers same`; else the names that differ,
  never a value). No extractor or locked file changed; every other build unchanged.
- D7 run 11 (owner, run 39239845145): the `routeb` APK closed when a YouTube video played. F7's
  check closed its answers on the main thread (route B's lookup runs there), where reading the
  network is stopped by Android. F7 fix (`[d7]` run 12): the check asks and closes off that thread,
  and any failure only says `trial check failed (<type>)` on the line. Tests +2.
- D7 run 12 (owner, run 39246815918): no crash. `routeb` V1 and V3 notices: `check: 206×8; same as
  main's: 9 of 9 rows, headers same` (Master's rows are main's own; YouTube gives each file's first
  byte). V3 360p (VISIONOS 134+140) still fails at download: 403 at CONNECT, `pot no`, 0 KB; V1 360p
  downloads. Reading (b): a download-time difference in the app (plan §9 3e, F8).
- F8 (every build's YouTube downloads, `[d7]` run 13): the check asked each file's first byte once;
  the download asks a file's first 1 MiB, then 10 MiB ranges, four of the file at once, beside the
  other file of the merge. Likely: YouTube gives a fresh link's first bytes but refuses its other
  ranges for a while (yt-dlp waits for the video's ads before it downloads, `available_at`), or
  refuses one file's ranges asked at once. Fix (`DashTransferEngine`, new `MediaHostRefusals`):
  after a 403 at CONNECT from YouTube's media host, that file's ranges are asked one at a time and
  a refused range is asked again after 3 s, then twice as long (at most 8 s), for up to 45 s; then
  it fails as before, its Details `403×N at X MiB, K at once, T s`. Other hosts, live streams and
  other failures unchanged. Route-B trial APK only: the notice also asks each checked file's 2nd
  range (the download's 2nd request, 1 MiB on): `; 2nd range: 206×N` or `403×N`. No extractor or
  locked file changed. Tests: `MediaHostRefusalsTest` 6, `DashMediaHostRefusalTest` 4,
  `RouteBTrialCheckTest` +3.
- D7 run 13 (owner, run 39257316082): `routeb` V1 and V2 download. V3 notice: `… replayed 3; new 0;
  check: 206×8; 2nd range: 206×4, 403×4; same as main's: 9 of 9 rows, headers same`. V3 360p
  (VISIONOS 134+140) fails twice after ~45 s at 7%: `2048 KB · 45 s; 403×8 at 1 MiB, 3 at once,
  43 s` and `… 403×8 at 11 MiB, 3 at once, 43 s`. Reading: YouTube gives each V3 file its first
  1 MiB and refuses every later range, also asked alone, for 43 s; F8's wait did not help (not the
  download's pace). The links are main's own. V1 720p (29 MB) downloads (run 12: `replayed 1`,
  visionOS's first answer); V2's files are under 1 MiB each; V3's lookup read the watch page
  (`replayed 3`). "First MBs, then 403" is how YouTube began asking a token of another client in
  Aug 2026 (yt-dlp #17456).
- Next: D7 run 13b (owner, no new build): run 13's `normal` APK → V3 → 360p download. Downloads:
  route B's lookup differs (app side). Same 403: YouTube limits these links for main too, so a fix
  is main's extractor (STOP, the owner's choice).

## Phase 1.4 item 7 — D7 run 9 phone check and F6: what a "Link expired" download was (2026-10-19)

- D7 run 9 (owner, run 39107280386): `normal` V1–V3 ✅ (V3 720p / 480p / 360p download). `routeb`:
  Master's rows on V1–V3 after Try again (V2 / V3 `replayed 3; new 0`); V1 360p and V2 240p
  download; V3's 720p, 480p and 360p all end "Link expired" before any byte, twice. Likely cause:
  main adds its proof-of-origin token (`pot`, BotGuard) to the page / mweb clients' media links,
  Master has no token maker (`NoPoTokenProvider`, Phase 1.4 scope), so YouTube refuses those links.
- F6 (debug diagnostics, `[d7]` run 10): a failed download of a YouTube media link keeps the link's
  facts at the start of its detail (`GoogleVideoLinkFacts`: client, itags, `pot` / `sig` yes or no,
  minutes left on the link and the plan, bytes, age; never the link or a value), and the Downloads
  screen offers Details for a "Link expired" (`NEEDS_REFRESH`) download too; one never tried says
  `Reason: Link not tried (…)`. No extractor or locked file changed; flag, trial and rows unchanged.
- Next: D7 run 10 (owner): `routeb` V3 → download → the "Link expired" item → Details. Then the
  owner's choice for V3 (A token maker for Master, B drop rows that need a token, C known limit;
  `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §9 item 3b), then promotion.

## Phase 1.4 item 7 — route B F4: a failure midway, offline (2026-10-18)

- Review follow-up F4 (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8): main asks and then fails with a
  recoverable reason (§2), not the trial's found page. Tests only — they found no bug, so no app or
  extractor code changed and there is no `[d7]`: D7 run 9's APKs (F5) stay the latest.
- (1) Master's rows reach the sheet: `BrowserMasterFlowTest` (the app as built, the real bridge and
  engine) for each recoverable reason, with and without main's watch page read with the tab's
  cookie — answered from the lookup's record (`replayed 1` / `2`, `new 0`, only main's page carried
  the cookie), notice `master: route B after <REASON>; replayed N; new 0`. `RouteBSimTest` (Master's
  engine after main over the same record): a moved shape (L2, L4, L7) completes from main's
  replayed visionOS answer with 0 player requests and 0 pages; after a bot-checked visionOS answer
  (main's script fetch failed, `HTTP_STATUS`) Master completes from main's replayed page, 0 new pages.
- (2) Master finds nothing: main's failure stays (reason, message, notice, Try again — as in a build
  without route B) with Master's details after main's (route B's line first, `master: youtube
  module: <REASON>` last); Try again asks afresh in a new record.
- (3) Main's final reasons (nine): `master: route B not run: <REASON> is final`, Master never runs,
  nothing of Master's sent; a 429 midway: `replayed 1; new 0; refused 1`, nothing new, main's
  failure kept; main's success untouched; flag off skipped as today (P12). Covers R5's open check (4)
  offline.
- Finding (no bug; §2 holds): main ranks a bot check as the page's own verdict, above a changed or
  empty answer — after a bot-checked visionOS answer such a page fails `BOT_CHECK` (final) and route
  B never runs; F5's page replay serves a real failure only when main's verdict outranks the bot
  check (streams found but no download), otherwise the trial's found page (run 9's `routeb` check).
- Tests: `RouteBSimTest` +4, `BrowserMasterFlowTest` +3 (stand-ins extended, nothing removed);
  `extractor-master` 731 (2 skipped), `extractor-sites` 280, app 992 (66 skipped) pass locally.
  Next: D7 run 9 phone check (⏸ owner).

## Phase 1.4 item 7 — route B F5: Master reads main's watch page from the lookup (2026-10-18)

- The owner's cookie decision (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8–§9, limit (a) of D7 run 8):
  the app's route-B build names the lookup's video in its record
  (`BrowserMasterFallback.lookupContext` → `LookupRecord(watchPageVideoId)`); the record keeps main's
  first GET of that video's watch page when it carries the tab's cookie and no other credential —
  Set-Cookie dropped, inside the caps, dropped at the lookup's end, keyed without the cookie (its
  value is neither kept nor compared). Master's identical cookie-less GET is answered from it once:
  replayed, counted, never sent. Every other authorized answer is still never kept; Master never
  sends a cookie or an authorization (its module drops the cookie).
- One watch page per lookup (guardrail 7): `LookupAsk.watchPage`, `LookupRecord.watchPageAsked`;
  `RouteBLookup` refuses Master's page GET after any page of the lookup (another address or video
  included); Master's own page goes out only when main asked none, once.
- Correction: Master's page GET never carried the cookie — R5's and run 8's `refused 1` was rule 4
  (main had asked that page with the cookie, an answer never kept), not rule 1; the plan's readings
  are corrected.
- Simulator (`RouteBSimTest`): the F5 sweep (1,090 steps) keeps every R2 rule; where route B answers
  in the app none ends worse than before F5 and 226 end better (STOP → COMPLETE / PARTIAL, as the
  ladder alone); after a final reason (route B never runs there) L9 stops as the ladder alone does.
  After a bot-checked visionOS answer (T1b, V2-like, V2-like signed in) Master completes from main's
  page: 0 pages, 0 player requests, 0 credentials, `replayed` ≥ 2. Signed in over every class,
  Master's requests carry no cookie, authorization, session header or account identity.
- Tests: `LookupRecordTest` +7, `RouteBHttpClientTest` +4, `RouteBSimTest` +3,
  `BrowserMasterFlowTest` +1 (Master's row with `replayed 2; new 0` on a V2-like page),
  `BrowserMasterFallbackTest` +1; lookup tests 58, `extractor-master` 727 (2 skipped),
  `extractor-sites` 280, app 989 (66 skipped) pass locally. Flag off and every other build
  unchanged. D7 run 9 (`[d7]`) builds `routeb` for the phone check (⏸ owner); next F4 (offline).

## Phase 1.4 item 7 — route B D7 run 8 phone check and the final plan (2026-10-18), docs only

- D7 run 8 (`routeb`, run 39095306817 on `8584519`, owner's phone): V1 — first open skipped
  (`main's answer reused: its remembered answer (P17)`), Try again → Master's rows and
  `replayed 1; new 0`; V2 / V3 — route B ran, the replayed visionOS answer was a bot check and the
  module's watch page (with the tab's cookie) was refused: `replayed 1; new 0; refused 1`, no rows,
  main's failure kept. The joined lookup (F3) never showed. Wrong rows 0; nothing with a cookie sent.
- Final plan (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §9): F3 deferred; the trial's first open
  unchanged (Try again); the owner's cookie decision → F5: Master reads main's watch page from the
  lookup once and never sends a cookie (no cookie-less page asked again); then F4 (a real failure
  midway, offline), D7 run 9 (⏸ owner) and the owner's promotion decision. Next agent's prompt:
  `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md` (follow-ups). No code.

## Phase 1.4 item 7 — route B F2: Details say why route B did not run (2026-10-18)

- Review follow-up (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8): R5's first opens showed the trial's
  message with no Master line and no reason. In a route-B build a YouTube page main failed that gets
  no answer from route B now keeps main's failure (reason, message, retry) with one Details line:
  `master: route B not run: <REASON> is final` / `no page to ask` / `another page` /
  `skipped (<REASON>); no lookup record` / `skipped (<REASON>); main's requests in this lookup: N`
  (+ `main's answer reused: its remembered answer (P17)` or `another caller's running lookup (P17)`),
  or `master: route B answer dropped: the page changed during the lookup`.
- The reuse is noted by the shared lookup (`SiteAdapterCoordinator.inspect`) in the lookup's record
  (`LookupRecord.reuse`); only a route-B build's YouTube lookup has a record. Flag off and the h-only
  APK: unchanged (main's answer itself; `master: h-only: <step>`).
- Tests: `BrowserMasterFallbackTest` (3 updated to expect the line, 1 new incl. flag off),
  `SiteLookupCacheTest` (1 new), `LookupRecordTest` (1 new); lookup tests 47 and
  `MasterFallbackEngineTest` pass locally. D7 run 8 (`[d7]`) builds `routeb` for the phone run (⏸ owner).

## Phase 1.4 item 7 — route B F1: the player-script client follows route B's rules (2026-10-18)

- Review follow-up (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8): the runners fetch the player script
  with their own client, which route B did not see — a 429 on Master's script fetch did not stop the
  runner's second address or Master's next requests, and main's script 429 was not in the lookup's
  record (offline probe: 2 fetches, `rateLimited` false, the next request sent).
- Fix: `RouteBScriptClient` (`extractor-master` `lookup`) is the player client in a route-B build
  (`YouTubeRouteB.scriptClient` in `SiteAdapterModule`; every other build the same client, flag off =
  today). Master's script fetches in a route-B run: rules 1, 3, 5 (`RouteBLookup.script`), counted in
  `new M`, nothing new after a 429; main's: unchanged, a 429 marked in the record
  (`LookupRecord.unlisted`, not listed or kept).
- R5 marked **partial** (main's 429 / bot check not met on the phone; first-open skips not logged).
  Tests: `RouteBScriptClientTest` (5), `YouTubeRouteBTest`; lookup tests 46, `RouteBSimTest`,
  `MasterFallbackEngineTest` pass. Next: F2 (why route B was skipped, in Details), then a phone run.

## Phase 1.4 item 7 — route B R5 phone check and R6 docs (2026-10-18): built behind the flag, not promoted

- R5 (owner's phone; D7 run 7 APKs `normal` and `routeb`, run 39084859828 on `44493f8`; V1
  `dQw4w9WgXcQ`, V2 `jNQXAC9IVRw`, V3 `aqz-KE-bpKQ`, V4 the Short `BGQWPY4IigY`): `normal` unchanged —
  main's rows as before on all four, no notice. `routeb`: V1 (after Try again) → Master's rows (MP3
  192 / 128, 2160p / 1440p WebM, 1080p, 720p, 360p …) with the notice `master: route B after
  RESPONSE_CHANGED; replayed 1; new 1`; 720p and 360p downloaded: picture, sound and seek work,
  duration 3:33 (the page's), the 360p file 640×360 (as its row says); a retest gave the same.
- V2 and V3 → no rows, main's failure kept (`replayed 1; new 0; refused 1`): the replayed visionOS
  answer was a bot check ("Sign in to confirm you're not a bot"), and the module's next step, the
  watch page, was asked by main with the tab's cookie (an answer never kept), so route B never sends
  Master's cookie-less GET of it (`RouteBLookup` rule 4; first written as rule 1, corrected in F5). V4: the
  same message, no Details. The bot check stayed final; wrong rows: 0.
- A first open showed the trial's message with only `master: route B trial: main's N rows set aside`:
  route B did not run (no request of main's in the lookup's record — a reused answer, P17 — or an
  early stop; not logged). Try again (a fresh lookup) runs it. R4's documented limit.
- Status: item 7 is built behind `-Pyft.youtubeRouteB` (off in every normal build), **not promoted**;
  promotion is the owner's decision. Possible follow-ups, not planned: a route-B way past a
  bot-checked visionOS answer (needs a decision on the plan's §2 rules); the trial asking main afresh
  on a first open.
- R6 docs: `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` (status, §7 R5 results, R6, item status),
  `MASTER_KEY_ARCHITECTURE.md` (layout `lookup/` and `YouTubeRouteB`, flow, R8 note, next),
  `MASTER_KEY_PHASE1_3_PLAN.md` §10 item 7. Docs only, no code.

## Phase 1.4 item 7 — route B R4: trial switch and the routeb APK (2026-10-17)

- Debug switch `-Pyft.routeBTrial` (default off, with `-Pyft.youtubeRouteB` only; release and preview
  false): in the trial a YouTube page main found is answered as main's `RESPONSE_CHANGED` after main
  asked (`BrowserMasterFallback.trialPrimary`), so Master's route B answers from the lookup's record;
  main's failures (bot check, login, player script, 429) stay final; every other build unchanged.
- Route B's rows now carry their details (`SiteAdapterOutcome.Detected.details`, default none) and the
  page lookup shows route B's line as the page notice: `master: route B after <reason>; replayed N; new M`.
- New D7 APK `routeb` (matrix entry in `master-optin-debug-apk.yml`; `.github/d7-apks.txt` = run 7),
  built by this `[d7]` push. Tests: `BrowserMasterFallbackTest`, `BrowserMasterFlowTest`,
  `YouTubeRouteBTest`. Next: R5 phone check (owner), list in `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §7.

## Phase 1.4 item 7 R3: route B's claim rule — Master's YouTube module answers after main's recoverable failure (2026-10-17)

- One place for the recoverable set (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §2):
  `TerminalRules.ROUTE_B_RECOVERABLE` = `NO_MEDIA_FOUND`, `EXPIRED_LINK`, `RESPONSE_CHANGED`,
  `HTTP_STATUS`, `MALFORMED_RESPONSE`, `RESPONSE_TOO_LARGE` (`routeBRecoverable`). Never a
  `NEVER_FALLBACK` or `BROWSER_REQUIRED` reason; `UNSUPPORTED_URL` is answered as before.
- Engine (`MasterFallbackEngine`, new `MasterPolicy.youtubeRouteB`, default off): a page Master's
  YouTube module claims, after main failed with one of these reasons, in a lookup whose record holds
  main's own requests, is answered by the module inside a `RouteBLookup` over that record (R2's
  rules: main's identical answers replayed, authorized requests refused, nothing new after a 429),
  within the engine's own time bound (no new numbers). Its rows or its failure name it first:
  `master: route B after <reason>; replayed N; new M` (`; refused K` when any; also on a timeout).
  Switch off, any other reason, no record, or a record without main's requests (a lookup that joined
  another caller's, or a cached answer) → `Skipped` as today (P12).
- App (`AndroidBrowserMasterFallback`): in a route-B build the engine gets the switch
  (`create`); Master's rows keep main's adapter ID; when Master's module gives no rows, main's failure
  stays the lookup's answer (reason, message, Try again) with Master's details after main's, so the
  answer never turns into another reason; the same video reloaded during the lookup keeps the rows
  (as the h-only trial). Every other build: the very same calls as before.
- Not yet: found rows carry no details, so on a found page the counts are not shown (a failure shows
  them in the sheet's Details); R4 (trial switch, `routeb` APK) shows them for the phone check.
- Tests: `RouteBClaimRuleTest` (10: the set; switch off = P12 for every reason; on: each recoverable
  reason answered from main's replayed answer, every final reason skipped, no record or no main
  request skipped, `UNSUPPORTED_URL` as before, only the YouTube module, nothing sent after main's
  429, a new request counted, the engine's own timeout); `BrowserMasterFallbackTest` +6 (each
  recoverable reason → Master's row; bot check, login, player script, 429 and the other final reasons
  → main's answer, no request; switch off → main's answer; no main request → main's answer; no rows →
  main's failure + Master's details; same video reloaded → rows kept); `BrowserMasterFlowTest` +1
  (a route-B page lookup: `RESPONSE_CHANGED` → Master's row is the page's video, main's request
  replayed; bot check → main's failure, Master never runs). `RouteBSimTest` reads the shared set.
- Local: `:extractor-master:test` 707 green (2 skipped as before), `:extractor-sites:test` 280 green,
  all app unit tests with the flags off 981 green (66 skipped as before), the four Master app classes
  with `masterCapture` + `ownSolver` on green (45) and again with `clientLadder` + `youtubeRouteB` on
  (45), drift 0 (107 symbols), workflow script tests green. Locked files and `extractor-sites`
  unchanged.

## Phase 1.4 item 7 R2: route B's Master wrappers for the YouTube module (2026-10-17)

- New in `extractor-master` `lookup`: `RouteBLookup` — one route-B run of Master's YouTube module over
  the lookup's record (a coroutine context element; counts replayed / new / refused, numbers only;
  `summary()` = `replayed N; new M` and `; refused K` when any, for R3's Details line) — and three
  wrappers that only pass through without it: `RouteBHttpClient` (the module's client),
  `RouteBMediaStartReader` (the ladder's media reads: L's V and init checks, H's first segment) and
  `RouteBPlayerScripts.wrap` (the own solver's runner; keeps `SignatureVerdicts`, so the module's
  signature gate is as before).
- Each request of Master's module, in order: with the user's session (`Cookie` / `Authorization`) →
  refused, never sent; identical to one whose unauthenticated answer main got → replayed from the
  record, once; after a 429 in the lookup (main's or Master's) → refused `RATE_LIMITED`, nothing new
  goes out; an endpoint (method, address, body; media hosts aside) already asked in the lookup →
  refused (so the watch page stays main's one per lookup); otherwise new — sent and listed as Master's,
  never kept. File checks and media reads are never replayed (as in the ladder's memo). A refusal
  reads as a request without an answer (`NETWORK`, no status), never `UNSUPPORTED_URL`. The own
  solver is not run after a 429 (its script fetch would be new).
- App: in a route-B build `MasterLadderSwitch.youTube` (`AndroidBrowserMasterFallback.create`) gives
  Master's module `YouTubeRouteB.masterClient` / `masterScripts` / `masterReader`; every other build:
  the very same objects as before. Nothing puts a `RouteBLookup` into a lookup yet (R3's engine), so
  every lookup, the census and every other site run as before.
- Simulator (`RouteBSimTest`): one lookup = main (`extractor-sites`, test only) then Master with the
  ladder through the wrappers, in the same world, every class on every base, own solver off and on;
  Master after every main result, as R4's trial will run it (990 runs). 0 wrong rows, 0 credentials,
  main + Master at most one watch page per lookup, Master at most 3 player requests and never more
  than the ladder alone, every request it sent listed in the record. After main's recoverable failure
  (230 runs: L2, L4, L7, L10, L13, F3) Master never ends worse than the ladder alone. Moved shape
  (L2, L4, L7): main fails, Master is complete from main's replayed visionOS answer with 0 player
  requests and 0 watch pages. After main's 429 Master sends nothing.
- Tests: `RouteBHttpClientTest` (11), `RouteBGatesTest` (5), `RouteBSimTest` (3), `YouTubeRouteBTest`
  +3. Local: `:extractor-master:test` 697 green (2 skipped as before), `:extractor-sites:test` 280
  green, all app unit tests with the flags off 974 green (66 skipped as before), the four Master app
  classes with `masterCapture` + `ownSolver` on green (38) and again with `clientLadder` +
  `youtubeRouteB` on (38), drift 0 (107 symbols), workflow script tests green. Locked files and
  `extractor-sites` unchanged.

## Phase 1.4 item 7 R1: per-lookup request recorder for YouTube routing B (2026-10-17)

- Owner decisions (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §5): the plan's proposed values are used —
  `-Pyft.youtubeRouteB`, default off, debug only (release and preview false); the §2 recoverable set
  (named in `TerminalRules` in R3, tested per reason); main's unauthenticated answers kept in memory per
  lookup, size-capped; guardrail 7's budgets, no new numbers; no promotion before the R5 phone runs.
- New `extractor-master` package `lookup`: `LookupRecord` (one per lookup, a coroutine context element,
  memory only, never logged): per ask who asked, a short hash of method + address + body + authorization
  state, status or failure, 429 seen, start and duration from the lookup's start; the endpoints asked;
  main's unauthenticated non-media answers kept for **one identical** replay (method, address, body,
  headers in order; cookies dropped; at most 16 answers / 8 MiB); authorized answers and file checks
  never kept. `LookupAsk` (hash, endpoint, authorization state) and `LookupRecorder` (wraps main
  adapters' shared client; without a record it only passes through).
- App: `BuildConfig.YOUTUBE_ROUTE_B` (debug, only with `yft.masterCapture`); `YouTubeRouteB.mainClient`
  wraps the shared adapter client in a route-B build (`SiteAdapterModule`);
  `BrowserMasterFallback.lookupContext` gives each YouTube browser lookup its own record
  (`BrowserViewModel.lookUp`: main's adapter, then `recover`). Flag off: the same client, no record,
  every lookup as before. Nothing reads the record yet (R2 replay, R3 claim rule).
- Tests: `LookupRecordTest` (15: two lookups at once, identical-only replay, authorized never kept,
  429 by status / reason / file check, bounds, timing, nothing shown), `BrowserMasterFallbackTest` +2,
  `BrowserMasterFlowTest` +2, new `YouTubeRouteBTest` (added to the opt-in workflow's list). Local:
  `:extractor-master:test` 678 green (2 skipped as before), `:extractor-sites:test` 280 green, all
  app unit tests with the flags off 971 green (66 skipped as before), the four Master app classes with
  `masterCapture` + `ownSolver` on green, drift 0 (107 symbols), workflow script tests green. Locked
  files and `extractor-sites` unchanged.

## Phase 1.4 backlog item 7: YouTube routing B plan (the owner, 2026-10-17; docs only)

- New `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` and `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md`;
  `MASTER_KEY_PHASE1_3_PLAN.md` §10 item 7 points to them. Finding: the YouTube ladder never answers a
  user lookup today (P12: a found page is `Skipped` unless `UNSUPPORTED_URL`). Plan: route B behind a
  debug flag (default off = today), recoverable reasons only, per-lookup recorder with replay of main's
  unauthenticated answers, 429 stop, steps R1–R6, phone proof before promotion. No code, no tests run.

## Final plan: next level for Instagram, TikTok, Facebook and X (the owner, 2026-10-17; docs only)

- `MASTER_KEY_SOCIAL_PLAN.md` is now the **final plan** (the owner's approval after the review round).
  Decided (§2.1, §8 items 7–9): **routing B** — Master acts only after main fails, for all four
  sites, as site packs inside the generic engine (no claiming module, no ~4,700-line copy of main's
  adapters); **B+** later (Master adds only the formats main lacks, main's rows untouched); **A** only
  if B+ is measured insufficient, through a real Master-owned route state (never `ADAPTER_DISABLED`,
  never relabeling a found page as `UNSUPPORTED_URL`). **Lookup** = the user's whole lookup (main +
  Master), with an app-side per-lookup request recorder (memory only) against repeated asks and for
  the 429 / bot-check stop; it replaces R8's "same endpoint may be asked a second time" for these
  sites (pointer added in `MASTER_KEY_ARCHITECTURE.md`). **R8** becomes a small budgeted ladder per
  site pack.
- Corrected: "never worse than main" is split into no regression under the same policy (structural
  under B) and listed policy differences (S4, S6, S7); the memo no longer "covers main's asks"; YouTube's
  module (R6) is not the routing model (it answers only after `UNSUPPORTED_URL`, run 3). Steps: S0 =
  recorder + census, S1 = kit with the pilot site only, pilot Facebook or TikTok by S0, Instagram and X
  first stop the repeated ask, S6 = B+. Items 1–6 still need the owner's answer; nothing is built until
  the owner says go. No code changed.

## Plan: next level for Instagram, TikTok, Facebook and X (owner's Plan Mode, 2026-10-17)

- New `MASTER_KEY_SOCIAL_PLAN.md` (plan only, no code): audit of main's four adapters and Master's
  generic path, the gaps against Master YouTube's layers, what can be reused (Master YouTube, main,
  Master generic, core-download, app, CI), guardrails S1–S10, layers L0–L8, per-site rungs, steps
  S0–S5 and six owner decisions. Nothing is built until the owner says go.

## visionOS 1.05 A check on the phone (owner, 2026-10-17 19:41 UTC): passed

- Run 6 normal APK, census screen → "A check (visionOS 1.05)", video 2, mobile data: visionOS 1.02
  `DIRECT`; visionOS 1.05 `DIRECT`, V `PASS` (AVC row) → "1.05 can be visionOS's bundled alternate
  (App Store, 2026-09-19)". 3 requests, 37 KB, 1 s; both answers bound to the video, the same 27
  adaptive rows (144p–2160p) and HLS. The bundled alternate 1.05 (`adc1f85`) is confirmed on the
  phone; nothing else to do.

## visionOS bundled alternate 1.04 → 1.05 (owner's order, 2026-10-17); run 6 APKs

- **Why:** run 5's full census found the visionOS App Store listing's form 1.05 `DIRECT` with a V
  pass (step `ST`); the owner said to change it ("ပြောင်း").
- **Change:** `MasterClientTable.VISION_OS_ALTERNATE` is now 1.05 (source: the App Store listing
  `com.google.visionosyoutube` 1.05.34, date 2026-09-19); NewPipe's 1.04 is no longer bundled. It
  is still the one alternate (guardrail 5), asked by A last and once, only after
  `VERSION_REJECTED`; R0 stays 1.02, so nothing changes on the healthy path. The census A check
  asks the same constant, so its button and headline now say 1.05 (App Store, 2026-09-19).
  Ladder files and the census debug screen only; no copied file, parity tests unchanged.
- **Tests:** `ClientTableTest`, `ClientCensusACheckTest`, `VersionSourceTest`, `VersionRungTest`,
  `ClientCensusTest` updated for 1.05; extractor-master tests pass locally.
- **Phone (optional):** census screen → "A check (visionOS 1.05)" should say
  `visionOS 1.02 DIRECT; visionOS 1.05 DIRECT, V PASS`.

## Run 5 phone check (owner, 2026-10-17): Phase 1.4 item 5 proven, item 1 D0 measured

- **Normal APK:** 1080p video with sound, MP3 320k and 2160p video with sound all download and play.
- **h-only APK (item 5):** over H the 2K / 4K rows no longer say "No sound"; a 2160p download is
  one MP4 with sound and seeking works; 1080p keeps its sound. Item 5 is proven on the phone.
- **Full census (item 1, the D0 measurement):** FULL, 10 videos, mobile data. The `appstore` fact
  reads the visionOS listing (1.05.34 → `1.05`) and step `ST` answered it accepted (`yes`, DIRECT,
  V PASS). The census line `CHANGED version visionos from appstore` only records the source moving
  from the iOS listing to the visionOS listing (the §7 step 3 hint is for a rejected value; 1.05 was
  accepted), so nothing to do. The bundled visionOS values are unchanged; changing them needs the
  owner's go.
- **INFO lines as before:** visionOS R0 BOT_CHECK without visitor data, answered by R6 (visitor
  data, DIRECT, V PASS); one video UNPLAYABLE for visionOS; the Play Store source reports "over 64
  KiB" (not new). All L steps unchanged.

## Phase 1.4 backlog items 2 and 6 (2026-10-17): item 2 stays closed (no trigger), item 6 not built; run 5 APKs

- **Item 2 (L steps 7–8):** no trigger has appeared (no BREAKING shape in W, no page signal
  missing for the copied reader). Item 3 records a moved layout and item 4 covers a page without
  a player ID, so the steps stay closed; nothing built.
- **Item 6 (live WebM pieces, one-file live formats):** not built. Joining WebM pieces needs a
  Matroska reader and writer proven on real pieces, and the sandbox cannot fetch any now
  (YouTube asks it to sign in); a wrong join would give a broken file where today says "pick an
  MP4 quality". The MP4 qualities of such a stream keep working (run 4f).
- **Run 5 APKs (`[d7]`):** this backlog's items 3, 4, 1 and 5 in one build for the phone check.

## Phase 1.4 backlog item 5 (2026-10-17): VP9 HLS 1440p / 2160p get their AAC sound, copied into one MP4 by YFT's own stream copy (phone proof pending)

- **What:** `HlsAudioPairing` also pairs a VP9 HLS quality (`vp09`) with its group's AAC sound,
  but only at a height where no quality has sound after the AVC pairing (YouTube's 1440p and
  2160p over H). At 1080p and below the AVC row keeps the sound and is the one listed, as before.
- **The merge:** Android's MP4 muxer takes no VP9, so it is the P35 stream copy alone:
  `Mp4TrackReader` reads the VP9 fMP4 (the `vp09` entry and its `vpcC` copied byte for byte, no
  NAL lengths) and the new `AdtsTrackReader` reads the joined ADTS sound (each frame without its
  header, 1024 ticks, an `mp4a` entry whose `esds` holds the AudioSpecificConfig the headers
  name). ADTS is read this way only next to VP9 video; AVC HLS keeps today's way (MediaMuxer).
  The read-back check stays; when the copy fails, the fallback fails too (no VP9 in Android's
  MP4), so the download ends in a merge failure, never a silent file.
- **Where it is allowed:** `AudioVideoMuxCompatibility.canWriteVideo(..., hlsVp9)` from
  `VP9_HLS_MP4_MIN_SDK` = 29 (Android 10, whose MediaExtractor reads VP9 in MP4 for the check);
  `DeviceMergeSupport` passes it for HLS candidates. On Android 9 and below such a row is left
  out, as the chain's VP9 WebM is.
- **Tests:** `AdtsStreamCopyReaderTest` (2, local), `StreamCopyAudioVideoMuxerTest` (+2: VP9 with
  ADTS is stream-copied; ADTS with AVC keeps today's way), `AudioVideoMuxEngineTest`,
  `MergeSupportTest`, `DefaultVariantResolverTest` (VP9 2160p paired, VP9 1080p not). No
  extractor-master file changed, so parity stays (`5116af8`).
- **Phone check:** a 1440p / 2160p row over H that said "No sound" now downloads as one MP4 that
  plays with sound and seeks.

## Phase 1.4 backlog item 1 (2026-10-17): the census reads YouTube's visionOS App Store listing and measures its form (full census only); A never reads it

- **What:** `CensusSources.APP_STORE_URL` now looks up `bundleId=com.google.visionosyoutube`
  (YouTube's visionOS app, a listing of its own; the iOS app listed no visionOS device). A store
  version has three parts (`1.05.34`), so the census reads its first two (`1.05`, cut, never
  counted up; guardrail 5) and notes the listing ("lists visionOS; listing 1.05.34").
- **The D0 measurement:** in a **full** census only (never the weekly check, so W's 20 requests
  stay as they were), when that form is newer than the bundled value it is asked once on video 1
  (step `ST`, visionOS, V on its row) and the `appstore` fact says `yes` / `rejected` / `no` /
  `bot-check`. The other sources' acceptance is unchanged (the App Store form never takes their
  place).
- **A:** unchanged; the client table keeps the source "measured, not active"
  (`VersionSource.approved` stays false for `itunes.apple.com`).
- **Tests:** `ClientCensusTest` (36/0) new "the visionOS App Store listing's form is asked once in
  a full census only, and A never reads it" (1.05.34 → one ask with 1.05, V PASS, `yes`; yt-dlp's
  newer 1.03 still tried; weekly → no 1.05 ask); `CensusSourcesTest` (forms, the visionOS
  bundle). CensusR6ParityTest 5/0, ClientCensusACheckTest 10/0, VersionSourceTest 11/0,
  ClientTableTest 7/0, CensusNoBackgroundTest 2/0.
- **Phone (the D0 measurement):** run the full census once; its versions line for `appstore`
  shows the form and whether YouTube took it.

## Phase 1.4 backlog item 4 (2026-10-17): the last good player ID when the page names none (the owner's go under guardrail 4)

- **What:** the ladder sees the chain's watch page. When the page names a player ID, the lookup
  keeps it, and after a **successful** lookup the health file keeps it (`"player":{"id","at"}`;
  public, the player script's version label; the strict pattern `^[A-Za-z0-9_-]{4,32}$` on write
  and read). When the page names **none** the copied reader can use, the ladder puts the last good
  ID in front of the page, so the copied reader finds it first and S runs with it; nothing else
  in the page changes. **Never after a bot check** (the health's 30-min bot-check window, which a
  bot check in this lookup opens). V and the init check stay on every row, so a stale ID ends in
  a clean stop, never a wrong row.
- **Requests:** a player script R6 would not fetch, only when the page names no player ID (the
  exception to D6's "the chain's requests stay R6's", approved with the backlog). The lookup's
  details say "S: the page names no player ID; none kept / the last good one used / … not used
  after a bot check".
- **Code:** `PagePlayerId.kt` (new, ladder: the copied reader's `jsUrl` / `PLAYER_JS_URL`
  pattern + `YouTubeUrls.playerId`), `LadderLookup.chain.get` (the watch page only) and `finish`
  (keeps the ID after a success), `ClientHealth.rememberPlayerId` / `lastPlayerId`. The copied
  files are not touched.
- **Tests:** `PagePlayerIdTest` (4): the copied reader reads the put-in-front ID on a page with no
  `jsUrl` and on one with an unusable `jsUrl`; a successful lookup keeps the page's ID; on a sim
  world whose page names none (visionOS answers with ciphers): no ID kept → no script asked; the
  last good ID → S asks with it, the same rows as a page that names the ID, no wrong row; after a
  bot check → not used, no script asked; the health file round-trips only a valid ID. Local:
  PagePlayerIdTest 4/0, ClientHealthTest 12/0, ClientLadderSimTest 1/0, MasterClientLadderSimTest
  9/0, MasterYouTubeParityTest 6/0, LadderTrialTest 4/0, HOnlyTrialTest 4/0.
- **Phone:** nothing to see while YouTube's page names the player ID (today); it acts only when
  a page stops naming it.

## Phase 1.4 backlog item 3 (2026-10-17): the shape map L located is recorded only (`ClientHealth` and the census record), nothing reads with it

- **What:** when L runs on a `SHAPE_CHANGED` visionOS answer, the ladder writes the map it
  located to the health file (`files/master/client-health.json`, the existing `shapes` entry):
  the shape fingerprint (core hash) → the canonical names' key paths (where the format lists sit,
  the video object's `lengthSeconds`, the status object's `status`, the first DRM marker's place).
  The census (W) keeps the same paths on a `SHAPE_CHANGED` record (`lmap`). Key paths only, never
  a value, address or ID; at most 4 maps × 64 paths (`ClientHealth` bounds).
- **Nothing reads with it:** rows, the whole-answer checks, V and the init check are unchanged
  (guardrail 9); the lookup's details say "L: shape map recorded". Parity tests unchanged.
- **Code:** `LocatedShapeMap.kt` (new, ladder), one call in `ClientLadder.lRows`, `lMap` in
  `CensusAsk` / `CensusJson`, set in `ClientCensus`.
- **Tests:** `ClientHealthTest` "L records the map it located, key paths only" (a moved layout:
  the paths, the 12-digit fingerprint, the file has no address, ID or value); `CensusDiffTest`
  round trip with `lmap`. Local: ClientHealthTest 12/0, ClientCensusTest 35/0, CensusDiffTest
  15/0, ShapeLocatorTest 11/0.
- **Phone:** nothing to see unless YouTube moves its layout (the debug screen's health line then
  says "shape maps 1").

## Owner item 6, run 4f on the phone (2026-10-17): ✅ recently ended live streams download whole (2:35:00 and 8:56:00, both failing on `main`); item 6 closed

- **Phone (the owner, normal APK of run 4f, `2026d8c`):** two recently ended live streams that
  fail on `main` (2:35:00 and 8:56:00) downloaded and play with their whole length (2:35:00 and
  8:56:00). The pieces-and-join way works on YouTube's live server.
- **Item 6:** closed. Left open (backlog, plan §10 item 6): live WebM qualities are not joined
  ("pick an MP4 quality"); the direct engine's one-file live formats keep the run 4e reading.

## Owner item 6, run 4e on the phone (2026-10-17): `NETWORK` READ_SOURCE, Detail "… sq 2021/2021 · SocketTimeoutException" → a live stream's file is asked piece by piece (`sq` 0 to the head) and the pieces are joined, as yt-dlp does; `[d7]` run 4f

- **Phone (the owner, run 4e APKs):** the recently ended live stream (2:48:23, ended about 9 h
  before), 720p and 240p, type Merge: `NETWORK` READ_SOURCE, Detail "live 671325 B/4 asks, last
  206 bytes 671325-11157084/-2 len 10485760 sq 2021/2021 · SocketTimeoutException" (240p: 230,132
  B). Another app got the whole 2:48 at 480p.
- **Reading:** YouTube still serves this file from its live server (`source=yt_live_broadcast`):
  the address without `sq` sends only the newest piece (sq 2021 of 0…2021, about 5 s each) and
  then waits for the next one, so every byte-range way gets one piece. yt-dlp (checked in its
  source) asks a HEAD of the address for `X-Head-Seqnum`, then `sq=0` … `sq=head`; each piece is
  a fragmented MP4 with its own `moov`.
- **Fix (owner plan A; `LivePieces.kt`, `DashTransferEngine`; live-origin whole-file DASH
  tracks only):** 1) a HEAD of the address gives the head (`X-Head-Seqnum`; none → new reason
  `LIVE_NOT_READY`, "YouTube is still preparing this live video. Try again later."). 2) one plain
  GET per piece, `sq` 0…head, no Range (a 200, or a 206 that holds the whole piece); piece 0
  first, and it must start a fragmented MP4 of the track, else `LIVE_NOT_READY` and nothing more
  is asked; the rest 4 at a time. A piece has 5 attempts for network errors, 5xx, 403 and 429
  (after its Retry-After, at most 60 s). 3) a missing piece among the newest 2 ends the file
  there; any other missing piece fails, Detail "live sq N/head · <status> <type> len <length>
  sq <X-Sequence-Num>/<X-Head-Seqnum>". 4) the pieces are joined (`LivePieceJoin`): piece 0's
  `ftyp` + `moov`, then of each piece its first `moof` to the end of its last `mdat`; a later
  piece's `moov` must name the same track (id + `stsd`); a `tfhd` with an absolute data offset
  is moved with its piece. The joined file is read by the merge's own reader (`Mp4TrackReader`)
  before it is written (a problem → `INTEGRITY_MISMATCH`, Detail "live: join: …"). 5) when the
  video's length is known and the joined track is shorter by more than 2 pieces + 1 s →
  `LIVE_NOT_READY`, Detail "live 1h02m03s of 2h48m23s · n/N pieces" (h/m/s: the Detail's
  cleaning takes "2:48:23" for an address). WebM live tracks: `UNSUPPORTED_SOURCE`, Detail "live
  WebM pieces are not joined; pick an MP4 quality". The plan now carries the video's length
  (`DashDownloadPlan.expectedDurationMillis`, from the variant; video and audio of a Merge).
  DASH no longer uses the run 4e growing read; `DirectTransferEngine` keeps it (one live file
  there is not joined). Merge and parity code untouched.
- **Limits:** WebM pieces are not joined; a stream still on air gets the pieces up to the head
  at the start; a resume keeps the pieces only while the head is the same.
- **Tests (CI; also run locally in a scratch JVM copy, `DashTransferEngineTest` and the other
  engine tests green):** `DashTransferEngineTest`: 10 live piece tests replace the 3 growing-read
  ones (see `docs/TEST_MATRIX.md`, item 6 row); `DownloadPlanFactoryTest`: a Merge plan's video
  and audio carry the video's length.
- **Phone check (next):** the same live stream, 720p and 240p, Merge → one 2:48 file that plays
  all through; on a failure, the Detail.

## Owner item 6, run 4d on the phone (2026-10-17): `NETWORK` READ_SOURCE (a socket read timed out in the body) → a live-origin answer is read only up to its Content-Range end, a stalled body keeps its bytes, only a 416 or an empty answer ends the file; `[d7]` run 4e

- **Phone (the owner, normal APK of run 4d, `94728e3`):** the recently ended live stream, type
  Merge: `NETWORK` READ_SOURCE, Detail "SocketTimeoutException: timeout; cause SocketException:
  Socket closed", "Start: plan 0.0 s · length 0.0 s (until the end) · first byte 15.3 s · first
  progress 15.3 s"; the row showed "Downloading · 8 KB" and then stood still.
- **Reading:** an HTTP/1.1 socket read timed out inside a body. Run 4d read every answer until
  its Content-Length; with the run 4c headers (Content-Range `bytes 0-671324/671325`,
  Content-Length 1048576) that waits for bytes the server never sends. The reading the owner
  forwarded (another agent): the Content-Length lies, the Content-Range is true and the server
  stops after the Content-Range end. Not proven (the live header check is blocked by YouTube's
  bot check), but it fits both phone results.
- **Fix (`LiveOriginFile.kt`, `GrowingRead`, shared by `DashTransferEngine` and
  `DirectTransferEngine`; live-origin files only):** 1) an answer is read up to its Content-Range
  end (at most the ask) and closed, never waiting for its Content-Length; the next ask starts at
  the next byte. 2) a live answer's body may stand still for 5 s (`liveStallMillis`) once its
  first byte came (that byte took 15 s on the phone, so it keeps the usual timeout); then, or when it breaks, its bytes stay and the
  next ask starts after them (at most 8 such answers in a row; an ask that brings nothing has the
  usual attempts). 3) only a 416 or an empty answer ends the file (after a full answer the ask is
  halved first, as before); a 403, 404 or any other 4xx now fails (run 4d took a 4xx after a
  short answer as the end), so a cut file is never kept as done. 4) a live failure's Detail:
  "live <bytes> B/<asks> asks, last <status> <Content-Range> len <Content-Length>", plus
  "sq <X-Sequence-Num>/<X-Head-Seqnum>" when the server sends them and the error class when the
  network broke. Other files stay strict; merge and parity are untouched.
- **If it still fails:** the Detail tells how far it got and what the last answer said. If it
  shows `sq` numbers, the file is probably served piece by piece (`sq=`); a piece-by-piece
  download would also change the merge → owner decision first.
- **Tests (CI, `core-download`; also run locally in a scratch JVM copy, engine tests green):**
  `DashTransferEngineTest` and `DirectTransferEngineTest`: a live server whose Content-Length is
  the whole ask but which sends nothing after the Content-Range end completes, exact bytes, each
  ask at the last Content-Range end + 1; an answer that stalls half way keeps its bytes and the
  next ask starts after them; a 403 part way fails (`ACCESS_DENIED`, Detail "live …", no file).
  The strict tests stay (a normal file with the phone's headers still fails with "asked bytes
  0-399, got bytes 0-399/400, length 1000").
- **Phone retest (`[d7]` run 4e, normal APK):** the recently ended live stream; if it fails,
  the Detail line.

## Owner item 6, run 4c on the phone (2026-10-17): still `INTEGRITY_MISMATCH` (Merge); the live server's Content-Range is wrong, its Content-Length right → live-origin files are read range after range until the server has no more; `[d7]` run 4d

- **Phone (the owner, normal APK of run 4c, `84c6af4`):** the ID3 fix works (census video 2
  1080p, 480p and 360p have sound all through). The recently ended live stream still failed,
  type Merge: `INTEGRITY_MISMATCH` CONNECT, Detail "asked bytes 0-671324, got bytes
  0-671324/671325, length 1048576", "Start: … length (from first range) · first byte –".
- **Reading:** the first range asked 1 MiB (`bytes=0-1048575`). The server answered
  `Content-Range: bytes 0-671324/671325` (a total of about one small piece) with
  `Content-Length: 1048576`, the whole asked length. The layout took 671 325 as the file's
  length, so the length check failed. The Content-Range end and total cannot be trusted; the
  Content-Length can. The real file length is not known before the end.
- **Fix (`LiveOriginFile` + `GrowingRead`):** a live-origin file (`source=yt_live_broadcast` or
  `yt_premiere_broadcast`) is read ask after ask from byte 0 (10 MiB each on YouTube) until the
  server has no more: a 416 or an empty answer ends it (also any 4xx after a short answer); one
  after a full answer halves the ask at that byte, down to one byte, in case the ask only went
  past the end. Each answer must start at the asked byte and bring at most what was asked
  (Content-Length); the stated length, the Content-Range end and total and ETag/Last-Modified
  are not compared, and no `If-Range` is sent. A failed ask keeps the bytes it wrote and asks
  again from there (the usual tries), so a broken connection does not start the file over. The
  progress has no total (length "until the end" in Start). `DashTransferEngine` (merges) reads it
  as one chunk; `DirectTransferEngine` (audio only) as one file of unknown length. The short
  answer follow-ups of run 4c are removed: other files are strict as before, and a mismatch
  keeps its Detail.
- **Tests (CI, `core-download`; also run locally in a scratch JVM copy, engine tests green):**
  `DashTransferEngineTest`: live-origin with the phone's headers (Content-Range of a 400-byte file,
  honest body and Content-Length, 416 past the end) and a wrong stated length → complete, exact
  bytes, asks 0-999, 1000-1999, 2000-2999, then 2500-3499 (416); no progress total; an answer cut
  half way → the next ask starts after the kept bytes, never at 0, and the file is exact; a normal
  file with the same headers → `INTEGRITY_MISMATCH` CONNECT, Detail "asked bytes 0-399, got bytes
  0-399/400, length 1000" (the phone's case). `DirectTransferEngineTest`: the same two live-origin
  cases (wrong stated size and probe, a new ETag each answer, no `If-Range`).
- **Phone retest (`[d7]` run 4d, normal APK):** a recently ended live stream in a few qualities
  and audio only; if it still fails, send the Details line.

## Owner item 6 (2026-10-17): a recently ended live stream failed every quality and audio (`INTEGRITY_MISMATCH`, CONNECT) → live-origin files trust the first range and take short answers; `[d7]` run 4c

- **Phone (the owner, main preview.78 and D7):** about 2 h after a live stream ended, every
  quality and the audio failed: `INTEGRITY_MISMATCH`, stage CONNECT, HTTP status and Detail
  empty, "Start: plan 7.9 s · length 7.9 s (from first range) · first byte –", type Merge (audio
  + video). Older videos fine; another downloader app works.
- **Live header check:** not done. YouTube answered `LOGIN_REQUIRED` (bot check) for every client
  from the sandbox and from the shared browser, so the answer headers are not confirmed.
- **Reading of the code:** a merge downloads each track with `DashTransferEngine` (whole file).
  The length came from the first range (no stated length); no byte was written, and the only
  CONNECT `INTEGRITY_MISMATCH` there is a range answer that is not the asked range (Content-Range
  end or Content-Length). So the server answered the first range with a different (most likely
  shorter) part: such a file is still being written after the stream.
- **Fix (`LiveOriginFile`, `source=yt_live_broadcast` or `yt_premiere_broadcast` in the file
  address):** `DashTransferEngine`: the stated length is ignored (the first range decides); an
  answer that starts at the asked byte and ends earlier is kept, and the rest is asked for from
  the next byte (at most 1 024 short answers in a row per range). `DirectTransferEngine` (single
  files): the same short answers, no `If-Range`, and the stated size, the answer's total and
  ETag/Last-Modified are not compared. Every other file is strict as before. A range mismatch
  now fills Detail: "asked bytes A-B, got <Content-Range>, length <Content-Length>", so a failure
  on the phone names its cause.
- **Tests (CI, `core-download`; also run locally in a scratch JVM copy, 77 engine tests green):**
  `DashTransferEngineTest`: live-origin + stale stated length + short answers → complete, exact
  bytes and ranges; normal + short answers → `INTEGRITY_MISMATCH` CONNECT with the Detail; normal
  + wrong stated length → fails. `DirectTransferEngineTest`: live-origin + short answers, another
  total and a new ETag each time + stale stated size → complete, no `If-Range`; normal + short
  answers → fails with the Detail; normal + stated size ≠ probe → fails at VERIFY, no request.
- **Phone retest (`[d7]` run 4c, normal APK):** the recently ended stream (soon after it ends)
  in a few qualities and audio; if it still fails, send the Details line.

## Phase 1.4 item 2, run 4b `[d7]` on the phone (the owner, 2026-10-17): H gives rows; HLS sound stopped after the first piece → packed audio loses its ID3 tags when the pieces are joined

- **Phone (`h-only` APK from `8ce88b2`):** H works (the sheet lists only HLS rows). Sound only for
  the first ~5 s (census video 2 1080p, 480p, 360p) or ~1 s (census video 1 240p); census video 2
  720p had full sound (not explained, same audio group). 2160p/1440p (VP9) have no sound: the
  pairing joins only AVC + AAC (out of this item).
- **Cause (live check in the sandbox, census video 2):** the AVC variants are MPEG-TS without
  sound and name an audio group (HE-AAC or AAC-LC). Those audio renditions are HLS packed audio
  (RFC 8216 section 3.4): every piece is an ID3 tag (a PRIV transport stream timestamp) and then
  raw ADTS frames; the timestamps run on without a gap. `HlsTransferEngine` joined the pieces
  byte for byte, so the second piece's ID3 tag sat inside the AAC stream and the AAC reader
  stopped there (226 frames, 5.25 s: exactly the first piece).
- **Fix:** `HlsPackedAudio.payloadStart`: a piece that starts with ID3 tags (syncsafe size, footer
  flag) followed by ADTS sync is written without the tags; totals and the length checks count
  the written bytes (follow-up commit: the check that the pieces are what was downloaded runs
  before the tags are dropped; `52ee188` compared the written bytes with the downloaded ones and
  failed its own test). TS, fMP4 and an ID3 tag followed by anything else are written as they are.
  Also covers the "Audio HLS" row.
- **Tests (CI, `core-download`):** `HlsTransferEngineTest`: 3 packed audio pieces (ID3v2.3, and an
  ID3v2.4 tag with a footer) give pure ADTS with every frame and `bytesWritten` = the frames; an
  ID3 tag followed by non-ADTS bytes is kept; the existing TS cases unchanged.
- **Phone retest (with the next `[d7]` APKs):** `h-only`: census video 1 240p, census video 2
  1080p/720p/480p/360p and the Audio HLS row must play with sound to the end.

## Phase 1.4 item 2, run 4b on the phone (the owner, 2026-10-17): H reached but gave no row (`H: SEGMENT`); two fixes + the owner's (က): a generic segment type lets the start decide; new `[d7]` APKs

- **Phone (`h-only` APK from `2dd3433`):** the found page now reaches the ladder. Details:
  "ladder trial h-only: the chain's 9 rows left out", "ladder H: SEGMENT", "master: youtube
  module: NO_MEDIA_FOUND" → "No verified media was captured". After Try again the sheet listed
  the adapter's rows again (census video 2, in the browser), so the downloads with sound were the
  normal path's, not H's.
- **Live check (sandbox, 2026-10-17, census video 2, visionOS 1.04):** master 200, variant 200
  (2160p VP9, fragmented MP4 with `#EXT-X-MAP`), the duration matches; the first segment answers
  `Range: bytes=0-65535` with **200 and the whole segment** (no length, no `Content-Range`), with
  either user agent; content type **`application/octet-stream`**; start `moof`.
- **Cause 1 (fixed, inside guardrail 6):** H read the segment through the HTTP client, which
  refuses a body over 64 KiB → too large → unknown → "H: SEGMENT". H now reads it through the
  ladder's media reader (as L rows' V): it sends the range and keeps at most 64 KiB even when the
  host ignores it; still 3 requests and one H check per lookup. Test: `HlsLegTest` "run 4 - a host
  that ignores the range - the reader keeps the start"; `ClientLadderSimTest` counts H's segment
  reads apart from L's V reads (at most one per lookup; the V budget unchanged).
- **Cause 2 (fixed):** in the h-only trial a lookup Master could not answer (no page to ask,
  another page, the page changed meanwhile, skipped) gave the adapter's rows back silently. Now it
  fails and names the step ("master: h-only: …"); the same video still open (reloaded) keeps the
  module's answer. Every other build unchanged. Test:
  `BrowserMasterFallbackTest.theMasterFirstSiteNeverGetsTheAdapterRowsBack`.
- **Guardrail 6 → the owner's (က) (2026-10-17):** with cause 1 fixed H still gave no row: the
  check wanted the segment's content type and container start to match, and
  `application/octet-stream` names no container, so it read as unknown (never a pass). YouTube
  serves its HLS segments this way. Now a generic binary type (`application/octet-stream`,
  `binary/octet-stream`, none) lets the container start decide (TS sync byte,
  `ftyp`/`styp`/`moof`/`sidx`/`emsg`/`prft`, EBML); any other start still fails; a named type must
  still match its start; the host, key and duration checks unchanged. Tests: `HlsLegTest`
  (octet-stream with a TS or `moof` start → row; no type → row; octet-stream with `<` or `@` →
  `SEGMENT_START`; the run 4 reader case ends in an MP4 row). New `[d7]` APKs (run 4b) → the
  h-only check again on the phone.

## Phase 1.4 run 4 on the phone (the owner, 2026-10-16/17, mobile data): items 1 and 3 ✅; h-only still showed the adapter's rows → fix `[d7]`

- **Item 3 ✅:** the `normal` APK's A check (19:42 UTC): visionOS 1.02 `DIRECT`, 1.04 `DIRECT` with
  V `PASS`; 3 requests, 37 KB, 1 s, no bot check.
- **Item 1 ✅:** census video 2's 2160p (about 345 MB) and 1440p (about 147 MB) VP9 + Opus rows
  downloaded, merged and played with picture and sound.
- **Item 2 ✗ → fixed:** the `h-only` APK still listed the adapter's rows (census video 1: 240p and
  144p MP4; census video 2: 2160p/1440p WebM, 1080p MP4). Cause: the found page reached
  `recover` (`7c7a72a`) with the reason `NO_MEDIA_FOUND`, but Master's engine lets a site's module
  answer a page it claims only when no site adapter answered (`UNSUPPORTED_URL`); otherwise it
  returns `Skipped` and `recover` gives the adapter's rows back, silently. The test used a page no
  module claims, so it passed. Fix: in the h-only trial a found page reaches Master as
  unanswered (`UNSUPPORTED_URL`), so the YouTube module (the ladder, H) answers; every other build
  and every failed lookup unchanged (the engine's rule untouched). Test:
  `BrowserMasterFallbackTest.aFoundPageOfTheMasterFirstSiteIsAnsweredByItsModule` (a module that
  claims the page answers one HLS row, no capture; a failed adapter's answer still stands).
- **Next:** the new `h-only` APK (this commit's `d7-matrix`): census videos 1 and 2 → HLS rows
  only → an AVC row downloads and plays with sound.

## Phase 1.4 items 1–3 built (the owner's order, 2026-10-16): the WebM merge buffer grows, h-only goes through H, visionOS 1.04 bundled; run 4 APKs `[d7]`

- **Item 3, visionOS 1.04 as the bundled alternate (`f02070a`):** `MasterClientTable.VISION_OS_ALTERNATE`
  = 1.04 (NewPipe Extractor `ClientsConstants.java`, 2026-08-06), visionOS's one alternate in
  guardrail 5's order (learned → self-healed → bundled); R0 stays 1.02, table `VERSION` 3, parity
  tests unchanged. The census A check reads the same constant (no second "1.04"); the weekly
  census step 3 asks nothing more. The ladder asks 1.04 only after YouTube rejects visionOS's
  version (`VERSION_REJECTED`), never after a bot check, never on the healthy path. Tests:
  `ClientTableTest`, `VersionSourceTest`, `ClientCensusACheckTest`, `ClientCensusTest`,
  `VersionRungTest` (3 new: 1.04 answers and is learned; not asked after a bot check; the healthy
  path never asks it).
- **Item 1, the WebM merge (`ec1f24e`):** WebM tracks state no `KEY_MAX_INPUT_SIZE`, so the
  merge's sample buffer was 256 KiB and a larger VP9 keyframe made `readSampleData` throw a bare
  `IllegalArgumentException` (suspect A; run 4's Detail confirms it if it still fails). From
  Android 9 the buffer grows to the next sample before each read (`MuxSampleBuffer`: twice the
  buffer or the sample, rounded up to 256 KiB, at most 32 MiB, never smaller than before); before
  Android 9 sizing is unchanged; the MP4 stream copy is untouched. A failure's Detail now names the
  call that threw and the sizes (`MuxTrace`, e.g. "IllegalArgumentException at readSampleData
  video: sample 348 KiB, buffer 256 KiB"); the log line adds the largest sample per track. Tests:
  `MuxSampleBufferTest` (core-download JVM, 8); `AudioVideoMuxerInstrumentedTest.aVp9KeyframeOver256KibMergesIntoOneWebm`
  (800×450 VP9 whose keyframe is 355,713 bytes, a Base64 asset; emulator-smoke, Run workflow).
- **Item 2, h-only through H (`7c7a72a`):** in the h-only APK only (`MasterLadderSwitch.hOnly`) a
  YouTube page the site adapter found still goes through `AndroidBrowserMasterFallback.recover`
  (`masterFirst`), so the ladder's H gives the rows; every other build is unchanged (a found page
  returns at once). Tests: `BrowserMasterFallbackTest` (2 new), `BrowserMasterFlowTest` (1 new).
- **CI of `1d3f41c`** (run 4): `build` ✅, `validate` ✅, `d7-matrix` 7/7 ✅ (Actions run
  38940779817). Before it: `f02070a` `build` ✅ `validate` ✅; `ec1f24e` `validate` ✅; `8f02d94`
  (the `[d7]` docs commit) red: `BrowserMasterFlowTest` did not compile (item 2 put
  `masterFirstSite` last in the `FakeBackup` helper, so trailing-lambda answers bound to it);
  fixed in `1d3f41c` (test code only, fix 1 of 3). **Emulator smoke** (the owner's Run workflow,
  `0c077a2`, run 38955756497) ✅: 54 tests, 0 failures, 3 skipped; the new
  `aVp9KeyframeOver256KibMergesIntoOneWebm` passed (the 355,713-byte VP9 keyframe merges into one WebM).
- **Run 4 on the phone** (handoff §4, mobile data): (1) `normal`: census video 2 → 1440p and 2160p
  download, merge and play with picture and sound (if one fails, copy its Detail: it now names the
  call and the sizes); the census A check still passes. (2) `h-only`: census videos 1 and 2 → HLS
  rows only → an AVC row downloads and plays with sound, its length the video's ±2 s. (3) Optional:
  the other five APKs, the lists as in run 3.

## Phase 1.4 backlog — items 1–3 (the owner's order, 2026-10-16): the WebM merge (2K/4K), h-only through H, visionOS 1.04 as a bundled alternate; handoff `docs/HANDOFF_WEBM_HONLY_104.md`

- **Run 3 on the phone** (owner, 2026-10-16, mobile data, Android 16):
  - A check ✅ (15:41 UTC): visionOS 1.02 and 1.04 `DIRECT`, V `PASS` on 1.04; 3 requests, 37 KB,
    1 s, no bot check; both answers the same shape.
  - `h-only`: the lists held "MP4"/"WebM" rows with sizes, not H's HLS rows — the trial never went
    through H (Master runs only when the primary adapter fails) → item 2. Census video 1 (240p)
    and census video 2 (720p MP4) downloaded with picture and sound.
  - Census video 2, 1440p (VP9 WebM + Opus WebM): fails at MERGE with an `IllegalArgumentException`
    without a message (3 of 3, `took video 1 min 2 s, merge 2.4 s`; another try `took video 46 s,
    merge 557 ms`); the same on preview.78 (`main`, the same merge code) → item 1.
- **CI of `5a389f1`** (run 3): `build` ✅, `validate` ✅, `d7-matrix` 7/7 ✅ (Actions run
  38919399644).
- **Backlog, Phase 1.4** (next to plan §10 items 2–5; the handoff gives each item's files, out of
  scope, stop rules and a review checklist). Order: items 1 and 3, then 2, then the `[d7]` APKs
  (run 4).
  1. **WebM merge fix:** first confirm with logs where it throws (`addTrack` video or audio,
     `readSampleData`, `writeSampleData`) and the largest sample per track. Suspect A: the 256 KiB
     fallback buffer (`MediaMuxerAudioVideoMuxer`; WebM tracks state no `KEY_MAX_INPUT_SIZE`) is
     smaller than a VP9 keyframe → the buffer grows to fit the sample, cap 32 MiB. Suspect B: the
     WebM muxer rejects the Opus track's format → fix what the logs show. A test with a fixture
     that reproduces it (a VP9 keyframe over 256 KiB). Done: CI green; 1440p and 2160p merge on
     the phone.
  2. **h-only through H:** with the h-only trial the primary's rows are not taken
     (`AndroidBrowserMasterFallback.recover`), so H runs. Done: the list shows HLS rows only, plus
     a test.
  3. **visionOS 1.04 as a bundled alternate** (guardrail 5): NewPipe Extractor, 2026-08-06; A
     check 2026-10-16 15:41 UTC pass; R0 stays 1.02; parity unchanged.
- Stop rules for all three: a parity test would change, a guardrail would change, or CI stays red
  after 3 fixes.

## Phase 1.3 — Run 3 APKs `[d7]` (the owner's order, 2026-10-16, item 4): D5b steps 7–8 closed without code (the owner's (1)), Phase 1.4 backlog; the A check and the h-only trial on the phone; VP9 over HLS stays "No sound"

- **D5b steps 7–8:** the owner chose (1) (plan §9 item 15): closed without code (Plan adapted,
  §4 D5b; no outcome changes). Backlog, Phase 1.4 (plan §10 items 2–4): steps 7–8 only when a
  trigger appears (W reports a BREAKING shape, or a page signal is missing for the copied reader);
  the shape map recorded only (`ClientHealth` and W's report, never read with); the last good
  player ID when the page names none (V and the init check stay on; a player script R6 would not
  fetch, so an exception to D6's "the chain's requests stay R6's", needing the owner's go, never
  after a bot check).
- **Checked before the APKs** (the order, item 3):
  - The A check is in every run 3 APK: `ClientCensusActivity` (debug source set) has the button
    "A check (visionOS 1.04)" since `0a63181`, after run 2 (`1a62b18`); the census tool's launcher
    entry is on when `yft.masterCapture` and `yft.ownSolver` are (`yftOwnerTools`), as in every
    `d7-matrix` APK. Run 3 is the first with it.
  - VP9 over HLS: YouTube's HLS names its sound apart (one AAC rendition). `HlsAudioPairing` (P50)
    pairs each AVC quality with it; VP9 with AAC has no container Android's muxer writes (MP4 takes
    no VP9, WebM no AAC; `AudioVideoMuxCompatibility`), so VP9 qualities stay "No sound" (not a
    fault). At one resolution the row with sound is the one listed (`QuickDownloadChoices`' order),
    so the h-only list has sound up to 1080p (AVC); VP9-only rows say "No sound". Test (core-media,
    CI only): `DefaultVariantResolverTest` "YouTube HLS pairs AVC qualities with the AAC sound and
    leaves VP9 ones silent" (YouTube's form: the rendition without CODECS, the audio codec first in
    each quality's CODECS). Behaviour unchanged. Backlog item 5 (plan §10): sound for VP9 over H
    needs a merge without Android's muxer.
- **Run 3:** `.github/d7-apks.txt` "run 3" with `[d7]` → `d7-matrix` builds the seven APKs on
  this commit. The phone steps (plan §4 D7 "Run 3"; mobile data):
  1. `normal`: "YFT client census" → "A check (visionOS 1.04)" → send the report's first line.
  2. `h-only`: video 1 → the list holds H's HLS qualities only → download one row without
     "No sound" (AVC) → the MP4 plays with picture and sound, its length the video's ±2 s.
  3. Optional: the other five APKs, the lists complete as in run 2.

  Then: 1.04 `DIRECT` with V `PASS` → `ClientCensus.A_CHECK_ALTERNATE` into `MasterClientTable`'s
  visionOS `alternates` (R0 stays 1.02); (ဂ) decided after.
- CI of `e31bb90`: `validate`'s red was transient — `9177a90`, the same code (docs only), passed
  `validate`; `e31bb90`'s `build` was ✅.

## Phase 1.3 — D5b steps 7–8 (the owner's order, item 3, 2026-10-16; docs only): neither changes an outcome; step 7's use needs the parallel agent's file, step 8 changes the chain's requests (guardrail 4); ⏸ the owner (plan §9 item 15)

- **Step 7** (plan §3 L): remember the shape map (located paths ↔ canonical names, by the shape
  fingerprint) so the next lookup normalizes without a full scan; V and the init check still on
  every L row. Found:
  - It saves only the scan (plan §8 "L cost": "the shape map saves the scan, not the checks"): a
    307 KiB answer normalizes in 8.0 ms median on the JVM (budget 20 ms; PROGRESS_PARALLEL), and
    only while a layout change lasts. Rows, wrong rows and MCF stay as they are.
  - Using a map needs a normalize entry that reads only the remembered paths. `ShapeNormalizer`
    has none (each entry scans the whole answer; `Rows.paths` gives the located side), and it and
    `ShapeLocator` are the parallel agent's files. A shortcut from our side (handing the normalizer
    only the remembered parts) skips the whole-answer checks — a DRM marker anywhere, another
    video's formats, two groups that disagree, several status objects: weaker than today
    (guardrail 9). Recording alone fits our files (`ClientHealth` stores maps; nothing records
    one), but nothing would read it.
- **Step 8** (plan §3 L, §4 D5a "S"): when the copied `pageSignals` misses the player ID, the
  signature timestamp or visitor data, L locates them by shape, for S only. Found:
  - Live, the copied reader found all three: S solved MWEB's cipher in every Full run (player ID,
    timestamp) and visionOS was asked again with the page's visitor data; visionOS's answers carry
    no cipher (S on visionOS asked 0 times). Simulator: L14 (every page signal moved) is complete
    15/15 in both ladder columns without step 8; S works only with the own solver on, where MCF is
    5 (V6, O live only). No outcome changes.
  - Since D6 (Plan adapted) S is the chain's own solve path, and the chain reads the page itself
    (copied, unchanged). Located signals could reach it only through a changed page body; the
    chain would then send requests R6 does not: a player script fetch, visionOS again with
    visitor data. After a bot check that breaks guardrail 4 (no InnerTube request R6 would not
    send; (က+) (c): no script fetch of the ladder's own), and always D6's "the chain's requests
    stay R6's" (§4 D6 "As built"). Visitor data from an answer's response context is guardrail
    4's rejected option (ခ) ("never visitor data kept to send from a lookup's first request").
    The timestamp from the player script would need the script before the chain's S request: a
    fetch of the ladder's own or a changed chain request (as A changes the version).
- Options (plan §9 item 15): **(1)** close steps 7–8 without code (Plan adapted: no outcome
  change; step 7 saves ≤ 20 ms at the price of a weaker check or the parallel agent's file;
  step 8 runs into guardrail 4), then the `[d7]` APK — recommended; **(2)** step 7 with the
  parallel agent: a map-based entry in `ShapeNormalizer` that keeps the whole-answer checks, then
  I record, use and drop the maps (`ClientLadder`, `ClientHealth`); **(3)** step 8 for the player
  ID only: L finds the player script address by shape on the page and the ladder hands the chain
  the page with it, only when the copied reader finds none and no bot check came in this lookup
  (one script fetch R6 would not make: an exception to D6's "the chain's requests stay R6's",
  never after a bot check); the timestamp and visitor data stay out.
- CI of `e31bb90` (D5c): `build` ✅ (Master tests and the opt-in APK); `validate` ❌ 39 s into its
  Gradle step (green runs spend ~9 min there; the job log needs admin rights, so the cause is not
  visible). main has not moved since the pull request's base. The pull request's checks run on
  every push, so this push runs `validate` again on the same code.

## Phase 1.3 — D5c h-only trial built (the owner's order, item 2, 2026-10-16): a seventh phone-check APK lists H's one HLS row alone; H's row now carries the video key

- The owner's order, item 2: a trial mode that leaves only the H row, so the phone download goes
  through H (plan §4 D5c "Done": one download per audio form D0 saw, a playable MP4 with both
  tracks, duration ±2 s).
- ✅ Built (`LadderTrial.H_ONLY`; `-Pyft.ladderSkip=h-only`, debug only; workflow `ladder_skip`
  choice `h-only`; `d7-matrix` mode `h-only`, artifact `yft-d7-h-only`, so seven APKs): visionOS's
  answer reaches the chain as it came (no layout change; no A in a trial); after the chain and the
  own solver's V the result is H's one HLS row alone — the chain's rows are left out, complete or
  not — or, when H gives no row, no row (the chain's failure, else "no media found") with H's
  reason in the notes ("H: no HLS address", "H: visionOS's answer AGE_CHECK", "H: <why>"). Nothing
  is recorded as repaired; health stays in memory as in every trial APK. The other modes and the
  flag-off path: unchanged.
- 🐞 Found and fixed (every mode): H's row had no video key. The module tags the chain's rows
  `site:contentId`, and the app's browser hook keeps only rows with the lookup's key, so an H row
  never reached the phone's list (the simulator counts the module's result, so it did not show).
  H's row now carries the chain's key (`ClientLadder.hRow`); a test checks it in the normal mode.
- Simulator: `SimHls.separateAudio` (an `EXT-X-MEDIA` audio rendition, `AUDIO=` on each variant;
  off by default, every output byte-identical). Tests: `HOnlyTrialTest` 4 — every class × own
  solver off / on: wrong 0, no credentials, ≤ 1 row and it is H's HLS row with the chain's key,
  no row ⇒ H's reason in the notes, every class with an H path complete; both audio forms (muxed,
  sound apart); no H row (no HLS address, an encryption key, a bad segment start, V9, an age
  check) → no row, never the chain's rows; the normal ladder's H row carries the key.
  `LadderTrialTest`: `h-only`'s label, refused with a mutation, changes no answer, B0 complete
  with one row. Module 652 tests, 0 failed, 2 skipped. `ladder-h-only.txt` (990 lookups): 440
  complete with the one H row, 550 stop with H's reason (no HLS address 190, master 140, a
  verdict about the video 160, segment 40, length 10, no visionOS answer 10), wrong 0. Every other
  simulator output byte-identical to `0a63181`, except `ladder-trials.txt` (+10 `h-only` lines).
  Workflow tests 17 OK; drift 0.
- On the phone (the next `[d7]` APK, `h-only`): D0 saw visionOS's HLS with the sound apart only
  (an audio rendition; codecs avc1, mp4a, vp09; 17 variants ≤ 2160p), so one download, sound
  apart; the muxed form is the simulator's only. The app pairs each AVC quality with its AAC
  rendition and merges both into one MP4 (P50, `HlsAudioPairing`); VP9 qualities stay "No sound",
  so the owner picks an AVC quality with sound.
- Next: D5b steps 7–8, then one `[d7]` APK (seven) where the owner runs the A check (`normal`)
  and the H download (`h-only`).

## Phase 1.3 — A check built (the owner's choice (2), 2026-10-16): a census "A check" button with NewPipe's visionOS 1.04; the owner runs it on the next APK

- The owner chose (2) (2026-10-16): on video 2, visionOS 1.02 as R6 asks it (`R0`, no visitor
  data); only if that answer is `DIRECT`, NewPipe's 1.04 the same way, once; a bot check or HTTP
  429 stops at once. If 1.04 is `DIRECT` with a V pass, it becomes visionOS's bundled alternate
  (source NewPipe, 2026-08-06) — after the owner's run on the phone; R0 stays 1.02 and parity
  does not change. Not (3). Then D5c, D5b steps 7–8 and one `[d7]` APK with the three.
- ✅ Built (`ClientCensus.kt`, mode `A_CHECK`; the census tool's third button "A check (visionOS
  1.04)"): video 2 only. 1.02 is the full census's own `R0` request (R6's, body and headers);
  1.04 is that request with only the version changed (the body's `clientVersion` and
  `X-YouTube-Client-Version`: exactly the ladder's A rung edit, `VersionSource.withVersion`); V on
  one AVC row of 1.04's answer, only when it is `DIRECT`. ≤ 4 requests, ≤ 2 MB; no watch page,
  no source read, no `R0+vd`. A bot check or 429 on either request stops it at once with no
  census for 30 min. The first line gives the outcome, e.g. "A CHECK — visionOS 1.02 DIRECT;
  visionOS 1.04 DIRECT, V PASS: 1.04 can be visionOS's bundled alternate (NewPipe, 2026-08-06)";
  the run is reported but not kept in `census-last.json` (no diff, the stored runs stay, only a
  cooldown is kept). 1.04 is not bundled yet (`MasterClientTable` visionOS `alternates` stays
  empty; `ClientCensus.A_CHECK_ALTERNATE` holds it with source and date): the phone decides.
- Tests: `ClientCensusACheckTest` 10 — the order, and both requests equal to the full census's
  `R0` and the A rung's edit; a bot check or 429 on 1.02 and on 1.04 → stop, 30 min cooldown, no
  V; 1.02 not `DIRECT` → no 1.04; 1.04 rejected (HTTP 400 or an "update" answer), a 403 (token)
  or V unknown → not an alternate; not kept for the diff (the next full census's diff unchanged);
  video 2 needed; no address, ID or visitor data in the report or state; 1.04 valid for visionOS's
  pattern, newer than 1.02, with source and date. Module 648 tests, 0 failed, 2 skipped;
  `CensusR6ParityTest` green; the flag-off path and the R6 columns untouched (census code only).
- Known limit (plan §4 D5a "A check"): how YouTube refuses a visionOS version stays unknown. The
  judge reads `VERSION_REJECTED` from HTTP 400 or the update / unsupported texts; if YouTube
  refuses another way, A does not fire and the lookup has R6's outcome (no worse), and the weekly
  check shows the change.
- Backlog (plan §10, new): the census's App Store source reads the iOS app; YouTube's visionOS
  app is a listing of its own (1.05.34: three parts, not visionOS's `^\d{1,2}\.\d{2}$`).
- Next: D5c (a trial mode with the H row only), then D5b steps 7–8, then one `[d7]` APK with the
  three, where the owner runs the A check; (ဂ) after its result.

## Phase 1.3 — A check (the owner's order, item 1, 2026-10-16; docs only): no visionOS version older than 1.02 in any public reference; ⏸ the owner: how the A check goes on (guardrail 5) → (2), above

- The owner's order (2026-10-16), in this order, CI green after each commit: (1) the A check
  (ခ) — offline, many old visionOS versions in tests and the simulator; online, an "A check"
  button in the census on video 2: 1.02 as R6 asks it (no visitor data), then, only if that
  answer is `DIRECT`, ≤ 3 old versions; only versions that really existed in yt-dlp / YouTube.js
  git history (guardrail 5); a bot check or a 429 stops at once (guardrail 4); (2) D5c, a trial
  mode with the H row only (the phone download); (3) D5b steps 7–8; then one `[d7]` APK with all
  three. (ဂ) is decided after the A check.
- Searched first (public GitHub history and the App Store's lookup, read only, from the
  workspace; no YouTube request):
  - yt-dlp `yt_dlp/extractor/youtube/_base.py`: `visionos` added 2026-07-09 (#17184) with
    `clientVersion` 1.02. All 6 commits of that pull request and all 5 later commits on the file
    (to 2026-10-10): 1.02 only. Before it: no visionOS.
  - YouTube.js `src/utils/Constants.ts`: `VISIONOS` added 2026-08-13 (#1213, one commit) with
    1.02; no later change. Before it: none.
  - NewPipe Extractor (guardrail 5's third reference), `ClientsConstants.java`: 1.02 from
    2026-06-07, **1.04** since 2026-08-06 (newer, not older).
  - The App Store: YouTube's visionOS app is a listing of its own (bundle
    `com.google.visionosyoutube`), not the iOS app the census reads (why D0 found no visionOS
    value there): version 1.05.34 (2026-09-19), first released 2026-02-12.
- Result: **no visionOS version older than 1.02 exists in any public reference.** The A check's
  old versions would have to be invented or counted down, which guardrail 5 forbids ("never
  invented, counted up or brute-forced"). Stopped before any code (the prompt's STOP AND ASK: a
  rule would need to change): no button, no test, no live request; items 2 and 3 not started.
- Known already: 1.02 is itself old — the App Store's app is at 1.05.34, NewPipe sends 1.04 — and
  YouTube still answers it `DIRECT` (the census: R0, or `R0+vd` after R0's bot check). How
  YouTube would refuse a version stays unknown (the judge's `VERSION_REJECTED`: HTTP 400, or the
  update / unsupported texts). And today A could not heal a rejected 1.02: yt-dlp's table names
  1.02 (not newer) and there is no bundled alternate.
- ⏸ The owner, one of: (1) end the A check with this result; go on with D5c, D5b and the `[d7]`
  APK. (2) The A check with NewPipe's 1.04 instead (real and newer; guardrail 5 allows NewPipe
  for a bundled alternate): video 2, 1.02 as R6 asks it, then only if `DIRECT` 1.04 the same way,
  once — shows whether A's version-only re-ask works live and whether 1.04 can be A's bundled
  alternate (then A could heal a rejected 1.02 while yt-dlp lags); not how YouTube refuses.
  (3) A guardrail 5 exception for the census button only (A unchanged): ≤ 3 invented old
  versions (for example 1.01, 1.00, 0.10) — the only way to see how YouTube refuses. Recommended:
  (2). (ဂ) waits for the A check's result.

## Phase 1.3 — A (D5a, the follow-up after the close, 2026-10-16): self-healed visionOS version; the own solver off MCF 95 → 65

- ✅ A (Plan adapted; plan §4 D5a "As built (A)"), in `VersionSource.kt` (new) and
  `ClientLadder.kt` (not in `Rungs.kt`, the parallel agent's file). When the R6 chain's visionOS
  request is answered `VERSION_REJECTED` (once a lookup; never in a live-check trial; only when
  visionOS may be asked), A sends the chain's request again with only the version changed (the
  `clientVersion` field and the `X-YouTube-Client-Version` header), ≤ 3 requests a lookup
  (guardrail 7), in guardrail 5's order: the learned version → a self-healed version → the
  bundled alternates (none today).
- Self-healed (`VersionSource`): yt-dlp's table on GitHub raw (§9 item 3's host; YouTube.js stays
  census-only); ≤ 1 read a day, claimed in the health before it reads, none in a bot-check
  window; the User-Agent only, no cookie, no redirect followed, ≤ 64 KiB, `text/plain`; one value
  through the census reader and the visionOS pattern, newer than the bundled one; kept in memory
  only.
- Never a wrong row: V on every A answer (provisional, no last-hour skip); only a V pass (or L
  rows) reaches the chain, and then the version and its source are learned (the health file).
  Otherwise A's answer is dropped: the chain, H and the health get visionOS's own rejection (its
  24 h cooldown) — R6's outcome, never worse. A bot check or a 429 stops A (the 30 min window);
  no answer, or an answer about another video, stops A.
- App: `OkHttpVersionSourceReader` (`MasterLadderSwitch.kt`): HTTPS on `raw.githubusercontent.com`
  only, no redirect followed, no cookie, the User-Agent only, ≤ 64 KiB, 10 s. Android's A is not
  wired (yt-dlp's Android version = bundled; no alternates). Comments: `CensusSources`,
  `ClientTable`.
- Tests: `VersionRungTest` 13 (12 red with A off; the live-check trial test green either way),
  `VersionSourceTest` 11, `ClientHealthTest` + 1 (11), `ClientLadderSimTest`: a source read at
  most once a case (one day), only in V1, V9, H1 and Y1–Y6; app `OkHttpVersionSourceReaderTest` 6
  (3 red with the redirect, header and host rules removed; run locally in a JVM copy, the app's
  run in CI). `:extractor-master:test` 638, 0 failed, 2 skipped; flag-off parity green.
- Simulator (plan §5): the own solver off MCF 95 → **65** — V1, Y1, Y5 and Y6 complete through A
  (2 player requests, no page fetch, 1 source read; Y5: 1 read for 3 lookups); left: Y2 20, Y3 10
  and Y4 5 (no source gives a newer version A could send, or it is rejected too), V7 5, V9 5, H1
  10 and F4 5 (need S), V6 5 (O, live only). The own solver on: MCF 5, unchanged (V1 now without
  the page fetch). Wrong 0 everywhere; the R6 columns, the trials and v7 byte-identical. The own
  solver off target: MCF ≤ 65 (plan §5.1).
- Known limit: V's budget is 4 a day per client, so while the bundled version stays rejected A
  completes at most 4 lookups a day; then A's answer is dropped (R6's outcome) and visionOS cools
  down 24 h. An APK with a newer bundled version ends it. And A waits 30 min after a bot check
  ((က+), plan §9 item 10): visionOS's first request met one on 24 of 30 census videos, so live A
  would run on few lookups; the simulator's classes have no bot check (95 → 65 is its upper
  bound; found 2026-10-16 while planning the owner's online check).
- No phone check now: A acts only when visionOS's bundled 1.02 is rejected and yt-dlp's table
  names a newer version (today both read 1.02). Drift 0. Open, not part of the close: L steps
  7–8 (D5b), H's phone download (D5c).

## Phase 1.3 — closed (the owner's decision (ခ), 2026-10-16; docs only): the proposed targets; next: A (D5a) as a follow-up, continuous

- The owner's decision (ခ) (2026-10-16, after the census FULL on the 1 MiB APK): close Phase 1.3
  with the targets proposed at D7 "Done" (plan §5.1 "Closing targets", §9 item 13): every flag on
  (the D7 APKs): MCF ≤ 5, V6 only (an O rung, live only), until an O rung passes the gate (§9 item
  9); wrong 0 everywhere; the own solver off: 95 at the close (V1 and Y1–Y6 65, the classes A is
  for; V7, V9, H1 and F4 25, which need S; V6 5), its target from the simulator once A is in.
- Then A (D5a: `VersionSource`, self-healed versions; guardrail 5, §9 item 3's host) as a
  follow-up after the close (owner); the owner: go on without stopping (continuous).
- Open, not part of the close: L steps 7–8 (D5b), H's phone download (D5c).
- At the close: D0–D7 done, D8 dropped; client table v3 (visionOS R0 live, H live, Android and
  MWEB provisional, embedded measured); wrong 0 in every phone run and in the simulator; MCF 5
  (own solver on) / 95 (off); drift 0; last code `c9c5959` (its APK on the phone).

## Phase 1.3 — census FULL on the 1 MiB APK (owner, 2026-10-16 06:56 UTC; docs only): H 9 / 9, no `BROKEN`; ⏸ the owner: A (D5a) next, or close with the proposed targets

- ✅ Census FULL on `c9c5959`'s APK (installed as an update), mobile data, 1 h 30 min after the
  05:26 UTC run: 10 census videos, 79 requests, 2781 KB, 45 s, not stopped. No `BROKEN`.
  `CHANGED (3)`, each expected:
  - H visionOS video 10: `variant too-large → 2xx; segment skip → 2xx` (§7 step 7) — the line the
    1 MiB cap predicted: the variant playlist fits. Its H row: master `2xx`, 8 variants ≤ 240p,
    variant `2xx` (212 segments, 1203.4 s, duration match), segment `2xx`, key `NONE`, audio
    separate, 3 requests.
  - MWEB R0 shape −`streamingData.probeUrl` (step 6): the known come-and-go (+ at 05:26 UTC); not
    BREAKING; no Master code reads it; nothing to do.
  - The App Store reachable again (step 3; `NETWORK` at 05:26 UTC, as in tests 1 and 2): a
    measured source, not used; its listing names no visionOS device (no visionOS value); yt-dlp
    and YouTube.js read 1.02 (= bundled); nothing to do.
  - `INFO`: 8 visionOS R0 bot checks answered as R6 (`R0+vd` `DIRECT`, V pass), as before.
- L steps on the 9 `DIRECT` visionOS answers: round-trip, L1, L2, L3, L7 9/9 `same`, L5 9 `n/a`.
  Every V that ran passed; wrong 0. **H 9 of 9** `2xx` / `2xx` / `2xx` (videos 1–5 and 7–10; key
  `NONE`, audio separate, duration within 2 s; video 6 is `REFUSED` for visionOS, so no H) — the
  first census with every asked H video passing. Cost: +1 request against 05:26 UTC (video 10's
  segment); 2781 KB (FULL's budget 6 MiB).
- Nothing to fix; no code change. Open for the owner (since D7 "Done", plan §4 D7): A (D5a:
  `VersionSource`, self-healed versions) next, or close Phase 1.3 with the proposed targets (every
  flag on: MCF ≤ 5, V6 only, until an O rung passes the gate; the own solver off: the simulator's
  number once A is in). Also open outside D7: L steps 7–8 (D5b), H's phone download (D5c).

## Phase 1.3 — D7 closed (census FULL on the H fix APK, owner, 2026-10-16 05:26 UTC) + guardrail 6: HLS variant cap 1 MiB (owner); ⏸ the owner: one census FULL on this APK

- ✅ Census FULL on `7277fd8`'s APK (installed as an update), mobile data, 1 h 25 min after run
  2's: 10 census videos, 78 requests, 2791 KB, 71 s, not stopped. `BROKEN (4)`:
  - H visionOS video 10: `variant 2xx → too-large; segment no-segment → skip` (§7 step 7) — the
    line the fix predicted: the status fix works on the phone, and the variant playlist really
    passes 256 KiB (cause confirmed; not an empty playlist). Its H row: master `2xx`, 8 variants
    ≤ 240p, variant `too-large`, 2 requests.
  - visionOS `R0+vd` shape −`streamingData.probeUrl`, MWEB R0 shape +`streamingData.probeUrl`
    (step 6): the known come-and-go; no Master code reads it; not BREAKING; nothing to do.
  - The App Store unreachable (`NETWORK`, step 3; as in tests 1 and 2): a measured source, not
    used; yt-dlp and YouTube.js read 1.02 (= bundled); not `VERSION_REJECTED`; nothing to do.
  - `INFO`: 8 visionOS R0 bot checks answered as R6 (`R0+vd` `DIRECT`, V pass), as before.
- L steps on the 9 `DIRECT` visionOS answers: round-trip, L1, L2, L3, L7 9/9 `same`, L5 9 `n/a`.
  Every V that ran passed; wrong 0; H 8 of 9 `2xx` / `2xx` / `2xx` (video 10 `too-large` →
  unknown, no row). **D7 closed**: nothing left in D7.
- Guardrail 6 (the owner's decision (ခ), 2026-10-16; plan §9 item 12): the HLS **variant**
  playlist's cap 256 KiB → **1 MiB** (`HlsCheck.MAX_VARIANT_BYTES`, new); the master's stays
  256 KiB (`MAX_MASTER_BYTES`; both were the shared `MAX_PLAYLIST_BYTES`); HlsLeg's 64 KiB segment
  start unchanged. The census and HlsLeg read with it: HlsLeg's code unchanged (its
  `SegmentReading` passes HlsCheck's cap through), its step-3 comment updated with the owner's OK
  (one line in PROGRESS_PARALLEL.md). The app's client reads any cap (`readBounded`): no app change.
- Effect: census video 10 (1203 s; its playlist ≈ 0.28 MB at the test's ≈ 1.2 KB a segment line,
  an estimate) fits; 1 MiB holds ≈ 74 min that way; a longer video → `too-large` → unknown, no
  row, never wrong (§7 known limit). Cost: ≤ 1 MiB more per H check, only on a long video; FULL's
  budget 6 MiB (runs: 2.8–3.4 MB), W's 2 MiB (W's H runs on video 1 or 2, both short).
- Tests: `HlsLongPlaylistTest` 5 → 7 (the caps pinned; video 10's playlist → `2xx`, 241 segments,
  duration match, the leg's row; over 1 MiB → `too-large` / unknown, no row; the master's cap
  stays 256 KiB), 2 red on `7277fd8` (variant `too-large`; the leg's unknown on `VARIANT`), green
  after; `HlsCheckTest`'s body-limit line (master 256 KiB, variant 1 MiB). 613 module tests (611 +
  2), 0 failed, 2 skipped; flag-off parity (`MasterYouTubeParityTest`) 6 / 6; simulator outputs
  byte-identical (ladder wrong 0, MCF 95 / 5); H matrix unchanged (300 runs, wrong 0); workflow
  tests 17; drift 0 (base and main).
- Next census FULL on this APK (≥ 1 h after the last): `CHANGED H visionos video 10: variant
  too-large → 2xx; segment skip → 2xx` once (§7 step 7): expected, nothing to do; no such line →
  still over 1 MiB (the known limit).

## Phase 1.3 — H status fix: census video 10's `no-segment` was a too-large playlist read as `2xx` (owner, 2026-10-16); ⏸ the owner: one census FULL on this APK

- ✅ Cause, in D0's `HlsCheck.kt` (`1135afe`, ours; not one of the parallel agent's files): the
  app's client (`OkHttpExtractorClient`) answers a `2xx` body over the read cap as
  `Failure(RESPONSE_TOO_LARGE, <that 2xx code>)`; `HlsCheck.statusOf` tested the code
  (`200..299` → `2xx`) before the reason. A variant playlist over 256 KiB therefore read `2xx`
  with no body → 0 segments → `no-segment`, 2 requests: census video 10's H line in tests 1–3
  and run 2. Not YouTube's side, as the docs had read it (§7, the Final plan table, §5.2).
- Evidence: ✅ the new test's result on the old code equals that line field for field (master
  and variant `2xx`, `no-segment`, 8 variants ≤ 240p, audio separate, avc1 + mp4a + vp09, 0
  segments, no duration, 2 requests); ✅ video 10 is the census's longest video (1203 s; the
  other H videos 1–5 and 7–9 are ≤ 888 s and read 2xx / 2xx / 2xx in run 2; video 9's 888 s =
  171 segments); ✅ the master was read whole (8 variants; on the old code an over-cap master
  read `not-m3u8`), so its ≤ 240p stays YouTube's. Not measured: the real playlist's size (the
  census never prints it; nothing asks YouTube here); the test assumes about 1.2 KB a segment
  line (888 s fits, 1203 s does not). The next census decides: `too-large` confirms; `2xx` +
  `no-segment` again → a really empty playlist.
- Fix (`HlsCheck.statusOf`): `RESPONSE_TOO_LARGE` first → `too-large`; a failed read is never
  `2xx` (another `2xx`-coded failure → `other`). HlsLeg (the parallel agent's) unchanged: it
  counts `too-large` and `other` as unknown, so video 10 → H unknown (`VARIANT`), no row, never
  wrong (before: fail on `SEGMENT`, no row). A first segment over the leg's 64 KiB with 200 (a
  server that ignores `Range`) → unknown too (before: fail on `SEGMENT_START`).
- Tests: `HlsLongPlaylistTest` (5, new), red on `4628f26` (4 of 5: variant `2xx` / `no-segment`;
  leg fail `SEGMENT`; `statusOf(200, too large)` = `2xx`; segment fail `SEGMENT_START`), green
  after. 611 module tests (606 + 5), 0 failed, 2 skipped; flag-off parity
  (`MasterYouTubeParityTest`) green, the app's in CI; simulator outputs byte-identical (ladder
  wrong 0, MCF 95 / 5); H matrix unchanged (300 runs, wrong 0); workflow tests 17; drift 0 (base
  and main).
- Known limit (runbook §7): some long videos' variant playlist passes 256 KiB → H `too-large` →
  unknown, no H row, never wrong; the cap stays (guardrail 6) unless the owner raises it.
- The first census on this APK prints once `BROKEN H visionos video 10: variant 2xx → too-large;
  segment no-segment → skip` (§7 step 7): expected (the old `2xx` was the misreading); later runs
  print no line for it.

## Phase 1.3 — D7 done: census FULL on run 2 (owner, 2026-10-16 04:01 UTC; docs only): L steps 9/9 same; §4 table brought up to date; ⏸ the owner: A (D5a) next, or close with the proposed targets

- ✅ Census FULL on run 2's `normal` APK (`1a62b18`, installed as an update), mobile data, 2 h 57
  min after W 5: 10 census videos, 78 requests, 3379 KB, 45 s, not stopped (D7: ≈ 3.4 MB, ≈ 1 min).
- ✅ L steps on the 9 `DIRECT` visionOS answers (in memory, no request): round-trip, L1, L2, L3
  and L7 9/9 `same`, L5 9 `n/a` (no cipher). Run 1 read 0/9 (L stopped on its DRM marker): the
  narrowed marker (`83ce453`) works on live answers.
- ✅ Wrong 0, every V `PASS`. visionOS R0: census video 2 `DIRECT` (27 adaptive rows ≤ 2160p); a
  bot check on the other 8 asked, each answered as R6 (visionOS with its visitor data: `DIRECT`,
  V pass; `INFO`). Census video 6: visionOS `REFUSED` ("This video is not available", as
  before). On census videos 2 and 6: Android `SABR_ONLY` (the 360p progressive row), MWEB
  `NEEDS_SCRIPT` → S ok-sig+n, embedded `REFUSED` (152-18). H 8 of 9 2xx / 2xx / 2xx, no key,
  the length matched; census video 10 no segment (unchanged).
- `CHANGED (1)`: visionOS `R0+vd` shape +1 −0 (+`streamingData.probeUrl`; §7 step 6). Not
  BREAKING (nothing the copied parser reads went away); no Master code reads the key; L and the
  parser read every row. Test 2 (2026-10-15) had lost it (−), now it is back: it comes and goes
  on YouTube's side, like MWEB's `adPlacements`. Nothing to do (runbook §7 notes it).
- Versions as before: the MWEB page's newer version accepted; visionOS `1.02` = yt-dlp and
  YouTube.js. Nothing to update.
- ✅ D7 done (plan §4 D7 "Done"): the phone check, the census L steps (this run), W 4 and W 5,
  runbook §7 (+ `probeUrl`, the full census's cost). §5.2: Parity, Faults, Budget, Live, W,
  Versions and flag off met, wrong 0 everywhere. **Not met: MCF = 0 in the simulator** — own
  solver on 5 (V6: O, live only); own solver off 95 (V1 and Y1–Y6 65, the version classes A is
  for; V7, V9, H1 and F4 25, which need S, i.e. the own solver; V6 5). Proposed targets: with
  every flag on, as the D7 APKs: MCF ≤ 5, V6 only, until an O rung passes the gate (§9 item 9);
  with the own solver off: the simulator's number once A is in.
- ✅ Plan §4 table brought up to date (owner, 2026-10-16): D3–D6 had read TODO since the Final
  plan. Now: D3 core done; D4 built; D5a S and Z, **A not built**; D5b steps 1–6 done, **steps
  7–8 not built** (the shape-map memory — `ClientHealth` stores maps, nothing records one — and
  L's page locate for S); D5c built and H live, **its phone download not done** (D7's
  `skip-visionos-adaptive` lists were complete through the chain, so H added no row); D6 core
  and O rungs done, A with D5a; D7 done. Final plan items 4, 5 and 7 and the Burmese summary
  likewise.
- ⏸ The owner: build A next (D5a: after `VERSION_REJECTED`, a self-healed visionOS version from
  the reference tables on GitHub raw, ≤ 1 fetch a day, §9 item 3; then the simulator's
  live-backed columns and L steps 7–8), or close Phase 1.3 with the targets above and leave A,
  L steps 7–8 and H's phone download for later. Weekly check: once a week on run 2's `normal`
  APK (update only, never uninstall).

## Phase 1.3 — D7 weekly checks (owner, 2026-10-15/16 UTC; docs only): W 4 and W 5 NO CHANGE within budget; runbook §7

- ✅ W 4 (the owner's D7 W1): 2026-10-15 23:55 UTC (72 min after the census FULL), mobile data,
  run 1's `normal` APK (`80ccb10`): `NO CHANGE`; 3 census videos, 15 requests, 623 KB, 7 s.
- ✅ W 5 (D7 W2): 2026-10-16 01:04 UTC (69 min after W 4), the same APK and network: `NO CHANGE`;
  3 census videos, 15 requests, 622 KB, 9 s.
- Both within the limits (20 requests, 2 MB, 60 s; §5.2 W: twice ✅). visionOS R0 bot check on
  census videos 1 and 5, each answered as R6 (visionOS with its visitor data: `DIRECT`, V pass);
  H on census video 1 2xx, the length matched.
- No re-run on run 2's APK (the owner offered, 2026-10-16): run 1 and run 2 differ only in
  `ShapeLocator`'s DRM marker and the full census's L note (`83ce453`). W has no L steps, and its
  visionOS answers were a bot check (judged before DRM) and R6's `DIRECT`. The weekly checks from
  now on run on run 2.
- ✅ Runbook §7: what D7's census runs showed (bot check → R6 with visitor data; MWEB shape
  ±`adPlacements`; H census video 10 no segment; how to read the L steps; W's cost).
- ⏸ Left for D7: one census FULL on run 2's `normal` APK (`1a62b18`; update, no uninstall),
  ≥ 1 h after W 5 (from 02:04 UTC): L steps expected all `same` (L5 n/a); then D7 Done.

## Phase 1.3 — D7 fix (2026-10-15): L's DRM marker narrowed to the copied parser's keys; L stops named in the census

- ✅ Reproduced first on `8164a39` (tests, no request): a live-like visionOS answer without DRM
  (status OK; adaptive rows with the live keys and value types; HLS; a SABR address without
  itag; tracking without a DRM session; a DRM-named player setting and a FAIRPLAY key-system
  list) → `ShapeNormalizer` `Stop(DRM_PROTECTED)`, and the census L line read
  `round-trip none (no rows), L1 none (no rows), …` as in the owner's census.
- ✅ `ShapeLocator.kt` (owner exception for this step): the DRM marker is one of the copied
  parser's DRM keys (`drmParams`, `drmSessionId`, `drmFamilies`, `drmTrackType`) with a value
  under any container (never weaker than the parser, also after L1, L2 or L7 moved or renamed
  it; the deep-cut check counts these keys too), or a WIDEVINE / PLAYREADY / FAIRPLAY /
  `DRM_TRACK_TYPE_…` text inside a format-like object (a format's renamed DRM key, as the
  simulator's L12). The starts-with / contains key rule and DRM texts outside a format are gone.
  `Scan.drmPath` names where the marker sits (a key path, never a value).
- ✅ `ClientCensus.kt`: an L step that stops reads `none (stop <reason>)` (was `none (no rows)`);
  a DRM stop adds `; DRM marker at <key path>`.
- ✅ Tests: `ShapeNormalizerTest` +1 (the live-like answer gives the direct parse's rows on the
  round-trip and after L1, L2, L3, L7; with each of the parser's four keys added, the parser's
  own stop on every step); `ShapeLocatorTest` +1 (marker paths; keys and texts that are no
  marker); L matrix +5 cases (`L12 params`, `L12 session`, `L12 families`, `L12 track`: stop;
  `L1 config`: rows, complete as L1); `ClientCensusTest` +1 (the L line `same` on every step; a
  DRM text in each format → `none (stop DRM_PROTECTED; DRM marker at …)`). Red on `8164a39`:
  4 failures (the new normalizer and census tests, the L matrix's and the judge's `L1 config`).
- ✅ Checks: module tests 606 (603 + 3), 0 failed, 2 skipped; flag-off parity
  (`MasterYouTubeParityTest`) green, the app's in CI; simulator outputs byte-identical to
  `8164a39` (ladder wrong 0, MCF 95 / 5); H matrix unchanged; L matrix only its five new cases
  (wrong 0); workflow tests 17; drift 0 (base and main).
- ⏸ Next: CI build + validate; six APKs (`.github/d7-apks.txt` run 2, `[d7]`); the owner installs
  `normal` and runs one census FULL on mobile data (≥ 1 h after 22:43 UTC): L steps expected 9/9
  same (L5 n/a), wrong 0; if not, the note's DRM marker path.

## Phase 1.3 — D7 phone check (owner, 2026-10-15 UTC, census 22:43 UTC; docs only); ⏸ L stops on every live answer

- ✅ Six APKs (`80ccb10`), all flags on, mobile data, the owner's 11 list videos each: `normal`
  11/11 complete (videos 7 and 11 listed on the reinstall), `skip-visionos` 11/11,
  `skip-visionos-adaptive` 11/11 (no HLS row), `mutate-L1` 11/11, `mutate-L3` 11/11, `mutate-L5`
  11/11. Every list as the normal APK's (same top rows and sizes); wrong 0; no bot-check message,
  no error, every row with a size. Video 9's top row (1920×818) reads 720p (by its short side).
- ✅ Video 1's top row (240p) downloaded once, on `normal`: sound, length 0:19 (Plan adapted: one
  download in all instead of one per APK; every trial list was the normal list).
- ✅ Census FULL on `normal` (mobile data): 10 census videos, 78 requests, 2791 KB, 33 s.
  CHANGED 3: visionOS R0 census video 2 V UNKNOWN → PASS; MWEB shape +1 (`adPlacements`);
  H visionOS census video 2 master net → 2xx. visionOS without visitor data: bot check on 8 of 9,
  each answered as R6 (visionOS with its visitor data: `DIRECT`, V PASS). H: 8 of 9 2xx with the
  length matched; census video 10 no segment (unchanged). MWEB page version accepted.
- ❌ Census L steps: round-trip, L1, L2, L3 and L7 0/9 same, L5 9 n/a; each `none (no rows)` =
  `ShapeNormalizer` gave `Stop`, not rows. Cause (read from the code, not reproduced): an OK,
  not-live answer stops only on DRM, and `ShapeLocator`'s DRM marker (any key starting with drm or
  holding Drm / DRM, with a value, anywhere in the answer; a WIDEVINE / PLAYREADY / FAIRPLAY text)
  is wider than the copied parser's (`streamingData.drmParams`, `playbackTracking.drmSessionId`, a
  format's `drmFamilies` / `drmTrackType`), which saw no DRM here. So live, L gives no rows; the
  phone lists stayed complete through R6. D7's "L expected" is not met.
- ⏸ Needs: `ShapeLocator.kt`'s DRM marker narrowed to the parser's places (the parallel agent's
  file) and an L case with a non-DRM key holding Drm; then one census FULL again (L steps). Left:
  two timed weekly checks (≥ 1 h apart), runbook §7.

## Phase 1.3 — D7 APKs (2026-10-15): job d7-matrix, six phone-check APKs from a push with [d7]

- ✅ `master-optin-debug-apk.yml` job `d7-matrix` (after `build`; only a push whose commit message
  carries `[d7]`): `normal`, `skip-visionos`, `skip-visionos-adaptive`, `mutate-L1`, `mutate-L3`,
  `mutate-L5`; all flags on; assemble only; spike key, cert SHA-256, package and versionCode
  checked (a mismatch uploads nothing); artifacts `yft-d7-<mode>`, 14 days. `build` unchanged.
- ✅ `.github/d7-apks.txt`: the trigger file (a docs-only push runs nothing).
- ✅ Trial APKs keep the ladder's health in memory only (never the file); `LadderTrialTest` +1.
- ✅ Workflow tests 17 (+5 D7; `contains()` in the evaluator); module tests 603, 0 failed; drift 0.
- "Complete" on the phone and the data budget: plan §4 D7. Left: CI, the six links, the phone check.

## Phase 1.3 — D7 prep (2026-10-15): live-check switches, health file, census fixes

- ✅ `ladder/LadderTrial.kt`: `visionos`, `visionos-adaptive`, `L1`, `L2`, `L3`, `L5`, `L7` on
  visionOS's live answer in memory (debug only). App `-Pyft.ladderSkip` / `-Pyft.ladderMutate`
  (checked, one at a time); workflow inputs `ladder_skip` / `ladder_mutate` (default `none`).
- ✅ One ladder per process; health in `files/master/client-health.json`.
- ✅ Census fixes: MWEB's address-less flip → `INFO`; the R6 note → "no other client for this video".
- ✅ `LadderTrialTest` (3): B0 × every mode × own solver off / on, wrong 0; L modes complete via L.
- ✅ Census L steps (full census, in memory): round-trip + L1, L2, L3, L5, L7 per `DIRECT` visionOS
  answer → `same` / `differs` / `n/a` / `none (<why>)`, and a counts note.
- CI: `4f884ec` ✅, `be0c52e` ✅, `6402fea` ✅, `e56cc3f` ✅, `a4ffd02` ✅. Left: the owner's phone check.

## Phase 1.3 — D5a S + D6 O rungs (2026-10-15)

- ✅ O rungs (Plan adapted): the chain's Android, MWEB and embedded answers judged for health; V on
  their direct rows (per-client budget; provisional = every lookup); a 403 list refused by the merge
  rule. Simulator totals unchanged (wrong 0; MCF 95 / 5); Android V 225 pass, 20 refused, 25 unknown.
- ✅ S (Plan adapted): own solver in the R6 chain under the ladder (copied challenge and apply code,
  SelfCheck, `SignatureGate` V). App: `MasterLadderSwitch.youTube` (ladder off = R6 as before; on =
  own runner only with the own solver flag, else none), `AndroidBrowserMasterFallback.create`
  `playerScripts`, `BrowserViewModel` injects `PlayerScriptRunner`.
- Local: `:extractor-master:test` 598, 0 failed, 2 skipped; drift 0. Left: A (`VersionSource`), D7 prep.

## Phase 1.3 — D3 + D6 core (2026-10-15): health v2, the ladder v1 in the R6 chain's HTTP path, flag

- ✅ `ladder/ClientHealth.kt` (D3): per-client class, times, pass, cooldown (REFUSED 1 h; SABR_ONLY,
  TOKEN_NEEDED, VERSION_REJECTED, SHAPE_CHANGED 24 h), working variant, learned version, shape maps,
  version-source clock (≤ 1 a day), 30 min bot-check window for the request-sending rungs only; JSON
  with strict patterns (no address, visitor data or video ID). `ClientHealthTest` (10).
- ✅ `ladder/ClientLadder.kt` (D6 core, Plan adapted): `MasterClientLadder` / `LadderLookup` on the R6
  chain's HTTP path through `LookupMemo` — another video's answer dropped (F4); visionOS's answer
  judged: L (V + init check on one ≤ 4 KiB read per row), Z, V when no pass in the last hour (a 403
  list refused by address, merge rule); after the chain and the own solver's V: the H leg when not
  complete. `MasterYouTubeModule(http, playerScripts, ladder = null)`; null = R6.
- ✅ App: `-Pyft.clientLadder` (debug, with masterCapture; BuildConfig `CLIENT_LADDER_ENABLED`, release
  and preview false), `MasterLadderSwitch` + `OkHttpMediaStartReader`, `BrowserMasterFallback.create`
  passes the ladder only with the flag; workflow input `client_ladder` (default false).
- ✅ Simulator (`ClientLadderSimTest`, plan §5 D6 core table): wrong 72 → 0 in both columns; MCF 305 →
  95 (own solver off: the S classes left for D5a) and 160 → 5 (own solver on: V6 = O, live only);
  never worse than R6; budget classes within R6's player requests.
- Module tests: 598, 0 failed, 2 skipped; R6 baseline unchanged; drift 0; workflow trigger tests OK.
  App code not compiled here (no Android SDK) — CI checks it. Next: D5a (A, S).

## Phase 1.3 — Final plan items 1–2 (2026-10-15, the owner's OK): client table v3 (H live) + T1c / T1d R6 columns

- ✅ `ClientTable.kt`: `MasterClientTable` v3 — visionOS's H variant provisional → **live** (tests 1–3
  on `595d259`: 8, 8 and 6 of 9 passed on 9 distinct videos, wrong 0); nothing else changed (Android
  and MWEB provisional, embedded measured, not active, A and S provisional). The census report's
  table line now reads `client table v3 (2026-10-15): visionos R0 live, A provisional, S
  provisional, H live · …`. `ClientTableTest` updated.
- ✅ `SimCases.kt`: T1c (the bot check as T1b; R6's `R0+vd` answer with its formats under a moved key,
  as L1) and T1d (as T1b; R6's `R0+vd` answer adaptive SABR-only, HLS kept, as V4), 3 lookups each
  on the 5 bases. R6 columns pinned in `r6-baseline.txt` (+20 lines, the other lines unchanged):
  without the own solver 0 / 15 complete (15 partial, MCF 15 — the ladder's (က+) target: L on T1c,
  H on T1d), with it 15 / 15 (MWEB through the own solver); wrong 0.
- Module tests: 587, 0 failed, 2 skipped (`:extractor-master:test`, masterCapture + ownSolver);
  drift 0. Next: D3 (health v2, order, budget, stop rules).

## Phase 1.3 — weekly check 3 (owner, 2026-10-15 14:40 UTC; docs only) and the Final plan; ⏸ the owner: one OK for the Final plan

- ✅ W 3 (`595d259`, mobile data, 80 min after test 3): 15 requests, 624 KB, 10.9 s (limits 20,
  2 MB, 60 s). `CHANGED (1)`: H video 1 master `net` → 2xx (test 3's network failure gone; §7
  step 7: nothing to fix) + 2 `INFO` bot checks (videos 1 and 5 answered as R6, `R0+vd` `DIRECT`,
  V pass). H on video 1's `R0+vd` answer 2xx / 2xx / 2xx (≤ 240p, 3 segments); video 2 (B6):
  Android `SABR_ONLY` 360p, V pass; MWEB `NEEDS_SCRIPT`, 30 ciphered formats ≤ 2160p, V
  `skip-needs-S` (W runs no S); no version source read (< 24 h), so no newer version tried (B7).
  Wrong 0; as designed.
- Final plan (plan: new "Final plan" section, title "Plan v3 (final, 2026-10-15)"): D0's phone part
  complete; table v3 = H live (gate met: 89 %, 89 %, 67 %, 9 distinct videos, wrong 0), Android
  and MWEB provisional (videos 2 and 6 only), embedded measured, not active, A and S provisional.
  Order: D2 follow-up (table v3, code) → T1c / T1d → D3 → D4 → D5a → D5b → D5c → D6 → D7
  (continuous). Also §9 items 9 and 11, the Burmese summary, the D0 and D3 rows.
- ⏸ Next (owner): one OK for the Final plan (table v3 with H live + the (က+) plan change); then the
  run goes on, stopping only on the stop rules. No code change in this commit.

## Phase 1.3 — census test run 3 (owner, 2026-10-15 13:20 UTC; docs only); H meets the gate; ⏸ the owner: a weekly check and the word on H live

- ✅ Test run 3 (`595d259`, mobile data, 1 h 21 min after test 2): FULL, 10 videos, 74 requests,
  2773 KB, 97 s, not stopped (page 1 took 6490 ms: a slow network). `BROKEN (10)`: H on videos 1
  and 2, the master request failed on the network (`net`, 1 request each; not a YouTube refusal,
  no wrong row); visionOS R0 video 2 V `UNKNOWN` (`NETWORK`); MWEB video 2 back to ≤ 2160p (the
  known flip, back: +`signatureCipher`, MWEB R0 also −`adPlacements`); the App Store reachable
  again (21.41.5). 8 `INFO` bot checks as tests 1–2.
- Per rung: R0 0 / 10 (video 2 V `UNKNOWN`); R0+vd 8 / 8; S 0 asked; **H 6 / 9** (67 %; videos
  1–2 `net`, video 10 `no-segment` again); Android 2 / 2 (360p); embedded 0 / 2 `REFUSED`; MWEB
  2 / 2 (video 2 ≤ 2160p, video 6 ≤ 1080p). Players 21090930 on 8, 0229cd64 on videos 4 and 10;
  MWEB page version accepted again; wrong 0.
- Gate (§9 item 9): H ≥ 60 % in 3 of 3 tests (89 %, 89 %, 67 %), 9 distinct videos, wrong 0 →
  **H meets the gate (live)**; the client table's H (provisional → live) changes in code only on
  the owner's word. Android and MWEB stay provisional (videos 2 and 6 only). No code change.
- ⏸ Next (owner): a weekly check on this APK and the word on H live. No T1c / T1d, no D3.

## Phase 1.3 — census test run 2 (owner, 2026-10-15 11:59 UTC; docs only); H meets the gate; ⏸ the owner: test run 3 and a weekly check

- ✅ Test run 2 (`595d259`, mobile data, 2 h 44 min after test 1): FULL, 10 videos, 78 requests,
  3382 KB, 62 s, not stopped. `CHANGED (7)`: MWEB video 2's address-less adaptive answer again (R0
  and SH, 30 → 29 without address, 2160p → 360p, −`signatureCipher`; the known flip of valid runs
  2–3, not a layout change); −`streamingData.probeUrl` in visionOS `R0+vd` answers (YouTube side;
  no Master code reads it; no size missing). 8 `INFO` bot checks as test 1.
- Per rung: R0 1 / 10; R0+vd 8 / 8; S 0 asked; **H 8 / 9** (video 10 `no-segment` again —
  repeatable for that video); Android 2 / 2 (360p); embedded 0 / 2 `REFUSED`; MWEB 2 / 2 (video 2
  360p only, video 6 ≤ 1080p). MWEB page version accepted again; App Store `NETWORK` again.
- Gate (§9 item 9): H 89 % in 2 of 2 tests on 9 distinct videos, wrong 0 → the rule is met; live
  once test 3 keeps wrong 0 (code change only on the owner's word). Android, MWEB: provisional.
- Candidates for later (no code change now): MWEB's address-less flip as an `INFO` line, not
  `CHANGED`; the note "nothing else for this video" (the H check still runs).
- ⏸ The owner: test run 3 (≥ 1 h after test 2) and a weekly check. Not started: T1c, T1d, D3.

## Phase 1.3 — census test run 1 on the B + D2 APK (owner, 2026-10-15 09:15 UTC; docs only); ⏸ the owner: test runs 2–3 and a weekly check

- ✅ Test run 1 (`595d259`, the first run after the one uninstall; mobile data, metered): FULL,
  10 videos (1–10), 78 requests, 3388 KB, 69 s, not stopped; `FIRST RUN` (expected) + 8 `INFO`
  lines (videos 1, 3, 4, 5, 7, 8, 9, 10: visionOS R0 `BOT_CHECK` → `R0+vd` `DIRECT`, V pass).
- As expected: B8 header "10 videos (1–10)"; B5 H on every video whose visionOS answer is
  `DIRECT`; V7 video 6's visionOS and the embedded 152-18 → `REFUSED` (not `VIDEO_VERDICT`); B7
  on video 2; B9 3388 KB ≤ 6 MB; the client table line.
- Per rung (passed / asked): R0 1 / 10; R0+vd 8 / 8; S (visionOS) 0 asked; **H 8 / 9** (video 10:
  master and variant 2xx, the master ≤ 240p while the formats reach 720p, no segment in the
  variant playlist — YouTube's HLS for it looked incomplete; R6 answered video 10 itself, no wrong
  row); Android 2 / 2 (360p only); embedded 0 / 2 `REFUSED`; MWEB 2 / 2 (S + V: ≤ 2160p, ≤ 1080p).
- New: MWEB's page version 2.20261014.01.00 tried for the first time (B7) → **accepted**; the App
  Store `NETWORK` this time; players 21090930 (8 pages) and 0229cd64 (2), S solved both.
- Gate (§9 item 9) after tests 2–3: H on 9 distinct videos (8 passed, 89 %) → live if ≥ 60 % in
  ≥ 2 of the 3 tests and wrong 0; Android and MWEB still on videos 2 and 6 only (provisional).
- Plan: §5 test-run table (test 1); §7: only the first run after an uninstall reads `FIRST RUN`
  (the "and so does W" of `595d259` was wrong).
- ⏸ The owner: test runs 2 and 3 (≥ 1 h apart) and a weekly check. Not started: T1c, T1d, D3.

## Phase 1.3 — one fixed debug key for the opt-in APK (2026-10-15; build and workflow only, no app code); ⏸ the owner: one uninstall, then one Full census

- The owner (2026-10-15, after `2b62605`): 1. B + D2 OK; the `INCOMPLETE` finding (main's
  `player_ok.json` and `player_4k.json`) OK. 2. T3 removed OK: main's unmarked "This video is
  unavailable" stays `REFUSED` (V7) and O goes on; the definite markers (removed, deleted,
  terminated, …) stay terminal and are never weakened — plan §5.2 now records it as the owner's
  decision (it said "the owner confirms"). 3. ⏸ until the census result: no T1c / T1d, no D3.
- Problem: the `2b62605` APK did not install over the installed debug app: "App not installed as
  package conflicts with an existing package".
- Cause: `master-optin-debug-apk.yml` signed with the runner's own debug key, made fresh on each
  runner, so each run's APK had another signer, and Android refuses an update signed by another
  key.
- Fix (main, release and preview signing, the other workflows and the 5 copied files untouched):
  - `app/keystore/spike-debug.keystore.b64` (new): the base64 text of a new debug keystore
    (PKCS12, alias `androiddebugkey`, password `android`, RSA 2048, SHA256withRSA,
    `CN=Android Debug, O=Android, C=US`, 10000 days: 2026-10-15 → 2054-03-02). The decoded
    `app/keystore/spike-debug.keystore` is never tracked (`.gitignore`: an explicit line next to
    `*.keystore`).
  - `app/build.gradle.kts`: when `keystore/spike-debug.keystore` exists, the `debug` signing
    config uses it (the debug app `com.alal.yft.debug` and its test APK); else the default debug
    key signs as before.
  - `master-optin-debug-apk.yml`: a step before the Gradle build decodes the `.b64` (without it:
    "the runner's own debug key signs"); "Record APK hashes" adds each APK's signing certificate
    SHA-256 (apksigner 35.0.0, both label forms as in `scripts/verify-release-apk.sh`) to the run
    summary and as a `::notice` "APK signing cert SHA-256", and fails (no upload) when an APK's
    certificate is not the decoded key's (keytool). The "throwaway debug key" comments are
    updated.
- **Certificate SHA-256** (the debug APK and its test APK):
  `a2696dff62331776dee9baf34a150ffcec19e1a358656bc65517d165cc88bbbe`.
- Checks in the agent's sandbox (Android SDK 35, build-tools 35.0.0, NDK 27.3.13750724 and
  CMake 3.22.1 now installed): the decode step gives the keystore byte for byte;
  `:app:compileDebugKotlin :app:assembleDebug :app:assembleDebugAndroidTest`
  (`-Pyft.masterCapture=true -Pyft.ownSolver=true`, as CI) ✅ → `com.alal.yft.debug`, both APKs
  signed with the certificate above (v2, one signer); without the decoded file
  `:app:assembleDebug` ✅ signs with the default debug key (another certificate), and the record
  step's guard, with the key back, fails on that APK after writing the summary; module 587 run /
  2 skipped / 0 failed (rerun); drift 0 (107 symbols).
- The key is public: install only APKs from this repo's `master-optin-debug-apk` workflow. It
  signs the debug app only, never a release or preview build (`scripts/verify-release-apk.sh`
  rejects a `CN=Android Debug` certificate). The older notes to uninstall first ("CI debug keys
  can differ": `MASTER_KEY_PLAN.md`, earlier sections here) hold only for APKs before this commit.
- Plan: §7 Runbook (the fixed key: one uninstall, then only updates; what the uninstall loses),
  §5.2 (T3 removed: the owner's decision), status board D0 (§9 item 5: decided), the Burmese
  summary. CI and the APK: this commit's checks (one push, no docs follow-up).
- ⏸ The owner, once: uninstall `com.alal.yft.debug`, install this commit's APK, re-enter slots
  5, 6 and 8–10 with the same videos (4 and 7 go back to their defaults), run one **Full census**
  on mobile data — it reads `FIRST RUN`, since the uninstall deleted the stored runs — and send
  the result. From then on: update only, never uninstall. Not started: T1c, T1d, D3.

## Phase 1.3 — B (census items 5–9) + D2 done (2026-10-15; code `eac2df3`, tests and plan `2b62605`, CI ✅; module 587 run / 0 failed, drift 0); ⏸ the owner: one Full census on the new APK

- The owner (2026-10-15, 8 points, "decide yourself"); the agent's decisions:
  1. O rungs back to live: **declined** — the gate stays ≥ 4 distinct videos (the owner's own
     rule; 2 distinct videos are thin; the provisional rungs stay wired as fallbacks, V on every
     row). Known limit for plan §5: "O rungs sampled on videos 2 and 6 only (bot-check rule)".
  2. (က+): already in `2c57124`. 3. B (items 5–9) and C (item 10 = D2) now, **one APK**.
  4. The parallel agent's files (ShapeLocator, ShapeNormalizer, HlsLeg, HlsCheck, InitCheck,
     ZRung, LookupMemo) are reused, not rewritten. 5. Census results go to plan §5.
  6. After the B + D2 APK → ⏸: no T1c/T1d simulator work, no D3.
  7. Stop on: wrong > 0, MCF > 0 in a modelled class, drift > 0, a flag-off parity failure,
     CI ❌ twice. 8. §9 item 5 does not apply (provisional rungs wired as fallbacks, V on every
     row) — decided 2026-10-15 (the owner delegated).
- Code (`eac2df3`, CI validate ✅ + build ✅):
  - **D2** `ladder/AnswerJudge.kt` (new): one judge for the census answers. Order on a failure:
    bot-check words (any status) → `BOT_CHECK`; "update to the latest version" →
    `VERSION_REJECTED`; age check → `AGE_CHECK`; an app refusal → `REFUSED`; a definite marker
    (private, removed, terminated, members-only, copyright, sign-in) → terminal `VIDEO_VERDICT`;
    **V7:** `UNPLAYABLE` / `ERROR` without such a marker ("This video is not available",
    "Error code: 152 - 18") → `REFUSED`, not terminal; DRM with formats → `VIDEO_VERDICT`; a moved
    or partial shape → `SHAPE_CHANGED` (kind `MOVED` / `PARTIAL`); SABR only → `SABR_ONLY`; else
    `REFUSED`. HTTP failures: 400 → `VERSION_REJECTED`, else `REFUSED`. The HLS address is taken
    only on a googlevideo host. `afterV` gives the final class.
  - **D2** `ladder/ClientTable.kt` (new): `MasterClientTable` version 2 (2026-10-15): visionOS R0
    live; Android O provisional; embedded O "measured, not active"; MWEB O provisional (the
    page's newer version listed, never tried); `oRungs()` = Android, MWEB; a one-line
    `describe()` in the census report.
  - **B** `ClientCensus.kt`, `CensusModel.kt`: 5. H after R6's `R0+vd` `DIRECT` answer (≤ 1 a
    video, ≤ 1 in a W run); 6. W, when video 1 met a bot check: on video 2 visionOS R0 + V (+ H
    only when no H ran yet), then the page, Android + V and MWEB, under a soft budget (what does
    not fit the 20 requests is not asked, a note says which, the run goes on); 7. the version
    acceptance on the first video without a bot check and with a watch page (a note says which;
    none → a note, no newer version tried); 8. the header lists the videos asked
    ("3 videos (1, 2, 5)", ranges "1–3"); 9. budgets: Full ≤ 6 MB; W ≤ 20 requests, ≤ 2 MB,
    ≤ 60 s. The census classes come from AnswerJudge (the census's own classify and regexes
    removed); the shape kind is stored (JSON key `kind`).
- Tests and plan (`2b62605`, CI validate ✅ 116301011797 + build ✅ 116300996665; no main code changed
  since `eac2df3`):
  - `AnswerJudgeTest` (11, new): main's fixtures, never `DIRECT` for a protected, SABR-only or
    cipher answer, the simulator's L1–L16 over the 5 bases (kinds `MOVED` / `PARTIAL`; L11, L12
    verdicts; L13 with its located HLS address; L14 `NEEDS_SCRIPT`), V7's texts → `REFUSED` for
    visionOS, embedded and MWEB, the definite markers → `VIDEO_VERDICT`, version and bot words
    under `UNPLAYABLE`, `ERROR` and `LOGIN_REQUIRED`, HTTP failures, `afterV`, the HLS host rule,
    `OK` without streaming data, the bounded reason text. One finding: main's `player_ok.json` and
    `player_4k.json` are `INCOMPLETE`, not `DIRECT` as the handoff expected — itag 22 (and 251 in
    ok) carry no size, and v1's rule makes an answer with a format without a size `INCOMPLETE`;
    the judge is right, the test pins `INCOMPLETE` and `DIRECT` once every size is added.
  - `ClientTableTest` (6, new): 4 identities in order with main's profiles, the patterns match
    the bundled versions, ≤ 2 alternates, ISO dates, live version sources only on the approved
    hosts, no O rung live, `oRungs()` = Android, MWEB, `describe()` one line without an address.
  - `AnswerJudgeSimTest` (2, new): **V7 live-backed** — 10 worlds (5 bases × the simulator's "not
    available on this app" and video 6's "This video is not available"): visionOS `REFUSED`, not
    terminal, 10 / 10; Android the 360p progressive row only (V pass, not complete); MWEB S + V on
    every row, complete 10 / 10; wrong 0, **MCF 0**, credentials 0. Controls: T2, T3 private, geo,
    DRM terminal at visionOS (no O request); T3 removed (main's unavailable answer, `REFUSED` by
    V7) → Android and MWEB refuse too, clean stop, wrong 0, MCF 0 (2 player requests more than
    R6: plan §5.2 — the owner's decision, 2026-10-15, in the section above).
  - `ClientCensusTest` (+4): B8 ranges ("4 videos (1–3, 5)", "0 videos"), V7 in the census
    (video 6's visionOS `REFUSED` with no row, the embedded 152-18 `REFUSED`, Android and MWEB
    asked, the run goes on), the table line after the summary, the shape kind round trip.
  - Module 587 run / 2 skipped / 0 failed (564 + 23); drift 0 (107 symbols); the R6 baseline
    unchanged. `:app:compileDebugKotlin`: the agent's sandbox has no Android SDK, so CI's build
    compiles the app (only tests and docs changed since `eac2df3`, whose build passed).
  - Plan: status board (D0: B5–B9; D2 done), §4 D0 "B, as built", §4 D2 "As built", §5 (known
    limit; V7 live-backed probe), §5.2 (T3 removed under V7), §9 items 5 (does not apply,
    decided) and 9 (O back to live declined), the Burmese summary.
- APK (`2b62605`, "Master opt-in debug APK (spike)" ✅):
  Run: https://github.com/Alalkipgen/YFT/actions/runs/38698595313
  Artifact `yft-master-optin-debug-apk`:
  https://github.com/Alalkipgen/YFT/actions/runs/38698595313/artifacts/11955890077
- ⏸ The owner: install the new APK **over** the installed app (no uninstall), re-enter slots 6
  and 8–10 with the same videos, run one **Full census** on mobile data and send the result.
  Expected: the header lists the videos asked; H on every video whose `R0+vd` answer is `DIRECT`
  (B5); video 6's visionOS and the embedded player's answers `REFUSED` (V7), not
  `VIDEO_VERDICT`. Not started until the owner says so: T1c, T1d, D3.

## Phase 1.3 — weekly check 2 + (က+) after a bot check + gate by distinct videos (owner, 2026-10-15; docs only, `2c57124`); ⏸ the owner's OK on this plan change

- ✅ **W 2: store reset by owner uninstall, no diff; H 4/4 (all video 2).** Weekly check 2 (owner,
  2026-10-15 04:34 UTC, 55 min after W 1; `3e8f378` APK, mobile data, metered): 17 requests,
  448 KB, 10.5 s (limits 20, 2 MB, 60 s) ✅. `FIRST RUN — nothing to compare yet`: the owner
  uninstalled the old app before installing `3e8f378`, which deleted the stored runs — not a bug,
  no code change. Slots 6 and 8–10 came back empty (slot 5 was re-entered: the same video, its
  slot fingerprint unchanged); the next Full census needs them re-entered with the same videos.
  - Item 4 ✅: videos 1 and 5 visionOS R0 `BOT_CHECK` → answered as R6 (`R0+vd` `DIRECT`, V pass;
    video 1 ≤ 240p, video 5 ≤ 1080p) → two `INFO` lines, not counted.
  - Item 5 ✅: video 2 visionOS R0 `DIRECT` ≤ 2160p (27 adaptive), V pass; H on video 2: 2xx / 2xx /
    2xx, 17 variants ≤ 2160p, audio separate, codecs avc1 / mp4a / vp09, key `NONE`, 38 segments,
    duration matches, 3 requests (`H: video 1 met a bot check, so the H check ran on video 2`).
  - Version sources as valid runs 1–3: visionOS yt-dlp and YouTube.js 1.02 (= bundled); Android
    yt-dlp 21.26.364 (= bundled), YouTube.js 21.03.36 (older); App Store 21.41.5 without a visionOS
    device; Play Store page over 64 KiB; the web page config 2.20261014.01.00 newer, not tried
    (video 1 met a bot check). No Android, embedded or MWEB (video 1 met a bot check).
  - The header said `6 videos` (the slots set: 1–5, 7) while 3 were asked (queued: B8).
  - Plan §5 Weekly checks table: W 2 row; H row 4 / 4.
- ✅ Runbook (plan §7): update the APK over the installed one, never uninstall it first —
  uninstalling deletes W's stored runs, the health file, the cooldown and the census video picks
  (slots 4–10; 4 and 7 go back to their defaults).
- ✅ **Item 6 decided: (က+)** (owner). (ခ) rejected (owner: it avoids the bot check instead of
  answering it, close to a bypass; one visitor ID would link the lookups); (ဂ) rejected. After a
  bot check, on R6's `R0+vd` answer and with no new InnerTube request (player or page):
  a. L (local) and Z, V and the init check (reads on the media host YouTube returned; guardrail 6's
     budget) — Z is the one-byte probe, so it sits with the media-host reads;
  b. the H check (playlists on `manifest.googlevideo.com`, the segment on the media host; ≤ 1 per
     lookup);
  c. S locally only: the own solver + SelfCheck + V, only when R6 already fetched the player
     script; no player request and no script fetch of the ladder's own;
  d. the 30 min cooldown blocks only the request-sending rungs (A, O, S's re-ask, a version
     source), never L, Z, V, the init check, H or local S (and the bot-check cooldown has no
     "asked when every other client failed" exception).
  The census likewise: after a bot check answered with `R0+vd` `DIRECT`, V and H run on it (B5).
  Plan: guardrail 4; §3 flow (`BOT_CHECK` line; the chain's `R0+vd` answer from the memo); §4 D0
  (census text), D1 (T1, T1b remedies; **T1c** bot check → `R0+vd` + `SHAPE_CHANGED` → L complete;
  **T1d** bot check → `R0+vd` SABR-only → H complete; a follow-up before D3 adds them to the
  simulator), D3 (cooldown, stop rules, done), D6 (memo keeps R6's `R0+vd` answer and player
  script for this lookup); §5.1 (a bot check R6's chain does not answer), §5.2 (T1–T3, T1b–T1d), §5
  baseline (T1c, T1d rows: "— (D1 follow-up)"); §6 (exposure); §9 items 7 and 10; the Burmese
  summary; prompt rule 4 (and the D1, D3 lines).
- ✅ **Gate (plan §9 item 9): the H / O minimum is ≥ 4 distinct videos** (by list number, over the
  valid Full runs and the weekly checks). Consequence (flagged to the owner):

  | Rung | asked (valid 1–3, W 1–2) | passed | distinct videos | now (before) |
  |---|---|---|---|---|
  | H | 4 | 4 | 1 (video 2) | provisional (provisional) |
  | Android | 6 | 6 | 2 (videos 2, 6) | provisional (live) |
  | MWEB | 6 | 6 | 2 (videos 2, 6) | provisional (live) |
  | embedded | 6 | 0 | 2 (videos 2, 6) | not live (not live) |
  | S | 0 | — | 0 | provisional (provisional) |

  No rung is live. Provisional rungs stay wired as fallbacks after visionOS fails, V on every row
  (§4 D0 gate), so the agent reads §9 item 5 ("neither H nor any O rung live" → A, S, Z, L only)
  as not applying — **the owner confirms**. Leaving provisional: H through B5 (the next Full run
  asks H on every video whose `R0+vd` is `DIRECT`: up to 9 distinct videos); Android and MWEB only
  on videos without a bot check (no O rung after one, (က+)) — so far only videos 2 and 6; B6 adds
  samples on video 2 in W, not a new distinct video. Plan: §4 D0 gate (+ the D0 "Done" note), §5
  (H, Android, MWEB cells; gate paragraph: "live" for H or an O rung includes a provisional one,
  wired as a fallback), status board (D0, D1, D3 rows), §9 items 5 and 9.
- Queued — **not started before the owner's OK on this plan change:**
  - **B** (census code, one APK, tests first): 5. the H check on every video whose `R0+vd` is
    `DIRECT` (≤ 3 requests a video); 6. W: when video 1 met a bot check, Android and MWEB on video 2
    too (as H); 7. MWEB asked once more with the page's newer version (2.20261014.01.00) on a video
    without a bot check (acceptance), and whether the adaptive formats without an address come from
    the old version; 8. the header counts and lists only the videos actually asked (W 2 = 3);
    9. budgets: Full ≤ 6 MB; W ≤ 20 requests, ≤ 2 MB, ≤ 60 s. Then push, CI ✅, the APK sha; the
    owner runs one Full census (update over the installed app, no uninstall; slots 6, 8–10 first).
  - **C** ("D2 စ", in parallel with B): 10. V7 — a single client's refusal (an error screen without
    age, login, private, removed, geo or DRM markers; video 6, made for kids: visionOS
    `UNPLAYABLE` "This video is not available" while MWEB S + V passed 3 / 3) → `REFUSED`, not a
    terminal `VIDEO_VERDICT`; O rungs may go on; when they do not complete, R6's verdict stays; the
    census class too (in B's APK). Tests: `player_embed_refused.json` + a video-6-like answer built
    in code → `REFUSED`, MWEB complete; age, private, geo and DRM stay terminal; simulator V7
    live-backed MCF 0. 11. Each task: push, CI ✅, a short note.
  - D1 follow-up (T1c, T1d in the simulator, the R6 columns) before D3; **D3 only after the OK.**
- ⏸ The owner: OK on this plan change (or changes to it); confirm §9 item 5 does not apply
  (provisional rungs wired as fallbacks).

## Phase 1.3 — D0 done: weekly check 1 + census fixes (owner items 4–5, 2026-10-15; `3e8f378`, CI ✅); ⏸ item 6 (before D3), D2 on "D2 စ"

- ✅ Weekly check 1 (owner, 2026-10-15 03:39 UTC, 94 min after valid run 3; `81388f8` APK, mobile
  data, metered): WEEKLY, 10 videos, 8 requests, 372 KB, 4 s (limits 20 requests, 2 MB, 60 s).
  Videos 1 and 5: visionOS R0 `BOT_CHECK` → answered as R6 (watch page, `R0+vd` `DIRECT`, V pass;
  video 1 ≤ 240p, video 5 ≤ 1080p). Video 1's bot check → no H, no Android, embedded or MWEB;
  version sources read < 24 h ago, not read. Diff `CHANGED (2)`: only the two answered bot checks
  (item 4's bug). Plan §5 (Weekly checks table, H row), status board.
- ✅ **D0 done** (plan §4 D0 "Done"): 3 valid Full runs, the gate, the version-source table, weekly
  check 1; the owner was told which rungs and sources are live.
- ✅ Item 4 — `CensusDiff` (tests first: the new `CensusDiffTest` cases failed before the fix):
  - a bot check answered as R6 whose `R0+vd` answer is `DIRECT` with V passed is an `INFO` line
    (`INFO visionos R0 video N: bot check, answered as R6 (visionOS with visitor data: DIRECT, V
    PASS)`): no runbook step and not counted, so W 1 would now read `NO CHANGE`; anything else (no
    `R0+vd`, another class, V not passed) stays `CHANGED … → §7 step 8`, now with V in the text;
  - shape: paths are gathered per client and step over its videos; a path one video gained and
    another lost cancels (no change, no `BREAKING` from it); the per-video count lines still show
    such a video.
- ✅ Item 5 — W's H on a video without a bot check: when video 1 met one, visionOS is asked on
  video 2 (no bot check in valid runs 1–3) as R6 asks it (V on one row) and H runs on its HLS
  address; video 2's own bot check is answered as R6, then no H. A note says which (`H: video 1
  met a bot check, so the H check ran on video 2`). At most 20 requests (videos 1 and 5 as R6:
  4–5 each; video 2: 5–6; sources 4); W 1's case becomes 13 requests (17 when the sources are
  read). Video 1 without a bot check: W as before (H on video 1; video 2 not asked).
- ✅ Census screen: the intro names video 2's H check and says `INFO` lines need nothing.
- ✅ Tests: `CensusDiffTest` 14 (+2: `INFO` or `CHANGED`; +/− cancel), `ClientCensusTest` 29 (+3:
  video 2's H after video 1's bot check, 17 requests; H stays on video 1 without one; video 2's
  bot check → no H), `CensusR6ParityTest` 5; expectations updated (`INFO`, headlines). Module 563
  run / 0 failed (2 skipped); drift 0; `:app:compileDebugKotlin` OK.
- Plan: Burmese summary (D8 dropped), status board (D0 done), §4 D0 W (H on video 2; first line:
  `INFO`, +/− cancel), §5 (H row, Weekly checks table, D0 done), §7 steps 1 and 3 (D8 dropped),
  §9 item 9 note, §9 item 10 (asked).
- ⏸ Item 6 (before D3; no code): the ladder after visionOS's bot check. visionOS R0 met one on 24
  of 30 census videos; under guardrail 4 the ladder's rungs (A, S, Z, L, H) then stop and R6's
  chain answers (`R0+vd` 24 / 24 `DIRECT`, V pass), so in about 80 % of lookups the ladder adds
  nothing. The owner decides (plan §9 item 10); agent's recommendation: (က).

  | Option | Effect | Risk | Requests (a bot-checked lookup) | MB | Guardrails |
  |---|---|---|---|---|---|
  | (က) L, Z, V on R6's `R0+vd` answer | L and Z also in the ~80 % of lookups R6's chain answers; A, S, H and O stay off there | low: R6's requests unchanged; L rows still need V + init check; reads only on hosts YouTube returned | +0 player requests; only when the answer is incomplete: Z ≤ 8 one-byte reads, L ≤ 8 reads of 4 KiB | ~0 (≤ 0.05 MB when L runs; cap 0.5 MB) | guardrail 4 wording (owner): after a bot check the ladder's request-making rungs stop, its on-answer steps (L, Z, V) may run; 6, 7, 9 unchanged |
  | (ခ) visitor data in session memory, sent from the first request | the first request may meet no bot check, so every rung can run | medium–high: avoids the bot check instead of answering it (close to guardrail 4's bypass); one visitor ID links a session's lookups; a refused remembered ID is not measured | −2 per lookup after the session's first when accepted (no refused R0, no page); 3–4 when refused (R6's chain as today) | −0.15 MB on the wire per lookup when accepted (the page, ~0.7 MB unpacked) | changes R6's first request (guardrail 4); needs a memory-only rule (guardrail 8); the census must measure it first |
  | (ဂ) no change | none | known limit: in ~80 % of lookups a layout change or missing sizes on R6's answer is not rescued (as R6 today); W shows it | 0 | 0 | fits all |
- ⏸ Item 7: D2 starts on the owner's "D2 စ" (it does not depend on item 6).
- Phone (owner, when convenient): install this commit's APK; the next Weekly check measures H on
  video 2 (H leaves provisional at ≥ 4 asked videos, ≥ 60 % passed, wrong 0).

## Phase 1.3 — owner decisions after the D0 gate (2026-10-15; docs only, `015eaab`)

- ✅ SOURCE_HOSTS_APPROVED = yes, with conditions (plan §9 item 3; prompt): `raw.githubusercontent.com`
  only; no redirect followed; ≤ 64 KiB; read as text only; in a lookup fetched only after
  `VERSION_REJECTED`, at most once a day per identity (guardrails 5 and 7). The reference tables
  (visionOS: yt-dlp, YouTube.js; Android: yt-dlp) are the usable sources (D0).
- ✅ D8 dropped (plan §4 D8 "Dropped (owner, 2026-10-15)", status board, §9 item 4; prompt
  `D8_APPROVED` note). Known limit: when both reference tables lag behind, or the layout changes,
  a new APK is needed; W shows it. Decided again only if that happens.
- Queued until the owner sends the Weekly result (no code change and no APK before it):
  4. `CensusDiff`: a path that is both added and removed is not a change; a bot check answered as
     R6 with `R0+vd` DIRECT is an info line, not "§7 step 8"; tests first.
  5. Weekly check: H on a video without a bot check instead of video 1 (valid runs 1–3: video 2
     never met one), so H can leave "provisional"; within ≤ 20 requests and ≤ 2 MB.
  6. ⏸ Before D3 (no code): an options table for the owner — visionOS R0 met a bot check on 24 of
     30, so under guardrail 4 the ladder's rungs (A, S, Z, L, H) do not run in about 80 % of
     lookups: (က) L / Z / V on R6's `R0+vd` answer with no extra request; (ခ) visitor data reused
     from the app's session memory only (never on disk), sent from the first request; (ဂ) no
     change, a known limit — each with risk, requests, MB and guardrail fit.
  7. D2 only on the owner's "D2 စ" (D2 does not depend on item 6).
- Phone (owner): one Weekly check after 03:05 UTC with the `81388f8` APK, normal mobile data, no
  clearing app data → Copy result. Done: weekly check 1, 03:39 UTC (next section).

## Phase 1.3 — D0 census valid run 3 + gate result (owner, 2026-10-15 02:05 UTC; docs only, `aba372a`)

- ✅ Valid run 3 (2026-10-15 02:05 UTC, 88 min after run 2; `81388f8` APK, mobile data, metered):
  FULL, 10 videos, 54 requests, 2643 KB, 30 s, not stopped. Report `CHANGED (13) — 0 days since
  the last run`: the 8 answered bot checks (videos 1, 3, 4, 5, 7, 8, 9, 10; each `R0+vd` DIRECT);
  MWEB video 2 formats 0/0/30/0/1 → 0/0/1/29/1, highest 2160p → 360p (§7 step 2); MWEB video 6
  back 0/0/1/25/1 → 0/0/26/0/1, 360p → 1080p (§7 steps 4, 2); shape mweb +1 −1
  (`adaptiveFormats[].signatureCipher`, §7 step 6).
- Per rung (passed / asked): R0 1 / 10 (video 2 DIRECT; bot check 8; video 6 VIDEO_VERDICT); R0+vd
  8 / 8; A none; S 0 asked; H 1 / 1 (as before); Android 2 / 2 `SABR_ONLY` (360p progressive only);
  embedded 0 / 2 (152-18); MWEB 2 / 2 (video 2 adaptive formats without address, only the 360p
  progressive row; video 6 ≤ 1080p; S ok-sig+n and V pass on both). Pages 10 / 10 (player 21090930
  on 9, 0229cd64 on 1). Version sources = runs 1–2.
- ✅ D0 gate (§9 item 9; valid runs 1–3, asked / passed; wrong 0: every answer bound to its video,
  no V fail):
  - live: Android 6 / 6 (3 of 3 runs; the 360p progressive row only, `SABR_ONLY`) and MWEB 6 / 6
    (3 of 3 runs; S path; on 2 of 6 answers the adaptive formats came without address: 360p only);
  - provisional: H 3 / 3 (asked on 3 videos, < 4) and S (0 asked: no cipher in any visionOS
    answer) — only a fallback after visionOS fails, V (H) on every row, W keeps measuring;
  - not live: embedded 0 / 6 (error 152-18) → measured, not active (2026-10-15); A: none bundled;
  - R0 (R6's own first request): 3 / 30 passed, bot check on 24, `VIDEO_VERDICT` 3 (made-for-kids);
    R0+vd 24 / 24 DIRECT, V pass. §9 decision 5 does not apply (O rungs live).
- ✅ Version sources (usable = 3 of 3 runs): visionOS reference tables (yt-dlp 1.02, YouTube.js 1.02
  = bundled) and Android yt-dlp (21.26.364 = bundled), all on GitHub raw — a host Master's lookups
  do not use yet, so census only until SOURCE_HOSTS_APPROVED. Not usable: the visionOS answer (no
  value), App Store (no visionOS value), Play Store (over 64 KiB), YouTube.js Android (older),
  embedded and MWEB page config (the newer 2.20261014.01.00 was never tried: video 1 met a bot
  check in every run; MWEB keeps its bundled version, which works).
- Finding for D2/D3: MWEB's address-less adaptive answer moves between videos and runs (run 2 video
  6, run 3 video 2): an answer whose adaptive formats have no address means "adaptive path not
  complete" (not a layout change), each time.
- Owner decisions asked (not waited for, prompt rule): SOURCE_HOSTS_APPROVED (GitHub raw for the
  reference tables: yes / no); D8 (signed identity data): yes / no — a usable source exists for
  visionOS, so D8 may be dropped.
- D0 left: one Weekly check (owner, ≥ 1 h after run 3: from 03:05 UTC). D2 starts on the owner's
  word (standing rule).
- Plan: §5 census table (valid 3, live?), gate paragraph, version-source table (run 3, usable?),
  status board D0, §9 items 3 and 4.

## Phase 1.3 — D0 census valid run 2 + gate count decided (owner, 2026-10-15; docs only, `30e7537`)

- Owner decision, plan §9 item 9: (က) with a minimum sample — count only the videos where a rung
  was actually asked; live = pass on ≥ 60 % of its asked videos in ≥ 2 of 3 valid runs, asked on
  ≥ 4 videos in total, wrong = 0; asked on fewer than 4 → provisional (only a fallback after
  visionOS fails, V on every row, the weekly check keeps measuring it). No code change, the same
  APK (`81388f8`). Written into plan §4 D0 (Gate) and §9 item 9.
- ✅ Valid run 2 (2026-10-15 00:37 UTC, 91 min after run 1; `81388f8` APK, mobile data, metered):
  FULL, 10 videos, 54 requests, 2661 KB, 26 s, not stopped. Report `CHANGED (11) — 0 days since
  the last run`: the 8 answered bot checks (videos 1, 3, 4, 5, 7, 8, 9, 10; each `R0+vd` DIRECT)
  and 3 MWEB lines for video 6: formats (progressive/adaptive/protected/no address/no size)
  0/0/26/0/1 → 0/0/1/25/1 and highest video 1080p → 360p (§7 step 2); shape
  −`streamingData.adaptiveFormats[].signatureCipher` (§7 step 6; not BREAKING: a cipher signal).
- Per rung (passed / asked): R0 1 / 10 (video 2 DIRECT; bot check 8; video 6 VIDEO_VERDICT); R0+vd
  8 / 8 DIRECT, V pass; A none; S 0 asked; H 1 / 1 (as run 1); Android 2 / 2 `SABR_ONLY` (360p
  progressive only, V pass); embedded 0 / 2 (152-18); MWEB 2 / 2 (video 2 S ok-sig+n, V pass,
  ≤ 2160p; video 6 adaptive formats without address or cipher, only the 360p progressive row, S
  and V pass).
- Pages 10 / 10 HTTP 200 (MWEB 2.20261014.01.00), all on player 21090930 (run 1: 3 players).
  Version sources = run 1 (page config 2.20261014.01.00 newer, not tried: video 1 bot check).
- Gate tracking after valid runs 1–2 (asked / passed): Android 4 / 4 and MWEB 4 / 4 (100 % in both
  runs, wrong 0) meet the rule unless run 3 shows a wrong row; Android's rows are the 360p
  progressive row only. embedded 4 / 0 → not live. H 2 / 2, asked once per run → at most 3 →
  provisional. S 0 asked → provisional. A: none bundled. R0 is R6's own first request (2 / 20
  passed, bot check on 16).
- Finding for D2: MWEB's answer for the made-for-kids video turned SABR-style within 91 min
  (adaptive formats without address or cipher); its one ciphered progressive row keeps the class
  `NEEDS_SCRIPT`, so D2's judge must mark the adaptive path not complete there (Android's same
  layout with a direct progressive row is `SABR_ONLY`).
- Note: the weekly check asks H, Android, embedded and MWEB on video 1 only; video 1 met a bot
  check in every run so far, so W adds samples for a provisional rung only when video 1 escapes
  it (D7 revisits).
- Plan: §4 D0 Gate, §9 item 9 (decided), §5 census table (valid 2 + tracking line),
  version-source table (run 2), status board D0.
- ⏸ Phone (owner): valid run 3 ≥ 1 h after run 2 (from 01:37 UTC; ideally another time of day),
  then one Weekly check; the same APK, normal mobile data, no clearing app data → Copy result
  each time.

## Phase 1.3 — D0 census valid run 1 (owner, 2026-10-14 23:06 UTC; docs only, `1e457b3`)

- ✅ Valid run 1 (`81388f8` APK, mobile data, metered): FULL, 10 videos, 54 requests, 2659 KB
  (budget 6 MB), 29 s, not stopped. Report `CHANGED (8) — first run`: 8 lines `visionos R0
  video N: bot check, answered as R6 (visionOS with visitor data: DIRECT) → §7 step 8` (videos 1,
  3, 4, 5, 7, 8, 9, 10).
- Per rung (videos with V- or H-passed rows / videos asked):
  - R0 visionOS: 1 / 10 — video 2 `DIRECT` up to 2160p, V pass; `BOT_CHECK` on 8 (no visitor
    data); video 6 (made-for-kids) `VIDEO_VERDICT` (UNPLAYABLE "This video is not available").
  - R0+vd (R6's chain request, not a rung): 8 / 8 `DIRECT`, V pass — the R6 answer works on every
    bot-checked video.
  - A: none bundled (0 asked). S: 0 asked (no cipher in a visionOS answer).
  - H: 1 / 1 (video 2): 2xx / 2xx / 2xx, no key, 17 variants up to 2160p, audio separate, 38
    segments, duration matches.
  - Android 21.26.364: 2 / 2 `SABR_ONLY` (only the 360p progressive row, V pass; videos 2, 6).
  - embedded: 0 / 2 `VIDEO_VERDICT` (error 152-18; videos 2, 6).
  - MWEB 2.20260708.05.00: 2 / 2 `NEEDS_SCRIPT` → S ok-sig+n, V pass (up to 2160p and 1080p).
- Pages: 10 / 10 HTTP 200 (MWEB 2.20261014.01.00; signature timestamp, visitor data and key on
  every page); 3 player scripts in one run (21090930 on 7 pages, 0229cd64 on 2, 6870cdc1 on 1).
- Version sources: visionOS answer reachable, no value; App Store 21.41.5 with no visionOS device
  (no visionOS value); yt-dlp visionOS 1.02 and Android 21.26.364 (= bundled); YouTube.js visionOS
  1.02 (=), Android 21.03.36 (older); Play Store page over 64 KiB; embedded and MWEB page config
  2.20261014.01.00 (newer, not tried: the acceptance request runs on video 1, which met a bot
  check). MWEB keeps the bundled version, which works (S, V pass).
- Findings for D2: (1) visionOS's UNPLAYABLE "This video is not available" on the made-for-kids
  video is not the video's verdict: MWEB (S) and Android answered it. The ladder stops there and
  R6's chain decides (guardrail 4), so no MCF; D2's judge takes it as a client refusal to record.
  (2) The Android answers reached the shape walk's bound (`trunc`): a fingerprint bound, not a cut
  answer. (3) embedded refuses ordinary videos (152-18), as in Phase 1.2 N7.
- ⚠️ Gate question (plan §9 item 9, proposed): under the per-video bot-check rule H and the O rungs
  are asked only on videos whose first visionOS request had no bot check (here 2 of 10), so the
  gate's "≥ 6 of 10 videos" cannot be met even when every ask passes (MWEB 2 / 2, H 1 / 1).
  Proposal: count each rung over the videos it was asked on (live: passed on ≥ 60 % of them in
  ≥ 2 of 3 runs, ≥ 1 asked video in each); the asked counts stay in the table. As written, neither
  H nor an O rung can be live (§9 decision 5). No code change either way.
- Plan: §5 census table (valid 1), version-source table (run 1), status board D0, §9 item 9.
- ⏸ Phone (owner): valid run 2 ≥ 1 h after run 1 (from 00:06 UTC), valid run 3 ≥ 1 h after run 2
  (ideally another time of day), then one Weekly check; the same APK, normal mobile data, no
  clearing app data → Copy result each time.

## Phase 1.3 — D0 follow-up 2: bot checks per video (owner, 2026-10-15; `81388f8`, CI ✅); census runs 2–4 not counted

- Owner decisions (2026-10-15):
  - Census runs 2, 3 and 4 (2026-10-14) are not valid: **not counted** (records in the D0 section
    below; plan §5 "Not valid" table), evidence only.
  - Bot checks per video, as R6 answers each lookup on its own: the run stops only on a bot check
    of a request that already carries visitor data, or on HTTP 429.
  - Run 2's HTTP 429 was an IP limit (the release app, R6, showed a rate-limit error on the same
    mobile data): the census still stops on 429 (no retry, no bypass). The census uses the same IP
    as the daily app, so runs stay few and ≥ 1 h apart (plan §7).
  - The 30 min cooldown is not a bug: the owner cleared the app's data before run 4, which reset
    it. Cooldown code unchanged, no 1 h gap display; plan §7 gets the owner's line: "Do not clear
    app data to skip the cooldown; it protects the IP the daily app uses."
- ✅ `ClientCensus` (Full and Weekly), per video:
  - a bot check on visionOS's first request of a video (no visitor data, R6's own first request):
    answered as R6 for that video (its watch page once, visionOS `R0+vd` with the page's visitor
    data, V on one row, nothing else for that video), then the next video — for every video, not
    only the run's first;
  - a bot check on a request that already carried visitor data (`R0+vd`, or a client asked with
    the page's visitor data): the run stops, 30 min cooldown; HTTP 429: the same (no retry);
  - a bot check on any other request without visitor data (for example Android when the video
    has no watch page): that video ends (R6 has no visitor data to ask again with), a note says
    so, the run goes on;
  - Weekly: videos 1 and 5 are each answered as R6 on their own. Version acceptance: no newer
    version is tried on a video that met a bot check; a bot check while trying one is recorded as
    `bot-check` (not `rejected`) and no further version is tried.
- ✅ Report (`CensusDiff`): the bot check that stopped a run names its request: `BROKEN bot check on
  <client> <step> video N (a request with visitor data) — the run stopped; no census for 30 min`;
  every other bot check is one CHANGED line per video (`visionos R0 video N: bot check, answered
  as R6 (…)`, or `<client> <step> video N: bot check without visitor data, nothing else asked for
  this video`). A bot-check answer is never compared as a class, count or shape change (it
  refuses the request, not the client).
- ✅ Census screen: the intro says the same (per video; a bot check on a request with visitor data,
  or HTTP 429, stops the run; no run for 30 min).
- ✅ Tests: `ClientCensusTest` (every video answered as R6; a request with visitor data stops at
  once; another request without visitor data ends that video only; Weekly: videos 1 and 5 both
  answered, `CHANGED (2)`); `CensusDiffTest` + 1; `CensusR6ParityTest` + 1 (two bot-checked
  videos: the census's requests to YouTube = R6's for both lookups, no Cookie or Authorization).
  Ladder 72 / 72; module 494 run / 0 failed (2 skipped); drift 0; `:app:compileDebugKotlin` and
  `:app:lintDebug` OK.
- ✅ Simulator T1b per video (3 lookups per base; R6 answers each on its own): R6 off and on 15 / 15
  complete, wrong 0, MCF 0. Golden: the 10 T1b lines only (485 cases, 515 lookups per column);
  totals unchanged (off: wrong 72, MCF 275; on: wrong 72, MCF 160).
- Plan: Burmese summary, guardrail 4, status board (D0, D1), §4 D0 bot checks + Weekly, D1 table
  (T1b), as-built T1b note, §5.2 (T1–T3, T1b), §5 "Not valid" table (runs 2–4), baseline (T1b,
  totals), §7 runbook (the IP note and the owner's line), §9 item 7; prompt rule 4.
- ⏸ Phone (owner): in the morning, on normal mobile data (no airplane mode or network change; do
  not clear app data): install the `81388f8` APK → "YFT client census" → Full census → Copy
  result. 3 Full runs ≥ 1 h apart, then one Weekly check. A stop (bot check or 429) → no retry;
  wait for the next run's time.

## Phase 1.3 — D0 follow-up: bot-check rule (owner, 2026-10-15) + census = R6's requests (`5cb781c`, CI ✅)

- Owner decisions (2026-10-15): bot-check rule **yes**; D1 targets (a), (b), (c) **yes** (plan §5.2,
  §9 items 7–8). SOURCE_HOSTS_APPROVED, D8 and M5 stay undecided (not done). Step 4b (airplane
  mode / new IP) removed: never change IP or network to avoid a bot check (a bypass).
- ✅ Bot-check rule in `ClientCensus` (Full and Weekly): the run's first bot check, when it answers
  visionOS's first request (no visitor data, R6's own first request), gets only what R6 sends
  next: that video's watch page once, then visionOS once more with the page's visitor data (body
  + `X-Goog-Visitor-Id`, step `R0+vd` = R6's `visionOsInChain` request), V on one row of that
  answer (a one-byte read of a file the download reads, on the host the answer named), nothing
  else for that video (no S, H, alternate, Android, embedded, MWEB, no newer version tried); the
  run goes on. No visitor data on the page → visionOS is not asked again (as R6). A second bot
  check, a bot check on any other request or HTTP 429 → the run stops, 30 min cooldown. Weekly:
  the same; video 1 answered as R6 → its other clients are not asked.
- ✅ Report: an answered bot check is a line even on a first run:
  `CHANGED visionos R0 video N: bot check, answered as R6 (visionOS with visitor data: <class>) →
  §7 step 8`; a stopped run stays `BROKEN bot check — the run stopped; no census for 30 min`.
- ✅ Parity (step 2): `CensusR6ParityTest` (4 tests) runs Master's module (R6 as the app runs it:
  the user's cookie removed, no solver) and the census on the same scripted YouTube: the census's
  first request = R6's visionOS-first request (address, every header in order, body); after a
  bot check the census sends R6's watch page GET and R6's visionOS request with visitor data and
  nothing else to YouTube (R6 also asks the page's own client, MWEB); when visionOS refuses again
  the census stops after R6's third request (R6's chain goes on); without visitor data neither
  asks again. No Cookie or Authorization anywhere.
- ✅ Census tool vs Master's lookup (step 2), two differences fixed in `ClientCensusActivity`:
  - HTTP client: a bare `OkHttpClient.Builder()` → `NetworkConfiguration.createClient()` (the app's
    client: 20 s connect, 30 s read/write) under `OkHttpExtractorClient` (the class Master's lookup
    uses: redirects re-checked by hand, no cookie jar). No retry, on purpose (the census records
    a failure; a retry only repeats a request).
  - User-Agent (watch page, embedded, MWEB, V, H): the raw WebView value (`; wv`, `Version/4.0`)
    → `BrowserUserAgent.from(…)`, what YFT's browser tab sends and Master's lookup gets. The
    visionOS and Android requests carry their app's own User-Agent, so the first request was
    already equal.
- ✅ Slot 7 default (the ID is in `ClientCensus.DEFAULT_VIDEO_7`, not in docs): a public 3:30 video
  whose owner disabled embedding (checked 2026-10-15: oEmbed 401; the embed page says "Playback on
  other websites has been disabled by the video owner"; yt-dlp's "Non-age-gated non-embeddable
  video" test). The field shows the default (slots 4 and 7) when no value was saved.
- ✅ Simulator T1b (bot check only for visionOS asked without visitor data): R6 off and on 5/5
  complete, wrong 0, MCF 0; 2 player requests + 1 page (2 after the bot check). 97 cases per base,
  485 cases (505 lookups) per column; totals unchanged (off: wrong 72, MCF 275; on: wrong 72,
  MCF 160). Golden + 10 lines (970), nothing else changed. The "every case changes its world"
  check now also asks each identity without the page's signals (else T1b looks unchanged).
- Plan: §2 guardrail 4, §3, §4 D0 (rule, slot defaults, normal mobile data only), D1 table (T1,
  T1b), §5.2 (T1–T3 + T1b, targets a–c), §5 census table (`R0+vd` row; run 1 under "not valid"),
  baseline (T1b, totals), §6, §9 items 7–8; prompt rule 4 and the STOP list.
- Tests: ladder 68 / 68; module 490 run / 0 failed (2 skipped); drift 0; `:app:compileDebugKotlin`
  OK.
- Step 4d adapted: the app shows Copy details only after a failed lookup (Home, not found), so:
  a normal lookup of video 1; formats shown → say which; not found → Copy details.
- Phone (owner, normal mobile data): install the `5cb781c` APK; 4d; then 4e — "YFT client
  census" → Full census → Copy result. BROKEN bot check again → no retry; wait. Runs 2–4 came
  from it (D0 section): not counted; next step in the top section.

## Phase 1.3 — D1 change simulator + R6 baseline (`f35b78f`, CI ✅); paused: D2 needs the census

- ✅ Built (test sources only, `extractor-master/src/test/.../modules/youtube/ladder/`):
  - `SimWorld`: one simulated YouTube per base answer (main's 5 fixtures, read in place; every
    change made in code; HLS playlists made in code; nothing captured). Each identity answers from
    the base's formats with its own made-up address: visionOS direct (+ HLS address, never sends
    sts), embedded / MWEB / the page's inline answer with `signatureCipher` + `n` (open only with
    the right values and, when asked, the page's sts), Android = main's progressive answer. The
    world knows what every address opens: 403, unsolved or wrong `n`/`sig`, another video,
    encrypted, failed H, unchecked.
  - `SimCases`: B0 (control) + V1–V11, L1–L16 (× V pass / fail / unknown on the L rows), Y1–Y6,
    T1–T3, H1–H2, F1–F4 on each base: 96 per base, 480 cases (500 lookups) per column; each case
    names the rungs with a checkable complete path (for MCF).
  - `SimMeasure`: per lookup complete / without sizes / partial / clean stop / wrong row, MCF
    (§5.1), requests by kind (player, page, probe, HLS, source, media), requests after a bot check.
  - `MasterClientLadderSimTest` (9 tests): self-checks of the world (ids and classes; every case
    changes its world; base answers parse and open; the sts rule; HLS passes `HlsCheck` and every
    F3 fault breaks it; L14 hides player ID, sts and visitor data; V modes touch only the L rows;
    decoys and encrypted files are wrong) and the R6 baseline, pinned line by line in
    `src/test/resources/ladder/r6-baseline.txt` (960 lines, so flag off = R6 stays proven);
    cookie and authorization never sent.
- R6 baseline (plan §5 table filled; ladder columns with D6). Totals: own solver off wrong 72,
  MCF 275; on wrong 72, MCF 160.
  - Wrong rows: V5 / V6 (visionOS's refused adaptive rows offered without V, 22 each); F4
    (another video's rows, R6 does not check the video ID, 28).
  - V2 / H2 with the own solver: clean stop (V drops visionOS's signed rows, refused without sts,
    and the chain is not resumed); without it Android's 360p only. F2 with it: clean stop (V
    unknown).
  - V3: complete rows without sizes (R6 reuses visionOS's unsized answer in the chain).
  - L: R6 reads no moved or renamed layout: clean stop in L1, L2, L4, L7, L8, L10, L13, L16;
    Android's 360p only in L5, L14, L15; L3 complete only with the own solver (the script
    clients' ciphers are untouched); L6 complete (the parser takes both types).
  - T1: 4 requests after the first bot check (page + 3 player); the ladder sends none that R6
    would not send (bot-check rule, owner 2026-10-15; T1b added then).
  - V1, V4, V7–V10, Y1–Y6, H1: own solver off = Android's 360p only; on = complete via embedded.
- Plan adapted (why):
  - Only the R6 columns now: the parity gate needs D6's ladder seam in `MasterYouTubeModule` and
    the L round-trip needs D5b's L; both are written with those tasks.
  - V `fail` / `unknown` act on the L rows only (files the copied parser cannot read; in L14 the
    files that need the moved page signals; a moved HLS address): rows R6 can read are not L rows.
  - MCF counts against every simulated path; the live-backed paths come with the D0 census.
  - "Without sizes" (P22's rule met, a row without size) is listed apart and is not MCF; Z's job.
  - Not modelled: a renamed DRM marker with the formats in place (L12 moves them).
- §5 target changes (D1 stop rule) — **approved by the owner on 2026-10-15**, now in plan §5.2:
  1. Budget "no more player requests than R6" in V2: R6 with the own solver makes 1 and offers
     nothing; S needs 2 (visionOS + visionOS with the page's sts). Proposal: compare with R6
     without the own solver (V2: 3) → ladder ≤ 2.
  2. "Where R6 is below 100 %, the ladder completes more cases": impossible where no checkable
     path exists (L9, L11, L12, T1–T3, F1–F3: the right answer is no row). Proposal: only in
     classes with a path; elsewhere the same verdict and wrong 0.
  3. H2 "saves at least one request": R6 with the own solver makes 1 request per lookup and stops.
     Proposal: the second lookup ≤ 1 player request and complete (the first needs 2).
- Tests: ladder 61 / 61; module 483 run / 0 failed (2 skipped); drift 0.
- Risks: the world's rules are assumptions (cipher without sts → 403; visionOS never sends sts;
  every identity works, which the live-backed column limits to the census); a class not modelled
  ends as a clean stop in the ladder, W shows it, and it becomes a class.
- Paused (⏸): D2 needs the D0 census (3 Full census runs on mobile data, ≥ 1 h apart, + 1 Weekly
  check) and the gate.

## Phase 1.3 — D0 census tool — code done (`1135afe`, CI ✅), owner census pending

- ✅ Built in extractor-master `modules/youtube/ladder/` (offline tests with
  `FakeExtractorHttpClient`: 52 new, module 474 run / 0 failed, drift 0):
  - `ClientCensus`: **Full census** per video 1–10 in plan order (visionOS as R6 asks it;
    visionOS again with the page's sts + visitor data only after `signatureCipher`; video 1:
    bundled alternates; Android; embedded; MWEB), V on one AVC row, H check; budget per video
    1 page, ≤ 6 player requests, ≤ 5 V, 1 H; a run ≤ 6 MB. **Weekly check**: videos 1 and 5,
    ≤ 60 s, ≤ 2 MB, ≤ 20 requests; version sources only when older than 24 h.
  - `HlsCheck`: token step (master, variant, first segment: 2xx / 403 each), variants, highest
    resolution, audio form, codecs, duration ±2 s.
  - `ShapeFingerprint` + `ShapeDictionary`: key names only (arrays collapse; odd or redacted
    keys become `*`), core and full hash, bounded walk (depth 16, 100 000 nodes).
  - `CensusSources`: version sources ≤ 64 KiB each, no token, no cookie (web-family page config;
    visionOS: answer/page field, App Store lookup, yt-dlp and YouTube.js tables read as text;
    Android: Play Store page, the reference tables); usable = 3 of 3 runs.
  - `CensusDiff`: `NO CHANGE` / `CHANGED (n)` / `BROKEN (n)` + days since the last run, one line
    per change (class, version, shape with **BREAKING**, H, stop) naming its runbook step.
  - `CensusModel`: `files/master/census-last.json` (last 8 runs; no address, value, visitor data
    or video ID) and `CensusSanitizer` for the report.
  - `CensusNoBackgroundTest`: the census code uses no background API; the debug manifest has
    no service, receiver or provider.
- ✅ Debug owner tool **"YFT client census"** (`ClientCensusActivity`, launcher entry only with
  the owner tools on): Full census / Weekly check / Copy result; videos 1–3 fixed, 4–10 entered
  by the owner (kept); Wi-Fi warning; byte meter; last-run line on the screen.
- Plan adapted (why):
  - The census copies the extractor's own request and solve steps instead of the app's
    `StreamCheckMeasurement` (app-only, so it could not be tested offline in extractor-master).
  - BREAKING = a removed path the copied parser reads to build rows; its negative or alternative
    signals (cipher, DRM, SABR address, `isLive`, reason texts) are left out (their removal never
    loses a row; a lost row shows as a class or count line).
  - HTTP 429 is treated like a bot check: the run stops, no new run for 30 min.
  - No alternate visionOS version is bundled (the reference table lists only 1.02), so step 3
    is skipped and the report says so.
  - embedded and MWEB take their version from the page config; the MWEB value is
    acceptance-tested with the rung's own request.
  - The Play Store page is probably larger than 64 KiB; then it is recorded as "over 64 KiB"
    (not reachable, so not usable), never read past the cap.
  - Leaving the screen stops a run (no background work, guardrail 11).
- Pending (owner, phone): 3 Full census runs on mobile data, ≥ 1 h apart, then one Weekly
  check; then the census table, version-source table and gate decision go into plan §5.
- ❌ Census run 1 — **not valid** (owner, 2026-10-14 17:19 UTC, mobile data, metered): FULL, 9
  videos (slot 7 empty), 1 request, 1920 bytes, 731 ms, stopped: bot-check. "BROKEN (1) — first
  run", "BROKEN bot check — the run stopped; no census for 30 min → §7 step 8". The only request:
  video 1, visionOS R0 1.02, HTTP 200, `BOT_CHECK` (`LOGIN_REQUIRED` "Sign in to confirm you're
  not a bot"), 0 formats, sh `7efb11d3c42e`, shf `4797c59004fe`; top keys adBreakHeartbeatParams,
  playabilityStatus, responseContext, trackingParams. Why not valid: the old rule stopped the
  whole run at R6's first request, where R6 goes on (watch page, then visionOS with the page's
  visitor data). The release app on the same mobile data at 17:31 UTC: a music video lookup
  succeeded (M4A 2.9 MB, 720p60, 480p, More formats 6), so the IP is not blocked. Fixed by the bot-check
  rule (the D0 follow-up sections above).
- ❌ Census runs 2, 3, 4 — **not counted** (owner, 2026-10-15; evidence only; plan §5 "Not valid"),
  `5cb781c` APK, mobile data (metered):
  - Run 2 (2026-10-14 20:01 UTC): FULL, 10 videos, 2 requests, 3501 bytes, 1.7 s, stopped:
    rate-limited. Video 1, visionOS R0: `BOT_CHECK` (as run 1), answered as R6: the watch page →
    HTTP 429, so the run stopped (no retry). The release app (main, R6) showed a rate-limit error
    on the same mobile data just before (owner): an IP limit, not a census fault.
  - Run 3 (20:06 UTC, after an airplane-mode toggle, so a new IP; owner): FULL, 5 videos (slots
    1–4, 7), 20 requests, 1099 KB, 11.9 s, stopped: bot-check. Evidence: video 1 `BOT_CHECK` →
    watch page → `R0+vd` DIRECT, V pass (the R6 rule works); video 2 visionOS DIRECT up to 2160p,
    V pass, H 2xx / 2xx / 2xx with no key (17 variants up to 2160p, audio separate, 38 segments,
    duration matches), Android `SABR_ONLY` (360p progressive only, V pass), embedded
    `VIDEO_VERDICT` (error 152-18), MWEB 2.20260708.05.00 `NEEDS_SCRIPT` (S ok-sig+n, V pass);
    video 3 visionOS R0 `BOT_CHECK` → the run stopped (the `5cb781c` rule: a second bot check;
    now per video). Version sources: MWEB page config 2.20261014.01.00 (newer, not tried: the run
    stopped first); yt-dlp visionOS 1.02 and Android 21.26.364 (= bundled); YouTube.js visionOS
    1.02 (=) and Android 21.03.36 (older); App Store 21.41.5 with no visionOS device (no visionOS
    value); Play Store page over 64 KiB. The report said "first run" and slots 5–10 were empty,
    so run 2's saved state was already gone before run 3.
  - Run 4 (20:12 UTC, 6 min after run 3): the same as run 3, stopped at video 3; App Store source
    `NETWORK`. The owner cleared the app's data before it, which reset the 30 min cooldown — not
    a bug (owner; cooldown code unchanged). Offline check: run 2's and run 3's rebuilt states
    block a run at run 3's and run 4's start (cooldown 26 and 25 min left).

## Phase 1.2 — done (owner closed it on 2026-10-14)

- ✅ Pass criteria (plan §4 "Result"): corpus = ejs 260/260; ladder wrong 0 in every class with and
  without V; simulated V 344/352 = 98 % of M1–M9 (≥ 95 %); faults 0 wrong / 0 missed; phone speed
  and live check ✅; flag off = main, CI green (`e5cad15`).
- ⚠️ Offline survival 0 % (target ≥ 80 %): by design since "V on every signature" (owner, N5b
  2026-10-12): no signature is used without V; all safe, none wrong.
- ⚠️ M5: ladder 24 = ejs 24 (not more): on 8 es6 files every step throws at init 3 times, the page
  too, so the init-error rule declines right answers (safe). Reproduced 2026-10-14 (24/8/0).
  Phase 1.3 candidate, owner decides.
- N4b closed (the ladder never uses worker B's M9bs wrong value). N2's n rule stays PENDING
  (decision; owner run 3 invalid as runs 1–2). Private video skipped (owner).
- Next: Phase 1.3 (`MASTER_KEY_PHASE1_3_PLAN.md`, owner's plan `f2c144e`).

## Phase 1.2 — N7 follow-up: page cap 20 s (owner, 2026-10-14; `e5cad15`, CI ✅)

- ✅ Page cap 35 s → 20 s: `OwnPlayerScriptRunner.DEFAULT_PAGE_CAP_MILLIS` (`OwnSolverProbe`, so
  the canary, takes the same default) and `scripts/own-solver/ladder.mjs` `STEP_CAPS_MS.page`
  (`ladder.test.mjs` checks it against the Kotlin constant). Why: on the phone a page job stalls
  the in-app browser for about its own length (0.8–1.3 s, page P); the cap only bounds a slow page
  job, so the longest possible stall goes 35 → 20 s. 20 s = the corpus's largest page B on the
  computer (7.5 s, A.1) × the phone's highest factor 1.4 × about 2. Worker 40 s and lookup 60 s
  unchanged.
- Effect on the ladder: when the workers use 40 + 20 s, the 20 s left is now page P's full cap, so
  a page P that ends `budget` there is off at once for that player (before: a short deadline, kept
  only the second time in a row).
- Tests: `OwnSolverLadderRunnerTest`: the page deadlines are 20 s; "all four steps slow" now takes
  60 / 60 / 20 / 0 s over four lookups (was 60 / 60 / 60 / 35 / 0 s); new: a page step with only a
  short deadline (10 s left) is not kept and runs again in the next lookup with its full cap.
  Local: extractor-master solver tests 126/126, JS 227/227. CI ✅ (`e5cad15`): checkpoint validation
  38490290692, opt-in APK push build 38490284228, own solver canary 38490284161.
- No new APK needed: the opt-in APK `eecfba7` still has 35 s, but the cap only matters for a page
  job slower than 20 s, and the phone's page jobs took ≤ 1.3 s; the live check and V run on it.
  The next APK built for another reason carries 20 s.
- Owner stream check (2026-10-14, `eecfba7`, aqz-KE-bpKQ): MWEB, n only, agree, all four reads 403,
  `valid: false`, as the 2026-10-11 run: the n rule stays PENDING (plan §2); nothing more to measure.
- ✅ V on the phone (owner, 2026-10-14, opt-in APK `eecfba7`, mobile data): §4.3's 1080-ladder
  video, whose streams need a signature (MWEB: every format ciphered, checked from the computer).
  The opt-in build resolves with the own runner only (`OwnSolverSelection`), and
  `YouTubeSignatureGuard` uses its signatures only after V. Rows: audio M4A 130 kbps and MP3;
  video 2160p and 1440p (WebM), 1080p, 720p, 480p, 360p (MP4). MP3 (3.3 MB) and 720p (29 MB)
  downloaded in well under a minute (not throttled).
- ✅ Live check against the release app (owner, 2026-10-14, mobile data; opt-in `eecfba7` vs the
  release app): the same rows (formats, sizes) on §4.3's short progressive, 1080-ladder and Shorts
  videos, a 2160p60 video and the canary's lookup video. Age-restricted video: terminal in both
  ("Sign in to YouTube on this page first", LOGIN_REQUIRED "Sign in to confirm your age" for the
  visionOS and WEB clients; no bypass). Private video: skipped (owner, 2026-10-14).
- Run 3's "no stream" video gives rows in both apps (their first client is visionOS), so run 3's
  FAIL came from the canary lookup's own clients (embedded, then MWEB SABR only) at that moment,
  not from the solver or the app.

## Phase 1.2 — N7 owner live check — done (canary tool `41d8a11`, worker fix `f9247bb`, round 2 prep `eecfba7`; round 2 on the phone ✅: P, B and E pass on both builds, P hit 0.3–0.4 s, 1.4 s after a restart)

- ✅ Canary tool (`41d8a11`, debug build only, opt-in APK `yft-master-optin-debug-apk-next-<sha7>`):
  `SolverCanaryActivity` (the owner starts it in the debug app), `SolverCanary` (first the app's
  lookup of one public video with V, then per build — phone, then main — ejs and the own steps P1,
  P2, B1, B2, page P, page B through `OwnSolverProbe`, with the app's breaker), report
  `SolverCanaryReport` (first line PASS/FAIL, hit times against the targets, verdicts P/B/E and V,
  kinds, counts, classes and times only; never a value, a player text or an address).
- ❌ Round 1 (owner, Android 16, WebView 149, app 1.0.0-beta.3-debug; 2 pairs = 4 runs): all FAIL,
  "stopped at phone B1 (error WorkerError)" after 4.2–4.7 s.
  - P: n 3/3, sig 3/3 against ejs. P2 hit in the same process 0.3–0.5 s ✅ (target ≤ 2 s).
  - Lookup: rows; its worker P in a new process 1.4–1.7 s, but without parts ("no parts yet"):
    YouTube served two player versions in turn ("lookup player same/other"), and parts are kept
    per player version (the store keeps 32), so a later run on a known version hits. V: not
    needed. Breaker off: none. Data: 1.7 MB the first run, ≈ 1.0 MB later.
  - B2, page P/B (E) and the main build never ran: the canary stopped at the first step that did
    not give a result.
- ✅ Computer, same day (`own-solver-canary.mjs`, today's player): P and B pass 3/3 on the four
  builds (B 3.6–5.3 s), so the phone's B error was not the core's answer.
- ✅ Cause (local headless Chromium with the phone build's text and a fake bridge; classes and
  counts only): strategy B runs the player in the worker's own global, and the player's code
  then raises error events of its own (4 at ≈ 2.6 s, one TypeError later). An error event the
  worker does not cancel goes on to the host page, whose `worker.onerror` fails the job
  ("WorkerError") and ends the worker before its reply. P raised none in the same run. Not
  memory: the owner's option 3 (a heap test) was not needed.
- ✅ Fix (`own-solver-worker.js`): a listener added before any player code runs cancels the
  worker's error events (`preventDefault` taken at the start, so the player cannot replace it);
  the job's own errors are caught as before and replied as error classes. No listener where there
  is no event target (the offline tests' bare context). Local repro after the fix: B result n 3,
  sig 3, init errors 0, parts n + sig, ≈ 3.8–4.0 s; P unchanged (≈ 1.4–1.5 s); B and P give the
  same values (3/3 n, 3/3 sig; compared on the computer, not printed).
- ✅ Canary: only a step whose engine gave no readable reply (none, a ready or job timeout, a gone
  renderer, an unreadable reply) stops it. A reply with an error class or out of time, and a step
  not run (breaker off, no prepared program), no longer stop it: the steps after it still run.
  B2 runs only when B1 kept a part (`OwnSolverProbe.hasParts`). The lookup the same: an error
  class, a budget or the lookup cap no longer stops the canary; a lookup whose worker P the breaker
  has off is "not measured" (before: stopped).
- ✅ Tests: `worker.test.mjs` 12 → 13 (the listener cancels, a replaced `preventDefault` changes
  nothing, no listener without `Event`); `OwnSolverProbeTest` 20 (stop rule: gone and an unreadable
  reply stop, breaker off, a budget and an error class do not; `hasParts`). Local: spike CI JS step
  225/225; app compile + `SolverCanaryReportTest` 13/13.
- ✅ Round 2 prep (`eecfba7`, owner's options 1 and 3; CI ✅: checkpoint validation, push APK, own
  solver canary; label build 38482303471 ✅, artifact `yft-master-optin-debug-apk-next-eecfba7`,
  19.2 MB): the page (`own-solver-page.js`) gives a worker
  error event back as one fixed class only, never its message: `WorkerStackOverflow`,
  `WorkerOutOfMemory`, `Worker` + a standard class the message starts with (`WorkerTypeError`,
  `WorkerRangeError`, …), else `WorkerError`; a reply it cannot read (`worker.onmessageerror`) is
  `WorkerMessageError`. The `Worker` prefix keeps them out of the environment rule
  (`OwnSolverEnvironmentErrors`): such a failure never sends a job to E. The report's run line
  shows the phone's JS heap limit (`performance.memory.jsHeapSizeLimit` of the app's WebView
  renderer, Chromium only, read in the tool's own WebView after the steps; else "unknown").
  Tests: `worker.test.mjs` 13 → 15 (fixed classes, no text, late reply ignored,
  `onmessageerror`); `OwnSolverEnvironmentErrorsTest` + 1; `SolverCanaryReportTest` 13 → 14 (the
  run line). Local: CI JS step 227/227; `OwnSolverEnvironmentErrorsTest` + `OwnSolverProbeTest`
  24/24 (JVM); app compile + `SolverCanaryReportTest` on CI. Local headless Chromium, the page
  with a fake worker (no player): a stack overflow → `WorkerStackOverflow`; a `TypeError` (also
  one thrown in a timer) → `WorkerTypeError`; `ReferenceError`, `RangeError` → `Worker` + class; a
  thrown string, a made-up name or a plain `Error` → `WorkerError` (no text in any).
- ✅ Round 2 (owner, 2026-10-14; Android 16, WebView 149.0.7827.163, app 1.0.0-beta.3-debug, JS heap
  limit 1850 MB; 3 runs, the app closed between them, so each run is a new process; today's player,
  the same in the lookup): run 1 PASS, run 2 PASS, run 3 FAIL (the lookup only, below). Every
  solver line of every run: P, B and E pass on both builds (n 3/3, sig 3/3 against ejs); breaker
  off: none.

  | run | 1 | 2 | 3 |
  |---|---|---|---|
  | P2 hit, same process (phone / main build) | 0.4 / 0.4 s | 0.3 / 0.4 s | 0.3 / 0.4 s |
  | lookup's P after a restart | 1.7 s, no parts yet | 1.4 s, n hit | not measured |
  | P1 from the text (phone / main) | 1.2 / 1.5 s | 0.9 / 1.5 s | 0.9 / 1.5 s |
  | B1 from the text (phone / main) | 5.5 / 6.9 s | 5.2 / 6.7 s | 5.2 / 6.6 s |
  | B2 hit (phone / main) | 2.1 / 2.8 s | 1.6 / 2.9 s | 1.8 / 2.9 s |
  | page P, its stall (phone / main) | 1.0, 0.9 / 1.4, 1.3 s | 0.9, 0.8 / 1.4, 1.3 s | 0.9, 0.8 / 1.3, 1.2 s |
  | lookup, start to rows | 14.2 s | 3.3 s | no stream |
  | data (app) / canary total | 2.7 MB / 44.3 s | 1.0 MB / 30.1 s | 380 KB / 28.6 s |

  - Run 1 was the first after the install: the lookup's store had no parts, so its P solved from
    the text (1.7 s, ready 0.7 s). Lookup 14.2 s = page 6.8 s + player API ×2 0.8 s + player
    download 4.5 s (590 KB) + P: the network first. Run 2: 3.3 s = page 0.6 s + API 0.5 s +
    download 0.3 s + P 1.4 s (ready 0.4 s, n hit).
  - Run 3: YouTube gave the lookup's video no stream (embedded client: "video unavailable"; MWEB:
    SABR only, no stream address of at least 1 MiB), so no n job, no V and no after-restart number,
    and the canary's rule (V ok) made it FAIL. Not the solver: runs 1 and 2, minutes before, gave
    rows for the same video; every solver line passed.
  - V: "not needed" in runs 1–2 (the lookup's stream needed no signature), "not run" in run 3. V
    has not run on the phone yet.
  - Page B did not run (page P answered both kinds). Ready waits 0.0–0.7 s (the longest at run 1's
    cold lookup). No worker error class showed: `f9247bb`'s fix holds on the phone (round 1 stopped
    at B1 in every run).
- ✅ Option 3 (computer, 2026-10-14; Node 24, this computer; today's player, each solve in its own
  worker isolate, its heap sampled about every 10 ms from the main thread; MB only):
  - Peak JS heap used, main / tce / es6 / phone build: ejs 152 / 152 / 134 / 96 MB, P 247 / 249 /
    219 / 188 MB, B 473 / 473 / 473 / 310 MB. Times: P 1.3–2.2 s, B 5.3–8.7 s.
  - Smallest old-generation limit that still solves n 3/3 and sig 3/3: phone build ejs 96, P 128,
    B 256 MB (192 runs out of memory); main build ejs 128, P 192, B 400 MB (320 runs out).
  - B needs about twice P's heap. The phone's limit is 1850 MB (the page's renderer; a worker's
    limit may differ): over 4× B's largest need, and B passed there. Memory is not a risk for B on
    this phone.
- Targets (plan §3 N7, §4 "Speed on the phone"):
  - ✅ P hit in the same process ≤ 2 s: phone build 0.3–0.4 s (3/3 runs); main build 0.4 s
    (recorded, no target).
  - ✅ P hit after a restart ≤ 8 s: 1.4 s (run 2, n hit, the engine's cold start included). From
    the text in a new process 1.4–1.7 s (round 1, run 1): even a miss is inside.
  - ✅ §4 first B run on a new player version ≤ 10 s (open since N5b A.1): B1 5.2–5.5 s (phone
    build), 6.6–6.9 s (main). B is a metric only: B2 hit 1.6–2.1 s (phone), 2.8–2.9 s (main).
  - Phone factor about ×1 of this computer (P1 ×0.7–0.9 and B1 ×0.85–1.0 against the same day's
    worker runs above; P2 ×1.0–1.4 against A.2's hits), not the ×4 margin: the job cap 40 s and the
    lookup cap 60 s keep their room; no re-tune.
  - Option 3 again (B's prepared program in memory): not needed. P passes on today's player and
    B's hit is 1.6–2.9 s (the condition: P fails, B is used and B hit > 5 s).
  - ✅ Page cap 35 s → 20 s (owner-approved 2026-10-14, N7 follow-up above): a page job stalls the in-app browser for about its own length (0.8–1.3 s on
    the phone, page P). The cap only bounds a slow page job. Chosen:
    20 s = the corpus's largest page B on the computer (7.5 s, A.1) × 1.4 (the phone's highest
    factor) × about 2; the longest possible stall 35 → 20 s.
  - ⚠️ P (b) in total with V: 3.3 s after a restart on a known player, 14.2 s the very first time
    (page and player download 11.3 s of it); the core 1.4–1.7 s of it. V never ran (no signature
    needed): its phone cost is unmeasured. From these numbers no timeout fallback to main and no V
    prewarm are needed now (owner decides; plan §5 "V wiring in the app").
- Open (outside N7's done rule): the live check against main's app on the YouTube URLs of
  `MASTER_KEY_PLAN.md` §4.3 (Phase 1.1 §4 and Phase 1.2 §4 "Live"), and V on the phone with a video
  whose streams need a signature; phone checklist in §4.3.
- ✅ Docs: runbooks (`MASTER_KEY_PHASE1_2_PLAN.md` §6, `MASTER_KEY_PHASE1_1_PLAN.md` §6) gain the phone
  canary and "computer passes, phone fails → local headless Chromium repro"; `MASTER_KEY_PLAN.md`
  §4.3 gains the phone checklist; plan board N7 → Done.

## Phase 1.2 — N6 canary workflow (computer) — done (`0714d76`; push run 38424779157 ✅, owner re-run ✅; label triggers `d390a0e`, CI ✅)

- ✅ Workflow `.github/workflows/own-solver-canary.yml` ("Own solver canary (spike)"):
  `workflow_dispatch` + pushes to the spike that touch the solver files (main's and the own
  solver's assets, `scripts/own-solver/**`, the canary, parity and mutation runners, the vectors,
  the workflow itself). No `schedule`: GitHub runs it only from the default branch; a daily
  schedule on main is the owner's decision. Node 24, `contents: read`, no secret. Checks (each
  runs even when an earlier one failed; any failure fails the job):
  1. today's player, 4 builds: ejs, own P, own B alone (`own-solver-canary.mjs`);
  2. corpus against the vectors: ejs, own P (`verify-own-solver.mjs`);
  3. corpus, own B alone (`--strategy B`);
  4. change simulator, quick: the phone build of each corpus player, 14 classes, 1 seed: ejs, own
     P and own B (`verify-own-solver-mutations.mjs --quick --strategy P,B`; any wrong value fails);
  5. job summary (`own-solver-canary-summary.mjs`): players, builds, classes and verdicts only.
  The scripts' stdout (names, counts, indexes) goes to the log; their stderr stays on the runner
  (Node prints a source line under some V8 errors, so stderr can hold player code).
- ✅ Canary (`own-solver-canary.mjs`): P and B alone (`{ strategy: "B", hint: false }`, as
  `--strategy B`) on every build; verdict per strategy and kind, worst first: wrong (a value that
  differs from ejs's) > oracle (ejs failed too) > miss (no value where ejs gave one) > pass; fail
  = no build. It passes only when all four verdicts pass. Plan adapted: a wrong value counts only
  where ejs gave one (before, a value without ejs's counted as wrong; such a player is oracle
  anyway). Downloads are tried 3 times, 5 s apart (a 404 = the build is absent, no retry). The
  report gets `strategies` and `verdicts.{P,B}.{n,sig}`.
- ✅ Tests: `canary.test.mjs` 3 → 10 (verdict rules, P + B verdicts, a build line without
  inputs or values, retry; summary: step states incl. a stopped step, corpus and mutation verdicts,
  Markdown without values, reports of checks that could not run skipped). Fail-first: 18/18
  code mutations killed. Spike CI JS step 224/224 (217 + 7).
- ✅ Local (2 CPU / 4 GB, Node 24.14.1): today's player (not in the corpus), 4
  builds: P n/sig pass, B n/sig pass (39 s; B 5.4–7.8 s per build); corpus own P 32/32, own = ejs
  260/260 (82 s); corpus B alone 32/32, 260/260 (241 s); quick P,B: 8 files × 14 classes × 1 seed,
  wrong 0 for ejs, own P and own B (own B survived 112/112, own P 104/112: M3 0/8 safe; 929 s).
  The summary rendered from these reports names players and verdicts only.
- ✅ CI for `0714d76`: canary push run 38424779157 ✅ in 14 min (today's player 28 s, YouTube
  answered the runner; corpus P 67 s; corpus B 158 s; quick P,B 597 s); build 38424778635 ✅,
  validate 38424785337 ✅.
- ✅ Manual run: the owner's re-run of 38424779157 (attempt 2) ✅, job summary "Result: pass".
- ✅ Plan adapted (owner 2026-10-13): GitHub's Run workflow needs the default branch, so the manual
  run = the owner's re-run of the push run. From now on, manual runs = labels on draft PR #2:
  run-canary (canary), build-optin-apk (opt-in APK with own_solver_next on).
- ✅ Label triggers (`d390a0e`; main untouched): both spike workflows also run on
  `pull_request: types: [labeled]` (no paths filter). The canary job runs only for run-canary,
  the APK job only for build-optin-apk; any other label = a run whose job is skipped. Both check
  out the PR head (`github.event.pull_request.head.sha || github.sha`), not its merge with main.
  Label APK: OWN_SOLVER=true and OWN_SOLVER_NEXT=true (both env pairs), artifact
  `yft-master-optin-debug-apk-next-<7-character sha>`, summary "Commit:" = the PR head. Pushes
  and manual runs unchanged (push: own solver on, ladder off, artifact
  `yft-master-optin-debug-apk`; the toolkit drift step as before). The concurrency group
  includes the label, so another label (a skipped run) never cancels a running canary or build.
  Test `scripts/tests/test_spike_label_triggers.py` (12 tests: a small GitHub expression
  evaluator checks push, manual run, run-canary, build-optin-apk and another label; both YAML
  files parse). Fail-first: 16/16 workflow mutations killed; scripts/tests 42/42; actionlint
  1.7.7 clean. If a label starts nothing, PR #2 may conflict with main (GitHub then runs no
  pull_request workflow): report it, never merge main.
- ✅ CI for `d390a0e`: canary push run 38438989262 ✅ in 15 min (today's player 29 s; corpus P
  64 s; corpus B 169 s; quick P,B 638 s); build 38438989328 ✅ (6 min, artifact 20.1 MB),
  validate 38438994892 ✅. Next: the owner creates the two labels; the first label runs.

## Phase 1.2 — N5b ladder, breaker, locator cache and V ledger behind `ownSolverNext` — done (TEST_SHA `48dd744`, test cycle 22/22 ✅; A.8 V wiring `df9a1e1`, CI 38413760115 ✅ + checkpoint 38413767701 ✅)

- ⏳ Plan adapted (owner-approved 2026-10-12; plan §3 N5 "Plan adapted (N5a results)"): V on every
  signature, environment tripwire, breaker also for worker jobs / gone renderer / V = fail, then
  the wiring behind `ownSolverNext` (default off); one test cycle at the end.
- ✅ Tripwire + V on every signature (this commit; app unchanged: the core checks only when a
  request asks `tripwire: true`, which only N5b's ladder will send, and nothing calls the rule
  yet). Plan adapted: no separate read counting; the second instance measures the dependence
  itself (a read that does not change the answer does no harm). Core: after worker P or worker B
  solves, the answered kinds run once more on a second instance with other environment values
  (page, user agent, language, platform, cookie setting, ...); a changed answer = one more init
  error (class `Tripwire`) and no answer for that kind (`kind:environment`). Page mode skips it
  (the window's own objects stay). Rule (Kotlin + JS, 22 shared cases, new `candidate()`): a
  signature from any step needs V. Runners: `--ladder` sends the tripwire (`--no-tripwire`: as
  N5a). JS 173/173 (171 + 2 tripwire tests).
- ✅ Step 0 evidence. Corpus `--ladder` (363 s, peak RSS 1.95 GB): 32/32, init errors 0/32 in all
  four steps (tripwire false positives 0); PB+V = ejs 260/260, B+V 260/260; PB off and B off: n =
  ejs 132/132, sig no value 128 (no signature without V, by design). M9c-nav 21cd2156 es6 (N5a's
  ladder wrong value): worker B `sig:environment` {init errors 1}, ladder safe in all four options.
- ✅ CI for `9f9eb95`: build 38270898966 ✅, validate 38270906278 ✅.
- ✅ 631d3938 main M9c-nav heap (N5a's out-of-memory line, mutation runner with `YFT_STAGE=1`
  stage/heap lines and the new `--skip-ejs`): main's ejs runs out of heap first (217 MB before it,
  exit 134 at ~37 s, ❗ a main risk, main untouched); with ejs skipped, own P (worker) runs out of
  heap too: an M9c-nav literal turns a player loop into one that never ends and grows memory.
- ✅ P step budget (core; Plan adapted: in the worker too, not only the page: the heap ran out
  before any timeout, and a renderer out of memory is gone, not terminated). Each loop body of
  P's program counts a step (B's counter); init and every candidate attempt get their own budget;
  a time cap is checked every 4096 steps; over budget, every later step throws
  `RangeError("steps")` until the next attempt. Budgets from the corpus (`--ladder`, 32/32
  files, new line "ladder P steps"): largest init 2814 steps / 196 ms, largest attempt 1005 steps /
  27 ms → init 30,000 steps / 20 s, attempt 12,000 steps / 3 s (steps ×10, time ×10 ×10 for a
  slow phone). Corpus with these budgets (`--ladder`, 384 s): init errors 0/32 in all four steps,
  PB+V = ejs 260/260, B+V 260/260, wrong 0. 631d3938 main M9c-nav with them: exit 0, all four
  steps safe (P: init errors 4), ladder wrong 0, own P heap +100 MB (was +1.4 GB at 50 M steps).
- ✅ eval / new Function (plan item 1: "refuse in page mode, or count"): refused in page mode.
  While a page job runs, `eval` and the Function constructors (also through `.constructor`, async
  and generator ones) throw `EvalError("dyncode")`: an init error in top-level code, no answer
  where n or sig needs it. Corpus: 0 such calls (N5a `--dyncode`). The worker runs them (the app's
  job timeout stops it).
- ✅ Job fields (worker and page frame): `"strategy":"B"` with `"hint"` (from the player text,
  nothing kept; a prepared B job is `BadJob`), `"tripwire":true` (worker). Fault fields stay
  blocked. JS 179/179 (173 + 4 step-budget tests, 1 worker, 1 frame).
- ✅ Kotlin, first part (not called yet; CI compiles it): `OwnSolverProtocol.Options` (strategy B
  + hint, tripwire) in `encode`; `OwnSolverEngine.Events` (`started` = the page read the job,
  `gone` = onRenderProcessGone) with `reportsReady`, in both WebView engines.
- ✅ CI for `cb75f1b`: build 38278036731 ✅, validate 38278041351 ✅.
- ✅ Kotlin, second part: the ladder behind `ownSolverNext` (`-Pyft.ownSolverNext=true`, debug
  builds only; workflow input `own_solver_next`, default off = Phase 1.1 unchanged).
  `OwnPlayerScriptRunner.Next`: worker P (kept program first, tripwire) → worker B (player text,
  no hint, tripwire) for kinds still open → page P, then page B, only when a worker needs the
  page; per kind the shared rule's `candidate()`. Ready timeout 20 s (engines that report it; no
  strike), job timeout 45 s. `OwnSolverBreaker`: a job timeout or a gone renderer turns that step
  off until the app restarts (both page steps together: one page engine); a marker per player
  version + step is on disk (`commit`) before each job and cleared after it; two left behind in a
  row (the process died twice) = that step off for that player version. V: a signature from any
  step is used only after one stream URL carrying it opens (`SignatureGate` around the reader:
  the reader is main's whole-file copy and stays main's text, R3; `StreamCheck`, control = a
  stream URL of the same lookup without an own signature); pass kept per player version + step (64), fail = that step's
  signatures off (the ladder looks further down), unknown = nothing kept and the signed streams of
  that lookup dropped. Plan adapted: the lookup counts as user-started (the extractor runs only for
  the page the user opened). Local JVM check (Kotlin 2.0.21): 69/69 (solver, own-solver YouTube,
  StreamCheck, canary; new: ladder runner 11, breaker 4, protocol options 2, V in the reader 2);
  Android parts compile in CI only.
- ❌→✅ CI for `6bc056e`: build 38282262857 ❌ at the R3 toolkit drift check (V was inside the
  reader's whole-file copy); fixed: the copy is main's text again (0 drifted), V moved to
  `SignatureGate`; local 70/70 (+1: an answer outside a lookup loses its signatures).
- ✅ CI for `8153f6c`: build 38284472227 ✅, validate 38284476176 ✅.
- ✅ Test cycle, unit: JS 179/179; Kotlin in CI (`8153f6c`) ✅.
- ✅ M9c-loc (`--strategy P,B --ladder`, tripwire on, one process per player: 11 runs, 1338 s,
  peak RSS 1.7 GB), survived/safe/wrong: ejs 32/0/0, own P 32/0/0, own B 12/20/0, page P
  0/32/0, page B 0/31/1 (9fcf08e8, as in N5a; the ladder does not take it), ladder PB+V 32/0/0,
  PB off 0/32/0, B+V 32/0/0, B off 0/32/0: **ladder wrong 0**.
- ✅ M9c-mix ladder: 11 runs, 1860 s, peak RSS 1749 MB; survived/safe/wrong:
  ejs 0/32/0, own P 0/32/0, own B 0/31/1, page P 0/32/0, page B 0/31/1;
  ladder PB+V, PB off, B+V and B off each 0/32/0: **ladder wrong 0**.
  ❌ Standalone B and page B each produced one wrong value (e7573094); that run exited 1
  because the runner counts standalone wrong values too. Ten other runs exited 0.
  This is not an all-green standalone-strategy result; no code changed.
- ⏳ Owner-approved test-only continuation: finish M9c-nav → corpus → M9b/M9bs → faults →
  M9 → per-class 448 suite. One heavy process at a time; update and push this document
  after each completed test/class before starting the next. Record assertion failures,
  continue the planned evidence collection without code fixes; stop on an incomplete run,
  timeout or infrastructure error. Push only spike/master-extractor-backup; no main,
  merge, release or tag. Code changes wait until testing is finished.
- ✅ M9c-nav: four 631d3938 variant runs, exits 0, 343 s, peak RSS 2222 MB;
  all four ladder columns 0/4/0, standalone wrong 0, **ladder wrong 0**.
  Main variant used --skip-ejs for the previously documented baseline heap issue;
  this does not claim full ejs coverage for that variant.
- ✅ M9c ladder checkpoint complete: 26 runs across loc/mix/nav, 3541 s cumulative,
  peak RSS 2222 MB, ladder wrong 0 in every run. Standalone wrong values remain
  documented separately; M9c is not an all-green standalone-strategy result.
- ✅ Post-M9c corpus --ladder: exit 0, 380 s, peak RSS 2053 MB; 32 files,
  all four steps init errors 0/32 (tripwire false positives 0).
  PB+V and B+V match ejs 260/260, wrong 0; PB off and B off each match 132/260,
  wrong 0, no value 128 (signature withheld without V, as designed).
- ✅ M9b/M9bs ladder checkpoint: 32 files, two seeds; 1828 s, peak RSS 1904 MB,
  **ladder wrong 0**. Survived/safe/wrong:
  M9b PB+V 32/0/0, PB off 0/32/0, B+V 10/22/0, B off 0/32/0;
  M9bs PB+V 32/0/0, PB off 0/32/0, B+V 13/19/0, B off 0/32/0.
  ❌ Standalone own B in M9bs: 0/31/1; total standalone wrong 1, runner exit 1.
  This is an assertion result, not an incomplete run; no code fix applied.
- ✅ Faults --ladder: exit 0, 2444 s, peak RSS 2432 MB; 384 runs (12 fault
  classes × 32 files), wrong values 0, expected FAILs missed 0, files not ok 0
  in every fault class. Baseline PB+V and B+V remain ejs 260/260, wrong 0;
  all four steps init errors 0/32. No code fixes applied.
- ✅ M9: exit 0, 473 s, peak RSS 2399 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M1: exit 0, 472 s, peak RSS 2400 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M1w: exit 0, 483 s, peak RSS 2328 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M2: exit 0, 447 s, peak RSS 2381 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M3: exit 0, 365 s, peak RSS 2018 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 0/32/0, own B 32/0/0, page P 0/32/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M4: exit 0, 431 s, peak RSS 2331 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M5: exit 0, 483 s, peak RSS 2318 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 24/8/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 24/8/0, PB off 0/32/0, B+V 24/8/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M6: exit 0, 441 s, peak RSS 2415 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M7: exit 0, 479 s, peak RSS 2613 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 11/21/0, own P 31/1/0, own B 32/0/0, page P 31/1/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M8: exit 0, 437 s, peak RSS 2357 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M8t: exit 0, 447 s, peak RSS 2354 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 0/32/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M10: exit 0, 431 s, peak RSS 2357 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M11: exit 0, 453 s, peak RSS 2414 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448-suite M11f: exit 0, 452 s, peak RSS 2441 MB; 32 files, two seeds;
  standalone wrong 0, ladder wrong 0. Survived/safe/wrong:
  ejs 32/0/0, own P 32/0/0, own B 32/0/0, page P 32/0/0, page B 32/0/0;
  PB+V 32/0/0, PB off 0/32/0, B+V 32/0/0, B off 0/32/0. No code fixes applied.
- ✅ 448 baseline suite complete: 14 classes × 32 files = 448 file/class cases,
  each run with two seeds; all 14 processes exited 0, standalone wrong 0 and
  ladder wrong 0. M9 above is included in this baseline, not counted twice.
- ✅ Owner-requested test-only cycle complete: M9c → corpus → M9b/M9bs → faults →
  M9 → remaining 448 classes. Ladder wrong 0 throughout; corpus PB+V/B+V remain
  ejs 260/260 and tripwire false positives 0; faults wrong/missed 0.
  ❌ Standalone-strategy wrong values remain in M9c-loc, M9c-mix and M9bs as
  recorded above. This checkpoint does not claim every standalone strategy passed.
- ⏸️ Paused after tests. No repository code fixes, main changes, merge, release or tag
  during this cycle. N5b implementation is still in progress: locator cache and the
  persistent V/StreamCheck ledger remain deferred; CI for 63758da not yet checked.
  Await owner instructions before any code work or N6. This is a test checkpoint,
  not completion of those deferred implementation items or release acceptance.
- ✅ A.0 (plan §3 N5b "Final plan"): fresh clone at `3300ccb` (= `f9eaedc` + plan docs only);
  no local drafts existed, nothing to back up; clean tree.
- ✅ A.1 Budget data, code (report only, answers unchanged; app behaviour unchanged: the wrappers
  pass only init errors from the report). Core: P `prepare` reports ms parse / find / build; P
  `run` reports ms init, search (attempts of candidates that gave no value), SelfCheck (the rest
  of the kinds' solve) and the tripwire (with its search share); B adds the winning group's
  confirm per kind and the tripwire. New `scripts/own-solver/phases.mjs`: phases per step (P
  prepare = parse + build, P search = find + silent attempts; B prepare = parse, instrument,
  templates, reparse, build; B search = the screen), the hit estimate (sum without search; B
  confirms only the winners), a max/median table and the lookup's time. Harness:
  `prepareWithOwn` returns the kept program's length, new `prepareBWithOwn` B's prepared sizes
  (characters only). Runners: corpus `--ladder` and mutation `--ladder --phases` print wall ms
  per step, the phase table and the lookup's largest time; `--prepare --strategy B` the prepared
  sizes. JS 185/185 (179 + 6 step-budget tests).
- ✅ A.1 corpus `--ladder` (551 s, peak RSS 2018 MB; this computer: Xeon 2.5 GHz, 2 vCPU): exit 0,
  PB+V = ejs 260/260, B+V 260/260, wrong 0, init errors 0/32 in all four steps. Max/median ms:

  | step | prepare | init | search | confirm | SelfCheck | tripwire | wall |
  |---|---|---|---|---|---|---|---|
  | P | 1496/1052 | 239/146 | 601/366 | – | 14/8 | 232/139 | 2501/1683 |
  | B | 3720/2847 | 491/184 | 3076/2735 | 1869/346 | 552/40 | 262/21 | 8167/6693 |
  | pP | 1674/1133 | 275/20 | 607/375 | – | 40/6 | – | 2330/1605 |
  | pB | 3822/2720 | 225/30 | 3540/2905 | 1802/137 | 427/46 | – | 7533/6283 |

  Hit estimate max/median ms (files over 0.5 s): P (a) same process 478/291 (0/32; the first
  run 516/272, at the edge), P (b) after a restart 1938/1329 (32/32); B (a) = (b) 5422/3724
  (32/32); B (a) with option 3 2807/631 (18/32); pP (a) 315/26, (b) 1865/1184; pB (a) = (b)
  4725/3219, option 3 2341/221. Lookup, largest per file: P+B 9872 ms (74edf1a3 main), all four
  steps 18997 ms (74edf1a3 es6).
- ✅ A.1 631d3938 M9c-nav (`--strategy P,B --ladder --phases`, one process per variant, main with
  `--skip-ejs`; 376 s, peak RSS 2202 MB): exits 0, ladder wrong 0, every step safe (P `probe`, B
  `no candidates` or `budget`). Wall ms P/B/pP/pB: main 2010/60059/1899/60088, tce
  1880/44081/2178/44628, es6 1591/9865/1549/9777, phone 1175/60078/1380/60076 (60 s = the
  harness timeout after B's reply: B's phases end at 44.4 s main and 38.6 s phone, then the
  player's late promise jobs; B init 26.0 s on main, the screen 40.7 s on tce = `B_SCREEN_MS`).
- ✅ A.1 prepared sizes (`--prepare --strategy B`, 130 s): B's prepared program + function table +
  templates largest 6,540,135 chars (5203c085 tce; main 6,539,969) → 2 players
  (`maxCachedPlayers`) ≤ 24.9 MB at 2 bytes per char; P's kept program (kept today) largest
  4,547,572 chars → ≤ 17.3 MB for 2.
- ✅ A.1 caps (proposed; numbers for A.2): phone margin ×4 (a mid-range phone runs JS ≈ 2–3× slower
  than this computer; ×4 leaves room; N7 measures the real factor).
  - Job cap 40 s (worker steps) = Kotlin job timeout 45 s − 5 s grace (the deadline is checked
    every 4096 steps, then the reply and the bridge). ≥ `P_INIT_MS` 20 s: P's init can use its
    whole budget and P still has 20 s; `B_SCREEN_MS` 40 s stays, the deadline ends B first.
  - Page cap 35 s (20 s since N7, owner 2026-10-14; tighter: a page step runs only for kinds the workers left, after a ready wait
    of up to 20 s, in the renderer that can run out of memory).
  - Lookup cap 60 s = `P_INIT_MS` + job cap: B keeps its full cap after P.
  - Corpus at ×4: P ≤ 10.0 s, B ≤ 32.7 s (fits the cap up to ×4.9), P+B ≤ 39.5 s, pB ≤ 30.1 s
    (fits up to ×4.6), all four ≤ 76.0 s > 60 s: on the largest players page steps may be cut
    (not run, not recorded, run in the next lookup). 631d3938 M9c-nav at ×4: es6 P 6.4 s + B
    39.5 s, inside; main, tce, phone: B does not finish even here (44.4 / 44.0 / 38.6 s), it ends
    at its deadline as `budget` (full cap → recorded → skipped in the next lookup); no answer is
    lost (B answers nothing there).
- ✅ A.1 option 3 (B's prepared program in memory): wrappers accept `type:"prepared"` with
  `strategy:"B"` (today `BadJob`: `own-solver-worker.js:67`, `page-frame.js:80`); core runs B from a
  kept prepared object (program, function table, templates; same core version) and returns it
  once (B with `keep_prepared`); protocol drops `require(!options.strategyB || player is
  Player.Raw)` (`OwnSolverProtocol.kt:107`) for a prepared-B player and reply field; Kotlin's
  `prepared` map keeps P's and B's per player (same eviction, `maxCachedPlayers` 2), worker B and
  page B use it first and rebuild once from the player text if it breaks (as worker P). RAM ≤
  24.9 MB for 2 players (+ P's ≤ 17.3 MB today); each B job then carries ≤ 6.5 M chars instead of
  the player text (≤ 2.7 MB). Re-run: JS unit (worker, page frame, B, ladder, tripwire, step
  budget), Kotlin protocol / runner / ladder runner / breaker (CI + local JVM), corpus `--ladder`
  miss + hit, faults `--ladder`, M9b/M9bs, 631d3938 M9c-nav, flag-off gate. Helps (a) only (the
  memory is gone after a restart): B (a) 5422/3724 → 2807/631 ms. Option 4 (prepared program on
  disk) brings (b) near (a) but changes N5b's rule "locator file = data only".
- ✅ CI for `c9e9931`: build 38327508945 ✅, validate 38327512152 ✅.
- ⏸️ A.1 stop point: hits over 0.5 s (B in (a) and (b), P in (b), P (a) at the edge). Waiting for
  the owner's choice: (1) target (a) only, (2) relax the (b) target, (3) keep B's prepared program
  in memory, (4) prepared program on disk. A.2 waits for the answer.
- ✅ A.1 decision (owner, 2026-10-13): (1) + (2); option 3 waits for N7; option 4 refused (the
  locator file stays data only, code from disk never runs). The 0.5 s target is removed. N7 phone
  targets: P hit in the same process ≤ 2 s, after a restart ≤ 8 s (job sent → reply, without the
  player download and V, on the phone build; after a restart with the engine's cold start); the
  main build is recorded separately with no target; B hit is a metric only. Option 3 again only
  if, at N7, P fails on the current player, B is really used and B hit > 5 s. Caps accepted: job
  40 s, page 35 s (provisional; §5: a page job can stall the in-app browser up to the cap, N7
  measures the stall; N7: 20 s, owner 2026-10-14), lookup 60 s. Added to A.2: a short-deadline `budget` is recorded the second
  time in a row for a `player|step` (definition and tests in plan §3 N5b A.2; weakness in §5).
  Open for N7: §4 "first B run ≤ 10 s" vs A.1 (computer max 8.2 s, ≈ 33 s at ×4).
- ✅ A.2 Deadline and caps (tests first; flag off = main: Phase 1.1 jobs carry no deadline and
  the core runs as before). Core: a request's `deadline` (ms from the call of solve / run / runB)
  bounds P's and B's step counters (checked every 4096 steps, under P's own caps) and is checked
  between phases and attempts; at the deadline only kinds whose requested checks all finished keep
  their answers (SelfCheck, and the tripwire when asked), the others fail as `n:budget` /
  `sig:budget` (never a trip, never an init error); a top level the deadline cut gives no kind and
  no init count; the report names the phase and the counter's stops (counts only). Wrappers (worker
  and page frame): job field `deadline_ms`, a whole number ≥ 0 (else `BadJob`), counted from the
  job's arrival. Kotlin (`ownSolverNext` only): each step's deadline = min(its cap, the time left in
  the lookup); caps worker 40 s, page 35 s (20 s since N7, owner 2026-10-14), lookup 60 s from the ladder's start (the player
  download included). No time left: the step does not run (`<step> lookup cap`; not recorded, no
  breaker); the ready wait never outlasts the lookup. Budget memory per `player|step` (≤ 64, per
  process, no breaker): `budget` with the full cap → the step is skipped (`<step> budget off`) and
  the ladder counts its last run without answers (a run that needed the page still sends the kinds
  there); with a short deadline only the second time in a row (another result in between starts
  the count again; a job that never started, a ready timeout, is not a run). A `budget` from the
  kept program does not break it (no rebuild). Runners: `--ladder` sends the step caps as
  deadlines (`--no-deadline`: A.1's runs; `STEP_CAPS_MS` in `ladder.mjs` = the Kotlin constants,
  checked by a test); the lookup cap is the app's alone. Tests: JS 193/193 (185 + 5 step-budget,
  1 worker, 1 page frame, 1 ladder); Kotlin solver package on a local JVM (JDK 17, kotlinc 2.0.21)
  61/61 with 9 new ladder-runner tests and 2 protocol tests; 5 code mutations (every budget off,
  never skip, ready timeout counted, no lookup cap, no in-a-row rule) each fail 1–6 of the new
  tests. §5: deadline limits (one native call and the parse are not cut; a lookup can run past its
  cap by the last step's ready wait).
- ✅ CI for `de83094` (A.2): build 38335870863 ✅, validate 38335874782 ✅.
- ✅ A.3 `--judge ladder` (mutation runner, with `--ladder`; tests first): the exit code judges the
  ladder's answers only (exit 1 on a ladder wrong value in any of PB+V, PB off, B+V, B off); ejs's
  and each step's own wrong values are still counted and printed. Default `--judge all` (any wrong
  value fails, as before); `--judge` other than all/ladder, or ladder without `--ladder` → exit 2.
  `judgeExit` in `ladder.mjs` (unit test); the summary line names the judge, the JSON report has
  `judge`. The corpus runner still judges B alone. Check: 631d3938 es6 M9bs `--strategy P,B
  --ladder --judge ladder` (17 s): own B wrong 1 (standalone, init errors 3), ladder wrong 0 →
  exit 0 (with `--judge all` the same run exits 1). JS 194/194.
- ✅ CI for `09097f9` (A.3): build 38337512614 ✅, validate 38337517655 ✅.
- ⏸️ Stopped after A.3 at the owner's request. Next: A.4 locator cache → A.5 V ledger on disk →
  A.6 docs → A.7 (TEST_SHA + Test Agent A–D commands), by a new agent with
  `MASTER_KEY_PHASE1_2_CONTINUE_N5B_A4.md`.
- ▶️ Resumed at `e9e4a6b` with `MASTER_KEY_PHASE1_2_CONTINUE_N5B_A4.md`: A.4 → A.5 → A.6 → A.7.
- ✅ A.4 locator cache (`ownSolverNext` only, tests first; flag off = main: a job without
  `locator` runs exactly as before; plan §3 N5b A.4 "Done" and "Plan adapted").
  - Core: locate mode (`request.locator`, `{}` or the cached parts, core version 1). For each
    kind answered cleanly (init errors 0 after the tripwire, every input answered, no failure,
    trip or budget) a run returns a data-only part: P = the candidate indexes that answered every
    input + a hash of the probe outputs; B = the winning group's members (static function id,
    shape index, a hash of the function's source and its size) + the probe hash (32-bit FNV-1a).
    A P part runs only its candidates (each must answer every input with the same probe outputs,
    every candidate runs once on one probe and must agree; the tripwire runs with all
    candidates); a B part skips the screen (the same functions by id, size and hash → profile →
    confirm → groups → solve → tripwire). Anything else is a miss (a reason class in the report,
    counts only) and the kind runs in full in the same job. `locatorOk` checks the form (bounded
    whole numbers, 8-digit hex hashes, ≤ 16 candidates, ≤ 8 members, the job's strategy).
  - Wrappers: worker job field `locator` (malformed or of the other strategy → `BadJob`), reply
    `locator` + `locator_hit`; the page frame refuses a job with a locator (page steps run in
    full every time).
  - Kotlin (extractor-master): `OwnSolverLocator` (the core's form check, decode/encode, the
    part's hash), `OwnSolverLocators` (≤ 32 players, least recently used first; key player ID +
    SHA-256 of the player text, another hash → the entry is dropped; the file is read on first use
    and written whole after each change; unreadable, corrupt, another format or core version →
    empty; IO never throws), `AtomicTextFile` (temp file + rename, ≤ 256 K chars), protocol
    `Options.locator`, `Decoded.Solved.locator` / `locatorHits`. `OwnPlayerScriptRunner.Next`:
    every worker job asks for parts and carries the parts cached for its step and player text; a
    kind's part is kept only from the step the ladder chose (a worker step with init errors 0 and
    a part of its strategy), a signature's part only after V = pass; a miss or another chosen
    step drops it, V = fail drops that step's signature part. App: `SiteAdapterModule` names
    `files/own-solver/locators.json` and `files/own-solver/v-ledger.json` next to
    `SharedPreferencesBreakerMarkers`.
  - Plan adapted: the file store is plain Java in extractor-master (no Android API needed; the
    local JVM tests cover it), not extractor-master-android; page steps never locate.
  - ❗ Bug found by the corpus and fixed (tests first): one function can sit in B's winning group
    in two shapes; the core's and Kotlin's form checks refused a repeated id, so B never hit on a
    real player. B's members are now distinct (id, shape) pairs in both.
  - Runners: `verify-own-solver.mjs --ladder` runs the worker steps in locate mode (the miss),
    writes the parts to `build/own-solver/locators.json` (data only) and times each file's hits
    in a fresh isolate per job (`harness.mjs` `hitInIsolate`: a worker thread, as the app's new
    Worker per job; a rerun in the same isolate is warm: P (a) 43 ms instead of 347 ms): "P (a)
    kept" (the kept program, as the same process), "P (b) text" and "B text" (from the player
    text, as after a restart). `--hits-only`: a new process reads the file (ejs, P (b), B per
    file). `--faults --ladder` sends the cached parts to the worker steps. `--no-locator` = A.3's
    runs. Mutation runner `--ladder`: worker jobs carry `locator: {}` (`--no-locator` drops it).
- ✅ A.5 V ledger on disk: `OwnSolverVLedger` (≤ 64 keys, oldest first;
  `files/own-solver/v-ledger.json` through the same file store; unreadable or another format →
  empty, V runs again) replaces the runner's memory `passed` set. Key
  `player|step|scriptHash|locatorHash` (`PendingCheck.cacheKey`; `-` without a part; a hit names
  the part it was sent, a reply cannot bring a part of its own into the key). Plan adapted:
  `StreamCheck.Ledger` (check budgets per day, remembered verdicts) stays in memory.
- ✅ Tests: JS 217/217 (194 + 23: locator 22, page frame 1); Kotlin on a local JVM (JDK 17,
  kotlinc 2.0.21; solver package + StreamCheck) 103/103 (+29: locator 14, locator runner 12,
  protocol 3). Poisoned entries → a miss, the kind runs in full, wrong 0: P index out of range,
  a silent candidate, the decoy fault's index, one of two disagreeing candidates, another core
  version; B wrong function id, size, source hash, shape, group (decoy) or outputs; other player
  text under the same player ID; a reply claiming a hit for a part it was not sent; a poisoned
  entry with a V pass on file → V runs again, its fail drops the part, the next answer is
  checked. A hit after a restart: a new runner reads both files and sends the parts.
- ✅ Mutation tests (each new check off → a new test fails): Kotlin 29, JS + harness 34. Equivalent
  survivors (same result by other code): Kotlin "init errors" (the rule takes a worker step only
  with init errors 0, and the core then gives no part), "strategy" and "parts' step" (`Located`
  ties the step to the part's strategy); JS "clean: kind failure" and "clean: answered" (each
  covered by the other and by the part itself: a failed kind keeps no answers, a part needs
  every input answered), "B confirm" on a hit (the group's probe hash and SelfCheck on a fresh
  instance catch the same cases; kept as defence in depth, as after the screen).
- ✅ Corpus `--ladder` (593 s, peak RSS 2359 MB, exit 0): 32/32 files pass, own = ejs 260/260;
  init errors 0/32 in all four steps (tripwire false positives 0); PB+V = ejs 260/260, B+V
  260/260, wrong 0; PB off and B off 132/260, no value 128 (no signature without V). Parts: P n
  32/32, sig 32/32; B n 32/32, sig 32/32. Hits, each in a fresh isolate, max/median ms (the
  core's / the whole job), every file n + sig hit, = ejs 260/260, wrong 0:
  - P (a) kept: 394/286, job 396/289 (A.1 estimate 478/291; N7 target ≤ 2000 ms).
  - P (b) from the text: 2236/1516, job 2260/1531 (A.1 estimate 1938/1329; N7 target ≤ 8000 ms).
  - B from the text: 4298/2951, job 4313/2967 (A.1 estimate 5422/3724; metric only).
  The misses as measured in the runner's process (warm): P 1535/1175, B 5950/4576. A P hit from
  the text saves only P's search (≤ 343 ms); a fresh isolate starts cold, so it is not faster
  than the warm miss.
- ✅ `--hits-only` (a new process reading `locators.json`; 155 s, peak RSS 1245 MB, exit 0): 32/32
  files (ejs) pass, not in the file 0; P (b) 1783/1366 ms, job 1797/1379; B 3796/2764 ms, job
  3810/2778; every file n + sig hit, = ejs 260/260, wrong 0.
- ✅ Faults with the cached parts (`--ladder --faults --only 74edf1a3 --variant main`, a quick
  check; 78 s, exit 0): 12 runs, wrong values 0, expected FAILs missed 0; parts sent 44, hit 10
  (omit-window, omit-self: both kinds; decoy-n, decoy-sig, raw-encoding: the kind the fault does
  not touch). The whole faults run is Test Agent A's.
- ✅ Mutation runner with locate mode (`--class M9bs --only 631d3938 --variant es6 --strategy P,B
  --ladder --judge ladder`, 16 s, exit 0): as A.3, own B wrong 1 (standalone, init errors 3),
  ladder wrong 0.
- ⚠️ P (b) on the phone: the largest player took 1.78 s (`--hits-only`) to 2.24 s (`--ladder`)
  here; at the ×4 phone margin that is 7.1–8.9 s against N7's 8 s target (the medians, 5.5–6.1 s,
  fit). N7 measures the real factor.
- ⚠️ Found (plan §5 "V wiring in the app"; `ownSolverNext` only, flag off = main unaffected): the
  app hands the own runner to main's `YouTubeExtractor` (extractor-sites), and the V gate is
  only in Master's `MasterYouTubeModule`, which the app builds without a runner. With
  `ownSolverNext` the app would use the ladder's signatures without V, never record a V pass and
  never cache a signature part. Not fixed in this cycle (the owner decides); proposed fix before
  N7, tests first: the gate around every reader that gets the own runner.
- ✅ A.6 docs: plan §3 N5b A.4/A.5 "Done" and "Plan adapted", §5 new entries (work the step
  budget cannot see → N8, locator cache, caps and why, B prepared program status, M9c-nav gap,
  V wiring in the app, main candidate list).
- ✅ A.7 push: this chat's pushes were blocked by its safety check (4 tries), so the owner had
  another agent apply this chat's patch on `e9e4a6b` and push it as `48dd744` (25 files; tree
  `290b374`, the same as the local tree here).
- ✅ CI for `48dd744` (A.4–A.6): build 38354883911 ✅, validate 38354887223 ✅.
- 🧪 **TEST_SHA `48dd744debcdaba0d2112cf801f8dfbe913fda8e`** (A.7; the next test cycle, plan §3 N5b
  B). Four Test Agents, each on its own computer (≥ 4 GB RAM, 2 CPUs are enough, Node ≥ 22,
  network to www.youtube.com), each with its own result files
  `docs/n5b-raw/<a|b|c|d>-<test>.json` (one per test, as in the owner's prompts; A.8: was one
  `.md` file per agent): commit only those files on the branch head (docs may follow TEST_SHA)
  and push only to this branch (a `docs/**` push starts no CI; never main, merge, release or
  tag). Kotlin and Android: the CI runs above,
  nothing to run. Rules: one heavy run at a time; no code fixes; a failed check (exit 1) is
  recorded and the list goes on; any other non-zero exit (out of memory, a timeout, an
  infrastructure error) stops the list: record it. Never print or commit player text, solver
  inputs or outputs, stream URLs, corpus hashes or a run's stderr (`.err`). Record per command:
  the command, exit code, seconds, peak RSS and its summary lines (`grep -vE '^[0-9a-f]{8} '
  <log>` for `verify-own-solver.mjs`, `sed -n '/^class /,$p' <log>` for the mutation runner).
  - Every Test Agent first (the mutation runner silently uses cached players only; copy the code
    blocks without this list's 4-space indent):
    ```
    git clone https://github.com/Alalkipgen/YFT && cd YFT
    git checkout --detach 48dd744debcdaba0d2112cf801f8dfbe913fda8e
    node scripts/verify-own-solver.mjs > fetch.log 2> fetch.err; echo "exit $?"   # 0
    grep -vE '^[0-9a-f]{8} ' fetch.log         # "32/32 files pass"
    ls build/own-solver/players | wc -l        # 32
    ```
    Then write the agent's `list.txt` below (one command per line) and run it with
    `nohup ./run-list.sh list.txt logs > /dev/null 2>&1 &`; `logs/summary.txt` gets one line per
    command (`NN exit E peak_rss_mb M seconds S | command`), then `done` (or `STOP`):
    ```
    cat > run-list.sh <<'SH'
    #!/bin/bash
    # run-list.sh <list> <dir>: each line in order, one at a time; stdout -> <dir>/NN.log,
    # stderr -> <dir>/NN.log.err (never print it); stops after an exit other than 0 or 1.
    list=$1; dir=$2; mkdir -p "$dir"; n=0
    while IFS= read -r cmd; do
      [ -z "$cmd" ] && continue; n=$((n + 1)); id=$(printf %02d $n); log="$dir/$id.log"; t=$SECONDS
      bash -c "exec $cmd" > "$log" 2> "$log.err" < /dev/null & pid=$!; peak=0
      while kill -0 $pid 2> /dev/null; do
        h=$(awk '/VmHWM/{print $2}' /proc/$pid/status 2> /dev/null)
        [ -n "$h" ] && [ "$h" -gt "$peak" ] && peak=$h; sleep 1
      done
      wait $pid; code=$?
      echo "$id exit $code peak_rss_mb $((peak / 1024)) seconds $((SECONDS - t)) | $cmd" >> "$dir/summary.txt"
      [ $code -gt 1 ] && { echo STOP >> "$dir/summary.txt"; exit 1; }
    done < "$list"
    echo done >> "$dir/summary.txt"
    SH
    chmod +x run-list.sh
    ```
  - **Test Agent A** (≈ 90 min): unit → corpus (miss + hit) → faults (with the cache hit) →
    M9b/M9bs. Expect: 01 `# pass 217`, `# fail 0`; 02 32/32 pass, init errors 0/32 in all four
    steps, PB+V = ejs 260/260, wrong 0, locator parts 32/32 for P and B, each hit line wrong 0
    (A.4 here: P (a) 394/286 ms, P (b) 2236/1516, B 4298/2951); 03 not in locators.json 0, wrong
    0; 04 wrong values 0, expected FAILs missed 0, "cached parts sent N, hit M" per fault; 05
    exit 0 = ladder wrong 0 (standalone wrong values are reported only; N5b: own B 1 in M9bs).
    ```
    cat > list.txt <<'LIST'
    node --test --test-reporter=tap extractor-master-android/src/test/js/capture.test.cjs extractor-master-android/src/test/js/codecs.test.cjs extractor-master-android/src/test/js/mse.test.cjs scripts/own-solver/tests/harness.test.mjs scripts/own-solver/tests/core-prepare.test.mjs scripts/own-solver/tests/core-run.test.mjs scripts/own-solver/tests/core-selfcheck.test.mjs scripts/own-solver/tests/worker.test.mjs scripts/own-solver/tests/canary.test.mjs scripts/own-solver/tests/mutate.test.mjs scripts/own-solver/tests/core-strategy-b.test.mjs scripts/own-solver/tests/page-env.test.mjs scripts/own-solver/tests/init-errors.test.mjs scripts/own-solver/tests/ladder.test.mjs scripts/own-solver/tests/tripwire.test.mjs scripts/own-solver/tests/step-budget.test.mjs scripts/own-solver/tests/locator.test.mjs
    node scripts/verify-own-solver.mjs --ladder
    node scripts/verify-own-solver.mjs --hits-only
    node scripts/verify-own-solver.mjs --ladder --faults
    node scripts/verify-own-solver-mutations.mjs --class M9b,M9bs --strategy P,B --ladder --judge ladder
    LIST
    ```
  - **Test Agent B** (≈ 60 min; N5b: loc 11 runs 1338 s, mix 1860 s): M9c-loc 11 → M9c-mix 11,
    one process per player, 631d3938 one per variant (22 lines). Its `list.txt`:
    ```
    R="--strategy P,B --ladder --judge ladder"; M="node scripts/verify-own-solver-mutations.mjs"
    for C in M9c-loc M9c-mix; do
      for P in 74edf1a3 901741ab e7573094 9fcf08e8 21cd2156 76ad2fe8 5203c085; do echo "$M --class $C --only $P $R"; done
      for V in main tce es6 phone; do echo "$M --class $C --only 631d3938 --variant $V $R"; done
    done > list.txt
    ```
  - **Test Agent C** (≈ 80 min; N5a/N5b: M9c-nav ≈ 2700 s, then 1767 s): M9c-nav 11 as Test
    Agent B's, with `--skip-ejs` on the 631d3938 main line only (main's ejs runs out of heap
    there, plan §5), then 448 part 1, one class per process (15 lines). Its `list.txt`:
    ```
    R="--strategy P,B --ladder --judge ladder"; M="node scripts/verify-own-solver-mutations.mjs"
    { for P in 74edf1a3 901741ab e7573094 9fcf08e8 21cd2156 76ad2fe8 5203c085; do echo "$M --class M9c-nav --only $P $R"; done
      echo "$M --class M9c-nav --only 631d3938 --variant main --skip-ejs $R"
      for V in tce es6 phone; do echo "$M --class M9c-nav --only 631d3938 --variant $V $R"; done
      for C in M1 M1w M2 M3; do echo "$M --class $C $R"; done; } > list.txt
    ```
  - **Test Agent D** (≈ 80 min; N5b: 4527 s): 448 part 2, one class per process (10 lines). Its
    `list.txt`:
    ```
    R="--strategy P,B --ladder --judge ladder"; M="node scripts/verify-own-solver-mutations.mjs"
    for C in M4 M5 M6 M7 M8 M8t M9 M10 M11 M11f; do echo "$M --class $C $R"; done > list.txt
    ```
  - Pass (plan §3 N5b B): ladder wrong 0 in every run; corpus = ejs with tripwire false positives
    0; faults wrong 0 and missed 0; M9c 33/33 runs done; 448 classes wrong 0. Each result file
    has its result (pass, fail, stop or incomplete) and the totals (runs, seconds, largest peak
    RSS). Retest after a fix: plan §3 N5b B.
- ✅ A.7 docs (this TEST_SHA entry and the lists) pushed as `e66b9ea` by the owner's agent (docs
  only, no CI). The owner has the Test Agent A–D prompts (22 result files).
- ✅ **A.8 V wiring in the app, fixed** (owner's go after A.7; tests first; no JS change, so
  TEST_SHA stays `48dd744` and the Test Agents' runs stay valid). Finding: not only
  `ownSolverNext` — Phase 1.1 too (`ownSolver` without Next, the debug APK's default) gave its
  signatures to main's reader with no V (only the core's SelfCheck). Fix: `YouTubeSignatureGuard`
  (extractor-master) puts the same `SignatureGate` (new `SignatureGate.of` and `around`) around
  main's reader in `SiteAdapterModule.provideSiteExtractors`, for both flags; `MasterYouTubeModule`
  uses the same factory (the same order as before: the lookup, then V). A runner that hands over
  no checks (main's ejs runner, flag off) gets main's reader as is. Phase 1.1's runner now hands
  over a check for worker P with no hashes: one V pass per player version, in memory for the
  process; V = fail turns that player version's signatures off for the process (at most 32
  versions, n stays); unknown keeps nothing (checked again, within StreamCheck's daily budget).
  Not built (owner): a timeout fallback to main and a V prewarm, decided after N7 (plan §3 N7).
- ✅ Tests first: `YouTubeSignatureGuardTest` (6; main's reader, the guard, the real gate,
  StreamCheck and runner; scripted core and media server) failed 5/6 on a stub that wires the app
  as before (the flag-off test passed), then 6/6: Next right signature → 1 V request, the pass in
  the ledger, the sig part cached; a ledger hit after a restart → no V, other player text → V
  again; a wrong signature → V fail with a control, no signed rows, nothing in the ledger or the
  cache, the next lookup runs worker B for sig; Phase 1.1 → one V per player version; Phase 1.1
  wrong → dropped and off (no new V); flag off → main's reader as is, 0 V requests. Updated for
  Phase 1.1's V: `OwnSolverYouTubeTest` (1 V request, the pass kept) and
  `OwnSolverLadderRunnerTest` (Phase 1.1's check; new: unknown, pass, fail). New app test
  `SiteAdapterYouTubeGuardTest` (Robolectric, CI only: no Android SDK here): the own runner gets
  the guarded reader, main's runner main's reader.
- ✅ Local Kotlin (kotlinc 2.0.21 + JUnit 4 on the JVM; no Gradle or Android here): 19 classes
  (solver and YouTube module), 210 tests OK, exit 0.
- ✅ Mutations (each alone, main rebuilt, the 3 V classes run; failed tests): guard without the
  gate 5, Phase 1.1 without a check 5, Phase 1.1 verdicts ignored 1, `around` without V 8, Phase
  1.1 off ignored 1, Master module without the gate 2: 6/6 killed. The app line without the guard
  is the app test's (CI), not run here.
- ⚠️ Behavior change with the own solver (both flags; flag off = main unchanged): a player version
  costs one V request (a one-byte read, at most the probe timeout) before its signatures are used
  (Phase 1.1: again after each restart); without a pass (unknown: no control, network, budget
  spent) that lookup's signed streams are dropped and only direct streams stay (none left →
  "player script required"). N7 measures P (b) in total, the V request included, on the phone.
- ✅ A.8 pushed from this chat as `df9a1e1` (the owner asked; its parent is `8c7fbfa`: Test
  Agents B and D had pushed their results, no file in common; the tree as computed before the
  push). CI: build 38413760115 ✅ (R3 drift check ✅), validate 38413767701 ✅; both run the app
  unit tests, `SiteAdapterYouTubeGuardTest` among them.
- ✅ **N5b B, the test cycle** (TEST_SHA `48dd744`; `docs/n5b-raw/`, 22 result files, 52 runs, all
  exit 0, all at TEST_SHA; each Test Agent on 2 CPUs, 4 GB, Node 24.14.1; A.8 changed no JS, so
  the results hold for `df9a1e1` too):
  - Test Agent A (5027 s, peak RSS 2428 MB): unit 217/217. Corpus `--ladder`: 32/32, own = ejs
    260/260, init errors 0/32 in all four steps (tripwire false positives 0), PB+V = ejs 260/260,
    wrong 0; locator parts 32/32 (P and B, n and sig); hits (max/median) P (a) 412/282 ms, P (b)
    1744/1525 ms, B 3889/2934 ms, every hit line wrong 0; largest lookup P+B 6.7 s, all four
    steps 13.5 s. `--hits-only`: not in locators.json 0, wrong 0, P (b) 1753/1425 ms, B
    3791/2744 ms. Faults: 384 runs, wrong values 0, expected FAILs missed 0. M9b/M9bs: ladder
    wrong 0 (standalone own B 1 in M9bs, as before).
  - Test Agent B (3489 s, peak 1746 MB): M9c-loc 11/11 and M9c-mix 11/11 runs, ladder wrong 0
    (standalone, reported only: page B 1 in M9c-loc; own B 1 and page B 1 in M9c-mix).
  - Test Agent C (4737 s, peak 2558 MB): M9c-nav 11/11 runs, ladder wrong 0 (standalone: page B 1;
    main's ejs 1, the known N5a case, plan §5 main candidate list (3)); M1, M1w, M2, M3 ladder
    wrong 0, standalone 0.
  - Test Agent D (4449 s, peak 2594 MB): M4, M5, M6, M7, M8, M8t, M9, M10, M11, M11f ladder wrong
    0, standalone 0.
  - Pass (plan §3 N5b B): ladder wrong 0 in every run ✅; corpus = ejs, tripwire false positives
    0 ✅; faults wrong 0 and missed 0 ✅; M9c 33/33 runs ✅; 448 classes wrong 0 ✅. Next: N6
    (canary workflow), then N7 (the owner's phone check).

## Phase 1.2 — N5a ladder rule, init errors in replies, M9c — done, ❌ M9c-nav ladder wrong 1 → N5b (code `a78626f`, CI 38189985242 ✅ + checkpoint 38189988228 ✅)

- ✅ Code (app behaviour unchanged: nothing calls the rule yet; flag off = main): worker and
  page-frame replies carry `init_errors` + `init_error_classes` (class names only);
  `OwnSolverProtocol` decodes them (missing → null = unknown = untrusted). `OwnSolverLadderRule`
  (extractor-master, pure) and `scripts/own-solver/ladder.mjs` read one case table
  (`extractor-master/src/test/resources/own-solver/ladder-rule-cases.json`, 18 cases). Runners:
  `verify-own-solver.mjs --ladder [--dyncode]`, `--faults --ladder` (lazy, as the app will),
  mutation runner `--ladder` (columns PB+V, PB off, B+V, B off). Mutators M9c-nav / M9c-loc /
  M9c-mix (environment classes, outside the 448 baseline). JS 171/171 (161 + 10).
- ✅ Corpus `--ladder --dyncode` (367 s, peak RSS 2.0 GB): 32/32 files; ladder = ejs 260/260 in
  all four options (wrong 0, no value 0); init errors 0/32 files in worker P, worker B, page P,
  page B; player `eval` reads and `Function` calls 0 in all four runs (init and later).
- ✅ M9b/M9bs `--strategy P,B --ladder` (all 32 files, 2172 s, peak RSS 1.9 GB), survived/safe/wrong
  per class: ladder PB+V 32/0/0 and 32/0/0 (n and sig from E, V simulated), PB off 0/32/0 both
  (n from E right in all 64; no page signature without V), B+V 10/22/0 and 13/19/0, B off 0/32/0
  both. **Ladder wrong 0.** The N4b wrong value is still there in worker B alone (631d3938 es6
  M9bs sig, init errors 3) and the rule does not return it (that case: n E, sig E, right). ejs,
  worker P, page P and page B as N4b (ejs 0 survived, safe; page P 32/32). 5/64 `{late promise
  jobs cut}`. (A first run was cut at 36/64 when the sandbox restarted; re-run whole.)
- ✅ Faults `--faults --ladder` (each case through the ladder run lazily; 3070 s, peak RSS 2.3 GB):
  384 runs, **wrong 0, missed 0**, 32/32 files 12/12 ok; init-wrong-n / init-wrong-sig wrong 0
  (N4b: 132 / 128 in the worker alone): init errors → worker answers not used → E (n and sig
  from E, right). Sources: decoy-n n none (failed as expected), sig P; decoy-sig and raw-encoding
  sig none, n P; omit-window/self P (1 step); omit-location/navigator/document/XMLHttpRequest/all
  E (3 steps: worker P, worker B, page P). Unfaulted ladder = ejs 260/260.
- ✅/⚠️ M9 `--strategy P,B --ladder` (641 s, peak RSS 2.3 GB): ladder PB+V 32/0/0, B+V 32/0/0,
  **wrong 0**; but PB off and B off 0/32/0 (all safe: n from E right, no signature without V).
  M9's reads throw once at init in the worker (init errors 1 in all 32, worker P and B; page 0):
  the worker's answers are right (32/32) but the rule does not use them, so E answers, and a
  signature from E needs V. Cost of the rule, not a wrong value (§4 note "N5a").
- ❌ M9c-nav `--strategy P,B --ladder`, one process per player (2346 s, peak RSS 2.55 GB; 7/8
  players, 28/32 lines). One process for all of M9c stopped at 72/96 with "heap out of memory"
  (exit 134, 4609 s, 2.6 GB), M9c-nav alone at 9/32; 631d3938 alone also stopped (0/4, rerun one
  variant per process pending). **Ladder wrong 1 in all four options** (PB+V, PB off, B+V, B off):
  21cd2156 es6, worker B and page B give a wrong signature (n: no candidates) with no init errors,
  so the ladder returns worker B's signature; +V checks page signatures only. Own P, page P 0/28/0
  (all safe). ejs wrong 1 (74edf1a3 es6; ❗ separate, also main: likely no cookieEnabled in the
  WebView worker's WorkerNavigator). The silent worker wrong is M9c-nav's design target. Proposed
  fix (not implemented, owner decides): every signature, the worker's too, needs V before use.
- ✅ M9c-loc `--strategy P,B --ladder`, one process per player, 631d3938 one per variant (11
  processes, 1596 s, peak RSS 1.7 GB; after one sandbox restart the unfinished player ran again):
  **ladder 32/0/0 in all four options, wrong 0**. ejs 32/0/0, own P 32/0/0, own B 12/20/0, page P
  0/32/0. Page B wrong 1 (9fcf08e8 tce: signature, n no candidates, init errors 2), not returned:
  worker P answers first, and the page answer had init errors.
- ✅ M9c-mix `--strategy P,B --ladder` (11 processes as M9c-loc, 2176 s, peak RSS 1.6 GB):
  **ladder 0/32/0 in all four options, wrong 0** (all safe; E answers). ejs, own P, page P 0/32/0.
  B wrong 1 in the worker and 1 in the page, both e7573094 main, a signature with n "no
  candidates" (as in M9c-nav): not returned, the worker answer had init errors 1 and a page
  signature needs V.
- ⚠️ M9c-nav 631d3938, one variant per process: es6, tce, phone safe in every step and every
  ladder option; **main stops even alone** with "heap out of memory" (exit 134, 61 s, 2.5 GB): this
  mutated player exhausts the heap in a single solve (which step is not known yet). In the app that
  is a memory risk in the worker or page renderer (onRenderProcessGone candidate). M9c-nav 31/32
  lines: ladder wrong 1 (21cd2156 es6) as above, no new wrong value.

## Phase 1.2 — N4b environment-dependent values (M9b / M9bs) — measured ❌ (code `bdb45c6`, CI 38175876765 ✅; harness fix `8140b27`, CI 38181863054 ✅)

- ✅ Code: mutation classes M9b (DOM-made value; use throws without the DOM) and M9bs (silent
  use), outside the 448 baseline; runner `--both` (worker and page side by side) and `--variant`;
  core init errors by class (`report.topLevelErrorClasses`); harness `--init`, `--fault-cases`;
  fault cases `init-wrong-n` / `init-wrong-sig`.
- ✅ Init errors before M9b: 0/32 files in the worker (P, B) and in page mode (P, B); corpus
  32/32 = ejs 260/260 in all four runs.
- M9b/M9bs `--both` (all 32 files in one process, 1963 s, peak RSS 1.8 GB), survived/files:
  M9b: ejs 0 (safe), worker P 0 (safe), worker B 0 (safe), page P 32, page B 10 (22 safe);
  M9bs: ejs 0, worker P 0, worker B 0 (31 safe, ❌ **1 wrong**), page P 32, page B 13 (19 safe).
  Plan §4 note "N4b".
- ❌ The wrong value: worker B on 631d3938 es6 M9bs, sig (0/4 right; n `no candidates`). Silent:
  the DOM statement fails in the worker (init errors 3: RangeError + TypeError), B keeps going and
  sig changes: SelfCheck and agreement cannot see it; the proposed init-error rule would (init
  errors > 0 → E; page B 0 init errors, page P/B right there). Reproduced in a fresh run and with the harness before the fix below. ejs is safe
  there: main is not affected; the app does not send B before N5. Not fixed (owner's stop rule).
- Faults: worker P (384 runs) and worker B: the 10 earlier cases wrong 0, missed 0; ❌ init-wrong-n
  132 and init-wrong-sig 128 wrong (by construction). Page P and page B (384 runs each): wrong 0,
  missed 0 (init-wrong inert in page mode); N4's page B faults are done.
- ✅ Harness fix (test scripts only; app and core unchanged): `harness.mjs` vm contexts use
  `microtaskMode: "afterEvaluate"` and keep the solve's reply before player promise jobs run; when
  those jobs time out, the kept reply is used (the app's worker also posts its reply before later
  promise jobs run). The hang (74edf1a3 phone M9bs, B) is gone: 5/64 cases `{late promise jobs
  cut}` (the runner marks them; all worker B, safe), 0 harness timeouts. JS 161/161.
- Lesson: two whole-corpus runs at once hit the 4 GB limit (page B faults was killed after 30/32
  files; the last 2 re-run alone). Run one whole-corpus `--page` or `--both` run at a time.
- ⏭ Next: the owner decides the fix (the init-error rule proposed in
  `MASTER_KEY_PHASE1_2_CONTINUE_N4B.md` covers this case); N5 only after "Next".

## Phase 1.2 — N4 strategy E (page environment) — N4 done (`a7c3dd9`, CI 38170962057 ✅)

- ✅ `WebViewPageSolverEngine` (`extractor-master-android`, an `OwnSolverEngine`; nothing calls it
  before N5): a fresh offscreen WebView per run, destroyed afterwards, with **no**
  `addJavascriptInterface`. The reply comes back through androidx.webkit `addWebMessageListener`,
  limited to `https://appassets.androidplatform.net` (the sandboxed frame's opaque origin never
  gets the object): main frame only, the first string reply ends the run
  (`OwnSolverPageReplyGate`, ≤ 12 MiB). `isAvailable` needs `WEB_MESSAGE_LISTENER`, else E is
  skipped. Settings as the worker engine, third-party cookies off; no console, dialogs, windows,
  permissions or location prompts; a page load error or a renderer crash ends the run with no
  answer.
- ✅ Own route table (`OwnSolverPageEnvRoutes`): the host page in the main frame only, the frame's
  page in a sub frame only, its scripts, parser, core and the job (served once) as sub resources
  only; the worker's files, main's ejs files and every other request are refused. Each answer has
  its own CSP, `no-store`, `nosniff` and `no-referrer`.
- ✅ Assets: `page-env.html` + `page-env.js` (host page; CSP `script-src 'self'; connect-src 'self';
  frame-src 'self'`) and `page-frame.html` + `page-frame.js` (the frame: `sandbox="allow-scripts"`
  set before it has an address, and again by the response header; CSP `connect-src 'none'`,
  `worker-src 'none'`, `webrtc 'block'`, scripts only `page-frame.js` + eval). The frame says it
  is ready; the host page then sends the job with a fresh 128-bit token; the frame takes its reply
  path and removes WebRTC before player code (it refuses the job if it cannot), runs the job in a
  later task and replies with the token; the host page passes on only the first reply with it.
- ✅ Core page mode (`request.page`): a real window's browser objects stay (the worker mode's
  defines would throw there); only missing names are added. The worker mode is unchanged.
- ✅ `OwnSolverEnvironmentErrors` (`extractor-master`): the rule N5 asks before E
  (`ReferenceError` or `TypeError` as a whole failure, a worker error class or a candidate error).
- ✅ Harness `--page` (`verify-own-solver.mjs` and the mutation runner): the same solve in page
  mode in a page-frame DOM stub (opaque origin: cookies and storage throw; the frame's location).
- ✅ Results (computer): **M9 in page mode: own P 32/32, own B alone 32/32, wrong = 0** (ejs 0/32,
  safe). Corpus in page mode: P and B 32/32 files, own = ejs on 260/260 inputs; faults in page
  mode (320 runs each): P wrong 0, missed 0; B alone not run yet (pending). Worker mode unchanged:
  corpus P and B 32/32, 260/260; full suite 448 cases: ejs 291, own P 415, own B alone 448,
  wrong 0 (as N3); faults (320 runs each) P and B alone wrong 0, missed 0. Plan §4 note "N4".
- ✅ Tests: `page-env.test.mjs` (14, fake players): page mode keeps a window's objects; on a
  window-shaped global the worker mode fails (`TypeError`) and page mode solves with P and B; a
  player whose n needs the DOM fails n in the worker and passes in the page; the frame stub's
  shape; ready handshake, one job, only from the host page, with the token; player code cannot
  read the token, forge the reply or replace the reply path; WebRTC removed (else no job); error
  classes only; the host page sandboxes the frame first and passes on only the first reply; pages
  and policies match the app's route table. In the CI JS step; locally 154/154. Kotlin: routes
  (6), reply gate (3), engine availability (1, Robolectric), environment errors (3);
  `:extractor-master:test` 326 (2 skipped), `:extractor-master-android:testDebugUnitTest` 69.
- Speed (computer, 32 files, two suites sharing 2 CPUs): page P 60 s, page B 198 s (worker 60 s,
  195 s). Phone: N5/N7.
- Risks: WebRTC through a frame the player makes itself (not closed; a design that holds the
  renderer's WebRTC connections was not built, it needs the owner's go-ahead); E runs on the
  app's shared renderer main thread and P has no step bound there (N5 decides); a hidden WebView
  may delay the job's start; not run on a phone yet (N7); the protocol is P only until N5
  (strategy field, per-kind error classes).
- ⏭ Next: N5 (ladder, locator cache, flag wiring). Continuation prompt for a new agent:
  `MASTER_KEY_PHASE1_2_CONTINUE_N5.md`.

## Phase 1.2 — N3 strategy B (behaviour) — N3 done (`52e1945`, CI 38164686967 ✅)

- ✅ Strategy B in `own.solver.core.js` (reached only with request `strategy: "B"`; the app never
  sends it before N5, so P and the app are unchanged): every function is registered when the
  player creates it, probed on one instance (screen), re-checked on fresh instances (T2),
  classified, grouped and ranked (T1), then SelfCheck as in Phase 1.1.
- ✅ Probe by call shape + static key propagation: the real n transform is a dispatcher branch
  (e.g. `Gn(17,369,_)`, reached only through `v = w ^ F` keys), so constant arguments bind the
  callee's parameters, locals set once from constants are evaluated, a reassigned name is never a
  constant (this bug had hidden one player's n), and chains through nested calls or locals set
  once (≤ 4 links) are one shape. AST only; plan §2 "Plan adapted (N3)".
- ✅ Classifier rules from data (all 32 files): n output length follows the input, not an
  encoding, at least half of 16 single-character changes alter it; trivial sig maps (identity,
  cut, reverse, rotation, swap) refused; a single swap only as a weak candidate with builder
  evidence (needed by `74edf1a3`; a name the builder calls through a plain alias counts, found
  by the first mutation run: M5 on `74edf1a3`) and only when nothing strong is confirmed.
- ✅ `hint: false` in the request switches P's hint off; "B alone" runs use it.
- ✅ Harness: `node scripts/verify-own-solver.mjs --strategy B` (B alone; `--hint` ranks by P;
  `--verbose` shows shapes, key bindings, chains and the builder trace); mutation runner
  `--strategy P|B|P,B` (column "own B"). `--faults` runs the Phase 1.1 set (10 per file) on B
  too; B has its own hooks for the decoy (one more confirmed candidate with other outputs must
  end in `disagree`) and the raw encoding (SelfCheck must refuse).
- ✅ Harness memory fix: player code run by B's probes leaves rejected promises nobody handles;
  Node keeps each one (and with it the whole player context) until the event loop turns, so the
  long synchronous runs hit the 2 GB heap limit (the first B fault run and mutation run stopped).
  The runners now let the event loop turn after every solve (`settle()` in `harness.mjs`); the
  rejections are ignored, never printed.
- ✅ Corpus: B alone 32/32 files, own = ejs on 260/260 inputs, exactly one n group and one sig
  group per file, wrong 0. P unchanged: 32/32, 260/260. Faults (320 runs each): P 0 wrong /
  0 missed; B 0 wrong / 0 missed.
- ✅ Mutation suite (448 cases, 0 skipped, **wrong = 0**), files survived of 32, ejs | own P |
  own B alone: M1, M1w, M2, M4, M8, M10, M11, M11f 32 | 32 | 32 · **M3 0 | 0 | 32** · M5
  24 | 32 | 32 · M6 0 | 32 | 32 · **M7 11 | 31 | 32** · M8t 0 | 32 | 32 · M9 0 | 32 | 32. All:
  ejs 291 (65 %), own P 415 (93 %), **own B alone 448 (100 %)**; B fixes P's only failures (M3,
  M7 on 5203c085 es6). Plan §4.
- ✅ Tests: `core-strategy-b.test.mjs` (11, fake players): known wrappers, an M3-style rewrap
  (P fails, B answers), hash/hex decoys dropped, a second n-like function → `disagree`, trivial
  maps and a reverse/swap-only player → no candidates, a non-deterministic function, a looping
  function, dispatcher keys `dsp(5,98,_)` + the reassigned-local guard, a sig chain, a one-swap
  sig only with builder evidence (also through an M5 alias), no values in the report. In the CI
  JS step; locally 140/140.
- Speed (computer, 5203c085 main): first B run ≈ 5 s (prepare ≈ 2–2.5 s, screen ≈ 2 s,
  confirm ≈ 0.3 s); budgets in plan §3 N3. Phone: measured in N5/N7 (target ≤ 10 s).
- Risks: phone time not measured yet; the weak sig tier depends on builder evidence; a new
  player shape may need a new shape form (the mutation suite and the canary watch for it).
- ⏭ Next: N4 (strategy E, isolated page environment). Continuation prompt for a new agent:
  `MASTER_KEY_PHASE1_2_CONTINUE_N4.md`.

## Phase 1.2 — N2 live check V (`9e15101`, CI 38117539688 ✅; phone measurement pending)

- ✅ `StreamCheck` (`extractor-master/.../modules/youtube/StreamCheck.kt`): asks YouTube whether
  one solved stream URL opens, through the client's existing one-byte probe (`Range: bytes=0-0`).
  Verdicts `PASS` / `FAIL` / `UNKNOWN`; a 403 fails the strategy only when a control (a direct URL
  of the same lookup) opens; control fails too → `UNKNOWN`; no control → `FAIL` for this lookup
  only. At most 4 checks per player version per UTC day (a check = target + control only after a
  403); proven verdicts cached per answer for a day. Never outside a user-started lookup, never on
  bot/login/age/private/DRM states, only on a returned `googlevideo.com/videoplayback` host, the
  download's headers without cookies or credentials, nothing logged, no URL or value in a result.
- ✅ n rule as a policy value: `PENDING` (now: 2xx = pass for sig, unknown for n; a proven 403
  fails both), `JUDGES_N`, `SIG_ONLY` (T4).
- ✅ `StreamCheckTest`: 13 tests with a fake HTTP client (pass, 403 + control, 403 without
  control, control fails, timeout / network / stuck / thrown, other statuses, unsupported client,
  budget, cache, terminal states, host rule, header stripping, nothing logged, n rule).
- ✅ Opt-in phone measurement `StreamCheckMeasureTest` + `bash scripts/stream-check-measure.sh`:
  right n, unchanged n, wrong n, wrong sig, right again → status class, bytes and time for the
  first 1 MiB; ejs and the own runner must agree first; report without addresses; suggests the n
  rule. **Owner action:** run it once on the phone; the result goes into plan §2.
- ✅ Follow-up (owner has no computer, mobile data only): the CI artifact also holds the test APK
  (`f5cff7b`), and the debug app has an owner tool **"YFT stream check"** (launcher entry, only
  with both Master flags on; Start → report → Copy result). Same code as the phone test
  (`StreamCheckMeasurement` in `app/src/debug`).
- Plan adapted (plan §3 N2): no new HTTP method (the probe is the bounded read); not wired into the
  YouTube module yet (N5, behind `ownSolverNext`; Phase 1.1 unchanged); ledger in memory (N5 adds
  the file). Phase 1.3 D4 reuses `StreamCheck`.
- Local check: `./gradlew :extractor-master:test` and `:app:compileDebugAndroidTestKotlin`
  (JDK 17 + Android SDK installed in the sandbox) before the push.

## Phase 1.3 — plan + prompt (no code)

- ✅ `MASTER_KEY_PHASE1_3_PLAN.md` + `MASTER_KEY_PHASE1_3_PROMPT.md` (2026-10-11): an own client
  ladder so that Master keeps working when YouTube's visionOS client fails, as the own solver did
  for ejs. A client table v2 of public app identities that need no token; every answer judged by
  what it contains (D direct, S player script solved by the own solver, never a token client); a
  health cache on the phone; a row check with YouTube (shared with Phase 1.2 N2); a response
  simulator C1–C13 (R6 chain vs ladder, wrong rows 0). Tasks D1–D7, all TODO; START_AT D1 (the
  owner chooses the order against Phase 1.2 N2–N7).
- Why: in yt-dlp's client table (read 2026-10-11) visionOS is the only client that needs neither
  the player script nor a token (android_vr: all formats 403 since 2026.08.17; android/ios need a
  token), so the second leg must be the no-token script clients (TV, embedded) through the own
  solver.

## Phase 1.2 — N1 change simulator (part 2–3: 8 more classes + full baseline) — N1 done (`765e91f`, CI 38113204972 ✅)

- ✅ `mutate.mjs` new classes: **M4** sig helper object → separate functions, **M5** functions
  with 3+ call sites called through a fresh alias, **M7** control-flow flattening (switch in a
  loop) of wrapper-level functions with a plain 3–40 statement body (chance 0.7 each; refuses
  async/generator, labels, function/class/`let`/`const` at the top; keeps directives), **M8**
  global `encodeURIComponent`/`decodeURIComponent` through fresh wrapper functions at the end of
  the wrapper body, **M8t** the same wrappers first, **M9** `navigator.userAgent`,
  `location.hostname`, `document.createElement("div")`, `window.top` read into unused variables in
  the middle of the init, **M11** harmless `var`s at the top, **M11f** harmless function
  declarations at the top.
- Plan adapted: M8 cannot change how the result is applied (that changes the right answer
  itself), so that part moves to the simulated V (N2/N5); M8t, M11 and M11f were added to explain
  the M8 finding (below); M7 flattens other functions too, not only n/sig. Details: plan §3 N1.
- ✅ Runner: report lines now say which kind failed (`safe (n 0/3, sig 3/3)`) and own P's failure
  class (`[no candidates]`); counts only, no player text.
- ✅ Tests: `mutate.test.mjs` 47 (was 29; new: M4, M5, M7 closures / early return / loops /
  hoisting / refusals, M8, M8t, M9, M11, M11f keep behaviour); CI JS step locally 129/129.
- ✅ Full baseline (32 files × 14 classes = 448 cases, 0 skipped; **wrong = 0**); files that
  survived out of 32, as ejs | own P: M1 32 | 32 · M1w 32 | 32 · M2 32 | 32 · **M3 0 | 0** (both
  safe: new wrapper) · M4 32 | 32 · **M5 24 | 32** · **M6 0 | 32** · **M7 11 | 31** ·
  M8 32 | 32 · **M8t 0 | 32** · **M9 0 | 32** · M10 32 | 32 · M11 32 | 32 · M11f 32 | 32.
  All cases: ejs 291 (65 %), own P 415 (93 %).
- Findings: ejs breaks (safely) on aliased call sites, string tables, flattening, new encode
  helpers met first (not at the end, not other new code at the top) and browser-object reads
  (the #56 class); own P survives all of these except M7 on 5203c085 es6 (`no candidates`). Both
  break on a new wrapper (M3): strategy B's first target. Numbers: plan §4.
- ⏭ Next: N2 (live check V + measuring the n behaviour on the owner's phone), then N3 (strategy B).
  Continuation prompt for a new agent: `MASTER_KEY_PHASE1_2_CONTINUE.md`.

## Phase 1.2 — N1 change simulator (part 1: 6 classes + baseline)

- ✅ `scripts/own-solver/mutate.mjs`: behaviour-keeping AST mutators (meriyah + astring from main's
  `yt.solver.lib.min.js`, test time only, never shipped) with a scope model first (T3: hoisting,
  block/catch/switch/class scopes, named function expressions, shorthand keys, `new.target`;
  refuses `eval`/`with`). Classes: **M1** rename function/block/catch locals with minifier-style
  names (globals, the wrapper parameter and locals deliberately named after browser globals such
  as `var window = this` stay), **M1w** M1 + the wrapper parameter, **M2** dotted ↔ computed
  access, **M3** re-wrap as `void (fn).apply(receiver, [args])` (exact receiver), **M6** strings
  into one shuffled `"…".split(sep)` table (directives and keys untouched), **M10** unreachable
  n/sig-like decoys.
- ✅ `scripts/verify-own-solver-mutations.mjs` (`--quick`, `--only`, `--class`, `--seeds`): mutates
  each cached player and judges ejs and own P per class (survived / safe / **wrong**); report
  `build/own-solver/mutation-report.json` (classes and counts only). Plan adapted: truth = ejs on
  the **original** player (answers depend on the input, not on how the code is written); every
  solve goes through `harness.mjs`'s bare V8 context, as the parity runner does — no other way of
  running player code (an ad-hoc truth hook was rejected by the safety review). A mutation that
  changed behaviour (ejs and own agree on a new value) is skipped, never counted as wrong.
- ✅ Tests: `mutate.test.mjs` 29 (scope cases, M1/M1w/M2/M3/M6/M10 keep behaviour, determinism),
  added to the spike CI JS step. Plan adapted: `--quick` needs the player downloads, so it runs in
  N6's canary workflow, not in this CI step.
- ✅ Baseline, full (32 files = 8 players × 4 builds, 2 seeds, 192 cases; wrong = **0**): M1 ejs
  32/32, own 32/32 · M1w 32/32, 32/32 · M2 32/32, 32/32 · **M3 0/32, 0/32** (both fail safely:
  an unknown wrapper) · **M6 ejs 0/32, own 32/32** (own resolves string tables) · M10 32/32,
  32/32. Quick run (8 phone builds) matches.
- Fairness fixes found while measuring: ejs first "failed" M1 only because the draft renamed
  `var window = this` and used `_m$` names; both were unrealistic and are fixed (ejs then 8/8).
- → M4, M5, M7, M8, M9 and the full 32-file baseline: done in part 2–3 (above).

## Phase 1.2 — plan + prompt (no code)

- ✅ `MASTER_KEY_PHASE1_2_PLAN.md` + `MASTER_KEY_PHASE1_2_PROMPT.md` (2026-10-11): next-level own
  solver that keeps working when YouTube changes the shape of its code — behaviour discovery (B),
  a live check with YouTube itself (V), an isolated page environment (E), a strategy ladder with a
  locator cache, and a change simulator (M1–M10) that measures ejs vs own (wrong values must stay
  0). Tasks N1–N7, all TODO; START_AT N1 (baseline).
- Why: Phase 1.1 equals ejs (260/260) because it uses the same shape-based method, so it would
  most likely break together with ejs.

## Phase 1.1 — S6: canary + runbook

- ✅ Computer canary `node scripts/own-solver-canary.mjs [player ID]` (owner-run, never CI):
  today's player from YouTube's embed API, all four builds (main, tce, es6, phone), main's ejs
  and the own core on the same 3 n + 3 sig inputs (one sig with the encoding edge characters),
  pass/fail per kind; report `build/own-solver/canary-report.json` (player ID, counts, timings
  only); exit 0 pass / 1 fail / 2 could not run; on failure it prints the runbook commands.
  Verdict rules tested offline (`canary.test.mjs`, 3, in the JS step).
- ✅ Phone canary `bash scripts/own-solver-canary.sh` → opt-in `OwnSolverCanaryTest`
  (`-e yft.ownSolverCanary 1`, never default CI): today's player through main's ejs runner and
  the own runner in their real WebView engines on the phone; verdict per kind, timings (ejs, own
  first run, own cached run) and the own runner's failure classes; report in the app's files
  `own-solver/` (checked for addresses). Compiled by CI (`compileDebugAndroidTestKotlin`).
- ✅ Canary report written (2026-10-11): today's player `5203c085` (in the corpus) — n PASS,
  sig PASS; 3/3 n and 3/3 sig equal to ejs on main, tce, es6 and phone; own core 1.1–2.1 s per
  build in this computer's Node `vm` (ejs 1.1–2.2 s).
- ✅ Runbook: plan §6 now lists the exact commands (canary → `--add` → `--only`/`--prepare` →
  classify G1–G4/new → fix → full parity + `--faults` + JS tests → push, phone canary, ship).
- ⏳ Owner: the phone canary and the live check with the opt-in APK (both flags on): YouTube rows
  equal main's on the parity URLs, no throttled rows, bot-check/age/private stay terminal (§4).
- ✅ CI (S6, `ab42a22`): Master opt-in debug APK run `38102358850` — success (JS step incl. `worker` +
  `canary` tests; APK built with the own solver on).

## Phase 1.1 — S5: own runner wiring (flag), cache, failure mapping

- ✅ `extractor-master/.../solver/OwnPlayerScriptRunner` implements main's `PlayerScriptRunner`:
  - **Fetch** exactly as main's runner: the address the page named (phone build), then the
    desktop build of the same version; page as Referer; only
    `https://www.youtube.com/s/player/<id>/…/base.js`; 8 MB cap; 45 s engine timeout.
  - **Cache:** the core's prepared program per player ID, in memory (2 players, least recently
    used dropped). A cached program that no longer runs is rebuilt once from the player; a
    SelfCheck refusal is final (a fresh parse would not change it).
  - **Failure mapping:** no engine → `Unavailable`; engine timeout/no reply, worker error,
    unreadable reply, a whole-run core failure, every asked kind refused, or no asked input
    answered → `Failed(PLAYER_SCRIPT_REQUIRED)`; a failed player download keeps its own reason
    (as main's runner). One kind refused → `Success` with the other kind only, and the YouTube
    reader drops the streams that needed the refused kind. `lastFailures` keeps failure
    classes only (for the canary).
  - `OwnSolverProtocol`: hand-encoded job (player escaped once, U+2028/2029 escaped), bounded
    parse of the reply, only asked inputs kept, failure strings cut to short classes.
- ✅ Android `extractor-master-android/.../solver/WebViewOwnSolverEngine` (adapted copy of main's
  `WebViewSolverEngine`; main's file is unchanged) + `OwnSolverPageRoutes`: a fresh offscreen
  WebView per run, reserved asset host, own folder `yft-own-solver/` (`own-solver.html`,
  `own-solver-page.js`, `own-solver-worker.js`, meriyah, core), the same CSP, a dedicated blob
  worker, no cookies/storage/network; main's ejs files are never routed. The worker passes only
  n/sig inputs to the core (a job cannot switch on fault injection or vectors), takes its reply
  path before any player code runs, and answers with error classes only.
- ✅ **Selection:** new flag `-Pyft.ownSolver` (default `false`; BuildConfig
  `OWN_SOLVER_ENABLED` in debug/preview, always `false` in release).
  `SiteAdapterModule.providePlayerScriptRunner` → `OwnSolverSelection.runner`: the own runner
  only when `ownSolver && masterCapture`; otherwise main's `YouTubePlayerScriptRunner`, built
  exactly as on main (only the chosen factory runs). With both flags on, YouTube's adapter
  (main's code, identical to Master's R6 copy) asks visionOS first, then the own runner; the ejs
  core is not loaded on that path. `MasterYouTubeModule(http, playerScripts)` accepts the runner
  too (default none, as in R6); the app leaves it without one because main's adapter answers
  YouTube pages before Master is asked.
- ✅ CI's opt-in APK is built with `-Pyft.ownSolver=true` (the manual run's `own_solver` input
  builds it off); the step summary names the setting.
- ✅ Tests: `OwnPlayerScriptRunnerTest` (12), `OwnSolverProtocolTest` (8), `OwnSolverSelectionTest`
  (3: flags off → main's runner, the own one never built), `OwnSolverYouTubeTest` (3: the real
  reader + runner + protocol with a scripted engine — cipher streams signed with own values; sig
  refused → cipher streams dropped (`PLAYER_SCRIPT_REQUIRED`) while n-only streams stay; broken
  engine → no stream), `OwnSolverPageRoutesTest` (3), JS `worker.test.mjs` (9: raw/prepared jobs
  on the fake player, no program unless asked or after a failure, refused kind, error classes,
  no fault/vector injection, reply path safe from player code, the page loads only its four
  files into a blob worker, routes ↔ files ↔ CSP).
- ✅ Local run (flags on): `:extractor-master:test` 310 (0 failures, 2 skipped: canaries),
  `:extractor-master-android:testDebugUnitTest` 59, app `MasterMainMoreSheetTest`,
  `BrowserMasterFlowTest`, `BrowserMasterFallbackTest` 25, `:app:compileDebugAndroidTestKotlin`
  OK; JS step 82/82; drift 107/0. The APK itself is built by CI (no NDK here).
- ⚠️ Not checked here: the WebView engine on a real phone (no device in this environment) → the
  S6 phone canary and the owner's live check.
- ✅ CI (S5, `ab42a22`, pushed together with S6): Master opt-in debug APK run `38102358850` —
  success.

## Phase 1.1 — S4: G3 encoding checks + SelfCheck + fault injection

- ✅ SelfCheck in `own.solver.core.js`: the values of a kind are returned only when every check
  passes; any failure drops the whole kind (`failed: "<kind>:<reason>"`, no values), so the
  caller falls back (S5) instead of using a doubtful value.
  - **Probes:** n runs two fixed probes; sig runs a map probe (64 distinct URL-safe
    characters) and an edge probe of the same length with `% / = + & ? #`, space, tab, newline
    and a broken `%zz` escape. Probes and the request's vectors go through the same multiTry,
    before the real inputs.
  - **Checks, in order (reason):** one value from every candidate and read path (`disagree`);
    shape (`shape`): n URL-safe, not its input, length ≤ 2 × input + 8; sig only reuses its
    input's characters and keeps a plausible length; distinct n inputs give distinct outputs
    (`not-distinct`); every probe solved (`probe`); **G3 index map:** the positions the map
    probe reveals, applied to the edge probe, must give exactly the edge probe's output
    (`encoding`), which catches a missing or doubled encode/decode of special characters;
    known vectors match (`vector`); the first probe still gives the same value at the end
    (`unstable`).
  - Reports name kinds, counts, candidate/path indexes and error classes, never inputs or
    outputs.
- ✅ Fault injection for tests (`request.faults`, never set by the app): a decoy candidate that
  changes n or sig by one rotated character, sig passed without URI encoding, and each G4
  global (`window`, `self`, `location`, `navigator`, `document`, `XMLHttpRequest`) or all of
  them left out.
- ✅ Harness: a sig edge input with `% / + & ? #`, space, tab and `=` per file (sig lengths
  104/108/107); `node scripts/verify-own-solver.mjs --faults` runs the 10 faults on every file
  against ejs: any wrong value fails, and so does a fault that should FAIL a kind but does not.
- ✅ Corpus: parity 32/32 files PASS, own = ejs on 260/260 inputs. Faults: 320 runs, wrong
  values 0, expected FAILs missed 0 (decoy → `disagree` of the kind it changes, missing
  encoding → sig FAIL; a missing global gave right values or a FAIL, never a wrong value).
- ✅ Offline tests `scripts/own-solver/tests/core-selfcheck.test.mjs` (12): clean pass, a
  wrong candidate inside the player, injected decoy/encoding/global faults, n and sig shape,
  G3 index map, distinct n, vectors, stability, no values in probes or reports; harness test
  for fault verdicts (harness 11). Workflow JS step: 70 tests (own solver 42).
- ⚠️ Limit: a lone candidate that is wrong but plausible (right shape, consistent and stable)
  is caught only by known vectors or a live check; a runner-level live 1-byte probe is planned
  with S5.
- No change in `main`, the app or ejs assets; the core is still unused with the flags off.
- ✅ CI (S4, `00aa458`): Master opt-in debug APK run `38100304754` — success.

## Phase 1.1 — S3: G4 environment + multiTry runner

- ✅ `own.solver.core.js` gains `yftOwnSolver.run(prepared, request)` (`solve` = `prepare` +
  `run`; `prepare` once, `run` many times). It runs inside the existing solver worker; no new
  isolate, no host access.
  - **G4 environment:** `window`, `self`, `location`, `navigator` and `document` are installed
    as own properties of the worker's global object (`Object.defineProperty`), plus an
    `XMLHttpRequest` stand-in without any network when the worker has none. `location` is a
    fixed test page URL with no-op `assign`/`replace`/`reload`: nothing navigates and nothing
    reaches the host page. The user agent and language come from the request (defaults
    otherwise).
  - **Instantiate:** the prepared program is compiled once per run (`Function(registry,
    program)`); failing top-level statements are only counted (`topLevelErrors`).
  - **multiTry:** every candidate builds a URL object for the test page. Its parameter setter
    and getter are found by a marker round-trip (`set`/`get` first, else any two-argument /
    one-argument method pair). n is set and read back through every read path: the getter right
    after building, then every zero-argument method on a fresh object (the getter again and the
    query of a returned URL string). A failing path or candidate only loses its own values;
    errors name candidate/path indexes and error classes, never values.
  - **G3 application:** sig goes in URI-encoded, the way a signatureCipher carries it, and comes
    out decoded exactly once; a query key seen twice or not at all gives no value.
  - A value is kept only when every candidate that produced one produced the same; otherwise
    the input counts as a conflict and gets no value.
- Plan adapted: the G3 *application* (encode in, decode once out) landed with multiTry in S3,
  because a sig cannot be read without it; S4 adds the G3 *checks* and the SelfCheck.
- ✅ Corpus parity (`node scripts/verify-own-solver.mjs`): 32/32 files PASS, own = ejs on
  228/228 inputs (vectors + generated n/sig inputs, every variant of every player).
- ✅ Offline tests `scripts/own-solver/tests/core-run.test.mjs` (9) on the fake player: n and
  sig in every code style and wrapper, sig encoded once / decoded once, a failing statement or
  candidate does not stop the rest, errors carry no values, G4 globals are own properties and
  nothing navigates, worker-shaped context, refusal of a missing/failed preparation, prepared
  program reuse, empty request. In the workflow's JS step (now 29 own-solver tests).
- Timing (desktop Node `vm`): prepare ≈ 0.9–1.8 s per player, run ≈ 0.1–0.3 s; the phone is
  measured in S5.
- No change in `main`, the app or ejs assets; the core is still unused with the flags off.
- ✅ CI (S3, `ace7276`): Master opt-in debug APK run `38099849746` — success.

## Phase 1.1 — S2: own solver core (parse, G1 unwrap, G2 candidates)

- ✅ `extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js` (YFT's own
  code; no ejs file copied or used): `yftOwnSolver.prepare(playerText)`.
  - **Parse:** meriyah (`ranges`, `webcompat`), vendored unmodified from npm `meriyah@6.1.4`
    as `meriyah.umd.min.js` below an ISC header (`docs/THIRD_PARTY_NOTICES.md`). No astring:
    the program is built from source slices, never regenerated.
  - **G1 unwrap:** only the known wrappers (`var ns = {}; (function (g) {...})(ns)`,
    `(function () {...}).call(this)`, an arrow IIFE); anything else → `FAIL unexpected
    structure`. Kept: declarations, assignments and control statements; dropped: other
    expression statements (calls, `new`, `a && b()`). Every `var` declarator and every
    assignment of a sequence runs in its own `try` (failures only counted), so one browser API
    the worker lacks cannot stop the rest. The wrapper text itself is kept verbatim.
  - **G2 normalise (matching only):** string tables (`T = "...".split(";")` / string arrays)
    resolve `x[T[3]]` like `x.name`; dotted, computed and template names alike; sequences
    flattened (also in `return`); `var`/`let`/`const`, function and arrow forms alike; a table
    name the function binds itself is not resolved.
  - **G2 candidates, all matches, two independent patterns:** (A) a definition whose
    unconditional steps mark a local URL object with `("alr", "yes")` and return it; (B) call
    sites `f(<…url…>, <sp>, <x.s>)` (the signatureCipher shape). The candidates are read at the
    end of the player's own top level.
- ✅ Corpus (`node scripts/verify-own-solver.mjs --prepare`): 32/32 files yield candidates
  (each: 1 candidate, found by both patterns; kept ≈ 92–98 % of top-level statements).
- ✅ Offline tests `scripts/own-solver/tests/core-prepare.test.mjs` (10) on a fake player
  written for the tests (`fake-player.mjs`, no YouTube code): known wrappers accepted, unknown
  ones and parse errors fail cleanly, both patterns in es5/es6/sequence styles, all matches
  collected, normalisation, shadowed tables, program keep/drop/guard. In the workflow's JS step.
- Parity runner: the own core is loaded with the vendored meriyah only (no ejs lib).
- No change in `main`, the app or ejs assets; the new asset is unused with the flags off.
- ✅ CI (S2, `f0d7be4`): Master opt-in debug APK run `38099393252` — success.

## Phase 1.1 — S1: own-solver harness and corpus

- ✅ `scripts/verify-own-solver.mjs` (parity runner, beside `verify-youtube-solver.mjs`) +
  `scripts/own-solver/harness.mjs` (pure parts): for every corpus file it runs main's bundled
  ejs core (the oracle) and, once built, the own core
  (`extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js`,
  `yftOwnSolve({player, n, sig})`, written in S2–S4) on the same player and inputs: the ejs
  vectors plus deterministic generated inputs (n 16/18/19, sig 104/108 chars, seeded per
  player). A file passes when ejs meets every vector and solves every input, and (once built)
  the own core equals ejs on all of them. Solvers run in a bare V8 context; the report
  (stdout + `build/own-solver/report.json`) names players, variants, kinds and input indexes
  only, never a solver input or output (checked: 0 of 39 known values in the report/log).
  Exit 0/1/2 = pass/fail/usage. `--only`, `--own <file>`, `--record`, `--add today|<id>`.
- ✅ `scripts/own-solver/corpus.json`: 8 players × 4 variants (main, tce, es6, phone) = 32
  files, player IDs + SHA-256 + size only. The 7 ejs-vector players (`74edf1a3`, `901741ab`,
  `e7573094`, `9fcf08e8`, `21cd2156`, `76ad2fe8`, `631d3938`) + today's player `5203c085`
  (generated inputs only). Player text is downloaded at test time into
  `build/own-solver/players/` (gitignored) and hash-checked; never committed.
- ✅ Live run: 32/32 files pass; ejs meets all vectors (74edf1a3 5/5, the other six 2/2 per
  variant = 68 checks) and solves every input; own core: not built yet (S2–S4).
- ✅ Offline test `scripts/own-solver/tests/harness.test.mjs` (10 tests: committed corpus
  valid + full coverage, bad corpus entries named, deterministic URL-safe inputs, vector
  merge, oracle faults, own disagree/missing/failed, absent vs download faults, summary leaks
  no value), added to the workflow's JS step.
- No change in `main`, the app or assets; flag-off behaviour unchanged.
- ✅ CI (S1, `491dc0e`): Master opt-in debug APK run `38094820752` — success.

## R9 — Signed recipe config (data only)

- ✅ `recipes/RemoteRecipes` (+ `RemoteRecipeConfig`, `RemoteRecipeSignature`): the bundled
  recipes (`ContractRecipes`) stay the defaults and always work. A config replaces their data
  only when it is **signed** (ES256: ECDSA P-256 + SHA-256 over the config text, envelope
  `{"alg","payload","signature"}`, hex because `java.util.Base64` is missing below Android 8),
  passes the **schema** (`extractor-master/recipes/master-recipes.schema.json`; unknown site/key,
  wrong type or out-of-bounds value → the whole config is ignored), is **newer** than the one in
  use (no rollback) and **unexpired** (`expires`; an expired config falls back to the bundled
  recipes). Data only, no code: `endpoint` (same https host, known placeholders; a GraphQL
  `doc_id` goes in its query), `headers` from a fixed list (never Cookie, Authorization,
  User-Agent), `agent`, and the key table (ID fields/paths, item lists, media keys, title,
  duration, thumbnail paths). DRM, no-video and login-wall paths can only be **added**. Hosts,
  cookie domains, body caps, regular expressions, photo/access answers never come from a config.
- ✅ Asked by `SiteContracts` before the site, bounded: HTTPS `raw.githubusercontent.com` only
  (answer host locked), no cookie, 128 KiB, 1.5 s, at most once a day (once an hour after a
  failed ask); L2 reads with the same recipe source (`ContractLayer(recipeOf)`). App: one
  process-wide config (`MasterRecipeConfig`); `-Pyft.masterRecipeKey=<hex>` (public key) and
  `-Pyft.masterRecipeUrl` (default `recipes/master-recipes.json` on this branch). **No key = the
  bundled recipes and no request** (the default build). Flag off = main.
- ✅ Owner tools: `scripts/master-recipe-sign.sh` (`keygen` once → private key stays off the
  repo, prints the public key; `sign <key> <config> <out>` checks the top-level shape and writes
  the envelope); `extractor-master/recipes/master-recipes.example.json`.
- ✅ Tests: `RemoteRecipesTest` (5) — a signed config changes Instagram's endpoint, a header and
  its media key, and the engine reads the renamed embed from the key table via `CONTRACT` (config
  asked first, no cookie, 128 KiB; hosts, cap, cookie domain, photo markers, wall paths
  unchanged; other sites stay bundled); 32 bad envelopes/configs (unsigned, empty signature,
  another key, tampered payload, other `alg`, extra envelope key, not JSON, `answerHosts`/
  `cookieDomain`/`maxBytes`/`itemNumber`/`photoMarkers`, Cookie/User-Agent/CRLF headers, another
  host, http, user part, unknown placeholder, deep path, bad mime, unknown media key, bad wall
  path, unknown site, schema 2, version 0/1.5, expired, no such date, unknown key, oversized) →
  ignored, bundled recipes unchanged and the embed still answers; no rollback, expiry falls back;
  asked once a day / once an hour after a failure / never off GitHub; no key, a bad key, a P-384
  key, http or a non-GitHub address → never asks; an openssl-signed envelope (throwaway key, its
  private half deleted) verifies; schema keys, sites, headers, mimes and orders match the reader;
  the example config is valid.
  Local: `:extractor-master:test` 284 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: `bash scripts/master-recipe-sign.sh keygen ~/yft-recipe-key.pem`, build with
  `-Pyft.masterRecipeKey=<printed hex>`, sign a config and commit it as
  `recipes/master-recipes.json`. Deviations: the bundled defaults stay in code (`ContractRecipes`,
  type-checked) instead of an asset copy; the app keeps the config in memory only (asked again
  once per process start, then daily); coverage is only data-only breakages (~30–40%).
- ✅ CI (R9, `f08b895`): Master opt-in debug APK run `38093579832` — success.

## R8 — Per site (L2 contract first, then L3 + capture)

- ✅ §3 rule conflict, decided (MASTER_KEY_ARCHITECTURE.md §3): layers still never fetch. The
  **engine** asks the identified video's own endpoint once, as stage `CONTRACT` (before page
  data and capture), through the caller's bounded `ExtractorHttpClient`
  (`contract/SiteContracts`: HTTPS, the client's redirect limit and timeouts, a per-site body
  cap, answer host lock, its own 6 s share of the 20 s budget so capture keeps time). L2
  (`ContractLayer` → `ContractReader`) only reads the answer, delivered as
  `PageSnapshot.contract`, which no page or capture can fill. Recipes are data
  (`recipes/ContractRecipes`: endpoint, headers, agent, cookie domain, host locks, key table,
  DRM paths, access phrases, expiry/title/duration paths).
- ✅ When: only with the caller's identity (`MasterRequest.identity`; the app passes its
  adapter's `SitePageIdentity`, `SiteAdapterCoordinator.identity`), never for a short-link
  code, never after `LOGIN_REQUIRED`/`BOT_CHECK`/`PLAYER_SCRIPT_REQUIRED` (those need the
  user's own playback) or a `NEVER_FALLBACK` reason; once, no retry. Engine without
  `contracts` = R7 engine. App: `create` passes `SiteContracts(OkHttpExtractorClient(client))`
  (the same client as the R6 module).
- ✅ Reading: the key table first (one page video, MAIN, rows keyed `site:contentId` = the
  app's key); when it finds nothing (a renamed key) L4 + L3 read the same answer
  (rename-proof). DRM paths → `DRM_PROTECTED`; access phrases count only when no file was found
  (main's rule) and stop with their reason: 0 capture, 0 probes. An answer naming another video
  gives nothing. Rows share the probe budget; success → `MasterStage.CONTRACT`.
- ✅ Performance: a contract answer needs no playback: one request replaces the
  play-and-capture round; a slow endpoint costs at most 6 s, then capture runs as before.
- ✅ **Vimeo**: `player.vimeo.com/video/{id}/config` (`?h=` unlisted hash kept; host lock;
  Referer = clip page; the user's cookie only inside vimeo.com, as main). Key table = main's
  renditions (progressive by height, then HLS/DASH from the default CDN, `url` then
  `avc_url`), `request.files.drm`/`video.drm`, main's access phrases, `request.expires`. One
  request instead of main's page + config. Fix: `UrlPolicy.videoKey` had no Vimeo mapping, so
  Master's Vimeo rows were keyed `master:vimeo.com:…` and the app's focused-key filter dropped
  them; now `vimeo:…`, and with an identity every row is keyed by the identity's site.
- ✅ Vimeo tests: `VimeoContractTest` (10) — parity with main's `VimeoExtractor` on
  `player_config.json` (main's 5 rows ⊆ Master's, sizes 1080/720/540, every row probed, key
  `vimeo:123456789`, 1 request, 0 capture); DRM/private/password/geo = main's reasons with 0
  capture and 0 probes; no files/insecure/malformed/expired → capture; unlisted hash, Referer
  and cookie; cookie never off vimeo.com; another video's answer; renamed keys found by shape;
  login/bot-check/player-script → no request; no contracts or identity → no request; slow
  endpoint → capture (capture rows keyed `vimeo:…`). App:
  `BrowserMasterFallbackTest.contractRowsReachTheAppUnderTheAdaptersKey` (rows reach the app
  under the adapter's key, 0 capture). Drift: 8 new rows (53), 0 drift.
  Local: `:extractor-master:test` 254 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `vimeo-clip`, `vimeo-embed`, `vimeo-unlisted`, `vimeo-ondemand` on a
  phone.
- ✅ **X**: `cdn.syndication.twimg.com/tweet-result?id={id}&token={token}` — the widget's own token
  (`contract/WidgetToken` = main's `XSyndication.token`/`radixString`), host lock, main's headers,
  no X cookie, 2 MiB cap. Key table = main's: `mediaDetails` items (`/video/N` names the item, else
  the first with files), MP4 variants by bitrate (sizes from `/vid/WxH/`), the playlist only
  without MP4s, files only from `video.twimg.com`, the quoted post's media only when the post has
  none (never by number), title = the text's first line without its trailing link, per-item
  duration and poster. Posts the answer does not show (tombstone, `{}`, photos only, 404) go to
  capture: main keeps the generic detector there and the user may be signed in, so X has no
  access phrases. Reader additions (data-driven, all sites): item metadata from the chosen item,
  `postText` titles, `thumbnailPath`.
- ✅ X tests: `XContractTest` (6) — parity with main's `XExtractor`: `tweet_video` (same request
  URL, heights 1280/852/568, title, duration, no cookie, no playlist row, 0 capture),
  `tweet_mixed` (`/video/3` → 720 and the foreign host dropped; no number → 1080/270),
  `tweet_quoted`/`tweet_gif`/`tweet_hls_only` (same kinds and durations; `/video/2` never picks
  a quoted item), `tweet_photos`/`tweet_tombstone`/404 → capture with 0 probes, another post's
  answer → nothing, main's token vectors. Drift: 19 new rows (72), 0 drift.
  Local: `:extractor-master:test` 260 (0 failures, 2 skipped); full flag-off app unit tests 938
  (0 failures, 66 skipped) and `lintDebug` OK.
- ⏳ Owner-run: live parity `x-nasa`, `x-multi-video`, `x-gif`, `x-protected` on a phone.
- ✅ CI (Vimeo + X, `d705e03`): Master opt-in debug APK run `38086897673` — success.
- ✅ **Facebook**: the embedded-video player page `www.facebook.com/plugins/video.php?href={href}`
  (one page for watch, reel and page videos; live check 2026-10-10: `hd_src`/`sd_src` with the
  matching `video_id` inside `s.handle({...})`), asked as main asks its public page: desktop Safari
  (main's `AVC_LADDER_USER_AGENT`), main's navigation headers, **no session cookie**, 6 MiB cap,
  host lock. Key table = main's page parser (`progressive_urls` in the delivery fragment and on
  the node, the legacy fields best first, inline DASH tracks, manifest addresses only without
  inline tracks, `video_id`/`videoId`/`id` anchor, title/thumbnail/duration fields) plus the
  embed's `hd_src`/`sd_src`, so the embed and main's page shapes share one reader. DRM = main's
  `drmAssessment` as paths (a flag only when `true`, a licence map only when non-empty,
  `drm_info` read inside its JSON text) + the embed's `videoLicenseUriMap`/
  `graphApiVideoLicenseUri` → `DRM_PROTECTED`, 0 capture. No access phrases: the embed is an
  anonymous view, so a login/unavailable/region page goes to the user's own playback (main has
  already given its signed-in verdict before Master runs). Share links: never asked (the code
  needs a redirect). Reader additions: loader-call payloads (`callMarkers`) also reach the shape
  fallback; on a page, the shape fallback keeps only rows anchored to the video's ID; title,
  duration (ms or s) and poster as path lists.
- ✅ Facebook tests: `FacebookContractTest` (6) — the embed shape (2 rows, Safari, no cookie,
  Referer, key `facebook:{id}`, 0 capture); parity with main's `FacebookExtractor` on
  `watch_progressive` (the suggested video never), `reel_legacy_fields`, `reel_hd_sd_only`,
  `reel_inline_dash`, `public_reel_empty_licences` (not DRM); `drm_video` and a licensed embed →
  `DRM_PROTECTED`, 0 capture, 0 probes; login/unavailable/region/no media/malformed/insecure →
  capture, 0 probes; renamed embed fields found by shape, main's `changed_markup` (renamed
  delivery key) recovered by shape under its own ID, another video's embed → nothing; share link
  → no request. The saved live embed page (not committed) gave HD + SD via `CONTRACT`. Drift: 11
  new rows (83), 0 drift. Deviation: main's second Safari request for the AVC ladder is not
  repeated (one answer per video); the resolver still reads every file.
  Local: `:extractor-master:test` 266 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `facebook-page-video`, `facebook-reel`, `facebook-fb-watch`,
  `facebook-private`, `facebook-share-link` on a phone.
- ✅ CI (Facebook, `e28f311`): Master opt-in debug APK run `38087871484` — success.
- ✅ **TikTok**: the embed player page `www.tiktok.com/embed/v2/{id}` (live check 2026-10-10:
  `__FRONTITY_CONNECT_STATE__` → `videoData.itemInfos`, `video.urls` + `videoMeta`; its file
  opened with a range request without any cookie or Referer), asked with the tab's own agent
  (main's rule: never YFT's), main's navigation headers + Referer, **no cookie**, main's 3 MiB
  page cap, host lock. Node = the object whose `itemInfos.id` or `id` is the post's ID. Key
  table = the embed's `video.urls` (one file's mirrors → one row; size/duration from
  `videoMeta`) plus main's page shapes: `bitrateInfo` (one row per entry, its first https
  `PlayAddr.UrlList` address; `PlayAddr.Width/Height`, `Bitrate`, `DataSize`, codec from
  `CodecType`/`UrlKey` → `avc1`/`hvc1`, best bitrate first), the play address, and the
  watermarked download address only when nothing else is listed, titled "… — With TikTok
  watermark" (main's label). Caption, cover and duration fields as main's. The music's address
  (`musicInfos.playUrl`) is never a row. Stops: `isDrm` → `DRM_PROTECTED`; a photo post
  (`imagePostInfo`/`imagePost`) → `NO_MEDIA_FOUND`, 0 capture (main's final answer). Anything
  else the anonymous embed says (error page, TikTok status codes) goes to the user's own
  playback. Reader additions: dotted ID/address/field paths, a list = one file's mirrors (first
  absolute https address; an empty field is no file), per-entry field names
  (`ContractFields`) + `metaPath`, row labels, `noVideoPaths`, list indexes in paths
  (`covers.0`); the shape fallback's access verdicts count only for an answer asked with the
  user's cookie (Vimeo); DRM always stops.
- ✅ TikTok tests: `TikTokContractTest` (7) — the embed shape (1 row 720×1280, 26 s, caption,
  cover, key `tiktok:{id}`, the tab's agent, no cookie, Referer, music never a row, 0 capture);
  parity with main's `TikTokExtractor` on `universal_video` (best first: 1080p, bitrate, size,
  `avc1`), `sigi_video`, `universal_reflow_video`, `live_desktop_video_detail`, the watermarked
  file never beside others; only the download address → main's row and "With TikTok watermark"
  title; `drm_video` + a DRM embed → `DRM_PROTECTED`, `universal_photo` + a photo embed on a
  `/photo/` page → `NO_MEDIA_FOUND`, 0 capture, 0 probes; private/login/region/malformed/
  insecure/home page/the embed's error page → capture, 0 probes; renamed `urls` found by shape
  under the post's ID, another post's embed and main's `changed_markup` (no ID) → nothing; short
  link → no request. The saved live embed page (not committed) gave its 720×1280 file via
  `CONTRACT`. Drift: 11 new rows (94), 0 drift. Deviations: one answer, no desktop-page second
  request and no file checks of main's (Master's own probes check every row); the embed gives
  one quality (main's page may list more); main's page-answer cookies (`tt_chain_token`) are
  not replayed, as the embed's files need none.
  Local: `:extractor-master:test` 273 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `tiktok-nasa`, `tiktok-short-link`, `tiktok-photo`, `tiktok-private`
  on a phone.
- ✅ CI (TikTok, `29eb119`): Master opt-in debug APK run `38089590941` — success.
- ✅ **Instagram**: the captioned embed page `www.instagram.com/p/{code}/embed/captioned/` (live
  check 2026-10-10, reel: `contextJSON` → `shortcode_media` with `video_url`, `dimensions`,
  caption and owner; its file opened without any cookie), asked with the tab's own agent, main's
  navigation headers + Referer `https://www.instagram.com/`, **no cookie** (main's `pageHeaders`
  without its cookie), main's 6 MiB page cap, host lock. Embedded JSON documents are read before
  the page. Node = the object whose `shortcode` or `code` is the post's code. Items = main's
  `postOf`: `carousel_media` or the sidecar's `edge_sidecar_to_children.edges.*.node`, else the
  post itself; a carousel link's `img_index` picks its item (main's `IMAGE_INDEX`), else the first
  video item. Key table = main's `itemOf`: `video_versions.*`, `video_url` with `dimensions`,
  both inline DASH manifests; duration (`video_duration`, s), thumbnail
  (`image_versions2.candidates.0.url`, `display_url`). Title = the caption's first line (main's
  `displayTitle`), else "{full_name} on Instagram", else "@{username} on Instagram". Stops: a
  photo post (`is_video` false / `media_type` 1 on every item, as the answer states it, never
  from a missing field) → `NO_MEDIA_FOUND`, 0 capture (main's final answer). A login or
  checkpoint page (`/accounts/login`, `/challenge`, main's `isLoginWall`) answers nothing, so it
  and any other anonymous wording go to the user's own playback. Reader additions: item lists
  as paths with `*` (X's `mediaDetails` now `[mediaDetails, *]`), the node itself when it has no
  list, `photoMarkers`, owner-name titles (`nameTitles`), embedded documents first;
  `SiteContracts.onWall()`.
- ✅ Instagram tests: `InstagramContractTest` (6) — the embed shape (1 row 640×1136, 15.2 s,
  caption, thumbnail, key `instagram:{code}`, the tab's agent, no cookie, Referer, 0 capture);
  parity with main's `InstagramExtractor` on `embed_captioned`, `page_signed_in`,
  `graphql_reel`, `api_info_reel`, `graphql_carousel` (`img_index` 3 and 1), another post's file
  never; a carousel link picks its item, else the first video ("Fixture Creator on Instagram");
  `graphql_photo`, a photo carousel item, an API photo and a photo embed → `NO_MEDIA_FOUND`,
  0 capture, 0 probes; no video/login/null/fileless/a redirect to the login page/another post's
  embed → capture, 0 probes; a renamed `video_url` found by shape. The saved live embed page
  (not committed, reel) gave its 720×1280, 62 s file via `CONTRACT`. Drift: 13 new rows (107), 0 drift. Deviations:
  one answer per post (no signed-in page or app API request; capture decides access, not the
  anonymous embed); a login page is read as no answer; the embed gives one quality; the `efg`
  bitrate inside a file address is not read (Master's probe gives the size).
  Local: `:extractor-master:test` 279 (0 failures, 2 skipped), Android 56, app 25 (CI filter),
  JS 28, drift 0, `:app:assembleDebug` OK.
- ⏳ Owner-run: live parity `instagram-reel`, `instagram-carousel`, `instagram-private` on a
  phone.
- ✅ **R8 done (offline)**: Vimeo, X, Facebook, TikTok and Instagram answer through `CONTRACT`
  first; flag off = main unchanged. Live parity and phone checks stay owner-run
  (`ONLY=<ids> bash scripts/canary.sh`).
- ✅ CI (Instagram + R8 close, `38840b5`): Master opt-in debug APK run `38093084650` — success.

## R7 — Generic web: capture + MSE/EME metadata hooks

- ✅ `assets/yft-master-mse.js` (document start via `addDocumentStartJavaScript`, top frame only,
  off on YouTube hosts; nothing on WebViews without the feature): records each SourceBuffer's
  type (`addSourceBuffer`), the picture size of its init segment (ISO-BMFF `tkhd`, WebM
  `PixelWidth`/`PixelHeight`), and which delivered addresses fed it (the ArrayBuffer the page
  read from XHR `response` or fetch `arrayBuffer()`, matched by identity; bytes are never
  copied or sent). Every original gets its own arguments and returns its own result;
  `dispose` restores them.
- ✅ EME: a key attached to a video (`setMediaKeys`) or a licence request (`generateRequest`)
  marks the page protected → `DRM_PROTECTED`, before any media check. A bare
  `requestMediaKeySystemAccess` is only counted: many players ask it at start-up for clear
  video, so it alone is not protection (R2's `mediaKeys`/`encrypted` checks stay).
- ✅ `yft-master-capture.js` adds the focused video's fed addresses to each sample (`fed`, its
  buffer type, init size); `CaptureFrame` → `MasterBrowserSession` → `CapturedRequest(fedPlayer,
  width, height)`. L1 (`CaptureLayer`) treats a fed address like the playing address (MAIN, the
  requested content ID) and keeps the stated codec and size; one player's fed tracks and
  qualities share one page-video key, so they are one focused video, never rival videos. R4
  grouping, ad/preview rules and the probe budget apply unchanged.
- ✅ L1 capture stays the default answer for sites with no adapter (the user's Download tap →
  one capture); no background capture was added.
- ✅ Tests: `mse.test.cjs` (6: buffer types + MP4 init size + XHR/fetch feeding, WebM init size,
  originals untouched/plain-HTTP ignored, EME query vs keys/licence, capture sample carries fed
  rows and stops on a licence, YouTube off + dispose), `CaptureLayerMseTest` (2),
  `MediaSourceCaptureTest` (3: frame parsing, fed address → MAIN with codec/size through the
  engine, licence → `DRM_PROTECTED` with 0 probes). Spike CI runs all three JS files. Live
  parity on the generic URLs (plain `<video>`, HLS, DASH, VAST pre-roll) is owner-run.
  Local: `:extractor-master:test` 244 (0 failures, 2 skipped), Android 56, app 24, JS 28,
  `:app:assembleDebug` OK.
- ✅ Spike CI run 38081550529 green on `e163ec5` (drift, JS 28 incl. `mse.test.cjs`, Master
  tests, opt-in debug APK).

## R6 — Own YouTube module (YT-1 / YT-2 / YT-4)

- ✅ YT-1: main's five YouTube files copied whole into `extractor-master/.../modules/youtube/`
  (provenance header; only the package differs) with their five tests and test support
  (`Fixtures` reads main's fixtures via `yft.siteFixtures`). Whole-file rows (`*`) in
  `toolkit-provenance.tsv`: the drift check hashes each file after its package line on main
  and also fails when the Master copy itself was edited. 45 rows, 0 drift vs `34a41890` and
  `origin/main`.
- ✅ `modules/MasterSiteModule` + `SiteExtractorModule` (row check: HTTPS media and companion,
  no `sabr` parameter, not protected; rows tagged `site:contentId` like the app coordinator).
  `MasterYouTubeModule`: the copy with **no** player-script runner and **no** PoToken provider
  (n/sig streams are never offered by Master; main, flag off, still covers them), asked
  without the user's cookie/authorization header.
- ✅ YT-2: `MasterYouTubeClients` (table version 1, source "main 34a41890 / yt-dlp
  2026.08.19", visionOS first, no client needs a script or token). Canary:
  `MasterYouTubeCanaryTest` (skipped unless `-Pyft.youtubeCanary=<id>`) +
  `bash scripts/master-youtube-canary.sh <id>` (owner-run, never CI): passes while visionOS alone
  gives a complete answer ("visionOS first: complete, no watch page").
- ✅ YT-4: no SABR code; SABR-only and cipher fixtures give 0 rows.
- ✅ Engine slot: `MasterFallbackEngine(modules = …)`, stage `SITE_MODULE`. A claimed page never
  reaches layers, capture or probes; the module asks only when no site adapter answered
  (`primaryFailure == UNSUPPORTED_URL`), otherwise `Skipped(primaryFailure)` (P12, one lookup
  per video). Terminal rules and the off switch come first; a different requested video →
  `RESPONSE_CHANGED`. App: `AndroidBrowserMasterFallback.create` passes
  `MasterYouTubeModule(OkHttpExtractorClient(client))`; with main's adapter present YouTube
  watch pages are therefore never captured by Master.
- ✅ Tests: copied YouTube tests (87), `MasterYouTubeParityTest` (6: main vs Master on 20
  fixture scenarios — same requests, posts, headers, rows, order, labels, sizes and details;
  cipher/SABR → 0 rows; bot check → `BOT_CHECK` with 0 capture/probe; engine answers through
  the module only; table order), `MasterSiteModuleTest` (7). `extractor-sites` is a
  test-only dependency for the parity comparison. `:extractor-master:test` 242 tests,
  0 failures (2 skipped: canary, live smoke).
- ✅ Spike CI run 38079213716 green on `46ef8ec` (the first run, 38078858636, stopped 15 s into
  the Gradle step before any task ran; job logs need admin rights, so the workflow now
  publishes Gradle failure lines as an annotation).

## R5 — E: codec steering

- ✅ `assets/yft-master-codecs.js` (document start, `addDocumentStartJavaScript`, opt-in builds
  only; nothing on WebViews without the feature): `MediaSource.isTypeSupported` (also
  `ManagedMediaSource`/`WebKitMediaSource`) and `mediaCapabilities.decodingInfo` answer only
  what `DeviceMergeSupport` merges — AVC/AAC always, VP9/Opus/Vorbis WebM from Android 10, AV1
  only while AV1 merges are on (off), HEVC/Dolby/E-AC-3 never, VP8 never. It only narrows (the
  browser is still asked), never touches DRM (`keySystemConfiguration`) queries, is off on
  YouTube hosts and can be disposed; unknown codecs pass through.
- ✅ `CodecSteering` (policy → script) and `WebViewPlaybackCapture(codecs = …)`; the app hook
  builds it with `AndroidBrowserMasterFallback.codecSteering(sdk)` from
  `AudioVideoMuxCompatibility` (same rules as `DeviceMergeSupport`). Main's Facebook Safari AVC
  agent stays the site-specific case (untouched).
- ✅ Tests: `codecs.test.cjs` (5: AVC/AAC on, AV1/HEVC/E-AC-3 off, VP9/Opus by Android
  version, never widens, decodingInfo + DRM pass-through, YouTube off/idempotent/dispose),
  `CodecSteeringTest` (3), app `BrowserMasterFallbackTest.codecSteeringFollowsTheDeviceMergeRules`.
  Spike CI runs both JS files. Live parity (AVC ladder where offered; 1440p/2160p kept with
  VP9) is owner-run on a phone.

## R4 — C: keyframe / duration fingerprint

- ✅ `verify/SegmentIndexReader`: DASH/ISO-BMFF `sidx` (v0/v1, media references only) and ended
  HLS `#EXTINF` playlists → length + keyframe cues (segment starts); HLS SAMPLE-AES/FairPlay
  keys → protected. `CapturedMp4Facts` uses it (same duration rule as before) and keeps cues.
- ✅ `verify/MediaFingerprint` (length + cues): `keyframesAgree` (≥ 90 % within 120 ms → yes,
  < 50 % → no, too few cues → cannot tell), `sameVideo` (agreeing cues and length within
  250 ms; 40 ms when both are a fixed grid), `otherVideo`. Length alone never groups.
- ✅ `CapturedMediaMetadata.inspect` returns the fingerprint from the same bounded reads (MP4
  prefix `sidx`; HLS playlist, then a master's first playlist only on the same origin). A
  protected HLS playlist sets `drmHint` → `DRM_PROTECTED`. `enrich` keeps its contract.
- ✅ `verify/FingerprintGroups` + `MasterMainSelection`: one video's qualities form main's group
  (one `MediaGroup` with several files, not extra "More" videos); a lone same-length file whose
  cues disagree with a main confirmed by two agreeing files is dropped (ad/related/preview);
  a second agreeing ladder or a file without cues stays its own group (before-R4 behaviour).
  No row is created or changed; ranking is unchanged inside each group.
- ✅ Tests: `SegmentIndexReaderTest` (7), `MediaFingerprintTest` (8, HLS fixtures under
  `src/test/resources/fingerprint/`), `CapturedMediaInspectTest` (5),
  `MasterFingerprintSelectionTest` (5). `:extractor-master:test` 141 tests, 0 failures.

## R3 — Toolkit + L3 shape search (content-ID anchoring)

- ✅ `toolkit/` copies (provenance header in each file, base main `34a41890`): `PageScripts`
  (T1: scripts by their own `id`/`type`, `var x = {…}`, JSON inside strings, `contextJSON`,
  `__additionalDataLoaded`), `BalancedJson` (T2: string-aware object/array reads, size cap,
  HTML-entity retry), `AnchoredMediaWalk` (T3: bounded walk carrying the nearest content ID),
  `MediaKeyTable` (T4 data: address/version/MIME/ID/hint keys, DRM statement, skip subtrees),
  `QualityLadder` (T7), `ProbeRounds` (T8), `RequestPolicy` (T9), `UrlPolicy`
  `PLAUSIBLE_EXPIRY_SECONDS` (T10). T7–T9 are ready for the per-site steps (R8); no
  behaviour uses them yet.
- ✅ `layers/ShapeLayer` (L3) runs last and only adds: a row an earlier layer found is dropped,
  earlier rows reach the normalizer unchanged, and it is skipped once a layer gave a terminal
  verdict. Never on a YouTube host, never inside `streamingData`/ad subtrees. A row is `MAIN`
  only when anchored to the page's content ID, `PREVIEW` for preview keys, otherwise no role
  (so a related video cannot become main). DRM statements (`drm`, licence objects, Widevine/
  FairPlay/PlayReady, FB `video_license_uri_map`) end in `DRM_PROTECTED`; a document's past
  `expires` drops its files at the gate.
- ✅ Drift check: `extractor-master/toolkit-provenance.tsv` (40 main symbols + SHA-256 at the
  base) and `scripts/master-toolkit-drift.py`; spike CI self-checks the base (must be 0) and
  reports changes on `origin/main` as warnings. Today: 0 drifted.
- ✅ Tests: `ShapeLayerTest` (shape ⊇ fixed-key on every committed fixture, renamed keys keep
  hits, related video rejected by anchoring, finders, DRM/expiry, all negative fixtures offer
  0, L3 only adds, YouTube/`streamingData` off), `ToolkitFindersTest`, `QualityLadderTest`,
  `ProbeRoundsTest`, `RequestPolicyTest`; `:extractor-master:test` 121 tests, 0 failures.
  Fixtures are read in place from `extractor-sites/src/test/resources/fixtures` (no copy, so
  no fixture drift) plus 3 new ones under `extractor-master/src/test/resources/shape/`.
- ✅ Fixture baseline rewritten on purpose (rows only grow): Facebook `changed_markup` 1→2,
  `watch_progressive` 3→6, `expired_links` 0→2 (expired, offered 0); Instagram `api_info_reel`
  2→7, `graphql_reel` 1→6, `page_signed_in` 3→8, `embed_captioned` 0→1 (heights gain 1920);
  Vimeo `player_config` 0→6, `player_page_inline` 0→2, `config_expired` 0→1 (expired),
  `config_drm` now ends `DRM_PROTECTED` (L3 reads `files.drm`; safety gain).

## R2 — Safety gaps (terminal walls, DRM stop, YouTube payload off)

- ✅ `policy/TerminalRules`: `BOT_CHECK`, `LOGIN_REQUIRED`, `PLAYER_SCRIPT_REQUIRED` are final on
  `youtube.com`, `youtu.be`, `youtube-nocookie.com`, `reddit.com`, `redd.it` and their
  subdomains (`blocksFallback(failure, pageUrl)`; host read as a browser reads it, so
  `youtube.com@evil.test` or `evil.test\@youtube.com` do not match). The engine checks
  `request.pageUrl`; the app hook checks the lookup link before any capture request and the
  tab page after it. Elsewhere the same failures still need the user's own playback.
- ✅ YouTube `streamingData` gives no Master rows (standard stack; never on a YouTube host even
  when opted in) until the R6 module. Its DRM statement still stops Master everywhere, with
  main's signals: `drmParams`, `playbackTracking.drmSessionId`, per-format `drmFamilies` /
  `drmTrackType` (main's `youtube/player_drm.json` now ends `DRM_PROTECTED`).
- ✅ EME stop: `yft-master-capture.js` marks the page protected when any visible video has
  media keys or an `encrypted` event (before: only the selected video). The session keeps it
  until navigation; the engine returns `DRM_PROTECTED` before any media check.
- ✅ Tests: `policy/SafetyGapsTest` (walled bot check → 0 capture, 0 probe; YouTube payload →
  0 rows, 0 probe; EME and stated DRM → `DRM_PROTECTED`, 0 probe), `TerminalRulesTest` host
  cases, `LayerStackTest` R2 cases, app `BrowserMasterFallbackTest.walledSites…` (0 request
  factory, 0 capture, 0 probe), 3 new JS capture tests. `MasterHardeningTest`'s companion
  budget fixture moved from YouTube JSON to an inline MPD (same assertions).
- ✅ Spike CI now also runs `node --test …/capture.test.cjs`.
- ✅ CI on `a2862abd`: spike `Master opt-in debug APK` run 38068967732 — success (capture JS
  tests, Master tests, opt-in APK; artifact `yft-master-optin-debug-apk`, 18.3 MB, 14 days).
  Flag-off `Work-branch checkpoint validation` run 38068970580 (PR #1) — success (artifact
  `yft-debug-apk`). Older runs on `314c97a`/`48915f3` are stale.

## R1 — Base sync + parity harness + canary

- ✅ Merged `origin/main` `34a41890` (61 commits since `a9eea7ba`). Conflicts only in
  `BrowserScreen.kt`, `BrowserViewModel.kt`, `docs/SESSION_STATE.md`: main's code kept, the
  Master hook re-applied around it. Main's P45 ("the tapped video only") removed the sheet's
  "Other videos on this page" row, so Master's other videos now stay in the browser's found
  list; `MasterMainMoreSheetTest` asserts exactly that (and flag-off = legacy).
- ✅ Frozen list: `app/src/androidTest/assets/parity/parity-urls.json` (32 cases from
  MASTER_KEY_PLAN §4.3; 14 public links; owner-picked slots stay `null`, never committed).
- ✅ Pure JVM harness `extractor-master/.../parity/`: `ParityUrls` (rejects http, user info,
  fragments, signed/session queries), `ParityReport` (host + content ID only; rows keep shape;
  titles hashed; fails closed on any `://`), `ParityVerdict` (§4.2 checks). Unit-tested,
  including a leak test with signed links, cookies and URL-bearing titles.
- ✅ Offline baseline: `FixtureBaselineTest` runs Master's stack over main's 84 site fixtures
  (`extractor-sites/src/test/resources/fixtures`, passed by Gradle) against
  `parity/fixture-baseline.json`: rows and heights may only grow, terminal verdicts must stay.
  A main sync that adds or removes fixtures fails until `-Pyft.parityBaseline=write`.
- ✅ Live: opt-in `MasterParityLiveTest` (`-e yft.parity 1`; arm A = main's adapters as the app
  wires them, arm B = Master in a visible WebView with real one-byte checks; attended mode
  waits for the owner's Play). `scripts/canary.sh` installs, runs and pulls the report. Not
  in CI. Spike CI now compiles `:app:compileDebugAndroidTestKotlin`.
- ✅ Local Gradle: `extractor-master` 101 tests (1 opt-in skip), Android module 45, app Master
  tests 23 (sheet 3, flow 9, fallback 11), androidTest compiles, 0 failures.
- ✅ The merge was made on GitHub through draft PR #1 (spike → main, "[DO NOT MERGE]"), used
  only for that merge: `314c97a` pre-merge → `48915f3` merge of main `34a4189` → `a2862ab` hook
  re-applied. PR #1 was closed unmerged after CI; main is untouched.
- ✅ CI on `a2862abd`: spike run 38068967732 and flag-off run 38068970580 both success (see R2).

## A0 — Architecture step A (structure only, same behavior)

- ✅ Map: [MASTER_KEY_ARCHITECTURE.md](MASTER_KEY_ARCHITECTURE.md). `extractor-master` now has
  `policy/`, `layers/` (L1 `CaptureLayer`, L2 `ContractLayer`, L4 `RecipeLayer`, L3 slot),
  `recipes/` (data-only key tables), `toolkit/`, `verify/`, `present/`, `capture/`; the engine
  is orchestration only.
- ✅ One stop-rule source: `policy/TerminalRules`. The app hook's own `NEVER_CAPTURE` copy is
  gone; `BrowserMasterFallback` calls `TerminalRules.blocksFallback`. Same sets as before.
- ✅ Android-free files moved to the JVM module: `CapturedMediaMetadata`, `CapturedMp4Facts`
  (`verify/`), `MasterMainPresentation` (`present/`). Public API names unchanged; only imports
  moved.
- ✅ Local JVM run: `extractor-master` 88 tests and 39 non-WebView Android-module tests, 0
  failures. Old-vs-new differential runs: reader 5,372 cases and engine 10,368 cases, 0
  differences.
- ✅ CI `master-optin-debug-apk` run `38064367003` on `fcceaec4` (final commit of the step):
  success — Master, Android-module and app Master tests plus `:app:assembleDebug`.
  Run: https://github.com/Alalkipgen/YFT/actions/runs/38064367003
  Artifact `yft-master-optin-debug-apk` (17,990,603 bytes, expires 2026-10-24):
  https://github.com/Alalkipgen/YFT/actions/runs/38064367003/artifacts/11674876484
  Pushed as `ef63a60a` (new layout; old paths as one-line placeholders) followed by 11
  placeholder deletions (the GitHub tool cannot delete in the same commit); the 11
  intermediate CI runs were cancelled by the workflow's concurrency group.
- No policy, site, Generic, model or download behavior changed. No main merge, release or tag.

## M2 — main/More sheet, expected TikTok More, opt-in CI APK

- ✅ TikTok More = 0 recorded as expected (single-video post page); no more alternative probes.
- ✅ `app/src/test/.../feature/browser/MasterMainMoreSheetTest.kt` (Robolectric, Compose, JVM).
  It drives the real `BrowserViewModel` hook path on a TikTok-shaped site page, the real
  `QuickDownloadViewModel`/`QuickDownloadRoute` sheet and the real `BrowserScreen` found list:
  - main + 7 More: the sheet shows only the main video; the existing More button
    "Other videos on this page (7)" closes the sheet and opens the found list with all seven.
  - main + 0 More: the main video and Download show; no More button exists.
  - flag off: the real factory with `false` is `BrowserMasterFallback.None`; selection, count and
    the full sheet state equal the pre-Master default (only the wall-clock resolve time is
    masked); no More button; a failing adapter gives the same lookup failure with no capture.
  - The main/More presentation is a fixture with the module's shape (public constructor, one
    group per verified file, owned UI keys, no post ID). Selection policy remains covered by the
    module tests. No production, model, Generic, extractor or download file changed.
- ✅ Local run of the exact CI test set with `-Pyft.masterCapture=true`: 18 suites, 153 tests,
  0 failures (`MasterMainMoreSheetTest` 3/3, `BrowserMasterFlowTest` 9, `BrowserMasterFallbackTest`
  10, `extractor-master-android` 54, `extractor-master` 77).
- CI: new spike-only workflow `.github/workflows/master-optin-debug-apk.yml` (existing workflows
  untouched) runs the Master tests and builds `:app:assembleDebug` with
  `-Pyft.masterCapture=true`, uploading artifact `yft-master-optin-debug-apk`.
  ✅ Run `37997610268` on `4ceab089`: success (tests and build steps all green).
  Run: https://github.com/Alalkipgen/YFT/actions/runs/37997610268
  Artifact `yft-master-optin-debug-apk` (zip, 17,969,814 bytes, expires 2026-10-23):
  https://github.com/Alalkipgen/YFT/actions/runs/37997610268/artifacts/11648655411
  APK SHA-256 is in the run summary. Debug key is the runner's throwaway key: uninstall an
  older `com.alal.yft.debug` signed by another key before installing.
- ⏳ Owner phone check of that APK is pending. No main merge, release or tag.

## Hooked selection and native live checkpoint

Dedicated, independently revertible hook/wiring commit:
`2b76e4d75136ff4a3e27f590e1e140a3570c9e6a`.
It contains exactly three production hook files plus two wiring/flag-off test files;
the selection/metadata/presentation algorithm remains entirely in the Android module.

| Hook file | Added | Removed |
| --- | ---: | ---: |
| `MasterFallbackEngine.kt` | 12 | 0 |
| `BrowserMasterFallback.kt` | 10 | 1 |
| `BrowserViewModel.kt` | 19 | 7 |

No existing identifiers were renamed or unrelated blocks reformatted. The ViewModel edits
are confined to existing selection call sites and opt-in presentation routing. App models,
Generic files, existing site extractors, download code and CI workflows are unchanged.

### Fresh checks

- ✅ Original seven selection contracts and five safety guards: all 12 pass.
- ✅ Combined policy/parser/transport-frame/opt-in/metadata unit suite: `OK (39 tests)`.
- ✅ Disabled policy skips hook/capture/probe for every primary failure enum. A disabled
  selector equals the legacy engine; a null hook retains legacy refusal and exact-address success.
- ✅ Real flag-off app factory returns the original `None`; every primary object passes through
  unchanged for both generic modes. `OK (1 test)`, isolated caller unit test with fail-fast
  dependency doubles, not an Android UI/device validation.
- ✅ JavaScript transport: 14/14; repository scripts: 30/30.
- ✅ Fragment-index/movie-extends duration and passive-result UI isolation were first run red,
  then fixed using actual container bytes and owned presentation keys respectively.
- ✅ TikTok focused DOM state at index 8 was actually omitted by the eight-script limit.
  The new regression failed first, then passed after prioritizing the two known delivered
  DOM-state nodes within the unchanged eight-node/eight-payload budget. No endpoint, URL,
  ID or playback evidence is invented. Projected bodies remain at most 64 KiB; refusal/DRM
  fields and all response-clone limits are preserved.
- ✅ Two audio-track/presentation tests failed first, then passed. Actual `hdlr=soun` with
  no video handler corrects a misleading video MIME; audio-only entries cannot become main
  or More. Missing decoded dimensions never reject a verified video; mixed tracks stay video.
- ✅ A slow-alternative test failed first, then passed. Android-owned validation has a 15-second
  sub-window inside the unchanged 20-second engine timeout, preserving an already verified main
  and eligible alternatives if a later check stalls. Caller cancellation, DRM refusal, probe
  budget and navigation-generation checks are not bypassed. Capture remains 2.5 seconds.

### Latest completed native diagnostics

These use actual desktop native Pause/Play, trusted click receipts, fresh production collector
frames, the hooked production selection policy, normal HTTPS validation and the unchanged
main/More presentation models. They do not prove Android APK sheet rendering.

- ✅ Instagram `Dcwk7e1yHaY`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  More list count 7. Native trusted click 1, capture 361.2 ms, authorized playback true.
  DOM duration 61,966 ms; independently read selected media duration 61,966 ms;
  selected dimensions 1080x1920. Nine validated inputs; one independently identified audio-only
  input excluded; eight video outputs. TLSv1.3; 2,363,904 bounded response bytes.
  An earlier retry exceeded the engine timeout; it is retained as a failure, not a pass.
- ✅ TikTok `7670337149526379789`: SUCCESS/PLAYBACK_CAPTURE, automatic main selected,
  no NEEDS_SELECTION. Latest repeat: native trusted click 1, capture 358.7 ms,
  authorized playback true. DOM duration 26,166 ms; independently read selected media
  duration 25,169 ms (within the exact 2-second tolerance); selected dimensions 720x1280.
  TLSv1.3; 461,139 bounded response bytes. The repeat uses actual browser-owned cookies
  scoped to real captured addresses, RAM/stdin only, matching existing Android CookieManager
  wiring. No cookie value, signed address or raw response is persisted.
- ✅ TikTok More count 0 is **expected** (owner decision): the public post page has one video.
  The accessible unrelated 2,067 ms file is correctly rejected; the four alternative probes that
  reported HTTP_STATUS are not pursued further and statuses were never overridden. No further
  TikTok alternative probing. One earlier retry blocked by a translation tip is not evidence.
- ✅ Fresh two-run Android gate for this changed tree passed (see below).
- ✅ Main/More sheet rendering is proven by a JVM Compose UI test (see M2 below). Live emulator
  sheet checks are intentionally skipped (no KVM); the owner checks the CI APK on a phone.

M2 status: see the M2 section below. Owner phone validation of the CI APK is pending.
No main merge, release/tag, full download/mux, all-resolution,
Master-CI-green or merge-readiness claim. Only the spike branch is used.

### Android environment recovery

- ✅ Official-index size/SHA-1 checks verified six SDK archives before extraction.
  JDK 17 is retained; API 35/build-tools 35.0.0, platform-tools 37.0.1, emulator exactly
  37.2.12.0 and API29/default/x86_64 image revision 8 are restored. Runtime libraries restored.
- ✅ Emulator package was registered through the official stable-channel SDK manager after
  manual extraction alone failed AVD-manager preflight. Strict yft-master29 AVD recreated:
  480x854, density 240, RAM 1536 MiB, 2 cores. Recovered focus guard/runner is not weakened.
- ✅ The sandbox was later wiped; the spike branch was re-cloned at `def91b91` (production code
  identical to `005fd0f6`), and the same verified SDK packages, Corretto JDK 17 (SHA-256 checked),
  emulator 37.2.12 and strict AVD were restored again.
- ✅ Fresh internal app/androidTest pair built from `def91b91` with `-Pyft.masterCapture=true`
  and `-Pandroid.injected.build.abi=x86_64`: BUILD SUCCESSFUL.
  App SHA-256 `fee5c66f7cedc231f8c69c28ba13a0ecc713e2b89f748d50d2a712b762630e90`;
  test SHA-256 `9c6d8f88f178fbc0efa10640f4a932c21d93d4d04d18c233258a8141b5c106aa`;
  both signed by the same debug certificate
  `ef6a9e9817aba79e5416d41ede1e183e2d3d1ace6c6c094b333f43ddc72b9609`.
- ✅ Emulator cold boot completed (boot_completed=1). A SystemUI ANR dialog was cleared with its
  native Wait button only; focus then Launcher, device-side screenshot 192,040 bytes.
- ✅ Strict gate `gate-def91b91`: two consecutive full-class
  `MasterCaptureInstrumentedTest` runs, each `OK (4 tests)` (172 s and 140 s), identical
  head/APK/test/certificate/harness hashes, installed hashes verified. Focus guard not weakened.
- ❌ Android main-sheet/More rendering is not covered by this gate and remains unproved.
  No owner phone delivery, release, tag or main merge.

### Secret-safe latest native log excerpts

```json
{"site":"tiktok","result":"SUCCESS","stage":"PLAYBACK_CAPTURE","nativeTrustedClicks":1,"captureMs":358.7,"authorizedPlayback":true,"mainSelected":true,"moreCount":0,"domDurationMillis":26166,"selectedDurationMillis":25169,"androidAppLiveProved":false}
{"site":"instagram","result":"SUCCESS","stage":"PLAYBACK_CAPTURE","nativeTrustedClicks":1,"captureMs":361.2,"authorizedPlayback":true,"mainSelected":true,"moreCount":7,"domDurationMillis":61966,"selectedDurationMillis":61966,"androidAppLiveProved":false}
```

## Authorized Android-owned policy checkpoint

The user authorized minimal hook/wiring edits in the three files listed below, in a separate
revertible commit. The selection algorithm, Generic ad-rule reuse, bounded independent MP4
metadata reads and main/More presentation mapping are implemented only in
`extractor-master-android`. Module policy/parser/frame tests: `OK (13 tests)`; JS: 11/11.
The original seven red contracts remain red until the separate hook commit is applied.

The projection keeps original source identities in the memory-only capture record. Only UI
copies use reserved presentation keys and no claimed post ID, so alternatives are not falsely
declared qualities of one post. The existing MediaCandidate/MediaGroup/otherVideos app models
are unchanged. Duration is read from delivered metadata or independently validated MP4 bytes,
never copied from the DOM. Default/release flags and all original security guards are unchanged.
Live main/More and full Android UI validation are still pending.

## Automatic main/More selection — test-first checkpoint

Date: 2026-10-09. Baseline: `ac9695ce6758fbcace6ea1381f185a9e6b5a8278`.
Branch: `spike/master-extractor-backup` only. No merge, release, tag or phone APK delivery.

### Status

- ✅ A compile-valid failing contract was written and executed before any production fix.
- ✅ 12 offline JVM tests ran: 7 expected selection failures, 5 safety tests passed.
- ✅ All seven failures report `NeedsSelection`, not compilation or fixture setup errors.
- ❌ Automatic TikTok/Instagram main selection is not implemented or live-verified.
- ❌ Main sheet plus More integration is not complete.
- ⏸ Production changes await clarification of the module-only boundary described below.

The only new code is
`extractor-master-android/src/test/kotlin/com/alal/yft/extractor/master/android/MasterMainSelectionContractTest.kt`.
This progress document is the explicitly requested documentation exception.
No unused production selector, fake HTTPS playing URL, fabricated MAIN role or guessed
content identity was added.

### Red contract

The test exercises the existing bounded frame reader, browser session and Master engine.
Independent validator fixtures supply candidate duration/dimensions; the DOM duration is
never copied onto candidates. Instagram uses the generic/no-ID request shape. TikTok keeps
its requested content ID instead of deleting it to force a result.

The seven deliberately failing behaviors are:

1. Known DOM duration: independent candidate duration must be within the inclusive 2-second
   tolerance; a candidate 2.001 seconds away must not qualify.
2. TikTok: a different-duration file must not receive the player's duration.
3. A missing candidate duration must not be treated as a known-duration match.
4. Closest decoded dimensions rank matching candidates; smaller matching files remain for More.
5. Equal candidates choose a stable main and retain the other eligible candidate for More.
6. Unknown DOM duration chooses the longest non-ad candidate and retains alternatives.
7. Ad-domain and far-shorter-duration candidates must not become main or More.

Passing guard cases: paused playback, only one progress sample, DRM evidence, stale navigation
and the disabled default policy. These are explicitly offline unit fixtures, not native
Android gesture evidence, live media validation, or a promise that the sheet already works.
No JS play/seek is used. No live-manifest or protected-media refusal is overridden.

Execution: JDK 17.0.20.1, Kotlin 2.0.21/JVM target 17, JUnit 4.13.2.
The standalone runner compiled 50 unchanged production source files and this one new test.
It did not run Gradle/Android instrumentation or the full application regression suite.
Equivalent module test command, once the Android toolchain is available:

```sh
./gradlew :extractor-master-android:testDebugUnitTest \
  --tests com.alal.yft.extractor.master.android.MasterMainSelectionContractTest
```

JUnit result: `Tests run: 12, Failures: 7`; exit 1 is intentional for this red checkpoint.
Do not describe this checkpoint as green CI or as the completed fix.

Fresh ancillary checks: JS transport 11/11 and repository script tests 30/30 passed.
Exact two-file allowlist, Kotlin lines at most 100 characters and the local equivalent
secret/sensitive-path scan passed. The 50 compiled production-source hashes and the capture
JS hash match the unchanged baseline. No GitHub Advanced Security scan or Android build is
claimed by these local checks.

### Confirmed production boundary

The requested full behavior cannot currently be wired through the Android capture module's
public contract alone:

- `CaptureFrame.kt` does not retain the player's duration or decoded dimensions yet. This
  part can be changed inside the allowed module.
- `WebViewPlaybackCapture` returns only `PageSnapshot`. That contract has no explicit selected
  main/More result or duration/dimension selection policy.
- `MasterFallbackEngine.kt` owns a private `select`. It runs before media validation, so
  independently probed candidate durations never reach selection when the player uses a blob
  URL. It accepts an exact HTTPS playing-address match or one explicit MAIN group; otherwise
  it produces `NeedsSelection`. There is no injected selection hook.
- The engine probes and returns only selected candidates; unselected alternatives are not
  passed through as More.
- `BrowserMasterFallback.kt` applies the caller's content-identity filter.
- `BrowserViewModel.kt`'s generic Master path requires `groups.singleOrNull()` and selects the
  group without an other-video count. Returning several legitimate video groups alone does
  not implement the requested main/More sheet.

Rewriting a blob address as a supposedly observed HTTPS playing URL, inventing page-owned
MAIN evidence or assigning different posts one fabricated identity would bypass these checks
instead of implementing the requested policy. Those workarounds were not used.

The smallest proposed scope clarification is permission for selection-hook/result-routing
edits in these three files, with the selection algorithm and new capture facts remaining in
`extractor-master-android`:

- `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/MasterFallbackEngine.kt`
- `app/src/main/java/com/alal/yft/detection/master/BrowserMasterFallback.kt`
- `app/src/main/java/com/alal/yft/feature/browser/BrowserViewModel.kt`

Existing reusable Generic-workflow rules were located: `BrowserObservationMapper.adRole`,
`MediaGroups.isPreviewAddress` and `PageVideoFacts.isFarShorter`. Reuse them without changing
their files. The requested duration-match tolerance remains exactly 2 seconds, rather than
the Generic long-video percentage tolerance.

### Live acceptance is still pending

No new live test is presented as a fix validation in this checkpoint. The latest previously
recorded native public-player diagnostics remain:

- ❌ Instagram: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.
- ❌ TikTok: real native Pause/Play, fresh progress and authorized capture, followed by
  `NEEDS_SELECTION`; no media probes or validated candidates.

The prior two-run Android gate passed with the same old APK pair. It does not validate a
future changed implementation. Default/release opt-out, 2.5-second capture timeout, independent
playback-progress authorization, DRM rejection and navigation-generation checks are unchanged.
Existing site extractors, Generic files, download code and CI workflows are untouched.

Next, after the boundary is clarified: make the red contracts pass through real production
wiring, rerun relevant guards/device checks, then perform native TikTok and Instagram live
validation. Completion requires logs showing automatic main selection without
`NEEDS_SELECTION` and actual main/More routing. TLS/media-byte, full download/mux, all
resolutions, Master CI green and merge readiness remain unverified.