# Continue Phase 11: P3 CI → P4 → P5 → P6 (handoff, 2026-10-04)

**ရည်ရွယ်ချက်:** P3 ပြီးပြီ (`56f0c79`)။ agent အသစ်က P3 CI ကိုစစ်ပြီး P4 → P5 → P6 ကို မရပ်မမေးဘဲ
ဆက်လုပ်မယ်။ P7/P8 မလုပ်ရ (owner စောင့်)။ အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Repository: https://github.com/Alalkipgen/YFT
Branch: work/phase-11-download-flow   ALLOW_PUSH: true (this branch only)
ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
Owner instruction: do P4, P5, P6 in order without stopping or asking; short Burmese report after
each task (FIX_ADD_PLAN 0.5) and all of them in the final message. Do NOT start P7 or P8.
The repository is the source of truth; if plan and verified code disagree, the code wins.

START
1. Follow AGENTS.md: git fetch --all --prune; git checkout work/phase-11-download-flow; git pull;
   git status; git log -5 --oneline (expect 56f0c79 "P3: one download sheet …" or later).
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and tasks P4–P6, docs/SESSION_STATE.md,
   docs/HANDOFF.md, and docs/prompts/P4-…, P5-…, P6-*.md (each task's own prompt).
3. Notion sandbox: `source /data/yft-env.sh` first. Run Gradle through
   `bash /data/tools/gw.sh <tasks>` (memory-safe), one Gradle call at a time, long runs in the
   background: ( bash /data/tools/gw.sh -q <tasks> > /data/tools/logs/x.log 2>&1; echo EXIT=$? >>
   /data/tools/logs/x.log ) & — then poll. Test totals: bash /data/tools/testsum.sh.
   CI: python3 /data/tools/ci-html.py runs | jobs RUN | ann RUN (GitHub API is rate limited).
   Before a "fails on the old code" mutation check, copy uncommitted files to /data/tools/bak and
   restore from there (never `git checkout` uncommitted work).

FIRST: P3 CI
- P3 checkpoint 56f0c79: checkpoint run #146 https://github.com/Alalkipgen/YFT/actions/runs/37224812846
  and emulator smoke #25 https://github.com/Alalkipgen/YFT/actions/runs/37224812796 (new
  AudioExtractorInstrumentedTest). If red, fix first. Then put both links in TEST_MATRIX "P3" CI
  row and SESSION_STATE (commit with the P4 work).

P4 — Facebook: one video, every quality (docs/prompts/P4-facebook-all-qualities.md)
Live findings 2026-10-04 (sandbox, public reel 1603698891196107, desktop page, HTTP 200):
- Page JSON has `videoDeliveryLegacyFields.dash_manifest_xml_string`: an inline MPD
  (profile isoff-on-demand; every Representation is a whole MP4 at its BaseURL with a
  SegmentBase indexRange/Initialization — no SegmentTemplate). `dash_manifest_url` points to
  www.facebook.com/dash_mpd_debug.mpd?v=…&dummy (debug URL: do not rely on it).
  `browser_native_hd_url` / `browser_native_sd_url` are the progressive AVC MP4s. No
  `progressive_urls` / `dash_manifests` on this page. Today the parser reads the inline MPD only
  for heights (FacebookQualityMetadata) and emits the dash_manifest_url as a DASH candidate,
  which lists in More formats but cannot download (DashDownloadManifestParser: SegmentBase →
  Unsupported).
- The codec ladder depends on the page request's identity:
  Chrome/Firefox desktop → FBTagsetUsed r2av1-r1gen2vp9: AV1 av01.0.05M.08 1108x720 with
  FBQualityLabel 240p…720p (a bitrate ladder at the same pixels) and av01.0.08M.08 1660x1078
  "1080p". Safari macOS 17.5 → basic_gen2: AVC avc1.4d001e 552x358 "360p" and avc1.64001f
  1108x720 "720p". Audio always mp4a.40.5 (HE-AAC) 44.1 kHz ~57 kbps.
