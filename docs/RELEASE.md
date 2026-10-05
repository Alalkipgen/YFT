# Release process

This is the owner's path from a work branch to a signed GitHub release. The permanent release
key is in the repository secrets: the workflow below signed `1.0.0-beta.1`, which the owner
published as a pre-release on 2026-10-02, and `1.0.0-beta.2` (the redesigned app) is the next
pre-release. The pipeline's verification results are in the Phase 7 table of
`docs/TEST_MATRIX.md`.

## Release identity

| Item | Value | Notes |
| --- | --- | --- |
| Display name | Video Downloader | `app_name` |
| Application ID | `com.alal.yft` | Permanent once an APK is published; the owner must reconfirm it first (`docs/PROJECT_CONTEXT.md`) |
| Version | `yft.versionName` / `yft.versionCode` in `gradle.properties` | `1.0.0-beta.2` / `2` |
| Git tag | `v<versionName>` | The release workflow refuses a tag that does not match |
| Android | minSdk 24 (7.0), targetSdk/compileSdk 35 (15) | |
| Signature schemes | APK Signature Scheme v2 + v3 | v1 is unnecessary for minSdk 24; v3 allows key rotation later |
| Debug builds | `com.alal.yft.debug`, versionName suffix `-debug` | Install next to the release app, never over it |
| Preview builds (P7) | `com.alal.yft.preview`, versionName suffix `-preview.<run>`, test key | The owner's phone test; see below |

Raise `yft.versionCode` by one for every APK that leaves the machine. Android refuses a lower
versionCode and refuses any update signed by a different key.

## Preview builds for the owner's phone test (P7)

`yft-preview-apk` is the release build — minified, not debuggable, without debug actions — under
its own application ID, so it installs next to the published release and never over it. It is
signed with a throwaway **test key**, never with the release key.

| Item | Value |
| --- | --- |
| Display name | YFT Preview (`app/src/preview/res/values/strings.xml`) |
| Application ID | `com.alal.yft.preview` |
| Version | `<yft.versionName>-preview.<CI run number>`, e.g. `1.0.0-beta.3-preview.12`; versionCode as in `gradle.properties` |
| Signature | v2 + v3 with a fresh RSA 3072 test key made inside the job (`CN=YFT Preview Test Key`) and deleted at its end; the workflow reads no repository secret |
| Workflow | `.github/workflows/preview-apk.yml`: on pushes to `work/phase-*` that change the app, or by hand (**Run workflow**) |
| Checks | `scripts/verify-release-apk.sh --package com.alal.yft.preview --expected-version … --expected-cert-sha256 <test key>`: package, version, not debuggable, zip-aligned, no debug crash action, exactly one v2/v3 signer equal to the job's test key |
| Artifact | `yft-preview-apk`: `video-downloader-<version>.apk`, `SHA256SUMS`, `release-info.txt` (30 days); the run summary shows `release-info.txt` |

Install: download the artifact from the workflow run, unzip it, check the APK with
`sha256sum -c SHA256SUMS`, open it on the phone and allow installing unknown apps. Each run
makes its own key, so Android refuses to install a newer preview over an older one: uninstall
YFT Preview first (its downloads and settings go with it). The release app is not touched.

Locally, the same variables sign a preview: `YFT_PREVIEW_STORE_FILE`, `YFT_PREVIEW_STORE_PASSWORD`,
`YFT_PREVIEW_KEY_ALIAS`, `YFT_PREVIEW_KEY_PASSWORD` and `./gradlew :app:assemblePreview`.
Without them the preview stays unsigned (`app-preview-unsigned.apk`); Gradle refuses partial
settings and the release keystore. A preview is never published as a release.

## 1. Create the permanent release key (owner, once)

```bash
keytool -genkeypair -v -keystore yft-release.jks -storetype PKCS12 \
  -alias yft-release -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Video Downloader, O=<owner>"
keytool -list -v -keystore yft-release.jks -alias yft-release | grep 'SHA256:'
```

- Let `keytool` prompt for the passwords; never put them on a command line, in a file in the
  repository, in an issue or in a chat.
- Keep the keystore outside the repository and keep at least two offline backups plus the
  passwords in a password manager. A lost key means existing users can never upgrade.
- The `SHA256:` certificate fingerprint is public. Record it as the repository variable
  `YFT_RELEASE_CERT_SHA256` and in the release notes; every build is checked against it.

## 2. Signed build on the owner's machine

Either copy `keystore.properties.example` to `keystore.properties` (ignored by Git and refused
by `scripts/checkpoint.sh`) and fill it in, or export the variables for one shell session:

```bash
export YFT_RELEASE_STORE_FILE=/secure/path/yft-release.jks
export YFT_RELEASE_KEY_ALIAS=yft-release
read -rs YFT_RELEASE_STORE_PASSWORD && export YFT_RELEASE_STORE_PASSWORD
read -rs YFT_RELEASE_KEY_PASSWORD && export YFT_RELEASE_KEY_PASSWORD
bash scripts/release-prep.sh --expected-cert-sha256 '<SHA256 fingerprint>' \
  [--previous-apk path/to/previous-release.apk]
```

