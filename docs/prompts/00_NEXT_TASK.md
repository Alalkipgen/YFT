# 00 — နောက် Task ကို ဆက်လုပ်ရန်

**ရည်ရွယ်ချက်:** Agent က `docs/FIX_PLAN.md` status board ကိုကြည့်ပြီး နောက်လုပ်ရမယ့် task တစ်ခုကို
ရွေးလုပ်ပြီး owner အစဉ်အတိုင်း ဆက်လုပ်ပါမယ်။ ဘာလုပ်ရမလဲ မသေချာရင် ဒီ prompt ကိုပဲ သုံးပါ။

**သုံးနည်း:** code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။ D1–D3 ကို ဖြေပြီးရင်
`OWNER ANSWERS:` line မှာ ထည့်ပါ။ T10/T15 ကို owner က skip ခိုင်းထားပြီး T19 ပြီးမှ merge/tag/signed release လုပ်ပါမယ် (ခွင့်ပြုပြီး)။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Work through the fix plan task by task in the owner's order.

Repository: https://github.com/Alalkipgen/YFT
ALLOW_PUSH: true          (checkpoint pushes to work/phase-* branches only)
ALLOW_MERGE_MAIN: false   (true for T19 only: owner-approved 2026-10-03)
ALLOW_RELEASE: false      (true for T19 only: owner-approved 2026-10-03)
OWNER ANSWERS: none       (example: D1=YES D2=B D3=YES)

The repository is the source of truth; do not rely on chat history.

1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline. Read
   docs/SESSION_STATE.md and docs/FIX_PLAN.md §0, §1 (status board) and §3 (decisions).
2. If OWNER ANSWERS has values, record them in FIX_PLAN §3 as `YES/NO/A/B (date)`.
3. Choose the task:
   a. A task that is IN PROGRESS: resume it (SESSION_STATE says where it stopped).
   b. Otherwise the first TODO task in the owner's order (FIX_PLAN §3, owner instructions of
      2026-10-03) whose needs are met: every task it needs is DONE or OWNER CHECK,
      and every decision it needs is answered.
   c. A task blocked by a PENDING decision is passed over when another task of the same phase
      can go first (for example T12 before T11). If nothing can start, ask the owner the
      pending question(s) in Burmese, checkpoint nothing, and stop.
   d. T10 and T15 are SKIPPED. T19 comes last; it merges, tags and releases the APK signed
      with the release key (owner-approved 2026-10-03).
4. Open the task's prompt file (table in docs/prompts/README.md) and follow it exactly from its
   START section: branch, reading, starting validation, work, tests, validation, docs,
   checkpoint, CI check, Burmese report.
5. After the report, continue with the next task in the same order (owner instruction,
   FIX_PLAN §3) unless a PENDING decision blocks it.
```
