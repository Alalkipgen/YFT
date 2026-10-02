# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 7 — Signed beta and GitHub release preparation: PARTIAL. All agent work is done and verified; the owner-signed beta APK, the draft GitHub release and the device checks wait for the owner's permanent keystore and a device. Phase 6 is complete at `4bdad07`
- Current branch: `work/phase-7-release` (created from `4bdad07`); nothing merged into `main`; no tag, draft or published release
- Last completed task: Phase 7 validation and docs — `scripts/release-prep.sh` with throwaway key A (uncached clean, lint, 459 tests, signed release, v2+v3 verification, certificate pin, SHA-256 staging), Gradle signing-path checks, static upgrade check (versionCode 2 over 1) and 9 verification negatives; TEST_MATRIX Phase 7 table and device rows, PHASE_STATUS, HANDOFF, ARCHITECTURE, SUPPORT_MATRIX, README, RELEASE. Throwaway keys and test APKs deleted from `/tmp`
- Work in progress: none uncommitted
- Build status: PASS — `bash scripts/release-prep.sh` (throwaway key, source `36f3395`): clean 11 s, `lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test` 4m 5s (459 tests, 0 failures; lint 0 errors), `:app:assembleRelease -Pyft.requireReleaseSigning=true` 4m 40s; signed test APK 3,011,624 bytes verified. CI `validate` success on `4bdad07` and `b5797de`
- Known failure/blocker: the owner's permanent release keystore is not available (never request it through chat; use repository secrets or a local `keystore.properties`); no device/emulator (`/dev/kvm` unavailable); the agent's deploy key cannot create tags, releases, secrets or workflow runs. Build env: `export JAVA_HOME=/data/toolchains/jdk17 ANDROID_HOME=/data/android-sdk ANDROID_SDK_ROOT=/data/android-sdk`; kill stale daemons with `pkill -f "[G]radleDaemon"`
- Next exact action: owner — reconfirm `com.alal.yft`, create/back up the keystore, add `YFT_RELEASE_KEYSTORE_BASE64`, `YFT_RELEASE_STORE_PASSWORD`, `YFT_RELEASE_KEY_ALIAS`, `YFT_RELEASE_KEY_PASSWORD` secrets and the `YFT_RELEASE_CERT_SHA256` variable, push tag `v1.0.0-beta.1` (draft only), run `scripts/device-smoke-test.sh` and the `docs/RELEASE.md` §4 checklist, publish only with `ALLOW_RELEASE=true`, then merge into `main`
- Last pushed checkpoint: `36f3395` — release-prep without build cache; the `phase-7:` completion commit follows
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
