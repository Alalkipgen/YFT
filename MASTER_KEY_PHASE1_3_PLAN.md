# Master Key — Phase 1.3 Plan v2: own client ladder at own-solver level (YouTube may change visionOS, Master keeps working)

Status: **plan + prompt, v2.** No code is changed by this document. v2 replaces v1 (2026-10-11,
kept in this file's git history); every v1 guardrail stays unless §2 marks it **changed**.
Date: 2026-10-14. Branch: `spike/master-extractor-backup` only.
Reads: `MASTER_KEY_PHASE1_2_PLAN.md` §0–§4 (the level to match), `PROGRESS.md` "R6", "R9",
"Phase 1.2 — N2" and "Phase 1.2 — N7", `MASTER_KEY_NOTES.md` §3–§4,
`docs/YOUTUBE_RISK_REVIEW.md` (P14, P22). Prompt: `MASTER_KEY_PHASE1_3_PROMPT.md` (v2).

Phase 1.2 made the own solver survive YouTube's code changes without ejs: it finds n/sig again
**in the same player** (B, E), asks **YouTube itself** (V), **remembers** what worked (locator
cache) and **proves** it on real players (M1–M11, wrong = 0). v2 brings the client side to the
same level. When YouTube changes visionOS, Master:

- first **adapts visionOS itself** to what its answer now needs: another public version, the
  player script through the own solver, sizes from YouTube's own probe, or the HLS delivery the
  same answer already carries;
- uses other no-token clients **only where a live census on the phone proved them**;
- **remembers** the working variant, so the normal lookup stays one request after a change;
- never offers a row that YouTube refused, whoever produced it.

## မြန်မာ အနှစ်ချုပ်

- ရည်မှန်းချက် (မပြောင်း): own solver ကြောင့် ejs ပျက်လည်း Master မပျက်သလို YouTube က visionOS ကို
  ပြောင်းလည်း Master မပျက်အောင်။ APK update မလိုဘဲ၊ မှားတဲ့ row လုံးဝ မပေးဘဲ။
- v1 ရဲ့ အားနည်းချက်များ (§0):
  - ဒုတိယ ခြေထောက် (TV, embedded) က ဒီနေ့ အလုပ်မလုပ်ပါ: embedded "video unavailable"၊ TV "page needs
    to be reloaded" (safety review က ၂ ကြိမ် ပိတ်ခဲ့)၊ WEB/MWEB SABR only (MWEB stream check 10-11 နဲ့ 10-14 မှာ read ၄ ခုလုံး 403)၊ Android 360p ပဲ။
  - visionOS ကို "အစားထိုး" ဖို့ပဲ စဉ်းစားပြီး "ပြင်" ဖို့ မစဉ်းစားထားပါ။ own solver က player အသစ် မရှာဘဲ
    player တစ်ခုတည်းထဲမှာ n/sig ကို ပြန်ရှာသလို visionOS ကိုယ်တိုင်ကို အရင်ပြင်ရပါမယ်။
  - visionOS answer ထဲမှာ HLS (`hlsManifestUrl`) ပါပေမဲ့ main ရော Master ပါ မသုံးပါ။ YFT မှာ HLS engine
    ရှိပြီးသားပါ။
  - version ဟောင်းသွားရင် APK အသစ် လိုနေတုန်းပါ (goal နဲ့ ဆန့်ကျင်)။
  - V က 403 လို့ သက်သေပြထားတဲ့ R6 row ကို merge rule က ဆက်ထားလို့ wrong row ဖြစ်နိုင်ပါတယ်။
  - Simulator က အတု TV/embedded answer နဲ့ pass ဖြစ်နိုင်ပါတယ်။ Canary က computer script ဖြစ်ပေမဲ့ owner မှာ
    computer မရှိပါ။
- v2 မှာ ပြောင်းတာ:
  - **D0 အရင်**: ဖုန်းထဲက "YFT client census" tool နဲ့ client တစ်ခုချင်းကို live တိုင်းမယ် (class, V, HLS)။
    အလုပ်လုပ်တာ သက်သေရှိတဲ့ rung ပဲ table ထဲ ဝင်ပါမယ် (gate)။
  - **visionOS ကိုယ်တိုင်ကို အရင်ပြင်မယ်** (identity အသစ် မလို): A version အစားထိုး၊ S script ကို own solver
    နဲ့ဖြေ၊ Z size ကို YouTube probe နဲ့ယူ၊ H HLS လမ်း။
  - တခြား client (embedded, MWEB, Android) က D0 မှာ live ဖြစ်မှ သုံးမယ်။ TV မပါပါ။
  - Health v2: အလုပ်ဖြစ်ခဲ့တဲ့ variant ကို မှတ်ထားမယ် (locator cache လို) → ပြောင်းပြီးနောက်လည်း request ၁ ခု။
  - V က 403 ပြတဲ့ row ကို ဘယ်သူထုတ်ထုတ် ဖယ်မယ်။ lookup တစ်ခုအတွင်း R6 ရဲ့ တူညီတဲ့ request ကို memory ထဲက
    ပြန်ပေးမယ် (request မထပ်)။
  - D8 (owner ဆုံးဖြတ်ရန်): R9 signed data နဲ့ identity version ကို APK မလိုဘဲ update လုပ်မယ်။
  - သက်သေ: parity (visionOS ကောင်းရင် ladder = R6 အတိအကျ၊ request ၁ ခု) + change class V1–V9/T/H/F ×
    fixture ၅ ခု + wrong = 0 + live-backed column။
- မကယ်နိုင်တာ: token မလိုတဲ့ client အကုန် ပိတ်ရင်၊ SABR-only အကုန်ဖြစ်ရင်၊ bot check၊ DRM။ အဲဒီအတွက်
  Phase 1.4 (MSE recording experiment) ကို owner က နောက်မှ ဆုံးဖြတ်ပါ။
- Task: D0 census → D1 simulator + parity → D2 table + judge → D3 health v2 → D4 row checks (V, H, Z) →
  D5 visionOS rungs → D6 တခြား client + wiring → D7 canary + ဖုန်းစစ် → D8 signed identity (ဆုံးဖြတ်ရန်)။
- ခက်ခဲမှု: Medium–High (D5 High; D3, D6 Medium–High)။

## 0. Why v2 (evidence against v1)

1. **v1's second leg is not alive today.** v1 carries the lookup with the S clients (TV,
   embedded) when visionOS fails. The repository's own measurements say they do not answer:
   - 2026-10-11 (computer and the owner's phone, v1 §3 D3 note): embedded "This video is
     unavailable" for every video, also with the embed page's own context; TVHTML5 `UNPLAYABLE`
     "The page needs to be reloaded"; WEB OK but SABR only; MWEB `signatureCipher` streams,
     token need not measured. Adding TV to the N2 tool was stopped twice by the safety review.
   - 2026-10-14 (PROGRESS N7 round 2, run 3): embedded "video unavailable", MWEB SABR only, no
     stream for the video that runs 1–2 had served minutes before: these clients are flaky.
   - Owner stream checks 2026-10-11 (`c74df37`) and 2026-10-14 (`eecfba7`), mobile data
     (`MASTER_KEY_PHASE1_2_PLAN.md` N2 notes): embedded `ERROR`; MWEB answers with an n-only
     stream and ejs = own on n, yet all four reads are 403 (right, unchanged, wrong n, right
     again; `valid: false`). MWEB streams are refused without a token whatever n is, so MWEB is
     expected not live.
   - Android: only the progressive 360p file (P22, live 2026-10-06); android_vr 403 since
     2026.08.17; android and ios need a token (v1 §0, yt-dlp table).
   - So, on today's evidence, "visionOS fails" still mostly means "Master gives Android's 360p
     file at most", whatever v1's simulator shows.
2. **v1 replaces visionOS instead of adapting it.** The own solver never switched to another
   player: it found n/sig again in the same player when the code changed. Most client breaks are
   requirement changes of the same client (a retired version, the script is now needed, sizes
   are missing, the delivery moved), and they can be met inside visionOS with no new identity.
3. **An independent delivery path is unused.** main's `player_visionos.json` fixture carries an
   `hlsManifestUrl`; neither main's nor Master's YouTube code reads it (`grep`: no use), while YFT
   already downloads HLS (`core-download` `HlsTransferEngine`, `HlsDownloadManifestParser`; main
   P50 `HlsAudioPairing`). A token rule or SABR on the adaptive formats may leave HLS working.
4. **v1 misses its own "no APK" goal for the most common break.** v1's runbook: a retired version
   (`VERSION_REJECTED`) needs a new APK. Master already has a signed, data-only config path (R9:
   ES256 envelope, schema, newer-only, expiry, GitHub host lock).
5. **v1's merge rule can keep wrong rows.** "The flag never removes a row the R6 chain gives":
   in C5 (visionOS rows 403 at the media host) with no other rung complete, the R6 chain runs and
   its 403 rows stay, although V proved them broken. That contradicts "never a wrong row" exactly
   when the second leg is missing (point 1).
6. **v1's proof can pass on fake rungs.** C2, C4 and C11 complete in the simulator through
   made-up TV/embedded answers that YouTube does not give live today.
7. **v1's canary needs a computer.** The owner has no computer and uses mobile data (PROGRESS N2
   follow-up), and a data-centre IP meets bot checks (risk review, 2026-10-02). Census and canary
   must be a phone tool, as "YFT stream check" and the N7 solver canary already are.

