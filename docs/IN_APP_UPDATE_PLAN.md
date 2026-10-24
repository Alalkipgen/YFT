# In-app update — plan (U0–U4)

Status: **U0 done — the plan (docs only); no app code** until the owner says go (after his S5 run
3). The owner's request: 2026-10-24, in chat. Branch `work/phase-in-app-update`, made from PR #2's
head `8dc46ff` (`spike/master-extractor-backup`), as A (`work/phase-download-fixes`) and VK
(`work/phase-vk-extractor`). Release: **v1.0.0**, the first stable release, with A and VK — U1 is
its floor; U2 joins only on time (§8), else v1.0.1.

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

- **U1** (v1.0.0 ရဲ့ floor): Alalkipgen/YFT ရဲ့ release တွေထဲက draft မဟုတ်၊ prerelease မဟုတ်၊
  versionCode ပိုမြင့်တဲ့ နောက်ဆုံး release ကို ရှာ → "Update available" + version + **Download**
  (APK link ကို browser မှာ ဖွင့်)။ **U2**: APK ကို app ထဲမှာ download → release ရဲ့ `SHA256SUMS`
  နဲ့ SHA-256 စစ် → signing cert ကို install ထားတဲ့ YFT နဲ့ စစ် → PackageInstaller (Android ရဲ့
  ကိုယ်ပိုင် confirm၊ `REQUEST_INSTALL_PACKAGES`)။ ဘယ်အဆင့် fail ဖြစ်ဖြစ် အကြောင်းပြ + U1 link။ U2 က
  U1 ပေါ်က commit သီးခြား — v1.0.1 ကို သီးသန့် ရွှေ့လို့ရ။
- **SHA256SUMS — စစ်ပြီး၊ workflow မပြင်ရ:** main ရဲ့ `release-draft.yml` က draft ဆောက်ချိန်ရော
  refresh ချိန်ရော APK + `SHA256SUMS` ကို upload လုပ် (`verify-release-apk.sh` က ရေး၊ မရှိရင် step
  fail)။ Publish က draft flag ပဲ ပြောင်း။ လက်နဲ့ လုပ်တဲ့ release မှာပဲ SUMS မပါ → U2 ငြင်း + link။
- **versionCode:** GitHub release မှာ versionCode မပါလို့ app က tag ကနေ တွက်: `vX.Y.Z` → X·1000000 +
  Y·10000 + Z·100။ **v1.0.0 = 1000000** (beta.1 = 1၊ draft beta.2/beta.3 = 2/3 ထက် မြင့်)၊ v1.0.1 =
  1000100။ Prerelease က သူ့ version အောက်က နံပါတ် ၉၉ ခုထဲက (ဥပမာ 1.0.1-beta.1 = 1000001)။ JVM test က
  စစ် (release-prep.sh ကလည်း run)။ ⇒ v1.0.0 ရဲ့ release commit က `1.0.0` / `1000000` (+1 မဟုတ်)။
- **ပိုင်ရှင့် default:** app ဖွင့်ချိန် တစ်ရက် အများဆုံး တစ်ခါ (background job မရှိ)၊ Settings
  switch (default ON) + Settings → Check for updates၊ Home ထိပ်မှာ card သေး + Settings row၊ release
  key နဲ့ release build ပဲ စစ်/ပြ၊ About › Privacy မှာ api.github.com request ကို နာမည်တပ်တဲ့ line
  အသစ်။
- **Preview/debug (test key) = test mode:** switch default OFF၊ ပိုင်ရှင် ဖွင့်မှ/နှိပ်မှ စစ်၊
  prerelease ပါ၊ installed ကို 0 လို့ ယူ → ဒီနေ့ beta.1 ကို "(test)" နဲ့ ပြ။ Release မှာ test mode
  မပါ (build-type source set)။
- **ဖုန်း test (v1.0.0 မတိုင်ခင်၊ test-key preview):** card ပေါ် → Download က beta.1 APK link ကို
  browser မှာ ဖွင့် → Update (U2) က download + SHA-256 OK → package/key မတူလို့ ငြင်း + link
  (မှန်တဲ့ အဖြေ)။ "Reinstall this build (test)" (preview/debug ပဲ) က preview ကိုယ်တိုင်ကို
  PackageInstaller နဲ့ ပြန်တင် → HyperOS ရဲ့ confirm ကို ဖုန်းမှာ တကယ် မြင်ရ။ Android confirm ကို
  emulator ကလည်း စစ်။
