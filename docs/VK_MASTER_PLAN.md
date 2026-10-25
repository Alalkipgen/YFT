# VK 18+ — the Master "next level" (layer by layer), for v1.0.1

Status: planning (the owner's go, 2026-10-25). Base commit `50fb8d0f`
(`spike/master-extractor-backup` after VK4's merge and the Master baseline fix). Nothing is
built yet; this file is docs only.

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

VK5 (path D) က 18+ public video တွေကို v1.0.0 မှာ ဖြေပေးပြီးပါပြီ။ ဒါပေမဲ့ ယူလို့ရတဲ့လမ်းက တစ်ကြောင်းတည်းမို့
VK က သူ့ web client ကို ပြောင်းလိုက်ရင် ချက်ချင်း ရပ်သွားပါတယ်။ ဒီ plan က အဲဒီ 18+ ကို YouTube/Facebook/TikTok/X/
Instagram လိုပဲ **Master ရဲ့ site pack ၅ ခုမြောက်** အဖြစ် layer အဆင့်ဆင့် ခံအောင် လုပ်မယ့် အစီအစဉ်ပါ။

- **Main VK (VK1–VK3, VK5) ကို လုံးဝ မထိပါဘူး။** Master က main ပျက်မှ (adult message ပြီး D မအောင်မှ) ဝင်ပါတယ်
  (routing B)။ Main အောင်ရင် Master မဝင်လို့ ယခု PASS ဖြစ်ပြီးသား VK5 ကို regression မဖြစ်စေပါဘူး။
- **Layer:** V0 (main D) → V1 (main ဖတ်ပြီးသား answer တွေကို Master ရဲ့ parser/judge နဲ့ ပြန်ဖတ်) →
  V2 (web client ရဲ့ value ပျက်ရင် vkvideo.ru ရဲ့ public script ကနေ ပြန်ယူ — self-heal) → V3 (user ကိုယ်တိုင်
  play တာကို browser မှာ capture)။ Vx (နောက်လမ်းကြောင်း) က A က independent ဖြစ်ကြောင်း သက်သေပြမှ။
- **v1.0.0 ထဲ မထည့်ပါ** (decision 30: release commit မှာ pack/engine/main မပြောင်းရ)။ **v1.0.1 မှာ ထည့်ပါမယ်။**
- **အခု wave 1 agent ၃ ယောက်:** A (recon/census — docs + fixture ပဲ)၊ B (VK pack code — app နဲ့ မချိတ်သေး)၊
  C (kit: self-heal + R9 schema 2)။ App ထဲ ချိတ်တာ (D) နဲ့ 403 (E) က နောက်မှ။
- **ဖုန်းစမ်းသပ်မှု:** B0 → trial APK proof → S5 ၃ ကြိမ် (decision 26) → v1.0.1 preview ၂ ခု + signed install/upgrade။

## 1. Scope and relation to what exists

This is the "VK 18+ as a Master site pack" build the owner chose on 2026-10-25 (`docs/VK_PLAN.md`
§13; `MASTER_KEY_SOCIAL_PLAN.md` §8 decisions 7, 26, 30). It reuses the social-ladder machinery the
four sites already pass S0–S5 on (judge, request recorder, budgets, cooldown, row check V, capture,
census, W, R9), adds one VK pack to it, and adds the one piece the four sites never needed: a
self-heal of the web client's values. It is the long-life answer to VK5's single risk: VK rotating
`VkWebClientProfile`'s identifiers, the token flow or the API version (`docs/VK_PLAN.md` §13.5).

In scope: a `vk` site pack inside the Master engine (`extractor-master`), its judge, its ladder
rungs V1–V3, the self-heal kit, VK's R9 (two files), VK census slots and W, the phone proof.

Out of scope here (own tracks, own prompts): wiring the pack into the app (wave 2, agent D); the
403 work for every site after a download starts (track E, after v1.0.0); a second independent VK
way Vx (only if A's recon proves one exists, on the owner's go).

Not touched: main's VK adapter (`extractor-sites/.../vk/*`), main's four adapters, any Master
YouTube file (`modules/youtube/*`, the own solver, the shape locks).

## 2. The owner's decisions recorded (2026-10-25)

The owner said "ဟုတ်ပြီ … VK Master Plan & Prompt ပါ" — taken as yes to the five questions put to
him the same day (the social plan's answer, §5 of that reply):

1. **VK 18+ is the Master engine's fifth site pack**, not a Master-in-VK and not a split-out of
   Master's code. Main's VK adapter is untouched (routing B: Master acts only after main fails).
2. **v1.0.0 stays as frozen** (decision 30: the release commit changes no site pack, engine or main
   lookup). VK Master ships in **v1.0.1**.
3. **Self-heal reads the live vkvideo.ru public script** (S5's rule), the source A proves in recon
   (§8, §10); the source is used only after A proves it and the owner says go (S10).
4. **Wave 1 is agents A, B and C now** (recon, pack, kit), in parallel.
5. **403 is its own track E for every site, after v1.0.0** (not VK-only, not in this pack).

Also noted, not yet decided: whether VK's R9 config should carry the web client's values (client
id, the public client secret, app id, API version) as strict pattern-matched data (S9, like
Instagram's `doc_id`), so a value rotation can be fixed without an APK. Self-heal (§8) is the
primary path for those values; an R9-carried copy is an optional second path that needs a schema
field and that the owner may refuse (a signed config carrying a credential vs. reading it live).
C scopes it (§9); the owner decides before it is built.

## 3. Routing and where Master runs

- **V0 is main.** VK5's order holds: embed on `vk.com` (B), the same on a shell, then `vk.ru` (B2);
  on the adult message, main's path D (token on `login.vk.ru`, `video.get` on `api.vkvideo.ru`).
  Master's pack claims a lookup only when main's VK answer is a non-final failure — exactly route B
  (`MASTER_KEY_SOCIAL_PLAN.md` §4 L0; the four packs already work this way, `SocialLadder.stage`).
- **Only in the YFT browser.** Master runs where the four packs run: `BrowserViewModel` calls
  `SocialLadder` after a browser lookup fails; the Home screen does not. With the release's "Backup
  finder" switch off (decision 30 (i)), no lookup asks Master at all and VK keeps D only.
- A lookup main answers is never slower and sends no more requests than today (decision 30): Master
  reads main's own pages first (V1) before anything new.

## 4. The rungs (layer by layer)

| Rung | What it does | New request? | Starts on |
| --- | --- | --- | --- |
| **V0** main | VK5 path D (embed → token → `video.get`) | main's | — (unchanged) |
| **V1** re-read | Master's own parser + judge over main's kept pages (the embed pages and D's `video.get` answer in the lookup's record), bound to the post's `owner_id` + video id; VK's key table then the L3 shape scan | none | always, when it claims |
| **V2** heal | after the adult answer **and** a value-rejected signal: one healed token (memory only) + one `video.get`, with values from the ValueSource order (§8) | ≤ 2 (one token, one `video.get`) | the owner's go; A's source proof |
| **V3** capture | the user plays the video in the visible YFT browser; Master reads the playing media (F3/T3-style) | none by Master | always (decision 30 carries capture) |
| **Vx** second way | a second independent lookup route, only if A proves one exists that does not share V2's token endpoint or API | its own | A's independence proof + the owner's go |

Rung rules (from the four packs, `SocialStage`, `RouteBLookup`):

- **V1** never sends a request. It re-reads what main already fetched this lookup, kept in
  `MainPages` (at most 4 pages, 8 MB; the page body, its final address and whether main's request
  carried the user's credentials — never a cookie the answer set, a request header, a body or the
  token). Master re-parses those with its own value-shape scan bound to `owner_id_videoid`, applies
  V (§7) to each row, and never offers the trailer or the poster (`iv.okcdn.ru`). If V1 finds the
  same rows main's D already returned, nothing changes (main's success stands).
- **V2** is the only rung that heals. It runs only after main's adult answer *and* a value-rejected
  class (§5: the token endpoint refused, or `video.get` answered "auth failed"/"version"). Then,
  in the ValueSource order (§8): an R9-signed value if present → a self-healed value from today's
  vkvideo.ru script → the bundled alternates (source + date). It asks one token
  (`login.vk.ru`, memory only, `toString` hides it) and one `video.get` (`api.vkvideo.ru`), with
  the lookup's own user agent and no cookie. It learns (keeps) a value only after an OK answer and a
  V pass, at most once a day per value, never invented or counted up or brute-forced, and never
  after a bot check (error 14) or a 429 (then the host cools 30 min). The script is read within the
  lookup if the budget allows, else fetched in an isolated reader and the next lookup uses it.
- **V3** is the capture the four sites already use (decision 30 carries it in the release). Only the
  user answers a captcha or an age gate, in the visible browser (ADR-006, S3); Master never bypasses
  one. The `RungsOff` capture rule applies (a signed config may turn it off).

## 5. The judge — `VkAnswerJudge` → `AnswerClass`

One small judge, like `FacebookAnswerJudge`/`XAnswerJudge`, mapping VK's answers to the shared
`AnswerClass` (`social/SiteAnswerJudge.kt`). It reads only; it sends no request and logs nothing.
Conservative: a final answer only on positive, language-free evidence.

| VK signal | Class | Next |
| --- | --- | --- |
| rows found (key table or shape scan), bound to the post | `DIRECT` / `PARTIAL` | check rows (V) |
| token endpoint refused, or `video.get` says auth failed / bad version (a stale web client) | `VALUE_REJECTED` | next rung (V2) |
| `video.get` shape not as expected (fields moved) | `SHAPE_CHANGED` | next rung |
| API error 14 (a captcha) or a bot check on `login.vk.ru` | `BOT_CHECK` | stop the site + cooldown |
| rate limited (the codes A confirms, e.g. 429 / API 6, 9, 29) | `RATE_LIMITED` | stop the site + cooldown |
| `restriction.can_play = 0` with the post's own "deleted"/"removed" evidence | `REMOVED` / `PRIVATE` | final (VK's "isn't available") |
| an age gate that only a signed-in session passes | `AGE_LIMITED` → browser | the user's browser (ADR-006, no bypass) |
| a network failure or another status | `TRANSIENT` | next rung |

The exact rate-limit and restriction shapes come from A's error-taxonomy recon (§10); until A
confirms them, the judge treats an unknown error as `TRANSIENT` (never final). The adult message
itself is V0's trigger (main's), not a Master class.

## 6. Budgets and stop rules

VK gets a `SocialBudget` (like `SocialStops.kt`: Facebook 4 req / 8 MB / 20 s, TikTok 20 / 4 MB /
30 s, X 9 / 2 MB / 20 s, Instagram 13 / 7 MB / 30 s). VK's V-rungs are few (V1 no request, V2 ≤ 2,
the script read 1), so VK's budget is small; A proposes the numbers from the recon (token + one
`video.get` + one script read, each well under VK5's 15 s). The per-lookup request recorder
(main's asks and Master's, memory only) enforces: no second ask of an endpoint already asked this
lookup; after a 429 or a bot check (seen on any host, main's or Master's), no new request to that
host this lookup and a 30-minute cooldown (`SocialCooldown`); a host stop; the site cooldown.

## 7. Row check V — `SiteRowCheck` with VK's hosts

Every row Master offers passes V (`social/SiteRowCheck.kt`, the shared rules: Range ≤ 64 KiB, the
download's own headers, only the host the answer gave, the init check on the same bytes, `unknown`
is never a pass, an HLS row only with a known duration). VK's `Site` (B's own file; D adds it to the site table) lists VK's
media hosts: `vk{N}-{N}.vkuser.net` and `vkvd{N}.okcdn.ru` (the failover), and **never**
`iv.okcdn.ru` (the poster/trailer host). The expiry window A measured is 72–127 h. The row's media
request uses the lookup's own user agent (VK's files bind `srcAg`: a file asked with one agent
answers 400 to another), which the download then sends, with the canonical `vk.com` page as
`Referer` and no cookie — exactly V0's media rule.

