# Master Key — Phase 1.3 Plan: own client ladder (visionOS may fail, Master keeps working)

Status: **plan + prompt.** No code is changed by this document.
Date: 2026-10-11. Branch: `spike/master-extractor-backup` only.
Reads: `PROGRESS.md` "R6 — Own YouTube module", `MASTER_KEY_PHASE1_1_PLAN.md` (the own solver),
`MASTER_KEY_PHASE1_2_PLAN.md` §1.3 and §2 (live check V, change simulator), `MASTER_KEY_NOTES.md`
§3. Prompt: `MASTER_KEY_PHASE1_3_PROMPT.md`.

Master's YouTube module (R6) leans on one client: the identity of YouTube's visionOS app (main
P14). Asked first, it answers in about 17 KB with direct addresses that need neither the player
script nor a token. Phase 1.1 made the n/sig solver independent of ejs, so "ejs fails" no longer
means "Master fails". Phase 1.3 does the same on the client side: when visionOS stops working,
Master keeps giving downloads without an APK update, never a wrong row:

- Take visionOS's logic (an app identity that YouTube answers with ready addresses) out of the
  single hard-coded client into a **table-driven ladder** of clients that need no token.
- Judge every answer by what it **contains**, not by the client's name: direct (D), needs the
  player script (S: solved by the own solver), or needs a token (never used by Master).
- Remember on the phone which client works (**health**), so the normal lookup stays one request.
- Ask **YouTube itself** with a small Range read before offering rows of a client that is not
  known to be good (the live check V of Phase 1.2).
- **Prove it:** a response simulator (C1–C13) measures the R6 chain against the ladder; wrong
  rows must stay 0.

## မြန်မာ အနှစ်ချုပ်

- ရည်မှန်းချက်: own solver ကြောင့် ejs ပျက်လည်း Master မပျက်သလို visionOS ပျက်လည်း Master မပျက်အောင်
  လုပ်မယ်။ APK update မလိုဘဲ၊ မှားတဲ့ row လုံးဝ မပေးဘဲ။
- visionOS ရဲ့ logic (app client identity တစ်ခုနဲ့ မေးရင် YouTube က URL အပြီးအစီး ပေးတာ) ကို client
  တစ်ခုတည်းမှာ မထားဘဲ client table + ladder အဖြစ် ချဲ့မယ်။
- client ကို နာမည်နဲ့ မယုံဘဲ ပြန်လာတဲ့ အဖြေနဲ့ ခွဲမယ်:
  - D — script မလို၊ token မလို (ဒီနေ့ visionOS)။
  - S — player script လို၊ token မလို (tv, embedded): own solver နဲ့ ဖြေမယ်။
  - T — token လို: Master မသုံးပါ။
- visionOS ကိုယ်တိုင် script လိုလာရင်တောင် own solver ရှိရင် S အဖြစ် ဆက်သုံးနိုင်ပါတယ်။
- client တစ်ခုချင်းရဲ့ health (ကောင်း/ပျက်) ကို ဖုန်းထဲ မှတ်ပြီး ကောင်းတာကို အရင်မေးမယ် → ပုံမှန် request
  တစ်ခုပဲ။
- row မပေးခင် YouTube ဆီက byte အနည်းငယ် တောင်းစစ်မယ် (V)။ 403 ဆို အဲဒီ client ရဲ့ row မပေးပါ။
- သက်သေ: response အတု C1–C13 နဲ့ R6 chain vs ladder ကို တိုင်းမယ်။ မှားတဲ့ row = ၀။
- Task: D1 simulator + baseline → D2 table v2 + judge → D3 health + အစဉ် + budget → D4 row check (V)
  → D5 S rung (own solver) → D6 ချိတ်ဆက် + flag → D7 canary + owner ဖုန်းစစ် + runbook။
- မပြောင်းတာ: flag ပိတ်ရင် Master = R6၊ app = main။ token/cookie/account မသုံး။ bypass မရှိ (age,
  private, DRM, bot check ဆို ladder ရပ်ပြီး R6 chain ကို မပြောင်းဘဲ လွှဲ — ငြင်းချက်ကို ladder က
  ဘယ်တော့မှ မဆုံးဖြတ်)။ remote table/code မရှိ။
