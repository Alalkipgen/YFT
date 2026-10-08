# Agent A — Downloads and merges keep going in the background (P34; later P38)

**ရည်ရွယ်ချက်:** YFT ကနေ တခြား app (Facebook စသဖြင့်) ကို ပြောင်းသုံးနေရင်လည်း၊ screen ပိတ်ထားရင်လည်း
download၊ **merge (video+audio ပေါင်းတာ)**၊ MP3 ပြောင်းတာ၊ Download/YFT ထဲ သိမ်းတာ ဆက်လုပ်နေအောင် လုပ်မယ်။
Notification bar မှာ Snaptube လို "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left" ပြမယ် (1,024 KB/s
အောက် KB/s၊ 1 MB/s ကနေ MB/s)၊ merge နေရင် "Merging audio and video · 45%"။ ဖုန်း (Xiaomi HyperOS) က
YFT ကို background မှာ ရပ်ထားခဲ့ရင် သိပြီး "No restrictions / Autostart / Recents lock" လုပ်နည်း card ပြမယ် (P34)။

**အကြောင်းရင်း:** Code ထဲမှာ merge လုပ်နေချိန်လည်း task က `RUNNING` ဖြစ်နေလို့ foreground service
မရပ်ပါဘူး — ဒါကြောင့် ရပ်သွားတာ ဖုန်းရဲ့ battery စနစ်က network မသုံးဘဲ CPU ပဲသုံးတဲ့ background app
(merge) ကို ခဏရပ် (freeze) ထားတာ ဖြစ်နိုင်ခြေ အများဆုံးပါ။ YFT ဘက်က wake lock မရှိ၊ service type
`dataSync` တစ်ခုတည်း (Android 15 မှာ merge အတွက် `mediaProcessing` ရှိ)၊ notification ပိတ်ထားရင် မမြင်ရ —
ဒါတွေ အကုန်ပြင်မယ်။ Merge ကိုယ်တိုင် မြန်အောင်က Agent C (P35)။