## 8. Self-heal — `ValueSource`, `SiteHealth`, `VkClientSource`

The kit the four sites deferred to S1b (`MASTER_KEY_SOCIAL_PLAN.md` §3.2, L6; built here first,
reusable later for Instagram's `doc_id`):

- **`ValueSource`** (new, `social/ValueSource.kt`): the S5 order for one named value — an R9-signed
  value, else a self-healed value read today, else the bundled alternates (each with its source and
  date). One pattern-matched value per kind, never code; at most once a day per value; kept only
  after an OK answer and a V pass; never invented, counted up or brute-forced.
- **`SiteHealth`** (new, `social/SiteHealth.kt`, like `ClientHealth`): per site and rung — the last
  class, the last pass, the cooldown, the last good rung, the last learned value — stored as one
  JSON text the same way YouTube's health is (`files/master/site-health.json`, data only, found on
  first use off the lookup thread). It records **never** a token, an address or an id: a learned
  value is kept by a strict pattern and its source + date, not its secret bytes.
- **`VkClientSource`** (new, `social/VkClientSource.kt`, **part 2**, built only after A proves the
  script source and the owner says go): reads one value from today's vkvideo.ru public script by a
  strict pattern (the pattern A gives), for V2's heal. Until then B's V2 uses the bundled values
  only, behind a debug mutate hook so the phone proof can force a value-rejected path.

