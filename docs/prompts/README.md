# YFT Prompts — Phase 11 part 2 (fast, stable, one sheet)

Agent တစ်ယောက်ကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (owner မြင်ခဲ့တာ၊ အကြောင်းရင်း၊ အဆင့်တွေ၊
status board) က [`docs/FIX_ADD_PLAN.md`](../FIX_ADD_PLAN.md) မှာ ရှိပါတယ်။

## သုံးနည်း

0. **Agent တိုင်းအတွက် generic:** [`MASTER_PROMPT.md`](MASTER_PROMPT.md) (`TASK: auto` သို့ `TASK: P9`)။
   Machine အသစ်မှာ JDK 17 / Android SDK 35 / NDK / CMake ထည့်နည်းလည်း ပါပါတယ်။
1. **အလွယ်ဆုံး:** [`00_NEXT_TASK.md`](00_NEXT_TASK.md) — agent က status board ကိုကြည့်ပြီး နောက် task ကို
   ရွေးကာ owner အစဉ်အတိုင်း ဆက်လုပ်ပါမယ်။ P19 ပြီးရင် Preview #2 link ပေးပြီး P14 ကို ဆက်လုပ်၊ P18
   ပြီးရင် Preview #3 link ပေးပြီး owner စမ်းတာကို စောင့်ပါမယ်။
2. **Task တစ်ခုကို တိတိကျကျ:** အောက်ဇယားထဲက task ဖိုင်ကိုဖွင့်ပြီး code block ကို paste လုပ်ပါ။
3. Task အားလုံး `work/phase-11-download-flow` branch ပေါ်မှာ checkpoint commit + push လုပ်ပါတယ်။
   `main` ကို P8 မတိုင်ခင် မထိပါဘူး။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `TASK` | `auto` | `auto` = နောက် task ကို ရွေးပြီး ဆက်လုပ်၊ သို့ `P9` လို task တစ်ခုတည်း |
