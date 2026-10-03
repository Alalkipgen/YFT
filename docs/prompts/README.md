# YFT Prompts — Phase 8–10

Agent တစ်ယောက်ကို အလုပ်ခိုင်းဖို့ prompt တွေပါ။ Plan အပြည့်အစုံ (အကြောင်းရင်း၊ အဆင့်တိုင်း၊ test၊
status board) က [`docs/FIX_PLAN.md`](../FIX_PLAN.md) မှာ ရှိပါတယ်။ Prompt တွေက အဲဒီ plan ကို
ညွှန်ပြီး agent ကို task တစ်ခုစီ လုပ်စေပါတယ်။

## သုံးနည်း

1. **အလွယ်ဆုံး:** [`00_NEXT_TASK.md`](00_NEXT_TASK.md) ထဲက code block ကို agent chat အသစ်ထဲ
   paste လုပ်ပါ။ Agent က status board ကိုကြည့်ပြီး နောက်လုပ်ရမယ့် task ကို သူ့ဘာသာ ရွေးပါမယ်။
2. **Task တစ်ခုကို တိတိကျကျ:** အောက်ဇယားထဲက task ဖိုင်ကိုဖွင့်ပြီး code block ကို paste လုပ်ပါ။
3. Chat တစ်ခုမှာ task တစ်ခုပဲ လုပ်ပါ။ ပြီးရင် agent က မြန်မာလို report ပေးပြီး ရပ်ပါမယ်။ နောက် task
   အတွက် chat အသစ်ဖွင့်ပြီး ထပ် paste လုပ်ပါ။
4. Agent တိုင်းက `work/phase-*` branch ပေါ်မှာ checkpoint commit + push လုပ်ပါတယ်။ `main` ကို
   မထိပါဘူး။

## Flag တွေ

| Flag | Default | အဓိပ္ပာယ် |
| --- | --- | --- |
| `ALLOW_PUSH` | `true` | Work branch ကို checkpoint push လုပ်ခွင့် |
| `ALLOW_MERGE_MAIN` | `false` | Release task (T10/T15/T19) မှာ `main` merge နဲ့ tag တင်ခွင့် — ခွင့်ပြုချင်မှ `true` ပြောင်းပါ |
| `ALLOW_RELEASE` | `false` | Draft release ကို public publish လုပ်ခွင့် — ဖုန်းမှာစစ်ပြီးမှ |
| `OWNER ANSWERS` | `none` | D1–D3 အဖြေတွေ၊ ဥပမာ `D1=YES D2=B D3=YES` |

## ဖြေပေးရမယ့် ဆုံးဖြတ်ချက်များ

