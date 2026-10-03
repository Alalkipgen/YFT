# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 8 — field fixes, IN PROGRESS (`docs/FIX_PLAN.md`). beta.2 is unchanged; main merges, tags and releases are not authorized.
- Current branch: `work/phase-8-field-fixes` (tracking origin, opened from `f18d924`).
- Last completed task: T01 — fix off-main WebView access. Atomic page-URL snapshot, main-thread User-Agent cache and main-dispatcher observation handling; status OWNER CHECK.
- Work in progress: no unfinished code; checkpoint CI and the real-WebView/phone check remain.
- Build status: GREEN — `source /data/yft-env.sh && ./gradlew --no-daemon -q --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :core-browser:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`: core-browser 53, app 386 (41 render tests skipped), 0 failures/errors; lint 0 errors, 83 existing warnings; added Kotlin width and whitespace checks pass.
- Regression proof: `interceptsRequestsOffMainWithoutTouchingWebViewOrSettings` failed on the old client with the main-looper guard and passes on the fix. Navigation/history/redirects, 32 concurrent callbacks and stale observations covered. Audit grep recorded in FIX_PLAN F1 and the commit message.
- Known failure/blocker: no local device/emulator (`/dev/kvm` unavailable). Initial default-memory baseline lost its daemon to an OOM kill; low-memory retry passed and orphaned worker stopped. T03 and site fixes still pending. D1–D3 remain PENDING.
- Environment: `source /data/yft-env.sh` sets JDK 17 (`/data/toolchains/jdk17`), SDK 35 (`/data/android-sdk`), Gradle cache (`/data/gradle-home`) and the repository SSH key/verified host file. Temporary logs/helpers are outside the repo.
- Next exact action: check T01 CI, then T02 (`docs/prompts/T02-ci-emulator-smoke.md`). Continue eligible Phase 8 tasks in order as the owner requested. Phone: Your sites, Home's Open in browser and typed Go; pages load and found media appear.
- Last pushed checkpoint: T01 (this commit); previous `f18d924`. Push work branch only; do not merge main, push tags or publish.
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
