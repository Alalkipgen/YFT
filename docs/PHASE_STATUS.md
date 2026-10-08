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
| 8 — Field fixes | COMPLETE | Fixes from the owner's beta.2 phone test: browser crash, start page, crash report, Home lookup identity, Facebook, TikTok, YouTube messages, Your sites; CI emulator smoke test. Tasks T01–T09 in the former `docs/FIX_PLAN.md` (Git history) (phone checks in §8); T10 SKIPPED by the owner — released with Phases 9–10 as `1.0.0-beta.3` (T19) |
| 9 — Copied-link flow | COMPLETE | Copied-link check, "Video you copied" quick sheet, Search to download, floating Download button. Tasks T11–T14; T15 SKIPPED by the owner — released in `1.0.0-beta.3` (T19) |
| 10 — Formats and YouTube | COMPLETE | YouTube client strategy (D2 = A + B + C, ADR-006), merged 480p/720p/1080p video and audio, MP3. Tasks T16–T18; T19 merged the branch into `main` (`2f6284f`), tagged `v1.0.0-beta.3` and created the signed draft pre-release `1.0.0-beta.3` (versionCode 3, 2026-10-04; APK SHA-256 `8fe466f1…988f` in HANDOFF) |
| 11 — Download flow (Snaptube style) | COMPLETE on `main` (`4db6c2b`, 2026-10-06; owner-tested with Preview #3, no tag) | Part 1 (P0–P7): browser follows in-page navigation, Facebook pages, one download sheet, all Facebook qualities, the focused feed video, 2K/4K as WebM, test-key preview APK. Part 2 (P9–P19): short sheet, slow networks, rows that never vanish, one lookup per page, wide Download button, real thumbnails, YouTube visionOS first, Facebook public page first, instant sheet, lookup reuse, early Download. The owner's Preview #3 test found the issues Phase 12 fixes; the signed `1.0.0-beta.4` (P8) moved behind Phase 12 |
| 12 — Preview #3 field fixes | COMPLETE on `main` (`bc806f9`, 2026-10-06; owner-tested with Preview #4 on 2026-10-07: about 90% fine, no tag) | Saving video files (P20), Retry and failure details (P21), YouTube every quality (P22), Facebook every quality (P23), other sites' main video (P24), one sheet everywhere (P25), merge and Preview #4 (P26). Plan and prompts: `git show bc806f9:docs/FIX_ADD_PLAN.md`, `git show bc806f9:docs/prompts/`; the signed `1.0.0-beta.4` (P8) moved behind Phase 13 |
| 13 — Preview #4 polish | COMPLETE on `main` (`436aa90`, docs `5a5bddb`, 2026-10-07; owner-tested with Preview #5 on 2026-10-08, no tag) | YouTube merge progress and a direct merge into Download/YFT (P27); other sites: the page's video instead of the pre-roll ad, the next video when one fails (P28, P29); browser: Google search, history, pop-up and ad-redirect blocking (P30–P32); merge and Preview #5 (P33). Plan and prompts: `git show 5a5bddb:docs/FIX_ADD_PLAN.md`, `git show 5a5bddb:docs/prompts/`; the signed `1.0.0-beta.4` (P8) moved behind Phase 14 |
| 14 — Preview #5 field fixes | MERGED (P38, 2026-10-08) on `work/phase-14-integration`, `main` fast-forwarded at the owner's request; Preview #6 sent to the owner | Downloads and merges keep going in the background, notification with %, speed and time left (P34, Agent A); faster merge by stream copy (P35, Agent C); TikTok Download on the For You feed and the phone page's data (P36, Agent B); fresh links instead of HTTP 410 on other sites (P37, Agent B); merge and Preview #6 (P38); then the signed `1.0.0-beta.4` (P8). Plan: [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md) |

## Current phase state

Phases 0–7, 5E (YouTube, owner override) and 6R (UI redesign) are complete and merged into
`main`. `1.0.0-beta.1` was published on 2026-10-02; `1.0.0-beta.2` (the redesign) is a signed draft
pre-release from 2026-10-03 (tag `v1.0.0-beta.2` on `39ea049`).

Phases 8–10 are complete and merged into `main` (`2f6284f`); `1.0.0-beta.3` is a signed draft
pre-release from 2026-10-04 (tag `v1.0.0-beta.3`).

Phase 11 is complete and merged into `main` (`4db6c2b`, 2026-10-06, no tag). Phase 12 (P20–P26)
is complete and merged into `main` (`bc806f9`, 2026-10-06, no tag); the owner tested it as
Preview #4 on 2026-10-07 ("about 90% fine").

Phase 13 (P27–P33, 2026-10-07) is complete and merged into `main` (`436aa90`, docs
`5a5bddb`, no tag); the owner tested it as Preview #5 on 2026-10-08.

Phase 14 (2026-10-08): three agents worked at once on their own branches from
`work/phase-14-integration` (A: downloads and merges in the background with speed in the
notification, B: TikTok's For You feed and fresh links instead of HTTP 410, C: a stream-copy
merge); P38 merged A → B → C (1532 tests, 0 failures), fast-forwarded `main` at the owner's
request (no tag) and built Preview #6; `1.0.0-beta.4` (P8) waits for his phone test of
Preview #6. Tasks, owner decisions, findings, file ownership and the phone checklist live in
[`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); prompts are in [`prompts/`](prompts/README.md).

## History

The detailed record of Phases 0–7, 5E and 6R (scope, deliverables, validation runs and
checkpoint commits) was condensed on 2026-10-03. Read it with
`git show 28930cf:docs/PHASE_STATUS.md`. Release records: [`release/`](release/) and
[`../CHANGELOG.md`](../CHANGELOG.md).
