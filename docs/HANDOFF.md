# Handoff

## Current handoff (2026-10-07)

- **Phase:** 13 — Preview #4 polish (other sites' pre-roll ads, the YouTube merge at 99%, the
  browser's search, history and pop-ups). Plan, file ownership, decisions, root causes and
  tasks: [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); prompts: [`prompts/`](prompts/README.md).
- **Owner's test of Preview #4** (2026-10-07, Preview APK run
  https://github.com/Alalkipgen/YFT/actions/runs/37530061595, `bc806f9` = `main`): "about 90%
  fine". Open: on a free video site without an adapter, Download during the pre-roll opens the
  ad (0:30, 1080p MP4) instead of the page's video (16:24, 720p HLS, only under Other videos),
  and one page fails with HTTP 410 (Snaptube shows the page's title, picture, 480p and 720p);
  long YouTube live recordings wait a long time at 99%; the browser searches DuckDuckGo, has no
  history, and ads redirect the tab. Root causes R7–R15 (FIX_ADD_PLAN §4): the playing element
  outranks the page's own video; the page's stated length, title and picture are dropped; free
  video sites' ad networks are unknown; no second try after a dead address; the merge writes
  the file twice without progress; DuckDuckGo is hard-coded; no history store; multiple windows
  off and every top-level navigation allowed.
- **Plan (Plan Mode, 2026-10-07):** three agents at once — A: P27 (merge progress and a direct
  mux into Download/YFT); B: P28 (the page's video, not the ad) → P29 (the next video when one
  fails); C: P30 (Google search) → P31 (history) → P32 (pop-ups and ad redirects). Then P33 (A
  merges A → B → C into `work/phase-13-integration`, full validation, **Preview #5**) and P8
  (signed `1.0.0-beta.4`) with the owner's OK.
- **Branches:** `work/phase-13-integration` (= `main` `bc806f9` + the plan); agents branch from
  it: `work/phase-13-merge-speed` (A), `work/phase-13-generic-main` (B), `work/phase-13-browser`
  (C). Status per agent: `SESSION_STATE.md`.
- **P33 (2026-10-07, Agent A):** A (P27), B (P28, P29) and C (P30–P32) merged into
  `work/phase-13-integration` without conflicts; full validation 1435 tests, 0 failures, 66
  skipped, lint 0 errors, release build OK; CI green (emulator 29 tests, 0 failures).
  **Preview #5** = https://github.com/Alalkipgen/YFT/actions/runs/37575586233 (`yft-preview-apk`).
  At the owner's request `main` was fast-forwarded to the merge (`436aa90`, no tag).
- **Next action — owner:** uninstall the older YFT Preview, install **Preview #5** (Preview APK
  run › Artifacts › `yft-preview-apk`) and test FIX_ADD_PLAN §6 "Preview #5"; then P8 (signed
  `1.0.0-beta.4`, `prompts/P8-signed-beta4.md`) only with his OK.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03;
  `1.0.0-beta.3` (versionCode 3, tag `v1.0.0-beta.3` on `2f6284f`) signed draft 2026-10-04:
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md)). Phases 11 and 12 are merged
  into `main` (`bc806f9`, no tag); `1.0.0-beta.4` waits for Preview #5 (P8).
- **Phase 12** (P20–P26, 2026-10-06): video downloads save again, Retry and failure details,
  YouTube and Facebook every quality, other sites' main video, one sheet everywhere, merge and
  Preview #4. Record: FIX_ADD_PLAN §8 and `git show bc806f9:docs/HANDOFF.md`.
- **Phase 11** (P0–P19, 2026-10-04 to 2026-10-06): record in `git show 4db6c2b:docs/HANDOFF.md`.

## Known limitations (Preview #4, `main` `bc806f9`)

- Other sites without an adapter can open a pre-roll ad instead of the page's video, and a dead
  file address (HTTP 410) ends the sheet (R7–R11, P28, P29).
- Long merged YouTube downloads wait at 99% without progress while the file is merged and
  copied (R12, P27). YouTube lookups from data-centre networks can stay bot-checked, so the
  phone is the real test.
- The browser searches DuckDuckGo only, keeps no history, and page scripts or ads can send the
  tab to another site (R13–R15, P30–P32).
- AV1-only sizes stay hidden while AV1 merges are off. No playlists or batch downloads,
  background playback or folder export; an expired link cannot be refreshed in place after the
  process was killed.

## Device testing

The agent sandbox has no emulator (`/dev/kvm` is missing) and Robolectric does not run a real
WebView or MediaStore. Owner checks are listed per task in FIX_ADD_PLAN §5 and §6; device-only
checks are in [`TEST_MATRIX.md`](TEST_MATRIX.md). Every green checkpoint run uploads a debug APK
(FIX_ADD_PLAN §0.6), and the emulator smoke job (API 34) runs the instrumented tests on GitHub
Actions.

## History

Detailed handoffs for Phases 0–7, 5E and the UI redesign: `git show 28930cf:docs/HANDOFF.md`.
Phases 8–10: `git show 2f6284f:docs/HANDOFF.md`. Phase 11 part 1: `git show ce3cd25:docs/HANDOFF.md`;
Phase 11 part 2 and the Track A/B merge: `git show 4db6c2b:docs/HANDOFF.md`.