Environment variables win over `keystore.properties`, key by key. A partial configuration or a
missing keystore file fails the build; without any configuration a plain `assembleRelease`
produces `app-release-unsigned.apk` and never falls back to the debug key.

`scripts/release-prep.sh`:

1. checks that `CHANGELOG.md` has a `## [<versionName>]` section and that
   `docs/release/<versionName>.md` exists, and warns about uncommitted changes;
2. fails within seconds when signing is missing or partial, then runs `clean`,
   `lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test
   :extractor-sites:test` (skip with `--skip-tests` only right after a green CI run) and
   `:app:assembleRelease -Pyft.requireReleaseSigning=true` as separate Gradle invocations (so a
   4 GiB machine copes) with `--no-build-cache`, so tests really run and nothing is reused;
3. runs `scripts/verify-release-apk.sh`: package `com.alal.yft`, versionName, not debuggable,
   zip alignment, `apksigner verify` with v2/v3, exactly one signer, not the Android debug
   certificate, the expected certificate fingerprint and, with `--previous-apk`, the upgrade
   rules (same package, same signer, higher versionCode);
4. stages `dist/<versionName>/` with `video-downloader-<versionName>.apk`, `SHA256SUMS`,
   `release-info.txt` and `release-notes.md` (the notes plus checksum, certificate fingerprint
   and source commit).

## 3. Signed build and draft release on GitHub Actions

`.github/workflows/release-draft.yml` runs on a pushed `v*` tag or manually
("Run workflow" appears once the file is on the default branch). It needs:

| Kind | Name | Value |
| --- | --- | --- |
| Secret | `YFT_RELEASE_KEYSTORE_BASE64` | `base64 -w0 yft-release.jks` output |
| Secret | `YFT_RELEASE_STORE_PASSWORD` | keystore password |
| Secret | `YFT_RELEASE_KEY_ALIAS` | key alias, e.g. `yft-release` |
| Secret | `YFT_RELEASE_KEY_PASSWORD` | key password |
| Variable | `YFT_RELEASE_CERT_SHA256` | the public certificate fingerprint from step 1 |
| Variable | `ALLOW_RELEASE` | leave unset; set to `true` only when publishing is approved |

The job fails without the secrets or the fingerprint variable, so it can never produce an
unsigned or wrongly signed "release". It runs `scripts/release-prep.sh` with the full test
matrix, uploads `dist/release/` as an artifact, deletes the decoded keystore, and creates or
refreshes a **draft** pre-release `v<versionName>` with the APK, `SHA256SUMS` and the notes. It
never edits a release that is already published. Publishing happens only in a manual run with
`publish` ticked **and** `ALLOW_RELEASE=true`. The `release` environment can be given required
reviewers in the repository settings for an extra approval step.

The agent's deploy key can push the version tag, which starts this workflow (it did for
`v1.0.0-beta.2`), but it cannot create, edit or publish releases, approve the `release`
environment or change secrets; those stay with the owner.

## 4. Device checks before publishing

Automated install, launch and upgrade check on one connected device or emulator:

```bash
bash scripts/device-smoke-test.sh --fresh dist/1.0.0-beta.2/video-downloader-1.0.0-beta.2.apk
bash scripts/device-smoke-test.sh --fresh --upgrade-from previous.apk new.apk
```

Then the manual checklist, ideally on Android 7.x, 10 and 14/15:

| Check | Expected |
| --- | --- |
| Fresh install and first launch | Launch screen, then Home; no crash |
| About and Licenses | About shows `Version 1.0.0-beta.2 (2)`; Licenses lists every notice and opens its text |
| Night theme and large text | With the system in dark mode and the largest font size every screen is readable and nothing is cut off |
| TalkBack | Every button and row is announced with its name; the bottom bar reads full tab names |
| Paste a direct MP4 link | Preview opens with real metadata |
| Browse a page with an HTML5 video | Media-found sheet and Detected Media list the source |
| Preview playback | Video and audio play; variants switch |
| Direct, HLS and DASH downloads | Finish, appear in the Library and play in another player |
| Pause, resume, retry; force-stop during a download | Progress continues; recovery after relaunch |
| Wi-Fi only on mobile data | "Waiting for Wi-Fi" banner, resumes on Wi-Fi |
| Android 13+ notification permission | Asked before the first download; progress notification with Pause all once granted |
| Clear browsing data | Sites are signed out; Detected Media is empty |
| Signed upgrade from the previous beta | Settings and downloads survive |

Record results in `docs/TEST_MATRIX.md`; do not publish with an unexplained failure.

## 5. Publish (only with `ALLOW_RELEASE=true`)

1. Review the draft: tag, title, notes, APK name, `SHA256SUMS` and the certificate line.
2. Download the draft APK once more and run `sha256sum -c SHA256SUMS`.
3. Set `ALLOW_RELEASE=true`, run "Release draft" manually with `publish` ticked, then set
   `ALLOW_RELEASE` back to false.
4. Date the `CHANGELOG.md` section, start the next version's section and raise
   `yft.versionCode`.