The web client's identifiers themselves (client id, the public client secret, app id, scopes,
token version, API version, the two hosts) are copied into the Master module once, with provenance
(§11, B), because main's `VkWebClientProfile` is `internal` to `extractor-sites` and cannot be
imported. They are the bundled defaults; self-heal replaces a rotated one at runtime (memory only).

## 9. R9 for VK — two files, schema 2

Why two files: the shipped v1.0.0 reader (`recipes/RemoteRecipeConfig.kt`, `SCHEMA = 1`) rejects a
whole config when it names an unknown site (`reject("unknown site …")`) or an unknown rung
(`RUNGS` = F1–F3, T1–T3, X1, X3, I2, probe, I3). So the moment a published config mentions `vk` or
`V1`/`V2`/`V3`, every v1.0.0 phone would reject it and lose the four sites' R9 fixes and kill
switches in that same config. The config is kept in memory only (no file on disk): a reject
leaves the phone on its last config until the app restarts, then on the bundled recipes only — a
silent regression for anyone still on v1.0.0.

The fix (C, additive; the schema-1 reader is **not** changed):

- A **schema 2** reader (a new class/file; the schema-1 reader and `ContractRecipes.ALL` stay as
  they are) that knows site `vk` and rungs `V1`, `V2`, `V3`, plus the four sites and their rungs.
