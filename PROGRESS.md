# YFT Master Extractor backup

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