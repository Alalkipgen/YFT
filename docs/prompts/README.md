# YFT Prompts — Phase 13 (Preview #4 polish: other sites' ads, YouTube merge, browser)

Agent တွေကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (owner မြင်ခဲ့တာ၊ အကြောင်းရင်း R7–R15၊
task အဆင့်တွေ၊ agent တစ်ယောက်ချင်းရဲ့ ဖိုင်တွေ၊ status board) က [`docs/FIX_ADD_PLAN.md`](../FIX_ADD_PLAN.md)
မှာ ရှိပါတယ်။ Phase 12 ရဲ့ prompt အဟောင်းတွေ (A/B/C/M) ကို ဖျက်ထားပါတယ် — လိုရင်
`git show bc806f9:docs/prompts/` နဲ့ ပြန်ကြည့်လို့ရပါတယ်။

## သုံးနည်း (agent ၃ ယောက် တပြိုင်နက်)

1. Chat အသစ် ၃ ခု ဖွင့်ပြီး တစ်ခုစီထဲ prompt တစ်ခုစီရဲ့ code block ကို paste လုပ်ပါ:
   - **Agent A** → [`A-merge-speed.md`](A-merge-speed.md) — YouTube 99% မှာ ကြာကြာ မရပ်တော့ (P27)
   - **Agent B** → [`B-generic-main.md`](B-generic-main.md) — တခြား site မှာ ကြော်ငြာ မဟုတ်ဘဲ video အမှန် (P28)၊ ဖိုင်ပျက်ရင် နောက် video (P29)
   - **Agent C** → [`C-browser.md`](C-browser.md) — Google search (P30)၊ History (P31)၊ pop-up / ad redirect ပိတ် (P32)
