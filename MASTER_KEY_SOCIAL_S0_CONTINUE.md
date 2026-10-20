# Continue the social plan's S0 (after S0c)

You continue YFT's Master Extractor work on branch `spike/master-extractor-backup` only.
Read first: `AGENTS.md`, `MASTER_KEY_PHASE1_4_ROUTING_PROMPT.md` (working rules),
`MASTER_KEY_SOCIAL_PLAN.md` §7.1 (S0 as built) and §8, the top entries of `PROGRESS.md`.
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

## Next

- **S0d** — the owner's phone run: the owner installs the APK of the S0c commit's "Master opt-in
  debug APK (spike)" run, opens "YFT site census", pastes public links into the slots (§8 item 6:
  the owner picks them; they stay on the phone, never in docs), taps "Run <site>" or "Run all
  sites", keeps the screen open until the end, then sends the "Copy report" text (and "Copy JSON").
  Record each site's number and the shares in §7.1 and `PROGRESS.md` (no link, ID or name); then
  the pilot and the order (§8 item 1). A site stopped by a 429 or a bot check runs again only
  after its 30 minutes (the status line says how long).

## Report to the owner after S0c

In Burmese: the defaults (browser identity, no retry, Master's trial, budgets and stops) and ask
§8 item 3 (are Instagram's `doc_id` sources measured in S0 or not). Phase 1.3 backlog item 6 (live
WebM) stays parked until needed.
