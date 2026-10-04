# Handoff

## Current handoff (2026-10-05)

- **Phase:** 11 — download flow like Snaptube. Plan, status board, decisions and findings:
  [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); one prompt per task in [`prompts/`](prompts/README.md).
  Order P0 → P1 → … → P6 → P7 (test-key preview APK) → owner phone test → P8 (signed
  `1.0.0-beta.4`). P0 (plan and docs) is DONE.
- **Branch:** `work/phase-11-download-flow`, created from `main` at `2f6284f`.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03 (tag
  `v1.0.0-beta.2` on `39ea049`). `1.0.0-beta.3` (versionCode 3) completes Phases 8–10: `main`
  fast-forwarded to `2f6284f`, tag `v1.0.0-beta.3`, Release draft run
  https://github.com/Alalkipgen/YFT/actions/runs/37204457527 → draft pre-release with
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (same key as beta.1 and beta.2). Notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md).
- **Owner's phone test of beta.3 (2026-10-04):** FIX_ADD_PLAN §2 — the browser's Download button
  does not follow a YouTube video opened inside the page, a Facebook page shows black, the
  download choices are split, Facebook shows only some qualities, and 2K/4K are missing.
- **Owner instruction (2026-10-04):** "Do P0 first, then P1; don't stop, don't ask." Continue P1 →
  P7 task after task with a checkpoint push and a short Burmese report after each.
- **Done in Phase 11:** P0 plan and docs (`99efca6`); P1 OWNER CHECK (2026-10-04) — the browser
  follows in-page navigation, so the Download button follows a video opened inside the page;
  P2 OWNER CHECK (2026-10-04) — Facebook and TikTok pages render and play in the browser, and the
  Facebook lookup asks for the desktop page (owner's phone: reel plays, Download button shown);
  P3 OWNER CHECK (2026-10-04) — one "Download" sheet for every path (Home View, Found list,
  browser button) with real resolutions and sizes, audio from MP4 (M4A/MP3) and one row per
  video; P3-FIX OWNER CHECK (2026-10-05) — after the owner's phone check the sheet has exactly
  two sections, Audio (M4A, MP3 320/192/128) and Video (one row per standard resolution,
  "480p" for 848 × 478), Facebook `story.php`/`permalink.php`/posts pages are identified, and a
  player's byte-range pieces count as one file, so the browser's Download button opens the
  sheet instead of the Found list; P4 OWNER CHECK (2026-10-05) — Facebook's DASH picture sizes
  are merged Video rows with their AAC sound, the AAC track is Audio, the AVC ladder comes from
  Safari's page without the session; AV1 merges are off (the API 34 emulator's muxer failed).
- **Owner instruction (2026-10-05):** P3-FIX, then P4, P5, P6 without stopping or asking
  ([`prompts/CONTINUE-P4-TO-P6.md`](prompts/CONTINUE-P4-TO-P6.md)); not P7/P8.
- **Next action — agent:** P5 — Download button on feeds (focused video)
  ([`prompts/P5-feed-focused-video.md`](prompts/P5-feed-focused-video.md)), then P6.
- **Next action — owner:** publish the beta.3 draft when ready; phone checks per task from the
  `yft-debug-apk` builds (FIX_ADD_PLAN §6); the P7 preview APK installs next to the release app.
- **Earlier handoffs:** the Phases 8–10 task log (T01–T19 validation, CI runs, decisions) is in Git
  history: `git show 2f6284f:docs/HANDOFF.md` and `git show 2f6284f:docs/FIX_PLAN.md`.

## Known limitations (beta.3)

- The browser's Download button follows only full page loads, not videos opened inside a page
  (P1, fixed on the branch); some Facebook pages show black in the browser (P2, fixed on the
  branch); the download choices are split over several screens (P3, fixed on the branch).
- Facebook's AV1-only sizes (often 1080p) are not offered while AV1 merges are off (P4);
  YouTube stops at 1080p AVC (2K/4K WebM is P6).
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
