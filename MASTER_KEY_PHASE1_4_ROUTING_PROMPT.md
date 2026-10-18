# Master Key — Phase 1.4 item 7 agent prompt: YouTube routing B (R1–R6)

**2026-10-18:** R1–R6, F1, F2 ပြီးပါပြီ (D7 run 8 ဖုန်းစစ်ပြီး)။ ဆက်လုပ်ရန် follow-ups **F5 → F4** (plan §9 final
plan) အတွက် ဒီ file ရဲ့ နောက်ဆုံး section "Follow-ups F5 → F4" ကို သုံးပါ (အောက်က R1–R6 prompt က မှတ်တမ်းအဖြစ်သာ)။

**ရည်ရွယ်ချက်:** `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` ထဲက R1–R6 ကို agent တစ်ယောက်က တစ်ဆင့်ချင်း
လုပ်ဖို့ prompt ပါ။ main ကျရှုံးပြီး ပြန်ကြိုးစားလို့ရတဲ့ အကြောင်းပြချက်ဆိုမှ Master YouTube ဖြေမယ် (flag off = ယနေ့)။

**သုံးနည်း:**
- အရင် plan §5 ဆုံးဖြတ်ချက် ၅ ခုကို chat ထဲ ပြောပါ (မပြောရင် agent က plan ထဲက proposed အတိုင်း သုံးပြီး မေးပါမယ်)။
- Agent chat အသစ်ဖွင့်၊ Reasoning Level အမြင့်ဆုံး ထားပြီး အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။
- `RUN_MODE: continuous` — step တစ်ခု push ပြီးတိုင်း "✅ R1 ပြီးပြီ — …" ရေးပြီး ဆက်လုပ်ပါမယ်။
- "⏸" = ဖုန်းစစ် (R4 `routeb` APK) ဒါမှမဟုတ် owner ဆုံးဖြတ်ချက် / STOP AND ASK မှပဲ ရပ်ပါမယ်။ "ဆက်လုပ်" လို့ ရိုက်ပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
Plan: MASTER_KEY_PHASE1_4_ROUTING_PLAN.md (read it first, all of it). Context:
      MASTER_KEY_SOCIAL_PLAN.md section 2.1 (decisions 7 and 8), MASTER_KEY_PHASE1_3_PLAN.md
      guardrails and section 10, MASTER_KEY_ARCHITECTURE.md, AGENTS.md, PROGRESS.md (top entries).
ALLOW_PUSH: true          (spike branch only)
ALLOW_MERGE_MAIN: false   (draft PR stays a draft)
ALLOW_RELEASE: false
START_AT: R1
RUN_MODE: continuous      (after each step push, post one short note and go on at once)

TASK: build YouTube routing B, steps R1 -> R6 in order, one step per commit.
- Master answers a YouTube lookup only after main failed with a recoverable reason (plan
  section 2 set), behind a debug flag that defaults OFF. Flag off must be byte-identical
  behavior to today (P12: claimed page -> Skipped unless UNSUPPORTED_URL).
- Never touch main's success path. NEVER_FALLBACK and BROWSER_REQUIRED reasons stay final on
  YouTube. Do not change masterFirstSite (debug only). Never relabel a found page UNSUPPORTED_URL.
- Recorder (R1) is per lookup, memory only, never logged. Replay only identical unauthenticated
  answers; never keep or replay authorized (cookie) answers; after a 429 no new YouTube request;
  new requests only from rungs main lacks, inside existing budgets (H2 <= 1 player request).
- Locked, do not edit: the 5 copied YouTube files, ShapeLocator, ShapeNormalizer, Rungs.
  extractor-sites stays untouched. Out of scope: SABR, PO tokens, MSE, TV, bypass, cookies or
  sign-in, merge to main, release, B+ and A.

EACH STEP:
1. Write the tests from plan section 4 for that step first, then the code.
2. Run the unit tests locally; all existing tests stay green (parity, drift 0,
   FixtureBaselineTest, extractor-sites). Never weaken or delete a test.
3. Update docs for the change (PROGRESS.md top entry, plan "As built" note).
4. Push with a clear message; wait for CI green before the next step.
5. Post: "✅ R<n> done — <one line>" and go on.

R4: add the debug trial switch and the D7 entry "routeb" in .github/d7-apks.txt; push with
"[d7]" in the commit message; when the APKs are built, post the phone check list from plan
R5 and STOP with "⏸ phone check". Continue with R6 only after the owner reports R5.