- **Release key နဲ့ အမှန် update test (အသက်သာဆုံး):** v1.0.0 publish မလုပ်ခင် release-key draft
  `1.0.0-rc.1` (code 999901၊ publish မလုပ်) ကို ဖုန်းမှာ တင် → v1.0.0 publish → rc.1 က 1.0.0 ကို ပြ
  → Update → confirm → 1.0.0 (settings မပျက်)။ Draft run တစ်ခု + doc line ၂ ကြောင်းပဲ ကုန်။ မလုပ်ရင်
  ပထမ အမှန် test က v1.0.0 → v1.0.1။
- **Tests:** JVM — version compare၊ asset ရွေး၊ prerelease/draft skip၊ checksum၊ signer၊ offline၊
  rate limit၊ တစ်ရက်တစ်ခါ။ Emulator — local HTTPS server၊ PackageInstaller က Android confirm ပြတဲ့
  အထိ၊ ပြီးရင် Cancel။
- **A/VK နဲ့ ထိတဲ့ file:** code file မတူ — A ရဲ့ Home/D4 file (`HomeSite.kt`၊
  `HomeSitesRepository.kt`၊ `HomeScreenTest.kt`) နဲ့ `FileNames.kt` ကို မထိ (`HomeScreen.kt` ကို A
  မပြင်)။ တူတာ `docs/SESSION_STATE.md` (section ကိုယ်စီ) ပဲ။ PR #2 ရဲ့ release commit နဲ့ conflict
  သေးသေး ဖြစ်နိုင်: `SettingsScreen.kt`၊ `AboutScreen.kt` — U4 မှာ ဖြေ။
- **အချိန် (MMT):** U1 ≈ 4–6 h + CI → Oct 25 ညနေ green; U2 ≈ 8–10 h + CI (emulator ပါ) → Oct 26 ညနေ
  green; preview + ပိုင်ရှင် ဖုန်း test Oct 26 ည–Oct 27။ U2 မမီရင် v1.0.0 = U1 ပဲ၊ U2 → v1.0.1။
- **Rules:** ဒီ branch ပဲ၊ main/spike/recipes မထိ၊ PR/label မလုပ်၊ shared docs ကို PR #2 merge
  ပြီးမှ၊ S5 run 3 count မဝင်မချင်း ဖုန်းမှာ APK မတင်၊ release/tag/Release draft run မလုပ်
  (ပိုင်ရှင်ပဲ)။

## 1. Goal and scope

**Goal:** a friend who installed YFT from a GitHub release learns that a newer release is out
without asking the owner, and (U2) installs it from inside YFT with Android's own confirmation —
never silently, never from anywhere but this repository's releases.

**In scope:**
- **U1 (v1.0.0's floor):** the check (§4.3) and "Update available" — the version and **Download**,
  which opens the release's APK link in the browser — on Home and in Settings.
- **U2:** the APK downloaded in YFT, its SHA-256 checked against the release's `SHA256SUMS`, its
  signer against the installed app's, then `PackageInstaller` with Android's confirmation
  (`REQUEST_INSTALL_PACKAGES`). Any failure says why and offers U1's link. U2 is separable: its own
  commits on top of U1, no U1 file depends on it, it reads U1's stored state as is, so it can move
  to v1.0.1 alone.

**Not in scope** (§10): background checks or notifications, silent installs, update ownership, delta
or resumable downloads, release notes in the card, a beta channel for friends, signing-key rotation,
any server but GitHub.

## 2. Hard rules

1. **Branch** `work/phase-in-app-update` only. Never push to `main`, `spike/master-extractor-backup`
   or another work branch; no pull request and no label until the owner says (after PR #2's merge:
   `main` merged in with a merge commit, no rebase — U4); never run `master-recipes-publish`, never
   touch the `recipes` branch.
2. **No app code before the owner's go** (after his S5 run 3). Then U1 and U2 in their own commits
   (`scripts/checkpoint.sh`), each with its tests.
3. **Files:** the new `update` package, its tests and the small edits of §4.7. Any other file — core
   modules, workflows, scripts, `gradle.properties`, Gradle beyond §4.7 — is an owner decision: ask
   in Burmese and stop. No workflow change is needed (§3).
4. **Shared docs** (`MASTER_KEY_SOCIAL_PLAN.md`, `PROGRESS.md`, `CHANGELOG.md`, `README.md`,
   `docs/RELEASE.md`, `docs/PHONE_TEST_NOTES.md`, `docs/TEST_MATRIX.md`, `docs/SUPPORT_MATRIX.md`):
   not before U4; their lines wait in §11.
5. **Main only:** no `extractor-master`, Master, R9, recipes or Backup finder dependency; the Backup
   finder switch does not gate updates (their own switch does).
6. **Security (AGENTS.md):** HTTPS only and the system's CAs only (`network_security_config.xml`
   unchanged); TLS is never disabled or relaxed in app code; no token, cookie or account in any
   request; GitHub's signed download addresses (its redirects) are never logged, stored or reported;
   every check fails closed.
