# Release process

This is the owner's path from a work branch to a signed GitHub release. The permanent release
key is in the repository secrets: the workflow below signed `1.0.0-beta.1`, which the owner
published as a pre-release on 2026-10-02, and `1.0.0-beta.2`. **`1.0.0`** (versionCode `1000000`)
is the first stable release, for the owner's friends (decision 30, `MASTER_KEY_SOCIAL_PLAN.md`
§8): its release commit is on `work/phase-release-v1`, its notes `docs/release/1.0.0.md`, its proof
the #12 phone test of its two preview APKs (below) before PR #2's merge. The pipeline's
verification results are in the Phase 7 table of `docs/TEST_MATRIX.md`.

## Release identity

| Item | Value | Notes |
| --- | --- | --- |
| Display name | Video Downloader | `app_name` |
| Application ID | `com.alal.yft` | Permanent once an APK is published; the owner must reconfirm it first (`docs/PROJECT_CONTEXT.md`) |
| Version | `yft.versionName` / `yft.versionCode` in `gradle.properties` | `1.0.1` / `1000100` |
| Git tag | `v<versionName>` | The release workflow refuses a tag that does not match |
| Android | minSdk 24 (7.0), targetSdk/compileSdk 35 (15) | |
| Signature schemes | APK Signature Scheme v2 + v3 | v1 is unnecessary for minSdk 24; v3 allows key rotation later |
| Debug builds | `com.alal.yft.debug`, versionName suffix `-debug` | Install next to the release app, never over it |
| Preview builds (P7) | `com.alal.yft.preview`, versionName suffix `-preview.<run>`, test key | The owner's phone test (#12 normal); see below |
| Trial builds (#12 trial) | `com.alal.yft.trial`, versionName suffix `-trial.<run>`, test key | The release with the trials on, beside #12 normal; see below |

`yft.versionCode` follows the version code rule (`docs/IN_APP_UPDATE_PLAN.md` §4.2), which the
in-app update computes from a release's tag: a stable X.Y.Z is X·1000000 + Y·10000 + Z·100
(1.0.0 = 1000000, 1.0.1 = 1000100, 1.1.0 = 1010000); a prerelease takes a code just below its
version's (1.0.1-beta.1 = 1000001, 1.0.0-rc.1 = 999901). `VersionCodeRuleTest` fails a stable
version whose code is not the rule's and a prerelease whose code is not below its version's (CI
and `release-prep.sh` run it). Android refuses a lower versionCode and refuses any update signed
by a different key.

## Preview builds for the owner's phone test (P7)

`yft-preview-apk` is the release build — minified, not debuggable, without debug actions — under
its own application ID, so it installs next to the published release and never over it. It is
signed with a throwaway **test key**, never with the release key. The same run also builds
`yft-preview-trial-apk` (decision 30 (iii), the #12 trial: `previewTrial`, "YFT Trial",
`com.alal.yft.trial`): the same release build with the trials on — main still asks, its rows are
set aside and compared, so Master answers every YouTube and social lookup on the shrunk build —
and every detail shown. Both come from one commit and install side by side (#12 normal and #12
trial); the trials and the details never reach the release.

| Item | Value |
| --- | --- |
| Display name | YFT Preview (`app/src/preview/res/values/strings.xml`); the trial: YFT Trial |
| Application ID | `com.alal.yft.preview`; the trial: `com.alal.yft.trial` |
| Version | `<yft.versionName>-preview.<CI run number>`, e.g. `1.0.0-preview.102`; the trial `<yft.versionName>-trial.<CI run number>`; versionCode as in `gradle.properties` |
| Signature | v2 + v3 with a fresh RSA 3072 test key made inside the job (`CN=YFT Preview Test Key`) and deleted at its end; the workflow reads no repository secret |
| Workflow | `.github/workflows/preview-apk.yml`: on pushes to `work/phase-*` that change the app, or by hand (**Run workflow**) |
| Checks | `scripts/verify-release-apk.sh --package com.alal.yft.preview` (and `--package com.alal.yft.trial`) `--expected-version … --expected-cert-sha256 <test key>`: package, version, not debuggable, zip-aligned, no debug crash action, exactly one v2/v3 signer equal to the job's test key |
| Artifacts | `yft-preview-apk` (#12 normal) and `yft-preview-trial-apk` (#12 trial), one run, one commit: each `video-downloader-<version>.apk`, `SHA256SUMS`, `release-info.txt` (30 days); the run summary shows both `release-info.txt` |
| Run check | `.github/workflows/minified-check.yml` (pushes to `work/phase-release-*`): the same two shrunk builds on the API 34 emulator, driven through their screens (YouTube's route B, one social ladder, the capture); its APKs are never uploaded |

Install: download the artifact from the workflow run, unzip it, check the APK with
`sha256sum -c SHA256SUMS`, open it on the phone and allow installing unknown apps. Each run
makes its own key, so Android refuses to install a newer preview over an older one: uninstall
YFT Preview (or YFT Trial) first (its downloads and settings go with it). The release app is not
touched, and the two previews never replace each other.

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
unsigned or wrongly signed "release". Its only input is `publish`; the version, the tag
(`v<versionName>`), the title ("Video Downloader <versionName>"), the prerelease flag (a name
with `-`) and the notes (`docs/release/<versionName>.md`, which `release-prep.sh` needs with a
`CHANGELOG.md` `## [<versionName>]` section) come from the run's commit. Every release is made by
this workflow, never by an APK built or uploaded by hand: the in-app update checks the APK against
the release's `SHA256SUMS` before it installs it (U2). It runs `scripts/release-prep.sh` with the full test
matrix, uploads `dist/release/` as an artifact, deletes the decoded keystore, and creates or
refreshes a **draft** pre-release `v<versionName>` with the APK, `SHA256SUMS` and the notes. It
never edits a release that is already published. Publishing happens only in a manual run with
`publish` ticked **and** `ALLOW_RELEASE=true`. The `release` environment can be given required
reviewers in the repository settings for an extra approval step.

The agent's deploy key can push the version tag, which starts this workflow (it did for
`v1.0.0-beta.2`), but it cannot create, edit or publish releases, approve the `release`
environment or change secrets; those stay with the owner.

The repository also holds the secret `YFT_RECIPE_KEY` (the social plan's S4, 2026-10-23): the
private key that signs Master's recipe config, read only by
`.github/workflows/master-recipes-publish.yml`. It is not a release secret: `release-draft.yml`
never reads it. A release build carries only the matching public key
(`extractor-master/recipes/master-recipes.pub.hex`, committed), with which it checks a config's
signature before using it (R9); never the private key.

## 4. Device checks before publishing

Automated install, launch and upgrade check on one connected device or emulator:

```bash
bash scripts/device-smoke-test.sh --fresh dist/1.0.0/video-downloader-1.0.0.apk
bash scripts/device-smoke-test.sh --fresh --upgrade-from previous.apk new.apk
```

Then the manual checklist, ideally on Android 7.x, 10 and 14/15:

| Check | Expected |
| --- | --- |
| Fresh install and first launch | Launch screen, then Home; no crash |
| About and Licenses | About shows `Version 1.0.0 (1000000)`; Licenses lists every notice and opens its text |
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
| Update from 1.0.0-rc.1 (P8, below) | The rc offers v1.0.0, "Verified: checksum and signature match", Android's confirmation, `Version 1.0.0 (1000000)`; settings and downloads survive |

Record results in `docs/TEST_MATRIX.md`; do not publish with an unexplained failure.

## 5. Publish (only with `ALLOW_RELEASE=true`)

1. Review the draft: tag, title, notes, APK name, `SHA256SUMS` and the certificate line.
2. Download the draft APK once more and run `sha256sum -c SHA256SUMS`.
3. Set `ALLOW_RELEASE=true`, run "Release draft" manually with `publish` ticked, then set
   `ALLOW_RELEASE` back to false.
4. Date the `CHANGELOG.md` section, start the next version's section and set `yft.versionCode`
   by the version code rule (Release identity above).

### 1.0.0: the release-key update test (1.0.0-rc.1, P8)

The owner decided on 2026-10-25 to run the update test of `docs/IN_APP_UPDATE_PLAN.md` §5.5 in P8.
Release draft reads the version from the run's `gradle.properties`, so the rc needs a commit of its
own; `CHANGELOG.md` `## [1.0.0-rc.1]` and `docs/release/1.0.0-rc.1.md` are already in the release
commit.

1. A branch from the release commit (after PR #2's merge, from `main`) whose only change is
   `yft.versionName=1.0.0-rc.1` and `yft.versionCode=999901` in `gradle.properties`.
2. Release draft on that branch with `publish` unticked, or the tag `v1.0.0-rc.1` pushed on its
   commit: a draft prerelease `v1.0.0-rc.1` ("Video Downloader 1.0.0-rc.1") with
   `video-downloader-1.0.0-rc.1.apk` and `SHA256SUMS`, artifact `yft-release-1.0.0-rc.1`. Never
   published.
3. The owner installs the rc on his phone (its notes: Install).
4. Release draft on `main` (`1.0.0` / `1000000`) with `publish` unticked, or the tag `v1.0.0`:
   the draft `v1.0.0` ("Video Downloader 1.0.0", not a prerelease) with
   `video-downloader-1.0.0.apk` and `SHA256SUMS`, artifact `yft-release-1.0.0`; steps 1–3 above
   publish it.
5. The rc offers v1.0.0 and installs it (its notes: The update test); the result goes in
   `docs/TEST_MATRIX.md`. Then the `v1.0.0-rc.1` draft (and its tag, if pushed) and the branch
   are deleted.