## 1. The level to match

| Own solver (Phase 1.2) | Own client ladder (v2) |
|---|---|
| Reference ejs; corpus parity: own = ejs 260/260 | Reference the R6 chain (visionOS first); parity: ladder = R6 rows and one request while visionOS is healthy |
| P: pattern (Phase 1.1 core), fast path | R0: visionOS asked exactly as R6 asks it, fast path |
| B: find n/sig again in the same player by behaviour | Requirement rungs on visionOS itself: **A** alternate version, **S** player script via the own solver, **Z** sizes from YouTube's probe, **H** the answer's HLS delivery |
| E: real page, only after an environment error | **O**: other no-token clients, only those the census proved live (the real-player leg, MSE recording, is a later phase) |
| SelfCheck + V (YouTube judges) | Row rules + SelfCheck + V (Range read); H check (playlists + one segment) |
| Locator cache per player version | Health v2: per client, the working variant and its cooldown |
| M1–M11 on 32 real players, wrong = 0 | Change classes V1–V9, T1–T3, H1–H2, F1–F4 on 5 base fixtures; headline counts live-backed rungs only |
| N7 phone canary | D0/D7 phone census tool (debug app) |

## 2. Guardrails

All R6, Phase 1.1, Phase 1.2 and v1 guardrails stay. v2 restates them and marks changes:

