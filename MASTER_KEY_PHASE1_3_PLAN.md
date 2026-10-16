# Master Key — Phase 1.3 Plan v3 (final, 2026-10-15): own client ladder at own-solver level — no Master-caused failure (YouTube may change visionOS, Master keeps working)

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

- **Final plan (2026-10-15):** D0 ဖုန်းစစ် ပြီးပြီ (valid run ၃၊ test ၃၊ weekly check ၃၊ wrong အားလုံး ၀)။
  client table v3: visionOS R0 live၊ **H live** (gate ကျော်: 89 / 89 / 67 %၊ video ၉ ခု)၊ Android နဲ့ MWEB
  provisional (video 2၊ 6 ပဲ၊ fallback)၊ embedded measured, not active၊ A၊ S provisional။ အစဉ်: table v3
  (code) → T1c/T1d → D3 → D4 → D5a → D5b → D5c → D6 → D7 (continuous)။ owner: OK တစ်ခါ၊ တစ်ပတ်တစ်ခါ
  weekly check၊ နောက်ဆုံး D7 ဖုန်းစစ်။ အသေးစိတ်: "Final plan" အပိုင်း။
- **D3 + D6 core (2026-10-15):** ladder v1 (health၊ wiring၊ flag `-Pyft.clientLadder`၊ memo၊ merge rule၊
  visionOS answer ပေါ်မှာ L၊ Z၊ V၊ H) ပြီးပြီ — ladder ကိုယ်ပိုင် InnerTube request မပို့သေး။ simulator:
  wrong 72 → **0** (column နှစ်ခုလုံး)၊ MCF 305 → 95 (own solver off)၊ 160 → **5** (own solver on၊ V6 ပဲ =
  O live only)။ ကျန်တာ: D5a (A၊ S)၊ O rung၊ D7။ flag off = R6 (မပြောင်း)။
- **D7 ✅ (2026-10-16):** ဖုန်းစစ် (APK ၆ ခု၊ တစ်ခုစီ ၁၁/၁၁)၊ weekly check ၂ ခါ (W 4၊ W 5 — `NO CHANGE`)၊
  run 2 ရဲ့ census FULL မှာ L steps 9/9 `same` (L5 n/a)၊ wrong ၀၊ runbook §7။ `CHANGED` တစ်ကြောင်း = visionOS
  answer မှာ `streamingData.probeUrl` ပြန်ပေါ် (test 2 မှာ ပျောက်ခဲ့၊ Master မဖတ်၊ ဘာမှ မလုပ်)။ မပြီးသေးတာ:
  A (D5a၊ self-healed version)၊ L steps 7–8 (D5b)၊ H ဖုန်း download (D5c)။ simulator MCF: own solver on ၅
  (V6)၊ off ၉၅ → owner ဆုံးဖြတ်: A ဆက်လုပ်မလား၊ target အသစ် (§4 D7 "Done") နဲ့ ပိတ်မလား။
- **D7 ပိတ်ပြီ (2026-10-16):** H status fix APK (`7277fd8`) ပေါ်က census FULL (05:26 UTC) မှာ video 10 ရဲ့ H
  က မျှော်လင့်ထားတဲ့အတိုင်း `too-large` (line တစ်ကြောင်း) — fix ကို ဖုန်းပေါ်မှာ အတည်ပြုပြီ၊ wrong ၀၊ L steps 9/9
  `same`။ Owner ဆုံးဖြတ်ချက် (ခ): guardrail 6 ရဲ့ HLS variant playlist cap 256 KiB → **1 MiB** (master 256 KiB၊
  segment 64 KiB မပြောင်း)။ video 10 (1203 s) ဆံ့ပြီ၊ ~၁ နာရီထက် ရှည်တဲ့ video ဆို `too-large` → H unknown
  (wrong မဖြစ်)။ 1 MiB APK (`c9c5959`) ပေါ်က census FULL (06:56 UTC): video 10 ရဲ့ H `2xx` ဖြစ်ပြီ (မျှော်ထားတဲ့
  `CHANGED` line တစ်ကြောင်း)၊ H 9/9၊ `BROKEN` မရှိ၊ wrong ၀။ ⏸ owner ဆုံးဖြတ်ရန်: A (D5a) ဆက်လုပ်မလား၊
  target အသစ် (§4 D7 "Done") နဲ့ ပိတ်မလား။
- **Phase 1.3 ပိတ်ပြီ (2026-10-16၊ owner ဆုံးဖြတ်ချက် (ခ)၊ §9 item 13):** target အသစ်နဲ့ ပိတ်တယ် — flag
  အားလုံး on (D7 APK တွေလို): MCF ≤ 5 (V6 ပဲ၊ O rung တစ်ခု gate ကျော်တဲ့အထိ)၊ wrong ၀ နေရာတိုင်း၊ own solver
  off: A ပြီးမှ simulator ရလဒ်နဲ့ target ရေးမယ်။ ပိတ်ပြီးနောက် **A (D5a) ကို နောက်ဆက်တွဲ (follow-up)** အဖြစ်
  မရပ်ဘဲ ဆက်လုပ်မယ် (owner)။ L steps 7–8 (D5b)၊ H ဖုန်း download (D5c) က open ကျန် (ပိတ်တာထဲ မပါ)။
- **A (D5a) ✅ နောက်ဆက်တွဲ (2026-10-16):** visionOS version ကို YouTube က ပယ်ရင် (`VERSION_REJECTED`) —
  learned version → GitHub raw (yt-dlp) ကနေ version အသစ် (တစ်ရက် တစ်ခါ၊ ≤ 64 KiB၊ redirect မလိုက်၊
  User-Agent ပဲ) → bundled alternates (မရှိသေး)။ chain ရဲ့ request အတိုင်း version ပဲ ပြောင်းပို့ (lookup
  တစ်ခု ≤ ၃)။ V pass မှ သုံးပြီး version မှတ်၊ မဟုတ်ရင် ပယ် (R6 အတိုင်း) + visionOS 24 h cooldown။ bot check →
  ရပ်။ simulator: own solver off MCF 95 → **65** (V1၊ Y1၊ Y5၊ Y6 complete)၊ on 5 (မပြောင်း)၊ wrong ၀။ own
  solver off target = **65** (§5.1)။ ကန့်သတ်ချက်: V budget က client တစ်ခု တစ်ရက် ၄ ခါ → bundled version
  ပယ်ခံနေသရွေ့ A က တစ်ရက် lookup ၄ ခုအထိ (ပြီးရင် R6 အတိုင်း)။ bot check ပြီး ၃၀ မိနစ်အတွင်း A မလုပ်
  ((က+)၊ §9 item 10): census မှာ visionOS ပထမ request က video ၃၀ ခုမှာ ၂၄ ခု bot check ခံရလို့ app ထဲမှာ
  A က lookup အများစုမှာ မလုပ်နိုင် — 65 က bot check မပါတဲ့ simulator ဂဏန်း (အများဆုံး)။ ဖုန်းစစ် အခု မလို။
- **A check (owner ရဲ့ order item 1၊ 2026-10-16၊ §9 item 14):** yt-dlp၊ YouTube.js၊ NewPipe git
  history အားလုံးမှာ visionOS version က 1.02 ထက် အဟောင်း မရှိ (yt-dlp 2026-07-09 ကစ 1.02 ပဲ၊
  YouTube.js 2026-08-13 ကစ 1.02 ပဲ၊ NewPipe 1.02 → 1.04)။ App Store ရဲ့ visionOS app က 1.05.34။ version
  အဟောင်း မေးရင် တီထွင်ရမှာမို့ guardrail 5 က မခွင့်ပြု။ owner ရွေးချယ်မှု **(2)** → census မှာ "A check"
  ခလုတ် ✅: video 2 မှာ 1.02 ကို R6 အတိုင်း မေး၊ DIRECT ဆိုမှ NewPipe ရဲ့ 1.04 ကို version ပဲ ပြောင်းပြီး
  တစ်ခါ မေး (request ≤ ၄)၊ bot check / 429 → ချက်ချင်း ရပ် (၃၀ မိနစ်)။ ဖုန်းမှာ 1.04 DIRECT + V pass ဆိုရင်
  visionOS ရဲ့ bundled alternate (NewPipe၊ 2026-08-06) အဖြစ် ထည့်၊ R0 က 1.02 ပဲ၊ parity မပြောင်း။
  ကန့်သတ်ချက်: YouTube က version ကို ဘယ်လို ပယ်လဲ မသိသေး (§4 D5a)။ App Store source ပြဿနာ → §10 Backlog။
- **D5c h-only trial ✅ (owner ရဲ့ order item 2၊ 2026-10-16၊ §4 D5c):** trial mode အသစ် `h-only` (debug ပဲ၊
  `[d7]` APK ၇ ခုမြောက်): list ထဲမှာ H ရဲ့ HLS row တစ်ခုတည်း (chain ရဲ့ row တွေ မပါ)၊ H row မရရင် row မရှိ +
  အကြောင်းရင်း note။ bug fix: H row မှာ video key မပါလို့ app က ဖယ်ထုတ်ခဲ့ (ဖုန်း list ထဲ H row မရောက်ဖူး) →
  ပြင်ပြီ။ simulator: lookup 990 — H row နဲ့ complete 440၊ H ရဲ့ အကြောင်းရင်းနဲ့ stop 550၊ wrong ၀။ D0 မှာ အသံ
  သီးခြား (audio rendition) ပဲ တွေ့လို့ ဖုန်းမှာ download တစ်ခါ (AVC quality — app က AAC အသံနဲ့ ပေါင်း၊ VP9 က
  "No sound")။ muxed က simulator မှာပဲ။
- **D5b steps 7–8 — code မရေးဘဲ ပိတ် (owner ရွေး (1)၊ 2026-10-16၊ §9 item 15):** နှစ်ခုလုံး ရလဒ် (row၊
  wrong၊ MCF) မပြောင်းလို့ (Plan adapted)။ step 7 (shape map memory) က scan ချိန် (8 ms) ပဲ သက်သာ၊ step 8 (page
  signal ရှာ) က R6 မပို့တဲ့ request ထွက် (guardrail 4)။ နောက်ဆက်တွဲ ၃ ခု → §10 Backlog "Phase 1.4": (i) trigger
  (W BREAKING၊ page signal ပျောက်) ပေါ်မှ step 7/8၊ (ii) shape map ကို မှတ်ရုံ (ဖတ်ဖို့ မသုံး)၊ (iii) page မှာ
  player ID မပါရင် နောက်ဆုံး အောင်မြင်ခဲ့တဲ့ lookup ရဲ့ player ID (V / init check ဆက်ဖွင့်၊ chain ရဲ့ request
  ပြောင်းလို့ owner ခွင့်ပြုမှ၊ bot check နောက်မှာ ဘယ်တော့မှ မသုံး)။
- **VP9 HLS အသံ (2026-10-16၊ §10 item 5):** H ရဲ့ HLS မှာ AVC quality တွေကို AAC အသံနဲ့ ပေါင်းပြီး MP4 ရ (P50)၊
  VP9 quality တွေက "No sound" (Android muxer: MP4 က VP9 မယူ၊ WebM က AAC မယူ) — ချို့ယွင်းချက် မဟုတ်၊ backlog။
  resolution တစ်ခုမှာ အသံပါ row ကို ပြလို့ 1080p အထိ အသံပါ။
- **Run 3 APK ၇ ခု (owner ရဲ့ order item 4၊ `[d7]`၊ §4 D7 "Run 3"):** A check ခလုတ် (APK တိုင်းရဲ့ census tool)
  နဲ့ h-only ပါ။ ဖုန်းစစ်: (1) `normal` → "YFT client census" → "A check (visionOS 1.04)" → report ရဲ့ ပထမ
  စာကြောင်း ပို့ပါ၊ (2) `h-only` → video 1 → list မှာ HLS quality တွေပဲ → "No sound" မပါတဲ့ row (AVC) တစ်ခု
  download → MP4 ဖွင့်၊ ပုံ + အသံ ပါ၊ ကြာချိန် ±2 s၊ (3) ကျန် ၅ ခု — optional (run 2 လို list complete)။
  1.04 DIRECT + V PASS ဆိုရင် visionOS ရဲ့ bundled alternate ထည့်မယ် (R0 = 1.02 မပြောင်း)၊ (ဂ) အဲဒီနောက် ဆုံးဖြတ်။
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
- ဆက်တည်: Wi-Fi မရှိ → live တိုင်းတာ အားလုံး mobile data။ D8 မလုပ်တော့ (owner
  2026-10-15၊ §9 ၄)။ Phase 1.2 ပိတ်ပြီး (G0 ✅ `a99e0fb`၊ owner 2026-10-14)။ main မထိ၊ PR #2 merge မလုပ်။
- M5 (Phase 1.2 က ကျန်တာ၊ es6 player ဖိုင် ၈ ခု၊ solver ဘက်) ဒီ plan ထဲ မပါ — owner ဆုံးဖြတ် (§9 ၆)။
- မထည့်: SABR client၊ PO token၊ MSE recording၊ TV client၊ bypass။
- သက်သေ: simulator class အသစ် L1–L16 (key ရွှေ့/နာမည်ပြောင်း၊ decoy၊ encrypted)၊ V10–V11 (HLS)၊
  Y1–Y6 (version) + v2 class တွေ × fixture ၅ ခု → wrong = ၀၊ MCF = ၀။ ဖုန်းပေါ်မှာ live answer ကို memory
  ထဲမှာပဲ ပုံစံပြောင်းပြီး (`ladderMutate`) L က row ပြန်ရှာနိုင်ကြောင်း စစ်မယ်။
- မကယ်နိုင်တာ (server ပိတ်): ခွင့်ပြုထားတဲ့ client အားလုံး token လို၊ SABR-only၊ client ဖယ်ရှား၊ bot check၊
  DRM။ အဲဒီအခါ verdict မှန်နဲ့ ရပ်ပြီး W က သတိပေးမယ်။
- Task: G0 Phase 1.2 ပိတ် (✅ `a99e0fb`) → D0 census + weekly check → D1 simulator → D2 table + judge → D3 health →
  D4 checks → D5a A/S/Z → D5b L → D5c H → D6 O + wiring → D7 ဖုန်းစစ် + runbook (D8 မလုပ်တော့)။