7. **Releases:** never publish a release, create or push a tag, run the Release draft workflow or
   change repository variables or secrets — the owner only.
8. **The phone:** no APK on the owner's phone before he says S5's run 3 is counted; then only with
   his OK.
9. **Quiet release (decision 30 (v)):** test mode and its test action exist only in debug and
   preview builds (build-type source sets); the release carries none of them.
10. **Reports** to the owner: Burmese, short. App text: English.

## 3. What the code and the releases already give

- **Releases today** (the public API, 2026-10-24): only `v1.0.0-beta.1` is published — a
  **prerelease** (versionCode 1) with `video-downloader-1.0.0-beta.1.apk` and `SHA256SUMS`;
  `v1.0.0-beta.2` and `v1.0.0-beta.3` are **drafts** (unseen without sign-in). So a release build
  finds nothing until a newer stable release exists. Friends on beta.1 have no updater: they move to
  v1.0.0 by hand once.
- **`SHA256SUMS` on every release — confirmed; no workflow change.** `release-draft.yml` (the same
  on `main` and on PR #2) runs `scripts/release-prep.sh --out-dir dist/release`, whose
  `scripts/verify-release-apk.sh --out-dir` writes `SHA256SUMS` beside the APK (one line: the APK's
  SHA-256, two spaces, `video-downloader-<versionName>.apk`). The workflow uploads
  `dist/release/*.apk dist/release/SHA256SUMS` both when it creates a draft and when it refreshes
  one (`--clobber`); a missing file fails the step. Publishing only sets `draft` to false; a
  published release is never modified. The tag is `v` + `yft.versionName`; `--prerelease` exactly
  when the name has a `-`. Only a release made by hand would lack `SHA256SUMS`: U2 then refuses and
  offers the link.
- The release notes carry the APK's SHA-256 and the signing certificate's SHA-256 (public;
  repository variable `YFT_RELEASE_CERT_SHA256`):
  `3A:EB:30:64:91:E2:DD:6F:F7:6D:C5:A8:68:E6:FC:C9:D3:30:BB:99:85:BF:4D:15:B3:4A:67:04:EC:78:98:8F`.
- **Build types** (`app/build.gradle.kts`): `debug` (`com.alal.yft.debug`), `release` (R8, the
  owner's key), `preview` (`initWith(release)`, `com.alal.yft.preview`, a **fresh test key per CI
  run** — a preview can update neither another preview nor the release). `gradle.properties`:
  `1.0.0-beta.3` / `3`. minSdk 24, target 35.
- **Code:** one `OkHttpClient` (core-data `NetworkConfiguration`: follows redirects, no cookies);
  one preferences `DataStore` (`settings.preferences_pb`, `DataModule`); `BoundedJsonParser`
  (`extractor-api`, plain Kotlin); Home is `HomeScreen.kt` (`HomeRoute`, a `LazyColumn`: header,
  intro, link, sites, recent); Settings is `SettingsScreen.kt` (groups Appearance, Downloads,
  Browser, Privacy, About; private `SettingsGroup`, `SettingsRow`, `ValueRow`, `SwitchRow`); About's
  `PRIVACY` list (9 lines); `MainActivity` (Hilt; `storageJanitor.launchOnce()`). The manifest has
  `INTERNET`, no `REQUEST_INSTALL_PACKAGES` and no web-link intent filter. `okhttp-mockwebserver`
  and `okhttp-tls` are JVM test dependencies already; no instrumented test serves HTTP today (the
  network rules forbid cleartext and user CAs).
- `scripts/release-prep.sh` runs `lintDebug testDebugUnitTest …` before the signed build, so a JVM
  test guards the version code rule at release time as well as on CI.

## 4. Design

### 4.1 Which builds check

| Build | When it asks GitHub | Switch default | What it offers |
|---|---|---|---|
| release with the release key (the installed app's signer = the release certificate, checked at run time) | on app open, at most once a day, and on tap | on | non-draft, non-prerelease releases with a higher versionCode |
| release with another key (CI's, a fork's) | never; no Updates rows | — | — |
| debug and preview — **test mode** | only on tap, or on open once the owner turned the switch on | off | prereleases too; the installed versionCode counted as 0 (beta.1 today), shown with "(test)" |

- Test mode is one constant in a build-type source set (`UpdateBuild.TEST_MODE`: false in
  `src/release`, true in `src/debug` and `src/preview`), so the release has no test code (decision
  30 (v)). Test mode also has "Reinstall this build (test)" (U2, §4.4 step 8).

### 4.2 The version code rule (v1.0.0 = 1000000)

- GitHub's release data has no versionCode, so YFT computes it from the tag — no second request:
  stable `vX.Y.Z` → **X·1000000 + Y·10000 + Z·100** (Y, Z ≤ 99). **v1.0.0 = 1000000** — above beta.1
  (1), the beta.2/beta.3 drafts (2, 3) and any beta.4; v1.0.1 = 1000100, v1.1.0 = 1010000.
- A prerelease takes a code below its version's, preferably among the 99 just below it
  (`1.0.1-beta.1` = 1000001, `1.0.0-rc.1` = 999901), so a test build fits between two releases.
  Outside test mode a prerelease is never offered.
- Guard: `VersionCodeRuleTest` (JVM) fails when `yft.versionName` is stable and `yft.versionCode` is
  not the rule's, or a prerelease's code is not below its version's; CI and `release-prep.sh` run
  it. The release commit sets `1.0.0` / `1000000` — the owner's (or the release pull request's), not
  this branch's. `docs/RELEASE.md`'s "raise by one" changes at U4 (§11).
- U2 also reads the downloaded APK's own versionCode (§4.4).

### 4.3 U1 — the check and "Update available"

- **Request:** `GET https://api.github.com/repos/Alalkipgen/YFT/releases?per_page=10` with
  `Accept: application/vnd.github+json` and `X-GitHub-Api-Version: 2022-11-28`; no `Authorization`,
  no cookie, no account. A client derived from the shared one: connect 15 s, the whole call 30 s, no
  HTTPS → HTTP redirect, only the hosts of §4.6. The body is read up to 512 KB and parsed with
  `BoundedJsonParser`.
- **The candidate:** `draft` false (unauthenticated lists never show drafts; checked anyway);
  `prerelease` false (allowed in test mode); the tag gives the code (§4.2; a tag that does not parse
  is skipped); the code above the installed `BuildConfig.VERSION_CODE` (0 in test mode); its APK is
  the asset named exactly `video-downloader-<version>.apk`, state `uploaded`, its
  `browser_download_url` under `https://github.com/Alalkipgen/YFT/releases/download/`. The highest
  code wins (the newest `published_at` in test mode). A release without its APK is skipped; the
  address of its `SHA256SUMS` asset is kept for U2.
- **When:** `MainActivity.onStart` calls `UpdateController.onAppOpened()`. It asks GitHub when the
  policy allows it (§4.1), the switch is on, no check is running, no rate limit is pending and
  GitHub's last answer is 24 h old or more (or dated in the future: the clock went back). No
  background job, no WorkManager, no notification. Settings' **Check for updates** asks at once;
  only a pending rate limit stops it, and its line says until when.
- **Results:** `UpToDate`; `Available(version, code, apkUrl, apkSize, sumsUrl)`; `Offline` (no
  connection or a timeout: not counted, so the next open tries again); `RateLimited(until)` (403 or
  429 with `x-ratelimit-remaining: 0` and `x-ratelimit-reset`, or `retry-after`; at most 24 h ahead;
  no request before it); `Failed` (any other answer, bad or oversize JSON). Every answer from GitHub
  starts the next 24 h, so on its own YFT asks at most once a day.
- **Kept** (`UpdateStore`: the app's preferences DataStore, keys `update_*`, no core-data change):
  the switch, the time of GitHub's last answer, the found update (the card shows again without a
  request), the rate limit's end and the version hidden by **Not now**. The found update is dropped
  once the installed versionCode reaches it.
- **The action (U1):** **Download** → `ACTION_VIEW` on the APK's `browser_download_url`: the user's
  browser downloads it and Android's installer takes over from there. YFT has no web-link intent
  filter, so this never opens YFT's own browser. No browser → "No app can open this link."

### 4.4 U2 — download, checks, Android's installer

The card's and the row's button becomes **Update**; every failure shows its reason and **Download in
browser** (U1's link). Steps, in order, all in `UpdateController`'s scope (it outlives the screens;
Cancel stops it):

1. **Allowed to install?** `canRequestPackageInstalls()` (API 26+) false → "Allow YFT to install
   updates, then tap Update again." with **Open settings** (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`,
   `package:` URI). Nothing is downloaded yet. On API 24–25 Android's installer asks by itself.
2. **Space:** free space ≥ twice the asset's size + 20 MB, else "Not enough free space for the
   update."
3. **`SHA256SUMS`:** GET the asset (≤ 16 KB); take the line for exactly
   `video-downloader-<version>.apk` (two spaces or ` *`, CRLF, either hex case; two different lines
   → refuse). No file or no line → "This release has no checksum for its APK, so YFT can't check
   it."
4. **Download:** `browser_download_url` → GitHub's redirect (HTTPS, §4.6's hosts) into
   `cacheDir/updates/yft-update.apk` (one fixed name: no download queue, no MediaStore, no
   `FileNames`); SHA-256 computed while writing; a % line and **Cancel**; the size must equal the
   asset's `size` (≤ 100 MB). A different hash or size → the file deleted, "The download was damaged
   (its SHA-256 doesn't match). Nothing was installed."
5. **The APK itself:** `getPackageArchiveInfo` with its signing certificates (`GET_SIGNATURES` as
   well on API 24–28): the package is this app's; the versionCode is the tag's (stable tags) and
   above the installed one (test mode: not below); the SHA-256 set of its current signers equals the
   installed app's. A mismatch, or nothing read → refused: "This file isn't an update for this YFT."
   / "This update isn't signed like your YFT, so it wasn't installed."
6. **Install:** a `PackageInstaller` session (`MODE_FULL_INSTALL`, `setAppPackageName`, `setSize`,
   `setInstallReason(USER)` on 26+, `setRequireUserAction(USER_ACTION_REQUIRED)` on 31+), the
   verified file written into it, committed with a mutable `PendingIntent` to the unexported
   `InstallStatusReceiver`. `STATUS_PENDING_USER_ACTION` → its confirmation intent goes to
   `UpdateController`; the visible activity starts it (Android's own screen; never from the
   background — if YFT is not in front, at its next resume). `STATUS_SUCCESS` → Android replaces
   YFT; settings and downloads stay. `STATUS_FAILURE_ABORTED` → "Update cancelled."; any other
   status → "Android couldn't install the update (<status>)."; each with the link.
7. **Cleanup:** the cached APK is deleted when its session ends; at the next start, a left-over file
   and YFT's own stale sessions (`mySessions`) are removed.
8. **Test mode only — "Reinstall this build (test)":** this build's own `base.apk` goes through
   steps 5–6 (same package, signer and code), so the owner's phone shows HyperOS's real confirmation
   before any release-key update exists. Never in the release.

The manifest changes only in U2: `REQUEST_INSTALL_PACKAGES` and the receiver. U1 changes no manifest
line.

### 4.5 Home, Settings, About

- **Home:** `HomeScreen(…, updateCard: @Composable () -> Unit = {})` and one `item(key = "update")`
  right under the header; `HomeRoute` passes the card (its own `UpdateViewModel`). No
  `HomeViewModel` or `HomeUiState` change. The card: "Update available", "YFT 1.0.1", **Download**
  (U1) or **Update** (U2), **Not now**; U2's % and failure lines in it.
- **Settings:** a group **Updates** before About through a slot like Home's (no `SettingsViewModel`
  change); `SettingsGroup`, `SettingsRow`, `ValueRow` and `SwitchRow` go from private to internal
  (same look). Rows: "Update available" / "YFT 1.0.1" (only when found; the card's action); **Check
  for updates** (its line: "Last checked today, 21:35" / "Checking…" / "YFT is up to date." / "No
  connection." / "GitHub is busy. Try again after 22:10."); the switch **Check for updates when YFT
  opens** ("At most once a day"), on by default (off in test mode).
- **About › Privacy:** one new last line (U1): "With Check for updates on, YFT asks GitHub
  (api.github.com) about its newest release at most once a day, when you open it. Nothing about you
  or your downloads is sent." U2 adds: "Update downloads the new version from GitHub." PR #2's
  release commit rewrites the list's first line (GitHub raw); this line is apart from it, and the
  two are aligned at U4.

### 4.6 Hosts and privacy

- Hosts: `api.github.com` (the check), `github.com` (the asset links) and GitHub's download hosts
  behind its redirect (`objects.githubusercontent.com`, `release-assets.githubusercontent.com`). Any
  other host, or `http:`, is refused. Nothing else is asked.
- GitHub allows 60 unauthenticated requests an hour per IP address; phones behind one carrier
  address share it — hence §4.3's rate limit handling and no retries.
- Logs: the step, the host and the status only — never a redirect address or a body.

### 4.7 Files

- **New (U1):** `app/src/main/java/com/alal/yft/update/` — `ReleaseVersion.kt` (tag ↔ code),
  `GitHubReleases.kt` (parse, candidate), `UpdateChecker.kt` (request, results), `UpdateStore.kt`,
  `UpdateController.kt` (gate, state), `UpdatePolicy.kt` (the release certificate, which builds
  check), `UpdateViewModel.kt`, `UpdateCard.kt`, `UpdateSettingsRows.kt`, `UpdateModule.kt` (Hilt);
  `app/src/{release,debug,preview}/java/com/alal/yft/update/UpdateBuild.kt`.
- **New (U2):** `Sha256Sums.kt`, `UpdateDownloader.kt`, `ApkCheck.kt`, `UpdateInstaller.kt`,
  `InstallStatusReceiver.kt`.
- **Edited (small):** `HomeScreen.kt` (slot, item), `SettingsScreen.kt` (slot, group, four `private`
  → `internal`), `AboutScreen.kt` (one `PRIVACY` line), `MainActivity.kt` (inject; `onAppOpened()`;
  U2: start the confirmation intent), `AndroidManifest.xml` (U2: the permission, the receiver),
  `app/build.gradle.kts` (U2: `androidTestImplementation` of `okhttp-mockwebserver` and
  `okhttp-tls`, already in the version catalog), this plan, this agent's `docs/SESSION_STATE.md`
  section.
- **Tests:** §6 (new files only).

## 5. Steps

### 5.1 U0 — the plan (done, 2026-10-24)

This file and the SESSION_STATE section, docs only (no CI run). STOP: the owner's go after his S5
run 3.

### 5.2 U1 — check and link

1. §4.7's U1 files, the JVM and Compose tests (§6.1).
2. CI's set (§7) green on the branch; checkpoint `checkpoint(phase-in-app-update): U1: …`. Its SHA
   is the **U1 cut**.
3. Short Burmese report; U2 starts at once unless the owner says stop (the time box, §8).

### 5.3 U2 — in-app install

1. §4.7's U2 files on top of U1 (their own commits), the JVM tests and the emulator test (§6.2).
2. CI green, the Emulator smoke included.
3. STOP: short Burmese report.

### 5.4 U3 — preview and the owner's phone (after S5's run 3 is counted, with his OK)

- The branch's CI preview APK is `com.alal.yft.preview` with its own test key: Android installs it
  only after the current `com.alal.yft.preview` is removed, so the owner picks the moment (after his
  #12 checks), or U3 runs on the joint preview after PR #2's merge (U4).
- Checklist (~30 min): (1) Settings → Updates: the switch off (test mode); Check for updates → "YFT
  1.0.0-beta.1 (test)" on Home and in Settings. (2) Download opens beta.1's APK link in the browser
  (not installed). (3) Update: downloaded, SHA-256 OK, then refused — beta.1 is `com.alal.yft` with
  the release key, the preview a test key — with the reason and Download in browser: the expected
  answer. (4) Reinstall this build (test): HyperOS's confirmation; Install → YFT restarts, settings
  kept; Cancel → "Update cancelled." with the link. (5) Airplane mode → "No connection." (6) Not now
  hides Home's card, Settings keeps the row; with the switch on, one check a day (the line's time).
- **The cheapest real release-key update test:** before v1.0.0 is published, the owner's release-key
  draft `1.0.0-rc.1` (versionCode 999901; his Release draft run on the release commit with the name
  changed, `CHANGELOG.md` `## [1.0.0-rc.1]` and `docs/release/1.0.0-rc.1.md`; never published,
  deleted after) installed on his phone → he publishes v1.0.0 → the rc's Check for updates offers
  1.0.0 → Update → Android's confirmation → 1.0.0 installed, settings kept. Cost: one draft run and
  two doc lines; it can be P8's upgrade check. Without it, the first real update is v1.0.0 → v1.0.1
  (free, but after the friends have v1.0.0; the link stays the fallback).

### 5.5 U4 — after PR #2's merge

1. Merge `main` into `work/phase-in-app-update` (a merge commit, no rebase); resolve §9's expected
   small conflicts keeping both sides; align the two privacy lines.
2. CI green; the joint preview (A, VK, U) or U's own; the owner's checks (U3 if not done).
3. The shared docs from §11; the pull request to `main` when the owner says (he merges). If U2 is
   late (§8), v1.0.0's pull request comes from `work/phase-in-app-update-u1` (the U1 cut) and U2
   waits for v1.0.1.

## 6. Tests

### 6.1 JVM (`testDebugUnitTest`; no live network)

- `ReleaseVersionTest`: tags → codes (`v1.0.0` = 1000000, `v1.2.3` = 1020300); bad tags (no `v`, a
  part above 99, letters, extra parts); prereleases; compare.
- `VersionCodeRuleTest`: `BuildConfig`'s name (without `-debug`) and code follow §4.2.
- `GitHubReleasesTest` (a trimmed releases JSON fixture and variants): drafts skipped; prereleases
  skipped, taken in test mode; an equal or lower code not offered; the highest of several; the APK
  by its exact name (another APK, or state not `uploaded`, ignored); no APK → skipped; no
  `SHA256SUMS` → offered with no checksum (U2 refuses); a link off GitHub or `http:` → skipped;
  malformed and oversize JSON → `Failed`.
- `UpdateCheckerTest` (MockWebServer, a fake clock): headers (Accept, API version; no Authorization,
  no Cookie); 200 → `Available` or `UpToDate`; offline (no server, `DISCONNECT_AT_START`) and a
  timeout → `Offline`, not counted; 403 with `x-ratelimit-remaining: 0` and a reset →
  `RateLimited(until)` and no request before it, not even on tap; 429 with `retry-after`; 403
  without rate headers and 5xx → `Failed`; once a day (23 h → no request, 24 h → one, the clock back
  → one); the switch off → no request; the policy off (another signer) → no request and no rows.
- `UpdateControllerTest`: the card's and the rows' states; Not now per version; the found update
  dropped after an update; one check at a time.
- U2: `Sha256SumsTest` (formats, no line, two lines, bad hex); `UpdateDownloaderTest`
  (MockWebServer: OK; another hash → file deleted; short and long bodies; a redirect to an allowed
  host OK, to `http:` or another host refused; over the cap; Cancel deletes); `ApkCheckTest`
  (package, code, signer sets — equal, different, empty, other order — on fakes);
  `InstallStatusTest` (each `PackageInstaller` status → its message and the link).
- Compose (Robolectric), new files: `HomeUpdateCardTest` (the card under the header with the version
  and the button; Download starts `ACTION_VIEW` with the exact link; Not now; no card without an
  update); `SettingsUpdateRowsTest` (rows, switch, status lines; no rows when the policy is off).
  A's `HomeScreenTest.kt` is not edited (the new parameter has a default).

### 6.2 Emulator (`connectedDebugAndroidTest`, the Emulator smoke, API 34)

- `UpdateInstallInstrumentedTest`: a local **HTTPS** MockWebServer (a test-only certificate from
  `okhttp-tls`; only the test's own client trusts it, the app's network rules stay as they are)
  serves the releases JSON (the tag from the debug build's own version), `SHA256SUMS` and the app's
  own `base.apk`; the debug build is in test mode with the switch off (the app itself asks GitHub
  nothing), `MainActivity` in front:
  1. check → found → download → SHA-256 and signer (the real PackageManager) OK → session →
     `STATUS_PENDING_USER_ACTION` → Android's installer window on screen
     (`com.android.packageinstaller` or `com.google.android.packageinstaller`) → **Cancel** (never
     Install: it would replace the app under test) → "Update cancelled." with the link; the cached
     APK gone. `appops set … REQUEST_INSTALL_PACKAGES allow` first.
  2. one byte changed in the served APK → refused before any session (`mySessions` empty), the file
     deleted.
  3. installs from YFT not allowed (`appops … deny`) → the "Allow YFT…" state; nothing downloaded.
  4. Reinstall this build (test) → the same confirmation → Cancel.
- If TLS on the emulator proves flaky: P34's in-test OkHttp interceptor as the server, the same
  assertions.

## 7. Validation

- U work: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.alal.yft.update.*'`.
- Before a push, CI's set: `./gradlew --no-daemon lintDebug testDebugUnitTest :core-model:test
  :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug
  :app:assembleRelease`; U2 also the Emulator smoke (`connectedDebugAndroidTest`, CI only).
- The sandbox has no Android SDK yet; JDK 17 and the SDK (platform 35) can be fetched there for the
  JVM runs, to save CI rounds. CI stays the proof.

## 8. Release placement and time

- The owner's time box: U1 + U2 CI green by **2026-10-26 (MMT)**, his phone test by **2026-10-27**;
  else v1.0.0 ships U1 only and U2 moves to v1.0.1 (from the U1 cut; nothing else changes).
- Estimates (agent work + CI; the go assumed on 2026-10-25 morning MMT, after S5's run 3):

| Step | Work | CI | Green (MMT) |
|---|---|---|---|
| U1 | 4–6 h | 1–2 rounds (~40 min each) | 2026-10-25 evening |
| U2 | 8–10 h | 2–3 rounds, the emulator included | 2026-10-26 evening |
| U3 | the preview ~20 min; the owner's checks 30–45 min | — | 2026-10-26 night or 10-27 |
| U4 | 1–2 h after PR #2's merge | 1 round | after PR #2's merge |

- Risk: the emulator's installer window (+½ day) still fits Oct 26, narrowly. Not green by
  2026-10-26 24:00 MMT → a report, and v1.0.0 = U1.
- v1.0.0's version line (`1.0.0` / `1000000`) belongs to the release commit, not to this branch.

## 9. Files shared with A, VK and PR #2

- **A** (`work/phase-download-fixes`): no code file in common. A's Home/D4 files
  (`core-model/.../settings/HomeSite.kt`, `core-data/.../HomeSitesRepository.kt`, their tests,
  `app/src/test/.../feature/home/HomeScreenTest.kt`) and `core-model/.../download/FileNames.kt` are
  not touched by U. U edits `HomeScreen.kt`, which A does not change (the new parameter has a
  default, so A's test compiles as it is); U2 saves under one fixed cache name, without `FileNames`.
- **VK** (`work/phase-vk-extractor`): none — U uses core-data's `OkHttpClient`, not
  `OkHttpExtractorClient`; `SiteAdapterModule.kt` untouched.
- **All three:** `docs/SESSION_STATE.md` — each appends its own section at the end; a merge keeps
  all of them.
- **PR #2's release commit** (expected small conflicts at U4): `SettingsScreen.kt` (the Backup
  finder switch), `AboutScreen.kt` (`PRIVACY`'s first line; U's line is the last),
  `app/build.gradle.kts` (flags; U2 adds two dependency lines). U never edits `gradle.properties`.

## 10. Backlog (after v1.0.x)

- Signing-key rotation: U2 compares the current signers strictly and refuses a rotated key (the link
  remains).
- Release notes in the card; a beta channel for friends who opt in; resuming a broken download; a
  mirror if GitHub is unreachable; update ownership (Android 14) — not wanted while Android's
  confirmation comes every time.
- If YFT ever goes to Google Play, U2 goes (Play does not allow self-updates).

## 11. Lines for the shared docs (written at U4)

- `docs/RELEASE.md`: the version code rule (§4.2) instead of "raise by one"; every release from the
  Release draft workflow (its `SHA256SUMS`); the update test (rc → stable).
- `CHANGELOG.md` (1.0.0) and `docs/release/1.0.0.md`: "YFT tells you when a new version is out
  (Settings → Updates)" — with U2: "and installs it with Android's confirmation"; the privacy line.
- `PROGRESS.md`; `docs/TEST_MATRIX.md` (one row per step: commits, results);
  `docs/PHONE_TEST_NOTES.md` (the owner's U3); `README.md`'s privacy lines, if they list hosts.

## 12. Status

- 2026-10-24: the owner's request: the in-app update in v1.0.0 (the first stable release) with A and
  VK; U0 only. Plan recorded (docs only); branch made from `8dc46ff`. `SHA256SUMS` confirmed on
  every workflow release (§3): no workflow change. Next: STOP — the owner's go after his S5 run 3.

## Appendix — the agent's prompt

```text
You are the in-app update agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: work/phase-in-app-update (it exists; made from PR #2's head 8dc46ff). Work only there.

Read first, in this order:
1. AGENTS.md — the repository's rules (checkpoints with scripts/checkpoint.sh).
2. docs/IN_APP_UPDATE_PLAN.md — your plan. Follow it step by step (U1 → U4). Its §2 hard rules
   come first.
3. app/src/main/java/com/alal/yft/feature/home/HomeScreen.kt, feature/settings/SettingsScreen.kt,
   feature/about/AboutScreen.kt and MainActivity.kt.

Start U1 only after the owner's go (after his S5 run 3). Stop at every STOP and report to the
owner in Burmese, short. App text in English.

Never: push to main, spike/master-extractor-backup or another work branch; open a pull request
or add a label before the owner says; run master-recipes-publish or touch the recipes branch;
publish a release, push a tag or run the Release draft workflow; change a workflow, a script or
gradle.properties; relax TLS or the network rules; send a token or a cookie; log or commit
GitHub's signed download addresses; install or send an APK for the owner's phone before he says
S5's run 3 is counted.
```
