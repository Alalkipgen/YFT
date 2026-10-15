# Master Key — Phase 1.3 Plan v3: own client ladder at own-solver level — no Master-caused failure (YouTube may change visionOS, Master keeps working)

Status: **plan + prompt, v3.** No code is changed by this document. v3 extends v2 (`f2c144e`,
kept in this file's git history) with the owner's four additions of 2026-10-14: an own parser for
layout changes (**L**), self-healing versions (**A**), an independent HLS leg (**H**) and a weekly
early-warning check (**W**). Every v2 guardrail stays unless §2 marks it **changed** or **new**.
Date: 2026-10-14. Branch: `spike/master-extractor-backup` only; main is not touched and the draft
PR #2 is never merged. **Prerequisite: Phase 1.2 closed — done (`a99e0fb`; §4 G0).**
Reads: `MASTER_KEY_PHASE1_2_PLAN.md` §0–§4 (the level to match), `PROGRESS.md` "R6", "R9",
"Phase 1.2 — done", "Phase 1.2 — N2" and "Phase 1.2 — N7", `MASTER_KEY_NOTES.md` §3–§4,
`docs/YOUTUBE_RISK_REVIEW.md` (P14, P22). Prompt: `MASTER_KEY_PHASE1_3_PROMPT.md` (v3).

Phase 1.2 made the own solver survive YouTube's code changes without ejs: it finds n/sig again
**in the same player** (B, E), asks **YouTube itself** (V), **remembers** what worked (locator
cache) and **proves** it on real players (M1–M11, wrong = 0). v3 brings the client side to the
same level, with a number for the goal: **no Master-caused failure** (§5.1). When YouTube changes
visionOS, Master:

- first **adapts visionOS itself** to what its answer now needs: a newer public version (learned,
  signed, self-healed from public data or bundled), the player script through the own solver,
  sizes from YouTube's own probe;
- **finds the formats again by their shape** when the answer's layout changes (L, as strategy B
  finds n/sig again in the same player), and offers only rows YouTube itself accepts for this
  video;
- **downloads through the answer's HLS delivery on its own leg** when the formats cannot be used
  (H: a standard format, independent of the JSON layout);
- uses other no-token clients **only where a live census on the phone proved them**;
- **remembers** what worked (variant, version, shape map), so the normal lookup stays one request;
- **tells the owner within a week** what changed (W: a one-minute check on the phone, no
  background work);
- never offers a row that YouTube refused, whoever produced it.

## မြန်မာ အနှစ်ချုပ်

- ရည်မှန်းချက်: YouTube ပြောင်းလို့ Master ပျက်ရင် Master code ကြောင့် ပျက်တာ ၀ ("Master-caused failure"
  = ၀၊ §5.1)။ YouTube server ဘက်က ခွင့်ပြုထားတဲ့ လမ်းအားလုံး ပိတ်မှသာ verdict မှန်နဲ့ ရပ်ပြီး တစ်ပတ်အတွင်း
  သိရမယ်။ wrong row ၀ (မပြောင်း)။ APK update မလိုဘဲ။
- v3 မှာ အသစ် (v2 + owner ရဲ့ ၄ ချက်):
  1. **L — Own parser (အရေးအကြီးဆုံး):** answer ပုံစံ ပြောင်းရင် (SHAPE_CHANGED) key နာမည်ကို မယုံဘဲ တန်ဖိုး
     ပုံစံ (itag၊ googlevideo address၊ mime) နဲ့ format တွေကို ပြန်ရှာမယ်။ video နဲ့ ချိတ်စစ်မယ် (videoId၊
     ကြာချိန်)။ canonical JSON ပြန်ဆောက်ပြီး main copy parser (မပြင်ဘဲ) ကိုပဲ row ထုတ်ခိုင်းမယ်။ row တိုင်း
     ဒီ lookup ထဲမှာ V pass + init check (file အစ bytes မှန်၊ encryption မပါ) ရမှ ပြမယ်။ unknown ဆို မပြ။
  2. **A — Self-healing version:** VERSION_REJECTED ဆို public source (token/cookie မလို၊ data ပဲ၊ code
     မဟုတ်) က version အသစ်ကို app ကိုယ်တိုင် ယူမယ်။ source တစ်ခုချင်းကို D0 မှာ live တိုင်းမယ်။ visionOS
     အတွက် မရရင် အကြောင်းရင်း ရေးမယ်။ ရရင် D8 အစား သုံးဖို့ owner နဲ့ ညှိမယ်။ version ခန့်မှန်း/တိုးစမ်း လုံးဝ မလုပ်။
  3. **H — HLS leg သီးသန့်:** format ပုံစံ ပျက်နေရင်တောင် `hlsManifestUrl` တစ်ခုတည်း + video ကြာချိန်နဲ့
     download ရမယ် (app ထဲမှာ HLS code ရှိပြီးသား)။ token လိုမလို D0 မှာ အဆင့်လိုက် တိုင်းမယ်။
  4. **W — Early warning:** census tool ထဲမှာ "Weekly check" (≤ ၁ မိနစ်၊ ≤ 2 MB၊ mobile data)။ ပြီးခဲ့တဲ့
     run နဲ့ diff ပြမယ် (class၊ version၊ shape)။ background request လုံးဝ မရှိ (owner နှိပ်မှ run)။
- ဆက်တည်: Wi-Fi မရှိ → live တိုင်းတာ အားလုံး mobile data။ D8 မဆုံးဖြတ်ရသေး (D0 ရဲ့ self-heal ရလဒ်ပြီးမှ
  ဆုံးဖြတ်)။ Phase 1.2 ပိတ်ပြီး (G0 ✅ `a99e0fb`၊ owner 2026-10-14)။ main မထိ၊ PR #2 merge မလုပ်။
- M5 (Phase 1.2 က ကျန်တာ၊ es6 player ဖိုင် ၈ ခု၊ solver ဘက်) ဒီ plan ထဲ မပါ — owner ဆုံးဖြတ် (§9 ၆)။
- မထည့်: SABR client၊ PO token၊ MSE recording၊ TV client၊ bypass။
- သက်သေ: simulator class အသစ် L1–L16 (key ရွှေ့/နာမည်ပြောင်း၊ decoy၊ encrypted)၊ V10–V11 (HLS)၊
  Y1–Y6 (version) + v2 class တွေ × fixture ၅ ခု → wrong = ၀၊ MCF = ၀။ ဖုန်းပေါ်မှာ live answer ကို memory
  ထဲမှာပဲ ပုံစံပြောင်းပြီး (`ladderMutate`) L က row ပြန်ရှာနိုင်ကြောင်း စစ်မယ်။
- မကယ်နိုင်တာ (server ပိတ်): ခွင့်ပြုထားတဲ့ client အားလုံး token လို၊ SABR-only၊ client ဖယ်ရှား၊ bot check၊
  DRM။ အဲဒီအခါ verdict မှန်နဲ့ ရပ်ပြီး W က သတိပေးမယ်။
- Task: G0 Phase 1.2 ပိတ် (✅ `a99e0fb`) → D0 census + weekly check → D1 simulator → D2 table + judge → D3 health →
  D4 checks → D5a A/S/Z → D5b L → D5c H → D6 O + wiring → D7 ဖုန်းစစ် + runbook → D8 (ဆုံးဖြတ်ရန်)။
- Bot check (owner 2026-10-15): bot check ပြီးရင် R6 မပို့တဲ့ request ဘယ်တော့မှ မပို့။ R6 လို video (lookup)
  တစ်ခုချင်း: census က video တိုင်းမှာ watch page တစ်ခါ + visionOS ကို page ရဲ့ visitor data နဲ့ တစ်ခါ၊ ပြီးရင်
  နောက် video ဆက်။ visitor data ပါပြီးသား request ကို bot check ဒါမှမဟုတ် HTTP 429 → run ရပ် + ၃၀ မိနစ်။
  ladder: ကိုယ်ပိုင် rung ရပ်၊ R6 chain ယခင်အတိုင်း။ IP/network ပြောင်းတာ မလုပ် (bypass)။
- Run mode (owner 2026-10-14): task တစ်ခု ပြီးတိုင်း agent က "✅ Dx ပြီးပြီ" စာတိုရေးပြီး ဆက်လုပ်မယ်။
  ဖုန်းစစ် (D0 census — အဲဒီအတောအတွင်း D1 လုပ်မယ်၊ D7)၊ owner ဆုံးဖြတ်ချက်၊ STOP ပေါ်မှပဲ ရပ်မယ်။
- ခက်ခဲမှု: High (D5b High; D3၊ D5a၊ D5c၊ D6 Medium–High)။

## 0. Why v3

### 0.1 What v3 adds to v2, and why

| Addition | Gap in v2 | v3 |
|---|---|---|
| **L** own parser | Layout changes stayed detect-only. One moved or renamed key breaks R0, A, S, Z and every O rung at once, because all of them read answers through the same copied parser; Master then waits for main's parser and an APK. | Find the formats by value shape, bind them to the video, rebuild canonical JSON and let the unchanged copied parser build the rows; every L row passes V and the init check in that lookup (D5b). |
| **A** self-healing version | ≤ 2 bundled alternates, then D8 or an APK. The owner has no computer, and R9's signing tool is a shell script for one. | D0 measures public version sources per identity; a proven source lets the app follow a version change by itself (D5a). D8 stays undecided until D0. |
| **H** independent leg | H ran only after the adaptive rows were judged SABR-only or token-refused, from the copied parser's fields. | H needs only a located `hlsManifestUrl` and the video's length, so it also works when no format can be read (D5c). HLS is the standard Apple's players read; its layout does not follow InnerTube's JSON. |
| **W** early warning | A change was noticed only when a lookup failed. | A weekly ≤ 1 min check on the phone shows the diff against the last run: classes, versions, shape (D0, D7). Owner-started only. |
| Goal as a number | "Master keeps working" had no failure definition. | Master-caused failure (MCF) = 0 and wrong = 0 (§5.1), in the simulator and live. |
| Constraints | A Wi-Fi run, an MSE phase and TV as options; v2's video list had 11 entries. | Mobile data only; SABR clients, PO tokens, MSE recording, TV and any bypass excluded; Phase 1.2 closed first (G0, done at `a99e0fb`); PR #2 never merged; 10 numbered videos. |