- မကယ်နိုင်တာ: token မလိုတဲ့ client အားလုံးကို YouTube က တပြိုင်နက် ပိတ်ရင်၊ SABR-only၊ bot check၊ DRM။
- ခက်ခဲမှု: Medium (D5 က Medium–High)။

## 0. Why (evidence)

- **Master today** (R6, the copy of main's P14/P22 chain): visionOS first; a complete answer ends
  the lookup without the watch page. Otherwise the watch page chain: the page's client (needs a
  token; Master has none), visionOS again with the page's visitor data, the embedded player
  (needs the player script; embeddable videos only), the mobile site (token), Android last (one
  progressive 360p file; its separate formats come only through SABR, live 2026-10-06).
  - So without visionOS, Master offers at most Android's 360p file, plus embedded rows when the
    own solver is on and the video may be embedded.
- **Reference: yt-dlp's client table** (master, read 2026-10-11; values and notes only):
  - Clients that need no player script: `android`, `android_vr`, `ios`, `visionos`. Only
    `visionos` has no proof-of-origin token rule. `android` and `ios` need a token for HTTPS
    formats (`ios` for HLS too); `android_vr`: "since 2026.08.17, ALL formats … are 403'd with
    version 1.65.10".
  - Clients with no token rule that need the player script: `tv` (and its variants
    `tv_downgraded`, `tv_samsung`) and `web_embedded` (embeddable videos only).
  - `visionos`: "Made for kids videos aren't available with this client".
- **Reading:** today the D class has one member (Android's 360p file is at risk). More D clients
  alone cannot carry Master. The second leg is the S class (no token, player script) solved by
  the own solver, which Phase 1.1 made independent of ejs. That is how "visionOS fails" stops
  meaning "Master fails", the way "ejs fails" did.
- **What no code can fix:** a client that YouTube closes stays closed (a server decision). The
  ladder survives one-client breaks (the common case) and requirement changes (a client that
  starts to need the script); not a token rule on every client at once.

## 1. Guardrails

All R6, Phase 1.1 and Phase 1.2 guardrails stay. In addition:

1. **Flag.** A new flag `-Pyft.clientLadder` (default `false`, BuildConfig
   `CLIENT_LADDER_ENABLED`, never in the release variant). It acts only when `masterCapture` is
   on; S rungs act only when `ownSolver` is on too. With it off, Master is exactly R6 + Phase 1.1:
   `MasterYouTubeParityTest` and `MasterMainMoreSheetTest` stay unchanged and green.
2. **Copy, never edit.** main's five copied YouTube files stay byte-identical (the drift check of
   `toolkit-provenance.tsv`). The ladder is new Master-only code; it may use the copies' internal
   API (`YouTubeClientProfile.playerRequestBody`, `YouTubePlayerResponseParser.inspect` and
   `pageSignals`, `YouTubeUrls`).
3. **No token, no cookie, no account.** `NoPoTokenProvider` stays. No cookie or `Authorization`
   header goes to any ladder client or media host. No sign-in.
4. **No bypass; the ladder never decides a refusal.** The ladder answers only with a complete
   success. Every stop hands the lookup to the R6 chain unchanged, and the chain's verdict is
   final (as in main's P22: what a device client refuses describes the client; only the page's
   own client judges the video):
   - An age check from any ladder client → stop; the ladder's rows are dropped.
   - A video verdict (the copied parser's `definite` failure: private, removed, geo,
     unavailable) or DRM → stop.
   - A bot check → stop, and a ladder-wide cooldown (30 min): the ladder never adds a request
     after a bot check.
   - Ladder rows are merged into the chain's answer only when the chain did not end in a
     terminal verdict (age, video verdict, DRM, bot check).
5. **Identity values.** Only real, public YouTube app identities listed by a public reference
   (yt-dlp, YouTube.js or NewPipe), each with its source and date in the table. At most 4
   identities and 2 alternate versions each. Never invented or brute-forced versions. Only the
   headers and context fields the table names; no TLS or fingerprint tricks.