- Consequences: AVC + AAC merges with the existing T17 path (MediaExtractor + MediaMuxer MPEG-4)
  on every phone. AV1 in MP4 needs MediaMuxer AV1 support (verify: Android 14 / API 34+) and an
  AV1 decoder for playback (check MediaCodecList); offer AV1 rows only when both hold, else keep
  them out or mark them. Check that HE-AAC (mp4a.40.5) passes Mp3Variants.isAacSource /
  AudioFromVideo and decodes for MP3.
Suggested design (verify against the code):
- Parse the inline MPD in extractor-sites (secure XML like core-media DashManifestParser) into
  whole-file representations: height, width, frame rate, codecs, bandwidth, BaseURL,
  FBQualityLabel. Emit each video representation as a DIRECT `video/mp4` candidate (videoId,
  width/height/fps/bitrate/codecs, title "<title> — 1080p") with `audioCompanion` = the AAC audio
  representation (exactly like YouTube's merged rows, T17), and the audio representation as a
  DIRECT `audio/mp4` candidate (Music M4A + MP3). Keep HD/SD progressive. Drop the
  dash_manifest_url candidate when inline representations were read. Collapse an AV1 ladder at
  one pixel height to its best bitrate (or show FB's label only as detail) so the sheet does not
  list "720p" seven times; real heights stay the label (P3 rule: never invent a height).
- Optional: a second page request with a Safari identity for the AVC ladder; the mobile page's
  inline MPD in data-extra attributes (G7).
- Sizes: Content-Length from a ranged request, else bandwidth × duration (estimated).
Tests: sanitized Facebook MPD fixture (fake hosts, no tokens/signatures), parser tests (heights,
audio, whole-file URLs), plan/merge tests, a regression that fails on the old code, live check
(status, heights, markers only — never commit or log URLs, cookies, tokens).
Owner check: Facebook link → sheet shows 360p/720p/1080p (when present) and Music → each
downloads and plays with sound.

P5 — Download button on feeds / focused video (docs/prompts/P5-feed-focused-video.md).
P6 — 2K/4K (docs/prompts/P6-*.md): YouTubeExtractor MERGED_QUALITIES {480,720,1080} AVC only;
AudioVideoMuxEngine writes MPEG-4 only → add the WebM path (VP9 + Opus, MediaMuxer
MUXER_OUTPUT_WEBM, `.webm`); AV1 only on Android 14+.

AFTER EACH TASK
- Validation (report only what ran): bash /data/tools/gw.sh -q :core-model:test
  :core-media:testDebugUnitTest :core-download:testDebugUnitTest :extractor-sites:test
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
- Kotlin lines ≤ 100: git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' |
  awk 'length > 101' (prints nothing); for untracked new files use LC_ALL=C.UTF-8 awk 'length>100'.
- Docs: SUPPORT_MATRIX, TEST_MATRIX (new section per task with CI links), CHANGELOG
  [Unreleased], FIX_ADD_PLAN status board (→ OWNER CHECK when only the phone check is left, or
  DONE) + Result note, SESSION_STATE, HANDOFF, PHASE_STATUS.
- Checkpoint: source /data/yft-env.sh && CHECKPOINT_TEST_COMMAND="<validation>" bash
  scripts/checkpoint.sh "Pn: <summary>" (needs a SESSION_STATE change; commits and pushes).
  Push WIP often. Never commit secrets, keystores, local.properties, .env, cookies, tokens,
  signed URLs.
- CI green for the commit (checkpoint + emulator smoke); fix red runs before reporting.
- Blocked item: at most 2 h, record evidence, mark OWNER CHECK, move on.
Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); keep every testTag; WebView calls on the main thread; regression tests must fail on
the old code; site tasks need a live check; TikTok cannot be tested on the owner's phone.

FINAL REPORT (Burmese, short): per task what changed, commands and results, commit SHAs, CI
links (checkpoint + emulator), the latest yft-debug-apk run link (Actions › run › Artifacts ›
yft-debug-apk), owner phone checks, open issues; P7/P8 wait for the owner.
```
