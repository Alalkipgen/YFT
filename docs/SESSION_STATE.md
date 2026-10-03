# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 8 — field fixes, IN PROGRESS (`docs/FIX_PLAN.md`). beta.2 is unchanged; main merges, tags and releases are not authorized.
- Current branch: `work/phase-8-field-fixes` (tracking origin, opened from `f18d924`).
- Last completed task: T01 — fix off-main WebView access. Atomic page-URL snapshot, main-thread User-Agent cache and main-dispatcher observation handling; status OWNER CHECK.
- Work in progress: T02 — collector repair is locally green; awaiting repaired CI. First emulator run on `8151813` passed 3/3 instrumentation tests with 0 fatal exceptions, but screenshot collection failed after APK cleanup: https://github.com/Alalkipgen/YFT/actions/runs/37141758594. The checkpoint build was GREEN: https://github.com/Alalkipgen/YFT/actions/runs/37141758482.
- Build status: GREEN locally — `source /data/yft-env.sh && ./gradlew --no-daemon -q --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`: instrumentation APK compiled (3 scenarios), app 386 (41 renders skipped), 0 failures/errors; lint 0 errors, 95 warnings. Python diagnostic/collector tests 10/10; actionlint, shellcheck, `bash -n`, whitespace pass.
- Regression proof: T01 off-main guard FAILED on the old client and PASSED on the fix. T02 `test_app_external_screenshots_survive_gradle_cleanup_for_collection` FAILED on the old collector (all 3 PNGs missing) and PASSED with the keep-installed flag. Pull/logcat/test-failure propagation covered.
- Known failure/blocker: no local device/emulator (`/dev/kvm` unavailable). Initial default-memory baseline lost its daemon to an OOM kill; low-memory retry passed and orphaned worker stopped. T03 and site fixes still pending. D1–D3 remain PENDING.
- Environment: `source /data/yft-env.sh` sets JDK 17 (`/data/toolchains/jdk17`), SDK 35 (`/data/android-sdk`), Gradle cache (`/data/gradle-home`) and the repository SSH key/verified host file. Temporary logs/helpers are outside the repo.
- Next exact action: check repaired T02 emulator/checkpoint CI; inspect safe bounds and confirm all 3 screenshots. If red, fix T02 again. Only after emulator GREEN update F2/status to DONE, then T03. Phone's T01 check remains. Continue eligible Phase 8 tasks in order; no main merge, tags or release.
- Last pushed checkpoint: T02 collector repair (this commit); previous implementation `8151813`, T01 `7179637`. Artifacts: `yft-debug-apk` (checkpoint workflow), `yft-emulator-smoke` (emulator).
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