| `ALLOW_PUSH` | `true` | Work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | P8 မှာ owner OK ပေးမှ `main` merge၊ push နဲ့ tag |
| `ALLOW_RELEASE` | `false` | P8 မှာ owner OK ပေးမှ release key နဲ့ signed APK |
| `OWNER ANSWERS` | `none` | ဥပမာ `STOP_AFTER=P19` (Preview #2 မှာ ရပ်)၊ `B1=YES`၊ `P8=OK` |

## ဖုန်းမှာ စမ်းဖို့ APK

- Push တစ်ခါတိုင်း CI က `yft-debug-apk` ထုတ်ပါတယ် (၁၄ ရက်)။ GitHub › Actions › run › Artifacts ›
  `yft-debug-apk` ကို download၊ unzip၊ install။ App ID `com.alal.yft.debug` ဖြစ်လို့ beta နဲ့ ဘေးချင်းယှဉ်
  install ဖြစ်ပါတယ်။
- Code ပြောင်းတဲ့ push တိုင်း **Preview APK (test key)** run က `yft-preview-apk` ထုတ်ပါတယ် (release
  build၊ "YFT Preview"၊ `com.alal.yft.preview`)။ Run တိုင်း test key အသစ်ဖြစ်လို့ preview အသစ်မထည့်ခင်
  အဟောင်းကို uninstall လုပ်ပါ။
- **Preview #2** = P19 ပြီးတဲ့ commit ရဲ့ preview၊ **Preview #3** = P18 ပြီးတဲ့ commit ရဲ့ preview။ Agent က
  link နဲ့ စစ်ရမယ့်စာရင်း ([`FIX_ADD_PLAN.md` §6](../FIX_ADD_PLAN.md#6-owner-phone-checklist)) ကို ပို့ပါမယ်။
- Release key နဲ့ signed APK ကို P8 ကပဲ ထုတ်ပါတယ်။

## Task ဇယား

| အစဉ် | ID | Prompt | အလုပ် | Level | AI agent အချိန် | လိုအပ်ချက် |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | P9 | [`P9-short-sheet.md`](P9-short-sheet.md) | Sheet တို: Audio ၂ + Video ၂ (720p default)၊ More formats၊ Download ခလုတ် အမြဲမြင် | Medium | 4–6 နာရီ | — |
| 2 | P10 | [`P10-slow-networks.md`](P10-slow-networks.md) | Line နှေးရင် data ဝင်နေသရွေ့ စောင့်၊ ၂ ကြိမ် ပြန်ကြိုးစား၊ Home 90 စက္ကန့် | Medium | 4–6 နာရီ | — |
| 3 | P11 | [`P11-rows-never-vanish.md`](P11-rows-never-vanish.md) | Quality row မပျောက်တော့ (size ကို နောက်မှ ဖြည့်) | Medium | 4–6 နာရီ | P9 |
| 4 | P12 | [`P12-one-lookup-per-page.md`](P12-one-lookup-per-page.md) | Site page မှာ sheet တစ်ခု၊ lookup တစ်ကြိမ် ("Found on this page 4" မပေါ်တော့) | Medium–Hard | 5–8 နာရီ | P9 |
| 5 | P13 | [`P13-wide-download-button.md`](P13-wide-download-button.md) | Video page မှာ အကျယ် Download ခလုတ် | Easy | 2–4 နာရီ | P12 |
| 6 | P19 | [`P19-thumbnails.md`](P19-thumbnails.md) | တကယ့် thumbnail ပုံ (sheet၊ Found list၊ Downloads) → **Preview #2** | Medium | 5–8 နာရီ | P9 |
| 7 | P14 | [`P14-youtube-visionos-first.md`](P14-youtube-visionos-first.md) | YouTube: visionOS ကို အရင်မေး (watch page မစောင့်) | Hard | 6–10 နာရီ | P10 |
| 8 | P15 | [`P15-facebook-public-first.md`](P15-facebook-public-first.md) | Facebook: public page အရင် (request ၁ ကြိမ်) | Medium–Hard | 4–7 နာရီ | P10, P11 |
| 9 | P16 | [`P16-instant-sheet.md`](P16-instant-sheet.md) | Sheet ချက်ချင်းပွင့်ပြီး quality ကို နောက်မှ ဖြည့် | Hard | 8–12 နာရီ | P9, P11, P12 |
| 10 | P17 | [`P17-reuse-lookups.md`](P17-reuse-lookups.md) | Lookup result ကို ပြန်သုံး (တူတဲ့ video ထပ်မရှာ) | Medium | 4–6 နာရီ | P12, P16 |
| 11 | P18 | [`P18-download-early.md`](P18-download-early.md) | Quality မပေါ်ခင် Download နှိပ်လို့ရ → **Preview #3** | Medium–Hard | 4–7 နာရီ | P16 |
| 12 | P8 | [`P8-signed-beta4.md`](P8-signed-beta4.md) | Signed `1.0.0-beta.4` | Easy | 1–2 နာရီ | P9–P19 + Preview #3 + owner OK |

P9–P19 စုစုပေါင်း AI agent အချိန် ၅၀–၈၀ နာရီခန့်။ အစဉ် (FIX_ADD_PLAN §3 E7): Group 1 (P9 → P10 → P11 →
P12 → P13 → P19) → Preview #2 → Group 2 (P14 → P15 → P16 → P17 → P18) → Preview #3 → owner စမ်း → P8။

## Owner ဆုံးဖြတ်ရန် (Backlog အကြံပြုချက်)

အသေးစိတ်: [`FIX_ADD_PLAN.md` §7](../FIX_ADD_PLAN.md#7-backlog)

- **B1 — YouTube page ထဲ video အောက်မှာ Download ခလုတ်ထည့်:** အခုမလုပ်သေး၊ P13 အကျယ်ခလုတ်ကို အရင်သုံး။
  Preview #2 စမ်းပြီးမှ ဆုံးဖြတ် (နောက်မှ add-on အဖြစ် ၄–၆ နာရီ + YouTube ပြောင်းတိုင်း ပြင်ရ)။
- **B2 — Snaptube လို YFT ကိုယ်ပိုင် YouTube page:** မလုပ်ဖို့ အကြံပြု (အပတ်ပေါင်းများစွာ၊ မကြာခဏ ပျက်)။
  P13 + P16 + P19 က Snaptube လို download အတွေ့အကြုံ ပေးပါတယ်။
- **B3 — Facebook format ကို browser page ကနေ ဖတ်:** လိုမှ လုပ်။ P15 ပြီး ဖုန်းမှာ တိုင်းကြည့်၊ ၅ စက္ကန့်ထက်
  ကြာနေသေးရင် spike (၆–၁၀ နာရီ)။
- **B4 — TikTok:** owner ဖုန်းမှာ စမ်းစရာ မလို (India မှာ ban)၊ fixture + CI emulator + sandbox live check နဲ့
  စစ်တယ်၊ VPN မသုံး။

## English summary

`00_NEXT_TASK.md` picks the next task from the status board in `docs/FIX_ADD_PLAN.md`; each
`Px-*.md` file holds one self-contained English prompt for one task (repository, branch, flags,
start, environment, work, tests, live check, validation, docs, checkpoint, short Burmese report,
next task). `MASTER_PROMPT.md` is the generic prompt for any agent, including the environment
setup. Defaults: `ALLOW_PUSH=true`, `ALLOW_MERGE_MAIN=false`, `ALLOW_RELEASE=false`; only P8
merges, tags and signs, after the owner's OK. The part 1 prompts (P1–P7, `CONTINUE-P2-TO-P6.md`,
`CONTINUE-P4-TO-P6.md`) were removed after the owner's test of Preview #1; they remain in Git
history (`git show ce3cd25:docs/prompts/`). The Phase 8–10 prompts: `git show 2f6284f:docs/prompts/`.
