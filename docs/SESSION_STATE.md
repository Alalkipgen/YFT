# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 7 — Signed beta and GitHub release preparation (IN PROGRESS, about 60%). Phase 6 is complete at `4bdad07`
- Current branch: `work/phase-7-release` (created from `4bdad07`)
- Last completed task: 7A — version `1.0.0-beta.1`/versionCode 1 in `gradle.properties`; original adaptive launcher icon (+ monochrome, legacy PNGs from `scripts/generate-launcher-icons.py`) and launch screen (`Theme.Yft.Launch`, Android 12+ splash attributes); release signing from `YFT_RELEASE_*` env vars or untracked `keystore.properties` (fails when partial, `-Pyft.requireReleaseSigning=true` fails when missing, never debug-key fallback); `scripts/verify-release-apk.sh`, `scripts/release-prep.sh`, `scripts/device-smoke-test.sh`; draft-only `.github/workflows/release-draft.yml`; CI builds and checks the unsigned release APK; `CHANGELOG.md`, `docs/release/1.0.0-beta.1.md`, `docs/RELEASE.md`, README
- Work in progress: none uncommitted. Phase 7 prompt: `docs/prompts/08_PHASE_7.md`
- Build status: PASS — `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` (app 180 tests incl. `AppIdentityTest`, lint 0 errors, no new warnings); `./gradlew --no-daemon :app:assembleRelease` (unsigned, 2,999,336 bytes) checked by `scripts/verify-release-apk.sh --allow-unsigned`; actionlint + shellcheck clean
- Known failure/blocker: no device/emulator (`/dev/kvm` unavailable); Robolectric jars cached for SDK 28 and 35 only. Build env: `export JAVA_HOME=/data/toolchains/jdk17 ANDROID_HOME=/data/android-sdk ANDROID_SDK_ROOT=/data/android-sdk`; kill stale daemons with `pkill -f "[G]radleDaemon"`
- Next exact action: run `scripts/release-prep.sh` with a throwaway key from `/tmp` (clean, lint, tests, signed release, verification, checksum), the versionCode-2 upgrade-compatibility check and negative signer checks, then Phase 7 docs (TEST_MATRIX, PHASE_STATUS, HANDOFF) and the `phase-7:` commit. Owner keystore is still required for a real signed beta
- Last pushed checkpoint: `4bdad07` — Phase 6 complete; this 7A checkpoint follows
- Last updated: 2026-10-02

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
