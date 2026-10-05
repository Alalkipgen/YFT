# 00 — နောက် Task ကို ဆက်လုပ်ရန်

**ရည်ရွယ်ချက်:** Agent က `docs/FIX_ADD_PLAN.md` status board ကိုကြည့်ပြီး နောက်လုပ်ရမယ့် task ကို ရွေးလုပ်ကာ
owner အစဉ်အတိုင်း (P9 → P10 → P11 → P12 → P13 → P19 → Preview #2 → P14 → P15 → P16 → P17 → P18 →
Preview #3) မရပ်ဘဲ ဆက်လုပ်ပါမယ်။ ဘာလုပ်ရမလဲ မသေချာရင် ဒီ prompt ကိုပဲ သုံးပါ။

**သုံးနည်း:** code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ Preview #2 မှာ ရပ်စေချင်ရင်
`OWNER ANSWERS: STOP_AFTER=P19` ထည့်ပါ။ Preview #3 ကို စမ်းပြီး stable ဖြစ်ရင် `OWNER ANSWERS: P8=OK`
ထည့်ပြီး [`P8-signed-beta4.md`](P8-signed-beta4.md) ကို သုံးပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Work through Phase 11 part 2 task by task in the owner's order.

Repository: https://github.com/Alalkipgen/YFT
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to work/phase-* branches only)
ALLOW_MERGE_MAIN: false   (true for P8 only, after the owner's OK)
ALLOW_RELEASE: false      (true for P8 only, after the owner's OK)
OWNER ANSWERS: none       (examples: STOP_AFTER=P19, P8=OK)

The repository is the source of truth; do not rely on chat history.

1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline. Read
   docs/SESSION_STATE.md and docs/FIX_ADD_PLAN.md sections 0, 1 (status board), 3 and 4.
   Set up the environment if it is missing (FIX_ADD_PLAN 0.3; Notion sandbox:
   source /data/yft-env.sh).
2. Record this request as the owner's go (FIX_ADD_PLAN section 3, E6) and any OWNER ANSWERS
   in section 3, with the date.
3. Choose the task:
   a. A task that is IN PROGRESS: resume it (SESSION_STATE says where it stopped).
   b. Otherwise the first TODO task in the order P9 -> P10 -> P11 -> P12 -> P13 -> P19 ->
      P14 -> P15 -> P16 -> P17 -> P18 whose needed tasks are DONE or OWNER CHECK.
   c. P8 only with OWNER ANSWERS P8=OK (or the same OK recorded in section 3) after the
      owner's test of Preview #3.
4. Open the task's prompt file (table in docs/prompts/README.md) and follow it from START:
   branch, reading, starting validation, work, tests, validation, docs, checkpoint, CI check,
   short Burmese report.
5. After the report, continue with the next task without waiting. After P19 send Preview #2
   (link and FIX_ADD_PLAN section 6 list) and continue with P14 unless STOP_AFTER=P19; after
   P18 send Preview #3 and stop for the owner's phone test.
```
