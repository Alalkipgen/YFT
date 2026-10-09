# Handoff

## Current handoff (2026-10-09)

- **Phase:** 15 — Preview #6 field fixes, merged (P44) into `work/phase-15-integration`: TikTok
  from TikTok's own page (tab data, TikTok's own API answers, a hidden TikTok page for Home) with
  every failure explained in Details (P39, P40); YouTube % and speed within seconds (P41);
  Delete file in Downloads and the Library (P42); the page's own video, never the pre-roll ad,
  on other sites (P43). Plan, Results and the phone list: [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md);
  prompts: [`prompts/`](prompts/README.md).
- **Validation (P44):** 1717 tests, 0 failures, 66 skipped (app 900, core-browser 149, core-data 33,
  core-download 184, core-media 33, core-model 122, extractor-api 36, extractor-generic 21,
  extractor-sites 239; 1532 at the start); lint 0 errors (98 warnings: A's 3 androidx.webkit
  notices); `:app:assembleRelease` OK (lint vital needed a 2.5 GiB Gradle heap on the 4 GiB sandbox)
- **Preview #7:** Preview APK (test key) run
  https://github.com/Alalkipgen/YFT/actions/runs/37893874478 of the P44 checkpoint `1ef8c86`
  (Artifacts › `yft-preview-apk`; uninstall the older YFT Preview first); checkpoint validation
  37893874474 and emulator smoke 37893874480 green. As the owner asked, `main` is
  fast-forwarded to the validated merge (no tag).
- **Next action — owner:** test Preview #7 with FIX_ADD_PLAN §6 (TikTok with the VPN in the
  browser and on Home, YouTube's start, Delete file, ads, Preview #6 items); send a Details
  screenshot of anything that fails. `1.0.0-beta.4` (P8) waits for his OK.
- **Open (C → A hand-offs, backlog):** (a) proven ads are still listed and counted as other
  videos in the browser (leaving them out breaks P24's owner-case test, so it needs Agent A and
  the owner's word); (b) the browser's own reads of a page's answers (e.g.
  `MediaMetadataProbe`'s XML answers) do not call `vastAds.onAnswer` yet.
- **Phase 14** (P34–P38, 2026-10-08): background downloads and merges, a stream-copy merge,
  TikTok's For You feed, fresh links instead of HTTP 410; merged into `main` (`4da3e61`, docs
  `a9eea7b`). Record: `git show a9eea7b:docs/HANDOFF.md`.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03;
  `1.0.0-beta.3` (versionCode 3, tag `v1.0.0-beta.3` on `2f6284f`) signed draft 2026-10-04:
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md)). Phases 11–15 are merged
  (no tag); `1.0.0-beta.4` waits for Preview #7 (P8).

## Known limitations (Preview #7)

- TikTok: the sandbox (US) sees TikTok's normal answers; the owner's phone with his VPN is the
  real test (FIX_ADD_PLAN §6 items 1–3). A check TikTok asks a person to pass is never answered
  by YFT ("TikTok wants a check …").
- YouTube: lookups from data-centre networks can stay bot-checked.
- Other sites: a VAST answer the browser reads itself does not start an ad break yet (C → A
  hand-off (b)); the ad rule is STRICT by default.
- AV1-only sizes stay hidden while AV1 merges are off. No playlists or batch downloads,
  background playback or folder export; an expired link cannot be refreshed in the middle of a
  download.

## Device testing

The agent sandbox has no emulator (`/dev/kvm` is missing) and Robolectric does not run a real
WebView or MediaStore. Owner checks are listed per task in FIX_ADD_PLAN §5 and §6; device-only
checks are in [`TEST_MATRIX.md`](TEST_MATRIX.md). Every green checkpoint run uploads a debug APK
(FIX_ADD_PLAN §0.6), and the emulator smoke job (API 34) runs the instrumented tests on GitHub
Actions.

## History

Detailed handoffs for Phases 0–7, 5E and the UI redesign: `git show 28930cf:docs/HANDOFF.md`.
Phases 8–10: `git show 2f6284f:docs/HANDOFF.md`. Phase 11 part 1: `git show ce3cd25:docs/HANDOFF.md`;
Phase 11 part 2 and the Track A/B merge: `git show 4db6c2b:docs/HANDOFF.md`. Phase 12:
`git show bc806f9:docs/HANDOFF.md`; Phase 13: `git show 5a5bddb:docs/HANDOFF.md`; Phase 14:
`git show a9eea7b:docs/HANDOFF.md`.
