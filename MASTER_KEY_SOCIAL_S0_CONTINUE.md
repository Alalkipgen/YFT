# Continue the social plan (after S0d)

You continue YFT's Master Extractor work on branch `spike/master-extractor-backup` only.
Read first: `AGENTS.md`, `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md` (working rules),
`MASTER_KEY_SOCIAL_PLAN.md` §7.1 (S0 as built), §7.2 (the owner's run) and §8, the top entries
of `PROGRESS.md`.
Reply to the owner in Burmese (English technical terms), short, outcome first.

## Rules (unchanged)

- Push only the spike branch. No merge to `main`, no release, no tag.
- One sub-step per commit; tests with each step (never weaken or delete a test); docs in the same
  commit (`PROGRESS.md` top entry, the §7.1 table row status, `docs/TEST_MATRIX.md` row,
  `docs/SUPPORT_MATRIX.md` only if site behaviour changes).
- Wait for CI green before the next push. STOP and ask the owner if CI fails twice on the same
  step, or if docs and code disagree.
- Locked files are never edited: the 5 copied YouTube files, `ShapeLocator`, `ShapeNormalizer`,
  `Rungs`; avoid editing `RouteBLookup` / `RouteBHttpClient`. `extractor-sites` (main) is untouched.
- Code copied from main gets a provenance header and rows in
  `extractor-master/toolkit-provenance.tsv` (`python3 scripts/master-toolkit-drift.py --write`);
  self-check with `--ref 34a4189002b7c9aaf6085ae4adab27b343ca28dc` (0 drifted). Old rows unchanged.
- Privacy (S8): no post IDs, links, titles or user names in docs, logs, reports or exports. Test
  links are entered on the owner's phone only.

## Done

- S0a (2026-10-20): `lookup/AskLabels`, labels in `LookupRecord` (way, reading, shape, repeated,
  after-stop, bot checks, stopped hosts; a record given none is today's), `social/SiteWays`,
  `SiteLabels`, `SiteReadings` (41 slots: FB 13, TikTok 10, IG 10, X 8), tests
  `LookupRecordLabelsTest` (11), `SiteWaysTest` (13), `SiteWaysDriftTest` (7).
- S0b (2026-10-20): `social/SiteRowCheck` (V), `social/SiteCensus` (`LinkIdentity`, `MainAnswer`,
  `MainLookup`, the engine) and `social/SiteCensusReport` (rows, each site's number, the report,
  the JSON export, the state, the privacy guard); tests `SiteRowCheckTest` (8), `SiteCensusTest`
  (16). Nothing copied from main.
- S0c (2026-10-20): debug `SiteCensusActivity` ("YFT site census", `android:enabled="${yftOwnerTools}"`,
  taskAffinity `com.alal.yft.ownertools`): a link field per slot (the screen's own store, prefs
  `site-census`), "Run <site>" / "Run all sites", "Copy report" / "Copy JSON", the state file
  `files/master/site-census-last.json`, the status line `SiteCensus.statusOf`. `SiteCensusLookup`
  (the screen's `MainLookup`): `SiteAdapterCoordinator(registry, DeviceMergeSupport(),
  SiteLookupCache(), the hidden page with its mark)`, `inspect(fresh = true)`; identity = the copied
  `WebViewAgent` + `CookieManager.getCookie(link)`. Clients: `OkHttpExtractorClient` over the app's
  `NetworkConfiguration` client with a byte meter, `Policy(callTimeoutSeconds = 60,
  retryDelaysMillis = emptyList())`, main's through `LookupRecorder` (TikTok's with
  `sendResponseCookies = true`). Tests `SiteCensusLookupTest` (5), `CensusNoBackgroundTest` +1.
  First check that the S0c commit's CI is green.
- S0d (2026-10-20): the owner's two runs (run 1 all four sites through a VPN with a US exit; run 2
  Facebook, Instagram, X without it), recorded in the plan §7.2, §8 items 1–3 and `PROGRESS.md`.
  Shares: Facebook 0/11, TikTok 0/6, Instagram 0/7, X 2/6 (videos only in X's card). No code
  changed.

## Next

- **The owner's answer on §8 item 1** (the pilot and the order). Recommended (§7.2): the pilot
  Facebook, then TikTok, X, Instagram (Instagram after item 3, `doc_id`). Also asked: a small fix
  in main's X reader (the card's videos), only on the owner's go.
- **S1** after the answer: the kit, built with the pilot site only (§7; §3.2's counterparts that
  site needs). For Facebook, S2a's known gaps (§7.2 finding 3): the links main resolves first,
  the access check by wording, the plugin player's empty answer.
- A census run again (the same screen; the links stay on the phone): TikTok needs the owner's VPN
  on the owner's network. A site stopped by a 429 or a bot check runs again only after its 30
  minutes (the status line says how long).

## Report to the owner after S0c

In Burmese: the defaults (browser identity, no retry, Master's trial, budgets and stops) and ask
§8 item 3 (are Instagram's `doc_id` sources measured in S0 or not). Phase 1.3 backlog item 6 (live
WebM) stays parked until needed.

## Report to the owner after S0d

In Burmese: the commit and its CI; the shares per site (both runs); X's two card posts (Master's
rows were HLS, so V unknown); ask §8 item 1 (the pilot and the order) and the X reader fix (go or
not); §8 item 3 still open.
