# YFT Prompts — Phase 15 (Preview #6 field fixes: TikTok, YouTube start, Delete file, ads)

Agent တွေကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (owner မြင်ခဲ့တာ၊ အကြောင်းရင်း R25–R34၊
task အဆင့်တွေ၊ agent တစ်ယောက်ချင်းရဲ့ ဖိုင်တွေ၊ status board) က [`docs/FIX_ADD_PLAN.md`](../FIX_ADD_PLAN.md)
မှာ ရှိပါတယ်။ Phase 14 ရဲ့ prompt အဟောင်းတွေ (A-background၊ B-tiktok-fresh-links၊ C-fast-merge၊
M-merge-preview6) ကို ဖျက်ထားပါတယ် — လိုရင် `git show a9eea7b:docs/prompts/` နဲ့ ပြန်ကြည့်လို့ရပါတယ်။

## သုံးနည်း (agent ၃ ယောက် တပြိုင်နက်)

1. Chat အသစ် ၃ ခု ဖွင့်ပြီး တစ်ခုစီထဲ prompt တစ်ခုစီရဲ့ code block ကို paste လုပ်ပါ:
   - **Agent A** → [`A-tiktok.md`](A-tiktok.md) — TikTok ကို YouTube လို TikTok ရဲ့ ကိုယ်ပိုင် page/script
     ကနေ data ယူပြီး browser နဲ့ Home နှစ်ခုလုံးမှာ Download ရအောင် (P39 → P40)
   - **Agent B** → [`B-downloads.md`](B-downloads.md) — YouTube download % နဲ့ speed ကို ၃ စက္ကန့်အတွင်း
     စရွေ့ (P41)၊ Downloads မှာ **Delete file** (P42)
   - **Agent C** → [`C-ads.md`](C-ads.md) — Pornhub လို site တွေမှာ sheet က ကြော်ငြာ (0:30) မဟုတ်ဘဲ page
     ရဲ့ video အစစ်ကိုပဲ အမြဲပြ (P43)