6. **Row check (V)** follows Phase 1.2 guardrail 3: only during a lookup the user started; only on
   the stream host YouTube returned; the same HTTP client and headers as a download; no cookies;
   a Range read of at most 64 KiB; at most 1 check per client per lookup; `unknown` is never
   `pass`. One implementation, shared with Phase 1.2 N2 (whichever task lands first builds it).
7. **Request budget.** Per lookup the ladder itself makes at most 3 player requests, 1 watch page
   fetch and 1 row check per client.
   - When visionOS is healthy and complete, the lookup is its one request, as today.
   - When the ladder completes, that is the whole lookup (when visionOS fails, the R6 chain makes
     up to 6 player requests and a watch page fetch).
   - When it stops or is incomplete, the R6 chain runs after it unchanged: the worst case is the
     chain plus the ladder's own requests, and the health cache keeps a dead client from being
     asked again.
8. **Health data stays on the phone** (`files/master/client-health.json`): client ID, class,
   time and counts only; no address, token, visitor data or video ID. No remote table, no remote
   code.
9. **Never a wrong row.**
   - Rows come only from the copied parser and pass `MasterSiteModule`'s row check (HTTPS, no
     `sabr` parameter, not protected).
   - S rows use only own-solver values that passed SelfCheck.
   - Rows of a client without a `pass` in the last hour need a V `pass` first.
   - The flag never removes a row the R6 chain gives (merge rule, D6).
10. **Spike only.** No main merge, tag or release.

## 2. Architecture

```
lookup (Master, clientLadder on)
  order = table v2 sorted by health: pass (24 h) -> unknown -> failed (in cooldown, last)
  for each client, within the budget:
    ask (copied request builder, no cookie)
    judge the answer -> class (table below)
      AGE_CHECK / VIDEO_VERDICT   -> stop; drop the ladder's rows; the R6 chain decides
      BOT_CHECK                   -> the same, plus a 30 min ladder cooldown
      DIRECT                      -> rows (copied parser) -> V when needed -> keep
      NEEDS_SCRIPT, ownSolver on  -> S path: watch page signals, ask again with the signature
                                     timestamp and visitor data, own solver + SelfCheck -> rows -> V
      other classes               -> next client
    record health(client, class)
    complete (a video with sound and its audio track) -> done
  not complete -> the R6 chain (copied YouTubeExtractor) runs unchanged; its rows win; the
                  ladder adds only formats the chain did not give, and nothing when the
                  chain ends in a terminal verdict
```

