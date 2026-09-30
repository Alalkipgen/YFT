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
| Checkpoint safeguards | Bash/temp repositories/YAML parser | Branch, secret and syntax checks behave correctly | PASS |

## Phase 1 automated checks

| Test | Environment | Expected | Current result |
| --- | --- | --- | --- |
| Production module graph | Gradle 8.9 | All nine modules configure | PASS |
| App starts at Home route | Robolectric Compose | Home semantics visible | PASS |
| Required destinations | Robolectric Compose | Browser, Detected Media, Preview, Downloads, Library, Settings and About open | PASS |
| Back navigation | Robolectric Compose | Every destination returns to Home | PASS |
| Theme state reducer | JVM unit test | System/light/dark actions are deterministic | PASS |
| Theme persistence | DataStore unit test | System default and saved selection | PASS |
| Room baseline DAO | Robolectric Room test | Upsert/count/find operate | PASS |
| Room migration 1→2 | MigrationTestHelper | Row preserved; nullable error code added | PASS |
| Result/error model | JVM unit test | Success maps; failure is preserved | PASS |
| Secret redaction | JVM unit test | Cookie/auth/password/token/signature values removed | PASS |
| OkHttp foundation | JVM unit test | Bounded timeouts, redirects, no logging interceptors | PASS |
| Android lint | Local JDK 17/SDK 35 | No blocking findings across Android modules | PASS |
| Debug build | Local JDK 17/SDK 35 | APK assembled | PASS |
| Minified release build | Local JDK 17/SDK 35 | Unsigned release APK assembled | PASS |
| Full work-branch CI | GitHub Actions | Lint, all Android/JVM tests and debug build pass | PASS — run 36791877079 |

## Runtime tests still requiring a device/emulator

| Test | Required environment | Success criterion | Current result |
| --- | --- | --- | --- |
| On-device app launch/navigation | Android API 24+ device/emulator | App launches to Home and routes render | NOT RUN — no device/KVM; Robolectric navigation smoke test passes |
| Direct HTTPS MP4 preview | Android API 24+ device/emulator | Player reaches ready and renders | NOT RUN |
| Non-DRM HLS preview | Android API 24+ device/emulator | Selected stream reaches ready | NOT RUN |
| Non-DRM DASH preview | Android API 24+ device/emulator | Selected stream reaches ready | NOT RUN |
| DOM candidate extraction | WebView test page | URLs returned without page mutation | NOT RUN — Phase 2 implementation pending |
| Cookie/header preview | Controlled authenticated fixture | Preview succeeds with session context | NOT RUN |
| DRM fixture | Known encrypted manifest | Structured unsupported result | NOT RUN |

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
