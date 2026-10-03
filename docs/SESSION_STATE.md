# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 8 — field fixes, IN PROGRESS (`docs/FIX_PLAN.md`). beta.2 is unchanged; main merges, tags and releases are not authorized.
- Current branch: `work/phase-8-field-fixes` (tracking origin, opened from `f18d924`).
- Last completed task: T03 OWNER CHECK — Compose start page, deferred/stable WebView, saved sites, tap-only clipboard, opaque/clipped bar, accessible idle field and 200% count repair. T02 DONE; T01 remains OWNER CHECK.
- Work in progress: T04 — local capped crash store/handler, About View/Copy/Share/Delete, source-set-only debug trigger, release DEX guard and sanitized memory-only Home lookup details implemented. Full validation GREEN; native-canvas render inspection and actual release APK check remain.
- CI: T03 `9be6b43` GREEN in both workflows: emulator https://github.com/Alalkipgen/YFT/actions/runs/37146164024 (3/3 tests, 0 fatal exceptions, 3 PNGs; empty WebView absent, loaded/found address bounds restored); checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37146164049 (`yft-debug-apk`).
- Build status: T04 GREEN — `source /data/yft-env.sh && ./gradlew --no-daemon -q --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :core-model:test :extractor-api:test :extractor-sites:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`: core-model 35, extractor-api 24, extractor-sites 91, app 432 (53 render skips), 0 failures/errors; lint 0 errors/95 warnings; instrumentation APK compiled. Python script tests 13/13; release verifier shellcheck/bash-n pass; new Kotlin lines <=100.
- Regression proof: T04 Home Copy details test FAILED old (control absent), PASSED fixed. Initial compile used the wrong theme field (repaired); focused 76-test run had one incorrect null-ClipData assertion (Android creates safe text ClipData), repaired and full validation passes. Handler cap/redaction/delegation, immutable step sanitation, About visibility/actions/share/delete and clearing old lookup details are covered. T03 final 8 browser PNGs passed inspection.
- Known failure/blocker: native PNG download needs authentication (HTTP 401), so no native pixel-review claim and no confirmed phone-overlay cause. No local KVM; phone checks remain. D1–D3 PENDING.
- Environment: `source /data/yft-env.sh` sets JDK 17 (`/data/toolchains/jdk17`), SDK 35 (`/data/android-sdk`), Gradle cache (`/data/gradle-home`) and the repository SSH key/verified host file. Temporary logs/helpers are outside the repo.
- Next exact action: render/inspect all 8 T04 PNGs (DiagnosticsRenderTest); separately assembleRelease and verify the APK/debug-action guard; check this checkpoint's CI. If green, record T04 OWNER CHECK and continue T05. No main merge, tags or release.
- Last pushed checkpoint: T04 implementation milestone (this commit); T03 completion `336851b` CI GREEN https://github.com/Alalkipgen/YFT/actions/runs/37147045869, implementation `9be6b43`, T02 completion `66a8823`, repair `e9e1a09`, T01 `7179637`.
- Last updated: 2026-10-03

## Checkpoint note template

```text
Current phase:
Current branch:
Last completed task:
Work in progress:
Build status and exact command:
Known failure/blocker:
Next exact action:
Last pushed checkpoint:
Last updated:
```
