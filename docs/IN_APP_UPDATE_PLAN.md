# In-app update — plan (U0–U3)

Status: **U0 done — the plan, revised after the owner's review (2026-10-24); docs only, no app
code** until the owner says go (after his S5 run 3). Then U1 and, without a stop, U2. Branch
`work/phase-in-app-update`, made from PR #2's head `8dc46ff` (`spike/master-extractor-backup`), as A
(`work/phase-download-fixes`) and VK (`work/phase-vk-extractor`). Release: **v1.0.0**, the first
stable release — A, VK and this branch are merged into `spike/master-extractor-backup` in that
order, only when the owner says, and the release commit goes on top (§5.6; no U4). U1 is v1.0.0's
floor; U2 joins on time (§8), else v1.0.1.

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

- **ပိုင်ရှင့် review (2026-10-24) အတိုင်း ပြင်ပြီး:** Home card မရှိ၊ About screen မပြင်၊ dialog
  မရှိ (Snaptube လို၊ ပိုရိုး)။ Settings → About group: **Version** (title မပြောင်း၊ About ကို
  ဖွင့်၊ version ကို ဒုတိယ line ကို ရွှေ့ — "1.0.0-beta.3-debug" စကားလုံးလယ်မှာ မကျိုးတော့) →
  **Updates** (အသစ်) → **Licenses**။
- **Updates row:** update ရှိရင် အနီရောင် **New** + "v1.0.1 available"၊ မရှိရင် "Up to date"။
  ကျွန်တော့် ဖြည့်စွက်ချက်: GitHub ဆီက အဖြေမှန် တစ်ခါမှ မရသေးရင် "Not checked yet" (မသိသေးတာကို
  "Up to date" လို့ မပြောအောင်)။
- **Updates screen (တစ်ခုတည်း):** "YFT 1.0.0" (install ထားတဲ့ version)။ Update ရှိ —
  "New version v1.0.1"၊ release notes (GitHub release body၊ plain text၊ scroll box)၊ **Download APK
  to update** ခလုတ်ကြီး + security line။ မရှိ — "You have the latest version"၊ "Last checked …"၊
  **Check now** (switch off လည်း ရ; "Checking…"၊ "No connection."၊
  "GitHub is busy. Try again after 22:10.")။ အောက်ဆုံး — switch **Check automatically** (default on;
  test mode မှာ off) + "Once a day when YFT opens. Asks GitHub (api.github.com)."
- **ကိုယ်တိုင် ပွင့်:** app ဖွင့်ချိန် daily check က version အသစ် တွေ့ရင် version တစ်ခုကို တစ်ခါပဲ —
  Home ပေါ်မှာ ရှိနေရင် ချက်ချင်း၊ မဟုတ်ရင် နောက်တစ်ခါ app ဖွင့်ချိန်။ Check now နဲ့ အရင်တွေ့တဲ့
  version က ပွင့်ပြီးသားလို့ ယူ။ Back = နောက်မှ; New badge ကျန်။
- **ခလုတ်:** U1 — APK link ကို browser မှာ ဖွင့်; U2 — app ထဲမှာ download (progress၊ Cancel) → စစ် →
  Android installer။ **Security text (မှန်တာပဲ):** download မတိုင်ခင်
  "Signed by YFT · checked before install" (U2 — YFT ရဲ့ စစ်ချက် ၃ ခု; U1 — key မတူတဲ့ update ကို
  Android ကိုယ်တိုင် ငြင်း; U1 က "verified" ဘယ်တော့မှ မပြော)၊ U2 စစ်ချက် အားလုံး အောင်ရင်
  "Verified: checksum and signature match"၊ fail ရင် ဘယ်အဆင့် fail + "Nothing was installed." +
  **Download in browser**။ Reinstall test — "Test: this build's own APK, signature matches" (release
  မဟုတ်လို့ checksum မပြော)။
- **U2 စစ်ချက် အစဉ်:** SHA-256 (`SHA256SUMS`) → signature (install ထားတဲ့ YFT နဲ့) → package/version
  (`getPackageArchiveInfo`: package `com.alal.yft` — test mode မှာ build ကိုယ်ပိုင်; versionCode =
  tag ရဲ့ formula code ဖြစ်ပြီး installed ထက် မြင့်; test mode prerelease — formula code မရှိလို့
  installed ထက် မနိမ့်ရုံ; reinstall test — တူ) → installer။ Tag ကို `v` ပါပါ မပါပါ လက်ခံ။
- **SHA256SUMS — စစ်ပြီး၊ workflow မပြင်ရ** (§3)။ **versionCode:** tag ကနေ `vX.Y.Z` → X·1000000 +
  Y·10000 + Z·100; **v1.0.0 = 1000000**၊ v1.0.1 = 1000100၊ prerelease က သူ့ version အောက်
  (1.0.0-rc.1 = 999901)။ JVM test က စောင့်; release commit က `1.0.0` / `1000000`။
- **Builds:** release key နဲ့ release ပဲ စစ်/ပြ; တခြား key — Updates row မရှိ။ Preview/debug = test
  mode (switch off၊ prerelease ပါ၊ installed = 0၊ "(test)")၊ release မှာ test code မပါ။
- **Merge (ပိုင်ရှင့် အစဉ်၊ ပိုင်ရှင် ပြောမှ):** A → VK → ဒီ branch ကို
  `spike/master-extractor-backup` ထဲ merge commit နဲ့; ပြီးမှ release commit (`1.0.0` / `1000000`၊
  shared docs) ကို အပေါ်မှာ → **U4 မရှိ**၊ main ကို ဒီ branch ထဲ မ merge၊ ဒီ branch က PR မရှိ၊
  Settings/About conflict မရှိ။ မျှော်တဲ့ conflict: `docs/SESSION_STATE.md` ပဲ (section အားလုံး
  ထား)။ U2 နောက်ကျရင် U1 cut ကနေ `work/phase-in-app-update-u1` ကို merge။
- **ဖုန်း test (U3):** U2 green ပြီးတာနဲ့ (Oct 26–27)၊ A နဲ့ VK ရဲ့ preview test တွေ ပြီးနောက်; step
  0 — preview အဟောင်း (`com.alal.yft.preview`) ဖြုတ်။ Checklist §5.4 (~30–45 min)။
- **rc.1 release-key test:** release ချိန်မှာ ပိုင်ရှင် ရွေး (§5.5); မလုပ်ရင် ပထမ အမှန် test က
  v1.0.0 → v1.0.1။