- Bot check (owner 2026-10-15): bot check ပြီးရင် R6 မပို့တဲ့ request ဘယ်တော့မှ မပို့။ R6 လို video (lookup)
  တစ်ခုချင်း: census က video တိုင်းမှာ watch page တစ်ခါ + visionOS ကို page ရဲ့ visitor data နဲ့ တစ်ခါ၊ ပြီးရင်
  နောက် video ဆက်။ visitor data ပါပြီးသား request ကို bot check ဒါမှမဟုတ် HTTP 429 → run ရပ် + ၃၀ မိနစ်။
  ladder (က+၊ owner 2026-10-15၊ §9 ၁၀): InnerTube (player/page) request အသစ် မပို့၊ R6 chain ယခင်အတိုင်း။
  R6 ရဲ့ R0+vd answer ပေါ်မှာ L၊ Z၊ V၊ init check၊ H check (lookup တစ်ခု ≤ ၁)၊ S local (R6 ယူပြီးသား
  player script ရှိမှ) ခွင့်ပြု။ ၃၀ မိနစ် cooldown က request ပို့တဲ့ rung (A၊ O၊ S re-ask၊ version source)
  ကိုပဲ ပိတ်။ census လည်း R0+vd DIRECT ဆို V၊ H စစ်။ visitor data သိမ်းပြီး ပထမ request ကစ ပို့တာ (ခ)
  မလုပ် (bot check ကို ရှောင်တာ၊ bypass နဲ့ နီး)။ IP/network ပြောင်းတာ မလုပ် (bypass)။
- Gate (owner 2026-10-15၊ §9 ၉): H/O rung live ဖြစ်ဖို့ distinct video ≥ ၄ လို။ H က test 1–3 မှာ gate ကျော်ပြီ (live၊ Final plan)၊
  Android နဲ့ MWEB (video 2၊ 6) provisional — fallback အဖြစ်ပဲ ချိတ်၊ row တိုင်း V။
- D2 ✅ (2026-10-15): client table v2 + answer judge။ V7 (owner): definite marker (age၊ sign-in၊ private၊
  removed၊ geo၊ DRM …) မပါတဲ့ error screen = client တစ်ခုရဲ့ ငြင်းချက် (`REFUSED`)၊ terminal မဟုတ် → O rung
  ဆက်မေးနိုင်၊ မပြည့်ရင် R6 ရဲ့ verdict။ simulator V7 live-backed: 10 / 10 MWEB နဲ့ ပြည့်၊ wrong ၀၊ MCF ၀။
  T3 removed (marker မပါတဲ့ "This video is unavailable") → `REFUSED`၊ O ဆက် — owner ဆုံးဖြတ်ချက်
  (2026-10-15၊ §5.2)၊ marker တွေ terminal ဆက်ဖြစ်၊ ဘယ်တော့မှ မလျှော့။
- Debug APK key (2026-10-15): CI က opt-in debug APK တိုင်းကို spike debug key တစ်ခုတည်းနဲ့ sign (repo ထဲ
  public၊ debug app သီးသန့်၊ release မဟုတ်) → နောက် APK တွေ update ရ။ ဒီ key ပါ APK ကို တစ်ခါ uninstall
  လုပ်ပြီး ထည့်၊ နောက်ပိုင်း update ပဲ (uninstall ဆို census history၊ health၊ slot ပျက်၊ `FIRST RUN`၊ §7)။
- Run mode (owner 2026-10-14): task တစ်ခု ပြီးတိုင်း agent က "✅ Dx ပြီးပြီ" စာတိုရေးပြီး ဆက်လုပ်မယ်။
  ဖုန်းစစ် (D0 census — အဲဒီအတောအတွင်း D1 လုပ်မယ်၊ D7)၊ owner ဆုံးဖြတ်ချက်၊ STOP ပေါ်မှပဲ ရပ်မယ်။
- ခက်ခဲမှု: High (D5b High; D3၊ D5a၊ D5c၊ D6 Medium–High)။

## Final plan (2026-10-15, after D0's census: tests 1–3 and W 3)

D0's phone part is complete: valid runs 1–3, tests 1–3 on the B + D2 APK (`595d259`) and weekly
checks W 1–W 3 (§4 D0), wrong 0 in every run. The client table this leaves, and the work left:

| Client / rung | Census (tests 1–3, W 1–W 3) | Table v3 |
|---|---|---|
| visionOS R0 | the first request met a bot check on 8 of 10 videos; R6's `R0+vd` answer `DIRECT`, V pass, on every such video (8 / 8 in each test; W 1–W 3) | live (as v2) |
| H (HLS leg) | 8 / 9, 8 / 9, 6 / 9 (89 %, 89 %, 67 %) on 9 distinct videos; test 3's two misses were network failures (`net`); video 10's `no-segment` was our status bug, not YouTube's side (a playlist over 256 KiB read as `2xx`; fixed 2026-10-16 and confirmed by the 05:26 UTC census FULL; since then the variant cap is 1 MiB, owner, guardrail 6; §7); W 2 and W 3 passed; the 06:56 UTC census FULL on the 1 MiB APK: 9 / 9 | **live** (§9 item 9's gate met) |
| Android O | the 360p progressive row only (adaptive `SABR_ONLY`), V pass; videos 2 and 6 only | provisional (a fallback, V on every row) |
| MWEB O | `NEEDS_SCRIPT` → S ok-sig+n, V pass (≤ 2160p, ≤ 1080p); its adaptive answer flips between ciphered and address-less; videos 2 and 6 only | provisional (a fallback, V on every row) |
| embedded O | `REFUSED` (error 152-18) on 2 / 2 | measured, not active |
| A | no alternates bundled; versions only from the GitHub raw reference tables (§9 item 3) | provisional |
| S (visionOS) | 0 asked (no cipher in a visionOS answer); S solved MWEB's cipher in every Full run | provisional |

Work left, in this order (run mode continuous, §4: a one-line note after each task; the stop rules
unchanged: wrong > 0, MCF > 0 in a modelled class, drift > 0, a flag-off parity failure, CI ❌ twice
in a row):