2. Agent တစ်ယောက်ချင်းစီ ကိုယ့် branch နဲ့ ကိုယ့် folder (`/data/YFT-A`, `-B`, `-C`) ပေါ်မှာ ကိုယ့်ဖိုင်တွေကိုပဲ
   ပြင်လို့ တစ်ယောက်နဲ့တစ်ယောက် မထိပါဘူး ([`FIX_ADD_PLAN.md` §0.7](../FIX_ADD_PLAN.md#07-three-agents-in-parallel))။
   Task တစ်ခုပြီးတိုင်း checkpoint push လုပ်ပြီး မြန်မာလို အတိုချုပ် report (Level ပါ) ပေးပါမယ်။ နောက်ဆုံး task
   ပြီးရင် `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။
3. **SSH key:** ဒီ computer မှာ အလုပ်လုပ်တဲ့ key ရှိပြီးသား (`/data/.ssh/CURRENT_KEY`၊ 2026-10-09 push
   ရခဲ့)။ Sandbox reset ဖြစ်လို့ key မရှိတော့ရင် (သို့ push "Permission denied") agent က key **အသစ်** လုပ်ပြီး
   (အဟောင်း မရှာပါ) public key တစ်ကြောင်း ပြပါမယ်။ GitHub › YFT › Settings › Deploy keys › Add deploy key
   မှာ ထည့်၊ "Allow write access" ကို အမှန်ခြစ်ပြီး "SSH Done" လို့ ပြောပါ။
4. သုံးယောက်လုံး `READY FOR MERGE` ဖြစ်ရင် **Agent B** ရဲ့ chat ထဲ [`M-merge-preview7.md`](M-merge-preview7.md)
   ကို paste လုပ်ပါ — B → C → A ပေါင်းပြီး full validation စစ်၊ **Preview #7** link ပို့ပါမယ်။
5. Preview #7 ကို ဖုန်းမှာ စမ်းပြီး OK ဆိုရင် [`P8-signed-beta4.md`](P8-signed-beta4.md) (signed beta.4)။
6. Agent တစ်ယောက်က တခြားသူ့ဖိုင်ကို ပြင်ဖို့လိုရင် "Hand-off to …" လို့ report ထဲ ပြောပါမယ်။ အဲဒါကို
   သက်ဆိုင်တဲ့ agent ရဲ့ chat ထဲ copy လုပ်ပေးပါ။
7. **TikTok ကို စောစောစမ်းပါ:** Agent A က P39 ပြီးတာနဲ့ Preview APK link ပို့ပါမယ်။ Sandbox က US network
   ဖြစ်လို့ VPN country ရဲ့ TikTok answer ကို မမြင်ရပါ — ဖုန်း (VPN) မှာ စမ်းပြီး မရရင် sheet ရဲ့ **Details**
   screenshot ကို Agent A chat ထဲ ပို့ပေးရင် P40 မှာ တန်းပြင်ပါမယ်။

## Agent ဘယ်နှစ်ယောက် သုံးမလဲ (အကြံပြုချက်)

- **၃ ယောက် တပြိုင်နက် — အကြံပြုပါတယ်။** A = TikTok (adapter၊ TikTok page in WebView၊ browser/Home
  lookup)၊ B = download engine + Downloads/Library၊ C = ကြော်ငြာ စစ်တာ + sheet ရဲ့ video ရွေးတာ — ဖိုင်ချင်း
  မထိပါဘူး။ ကြာချိန် ၁၅–၂၂ နာရီခန့် (A ရဲ့ TikTok ၁၃–၁၉ နာရီ က အရှည်ဆုံး + merge ၂–၃ နာရီ)။
- **၂ ယောက်ဆိုလည်း ရပါတယ်:** Agent B က P42 ပြီးရင် `C-ads.md` ကို
  `BRANCH_OVERRIDE: work/phase-15-downloads` နဲ့ ဆက်လုပ် (P43)၊ Agent A က P39 → P40။ ကြာချိန် ၁၆–၂၄ နာရီခန့်
  (A က အရှည်ဆုံးမို့ သိပ်မကွာ)။
- **၄ ယောက်ထက် မများပါနဲ့:** ပိုခွဲရင် ဖိုင်တူတွေကို ပြိုင်ပြင်ရပါမယ်။
- CI က branch ၃ ခုစလုံးအတွက် run လို့ တစ်ခါတလေ တန်းစီစောင့်ရနိုင်ပါတယ်။ Docs ပဲပြောင်းတဲ့ push တွေက CI
  မ run ပါ၊ code ပြောင်းတဲ့ push တွေပဲ run ပါတယ်။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `ALLOW_PUSH` | `true` | ကိုယ့် work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | P8 မှာ owner OK ပေးမှ `main` merge၊ push နဲ့ tag |
| `ALLOW_RELEASE` | `false` | P8 မှာ owner OK ပေးမှ release key နဲ့ signed APK |
| `BRANCH_OVERRIDE` | `none` | Agent C prompt ကို Agent B ရဲ့ branch ပေါ်မှာ လုပ်စေချင်ရင် (agent ၂ ယောက် mode) |
| `OWNER ANSWERS` | `none` | ဥပမာ `TT_AGENT=HEADLESS`၊ `TT_HOME_COOKIES=OFF`၊ `TT_WATERMARK=SHOW`၊ `TT_HIDDEN_PAGE=OFF` (A)၊ `FAST_START=OFF`၊ `DELETE_CONFIRM=OFF` (B)၊ `AD_RULE=LENIENT` (C)၊ `MAIN=OK`၊ `P8=OK` |

## ဖုန်းမှာ စမ်းဖို့ APK

- Code ပြောင်းတဲ့ push တိုင်း **Preview APK (test key)** run က `yft-preview-apk` ထုတ်ပါတယ် ("YFT Preview"၊
  `com.alal.yft.preview`)။ Run တိုင်း test key အသစ်ဖြစ်လို့ preview အသစ်မထည့်ခင် အဟောင်းကို uninstall လုပ်ပါ။
- Agent တစ်ယောက်ချင်းရဲ့ preview ကို စောစောစမ်းလို့ရပါတယ် (အထူးသဖြင့် Agent A ရဲ့ TikTok)၊ အဓိကစမ်းရမှာက
  **Preview #7** (သုံးယောက်ပေါင်းပြီး P44 ရဲ့ preview) ပါ။ စစ်ရမယ့်စာရင်း:
  [`FIX_ADD_PLAN.md` §6](../FIX_ADD_PLAN.md#6-owner-phone-checklist)။
- တစ်ခုခု မအောင်မြင်ရင် sheet ရဲ့ **Details** (lookup) သို့ download ရဲ့ **Details** (saving) screenshot ကို
  သက်ဆိုင်တဲ့ agent chat ထဲ ထည့်ပေးပါ။

## Task ဇယား

| Agent | ID | Prompt | အလုပ် | Level | AI agent အချိန် |
| --- | --- | --- | --- | --- | --- |
| A | P39 | [`A-tiktok.md`](A-tiktok.md) | TikTok page ပုံစံ အားလုံးဖတ်၊ ဖုန်း/desktop Chrome agent နှစ်မျိုးစမ်း၊ header error မဖြစ်၊ file တကယ်ရမှပြ (size အမှန်၊ watermark မပါ)၊ မရရင် Details မှာ ဘာကြောင့်လဲ၊ browser မှာ player ရဲ့ file ကို အနည်းဆုံးပေး | Medium | 5–7 နာရီ |
| A | P40 | [`A-tiktok.md`](A-tiktok.md) | YouTube လို TikTok ရဲ့ ကိုယ်ပိုင် page ကနေ: tab ထဲက data၊ For You ရဲ့ API answer (document-start script)၊ Home link အတွက် hidden TikTok page (၁၅ စက္ကန့်အထိ) | Hard | 8–12 နာရီ |
| B | P41 | [`B-downloads.md`](B-downloads.md) | YouTube: ပထမအပိုင်း 1 MiB၊ အပိုင်းထဲမှာ progress၊ batch မစောင့်၊ probe မလို — % နဲ့ speed ၃ စက္ကန့်အတွင်း | Medium | 4–6 နာရီ |
| B | P42 | [`B-downloads.md`](B-downloads.md) | Downloads ⋮ → **Delete file** (မေးပြီး ဖိုင်ကိုယ်တိုင်ဖျက်၊ Library/Files ကပါ ပျောက်)၊ "Remove from list" ဆက်ရှိ | Easy–Medium | 3–5 နာရီ |
| C | P43 | [`C-ads.md`](C-ads.md) | "Page ရဲ့ video လား" စည်းမျဉ်း တစ်ခုတည်း (page ပြောတဲ့ length နဲ့ ကိုက်မှ)၊ length မသိရင် အရင်တိုင်း၊ resolve ပြီး ထပ်စစ်၊ ad host/VAST ပိုသိ — ကြော်ငြာ ဘယ်တော့မှ main မဖြစ် | Medium | 5–7 နာရီ |
| B | P44 | [`M-merge-preview7.md`](M-merge-preview7.md) | B → C → A ပေါင်း၊ full validation၊ **Preview #7** | Medium | 2–3 နာရီ |
| — | P8 | [`P8-signed-beta4.md`](P8-signed-beta4.md) | Signed `1.0.0-beta.4` (Preview #7 + owner OK) | Easy | 1–2 နာရီ |

## Owner ဆုံးဖြတ်ရန် ([`FIX_ADD_PLAN.md` §3](../FIX_ADD_PLAN.md#3-owner-decisions))

- **G1 — Agent အရေအတွက်:** default ၃ ယောက်၊ ၂ ယောက်လည်း ရ (အပေါ်မှာ ကြည့်ပါ)။
- **G2 — TikTok agent:** ဖုန်း/desktop Chrome အဖြစ် မေး ("YFT" စာလုံး မပါ) (default `CHROME`)။
- **G3 — Home မှာ TikTok cookie:** YFT browser မှာ ရှိပြီးသား TikTok cookie ကို Home lookup နဲ့ hidden page
  မှာ သုံး (default `ON`၊ log မလုပ်)။
- **G4 — Watermark:** watermark ပါတဲ့ file ကို ဖျောက်၊ တခြား file မရမှ "With TikTok watermark" နဲ့ ပြ
  (default `HIDE`)။
- **G5 — Hidden TikTok page:** page ဖတ်မရရင် နောက်ကွယ်မှာ TikTok page ဖွင့်ပြီး TikTok ရဲ့ data ယူ
  (YouTube BotGuard လို) (default `ON`)။
- **G6 — YouTube fast start:** default `ON`။
- **G7 — Delete မေး:** "Delete this file?" (default `ON`)။
- **G8 — ကြော်ငြာ စည်းမျဉ်း:** တင်းကျပ် (default `STRICT`) — page ရဲ့ video သေချာမှ ပြ၊ မသေချာရင်
  "Reload page and try again"။

## English summary

Phase 15 runs three agents at the same time, each with one prompt: `A-tiktok.md` (P39: the
TikTok adapter reads every page shape, tries phone and desktop Chrome agents, checks that each
quality's file answers, hides the watermarked file and explains every failure in Details; the
browser offers the player's own file instead of a dead end; P40: TikTok's data from TikTok's own
page — the tab's data, TikTok's API answers through a document-start script, a hidden TikTok
page for Home's links — like YouTube's BotGuard token), `B-downloads.md` (P41: YouTube % and
speed within seconds; P42: Delete file in Downloads) and `C-ads.md` (P43: the sheet's main video
is always the page's own video, never the pre-roll ad). Each agent works on its own branch from
`work/phase-15-integration` in its own folder, changes only its own files
(`docs/FIX_ADD_PLAN.md` §0.7) and writes only its own sections of the shared docs. When all three
report `READY FOR MERGE`, Agent B runs `M-merge-preview7.md` (P44: merge B → C → A, full
validation, Preview #7). `P8-signed-beta4.md` releases the signed `1.0.0-beta.4` only after the
owner's OK. Docs-only pushes start no CI. Phase 14's prompts are in Git history
(`git show a9eea7b:docs/prompts/`).
