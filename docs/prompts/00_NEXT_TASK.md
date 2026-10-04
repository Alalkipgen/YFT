> Continue with [CONTINUE-P2-TO-P6.md](CONTINUE-P2-TO-P6.md): P2 is in progress.

# 00 — နောက် Task ကို ဆက်လုပ်ရန်

**ရည်ရွယ်ချက်:** Agent က `docs/FIX_ADD_PLAN.md` status board ကိုကြည့်ပြီး နောက်လုပ်ရမယ့် task ကို
ရွေးလုပ်ပြီး owner အစဉ်အတိုင်း (P1 → P7) မရပ်ဘဲ ဆက်လုပ်ပါမယ်။ ဘာလုပ်ရမလဲ မသေချာရင် ဒီ prompt ကိုပဲ
သုံးပါ။

**သုံးနည်း:** code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ P7 preview APK ကို စမ်းပြီး
stable ဖြစ်ရင် `OWNER ANSWERS: P8=OK` လို့ ထည့်ပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Work through the Phase 11 plan task by task in the owner's order.

Repository: https://github.com/Alalkipgen/YFT
Branch: work/phase-11-download-flow
ALLOW_PUSH: true          (checkpoint pushes to work/phase-* branches only)
ALLOW_MERGE_MAIN: false   (true for P8 only, after the owner's OK)
ALLOW_RELEASE: false      (true for P8 only, after the owner's OK)
OWNER ANSWERS: none       (example: P8=OK)

The repository is the source of truth; do not rely on chat history.

1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline. Read
   docs/SESSION_STATE.md and docs/FIX_ADD_PLAN.md sections 0, 1 (status board), 3 and 4.
2. If OWNER ANSWERS has values, record them in FIX_ADD_PLAN section 3 with the date.
3. Choose the task:
   a. A task that is IN PROGRESS: resume it (SESSION_STATE says where it stopped).
   b. Otherwise the first TODO task in the order P1 → P2 → P3 → P4 → P5 → P6 → P7 whose
      needed tasks are DONE or OWNER CHECK.
   c. P8 only with OWNER ANSWERS P8=OK (or the same OK recorded in section 3).
4. Open the task's prompt file (table in docs/prompts/README.md) and follow it from START:
   branch, reading, starting validation, work, tests, validation, docs, checkpoint, CI check,
   short Burmese report.
5. After the report, continue with the next task without waiting; stop after P7 so the owner
   can test the preview APK.
```
