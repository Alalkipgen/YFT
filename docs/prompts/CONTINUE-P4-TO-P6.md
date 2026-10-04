# Continue Phase 11: P3 fix → P4 → P5 → P6 (handoff, updated 2026-10-05)

**ရည်ရွယ်ချက်:** P3 (`56f0c79`, CI green) ကို owner ဖုန်းနဲ့စစ်တော့ error တွေကျန်သေးတယ်။ agent အသစ်က
P3-FIX ကို root cause ရှာပြီးပြင်၊ ပြီးရင် P4 → P5 → P6 ကို မရပ်မမေးဘဲ ဆက်လုပ်မယ်။ P7/P8 မလုပ်ရ (owner စောင့်)။ အောက်က code block တစ်ခုလုံးကို agent chat အသစ်ထဲ paste လုပ်ပါ။

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Media3). Repository: https://github.com/Alalkipgen/YFT
Branch: work/phase-11-download-flow   ALLOW_PUSH: true (this branch only)
ALLOW_MERGE_MAIN: false   ALLOW_RELEASE: false
Owner instruction: do P3-FIX, then P4, P5, P6 in order without stopping or asking; short Burmese report after
each task (FIX_ADD_PLAN 0.5) and all of them in the final message. Do NOT start P7 or P8.
The repository is the source of truth; if plan and verified code disagree, the code wins.

START
1. Follow AGENTS.md: git fetch --all --prune; git checkout work/phase-11-download-flow; git pull;
   git status; git log -5 --oneline (expect the "handoff: P3-FIX …" commit or later).
2. Read docs/FIX_ADD_PLAN.md sections 0, 3, 4 and tasks P4–P6, docs/SESSION_STATE.md,
   docs/HANDOFF.md, and docs/prompts/P4-…, P5-…, P6-*.md (each task's own prompt).
3. Set up the build environment (ENVIRONMENT below). Before a "fails on the old code" mutation
   check, copy uncommitted files outside the repo and restore from there (never `git checkout`
   uncommitted work).

ENVIRONMENT (sandbox reset 2026-10-05)
- /data/yft-env.sh, /data/tools (gw.sh, testsum.sh, ci-html.py, JDK, Android SDK) and the SSH
  deploy key are gone. Recreate: JDK 17, Android SDK 35 (platform + build-tools), NDK
  27.3.13750724, CMake 3.22.1, then a memory-safe Gradle call: ./gradlew --no-daemon
  --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m"
  -Pkotlin.compiler.execution.strategy=in-process <tasks> (one Gradle call at a time; long runs in
  the background and poll). Pushing needs a write credential: ask the owner for a new deploy key
  only if the GitHub connector cannot push; never commit it. Keep scripts/logs outside the repo.

P3 CI: GREEN (owner, 2026-10-05) — checkpoint #146
https://github.com/Alalkipgen/YFT/actions/runs/37224812846, emulator smoke #25
https://github.com/Alalkipgen/YFT/actions/runs/37224812796 (already in TEST_MATRIX P3).

P3-FIX — owner phone check 2026-10-05 (debug APK of 56f0c79). Find the root cause yourself
first (reproduce with tests / CI emulator / live pages); the notes below are symptoms and
suspects, not verified causes.
1. Sheet sections. Home → Facebook reel sheet showed: "Music" (M4A · Fast 48 kbps ~361 KB [Slow];
   MP3 192 kbps ~1.4 MB [Slow]), "Video" (Fast 478p · 30 fps · 2.9 MB) and More formats
   (6 formats: VIDEO 478p 848×478 and 358p 636×358 "Video + audio"; AUDIO M4A 48 kbps "The video's
   own sound", MP3 320/192/128). The owner reads Music and Audio as duplicates. Wanted (Snaptube
   style), same for every site: exactly two sections —
   Audio: M4A and MP3 (MP3 bitrates as choices),
   Video: MP4, one row per resolution (240p, 360p, 480p, 720p, 1080p and higher when present)
   with sizes; the default quality preselected. No Music quick rows + More formats duplication,
   no "Fast"/"High" names. Label a row with the standard name for its real height (848×478 →
   "480p", 636×358 → "360p": nearest of 144/240/360/480/720/1080/1440/2160/4320) and keep the real
   "848 × 478 · 30 fps" in the detail, so no height is invented. Keep testTags where the element
   still exists; record removed/renamed tags in docs/design/DESIGN-NOTES.md and update tests.
2. Browser on Facebook. Page facebook.com/story.php (mobile page, "Log in / Open app"); the
   Download button opened the old "Found on this page" list with 26 rows "Video file · MP4 ·
   2.9 MB" / "105 KB", in duplicate pairs, instead of the Download sheet. The button still looks/
   acts like the old one. Required: in the browser the Download button opens the same Download
   sheet (Audio + Video sections) for the page's video; the found list shows one row per real
   video only when the page has several, each row opening the sheet. Suspects to check:
   story.php / permalink.php / posts / groups / m. / mbasic. URLs not identified by FacebookUrls
   (no adapter run, no videoId); the page player fetching one file with byte-range parameters
   (bytestart/byteend) or per-segment URLs, each counted as its own MP4 by generic detection;
   MediaGroups unable to group without a videoId or a known length; the FAB opening the list
   when groups > 1.
3. Generic web: same rule — Download button → Download sheet for the page's main video (Audio
   M4A/MP3 when the audio can be had, Video MP4 rows by resolution); URLs that differ only by
   range/segment parameters collapse to one file; distinct videos stay apart.
Tests: regressions that fail on the old code (Audio + Video sections only, standard labels, the
FAB opens the sheet for one Facebook video, range/segment duplicates collapse, story.php
identified), plus a live check of a public Facebook story/post page (status, markers only).
Finish P3-FIX like a task (validation, docs, checkpoint "P3-fix: …", CI green, Burmese report),
set P3 back to OWNER CHECK, then continue with P4 immediately. In P4 the Facebook DASH qualities
appear as rows of the same Video section.

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
- Validation (report only what ran): ./gradlew (memory-safe flags above) -q :core-model:test
  :core-media:testDebugUnitTest :core-download:testDebugUnitTest :extractor-sites:test
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
- Kotlin lines ≤ 100: git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' |
  awk 'length > 101' (prints nothing); for untracked new files use LC_ALL=C.UTF-8 awk 'length>100'.
- Docs: SUPPORT_MATRIX, TEST_MATRIX (new section per task with CI links), CHANGELOG
  [Unreleased], FIX_ADD_PLAN status board (→ OWNER CHECK when only the phone check is left, or
  DONE) + Result note, SESSION_STATE, HANDOFF, PHASE_STATUS.
- Checkpoint (after setting up the environment): CHECKPOINT_TEST_COMMAND="<validation>" bash
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
