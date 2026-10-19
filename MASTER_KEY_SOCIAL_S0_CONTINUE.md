# Continue the social plan's S0 (after S0a)

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
  `LookupRecordLabelsTest` (11), `SiteWaysTest` (13), `SiteWaysDriftTest` (7). First check that
  the S0a commit's CI is green.

## Next

- **S0b** — `SiteRowCheck` (V): a one-byte probe with `candidate.requestContext.replayHeaders()`,
  only to the site's media hosts (`Site.mediaHost`); HLS / other → unknown; DASH → its video file.
  `SiteCensus(main: MainLookup, masterHttp, validator, meter, clock)` with
  `MainLookup { identify(link); suspend lookup(link, identity): MainAnswer }`
  (`Found` / `Failed` / `NotHandled`, plus the hidden page label). Per row:
  `LookupRecord(labels = site.labels())`; main inside `withContext(record)`; Master via
  `MasterFallbackEngine(validator, NoPlaybackCaptureProvider, MasterPolicy(enabled = true,
  contractTimeoutMillis = 6 s, timeoutMillis = 20 s), modules = emptyList(),
  contracts = SiteContracts(RouteBHttpClient(masterHttp)))` inside `withContext(RouteBLookup(record))`;
  Master's rules and the marked trial as in §7.1; judge classes recoverable / browser / final against
  the slot's `Expect`. Stops: a 429 or a bot check stops the site for the run and 30 minutes (state
  file `files/master/site-census-last.json`); at most 40 MB and 150 requests per site. JSON report
  (reuse the internal `CensusJson.quote`); the sanitizer masks addresses and given values. JVM tests
  with fake sites.
- **S0c** — debug `SiteCensusActivity` like `ClientCensusActivity`; `app/src/debug/AndroidManifest.xml`
  label "YFT site census", `android:enabled="${yftOwnerTools}"`, taskAffinity
  `com.alal.yft.ownertools`. Clients: `OkHttpExtractorClient(NetworkConfiguration.createClient()
  .newBuilder().eventListener(meter).build(), Policy(callTimeoutSeconds = 60,
  retryDelaysMillis = emptyList()))` wrapped in `LookupRecorder` (TikTok's own client with
  `sendResponseCookies = true`); the copied WebView agent; `SiteAdapterCoordinator(registry,
  DeviceMergeSupport(), SiteLookupCache(), recording hidden-page wrapper)`, `inspect(fresh = true)`;
  identity = the WebView user agent + `CookieManager.getCookie(link)`. Docs, CI green, APK.
- **S0d** — the owner's phone run; then the report and the pilot (§8 item 1).

## Report to the owner after S0c

In Burmese: the defaults (browser identity, no retry, Master's trial, budgets and stops) and ask
§8 item 3 (are Instagram's `doc_id` sources measured in S0 or not). Phase 1.3 backlog item 6 (live
WebM) stays parked until needed.