STOP AND ASK (post "⏸ <what you need>") when: an owner decision in plan section 5 is missing
and the proposed value is unclear; a locked file or extractor-sites would change; flag-off
behavior would differ; a final reason would be answered; a test would need weakening; CI fails
twice on the same step; docs and code disagree.
```

## Follow-ups F5 → F4 (final plan, 2026-10-18)

**ရည်ရွယ်ချက်:** plan §9 (final plan) အတိုင်း **F5** (main ဖတ်ပြီးသား watch page အဖြေကို Master က lookup ထဲကနေ
တစ်ကြိမ်ဖတ်၊ cookie ဘယ်တော့မှ မပို့) ပြီးမှ **F4** (လမ်းခုလတ် ကျရှုံးမှု offline tests) ကို agent တစ်ယောက်က ဆက်လုပ်ဖို့
prompt ပါ။ Cookie rule ကို owner က agent ဆုံးဖြတ်ခွင့်ပေးပြီးပါပြီ (F5 ရွေးပြီး) — ထပ်မမေးရပါ။

**သုံးနည်း:**
- Agent chat အသစ်ဖွင့်၊ Reasoning Level အမြင့်ဆုံး ထားပြီး အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။
- F5 ကို `[d7]` နဲ့ push လုပ်ပြီး CI green ဖြစ်ရင် D7 run 9 APK တွေ ထွက်ပါမယ်၊ agent က F4 ကို ဆက်လုပ်ပါမယ်။
- F4 green ပြီးရင် ဖုန်းစစ်စာရင်း (plan §9 item 3) ပို့ပြီး "⏸ phone check (D7 run 9)" နဲ့ ရပ်ပါမယ်။ စစ်ပြီး
  Details စာကြောင်းတွေ ပို့ပါ။
- Promotion (normal build မှာ flag ဖွင့်) = owner ဆုံးဖြတ်ချက် (plan §9 item 4)၊ agent က မလုပ်ပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, OkHttp, Media3, Android WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch)
Plan: MASTER_KEY_PHASE1_4_ROUTING_PLAN.md (read it all; sections 2, 6, 7, 8 and 9 above all).
      Context: MASTER_KEY_PHASE1_3_PLAN.md guardrails and section 10, MASTER_KEY_ARCHITECTURE.md,
      AGENTS.md, PROGRESS.md (top entries).
ALLOW_PUSH: true          (spike branch only)
ALLOW_MERGE_MAIN: false   (draft PR stays a draft)
ALLOW_RELEASE: false
START_AT: F5
RUN_MODE: continuous      (after each step push, post one short note and go on at once)

STATE: R1-R6, F1 and F2 are done (F2 = 8584519, then the final-plan docs commit). D7 run 8 was
checked on the phone (plan section 7). The owner left the cookie rule to the agent; the choice is
F5 (plan section 9). Do not ask about it again.

TASK: the final plan (plan section 9), in order, one step per commit.

F5 (plan section 8) - main's watch page for route B:
- Change only extractor-master's lookup package (and tests); no locked file.
- The lookup's record also keeps main's answer to the lookup's watch page GET (the looked-up
  video's page address, one per lookup) when main's request carried the cookie: Set-Cookie
  dropped, inside the record's caps, dropped at the lookup's end, keyed without the cookie (its
  value is neither kept nor compared, and never appears in any text or log).
- Master's GET of that address (same method, address and body; same headers except Cookie) is
  answered from it once: replayed, counted, never sent.
- Unchanged: Master never sends a cookie or any other authorization (rule 1 still refuses every
  other authorized request); no other authorized answer is kept; Master's new requests carry at
  most what main's own requests carried (visitor data, P22), never the cookie, an authorization,
  the account's data-sync id or a PO token; at most 1 watch page per lookup (guardrail 7); after
  a 429 no new request; nothing logged; flag off and every other build unchanged (no record).
- Tests first: LookupRecordTest and RouteBHttpClientTest (main's page kept for main's lookup
  only, replayed once, within the caps and the lookup; other authorized answers never kept;
  another address refused; no cookie in any text), RouteBSimTest (bot-checked visionOS: Master
  complete from main's page, 0 credentials sent, <= 1 page), BrowserMasterFlowTest (the trial on
  a V2-like page: Master's row and "replayed 2").
- Push with "[d7]" in the commit message and .github/d7-apks.txt set to "run 9", so the owner
  gets the APKs.

F4 (plan section 8) - a failure midway, offline:
- App-flow tests (BrowserMasterFlowTest, BrowserMasterFallbackTest; the engine and the simulator
  where needed) where main asks and then fails with a recoverable reason (plan section 2), not
  the trial's found page. Check: (1) Master's rows reach the sheet with route B's notice and
  counts - from main's replayed visionOS answer, and with F5 from main's replayed watch page after
  a bot-checked visionOS answer (0 new pages); (2) when Master finds nothing, main's own failure
  stays (reason, message, Try again) with Master's details after main's; (3) no route B on main's
  final reasons (BOT_CHECK, LOGIN_REQUIRED, PLAYER_SCRIPT_REQUIRED, RATE_LIMITED, ...), nothing
  new after main's 429, main's success untouched, flag off = today (P12).
- Fix only what the tests find, in the same step. No "[d7]" unless app or extractor code changed
  (then move .github/d7-apks.txt to the next run number and name that run in the phone list).

After F4 is green: post the D7 phone check list from plan section 9 item 3 and STOP with
"⏸ phone check (D7 run 9)". Promotion (plan section 9 item 4) is the owner's decision: do not
turn -Pyft.youtubeRouteB on in normal builds.

Rules unchanged (plan sections 2 and 6): never touch main's success path; NEVER_FALLBACK and
BROWSER_REQUIRED reasons stay final on YouTube; do not change masterFirstSite (debug only);
never relabel a found page UNSUPPORTED_URL. Locked, do not edit: the 5 copied YouTube files,
ShapeLocator, ShapeNormalizer, Rungs; extractor-sites stays untouched. Not in scope: a
cookie-less watch page asked again; F3 (the joined lookup; reopen only if a Details line says
"another caller's running lookup (P17)"); any change to the trial's first open or main's cache;
promotion; SABR, PO tokens, MSE, TV, bypass, sign-in, merge to main, release, B+ and A.

EACH STEP:
1. Write the tests from plan section 8 for that step first, then the code.
2. Run the unit tests locally; all existing tests stay green (parity, drift 0,
   FixtureBaselineTest, extractor-sites). Never weaken or delete a test.
3. Update docs for the change (PROGRESS.md top entry, plan section 7 "As built", section 8).
4. Push with a clear message; wait for CI green before the next step.
5. Post: "✅ F<n> done — <one line>" and go on.

STOP AND ASK (post "⏸ <what you need>") when: Master would send a cookie or another
authorization, or keep any other authorized answer; a second watch page per lookup would be
asked; a locked file or extractor-sites would change; flag-off behavior would differ; a final
reason would be answered; a test would need weakening; CI fails twice on the same step; docs
and code disagree.
```