### 0.2 Evidence against v1 (from v2, still true)

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

| Own solver (Phase 1.2) | Own client ladder (v3) |
|---|---|
| Reference ejs; corpus parity: own = ejs 260/260 | Reference the R6 chain (visionOS first); parity: ladder = R6 rows and one request while visionOS is healthy |
| P: pattern (Phase 1.1 core), fast path | R0: visionOS asked exactly as R6 asks it, fast path |
| B: find n/sig again **in the same player** by behaviour, not by names | **L**: find the formats again **in the same answer** by value shape, not by key names; the copied parser still builds the rows |
| — | Requirement rungs on visionOS itself: **A** version (learned, signed, self-healed, bundled), **S** player script via the own solver, **Z** sizes from YouTube's probe |
| E: real page, only after an environment error | **H**: the HLS leg (a standard delivery, independent of the JSON layout); **O**: other no-token clients, census-live only |
| SelfCheck + V (YouTube judges) | Row rules + SelfCheck + V; for L rows V + init check in every lookup; H check with the length binding |
| Locator cache per player version | Health v2: per client the working variant, the learned version and the L shape map |
| M1–M11 (+ M9b, M9c) mutations of 32 real players, wrong = 0 | Classes V1–V11, L1–L16, Y1–Y6, T1–T3, H1–H2, F1–F4 on 5 base fixtures, plus L mutations of live answers on the phone (in memory); wrong = 0, MCF = 0 |
| N7 phone canary | D0 census (gate) + W weekly check with diff (early warning) |

## 2. Guardrails

All R6, Phase 1.1, Phase 1.2, v1 and v2 guardrails stay. v3 restates them and marks changes:

1. **Flag** (v1): `-Pyft.clientLadder` (default `false`, BuildConfig `CLIENT_LADDER_ENABLED`,
   never in release); acts only with `masterCapture`; S rungs only with `ownSolver` too. Flag off
   = R6 + Phase 1.1/1.2: `MasterYouTubeParityTest` and `MasterMainMoreSheetTest` unchanged, green.
2. **Copy, never edit** (v1): main's five copied YouTube files (`YouTubeClientProfile`,
   `YouTubeExtractor`, `YouTubePlayerResponseParser`, `YouTubeSessionAuth`, `YouTubeUrls`) stay
   byte-identical (drift check); new code only in `modules/youtube/ladder/`; the copies' internal
   API may be used. **L gives the copied parser canonical JSON through its existing `parse` and
   never changes a copied file.**
3. **No token, no cookie, no account** (v1). `NoPoTokenProvider` stays. **Excluded by the owner
   (v3): SABR clients (SABR/UMP), PO tokens, MSE recording, the TV client and any bypass** — none
   of them is a rung, an experiment or a later phase of this plan.
4. **No bypass; the ladder never decides a refusal** (v1; bot check **changed**, owner
   2026-10-15): age check, video verdict or DRM → stop, the R6 chain decides; bot check → the
   ladder's own rungs stop, plus a 30 min ladder-wide cooldown, and the R6 chain runs as today:
   after a bot check nothing is sent that R6 would not send (the census, for each video as R6
   answers each lookup: the watch page once and visionOS once more with the page's visitor data,
   as R6 asks it; a bot check on a request that already carried visitor data stops the run);
   never a changed IP or network to avoid a bot check; ladder rows merge only when the chain did
   not end in a terminal verdict.
5. **Identities and versions (changed).** At most 4 identities, all already in main's code —
   visionOS, Android, embedded, MWEB. Versions in this order: the learned working version
   (health); a signed version (D8, only if approved); a **self-healed** version; ≤ 2 bundled
   alternates listed by a public reference (yt-dlp, YouTube.js or NewPipe) with source and date.
   **Self-healed:** only from a source D0 proved usable for that identity (public, no token, no
   cookie, data only: one pattern-matched value, never code); a strict version pattern per
   client; newer than the bundled version; the version field only (user agent and device stay);
   fetched at most once a day; kept only after the next answer judges OK and a row passes V.
   Never invented, counted up or brute-forced; no TLS or fingerprint tricks.
6. **Row checks (changed: L rows, init check, H binding).**
   - V (v2): the shared `StreamCheck`, only in a user-started lookup, only on the host YouTube
     returned, the download's headers without cookies, Range ≤ 64 KiB, at most 1 per client per
     lookup — **except L rows: one V per offered row (≤ 8), reading the first 4 KiB (≤ 64 KiB)
     so the init check runs on the same bytes (no extra request)**; `unknown` is never `pass`.
   - **Init check (new, L rows):** the content type matches the row's type; the container start
     matches (`ftyp`/`styp`/`moof`/`sidx`, EBML); no encryption marker in the bytes read (MP4
     `encv`, `enca`, `sinf`, `tenc`, `pssh`; WebM content encryption); the total size in
     `Content-Range` equals the row's size when one is known.
   - H check (v2, extended): every address under `googlevideo.com`; the master playlist and one
     variant playlist, ≤ 256 KiB each; the first segment of that variant, Range ≤ 64 KiB; content
     type and container start must match (MPEG-TS sync byte, `ftyp`/`styp`/`moof`, EBML); **no
     `#EXT-X-KEY` or `#EXT-X-SESSION-KEY` other than `METHOD=NONE`**; the playlist's total
     duration within ±2 s of the video's length — **the binding: H rows are never offered
     without a known length**; at most 1 per client per lookup.
   - Z (v2): the existing one-byte probe's total size (`Answered(totalBytes)`) replaces a missing
     `contentLength`, only for rows about to be offered, at most 8 per lookup.
7. **Budget (changed).** A healthy, complete visionOS answer stays the whole lookup (one
   request). Per lookup the ladder itself makes at most 3 player requests, 1 watch page fetch,
   1 V and 1 H check per client, 8 Z probes and **≤ 8 V for L rows**; **a version source at most
   once a day per identity (≤ 64 KiB, cached)**. Same-lookup memo (v2): a request of the R6 chain
   byte-identical to one the ladder already made in this lookup (method, address, body, headers)
   is answered from memory once; never across lookups, never for media hosts. **Census: a full
   run ≤ 6 MB; the weekly check ≤ 60 s, ≤ 2 MB, ≤ 20 requests.**
8. **Health on the phone (changed).** `files/master/client-health.json`: client ID, class, time,
   counts, the working variant (version index, S, H), **the learned version string (public) and
   the L shape map (key paths only, no values)**; no address, token, visitor data or video ID.
   No remote code. Only with the owner's D8 decision: a signed identity section over R9's envelope
   (data only; the bundled table stays the fallback).
9. **Never a wrong row (changed).** Rows come only from the copied parser — **on the answer itself
   or on L's canonical JSON** — and pass `MasterSiteModule`'s row check; S values only after
   SelfCheck; a client without a pass in the last hour needs a V (or H) pass; **L rows need a V
   pass and an init-check pass in this lookup, always; `unknown` → not shown.** Merge rule (v2):
   a client whose rows V proved refused in this lookup (403 with an open control) loses those
   rows, whoever produced them (ladder or R6 chain; matched by address in memory, never logged);
   otherwise the R6 chain's rows win and the ladder adds only formats the chain did not give.
   "Never worse than R6" = never fewer working rows, never a worse verdict.
10. **Spike only (changed).** No main merge, tag or release; **the draft PR #2 (spike → main, "do
    not merge") stays a draft and is never merged.**
11. **No background work (new).** The census and the weekly check run only after the owner's tap,
    while their screen is open; no WorkManager, AlarmManager, JobScheduler, foreground service,
    push or other scheduled request; leaving the screen stops them. Lookup checks run only inside
    a user-started lookup (v2).
12. **Mobile data only (new).** Every live step is planned for the owner's mobile data (no
    Wi-Fi); budgets are in MB; no step needs a computer.

## 3. Architecture

```
lookup (Master, clientLadder on)
  fast path: health says visionOS R0 passed in 24 h -> R0 exactly as R6 asks (1 request)
  order    = health v2: learned variant first; pass (24 h) -> unknown -> cooldown (last)
  for each client, within the budget:
    answer = ask(client, its learned variant or the base)   copied request builder, no cookie
    class  = judge(answer)                                   v2 class table (below)
      AGE_CHECK / VIDEO_VERDICT / BOT_CHECK -> stop (bot check: cooldown); the R6 chain decides
                         (after a bot check nothing R6 would not send; guardrail 4)
      VERSION_REJECTED   -> A: learned -> signed (D8) -> self-healed (D0-proven source,
                            <= 1 fetch a day) -> bundled alternates -> next client
      NEEDS_SCRIPT       -> S: page signals once; ask again with signature timestamp + visitor data;
                            own solver (PlayerScriptRunner.resolve) + SelfCheck; V
      INCOMPLETE (sizes) -> Z: sizes of the rows to offer from the one-byte probe
      SHAPE_CHANGED      -> L: locate by value shape -> bind to the video -> canonical JSON ->
                            copied parser -> offer set (<= 8) -> V + init check on every row
      DIRECT             -> V when needed
      SABR_ONLY / TOKEN_NEEDED / REFUSED / other -> the adaptive path is not complete
    adaptive path not complete and an HLS address located (the field or L's locate)
                         -> H leg: H check with the length binding -> one HLS row
    still not complete   -> next client (O rungs: census-live only)
    health.record(client, class, variant, version, shape map)
    complete (P22 rule: a video merged with sound and the audio track itself) -> done
  not complete -> the R6 chain runs unchanged behind the same-lookup memo;
                  merge rule (guardrail 9)
```