- **About privacy (release commit အတွက်):** About ရဲ့ ပထမ line
  "YFT only contacts the sites you open…" က update check ကြောင့် မမှန်တော့။ ဒီ branch က About
  မပြင်လို့ release commit မှာ update check ကို ထည့်ရေးဖို့ (§11)။
- **Tests:** JVM — version/tag (`v` ပါ/မပါ)၊ asset ရွေး၊ notes၊ offline၊ rate limit၊ တစ်ရက်တစ်ခါ၊
  row/screen state၊ ကိုယ်တိုင်ပွင့်ပုံ၊ U2 စစ်ချက် ၃ ခုနဲ့ အစဉ်၊ security line။ Emulator — GitHub
  အတု (test ထဲက OkHttp interceptor၊ redirect ပါ)၊ Android installer window ပေါ်တဲ့အထိ → Cancel။
- **A/VK နဲ့ ထိတဲ့ file:** code file မတူ (A/VK က Settings၊ nav၊ `YftApp.kt`၊ `MainActivity.kt`၊
  manifest၊ Gradle ကို မထိ; U က Home ကို မထိတော့)။ တူတာ `docs/SESSION_STATE.md` ပဲ။
- **အချိန် (MMT):** U1 ≈ 6–8 h (row/screen/ကိုယ်တိုင်ပွင့်ဖို့ +2 h) + CI → Oct 25 ည green → report
  တို၊ U2 ဆက်; U2 ≈ 8–10 h + CI (emulator ပါ) → Oct 26 ညနေ; ဖုန်း Oct 26–27; merge ~30 min ပိုင်ရှင်
  ပြောမှ။ Oct 26 24:00 မှာ U2 မ green ရင် v1.0.0 = U1 cut၊ U2 → v1.0.1။
- **Rules:** ဒီ branch ပဲ (spike ထဲ merge တစ်ခုပဲ ခြွင်းချက်၊ ပိုင်ရှင် ပြောမှ)၊ main/recipes မထိ၊
  PR/label မလုပ်၊ shared docs မပြင် (§11 က release commit အတွက်)၊ S5 run 3 count မဝင်မချင်း ဖုန်းမှာ
  APK မတင်၊ release/tag/Release draft run မလုပ် (ပိုင်ရှင်ပဲ)။

## 1. Goal and scope

**Goal:** a friend who installed YFT from a GitHub release learns that a newer release is out
without asking the owner — in Settings, and once per new version on a screen that opens by itself —
and (U2) installs it from inside YFT after YFT's checks, with Android's own confirmation: never
silently, never from anywhere but this repository's releases.