- `extractor-master/recipes/master-recipes-2.schema.json` (new), the schema-2 JSON schema.
- `scripts/master-recipes-publish.py` + `.github/workflows/master-recipes-publish.yml`: from **one**
  committed schema-2 config, publish **both** signed files on the `recipes` branch — `master-recipes.json`
  (schema 1, `vk` stripped: identical in shape to today's four-site file, for v1.0.0) and
  `master-recipes-2.json` (schema 2, all five, for v1.0.1). Same key (decision 5), both verified.
- The v1.0.1 app reads the schema-2 URL (Gradle `yft.masterRecipeUrl`, the wiring is D's). v1.0.0
  keeps reading the schema-1 URL and keeps getting four-site fixes.

What VK's R9 may carry: the kill switches (`rungsOff` V1/V2/V3) and the overlays R9 already supports
(headers, media paths, node fields). Whether it also carries the web client's values (§2, open) is
C's scoping question; it needs a schema field and strict patterns, and the owner decides. C never
runs the publish workflow, never pushes the `recipes` branch, and never adds a label — code and
tests only (`scripts/tests/test_master_recipes_publish.py`).

## 10. Early warning — census, W, optional canary

VK joins the site census (`social/SiteCensus*`, the debug screen `SiteCensusActivity`). A proposes
VK's slots (the owner's own public links, kept on the phone only, slots not links): three 18+ (one
with `list=`), two normal video, one clip, one removed, one private/sign-in-only, one live replay —
A finalizes the kinds from recon. W (the weekly check, `SiteWeekly.kt`) takes three of VK's slots,
≤ 1 min and ≤ 2 MB, with a CHANGED/BREAKING diff and a runbook step. B0 (VK's baseline) is the
census's first run of main's part on D's first trial APK. An optional daily CI canary is the
owner's call and only if A confirms a cloud IP behaves like a phone for VK (the sandbox is a US
datacenter; VK may answer it differently).

## 11. Files and ownership (no overlap)

Wave 1 (parallel, each its own branch off `work/phase-vk-master`, each its own `SESSION_STATE`
section, checkpoints with `scripts/checkpoint.sh`):

- **A — recon/census — `work/phase-vk-master-census` — docs + fixtures only, no code.**
  `docs/VK_MASTER_CENSUS.md`; `extractor-master/src/test/resources/fixtures/vk-master/**` (sanitized,
  made-up values, with a README). A answers the recon questions (below) and STOPs.
- **B — VK pack — `work/phase-vk-master-pack` — new Master code, not wired.**
  New `extractor-master/src/main/kotlin/com/alal/yft/extractor/master/social/Vk*.kt`: `VkLadder`
  (a `SocialStage`, templated on `XLadder`/`InstagramLadder`), `VkAnswerJudge`, `VkRecipe` (**not**
  added to `ContractRecipes.ALL` yet), `VkLabels` (an `AskLabels`), `VkClient` (the identifiers
  copied from main's `VkWebClientProfile`/`VkApiToken`/`VkUrls`, with provenance rows added to
  `extractor-master/toolkit-provenance.tsv` and passing `scripts/master-toolkit-drift.py`), and an
  interface `VkClientValues` (C implements the live source; B fakes it in tests). Tests `Vk*Test.kt`;
  fixtures `extractor-master/src/test/resources/fixtures/vk-pack/`. B's V2 uses bundled values plus a
  debug mutate hook; it does not call C's live source until C lands it.
- **C — kit + R9 schema 2 — `work/phase-vk-master-kit` — new/additive code.**
  New `social/ValueSource.kt`, `social/SiteHealth.kt`, and (part 2, after A + the owner's go)
  `social/VkClientSource.kt` implementing `VkClientValues`. R9 schema 2: a new schema-2 reader
  (the schema-1 `RemoteRecipeConfig.kt` unchanged), `recipes/master-recipes-2.schema.json`,
  `scripts/master-recipes-publish.py` and `.github/workflows/master-recipes-publish.yml` changes
  (publish both files), with script tests. YouTube files are locked (design reuse only).

Provenance note for B: `toolkit-provenance.tsv` has one `# base <sha>` line (today `34a41890`),
and the drift self-check (`--ref <base>`) must still pass. Main's VK files were added after that
base, so a naive VK row would fail "symbol not found at base". B must propose how to record VK
provenance without silently absorbing main's already-drifted X symbols (a base bump re-hashes
every symbol), and STOP for the owner if unsure rather than bumping the base on its own.

Shared files are for wave 2 (agent D) only — no wave-1 agent edits them: the app's
`SocialLadder.kt`, `SiteWays.kt`, `LadderRungs.kt`, `RungsOff.kt`, `SocialStops.kt`,
`SiteRowCheck.kt`, `SocialRowCheck.kt`, `SiteCensus*`, `SiteWeekly.kt`, `ContractRecipes.kt`, the
engine, the debug census screen, Gradle, the D7 matrix (`.github/d7-apks.txt`), and the shared docs
(`PROGRESS`, `CHANGELOG`, `docs/*MATRIX*`). A change one of them needs is a hand-off line in the
agent's `SESSION_STATE` section and its report, not an edit.

### A's recon questions (STOP, Burmese report)

1. **Independence of other ways.** Is there any VK lookup route that does **not** share V2's token
   endpoint (`login.vk.ru`) or `api.vkvideo.ru`? If yes, what and how stable — the basis for Vx.
2. **The script source.** Where is the web client's value in vkvideo.ru's public script (file,
   approximate size, the pattern around each value), and how stable across loads? Is yt-dlp (or a
   similar public raw file) a viable second source?
3. **Error taxonomy.** The token endpoint's and `video.get`'s error codes, and the restriction
   shapes (`can_play`, deleted, private, age, region, live), each with one plainly-wrong value per
   kind (once) — the judge's table (§5).
4. **Media, 403, srcIp, srcAg.** The media hosts and the file-expiry window; whether a file 403s by
   source IP (track E) or only by `srcAg`; the evidence labels for E.
5. **Budgets.** The request, byte and time numbers for VK's `SocialBudget` (§6).
6. **Census + W.** The slot kinds and the three W slots (§10).
7. **Sanitized fixtures.** The fixtures B and C need, with made-up values (never a real token, id,
   secret, link or body).

## 12. Phone testing (decision 26)

1. **B0** (~5 min): on D's first trial APK, the census's first run of main's VK part — VK's request
   count and time, the baseline nothing may exceed.
2. **Pack proof** (~30 min, the trial APK): main's rows set aside, Master answers every VK lookup;
   V1 reads main's pages, V2 via the debug mutate hook (a forced value-rejected path), V3 by playing
   the video; wrong rows 0; W's first VK run; the R9 drill with V2 off. Fixed before the merge.
3. **S5** (decision 26): per site, 3 runs, each ≥ 6 h after the previous, the first and last ≥ 24 h
   apart, counted only on the final code's APK — a change to VK's pack, the engine or main restarts
   VK's count. One session runs all five sites together (the shared-code changes restart the four
   sites' counts too, so all five re-pass in one census session; TikTok via the VPN, decision 25).
   Each run: wrong rows 0, no regression against each site's baseline.
4. **v1.0.1 release:** two previews (normal + trial) of one release commit, then the signed APK's
   install and upgrade on the owner's phone — as every release, VK or not.

A code change anywhere in the pack, the engine or main restarts the relevant counts (decision 26),
so the APK is built once CI is green and nothing is changed across the three S5 runs.

## 13. Steps and waves

- **Wave 1 (now):** A, B, C in parallel, each STOP + Burmese report. A's recon gates V2's live
  source and Vx. CI on a `work/**` push (checkpoint validation) does not run `:extractor-master:test`,
  the drift self-check or the recipe-script tests (the Master opt-in APK workflow runs only on the
  spike branch), so B and C run them locally before every checkpoint and report the results; they
  run in CI when the branches are merged.
- **Between waves:** the owner reviews the three reports; VK4 and the in-app-update are already on
  the release train for v1.0.0; v1.0.1 opens after PR #2's merge and P8.
- **Wave 2 (agent D, after PR #2's merge + P8):** branch `work/phase-vk-master-wiring` from `main`;
  merge A/B/C; add `VkRecipe` to the engine, register `VkLadder`, wire V, census, W, the Backup-
  finder gate, the config URL to the schema-2 file, the debug mutate hook; build the trial APK
  (main's VK answer set aside); docs. Its prompt follows the wave-1 reports.
- **Track E (403, after v1.0.0):** every site, core-download — a fresh lookup and restart, resume
  only when the file is provably the same. Its prompt follows P8.

## 14. Risks

- VK changes the script's structure → V2's self-heal needs a new pattern (R9 value or an APK), and
  V3 (capture) and V0 carry the lookup meanwhile.
- A new token flow or API shape → code, not data (a new build); the judge keeps it non-final.
- A captcha or a new age gate → the site stops; only the user answers it in the browser.
- More requests than VK5 → the budget and recorder cap it; B0 is the ceiling.
- The R9 two-file mistake (a v1.0.0 phone served a schema-2 file) → it rejects and keeps its last
  config; the publisher must always emit both, verified.
- S5 restarts on any shared-code change; keep wave-2 changes in one commit set.
- Home and Backup-finder-off leave VK with D only; capture is browser-only.
- A file 403 by source IP is track E, not this pack.
- ADR-006 terms: public videos only; no sign-in, no DRM, no captcha/age bypass.

## 15. Backlog

- Vx (a second independent way) if A finds one.
- VK's R9 carrying the web client's values (owner's decision).
- A daily CI canary for VK if a cloud IP behaves like a phone.

## Appendix A — prompt for agent A (recon / census)

```text
You are the VK-Master recon agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: make work/phase-vk-master-census from origin/work/phase-vk-master (its head: this plan's commit on 50fb8d0f). Work
only there. You write docs and test fixtures only — no Kotlin, no Gradle, no workflow.

Read first, in this order:
1. AGENTS.md — the repository's rules (checkpoints with scripts/checkpoint.sh; your own
   docs/SESSION_STATE.md section; Burmese reports; never commit a cookie, token, signed URL or
   response body).
2. docs/VK_MASTER_PLAN.md — this plan. Your part is section 11 ("A's recon questions") and the
   fixtures in section 11. Section 2's hard rules and ADR-006 come first.
3. docs/VK_PLAN.md section 13 (VK5 path D: what main already does) and
   docs/decisions/ADR-006-owner-override-any-working-method.md.
4. MASTER_KEY_SOCIAL_PLAN.md sections 2-6 and decisions 7, 26 and 30 (how the four sites' census,
   budgets and routing work), for the shape of your answers.

Task: recon only, then STOP. On live public vkvideo.ru pages, signed out, make only the kinds of
requests VK5's recon made (the page and its public script, the token, video.get, 1-byte range
checks) — never sign in, never solve a captcha or age gate, never download a whole video. Answer the seven questions in section 11: (1) is there any lookup
route independent of login.vk.ru and api.vkvideo.ru (the basis for Vx); (2) where the web client's
values sit in vkvideo.ru's public script (file, size, the pattern around each, how stable; is
yt-dlp or another public raw file a second source); (3) the token and video.get error codes and
the restriction shapes, each with one plainly-wrong value per kind tried once; (4) the media hosts
and file-expiry window, and whether a file 403s by source IP or only by srcAg (evidence labels for
track E); (5) the request/byte/time numbers for VK's budget; (6) the census slot kinds and three W
slots; (7) the sanitized fixtures B and C need. Write docs/VK_MASTER_CENSUS.md with the answers and
put the fixtures under extractor-master/src/test/resources/fixtures/vk-master/ with a README. Every
value in a fixture is made up (never a real token, id, secret, link, title or body); use slots and
short hashes, not links (S8). Checkpoint after the doc and after the fixtures. Then STOP and report
to the owner in Burmese, short, with your branch, the commit SHA and the CI result.

Never: push to main, spike/master-extractor-backup or another agent's branch; open a pull request;
add a label; run master-recipes-publish or touch the recipes branch; write any Kotlin, Gradle or
workflow file; edit a shared doc (PROGRESS, CHANGELOG, docs/*MATRIX*) or another agent's files;
sign in, solve a captcha or age gate, or bypass access or DRM; print or commit a cookie, token,
signed URL or response body; put a real link, id or title in a doc or fixture. Anything outside
this task is a backlog line in your SESSION_STATE section — not an edit.
```

## Appendix B — prompt for agent B (the VK pack)

```text
You are the VK-Master pack agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: make work/phase-vk-master-pack from origin/work/phase-vk-master (its head: this plan's commit on 50fb8d0f). Work only
there. You add new Master code; you do not wire it into the app and you do not edit shared files.

Read first, in this order:
1. AGENTS.md — the rules (checkpoints with scripts/checkpoint.sh; your own SESSION_STATE section;
   Burmese reports; never commit a secret).
2. docs/VK_MASTER_PLAN.md — this plan. Your part is the VK pack (sections 4, 5, 7, 11). Section 2's
   rules and ADR-006 come first.
3. The four packs as your template: extractor-master/src/main/kotlin/com/alal/yft/extractor/master/
   social/XLadder.kt and InstagramLadder.kt (a SocialStage), XAnswerJudge.kt and
   FacebookAnswerJudge.kt (a judge), SiteRowCheck.kt and SiteWays.kt (the Site table and V),
   SocialStops.kt (budgets and cooldown), AskLabels (extractor-master/.../lookup/AskLabels.kt).
4. Main's VK adapter you copy identifiers from (do not change it, do not import it; it is internal):
   extractor-sites/src/main/kotlin/com/alal/yft/extractor/sites/vk/VkWebClientProfile.kt,
   VkApiToken.kt, VkUrls.kt, VkExtractor.kt; and docs/VK_PLAN.md section 13.
5. extractor-master/toolkit-provenance.tsv and scripts/master-toolkit-drift.py (how copied symbols
   are recorded and checked).

Task, in this order, each a checkpoint:
1. VkClient: the web client's identifiers (client id, public client secret, app id, scopes, token
   version, API version 5.289, the two hosts, never iv.okcdn.ru), copied into a new Master file
   with provenance rows in toolkit-provenance.tsv. The provenance file has one "# base <sha>" line
   and the drift self-check (--ref that base) must pass; main's VK files were added after today's
   base, so propose how to record VK provenance without a base bump that would absorb main's
   already-drifted X symbols, and STOP for the owner if unsure. Define an interface VkClientValues
   (agent C implements the live source; you fake it in tests).
2. VkAnswerJudge over the shared AnswerClass (section 5's table): a final answer only on
   language-free positive evidence; an unknown error is TRANSIENT, never final. Reads only.
3. VkLadder (a SocialStage): V1 re-reads main's kept pages (MainPages) with a value-shape scan
   bound to owner_id_videoid, never the trailer or poster; V2 heals (one token in memory, one
   video.get) only after the adult answer and a VALUE_REJECTED class, using VkClientValues (bundled
   plus a debug mutate hook; do not call C's live source yet); V3 is the shared capture. Under
   RouteBLookup's rules (never the user's credentials, nothing new after a 429, no endpoint twice).
4. VkRecipe (the contract) — define it, but do NOT add it to ContractRecipes.ALL (that is wiring,
   agent D). VkLabels (an AskLabels). VK's budget (a SocialBudget value) and VK's Site (its
   media hosts for V) in your own Vk files — SocialStops.kt and SiteWays.kt are shared, agent D
   registers them; placeholder budget numbers from docs/VK_PLAN.md section 13, noted (A finalizes).
5. Tests Vk*Test.kt and fixtures under extractor-master/src/test/resources/fixtures/vk-pack/ (made-
   up values only). JVM only, no live network. Before every checkpoint run locally
   ./gradlew --no-daemon :extractor-master:test and python3 scripts/master-toolkit-drift.py --ref
   <the provenance base> (work-branch CI does not run them) and give the results in your report.
Then STOP and report in Burmese, short, with the branch, SHA, CI result and any hand-off.

Never: push to main, spike or another agent's branch; open a pull request; add a label; run
master-recipes-publish or touch the recipes branch; change main's VK adapter or any file outside
the Master social package; add VkRecipe to ContractRecipes.ALL or edit any shared file
(SocialLadder, SiteWays, ContractRecipes, the engine, Gradle, workflows, the census screen, the D7
matrix, shared docs); edit a Master YouTube file; sign in or bypass access, a captcha, an age gate
or DRM; print or commit a cookie, token, signed URL or response body. Out of scope -> a backlog
line in your SESSION_STATE section.
```

## Appendix C — prompt for agent C (the kit and R9 schema 2)

```text
You are the VK-Master kit agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: make work/phase-vk-master-kit from origin/work/phase-vk-master (its head: this plan's commit on 50fb8d0f). Work only
there. Your code is new and additive; you do not change the shipped schema-1 recipe reader and you
do not wire anything into the app.

Read first, in this order:
1. AGENTS.md — the rules (checkpoints with scripts/checkpoint.sh; your own SESSION_STATE section;
   Burmese reports; never commit a secret).
2. docs/VK_MASTER_PLAN.md — this plan. Your part is self-heal (section 8) and R9 schema 2 (section
   9). Section 2's rules (including the open question on R9-carried values) and ADR-006 come first.
3. The pieces you template on: extractor-master/.../modules/youtube/ladder/VersionSource.kt and
   ClientHealth.kt (the S5 value source and the health file, data only); MASTER_KEY_SOCIAL_PLAN.md
   section 3.2 (ValueSource/SiteHealth as the generalization of these) and guardrail S5.
4. R9 as it is: extractor-master/.../recipes/RemoteRecipeConfig.kt (SCHEMA = 1; it rejects an
   unknown site or rung — read lines 80-105), ContractRecipes.kt (ALL has no vk), RemoteRecipes.kt
   (memory only), scripts/master-recipes-publish.py, .github/workflows/master-recipes-publish.yml,
   scripts/tests/test_master_recipes_publish.py, extractor-master/recipes/configs/*.json and
   master-recipes.schema.json.

Task, each a checkpoint:
1. ValueSource (new social/ValueSource.kt): the S5 order for one named value — an R9-signed value,
   else a value self-healed today, else the bundled alternates with source and date; one pattern-
   matched value per kind, once a day, kept only after an OK answer and a V pass; never invented,
   counted up or brute-forced. SiteHealth (new social/SiteHealth.kt, like ClientHealth): per site
   and rung, one JSON text through an OwnSolverTextFile like ClientHealth (the app's
   files/master/site-health.json is agent D's wiring), data only — never a token, address or id.
2. R9 schema 2, additive: a new schema-2 reader (do NOT change RemoteRecipeConfig.kt; the schema-1
   reader stays exactly as shipped) that knows site vk and rungs V1, V2, V3 plus the four sites;
   extractor-master/recipes/master-recipes-2.schema.json; make scripts/master-recipes-publish.py
   and the workflow publish BOTH files from one committed schema-2 config — master-recipes.json
   (schema 1, vk stripped, identical in shape to today's four-site file) and master-recipes-2.json
   (schema 2, all five), both signed with the same key and verified. Add script tests. Do NOT run
   the publish workflow, push the recipes branch, or add a label.
3. Scope the open question (section 2/9): can VK's R9 carry the web client's values as strict
   pattern-matched data, and at what schema cost? Write your recommendation in your SESSION_STATE
   section and the report; do not build it — the owner decides.
4. VkClientSource (new social/VkClientSource.kt, implementing agent B's VkClientValues) — PART 2,
   build only after agent A's recon proves the script source and the owner says go. If A has not
   reported yet, STOP after steps 1-3 and say so.
Before every checkpoint run locally ./gradlew --no-daemon :extractor-master:test and
python3 -m unittest discover -s scripts/tests -p 'test_master_recipes_publish.py' (work-branch CI
does not run them) and give the results. Then STOP and report in Burmese, short, with the branch, SHA, CI result and any hand-off.

Never: push to main, spike or another agent's branch; open a pull request; add a label; run
master-recipes-publish or touch the recipes branch; change RemoteRecipeConfig.kt (the schema-1
reader) or ContractRecipes.ALL; wire anything into the app or edit a shared app file, the engine,
the census screen, the D7 matrix or a shared doc; edit a Master YouTube file; build VkClientSource
before A's proof and the owner's go; sign in or bypass access, a captcha, an age gate or DRM;
print or commit a cookie, token, signed URL or response body. Out of scope -> a backlog line in
your SESSION_STATE section.
```
