# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: 11 — Group A: P9 and P10 complete on the branch (OWNER CHECK). Next P11 → P12 → P13 → P19 → Preview #2. No merge or signed release before authorized P8.
- Rules: [ADR-006](decisions/ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03) — any working technique for public videos; still no DRM, paid, private-content or age-gate bypass; adapters never sign in; secrets never logged or committed. D2 = A + B + C. D1 = YES and D3 = YES (owner delegated the choice, 2026-10-03: "do as you see fit; do not stop; commit and push after every task").
- Current branch: `work/phase-11-download-flow` (from `main` `2f6284f`, tracking origin; part 1 ends at `ce3cd25`).
- Last completed task: P10 (OWNER CHECK, 2026-10-05) — 20 s idle/60 s attempt transport budgets, two 1/3 s retries, cancellable sockets, 90 s Home lookup with slow status/Cancel after 10 s, no background NETWORK banner and focused Retry. P9 `13bbf05` remains complete.
- Owner go (2026-10-05): start P9 and Group A (P9 → P10 → P11 → P12 → P13 → P19 → Preview #2). Do not change workflows or the Plan Doc. `docs/FIX_ADD_PLAN.md`, prompts and `.github/workflows/` are left byte-for-byte unchanged; actual progress is recorded here.
- Work in progress: none; P10 full validation passed, checkpoint next.
- Build status (P10): core-browser 79 tests; core-data 17 tests; app 607 tests (66 skipped); 0 failures/errors; :app:lintDebug 0 errors. Three regressions failed against actual pre-P10 client/Home code; restored/cmp verified.
- Known limitations: no local KVM; native PNG download requires authentication (HTTP 401), so no native pixel-review claim. Sandbox live checks use a datacenter IP: one YouTube test video stays bot-checked for every client, so YouTube needs the owner's phone check. The page player's own PO token is not captured (FIX_ADD_PLAN §7).
- Environment (sandbox reset again 2026-10-05; partly rebuilt): `source /data/yft-env.sh` sets JDK 17 (`/data/toolchains/jdk17`), the Android SDK (`/data/toolchains/android-sdk`: cmdline-tools, platforms 35/36, build-tools 34/35/36, platform-tools) and the Gradle cache (`/data/gradle-home`); `/data/gw.sh` runs the wrapper with `--no-daemon --max-workers=2`; repo at `/data/YFT`. NDK 27.3.13750724 and CMake 3.22.1 installed for P9. The 4 GiB limit killed the first baseline daemon; unchanged baseline passed with `/data/gw-safe.sh` (one worker, 768 MiB Gradle heap, in-process Kotlin compiler). Use that helper for subsequent local validation; workflows unchanged. SSH deploy key at `/data/secure/ssh/id_ed25519`, used through `core.sshCommand`.
- Next exact action: P11 (`docs/prompts/P11-rows-never-vanish.md`), then P12 → P13 → P19 → Preview #2. Check P10 CI during work; distinguish hosted-runner acquisition failures from code failures. Workflows/Plan/prompts unchanged. P8 not authorized.
- Last pushed checkpoint: P10 (this checkpoint); P9 `13bbf05`; PLAN `d642607`; part 1 `ce3cd25`; main `2f6284f`.
- Last updated: 2026-10-05

- CI checked during P10: P9 checkpoint validation green (37363825709); P9 preview (37363825730) and emulator (37363825748) never acquired hosted runners. Record as infrastructure cancellation, not a code failure. No workflow edits; retry on the next push.
