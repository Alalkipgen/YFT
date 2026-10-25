# Master Key — Phase 1.2 continuation prompt (finish N4b: M9b/M9bs measurement, local)

**ရည်ရွယ်ချက်:** N4b code ပြီးပါပြီ (`bdb45c6`, CI ✅)။ ကျန်တာက computer ပေါ်မှာ M9b/M9bs
တိုင်းတာမှုနဲ့ faults အချို့ပဲ ကျန်ပါတယ်။ Agent အသစ်ထဲကို အောက်က code block ကို paste လုပ်ပါ။
ပြီးရင် push လုပ်ပြီး မြန်မာလို ✅/❌ နဲ့ report ပေးပြီး ရပ်ပါမယ်။

**အခြေအနေ (2026-10-11): ပြီးပါပြီ ❌** — worker B M9bs အဖြေမှား ၁ (631d3938 es6, sig)။ Fix ကို owner
ဆုံးဖြတ်ရန်။ ဒီ prompt ကို ထပ် run စရာ မလိုပါ (PROGRESS.md "N4b")။

```text
You are a coding agent on YFT (Android video downloader; Kotlin, Compose, Hilt, OkHttp, WebView).

Repository: https://github.com/Alalkipgen/YFT
Branch: spike/master-extractor-backup   (push ONLY this branch; draft PR #2 stays a draft)
ALLOW_PUSH: true   ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
FIRST read: MASTER_KEY_PHASE1_2_PROMPT.md (HARD RULES), MASTER_KEY_PHASE1_2_PLAN.md (section 3
N4 and N4b, section 4 notes "N4"/"N4b"), PROGRESS.md top section "N4b", then this file.
START_AT: finish N4b (measure only; do NOT build the fix), push, report, stop. N5 only after the
owner says "Next".

OWNER: Burmese, short, ✅/❌. No computer, mobile data only. Token budget is tight: start long
runs with nohup, then wait with ONE blocking call (a shell loop that sleeps until a "done" line
appears, read with the wait tool at 300 s); do not poll in small steps; print only summary lines.

STATE (verify against the repo)
- N4 done (a7c3dd9, CI 38170962057). N4b code bdb45c6 (CI 38175876765 ✅); docs save 8321773 and
  the docs commit after it. Runner (scripts/verify-own-solver-mutations.mjs) has --both,
  --variant and settle() after every solve (last commit; not yet run).
- Done results: init errors 0/32 files in worker P, worker B, page P, page B (corpus 32/32 =
  ejs 260/260 in all four). Faults worker P 384 runs: 10 earlier cases wrong 0 / missed 0;
  init-wrong-n 132 wrong, init-wrong-sig 128 wrong (❌ by construction). Worker B init-wrong:
  132 / 128 wrong (❌ by construction). Page P init-wrong: wrong 0 (inert). N4 page faults: P 320
  runs wrong 0; B not run.
- M9b/M9bs --both: only 7/64 cases ran (wrong 0: worker P/B safe, page P survived, page B some
  "n: no candidates" — B's call-shape keys are static constants, M9b makes them DOM-dependent).
  Then the runner HUNG on 74edf1a3 phone M9bs in strategy B (P alone finishes in 5 s; B with
  `timeout 240` was killed). The vm timeout (60 s) did not stop it: likely player microtasks
  (contexts use the default microtaskMode, so promise jobs run outside the timeout during
  settle()) or a native call. RSS grew to ~1.5 GB.

TODO (in this order, at most two heavy runs at once, 2 CPUs / 4 GB)
1. M9b/M9bs per file, a fresh process each with an external limit, so one hang costs one file:
   for each player x variant (corpus order 74edf1a3 901741ab e7573094 9fcf08e8 21cd2156
   76ad2fe8 631d3938 5203c085; main tce es6 phone):
     timeout 300 node --max-old-space-size=1024 scripts/verify-own-solver-mutations.mjs \
       --only <p> --variant <v> --class M9b,M9bs --strategy P,B --both
   Keep stderr result lines only (grep '^[0-9a-f]{8} [a-z0-9]+ M9bs?: '). Owner's goal: keep
   memory low (< ~0.6 GB per process as in N3) so nothing hangs; if one file still hangs, rerun it
   with --strategy P and --strategy B separately and report "harness timeout (B)" for it. A
   timeout counts as a safe failure (no value), never as survived.
2. Merge the lines into rows: M9b and M9bs x (ejs | worker P | worker B | page P | page B),
   survived/safe/wrong. Separate rows; the 448 baseline stays untouched.
3. Faults: page B init-wrong (`--faults --page --strategy B --fault-cases
   init-wrong-n,init-wrong-sig`, expect wrong 0) and N4's page B faults (`--faults --page
   --strategy B`, all cases; init-wrong inert there).
4. Owner's stop rule: if M9bs gives ANY wrong value in the worker -> ❌ report and STOP (do not
   fix). If ejs also gives M9bs wrong -> separate ❗ "affects main too"; do not touch main.
5. Docs: plan section 3 N4b "Built", section 4 note "N4b" (rows + init errors + faults), section 5
   risk (hang; B static keys), PROGRESS.md N4b section, then a Burmese ✅/❌ report and STOP.

PROPOSED FIX (owner decides later; do not build now)
- Worker and frame replies carry init-error count + classes (classes only).
- Init errors > 0 => the worker answer is unverified: E runs; E's answer is used when E has 0 init
  errors and passes SelfCheck (sig: or V passes); if E also has init errors, sig needs a V pass and
  n fails. Faults then judge the ladder (unverified = no answer) and init-wrong turns ✅.
- Today it costs nothing (0/32 files have init errors). Optional later: taint guard.
- Also consider for the harness: vm contexts with microtaskMode "afterEvaluate", the result
  stored before microtasks run, so a player's promise loop cannot hang a run.

RULES LEARNED
- Player code only through the repo harness and static parsing; never commit player text; never
  log or paste solver inputs/outputs/URLs or player-code lines ("Maximum call stack" /
  "PromiseRejectCallback" lines are harmless noise: filter them out).
- Commands <= 25 s; long runs nohup; never combine `pkill -f` with other commands.
- No git credentials in the sandbox: push with the GitHub tool push_files (this branch only;
  large payloads as a JSON arguments file); docs-only pushes start no CI.
- The harness page is the N4 DOM stub: a page pass proves plumbing only; real WebView = N7.
```