- **D1** — YFT ဖွင့်တိုင်း clipboard ကို အလိုအလျောက် စစ်မလား (Android 12+ မှာ "pasted from your
  clipboard" ပြမယ်)။ T11 ကို ပိတ်ထားပါတယ်။
- **D2** — YouTube: **A** = PO token (ခက်၊ မတည်ငြိမ်) / **B** = device client (`visionos`၊ လွယ်ပြီး
  format ပိုရ)။ T16/T17 ကို ပိတ်ထားပါတယ်။
- **D3** — MP3 ထည့်မလား (LGPL၊ APK ~1 MB ကြီးမယ်)။ T18 ကို ပိတ်ထားပါတယ်။

အဖြေကို prompt ထဲ `OWNER ANSWERS:` line မှာ ရေးပေးရင် agent က `FIX_PLAN.md` §3 ထဲ မှတ်ပါမယ်။

## ဖုန်းမှာ စမ်းဖို့ APK

Push တစ်ခါတိုင်း CI က `yft-debug-apk` artifact ထုတ်ပါတယ် (၁၄ ရက်ထားတယ်)။ GitHub › Actions › run ›
Artifacts › `yft-debug-apk` ကို download လုပ်ပြီး unzip ပြီး install လုပ်ပါ။ Debug app က beta နဲ့
ဘေးချင်းယှဉ် install ဖြစ်ပါတယ် (`com.alal.yft.debug`)။ Signed beta ကတော့ release task တွေကပဲ ထုတ်ပါတယ်။

## Task ဇယား

| ID | Phase | Prompt | အလုပ် | လိုအပ်ချက် |
| --- | --- | --- | --- | --- |
| T01 | 8 | [`T01-browser-crash.md`](T01-browser-crash.md) | Browser ဖွင့်တိုင်း app ပိတ်သွားတာ ပြင် | — |
| T02 | 8 | [`T02-ci-emulator-smoke.md`](T02-ci-emulator-smoke.md) | CI emulator မှာ တကယ့် WebView စမ်းသပ်မှု | T01 |
| T03 | 8 | [`T03-browser-start-page.md`](T03-browser-start-page.md) | Browser start page၊ address bar ပျောက်တာ ပြင် | T01 |
| T04 | 8 | [`T04-crash-report-details.md`](T04-crash-report-details.md) | Crash report နဲ့ Copy details | — |
| T05 | 8 | [`T05-headless-identity.md`](T05-headless-identity.md) | Home lookup အတွက် browser လို header | — |
| T06 | 8 | [`T06-facebook-public-video.md`](T06-facebook-public-video.md) | Facebook public reel/video | T05 |
| T07 | 8 | [`T07-tiktok-media-cookies.md`](T07-tiktok-media-cookies.md) | TikTok download 403 ပြင် | T05 |
| T08 | 8 | [`T08-youtube-messages-details.md`](T08-youtube-messages-details.md) | YouTube message အမှန်နဲ့ details | T04, T05 |
| T09 | 8 | [`T09-your-sites-logos.md`](T09-your-sites-logos.md) | Your sites: YouTube/Facebook/TikTok + logo | — |
| T10 | 8 | [`T10-release-beta3.md`](T10-release-beta3.md) | 1.0.0-beta.3 release | T01–T09, owner |
| T11 | 9 | [`T11-copied-link-watcher.md`](T11-copied-link-watcher.md) | Copy ထားတဲ့ link ကို အလိုအလျောက်စစ် | D1 |
| T12 | 9 | [`T12-quick-download-sheet.md`](T12-quick-download-sheet.md) | "Video you copied" quick sheet | — |
| T13 | 9 | [`T13-search-to-download.md`](T13-search-to-download.md) | Search to download page | T03, T09 |
| T14 | 9 | [`T14-download-fab.md`](T14-download-fab.md) | Browser floating Download ခလုတ် | T12 |
| T15 | 9 | [`T15-release-beta4.md`](T15-release-beta4.md) | 1.0.0-beta.4 release | T11–T14, owner |
| T16 | 10 | [`T16-youtube-client-strategy.md`](T16-youtube-client-strategy.md) | YouTube client strategy | D2, T08 |
| T17 | 10 | [`T17-video-audio-mux.md`](T17-video-audio-mux.md) | 720p/1080p video + audio ပေါင်း | T16 |
| T18 | 10 | [`T18-mp3-audio.md`](T18-mp3-audio.md) | MP3 audio | D3 |
| T19 | 10 | [`T19-release-beta5.md`](T19-release-beta5.md) | 1.0.0-beta.5 release | T16–T18, owner |

အစဉ်: Phase 8 = T01 → T02 → T03 → T04 → T05 → T06 → T07 → T08 → T09 → T10 · Phase 9 = T12 → T11 →
T13 → T14 → T15 · Phase 10 = T16 → T17 → T18 → T19။

## English summary

`00_NEXT_TASK.md` picks the next task from the status board in `docs/FIX_PLAN.md`; each `Txx-*.md`
file holds one self-contained English prompt for one task (repository, branch, flags, startup,
key points, tests, validation, docs, checkpoint, Burmese report, stop). Defaults:
`ALLOW_PUSH=true`, `ALLOW_MERGE_MAIN=false`, `ALLOW_RELEASE=false`. Earlier phase prompts
(Phases 0–7) were removed after those phases were completed; they remain in Git history
(`git show 28930cf:docs/prompts/00_MASTER_PROMPT.md`).
