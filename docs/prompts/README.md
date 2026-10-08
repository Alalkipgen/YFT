# YFT Prompts — Phase 14 (Preview #5 field fixes: background, faster merge, TikTok, fresh links)

Agent တွေကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (owner မြင်ခဲ့တာ၊ အကြောင်းရင်း R16–R24၊
task အဆင့်တွေ၊ agent တစ်ယောက်ချင်းရဲ့ ဖိုင်တွေ၊ status board) က [`docs/FIX_ADD_PLAN.md`](../FIX_ADD_PLAN.md)
မှာ ရှိပါတယ်။ Phase 13 ရဲ့ prompt အဟောင်းတွေ (A-merge-speed၊ B-generic-main၊ C-browser၊ M-merge-preview5)
ကို ဖျက်ထားပါတယ် — လိုရင် `git show 5a5bddb:docs/prompts/` နဲ့ ပြန်ကြည့်လို့ရပါတယ်။

## သုံးနည်း (agent ၃ ယောက် တပြိုင်နက်)

1. Chat အသစ် ၃ ခု ဖွင့်ပြီး တစ်ခုစီထဲ prompt တစ်ခုစီရဲ့ code block ကို paste လုပ်ပါ:
   - **Agent A** → [`A-background.md`](A-background.md) — download နဲ့ **merge** ကို တခြား app သုံးနေချိန်/screen
     ပိတ်ချိန်မှာလည်း ဆက်လုပ်၊ notification မှာ % · speed · ကျန်ချိန် (P34)
   - **Agent B** → [`B-tiktok-fresh-links.md`](B-tiktok-fresh-links.md) — TikTok For You မှာ Download ရ (P36)၊
     တခြား site မှာ HTTP 410 အစား link အသစ် ကိုယ်တိုင်ရှာ (P37)
   - **Agent C** → [`C-fast-merge.md`](C-fast-merge.md) — ၁ နာရီ video merge ကို ၂ မိနစ် → ၁၀–၂၀ စက္ကန့်ခန့် (P35)