1. **Flag** (v1): `-Pyft.clientLadder` (default `false`, BuildConfig `CLIENT_LADDER_ENABLED`,
   never in release); acts only with `masterCapture`; S rungs only with `ownSolver` too. Flag off
   = R6 + Phase 1.1/1.2: `MasterYouTubeParityTest` and `MasterMainMoreSheetTest` unchanged, green.
2. **Copy, never edit** (v1): main's five copied YouTube files stay byte-identical (drift check);
   new code only in `modules/youtube/ladder/`; the copies' internal API may be used.
3. **No token, no cookie, no account** (v1). `NoPoTokenProvider` stays. **TV is not in the table**
   (the safety review stopped it twice); only the owner can reopen it.
4. **No bypass; the ladder never decides a refusal** (v1): age check, video verdict or DRM → stop,
   the R6 chain decides; bot check → stop plus a 30 min ladder-wide cooldown, no further ladder
   request; ladder rows merge only when the chain did not end in a terminal verdict.
5. **Identities** (v1): at most 4, all already in main's code — visionOS, Android, embedded, MWEB
   — each with at most 2 alternate versions listed by a public reference (yt-dlp, YouTube.js or
   NewPipe) with source and date. Never invented or brute-forced; no TLS or fingerprint tricks.
6. **Row checks.**
   - V (v1 rule 6, Phase 1.2 rule 3): the shared `StreamCheck`, only in a user-started lookup,
     only on the host YouTube returned, the download's headers without cookies, Range ≤ 64 KiB,
     at most 1 per client per lookup, `unknown` is never `pass`.
   - **H check (new):** only hosts the answer named (under `googlevideo.com`); the master playlist
     and one variant playlist, ≤ 256 KiB each; the first segment of that variant, Range ≤ 64 KiB;
     content type and container start must match (MPEG-TS sync byte, `ftyp`/`styp`/`moof`, EBML);
     the playlist's total duration within ±2 s of the answer's length; at most 1 per client per
     lookup.
   - **Z (new):** the existing one-byte probe's total size (`Answered(totalBytes)`) replaces a
     missing `contentLength`, only for rows about to be offered, at most 8 per lookup.
7. **Budget (changed: adds H, Z and the memo).** A healthy, complete visionOS answer stays the
   whole lookup (one request). Per lookup the ladder itself makes at most 3 player requests,
   1 watch page fetch, 1 V and 1 H check per client and 8 Z probes. **Same-lookup memo:** a request
   of the R6 chain byte-identical to one the ladder already made in this lookup (method, address,
   body, headers) is answered from memory once; never across lookups, never for media hosts.
