# YFT — Phase 6 ဆက်လုပ်ရန် Prompt (နောက် Agent အတွက်)

မင်းက YFT Android project (ကြော်ငြာမပါတဲ့ video downloader) ကို ဆက်လုပ်မယ့် agent ဖြစ်တယ်။
Repo: `https://github.com/Alalkipgen/YFT` · Branch: **`work/phase-6-hardening`** (ဒီ branch ပေါ်မှာပဲ ဆက်လုပ်ပါ)

## အမိန့်များ (မဖြစ်မနေ လိုက်နာ)
- မရပ်နဲ့၊ မေးခွန်းမမေးနဲ့၊ token ကုန်တဲ့အထိ ဆက်လုပ်ပါ။ milestone တစ်ခုပြီးတိုင်း checkpoint commit + push လုပ်ပါ။
- Commit လုပ်နည်း: `CHECKPOINT_TEST_COMMAND="..." bash scripts/checkpoint.sh "msg"` (`docs/SESSION_STATE.md` ကို stage မလုပ်ရင် script က ငြင်းတယ်၊ secret scan လုပ်ပြီး same-name branch ကို push လုပ်တယ်)။
- Secret / keystore / `local.properties` / `.env` မ commit နဲ့။ `main` ကို merge မလုပ်နဲ့၊ broken code ကို `main` ပေါ် မတင်နဲ့။ Publish မလုပ်နဲ့၊ Phase 7 မစနဲ့။
- Kotlin line ≤ 100 chars (`awk 'length > 100'` နဲ့စစ်)။ ရှိပြီးသား test tag တွေ (`home-list`, `destination-<route>`, `navigate-back`, `preview-download`, `candidate-sheet`, `downloads-*` …) နဲ့ `YftNavigationSmokeTest` (destination title စစ်တာ) ကို မပျက်စေနဲ့။
- နောက်ဆုံး report ကို မြန်မာလိုရေး: လုပ်ခဲ့တာ၊ test ရလဒ်၊ commit SHA၊ push status၊ known limitations၊ next step။

## ဖတ်ရမယ့် docs
`docs/prompts/07_PHASE_6.md` (Phase 6 DoD) · `docs/HARDENING_AUDIT.md` (S/R/P/U findings နဲ့ status) · `docs/PHASE_STATUS.md` · `docs/SESSION_STATE.md` · `docs/HANDOFF.md`

## Build environment
```
cd /data/YFT; export JAVA_HOME=/data/toolchains/jdk17 PATH="/data/toolchains/jdk17/bin:$PATH" ANDROID_HOME=/data/android-sdk ANDROID_SDK_ROOT=/data/android-sdk
pkill -f "[G]radleDaemon"; nohup ./gradlew --no-daemon <tasks> > /data/LOG_x 2>&1 &
```
- Robolectric SDK jar cache မှာ **28 နဲ့ 35 ပဲရှိတယ်** (`@Config(sdk=[28])` / `[35]` ပဲသုံး)။
- Background scope ထဲက coroutine တွေကို test မှာ `runCurrent()` နဲ့ မောင်းပါ (`advanceUntilIdle` က background work မစောင့်ဘူး)။
- Test ရလဒ်: `*/build/test-results/**/*.xml` · Lint: `app/build/reports/lint-results-debug.xml`

## ပြီးပြီးသား (ဒီ checkpoint အထိ)
- **6A** audit (`b503e30`)။
- **6B security/privacy**: `data_extraction_rules.xml`, `network_security_config.xml`, manifest flags; notification `VISIBILITY_PRIVATE` + public version; `POST_NOTIFICATIONS` runtime request (`ui/components/NotificationPermission.kt`); bidi filename test; Settings › Privacy › Clear browsing data (`feature/settings/PrivacyCleaners.kt` — `WebViewBrowsingDataCleaner`) နဲ့ Clear download history (`QueueDownloadHistory` — COMPLETED/FAILED/CANCELLED record တွေပဲဖျက်၊ ဖိုင်မဖျက်)။
- **6C reliability/settings**:
  - core-model `settings/DownloadPreferences.kt` (`QualityPreference`, `DownloadLocation`, `DownloadPreferences`, `pick()`)၊ core-data `DataStoreDownloadPreferencesRepository` (DataModule မှာ bind ပြီး)။
  - `download/policy/` — `NetworkStatusSource`/`ConnectivityNetworkMonitor`, `DownloadNetworkPolicy`, `DownloadPolicyController` (`DownloadPolicyGate`)၊ `DownloadRuntimeModule` မှာ Hilt provide ပြီး၊ `DownloadEnqueuer` က enqueue မလုပ်ခင် `ensureApplied()` ခေါ်တယ်၊ `DownloadForegroundService` ရဲ့ ကိုယ်ပိုင် network callback ကို ဖယ်ပြီး policy ကိုသုံးတယ်။
  - Location pref: `DownloadDestinationProvider.prepare(fileName, mimeType, location)`; API29+ & SHARED → MediaStore၊ မဟုတ်ရင် app-private။
  - R6 fix: app-private နာမည်တူဆိုရင် "name (1).ext" reserve (`AppPrivateNames`)၊ `PreparedDestination.fileName` → queue displayName/result ထဲရောက်တယ်။
  - Settings screen အသစ် (`SettingsScreen`/`SettingsRoute`/`SettingsViewModel`): quality, location, Wi-Fi only, mobile-data confirm, concurrency 1–4, theme, clear data dialogs။ `YftNavHost` မှာ `settingsContent` slot ထည့်ပြီး smoke test ကို update လုပ်ပြီး။
  - Preview: default quality နဲ့ variant ကို preselect၊ mobile data မှာ `ConfirmMetered` dialog (`metered-confirm`/`metered-dismiss`)၊ Wi-Fi only ဆိုရင် "starts when Wi-Fi is available" message။