Answer classes (decided from the answer and the row check, never from the client's name):

| Class | When | Action |
|---|---|---|
| `DIRECT` | Playable; formats with direct addresses; none protected; not SABR-only | rows → V when needed |
| `NEEDS_SCRIPT` | Formats carry `signatureCipher` / need the player script | S path (ownSolver on), else next |
| `SABR_ONLY` | Only the server-ABR protocol | next; health |
| `INCOMPLETE` | No title or length, a format without a size, or no audio | keep its rows; next |
| `REFUSED` | A refusal of this request, not a video verdict (the copied parser's non-definite failure, e.g. "not on this app", no formats) | next |
| `VERSION_REJECTED` | HTTP 400 / a precondition failure for the client | one alternate version, then next |
| `SHAPE_CHANGED` | HTTP 200 JSON, the copied parser finds no streaming data, a shape scan finds format-like objects | next; health; the canary reports it |
| `TOKEN_NEEDED` | V: 403 on a direct row | drop the client's rows; health |
| `AGE_CHECK` | Age verdict | stop; the R6 chain decides |
| `VIDEO_VERDICT` | The copied parser's `definite` failure (private, removed, geo, unavailable) or DRM | stop; the R6 chain decides |
| `BOT_CHECK` | Bot check | stop; ladder cooldown; the R6 chain decides |

- **Why the shape scan only detects:** rows still come only from the copied parser, so a changed
  response can never produce a half-understood row. It turns a silent miss into a named class.
- **visionOS as S:** if visionOS starts to need the script, it stays usable through the S path
  (with the own solver on), because the class comes from the answer.
- **Where it plugs in:** `MasterYouTubeModule(http, playerScripts, ladder = null)`; `null` = R6.
  The app passes a ladder only when `BuildConfig.CLIENT_LADDER_ENABLED`.
- **New Master-only files** (`extractor-master/.../modules/youtube/ladder/`): `ClientTable.kt`,
  `AnswerJudge.kt`, `ClientHealth.kt`, `ScriptRung.kt`, `ClientLadder.kt`; the row check is the
  shared `StreamCheck` (Phase 1.2 N2) or built here first.

## 3. Tasks

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| D1 | Response simulator (C1–C13) + baseline of the R6 chain | R6 | Medium | TODO |
| D2 | Client table v2 + answer judge | D1 | Medium | TODO |
| D3 | Health cache, order, budget, stop rules | D2 | Medium | TODO |
| D4 | Row check (V) for ladder rows | D2 (shared with Phase 1.2 N2) | Low–Medium | TODO |
| D5 | S rungs: script clients through the own solver | D2, Phase 1.1 | Medium–High | TODO |
| D6 | Ladder wiring, flag, merge rule | D3, D4, D5 | Medium | TODO |
| D7 | Canary for every client, owner live check, runbook | D6 | Low | TODO |

D1 comes first: it measures the baseline every later task is judged by. Phase 1.3 is independent
of Phase 1.2 N3–N7; it can run before or after them (owner's choice). D4 and Phase 1.2 N2 build one
stream check, not two.

Overall difficulty: **Medium**. The hardest task is D5 (the S path: signature timestamp, visitor
data, challenges, applying the values, all without editing the copies), then D3 (stop and budget
rules). Use the highest reasoning level for D3 and D5.

### D1 — Response simulator and baseline

- New `MasterClientLadderSimTest` (Kotlin, offline): `FakeExtractorHttpClient` and
  `FakePlayerScriptRunner` from `testing/ExtractorTestSupport.kt`, and main's fixtures
  (`fixtures/youtube/*`, read through `yft.siteFixtures`). Scenario answers are made inside the
  test from those fixtures (status, fields and HTTP results changed in code), so no new YouTube
  data is captured or committed.
- Scenarios (each tied to a real or likely change):

| ID | Change | Real analogue | Expected with the ladder |
|---|---|---|---|
| C1 | visionOS: bot check | IP flagged | ladder stops (cooldown); the R6 chain decides |
| C2 | visionOS refuses the request (non-definite) | "made for kids" note | next client (S) completes |
| C3 | visionOS: SABR-only | SABR rollout | next client |
| C4 | visionOS formats need the script (`signatureCipher`) | client policy change | visionOS through the S path |
| C5 | visionOS rows 403 at the media host | token rule (android_vr, 2026.08.17) | V fails → dropped; next client |
| C6 | visionOS: HTTP 400 for the client | version retired | one alternate version, then next |
| C7 | visionOS: no sizes or no audio | partial answer | its rows kept; next client completes |
| C8 | visionOS: HTTP 200, formats under a moved key | response shape change | `SHAPE_CHANGED`; next client |
| C9 | Age check | — | ladder stops; its rows dropped; the R6 chain decides |
| C10 | Private, removed, geo or DRM | — | ladder stops; the R6 chain decides |
| C11 | visionOS and Android both 403 | token rule on every D client | S rungs carry the lookup |
| C12 | Every client fails | worst case | clean failure, right verdict, budget kept |
| C13 | visionOS failed 10 minutes ago (health) | repeated lookups | visionOS skipped; one request fewer |

- **Measured per scenario:** complete / partial / clean stop / **wrong row**; player requests,
  page fetches and row checks used.
  - A wrong row is any row the scenario marks as broken (403, SABR, protected, unsolved or wrongly
    solved S value), a row after an age or video verdict, or a row of another video.
- Columns: R6 chain (ownSolver off), R6 chain (ownSolver on, a fake runner), ladder D only,
  ladder D + S.
- **Done:** the suite runs in CI's Gradle test step; the R6 chain baseline is written into §4.

### D2 — Client table v2 and answer judge

- `MasterClientTable` (version 2, data only): visionOS (D), Android (D, at risk), TV (S),
  embedded (S, embeddable only); each entry with identity values, expected class, source and
  date, and at most 2 alternate versions. `describe()` for the canary. The table's own test
  checks the rule-5 limits.
- `AnswerJudge`: the class table of §2, from the copied parser's summary, the HTTP result and a
  shape scan.
- **Done:** a unit test for every class (from fixtures such as `player_visionos.json`,
  `player_bot_check.json`, `player_age_gate.json`, `player_sabr_only.json`, `player_cipher.json`,
  `player_malformed.json`, `player_private.json`, `player_geo.json`, `player_drm.json`); the
  judge never says `DIRECT` for a protected, SABR-only or cipher answer.

### D3 — Health cache, order, budget and stop rules

- `ClientHealth`: memory + `files/master/client-health.json` (rule 8); a fake clock in tests.
  - Cooldowns: `REFUSED` 1 h; `SABR_ONLY`, `TOKEN_NEEDED`, `VERSION_REJECTED`, `SHAPE_CHANGED`
    24 h; `BOT_CHECK` 30 min for the whole ladder.
  - A client in cooldown is asked only when every other client failed in this lookup, and then
    only the one whose cooldown ends first.
- Order, budget (rule 7) and stop rules (rule 4) in `ClientLadder`.
- **Done:** unit tests for each rule; no request after a stop; C13 shows the saving.

> **Observation 2026-10-11 (Phase 1.2 N2, before D1):** from the computer and from the owner's
> phone, WEB_EMBEDDED_PLAYER answered `ERROR` "This video is unavailable" for every video tried
> (also with the embed page's own context); TVHTML5 answered `UNPLAYABLE` "The page needs to be
> reloaded"; WEB answered OK but SABR only; MWEB answered OK with `signatureCipher` streams (token
> need for its streams not yet measured). D1/D2 must re-check the no-token script clients before
> relying on them; the R6 chain's embedded leg is affected too (VISIONOS first hides it).

### D4 — Row check (V) for ladder rows

- If Phase 1.2 N2 is done, reuse its `StreamCheck`. Otherwise build `StreamCheck` exactly as the
  Phase 1.2 plan §2 "Live check V" describes (pass / fail / unknown, rule 6), and N2 later reuses
  it.
  - **N2 is done (`9e15101`):** `extractor-master/.../modules/youtube/StreamCheck.kt` exists
    (one-byte probe, control, budget 4 per key per day, cache, no cookies, nothing logged). D4
    reuses it with the client ID in the budget/cache key and adds only the row rule and the
    cases the probe result does not carry yet (wrong content type, redirect to another host).
- Rule: rows of a client without a `pass` in the last hour need a `pass`; 403 → `TOKEN_NEEDED`;
  `unknown` → that client's rows are dropped for this lookup (the R6 chain's rows are never
  dropped).
- **Done:** fake-HTTP unit tests (2xx, 206, 403, timeout, wrong content type, redirect to another
  host); C5 and C11 behave as expected.

### D5 — S rungs: script clients through the own solver

- The S path: watch page signals (visitor data, signature timestamp, player ID; one fetch per
  lookup), the client asked again with them, challenges from `signatureCipher` (s/sp) and `n`,
  `PlayerScriptRunner.resolve` with the own solver (`solver.OwnPlayerScriptRunner`), the values
  applied, SelfCheck, rows, V.
- Reuse before writing: if the copied extractor's challenge and apply code is not reachable
  (private), write a Master-only equivalent with tests against `player_cipher.json` and
  `player_cipher_ladder.json`, and record "Plan adapted".
- **Done:** fixture tests; C2, C4 and C11 complete in the simulator with a fake runner; an unsolved
  or failed value drops its stream (never a guessed value).

### D6 — Ladder wiring, flag and merge rule

- `MasterYouTubeModule(http, playerScripts, ladder)`: the ladder first; complete → its answer.
  Otherwise the R6 chain runs unchanged; its rows win; the ladder adds only formats (by itag) the
  chain did not give. Details name the client of each row (no address).
- `-Pyft.clientLadder` in `app/build.gradle.kts` (debug only; release `false`); the workflow input
  `client_ladder` (default `false` until D7) in `master-optin-debug-apk.yml`;
  `AndroidBrowserMasterFallback.create` passes the ladder only when the flag is on.
- **Done:** flag-off parity unchanged; in the simulator the ladder is never worse than the chain
  (never fewer rows, never a worse verdict) and wrong rows = 0; CI green.

### D7 — Canary for every client, owner live check, runbook

- `scripts/master-youtube-canary.sh --all <id>` (owner-run, never CI): asks every table client
  once, live; prints each client's class, whether the ladder completes and which rung answered.
- Owner live check on the phone (all flags on): 10 public videos with visionOS switched off by a
  debug-only switch (`-Pyft.ladderSkip=visionos`, BuildConfig, never in release), and the same 10
  with it on.
- Runbook (§6) updated with what the canary showed.
- **Done:** the owner's report; targets in §4 met or new targets proposed with numbers.

## 4. Pass criteria (Phase 1.3 done)

- **Simulator:** wrong rows = 0 in C1–C13. The ladder is never worse than the R6 chain.
  - With the own solver on, complete answers in C2–C8, C11 and C13.
  - C1, C9 and C10: the ladder stops at the first such answer, makes no further request, and
    the result equals the R6 chain's.
  - C12: a clean failure within the budget.
- **Budget (rule 7):** the ladder's own requests stay within 3 player requests, 1 page fetch and 1
  row check per client per lookup; in C2–C8 and C11 the whole lookup makes fewer requests than the
  R6 chain.
- **Live (owner, D7):** visionOS off → at least 8 of 10 public videos complete; visionOS on → the
  R6 rows, plus at most extra formats; no bot check caused by the ladder in the session.
- **Flag off = R6 / main;** CI, `MasterYouTubeParityTest` and `MasterMainMoreSheetTest` green.

Baseline (filled by D1; the ladder columns by D6):

| Scenario | R6 chain, own solver off | R6 chain, own solver on | ladder D only | ladder D + S |
|---|---|---|---|---|
| C1–C13 | — | — | — | — |

## 5. Risks

- **Every no-token client closed at once:** the ladder cannot help. What is left: main's own path
  and MSE recording (`MASTER_KEY_NOTES.md` §3 (b), still only an idea).
- **The TV client** may need visitor data, may meet bot checks sooner or give SABR for some
  videos: measured in D7; the stop and budget rules bound the cost.
- **More identities:** more exposure under YouTube's terms and more chances to be noticed. At most
  4 identities, no fingerprint tricks, no cookies, stop on the first bot check.
- **A D client's n starts to need the script:** its downloads are only slowed, not refused, so V
  may not see it (Phase 1.2 T4 / N2). The canary logs a speed class; the judge cannot decide on
  speed.
- **Table values age.** Updates need an APK (no remote table, by design); the per-client canary
  warns early.
- **Duplicated logic** in the S path when the copied code cannot be reused: tests against main's
  fixtures; the drift check keeps guarding the copies.
- **Cost:** an extra watch page fetch (~166 KB) when the S path runs and the ladder is still
  incomplete.
- **Different formats per client:** the details name the client of each row.

## 6. Runbook (when a client fails)

1. Run `bash scripts/master-youtube-canary.sh --all <public video id>`: which client, which class.
2. `VERSION_REJECTED`: look up a newer version of the same identity in the references (yt-dlp,
   YouTube.js, NewPipe); table version + 1; new APK.
3. `TOKEN_NEEDED` or `SABR_ONLY` on a D client: disable the entry (Master never uses tokens); the
   health cache already skips it in the meantime.
4. `NEEDS_SCRIPT`: nothing to do while the own solver is on (S path).
5. `SHAPE_CHANGED`: main's parser must change first; the copy follows through the drift process.
6. A new no-token client appears in a reference: add it (source and date), run the canary, ship an
   APK.

## 7. Out of scope

Proof-of-origin tokens and attestation, cookies and sign-in, SABR/UMP, DRM, age or geo bypass,
proxies or VPNs, TLS fingerprinting, remote tables or code, MSE recording (a separate idea),
main merge and release.