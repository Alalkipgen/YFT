# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 8 — field fixes, IN PROGRESS (`docs/FIX_PLAN.md`). beta.2 unchanged; no main merge, tag or release authorized.
- Current branch: `work/phase-8-field-fixes` (tracking origin).
- Last completed task: T06 OWNER CHECK (2026-10-03) — Facebook public reels without sign-in: licence-based DRM only, unreadable drm_info as a details warning, share-link IDs from redirects, single HTML-entity decoding with Facebook suffix trim, heights only from DASH/rendition metadata. T05/T02 DONE; T01/T03/T04 OWNER CHECK.
- Work in progress: none. T06 checkpoint pushed; its two CI workflows are checked before T07 starts.
- Build status: GREEN — `source /data/yft-env.sh && ./gradlew --no-daemon -q --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`: extractor-api 26, extractor-sites 109, app 437 (54 render skips), 0 failures/errors; lint 0 errors/95 warnings; instrumentation APK compiled. After the og:image decoding fix: extractor-sites 110, 0 failures. Python 22/22, diff-check and new Kotlin <=100 audit pass.
- Regression: T06 certificate-only drm_info (empty licence map) FAILED old as DRM_PROTECTED; PASSED fixed with HD, SD and DASH. Licence maps, graph licence URIs and explicit flags still block; share redirects, entities, heights and login walls are tested.
- Public live check: owner's share link https://www.facebook.com/share/v/1Q3kAyptrS/ → HTTP 200, www.facebook.com/reel/1603698891196107/, 610752 bytes; parser Success, 2 progressive (HD, SD) + 1 DASH; ranged SD GET → 206. Only safe summary output.
- CI: T05 `cbc92e5ccdb491ce71ef1fe78134caa66f007dee` GREEN: emulator https://github.com/Alalkipgen/YFT/actions/runs/37152854933 and checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37152854989 (`yft-debug-apk`). Check the T06 checkpoint's two workflows before T07.
- Known limitations: no local KVM; native PNG download requires authentication (HTTP 401), so no native pixel-review claim. Phone checks remain OWNER CHECK (T01/T03/T04 browser/crash flows; T06 Facebook Home download and playback). TikTok media cookies T07, YouTube bot/SABR blockers T08/T16. D1–D3 PENDING; clipboard stays tap-only.
- Environment: `source /data/yft-env.sh` sets JDK 17, SDK 35, Gradle cache and verified SSH access. Temporary data outside repo; one memory-safe Gradle at a time. The latest local unsigned release (T04) passes the debug-action guard but is not installable/published.
- Next exact action: check the T06 checkpoint CI (fix a red run first); then T07 per `docs/prompts/T07-tiktok-media-cookies.md` (baseline with :core-download:testDebugUnitTest, Set-Cookie pairs on ExtractorHttpResult.Success, TikTok media cookie from page cookies, live 206 check). Continue eligible Phase 8 only; no main merge, tags or release.
- Last pushed checkpoint: T06 (this checkpoint) after T05 `cbc92e5`; T04 final `ce881f7`, implementation `3b9224c`. T03 completion `336851b`, implementation `9be6b43`; T02 completion `66a8823`, repair `e9e1a09`; T01 `7179637`.
- Last updated: 2026-10-03
