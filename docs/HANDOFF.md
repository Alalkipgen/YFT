# Handoff

## Current handoff (2026-10-05)

**Latest owner instruction: finish P11 and STOP.** P9/P10 are pushed and P11's stable quality
rows are locally verified (1124 tests, 66 native-render skips; no failures/lint errors). P11
feature milestone `f82427b` passed preview and emulator CI; final validation CI retry follows
verified loopback-fixture hardening. Actual status/stop boundary is in `SESSION_STATE.md`.
Workflows, FIX_ADD_PLAN and prompts remain unchanged. P12/P13/P19/Preview #2 have not started;
no task may auto-continue until the owner explicitly resumes it.

- **Phase:** 11 — download flow like Snaptube. Plan, status board, decisions and findings:
  [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); one prompt per task in [`prompts/`](prompts/README.md).
- **Branch:** `work/phase-11-download-flow`, created from `main` at `2f6284f`; part 1 ends at
  `ce3cd25`.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03 (tag
  `v1.0.0-beta.2` on `39ea049`). `1.0.0-beta.3` (versionCode 3) completes Phases 8–10: `main`
  fast-forwarded to `2f6284f`, tag `v1.0.0-beta.3`, Release draft run
  https://github.com/Alalkipgen/YFT/actions/runs/37204457527 → draft pre-release with
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (same key as beta.1 and beta.2). Notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md).
- **Part 1 (P0–P7, done):** the browser follows in-page navigation; Facebook and TikTok pages
  render and play; one "Download" sheet with Audio and Video; every Facebook DASH quality merged
  with its sound; the Download button on feeds finds the video on screen; YouTube 2K/4K as
  VP9 + Opus `.webm`; `yft-preview-apk` signed with a CI test key. Commits and the short record:
  FIX_ADD_PLAN §8; full part 1 plan: `git show ce3cd25:docs/FIX_ADD_PLAN.md`.
- **Owner's phone test of Preview APK #1 (`1.0.0-beta.3-preview.1`, 2026-10-05):** FIX_ADD_PLAN
  §2 — lookups fail on a slow line, the sheet is long with 4K preselected and Download scrolls
  away, Facebook rows vanish, site pages can open "Found on this page 4", no wide Download
  button, the sheet waits for the lookup; the owner also asked for real thumbnails.
- **Part 2 plan (Plan Mode, 2026-10-05):** P9 → P10 → P11 → P12 → P13 → P19 → Preview #2 → P14 →
  P15 → P16 → P17 → P18 → Preview #3 → owner phone test → P8 (FIX_ADD_PLAN §1, §3 E6–E10, §5).
  Advice on the owner's four questions (YouTube in-page button, a YouTube page of YFT's own,
  Facebook formats from the browser page, TikTok testing) is FIX_ADD_PLAN §7 B1–B4, waiting for
  his decision.
- **Next action — agent:** when the owner asks for it, P9 ([`prompts/P9-short-sheet.md`](prompts/P9-short-sheet.md)
  or [`prompts/00_NEXT_TASK.md`](prompts/00_NEXT_TASK.md)), then task after task with a checkpoint
  push and a short Burmese report after each; Preview #2 link after P19, Preview #3 link after
  P18. P8 (signed beta.4) only with his OK.
- **Next action — owner:** say when part 2 should start; decide B1–B4 when convenient (B1 after
  Preview #2).
- **Earlier handoffs:** the Phases 8–10 task log (T01–T19 validation, CI runs, decisions) is in Git
  history: `git show 2f6284f:docs/HANDOFF.md` and `git show 2f6284f:docs/FIX_PLAN.md`; the part 1
  handoff: `git show ce3cd25:docs/HANDOFF.md`.

## Known limitations (beta.3)

- The browser's Download button follows only full page loads, not videos opened inside a page
  (P1, fixed on the branch); some Facebook pages show black in the browser (P2, fixed on the
  branch); the download choices are split over several screens (P3, fixed on the branch).
- Facebook's AV1-only sizes (often 1080p) are not offered while AV1 merges are off (P4);
  YouTube 2K/4K needs Android 10+ (VP9 + Opus WebM, P6) and AV1-only 2K/4K stays hidden.
- On the branch (Preview #1): lookups fail on slow lines (15 s/25 s limits), the sheet is long
  with 4K preselected, Facebook rows can vanish, and the sheet waits for the lookup — planned
  as part 2 (FIX_ADD_PLAN §2, P9–P19).
- YouTube lookups from datacenter networks can stay bot-checked; the phone on a home or mobile
  network is the real test.
- No playlists or batch downloads, background playback or folder export; an expired link cannot
  be refreshed in place after the process was killed.

## Device testing

The agent sandbox has no emulator (`/dev/kvm` is missing) and Robolectric does not run a real
WebView. Owner checks are listed per task in FIX_ADD_PLAN §6; device-only checks are in
[`TEST_MATRIX.md`](TEST_MATRIX.md). Every green checkpoint run uploads a debug APK
(FIX_ADD_PLAN §0.6), and an emulator smoke job runs on GitHub Actions.

## History

Detailed handoffs for Phases 0–7, 5E and the UI redesign (work done, validation, decisions and
checkpoint commits) were condensed on 2026-10-03. Read them with
`git show 28930cf:docs/HANDOFF.md`. The Phases 8–10 log was condensed on 2026-10-04:
`git show 2f6284f:docs/HANDOFF.md`.
