# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: UI redesign (Phase 6 follow-up) COMPLETE and merged with Phase 7 (`main` was `a6bd059`; `v1.0.0-beta.1` published by the owner on 2026-10-02). Release `1.0.0-beta.2` (versionCode 2), the first build with the redesign, is being prepared
- Current branch: `work/phase-6-ui-redesign` (created from `4bdad07`), now containing `origin/main`; `main` is fast-forwarded to it once CI passes. On 2026-10-03 the owner asked: finish Tasks 8 and 9, merge into `main`, then build a signed release APK with the signing key from the repository secrets
- Redesign checkpoints (one per task): 0 `81592fd` · 1 `2ce6e4d` · 2 `3673eb2` · 3 `9fa41c2` · 4 `548d205` · 5 `dcd1019` · 6 `4a64606` · 7 `59b88fb` · 8 `e7df19e` · 9 `328d2fb`. Decisions and remaining differences: `docs/design/DESIGN-NOTES.md`
- Last completed task: merge of `origin/main` — kept the redesign app icon (this branch's `ic_launcher_foreground.xml`, mipmap XMLs and PNGs) and Phase 7's launch theme, signing, scripts and workflows; `yft_launch_background` is Deep Teal `#007A6E` and main's unused `ic_launcher_background` color is gone; `scripts/generate-launcher-icons.py` reads the gradient and the mark from the vectors. Version `1.0.0-beta.2` / versionCode 2 with its CHANGELOG section (beta.1 dated 2026-10-02) and `docs/release/1.0.0-beta.2.md`; README, RELEASE, HANDOFF, PHASE_STATUS and TEST_MATRIX updated
- Work in progress: none
- Build status: merge GREEN — `source /data/yft-env.sh && ./gradlew --no-daemon -q --continue testDebugUnitTest lintDebug :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:assembleRelease` then `bash scripts/verify-release-apk.sh --allow-unsigned --expected-version 1.0.0-beta.2 app/build/outputs/apk/release/app-release-unsigned.apk`: 687 tests, 0 failures (app 384 incl. `AppIdentityTest` 5, 41 render tests skipped without `YFT_RENDER_DIR`; core-browser 46, core-data 12, core-download 81, core-media 14, core-model 30, extractor-api 22, extractor-generic 7, extractor-sites 91); lint 0 errors (app 83 warnings, core-data 1); unsigned minified release APK 3,360,065 bytes, version 1.0.0-beta.2 (2), not debuggable, zip-aligned
- Known failure/blocker: no device/emulator (`/dev/kvm` unavailable). Build env (this sandbox): `source /data/yft-env.sh` — `JAVA_HOME=/data/.tools/jdk17`, `ANDROID_HOME=ANDROID_SDK_ROOT=/data/.android-sdk`, `GRADLE_USER_HOME=/data/.gradle-home`, `~/.m2 -> /data/.m2` (Robolectric jars); kill stale daemons with `pkill -f "[G]radleDaemon"`
- Next exact action: after CI passes on this commit: `git checkout main && git merge --ff-only work/phase-6-ui-redesign && git push origin main`, then `git tag v1.0.0-beta.2 && git push origin v1.0.0-beta.2` to run `release-draft.yml` (secrets in the `release` environment; signed draft pre-release with the APK and `SHA256SUMS`). Owner: review the draft, test on a phone, publish with `ALLOW_RELEASE=true`
- Last pushed checkpoint: merge of `main` (this commit, after Task 9 `328d2fb`)
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
