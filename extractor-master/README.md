# extractor-master

Master, YFT's backup engine, in plain Kotlin (JVM). It looks for a video only after main's site
adapter failed for a reason that can recover, and only while Settings → Backup finder is on. Its
Android parts are in [extractor-master-android](../extractor-master-android/README.md). The app side
is in `app/src/main/java/com/alal/yft/detection/master/`. Names follow
[GLOSSARY.md](../docs/GLOSSARY.md); the big picture is in [ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## Dependencies

- `:extractor-api`, `:extractor-generic`, kotlinx coroutines and OkHttp.
- `:extractor-sites` in tests only, to compare the Master YouTube module with main's adapter.
- Code shared with main is copied into `toolkit/` and `modules/youtube/`. `toolkit-provenance.tsv`
  lists each copy with its source and hash. `python3 scripts/master-toolkit-drift.py` reports copies
  whose original changed on `origin/main`; `--write` records new hashes after a review.

## How a lookup runs

`MasterFallbackEngine` is the entry point. It is not a site adapter and never calls the adapter that
failed.

1. `TerminalRules` decide whether Master may run. A network failure or a rate limit starts nothing.
2. A page a Master module claims (the Master YouTube module) is answered by that module alone.
3. Else the site contract reader asks the video's own public player endpoint once.
4. The layers read the evidence: browser capture, the contract answer, recipes and shape search.
5. `CandidateGate` and `FocusSelection` keep only the focused video's files; `ProbeSession` checks
   them. Browser capture is asked at most once per lookup.
6. The answer is an ordinary `SiteExtractionResult` with `MediaCandidate` rows.

The app starts the YouTube backup route and the social ladders; their steps live here.

## Packages

| Package | What it holds |
| --- | --- |
| (root) | `MasterFallbackEngine`; `MasterContracts`: the request, policy and result types |
| `capture` | `InMemoryCaptureStore`: per tab, in memory, what the tab requested and played |
| `contract` | Site contract reader (`SiteContracts`, `WidgetToken`) |
| `layers` | `CaptureLayer`, `ContractLayer`, `RecipeLayer`, `ShapeLayer` |
| `lookup` | Lookup record (`LookupRecord`, `LookupRecorder`, `LookupAsk`); the YouTube backup route's client (`RouteB*`) |
| `modules` | Master YouTube module (`modules/youtube/`); YouTube ladder (`modules/youtube/ladder/`: clients, health, answer judge, HLS rung, shape reader, client census) |
| `parity` | Master parity check: report, URL list, verdicts |
| `policy` | `TerminalRules` (when Master stops), `SnapshotBudget` (read limits) |
| `present` | `MasterMainPresentation`: the main video and the others for the Download sheet |
| `recipes` | Recipe reader data; signed recipe config (`RemoteRecipes`, `RemoteRecipeConfig2`, `RemoteRecipeSignature`) |
| `social` | Social ladders, answer judges, social limits, row checks, weak-answer fill, site census, weekly site check |
| `solver` | YouTube own solver and own-solver ladder (breaker, locators, protocol) |
| `toolkit` | Master toolkit: the copies from main |
| `verify` | Media checks: `OkHttpMediaValidator`, `ProbeSession`, `CandidateGate`, media fingerprint |

## Limits

- `MasterPolicy` is off by default; the app turns it on.
- One snapshot: 2 MiB of text, 200 request records, 16 data documents.
- At most 16 rows checked per lookup. The engine has 20 s; the contract ask gets 6 s of it.
- A media check reads 512 bytes of a file or 256 KiB of a manifest, within 10 s. HTTPS only, no
  cookie jar, a redirect limit, and no credentials after leaving the origin.
- Nothing bypasses a sign-in, an age check or DRM.

## Recipes

- `recipes/master-recipes.schema.json` (schema 1, read by 1.0.0) and `master-recipes-2.schema.json`
  (schema 2, adds VK; read by 1.0.1 and later), each with an example file.
- `recipes/master-recipes.pub.hex`: the public key the apps check the signature with.
- `recipes/configs/defaults.json`: the config the Recipe publish workflow signs. One config makes
  both files.
- `recipes/configs/drill-*.json`: drills for the publish path (a renamed key, a step turned off,
  VK's value heal turned off). Publish `defaults` again after a drill.

How to change and publish a config: [TESTING.md](../docs/TESTING.md), "Two kinds of fix".

## Tests

`./gradlew :extractor-master:test` (JDK 17). The tests read main's site fixtures, the parity URL
list, `src/test/resources/parity/fixture-baseline.json` and `recipes/`. Owner switches, never in CI:

- `-Pyft.youtubeCanary=<video id>`: the Master YouTube canary asks YouTube live
  (`scripts/master-youtube-canary.sh`).
- `-Pyft.parityBaseline=write`: rewrites the parity baseline on purpose.
- `YFT_MASTER_SMOKE_SNAPSHOT=<file>`: runs the smoke test on a real capture snapshot.
