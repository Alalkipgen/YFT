# Phase Status

| Phase | Status | Summary |
| --- | --- | --- |
| 0 — Discovery and feasibility | COMPLETE | Architecture, support matrix, ADRs and compileable Media3/WebView spike validated locally and in CI |
| 1 — Foundation | COMPLETE | Production modules, Compose navigation shell, DI/data/media foundations, tests and CI validated on the work branch |
| 2 — Browser/detection | COMPLETE | Secure browser, layered generic detection, bounded probes, page-scoped normalization, fixtures and candidate UI validated |
| 3 — Preview/variants | COMPLETE | Bounded direct/HLS/DASH resolution, honest variants and secure Media3 preview validated |
| 4 — Download engines | COMPLETE | Typed download plans, direct/HLS/DASH transfer, mux compatibility, foreground execution, Room recovery and safe export validated |
| 5 — Site adapters | COMPLETE | TikTok, Facebook and Vimeo adapters behind the extractor API with offline fixtures, registry fallback to generic detection, and the YouTube blocker reported |
| 5E — YouTube (owner override) | COMPLETE | YouTube adapter by owner decision (ADR-005): embedded-first lookup, progressive MP4 plus M4A audio, bundled ejs solver in a sandboxed WebView, offline fixtures and tests |
| 6 — Hardening/UI | COMPLETE | Privacy and backup hardening, download preferences and Wi-Fi-only policy, Library, Detected Media, Home link entry, About notices, original palette, free-space check and storage janitor; every audit finding closed or device-only |
| 7 — Signed beta/release | COMPLETE | Version `1.0.0-beta.1`, original launcher icon and launch screen, release signing from env/untracked properties, verification/checksum/device scripts, draft-only release workflow, changelog, release notes and release process; signed by the release workflow with the owner's key and published as a pre-release on 2026-10-02. Device checks still wait for a device |
| 6R — UI redesign | COMPLETE | Every screen rebuilt to the owner's design images (`docs/design/DESIGN-NOTES.md`) on `work/phase-6-ui-redesign`, Tasks 0–9, with every feature kept; merged with Phase 7 into `main` and released as `1.0.0-beta.2` |
| 8 — Field fixes | IN PROGRESS | Fixes from the owner's beta.2 phone test: browser crash, start page, crash report, Home lookup identity, Facebook, TikTok, YouTube messages, Your sites; CI emulator smoke test. Tasks T01–T09 in `docs/FIX_PLAN.md`; T10 (beta.3 release) SKIPPED by the owner on 2026-10-03 — one release after T19 |
| 9 — Copied-link flow | PLANNED | Copied-link check, "Video you copied" quick sheet, Search to download, floating Download button. Tasks T11–T14; T15 (release) SKIPPED by the owner |
| 10 — Formats and YouTube | IN PROGRESS (T16, T17 OWNER CHECK; T09 next) | YouTube client strategy (D2 = A + B + C, ADR-006), merged 720p/1080p video and audio, MP3. Tasks T16–T18; T19 merges, tags and signs the one release, `1.0.0-beta.3` |

## Current phase state

Phases 0–7, 5E (YouTube, owner override) and 6R (UI redesign) are complete and merged into
`main`. `1.0.0-beta.1` was published on 2026-10-02; `1.0.0-beta.2` (the redesign) is a signed draft
pre-release from 2026-10-03 (tag `v1.0.0-beta.2` on `39ea049`).

Phase 8 is planned on `work/phase-8-field-fixes`. Tasks, status board, owner decisions and
findings for Phases 8–10 live in [`FIX_PLAN.md`](FIX_PLAN.md); ready-to-paste prompts are in
[`prompts/`](prompts/README.md). Agents update the status board there, not this file, until a
phase is complete.

## History

The detailed record of Phases 0–7, 5E and 6R (scope, deliverables, validation runs and
checkpoint commits) was condensed on 2026-10-03. Read it with
`git show 28930cf:docs/PHASE_STATUS.md`. Release records: [`release/`](release/) and
[`../CHANGELOG.md`](../CHANGELOG.md).