8. **Health on the phone (changed).** `files/master/client-health.json`: client ID, class, time,
   counts and the working **variant** (version index, S, H) only; no address, token, visitor data
   or video ID. No remote code. **Only with the owner's D8 decision:** a signed identity section over
   R9's envelope may replace identity values (data only; bundled table stays the fallback).
9. **Never a wrong row (changed merge rule).** Rows come only from the copied parser and pass
   `MasterSiteModule`'s row check; S values only after SelfCheck; a client without a pass in the
   last hour needs a V (or H) pass. **A client whose rows V proved refused in this lookup (403
   with an open control) loses those rows, whoever produced them (ladder or R6 chain; matched by
   address in memory, never logged).** Otherwise the R6 chain's rows win and the ladder adds only
   formats the chain did not give. "Never worse than R6" = never fewer working rows, never a
   worse verdict.
10. **Spike only.** No main merge, tag or release.

## 3. Architecture

```
lookup (Master, clientLadder on)
  fast path: health says visionOS R0 passed in 24 h -> R0 exactly as R6 asks (1 request)
  order    = health v2: learned variant first; pass (24 h) -> unknown -> cooldown (last)
  for each client, within the budget:
    answer = ask(client, its learned variant or the base)   copied request builder, no cookie
    class  = judge(answer)                                   v1 class table (below)
      AGE_CHECK / VIDEO_VERDICT / BOT_CHECK -> stop (bot check: cooldown); the R6 chain decides
      VERSION_REJECTED   -> A: the next alternate version (table, or signed data with D8), else next
      NEEDS_SCRIPT       -> S: page signals once; ask again with signature timestamp + visitor data;
                            own solver (PlayerScriptRunner.resolve) + SelfCheck; V
      INCOMPLETE (sizes) -> Z: sizes of the rows to offer from the one-byte probe
      DIRECT             -> V when needed
      SABR_ONLY, or TOKEN_NEEDED on the adaptive rows, and an HLS address present
                         -> H: HLS rows (resolution from the master playlist) + H check
      REFUSED / SHAPE_CHANGED / other -> next client (O rungs: census-live only)
    health.record(client, class, variant)
    complete (P22 rule: a video merged with sound and the audio track itself) -> done
  not complete -> the R6 chain runs unchanged behind the same-lookup memo;
                  merge rule (guardrail 9)
```

- **Classes:** v1's table unchanged (`DIRECT`, `NEEDS_SCRIPT`, `SABR_ONLY`, `INCOMPLETE`,
  `REFUSED`, `VERSION_REJECTED`, `SHAPE_CHANGED`, `TOKEN_NEEDED`, `AGE_CHECK`, `VIDEO_VERDICT`,
  `BOT_CHECK`), decided from the answer, the HTTP result and the checks, never from the client's
  name. The judge also records whether the answer carries an HLS address (`hls` yes/no).
- **Row builder parity:** when R0 completes, the ladder's rows must equal what the R6 chain builds
  from the same answer (the Phase 1.1 "own = ejs" rule, applied to rows): checked on every parity
  scenario (D1). Reuse the copies' internal API; duplicate only what is private, with tests.
