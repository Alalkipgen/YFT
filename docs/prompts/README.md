# YFT Prompts — Phase 12 (Preview #3 field fixes: saving, every quality, one sheet)

Agent တွေကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (owner မြင်ခဲ့တာ၊ အကြောင်းရင်း R1–R6၊
task အဆင့်တွေ၊ agent တစ်ယောက်ချင်းရဲ့ ဖိုင်တွေ၊ status board) က [`docs/FIX_ADD_PLAN.md`](../FIX_ADD_PLAN.md)
မှာ ရှိပါတယ်။

## သုံးနည်း (agent ၃ ယောက် တပြိုင်နက်)

1. Chat အသစ် ၃ ခု ဖွင့်ပြီး တစ်ခုစီထဲ prompt တစ်ခုစီရဲ့ code block ကို paste လုပ်ပါ:
   - **Agent A** → [`A-download-fix.md`](A-download-fix.md) — video download သိမ်းမရတာ ပြင် (P20)၊ Retry + Details (P21)
   - **Agent B** → [`B-site-qualities.md`](B-site-qualities.md) — YouTube quality အားလုံး (P22)၊ Facebook 720p + Audio (P23)
   - **Agent C** → [`C-generic-and-sheet.md`](C-generic-and-sheet.md) — တခြား site တွေမှာ video အမှန် (P24)၊ sheet တစ်မျိုးတည်း (P25)
