# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 6 follow-up — UI redesign to the owner's images (`docs/design/DESIGN-NOTES.md`, `docs/design/reference/`). Phase 6 itself is complete at `4bdad07`. Phase 7 has NOT started and must not start until the owner asks
- Current branch: `work/phase-6-ui-redesign` (created from `4bdad07`; never merge to `main` without the owner)
- Redesign plan (one checkpoint per task): 1 design system ✅ · 2 app icon + shell ✅ · 3 Home + Promptbox states + Your sites + Recent + link inspector · 4 Browser + Found on this page sheet · 5 Download as sheet (Preview) · 6 Downloads · 7 Library + mini player · 8 Settings + About · 9 dark/a11y pass, screenshot renders, docs, full validation
- Last completed task: Task 2 — app icon + shell: adaptive launcher icon (`res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, `mipmap-anydpi-v26`, legacy PNGs in `mipmap-*dpi`, in-app `yft_logo.xml`), window background per theme (`values-night`), `YftBottomBar` (Home · Downloads · Library · Settings, Mint pill, Coral badge = `activeDownloadCount()` of PROBING/RUNNING/PAUSING/VERIFYING, count announced as the tab state), `YftAppShell` (bar on tabs only; Browser, Detected Media, Preview, About full screen; tab switches save/restore state; back from a tab returns Home), system-bar icons follow the app theme, Settings → About row. Task 1 (design system) is at `2ce6e4d`
- Work in progress: none uncommitted
- Build status: PASS — `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` (app unit tests 0 failures; lint 0 errors). Phone-size renders for design comparison: `YFT_RENDER_DIR=/tmp/renders ./gradlew --no-daemon :app:testDebugUnitTest --tests "*DesignRenderTest"` (skipped without the variable)
- Known failure/blocker: no device/emulator (`/dev/kvm` unavailable); Robolectric jars cached for SDK 28 and 35 only. Build env: `export JAVA_HOME=/data/toolchains/jdk17 ANDROID_HOME=/data/android-sdk ANDROID_SDK_ROOT=/data/android-sdk`; kill stale daemons with `pkill -f "[G]radleDaemon"`
- Next exact action: Task 3 — Home to `01-home-light`/`07-home-dark`: logo + YFT wordmark + No ads chip, headline, Promptbox card (Paste / Open browser chips, clipboard hint from ClipDescription only, Searching → Found/NotFound via a headless link inspector that publishes to `DetectedMediaStore`), Your sites (DataStore list, Add/Edit), Recent (2 newest Library items, See all → Library); drop the old destination list
- Last pushed checkpoint: `2ce6e4d` — Task 1 design system; this Task 2 checkpoint follows
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