**In scope:**
- **U1 (v1.0.0's floor):** the check (§4.3); Settings → About's **Updates** row and the **Updates**
  screen with the release notes (§4.5); the screen opening by itself once per new version;
  **Download APK to update**, which opens the release's APK link in the browser.
- **U2:** the same button downloads the APK in YFT; its SHA-256 is checked against the release's
  `SHA256SUMS`, its signer against the installed app's, its package and versionCode against this app
  and the tag; then `PackageInstaller` with Android's confirmation (`REQUEST_INSTALL_PACKAGES`). Any
  failure says which check failed and offers **Download in browser** (U1's link). U2 is separable:
  its own commits on top of U1, no U1 file depends on it, so it can move to v1.0.1 alone (the U1
  cut, §5.6).

**Not in scope** (§10): a Home card, a dialog or any About change; background checks or
notifications; silent installs; update ownership; delta or resumable downloads; Markdown in the
notes; a beta channel for friends; signing-key rotation; any server but GitHub.

## 2. Hard rules

1. **Branch** `work/phase-in-app-update` only (and `work/phase-in-app-update-u1`, made at the U1 cut
   only when the owner says, §5.6). Never push to `main`, `spike/master-extractor-backup` or another
   work branch — the one exception is this branch's merge into `spike/master-extractor-backup`
   (§5.6), only when the owner says. No pull request and no label; `main` is never merged into this
   branch; never run `master-recipes-publish`, never touch the `recipes` branch.
2. **No app code before the owner's go** (after his S5 run 3). Then U1 and, without a stop, U2, in
   their own commits (`scripts/checkpoint.sh`), each with its tests.
3. **Files:** the new `update` package, its tests and the small edits of §4.7. `HomeScreen.kt` and
   the other Home files and `AboutScreen.kt` are not touched. Any other file — core modules,
   workflows, scripts, `gradle.properties`, Gradle beyond §4.7 — is an owner decision: ask in
   Burmese and stop. No workflow change is needed (§3).
4. **Shared docs** (`MASTER_KEY_SOCIAL_PLAN.md`, `PROGRESS.md`, `CHANGELOG.md`, `README.md`,
   `docs/RELEASE.md`, `docs/PHONE_TEST_NOTES.md`, `docs/TEST_MATRIX.md`, `docs/SUPPORT_MATRIX.md`):
   never edited on this branch; their lines wait in §11 for the release commit.
5. **Main only:** no `extractor-master`, Master, R9, recipes or Backup finder dependency; the Backup
   finder switch does not gate updates (their own switch does).
6. **Security (AGENTS.md):** HTTPS only and the system's CAs only (`network_security_config.xml`
   unchanged); TLS is never disabled or relaxed in app code; no token, cookie or account in any
   request; GitHub's signed download addresses (its redirects) are never logged, stored or reported;
   every check fails closed.
7. **Releases:** never publish a release, create or push a tag, run the Release draft workflow or
   change repository variables or secrets — the owner only.
8. **The phone:** no APK on the owner's phone before he says S5's run 3 is counted; U3 then on his
   schedule (§5.4).
9. **Quiet release (decision 30 (v)):** test mode and its test action exist only in debug and
   preview builds (build-type source sets); the release carries none of them.
10. **True claims only:** every text says only what YFT or Android really checked (§4.5); U1 never
    says "verified".
11. **Reports** to the owner: Burmese, short. App text: English.

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
- **Build types** (`app/build.gradle.kts`): `debug` (`com.alal.yft.debug`, name suffix `-debug`),
  `release` (R8, the owner's key), `preview` (`initWith(release)`, `com.alal.yft.preview`, suffix
  `-preview.<run>`, a **fresh test key per CI run** — a preview can update neither another preview
  nor the release). `gradle.properties`: `1.0.0-beta.3` / `3`. minSdk 24, target 35.
- **Code:** one `OkHttpClient` (core-data `NetworkConfiguration`: follows redirects, no cookies);
  one preferences `DataStore` (`settings.preferences_pb`, `DataModule`); `BoundedJsonParser`
  (`extractor-api`, plain Kotlin). Settings is `SettingsScreen.kt` (`SettingsRoute`; its About
  group: a `ValueRow` "Version" with the version as its trailing `value`, opening About, then
  "Licenses"; `ValueRow` already has a `supporting` line; `SettingsGroup`, `SettingsRow`, `ValueRow`
  and `SwitchRow` are private to the file). The navigation: `YftDestination` (entries that are not
  tabs open full screen) and `YftNavHost` (a `settingsContent(onOpenAbout, onOpenLicenses)` slot;
  `YftNavigationSmokeTest.kt` and `DesignRenderTest.kt` pass their own). `YftApp.kt` holds the
  `navController`; `MainActivity` (Hilt; no `onStart` yet). The manifest has `INTERNET`, no
  `REQUEST_INSTALL_PACKAGES` and no web-link intent filter. `okhttp-mockwebserver` and `okhttp-tls`
  are JVM test dependencies already; no instrumented test serves HTTP today (the network rules
  forbid cleartext and user CAs).
- About's privacy list starts with "YFT only contacts the sites you open and the media servers they
  point to." — untrue once the update check is on. This branch leaves About alone, so the line is
  for the release commit (§11).
- `scripts/release-prep.sh` runs `lintDebug testDebugUnitTest …` before the signed build, so a JVM
  test guards the version code rule at release time as well as on CI.

## 4. Design

### 4.1 Which builds check

| Build | When it asks GitHub | Check automatically | What it offers |
|---|---|---|---|
| release with the release key (the installed app's signer = the release certificate, checked at run time) | on app open, at most once a day; Check now | on | non-draft, non-prerelease releases with a higher versionCode |
| release with another key (CI's, a fork's) | never; no Updates row, no screen | — | — |
| debug and preview — **test mode** | Check now; on open only once the owner turned the switch on | off | prereleases too; the installed versionCode counted as 0 (beta.1 today), shown with "(test)" |

- Test mode is one constant in a build-type source set (`UpdateBuild.TEST_MODE`: false in
  `src/release/kotlin` — not `src/release/java`, which the preview build compiles too — true in
  `src/debug` and `src/preview`), so the release has no test code (decision 30 (v)). Test mode also
  has **Reinstall this build (test)** (U2, §4.4 step 9).

### 4.2 The version code rule (v1.0.0 = 1000000)

- GitHub's release data has no versionCode, so YFT computes it from the tag — no second request:
  stable `vX.Y.Z` → **X·1000000 + Y·10000 + Z·100** (Y, Z ≤ 99). **v1.0.0 = 1000000** — above beta.1
  (1), the beta.2/beta.3 drafts (2, 3) and any beta.4; v1.0.1 = 1000100, v1.1.0 = 1010000.
- **Tags with or without `v`:** `v1.0.1` and `1.0.1` both read as 1.0.1 (the workflow's tags have
  the `v`; one made by hand may not). The APK's name never has it (`video-downloader-1.0.1.apk`);
  the screen always shows "v1.0.1".
- A prerelease takes a code below its version's, preferably among the 99 just below it
  (`1.0.1-beta.1` = 1000001, `1.0.0-rc.1` = 999901), so a test build fits between two releases — a
  convention, not a rule YFT can compute from the tag. Outside test mode a prerelease is never
  offered.
- Guard: `VersionCodeRuleTest` (JVM) fails when `yft.versionName` is stable and `yft.versionCode` is
  not the rule's, or a prerelease's code is not below its version's; CI and `release-prep.sh` run
  it. The release commit sets `1.0.0` / `1000000` — the owner's, not this branch's;
  `docs/RELEASE.md`'s "raise by one" changes there too (§11).
- U2 reads the downloaded APK's own versionCode and compares it with the tag's (§4.4 step 6).

### 4.3 U1 — the check

- **Request:** `GET https://api.github.com/repos/Alalkipgen/YFT/releases?per_page=10` with
  `Accept: application/vnd.github+json` and `X-GitHub-Api-Version: 2022-11-28`; no `Authorization`,
  no cookie, no account. A client derived from the shared one: connect 15 s, the whole call 30 s,
  redirects followed by hand (at most 5; each one HTTPS and on §4.6's host list), no cookie jar, no
  cache. The body is read up to 512 KB and parsed with `BoundedJsonParser`.
- **The candidate:** `draft` false (unauthenticated lists never show drafts; checked anyway);
  `prerelease` false (allowed in test mode); the tag gives the version and, for a stable tag, the
  code (§4.2; a tag that does not parse is skipped); that code above the installed
  `BuildConfig.VERSION_CODE` — in test mode the installed code counts as 0, so every parsed release
  qualifies, prereleases included; its APK is the asset named exactly
  `video-downloader-<version>.apk`, state `uploaded`, its `browser_download_url` under
  `https://github.com/Alalkipgen/YFT/releases/download/`. The highest code wins (the newest
  `published_at` in test mode). A release without its APK is skipped; the address of its
  `SHA256SUMS` asset is kept for U2; its `body` is kept as the notes (at most 20,000 characters, cut
  at a line end).
- **When:** `MainActivity.onStart` calls `UpdateController.onAppOpened()`. It asks GitHub when the
  policy allows it (§4.1), **Check automatically** is on, no check is running, no rate limit is
  pending and GitHub's last answer is 24 h old or more (or dated in the future: the clock went
  back). No background job, no WorkManager, no notification. The screen's **Check now** asks at
  once, with the switch on or off; only a pending rate limit stops it, and its line says until when.
- **Results:** `UpToDate`; `Available(version, code, apkUrl, apkSize, sumsUrl, notes)`; `Offline`
  (no connection or a timeout: not counted, so the next open tries again); `RateLimited(until)` (403
  or 429 with `x-ratelimit-remaining: 0` and `x-ratelimit-reset`, or `retry-after`; at most 24 h
  ahead; no request before it); `Failed` (any other answer, bad or oversize JSON). Every answer from
  GitHub starts the next 24 h, so on its own YFT asks at most once a day.
- **Kept** (`UpdateStore`: an app-private `SharedPreferences` file, `update`, keys `update_*` — the
  settings DataStore is core-data's, so no core-data change; a damaged value reads as unset): the
  switch (unset = the build's default); the time of GitHub's last answer (the 24 h gate); the time
  of the last good answer (`UpToDate` or `Available`: "Last checked", and "Up to date" only after
  one); the found update (the row and the screen show it again without a request); the rate limit's
  end; `update_opened_version` (the last version the screen opened for, §4.5). The found update is
  dropped once the installed versionCode reaches it.
- **The action (U1):** **Download APK to update** → `ACTION_VIEW` (category `BROWSABLE`) on the
  APK's `browser_download_url` — no browser selector, which on a phone with two browsers would ask
  which one every time: the user's browser downloads it and Android's installer takes over from
  there. YFT has no web-link intent filter, so this never opens YFT's own browser. No browser →
  "No app can open this link."

### 4.4 U2 — download, checks, Android's installer

The button stays **Download APK to update**; in U2 it runs these steps in order in
`UpdateInstallFlow` (one for the process, beside `UpdateController`: it outlives the screen;
**Cancel** stops the download, or gives up the session while Android's confirmation waits). Every
failure from step 2 on shows which step failed, "Nothing was installed." and **Download in browser**
(U1's link). The three checks run in the owner's order — checksum, signature, package and version —
and only then the installer:

1. **Allowed to install?** `canRequestPackageInstalls()` (API 26+) false → "Allow YFT to install
   updates, then tap Download APK to update again." with **Open settings**
   (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`, `package:` URI). Nothing is downloaded yet. On API 24–25
   Android's installer asks by itself.
2. **Space:** free space ≥ twice the asset's size + 20 MB, else
   "Not enough free space for the update."
3. **`SHA256SUMS`:** GET the asset (≤ 16 KB); take the line for exactly
   `video-downloader-<version>.apk` (two spaces or ` *`, CRLF, either hex case; two different lines
   → refuse). No file or no line → "This release has no checksum for its APK, so YFT can't check
   it." No connection, or it broke → "The download stopped. Check the connection."; another answer,
   or an address off §4.6's hosts → "Couldn't download the update." (the same two in step 4).
4. **Download; check 1 — the checksum:** `browser_download_url` → GitHub's redirect (HTTPS, §4.6's
   hosts) into `cacheDir/updates/yft-update.apk` (one fixed name: no download queue, no MediaStore,
   no `FileNames`); the SHA-256 computed while writing; a progress bar, "Downloading… 42%" and
   **Cancel**; the size must equal the asset's `size` (≤ 100 MB). Another hash or size → the file
   deleted, "Checksum doesn't match — the download was damaged or changed."
5. **Check 2 — the signature:** `getPackageArchiveInfo` with its signing certificates
   (`GET_SIGNING_CERTIFICATES` on 28+, `GET_SIGNATURES` on 24–27): the SHA-256 set of the APK's
   current signers must equal the installed app's. Different, or nothing read →
   "Signature doesn't match your YFT."
6. **Check 3 — package and version** (the same `PackageInfo`): the package must be this app's own
   (`com.alal.yft` in the release; the debug or preview build's own in test mode), else
   "This file is a different app."; the versionCode: for a stable tag exactly the tag's code (§4.2)
   and above the installed one; for a prerelease (test mode only; it has no rule code) not below the
   installed one; else "This file isn't YFT v1.0.1." or "This file isn't newer than your YFT."
7. **Install:** "Verified: checksum and signature match", then a `PackageInstaller` session
   (`MODE_FULL_INSTALL`, `setAppPackageName`, `setSize`, `setInstallReason(USER)` on 26+,
   `setRequireUserAction(USER_ACTION_REQUIRED)` on 31+), the checked file written into it, committed
   with a mutable `PendingIntent` to the unexported `InstallStatusReceiver`.
   `STATUS_PENDING_USER_ACTION` → its confirmation intent goes to `UpdateInstallFlow`; YFT starts it
   while it is in front (`UpdateAutoOpen` in `YftApp.kt`; Android's own screen; never from the
   background — else at YFT's next resume). `STATUS_SUCCESS` → Android replaces YFT; settings and
   downloads stay. `STATUS_FAILURE_ABORTED` → "Update cancelled."; any other status → "Android
   couldn't install the update (<status>)."; a confirmation that doesn't open → "Android's installer
   didn't open." (the session given up); each with "Nothing was installed." and the link.
8. **Cleanup:** the cached APK is deleted when a check fails, and as soon as it is written into the
   session (the session holds its own copy); at the next start, a left-over file and YFT's own stale
   sessions (`mySessions`) are removed; a status for a session nothing waits for any more gives it
   up.
9. **Test mode only — "Reinstall this build (test)":** this build's own `base.apk` goes through
   steps 5, 6 and 7's session (the same package and signer; its versionCode must equal the installed
   one); instead of "Verified…" its line is "Test: this build's own APK, signature matches" — it is
   no release, so no checksum is claimed. The owner's phone shows HyperOS's real confirmation before
   any release-key update exists. Never in the release.

The manifest changes only in U2: `REQUEST_INSTALL_PACKAGES` and the receiver. U1 changes no manifest
line.

### 4.5 The UI — Settings, the Updates screen

The owner's choice (Snaptube as the model, simpler): no Home card, no dialog, no change to the About
screen.

**Settings → About** (no `SettingsViewModel` change):
- The rows: **Version** (title unchanged; opens About as today; the version moves from the trailing
  `value` to the `supporting` second line, so "1.0.0-beta.3-debug" no longer breaks in the middle) →
  **Updates** (new; opens the Updates screen) → **Licenses** (unchanged).
- The Updates row's second line: a red **New** badge (Coral, as `YftCountBadge`) and
  "v1.0.1 available" when an update is known; "Up to date" after a good answer found none;
  "Not checked yet" before GitHub's first good answer — so the row never claims more than YFT knows.
  No row when the policy is off (§4.1).
- How: `ValueRow` gets an optional badge; `SettingsScreen` gets `updates: UpdatesRow? = null` and
  `onOpenUpdates: () -> Unit = {}` (defaults, so today's tests compile unchanged); `SettingsRoute`
  reads the row from `UpdatesViewModel`; `YftNavHost`'s `settingsContent` gets a third parameter,
  `onOpenUpdates`.

**The Updates screen** (`YftDestination.UPDATES`, full screen with Back, as About):
- Top: "YFT 1.0.0" — the installed version.
- With an update: "New version v1.0.1" ("(test)" added in test mode); the release notes — the
  release's body as plain text in a scrollable box (Markdown signs shown as they are; no link in it
  is opened); the big **Download APK to update** button with its security line under it (the table
  below).
- Without: "You have the latest version" and "Last checked today, 21:35" after a good answer, or
  "Not checked yet" before one; **Check now** — it works with the switch off; while it runs
  "Checking…", then the result, or "No connection.", "GitHub is busy. Try again after 22:10." (the
  phone's time format), "Couldn't check. Try again later.".
- Bottom: the switch **Check automatically** — on by default, off in test mode — with "Once a day
  when YFT opens. Asks GitHub (api.github.com)." In test mode, under it: **Reinstall this build
  (test)** (U2).

**It opens by itself** (`UpdateAutoOpen(navController)`, one line in `YftApp.kt`; in U2 it also
starts Android's confirmation, §4.4 step 7):
- Once per new version: when the app opens and the daily check finds — or has found — a version the
  screen has not opened for: at once if Home is in front, else at the next app open. Never over
  another screen, never as a dialog.
- A version first seen on Check now counts as opened (the user is on the screen). The opened version
  is stored (`update_opened_version`; a prerelease has no code).
- Back = not now: the screen does not open again for that version; the row's New badge stays until
  the update is installed.

**The button and its security line** (true claims only):

| State | U1 (the link) | U2 (in YFT) |
|---|---|---|
| Before the download | "Signed by YFT · checked before install" — Android itself refuses an update signed with another key | the same — YFT's three checks (§4.4), then Android's |
| Downloading | — (the browser downloads) | a progress bar, "Downloading… 42%", Cancel |
| All three checks passed | — (U1 never says "verified") | "Verified: checksum and signature match" → Android's installer |
| A check failed | — | which check, "Nothing was installed.", Download in browser |
| Android's installer cancelled | — | "Update cancelled. Nothing was installed.", Download in browser |
| Reinstall this build (test) | — | "Test: this build's own APK, signature matches" (no checksum: no release) |

### 4.6 Hosts and privacy

- Hosts: `api.github.com` (the check), `github.com` (the asset links) and GitHub's download hosts
  behind its redirect (`objects.githubusercontent.com`, `release-assets.githubusercontent.com`). Any
  other host, or `http:`, is refused. Nothing else is asked.
- GitHub allows 60 unauthenticated requests an hour per IP address; phones behind one carrier
  address share it — hence §4.3's rate limit handling and no retries.
- The notes are text only: no HTML, no image, no link opened.
- Logs: the step, the host and the status only — never a redirect address or a body.

### 4.7 Files

- **New (U1):** `app/src/main/java/com/alal/yft/update/` — `ReleaseVersion.kt` (tag ↔ version and
  code), `GitHubReleases.kt` (parse, candidate, notes), `UpdateChecker.kt` (request, results;
  `UpdateHttp`, the redirects by hand), `UpdateStore.kt`, `UpdateController.kt` (gate, state,
  auto-open bookkeeping), `UpdatePolicy.kt` (the release certificate, which builds check),
  `UpdatesViewModel.kt`, `UpdatesScreen.kt` (`UpdatesRoute`), `UpdateAutoOpen.kt`, `UpdateModule.kt`
  (Hilt); `UpdateBuild.kt` in `app/src/debug/java/`, `app/src/preview/java/` and
  `app/src/release/kotlin/` (§4.1) under `com/alal/yft/update/`.
- **New (U2):** `Sha256Sums.kt`, `UpdateDownloader.kt`, `ApkCheck.kt`, `UpdateInstaller.kt`,
  `InstallStatusReceiver.kt`, `UpdateInstallFlow.kt` (the steps and their state, one for the
  process). Edited in U2: `UpdateChecker.kt` (`UpdateHttp.execute` hands out each hop's call, so
  Cancel stops the one in flight), `UpdateModule.kt` (the shared environment; the flow),
  `UpdatesViewModel.kt`, `UpdatesScreen.kt` (§4.5's U2 column), `UpdateAutoOpen.kt` (the
  confirmation).
- **Edited (small):** `feature/settings/SettingsScreen.kt` (the Version row's version →
  `supporting`; the Updates row; `ValueRow`'s optional badge; `SettingsScreen`'s two new parameters
  with defaults; `SettingsRoute`), `ui/navigation/YftDestination.kt` (`UPDATES`),
  `ui/navigation/YftNavHost.kt` (the route, an `updatesContent` slot with `UpdatesRoute` as its
  default; `settingsContent`'s third parameter), `ui/YftApp.kt` (`UpdateAutoOpen(navController)`),
  `MainActivity.kt` (inject; `onStart` → `onAppOpened()`), the two tests' `settingsContent` lambdas
  (`YftNavigationSmokeTest.kt`, `DesignRenderTest.kt`: a third parameter), `YftDestinationTest.kt`
  (`UPDATES`: its title, full screen), `AndroidManifest.xml` (U2: the permission, the receiver),
  `app/build.gradle.kts` unchanged (§6.2 needs no new library), this plan, this agent's
  `docs/SESSION_STATE.md` section.
- **Not touched:** `HomeScreen.kt` and every Home file, `AboutScreen.kt`, the shared docs (§2.4).
- **Tests:** §6 (new files, the two lambdas, `YftDestinationTest.kt`).

## 5. Steps

### 5.1 U0 — the plan (done, 2026-10-24; revised after the owner's review the same day)

This file and the SESSION_STATE section, docs only (no CI run). STOP: the owner's go after his S5
run 3.

### 5.2 U1 — the check, the Updates row and screen, the link

1. §4.7's U1 files, the JVM and Compose tests (§6.1).
2. CI's set (§7) green on the branch; checkpoint `checkpoint(phase-in-app-update): U1: …`. Its SHA
   is the **U1 cut**.
3. A short Burmese report (green, the U1 cut's SHA), then straight on to U2 — no stop, unless the
   owner says so.

### 5.3 U2 — in-app install

1. §4.7's U2 files on top of U1 (their own commits), the JVM tests and the emulator test (§6.2).
2. CI green, the Emulator smoke included; the branch's Preview APK for U3.
3. STOP: a short Burmese report.

### 5.4 U3 — the owner's phone (after U2 and A's and VK's preview tests; 2026-10-26–27)

- The branch's CI preview APK is `com.alal.yft.preview` with its own test key: Android installs it
  only after the old preview is removed — the owner agreed, so that is step 0.
- Checklist (~30–45 min, with internet unless a step says otherwise):
  0. Uninstall the old YFT preview (`com.alal.yft.preview`); install this branch's preview APK.
  1. Settings → About: Version shows the version on its second line; Updates says "Not checked yet".
     Open Updates: "YFT 1.0.0-beta.3-preview…", "Not checked yet", Check now, Check automatically
     off (test mode).
  2. Airplane mode on → Check now → "No connection." → airplane mode off.
  3. Check automatically on → Back → the Home tab → swipe YFT away and open it again → the Updates
     screen opens by itself: "New version v1.0.0-beta.1 (test)", the notes, the button,
     "Signed by YFT · checked before install". Back (to Home) → Settings → About: the Updates row
     shows the red New and "v1.0.0-beta.1 available". Swipe away and open again → the screen does
     not open again; the badge stays.
  4. Updates → Download APK to update (if asked: Open settings → allow YFT → Back → tap again) → the
     progress → "Signature doesn't match your YFT. Nothing was installed." — the expected answer:
     beta.1's checksum passes, but it has the release key and the preview a test key. Download in
     browser is there (don't install from it).
  5. Reinstall this build (test) → "Test: this build's own APK, signature matches" → HyperOS's
     installer: Cancel → "Update cancelled. Nothing was installed." with the link; again → Install →
     YFT restarts, its settings kept.
- The owner's results are recorded by the release commit (`docs/PHONE_TEST_NOTES.md`, §11).

### 5.5 The release-key update test (rc.1) — the owner's choice at release time

Before v1.0.0 is published, a release-key draft `1.0.0-rc.1` (versionCode 999901; his Release draft
run on the release commit with the name changed, `CHANGELOG.md` `## [1.0.0-rc.1]` and
`docs/release/1.0.0-rc.1.md`; never published, so no friend sees it; deleted after) installed on his
phone → he publishes v1.0.0 → the rc's Updates screen offers v1.0.0 (by itself at the next open) →
Download APK to update → "Verified: checksum and signature match" → Android's confirmation → 1.0.0
installed, settings kept. Cost: one draft run and two doc lines; it can be P8's upgrade check.
Without it, the first real update is v1.0.0 → v1.0.1 (free, but after the friends have v1.0.0; the
link stays the fallback).

### 5.6 The merge — only when the owner says

1. The owner's order: A (`work/phase-download-fixes`), then VK (`work/phase-vk-extractor`), then
   this branch — each into `spike/master-extractor-backup` with a merge commit (no rebase, no
   squash). This branch's merge is done only when the owner says, after A's and VK's.
2. The expected conflict: `docs/SESSION_STATE.md` only (each branch appends its own section) — every
   section kept. No code file is shared (§9; checked again right before the merge).
3. CI's set green on `spike/master-extractor-backup` after the merge.
4. Then the release commit on top: `1.0.0` / `1000000` and the shared docs (§11) — the owner's, not
   this branch's. So there is no U4: `main` is never merged into this branch, this branch has no
   pull request, and nothing in Settings or About is left to align.
5. U2 late (§8): when the owner says, `work/phase-in-app-update-u1` is made at the U1 cut and merged
   instead; U2 waits for v1.0.1.

## 6. Tests

### 6.1 JVM (`testDebugUnitTest`; no live network)

- `ReleaseVersionTest`: tags → versions and codes (`v1.0.0` and `1.0.0` = 1000000, `v1.2.3` =
  1020300); bad tags (a part above 99, letters, extra or missing parts, `vv1.0.0`); prereleases (a
  version, no rule code); compare; the asset name without the `v`.
- `VersionCodeRuleTest`: `BuildConfig`'s name (without `-debug`) and code follow §4.2.
- `GitHubReleasesTest` (a trimmed releases JSON fixture and variants): drafts skipped; prereleases
  skipped, taken in test mode; an equal or lower code not offered; the highest of several; a tag
  without `v`; the APK by its exact name (another APK, or state not `uploaded`, ignored); no APK →
  skipped; no `SHA256SUMS` → offered with no checksum (U2 refuses); a link off GitHub or `http:` →
  skipped; the body kept as the notes (cut at 20,000 characters, at a line end); malformed and
  oversize JSON → `Failed`.
- `UpdateCheckerTest` (MockWebServer, a fake clock): headers (Accept, API version; no Authorization,
  no Cookie); 200 → `Available` or `UpToDate`; offline (no server, `DISCONNECT_AT_START`) and a
  timeout → `Offline`, not counted; 403 with `x-ratelimit-remaining: 0` and a reset →
  `RateLimited(until)` and no request before it, not even on Check now; 429 with `retry-after`; 403
  without rate headers and 5xx → `Failed`; redirects only to the allowed HTTPS hosts, at most 5. The
  gate's cases run in `UpdateControllerTest` on a fake check: once a day (23 h → no request, 24 h →
  one, the clock back → one); the switch off → no request on open, Check now still asks; the policy
  off (another signer) → no request, no row.
- `UpdateControllerTest`: the row's states ("Not checked yet"; "Up to date" only after a good
  answer; New with the version); the screen's states and lines ("Checking…", "No connection.",
  "GitHub is busy…" with its time, "Couldn't check…"); the auto-open bookkeeping (once per version;
  the stored code; Check now counts as opened; the switch off → never); the found update dropped
  after an update; one check at a time. `PreferencesUpdateStoreTest` (Robolectric): every field
  kept, cleared fields removed, a damaged or foreign value read as unset. Helpers:
  `UpdateTestKit.kt` (an in-memory store, a fake check), `ReleaseFixtures.kt` (the releases JSON).
- U2: `Sha256SumsTest` (both modes and cases, CRLF; only the APK's own line; two lines must agree;
  bad hex or spacing); `UpdateDownloaderTest` (a local HTTPS MockWebServer: the list's own line, no
  Authorization or Cookie; no list or no line → no checksum; an oversize list, another answer or no
  server; the APK written and hashed as it comes; another hash or size → damaged, no file; redirects
  only to the allowed HTTPS hosts; over the cap; a broken connection and Cancel leave no file);
  `ApkCheckTest` on fakes (the release's own APK; signer sets — equal, different, empty, other
  order; the signature before the package; a stable tag's code exactly and above the installed one;
  a prerelease only in test mode and not below; the reinstall test's equal code);
  `UpdateInstallFlowTest` on fakes (a start removes what an earlier run left; the owner's order,
  then Android's installer; not allowed → nothing downloaded; no space; no checksum → no download; a
  bad checksum stops before the APK is read, a bad signature before the package check, any failure
  before a session; the progress and Cancel; Cancel while Android waits gives the session up;
  Android's answers; an earlier run's session given up; a session that can't be written; a
  confirmation that can't open; Reinstall this build only in test mode, with its own APK and code; a
  build that never checks never installs); `UpdatesScreenTest`'s U2 cases (the button starts the
  flow, no browser; the progress and Cancel; "Verified…" only once every check passed; each
  failure's line, then "Nothing was installed."; Android's statuses in words; Download in browser
  opens the exact APK link; "Update cancelled."; not allowed → Android's "Install unknown apps" page
  for YFT; the reinstall test's line); `UpdateAutoOpenTest` (Android's confirmation started once the
  checks passed). Planned as `InstallStatusTest` and `UpdateControllerTest`'s U2 cases: the steps
  live in `UpdateInstallFlow`, so their tests do too.
- Compose (Robolectric), new files: `SettingsAboutUpdatesTest` (About's rows in order — Version with
  the version on its second line, Updates, Licenses; the Updates row's three states and the New
  badge; no row with the policy off; a tap opens Updates); `UpdatesScreenTest` (the installed
  version; with an update: the title, the notes box scrolls, the button starts `ACTION_VIEW` with
  the exact link (U1), the security line; without: the latest line and Last checked,
  "Not checked yet", Check now and its lines; the switch and its text; "(test)");
  `UpdateAutoOpenTest` (opens once per version from Home; not over another screen, then at the next
  open; not again after Back; not after Check now). Edited: the `settingsContent` lambdas of
  `YftNavigationSmokeTest.kt` and `DesignRenderTest.kt` (a third parameter) and
  `YftDestinationTest.kt` (`UPDATES`); `SettingsScreenTest` passes as it is (the About row still has
  "Version" and the version).

### 6.2 Emulator (`connectedDebugAndroidTest`, the Emulator smoke, API 34)

- `UpdateInstallInstrumentedTest` (U2) runs the debug build's in-app update (test mode) with the
  real `PackageManager` reading the APK and Android's real installer: the app's own `UpdatesRoute`
  and `UpdateAutoOpen` (its confirmation starter) in the test's activity
  (`createAndroidComposeRule`), the production `UpdateDownloader`, `PackageApkReader` and
  `PackageInstallSystem`, and a controller whose check finds a prerelease (`v1.0.0-beta.3`, no rule
  code) on Check now — the app itself asks GitHub nothing. GitHub is played by an OkHttp interceptor
  on its own hosts and paths (`github.com`'s 302 to `release-assets.githubusercontent.com`, so
  `UpdateHttp`'s host and redirect rules run as in the release); the APK streams from its file and
  nothing leaves the device. The planned local HTTPS MockWebServer is not used: it holds each body
  in memory (the debug APK is tens of MB), and its TLS on the emulator was the risk this section
  named. Android's statuses come to the test's own receiver (`PackageInstallSystem`'s status intent,
  then `InstallStatusReceiver.deliver`), as the app's flow is not the test's. In order:
  1. Installs from YFT not allowed (a fresh install's default; skipped if already allowed) → the
     "Allow YFT…" state with Open settings; nothing asked, nothing downloaded. Never taken back
     later: Android may end the app then.
  2. `appops set … REQUEST_INSTALL_PACKAGES allow`; the app's own `base.apk` → the checksum, the
     signature, the package and version pass → "Verified: checksum and signature match" → a session
     → `STATUS_PENDING_USER_ACTION` → Android's installer on screen (`com.android.packageinstaller`
     or `com.google.android.packageinstaller`) → **Cancel** (never Install: it would replace the app
     under test) → "Update cancelled." and "Nothing was installed." with the link; the cached APK
     gone, no session left; only the list and the APK asked, each through the redirect.
  3. One byte changed in the served APK → "Checksum doesn't match…" before any session (`mySessions`
     empty), the file deleted.
  4. The instrumentation APK served instead (`com.alal.yft.debug.test`, the same debug key, its own
     `SHA256SUMS` line) → the checksum and the signature pass, the package check refuses:
     "This file is a different app."
  5. Reinstall this build (test) → "Test: this build's own APK, signature matches" → the same
     confirmation → Cancel; GitHub not asked; the build's own APK kept.

## 7. Validation

- U work: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.alal.yft.update.*'`.
- Before a push, CI's set: `./gradlew --no-daemon lintDebug testDebugUnitTest :core-model:test
  :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug
  :app:assembleRelease`; U2 also the Emulator smoke (`connectedDebugAndroidTest`, CI only).
- The agent's sandbox has JDK 17 and the Android SDK (platform 35) outside the repo, for the JVM
  runs and the builds before a push, to save CI rounds. CI stays the proof.

## 8. Release placement and time

- The owner's time box: U1 + U2 CI green by **2026-10-26 (MMT)**, his phone test by **2026-10-27**;
  else v1.0.0 ships the U1 cut and U2 moves to v1.0.1 (nothing else changes).
- Estimates (agent work + CI; the go assumed on 2026-10-25 morning MMT, after S5's run 3), U1
  re-estimated for the new UI:

| Step | Work | CI | Green (MMT) |
|---|---|---|---|
| U1 | 6–8 h (+2 h: the Updates row and screen, the auto-open) | 1–2 rounds (~40 min each) | 2026-10-25 night |
| U2 | 8–10 h, straight after U1 | 2–3 rounds, the emulator included | 2026-10-26 evening |
| U3 | the preview ~20 min; the owner's checks 30–45 min | — | 2026-10-26 night or 10-27, after A's and VK's preview tests |
| Merge | ~30 min, when the owner says (after A and VK) | 1 round on the spike | the owner's day |

- Risk: U1's +2 h leaves less room — the emulator's installer window (+½ day) would push U2 past
  Oct 26. Not green by 2026-10-26 24:00 MMT → a report, and v1.0.0 = the U1 cut.
- v1.0.0's version line (`1.0.0` / `1000000`) belongs to the release commit, not to this branch.

## 9. Files shared with A and VK

- **A** (`work/phase-download-fixes`, `abac20e` when rechecked on a fresh clone, 2026-10-24): no
  code file in common — A changes none of `SettingsScreen.kt`, the navigation files, `YftApp.kt`,
  `MainActivity.kt`, the manifest, Gradle or the two tests U edits; U no longer touches Home (A's
  Home/D4 files) nor `FileNames.kt` (U2 saves under one fixed cache name).
- **VK** (`work/phase-vk-extractor`, `f2433a1`): none — U uses core-data's `OkHttpClient`, not
  `OkHttpExtractorClient`; `SiteAdapterModule.kt` untouched.
- **All three:** `docs/SESSION_STATE.md` — each appends its own section; the only expected conflict
  at the merge (§5.6), every section kept.
- **The release commit** comes after the three merges, on top of them: nothing to resolve with it.

## 10. Backlog (after v1.0.x)

- A small Home card when an update is found (v1.0.0 has the Settings row and the screen only).
- The notes with Markdown (headings, lists, links) instead of plain text.
- Signing-key rotation: U2 compares the current signers strictly and refuses a rotated key (the link
  remains).
- A beta channel for friends who opt in; resuming a broken download; a mirror if GitHub is
  unreachable; update ownership (Android 14) — not wanted while Android's confirmation comes every
  time.
- If YFT ever goes to Google Play, U2 goes (Play does not allow self-updates).

## 11. Lines for the release commit (not this branch)

- `docs/RELEASE.md`: the version code rule (§4.2) instead of "raise by one"; every release from the
  Release draft workflow (its `SHA256SUMS`); the update test (rc → stable, §5.5) if the owner runs
  it.
- `CHANGELOG.md` (1.0.0) and `docs/release/1.0.0.md`: "YFT tells you when a new version is out
  (Settings → About → Updates), with its release notes" — with U2: "and installs it after checking
  its checksum and signature, with Android's confirmation".
- **About › Privacy** (`AboutScreen.kt`'s first line, which the release commit rewrites for the
  Backup finder anyway): today's "YFT only contacts the sites you open and the media servers they
  point to." is untrue with the update check on — it should name it, e.g. "With Check automatically
  on, YFT also asks GitHub (api.github.com) about its newest release once a day; nothing about you
  or your downloads is sent."
- `PROGRESS.md`; `docs/TEST_MATRIX.md` (one row per step: commits, results);
  `docs/PHONE_TEST_NOTES.md` (the owner's U3); `README.md`'s privacy lines, if they list hosts.

## 12. Status

- 2026-10-24: the owner's request: the in-app update in v1.0.0 (the first stable release) with A and
  VK; U0 only. Plan recorded (docs only, `5b5c31a`); branch made from `8dc46ff`. `SHA256SUMS`
  confirmed on every workflow release (§3): no workflow change.
- 2026-10-24, the owner's review: the Updates row and screen in Settings → About (no Home card, no
  About change, no dialog), the screen opening by itself once per new version, the button's security
  lines (true claims only); U2's package and version check, tags with or without `v`; the merge
  order A → VK → this branch into the spike, then the release commit (no U4); U3 right after U2,
  after A's and VK's preview tests, the old preview uninstalled first; rc.1 the owner's choice at
  release time. Plan revised (docs only); U1 re-estimated at 6–8 h. Next: STOP — the owner's go
  after his S5 run 3; then U1 → U2 without a stop.
- 2026-10-25 (MMT), the owner's go (S5 run 3 passed; the plan `1a354a3` approved as written, §11's
  About line for the release commit): **U1** — §4.7's U1 files and §6.1's tests, checkpoint
  `checkpoint(phase-in-app-update): U1: …` (its SHA, the U1 cut, and CI in the next entry). Where U1
  differs from the plan as written: the store is an app-private `SharedPreferences` file
  (`update_opened_version`, a prerelease having no code); the release's `UpdateBuild` in
  `src/release/kotlin`; `YftDestinationTest.kt` edited for the new destination; the link without a
  browser selector (§4.3). Next: U2 without a stop; U2 not green by 2026-10-26 24:00 MMT → a report,
  and v1.0.0 takes the U1 cut.
- 2026-10-25 (MMT): **U1 green** — the U1 cut is `e615a96` (`checkpoint(phase-in-app-update): U1:
  the check, the Updates row and screen, the link`); its CI all success: Work-branch checkpoint
  validation 40123514346, Android emulator smoke 40123514304, Preview APK (test key) 40123514313.
  Straight on to U2.
- 2026-10-25 (MMT): **U2** — §4.7's U2 files and §6's U2 tests, checkpoint
  `checkpoint(phase-in-app-update): U2: …` (its SHA and CI in the next entry). Where U2 differs from
  the plan as written: the steps in `UpdateInstallFlow`, not `UpdateController` (so the planned
  `InstallStatusTest` and `UpdateControllerTest` U2 cases are `UpdateInstallFlowTest`'s and
  `UpdatesScreenTest`'s); the cached APK deleted as soon as the session holds its copy; the
  download's own lines ("The download stopped. Check the connection.",
  "Couldn't download the update.") and "Android's installer didn't open."; Cancel also gives up a
  session that waits for Android; §6.2's GitHub an in-test interceptor, not MockWebServer, so
  `app/build.gradle.kts` is unchanged. Next: CI green (the Emulator smoke included; the branch's
  Preview APK for U3), then STOP with a short Burmese report.
- 2026-10-25 (MMT): U2's checkpoint is `6883e4d`. Its CI: Work-branch checkpoint validation
  40131269232 and Preview APK (test key) 40131269208 success; the Android emulator smoke 40131269200
  failed in the new test only (59 tests, 2 failures): steps 2 and 5 waited for Android's installer
  in UiAutomator, where Compose's test clock stands still, so the app's confirmation starter (a
  composition) never ran — the state already held Android's confirmation, so the checks, the session
  and its status all worked on API 34; steps 1, 3 and 4 passed. The test now waits through Compose
  (`checkpoint(phase-in-app-update): U2: wait for Android's installer through Compose`; its SHA and
  CI in the next entry). No app change.

## Appendix — the agent's prompt

```text
You are the in-app update agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: work/phase-in-app-update (it exists; made from PR #2's head 8dc46ff). Work only there.

Read first, in this order:
1. AGENTS.md — the repository's rules (checkpoints with scripts/checkpoint.sh).
2. docs/IN_APP_UPDATE_PLAN.md — your plan. Follow it step by step (U1 → U2 → U3). Its §2 hard
   rules come first.
3. In app/src/main/java/com/alal/yft/: feature/settings/SettingsScreen.kt,
   ui/navigation/YftDestination.kt, ui/navigation/YftNavHost.kt, ui/YftApp.kt, MainActivity.kt.

Start U1 only after the owner's go (after his S5 run 3). When U1 is green, send a short report
and go on with U2 at once. Stop at every STOP and report to the owner in Burmese, short. App
text in English; every text says only what YFT or Android really checked.

Never: push to main, spike/master-extractor-backup or another work branch (the one exception:
this branch's merge into spike/master-extractor-backup, after A's and VK's, only when the owner
says); open a pull request or add a label; merge main into this branch; touch the Home files or
AboutScreen.kt; edit the shared docs; run master-recipes-publish or touch the recipes branch;
publish a release, push a tag or run the Release draft workflow; change a workflow, a script or
gradle.properties; relax TLS or the network rules; send a token or a cookie; log or commit
GitHub's signed download addresses; install or send an APK for the owner's phone before he says
S5's run 3 is counted.
```
