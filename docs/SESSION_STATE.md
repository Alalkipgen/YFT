# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 8 — field fixes, IN PROGRESS (`docs/FIX_PLAN.md`). beta.2 unchanged; no main merge, tag or release authorized.
- Current branch: `work/phase-8-field-fixes` (tracking origin).
- Last completed task: T05 DONE (2026-10-03) — honest desktop YFT identity on Home adapters, generic fetcher and direct/markup candidates; shared navigation-only defaults; safe public live checker. T01/T03/T04 OWNER CHECK; T02 DONE.
- Work in progress: T05 checkpoint/CI. No T06 changes yet; page identity is fixed but Facebook's false DRM classification remains T06.
- Build status: GREEN — `source /data/yft-env.sh && ./gradlew --no-daemon -q --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :core-model:test :core-browser:testDebugUnitTest :extractor-api:test :extractor-generic:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`: core-model 38, core-browser 54, extractor-api 24, extractor-generic 7, extractor-sites 95, app 436 (54 render skips), 0 failures/errors; lint 0 errors/95 warnings; instrumentation APK compiled. Python 22/22, live checker bash-n/shellcheck, diff-check and new Kotlin <=100 audit pass.
- Regression: Home adapter context User-Agent test FAILED old (null); PASSED fixed. Four adapter GETs and redirect/default/header casing/snapshots are tested; Vimeo/YouTube JSON headers and existing browser identity remain unchanged. No browser cookie is injected.
- Public live check: `bash scripts/live-check.sh https://www.facebook.com/share/v/1Q3kAyptrS/` → HTTP 200, host www.facebook.com, path /reel/1603698891196107/, 609875 bytes; HD marker yes, TikTok/YouTube markers no. Only safe summary output; no extraction/download claim until T06.
- CI: final T04 `ce881f72a48fd71cc8a38161567a01d8018e8365` GREEN: emulator https://github.com/Alalkipgen/YFT/actions/runs/37151155280 (3 tests, 0 failures/errors/fatal exceptions), checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37151155259 (`yft-debug-apk`). Check this T05 checkpoint's two workflows before T06.
- Known limitations: no local KVM; native PNG download requires authentication (HTTP 401), so no native pixel-review claim. Phone crash/restart/export and browser checks remain OWNER CHECK. Facebook DRM parser T06, TikTok media cookies T07, YouTube bot/SABR blockers T08/T16. D1–D3 PENDING; clipboard stays tap-only.
- Environment: `source /data/yft-env.sh` sets JDK 17, SDK 35, Gradle cache and verified SSH access. Temporary data outside repo; one memory-safe Gradle at a time. The latest local unsigned release (T04) passes the debug-action guard but is not installable/published.
- Next exact action: check this T05 checkpoint's CI; if both workflows green, run T06 baseline and follow `docs/prompts/T06-facebook-public-video.md`, including sanitized public fixture/parser and SD 206 check. Continue eligible Phase 8 only; no main merge, tags or release.
- Last pushed checkpoint: final T04 `ce881f7`, implementation `3b9224c`; this T05 task checkpoint follows it. T03 completion `336851b`, implementation `9be6b43`; T02 completion `66a8823`, repair `e9e1a09`; T01 `7179637`.
- Last updated: 2026-10-03
