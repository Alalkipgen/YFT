# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: 11 — download flow like Snaptube (`docs/FIX_ADD_PLAN.md`). Order P0 → P1 → … → P6 → P7 (test-key preview APK) → owner phone test → P8 (signed `1.0.0-beta.4`). Owner instruction 2026-10-04: continue P1 → P7 without asking; short Burmese report after each task. Nothing is merged into `main` before P8.
- Rules: [ADR-006](decisions/ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03) — any working technique for public videos; still no DRM, paid, private-content or age-gate bypass; adapters never sign in; secrets never logged or committed. D2 = A + B + C. D1 = YES and D3 = YES (owner delegated the choice, 2026-10-03: "do as you see fit; do not stop; commit and push after every task").
- Current branch: `work/phase-11-download-flow` (from `main` `2f6284f`, tracking origin).
- Last completed task: P0 DONE (2026-10-04) — `docs/FIX_ADD_PLAN.md`, prompts P1–P8, generic master prompt, docs links. T19 DONE (2026-10-04): `main` = `2f6284f`, tag `v1.0.0-beta.3`, signed draft pre-release (APK 6,334,176 bytes, SHA-256 `8fe466f1…988f`, release run https://github.com/Alalkipgen/YFT/actions/runs/37204457527). Phases 8–10 task log: `git show 2f6284f:docs/SESSION_STATE.md`.
- Work in progress: none (P1 next).
- Build status: GREEN on `2f6284f` (CI checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37203674358, emulator https://github.com/Alalkipgen/YFT/actions/runs/37203631028). P0 changes docs only (link check).
- Known limitations: no local KVM; native PNG download requires authentication (HTTP 401), so no native pixel-review claim. Sandbox live checks use a datacenter IP: one YouTube test video stays bot-checked for every client even with a minted token, so YouTube needs the owner's phone check. The page player's own PO token is not captured, and in-page YouTube navigation without a page load does not rerun the lookup (FIX_PLAN §9).
- Environment: `source /data/yft-env.sh` sets JDK 17, SDK 35, Gradle cache and verified SSH access. Temporary data outside repo; one memory-safe Gradle at a time.
- Next exact action: P1 — browser follows in-page navigation (`docs/prompts/P1-browser-spa-navigation.md`): `onUrlChanged` from `doUpdateVisitedHistory`, new candidate scope in `BrowserViewModel`, debounced site adapters, Download button for `/watch`, `/shorts/` and Facebook/TikTok video URLs.
- Last pushed checkpoint: P0 (this checkpoint); P0 WIP `333f827`; T19 `2f6284f`, `b0abe05`; T18 `0ec1e46`; earlier in `git show 2f6284f:docs/SESSION_STATE.md`.
- Last updated: 2026-10-04