2. Agent တစ်ယောက်ချင်းစီ ကိုယ့် branch ပေါ်မှာ ကိုယ့်ဖိုင်တွေကိုပဲ ပြင်လို့ တစ်ယောက်နဲ့တစ်ယောက် မထိပါဘူး
   ([`FIX_ADD_PLAN.md` §0.7](../FIX_ADD_PLAN.md#07-three-agents-in-parallel))။ Task တစ်ခုပြီးတိုင်း
   checkpoint push လုပ်ပြီး မြန်မာလို အတိုချုပ် report ပေးပါမယ်။ နောက်ဆုံး task ပြီးရင် `READY FOR MERGE`
   လို့ပြောပြီး ရပ်ပါမယ်။
3. သုံးယောက်လုံး `READY FOR MERGE` ဖြစ်ရင် Agent A ရဲ့ chat ထဲ [`M-merge-preview4.md`](M-merge-preview4.md)
   ကို paste လုပ်ပါ — ပေါင်းပြီး full validation စစ်၊ **Preview #4** link ပို့ပါမယ်။
4. Preview #4 ကို ဖုန်းမှာ စမ်းပြီး OK ဆိုရင် [`P8-signed-beta4.md`](P8-signed-beta4.md) (signed beta.4)။
5. Agent တစ်ယောက်က တခြားသူ့ဖိုင်ကို ပြင်ဖို့လိုရင် "Hand-off to …" လို့ report ထဲ ပြောပါမယ်။ အဲဒါကို
   သက်ဆိုင်တဲ့ agent ရဲ့ chat ထဲ copy လုပ်ပေးပါ။

## Agent ဘယ်နှစ်ယောက် သုံးမလဲ (အကြံပြုချက်)

- **၃ ယောက် တပြိုင်နက် — အကြံပြုပါတယ်။** ပြဿနာ ၃ စုက module မတူတဲ့နေရာ ၃ ခုမှာ ရှိလို့ ဖိုင်ချင်း မထိပါဘူး
  (A = download/storage၊ B = YouTube/Facebook adapter၊ C = generic detection + sheet)။ Shared doc ၃ ခုမှာ
  agent တစ်ယောက်ချင်းအတွက် section ကြိုလုပ်ထားလို့ merge conflict မဖြစ်ပါဘူး။ တစ်ယောက်တည်းဆို ၃၅–၅၅
  နာရီ၊ ၃ ယောက်ဆို ၁၅–၂၅ နာရီ + merge ၃–၅ နာရီခန့်။
- **၂ ယောက်ဆိုလည်း ရပါတယ်:** Agent A က P20 → P21 ပြီးရင် `C-generic-and-sheet.md` ကို
  `BRANCH_OVERRIDE: work/phase-12-download-fix` နဲ့ ဆက်လုပ် (P24 → P25)၊ Agent B က P22 → P23။
- **၄ ယောက်ထက် မများပါနဲ့:** sheet နဲ့ detection ကို ခွဲရင် ဖိုင်တူတွေကို ပြိုင်ပြင်ရပါမယ်။
- CI က branch ၃ ခုစလုံးအတွက် run လို့ တစ်ခါတလေ တန်းစီစောင့်ရနိုင်ပါတယ်။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `ALLOW_PUSH` | `true` | ကိုယ့် work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | P8 မှာ owner OK ပေးမှ `main` merge၊ push နဲ့ tag |
| `ALLOW_RELEASE` | `false` | P8 မှာ owner OK ပေးမှ release key နဲ့ signed APK |
| `BRANCH_OVERRIDE` | `none` | Agent C prompt ကို Agent A ရဲ့ branch ပေါ်မှာ လုပ်စေချင်ရင် (agent ၂ ယောက် mode) |
| `OWNER ANSWERS` | `none` | ဥပမာ `STOP_AFTER=P20`၊ `SHEET_NAMES=SNAPTUBE` (row နာမည် "Fast"/"High quality")၊ `MAIN=OK`၊ `P8=OK` |

## ဖုန်းမှာ စမ်းဖို့ APK

- Push တစ်ခါတိုင်း CI က `yft-debug-apk` ထုတ်ပါတယ် (၁၄ ရက်)။ Code ပြောင်းတဲ့ push တိုင်း **Preview APK
  (test key)** run က `yft-preview-apk` ထုတ်ပါတယ် ("YFT Preview"၊ `com.alal.yft.preview`)။ Run တိုင်း test key
  အသစ်ဖြစ်လို့ preview အသစ်မထည့်ခင် အဟောင်းကို uninstall လုပ်ပါ။
- Agent တစ်ယောက်ချင်းရဲ့ preview ကို စောစောစမ်းလို့ရပေမယ့် အဓိကစမ်းရမှာက **Preview #4** (သုံးယောက်ပေါင်းပြီး
  P26 ရဲ့ preview) ပါ။ စစ်ရမယ့်စာရင်း: [`FIX_ADD_PLAN.md` §6](../FIX_ADD_PLAN.md#6-owner-phone-checklist)။
- တစ်ခုခု မအောင်မြင်ရင် sheet ရဲ့ **Details** (lookup) သို့ download ရဲ့ **Details** (P21 ပြီးရင်) screenshot ကို
  သက်ဆိုင်တဲ့ agent chat ထဲ ထည့်ပေးပါ။

## Task ဇယား

| Agent | ID | Prompt | အလုပ် | Level | AI agent အချိန် |
| --- | --- | --- | --- | --- | --- |
| A | P20 | [`A-download-fix.md`](A-download-fix.md) | Video download "Storage unavailable" နဲ့ မပျက်တော့ (MediaStore အစဉ်ပြင် + emulator test) | Medium | 3–5 နာရီ |
| A | P21 | [`A-download-fix.md`](A-download-fix.md) | Retry က တကယ်ပြန်စ၊ ပျက်ရင် အကြောင်းရင်းမှန် + Details + Copy | Medium | 4–6 နာရီ |
| B | P22 | [`B-site-qualities.md`](B-site-qualities.md) | YouTube: 144p–1080p၊ 2K/4K size အပြည့် (visionOS + visitor data၊ 360p နဲ့ မရပ်) | Hard | 8–12 နာရီ |
| B | P23 | [`B-site-qualities.md`](B-site-qualities.md) | Facebook: Home နဲ့ browser မှာ 720p/360p + Audio M4A တူတူ | Medium–Hard | 5–8 နာရီ |
| C | P24 | [`C-generic-and-sheet.md`](C-generic-and-sheet.md) | တခြား site: preview မဟုတ်ဘဲ main video၊ count အမှန်၊ error အမှန် | Hard | 6–10 နာရီ |
| C | P25 | [`C-generic-and-sheet.md`](C-generic-and-sheet.md) | Site တိုင်း sheet တစ်မျိုး (Audio/Video၊ size၊ ရှင်းလင်းချက်) | Medium–Hard | 5–8 နာရီ |
| A | P26 | [`M-merge-preview4.md`](M-merge-preview4.md) | A → B → C ပေါင်း၊ full validation၊ **Preview #4** | Medium | 3–5 နာရီ |
| — | P8 | [`P8-signed-beta4.md`](P8-signed-beta4.md) | Signed `1.0.0-beta.4` (Preview #4 + owner OK) | Easy | 1–2 နာရီ |

## Owner ဆုံးဖြတ်ရန်

- **E13 — Sheet row နာမည်:** agent default က "720p · HD"၊ "M4A"၊ "MP3 · 128 kbps" + အောက်မှာ Snaptube လို
  ရှင်းလင်းချက်တစ်ကြောင်း။ Snaptube လို "Fast"/"High quality"/"Classic MP3" ခေါင်းစဉ်လိုချင်ရင်
  `SHEET_NAMES=SNAPTUBE`။
- **E12 — Agent အရေအတွက်:** default ၃ ယောက်၊ ၂ ယောက်လည်း ရ (အပေါ်မှာ ကြည့်ပါ)။
- Backlog B1–B4 အကြံပြုချက်: [`FIX_ADD_PLAN.md` §7](../FIX_ADD_PLAN.md#7-backlog)။

## English summary

Phase 12 runs three agents at the same time, each with one prompt that covers its two tasks:
`A-download-fix.md` (P20 saving, P21 Retry and failure details), `B-site-qualities.md` (P22
YouTube, P23 Facebook) and `C-generic-and-sheet.md` (P24 other sites' main video, P25 one sheet
everywhere). Each agent works on its own branch from `work/phase-12-integration`, changes only
the files `docs/FIX_ADD_PLAN.md` §0.7 gives it, writes only its own sections of the shared docs,
and stops at `READY FOR MERGE`. `M-merge-preview4.md` (P26) merges A → B → C, runs the full
validation and sends Preview #4; `P8-signed-beta4.md` signs `1.0.0-beta.4` after the owner's OK.
Defaults: `ALLOW_PUSH=true`, `ALLOW_MERGE_MAIN=false`, `ALLOW_RELEASE=false`. The Phase 11
prompts (`00_NEXT_TASK.md`, `MASTER_PROMPT.md`, P9–P19) were removed after the owner's test of
Preview #3; they remain in Git history (`git show 4db6c2b:docs/prompts/`). Older prompts:
`git show ce3cd25:docs/prompts/` (Phase 11 part 1) and `git show 2f6284f:docs/prompts/`
(Phases 8–10).
