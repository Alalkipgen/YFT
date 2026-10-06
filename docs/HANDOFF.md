# Handoff

## Current handoff (2026-10-06)

- **Phase:** 12 — Preview #3 field fixes (saving, every quality, one sheet). Plan, file ownership,
  decisions, root causes and tasks: [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); prompts:
  [`prompts/`](prompts/README.md).
- **Owner's test of Preview #3** (2026-10-06, Preview APK run
  https://github.com/Alalkipgen/YFT/actions/runs/37455870506, `4db6c2b` = `main`): every video
  download fails at once with "Storage unavailable" (Retry too; audio works); YouTube offers only
  360p; Facebook gives HD/SD without Audio from Home and only 360p in the browser; another site
  opens a 29 s preview ("50 media found"); the sheet differs per site. Root causes R1–R6
  (FIX_ADD_PLAN §4): the direct engine reads a fresh MediaStore row before creating its file;
  Retry repeats the same steps; YouTube's visionOS is asked without visitor data and the chain
  stops at ANDROID's 360p; Facebook's lookup ends with HD/SD or skips the AVC ladder; other sites
  never match an MSE player and rank previews first; the sheet shows different data per site.
- **Plan (Plan Mode, 2026-10-06):** three agents at once — A: P20 (saving) → P21 (Retry and
  failure details); B: P22 (YouTube every quality) → P23 (Facebook every quality); C: P24 (other
  sites' main video) → P25 (one sheet everywhere). Then P26 (A merges A → B → C into
  `work/phase-12-integration`, full validation, **Preview #4**) and P8 (signed `1.0.0-beta.4`)
  with the owner's OK.
- **Branches:** `work/phase-12-integration` (= `main` `4db6c2b` + the plan); agents branch from
  it: `work/phase-12-download-fix` (A), `work/phase-12-site-qualities` (B),
  `work/phase-12-generic-sheet` (C). Status per agent: `SESSION_STATE.md`.
- **Next action — owner:** paste [`prompts/A-download-fix.md`](prompts/A-download-fix.md),
  [`prompts/B-site-qualities.md`](prompts/B-site-qualities.md) and
  [`prompts/C-generic-and-sheet.md`](prompts/C-generic-and-sheet.md) into three agent chats;
  optionally send the YouTube and other-site sheet **Details** screenshots from Preview #3 to
  Agents B and C. When all three say `READY FOR MERGE`, paste
  [`prompts/M-merge-preview4.md`](prompts/M-merge-preview4.md) into Agent A's chat.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03;
  `1.0.0-beta.3` (versionCode 3, tag `v1.0.0-beta.3` on `2f6284f`) signed draft 2026-10-04:
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md)). Phase 11 is merged into `main`
  (`4db6c2b`, no tag); `1.0.0-beta.4` waits for Preview #4 (P8).
- **Phase 11** (P0–P19, 2026-10-04 to 2026-10-06): browser navigation, Facebook pages, one sheet,
  Facebook qualities, feeds, 2K/4K, preview APK (part 1); short sheet, slow networks, stable
  rows, one lookup per page, wide Download button, thumbnails, visionOS first, Facebook public
  page first, instant sheet, lookup reuse, early Download (part 2). Record: FIX_ADD_PLAN §8 and
  `git show 4db6c2b:docs/HANDOFF.md`.

## Known limitations (Preview #3, `main` `4db6c2b`)

- Direct video downloads into `Download/YFT` fail with "Storage unavailable" (R1, P20); Retry
  does not help (R2, P21).
- YouTube can offer only 360p on networks where visionOS is bot-checked (R3, P22); YouTube
  lookups from data-centre networks can stay bot-checked, so the phone is the real test.
- Facebook can miss 720p and Audio (R4, P23); AV1-only sizes stay hidden while AV1 merges are off.
- Other sites can open a preview clip instead of the main video (R5, P24).
- No playlists or batch downloads, background playback or folder export; an expired link cannot
  be refreshed in place after the process was killed.

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
