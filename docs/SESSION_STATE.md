# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: 11 — download flow like Snaptube (`docs/FIX_ADD_PLAN.md`). Order P0 → P1 → … → P6 → P7 (test-key preview APK) → owner phone test → P8 (signed `1.0.0-beta.4`). Owner instruction 2026-10-04: continue P1 → P7 without asking; short Burmese report after each task. Nothing is merged into `main` before P8.
- Rules: [ADR-006](decisions/ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03) — any working technique for public videos; still no DRM, paid, private-content or age-gate bypass; adapters never sign in; secrets never logged or committed. D2 = A + B + C. D1 = YES and D3 = YES (owner delegated the choice, 2026-10-03: "do as you see fit; do not stop; commit and push after every task").
- Current branch: `work/phase-11-download-flow` (from `main` `2f6284f`, tracking origin).
- Last completed task: P3-FIX OWNER CHECK (2026-10-05) — after the owner's phone check of `56f0c79`: the Download sheet has exactly two sections, Audio (best M4A, else the MP4's own sound as M4A; MP3 320/192/128) and Video (one row per standard resolution, 848 × 478 → "480p" with the real picture in the detail; no Fast/High, no Music rows, no More formats); `FacebookUrls` identifies `story.php`, `permalink.php`, `/{page}/posts/{id}` and group posts (canonical `https://www.facebook.com/{id}/posts/{story_fbid}`, live: the desktop posts path redirects to the video page, `story.php` is a login wall); byte-range pieces (`bytestart`/`byteend`, `range=`) collapse to one file (`MediaFileUrls.wholeFile` in the generic normalizer and the browser mapper); `MediaGroups.pageVideos` counts only the adapter's video when it named one, so the browser's Download button opens the sheet. P3 OWNER CHECK (2026-10-04, `56f0c79`; CI checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37224812846, emulator https://github.com/Alalkipgen/YFT/actions/runs/37224812796). P2 OWNER CHECK (2026-10-04, `8421700`). P1 OWNER CHECK (2026-10-04). P0 DONE (2026-10-04, `99efca6`). T19 DONE (2026-10-04): `main` = `2f6284f`, tag `v1.0.0-beta.3`, signed draft pre-release (APK 6,334,176 bytes, SHA-256 `8fe466f1…988f`, release run https://github.com/Alalkipgen/YFT/actions/runs/37204457527). Phases 8–10 task log: `git show 2f6284f:docs/SESSION_STATE.md`.
- Work in progress: P4 — Facebook: one video, every quality (inline MPD `SegmentBase` whole files as merged Video rows + HE-AAC audio), then P5, P6 per `docs/prompts/CONTINUE-P4-TO-P6.md`. Not P7/P8. P3-FIX CI: see TEST_MATRIX P3-FIX.
- Build status: P3-FIX — core-model 63, core-media 24, core-download 107, extractor-sites 155, extractor-generic 14, core-browser 67, app 565 (66 render tests skipped) tests, 0 failures; `:app:lintDebug` 0 errors (95 warnings, unchanged). Regressions: with the old code put back 10 of 70 targeted tests fail — exactly the new ones (TEST_MATRIX P3-FIX).
- Known limitations: no local KVM; native PNG download requires authentication (HTTP 401), so no native pixel-review claim. Sandbox live checks use a datacenter IP: one YouTube test video stays bot-checked for every client even with a minted token, so YouTube needs the owner's phone check. The page player's own PO token is not captured, and in-page YouTube navigation without a page load does not rerun the lookup (FIX_PLAN §9).
- Environment (sandbox reset 2026-10-05, recreated): `source /data/yft-env.sh` sets JDK 17 (`/data/toolchains/jdk17`), SDK 35 with NDK 27.3.13750724 and CMake 3.22.1 (`/data/android-sdk`) and the Gradle cache (`/data/.gradle-home`). SSH deploy key outside the repo (`/data/.ssh-yft/`, write). One memory-safe Gradle call at a time: `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=512m"` (384m ran out of Metaspace in kapt) `-Pkotlin.compiler.execution.strategy=in-process`. The public GitHub API often hits its 60/hour limit from the sandbox.
- Next exact action: P4 (Facebook inline MPD qualities), then P5, P6 per `docs/prompts/CONTINUE-P4-TO-P6.md`. Not P7/P8.
- Last pushed checkpoint: P3-FIX (this commit); handoff commits `9fcaa56`, `6a4930f` (docs only); P3 `56f0c79`; P2 `8421700`; P1 `9afd965`; P0 `99efca6`; T19 `2f6284f`; earlier in `git show 2f6284f:docs/SESSION_STATE.md`.
- Last updated: 2026-10-05

## P4 work in progress (WIP commit, not a checkpoint)

Saved at the owner's request before the session budget ran out. P4 is not finished and has not
been through the full checkpoint.

In this commit: Facebook DASH manifest parsing (`FacebookDashManifest`, `FacebookDashOffers`),
merged 360p/720p/1080p rows plus an Audio row, the anonymous Safari page for the AVC ladder,
the AV1 merge gate (Android 14+, `MergeSupport`), quick-download order (a complete file before
a merge), `AudioVideoMuxerInstrumentedTest` with its assets, and the resolver keeping a stated
`audio/mp4` when the CDN says `video/mp4` (its new test has not run yet).
Last runs: extractor-sites 166 tests / 0 failures, core-download 109 / 0, app (targeted) 86 / 0.

Next:
1. `FacebookExtractor.trackCandidates`: leave a merged row's `contentLengthBytes` null (AVC
   bandwidth is a peak, about 3.5x the real size) and set `companion.contentLengthBytes` to
   audio bandwidth x duration / 8000; update `FacebookDashExtractorTest` (5_502_152 -> null,
   companion 225_902).
2. `DownloadPlanFactory.extensionFor`: an audio track in an MP4 container should be `.m4a`
   (today a direct `audio/mp4` variant with container "MP4" is saved as `.mp4`).
3. Run core-media tests, the full checkpoint tasks, line-length and mutation checks.
4. Docs (SUPPORT_MATRIX, TEST_MATRIX P4 plus P3-FIX CI links: checkpoint #149 run 37234905286,
   emulator #26 run 37234905207), CHANGELOG, FIX_ADD_PLAN, HANDOFF, PHASE_STATUS; checkpoint
   "P4: ...", CI green; then P5 and P6 per `docs/prompts/CONTINUE-P4-TO-P6.md`.
