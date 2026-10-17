# Master Key — Phase 1.4 item 7 agent prompt: YouTube routing B (R1–R6)

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
