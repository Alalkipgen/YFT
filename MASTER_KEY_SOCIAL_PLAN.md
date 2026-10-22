# Master Key — Next level for Instagram, TikTok, Facebook and X (final plan)

Status: **final plan** — the owner's Plan Mode request (2026-10-17), reviewed and approved by the
owner as the final plan the same day. This document changes no code. Decided: routing, lookup
scope and the R8 ladder (§2.1, §8 items 7–9). Nothing here is built until the owner says go, one
step at a time (§7, §8). **S0 started on the owner's go (2026-10-20): §7.1; the owner's run (S0d):
§7.2. The final plan of the steps after S0d (the owner, 2026-10-20): §7.3; decided §8 items 1, 2,
4, 6 and 10–12; open: item 3 (after the Instagram check, §7.3 item 0.2) and item 5 (before S4).
The plan after S1a (8) (the owner, 2026-10-21): §7.4; decided §8 items 13–17; its steps 0–5
done (2026-10-21; step 4's runs: §7.4); step 6's APK (S1a (9)) built (2026-10-21); step 6's
first run (the owner, 2026-10-21: §7.4) and its fixes (S1a (10), behind the flag) built
(2026-10-21); step 6's re-run (the owner, 2026-10-21): S1a's phone proof passed (§7.4). Instagram's
census and X's B0 (the owner, 2026-10-21): §7.4; decided §8 items 3, 18 and 19; open: item 5
(before S4). Decision 18 (link 9's desktop form, main) built (2026-10-21; §8). S2b (TikTok's
ladder, behind the flag) built (2026-10-21; §7.3). S2c (X's ladder, behind the flag) built
(2026-10-21; §7.3, §7.4). S2d part 1 (Instagram's ladder, I2, and the tab-fetch probe, off by
default; behind the flag) built (2026-10-22; §7.3, §7.4). Task 6a (one trial APK for the four
sites: d7-matrix's `social-trial`, artifact `yft-d7-social-trial`, `fb-trial` before) built
(2026-10-22; §7.3 S1a (9)). The owner's acceptance of S2c's and S2d part 1's intended differences
(2026-10-22): §8 decisions 20 and 21. Task 0 (X's widget 404 never final, by decision 21; behind
the flag) built (2026-10-22; §7.3). Task 6b (the site census runs each site's ladder in a
social-ladder build: the rungs, wrong and unknown rows; flag off unchanged) built (2026-10-22;
§7.3, §7.4). Task 7 (Facebook's Master rows 2 against main's 3: main's audio row from the page's
DASH manifest, listed in `docs/SUPPORT_MATRIX.md` as an intended difference; link 9's desktop form
pinned with the trial; tests and docs only) done (2026-10-22; §7.3, §7.4). Task 8 (S3's weekly
check W in the site census: three slots per site, each way at most once, per site at most 60 s,
2 MB and 24 requests, W's own baselines, each CHANGED or BREAKING line with its runbook step; debug
only, in a social-ladder build only; flag off and the full census unchanged) built (2026-10-22;
§7.3, §7.5). Task 9 (S6's row reader checks each read against its lookup's site: one reader for
every site, another site's host refused before any ask and counted refused, never `NETWORK`;
Facebook unchanged; from the phone session of 2026-10-22; debug only, behind the flag) built
(2026-10-22; §7.3, §7.4). Next: the owner's phone session (§7.4: Master on X first, then TikTok
after its 429's cooldown, then Instagram; W's first run last).**

## 0. အကျဉ်းချုပ်

- **အဖြေ: သုံးသင့်ပါတယ်။** ဒါပေမဲ့ YouTube လောက်ကြီးတဲ့ extractor ၄ ခုကို သီးသန့် မရေးပါဘူး။
  **Next Level kit တစ်ခု (site အားလုံး မျှသုံး)** နဲ့ site တစ်ခုစီအတွက် **site pack ပါးပါး** (contract
  recipe၊ answer judge rule၊ post နဲ့ ချိတ်တဲ့ shape anchor၊ row check host — အများစု data) ကို generic
  engine ထဲမှာ ထည့်ပါမယ်။ main adapter ကို မကူးပါ (rung-0 copy ~4,700 lines မလုပ်တော့ပါ)၊ site ကို
  သိမ်းယူတဲ့ (claiming) module လည်း မလုပ်ပါ။
- **Final ဆုံးဖြတ်ချက် (ပိုင်ရှင်၊ 2026-10-17; §2.1၊ §8 #7–#9):**
  - **#7 Routing = B:** site ၄ ခုလုံးမှာ main က အရင်ရှာပြီး **main မအောင်မြင်မှ** Master လုပ်ပါတယ်။
    main အောင်မြင်ရင် main ရဲ့ row တွေ ဒီအတိုင်း ထွက်ပါတယ်။ နောက်မှ **B+**: main ရဲ့ အဖြေ အားနည်းရင်
    (HD မပါ၊ အသံမပါ စတဲ့ တိုင်းလို့ရတဲ့ signal) Master က မပါတဲ့ format ကိုပဲ ဖြည့်ပြီး main row တွေကို
    မထိပါ။ **A** (Master က main နေရာ အစားထိုး) ကို B+ မလုံလောက်ကြောင်း တိုင်းပြနိုင်မှသာ လုပ်ပါမယ်။
  - **#8 Lookup = user ရဲ့ ရှာမှု တစ်ခုလုံး (main + Master):** app ဘက်က per-lookup request recorder
    (memory ထဲမှာပဲ) က main မေးခဲ့တာနဲ့ 429/bot check ပြန်တဲ့ host ကို မှတ်ထားလို့ Master က
    ထပ်မမေးပါ၊ အဲဒီ host ကိုလည်း ဆက်မမေးပါ။ main adapter code ကို မပြင်ပါ။
  - **#9 R8 ladder:** "တစ်ခါပဲ မေး" အစား budget ပါတဲ့ လှေကားသေးသေး ဖြစ်ပါမယ်။ rung အသစ်တိုင်း
    ပိုင်ရှင် go လိုပါတယ်။
  - **"main ထက် ဘယ်တော့မှ မညံ့" ကို အာမမခံတော့ပါ:** policy တူရင် regression မရှိပါ (main အောင်မြင်ရင်
    မထိလို့)။ ရည်ရွယ်ချက်ရှိရှိ ကွဲတဲ့ policy (third party မသုံး၊ V မအောင်ရင် row မပြ၊ 429 ပြီးရင် ရပ်)
    ကို site တစ်ခုစီအလိုက် စာရင်းပြပါမယ် (§4 S1)။
- **ဒီနေ့ အခြေအနေ:** main adapter ၄ ခုက လမ်းတွေကို အစဉ်တစ်ခုတည်းနဲ့ အမြဲ မေးပါတယ်။ ဘယ်လမ်း
  အလုပ်ဖြစ်ခဲ့လဲ မမှတ်ပါဘူး။ row တွေ ဖွင့်လို့ရမရကို TikTok တစ်ခုပဲ စစ်ပါတယ်။ Master generic က
  main ပျက်မှ embed endpoint တစ်ခု မေးပြီး page/capture layer တွေကို ဖတ်ပါတယ်။ **IG နဲ့ X မှာ
  Master က main မေးပြီးသား endpoint ကိုပဲ ထပ်မေးနေလို့ တကယ့် ဒုတိယလမ်း မရှိပါဘူး။**
- **YouTube ပုံစံနဲ့ ယှဉ်ရင် မရှိသေးတာ:** answer judge၊ ladder + health (နောက်ဆုံး အလုပ်ဖြစ်ခဲ့တဲ့လမ်းကို
  မှတ်ခြင်း၊ cooldown)၊ own parser (key နာမည် ပြောင်းသွားလည်း တန်ဖိုးပုံစံနဲ့ ရှာခြင်း)၊ row တိုင်း V
  check၊ census နဲ့ weekly check (မပျက်ခင် ကြိုသိခြင်း)၊ self-healing value (IG `doc_id`၊ X token)၊
  APK အသစ်မထုတ်ဘဲ ပြင်နိုင်တဲ့ remote recipe (တည်ဆောက်ပြီးသား၊ live မဖြစ်သေး)။
- **ပြန်သုံးလို့ရတာ (§3):** လိုအပ်တာရဲ့ **သုံးပုံနှစ်ပုံလောက် (ခန့်မှန်း)** က ရှိပြီးသားပါ — main adapter
  ၄ ခု (မကူးဘဲ main အဖြစ် အရင် run)၊ FB DASH reader၊ embed recipe တွေ၊ capture၊ download engine
  (DASH/HLS/merge)၊ `InitCheck`၊ probe/V စနစ်၊ R9၊ parity/drift check၊ D7 APK matrix။ အသစ်ရေးရမှာက
  site judge rule တွေ၊ post နဲ့ ချိတ်တဲ့ parser binding၊ site census၊ request recorder နဲ့ ချိတ်ဆက်တဲ့
  code ပါ။ YouTube ဖိုင်တွေကို မပြင်ပါ။
- **Layer 0–8 (§5):** policy → site table → answer judge → own parser → ladder/health → row check →
  self-heal → early warning → repair channel။
- **အစဉ် (§7၊ §7.3):** S0 (ပြီးပြီ) → **Stage 0** (0.1 main ရဲ့ X card fix၊ 0.2 Instagram check၊
  0.3 Facebook access check evidence၊ 0.4 census fix + site census diff၊ 0.5 ပိုင်ရှင်ရဲ့ baseline
  B0) → **S1a** kit အနည်းဆုံး + Facebook pack (pilot) → **S2b** TikTok → **S2c** X → **S2d**
  Instagram → S3 weekly check → S4 remote recipe live → S5 site တစ်ခုစီ phone run ၃ ခါ → PR #2
  merge → P8 (beta.4)။ release ပြီးမှ: S1b၊ X2၊ "item အားလုံး" feature၊ B+ (S6)၊ A။
- **ဆုံးဖြတ်ပြီး:** §8 #1–#4၊ #6–#19။ **ကျန်:** #5 (S4 မတိုင်မီ)။
- **S0d (2026-10-20, §7.2):** ပိုင်ရှင်ရဲ့ phone run ၂ ခု။ Recoverable failure: Facebook 0/11၊
  TikTok 0/6၊ Instagram 0/7၊ X 2/6 (main မဖတ်တဲ့ card ထဲက video)။ Pilot: Facebook → TikTok →
  X → Instagram (ပိုင်ရှင် အတည်ပြုပြီး၊ 2026-10-20၊ §8 #1)။
- **Final Plan (ပိုင်ရှင်၊ 2026-10-20၊ §7.3):** X2 အခု မလုပ်ပါ (#2)၊ Master မှာ third party
  မသုံးပါ (#4)။ main fix ကို ပိုင်ရှင် ခွင့်ပြုတဲ့ ခြွင်းချက်အဖြစ် fix တစ်ခု commit တစ်ခုနဲ့ပဲ
  လုပ်ပါမယ် (#10): X card (go)၊ Facebook access check (evidence ပြီးမှ)၊ Instagram (check
  ပြီးမှ)။ X ရဲ့ age-limited / removed reason (`NO_MEDIA_FOUND`) မပြောင်းပါ။ link တစ်ခုကို item
  တစ်ခုပဲ ပြပါမယ် (IG `img_index`၊ X `/video/N`၊ မပါရင် ပထမ item; #11)၊ "item အားလုံး" feature
  က release ပြီးမှ။ Release gate (#12): site ၄ ခုလုံး S0–S5 ပြီးမှ PR #2 merge → P8 (beta.4) →
  တခြားသူတွေ။ S1b နဲ့ B+ (S6) က release ပြီးမှ။ Baseline B0 (§9) ထက် main အောင်မြင်တဲ့ lookup
  ရဲ့ request/အချိန် မများရပါ။
- **S1a (8) ပြီးနောက် Plan (ပိုင်ရှင်၊ 2026-10-21၊ §7.4၊ §8 #13–#17):** census fix 0.4 (c) (debug
  only) ကို အရင်လုပ်ပြီး Facebook access fix A1 ("0.3 fix"၊ main၊ commit တစ်ခု) ကို နောက်မှ
  လုပ်ပါမယ်။ Phone test ကို commit ၂ ခုလုံးပါတဲ့ APK နဲ့ တစ်ခါပဲ (FB-only VPN နဲ့ no-VPN) run
  ပါမယ်။ A2 (reel ရဲ့ `video: null` ကို final ယူခြင်း) ကို main မှာ အပြီးပိတ်ပါပြီ — design အရ
  recoverable ပါ။ FB slot 12 ကို signed out ဆိုရင် "ambiguous (no session)" လို့ ပြပါမယ်။
  TikTok (§8 #17): လက်ရှိ APK နဲ့ ထပ်မ run ပါ။ Commit ၂ ခုပါတဲ့ APK ရမှ VPN (US exit) နဲ့
  TikTok-only ကို အရင်ဆုံး run ပါမယ်။ Census က slot အားလုံးရဲ့ main ကို အရင် run တာမို့ embed မှာ
  429 ထွက်လည်း main ပြည့်ပါမယ်။ ဒီ run က TikTok ရဲ့ B0 ပါ။ S2b အတွက် မှတ်ချက်တွေ §7.4 မှာ ပါပါတယ်။
- **§7.4 step 4–5 (ပိုင်ရှင်၊ 2026-10-21၊ `6b011da` ရဲ့ APK၊ sign out):** TikTok (VPN) main 9/9၊
  not as expected 0၊ V pass 6/6 — TikTok ရဲ့ B0 ရပါပြီ (embed 429 ကြောင့် trials မပြီးတာက note ပဲ
  ဖြစ်ပါတယ်)။ Facebook (VPN ပါ/မပါ) rows 11/11၊ V pass 11၊ trials 8/8၊ requests 68၊ time 65 s /
  52 s — B0 ထက် မပိုပါ။ VPN မပါတဲ့ run မှာ slot 13 က `PRIVATE_OR_UNAVAILABLE` (final) ဖြစ်လာပါပြီ
  (A1 ကို ဖုန်းပေါ်မှာ သက်သေပြပြီ)။ Slot 12 က "ambiguous (no session)" ပါ။ ဒီ run ၃ ခုက baseline
  အသစ်တွေ ဖြစ်ပါတယ်။ နောက်တစ်ဆင့်: step 6 (S1a ရဲ့ phone proof၊ ပိုင်ရှင် go ပေးမှ)။
- **§7.4 step 6 ရဲ့ ပထမ run (ပိုင်ရှင်၊ 2026-10-21၊ `ef4a546` ရဲ့ `yft-d7-fb-trial`၊ sign out၊ VPN
  ပါ/မပါ):** trial line ပြတဲ့ နေရာတိုင်းမှာ the same video yes၊ wrong rows 0 ပါ။ ဒါပေမဲ့ link 9
  (Page post ရဲ့ desktop link) မှာ trial line မပြဘဲ post ရဲ့ video မဟုတ်တဲ့ video (b) ကိုပါ ပြပါတယ်
  — main က ဒီ address ကို site page လို့ မသိလို့ပါ၊ flag ပိတ်ထားလည်း ဒီအတိုင်းပါပဲ (main ရဲ့ bug၊
  fix က ပိုင်ရှင် ဆုံးဖြတ်ရန်)။ Link 10 (group post) "Master found none"၊ duration line အားလုံး
  "unknown"၊ link 1–2 ရဲ့ ပထမ lookup "Master found none"၊ link 12–13 က sign-in page ပါ။ S1a (10)
  (flag နောက်မှာ): Master row ရဲ့ length ကို S6 ဖတ်ပြီးသား file အစကနေ ယူပါတယ်၊ lookup အတွင်း page
  ပြောင်းသွားပေမဲ့ video အတူတူ ဖွင့်ထားဆဲဆိုရင် ladder rows ကို ထားပါတယ်၊ Master ရဲ့ အဖြေကို
  မသုံးရင် Details မှာ step ကို ပြပါတယ်။ Link 9–10 ရဲ့ desktop form နဲ့ link 12–13 က documented
  limitation ပါ (link 9–10 ရဲ့ share link တွေက Master rows ပေးပါတယ်)။ နောက်တစ်ဆင့်: step 6 ရဲ့
  re-run (link 13 ခု နဲ့ share/p link တစ်ခု၊ VPN ပါ/မပါ)။
- **§7.4 step 6 ရဲ့ re-run (ပိုင်ရှင်၊ 2026-10-21၊ `042d57a` ရဲ့ `yft-d7-fb-trial`၊ sign out၊ VPN
  ပါ/မပါ): S1a ရဲ့ phone proof PASS ပါ။** Link 1–8 နဲ့ 11 မှာ ပထမ lookup ကတည်းက Master rows ရပြီး
  trial line က "Master's rows 2, main's 3; the same video: yes; duration within 1 s: yes" ပါ။ Link
  9–10 ရဲ့ share/v link တွေ Master rows ပေးပါတယ်။ Link 9 ရဲ့ desktop form က main ရဲ့ bug ပါ (#18:
  main မှာ commit သီးသန့်နဲ့ ပြင်ပါမယ်)။ Link 10 ရဲ့ desktop form နဲ့ link 12–13 က documented
  limitation ပါ။ Master wrong rows 0 ပါ။ S5 မတိုင်မီ: Master rows 2 နဲ့ main rows 3 ကွာတာကို ပြင်ရန်
  ဒါမှမဟုတ် `docs/SUPPORT_MATRIX.md` မှာ ရေးရန်ပါ။
- **Instagram census နဲ့ X ရဲ့ B0 (ပိုင်ရှင်၊ 2026-10-21၊ §7.4):** Instagram sign out (VPN မပါ)
  GraphQL 401 10/10၊ main rows video slot 7 ခုမှာ 6 ခု — Instagram ရဲ့ baseline (VPN off၊ sign
  out)။ Sign in ဆိုရင် app API နဲ့ rows 7/7 (VPN မပါ) နဲ့ 8 (VPN ပါ)၊ slot 10 (removed) က
  `RESPONSE_CHANGED` ပါ။ X (VPN ပါ/မပါ) main slot 1–6 rows၊ V pass 6/6၊ slot 7–8 က
  `NO_MEDIA_FOUND` (as expected) — X ရဲ့ B0 ရပါပြီ။ Master trial မှာ HLS row (duration မသိ)၊ slot 6
  မှာ video ၂ ခုလုံးရဲ့ rows နဲ့ removed post ကို "needs playback" ပြတာတွေကို S2c မှာ ပြင်ပါမယ်။
- **ဆုံးဖြတ်ချက် #3၊ #18၊ #19 (ပိုင်ရှင်၊ 2026-10-21၊ §8):** #3 — `doc_id` self-healing အခု မလုပ်ပါ
  (`VALUE_REJECTED` ဆိုရင် နောက် stage ကို သွားပါတယ်၊ value ကို ခန့်မှန်းမထည့်ပါ)။ #18 — link 9 ရဲ့
  desktop form ကို main မှာ ပြင်ပါမယ် (S1 ရဲ့ ခြွင်းချက်၊ commit သီးသန့်)။ #19 — Instagram stage က
  visible tab ရဲ့ fetch ကို စည်းကမ်း (i)–(vi) နဲ့ပဲ သုံးနိုင်ပါတယ်၊ ပထမဆုံး debug-only probe ပါ။
  နောက်တစ်ဆင့်: #18၊ S2b (TikTok)၊ S2c (X)၊ S2d (Instagram အပိုင်း ၁)၊ ပြီးရင် ပိုင်ရှင်ရဲ့ phone
  session (§7.4)။
- **ဆုံးဖြတ်ချက် #18 ပြီးပါပြီ (main၊ 2026-10-21၊ §8):** `story.php`/`permalink.php` ရဲ့ `id` က Page
  နာမည် (Facebook ရဲ့ surface မဟုတ်တဲ့ နာမည်) ဖြစ်ရင်လည်း main က Page ရဲ့ `/posts/` address ကို
  မေးပါတယ်၊ ဒါကြောင့် link 9 ရဲ့ desktop form မှာ lookup run ပါပြီ (flag off/on နှစ်မျိုးလုံး)။
  Sandbox live check (2026-10-21၊ session မပါ၊ shape only): desktop Chrome နဲ့ 200၊ redirect မရှိ၊
  post ရဲ့ video တစ်ခုတည်း။ Master ရဲ့ trial က link 10 လိုပဲ "Master found none" ဖြစ်မယ်လို့
  မျှော်လင့်ပါတယ် (redirect မရှိလို့ F1 အတွက် video link မရပါ — documented limitation)။ Facebook ကို
  နောက် phone session မှာ re-baseline လုပ်ပါမယ်။ နောက်တစ်ဆင့်: S2b (TikTok)၊ S2c (X)၊ S2d
  (Instagram အပိုင်း ၁)။
- **S2b ပြီးပါပြီ (TikTok၊ flag နောက်မှာ၊ debug only၊ 2026-10-21၊ §7.3):** main ရဲ့ TikTok adapter
  မအောင်မြင်မှ TikTok ladder က ဖြေပါတယ်။ Photo post ဆိုရင် request မပို့ဘဲ final ဖြစ်ပါတယ်
  (`NO_MEDIA_FOUND`၊ main ရဲ့ စကား)။ T1 က embed player (`embed/v2`) ကို cookie မပါဘဲ တစ်ခါပဲ
  မေးပြီး judge လုပ်ပါတယ် — row တွေကို item ID နဲ့ ချိတ်ပြီး V နဲ့ init check ကို TikTok ရဲ့ media
  host မှာ စစ်ပါတယ်။ 429 ဒါမှမဟုတ် bot check ဆိုရင် host ကို ဆက်မမေးဘဲ 30 မိနစ် cooldown ပါ။ T2 က
  main ဖတ်ပြီးသား page တွေကို request မပို့ဘဲ ပြန်ဖတ်ပါတယ်၊ မရရင် visible tab ရဲ့ playback (T3)
  ပါ။ TikTok ရဲ့ budget (`SocialBudget.TIKTOK`: request 20၊ 4 MiB၊ 30 s)။ "T2 before T1" ကို
  မရွေးပါ — TikTok page ရဲ့ file တွေက အဲဒီ page answer ရဲ့ cookie (`tt_chain_token`) နဲ့ပဲ ပွင့်ပြီး
  V က cookie မပို့လို့ပါ (R20)။ Intended difference: T1 rows က embed ရဲ့ file (1280 lines အထိ)။
  `yft-d7-fb-trial` APK က Facebook နဲ့ TikTok နှစ်ခုလုံးကို trial လုပ်ပါတယ်။ Flag off = ဒီနေ့
  အတိုင်း။ နောက်တစ်ဆင့်: S2c (X)။
- **S2c ပြီးပါပြီ (X၊ flag နောက်မှာ၊ debug only၊ 2026-10-21၊ §7.3၊ §7.4):** main ရဲ့ X adapter
  မအောင်မြင်မှ X ladder က ဖြေပါတယ်။ X1 က main ရဲ့ widget answer (`tweet-result`) ကို request အသစ်
  မပို့ဘဲ ပြန်ဖတ်ပြီး judge လုပ်ပါတယ် (token self-check မလုပ်၊ X2 မရှိ — decision 2)။ Link ရဲ့ item
  (`/video/N`၊ မရှိရင် ပထမ video) ရဲ့ rows ပဲ ယူပါတယ် — MP4 rows အရင်၊ duration က main ရဲ့ answer
  ကနေ၊ HLS row က MP4 မရှိပြီး duration သိမှသာ။ Rows ကို post ID နဲ့ ချိတ်ပြီး V နဲ့ init check ကို
  X ရဲ့ video host မှာ စစ်ပါတယ်။ Photo post ဆိုရင် request မပို့ဘဲ final ပါ (widget ရဲ့ 404 က task 0
  ကစပြီး final မဟုတ်ပါ)။ Live
  shape check (2026-10-21၊ sign out၊ shape only): age-limited post က empty tombstone (→ browser)၊
  deleted post နဲ့ protected post က X ရဲ့ notice link ပါတဲ့ tombstone တူတူ (ခွဲမရလို့ ဒီနေ့ reason
  အတိုင်း needs playback)၊ မရှိတဲ့ ID က 404 (task 0 ကစပြီး → needs playback၊ final မဟုတ်)၊ token မပါရင် `{}` (→
  `VALUE_REJECTED`၊ final မဟုတ်)။ X ရဲ့ budget (`SocialBudget.X`: request 9၊ 2 MiB၊ 20 s)။
  `yft-d7-fb-trial` က X ရဲ့ rows ကိုလည်း ဘေးဖယ်ပြီး item နဲ့ duration ကို နှိုင်းယှဉ်ပါတယ်။ Intended
  differences ကို `docs/SUPPORT_MATRIX.md` မှာ ရေးထားပါတယ် (ပိုင်ရှင် ဆုံးဖြတ်ရန်)။ Flag off = ဒီနေ့
  အတိုင်း။ နောက်တစ်ဆင့်: S2d (Instagram အပိုင်း ၁)။
- **S2d အပိုင်း ၁ ပြီးပါပြီ (Instagram၊ flag နောက်မှာ၊ debug only၊ 2026-10-22၊ §7.3၊ §7.4):** main ရဲ့
  Instagram adapter မအောင်မြင်မှ Instagram ladder က ဖြေပါတယ်။ I2 က main ဒီ lookup မှာ ရထားတဲ့
  answers (sign in ထားရင် app API၊ GraphQL၊ post page၊ embed page) ကို request အသစ် မပို့ဘဲ ဖတ်ပြီး
  judge လုပ်ပါတယ် (main ရဲ့ embed ask ကို ထပ်မမေး၊ Master ကိုယ်ပိုင် GraphQL ask မရှိ — decision 3)။
  Link ရဲ့ item (`img_index`၊ မရှိရင် file ပါတဲ့ ပထမ item) ရဲ့ rows ပဲ ယူပြီး post နဲ့ ချိတ်၊ V နဲ့ init
  check ကို Instagram ရဲ့ media hosts မှာ စစ်ပါတယ် (DASH ဆိုရင် video နဲ့ sound နှစ်ခုလုံး)။ Photo post
  ဆိုရင် request မပို့ဘဲ main ရဲ့ စကားနဲ့ final ပါ။ Sign-in wall (checkpoint အပါ)၊ `VALUE_REJECTED`၊
  post မပါတာ၊ `EmbedIsBroken` တို့က final မဟုတ်ဘဲ tab ရဲ့ playback ဆီ သွားပါတယ်; 429 သို့မဟုတ်
  "wait a few minutes" ဆိုရင် site ရပ်ပြီး host ကို ၃၀ မိနစ် cool လုပ်ပါတယ်။ Removed post ကို
  ဘယ်တော့မှ `REMOVED` မခေါ်ပါ (decision 19 (vi): sign out အဖြေတွေက private / sign-in-only post နဲ့
  တူလို့)။ Budget `SocialBudget.INSTAGRAM` (request 13၊ 7 MiB၊ 30 s)။ Tab-fetch probe (decision 19၊
  debug only၊ default off): "YFT site census" ထဲက "Instagram's tab-fetch probe" box နဲ့ ဖွင့်ရပြီး
  ဖွင့်ထားရင် app browser ရဲ့ Instagram lookup တစ်ခုမှာ I2 ပြီးနောက် တစ်ကြိမ်ပဲ visible tab ရဲ့ ကိုယ်ပိုင်
  fetch နဲ့ yt-dlp ရဲ့ signed-out query ကို မေးပြီး Details line တစ်ကြောင်း (HTTP status၊ bytes၊
  post ရဲ့ video ပါ/မပါ၊ wall ရှိ/မရှိ) ပြပါတယ်; row ဘယ်တော့မှ မဖြစ်ပါ။ `yft-d7-fb-trial` က
  Instagram ရဲ့ rows ကိုလည်း ဘေးဖယ်ပြီး item၊ duration နဲ့ tallest ကို နှိုင်းယှဉ်ပါတယ်။ Census မှာ
  tallest ကွာတာက census ရဲ့ Master trial က R8 ရဲ့ public embed page (cookie မပါ) ကို ဖတ်ပြီး main
  က session နဲ့ app API ကို ဖတ်လို့ပါ; I2 က main ရဲ့ answers ကို ဖတ်လို့ trial မှာ တူသင့်ပါတယ်။
  Intended differences (360p file၊ removed မခေါ်တာ) ကို `docs/SUPPORT_MATRIX.md` မှာ ရေးထားပါတယ်။
  Flag off = ဒီနေ့ အတိုင်း။ နောက်တစ်ဆင့်: site ၄ ခုလုံးအတွက် trial APK နဲ့ census၊ ပြီးရင် ပိုင်ရှင်ရဲ့
  phone session။
- **Task 6a — site ၄ ခုအတွက် trial APK တစ်ခု (2026-10-22):** d7-matrix ရဲ့ ninth mode `fb-trial` ကို
  `social-trial` (artifact `yft-d7-social-trial`) လို့ အမည်ပြောင်းပါတယ်။ App က S2d part 1 နဲ့
  အတူတူပါ: ladder နဲ့ trial က Facebook၊ TikTok၊ X၊ Instagram လေးခုလုံးမှာ ဖွင့်ထားပြီး Instagram ရဲ့
  tab-fetch probe က default off ပါ (build flag မရှိ၊ "YFT site census" ရဲ့ box နဲ့ပဲ ဖွင့်ရ)။ ကျန် mode
  ရှစ်ခုနဲ့ push build မပြောင်းပါ (flag off)။ နောက်တစ်ဆင့်: census ရဲ့ Master trial မှာ site pack တွေ
  (task 6b)၊ ပြီးရင် ပိုင်ရှင်ရဲ့ phone session (`yft-d7-social-trial`)။
- **ပိုင်ရှင်ရဲ့ ဆုံးဖြတ်ချက် 20–21 (2026-10-22၊ §8):** Instagram (S2d part 1): main ချန်ထားတဲ့ 360p
  file ကို Master က ထားတာ — မှန်တဲ့ post/item ဖြစ်ပြီး wrong row မရှိ၊ အမြင့် quality မပျောက်မှ —
  လက်ခံပါတယ်; removed post ကို ဒီနေ့ ambiguous answers ကနေ final မလုပ်ဘဲ main ရဲ့ reason ထားတာ
  လက်ခံပါတယ်; ဒါက part 2 ရဲ့ tab-fetch rung နဲ့ `doc_id` self-healing ကို ခွင့်မပြုပါ။ X (S2c): `{}` →
  `VALUE_REJECTED` (final မဟုတ်) နဲ့ notice tombstone (deleted/protected ခွဲမရ) → `NEEDS_PLAYBACK`
  (final မဟုတ်) လက်ခံပါတယ်; widget ရဲ့ 404 → removed final ကို **လက်မခံပါ** (404 တစ်ခုတည်းက
  ဖျက်ထားတာကို မပြနိုင်၊ recovery ကို မရပ်ရ)။ လက်ခံချက်တွေက phone proof၊ regression နဲ့ release gate
  ကို မလျှော့ပါ။
- **Task 0 — X widget ရဲ့ 404 က final မဟုတ်တော့ပါ (2026-10-22၊ flag နောက်မှာ၊ debug only၊ §7.3):**
  decision 21 အတိုင်း X1 က widget ရဲ့ 404 ကို `NEEDS_PLAYBACK` (final မဟုတ်၊ reason မပေး) လို့ judge
  လုပ်ပါတယ်။ ရှိပြီးသား လမ်းကြောင်း (visible tab ရဲ့ ကိုယ်ပိုင် playback၊ ပြီးရင် user ရဲ့ browser) ဆက်ပြီး
  ဘာမှ မတွေ့ရင် main ရဲ့ reason (`NO_MEDIA_FOUND`၊ main ရဲ့ စကား) ကျန်ပြီး Try again ရပါတယ်။ Widget
  request အသစ်၊ X2၊ token self-healing၊ route အသစ် မရှိပါ; main၊ auth gates၊ photo-only final၊
  empty tombstone၊ 429/cooldown၊ flag off မပြောင်းပါ။ App ရဲ့ "This X post is no longer available."
  စကားကို ဖယ်လိုက်ပါတယ် (X ladder မှာ removed final မရှိတော့လို့)။ နောက်တစ်ဆင့်: task 6b။
- **Task 6b — site census က social-ladder build မှာ site တစ်ခုစီရဲ့ ladder ကို run ပါတယ် (2026-10-22၊
  flag နောက်မှာ၊ debug only၊ §7.3):** `yft-d7-social-trial` ရဲ့ "YFT site census" မှာ Facebook၊ TikTok၊ X၊
  Instagram slot တစ်ခုစီရဲ့ Master part က R8 ရဲ့ way အစား site ရဲ့ ladder (census ရဲ့ main lookup က
  pages ပေါ်မှာ၊ S6 file check၊ S7 stops/budgets/cooldown) ကို run ပါတယ်။ Rung တစ်ခုစီရဲ့ state (rows၊
  run၊ not run (cooldown/budget/…)၊ not reached) နဲ့ main ရဲ့ rows နဲ့ တိုက်စစ်ချက် (the same video/item၊
  duration within 1 s၊ **wrong rows** နဲ့ **unknown rows** ခွဲပြီး) ကို report/export မှာ ပြပါတယ်။ Main ရဲ့
  part၊ requests၊ time မပြောင်းပါ (B0 နဲ့ နှိုင်းယှဉ်လို့ရ)။ Export က field တွေပဲ တိုးပြီး SiteCensusDiff က
  အဟောင်းတွေကို ဆက်ဖတ်ပါတယ်; ladder တစ်ဖက်ပဲပါရင် Master part ကို မနှိုင်းယှဉ်ပါ၊ wrong 0 → 1+ က BREAKING။
  Flag off မှာ census ဟာ ဒီနေ့အတိုင်း (byte-identical)။ Site တစ်ခုစီရဲ့ proof က network တစ်ခုစီ census
  run တစ်ခု ဖြစ်လာပါတယ် — wrong rows 0 က parity မဟုတ်၊ release readiness မဟုတ်ပါ။ နောက်တစ်ဆင့်: task 7။
- **Task 7 — Facebook ရဲ့ Master rows 2 နဲ့ main ရဲ့ 3 (2026-10-22၊ tests နဲ့ docs ပဲ၊ behavior မပြောင်း၊
  §7.3):** Master မှာ မပါတဲ့ row က main ရဲ့ **audio row** ပါ — watch page ရဲ့ inline DASH manifest ထဲက
  အကောင်းဆုံး AAC track (`FacebookDashOffers`; manifest ရဲ့ video track တွေက ဒီနေ့ AV1 ဖြစ်ပြီး app က AV1
  merge ကို phone တိုင်းမှာ ပိတ်ထားလို့ main မှာ HD၊ SD၊ Audio = 3)။ F1 (plugin player) မှာ manifest မရှိပါ
  (`dash_manifest` null; prefetch block ပဲ ရှိပြီး S1a ရဲ့ recipe က မဖတ်ပါ)၊ F2 က F1 ဘာမှ မပေးမှ run ပါတယ်။
  S1a ရဲ့ rules ထဲမှာ ပြင်လို့ မရလို့ `docs/SUPPORT_MATRIX.md` မှာ intended difference အဖြစ် ရေးထားပါတယ်
  (count တူအောင် row မဖန်တီးပါ)။ User မြင်တာ: video quality တွေ အတူတူ (the same video yes, duration yes);
  sheet ရဲ့ Audio (M4A, MP3) ကို video ရဲ့ ကိုယ်ပိုင် sound ကနေ ဆက်ပေးပါတယ် (download ပိုကြီး)။ Option
  (ပိုင်ရှင်ဆုံးဖြတ်ရန်၊ မဆောက်ပါ): (a) F1 က prefetched audio representation ကို video ရဲ့ representation ID
  နဲ့ ချိတ်ဖတ် (V စစ်)၊ (b) F1 ရဲ့ rows နောက်မှာ F2 ကို audio track အတွက်ပဲ run (S1a (4) ရဲ့ order ပြောင်း)။
  Link 9 ရဲ့ desktop form ကို trial နဲ့ JVM test (`SocialTrialTest`၊ made-up IDs) နဲ့ pin လုပ်ထားပါတယ်:
  "Master found none" + Details မှာ step ("the page changed during the lookup: the same address
  again")။ Option (မဆောက်ပါ): main ရဲ့ post page မှာ video တစ်ခုတည်းရှိရင် F2 က အဲဒီ video ရဲ့ rows ကို
  ချိတ်နိုင် (request အသစ် မလို)။ နောက်တစ်ဆင့်: task 8 (S3 ရဲ့ weekly check W)။
- **Task 8 — S3 ရဲ့ weekly check W (2026-10-22၊ debug only၊ social-ladder build (flag on) မှာပဲ၊ flag off
  နဲ့ full census မပြောင်း၊ §7.3၊ §7.5):** "YFT site census" မှာ **Weekly check W (all sites)** button
  အသစ် (social-ladder build မှာပဲ; တခြား build ရဲ့ census screen အရင်အတိုင်း)။ Site တစ်ခုစီရဲ့ census slot
  ၃ ခု (case kind အလိုက်၊ link မဟုတ်: Facebook 1, 8, 13; TikTok 1, 6, 9; Instagram 1, 8, 10; X 1, 6, 8)
  ကိုပဲ census ရဲ့ steps အတိုင်း run ပါတယ်: way တစ်ခုကို အများဆုံး တစ်ခါ (main lookup slot တစ်ခုကို
  တစ်ခါ၊ V best row ပေါ် တစ်ခါ၊ Master part က slot 1 မှာပဲ)။ Site တစ်ခုစီ အများဆုံး 60 s၊ 2 MB၊
  requests 24 (step တိုင်း မစခင် စစ်ပြီး time က step တစ်ခုစီကိုလည်း ကန့်သတ်)၊ cap ရောက်ရင် ကျန်တာ
  "not run" (cooldown မရှိ)။ 429 / bot check ဆိုရင် census ရဲ့ 30 min cooldown — "not run"၊ မစောင့်၊
  pass မရေတွက်။ W ရဲ့ baseline က census ရဲ့နဲ့ သီးခြား; diff ရဲ့ CHANGED / BREAKING line တစ်ကြောင်းစီ
  "→ W runbook step N (…)" နဲ့ (§7.5 ရဲ့ runbook: line kind ၈ မျိုး၊ ဘာစစ်ရမလဲ၊ S4 live ဖြစ်ရင် R9 နဲ့
  data ပြင်မလား code ပြင်မလား)။ YouTube ရဲ့ W design ကို ပြန်သုံးပြီး YouTube file မပြောင်းပါ။ S4 မဆောက်ရသေး
  (decision 5 ဖွင့်ထား)။ နောက်တစ်ဆင့်: ပိုင်ရှင်ရဲ့ phone session (§7.4; W ရဲ့ first run က C၊ နောက်ဆုံး)။
- **Task 9 — S6 ရဲ့ row reader က read တစ်ခုစီကို lookup ရဲ့ site နဲ့ စစ် (2026-10-22၊ debug only၊ flag
  နောက်ကွယ်၊ flag off မပြောင်း၊ §7.3၊ §7.4):** 2026-10-22 phone session (A ရဲ့ X census၊ `430da9f` ရဲ့
  `yft-d7-social-trial`၊ signed out၊ VPN on၊ 10:48 UTC) မှာ slots 1–6 ရဲ့ Master trial တွေ "failed NETWORK;
  new 3 (GIF 1), refused 0"၊ ask တစ်ခုစီ "unlabelled GET NETWORK 1–2 ms"၊ ladder rows 0 (main ရဲ့ V က host
  တူမှာ pass 206)။ အကြောင်းရင်း: `OkHttpRowReader` ရဲ့ default site က Facebook ဖြစ်ပြီး app နဲ့ census က site
  မပေးလို့ Facebook မဟုတ်တဲ့ host (X ရဲ့ `video.twimg.com`၊ TikTok၊ Instagram ရဲ့ `cdninstagram.com`) ရဲ့ file တွေကို မပို့ဘဲ fail
  လုပ်ခဲ့ပြီး route B က အဲဒါကို `NETWORK` ask အသစ်အဖြစ် ရေတွက်ခဲ့ပါတယ်။ ပြင်ချက်: site အားလုံးအတွက် reader
  တစ်ခုတည်း (`FACEBOOK` default ဖယ်)၊ read တစ်ခုစီက `SocialRowCheck` ရွေးတဲ့ lookup ရဲ့ site
  (`RowReadSite`) ကို ယူသွားပြီး အဲဒီ site ရဲ့ media hosts ကိုပဲ မေးပါတယ် (hosts မချဲ့; HTTPS only၊
  redirect မလိုက်၊ cookie မပို့၊ 64 KiB၊ 10 s၊ retry မရှိ — အရင်အတိုင်း)။ တခြား site ရဲ့ host ကို ask
  မမှတ်ခင် refuse (မပို့၊ refused ရေတွက်၊ `NETWORK` မဟုတ်)၊ တကယ့် network failure က `NETWORK` ဆက်ဖြစ်ပါတယ်။
  Facebook ရဲ့ behavior နဲ့ ရှိပြီးသား tests မပြောင်းပါ။ နောက်တစ်ဆင့်: phone session မှာ Master ကို X
  အရင်၊ ပြီးရင် TikTok (429 cooldown ပြီးမှ)၊ ပြီးရင် Instagram ပြန်စမ်း; B1၊ A-FB နဲ့ X/TikTok ရဲ့ main
  results တွေ ဆက်တည်ပါတယ်။

## 1. Audit — what exists today

### 1.1 The model: Master YouTube's layers

| Layer | Master YouTube piece |
| --- | --- |
| Parity floor | main's five YouTube files copied byte for byte (drift check) behind `MasterYouTubeModule` |
| Identity table (data) | `ladder/ClientTable`: ≤ 4 identities, status per identity, ≤ 2 alternates with source and date |
| Answer judge | `ladder/AnswerJudge` + `CensusModel` classes (`DIRECT`, `BOT_CHECK`, `VERSION_REJECTED`, …) |
| Ladder | `ladder/ClientLadder` (rungs and variants A, S, H, L, Z), debug flag `-Pyft.clientLadder` |
| Health | `ladder/ClientHealth`: last class, last pass, cooldown, learned version, shape map (stored through `OwnSolverTextFile`) |
| Own parser | `ShapeLocator` (value-shape scan) → `ShapeNormalizer` (bind to the video → canonical JSON → copied parser), `ShapeFingerprint`, `LocatedShapeMap` |
| Own solver | `solver/` (player script; YouTube only) |
| Self-healing values | `VersionSource`, `CensusSources` (public source, strict pattern, once a day, kept after OK + V) |
| Row checks | `StreamCheck` (V), `InitCheck`, `HlsCheck` / `HlsLeg`, rung Z (sizes) |
| Budget and memo | `LookupMemo`, budgets, stop rules after a bot check |
| Early warning | `ClientCensus` (full census, weekly check W, A check) + `CensusDiff` (CHANGED / BREAKING lines, runbook) |
| Live proof | `LadderTrial` (skip / mutate switches) + the D7 phone APK matrix |

### 1.2 Main's four adapters (`extractor-sites`)

| Site | Lines | Ways asked, always in this order | Row check | Hard-coded values | Weak point |
| --- | --- | --- | --- | --- | --- |
| Instagram | 740 | app API (only with the browser's session) → web GraphQL → post page → public embed | none | the GraphQL `doc_id`, app ID, ASBD ID | the `doc_id` rotates; sign-in walls; 429; the same order every lookup |
| TikTok | 1,585 | the tab's (or hidden page's) post data → phone page → desktop page → hidden page → `tikwm.com` (public download service, last) | one-byte check per quality (≤ 8) | page-data block and key names, status codes | a renamed data block; bot checks; a third-party service as the last way |
| Facebook | 1,842 | public Safari page (reels) → session page → AVC ladder | none (link expiry is read) | media key lists (`MEDIA_KEYS`, `LEGACY_FIELDS`) | renamed page-data keys; sign-in walls |
| X | 523 | the embed widget's answer (`tweet-result` with the widget token) | none | the token arithmetic, one endpoint | one way only: if it breaks, main has nothing |

### 1.3 Master today for the four ("generic")

- No site module: `modules/` holds YouTube only.
- After a main failure that is not final (`policy/TerminalRules`), `MasterFallbackEngine` runs the R8
  contract stage — one ask per site from `recipes/ContractRecipes`:
  - **X:** the same `tweet-result` endpoint main already asked (no second way).
  - **Instagram:** the same embed page as main's last way (no second way).
  - **Facebook:** `plugins/video.php` (a real second way).
  - **TikTok:** `embed/v2` (a real second way).
- Then layers L2 → L4 → L1 → L3 on page data and on the user's own playback in the visible browser
  (capture), then `CandidateGate` and `ProbeSession`.
- Sign-in or bot check (`BROWSER_REQUIRED`): Master reads only what the user's own playback delivered.
- R9 remote recipes are built (signed, data only), but no hosted config exists yet, so they are not live.
- R1 live parity list (owner-run): Facebook 5, Instagram 3, TikTok 4, X 4 links.
- **Routing today:** for these sites Master runs only after main fails (`BrowserViewModel` sends a
  `SiteAdapterOutcome.Failed` to `BrowserMasterFallback.recover`). A page claimed by a module
  (YouTube) is answered only when main returned `UNSUPPORTED_URL` (P12, `MasterFallbackEngine`), which
  YouTube's main returns only for an invalid video ID. Phone run 3 hit this (`PROGRESS.md`, "Run 3 on
  the phone": "Master runs only when the primary adapter fails"); the fix was the debug-only
  `masterFirstSite` (h-only APK), which hands a found YouTube page to Master as `UNSUPPORTED_URL`
  (two lookups). A module with a copy of main as rung 0 would never answer these four sites in normal
  builds.
- **Stop rules today:** `TerminalRules.NEVER_FALLBACK` (`RATE_LIMITED`, `NETWORK`, …) reads only main's
  final reason, and `MasterRequest` carries only `primaryFailure`. Instagram's main asks its next way
  after a 429; a later 404 or 5xx makes the final reason `RESPONSE_CHANGED`, so Master never sees the
  429.

### 1.4 Gaps: the YouTube layers against the four sites

| Layer | Instagram | TikTok | Facebook | X |
| --- | --- | --- | --- | --- |
| Answer judge (class → next step) | failure reason only | status codes (`TikTokStatusRecipe`) | failure reason only | failure reason only |
| Ladder + health (last good way first, cooldown) | no | no (fixed order) | no | no (one way) |
| Own parser (find by value shape, bound to the post) | no (generic L3 only) | no | no | no |
| V on every offered row | no | yes (one byte) | no | no |
| Init check / size from the check | no | no | no | no |
| Self-healing values | `doc_id`: no | data block found by content: no | n/a | token: no |
| Census + weekly check W | no | no | no | no |
| Repair without an APK (R9 live) | no | no | no | no |
| Second independent way in Master | no | yes (embed) | yes (embed) | no |

## 2. The answer and the shape of the build

**Yes**, as one shared **Next Level kit** and four **thin site packs inside the generic engine** — not
four YouTube-size copies, and no module that claims a site.

1. Each site pack (mostly data): the site's R8 contract recipes (data, fixable through R9), its
   judge rules, L3 anchors bound to the post, the media hosts for row checks, and its extra rungs
   (§6). Main's adapter is **not copied** (no rung 0, no ~4,700 copied lines): main answers first,
   as today, and Master acts only after main fails (decision 7, §2.1). The generic layers and capture
   stay after the pack. Repeats inside one lookup are stopped by the per-lookup request recorder, the
   ledger and the stop rules (decision 8); the memo only replays successes. (YouTube's module, R6, is
   not the model: it answers only after main returned `UNSUPPORTED_URL`, so in normal builds it
   practically never answers, §1.3.)
2. **Measured first** (S0 census), as YouTube's D0 was.
3. **Behind a debug flag first** (`-Pyft.socialLadder`), proven on the phone with a D7-style APK matrix,
   then promoted per site only on the owner's go.
4. **Not ported:** the own solver (these sites do not cipher their media links, and signing their APIs
   — e.g. TikTok's request signatures — is excluded), client versions (their counterpart here is a
   self-healed request value), SABR / PO tokens.

### 2.1 Final decisions (the owner, 2026-10-17)

**Decision 7 — routing: B first, B+ later, A only if measured.**

| Route | When Master acts | Main's rows | Status |
| --- | --- | --- | --- |
| **B** | only after main fails with a reason that is not final (`TerminalRules`), all four sites | returned untouched when main succeeds | decided: build first |
| **B+** | also when main's answer is weak by an objective signal (no HD row, no sound row) | untouched; Master adds only the formats main lacks (YouTube's merge rule, guardrail 9 in `ClientLadder`), inside the budget | later, on the owner's go, after B's phone proof |
| **A** | in place of main | replaced | only if B+ is measured insufficient |

- **Why B:** "no regression" becomes structural (main's success is never touched), the copies are not
  needed, and today's engine order already fits (R8 contract → page data → capture,
  `MasterFallbackEngine.run`). Facebook's `plugins/video.php` and TikTok's `embed/v2` are real second
  ways today; X's `tweet-result` and Instagram's embed repeat main's ask, so under B their first fix
  is to stop that repeat.
- **If A is ever needed:** a real "Master-owned" route state in the app and the engine. Never through
  `ADAPTER_DISABLED` (it is in `TerminalRules.NEVER_FALLBACK`, so Master would never run) and never by
  relabeling a found page as `UNSUPPORTED_URL`, as the debug-only `masterFirstSite` does.
- **One project-level contract:** the routing rule covers YouTube too (its `masterFirstSite` stays a
  debug-only trial); a change to it is one decision for all sites, not a per-site trick.

**Decision 8 — "lookup" = the user's whole lookup (main + Master), seen by a request recorder.**

- An app-side **per-lookup request recorder** wraps the HTTP client that main's adapters already use
  (no main adapter code change; scoped to the lookup, e.g. through the coroutine context). Memory
  only (S8): the hosts that answered 429 or a bot check, short hashes of the requests asked, the time
  left.
- Master's contract stage reads it: an endpoint main already asked is not asked again; a host that
  answered 429 or a bot check gets no new request in this lookup (S7).
- All kit requests go through one per-lookup client: the memo replays successes; the ledger and the
  stop rules count attempts.
- The same recorder feeds the S0 census (per site: which way failed, how, and how often a failure was
  recoverable).
- For these sites this replaces R8's "Cost accepted: after a main failure the same endpoint may be
  asked a second time (Vimeo, X)" (`MASTER_KEY_ARCHITECTURE.md`).

**Decision 9 — R8 becomes a small bounded ladder, per site pack.**

- R8's single ask ("asks once … never twice") becomes, for these four sites, a small budgeted ladder
  in the contract stage: the engine fetches; the parser layers only read and never fetch.
- Each new rung still needs the owner's go (S10); rungs, endpoints and key paths are data (S9).

## 3. Reuse — what already exists (owner's question, 2026-10-17)

Rule: **no Master YouTube file is edited** (the five copies, `ShapeLocator`, `ShapeNormalizer` and
`Rungs` are locked; the other YouTube files stay as they are so YouTube's proven behaviour cannot
move). Reuse is by calling a piece as it is, by copying it with a provenance row (drift check), or by
following its design in new kit code. Main's code reaches Master only as symbol copies with a
provenance row (R3, `toolkit-provenance.tsv`; `extractor-master` uses `extractor-sites` for tests
only); there is no whole-adapter copy (decision 7).

### 3.1 Reused as it is

| Piece | Where | Used for |
| --- | --- | --- |
| The four site adapters, their URL readers (`InstagramUrls`, `TikTokUrls`, `FacebookUrls`, `XUrls`) | `extractor-sites` (main) | main's own first answer, unchanged and not copied (B); the URL readers for the post's identity |
| Facebook DASH reader and offers (`FacebookDashManifest`, `FacebookDashOffers`) | main (Instagram already uses them) | DASH rows with their sound on Facebook and Instagram |
| `TikTokAgents`, `TikTokStatusRecipe`, `WidgetToken` (X token arithmetic) | main / Master | agents, status codes, X's token |
| Embed recipes and their reader (`ContractRecipes`, `ContractReader`) | Master generic | rung F1 (`plugins/video.php`), T1 (`embed/v2`); key tables, DRM signals |
| Toolkit (`AnchoredMediaWalk`, `MediaKeyTable`, `BalancedJson`, `PageScripts`, `HtmlScan`, `InlineDashReader`, `QualityLadder`, `UrlPolicy`) | Master generic | the base of the own parser (L3): the walk, JSON blocks in pages, inline DASH |
| Row checks: `InitCheck` (a pure check on bytes already read), `ProbeSession`, `OkHttpMediaValidator`, `CandidateGate`, `SegmentIndexReader`, `MediaFingerprint` | Master YouTube / generic | the init check on V's bytes, probe budget, gate, fingerprints |
| Capture: L1 `CaptureLayer`, the Android capture (`yft-master-mse.js`, codec steering, EME stop) | Master generic | rung F3 / T3 / I3 / X3 (the user's own playback after a sign-in wall or a check) |
| Download side: `DashTransferEngine`, `HlsTransferEngine` (audio playlists, packed audio), `AudioVideoMuxEngine`, `StreamCopyAudioVideoMuxer`, `Mp4StreamCopyReader`, `AdtsStreamCopyReader` | core-download | every new row type downloads with no new download code (DASH pairs, HLS, ADTS sound) |
| `TerminalRules`, `SnapshotBudget` | Master policy | L0 |
| R9 remote recipes (`RemoteRecipes`, `RemoteRecipeConfig`, `RemoteRecipeSignature`, `scripts/master-recipe-sign.sh`) | Master | L8 (only hosting is missing) |
| Parity harness (`ParityUrls`, `ParityReport`, `ParityVerdict`, `MasterParityLiveTest`, `parity-urls.json`), `FixtureBaselineTest`, `toolkit-provenance.tsv` + `scripts/master-toolkit-drift.py` | Master | no regression under the same policy (S1), drift of copied symbols |
| App: `SiteAdapterCoordinator`, `PageDataSources` and TikTok's hidden pages (`WebViewHiddenPages`), `InstagramHomeSession` / TikTok's home session (the browser's cookies), Show check, `BrowserMasterFallback` | app | wiring, page data for rungs, the user's own session, the user answering a check; the place of the per-lookup request recorder (decision 8) |
| CI: the D7 APK matrix (`master-optin-debug-apk.yml`, `.github/d7-apks.txt`) | repo | phone proof per rung (new matrix entries only) |

### 3.2 Reused with a small generalization (new kit code; the YouTube file stays unchanged)

| YouTube piece | What is YouTube-only in it | Kit counterpart |
| --- | --- | --- |
| `StreamCheck` (V) | only YouTube's media host (`YouTubeUrls.isMediaServerRequest`) | `SiteRowCheck`: the same rules (Range ≤ 64 KiB, the download's headers, returned hosts only, `unknown` ≠ pass) with the site's media hosts from the site table |
| `HlsCheck` | addresses must be on `googlevideo.com` | the same checks with the site's host (X's HLS) |
| `LookupMemo` | `googlevideo.com` is the media host it never keeps | the same memo with the site's media hosts (it replays successes only); main's asks are seen through the request recorder, attempts through the ledger and the stop rules (decision 8) |
| `ClientHealth` | ids of the YouTube client table only | `SiteHealth`: per site and rung (last class, last pass, cooldown, last good rung, learned value), stored the same way (`OwnSolverTextFile`) |
| `CensusModel` (`CensusJson`), `CensusDiff` | YouTube's classes and steps | the same JSON export (slots only) and diff (CHANGED / BREAKING + runbook) for site rungs |
| `VersionSource` + `CensusSources` | YouTube version headers and sources | `ValueSource`: the same rules (S5) for Instagram's `doc_id` and X's token |
| `LadderTrial` | YouTube rung names | the same skip / mutate switches with site rung names |
| `ClientCensusActivity` (debug screen) | YouTube's census modes | `SiteCensusActivity` (S0), same layout and export |

### 3.3 Design reused, code new (site-specific)

| YouTube piece | Why it cannot be called | New site code |
| --- | --- | --- |
| `AnswerJudge` | reads YouTube's player answer | one small judge per site (§6 signals) over the shared classes (§5 L2) |
| `ShapeLocator` / `ShapeNormalizer` / `ShapeFingerprint` | YouTube format fields (`itag`, `mimeType`, …), locked files | site anchors (post ID, media host, size fields) on top of the generic walk; fingerprint of each site's answer |
| `ClientLadder`, `ClientTable` | YouTube identities and rungs | one small ladder over the site table (rungs per site) |
| `ClientCensus` | YouTube's steps | the site census (S0) and W (S3) |
| `LookupMemo` + budgets (attempts) | they see only Master's own asks | the per-lookup request recorder (app side, memory only): main's and Master's asks, the hosts that answered 429 or a bot check, the time left (decision 8) |

### 3.4 Not reusable

- The own solver (`solver/`), `SignatureGate`, `HlsLeg`, rung Z's YouTube parts, PO-token and SABR
  pieces: they solve YouTube-only problems.

**Estimate:** about two thirds of what this plan needs exists already (3.1 + 3.2). The new code is mainly
the site judges, the post binding of the parser, the site census, the request recorder and the glue.
With no adapter copies (B), the build is also about 4,700 lines smaller than the first draft.

## 4. Guardrails (S1–S10)

- **S1 Flag and parity.** Flag off = today. Main's adapters are not copied (B) and not changed;
  parity tests unchanged; `FixtureBaselineTest` holds (rows and heights only grow); no Master YouTube
  file changes. "Never worse than main" is not a promise this plan can make; it is split into:
  (1) **no regression under the same policy and config** — under B this is structural (main's
  success is returned untouched); requests and time per lookup are still measured, and capture stays
  the last fallback; (2) **intended policy differences**, listed per site in each step's docs: no
  third-party service in Master (S4; main's TikTok `tikwm.com` way has no Master counterpart), a row
  only after V passes (S6; `unknown` is not a pass), and the stop rules (S7; e.g. Instagram's main
  keeps asking after a 429, Master does not).
  **Exception (the owner, 2026-10-20; §8 item 10):** a fix in main's adapter code only as its own
  commit with its own tests, on the owner's go for that fix (X's card: go; Facebook's access check:
  after §7.3 item 0.3; Instagram: after item 0.2); the site is re-baselined after it (§9).
- **S2 The user's own session only.** Only the cookies of YFT's browser, only to the site's own host
  (as main does), never to a CDN. YFT never signs in. No sign-in, age, private or region bypass;
  DRM → stop.
- **S3 No signing or tricks.** No request-signature generation, no device registration, no TLS,
  fingerprint, IP or proxy changes, no automatic captcha answers (the user answers a check in the
  visible browser, as main's "Show check").
- **S4 No third-party services in Master** (download sites, mirror APIs). Main's `tikwm.com` stays
  main's decision.
- **S5 Self-healed values** (guardrail 5's rule): only from a source S0 proved (the site's own page or
  script, or a public raw file such as yt-dlp's); one pattern-matched value, never code; at most once
  a day per value; kept only after an OK answer and a V pass; bundled alternates with source and
  date; never invented, counted up or brute-forced.
- **S6 Row checks.** V on every row Master offers; main's rows stay main's (B). V: Range ≤ 64 KiB,
  the download's own headers, the host the answer gave; the init check on the same bytes (container
  start, no encryption marker, size match); `unknown` is never a pass; an HLS row only with a known
  duration (±2 s).
- **S7 Budgets and stop rules.** "Lookup" = the user's whole lookup, main's asks and Master's
  (decision 8). Per lookup: a request, byte and time budget per site. After a bot check or HTTP 429
  (seen by the per-lookup request recorder, main's asks included): no new request to that host in
  this lookup, and a 30-minute cooldown for the rungs that send requests. No second ask of an
  endpoint already asked in the same lookup, by main or by Master (recorder hashes); the memo only
  replays successes, so the recorder and the ledger enforce this.
- **S8 Privacy.** No post IDs, links, titles or user names in docs, logs or census exports (slots and
  short hashes only, as the YouTube census does).
- **S9 Data over code.** Endpoints, key paths, header values and values like `doc_id` are data (site
  table, `ContractRecipes`, R9), so most fixes are data changes.
- **S10 Owner gates.** Each site pack, each new rung and each self-heal source starts only on the
  owner's go; a phone proof comes before "live".

## 5. Layers L0–L8

| Layer | What it does | Built from (§3) |
| --- | --- | --- |
| **L0 Policy** | routing B (Master only after main fails, decision 7), final answers, `BROWSER_REQUIRED`, walls, S1–S10 | `TerminalRules` + per-site stop classes + the request recorder's stop signals |
| **L1 Site table** (data) | per site: the rungs, each with status (live, provisional, measured but not active), the values it needs (with source and date), the expected class and its budget share | the `ClientTable` design |
| **L2 Answer judge** | one class per answer: `DIRECT`, `PARTIAL` (fewer heights), `LOGIN_WALL`, `BOT_CHECK`, `RATE_LIMITED`, `REMOVED`, `PRIVATE`, `AGE_LIMITED`, `REGION_LIMITED`, `PHOTO_ONLY`, `DRM`, `VALUE_REJECTED` (a stale `doc_id` or token), `SHAPE_CHANGED`, `TRANSIENT`; decides the next rung or the stop | the `AnswerJudge` design; main's reasons and `TikTokStatusRecipe` map in |
| **L3 Own parser** | value-shape scan for media objects (an https link on the site's media host with width, height, bitrate or codec; a DASH representation; an HLS playlist), bound to the post's ID (Instagram shortcode or pk, TikTok item ID, Facebook video ID, X post ID) → canonical rows (main's readers only as symbol copies with a provenance row, R3); reads only, never fetches; shape fingerprint and map recorded | toolkit walk + `InlineDashReader` + the `ShapeNormalizer` design |
| **L4 Ladder + health** | the contract stage's small ladder after main fails (decisions 7, 9): the last good rung first (health), then table order; one try per rung per lookup; cooldowns; budget; recorder + memo; Details lines name each rung's class | `SiteHealth`, memo (3.2), request recorder (3.3) |
| **L5 Row checks** | V + init check + size + link lifetime (Facebook/Instagram `oe`, TikTok `x-expires`); later (app side): find the links again before a download when they expired | `SiteRowCheck`, `InitCheck`, `ProbeSession` |
| **L6 Self-healing values** | Instagram's GraphQL `doc_id`; X's token self-check (X2 only on the owner's go); TikTok and Facebook: the data block found by content, so no value is needed | `ValueSource` (3.2) |
| **L7 Early warning** | **site census** (owner-run, a test list per site kept on the phone, slots only) and **weekly check W** (≤ 1 min, ≤ 2 MB per site) with a diff: CHANGED / BREAKING lines, each with a runbook step | `CensusJson`, `CensusDiff`, `SiteCensusActivity` (3.2) |
| **L8 Repair channel** | R9 remote recipes live (the owner signs and hosts the config), a kill switch per rung; the bundled defaults stay the fallback | R9 (built) |

## 6. Per-site plans

### 6.1 Facebook

- **Rungs:** F0 = main itself, first and not copied (public Safari page → session page → AVC ladder).
  After main fails: F1 `plugins/video.php` (today's R8, now judged and read by L3); F2 L3 rows from
  any page already read when key names moved; F3 the visible tab's own playback (capture) after a
  sign-in wall.
- **Judge signals:** a sign-in or checkpoint redirect → `LOGIN_WALL`; "content isn't available" →
  `REMOVED` / `PRIVATE`; `drm_info` or Widevine → `DRM` (stop); page data with no media object →
  `SHAPE_CHANGED`.
- **Anchors:** the video ID; media objects = a Facebook media-host link with bandwidth, width and
  height; the inline DASH manifest's representations.
- **Row checks:** V and init check on each offered row (≤ 8); lifetime from `oe`.
- **Census list (the owner's, on the phone):** video, reel, watch link, share link, post with a video,
  group post, live replay, sign-in-only, removed.

### 6.2 TikTok

- **Rungs:** T0 = main itself, first and not copied (tab or hidden-page data → phone page → desktop
  page → hidden page → `tikwm.com`; Master has no counterpart of `tikwm.com`, S4). After main fails:
  T1 `embed/v2` (today's R8, judged); T2 L3 rows when the data block or its keys move (the block is
  found by content — the one holding the item ID and a video object — not by its script ID); T3 the
  visible tab's own playback.
- **Judge signals:** the status code (`TikTokStatusRecipe`: removed, private, region); a verify or
  captcha page → `BOT_CHECK` (the user answers it with Show check); a photo post → `PHOTO_ONLY`; 403
  on the files → files refused.
- **Health:** for Master's rungs (T1–T3) only. The phone / desktop page order inside T0 is main's
  (changing it is main's decision, or route A).
- **Row checks:** main's one-byte check stays on main's rows; Master's rows get V, the init check and
  the size; codec label (H.264 / H.265).
- **Census list:** single video, long video, photo post, private, region-limited, removed, a post
  only the desktop page lists in HD.

### 6.3 Instagram

- **Rungs:** I0 = main itself, first and not copied (app API with the session → GraphQL → post page →
  embed). First fix under B: Master no longer repeats main's embed ask (recorder). After main fails:
  I1 GraphQL with a self-healed `doc_id` after `VALUE_REJECTED` (decision 3; S5: from the source S0
  proves — Instagram's own page scripts or yt-dlp's raw file; a strict digits pattern; once a day;
  kept after OK + V); I2 L3 rows when the answer's keys are renamed (`shortcode_media` →
  `xdt_shortcode_media` style moves, `items[]`); I3 the visible tab's own playback.
- **Judge signals:** `require_login` or a checkpoint → `LOGIN_WALL`; 429 or "wait a few minutes" →
  `RATE_LIMITED` (cooldown, no more Instagram requests this lookup); a GraphQL error or empty `data`
  for a valid post → `VALUE_REJECTED`; photos only → `PHOTO_ONLY`.
- **Health:** after `RATE_LIMITED`, Master skips its GraphQL rung (I1) for 30 minutes and goes to
  I2 / I3.
- **Row checks:** each row; DASH rows check the video and its sound.
- **Census list:** single video, reel, carousel with videos, photo-only, sign-in-only, removed.

### 6.4 X

- **Rungs:** X0 = main itself, first and not copied (`tweet-result` + widget token). After main fails:
  X1 the token self-check (the computed token against a census-proven answer; an alternate formula
  only from a public source with a date); **X2 only on the owner's go:** X's signed-out web API
  (`TweetResultByRestId`), which needs the public web bearer and a guest token — against the "no
  token" rule unless the owner allows it; X3 the visible tab's own playback (the user's own X
  sign-in; capture).
- **Judge signals:** tombstone (age or sensitive) → `AGE_LIMITED` (browser required); protected →
  `PRIVATE`; 429 → `RATE_LIMITED`. The plan's "404 or an empty answer → `REMOVED`" is not built:
  `{}` → `VALUE_REJECTED` (S2c), and the 404 → needs playback, never final (task 0, decision 21: a
  404 alone does not prove the post was deleted). (S2c as built, by the live shapes of 2026-10-21:
  §7.3.)
- **Row checks:** each MP4 row on X's media host; HLS only with a known duration.
- **Recorder (first fix under B):** Master's R8 for X asks main's endpoint a second time today; the
  site pack stops that first (decision 8). A new way only through decision 2 (X2).
- **Census list:** video, GIF, quoted post's video, several videos, age-limited, removed.

## 7. Steps (each: build → tests → CI → APK → phone → docs)

| Step | Size | What | Done when |
| --- | --- | --- | --- |
| **S0** | M | The per-lookup request recorder (app side, memory only; decision 8) + the site census tool (`SiteCensusActivity`, like "YFT client census"): each way of each site asked once on the owner's test list, judged, shape fingerprint, V on one row; the recorder's counts (which way failed and how, 429 / bot checks, repeated asks); JSON export with slots only | the owner's run gives each site's share of recoverable failures → the pilot and the order (§8 item 1) — **done** (§7.1, §7.2) |
| **Stage 0** | M | Five items, in parallel, before S1a's first phone APK (§7.3): **0.1** X's card fix in main (the owner's go); **0.2** the Instagram check (no code); **0.3** Facebook's access-check evidence (no code); **0.4** the census fixes and the site census diff (debug builds only); **0.5** the owner's baseline run B0 (§9) | 0.1 and 0.4: CI green and the census APK; 0.2 and 0.3: a verdict each (§7.3); 0.5: B0 recorded |
| **S1a** | L | The kit's minimum with the pilot's pack, Facebook (the first draft's S1 and S2a together), flag `-Pyft.socialLadder` (off = today): the recorder in the app's lookup path, judge classes, the site table, one judged request rung + L3 + capture, budgets and the 30-minute cooldown (S7), `SiteRowCheck` + `InitCheck` on every Master row, Facebook's anchors, post-identity mutation tests, a simulator world (§7.3) | JVM tests, simulator and CI green; flag off = today; the D7-style APK's phone proof (§7.3) — **passed (2026-10-21, §7.4 step 6's re-run)** |
| **S2b** | M | TikTok's pack (§7.3) | phone proof; wrong rows 0; no regression against B0 — built (2026-10-21, behind the flag); its proof: the next phone session (§7.4) |
| **S2c** | M | X's pack: main's answer replayed, no new ask (X2 not now; §7.3) | the same — built (2026-10-21, behind the flag); its proof: the next phone session (§7.4) |
| **S2d** | M | Instagram's pack by the Instagram check's verdict (0.2); the owner's signed-in census first (§7.3) | the same — part 1 built (2026-10-22, behind the flag: I2 and the tab-fetch probe, off by default); its proof and the probe's result: the next phone session (§7.4); part 2 (the probe's rung) only after that result |
| **S3** | S–M | Weekly check W for all four + runbook (each CHANGED / BREAKING line and its fix: a data change via R9, or code) | the owner's first W run — built (2026-10-22, debug only, a social-ladder build: the census's "Weekly check W"; its runbook §7.5); its first run: the next phone session (§7.4, C) |
| **S4** | S | R9 live: the owner signs and hosts the config (§8 item 5, decided before S4); kill switch per rung; one repair drill (a key changed by config on the phone, no APK) | drill passed |
| **S5** | — | Promote per site after 3 phone runs on different days with 0 wrong rows and no regression against B0; then the release gate (§7.3): all four sites, the first W run and the R9 drill passed, the intended differences in `docs/SUPPORT_MATRIX.md` → PR #2 merged → P8 (beta.4) | the owner's go |
| **S1b** | M | After the release, on the owner's go: the rest of the kit — several request rungs per site with `SiteHealth` (the last good rung first) and self-healed values (L6). Needed only if X2 is allowed or Instagram's `doc_id` needs self-healing: Facebook, TikTok and Instagram each have one request rung in S1a–S2d | JVM tests; phone proof per site |
| **S6** | M | **B+** (the owner's go, after the release and B's phone proof): when main's answer is weak by an objective signal (no HD row, no sound row), Master adds only the missing formats; main's rows stay (merge rule); budgeted | phone proof; **A** only if B+ is measured insufficient (decision 7) |


### 7.1 S0 — as built (started 2026-10-20, the owner's go)

S0 measures only: it changes no lookup. Main's adapters, the app's lookups and Master's engine stay
as they are; Master's way in the census is today's R8 contract of each site; the census screen is
an owner tool of debug builds (nothing in release or preview).

| Sub-step | What | Done when |
| --- | --- | --- |
| **S0a** | The recorder's labels (`LookupRecord`, additive: a record given none is today's, so route B and every lookup stay as they are): each listed ask gets its way, its reading of the answer and a short shape fingerprint; the record counts repeated asks (an endpoint already asked in the lookup), asks after a stop (a host that answered 429 or a bot check, asked again) and bot checks, and names the hosts that stopped. The site table (`SiteWays`): each site's way labels, media hosts and readings, its census kinds and what each kind expects | JVM tests; the route B tests unchanged — **built (2026-10-20)**: 41 slots (Facebook 13, TikTok 10, Instagram 10, X 8), drift tests against main's adapters |
| **S0b** | The census engine (`SiteCensus`) and the one-row check (`SiteRowCheck`, V) | JVM tests with fake sites — **built (2026-10-20)**: the row rules, the stops and budgets and the privacy guard below; V checks a DASH row's tallest video file through its manifest; 24 JVM tests |
| **S0c** | The debug screen "YFT site census" (`SiteCensusActivity`, like "YFT client census"): the owner's links per slot, a run per site or all, the report and its JSON export | CI green; APK — **built (2026-10-20)**: a link field per slot (kept on the phone), "Run <site>" / "Run all sites", "Copy report" / "Copy JSON", the state file `files/master/site-census-last.json`; main as Home runs it (`SiteCensusLookup`: the app's adapter coordinator, fresh, TikTok's hidden page, the browser's identity) on clients with no retry, wire bytes counted; `WebViewAgent` copied from main; 5 JVM tests and the no-background guard |
| **S0d** | The owner's run on the phone; the shares recorded here and in `PROGRESS.md` | the pilot and the order (§8 item 1) — **run (2026-10-20)**: two runs, §7.2; the pilot and the order wait for the owner |

**Row rules** (a row is one link of the owner's list, in its slot):

- **Main:** main's own lookup with the four adapters as the app builds them (TikTok's hidden page
  and `tikwm.com` included), fresh (no remembered answer), with the browser's identity: the agent
  YFT's tab sends and the browser's cookies for the post's link (the report says only whether a
  session was there). There is no tab, so no tab data: the page read, then the hidden page, as main
  does. The census's clients retry nothing (each ask once; main's own order of ways stays); at most
  90 s per row (Home's limit). Every ask is listed with its way, status, reading and shape, and the
  ways main did not reach are named.
- **Master:** today's R8 way of the site, once, under route B's rules in the same record (main's
  identical unauthenticated answer replayed, an endpoint main asked not asked again, nothing new
  after a 429; all counted). After main's failure with a reason Master's engine answers (not final,
  not browser-required), as route B would. After main's success or the site's final answer about
  the post (private or removed, region), once as a trial (main's failure given as `NO_MEDIA_FOUND`;
  marked "trial"). Not after a browser-required reason (the engine asks no contract then), a network
  failure, a rate limit, a bot check or main's time limit, and not for a short link Master's
  contracts do not read.
- **V:** one row of each answer (main's best, Master's best: the tallest, then the highest
  bitrate) gets a one-byte check with the headers the download sends for that row, only on the
  site's media hosts. An HLS row or any other host is `unknown`, never a pass; a DASH row checks its
  video file.
- **Judge:** each ask's reading (data, no post, no data, not JSON, login page, bot check) and each
  row's class from main's reason as Master's engine sees it — **recoverable** (Master's contract
  would be asked), **browser** (`TerminalRules.BROWSER_REQUIRED`), **final**
  (`TerminalRules.NEVER_FALLBACK`) — against what the slot's kind expects (§6's lists: video kinds
  expect rows; removed, private, photo-only and sign-in-only kinds expect a final answer, and a
  different answer is "not as expected"). Each site's number: main's recoverable failures ÷ the
  rows that should download, and how many of them Master's way recovered (rows that passed V).

**Stops and budgets:** a 429 or a bot check (any ask, the hidden page included) stops that site for
the run and for 30 minutes after (the screen's state file); at most 40 MB and 150 requests per site
(the census's own clients; the hidden page's own loads are neither counted nor listed, and the
report says so).

**Privacy (S8):** the links stay on the phone (the screen's own store); the report and the export
hold only sites, slot numbers, kinds, way labels, classes, counts, sizes and short fingerprints; a
last guard masks any address and the given values (the link, the post ID, handles).

**Not in S0** (the owner's decisions): measuring Instagram's `doc_id` sources (§8 item 3; S0 shows
only whether main's GraphQL answers the post), X2 (§8 item 2), any new way.

### 7.2 S0d — the owner's run (2026-10-20)

The owner ran "YFT site census" on the phone (the S0c commit's APK) with the owner's public test
posts (kept on the phone; 40 of the 41 slots, TikTok's private slot empty):

- **Run 1** (02:14 UTC): all four sites, 40 rows, 192 s, mobile data, through the owner's VPN with
  a US exit (TikTok needs a VPN on the owner's network).
- **Run 2** (02:39–02:43 UTC): Facebook, Instagram and X without the VPN; TikTok not run.

No 429, bot check, stop or time limit in either run; the most one site used is 67 requests and
5.9 MB (the budgets: 150 and 40 MB). Every row main gave passed V (Facebook 11, TikTok 6,
Instagram 6, X 4; fail 0, unknown 0).

| Site | Run | Should download | Recoverable | Master recovered | Browser | Not as expected | Route B | Trials (with rows, V pass) | Requests · MB · s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Facebook | 1 | 11 | 0 | 0 | 0 | 1 (slot 12) | 1 | 8 (8, 8) | 67 · 5.6 · 75 |
| Facebook | 2 | 11 | 0 | 0 | 0 | 2 (slots 12, 13) | 1 | 8 (8, 8) | 67 · 5.9 · 74 |
| TikTok | 1 | 6 | 0 | 0 | 0 | 0 | 1 | 8 (6, 6) | 59 · 1.6 · 65 |
| Instagram | 1 | 7 | 0 | 0 | 1 | 2 (slots 5, 10) | 1 | 6 (1, 1) | 38 · 2.1 · 31 |
| Instagram | 2 | 7 | 0 | 0 | 1 | 2 (slots 5, 10) | 1 | 6 (1, 1) | 38 · 2.1 · 33 |
| X | 1 | 6 | 2 | 0 | 0 | 2 (slots 3, 6) | 4 | 4 (4, 4) | 38 · 0.1 · 21 |
| X | 2 | 6 | 2 | 0 | 0 | 2 (slots 3, 6) | 4 | 4 (4, 4) | 38 · 0.1 · 11 |

Findings (both runs, unless said):

1. **X — videos only in the card (slots 3 and 6).** The embed's answer (`tweet-result`, 200)
   carries these posts' videos only in the post's card (`card.binding_values.unified_card.string_value`:
   JSON inside a string; card types `video_website` and `video_multi_dest_carousel_website`), not
   in `mediaDetails`, which main's reader (`XSyndication.parse`) and Master's X contract
   (`mediaDetails/*`, then the quoted post) read; main says `RESPONSE_CHANGED`. Under route B
   Master's engine found rows in main's replayed answer (no new ask): 4 and 8 rows, tallest 720,
   through its shape layer (L3 reads JSON inside strings). Its best row was HLS, so V said
   `unknown` and the census counts no recovery. The card also lists MP4 files up to 720p (checked
   off the phone). The age-limited and removed slots (7, 8) read "no post" → `NO_MEDIA_FOUND`, as
   expected. A small fix in main's X reader (the card's videos) would make both main's rows; only
   on the owner's go, not part of S0.
2. **Instagram — signed out, the embed page is the one way.** Main's GraphQL answered 401 on 10 of
   10 (no session); main gave 6 of the 7 video rows: 5 from the embed page (one shape,
   `hd098d70a`), the carousel's from the post's page. Slot 5 (a reel): the embed page had no data (`h8295e9f2`) →
   `LOGIN_REQUIRED` (browser), so Master is not asked (signed out, off the phone, yt-dlp found it
   as DASH). Slot 10 (removed): the embed page answers as for the photo-only and sign-in-only
   slots (`h484533f7`) → `LOGIN_REQUIRED`, not a final answer. Slot 7 (three videos): main 1 row,
   Master's trial (the embed page) 1 row, tallest 720. Master's other five trials were refused (its
   way is the embed page main had read; one page per lookup), so Master adds nothing on Instagram
   today; a gain needs another way (§8 item 3).
3. **Facebook — every video row; two wrong readings.** Main gave rows on 11 of 11 video slots;
   Master's plugin player gave rows on 8 of 8 trials, V pass 8. Slot 12 (sign-in-only; the link
   itself is not verified, it may be gone) reads as `RESPONSE_CHANGED` (recoverable) on a page of
   the removed slot's shape (`h0cd5c634`); Master's route B (the plugin player) found nothing
   ("needs playback"). Slot 13 (removed): `PRIVATE_OR_UNAVAILABLE` (final) with the VPN,
   `NO_MEDIA_FOUND` (recoverable) without it, the same page shape: main's access markers
   (`FacebookPageParser`) are mostly English phrases and the page's wording likely differed, so a
   removed post shows as "no media". Master's way did not run on slots 8, 9, 10 and 13 (a share
   link, a post with a video, a group post, the removed post): links main resolves first
   (`requiresCanonicalResolution`), which Master's contracts do not read.
4. **TikTok (run 1) — all as expected.** 6 of 6 video rows; main's tallest 1920 on slots 1 and 10
   (the desktop page), Master's embed player at most 1280; region-limited `GEO_RESTRICTED` and
   removed `PRIVATE_OR_UNAVAILABLE` (final; the download service 403, Master's embed player 400);
   the photo post `NO_MEDIA_FOUND` (Master's route B the same). Main's desktop page after the phone
   page counts as a repeated ask (the same endpoint) on 7 of 9 rows.

**Reading for §8 item 1:** the shares do not separate Facebook and TikTok (0 of 11, 0 of 6): main's
own ways answered every video post. S0's only recoverable failures are X's two card posts (a fix in
main's reader). Under route B's rules Master repeated no ask (X: main's answer replayed; Instagram:
refused). Recommended: the pilot **Facebook** (the most kinds and ways; Master's way gives rows on
8 of 8; its gaps are S2a's work (now S1a, §7.3): the links main resolves first, the access check by wording, the
plugin player's empty answer), then TikTok, X, Instagram (after item 3). Waiting for the owner.

### 7.3 The final plan of the steps (the owner, 2026-10-20)

After S0d the owner approved this plan of the steps; it records §8 items 1, 2, 4, 6 and 10–12.
Each step still starts on the owner's go (S10), and a phone proof comes before "live". The first
draft's S1 (the kit) and S2a (the pilot pack) are now one step, S1a; the rest of the kit is S1b,
after the release.

**Stage 0** — five items in parallel, before S1a's first phone APK (S1a's JVM work may start
earlier):

| Item | What | Done when |
| --- | --- | --- |
| **0.1 X's card fix (main; the owner's go)** | In main's X reader (`XSyndication.parse`): the post's own `mediaDetails` first, then the post's own card (`card.binding_values.unified_card.string_value`, JSON in a string → its `media_entities`, read by the existing `videoOf`), then the quoted post as today; the quoted post's card is not read. A card's several videos keep the card's own order (its own media list, not the key order of `media_entities`). The choice (`/video/N`, else the first), MP4 first and the host guard stay as they are. X's age-limited and removed posts keep `NO_MEDIA_FOUND` (by design: the generic detector stays reachable). Synthetic fixtures for both card types (`video_website`, `video_multi_dest_carousel_website`), decoys (a card with no video, a quoted post's card, a media link off X's media hosts) and `/video/2` on a carousel card; `FixtureBaselineTest` holds. Drift: both copies of `parse` (`ContractRecipes`, `SiteReadings`) reviewed in the same commit; the census reading reads a card's video as `data` | CI green; the owner's X-only census run: slots 3 and 6 give MP4 rows with V pass, slots 1, 2, 4 and 5 unchanged — **built (2026-10-20)**: the card's videos in the card's own media list (`media`, `swipeable_media`), after the post's own media and before the quoted post's; the quoted post's card not read; 5 synthetic fixtures (both card types; decoys: no video, off X's video host, the quoted post's card); `XSyndicationTest` +3, `XExtractorTest` +2; `FixtureBaselineTest` +5 baseline entries (only additions); `SiteReadings.tweetResult` reads the card's video as data; the X contract recipe and the provenance rows unchanged (reviewed in their headers; the self-check 0 drifted); the owner's X-only run waits for the census APK (0.4) |
| **0.2 The Instagram check (no code)** | Locally only, never on public CI: main's GraphQL ask against yt-dlp's current Instagram extractor (its commit and date): endpoint, `doc_id`, variables, headers. The question: does an ask work signed out without TLS impersonation? Verdict: (a) yes → a fix proposal for main (the owner's go, its own commit); (b) only with impersonation → S3 forbids it; Instagram's stage may then use only the visible tab's own fetch (WebView), on the owner's go; (c) 401 signed out always → a documented limitation. Also answered: main's 401 → `LOGIN_REQUIRED` mapping (keep or change) and §8 item 3 (the `doc_id` sources) | the verdict here and in `PROGRESS.md` (structure only; no ID or link) — **checked (2026-10-20): verdict (b)**, only with impersonation: yt-dlp's current file (commit `1f1101d0`, 2026-07-21) sends its signed-out GraphQL ask (`POST /api/graphql`, another `doc_id`, `media_id`, the page's LSD token, after the access check) only with TLS impersonation, else it reads the post's page; main's ask (yt-dlp's previous endpoint and `doc_id`, other variables) answered 401 signed out on 10 of 10 (S0d). No fix for main's GraphQL (S3), a documented limitation signed out; Instagram's stage (S2d) may use only the visible tab's own fetch, on the owner's go. Main's 401 → `LOGIN_REQUIRED`: keep (the signed-out wording optional, the owner's go). §8 item 3: see there |
| **0.3 Facebook's access-check evidence (no code)** | The "content isn't available" page in English against at least one other language (a made-up video ID, not a real post; the page's structure, not its wording): is there a language-independent signal? Yes → a fix proposal for main's `FacebookPageParser` (the owner's go, its own commit); no → Master's judge stays conservative (a final answer only on positive evidence). Slot 12's expectation after the owner's check (the owner's tasks below) | the verdict here and in `PROGRESS.md` — **checked (2026-10-20): yes**: the page's own route (`canonicalRouteName`: `comet.fbweb.CometErrorRoute` with the error root's `privacy: true`; `comet.fbweb.CometVideoHomeVideoNotFoundRoute`) is the same in English, Burmese and German (made-up IDs, signed out, from the sandbox); main reads the English error page `PRIVATE_OR_UNAVAILABLE` and the Burmese one `RESPONSE_CHANGED` (S0d's slot 13). The fix proposal for `FacebookPageParser` is in `PROGRESS.md` (the owner's go, its own commit); until then Master's judge stays conservative; slot 12 after the owner's check |
| **0.4 The census fixes (debug builds only)** | Per row `setup_ms` and the end-to-end ms; a social report key (method + URL + body + identity class, hashed; a new key: `LookupAsk.endpoint` is route B's R2 key and stays as it is) that splits a "repeat" (the same key) from a "variant" (the same endpoint, another body or identity) — the endpoint rule, its enforcement and the host stops stay as they are; the best row's duration (s) and "item N of M"; Instagram's removed slot signed out reads "ambiguous (no session)"; a photo post stays `NO_MEDIA_FOUND`; the site census diff: two JSON exports → per-slot CHANGED / BREAKING lines | JVM tests; CI green; the census APK with 0.1 — **(a) built (2026-10-20)**: per row the setup and end-to-end ms; the record's own report key (method, address, body, identity class: signed in or not and the agent; hashed, never exported) splits repeats from variants, while `LookupAsk.endpoint`, the endpoint rule, its enforcement and the host stops stay as they are; the best row's duration (s) and "item N of M" (X's and Instagram's answer lines; the screen's main taps the four adapters, main's code unchanged); Instagram's removed slot signed out reads "ambiguous (no session)" (counted apart); the photo posts keep `NO_MEDIA_FOUND`; `SiteCensusTest` +4, `LookupRecordLabelsTest` +1, `SiteCensusLookupTest` +1; **(b) built (2026-10-20)**: `SiteCensusDiff`, two exports → per-slot CHANGED / BREAKING lines (BREAKING: main's rows gone, as expected no longer, V pass → fail, Master's route-B recovery lost, a new site stop: a 429, a bot check, the rate limit), a note for each site or slot not compared (in one run only, skipped), an older export's missing fields not compared; the screen's report holds the diff against the last run the state kept; `SiteCensusDiffTest` 5, `SiteCensusTest` +1; next the census APK; **(c) built (2026-10-21)** (§7.4 step 2): per site (i) every slot's main and V, (ii) route B, (iii) the trials (after the final answers, then after rows, each in slot order), each slot's Master part in its own record (at most 16 answers, 8 Mi chars, kept until its Master part); a 429 or a bot check in (i) stops the site, in (ii) or (iii) only the remaining Master parts (`masterStop`: "main N/N · trials incomplete (429)"; its cooldown skips only the Master parts); the diff: `masterStop` alone a note, a Master part not run or limited not compared, nothing compared → "NOT COMPARED"; a baseline per site, VPN (Android's `TRANSPORT_VPN`) and session, the last run whose (i) completed; a changed link → "new link" (a local fingerprint, never exported); Facebook's slot 12 signed out → "ambiguous (no session)"; the state's format 2 (format 1's cooldowns kept); `SiteCensusTest` +11, `SiteCensusDiffTest` +2; next §7.4 step 3, then step 4's runs with the APK of both commits |
| **0.5 The baseline B0 (the owner's run)** | §9's fixed config with the APK of 0.1 and 0.4 = §9's "before"; a site is re-baselined after any new main fix | the shares and the requests and time per lookup recorded here and in `PROGRESS.md`; **2026-10-21:** Facebook's B0 (00:09 UTC with the VPN, 00:21 UTC without); TikTok's B0 and Facebook's new baseline (after A1): §7.4 step 4 |

**S1a — the kit's minimum and Facebook's pack (the pilot)**, flag `-Pyft.socialLadder` (off = today):

- The request recorder in the app's real lookup path, behind the flag.
- Rungs: F1 the plugin player (`plugins/video.php`, today's R8 way), judged; F2 L3 rows on pages
  already read (no fetch); F3 the visible tab's own playback (capture). Master reads the links main
  resolves first (slots 8, 9, 10 and 13) through main's resolved link.
- The judge is conservative: a final answer only on positive evidence; "needs playback" → F3 or
  the browser; no shape hash in the judge.
- S7: no repeated ask; a host stop after a 429 or a bot check; F1's 30-minute cooldown across
  lookups; the budgets.
- S6: V and the init check on every Master row (at most 8 rows); `unknown` is not a pass; the
  link lifetime (`oe`).
- Post identity bound to the Facebook video ID; mutation tests (a missing or renamed ID, a
  related-video decoy, a page with two videos → no rows); a simulator world (a renamed key, a
  moved block, a 429, a sign-in wall, the unavailable page in two languages).
- Flag off: the route B tests, the parity tests, `FixtureBaselineTest` and the drift check as they
  are; no YouTube file changes.
- **Done when:** JVM tests, simulator and CI green; a D7-style APK (main's Facebook skipped or
  mutated): every Master row passes V; wrong rows 0 (the video ID matches; a trial's duration
  within ±1 s of main's); slots 8–10 give Master rows (the owner, 2026-10-21, after step 6's
  first run: slots 9–10 in their desktop forms, a Page post and a group post, may end as a
  documented limitation; their mobile share links must give Master rows); slot 13
  `PRIVATE_OR_UNAVAILABLE` with and without the VPN if 0.3 found a signal, else no wrong final
  answer and a documented limitation; slot 12 by the owner's check; requests and time on
  main-success lookups not above B0 (§9); docs. **Met (the owner, 2026-10-21): §7.4 step 6's
  re-run, S1a's phone proof PASS; for S5: Master's rows 2 against main's 3 on every link (§7.4):
  listed as an intended difference by task 7 (`docs/SUPPORT_MATRIX.md`; main's audio row).**
- **(1) built (2026-10-20), JVM only, not wired:** the answer judge (`SiteAnswerJudge.kt`): L2's
  classes and `NEEDS_PLAYBACK`, each with its next step; Facebook's rules final only on 0.3's
  route (the error route with `privacy: true` → `PRIVATE`, the video's not-found route →
  `REMOVED`), in every language; the words, a route without the flag or two that disagree, the
  reel's `video: null` and a login form → `SHAPE_CHANGED` (the next rung); rows bound to the video
  ID over any page signal; a 429 or a checkpoint → the host stop; a login address → the browser;
  `SiteAnswerJudgeTest` 7. No caller yet (the flag comes with the first wiring slice); region and
  age wait for evidence. Next: the recorder behind the flag, F1 judged, F2, S7, S6, the mutation
  tests, the simulator world; the phone APK on the owner's go.
- **(2) built (2026-10-20), behind the flag:** the flag `-Pyft.socialLadder` (debug only, with
  Master capture; off = today, no CI build passes it); with it on each Facebook lookup (every link
  form) runs in its own record with Facebook's labels and its own `MainPages` (main's 2xx GET
  answers: the address asked, the final address, the credentials flag and the page; at most 4
  answers and 8 Mi characters; no cookie; memory only), written by main's shared client
  (`LookupRecorder`, `MainPageRecorder`); main's requests unchanged; Master reads nothing of it
  yet; `MainPagesTest` 4, `SocialLadderTest` 4. Next: F1 judged with the links main resolves
  first, F2, S7, S6, the mutation tests, the simulator world.
- **(3) built (2026-10-20), behind the flag:** F1 judged: in a Facebook lookup with its own record
  Master's first stage is `FacebookLadder` (the engine's `SocialStage`) in place of R8's unjudged
  ask: the plugin player asked once under route B's rules, read for the video and judged
  (`FacebookAnswerJudge`): rows → V; final only on the page's route (private, removed) or DRM, in
  main's words in the app; "needs playback", a sign-in wall, a 429 or a bot check and no evidence
  → the tab's own playback, no second ask; the links main resolves first read through main's
  resolved link (`MainPages.resolved`; none or two videos: not asked), the rows keyed to that
  video; flag off = today; `FacebookLadderTest` 7, `SocialLadderF1Test` 3. Next: F2, S7, S6, the
  mutation tests, the simulator world.
- **(4) built (2026-10-20), behind the flag:** F2, main's pages: when F1 leaves nothing to read,
  the pages main read in the lookup for the link (`MainPages`) are read again with Master's
  layers, no request (the JSON data scripts that name the video; L4's keys, L3's shape search;
  rows bound to the video ID) and each judged as F1's answer is: rows → V (the page data stage);
  DRM → final; the page's route → final only when every page main read for the link says the same
  and the plugin player did not name the video; another video's page, a page over the bound and a
  related video's files → no rows, never final; a link main did not resolve: its page judged for
  the route only; flag off = today; `FacebookLadderF2Test` 6, `SocialLadderF2Test` 3. Next: S7,
  S6, the mutation tests, the simulator world.
- **(5) built (2026-10-20), behind the flag:** S7, stops and budgets: F1 asks only when none of
  its hosts stopped in the lookup (a bot check, main's asks included), none cools (30 minutes
  after a host's last 429 or bot check, across lookups; `SocialCooldown`, memory only) and the
  lookup's budget holds (`SocialBudget.FACEBOOK`: 4 requests, main's and Master's; 20 s; 8 MiB of
  Master's answers; F1 waits at most the time left, reads at most the characters left); else not
  asked, F2 still reads; no endpoint asked twice in a lookup, by main or by Master (route B's
  rules); main's 429 and bot checks and F1's own cool the host from the stage's end (the app:
  before main's final 429 ends the lookup); flag off = today; `FacebookLadderS7Test` 5,
  `SocialLadderS7Test` 2. Next: S6, the mutation tests, the simulator world.
- **(6) built (2026-10-20), behind the flag:** S6, row checks: in a Facebook lookup (its own
  record and main's pages) every Master row passes V (one read of the file's first 4096 bytes,
  the download's headers without the session, the answer's host only when a media host, no
  redirect; each file once, at most 8 a lookup; route B's rules) and the init check on the same
  bytes (type, container start, no encryption marker, size) in place of the engine's check;
  `unknown` is not a pass (a manifest row too), an encryption marker → DRM (final); the link's
  lifetime (`oe`, at least 60 s left); flag off = today (the engine's check itself);
  `FacebookLadderS6Test` 7, `OkHttpRowReaderTest` 4, `SocialLadderS6Test` 2. Next: the mutation
  tests, the simulator world.
- **(7) built (2026-10-21), behind the flag:** post identity bound to the video ID: in a Facebook
  lookup F1's answer and each of F2's pages that gave rows are walked once more (`FacebookIdentity`:
  the documents the readers read, the JSON texts inside, at most 500,000 values; an object's own ID,
  else its parent's; files compared by their path); two videos under the ID (an ID-less list item
  with two or more files, two objects with the ID giving one field disjoint files, a list item's
  file under a field the video's object has empty or with another file, a row's file listed only
  under another ID) or the walk over its bound → no rows from that answer (F1: the next rung, F2
  still reads; an F2 page: not staged); known limits: one ID-less file under a field the video's
  object lacks or leaves empty, the player's second item claiming the video when the first has no
  file; flag off = today; `FacebookIdentityTest` 7, `SocialLadderIdTest` 2. Next: the simulator
  world.
- **(8) built (2026-10-21), behind the flag:** the simulator world (`FacebookWorldTest`, a test
  only): one made-up Facebook on a fake clock (watch and reel pages with a related video, the plugin
  player, the media host), main's real adapter first and Master after its failure, the ladder, S6
  and S7 shared by the lookups; a renamed key, a moved block, a 429 (the site stopped in the lookup,
  F1 cooled 30 minutes, F2 still reads), a sign-in wall, the unavailable page in two languages
  (final on its route, never on its words); 14 cases × 2 link forms, wrong rows 0; in each lookup
  the rows only the video's, each file read once, the budgets, no repeated ask, none after a stop,
  bare lines; flag off = today; `FacebookWorldTest` 7. Next: §7.4.
- **(9) built (2026-10-21), behind the flag:** the Facebook trial APK for S1a's phone proof (§7.4
  step 6; d7-matrix's ninth mode `fb-trial`, artifact `yft-d7-fb-trial` — since task 6a
  (2026-10-22) `social-trial`, artifact `yft-d7-social-trial`, the one trial APK for Facebook,
  TikTok, X and Instagram, Instagram's probe off by default: `-Pyft.socialTrial` with
  `-Pyft.socialLadder`, debug only, `BuildConfig.SOCIAL_TRIAL`): in a Facebook lookup (every link
  form) a page main found is answered as main's `RESPONSE_CHANGED` ("Facebook trial: main's rows
  were set aside and Master found none. Tap Try again."; Details "master: facebook trial: main's N
  rows set aside") after main asked (its pages and resolved link kept: F2, slots 8–10), its rows
  set aside for that lookup only (memory), so the ladder answers every Facebook lookup (YouTube's
  `routeb`, item 7 F7, the model); main's failures (final ones too) and every other site as is;
  when Master gives rows, Facebook's line compares them ("; trial: Master's rows M, main's N; the
  same video: yes|no; duration within 1 s: yes|no|unknown"; it never fails the lookup), shown where
  route B's trial line is; the other eight APKs and the push build unchanged; flag off or trial
  off = today; `SocialTrialTest` 11. Next: §7.4 step 6.
- **(10) built (2026-10-21), behind the flag:** the fixes from step 6's first run (§7.4 step 6).
  The length: a Master row that passes S6 and states none gets its file's own from the bytes S6
  read (`StartDuration`: a whole file's movie header, else a fragmented file's own length, else
  its index; bounded, no new request), so the trial's line compares it with main's (the plugin
  player's answer names no length, so every line said "unknown"). A Facebook lookup whose Master
  answer is not used keeps its own answer (the trial's or main's) with the Details line "master:
  facebook: Master's answer not used: <step>" (no page to ask, another page, the page changed
  during the lookup and which change, skipped), then Master's own lines; the ladder's rows (F1,
  F2: the video's own) stay while the same video stays open (the page started again, or the same
  video's other address), the tab's playback (F3) and every other answer still need the same
  page. Links 9–10's desktop forms (a Page post, a group post) and links 12–13 (the sign-in page):
  documented limitations (§7.4 step 6); link 9's other video is main's (flag off too: main's bug,
  its fix the owner's call). Flag off = today; `StartDurationTest` 4, `FacebookLadderS6Test` +1,
  `SocialTrialTest` +5 (16). Next: §7.4 step 6's re-run.

**S2b TikTok:** T1 the embed player (`embed/v2`), judged; a bot check stops; a photo post stops
with no request; T2 L3; T3 capture; TikTok's own client (not the shared one) wrapped by the
recorder; V and the init check; the lower quality (at most 1280) documented.
**S2b built (2026-10-21, behind the flag; `PROGRESS.md`):** the photo check first (no request,
final in main's words); T1 judged (`TikTokAnswerJudge`); T2 main's pages (no request); T3 the
tab's own playback; main's TikTok client wrapped by the recorder and main's pages
(`SocialLadder.tiktokClient`); V and the init check on TikTok's media hosts (`MainPages(site)`);
`SocialBudget.TIKTOK` (20 requests, 4 MiB, 30 s); T1 before T2 (§7.4's note); the lower quality
and T2's cookie limit (R20) in `docs/SUPPORT_MATRIX.md`; the trial APK `fb-trial` sets aside
TikTok's pages too. Its phone proof: the owner's next session (§7.4).
**S2c X:** main's answer replayed, no new ask (X2 not now); a hidden post → the browser; a photo
post stops; an HLS row only with a known duration (V).
**S2c built (2026-10-21, behind the flag; `PROGRESS.md`):** X1 main's answer from the widget
(`tweet-result`) replayed with no new ask (no token self-check, no X2), judged by its shape
(`XAnswerJudge`); `MainPages` also keeps main's refused GETs (status and reason only), so main's
404 is judged without asking again. Rows of the link's item only (`/video/N` among the post's own
or card's media, else the first; the quoted post's video when the post has none), bound to the
link's post ID; MP4 rows first with main's length, an HLS row only without MP4 and with a known
length; V and the init check on X's video host; `SocialBudget.X` (9 requests, 2 MiB, 20 s).
Final, no request: a photo post (main's words). (S2c also made the widget's 404 final as removed;
task 0 undid that, below.) By the live shapes (2026-10-21, signed out): the empty
tombstone (age-limited) → the browser; X's notice tombstone (a deleted post's and a protected
post's alike) → needs playback, today's reason; `{}` (no widget token) → `VALUE_REJECTED`, not
final — so §6.4's "an empty answer → `REMOVED`" and "protected → `PRIVATE`" are not built (the
shapes do not show them); intended differences in `docs/SUPPORT_MATRIX.md`. The trial APK
`fb-trial` sets aside X's rows too and compares the item and the duration. Its phone proof: the
owner's next session (§7.4).
**Task 0 built (2026-10-22, behind the flag; `PROGRESS.md`; decision 21):** X1 judges the widget's
404 `NEEDS_PLAYBACK` with no reason of its own (line `master: x X1 main's answer: NEEDS_PLAYBACK
(status 404: no post in the widget's answer, not proof of removal); next: PLAYBACK`): never final,
so the engine's existing next stages follow (the visible tab's own playback, X3, then the user's
browser), and when they find nothing the lookup keeps main's reason (`NO_MEDIA_FOUND`, main's
words, Try again). No new ask (no widget request, no X2, no token check, no new route); X's ladder
has no final "removed" answer left, so the app's X words for one ("This X post is no longer
available.") are gone. Unchanged: main, the auth gates (a sign-in or bot-check failure skips the
ladder), the photo post's final, the empty tombstone (the browser), the 429's stop and cooldown,
and flag off.
**S2d Instagram:** by 0.2's verdict; no repeat of main's embed ask; `RATE_LIMITED` → the cooldown;
`doc_id` self-healing only if 0.2 shows it rotates (then S1b); the owner's signed-in census before
this stage.
**S2d part 1 built (2026-10-22, behind the flag; `PROGRESS.md`):** I2, main's answers in the lookup
(`MainPages`: the app API signed in, GraphQL, the post's page, the embed page; each way's kept page,
else its refused GET's status and reason) read with no new ask (main's embed ask never repeated, no
GraphQL ask of Master's own: decision 3) and judged (`InstagramAnswerJudge`: a sign-in wall, the
checkpoint too, → the browser; a 429 or "wait a few minutes" → the site stops, the host cools; a JSON
way's 400 or `errors` → `VALUE_REJECTED`, the next stage; photos only → final in main's words; no
post, `EmbedIsBroken` → needs playback; never `REMOVED`: decision 19 (vi)). Rows of the link's item
(`img_index`, else the first item with files, as main), bound to the post, V and the init check
(DASH: the video and its sound); Master's recipe keeps the 360p file main drops (an intended
difference). `SocialBudget.INSTAGRAM` (13 requests, 7 MiB, 30 s). I3: the tab's own playback. The
tab-fetch probe (decision 19 (i)–(v), debug only): its own switch, off by default ("YFT site
census" → "Instagram's tab-fetch probe"), once per lookup after I2, only on the post's own page in
the visible tab, signed out; Master's ask in the record (budget, stops, no second ask); one Details
line; never a row. The trial APK `fb-trial` sets aside Instagram's rows too and also says each side's
tallest row. The census's tallest difference (signed in, slots 1, 4, 6, 7): its Master trial read
R8's public embed page (no cookie), main the app API with the session. Its phone proof and the
probe's result: the owner's next session (§7.4).
**Task 6b built (2026-10-22, behind the flag; `PROGRESS.md`):** in a social-ladder build
(`yft-d7-social-trial`) the site census's Master part of each Facebook, TikTok, X and Instagram
slot runs the site's pack in place of R8's way: the site's ladder (the app's `SocialLadder.stage`)
on main's pages from the census's own main lookup of that slot (`MainPages`), S6's file check and
S7's stops, budgets and cooldown (the app's); the row's record clock stops between main's lookup
and Master's part (a ladder's time budget is the row's own time); a short link runs too. Each part
names its rungs (Facebook F1–F3, TikTok T1–T3, X X1 and X3, Instagram I2 and I3: rows, rows not
checked, run, not run with why — cooldown, stop, request/time/byte budget, no video, nothing to
read, final, no tab — not reached, unknown after the engine's time limit) and compares Master's
rows with main's (`TrialCompare`, the app's trial comparison: the same video or item, the duration
within 1 s, wrong rows and unknown rows apart; main with no rows: every Master row wrong on a slot
that wants none, unknown otherwise). Main's part, its requests and its time stay as they are and
apart from Master's (B0 stays comparable); the export only gains fields (format 2); the diff still
reads older exports, compares Master's parts only when both runs have the ladder, and a part's
first wrong row is BREAKING. Flag off: today's census, byte for byte. So each site's proof is one
census run per network (§7.4's next phone session); no wrong row is not parity or release
readiness.
**Task 7 done (2026-10-22, tests and docs only, no behavior change; `PROGRESS.md`):** Facebook's
Master rows 2 against main's 3 (§7.4 step 6's re-run, every link). The row Master lacks is main's
**audio row**: main reads each link's video through its watch page, whose inline DASH manifest
(`dash_manifest_xml_string`) holds video-only tracks (AV1 today) and AAC audio; main's
`FacebookDashOffers` offers the best AAC track alone as an M4A row and merges the video tracks,
which the app drops on every phone (AV1 merges are off), so main's rows are HD and SD (the page's
progressive files) and the audio row. F1 (the plugin player) names the HD and SD files and no
manifest (`dash_manifest` null); its page holds only a player prefetch block (byte ranges of one
AVC video and one AAC audio representation, named by the video's prefetched representation IDs,
outside the video's own data), which S1a's recipe does not read. F2 (main's pages, which hold the
manifest) runs only when F1 leaves nothing to read (S1a (4)). So no fix within S1a's rules (no row
made up to match the count): listed in `docs/SUPPORT_MATRIX.md` as an intended difference. What
the user sees: the same video qualities (the trial's line: the same video yes, the duration within
1 s yes); the sheet's Audio (M4A, MP3) still offered from the video's own sound (the MP4's AAC
track), from a bigger download than main's audio-only file. Options, the owner's call (not built):
(a) F1 reads the prefetched audio representation, bound by the video's representation IDs, V on
it; (b) F2 after F1's rows, for the audio track only (S1a (4)'s order changes). Found from main's
code and today's page shape (a sandbox check of a public test video, signed out, nothing kept);
the next phone session's Facebook items show it. **Link 9's desktop form** is pinned with the trial
in `SocialTrialTest` (made-up IDs): main's rows of the post's video set aside; F1 not asked (no
video link resolved: "a link main resolves first, no video in main's answer; not asked"); F2 no row
bound to the post; F3's focus check never matches a post's ID, so the tab's page starts again →
"Master found none" with its step in Details ("Master's answer not used: the page changed during
the lookup: the same address again"); without the trial, main's rows of the post's one video. An
option, the owner's call (not built): when main's post page names exactly one video (S1a (7)'s
one-video check), F2 binds that video's rows with no new ask (links 9 and 10's desktop forms would
get Master rows). Next: task 8 (S3's weekly check W).
**Task 8 built (2026-10-22, debug only; flag off and the full census unchanged; `PROGRESS.md`):**
S3's weekly check W in the site census (§7.5, W's runbook). In a social-ladder build only (behind
the flag; every other build's census screen as before), "YFT site census" → "Weekly check W (all
sites)" runs the census's own steps (`SiteCensus.run` with `ClientCensus.Mode.WEEKLY`;
`SiteWeekly`) on three slots per site by case kind (Facebook 1, 8, 13; TikTok 1, 6, 9; Instagram
1, 8, 10; X 1, 6, 8), each way at most once (Master's part, the site's ladder, on slot 1 only, the others `W:
main only`), per site at most 60 s, 2 MB and 24 requests (each checked before every step; the time
left bounds each step too), a cap stopping the site's rest with no cooldown, and the census's
stops and cooldowns ("not run", never waited out or passed). W keeps its own baselines (per site,
VPN and session; never the census's) and its diff prints one line per change, each with its
runbook step (`→ W runbook step N (…)`; eight steps: the site's stop, main's answer, the slot's
verdict, V, the item or the length, Master's part, Master's wrong rows, the run's conditions; each
with what to check and its fix: data via R9 once S4 is live, or code). YouTube's W's design reused
(`ClientCensus`'s caps, `CensusDiff`'s lines with a runbook, `MASTER_KEY_PHASE1_2_PLAN.md` §6's
numbered steps); no YouTube file changed. JVM tests (`SiteWeeklyTest`, made-up IDs): the slots and
each way once, the three caps, a 429's cooldown read as not run, W's and the census's baselines
apart, the lines and their steps, the runbook's entry for each step. Not S4 (R9 live); decision 5
open. Next: the owner's phone session (§7.4; W's first run: C).
**Task 9 built (2026-10-22, debug only, behind the flag; flag off unchanged; `PROGRESS.md`):**
S6's row reader checks each read against its lookup's site. The phone session of 2026-10-22 (A's
X census, `yft-d7-social-trial` of `430da9f`, signed out, the VPN on, 10:48 UTC): slots 1–6 each
Master trial "failed NETWORK; new 3 (GIF 1), refused 0", each ask "unlabelled GET NETWORK 1–2
ms", the ladder's rows 0, while main's V on the same host passed (206). Cause: `OkHttpRowReader`'s
own site (Facebook by default; the app and the census named none) refused every file off
Facebook's media hosts before any network, after route B had listed the ask, so the refusal read
as a new `NETWORK` ask. Fix: one reader for every site, holding no site (the default removed);
each read carries the site `SocialRowCheck` chose for its lookup (`RowReadSite`: the lookup's
`MainPages` site), and only that site's media hosts and paths are asked (no union of the sites'
hosts; HTTPS only, no redirect, no cookie or credential, 64 KiB, 10 s, no retry, as before); a
file off them is refused before route B lists an ask (never sent, counted refused, never
`NETWORK`); a real network failure stays `NETWORK`. The app and the census wire S6 through one
factory (`SocialLadder.validator(validator, client)`: one check, one reader). JVM tests through
that factory (`SocialLadderRowReadTest`: X's, TikTok's, Instagram's and Facebook's files each read
once on their own host; another site's host refused; no answer still `NETWORK`), and
`OkHttpRowReaderTest` and `SiteCensusLadderTest` +1 each; Facebook's behavior and existing tests
unchanged. Next: the owner's phone session (§7.4: Master on X first, then TikTok after its 429's
cooldown, then Instagram).
Each site: a D7-style phone proof, wrong rows 0, no regression against B0.

**S3 and S4** as in §7: W (≤ 1 min, ≤ 2 MB per site) with the diff and the runbook; R9 live (the
owner signs and hosts the config, a kill switch per rung, one repair drill on the phone).

**The release gate (§8 item 12):** each site's S5 (3 phone runs on different days, wrong rows 0,
no regression against B0), the first W run passed, the R9 drill passed, the intended differences
listed in `docs/SUPPORT_MATRIX.md` → PR #2 merged → P8 (beta.4) → others get the app.

**After the release** (each on the owner's go): S1b, X2, the "all items" feature, B+ (S6), A.

**Not done:** X's reason for the age-limited and removed posts changed; `LookupAsk.endpoint` or a
YouTube file edited; a shape hash in a judge; yt-dlp on public CI; IDs, links or names in docs,
fixtures or logs.

**The owner's tasks:** slot 12 checked signed in (replaced on the phone if it is gone); the weekly
census in the fixed config (§9), the B0 run first; Instagram's signed-in census before S2d; R9's
hosting before S4. Instagram slot 7's check in the app is no longer needed (one item per link).

### 7.4 After S1a (8): the census fix and Facebook's access fix (the owner, 2026-10-21)

After Facebook's B0 runs (2026-10-21, with and without the VPN) the owner approved this plan for
the steps after S1a (8); it records §8 items 13–16. Each step still starts on the owner's go
(S10). TikTok's part (step 1, §8 item 17) was added by the owner on 2026-10-21, after the Salt
Lake City run.

**Why** (read in the code at `1028829`; no new test run):

- `PRIVATE_OR_UNAVAILABLE` is final (`NEVER_FALLBACK`; the generic detector off too). Main reads
  the English "content isn't available" page so today, and the same page in another language
  `RESPONSE_CHANGED` (0.3; S0d's slot 13). A1 makes that verdict independent of the language with
  0.3's signal; it is not a new verdict.
- A2 (a reel's `video: null` read as final) would be a new verdict from missing data. If Facebook
  moved a reel's video data out of the page (loaded later), every public reel would read
  "private", final, and Master would be off where it is needed most; the census would see it
  (slots 4–6), but users could not download until an app update, and more evidence cannot remove
  a risk that comes from a future change. Master's judge already reads the reel's `video: null` as
  no evidence (`SHAPE_CHANGED`, the next rung; S1a (1)); X keeps the generic detector for what it
  cannot see (item 0.1). One policy for both. A private reel's extra cost stays within S7's
  Facebook budget (4 requests, 20 s).
- The census: a 429 or a bot check in a slot's Master part stops the whole site, so the later
  slots' main is not looked up; the diff compares the last run only (any site, with or without
  the VPN: two false BREAKING so far) and reads "NO CHANGE" when nothing was compared.
- Slot 12 (`sign-in-only`) expects `LOGIN_REQUIRED` signed out, but signed out Facebook answers a
  friends-only reel as it answers a missing one: its signed-out answer is ambiguous, like
  Instagram's removed slot (item 0.4 (a)).
- TikTok (the owner's four runs, all with the VPN): S0d (2026-10-20 02:14 UTC) asked the embed
  player 9 times with no 429; three later runs (two through S0d's exit, one through another US
  exit) got a 429 at the embed's third or fourth ask. The census then stopped the site, so the
  later slots' main (from slot 4 or 5) was not looked up: TikTok's B0 is not complete. Main's asks
  got no 429, but after the 429 main was not asked again. Main's page asks carry the browser's
  cookie (authorized); the embed's asks do not. The cause is not known: another VPN exit is not
  shown to fix it, nor shown not to matter.
- `PRIVATE_OR_UNAVAILABLE` and `GEO_RESTRICTED` are final (`NEVER_FALLBACK`). The census tries
  Master after them only as a trial (given `NO_MEDIA_FOUND`): a check that Master gives no rows
  (S2: no private or region bypass), not a recovery. S0d already ran slots 8–9's trials (the embed
  answered 400: "needs playback", no rows) and slot 6's route B (200, no data).
- With the phase order, a slot whose main ran but whose Master part did not (the Master phase
  stopped) would still be compared today: CHANGED, or BREAKING when the earlier run's route B
  recovered it. A stop's 30-minute cooldown skips the whole site in the next run, main too, while
  S7's cooldown is for the rungs that send requests.

**Decided** (§8 items 13–17):

- The census fix 0.4 (c): go, debug builds only.
- A1, Facebook's access fix ("0.3 fix"): go; main, its own commit (§8 item 10's exception).
- A2: not in main, closed; a reel's `video: null` stays recoverable by design.
- Facebook's slot 12 signed out: `PRIVATE_OR_UNAVAILABLE` or `RESPONSE_CHANGED` reads "ambiguous
  (no session)", counted apart; signed in: rows, as today. No `LOGIN_REQUIRED` guessed without
  evidence.
- One phone test, with the APK of both commits (not one per commit).
- No real link or ID in the repo, fixtures, docs, logs or prompts (S8).
- TikTok (§8 item 17): no TikTok run before step 4; TikTok's B0 is step 4's first run, with the
  VPN's US exit; no VPN-exit or slot-split runs. Commit 1 also gets step 2's TikTok items (the two
  stops, the Master parts not compared, the Master-phase cooldown, the trial order).
  `PRIVATE_OR_UNAVAILABLE` and `GEO_RESTRICTED` stay final; a Master row on those slots is a
  failure (S2).

**The steps:**

0. **Now (the agent):** S1a (7) and (8), the running task. No other code commit before its report
   is checked (the SHAs and CI).
1. **TikTok (the owner, 2026-10-21):** no TikTok run with today's APK, through another VPN exit
   or of only some slots (each stops again after 2–3 embed asks; mixed runs make no baseline).
   TikTok's B0 is step 4's first run. Its pass: (i) completed, every filled slot's main looked up
   (9 of 9; the private slot is empty); not as expected 0; main's V pass on every slot with rows
   (the desktop-only HD slot at least 720 high, as its slot expects); no Master row on slots 8–9
   when their trials run; the trials: any number (a Master-phase 429 is a note). A 429 in (i): not
   B0; one rerun on another day, after the cooldown; a second one → its cause is looked at before
   S2b, not by trying VPN exits.
2. **The agent, commit 1 — "Social plan 0.4 (c)": the census (debug builds only).** **Built
   (2026-10-21, `11c64d2`).**
   - The order within a site: (i) every slot's main and main's V; (ii) route B for main's
     failures; (iii) the trials. A slot's Master part runs in that slot's own `LookupRecord` (the
     replay rules unchanged), with a memory cap for the whole run (a record keeps at most 16
     answers, 8 Mi chars).
   - Stops: a 429 or a bot check in (i) stops the site, as today; in (ii) or (iii) it skips only
     the remaining Master parts and keeps main's results; the report reads "main N/N · trials
     incomplete (429)".
   - The two stops apart in the export: `stop` (in (i)) and `masterStop` (in (ii) or (iii)). The
     diff: `stop` as today; a change of `masterStop` alone is a note, never CHANGED or BREAKING;
     the headline adds "· trials incomplete (429)" (or the stop's word) when the later run has one.
   - A Master part that did not run because of `masterStop`, or whose own ask got the 429 or the
     bot check, is not compared (a note), never CHANGED or BREAKING (route B's "no longer
     recovers" too); Master parts that ran in both runs are compared as today; main's rules are
     unchanged; a field an export lacks (format 1) is not compared.
   - A `masterStop`'s 30-minute cooldown skips only the Master parts of a run inside it (main
     runs; S7's cooldown is for the rungs that send requests); a stop in (i) cools the whole site,
     as today.
   - Within (iii): the trials after main's final answers (`PRIVATE_OR_UNAVAILABLE`,
     `GEO_RESTRICTED`) first, then the trials after main's rows, each in slot order (low priority:
     S0d already ran both kinds once).
   - The budget and the cooldowns shared by all phases (no reset per phase); a row's time is the
     sum of its own steps.
   - The baseline: one per site, VPN (on or off) and session; the VPN read by Android
     (`NetworkCapabilities.TRANSPORT_VPN`, as the screen reads Wi-Fi), not chosen by the owner;
     the baseline is the last run whose (i) completed, pass or fail; a run stopped in (i) does not
     replace it.
   - Nothing compared → "NOT COMPARED", never "NO CHANGE".
   - A slot's link changed → seen by a local fingerprint (never exported); the slot reads "new
     link" and starts a new baseline.
   - Facebook's slot 12 rule (above). The state's format 2; format 1's cooldowns kept.
   - Not touched: main's lookups, Master's engine and contracts, `RouteBLookup`,
     `RouteBHttpClient`, `LookupAsk.endpoint`, the YouTube files, the workflows.
   - Tests: a trial's 429 does not stop the later slots' main; main's 429 still stops the site;
     the deferred replay; the memory cap; the baseline's choice; NOT COMPARED; a new link; slot
     12; format 1's state; `masterStop` alone never CHANGED or BREAKING; a Master part that did
     not run (or got the 429) not compared, route B's too; the Master-phase cooldown keeps main;
     the trial order.
   - Docs: a `PROGRESS.md` top entry (it also records this plan), a `docs/TEST_MATRIX.md` row,
     item 0.4's status in §7.3.
3. **The agent, commit 2 — "Social plan 0.3 fix": A1 (main)**, pushed only when commit 1's CI is
   green. **Built (2026-10-21, `6b011da`); F2's follow-up `4f67209` (Master, behind the flag).**
   - Only when the page has no playable media and no existing marker matches, the page's own
     route (`initialRouteInfo.route`): the error route with the root's `privacy` JSON `true` →
     `PRIVATE_OR_UNAVAILABLE`; the video's not-found route → `PRIVATE_OR_UNAVAILABLE`. In
     `accessFailure`, so both parser paths are covered; the English markers stay; the route scan
     bounded as in Master's judge (4 route infos, 256 KiB).
   - The final answer only from the session page (main's last page read): the public page alone
     never ends the lookup, and a video on the public page wins (today's flow, locked by tests).
   - Today's reason kept for: no flag; a flag as a string or `false`; a route only in another
     script; two routes that disagree; a renamed key (as Master's judge's 0.3 rule). The lookup
     flow unchanged; a reel's route with `video: null` stays as today (locked by a test).
   - Tests (synthetic, made-up IDs): English and Burmese; both parser paths; the not-found route;
     decoys and media precedence; a public page's error with a video on the session page → rows;
     a post link never `NO_MEDIA_FOUND`; `FixtureBaselineTest` unchanged.
   - The provenance-tracked symbols unchanged; if one must change, its copy and its provenance
     row in the same commit (R3).
   - Docs: `docs/SUPPORT_MATRIX.md` (Facebook's unavailable page in any language; a reel's
     `video: null` stays recoverable, A2), a `PROGRESS.md` entry, a `docs/TEST_MATRIX.md` row.
4. **The owner** (the APK of step 3's SHA, signed out), the same day, in this order: TikTok only
   with the VPN (§9's US exit; step 1's pass), Facebook only with the VPN, Facebook only without
   it. Facebook's pass: slots 1–11 rows 11 of 11 and V pass 11; slot 13 `PRIVATE_OR_UNAVAILABLE`
   (final) in both runs; slot 12 ambiguous; not as expected 0; requests and time not above
   Facebook's B0 (about 68 requests, 65 s). The new state has no baseline yet, so these runs are
   compared with B0 by hand; they become Facebook's new baseline (a main fix re-baselines its
   site, §9). Instagram and X need no new run. **Done (2026-10-21; the runs below).**
5. **Docs only:** Facebook's new baseline and TikTok's B0 (step 4's runs). **Done (2026-10-21).**
6. **S1a's phone proof (the owner's go):** §7.3's done-when, with slot 13 = main's final answer
   and slot 12 = ambiguous; optional: slot 12 once signed in (rows). The APK: `yft-d7-fb-trial`
   (d7-matrix's `fb-trial`, S1a (9): the social ladder and its Facebook trial on, so the ladder
   answers every Facebook page main found). Then S2b (§7.3). **First run (2026-10-21): below; its
   fixes: S1a (10) (§7.3), behind the flag. Its re-run (2026-10-21, `042d57a`): PASS (below).**

**Step 4 — the owner's runs (2026-10-21).** The census with the APK of step 3's SHA (`6b011da`'s
"Master opt-in debug APK (spike)"), signed out, mobile data, the same day, in step 4's order; the
VPN through its US exit (Boston), as in Facebook's B0 runs; without it the owner's mobile network,
the same one as Facebook's no-VPN B0 run, so the two compare (TikTok runs only with the VPN on it,
§7.2). The new state had no baseline yet, so Facebook's runs were compared with B0 by hand (B0's
rows from its two reports).

| Site | VPN | UTC | Should download | Recoverable | Master recovered | Browser | Not as expected | Ambiguous | Route B | Trials (with rows, V pass) | Requests · MB · s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TikTok | on | 05:59 | 6 | 0 | 0 | 0 | 0 | 0 | 1 | 0 (0, 0): trials incomplete (429) | 39 · 1.2 · 49 |
| Facebook | on | 06:01 | 11 | 0 | 0 | 0 | 0 | 1 (slot 12) | 1 | 8 (8, 8) | 68 · 5.1 · 65 |
| Facebook | off | 06:04 | 11 | 0 | 0 | 0 | 0 | 1 (slot 12) | 1 | 8 (8, 8) | 68 · 5.3 · 52 |
| Facebook B0 | on | 00:09 | 11 | 0 | 0 | 0 | 1 (slot 12) | 0 | 1 | 8 (8, 8) | 68 · 5.1 · 65 |
| Facebook B0 | off | 00:21 | 11 | 0 | 0 | 0 | 2 (slots 12, 13) | 0 | 1 | 8 (8, 8) | 68 · 5.2 · 63 |

- **TikTok — B0 (step 1's pass).** Main's phase completed: 9 of 9 filled slots looked up (slot 7,
  the private slot, empty), no 429 or bot check in main's asks; rows on 6 of 6 video slots, main's
  V pass 6 (fail 0, unknown 0); the desktop-only HD slot (10) tallest 1920 (slot 1 too); not as
  expected 0. The photo post (slot 6) `NO_MEDIA_FOUND`, as expected; its route B asked the embed
  player once: 429 → `masterStop` ("main 9/9 · trials incomplete (429)"; its 30-minute cooldown
  for the Master parts only), so the other Master parts did not run ("stopped (429)"): trials 0,
  no Master row on slots 8–9. Region-limited (slot 8) `GEO_RESTRICTED` and removed (slot 9)
  `PRIVATE_OR_UNAVAILABLE`, both final (the download service 403). The diff: "FIRST RUN"; the run
  is TikTok's baseline (VPN on, signed out). For S2b: this time the embed's first ask got the 429
  (before: its third or fourth); the cause is still not known.
- **Facebook with the VPN — the new baseline (VPN on, signed out).** Against B0 (00:09 UTC): the
  same rows on every slot, V pass 11, trials 8 (8, 8), the same requests (68) and time (65 s); one
  change: slot 12 (sign-in-only: `RESPONSE_CHANGED`; its route B, the plugin player, no data →
  "needs playback") reads "ambiguous (no session)", not "not as expected" (commit 1's rule). Slot
  13 (removed) `PRIVATE_OR_UNAVAILABLE` (final, one request), as in B0. Master not run on slots 8,
  9, 10 and 13 (links main resolves first, as in S0d).
- **Facebook without the VPN — the new baseline (VPN off, signed out); A1's phone proof.** Against
  B0 (00:21 UTC): 68 requests (B0 68), 52 s (B0 63 s); the same rows, V pass 11 and trials 8 (8,
  8); not as expected 2 → 0: slot 13 `PRIVATE_OR_UNAVAILABLE` (final, one request) where B0 read
  `NO_MEDIA_FOUND` (recoverable; main's English words did not match): main now reads the page's
  own route, in any language (A1); slot 12 ambiguous, as with the VPN.
- Step 4's pass is met by all three runs. The census's diff read "NOT COMPARED" in both Facebook
  runs (no format-2 baseline yet); each run starts its site's baseline (VPN, session) on the
  phone. Instagram and X: no new run, as step 4 says.
- The runs came before the F2 follow-up (`4f67209`: in Master's F2 the route on pages read without
  the session never ends the lookup); it is behind `-Pyft.socialLadder`, which no CI build passes,
  so the runs stand (no rerun).
- Then step 6 (the owner's go) with `yft-d7-fb-trial` (S1a (9), 2026-10-21): its first run below.

**Step 6 — the owner's first run (2026-10-21).** `yft-d7-fb-trial` of `ef4a546` (S1a (9)), the
app's browser, signed out, mobile data; with the VPN through its US exit (Boston) 10:52–11:43 UTC,
all 13 links; without it 11:46–11:53 UTC, links 1, 8, 9, 10 and 13 (link N is slot N). Wherever a
trial line showed: the same video yes, wrong rows 0. §7.3's done-when was not met (links 9 and
10, the duration).

| Link | VPN | What the app showed |
| --- | --- | --- |
| 1–2 (watch links) | on | the first lookup: "Master found none"; one Try again: rows (Master's 2, main's 3) |
| 1 | off | rows on the first lookup |
| 3–8, 11 | on | rows on the first lookup: Master's 1–2 (often "Quality unknown"), main's 1–3; the sizes fit the length |
| 8 | off | the same |
| 9 (a Page post's desktop link; it opens as `story.php`) | on, off | no ladder or trial line; "Found on this page 2": (a) the post's video (2:13, the owner: 2:12; 360x360) and (b) another video (1:00, 360x640, "360p · Data saver", "Video + audio"), both under the post's title and at first its thumbnail: (b) is a wrong item. The same video's share/v link (it opens as a watch page): Master's rows 2, main's 3, the same video yes |
| 10 (a group post's desktop link) | on, off | "Facebook trial: main's rows were set aside and Master found none" (main's 2 rows with the VPN, 3 without), also after playing the video on the page and Try again. The same video's share/v link (it opens as a reel): Master's rows 1 (240p only), main's 2, the same video yes |
| 12–13 | on, off | the page lands on Facebook's mobile sign-in page; no lookup; the Download button: "No video on screen to download. Scroll to a video and tap Download again." No wrong answer, but not slot 13's `PRIVATE_OR_UNAVAILABLE` (step 4's census) |
| all 13 | | every trial line "duration within 1 s: unknown", though the sheet shows the length (0:03 to 1:15:24): the ±1 s check never ran |

The owner's decision (2026-10-21, after this run): links 9 and 10 are desktop link forms; the
owner (the only user now) mostly pastes mobile share links (share/v, share/r), and those work
(links 9 and 10 by share/v gave Master rows). Link 9 must never offer a video that is not the
post's own; if its other video shows with the flag off too, it is main's bug (its fix the owner's
call). The trial on these two forms only if cheap and safe, else a documented limitation with the
evidence; the duration (a must) and links 1–2 stay as asked; §7.3's done-when amended (slots 9–10
there); the re-run adds one share/p link.

What the agent found, and S1a (10)'s fixes (behind the flag; flag off = today):

- **The duration (all 13 links).** The plugin player's answer (F1) names no length (its video's
  data: the ID, the HD and SD files, the picture size); S1a (9)'s test player wrongly had one, so
  its tests passed. F2's rows state one only where the page does (an ISO length); main's rows
  carry the page's. Master's rows stated none, so every line said "unknown" (the sheet measured
  the files itself). **Fix:** a Master row that passes S6 and states no length gets its file's
  own from the bytes S6 already read (`StartDuration`; the first 4 KiB, which hold it, as checked
  on real files' starts: a whole file's movie header comes first, a DASH file's own length and
  index fit in them): the movie header (`mvhd`), else a fragmented file's own length (`mehd`),
  else its index (`sidx`); no new request. The line then says yes or no whenever main's rows
  state a length.
- **Links 1–2 (watch links, VPN): "Master found none" on the first lookup.** That message is the
  trial's own answer, kept only when Master's answer was not used (no page to ask, another page,
  the page changed during the lookup, skipped); Master finding nothing says "Play the video on
  this page, then tap Try again." So the first lookup's answer was most likely dropped as "the
  page changed during the lookup": a watch link's page moves to the video's own address (or
  starts again) after the early lookup began, later with the VPN's slower answers. Not proven:
  the line named no step. **Fix:** the ladder's rows (F1, F2: the video's own, bound to its ID)
  stay while the same video stays open (the same address again, or the same video's other
  address); the tab's playback (F3) and every other answer still need the same page. When
  Master's answer is not used, Details now name the step ("master: facebook: Master's answer not
  used: the page changed during the lookup: another page"; the change: the page closed, the same
  address again, another address of the same video, another page) and add Master's own lines. No
  wait was added: the rows come from the plugin player (F1), not from the page's media, so a wait
  for the player's media would not help; the re-run's Details will show the step.
- **Link 9 (a Page post's desktop link): main's bug, and a documented limitation.** In the app's
  browser the post opens as Facebook's mobile page; its address becomes `story.php` with the
  Page's name, not a numeric ID. Main's post addresses (P3-FIX) need a numeric ID, so main knows
  no site page there: no lookup (main's or Master's), no trial line. The Download button then
  lists every video the page loaded ("Found on this page"), so the related video (b) is offered
  too. The flag changes none of this (no lookup runs), so (b) shows with the flag off too:
  **main's bug** (the browser's list on a Facebook page main does not know); its fix is the
  owner's call, not in S1a (10). The trial on this form would need main's address rule changed
  (main's code) → a documented limitation; the same video's share/v link gives Master rows.
- **Link 10 (a group post's desktop link): a documented limitation.** Main reads the post and
  finds its video (its rows set aside by the trial), but the post's ID is not the video's: F1 is
  not asked (no video link resolved); F2 binds no row to the post (rows are bound to a video's
  ID); F3 (the tab's playback) checks the video in focus against the post, they never match, so
  the capture reloads the page and its answer is dropped ("the page changed during the lookup")
  → "Master found none", also after playing. Binding a post to its video needs the page's own
  data → not in S1a (10); the same video's share/v link gives Master rows. The re-run's Details
  will name the step. A share/p link that opens a post page may behave as link 9 or 10 (the
  re-run's extra link shows which).
- **Links 12–13: a documented limitation (docs only).** Signed out, Facebook sends these links to
  its sign-in page in the app's browser, so no lookup runs and the button finds no video: no wrong
  answer. Step 4's census reads slot 13 by main's own request (`PRIVATE_OR_UNAVAILABLE`), not in
  the browser.

**Step 6's re-run (the owner's go).** `yft-d7-fb-trial` of S1a (10)'s SHA, the app's browser,
signed out, with the VPN (Boston) and without it: the 13 links and one share/p link of a post
with a video (the app's Share on a post can give share/p, which may open a post page like link
9). Expected: wrong rows 0; every trial line says the duration yes or no (unknown only when
main's rows state no length); links 1–2 rows on the first lookup, else Details name the step;
link 9 as in the first run (main's bug, the owner's call) and link 10 "Master found none" with its
step in Details (documented limitations), their share/v links Master rows; links 12–13 as in the
first run (documented).

**Step 6's re-run — the owner's run (2026-10-21).** `yft-d7-fb-trial` of `042d57a` (S1a (10)),
the app's browser, signed out, mobile data; with the VPN through its US exit (Boston) 14:14–14:27
UTC, without it 14:31–14:45 UTC. **S1a's phone proof: PASS.**

| Link | Both networks |
| --- | --- |
| 1–8, 11 | Master's rows on the first lookup (no Try again); the trial line "Master's rows 2, main's 3; the same video: yes; duration within 1 s: yes" |
| 9, 10 by their share/v links | Master's rows (and one more share/v link, without the VPN) |
| 9 (a Page post's desktop form) | no lookup; "Found on this page 2" with the related video (b), with the flag off too: main's bug → decision 18 (§8) |
| 10 (a group post's desktop form) | "Master found none", Details name the step: the documented limitation |
| 12–13 | Facebook's sign-in page (documented) |
| share/p | not run: the owner found none (Facebook's Share gives share/v for a video post) |

- Master's wrong rows 0 everywhere. S1a (10)'s fixes hold: every trial line compared the lengths
  (yes), and links 1–2 gave rows on the first lookup.
- **For S5 (before the release):** Master's rows 2 against main's 3 on every link: fixed, or
  listed in `docs/SUPPORT_MATRIX.md` as an intended difference. **Task 7 (2026-10-22): listed**
  (main's audio row from the page's DASH manifest; §7.3's task 7 paragraph).
- Next: decision 18 (link 9's desktop form, main; §8; built 2026-10-21), then S2b. Facebook is
  re-baselined in the next phone session (below).

**Instagram's census (the owner's runs, 2026-10-21; S2d's input).** The census, mobile data, the
same day; no link or ID kept (S8).

- **15:00 UTC, without the VPN, signed out — Instagram's baseline (VPN off, signed out):** 38
  requests, 2.1 MB, 31 s. Main's GraphQL 401 on 10 of 10; main's rows on 6 of 7 video slots
  (slots 1–4 and 6 from the embed page, 7 from the page data); slot 5 (a reel) `LOGIN_REQUIRED`
  (the embed: no data); slot 8 (a photo post) `NO_MEDIA_FOUND`; slot 9 `LOGIN_REQUIRED`; slot 10
  (removed) `LOGIN_REQUIRED`, "ambiguous (no session)". Master's trials: needs playback on slots
  1–4 and 6; slot 7 rows 1, V pass.
- **15:04 UTC, without the VPN, signed in:** 41 requests, 0.8 MB, 20 s. Main by the app API: rows
  on 7 of 7 video slots, V pass 7. Slot 9 was a photo post (mislabeled; the owner replaced it with
  a sign-in-only reel). Slot 10 `RESPONSE_CHANGED` [recoverable] (the app API 400, GraphQL 400,
  the page and the embed no data): not as expected. Master's trials 7: rows 6, V pass 6 (slot 5
  needs playback); Master's tallest row differs from main's on slots 1, 4, 6 and 7.
- **15:13 UTC, with the VPN (Boston), signed in (the new slot 9):** 42 requests, 0.8 MB, 44 s.
  Main's rows on slots 1–7 and 9 (slot 9: 1 row, 360, 106.3 s), V pass 8; only slot 10 not as
  expected (`RESPONSE_CHANGED`). Master's trials 8: rows 6, V pass 6 (slots 5 and 9 need
  playback).

**X's B0 (the owner's runs, 2026-10-21).** The census with the phone's current debug APK (of
`042d57a`), signed out, mobile data; no link or ID kept (S8).

| VPN | UTC | Slots | Requests · MB · s | Main | Master's trials |
| --- | --- | --- | --- | --- | --- |
| on (Boston) | 15:52 | 8 | 40 · 0.1 · 23 | slots 1–6 rows, all as expected, V pass 6/6; one ask per lookup (`tweet-result` GET 200, about 0.5 s; slot 1 2.4 s); slots 7 (age-limited) and 8 (removed) `NO_MEDIA_FOUND` [recoverable], as expected; route B: needs playback | 6 (each: replayed 1, new 0): rows 6, V pass 4 |
| off | 15:56 | 8 | 40 · 0.1 · 11 | the same answers (main about 0.7–1.0 s per lookup) | the same |

- These runs are X's B0 (VPN on and off, signed out).
- **For S2c (X), the owner's inputs (2026-10-21; both runs show them; S2c must cover them):**
  1. Slots 3 and 6: Master's rows include an HLS row, Master's answer states no duration, and V
     checked the HLS row (unknown). An HLS row only with a known duration (S6); MP4 rows first; V
     checks an MP4 row.
  2. Slot 6 (several videos; main: item 1 of 2, 3 rows): Master's trial gave 8 rows, likely both
     videos'. Only the link's item (`/video/N`, else the first; decision 11); the trial compares
     the item.
  3. Slots 7 and 8 both answer "no post" with different shapes; Master says "needs playback" for
     the removed post too. If the answers tell them apart, removed → `REMOVED` (§6.4),
     age-limited → the browser; else today's reason stays.

  The proof: on these 8 slots Master's trial rows are main's item, with its duration, and V passes
  on every video slot.

  **S2c's answers (built 2026-10-21; `PROGRESS.md`):** 1 — the item's MP4 rows with main's length,
  each checked by V; an HLS row only when the item has no MP4 file and its length is known (V never
  passes a playlist: an HLS-only post ends in the tab's playback). 2 — the link's item only (slot
  6: video 1's rows, as main's); the trial's line compares the item ("the same item: yes" only when
  every Master row names main's one item by its file's path) and the duration. 3 — the live shapes
  (2026-10-21, signed out, shape only, nothing kept): an age-limited post → an empty tombstone (→
  the browser: slot 7 needs playback, "AGE_LIMITED … next: BROWSER"); a deleted post and a protected
  account's post → the same tombstone with X's notice link (not told apart → today's reason: needs
  playback); a made-up ID → 404 (→ needs playback, never final since task 0: a 404 alone does not
  prove deletion); no widget token → `{}` (→ `VALUE_REJECTED`, not final). Slot 8 (removed): needs
  playback whatever its answer (X's notice tombstone or a 404), main's reason when the tab's
  playback finds nothing, never "removed". Note: since task 6b the census's Master trial runs X's
  ladder (X1 on main's kept answer) in a social-ladder build (`yft-d7-social-trial`), so X's proof
  is the census's run; slot 8's playback (X3) still needs the app's browser (the census has no
  tab: `X3 not run (no tab)`).

**The next phone session (the owner, after this run's last pushed task — S2d part 1, task 6a's
one trial APK, task 0 (X's 404 never final), task 6b (the census's ladder), task 7, task 8 (W) and
task 9 (S6's reads on the lookup's site) are pushed;
`yft-d7-social-trial` and `yft-d7-normal` of the last pushed `[d7]` commit, never an older run's
APK (not `fb-trial`, not task 6a's); the run's report and `PROGRESS.md` name the commit and the
artifacts):**

- **The session of 2026-10-22 (`430da9f`'s APKs) and task 9:** B1 (link 9's desktop form), A's
  Facebook census (A-FB) and X's and TikTok's main results stand (not run again). A's X census
  (signed out, the VPN on, 10:48 UTC): slots 1–6's Master trials failed (`NETWORK`, each ask
  "unlabelled GET NETWORK 1–2 ms", the ladder's rows 0) as S6's reader allowed Facebook's media
  hosts only; task 9 fixes it. **A's Master parts again, with task 9's APKs, in this order:** X
  first (both networks; S6's reads answered on X's video host, no `GET NETWORK` ask of 1–2 ms),
  then TikTok after its 429's cooldown (30 min; `T1 not run (cooldown)` until then, never waited
  out within a run), then Instagram (its three sessions). Then B2 (Instagram's probe), B3 (the
  short regression set) and C below.
- **A — `yft-d7-social-trial`, "YFT site census" (the probe's box off), one run per network and
  session below:** each site against its baseline: wrong rows 0, no regression, unknown rows apart
  (wrong rows 0 alone is not parity); main's part (the site's line, B0's columns) apart from
  Master's (each site's `ladder:` line, each slot's `rungs:` line).
  - Facebook, signed out, the VPN Boston and no VPN: main's parts are the re-baseline after
    decision 18. Master: the ladder's rows of the same video, the duration within 1 s, V pass;
    Master's rows 2 against main's 3 (main's audio row: task 7's intended difference).
  - TikTok, signed out, B0's US VPN exit (Boston): T1's or T2's rows of the post on video slots
    (1–5, 10); slot 6 (photo) main's words, no request; slots 8–9 no Master row. T1's positives may
    need runs at least 30 minutes apart; after a 429 `T1 not run (cooldown)` — never waited out or
    bypassed within a run.
  - X, signed out, the VPN Boston and no VPN, B0's 8 slots: slots 1–6 Master's rows of main's item
    ("the same item: yes; duration within 1 s: yes"), slot 6 video 1's rows only; slots 7 and 8 no
    Master row (slot 8: `X3 not run (no tab)`), never "removed" (a widget 404 alone is not final:
    task 0).
  - Instagram: signed out, no VPN; signed in, no VPN; signed in, the VPN Boston. On every video slot
    main answers: the same item, the duration, the same tallest (the 360p rows unknown: decision
    20); slot 8 (photo) in main's words; the slots main does not answer (signed out: 5, 9 and 10) no
    Master row, never "removed".
- **B — the app's browser (signed out, no VPN unless said):**
  - Link 9's desktop form (a Page post): `yft-d7-normal` — main's rows of the post's video only (no
    list of the page's videos); `yft-d7-social-trial` — "Master found none" with its step in Details
    ("Master's answer not used: the page changed during the lookup: …"), no wrong row.
  - Instagram's tab-fetch probe, `yft-d7-social-trial`: the switch on ("YFT site census" →
    Instagram's tab-fetch probe), slots 1, 5 and 9: one `master: instagram probe:` Details line per
    lookup (HTTP status, bytes, the post's video in the answer yes/no, a wall on the page yes/no),
    no row from it, no second probe — a diagnostic, not a promise of rows (S2d part 2, the probe's
    rung, waits for it and the owner's decision); then the switch off.
  - A short regression set, `yft-d7-social-trial`: Facebook link 1 (a watch link: Master's rows of
    the same video, the trial line yes/yes; the sheet's Audio from the video's own sound) and link
    10 (a group post's desktop form: "Master found none" with its step); TikTok slot 6 (photo: main's
    words, no request) and slot 1 (the post's rows only); X slot 6 (video 1's rows only) and slot 8
    (needs playback, main's reason after the tab's playback finds nothing, never "removed");
    Instagram slot 8 (photo: main's words) and slot 1 (the post's item).
- **C — W's first run (S3, task 8), last:** `yft-d7-social-trial` (the same APK as A and B),
  "YFT site census" → **Weekly check W (all sites)**, the probe's box off; the links already in the
  census's slots (W reads only its slots: Facebook 1 video, 8 share link, 13 removed; TikTok 1
  single video, 6 photo post, 9 removed; Instagram 1 single video, 8 photo-only, 10 removed; X 1
  video, 6 several videos, 8 removed). Runs, each after A's census on the same network: (1) signed
  out, the VPN Boston (TikTok's B0 exit); (2) signed out, no VPN (TikTok's lines there are what W
  reads on that network, not a failure); (3) Instagram signed in, no VPN (W's baselines are per
  session). Caps per site: at most 60 s, 2 MB and 24 requests (the header's `ran, N requests, X
  MB, S s` line per site). Expected: the first line `W (weekly check): FIRST RUN — nothing to
  compare yet` (W's baselines start; the census's stay apart); each site `ran` within its caps;
  Master's part on slot 1 only (in this APK the site's ladder: rows of the same video or item,
  wrong rows 0), the other W slots `W: main only`; a site cooling after A's runs (a 429 or a bot
  check) `not run (cooldown N min (…))` — never waited out, not a pass: W again after the cooldown.
  A later W run (another day, the same network and session) is the first comparison: `NO CHANGE`,
  or CHANGED / BREAKING lines each ending `→ W runbook step N (…)` (§7.5: what to check, the fix).
  Copy report and Copy JSON (the export says `"mode":"weekly"`).

**For S2b (TikTok), noted now** (inputs to S2b's design, §7.3; not decided):

- T2 before T1: T2 reads main's pages (`MainPages`, S1a (2)) with no fetch, so a success saves an
  embed ask; its V and init-check requests still count in the budget. The order may be the site
  table's data (L4).
- After T1's 429 or bot check: no new request to that host in the lookup (S7); T2 may still run
  (no request); then "needs playback" (T3 is the user's own playback in the visible tab), never an
  automatic capture or a retry.
- TikTok's own budget in `SocialBudget` (today Facebook's only), sized by TikTok's main, T1 and
  V, not copied from Facebook's.
- The proof: positives (named public slots give Master rows: the item ID matches, the duration
  within ±1 s of main's, V pass) and wrong rows 0; no Master row on slots 7–9 (private, region,
  removed; S2); per way: run, not run (cooldown), rows, wrong rows. A D7-style APK with main
  mutated tries T2 with no embed ask; T1's positive slots may need runs at least 30 minutes apart.
- One hypothesis for one controlled test (the owner's go, as it uses the user's session): T1 with
  YFT's browser cookies to TikTok's own host (S2 allows it, as main does); so far the 429s came
  only on the embed's asks without a cookie.

**S2b's choices (built 2026-10-21; `PROGRESS.md`):** T1 before T2 — "T2 before T1" not chosen:
TikTok's page files open only with the cookies of the page answer that gave them (R20:
`tt_chain_token`), which V never sends (S2) and `MainPages` never keeps, so T2's rows would mostly
fail V, while T1's embed files open without cookies; the order stays code until a phone run shows
T2's rows pass V. After T1's 429 or bot check: no new ask to the host in the lookup, T2 still
reads (no request), then the tab's own playback (never a capture or a retry by itself). TikTok's
own budget (`SocialBudget.TIKTOK`: 20 requests, sized by main's at most 19 asks and T1's one; 4 MiB
of Master's answers; 30 s). Not built (the owner's go): the mutated-main APK for TikTok (on the
phone T2 runs when T1 cools or the budget is spent) and T1 with YFT's browser cookies.

**Parked:**

- Master's two-source rule (final only when all of main's pages show the reel's `video: null` and
  F1 shows a language-independent unavailable signal): after the release, only with evidence.
- A private reel's message to the user: today the `RESPONSE_CHANGED` wording ("the page could not
  be read … try again") misleads; only the message would change, not the reason; decided after
  S1a's phone proof, by what the user sees.

### 7.5 W's runbook (task 8, S3)

W's runbook (task 8, 2026-10-22): what each line of the weekly check W says, what to check and how
to fix it. W is the census's own steps on a few slots: "YFT site census" → **Weekly check W (all
sites)**, debug only, in a social-ladder build only (behind the flag: `yft-d7-social-trial`; every
other build's census screen has no W, as before), on the owner's tap (`SiteWeekly`; `SiteCensus.run` with
`ClientCensus.Mode.WEEKLY`; YouTube's W's design — `ClientCensus`'s caps, `CensusDiff`'s lines with
a runbook, `MASTER_KEY_PHASE1_2_PLAN.md` §6's numbered steps — with no YouTube file changed).

- **Slots** (case kinds, never links; the census's own slots, so nothing new to fill): Facebook 1
  (video), 8 (share link), 13 (removed); TikTok 1 (single video), 6 (photo post), 9 (removed);
  Instagram 1 (single video), 8 (photo-only), 10 (removed); X 1 (video), 6 (several videos), 8
  (removed). An empty W slot: `W slot N (kind): no link, not run`; a site with none: `not run (no
  link in W's slots)`.
- **Each way at most once:** main's lookup once per W slot (no retry), V once on main's best row,
  Master's part on slot 1 only (the site's ladder once, as the social-ladder build's census runs
  it: each rung at most once, S7), V once on Master's best row; the other W slots `W:
  main only`. Instagram's tab-fetch probe is never part of it.
- **Caps per site:** at most 60 s, 2 MB (YouTube's W's) and 24 requests (sized from B0: Facebook,
  the most asking site, about 5 per slot with V, so three slots and one Master part fit with room),
  each checked before every step (main's lookup, V, Master's part); the time left also bounds each
  step, so no site runs past 60 s (a step it cuts is not run: `time`). A step that starts under the
  request and byte caps finishes, so a site can pass them by that one step's asks; the header
  prints each site's spend (`ran, N requests, X MB, S s`). A cap reached stops the site's rest
  (`stopped (requests | bytes | time): the rest not run`), with no cooldown.
- **Stops and cooldowns:** the census's (a 429 or a bot check stops the site and cools it 30 min;
  in Master's part, only Master's parts): a cooling site reads `not run (cooldown N min (why))` —
  never waited out, never retried, never a pass. W's first line names each site not run or stopped.
- **Baselines:** W's own (per site, VPN and session, keyed as the census keys them; the last W run
  whose main phase completed), apart from the full census's: W never compares with a census run,
  nor the census with W's. W's export says `"mode":"weekly"`; the full census's report, export and
  state are as before (a state gets its `weekly` list only once W ran).
- **Lines:** first `W (weekly check): FIRST RUN | NO CHANGE | CHANGED (n) | BREAKING (n) | NOT
  COMPARED …` (then each site not run or stopped); then one line per change, each ending `→ W
  runbook step N (…)`, the step below. A slot W did not look up is a note, never NO CHANGE.

**The fix, two kinds.** *Data:* once S4 is live (R9's signed config, hosted by the owner; §8 item
5, open), a recipe key or path, a site's media hosts or a rung's kill switch changes by config, with
no APK. *Code:* a commit on the work branch (JVM tests, the census, W again, the phone). Until S4 is
live, every fix below is code. Main's adapters are main's code: a main fix waits for the owner's
go; meanwhile Master's ladder (behind the flag) is the backup.

1. **W runbook step 1 (the site's stop)** — `BREAKING <Site>: stopped (429 | bot check | rate
   limit)` where the baseline had none; `CHANGED <Site>: stopped (requests | bytes | time)` (a W
   cap); `CHANGED <Site>: no stop (the earlier run stopped: …)`. Check: the header's spend line
   against the baseline's, the network (the VPN exit), the time since the last run, and whether the
   full census on the same network stops too. Fix: a 429 or a bot check is the site's limit, not a
   fault: W skips the site for 30 min (not run); run W again after it, another day if it repeats;
   never a bypass (guardrail S3) or a retry. On every run and network → the site tightened: code
   (fewer asks: main's order, Master's budget). A W cap → the site asks or moves more than in the
   baseline: compare the slots' `main:` lines (asks, repeats, variants) with the baseline's; a new
   ask is step 2's; bigger pages → code (the cap in `SiteWeekly`, re-sized only on the owner's OK).
2. **W runbook step 2 (main's answer)** — `<Site> slot N (kind): main <before> → <after>`; BREAKING
   when main's rows are gone. Check: the slot's `main:` lines (each ask's way, status, reading and
   shape fingerprint) against the baseline's; the full census and Home's own lookup of the same
   post. Fix: a status on a public post (403, 404, 410) → open the post in a browser: removed or
   private → a new link for the slot (the census screen; the next run reads `new link`); a new
   reading or shape (the site moved its data) → main's adapter: code in `extractor-sites` with a
   fixture, on the owner's go; meanwhile Master's part covers it (step 6), and with S4 live a
   Master rung's key or path is data.
3. **W runbook step 3 (the slot's verdict)** — `as expected → not as expected (why)` (BREAKING),
   the reverse, or `ambiguous (no session)`. Check: main's line of the same slot (step 2), the
   session (step 8), the slot's kind and its expectation (`SiteWays`). Fix: main's answer changed →
   step 2; the post changed (deleted, private, region-limited) → a new link for the slot; the site
   now answers the case another way (a removed post's reason) → code: the slot's expectation
   (`SiteWays`), on the owner's OK.
4. **W runbook step 4 (V)** — `V pass → fail` (BREAKING), `V … → not run`, `V … → unknown`. Check:
   V's line (status, bytes); `not run` follows a stop or a cap (step 1) or main's rows gone (step
   2); `fail` with 403 or 410 → the file wants the page's cookies or a fresh signature; `unknown` →
   another host (or HLS). Fix: a new media host or path → data via R9 once S4 is live (the site's
   media hosts), code before; a signed file address → code (the reading of the address); V never
   sends cookies (guardrail S2).
5. **W runbook step 5 (the item or the length)** — `item N of M → …`, `best X s → Y s`. Check: the
   post (a re-upload, a changed carousel or several-video post), X's `/video/N`, Instagram's
   carousel index. Fix: the post changed → a new link for the slot; main reads another item → code
   (main's item rule) with a fixture; Master's rows are bound by item and duration, so a mismatch
   there is step 7's.
6. **W runbook step 6 (Master's part)** — `Master <before> → <after>`; BREAKING when route B's
   recovery (rows that passed V) is lost. Check: the slot's `Master` and `rungs:` lines (each rung:
   rows, run, not run (cooldown, budget), not reached) and main's line (step 2). Fix: a cooling host or a spent budget → no fix (W again after the
   cooldown); a rung's reading moved → data via R9 once S4 is live (the rung's recipe key or path;
   its kill switch while it is fixed), code before (the site's ladder in `extractor-master`, behind
   the flag).
7. **W runbook step 7 (Master's wrong rows)** — `BREAKING … Master … wrong 0 → … wrong N`: a row
   proven another post's (another video or item, a duration more than 1 s from every one of main's
   rows, or a row where the slot wants none). Check: the slot's `ladder:` and `rungs:` lines (which
   rung gave it) and main's rows. Fix: first, before anything else — the rung's kill switch via R9
   once S4 is live, else code (the rung's binding to the post: its ID, item or duration check), and
   the site's flag stays off; S5 and the release gate need wrong rows 0.
8. **W runbook step 8 (the run's conditions)** — `session no → yes` (or the reverse), `kind A → B`.
   Check: the header's network, the site's sign-in in YFT's browser, the build. Fix: none in code —
   W's baselines are per VPN and session, so run W again in the baseline's conditions; a kind
   change means the census's slot list changed (another build): its baseline starts again.

**W's first run** (C of §7.4's next phone session; the release gate's "the first W run passed" is
the owner's call): its first line is `FIRST RUN` (W's baselines start). What to look for: each site
`ran` within its caps (a cap stop or `not run` is not passed: W again after the cooldown), main's
part of each W slot as in the site's census run of the same network and session, slot 1's V pass,
and Master's part on slot 1 with wrong rows 0. The next W run (another day, the same network and
session) is the first comparison: `NO CHANGE`, or lines with their steps above.

## 8. Owner decisions

Decided (the owner, 2026-10-17; details in §2.1):

7. **Routing:** B — Master acts only after main fails, for all four sites, as site packs inside the
   generic engine; no claiming module, no rung-0 copy. B+ later (adds only missing formats, main's
   rows untouched). A only if B+ is measured insufficient, through a real Master-owned route state.
8. **Lookup scope:** the user's whole lookup (main + Master), with the per-lookup request recorder
   (memory only) that the contract stage and the S0 census read.
9. **R8 ladder:** a small, budgeted ladder per site pack in the contract stage; each new rung on the
   owner's go.

Decided (the owner, 2026-10-20; §7.3):

- **1 — Pilot and order:** the pilot Facebook, then TikTok, X, Instagram (S1a, S2b–S2d). S0d's
  shares did not separate Facebook and TikTok (0 of 11, 0 of 6); Facebook has the most kinds and
  ways, and Master's way gave rows on 8 of 8 trials.
- **2 — X2** (X's signed-out web API with a guest token): not now; S0d's embed answer carried every
  video post's media (2 of 6 only in the card, fixed in main by §7.3 item 0.1). After the release,
  on the owner's go.
- **4 — Third-party services in Master:** none; main's `tikwm.com` stays main's.
- **6 — Census test lists:** the owner's public posts, kept on the phone only; a gone link is
  replaced on the phone.
- **10 — Main fixes:** exceptions to S1, each its own commit on the owner's go: X's card (go, item
  0.1); Facebook's access check (after item 0.3's evidence); Instagram (after item 0.2's check). X's
  age-limited and removed posts keep main's reason (`NO_MEDIA_FOUND`, by design).
- **11 — One item per link:** Instagram's `img_index`, X's `/video/N`, else the first item; an "all
  items" feature after the release.
- **12 — The release gate:** S0–S5 for all four sites before PR #2's merge and P8 (beta.4); S1b and
  B+ (S6) after the release.

Decided (the owner, 2026-10-21; §7.4):

- **13 — The census fix 0.4 (c):** go (debug builds only): every slot's main first, then route B,
  then the trials; a stop in Master's part keeps main's results; a baseline per site, VPN and
  session; "NOT COMPARED" when nothing is compared; Facebook's slot 12 signed out reads "ambiguous
  (no session)" (counted apart; signed in: rows, as today).
- **14 — Facebook's access fix (A1, "0.3 fix"):** go (item 10's exception, its own commit): main
  reads 0.3's route only when the page has no playable media and no existing marker matches; the
  English markers stay.
- **15 — A reel's `video: null` (A2):** not a final answer in main (closed); recoverable by design,
  as in Master's judge. The two-source rule is parked (§7.4).
- **16 — One phone test:** the APK of both commits, Facebook only with and without the VPN, the
  same day (§7.4 step 4); not one per commit.
- **17 — TikTok after the Salt Lake City run:** no TikTok run with today's APK, no VPN-exit or
  slot-split runs; TikTok's B0 is §7.4 step 4's first run (the APK of both commits, the VPN's US
  exit). The census fix also keeps the two stops apart, compares no Master part that did not run,
  cools only Master's parts after a Master-phase stop, and runs the trials after main's final
  answers first. `PRIVATE_OR_UNAVAILABLE` and `GEO_RESTRICTED` stay final. The S2b notes (§7.4)
  are inputs, not decisions.

Decided (the owner, 2026-10-21, after step 6's re-run and Instagram's census; §7.4):

- **3 — Instagram's `doc_id` (moved from "Still open"):** no `doc_id` self-healing now (no S1b for
  it). The query's values are data (S9) with their source and date; `VALUE_REJECTED` → the next
  stage, never a guessed value (S5). If ever: Instagram's own scripts as the visible tab loads
  them; yt-dlp's file stays the owner's local reference, never read by the app. Revisited only
  when `VALUE_REJECTED` is seen (W or the phone) and a value-only change would fix it. (0.2's
  recommendation, 2026-10-20: in yt-dlp's file the post's query changed twice in four years, each
  time with a new request, never the value alone; today's signed-out request needs impersonation,
  S3.)
- **18 — Link 9's desktop form:** fixed in main (an exception to S1, its own commit), before
  Facebook's S5; Facebook is re-baselined in the next phone session. **Built (2026-10-21):**
  `FacebookUrls`' story.php and permalink.php take a Page's name as the owner's `id` too (not one
  of Facebook's surfaces) → the Page's `/posts/` path, a post identity, as the numeric form; flag
  off and on alike. The posts path answers without a redirect (sandbox, 2026-10-21), so Master's
  trial on this form meets link 10's limitation (no video link for F1; F2 binds no row to a post).
- **19 — Instagram's visible-tab fetch (0.2's verdict (b)):** Instagram's stage may use the
  visible tab's own fetch, only so: (i) every tab-fetch request goes through the request recorder
  and the ledger (the budget; a 429 or a bot check → the stop and the cooldown; no second ask;
  the census and Details show them); (ii) no `addJavascriptInterface` on the visible tab:
  `evaluateJavascript` with a read-back (as `BrowserReads`), or `addWebMessageListener` for
  Instagram's origin only; (iii) only on `instagram.com` in the visible tab, only after main
  fails, the last request rung, behind its own switch; (iv) an answer for a post whose page shows
  a sign-in or age wall is never used (S2); (v) first a debug-only probe (S2d part 1), the rung
  only after the owner's phone result; (vi) a removed post is the judge's call from what was
  already read, never a tab fetch.

Decided (the owner, 2026-10-22, before tasks 0–8; recorded in task 0's commit; decisions, then
each one's implementation status):

- **20 — Instagram, S2d part 1's intended differences: accepted.** (i) Master's rows may keep the
  360p file main drops, only when they are the link's post and item: never wrong rows, never fewer
  of the higher qualities main offers. (ii) A removed post is never final from today's ambiguous
  answers (decision 19 (vi)); main's reason stays. Neither authorizes S2d part 2's tab-fetch rung
  or `doc_id` self-healing (decision 3 stands). *Status:* both as built in S2d part 1 (`dd761c8`);
  not yet proved on the phone (§7.4's next phone session); the census's site packs (task 6b)
  compare Master's rows with the census's own main answer.
- **21 — X, S2c's intended differences.** (i) `{}` → `VALUE_REJECTED`, not final: accepted. (ii)
  X's notice tombstone (a deleted and a protected account's post look alike) → `NEEDS_PLAYBACK`,
  not final: accepted. (iii) The widget's 404 → final as removed: **not accepted**: a 404 alone
  must not establish deletion or end the recovery. *Status:* (i) and (ii) as built in S2c
  (`efc3670`); (iii) changed by task 0 (2026-10-22, §7.3): the 404 → `NEEDS_PLAYBACK`, never
  final; the existing playback and browser fallback follow; when they find nothing, main's reason
  stays. Not yet proved on the phone.
- These acceptances are not a waiver: each site's phone proof, its regression check and the
  release gate (decision 12) still apply.

Still open (the owner's answer is needed before the step that uses it):

- **5 — R9 hosting:** the owner signs and hosts the recipe config (needed for repair without an
  APK); decided before S4.

## 9. Tests and docs per step

- JVM unit tests; simulator worlds with mutations; post-identity mutation tests (today's generic test
  covers only JSON with an `id`); recorder tests (main's asks seen, no repeated ask, stop after a 429
  or a bot check); parity tests unchanged; drift check for every symbol copy; `FixtureBaselineTest`;
  CI green before any APK.
- No-regression measure (S1): requests and time per lookup on the lookups main answers, before and
  after, on the census list. **Baseline B0** (§7.3 item 0.5) is "before": the owner's run in a fixed
  config — all four sites through the owner's VPN with the same US exit as S0d, and Facebook,
  Instagram and X without the VPN, at the same weekday and time window each time — with the APK of
  items 0.1 and 0.4; "after" is the same config with the step's APK. A site is re-baselined after
  any new main fix. A step's requests and time on main-success lookups may not exceed B0's.
- Docs per change: `PROGRESS.md` entry, a `docs/TEST_MATRIX.md` row, `docs/SUPPORT_MATRIX.md` where
  a site's behaviour changes, and this plan's step status.

## 10. Risks

- Sites change fast. The kit turns most fixes into data changes and W warns early, but a full redesign
  of a site still needs code.
- Sign-in walls (Instagram, Facebook) are not "fixed": next level means a clear reason and the visible
  tab's own playback, never a bypass.
- More rungs means more requests: budgets, cooldowns, the recorder and the memo are required, not
  optional.
- Under B, Master never improves a lookup main answered, even a weak one (no HD, no sound); B+ is the
  planned answer, measured first. Main's own habits (e.g. Instagram asking on after a 429) stay
  main's until the owner decides otherwise.
- The recorder sees only requests through the shared HTTP client, not what a page loads in the
  browser (hidden pages, the visible tab); the census export says so.
