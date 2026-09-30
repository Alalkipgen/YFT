# Test Matrix

## Phase 0 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Preview kind recognizes `.m3u8` with query | JVM unit test | HLS | PASS |
| Preview kind recognizes `.mpd` with fragment | JVM unit test | DASH | PASS |
| Other HTTPS media defaults to direct | JVM unit test | Direct | PASS |
| Replay headers strip Range/Connection | JVM unit test | Stripped | PASS |
| Replay headers preserve safe browser context | JVM unit test | Cookie/UA/HTTPS Referer available | PASS |
| DOM probe targets HTML media elements | JVM unit test | Read-only script structure | PASS |
| Media3 direct/HLS/DASH sources compile | Android debug build | Build succeeds | PASS |
| Android lint | Local Android toolchain | No blocking errors | PASS |
| Phase 0 CI workflow | GitHub Actions, JDK 17, SDK 35, Gradle 8.9 | Lint, tests and debug build pass | PASS — run 36783628412 |
| Checkpoint script syntax | Local Bash | `bash -n` succeeds | PASS |
| Remote checkpoint flow | Temporary local/bare Git remotes | Work-branch commit and push succeed | PASS |
| Main branch safeguard | Temporary Git repository | Checkpoint is refused | PASS |
| Sensitive-file safeguard | Temporary Git repository | `.jks` checkpoint is refused | PASS |
| Checkpoint workflow YAML | PyYAML parse | Valid workflow structure | PASS |

## Device tests required before Phase 0 is fully closed

| Test | Required environment | Success criterion |
| --- | --- | --- |
| Direct HTTPS MP4 preview | Android API 24+ device/emulator | Player reaches ready and renders |
| Non-DRM HLS preview | Android API 24+ device/emulator | Selected stream reaches ready |
| Non-DRM DASH preview | Android API 24+ device/emulator | Selected stream reaches ready |
| DOM candidate extraction | WebView test page | URLs returned without page mutation |
| Cookie/header preview | Controlled authenticated fixture | Preview succeeds with session context |
| DRM fixture | Known encrypted manifest | Structured unsupported result |

## Later regression categories

- Redirects and URL expiry
- Process death and task recovery
- Range/no-range direct servers
- HLS/DASH malformed manifests
- Separate audio/video muxing
- Low storage
- Network switching
- Site-adapter fixture drift
- Secret-redaction tests
