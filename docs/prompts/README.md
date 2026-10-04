# YFT Prompts — Phase 11 (Snaptube-style download flow)

Agent တစ်ယောက်ကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (အကြောင်းရင်း၊ အဆင့်တွေ၊ status
board) က [`docs/FIX_ADD_PLAN.md`](../FIX_ADD_PLAN.md) မှာ ရှိပါတယ်။

## သုံးနည်း

0. **Agent တိုင်းအတွက် generic:** [`MASTER_PROMPT.md`](MASTER_PROMPT.md) (`TASK: auto` သို့ `TASK: P3`)။
1. **အလွယ်ဆုံး:** [`00_NEXT_TASK.md`](00_NEXT_TASK.md) — agent က status board ကိုကြည့်ပြီး နောက် task ကို
   ရွေးပြီး P7 အထိ မရပ်ဘဲ ဆက်လုပ်ပါမယ်။
2. **Task တစ်ခုကို တိတိကျကျ:** အောက်ဇယားထဲက task ဖိုင်ကိုဖွင့်ပြီး code block ကို paste လုပ်ပါ။
3. Task အားလုံး `work/phase-11-download-flow` branch ပေါ်မှာ checkpoint commit + push လုပ်ပါတယ်။
   `main` ကို P8 မတိုင်ခင် မထိပါဘူး။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `ALLOW_PUSH` | `true` | Work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | P8 မှာ owner OK ပေးမှ `main` merge၊ push နဲ့ tag |
| `ALLOW_RELEASE` | `false` | P8 မှာ owner OK ပေးမှ release key နဲ့ signed APK |
| `OWNER ANSWERS` | `none` | ဥပမာ `P8=OK` (preview APK စမ်းပြီး stable ဖြစ်ရင်) |

## ဖုန်းမှာ စမ်းဖို့ APK

- Push တစ်ခါတိုင်း CI က `yft-debug-apk` ထုတ်ပါတယ် (၁၄ ရက်)။ GitHub › Actions › run › Artifacts ›
  `yft-debug-apk` ကို download၊ unzip၊ install။ App ID `com.alal.yft.debug` ဖြစ်လို့ beta နဲ့ ဘေးချင်းယှဉ်
  install ဖြစ်ပါတယ်။
- P7 ကစပြီး `yft-preview-apk` (release build၊ test key၊ `com.alal.yft.preview`) ပါ ထုတ်ပါမယ်။
  Signature လုံးဝမပါတဲ့ APK ကို Android က install မပေးလို့ပါ။
- Release key နဲ့ signed APK ကို P8 ကပဲ ထုတ်ပါတယ်။

## Task ဇယား

| ID | Prompt | အလုပ် | Level | AI agent အချိန် | လိုအပ်ချက် |
| --- | --- | --- | --- | --- | --- |
| P0 | — | Plan၊ prompt နဲ့ docs (ပြီးပြီ, 2026-10-04) | Easy | 1–2 နာရီ | owner အတည်ပြုချက် |
| P1 | [`P1-browser-spa-navigation.md`](P1-browser-spa-navigation.md) | YouTube watch/shorts မှာ Download ခလုတ်၊ address bar မှန် | Medium | 3–5 နာရီ | — |
| P2 | [`P2-facebook-black-page.md`](P2-facebook-black-page.md) | Facebook/TikTok browser black screen ပြင် | Medium–Hard | 4–8 နာရီ | P1 |
| P3 | [`P3-one-download-sheet.md`](P3-one-download-sheet.md) | Snaptube ပုံစံ sheet တစ်ခုတည်း | Hard | 10–14 နာရီ | P1 |
| P4 | [`P4-facebook-all-qualities.md`](P4-facebook-all-qualities.md) | Facebook video ၁ ခု၊ quality အားလုံး | Medium–Hard | 5–8 နာရီ | P3 |
| P5 | [`P5-feed-focused-video.md`](P5-feed-focused-video.md) | Feed ပေါ်က focus video ကို Download | Hard | 6–10 နာရီ | P1, P3 |
| P6 | [`P6-2k-4k-webm.md`](P6-2k-4k-webm.md) | 2K/4K (VP9 + Opus → `.webm`) | Hard | 8–12 နာရီ | P3 |
| P7 | [`P7-preview-apk.md`](P7-preview-apk.md) | Owner စမ်းဖို့ preview APK | Easy | 1–2 နာရီ | P1–P6 |
| P8 | [`P8-signed-beta4.md`](P8-signed-beta4.md) | Signed `1.0.0-beta.4` | Easy | 1–2 နာရီ | P7 + owner OK |

အစဉ် (owner, 2026-10-04): P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → owner စမ်း → P8။

## English summary

`00_NEXT_TASK.md` picks the next task from the status board in `docs/FIX_ADD_PLAN.md`; each
`Px-*.md` file holds one self-contained English prompt for one task (repository, branch, flags,
start, work, tests, validation, docs, checkpoint, short Burmese report, next task).
`MASTER_PROMPT.md` is the generic prompt for any agent. Defaults: `ALLOW_PUSH=true`,
`ALLOW_MERGE_MAIN=false`, `ALLOW_RELEASE=false`; only P8 merges, tags and signs, after the
owner's OK. The Phase 8–10 prompts (T01–T19) were removed after release `1.0.0-beta.3`; they
remain in Git history (`git show 2f6284f:docs/prompts/`).
