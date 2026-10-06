# Agent A — YouTube: no long wait at 99% (P27; later P33)

**ရည်ရွယ်ချက်:** YouTube video ကြာကြာ (live recording ၁ နာရီကျော်) ကို download ဆွဲတဲ့အခါ 99% မှာ
အကြာကြီး ရပ်မနေအောင် လုပ်မယ်။ အကြောင်းရင်း — video နဲ့ audio ကို ပေါင်း (merge) တာကို app ထဲမှာ
အရင်လုပ်ပြီး ဖိုင်တစ်ခုလုံးကို Download/YFT ထဲ ထပ်ကူးနေတာ (၂ ခါ ရေး) ဖြစ်ပြီး အဲဒီအချိန်မှာ progress
မပြလို့ 99% မှာ ငြိမ်နေတာပါ။ ပြင်ပြီးရင် card မှာ "Merging audio and video · 45%" → "Saving to
Download/YFT · 80%" ပြမယ်၊ Android 8.0+ မှာ Download/YFT ထဲကို တိုက်ရိုက် တစ်ခါပဲ ရေးမယ် (P27)။

**အချက်အလက်:** Phase 13 · **Agent A** · branch `work/phase-13-merge-speed` · P27 (Medium, 3–5 နာရီ)
· Agent B နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)။ P27 ပြီးရင် A က B နဲ့ C ကို စောင့်ပြီး
P33 (merge + Preview #5) ကို [`M-merge-preview5.md`](M-merge-preview5.md) နဲ့ လုပ်ပါမယ်။

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ P27 ပြီးရင်
`READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။ Agent ၂ ယောက်ပဲ သုံးရင် P27 ပြီးတဲ့အခါ ဒီ chat ထဲမှာပဲ
[`C-browser.md`](C-browser.md) ကို `BRANCH_OVERRIDE: work/phase-13-merge-speed` နဲ့ paste လုပ်ပါ။
Computer မှာ SSH key မရှိရင် agent က key အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — GitHub › YFT ›
Settings › Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး "SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P27](../FIX_ADD_PLAN.md#p27--youtube-no-long-wait-at-99)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, MediaMuxer, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: A — download engines (merge), Downloads screen and notification (one of three agents
       working at once; later the integrator for P33)
Task: P27 — YouTube: no long wait at 99%
Branch: work/phase-13-merge-speed (first start: create it from origin/work/phase-13-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: DIRECT_MUX=NO keeps today's two-step save and only adds
                           progress and the larger buffer)

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
- core-download/**; core-model/src/main/kotlin/com/alal/yft/core/model/download/**;
  app/src/main/java/com/alal/yft/download/**; app/src/main/java/com/alal/yft/feature/downloads/**;
  app/src/androidTest/java/com/alal/yft/download/**; the tests beside them;
  app/build.gradle.kts only for an androidTestImplementation line.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent A ...", CHANGELOG.md
  "### Phase 13 — Agent A (P27)", docs/TEST_MATRIX.md "### Agent A — P27".
- Never: core-browser, core-media, extractor-*, app/.../feature/{browser,quickdownload,home,
  detectedmedia,settings}/** (Agents B and C), core-data, docs/FIX_ADD_PLAN.md, docs/prompts/**,
  .github/workflows/**, res/ and the manifest. Agents B and C push their own branches at the
  same time. Need a change outside your files? Do not make it: write "Hand-off to <agent>:
  <file> — <change> — <why>" in your SESSION_STATE section and report it.
- Contracts: DownloadEnqueuer, DownloadPlanFactory, the public DownloadQueue API, DownloadTask,
  DownloadProgress, DownloadFailure, DownloadFailureReason, AudioVideoMuxStage and
  DownloadDestination are used by B's and C's code: change them only by adding, with defaults;
  never rename, remove or change a meaning.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-13-merge-speed origin/work/phase-13-integration
   Later:       git switch work/phase-13-merge-speed && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2, 3 (F5), 4 (R12) and task P27 with its
   "Read first" files, then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent A scope):
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P27 IN PROGRESS, the start date and your base commit.

WORK P27 — YouTube: no long wait at 99% (FIX_ADD_PLAN P27, finding R12)
Root cause: after both tracks are downloaded, AudioVideoMuxEngine (AndroidLocalMuxer.mux ->
copySamples, MediaExtractor -> MediaMuxer) writes a new file in app storage, then publish()
copies that whole file again into Download/YFT (64 KiB buffer) and syncs it. None of this
reports progress, and the MUXING stage is shown nowhere, so the card sits at 99%: for a 1.3 GB
recording about 4 GB of storage reads and writes after "99%", up to three copies at once.
1. Measure first: time each step (video track, audio track, mux, copy, sync, commit) in log
   lines (no addresses) and in a merge failure's detail. An instrumented test on the CI
   emulator merges a long generated input (>= 20 min, small bitrate, many samples; fragmented
   MP4 if the test can make one) and prints the split. Record the numbers in the Result.
2. Progress after the download: the mux reports sample time written / track duration, the copy
   reports bytes; the record keeps the stage. The Downloads card and the notification say
   "Merging audio and video · 45%", then "Saving to Download/YFT · 80%" (testTag
   download-stage-<id>); the bar keeps moving.
3. Direct mux (default; OWNER ANSWERS DIRECT_MUX=NO skips this step): on API 26+
   (MediaMuxer(FileDescriptor, format)), when the destination gives a seekable read-write file
   descriptor (pending MediaStore row, app-storage file; a SAF document only when its
   descriptor is seekable), mux straight into it, check its length, commit — no second copy.
   Add this to DownloadDestination by addition (default: not available). Otherwise and on
   Android 7.x keep today's path with a 1 MiB copy buffer. A direct mux that fails before its
   first sample falls back once to today's path.
4. Space: before merging check the free space the chosen path needs (direct ~ video + audio;
   today's path twice that); fail early with INSUFFICIENT_STORAGE at stage MERGE; delete each
   track file as soon as the merge has succeeded.
5. If step 1 shows MediaExtractor's reading is the slow part, write the numbers and a backlog
   note in your SESSION_STATE section; do not replace the muxer now.
TESTS P27
- JVM: progress rises through MUXING and the copy and never stays at 99% (must fail on the old
  code); the two labels; a destination with a file descriptor -> one write, no second copy
  (must fail on the old code: two writes); without one -> today's path; a direct-mux failure
  before the first sample falls back once; the space check fails early.
- Instrumented (app/src/androidTest/.../download/): a direct mux into a new MediaStore item
  gives a playable file with one video and one audio track; the long-input timing test.
LIVE CHECK P27: none in the sandbox (YouTube answers data-centre networks with a bot check);
the CI emulator tests and the owner's phone are the proof.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass; adapters never
sign in; never automate a page's age or identity check); never log or commit cookies, tokens,
signed media or image URLs or keys; WebView and WebSettings calls on the main thread
(shouldInterceptRequest and @JavascriptInterface on other threads); Kotlin lines within 100
characters; keep every testTag; app text stays English; temporary files and backups outside
the repository (/data/tmp, /data/bak); never undo work with git reset --hard, git clean or
git stash; do not edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P27, put the old versions
back, run the new tests and see them fail, restore with cp, check with cmp, and name the
failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Your sections only: docs/TEST_MATRIX.md (with the measured times), CHANGELOG.md,
   docs/SESSION_STATE.md (status OWNER CHECK; Result with any "Plan adapted"; validation
   numbers; regression proof; measured split; CI links; hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P27: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting.
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5), with the run links, the measured
   split and the owner check: a long YouTube live recording (1 h or more) at 480p or 720p ->
   "Merging ... %" then "Saving ... %", a much shorter wait than in Preview #4, the file plays.
After P27: set your SESSION_STATE section to READY FOR MERGE (last commit, green CI links),
report, and stop. When B and C are READY FOR MERGE the owner gives you
docs/prompts/M-merge-preview5.md (P33).
```