2. Agent တစ်ယောက်ချင်းစီ ကိုယ့် branch နဲ့ ကိုယ့် folder (`/data/YFT-A`, `-B`, `-C`) ပေါ်မှာ ကိုယ့်ဖိုင်တွေကိုပဲ
   ပြင်လို့ တစ်ယောက်နဲ့တစ်ယောက် မထိပါဘူး ([`FIX_ADD_PLAN.md` §0.7](../FIX_ADD_PLAN.md#07-three-agents-in-parallel))။
   Task တစ်ခုပြီးတိုင်း checkpoint push လုပ်ပြီး မြန်မာလို အတိုချုပ် report (Level ပါ) ပေးပါမယ်။ နောက်ဆုံး task
   ပြီးရင် `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။
3. **SSH key:** computer မှာ key မရှိရင် agent က key **အသစ်** လုပ်ပြီး (အဟောင်း မရှာပါ) public key တစ်ကြောင်း
   ပြပါမယ်။ GitHub › YFT › Settings › Deploy keys › Add deploy key မှာ ထည့်၊ "Allow write access" ကို
   အမှန်ခြစ်ပြီး "SSH Done" လို့ ပြောပါ။ Agent ၃ ယောက် computer တစ်ခုတည်းဆို key တစ်ခုတည်း သုံးပါတယ်။
4. သုံးယောက်လုံး `READY FOR MERGE` ဖြစ်ရင် Agent A ရဲ့ chat ထဲ [`M-merge-preview6.md`](M-merge-preview6.md)
   ကို paste လုပ်ပါ — ပေါင်းပြီး full validation စစ်၊ **Preview #6** link ပို့ပါမယ်။
5. Preview #6 ကို ဖုန်းမှာ စမ်းပြီး OK ဆိုရင် [`P8-signed-beta4.md`](P8-signed-beta4.md) (signed beta.4)။
6. Agent တစ်ယောက်က တခြားသူ့ဖိုင်ကို ပြင်ဖို့လိုရင် "Hand-off to …" လို့ report ထဲ ပြောပါမယ်။ အဲဒါကို
   သက်ဆိုင်တဲ့ agent ရဲ့ chat ထဲ copy လုပ်ပေးပါ။

## Agent ဘယ်နှစ်ယောက် သုံးမလဲ (အကြံပြုချက်)

- **၃ ယောက် တပြိုင်နက် — အကြံပြုပါတယ် (အမြန်ဆုံး)။** A = app ရဲ့ download service/notification/Downloads
  screen၊ B = TikTok + browser/detection၊ C = `core-download` ထဲက merge engine — ဖိုင်ချင်း မထိပါဘူး။ ကြာချိန်
  ၉–၁၄ နာရီခန့် (B ၇–၁၁ နာရီ၊ C ၆–၁၀ နာရီ က အရှည်ဆုံး + merge ၂–၃ နာရီ)။
- **၂ ယောက်ဆိုလည်း ရပါတယ်:** Agent A က P34 ပြီးရင် `C-fast-merge.md` ကို
  `BRANCH_OVERRIDE: work/phase-14-background` နဲ့ ဆက်လုပ် (P35)၊ Agent B က P36 → P37။ ကြာချိန် ၁၃–၂၀ နာရီခန့်။
- **၄ ယောက်ထက် မများပါနဲ့:** ပိုခွဲရင် ဖိုင်တူတွေကို ပြိုင်ပြင်ရပါမယ်။
- CI က branch ၃ ခုစလုံးအတွက် run လို့ တစ်ခါတလေ တန်းစီစောင့်ရနိုင်ပါတယ်။ **Docs ပဲပြောင်းတဲ့ push တွေက CI
  မ run တော့ပါ** (Phase 14 ကစပြီး)၊ code ပြောင်းတဲ့ push တွေပဲ run ပါတယ်။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `ALLOW_PUSH` | `true` | ကိုယ့် work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | P8 မှာ owner OK ပေးမှ `main` merge၊ push နဲ့ tag |
| `ALLOW_RELEASE` | `false` | P8 မှာ owner OK ပေးမှ release key နဲ့ signed APK |
| `BRANCH_OVERRIDE` | `none` | Agent C prompt ကို Agent A ရဲ့ branch ပေါ်မှာ လုပ်စေချင်ရင် (agent ၂ ယောက် mode) |
| `OWNER ANSWERS` | `none` | ဥပမာ `FAST_MERGE=OFF` (C: stream copy မလုပ်)၊ `BATTERY_CARD=OFF`၊ `DONE_NOTICE=OFF` (A)၊ `TIKTOK_QUALITIES=PAGE`၊ `REREAD=0`၊ `STOP_AFTER=P36` (B)၊ `MAIN=OK`၊ `P8=OK` |

## ဖုန်းမှာ စမ်းဖို့ APK

- Code ပြောင်းတဲ့ push တိုင်း **Preview APK (test key)** run က `yft-preview-apk` ထုတ်ပါတယ် ("YFT Preview"၊
  `com.alal.yft.preview`)။ Run တိုင်း test key အသစ်ဖြစ်လို့ preview အသစ်မထည့်ခင် အဟောင်းကို uninstall လုပ်ပါ။
- Agent တစ်ယောက်ချင်းရဲ့ preview ကို စောစောစမ်းလို့ရပေမယ့် အဓိကစမ်းရမှာက **Preview #6** (သုံးယောက်ပေါင်းပြီး
  P38 ရဲ့ preview) ပါ။ စစ်ရမယ့်စာရင်း: [`FIX_ADD_PLAN.md` §6](../FIX_ADD_PLAN.md#6-owner-phone-checklist)။
- တစ်ခုခု မအောင်မြင်ရင် sheet ရဲ့ **Details** (lookup) သို့ download ရဲ့ **Details** (saving) screenshot ကို
  သက်ဆိုင်တဲ့ agent chat ထဲ ထည့်ပေးပါ။

## Task ဇယား

| Agent | ID | Prompt | အလုပ် | Level | AI agent အချိန် |
| --- | --- | --- | --- | --- | --- |
| A | P34 | [`A-background.md`](A-background.md) | Download၊ merge၊ MP3၊ save ကို background/screen ပိတ်ချိန်မှာလည်း ဆက်လုပ် (wake lock၊ Android 15 media processing၊ ဖုန်းက ရပ်ထားရင် သိ + Xiaomi လုပ်နည်း card)၊ notification "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left"၊ ပြီးရင် "Downloaded" | Medium | 5–7 နာရီ |
| B | P36 | [`B-tiktok-fresh-links.md`](B-tiktok-fresh-links.md) | TikTok For You/profile/video page မှာ Download → quality နဲ့ sheet (video id ကို feed card ကနေ၊ ဖုန်း page data၊ cookie) | Medium | 3–5 နာရီ |
| B | P37 | [`B-tiktok-fresh-links.md`](B-tiktok-fresh-links.md) | 410 ဆိုရင် player link အသစ်/page ပြန်ဖတ်ပြီး ကိုယ်တိုင် ပြင်၊ Try again တကယ်ပြန်ဖတ်၊ "Reload page and try again"၊ Details မှာ link အကြောင်း | Medium | 4–6 နာရီ |
| C | P35 | [`C-fast-merge.md`](C-fast-merge.md) | MP4/M4A merge ကို stream copy နဲ့ (၁ နာရီ 720p: ~၂ မိနစ် → ~၁၀–၂၀ စက္ကန့်)၊ စစ်ဆေးပြီး မရရင် အခုနည်း | Hard | 6–10 နာရီ |
| A | P38 | [`M-merge-preview6.md`](M-merge-preview6.md) | A → B → C ပေါင်း၊ full validation၊ **Preview #6** | Medium | 2–3 နာရီ |
| — | P8 | [`P8-signed-beta4.md`](P8-signed-beta4.md) | Signed `1.0.0-beta.4` (Preview #6 + owner OK) | Easy | 1–2 နာရီ |

## Owner ဆုံးဖြတ်ရန် ([`FIX_ADD_PLAN.md` §3](../FIX_ADD_PLAN.md#3-owner-decisions))

- **G1 — Agent အရေအတွက်:** default ၃ ယောက်၊ ၂ ယောက်လည်း ရ (အပေါ်မှာ ကြည့်ပါ)။
- **G2 — Merge မြန်အောင်:** default stream copy (`FAST_MERGE=ON`)၊ စစ်ဆေးပြီး မရရင် အခုနည်းနဲ့ ပြန်လုပ်။
- **G3–G5 — Background:** wake lock ဖွင့်၊ battery card (Xiaomi လုပ်နည်းပါ) ဖွင့်၊ ပြီးရင် "Downloaded"
  notification ဖွင့် — အကုန် default on။
- **G6 — TikTok quality:** desktop page ကနေ quality စာရင်း ယူ (default)။
- **G7 — Link အသစ်:** page ကို နောက်ကွယ်မှာ ၂ ကြိမ်အထိ ပြန်ဖတ် (default)။
- **G8 — Speed:** 1,024 KB/s အောက် KB/s၊ 1 MB/s ကနေ MB/s (owner ပြောတဲ့အတိုင်း)။

## English summary

Phase 14 runs three agents at the same time, each with one prompt: `A-background.md` (P34:
downloads, merges, MP3 conversions and saves keep going in the background, with %, speed and
time left in the notification and a fix for phones that freeze YFT), `B-tiktok-fresh-links.md`
(P36 TikTok Download on the For You feed and video pages, P37 fresh links instead of HTTP 410 on
other sites) and `C-fast-merge.md` (P35 a stream-copy merge for long videos). Each agent works on
its own branch from `work/phase-14-integration` in its own folder, changes only its own files
(`docs/FIX_ADD_PLAN.md` §0.7) and writes only its own sections of the shared docs. When all three
report `READY FOR MERGE`, Agent A runs `M-merge-preview6.md` (P38: merge A → B → C, full
validation, Preview #6). `P8-signed-beta4.md` releases the signed `1.0.0-beta.4` only after the
owner's OK. Docs-only pushes no longer start CI. Phase 13's prompts are in Git history
(`git show 5a5bddb:docs/prompts/`).