- Tests အသစ်: `DownloadPolicyControllerTest`(7), `SettingsViewModelTest`(5), `SettingsScreenTest`(5), enqueuer/destination provider tests ထပ်တိုး။

## လုပ်ရန်ကျန် (အစဉ်လိုက်)
1. **ဒီ checkpoint ရဲ့ test status ကို `docs/SESSION_STATE.md` မှာ ကြည့်ပါ။** FAIL ရှိခဲ့ရင် အရင်ပြင်ပါ (`:core-model:test :core-data:testDebugUnitTest :app:testDebugUnitTest`)။ Preview ViewModel အတွက် test အသစ်ထည့်ပါ: quality preselect (`UP_TO_720P` → 720p variant)၊ metered confirm → `confirmMeteredDownload()` → enqueue၊ dismiss → Idle၊ Wi-Fi-only → `waitingForUnmetered=true`။ PreviewScreenTest မှာ metered dialog test။
2. **6D UI finalization**:
   - Library screen: MediaStore `Download/YFT/` items + app-private `noBackupFilesDir/downloads` files စာရင်း၊ in-app Media3 နဲ့ play၊ MediaStore item ကို open/share၊ confirm dialog နဲ့ delete (FileProvider မရှိသေးဘူး — app-private share လိုရင် FileProvider ထည့်ရမယ်)။ `YftNavHost` မှာ `libraryContent` slot ထည့်ပြီး smoke test ကို update လုပ်ပါ။
   - Home: URL field + Paste button (tap မှသာ clipboard ဖတ်) → Browser ကို URL ပို့ပါ (navigation arg သို့မဟုတ် shared state)။
   - `YftTopBar` back button ကို "←" text အစား `Icons.AutoMirrored.Filled.ArrowBack` + contentDescription ပြောင်း (tag `navigate-back` ထိန်း)။ Browser icon buttons မှာ contentDescription။
   - About: licenses / `docs/THIRD_PARTY_NOTICES.md` အကျဉ်း / privacy စာသား။ Original color palette၊ empty/loading/error states၊ accessibility။
3. **6E**: free-space pre-check (R3, enqueue မှာ `INSUFFICIENT_STORAGE`)၊ Downloads screen မှာ `DownloadPolicyController.state` ကိုသုံးပြီး "Waiting for Wi-Fi" banner၊ P2 pruning (orphan `.part` / workspace သန့်ရှင်းရေး)။
4. Docs update: `TEST_MATRIX.md`, `HARDENING_AUDIT.md`, `PHASE_STATUS.md`, `HANDOFF.md`, `SESSION_STATE.md`, `README.md`။
5. **Full validation**: `lintDebug testDebugUnitTest :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test :app:assembleDebug :app:assembleRelease` → lint 0 errors၊ test အားလုံး pass၊ R8 က bridge method `post` ကို keep ထားဆဲလား စစ်။ ပြီးရင် Phase 6 completion commit + push၊ CI စစ် (`curl https://api.github.com/repos/Alalkipgen/YFT/commits/<sha>/check-runs`)၊ မြန်မာလို final report။

## Known limitations (report မှာ ထည့်ရန်)
YouTube progressive only (များသောအားဖြင့် 360p) + M4A; PO token မရှိ (non-embeddable → 403 ဖြစ်နိုင်); network အချို့မှာ bot check; solver/client profile maintenance လို; WebView solver နဲ့ live YouTube ကို device ပေါ်မစစ်ရသေး (`/dev/kvm` မရှိ); muxing AVC/AAC ပဲ; DASH SegmentBase/live မရ; SAF export မချိတ်ရသေး; release APK unsigned; KAPT warning; adapters fixture နဲ့ပဲ စစ်ထား; Clear browsing data နဲ့ Wi-Fi-only ကို device ပေါ် မစစ်ရသေး။