**အချက်အလက်:** Phase 14 · **Agent A** · branch `work/phase-14-background` · P34 (Medium, 5–7 နာရီ)
· Agent B နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)။ P34 ပြီးရင် A က B နဲ့ C ကို စောင့်ပြီး
P38 (merge + Preview #6) ကို [`M-merge-preview6.md`](M-merge-preview6.md) နဲ့ လုပ်ပါမယ်။

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ P34 ပြီးရင်
`READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။ Agent ၂ ယောက်ပဲ သုံးရင် P34 ပြီးတဲ့အခါ ဒီ chat ထဲမှာပဲ
[`C-fast-merge.md`](C-fast-merge.md) ကို `BRANCH_OVERRIDE: work/phase-14-background` နဲ့ paste လုပ်ပါ။
Computer မှာ SSH key မရှိရင် agent က key အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — GitHub › YFT ›
Settings › Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး "SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P34](../FIX_ADD_PLAN.md#p34--downloads-and-merges-keep-going-in-the-background)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, MediaMuxer, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: A — foreground service, notification, Downloads and Settings screens (one of three
       agents working at once; later the integrator for P38)
Task: P34 — Downloads and merges keep going in the background; speed in the notification
Branch: work/phase-14-background (first start: create it from origin/work/phase-14-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: BATTERY_CARD=OFF keeps only the Settings entry and the
                           freeze message; DONE_NOTICE=OFF skips the finished notice)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-A (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-A
- Push with SSH. If /data/.ssh/id_ed25519 is missing, make a NEW key (never search the
  computer for old keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/id_ed25519 -C "yft-a-<date>"
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of /data/.ssh/id_ed25519.pub and wait until he says he added it
  (GitHub > YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i /data/.ssh/id_ed25519 -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- app/src/main/java/com/alal/yft/download/**; app/src/main/java/com/alal/yft/feature/downloads/**;
  app/src/main/java/com/alal/yft/feature/settings/**;
  app/src/main/java/com/alal/yft/ui/components/NotificationPermission.kt;
  app/src/main/AndroidManifest.xml (permissions and the download service's entry only);
  app/src/androidTest/java/com/alal/yft/background/** (new); the tests beside them;
  app/build.gradle.kts only for an androidTestImplementation line.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent A ...", CHANGELOG.md
  "### Phase 14 — Agent A (P34)", docs/TEST_MATRIX.md "### Agent A — P34".
- Never: core-download and core-model/.../download (Agent C: the merge itself), core-browser,
  core-media, extractor-*, app/.../detection and app/.../feature/{browser,quickdownload,home,
  detectedmedia}/** (Agent B), ui/format (read only), res/, MainActivity, ui/navigation,
  docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**. Agents B and C push their own
  branches at the same time. Need a change outside your files? Do not make it: write
  "Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE section and report it.
- Contracts: DownloadEnqueuer and DownloadPlanFactory are called by B's sheet: change them only
  by adding, with defaults. Use C's DownloadQueue, DownloadTask, DownloadProgress,
  AudioVideoMuxStage and the task's AudioVideoMuxCheckpoint as they are (read the stage from
  them; do not change them).

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-14-background origin/work/phase-14-integration
   Later:       git switch work/phase-14-background && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2 (items 4 and 5), 3 (G3, G4, G5, G8),
   4 (R21, R22, R24) and task P34 with its "Read first" files, then your section of
   docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent A scope):
   ./gradlew --no-daemon --continue :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P34 IN PROGRESS, the start date and your base commit.

WORK P34 (FIX_ADD_PLAN P34, findings R21, R22, R24)
Root causes: the task stays RUNNING through download, merge and save, and RUNNING keeps
DownloadForegroundService in the foreground, so the merge stopping in the background most likely
comes from the phone (HyperOS freezes background apps that use only the CPU — a merge has no
network traffic — unless YFT may run without battery limits). YFT holds no wake lock or Wi-Fi
lock, declares only the dataSync type, has no onTimeout, and its notification has no % text,
speed or time left (and is hidden when notifications are off).
1. Measure first (CI emulator, app/src/androidTest/java/com/alal/yft/background/): (a) a real
   download from a small slow server inside the test (~200 KB/s for ~1 min) -> pressHome() ->
   for 20 s the stored bytes keep rising and the ongoing notification exists; (b) a merged
   download from the test server (the androidTest mux assets, or P27's long input from
   LongFragmentedMp4.kt copied into your folder) -> pressHome() when the stage is MUXING -> the
   merge progress keeps rising and the task completes without the app coming back. Optional:
   (a) with `dumpsys deviceidle force-idle` (then unforce). Record the old code's behaviour.
2. While any task runs (download, merge, MP3 conversion, save): partial wake lock (tag
   yft:downloads, renewed with a timeout); Wi-Fi lock (WIFI_MODE_FULL_HIGH_PERF) while bytes are
   downloaded; both released as soon as nothing runs and in onDestroy. Manifest WAKE_LOCK. The
   service never stops while a task is RUNNING at any stage.
3. ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_DATA_SYNC) on API 29+; on API 35+
   add FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING while a merge, conversion or save runs (manifest:
   foregroundServiceType="dataSync|mediaProcessing" and FOREGROUND_SERVICE_MEDIA_PROCESSING).
   FOREGROUND_SERVICE_IMMEDIATE for the notification. onTimeout(startId, fgsType): pause what
   runs, stop, post "Android paused downloads after 6 hours. Open YFT to resume." A refused
   start (ForegroundServiceStartNotAllowedException) keeps the downloads queued with that notice,
   no crash. Keep today's resume after a START_STICKY restart.
4. Freeze detector: while a task runs, tick once a second on SystemClock.elapsedRealtime; a tick
   more than 10 s late while the wake lock is held = the phone froze YFT: count it, log one line
   (time lost, stage; no names or addresses), show the battery card again with "Your phone
   paused YFT in the background for 1 min 40 s."
5. Notification (one ongoing, at most one update a second): one download -> title = video title
   (shortened), text "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left", progress bar, Pause all, tap
   opens YFT; unknown size -> "61 MB · 1.2 MB/s"; several -> "Downloading 3 videos · 45%",
   "2.4 MB/s · 1 min left" and InboxStyle lines "<title> — 45% · 1.2 MB/s" (up to 5); merge and
   save -> P27's "Merging audio and video · 45%" / "Saving to Download/YFT · 80%" (moving in the
   background); "Waiting for network" / "Waiting for Wi-Fi". Speed over the last few seconds,
   computed once for card and notification (share TransferRateTracker's logic); format: below
   1,024 KB/s "850 KB/s", from there "1.2 MB/s" (one decimal), 1,024-based. Time left: "15 s
   left", "3 min left", "1 h 5 min left"; hidden when unknown.
6. Finished notice (DONE_NOTICE, default on): channel "Finished downloads": "Downloaded ·
   <title>" and "Download failed · <title> — <short reason>"; no path or address.
7. Notifications off (Android 13+ denied or channel off): Downloads card "Turn on notifications
   to see download progress outside YFT" -> ACTION_APP_NOTIFICATION_SETTINGS; dismissable;
   testTag downloads-notifications-card.
8. Battery card (BATTERY_CARD, default on): when not ignoring battery optimizations and a
   download was started (or a freeze was seen): "Downloads and merges may stop when YFT is in
   the background. Allow YFT to run without battery limits." ->
   ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (manifest REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
   YFT is not on Google Play), fallback ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS / app
   details. Xiaomi, Redmi, POCO (Build.MANUFACTURER): add "Settings › Apps › Manage apps › YFT ›
   Battery saver › No restrictions; turn on Autostart; in Recents, hold YFT's card and tap the
   lock." Not now hides it until the next freeze. Settings › Downloads › "Background downloads":
   Allowed / Limited, same button and steps. testTags downloads-battery-card,
   settings-background-downloads.
9. Keep the Wi-Fi-only policy, Pause all, P27's stages and every testTag.
TESTS P34
- JVM: one-download text "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left" (must fail on the old
  code); several downloads; unknown size; merge and save stages; speed boundaries (1,023 KB/s ->
  "1023 KB/s", 1,024 KB/s -> "1.0 MB/s", 12.3 MB/s); time-left texts; <= 1 update a second; the
  service stays foreground at MUXING and SAVING; wake lock through download, merge and save,
  released when paused/finished/failed, Wi-Fi lock only while downloading (fake lock; must fail
  on the old code); media-processing type while merging on API 35 only; freeze detector (must
  fail on the old code); onTimeout; battery card by state with the Xiaomi steps only on Xiaomi;
  notifications card.
- Instrumented (CI emulator): step 1's two background tests; the finished notice.
LIVE CHECK P34: none (no site involved); the CI emulator and the owner's phone are the proof.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in); never log or commit cookies, tokens, signed media or image URLs or keys; WebView and
WebSettings calls on the main thread; Kotlin lines within 100 characters; keep every testTag;
app text stays English; temporary files and backups outside the repository (/data/tmp,
/data/bak); never undo work with git reset --hard, git clean or git stash; do not edit
.github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P34, put the old versions
back, run the new tests and see them fail, restore with cp, check with cmp, and name the
failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Your sections only: docs/TEST_MATRIX.md (with step 1's findings), CHANGELOG.md,
   docs/SESSION_STATE.md (status OWNER CHECK; Result with any "Plan adapted"; validation
   numbers; regression proof; CI links; hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P34: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting. (Docs-only pushes start no CI.)
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5, with Level), with the run links and
   the owner check: (1) a large download, Facebook for 2 minutes -> "N% · speed · … left" and it
   keeps going; screen off a minute -> still going; (2) a long YouTube video: when "Merging … %"
   starts switch to Facebook -> the % keeps moving and "Downloaded · …" arrives without opening
   YFT; (3) if the card says the phone paused YFT, follow its Xiaomi steps once and repeat (2).
After P34: set your SESSION_STATE section to READY FOR MERGE (last code commit, green CI links),
report, and stop. When B and C are READY FOR MERGE the owner gives you
docs/prompts/M-merge-preview6.md (P38).
```
