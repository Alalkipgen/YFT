# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 8 — field fixes, IN PROGRESS (`docs/FIX_PLAN.md`). beta.2 is unchanged; main merges, tags and releases are not authorized.
- Current branch: `work/phase-8-field-fixes` (tracking origin, opened from `f18d924`).
- Last completed task: T02 DONE — API 34 real-WebView smoke, safe annotations/artifacts and collector regression. T01 remains OWNER CHECK on the phone.
- Work in progress: none; T03 is next and its baseline passed (core-browser 53, app 386/41 renders skipped, 0 failures/errors, lint 0 errors/95 warnings).
- CI: T02 repair `e9e1a09` GREEN in both workflows: emulator https://github.com/Alalkipgen/YFT/actions/runs/37143005734 (3/3 tests, 0 fatal exceptions, 3 PNGs collected); checkpoint https://github.com/Alalkipgen/YFT/actions/runs/37143005667 (`yft-debug-apk`).
- Build status: GREEN locally — `source /data/yft-env.sh && ./gradlew --no-daemon -q --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`: instrumentation APK compiled (3 scenarios), app 386 (41 renders skipped), 0 failures/errors; lint 0 errors, 95 warnings. Python diagnostic/collector tests 10/10; actionlint, shellcheck, `bash -n`, whitespace pass.
- Regression proof: T01 off-main guard FAILED on the old client and PASSED on the fix. T02 `test_app_external_screenshots_survive_gradle_cleanup_for_collection` FAILED on the old collector (all 3 PNGs missing) and PASSED with the keep-installed flag. Pull/logcat/test-failure propagation covered.
- Known failure/blocker: native PNG download needs authentication (HTTP 401), so no agent pixel-review claim. Empty API 34 address/close assertions pass; loaded/found address bounds missing from accessibility tree. T03 must preserve idle-field semantics. No local KVM; phone checks still needed. D1–D3 remain PENDING.
- Environment: `source /data/yft-env.sh` sets JDK 17 (`/data/toolchains/jdk17`), SDK 35 (`/data/android-sdk`), Gradle cache (`/data/gradle-home`) and the repository SSH key/verified host file. Temporary logs/helpers are outside the repo.
- Next exact action: mark T03 IN PROGRESS and implement deferred WebView/start page; add old-code regression, route/lifecycle/clipboard/site tests, a11y and Day/Night/large-text renders; validate/checkpoint/check CI. Continue eligible Phase 8 tasks; no main merge, tags or release.
- Last pushed checkpoint: T02 completion docs (this commit); tested repair `e9e1a09`, implementation `8151813`, T01 `7179637`. Artifacts: `yft-debug-apk` and `yft-emulator-smoke`.
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