1. **D2 follow-up — client table v3** (a small code change): H provisional → live; nothing else in
   the table changes. **Done** (2026-10-15; the owner's OK).
2. **D1 follow-up — T1c, T1d** ((က+), §9 item 10): the R6 columns in the simulator. **Done**
   (2026-10-15).
3. **D3** health v2, order, budget and stop rules (learned version, shape map; (က+) after a bot
   check). **Core done** (2026-10-15, with item 6's core): `ClientHealth` (memory; its file with D7),
   the cooldowns, the bot-check window, order, the version-source clock and the shape maps, one
   unit test per rule; order and cooldowns steer the ladder's own requests, which come with D5a.
4. **D4** row checks: V (reuse), init check, H check, Z. **Built** (2026-10-14/15; the parallel
   agent's pieces, wired with item 6's core; §4 table).
5. **D5a** visionOS rungs A, S, Z → **D5b** own parser L → **D5c** H leg. **Progress**
   (2026-10-15): S under the ladder (Plan adapted, §4 D5a "As built"); Z, L and H on visionOS's
   answers came with item 6's core. **A done** (2026-10-16, the follow-up after the close, §9
   item 13; §4 D5a "As built (A)"; own solver off MCF 95 → 65). Left, open: L steps 7–8 (D5b:
   the shape-map memory, L's page locate for S); H's phone download (D5c).
6. **D6** O rungs (H live; Android and MWEB as fallbacks), memo, wiring, flag and merge rule.
   **Core done** (2026-10-15): wiring, flag, memo, merge rule; L, Z, V and H on visionOS's answers in
   the R6 chain's HTTP path (Plan adapted, §4 D6); simulator wrong 72 → 0 in both columns, MCF
   305 → 95 (own solver off) and 160 → 5 (on; V6 = O, live only), §5. **O rungs done**
   (2026-10-15, Plan adapted): the chain's other clients (Android, MWEB, embedded) judged for
   health, V on their direct rows; the simulator totals unchanged. A: item 5 (2026-10-16).
7. **D7** the owner's live check (with live L mutations), the weekly check and the runbook, with two
   census fixes: MWEB's address-less flip becomes an `INFO` line (not `CHANGED`), and the R6 note
   no longer says "nothing else for this video" when H ran on that answer. **Prep done**
   (2026-10-15, §4 D7 "As built (prep)"): the switches, the workflow inputs, the health file,
   both census fixes and the census tool's L steps; the six APKs from a push with `[d7]` (job
   `d7-matrix`). Left: the owner's phone check. **Done** (2026-10-16): the phone check, the
   census L steps 9/9 on run 2, W 4 and W 5, runbook §7; §5.2's MCF = 0 is not met in the
   simulator → targets proposed (§4 D7 "Done", ⏸ the owner). **Closed** (2026-10-16): a census
   FULL confirmed the H status fix; the HLS variant cap is 1 MiB (owner, guardrail 6), and the
   census FULL on that APK read H 9 / 9 (06:56 UTC). **Phase 1.3 closed** (the owner's decision
   (ခ), 2026-10-16, §9 item 13): on the proposed targets (§5.1); A follows as a follow-up.

The owner's part: one OK for this plan (items 1–2 and the (က+) plan change; §9 items 10–11); a
weekly check about once a week on mobile data; D7's phone check at the end. Known limits (§5):
Android and MWEB reach 4 distinct videos only when more videos escape the bot check (W asks them on
video 2 only); W runs no S, so MWEB's V there is `skip-needs-S`. PR #2 stays a draft; main is not
touched.

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
   2026-10-15, twice): age check, video verdict or DRM → stop, the R6 chain decides; bot check →
   the R6 chain runs as today, and after a bot check no InnerTube request (player or page) is sent
   that R6 would not send (the census, for each video as R6 answers each lookup: the watch page
   once and visionOS once more with the page's visitor data, as R6 asks it; a bot check on a
   request that already carried visitor data stops the run). **(က+)** (owner, 2026-10-15; §9
   item 10): on R6's `R0+vd` answer the ladder may still run (a) L (local) and Z, V and the init
   check (reads on the media host YouTube returned; guardrail 6's budget); (b) the H check (its
   playlists on `manifest.googlevideo.com`, the segment on the media host; ≤ 1 per lookup);
   (c) S locally — the own solver, SelfCheck and V — only when R6 already fetched the player
   script (no player request or script fetch of its own); (d) a 30 min cooldown that blocks only
   the rungs that send requests (A, O, S's re-ask, a version source), never L, Z, V, the init
   check, H or local S. The census likewise runs the media-host checks (V, H) on an `R0+vd`
   answer that is `DIRECT`. Never a changed IP or network to avoid a bot check, and never visitor
   data kept to send from a lookup's first request (option (ခ), rejected: it avoids the bot check
   instead of answering it); ladder rows merge only when the chain did not end in a terminal
   verdict.
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
   - H check (v2, extended): every address under `googlevideo.com`; the master playlist ≤ 256 KiB
     and one variant playlist ≤ 1 MiB (owner, 2026-10-16, §9 item 12; was 256 KiB: a variant lists
     every segment with its full address, so it grows with the video; census video 10, 1203 s,
     passed 256 KiB); the first segment of that variant, Range ≤ 64 KiB; content
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
      AGE_CHECK / VIDEO_VERDICT -> stop; the R6 chain decides
      BOT_CHECK          -> stop asking (30 min cooldown for A, O, S's re-ask, version sources);
                            the R6 chain runs as today (watch page, R0+vd); on R6's R0+vd answer
                            only steps without an InnerTube request run ((က+), guardrail 4):
                            L, Z, V, init check, H check, S locally with R6's player script
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
                  after a bot check the steps above run on the chain's R0+vd answer
                  (from the memo, this lookup only); merge rule (guardrail 9)
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
| D0 | Census tool: full census + weekly check (W) with diff; live census on mobile data (gate); version sources; H token step | G0 | Medium | **Done** (2026-10-15): code `1135afe`; census runs 1–4 not valid, not counted (§5); bot-check rule (owner, 2026-10-15; `5cb781c`), per video (`81388f8`); valid runs 1–3 done (2026-10-14 23:06, 2026-10-15 00:37 and 02:05 UTC); gate (§5, §9 item 9; distinct videos, owner 2026-10-15 after W 2): provisional H (1 distinct video), Android and MWEB (2 each), S (0 asked); not live embedded; no rung live (§9 item 5: does not apply, decided 2026-10-15); usable sources: the reference tables (GitHub raw, approved for lookups with conditions, §9 item 3); D8 dropped; Weekly check 1 (2026-10-15 03:39 UTC, §5); then (owner): the diff's `INFO` lines and +/− paths cancel, W's H on video 2 after video 1's bot check (`3e8f378`); Weekly check 2 (04:34 UTC, §5); after a bot check (က+) (§9 item 10); census items B5–B9 (owner items 5–9, 2026-10-15; code `eac2df3`, tests `2b62605`; §4 D0 "B, as built"); tests 1–3 and W 3 on `595d259`: H meets the gate (Final plan) |
| D1 | Change simulator, parity gate, L round-trip, baseline | D0 (classes) | Medium–High | Done: simulator + R6 baseline (`f35b78f`; T1b `5cb781c`, per video `81388f8`); parity gate with D6, L round-trip with D5b; T1c, T1d ((က+), §4 D1): R6 columns done (2026-10-15) |
| D2 | Client table v2 + answer judge (shape kinds, HLS locate, fingerprint) | D1 | Medium | **Done** (2026-10-15): code `eac2df3`, tests and plan `2b62605` (CI ✅; §4 D2 "As built"); V7 (owner): an error screen without a definite marker = one client's refusal (`REFUSED`), not a verdict; V7 live-backed simulator 10 / 10 complete through MWEB, wrong 0, MCF 0 (§5) |
| D3 | Health v2, order, budget, stop rules (learned version, shape map; (က+) after a bot check) | D2 | Medium–High | **Core done** (2026-10-15, `be0c52e`; §4 D3 "As built"): `ClientHealth`, one test per rule (10); its file with D7 prep (`e56cc3f`); order and cooldowns steer only the ladder's own requests — A's since D5a's A (2026-10-16) |
| D4 | Row checks: V (reuse), init check, H check, Z | D2 | Medium | **Built** (2026-10-14/15; the parallel agent's pieces, `PROGRESS_PARALLEL.md`): `InitCheck` (`3342508`), Z (`8795699`); the H check = D0's `HlsCheck` inside `HlsLeg` (`240cd4d`); V = `StreamCheck` reused unchanged; all wired with D6 core (`be0c52e`). Not added: `StreamCheck`'s wrong-type and other-host cases (L rows have them through the init check and `OkHttpMediaStartReader`) |
| D5a | visionOS rungs A (with self-healed versions), S, Z | D3, D4 | Medium–High | **Done** (2026-10-16): S (Plan adapted: the own solver in the R6 chain under the ladder, `6402fea`; §4 D5a "As built"), Z (`8795699`), A (the follow-up after the close, owner 2026-10-16, §9 item 13; Plan adapted, §4 D5a "As built (A)": `VersionSource`, self-healed versions from yt-dlp's table; simulator own solver off MCF 95 → 65, on 5, wrong 0) |
| D5b | Own parser L (locate, bind, canonical JSON, copied parser) | D3, D4 | High | **Steps 1–6 done** (2026-10-14/15): `ShapeLocator` + `ShapeNormalizer` (`fdc7dea`, the parallel agent), wired (`be0c52e`), census L steps (`a4ffd02`), DRM marker narrowed (`83ce453`); simulator L1–L16 wrong 0, MCF 0; live L steps 9/9 `same` (D7). **Steps 7–8 closed without code** (2026-10-16, the owner's choice (1), §9 item 15; Plan adapted): neither changes an outcome; step 7's use needs the parallel agent's `ShapeNormalizer`, step 8 changes the chain's requests (guardrail 4); the follow-ups are in §10 (Phase 1.4: steps 7–8 only on a trigger, the shape map recorded only, the last good player ID) |
| D5c | H leg (independent HLS) | D4, D5b (locate) | Medium–High | **Built** (2026-10-14/15): `HlsLeg` (`240cd4d`, the parallel agent; H matrix 300 runs, wrong 0), wired (`be0c52e`); H live (Final plan, table v3 `4f884ec`; census H 8 of 9 in each Full run). **The phone download through H not done** (D7's `skip-visionos-adaptive` lists were complete through the chain, so H added no row). **h-only trial built** (2026-10-16, §4 D5c "As built (h-only trial)"): the seventh `[d7]` APK lists H's row alone; H's row now carries the video key (the app had dropped it); the phone download on run 3 (§4 D7 "Run 3"; VP9 HLS stays "No sound", §10 item 5) |
| D6 | O rungs (census-live only), memo, wiring, flag, merge rule | D5a–D5c | Medium–High | **Core and O rungs done** (2026-10-15, Plan adapted; §4 D6 "As built"): wiring, flag, memo (`8795699`), merge rule, L / Z / V / H on visionOS's answers in the R6 chain's HTTP path (`be0c52e`); O rungs = the chain's Android, MWEB and embedded answers judged, V on their direct rows (`6402fea`); simulator wrong 72 → 0, MCF 305 → 95 (own solver off) and 160 → 5 (on; V6 = O, live only), §5. A with D5a (2026-10-16: own solver off 95 → 65) |
| D7 | Owner live check (with live L mutations), weekly check, runbook | D6 | Low–Medium | **Done** (2026-10-16): phone check ✅ (six APKs, 11/11 each, wrong 0); L DRM marker narrowed (`83ce453`) ✅; census FULL on run 2 (`1a62b18`): L steps 9/9 `same` (L5 n/a), wrong 0 ✅; W 4 and W 5 ✅ (§5); runbook §7 ✅; §5.2's MCF = 0 not met in the simulator → targets proposed (§4 D7 "Done") and accepted (owner (ခ), 2026-10-16, §9 item 13: **Phase 1.3 closed**); **closed** (2026-10-16): the census FULL on the H fix APK (`7277fd8`) read `too-large` on video 10 as expected; variant cap 1 MiB (owner, guardrail 6), confirmed by the census FULL on its APK (`c9c5959`, 06:56 UTC: H 9 / 9); **run 3** (2026-10-16, §4 D7 "Run 3"): the A check and the H download, ⏸ the owner's phone |
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
  and the run goes on with the next video; no newer version is tried on that video. **(က+)**
  (owner, 2026-10-15; §9 item 10): when that `R0+vd` answer is `DIRECT`, the media-host checks
  run on it too — V on one row and the H check with the token step on its HLS address (≤ 3
  requests; in the census from the next APK) — and still no other client and no other InnerTube
  request. A bot check on
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
- **Gate** (owner, 2026-10-15, §9 item 9: the per-video bot-check rule asks the O rungs only on
  videos without a bot check — and H too, until (က+) adds it on `R0+vd` `DIRECT` answers — so only
  the videos where a rung was actually asked count): a rung is **live** when its rows passed V (or
  H) on ≥ 60 % of the videos it was asked on in ≥ 2 of 3 valid runs, it was asked on ≥ 4
  **distinct** videos (by list number) over the valid runs and the weekly checks (owner,
  2026-10-15, after W 2: H's four samples were all video 2), and wrong = 0. Asked on fewer than 4
  distinct videos → **provisional**: wired only as a fallback after visionOS fails, V on
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
    asked. **H on a video without a bot check** (owner, 2026-10-15, after weekly check 1): when
    video 1 met a bot check (so no H and no other client on it), visionOS is asked on video 2 — no
    bot check there in valid runs 1–3 — as R6 asks it (class, counts, fingerprint, V on one row),
    and the H check runs on its HLS address; a bot check on video 2 is answered as R6 and then no
    H runs (a note says which). At most 20 requests: videos 1 and 5 answered as R6 (4–5 each),
    video 2 (5–6), the sources (4).
  - Its summary is stored in `files/master/census-last.json` (the last 8 runs: classes, counts,
    fingerprints as key-path lists and hashes, public version strings, verdicts, times, bytes;
    no address, value, visitor data or video ID).
  - First line: `NO CHANGE`, `CHANGED (n)` or `BROKEN (n)`, and the days since the last run.
    Then one line per change: class (per client); version (accepted → rejected; a source's value
    changed); shape (paths added or removed — a removed path the copied parser reads is marked
    **BREAKING**); H (verdict, token step, highest resolution); the rung that answered. Each line
    names its runbook step (§7). Owner, 2026-10-15 (after weekly check 1): shape paths are
    gathered per client and step over its videos, and a path one video gained while another lost
    it is not a change; a bot check answered as R6 whose `R0+vd` answer is `DIRECT` with V passed
    is an `INFO` line (no runbook step, not counted: `NO CHANGE` when nothing else changed); any
    other answered bot check stays a `CHANGED` line (step 8).
  - No background work (guardrail 11); the screen shows when the last check ran.
- **Done:** both modes in the opt-in debug APK; unit tests for the diff (each change kind;
  identical runs = `NO CHANGE`), the report sanitizer and a static check that the census code
  uses no background API; the owner's 3 full runs and one weekly check; the census table, the
  version-source table and the gate decision in §5 and `PROGRESS.md`; the owner told which rungs
  and sources are live. If neither H nor any O rung is live, stop and ask the owner (§9): Phase
  1.3 then keeps A, S, Z and L. (2026-10-15, distinct count: none live — H, Android and MWEB
  provisional; §9 item 5 does not apply — decided 2026-10-15, the owner delegated.)
- **B, as built** (owner items 5–9, 2026-10-15; code `eac2df3`, tests `2b62605`):
  5. (က+) in the census: on R6's `R0+vd` answer that is `DIRECT`, V on one row and the H check
     (token step) on its HLS address — ≤ 1 H a video, ≤ 1 in a weekly run; no other client and
     no other InnerTube request on that video.
  6. W, when video 1 met a bot check: on video 2 visionOS as R6 asks it + V (+ H only when no H
     ran yet), then its watch page, Android + V and MWEB (S, V), under a soft budget: what does
     not fit the 20 requests is not asked, a note says which, and the run goes on.
  7. Version acceptance: a new valid value newer than the bundled one is tried once (`SH`: one
     request and V) on the first video in list order without a bot check and with a watch page (a
     note names it when that is not video 1; none → a note, no newer version tried); for MWEB the
     formats without an address are compared between the bundled and the newer version (a note).
  8. The header counts and lists only the videos asked ("3 videos (1, 2, 5)"; three or more in a
     row as a range, "1–3").
  9. Budgets: Full ≤ 6 MB; W ≤ 20 requests, ≤ 2 MB, ≤ 60 s.
  The census classes every answer with D2's `AnswerJudge` (its own classifier removed): a V7
  refusal is `REFUSED` and the run goes on; the shape kind is stored (JSON key `kind`: `moved` or
  `partial`, kept through the state's round trip); the report's second line is the client table
  (`MasterClientTable.describe()`). Tests: `ClientCensusTest` (+4: B8 ranges, V7 in the census on
  video 6 and the embedded 152-18, the table line, the shape kind round trip).

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
| T1 | bot check from every identity and the page | IP flagged | request-sending rungs stop + cooldown; the R6 chain runs = R6 (no `R0+vd` answer to work on) |
| T1b | bot check only for visionOS asked without visitor data (owner, 2026-10-15), per video: three lookups in a row each meet it | carrier IP (census runs 1–4, 2026-10-14) | request-sending rungs stop + cooldown; the R6 chain (watch page, visionOS with the page's visitor data) completes each lookup = R6 |
| T1c | bot check as T1b (owner, 2026-10-15, (က+)); R6's `R0+vd` answer has its formats under a moved key (`SHAPE_CHANGED`, as L1) | carrier IP + a layout change | the R6 chain answers (watch page, `R0+vd`); L on that answer (no player request or page fetch of the ladder's own), every L row V + init check → complete |
| T1d | bot check as T1b (owner, 2026-10-15, (က+)); R6's `R0+vd` answer adaptive SABR-only, HLS kept (as V4) | carrier IP + SABR rollout | the R6 chain answers; the H check on that answer's HLS address (≤ 1 per lookup) → one HLS row → complete |
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
  T1c and T1d ((က+), owner 2026-10-15): a D1 follow-up before D3 adds them (the R6 columns; the
  ladder columns with D6). **R6 columns done** (2026-10-15): without the own solver 0 / 15 complete in
  each (15 partial, MCF 15 — the ladder's (က+) target: L on T1c, H on T1d), with it 15 / 15 (MWEB
  through the own solver); the other 970 baseline lines unchanged.

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
  `SHAPE_CHANGED` for L1–L7 and L10, never for a terminal answer (L11). **Done** (2026-10-15).
- **As built** (code `eac2df3`, tests `2b62605`):
  - `ladder/ClientTable.kt` — `MasterClientTable` v2 (2026-10-15): visionOS R0 live (A, S, H
    provisional; version sources yt-dlp and YouTube.js live, the App Store and the answer measured,
    not active); Android O provisional (A provisional; yt-dlp live; YouTube.js and the Play Store
    not active); embedded O measured, not active (error 152-18 on 6 of 6; the page's version, a
    fixed fallback without a page); MWEB O provisional (S provisional; the page's newer version
    not active, never tried). `oRungs()` = Android, MWEB (table order, the inactive ones left out);
    `describe()` = one line in the census report. Live version sources only on
    `raw.githubusercontent.com` (§9 item 3).
  - `ladder/AnswerJudge.kt` — an answer the copied parser refuses, first match: bot-check words
    (any status) or the parser's bot check → `BOT_CHECK`; YouTube asking for a newer app →
    `VERSION_REJECTED`; the parser's age check → `AGE_CHECK`; "not available on this app" →
    `REFUSED`; a definite verdict → `VIDEO_VERDICT`, except **V7**; `LOGIN_REQUIRED` →
    `VIDEO_VERDICT`; unreadable → `REFUSED`; DRM markers beside located formats →
    `VIDEO_VERDICT`; a shape change → `SHAPE_CHANGED` with its kind; SABR only → `SABR_ONLY`;
    else `REFUSED` (e.g. `OK` without streaming data). An answer it reads: a shape change →
    `SHAPE_CHANGED` (with DRM markers `VIDEO_VERDICT`); then `NEEDS_SCRIPT` (a protected row, or
    a script client's `n`), `SABR_ONLY`, `INCOMPLETE` (a format without an address or a size, no
    title or length), `DIRECT`. HTTP 400 → `VERSION_REJECTED`, another failure → `REFUSED`; a 403
    on V's row → `TOKEN_NEEDED`.
  - **V7** (owner, 2026-10-15): an `UNPLAYABLE` or `ERROR` screen the copied parser reads as
    private or unavailable whose words (reason, subreason, messages; markup removed, ≤ 2000
    characters) name no definite marker — age, "inappropriate for some users", sign-in, log-in,
    members, membership, premium, purchase, payment, rental, private, removed, deleted, terminated,
    "no longer available", "available anymore", copyright, violation, country, region, DRM,
    protected, licence, live, premiere — is one client's refusal:
    `REFUSED`, not terminal; the O rungs may go on, and when they do not complete R6's verdict
    stays (video 6's visionOS "This video is not available"; the embedded player's error 152-18;
    main's `player_unavailable.json`, "This video is unavailable"). With a marker it stays a
    terminal `VIDEO_VERDICT`.
  - Shape kinds: `PARTIAL` — renamed keys in the parser's lists, or this video's length unread
    (L3–L6); `MOVED` — formats outside the lists, the status or the HLS address elsewhere, while
    the parser read no adaptive video and audio pair (L1, L2, L7–L10, L13, L15, L16). L11 and L12
    stay verdicts, L14 is `NEEDS_SCRIPT`.
  - `hls`: the answer's field only on an HTTPS `*.googlevideo.com` host, else the located address
    on such a host (`HlsLeg`), else none.
  - Tests: `AnswerJudgeTest` (11): main's fixtures (main's ok and 4k answers are `INCOMPLETE` —
    itag 22, and 251 in ok, carry no size — and `DIRECT` with every size), never `DIRECT` for a
    protected, SABR-only or cipher answer, L1–L16 over the 5 bases, V7's texts, the definite
    markers, version and bot words under every status, HTTP failures, `afterV`, the HLS host
    rule, `OK` without streaming data, the bounded reason text; `ClientTableTest` (6);
    `AnswerJudgeSimTest` (2): V7 live-backed (§5).

### D3 — Health v2, order, budget and stop rules

- `ClientHealth`: memory + `files/master/client-health.json` (guardrail 8); a fake clock in tests.
  - Per client: last class, last pass, cooldown end, working variant (version index, S, H).
  - Cooldowns (v1): `REFUSED` 1 h; `SABR_ONLY`, `TOKEN_NEEDED`, `VERSION_REJECTED`,
    `SHAPE_CHANGED` 24 h; `BOT_CHECK` 30 min for the ladder's request-sending rungs (A, O, S's
    re-ask, a version source; (က+), guardrail 4), never for L, Z, V, the init check, H or local S.
    A client in cooldown is asked only when every other client failed in this lookup, then only
    the one whose cooldown ends first (the bot-check cooldown has no such exception).
  - A learned variant is tried first; when it fails, the base is tried once, then the next client.
- v3: per client also the learned version (with its source and time) and the L shape map (keyed
  by fingerprint; paths only); the version-source clock (≤ 1 fetch a day per identity); a learned
  version or shape map that fails once is dropped (the base once, then the next rung). A class a
  rung repaired (A, S, Z, L, H) counts as a pass with its learned variant; a cooldown starts only
  when the client still fails after its rungs.
- Order, budget (guardrail 7) and stop rules (guardrail 4) in `ClientLadder`. **(က+)** (owner,
  2026-10-15; §9 item 10): after a bot check the ladder sends no InnerTube request; on R6's
  `R0+vd` answer (from the memo, this lookup only) it runs its on-answer steps — L, Z, V, the init
  check, the H check (≤ 1 per lookup) and S locally when R6 already fetched the player script.
- **Done:** a unit test per rule; no request after a stop (after a bot check: no InnerTube request
  of the ladder's own, the on-answer steps allowed — T1, T1b–T1d); H1 and H2 show the saving.
- **As built (core, 2026-10-15):** `ladder/ClientHealth.kt` — per client of `MasterClientTable`: last
  class and time, last pass, cooldown end, working variant (`R0`, `A`, `S`, `Z`, `L`, `H`, `O`), asked
  and passed counts, learned version (with source and time), shape maps (≤ 4 per client, ≤ 64 key
  paths each); the version-source clock (≤ 1 a day per identity) and the 30 min bot-check window
  (`mayAsk(sendsRequest = false)` stays open for L, Z, V, the init check, H and a local S). JSON
  text through `OwnSolverTextFile`; every stored string matches a strict pattern on write and on
  read; an unreadable file starts empty. `ClientHealthTest`: 10 tests (each cooldown, a repaired
  class is a pass, a direct answer without a V pass is no pass, the bot-check window, order and
  the cooling exception, learned versions, shape maps, the source clock, the file round-trip
  without an address, visitor data or video ID, a foreign file). Plan adapted: the app keeps
  health in memory until D7 (the module's `create` has no app `Context`); the ladder v1 sends no
  request of its own, so order and cooldowns take effect with D5a's A and S.

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
- **Follow-up after the close** (owner, 2026-10-16, §9 item 13): A is built after Phase 1.3
  closed, under this plan's guardrails (5 above all) and stop rules; its "Done" stays the line
  above; the own-solver-off target is written from the simulator once it is in.
- **As built (S, 2026-10-15) — Plan adapted:** S is the own solver **in the R6 chain under the
  ladder**: the copied extractor's challenge and apply code (unchanged), the own solver's
  SelfCheck, and the module's `SignatureGate` (V). App: `MasterLadderSwitch.youTube` — ladder off →
  `MasterYouTubeModule(http)`, R6 exactly as before; ladder on → the module with the app's
  `PlayerScriptRunner` only when the own solver is on (with both opt-in flags that runner is
  `OwnPlayerScriptRunner`, never main's), else `NoPlayerScriptRunner`, plus the ladder;
  `AndroidBrowserMasterFallback.create(…, playerScripts)` and `BrowserViewModel` hand it over. The
  simulator's "ladder, own solver on" column is this configuration (§5). Not yet: A
  (`VersionSource`; Y1–Y6 complete today through the chain's alternates, MWEB with S). A: below.
- **As built (A, 2026-10-16; the follow-up after the close) — Plan adapted:** A is in
  `VersionSource.kt` (new) and `ClientLadder.kt`, not in `Rungs.kt` (the parallel agent's file).
  When the R6 chain's visionOS request is answered `VERSION_REJECTED` — once a lookup, never in a
  live-check trial, only when visionOS may be asked (no cooldown, no bot-check window) — A sends
  the chain's request again with **only the version changed** (the one `clientVersion` field and
  the `X-YouTube-Client-Version` header; user agent, device and every other byte stay), at most 3
  requests a lookup (guardrail 7), in guardrail 5's order: the learned version (forgotten when
  rejected) → a self-healed version → the bundled alternates (none today; D8 dropped).
  Self-healed (`VersionSource`): the first live source in visionOS's table row, **yt-dlp's
  table** (YouTube.js stays census-only); at most one read a day per identity, claimed in the
  health before it reads, none in a bot-check window; the user's User-Agent only, no cookie, no
  redirect followed, ≤ 64 KiB, `text/plain` only; one value through the census reader and the
  visionOS pattern, newer than the bundled one; the value read today is kept in memory only.
  A rejected value is dropped (not stored); a bot check or a 429 stops A and opens the 30 min
  window; no answer, or an answer about another video, stops A. A's answer gets visionOS's rungs
  (L on a shape change, Z on missing sizes) and **V on every A answer** (A is provisional: no
  last-hour skip). Only with a V pass (or L rows) does it reach the chain; then the version and
  its source are learned (the health file) and A becomes visionOS's working variant. Otherwise
  it is dropped: the chain, H and the health get visionOS's own rejection (its 24 h cooldown).
  App: `OkHttpVersionSourceReader` (`MasterLadderSwitch.kt`): HTTPS on `raw.githubusercontent.com`
  only, no redirect followed, no cookie, the User-Agent only, ≤ 64 KiB, 10 s. Android's A is not
  wired (yt-dlp = bundled, no alternates). **Known limit:** V's budget is 4 a day per client
  (`StreamCheck`), so while the bundled version stays rejected A completes at most 4 lookups a
  day; then A's answer is dropped (R6's outcome, never worse) and visionOS cools down 24 h — an
  APK with the newer bundled version ends it. **And A waits out a bot check** ((က+), §9 item 10
  (d): the request-sending rungs wait 30 min): visionOS's first request (R6's, without visitor
  data) met a bot check on 24 of 30 census videos, so in census conditions A would run on few
  lookups, and A's request again without visitor data would likely meet one too. The simulator's
  V1 and Y classes have no bot check: its 95 → 65 is an upper bound (found 2026-10-16, planning
  the owner's online check of A).
- **Done (A, 2026-10-16):** `VersionSourceTest` 11; `VersionRungTest` 13 (12 red with A off; the
  trial test is green either way); `ClientHealthTest` + 1 (the day's read claimed before it
  reads); `ClientLadderSimTest`: a source read at most once a case (one day), only in V1, V9,
  H1 and Y1–Y6; app `OkHttpVersionSourceReaderTest` 6 (3 red with the redirect, header and host
  rules removed). Simulator (§5): own solver off MCF 95 → **65** — V1, Y1, Y5 and Y6 complete through
  A (2 player requests, no page fetch, 1 source read; Y5: 1 read for 3 lookups); Y2 and Y3 no A
  request; Y4 not stored, then the cooldown; own solver on 5 (= R6's outcome in V1 and Y1–Y6, V1
  without the page fetch); wrong 0 everywhere; the R6 columns, the trials and v7 byte-identical.
  Module 638 tests, 0 failed; parity green; drift 0. No phone check now: A acts only when visionOS's
  1.02 is rejected and yt-dlp's table names a newer version.
- **A check (the owner's order, item 1, 2026-10-16; §9 item 14):** the
  order: tests and the simulator with many old visionOS versions, and a census "A check" button
  (video 2: 1.02 as R6 asks it, then ≤ 3 old versions only if `DIRECT`), with only versions that
  really existed in yt-dlp / YouTube.js git history (guardrail 5). Found: none older than 1.02 in
  any public reference — yt-dlp's `visionos` (since 2026-07-09, #17184 and its 6 commits) and
  YouTube.js's `VISIONOS` (since 2026-08-13, #1213) list 1.02 only; NewPipe went 1.02 → 1.04
  (2026-08-06); the App Store's visionOS app (its own listing, `com.google.visionosyoutube`, not
  the iOS app the census reads) is at 1.05.34. Old versions would be invented or counted down,
  which guardrail 5 forbids, so it stopped before code and asked. Known: YouTube still answers
  the old 1.02 `DIRECT`; today A could not heal a rejected 1.02 (yt-dlp's table = 1.02, no
  alternate).
  - **The owner's decision: (2) — built 2026-10-16** (`ClientCensus` mode `A_CHECK`; the census
    tool's "A check (visionOS 1.04)" button): video 2 only; visionOS 1.02 as R6 asks it (`R0`, no
    visitor data: the full census's own request); only when that answer is `DIRECT`, NewPipe's
    1.04 once, as the ladder's A rung asks it (that request with only the body's `clientVersion`
    and `X-YouTube-Client-Version` changed, `VersionSource.withVersion`); V on one AVC row of
    1.04's answer when it is `DIRECT`. ≤ 4 requests, ≤ 2 MB; no watch page, no source read, no
    `R0+vd`. A bot check or HTTP 429 on either request stops it at once, with no census for
    30 min. The report's first line gives the outcome; the run is not kept for the diff (the stored
    runs stay; only a cooldown is kept). **Rule:** if 1.04 is `DIRECT` with a V pass on the
    owner's phone, it is bundled as visionOS's alternate (`MasterClientTable`, source NewPipe
    Extractor `ClientsConstants.java`, 2026-08-06; guardrail 5: ≤ 2, public, with source and date;
    held until then as `ClientCensus.A_CHECK_ALTERNATE`) — A's last step, after the learned
    version and the source; R0 stays 1.02, so the R6 columns and parity do not change. Otherwise
    nothing is bundled. Not (3): no invented version is ever asked. Tests:
    `ClientCensusACheckTest` (10); module 648 tests, 0 failed.
  - **Known limit: how YouTube refuses a visionOS version stays unknown.** The A check asks only
    real versions YouTube is expected to accept, so it cannot show a refusal. The judge reads
    `VERSION_REJECTED` from HTTP 400 or the update / unsupported texts
    (`AnswerJudge.VERSION_REFUSAL`); if YouTube refuses another way (another status or text), A
    does not fire and the lookup has R6's outcome (never worse), and W (the weekly check) shows
    the change (CHANGED / BROKEN) for the runbook (§7).

### D5b — Own parser L

- `ShapeLocator` and `ShapeNormalizer` (§3 L, steps 1–8); `SHAPE_CHANGED` → L; L's page locate
  for S.
- Tests: L1–L16 × the 5 base fixtures × V `pass` / `fail` / `unknown`; the round-trip (D1); a
  time bound (JVM ≤ 20 ms for a 300 KB answer; the phone's time is a D7 metric).
- Census tool update, on each live answer, in memory only (counts in the report): (1) the L
  round-trip — canonical JSON gives the same rows as the direct parse; (2) mutations L1, L2, L3,
  L5 and L7 applied to the live answer — L finds the same itags again, and their rows pass V.
- **Steps 7–8 (2026-10-16, the owner's order, item 3): closed without code (the owner chose (1), §9
  item 15; Plan adapted).** Step 7 saves only the scan (8.0 ms median for a 307 KiB answer on the
  JVM); its use needs a map-based entry in `ShapeNormalizer` (the parallel agent's file), and a
  shortcut from our side would skip the whole-answer checks (DRM marker, another video's formats,
  disagreeing groups, several statuses). Step 8: live, the copied `pageSignals` found the player ID,
  the timestamp and visitor data (S solved MWEB's cipher in every Full run); S is the chain's own
  path since D6, so located signals would mean a changed page body and requests R6 does not send
  (guardrail 4; visitor data from an answer is its rejected option (ခ)). Neither changes the
  simulator's outcomes (L14 complete 15/15 in both ladder columns already). Follow-ups (§10, Phase
  1.4): steps 7–8 only when a trigger appears, the shape map recorded only, the last good player ID.
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
- **As built (h-only trial, 2026-10-16; the owner's order, item 2):** a seventh live-check mode
  (`LadderTrial.H_ONLY`, `-Pyft.ladderSkip=h-only`, debug only; workflow choice `h-only`;
  `d7-matrix` mode `h-only`): visionOS's answer reaches the chain as it came; after the chain and
  the own solver's V the result is H's one HLS row alone (the chain's rows left out, complete or
  not) or, without an H row, no row (the chain's failure, else "no media found") with H's reason
  noted; nothing recorded as repaired. Fixed on the way (every mode): H's row carries the chain's
  video key (`site:contentId`); without it the app's browser hook dropped the row, so no H row had
  reached a phone list. Simulator (`HOnlyTrialTest`, `ladder-h-only.txt`, both audio forms with
  `SimHls.separateAudio`): 990 lookups, 440 complete with the one H row, 550 stop with H's reason,
  wrong 0; the other outputs unchanged. D0 saw the sound apart only, so the phone check is one
  download on `h-only` (an AVC quality: the app pairs it with its AAC rendition, P50; VP9 qualities
  stay "No sound"); muxed: the simulator only.

### D6 — O rungs, memo, wiring, flag and merge rule

- **O rungs:** embedded, MWEB (S path) and Android, each only when D0 found it live; otherwise
  skipped with the census date in `describe()`.
- **Memo** (`LookupMemo`, guardrail 7): wraps the HTTP client the R6 chain gets; byte-identical
  requests of the same lookup are served once from memory; media hosts never. (က+): it also
  keeps, for this lookup only, R6's `R0+vd` answer and the player script R6 fetched, for the
  ladder's on-answer steps after a bot check (guardrail 4).
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
- **As built (core, 2026-10-15) — Plan adapted:** the ladder works **in the R6 chain's HTTP path**
  (`ladder/ClientLadder.kt`, `MasterClientLadder`, `LadderLookup`): R6 (the copied extractor,
  unchanged) asks its clients as today through `LookupMemo.chain`, so every row still comes from the
  copied parser and the row-builder parity holds by construction. Each player answer about another
  video is dropped (F4, `RESPONSE_CHANGED`); each visionOS answer is judged (`AnswerJudge`):
  `SHAPE_CHANGED` → L (`ShapeNormalizer`, `LRows`; V and the init check on one ≤ 4 KiB read per row
  through `MediaStartReader`; the chain gets canonical JSON with the passed rows only);
  `INCOMPLETE` → Z (sizes of the rows to offer); `DIRECT` (and Z's rows) → V when visionOS has no
  pass in the last hour (`StreamCheck`, 1 per lookup, budget key = the client ID; a list whose
  checked row got 403 is refused by address — the merge rule removes those rows from the chain's
  result, so the chain's requests stay R6's). After the chain and the own solver's V: a verdict
  about the video keeps R6's result; a complete result too; otherwise the H leg on visionOS's HLS
  address (length from the copied parser, else L's binding) adds one HLS row. Health records
  every visionOS class and the rung that repaired it. `MasterYouTubeModule(http, playerScripts,
  ladder = null)`: null = R6, unchanged. App: `-Pyft.clientLadder` (debug only, with
  masterCapture; BuildConfig `CLIENT_LADDER_ENABLED`, release and preview `false`),
  `MasterLadderSwitch` + `OkHttpMediaStartReader` (HTTPS `googlevideo.com` only, also after a
  redirect; no cookie or credentials; `Range: bytes=0-4095`), `AndroidBrowserMasterFallback.create`
  passes the ladder only with the flag; workflow input `client_ladder` (default `false`; push and
  label builds `false` until D7). Simulator: `ClientLadderSimTest` (§5, D6 core table). Not yet:
  A (D5a). The switches and the health file: §4 D7 "As built (prep)".
- **As built (O rungs, 2026-10-15) — Plan adapted:** the O rungs are the chain's own other clients
  (Android, MWEB, embedded), asked by R6 as today: each answer is judged (`AnswerJudge` with that
  client's profile) and recorded in health; V on its direct rows (one per client per lookup,
  budget key = the client ID; a provisional client on every lookup, a live one skipped after a
  pass in the last hour); a list whose checked row got 403 is refused by address (merge rule). The
  answer reaches the chain as it came. Simulator: totals unchanged; Android V 225 pass, 20 refused
  (403 without control, L4), 25 unknown (network, L4 / F2); MWEB has no direct rows (cipher rows,
  V by the gate).

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
- **As built (prep, 2026-10-15):** `ladder/LadderTrial.kt` — the owner's live-check switch, one
  at a time, on visionOS's live answer in memory before the judge (never a request):
  `visionos` (its answer not used; the chain goes on as after a refusal → O), `visionos-adaptive`
  (its adaptive formats left out → H), `L1`, `L2`, `L3`, `L5`, `L7` (the simulator's layout
  changes of those names → L; L5 leaves a cipherless answer as it came, noted). The ladder notes
  each trial (`trial <mode>: …`). App: `-Pyft.ladderSkip` / `-Pyft.ladderMutate` (debug only,
  checked lists, one at a time; BuildConfig `LADDER_SKIP` / `LADDER_MUTATE`, release and preview
  blank); workflow inputs `ladder_skip` / `ladder_mutate` (choices, default `none`, only with
  `client_ladder` on a manual run; summary line). One ladder per process (health thread-safe,
  shared by every tab), its health in `files/master/client-health.json` (`AtomicTextFile`,
  `BrowserViewModel` hands the files directory over). Census fixes: MWEB's flip (the same
  progressive / adaptive / sizeless counts, formats only moving between protected and without
  an address) is an `INFO` line, its cipher path no shape change; the R6 note says "no other
  client for this video". Tests: `LadderTrialTest` (3; every mode over the B0 videos, own solver
  off and on: wrong 0; L1, L2, L3, L7 complete as the normal mode through L's offer set; skipping
  visionOS → O, its adaptive formats → H or MWEB), `CensusDiffTest` +1. Census L steps (full
  census, each `DIRECT` visionOS answer, in memory, no request): the round-trip and L1, L2, L3,
  L5, L7 on the live answer, each `same` when L's offer set has the direct parse's itags and
  addresses (`n/a` when the change has nothing to change, `none (<why>)` when L gives no rows);
  one note per answer and one with the counts. L's rows are the direct parse's own addresses, so
  the answer's V stands for them there; in the app the ladder checks every L row itself
  (`ClientCensusTest`: the census fixture's formats now carry `approxDurationMs`).
- **The phone check's six APKs (owner, 2026-10-15; "Run workflow" shows only on the default
  branch; seven since D5c's `h-only`, §4 D5c):** job `d7-matrix` in `master-optin-debug-apk.yml`, after `build` (its tests run once
  there; `build` itself unchanged), only for a push whose commit message carries `[d7]` (any
  case). One APK per mode: `normal`, `skip-visionos`, `skip-visionos-adaptive`, `mutate-L1`,
  `mutate-L3`, `mutate-L5`, each with every flag on (own solver, its ladder, client ladder);
  assemble only. The spike debug key is required; the signing cert's SHA-256, the package
  `com.alal.yft.debug` and gradle.properties' versionCode are checked, a mismatch uploads nothing.
  Artifacts `yft-d7-<mode>`, kept 14 days. Each installs as an update over the last (same package,
  key and versionCode). A docs-only push runs nothing (paths-ignore): for the next six, edit
  `.github/d7-apks.txt` in a commit whose message carries `[d7]`. A trial APK (a skip or a
  mutation) keeps the ladder's health in memory only, never reading or writing
  `client-health.json`, so the normal APK's health never holds a trial's verdicts
  (`LadderTrialTest` +1); no reset is needed.
- **Complete on the phone** (the P22 rule, as the simulator's `complete`): the quality list shows
  either the top quality's merged row (video with sound; the normal APK's top: ≤ 1080p AVC,
  2K / 4K VP9 when offered) with its size and the sound-only row with its size, or one HLS row
  (≤ 1080p, with sound; expected with `skip-visionos-adaptive`). Partial: only 360p, no sound or
  no size. Stop: an error. Mobile data: per APK one full download of video 1's top row (sound and
  length checked), the other nine videos listed only; the normal APK also one full census
  (≈ 3.4 MB, ≈ 1 min). The report per APK: complete of 10, the videos (by number) that are not,
  top-quality differences from the normal APK, any bot-check message.
- **Results** (owner, 2026-10-15 UTC; PROGRESS): six APKs × 11 videos complete, wrong 0, no
  bot-check message; video 1's top row downloaded once, on `normal` (sound, 0:19; Plan adapted
  from one per APK); census FULL 78 requests, 2791 KB, 33 s. ❌ Census L steps 0/9 same: L stops
  on every live visionOS answer (`ShapeLocator`'s DRM marker is wider than the parser's).
  ⏸ Until the marker is narrowed (the parallel agent's file), then one census FULL again. Left:
  two weekly checks (≥ 1 h apart), runbook §7.
- **Marker narrowed** (2026-10-15; owner exception for `ShapeLocator.kt` and its L tests;
  PROGRESS): L's DRM marker is the copied parser's four DRM keys with a value under any
  container (never weaker than the parser), or a DRM text inside a format-like object;
  `Scan.drmPath` names its place. The census L line reads a stop as `none (stop <reason>)`, a DRM
  stop with `; DRM marker at <key path>`. Reproduced first on `8164a39` (a live-like answer
  without DRM: `Stop`); now rows on the round-trip and L1, L2, L3, L7. L matrix +5 cases; module
  tests 606, 0 failed; simulator unchanged; drift 0. ⏸ Next: one census FULL on the new `normal`
  APK (L steps 9/9 expected), two weekly checks, runbook §7.
- **Weekly checks** (owner, mobile data, run 1's `normal` APK; §5 table): W 4 2026-10-15 23:55
  UTC and W 5 2026-10-16 01:04 UTC (69 min apart), each `NO CHANGE`, 15 requests, 623 / 622 KB,
  7 / 9 s — §5.2 W met. Run 1 stands for run 2 here (no re-run): W has no L steps; its visionOS
  answers were a bot check (judged before DRM) and R6's `DIRECT`. Runbook §7 updated. ⏸ Left: one
  census FULL on run 2's `normal` APK (≥ 1 h after W 5), L steps expected all `same`.
- **Census FULL on run 2** (owner, 2026-10-16 04:01 UTC, mobile data, 2 h 57 min after W 5;
  PROGRESS): 10 videos, 78 requests, 3379 KB, 45 s, not stopped. L steps on the 9 `DIRECT`
  visionOS answers: round-trip, L1, L2, L3 and L7 9/9 `same`, L5 9 `n/a` (no cipher) — L works
  on live answers. Wrong 0, every V pass; H 8 of 9 (census video 10 no segment, as since D0).
  `CHANGED (1)`: visionOS `R0+vd` shape +`streamingData.probeUrl` (back after test 2's −; not
  BREAKING, no Master code reads it; §7).
- **Done** (2026-10-16): the owner's reports (the phone check, W 4 and W 5, the census FULL on
  run 2); runbook §7 updated. §5.2: Parity, Faults, Budget, Live, W, Versions and flag off met,
  wrong 0 everywhere. **Not met: MCF = 0 in the simulator** (§5, D6 core table): own solver on 5
  (V6: O, live only); own solver off 95 (V1 and Y1–Y6 65, the version classes A is for; V7, V9,
  H1 and F4 25, which need S, i.e. the own solver; V6 5). **Proposed targets** (⏸ the owner):
  with every flag on, as the D7 APKs: MCF ≤ 5, V6 only, until an O rung passes the gate (§9
  item 9); with the own solver off: the simulator's number once A is in (D5a). Open outside D7:
  A (D5a), L steps 7–8 (D5b), H's phone download (D5c).
- **After D7: H status fix** (owner, 2026-10-16; PROGRESS): census video 10's `no-segment`
  (tests 1–3, run 2) was D0's `HlsCheck.statusOf` reading the app client's `RESPONSE_TOO_LARGE`
  (with the answer's `2xx` code) as `2xx`: a variant playlist over 256 KiB → no body → no
  segment. Fixed (the reason first; a failed read is never `2xx`); HlsLeg unchanged: `too-large`
  is unknown there (no row, never wrong; before: fail, no row). ⏸ One census FULL on the new APK
  confirms (`too-large` on video 10; §7).
- **Census FULL on the H fix APK** (owner, 2026-10-16 05:26 UTC, mobile data, 1 h 25 min after
  run 2's; `7277fd8` installed as an update; PROGRESS): 10 videos, 78 requests, 2791 KB, 71 s, not
  stopped. `BROKEN (4)`: H video 10 `variant 2xx → too-large; segment no-segment → skip`, the
  expected line — the fix works on the phone and the variant playlist really passes 256 KiB (not
  an empty playlist); visionOS `R0+vd` shape −`streamingData.probeUrl` and MWEB R0
  +`streamingData.probeUrl` (the known come-and-go; no Master code reads it; not BREAKING); the App
  Store unreachable (`NETWORK`, as in tests 1 and 2; measured, not active; yt-dlp and YouTube.js
  still read 1.02, the bundled value). L steps 9/9 `same` (L5 n/a); wrong 0, every V pass; H 8 of
  9 `2xx` (video 10 `too-large` → unknown, no row). **D7 closed.**
- **Variant cap 1 MiB** (the owner's decision (ခ), 2026-10-16; guardrail 6, §9 item 12; PROGRESS):
  `HlsCheck.MAX_VARIANT_BYTES` = 1 MiB (was 256 KiB); the master's stays 256 KiB
  (`MAX_MASTER_BYTES`), HlsLeg's segment start 64 KiB. The census and HlsLeg read with it (HlsLeg's
  code unchanged; its step-3 comment updated with the owner's OK; PROGRESS_PARALLEL.md). 1 MiB
  holds about an hour of video (≈ 74 min at ≈ 1.2 KB a segment line, an estimate); longer →
  `too-large` → unknown, no row, never wrong. Cost: at most 1 MiB more per H check, only on a long
  video (FULL ≤ 6 MiB; W's H runs on video 1 or 2, both short). `HlsLongPlaylistTest` 5 → 7 (2 red
  on `7277fd8`). ⏸ One census FULL on the new APK: `CHANGED H visionos video 10: variant too-large
  → 2xx; segment skip → 2xx` once (§7).
- **Census FULL on the 1 MiB APK** (owner, 2026-10-16 06:56 UTC, mobile data, 1 h 30 min after
  the 05:26 UTC run; `c9c5959` installed as an update; PROGRESS): 10 videos, 79 requests, 2781 KB,
  45 s, not stopped; no `BROKEN`. `CHANGED (3)`: H video 10 `variant too-large → 2xx; segment skip
  → 2xx`, the expected line (212 segments, 1203.4 s, duration match, 3 requests); MWEB R0 shape
  −`streamingData.probeUrl` (the come-and-go; not BREAKING); the App Store reachable again (its
  listing names no visionOS device, so no visionOS value; measured, not active). L steps 9/9
  `same` (L5 n/a); wrong 0, every V pass; **H 9 of 9** `2xx` / `2xx` / `2xx` — the first census
  with every asked H video passing. Nothing to do. ⏸ The owner, as since "Done" above: A (D5a)
  next, or close with the proposed targets.
- **Phase 1.3 closed** (the owner's decision (ခ), 2026-10-16; §9 item 13; PROGRESS): on the
  proposed targets above — every flag on (the D7 APKs): MCF ≤ 5, V6 only, until an O rung passes
  the gate; wrong 0 everywhere; the own solver off: 95 today, its target written from the
  simulator once A is in. Next, as a follow-up after the close (owner, continuous): A (D5a).
  Open, not part of the close: L steps 7–8 (D5b), H's phone download (D5c).
- **After the close: A** (2026-10-16; §4 D5a "As built (A)"): the own solver off 95 → **65** in
  the simulator, now its target (§5.1); every flag on unchanged (MCF 5, V6 only), wrong 0.
- **Run 3** (the owner's order, 2026-10-16, item 4; `.github/d7-apks.txt` "run 3", the `[d7]`
  commit after `9177a90`; artifacts `yft-d7-<mode>`): the seven APKs with the A check (`0a63181`,
  after run 2; the census tool's third button — the owner tools are on in every `d7-matrix` APK,
  `yft.masterCapture` and `yft.ownSolver`) and the h-only trial (`e31bb90`). The phone steps
  (mobile data; each APK installs as an update over the last):
  1. `normal`: "YFT client census" → **A check (visionOS 1.04)** (video 2; ≤ 4 requests,
     ≤ 2 MB) → the report's first line ("A CHECK — …"; Copy gives the whole report).
  2. `h-only`: video 1 in YFT's browser → the list holds H's HLS qualities only: AVC rows (up to
     1080p) with sound, merged with the AAC rendition; VP9-only rows (above 1080p, when offered)
     "No sound" (§10 item 5) → download one row without "No sound" → the MP4 plays with picture
     and sound, its length the video's ±2 s. No list: the error's details (the `ladder H: …` line).
  3. Optional: the other five APKs, the 10 videos' lists complete as in run 2.

  Then: 1.04 `DIRECT` with V `PASS` → `ClientCensus.A_CHECK_ALTERNATE` goes into
  `MasterClientTable`'s visionOS `alternates` (R0 stays 1.02; parity unchanged); any other result
  keeps it out (§9 item 14); (ဂ) is decided then. The H download is D5c's "Done" for the form D0
  saw (the sound apart).

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

**Not Master-caused:** a terminal verdict (age, private, removed, geo, DRM, a bot check R6's chain does not answer) and a
**server closure** — every allowed path refused (a token rule, SABR only, the client removed,
every allowed version rejected). Master then stops cleanly with R6's verdict, and W shows it
within a week.

Targets: MCF = 0 and wrong = 0 in every simulator class, in both ladder columns; MCF = 0 in the
owner's D7 check and in every weekly check. A live MCF is a top-priority bug: it is fixed and
becomes a simulator class (as Phase 1.2 added M9b and M9c). Honest limit: 0 is proved for the
modelled classes; an unmodelled change ends in a clean stop, W shows it, and it becomes a class.

**Closing targets** (the owner's decision (ခ), 2026-10-16, §9 item 13; proposed at §4 D7 "Done"):
Phase 1.3 closed with MCF ≤ 5 with every flag on (the D7 APKs' configuration; V6 only: an O rung,
live only), until an O rung passes the gate (§9 item 9), and wrong = 0 everywhere; with the own
solver off: **MCF ≤ 65**, the simulator's number once A was in (2026-10-16; no bot check in its
classes — live, A waits out (က+)'s 30 min after a bot check, §4 D5a "As built (A)"; 95 at the
close: V1 and Y1–Y6 65, the classes A is for — A completes V1, Y1, Y5 and Y6, 30; left: Y2–Y4 35,
where no source gives a newer version A could send or its version is rejected too, and V7, V9, H1
and F4 25 — all need S, i.e. the own solver; V6 5). MCF = 0 stays the aim.

### 5.2 Criteria

- **Parity:** the D1 parity gate and the L round-trip green; flag on and visionOS healthy: one
  request, the R6 rows; live: the same rows as R6 on 10 of 10 videos (extras only where R6 gave
  none).
- **Simulator:** wrong = 0 and MCF = 0 in every class and fault, in both ladder columns (closed
  on the closing targets, §5.1).
  - T1–T3 and T1b–T1d (owner, 2026-10-15): after such an answer the ladder sends no InnerTube
    request (player or page) that R6 would not send, in every lookup; T1–T3 and T1b: its result
    equals R6's (T1b: three lookups in a row, each completes as R6 does); T1c and T1d ((က+), §9
    item 10): complete on R6's `R0+vd` answer through L (T1c) or H (T1d), with no more player
    requests or page fetches than R6; the 30 min cooldown blocks only the request-sending rungs
    (A, O, S's re-ask, a version source). T3 "removed" — **owner decision (2026-10-15, after
    `2b62605`):** main's `player_unavailable.json` ("This video is unavailable", no definite
    marker) stays `REFUSED` under V7 (one client's refusal, not terminal) and O goes on: Android
    and MWEB are asked (2 player requests more than R6, no page beyond R6's), refuse too, and the
    lookup ends with R6's verdict, wrong 0. A removal in YouTube's own words ("removed",
    "deleted", "terminated", "no longer available" and the other definite markers) stays
    terminal; the markers are never weakened.
  - The ladder is never worse than R6 in any class.
  - Live-backed column: complete in V1–V3 (V1 through a live alternate, a usable source or D8);
    in V4–V5 and V10 when D0 found H live; in V6–V9 only through census-live O rungs, else a
    clean failure with R6's verdict; V11 = R0. H1 saves at least one request; H2 (owner,
    2026-10-15): the second lookup makes ≤ 1 player request and completes (the first needs 2).
  - L: complete in L1–L7, L10 (found), L13 (with H live), L14 and L15 with V `pass`; L8 only
    this video's rows; L9 and L12 no rows; L11 = R6; no L row with V `fail` or `unknown`; L16
    within the time bound.
  - Y: Y1 complete through the self-healed version when a usable source exists (else through an
    alternate, or a clean stop); Y2–Y6 as listed; ≤ 1 source fetch a day. Met with A
    (2026-10-16, `VersionRungTest`; the own solver off).
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
| H (token step: master / variant / segment) | 1 / 1: 2xx / 2xx / 2xx, no key, 17 variants ≤ 2160p, audio separate, duration matches | 1 / 1: as valid 1 | 1 / 1: as valid 1 | **provisional** (4 / 4 passed, wrong 0 — valid runs 1–3 and W 2 — but all on video 2: 1 distinct video < 4, §9 item 9; W 1: not asked, video 1 met a bot check) |
| Android | 2 / 2 `SABR_ONLY`: the 360p progressive row only (V pass) | 2 / 2: as valid 1 | 2 / 2: as valid 1 | **provisional** (6 / 6 passed, 3 of 3 runs, wrong 0, but on 2 distinct videos — 2 and 6 — < 4, §9 item 9; live before the distinct count): the 360p progressive row only (`SABR_ONLY`) |
| embedded | 0 / 2 `VIDEO_VERDICT` (error 152-18) | 0 / 2: as valid 1 | 0 / 2: as valid 1 | not live (0 / 6): measured, not active (2026-10-15) |
| MWEB | 2 / 2 `NEEDS_SCRIPT` → S ok-sig+n, V pass | 2 / 2: video 2 as valid 1; video 6 adaptive formats without address or cipher (25), only the 360p progressive row (S, V pass) | 2 / 2: video 2 adaptive formats without address or cipher (29), only the 360p progressive row (S, V pass); video 6 ≤ 1080p (S, V pass) | **provisional** (6 / 6 passed, 3 of 3 runs, wrong 0, but on 2 distinct videos — 2 and 6 — < 4, §9 item 9; live before the distinct count); on 2 of 6 answers the adaptive formats had no address (360p only) |

**Gate** (§9 item 9, valid runs 1–3 and W 2; asked / passed; wrong 0: every answer bound to its
video, no V fail; the minimum counts **distinct** videos, owner 2026-10-15 after W 2): **live** —
none; **provisional** (asked on fewer than 4 distinct videos) — Android 6 / 6 (videos 2 and 6; 3
of 3 runs; the 360p progressive row only), MWEB 6 / 6 (videos 2 and 6; 3 of 3 runs; S path; on 2
of 6 answers the adaptive formats came without address, so 360p only), H 4 / 4 (video 2 only:
valid runs 1–3 and W 2) and S (0 asked); **not live** — embedded 0 / 6 (error 152-18); A: none
bundled. R0 is R6's own first request (3 / 30 passed, bot check on 24); `R0+vd` 24 / 24. Before
the distinct count (`aba372a`): live Android and MWEB, provisional H and S. Provisional rungs are
wired as fallbacks after visionOS fails, V on every row (§4 D0 gate), so where this plan says
"live" for H or an O rung (§3, D1's live-backed column, D6, §5.2, §7 step 7) a provisional rung
counts the same way; §9 decision 5 ("neither H nor any O rung live") therefore does not stop
Phase 1.3 (decided 2026-10-15, the owner delegated). Leaving provisional:
H through the H check on every video whose `R0+vd` answer is `DIRECT` ((က+), from the next Full
run: up to 9 distinct videos a run); Android and MWEB only on videos without a bot check (no O
rung is asked after one), so far videos 2 and 6 only.

**Known limit** (2026-10-15): the O rungs were sampled on videos 2 and 6 only (the bot-check rule
asks no O rung after a bot check). The owner's point 1 of 2026-10-15 (O rungs back to live) was
declined by the agent with the owner's delegation: 2 distinct videos are thin; the gate stays ≥ 4
distinct videos; Android and MWEB stay provisional, wired as fallbacks after visionOS fails, V on
every row (§9 items 5 and 9).

**V7 live-backed probe** (D2, `AnswerJudgeSimTest`, before D6's ladder): 10 simulated worlds — the
5 bases × visionOS's two V7 answers (the simulator's "not available on this app"; video 6's "This
video is not available", made in code as the census saw it). visionOS is judged `REFUSED` (not
terminal) on 10 / 10; then, as the census asks them (the watch page's signals, no cookie or
token), the table's O rungs: Android gives its 360p progressive row only (`SABR_ONLY`, V pass, not
complete), MWEB (`NEEDS_SCRIPT`: sig and n through the world's solver, V on every row) completes
on 10 / 10; wrong 0, MCF 0, credentials 0; 3 player requests, 1 page each. Controls (5 bases):
T2 and T3 private, geo and DRM stop at visionOS (terminal; 1 player request, no page, no O
request); T3 removed (main's unavailable answer, `REFUSED` by V7) asks Android and MWEB, which
refuse too: no row, clean stop, wrong 0, MCF 0. Lines in `build/ladder-sim/v7-live-backed.txt`
(and `-controls.txt`); the R6 baseline is unchanged. From the B + D2 APK on, the census cells
above that read `VIDEO_VERDICT` for video 6's visionOS answer and the embedded player's error
152-18 read `REFUSED` (the judge's V7 rule, not a YouTube change).

Census on the B + D2 APK (`595d259`, the fixed debug key; the owner's test runs 1–3, the first
after the one uninstall, §7; a cell = videos with V- or H-passed rows / videos asked; test 1:
2026-10-15 09:15 UTC, mobile data (metered), 10 videos (1–10), 78 requests, 3388 KB, 69 s, not
stopped, `FIRST RUN` + 8 `INFO` lines for the answered bot checks):

| Rung | test 1 | test 2 | test 3 |
|---|---|---|---|
| R0 visionOS (adaptive rows) | 1 / 10: video 2 `DIRECT` (≤ 2160p, V pass); `BOT_CHECK` 8; video 6 `REFUSED` (V7: "This video is not available"; was `VIDEO_VERDICT`) | 1 / 10: as test 1 | 0 / 10: video 2 `DIRECT` (≤ 2160p) but V `UNKNOWN` (`NETWORK`); `BOT_CHECK` 8; video 6 `REFUSED` |
| R0+vd (R6's chain request) | 8 / 8 `DIRECT`, V pass | 8 / 8 `DIRECT`, V pass | 8 / 8 `DIRECT`, V pass |
| A alternates | none bundled | none bundled | none bundled |
| S (visionOS with page signals) | 0 asked (no cipher in a visionOS answer) | 0 asked | 0 asked |
| H (master / variant / segment) | **8 / 9**: videos 1–5 and 7–9 2xx / 2xx / 2xx, no key, audio separate, duration within 2 s; video 10: master and variant 2xx, but the master's highest variant is 240p (its formats reach 720p) and that variant's playlist read no segment (`no-segment`, 2 requests; 2026-10-16: our status bug, a playlist over 256 KiB read as `2xx`, §7) | **8 / 9**: as test 1 (video 10 `no-segment` again: repeatable for that video) | **6 / 9**: videos 3–5 and 7–9 as test 1; videos 1 and 2: the master request failed on the network (`net`, 1 request each, variant and segment skipped; 2 `BROKEN` lines, §7 step 7; not a YouTube refusal, no wrong row); video 10 `no-segment` again |
| Android | 2 / 2 `SABR_ONLY`: the 360p progressive row only (V pass) | 2 / 2: as test 1 | 2 / 2: as test 1 |
| embedded | 0 / 2 `REFUSED` (error 152-18; V7, was `VIDEO_VERDICT`) | 0 / 2: as test 1 | 0 / 2: as test 1 |
| MWEB | 2 / 2 `NEEDS_SCRIPT` → S ok-sig+n, V pass: video 2 ≤ 2160p, video 6 ≤ 1080p (no address-less adaptive answer) | 2 / 2: video 2 adaptive formats without address (29; the bundled and the page's version alike), only the 360p progressive row (S, V pass), as valid run 3; video 6 ≤ 1080p | 2 / 2: video 2 back to ≤ 2160p (30 adaptive formats with a ciphered address, S ok-sig+n, V pass; as test 1); video 6 ≤ 1080p |

Test 1 also: B5–B9 and V7 as expected (the header lists the 10 videos asked; H on every video
whose visionOS answer is `DIRECT`; Full ≤ 6 MB; the client table line); pages 10 / 10 HTTP 200
(MWEB 2.20261014.01.00; player 21090930 on 8, 0229cd64 on videos 6 and 7; S solved both). B7's
version acceptance (video 2): MWEB with the page's newer 2.20261014.01.00 is **accepted** (the
same `NEEDS_SCRIPT` answer, 30 formats; the step checks acceptance, not rows) — the first time a
newer page value was tried; embedded's is not tried (measured, not active). Sources as valid
runs 1–3, except the App Store (`NETWORK` this time). Video 10's H: the 2xx variant playlist held
no `#EXTINF` segment and the master listed only ≤ 240p, so YouTube's HLS for that video looked
incomplete (a reading; corrected 2026-10-16: the `2xx` was our status bug on a playlist over
256 KiB, §7; the ≤ 240p master stays a reading). R6 answered
video 10 itself (23 adaptive rows ≤ 720p, V pass) and H gives no row there (no wrong row). Gate
(§9 item 9) after tests 2–3: H was asked on 9 distinct videos for the first time (8 passed,
89 %) — live if ≥ 60 % in ≥ 2 of the 3 tests and wrong 0; Android and MWEB still on videos 2
and 6 only (provisional).

Test 2 (11:59 UTC, 2 h 44 min after test 1; 10 videos, 78 requests, 3382 KB, 62 s, not stopped):
`CHANGED (7)` — MWEB video 2's address-less adaptive answer (R0 and SH: 0/0/30/0/1 → 0/0/1/29/1,
2160p → 360p, shape −`adaptiveFormats[].signatureCipher`), the known flip of valid runs 2–3 (not
a layout change); and −`streamingData.probeUrl` in the visionOS `R0+vd` answers (YouTube side; no
Master code reads it; no size missing, V pass on every row). The same 8 answered bot checks;
pages: player 21090930 on 8, 0229cd64 on 2. Gate: H ≥ 60 % in 2 of 2 tests (89 %, 89 %) on 9
distinct videos, wrong 0 — the ≥ 2 of 3 rule is met; H is live once test 3 keeps wrong 0 (the
client table's H changes in code only after that, on the owner's word). Android and MWEB: still
videos 2 and 6 only (provisional).

Test 3 (13:20 UTC, 1 h 21 min after test 2; 10 videos, 74 requests, 2773 KB, 97 s, not stopped;
page 1 took 6490 ms, a slow network): `BROKEN (10)` — `BROKEN` because of H on videos 1 and 2
(two `BROKEN` lines, §7 step 7: the master request failed on the network, `net`, 1 request each,
variant and segment skipped; not a YouTube refusal, no wrong row); also visionOS R0 video 2's V
PASS → `UNKNOWN` (`NETWORK`), MWEB video 2 back to its ciphered adaptive answer (R0 and SH:
0/0/1/29/1 → 0/0/30/0/1, 360p → 2160p, shape +`adaptiveFormats[].signatureCipher`, MWEB R0 also
−`adPlacements`; the known flip, back) and the App Store reachable again (21.41.5, no visionOS
device listed). The same 8 answered bot checks (`INFO`); pages: player 21090930 on 8, 0229cd64 on
videos 4 and 10; MWEB page version accepted again. The `net` failures read as a short
mobile-network blip (a reading). Gate (§9 item 9): H ≥ 60 % in 3 of 3 tests (89 %, 89 %, 67 %) on
9 distinct videos, wrong 0 — **H meets the gate (live)**; the client table's H (provisional →
live) changes in code only on the owner's word. Android and MWEB: still videos 2 and 6 only
(provisional).

Weekly checks (W, §4 D0; the diff against the last stored run):

| Check | When, network, APK | Requests, bytes, time | Asked | Diff |
|---|---|---|---|---|
| W 1 | 2026-10-15 03:39 UTC (94 min after valid run 3), mobile data (metered), `81388f8` | 8, 372 KB, 4 s (limits 20, 2 MB, 60 s) | videos 1 and 5: visionOS R0 `BOT_CHECK` → answered as R6 (watch page, `R0+vd` `DIRECT`, V pass; video 1 ≤ 240p, video 5 ≤ 1080p); no H and no Android, embedded or MWEB (video 1 met a bot check); version sources read < 24 h ago, not read | `CHANGED (2)`: only the two answered bot checks; after the owner's fixes (2026-10-15) they are `INFO` lines and the result reads `NO CHANGE` |
| W 2 | 2026-10-15 04:34 UTC (55 min after W 1), mobile data (metered), `3e8f378` | 17, 448 KB, 10.5 s (limits 20, 2 MB, 60 s) | videos 1 and 5: visionOS R0 `BOT_CHECK` → answered as R6 (`R0+vd` `DIRECT`, V pass; video 1 ≤ 240p, video 5 ≤ 1080p); video 2: visionOS R0 `DIRECT` ≤ 2160p (27 adaptive), V pass; H on video 2: 2xx / 2xx / 2xx, 17 variants ≤ 2160p, audio separate, codecs avc1 / mp4a / vp09, key `NONE`, 38 segments, duration matches, 3 requests; no Android, embedded or MWEB (video 1 met a bot check); version sources read: as valid runs 1–3 | `FIRST RUN` (no diff): the owner uninstalled the old app before installing `3e8f378`, which deleted the stored runs (not a bug, no code change; §7); the two bot checks are `INFO` lines (owner item 4 ✅); the header named 6 videos (the slots set) while 3 were asked (fixed in the next census APK) |
| W 3 | 2026-10-15 14:40 UTC (80 min after test 3), mobile data (metered), `595d259` | 15, 624 KB, 10.9 s (limits 20, 2 MB, 60 s) | videos 1 and 5: visionOS R0 `BOT_CHECK` → answered as R6 (`R0+vd` `DIRECT`, V pass; video 1 ≤ 240p, video 5 ≤ 1080p); H on video 1's `R0+vd` answer (B5): 2xx / 2xx / 2xx, 8 variants ≤ 240p, audio separate, codecs avc1 / mp4a / vp09, key `NONE`, 3 segments, duration matches, 3 requests; video 2 (B6; visionOS not asked, as H already ran): watch page 200 (MWEB 2.20261014.01.00, player 21090930), Android `SABR_ONLY` (the 360p progressive row, V pass), MWEB `NEEDS_SCRIPT` (30 ciphered adaptive formats ≤ 2160p; V `skip-needs-S`: W runs no S); embedded not asked (measured, not active); version sources read < 24 h ago, not read, so no newer version was tried (B7) | `CHANGED (1)`: H on video 1, master `net` → 2xx, variant and segment skip → 2xx — test 3's network failure gone (§7 step 7: nothing to fix); the two answered bot checks are `INFO` lines; no YouTube-side change |
| W 4 | 2026-10-15 23:55 UTC (72 min after the D7 census FULL), mobile data (metered), `80ccb10` (D7 run 1 `normal`) | 15, 623 KB, 7 s (limits 20, 2 MB, 60 s) | 3 census videos; videos 1 and 5: visionOS R0 `BOT_CHECK` → answered as R6 (`R0+vd` `DIRECT`, V pass); H on video 1: 2xx, length matched | `NO CHANGE` |
| W 5 | 2026-10-16 01:04 UTC (69 min after W 4), mobile data (metered), `80ccb10` (D7 run 1 `normal`) | 15, 622 KB, 9 s (limits 20, 2 MB, 60 s) | as W 4 | `NO CHANGE` |

D0 is **done** (2026-10-15): 3 valid Full runs, the gate, the version-source table and weekly check 1.

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
| T1c | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| T1d | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| T2 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| T3 | 0/20 complete, 20 clean stop; wrong 0, MCF 0 | 0/20 complete, 20 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| H1 | 0/10 complete, 10 partial; wrong 0, MCF 10 | 10/10 complete; wrong 0, MCF 0 | — (D6) | — (D6) |
| H2 | 0/10 complete, 10 partial; wrong 0, MCF 10 | 0/10 complete, 10 clean stop; wrong 0, MCF 10 | — (D6) | — (D6) |
| F1 | 0/10 complete, 10 partial; wrong 0, MCF 0 | 0/10 complete, 5 partial, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| F2 | 0/5 complete, 5 partial; wrong 0, MCF 0 | 0/5 complete, 5 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| F3 | 0/25 complete, 25 clean stop; wrong 0, MCF 0 | 0/25 complete, 25 clean stop; wrong 0, MCF 0 | — (D6) | — (D6) |
| F4 | 0/5 complete, 5 with a wrong row; wrong 28, MCF 5 | 0/5 complete, 5 with a wrong row; wrong 28, MCF 5 | — (D6) | — (D6) |
| all | wrong 72, MCF 275 (485 cases, 515 lookups) | wrong 72, MCF 160 (485 cases, 515 lookups) | — (D6) | — (D6) |

D6 core (2026-10-15; `ClientLadderSimTest`, `build/ladder-sim/ladder-summary.md`): every case through
R6 and through the same reader with the ladder v1 (L, Z, V, H on visionOS's answers; no InnerTube
request of its own), own solver off and on, each in its own world; with T1b–T1d (495 cases, 545
lookups per column). Asserted: never worse than R6 in any lookup (complete > without sizes >
partial > clean stop), no new wrong row, V1–V5 and L1–L15 no more player requests than R6, T1–T3
and T1b–T1d no player request or page fetch R6 would not send, no credentials, ≤ 8 L reads a
lookup. The two plan columns above (live-backed, all simulated) are filled when the O rungs and
D5a's A and S are in; open now: the own-solver-off classes that need S (V7, V9, Y2–Y4, H1; in
Y2–Y4 no source gives a newer version A could send, or its version is rejected too), F4 without
the own solver (a clean partial; the next client's rows need S) and V6 (O, live only). With A
(2026-10-16): V1, Y1, Y5 and Y6 complete with the own solver off (2 player requests, no page
fetch, 1 source read); with it on the outcome stays R6's (V1 now without the page fetch).

| Class | R6, own solver off | ladder, own solver off | R6, own solver on | ladder, own solver on |
|---|---|---|---|---|
| V1 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 (A) | 5/5 complete; wrong 0, MCF 0 | = R6 |
| V2 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | 0/5 complete, 5 clean stop; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 |
| V3 | 0/5 complete, 5 without sizes; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 | 0/5 complete, 5 without sizes; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 |
| V4 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 | = R6 |
| V5 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | 5/5 complete; wrong 0, MCF 0 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | 5/5 complete; wrong 0, MCF 0 |
| V6 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | 0/5 complete, 3 partial, 2 clean stop; wrong 0, MCF 5 | 0/5 complete, 5 with a wrong row; wrong 22, MCF 5 | 0/5 complete, 3 partial, 2 clean stop; wrong 0, MCF 5 |
| V8 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 | = R6 |
| V10 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 | 5/5 complete; wrong 0, MCF 0 | = R6 |
| L1 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 |
| L2 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 |
| L3 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 15/15 complete; wrong 0, MCF 0 | = R6 |
| L4 | 0/30 complete, 30 clean stop; wrong 0, MCF 30 | 30/30 complete; wrong 0, MCF 0 | 0/30 complete, 30 clean stop; wrong 0, MCF 30 | 30/30 complete; wrong 0, MCF 0 |
| L5 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 |
| L7 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 |
| L8 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 |
| L10 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 |
| L13 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 |
| L14 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 |
| L15 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 |
| L16 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 | 0/15 complete, 15 clean stop; wrong 0, MCF 5 | 5/15 complete, 10 clean stop; wrong 0, MCF 0 |
| Y1 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 (A) | 5/5 complete; wrong 0, MCF 0 | = R6 |
| Y5 | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 (A) | 15/15 complete; wrong 0, MCF 0 | = R6 |
| Y6 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 5/5 complete; wrong 0, MCF 0 (A) | 5/5 complete; wrong 0, MCF 0 | = R6 |
| T1c | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 15/15 complete; wrong 0, MCF 0 | = R6 |
| T1d | 0/15 complete, 15 partial; wrong 0, MCF 15 | 15/15 complete; wrong 0, MCF 0 | 15/15 complete; wrong 0, MCF 0 | = R6 |
| H2 | 0/10 complete, 10 partial; wrong 0, MCF 10 | 10/10 complete; wrong 0, MCF 0 | 0/10 complete, 10 clean stop; wrong 0, MCF 10 | 10/10 complete; wrong 0, MCF 0 |
| F4 | 0/5 complete, 5 with a wrong row; wrong 28, MCF 5 | 0/5 complete, 5 partial; wrong 0, MCF 5 | 0/5 complete, 5 with a wrong row; wrong 28, MCF 5 | 5/5 complete; wrong 0, MCF 0 |
| all | wrong 72, MCF 305 | **wrong 0, MCF 65** (95 before A) | wrong 72, MCF 160 | **wrong 0, MCF 5** |

Unchanged (= R6 in both columns): B0, V7, V9, V11, L6, L9, L11, L12, Y2, Y3, Y4, T1, T1b, T2, T3, H1, F1, F2, F3.


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
  no cookies, after a bot check no InnerTube request R6 would not send (the request-sending
  rungs stop for 30 min; on R6's answer only reads on the hosts YouTube returned, (က+)), health
  cooldowns, the budget.
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
- Update the APK over the installed one; never uninstall it first (owner, 2026-10-15, after W 2):
  uninstalling deletes the app's files — W's stored runs (the next check reads `FIRST RUN`, no
  diff), the health file, the cooldown and the census video picks (slots 4–10; slots 4 and 7 go
  back to their defaults).
  - One fixed debug key (2026-10-15, after `2b62605`): until then each CI debug APK was signed
    with the runner's own debug key, a new one per run, so the keys differed and the update
    failed ("App not installed as package conflicts with an existing package"). From the commit
    that added `app/keystore/spike-debug.keystore.b64` on, every opt-in debug APK and its test APK
    carry the spike's fixed debug key (certificate SHA-256
    `a2696dff62331776dee9baf34a150ffcec19e1a358656bc65517d165cc88bbbe`, in each run's summary
    and annotations). Install the first fixed-key APK once after one uninstall; from then on only
    update, never uninstall. That one uninstall loses the census history (the next Full census
    reads `FIRST RUN`; the runs after it, W included, compare with it), the health file, the
    cooldown and the slots (re-enter 5, 6 and 8–10 with the same videos; 4 and 7 go back to
    their defaults). Done once (owner, 2026-10-15): test run 1 (§5) read `FIRST RUN`.
  - The key is public (it is in the repo): install only APKs from this repo's
    `master-optin-debug-apk` workflow. It signs the debug app (`com.alal.yft.debug`) only, never
    a release or preview build (its `CN=Android Debug` certificate fails
    `scripts/verify-release-apk.sh`).

1. Weekly check (W), once a week: `NO CHANGE` → nothing (`INFO` lines need nothing). `CHANGED` or
   `BROKEN` → copy the result to the agent; each line names a step below.
2. One video fails: run the full census on it: which rung, which class.
3. `VERSION_REJECTED`: the learned or self-healed version (a usable source: the reference tables,
   §9 item 3) or an alternate carries it; else a new APK (D8 dropped, §9 item 4).
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

What D7's census runs showed (owner, 2026-10-15/16; PROGRESS):

- visionOS R0 `BOT_CHECK` without visitor data (8 of 9 videos in the full census; videos 1 and 5
  in each W) answered as R6 (visionOS with its visitor data: `DIRECT`, V pass): an `INFO` line,
  nothing to do. A bot check on R6 too → step 1.
- MWEB shape ±`adPlacements`: MWEB's ad data comes and goes between runs (test 3 −, D7 +); not a
  format path, nothing to do.
- visionOS shape ±`streamingData.probeUrl` (`R0+vd` answers; MWEB R0 too): comes and goes too
  (test 2 −, run 2's census FULL +, the 05:26 UTC census FULL − on visionOS and + on MWEB, the
  06:56 UTC one − on MWEB); no Master code reads it, nothing to do. The census names step 6 on every
  shape line; step 6's work is only for a line marked **BREAKING** (a path the copied parser
  reads went away; the check then reads `BROKEN`).
- H on census video 10 (tests 1–3, run 2): master and variant `2xx`, `no-segment`, 0 segments, 2
  requests. **Not YouTube's side** (corrected 2026-10-16; PROGRESS): the app's client answers a
  body over the cap as `RESPONSE_TOO_LARGE` with its `2xx` code, and D0's `HlsCheck.statusOf`
  read the code first, so a variant playlist over 256 KiB read `2xx` with no body. Video 10
  (1203 s) is the census's longest video; its variant lists every segment with its full address
  (its size is not measured: the census never prints it). Fixed: the reason first, a failed read
  is never `2xx` (`HlsLongPlaylistTest`). The first census after the update printed once `BROKEN
  H visionos video 10: variant 2xx → too-large; segment no-segment → skip` (step 7; 2026-10-16
  05:26 UTC): expected, the cause confirmed. With the variant cap at 1 MiB (owner, 2026-10-16) the
  next census printed once `CHANGED H visionos video 10: variant too-large → 2xx; segment skip →
  2xx` (step 7; 06:56 UTC: 212 segments, duration match): expected, the cap confirmed; later runs
  print no line for it. `2xx` and `no-segment` again on that video → a really empty playlist (YouTube's
  side): copy the line to the agent. Another video `no-segment` → step 1.
- Known limit: a very long video's HLS variant playlist passes 1 MiB (guardrail 6's variant cap,
  256 KiB until 2026-10-16; about an hour of video, an estimate) → H `too-large` → unknown, no H
  row, never a wrong row; the lookup keeps its other rows (video အရှည်ကြီး (~၁ နာရီကျော်) တွေမှာ HLS
  playlist `too-large` → H unknown ပေး၊ wrong မဖြစ်). A larger cap is the owner's decision (the app's
  client reads any cap; the cost is bytes on mobile data).
- `CHANGED version visionos from appstore: unreachable (NETWORK)` (step 3; tests 1 and 2, the
  05:26 UTC census FULL): the App Store is a measured source, not used for the version; step 3's
  work is only for `VERSION_REJECTED`; nothing to do. `reachable again` (test 3, the 06:56 UTC
  census FULL; its listing names no visionOS device, so no visionOS value): nothing to do either.
- L steps (full census only; in memory, no request): one line per `DIRECT` visionOS answer;
  `same` on each step (L5 `n/a` without a cipher) is the expected reading (run 2's census FULL:
  9/9 `same`). `differs` or `none (…)` → copy the line to the agent.
  `none (stop DRM_PROTECTED; DRM marker at <path>)` names where L saw DRM that the copied parser
  did not: the agent adds that answer's shape as a test case first, then narrows the marker (as
  `83ce453` did).
- W's cost on mobile data, W 1–5: 8–17 requests, 372–624 KB, 4–11 s (limits 20, 2 MB, 60 s); a
  full census (D7: runs 1 and 2, the H fix APK and the 1 MiB APK): 78–79 requests, 2781–3379 KB,
  33–71 s.

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
   L; no SABR, token or MSE work follows from this plan. (2026-10-15, after item 9's distinct
   count: no rung is live — H, Android and MWEB are provisional, wired as fallbacks with V on
   every row.) **Decided 2026-10-15: does not apply** (the owner delegated the decision to the
   agent): Phase 1.3 goes on with the provisional H and O rungs as fallbacks.
6. **M5 (Phase 1.2 carry-over, `PROGRESS.md` "Phase 1.2 — done"):** not part of this plan; the
   owner decides whether it becomes its own solver task. Not needed for D0.
7. **Bot-check rule** (proposed after census run 1, 2026-10-14): **yes — owner, 2026-10-15.** After
   a bot check the census and the ladder never send a request R6 would not send. Census, for
   each video (owner, after runs 2–4: R6 answers each lookup on its own): the watch page once and
   visionOS once more with the page's visitor data; a bot check on a request that already carried
   visitor data, or HTTP 429, stops the run (30 min cooldown). Ladder: its request-sending rungs
   stop and R6's chain runs as today; since item 10 ((က+)) its local and media-host steps run on
   R6's `R0+vd` answer (D3/D6). Simulator class T1b (per video); §5.2
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
   of 3 valid runs, AND asked on ≥ 4 **distinct** videos (by list number) across the valid runs and
   the weekly checks (owner, 2026-10-15, after W 2), AND wrong = 0. Asked on fewer than 4
   distinct videos → **provisional**: wired only as a fallback after visionOS fails, V
   required on every row, and the weekly check keeps measuring it until it reaches the minimum. No
   code change; the same APK (`81388f8`). Version acceptance runs on video 1 only (no newer version
   is tried after its bot check), so the web page config values stay "not tried"; MWEB keeps its
   bundled version, which works. Note: W asks Android, embedded and MWEB on video 1 only, and
   video 1 met a bot check in every run so far (W 1 too), so W adds samples for them only when
   video 1 escapes it (D7 revisits). H: since the owner's fix (2026-10-15, after W 1) W runs it on
   video 2 when video 1 met a bot check, so W adds an H sample each week — always on video 2, which
   adds no distinct video (W 2: H 4 / 4, all video 2). H leaves provisional at ≥ 4 distinct videos
   with ≥ 60 % passed and wrong 0: under (က+) (item 10) the next Full run asks H on every video
   whose `R0+vd` answer is `DIRECT` (recorded in §5; the owner told). Consequence of the distinct
   count (2026-10-15): Android 6 / 6 and MWEB 6 / 6 were asked on videos 2 and 6 only (2 distinct)
   and are provisional too; no O rung is asked after a bot check, so they reach 4 distinct videos
   only when more videos escape one; no rung is live (item 5: does not apply, decided
   2026-10-15). The owner's point 1 of 2026-10-15 (Android and MWEB back to live) was declined by
   the agent with the owner's delegation: 2 distinct videos are thin, the gate stays ≥ 4 distinct
   videos, and the provisional rungs are wired as fallbacks with V on every row (known limit, §5).
   Tests 1–3 (`595d259`, §4 D0): H 89 %, 89 %, 67 % on 9 distinct videos, wrong 0 — **H meets the
   gate (live)**; W 3: H 1 / 1 (video 1). The table's H changes in code with item 11's OK; Android
   and MWEB stay provisional (videos 2 and 6 only).
10. **The ladder after visionOS's bot check: (က+) — owner, 2026-10-15** (asked after W 1; options
    in `PROGRESS.md`). visionOS's first request met a bot check on 24 of 30 census videos, and
    under the old guardrail 4 the ladder's own rungs (A, S, Z, L, H) then stopped while R6's chain
    answered (`R0+vd`: 24 / 24 `DIRECT`, V pass), so in about 80 % of lookups the ladder added
    nothing. Decided: after a bot check, on R6's `R0+vd` answer and with no new InnerTube request
    (player or page): (a) L (local) plus Z, V and the init check (media host, guardrail 6's
    budget); (b) the H check (≤ 1 per lookup); (c) S locally only — the own solver, SelfCheck and
    V — when R6 already fetched the player script; (d) the 30 min cooldown blocks only the
    request-sending rungs (A, O, S's re-ask, a version source). The census likewise: V and H on an
    `R0+vd` answer that is `DIRECT`. (ခ) rejected (owner: it avoids the bot check instead of
    answering it, close to a bypass, and one visitor ID would link the lookups); (ဂ) rejected.
    Written into guardrail 4, §3, D0, D1 (T1c, T1d), D3, D6, §5.1–§5.2, §6 and the prompt's rule
    4; D3 starts after the owner's OK on this plan change.
11. **Final plan (2026-10-15, after tests 1–3 and W 3):** asked — one OK for (a) client table v3
    (H live; nothing else changes) and (b) the (က+) plan change (item 10); then the run goes on as
    the "Final plan" section orders it (D2 follow-up → T1c / T1d → D3 → D4 → D5a–D5c → D6 → D7).
12. **Guardrail 6's HLS variant cap: (ခ) 1 MiB — owner, 2026-10-16** (asked after the H status
    fix: keep 256 KiB, or raise the variant's): the variant playlist's cap 256 KiB → 1 MiB; the
    master's 256 KiB and HlsLeg's 64 KiB segment start unchanged; `HlsLeg.kt`'s comment edited
    with the owner's OK (its code untouched), one line in PROGRESS_PARALLEL.md. Written into
    guardrail 6, §4 D7, §7 and the Final plan table.
13. **Phase 1.3's close: (ခ) close with the proposed targets, then A as a follow-up — owner,
    2026-10-16** (asked at D7 "Done": A (D5a) first, or close with the targets): closed on §5.1's
    closing targets (every flag on: MCF ≤ 5, V6 only, until an O rung passes the gate; wrong 0
    everywhere; the own solver off: the simulator's number once A is in). After the close, A
    (D5a: `VersionSource`, self-healed versions; guardrail 5, item 3's host) follows as a
    follow-up (owner: "(က) A ကို နောက်ဆက်တွဲအနေနဲ့ ဆက်လုပ်"; continuous, without stopping).
    L steps 7–8 (D5b) and H's phone download (D5c) stay open, not part of the close. Written into
    the Burmese summary, the Final plan, §4 (D5a, D7), §5.1–§5.2 and the prompt. **A done
    2026-10-16** (§4 D5a "As built (A)"): the own solver off 95 → 65, its target in §5.1.
14. **The A check (the owner's order, 2026-10-16, item 1 of 3: A check, D5c, D5b steps 7–8, then
    one `[d7]` APK): asked; answered (2) 2026-10-16** — no visionOS version older than 1.02
    exists in yt-dlp, YouTube.js or NewPipe (§4 D5a "A check"), so the order's old versions would
    break guardrail 5. Options:
    (1) end the A check with this result; (2) the online check with NewPipe's 1.04 instead (real,
    newer: whether A's version-only re-ask works live and whether 1.04 can be the bundled
    alternate; recommended); (3) a guardrail 5 exception for the census button only (invented old
    versions; the only way to see how YouTube refuses). (ဂ) — A after R0's routine bot check, an
    exception to (က+) — is decided after the A check's result. **The owner chose (2)**: the census
    "A check" button (built, §4 D5a "A check"); if 1.04 is `DIRECT` with a V pass on the phone, it
    becomes visionOS's bundled alternate (NewPipe, 2026-08-06), R0 stays 1.02 and parity does not
    change; not (3). How YouTube refuses stays a known limit (§4 D5a); the App Store source's
    listing goes to the backlog (§10).
15. **D5b steps 7–8 (the owner's order, 2026-10-16, item 3): asked; answered (1) 2026-10-16** —
    neither step changes rows, wrong rows or MCF (§4 D5b). Step 7 (the shape-map memory) saves only
    the scan (8.0 ms median on the JVM); using a map needs a new entry in `ShapeNormalizer` (the
    parallel agent's file), else the whole-answer checks are skipped (weaker than today, guardrail
    9). Step 8 (L's page locate for S): the copied reader found every signal live; S runs in the R6
    chain since D6, so located signals need a changed page body, and the chain then sends requests
    R6 does not (guardrail 4 after a bot check; visitor data from an answer = its rejected option
    (ခ)). Options: (1) close steps 7–8 without code (Plan adapted), then the `[d7]` APK —
    recommended; (2) step 7 with the parallel agent (a map-based entry that keeps the whole-answer
    checks; I record, use and drop the maps); (3) step 8 for the player ID only, when the copied
    reader finds none and no bot check came in the lookup (one script fetch R6 would not make; the
    timestamp and visitor data stay out). **The owner chose (1)**: steps 7–8 are closed without code
    (Plan adapted, §4 D5b); three follow-ups wait in the backlog under Phase 1.4 (§10 items 2–4:
    steps 7–8 only when a trigger appears; the shape map recorded only; the last good player ID, an
    exception to D6's "the chain's requests stay R6's"); then the `[d7]` APKs (run 3, §4 D7).

## 10. Backlog

Found on the way and outside every task above; nothing here starts without the owner's go.

1. **The census's App Store source reads the iOS app (found 2026-10-16, the A check).**
   `CensusSources.APP_STORE_URL` looks up `bundleId=com.google.ios.youtube`, YouTube's iOS app,
   which lists no visionOS device (why D0 found no visionOS value there). YouTube's visionOS app
   is a listing of its own (`com.google.visionosyoutube`; 1.05.34 on 2026-09-19, first released
   2026-02-12). Its version has three parts, so it does not match visionOS's pattern
   (`CensusSources.VISION_OS_VERSION`, `^\d{1,2}\.\d{2}$`), and a store version is not
   necessarily the InnerTube `clientVersion` (yt-dlp and YouTube.js send 1.02, NewPipe 1.04).
   Reading it would need a D0 measurement first (does YouTube answer the listing's value, and in
   which form) and stays under guardrail 5 (a public source with source and date; never
   invented, counted up or brute-forced). Until then the source stays "measured, not active" in
   the client table and A never reads it.

**Phase 1.4** (the owner, 2026-10-16, when D5b steps 7–8 were closed without code; §9 item 15):

2. **L steps 7–8 only when a trigger appears.** Step 7 (the shape-map memory) or step 8 (L's page
   locate for S) is built only when the live checks show the need: W reports a **BREAKING** shape
   (a removed path the copied parser reads, `CensusDiff`), or a page signal (the player ID, the
   signature timestamp, visitor data) is missing for the copied reader. Until then both stay
   closed (§4 D5b).
3. **Record the shape map only.** L writes the map it located (the canonical names' paths and the
   shape fingerprint; no value, address or ID) to `ClientHealth` and W's report; nothing reads
   with it, so the whole-answer checks stay as they are (guardrail 9). It shows a moved layout
   before step 7 is needed.
4. **Fall back to the last good player ID.** When the page names no player ID, S uses the one from
   the last successful lookup; V and the init check stay on every row, so a stale ID ends in a
   clean stop, never a wrong row. It changes the chain's requests (a player script R6 would not
   fetch: an exception to D6's "the chain's requests stay R6's", §4 D6 "As built"), so it needs
   the owner's go under guardrail 4, and never after a bot check in the lookup.

5. **VP9 HLS qualities have no sound (found 2026-10-16, before the run 3 APKs).** YouTube's HLS
   names its sound apart (one AAC rendition; D0: codecs avc1, mp4a, vp09). The app pairs each AVC
   quality with it and merges both into one MP4 (P50, `HlsAudioPairing`); a VP9 quality stays
   "No sound": Android's muxer writes VP9 only into WebM, which takes no AAC, and its MP4 takes no
   VP9 (`AudioVideoMuxCompatibility`). At one resolution the row with sound is the one listed
   (`QuickDownloadChoices`), so over H the rows up to 1080p have sound and 1440p / 2160p (VP9
   only) say "No sound"; over the chain, 2K and 4K come as VP9 WebM with Opus (P6). Sound for VP9
   over H would need a merge that writes VP9 and AAC into one file without Android's muxer: new
   core-download work, proven on a phone first, parity kept (guardrail 1).