2. Agent တစ်ယောက်ချင်းစီ ကိုယ့် branch နဲ့ ကိုယ့် folder (`/data/YFT-A`, `-B`, `-C`) ပေါ်မှာ ကိုယ့်ဖိုင်တွေကိုပဲ
   ပြင်လို့ တစ်ယောက်နဲ့တစ်ယောက် မထိပါဘူး ([`FIX_ADD_PLAN.md` §0.7](../FIX_ADD_PLAN.md#07-three-agents-in-parallel))။
   Task တစ်ခုပြီးတိုင်း checkpoint push လုပ်ပြီး မြန်မာလို အတိုချုပ် report ပေးပါမယ်။ နောက်ဆုံး task ပြီးရင်
   `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။
3. **SSH key:** computer မှာ key မရှိရင် agent က key **အသစ်** လုပ်ပြီး (အဟောင်း မရှာပါ) public key တစ်ကြောင်း
   ပြပါမယ်။ GitHub › YFT › Settings › Deploy keys › Add deploy key မှာ ထည့်၊ "Allow write access" ကို
   အမှန်ခြစ်ပြီး "SSH Done" လို့ ပြောပါ။ Agent ၃ ယောက် computer တစ်ခုတည်းဆို key တစ်ခုတည်း သုံးပါတယ်။
4. သုံးယောက်လုံး `READY FOR MERGE` ဖြစ်ရင် Agent A ရဲ့ chat ထဲ [`M-merge-preview5.md`](M-merge-preview5.md)
   ကို paste လုပ်ပါ — ပေါင်းပြီး full validation စစ်၊ **Preview #5** link ပို့ပါမယ်။
5. Preview #5 ကို ဖုန်းမှာ စမ်းပြီး OK ဆိုရင် [`P8-signed-beta4.md`](P8-signed-beta4.md) (signed beta.4)။
6. Agent တစ်ယောက်က တခြားသူ့ဖိုင်ကို ပြင်ဖို့လိုရင် "Hand-off to …" လို့ report ထဲ ပြောပါမယ်။ အဲဒါကို
   သက်ဆိုင်တဲ့ agent ရဲ့ chat ထဲ copy လုပ်ပေးပါ။

## Agent ဘယ်နှစ်ယောက် သုံးမလဲ (အကြံပြုချက်)

- **၃ ယောက် တပြိုင်နက် — အကြံပြုပါတယ်။** ပြဿနာ ၃ ခုက module မတူတဲ့နေရာ ၃ ခုမှာ ရှိလို့ ဖိုင်ချင်း မထိပါဘူး
  (A = download merge/save၊ B = generic detection + sheet ရဲ့ video ရွေးချယ်မှု၊ C = browser WebView/settings/
  Room)။ Shared doc ၃ ခုမှာ agent တစ်ယောက်ချင်းအတွက် section ကြိုလုပ်ထားလို့ merge conflict မဖြစ်ပါဘူး။
  တစ်ယောက်တည်းဆို ၂၀–၃၃ နာရီ၊ ၃ ယောက်ဆို ၁၀–၁၄ နာရီ + merge ၂–၃ နာရီခန့်။
- **၂ ယောက်ဆိုလည်း ရပါတယ်:** Agent A က P27 ပြီးရင် `C-browser.md` ကို
  `BRANCH_OVERRIDE: work/phase-13-merge-speed` နဲ့ ဆက်လုပ် (P30 → P31 → P32)၊ Agent B က P28 → P29။
  ကြာချိန် ၁၂–၁၉ နာရီခန့်။
- **၄ ယောက်ထက် မများပါနဲ့:** browser ဖိုင်တွေ (B ရဲ့ `BrowserViewModel` နဲ့ C ရဲ့ `BrowserScreen`) ကို ခွဲရင်
  ပြိုင်ပြင်ရပါမယ်။
- CI က branch ၃ ခုစလုံးအတွက် run လို့ တစ်ခါတလေ တန်းစီစောင့်ရနိုင်ပါတယ်။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `ALLOW_PUSH` | `true` | ကိုယ့် work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | P8 မှာ owner OK ပေးမှ `main` merge၊ push နဲ့ tag |
| `ALLOW_RELEASE` | `false` | P8 မှာ owner OK ပေးမှ release key နဲ့ signed APK |
| `BRANCH_OVERRIDE` | `none` | Agent C prompt ကို Agent A ရဲ့ branch ပေါ်မှာ လုပ်စေချင်ရင် (agent ၂ ယောက် mode) |
| `OWNER ANSWERS` | `none` | ဥပမာ `GENERIC=B` (B: ကြော်ငြာပြီးတဲ့အထိ စောင့်)၊ `SEARCH=DUCKDUCKGO`၊ `HISTORY=OFF`၊ `POPUPS=ALLOW`၊ `DIRECT_MUX=NO` (A)၊ `STOP_AFTER=P28`၊ `MAIN=OK`၊ `P8=OK` |

## ဖုန်းမှာ စမ်းဖို့ APK

- Code ပြောင်းတဲ့ push တိုင်း **Preview APK (test key)** run က `yft-preview-apk` ထုတ်ပါတယ် ("YFT Preview"၊
  `com.alal.yft.preview`)။ Run တိုင်း test key အသစ်ဖြစ်လို့ preview အသစ်မထည့်ခင် အဟောင်းကို uninstall လုပ်ပါ။
- Agent တစ်ယောက်ချင်းရဲ့ preview ကို စောစောစမ်းလို့ရပေမယ့် အဓိကစမ်းရမှာက **Preview #5** (သုံးယောက်ပေါင်းပြီး
  P33 ရဲ့ preview) ပါ။ စစ်ရမယ့်စာရင်း: [`FIX_ADD_PLAN.md` §6](../FIX_ADD_PLAN.md#6-owner-phone-checklist)။
- တစ်ခုခု မအောင်မြင်ရင် sheet ရဲ့ **Details** (lookup) သို့ download ရဲ့ **Details** (saving) screenshot ကို
  သက်ဆိုင်တဲ့ agent chat ထဲ ထည့်ပေးပါ။

## Task ဇယား

| Agent | ID | Prompt | အလုပ် | Level | AI agent အချိန် |
| --- | --- | --- | --- | --- | --- |
| A | P27 | [`A-merge-speed.md`](A-merge-speed.md) | YouTube ကြာကြာ video: "Merging · N%" / "Saving · N%" ပြ၊ Download/YFT ထဲ တိုက်ရိုက် တစ်ခါပဲ ရေး | Medium | 3–5 နာရီ |
| B | P28 | [`B-generic-main.md`](B-generic-main.md) | တခြား site: ကြော်ငြာ ပြနေတုန်းလည်း page ရဲ့ video (title၊ ပုံ၊ အရှည်၊ 480p/720p)၊ ကြော်ငြာ server စာရင်း၊ ၆ စက္ကန့် စောင့် | Hard | 6–10 နာရီ |
| B | P29 | [`B-generic-main.md`](B-generic-main.md) | ဖိုင်ပျက် (410/404/403) ရင် နောက် video ကို ကိုယ်တိုင်ပြ၊ Try again က လိပ်စာအသစ် | Medium | 2–4 နာရီ |
| C | P30 | [`C-browser.md`](C-browser.md) | Google search default၊ Settings › Browser › Search engine | Easy | 1–2 နာရီ |
| C | P31 | [`C-browser.md`](C-browser.md) | Browser History (စာရင်း၊ ရှာ၊ ဖျက်၊ Recent၊ ဖွင့်/ပိတ် switch) | Medium | 3–5 နာရီ |
| C | P32 | [`C-browser.md`](C-browser.md) | Pop-up နဲ့ ad redirect ပိတ် ("Pop-up blocked · Open")၊ ပုံမှန် link ရဆဲ | Medium–Hard | 4–7 နာရီ |
| A | P33 | [`M-merge-preview5.md`](M-merge-preview5.md) | A → B → C ပေါင်း၊ full validation၊ **Preview #5** | Medium | 2–3 နာရီ |
| — | P8 | [`P8-signed-beta4.md`](P8-signed-beta4.md) | Signed `1.0.0-beta.4` (Preview #5 + owner OK) | Easy | 1–2 နာရီ |

## Owner ဆုံးဖြတ်ရန် ([`FIX_ADD_PLAN.md` §3](../FIX_ADD_PLAN.md#3-owner-decisions))

- **F1 — တခြား site မှာ ကြော်ငြာ အစား video အမှန် ရှာနည်း:** default **A+** (`GENERIC=A`) — page က ပြောတဲ့
  အရှည်/title/ပုံ ကို ယုံ၊ player setup ဖတ်၊ ကြော်ငြာ server စာရင်း၊ ၆ စက္ကန့်အထိ စောင့်၊ ပျက်ရင် နောက် video။
  **B** (`GENERIC=B`) — ကြော်ငြာ ပြီးတဲ့အထိ စောင့် (နှေး၊ ကြော်ငြာ ပြောင်းရင် ပျက်)။ **C** — Snaptube လို site
  နာမည်နဲ့ adapter (support scope အပြင်၊ ရက်အတော်ကြာ + ထိန်းရ) — Preview #5 ပြီးမှ လိုရင် ပြောပါ။
- **F2–F4 — Browser:** Google default၊ History ဖွင့် (၉၀ ရက်၊ page ၅၀၀၀)၊ pop-up / redirect ပိတ် —
  Settings › Browser မှာ ပြောင်းလို့ရ။
- **F5 — YouTube merge:** Download/YFT ထဲ တိုက်ရိုက် ရေး (Android 8.0+)၊ default yes။
- **F6 — Agent အရေအတွက်:** default ၃ ယောက်၊ ၂ ယောက်လည်း ရ (အပေါ်မှာ ကြည့်ပါ)။

## English summary

Phase 13 runs three agents at the same time, each with one prompt that covers its tasks:
`A-merge-speed.md` (P27 YouTube merge without a silent 99%), `B-generic-main.md` (P28 the page's
video instead of a pre-roll ad, P29 the next video when one fails) and `C-browser.md` (P30
Google search, P31 browser history, P32 pop-up and ad-redirect blocking). Each agent works on
its own branch from `work/phase-13-integration` in its own folder, changes only its own files
(`docs/FIX_ADD_PLAN.md` §0.7) and writes only its own sections of the shared docs. When all three
report `READY FOR MERGE`, Agent A runs `M-merge-preview5.md` (P33: merge A → B → C, full
validation, Preview #5). `P8-signed-beta4.md` releases the signed `1.0.0-beta.4` only after the
owner's OK. Phase 12's prompts are in Git history (`git show bc806f9:docs/prompts/`).
