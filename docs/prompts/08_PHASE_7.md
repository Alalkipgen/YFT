CURRENT TASK: PHASE 7 — SIGNED BETA AND GITHUB RELEASE PREPARATION

Verify Phase 6 and review all known issues first.

OBJECTIVES
- Finalize identity, version, icon/splash, About/Privacy, license notices and accurate README.
- Produce and verify a signed beta APK.
- Generate SHA-256 and release notes.
- Prepare a GitHub Release; publish only with explicit ALLOW_RELEASE=true.

SIGNING
Use the user's permanent keystore through secure local/CI configuration. Never commit or print keystore/passwords. Verify the final certificate fingerprint. If credentials are unavailable, request them securely and stop before fake success.

VALIDATION
Run clean, lint, unit/fixture tests, release build, APK signature verification and checksum generation. Test fresh install, launch, detection, preview, direct and HLS/DASH download, pause/resume, output playback and signed upgrade where possible.

ARTIFACTS
Signed APK, SHA-256, changelog, known issues, supported Android versions and installation instructions.

RELEASE
Create a draft release with clear tag/version, APK, checksum, tested changelog and limitations. Do not publish unless ALLOW_RELEASE is true.

DEFINITION OF DONE
- Tests pass; APK is signed and signature verified.
- Checksum and install test exist; upgrade test performed where possible.
- README claims only tested features.
- No key/password appears in Git history.
- Docs/handoff/changelog updated; phase-7 commit created.
- Push/release status reported honestly.