- **Classes:** v2's table (`DIRECT`, `NEEDS_SCRIPT`, `SABR_ONLY`, `INCOMPLETE`, `REFUSED`,
  `VERSION_REJECTED`, `SHAPE_CHANGED`, `TOKEN_NEEDED`, `AGE_CHECK`, `VIDEO_VERDICT`, `BOT_CHECK`),
  decided from the answer, the HTTP result and the checks, never from the client's name.
  `SHAPE_CHANGED` now has two kinds: **moved** (the copied parser finds no formats while
  format-like objects exist elsewhere) and **partial** (the copied parser skips rows for a missing
  field that format-like objects carry under another name). The judge also records `hls` (an HLS
  address located) and the answer's shape fingerprint (for W).
- **L — own parser** (`ShapeLocator`, `ShapeNormalizer`):
  1. Scan the parsed answer (bounded: depth ≤ 16, ≤ 100 000 nodes, answer ≤ 4 MB) for
     format-like objects: an itag-like integer (1–999, number or digit string); an https address
     on a `googlevideo.com` host whose `itag` parameter equals it, or a cipher-like string
     carrying such an address; a mime-like string (`video/…` or `audio/…` with `codecs="…"`).
     Key names are never trusted; values are.
  2. Bind to the video: exactly one object carrying the requested `videoId` with a digit
     `lengthSeconds` (else no L rows); each candidate's duration (an `approxDurationMs`-like value
     or the address's `dur`) within ±1.5 s of that length (candidates without one are dropped);
     candidates are grouped by their parent list; two groups that both bind but disagree →
     ambiguous → no L rows.
  3. Map each candidate's fields to canonical names by value shape (address, cipher, mime, width,
     height, fps, bitrate, size, duration, quality label, audio fields); a required field (itag,
     address or cipher, mime) without exactly one match → that candidate is dropped.
  4. Playability: the copied parser's verdict, or exactly one located status object; anything
     but `OK` → stop (= R6); none found → no L rows.
  5. Canonical JSON (`playabilityStatus`, `videoDetails`, `streamingData` with `formats`,
     `adaptiveFormats`, `hlsManifestUrl`, canonical keys) → `YouTubePlayerResponseParser.parse`
     (copied, unchanged): main's row rules (labels, sizes, protection, address checks) apply as
     they are.
  6. Offer set: the rows the app would offer (per height the best AVC, then VP9/AV1 row; the best
     M4A and Opus audio), ≤ 8; each needs V + init check in this lookup; failed or `unknown`
     rows are dropped; still incomplete → H leg → O rungs → R6 chain.
  7. Remember the shape map (located paths ↔ canonical names, keyed by the shape fingerprint):
     the next lookup normalizes without a full scan; V and the init check still run on every L
     row.
  8. Page signals: when the copied `pageSignals` misses the player ID, the signature timestamp or
     visitor data, L locates them by shape (the player script address; the timestamp from the
     player script the own solver reads; visitor data from the answer's response context), for S
     only.
- **H — independent HLS leg** (`HlsLeg`, `HlsCheck`):
  1. The HLS address: `streamingData.hlsManifestUrl`, else exactly one located https string on
     `manifest.googlevideo.com` under `/api/manifest/hls_variant/` or
     `/api/manifest/hls_playlist/`; none or several → no H.
  2. The video's length: from the copied parser, L's binding or the page (when fetched); none →
     no H rows (guardrail 6).
  3. H check pass → one row of kind HLS (the master address) for the app's existing HLS path
     (`DefaultVariantResolver` → `HlsManifestParser`, `HlsAudioPairing` for a separate audio
     group; download by `HlsTransferEngine`; `OkHttpMediaValidator`'s HLS facts reused),
     labelled HLS in details, resolution and audio form from the master playlist.
  4. Used when the adaptive path cannot complete for any reason (SABR-only, token-refused rows, a
     layout change L cannot finish, rows failing V, an answer with HLS only). When the adaptive
     rows are complete, H makes no request in a lookup; the census and W still measure it.
- **A — versions** (`VersionSource`): per identity only the source D0 proved usable (§4 D0),
  fetched after `VERSION_REJECTED` of the base (and learned) version, at most once a day;
  guardrail 5's order and bounds.
- **W — weekly check:** the census tool's second mode (§4 D0), owner-started, with the diff
  against the stored last run.
- **Row builder parity** (v2): when R0 completes, the ladder's rows equal what the R6 chain builds
  from the same answer. L never runs on a complete answer, and the L round-trip (D1) proves that
  canonical JSON of an unchanged answer gives the same rows.
- **Where it plugs in** (v2): `MasterYouTubeModule(http, playerScripts, ladder = null)`; `null` =
  R6. The app passes a ladder only with `BuildConfig.CLIENT_LADDER_ENABLED`.
- **New Master-only files** (`extractor-master/.../modules/youtube/ladder/`): v2's
  `ClientTable.kt`, `AnswerJudge.kt`, `ClientHealth.kt`, `ClientCensus.kt`, `HlsCheck.kt`,
  `Rungs.kt` (A, S, Z), `LookupMemo.kt`, `ClientLadder.kt`, plus `ShapeLocator.kt`,
  `ShapeNormalizer.kt`, `ShapeFingerprint.kt`, `InitCheck.kt`, `VersionSource.kt`, `HlsLeg.kt`,
  `CensusDiff.kt`; `StreamCheck` reused.

## 4. Tasks

| ID | Task | Depends on | Difficulty | Status |
|---|---|---|---|---|
| G0 | Phase 1.2 closed (gate; Phase 1.2's agent and the owner) | — | Low | Done (`a99e0fb`) |
| D0 | Census tool: full census + weekly check (W) with diff; live census on mobile data (gate); version sources; H token step | G0 | Medium | Code done (`1135afe`); census runs 1–4 not valid, not counted (§5); bot-check rule (owner, 2026-10-15; `5cb781c`), per video (`81388f8`); valid runs 1–3 done (2026-10-14 23:06, 2026-10-15 00:37 and 02:05 UTC); gate (§5, §9 item 9): live Android, MWEB; provisional H, S; not live embedded; usable sources: the reference tables (GitHub raw, approved for lookups with conditions, §9 item 3); D8 dropped; 1 Weekly check pending |
| D1 | Change simulator, parity gate, L round-trip, baseline | D0 (classes) | Medium–High | Done: simulator + R6 baseline (`f35b78f`; T1b `5cb781c`, per video `81388f8`); parity gate with D6, L round-trip with D5b |
| D2 | Client table v2 + answer judge (shape kinds, HLS locate, fingerprint) | D1 | Medium | TODO |
| D3 | Health v2, order, budget, stop rules (learned version, shape map) | D2 | Medium–High | TODO |
| D4 | Row checks: V (reuse), init check, H check, Z | D2 | Medium | TODO |
| D5a | visionOS rungs A (with self-healed versions), S, Z | D3, D4 | Medium–High | TODO |
| D5b | Own parser L (locate, bind, canonical JSON, copied parser) | D3, D4 | High | TODO |
| D5c | H leg (independent HLS) | D4, D5b (locate) | Medium–High | TODO |
| D6 | O rungs (census-live only), memo, wiring, flag, merge rule | D5a–D5c | Medium–High | TODO |
| D7 | Owner live check (with live L mutations), weekly check, runbook | D6 | Low–Medium | TODO |
| D8 | Signed identity data over R9 (owner decision, after D0) | D2 | Medium | Dropped (owner, 2026-10-15; §9 item 4) |

G0 is done (`a99e0fb`); D0 is next: it decides which rungs and sources exist and starts the
weekly check. Use the highest reasoning level for D3, D5a, D5b, D5c and D6. D8 only after the
owner's yes. **Run mode (owner, 2026-10-14): continuous** — after each task the agent pushes,
posts a one-line note (task, commit, CI) and goes on; it pauses only for the owner's phone steps
(D0's census, during which it does D1; D7), owner decisions and the prompt's stop rules.

### G0 — Phase 1.2 closed (gate) — done (`a99e0fb`)

Phase 1.3 starts only when `PROGRESS.md` has the Phase 1.2 closing entry. **Done on 2026-10-14**
(`a99e0fb`, owner): `PROGRESS.md` "Phase 1.2 — done (owner closed it on 2026-10-14)":
- Phase 1.2 §4 results with evidence: ladder wrong 0 in every class, with and without V;
  simulated V 344/352 = 98 % of M1–M9; faults 0 wrong / 0 missed; phone speed and live check
  passed; flag off = main, CI green (`e5cad15`). Offline survival 0 % by design ("V on every
  signature": no signature is used without V; none wrong).
- N4b closed (the ladder never uses worker B's M9bs wrong value); N2's n rule stays PENDING by
  decision (a known limit); the private-video check was skipped by the owner.
- Carry-over, **not in this plan:** M5 (ladder 24 = ejs 24: on 8 es6 player files every step
  throws at init, so the init-error rule declines right answers; safe, none wrong). Phase 1.3
  calls the own solver for S but does not change it; whether M5 becomes its own task is the
  owner's decision (§9 item 6) and does not block D0.

### D0 — Census tool, weekly check and live census (gate)

- `ladder/ClientCensus.kt`, `ladder/ShapeFingerprint.kt` and `ladder/CensusDiff.kt`
  (extractor-master, offline-tested with `FakeExtractorHttpClient`) and a debug-only owner tool
  **"YFT client census"** with two modes, **Full census** and **Weekly check** (launcher entry
  only with the Master flags on; Start → report → Copy result), built like "YFT stream check"
  and the N7 solver canary; it reuses `StreamCheckMeasurement`'s request and solve steps for the
  script clients.
- Videos, numbered 1–10: 1–3 the three of `MASTER_KEY_PLAN.md` §4.3 (short progressive, 1080
  ladder, a Short); owner picks: 4 a 2160p video, 5 a music video whose streams need a signature,
  6 a made-for-kids video, 7 a video that disallows embedding, 8–10 three ordinary videos; every
  video ≤ 30 min. Defaults the owner may change (`ClientCensus.DEFAULT_VIDEOS`): 4 the stream
  check's 2160p60 video; 7 (set by the agent, 2026-10-15) a public 3:30 video whose owner disabled
  embedding (checked: oEmbed 401; the embed page says playback on other websites is disabled).
- **Full census**, per video, in this order. **Bot checks** (owner, 2026-10-15; guardrail 4; per
  video since census runs 2–4): R6 answers each lookup on its own, and so does the census. A bot
  check on a video's first request (visionOS, no visitor data: R6's own first request) gets what
  R6 sends next and nothing else for that video — its watch page once and visionOS once more with
  the page's visitor data (`R0+vd`, R6's `visionOsInChain` request; V on one row of that answer) —
  and the run goes on with the next video; no newer version is tried on that video. A bot check on
  a request that already carried visitor data (`R0+vd`, or a client asked with the page's visitor
  data) or HTTP 429 stops the run, without a retry (then no new run for 30 min); a bot check on
  any other request without visitor data ends that video only:
  1. visionOS as R6 asks it: class; formats, heights, audio and sizes (counts); V on one AVC
     row; the shape fingerprint; HLS address present → H check with the **token step** (master
     playlist, variant playlist and first segment: 2xx or 403 each), variants, highest
     resolution, audio form (muxed or separate) and codecs.
  2. visionOS again with the page's signature timestamp and visitor data, only when (1) carried
     `signatureCipher` (S).
  3. Video 1 only: visionOS with each bundled alternate version (≤ 2): class; V (so A is never a
     fake rung).
  4. Android: class; progressive only or not; V.
  5. embedded (the page's version and embed context, as main): class; S through the own solver;
     V.
  6. MWEB: class; S through the own solver; V (owner stream checks: all four reads 403, so
     expected not live; kept as one cheap re-check).
- **Version sources** (once per run, ≤ 64 KiB each; no token, no cookie, data only):

| Identity | Candidate public source | Recorded per run |
|---|---|---|
| embedded, MWEB | the client's own YouTube page config (`INNERTUBE_CLIENT_VERSION`) | value found; pattern; accepted (the rung's own request) |
| visionOS | (1) YouTube's own answers and pages: a field naming a VISIONOS version; (2) the App Store's public lookup for YouTube's visionOS app, if it is listed; (3) a reference's public client table (yt-dlp, YouTube.js) read as text: one pattern-matched value | per source: reachable; exactly one value; same numbering as `clientVersion` ("1.02" today); newer; accepted (1 visionOS request + V on video 1, only for a new valid value) |
| Android | (1) the Play Store's public app page; (2) a reference's public client table | as visionOS |

  - A source is **usable** for an identity when in 3 of 3 runs it is reachable, gives exactly
    one value matching the identity's pattern, and that value is accepted (OK + V pass) or equals
    the bundled one. Expectations, to be measured, not assumed: the web-family pages are usable;
    visionOS's "1.02" does not look like an App Store release number, so for visionOS only a
    reference's table may be usable; Android's store page may show no version ("varies with
    device").
  - A host Master does not use yet (Apple, Google Play, GitHub raw for a reference) is fetched by
    the census tool only; using it in lookups needs the owner's yes (§9).
- Budget per video: 1 page fetch, ≤ 6 player requests (+ ≤ 2 alternates on video 1), ≤ 5 V,
  1 H check. A full run ≤ 6 MB on mobile data.
- Report: classes, counts, verdicts, times and bytes; shape fingerprints (key names only); public
  version strings; videos by their list number. No address, value, visitor data or player text.
- Runs: **3, all on mobile data (no Wi-Fi), at least an hour apart, ideally at different times of
  day**, on the owner's normal mobile data only (never a changed IP, airplane mode or another
  network to avoid a bot check: that is a bypass; owner, 2026-10-15).
- **Gate** (owner, 2026-10-15, §9 item 9: the per-video bot-check rule asks H and the O rungs only
  on videos without a bot check, so only the videos where a rung was actually asked count): a rung
  is **live** when its rows passed V (or H) on ≥ 60 % of the videos it was asked on in ≥ 2 of 3
  valid runs, it was asked on ≥ 4 videos in total over the 3 runs, and wrong = 0. Asked on fewer
  than 4 videos in total → **provisional**: wired only as a fallback after visionOS fails, V on
  every row, and the weekly check keeps measuring it until it reaches the minimum. An alternate
  version is live when it was accepted in ≥ 2 of 3 runs; a version source when it is usable
  (above). Only live rungs (provisional ones as above) and usable sources become active; the
  others are stored as "measured, not active" with the date. Results go into §5 and `PROGRESS.md`.
- **Weekly check (W)**, ≤ 60 s, ≤ 2 MB, ≤ 20 requests, on mobile data:
  - Videos 1 and 5: visionOS as R6 asks it (class, counts, fingerprint, V on one row); the H
    check on video 1; the version sources only if not fetched in 24 h; Android, embedded and MWEB
    once on video 1 (class, counts, fingerprint; V on one row when there are rows); from D6 on
    also the ladder's own lookup of video 1 (which rung answered). Bot checks as in the full
    census (videos 1 and 5 each on its own); when video 1 met one, its other clients are not
    asked.
  - Its summary is stored in `files/master/census-last.json` (the last 8 runs: classes, counts,
    fingerprints as key-path lists and hashes, public version strings, verdicts, times, bytes;
    no address, value, visitor data or video ID).
  - First line: `NO CHANGE`, `CHANGED (n)` or `BROKEN (n)`, and the days since the last run.
    Then one line per change: class (per client); version (accepted → rejected; a source's value
    changed); shape (paths added or removed — a removed path the copied parser reads is marked
    **BREAKING**); H (verdict, token step, highest resolution); the rung that answered. Each line
    names its runbook step (§7).
  - No background work (guardrail 11); the screen shows when the last check ran.
- **Done:** both modes in the opt-in debug APK; unit tests for the diff (each change kind;
  identical runs = `NO CHANGE`), the report sanitizer and a static check that the census code
  uses no background API; the owner's 3 full runs and one weekly check; the census table, the
  version-source table and the gate decision in §5 and `PROGRESS.md`; the owner told which rungs
  and sources are live. If neither H nor any O rung is live, stop and ask the owner (§9): Phase
  1.3 then keeps A, S, Z and L.

### D1 — Change simulator, parity gate and baseline

- `MasterClientLadderSimTest` (offline): `FakeExtractorHttpClient`, `FakePlayerScriptRunner`,
  main's fixtures through `yft.siteFixtures`; changes made in code; HLS playlists made in code (no
  YouTube data captured or committed).
- Base answers (5): `player_visionos.json`, `player_visionos_ladder.json`, `player_ok.json`,
  `player_4k.json`, `player_4k_av1_only.json`.
- Change classes (each tied to a real or likely change):

| ID | Change (made in code) | Real analogue | Remedy |
|---|---|---|---|
| V1 | HTTP 400 / "update the app" for the base version | version retired | A (alternates, a usable source or D8) |
| V2 | formats carry `signatureCipher` | client starts to need the script | S |
| V3 | `contentLength` removed | partial answer (P22's M4A without a size) | Z |
| V4 | adaptive SABR-only, HLS kept | SABR rollout | H |
| V5 | adaptive rows 403 at the media host, HLS kept | token rule (android_vr, 2026.08.17) | V → H |
| V6 | adaptive and HLS 403 | token rule on every visionOS path | O (live only) |
| V7 | non-definite refusal ("not on this app", made for kids) | per-video refusal | O (live only) |
| V8 | formats under a moved key | response shape change | detect only → O |
| V9 | every request 400 / refused | client closed | O (live only) |
| T1 | bot check from every identity and the page | IP flagged | own rungs stop + cooldown; the R6 chain runs = R6 |
| T1b | bot check only for visionOS asked without visitor data (owner, 2026-10-15), per video: three lookups in a row each meet it | carrier IP (census runs 1–4, 2026-10-14) | own rungs stop + cooldown; the R6 chain (watch page, visionOS with the page's visitor data) completes each lookup = R6 |
| T2 | age check | — | stop, ladder rows dropped = R6 |
| T3 | private, removed, geo, DRM | — | stop = R6 |
| H1 | visionOS failed 10 min ago | repeated lookups | skipped; one request fewer |
| H2 | visionOS needed S or H on the last lookup | after a YouTube change | learned variant first |
| F1 | wrong or unsolved S value | solver fault | dropped (SelfCheck / V) |
| F2 | V `unknown` (timeout) | network | that client's ladder rows dropped |
| F3 | H: segment 403, wrong content type, wrong start bytes, another host, wrong duration | HLS faults | H rows dropped |
| F4 | an answer about another video | response mix-up | `RESPONSE_CHANGED`, dropped |
| V10 | formats removed, only `hlsManifestUrl` left | delivery change | H |
| V11 | HLS playlist or segment 403, adaptive rows fine | token rule on HLS only | R0 (H unused) |
| L1 | `streamingData` moved under another parent | re-nesting | L |
| L2 | `formats` / `adaptiveFormats` renamed | key rename | L |
| L3 | the address key renamed | key rename | L |
| L4 | mime renamed, or split into type and codecs | field split | L (dropped when not mappable) |
| L5 | `signatureCipher` renamed | `cipher` → `signatureCipher` (2020) | L + S |
| L6 | types changed (itag or size as string ↔ number) | type drift | L |
| L7 | list → object map keyed by itag | container change | L |
| L8 | decoy: another video's format list (another length) | ads, end screens | L binds; this video's rows only |
| L9 | hard decoy: same length, nothing binds | — | no L rows (clean) |
| L10 | `playabilityStatus` moved | re-nesting | L when found `OK`; else no rows |
| L11 | terminal answer (age, login, private) with format-like objects | — | stop = R6 |
| L12 | protection marker renamed, streams encrypted | DRM layout change | init check drops all (clean) |
| L13 | `hlsManifestUrl` moved | re-nesting | H via L's locate |
| L14 | page signals moved (player ID, timestamp, visitor data) | page change | S via L's page locate |
| L15 | half the formats in the old layout, half in a new one | partial rollout | both read, no duplicates |
| L16 | 10 000 unrelated objects, deep nesting | noise | bounded scan; time metric |
| Y1 | base version rejected; the source gives a newer valid one | version retired | A (self-healed) |
| Y2 | the source gives garbage, a bad pattern, an older or the same value | source change | ignored → alternates |
| Y3 | the source times out or is unreachable | network | ignored, no retry → alternates |
| Y4 | the self-healed version is rejected too | — | dropped, not stored → alternates |
| Y5 | many lookups in a day | — | ≤ 1 source fetch a day (fake clock) |
| Y6 | the source also names other identity fields | tampering | only the version is read |

- Every L case runs three times: V `pass`, `fail` and `unknown` on the L rows.
- Columns: R6 chain (own solver off), R6 chain (own solver on), ladder (**live-backed**: R0 and
  the local remedies A (the alternates and sources D0 proved), S, Z and L, plus only the H and O
  rungs D0 proved live), ladder (all rungs, simulated).
- Measured per case: complete / partial / clean stop / **wrong row** / **MCF** (§5.1); player
  requests, page fetches, V, init checks, H and Z used. A wrong row: any row the case marks as
  broken (403, SABR, protected, unsolved or wrongly solved, failed H check), a row after an age or
  video verdict, a row of another video (a decoy's row included).
- **Parity gate** (the 20 scenarios of `MasterYouTubeParityTest`, flag on): where visionOS's first
  answer is complete, the same requests, rows, order, labels and sizes as R6 (details may only gain
  ladder lines); where the chain ends in a terminal verdict, the same verdict and no ladder row;
  elsewhere every working R6 row is kept.
- **L round-trip:** canonical JSON built by L from each of the 5 base fixtures and the 20 parity
  answers gives exactly the copied parser's rows of the original.
- **Done:** the suite runs in CI's Gradle test step; the R6 baseline (every class, V10–V11,
  L1–L16 and Y1–Y6 included), the parity and round-trip results are written into §5.
- **As built (D1, 2026-10-14):** `MasterClientLadderSimTest` + `SimWorld` / `SimCases` /
  `SimMeasure` (test sources, CI's `:extractor-master:test`). One simulated YouTube per base answer:
  every identity answers from the base's formats with its own made-up address (visionOS direct
  with an HLS address; embedded, MWEB and the page's inline answer with `signatureCipher` + `n`;
  Android = main's progressive answer); a row is judged by what the media host would do with its
  address (403, unsolved or wrong `n`/`sig`, another video, encrypted, failed H, unchecked).
  Adapted: the columns R6 off / R6 on are filled; the ladder columns, the parity gate (needs D6's
  ladder seam in `MasterYouTubeModule`) and the L round-trip (needs D5b's L) come with those tasks;
  B0 (no change) is a control row; V `fail` / `unknown` act on the L rows only (the files the
  copied parser cannot read, in L14 the files that need the moved page signals; a moved HLS
  address too); each case lists the rungs with a checkable complete path (MCF); a renamed DRM
  marker with the formats in place is not modelled (L12 moves them). T1b added on 2026-10-15
  (bot-check rule) and made per video the same day (three lookups in a row): 97 cases per base,
  485 cases (515 lookups) per column; R6 completes each lookup (2 player requests, 1 page each).

### D2 — Client table v2 and answer judge

- `MasterClientTable` (version 2, data only): visionOS (R0, ≤ 2 alternates), Android, embedded,
  MWEB; each entry with identity values, source and date, expected class, census status (live /
  measured, not active, with date) and allowed variants (A, S, H). `describe()` for the census and
  canary. The table's test checks guardrail 5.
- `AnswerJudge`: v1's class table from the copied parser's summary, the HTTP result and a shape
  scan; plus `hls` (an HLS address on a `googlevideo.com` host) and `VERSION_REJECTED` (HTTP 400 or
  the client's "update" answer).
- v3: the judge's `SHAPE_CHANGED` kinds (moved, partial); `hls` from the field or L's locate;
  `ShapeFingerprint` (key paths with value types to depth 6, arrays collapsed; key names only,
  never values). The table also holds each identity's version pattern and its D0 version-source
  status (usable / measured, not active, with the date).
- **Done:** a unit test for every class from main's fixtures (`player_visionos.json`,
  `player_bot_check.json`, `player_age_gate.json`, `player_sabr_only.json`, `player_cipher.json`,
  `player_malformed.json`, `player_private.json`, `player_geo.json`, `player_drm.json`,
  `player_embed_refused.json`); never `DIRECT` for a protected, SABR-only or cipher answer;
  `SHAPE_CHANGED` for L1–L7 and L10, never for a terminal answer (L11).

### D3 — Health v2, order, budget and stop rules

- `ClientHealth`: memory + `files/master/client-health.json` (guardrail 8); a fake clock in tests.
  - Per client: last class, last pass, cooldown end, working variant (version index, S, H).
  - Cooldowns (v1): `REFUSED` 1 h; `SABR_ONLY`, `TOKEN_NEEDED`, `VERSION_REJECTED`,
    `SHAPE_CHANGED` 24 h; `BOT_CHECK` 30 min for the whole ladder. A client in cooldown is asked
    only when every other client failed in this lookup, then only the one whose cooldown ends first.
  - A learned variant is tried first; when it fails, the base is tried once, then the next client.
- v3: per client also the learned version (with its source and time) and the L shape map (keyed
  by fingerprint; paths only); the version-source clock (≤ 1 fetch a day per identity); a learned
  version or shape map that fails once is dropped (the base once, then the next rung). A class a
  rung repaired (A, S, Z, L, H) counts as a pass with its learned variant; a cooldown starts only
  when the client still fails after its rungs.
- Order, budget (guardrail 7) and stop rules (guardrail 4) in `ClientLadder`.
- **Done:** a unit test per rule; no request after a stop; H1 and H2 show the saving.

### D4 — Row checks: V, init check, H check and Z

- V: reuse `StreamCheck` with the client ID in its budget and cache key; add only the cases the
  probe result does not carry yet (wrong content type, a redirect to another host).
- `HlsCheck` (new, guardrail 6): master playlist → the best variant to offer → its playlist →
  first segment; verdicts `pass` / `fail` / `unknown`; the variants, highest resolution and audio
  form go into the answer, never an address into a result or a log.
- Z: the one-byte probe's total size fills a missing `contentLength`, rows to offer only, ≤ 8.
- Rule: rows of a client without a pass in the last hour need a `pass`; 403 with an open control →
  `TOKEN_NEEDED` (guardrail 9 applies); `unknown` → that client's ladder rows are dropped for this
  lookup.
- **Init check** (`InitCheck`, guardrail 6) on V's bytes for L rows: type, container start, no
  encryption marker (MP4 `encv`, `enca`, `sinf`, `tenc`, `pssh`; WebM content encryption), size.
- H check (v3): `#EXT-X-KEY` or `#EXT-X-SESSION-KEY` with a method other than `NONE` → `fail`;
  no known video length → no H rows.
- **Done:** fake-HTTP unit tests: 2xx, 206, 403 with and without control, timeout, wrong content
  type, wrong start bytes, a redirect to another host, an oversized playlist, a wrong duration;
  V5 and F2–F3 behave as expected; every init-check marker, wrong start bytes, a wrong type, a size
  mismatch, an encryption key and a missing length fail as expected.

### D5a — visionOS rungs: A (with self-healed versions), S, Z

- **A:** on `VERSION_REJECTED`: learned → signed (D8, if approved) → self-healed
  (`VersionSource`, only a D0-usable source; guardrail 5) → bundled alternates; never a version
  that neither a reference nor a usable source gives.
- **S:** watch page signals once per lookup (visitor data, signature timestamp, player ID); ask
  again with them; challenges from `signatureCipher` (s/sp) and `n`; `PlayerScriptRunner.resolve`
  with the own solver (`solver.OwnPlayerScriptRunner`, Phase 1.2 ladder and `SignatureGate`);
  apply; SelfCheck; V. Reuse the copied extractor's challenge and apply code where reachable;
  otherwise a Master-only equivalent tested on `player_cipher.json` and
  `player_cipher_ladder.json` ("Plan adapted"). From D5b on, a signal the copied `pageSignals`
  misses comes from L's page locate.
- **Z:** as D4, inside the rung.
- **Done:** V1–V3, Y1–Y6, H2 and F1–F2 complete or drop as expected in the simulator with a fake
  runner; a self-healed version is stored only after OK + V.

### D5b — Own parser L

- `ShapeLocator` and `ShapeNormalizer` (§3 L, steps 1–8); `SHAPE_CHANGED` → L; L's page locate
  for S.
- Tests: L1–L16 × the 5 base fixtures × V `pass` / `fail` / `unknown`; the round-trip (D1); a
  time bound (JVM ≤ 20 ms for a 300 KB answer; the phone's time is a D7 metric).
- Census tool update, on each live answer, in memory only (counts in the report): (1) the L
  round-trip — canonical JSON gives the same rows as the direct parse; (2) mutations L1, L2, L3,
  L5 and L7 applied to the live answer — L finds the same itags again, and their rows pass V.
- Debug-only switch for D7: `-Pyft.ladderMutate=L1|L2|L3|L5|L7` (mutates visionOS's live answer
  in memory before the judge; release `false`).
- **Done:** wrong = 0 and MCF = 0 in L1–L16; complete in L1–L7, L10 (found), L14 and L15 with V
  `pass`; no L row with V `fail` or `unknown`; the census steps unit-tested on the fixtures;
  drift check 0; flag off = R6.

### D5c — H leg (independent HLS)

- `HlsLeg` (§3 H, steps 1–4) with D4's `HlsCheck` and the app's existing HLS path; H moves out
  of v2's D5.
- Tests: V4–V6, V10, V11, L1–L13 with the HLS address intact (L13: moved), and F3's faults plus
  an encryption key and a missing length — complete or clean as expected; wrong = 0.
- **Done:** the simulator as above; one owner-phone download per audio form D0 saw (muxed,
  separate): a playable MP4 with both tracks, duration ±2 s. If D0 found that HLS needs a token,
  H is built but stays "measured, not active", and W keeps measuring it.

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
  switches for the owner's check: `-Pyft.ladderSkip=visionos` and `=visionos-adaptive`, and D5b's
  `-Pyft.ladderMutate`.
- **Done:** flag-off parity unchanged; parity gate green; in the simulator the ladder is never
  worse than the chain, wrong rows = 0 and MCF = 0; the memo saves the chain's repeated visionOS
  request and page fetch; CI green.

### D7 — Owner live check, weekly check, runbook

- The canary is the census tool (both modes): per client its class, whether the ladder completes
  and which rung answered. Owner-run on the phone; never CI.
- Owner live check (all flags on, mobile data), the same 10 videos in four modes: normal;
  `visionos-adaptive` skipped (H expected); `visionos` skipped (O expected); `ladderMutate` L1,
  L3 and L5 (L expected: the normal mode's itags found again, every L row V-passed).
- The census tool's L round-trip and mutation steps on the live answers of one full run.
- Two weekly checks, timed (≤ 60 s and ≤ 2 MB each); from then on the owner runs one a week.
- Runbook (§7) updated with what the census showed.
- **Done:** the owner's report; §5 targets met or new targets proposed with numbers.

### D8 — Signed identity data over R9 (owner decision) — Dropped (owner, 2026-10-15)

- **Dropped** (§9 item 4): the reference tables are a usable version source (§5; lookups may read
  them under §9 item 3's conditions). Known limit: when both reference tables lag behind, or the
  layout changes, a new APK is needed; W shows it. Decided again only if that happens. The text
  below stays as the design, should it come back.

- A `clientIdentities` section for R9's envelope (ES256, newer-only, expiry, GitHub host lock,
  asked at most once a day): only the four known client names; a strict version pattern per
  client; user agent and device fields from fixed lists; ≤ 2 alternates; source and date
  required. No hosts, no other headers, no code. The bundled table stays the fallback; no key =
  bundled table and no request.
- Tests like `RemoteRecipesTest`: a signed section changes visionOS's version; unsigned,
  tampered, older, expired, unknown-client, bad-pattern and oversized sections are ignored.
- Runbook: `VERSION_REJECTED` → the owner signs a table with the reference's new value; phones
  pick it up within a day, no APK. **If the owner says no** and no source is usable: a retired
  version needs an APK (v1), recorded as a known limit.
- v3: decided after D0. If a usable source self-heals visionOS's version, D8 may be dropped (the
  owner decides). The owner has no computer, and R9's `scripts/master-recipe-sign.sh` is a shell
  tool for one: an approved D8 therefore includes a phone path — a `workflow_dispatch` signing
  workflow with the private key as a repository secret (the owner decides the key's custody),
  started from the phone.

## 5. Pass criteria (Phase 1.3 done)

### 5.1 Master-caused failure (MCF)

A lookup is a **Master-caused failure** when it ends without the complete correct rows (P22 rule)
although a path existed that uses only the allowed identities and versions (learned, signed,
self-healed, bundled), needs no token, cookie, SABR, TV, MSE recording or bypass, and whose rows
pass V or the H check — or when it offers any wrong row.

**Not Master-caused:** a terminal verdict (age, private, removed, geo, DRM, bot check) and a
**server closure** — every allowed path refused (a token rule, SABR only, the client removed,
every allowed version rejected). Master then stops cleanly with R6's verdict, and W shows it
within a week.

Targets: MCF = 0 and wrong = 0 in every simulator class, in both ladder columns; MCF = 0 in the
owner's D7 check and in every weekly check. A live MCF is a top-priority bug: it is fixed and
becomes a simulator class (as Phase 1.2 added M9b and M9c). Honest limit: 0 is proved for the
modelled classes; an unmodelled change ends in a clean stop, W shows it, and it becomes a class.

### 5.2 Criteria

- **Parity:** the D1 parity gate and the L round-trip green; flag on and visionOS healthy: one
  request, the R6 rows; live: the same rows as R6 on 10 of 10 videos (extras only where R6 gave
  none).
- **Simulator:** wrong = 0 and MCF = 0 in every class and fault, in both ladder columns.
  - T1–T3 and T1b (owner, 2026-10-15): after such an answer the ladder sends no request that R6
    would not send, and its result equals R6's, in every lookup (T1b: three lookups in a row,
    each completes as R6 does; the ladder's 30 min cooldown stops only its own rungs).
  - The ladder is never worse than R6 in any class.
  - Live-backed column: complete in V1–V3 (V1 through a live alternate, a usable source or D8);
    in V4–V5 and V10 when D0 found H live; in V6–V9 only through census-live O rungs, else a
    clean failure with R6's verdict; V11 = R0. H1 saves at least one request; H2 (owner,
    2026-10-15): the second lookup makes ≤ 1 player request and completes (the first needs 2).
  - L: complete in L1–L7, L10 (found), L13 (with H live), L14 and L15 with V `pass`; L8 only
    this video's rows; L9 and L12 no rows; L11 = R6; no L row with V `fail` or `unknown`; L16
    within the time bound.
  - Y: Y1 complete through the self-healed version when a usable source exists (else through an
    alternate, or a clean stop); Y2–Y6 as listed; ≤ 1 source fetch a day.
  - In every class where R6 is below 100 % and a checkable path exists, the ladder completes more
    cases than R6 (owner, 2026-10-15); where none exists (L9, L11, L12, T1–T3, F1–F3: the right
    answer is no row) the same verdict as R6 and wrong 0.
- **Faults:** F1–F4, the init-check and the H-check faults: 0 wrong, 0 missed.
- **Budget:** guardrail 7; in V1–V5 and L1–L15 the whole lookup makes no more player requests
  than R6; in V2 counted against R6 without the own solver (3), so the ladder makes ≤ 2 (owner,
  2026-10-15; with the own solver R6 makes 1 and offers nothing).
- **Live (owner, D7, mobile data):** normal mode = the R6 rows plus at most extra formats;
  `visionos-adaptive` skipped → ≥ 8 of 10 complete when D0 found H live; `visionos` skipped →
  targets written after D0 with numbers; `ladderMutate` → every video complete in normal mode is
  complete again with the same itags, every L row V-passed, wrong 0; no bot check caused by the
  ladder in the session.
- **W:** the weekly check ≤ 60 s and ≤ 2 MB on the phone, twice; the diff tests green; no
  background API in the census code.
- **Versions:** the version-source table filled; for visionOS either a usable source or the
  written reason.
- **Flag off = R6 / main;** CI, `MasterYouTubeParityTest`, `MasterMainMoreSheetTest` green;
  drift 0.

Census (filled by D0; valid 1–3 = the three valid Full runs, under the bot-check rule; a cell = videos with V- or H-passed rows / videos asked; valid 1: 2026-10-14 23:06 UTC, mobile data, 10 videos, 54 requests, 2659 KB, 29 s; valid 2: 2026-10-15 00:37 UTC, the same, 54 requests, 2661 KB, 26 s; valid 3: 2026-10-15 02:05 UTC, the same, 54 requests, 2643 KB, 30 s; H and the O rungs are asked only on videos without a bot check, so the gate counts asked videos, §9 item 9):

| Rung | valid 1 | valid 2 | valid 3 | live? |
|---|---|---|---|---|
| R0 visionOS (adaptive rows) | 1 / 10: video 2 `DIRECT` (≤ 2160p, V pass); `BOT_CHECK` 8; video 6 (made-for-kids) `VIDEO_VERDICT` | 1 / 10: as valid 1 | 1 / 10: as valid 1 | R6's own first request, not gated: 3 / 30 passed, bot check on 24, `VIDEO_VERDICT` 3 (video 6) |
| R0+vd: visionOS again with the page's visitor data, only after a bot check (R6's chain request, not a ladder rung; does not make R0 live) | 8 / 8 `DIRECT`, V pass | 8 / 8 `DIRECT`, V pass | 8 / 8 `DIRECT`, V pass | R6's chain request, not gated: 24 / 24 `DIRECT`, V pass |
| A alternates (bundled, ≤ 2) | none bundled (0 asked) | none bundled (0 asked) | none bundled (0 asked) | n/a (none bundled) |
| S (visionOS with page signals) | 0 asked (no cipher in a visionOS answer) | 0 asked | 0 asked | **provisional** (0 asked: no cipher in any visionOS answer) |
| H (token step: master / variant / segment) | 1 / 1: 2xx / 2xx / 2xx, no key, 17 variants ≤ 2160p, audio separate, duration matches | 1 / 1: as valid 1 | 1 / 1: as valid 1 | **provisional** (3 / 3 passed, asked on 3 < 4 videos) |
| Android | 2 / 2 `SABR_ONLY`: the 360p progressive row only (V pass) | 2 / 2: as valid 1 | 2 / 2: as valid 1 | **live** (6 / 6, 3 of 3 runs, wrong 0): the 360p progressive row only (`SABR_ONLY`) |
| embedded | 0 / 2 `VIDEO_VERDICT` (error 152-18) | 0 / 2: as valid 1 | 0 / 2: as valid 1 | not live (0 / 6): measured, not active (2026-10-15) |
| MWEB | 2 / 2 `NEEDS_SCRIPT` → S ok-sig+n, V pass | 2 / 2: video 2 as valid 1; video 6 adaptive formats without address or cipher (25), only the 360p progressive row (S, V pass) | 2 / 2: video 2 adaptive formats without address or cipher (29), only the 360p progressive row (S, V pass); video 6 ≤ 1080p (S, V pass) | **live** (6 / 6, 3 of 3 runs, wrong 0); on 2 of 6 answers the adaptive formats had no address (360p only) |

**Gate** (§9 item 9, valid runs 1–3; asked / passed; wrong 0: every answer bound to its video, no V
fail): **live** — Android 6 / 6 (3 of 3 runs; the 360p progressive row only) and MWEB 6 / 6 (3 of 3
runs; S path; on 2 of 6 answers the adaptive formats came without address, so 360p only);
**provisional** — H 3 / 3 (asked on 3 videos) and S (0 asked); **not live** — embedded 0 / 6 (error
152-18); A: none bundled. R0 is R6's own first request (3 / 30 passed, bot check on 24); `R0+vd`
24 / 24. §9 decision 5 does not apply (O rungs are live).

Not valid (not counted for the gate):

| Run | When, network | Mode, videos | Requests | Stopped | Why not valid |
|---|---|---|---|---|---|
| run 1 | 2026-10-14 17:19 UTC, mobile data (metered) | FULL, 9 (slot 7 empty) | 1 (1920 bytes, 731 ms) | bot-check | video 1, visionOS R0 1.02: HTTP 200 `BOT_CHECK` (`LOGIN_REQUIRED` "Sign in to confirm you're not a bot", 0 formats); the old rule stopped the whole run at R6's first request, where R6 goes on (watch page, visionOS with the page's visitor data). The release app on the same mobile data 12 min later: a music video lookup succeeded (M4A 2.9 MB, 720p60, 480p, More formats 6). |
| run 2 | 2026-10-14 20:01 UTC, mobile data (metered) | FULL, 10 | 2 (3501 bytes, 1.7 s) | rate-limited | not counted — video 1, visionOS R0: `BOT_CHECK` (as run 1), answered as R6: the watch page → HTTP 429, so the run stopped (no retry). The release app (main, R6) showed a rate-limit error on the same mobile data just before (owner): an IP limit, not a census fault. |
| run 3 | 2026-10-14 20:06 UTC, mobile data (metered), after an airplane-mode toggle | FULL, 5 (slots 1–4, 7) | 20 (1099 KB, 11.9 s) | bot-check | not counted — a new IP (the network was changed before it; owner). Evidence only: video 1 `BOT_CHECK` → watch page → `R0+vd` DIRECT, V pass (the R6 rule works); video 2 visionOS DIRECT up to 2160p, V pass, H 2xx / 2xx / 2xx with no key (17 variants, up to 2160p, audio separate, duration matches), Android `SABR_ONLY` (360p progressive only, V pass), embedded `VIDEO_VERDICT` (error 152-18), MWEB `NEEDS_SCRIPT` (S ok-sig+n, V pass); video 3 `BOT_CHECK` → the old rule stopped the run (now per video). |
| run 4 | 2026-10-14 20:12 UTC, mobile data (metered), 6 min after run 3 | FULL, 5 | as run 3 | bot-check | not counted — the owner cleared the app's data before it, which reset the 30 min cooldown (not a bug; §7). The same as run 3, stopped at video 3; App Store source: `NETWORK`. |

Version sources (filled by D0):

| Identity | Source | run 1 | run 2 | run 3 | usable? (or why not) |
|---|---|---|---|---|---|
| visionOS | YouTube answers and pages | reachable, no value | = run 1 | = run 1 | no (no value in any run) |
| visionOS | App Store lookup | 21.41.5, no visionOS device (no visionOS value) | = run 1 | = run 1 | no (no visionOS value: the listing names no visionOS device) |
| visionOS | reference table | yt-dlp 1.02, YouTube.js 1.02 (= bundled) | = run 1 | = run 1 | **usable** (yt-dlp and YouTube.js = bundled in 3 of 3); GitHub raw: approved for lookups (owner, 2026-10-15; §9 item 3) |
| Android | Play Store page | over 64 KiB (not read past the cap) | = run 1 | = run 1 | no (over 64 KiB in every run) |
| Android | reference table | yt-dlp 21.26.364 (= bundled); YouTube.js 21.03.36 (older) | = run 1 | = run 1 | yt-dlp **usable** (= bundled in 3 of 3; GitHub raw, as above); YouTube.js no (older value) |
| embedded | embed page config | 2.20261014.01.00 from the watch page (client MWEB); newer, not tried (video 1 met a bot check) | = run 1 | = run 1 | no (the newer value was never tried: video 1 met a bot check in every run); embedded not live |
| MWEB | mobile page config | 2.20261014.01.00, newer, not tried (video 1 met a bot check) | = run 1 | = run 1 | no (the newer value was never tried: video 1 met a bot check in every run); MWEB keeps its bundled version, which works |

Baseline (filled by D1; the ladder columns by D6). D1 (`MasterClientLadderSimTest`, pinned in
`extractor-master/src/test/resources/ladder/r6-baseline.txt`): each cell counts lookups over the 5 bases
(L: × V pass / fail / unknown on the L rows; variants as listed in §4 D1); "without sizes" = P22's rule met
but a row has no size (not MCF); MCF counted against every simulated path (the live-backed paths come with
the D0 census):

| Class | R6, own solver off | R6, own solver on | ladder, live-backed | ladder, all simulated |
|---|---|---|---|---|
| B0 (no change, control) | 5/5 complete; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V1 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V2 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 0/5 complete, 5 clean stop; wrong 0, MCF 5 | — (D6) | — (D6) |
| V3 | 0/5 complete, 5 without sizes; wrong 0, MCF 0 | 0/5 complete, 5 without sizes; wrong 0, MCF 0 | — (D6) | — (D6) |
| V4 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V5 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | — (D6) | — (D6) |
| V6 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | — (D6) | — (D6) |
| V7 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V8 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V9 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V10 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| V11 | 5/5 complete; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| L1 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | — (D6) | — (D6) |
| L2 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | — (D6) | — (D6) |
| L3 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| L4 | 0/30 complete, 30 clean stop; wrong 0, MCF 30 | 0/30 complete, 30 clean stop; wrong 0, MCF 30 | — (D6) | — (D6) |
| L5 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 0/15 complete, 15 partial; wrong 0, MCF 15 | — (D6) | — (D6) |
| L6 | 15/15 complete; wrong 0, MCF 0 | 15/15 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| L7 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | — (D6) | — (D6) |
| L8 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | — (D6) | — (D6) |
| L9 | 0/15 complete, 15 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| L10 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | — (D6) | — (D6) |
| L11 | 0/45 complete, 45 clean stop; wrong 0, MCF 0 | 0/45 complete, 45 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| L12 | 0/15 complete, 15 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| L13 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | — (D6) | — (D6) |
| L14 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 0/15 complete, 15 partial; wrong 0, MCF 15 | — (D6) | — (D6) |
| L15 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 0/15 complete, 15 partial; wrong 0, MCF 15 | — (D6) | — (D6) |
| L16 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | — (D6) | — (D6) |
| Y1 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| Y2 | 0/20 complete, 20 partial; wrong 0, MCF 20 | 20/20 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| Y3 | 0/10 complete, 10 partial; wrong 0, MCF 10 | 10/10 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| Y4 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| Y5 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| Y6 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| T1 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| T1b | 15/15 complete; wrong 0, MCF 0 | 15/15 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| T2 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| T3 | 0/20 complete, 20 clean stop; wrong 0, MCF 0 | 0/20 complete, 20 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| H1 | 0/10 complete, 10 partial; wrong 0, MCF 10 | 10/10 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| H2 | 0/10 complete, 10 partial; wrong 0, MCF 10 | 0/10 complete, 10 clean stop; wrong 0, MCF 10 | — (D6) | — (D6) |
| F1 | 0/10 complete, 10 partial; wrong 0, MCF 0 | 0/10 complete, 5 partial, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| F2 | 0/5 complete, 5 partial; wrong 0, MCF 0 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| F3 | 0/25 complete, 25 clean stop; wrong 0, MCF 0 | 0/25 complete, 25 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| F4 | 0/5 complete, 5 with a wrong row; wrong 28, MCF 5 | 0/5 complete, 5 with a wrong row; wrong 28, MCF 5 | — (D6) | — (D6) |
| all | wrong 72, MCF 275 (485 cases, 515 lookups) | wrong 72, MCF 160 (485 cases, 515 lookups) | — (D6) | — (D6) |

## 6. Risks

- **No live second leg.** D0 may find only R0 (+ H). Then Phase 1.3 saves the requirement and
  layout changes (version, script, sizes, layout and, with H, SABR or a token rule on the
  adaptive rows), not a closed visionOS. Reported plainly; SABR clients, tokens and MSE recording
  stay excluded, so the owner decides what follows (§9).
- **L false positives** (decoys: ads, end screens, another video's data): the binding
  (`videoId`, length ±1.5 s, the itag inside the address), ambiguity = no rows, V and the init
  check on every L row; L8, L9 and L12 in the simulator; live mutations in D7.
- **L hiding a terminal state:** playability must be read (the copied parser, or exactly one
  located `OK`), else no rows (L10, L11).
- **L cost:** up to 8 V (4 KiB each) per lookup while a layout change lasts; the shape map saves
  the scan, not the checks; it ends when main's parser follows the change (drift process).
- **Self-heal source trust:** a public source can change or be tampered with: the pattern,
  newer-only, the version field only, kept only after OK + V, ≤ 1 fetch a day; a reference's
  file is read as text, never run; a new host only with the owner's yes.
- **HLS** may need a token, top out lower than the adaptive formats or use other codecs: measured
  in D0; used only when the adaptive path cannot complete; labelled per row; never without the
  length binding.
- **Weekly cadence:** a change can stay unseen for up to 7 days; the lookup's own fallbacks cover
  the gap, and the owner may run the check any time.
- **Mobile-data-only measurements** show the owner's carrier network only; YouTube may answer
  other networks differently.
- **Learned variants go stale:** each has a pass time; a failure falls back to the base once.
- **Signed data (D8):** a lost key could ship bad identity values; the schema bounds them (4 known
  clients, patterns, fixed lists, no code, no hosts) and the bundled table stays; a key kept as a
  repository secret (the phone path) is as safe as the owner's GitHub account.
- **More exposure under YouTube's terms:** ≤ 4 identities already in main, no fingerprint tricks,
  no cookies, after a bot check nothing R6 would not send (own rungs stop, 30 min cooldown),
  health cooldowns, the budget.
- **n slowdowns are not visible to V** (Phase 1.2 T4): the census records the time to the first
  64 KiB as a metric only.
- **Duplicated logic** (row builder, S path): parity gate and fixture tests; the drift check keeps
  guarding the copies. L builds no rows of its own (canonical JSON into the copied parser).
- **Flaky clients** (N7 run 3): the census measures rates over runs; health uses cooldowns, not a
  single answer, to retire a client.

## 7. Runbook (when a client fails)

- The census uses the same IP as the owner's daily app: keep runs few and at least 1 h apart;
  HTTP 429 stops a run (no retry, no bypass).
- Do not clear app data to skip the cooldown; it protects the IP the daily app uses.

1. Weekly check (W), once a week: `NO CHANGE` → nothing. `CHANGED` or `BROKEN` → copy the result
   to the agent; each line names a step below.
2. One video fails: run the full census on it: which rung, which class.
3. `VERSION_REJECTED`: the learned, signed (D8) or self-healed version (a usable source) or an
   alternate carries it; else (D8) sign a table with the reference's new value (source and date);
   else a new APK.
4. `NEEDS_SCRIPT`: nothing to do while the own solver is on (S).
5. Missing sizes: nothing to do (Z).
6. `SHAPE_CHANGED` (W: **BREAKING** paths): L carries the lookup, every row V-passed; the agent
   adds the change as a simulator class; main's parser changes first, the copy follows through
   the drift process.
7. `SABR_ONLY` or `TOKEN_NEEDED` on visionOS's adaptive rows: H carries the lookup when H is
   live; otherwise only live O rungs.
8. Every allowed path refused (server closure, §5.1): nothing in Master can carry it; W keeps
   measuring; the owner decides.
9. A new no-token client in a reference: census first, then the table (≤ 4 identities), then an
   APK.

## 8. Out of scope

Excluded by the owner (v3): SABR clients (SABR/UMP), proof-of-origin tokens and attestation, MSE
recording, the TV client and any bypass (age, geo, bot check, DRM). Also out: cookies and
sign-in, proxies or VPNs, TLS fingerprinting, remote code, background or scheduled checks, main
merge and release, merging PR #2.

## 9. Owner decisions

1. **Adopt v3** (extends v2; v2's D5 becomes D5a–D5c). Needed before D0.
2. **G0:** done — the owner closed Phase 1.2 on 2026-10-14 (`a99e0fb`: N4b closed, private video
   skipped, n rule PENDING). Nothing left to decide.
3. **After D0, version sources:** which usable source may be used in lookups, above all one on a
   host Master does not use yet (Apple, Google Play, GitHub raw for a reference). D0 (2026-10-15):
   usable = the reference tables on GitHub raw (visionOS: yt-dlp, YouTube.js; Android: yt-dlp), each
   equal to the bundled value in 3 of 3 runs; the App Store, the Play Store page, the visionOS answer
   and the web page configs are not usable (§5). **SOURCE_HOSTS_APPROVED = yes — owner, 2026-10-15,
   with conditions:** `raw.githubusercontent.com` only; no redirect followed; ≤ 64 KiB; read as text
   only; in a lookup fetched only after `VERSION_REJECTED`, at most once a day per identity
   (guardrails 5 and 7).
4. **D8 signed identity data: dropped — owner, 2026-10-15** (after D0: a usable source exists for
   visionOS, the reference tables, item 3). Known limit: when both reference tables lag behind, or
   the layout changes, a new APK is needed; W shows it. Decided again only if that happens.
5. **After the D0 gate:** if neither H nor any O rung is live, Phase 1.3 finishes with A, S, Z and
   L; no SABR, token or MSE work follows from this plan.
6. **M5 (Phase 1.2 carry-over, `PROGRESS.md` "Phase 1.2 — done"):** not part of this plan; the
   owner decides whether it becomes its own solver task. Not needed for D0.
7. **Bot-check rule** (proposed after census run 1, 2026-10-14): **yes — owner, 2026-10-15.** After
   a bot check the census and the ladder never send a request R6 would not send. Census, for
   each video (owner, after runs 2–4: R6 answers each lookup on its own): the watch page once and
   visionOS once more with the page's visitor data; a bot check on a request that already carried
   visitor data, or HTTP 429, stops the run (30 min cooldown). Ladder: its own rungs stop and R6's
   chain runs as today, so the result equals R6's (D3/D6). Simulator class T1b (per video); §5.2
   T1–T3 reworded; never a changed IP or network to avoid a bot check (a bypass). Census and
   simulator: `5cb781c`; per video: `81388f8`.
8. **D1 targets** (proposed in the D1 report): **yes — owner, 2026-10-15**, written into §5.2:
   (a) V2's budget counted against R6 without the own solver (3) → the ladder ≤ 2; (b) "completes
   more than R6" only in classes with a checkable path, elsewhere the same verdict and wrong 0;
   (c) H2: the second lookup ≤ 1 player request and complete.
9. **Gate count under the per-video bot-check rule: (က) with a minimum sample — owner, 2026-10-15**
   (proposed after valid run 1). H and the O rungs are asked only on videos whose first visionOS
   request had no bot check (valid runs 1–2: 2 of 10 each), so only the videos where a rung was
   actually asked count: **live** = pass (V- or H-passed rows) on ≥ 60 % of its asked videos in ≥ 2
   of 3 valid runs, AND asked on ≥ 4 videos in total across the 3 runs, AND wrong = 0. Asked on
   fewer than 4 videos in total → **provisional**: wired only as a fallback after visionOS fails, V
   required on every row, and the weekly check keeps measuring it until it reaches the minimum. No
   code change; the same APK (`81388f8`). Version acceptance runs on video 1 only (no newer version
   is tried after its bot check), so the web page config values stay "not tried"; MWEB keeps its
   bundled version, which works. Note: the weekly check asks H, Android, embedded and MWEB on
   video 1 only, and video 1 met a bot check in every run so far, so W adds samples for a
   provisional rung only when video 1 escapes it (D7 revisits).