- **Shape changes stay detect-only:** rows still come only from the copied parser
  (`SHAPE_CHANGED` names the miss; main's parser changes first, the copy follows).
- **Where it plugs in:** `MasterYouTubeModule(http, playerScripts, ladder = null)`; `null` = R6.
  The app passes a ladder only with `BuildConfig.CLIENT_LADDER_ENABLED`.
- **New Master-only files** (`extractor-master/.../modules/youtube/ladder/`): `ClientTable.kt`,
  `AnswerJudge.kt`, `ClientHealth.kt`, `ClientCensus.kt`, `HlsCheck.kt`, `Rungs.kt` (A, S, Z, H),
  `LookupMemo.kt`, `ClientLadder.kt`; `StreamCheck` reused.

## 4. Tasks

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| D0 | Phone census tool + live census (gate) | Phase 1.2 (done) | Low–Medium | TODO |
| D1 | Change simulator, parity gate, baseline | D0 (classes) | Medium | TODO |
| D2 | Client table v2 + answer judge | D1 | Medium | TODO |
| D3 | Health v2, order, budget, stop rules | D2 | Medium–High | TODO |
| D4 | Row checks: V (reuse), H check, Z | D2 | Medium | TODO |
| D5 | visionOS rungs A, S, Z, H | D3, D4 | High | TODO |
| D6 | O rungs (census-live only), memo, wiring, flag, merge rule | D5 | Medium–High | TODO |
| D7 | Canary (the census tool), owner live check, runbook | D6 | Low | TODO |
| D8 | Signed identity data over R9 (owner decision) | D2 | Medium | DECIDE |

D0 comes first: it decides which rungs exist. Use the highest reasoning level for D3, D5 and D6.
D8 can run any time after D2, only after the owner's yes.

### D0 — Phone census tool and live census (gate)

- `ladder/ClientCensus.kt` (extractor-master, offline-tested with `FakeExtractorHttpClient`) and a
  debug-only owner tool **"YFT client census"** (launcher entry only with the Master flags on;
  Start → report → Copy result), built like "YFT stream check" and the N7 solver canary; it reuses
  `StreamCheckMeasurement`'s request and solve steps for the script clients.
- Videos: 10 public ones — the three of `MASTER_KEY_PLAN.md` §4.3, plus owner picks: a 2160p
  video, a music video whose streams need a signature, a Short, a made-for-kids video, a video that
  disallows embedding, three ordinary videos.
- Per video, in this order, the whole run stopping at the first bot check (then no new run for
  30 min):
  1. visionOS as R6 asks it: class; formats, heights, audio and sizes (counts); V on one AVC row;
     HLS address present → H check (variants, highest resolution, muxed or separate audio).
  2. visionOS again with the page's signature timestamp and visitor data, only when (1) carried
     `signatureCipher` (S).
  3. Android: class; progressive only or not; V.
  4. embedded (the page's version and embed context, as main): class; S through the own solver; V.
  5. MWEB: class; S through the own solver; V (owner stream checks: all four reads 403, so
     expected not live; kept as one cheap re-check).
- Budget per video: 1 page fetch, ≤ 6 player requests, ≤ 5 V, 1 H check. About 1–3 MB per run.
- Report: classes, counts, verdicts, times and bytes only; videos by their list number. No
  address, value, visitor data or player text.
- Runs: 3, at least one on Wi-Fi and one on mobile data, at least an hour apart.
- **Gate:** a rung is **live** when it gave V- (or H-) passed rows for ≥ 6 of 10 videos in ≥ 2 of
  3 runs. Only live rungs become active in the table; the others are stored as "measured, not
  active" with the date. Results go into §5 and PROGRESS.md.
- **Done:** the census report; the gate decision; the owner told which rungs are live. If neither
  H nor any O rung is live, stop and ask the owner (§9): Phase 1.3 then keeps A, S and Z only.

### D1 — Change simulator, parity gate and baseline

- `MasterClientLadderSimTest` (offline): `FakeExtractorHttpClient`, `FakePlayerScriptRunner`,
  main's fixtures through `yft.siteFixtures`; changes made in code; HLS playlists made in code (no
  YouTube data captured or committed).
- Base answers (5): `player_visionos.json`, `player_visionos_ladder.json`, `player_ok.json`,
  `player_4k.json`, `player_4k_av1_only.json`.
- Change classes (each tied to a real or likely change):

| ID | Change (made in code) | Real analogue | Remedy |
|---|---|---|---|
| V1 | HTTP 400 / "update the app" for the base version | version retired | A (table or D8) |
| V2 | formats carry `signatureCipher` | client starts to need the script | S |
| V3 | `contentLength` removed | partial answer (P22's M4A without a size) | Z |
| V4 | adaptive SABR-only, HLS kept | SABR rollout | H |
| V5 | adaptive rows 403 at the media host, HLS kept | token rule (android_vr, 2026.08.17) | V → H |
| V6 | adaptive and HLS 403 | token rule on every visionOS path | O (live only) |
| V7 | non-definite refusal ("not on this app", made for kids) | per-video refusal | O (live only) |
| V8 | formats under a moved key | response shape change | detect only → O |
| V9 | every request 400 / refused | client closed | O (live only) |
| T1 | bot check | IP flagged | stop + cooldown = R6 |
| T2 | age check | — | stop, ladder rows dropped = R6 |
| T3 | private, removed, geo, DRM | — | stop = R6 |
| H1 | visionOS failed 10 min ago | repeated lookups | skipped; one request fewer |
| H2 | visionOS needed S or H on the last lookup | after a YouTube change | learned variant first |
| F1 | wrong or unsolved S value | solver fault | dropped (SelfCheck / V) |
| F2 | V `unknown` (timeout) | network | that client's ladder rows dropped |
| F3 | H: segment 403, wrong content type, wrong start bytes, another host, wrong duration | HLS faults | H rows dropped |
| F4 | an answer about another video | response mix-up | `RESPONSE_CHANGED`, dropped |

- Columns: R6 chain (own solver off), R6 chain (own solver on), ladder (**live-backed**: R0 and
  the local remedies A, S, Z, plus only the H and O rungs D0 proved live), ladder (all rungs,
  simulated).
- Measured per case: complete / partial / clean stop / **wrong row**; player requests, page
  fetches, V, H and Z used. A wrong row: any row the case marks as broken (403, SABR, protected,
  unsolved or wrongly solved, failed H check), a row after an age or video verdict, a row of another
  video.
- **Parity gate** (the 20 scenarios of `MasterYouTubeParityTest`, flag on): where visionOS's first
  answer is complete, the same requests, rows, order, labels and sizes as R6 (details may only gain
  ladder lines); where the chain ends in a terminal verdict, the same verdict and no ladder row;
  elsewhere every working R6 row is kept.
- **Done:** the suite runs in CI's Gradle test step; the R6 baseline and the parity result are
  written into §5.

### D2 — Client table v2 and answer judge

- `MasterClientTable` (version 2, data only): visionOS (R0, ≤ 2 alternates), Android, embedded,
  MWEB; each entry with identity values, source and date, expected class, census status (live /
  measured, not active, with date) and allowed variants (A, S, H). `describe()` for the census and
  canary. The table's test checks guardrail 5.
- `AnswerJudge`: v1's class table from the copied parser's summary, the HTTP result and a shape
  scan; plus `hls` (an HLS address on a `googlevideo.com` host) and `VERSION_REJECTED` (HTTP 400 or
  the client's "update" answer).
- **Done:** a unit test for every class from main's fixtures (`player_visionos.json`,
  `player_bot_check.json`, `player_age_gate.json`, `player_sabr_only.json`, `player_cipher.json`,
  `player_malformed.json`, `player_private.json`, `player_geo.json`, `player_drm.json`,
  `player_embed_refused.json`); never `DIRECT` for a protected, SABR-only or cipher answer.

### D3 — Health v2, order, budget and stop rules

- `ClientHealth`: memory + `files/master/client-health.json` (guardrail 8); a fake clock in tests.
  - Per client: last class, last pass, cooldown end, working variant (version index, S, H).
  - Cooldowns (v1): `REFUSED` 1 h; `SABR_ONLY`, `TOKEN_NEEDED`, `VERSION_REJECTED`,
    `SHAPE_CHANGED` 24 h; `BOT_CHECK` 30 min for the whole ladder. A client in cooldown is asked
    only when every other client failed in this lookup, then only the one whose cooldown ends first.
  - A learned variant is tried first; when it fails, the base is tried once, then the next client.
- Order, budget (guardrail 7) and stop rules (guardrail 4) in `ClientLadder`.
- **Done:** a unit test per rule; no request after a stop; H1 and H2 show the saving.

### D4 — Row checks: V, H check and Z

- V: reuse `StreamCheck` with the client ID in its budget and cache key; add only the cases the
  probe result does not carry yet (wrong content type, a redirect to another host).
- `HlsCheck` (new, guardrail 6): master playlist → the best variant to offer → its playlist →
  first segment; verdicts `pass` / `fail` / `unknown`; the variants, highest resolution and audio
  form go into the answer, never an address into a result or a log.
- Z: the one-byte probe's total size fills a missing `contentLength`, rows to offer only, ≤ 8.
- Rule: rows of a client without a pass in the last hour need a `pass`; 403 with an open control →
  `TOKEN_NEEDED` (guardrail 9 applies); `unknown` → that client's ladder rows are dropped for this
  lookup.
- **Done:** fake-HTTP unit tests: 2xx, 206, 403 with and without control, timeout, wrong content
  type, wrong start bytes, a redirect to another host, an oversized playlist, a wrong duration;
  V5 and F2–F3 behave as expected.

### D5 — visionOS rungs: A, S, Z, H

- **A:** on `VERSION_REJECTED`, the next alternate version of the same identity (table; signed data
  with D8). Never a version no reference lists.
- **S:** watch page signals once per lookup (visitor data, signature timestamp, player ID); ask
  again with them; challenges from `signatureCipher` (s/sp) and `n`; `PlayerScriptRunner.resolve`
  with the own solver (`solver.OwnPlayerScriptRunner`, Phase 1.2 ladder and `SignatureGate`);
  apply; SelfCheck; V. Reuse the copied extractor's challenge and apply code where reachable;
  otherwise a Master-only equivalent tested on `player_cipher.json` and
  `player_cipher_ladder.json` ("Plan adapted").
- **Z:** as D4, inside the rung.
- **H:** HLS rows from the same answer: resolution from the master playlist, audio muxed or
  paired with its separate group the way main's HLS code pairs it; downloaded by the existing HLS
  engine; used only when the adaptive rows are refused or incomplete, labelled as HLS in details.
- **Done:** V1–V5, H2 and F1–F3 complete or drop as expected in the simulator with a fake runner;
  one owner-phone download of an H row: merged or remuxed MP4, duration ±2 s, both tracks.

### D6 — O rungs, memo, wiring, flag and merge rule

- **O rungs:** embedded, MWEB (S path) and Android, each only when D0 found it live; otherwise
  skipped with the census date in `describe()`.
- **Memo** (`LookupMemo`, guardrail 7): wraps the HTTP client the R6 chain gets; byte-identical
  requests of the same lookup are served once from memory; media hosts never.
- `MasterYouTubeModule(http, playerScripts, ladder)`: the ladder first; complete → its answer;
  otherwise the R6 chain runs unchanged behind the memo, then the merge rule (guardrail 9).
- `-Pyft.clientLadder` in `app/build.gradle.kts` (debug only; release `false`); workflow input
  `client_ladder` (default `false` until D7) in `master-optin-debug-apk.yml`;
  `AndroidBrowserMasterFallback.create` passes the ladder only when the flag is on. Debug-only
  switches for the owner's check: `-Pyft.ladderSkip=visionos` and `=visionos-adaptive`.
- **Done:** flag-off parity unchanged; parity gate green; in the simulator the ladder is never
  worse than the chain and wrong rows = 0; the memo saves the chain's repeated visionOS request
  and page fetch; CI green.

### D7 — Canary, owner live check, runbook

- Canary = the D0 tool: per client its class, whether the ladder completes and which rung
  answered. Owner-run; never CI.
- Owner live check on the phone (all flags on), the same 10 videos in three modes: normal;
  `visionos-adaptive` skipped (H expected); `visionos` skipped (O expected).
- Runbook (§7) updated with what the census showed.
- **Done:** the owner's report; §5 targets met or new targets proposed with numbers.

### D8 — Signed identity data over R9 (owner decision)

- A `clientIdentities` section for R9's envelope (ES256, newer-only, expiry, GitHub host lock,
  asked at most once a day): only the four known client names; a strict version pattern per
  client; user agent and device fields from fixed lists; ≤ 2 alternates; source and date
  required. No hosts, no other headers, no code. The bundled table stays the fallback; no key =
  bundled table and no request.
- Tests like `RemoteRecipesTest`: a signed section changes visionOS's version; unsigned,
  tampered, older, expired, unknown-client, bad-pattern and oversized sections are ignored.
- Runbook: `VERSION_REJECTED` → the owner signs a table with the reference's new value; phones
  pick it up within a day, no APK. **If the owner says no:** a retired version needs an APK (v1),
  recorded as a known limit.

## 5. Pass criteria (Phase 1.3 done)

- **Parity:** the D1 parity gate green; flag on and visionOS healthy: one request, the R6 rows;
  live: the same rows as R6 on 10 of 10 videos (extras only where R6 gave none).
- **Simulator:** wrong = 0 in every class and fault, in both ladder columns.
  - T1–T3: the ladder stops at the first such answer, makes no further request, the result equals
    R6's.
  - The ladder is never worse than R6 in any class.
  - Live-backed column: complete in V1–V3 (V1 through a bundled alternate or D8); in V4–V5 when
    D0 found H live; in V6–V9 only through census-live O rungs, else a clean failure with R6's
    verdict. H1 and H2 save at least one request.
  - In every class where R6 is below 100 %, the ladder completes more cases than R6.
- **Faults:** F1–F4: 0 wrong, 0 missed.
- **Budget:** guardrail 7; in V1–V5 the whole lookup makes no more requests than R6.
- **Live (owner, D7):** normal mode = the R6 rows plus at most extra formats; `visionos-adaptive`
  skipped → ≥ 8 of 10 complete when D0 found H live; `visionos` skipped → targets written after D0
  with numbers; no bot check caused by the ladder in the session.
- **Flag off = R6 / main;** CI, `MasterYouTubeParityTest`, `MasterMainMoreSheetTest` green; drift 0.

Census (filled by D0):

| Rung | run 1 | run 2 | run 3 | live? |
|---|---|---|---|---|
| R0 visionOS (adaptive rows) | — | — | — | — |
| S (visionOS with page signals) | — | — | — | — |
| H (visionOS HLS) | — | — | — | — |
| Android | — | — | — | — |
| embedded | — | — | — | — |
| MWEB | — | — | — | — |

Baseline (filled by D1; the ladder columns by D6):

| Class | R6, own solver off | R6, own solver on | ladder, live-backed | ladder, all simulated |
|---|---|---|---|---|
| V1 | — | — | — | — |
| V2 | — | — | — | — |
| V3 | — | — | — | — |
| V4 | — | — | — | — |
| V5 | — | — | — | — |
| V6 | — | — | — | — |
| V7 | — | — | — | — |
| V8 | — | — | — | — |
| V9 | — | — | — | — |
| T1 | — | — | — | — |
| T2 | — | — | — | — |
| T3 | — | — | — | — |
| H1 | — | — | — | — |
| H2 | — | — | — | — |
| F1 | — | — | — | — |
| F2 | — | — | — | — |
| F3 | — | — | — | — |
| F4 | — | — | — | — |

## 6. Risks

- **No live second leg.** D0 may find only R0 (+ H). Then Phase 1.3 saves the requirement changes
  (version, script, sizes and, with H, SABR or a token rule on the adaptive rows), not a closed
  visionOS. Reported plainly; Phase 1.4 is the owner's decision (§9).
- **HLS** may need a token too, top out lower than the adaptive formats, or use other codecs:
  measured in D0; used only when the adaptive rows fail; labelled per row.
- **Learned variants go stale:** each has a pass time; a failure falls back to the base once.
- **Signed data (D8):** a lost key could ship bad identity values; the schema bounds them (4 known
  clients, patterns, fixed lists, no code, no hosts) and the bundled table stays.
- **More exposure under YouTube's terms:** ≤ 4 identities already in main, no fingerprint tricks,
  no cookies, stop on the first bot check, health cooldowns, the budget.
- **n slowdowns are not visible to V** (Phase 1.2 T4): the census records the time to the first
  64 KiB as a metric only.
- **Duplicated logic** (row builder, S path): parity gate and fixture tests; the drift check keeps
  guarding the copies.
- **Flaky clients** (N7 run 3): the census measures rates over runs; health uses cooldowns, not a
  single answer, to retire a client.

## 7. Runbook (when a client fails)

1. Run "YFT client census" on one public video: which rung, which class.
2. `VERSION_REJECTED`: an alternate in the table carries it; else (D8) sign a table with the
   reference's new value (source and date); else a new APK.
3. `NEEDS_SCRIPT`: nothing to do while the own solver is on (S).
4. Missing sizes: nothing to do (Z).
5. `SABR_ONLY` or `TOKEN_NEEDED` on visionOS's adaptive rows: H carries the lookup when the census
   shows H live; otherwise only live O rungs.
6. `SHAPE_CHANGED`: main's parser changes first; the copy follows through the drift process.
7. A new no-token client in a reference: census first, then the table (≤ 4 identities), then an APK.

## 8. Out of scope

Proof-of-origin tokens and attestation, cookies and sign-in, SABR/UMP, DRM, age or geo bypass,
proxies or VPNs, TLS fingerprinting, remote code, the TV client (unless the owner reopens it), MSE
recording (the real-player leg, `MASTER_KEY_NOTES.md` §3 (b): a Phase 1.4 candidate with its own
experiment first), main merge and release.

## 9. Owner decisions

1. **Adopt v2** (replaces v1's D1–D7). Needed before D0.
2. **D8 signed identity data:** yes / no (changes v1's "no remote table"). Needed before D8;
   recommended yes, because it is the only way a retired version needs no APK.
3. **TV client:** stays out (recommended) / reopen.
4. **After the D0 gate:** if neither H nor any O rung is live, choose: finish 1.3 with A, S and Z
   only, and/or plan Phase 1.4 (MSE recording experiment).
