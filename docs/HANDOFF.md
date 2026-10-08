# Handoff

## Current handoff (2026-10-08)

- **Phase:** 14 — Preview #5 field fixes (downloads and merges that keep going in the
  background with speed in the notification, a faster merge, TikTok on the For You feed, fresh
  links instead of HTTP 410). Plan, file ownership, decisions, root causes and tasks:
  [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); prompts: [`prompts/`](prompts/README.md).
- **Owner's test of Preview #5** (2026-10-08, Preview APK run
  https://github.com/Alalkipgen/YFT/actions/runs/37575586233, `436aa90`; `main` = `5a5bddb`):
  on some pages of a site without an adapter the sheet shows "The site no longer has this video
  (HTTP 410)" (manifest and MP4 links of the site's CDN) until he reloads the page by hand, and
  Try again repeats it; a 1-hour YouTube live recording still merges for about 2 minutes, and the
  merge stops while he uses another app until he opens YFT again; TikTok (with a VPN) says "No
  video on screen to download" on the For You feed; he wants downloads to keep going in the
  background with % and speed (KB/s below 1,024 KB/s, MB/s above) in the notification. Root
  causes R16–R24 (FIX_ADD_PLAN §4): browser Try again reuses the stale page; page-script links
  outrank the player's own requests; TikTok's feed has no video links and its phone page keeps
  its data under another key, with media cookies dropped; no wake lock, no media-processing
  service type and no freeze handling (the phone's battery manager most likely freezes a
  CPU-only merge); the notification has no numbers; the merge works sample by sample.
- **Plan (Plan Mode, 2026-10-08):** three agents at once — A: P34 (background downloads and
  merges, notification with speed, battery and notification cards); B: P36 (TikTok) → P37
  (fresh links, Try again, "Reload page and try again"); C: P35 (stream-copy merge). Then P38 (A
  merges A → B → C into `work/phase-14-integration`, full validation, **Preview #6**) and P8
  (signed `1.0.0-beta.4`) with the owner's OK. Docs-only pushes no longer start CI.
- **Branches:** `work/phase-14-integration` (= `main` `5a5bddb` + the plan); agents branch from
  it: `work/phase-14-background` (A), `work/phase-14-sites` (B), `work/phase-14-fast-merge` (C).
  Status per agent: `SESSION_STATE.md`.
- **P38 (2026-10-08, Agent A):** A (P34), B (P36, P37) and C (P35) merged into
  `work/phase-14-integration` without conflicts; full validation 1532 tests, 0 failures, 66
  skipped, lint 0 errors, release build OK. The owner asked to push `main` after the merge: it
  was fast-forwarded to the validated merge after its CI was green (no tag). Preview #6 =
  Preview APK run 37811403962 of `4da3e61`; CI links: `SESSION_STATE.md` › Overview.
- **Next action — owner:** uninstall the older YFT Preview, install **Preview #6** (Preview APK
  run › Artifacts › `yft-preview-apk`) and test FIX_ADD_PLAN §6 "Preview #6"; then P8 (signed
  `1.0.0-beta.4`, `prompts/P8-signed-beta4.md`) only with his OK.
- **Phase 13** (P27–P33, 2026-10-07): merge progress and a direct merge into Download/YFT, the
  page's video instead of the pre-roll ad, the next video when one fails, Google search, browser
  history, pop-up and ad-redirect blocking; merged into `main` (`436aa90`, docs `5a5bddb`, no
  tag). Record: FIX_ADD_PLAN §8 and `git show 5a5bddb:docs/HANDOFF.md`.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03;
  `1.0.0-beta.3` (versionCode 3, tag `v1.0.0-beta.3` on `2f6284f`) signed draft 2026-10-04:
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md)). Phases 11–13 are merged into
  `main` (no tag); `1.0.0-beta.4` waits for Preview #6 (P8).
- **Phases 11 and 12:** records in `git show 4db6c2b:docs/HANDOFF.md` and
  `git show bc806f9:docs/HANDOFF.md`.

## Known limitations (Preview #5, `main` `5a5bddb`)

- Other sites without an adapter: on some page loads every link the page's player script names
  answers HTTP 410 to YFT; Try again in the browser repeats the same links; a manual reload helps
  (R16–R18, P37).
- A long merged YouTube download merges for minutes (about 2 minutes for a 1-hour 720p
  recording), and on the owner's Xiaomi phone the merge stops while YFT is in the background
  (R23, R24, P34, P35). The notification shows no %, speed or time left (R22, P34).
- TikTok in the browser: the For You feed gives "No video on screen to download", and the phone
  page's data are not read (R19, R20, P36). YouTube lookups from data-centre networks can stay
  bot-checked, so the phone is the real test.
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
`git show bc806f9:docs/HANDOFF.md`; Phase 13: `git show 5a5bddb:docs/HANDOFF.md`.
