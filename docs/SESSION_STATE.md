# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phases 8–10 on one branch (owner change 2026-10-03, `docs/FIX_PLAN.md` §3): T10 and T15 SKIPPED; nothing is merged or released before T19. Order: T16 → T17 → T09 → T12 → T11 → T13 → T14 → T18 → T19, task after task without waiting. After T19: merge into `main`, push, tag `v1.0.0-beta.3`, release APK signed with the release key (approved for T19 only).
- Rules: [ADR-006](decisions/ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03) — any working technique for public videos; still no DRM, paid, private-content or age-gate bypass; adapters never sign in; secrets never logged or committed. D2 = A + B + C. D1 = YES and D3 = YES (owner delegated the choice, 2026-10-03: "do as you see fit; do not stop; commit and push after every task").
- Current branch: `work/phase-8-field-fixes` (tracking origin).
- Last completed task: T12 OWNER CHECK (2026-10-04) — "Video you copied" quick sheet (`feature/quickdownload/`): Music, Fast ≤ 480p, High ≤ 720p, More formats, Download keeps `audioCompanion`. T09 OWNER CHECK (`b4ea4cc`); T17 OWNER CHECK (`210814b`, CI runs 37178339187/37178339186); T16 OWNER CHECK; T08 DONE; T07/T06/T01/T03/T04 OWNER CHECK; T05/T02 DONE.
- Work in progress: none. The owner's "pause after T17" note is withdrawn (owner, 2026-10-04): continue T09 → T12 → T11 → T13 → T14 → T18 → T19 without stopping.
- Build status: GREEN on the T12 tree — core-model 49, core-download 89, core-media 17, core-data 15, extractor-api 32, extractor-generic 8, extractor-sites 150, app 500 (58 render skips), 0 failures; lint 0 errors / 95 warnings; instrumentation APK compiled; Python 22/22; new Kotlin lines <=100.
- Known limitations: no local KVM; native PNG download requires authentication (HTTP 401), so no native pixel-review claim. Sandbox live checks use a datacenter IP: one YouTube test video stays bot-checked for every client even with a minted token, so YouTube needs the owner's phone check. The page player's own PO token is not captured, and in-page YouTube navigation without a page load does not rerun the lookup (FIX_PLAN §9).
- Environment: `source /data/yft-env.sh` sets JDK 17, SDK 35, Gradle cache and verified SSH access. Temporary data outside repo; one memory-safe Gradle at a time.
- Next exact action: T11 — check the copied link when YFT opens ([prompt](prompts/T11-copied-link-watcher.md)); D1 = YES (on by default). Then T13 → T14 → T18 → T19.
- Last pushed checkpoint: T12 (this checkpoint); T09 `b4ea4cc`; T17 `210814b`; T17 WIP `507f236`; T16 `30cefd8`; T16 docs `2591ff3`; T08 `aa915ff`; T07 `ba165c5`; T06 `af55f34`; T05 `cbc92e5`; T04 final `ce881f7`, implementation `3b9224c`. T03 completion `336851b`, implementation `9be6b43`; T02 completion `66a8823`, repair `e9e1a09`; T01 `7179637`.
- Last updated: 2026-10-04
