# Handoff

## Current handoff (2026-10-09)

- **Phase:** 15 — Preview #6 field fixes (TikTok from TikTok's own page, YouTube progress from
  the first seconds, Delete file, the page's video never the ad). Plan, file ownership,
  decisions, root causes and tasks: [`FIX_ADD_PLAN.md`](FIX_ADD_PLAN.md); prompts:
  [`prompts/`](prompts/README.md).
- **Owner's test of Preview #6** (2026-10-09, Preview APK run
  https://github.com/Alalkipgen/YFT/actions/runs/37811403962, `4da3e61`; `main` = `a9eea7b`):
  TikTok (with a VPN) says "TikTok changed its page format. Falling back to generic detection."
  on every video in YFT's browser (Try again fails, nothing downloads) and on many Home links
  (then "Quality unknown" rows; a "Download · 1.4 MB" row fails with "This quality is not
  available now"); he wants TikTok to work like YouTube, by whatever works; a YouTube live
  recording waits long at "Downloading" before the % moves; Downloads needs Delete for the file
  itself; on an adult site the sheet sometimes shows the 0:30 pre-roll ad after "The first link
  is gone — using a fresh link". Root causes R25–R34 (FIX_ADD_PLAN §4): the browser's adapter
  failure hides the page's files; "changed its page format" covers header errors, redirects,
  any exception and unknown shapes, with no Details; the desktop retry runs in one case only;
  TikTok's media files need the same answer's token and Referer; dead short links land on the
  home page; the resolver hides unexpected exceptions; YouTube's progress waits for whole
  10 MiB ranges in batches; Delete only removes the record; the fresh-link chain accepts a
  candidate of unknown length (the ad).
- **Plan (Plan Mode, 2026-10-09):** three agents at once — A: P39 (TikTok adapter reads every
  answer, file checks, Details, no dead end) → P40 (TikTok's own page: tab data, API answers
  through a document-start script, a hidden TikTok page for Home); B: P41 (YouTube fast start)
  → P42 (Delete file); C: P43 (the page's video, never the ad). Then P44 (B merges B → C → A
  into `work/phase-15-integration`, full validation, **Preview #7**) and P8 (signed
  `1.0.0-beta.4`) with the owner's OK.
- **Branches:** `work/phase-15-integration` (= `main` `a9eea7b` + the plan); agents branch from
  it: `work/phase-15-tiktok` (A), `work/phase-15-downloads` (B), `work/phase-15-ads` (C).
  Status per agent: `SESSION_STATE.md`.
- **Next action — owner:** paste `prompts/A-tiktok.md`, `prompts/B-downloads.md` and
  `prompts/C-ads.md` into three agent chats; try Agent A's TikTok preview early with the VPN and
  send a Details screenshot of anything that fails.
- **Phase 14** (P34–P38, 2026-10-08): background downloads and merges with speed in the
  notification, a stream-copy merge, TikTok's For You feed, fresh links instead of HTTP 410;
  merged into `main` (`4da3e61`, docs `a9eea7b`, no tag; 1532 tests, 0 failures). Record:
  FIX_ADD_PLAN §8 and `git show a9eea7b:docs/HANDOFF.md`.
- **Releases:** `1.0.0-beta.1` published 2026-10-02; `1.0.0-beta.2` signed draft 2026-10-03;
  `1.0.0-beta.3` (versionCode 3, tag `v1.0.0-beta.3` on `2f6284f`) signed draft 2026-10-04:
  `video-downloader-1.0.0-beta.3.apk` 6,334,176 bytes, SHA-256
  `8fe466f17b62e88cdd5deb08482f7dc4c70a1a3dba7182bf94b93c6d1604e955`, certificate SHA-256
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`
  (notes: [`release/1.0.0-beta.3.md`](release/1.0.0-beta.3.md)). Phases 11–14 are merged into
  `main` (no tag); `1.0.0-beta.4` waits for Preview #7 (P8).

## Known limitations (Preview #6, `main` `a9eea7b`)

- TikTok: in the owner's VPN country the adapter's page reads fail ("changed its page format")
  and the browser then offers nothing; Home's fallback rows have no working size; the
  watermarked "Download" file can fail without a step (R25–R31, P39, P40). The sandbox (US)
  sees TikTok's normal answers, so the owner's phone is the real test.
- YouTube: % and speed stay at 0 until the first 10 MiB range ends (R32, P41); lookups from
  data-centre networks can stay bot-checked.
- Downloads can only remove a record; the file stays (R33, P42).
- Other sites: after a gone first link the sheet can offer the pre-roll ad (R34, P43).
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
